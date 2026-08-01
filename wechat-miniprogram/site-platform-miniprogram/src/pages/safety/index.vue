<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import AppTabBar from '@/components/AppTabBar.vue';
import WorkspaceAreaSwitcher from '@/components/workspace/WorkspaceAreaSwitcher.vue';
import WorkspaceMetricStrip, { type WorkspaceMetric } from '@/components/workspace/WorkspaceMetricStrip.vue';
import WorkspaceSegmentControl from '@/components/workspace/WorkspaceSegmentControl.vue';
import { getTodoItems } from '@/api/todo';
import { useAuthStore } from '@/stores/auth';
import { useProjectStore } from '@/stores/project';
import type { TodoItem } from '@/types';
import { usePageScrollHeight } from '@/utils/navLayout';
import { navigateTo, showToast } from '@/utils/navigation';
import { startElectricBoxScan } from '@/utils/electricBoxScan';

type SafetyFilter = 'ALL' | 'REVIEW' | 'RECTIFICATION';

const ACCENT = '#C07A32';
const TINT = '#FFF3E6';
const projectStore = useProjectStore();
const authStore = useAuthStore();
const todos = ref<TodoItem[]>([]);
const loading = ref(false);
const errorMessage = ref('');
const activeFilter = ref<SafetyFilter>('ALL');
const scanAnimating = ref(false);
const { scrollStyle } = usePageScrollHeight({ bottomRpx: 124, minHeight: 320 });

const projects = computed(() => projectStore.state.projects);
const currentProject = computed(() => projects.value.find((item) => item.id === projectStore.state.currentProjectId));
const currentRole = computed(() => authStore.state.user?.projectRoles?.find((item) => item.projectId === currentProject.value?.id));
const isPlatformAdmin = computed(() => authStore.state.user?.roles?.includes('PLATFORM_ADMIN'));
const canReview = computed(() => isPlatformAdmin.value || currentRole.value?.permissionCodes?.includes('INSPECTION_REVIEW'));
const roleLabel = computed(() => {
  if (isPlatformAdmin.value) return '平台管理员';
  const names = (currentRole.value?.projectRoles || []).map((role) => role.roleName).filter(Boolean);
  return names.join('、') || '项目成员';
});
const projectTodos = computed(() => todos.value.filter((todo) =>
  todo.businessType !== 'QUALITY_ISSUE'
  && (!todo.projectId || todo.projectId === currentProject.value?.id)));
const filteredTodos = computed(() => projectTodos.value.filter((todo) => {
  if (!canReview.value && ['REVIEW', 'RECHECK'].includes(todo.type)) return false;
  if (activeFilter.value === 'ALL') return true;
  if (activeFilter.value === 'REVIEW') return todo.type === 'REVIEW' || todo.type === 'RECHECK';
  return todo.type === 'RECTIFICATION';
}));
const metrics = computed<WorkspaceMetric[]>(() => [
  { label: '电箱', value: currentProject.value?.electricBoxTotal || 0, tone: 'amber' },
  { label: '今日巡检', value: `${currentProject.value?.todayInspectionCount || 0}/${currentProject.value?.electricBoxTotal || 0}`, tone: 'green' },
  { label: '待复核', value: currentProject.value?.pendingReviewCount || 0, tone: 'amber' },
  { label: '待整改', value: currentProject.value?.pendingRectificationCount || 0, tone: 'red' }
]);
const tabs = computed(() => [
  { value: 'ALL', label: canReview.value ? '今日任务' : '我的任务', badge: filteredCount('ALL') },
  ...(canReview.value ? [{ value: 'REVIEW', label: '待复核', badge: filteredCount('REVIEW') }] : []),
  { value: 'RECTIFICATION', label: '整改', badge: filteredCount('RECTIFICATION') }
]);

function hideNativeTabBar() { uni.hideTabBar({ animation: false, fail: () => undefined }); }
onShow(async () => { hideNativeTabBar(); await refresh(); });

async function refresh() {
  loading.value = true;
  errorMessage.value = '';
  try {
    await Promise.all([projectStore.loadProjects(), authStore.loadUser()]);
    todos.value = await getTodoItems(currentProject.value?.id);
  } catch (error) {
    todos.value = [];
    errorMessage.value = error instanceof Error ? error.message : '安全任务加载失败';
  } finally { loading.value = false; }
}

async function selectProject(projectId: number) { projectStore.setCurrentProject(projectId); activeFilter.value = 'ALL'; await refresh(); }
function setFilter(value: string) { activeFilter.value = value as SafetyFilter; }
function filteredCount(filter: SafetyFilter) {
  const source = projectTodos.value.filter((todo) => canReview.value || !['REVIEW', 'RECHECK'].includes(todo.type));
  if (filter === 'ALL') return source.length;
  if (filter === 'REVIEW') return source.filter((todo) => todo.type === 'REVIEW' || todo.type === 'RECHECK').length;
  return source.filter((todo) => todo.type === 'RECTIFICATION').length;
}

async function scan() {
  if (scanAnimating.value) return;
  scanAnimating.value = true;
  try {
    await new Promise((resolve) => setTimeout(resolve, 180));
    await startElectricBoxScan(currentProject.value?.id || 1);
  } catch (error) {
    showToast(error instanceof Error ? error.message : '扫码失败');
  } finally {
    scanAnimating.value = false;
  }
}

function openTodo(todo: TodoItem) {
  if (!todo.targetId) { showToast('任务详情暂不可查看'); return; }
  if (todo.type === 'INSPECTION') { navigateTo(`/pages/inspection/form?boxId=${todo.targetId}`); return; }
  if (todo.type === 'REVIEW') { navigateTo(`/pages/inspection/review-detail?id=${todo.targetId}`); return; }
  navigateTo(`/pages/rectification/detail?id=${todo.targetId}`);
}

function actionLabel(type: TodoItem['type']) { return type === 'INSPECTION' ? '立即巡检' : type === 'REVIEW' ? '去复核' : type === 'RECTIFICATION' ? '去整改' : '去复查'; }
function actionTone(type: TodoItem['type']) { return type === 'RECTIFICATION' ? 'red' : type === 'REVIEW' || type === 'RECHECK' ? 'amber' : 'blue'; }
function taskCode(todo: TodoItem) { return todo.boxCode || `任务${todo.id}`; }
</script>

<template>
  <view class="workspace-shell safety-page" :style="{ '--page-accent': ACCENT, '--page-accent-deep': '#9D6024', '--page-tint': TINT, '--page-background': '#F8F5F1' }">
    <AppNavBar title="安全管理" :show-back="false" />
    <scroll-view class="workspace-scroll" scroll-y enable-flex :style="scrollStyle">
      <view class="workspace-content">
        <WorkspaceAreaSwitcher :project="currentProject" :projects="projects" :accent="ACCENT" :tint="TINT" @select="selectProject" />
        <view v-if="loading && !currentProject" class="state-panel"><text class="state-title">正在加载安全任务</text></view>
        <view v-else-if="errorMessage" class="state-panel"><text class="state-title">安全任务加载失败</text><text class="state-desc">{{ errorMessage }}</text><button class="retry-button" @tap="refresh">重新加载</button></view>
        <template v-else-if="currentProject">
          <WorkspaceMetricStrip :metrics="metrics" :accent="ACCENT" :motion-key="currentProject.id" />
          <button class="scan-primary" :class="{ scanning: scanAnimating }" @tap="scan"><view class="scan-icon-wrap"><image src="/static/design-preview-icons/safety-scan-light.png" mode="aspectFit" /><text class="scan-line"></text></view><view class="scan-copy"><text>扫描现场二维码</text><text>日检、抽查和电箱信息从这里开始</text></view><text class="scan-arrow"></text></button>
          <view class="section-block task-section">
            <view class="section-head"><text class="section-title">现场任务</text><text class="section-note">{{ roleLabel }}视图</text></view>
            <WorkspaceSegmentControl :model-value="activeFilter" :options="tabs" :accent="ACCENT" :tint="TINT" @update:model-value="setFilter" />
            <view class="plain-list">
              <button v-for="todo in filteredTodos" :key="`${todo.type}-${todo.targetId}`" class="plain-row task-row" @tap="openTodo(todo)"><view class="task-code-box" :class="actionTone(todo.type)">{{ taskCode(todo).slice(-3) }}</view><view class="plain-copy"><text class="task-code">{{ taskCode(todo) }}</text><text class="plain-title">{{ todo.title }}</text><text class="plain-meta">{{ todo.installLocation || currentProject.projectName }} · {{ todo.dueText }}</text></view><text class="task-action" :class="actionTone(todo.type)">{{ actionLabel(todo.type) }}</text><text class="row-arrow"></text></button>
              <view v-if="!filteredTodos.length" class="empty-line">当前分类暂无任务</view>
            </view>
          </view>
          <view class="shortcut-row">
            <button @tap="navigateTo(`/pages/electric-box/index?projectId=${currentProject.id}`)"><view><image src="/static/design-preview-icons/safety-ledger.png" mode="aspectFit" /></view><text>电箱台账</text></button>
            <button @tap="navigateTo(`/pages/inspection/records?projectId=${currentProject.id}`)"><view><image src="/static/design-preview-icons/safety-records.png" mode="aspectFit" /></view><text>检查记录</text></button>
            <button @tap="navigateTo(`/pages/summary/index?projectId=${currentProject.id}`)"><view><image src="/static/design-preview-icons/safety-summary.png" mode="aspectFit" /></view><text>巡检汇总</text></button>
          </view>
        </template>
      </view>
    </scroll-view>
    <AppTabBar active="safety" />
  </view>
</template>

<style scoped src="../../styles/workspace-page.css"></style>
<style scoped>
.scan-primary { position: relative; display: flex; width: 100%; min-height: 116rpx; align-items: center; gap: 18rpx; overflow: hidden; padding: 19rpx 22rpx; border-radius: 16rpx; background: var(--page-accent-deep); box-shadow: 0 12rpx 28rpx rgba(157,96,36,.19); color: #fff; text-align: left; transition: box-shadow 100ms ease, transform 100ms ease; }
.scan-primary::after, .shortcut-row button::after { border: 0; }.scan-primary:active, .scan-primary.scanning { box-shadow: 0 5rpx 15rpx rgba(157,96,36,.15); transform: scale(.985); }
.scan-icon-wrap { position: relative; display: flex; width: 66rpx; height: 66rpx; align-items: center; justify-content: center; flex-shrink: 0; overflow: hidden; border-radius: 15rpx; background: rgba(255,255,255,.13); }.scan-icon-wrap image { width: 40rpx; height: 40rpx; }.scan-line { position: absolute; right: 10rpx; left: 10rpx; height: 2rpx; border-radius: 999rpx; background: #fff3df; opacity: 0; }.scanning .scan-line { animation: scan-once 240ms ease both; }
.scan-copy { min-width: 0; flex: 1; }.scan-copy text { display: block; }.scan-copy text:first-child { font-size: 28rpx; font-weight: 750; }.scan-copy text:last-child { margin-top: 6rpx; color: rgba(255,255,255,.72); font-size: 21rpx; }.scan-arrow { width: 12rpx; height: 12rpx; border-top: 2rpx solid rgba(255,255,255,.76); border-right: 2rpx solid rgba(255,255,255,.76); transform: rotate(45deg); }
.task-section :deep(.segment-control) { margin: 18rpx 20rpx 0; }.task-row { min-height: 116rpx; }.task-code-box { display: flex; width: 58rpx; height: 58rpx; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 13rpx; background: #eef4f8; color: #51728d; font-size: 20rpx; font-weight: 800; }.task-code-box.amber { background: #fff1df; color: #b16b22; }.task-code-box.red { background: #fdebea; color: #bd5252; }.task-code { display: block; margin-bottom: 4rpx; color: #8390a1; font-size: 18rpx; font-weight: 700; }.task-action { flex-shrink: 0; padding: 7rpx 11rpx; border-radius: 999rpx; background: #eef4f8; color: #51728d; font-size: 20rpx; font-weight: 700; }.task-action.amber { background: #fff1df; color: #b16b22; }.task-action.red { background: #fdebea; color: #bd5252; }
.empty-line { padding: 44rpx 0; color: #98a2b3; font-size: 22rpx; text-align: center; }
.shortcut-row { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); overflow: hidden; border-radius: 16rpx; background: #fff; box-shadow: 0 9rpx 30rpx rgba(43,56,72,.06); }.shortcut-row button { position: relative; display: flex; min-height: 106rpx; align-items: center; justify-content: center; flex-direction: column; gap: 8rpx; background: #fff; color: #48586d; font-size: 22rpx; }.shortcut-row button + button::before { position: absolute; top: 22rpx; bottom: 22rpx; left: 0; width: 1rpx; background: #edf0f3; content: ""; }.shortcut-row button view { display: flex; width: 44rpx; height: 44rpx; align-items: center; justify-content: center; border-radius: 12rpx; background: var(--page-tint); }.shortcut-row image { width: 30rpx; height: 30rpx; }
@keyframes scan-once { 0% { top: 11rpx; opacity: 0; } 24%,80% { opacity: .95; } 100% { top: 53rpx; opacity: 0; } }
</style>
