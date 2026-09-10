import React, { useEffect, useRef, useState } from 'react';
import { getMeetingAttendanceScreen, getMeetingCheckinMiniCode } from '../../services/siteAccess';
import { meetingQrImage, meetingScreenQrVisible, nextMeetingScreenPage } from './meetingScreenModel';
import './meetingAttendanceScreen.css';

const formatTime = (value) => value ? String(value).replace('T', ' ').slice(0, 19) : '—';
const statusOf = (error) => Number(error?.response?.status || error?.response?.data?.code || error?.code);
const unwrap = (response) => {
  if (response?.code !== 200) throw Object.assign(new Error(response?.message || '签到数据加载失败'), { code: response?.code });
  return response.data;
};

export default function MeetingAttendanceScreen({ invitationId, theme, onBack, backDisabled = false }) {
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  const [fatal, setFatal] = useState('');
  const [lastUpdated, setLastUpdated] = useState('');
  const [qr, setQr] = useState('');
  const [qrNotice, setQrNotice] = useState('');
  const [clock, setClock] = useState(Date.now());
  const reloadRef = useRef(() => {});
  const containerRef = useRef(null);

  useEffect(() => {
    if (!invitationId) { setFatal('会议展示地址无效'); return undefined; }
    let disposed = false;
    let fetching = false;
    let page = 1;
    let total = 0;
    let offset = 0;
    let qrKey = '';
    let qrRetryAt = 0;
    let qrLoadingKey = '';
    const qrCache = new Map();
    let poll;
    let rotate;
    setData(null); setQr(''); setError(''); setFatal('');

    const refresh = async () => {
      if (disposed || fetching) return;
      fetching = true;
      try {
        const requestedPage = page;
        const value = unwrap(await getMeetingAttendanceScreen(invitationId, requestedPage));
        if (disposed) return;
        if (page !== requestedPage) { queueMicrotask(refresh); return; }
        const serverNow = new Date(value.serverTime).getTime();
        offset = Number.isFinite(serverNow) ? serverNow - Date.now() : 0;
        page = value.pageNo; total = value.total;
        setData(value); setClock(Date.now() + offset); setLastUpdated(formatTime(value.serverTime));
        setError(''); setFatal('');
        const nextKey = `${invitationId}:${value.qrVersion}`;
        if (!meetingScreenQrVisible(value, Date.now() + offset)) {
          qrKey = ''; qrCache.clear(); setQr(''); setQrNotice('');
        } else {
          if (qrKey !== nextKey) { qrKey = nextKey; qrRetryAt = 0; setQr(''); }
          if (qrCache.has(nextKey)) setQr(qrCache.get(nextKey));
          else if (Date.now() >= qrRetryAt && qrLoadingKey !== nextKey) {
            qrLoadingKey = nextKey;
            qrRetryAt = Date.now() + 15000;
            void (async () => { try {
              const code = unwrap(await getMeetingCheckinMiniCode(invitationId));
              if (disposed || qrKey !== nextKey || code.qrVersion !== value.qrVersion) return;
              const source = meetingQrImage(code);
              if (source) { qrCache.clear(); qrCache.set(nextKey, source); setQr(source); setQrNotice(''); }
              else setQrNotice('当前环境暂不能生成微信签到码');
            } catch (qrError) {
              if (disposed || qrKey !== nextKey) return;
              setQr('');
              setQrNotice([401, 403].includes(statusOf(qrError)) ? '当前账号无签到码展示权限' : '签到码加载失败，正在重试');
            } finally { if (qrLoadingKey === nextKey) qrLoadingKey = ''; } })();
          }
        }
      } catch (loadError) {
        if (disposed) return;
        const status = statusOf(loadError);
        if ([401, 403, 404].includes(status)) {
          setData(null); setQr(''); qrKey = ''; qrCache.clear();
          setFatal(status === 401 ? '登录已失效，请重新登录后台' : status === 403 ? '当前账号已无权查看这场会议' : '会议不存在或已删除');
          clearInterval(poll); clearInterval(rotate);
        } else setError('连接中断，正在自动重连');
      } finally { fetching = false; }
    };
    reloadRef.current = refresh;
    void refresh();
    poll = window.setInterval(refresh, 5000);
    rotate = window.setInterval(() => { page = nextMeetingScreenPage(page, total); void refresh(); }, 10000);
    const tick = window.setInterval(() => setClock(Date.now() + offset), 1000);
    return () => {
      disposed = true; reloadRef.current = () => {};
      clearInterval(poll); clearInterval(rotate); clearInterval(tick); qrCache.clear();
    };
  }, [invitationId]);

  const fullScreen = async () => {
    try {
      if (document.fullscreenElement) await document.exitFullscreen();
      else await containerRef.current?.requestFullscreen();
    } catch { setError('浏览器未进入全屏，可使用浏览器全屏菜单'); }
  };
  const showQr = meetingScreenQrVisible(data, clock);

  return <main className="meeting-screen" data-theme={theme.id} ref={containerRef}>
    <div className="meeting-screen-tools"><button type="button" disabled={backDisabled} onClick={() => onBack(data?.projectId)}>返回上一页</button><button type="button" onClick={() => reloadRef.current()}>刷新</button><button type="button" onClick={fullScreen}>全屏切换</button></div>
    {fatal ? <div className="meeting-screen-state" role="alert"><strong>{fatal}</strong><button type="button" disabled={backDisabled} onClick={() => onBack(data?.projectId)}>返回上一页</button></div> : !data ? <div className="meeting-screen-state">{error || '正在加载会议签到…'}</div> : <>
      <header className="meeting-screen-header"><div><span className="meeting-screen-eyebrow">会议现场 · 签到看板</span><h1>{data.title || '会议签到'}</h1><p>{formatTime(data.visitStartTime).slice(0, 16)} — {formatTime(data.visitEndTime).slice(0, 16)}</p></div><div className="meeting-screen-live"><i className={error || data.meetingStatus !== 'OPEN' ? 'paused' : ''} />{data.meetingStatus === 'ENDED' ? '会议已结束' : data.meetingStatus === 'VOIDED' ? '会议已作废' : error ? '连接中断' : '实时更新'}<small>最后更新 {lastUpdated}</small></div></header>
      {error && <div className="meeting-screen-connection" role="status">{error} · 当前显示 {lastUpdated} 的数据</div>}
      <section className="meeting-screen-counts" aria-label="会议签到统计">
        {[['预约人数', data.reservedPersonCount], ['已签到', data.totalCheckedInCount], ['预约待签到', data.reservedPendingCount], ['现场补录签到', data.walkInCheckedInCount]].map(([label, value], index) => <div key={label} className={index === 1 ? 'highlight' : ''}><span>{label}</span><strong>{value ?? 0}<small>人</small></strong></div>)}
      </section>
      <section className={`meeting-screen-body${showQr ? ' with-qr' : ''}`}>
        <div className="meeting-screen-attendees"><div className="meeting-screen-list-head"><h2>已签到人员</h2><span>{data.pageNo} / {Math.max(1, Math.ceil(data.total / 24))} 页 · 每 10 秒翻页</span></div>
          {!data.records?.length ? <div className="meeting-screen-empty">等待来宾签到</div> : <div className="meeting-screen-grid">{data.records.map((person) => <article key={person.personId}><span className="meeting-screen-avatar">{String(person.personName || '访').slice(-1)}</span><div><strong>{person.personName || '姓名待补全'}</strong><p title={person.personCompany}>{person.personCompany || '单位未填写'}</p><time>{formatTime(person.checkinTime).slice(11)}</time></div><b>已签到</b></article>)}</div>}
        </div>
        {showQr && <aside className="meeting-screen-qr"><span>微信扫码签到</span>{qr ? <img src={qr} alt="本场会议签到小程序码" /> : <div className="meeting-screen-qr-placeholder">{qrNotice || '签到码加载中…'}</div>}<strong>欢迎莅临</strong><p>请扫描本场会议签到码<br />确认实际到场人员</p></aside>}
      </section>
      <footer className="meeting-screen-footer"><span>门卫放行后，请在会场完成签到</span><span>签到数据每 5 秒更新</span></footer>
    </>}
  </main>;
}
