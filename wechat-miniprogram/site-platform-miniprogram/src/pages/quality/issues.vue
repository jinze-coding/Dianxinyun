<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad, onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import WorkspaceStatusPill from '@/components/workspace/WorkspaceStatusPill.vue';
import { WORKSPACE_THEME } from '@/constants/workspaceTheme';
import { getQualityIssuePage, getQualityTodos } from '@/api/quality';
import { useAuthStore } from '@/stores/auth';
import { useProjectStore } from '@/stores/project';
import type { QualityIssue, QualityIssueStatus, TodoItem } from '@/types';
import { usePageScrollHeight } from '@/utils/navLayout';
import { getQueryNumber, showToast, switchTab } from '@/utils/navigation';

type IssueFilter = 'ALL' | 'OVERDUE' | QualityIssueStatus;
type IssueSource = 'ALL' | 'WEEKLY' | 'HISTORICAL';

const authStore = useAuthStore();
const projectStore = useProjectStore();
const projectId = ref(0);
const todoMode = ref(false);
const activeFilter = ref<IssueFilter>('ALL');
const activeSource = ref<IssueSource>('ALL');
const keyword = ref('');
const submittedKeyword = ref('');
const issues = ref<QualityIssue[]>([]);
const todos = ref<TodoItem[]>([]);
const pageNo = ref(1);
const total = ref(0);
const loading = ref(false);
const loadingMore = ref(false);
const errorMessage = ref('');
const ready = ref(false);
const { scrollStyle } = usePageScrollHeight({ bottomRpx: 0, minHeight: 320 });
let requestSequence = 0;

const currentProject = computed(() => projectStore.state.projects.find((item) => item.id === projectId.value));
const hasMore = computed(() => !todoMode.value && issues.value.length < total.value);
const pageTitle = computed(() => todoMode.value ? '我的质量待办' : '整改闭环');
const statusFilters: Array<{ value: IssueFilter; label: string }> = [
  { value: 'ALL', label: '全部' },
  { value: 'PENDING', label: '待整改' },
  { value: 'RECHECK', label: '待复查' },
  { value: 'OVERDUE', label: '已逾期' },
  { value: 'CLOSED', label: '已关闭' }
];
const sourceLabels: Record<IssueSource, string> = {
  ALL: '全部问题', WEEKLY: '周检问题', HISTORICAL: '历史独立问题'
};

onLoad(async (query) => {
  if (!await authStore.ensureRootAccess('/pages/quality/index')) return;
  await projectStore.loadProjects();
  const resolved = getQueryNumber(query?.projectId, projectStore.state.currentProjectId || 0);
  if (!resolved || !await authStore.ensureProjectPermission('/pages/quality/index', resolved, 'quality.view')) return;
  projectId.value = resolved;
  todoMode.value = String(query?.mode || '').toLowerCase() === 'todo';
  const status = String(query?.status || '').toUpperCase() as IssueFilter;
  if (statusFilters.some((item) => item.value === status)) activeFilter.value = status;
  ready.value = true;
  await refresh();
});

onShow(async () => {
  if (ready.value) await refresh();
});

async function refresh() {
  if (!projectId.value || loading.value) return;
  const sequence = ++requestSequence;
  loading.value = true;
  errorMessage.value = '';
  try {
    if (todoMode.value) {
      const result = await getQualityTodos(projectId.value);
      if (sequence !== requestSequence) return;
      todos.value = result;
      total.value = result.length;
      return;
    }
    const result = await getQualityIssuePage(
      projectId.value,
      activeFilter.value,
      submittedKeyword.value,
      1,
      20,
      activeSource.value
    );
    if (sequence !== requestSequence) return;
    issues.value = result.records;
    pageNo.value = result.pageNo;
    total.value = result.total;
  } catch (error) {
    if (sequence === requestSequence) errorMessage.value = error instanceof Error ? error.message : '质量问题加载失败';
  } finally {
    if (sequence === requestSequence) loading.value = false;
  }
}

async function setStatus(value: IssueFilter) {
  if (value === activeFilter.value) return;
  activeFilter.value = value;
  issues.value = [];
  await refresh();
}

async function selectSource() {
  const choices: IssueSource[] = ['ALL', 'WEEKLY', 'HISTORICAL'];
  const tapIndex = await new Promise<number>((resolve) => {
    uni.showActionSheet({
      itemList: choices.map((item) => sourceLabels[item]),
      success: (result) => resolve(result.tapIndex),
      fail: () => resolve(-1)
    });
  });
  if (tapIndex < 0 || choices[tapIndex] === activeSource.value) return;
  activeSource.value = choices[tapIndex];
  issues.value = [];
  await refresh();
}

async function applySearch() {
  submittedKeyword.value = keyword.value.trim();
  issues.value = [];
  await refresh();
}

async function loadMore() {
  if (loading.value || loadingMore.value || !hasMore.value) return;
  const sequence = requestSequence;
  const nextPage = pageNo.value + 1;
  loadingMore.value = true;
  try {
    const result = await getQualityIssuePage(
      projectId.value,
      activeFilter.value,
      submittedKeyword.value,
      nextPage,
      20,
      activeSource.value
    );
    if (sequence !== requestSequence) return;
    const knownIds = new Set(issues.value.map((item) => item.id));
    issues.value = [...issues.value, ...result.records.filter((item) => !knownIds.has(item.id))];
    pageNo.value = result.pageNo;
    total.value = result.total;
  } catch (error) {
    showToast(error instanceof Error ? error.message : '更多质量问题加载失败');
  } finally {
    loadingMore.value = false;
  }
}

function openIssue(id: number) {
  uni.navigateTo({ url: `/pages/quality/issue-detail?id=${id}` });
}

function statusLabel(issue: QualityIssue) {
  if (issue.status === 'VOIDED') return '已作废';
  if (issue.overdue) return '已逾期';
  if (issue.status === 'PENDING') return '待整改';
  if (issue.status === 'RECHECK') return '待复查';
  return '已关闭';
}

function statusTone(issue: QualityIssue) {
  if (issue.status === 'VOIDED') return 'gray' as const;
  if (issue.overdue) return 'red' as const;
  if (issue.status === 'PENDING') return 'amber' as const;
  if (issue.status === 'RECHECK') return 'blue' as const;
  return 'green' as const;
}

function severityLabel(value: string) {
  return value === 'DANGER' ? '严重' : value === 'WARNING' ? '重要' : '一般';
}

function todoMark(todo: TodoItem) {
  return String(todo.taskType || todo.type).includes('RECHECK') ? '查' : '改';
}

function goBack() {
  if (getCurrentPages().length > 1) uni.navigateBack();
  else switchTab('/pages/quality/index');
}
</script>

<template>
  <view class="workspace-shell issue-page" :style="{ '--page-accent': WORKSPACE_THEME.accent, '--page-accent-deep': WORKSPACE_THEME.accentDeep, '--page-tint': WORKSPACE_THEME.tint, '--page-background': WORKSPACE_THEME.page }">
    <AppNavBar :title="pageTitle" @back="goBack" />
    <scroll-view class="workspace-scroll" scroll-y enable-flex lower-threshold="80" :style="scrollStyle" @scrolltolower="loadMore">
      <view class="issue-content">
        <view class="context-card"><text>{{ currentProject?.projectName || currentProject?.shortName || '当前施工区域' }}</text><text>{{ todoMode ? '仅显示当前账号需要处理的质量任务' : '周检问题与历史独立问题' }}</text></view>

        <template v-if="!todoMode">
          <scroll-view class="status-scroll" scroll-x enable-flex show-scrollbar="false">
            <view class="status-chips"><button v-for="item in statusFilters" :key="item.value" :class="{ active: activeFilter === item.value }" @tap="setStatus(item.value)">{{ item.label }}</button></view>
          </scroll-view>
          <view class="filter-toolbar">
            <view class="search-box"><text class="search-icon"></text><input v-model="keyword" class="search-input" confirm-type="search" placeholder="搜索问题、位置、负责人" placeholder-class="search-placeholder" @confirm="applySearch" /><button @tap="applySearch">搜索</button></view>
            <button class="source-button" @tap="selectSource"><text>来源</text><text>{{ sourceLabels[activeSource] }}</text><text class="down-arrow"></text></button>
          </view>
        </template>

        <view v-if="loading && !(todoMode ? todos.length : issues.length)" class="state-panel"><text class="state-title">正在加载{{ pageTitle }}</text></view>
        <view v-else-if="errorMessage" class="state-panel"><text class="state-title">{{ pageTitle }}加载失败</text><text class="state-desc">{{ errorMessage }}</text><button class="retry-button" @tap="refresh">重新加载</button></view>
        <view v-else class="issue-list">
          <template v-if="todoMode">
            <button v-for="todo in todos" :key="todo.todoKey || `${todo.type}-${todo.targetId}`" class="todo-row" @tap="openIssue(todo.targetId)">
              <text class="todo-mark" :class="{ recheck: todoMark(todo) === '查' }">{{ todoMark(todo) }}</text>
              <view><text>{{ todo.title }}</text><text>{{ todo.installLocation || '未设置位置' }}</text><text :class="{ overdue: todo.priority === 'danger' }">{{ todo.dueText }}</text></view><text class="row-arrow"></text>
            </button>
            <view v-if="!todos.length" class="empty-line">当前施工区域暂无质量待办</view>
          </template>
          <template v-else>
            <button v-for="issue in issues" :key="issue.id" class="issue-row" @tap="openIssue(issue.id)">
              <text class="issue-indicator" :class="statusTone(issue)"></text>
              <view class="issue-copy"><view><text>{{ issue.title }}</text><text class="severity" :class="issue.severity.toLowerCase()">{{ severityLabel(issue.severity) }}</text></view><text>{{ issue.location || '未设置位置' }} · {{ issue.assigneeName || '未指定负责人' }}</text><text class="due" :class="statusTone(issue)">{{ issue.dueText }}</text></view>
              <WorkspaceStatusPill :label="statusLabel(issue)" :tone="statusTone(issue)" /><text class="row-arrow"></text>
            </button>
            <view v-if="!issues.length" class="empty-line">{{ submittedKeyword ? '没有匹配当前搜索条件的质量问题' : '当前筛选条件下暂无质量问题' }}</view>
            <view v-else class="page-tail">{{ loadingMore ? '正在加载更多' : hasMore ? '继续上拉加载更多' : `共 ${total} 项` }}</view>
          </template>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<style scoped src="../../styles/workspace-page.css"></style>
<style scoped>
.issue-content { display: flex; min-height: 100%; flex-direction: column; gap: 15rpx; padding: 20rpx 24rpx calc(34rpx + env(safe-area-inset-bottom)); }
.context-card { padding: 17rpx 20rpx; border-radius: 14rpx; background: var(--page-tint); }
.context-card text { display: block; color: var(--workspace-text-secondary); font-size: 19rpx; }
.context-card text:first-child { overflow: hidden; color: var(--workspace-text); font-size: 23rpx; font-weight: 760; text-overflow: ellipsis; white-space: nowrap; }
.context-card text + text { margin-top: 5rpx; }
.status-scroll { width: 100%; white-space: nowrap; }
.status-chips { display: inline-flex; gap: 10rpx; padding: 2rpx; }
.status-chips button { min-width: 102rpx; min-height: 58rpx; margin: 0; padding: 0 18rpx; border: 1rpx solid var(--workspace-divider); border-radius: 999rpx; background: #fff; color: var(--workspace-text-secondary); font-size: 20rpx; font-weight: 680; }
.status-chips button.active { border-color: rgba(49,95,134,.2); background: var(--page-tint); color: var(--page-accent-deep); font-weight: 760; }
.status-chips button::after,.source-button::after,.issue-row::after,.todo-row::after { border: 0; }
.filter-toolbar { display: grid; grid-template-columns: minmax(0,1fr) 170rpx; gap: 10rpx; }
.filter-toolbar .search-box { min-width: 0; margin: 0; background: #fff; }
.filter-toolbar .search-box button { min-height: 52rpx; margin: 0; padding: 0 4rpx; border: 0; background: transparent; color: var(--page-accent-deep); font-size: 19rpx; font-weight: 750; }
.filter-toolbar .search-box button::after { border: 0; }
.source-button { display: flex; min-width: 0; min-height: 68rpx; align-items: center; justify-content: center; flex-direction: column; gap: 2rpx; margin: 0; padding: 5rpx 24rpx 5rpx 10rpx; border: 1rpx solid var(--workspace-divider); border-radius: 12rpx; background: #fff; color: var(--workspace-text); font-size: 18rpx; position: relative; }
.source-button text:nth-child(2) { overflow: hidden; max-width: 125rpx; color: var(--page-accent-deep); font-weight: 730; text-overflow: ellipsis; white-space: nowrap; }
.down-arrow { position: absolute; right: 12rpx; bottom: 16rpx; width: 9rpx; height: 9rpx; border-right: 2rpx solid #8b98a8; border-bottom: 2rpx solid #8b98a8; transform: rotate(45deg); }
.issue-list { overflow: hidden; border-radius: 16rpx; background: #fff; box-shadow: var(--workspace-shadow); }
.issue-row,.todo-row { box-sizing: border-box; display: flex; width: 100%; min-height: 116rpx; align-items: flex-start; gap: 13rpx; padding: 18rpx 18rpx; border-bottom: 1rpx solid var(--workspace-divider); background: #fff; text-align: left; }
.issue-copy,.todo-row > view { min-width: 0; flex: 1; }
.issue-copy > view { display: flex; min-width: 0; align-items: center; gap: 8rpx; }
.issue-copy > view > text:first-child,.todo-row > view > text:first-child { display: block; overflow: hidden; min-width: 0; color: var(--workspace-text); font-size: 23rpx; font-weight: 760; text-overflow: ellipsis; white-space: nowrap; }
.issue-copy > text,.todo-row > view > text { display: block; margin-top: 6rpx; color: var(--workspace-text-secondary); font-size: 19rpx; line-height: 1.35; }
.issue-indicator { width: 6rpx; height: 61rpx; flex-shrink: 0; margin-top: 2rpx; border-radius: 999rpx; background: var(--page-accent); }
.issue-indicator.amber { background: #d39439; }.issue-indicator.red { background: #c95b5b; }.issue-indicator.green { background: #2e8b72; }.issue-indicator.gray { background: #8794a2; }
.issue-row :deep(.status-pill) { flex-shrink: 0; margin-top: 2rpx; }
.severity { flex-shrink: 0; padding: 3rpx 8rpx; border-radius: 999rpx; background: #eef3f7; color: #647586; font-size: 17rpx; }
.severity.warning { background: #fff2dd; color: #9b621d; }.severity.danger { background: #fde8e8; color: #ac4646; }
.due.amber { color: #a96822; }.due.red,.todo-row > view > text.overdue { color: #b94f4f; }.due.green { color: #2e8069; }.due.blue { color: var(--page-accent-deep); }
.todo-mark { display: flex; width: 46rpx; height: 46rpx; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 12rpx; background: #fff1df; color: #a96822; font-size: 20rpx; font-weight: 800; }
.todo-mark.recheck { background: var(--page-tint); color: var(--page-accent-deep); }
.empty-line,.page-tail { padding: 44rpx 20rpx; color: var(--workspace-text-muted); font-size: 20rpx; text-align: center; }
.page-tail { padding: 22rpx 20rpx; }
@media (max-width: 360px) { .issue-content { padding-right: 18rpx; padding-left: 18rpx; } .filter-toolbar { grid-template-columns: minmax(0,1fr) 150rpx; } .source-button text:nth-child(2) { max-width: 106rpx; } .issue-row :deep(.status-pill) { display: none; } }
</style>
