<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import AppTabBar from '@/components/AppTabBar.vue';
import { getScopedTodoPage, getUserNotifications, markAllNotificationsRead, markNotificationRead } from '@/api/todo';
import { useAuthStore } from '@/stores/auth';
import { mergeTodoItems, useTodoStore } from '@/stores/todo';
import type { PageResult, TodoItem, UserNotification } from '@/types';
import { usePageScrollHeight } from '@/utils/navLayout';
import { showToast } from '@/utils/navigation';
import { openBusinessRoute } from '@/utils/businessNavigation';

type ViewKey = 'PENDING' | 'CC' | 'NOTIFICATION';
type BusinessKey = 'ALL' | 'INSPECTION' | 'QUALITY' | 'SEAL';
type PageMeta = { pageNo: number; pageSize: number; total: number };
type PagePayload =
  | { view: 'PENDING' | 'CC'; result: PageResult<TodoItem> }
  | { view: 'NOTIFICATION'; result: PageResult<UserNotification> };

const TODO_PAGE_SIZE = 20;
const NOTIFICATION_PAGE_SIZE = 50;

const auth = useAuthStore();
const todoStore = useTodoStore();
const activeView = ref<ViewKey>('PENDING');
const businessFilter = ref<BusinessKey>('ALL');
const pendingTodos = ref<TodoItem[]>([]);
const ccTodos = ref<TodoItem[]>([]);
const notifications = ref<UserNotification[]>([]);
const loading = ref(false);
const loadingMore = ref(false);
const errorMessage = ref('');
const pages = reactive<Record<ViewKey, PageMeta>>({
  PENDING: { pageNo: 0, pageSize: TODO_PAGE_SIZE, total: 0 },
  CC: { pageNo: 0, pageSize: TODO_PAGE_SIZE, total: 0 },
  NOTIFICATION: { pageNo: 0, pageSize: NOTIFICATION_PAGE_SIZE, total: 0 }
});
let requestSequence = 0;
let loadMoreSequence = 0;
const { scrollStyle } = usePageScrollHeight({ bottomRpx: 124, minHeight: 300, includeSafeBottom: false });

const viewOptions = computed(() => [
  { key: 'PENDING' as const, label: '待我处理', count: todoStore.state.summary.pendingCount },
  { key: 'CC' as const, label: '抄送我的', count: todoStore.state.summary.ccCount },
  { key: 'NOTIFICATION' as const, label: '通知', count: todoStore.state.summary.unreadNotificationCount }
]);

const businessOptions: Array<{ key: BusinessKey; label: string }> = [
  { key: 'ALL', label: '全部' },
  { key: 'INSPECTION', label: '巡检' },
  { key: 'QUALITY', label: '质量' },
  { key: 'SEAL', label: '用印' }
];

function businessKind(value: { businessType?: string; routeKey?: string; routeCode?: string; type?: string }) : Exclude<BusinessKey, 'ALL'> {
  const businessType = String(value.businessType || '').toUpperCase();
  const routeKey = String(value.routeCode || value.routeKey || '').toUpperCase();
  if (businessType.includes('SEAL') || routeKey.includes('SEAL') || value.type === 'SEAL_APPROVAL') return 'SEAL';
  if (businessType.includes('QUALITY') || routeKey.includes('QUALITY')) return 'QUALITY';
  return 'INSPECTION';
}

const currentTodos = computed(() => activeView.value === 'CC' ? ccTodos.value : pendingTodos.value);
const visibleTodos = computed(() => currentTodos.value.filter((item) =>
  businessFilter.value === 'ALL' || businessKind(item) === businessFilter.value));
const visibleNotifications = computed(() => notifications.value.filter((item) =>
  businessFilter.value === 'ALL' || businessKind(item) === businessFilter.value));
const currentItemCount = computed(() => activeView.value === 'NOTIFICATION'
  ? notifications.value.length : currentTodos.value.length);
const hasMore = computed(() => {
  const page = pages[activeView.value];
  return currentItemCount.value < page.total && page.pageNo * page.pageSize < page.total;
});

function hideNativeTabBar() { uni.hideTabBar({ animation: false, fail: () => undefined }); }

onShow(async () => {
  hideNativeTabBar();
  if (!await auth.ensureRootAccess('/pages/todo/index')) return;
  await refreshCurrent(true);
});

function todoFilterType(value: BusinessKey) {
  if (value === 'INSPECTION') return 'INSPECTION_RECORD';
  if (value === 'QUALITY') return 'QUALITY';
  if (value === 'SEAL') return 'SEAL';
  return 'ALL';
}

function notificationBusinessType(value: BusinessKey) {
  if (value === 'INSPECTION') return 'INSPECTION_RECORD';
  if (value === 'QUALITY') return 'QUALITY_ISSUE';
  if (value === 'SEAL') return 'SEAL_APPLICATION';
  return undefined;
}

function resetView(view: ViewKey) {
  const pageSize = view === 'NOTIFICATION' ? NOTIFICATION_PAGE_SIZE : TODO_PAGE_SIZE;
  pages[view] = { pageNo: 0, pageSize, total: 0 };
  if (view === 'PENDING') pendingTodos.value = [];
  else if (view === 'CC') ccTodos.value = [];
  else notifications.value = [];
}

function mergeNotifications(current: UserNotification[], incoming: UserNotification[]) {
  const merged = new Map<number, UserNotification>();
  current.forEach((item) => merged.set(item.id, item));
  incoming.forEach((item) => merged.set(item.id, item));
  return [...merged.values()];
}

function applyPage(payload: PagePayload, append: boolean) {
  const { view, result } = payload;
  pages[view] = {
    pageNo: Math.max(1, Number(result.pageNo || 1)),
    pageSize: Math.max(1, Number(result.pageSize || (view === 'NOTIFICATION' ? NOTIFICATION_PAGE_SIZE : TODO_PAGE_SIZE))),
    total: Math.max(0, Number(result.total || 0))
  };
  if (view === 'NOTIFICATION') {
    notifications.value = mergeNotifications(append ? notifications.value : [], result.records || []);
    return;
  }
  const records = result.records || [];
  if (view === 'PENDING') {
    pendingTodos.value = mergeTodoItems(append ? pendingTodos.value : [], records);
    todoStore.state.todos = pendingTodos.value;
    todoStore.state.todoPage = { ...pages.PENDING };
  } else {
    ccTodos.value = mergeTodoItems(append ? ccTodos.value : [], records);
  }
}

async function fetchPage(view: ViewKey, filter: BusinessKey, pageNo: number): Promise<PagePayload> {
  if (view === 'NOTIFICATION') {
    const result = await getUserNotifications({
      businessType: notificationBusinessType(filter),
      pageNo,
      pageSize: NOTIFICATION_PAGE_SIZE
    });
    return { view, result };
  }
  const result = await getScopedTodoPage(view, {
    type: todoFilterType(filter),
    pageNo,
    pageSize: TODO_PAGE_SIZE
  });
  return { view, result };
}

function contextMatches(sequence: number, view: ViewKey, filter: BusinessKey) {
  return sequence === requestSequence && view === activeView.value && filter === businessFilter.value;
}

async function refreshCurrent(refreshSummary = false) {
  const sequence = ++requestSequence;
  ++loadMoreSequence;
  const view = activeView.value;
  const filter = businessFilter.value;
  loading.value = true;
  loadingMore.value = false;
  errorMessage.value = '';
  resetView(view);
  try {
    const [payload] = await Promise.all([
      fetchPage(view, filter, 1),
      refreshSummary ? todoStore.loadSummary() : Promise.resolve()
    ]);
    if (!contextMatches(sequence, view, filter)) return;
    applyPage(payload, false);
  } catch (error) {
    if (!contextMatches(sequence, view, filter)) return;
    errorMessage.value = error instanceof Error ? error.message : '个人待办加载失败';
  } finally {
    if (contextMatches(sequence, view, filter)) loading.value = false;
  }
}

async function loadMore() {
  if (loading.value || loadingMore.value || !hasMore.value) return;
  const sequence = requestSequence;
  const moreSequence = ++loadMoreSequence;
  const view = activeView.value;
  const filter = businessFilter.value;
  const nextPage = pages[view].pageNo + 1;
  loadingMore.value = true;
  try {
    const payload = await fetchPage(view, filter, nextPage);
    if (!contextMatches(sequence, view, filter) || moreSequence !== loadMoreSequence) return;
    applyPage(payload, true);
  } catch (error) {
    if (contextMatches(sequence, view, filter) && moreSequence === loadMoreSequence) {
      showToast(error instanceof Error ? error.message : '更多个人事项加载失败');
    }
  } finally {
    if (contextMatches(sequence, view, filter) && moreSequence === loadMoreSequence) {
      loadingMore.value = false;
    }
  }
}

async function changeView(value: ViewKey) {
  if (activeView.value === value) return;
  activeView.value = value;
  await refreshCurrent();
}

async function changeBusinessFilter(value: BusinessKey) {
  if (businessFilter.value === value) return;
  businessFilter.value = value;
  await refreshCurrent();
}

function typeLabel(todo: TodoItem) {
  if (businessKind(todo) === 'SEAL') return todo.scope === 'CC' ? '用印抄送' : '用印审批';
  if (businessKind(todo) === 'QUALITY') return todo.type === 'RECHECK' ? '质量复查' : '质量整改';
  if (todo.type === 'RECTIFICATION') return '巡检整改';
  if (todo.type === 'RECHECK') return '巡检复查';
  if (todo.type === 'REVIEW') return '巡检复核';
  return '待巡检';
}

function businessLabel(kind: Exclude<BusinessKey, 'ALL'>) {
  return kind === 'SEAL' ? '印' : kind === 'QUALITY' ? '质' : '巡';
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '';
}

function openTodo(todo: TodoItem) { openBusinessRoute(todo); }

async function openNotification(item: UserNotification) {
  if (!item.isRead) {
    try {
      await markNotificationRead(item.id);
      item.isRead = true;
      await todoStore.loadSummary();
    } catch (error) {
      showToast(error instanceof Error ? error.message : '通知已读状态更新失败');
    }
  }
  openBusinessRoute(item);
}

async function readAll() {
  try {
    await markAllNotificationsRead();
    notifications.value.forEach((item) => { item.isRead = true; });
    await todoStore.loadSummary();
    showToast('通知已全部标记为已读');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '全部已读失败');
  }
}
</script>

<template>
  <view class="todo-page">
    <AppNavBar title="个人待办" :show-back="false" />
    <view class="view-tabs">
      <button v-for="item in viewOptions" :key="item.key" :class="{ active: activeView === item.key }" @tap="changeView(item.key)">
        <text>{{ item.label }}</text><text v-if="item.count" class="count">{{ item.count > 99 ? '99+' : item.count }}</text>
      </button>
    </view>
    <view class="business-tabs">
      <button v-for="item in businessOptions" :key="item.key" :class="{ active: businessFilter === item.key }" @tap="changeBusinessFilter(item.key)">{{ item.label }}</button>
      <button v-if="activeView === 'NOTIFICATION' && notifications.some((item) => !item.isRead)" class="read-all" @tap="readAll">全部已读</button>
    </view>

    <scroll-view class="todo-scroll" scroll-y enable-flex lower-threshold="120" :style="scrollStyle" refresher-enabled :refresher-triggered="loading" @refresherrefresh="refreshCurrent(true)" @scrolltolower="loadMore">
      <view class="content">
        <view v-if="loading && !currentItemCount" class="state-card">正在加载个人事项…</view>
        <view v-else-if="errorMessage" class="state-card error"><text>{{ errorMessage }}</text><button @tap="refreshCurrent(true)">重新加载</button></view>

        <template v-else-if="activeView !== 'NOTIFICATION'">
          <button v-for="todo in visibleTodos" :key="todo.todoKey || `${todo.type}-${todo.targetId}`" class="business-card" :class="[`kind-${businessKind(todo).toLowerCase()}`, { urgent: todo.priority === 'danger' }]" @tap="openTodo(todo)">
            <view class="kind-mark">{{ businessLabel(businessKind(todo)) }}</view>
            <view class="card-copy">
              <view class="card-title"><text>{{ todo.title }}</text><text>{{ typeLabel(todo) }}</text></view>
              <text v-if="todo.summary" class="summary">{{ todo.summary }}</text>
              <text class="meta">{{ [todo.projectName, todo.applicantName ? `申请人 ${todo.applicantName}` : '', todo.dueText].filter(Boolean).join(' · ') }}</text>
              <text v-if="todo.createdAt" class="time">{{ formatTime(todo.createdAt) }}</text>
            </view>
            <text class="arrow">›</text>
          </button>
          <view v-if="!visibleTodos.length" class="empty-card"><text>✓</text><text>{{ activeView === 'CC' ? '暂无抄送给我的事项' : '当前没有待处理事项' }}</text><text>业务状态变化后会自动更新</text></view>
        </template>

        <template v-else>
          <button v-for="item in visibleNotifications" :key="item.id" class="notification-card" :class="{ unread: !item.isRead }" @tap="openNotification(item)">
            <view class="unread-dot"></view>
            <view class="card-copy"><view class="card-title"><text>{{ item.title }}</text><text>{{ businessKind(item) === 'SEAL' ? '用印' : businessKind(item) === 'QUALITY' ? '质量' : '巡检' }}</text></view><text v-if="item.summary" class="summary">{{ item.summary }}</text><text class="meta">{{ [item.projectName, formatTime(item.createTime)].filter(Boolean).join(' · ') }}</text></view>
            <text class="arrow">›</text>
          </button>
          <view v-if="!visibleNotifications.length" class="empty-card"><text>铃</text><text>暂无业务通知</text><text>审批、整改和抄送动态会显示在这里</text></view>
        </template>
        <view v-if="loadingMore" class="load-more">正在加载更多</view>
        <view v-else-if="currentItemCount && hasMore" class="load-more">上拉加载更多</view>
        <view v-else-if="currentItemCount && !hasMore" class="load-more">已显示全部 {{ pages[activeView].total }} 条</view>
      </view>
    </scroll-view>
    <AppTabBar active="todo" />
  </view>
</template>

<style scoped>
.todo-page { height: 100vh; overflow: hidden; background: #f4f6f7; color: #223247; }
.view-tabs { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 10rpx; margin: 8rpx 24rpx 0; padding: 8rpx; border-radius: 17rpx; background: #e7edf1; }
.view-tabs button { min-height: 62rpx; gap: 8rpx; border-radius: 13rpx; color: #657587; font-size: 22rpx; font-weight: 700; }
.view-tabs button.active { background: #fff; color: #315f86; box-shadow: 0 5rpx 16rpx rgba(49,95,134,.1); }
.count { display: flex; min-width: 31rpx; height: 31rpx; align-items: center; justify-content: center; padding: 0 7rpx; border-radius: 999rpx; background: #c65050; color: #fff; font-size: 17rpx; }
.business-tabs { display: flex; align-items: center; gap: 10rpx; padding: 16rpx 24rpx 10rpx; }
.business-tabs button { min-height: 52rpx; padding: 0 20rpx; border-radius: 999rpx; background: #e9eef2; color: #718090; font-size: 20rpx; }
.business-tabs button.active { background: #dbe8f0; color: #315f86; font-weight: 750; }
.business-tabs .read-all { margin-left: auto; background: transparent; color: #315f86; }
.content { display: flex; flex-direction: column; gap: 14rpx; padding: 8rpx 24rpx 34rpx; }
.business-card,.notification-card { display: flex; width: 100%; min-height: 132rpx; align-items: flex-start; gap: 17rpx; padding: 20rpx; border: 1rpx solid #e1e8ed; border-radius: 18rpx; background: #fff; box-shadow: 0 8rpx 24rpx rgba(42,64,82,.055); text-align: left; }
.business-card.urgent { border-color: #efc9c9; }
.kind-mark { display: flex; width: 54rpx; height: 54rpx; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 15rpx; background: #e5eef4; color: #315f86; font-size: 22rpx; font-weight: 850; }
.kind-quality .kind-mark { background: #e9ebf7; color: #5866a0; }.kind-seal .kind-mark { background: #f5ecdf; color: #966421; }
.card-copy { min-width: 0; flex: 1; }
.card-title { display: flex; align-items: flex-start; justify-content: space-between; gap: 12rpx; }
.card-title text:first-child { min-width: 0; flex: 1; color: #25364a; font-size: 24rpx; font-weight: 800; line-height: 1.4; }
.card-title text:last-child { flex-shrink: 0; padding: 5rpx 9rpx; border-radius: 999rpx; background: #edf2f5; color: #65788a; font-size: 17rpx; }
.summary,.meta,.time { display: block; margin-top: 7rpx; color: #66798b; font-size: 20rpx; line-height: 1.45; }.meta,.time { color: #8a97a5; font-size: 18rpx; }
.arrow { align-self: center; color: #9ca8b3; font-size: 34rpx; }
.notification-card { position: relative; min-height: 116rpx; }.notification-card.unread { border-color: #c8dce9; background: #fbfdff; }.unread-dot { width: 10rpx; height: 10rpx; flex-shrink: 0; margin-top: 13rpx; border-radius: 50%; background: transparent; }.unread .unread-dot { background: #c65050; }
.state-card,.empty-card { display: flex; min-height: 270rpx; align-items: center; justify-content: center; flex-direction: column; padding: 34rpx; border-radius: 18rpx; background: #fff; color: #8794a1; font-size: 21rpx; text-align: center; }
.state-card.error { color: #b75353; }.state-card button { min-height: 60rpx; margin-top: 18rpx; padding: 0 24rpx; border-radius: 12rpx; background: #315f86; color: #fff; }
.empty-card text:first-child { display: flex; width: 62rpx; height: 62rpx; align-items: center; justify-content: center; border-radius: 50%; background: #eaf1f5; color: #4b718a; font-size: 24rpx; font-weight: 800; }.empty-card text:nth-child(2) { margin-top: 16rpx; color: #4d5e70; font-size: 24rpx; font-weight: 750; }.empty-card text:last-child { margin-top: 7rpx; }
.load-more { padding: 18rpx 12rpx 4rpx; color: #97a2ad; font-size: 19rpx; text-align: center; }
</style>
