import React, { useEffect, useRef, useState } from 'react';
import { committee, committeeContent, committeeDate, committeeSize, committeeAccept, committeeKey, uploadCommitteeFile } from '../../services/safetyCommittee';
import { hasProjectPermission } from '../../utils/permissions';
import { confirmAdministrativeDeletion } from '../../services/administrativeDeletion';
import './style.css';
import { mergeCommitteePage } from './model';

const activeFiles = (record) => (record?.attachments || []).filter((a) => a.status === 'ACTIVE');
const statusText = (a) => a.previewStatus === 'FAILED' ? '预览失败' : a.previewStatus === 'READY' ? '' : '预览处理中';
function Thumbnail({ file, onClick }) {
  const [url, setUrl] = useState('');
  useEffect(() => {
    let alive = true;
    if (['IMAGE','HEIF'].includes(file.previewKind) && file.previewStatus === 'READY') {
      committee.read(file.id).then(() => { if (alive) setUrl(committeeContent(file.id)); }).catch(() => {});
    }
    return () => { alive = false; };
  }, [file.id, file.previewKind, file.previewStatus]);
  return <button className="sc-thumb" onClick={onClick} title={file.fileName}>
    {url ? <img src={url} alt={file.fileName} /> : <span>{file.previewKind === 'VIDEO' ? '▶' : file.extension?.toUpperCase()}</span>}
  </button>;
}
function Preview({ initial, close, reportError, canRetry }) {
  const [file, setFile] = useState(initial); const [url, setUrl] = useState('');
  const [error, setError] = useState(''); const [downloading, setDownloading] = useState(false);
  const downloadController = useRef(null); const blobUrl = useRef(''); const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; downloadController.current?.abort(); if (blobUrl.current) URL.revokeObjectURL(blobUrl.current); }; }, []);
  useEffect(() => {
    let alive = true;
    const load = async () => {
      if (document.hidden) return;
      try {
        const current = await committee.attachment(initial.id);
        if (!alive) return;
        setFile(current); setError('');
        if (current.previewStatus === 'READY') { await committee.read(current.id); if (alive) setUrl(committeeContent(current.id)); }
      } catch (e) { if (alive) { setUrl(''); setError(e.message); reportError(e); } }
    };
    void load(); const timer = setInterval(load, 5000);
    const key = (e) => { if (e.key === 'Escape') close(); };
    window.addEventListener('keydown', key); document.addEventListener('visibilitychange', load);
    return () => { alive = false; clearInterval(timer); window.removeEventListener('keydown', key); document.removeEventListener('visibilitychange', load); };
  }, [initial.id]);
  const download = async () => {
    if (downloading) { downloadController.current?.abort(); return; }
    const controller = new AbortController(); downloadController.current = controller; setDownloading(true);
    try {
      // Supported browsers stream to the user's chosen file; the bounded Blob fallback remains cancellable.
      const handle = typeof window.showSaveFilePicker === 'function' ? await window.showSaveFilePicker({ suggestedName: file.fileName }) : null;
      await committee.read(file.id);
      if (controller.signal.aborted) return;
      const response = await fetch(committeeContent(file.id, false), { credentials: 'same-origin', signal: controller.signal });
      if (!response.ok) { const failure = new Error('文件读取失败，请重新打开附件'); failure.response = { status: response.status }; throw failure; }
      if (handle) await response.body.pipeTo(await handle.createWritable(), { signal: controller.signal });
      else {
        const blob = await response.blob(); if (!mounted.current || controller.signal.aborted) return;
        if (blobUrl.current) URL.revokeObjectURL(blobUrl.current); blobUrl.current = URL.createObjectURL(blob);
        const link = document.createElement('a'); link.href = blobUrl.current; link.download = file.fileName; link.click();
      }
    } catch (e) { if (mounted.current && e.name !== 'AbortError') { setError(e.message); reportError(e); } }
    finally { if (mounted.current) setDownloading(false); downloadController.current = null; }
  };
  return <div className="sc-overlay" role="dialog" aria-modal="true" aria-label="附件预览">
    <section className="sc-preview"><header><strong>{file.fileName}</strong><div><button onClick={download}>{downloading ? '取消下载' : '下载原件'}</button><button onClick={close}>关闭</button></div></header>
      {error ? <p className="sc-error">{error}</p> : file.previewStatus !== 'READY' ? <div className="sc-empty"><p>{statusText(file)}</p><p>{file.failureMessage || '转换完成后自动显示，也可下载原件。'}</p>{canRetry && file.previewStatus === 'FAILED' && <button onClick={async () => { try { await committee.retry(file.id); setFile({ ...file, previewStatus: 'QUEUED' }); } catch (e) { setError(e.message); } }}>重新生成预览</button>}</div>
        : !url ? <p className="sc-empty">正在打开附件…</p>
        : file.previewKind === 'VIDEO' ? <video src={url} controls playsInline preload="metadata" onError={() => setError('播放中断，请重新打开附件或下载原件')} />
        : ['IMAGE','HEIF'].includes(file.previewKind) ? <div className="sc-image"><img src={url} alt={file.fileName} onClick={(e) => e.currentTarget.classList.toggle('sc-natural')} /></div>
        : <iframe src={url} title={file.fileName} />}
    </section>
  </div>;
}
export default function SafetyCommitteePage({ projectId, currentUser, onAccessLost }) {
  const [categories, setCategories] = useState([]); const [filter, setFilter] = useState(''); const [page, setPage] = useState(1);
  const [data, setData] = useState({ records: [], total: 0 }); const [lastSync, setLastSync] = useState('');
  const [error, setError] = useState(''); const [newRecords, setNewRecords] = useState(false);
  const latest = useRef(null); const inFlight = useRef(false); const alive = useRef(true);
  const [detail, setDetail] = useState(null); const [form, setForm] = useState(null); const [busy, setBusy] = useState(false);
  const [preview, setPreview] = useState(null); const uploads = useRef(new Map()); const queue = useRef(new Set()); const uploadGeneration = useRef(0); const scroll = useRef(0); const root = useRef(null);
  const canSubmit = hasProjectPermission(currentUser, projectId, 'safety_committee.submit');
  const reportError = (e) => { setError(e.message || '暂时无法连接，恢复网络后自动重试'); if ([401,403].includes(e.response?.status)) { uploads.current.forEach((c) => c.abort()); setData({ records: [], total: 0 }); setPreview(null); onAccessLost(); } };
  useEffect(() => { alive.current = true; return () => { alive.current = false; uploadGeneration.current += 1; uploads.current.forEach((c) => c.abort()); }; }, []);
  useEffect(() => { committee.categories(projectId).then(setCategories).catch(reportError); }, [projectId]);
  useEffect(() => {
    let disposed = false; const controller = new AbortController();
    const refresh = async () => {
      if (document.hidden || inFlight.current || !projectId) return;
      inFlight.current = true;
      try {
        if (form) { await committee.categories(projectId); return; }
        if (detail) { const value = await committee.detail(detail.id); if (!disposed) setDetail(value); return; }
        const next = await committee.list({ projectId, category: filter || undefined, pageNo: page }, controller.signal);
        if (disposed) return;
        const merged = mergeCommitteePage(null, next, page, latest.current);
        if (merged.hasNew) setNewRecords(true);
        else { setData(merged.data); latest.current = merged.latestId; }
        setLastSync(new Date().toLocaleTimeString('zh-CN', { hour12: false })); setError('');
        if (page > 1 && next.total <= (page - 1) * 20 && !newRecords) setPage(Math.max(1, Math.ceil(next.total / 20)));
      } catch (e) { if (!disposed && e.code !== 'ERR_CANCELED') reportError(e); }
      finally { inFlight.current = false; }
    };
    void refresh(); const timer = setInterval(refresh, 5000);
    document.addEventListener('visibilitychange', refresh); window.addEventListener('online', refresh);
    return () => { disposed = true; controller.abort(); clearInterval(timer); document.removeEventListener('visibilitychange', refresh); window.removeEventListener('online', refresh); };
  }, [projectId, filter, page, detail?.id, Boolean(form)]);
  const newest = () => { latest.current = null; setNewRecords(false); setPage(1); };
  const openDetail = async (id) => { try { scroll.current = root.current?.scrollTop || 0; setDetail(await committee.detail(id)); root.current?.scrollTo(0,0); } catch (e) { reportError(e); } };
  const back = () => { uploadGeneration.current += 1; queue.current.clear(); uploads.current.forEach((c) => c.abort()); setForm(null); setDetail(null); setError(''); requestAnimationFrame(() => root.current?.scrollTo(0, scroll.current)); };
  const edit = (record) => { if (!record) scroll.current = root.current?.scrollTop || 0; root.current?.scrollTo(0,0); uploadGeneration.current += 1; queue.current.clear(); setForm({ id: record?.id, expectedVersion: record?.version, requestKey: committeeKey(), category: record?.category || '', conclusion: record?.conclusion || '', files: activeFiles(record).map((a) => ({ key: committeeKey(), attachment: a, state: 'done' })) }); setError(''); };
  const changeFile = (key, changes) => { if (alive.current) setForm((f) => f ? { ...f, files: f.files.map((a) => a.key === key ? { ...a, ...changes } : a) } : f); };
  const upload = async (item, draft) => {
    if (uploads.current.has(item.key)) return;
    const controller = new AbortController(); uploads.current.set(item.key, controller); changeFile(item.key, { state: 'uploading', message: '等待上传' });
    try {
      const attachment = await uploadCommitteeFile(item.file, { projectId, draftKey: draft.requestKey, targetRecordId: draft.id || null }, currentUser.id,
        (message) => changeFile(item.key, { message }), controller.signal);
      changeFile(item.key, { attachment, state: 'done', message: '' });
    } catch (e) { changeFile(item.key, { state: 'failed', message: controller.signal.aborted ? '已暂停，可重试续传' : e.message }); if ([401,403].includes(e.response?.status)) reportError(e); }
    finally { uploads.current.delete(item.key); }
  };
  const addFiles = (files) => {
    if (form.files.length + files.length > 30) { setError('每条巡检最多 30 个附件'); return; }
    const items = [...files].map((file) => ({ key: committeeKey(), file, state: 'uploading' }));
    setForm((f) => ({ ...f, files: [...f.files, ...items] }));
    // Sequential work limits memory to one 8 MiB chunk even when selecting many videos.
    const generation = uploadGeneration.current; items.forEach(item => queue.current.add(item.key));
    void (async () => { for (const item of items) { if (!alive.current || generation !== uploadGeneration.current) break; if (queue.current.has(item.key)) await upload(item, form); } })();
  };
  const remove = async (item) => { queue.current.delete(item.key); uploads.current.get(item.key)?.abort(); if (item.attachment?.status === 'PENDING') { try { await committee.discard(item.attachment.id); } catch (e) { reportError(e); return; } } setForm((f) => ({ ...f, files: f.files.filter((a) => a.key !== item.key) })); };
  const save = async (e) => {
    e.preventDefault(); if (busy || form.files.some((a) => a.state !== 'done')) { setError('请完成附件上传，或明确移除失败的附件后提交'); return; }
    setBusy(true); setError('');
    try { const payload = { category: form.category, conclusion: form.conclusion, attachmentIds: form.files.map((a) => a.attachment.id) }; const result = form.id ? await committee.edit(form.id, { ...payload, expectedVersion: form.expectedVersion }) : await committee.create({ ...payload, projectId, requestKey: form.requestKey }); setForm(null); setDetail(result); newest(); }
    catch (e) { reportError(e); } finally { setBusy(false); }
  };
  const deleteRecord = async () => { try { if (await confirmAdministrativeDeletion('COMMITTEE_INSPECTION', detail.id)) back(); } catch (e) { reportError(e); } };
  const attachmentCards = (files) => <div className="sc-files">{files.map((a) => <div className="sc-file" key={a.id}><Thumbnail file={a} onClick={() => setPreview(a)} /><div><button className="sc-link" onClick={() => setPreview(a)}>{a.fileName}</button><small>{committeeSize(a.fileSize)} {statusText(a)} {a.status === 'HISTORICAL' ? ' · 历史附件' : ''}</small></div></div>)}</div>;
  return <main ref={root} className="sc-page">
    <header className="sc-header"><div><h2>安委会巡检</h2><p>随时记录现场安全问题，共享项目巡检信息</p></div><div>{form || detail ? <button disabled={busy} onClick={back}>返回巡检记录</button> : canSubmit && <button className="sc-primary" onClick={() => edit(null)}>＋ 上报巡检</button>}</div></header>
    {error && <div role="alert" className="sc-error">{error}{lastSync && ` · 上次更新 ${lastSync}`}</div>}
    {form ? <form className="sc-panel sc-form" onSubmit={save}><h3>{form.id ? '修改本人巡检' : '上报巡检'}</h3><p className="sc-muted">检查人：{currentUser.realName || currentUser.username}　检查时间：{form.id ? committeeDate(detail?.inspectedAt) : '提交成功时自动记录'}</p>
      <label>安全隐患分类 <em>必选</em><select required value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })}><option value="">请选择分类</option>{categories.map((c) => <option key={c}>{c}</option>)}</select></label>
      <label>检查结论 <span className="sc-muted">选填 · {form.conclusion.length}/2000</span><textarea rows={6} maxLength={2000} value={form.conclusion} onChange={(e) => setForm({ ...form, conclusion: e.target.value })} placeholder="填写现场检查情况…" /></label>
      <label>现场附件 <span className="sc-muted">选填 · {form.files.length}/30</span><input type="file" accept={committeeAccept} multiple onChange={(e) => { addFiles(e.target.files); e.target.value = ''; }} /></label><p className="sc-muted">照片 15 MB、办公文件 100 MB、视频 500 MB；上传中断后可重试续传。</p>
      {form.files.map((a) => <div className="sc-upload" key={a.key}><div>{a.file?.name || a.attachment.fileName}<small>{a.state === 'done' ? '上传完成' : a.message}</small></div><div>{a.state === 'uploading' && <button type="button" onClick={() => { queue.current.delete(a.key); const controller = uploads.current.get(a.key); if (controller) controller.abort(); else changeFile(a.key, { state: 'failed', message: '已暂停，可重试续传' }); }}>暂停</button>}{a.state === 'failed' && <button type="button" onClick={() => upload(a, form)}>重试</button>}<button type="button" onClick={() => remove(a)}>移除</button></div></div>)}
      <footer><button className="sc-primary" disabled={busy || form.files.some((a) => a.state !== 'done')}>{busy ? '正在保存…' : '提交保存'}</button></footer>
    </form> : detail ? <><section className="sc-panel"><div className="sc-detail-head"><div><span className="sc-category">{detail.category}</span><h3>{detail.inspectorName}的巡检记录</h3><p className="sc-muted">检查时间：{committeeDate(detail.inspectedAt)}（北京时间）　版本 {detail.version}</p></div><div>{detail.canEdit && <button onClick={() => edit(detail)}>修改本人记录</button>}{detail.canDelete && <button className="sc-danger" onClick={deleteRecord}>永久删除</button>}</div></div><h4>检查结论</h4><p className="sc-conclusion">{detail.conclusion || '未填写'}</p><h4>现场附件</h4>{activeFiles(detail).length ? attachmentCards(activeFiles(detail)) : <p className="sc-muted">无附件</p>}</section><section className="sc-panel"><h3>修改记录</h3>{(detail.logs || []).map((log) => <div className="sc-log" key={log.id}><strong>{log.operatorName} · {log.action === 'CREATE' ? '上报巡检' : log.action === 'EDIT' ? '修改巡检' : '重新生成预览'}</strong><small>{committeeDate(log.createTime)}</small>{log.action === 'EDIT' && <details><summary>查看修改前后内容</summary>{[['修改前',log.beforeJson],['修改后',log.afterJson]].map(([label,raw]) => { let v; try { v = JSON.parse(raw); } catch { v = null; } return <div key={label}><h4>{label}</h4>{v && <><p>{v.category}</p><p className="sc-conclusion">{v.conclusion || '未填写结论'}</p>{attachmentCards(detail.attachments.filter((a) => v.attachmentIds?.includes(a.id)))}</>}</div>; })}</details>}</div>)}</section></> : <>
      <section className="sc-toolbar"><label>安全隐患分类 <select value={filter} onChange={(e) => { setFilter(e.target.value); newest(); }}><option value="">全部分类</option>{categories.map((c) => <option key={c}>{c}</option>)}</select></label><span className="sc-muted">{lastSync ? `${lastSync} 更新 · 每 5 秒同步` : '正在加载巡检记录…'}</span></section>
      {newRecords && <button className="sc-new" onClick={newest}>有新的巡检记录，点击查看最新</button>}
      <section className="sc-panel sc-list"><div className="sc-table-wrap"><table><thead><tr><th>检查人</th><th>检查时间（北京时间）</th><th>安全隐患分类</th><th>检查结论</th><th>现场附件</th><th>操作</th></tr></thead><tbody>{data.records.map((r) => <tr key={r.id}><td>{r.inspectorName}</td><td className="sc-date">{committeeDate(r.inspectedAt)}</td><td><span className="sc-category">{r.category}</span></td><td className="sc-excerpt">{r.conclusion || <span className="sc-muted">未填写</span>}</td><td><div className="sc-thumbs">{activeFiles(r).slice(0,3).map((a) => <Thumbnail key={a.id} file={a} onClick={() => setPreview(a)} />)}{activeFiles(r).length > 3 && <span>+{activeFiles(r).length - 3}</span>}</div></td><td><button onClick={() => openDetail(r.id)}>详情</button></td></tr>)}</tbody></table>{!data.records.length && <div className="sc-empty">暂无巡检记录{filter ? '，可切换分类查看' : ''}</div>}</div><footer><span>共 {data.total} 条 · 第 {page}/{Math.max(1, Math.ceil(data.total/20))} 页</span><div><button disabled={page <= 1} onClick={() => setPage(page - 1)}>上一页</button><button disabled={page * 20 >= data.total} onClick={() => setPage(page + 1)}>下一页</button></div></footer></section>
    </>}
    {preview && <Preview key={preview.id} initial={preview} close={() => setPreview(null)} reportError={reportError} canRetry={Boolean(detail?.canEdit || detail?.canDelete)} />}
  </main>;
}
