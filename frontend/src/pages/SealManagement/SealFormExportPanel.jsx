import React, { useEffect, useRef, useState } from 'react';
import { createSealFormExport, downloadSealFormExport, getSealFormExports, retrySealFormExport } from '../../services/seal';
import { formExportRequest } from './formExportModel';
import './form-export.css';

const labels = { PENDING: '等待生成', RUNNING: '正在生成', SUCCEEDED: '生成完成', FAILED: '生成失败', EXPIRED: '文件已过期' };
const scopes = { INITIATED: '我发起的', PENDING_FOR_ME: '待我审批', CC_TO_ME: '抄送我的', ALL: '全部申请' };
const statuses = { DRAFT: '草稿', PENDING_APPROVAL: '审批中', APPROVED: '已通过', REJECTED: '已驳回', WITHDRAWN: '已撤回' };
const message = (error) => error?.response?.data?.message || error?.message || '导出失败，请重试';
const unwrap = (response) => {
  if (Number(response?.code) !== 200) throw new Error(response?.message || '导出请求失败');
  return response.data;
};

export default function SealFormExportPanel({ projectId, projectName, scope, appliedFilters, selected }) {
  const [open, setOpen] = useState(false);
  const [mode, setMode] = useState('FILTER');
  const [jobs, setJobs] = useState([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [refresh, setRefresh] = useState(0);
  const requestRef = useRef(null);
  const activeRef = useRef(true);
  const closeRef = useRef(null);
  const triggerRef = useRef(null);
  const dialogRef = useRef(null);

  useEffect(() => {
    activeRef.current = true;
    return () => { activeRef.current = false; };
  }, []);

  useEffect(() => {
    if (!open) return undefined;
    let cancelled = false;
    let timer;
    const load = async () => {
      try {
        const data = unwrap(await getSealFormExports(projectId));
        if (!cancelled) setJobs(data || []);
      } catch (loadError) {
        if (!cancelled) {
          setError(message(loadError));
          if ([401, 403].includes(loadError?.response?.status)) setJobs([]);
        }
      } finally {
        if (!cancelled) timer = setTimeout(load, 3000);
      }
    };
    load();
    return () => { cancelled = true; clearTimeout(timer); };
  }, [open, projectId, refresh]);

  useEffect(() => {
    if (!open) return undefined;
    closeRef.current?.focus();
    const keydown = (event) => {
      if (event.key === 'Escape') setOpen(false);
      if (event.key !== 'Tab') return;
      const elements = [...(dialogRef.current?.querySelectorAll('button:not(:disabled), input:not(:disabled)') || [])];
      const first = elements[0];
      const last = elements.at(-1);
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    };
    document.addEventListener('keydown', keydown);
    return () => { document.removeEventListener('keydown', keydown); triggerRef.current?.focus(); };
  }, [open]);

  const submit = async (retryId) => {
    if (busy) return;
    setBusy(true);
    setError('');
    const body = formExportRequest(projectId, scope, appliedFilters, mode, selected);
    const signature = retryId ? `retry:${retryId}` : JSON.stringify(body);
    if (requestRef.current?.signature === signature && requestRef.current.jobId) {
      const previous = jobs.find((job) => job.id === requestRef.current.jobId);
      if (previous && ['PENDING', 'RUNNING'].includes(previous.status)) { setBusy(false); return; }
      // A deliberate new export after completion must capture newly approved records too.
      requestRef.current = null;
    }
    if (requestRef.current?.signature !== signature) {
      requestRef.current = { signature, key: crypto.randomUUID() };
    }
    try {
      const job = unwrap(retryId
        ? await retrySealFormExport(retryId, requestRef.current.key)
        : await createSealFormExport({ ...body, requestKey: requestRef.current.key }));
      if (!activeRef.current) return;
      requestRef.current.jobId = job.id;
      setJobs((current) => [job, ...current.filter((item) => item.id !== job.id)].slice(0, 20));
      setRefresh((value) => value + 1);
    } catch (submitError) {
      if (activeRef.current) setError(message(submitError));
      if (submitError?.response?.status >= 400 && submitError.response.status < 500) requestRef.current = null;
    } finally {
      if (activeRef.current) setBusy(false);
    }
  };

  const download = async (job) => {
    setBusy(true);
    setError('');
    try {
      const blob = await downloadSealFormExport(job.id);
      if (!activeRef.current) return;
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = job.fileName;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (downloadError) {
      if (activeRef.current) setError(message(downloadError));
    } finally {
      if (activeRef.current) setBusy(false);
    }
  };

  return <>
    <button ref={triggerRef} className="seal-merge-trigger" onClick={() => { setMode(selected.length ? 'SELECTED' : 'FILTER'); setError(''); setOpen(true); }}>合并导出 PDF</button>
    {open && <div className="seal-form-export-mask" onMouseDown={() => setOpen(false)}>
      <section ref={dialogRef} className="seal-form-export-dialog" role="dialog" aria-modal="true" aria-labelledby="seal-form-export-title" onMouseDown={(event) => event.stopPropagation()}>
        <header><div><h2 id="seal-form-export-title">合并导出用印申请单</h2><p>{projectName}</p></div><button ref={closeRef} aria-label="关闭合并导出" onClick={() => setOpen(false)}>×</button></header>
        <div className="seal-form-export-body">
          <div className="seal-form-export-modes">
            <label className={mode === 'FILTER' ? 'active' : ''}><input type="radio" name="sealFormExportMode" checked={mode === 'FILTER'} onChange={() => setMode('FILTER')} /><strong>当前筛选全部</strong><small>包含全部分页中已通过的申请</small></label>
            <label className={mode === 'SELECTED' ? 'active' : ''}><input type="radio" name="sealFormExportMode" checked={mode === 'SELECTED'} onChange={() => setMode('SELECTED')} /><strong>已勾选申请（{selected.length} 份）</strong><small>包含跨页保留的选择</small></label>
          </div>
          {mode === 'FILTER' && <p className="seal-form-export-summary">已执行查询：{scopes[scope]} · {statuses[appliedFilters.status] || '全部状态'} · {appliedFilters.keyword || '全部关键词'} · {appliedFilters.startDate || '不限开始日期'} 至 {appliedFilters.endDate || '不限结束日期'}</p>}
          <p className="seal-form-export-summary">每次最多 500 份，同时最多生成 2 个任务。每份申请另起一页，按单面打印排列；资料份数不增加打印次数，附件不合并。生成文件保留 7 天。</p>
          <div className="seal-form-export-create"><button className="primary" disabled={busy || (mode === 'SELECTED' && (!selected.length || selected.length > 500))} onClick={() => submit()}>{busy ? '处理中…' : '开始合并'}</button><span>关闭窗口后仍会继续生成</span></div>
          {mode === 'SELECTED' && selected.length > 500 && <p role="alert">已超过 500 份，请减少勾选数量。</p>}
          {error && <div className="seal-form-export-error" role="alert">{error}</div>}
          <div className="seal-form-export-history-head"><h3>我的导出任务</h3><button onClick={() => { setError(''); setRefresh((value) => value + 1); }}>刷新</button></div>
          <div className="seal-form-export-jobs" aria-live="polite">
            {!jobs.length && <p className="seal-form-export-summary">暂无导出任务</p>}
            {jobs.map((job) => <article key={job.id}>
              <div><strong>{job.fileName}</strong><span>{labels[job.status] || job.status}</span></div>
              <p>{job.applicationCount} 份 · 已处理 {job.processedCount} 份{job.pageCount > 0 ? ` · ${job.pageCount} 页` : ''} · {String(job.createTime || '').replace('T', ' ').slice(0, 16)}</p>
              {['PENDING', 'RUNNING'].includes(job.status) && <progress max={job.applicationCount || 1} value={job.processedCount} aria-label="合并进度" />}
              {job.errorMessage && <p className="seal-form-export-error">{job.errorMessage}</p>}
              <div className="seal-form-export-job-actions">
                {job.status === 'SUCCEEDED' && <><small>有效至 {String(job.expiresTime || '').replace('T', ' ').slice(0, 16)}</small><button className="primary" disabled={busy} onClick={() => download(job)}>下载 PDF</button></>}
                {['FAILED', 'EXPIRED'].includes(job.status) && <button disabled={busy} onClick={() => submit(job.id)}>重新生成</button>}
              </div>
            </article>)}
          </div>
        </div>
      </section>
    </div>}
  </>;
}
