import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  confirmDocumentDistribution,
  createDocumentDistribution,
  createIncomingBatch,
  disputeDocumentDistribution,
  exportDocumentCirculationLedger,
  getDistributionQrSvg,
  getDocumentDistribution,
  getDocumentMatchCandidates,
  getDocumentRecipientCandidates,
  getIncomingBatch,
  getMyDocumentDistribution,
  listDocumentDistributions,
  listIncomingBatches,
  publishIncomingBatch,
  updateIncomingBatch,
  uploadCirculationFile,
  voidDocumentDistribution,
  voidIncomingBatch,
} from '../../services/documentCirculation';
import { downloadProjectDocument } from '../../services/document';
import { hasProjectPermission, isPlatformAdmin } from '../../utils/permissions';
import './index.css';
import './reissue.css';

const unwrap = (response, fallback) => {
  if (!response || Number(response.code) !== 200) throw new Error(response?.message || fallback);
  return response.data;
};
const rows = (data) => data?.records || data?.items || [];
const message = (error, fallback) => error?.response?.data?.message || error?.message || fallback;
const localDateTime = () => {
  const date = new Date();
  date.setMinutes(date.getMinutes() - date.getTimezoneOffset());
  return date.toISOString().slice(0, 16);
};
const formatTime = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : '-';
const emptySource = () => ({ sourceOrganization: '', senderName: '', sourceReferenceNo: '', receiveMethod: '', receivedAt: localDateTime(), remark: '' });
const itemRequest = (item, index) => ({
  id: item.id,
  fileResourceId: item.fileResourceId,
  folderId: item.folderId || 0,
  itemOrder: index + 1,
  title: item.title,
  documentNo: item.documentNo || '',
  documentType: item.documentType || 'DRAWING',
  externalRevision: item.externalRevision || '',
  matchMode: item.matchMode || 'NEW_DOCUMENT',
  targetDocumentId: item.matchMode === 'NEW_VERSION' ? item.targetDocumentId : null,
  duplicateRevisionReason: item.duplicateRevisionReason || '',
  changeNote: item.changeNote || '',
});

function SignaturePad({ onChange }) {
  const canvasRef = useRef(null);
  const drawing = useRef(false);
  const point = (event) => {
    const rect = canvasRef.current.getBoundingClientRect();
    const source = event.touches?.[0] || event;
    return { x: (source.clientX - rect.left) * (canvasRef.current.width / rect.width), y: (source.clientY - rect.top) * (canvasRef.current.height / rect.height) };
  };
  const start = (event) => {
    event.preventDefault(); drawing.current = true;
    const context = canvasRef.current.getContext('2d'); const value = point(event);
    context.beginPath(); context.moveTo(value.x, value.y);
  };
  const move = (event) => {
    if (!drawing.current) return; event.preventDefault();
    const context = canvasRef.current.getContext('2d'); const value = point(event);
    context.lineWidth = 3; context.lineCap = 'round'; context.strokeStyle = '#172033';
    context.lineTo(value.x, value.y); context.stroke(); onChange?.(true);
  };
  const clear = () => {
    const canvas = canvasRef.current; const context = canvas.getContext('2d');
    context.fillStyle = '#fff'; context.fillRect(0, 0, canvas.width, canvas.height); onChange?.(false);
  };
  useEffect(() => { clear(); }, []);
  return <div className="dc-signature"><canvas ref={canvasRef} width="720" height="220" onMouseDown={start} onMouseMove={move} onMouseUp={() => { drawing.current = false; }} onMouseLeave={() => { drawing.current = false; }} onTouchStart={start} onTouchMove={move} onTouchEnd={() => { drawing.current = false; }} /><button type="button" onClick={clear}>清空签名</button></div>;
}

function RecipientSelector({ options, selected, items, itemKey, onChange }) {
  return <div className="dc-recipient-grid">{options.map((option) => {
    const state = selected[option.userId] || {};
    const selectedNow = Boolean(state.selected || option.mandatory);
    const channel = state.channel || 'ELECTRONIC';
    return <div className={option.mandatory ? 'mandatory' : ''} key={option.userId}>
      <label className="check"><input type="checkbox" checked={selectedNow} disabled={option.mandatory}
        onChange={(event) => onChange(option.userId, { ...state, selected: event.target.checked, channel })} />
        <strong>{option.realName}</strong>{option.mandatory && <em>强制接收</em>}</label>
      <small>{option.roleNames || '项目成员'} · {option.phone || '无手机号'}</small>
      <select value={channel} onChange={(event) => onChange(option.userId, { ...state, selected: true, channel: event.target.value })}>
        <option value="ELECTRONIC">电子</option><option value="PAPER">纸质</option><option value="BOTH">电子+纸质</option>
      </select>
      {selectedNow && channel !== 'ELECTRONIC' && <div className="dc-copy-grid">{items.map((item) => {
        const key = itemKey(item);
        return <label key={key}><span>{item.documentNo || item.title || item.fileName}</span><input type="number" min="1" max="999"
          value={state.copies?.[key] || 1} onChange={(event) => onChange(option.userId, { ...state, selected: true, channel,
            copies: { ...(state.copies || {}), [key]: Math.max(1, Math.min(999, Number(event.target.value) || 1)) } })} /><em>份</em></label>;
      })}</div>}
    </div>;
  })}</div>;
}

export default function DocumentCirculationPage({ projectId, projectList, currentUser, distributionTarget }) {
  const directRecipient = Boolean(distributionTarget?.id);
  const admin = isPlatformAdmin(currentUser);
  const canReceive = admin || hasProjectPermission(currentUser, projectId, 'document.receive');
  const canIssue = admin || hasProjectPermission(currentUser, projectId, 'document.issue');
  const canView = admin || hasProjectPermission(currentUser, projectId, 'document.circulation.view');
  const canExport = admin || hasProjectPermission(currentUser, projectId, 'document.circulation.export');
  const projectName = projectList?.find((item) => Number(item.id) === Number(projectId))?.projectName || '当前项目';
  const [tab, setTab] = useState('incoming');
  const [incoming, setIncoming] = useState([]);
  const [distributions, setDistributions] = useState([]);
  const [draft, setDraft] = useState(null);
  const [source, setSource] = useState(emptySource);
  const [uploading, setUploading] = useState({});
  const [recipientOptions, setRecipientOptions] = useState([]);
  const [selectedRecipients, setSelectedRecipients] = useState({});
  const [distribution, setDistribution] = useState({ deadline: '', messageNote: '', electronicSignatureRequired: false, paperSignatureRequired: true });
  const [detail, setDetail] = useState(null);
  const [qrSvg, setQrSvg] = useState('');
  const [myTask, setMyTask] = useState(null);
  const [signatureDrawn, setSignatureDrawn] = useState(false);
  const [disputeNote, setDisputeNote] = useState('');
  const [voidReason, setVoidReason] = useState('');
  const [incomingVoidReason, setIncomingVoidReason] = useState('');
  const [reissue, setReissue] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const signatureCanvas = () => document.querySelector('.dc-signature canvas');

  const refresh = useCallback(async () => {
    if (!projectId || !canView) return;
    setError('');
    try {
      const [incomingResponse, distributionResponse] = await Promise.all([
        listIncomingBatches({ projectId, pageNo: 1, pageSize: 100 }),
        listDocumentDistributions({ projectId, pageNo: 1, pageSize: 100 }),
      ]);
      setIncoming(rows(unwrap(incomingResponse, '收文批次加载失败')));
      setDistributions(rows(unwrap(distributionResponse, '发放批次加载失败')));
    } catch (loadError) { setError(message(loadError, '图纸收发数据加载失败')); }
  }, [canView, projectId]);

  useEffect(() => { refresh(); }, [refresh]);
  useEffect(() => {
    if (!directRecipient) return;
    setTab('receipt'); setBusy(true); setError('');
    getMyDocumentDistribution(distributionTarget.id)
      .then((response) => setMyTask(unwrap(response, '签收任务加载失败')))
      .catch((loadError) => setError(message(loadError, '签收任务加载失败')))
      .finally(() => setBusy(false));
  }, [directRecipient, distributionTarget?.id, distributionTarget?.openedAt]);

  const createDraft = async () => {
    setBusy(true); setError('');
    try {
      const data = unwrap(await createIncomingBatch({ projectId, ...source, items: [] }), '收文草稿创建失败');
      setDraft(data);
    } catch (createError) { setError(message(createError, '收文草稿创建失败')); }
    finally { setBusy(false); }
  };

  const uploadFiles = async (fileList) => {
    if (!draft) return;
    let nextItems = [...(draft.items || [])];
    for (const file of [...fileList]) {
      setUploading((current) => ({ ...current, [file.name]: 0 }));
      try {
        const session = await uploadCirculationFile({ projectId, incomingBatchId: draft.id, file,
          onProgress: (progress) => setUploading((current) => ({ ...current, [file.name]: progress })) });
        nextItems = [...nextItems, { fileResourceId: session.fileResourceId, fileName: file.name, fileSize: file.size,
          title: file.name.replace(/\.[^.]+$/, ''), documentType: 'DRAWING', documentNo: '', externalRevision: '',
          matchMode: 'NEW_DOCUMENT', targetDocumentId: null, candidates: [] }];
        setDraft((current) => ({ ...current, items: nextItems }));
      } catch (uploadError) { setError(message(uploadError, `${file.name}上传失败`)); break; }
    }
  };

  const patchItem = (index, values) => setDraft((current) => ({ ...current,
    items: current.items.map((item, itemIndex) => itemIndex === index ? { ...item, ...values } : item) }));

  const matchItem = async (index) => {
    const item = draft.items[index];
    if (!item.documentNo) return setError('请先填写图号或编号');
    try {
      const candidates = unwrap(await getDocumentMatchCandidates({ projectId, documentType: item.documentType, documentNo: item.documentNo }), '匹配候选加载失败') || [];
      patchItem(index, { candidates, ...(candidates.length === 1 ? { matchMode: 'NEW_VERSION', targetDocumentId: candidates[0].documentId } : {}) });
    } catch (matchError) { setError(message(matchError, '匹配候选加载失败')); }
  };

  const saveDraft = async () => {
    const data = unwrap(await updateIncomingBatch(draft.id, { projectId, ...source, expectedVersion: draft.version,
      items: draft.items.map(itemRequest) }), '收文草稿保存失败');
    setDraft(data); return data;
  };

  const loadRecipients = async () => {
    setBusy(true); setError('');
    try {
      const saved = await saveDraft();
      const versionIds = (await Promise.all(saved.items.filter((item) => item.matchMode === 'NEW_VERSION')
        .map(async (item) => {
          const candidates = unwrap(await getDocumentMatchCandidates({ projectId, documentType: item.documentType,
            documentNo: item.documentNo }), '历史版本接收人加载失败') || [];
          return candidates.find((candidate) => Number(candidate.documentId) === Number(item.targetDocumentId))?.currentVersionId;
        }))).filter(Boolean);
      const options = unwrap(await getDocumentRecipientCandidates(projectId, versionIds), '接收人加载失败') || [];
      setRecipientOptions(options);
      setSelectedRecipients((current) => {
        const next = { ...current };
        options.filter((option) => option.mandatory).forEach((option) => { next[option.userId] = next[option.userId] || { selected: true, channel: 'ELECTRONIC' }; });
        return next;
      });
    } catch (recipientError) { setError(message(recipientError, '接收人加载失败')); }
    finally { setBusy(false); }
  };

  const publishDraft = async () => {
    setBusy(true); setError('');
    try {
      const saved = await saveDraft();
      const recipients = recipientOptions.filter((option) => selectedRecipients[option.userId]?.selected || option.mandatory).map((option) => {
        const selected = selectedRecipients[option.userId] || {};
        const channel = selected.channel || 'ELECTRONIC';
        return { userId: option.userId, channel, copies: saved.items.map((item) => ({ incomingItemId: item.id,
          paperCopyCount: channel === 'ELECTRONIC' ? 0 : Math.max(1, Number(selected.copies?.[item.fileResourceId] || 1)) })) };
      });
      const result = unwrap(await publishIncomingBatch(saved.id, { expectedVersion: saved.version,
        items: saved.items.map(itemRequest), distribution: { ...distribution, recipients } }), '收文发布失败');
      setDetail(result); setDraft(null); setRecipientOptions([]); setSelectedRecipients({}); await refresh(); setTab('distribution');
    } catch (publishError) { setError(message(publishError, '收文发布失败')); }
    finally { setBusy(false); }
  };

  const openIncoming = async (id) => {
    try {
      const data = unwrap(await getIncomingBatch(id), '收文详情加载失败');
      if (data.status === 'DRAFT' && canReceive) {
        setSource({ sourceOrganization: data.sourceOrganization || '', senderName: data.senderName || '',
          sourceReferenceNo: data.sourceReferenceNo || '', receiveMethod: data.receiveMethod || '',
          receivedAt: data.receivedAt ? String(data.receivedAt).slice(0, 16) : localDateTime(), remark: data.remark || '' });
        setDraft({ ...data, items: (data.items || []).map((item) => ({ ...item, candidates: [] })) });
        setRecipientOptions([]); setSelectedRecipients({}); setDetail(null); setIncomingVoidReason('');
      } else setDetail(data);
    }
    catch (loadError) { setError(message(loadError, '收文详情加载失败')); }
  };
  const openDistribution = async (id) => {
    try { const data = unwrap(await getDocumentDistribution(id), '发放详情加载失败'); setDetail(data); setQrSvg(''); }
    catch (loadError) { setError(message(loadError, '发放详情加载失败')); }
  };
  const showQr = async (id) => {
    try { setQrSvg(await getDistributionQrSvg(id)); }
    catch (qrError) { setError(message(qrError, '二维码加载失败')); }
  };

  const startReissue = async () => {
    setBusy(true); setError('');
    try {
      const options = unwrap(await getDocumentRecipientCandidates(projectId, detail.items.map((item) => item.versionId)), '接收人加载失败') || [];
      const previous = new Map((detail.recipients || []).map((item) => [Number(item.userId), item.channel]));
      const selected = {};
      options.forEach((option) => {
        const prior = (detail.recipients || []).find((item) => Number(item.userId) === Number(option.userId));
        if (option.mandatory || previous.has(Number(option.userId))) selected[option.userId] = {
          selected: true,
          channel: previous.get(Number(option.userId)) || 'ELECTRONIC',
          copies: Object.fromEntries(detail.items.map((item) => [item.versionId,
            prior?.items?.find((copy) => Number(copy.versionId) === Number(item.versionId))?.paperCopyCount || 1])),
        };
      });
      setReissue({ options, selected, deadline: '', messageNote: detail.messageNote || '', electronicSignatureRequired: detail.electronicSignatureRequired, paperSignatureRequired: detail.paperSignatureRequired });
    } catch (loadError) { setError(message(loadError, '再次发放准备失败')); }
    finally { setBusy(false); }
  };

  const submitReissue = async () => {
    setBusy(true); setError('');
    try {
      const recipients = reissue.options.filter((option) => reissue.selected[option.userId]?.selected).map((option) => {
        const channel = reissue.selected[option.userId].channel;
        return { userId: option.userId, channel, copies: detail.items.map((item) => ({ versionId: item.versionId,
          paperCopyCount: channel === 'ELECTRONIC' ? 0 : Math.max(1, Number(reissue.selected[option.userId].copies?.[item.versionId] || 1)) })) };
      });
      const created = unwrap(await createDocumentDistribution({ projectId, versionIds: detail.items.map((item) => item.versionId), distribution: {
        deadline: reissue.deadline, messageNote: reissue.messageNote,
        electronicSignatureRequired: reissue.electronicSignatureRequired,
        paperSignatureRequired: reissue.paperSignatureRequired, recipients,
      } }), '再次发放失败');
      setDetail(created); setReissue(null); await refresh();
    } catch (createError) { setError(message(createError, '再次发放失败')); }
    finally { setBusy(false); }
  };

  const downloadVersion = async (item, batchId) => {
    try {
      let blob;
      try {
        blob = await downloadProjectDocument(item.documentId, item.versionId, false, batchId);
      } catch (downloadError) {
        if (!String(downloadError?.message || '').includes('已被替代')
          || !window.confirm(`${downloadError.message}\n\n仍要下载本批次精确历史版本吗？`)) throw downloadError;
        blob = await downloadProjectDocument(item.documentId, item.versionId, true, batchId);
      }
      const url = URL.createObjectURL(blob); const anchor = document.createElement('a');
      anchor.href = url; anchor.download = item.fileName || item.title; anchor.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (downloadError) { setError(message(downloadError, '文件下载失败')); }
  };

  const confirmMyTask = async () => {
    setBusy(true); setError('');
    try {
      let signature;
      if (myTask.signatureRequiredForCurrentRecipient) {
        if (!signatureDrawn) throw new Error('请先完成手写签名');
        signature = await new Promise((resolve) => {
          signatureCanvas().toBlob(resolve, 'image/png');
        });
      }
      setMyTask(unwrap(await confirmDocumentDistribution(myTask.id, { signature }), '签收失败'));
    } catch (confirmError) { setError(message(confirmError, '签收失败')); }
    finally { setBusy(false); }
  };

  const submitDispute = async () => {
    setBusy(true); setError('');
    try { setMyTask(unwrap(await disputeDocumentDistribution(myTask.id, disputeNote), '异议提交失败')); setDisputeNote(''); }
    catch (submitError) { setError(message(submitError, '异议提交失败')); }
    finally { setBusy(false); }
  };

  const voidCurrentIncoming = async () => {
    if (!draft?.id || !incomingVoidReason.trim()) return;
    setBusy(true); setError('');
    try {
      await voidIncomingBatch(draft.id, incomingVoidReason.trim());
      setDraft(null); setIncomingVoidReason(''); await refresh();
    } catch (voidError) { setError(message(voidError, '收文草稿作废失败')); }
    finally { setBusy(false); }
  };

  const voidCurrentDistribution = async () => {
    if (!detail?.id || !voidReason.trim()) return;
    setBusy(true); setError('');
    try {
      await voidDocumentDistribution(detail.id, voidReason.trim());
      setDetail(null); setVoidReason(''); await refresh();
    } catch (voidError) { setError(message(voidError, '发放批次作废失败')); }
    finally { setBusy(false); }
  };

  const exportLedger = async () => {
    setBusy(true); setError('');
    try {
      const blob = await exportDocumentCirculationLedger(projectId); const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a'); anchor.href = url; anchor.download = `图纸收发综合台账-${projectId}.xlsx`; anchor.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (exportError) { setError(message(exportError, '台账导出失败')); }
    finally { setBusy(false); }
  };

  const visibleTabs = useMemo(() => [
    ...(canView ? [
      { id: 'incoming', label: '收文批次' },
      { id: 'distribution', label: '发放批次' },
      { id: 'ledger', label: '综合台账' },
    ] : []),
    ...(directRecipient ? [{ id: 'receipt', label: '我的签收' }] : []),
  ], [canView, directRecipient]);

  return <div className="dc-page">
    <header className="dc-head">
      <div><span className="dc-project-name">{projectName}</span><h1>图纸收发</h1><p>正式图纸和技术文件的收文、版本替代、发放签收与精确追溯</p></div>
      {canReceive && tab === 'incoming' && !draft && <button className="primary" onClick={() => { setSource(emptySource()); setDraft({ items: [] }); setRecipientOptions([]); setSelectedRecipients({}); }}>登记收文</button>}
    </header>
    <nav className="dc-tabs" role="tablist" aria-label="图纸收发功能">{visibleTabs.map((item) => <button key={item.id} type="button" role="tab" aria-selected={tab === item.id} className={tab === item.id ? 'active' : ''} onClick={() => setTab(item.id)}>{item.label}</button>)}</nav>
    {error && <div className="dc-error" role="alert"><span>{error}</span><button onClick={() => setError('')} title="关闭提示" aria-label="关闭提示">×</button></div>}

    {tab === 'incoming' && !draft && <section className="dc-card dc-list-card"><div className="dc-table-wrap"><table><thead><tr><th>收文批次</th><th>来源单位</th><th>收文时间</th><th>接收技术员</th><th>状态</th><th /></tr></thead><tbody>{incoming.map((item) => <tr key={item.id}><td><strong>{item.incomingNo}</strong></td><td>{item.sourceOrganization}</td><td>{formatTime(item.receivedAt)}</td><td>{item.receiverName}</td><td><span className={`status ${String(item.status).toLowerCase()}`}>{item.status}</span></td><td><button onClick={() => openIncoming(item.id)}>{item.status === 'DRAFT' && canReceive ? '继续登记' : '查看'}</button></td></tr>)}{!incoming.length && <tr><td colSpan="6" className="empty">暂无收文批次</td></tr>}</tbody></table></div></section>}

    {tab === 'incoming' && draft && <section className="dc-editor">
      <div className="dc-card dc-form"><h2>{draft.id ? draft.incomingNo : '新建收文草稿'}</h2><div className="grid"><label>来源单位*<input value={source.sourceOrganization} onChange={(e) => setSource({ ...source, sourceOrganization: e.target.value })} /></label><label>收文时间*<input type="datetime-local" value={source.receivedAt} onChange={(e) => setSource({ ...source, receivedAt: e.target.value })} /></label><label>发件人<input value={source.senderName} onChange={(e) => setSource({ ...source, senderName: e.target.value })} /></label><label>来文编号<input value={source.sourceReferenceNo} onChange={(e) => setSource({ ...source, sourceReferenceNo: e.target.value })} /></label><label>接收方式<input value={source.receiveMethod} onChange={(e) => setSource({ ...source, receiveMethod: e.target.value })} /></label><label>备注<input value={source.remark} onChange={(e) => setSource({ ...source, remark: e.target.value })} /></label></div>{!draft.id ? <button className="primary" disabled={busy} onClick={createDraft}>创建草稿后上传文件</button> : <label className="dc-upload">选择文件（单文件最大200MB，支持续传）<input type="file" multiple onChange={(e) => uploadFiles(e.target.files)} /></label>}{Object.entries(uploading).map(([name, progress]) => <div className="progress" key={name}><span>{name}</span><i><b style={{ width: `${progress}%` }} /></i><em>{progress}%</em></div>)}</div>
      {draft.id && <div className="dc-card"><h2>文件核对与版本匹配</h2><div className="dc-item-list">{draft.items.map((item, index) => <div className="dc-item" key={`${item.fileResourceId}-${index}`}><div className="dc-item-file"><strong>{item.fileName}</strong><small>{item.title}</small></div><label>正式类型<select value={item.documentType} onChange={(e) => patchItem(index, { documentType: e.target.value })}><option value="DRAWING">图纸</option><option value="TECHNICAL_DOCUMENT">技术文件</option></select></label><label>标题<input value={item.title} onChange={(e) => patchItem(index, { title: e.target.value })} /></label><label>图号/编号<input value={item.documentNo || ''} placeholder={item.documentType === 'DRAWING' ? '图纸必填' : '空白时系统生成'} onChange={(e) => patchItem(index, { documentNo: e.target.value })} /></label><label>外部版次<input value={item.externalRevision || ''} placeholder="Rev.A / A版" onChange={(e) => patchItem(index, { externalRevision: e.target.value })} /></label><label>版本关系<select value={item.matchMode} onChange={(e) => patchItem(index, { matchMode: e.target.value, targetDocumentId: e.target.value === 'NEW_DOCUMENT' ? null : item.targetDocumentId })}><option value="NEW_DOCUMENT">新资料</option><option value="NEW_VERSION">已有资料新版本</option></select></label><button onClick={() => matchItem(index)}>按编号推荐</button>{item.matchMode === 'NEW_VERSION' && <label>目标资料<select value={item.targetDocumentId || ''} onChange={(e) => patchItem(index, { targetDocumentId: Number(e.target.value) || null })}><option value="">请选择</option>{(item.candidates || []).map((candidate) => <option key={candidate.documentId} value={candidate.documentId}>{candidate.documentNo} · {candidate.title} · V{candidate.currentVersionNo}</option>)}</select></label>}<label>版本说明<input value={item.changeNote || ''} onChange={(e) => patchItem(index, { changeNote: e.target.value })} /></label><label>重复版次原因<input value={item.duplicateRevisionReason || ''} onChange={(e) => patchItem(index, { duplicateRevisionReason: e.target.value })} /></label></div>)}</div><div className="actions"><button onClick={() => setDraft(null)}>返回列表</button><button disabled={!draft.items.length || busy} onClick={loadRecipients}>保存并选择接收人</button></div><div className="dc-void"><textarea value={incomingVoidReason} onChange={(e) => setIncomingVoidReason(e.target.value)} placeholder="不再继续的收文草稿可填写原因后作废" /><button className="danger" disabled={busy || !incomingVoidReason.trim()} onClick={voidCurrentIncoming}>作废收文草稿</button></div></div>}
      {!!recipientOptions.length && <div className="dc-card"><h2>发放与接收人</h2><div className="grid"><label>签收期限*<input type="datetime-local" value={distribution.deadline} onChange={(e) => setDistribution({ ...distribution, deadline: e.target.value })} /></label><label>补充说明<input value={distribution.messageNote} onChange={(e) => setDistribution({ ...distribution, messageNote: e.target.value })} /></label><label className="check"><input type="checkbox" checked={distribution.electronicSignatureRequired} onChange={(e) => setDistribution({ ...distribution, electronicSignatureRequired: e.target.checked })} />电子签收要求手写签名</label><label className="check"><input type="checkbox" checked={distribution.paperSignatureRequired} onChange={(e) => setDistribution({ ...distribution, paperSignatureRequired: e.target.checked })} />纸质领取要求手写签名</label></div><RecipientSelector options={recipientOptions} selected={selectedRecipients} items={draft.items} itemKey={(item) => item.fileResourceId} onChange={(userId, value) => setSelectedRecipients((current) => ({ ...current, [userId]: value }))} /><div className="actions"><button className="primary" disabled={busy || !distribution.deadline} onClick={publishDraft}>原子发布并通知</button></div></div>}
    </section>}

    {tab === 'distribution' && <section className="dc-card dc-list-card"><div className="dc-table-wrap"><table><thead><tr><th>发放批次</th><th>发布时间</th><th>签收期限</th><th>发布人</th><th>状态</th><th /></tr></thead><tbody>{distributions.map((item) => <tr key={item.id}><td><strong>{item.distributionNo}</strong></td><td>{formatTime(item.publishedTime)}</td><td className={item.overdue ? 'overdue' : ''}>{formatTime(item.deadline)}</td><td>{item.publishedByName}</td><td><span className={`status ${String(item.status).toLowerCase()}`}>{item.status}</span></td><td><button onClick={() => openDistribution(item.id)}>查看</button></td></tr>)}{!distributions.length && <tr><td colSpan="6" className="empty">暂无发放批次</td></tr>}</tbody></table></div></section>}
    {tab === 'ledger' && <section className="dc-card dc-ledger"><h2>图纸收发综合台账</h2><p>导出固定包含收文批次、发放批次、接收人明细、文件与版本追溯、预览下载明细五个工作表。</p><button className="primary" disabled={!canExport || busy} onClick={exportLedger}>{canExport ? '导出 Excel' : '当前角色无导出权限'}</button></section>}

    {tab === 'receipt' && <section className="dc-card dc-receipt">{busy && !myTask ? <p>签收任务加载中…</p> : myTask && <><div className="dc-receipt-head"><div><span>{myTask.distributionNo}</span><h2>核对并签收资料</h2><p>签收期限：{formatTime(myTask.deadline)} · 渠道：{myTask.currentRecipient?.channel}</p>{myTask.notificationTemplate && <p>{myTask.notificationTemplate}</p>}</div><span className={`status ${String(myTask.currentRecipient?.status).toLowerCase()}`}>{myTask.currentRecipient?.status}</span></div>{myTask.items.map((item) => <div className="dc-receipt-item" key={item.id}><div><strong>{item.documentNo || '无编号'} · {item.title}</strong><small>V{item.systemVersionNo}{item.externalRevision ? ` / ${item.externalRevision}` : ''} · {item.fileName}</small></div><span>{item.paperCopyCount ? `纸质 ${item.paperCopyCount} 份` : '电子文件'}</span><button onClick={() => downloadVersion(item, myTask.id)}>下载核对</button></div>)}{myTask.currentRecipient?.status === 'PENDING' && <>{myTask.scanRequiredForCurrentRecipient ? <div className="dc-notice">纸质或两者兼有渠道只能在微信小程序扫描技术员展示的批次二维码后确认领取。</div> : <>{myTask.signatureRequiredForCurrentRecipient && <><h3>手写签名</h3><SignaturePad onChange={setSignatureDrawn} /></>}<button className="primary" disabled={busy} onClick={confirmMyTask}>确认电子签收</button></>}<div className="dc-dispute"><textarea value={disputeNote} onChange={(e) => setDisputeNote(e.target.value)} placeholder="文件、版次或份数不符时填写异议；提交异议不会形成签收" /><button disabled={busy || !disputeNote.trim()} onClick={submitDispute}>提交异议</button></div></>}{myTask.currentRecipient?.status === 'CONFIRMED' && <div className="dc-success">已于 {formatTime(myTask.currentRecipient.confirmedTime)} 完成签收</div>}{myTask.currentRecipient?.status === 'DISPUTED' && <div className="dc-notice">已提交异议：{myTask.currentRecipient.disputeNote}</div>}</>}</section>}

    {detail && <div className="dc-modal" onMouseDown={(e) => e.target === e.currentTarget && setDetail(null)}><div><header><h2>{detail.distributionNo || detail.incomingNo}</h2><button onClick={() => { setDetail(null); setReissue(null); }}>×</button></header>{detail.notificationTemplate && <p className="dc-template">{detail.notificationTemplate}</p>}{detail.items?.map((item) => <div className="dc-detail-item" key={item.id}><strong>{item.documentNo || item.fileName} · {item.title}</strong><span>{item.versionId ? `V${item.systemVersionNo}${item.externalRevision ? ` / ${item.externalRevision}` : ''}` : item.documentType}</span>{item.versionId && detail.distributionNo && <button onClick={() => downloadVersion(item, detail.id)}>下载精确版本</button>}</div>)}{detail.recipients?.length > 0 && <><h3>接收人签收状态</h3>{detail.recipients.map((recipient) => <div className="dc-detail-recipient" key={recipient.id}><span>{recipient.realName} · {recipient.channel}{recipient.mandatory ? ' · 强制接收' : ''}<small>{(recipient.items || []).filter((item) => item.paperCopyCount > 0).map((item) => `${item.documentNo || item.title} ${item.paperCopyCount}份`).join('；')}</small></span><strong>{recipient.status}</strong></div>)}</>}{detail.distributionNo && canIssue && <><div className="actions"><button onClick={() => showQr(detail.id)}>显示纸质领取二维码</button><button onClick={startReissue}>按当前版本再次发放</button></div>{detail.status !== 'VOIDED' && <div className="dc-void"><textarea value={voidReason} onChange={(e) => setVoidReason(e.target.value)} placeholder="作废批次必须填写原因" /><button className="danger" disabled={busy || !voidReason.trim()} onClick={voidCurrentDistribution}>确认作废批次</button></div>}</>}{reissue && <div className="dc-reissue"><h3>基于同一现行版本重建发放批次</h3><label>新签收期限<input type="datetime-local" value={reissue.deadline} onChange={(e) => setReissue({ ...reissue, deadline: e.target.value })} /></label><label>补充说明<input value={reissue.messageNote} onChange={(e) => setReissue({ ...reissue, messageNote: e.target.value })} /></label><RecipientSelector options={reissue.options} selected={reissue.selected} items={detail.items} itemKey={(item) => item.versionId} onChange={(userId, value) => setReissue((current) => ({ ...current, selected: { ...current.selected, [userId]: value } }))} /><div className="actions"><button onClick={() => setReissue(null)}>取消</button><button className="primary" disabled={!reissue.deadline || busy} onClick={submitReissue}>发布新批次</button></div></div>}{qrSvg && <div className="dc-qr" dangerouslySetInnerHTML={{ __html: qrSvg }} />}</div></div>}
  </div>;
}
