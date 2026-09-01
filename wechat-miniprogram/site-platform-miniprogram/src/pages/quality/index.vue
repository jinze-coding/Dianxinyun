<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import AppTabBar from '@/components/AppTabBar.vue';
import WorkspaceAreaSheet from '@/components/workspace/WorkspaceAreaSheet.vue';
import WorkspaceAreaSwitcher from '@/components/workspace/WorkspaceAreaSwitcher.vue';
import WorkspaceMetricStrip, { type WorkspaceMetric } from '@/components/workspace/WorkspaceMetricStrip.vue';
import WorkspaceStatusPill from '@/components/workspace/WorkspaceStatusPill.vue';
import { WORKSPACE_THEME } from '@/constants/workspaceTheme';
import {
  getQualitySummary,
  getQualityTodos,
  getQualityWeeklyInspectionPage,
  getQualityWeeklySummary
} from '@/api/quality';
import { useProjectStore } from '@/stores/project';
import { useAuthStore } from '@/stores/auth';
import type { QualitySummary, QualityWeeklyInspection, QualityWeeklySummary, TodoItem } from '@/types';
import { usePageScrollHeight } from '@/utils/navLayout';
import { showToast } from '@/utils/navigation';

const ACCENT = WORKSPACE_THEME.accent;
const TINT = WORKSPACE_THEME.tint;
const projectStore = useProjectStore();
const authStore = useAuthStore();
const summary = ref<QualitySummary>();
const weeklySummary = ref<QualityWeeklySummary>();
const recentInspections = ref<QualityWeeklyInspection[]>([]);
const qualityTodos = ref<TodoItem[]>([]);
const loading = ref(false);
const errorMessage = ref('');
const recentError = ref('');
const todosError = ref('');
const areaSheetOpen = ref(false);
const { scrollStyle } = usePageScrollHeight({ bottomRpx: 124, minHeight: 320 });
let requestSequence = 0;

const projects = computed(() => projectStore.state.projects);
const currentProject = computed(() => projects.value.find((item) => item.id === projectStore.state.currentProjectId));
const canManage = computed(() => Boolean(currentProject.value)
  && authStore.hasProjectPermission(currentProject.value!.id, 'quality.manage'));
const openIssueCount = computed(() => (summary.value?.pendingCount || 0) + (summary.value?.recheckCount || 0));
const metrics = computed<WorkspaceMetric[]>(() => [
  { key: 'PENDING', label: '待整改', value: summary.value?.pendingCount || 0, tone: 'amber' },
  { key: 'RECHECK', label: '待复查', value: summary.value?.recheckCount || 0 },
  { key: 'OVERDUE', label: '已逾期', value: summary.value?.overdueCount || 0, tone: 'red' }
]);
const currentInspection = computed(() => {
  const value = weeklySummary.value;
  if (!value?.hasInspection || !value.inspectionId) return undefined;
  return recentInspections.value.find((item) => item.id === value.inspectionId);
});
const weeklyActionTitle = computed(() => {
  if (!weeklySummary.value?.hasInspection) return canManage.value ? '创建本周周检' : '本周周检尚未提交';
  return weeklySummary.value.status === 'DRAFT' ? '继续本周周检' : '查看本周周检';
});
const weeklyActionSubtitle = computed(() => {
  const value = weeklySummary.value;
  if (!value?.hasInspection) return canManage.value ? '建立共享草稿，整理完成后整批提交' : '当前账号可在提交后查看本周周检';
  if (value.status === 'DRAFT') {
    const editor = currentInspection.value?.lastEditedByName || currentInspection.value?.createdByName;
    return `共享草稿 · 已录入 ${value.draftItemCount || 0} 个问题${editor ? ` · ${editor} 最近编辑` : ''}`;
  }
  return `${value.submittedIssueCount || 0} 个问题 · ${(value.pendingCount || 0) + (value.recheckCount || 0)} 项未闭环`;
});
const weeklyActionDisabled = computed(() => !weeklySummary.value?.hasInspection && !canManage.value);

function hideNativeTabBar() {
  uni.hideTabBar({ animation: false, fail: () => undefined });
}

onShow(async () => {
  hideNativeTabBar();
  if (!await authStore.ensureRootAccess('/pages/quality/index')) return;
  await refresh();
});

async function refresh() {
  const sequence = ++requestSequence;
  loading.value = true;
  errorMessage.value = '';
  recentError.value = '';
  todosError.value = '';
  try {
    await projectStore.loadProjects();
    if (sequence !== requestSequence) return;
    if (projectStore.state.errorMessage) throw new Error(projectStore.state.errorMessage);
    let projectId = projectStore.state.currentProjectId;
    if (!projectId) throw new Error('当前账号暂无可访问的施工区域');
    if (!authStore.hasProjectPermission(projectId, 'quality.view')) {
      const authorized = projects.value.find((project) => authStore.hasProjectPermission(project.id, 'quality.view'));
      if (!authorized) throw new Error('当前账号暂无质量周检查看权限');
      projectStore.setCurrentProject(authorized.id);
      projectId = authorized.id;
    }
    summary.value = undefined;
    weeklySummary.value = undefined;
    recentInspections.value = [];
    qualityTodos.value = [];
    const [summaryResult, weeklyResult, recentResult, todoResult] = await Promise.allSettled([
      getQualitySummary(projectId),
      getQualityWeeklySummary(projectId),
      getQualityWeeklyInspectionPage(projectId, 1, 2),
      getQualityTodos(projectId)
    ]);
    if (sequence !== requestSequence || projectStore.state.currentProjectId !== projectId) return;
    if (summaryResult.status === 'fulfilled') summary.value = summaryResult.value;
    if (weeklyResult.status === 'fulfilled') weeklySummary.value = weeklyResult.value;
    if (!summary.value || !weeklySummary.value) {
      const failure = summaryResult.status === 'rejected' ? summaryResult.reason : weeklyResult.status === 'rejected' ? weeklyResult.reason : undefined;
      errorMessage.value = failure instanceof Error ? failure.message : '质量首页数据加载失败';
    }
    if (recentResult.status === 'fulfilled') recentInspections.value = recentResult.value.records;
    else recentError.value = recentResult.reason instanceof Error ? recentResult.reason.message : '最近周检加载失败';
    if (todoResult.status === 'fulfilled') qualityTodos.value = todoResult.value;
    else todosError.value = todoResult.reason instanceof Error ? todoResult.reason.message : '质量待办加载失败';
  } catch (error) {
    if (sequence === requestSequence) errorMessage.value = error instanceof Error ? error.message : '质量首页加载失败';
  } finally {
    if (sequence === requestSequence) loading.value = false;
  }
}

async function selectProject(projectId: number) {
  if (projectId === projectStore.state.currentProjectId) return;
  projectStore.setCurrentProject(projectId);
  await refresh();
}

function openWeeklyAction() {
  if (!currentProject.value || weeklyActionDisabled.value) return;
  const current = weeklySummary.value;
  if (current?.status === 'SUBMITTED' && current.inspectionId) {
    uni.navigateTo({ url: `/pages/quality/weekly-detail?id=${current.inspectionId}` });
    return;
  }
  if (!canManage.value) {
    showToast('当前账号无质量周检发起权限');
    return;
  }
  uni.navigateTo({ url: `/pages/quality/weekly-edit?projectId=${currentProject.value.id}&weekStart=${current?.weekStart || currentWeekStart()}` });
}

function openSelectedWeek(event: unknown) {
  if (!canManage.value || !currentProject.value) return;
  const value = String((event as { detail?: { value?: string } }).detail?.value || '');
  if (!value) return;
  const date = new Date(`${value}T00:00:00`);
  const day = date.getDay();
  date.setDate(date.getDate() - (day === 0 ? 6 : day - 1));
  uni.navigateTo({ url: `/pages/quality/weekly-edit?projectId=${currentProject.value.id}&weekStart=${localDate(date)}` });
}

function currentWeekStart() {
  const date = new Date();
  const day = date.getDay();
  date.setDate(date.getDate() - (day === 0 ? 6 : day - 1));
  return localDate(date);
}

function localDate(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function currentDateText() {
  return localDate(new Date());
}

function openIssues(status = 'ALL', mode = '') {
  if (!currentProject.value) return;
  const query = [`projectId=${currentProject.value.id}`, status !== 'ALL' ? `status=${status}` : '', mode ? `mode=${mode}` : '']
    .filter(Boolean).join('&');
  uni.navigateTo({ url: `/pages/quality/issues?${query}` });
}

function selectMetric(metric: WorkspaceMetric) {
  openIssues(metric.key || 'ALL');
}

function openWeeklyList() {
  if (currentProject.value) uni.navigateTo({ url: `/pages/quality/weekly-list?projectId=${currentProject.value.id}` });
}

function openWeeklyInspection(item: QualityWeeklyInspection) {
  const path = item.status === 'DRAFT' && canManage.value ? 'weekly-edit' : 'weekly-detail';
  const query = path === 'weekly-edit' ? `projectId=${item.projectId}&weekStart=${item.weekStart}` : `id=${item.id}`;
  uni.navigateTo({ url: `/pages/quality/${path}?${query}` });
}

function openDocuments() {
  if (currentProject.value) uni.navigateTo({ url: `/pages/quality/documents?projectId=${currentProject.value.id}` });
}

function weeklyStatusLabel(item: QualityWeeklyInspection) {
  return item.status === 'DRAFT' ? '共享草稿' : item.lateSubmission ? '已提交 · 补录' : '已提交';
}
</script>

<template>
  <view class="workspace-shell quality-home" :style="{ '--page-accent': ACCENT, '--page-accent-deep': WORKSPACE_THEME.accentDeep, '--page-tint': TINT, '--page-background': WORKSPACE_THEME.page }">
    <AppNavBar title="质量周检" :show-back="false" />
    <scroll-view class="workspace-scroll" scroll-y enable-flex :style="scrollStyle">
      <view class="workspace-content">
        <WorkspaceAreaSwitcher :project="currentProject" :projects="projects" :accent="ACCENT" :tint="TINT" :open="areaSheetOpen" @open="areaSheetOpen = true" />
        <view v-if="loading && !weeklySummary" class="state-panel"><text class="state-title">正在加载质量数据</text></view>
        <view v-else-if="errorMessage" class="state-panel"><text class="state-title">质量首页加载失败</text><text class="state-desc">{{ errorMessage }}</text><button class="retry-button" @tap="refresh">重新加载</button></view>
        <template v-else-if="summary && weeklySummary && currentProject">
          <button class="weekly-action" :class="{ disabled: weeklyActionDisabled }" :disabled="weeklyActionDisabled" @tap="openWeeklyAction">
            <view class="weekly-action-icon"><image src="/static/design-preview-icons/quality-inspect.png" mode="aspectFit" /></view>
            <view class="weekly-action-copy"><text>{{ weeklyActionTitle }}</text><text>{{ weeklyActionSubtitle }}</text></view>
            <text v-if="!weeklyActionDisabled" class="row-arrow light"></text>
          </button>
          <picker v-if="canManage" class="past-week-entry" mode="date" :end="currentDateText()" @change="openSelectedWeek"><view>补录往期周检 <text class="row-arrow"></text></view></picker>

          <button v-if="qualityTodos.length" class="todo-entry" @tap="openIssues('ALL', 'todo')">
            <view><text>我的质量待办</text><text>{{ qualityTodos.length }} 项需要处理</text></view>
            <view><text>{{ qualityTodos.length }}</text><text class="row-arrow"></text></view>
          </button>
          <button v-else-if="todosError" class="inline-error" @tap="refresh"><text>质量待办加载失败</text><text>点击重试</text></button>

          <view class="section-block closure-section">
            <view class="compact-head"><view><text>整改闭环</text><text>{{ openIssueCount }} 项未闭环</text></view><button @tap="openIssues()">查看全部 <text class="row-arrow"></text></button></view>
            <WorkspaceMetricStrip :metrics="metrics" :accent="ACCENT" :motion-key="`${currentProject.id}-${openIssueCount}`" interactive @select="selectMetric" />
          </view>

          <view class="section-block recent-section">
            <view class="compact-head"><view><text>最近周检</text><text>共享草稿与已提交记录</text></view><button @tap="openWeeklyList">查看全部 <text class="row-arrow"></text></button></view>
            <view v-if="recentError" class="section-error"><text>{{ recentError }}</text><button @tap="refresh">重新加载</button></view>
            <view v-else class="plain-list">
              <button v-for="item in recentInspections" :key="item.id" class="plain-row" @tap="openWeeklyInspection(item)">
                <view class="plain-copy"><text class="plain-title">{{ item.weekStart }} 至 {{ item.weekEnd }}</text><text class="plain-meta">{{ item.status === 'DRAFT' ? `共享整理中 · ${item.lastEditedByName || item.createdByName || '项目成员'}` : `${item.submittedIssueCount} 个问题 · ${item.submittedByName || '已提交'}` }}</text></view>
                <WorkspaceStatusPill :label="weeklyStatusLabel(item)" :tone="item.status === 'DRAFT' ? 'amber' : 'green'" /><text class="row-arrow"></text>
              </button>
              <view v-if="!recentInspections.length" class="empty-line">暂无周检记录</view>
            </view>
          </view>

          <button class="document-entry" @tap="openDocuments"><view class="document-mark">文</view><view><text>质量资料</text><text>查看当前施工区域有效质量资料</text></view><text class="row-arrow"></text></button>
        </template>
      </view>
    </scroll-view>
    <WorkspaceAreaSheet :open="areaSheetOpen" :project="currentProject" :projects="projects" :accent="ACCENT" :tint="TINT" @close="areaSheetOpen = false" @select="selectProject" />
    <AppTabBar v-if="!areaSheetOpen" active="quality" />
  </view>
</template>

<style scoped src="../../styles/workspace-page.css"></style>
<style scoped>
.weekly-action { box-sizing: border-box; display: flex; width: 100%; min-height: 112rpx; align-items: center; gap: 16rpx; margin: 0; padding: 20rpx 22rpx; border: 0; border-radius: 18rpx; background: var(--page-accent-deep); box-shadow: 0 12rpx 28rpx rgba(49,95,134,.2); text-align: left; }
.weekly-action::after,.compact-head button::after,.todo-entry::after,.inline-error::after,.document-entry::after { border: 0; }
.weekly-action-icon { display: flex; width: 60rpx; height: 60rpx; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 14rpx; background: rgba(255,255,255,.16); }
.weekly-action-icon image { width: 39rpx; height: 39rpx; filter: brightness(0) invert(1); }
.weekly-action-copy { min-width: 0; flex: 1; }
.weekly-action-copy text { display: block; color: rgba(255,255,255,.76); font-size: 20rpx; line-height: 1.4; }
.weekly-action-copy text:first-child { overflow: hidden; color: #fff; font-size: 27rpx; font-weight: 800; text-overflow: ellipsis; white-space: nowrap; }
.weekly-action-copy text + text { margin-top: 5rpx; }
.weekly-action.disabled { background: #dbe2e7; box-shadow: none; }
.weekly-action.disabled .weekly-action-icon { background: rgba(255,255,255,.55); }
.weekly-action.disabled image { filter: grayscale(1); opacity: .5; }
.weekly-action.disabled .weekly-action-copy text:first-child { color: #617181; }
.weekly-action.disabled .weekly-action-copy text:last-child { color: #7b8996; }
.row-arrow.light { border-color: rgba(255,255,255,.82); }
.past-week-entry { margin-top: -8rpx; }
.past-week-entry view { display: flex; min-height: 44rpx; align-items: center; justify-content: center; gap: 8rpx; color: var(--page-accent-deep); font-size: 20rpx; font-weight: 700; }
.past-week-entry .row-arrow,.compact-head .row-arrow { display: inline-block; margin-left: 5rpx; }
.todo-entry,.document-entry { box-sizing: border-box; display: flex; width: 100%; min-height: 76rpx; align-items: center; gap: 14rpx; margin: 0; padding: 14rpx 18rpx; border: 1rpx solid var(--workspace-divider); border-radius: 14rpx; background: #fff; text-align: left; }
.todo-entry > view:first-child,.document-entry > view:nth-child(2) { min-width: 0; flex: 1; }
.todo-entry text,.document-entry text { display: block; color: var(--workspace-text-muted); font-size: 18rpx; }
.todo-entry text:first-child,.document-entry text:first-child { color: var(--workspace-text); font-size: 22rpx; font-weight: 760; }
.todo-entry > view:last-of-type { display: flex; align-items: center; gap: 10rpx; color: #b87827; }
.todo-entry > view:last-of-type > text:first-child { color: #b87827; font-size: 24rpx; font-weight: 800; }
.inline-error { display: flex; width: 100%; min-height: 64rpx; align-items: center; justify-content: space-between; margin: 0; padding: 0 18rpx; border: 1rpx solid #f0d9d9; border-radius: 13rpx; background: #fff8f8; color: #ad5050; font-size: 19rpx; }
.compact-head { display: flex; min-height: 78rpx; align-items: center; justify-content: space-between; gap: 18rpx; padding: 14rpx 20rpx; border-bottom: 1rpx solid var(--workspace-divider); }
.compact-head > view { min-width: 0; }
.compact-head > view text { display: block; color: var(--workspace-text-secondary); font-size: 18rpx; }
.compact-head > view text:first-child { color: var(--workspace-text); font-size: 24rpx; font-weight: 780; }
.compact-head > view text + text { margin-top: 4rpx; }
.compact-head button { display: flex; min-height: 50rpx; align-items: center; flex-shrink: 0; margin: 0; padding: 0; border: 0; background: transparent; color: var(--page-accent-deep); font-size: 20rpx; font-weight: 720; }
.closure-section :deep(.metric-strip) { border-radius: 0; box-shadow: none; }
.closure-section :deep(.metric-cell) { padding-top: 20rpx; padding-bottom: 18rpx; }
.recent-section .plain-list { padding-top: 0; }
.recent-section .plain-row { min-height: 96rpx; }
.recent-section :deep(.status-pill) { flex-shrink: 0; }
.empty-line { padding: 34rpx 0; color: var(--workspace-text-muted); font-size: 20rpx; text-align: center; }
.section-error { display: flex; min-height: 84rpx; align-items: center; justify-content: space-between; gap: 16rpx; padding: 14rpx 20rpx; color: #ad5050; font-size: 19rpx; }
.section-error button { flex-shrink: 0; margin: 0; padding: 8rpx 12rpx; border: 0; background: transparent; color: var(--page-accent-deep); font-size: 19rpx; }
.section-error button::after { border: 0; }
.document-mark { display: flex; width: 48rpx; height: 48rpx; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 12rpx; background: var(--page-tint); color: var(--page-accent-deep); font-size: 22rpx; font-weight: 800; }
@media (max-width: 360px) {
  .weekly-action { padding-right: 18rpx; padding-left: 18rpx; }
  .weekly-action-copy text:first-child { font-size: 25rpx; }
  .compact-head { padding-right: 16rpx; padding-left: 16rpx; }
}
</style>
