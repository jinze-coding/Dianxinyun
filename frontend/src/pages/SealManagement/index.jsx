import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  approveSealApplication,
  archiveSealApplicationFile,
  copySealApplication,
  deleteSealApplicationFile,
  downloadSealApplicationFile,
  downloadSealApplicationPdf,
  exportSealApplicationLedger,
  getSealApplication,
  getSealApplicationCcCandidates,
  getSealApplications,
  getSealTransferCandidates,
  previewSealApplicationFile,
  rejectSealApplication,
  submitSealApplication,
  transferSealApplication,
  updateSealApplication,
  uploadSealApplicationFile,
  withdrawSealApplication,
} from '../../services/seal';
import { getDocumentFolders, getProjectDocuments } from '../../services/document';
import { hasProjectPermission, isPlatformAdmin } from '../../utils/permissions';
import './index.css';
import './editor.css';

const SCOPE_TABS = [
  { id: 'INITIATED', label: '我发起的' },
  { id: 'PENDING_FOR_ME', label: '待我审批' },
  { id: 'CC_TO_ME', label: '抄送我的' },
  { id: 'ALL', label: '全部申请', permission: 'seal.view' },
];

const STATUS_LABELS = {
  DRAFT: '草稿',
  PENDING_APPROVAL: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回',
};

const STATUS_OPTIONS = [
  ['', '全部状态'],
  ['DRAFT', '草稿'],
  ['PENDING_APPROVAL', '审批中'],
  ['APPROVED', '已通过'],
  ['REJECTED', '已驳回'],
  ['WITHDRAWN', '已撤回'],
];

const emptyFilters = { keyword: '', status: '', startDate: '', endDate: '' };
const ARCHIVE_DOCUMENT_PAGE_SIZE = 50;

const localDateString = (date = new Date()) => {
  const pad = (value) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
};

function ledgerDateRange(unit, anchorValue) {
  const parts = String(anchorValue || '').split('-').map(Number);
  const anchor = parts.length === 3 && parts.every(Number.isFinite)
    ? new Date(parts[0], parts[1] - 1, parts[2]) : new Date();
  if (unit === 'DAY') {
    const day = localDateString(anchor);
    return { startDate: day, endDate: day };
  }
  if (unit === 'WEEK') {
    const start = new Date(anchor);
    start.setDate(anchor.getDate() - (anchor.getDay() || 7) + 1);
    const end = new Date(start);
    end.setDate(start.getDate() + 6);
    return { startDate: localDateString(start), endDate: localDateString(end) };
  }
  return {
    startDate: localDateString(new Date(anchor.getFullYear(), anchor.getMonth(), 1)),
    endDate: localDateString(new Date(anchor.getFullYear(), anchor.getMonth() + 1, 0)),
  };
}

const extractList = (data) => Array.isArray(data)
  ? data
  : (data?.records || data?.items || data?.list || data?.content || []);

const unwrap = (response, fallback) => {
  if (!response || Number(response.code) !== 200) throw new Error(response?.message || fallback);
  return response.data;
};

const errorText = (error, fallback) => error?.response?.data?.message || error?.message || fallback;
const formatDateTime = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : '-';
const fileIdOf = (file) => file?.fileId ?? file?.id;
const itemIdOf = (item) => item?.itemId ?? item?.id;
const statusOf = (application) => String(application?.status || '').toUpperCase();

function saveBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.rel = 'noopener';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function StatusPill({ status }) {
  const normalized = String(status || '').toUpperCase();
  return <span className={`seal-status ${normalized.toLowerCase()}`}>{STATUS_LABELS[normalized] || normalized || '-'}</span>;
}

function Modal({ title, subtitle, children, footer, onClose, wide = false }) {
  return (
    <div className="seal-modal-mask" onMouseDown={onClose}>
      <section className={`seal-modal ${wide ? 'wide' : ''}`} onMouseDown={(event) => event.stopPropagation()}>
        <header><div><h2>{title}</h2>{subtitle && <p>{subtitle}</p>}</div><button className="icon" onClick={onClose} aria-label="关闭">×</button></header>
        <div className="seal-modal-body">{children}</div>
        {footer && <footer>{footer}</footer>}
      </section>
    </div>
  );
}

function ApplicationEditor({ value, ccCandidates, ccLoading, busy, onSearchCc, onClose, onSave }) {
  const [form, setForm] = useState(() => ({
    id: value?.id || null,
    requestKey: value?.requestKey || (value?.id ? `web-update-${value.id}` : ''),
    projectId: value?.projectId,
    sealId: value?.sealId,
    sealName: value?.sealName || '',
    departmentName: value?.departmentName || '',
    purpose: value?.purpose || '',
    items: (value?.items?.length ? value.items : [{ documentName: '', copies: 1 }]).map((item) => ({
      id: itemIdOf(item),
      documentName: item.documentName || item.title || '',
      copies: Number(item.copies || item.copyCount || 1),
    })),
    ccUserIds: value?.ccUserIds || [],
  }));
  const [validation, setValidation] = useState('');
  const [ccKeyword, setCcKeyword] = useState('');

  useEffect(() => {
    if (form.id || form.ccUserIds.length || !ccCandidates.length) return;
    const defaults = ccCandidates
      .filter((candidate) => candidate.defaultSelected || candidate.selected)
      .map((candidate) => Number(candidate.userId ?? candidate.id))
      .filter(Boolean);
    if (defaults.length) setForm((current) => ({ ...current, ccUserIds: defaults }));
  }, [ccCandidates, form.ccUserIds.length, form.id]);

  const updateItem = (index, patch) => setForm((current) => ({
    ...current,
    items: current.items.map((item, itemIndex) => itemIndex === index ? { ...item, ...patch } : item),
  }));

  const submit = () => {
    const normalizedItems = form.items
      .map((item) => ({ documentName: item.documentName.trim(), copies: Math.max(1, Number(item.copies || 1)) }))
      .filter((item) => item.documentName);
    if (!form.purpose.trim()) return setValidation('请填写用印事由');
    if (!normalizedItems.length) return setValidation('请至少填写一项待盖章资料');
    setValidation('');
    onSave({ ...form, departmentName: value?.projectName || form.departmentName, purpose: form.purpose.trim(), items: normalizedItems });
  };

  return (
    <Modal
      title="编辑用印申请"
      subtitle="印章和项目由原扫码入口确定，申请人不能更换。"
      onClose={busy ? undefined : onClose}
      wide
      footer={<><button onClick={onClose} disabled={busy}>取消</button><button className="primary" onClick={submit} disabled={busy}>{busy ? '保存中…' : '保存草稿'}</button></>}
    >
      <div className="seal-form-grid">
        <label><span>项目</span><input value={value?.projectName || `项目 ${form.projectId || '-'}`} disabled /></label>
        <label><span>印章</span><input value={form.sealName || '由扫码入口确定'} disabled /></label>
        <label className="full"><span>申请项目部</span><input value={value?.projectName || form.departmentName || '-'} disabled /><small>项目部由扫码印章所属项目确定，申请人不能修改。</small></label>
        <label className="full"><span>用印事由 *</span><textarea value={form.purpose} maxLength={1000} onChange={(event) => setForm({ ...form, purpose: event.target.value })} /></label>
        <div className="seal-form-section full"><strong>待盖章资料</strong><button onClick={() => setForm((current) => ({ ...current, items: [...current.items, { documentName: '', copies: 1 }] }))}>+ 增加资料</button></div>
        <div className="seal-items-editor full">
          {form.items.map((item, index) => (
            <div key={`${item.id || 'new'}-${index}`}>
              <input aria-label={`资料名称 ${index + 1}`} placeholder="资料名称" value={item.documentName} maxLength={200} onChange={(event) => updateItem(index, { documentName: event.target.value })} />
              <input aria-label={`份数 ${index + 1}`} type="number" min="1" max="999" value={item.copies} onChange={(event) => updateItem(index, { copies: event.target.value })} />
              <span>份</span>
              <button className="danger" disabled={form.items.length === 1} onClick={() => setForm((current) => ({ ...current, items: current.items.filter((_, itemIndex) => itemIndex !== index) }))}>移除</button>
            </div>
          ))}
        </div>
        <div className="seal-form-section full"><strong>申请抄送人</strong><span>新草稿会预选管理员配置的默认抄送人，申请人可增加或取消</span></div>
        <div className="seal-cc-picker full">
          <div><input value={ccKeyword} placeholder="搜索同项目姓名、账号或手机号" onChange={(event) => setCcKeyword(event.target.value)} onKeyDown={(event) => event.key === 'Enter' && onSearchCc(ccKeyword)} /><button onClick={() => onSearchCc(ccKeyword)}>搜索</button><span>已选 {form.ccUserIds.length} 人</span></div>
          <section>{ccLoading ? <div className="seal-inline-empty">抄送候选人加载中…</div> : ccCandidates.map((candidate) => {
            const userId = Number(candidate.userId ?? candidate.id);
            const checked = form.ccUserIds.map(Number).includes(userId);
            return <label key={userId} className={checked ? 'selected' : ''}><input type="checkbox" checked={checked} onChange={() => setForm((current) => ({ ...current, ccUserIds: checked ? current.ccUserIds.filter((id) => Number(id) !== userId) : [...current.ccUserIds, userId] }))} /><strong>{candidate.displayName || candidate.realName || candidate.username}</strong><span>{candidate.username || '-'} · {candidate.phone || '-'}</span></label>;
          })}{!ccLoading && !ccCandidates.length && <div className="seal-inline-empty">没有匹配的同项目成员</div>}</section>
        </div>
        {validation && <div className="seal-form-error full" role="alert">{validation}</div>}
      </div>
    </Modal>
  );
}

function OpinionDialog({ action, busy, onClose, onSubmit }) {
  const [opinion, setOpinion] = useState('');
  const approving = action === 'approve';
  return (
    <Modal
      title={approving ? '填写项目经理审批意见' : '填写驳回意见'}
      subtitle="审批意见将进入正式申请单和操作留痕，提交后不可由申请人修改。"
      onClose={busy ? undefined : onClose}
      footer={<><button onClick={onClose} disabled={busy}>取消</button><button className={approving ? 'primary' : 'danger'} disabled={busy || !opinion.trim()} onClick={() => onSubmit(opinion.trim())}>{busy ? '提交中…' : approving ? '同意用印' : '驳回申请'}</button></>}
    >
      <label className="seal-dialog-field"><span>项目经理审批意见 *</span><textarea autoFocus value={opinion} maxLength={1000} onChange={(event) => setOpinion(event.target.value)} placeholder={approving ? '请填写同意用印的审批意见' : '请说明驳回原因和修改要求'} /></label>
    </Modal>
  );
}

function TransferDialog({ candidates, loading, busy, onSearch, onClose, onSubmit }) {
  const [keyword, setKeyword] = useState('');
  const [assigneeUserId, setAssigneeUserId] = useState('');
  const [reason, setReason] = useState('');
  return (
    <Modal
      title="转办审批任务"
      subtitle="转办只改变当前待办处理人，不会给目标用户增加角色或其他业务权限。"
      onClose={busy ? undefined : onClose}
      footer={<><button onClick={onClose} disabled={busy}>取消</button><button className="primary" disabled={busy || !assigneeUserId || !reason.trim()} onClick={() => onSubmit({ assigneeUserId: Number(assigneeUserId), reason: reason.trim() })}>{busy ? '转办中…' : '确认转办'}</button></>}
    >
      <div className="seal-candidate-search"><input value={keyword} placeholder="搜索姓名、账号或手机号" onChange={(event) => setKeyword(event.target.value)} /><button onClick={() => onSearch(keyword)}>搜索</button></div>
      <div className="seal-candidate-list">
        {loading ? <div className="seal-inline-empty">候选人加载中…</div> : candidates.map((candidate) => {
          const userId = candidate.userId ?? candidate.id;
          return <label key={userId} className={Number(assigneeUserId) === Number(userId) ? 'selected' : ''}><input type="radio" name="transferCandidate" value={userId} checked={Number(assigneeUserId) === Number(userId)} onChange={() => setAssigneeUserId(String(userId))} /><strong>{candidate.realName || candidate.username}</strong><span>{candidate.username || '-'} · {candidate.phone || '-'}</span></label>;
        })}
        {!loading && !candidates.length && <div className="seal-inline-empty">没有可转办的项目成员</div>}
      </div>
      <label className="seal-dialog-field"><span>转办原因 *</span><textarea value={reason} maxLength={500} onChange={(event) => setReason(event.target.value)} placeholder="请说明转办原因" /></label>
    </Modal>
  );
}

function ArchiveDialog({
  file,
  folders,
  documents,
  documentTotal,
  foldersLoading,
  documentsLoading,
  documentsLoadingMore,
  busy,
  application,
  onDocumentSearch,
  onDocumentLoadMore,
  onClose,
  onSubmit,
}) {
  const [documentKeyword, setDocumentKeyword] = useState('');
  const [form, setForm] = useState({
    archiveMode: 'NEW_DOCUMENT',
    fileId: fileIdOf(file),
    folderId: 0,
    documentId: '',
    title: file?.fileName || application?.purpose || '盖章资料',
    documentNo: application?.applicationNo || '',
    changeNote: `由用印申请 ${application?.applicationNo || ''} 归档`,
  });
  const searchDocuments = () => {
    setForm((current) => ({ ...current, documentId: '' }));
    onDocumentSearch(documentKeyword);
  };
  const hasMoreDocuments = documents.length < documentTotal;
  return (
    <Modal
      title="归档盖章件到工程资料"
      subtitle="系统会复制盖章件生成工程资料，不会与用印附件共用文件生命周期。"
      onClose={busy ? undefined : onClose}
      footer={<><button onClick={onClose} disabled={busy}>取消</button><button className="primary" disabled={busy || (form.archiveMode === 'NEW_DOCUMENT' ? foldersLoading || !form.title.trim() : documentsLoading || !form.documentId)} onClick={() => onSubmit({ ...form, folderId: Number(form.folderId), documentId: form.documentId ? Number(form.documentId) : undefined, title: form.title.trim() })}>{busy ? '归档中…' : '确认归档'}</button></>}
    >
      <div className="seal-form-grid">
        <label className="full"><span>盖章件</span><input value={file?.fileName || file?.originalName || `文件 ${fileIdOf(file)}`} disabled /></label>
        <label className="full"><span>归档方式</span><select value={form.archiveMode} onChange={(event) => setForm({ ...form, archiveMode: event.target.value, documentId: '' })}><option value="NEW_DOCUMENT">新建工程资料</option><option value="NEW_VERSION">作为已有资料的新版本</option></select></label>
        {form.archiveMode === 'NEW_DOCUMENT' ? <>
          <label><span>资料目录</span><select value={form.folderId} disabled={foldersLoading} onChange={(event) => setForm({ ...form, folderId: event.target.value })}><option value="0">未分类</option>{folders.map((folder) => <option key={folder.id} value={folder.id}>{folder.folderName}</option>)}</select></label>
          <label><span>资料编号</span><input value={form.documentNo} maxLength={100} onChange={(event) => setForm({ ...form, documentNo: event.target.value })} /></label>
          <label className="full"><span>资料标题 *</span><input value={form.title} maxLength={200} onChange={(event) => setForm({ ...form, title: event.target.value })} /></label>
        </> : <div className="full seal-dialog-field">
          <span>选择已有资料 *</span>
          <div className="seal-candidate-search">
            <input
              value={documentKeyword}
              maxLength={100}
              placeholder="按资料名称、编号或备注搜索"
              onChange={(event) => setDocumentKeyword(event.target.value)}
              onKeyDown={(event) => { if (event.key === 'Enter') searchDocuments(); }}
            />
            <button type="button" disabled={documentsLoading} onClick={searchDocuments}>搜索</button>
          </div>
          <select value={form.documentId} disabled={documentsLoading} onChange={(event) => setForm({ ...form, documentId: event.target.value })}>
            <option value="">{documentsLoading ? '工程资料加载中…' : '请选择同项目资料'}</option>
            {documents.map((document) => <option key={document.id} value={document.id}>{document.title || document.documentName}{document.documentNo ? ` · ${document.documentNo}` : ''}</option>)}
          </select>
          <div className="seal-candidate-search">
            <small>{documentsLoading ? '正在从服务端查询…' : `已加载 ${documents.length} / ${documentTotal} 条；盖章件将成为所选资料的新版本。`}</small>
            {hasMoreDocuments && <button type="button" disabled={documentsLoading || documentsLoadingMore} onClick={onDocumentLoadMore}>{documentsLoadingMore ? '加载中…' : '加载更多'}</button>}
          </div>
          {!documentsLoading && !documents.length && <small className="seal-form-error">未找到可追加版本的使用中资料，请更换关键词。</small>}
        </div>}
        <label className="full"><span>版本说明</span><textarea value={form.changeNote} maxLength={500} onChange={(event) => setForm({ ...form, changeNote: event.target.value })} /></label>
      </div>
    </Modal>
  );
}

export default function SealManagementPage({
  projectId,
  projectList = [],
  theme: T,
  currentUser,
  initialScope = 'INITIATED',
  initialApplicationId,
  mode = 'applications',
}) {
  const [scope, setScope] = useState(mode === 'ledger' ? 'ALL' : initialScope);
  const [filters, setFilters] = useState(() => ({ ...emptyFilters, status: mode === 'ledger' ? 'APPROVED' : '' }));
  const [appliedFilters, setAppliedFilters] = useState(() => ({ ...emptyFilters, status: mode === 'ledger' ? 'APPROVED' : '' }));
  const [ledgerRangeUnit, setLedgerRangeUnit] = useState('MONTH');
  const [ledgerRangeAnchor, setLedgerRangeAnchor] = useState(() => localDateString());
  const [appliedLedgerRange, setAppliedLedgerRange] = useState(() => ({ unit: 'MONTH', anchor: localDateString() }));
  const [rows, setRows] = useState([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const pageSize = 20;
  const [counts, setCounts] = useState({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [detail, setDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [editor, setEditor] = useState(null);
  const [ccCandidates, setCcCandidates] = useState([]);
  const [ccLoading, setCcLoading] = useState(false);
  const [opinionAction, setOpinionAction] = useState('');
  const [transferOpen, setTransferOpen] = useState(false);
  const [transferCandidates, setTransferCandidates] = useState([]);
  const [transferLoading, setTransferLoading] = useState(false);
  const [archiveState, setArchiveState] = useState(null);
  const [folders, setFolders] = useState([]);
  const [archiveDocuments, setArchiveDocuments] = useState([]);
  const [archiveDocumentKeyword, setArchiveDocumentKeyword] = useState('');
  const [archiveDocumentPage, setArchiveDocumentPage] = useState(1);
  const [archiveDocumentTotal, setArchiveDocumentTotal] = useState(0);
  const [foldersLoading, setFoldersLoading] = useState(false);
  const [archiveDocumentsLoading, setArchiveDocumentsLoading] = useState(false);
  const [archiveDocumentsLoadingMore, setArchiveDocumentsLoadingMore] = useState(false);
  const [busy, setBusy] = useState(false);
  const sourceFileRef = useRef(null);
  const stampedFileRef = useRef(null);
  const listSequenceRef = useRef(0);
  const detailSequenceRef = useRef(0);
  const archiveDocumentSequenceRef = useRef(0);
  const initialApplicationRef = useRef(null);

  const projectName = projectList.find((item) => Number(item.id) === Number(projectId))?.projectName || '当前项目';
  const canViewAll = isPlatformAdmin(currentUser)
    || hasProjectPermission(currentUser, projectId, 'seal.view', 'seal.manage', 'seal.export');
  const canManage = isPlatformAdmin(currentUser) || hasProjectPermission(currentUser, projectId, 'seal.manage');
  const canExport = isPlatformAdmin(currentUser) || hasProjectPermission(currentUser, projectId, 'seal.export');
  const currentUserId = Number(currentUser?.id ?? currentUser?.userId ?? 0);
  const visibleScopes = useMemo(() => SCOPE_TABS.filter((item) => item.id !== 'ALL' || canViewAll), [canViewAll]);
  const pageCount = Math.max(1, Math.ceil(total / pageSize));

  const variables = {
    '--seal-page': T.pageBg,
    '--seal-card': T.cardBg,
    '--seal-surface': T.surface2,
    '--seal-border': T.borderColor,
    '--seal-text': T.textPrimary,
    '--seal-secondary': T.textSecondary,
    '--seal-muted': T.textMuted,
    '--seal-accent': T.accent,
    '--seal-active': T.activeItemBg,
    '--seal-success': T.success,
    '--seal-warning': T.warning,
    '--seal-danger': T.danger,
  };

  const loadCounts = useCallback(async () => {
    if (mode === 'ledger' || !projectId) return;
    const scopes = SCOPE_TABS.filter((item) => item.id !== 'ALL' || canViewAll).map((item) => item.id);
    const results = await Promise.allSettled(scopes.map((itemScope) => getSealApplications({
      projectId,
      scope: itemScope,
      pageNo: 1,
      pageSize: 1,
    })));
    const next = {};
    results.forEach((result, index) => {
      if (result.status !== 'fulfilled' || Number(result.value?.code) !== 200) return;
      const data = result.value.data;
      next[scopes[index]] = Number(data?.total ?? extractList(data).length ?? 0);
    });
    setCounts(next);
  }, [canViewAll, mode, projectId]);

  const loadRows = useCallback(async () => {
    if (!projectId) return;
    const sequence = ++listSequenceRef.current;
    setLoading(true);
    setError('');
    try {
      const response = await getSealApplications({
        projectId,
        scope: mode === 'ledger' ? 'ALL' : scope,
        keyword: appliedFilters.keyword || undefined,
        status: appliedFilters.status || undefined,
        startDate: appliedFilters.startDate || undefined,
        endDate: appliedFilters.endDate || undefined,
        dateBasis: mode === 'ledger' ? 'APPROVAL_TIME' : undefined,
        pageNo,
        pageSize,
      });
      const data = unwrap(response, '用印申请加载失败');
      if (sequence !== listSequenceRef.current) return;
      const list = extractList(data);
      setRows(list);
      setTotal(Number(data?.total ?? list.length));
    } catch (loadError) {
      if (sequence !== listSequenceRef.current) return;
      setRows([]);
      setTotal(0);
      setError(errorText(loadError, '用印申请加载失败'));
    } finally {
      if (sequence === listSequenceRef.current) setLoading(false);
    }
  }, [appliedFilters, mode, pageNo, pageSize, projectId, scope]);

  useEffect(() => {
    setScope(mode === 'ledger' ? 'ALL' : initialScope);
    const anchor = localDateString();
    const nextFilters = mode === 'ledger'
      ? { ...emptyFilters, status: 'APPROVED', ...ledgerDateRange('MONTH', anchor) }
      : { ...emptyFilters };
    setLedgerRangeUnit('MONTH');
    setLedgerRangeAnchor(anchor);
    setAppliedLedgerRange({ unit: 'MONTH', anchor });
    setFilters(nextFilters);
    setAppliedFilters(nextFilters);
    setPageNo(1);
    setDetail(null);
  }, [initialScope, mode, projectId]);

  useEffect(() => { loadRows(); }, [loadRows]);
  useEffect(() => { loadCounts(); }, [loadCounts]);

  const refreshAll = useCallback(async () => {
    await Promise.all([loadRows(), loadCounts()]);
  }, [loadCounts, loadRows]);

  const openDetail = useCallback(async (id) => {
    const sequence = ++detailSequenceRef.current;
    setDetailLoading(true);
    setError('');
    try {
      const response = await getSealApplication(id);
      const data = unwrap(response, '用印申请详情加载失败');
      if (sequence === detailSequenceRef.current) setDetail(data);
    } catch (loadError) {
      if (sequence === detailSequenceRef.current) setError(errorText(loadError, '用印申请详情加载失败'));
    } finally {
      if (sequence === detailSequenceRef.current) setDetailLoading(false);
    }
  }, []);

  useEffect(() => {
    const targetKey = initialApplicationId ? `${projectId || ''}:${initialApplicationId}` : '';
    if (!targetKey || initialApplicationRef.current === targetKey) return;
    initialApplicationRef.current = targetKey;
    openDetail(initialApplicationId);
  }, [initialApplicationId, openDetail, projectId]);

  const runAction = async (action, successMessage) => {
    setBusy(true);
    setError('');
    try {
      const response = await action();
      unwrap(response, successMessage || '操作失败');
      setNotice(successMessage);
      if (detail?.id) await openDetail(detail.id);
      await refreshAll();
      return true;
    } catch (actionError) {
      setError(errorText(actionError, '操作失败'));
      return false;
    } finally {
      setBusy(false);
    }
  };

  const submitCurrent = async () => {
    if (!window.confirm('提交后将按当前印章审批配置生成待办，确认提交吗？')) return;
    await runAction(() => submitSealApplication(detail.id), '申请已提交审批');
  };

  const withdrawCurrent = async () => {
    if (!window.confirm('确认撤回当前用印申请？撤回后审批任务将关闭。')) return;
    await runAction(() => withdrawSealApplication(detail.id), '申请已撤回');
  };

  const saveEditor = async (form) => {
    setBusy(true);
    setError('');
    try {
      const payload = {
        requestKey: form.requestKey || `web-update-${form.id}`,
        projectId: Number(form.projectId || projectId),
        sealId: form.sealId ? Number(form.sealId) : undefined,
        sealName: form.sealName || undefined,
        departmentName: form.departmentName,
        purpose: form.purpose,
        items: form.items,
        ccUserIds: form.ccUserIds,
      };
      const response = await updateSealApplication(form.id, payload);
      const saved = unwrap(response, '申请草稿保存失败');
      const savedId = saved?.id ?? saved?.applicationId ?? form.id;
      setEditor(null);
      setNotice('申请草稿已保存');
      await refreshAll();
      if (savedId) await openDetail(savedId);
    } catch (saveError) {
      setError(errorText(saveError, '申请草稿保存失败'));
    } finally {
      setBusy(false);
    }
  };

  const loadCcCandidates = useCallback(async (keyword = '', targetProjectId = projectId, targetSealId) => {
    if (!targetProjectId) return;
    setCcLoading(true);
    try {
      const response = await getSealApplicationCcCandidates({
        projectId: targetProjectId,
        sealId: targetSealId || undefined,
        keyword: keyword.trim() || undefined,
      });
      setCcCandidates(extractList(unwrap(response, '抄送候选人加载失败')));
    } catch (loadError) {
      setCcCandidates([]);
      setError(errorText(loadError, '抄送候选人加载失败'));
    } finally {
      setCcLoading(false);
    }
  }, [projectId]);

  const openEditor = async (value) => {
    setEditor(value);
    await loadCcCandidates('', value?.projectId || projectId, value?.sealId);
  };

  const copyCurrent = async () => {
    setBusy(true);
    setError('');
    try {
      const copied = unwrap(await copySealApplication(detail.id, {
        requestKey: globalThis.crypto?.randomUUID?.() || `web-copy-${detail.id}-${Date.now()}`,
        ccUserIds: (detail.ccRecipients || detail.ccUsers || []).map((user) => user.userId ?? user.id).filter(Boolean),
      }), '复制申请失败');
      const copiedId = copied?.id ?? copied?.applicationId;
      setNotice('已复制为新草稿并保留 COPY 审计记录；原附件不会复制，请重新上传。');
      await refreshAll();
      if (copiedId) await openDetail(copiedId);
    } catch (copyError) {
      setError(errorText(copyError, '复制申请失败'));
    } finally {
      setBusy(false);
    }
  };

  const submitOpinion = async (opinion) => {
    const action = opinionAction === 'approve'
      ? () => approveSealApplication(detail.id, opinion)
      : () => rejectSealApplication(detail.id, opinion);
    const succeeded = await runAction(action, opinionAction === 'approve' ? '审批已通过' : '申请已驳回');
    if (succeeded) setOpinionAction('');
  };

  const searchTransferCandidates = useCallback(async (keyword = '') => {
    if (!detail?.id) return;
    setTransferLoading(true);
    try {
      const response = await getSealTransferCandidates(detail.id, keyword.trim());
      setTransferCandidates(extractList(unwrap(response, '转办候选人加载失败')));
    } catch (loadError) {
      setTransferCandidates([]);
      setError(errorText(loadError, '转办候选人加载失败'));
    } finally {
      setTransferLoading(false);
    }
  }, [detail?.id]);

  const openTransfer = async () => {
    setTransferOpen(true);
    await searchTransferCandidates('');
  };

  const submitTransfer = async (values) => {
    const succeeded = await runAction(() => transferSealApplication(detail.id, values), '审批任务已转办');
    if (succeeded) setTransferOpen(false);
  };

  const uploadSelectedFile = async (event, role) => {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file || !detail?.id) return;
    await runAction(() => uploadSealApplicationFile(detail.id, file, role), role === 'SOURCE' ? '待盖章资料已上传' : '盖章件已上传');
  };

  const removeFile = async (file) => {
    if (!window.confirm(`确认移除附件“${file.fileName || file.originalName || fileIdOf(file)}”？`)) return;
    await runAction(() => deleteSealApplicationFile(detail.id, fileIdOf(file)), '附件已移除');
  };

  const previewFile = async (file) => {
    try {
      const blob = await previewSealApplicationFile(detail.id, fileIdOf(file));
      const url = URL.createObjectURL(blob);
      const opened = window.open(url, '_blank', 'noopener,noreferrer');
      if (!opened) saveBlob(blob, file.fileName || file.originalName || '用印附件');
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (previewError) {
      setError(errorText(previewError, '附件预览失败'));
    }
  };

  const downloadFile = async (file) => {
    try {
      const blob = await downloadSealApplicationFile(detail.id, fileIdOf(file));
      saveBlob(blob, file.fileName || file.originalName || `用印附件-${fileIdOf(file)}`);
    } catch (downloadError) {
      setError(errorText(downloadError, '附件下载失败'));
    }
  };

  const downloadPdf = async (application) => {
    try {
      const blob = await downloadSealApplicationPdf(application.id);
      saveBlob(blob, `${application.applicationNo || `用印申请-${application.id}`}.pdf`);
    } catch (downloadError) {
      setError(errorText(downloadError, '用印申请单下载失败'));
    }
  };

  const loadArchiveDocuments = async ({ targetProjectId, keyword = '', page = 1, append = false }) => {
    const sequence = ++archiveDocumentSequenceRef.current;
    if (append) setArchiveDocumentsLoadingMore(true);
    else setArchiveDocumentsLoading(true);
    try {
      const response = await getProjectDocuments({
        projectId: targetProjectId,
        status: 'ACTIVE',
        keyword: keyword || undefined,
        pageNo: page,
        pageSize: ARCHIVE_DOCUMENT_PAGE_SIZE,
      });
      const data = unwrap(response, '工程资料加载失败');
      if (sequence !== archiveDocumentSequenceRef.current) return;
      const records = extractList(data);
      setArchiveDocuments((current) => append
        ? [...current, ...records.filter((record) => !current.some((item) => Number(item.id) === Number(record.id)))]
        : records);
      setArchiveDocumentPage(page);
      setArchiveDocumentTotal(Number(data?.total ?? records.length));
    } catch (loadError) {
      if (sequence !== archiveDocumentSequenceRef.current) return;
      if (!append) {
        setArchiveDocuments([]);
        setArchiveDocumentTotal(0);
      }
      setError(errorText(loadError, '工程资料加载失败'));
    } finally {
      if (sequence === archiveDocumentSequenceRef.current) {
        setArchiveDocumentsLoading(false);
        setArchiveDocumentsLoadingMore(false);
      }
    }
  };

  const openArchive = async (file) => {
    setArchiveState(file);
    setArchiveDocuments([]);
    setArchiveDocumentKeyword('');
    setArchiveDocumentPage(1);
    setArchiveDocumentTotal(0);
    setFoldersLoading(true);
    const targetProjectId = detail.projectId || projectId;
    const folderRequest = getDocumentFolders(targetProjectId);
    const documentRequest = loadArchiveDocuments({ targetProjectId });
    try {
      const folderResponse = await folderRequest;
      setFolders(extractList(unwrap(folderResponse, '资料目录加载失败')));
    } catch (loadError) {
      setFolders([]);
      setError(errorText(loadError, '资料目录加载失败'));
    } finally {
      setFoldersLoading(false);
    }
    await documentRequest;
  };

  const searchArchiveDocuments = async (keyword) => {
    const normalizedKeyword = String(keyword || '').trim();
    setArchiveDocumentKeyword(normalizedKeyword);
    await loadArchiveDocuments({
      targetProjectId: detail.projectId || projectId,
      keyword: normalizedKeyword,
    });
  };

  const loadMoreArchiveDocuments = async () => {
    if (archiveDocumentsLoading || archiveDocumentsLoadingMore || archiveDocuments.length >= archiveDocumentTotal) return;
    await loadArchiveDocuments({
      targetProjectId: detail.projectId || projectId,
      keyword: archiveDocumentKeyword,
      page: archiveDocumentPage + 1,
      append: true,
    });
  };

  const closeArchive = () => {
    archiveDocumentSequenceRef.current += 1;
    setArchiveState(null);
    setArchiveDocumentsLoading(false);
    setArchiveDocumentsLoadingMore(false);
  };

  const submitArchive = async (values) => {
    const succeeded = await runAction(() => archiveSealApplicationFile(detail.id, values), '盖章件已归档到工程资料');
    if (succeeded) closeArchive();
  };

  const exportLedger = async () => {
    if (!canExport) return;
    setBusy(true);
    setError('');
    try {
      const customRange = appliedLedgerRange.unit === 'CUSTOM';
      const blob = await exportSealApplicationLedger({
        projectId,
        period: customRange ? undefined : appliedLedgerRange.unit,
        anchorDate: customRange ? undefined : appliedLedgerRange.anchor,
        keyword: appliedFilters.keyword || undefined,
        status: appliedFilters.status || undefined,
        startDate: customRange ? (appliedFilters.startDate || undefined) : undefined,
        endDate: customRange ? (appliedFilters.endDate || undefined) : undefined,
      });
      saveBlob(blob, `用印台账-${projectName}-${appliedFilters.startDate || '全部'}-${appliedFilters.endDate || '全部'}.xlsx`);
      setNotice('用印台账已生成');
    } catch (exportError) {
      setError(errorText(exportError, '用印台账导出失败'));
    } finally {
      setBusy(false);
    }
  };

  const files = detail?.files || detail?.attachments || [];
  const sourceFiles = files.filter((file) => String(file.fileRole || file.role || '').toUpperCase() !== 'STAMPED_RESULT');
  const stampedFiles = files.filter((file) => String(file.fileRole || file.role || '').toUpperCase() === 'STAMPED_RESULT');
  const canUploadStamped = Boolean(detail?.canUploadStampedResult ?? detail?.canArchive ?? (statusOf(detail) === 'APPROVED' && canManage));
  const canCopyDetail = Boolean(
    detail
    && ['REJECTED', 'WITHDRAWN'].includes(statusOf(detail))
    && currentUserId > 0
    && Number(detail.applicantId ?? detail.applicantUserId ?? 0) === currentUserId
    && detail.canCopy !== false,
  );

  return (
    <div className="seal-page" style={variables}>
      <section className="seal-page-head">
        <div><span>{projectName}</span><h1>{mode === 'ledger' ? '用印台账' : '用印申请'}</h1><p>{mode === 'ledger' ? '查询已审批申请、盖章件和资料归档结果，并按审批完成时间导出 Excel。' : '申请人、审批人和抄送人通过同一申请详情协同，审批权限按管理员直接配置的用户执行。'}</p></div>
        <div className="seal-head-actions">
          {mode === 'ledger' && <button className="primary" disabled={!canExport || busy} title={!canExport ? '没有用印台账导出权限' : ''} onClick={exportLedger}>导出 Excel 台账</button>}
          <button onClick={refreshAll} disabled={loading}>刷新</button>
        </div>
      </section>

      {mode !== 'ledger' && <nav className="seal-scope-tabs" aria-label="用印申请视图">
        {visibleScopes.map((item) => <button key={item.id} className={scope === item.id ? 'active' : ''} onClick={() => { setScope(item.id); setPageNo(1); }}><span>{item.label}</span>{counts[item.id] !== undefined && <b>{counts[item.id]}</b>}</button>)}
      </nav>}

      <section className="seal-filters">
        <input value={filters.keyword} placeholder="申请编号、申请人、事由或资料名称" onChange={(event) => setFilters({ ...filters, keyword: event.target.value })} onKeyDown={(event) => { if (event.key === 'Enter') { setPageNo(1); setAppliedFilters(filters); if (mode === 'ledger') setAppliedLedgerRange({ unit: ledgerRangeUnit, anchor: ledgerRangeAnchor }); } }} />
        <select value={filters.status} disabled={mode === 'ledger'} onChange={(event) => setFilters({ ...filters, status: event.target.value })}>{STATUS_OPTIONS.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>
        {mode === 'ledger' && <select aria-label="台账导出单位" value={ledgerRangeUnit} onChange={(event) => {
          const unit = event.target.value;
          setLedgerRangeUnit(unit);
          if (unit !== 'CUSTOM') setFilters((current) => ({ ...current, ...ledgerDateRange(unit, ledgerRangeAnchor) }));
        }}><option value="DAY">按日</option><option value="WEEK">按周</option><option value="MONTH">按月</option><option value="CUSTOM">自定义</option></select>}
        {mode === 'ledger' && ledgerRangeUnit !== 'CUSTOM' && <label><span>选择{ledgerRangeUnit === 'DAY' ? '日期' : ledgerRangeUnit === 'WEEK' ? '所在周' : '所在月'}</span><input type="date" value={ledgerRangeAnchor} onChange={(event) => { setLedgerRangeAnchor(event.target.value); setFilters((current) => ({ ...current, ...ledgerDateRange(ledgerRangeUnit, event.target.value) })); }} /></label>}
        {(mode !== 'ledger' || ledgerRangeUnit === 'CUSTOM') && <label><span>开始</span><input type="date" value={filters.startDate} onChange={(event) => setFilters({ ...filters, startDate: event.target.value })} /></label>}
        {(mode !== 'ledger' || ledgerRangeUnit === 'CUSTOM') && <label><span>结束</span><input type="date" value={filters.endDate} onChange={(event) => setFilters({ ...filters, endDate: event.target.value })} /></label>}
        {mode === 'ledger' && ledgerRangeUnit !== 'CUSTOM' && <span className="seal-range-preview">{filters.startDate} 至 {filters.endDate}</span>}
        <button className="primary" onClick={() => { setPageNo(1); setAppliedFilters(filters); if (mode === 'ledger') setAppliedLedgerRange({ unit: ledgerRangeUnit, anchor: ledgerRangeAnchor }); }}>查询</button>
        <button onClick={() => { const anchor = localDateString(); const next = mode === 'ledger' ? { ...emptyFilters, status: 'APPROVED', ...ledgerDateRange('MONTH', anchor) } : { ...emptyFilters }; setLedgerRangeUnit('MONTH'); setLedgerRangeAnchor(anchor); setAppliedLedgerRange({ unit: 'MONTH', anchor }); setFilters(next); setAppliedFilters(next); setPageNo(1); }}>重置</button>
      </section>

      {notice && <div className="seal-notice" role="status" onClick={() => setNotice('')}>{notice}</div>}
      {error && <div className="seal-error" role="alert"><span>{error}</span><button onClick={() => setError('')}>×</button></div>}

      <section className="seal-table-card">
        <div className="seal-table-wrap"><table><thead><tr>
          <th>申请编号</th><th>申请人/部门</th><th>印章</th><th>用印事由</th><th>资料/份数</th><th>状态</th><th>{mode === 'ledger' ? '审批完成时间' : '申请时间'}</th><th>当前处理</th><th>操作</th>
        </tr></thead><tbody>
          {rows.map((application) => {
            const applicationItems = application.items || [];
            const copies = applicationItems.reduce((sum, item) => sum + Number(item.copies || item.copyCount || 0), 0);
            return <tr key={application.id}>
              <td><button className="link" onClick={() => openDetail(application.id)}>{application.applicationNo || `#${application.id}`}</button><small>{application.projectName || projectName}</small></td>
              <td><strong>{application.applicantName || '-'}</strong><small>{application.departmentName || application.companyName || '-'}</small></td>
              <td>{application.sealName || '-'}</td>
              <td className="seal-purpose-cell" title={application.purpose}>{application.purpose || '-'}</td>
              <td>{application.itemCount ?? (applicationItems.length || '-')} 项<small>{application.totalCopies ?? (copies || '-')} 份</small></td>
              <td><StatusPill status={application.status} /></td>
              <td>{mode === 'ledger' ? formatDateTime(application.approvalTime || application.approvedAt || application.reviewedAt) : formatDateTime(application.applicationDate || application.createTime || application.createdAt)}</td>
              <td>{application.currentAssigneeName || application.currentApproverName || (statusOf(application) === 'PENDING_APPROVAL' ? '待审批' : '-')}</td>
              <td><div className="seal-row-actions"><button onClick={() => openDetail(application.id)}>详情</button>{statusOf(application) === 'APPROVED' && <button onClick={() => downloadPdf(application)}>PDF</button>}</div></td>
            </tr>;
          })}
          {!loading && !rows.length && <tr><td className="seal-empty" colSpan="9">当前条件下暂无用印申请</td></tr>}
        </tbody></table></div>
        {loading && <div className="seal-loading">正在加载用印申请…</div>}
        <div className="seal-pagination"><span>共 {total} 条，第 {pageNo}/{pageCount} 页</span><button disabled={pageNo <= 1 || loading} onClick={() => setPageNo((value) => value - 1)}>上一页</button><button disabled={pageNo >= pageCount || loading} onClick={() => setPageNo((value) => value + 1)}>下一页</button></div>
      </section>

      {(detail || detailLoading) && <div className="seal-drawer-mask" onMouseDown={() => !busy && setDetail(null)}>
        <aside className="seal-drawer" onMouseDown={(event) => event.stopPropagation()}>
          {detailLoading && !detail ? <div className="seal-drawer-loading">申请详情加载中…</div> : detail && <>
            <header><div><span>{detail.applicationNo || `#${detail.id}`}</span><h2>{detail.purpose || '用印申请'}</h2></div><button className="icon" onClick={() => setDetail(null)}>×</button></header>
            <div className="seal-drawer-actions">
              {detail.canEdit && <button onClick={() => openEditor({ ...detail, projectName: detail.projectName || projectName, ccUserIds: detail.ccUserIds || (detail.ccRecipients || detail.ccUsers || []).map((user) => user.userId ?? user.id).filter(Boolean) })}>编辑</button>}
              {detail.canSubmit && <button className="primary" onClick={submitCurrent} disabled={busy}>提交审批</button>}
              {detail.canApprove && <button className="primary" onClick={() => setOpinionAction('approve')}>同意</button>}
              {detail.canReject && <button className="danger" onClick={() => setOpinionAction('reject')}>驳回</button>}
              {detail.canTransfer && <button onClick={openTransfer}>转办</button>}
              {(detail.canCancel || detail.canWithdraw) && <button className="danger" onClick={withdrawCurrent}>撤回</button>}
              {canCopyDetail && <button onClick={copyCurrent} disabled={busy}>复制申请</button>}
              {statusOf(detail) === 'APPROVED' && <button onClick={() => downloadPdf(detail)}>下载申请单 PDF</button>}
            </div>
            <div className="seal-detail-grid">
              <div><span>项目</span><strong>{detail.projectName || projectName}</strong></div>
              <div><span>印章</span><strong>{detail.sealName || '-'}</strong></div>
              <div><span>申请人</span><strong>{detail.applicantName || '-'}</strong><small>{detail.applicantPhone || '-'}</small></div>
              <div><span>申请项目部</span><strong>{detail.departmentName || detail.projectName || '-'}</strong></div>
              <div><span>所属公司</span><strong>{detail.companyName || '-'}</strong></div>
              <div><span>申请日期</span><strong>{formatDateTime(detail.applicationDate || detail.createTime || detail.createdAt)}</strong></div>
              <div><span>当前状态</span><StatusPill status={detail.status} /></div>
              <div className="full"><span>用印事由</span><strong>{detail.purpose || '-'}</strong></div>
              {detail.approvalTime && <div><span>审批完成时间</span><strong>{formatDateTime(detail.approvalTime)}</strong></div>}
              {detail.approvalOpinion && <div className="full"><span>项目经理审批意见</span><strong>{detail.approvalOpinion}</strong><small>{detail.approverName || '-'}</small></div>}
            </div>
            <section className="seal-detail-section"><h3>待盖章资料</h3><div className="seal-item-list">{(detail.items || []).map((item, index) => <div key={itemIdOf(item) || index}><span>{index + 1}</span><strong>{item.documentName || item.title}</strong><b>{item.copies || item.copyCount || 1} 份</b></div>)}{!detail.items?.length && <div className="seal-inline-empty">未填写资料明细</div>}</div></section>
            <section className="seal-detail-section"><div className="seal-section-head"><h3>待盖章资料附件</h3>{detail.canEdit && <button onClick={() => sourceFileRef.current?.click()}>上传资料</button>}</div><input ref={sourceFileRef} type="file" hidden onChange={(event) => uploadSelectedFile(event, 'SOURCE')} /><div className="seal-file-list">{sourceFiles.map((file) => <div key={fileIdOf(file)}><div><strong>{file.fileName || file.originalFileName || file.originalName || `文件 ${fileIdOf(file)}`}</strong><span>{formatDateTime(file.createTime || file.createdAt || file.uploadTime)}</span></div><button onClick={() => previewFile(file)}>预览</button><button onClick={() => downloadFile(file)}>下载</button>{detail.canEdit && file.canDelete !== false && <button className="danger" onClick={() => removeFile(file)}>移除</button>}</div>)}{!sourceFiles.length && <div className="seal-inline-empty">尚未上传待盖章资料</div>}</div></section>
            <section className="seal-detail-section"><div className="seal-section-head"><div><h3>盖章件与资料归档</h3><p>审批通过后上传盖章扫描件，再按需复制归档到工程资料。</p></div>{canUploadStamped && <button className="primary" onClick={() => stampedFileRef.current?.click()}>上传盖章件</button>}</div><input ref={stampedFileRef} type="file" hidden onChange={(event) => uploadSelectedFile(event, 'STAMPED_RESULT')} /><div className="seal-file-list">{stampedFiles.map((file) => <div key={fileIdOf(file)}><div><strong>{file.fileName || file.originalName || `文件 ${fileIdOf(file)}`}</strong><span>{file.archivedDocumentId ? `已归档为资料 #${file.archivedDocumentId}` : '尚未归档'}</span></div><button onClick={() => previewFile(file)}>预览</button><button onClick={() => downloadFile(file)}>下载</button>{detail.canArchive && !file.archivedDocumentId && <button className="primary" onClick={() => openArchive(file)}>归档</button>}</div>)}{!stampedFiles.length && <div className="seal-inline-empty">{statusOf(detail) === 'APPROVED' ? '尚未上传盖章件' : '审批通过后可上传盖章件'}</div>}</div></section>
            <section className="seal-detail-section"><h3>审批与操作留痕</h3><div className="seal-timeline">{[...(detail.tasks || []), ...(detail.logs || detail.operationLogs || [])].map((item, index) => <div key={item.id || `${item.action || item.taskStatus}-${index}`}><i /><div><strong>{item.actionLabel || item.actionName || item.action || item.taskName || item.taskStatus || '流程记录'}</strong><span>{item.operatorName || item.assigneeName || item.createdByName || '-'} · {formatDateTime(item.createTime || item.createdAt || item.actionTime || item.completedAt)}</span>{(item.opinion || item.comment || item.description) && <p>{item.opinion || item.comment || item.description}</p>}</div></div>)}{!(detail.tasks?.length || detail.logs?.length || detail.operationLogs?.length) && <div className="seal-inline-empty">暂无审批留痕</div>}</div></section>
            {(detail.ccRecipients || detail.ccUsers)?.length > 0 && <section className="seal-detail-section"><h3>抄送人</h3><div className="seal-people-tags">{(detail.ccRecipients || detail.ccUsers).map((user) => <span key={user.userId || user.id}>{user.displayName || user.realName || user.username}</span>)}</div></section>}
          </>}
        </aside>
      </div>}

      {editor && <ApplicationEditor value={editor} ccCandidates={ccCandidates} ccLoading={ccLoading} busy={busy} onSearchCc={(keyword) => loadCcCandidates(keyword, editor.projectId || projectId, editor.sealId)} onClose={() => setEditor(null)} onSave={saveEditor} />}
      {opinionAction && <OpinionDialog action={opinionAction} busy={busy} onClose={() => setOpinionAction('')} onSubmit={submitOpinion} />}
      {transferOpen && <TransferDialog candidates={transferCandidates} loading={transferLoading} busy={busy} onSearch={searchTransferCandidates} onClose={() => setTransferOpen(false)} onSubmit={submitTransfer} />}
      {archiveState && <ArchiveDialog
        file={archiveState}
        folders={folders}
        documents={archiveDocuments}
        documentTotal={archiveDocumentTotal}
        foldersLoading={foldersLoading}
        documentsLoading={archiveDocumentsLoading}
        documentsLoadingMore={archiveDocumentsLoadingMore}
        busy={busy}
        application={detail}
        onDocumentSearch={searchArchiveDocuments}
        onDocumentLoadMore={loadMoreArchiveDocuments}
        onClose={closeArchive}
        onSubmit={submitArchive}
      />}
    </div>
  );
}
