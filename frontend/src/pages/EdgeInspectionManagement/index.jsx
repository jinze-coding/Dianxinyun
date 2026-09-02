import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  cancelEdgeInspectionTasks,
  changeEdgeInspectionPointStatus,
  createEdgeInspectionExportJob,
  createEdgeInspectionPoint,
  downloadEdgeInspectionExport,
  getEdgeInspectionExportJob,
  getEdgeInspectionExportJobs,
  getEdgeInspectionPointTypes,
  getEdgeInspectionPoints,
  getEdgeInspectionRectification,
  getEdgeInspectionRectifications,
  getEdgeInspectionSetting,
  getEdgeInspectionStatistics,
  getEdgeInspectionTask,
  getEdgeInspectionTasks,
  getEdgeInspectionUserOptions,
  getEdgeInspectionWorkspaceSummary,
  reassignEdgeInspectionRectification,
  reassignEdgeInspectionTask,
  updateEdgeInspectionPoint,
  updateEdgeInspectionSetting,
} from '../../services/edgeInspection';
import { hasProjectPermission, isPlatformAdmin } from '../../utils/permissions';
import { createProjectRequestGuard } from '../../utils/projectRequestContext';
import {
  EDGE_POINT_TYPE_OPTIONS,
  EDGE_REMINDER_PROJECTION_LABEL,
  EDGE_REMINDER_PROJECTION_NOTICE,
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
const exportStatusText = (value) => ({ PENDING: '等待生成', RUNNING: '生成中', SUCCEEDED: '已完成', FAILED: '失败', EXPIRED: '已过期' })[String(value || '').toUpperCase()] || value || '-';
const displayPointName = (value) => String(value || '').replace(/\[EDGE_DEMO_[^\]]+\]\s*/gi, '').trim() || '-';
const taskDate = (task) => task?.occurrenceDate || task?.taskDate || task?.scheduledDate || '-';
const rectificationStatus = (row) => String(row?.status || row?.rectificationStatus || '').toUpperCase();
const taskSubmittedTime = (task) => task?.submittedTime || task?.submittedAt || task?.submitTime;
const timestamp = (value) => {
  const parsed = value ? new Date(value).getTime() : Number.POSITIVE_INFINITY;
  return Number.isFinite(parsed) ? parsed : Number.POSITIVE_INFINITY;
};
const EDGE_FLOW_STEPS = ['点位设置', '自动生成任务', '小程序巡检', '异常整改', '复查闭环'];

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
  const canExport = platformAdmin || (
    hasProjectPermission(currentUser, projectId, 'EDGE_INSPECTION_VIEW')
    && hasProjectPermission(currentUser, projectId, 'EDGE_INSPECTION_EXPORT')
  );
  const [activeView, setActiveView] = useState('todo');
  const [pointTypes, setPointTypes] = useState([]);
  const [points, setPoints] = useState([]);
  const [users, setUsers] = useState([]);
  const [setting, setSetting] = useState(null);
  const [workspaceSummary, setWorkspaceSummary] = useState(null);
  const [flowVisible, setFlowVisible] = useState(false);
  const [settingEditor, setSettingEditor] = useState(null);
  const [todoTasks, setTodoTasks] = useState([]);
  const [todoFilter, setTodoFilter] = useState('ALL');
  const [tasks, setTasks] = useState([]);
  const [rectifications, setRectifications] = useState([]);
  const [statistics, setStatistics] = useState(null);
  const [statisticsVisible, setStatisticsVisible] = useState(false);
  const [range, setRange] = useState({ startDate: monthStart(), endDate: today(), status: '' });
  const [pointFilter, setPointFilter] = useState({ keyword: '', typeCode: '', status: '' });
  const [recordFilter, setRecordFilter] = useState({ keyword: '', typeCode: '' });
  const [recordQuickFilter, setRecordQuickFilter] = useState('ALL');
  const [batchMode, setBatchMode] = useState(false);
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
  const [exportEditor, setExportEditor] = useState(null);
  const [exportJobs, setExportJobs] = useState([]);
  const currentEdgeProjectIdRef = useRef(projectId);
  const currentEdgeViewRef = useRef(activeView);
  const taskRequestGuardRef = useRef(createProjectRequestGuard());
  const todoRequestGuardRef = useRef(createProjectRequestGuard());
  const settingRequestGuardRef = useRef(createProjectRequestGuard());
  const statisticsRequestGuardRef = useRef(createProjectRequestGuard());
  const exportRequestGuardRef = useRef(createProjectRequestGuard());
  const exportPointRequestGuardRef = useRef(createProjectRequestGuard());
  const detailRequestGuardRef = useRef(createProjectRequestGuard());
  const actionRequestGuardRef = useRef(createProjectRequestGuard());
  currentEdgeProjectIdRef.current = projectId;
  currentEdgeViewRef.current = activeView;

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
  const filteredPoints = useMemo(() => points.filter((point) => {
    const keyword = pointFilter.keyword.trim().toLowerCase();
    const haystack = `${point.pointCode || ''} ${point.pointName || ''} ${point.buildingName || ''} ${point.floorName || ''} ${point.locationDesc || ''}`.toLowerCase();
    return (!keyword || haystack.includes(keyword))
      && (!pointFilter.typeCode || point.pointTypeCode === pointFilter.typeCode)
      && (!pointFilter.status || point.status === pointFilter.status);
  }), [pointFilter, points]);
  const recordBaseTasks = useMemo(() => tasks.filter((task) => {
    const keyword = recordFilter.keyword.trim().toLowerCase();
    const haystack = `${task.pointCode || ''} ${task.pointName || ''}`.toLowerCase();
    return (!keyword || haystack.includes(keyword))
      && (!recordFilter.typeCode || task.pointTypeCode === recordFilter.typeCode);
  }), [recordFilter, tasks]);
  const filteredTasks = useMemo(() => recordBaseTasks.filter((task) => {
    if (recordQuickFilter === 'ON_TIME') return Boolean(taskSubmittedTime(task)) && !task.lateSubmission && task.status !== 'CANCELLED';
    if (recordQuickFilter === 'LATE') return Boolean(taskSubmittedTime(task)) && Boolean(task.lateSubmission) && task.status !== 'CANCELLED';
    if (recordQuickFilter === 'MISSED') return edgeTaskStatusText(task) === '逾期未检';
    return true;
  }), [recordBaseTasks, recordQuickFilter]);
  const pointMetrics = useMemo(() => ({
    total: points.length,
    active: points.filter((point) => point.status === 'ACTIVE').length,
    inactive: points.filter((point) => point.status !== 'ACTIVE').length,
    types: new Set(points.map((point) => point.pointTypeCode).filter(Boolean)).size,
  }), [points]);
  const taskMetrics = useMemo(() => ({
    total: recordBaseTasks.length,
    onTime: recordBaseTasks.filter((task) => task.status !== 'CANCELLED' && taskSubmittedTime(task) && !task.lateSubmission).length,
    late: recordBaseTasks.filter((task) => task.status !== 'CANCELLED' && taskSubmittedTime(task) && Boolean(task.lateSubmission)).length,
    missed: recordBaseTasks.filter((task) => edgeTaskStatusText(task) === '逾期未检').length,
  }), [recordBaseTasks]);
  const todoItems = useMemo(() => {
    const rectificationTaskIds = new Set(rectifications.map((row) => Number(row.taskId || row.inspectionTaskId || row.id)).filter(Boolean));
    const overdueTasks = todoTasks
      .filter((task) => String(task.status || '').toUpperCase() === 'PENDING')
      .filter((task) => timestamp(task.dueTime || task.deadline || task.windowEnd) < Date.now())
      .filter((task) => !rectificationTaskIds.has(Number(task.id)))
      .map((task) => ({
        key: `task-${task.id}`, category: 'MISSED', categoryLabel: '逾期未检', source: task,
        pointCode: task.pointCode, pointName: task.pointName, pointTypeName: task.pointTypeName,
        businessDate: taskDate(task), deadline: task.dueTime || task.deadline || task.windowEnd,
        owner: task.assigneeName || '-', statusLabel: '逾期未检', overdue: true,
      }));
    const rectificationItems = rectifications
      .filter((row) => ['UNASSIGNED', 'PENDING', 'REJECTED', 'COMPLETED'].includes(rectificationStatus(row)))
      .map((row) => {
        const status = rectificationStatus(row);
        const review = status === 'COMPLETED';
        const deadline = row.deadline || row.dueTime || row.rectificationDeadline;
        return {
          key: `rectification-${row.taskId || row.inspectionTaskId || row.id}`,
          category: review ? 'REVIEW' : 'RECTIFY', categoryLabel: review ? '待复查' : '待整改', source: row,
          pointCode: row.pointCode, pointName: row.pointName, pointTypeName: row.pointTypeName,
          businessDate: row.occurrenceDate || row.taskDate || row.scheduledDate || '-', deadline,
          owner: review ? (row.reviewerName || '-') : (row.assigneeName || row.rectifierName || '待分派'),
          statusLabel: EDGE_RECTIFICATION_STATUS_TEXT[status] || status || '-',
          overdue: !review && Boolean(deadline) && String(deadline).slice(0, 10) < today(),
        };
      });
    return [...overdueTasks, ...rectificationItems].sort((left, right) => {
      if (left.overdue !== right.overdue) return left.overdue ? -1 : 1;
      const deadlineOrder = timestamp(left.deadline) - timestamp(right.deadline);
      if (Number.isFinite(deadlineOrder) && deadlineOrder !== 0) return deadlineOrder;
      return String(left.pointCode || '').localeCompare(String(right.pointCode || ''), 'zh-CN');
    });
  }, [rectifications, todoTasks]);
  const todoMetrics = useMemo(() => ({
    MISSED: todoItems.filter((item) => item.category === 'MISSED').length,
    RECTIFY: todoItems.filter((item) => item.category === 'RECTIFY').length,
    REVIEW: todoItems.filter((item) => item.category === 'REVIEW').length,
  }), [todoItems]);
  const filteredTodoItems = useMemo(() => todoFilter === 'ALL' ? todoItems : todoItems.filter((item) => item.category === todoFilter), [todoFilter, todoItems]);
  const settingFrequencyText = useMemo(() => {
    if (!setting) return '-';
    if (setting.frequency === 'WEEKLY') return `每周${WEEKDAY_OPTIONS.filter((day) => setting.weekdays?.includes(day.value)).map((day) => day.label.replace('周', '')).join('、') || '-'}`;
    if (setting.frequency === 'MONTHLY') return setting.monthEnd ? '每月最后一天' : `每月${setting.monthDay || 1}日`;
    return '每天';
  }, [setting]);

  const loadTasks = useCallback(async () => {
    const requestGuard = taskRequestGuardRef.current;
    if (!projectId || !canView) {
      requestGuard.invalidate();
      return;
    }
    const targetProjectId = projectId;
    const requestTicket = requestGuard.begin(targetProjectId);
    setLoading(true); setError('');
    try {
      const requests = [
        getEdgeInspectionTasks({ projectId: targetProjectId, mine: false, startDate: range.startDate, endDate: range.endDate, status: range.status || undefined }),
        getEdgeInspectionPointTypes(targetProjectId),
        getEdgeInspectionWorkspaceSummary(targetProjectId),
      ];
      if (canManage) requests.push(getEdgeInspectionUserOptions(targetProjectId));
      const [response, typeResponse, summaryResponse, userResponse] = await Promise.all(requests);
      if (!requestGuard.isCurrent(requestTicket, currentEdgeProjectIdRef.current)
        || currentEdgeViewRef.current !== 'records') return;
      const rows = unwrap(response, '临边记录加载失败') || [];
      setTasks(rows);
      setPointTypes(unwrap(typeResponse, '固定点位类型加载失败') || []);
      setWorkspaceSummary(unwrap(summaryResponse, '临边巡检工作台摘要加载失败') || null);
      if (userResponse) setUsers(unwrap(userResponse, '人员选项加载失败') || []);
      setSelectedTaskIds(new Set());
    } catch (err) {
      if (requestGuard.isCurrent(requestTicket, currentEdgeProjectIdRef.current)
        && currentEdgeViewRef.current === 'records') setError(err.message || '临边记录加载失败');
    } finally {
      if (requestGuard.isCurrent(requestTicket, currentEdgeProjectIdRef.current)
        && currentEdgeViewRef.current === 'records') setLoading(false);
    }
  }, [canManage, canView, projectId, range]);

  const loadTodo = useCallback(async () => {
    const requestGuard = todoRequestGuardRef.current;
    if (!projectId || !canView) {
      requestGuard.invalidate();
      return;
    }
    const targetProjectId = projectId;
    const requestTicket = requestGuard.begin(targetProjectId);
    setLoading(true); setError('');
    try {
      const requests = [
        getEdgeInspectionTasks({ projectId: targetProjectId, mine: false, status: 'PENDING' }),
        getEdgeInspectionRectifications({ projectId: targetProjectId, scope: 'ALL' }),
        getEdgeInspectionWorkspaceSummary(targetProjectId),
      ];
      if (canManage) requests.push(getEdgeInspectionUserOptions(targetProjectId));
      const [taskResponse, rectificationResponse, summaryResponse, userResponse] = await Promise.all(requests);
      if (!requestGuard.isCurrent(requestTicket, currentEdgeProjectIdRef.current)
        || currentEdgeViewRef.current !== 'todo') return;
      setTodoTasks(unwrap(taskResponse, '待处理巡检任务加载失败') || []);
      setRectifications(unwrap(rectificationResponse, '待处理整改单加载失败') || []);
      setWorkspaceSummary(unwrap(summaryResponse, '临边巡检工作台摘要加载失败') || null);
      if (userResponse) setUsers(unwrap(userResponse, '人员选项加载失败') || []);
    } catch (err) {
      if (requestGuard.isCurrent(requestTicket, currentEdgeProjectIdRef.current)
        && currentEdgeViewRef.current === 'todo') setError(err.message || '待处理事项加载失败');
    } finally {
      if (requestGuard.isCurrent(requestTicket, currentEdgeProjectIdRef.current)
        && currentEdgeViewRef.current === 'todo') setLoading(false);
    }
  }, [canManage, canView, projectId]);

  const loadSettingsWorkspace = useCallback(async () => {
    const requestGuard = settingRequestGuardRef.current;
    if (!projectId || !canView) {
      requestGuard.invalidate();
      return;
    }
    const targetProjectId = projectId;
    const requestTicket = requestGuard.begin(targetProjectId);
    setLoading(true); setError('');
    try {
      const requests = [
        getEdgeInspectionPointTypes(targetProjectId), getEdgeInspectionPoints(targetProjectId), getEdgeInspectionSetting(targetProjectId),
        getEdgeInspectionWorkspaceSummary(targetProjectId),
      ];
      if (canManage) requests.push(getEdgeInspectionUserOptions(targetProjectId));
      const [typeResponse, pointResponse, settingResponse, summaryResponse, userResponse] = await Promise.all(requests);
      if (!requestGuard.isCurrent(requestTicket, currentEdgeProjectIdRef.current)
        || currentEdgeViewRef.current !== 'settings') return;
      setPointTypes(unwrap(typeResponse, '固定点位类型加载失败') || []);
      setPoints(unwrap(pointResponse, '临边点位加载失败') || []);
      const data = unwrap(settingResponse, '巡检设置加载失败') || {};
      setSetting({ ...data, ...normalizeEdgeSetting(data, targetProjectId) });
      setWorkspaceSummary(unwrap(summaryResponse, '临边巡检工作台摘要加载失败') || null);
      if (userResponse) setUsers(unwrap(userResponse, '人员选项加载失败') || []);
    } catch (err) {
      if (requestGuard.isCurrent(requestTicket, currentEdgeProjectIdRef.current)
        && currentEdgeViewRef.current === 'settings') setError(err.message || '基础设置加载失败');
    } finally {
      if (requestGuard.isCurrent(requestTicket, currentEdgeProjectIdRef.current)
        && currentEdgeViewRef.current === 'settings') setLoading(false);
    }
  }, [canManage, canView, projectId]);

  const loadStatistics = useCallback(async () => {
    const requestGuard = statisticsRequestGuardRef.current;
    if (!projectId || !canView) {
      requestGuard.invalidate();
      return;
    }
    const targetProjectId = projectId;
    const requestTicket = requestGuard.begin(targetProjectId);
    setLoading(true); setError('');
    try {
      const data = unwrap(await getEdgeInspectionStatistics({ projectId: targetProjectId, startDate: range.startDate, endDate: range.endDate }), '临边统计加载失败');
      if (!requestGuard.isCurrent(requestTicket, currentEdgeProjectIdRef.current)) return;
      setStatistics(data);
    }
    catch (err) {
      if (requestGuard.isCurrent(requestTicket, currentEdgeProjectIdRef.current)) setError(err.message || '临边统计加载失败');
    } finally {
      if (requestGuard.isCurrent(requestTicket, currentEdgeProjectIdRef.current)) setLoading(false);
    }
  }, [canView, projectId, range.endDate, range.startDate]);

  const loadExportJobs = useCallback(async () => {
    const requestGuard = exportRequestGuardRef.current;
    if (!projectId || !canExport) {
      requestGuard.invalidate();
      return;
    }
    const targetProjectId = projectId;
    const requestTicket = requestGuard.begin(targetProjectId);
    try {
      const response = await getEdgeInspectionExportJobs(targetProjectId);
      if (!requestGuard.isCurrent(requestTicket, currentEdgeProjectIdRef.current)) return;
      setExportJobs(unwrap(response, '导出任务加载失败') || []);
    } catch (err) {
      if (requestGuard.isCurrent(requestTicket, currentEdgeProjectIdRef.current)) throw err;
    }
  }, [canExport, projectId]);

  useEffect(() => {
    currentEdgeViewRef.current = 'todo';
    taskRequestGuardRef.current.invalidate();
    todoRequestGuardRef.current.invalidate();
    settingRequestGuardRef.current.invalidate();
    statisticsRequestGuardRef.current.invalidate();
    exportRequestGuardRef.current.invalidate();
    exportPointRequestGuardRef.current.invalidate();
    detailRequestGuardRef.current.invalidate();
    actionRequestGuardRef.current.invalidate();
    setActiveView('todo'); setPointTypes([]); setPoints([]); setUsers([]); setSetting(null); setSettingEditor(null);
    setWorkspaceSummary(null); setFlowVisible(false);
    setTodoTasks([]); setTodoFilter('ALL'); setTasks([]); setRectifications([]); setStatistics(null); setStatisticsVisible(false);
    setRange({ startDate: monthStart(), endDate: today(), status: '' }); setPointFilter({ keyword: '', typeCode: '', status: '' });
    setRecordFilter({ keyword: '', typeCode: '' }); setRecordQuickFilter('ALL'); setBatchMode(false); setSelectedTaskIds(new Set());
    setPointAction(null); setCancelEditor(null); setTaskReassign(null); setRectificationReassign(null);
    setTaskDetail(null); setRectificationDetail(null); setExportEditor(null); setExportJobs([]);
    setLoading(false); setBusy(''); setMessage(''); setError('');
  }, [projectId]);
  useEffect(() => {
    if (activeView === 'todo') loadTodo();
    if (activeView === 'records') loadTasks();
    if (activeView === 'settings') loadSettingsWorkspace();
  }, [activeView, loadSettingsWorkspace, loadTasks, loadTodo]);
  useEffect(() => {
    const routeCode = String(businessTarget?.routeCode || '');
    if (routeCode === 'EDGE_INSPECTION_RECTIFICATION_DETAIL') setActiveView('todo');
    else if (routeCode === 'EDGE_INSPECTION_TASK_DETAIL') setActiveView('records');
  }, [businessTarget]);

  useEffect(() => {
    if (!exportEditor || !canExport) return undefined;
    let active = true;
    const refresh = async () => {
      try {
        await loadExportJobs();
      } catch (err) {
        if (active) setError(err.message || '导出任务加载失败');
      }
    };
    refresh();
    const timer = window.setInterval(refresh, 3000);
    return () => { active = false; window.clearInterval(timer); };
  }, [canExport, exportEditor, loadExportJobs]);

  const run = async (key, action, success, refresh) => {
    const targetProjectId = projectId;
    const requestGuard = actionRequestGuardRef.current;
    const requestTicket = requestGuard.begin(targetProjectId);
    setBusy(key); setError(''); setMessage('');
    try {
      await action();
      if (!requestGuard.isCurrent(requestTicket, currentEdgeProjectIdRef.current)) return;
      setMessage(success);
      if (refresh) await refresh();
    } catch (err) {
      if (requestGuard.isCurrent(requestTicket, currentEdgeProjectIdRef.current)) setError(err.message || '操作失败');
    } finally {
      if (requestGuard.isCurrent(requestTicket, currentEdgeProjectIdRef.current)) setBusy('');
    }
  };

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
  }, pointEditor?.id ? '临边点位已更新' : '临边点位已创建，编码由系统生成', loadSettingsWorkspace);

  const changePointStatus = () => run('point-status', async () => {
    if (!pointAction.reason.trim()) throw new Error('请填写状态变更原因');
    unwrap(await changeEdgeInspectionPointStatus(pointAction.point.id, pointAction.nextStatus, {
      expectedVersion: Number(pointAction.point.version), reason: pointAction.reason.trim(),
    }), '点位状态更新失败');
    setPointAction(null);
  }, '点位已停用，未提交任务已取消', loadSettingsWorkspace);

  const saveSetting = () => run('setting-save', async () => {
    if (!settingEditor.assigneeId || !settingEditor.rectifierId || !settingEditor.reviewerId) throw new Error('请完整选择巡检人、整改人和复查人');
    if (settingEditor.frequency === 'WEEKLY' && !settingEditor.weekdays.length) throw new Error('每周巡检至少选择一个星期');
    if (!settingEditor.startTime || !settingEditor.dueTime) throw new Error('请填写完整执行时段');
    const payload = {
      frequency: settingEditor.frequency,
      weekdays: settingEditor.frequency === 'WEEKLY' ? settingEditor.weekdays.map(Number) : [],
      monthDay: settingEditor.frequency === 'MONTHLY' && !settingEditor.monthEnd ? Number(settingEditor.monthDay) : null,
      monthEnd: settingEditor.frequency === 'MONTHLY' && Boolean(settingEditor.monthEnd),
      effectiveStart: settingEditor.effectiveStart, startTime: settingEditor.startTime, dueTime: settingEditor.dueTime,
      assigneeId: Number(settingEditor.assigneeId), rectifierId: Number(settingEditor.rectifierId), reviewerId: Number(settingEditor.reviewerId),
      rectificationDays: Number(settingEditor.rectificationDays), enabled: Boolean(settingEditor.enabled),
      submissionReminderEnabled: Boolean(settingEditor.submissionReminderEnabled),
      expectedVersion: Number(settingEditor.expectedVersion || 0),
    };
    const targetProjectId = projectId;
    const saved = unwrap(await updateEdgeInspectionSetting(targetProjectId, payload), '巡检设置保存失败');
    if (String(currentEdgeProjectIdRef.current ?? '') !== String(targetProjectId ?? '')) return;
    setSetting({ ...(saved || payload), ...normalizeEdgeSetting(saved || payload, targetProjectId) });
    setSettingEditor(null);
  }, '巡检设置已保存，新设置只影响后续生成的任务', loadSettingsWorkspace);

  const openTask = async (task) => run(`task-${task.id}`, async () => {
    const requestGuard = detailRequestGuardRef.current;
    const requestTicket = requestGuard.begin(projectId);
    const detail = unwrap(await getEdgeInspectionTask(task.id), '临边巡检详情加载失败');
    if (requestGuard.isCurrent(requestTicket, currentEdgeProjectIdRef.current)) setTaskDetail(detail);
  }, '', null);

  const cancelTasks = () => run('task-cancel', async () => {
    if (!cancelEditor.reason.trim()) throw new Error('请填写取消原因');
    unwrap(await cancelEdgeInspectionTasks({ taskIds: cancelEditor.taskIds, reason: cancelEditor.reason.trim() }), '任务取消失败');
    setCancelEditor(null);
  }, '所选未提交任务已取消', activeView === 'todo' ? loadTodo : loadTasks);

  const saveTaskReassign = () => run('task-reassign', async () => {
    if (!taskReassign.assigneeId) throw new Error('请选择新的巡检人');
    if (!taskReassign.reason.trim()) throw new Error('请填写改派原因');
    unwrap(await reassignEdgeInspectionTask(taskReassign.task.id, {
      assigneeId: Number(taskReassign.assigneeId), reason: taskReassign.reason.trim(),
      expectedVersion: Number(taskReassign.task.version),
    }), '巡检任务改派失败');
    setTaskReassign(null);
    setTaskDetail(null);
  }, '巡检任务已改派并保留审计记录', activeView === 'todo' ? loadTodo : loadTasks);

  const openRectification = async (row) => run(`rectification-${row.taskId || row.id}`, async () => {
    const taskId = row.taskId || row.inspectionTaskId || row.id;
    const requestGuard = detailRequestGuardRef.current;
    const requestTicket = requestGuard.begin(projectId);
    const detail = unwrap(await getEdgeInspectionRectification(taskId), '整改单详情加载失败');
    if (requestGuard.isCurrent(requestTicket, currentEdgeProjectIdRef.current)) setRectificationDetail(detail);
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
    setRectificationDetail(null);
  }, '整改人和复查人已更新并保留审计记录', loadTodo);

  const openStatistics = async () => {
    setStatisticsVisible(true);
    await loadStatistics();
  };

  const openExport = async () => {
    setExportEditor({ startDate: range.startDate, endDate: range.endDate, allPoints: true, pointIds: [] });
    if (points.length) return;
    const requestGuard = exportPointRequestGuardRef.current;
    const targetProjectId = projectId;
    const requestTicket = requestGuard.begin(targetProjectId);
    try {
      const response = await getEdgeInspectionPoints(targetProjectId);
      if (!requestGuard.isCurrent(requestTicket, currentEdgeProjectIdRef.current)) return;
      setPoints(unwrap(response, '临边点位加载失败') || []);
    } catch (err) {
      if (requestGuard.isCurrent(requestTicket, currentEdgeProjectIdRef.current)) setError(err.message || '临边点位加载失败');
    }
  };

  const createExport = () => run('export-create', async () => {
    if (!exportEditor.startDate || !exportEditor.endDate) throw new Error('请选择完整导出日期');
    const response = await createEdgeInspectionExportJob({
      projectId: Number(projectId),
      startDate: exportEditor.startDate,
      endDate: exportEditor.endDate,
      pointIds: exportEditor.allPoints ? [] : exportEditor.pointIds.map(Number),
    });
    unwrap(response, '导出任务创建失败');
    await loadExportJobs();
  }, '含图 Excel 已进入后台生成队列', null);

  const downloadExport = async (job) => run(`export-download-${job.id}`, async () => {
    const latest = unwrap(await getEdgeInspectionExportJob(job.id), '导出任务状态读取失败');
    if (!latest.downloadable) throw new Error('导出文件尚未生成或已经过期');
    const blob = await downloadEdgeInspectionExport(job.id);
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `临边巡检记录_${latest.startDate}至${latest.endDate}.xlsx`;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  }, '', null);

  useEffect(() => {
    if (!businessTarget?.id) return undefined;
    const routeCode = String(businessTarget.routeCode || '');
    if (!['EDGE_INSPECTION_TASK_DETAIL', 'EDGE_INSPECTION_RECTIFICATION_DETAIL'].includes(routeCode)) return undefined;
    if (businessTarget.projectId && Number(businessTarget.projectId) !== Number(projectId)) return undefined;
    const requestGuard = detailRequestGuardRef.current;
    const requestTicket = requestGuard.begin(projectId);
    let active = true;
    setBusy('business-target'); setError('');
    const request = routeCode === 'EDGE_INSPECTION_TASK_DETAIL'
      ? getEdgeInspectionTask(businessTarget.id)
      : getEdgeInspectionRectification(businessTarget.id);
    request.then((response) => {
      if (!active || !requestGuard.isCurrent(requestTicket, currentEdgeProjectIdRef.current)) return;
      const detail = unwrap(response, '临边巡检待办详情加载失败');
      if (routeCode === 'EDGE_INSPECTION_TASK_DETAIL') setTaskDetail(detail);
      else setRectificationDetail(detail);
    }).catch((err) => {
      if (active && requestGuard.isCurrent(requestTicket, currentEdgeProjectIdRef.current)) {
        setError(err.message || '临边巡检待办详情加载失败');
      }
    }).finally(() => {
      if (active && requestGuard.isCurrent(requestTicket, currentEdgeProjectIdRef.current)) setBusy('');
    });
    return () => { active = false; };
  }, [businessTarget?.id, businessTarget?.openedAt, businessTarget?.projectId, businessTarget?.routeCode, projectId]);

  const userOptions = (list, selected = '') => <>
    <option value="">请选择</option>
    {list.map((user) => <option key={valueId(user)} value={valueId(user)}>{valueName(user)}</option>)}
    {selected && !list.some((user) => Number(valueId(user)) === Number(selected)) && <option value={selected}>当前人员（已失效）</option>}
  </>;
  const checklistItems = (type) => type.items || type.checkItems || type.inspectionItems || [];
  const rectificationRows = (detail) => detail?.items || detail?.abnormalItems || detail?.rectifications || [];
  const taskItems = taskDetail?.items || taskDetail?.checkItems || [];
  const userNameById = (id, fallback) => {
    const matched = users.find((user) => Number(valueId(user)) === Number(id));
    return fallback || (matched ? valueName(matched) : '-');
  };
  const statisticBreakdowns = statistics?.breakdowns || [];
  const dimensionRows = (dimension) => statisticBreakdowns
    .filter((row) => row.dimension === dimension)
    .sort((left, right) => Number(right.dueCount || 0) - Number(left.dueCount || 0));
  const tabs = [
    { id: 'todo', label: '待处理' },
    { id: 'records', label: '巡检记录' },
    { id: 'settings', label: '基础设置' },
  ];

  if (!canView) return <div className="edge-card" style={cssVars}>当前角色缺少“临边巡检查看”权限。</div>;

  return <div className="edge-inspection" style={cssVars}>
    {message && <div className="edge-success">{message}</div>}
    {error && <div className="edge-error">{error}</div>}
    {loading && <div className="edge-loading">正在加载…</div>}

    <nav className="edge-tabs" aria-label="临边巡检子页面">{tabs.map((tab) => <button key={tab.id} type="button" className={activeView === tab.id ? 'active' : ''} onClick={() => { setActiveView(tab.id); setMessage(''); setError(''); }}>
      {tab.label}{tab.id === 'todo' && todoItems.length > 0 && <em>{todoItems.length}</em>}
    </button>)}</nav>

    <section className="edge-flow-strip" aria-label="临边巡检业务流程">
      <div className="edge-flow-scope"><b>{workspaceSummary?.enabledPointCount ?? '—'}</b><span>项目启用点位</span></div>
      <div className="edge-flow-steps">{EDGE_FLOW_STEPS.map((step, index) => <React.Fragment key={step}><span>{step}</span>{index < EDGE_FLOW_STEPS.length - 1 && <i>→</i>}</React.Fragment>)}</div>
      <button className="edge-link-button" type="button" onClick={() => setFlowVisible(true)}>流程说明</button>
    </section>

    {activeView === 'todo' && <section className="edge-card edge-workspace-card">
      <div className="edge-toolbar"><div><h3>项目待处理</h3><span className="edge-muted">项目范围：只显示逾期未检、待整改和待复查；未来预生成任务不会进入这里。</span></div><button className="edge-link-button" type="button" onClick={() => setActiveView('records')}>查看全部记录</button></div>
      <div className="edge-todo-filters">
        {[['MISSED', '逾期未检'], ['RECTIFY', '待整改'], ['REVIEW', '待复查']].map(([key, label]) => <button key={key} type="button" className={todoFilter === key ? 'active' : ''} onClick={() => setTodoFilter((current) => current === key ? 'ALL' : key)}><span>{label}</span><strong>{todoMetrics[key]}</strong></button>)}
      </div>
      {!filteredTodoItems.length ? <Empty>{todoItems.length ? '当前分类没有待处理事项。' : '当前没有需要处理的临边巡检事项。'}</Empty> : <div className="edge-table-wrap"><table className="edge-table edge-todo-table"><thead><tr><th>事项类型</th><th>点位</th><th>任务日期 / 期限</th><th>当前责任人</th><th>状态</th><th>操作</th></tr></thead><tbody>
        {filteredTodoItems.map((item) => <tr className="edge-clickable-row" key={item.key} onClick={() => item.category === 'MISSED' ? openTask(item.source) : openRectification(item.source)}><td><strong>{item.categoryLabel}</strong></td><td><strong>{item.pointCode}</strong><br />{displayPointName(item.pointName)}{item.pointTypeName && <><br /><span className="edge-muted">{item.pointTypeName}</span></>}</td><td>{item.businessDate}<br /><span className={item.overdue ? 'edge-danger-text' : 'edge-muted'}>{item.deadline ? `期限 ${dateText(item.deadline)}` : '-'}</span></td><td>{item.owner}</td><td><span className={`edge-status ${item.overdue ? 'danger' : ''}`}>{item.statusLabel}</span></td><td><button className="edge-button" type="button" onClick={(event) => { event.stopPropagation(); if (item.category === 'MISSED') openTask(item.source); else openRectification(item.source); }}>查看</button></td></tr>)}
      </tbody></table></div>}
    </section>}

      {activeView === 'records' && <section className="edge-card">
        <div className="edge-toolbar"><div><h3>巡检记录</h3><span className="edge-muted">项目范围：一条记录代表一个点位在一个计划日期的一次巡检；检查项、照片和执行时段在详情中展示。</span></div><div>{canManage && <button className="edge-button" type="button" onClick={() => { setBatchMode((current) => !current); setSelectedTaskIds(new Set()); }}>{batchMode ? '退出批量' : '批量操作'}</button>}<button className="edge-button" type="button" onClick={openStatistics}>统计</button>{canExport && <button className="edge-button" type="button" onClick={openExport}>导出</button>}</div></div>
        <div className="edge-compact-metrics">{[['ALL', '全部', taskMetrics.total], ['ON_TIME', '按时', taskMetrics.onTime], ['LATE', '逾期补检', taskMetrics.late], ['MISSED', '仍未检', taskMetrics.missed]].map(([key, label, value]) => <button key={key} type="button" className={recordQuickFilter === key ? 'active' : ''} onClick={() => setRecordQuickFilter(key)}><span>{label}</span><strong>{value}</strong></button>)}</div>
        <div className="edge-filter-panel edge-record-filters"><Field label="开始日期"><input type="date" value={range.startDate} onChange={(event) => setRange({ ...range, startDate: event.target.value })} /></Field><Field label="结束日期"><input type="date" value={range.endDate} onChange={(event) => setRange({ ...range, endDate: event.target.value })} /></Field><Field label="点位"><input value={recordFilter.keyword} placeholder="编码或名称" onChange={(event) => setRecordFilter({ ...recordFilter, keyword: event.target.value })} /></Field><Field label="点位类型"><select value={recordFilter.typeCode} onChange={(event) => setRecordFilter({ ...recordFilter, typeCode: event.target.value })}><option value="">全部类型</option>{types.map((type) => <option key={edgePointTypeCode(type)} value={edgePointTypeCode(type)}>{edgePointTypeName(type)}</option>)}</select></Field><Field label="状态"><select value={range.status} onChange={(event) => { setRange({ ...range, status: event.target.value }); setRecordQuickFilter('ALL'); }}><option value="">全部状态</option><option value="PENDING">待巡检</option><option value="COMPLETED">已完成</option><option value="RECTIFICATION_PENDING">整改中</option><option value="CLOSED">已闭环</option><option value="CANCELLED">已取消</option></select></Field><button className="edge-button" type="button" onClick={loadTasks}>查询</button></div>
        {canManage && batchMode && <div className="edge-batchbar"><span>{selectedTaskIds.size ? `已选择 ${selectedTaskIds.size} 项` : '请选择需要取消的未提交任务'}</span><button className="edge-button danger" disabled={!selectedTaskIds.size} type="button" onClick={() => setCancelEditor({ taskIds: [...selectedTaskIds], reason: '' })}>批量取消</button></div>}
        {!filteredTasks.length ? <Empty>所选条件下暂无临边任务。</Empty> : <div className="edge-table-wrap"><table className="edge-table edge-record-table"><thead><tr>{canManage && batchMode && <th>选择</th>}<th>任务日期</th><th>点位与类型</th><th>状态</th><th>巡检人 / 提交时间</th><th>操作</th></tr></thead><tbody>
          {filteredTasks.map((task) => { const cancellable = String(task.status).toUpperCase() === 'PENDING'; return <tr key={task.id}>{canManage && batchMode && <td><input type="checkbox" disabled={!cancellable} checked={selectedTaskIds.has(task.id)} onChange={(event) => setSelectedTaskIds((current) => { const next = new Set(current); if (event.target.checked) next.add(task.id); else next.delete(task.id); return next; })} /></td>}<td>{taskDate(task)}</td><td><strong>{task.pointCode} · {displayPointName(task.pointName)}</strong><br /><span className="edge-muted">{task.pointTypeName || pointTypeNameByCode.get(task.pointTypeCode) || task.pointTypeCode}</span></td><td><span className="edge-status">{edgeTaskStatusText(task)}</span></td><td>{task.assigneeName || '-'}<br /><span className="edge-muted">{dateText(taskSubmittedTime(task))}</span></td><td><button className="edge-button" type="button" onClick={() => openTask(task)}>详情</button></td></tr>; })}
        </tbody></table></div>}
      </section>}

    {activeView === 'settings' && <section className="edge-settings-workspace">
      <article className="edge-card edge-rule-summary"><div className="edge-toolbar"><div><h3>当前巡检规则</h3><span className="edge-muted">当前 {workspaceSummary?.enabledPointCount ?? pointMetrics.active} 个启用点位采用同一周期；每个执行日按“一个点位一项任务”自动生成。</span></div>{canManage && setting && <button className="edge-button primary" type="button" onClick={() => setSettingEditor({ ...setting, weekdays: [...(setting.weekdays || [])] })}>修改设置</button>}</div>
        {!setting ? <Empty>尚未创建巡检设置。</Empty> : <div className="edge-rule-grid">{[
          ['周期', settingFrequencyText], ['执行时段', `${setting.startTime || '-'} 至 ${setting.dueTime || '-'}`],
          ['巡检人', userNameById(setting.assigneeId, setting.assigneeName)], ['整改人', userNameById(setting.rectifierId, setting.rectifierName)],
          ['复查人', userNameById(setting.reviewerId, setting.reviewerName)], ['整改期限', `${setting.rectificationDays || 0} 天`],
          ['开始日期', setting.effectiveStart || '-'], ['状态', setting.enabled ? '启用' : '停用'],
          ['未提交提醒', setting.submissionReminderEnabled ? '已启用' : '未启用'], [EDGE_REMINDER_PROJECTION_LABEL, dateText(setting.nextReminderTime)],
        ].map(([label, value]) => <div key={label}><span>{label}</span><strong>{value}</strong></div>)}</div>}
        {setting && <div className="edge-note">{EDGE_REMINDER_PROJECTION_NOTICE}</div>}
      </article>
      <article className="edge-card"><div className="edge-toolbar"><div><h3>临边点位</h3><div className="edge-inline-summary">共 {pointMetrics.total} 个 · 启用 {pointMetrics.active} · 停用 {pointMetrics.inactive} · 覆盖 {pointMetrics.types}/8 类</div></div><div><button className="edge-link-button" type="button" onClick={() => setChecklistVisible(true)}>查看固定检查表</button>{canManage && <button className="edge-button primary" type="button" onClick={() => setPointEditor(emptyPoint(projectId))}>新增点位</button>}</div></div>
        <div className="edge-filter-panel edge-point-filters"><Field label="名称或位置"><input value={pointFilter.keyword} placeholder="输入编码、名称或位置" onChange={(event) => setPointFilter({ ...pointFilter, keyword: event.target.value })} /></Field><Field label="固定类型"><select value={pointFilter.typeCode} onChange={(event) => setPointFilter({ ...pointFilter, typeCode: event.target.value })}><option value="">全部类型</option>{types.map((type) => <option key={edgePointTypeCode(type)} value={edgePointTypeCode(type)}>{edgePointTypeName(type)}</option>)}</select></Field><Field label="状态"><select value={pointFilter.status} onChange={(event) => setPointFilter({ ...pointFilter, status: event.target.value })}><option value="">全部状态</option><option value="ACTIVE">启用</option><option value="INACTIVE">停用</option></select></Field></div>
        {!filteredPoints.length ? <Empty>{points.length ? '当前筛选条件下暂无点位。' : '暂无临边点位，请先新增点位。'}</Empty> : <div className="edge-table-wrap"><table className="edge-table"><thead><tr><th>点位编码 / 名称</th><th>系统固定类型</th><th>位置</th><th>状态</th><th>操作</th></tr></thead><tbody>
          {filteredPoints.map((point) => <tr key={point.id}><td><strong>{point.pointCode}</strong><br />{displayPointName(point.pointName)}</td><td>{point.pointTypeName || pointTypeNameByCode.get(point.pointTypeCode) || point.pointTypeCode}</td><td>{[point.buildingName, point.floorName, point.locationDesc].filter(Boolean).join(' / ') || '-'}</td><td><span className={`edge-status ${point.status === 'ACTIVE' ? 'enabled' : ''}`}>{point.status === 'ACTIVE' ? '启用' : '停用'}</span></td><td className="edge-actions">{canManage && <><button className="edge-button" type="button" onClick={() => setPointEditor({ ...point, pointName: displayPointName(point.pointName) })}>编辑</button>{point.status === 'ACTIVE' && <button className="edge-button danger" type="button" onClick={() => setPointAction({ point, nextStatus: 'INACTIVE', reason: '' })}>停用</button>}</>}</td></tr>)}
        </tbody></table></div>}
      </article>
    </section>}

    {settingEditor && <Modal title="修改巡检设置" wide onClose={() => setSettingEditor(null)} onSubmit={saveSetting} busy={busy === 'setting-save'} submitText="保存设置">
      <div className="edge-setting-sections">
        <article><header><b>1</b><div><h4>巡检周期</h4><span>选择任务生成频率和开始日期</span></div></header><div className="edge-form-grid"><Field label="巡检频率"><select value={settingEditor.frequency} onChange={(event) => setSettingEditor({ ...settingEditor, frequency: event.target.value })}><option value="DAILY">每天</option><option value="WEEKLY">每周</option><option value="MONTHLY">每月</option></select></Field><Field label="开始日期"><input type="date" value={settingEditor.effectiveStart} onChange={(event) => setSettingEditor({ ...settingEditor, effectiveStart: event.target.value })} /></Field></div>{settingEditor.frequency === 'WEEKLY' && <div className="edge-choice-row"><span>每周执行：</span>{WEEKDAY_OPTIONS.map((day) => <label key={day.value}><input type="checkbox" checked={settingEditor.weekdays.includes(day.value)} onChange={(event) => setSettingEditor({ ...settingEditor, weekdays: event.target.checked ? [...settingEditor.weekdays, day.value].sort() : settingEditor.weekdays.filter((value) => value !== day.value) })} /> {day.label}</label>)}</div>}{settingEditor.frequency === 'MONTHLY' && <div className="edge-choice-row"><label><input type="checkbox" checked={settingEditor.monthEnd} onChange={(event) => setSettingEditor({ ...settingEditor, monthEnd: event.target.checked })} /> 每月最后一天</label>{!settingEditor.monthEnd && <Field label="每月日期（1—31）" hint="当月没有该日期时不生成任务"><input type="number" min="1" max="31" value={settingEditor.monthDay} onChange={(event) => setSettingEditor({ ...settingEditor, monthDay: Number(event.target.value) })} /></Field>}</div>}</article>
        <article><header><b>2</b><div><h4>执行时段</h4><span>每天仅使用一个巡检时段</span></div></header><div className="edge-form-grid"><Field label="开始时间"><input type="time" value={settingEditor.startTime} onChange={(event) => setSettingEditor({ ...settingEditor, startTime: event.target.value })} /></Field><Field label="截止时间"><input type="time" value={settingEditor.dueTime} onChange={(event) => setSettingEditor({ ...settingEditor, dueTime: event.target.value })} /></Field></div></article>
        <article><header><b>3</b><div><h4>责任人员</h4><span>统一应用到全部启用点位</span></div></header><div className="edge-form-grid"><Field label="巡检人"><select value={settingEditor.assigneeId} onChange={(event) => setSettingEditor({ ...settingEditor, assigneeId: event.target.value })}>{userOptions(assigneeUsers, settingEditor.assigneeId)}</select></Field><Field label="整改人"><select value={settingEditor.rectifierId} onChange={(event) => setSettingEditor({ ...settingEditor, rectifierId: event.target.value })}>{userOptions(rectifierUsers, settingEditor.rectifierId)}</select></Field><Field label="复查人"><select value={settingEditor.reviewerId} onChange={(event) => setSettingEditor({ ...settingEditor, reviewerId: event.target.value })}>{userOptions(reviewerUsers, settingEditor.reviewerId)}</select></Field><Field label="整改期限（天）"><input type="number" min="1" max="365" value={settingEditor.rectificationDays} onChange={(event) => setSettingEditor({ ...settingEditor, rectificationDays: Number(event.target.value) })} /></Field></div></article>
        <article><header><b>4</b><div><h4>生效与站内提醒</h4><span>实际任务到达截止时间后才可能产生提醒</span></div></header><label className="edge-enable-setting"><input type="checkbox" checked={settingEditor.enabled} onChange={(event) => setSettingEditor({ ...settingEditor, enabled: event.target.checked })} /> 启用周期任务生成</label><label className="edge-enable-setting"><input type="checkbox" checked={settingEditor.submissionReminderEnabled} onChange={(event) => setSettingEditor({ ...settingEditor, submissionReminderEnabled: event.target.checked })} /> 到期仍未提交时提醒当前巡检人</label><div className="edge-note">每个任务仅提醒一次；首次启用或重新启用时，若当前时段尚未截止会生成本时段任务，已截止的历史任务不追补。新建点位仍从创建后的下一适用时段开始纳入。{settingEditor.nextReminderTime ? ` ${EDGE_REMINDER_PROJECTION_LABEL}：${dateText(settingEditor.nextReminderTime)}。` : ''} {EDGE_REMINDER_PROJECTION_NOTICE}</div></article>
      </div>
      <div className="edge-note">保存后生成内部版本；已生成和已提交任务继续保留原快照。</div>
    </Modal>}

    {exportEditor && <Modal title="临边巡检含图 Excel 导出" wide onClose={() => setExportEditor(null)} onSubmit={createExport} submitText="创建导出任务" busy={busy === 'export-create'}>
      <div className="edge-export-layout"><section><h4>导出范围</h4><div className="edge-form-grid"><Field label="开始日期"><input type="date" max={today()} value={exportEditor.startDate} onChange={(event) => setExportEditor({ ...exportEditor, startDate: event.target.value })} /></Field><Field label="结束日期" hint="闭区间，最多31天且不能晚于今天"><input type="date" max={today()} value={exportEditor.endDate} onChange={(event) => setExportEditor({ ...exportEditor, endDate: event.target.value })} /></Field></div><div className="edge-choice-row"><label><input type="radio" checked={exportEditor.allPoints} onChange={() => setExportEditor({ ...exportEditor, allPoints: true, pointIds: [] })} /> 全部点位</label><label><input type="radio" checked={!exportEditor.allPoints} onChange={() => setExportEditor({ ...exportEditor, allPoints: false })} /> 指定点位</label></div>{!exportEditor.allPoints && <div className="edge-point-selector">{points.map((point) => <label key={point.id}><input type="checkbox" checked={exportEditor.pointIds.includes(point.id)} onChange={(event) => setExportEditor({ ...exportEditor, pointIds: event.target.checked ? [...exportEditor.pointIds, point.id] : exportEditor.pointIds.filter((id) => id !== point.id) })} /><span><strong>{point.pointCode} · {displayPointName(point.pointName)}</strong><small>{point.pointTypeName || pointTypeNameByCode.get(point.pointTypeCode)}</small></span></label>)}</div>}<div className="edge-note">当前页面已加载 {tasks.length} 个任务供范围参考。后台会重新校验实际任务数、照片数量和容量；单次最多 500 个任务、800 张照片和 120MB 原图。</div></section>
      <section><h4>近期导出任务</h4>{!exportJobs.length ? <Empty>尚无导出任务</Empty> : <div className="edge-export-jobs">{exportJobs.map((job) => <article key={job.id}><div><strong>{job.startDate} 至 {job.endDate}</strong><span className={`edge-status ${job.status === 'SUCCEEDED' ? 'enabled' : ''}`}>{exportStatusText(job.status)}</span></div><p>{job.pointCount || 0} 个点位 · {job.taskCount || 0} 个任务 · {job.photoCount || 0} 张照片</p><div className="edge-progress"><i style={{ width: `${Math.max(0, Math.min(100, job.progress || 0))}%` }} /></div>{job.errorMessage && <small className="edge-job-error">{job.errorMessage}</small>}<footer><span>{job.expiresTime ? `有效期至 ${dateText(job.expiresTime)}` : `创建于 ${dateText(job.createTime)}`}</span>{job.downloadable && <button className="edge-button" type="button" disabled={busy === `export-download-${job.id}`} onClick={() => downloadExport(job)}>下载</button>}</footer></article>)}</div>}</section></div>
    </Modal>}

    {checklistVisible && <Modal title="系统固定的8类临边检查表" wide onClose={() => setChecklistVisible(false)}><div className="edge-checklists">{types.map((type) => <article className="edge-checklist" key={edgePointTypeCode(type)}><h4>{edgePointTypeName(type)}</h4>{checklistItems(type).length ? <ol>{checklistItems(type).map((item) => <li key={item.itemKey || item.code || item.id}>{item.itemName || item.name}{(item.guidance || item.hint) && <small>检查提示：{item.guidance || item.hint}</small>}{item.standardReference && <small>规范依据：{item.standardReference}</small>}</li>)}</ol> : <p className="edge-muted">固定检查项由后端系统表提供，项目用户不可修改。</p>}</article>)}</div><div className="edge-note">系统检查表用于日常巡检，不能替代项目专项施工方案和验收结论。</div></Modal>}

    {pointEditor && <Modal title={pointEditor.id ? '编辑临边点位' : '新增临边点位'} onClose={() => setPointEditor(null)} onSubmit={savePoint} busy={busy === 'point-save'}><div className="edge-form-grid">
      {pointEditor.id && <Field label="点位编码" hint="系统生成，不可修改"><input disabled value={pointEditor.pointCode || ''} /></Field>}
      <Field label="点位名称"><input maxLength="100" value={pointEditor.pointName || ''} onChange={(event) => setPointEditor({ ...pointEditor, pointName: event.target.value })} /></Field>
      <Field label="固定点位类型" hint={pointEditor.hasGeneratedTasks ? '已产生任务，类型不可修改；类型错误请停用后重建。' : '只可从系统预设8类中选择'}><select disabled={Boolean(pointEditor.hasGeneratedTasks)} value={pointEditor.pointTypeCode || ''} onChange={(event) => setPointEditor({ ...pointEditor, pointTypeCode: event.target.value })}><option value="">请选择</option>{types.map((type) => <option key={edgePointTypeCode(type)} value={edgePointTypeCode(type)}>{edgePointTypeName(type)}</option>)}</select></Field>
      <Field label="楼栋"><input maxLength="100" value={pointEditor.buildingName || ''} onChange={(event) => setPointEditor({ ...pointEditor, buildingName: event.target.value })} /></Field>
      <Field label="楼层"><input maxLength="100" value={pointEditor.floorName || ''} onChange={(event) => setPointEditor({ ...pointEditor, floorName: event.target.value })} /></Field>
    </div><Field label="位置说明"><textarea rows="3" maxLength="300" value={pointEditor.locationDesc || ''} onChange={(event) => setPointEditor({ ...pointEditor, locationDesc: event.target.value })} /></Field></Modal>}

    {pointAction && <Modal title="停用临边点位" onClose={() => setPointAction(null)} onSubmit={changePointStatus} busy={busy === 'point-status'} submitText="确认停用"><p>{pointAction.point.pointCode} · {displayPointName(pointAction.point.pointName)}</p><Field label="停用原因（必填）"><textarea rows="3" maxLength="300" value={pointAction.reason} onChange={(event) => setPointAction({ ...pointAction, reason: event.target.value })} /></Field><div className="edge-note warning">停用后，其尚未提交的临边任务将自动取消。首版不支持重新启用，需要恢复巡检时请新建点位。</div></Modal>}

    {flowVisible && <div className="edge-drawer-backdrop" onMouseDown={(event) => event.target === event.currentTarget && setFlowVisible(false)}><aside className="edge-drawer edge-flow-drawer"><header><div><span>点位不等于任务</span><h3>临边巡检如何运转</h3></div><button className="edge-button" type="button" onClick={() => setFlowVisible(false)}>关闭</button></header><div className="edge-drawer-body">
      <div className="edge-flow-equation"><strong>{workspaceSummary?.enabledPointCount ?? '—'} 个启用点位</strong><span>× 每个计划日期 1 次</span><b>= 每个执行日 {workspaceSummary?.enabledPointCount ?? '—'} 项任务</b></div>
      <ol className="edge-flow-detail">
        <li><b>点位设置</b><p>管理员在 Web 登记点位，并统一设置周期、时段、巡检人、整改人和复查人。</p></li>
        <li><b>自动生成任务</b><p>系统按“启用点位 × 计划日期”生成任务。未来任务可在后台预生成，但不会提前进入页面或个人待办。</p></li>
        <li><b>小程序巡检</b><p>指定巡检人在小程序上传现场全景照片，并完成对应类型的 5 项固定检查。</p></li>
        <li><b>异常整改</b><p>全部正常即完成；只要存在异常，同一点位同一次巡检的异常项会合成一张整改单。</p></li>
        <li><b>复查闭环</b><p>整改人逐项反馈并上传照片，复查人统一通过关闭或退回整改。</p></li>
      </ol>
      <div className="edge-note"><b>数字口径：</b>本 Web 页面展示项目范围；小程序只展示当前账号被明确指派的巡检、整改和复查事项。</div>
    </div></aside></div>}

    {taskDetail && <div className="edge-drawer-backdrop" onMouseDown={(event) => event.target === event.currentTarget && setTaskDetail(null)}><aside className="edge-drawer"><header><div><span>巡检记录详情</span><h3>{taskDetail.pointCode} · {displayPointName(taskDetail.pointName)}</h3></div><div>{canManage && String(taskDetail.status || '').toUpperCase() === 'PENDING' && <button className="edge-button" type="button" onClick={() => setTaskReassign({ task: taskDetail, assigneeId: taskDetail.assigneeId || '', reason: '' })}>改派巡检人</button>}<button className="edge-button" type="button" onClick={() => setTaskDetail(null)}>关闭</button></div></header><div className="edge-drawer-body"><div className="edge-detail-grid"><div><span>点位类型</span><strong>{taskDetail.pointTypeName || pointTypeNameByCode.get(taskDetail.pointTypeCode) || taskDetail.pointTypeCode}</strong></div><div><span>任务日期</span><strong>{taskDate(taskDetail)}</strong></div><div><span>状态</span><strong>{edgeTaskStatusText(taskDetail)}</strong></div><div><span>执行时段</span><strong>{taskTimeRange(taskDetail)}</strong></div><div><span>巡检人</span><strong>{taskDetail.assigneeName || '-'}</strong></div><div><span>提交时间</span><strong>{dateText(taskSubmittedTime(taskDetail))}</strong></div></div><h4>固定检查结果</h4>{!taskItems.length ? <Empty>尚未提交检查结果。</Empty> : <div className="edge-table-wrap"><table className="edge-table"><thead><tr><th>检查项</th><th>结果</th><th>说明</th><th>证据照片</th></tr></thead><tbody>{taskItems.map((item) => <tr key={item.snapshotItemId || item.id}><td>{item.itemName}{item.guidance && <div className="edge-muted">提示：{item.guidance}</div>}{item.standardReference && <div className="edge-muted">依据：{item.standardReference}</div>}</td><td>{resultText(item.result)}</td><td>{item.description || '-'}</td><td>{(item.photoFileIds || item.photos || []).length || 0} 张</td></tr>)}</tbody></table></div>}<div className="edge-note">现场全景照片 {(taskDetail.overallPhotoFileIds || taskDetail.overallPhotos || []).length || 0} 张；总备注：{taskDetail.remark || taskDetail.overallRemark || '-'}</div></div></aside></div>}

    {cancelEditor && <Modal title="批量取消临边任务" onClose={() => setCancelEditor(null)} onSubmit={cancelTasks} busy={busy === 'task-cancel'} submitText="确认取消"><p>即将取消 {cancelEditor.taskIds.length} 项尚未提交的临边任务。取消任务不计入应检和漏检。</p><Field label="取消原因（必填）"><textarea rows="3" maxLength="300" value={cancelEditor.reason} onChange={(event) => setCancelEditor({ ...cancelEditor, reason: event.target.value })} /></Field></Modal>}

    {taskReassign && <Modal title="改派临边巡检人" onClose={() => setTaskReassign(null)} onSubmit={saveTaskReassign} busy={busy === 'task-reassign'}><Field label="新巡检人"><select value={taskReassign.assigneeId} onChange={(event) => setTaskReassign({ ...taskReassign, assigneeId: event.target.value })}>{userOptions(assigneeUsers, taskReassign.assigneeId)}</select></Field><Field label="改派原因（必填）"><textarea rows="3" maxLength="300" value={taskReassign.reason} onChange={(event) => setTaskReassign({ ...taskReassign, reason: event.target.value })} /></Field></Modal>}

    {rectificationDetail && <div className="edge-drawer-backdrop" onMouseDown={(event) => event.target === event.currentTarget && setRectificationDetail(null)}><aside className="edge-drawer"><header><div><span>整改闭环详情</span><h3>{rectificationDetail.pointCode} · {displayPointName(rectificationDetail.pointName)}</h3></div><div>{canManage && rectificationStatus(rectificationDetail) !== 'CLOSED' && <button className="edge-button" type="button" onClick={() => setRectificationReassign({ row: rectificationDetail, assigneeId: rectificationDetail.assigneeId || rectificationDetail.rectifierId || '', reviewerId: rectificationDetail.reviewerId || '', reason: '' })}>改派责任人</button>}<button className="edge-button" type="button" onClick={() => setRectificationDetail(null)}>关闭</button></div></header><div className="edge-drawer-body"><div className="edge-detail-grid"><div><span>任务日期</span><strong>{rectificationDetail.occurrenceDate || '-'}</strong></div><div><span>整改期限</span><strong>{dateText(rectificationDetail.deadline)}</strong></div><div><span>整改人</span><strong>{rectificationDetail.assigneeName || rectificationDetail.rectifierName || '-'}</strong></div><div><span>复查人</span><strong>{rectificationDetail.reviewerName || '-'}</strong></div><div><span>状态</span><strong>{EDGE_RECTIFICATION_STATUS_TEXT[rectificationStatus(rectificationDetail)] || rectificationDetail.status || '-'}</strong></div></div><h4>异常与整改反馈</h4>{rectificationRows(rectificationDetail).map((item) => <article className="edge-rectification-item" key={item.rectificationId || item.itemId || item.id}><h5>{item.itemName}</h5><dl><div><dt>异常说明</dt><dd>{item.problemDesc || item.abnormalDescription || item.description || '-'}</dd></div><div><dt>异常证据</dt><dd>{(item.evidencePhotoFileIds || []).length || 0} 张</dd></div><div><dt>整改说明</dt><dd>{item.feedback || item.rectificationDescription || item.feedbackDescription || '-'}</dd></div><div><dt>整改照片</dt><dd>{(item.photoFileIds || item.rectificationPhotoFileIds || item.feedbackPhotoFileIds || []).length || 0} 张</dd></div></dl></article>)}{(rectificationDetail.reviewComment || rectificationDetail.rejectReason) && <div className="edge-note warning">复查意见：{rectificationDetail.reviewComment || rectificationDetail.rejectReason}</div>}</div></aside></div>}

    {statisticsVisible && <div className="edge-drawer-backdrop" onMouseDown={(event) => event.target === event.currentTarget && setStatisticsVisible(false)}><aside className="edge-drawer edge-statistics-drawer"><header><div><span>{range.startDate} 至 {range.endDate}</span><h3>临边巡检统计</h3></div><button className="edge-button" type="button" onClick={() => setStatisticsVisible(false)}>关闭</button></header><div className="edge-drawer-body">{!statistics ? <Empty>暂无统计数据。</Empty> : <><div className="edge-stats">{[['应检', statistics.dueCount ?? 0], ['按时完成', statistics.onTimeCount ?? 0], ['逾期补检', statistics.lateCompletedCount ?? 0], ['仍未检', statistics.missedCount ?? 0], ['异常点位', statistics.abnormalTaskCount ?? 0], ['整改关闭率', `${Number(statistics.rectificationClosureRate ?? 0).toFixed(1)}%`]].map(([label, value]) => <div className="edge-stat" key={label}><span>{label}</span><strong>{value}</strong></div>)}</div><div className="edge-note">已完成 {statistics.completedCount ?? 0} 项；整改总数 {(statistics.openRectificationCount ?? 0) + (statistics.closedRectificationCount ?? 0)}，已关闭 {statistics.closedRectificationCount ?? 0}。</div><div className="edge-ranking-grid">{[['POINT_TYPE', '点位类型排行'], ['POINT', '具体点位排行'], ['PERSON', '巡检人员排行']].map(([dimension, title]) => <article key={dimension}><h4>{title}</h4>{!dimensionRows(dimension).length ? <Empty>暂无数据</Empty> : <div className="edge-table-wrap"><table className="edge-table compact"><thead><tr><th>名称</th><th>应检</th><th>按时</th><th>逾期</th><th>未检</th><th>异常</th><th>关闭率</th></tr></thead><tbody>{dimensionRows(dimension).map((row) => <tr key={`${dimension}-${row.dimensionKey}`}><td>{dimension === 'POINT' ? displayPointName(row.dimensionName) : row.dimensionName}</td><td>{row.dueCount || 0}</td><td>{row.onTimeCount || 0}</td><td>{row.lateCompletedCount || 0}</td><td>{row.missedCount || 0}</td><td>{row.abnormalCount || 0}</td><td>{Number(row.rectificationClosureRate || 0).toFixed(1)}%</td></tr>)}</tbody></table></div>}</article>)}</div></>}</div></aside></div>}

    {rectificationReassign && <Modal title="改派临边整改责任" onClose={() => setRectificationReassign(null)} onSubmit={saveRectificationReassign} busy={busy === 'rectification-reassign'}><div className="edge-form-grid"><Field label="整改人"><select value={rectificationReassign.assigneeId} onChange={(event) => setRectificationReassign({ ...rectificationReassign, assigneeId: event.target.value })}>{userOptions(rectifierUsers, rectificationReassign.assigneeId)}</select></Field><Field label="复查人"><select value={rectificationReassign.reviewerId} onChange={(event) => setRectificationReassign({ ...rectificationReassign, reviewerId: event.target.value })}>{userOptions(reviewerUsers, rectificationReassign.reviewerId)}</select></Field></div><Field label="改派原因（必填）"><textarea rows="3" maxLength="300" value={rectificationReassign.reason} onChange={(event) => setRectificationReassign({ ...rectificationReassign, reason: event.target.value })} /></Field></Modal>}
  </div>;
}
