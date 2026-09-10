import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  createMeetingWalkIn,
  exportMeetingAttendance,
  getMeetingAttendanceSummary,
  getMeetingAttendees,
  getMeetingCheckinMiniCode,
  getMeetingCheckinSettings,
  manualMeetingCheckIn,
  revokeMeetingCheckIn,
  rotateMeetingCheckinQr,
  updateMeetingAttendee,
  updateMeetingCheckinSettings,
  updateMeetingCheckinStatus,
} from '../../services/siteAccess';
import { createSiteAccessRequestGuard, meetingRequestContext } from './meetingManagement';

const PAGE_SIZE = 20;
const responseData = (response, fallback) => {
  if (response?.code !== 200) throw new Error(response?.message || fallback);
  return response.data;
};
const formatDateTime = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : '-';
const toLocalInput = (value) => value ? String(value).slice(0, 16) : '';
const STATUS_LABELS = { CHECKED_IN: '已签到', REVOKED: '已撤销', PENDING: '未签到' };
const SOURCE_LABELS = { INVITATION: '预约登记', WALK_IN: '现场补录' };
const METHOD_LABELS = { VENUE_QR: '会场扫码', STAFF_MANUAL: '工作人员' };
const LOCATION_LABELS = {
  IN_RANGE: '范围内', OUT_OF_RANGE: '超出范围', UNAVAILABLE: '未取得定位',
  NO_REFERENCE: '项目未设定位', MANUAL: '人工操作',
};

function CheckinModal({ title, children, onClose, width = 620 }) {
  return <div className="site-access-modal-mask" onMouseDown={onClose}>
    <div className="site-access-modal" style={{ width }} onMouseDown={(event) => event.stopPropagation()}>
      <div className="site-access-modal-head"><strong>{title}</strong><button type="button" onClick={onClose}>×</button></div>
      {children}
    </div>
  </div>;
}

function Field({ label, children, full = false, required = false }) {
  return <label className={`site-access-field${full ? ' full' : ''}`}><span>{label}{required ? ' *' : ''}</span>{children}</label>;
}

const blankCompanion = () => ({ personCompany: '', personName: '', personPhone: '' });

export default function MeetingCheckinPanel({ invitation, projectId, canManage, canExport }) {
  const [summary, setSummary] = useState(null);
  const [settings, setSettings] = useState(null);
  const [page, setPage] = useState({ records: [], total: 0 });
  const [pageNo, setPageNo] = useState(1);
  const [filters, setFilters] = useState({ registrationSource: '', attendanceStatus: '', locationResult: '', keyword: '' });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [saving, setSaving] = useState(false);
  const [settingsForm, setSettingsForm] = useState(null);
  const [qrCode, setQrCode] = useState(null);
  const [action, setAction] = useState(null);
  const [walkIn, setWalkIn] = useState(null);
  const activeRequestContextRef = useRef(meetingRequestContext(projectId, invitation.id));
  const loadRequestGuardRef = useRef(createSiteAccessRequestGuard());
  const mutationRequestGuardRef = useRef(createSiteAccessRequestGuard());
  const exportRequestGuardRef = useRef(createSiteAccessRequestGuard());
  activeRequestContextRef.current = meetingRequestContext(projectId, invitation.id);

  const load = useCallback(async (targetPage = pageNo, nextFilters = filters) => {
    const activeContext = meetingRequestContext(projectId, invitation.id);
    const requestTicket = loadRequestGuardRef.current.begin(activeContext);
    setLoading(true);
    setError('');
    try {
      const [summaryResponse, settingsResponse, attendeeResponse] = await Promise.all([
        getMeetingAttendanceSummary(invitation.id),
        getMeetingCheckinSettings(invitation.id),
        getMeetingAttendees(invitation.id, {
          ...nextFilters,
          registrationSource: nextFilters.registrationSource || undefined,
          attendanceStatus: nextFilters.attendanceStatus || undefined,
          locationResult: nextFilters.locationResult || undefined,
          keyword: nextFilters.keyword.trim() || undefined,
          pageNo: targetPage,
          pageSize: PAGE_SIZE,
        }),
      ]);
      if (!loadRequestGuardRef.current.isCurrent(requestTicket, activeRequestContextRef.current)) return;
      setSummary(responseData(summaryResponse, '签到统计加载失败'));
      setSettings(responseData(settingsResponse, '签到设置加载失败'));
      setPage(responseData(attendeeResponse, '参会人员加载失败') || { records: [], total: 0 });
      setPageNo(targetPage);
    } catch (loadError) {
      if (loadRequestGuardRef.current.isCurrent(requestTicket, activeRequestContextRef.current)) {
        setError(loadError.message || '会议签到信息加载失败');
      }
    } finally {
      if (loadRequestGuardRef.current.isCurrent(requestTicket, activeRequestContextRef.current)) {
        setLoading(false);
      }
    }
  }, [filters, invitation.id, pageNo, projectId]);

  useEffect(() => {
    loadRequestGuardRef.current.invalidate();
    mutationRequestGuardRef.current.invalidate();
    exportRequestGuardRef.current.invalidate();
    setFilters({ registrationSource: '', attendanceStatus: '', locationResult: '', keyword: '' });
    setPageNo(1);
    setPage({ records: [], total: 0 });
    setSummary(null);
    setSettings(null);
    load(1, { registrationSource: '', attendanceStatus: '', locationResult: '', keyword: '' });
    return () => {
      loadRequestGuardRef.current.invalidate();
      mutationRequestGuardRef.current.invalidate();
      exportRequestGuardRef.current.invalidate();
    };
  }, [invitation.id, projectId]);

  const mutate = async (task, success) => {
    if (saving) return;
    const requestTicket = mutationRequestGuardRef.current.begin(
      meetingRequestContext(projectId, invitation.id),
    );
    setSaving(true); setError(''); setNotice('');
    try {
      await task();
      if (!mutationRequestGuardRef.current.isCurrent(
        requestTicket, activeRequestContextRef.current,
      )) return;
      setNotice(success);
      setAction(null); setWalkIn(null); setSettingsForm(null);
      await load(pageNo);
    } catch (requestError) {
      if (mutationRequestGuardRef.current.isCurrent(
        requestTicket, activeRequestContextRef.current,
      )) setError(requestError.message || '操作失败');
    } finally {
      if (mutationRequestGuardRef.current.isCurrent(
        requestTicket, activeRequestContextRef.current,
      )) setSaving(false);
    }
  };

  const openSettings = () => setSettingsForm({
    checkinStartTime: toLocalInput(settings?.checkinStartTime),
    checkinEndTime: toLocalInput(settings?.checkinEndTime),
    locationRadiusMeters: settings?.locationRadiusMeters || 300,
    version: settings?.version ?? 0,
  });

  const saveSettings = () => mutate(async () => {
    if (!settingsForm.checkinStartTime || !settingsForm.checkinEndTime) throw new Error('请填写完整签到时间');
    await updateMeetingCheckinSettings(invitation.id, {
      ...settingsForm,
      checkinStartTime: `${settingsForm.checkinStartTime}:00`,
      checkinEndTime: `${settingsForm.checkinEndTime}:00`,
      locationRadiusMeters: Number(settingsForm.locationRadiusMeters),
    });
  }, '签到时间和定位半径已更新');

  const changeStatus = () => mutate(() => updateMeetingCheckinStatus(invitation.id, {
    enabled: settings?.qrStatus !== 'ENABLED', version: settings?.version ?? 0,
  }), settings?.qrStatus === 'ENABLED' ? '会场签到码已停用' : settings ? '会场签到码已启用' : '会场签到码已创建');

  const rotate = () => mutate(() => rotateMeetingCheckinQr(invitation.id, settings?.version ?? 0), '会场签到码已轮换，旧码立即失效');

  const openQr = async () => {
    const requestTicket = mutationRequestGuardRef.current.begin(
      meetingRequestContext(projectId, invitation.id),
    );
    setSaving(true); setError('');
    try {
      const value = responseData(await getMeetingCheckinMiniCode(invitation.id), '会场签到码加载失败');
      if (!mutationRequestGuardRef.current.isCurrent(
        requestTicket, activeRequestContextRef.current,
      )) return;
      setQrCode(value);
    } catch (requestError) {
      if (mutationRequestGuardRef.current.isCurrent(
        requestTicket, activeRequestContextRef.current,
      )) setError(requestError.message || '会场签到码加载失败');
    } finally {
      if (mutationRequestGuardRef.current.isCurrent(
        requestTicket, activeRequestContextRef.current,
      )) setSaving(false);
    }
  };

  const exportCurrent = async () => {
    if (saving) return;
    const requestTicket = exportRequestGuardRef.current.begin(
      meetingRequestContext(projectId, invitation.id),
    );
    const requestProjectId = projectId;
    const requestInvitationId = invitation.id;
    const requestInviteNo = invitation.inviteNo;
    setSaving(true); setError('');
    try {
      const blob = await exportMeetingAttendance({
        projectId: requestProjectId,
        invitationId: requestInvitationId,
      });
      if (!exportRequestGuardRef.current.isCurrent(
        requestTicket, activeRequestContextRef.current,
      )) return;
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url; link.download = `会议签到_${requestInviteNo}.xlsx`; link.click();
      URL.revokeObjectURL(url);
    } catch (requestError) {
      if (exportRequestGuardRef.current.isCurrent(
        requestTicket, activeRequestContextRef.current,
      )) setError(requestError.message || '会议签到导出失败');
    } finally {
      if (exportRequestGuardRef.current.isCurrent(
        requestTicket, activeRequestContextRef.current,
      )) setSaving(false);
    }
  };

  const submitAction = () => mutate(async () => {
    const reason = action.reason?.trim();
    if (!reason) throw new Error('请填写操作原因');
    if (action.type === 'checkin') {
      await manualMeetingCheckIn(action.item.personId, {
        occurredTime: action.occurredTime ? `${action.occurredTime}:00` : null,
        reason, version: action.item.version || 0,
      });
    } else if (action.type === 'revoke') {
      await revokeMeetingCheckIn(action.item.personId, { reason, version: action.item.version || 0 });
    } else {
      if (!action.personName?.trim()) throw new Error('请填写姓名');
      if (action.item.personType === 'CONTACT' && !action.personCompany?.trim()) throw new Error('请填写单位');
      if (action.item.personType === 'CONTACT' && !/^1[3-9]\d{9}$/.test(action.personPhone?.trim() || '')) throw new Error('请填写正确的手机号码');
      if (action.item.personType !== 'CONTACT' && action.personPhone?.trim() && !/^1[3-9]\d{9}$/.test(action.personPhone.trim())) throw new Error('手机号码格式不正确');
      await updateMeetingAttendee(action.item.personId, {
        personName: action.personName.trim(), personCompany: action.personCompany?.trim() || null,
        personPhone: action.personPhone?.trim() || '', reason, version: action.item.version || 0,
      });
    }
  }, action?.type === 'checkin' ? '人工补签完成' : action?.type === 'revoke' ? '签到已撤销' : '人员信息已更正');

  const submitWalkIn = () => mutate(async () => {
    if (!walkIn.visitorCompany.trim() || !walkIn.contactName.trim()) throw new Error('请填写单位和姓名');
    if (!/^1[3-9]\d{9}$/.test(walkIn.contactPhone.trim())) throw new Error('请填写正确的手机号码');
    if (!walkIn.reason.trim()) throw new Error('请填写现场补录原因');
    const companions = walkIn.companions.filter((person) => person.personCompany.trim() || person.personName.trim() || person.personPhone.trim());
    if (companions.some((person) => !person.personName.trim())) throw new Error('每位到场同行人员都必须填写姓名');
    if (companions.some((person) => person.personPhone.trim() && !/^1[3-9]\d{9}$/.test(person.personPhone.trim()))) throw new Error('同行人员手机号码格式不正确');
    await createMeetingWalkIn(invitation.id, {
      visitorCompany: walkIn.visitorCompany.trim(), contactName: walkIn.contactName.trim(),
      contactPhone: walkIn.contactPhone.trim(),
      companions: companions.map((person) => ({ personCompany: person.personCompany.trim() || null, personName: person.personName.trim(), personPhone: person.personPhone.trim() || null })),
      travelMode: walkIn.travelMode, vehiclePlate: walkIn.travelMode === 'DRIVING' ? walkIn.vehiclePlate.trim().toUpperCase() : null,
      visitorRemark: walkIn.visitorRemark.trim() || null,
      occurredTime: walkIn.occurredTime ? `${walkIn.occurredTime}:00` : null,
      reason: walkIn.reason.trim(),
    });
  }, '现场人员已补录并完成签到');

  const totalPages = Math.max(1, Math.ceil(Number(page.total || 0) / PAGE_SIZE));
  const meetingOpen = invitation.status === 'OPEN'
    && new Date(invitation.visitEndTime).getTime() > Date.now();
  const canCreateQr = canManage && !settings && meetingOpen;
  const canManageQr = canManage && Boolean(settings) && meetingOpen;
  const stats = [
    ['预约人数', summary?.reservedPersonCount || 0, 'blue'],
    ['预约已签到', summary?.reservedCheckedInCount || 0, 'green'],
    ['预约未签到', summary?.reservedPendingCount || 0, 'orange'],
    ['现场补录', summary?.walkInCheckedInCount || 0, 'purple'],
    ['预约签到率', `${Number(summary?.reservedAttendanceRate || 0).toFixed(1)}%`, 'cyan'],
  ];

  return <section className="meeting-checkin-workbench">
    <div className="meeting-checkin-head">
      <div><strong>会场签到工作台</strong><p>会场码只用于到场签到；邀请登记码继续用于会前预约。</p></div>
      <div className="meeting-checkin-head-actions">
        <button type="button" onClick={() => load(pageNo)} disabled={loading}>刷新</button>
        {canManageQr && <button type="button" onClick={openSettings}>签到设置</button>}
        {canManageQr && <button className="primary" type="button" onClick={openQr}>下载会场码</button>}
        {canCreateQr && <button className="primary" type="button" onClick={changeStatus}>创建会场码</button>}
        {canExport && <button type="button" onClick={exportCurrent}>导出签到名单</button>}
      </div>
    </div>
    <div className="meeting-checkin-qr-state">
      <span className={`dot ${String(settings?.effectiveStatus || '').toLowerCase()}`} />
      <b>{settings ? (({ NOT_STARTED: '签到未开始', OPEN: '签到开放中', ENDED: '签到已结束', DISABLED: '签到码已停用', ROTATED: '签到码已轮换', VOIDED: '会议已作废' })[settings.effectiveStatus] || '状态未知') : '尚未创建会场签到码'}</b>
      <span>{settings ? `${formatDateTime(settings.checkinStartTime)} 至 ${formatDateTime(settings.checkinEndTime)}` : canCreateQr ? '该历史会议尚未配置会场码，可由管理人员创建' : '该历史会议未配置会场码，预约人员和登记历史仍可查看'}</span>
      {settings && <span>定位半径 {settings.locationRadiusMeters || '-'} 米 · {settings.projectLocationAvailable ? '项目定位已配置' : '项目未配置定位，仅记录不可用'}</span>}
      {canManageQr && <><button type="button" disabled={saving} onClick={changeStatus}>{settings.qrStatus === 'ENABLED' ? '停用' : '启用'}</button><button type="button" disabled={saving} onClick={rotate}>轮换二维码</button></>}
    </div>
    <div className="meeting-checkin-stats">{stats.map(([label, value, tone]) => <div key={label} className={tone}><span>{label}</span><strong>{value}</strong></div>)}</div>
    <div className="meeting-checkin-location-note">定位证据：范围内 {summary?.inRangeCount || 0} 人，超距 {summary?.outOfRangeCount || 0} 人，定位失败/拒绝 {summary?.unavailableLocationCount || 0} 人，项目无定位 {summary?.noReferenceLocationCount || 0} 人，工作人员签到 {summary?.manualLocationCount || 0} 人。定位异常仅标识，不阻断签到。</div>
    {notice && <div className="site-access-notice" onClick={() => setNotice('')}>{notice}</div>}
    {error && <div className="site-access-error" onClick={() => setError('')}>{error}</div>}
    <div className="meeting-checkin-toolbar">
      <select value={filters.registrationSource} onChange={(event) => { const next = { ...filters, registrationSource: event.target.value }; setFilters(next); load(1, next); }}><option value="">全部来源</option><option value="INVITATION">预约登记</option><option value="WALK_IN">现场补录</option></select>
      <select value={filters.attendanceStatus} onChange={(event) => { const next = { ...filters, attendanceStatus: event.target.value }; setFilters(next); load(1, next); }}><option value="">全部签到状态</option><option value="PENDING">未签到</option><option value="CHECKED_IN">已签到</option><option value="REVOKED">已撤销</option></select>
      <select value={filters.locationResult} onChange={(event) => { const next = { ...filters, locationResult: event.target.value }; setFilters(next); load(1, next); }}><option value="">全部定位结果</option>{Object.entries(LOCATION_LABELS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>
      <input value={filters.keyword} placeholder="登记编号、单位、姓名、车牌" onChange={(event) => setFilters({ ...filters, keyword: event.target.value })} onKeyDown={(event) => event.key === 'Enter' && load(1)} />
      <button type="button" onClick={() => load(1)}>查询</button>
      {canManage && <button className="primary" type="button" onClick={() => setWalkIn({ visitorCompany: '', contactName: '', contactPhone: '', companions: [], travelMode: 'OTHER', vehiclePlate: '', visitorRemark: '', occurredTime: '', reason: '' })}>工作人员现场补录</button>}
    </div>
    <div className="meeting-checkin-table"><table><thead><tr><th>来源 / 登记编号</th><th>参会人员</th><th>预约时间</th><th>签到状态</th><th>签到时间 / 方式</th><th>定位证据</th><th>操作</th></tr></thead><tbody>
      {!loading && (page.records || []).map((item) => <tr key={item.personId}>
        <td><span className={`checkin-source ${String(item.registrationSource).toLowerCase()}`}>{SOURCE_LABELS[item.registrationSource] || item.registrationSource}</span><small>{item.registrationNo}</small></td>
        <td><b>{item.personName || '姓名待补全'}</b><small>{item.personCompany || '-'} · {item.personPhone || '-'}</small></td>
        <td>{formatDateTime(item.registeredTime)}</td>
        <td><span className={`checkin-status ${String(item.attendanceStatus || 'PENDING').toLowerCase()}`}>{STATUS_LABELS[item.attendanceStatus || 'PENDING']}</span></td>
        <td>{formatDateTime(item.checkinTime)}<small>{METHOD_LABELS[item.checkinMethod] || '-'}</small></td>
        <td>{LOCATION_LABELS[item.locationResult] || '-'}<small>{item.distanceMeters == null ? '' : `${item.distanceMeters}米`} {item.accuracyMeters == null ? '' : `· 精度${item.accuracyMeters}米`}</small></td>
        <td><div className="site-access-row-actions">{canManage && item.attendanceStatus !== 'CHECKED_IN' && <button type="button" onClick={() => setAction({ type: 'checkin', item, occurredTime: '', reason: '' })}>补签</button>}{canManage && item.attendanceStatus === 'CHECKED_IN' && <button className="danger" type="button" onClick={() => setAction({ type: 'revoke', item, reason: '' })}>撤销</button>}{canManage && <button type="button" onClick={() => setAction({ type: 'edit', item, personName: item.personName || '', personCompany: item.personCompany || '', personPhone: item.personPhone || '', reason: '' })}>更正</button>}</div></td>
      </tr>)}
      {!loading && !(page.records || []).length && <tr><td colSpan="7" className="site-access-empty">暂无参会人员记录</td></tr>}
      {loading && <tr><td colSpan="7" className="site-access-empty">正在加载...</td></tr>}
    </tbody></table></div>
    <div className="site-access-pagination"><span>共 {page.total || 0} 人 · 第 {pageNo}/{totalPages} 页</span><button type="button" disabled={pageNo <= 1 || loading} onClick={() => load(pageNo - 1)}>上一页</button><button type="button" disabled={pageNo >= totalPages || loading} onClick={() => load(pageNo + 1)}>下一页</button></div>

    {settingsForm && <CheckinModal title="会场签到设置" onClose={() => !saving && setSettingsForm(null)}><div className="site-access-form-grid"><Field label="签到开始" required><input type="datetime-local" value={settingsForm.checkinStartTime} onChange={(event) => setSettingsForm({ ...settingsForm, checkinStartTime: event.target.value })} /></Field><Field label="签到结束" required><input type="datetime-local" value={settingsForm.checkinEndTime} onChange={(event) => setSettingsForm({ ...settingsForm, checkinEndTime: event.target.value })} /></Field><Field label="定位半径（50-2000米）" required full><input type="number" min="50" max="2000" value={settingsForm.locationRadiusMeters} onChange={(event) => setSettingsForm({ ...settingsForm, locationRadiusMeters: event.target.value })} /></Field><p className="full">定位只用于留痕和异常标识，不阻断参会人员签到。</p></div><div className="site-access-modal-actions"><button type="button" onClick={() => setSettingsForm(null)}>取消</button><button className="primary" type="button" disabled={saving} onClick={saveSettings}>{saving ? '保存中...' : '保存设置'}</button></div></CheckinModal>}
    {qrCode && <CheckinModal title={`会场签到二维码 · 第${qrCode.qrVersion || 1}版`} onClose={() => setQrCode(null)} width={440}><div className="site-access-qr">{qrCode.imageContent ? <img src={qrCode.imageContent} alt="会场签到小程序码" /> : <div className="site-access-scene"><span>开发调试 scene</span><code>{qrCode.sceneCode}</code></div>}<p>{qrCode.hint}</p><b>此码仅在会场摆放，请勿与会前邀请登记码混用。</b><p>静态码可能被拍照转发；定位只提供异常证据，不构成严格的物理在场证明。</p></div><div className="site-access-modal-actions"><button type="button" onClick={() => setQrCode(null)}>关闭</button></div></CheckinModal>}
    {action && <CheckinModal title={action.type === 'checkin' ? '工作人员人工补签' : action.type === 'revoke' ? '撤销签到' : '更正参会人员'} onClose={() => !saving && setAction(null)}>{action.type === 'edit' ? <div className="site-access-form-grid"><Field label="姓名" required><input maxLength="50" value={action.personName} onChange={(event) => setAction({ ...action, personName: event.target.value })} /></Field><Field label="手机号码" required={action.item.personType === 'CONTACT'}><input maxLength="11" value={action.personPhone} onChange={(event) => setAction({ ...action, personPhone: event.target.value })} /></Field><Field label="单位" required={action.item.personType === 'CONTACT'} full><input maxLength="200" value={action.personCompany} onChange={(event) => setAction({ ...action, personCompany: event.target.value })} /></Field></div> : action.type === 'checkin' ? <div className="site-access-form-grid"><Field label="实际签到时间"><input type="datetime-local" value={action.occurredTime} onChange={(event) => setAction({ ...action, occurredTime: event.target.value })} /></Field><p className="full">不填写时使用服务端当前时间；历史时间必须在签到窗口内且不晚于当前时间。</p></div> : <p>撤销后该人员可再次扫码或由工作人员重新补签，历史签到仍保留审计。</p>}<div className="site-access-form-grid"><Field label="操作原因" required full><textarea maxLength="300" value={action.reason} onChange={(event) => setAction({ ...action, reason: event.target.value })} /></Field></div><div className="site-access-modal-actions"><button type="button" onClick={() => setAction(null)}>取消</button><button className={action.type === 'revoke' ? 'danger' : 'primary'} type="button" disabled={saving || !action.reason.trim()} onClick={submitAction}>{saving ? '处理中...' : '确认提交'}</button></div></CheckinModal>}
    {walkIn && <CheckinModal title="工作人员现场补录并签到" onClose={() => !saving && setWalkIn(null)} width={760}><div className="site-access-form-grid"><Field label="单位" required full><input maxLength="200" value={walkIn.visitorCompany} onChange={(event) => setWalkIn({ ...walkIn, visitorCompany: event.target.value })} /></Field><Field label="姓名" required><input maxLength="50" value={walkIn.contactName} onChange={(event) => setWalkIn({ ...walkIn, contactName: event.target.value })} /></Field><Field label="手机号码" required><input maxLength="11" value={walkIn.contactPhone} onChange={(event) => setWalkIn({ ...walkIn, contactPhone: event.target.value })} /></Field><Field label="出行方式"><select value={walkIn.travelMode} onChange={(event) => setWalkIn({ ...walkIn, travelMode: event.target.value, vehiclePlate: event.target.value === 'OTHER' ? '' : walkIn.vehiclePlate })}><option value="OTHER">非驾车</option><option value="DRIVING">驾车</option></select></Field><Field label="车牌号"><input disabled={walkIn.travelMode !== 'DRIVING'} maxLength="20" value={walkIn.vehiclePlate} onChange={(event) => setWalkIn({ ...walkIn, vehiclePlate: event.target.value })} /></Field><div className="site-access-companions full"><div className="site-access-companion-head"><strong>同行到场人员</strong><button type="button" onClick={() => setWalkIn({ ...walkIn, companions: [...walkIn.companions, blankCompanion()] })}>添加同行人</button></div>{walkIn.companions.map((person, index) => <div className="site-access-companion-row" key={index}><input placeholder="单位（选填）" maxLength="200" value={person.personCompany} onChange={(event) => setWalkIn({ ...walkIn, companions: walkIn.companions.map((item, i) => i === index ? { ...item, personCompany: event.target.value } : item) })} /><input placeholder="姓名（必填）" maxLength="50" value={person.personName} onChange={(event) => setWalkIn({ ...walkIn, companions: walkIn.companions.map((item, i) => i === index ? { ...item, personName: event.target.value } : item) })} /><input placeholder="手机号码（选填）" maxLength="11" value={person.personPhone} onChange={(event) => setWalkIn({ ...walkIn, companions: walkIn.companions.map((item, i) => i === index ? { ...item, personPhone: event.target.value } : item) })} /><button className="danger" type="button" onClick={() => setWalkIn({ ...walkIn, companions: walkIn.companions.filter((_, i) => i !== index) })}>移除</button></div>)}</div><Field label="实际签到时间"><input type="datetime-local" value={walkIn.occurredTime} onChange={(event) => setWalkIn({ ...walkIn, occurredTime: event.target.value })} /></Field><Field label="现场备注"><input maxLength="500" value={walkIn.visitorRemark} onChange={(event) => setWalkIn({ ...walkIn, visitorRemark: event.target.value })} /></Field><Field label="补录原因" required full><textarea maxLength="300" value={walkIn.reason} onChange={(event) => setWalkIn({ ...walkIn, reason: event.target.value })} /></Field></div><div className="site-access-modal-actions"><button type="button" onClick={() => setWalkIn(null)}>取消</button><button className="primary" type="button" disabled={saving} onClick={submitWalkIn}>{saving ? '提交中...' : '补录并签到'}</button></div></CheckinModal>}
  </section>;
}
