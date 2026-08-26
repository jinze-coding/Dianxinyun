import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  archiveGeneralInspectionTemplate,
  appendGeneralInspectionCorrectionNote,
  cancelGeneralInspectionTasks,
  changeGeneralInspectionPlanStatus,
  changeGeneralInspectionPointStatus,
  copyGeneralInspectionTemplate,
  createGeneralInspectionCategory,
  createGeneralInspectionExport,
  createGeneralInspectionPlan,
  createGeneralInspectionPoint,
  createGeneralInspectionTemplate,
  downloadGeneralInspectionExport,
  getGeneralInspectionCategories,
  getGeneralInspectionDashboard,
  getGeneralInspectionExports,
  getGeneralInspectionFeature,
  getGeneralInspectionPlans,
  getGeneralInspectionPoints,
  getGeneralInspectionPointQr,
  getGeneralInspectionTask,
  getGeneralInspectionTasks,
  getGeneralInspectionTemplate,
  getGeneralInspectionTemplates,
  getGeneralInspectionUserOptions,
  previewGeneralInspectionPlan,
  previewGeneralInspectionTemplate,
  publishGeneralInspectionPlan,
  publishGeneralInspectionTemplate,
  reassignGeneralInspectionTask,
  rotateGeneralInspectionPointCode,
  correctGeneralInspectionTask,
  updateGeneralInspectionFeature,
  updateGeneralInspectionPlan,
  updateGeneralInspectionPoint,
  updateGeneralInspectionTemplate,
  voidGeneralInspectionForReinspection,
} from '../../services/generalInspection';
import { uploadFile } from '../../services/file';
import { hasProjectPermission, isPlatformAdmin } from '../../utils/permissions';
import './index.css';

const today = () => new Date().toISOString().slice(0, 10);
const monthStart = () => `${today().slice(0, 7)}-01`;
const futureDateTime = (minutes = 5) => {
  const value = new Date(Date.now() + minutes * 60000);
  const offset = value.getTimezoneOffset() * 60000;
  return new Date(value.getTime() - offset).toISOString().slice(0, 16);
};
const unwrap = (response, fallback) => {
  if (response?.code !== 200) throw new Error(response?.message || fallback);
  return response.data;
};
const statusText = (value) => ({
  DRAFT: '草稿', PUBLISHED: '已发布', PAUSED: '已暂停', ARCHIVED: '已归档', ACTIVE: '启用',
  INACTIVE: '停用', PENDING: '待执行', COMPLETED: '已完成', RECTIFICATION_PENDING: '整改中',
  CLOSED: '已闭环', CANCELLED: '已取消', SUCCEEDED: '已完成', RUNNING: '生成中', FAILED: '失败',
  EXPIRED: '已过期',
})[value] || value || '-';

function Modal({ title, children, onClose, onSubmit, submitText = '保存', busy }) {
  return <div className="gi-modal-backdrop" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
    <div className="gi-modal">
      <div className="gi-modal-header"><strong>{title}</strong><button className="gi-button" onClick={onClose}>关闭</button></div>
      {children}
      <div className="gi-modal-footer">
        <button className="gi-button" onClick={onClose}>取消</button>
        <button className="gi-button primary" disabled={busy} onClick={onSubmit}>{busy ? '处理中…' : submitText}</button>
      </div>
    </div>
  </div>;
}

function Field({ label, children }) {
  return <label className="gi-field"><span>{label}</span>{children}</label>;
}

const newTemplateItem = (index = 0) => ({
  itemKey: `ITEM_${String(index + 1).padStart(2, '0')}`, itemName: '', guidance: '', standardReference: '',
  allowNa: true, normalPhotoMin: 0, abnormalPhotoMin: 1, photoMax: 9,
  normalDescriptionRequired: false, abnormalDescriptionRequired: true,
});

const newPlanForm = (projectId) => ({
  projectId: Number(projectId), templateId: '', planName: '', expectedVersion: 0,
  config: {
    frequency: 'DAILY', weekdays: [1, 2, 3, 4, 5, 6, 7], monthDays: [1], effectiveStart: today(),
    effectiveEnd: '', earlyMinutes: 30,
    assigneeId: '', backupAssigneeIds: [], defaultRectifierId: '', rectificationDays: 3,
    reviewerId: '', backupReviewerIds: [],
    slots: [{ slotCode: 'DAY', slotName: '日间巡检', startTime: '08:00', dueTime: '18:00', dueDayOffset: 0 }],
    points: [],
  },
});

export default function GeneralInspectionManagement({ projectId, theme: T, currentUser, businessTarget }) {
  const [view, setView] = useState('templates');
  const [feature, setFeature] = useState(null);
  const [templates, setTemplates] = useState([]);
  const [points, setPoints] = useState([]);
  const [categories, setCategories] = useState([]);
  const [plans, setPlans] = useState([]);
  const [users, setUsers] = useState([]);
  const [tasks, setTasks] = useState([]);
  const [dashboard, setDashboard] = useState(null);
  const [dashboardDimension, setDashboardDimension] = useState('TYPE');
  const [exports, setExports] = useState([]);
  const [range, setRange] = useState({ startDate: monthStart(), endDate: today() });
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState('');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [templateEditor, setTemplateEditor] = useState(null);
  const [pointEditor, setPointEditor] = useState(null);
  const [planEditor, setPlanEditor] = useState(null);
  const [previewRows, setPreviewRows] = useState([]);
  const [selectedTasks, setSelectedTasks] = useState(new Set());
  const [cancelReason, setCancelReason] = useState('');
  const [pointQr, setPointQr] = useState(null);
  const [correctionEditor, setCorrectionEditor] = useState(null);
  const [auditEditor, setAuditEditor] = useState(null);
  const [templatePreview, setTemplatePreview] = useState(null);
  const [publishEditor, setPublishEditor] = useState(null);
  const [reassignEditor, setReassignEditor] = useState(null);

  const platformAdmin = isPlatformAdmin(currentUser);
  const canManage = hasProjectPermission(currentUser, projectId, 'inspection.manage');
  const canViewRecords = hasProjectPermission(currentUser, projectId, 'INSPECTION_RECORD_VIEW');
  const canSummary = hasProjectPermission(currentUser, projectId, 'SUMMARY_VIEW');
  const canExport = hasProjectPermission(currentUser, projectId, 'inspection.export', 'SUMMARY_EXPORT');
  const cssVars = {
    '--gi-text': T.textPrimary, '--gi-muted': T.textMuted, '--gi-border': T.borderColor, '--gi-card': T.cardBg,
    '--gi-surface': T.surface2, '--gi-accent': T.accent, '--gi-danger': T.danger, '--gi-success': T.success,
  };

  const loadFeature = useCallback(async () => {
    if (!projectId) return;
    setLoading(true); setError('');
    try { setFeature(unwrap(await getGeneralInspectionFeature(projectId), '项目开关加载失败')); }
    catch (err) { setError(err.message || '项目开关加载失败'); }
    finally { setLoading(false); }
  }, [projectId]);

  const loadConfig = useCallback(async () => {
    if (!projectId || !feature?.enabled || !canSummary) return;
    setLoading(true); setError('');
    try {
      const requests = [getGeneralInspectionTemplates(projectId), getGeneralInspectionPoints(projectId),
        getGeneralInspectionCategories(projectId), getGeneralInspectionPlans(projectId)];
      if (canManage) requests.push(getGeneralInspectionUserOptions(projectId));
      const [templateRes, pointRes, categoryRes, planRes, userRes] = await Promise.all(requests);
      setTemplates(unwrap(templateRes, '模板加载失败') || []);
      setPoints(unwrap(pointRes, '点位加载失败') || []);
      setCategories(unwrap(categoryRes, '类别加载失败') || []);
      setPlans(unwrap(planRes, '计划加载失败') || []);
      setUsers(userRes ? unwrap(userRes, '人员选项加载失败') || [] : []);
    } catch (err) { setError(err.message || '巡检配置加载失败'); }
    finally { setLoading(false); }
  }, [canManage, feature?.enabled, projectId]);

  const loadTasks = useCallback(async () => {
    if (!projectId || !feature?.enabled || !canViewRecords) return;
    setLoading(true); setError('');
    try {
      const response = await getGeneralInspectionTasks({ projectId, mine: false,
        startDate: range.startDate, endDate: range.endDate });
      setTasks(unwrap(response, '任务加载失败') || []);
    } catch (err) { setError(err.message || '任务加载失败'); }
    finally { setLoading(false); }
  }, [canViewRecords, feature?.enabled, projectId, range]);

  const loadDashboard = useCallback(async () => {
    if (!projectId || !feature?.enabled) return;
    setLoading(true); setError('');
    try {
      const [dashboardRes, exportRes] = await Promise.all([
        getGeneralInspectionDashboard({ projectId, ...range }),
        canExport ? getGeneralInspectionExports(projectId) : Promise.resolve({ code: 200, data: [] }),
      ]);
      setDashboard(unwrap(dashboardRes, '看板加载失败'));
      setExports(unwrap(exportRes, '导出任务加载失败') || []);
    } catch (err) { setError(err.message || '看板加载失败'); }
    finally { setLoading(false); }
  }, [canExport, canSummary, feature?.enabled, projectId, range]);

  useEffect(() => { setFeature(null); setView('templates'); setMessage(''); setError(''); loadFeature(); }, [loadFeature]);
  useEffect(() => { loadConfig(); }, [loadConfig]);
  useEffect(() => { if (view === 'tasks') loadTasks(); if (view === 'dashboard') loadDashboard(); }, [loadDashboard, loadTasks, view]);
  useEffect(() => {
    if (businessTarget?.routeCode === 'GENERAL_INSPECTION_EXPORT') setView('dashboard');
    else if (businessTarget?.routeCode?.startsWith('GENERAL_INSPECTION_')) setView('tasks');
  }, [businessTarget]);

  const run = async (key, action, success, refresh = loadConfig) => {
    setBusy(key); setError(''); setMessage('');
    try { await action(); setMessage(success); await refresh(); }
    catch (err) { setError(err.message || '操作失败'); }
    finally { setBusy(''); }
  };

  const toggleFeature = () => run('feature', async () => {
    const response = await updateGeneralInspectionFeature(projectId, { enabled: !Boolean(feature?.enabled), expectedVersion: feature?.version || 0 });
    setFeature(unwrap(response, '项目开关更新失败'));
  }, feature?.enabled ? '通用巡检已关闭，历史记录仍保留' : '通用巡检已为当前试点项目开启', loadFeature);

  const openTemplate = async (template = null, company = false) => {
    if (!template) {
      setTemplateEditor({ id: null, projectId: company ? null : Number(projectId), templateName: '', categoryName: '临边巡检',
        overallPhotoMin: 1, overallPhotoMax: 9, overallRemarkRequired: false,
        remark: '需结合项目施工方案审核后发布', expectedVersion: 0, items: [newTemplateItem()] });
      return;
    }
    setBusy(`template-${template.id}`);
    try {
      const detail = unwrap(await getGeneralInspectionTemplate(template.id), '模板详情加载失败');
      setTemplateEditor({ ...detail, projectId: detail.projectId, expectedVersion: detail.version,
        items: (detail.items || []).map((item) => ({ ...item, id: undefined, templateId: undefined, templateVersionId: undefined })) });
    } catch (err) { setError(err.message); }
    finally { setBusy(''); }
  };

  const saveTemplate = () => run('template-save', async () => {
    const payload = { ...templateEditor, id: undefined, scopeType: undefined, status: undefined,
      currentVersionId: undefined, currentVersionNo: undefined, effectiveTime: undefined, canManage: undefined };
    const response = templateEditor.id
      ? await updateGeneralInspectionTemplate(templateEditor.id, payload)
      : await createGeneralInspectionTemplate(payload);
    unwrap(response, '模板保存失败'); setTemplateEditor(null);
  }, '模板草稿已保存');

  const previewTemplate = async (template) => {
    setBusy(`preview-template-${template.id}`); setError('');
    try { setTemplatePreview(unwrap(await previewGeneralInspectionTemplate(template.id), '模板预览失败')); }
    catch (err) { setError(err.message || '模板预览失败'); }
    finally { setBusy(''); }
  };

  const publishVersion = () => run('publish-version', async () => {
    const action = publishEditor.type === 'TEMPLATE' ? publishGeneralInspectionTemplate : publishGeneralInspectionPlan;
    unwrap(await action(publishEditor.id, { expectedVersion: publishEditor.version, effectiveTime: publishEditor.effectiveTime }), '版本发布失败');
    setPublishEditor(null);
  }, publishEditor?.type === 'TEMPLATE' ? '模板新版本已发布，将在指定时间生效' : '计划版本已发布，任务生成器将按生效时间切换');

  const copyTemplate = (template) => run(`copy-template-${template.id}`, async () => {
    const response = await copyGeneralInspectionTemplate(template.id, {
      projectId: Number(projectId), templateName: `${template.templateName}-项目副本`,
    }); unwrap(response, '模板复制失败');
  }, '参考模板已复制为项目草稿，请审核后发布');

  const savePoint = () => run('point-save', async () => {
    const payload = { ...pointEditor, id: undefined, categoryName: undefined, publicCode: undefined,
      qrVersion: undefined, status: undefined, version: undefined,
      expectedVersion: pointEditor.id ? pointEditor.version : undefined };
    const response = pointEditor.id ? await updateGeneralInspectionPoint(pointEditor.id, payload)
      : await createGeneralInspectionPoint(payload);
    unwrap(response, '点位保存失败'); setPointEditor(null);
  }, '点位已保存');

  const uploadPointPhotos = async (files) => {
    if (!files?.length) return;
    setBusy('point-upload');
    try {
      const ids = [];
      for (const file of [...files].slice(0, 9)) {
        const result = await uploadFile({ file, projectId, businessType: 'INSPECTION_CUSTOM_POINT_PENDING', fileType: 'image' });
        ids.push(unwrap(result, '参考照片上传失败').id);
      }
      setPointEditor((current) => ({ ...current, referencePhotoFileIds: [...(current.referencePhotoFileIds || []), ...ids].slice(0, 9) }));
    } catch (err) { setError(err.message); }
    finally { setBusy(''); }
  };

  const openPointQr = async (point) => {
    setBusy(`point-qr-${point.id}`); setError('');
    try { setPointQr(unwrap(await getGeneralInspectionPointQr(point.id), '点位二维码加载失败')); }
    catch (err) { setError(err.message || '点位二维码加载失败'); }
    finally { setBusy(''); }
  };

  const downloadPointQr = () => {
    if (!pointQr?.svg) return;
    const url = URL.createObjectURL(new Blob([pointQr.svg], { type: 'image/svg+xml;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url; link.download = `${pointQr.pointCode}_巡检码_V${pointQr.qrVersion}.svg`; link.click();
    URL.revokeObjectURL(url);
  };

  const openCorrection = async (task) => {
    setBusy(`task-detail-${task.id}`); setError('');
    try {
      const detail = unwrap(await getGeneralInspectionTask(task.id), '巡检详情加载失败');
      setCorrectionEditor({ ...detail, reason: '', overallPhotoFileIds: detail.overallPhotoFileIds || [],
        items: (detail.items || []).map((item) => ({ ...item, photoFileIds: item.photoFileIds || [], requirement: '' })) });
    } catch (err) { setError(err.message || '巡检详情加载失败'); }
    finally { setBusy(''); }
  };

  const uploadCorrectionPhotos = async (files, itemIndex = null) => {
    if (!files?.length || !correctionEditor) return;
    setBusy('correction-upload'); setError('');
    try {
      const ids = [];
      for (const file of [...files].slice(0, 9)) {
        const result = await uploadFile({ file, projectId, businessType: 'INSPECTION_CUSTOM_TASK_PENDING', fileType: 'image' });
        ids.push(unwrap(result, '纠错照片上传失败').id);
      }
      setCorrectionEditor((current) => {
        if (itemIndex === null) return { ...current, overallPhotoFileIds: [...current.overallPhotoFileIds, ...ids].slice(0, 9) };
        const items = [...current.items];
        items[itemIndex] = { ...items[itemIndex], photoFileIds: [...items[itemIndex].photoFileIds, ...ids].slice(0, items[itemIndex].photoMax || 9) };
        return { ...current, items };
      });
    } catch (err) { setError(err.message || '纠错照片上传失败'); }
    finally { setBusy(''); }
  };

  const saveCorrection = () => run('task-correct', async () => {
    if (!correctionEditor.reason.trim()) throw new Error('请填写纠错原因');
    const payload = { expectedVersion: correctionEditor.version, reason: correctionEditor.reason.trim(),
      overallPhotoFileIds: correctionEditor.overallPhotoFileIds, remark: correctionEditor.remark || '',
      publicRemark: correctionEditor.publicRemark || '', items: correctionEditor.items.map((item) => ({
        taskItemId: item.id, result: item.result, description: item.description || '', photoFileIds: item.photoFileIds || [],
        rectifierId: item.rectifierId || null, deadline: item.deadline || null, requirement: item.requirement || '',
      })) };
    unwrap(await correctGeneralInspectionTask(correctionEditor.id, payload), '巡检记录纠错失败');
    setCorrectionEditor(null);
  }, '巡检记录已纠错，修改前后快照已留痕', loadTasks);

  const submitAuditAction = () => run('task-audit-action', async () => {
    if (!auditEditor?.reason?.trim()) throw new Error('请填写原因');
    if (auditEditor.mode === 'NOTE') {
      unwrap(await appendGeneralInspectionCorrectionNote(auditEditor.task.id, {
        expectedVersion: auditEditor.task.version, reason: auditEditor.reason.trim(),
        remark: auditEditor.remark, publicRemark: auditEditor.publicRemark,
      }), '更正说明保存失败');
    } else {
      unwrap(await voidGeneralInspectionForReinspection(auditEditor.task.id, {
        expectedVersion: auditEditor.task.version, reason: auditEditor.reason.trim(),
      }), '作废重检失败');
    }
    setAuditEditor(null);
  }, auditEditor?.mode === 'NOTE' ? '更正说明已追加' : '原记录已作废，并生成新的重检任务', loadTasks);

  const savePlan = () => run('plan-save', async () => {
    const payload = { ...planEditor, id: undefined, planCode: undefined, status: undefined,
      currentVersionId: undefined, currentVersionNo: undefined, effectiveTime: undefined,
      generatedThroughTime: undefined, version: undefined,
      expectedVersion: planEditor.id ? planEditor.version : 0,
      config: { ...planEditor.config, effectiveEnd: planEditor.config.effectiveEnd || null },
    };
    const response = planEditor.id ? await updateGeneralInspectionPlan(planEditor.id, payload)
      : await createGeneralInspectionPlan(payload);
    unwrap(response, '计划保存失败'); setPlanEditor(null); setPreviewRows([]);
  }, '计划草稿已保存');

  const previewPlan = async () => {
    setBusy('plan-preview'); setError('');
    try {
      const payload = { ...planEditor, expectedVersion: planEditor.id ? planEditor.version : 0,
        config: { ...planEditor.config, effectiveEnd: planEditor.config.effectiveEnd || null } };
      setPreviewRows(unwrap(await previewGeneralInspectionPlan(payload), '计划预览失败') || []);
    } catch (err) { setError(err.message); }
    finally { setBusy(''); }
  };

  const publishPlan = (plan) => setPublishEditor({ type: 'PLAN', id: plan.id, version: plan.version,
    name: plan.planName, effectiveTime: futureDateTime() });

  const toggleTaskSelection = (id) => setSelectedTasks((current) => {
    const next = new Set(current); if (next.has(id)) next.delete(id); else next.add(id); return next;
  });

  const cancelTasks = () => run('cancel-tasks', async () => {
    if (!selectedTasks.size) throw new Error('请先选择待取消任务');
    if (!cancelReason.trim()) throw new Error('请填写取消原因');
    unwrap(await cancelGeneralInspectionTasks({ taskIds: [...selectedTasks], reason: cancelReason.trim() }), '任务取消失败');
    setSelectedTasks(new Set()); setCancelReason('');
  }, '所选未提交任务已取消', loadTasks);

  const saveReassignment = () => run('task-reassign', async () => {
    if (!reassignEditor.reason.trim()) throw new Error('请填写改派原因');
    if (!reassignEditor.assigneeId && !reassignEditor.reviewerId) throw new Error('请至少选择新的主巡检人或主复查人');
    unwrap(await reassignGeneralInspectionTask(reassignEditor.task.id, {
      expectedVersion: reassignEditor.task.version, reason: reassignEditor.reason.trim(),
      assigneeId: reassignEditor.assigneeId || null, reviewerId: reassignEditor.reviewerId || null,
    }), '任务改派失败');
    setReassignEditor(null);
  }, '任务已明确改派', loadTasks);

  const createExport = () => run('export-create', async () => {
    unwrap(await createGeneralInspectionExport({ projectId: Number(projectId), ...range }), '导出任务创建失败');
  }, '导出任务已创建，完成后会形成站内通知', loadDashboard);

  const downloadExport = async (job) => {
    setBusy(`download-${job.id}`);
    try {
      const blob = await downloadGeneralInspectionExport(job.id);
      const url = URL.createObjectURL(blob); const link = document.createElement('a');
      link.href = url; link.download = `通用巡检_${job.startDate}_${job.endDate}.xlsx`; link.click(); URL.revokeObjectURL(url);
    } catch (err) { setError(err.message); }
    finally { setBusy(''); }
  };

  const assignmentUsers = (kind) => users.filter((user) => Boolean(user[kind]));
  const pointMap = useMemo(() => new Map(points.map((point) => [Number(point.id), point])), [points]);
  const userOptions = (kind) => <><option value="">请选择</option>{assignmentUsers(kind).map((user) =>
    <option key={user.userId} value={user.userId}>{user.userName}</option>)}</>;

  if (!projectId) return <div className="gi-card">请先选择项目</div>;
  if (loading && !feature) return <div className="gi-card">正在加载通用巡检配置…</div>;

  return <div className="general-inspection" style={cssVars}>
    {error && <div className="gi-error">{error}</div>}
    {message && <div className="gi-success">{message}</div>}
    <div className="gi-card">
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center' }}>
        <div><h3>项目通用巡检</h3><div className="gi-muted">默认关闭；电箱巡检保持原专用流程，通用巡检从项目模板、点位和周期计划生成任务。</div></div>
        <div><span className="gi-badge">{feature?.enabled ? '试运行已开启' : '未开启'}</span>{platformAdmin && <button className="gi-button primary" disabled={busy === 'feature'} onClick={toggleFeature} style={{ marginLeft: 8 }}>{feature?.enabled ? '关闭试点' : '开启试点'}</button>}</div>
      </div>
    </div>
    {!feature?.enabled ? <div className="gi-card"><strong>当前项目尚未启用通用巡检</strong><div className="gi-muted" style={{ marginTop: 7 }}>历史电箱巡检不受影响。由平台管理员选择试点项目开启后，才可维护模板、点位和计划。</div></div> : <>
      <div className="gi-toolbar">
        {[['templates', '模板库', true], ['points', '点位台账', true], ['plans', '计划管理', true], ['tasks', '任务记录', canViewRecords], ['dashboard', '看板与导出', canSummary]].filter(([, , visible]) => visible).map(([key, label]) =>
          <button key={key} className={view === key ? 'active' : ''} onClick={() => setView(key)}>{label}</button>)}
        <span className="gi-muted">{loading ? '正在刷新…' : ''}</span>
      </div>

      {view === 'templates' && <div className="gi-card">
        <div className="gi-toolbar"><h3 style={{ marginRight: 'auto' }}>公司参考模板与项目模板</h3>{platformAdmin && <button className="gi-button" onClick={() => openTemplate(null, true)}>新建公司模板</button>}{canManage && <button className="gi-button primary" onClick={() => openTemplate()}>新建项目模板</button>}</div>
        <table className="gi-table"><thead><tr><th>范围</th><th>模板</th><th>类别</th><th>版本</th><th>状态</th><th>提示</th><th>操作</th></tr></thead><tbody>
          {templates.map((template) => <tr key={template.id}><td>{template.scopeType === 'COMPANY' ? '公司参考' : '当前项目'}</td><td>{template.templateName}</td><td>{template.categoryName || '-'}</td><td>{template.currentVersionNo ? `V${template.currentVersionNo}` : '-'}</td><td><span className="gi-badge">{statusText(template.status)}</span></td><td className="gi-muted">{template.remark || '-'}</td><td className="actions">
            {template.scopeType === 'COMPANY' && canManage && <button className="gi-button" onClick={() => copyTemplate(template)}>复制到项目</button>}
            {template.canManage && template.status !== 'ARCHIVED' && <><button className="gi-button" onClick={() => openTemplate(template)}>编辑</button><button className="gi-button" onClick={() => previewTemplate(template)}>预览</button><button className="gi-button primary" onClick={() => setPublishEditor({ type: 'TEMPLATE', id: template.id, version: template.version, name: template.templateName, effectiveTime: futureDateTime() })}>发布新版本</button><button className="gi-button danger" onClick={() => run(`archive-${template.id}`, async () => unwrap(await archiveGeneralInspectionTemplate(template.id, { expectedVersion: template.version, reason: '项目不再使用' }), '归档失败'), '模板已归档')}>归档</button></>}
          </td></tr>)}
        </tbody></table>
      </div>}

      {view === 'points' && <div className="gi-card">
        <div className="gi-toolbar"><h3 style={{ marginRight: 'auto' }}>巡检点位台账</h3>{canManage && <><button className="gi-button" onClick={async () => { const name = window.prompt('新增点位类别名称'); if (name?.trim()) await run('category', async () => unwrap(await createGeneralInspectionCategory({ projectId: Number(projectId), categoryName: name.trim() }), '类别新增失败'), '点位类别已新增'); }}>新增类别</button><button className="gi-button primary" onClick={() => setPointEditor({ projectId: Number(projectId), pointCode: '', pointName: '', categoryId: '', areaName: '', buildingName: '', floorName: '', locationDesc: '', riskNote: '', referencePhotoFileIds: [], qrEnabled: false, publicAccessEnabled: false })}>新增点位</button></>}</div>
        <table className="gi-table"><thead><tr><th>编码/名称</th><th>类别</th><th>区域位置</th><th>二维码</th><th>公开</th><th>状态</th><th>操作</th></tr></thead><tbody>
          {points.map((point) => <tr key={point.id}><td><strong>{point.pointCode}</strong><br />{point.pointName}</td><td>{point.categoryName || '-'}</td><td>{[point.areaName, point.buildingName, point.floorName, point.locationDesc].filter(Boolean).join(' / ') || '-'}</td><td>{point.qrEnabled ? `强制扫码 · V${point.qrVersion}` : '不强制'}</td><td>{point.publicAccessEnabled ? '开启' : '关闭'}</td><td><span className="gi-badge">{statusText(point.status)}</span></td><td className="actions">{canManage && <><button className="gi-button" onClick={() => setPointEditor({ ...point, expectedVersion: point.version, referencePhotoFileIds: point.referencePhotoFileIds ? String(point.referencePhotoFileIds).split(',').map(Number) : [] })}>编辑</button>{point.qrEnabled && <button className="gi-button" onClick={() => openPointQr(point)}>查看码</button>}<button className="gi-button" onClick={() => run(`rotate-${point.id}`, async () => unwrap(await rotateGeneralInspectionPointCode(point.id, { expectedVersion: point.version, reason: '二维码损坏或换码' }), '换码失败'), '点位二维码已换码，未提交任务已同步新版本')}>换码</button><button className="gi-button danger" onClick={() => run(`status-${point.id}`, async () => unwrap(await changeGeneralInspectionPointStatus(point.id, point.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE', { expectedVersion: point.version, reason: '项目现场状态调整' }), '状态更新失败'), '点位状态已更新')}>{point.status === 'ACTIVE' ? '停用' : '启用'}</button></>}</td></tr>)}
        </tbody></table>
      </div>}

      {view === 'plans' && <div className="gi-card">
        <div className="gi-toolbar"><h3 style={{ marginRight: 'auto' }}>周期计划与任务预览</h3>{canManage && <button className="gi-button primary" onClick={() => setPlanEditor(newPlanForm(projectId))}>新建计划</button>}</div>
        <table className="gi-table"><thead><tr><th>计划</th><th>模板</th><th>频率</th><th>点位/时段</th><th>版本</th><th>状态</th><th>操作</th></tr></thead><tbody>
          {plans.map((plan) => <tr key={plan.id}><td>{plan.planName}<div className="gi-muted">{plan.planCode}</div></td><td>{templates.find((item) => Number(item.id) === Number(plan.templateId))?.templateName || plan.templateId}</td><td>{plan.config?.frequency}</td><td>{plan.config?.points?.length || 0} 点位 / {plan.config?.slots?.length || 0} 时段</td><td>{plan.currentVersionNo ? `V${plan.currentVersionNo}` : '-'}</td><td><span className="gi-badge">{statusText(plan.status)}</span></td><td className="actions">{canManage && plan.status !== 'ARCHIVED' && <><button className="gi-button" onClick={() => setPlanEditor({ ...plan, expectedVersion: plan.version, config: { ...plan.config, effectiveEnd: plan.config?.effectiveEnd || '' } })}>编辑</button><button className="gi-button primary" onClick={() => publishPlan(plan)}>发布</button>{plan.currentVersionId && <button className="gi-button" onClick={() => run(`plan-state-${plan.id}`, async () => unwrap(await changeGeneralInspectionPlanStatus(plan.id, plan.status === 'PAUSED' ? 'PUBLISHED' : 'PAUSED', { expectedVersion: plan.version, reason: '项目计划状态调整' }), '计划状态更新失败'), '计划状态已更新')}>{plan.status === 'PAUSED' ? '恢复' : '暂停'}</button>}<button className="gi-button danger" onClick={() => run(`plan-archive-${plan.id}`, async () => unwrap(await changeGeneralInspectionPlanStatus(plan.id, 'ARCHIVED', { expectedVersion: plan.version, reason: '计划归档停止后续任务生成' }), '计划归档失败'), '计划已归档，历史任务保留')}>归档</button></>}</td></tr>)}
        </tbody></table>
      </div>}

      {view === 'tasks' && <div className="gi-card">
        <div className="gi-toolbar"><h3 style={{ marginRight: 'auto' }}>通用巡检任务与记录</h3><Field label="开始日期"><input type="date" value={range.startDate} onChange={(event) => setRange({ ...range, startDate: event.target.value })} /></Field><Field label="结束日期"><input type="date" value={range.endDate} onChange={(event) => setRange({ ...range, endDate: event.target.value })} /></Field><button onClick={loadTasks}>查询</button></div>
        {canManage && <div className="gi-toolbar"><input style={{ minWidth: 260, padding: 7 }} placeholder="批量取消原因（必填）" value={cancelReason} onChange={(event) => setCancelReason(event.target.value)} /><button className="gi-button danger" disabled={!selectedTasks.size || busy === 'cancel-tasks'} onClick={cancelTasks}>取消所选未提交任务</button></div>}
        <table className="gi-table"><thead><tr><th></th><th>日期/时段</th><th>点位</th><th>模板/计划</th><th>主巡检人</th><th>截止时间</th><th>状态</th><th>异常</th><th>管理操作</th></tr></thead><tbody>
          {tasks.map((task) => <tr key={task.id}><td>{canManage && task.status === 'PENDING' && <input type="checkbox" checked={selectedTasks.has(task.id)} onChange={() => toggleTaskSelection(task.id)} />}</td><td>{task.occurrenceDate}<br />{task.slotName}{task.revisionNo > 0 && <div className="gi-muted">重检第 {task.revisionNo} 次</div>}</td><td>{task.pointCode} · {task.pointName}<div className="gi-muted">{task.locationDesc}</div></td><td>{task.templateName}<div className="gi-muted">{task.planName}</div></td><td>{task.assigneeName}</td><td>{String(task.dueTime || '').replace('T', ' ').slice(0, 16)}</td><td><span className="gi-badge">{statusText(task.displayStatus || task.status)}</span></td><td>{task.abnormalCount || 0}</td><td className="actions">{canManage && !['CANCELLED', 'CLOSED'].includes(task.status) && <button className="gi-button" onClick={() => setReassignEditor({ task, assigneeId: '', reviewerId: '', reason: '' })}>改派</button>}{canManage && ['COMPLETED', 'RECTIFICATION_PENDING', 'CLOSED'].includes(task.status) && <><button className="gi-button" onClick={() => openCorrection(task)}>纠错</button><button className="gi-button" onClick={() => setAuditEditor({ mode: 'NOTE', task, reason: '', remark: task.remark || '', publicRemark: task.publicRemark || '' })}>追加说明</button><button className="gi-button danger" onClick={() => setAuditEditor({ mode: 'VOID', task, reason: '' })}>作废重检</button></>}</td></tr>)}
        </tbody></table>
      </div>}

      {view === 'dashboard' && <>
        <div className="gi-card"><div className="gi-toolbar"><h3 style={{ marginRight: 'auto' }}>统一履约与异常闭环看板</h3><Field label="开始日期"><input type="date" value={range.startDate} onChange={(event) => setRange({ ...range, startDate: event.target.value })} /></Field><Field label="结束日期"><input type="date" value={range.endDate} onChange={(event) => setRange({ ...range, endDate: event.target.value })} /></Field><button onClick={loadDashboard}>刷新</button>{canExport && <button className="gi-button primary" onClick={createExport}>创建含图 Excel 导出</button>}</div>
          <div className="gi-grid">{[['应检', dashboard?.dueCount], ['已检', dashboard?.completedCount], ['按时完成', dashboard?.onTimeCount], ['逾期补检', dashboard?.lateCompletedCount], ['仍未检', dashboard?.missedCount], ['异常任务', dashboard?.abnormalTaskCount], ['未闭环整改', dashboard?.openRectificationCount], ['整改关闭率', `${dashboard?.rectificationClosureRate || 0}%`]].map(([label, value]) => <div className="gi-stat" key={label}><span className="gi-muted">{label}</span><strong>{value ?? 0}</strong></div>)}</div>
          <div className="gi-muted" style={{ marginTop: 12 }}>电箱：应检 {dashboard?.electricBoxDueCount || 0} / 已检 {dashboard?.electricBoxCompletedCount || 0} / 未检 {dashboard?.electricBoxMissedCount || 0} / 异常 {dashboard?.electricBoxAbnormalCount || 0}；通用：应检 {dashboard?.generalDueCount || 0} / 已检 {dashboard?.generalCompletedCount || 0} / 未检 {dashboard?.generalMissedCount || 0} / 异常 {dashboard?.generalAbnormalCount || 0}。</div>
          <div className="gi-toolbar" style={{ marginTop: 14 }}><strong>统计维度</strong><select value={dashboardDimension} onChange={(event) => setDashboardDimension(event.target.value)}><option value="TYPE">巡检类型</option><option value="POINT">点位</option><option value="PERSON">人员</option><option value="DATE">日期</option></select></div>
          <table className="gi-table"><thead><tr><th>维度项</th><th>应检</th><th>完成</th><th>按时</th><th>逾期补检</th><th>仍未检</th><th>异常</th><th>整改关闭率</th><th>平均关闭时长</th></tr></thead><tbody>{(dashboard?.breakdowns || []).filter((row) => row.dimension === dashboardDimension).map((row) => <tr key={`${row.dimension}:${row.dimensionKey}`}><td>{row.dimensionName}</td><td>{row.dueCount}</td><td>{row.completedCount}</td><td>{row.onTimeCount}</td><td>{row.lateCompletedCount}</td><td>{row.missedCount}</td><td>{row.abnormalCount}</td><td>{row.rectificationClosureRate || 0}%</td><td>{row.averageCloseHours || 0} 小时</td></tr>)}</tbody></table>
        </div>
        {canExport && <div className="gi-card"><h3>异步导出任务</h3><table className="gi-table"><thead><tr><th>范围</th><th>状态</th><th>进度</th><th>过期时间</th><th>操作</th></tr></thead><tbody>{exports.map((job) => <tr key={job.id}><td>{job.startDate} 至 {job.endDate}</td><td>{statusText(job.status)}{job.errorMessage && <div className="gi-error">{job.errorMessage}</div>}</td><td>{job.progress}%</td><td>{String(job.expiresTime || '-').replace('T', ' ').slice(0, 16)}</td><td>{job.status === 'SUCCEEDED' && <button className="gi-button" onClick={() => downloadExport(job)}>下载</button>}</td></tr>)}</tbody></table></div>}
      </>}
    </>}

    {templatePreview && <Modal title={`模板预览 · ${templatePreview.templateName}`} onClose={() => setTemplatePreview(null)} onSubmit={() => setTemplatePreview(null)} submitText="确认">
      <div className="gi-card"><strong>{templatePreview.categoryName || '通用巡检'}</strong><div className="gi-muted">整体照片 {templatePreview.overallPhotoMin || 0}—{templatePreview.overallPhotoMax || 9} 张；{templatePreview.overallRemarkRequired ? '总备注必填' : '总备注选填'}</div><div className="gi-muted">{templatePreview.remark || '无发布提示'}</div></div>
      {(templatePreview.items || []).map((item, index) => <div className="gi-item-editor" key={item.id || item.itemKey}><strong>{index + 1}. {item.itemName}</strong><span>{item.allowNa ? '允许不适用' : '不可不适用'}</span><span>正常照片≥{item.normalPhotoMin || 0}</span><span>异常照片≥{item.abnormalPhotoMin || 0}</span><span>最多{item.photoMax || 9}张</span><div className="gi-muted">{item.guidance || '无检查提示'}{item.standardReference ? ` · ${item.standardReference}` : ''}</div></div>)}
    </Modal>}

    {publishEditor && <Modal title={`发布${publishEditor.type === 'TEMPLATE' ? '模板' : '计划'}版本 · ${publishEditor.name}`} onClose={() => setPublishEditor(null)} onSubmit={publishVersion} submitText="确认发布" busy={busy === 'publish-version'}>
      <Field label="未来生效时间"><input type="datetime-local" value={publishEditor.effectiveTime} min={futureDateTime(1)} onChange={(event) => setPublishEditor({ ...publishEditor, effectiveTime: event.target.value })} /></Field>
      <div className="gi-muted">发布后版本不可变；已生成任务继续保留原快照，生效时间后的新任务自动切换。</div>
    </Modal>}

    {pointQr && <Modal title={`${pointQr.pointCode} · 点位巡检码 V${pointQr.qrVersion}`} onClose={() => setPointQr(null)} onSubmit={downloadPointQr} submitText="下载 SVG">
      <div style={{ display: 'grid', placeItems: 'center', gap: 12 }}>
        <img style={{ width: 280, height: 280 }} src={`data:image/svg+xml;charset=utf-8,${encodeURIComponent(pointQr.svg)}`} alt={`${pointQr.pointName}巡检二维码`} />
        <strong>{pointQr.pointName}</strong><code>{pointQr.sceneCode}</code>
        <div className="gi-muted">点位启用二维码后，关联任务必须现场扫码；二维码损坏应换码，不能人工跳过。</div>
      </div>
    </Modal>}

    {correctionEditor && <Modal title={`管理纠错 · ${correctionEditor.pointCode} ${correctionEditor.pointName}`} onClose={() => setCorrectionEditor(null)} onSubmit={saveCorrection} submitText="保存纠错" busy={busy === 'task-correct'}>
      <Field label="纠错原因（必填）"><textarea rows="2" value={correctionEditor.reason} onChange={(event) => setCorrectionEditor({ ...correctionEditor, reason: event.target.value })} /></Field>
      <div className="gi-form-grid"><Field label="总备注"><input value={correctionEditor.remark || ''} onChange={(event) => setCorrectionEditor({ ...correctionEditor, remark: event.target.value })} /></Field><Field label="公开备注"><input value={correctionEditor.publicRemark || ''} onChange={(event) => setCorrectionEditor({ ...correctionEditor, publicRemark: event.target.value })} /></Field><Field label={`整体照片 ID（${correctionEditor.overallPhotoFileIds.length}/${correctionEditor.overallPhotoMax || 9}）`}><input type="file" accept="image/*" multiple onChange={(event) => uploadCorrectionPhotos(event.target.files)} /></Field></div>
      <div className="gi-muted">已绑定整体照片：{correctionEditor.overallPhotoFileIds.join('、') || '无'}</div>
      {(correctionEditor.items || []).map((item, index) => <div className="gi-item-editor" key={item.id}>
        <strong>{item.itemName}</strong>
        <select value={item.result || ''} onChange={(event) => { const items = [...correctionEditor.items]; items[index] = { ...item, result: event.target.value }; setCorrectionEditor({ ...correctionEditor, items }); }}><option value="NORMAL">正常</option><option value="ABNORMAL">异常</option>{item.allowNa && <option value="NA">不适用</option>}</select>
        <input value={item.description || ''} placeholder="检查说明" onChange={(event) => { const items = [...correctionEditor.items]; items[index] = { ...item, description: event.target.value }; setCorrectionEditor({ ...correctionEditor, items }); }} />
        <input type="file" accept="image/*" multiple onChange={(event) => uploadCorrectionPhotos(event.target.files, index)} />
        <span className="gi-muted">照片 {item.photoFileIds.length}/{item.photoMax || 9}</span>
        {item.result === 'ABNORMAL' && <><select value={item.rectifierId || ''} onChange={(event) => { const items = [...correctionEditor.items]; items[index] = { ...item, rectifierId: event.target.value ? Number(event.target.value) : null }; setCorrectionEditor({ ...correctionEditor, items }); }}>{userOptions('canRectify')}</select><input type="date" value={item.deadline || ''} onChange={(event) => { const items = [...correctionEditor.items]; items[index] = { ...item, deadline: event.target.value }; setCorrectionEditor({ ...correctionEditor, items }); }} /><input value={item.requirement || ''} placeholder="整改要求" onChange={(event) => { const items = [...correctionEditor.items]; items[index] = { ...item, requirement: event.target.value }; setCorrectionEditor({ ...correctionEditor, items }); }} /></>}
      </div>)}
      <div className="gi-muted">若任一关联整改已提交反馈，系统会拒绝修改检查结果；此时只能追加更正说明，或作废后生成新的重检任务。</div>
    </Modal>}

    {auditEditor && <Modal title={auditEditor.mode === 'NOTE' ? '追加更正说明' : '作废并重新巡检'} onClose={() => setAuditEditor(null)} onSubmit={submitAuditAction} submitText={auditEditor.mode === 'NOTE' ? '保存说明' : '确认作废并生成重检'} busy={busy === 'task-audit-action'}>
      <Field label={auditEditor.mode === 'NOTE' ? '更正说明（必填）' : '作废重检原因（必填）'}><textarea rows="3" value={auditEditor.reason} onChange={(event) => setAuditEditor({ ...auditEditor, reason: event.target.value })} /></Field>
      {auditEditor.mode === 'NOTE' ? <div className="gi-form-grid"><Field label="总备注（可同步更正）"><input value={auditEditor.remark || ''} onChange={(event) => setAuditEditor({ ...auditEditor, remark: event.target.value })} /></Field><Field label="公开备注（可同步更正）"><input value={auditEditor.publicRemark || ''} onChange={(event) => setAuditEditor({ ...auditEditor, publicRemark: event.target.value })} /></Field></div> : <div className="gi-error">原记录及关联整改将保留审计快照并标记作废；系统会复制原任务快照生成新的待巡检任务。</div>}
    </Modal>}

    {reassignEditor && <Modal title={`明确改派 · ${reassignEditor.task.pointName}`} onClose={() => setReassignEditor(null)} onSubmit={saveReassignment} submitText="确认改派" busy={busy === 'task-reassign'}>
      <div className="gi-form-grid"><Field label="新的主巡检人（可不改）"><select value={reassignEditor.assigneeId} onChange={(event) => setReassignEditor({ ...reassignEditor, assigneeId: event.target.value ? Number(event.target.value) : '' })}>{userOptions('canSubmit')}</select></Field><Field label="新的主复查人（可不改）"><select value={reassignEditor.reviewerId} onChange={(event) => setReassignEditor({ ...reassignEditor, reviewerId: event.target.value ? Number(event.target.value) : '' })}>{userOptions('canReview')}</select></Field></div>
      <Field label="改派原因（必填）"><textarea rows="3" value={reassignEditor.reason} onChange={(event) => setReassignEditor({ ...reassignEditor, reason: event.target.value })} /></Field>
      <div className="gi-muted">系统不会把主责任务自动转给备选人；本操作会记录明确的改派审计。</div>
    </Modal>}

    {templateEditor && <Modal title={templateEditor.id ? '编辑模板草稿' : '新建项目模板'} onClose={() => setTemplateEditor(null)} onSubmit={saveTemplate} busy={busy === 'template-save'}>
      <div className="gi-form-grid"><Field label="模板名称"><input value={templateEditor.templateName || ''} onChange={(event) => setTemplateEditor({ ...templateEditor, templateName: event.target.value })} /></Field><Field label="业务类别"><input value={templateEditor.categoryName || ''} onChange={(event) => setTemplateEditor({ ...templateEditor, categoryName: event.target.value })} /></Field><Field label="整体照片最少"><input type="number" min="0" max="9" value={templateEditor.overallPhotoMin ?? 0} onChange={(event) => setTemplateEditor({ ...templateEditor, overallPhotoMin: Number(event.target.value) })} /></Field><Field label="整体照片最多"><input type="number" min="0" max="9" value={templateEditor.overallPhotoMax ?? 9} onChange={(event) => setTemplateEditor({ ...templateEditor, overallPhotoMax: Number(event.target.value) })} /></Field></div>
      <Field label="发布提示/备注"><textarea rows="2" value={templateEditor.remark || ''} onChange={(event) => setTemplateEditor({ ...templateEditor, remark: event.target.value })} /></Field>
      <label style={{ fontSize: 12 }}><input type="checkbox" checked={Boolean(templateEditor.overallRemarkRequired)} onChange={(event) => setTemplateEditor({ ...templateEditor, overallRemarkRequired: event.target.checked })} /> 总备注必填</label>
      <div className="gi-toolbar" style={{ marginTop: 14 }}><strong>检查项（最多50项）</strong><button className="gi-button" onClick={() => setTemplateEditor({ ...templateEditor, items: [...templateEditor.items, newTemplateItem(templateEditor.items.length)] })}>增加检查项</button></div>
      {templateEditor.items.map((item, index) => <div className="gi-item-editor" key={`${item.itemKey}-${index}`}><input placeholder="检查项编码" value={item.itemKey || ''} onChange={(event) => { const items = [...templateEditor.items]; items[index] = { ...item, itemKey: event.target.value }; setTemplateEditor({ ...templateEditor, items }); }} /><input placeholder="检查项名称" value={item.itemName || ''} onChange={(event) => { const items = [...templateEditor.items]; items[index] = { ...item, itemName: event.target.value }; setTemplateEditor({ ...templateEditor, items }); }} /><select value={item.allowNa ? '1' : '0'} onChange={(event) => { const items = [...templateEditor.items]; items[index] = { ...item, allowNa: event.target.value === '1' }; setTemplateEditor({ ...templateEditor, items }); }}><option value="1">允许不适用</option><option value="0">禁止不适用</option></select><input title="正常最少照片" type="number" min="0" max="9" value={item.normalPhotoMin ?? 0} onChange={(event) => { const items = [...templateEditor.items]; items[index] = { ...item, normalPhotoMin: Number(event.target.value) }; setTemplateEditor({ ...templateEditor, items }); }} /><input title="异常最少照片" type="number" min="0" max="9" value={item.abnormalPhotoMin ?? 0} onChange={(event) => { const items = [...templateEditor.items]; items[index] = { ...item, abnormalPhotoMin: Number(event.target.value) }; setTemplateEditor({ ...templateEditor, items }); }} /><input title="照片最多数量" type="number" min="0" max="9" value={item.photoMax ?? 9} onChange={(event) => { const items = [...templateEditor.items]; items[index] = { ...item, photoMax: Number(event.target.value) }; setTemplateEditor({ ...templateEditor, items }); }} /><label><input type="checkbox" checked={Boolean(item.normalDescriptionRequired)} onChange={(event) => { const items = [...templateEditor.items]; items[index] = { ...item, normalDescriptionRequired: event.target.checked }; setTemplateEditor({ ...templateEditor, items }); }} /> 正常说明必填</label><label><input type="checkbox" checked={Boolean(item.abnormalDescriptionRequired)} onChange={(event) => { const items = [...templateEditor.items]; items[index] = { ...item, abnormalDescriptionRequired: event.target.checked }; setTemplateEditor({ ...templateEditor, items }); }} /> 异常说明必填</label><button className="gi-button danger" disabled={templateEditor.items.length <= 1} onClick={() => setTemplateEditor({ ...templateEditor, items: templateEditor.items.filter((_, i) => i !== index) })}>删除</button><input style={{ gridColumn: '2 / span 2' }} placeholder="检查提示" value={item.guidance || ''} onChange={(event) => { const items = [...templateEditor.items]; items[index] = { ...item, guidance: event.target.value }; setTemplateEditor({ ...templateEditor, items }); }} /><input style={{ gridColumn: '4 / span 2' }} placeholder="规范依据" value={item.standardReference || ''} onChange={(event) => { const items = [...templateEditor.items]; items[index] = { ...item, standardReference: event.target.value }; setTemplateEditor({ ...templateEditor, items }); }} /></div>)}
    </Modal>}

    {pointEditor && <Modal title={pointEditor.id ? '编辑巡检点位' : '新增巡检点位'} onClose={() => setPointEditor(null)} onSubmit={savePoint} busy={busy === 'point-save'}>
      <div className="gi-form-grid"><Field label="点位编码"><input value={pointEditor.pointCode || ''} onChange={(event) => setPointEditor({ ...pointEditor, pointCode: event.target.value })} /></Field><Field label="点位名称"><input value={pointEditor.pointName || ''} onChange={(event) => setPointEditor({ ...pointEditor, pointName: event.target.value })} /></Field><Field label="类别"><select value={pointEditor.categoryId || ''} onChange={(event) => setPointEditor({ ...pointEditor, categoryId: event.target.value ? Number(event.target.value) : null })}><option value="">请选择</option>{categories.map((category) => <option key={category.id} value={category.id}>{category.categoryName}</option>)}</select></Field><Field label="区域"><input value={pointEditor.areaName || ''} onChange={(event) => setPointEditor({ ...pointEditor, areaName: event.target.value })} /></Field><Field label="楼栋"><input value={pointEditor.buildingName || ''} onChange={(event) => setPointEditor({ ...pointEditor, buildingName: event.target.value })} /></Field><Field label="楼层"><input value={pointEditor.floorName || ''} onChange={(event) => setPointEditor({ ...pointEditor, floorName: event.target.value })} /></Field><Field label="位置说明"><input value={pointEditor.locationDesc || ''} onChange={(event) => setPointEditor({ ...pointEditor, locationDesc: event.target.value })} /></Field><Field label="参考照片（最多9张）"><input type="file" accept="image/*" multiple onChange={(event) => uploadPointPhotos(event.target.files)} /></Field></div>
      <Field label="风险提示"><textarea rows="3" value={pointEditor.riskNote || ''} onChange={(event) => setPointEditor({ ...pointEditor, riskNote: event.target.value })} /></Field>
      <div className="gi-toolbar"><label><input type="checkbox" checked={Boolean(pointEditor.qrEnabled)} onChange={(event) => setPointEditor({ ...pointEditor, qrEnabled: event.target.checked })} /> 配置二维码后强制扫码</label><label><input type="checkbox" checked={Boolean(pointEditor.publicAccessEnabled)} onChange={(event) => setPointEditor({ ...pointEditor, publicAccessEnabled: event.target.checked })} /> 开放匿名月表</label><span className="gi-muted">已上传参考照片 {pointEditor.referencePhotoFileIds?.length || 0} 张</span></div>
    </Modal>}

    {planEditor && <Modal title={planEditor.id ? '编辑计划草稿' : '新建巡检计划'} onClose={() => { setPlanEditor(null); setPreviewRows([]); }} onSubmit={savePlan} busy={busy === 'plan-save'}>
      <div className="gi-form-grid"><Field label="计划名称"><input value={planEditor.planName || ''} onChange={(event) => setPlanEditor({ ...planEditor, planName: event.target.value })} /></Field><Field label="项目模板"><select value={planEditor.templateId || ''} onChange={(event) => setPlanEditor({ ...planEditor, templateId: Number(event.target.value) })}><option value="">请选择已发布项目模板</option>{templates.filter((item) => item.scopeType === 'PROJECT' && item.currentVersionId && item.status !== 'ARCHIVED').map((item) => <option key={item.id} value={item.id}>{item.templateName}</option>)}</select></Field><Field label="频率"><select value={planEditor.config.frequency} onChange={(event) => setPlanEditor({ ...planEditor, config: { ...planEditor.config, frequency: event.target.value } })}><option value="DAILY">每天</option><option value="WEEKLY">每周</option><option value="MONTHLY">每月</option></select></Field><Field label="提前执行分钟"><input type="number" min="0" max="1440" value={planEditor.config.earlyMinutes} onChange={(event) => setPlanEditor({ ...planEditor, config: { ...planEditor.config, earlyMinutes: Number(event.target.value) } })} /></Field><Field label="开始日期"><input type="date" value={planEditor.config.effectiveStart} onChange={(event) => setPlanEditor({ ...planEditor, config: { ...planEditor.config, effectiveStart: event.target.value } })} /></Field><Field label="结束日期（可空）"><input type="date" value={planEditor.config.effectiveEnd || ''} onChange={(event) => setPlanEditor({ ...planEditor, config: { ...planEditor.config, effectiveEnd: event.target.value } })} /></Field></div>
      <div className="gi-card" style={{ marginTop: 12 }}><strong>计划默认人员配置</strong><div className="gi-muted">批量关联点位时沿用本组配置，随后可逐点覆盖；备选人不会自动接手。</div><div className="gi-form-grid">
        <Field label="主巡检人"><select value={planEditor.config.assigneeId || ''} onChange={(event) => setPlanEditor({ ...planEditor, config: { ...planEditor.config, assigneeId: event.target.value ? Number(event.target.value) : null } })}>{userOptions('canSubmit')}</select></Field>
        <Field label="备选巡检人"><select multiple value={(planEditor.config.backupAssigneeIds || []).map(String)} onChange={(event) => setPlanEditor({ ...planEditor, config: { ...planEditor.config, backupAssigneeIds: [...event.target.selectedOptions].map((option) => Number(option.value)) } })}>{assignmentUsers('canSubmit').map((user) => <option key={user.userId} value={user.userId}>{user.userName}</option>)}</select></Field>
        <Field label="默认整改人"><select value={planEditor.config.defaultRectifierId || ''} onChange={(event) => setPlanEditor({ ...planEditor, config: { ...planEditor.config, defaultRectifierId: event.target.value ? Number(event.target.value) : null } })}>{userOptions('canRectify')}</select></Field>
        <Field label="整改天数"><input type="number" min="0" max="365" value={planEditor.config.rectificationDays ?? 3} onChange={(event) => setPlanEditor({ ...planEditor, config: { ...planEditor.config, rectificationDays: Number(event.target.value) } })} /></Field>
        <Field label="主复查人"><select value={planEditor.config.reviewerId || ''} onChange={(event) => setPlanEditor({ ...planEditor, config: { ...planEditor.config, reviewerId: event.target.value ? Number(event.target.value) : null } })}>{userOptions('canReview')}</select></Field>
        <Field label="备选复查人"><select multiple value={(planEditor.config.backupReviewerIds || []).map(String)} onChange={(event) => setPlanEditor({ ...planEditor, config: { ...planEditor.config, backupReviewerIds: [...event.target.selectedOptions].map((option) => Number(option.value)) } })}>{assignmentUsers('canReview').map((user) => <option key={user.userId} value={user.userId}>{user.userName}</option>)}</select></Field>
      </div></div>
      {planEditor.config.frequency === 'WEEKLY' && <Field label="星期（1=周一，逗号分隔）"><input value={(planEditor.config.weekdays || []).join(',')} onChange={(event) => setPlanEditor({ ...planEditor, config: { ...planEditor.config, weekdays: event.target.value.split(',').map(Number).filter(Boolean) } })} /></Field>}
      {planEditor.config.frequency === 'MONTHLY' && <Field label="日期（1-28，-1=月末，逗号分隔）"><input value={(planEditor.config.monthDays || []).join(',')} onChange={(event) => setPlanEditor({ ...planEditor, config: { ...planEditor.config, monthDays: event.target.value.split(',').map(Number).filter((value) => value) } })} /></Field>}
      <div className="gi-toolbar" style={{ marginTop: 14 }}><strong>命名时间段</strong><button className="gi-button" onClick={() => setPlanEditor({ ...planEditor, config: { ...planEditor.config, slots: [...planEditor.config.slots, { slotCode: `SLOT_${planEditor.config.slots.length + 1}`, slotName: '', startTime: '08:00', dueTime: '18:00', dueDayOffset: 0 }] } })}>增加时间段</button></div>
      {planEditor.config.slots.map((slot, index) => <div className="gi-item-editor" key={`${slot.slotCode}-${index}`}><input value={slot.slotCode} onChange={(event) => { const slots = [...planEditor.config.slots]; slots[index] = { ...slot, slotCode: event.target.value }; setPlanEditor({ ...planEditor, config: { ...planEditor.config, slots } }); }} /><input value={slot.slotName} placeholder="时间段名称" onChange={(event) => { const slots = [...planEditor.config.slots]; slots[index] = { ...slot, slotName: event.target.value }; setPlanEditor({ ...planEditor, config: { ...planEditor.config, slots } }); }} /><input type="time" value={slot.startTime} onChange={(event) => { const slots = [...planEditor.config.slots]; slots[index] = { ...slot, startTime: event.target.value }; setPlanEditor({ ...planEditor, config: { ...planEditor.config, slots } }); }} /><input type="time" value={slot.dueTime} onChange={(event) => { const slots = [...planEditor.config.slots]; slots[index] = { ...slot, dueTime: event.target.value }; setPlanEditor({ ...planEditor, config: { ...planEditor.config, slots } }); }} /><select value={slot.dueDayOffset} onChange={(event) => { const slots = [...planEditor.config.slots]; slots[index] = { ...slot, dueDayOffset: Number(event.target.value) }; setPlanEditor({ ...planEditor, config: { ...planEditor.config, slots } }); }}><option value="0">当日截止</option><option value="1">次日截止</option></select><button className="gi-button danger" disabled={planEditor.config.slots.length <= 1} onClick={() => setPlanEditor({ ...planEditor, config: { ...planEditor.config, slots: planEditor.config.slots.filter((_, i) => i !== index) } })}>删除</button></div>)}
      <strong>批量关联点位</strong><div className="gi-point-picker">{points.filter((point) => point.status === 'ACTIVE').map((point) => { const selected = planEditor.config.points.some((item) => Number(item.pointId) === Number(point.id)); return <label key={point.id} className="gi-card" style={{ margin: 0, padding: 8 }}><input type="checkbox" checked={selected} onChange={(event) => { const assignments = event.target.checked ? [...planEditor.config.points, { pointId: point.id, assigneeId: null, backupAssigneeIds: null, defaultRectifierId: null, rectificationDays: null, reviewerId: null, backupReviewerIds: null }] : planEditor.config.points.filter((item) => Number(item.pointId) !== Number(point.id)); setPlanEditor({ ...planEditor, config: { ...planEditor.config, points: assignments } }); }} /> {point.pointCode} · {point.pointName}</label>; })}</div>
      {planEditor.config.points.map((assignment, index) => <div className="gi-assignment" key={assignment.pointId}>
        <div><strong>{pointMap.get(Number(assignment.pointId))?.pointName}</strong><div className="gi-muted">留空沿用计划默认；备选人不会自动接手</div><button className="gi-button" onClick={() => { const values = [...planEditor.config.points]; values[index] = { pointId: assignment.pointId, assigneeId: null, backupAssigneeIds: null, defaultRectifierId: null, rectificationDays: null, reviewerId: null, backupReviewerIds: null }; setPlanEditor({ ...planEditor, config: { ...planEditor.config, points: values } }); }}>恢复计划默认</button></div>
        <Field label="主巡检人覆盖"><select value={assignment.assigneeId || ''} onChange={(event) => { const values = [...planEditor.config.points]; values[index] = { ...assignment, assigneeId: event.target.value ? Number(event.target.value) : null }; setPlanEditor({ ...planEditor, config: { ...planEditor.config, points: values } }); }}><option value="">沿用计划默认</option>{assignmentUsers('canSubmit').map((user) => <option key={user.userId} value={user.userId}>{user.userName}</option>)}</select></Field>
        <Field label="备选巡检人（可多选）"><select multiple value={(assignment.backupAssigneeIds || []).map(String)} onChange={(event) => { const values = [...planEditor.config.points]; values[index] = { ...assignment, backupAssigneeIds: [...event.target.selectedOptions].map((option) => Number(option.value)) }; setPlanEditor({ ...planEditor, config: { ...planEditor.config, points: values } }); }}>{assignmentUsers('canSubmit').map((user) => <option key={user.userId} value={user.userId}>{user.userName}</option>)}</select></Field>
        <Field label="默认整改人覆盖"><select value={assignment.defaultRectifierId || ''} onChange={(event) => { const values = [...planEditor.config.points]; values[index] = { ...assignment, defaultRectifierId: event.target.value ? Number(event.target.value) : null }; setPlanEditor({ ...planEditor, config: { ...planEditor.config, points: values } }); }}><option value="">沿用计划默认</option>{assignmentUsers('canRectify').map((user) => <option key={user.userId} value={user.userId}>{user.userName}</option>)}</select></Field>
        <Field label="整改天数覆盖"><input type="number" min="0" max="365" placeholder={String(planEditor.config.rectificationDays ?? 3)} value={assignment.rectificationDays ?? ''} onChange={(event) => { const values = [...planEditor.config.points]; values[index] = { ...assignment, rectificationDays: event.target.value === '' ? null : Number(event.target.value) }; setPlanEditor({ ...planEditor, config: { ...planEditor.config, points: values } }); }} /></Field>
        <Field label="主复查人覆盖"><select value={assignment.reviewerId || ''} onChange={(event) => { const values = [...planEditor.config.points]; values[index] = { ...assignment, reviewerId: event.target.value ? Number(event.target.value) : null }; setPlanEditor({ ...planEditor, config: { ...planEditor.config, points: values } }); }}><option value="">沿用计划默认</option>{assignmentUsers('canReview').map((user) => <option key={user.userId} value={user.userId}>{user.userName}</option>)}</select></Field>
        <Field label="备选复查人（可多选）"><select multiple value={(assignment.backupReviewerIds || []).map(String)} onChange={(event) => { const values = [...planEditor.config.points]; values[index] = { ...assignment, backupReviewerIds: [...event.target.selectedOptions].map((option) => Number(option.value)) }; setPlanEditor({ ...planEditor, config: { ...planEditor.config, points: values } }); }}>{assignmentUsers('canReview').map((user) => <option key={user.userId} value={user.userId}>{user.userName}</option>)}</select></Field>
      </div>)}
      <div className="gi-toolbar" style={{ marginTop: 12 }}><button className="gi-button" disabled={busy === 'plan-preview'} onClick={previewPlan}>预览未来14天任务</button><span className="gi-muted">预计生成 {previewRows.length} 个“点位 + 日期 + 时间段”任务</span></div>
    </Modal>}
  </div>;
}
