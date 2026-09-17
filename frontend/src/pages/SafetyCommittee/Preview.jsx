import React, { useEffect, useRef, useState } from 'react';
import { committee, committeeContent } from '../../services/safetyCommittee';

export default function Preview({ initial, close, reportError, canRetry, onChange }) {
  const [file, setFile] = useState(initial);
  const [url, setUrl] = useState('');
  const [poster, setPoster] = useState('');
  const [localVideo, setLocalVideo] = useState('');
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const mounted = useRef(true), currentFile = useRef(initial), generation = useRef(0), rotating = useRef(false);
  const downloadController = useRef(null), blobUrl = useRef(''), loadedVersion = useRef(''), renewedAt = useRef(0);
  const accept = (next) => {
    if ((next.rotationVersion || 1) < (currentFile.current.rotationVersion || 1)) return false;
    if (next.rotationVersion !== currentFile.current.rotationVersion || next.previewStatus !== 'READY') {
      setUrl(''); setPoster(''); loadedVersion.current = '';
    }
    currentFile.current = next; setFile(next); onChange?.(next); return true;
  };
  const load = async () => {
    if (document.hidden || rotating.current || !mounted.current) return;
    const ticket = ++generation.current;
    const valid = () => mounted.current && ticket === generation.current && !rotating.current;
    try {
      const next = await committee.attachment(initial.id);
      if (!valid() || !accept(next)) return;
      const version = String(next.rotationVersion || 1);
      if (next.previewStatus === 'READY' && (loadedVersion.current !== version || Date.now() - renewedAt.current > 12 * 60 * 1000)) {
        await committee.read(next.id);
        if (valid()) { loadedVersion.current = version; renewedAt.current = Date.now(); setUrl(`${committeeContent(next.id)}&v=${version}`); }
      } else if (next.previewStatus !== 'READY') {
        const state = await committee.thumbnail(next.id);
        if (!valid() || state.status !== 'READY') return;
        await committee.read(next.id);
        if (valid()) setPoster(`${committeeContent(next.id, true, true)}&v=${version}`);
      }
    } catch (e) { if (valid()) { setUrl(''); setError(e.message); reportError(e); } }
  };
  useEffect(() => {
    mounted.current = true;
    void load(); const timer = setInterval(load, 5000);
    const key = (e) => { if (e.key === 'Escape') close(); };
    window.addEventListener('keydown', key); document.addEventListener('visibilitychange', load);
    return () => {
      mounted.current = false; generation.current++; clearInterval(timer);
      downloadController.current?.abort(); if (blobUrl.current) URL.revokeObjectURL(blobUrl.current);
      window.removeEventListener('keydown', key); document.removeEventListener('visibilitychange', load);
    };
  }, [initial.id]);
  useEffect(() => {
    if (!initial.localFile || initial.previewKind !== 'VIDEO') return undefined;
    const next = URL.createObjectURL(initial.localFile); setLocalVideo(next);
    return () => URL.revokeObjectURL(next);
  }, [initial.localFile, initial.previewKind]);
  const rotate = async (step) => {
    if (rotating.current || !currentFile.current.canRotate || downloading) return;
    rotating.current = true; setSaving(true); setError(''); generation.current++;
    const before = currentFile.current;
    try {
      const next = await committee.rotate(before.id, ((before.rotationDegrees || 0) + step + 360) % 360, before.rotationVersion || 1);
      if (mounted.current) accept(next);
    } catch (e) { if (mounted.current) { setError(e.message); reportError(e); } }
    finally { rotating.current = false; if (mounted.current) { setSaving(false); void load(); } }
  };
  const download = async (adjusted = false) => {
    if (downloading) { downloadController.current?.abort(); return; }
    const controller = new AbortController(); downloadController.current = controller; setDownloading(true);
    const extension = file.previewKind === 'VIDEO' ? 'mp4' : file.previewKind === 'HEIF' ? 'jpg' : file.extension === 'gif' ? 'gif' : 'png';
    const name = adjusted ? `${file.fileName.replace(/\.[^.]+$/, '')}_已调整.${extension}` : file.fileName;
    try {
      const handle = typeof window.showSaveFilePicker === 'function' ? await window.showSaveFilePicker({ suggestedName: name }) : null;
      await committee.read(file.id); if (controller.signal.aborted) return;
      const response = await fetch(committeeContent(file.id, adjusted), { credentials: 'same-origin', signal: controller.signal });
      if (!response.ok) { const e = new Error('文件读取失败，请重新打开附件'); e.response = { status: response.status }; throw e; }
      if (handle) await response.body.pipeTo(await handle.createWritable(), { signal: controller.signal });
      else {
        const blob = await response.blob(); if (!mounted.current || controller.signal.aborted) return;
        if (blobUrl.current) URL.revokeObjectURL(blobUrl.current); blobUrl.current = URL.createObjectURL(blob);
        const link = document.createElement('a'); link.href = blobUrl.current; link.download = name; link.click();
      }
    } catch (e) { if (mounted.current && e.name !== 'AbortError') { setError(e.message); reportError(e); } }
    finally { if (mounted.current) setDownloading(false); downloadController.current = null; }
  };
  const media = ['IMAGE', 'HEIF', 'VIDEO'].includes(file.previewKind);
  const local = localVideo && !file.rotationDegrees && (file.rotationVersion || 1) === 1 && !url;
  return <div className="sc-overlay" role="dialog" aria-modal="true" aria-label="附件预览">
    <section className="sc-preview"><header><strong>{file.fileName}</strong><div><button onClick={() => download(false)} disabled={saving}>{downloading ? '取消下载' : '下载原件'}</button><button onClick={close}>关闭</button></div></header>
      {media && <div className="sc-rotation-toolbar">
        {file.canRotate && <><button type="button" disabled={saving || downloading} onClick={() => rotate(-90)}>↶ 向左旋转 90°</button><button type="button" disabled={saving || downloading} onClick={() => rotate(90)}>↷ 向右旋转 90°</button></>}
        <span role="status">{saving ? '正在保存角度…' : `已保存角度 ${file.rotationDegrees || 0}°`}</span>
        {!!file.rotationDegrees && <button disabled={file.previewStatus !== 'READY' || saving || downloading} onClick={() => download(true)}>下载调整后文件</button>}
      </div>}
      {error && <p role="alert" className="sc-error">{error}</p>}
      {local ? <video src={localVideo} controls playsInline preload="metadata" /> : file.previewStatus !== 'READY' ? <div className="sc-pending-preview">
        {poster && <img src={poster} alt="附件首页或视频封面" />}
        <p>{file.previewStatus === 'FAILED' ? '预览生成失败' : file.rotationDegrees ? '角度已保存，正在生成调整后预览…' : '预览处理中…'}</p>
        <p>{file.failureMessage || (file.previewStatus === 'WAITING' ? '提交记录后生成完整预览，可先查看缩略图或下载原件。' : '完成后自动显示，可先下载原件。')}</p>
        {(canRetry || file.canRotate) && file.previewStatus === 'FAILED' && <button onClick={async () => { try { await committee.retry(file.id); loadedVersion.current = ''; await load(); } catch (e) { setError(e.message); } }}>重新生成预览</button>}
      </div> : !url ? <p className="sc-empty">正在打开附件…</p>
        : file.previewKind === 'VIDEO' ? <video key={url} src={url} controls playsInline preload="metadata" onError={() => setError('播放中断，请重新打开附件或下载原件')} />
        : ['IMAGE', 'HEIF'].includes(file.previewKind) ? <div className="sc-image"><img src={url} alt={file.fileName} onClick={(e) => e.currentTarget.classList.toggle('sc-natural')} /></div>
        : <iframe src={url} title={file.fileName} />}
    </section>
  </div>;
}
