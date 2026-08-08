import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  getPersonalNotifications,
  getPersonalTodos,
  getPersonalTodoSummary,
  getUnreadNotificationCount,
  markAllNotificationsRead,
  markNotificationRead,
} from '../../services/personalInbox';
import './index.css';

const extractList = (data) => Array.isArray(data)
  ? data
  : (data?.records || data?.items || data?.list || data?.content || []);

const unwrap = (response, fallback) => {
  if (!response || Number(response.code) !== 200) throw new Error(response?.message || fallback);
  return response.data;
};

const errorText = (error, fallback) => error?.response?.data?.message || error?.message || fallback;
const formatDateTime = (value) => value ? String(value).replace('T', ' ').slice(0, 16) : '-';
const notificationIdOf = (item) => item?.notificationId ?? item?.id;

const TODO_TYPES = [
  ['', '全部待办'],
  ['SEAL_APPROVAL', '用印审批'],
  ['INSPECTION', '巡检执行'],
  ['REVIEW', '巡检审核'],
  ['RECTIFICATION', '整改任务'],
  ['RECHECK', '整改复查'],
];

const BUSINESS_LABELS = {
  SEAL_APPLICATION: '用印申请',
  INSPECTION_RECORD: '巡检记录',
  QUALITY_ISSUE: '质量整改',
};

function Badge({ children, tone = 'normal' }) {
  return <span className={`inbox-badge ${tone}`}>{children}</span>;
}

export default function PersonalInboxPage({ projectId, projectList = [], theme: T, onOpenBusiness, onCountsChange }) {
  const [activeTab, setActiveTab] = useState('todos');
  const [todoType, setTodoType] = useState('');
  const [notificationReadStatus, setNotificationReadStatus] = useState('');
  const [businessType, setBusinessType] = useState('');
  const [todos, setTodos] = useState([]);
  const [ccItems, setCcItems] = useState([]);
  const [notifications, setNotifications] = useState([]);
  const [summary, setSummary] = useState({ total: 0, byBusinessType: {}, byTaskType: {} });
  const [todoTotal, setTodoTotal] = useState(0);
  const [ccTotal, setCcTotal] = useState(0);
  const [notificationTotal, setNotificationTotal] = useState(0);
  const [todoPage, setTodoPage] = useState(1);
  const [ccPage, setCcPage] = useState(1);
  const [notificationPage, setNotificationPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const sequenceRef = useRef(0);
  const pageSize = 20;

  const projectName = useMemo(() => projectList.find((item) => Number(item.id) === Number(projectId))?.projectName || '当前项目', [projectId, projectList]);
  const variables = {
    '--inbox-page': T.pageBg,
    '--inbox-card': T.cardBg,
    '--inbox-surface': T.surface2,
    '--inbox-border': T.borderColor,
    '--inbox-text': T.textPrimary,
    '--inbox-secondary': T.textSecondary,
    '--inbox-muted': T.textMuted,
    '--inbox-accent': T.accent,
    '--inbox-active': T.activeItemBg,
    '--inbox-danger': T.danger,
    '--inbox-warning': T.warning,
  };

  const loadSummary = useCallback(async () => {
    if (!projectId) return;
    const response = await getPersonalTodoSummary({ projectId });
    const data = unwrap(response, '待办汇总加载失败') || {};
    const todoSummary = data.todoSummary || data.todos || data;
    const next = {
      total: Number(todoSummary.total ?? todoSummary.todoCount ?? todoSummary.pendingCount ?? 0),
      byBusinessType: todoSummary.byBusinessType || {},
      byTaskType: todoSummary.byTaskType || {},
    };
    setSummary(next);
    onCountsChange?.({ todoCount: next.total });
  }, [onCountsChange, projectId]);

  const loadCurrent = useCallback(async () => {
    if (!projectId) return;
    const sequence = ++sequenceRef.current;
    setLoading(true);
    setError('');
    try {
      if (activeTab === 'todos') {
        const response = await getPersonalTodos({
          projectId,
          type: todoType || undefined,
          pageNo: todoPage,
          pageSize,
        });
        const data = unwrap(response, '待办加载失败');
        if (sequence !== sequenceRef.current) return;
        const list = extractList(data);
        setTodos(list);
        setTodoTotal(Number(data?.total ?? list.length));
      } else if (activeTab === 'cc') {
        const response = await getPersonalTodos({
          projectId,
          scope: 'CC',
          pageNo: ccPage,
          pageSize,
        });
        const data = unwrap(response, '抄送事项加载失败');
        if (sequence !== sequenceRef.current) return;
        const list = extractList(data);
        setCcItems(list);
        setCcTotal(Number(data?.total ?? list.length));
      } else {
        const response = await getPersonalNotifications({
          projectId,
          readStatus: notificationReadStatus || undefined,
          businessType: businessType || undefined,
          pageNo: notificationPage,
          pageSize,
        });
        const data = unwrap(response, '消息加载失败');
        if (sequence !== sequenceRef.current) return;
        const list = extractList(data);
        setNotifications(list);
        setNotificationTotal(Number(data?.total ?? list.length));
      }
    } catch (loadError) {
      if (sequence === sequenceRef.current) setError(errorText(loadError, '个人工作台加载失败'));
    } finally {
      if (sequence === sequenceRef.current) setLoading(false);
    }
  }, [activeTab, businessType, ccPage, notificationPage, notificationReadStatus, projectId, todoPage, todoType]);

  useEffect(() => { loadCurrent(); }, [loadCurrent]);
  useEffect(() => {
    loadSummary().catch((loadError) => setError(errorText(loadError, '待办汇总加载失败')));
  }, [loadSummary]);

  useEffect(() => {
    setTodoPage(1);
    setCcPage(1);
    setNotificationPage(1);
  }, [projectId]);

  const openItem = async (item, notification = false) => {
    if (notification && String(item.readStatus || '').toUpperCase() !== 'READ' && notificationIdOf(item)) {
      try {
        await markNotificationRead(notificationIdOf(item));
        setNotifications((current) => current.map((row) => notificationIdOf(row) === notificationIdOf(item) ? { ...row, readStatus: 'READ', read: true } : row));
        onCountsChange?.({ notificationDelta: -1 });
      } catch (markError) {
        setError(errorText(markError, '消息标记已读失败'));
      }
    }
    onOpenBusiness?.(item);
  };

  const markAllRead = async () => {
    setBusy(true);
    setError('');
    try {
      unwrap(await markAllNotificationsRead(projectId), '全部标记已读失败');
      setNotifications((current) => current.map((item) => ({ ...item, readStatus: 'READ', read: true })));
      const unreadResponse = await getUnreadNotificationCount().catch(() => null);
      if (Number(unreadResponse?.code) === 200) {
        onCountsChange?.({
          notificationCount: Number(
            unreadResponse.data?.count
            ?? unreadResponse.data?.unreadCount
            ?? unreadResponse.data
            ?? 0,
          ),
        });
      }
      await loadCurrent();
    } catch (markError) {
      setError(errorText(markError, '全部标记已读失败'));
    } finally {
      setBusy(false);
    }
  };

  const todoPageCount = Math.max(1, Math.ceil(todoTotal / pageSize));
  const notificationPageCount = Math.max(1, Math.ceil(notificationTotal / pageSize));
  const ccPageCount = Math.max(1, Math.ceil(ccTotal / pageSize));

  return (
    <div className="personal-inbox" style={variables}>
      <section className="inbox-head">
        <div><span>{projectName}</span><h1>个人待办与消息</h1><p>这里只展示当前用户的可执行任务和业务通知；抄送不会自动授予审批权限。</p></div>
        <button onClick={() => { loadCurrent(); loadSummary(); }} disabled={loading}>刷新</button>
      </section>

      <section className="inbox-summary">
        <div className="primary"><span>待办总数</span><strong>{summary.total}</strong></div>
        <div><span>用印审批</span><strong>{Number(summary.byTaskType?.SEAL_APPROVAL ?? summary.byBusinessType?.SEAL_APPLICATION ?? 0)}</strong></div>
        <div><span>巡检任务</span><strong>{Number(summary.byTaskType?.INSPECTION || 0) + Number(summary.byTaskType?.REVIEW || 0)}</strong></div>
        <div><span>整改任务</span><strong>{Number(summary.byTaskType?.RECTIFICATION || 0) + Number(summary.byTaskType?.RECHECK || 0)}</strong></div>
      </section>

      <nav className="inbox-tabs">
        <button className={activeTab === 'todos' ? 'active' : ''} onClick={() => setActiveTab('todos')}>待我处理 <b>{summary.total}</b></button>
        <button className={activeTab === 'cc' ? 'active' : ''} onClick={() => setActiveTab('cc')}>抄送我的</button>
        <button className={activeTab === 'notifications' ? 'active' : ''} onClick={() => setActiveTab('notifications')}>消息通知</button>
      </nav>

      {error && <div className="inbox-error"><span>{error}</span><button onClick={() => setError('')}>×</button></div>}

      {activeTab === 'todos' ? <>
        <section className="inbox-toolbar"><select value={todoType} onChange={(event) => { setTodoType(event.target.value); setTodoPage(1); }}>{TODO_TYPES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select><span>当前 {todoTotal} 条可执行待办</span></section>
        <section className="inbox-list">
          {todos.map((item) => <article key={item.todoKey || `${item.taskType}-${item.targetId}`} className={`priority-${item.priority || 'normal'}`} onClick={() => openItem(item)}>
            <div className="inbox-item-icon">待</div>
            <div className="inbox-item-main"><div><Badge tone={item.priority}>{BUSINESS_LABELS[item.businessType] || item.businessType || '业务待办'}</Badge><span>{item.projectName || projectName}</span></div><h2>{item.title || '待处理任务'}</h2><p>{item.summary || item.dueText || '点击进入业务详情处理'}</p><footer><span>{item.applicantName ? `申请人：${item.applicantName}` : ''}</span><time>{formatDateTime(item.createdAt)}</time></footer></div>
            <button>去处理 →</button>
          </article>)}
          {!loading && !todos.length && <div className="inbox-empty"><strong>暂无待处理任务</strong><span>新任务生成后会出现在这里。</span></div>}
          {loading && <div className="inbox-loading">待办加载中…</div>}
        </section>
        <div className="inbox-pagination"><span>第 {todoPage}/{todoPageCount} 页</span><button disabled={loading || todoPage <= 1} onClick={() => setTodoPage((value) => value - 1)}>上一页</button><button disabled={loading || todoPage >= todoPageCount} onClick={() => setTodoPage((value) => value + 1)}>下一页</button></div>
      </> : activeTab === 'cc' ? <>
        <section className="inbox-toolbar"><span>共 {ccTotal} 条抄送事项；只读查看，不计入可执行待办。</span></section>
        <section className="inbox-list">
          {ccItems.map((item) => <article key={item.todoKey || `cc-${item.businessType}-${item.targetId}`} onClick={() => openItem(item)}>
            <div className="inbox-item-icon">抄</div>
            <div className="inbox-item-main"><div><Badge>{BUSINESS_LABELS[item.businessType] || item.businessType || '业务抄送'}</Badge><span>{item.projectName || projectName}</span></div><h2>{item.title || '抄送事项'}</h2><p>{item.summary || '点击查看业务详情'}</p><footer><span>{item.applicantName ? `申请人：${item.applicantName}` : ''}</span><time>{formatDateTime(item.createdAt)}</time></footer></div>
            <button>查看 →</button>
          </article>)}
          {!loading && !ccItems.length && <div className="inbox-empty"><strong>暂无抄送事项</strong><span>你被选为业务抄送人后，可在这里只读查看。</span></div>}
          {loading && <div className="inbox-loading">抄送事项加载中…</div>}
        </section>
        <div className="inbox-pagination"><span>第 {ccPage}/{ccPageCount} 页</span><button disabled={loading || ccPage <= 1} onClick={() => setCcPage((value) => value - 1)}>上一页</button><button disabled={loading || ccPage >= ccPageCount} onClick={() => setCcPage((value) => value + 1)}>下一页</button></div>
      </> : <>
        <section className="inbox-toolbar">
          <select value={notificationReadStatus} onChange={(event) => { setNotificationReadStatus(event.target.value); setNotificationPage(1); }}><option value="">全部状态</option><option value="UNREAD">未读</option><option value="READ">已读</option></select>
          <select value={businessType} onChange={(event) => { setBusinessType(event.target.value); setNotificationPage(1); }}><option value="">全部业务</option><option value="SEAL_APPLICATION">用印申请</option><option value="INSPECTION_RECORD">巡检记录</option><option value="QUALITY_ISSUE">质量整改</option></select>
          <span>共 {notificationTotal} 条消息</span><button onClick={markAllRead} disabled={busy || !notifications.length}>全部标为已读</button>
        </section>
        <section className="inbox-list">
          {notifications.map((item) => {
            const unread = String(item.readStatus || '').toUpperCase() !== 'READ' && item.read !== true;
            return <article key={notificationIdOf(item)} className={unread ? 'unread' : ''} onClick={() => openItem(item, true)}>
              <div className="inbox-item-icon">消</div>
              <div className="inbox-item-main"><div>{unread && <i />}{unread && <Badge tone="warning">未读</Badge>}<Badge>{BUSINESS_LABELS[item.businessType] || item.businessType || '系统通知'}</Badge><span>{item.projectName || projectName}</span></div><h2>{item.title || item.notificationTitle || '业务通知'}</h2><p>{item.content || item.summary || item.message || '-'}</p><footer><span>{item.senderName ? `发送人：${item.senderName}` : ''}</span><time>{formatDateTime(item.createdAt || item.sentAt)}</time></footer></div>
              {(item.targetId || item.routeCode || item.routeKey) && <button>查看 →</button>}
            </article>;
          })}
          {!loading && !notifications.length && <div className="inbox-empty"><strong>暂无消息通知</strong><span>申请状态、审批和抄送消息会汇总到这里。</span></div>}
          {loading && <div className="inbox-loading">消息加载中…</div>}
        </section>
        <div className="inbox-pagination"><span>第 {notificationPage}/{notificationPageCount} 页</span><button disabled={loading || notificationPage <= 1} onClick={() => setNotificationPage((value) => value - 1)}>上一页</button><button disabled={loading || notificationPage >= notificationPageCount} onClick={() => setNotificationPage((value) => value + 1)}>下一页</button></div>
      </>}
    </div>
  );
}
