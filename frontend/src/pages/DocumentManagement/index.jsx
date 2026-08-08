import React, { useCallback, useEffect, useState } from 'react';
import {
  archiveProjectDocument,
  batchProjectDocuments,
  createDocumentFolder,
  createProjectDocument,
  deleteProjectDocument,
  downloadProjectDocument,
  getDocumentFolders,
  getProjectDocumentDetail,
  getProjectDocumentRecycleBin,
  getProjectDocuments,
  getProjectDocumentSummary,
  previewProjectDocument,
  restoreProjectDocument,
  unarchiveProjectDocument,
  updateDocumentFolder,
  updateProjectDocument,
  uploadProjectDocumentVersion,
} from '../../services/document';
import { confirmAdministrativeDeletion } from '../../services/administrativeDeletion';
import { collectProjectMenuCodes, hasProjectPermission, isPlatformAdmin } from '../../utils/permissions';
import { pageMenuAllowed } from '../../utils/roleAuthorization';
import './index.css';

const IMAGE_EXTENSIONS = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg'];
const TEXT_EXTENSIONS = ['txt', 'md', 'csv', 'json', 'xml', 'log'];
const VIDEO_EXTENSIONS = ['mp4', 'webm', 'ogg', 'mov'];
const AUDIO_EXTENSIONS = ['mp3', 'wav', 'm4a', 'aac', 'ogg'];

const initialFilters = { keyword: '', status: '', startDate: '', endDate: '' };
const initialUpload = { title: '', documentNo: '', folderId: 0, remark: '', changeNote: '' };

const formatDateTime = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : '-';
const formatFileSize = (size) => {
  const bytes = Number(size || 0);
  if (!bytes) return '-';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 ** 2) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 ** 3) return `${(bytes / 1024 ** 2).toFixed(1)} MB`;
  return `${(bytes / 1024 ** 3).toFixed(1)} GB`;
};
const extensionOf = (name = '') => name.includes('.') ? name.split('.').pop().toLowerCase() : '';
const responseData = (response, fallback) => {
  if (response?.code !== 200) throw new Error(response?.message || fallback);
  return response.data;
};
const errorMessage = (error, fallback) => error?.response?.data?.message || error?.message || fallback;

function Modal({ title, subtitle, children, footer, onClose, wide = false }) {
  return (
    <div className="dm-overlay" onMouseDown={onClose}>
      <section className={`dm-modal ${wide ? 'is-wide' : ''}`} onMouseDown={(event) => event.stopPropagation()}>
        <header className="dm-modal-header">
          <div><h2>{title}</h2>{subtitle && <span>{subtitle}</span>}</div>
          <button className="dm-icon-button" onClick={onClose} title="关闭" aria-label="关闭">×</button>
        </header>
        <div className="dm-modal-body">{children}</div>
        {footer && <footer className="dm-modal-footer">{footer}</footer>}
      </section>
    </div>
  );
}

function FolderTree({ folders, selectedId, onSelect, onCreate, onRename, onDelete, recycle, onRecycle, canManage, canDelete, canAccessRecycle }) {
  return (
    <aside className="dm-folders">
      <div className="dm-panel-heading">
        <div><strong>资料目录</strong><span>一级目录</span></div>
        {canManage && <button className="dm-icon-button" onClick={onCreate} title="新建一级目录">+</button>}
      </div>
      <div className="dm-folder-scroll">
        <button className={`dm-root-folder ${selectedId === null && !recycle ? 'is-active' : ''}`} onClick={() => onSelect(null)}>
          <span>全部资料</span><small>{folders.reduce((sum, item) => sum + Number(item.documentCount || 0), 0)}</small>
        </button>
        <button className={`dm-root-folder ${selectedId === 0 && !recycle ? 'is-active' : ''}`} onClick={() => onSelect(0)}>
          <span>未分类</span>
        </button>
        <div className="dm-folder-divider" />
        {folders.map((folder) => (
          <div key={folder.id} className={`dm-folder-row ${selectedId === folder.id && !recycle ? 'is-active' : ''}`}>
        <button className="dm-folder-main" onClick={() => onSelect(folder.id)} title={folder.folderName}>
          <span className="dm-folder-mark">□</span>
          <span>{folder.folderName}</span>
          <small>{folder.documentCount || 0}</small>
        </button>
        {(canManage || canDelete) && <div className="dm-folder-actions">
          {canManage && <button title="重命名" onClick={() => onRename(folder)}>✎</button>}
          {canDelete && <button title="永久删除目录" onClick={() => onDelete(folder)}>×</button>}
        </div>}
          </div>
        ))}
      </div>
      {canAccessRecycle && <button className={`dm-recycle-button ${recycle ? 'is-active' : ''}`} onClick={onRecycle}>
        <span>回收站</span>
      </button>}
    </aside>
  );
}

export default function DocumentManagementPage({ projectId, projectList, theme: T, currentUser }) {
  const [folders, setFolders] = useState([]);
  const [documents, setDocuments] = useState([]);
  const [summary, setSummary] = useState({ total: 0, active: 0, archived: 0, recentUpdates: 0 });
  const [selectedFolder, setSelectedFolder] = useState(null);
  const [filters, setFilters] = useState(initialFilters);
  const [appliedFilters, setAppliedFilters] = useState(initialFilters);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [recycle, setRecycle] = useState(false);
  const [selectedIds, setSelectedIds] = useState([]);
  const [loading, setLoading] = useState(false);
  const [errorText, setErrorText] = useState('');
  const [detail, setDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [uploadState, setUploadState] = useState(null);
  const [editState, setEditState] = useState(null);
  const [versionState, setVersionState] = useState(null);
  const [folderState, setFolderState] = useState(null);
  const [moveState, setMoveState] = useState(null);
  const [preview, setPreview] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [menuNotice, setMenuNotice] = useState('');

  const projectName = projectList?.find((item) => item.id === projectId)?.projectName || '当前项目';
  const pageCount = Math.max(1, Math.ceil(total / pageSize));
  const allSelected = documents.length > 0 && documents.every((item) => selectedIds.includes(item.id));
  const canManage = summary.canManage === true
    && (isPlatformAdmin(currentUser) || hasProjectPermission(currentUser, projectId, 'document.manage'));
  const canDelete = isPlatformAdmin(currentUser);
  const canUpload = isPlatformAdmin(currentUser)
    || hasProjectPermission(currentUser, projectId, 'document.upload');
  const projectMenuCodes = collectProjectMenuCodes(currentUser, projectId);
  const canViewLibrary = isPlatformAdmin(currentUser)
    || pageMenuAllowed(projectMenuCodes, ['DOCUMENT_LIBRARY'], ['WEB_DOCUMENT', 'DOCUMENT_MANAGEMENT']);
  const canViewRecycle = isPlatformAdmin(currentUser)
    || pageMenuAllowed(projectMenuCodes, ['DOCUMENT_RECYCLE'], ['WEB_DOCUMENT', 'DOCUMENT_MANAGEMENT']);
  const themeVars = {
    '--dm-page-bg': T.pageBg,
    '--dm-card-bg': T.cardBg,
    '--dm-surface': T.surface2,
    '--dm-border': T.borderColor,
    '--dm-text': T.textPrimary,
    '--dm-text-secondary': T.textSecondary,
    '--dm-muted': T.textMuted,
    '--dm-accent': T.accent,
    '--dm-active-bg': T.activeItemBg,
    '--dm-success': T.success,
    '--dm-warning': T.warning,
    '--dm-danger': T.danger,
  };

  const loadFolders = useCallback(async () => {
    const response = await getDocumentFolders(projectId);
    setFolders(responseData(response, '目录加载失败') || []);
  }, [projectId]);

  const loadSummary = useCallback(async () => {
    const response = await getProjectDocumentSummary(projectId);
    setSummary(responseData(response, '统计加载失败') || {});
  }, [projectId]);

  const loadDocuments = useCallback(async () => {
    setLoading(true);
    setErrorText('');
    try {
      const params = { projectId, keyword: appliedFilters.keyword || undefined, pageNo, pageSize };
      let response;
      if (recycle) {
        response = await getProjectDocumentRecycleBin(params);
      } else {
        response = await getProjectDocuments({
          ...params,
          folderId: selectedFolder === null ? undefined : selectedFolder,
          status: appliedFilters.status || undefined,
          startDate: appliedFilters.startDate || undefined,
          endDate: appliedFilters.endDate || undefined,
        });
      }
      const data = responseData(response, '资料加载失败') || {};
      setDocuments(data.records || []);
      setTotal(Number(data.total || 0));
      setSelectedIds([]);
    } catch (error) {
      setDocuments([]);
      setTotal(0);
      setErrorText(errorMessage(error, '资料加载失败'));
    } finally {
      setLoading(false);
    }
  }, [projectId, selectedFolder, appliedFilters, pageNo, pageSize, recycle]);

  const refreshAll = useCallback(async () => {
    await Promise.all([loadFolders(), loadSummary(), loadDocuments()]);
  }, [loadFolders, loadSummary, loadDocuments]);

  useEffect(() => {
    setSelectedFolder(null);
    setFilters(initialFilters);
    setAppliedFilters(initialFilters);
    setPageNo(1);
    setRecycle(false);
    setDetail(null);
  }, [projectId]);
  useEffect(() => {
    if (recycle && !canViewRecycle && canViewLibrary) {
      setRecycle(false);
      setPageNo(1);
      setMenuNotice('当前角色无回收站菜单权限，已返回资料库');
    } else if (!recycle && !canViewLibrary && canViewRecycle) {
      setRecycle(true);
      setPageNo(1);
      setMenuNotice('当前角色无资料库菜单权限，已切换到回收站');
    }
  }, [canViewLibrary, canViewRecycle, recycle]);
  useEffect(() => { loadFolders().catch(() => setFolders([])); }, [loadFolders]);
  useEffect(() => { loadSummary().catch(() => setSummary({})); }, [loadSummary]);
  useEffect(() => { loadDocuments(); }, [loadDocuments]);
  useEffect(() => () => { if (preview?.objectUrl) URL.revokeObjectURL(preview.objectUrl); }, [preview]);

  const applySearch = () => {
    setPageNo(1);
    setAppliedFilters(filters);
  };

  const openDetail = async (id) => {
    setDetailLoading(true);
    try {
      const response = await getProjectDocumentDetail(id);
      setDetail(responseData(response, '资料详情加载失败'));
    } catch (error) {
      alert(errorMessage(error, '资料详情加载失败'));
    } finally {
      setDetailLoading(false);
    }
  };

  const runAction = async (action, successText) => {
    setSubmitting(true);
    try {
      await action();
      setDetail(null);
      await refreshAll();
      if (successText) window.setTimeout(() => {}, 0);
      return true;
    } catch (error) {
      if (error?.cancelled) return false;
      alert(errorMessage(error, successText || '操作失败'));
      return false;
    } finally {
      setSubmitting(false);
    }
  };

  const submitFolder = async () => {
    const name = folderState?.name?.trim();
    if (!name) return alert('请输入目录名称');
    const ok = await runAction(async () => {
      const response = folderState.folder
        ? await updateDocumentFolder(folderState.folder.id, { folderName: name })
        : await createDocumentFolder({ projectId, parentId: 0, folderName: name });
      responseData(response, '目录保存失败');
    }, '目录保存失败');
    if (ok) setFolderState(null);
  };

  const removeFolder = async (folder) => {
    await runAction(async () => {
      const deleted = await confirmAdministrativeDeletion('DOCUMENT_FOLDER', folder.id);
      if (!deleted) throw Object.assign(new Error('已取消删除'), { cancelled: true });
    }, '目录永久删除失败');
  };

  const submitUpload = async () => {
    if (!uploadState?.file) return alert('请选择文件');
    if (!uploadState.form.title.trim()) return alert('请输入资料名称');
    const ok = await runAction(async () => responseData(await createProjectDocument({
      ...uploadState.form,
      projectId,
      file: uploadState.file,
    }), '资料上传失败'), '资料上传失败');
    if (ok) setUploadState(null);
  };

  const submitEdit = async () => {
    const form = editState?.form;
    if (!form?.title?.trim()) return alert('请输入资料名称');
    const ok = await runAction(async () => responseData(await updateProjectDocument(editState.document.id, form), '资料保存失败'), '资料保存失败');
    if (ok) setEditState(null);
  };

  const submitVersion = async () => {
    if (!versionState?.file) return alert('请选择新版本文件');
    const ok = await runAction(async () => responseData(await uploadProjectDocumentVersion(
      versionState.document.id, versionState.file, versionState.changeNote,
    ), '新版本上传失败'), '新版本上传失败');
    if (ok) setVersionState(null);
  };

  const handleDelete = async (document) => {
    if (!window.confirm(`将《${document.title}》移入回收站？`)) return;
    await runAction(async () => responseData(await deleteProjectDocument(document.id), '删除失败'), '删除失败');
  };

  const handleArchive = async (document) => {
    const archived = document.status === 'ARCHIVED';
    await runAction(async () => responseData(
      archived ? await unarchiveProjectDocument(document.id) : await archiveProjectDocument(document.id),
      archived ? '恢复归档失败' : '归档失败',
    ), archived ? '恢复归档失败' : '归档失败');
  };

  const handleRecycleAction = async (document, purge) => {
    const label = purge ? '永久删除' : '恢复';
    if (purge) {
      await runAction(async () => {
        const deleted = await confirmAdministrativeDeletion('PROJECT_DOCUMENT', document.id);
        if (!deleted) throw Object.assign(new Error('已取消删除'), { cancelled: true });
      }, `${label}失败`);
      return;
    }
    await runAction(async () => responseData(await restoreProjectDocument(document.id), `${label}失败`), `${label}失败`);
  };

  const submitBatch = async (action, folderId) => {
    if (!selectedIds.length) return;
    const ok = await runAction(async () => responseData(await batchProjectDocuments({ ids: selectedIds, action, folderId }), '批量操作失败'), '批量操作失败');
    if (ok) setMoveState(null);
  };

  const download = async (document, version) => {
    try {
      const blob = await downloadProjectDocument(document.id, version?.id);
      const url = URL.createObjectURL(blob);
      const anchor = window.document.createElement('a');
      anchor.href = url;
      anchor.download = version?.fileName || document.currentVersion?.fileName || document.title;
      window.document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      if (detail) openDetail(document.id);
    } catch (error) {
      alert(errorMessage(error, '下载失败'));
    }
  };

  const openPreview = async (document, version = document.currentVersion) => {
    const fileName = version?.fileName || document.title;
    const extension = extensionOf(fileName);
    const kind = extension === 'pdf' ? 'pdf'
      : IMAGE_EXTENSIONS.includes(extension) ? 'image'
        : TEXT_EXTENSIONS.includes(extension) ? 'text'
          : VIDEO_EXTENSIONS.includes(extension) ? 'video'
            : AUDIO_EXTENSIONS.includes(extension) ? 'audio' : 'unsupported';
    if (kind === 'unsupported') return setPreview({ document, version, kind, fileName });
    try {
      const blob = await previewProjectDocument(document.id, version?.id);
      const next = { document, version, kind, fileName, objectUrl: null, text: '' };
      if (kind === 'text') next.text = await blob.text();
      else next.objectUrl = URL.createObjectURL(blob);
      setPreview(next);
      if (detail) openDetail(document.id);
    } catch (error) {
      alert(errorMessage(error, '预览失败'));
    }
  };

  const closePreview = () => {
    if (preview?.objectUrl) URL.revokeObjectURL(preview.objectUrl);
    setPreview(null);
  };

  const startUpload = () => setUploadState({
    file: null,
    form: { ...initialUpload, folderId: selectedFolder == null ? 0 : selectedFolder },
  });

  return (
    <div className="dm-page" style={themeVars}>
      <header className="dm-page-header">
        <div><span>{projectName}</span><h1>资料管理</h1><p>{menuNotice || '工程资料库'}</p></div>
        <div className="dm-header-actions">
          {recycle && canViewLibrary && <button className="dm-button dm-button-secondary" onClick={() => { setRecycle(false); setMenuNotice(''); setPageNo(1); }}>返回资料库</button>}
          {!recycle && canViewRecycle && <button className="dm-button dm-button-secondary" onClick={() => { setRecycle(true); setMenuNotice(''); setPageNo(1); }}>回收站</button>}
          {!recycle && canUpload && <button className="dm-button dm-button-primary" onClick={startUpload}>上传资料</button>}
        </div>
      </header>

      <section className="dm-summary">
        {[
          ['全部资料', summary.total || 0], ['使用中', summary.active || 0],
          ['已归档', summary.archived || 0], ['近七日更新', summary.recentUpdates || 0],
        ].map(([label, value]) => <div key={label}><span>{label}</span><strong>{value}</strong></div>)}
      </section>

      <main className="dm-workspace">
        <FolderTree
          folders={folders}
          selectedId={selectedFolder}
          recycle={recycle}
          canManage={canManage}
          canDelete={canDelete}
          canAccessRecycle={canViewRecycle}
          onSelect={(id) => { if (canViewLibrary) { setSelectedFolder(id); setRecycle(false); setMenuNotice(''); setPageNo(1); } }}
          onCreate={() => setFolderState({ name: '' })}
          onRename={(folder) => setFolderState({ folder, name: folder.folderName })}
          onDelete={removeFolder}
          onRecycle={() => { setRecycle(true); setMenuNotice(''); setPageNo(1); }}
        />

        <section className="dm-list-panel">
          <div className="dm-toolbar">
            <div className="dm-search">
              <span>⌕</span>
              <input value={filters.keyword} onChange={(event) => setFilters({ ...filters, keyword: event.target.value })}
                onKeyDown={(event) => event.key === 'Enter' && applySearch()} placeholder="资料名称、编号或备注" />
            </div>
            {!recycle && <>
              <select value={filters.status} onChange={(event) => setFilters({ ...filters, status: event.target.value })}>
                <option value="">全部归档状态</option><option value="ACTIVE">使用中</option><option value="ARCHIVED">已归档</option>
              </select>
              <input className="dm-date" type="date" value={filters.startDate} onChange={(event) => setFilters({ ...filters, startDate: event.target.value })} title="起始日期" />
              <input className="dm-date" type="date" value={filters.endDate} onChange={(event) => setFilters({ ...filters, endDate: event.target.value })} title="结束日期" />
            </>}
            <button className="dm-button dm-button-primary" onClick={applySearch}>查询</button>
            <button className="dm-icon-button" title="刷新" onClick={refreshAll}>↻</button>
          </div>

          {selectedIds.length > 0 && !recycle && (
            <div className="dm-batch-bar">
              <span>已选 {selectedIds.length} 项</span>
              <button onClick={() => setMoveState({ folderId: 0 })}>移动</button>
              <button onClick={() => submitBatch('ARCHIVE')}>归档</button>
              {canDelete && <button className="is-danger" onClick={() => window.confirm('将所选资料移入回收站？') && submitBatch('DELETE')}>删除</button>}
              <button onClick={() => setSelectedIds([])}>取消选择</button>
            </div>
          )}

          <div className="dm-table-wrap">
            <table className="dm-table">
              <thead><tr>
                <th className="dm-check-column"><input type="checkbox" checked={allSelected} onChange={(event) => setSelectedIds(event.target.checked ? documents.map((item) => item.id) : [])} /></th>
                <th>资料名称</th><th>编号</th><th>资料目录</th><th>版本 / 大小</th><th>上传人</th><th>更新时间</th><th>归档状态</th><th className="dm-actions-column">操作</th>
              </tr></thead>
              <tbody>
                {loading && <tr><td colSpan="9" className="dm-empty">资料加载中...</td></tr>}
                {!loading && errorText && <tr><td colSpan="9" className="dm-empty is-error">{errorText}</td></tr>}
                {!loading && !errorText && documents.length === 0 && <tr><td colSpan="9" className="dm-empty">{recycle ? '回收站暂无资料' : '当前目录暂无资料'}</td></tr>}
                {!loading && !errorText && documents.map((document) => {
                  const version = document.currentVersion || {};
                  const checked = selectedIds.includes(document.id);
                  return (
                    <tr key={document.id} className={checked ? 'is-selected' : ''} onDoubleClick={() => !recycle && openDetail(document.id)}>
                      <td><input type="checkbox" checked={checked} onChange={(event) => setSelectedIds(event.target.checked ? [...selectedIds, document.id] : selectedIds.filter((id) => id !== document.id))} /></td>
                      <td><div className="dm-file-cell"><span>{(version.fileExtension || extensionOf(version.fileName) || 'FILE').slice(0, 5).toUpperCase()}</span><div><button onClick={() => recycle ? null : openDetail(document.id)}>{document.title}</button><small>{document.remark || version.fileName || '无备注'}</small></div></div></td>
                      <td>{document.documentNo || '-'}</td>
                      <td>{document.folderName || '未分类'}</td>
                      <td><strong className="dm-plain-strong">{version.versionLabel || '-'}</strong><small className="dm-cell-subline">{formatFileSize(version.fileSize)}</small></td>
                      <td>{document.createdByName || `用户 ${document.createdBy}`}</td>
                      <td>{formatDateTime(document.updateTime)}</td>
                      <td><span className={`dm-status ${document.status === 'ARCHIVED' ? 'is-archived' : ''}`}>{document.status === 'ARCHIVED' ? '已归档' : '使用中'}</span></td>
                      <td><div className="dm-row-actions">
                        {recycle ? <>
                          <button onClick={() => handleRecycleAction(document, false)}>恢复</button>
                          {canDelete && <button className="is-danger" onClick={() => handleRecycleAction(document, true)}>永久删除</button>}
                        </> : <>
                          <button onClick={() => openPreview(document)}>预览</button>
                          <button onClick={() => download(document)}>下载</button>
                          <button onClick={() => openDetail(document.id)}>详情</button>
                          {document.canEdit && <button onClick={() => setEditState({ document, form: { folderId: document.folderId, documentNo: document.documentNo || '', title: document.title, remark: document.remark || '' } })}>编辑</button>}
                        </>}
                      </div></td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          <footer className="dm-pagination">
            <span>共 {total} 条</span>
            <select value={pageSize} onChange={(event) => { setPageSize(Number(event.target.value)); setPageNo(1); }}><option value="10">10 条/页</option><option value="20">20 条/页</option><option value="50">50 条/页</option></select>
            <button disabled={pageNo <= 1} onClick={() => setPageNo(pageNo - 1)}>‹</button>
            <span>{pageNo} / {pageCount}</span>
            <button disabled={pageNo >= pageCount} onClick={() => setPageNo(pageNo + 1)}>›</button>
          </footer>
        </section>
      </main>

      {(detail || detailLoading) && (
        <div className="dm-drawer-backdrop" onMouseDown={() => !detailLoading && setDetail(null)}>
          <aside className="dm-drawer" onMouseDown={(event) => event.stopPropagation()}>
            {detailLoading && !detail ? <div className="dm-drawer-loading">详情加载中...</div> : detail && <>
              <header className="dm-drawer-header"><div><span>{detail.document.documentNo || '无资料编号'}</span><h2>{detail.document.title}</h2></div><button className="dm-icon-button" onClick={() => setDetail(null)}>×</button></header>
              <div className="dm-drawer-actions">
                <button onClick={() => openPreview(detail.document)}>预览</button>
                <button onClick={() => download(detail.document)}>下载</button>
                {detail.document.canEdit && canUpload && detail.document.status === 'ACTIVE' && <button onClick={() => setVersionState({ document: detail.document, file: null, changeNote: '' })}>上传新版本</button>}
                {detail.document.canEdit && <button onClick={() => setEditState({ document: detail.document, form: { folderId: detail.document.folderId, documentNo: detail.document.documentNo || '', title: detail.document.title, remark: detail.document.remark || '' } })}>编辑属性</button>}
                {detail.document.canEdit && <button onClick={() => handleArchive(detail.document)}>{detail.document.status === 'ARCHIVED' ? '恢复归档' : '归档'}</button>}
                {canDelete && <button className="is-danger" onClick={() => handleDelete(detail.document)}>删除</button>}
              </div>
              <div className="dm-drawer-scroll">
                <section className="dm-detail-section"><h3>资料属性</h3><dl>
                  <div><dt>资料目录</dt><dd>{detail.document.folderName || '未分类'}</dd></div><div><dt>资料编号</dt><dd>{detail.document.documentNo || '-'}</dd></div>
                  <div><dt>归档状态</dt><dd>{detail.document.status === 'ARCHIVED' ? '已归档' : '使用中'}</dd></div><div><dt>上传人</dt><dd>{detail.document.createdByName}</dd></div>
                  <div><dt>更新时间</dt><dd>{formatDateTime(detail.document.updateTime)}</dd></div><div><dt>备注</dt><dd>{detail.document.remark || '-'}</dd></div>
                </dl></section>
                <section className="dm-detail-section"><h3>版本记录</h3><div className="dm-version-list">
                  {(detail.versions || []).map((version) => <article key={version.id}><div><strong>{version.versionLabel}</strong><span>{version.fileName}</span><small>{version.changeNote || '无版本说明'} · {version.createdByName} · {formatDateTime(version.createTime)}</small></div><div><span>{formatFileSize(version.fileSize)}</span><button onClick={() => openPreview(detail.document, version)}>预览</button><button onClick={() => download(detail.document, version)}>下载</button></div></article>)}
                </div></section>
                <section className="dm-detail-section"><h3>操作记录</h3><div className="dm-activity-list">
                  {(detail.activities || []).length === 0 ? <p>暂无操作记录</p> : detail.activities.map((activity) => <article key={activity.id}><span /><div><strong>{activity.operatorName}</strong><b>{activity.operationLabel}</b><p>{activity.description}</p><time>{formatDateTime(activity.createTime)}</time></div></article>)}
                </div></section>
              </div>
            </>}
          </aside>
        </div>
      )}

      {folderState && <Modal title={folderState.folder ? '重命名目录' : '新建一级目录'} onClose={() => setFolderState(null)} footer={<><button className="dm-button dm-button-secondary" onClick={() => setFolderState(null)}>取消</button><button className="dm-button dm-button-primary" disabled={submitting} onClick={submitFolder}>保存</button></>}>
        <label className="dm-field"><span>目录名称</span><input autoFocus maxLength="100" value={folderState.name} onChange={(event) => setFolderState({ ...folderState, name: event.target.value })} /></label>
      </Modal>}

      {uploadState && <Modal title="上传资料" subtitle={projectName} onClose={() => !submitting && setUploadState(null)} footer={<><button className="dm-button dm-button-secondary" onClick={() => setUploadState(null)}>取消</button><button className="dm-button dm-button-primary" disabled={submitting} onClick={submitUpload}>{submitting ? '上传中...' : '上传 V1'}</button></>}>
        <div className="dm-form-grid">
          <label className="dm-file-picker dm-field is-wide"><input type="file" onChange={(event) => { const file = event.target.files?.[0] || null; setUploadState({ ...uploadState, file, form: { ...uploadState.form, title: uploadState.form.title || file?.name?.replace(/\.[^.]+$/, '') || '' } }); }} /><strong>{uploadState.file?.name || '选择文件'}</strong><span>{uploadState.file ? formatFileSize(uploadState.file.size) : 'PDF、图片、Office、CAD、压缩包等'}</span></label>
          <label className="dm-field"><span>资料名称</span><input value={uploadState.form.title} onChange={(event) => setUploadState({ ...uploadState, form: { ...uploadState.form, title: event.target.value } })} /></label>
          <label className="dm-field"><span>资料编号</span><input value={uploadState.form.documentNo} onChange={(event) => setUploadState({ ...uploadState, form: { ...uploadState.form, documentNo: event.target.value } })} /></label>
          <label className="dm-field"><span>资料目录</span><select value={uploadState.form.folderId} onChange={(event) => setUploadState({ ...uploadState, form: { ...uploadState.form, folderId: Number(event.target.value) } })}><option value="0">未分类</option>{folders.map((folder) => <option value={folder.id} key={folder.id}>{folder.folderName}</option>)}</select></label>
          <label className="dm-field is-wide"><span>版本说明</span><input value={uploadState.form.changeNote} onChange={(event) => setUploadState({ ...uploadState, form: { ...uploadState.form, changeNote: event.target.value } })} placeholder="首次上传" /></label>
          <label className="dm-field is-wide"><span>备注</span><textarea value={uploadState.form.remark} onChange={(event) => setUploadState({ ...uploadState, form: { ...uploadState.form, remark: event.target.value } })} /></label>
        </div>
      </Modal>}

      {editState && <Modal title="编辑资料属性" subtitle={editState.document.title} onClose={() => setEditState(null)} footer={<><button className="dm-button dm-button-secondary" onClick={() => setEditState(null)}>取消</button><button className="dm-button dm-button-primary" disabled={submitting} onClick={submitEdit}>保存</button></>}>
        <div className="dm-form-grid">
          <label className="dm-field"><span>资料名称</span><input value={editState.form.title} onChange={(event) => setEditState({ ...editState, form: { ...editState.form, title: event.target.value } })} /></label>
          <label className="dm-field"><span>资料编号</span><input value={editState.form.documentNo} onChange={(event) => setEditState({ ...editState, form: { ...editState.form, documentNo: event.target.value } })} /></label>
          <label className="dm-field"><span>资料目录</span><select value={editState.form.folderId} onChange={(event) => setEditState({ ...editState, form: { ...editState.form, folderId: Number(event.target.value) } })}><option value="0">未分类</option>{folders.map((folder) => <option value={folder.id} key={folder.id}>{folder.folderName}</option>)}</select></label>
          <label className="dm-field is-wide"><span>备注</span><textarea value={editState.form.remark} onChange={(event) => setEditState({ ...editState, form: { ...editState.form, remark: event.target.value } })} /></label>
        </div>
      </Modal>}

      {versionState && <Modal title="上传新版本" subtitle={versionState.document.title} onClose={() => setVersionState(null)} footer={<><button className="dm-button dm-button-secondary" onClick={() => setVersionState(null)}>取消</button><button className="dm-button dm-button-primary" disabled={submitting} onClick={submitVersion}>上传</button></>}>
        <div className="dm-form-grid"><label className="dm-file-picker dm-field is-wide"><input type="file" onChange={(event) => setVersionState({ ...versionState, file: event.target.files?.[0] || null })} /><strong>{versionState.file?.name || '选择新版本文件'}</strong><span>{versionState.file ? formatFileSize(versionState.file.size) : '旧版本会继续保留'}</span></label><label className="dm-field is-wide"><span>版本说明</span><textarea value={versionState.changeNote} onChange={(event) => setVersionState({ ...versionState, changeNote: event.target.value })} /></label></div>
      </Modal>}

      {moveState && <Modal title="批量移动" subtitle={`已选 ${selectedIds.length} 项`} onClose={() => setMoveState(null)} footer={<><button className="dm-button dm-button-secondary" onClick={() => setMoveState(null)}>取消</button><button className="dm-button dm-button-primary" disabled={submitting} onClick={() => submitBatch('MOVE', Number(moveState.folderId))}>移动</button></>}><label className="dm-field"><span>目标目录</span><select value={moveState.folderId} onChange={(event) => setMoveState({ folderId: event.target.value })}><option value="0">未分类</option>{folders.map((folder) => <option value={folder.id} key={folder.id}>{folder.folderName}</option>)}</select></label></Modal>}

      {preview && <Modal title={preview.fileName} subtitle={`${preview.version?.versionLabel || ''} ${formatFileSize(preview.version?.fileSize)}`} onClose={closePreview} wide footer={<><button className="dm-button dm-button-secondary" onClick={() => download(preview.document, preview.version)}>下载</button><button className="dm-button dm-button-primary" onClick={closePreview}>关闭</button></>}>
        <div className="dm-preview">
          {preview.kind === 'image' && <img src={preview.objectUrl} alt={preview.fileName} />}
          {preview.kind === 'pdf' && <iframe src={preview.objectUrl} title={preview.fileName} />}
          {preview.kind === 'text' && <pre>{preview.text}</pre>}
          {preview.kind === 'video' && <video src={preview.objectUrl} controls />}
          {preview.kind === 'audio' && <audio src={preview.objectUrl} controls />}
          {preview.kind === 'unsupported' && <div className="dm-unsupported"><strong>{extensionOf(preview.fileName).toUpperCase() || 'FILE'}</strong><span>请下载后使用对应软件打开</span><button className="dm-button dm-button-primary" onClick={() => download(preview.document, preview.version)}>下载文件</button></div>}
        </div>
      </Modal>}
    </div>
  );
}
