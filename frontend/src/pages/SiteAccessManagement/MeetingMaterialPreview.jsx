import React, { useEffect, useState } from 'react';
import { downloadMeetingMaterial, getMaterialVersion, materialContentUrl, materialSize, openMaterialReadSession, retryMaterialPreview } from '../../services/meetingMaterials';

export default function MeetingMaterialPreview({ version, canManage, onClose }) {
  const [current, setCurrent] = useState(version);
  const [ready, setReady] = useState(false);
  const [error, setError] = useState('');
  const [expanded, setExpanded] = useState(false);
  const [text, setText] = useState('');
  const [textPage, setTextPage] = useState(0);
  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    const refresh = async () => {
      try {
        const next = await getMaterialVersion(version.id);
        await openMaterialReadSession(version.id);
        if (!active) return;
        setCurrent(next); setReady(next.previewStatus === 'READY'); setError('');
      } catch (e) { if (active) { setReady(false); setError(e.message || '文件读取失败'); } }
    };
    void refresh();
    const timer = window.setInterval(refresh, 15000);
    const key = (event) => { if (event.key === 'Escape') onClose(); };
    window.addEventListener('keydown', key);
    return () => { active = false; controller.abort(); window.clearInterval(timer); window.removeEventListener('keydown', key); };
  }, [version.id, onClose]);
  useEffect(() => {
    if (!ready || current.previewKind !== 'TEXT') return undefined;
    const controller = new AbortController();
    fetch(materialContentUrl(current.id), { signal: controller.signal, headers: { Range: `bytes=${textPage * 1048576}-${(textPage + 1) * 1048576 - 1}` } })
      .then(async (response) => { if (!response.ok) throw new Error('文本读取失败'); return response.text(); })
      .then(setText).catch((e) => { if (!controller.signal.aborted) setError(e.message); });
    return () => controller.abort();
  }, [ready, current.id, current.previewKind, textPage]);
  const download = async () => { try { await downloadMeetingMaterial(current); } catch (e) { setError(e.message); } };
  const regenerate = async () => { try { await retryMaterialPreview(current.id); setReady(false); setCurrent({ ...current, previewStatus: 'QUEUED' }); } catch (e) { setError(e.message); } };
  const kind = current.previewKind;
  return <div className="site-access-modal-mask meeting-material-preview-mask" role="dialog" aria-modal="true" aria-label={`预览 ${current.fileName}`}>
    <section className={`meeting-material-preview${expanded ? ' expanded' : ''}`}>
      <header><div><strong>{current.fileName}</strong><small>V{current.versionNo} · {materialSize(current.fileSize)}</small></div>
        <div className="site-access-row-actions">{canManage && ready && ['OFFICE', 'VIDEO', 'AUDIO', 'HEIF'].includes(kind) && <button onClick={regenerate}>重新生成预览</button>}<button onClick={() => setExpanded(!expanded)}>{expanded ? '还原窗口' : '放大'}</button><button onClick={download}>下载原文件</button><button onClick={onClose} aria-label="关闭预览">关闭</button></div></header>
      <div className="meeting-material-preview-body">
        {error ? <p role="alert">{error}</p> : !ready ? <div className="meeting-material-preview-message"><strong>{current.previewStatus === 'FAILED' ? '预览生成失败' : '正在准备预览…'}</strong><p>文件已留档，可以下载原文件。</p>
          {canManage && current.previewStatus === 'FAILED' && <button onClick={regenerate}>重新生成预览</button>}</div>
          : <>{['PDF', 'OFFICE'].includes(kind) && <iframe src={materialContentUrl(current.id)} title={current.fileName} />}
            {['IMAGE', 'HEIF'].includes(kind) && <img src={materialContentUrl(current.id)} alt={current.fileName} />}
            {kind === 'VIDEO' && <video src={materialContentUrl(current.id)} controls preload="metadata" />}
            {kind === 'AUDIO' && <audio src={materialContentUrl(current.id)} controls preload="metadata" />}
            {kind === 'TEXT' && <pre>{text}</pre>}
            {kind === 'UNSUPPORTED' && <div className="meeting-material-preview-message"><strong>此格式请下载后使用对应软件查看</strong><button onClick={download}>下载原文件</button></div>}</>}
      </div>
      {kind === 'TEXT' && <footer><button disabled={textPage === 0} onClick={() => setTextPage(textPage - 1)}>上一段</button><span>第 {textPage + 1} / {Math.max(1, Math.ceil(current.fileSize / 1048576))} 段</span><button disabled={(textPage + 1) * 1048576 >= current.fileSize} onClick={() => setTextPage(textPage + 1)}>下一段</button></footer>}
    </section>
  </div>;
}
