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
  ['DOCUMENT_RECEIPT', '图纸签收'],
];

const BUSINESS_LABELS = {
  SEAL_APPLICATION: '用印申请',
  INSPECTION_RECORD: '巡检记录',
  ELECTRIC_BOX_INSPECTION: '电箱日检提醒',
  EDGE_INSPECTION_TASK: '临边巡检提醒',
  QUALITY_ISSUE: '质量整改',
  QUALITY_WEEKLY_INSPECTION: '质量周检提醒',
  DOCUMENT_DISTRIBUTION: '图纸签收',
};

function Badge({ children, tone = 'normal' }) {
  return <span className={`inbox-badge ${tone}`}>{children}</span>;
}

function EmptyState({ symbol, title, description, onRefresh }) {
  return (
    <div className="inbox-empty">
      <div className="inbox-empty-icon" aria-hidden="true">{symbol}</div>
      <strong>{title}</strong>
      <span>{description}</span>
      <button type="button" onClick={onRefresh}>重新检查</button>
    </div>
  );
}

function Pagination({ page, pageCount, loading, onChange }) {
  if (pageCount <= 1) return null;
  return (
    <div className="inbox-pagination">
      <span>第 {page} / {pageCount} 页</span>
      <button type="button" disabled={loading || page <= 1} onClick={() => onChange(page - 1)}>上一页</button>
      <button type="button" disabled={loading || page >= pageCount} onClick={() => onChange(page + 1)}>下一页</button>
    </div>
  );
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
        const groupFilter = businessType.startsWith('GROUP:') ? businessType.slice(6) : '';
        const response = await getPersonalNotifications({
          projectId,
          readStatus: notificationReadStatus || undefined,
          businessGroup: groupFilter || undefined,
          businessType: groupFilter ? undefined : businessType || undefined,
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
  const refreshAll = () => {
    loadCurrent();
    loadSummary().catch((loadError) => setError(errorText(loadError, '待办汇总加载失败')));
  };

  return (
    <div className="personal-inbox" style={variables}>
      <div className="inbox-shell">
        <header className="inbox-head">
          <div className="inbox-head-copy">
            <span className="inbox-project-chip"><i aria-hidden="true" />{projectName}</span>
            <h1>个人待办与消息</h1>
            <p>集中处理当前项目分配给你的任务，并查看业务抄送与站内通知。</p>
          </div>
          <button type="button" className="inbox-refresh" onClick={refreshAll} disabled={loading}><span aria-hidden="true">↻</span>{loading ? '刷新中…' : '刷新'}</button>
        </header>

        <section className="inbox-summary" aria-label="待办概览">
          <div className="primary"><span className="inbox-summary-icon">总</span><div><span>待办总数</span><small>全部待处理事项</small></div><strong>{summary.total}</strong></div>
          <div className="seal"><span className="inbox-summary-icon">印</span><div><span>用印审批</span><small>等待你的审批</small></div><strong>{Number(summary.byTaskType?.SEAL_APPROVAL ?? summary.byBusinessType?.SEAL_APPLICATION ?? 0)}</strong></div>
          <div className="inspection"><span className="inbox-summary-icon">巡</span><div><span>巡检任务</span><small>执行与审核</small></div><strong>{Number(summary.byTaskType?.INSPECTION || 0) + Number(summary.byTaskType?.REVIEW || 0)}</strong></div>
          <div className="rectification"><span className="inbox-summary-icon">图</span><div><span>图纸签收</span><small>电子与纸质领取</small></div><strong>{Number(summary.byTaskType?.DOCUMENT_RECEIPT ?? summary.byBusinessType?.DOCUMENT_DISTRIBUTION ?? 0)}</strong></div>
        </section>

        <section className="inbox-workbench">
          <nav className="inbox-tabs" role="tablist" aria-label="个人消息分类">
            <button type="button" role="tab" aria-selected={activeTab === 'todos'} className={activeTab === 'todos' ? 'active' : ''} onClick={() => setActiveTab('todos')}>待我处理 <b>{summary.total}</b></button>
            <button type="button" role="tab" aria-selected={activeTab === 'cc'} className={activeTab === 'cc' ? 'active' : ''} onClick={() => setActiveTab('cc')}>抄送我的</button>
            <button type="button" role="tab" aria-selected={activeTab === 'notifications'} className={activeTab === 'notifications' ? 'active' : ''} onClick={() => setActiveTab('notifications')}>消息通知</button>
          </nav>

          {error && <div className="inbox-error"><span>{error}</span><button type="button" aria-label="关闭错误提示" onClick={() => setError('')}>×</button></div>}

          {activeTab === 'todos' ? <div className="inbox-tab-panel">
            <section className="inbox-toolbar">
              <div className="inbox-filter-group"><label htmlFor="todo-type-filter">任务类型</label><select id="todo-type-filter" value={todoType} onChange={(event) => { setTodoType(event.target.value); setTodoPage(1); }}>{TODO_TYPES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></div>
              <span className="inbox-result-count">当前 {todoTotal} 条可执行待办</span>
            </section>
            <section className={`inbox-list${!loading && !todos.length ? ' is-empty' : ''}${loading ? ' is-loading' : ''}`}>
              {todos.map((item) => <article key={item.todoKey || `${item.taskType}-${item.targetId}`} className={`priority-${item.priority || 'normal'}`} onClick={() => openItem(item)}>
                <div className="inbox-item-icon">待</div>
                <div className="inbox-item-main"><div><Badge tone={item.priority}>{BUSINESS_LABELS[item.businessType] || item.businessType || '业务待办'}</Badge><span>{item.projectName || projectName}</span></div><h2>{item.title || '待处理任务'}</h2><p>{item.summary || item.dueText || '点击进入业务详情处理'}</p><footer><span>{item.applicantName ? `申请人：${item.applicantName}` : ''}</span><time>{formatDateTime(item.createdAt)}</time></footer></div>
                <button type="button">去处理 →</button>
              </article>)}
              {!loading && !todos.length && <EmptyState symbol="✓" title="当前任务已全部处理" description="新的审批、巡检或整改任务生成后会自动显示在这里。" onRefresh={refreshAll} />}
              {loading && <div className="inbox-loading"><i aria-hidden="true" /><span>正在加载待办任务…</span></div>}
            </section>
            <Pagination page={todoPage} pageCount={todoPageCount} loading={loading} onChange={setTodoPage} />
          </div> : activeTab === 'cc' ? <div className="inbox-tab-panel">
            <section className="inbox-toolbar inbox-toolbar-note"><span>只读查看业务抄送，不计入可执行待办</span><span className="inbox-result-count">共 {ccTotal} 条</span></section>
            <section className={`inbox-list${!loading && !ccItems.length ? ' is-empty' : ''}${loading ? ' is-loading' : ''}`}>
              {ccItems.map((item) => <article key={item.todoKey || `cc-${item.businessType}-${item.targetId}`} onClick={() => openItem(item)}>
                <div className="inbox-item-icon">抄</div>
                <div className="inbox-item-main"><div><Badge>{BUSINESS_LABELS[item.businessType] || item.businessType || '业务抄送'}</Badge><span>{item.projectName || projectName}</span></div><h2>{item.title || '抄送事项'}</h2><p>{item.summary || '点击查看业务详情'}</p><footer><span>{item.applicantName ? `申请人：${item.applicantName}` : ''}</span><time>{formatDateTime(item.createdAt)}</time></footer></div>
                <button type="button">查看 →</button>
              </article>)}
              {!loading && !ccItems.length && <EmptyState symbol="抄" title="暂无抄送事项" description="当你被选为业务抄送人后，可在这里集中只读查看。" onRefresh={refreshAll} />}
              {loading && <div className="inbox-loading"><i aria-hidden="true" /><span>正在加载抄送事项…</span></div>}
            </section>
            <Pagination page={ccPage} pageCount={ccPageCount} loading={loading} onChange={setCcPage} />
          </div> : <div className="inbox-tab-panel">
            <section className="inbox-toolbar">
              <div className="inbox-filter-group"><label htmlFor="notification-status-filter">阅读状态</label><select id="notification-status-filter" value={notificationReadStatus} onChange={(event) => { setNotificationReadStatus(event.target.value); setNotificationPage(1); }}><option value="">全部状态</option><option value="UNREAD">未读</option><option value="READ">已读</option></select></div>
              <div className="inbox-filter-group"><label htmlFor="notification-business-filter">业务分类</label><select id="notification-business-filter" value={businessType} onChange={(event) => { setBusinessType(event.target.value); setNotificationPage(1); }}><option value="">全部业务</option><option value="SEAL_APPLICATION">用印申请</option><option value="GROUP:INSPECTION">巡检管理</option><option value="GROUP:QUALITY">质量管理</option><option value="DOCUMENT_DISTRIBUTION">图纸签收</option></select></div>
              <span className="inbox-result-count">共 {notificationTotal} 条消息</span><button type="button" className="inbox-read-all" onClick={markAllRead} disabled={busy || !notifications.length}>全部标为已读</button>
            </section>
            <section className={`inbox-list${!loading && !notifications.length ? ' is-empty' : ''}${loading ? ' is-loading' : ''}`}>
              {notifications.map((item) => {
                const unread = String(item.readStatus || '').toUpperCase() !== 'READ' && item.read !== true;
                return <article key={notificationIdOf(item)} className={unread ? 'unread' : ''} onClick={() => openItem(item, true)}>
                  <div className="inbox-item-icon">消</div>
                  <div className="inbox-item-main"><div>{unread && <i />}{unread && <Badge tone="warning">未读</Badge>}<Badge>{BUSINESS_LABELS[item.businessType] || item.businessType || '系统通知'}</Badge><span>{item.projectName || projectName}</span></div><h2>{item.title || item.notificationTitle || '业务通知'}</h2><p>{item.content || item.summary || item.message || '-'}</p><footer><span>{item.senderName ? `发送人：${item.senderName}` : ''}</span><time>{formatDateTime(item.createdAt || item.sentAt)}</time></footer></div>
                  {(item.targetId || item.routeCode || item.routeKey) && <button type="button">查看 →</button>}
                </article>;
              })}
              {!loading && !notifications.length && <EmptyState symbol="信" title="暂无消息通知" description="申请状态、审批结果和业务提醒会统一汇总到这里。" onRefresh={refreshAll} />}
              {loading && <div className="inbox-loading"><i aria-hidden="true" /><span>正在加载消息通知…</span></div>}
            </section>
            <Pagination page={notificationPage} pageCount={notificationPageCount} loading={loading} onChange={setNotificationPage} />
          </div>}
        </section>
      </div>
    </div>
  );
}
