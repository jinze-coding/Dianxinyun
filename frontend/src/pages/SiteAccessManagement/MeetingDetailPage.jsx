import React, { useEffect, useState } from 'react';
import { getMeetingAttendanceSummary } from '../../services/siteAccess';
import { getMaterialActivities } from '../../services/meetingMaterials';
import MeetingRegistrationPanel from './MeetingRegistrationPanel';
import MeetingMaterialPanel from './MeetingMaterialPanel';

const time = (v) => v ? String(v).replace('T', ' ').slice(0, 16) : '—';
const tabs = { registrations: '预约登记', checkin: '会场签到', materials: '会议资料', activity: '操作记录' };
export default function MeetingDetailPage({ invitation, projectId, currentUser, canManage, canExport, canDelete, active, statusLabel, tab, onTabChange, onBack, onQr, onEdit, onScreen, onChanged }) {
  const [summary, setSummary] = useState(null);
  const [activities, setActivities] = useState([]);
  const [error, setError] = useState('');
  useEffect(() => {
    let current = true;
    const load = async () => {
      try { const response = await getMeetingAttendanceSummary(invitation.id); if (response.code !== 200) throw new Error(response.message); if (current) { setSummary(response.data); setError(''); } }
      catch (e) { if (current) { setSummary(null); setError(e.message || '统计读取失败'); } }
    };
    void load(); const timer = window.setInterval(load, 15000);
    return () => { current = false; window.clearInterval(timer); };
  }, [invitation.id, tab]);
  useEffect(() => {
    if (tab !== 'activity') return undefined;
    let current = true;
    getMaterialActivities(invitation.id).then((rows) => { if (current) setActivities(rows); }).catch((e) => { if (current) setError(e.message); });
    return () => { current = false; };
  }, [invitation.id, tab]);
  const records = [...(invitation.auditLogs || []), ...activities].sort((a, b) => String(b.createTime).localeCompare(String(a.createTime)));
  return <article className="meeting-detail-page">
    <header className="meeting-detail-heading"><div><button className="secondary" onClick={onBack}>← 返回邀请列表</button><p>场内管理 / 会议邀请 · {invitation.inviteNo}</p><h1>{invitation.purpose}</h1><span className={`site-access-status ${active ? 'submitted' : 'expired'}`}>{statusLabel}</span></div>
      <div className="meeting-detail-actions">{canManage && active && <><button onClick={() => onQr(false)}>预约码</button><button onClick={() => onQr(true)}>签到码</button><button onClick={onEdit}>修改会议</button></>}<button className="primary" onClick={onScreen}>签到大屏</button></div></header>
    <section className="meeting-detail-information">{[['所属项目', invitation.projectName], ['会议时间', `${time(invitation.visitStartTime)} 至 ${time(invitation.visitEndTime)}`], ['会议地点', invitation.visitLocation], ['接待人', `${invitation.hostName || '—'} ${invitation.hostPhone || ''}`], ['内部备注', invitation.internalRemark || '—'], ...(invitation.voidReason ? [['作废原因', invitation.voidReason]] : [])].map(([label, value]) => <div key={label}><span>{label}</span><p>{value}</p></div>)}</section>
    <section className="meeting-detail-statistics">{[['预约人数', summary?.reservedPersonCount], ['已签到', summary?.totalCheckedInCount], ['预约待签到', summary?.reservedPendingCount], ['现场补录签到', summary?.walkInCheckedInCount]].map(([label, value]) => <div key={label}><span>{label}</span><strong>{value ?? '—'}<small>人</small></strong></div>)}</section>
    {error && <div className="site-access-error" role="alert">{error}</div>}
    <nav className="meeting-detail-tabs" aria-label="会议详情">{Object.entries(tabs).map(([key, label]) => <button className={tab === key ? 'active' : ''} key={key} onClick={() => onTabChange(key)}>{label}</button>)}</nav>
    <section className="meeting-detail-body">
      {['checkin', 'registrations'].includes(tab) && <MeetingRegistrationPanel invitation={invitation} projectId={projectId} canManage={canManage} canExport={canExport} controlledSection={tab} onChanged={onChanged} />}
      {tab === 'materials' && <MeetingMaterialPanel key={invitation.id} invitation={invitation} userId={currentUser.id} canManage={canManage} canDelete={canDelete} />}
      {tab === 'activity' && <div className="meeting-detail-activities"><h2>操作记录</h2><p>展示本会议最近 200 条登记、签到和资料操作；历史记录继续留档。</p>{records.map((log, index) => <article key={`${log.id}-${index}`}><strong>{log.operatorName}</strong><time>{time(log.createTime)}</time><p>{log.comment || ({ REGISTER: '访客预约登记', UPDATE: '更正登记信息', VOID: '作废登记', CHECK_IN: '人员签到', REVOKE: '撤销签到' })[log.actionType] || '更新会议信息'}</p></article>)}{!records.length && <p>暂无操作记录</p>}</div>}
    </section>
  </article>;
}
