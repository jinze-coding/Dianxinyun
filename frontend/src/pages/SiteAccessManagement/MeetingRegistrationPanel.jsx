import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  exportMeetingVisitRegistrations,
  getMeetingVisitRegistration,
  getMeetingVisitRegistrations,
  updateMeetingVisitRegistration,
  voidMeetingVisitRegistration,
} from '../../services/siteAccess';
import {
  createSiteAccessRequestGuard,
  isMeetingInvitationOpen,
  meetingRequestContext,
  normalizePageAfterRemoval,
} from './meetingManagement';
import MeetingCheckinPanel from './MeetingCheckinPanel';

const PAGE_SIZE = 20;
const responseData = (response, fallback) => {
  if (response?.code !== 200) throw new Error(response?.message || fallback);
  return response.data;
};
const formatDateTime = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : '-';
const hasContent = (person) => Boolean(
  person.personCompany?.trim() || person.personName?.trim() || person.personPhone?.trim()
);

function MeetingModal({ title, children, onClose, width = 760, className = '' }) {
  return <div className="site-access-modal-mask" onMouseDown={onClose}>
    <div className={`site-access-modal${className ? ` ${className}` : ''}`} style={{ width }} onMouseDown={(event) => event.stopPropagation()}>
      <div className="site-access-modal-head"><strong>{title}</strong><button type="button" onClick={onClose}>×</button></div>
      {children}
    </div>
  </div>;
}

export default function MeetingRegistrationPanel({ invitation, projectId, canManage, canExport, currentTime = Date.now(), onChanged }) {
  const [section, setSection] = useState('checkin');
  const [filters, setFilters] = useState({ status: '', keyword: '' });
  const [pageNo, setPageNo] = useState(1);
  const [page, setPage] = useState({ records: [], total: 0 });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [detail, setDetail] = useState(null);
  const [editing, setEditing] = useState(null);
  const [voiding, setVoiding] = useState(null);
  const [reason, setReason] = useState('');
  const [saving, setSaving] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [deadlineNow, setDeadlineNow] = useState(Date.now);
  const activeRequestContextRef = useRef(meetingRequestContext(projectId, invitation.id));
  const registrationListRequestGuardRef = useRef(createSiteAccessRequestGuard());
  const registrationDetailRequestGuardRef = useRef(createSiteAccessRequestGuard());
  const registrationEditRequestGuardRef = useRef(createSiteAccessRequestGuard());
  const registrationMutationRequestGuardRef = useRef(createSiteAccessRequestGuard());
  const registrationExportRequestGuardRef = useRef(createSiteAccessRequestGuard());
  const requestContext = meetingRequestContext(projectId, invitation.id);
  activeRequestContextRef.current = requestContext;
  const open = isMeetingInvitationOpen(invitation, Math.max(currentTime, deadlineNow));

  const load = useCallback(async (targetPage = pageNo, nextFilters = filters) => {
    const activeContext = meetingRequestContext(projectId, invitation.id);
    const requestTicket = registrationListRequestGuardRef.current.begin(activeContext);
    setLoading(true);
    setError('');
    try {
      const response = await getMeetingVisitRegistrations(invitation.id, {
        status: nextFilters.status || undefined,
        keyword: nextFilters.keyword.trim() || undefined,
        pageNo: targetPage,
        pageSize: PAGE_SIZE,
      });
      if (!registrationListRequestGuardRef.current.isCurrent(
        requestTicket, activeRequestContextRef.current,
      )) return;
      setPage(responseData(response, '会议登记加载失败') || { records: [], total: 0 });
      setPageNo(targetPage);
    } catch (loadError) {
      if (registrationListRequestGuardRef.current.isCurrent(
        requestTicket, activeRequestContextRef.current,
      )) {
        setError(loadError.message || '会议登记加载失败');
      }
    } finally {
      if (registrationListRequestGuardRef.current.isCurrent(
        requestTicket, activeRequestContextRef.current,
      )) {
        setLoading(false);
      }
    }
  }, [filters, invitation.id, pageNo, projectId]);

  useEffect(() => {
    const nextFilters = { status: '', keyword: '' };
    registrationListRequestGuardRef.current.invalidate();
    registrationDetailRequestGuardRef.current.invalidate();
    registrationEditRequestGuardRef.current.invalidate();
    registrationMutationRequestGuardRef.current.invalidate();
    registrationExportRequestGuardRef.current.invalidate();
    setFilters(nextFilters);
    setPageNo(1);
    setPage({ records: [], total: 0 });
    setDetail(null);
    setEditing(null);
    setVoiding(null);
    setReason('');
    setSaving(false);
    setExporting(false);
    setSection('checkin');
    load(1, nextFilters);
    return () => {
      registrationListRequestGuardRef.current.invalidate();
      registrationDetailRequestGuardRef.current.invalidate();
      registrationEditRequestGuardRef.current.invalidate();
      registrationMutationRequestGuardRef.current.invalidate();
      registrationExportRequestGuardRef.current.invalidate();
    };
  }, [invitation.id, projectId]);

  useEffect(() => {
    setDeadlineNow(Date.now());
    const endTime = new Date(invitation.visitEndTime).getTime();
    if (!Number.isFinite(endTime) || endTime <= Date.now()) return undefined;
    const delay = Math.min(endTime - Date.now() + 25, 2_147_483_647);
    const timer = window.setTimeout(() => setDeadlineNow(Date.now()), delay);
    return () => window.clearTimeout(timer);
  }, [currentTime, invitation.id, invitation.visitEndTime]);

  useEffect(() => {
    if (open) return;
    registrationEditRequestGuardRef.current.invalidate();
    registrationMutationRequestGuardRef.current.invalidate();
    setEditing(null);
    setVoiding(null);
    setReason('');
    setSaving(false);
  }, [open]);

  const openDetail = async (id) => {
    const activeContext = meetingRequestContext(projectId, invitation.id);
    const requestTicket = registrationDetailRequestGuardRef.current.begin(activeContext);
    setError('');
    try {
      const value = responseData(await getMeetingVisitRegistration(id), '会议登记详情加载失败');
      if (!registrationDetailRequestGuardRef.current.isCurrent(
        requestTicket, activeRequestContextRef.current,
      )) return;
      if (String(value?.invitationId ?? '') !== String(invitation.id ?? '')) return;
      setDetail(value);
    } catch (requestError) {
      if (registrationDetailRequestGuardRef.current.isCurrent(
        requestTicket, activeRequestContextRef.current,
      )) {
        setError(requestError.message || '会议登记详情加载失败');
      }
    }
  };

  const openEdit = async (id) => {
    if (!isMeetingInvitationOpen(invitation, Date.now())) {
      setError('会议已截止或作废，登记记录仅可查看和导出');
      return;
    }
    const activeContext = meetingRequestContext(projectId, invitation.id);
    const requestTicket = registrationEditRequestGuardRef.current.begin(activeContext);
    setError('');
    try {
      const value = responseData(await getMeetingVisitRegistration(id), '会议登记详情加载失败');
      if (!registrationEditRequestGuardRef.current.isCurrent(
        requestTicket, activeRequestContextRef.current,
      )) return;
      if (!isMeetingInvitationOpen(invitation, Date.now())) return;
      if (String(value?.invitationId ?? '') !== String(invitation.id ?? '')) return;
      setEditing({
        value,
        form: {
          visitorCompany: value.visitorCompany || '',
          contactName: value.contactName || '',
          contactPhone: value.contactPhone || '',
          companions: (value.visitors || []).filter((person) => person.personType === 'COMPANION').map((person) => ({
            personCompany: person.personCompany || '',
            personName: person.personName || '',
            personPhone: person.personPhone || '',
          })),
          travelMode: value.travelMode || 'OTHER',
          vehiclePlate: value.vehiclePlate || '',
          visitorRemark: value.visitorRemark || '',
        },
      });
    } catch (requestError) {
      if (registrationEditRequestGuardRef.current.isCurrent(
        requestTicket, activeRequestContextRef.current,
      )) {
        setError(requestError.message || '会议登记详情加载失败');
      }
    }
  };

  const updateForm = (patch) => setEditing((current) => ({
    ...current,
    form: { ...current.form, ...patch },
  }));

  const save = async () => {
    if (!editing || saving) return;
    if (!open) return;
    if (!isMeetingInvitationOpen(invitation, Date.now())) {
      setEditing(null);
      setError('会议已截止或作废，登记记录仅可查看和导出');
      return;
    }
    const form = editing.form;
    if (!form.visitorCompany.trim() || !form.contactName.trim()) return setError('请填写单位和姓名');
    if (!/^1[3-9]\d{9}$/.test(form.contactPhone.trim())) return setError('请填写正确的手机号码');
    const companions = form.companions.filter(hasContent).map((item) => ({
      personCompany: item.personCompany.trim(),
      personName: item.personName.trim(),
      personPhone: item.personPhone.trim(),
    }));
    if (companions.length > 49) return setError('同行人员最多添加49位');
    if (companions.some((item) => item.personPhone && !/^1[3-9]\d{9}$/.test(item.personPhone))) {
      return setError('同行人员手机号码格式不正确');
    }
    if (form.travelMode === 'DRIVING' && !form.vehiclePlate.trim()) return setError('驾车来访请填写车牌号');
    const activeContext = meetingRequestContext(projectId, invitation.id);
    const requestTicket = registrationMutationRequestGuardRef.current.begin(activeContext);
    setSaving(true);
    setError('');
    try {
      const updated = responseData(await updateMeetingVisitRegistration(editing.value.id, {
        visitorCompany: form.visitorCompany.trim(),
        contactName: form.contactName.trim(),
        contactPhone: form.contactPhone.trim(),
        companions,
        travelMode: form.travelMode,
        vehiclePlate: form.travelMode === 'DRIVING' ? form.vehiclePlate.trim().toUpperCase() : null,
        visitorRemark: form.visitorRemark.trim() || null,
        version: editing.value.version,
      }), '会议登记保存失败');
      if (!registrationMutationRequestGuardRef.current.isCurrent(
        requestTicket, activeRequestContextRef.current,
      )) return;
      setEditing(null);
      setDetail(updated);
      await load(pageNo);
      await onChanged?.();
    } catch (saveError) {
      if (registrationMutationRequestGuardRef.current.isCurrent(
        requestTicket, activeRequestContextRef.current,
      )) {
        setError(saveError.message || '会议登记保存失败');
      }
    } finally {
      if (registrationMutationRequestGuardRef.current.isCurrent(
        requestTicket, activeRequestContextRef.current,
      )) {
        setSaving(false);
      }
    }
  };

  const confirmVoid = async () => {
    if (!voiding || saving || !reason.trim()) return;
    if (!open) return;
    if (!isMeetingInvitationOpen(invitation, Date.now())) {
      setVoiding(null);
      setReason('');
      setError('会议已截止或作废，登记记录仅可查看和导出');
      return;
    }
    const activeContext = meetingRequestContext(projectId, invitation.id);
    const requestTicket = registrationMutationRequestGuardRef.current.begin(activeContext);
    setSaving(true);
    setError('');
    try {
      await voidMeetingVisitRegistration(voiding.id, reason.trim(), voiding.version);
      if (!registrationMutationRequestGuardRef.current.isCurrent(
        requestTicket, activeRequestContextRef.current,
      )) return;
      setVoiding(null);
      setReason('');
      setDetail(null);
      const remainingTotal = filters.status === 'REGISTERED'
        ? Math.max(0, Number(page.total || 0) - 1)
        : Number(page.total || 0);
      const targetPage = normalizePageAfterRemoval(pageNo, remainingTotal, PAGE_SIZE);
      await load(targetPage);
      await onChanged?.();
    } catch (requestError) {
      if (registrationMutationRequestGuardRef.current.isCurrent(
        requestTicket, activeRequestContextRef.current,
      )) {
        setError(requestError.message || '会议登记作废失败');
      }
    } finally {
      if (registrationMutationRequestGuardRef.current.isCurrent(
        requestTicket, activeRequestContextRef.current,
      )) {
        setSaving(false);
      }
    }
  };

  const exportCurrent = async () => {
    if (exporting) return;
    const activeContext = meetingRequestContext(projectId, invitation.id);
    const requestTicket = registrationExportRequestGuardRef.current.begin(activeContext);
    const requestProjectId = projectId;
    const requestInvitationId = invitation.id;
    const requestInviteNo = invitation.inviteNo;
    setExporting(true);
    setError('');
    try {
      const blob = await exportMeetingVisitRegistrations({
        projectId: requestProjectId,
        invitationId: requestInvitationId,
      });
      if (!registrationExportRequestGuardRef.current.isCurrent(
        requestTicket, activeRequestContextRef.current,
      )) return;
      const link = document.createElement('a');
      link.href = URL.createObjectURL(blob);
      link.download = `会议登记_${requestInviteNo}.xlsx`;
      link.click();
      URL.revokeObjectURL(link.href);
    } catch (exportError) {
      if (registrationExportRequestGuardRef.current.isCurrent(
        requestTicket, activeRequestContextRef.current,
      )) {
        setError(exportError.message || '会议登记导出失败');
      }
    } finally {
      if (registrationExportRequestGuardRef.current.isCurrent(
        requestTicket, activeRequestContextRef.current,
      )) {
        setExporting(false);
      }
    }
  };

  const totalPages = Math.max(1, Math.ceil(Number(page.total || 0) / PAGE_SIZE));
  return <div className="site-access-meeting-panel">
    <div className="meeting-closed-loop">
      {[['1', '创建会议'], ['2', '分享邀请'], ['3', '预约登记'], ['4', '会场签到'], ['5', '导出名单']].map(([step, label], index) => <React.Fragment key={step}><div className={index < 4 ? 'done' : ''}><span>{step}</span><b>{label}</b></div>{index < 4 && <i>→</i>}</React.Fragment>)}
    </div>
    <div className="meeting-workbench-tabs"><button type="button" className={section === 'registrations' ? 'active' : ''} onClick={() => setSection('registrations')}>预约登记管理</button><button type="button" className={section === 'checkin' ? 'active' : ''} onClick={() => setSection('checkin')}>会场签到管理</button><p>{section === 'registrations' ? '查看会前预约登记组，并在开放期内纠错或作废。' : '管理会场二维码、逐人签到、现场补录和签到名单导出。'}</p></div>
    {section === 'checkin' && <MeetingCheckinPanel invitation={invitation} projectId={projectId} canManage={canManage} canExport={canExport} />}
    {section === 'registrations' && <>
    <div className="site-access-meeting-summary">
      <div><strong>{invitation.registrationGroupCount || 0}</strong><span>登记组</span></div>
      <div><strong>{invitation.registeredPersonCount || 0}</strong><span>登记人员</span></div>
      <p>{open
        ? '共享会议码按微信身份隔离；每个身份只能保留一组有效登记。'
        : '会议已截止或作废，登记记录仅支持查看和导出。'}</p>
      {canExport && <button type="button" disabled={exporting} onClick={exportCurrent}>{exporting ? '导出中...' : '导出本场'}</button>}
    </div>
    <div className="site-access-meeting-toolbar">
      <select value={filters.status} onChange={(event) => {
        const next = { ...filters, status: event.target.value };
        setFilters(next); load(1, next);
      }}><option value="">全部登记</option><option value="REGISTERED">已登记</option><option value="VOIDED">已作废</option></select>
      <input value={filters.keyword} placeholder="登记编号、单位、姓名、车牌" onChange={(event) => setFilters({ ...filters, keyword: event.target.value })} onKeyDown={(event) => event.key === 'Enter' && load(1)} />
      <button type="button" onClick={() => load(1)}>查询</button>
    </div>
    {error && <div className="site-access-error" onClick={() => setError('')}>{error}</div>}
    <div className="site-access-meeting-table"><table><thead><tr><th>登记编号</th><th>单位 / 姓名</th><th>人数</th><th>出行</th><th>登记时间</th><th>状态</th><th>操作</th></tr></thead>
      <tbody>{!loading && (page.records || []).map((item) => <tr key={item.id}>
        <td><button className="link" type="button" onClick={() => openDetail(item.id)}>{item.registrationNo}</button></td>
        <td>{item.visitorCompany}<small>{item.contactName}</small></td><td>{item.visitorCount}</td>
        <td>{item.travelMode === 'DRIVING' ? `驾车 · ${item.vehiclePlate || '-'}` : '非驾车'}</td>
        <td>{formatDateTime(item.registeredTime)}</td><td>{item.status === 'REGISTERED' ? '已登记' : '已作废'}</td>
        <td><div className="site-access-row-actions"><button type="button" onClick={() => openDetail(item.id)}>详情</button>
          {canManage && open && item.status === 'REGISTERED' && <button type="button" onClick={() => openEdit(item.id)}>纠错</button>}
          {canManage && open && item.status === 'REGISTERED' && <button className="danger" type="button" onClick={() => { setReason(''); setVoiding(item); }}>作废</button>}
        </div></td>
      </tr>)}
      {!loading && !(page.records || []).length && <tr><td colSpan="7" className="site-access-empty">暂无会议登记</td></tr>}
      {loading && <tr><td colSpan="7" className="site-access-empty">正在加载...</td></tr>}</tbody></table></div>
    <div className="site-access-pagination"><span>共 {page.total || 0} 条 · 第 {pageNo}/{totalPages} 页</span><button type="button" disabled={pageNo <= 1 || loading} onClick={() => load(pageNo - 1)}>上一页</button><button type="button" disabled={pageNo >= totalPages || loading} onClick={() => load(pageNo + 1)}>下一页</button></div>

    {detail && <MeetingModal title={`会议登记 · ${detail.registrationNo}`} onClose={() => setDetail(null)} width={920} className="site-access-visitor-modal">
      <div className="site-access-detail-grid">{[
        ['状态', detail.status === 'REGISTERED' ? '已登记' : '已作废'], ['单位', detail.visitorCompany],
        ['姓名', `${detail.contactName} ${detail.contactPhone || ''}`], ['登记时间', formatDateTime(detail.registeredTime)],
        ['出行方式', detail.travelMode === 'DRIVING' ? `驾车 · ${detail.vehiclePlate || '-'}` : '非驾车'],
        ['资料来源', detail.sourceProfileName || '本次手工填写'], ['备注', detail.visitorRemark || '-'],
      ].map(([label, value]) => <div key={label}><span>{label}</span><b>{value}</b></div>)}</div>
      <h3>登记人员（{detail.visitors?.length || 0}）</h3><div className="site-access-person-list">{(detail.visitors || []).map((person, index) => <div key={index}><span>{person.personType === 'CONTACT' ? '本人' : '同行人员'}</span><div className="site-access-person-contact-fields"><b>单位：{person.personCompany || '-'}</b><b>姓名：{person.personName || '-'}</b><b>手机号码：{person.personPhone || '-'}</b></div></div>)}</div>
      <h3>操作记录</h3><div className="site-access-audit-list">{(detail.auditLogs || []).map((log, index) => <div key={index}><b>{({ REGISTER: '访客登记', UPDATE: '后台纠错', VOID: '作废登记' })[log.actionType] || log.actionType}</b><span>{log.operatorName} · {formatDateTime(log.createTime)}</span><p>{log.comment || '-'}</p></div>)}</div>
      {detail.voidReason && <div className="site-access-void-reason">作废原因：{detail.voidReason}</div>}
      <div className="site-access-modal-actions"><button type="button" onClick={() => setDetail(null)}>关闭</button>{canManage && open && detail.status === 'REGISTERED' && <button type="button" onClick={() => { setDetail(null); openEdit(detail.id); }}>纠错</button>}{canManage && open && detail.status === 'REGISTERED' && <button className="danger" type="button" onClick={() => { setReason(''); setVoiding(detail); }}>作废</button>}</div>
    </MeetingModal>}

    {editing && <MeetingModal title={`纠错 · ${editing.value.registrationNo}`} onClose={() => !saving && setEditing(null)}>
      <div className="site-access-form-grid"><label className="site-access-field full"><span>单位</span><input maxLength="200" value={editing.form.visitorCompany} onChange={(event) => updateForm({ visitorCompany: event.target.value })} /></label><label className="site-access-field"><span>姓名</span><input maxLength="50" value={editing.form.contactName} onChange={(event) => updateForm({ contactName: event.target.value })} /></label><label className="site-access-field"><span>手机号码</span><input maxLength="11" value={editing.form.contactPhone} onChange={(event) => updateForm({ contactPhone: event.target.value })} /></label>
        <div className="site-access-companions full"><div className="site-access-companion-head"><strong>同行人员（最多49位）</strong><button type="button" disabled={editing.form.companions.length >= 49} onClick={() => updateForm({ companions: [...editing.form.companions, { personCompany: '', personName: '', personPhone: '' }] })}>添加同行人</button></div>{editing.form.companions.map((person, index) => <div className="site-access-companion-row" key={index}><input placeholder="单位" maxLength="200" value={person.personCompany} onChange={(event) => updateForm({ companions: editing.form.companions.map((item, i) => i === index ? { ...item, personCompany: event.target.value } : item) })} /><input placeholder="姓名" maxLength="50" value={person.personName} onChange={(event) => updateForm({ companions: editing.form.companions.map((item, i) => i === index ? { ...item, personName: event.target.value } : item) })} /><input placeholder="手机号码" maxLength="11" value={person.personPhone} onChange={(event) => updateForm({ companions: editing.form.companions.map((item, i) => i === index ? { ...item, personPhone: event.target.value } : item) })} /><button className="danger" type="button" onClick={() => updateForm({ companions: editing.form.companions.filter((_, i) => i !== index) })}>移除</button></div>)}</div>
        <label className="site-access-field"><span>出行方式</span><select value={editing.form.travelMode} onChange={(event) => updateForm({ travelMode: event.target.value, vehiclePlate: event.target.value === 'OTHER' ? '' : editing.form.vehiclePlate })}><option value="OTHER">非驾车</option><option value="DRIVING">驾车</option></select></label><label className="site-access-field"><span>车牌号</span><input disabled={editing.form.travelMode !== 'DRIVING'} maxLength="20" value={editing.form.vehiclePlate} onChange={(event) => updateForm({ vehiclePlate: event.target.value.toUpperCase() })} /></label><label className="site-access-field full"><span>外访备注</span><textarea maxLength="500" value={editing.form.visitorRemark} onChange={(event) => updateForm({ visitorRemark: event.target.value })} /></label></div>
      <div className="site-access-modal-actions"><button type="button" disabled={saving} onClick={() => setEditing(null)}>取消</button><button className="primary" type="button" disabled={saving || !open} onClick={save}>{saving ? '保存中...' : '保存纠错'}</button></div>
    </MeetingModal>}

    {voiding && <MeetingModal title={`作废登记 · ${voiding.registrationNo}`} onClose={() => { if (!saving) { setVoiding(null); setReason(''); } }} width={520}><div className="site-access-form-grid"><label className="site-access-field full"><span>作废原因</span><textarea maxLength="300" value={reason} onChange={(event) => setReason(event.target.value)} /></label><p className="full">作废后原记录与审计仍保留，该微信用户可在会议截止前重新登记。</p></div><div className="site-access-modal-actions"><button type="button" disabled={saving} onClick={() => { setVoiding(null); setReason(''); }}>取消</button><button className="danger" type="button" disabled={saving || !open || !reason.trim()} onClick={confirmVoid}>{saving ? '处理中...' : '确认作废'}</button></div></MeetingModal>}
    </>}
  </div>;
}
