import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  createGuardVisitQr,
  exportGuardVisitRegistrations,
  getGuardVisitMiniCode,
  getGuardVisitQr,
  getGuardVisitRegistration,
  getGuardVisitRegistrations,
  rotateGuardVisitQr,
  updateGuardVisitQrStatus,
  updateGuardVisitRegistration,
  voidGuardVisitRegistration,
} from '../../services/siteAccess';
import { formatLocalDate, validateSiteVisitDateRange } from '../../utils/siteAccessDates';
import './guardVisits.css';

const PAGE_SIZE = 20;
const STATUS_LABELS = { REGISTERED: '有效', EXPIRED: '已过期', VOIDED: '已作废' };
const AUDIT_LABELS = { REGISTER: '访客登记', UPDATE: '后台纠错', VOID: '后台作废', EXPORT: '导出数据' };
const responseData = (response, fallback) => {
  if (response?.code !== 200) throw new Error(response?.message || fallback);
  return response.data;
};
const formatDateTime = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : '-';
const hasCompanionContent = (person) => Boolean(
  person.personCompany?.trim() || person.personName?.trim() || person.personPhone?.trim()
);
const emptyForm = () => ({
  visitorCompany: '', contactName: '', contactPhone: '', companions: [],
  travelMode: 'OTHER', vehiclePlate: '', visitorRemark: '', version: 0,
});

function GuardModal({ title, onClose, children, width = 720 }) {
  return <div className="site-access-modal-mask" onMouseDown={onClose}>
    <div className="site-access-modal" style={{ width }} onMouseDown={(event) => event.stopPropagation()}>
      <div className="site-access-modal-head"><strong>{title}</strong><button type="button" onClick={onClose}>×</button></div>
      {children}
    </div>
  </div>;
}

export default function GuardVisitPanel({ projectId, canManage, canExport, onOpenProfiles }) {
  const today = useMemo(() => formatLocalDate(new Date()), []);
  const [qr, setQr] = useState(null);
  const [qrCode, setQrCode] = useState(null);
  const [startDate, setStartDate] = useState(today);
  const [endDate, setEndDate] = useState(today);
  const [status, setStatus] = useState('');
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [pageNo, setPageNo] = useState(1);
  const [pageData, setPageData] = useState({ records: [], total: 0 });
  const [loading, setLoading] = useState(false);
  const [qrLoading, setQrLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [detail, setDetail] = useState(null);
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [saving, setSaving] = useState(false);
  const activeProjectRef = useRef(projectId);
  const qrRequestRef = useRef(0);
  const listRequestRef = useRef(0);
  activeProjectRef.current = projectId;

  const rangeError = validateSiteVisitDateRange(startDate, endDate);

  const loadQr = useCallback(async () => {
    if (!projectId) return;
    const requestProjectId = projectId;
    const requestId = ++qrRequestRef.current;
    setQrLoading(true);
    try {
      const value = responseData(await getGuardVisitQr(requestProjectId), '门卫登记码加载失败') || null;
      if (requestId !== qrRequestRef.current || activeProjectRef.current !== requestProjectId) return;
      setQr(value);
    } catch (loadError) {
      if (requestId === qrRequestRef.current && activeProjectRef.current === requestProjectId) {
        setError(loadError.message || '门卫登记码加载失败');
      }
    } finally {
      if (requestId === qrRequestRef.current && activeProjectRef.current === requestProjectId) setQrLoading(false);
    }
  }, [projectId]);

  const load = useCallback(async (targetPage = 1) => {
    if (!projectId) return;
    const invalid = validateSiteVisitDateRange(startDate, endDate);
    if (invalid) return setError(invalid);
    const requestProjectId = projectId;
    const requestId = ++listRequestRef.current;
    setLoading(true);
    setError('');
    try {
      const result = responseData(await getGuardVisitRegistrations({
        projectId: requestProjectId, status: status || undefined, keyword: keyword || undefined,
        startDate, endDate, pageNo: targetPage, pageSize: PAGE_SIZE,
      }), '门卫访客登记列表加载失败');
      if (requestId !== listRequestRef.current || activeProjectRef.current !== requestProjectId) return;
      setPageData(result || { records: [], total: 0 });
      setPageNo(targetPage);
    } catch (loadError) {
      if (requestId === listRequestRef.current && activeProjectRef.current === requestProjectId) {
        setError(loadError.message || '门卫访客登记列表加载失败');
      }
    } finally {
      if (requestId === listRequestRef.current && activeProjectRef.current === requestProjectId) setLoading(false);
    }
  }, [endDate, keyword, projectId, startDate, status]);

  useEffect(() => {
    qrRequestRef.current += 1;
    listRequestRef.current += 1;
    setQr(null); setQrCode(null); setDetail(null); setEditing(null); setKeyword(''); setKeywordInput(''); setPageNo(1);
    void loadQr();
    void load(1);
  }, [projectId]);

  useEffect(() => { void load(1); }, [startDate, endDate, status, keyword]);

  const ensureQr = async () => {
    setQrLoading(true); setError('');
    try {
      const value = responseData(await createGuardVisitQr(projectId), '门卫登记码创建失败');
      setQr(value); setNotice('门卫室固定登记码已创建');
    } catch (createError) { setError(createError.message || '门卫登记码创建失败'); }
    finally { setQrLoading(false); }
  };

  const changeQrStatus = async () => {
    if (!qr) return;
    const enabling = qr.qrStatus !== 'ENABLED';
    if (!window.confirm(enabling ? '确认重新启用当前门卫登记码？' : '停用后扫码将立即无法登记，确认停用？')) return;
    setQrLoading(true); setError('');
    try {
      const value = responseData(await updateGuardVisitQrStatus(qr.id, { enabled: enabling, version: qr.version }), '门卫登记码状态更新失败');
      setQr(value); setQrCode(null); setNotice(enabling ? '门卫登记码已启用' : '门卫登记码已停用');
    } catch (updateError) { setError(updateError.message || '门卫登记码状态更新失败'); }
    finally { setQrLoading(false); }
  };

  const rotateQr = async () => {
    if (!qr || !window.confirm('轮换后旧二维码立即失效，门卫处必须更换为新二维码。确认轮换？')) return;
    setQrLoading(true); setError('');
    try {
      const value = responseData(await rotateGuardVisitQr(qr.id, qr.version), '门卫登记码轮换失败');
      setQr(value); setQrCode(null); setNotice(`门卫登记码已轮换为第 ${value.qrVersion} 版，请下载并替换旧码`);
    } catch (rotateError) { setError(rotateError.message || '门卫登记码轮换失败'); }
    finally { setQrLoading(false); }
  };

  const showQr = async () => {
    if (!qr) return;
    setQrLoading(true); setError('');
    try { setQrCode(responseData(await getGuardVisitMiniCode(qr.id), '门卫小程序码生成失败')); }
    catch (showError) { setError(showError.message || '门卫小程序码生成失败'); }
    finally { setQrLoading(false); }
  };

  const downloadQr = () => {
    if (!qrCode?.imageContent) return;
    const link = document.createElement('a');
    link.href = qrCode.imageContent;
    link.download = `门卫访客登记码_第${qrCode.qrVersion}版.png`;
    link.click();
  };

  const openDetail = async (id) => {
    setError('');
    try { setDetail(responseData(await getGuardVisitRegistration(id), '门卫访客登记详情加载失败')); }
    catch (detailError) { setError(detailError.message || '门卫访客登记详情加载失败'); }
  };

  const openEdit = async (item) => {
    setError('');
    try {
      const value = responseData(await getGuardVisitRegistration(item.id), '门卫访客登记详情加载失败');
      setForm({
        visitorCompany: value.visitorCompany || '', contactName: value.contactName || '',
        contactPhone: value.contactPhone || '', travelMode: value.travelMode || 'OTHER',
        vehiclePlate: value.vehiclePlate || '', visitorRemark: value.visitorRemark || '',
        version: value.version,
        companions: (value.visitors || []).filter((person) => person.personType === 'COMPANION').map((person) => ({
          personCompany: person.personCompany || '', personName: person.personName || '', personPhone: person.personPhone || '',
        })),
      });
      setEditing(value);
    } catch (editError) { setError(editError.message || '无法打开纠错表单'); }
  };

  const updateCompanion = (index, field, value) => setForm((current) => ({
    ...current,
    companions: current.companions.map((person, itemIndex) => itemIndex === index ? { ...person, [field]: value } : person),
  }));

  const saveCorrection = async () => {
    if (!editing) return;
    const companions = form.companions.filter(hasCompanionContent).map((person) => ({
      personCompany: person.personCompany.trim(), personName: person.personName.trim(), personPhone: person.personPhone.trim(),
    }));
    const invalidPhone = companions.findIndex((person) => person.personPhone && !/^1[3-9]\d{9}$/.test(person.personPhone));
    if (!form.visitorCompany.trim() || !form.contactName.trim() || !/^1[3-9]\d{9}$/.test(form.contactPhone.trim())) {
      return setError('请完整填写单位、姓名和正确手机号码');
    }
    if (invalidPhone >= 0) return setError(`第 ${invalidPhone + 1} 位同行人员手机号码格式不正确`);
    if (form.travelMode === 'DRIVING' && !form.vehiclePlate.trim()) return setError('驾车来访必须填写车牌号');
    setSaving(true); setError('');
    try {
      await updateGuardVisitRegistration(editing.id, {
        visitorCompany: form.visitorCompany.trim(), contactName: form.contactName.trim(),
        contactPhone: form.contactPhone.trim(), companions, travelMode: form.travelMode,
        vehiclePlate: form.travelMode === 'DRIVING' ? form.vehiclePlate.trim().toUpperCase() : null,
        visitorRemark: form.visitorRemark.trim() || null, version: form.version,
      });
      setEditing(null); setNotice('门卫访客登记已纠错，访客重扫后将显示最新信息');
      await load(pageNo);
      if (detail?.id === editing.id) await openDetail(editing.id);
    } catch (saveError) { setError(saveError.message || '纠错保存失败'); }
    finally { setSaving(false); }
  };

  const voidRegistration = async (item) => {
    const reason = window.prompt(`请输入作废登记 ${item.registrationNo} 的原因`);
    if (!reason?.trim()) return;
    setError('');
    try {
      await voidGuardVisitRegistration(item.id, reason.trim());
      setNotice('登记已作废，该微信访客重扫后可以重新登记');
      setDetail(null); await load(pageNo);
    } catch (voidError) { setError(voidError.message || '作废失败'); }
  };

  const exportRows = async () => {
    if (rangeError) return setError(rangeError);
    setExporting(true); setError('');
    try {
      const blob = await exportGuardVisitRegistrations({
        projectId, status: status || undefined, keyword: keyword || undefined, startDate, endDate,
      });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a'); link.href = url;
      link.download = `场内管理_门卫访客登记_${startDate}-${endDate}.xlsx`; link.click(); URL.revokeObjectURL(url);
      setNotice(`已导出 ${startDate} 至 ${endDate} 的门卫访客登记`);
    } catch (exportError) { setError(exportError.message || '导出失败'); }
    finally { setExporting(false); }
  };

  const records = pageData.records || pageData.items || [];
  const totalPages = Math.max(1, Math.ceil(Number(pageData.total || 0) / PAGE_SIZE));

  return <div className="guard-visit-panel">
    <section className="guard-qr-card">
      <div><strong>门卫室固定访客登记码</strong><p>二维码长期有效；访客每次登记的放行状态自提交起有效 24 小时，过期后需要重新登记。</p></div>
      <div className="guard-qr-meta">
        {qr ? <><span className={`guard-qr-status ${qr.qrStatus.toLowerCase()}`}>{qr.qrStatus === 'ENABLED' ? '使用中' : '已停用'}</span><small>第 {qr.qrVersion} 版 · 更新 {formatDateTime(qr.updateTime)}</small></> : <span>尚未创建</span>}
      </div>
      <div className="guard-qr-actions">
        <button type="button" onClick={onOpenProfiles}>常用资料</button>
        {!qr && canManage && <button className="primary" type="button" disabled={qrLoading} onClick={ensureQr}>创建固定码</button>}
        {qr && canManage && <button type="button" disabled={qrLoading} onClick={showQr}>查看 / 下载</button>}
        {qr && canManage && <button type="button" disabled={qrLoading} onClick={changeQrStatus}>{qr.qrStatus === 'ENABLED' ? '停用' : '启用'}</button>}
        {qr && canManage && <button className="danger" type="button" disabled={qrLoading} onClick={rotateQr}>轮换</button>}
      </div>
    </section>

    <section className="site-access-filter-card guard-filter-card">
      <input type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} /><span>至</span><input type="date" value={endDate} onChange={(event) => setEndDate(event.target.value)} />
      <select value={status} onChange={(event) => setStatus(event.target.value)}><option value="">全部状态</option><option value="REGISTERED">有效</option><option value="EXPIRED">已过期</option><option value="VOIDED">已作废</option></select>
      <div className="site-access-keyword"><input value={keywordInput} onChange={(event) => setKeywordInput(event.target.value)} onKeyDown={(event) => event.key === 'Enter' && setKeyword(keywordInput.trim())} placeholder="登记编号、单位、姓名、车牌" /><button type="button" onClick={() => setKeyword(keywordInput.trim())}>查询</button></div>
      {canExport && <button type="button" disabled={exporting || Boolean(rangeError)} onClick={exportRows}>{exporting ? '导出中...' : '导出 Excel'}</button>}
    </section>
    {notice && <div className="site-access-notice" onClick={() => setNotice('')}>{notice}</div>}
    {error && <div className="site-access-error" onClick={() => setError('')}>{error}</div>}
    <section className="site-access-table-card">
      <div className="site-access-table-wrap"><table><thead><tr><th>登记编号</th><th>单位 / 姓名</th><th>人数</th><th>车辆</th><th>登记时间</th><th>有效截止</th><th>状态</th><th>操作</th></tr></thead><tbody>
        {!loading && records.map((item) => <tr key={item.id}>
          <td><button className="link" type="button" onClick={() => openDetail(item.id)}>{item.registrationNo}</button></td><td>{item.visitorCompany}<small>{item.contactName}</small></td><td>{item.visitorCount}</td><td>{item.travelMode === 'DRIVING' ? item.vehiclePlate || '驾车' : '非驾车'}</td><td>{formatDateTime(item.registeredTime)}</td><td>{formatDateTime(item.validUntil)}</td><td><span className={`site-access-status ${String(item.status).toLowerCase()}`}>{STATUS_LABELS[item.status] || item.status}</span></td>
          <td><div className="site-access-row-actions"><button type="button" onClick={() => openDetail(item.id)}>详情</button>{canManage && item.status === 'REGISTERED' && <button type="button" onClick={() => openEdit(item)}>纠错</button>}{canManage && item.status === 'REGISTERED' && <button className="danger" type="button" onClick={() => voidRegistration(item)}>作废</button>}</div></td>
        </tr>)}
        {!loading && !records.length && <tr><td colSpan="8" className="site-access-empty">当前条件下没有门卫访客登记</td></tr>}
        {loading && <tr><td colSpan="8" className="site-access-empty">正在加载...</td></tr>}
      </tbody></table></div>
      <div className="site-access-pagination"><span>共 {pageData.total || 0} 条 · 第 {pageNo}/{totalPages} 页</span><button type="button" disabled={pageNo <= 1 || loading} onClick={() => load(pageNo - 1)}>上一页</button><button type="button" disabled={pageNo >= totalPages || loading} onClick={() => load(pageNo + 1)}>下一页</button></div>
    </section>

    {qrCode && <GuardModal title={`门卫室固定访客登记码 · 第 ${qrCode.qrVersion} 版`} onClose={() => setQrCode(null)} width={430}>
      <div className="site-access-qr">{qrCode.imageContent ? <img src={qrCode.imageContent} alt="门卫访客登记小程序码" /> : <div className="site-access-scene"><span>当前环境未生成小程序码</span></div>}<p>{qrCode.hint}</p><small>页面：{qrCode.pagePath}</small></div>
      <div className="site-access-modal-actions"><button type="button" onClick={() => setQrCode(null)}>关闭</button>{qrCode.imageContent && <button className="primary" type="button" onClick={downloadQr}>下载小程序码</button>}</div>
    </GuardModal>}

    {editing && <GuardModal title={`纠错 · ${editing.registrationNo}`} onClose={() => setEditing(null)}>
      <div className="site-access-form-grid">
        <label className="site-access-field full"><span>单位 *</span><input value={form.visitorCompany} onChange={(event) => setForm({ ...form, visitorCompany: event.target.value })} /></label>
        <label className="site-access-field"><span>姓名 *</span><input value={form.contactName} onChange={(event) => setForm({ ...form, contactName: event.target.value })} /></label><label className="site-access-field"><span>手机号码 *</span><input maxLength="11" value={form.contactPhone} onChange={(event) => setForm({ ...form, contactPhone: event.target.value })} /></label>
        <div className="site-access-companions full"><div className="site-access-companion-head"><strong>同行人员（单位、姓名、手机号码均选填）</strong><button type="button" disabled={form.companions.length >= 49} onClick={() => setForm({ ...form, companions: [...form.companions, { personCompany: '', personName: '', personPhone: '' }] })}>添加同行人</button></div>{form.companions.map((person, index) => <div className="site-access-companion-row" key={index}><input placeholder="单位（选填）" value={person.personCompany} onChange={(event) => updateCompanion(index, 'personCompany', event.target.value)} /><input placeholder="姓名（选填）" value={person.personName} onChange={(event) => updateCompanion(index, 'personName', event.target.value)} /><input placeholder="手机号码（选填）" maxLength="11" value={person.personPhone} onChange={(event) => updateCompanion(index, 'personPhone', event.target.value)} /><button className="danger" type="button" onClick={() => setForm({ ...form, companions: form.companions.filter((_, itemIndex) => itemIndex !== index) })}>移除</button></div>)}</div>
        <label className="site-access-field"><span>出行方式 *</span><select value={form.travelMode} onChange={(event) => setForm({ ...form, travelMode: event.target.value, vehiclePlate: event.target.value === 'OTHER' ? '' : form.vehiclePlate })}><option value="OTHER">非驾车</option><option value="DRIVING">驾车</option></select></label><label className="site-access-field"><span>车牌号{form.travelMode === 'DRIVING' ? ' *' : ''}</span><input disabled={form.travelMode !== 'DRIVING'} value={form.vehiclePlate} onChange={(event) => setForm({ ...form, vehiclePlate: event.target.value.toUpperCase() })} /></label>
        <label className="site-access-field full"><span>外访备注</span><textarea value={form.visitorRemark} onChange={(event) => setForm({ ...form, visitorRemark: event.target.value })} /></label>
      </div><div className="site-access-modal-actions"><button type="button" onClick={() => setEditing(null)}>取消</button><button className="primary" type="button" disabled={saving} onClick={saveCorrection}>{saving ? '保存中...' : '保存纠错'}</button></div>
    </GuardModal>}

    {detail && <div className="site-access-drawer-mask" onMouseDown={() => setDetail(null)}><aside className="site-access-drawer site-access-visitor-drawer" onMouseDown={(event) => event.stopPropagation()}>
      <div className="site-access-modal-head"><div><strong>{detail.registrationNo}</strong><span className={`site-access-status ${String(detail.status).toLowerCase()}`}>{STATUS_LABELS[detail.status] || detail.status}</span></div><button type="button" onClick={() => setDetail(null)}>×</button></div>
      <div className="site-access-detail-grid">{[['项目', detail.projectName], ['单位', detail.visitorCompany], ['姓名', `${detail.contactName} ${detail.contactPhone || ''}`], ['出行方式', detail.travelMode === 'DRIVING' ? `驾车 · ${detail.vehiclePlate || '-'}` : '非驾车'], ['登记时间', formatDateTime(detail.registeredTime)], ['有效截止', formatDateTime(detail.validUntil)], ['资料来源', detail.sourceProfileName || '本次手工填写'], ['外访备注', detail.visitorRemark || '-']].map(([label, value]) => <div key={label}><span>{label}</span><b>{value}</b></div>)}</div>
      <h3>登记人员（{detail.visitors?.length || 0}）</h3><div className="site-access-person-list">{(detail.visitors || []).map((person, index) => <div key={index}><span>{person.personType === 'CONTACT' ? '本人' : '同行人员'}</span><div className="site-access-person-contact-fields"><b>单位：{person.personCompany || '-'}</b><b>姓名：{person.personName || '-'}</b><b>手机号码：{person.personPhone || '-'}</b></div></div>)}</div>
      <h3>操作记录</h3><div className="site-access-audit-list">{(detail.auditLogs || []).map((log, index) => <div key={index}><b>{AUDIT_LABELS[log.actionType] || log.actionType}</b><span>{log.operatorName} · {formatDateTime(log.createTime)}</span><p>{log.comment || '-'}</p></div>)}</div>
      {detail.voidReason && <div className="site-access-void-reason">作废原因：{detail.voidReason}</div>}
    </aside></div>}
  </div>;
}
