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
const statusLabels = {
  DRAFT: '草稿',
  PUBLISHED: '等待签收',
  PENDING: '待签收',
  CONFIRMED: '已签收',
  COMPLETED: '已完成',
  DISPUTED: '有异议',
  VOIDED: '已作废',
};
const formatStatus = (value) => statusLabels[String(value || '').toUpperCase()] || value || '-';
const incomingStatusLabels = { ...statusLabels, PUBLISHED: '已发布' };
const formatIncomingStatus = (value) => incomingStatusLabels[String(value || '').toUpperCase()] || value || '-';
const channelLabels = { ELECTRONIC: '电子签收', PAPER: '纸质领取', BOTH: '电子＋纸质' };
const formatChannel = (value) => channelLabels[String(value || '').toUpperCase()] || value || '-';
const documentTypeLabels = { DRAWING: '图纸', TECHNICAL_DOCUMENT: '技术文件' };
const matchModeLabels = { NEW_DOCUMENT: '新资料', NEW_VERSION: '已有资料新版本' };
const CIRCULATION_STEPS = [
  { title: '收到资料', description: '登记来源与收文时间' },
  { title: '核对版本', description: '确认图号和版本关系' },
  { title: '发放通知', description: '选接收人并生成待办' },
  { title: '签收领取', description: '电子确认或纸质扫码' },
  { title: '完成追溯', description: '旧版替代并留存记录' },
];
const INCOMING_WIZARD_STEPS = ['收文信息', '上传文件', '核对文件与版本', '接收人及发布'];
const formatFileSize = (value) => {
  const size = Number(value || 0);
  if (!size) return '-';
  if (size >= 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`;
  if (size >= 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${size} B`;
};
const recipientProgress = (recipients = []) => {
  const total = recipients.length;
  const confirmed = recipients.filter((item) => item.status === 'CONFIRMED').length;
  const disputed = recipients.filter((item) => item.status === 'DISPUTED').length;
  const pending = Math.max(0, total - confirmed - disputed);
  return { total, confirmed, disputed, pending, percent: total ? Math.round((confirmed / total) * 100) : 0 };
};
const distributionNextAction = (batch) => {
  if (!batch) return null;
  if (batch.status === 'VOIDED') return { tone: 'muted', title: '批次已作废', text: batch.voidReason || '该批次不再允许签收，历史记录继续保留。' };
  if (batch.status === 'COMPLETED') return { tone: 'success', title: '闭环已完成', text: '全部接收人均已确认，精确文件版本、签名和领取份数已经留痕。' };
  if (batch.status === 'DISPUTED') return { tone: 'danger', title: '处理签收异议', text: '名单或份数错误可按当前版本重新发放；文件或版次错误应登记纠正版，再将本批次填写原因后作废。' };
  if (batch.overdue) return { tone: 'warning', title: '跟进逾期签收', text: '批次已超过签收期限。请核对未签收人员，必要时出示纸质领取二维码或重新发放。' };
  return { tone: 'info', title: '等待接收人签收', text: '电子接收人从个人待办确认；纸质或两者兼有的接收人需用小程序扫描本批次二维码。' };
};
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

function CirculationFlow() {
  return <ol className="dc-process-flow">{CIRCULATION_STEPS.map((step, index) => <li key={step.title}>
    <span>{index + 1}</span><div><strong>{step.title}</strong><small>{step.description}</small></div>
  </li>)}</ol>;
}

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
  const [tab, setTab] = useState('workspace');
  const [incoming, setIncoming] = useState([]);
  const [distributions, setDistributions] = useState([]);
  const [draft, setDraft] = useState(null);
  const [draftStep, setDraftStep] = useState(1);
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

  const startIncoming = () => {
    setTab('incoming');
    setSource(emptySource());
    setDraft({ items: [] });
    setDraftStep(1);
    setUploading({});
    setRecipientOptions([]);
    setSelectedRecipients({});
    setIncomingVoidReason('');
    setError('');
  };

  const closeDraft = () => {
    setDraft(null);
    setDraftStep(1);
    setUploading({});
    setRecipientOptions([]);
    setSelectedRecipients({});
    setIncomingVoidReason('');
  };

  const continueFromSource = () => {
    if (!source.sourceOrganization.trim()) return setError('请填写来源单位');
    if (!source.receivedAt) return setError('请选择收文时间');
    setError('');
    if (!draft?.id) return createDraft();
    setDraftStep(2);
  };

  const createDraft = async () => {
    setBusy(true); setError('');
    try {
      const data = unwrap(await createIncomingBatch({ projectId, ...source, items: [] }), '收文草稿创建失败');
      setDraft(data);
      setDraftStep(2);
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

  const removeDraftItem = (index) => setDraft((current) => ({ ...current,
    items: current.items.filter((item, itemIndex) => itemIndex !== index) }));

  const continueToReview = () => {
    if (!draft?.items?.length) return setError('请至少上传一份图纸或技术文件');
    setError('');
    setDraftStep(3);
  };

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
      setDraftStep(4);
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
      if (!recipients.length) throw new Error('请至少选择一名接收人');
      const result = unwrap(await publishIncomingBatch(saved.id, { expectedVersion: saved.version,
        items: saved.items.map(itemRequest), distribution: { ...distribution, recipients } }), '收文发布失败');
      setDetail(result); closeDraft(); await refresh(); setTab('distribution');
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
        setDraftStep(data.items?.length ? 3 : 2);
        setUploading({});
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
      closeDraft(); await refresh();
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
      { id: 'workspace', label: '工作台' },
      { id: 'incoming', label: '收文管理' },
      { id: 'distribution', label: '发放签收' },
      { id: 'ledger', label: '追溯台账' },
    ] : []),
    ...(directRecipient ? [{ id: 'receipt', label: '我的签收' }] : []),
  ], [canView, directRecipient]);

  const workbenchCounts = useMemo(() => ({
    drafts: incoming.filter((item) => item.status === 'DRAFT').length,
    pending: distributions.filter((item) => item.status === 'PUBLISHED').length,
    overdue: distributions.filter((item) => item.overdue && !['COMPLETED', 'VOIDED'].includes(item.status)).length,
    disputed: distributions.filter((item) => item.status === 'DISPUTED').length,
    completed: distributions.filter((item) => item.status === 'COMPLETED').length,
  }), [incoming, distributions]);

  const workbenchTasks = useMemo(() => {
    const tasks = [
      ...incoming.filter((item) => item.status === 'DRAFT').map((item) => ({
        id: `incoming-${item.id}`, kind: 'incoming', targetId: item.id, priority: 2, tone: 'draft',
        tag: '收文待完善', title: item.incomingNo,
        meta: `${item.sourceOrganization || '未填写来源单位'} · ${formatTime(item.receivedAt)}`,
        description: '补齐文件、版本关系和接收人后发布。', action: '继续登记',
      })),
      ...distributions.filter((item) => item.status === 'DISPUTED').map((item) => ({
        id: `distribution-${item.id}`, kind: 'distribution', targetId: item.id, priority: 0, tone: 'danger',
        tag: '签收有异议', title: item.distributionNo,
        meta: `${item.items?.length || 0} 份文件 · ${item.publishedByName || '未知发布人'}`,
        description: '先核对异议原因，再决定按当前版本重发或登记纠正版。', action: '处理异议',
      })),
      ...distributions.filter((item) => item.status === 'PUBLISHED' && item.overdue).map((item) => ({
        id: `distribution-${item.id}`, kind: 'distribution', targetId: item.id, priority: 1, tone: 'warning',
        tag: '签收已逾期', title: item.distributionNo,
        meta: `${item.items?.length || 0} 份文件 · 截止 ${formatTime(item.deadline)}`,
        description: '查看未签收人员并继续跟进纸质或电子领取。', action: '查看进度',
      })),
      ...distributions.filter((item) => item.status === 'PUBLISHED' && !item.overdue).map((item) => ({
        id: `distribution-${item.id}`, kind: 'distribution', targetId: item.id, priority: 3, tone: 'pending',
        tag: '等待签收', title: item.distributionNo,
        meta: `${item.items?.length || 0} 份文件 · 截止 ${formatTime(item.deadline)}`,
        description: '等待接收人完成电子确认或纸质扫码领取。', action: '查看进度',
      })),
    ];
    return tasks.sort((left, right) => left.priority - right.priority).slice(0, 8);
  }, [incoming, distributions]);

  const selectedRecipientCount = recipientOptions.filter((option) => selectedRecipients[option.userId]?.selected || option.mandatory).length;
  const detailReceiptProgress = recipientProgress(detail?.recipients);
  const detailNextAction = detail?.distributionNo ? distributionNextAction(detail) : null;
  const hasPaperRecipients = detail?.recipients?.some((recipient) => recipient.channel !== 'ELECTRONIC');

  const closeDetail = () => {
    setDetail(null);
    setReissue(null);
    setQrSvg('');
    setVoidReason('');
  };

  return <div className="dc-page">
    <header className="dc-head">
      <div><span className="dc-project-name">{projectName}</span><h1>图纸收发</h1><p>正式图纸和技术文件的收文、版本替代、发放签收与精确追溯</p></div>
      {canReceive && ['workspace', 'incoming'].includes(tab) && !draft && <button className="primary" onClick={startIncoming}>登记新收文</button>}
    </header>
    <nav className="dc-tabs" role="tablist" aria-label="图纸收发功能">{visibleTabs.map((item) => <button key={item.id} type="button" role="tab" aria-selected={tab === item.id} className={tab === item.id ? 'active' : ''} onClick={() => setTab(item.id)}>{item.label}</button>)}</nav>
    {error && <div className="dc-error" role="alert"><span>{error}</span><button onClick={() => setError('')} title="关闭提示" aria-label="关闭提示">×</button></div>}

    {tab === 'workspace' && <section className="dc-workbench">
      <div className="dc-card dc-process-card">
        <div className="dc-section-heading"><div><span>完整业务闭环</span><h2>一张图看懂图纸从收到到追溯</h2></div><button onClick={() => setTab('incoming')}>查看全部收文</button></div>
        <CirculationFlow />
      </div>

      <div className="dc-metric-grid" aria-label="图纸收发状态概览">
        <button className="dc-metric draft" onClick={() => setTab('incoming')}><span>收</span><div><strong>{workbenchCounts.drafts}</strong><small>待完善收文</small></div></button>
        <button className="dc-metric pending" onClick={() => setTab('distribution')}><span>签</span><div><strong>{workbenchCounts.pending}</strong><small>待签收批次</small></div></button>
        <button className="dc-metric warning" onClick={() => setTab('distribution')}><span>期</span><div><strong>{workbenchCounts.overdue}</strong><small>逾期批次</small></div></button>
        <button className="dc-metric danger" onClick={() => setTab('distribution')}><span>异</span><div><strong>{workbenchCounts.disputed}</strong><small>异议批次</small></div></button>
        <button className="dc-metric success" onClick={() => setTab('distribution')}><span>完</span><div><strong>{workbenchCounts.completed}</strong><small>已完成批次</small></div></button>
      </div>

      <div className="dc-workbench-grid">
        <div className="dc-card dc-workbench-tasks">
          <div className="dc-section-heading"><div><span>按优先级处理</span><h2>需要我处理</h2></div><small>异议和逾期优先显示</small></div>
          {workbenchTasks.map((task) => <article className={`dc-task-row ${task.tone}`} key={task.id}>
            <span className="dc-task-tag">{task.tag}</span><div><strong>{task.title}</strong><small>{task.meta}</small><p>{task.description}</p></div>
            <button onClick={() => task.kind === 'incoming' ? openIncoming(task.targetId) : openDistribution(task.targetId)}>{task.action}</button>
          </article>)}
          {!workbenchTasks.length && <div className="dc-workbench-empty"><span>✓</span><strong>当前没有待处理事项</strong><small>新的收文草稿、待签收、逾期或异议批次会自动显示在这里。</small></div>}
        </div>
        <aside className="dc-card dc-workbench-guide">
          <div className="dc-section-heading"><div><span>操作规则</span><h2>闭环判断</h2></div></div>
          <ul>
            <li><strong>版本替代</strong><span>新版本发布后，旧版本自动标记为已替代，但仍可警告确认后追溯。</span></li>
            <li><strong>纸质领取</strong><span>技术员出示批次二维码，名单内人员使用小程序扫码确认。</span></li>
            <li><strong>签收完成</strong><span>全部接收人确认后批次自动完成，文件版本、签名及份数完整留痕。</span></li>
            <li><strong>发生异议</strong><span>份数或名单错误按当前版本重发；文件或版次错误登记纠正版。</span></li>
          </ul>
        </aside>
      </div>
    </section>}

    {tab === 'incoming' && !draft && <section className="dc-card dc-list-card"><div className="dc-list-heading"><div><span>收文登记</span><h2>本次收到的资料</h2><p>草稿可继续补充；发布后请到“发放签收”查看领取进度。</p></div>{canReceive && <button className="primary" onClick={startIncoming}>登记新收文</button>}</div><div className="dc-table-wrap"><table className="dc-data-table"><colgroup><col className="dc-col-batch" /><col className="dc-col-context" /><col className="dc-col-time" /><col className="dc-col-owner" /><col className="dc-col-status" /><col className="dc-col-action" /></colgroup><thead><tr><th>收文批次</th><th>来源单位</th><th>收文时间</th><th>接收技术员</th><th>状态</th><th className="dc-table-action-head">操作</th></tr></thead><tbody>{incoming.map((item) => <tr key={item.id}><td className="dc-table-primary" title={item.incomingNo}><strong>{item.incomingNo}</strong></td><td className="dc-table-ellipsis" title={item.sourceOrganization}>{item.sourceOrganization}</td><td className="dc-table-time">{formatTime(item.receivedAt)}</td><td className="dc-table-ellipsis" title={item.receiverName}>{item.receiverName}</td><td className="dc-table-status"><span className={`status ${String(item.status).toLowerCase()}`}>{formatIncomingStatus(item.status)}</span></td><td className="dc-table-action"><button className="dc-row-action" onClick={() => openIncoming(item.id)}>{item.status === 'DRAFT' && canReceive ? '继续登记' : '查看详情'}</button></td></tr>)}{!incoming.length && <tr><td colSpan="6" className="empty">暂无收文批次</td></tr>}</tbody></table></div></section>}

    {tab === 'incoming' && draft && <section className="dc-editor dc-wizard">
      <div className="dc-card dc-wizard-shell">
        <div className="dc-wizard-heading"><div><span>{draft.id ? draft.incomingNo : '新收文'}</span><h2>登记并发布图纸</h2><p>按步骤完成即可；正式发布前仍为草稿，不会通知接收人。</p></div><button onClick={closeDraft}>退出向导</button></div>
        <ol className="dc-wizard-steps">{INCOMING_WIZARD_STEPS.map((label, index) => {
          const number = index + 1; const state = number === draftStep ? 'active' : number < draftStep ? 'done' : '';
          return <li className={state} key={label}><span>{number < draftStep ? '✓' : number}</span><strong>{label}</strong></li>;
        })}</ol>
      </div>

      {draftStep === 1 && <div className="dc-card dc-form dc-wizard-panel">
        <div className="dc-panel-heading"><div><span>第 1 步</span><h2>填写收文信息</h2><p>来源单位和收文时间为必填，接收技术员自动记录为当前账号。</p></div></div>
        <div className="grid"><label>来源单位*<input value={source.sourceOrganization} onChange={(e) => setSource({ ...source, sourceOrganization: e.target.value })} /></label><label>收文时间*<input type="datetime-local" value={source.receivedAt} onChange={(e) => setSource({ ...source, receivedAt: e.target.value })} /></label><label>发件人<input value={source.senderName} onChange={(e) => setSource({ ...source, senderName: e.target.value })} /></label><label>来文编号<input value={source.sourceReferenceNo} onChange={(e) => setSource({ ...source, sourceReferenceNo: e.target.value })} /></label><label>接收方式<input value={source.receiveMethod} placeholder="如：邮件、现场移交" onChange={(e) => setSource({ ...source, receiveMethod: e.target.value })} /></label><label>备注<input value={source.remark} onChange={(e) => setSource({ ...source, remark: e.target.value })} /></label></div>
        <div className="dc-wizard-footer"><button onClick={closeDraft}>取消</button><button className="primary" disabled={busy} onClick={continueFromSource}>下一步：上传文件</button></div>
      </div>}

      {draftStep === 2 && draft.id && <div className="dc-card dc-wizard-panel">
        <div className="dc-panel-heading"><div><span>第 2 步</span><h2>上传本次收到的文件</h2><p>可一次选择多个文件；单文件最大 200MB，上传失败后可继续重试。</p></div><em>{draft.items?.length || 0} 个文件</em></div>
        <label className="dc-upload">选择图纸或技术文件<input type="file" multiple onChange={(e) => uploadFiles(e.target.files)} /><small>支持现有白名单格式；IFC、RVT、DGN 上线后仅提供安全下载。</small></label>
        {Object.entries(uploading).map(([name, progress]) => <div className="progress" key={name}><span>{name}</span><i><b style={{ width: `${progress}%` }} /></i><em>{progress}%</em></div>)}
        <div className="dc-upload-list">{(draft.items || []).map((item, index) => <article key={`${item.fileResourceId}-${index}`}><span>{index + 1}</span><div><strong>{item.fileName}</strong><small>{formatFileSize(item.fileSize)}</small></div><button onClick={() => removeDraftItem(index)}>移除</button></article>)}{!draft.items?.length && <div className="dc-upload-empty">尚未选择文件。上传完成后再进入版本核对。</div>}</div>
        <div className="dc-wizard-footer"><button onClick={() => setDraftStep(1)}>上一步</button><button className="primary" disabled={!draft.items?.length || busy} onClick={continueToReview}>下一步：核对文件</button></div>
      </div>}

      {draftStep === 3 && draft.id && <div className="dc-card dc-wizard-panel">
        <div className="dc-panel-heading"><div><span>第 3 步</span><h2>核对文件与版本</h2><p>逐份确认标题、图号和版本关系。图纸必须填写图号，技术文件编号可由系统生成。</p></div><em>{draft.items?.length || 0} 份待核对</em></div>
        <div className="dc-review-list">{draft.items.map((item, index) => <article className="dc-review-item" key={`${item.fileResourceId}-${index}`}>
          <header><span>文件 {index + 1}</span><div><strong>{item.fileName}</strong><small>{formatFileSize(item.fileSize)}</small></div><em>{documentTypeLabels[item.documentType] || item.documentType}</em></header>
          <div className="dc-review-grid"><label>正式类型<select value={item.documentType} onChange={(e) => patchItem(index, { documentType: e.target.value })}><option value="DRAWING">图纸</option><option value="TECHNICAL_DOCUMENT">技术文件</option></select></label><label>标题*<input value={item.title} onChange={(e) => patchItem(index, { title: e.target.value })} /></label><label>图号/编号<input value={item.documentNo || ''} placeholder={item.documentType === 'DRAWING' ? '图纸必填' : '空白时系统生成'} onChange={(e) => patchItem(index, { documentNo: e.target.value })} /></label><label>外部版次<input value={item.externalRevision || ''} placeholder="如 Rev.A / A版" onChange={(e) => patchItem(index, { externalRevision: e.target.value })} /></label></div>
          <div className="dc-match-panel"><div><strong>版本关系</strong><small>同一项目、类型和规范化图号用于推荐匹配，最终由技术员确认。</small></div><label><select value={item.matchMode} onChange={(e) => patchItem(index, { matchMode: e.target.value, targetDocumentId: e.target.value === 'NEW_DOCUMENT' ? null : item.targetDocumentId })}><option value="NEW_DOCUMENT">作为新资料</option><option value="NEW_VERSION">作为已有资料的新版本</option></select></label><button onClick={() => matchItem(index)}>按图号推荐</button></div>
          {item.matchMode === 'NEW_VERSION' && <div className="dc-review-grid dc-review-secondary"><label>目标资料*<select value={item.targetDocumentId || ''} onChange={(e) => patchItem(index, { targetDocumentId: Number(e.target.value) || null })}><option value="">请选择目标资料</option>{item.targetDocumentId && !(item.candidates || []).some((candidate) => Number(candidate.documentId) === Number(item.targetDocumentId)) && <option value={item.targetDocumentId}>已匹配资料 #{item.targetDocumentId}</option>}{(item.candidates || []).map((candidate) => <option key={candidate.documentId} value={candidate.documentId}>{candidate.documentNo} · {candidate.title} · V{candidate.currentVersionNo}</option>)}</select></label><label>版本说明<input value={item.changeNote || ''} placeholder="说明本次变更内容" onChange={(e) => patchItem(index, { changeNote: e.target.value })} /></label><label>重复版次原因<input value={item.duplicateRevisionReason || ''} placeholder="外部版次重复时必填" onChange={(e) => patchItem(index, { duplicateRevisionReason: e.target.value })} /></label></div>}
        </article>)}</div>
        <div className="dc-wizard-footer"><button onClick={() => setDraftStep(2)}>上一步</button><button className="primary" disabled={!draft.items.length || busy} onClick={loadRecipients}>保存并选择接收人</button></div>
      </div>}

      {draftStep === 4 && draft.id && <div className="dc-card dc-wizard-panel">
        <div className="dc-panel-heading"><div><span>第 4 步</span><h2>选择接收人并发布</h2><p>发布后文件版本、接收人和渠道不可修改；系统将同时生成站内通知和个人待办。</p></div><em>已选 {selectedRecipientCount} 人</em></div>
        <div className="grid dc-distribution-settings"><label>签收期限*<input type="datetime-local" value={distribution.deadline} onChange={(e) => setDistribution({ ...distribution, deadline: e.target.value })} /></label><label>补充说明<input value={distribution.messageNote} placeholder="可补充领取地点、用途等说明" onChange={(e) => setDistribution({ ...distribution, messageNote: e.target.value })} /></label></div>
        <details className="dc-advanced-settings"><summary>签名与通知高级设置</summary><div><label className="check"><input type="checkbox" checked={distribution.electronicSignatureRequired} onChange={(e) => setDistribution({ ...distribution, electronicSignatureRequired: e.target.checked })} />电子签收要求手写签名</label><label className="check"><input type="checkbox" checked={distribution.paperSignatureRequired} onChange={(e) => setDistribution({ ...distribution, paperSignatureRequired: e.target.checked })} />纸质领取要求手写签名</label></div></details>
        <div className="dc-recipient-heading"><div><strong>接收人名单</strong><small>强制接收人来自历史下载或签收记录，仍具备项目资料权限时不可删除。</small></div></div>
        <RecipientSelector options={recipientOptions} selected={selectedRecipients} items={draft.items} itemKey={(item) => item.fileResourceId} onChange={(userId, value) => setSelectedRecipients((current) => ({ ...current, [userId]: value }))} />
        {!recipientOptions.length && <div className="dc-notice">当前没有可选择的有效项目成员，请先检查成员的资料模块和查看权限。</div>}
        <div className="dc-publish-check"><strong>发布将一次完成</strong><span>创建正式版本 → 旧版标记为已替代 → 生成发放批次 → 通知接收人 → 写入追溯记录</span></div>
        <div className="dc-wizard-footer"><button onClick={() => setDraftStep(3)}>上一步</button><button className="primary" disabled={busy || !distribution.deadline || !selectedRecipientCount} onClick={publishDraft}>确认发布并通知 {selectedRecipientCount || ''}</button></div>
      </div>}

      {draft.id && <details className="dc-card dc-wizard-danger"><summary>不再继续本次收文</summary><div className="dc-void"><textarea value={incomingVoidReason} onChange={(e) => setIncomingVoidReason(e.target.value)} placeholder="填写作废原因后，本草稿将不可继续发布" /><button className="danger" disabled={busy || !incomingVoidReason.trim()} onClick={voidCurrentIncoming}>作废收文草稿</button></div></details>}
    </section>}

    {tab === 'distribution' && <section className="dc-card dc-list-card"><div className="dc-list-heading"><div><span>发放与签收</span><h2>资料发给谁、是否完成领取</h2><p>优先处理异议与逾期批次；点击详情查看逐人签收进度和精确文件版本。</p></div></div><div className="dc-table-wrap"><table className="dc-data-table"><colgroup><col className="dc-col-batch" /><col className="dc-col-context" /><col className="dc-col-time" /><col className="dc-col-owner" /><col className="dc-col-status" /><col className="dc-col-action" /></colgroup><thead><tr><th>发放批次</th><th>发布时间</th><th>签收期限</th><th>发布人</th><th>状态</th><th className="dc-table-action-head">操作</th></tr></thead><tbody>{distributions.map((item) => <tr key={item.id}><td className="dc-table-primary" title={item.distributionNo}><strong>{item.distributionNo}</strong></td><td className="dc-table-time">{formatTime(item.publishedTime)}</td><td className={`dc-table-time${item.overdue ? ' overdue' : ''}`}>{formatTime(item.deadline)}</td><td className="dc-table-ellipsis" title={item.publishedByName}>{item.publishedByName}</td><td className="dc-table-status"><span className={`status ${String(item.status).toLowerCase()}`}>{formatStatus(item.status)}</span></td><td className="dc-table-action"><button className="dc-row-action" onClick={() => openDistribution(item.id)}>查看进度</button></td></tr>)}{!distributions.length && <tr><td colSpan="6" className="empty">暂无发放批次</td></tr>}</tbody></table></div></section>}
    {tab === 'ledger' && <section className="dc-card dc-ledger"><div><span>审计与追溯</span><h2>图纸收发综合台账</h2><p>一个 Excel 固定包含收文批次、发放批次、接收人明细、文件与版本追溯、预览下载明细五个工作表。</p><ul><li>每次预览和下载对应到实际资料版本</li><li>纸质份数、签名、异议和作废原因可复核</li><li>新旧版本替代关系可持续追溯</li></ul></div><button className="primary" disabled={!canExport || busy} onClick={exportLedger}>{canExport ? '导出完整台账' : '当前角色无导出权限'}</button></section>}

    {tab === 'receipt' && <section className="dc-card dc-receipt">{busy && !myTask ? <p>签收任务加载中…</p> : myTask && <><div className="dc-receipt-head"><div><span>{myTask.distributionNo}</span><h2>核对并签收资料</h2><p>签收期限：{formatTime(myTask.deadline)} · 渠道：{formatChannel(myTask.currentRecipient?.channel)}</p>{myTask.notificationTemplate && <p>{myTask.notificationTemplate}</p>}</div><span className={`status ${String(myTask.currentRecipient?.status).toLowerCase()}`}>{formatStatus(myTask.currentRecipient?.status)}</span></div>{myTask.items.map((item) => <div className="dc-receipt-item" key={item.id}><div><strong>{item.documentNo || '无编号'} · {item.title}</strong><small>V{item.systemVersionNo}{item.externalRevision ? ` / ${item.externalRevision}` : ''} · {item.fileName}</small></div><span>{item.paperCopyCount ? `纸质 ${item.paperCopyCount} 份` : '电子文件'}</span><button onClick={() => downloadVersion(item, myTask.id)}>下载核对</button></div>)}{myTask.currentRecipient?.status === 'PENDING' && <>{myTask.scanRequiredForCurrentRecipient ? <div className="dc-notice">纸质或两者兼有渠道只能在微信小程序扫描技术员展示的批次二维码后确认领取。</div> : <>{myTask.signatureRequiredForCurrentRecipient && <><h3>手写签名</h3><SignaturePad onChange={setSignatureDrawn} /></>}<button className="primary" disabled={busy} onClick={confirmMyTask}>确认电子签收</button></>}<div className="dc-dispute"><textarea value={disputeNote} onChange={(e) => setDisputeNote(e.target.value)} placeholder="文件、版次或份数不符时填写异议；提交异议不会形成签收" /><button disabled={busy || !disputeNote.trim()} onClick={submitDispute}>提交异议</button></div></>}{myTask.currentRecipient?.status === 'CONFIRMED' && <div className="dc-success">已于 {formatTime(myTask.currentRecipient.confirmedTime)} 完成签收</div>}{myTask.currentRecipient?.status === 'DISPUTED' && <div className="dc-notice">已提交异议：{myTask.currentRecipient.disputeNote}</div>}</>}</section>}

    {detail && <div className="dc-modal" onMouseDown={(e) => e.target === e.currentTarget && closeDetail()}><div className="dc-circulation-detail">
      <header className="dc-detail-header"><div><span>{detail.distributionNo ? '发放签收详情' : '收文详情'}</span><h2>{detail.distributionNo || detail.incomingNo}</h2><p>{detail.distributionNo ? `发布于 ${formatTime(detail.publishedTime)} · ${detail.publishedByName || '-'}` : `${detail.sourceOrganization || '-'} · 收文于 ${formatTime(detail.receivedAt)}`}</p></div><div><span className={`status ${String(detail.status).toLowerCase()}`}>{detail.distributionNo ? formatStatus(detail.status) : formatIncomingStatus(detail.status)}</span><button onClick={closeDetail} title="关闭" aria-label="关闭详情">×</button></div></header>

      {detail.distributionNo ? <>
        <section className={`dc-next-action ${detailNextAction?.tone || 'info'}`}><span>{['success', 'muted'].includes(detailNextAction?.tone) ? '当前状态' : '下一步'}</span><div><strong>{detailNextAction?.title}</strong><p>{detailNextAction?.text}</p></div></section>
        <section className="dc-detail-progress">
          <div className="dc-progress-heading"><div><span>签收进度</span><strong>{detailReceiptProgress.confirmed} / {detailReceiptProgress.total} 人已完成</strong></div><em>{detailReceiptProgress.percent}%</em></div>
          <div className="dc-progress-bar"><i style={{ width: `${detailReceiptProgress.percent}%` }} /></div>
          <div className="dc-progress-counts"><span><strong>{detailReceiptProgress.confirmed}</strong>已签收</span><span><strong>{detailReceiptProgress.pending}</strong>待签收</span><span className={detailReceiptProgress.disputed ? 'danger' : ''}><strong>{detailReceiptProgress.disputed}</strong>有异议</span><span><strong>{detail.items?.length || 0}</strong>份文件</span></div>
        </section>

        <section className="dc-detail-section"><div className="dc-section-heading"><div><span>本次发放内容</span><h3>文件与精确版本</h3></div><small>下载始终记录实际版本</small></div><div className="dc-detail-files">{detail.items?.map((item) => <article key={item.id}><div><span>{documentTypeLabels[item.documentType] || item.documentType}</span><strong>{item.documentNo || '无编号'} · {item.title}</strong><small>{item.fileName}</small></div><em>V{item.systemVersionNo}{item.externalRevision ? ` / ${item.externalRevision}` : ''}</em><button onClick={() => downloadVersion(item, detail.id)}>下载核对</button></article>)}</div></section>

        <section className="dc-detail-section"><div className="dc-section-heading"><div><span>逐人留痕</span><h3>接收人状态</h3></div><small>截止 {formatTime(detail.deadline)}</small></div><div className="dc-recipient-status-list">{[...(detail.recipients || [])].sort((left, right) => ({ DISPUTED: 0, PENDING: 1, CONFIRMED: 2 }[left.status] ?? 3) - ({ DISPUTED: 0, PENDING: 1, CONFIRMED: 2 }[right.status] ?? 3)).map((recipient) => <article className={String(recipient.status).toLowerCase()} key={recipient.id}><div><strong>{recipient.realName}</strong><span>{formatChannel(recipient.channel)}{recipient.mandatory ? ' · 强制接收' : ''}</span><small>{recipient.roleNames || '项目成员'}{recipient.phone ? ` · ${recipient.phone}` : ''}</small></div><div>{(recipient.items || []).filter((item) => item.paperCopyCount > 0).map((item) => <small key={item.id}>{item.documentNo || item.title}：{item.paperCopyCount} 份</small>)}{recipient.status === 'CONFIRMED' && <small>确认时间：{formatTime(recipient.confirmedTime)}</small>}{recipient.status === 'DISPUTED' && <small className="danger">异议：{recipient.disputeNote}</small>}</div><span className={`status ${String(recipient.status).toLowerCase()}`}>{formatStatus(recipient.status)}</span></article>)}{!detail.recipients?.length && <div className="dc-upload-empty">暂无接收人记录</div>}</div></section>

        {detail.notificationTemplate && <section className="dc-detail-note"><strong>通知内容</strong><p>{detail.notificationTemplate}</p>{detail.messageNote && <p>补充说明：{detail.messageNote}</p>}</section>}

        {canIssue && detail.status !== 'VOIDED' && <div className="dc-detail-primary-actions">{hasPaperRecipients && !['COMPLETED'].includes(detail.status) && <button onClick={() => showQr(detail.id)}>出示纸质领取二维码</button>}{detail.status === 'DISPUTED' && <button className="primary" onClick={startReissue}>按当前版本重新发放</button>}</div>}

        {canIssue && <details className="dc-more-actions"><summary>更多操作</summary><div>{detail.status !== 'VOIDED' && detail.status !== 'DISPUTED' && <button onClick={startReissue}>按当前版本再次发放</button>}{detail.status !== 'VOIDED' && <div className="dc-void"><textarea value={voidReason} onChange={(e) => setVoidReason(e.target.value)} placeholder="作废批次必须填写原因" /><button className="danger" disabled={busy || !voidReason.trim()} onClick={voidCurrentDistribution}>确认作废批次</button></div>}{detail.status === 'VOIDED' && <p>作废原因：{detail.voidReason || '-'}</p>}</div></details>}

        {reissue && <div className="dc-reissue"><h3>基于同一现行版本重建发放批次</h3><p>适用于名单、说明或纸质份数错误；如文件或版次错误，请先登记纠正版。</p><label>新签收期限<input type="datetime-local" value={reissue.deadline} onChange={(e) => setReissue({ ...reissue, deadline: e.target.value })} /></label><label>补充说明<input value={reissue.messageNote} onChange={(e) => setReissue({ ...reissue, messageNote: e.target.value })} /></label><RecipientSelector options={reissue.options} selected={reissue.selected} items={detail.items} itemKey={(item) => item.versionId} onChange={(userId, value) => setReissue((current) => ({ ...current, selected: { ...current.selected, [userId]: value } }))} /><div className="actions"><button onClick={() => setReissue(null)}>取消</button><button className="primary" disabled={!reissue.deadline || busy} onClick={submitReissue}>发布新批次</button></div></div>}
        {qrSvg && <section className="dc-qr"><div><strong>纸质领取二维码</strong><p>仅名单内有效成员可用小程序扫描；重复扫码将返回原回执。</p></div><div dangerouslySetInnerHTML={{ __html: qrSvg }} /></section>}
      </> : <>
        <section className="dc-incoming-summary"><div><span>来源单位</span><strong>{detail.sourceOrganization || '-'}</strong></div><div><span>收文时间</span><strong>{formatTime(detail.receivedAt)}</strong></div><div><span>接收技术员</span><strong>{detail.receiverName || '-'}</strong></div><div><span>来文编号</span><strong>{detail.sourceReferenceNo || '-'}</strong></div><div><span>发件人</span><strong>{detail.senderName || '-'}</strong></div><div><span>接收方式</span><strong>{detail.receiveMethod || '-'}</strong></div></section>
        {detail.voidReason && <section className="dc-next-action muted"><span>作废原因</span><div><strong>本次收文已作废</strong><p>{detail.voidReason}</p></div></section>}
        <section className="dc-detail-section"><div className="dc-section-heading"><div><span>收文文件</span><h3>文件及版本处理结果</h3></div><small>{detail.items?.length || 0} 份</small></div><div className="dc-detail-files">{detail.items?.map((item) => <article key={item.id}><div><span>{documentTypeLabels[item.documentType] || item.documentType}</span><strong>{item.documentNo || item.fileName} · {item.title}</strong><small>{item.fileName}</small></div><em>{matchModeLabels[item.matchMode] || item.matchMode}{item.externalRevision ? ` · ${item.externalRevision}` : ''}</em></article>)}</div></section>
        {detail.distributionBatchId && <div className="dc-detail-primary-actions"><button className="primary" onClick={() => openDistribution(detail.distributionBatchId)}>查看关联发放与签收进度</button></div>}
      </>}
    </div></div>}
  </div>;
}
