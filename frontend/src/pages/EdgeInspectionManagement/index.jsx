import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  cancelEdgeInspectionTasks,
  changeEdgeInspectionPointStatus,
  createEdgeInspectionPoint,
  getEdgeInspectionFeature,
  getEdgeInspectionPointTypes,
  getEdgeInspectionPoints,
  getEdgeInspectionRectification,
  getEdgeInspectionRectifications,
  getEdgeInspectionSetting,
  getEdgeInspectionStatistics,
  getEdgeInspectionTask,
  getEdgeInspectionTasks,
  getEdgeInspectionUserOptions,
  reassignEdgeInspectionRectification,
  reassignEdgeInspectionTask,
  updateEdgeInspectionFeature,
  updateEdgeInspectionPoint,
  updateEdgeInspectionSetting,
} from '../../services/edgeInspection';
import { hasProjectPermission, isPlatformAdmin } from '../../utils/permissions';
import {
  EDGE_POINT_TYPE_OPTIONS,
  EDGE_RECTIFICATION_STATUS_TEXT,
  WEEKDAY_OPTIONS,
  edgePointTypeCode,
  edgePointTypeName,
  edgeTaskStatusText,
  formatLocalDate,
  normalizeEdgeSetting,
  taskTimeRange,
} from './model';
import './index.css';

const today = () => formatLocalDate();
const monthStart = () => `${today().slice(0, 7)}-01`;
const emptyPoint = (projectId) => ({
  projectId: Number(projectId), pointName: '', pointTypeCode: '', buildingName: '', floorName: '', locationDesc: '',
});
const unwrap = (response, fallback) => {
  if (response?.code !== 200) throw new Error(response?.message || fallback);
  return response.data;
};
const valueName = (value) => value?.userName || value?.realName || value?.name || value?.username || `用户${value?.userId || value?.id || ''}`;
const valueId = (value) => value?.userId ?? value?.id;
const dateText = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : '-';
const resultText = (value) => ({ NORMAL: '正常', ABNORMAL: '异常' })[String(value || '').toUpperCase()] || value || '-';

function Modal({ title, children, onClose, onSubmit, submitText = '保存', busy, wide = false }) {
  return <div className="edge-modal-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
    <div className={`edge-modal${wide ? ' wide' : ''}`}>
      <div className="edge-modal-header"><strong>{title}</strong><button className="edge-button" type="button" onClick={onClose}>关闭</button></div>
      {children}
      {onSubmit && <div className="edge-modal-footer">
        <button className="edge-button" type="button" onClick={onClose}>取消</button>
        <button className="edge-button primary" type="button" disabled={busy} onClick={onSubmit}>{busy ? '处理中…' : submitText}</button>
      </div>}
    </div>
  </div>;
}

function Field({ label, hint, children }) {
  return <label className="edge-field"><span>{label}</span>{children}{hint && <small>{hint}</small>}</label>;
}

function Empty({ children = '暂无数据' }) {
  return <div className="edge-empty">{children}</div>;
}

export default function EdgeInspectionManagement({ projectId, theme: T, currentUser, businessTarget }) {
  const platformAdmin = isPlatformAdmin(currentUser);
  const canView = platformAdmin || hasProjectPermission(currentUser, projectId, 'EDGE_INSPECTION_VIEW');
  const canManage = platformAdmin || hasProjectPermission(currentUser, projectId, 'EDGE_INSPECTION_MANAGE');
  const [activeView, setActiveView] = useState('points');
  const [feature, setFeature] = useState(null);
  const [pointTypes, setPointTypes] = useState([]);
  const [points, setPoints] = useState([]);
  const [users, setUsers] = useState([]);
  const [setting, setSetting] = useState(null);
  const [tasks, setTasks] = useState([]);
  const [rectifications, setRectifications] = useState([]);
  const [statistics, setStatistics] = useState(null);
  const [range, setRange] = useState({ startDate: monthStart(), endDate: today(), status: '' });
  const [rectificationFilter, setRectificationFilter] = useState({ status: '', scope: 'ALL' });
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [pointEditor, setPointEditor] = useState(null);
  const [pointAction, setPointAction] = useState(null);
  const [checklistVisible, setChecklistVisible] = useState(false);
  const [taskDetail, setTaskDetail] = useState(null);
  const [selectedTaskIds, setSelectedTaskIds] = useState(new Set());
  const [cancelEditor, setCancelEditor] = useState(null);
  const [taskReassign, setTaskReassign] = useState(null);
  const [rectificationDetail, setRectificationDetail] = useState(null);
  const [rectificationReassign, setRectificationReassign] = useState(null);

  const cssVars = {
    '--edge-text': T.textPrimary, '--edge-muted': T.textMuted, '--edge-border': T.borderColor,
    '--edge-card': T.cardBg, '--edge-surface': T.surface2, '--edge-accent': T.accent,
    '--edge-danger': T.danger, '--edge-success': T.success, '--edge-warning': T.warning,
  };
  const types = useMemo(() => pointTypes.length ? pointTypes : EDGE_POINT_TYPE_OPTIONS, [pointTypes]);
  const pointTypeNameByCode = useMemo(() => new Map(types.map((type) => [edgePointTypeCode(type), edgePointTypeName(type)])), [types]);
  const assigneeUsers = useMemo(() => users.filter((user) => user.canSubmit !== false), [users]);
  const rectifierUsers = useMemo(() => users.filter((user) => user.canRectify !== false), [users]);
  const reviewerUsers = useMemo(() => users.filter((user) => user.canReview !== false), [users]);

  const loadFeature = useCallback(async () => {
    if (!projectId) return;
    setLoading(true); setError('');
    try { setFeature(unwrap(await getEdgeInspectionFeature(projectId), '临边巡检开关加载失败')); }
    catch (err) { setError(err.message || '临边巡检开关加载失败'); }
    finally { setLoading(false); }
  }, [projectId]);

  const loadPoints = useCallback(async () => {
    if (!projectId || !feature?.enabled || !canView) return;
    setLoading(true); setError('');
    try {
      const [typeResponse, pointResponse] = await Promise.all([getEdgeInspectionPointTypes(projectId), getEdgeInspectionPoints(projectId)]);
      setPointTypes(unwrap(typeResponse, '固定点位类型加载失败') || []);
      setPoints(unwrap(pointResponse, '临边点位加载失败') || []);
    } catch (err) { setError(err.message || '临边点位加载失败'); }
    finally { setLoading(false); }
  }, [canView, feature?.enabled, projectId]);

  const loadSetting = useCallback(async () => {
    if (!projectId || !feature?.enabled || !canView) return;
    setLoading(true); setError('');
    try {
      const requests = [getEdgeInspectionSetting(projectId)];
      if (canManage) requests.push(getEdgeInspectionUserOptions(projectId));
      const [settingResponse, userResponse] = await Promise.all(requests);
      setSetting(normalizeEdgeSetting(unwrap(settingResponse, '巡检设置加载失败') || {}, projectId));
      if (userResponse) setUsers(unwrap(userResponse, '人员选项加载失败') || []);
    } catch (err) { setError(err.message || '巡检设置加载失败'); }
    finally { setLoading(false); }
  }, [canManage, canView, feature?.enabled, projectId]);

  const loadTasks = useCallback(async () => {
    if (!projectId || !feature?.enabled || !canView) return;
    setLoading(true); setError('');
    try {
      const requests = [getEdgeInspectionTasks({ projectId, mine: false, startDate: range.startDate, endDate: range.endDate, status: range.status || undefined })];
      if (canManage && !users.length) requests.push(getEdgeInspectionUserOptions(projectId));
      const [response, userResponse] = await Promise.all(requests);
      setTasks(unwrap(response, '临边记录加载失败') || []);
      if (userResponse) setUsers(unwrap(userResponse, '人员选项加载失败') || []);
      setSelectedTaskIds(new Set());
    } catch (err) { setError(err.message || '临边记录加载失败'); }
    finally { setLoading(false); }
  }, [canManage, canView, feature?.enabled, projectId, range, users.length]);

  const loadRectifications = useCallback(async () => {
    if (!projectId || !feature?.enabled || !canView) return;
    setLoading(true); setError('');
    try {
      const requests = [getEdgeInspectionRectifications({
        projectId, status: rectificationFilter.status || undefined, scope: rectificationFilter.scope,
      })];
      if (canManage && !users.length) requests.push(getEdgeInspectionUserOptions(projectId));
      const [response, userResponse] = await Promise.all(requests);
      setRectifications(unwrap(response, '整改闭环加载失败') || []);
      if (userResponse) setUsers(unwrap(userResponse, '人员选项加载失败') || []);
    } catch (err) { setError(err.message || '整改闭环加载失败'); }
    finally { setLoading(false); }
  }, [canManage, canView, feature?.enabled, projectId, rectificationFilter, users.length]);

  const loadStatistics = useCallback(async () => {
    if (!projectId || !feature?.enabled || !canView) return;
    setLoading(true); setError('');
    try { setStatistics(unwrap(await getEdgeInspectionStatistics({ projectId, startDate: range.startDate, endDate: range.endDate }), '临边统计加载失败')); }
    catch (err) { setError(err.message || '临边统计加载失败'); }
    finally { setLoading(false); }
  }, [canView, feature?.enabled, projectId, range.endDate, range.startDate]);

  useEffect(() => {
    setFeature(null); setPointTypes([]); setPoints([]); setUsers([]); setSetting(null); setTasks([]);
    setRectifications([]); setStatistics(null); setMessage(''); setError(''); loadFeature();
  }, [loadFeature]);
  useEffect(() => {
    if (activeView === 'points') loadPoints();
    if (activeView === 'setting') loadSetting();
    if (activeView === 'records') loadTasks();
    if (activeView === 'rectifications') loadRectifications();
    if (activeView === 'statistics') loadStatistics();
  }, [activeView, loadPoints, loadRectifications, loadSetting, loadStatistics, loadTasks]);
  useEffect(() => {
    const routeCode = String(businessTarget?.routeCode || '');
    if (routeCode === 'EDGE_INSPECTION_RECTIFICATION_DETAIL') setActiveView('rectifications');
    else if (routeCode === 'EDGE_INSPECTION_TASK_DETAIL') setActiveView('records');
  }, [businessTarget]);

  const run = async (key, action, success, refresh) => {
    setBusy(key); setError(''); setMessage('');
    try { await action(); setMessage(success); if (refresh) await refresh(); }
    catch (err) { setError(err.message || '操作失败'); }
    finally { setBusy(''); }
  };

  const toggleFeature = () => run('feature', async () => {
    const next = unwrap(await updateEdgeInspectionFeature(projectId, {
      enabled: !Boolean(feature?.enabled), expectedVersion: Number(feature?.version || 0),
    }), '项目开关更新失败');
    setFeature(next);
  }, feature?.enabled ? '临边巡检已关闭，历史记录继续保留' : '临边巡检已为当前试点项目开启', loadFeature);

  const savePoint = () => run('point-save', async () => {
    if (!pointEditor.pointName.trim()) throw new Error('请填写点位名称');
    if (!pointEditor.pointTypeCode) throw new Error('请选择系统固定点位类型');
    const payload = {
      projectId: Number(projectId), pointName: pointEditor.pointName.trim(), pointTypeCode: pointEditor.pointTypeCode,
      buildingName: pointEditor.buildingName?.trim() || '', floorName: pointEditor.floorName?.trim() || '',
      locationDesc: pointEditor.locationDesc?.trim() || '',
      expectedVersion: pointEditor.id ? Number(pointEditor.version) : undefined,
    };
    const response = pointEditor.id ? await updateEdgeInspectionPoint(pointEditor.id, payload) : await createEdgeInspectionPoint(payload);
    unwrap(response, '临边点位保存失败'); setPointEditor(null);
  }, pointEditor?.id ? '临边点位已更新' : '临边点位已创建，编码由系统生成', loadPoints);

  const changePointStatus = () => run('point-status', async () => {
    if (!pointAction.reason.trim()) throw new Error('请填写状态变更原因');
    unwrap(await changeEdgeInspectionPointStatus(pointAction.point.id, pointAction.nextStatus, {
      expectedVersion: Number(pointAction.point.version), reason: pointAction.reason.trim(),
    }), '点位状态更新失败');
    setPointAction(null);
  }, '点位已停用，未提交任务已取消', loadPoints);

  const saveSetting = () => run('setting-save', async () => {
    if (!setting.assigneeId || !setting.rectifierId || !setting.reviewerId) throw new Error('请完整选择巡检人、整改人和复查人');
    if (setting.frequency === 'WEEKLY' && !setting.weekdays.length) throw new Error('每周巡检至少选择一个星期');
    if (!setting.startTime || !setting.dueTime) throw new Error('请填写完整执行时段');
    const payload = {
      frequency: setting.frequency,
      weekdays: setting.frequency === 'WEEKLY' ? setting.weekdays.map(Number) : [],
      monthDay: setting.frequency === 'MONTHLY' && !setting.monthEnd ? Number(setting.monthDay) : null,
      monthEnd: setting.frequency === 'MONTHLY' && Boolean(setting.monthEnd),
      effectiveStart: setting.effectiveStart, startTime: setting.startTime, dueTime: setting.dueTime,
      assigneeId: Number(setting.assigneeId), rectifierId: Number(setting.rectifierId), reviewerId: Number(setting.reviewerId),
      rectificationDays: Number(setting.rectificationDays), enabled: Boolean(setting.enabled),
      expectedVersion: Number(setting.expectedVersion || 0),
    };
    const saved = unwrap(await updateEdgeInspectionSetting(projectId, payload), '巡检设置保存失败');
    setSetting(normalizeEdgeSetting(saved || payload, projectId));
  }, '巡检设置已保存，新设置只影响后续生成的任务', loadSetting);

  const openTask = async (task) => run(`task-${task.id}`, async () => {
    setTaskDetail(unwrap(await getEdgeInspectionTask(task.id), '临边巡检详情加载失败'));
  }, '', null);

  const cancelTasks = () => run('task-cancel', async () => {
    if (!cancelEditor.reason.trim()) throw new Error('请填写取消原因');
    unwrap(await cancelEdgeInspectionTasks({ taskIds: cancelEditor.taskIds, reason: cancelEditor.reason.trim() }), '任务取消失败');
    setCancelEditor(null);
  }, '所选未提交任务已取消', loadTasks);

  const saveTaskReassign = () => run('task-reassign', async () => {
    if (!taskReassign.assigneeId) throw new Error('请选择新的巡检人');
    if (!taskReassign.reason.trim()) throw new Error('请填写改派原因');
    unwrap(await reassignEdgeInspectionTask(taskReassign.task.id, {
      assigneeId: Number(taskReassign.assigneeId), reason: taskReassign.reason.trim(),
      expectedVersion: Number(taskReassign.task.version),
    }), '巡检任务改派失败');
    setTaskReassign(null);
  }, '巡检任务已改派并保留审计记录', loadTasks);

  const openRectification = async (row) => run(`rectification-${row.taskId || row.id}`, async () => {
    const taskId = row.taskId || row.inspectionTaskId || row.id;
    setRectificationDetail(unwrap(await getEdgeInspectionRectification(taskId), '整改单详情加载失败'));
  }, '', null);

  const saveRectificationReassign = () => run('rectification-reassign', async () => {
    if (!rectificationReassign.assigneeId || !rectificationReassign.reviewerId) throw new Error('请选择整改人和复查人');
    if (!rectificationReassign.reason.trim()) throw new Error('请填写改派原因');
    const taskId = rectificationReassign.row.taskId || rectificationReassign.row.inspectionTaskId || rectificationReassign.row.id;
    unwrap(await reassignEdgeInspectionRectification(taskId, {
      assigneeId: Number(rectificationReassign.assigneeId), reviewerId: Number(rectificationReassign.reviewerId),
      reason: rectificationReassign.reason.trim(), expectedVersion: Number(rectificationReassign.row.version || 0),
    }), '整改责任改派失败');
    setRectificationReassign(null);
  }, '整改人和复查人已更新并保留审计记录', loadRectifications);

  useEffect(() => {
    if (!feature?.enabled || !businessTarget?.id) return undefined;
    const routeCode = String(businessTarget.routeCode || '');
    if (!['EDGE_INSPECTION_TASK_DETAIL', 'EDGE_INSPECTION_RECTIFICATION_DETAIL'].includes(routeCode)) return undefined;
    let active = true;
    setBusy('business-target'); setError('');
    const request = routeCode === 'EDGE_INSPECTION_TASK_DETAIL'
      ? getEdgeInspectionTask(businessTarget.id)
      : getEdgeInspectionRectification(businessTarget.id);
    request.then((response) => {
      if (!active) return;
      const detail = unwrap(response, '临边巡检待办详情加载失败');
      if (routeCode === 'EDGE_INSPECTION_TASK_DETAIL') setTaskDetail(detail);
      else setRectificationDetail(detail);
    }).catch((err) => { if (active) setError(err.message || '临边巡检待办详情加载失败'); })
      .finally(() => { if (active) setBusy(''); });
    return () => { active = false; };
  }, [businessTarget?.id, businessTarget?.openedAt, businessTarget?.routeCode, feature?.enabled]);

  const userOptions = (list, selected = '') => <>
    <option value="">请选择</option>
    {list.map((user) => <option key={valueId(user)} value={valueId(user)}>{valueName(user)}</option>)}
    {selected && !list.some((user) => Number(valueId(user)) === Number(selected)) && <option value={selected}>当前人员（已失效）</option>}
  </>;
  const checklistItems = (type) => type.items || type.checkItems || type.inspectionItems || [];
  const rectificationRows = (detail) => detail?.items || detail?.abnormalItems || detail?.rectifications || [];
  const taskItems = taskDetail?.items || taskDetail?.checkItems || [];
  const tabs = [
    ['points', '临边点位'], ['setting', '巡检设置'], ['records', '临边记录'],
    ['rectifications', '整改闭环'], ['statistics', '统计'],
  ];

  if (!canView) return <div className="edge-card" style={cssVars}>当前角色缺少“临边巡检查看”权限。</div>;

  return <div className="edge-inspection" style={cssVars}>
    <div className="edge-heading edge-card">
      <div><h3>临边巡检</h3><p>点位类型和检查表由系统固定维护；项目只登记点位并设置一套巡检周期与责任人员。</p></div>
      <div className="edge-heading-actions">
        <span className={`edge-status ${feature?.enabled ? 'enabled' : ''}`}>{feature?.enabled ? '项目已启用' : '项目未启用'}</span>
        {platformAdmin && <button className={`edge-button ${feature?.enabled ? 'danger' : 'primary'}`} disabled={busy === 'feature'} type="button" onClick={toggleFeature}>{feature?.enabled ? '关闭试点' : '开启试点'}</button>}
      </div>
    </div>
    {message && <div className="edge-success">{message}</div>}
    {error && <div className="edge-error">{error}</div>}
    {loading && <div className="edge-loading">正在加载…</div>}

    {!feature?.enabled ? <div className="edge-card edge-disabled">
      <strong>当前项目尚未启用临边巡检</strong>
      <p>临边巡检默认关闭，由平台管理员选择试点项目开启。电箱台账、六项日检和原整改流程不受影响。</p>
    </div> : <>
      <div className="edge-tabs">{tabs.map(([id, label]) => <button key={id} type="button" className={activeView === id ? 'active' : ''} onClick={() => { setActiveView(id); setMessage(''); setError(''); }}>{label}</button>)}</div>

      {activeView === 'points' && <section className="edge-card">
        <div className="edge-toolbar"><div><h3>临边点位</h3><span className="edge-muted">共 {points.length} 个点位；点位编码由系统自动生成。</span></div><div>
          <button className="edge-button" type="button" onClick={() => setChecklistVisible(true)}>查看8类固定检查表</button>
          {canManage && <button className="edge-button primary" type="button" onClick={() => setPointEditor(emptyPoint(projectId))}>新增点位</button>}
        </div></div>
        {!points.length ? <Empty>暂无临边点位，请先新增点位。</Empty> : <div className="edge-table-wrap"><table className="edge-table"><thead><tr><th>点位编码 / 名称</th><th>系统固定类型</th><th>位置</th><th>状态</th><th>操作</th></tr></thead><tbody>
          {points.map((point) => <tr key={point.id}><td><strong>{point.pointCode}</strong><br />{point.pointName}</td><td>{point.pointTypeName || pointTypeNameByCode.get(point.pointTypeCode) || point.pointTypeCode}</td><td>{[point.buildingName, point.floorName, point.locationDesc].filter(Boolean).join(' / ') || '-'}</td><td><span className={`edge-status ${point.status === 'ACTIVE' ? 'enabled' : ''}`}>{point.status === 'ACTIVE' ? '启用' : '停用'}</span></td><td className="edge-actions">
            {canManage && <><button className="edge-button" type="button" onClick={() => setPointEditor({ ...point })}>编辑</button>{point.status === 'ACTIVE' && <button className="edge-button danger" type="button" onClick={() => setPointAction({ point, nextStatus: 'INACTIVE', reason: '' })}>停用</button>}</>}
          </td></tr>)}
        </tbody></table></div>}
      </section>}

      {activeView === 'setting' && <section className="edge-card">
        <div className="edge-toolbar"><div><h3>项目巡检设置</h3><span className="edge-muted">一个项目仅一套设置，自动应用到全部启用点位；已生成任务保留原快照。</span></div></div>
        {!setting ? <Empty>尚未创建巡检设置。</Empty> : <div className="edge-setting-form">
          <div className="edge-form-grid">
            <Field label="巡检频率"><select disabled={!canManage} value={setting.frequency} onChange={(event) => setSetting({ ...setting, frequency: event.target.value })}><option value="DAILY">每天</option><option value="WEEKLY">每周</option><option value="MONTHLY">每月</option></select></Field>
            <Field label="开始日期"><input disabled={!canManage} type="date" value={setting.effectiveStart} onChange={(event) => setSetting({ ...setting, effectiveStart: event.target.value })} /></Field>
            <Field label="执行开始时间"><input disabled={!canManage} type="time" value={setting.startTime} onChange={(event) => setSetting({ ...setting, startTime: event.target.value })} /></Field>
            <Field label="执行截止时间"><input disabled={!canManage} type="time" value={setting.dueTime} onChange={(event) => setSetting({ ...setting, dueTime: event.target.value })} /></Field>
          </div>
          {setting.frequency === 'WEEKLY' && <div className="edge-choice-row"><span>每周执行：</span>{WEEKDAY_OPTIONS.map((day) => <label key={day.value}><input disabled={!canManage} type="checkbox" checked={setting.weekdays.includes(day.value)} onChange={(event) => setSetting({ ...setting, weekdays: event.target.checked ? [...setting.weekdays, day.value].sort() : setting.weekdays.filter((value) => value !== day.value) })} /> {day.label}</label>)}</div>}
          {setting.frequency === 'MONTHLY' && <div className="edge-choice-row"><label><input disabled={!canManage} type="checkbox" checked={setting.monthEnd} onChange={(event) => setSetting({ ...setting, monthEnd: event.target.checked })} /> 每月最后一天</label>{!setting.monthEnd && <Field label="每月日期（1—31）" hint="当月没有该日期时，本月不生成任务"><input disabled={!canManage} type="number" min="1" max="31" value={setting.monthDay} onChange={(event) => setSetting({ ...setting, monthDay: Number(event.target.value) })} /></Field>}</div>}
          <div className="edge-form-grid edge-people-grid">
            <Field label="巡检人"><select disabled={!canManage} value={setting.assigneeId} onChange={(event) => setSetting({ ...setting, assigneeId: event.target.value })}>{userOptions(assigneeUsers, setting.assigneeId)}</select></Field>
            <Field label="整改人"><select disabled={!canManage} value={setting.rectifierId} onChange={(event) => setSetting({ ...setting, rectifierId: event.target.value })}>{userOptions(rectifierUsers, setting.rectifierId)}</select></Field>
            <Field label="复查人"><select disabled={!canManage} value={setting.reviewerId} onChange={(event) => setSetting({ ...setting, reviewerId: event.target.value })}>{userOptions(reviewerUsers, setting.reviewerId)}</select></Field>
            <Field label="整改期限（天）"><input disabled={!canManage} type="number" min="1" max="365" value={setting.rectificationDays} onChange={(event) => setSetting({ ...setting, rectificationDays: Number(event.target.value) })} /></Field>
          </div>
          <label className="edge-enable-setting"><input disabled={!canManage} type="checkbox" checked={setting.enabled} onChange={(event) => setSetting({ ...setting, enabled: event.target.checked })} /> 启用周期任务生成</label>
          {canManage && <div className="edge-form-actions"><button className="edge-button primary" disabled={busy === 'setting-save'} type="button" onClick={saveSetting}>保存巡检设置</button></div>}
        </div>}
      </section>}

      {activeView === 'records' && <section className="edge-card">
        <div className="edge-toolbar"><div><h3>临边记录</h3><span className="edge-muted">本列表仅显示临边任务，不与电箱巡检记录混排。</span></div><div className="edge-filters"><input type="date" value={range.startDate} onChange={(event) => setRange({ ...range, startDate: event.target.value })} /><span>至</span><input type="date" value={range.endDate} onChange={(event) => setRange({ ...range, endDate: event.target.value })} /><select value={range.status} onChange={(event) => setRange({ ...range, status: event.target.value })}><option value="">全部状态</option><option value="PENDING">待巡检</option><option value="COMPLETED">已完成</option><option value="RECTIFICATION_PENDING">整改中</option><option value="CLOSED">已闭环</option><option value="CANCELLED">已取消</option></select><button className="edge-button" type="button" onClick={loadTasks}>查询</button></div></div>
        {canManage && selectedTaskIds.size > 0 && <div className="edge-batchbar"><span>已选择 {selectedTaskIds.size} 项</span><button className="edge-button danger" type="button" onClick={() => setCancelEditor({ taskIds: [...selectedTaskIds], reason: '' })}>批量取消未提交任务</button></div>}
        {!tasks.length ? <Empty>所选日期范围内暂无临边任务。</Empty> : <div className="edge-table-wrap"><table className="edge-table"><thead><tr>{canManage && <th>选择</th>}<th>任务日期</th><th>点位</th><th>执行时段</th><th>巡检人</th><th>状态</th><th>提交时间</th><th>操作</th></tr></thead><tbody>
          {tasks.map((task) => { const cancellable = String(task.status).toUpperCase() === 'PENDING'; return <tr key={task.id}>{canManage && <td><input type="checkbox" disabled={!cancellable} checked={selectedTaskIds.has(task.id)} onChange={(event) => setSelectedTaskIds((current) => { const next = new Set(current); if (event.target.checked) next.add(task.id); else next.delete(task.id); return next; })} /></td>}<td>{task.occurrenceDate || task.taskDate || task.scheduledDate || '-'}</td><td><strong>{task.pointCode}</strong><br />{task.pointName}<br /><span className="edge-muted">{task.pointTypeName || pointTypeNameByCode.get(task.pointTypeCode) || task.pointTypeCode}</span></td><td>{taskTimeRange(task)}</td><td>{task.assigneeName || '-'}</td><td><span className="edge-status">{edgeTaskStatusText(task)}</span></td><td>{dateText(task.submittedTime || task.submittedAt || task.submitTime)}</td><td className="edge-actions"><button className="edge-button" type="button" onClick={() => openTask(task)}>详情</button>{canManage && cancellable && <button className="edge-button" type="button" onClick={() => setTaskReassign({ task, assigneeId: task.assigneeId || '', reason: '' })}>改派巡检人</button>}</td></tr>; })}
        </tbody></table></div>}
      </section>}

      {activeView === 'rectifications' && <section className="edge-card">
        <div className="edge-toolbar"><div><h3>整改闭环</h3><span className="edge-muted">同一点位同一次巡检的全部异常合并为一张整改单。</span></div><div className="edge-filters"><select value={rectificationFilter.status} onChange={(event) => setRectificationFilter({ ...rectificationFilter, status: event.target.value })}><option value="">全部状态</option><option value="UNASSIGNED">待分派</option><option value="PENDING">待整改</option><option value="COMPLETED">待复查</option><option value="REJECTED">已退回</option><option value="CLOSED">已关闭</option></select><select value={rectificationFilter.scope} onChange={(event) => setRectificationFilter({ ...rectificationFilter, scope: event.target.value })}><option value="ALL">全部整改单</option><option value="RECTIFY">由我整改</option><option value="REVIEW">由我复查</option><option value="MINE">与我相关</option></select><button className="edge-button" type="button" onClick={loadRectifications}>查询</button></div></div>
        {!rectifications.length ? <Empty>当前筛选条件下暂无临边整改单。</Empty> : <div className="edge-table-wrap"><table className="edge-table"><thead><tr><th>点位</th><th>任务日期</th><th>异常项</th><th>整改人</th><th>复查人</th><th>期限</th><th>状态</th><th>操作</th></tr></thead><tbody>
          {rectifications.map((row) => { const status = String(row.status || row.rectificationStatus || '').toUpperCase(); return <tr key={row.taskId || row.inspectionTaskId || row.id}><td><strong>{row.pointCode}</strong><br />{row.pointName}</td><td>{row.occurrenceDate || row.taskDate || row.scheduledDate || '-'}</td><td>{row.abnormalCount ?? row.totalCount ?? (row.items || []).length}</td><td>{row.assigneeName || row.rectifierName || '-'}</td><td>{row.reviewerName || '-'}</td><td>{dateText(row.deadline || row.dueTime || row.rectificationDeadline)}</td><td><span className="edge-status">{EDGE_RECTIFICATION_STATUS_TEXT[status] || status || '-'}</span></td><td className="edge-actions"><button className="edge-button" type="button" onClick={() => openRectification(row)}>查看整单</button>{canManage && status !== 'CLOSED' && <button className="edge-button" type="button" onClick={() => setRectificationReassign({ row, assigneeId: row.assigneeId || row.rectifierId || '', reviewerId: row.reviewerId || '', reason: '' })}>改派责任人</button>}</td></tr>; })}
        </tbody></table></div>}
      </section>}

      {activeView === 'statistics' && <section className="edge-card">
        <div className="edge-toolbar"><div><h3>临边巡检统计</h3><span className="edge-muted">统计口径只包含临边巡检，不包含电箱日检。</span></div><div className="edge-filters"><input type="date" value={range.startDate} onChange={(event) => setRange({ ...range, startDate: event.target.value })} /><span>至</span><input type="date" value={range.endDate} onChange={(event) => setRange({ ...range, endDate: event.target.value })} /><button className="edge-button" type="button" onClick={loadStatistics}>查询</button></div></div>
        {!statistics ? <Empty>暂无统计数据。</Empty> : <div className="edge-stats">
          {[['应检', statistics.dueCount ?? 0], ['按时完成', statistics.onTimeCount ?? 0], ['逾期补检', statistics.lateCompletedCount ?? 0], ['仍未检', statistics.missedCount ?? 0], ['异常点位', statistics.abnormalTaskCount ?? 0], ['整改关闭率', `${Number(statistics.rectificationClosureRate ?? 0).toFixed(1)}%`]].map(([label, value]) => <div className="edge-stat" key={label}><span>{label}</span><strong>{value}</strong></div>)}
        </div>}
        {statistics && <div className="edge-note">已完成 {statistics.completedCount ?? 0} 项；整改总数 {(statistics.openRectificationCount ?? 0) + (statistics.closedRectificationCount ?? 0)}，已关闭 {statistics.closedRectificationCount ?? 0}。</div>}
      </section>}
    </>}

    {checklistVisible && <Modal title="系统固定的8类临边检查表" wide onClose={() => setChecklistVisible(false)}><div className="edge-checklists">{types.map((type) => <article className="edge-checklist" key={edgePointTypeCode(type)}><h4>{edgePointTypeName(type)}</h4>{checklistItems(type).length ? <ol>{checklistItems(type).map((item) => <li key={item.itemKey || item.code || item.id}>{item.itemName || item.name}{(item.guidance || item.hint) && <small>检查提示：{item.guidance || item.hint}</small>}{item.standardReference && <small>规范依据：{item.standardReference}</small>}</li>)}</ol> : <p className="edge-muted">固定检查项由后端系统表提供，项目用户不可修改。</p>}</article>)}</div><div className="edge-note">系统检查表用于日常巡检，不能替代项目专项施工方案和验收结论。</div></Modal>}

    {pointEditor && <Modal title={pointEditor.id ? '编辑临边点位' : '新增临边点位'} onClose={() => setPointEditor(null)} onSubmit={savePoint} busy={busy === 'point-save'}><div className="edge-form-grid">
      {pointEditor.id && <Field label="点位编码" hint="系统生成，不可修改"><input disabled value={pointEditor.pointCode || ''} /></Field>}
      <Field label="点位名称"><input maxLength="100" value={pointEditor.pointName || ''} onChange={(event) => setPointEditor({ ...pointEditor, pointName: event.target.value })} /></Field>
      <Field label="固定点位类型" hint={pointEditor.hasGeneratedTasks ? '已产生任务，类型不可修改；类型错误请停用后重建。' : '只可从系统预设8类中选择'}><select disabled={Boolean(pointEditor.hasGeneratedTasks)} value={pointEditor.pointTypeCode || ''} onChange={(event) => setPointEditor({ ...pointEditor, pointTypeCode: event.target.value })}><option value="">请选择</option>{types.map((type) => <option key={edgePointTypeCode(type)} value={edgePointTypeCode(type)}>{edgePointTypeName(type)}</option>)}</select></Field>
      <Field label="楼栋"><input maxLength="100" value={pointEditor.buildingName || ''} onChange={(event) => setPointEditor({ ...pointEditor, buildingName: event.target.value })} /></Field>
      <Field label="楼层"><input maxLength="100" value={pointEditor.floorName || ''} onChange={(event) => setPointEditor({ ...pointEditor, floorName: event.target.value })} /></Field>
    </div><Field label="位置说明"><textarea rows="3" maxLength="300" value={pointEditor.locationDesc || ''} onChange={(event) => setPointEditor({ ...pointEditor, locationDesc: event.target.value })} /></Field></Modal>}

    {pointAction && <Modal title="停用临边点位" onClose={() => setPointAction(null)} onSubmit={changePointStatus} busy={busy === 'point-status'} submitText="确认停用"><p>{pointAction.point.pointCode} · {pointAction.point.pointName}</p><Field label="停用原因（必填）"><textarea rows="3" maxLength="300" value={pointAction.reason} onChange={(event) => setPointAction({ ...pointAction, reason: event.target.value })} /></Field><div className="edge-note warning">停用后，其尚未提交的临边任务将自动取消。首版不支持重新启用，需要恢复巡检时请新建点位。</div></Modal>}

    {taskDetail && <Modal title="临边巡检记录详情" wide onClose={() => setTaskDetail(null)}><div className="edge-detail-grid"><div><span>点位</span><strong>{taskDetail.pointCode} · {taskDetail.pointName}</strong></div><div><span>点位类型</span><strong>{taskDetail.pointTypeName || pointTypeNameByCode.get(taskDetail.pointTypeCode) || taskDetail.pointTypeCode}</strong></div><div><span>任务日期</span><strong>{taskDetail.occurrenceDate || taskDetail.taskDate || taskDetail.scheduledDate || '-'}</strong></div><div><span>状态</span><strong>{edgeTaskStatusText(taskDetail)}</strong></div><div><span>执行时段</span><strong>{taskTimeRange(taskDetail)}</strong></div><div><span>巡检人</span><strong>{taskDetail.assigneeName || '-'}</strong></div></div><h4>固定检查结果</h4>{!taskItems.length ? <Empty>尚未提交检查结果。</Empty> : <table className="edge-table"><thead><tr><th>检查项</th><th>结果</th><th>说明</th><th>证据照片</th></tr></thead><tbody>{taskItems.map((item) => <tr key={item.snapshotItemId || item.id}><td>{item.itemName}{item.guidance && <div className="edge-muted">提示：{item.guidance}</div>}{item.standardReference && <div className="edge-muted">依据：{item.standardReference}</div>}</td><td>{resultText(item.result)}</td><td>{item.description || '-'}</td><td>{(item.photoFileIds || item.photos || []).length || 0} 张</td></tr>)}</tbody></table>}<div className="edge-note">现场全景照片 {(taskDetail.overallPhotoFileIds || taskDetail.overallPhotos || []).length || 0} 张；总备注：{taskDetail.remark || taskDetail.overallRemark || '-'}</div></Modal>}

    {cancelEditor && <Modal title="批量取消临边任务" onClose={() => setCancelEditor(null)} onSubmit={cancelTasks} busy={busy === 'task-cancel'} submitText="确认取消"><p>即将取消 {cancelEditor.taskIds.length} 项尚未提交的临边任务。取消任务不计入应检和漏检。</p><Field label="取消原因（必填）"><textarea rows="3" maxLength="300" value={cancelEditor.reason} onChange={(event) => setCancelEditor({ ...cancelEditor, reason: event.target.value })} /></Field></Modal>}

    {taskReassign && <Modal title="改派临边巡检人" onClose={() => setTaskReassign(null)} onSubmit={saveTaskReassign} busy={busy === 'task-reassign'}><Field label="新巡检人"><select value={taskReassign.assigneeId} onChange={(event) => setTaskReassign({ ...taskReassign, assigneeId: event.target.value })}>{userOptions(assigneeUsers, taskReassign.assigneeId)}</select></Field><Field label="改派原因（必填）"><textarea rows="3" maxLength="300" value={taskReassign.reason} onChange={(event) => setTaskReassign({ ...taskReassign, reason: event.target.value })} /></Field></Modal>}

    {rectificationDetail && <Modal title="临边巡检整改单" wide onClose={() => setRectificationDetail(null)}><div className="edge-detail-grid"><div><span>点位</span><strong>{rectificationDetail.pointCode} · {rectificationDetail.pointName}</strong></div><div><span>整改人</span><strong>{rectificationDetail.assigneeName || rectificationDetail.rectifierName || '-'}</strong></div><div><span>复查人</span><strong>{rectificationDetail.reviewerName || '-'}</strong></div><div><span>状态</span><strong>{EDGE_RECTIFICATION_STATUS_TEXT[String(rectificationDetail.status || rectificationDetail.rectificationStatus || '').toUpperCase()] || rectificationDetail.status || '-'}</strong></div></div><table className="edge-table"><thead><tr><th>异常检查项</th><th>异常说明</th><th>异常证据</th><th>整改说明</th><th>整改照片</th></tr></thead><tbody>{rectificationRows(rectificationDetail).map((item) => <tr key={item.rectificationId || item.itemId || item.id}><td>{item.itemName}</td><td>{item.problemDesc || item.abnormalDescription || item.description || '-'}</td><td>{(item.evidencePhotoFileIds || []).length || 0} 张</td><td>{item.feedback || item.rectificationDescription || item.feedbackDescription || '-'}</td><td>{(item.photoFileIds || item.rectificationPhotoFileIds || item.feedbackPhotoFileIds || []).length || 0} 张</td></tr>)}</tbody></table>{(rectificationDetail.reviewComment || rectificationDetail.rejectReason) && <div className="edge-note warning">复查意见：{rectificationDetail.reviewComment || rectificationDetail.rejectReason}</div>}</Modal>}

    {rectificationReassign && <Modal title="改派临边整改责任" onClose={() => setRectificationReassign(null)} onSubmit={saveRectificationReassign} busy={busy === 'rectification-reassign'}><div className="edge-form-grid"><Field label="整改人"><select value={rectificationReassign.assigneeId} onChange={(event) => setRectificationReassign({ ...rectificationReassign, assigneeId: event.target.value })}>{userOptions(rectifierUsers, rectificationReassign.assigneeId)}</select></Field><Field label="复查人"><select value={rectificationReassign.reviewerId} onChange={(event) => setRectificationReassign({ ...rectificationReassign, reviewerId: event.target.value })}>{userOptions(reviewerUsers, rectificationReassign.reviewerId)}</select></Field></div><Field label="改派原因（必填）"><textarea rows="3" maxLength="300" value={rectificationReassign.reason} onChange={(event) => setRectificationReassign({ ...rectificationReassign, reason: event.target.value })} /></Field></Modal>}
  </div>;
}
