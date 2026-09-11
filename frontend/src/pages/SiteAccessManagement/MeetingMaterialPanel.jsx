import React, { useCallback, useEffect, useRef, useState } from 'react';
import { confirmAdministrativeDeletion } from '../../services/administrativeDeletion';
import { downloadMeetingMaterial, editMaterial, getMaterialVersions, getMeetingMaterials, materialCategories, materialSize, publishMaterial, setMaterialState, uploadMeetingMaterial } from '../../services/meetingMaterials';
import MeetingMaterialPreview from './MeetingMaterialPreview';

const time = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : '—';
export default function MeetingMaterialPanel({ invitation, userId, canManage, canDelete }) {
  const [query, setQuery] = useState({ keyword: '', category: '', status: 'ACTIVE', pageNo: 1 });
  const [page, setPage] = useState({ records: [], total: 0 });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [upload, setUpload] = useState(null);
  const [editing, setEditing] = useState(null);
  const [history, setHistory] = useState(null);
  const [preview, setPreview] = useState(null);
  const [publication, setPublication] = useState(null);
  const [progress, setProgress] = useState({});
  const controller = useRef(null);
  const generation = useRef(0);
  const load = useCallback(async () => {
    const ticket = ++generation.current; setLoading(true);
    try { const next = await getMeetingMaterials(invitation.id, query); if (ticket === generation.current) { setPage(next); setError(''); } }
    catch (e) { if (ticket === generation.current) { setError(e.message); if ([401, 403].includes(e.response?.status)) { setPage({ records: [], total: 0 }); setPreview(null); } } }
    finally { if (ticket === generation.current) setLoading(false); }
  }, [invitation.id, query]);
  useEffect(() => { void load(); const timer = window.setInterval(load, 15000); return () => { generation.current += 1; window.clearInterval(timer); }; }, [load]);
  useEffect(() => () => controller.current?.abort(), [invitation.id]);
  const closePreview = useCallback(() => setPreview(null), []);
  const action = async (run) => { setBusy(true); setError(''); try { await run(); await load(); } catch (e) { setError(e.message); } finally { setBusy(false); } };
  const changeFilter = (key, value) => setQuery({ ...query, [key]: value, pageNo: 1 });
  const showUpload = (row) => { setProgress({}); setUpload({ row, files: [], category: row?.material.category || 'OTHER', description: '', changeNote: '' }); };
  const beginUpload = async () => {
    const currentUpload = upload;
    controller.current?.abort(); controller.current = new AbortController(); const signal = controller.current.signal;
    setBusy(true); setError('');
    for (const file of currentUpload.files) {
      if (signal.aborted) break;
      const key = `${file.name}:${file.size}:${file.lastModified}`;
      if (progress[key]?.done) continue;
      try {
        await uploadMeetingMaterial(invitation.id, userId, file, {
          title: currentUpload.row?.material.title || file.name, category: currentUpload.category, description: currentUpload.description,
          materialId: currentUpload.row?.material.id || null, expectedVersion: currentUpload.row?.material.version ?? null, changeNote: currentUpload.changeNote,
        }, (next) => { if (!signal.aborted) setProgress((previous) => ({ ...previous, [key]: next })); }, signal);
        if (!signal.aborted) setProgress((previous) => ({ ...previous, [key]: { stage: '已留档 · 内部可见', percent: 100, done: true } }));
      } catch (e) {
        if (!signal.aborted) setProgress((previous) => ({ ...previous, [key]: { stage: e.message || '上传失败，可重试', failed: true } }));
      }
    }
    if (!signal.aborted) { setBusy(false); await load(); }
  };
  const closeUpload = () => { controller.current?.abort(); setBusy(false); setUpload(null); };
  const showHistory = async (row) => {
    try { setHistory({ title: row.material.title, versions: await getMaterialVersions(row.material.id) }); } catch (e) { setError(e.message); }
  };
  const download = async (version) => { try { await downloadMeetingMaterial(version); } catch (e) { setError(e.message); } };
  return <section className="meeting-material-panel">
    <div className="meeting-material-heading"><div><h2>会议资料</h2><p>会前宣发、会中记录和会后成果统一留档。新文件及新版本默认内部可见。</p></div>
      {canManage && <button className="primary" onClick={() => showUpload(null)}>上传资料</button>}</div>
    {error && <div className="site-access-error" role="alert">{error}</div>}
    <div className="meeting-material-filters"><input aria-label="搜索资料" placeholder="搜索资料名称" value={query.keyword} onChange={(e) => changeFilter('keyword', e.target.value)} maxLength={200} />
      <select aria-label="资料分类" value={query.category} onChange={(e) => changeFilter('category', e.target.value)}><option value="">全部分类</option>{Object.entries(materialCategories).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select>
      <select aria-label="资料状态" value={query.status} onChange={(e) => changeFilter('status', e.target.value)}><option value="ACTIVE">有效资料</option><option value="WITHDRAWN">已撤下</option><option value="">全部状态</option></select><button onClick={load} disabled={loading}>刷新</button></div>
    <div className="meeting-material-table"><table><thead><tr><th>资料名称 / 说明</th><th>分类</th><th>当前文件</th><th>可见范围</th><th>上传人 / 时间</th><th>操作</th></tr></thead><tbody>
      {page.records.map((row) => { const m = row.material; const v = row.currentVersion; return <tr key={m.id}>
        <td><button className="link" onClick={() => setPreview(v)}>{m.title}</button>{m.description && <small>{m.description}</small>}{m.status === 'WITHDRAWN' && <small>已撤下，保留历史</small>}</td>
        <td>{materialCategories[m.category]}</td><td><b>V{v.versionNo} · {materialSize(v.fileSize)}</b><small>{v.fileName}</small><small>{({ QUEUED: '等待生成预览', PROCESSING: '正在生成预览', FAILED: '预览生成失败', READY: v.previewKind === 'UNSUPPORTED' ? '原文件下载' : '可在线预览' })[v.previewStatus]}</small></td>
        <td>{m.publishedVersionId ? <span className="meeting-material-public">访客可见{m.publishedVersionId !== v.id && <small>公开的是历史版本，最新版仅内部</small>}</span> : <span>内部可见</span>}</td>
        <td>{v.uploaderName}<small>{time(v.createTime)}</small></td>
        <td><div className="site-access-row-actions"><button onClick={() => setPreview(v)}>预览</button><button onClick={() => download(v)}>下载</button><button onClick={() => showHistory(row)}>版本</button>
          {canManage && <><button disabled={busy} onClick={() => setEditing({ ...m })}>编辑</button>
            {m.status === 'ACTIVE' && <><button disabled={busy} onClick={() => showUpload(row)}>更新文件</button>
              {invitation.status !== 'VOIDED' && <button disabled={busy} onClick={() => setPublication(row)}>{m.publishedVersionId === v.id ? '可见设置' : '公开最新版'}</button>}</>}
            <button disabled={busy} onClick={() => action(() => setMaterialState(m.id, { expectedVersion: m.version, withdrawn: m.status !== 'WITHDRAWN' }))}>{m.status === 'WITHDRAWN' ? '恢复' : '撤下'}</button></>}
          {canDelete && m.status === 'WITHDRAWN' && <button className="danger" disabled={busy} onClick={() => action(() => confirmAdministrativeDeletion('MEETING_MATERIAL', m.id))}>永久删除</button>}
        </div></td></tr>; })}
      {!page.records.length && <tr><td colSpan={6} className="site-access-empty">{loading ? '正在加载…' : '暂无会议资料'}</td></tr>}
    </tbody></table></div>
    <div className="site-access-pagination"><span>共 {page.total} 份资料 · 第 {query.pageNo}/{Math.max(1, Math.ceil(page.total / 20))} 页</span><button disabled={query.pageNo <= 1 || loading} onClick={() => setQuery({ ...query, pageNo: query.pageNo - 1 })}>上一页</button><button disabled={query.pageNo * 20 >= page.total || loading} onClick={() => setQuery({ ...query, pageNo: query.pageNo + 1 })}>下一页</button></div>
    {upload && <Dialog error={error} title={upload.row ? `上传新版本 · ${upload.row.material.title}` : '上传会议资料'} onClose={closeUpload}>
      <div className="meeting-material-form"><label>选择文件<input type="file" multiple={!upload.row} disabled={busy} onChange={(e) => { setProgress({}); setUpload({ ...upload, files: Array.from(e.target.files || []) }); }} /></label>
        <p>单文件最大 1GB；支持分片续传。文件上传后默认内部可见，请在资料列表明确发布。</p>
        {!upload.row && <><label>资料分类<select value={upload.category} disabled={busy} onChange={(e) => setUpload({ ...upload, category: e.target.value })}>{Object.entries(materialCategories).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label><label>资料说明<textarea maxLength={1000} disabled={busy} value={upload.description} onChange={(e) => setUpload({ ...upload, description: e.target.value })} /></label></>}
        {upload.row && <label>版本说明<textarea maxLength={500} disabled={busy} value={upload.changeNote} onChange={(e) => setUpload({ ...upload, changeNote: e.target.value })} /></label>}
        {upload.files.map((file, index) => { const state = progress[`${file.name}:${file.size}:${file.lastModified}`]; return <div className="meeting-upload-item" key={`${file.name}:${index}`}><strong>{file.name}</strong><span>{materialSize(file.size)} · {state?.stage || '等待上传'}</span>{state && !state.failed && <progress max={100} value={state.percent || 0} />}</div>; })}
      </div><footer><button onClick={closeUpload}>{busy ? '暂停并关闭' : '关闭'}</button><button className="primary" disabled={busy || !upload.files.length || upload.files.every((f) => progress[`${f.name}:${f.size}:${f.lastModified}`]?.done)} onClick={beginUpload}>{busy ? '处理中…' : '上传 / 重试未完成文件'}</button></footer>
    </Dialog>}
    {editing && <Dialog error={error} title="编辑资料信息" onClose={() => !busy && setEditing(null)}><div className="meeting-material-form"><label>资料名称<input maxLength={200} value={editing.title} onChange={(e) => setEditing({ ...editing, title: e.target.value })} /></label><label>分类<select value={editing.category} onChange={(e) => setEditing({ ...editing, category: e.target.value })}>{Object.entries(materialCategories).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</select></label><label>说明<textarea maxLength={1000} value={editing.description} onChange={(e) => setEditing({ ...editing, description: e.target.value })} /></label></div><footer><button disabled={busy || !editing.title.trim()} className="primary" onClick={() => action(async () => { await editMaterial(editing.id, { title: editing.title, category: editing.category, description: editing.description, expectedVersion: editing.version }); setEditing(null); })}>保存</button></footer></Dialog>}
    {publication && <Dialog error={error} title="资料可见范围" onClose={() => !busy && setPublication(null)}><div className="meeting-material-form"><strong>{publication.material.title} · V{publication.currentVersion.versionNo}</strong><p>公开后，持会议码的访客可以查看和下载，文件地址可转发；会议结束后继续可见。</p></div><footer><button disabled={busy} onClick={() => action(async () => { await publishMaterial(publication.material.id, { expectedVersion: publication.material.version, versionId: null }); setPublication(null); })}>仅内部可见</button><button className="primary" disabled={busy} onClick={() => action(async () => { await publishMaterial(publication.material.id, { expectedVersion: publication.material.version, versionId: publication.currentVersion.id }); setPublication(null); })}>向访客公开最新版</button></footer></Dialog>}
    {history && <Dialog error={error} title={`历史版本 · ${history.title}`} onClose={() => setHistory(null)}><div className="meeting-material-form">{history.versions.map((v) => <article className="meeting-version-item" key={v.id}><div><strong>V{v.versionNo} · {v.fileName}</strong><small>{v.uploaderName} · {time(v.createTime)} · {materialSize(v.fileSize)}</small><p>{v.changeNote || '无版本说明'}</p></div><button onClick={() => setPreview(v)}>预览</button><button onClick={() => download(v)}>下载</button></article>)}</div></Dialog>}
    {preview && <MeetingMaterialPreview key={preview.id} version={preview} canManage={canManage} onClose={closePreview} />}
  </section>;
}

function Dialog({ title, onClose, children, error }) {
  return <div className="site-access-modal-mask" role="dialog" aria-modal="true" aria-label={title}><section className="meeting-material-dialog"><header><strong>{title}</strong><button onClick={onClose}>关闭</button></header>{error && <div className="site-access-error" role="alert">{error}</div>}{children}</section></div>;
}
