<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import AppTabBar from '@/components/AppTabBar.vue';
import WorkspaceAreaSwitcher from '@/components/workspace/WorkspaceAreaSwitcher.vue';
import WorkspaceMetricStrip, { type WorkspaceMetric } from '@/components/workspace/WorkspaceMetricStrip.vue';
import { WORKSPACE_THEME } from '@/constants/workspaceTheme';
import { getTodoItems } from '@/api/todo';
import { useProjectStore } from '@/stores/project';
import { useAuthStore } from '@/stores/auth';
import type { TodoItem } from '@/types';
import { usePageScrollHeight } from '@/utils/navLayout';
import { navigateTo, showToast } from '@/utils/navigation';
import { startElectricBoxScan } from '@/utils/electricBoxScan';

const ACCENT = WORKSPACE_THEME.accent;
const TINT = WORKSPACE_THEME.tint;
const projectStore = useProjectStore();
const authStore = useAuthStore();
const todos = ref<TodoItem[]>([]);
const loading = ref(false);
const errorMessage = ref('');
const scanBusy = ref(false);
const { scrollStyle } = usePageScrollHeight({ bottomRpx: 124, minHeight: 320 });

const projects = computed(() => projectStore.state.projects);
const currentProject = computed(() => projects.value.find((item) => item.id === projectStore.state.currentProjectId));
const canView = computed(() => Boolean(currentProject.value)
  && authStore.hasProjectPermission(
    currentProject.value!.id,
    'inspection.view',
    'BOX_VIEW',
    'INSPECTION_RECORD_VIEW',
    'SUMMARY_VIEW'
  ));
const canSubmit = computed(() => Boolean(currentProject.value)
  && authStore.hasProjectPermission(
    currentProject.value!.id,
    'inspection.submit',
    'INSPECTION_DAILY_SUBMIT'
  ));
const inspectionTodos = computed(() => todos.value.filter((todo) => todo.type === 'INSPECTION'
  && (!todo.projectId || todo.projectId === currentProject.value?.id)));
const checkedCount = computed(() => currentProject.value?.todayInspectionCount || 0);
const requiredCount = computed(() => checkedCount.value + inspectionTodos.value.length);
const metrics = computed<WorkspaceMetric[]>(() => [
  { label: '今日应检', value: requiredCount.value, tone: 'amber' },
  { label: '今日已检', value: checkedCount.value, tone: 'green' },
  { label: '今日未检', value: inspectionTodos.value.length, tone: inspectionTodos.value.length ? 'red' : 'green' }
]);

function hideNativeTabBar() {
  uni.hideTabBar({ animation: false, fail: () => undefined });
}

onShow(async () => {
  hideNativeTabBar();
  if (!await authStore.ensureRootAccess('/pages/inspection/index')) return;
  await refresh();
});

async function refresh() {
  loading.value = true;
  errorMessage.value = '';
  try {
    await projectStore.loadProjects();
    if (currentProject.value && !canView.value) {
      todos.value = [];
      errorMessage.value = '当前项目无巡检查看权限，可切换到其他施工区域';
      return;
    }
    todos.value = currentProject.value ? await getTodoItems(currentProject.value.id) : [];
  } catch (error) {
    todos.value = [];
    errorMessage.value = error instanceof Error ? error.message : '巡检任务加载失败';
  } finally {
    loading.value = false;
  }
}

async function selectProject(projectId: number) {
  projectStore.setCurrentProject(projectId);
  await refresh();
}

async function scan() {
  if (scanBusy.value) return;
  if (!canView.value) {
    showToast('当前项目无巡检查看权限');
    return;
  }
  scanBusy.value = true;
  try {
    await startElectricBoxScan(currentProject.value?.id || 1);
  } catch (error) {
    showToast(error instanceof Error ? error.message : '扫码失败');
  } finally {
    scanBusy.value = false;
  }
}

function startInspection(todo: TodoItem) {
  if (!canSubmit.value) {
    showToast('当前项目无巡检提交权限');
    return;
  }
  if (!todo.targetId) {
    showToast('未找到对应电箱');
    return;
  }
  navigateTo(`/pages/inspection/form?boxId=${todo.targetId}`);
}
</script>

<template>
  <view class="workspace-shell inspection-home" :style="{ '--page-accent': ACCENT, '--page-accent-deep': WORKSPACE_THEME.accentDeep, '--page-tint': TINT, '--page-background': WORKSPACE_THEME.page }">
    <AppNavBar title="巡检" :show-back="false" />
    <scroll-view class="workspace-scroll" scroll-y enable-flex :style="scrollStyle">
      <view class="workspace-content">
        <WorkspaceAreaSwitcher :project="currentProject" :projects="projects" :accent="ACCENT" :tint="TINT" @select="selectProject" />

        <view v-if="loading && !currentProject" class="state-panel">
          <text class="state-title">正在加载巡检任务</text>
        </view>
        <view v-else-if="errorMessage" class="state-panel">
          <text class="state-title">巡检任务加载失败</text>
          <text class="state-desc">{{ errorMessage }}</text>
          <button class="retry-button" @tap="refresh">重新加载</button>
        </view>

        <template v-else-if="currentProject">
          <WorkspaceMetricStrip :metrics="metrics" :accent="ACCENT" :motion-key="`${currentProject.id}-${inspectionTodos.length}`" />

          <button class="scan-primary" :disabled="scanBusy || !canView" @tap="scan">
            <view class="scan-icon-wrap">
              <image src="/static/design-preview-icons/safety-scan.png" mode="aspectFit" />
            </view>
            <view class="scan-copy">
              <text>{{ scanBusy ? '正在读取二维码' : '扫描电箱二维码' }}</text>
              <text>开发者工具选择本地图片，真机调起微信扫码</text>
            </view>
            <text class="scan-arrow"></text>
          </button>

          <view class="section-block task-section">
            <view class="section-head">
              <view><text class="section-title">今日待巡检</text><text class="section-subtitle">{{ inspectionTodos.length }} 台电箱待完成</text></view>
              <text class="section-note">{{ currentProject.shortName || currentProject.projectName }}</text>
            </view>
            <view class="plain-list">
              <button v-for="todo in inspectionTodos" :key="todo.targetId" class="plain-row task-row" :disabled="!canSubmit" @tap="startInspection(todo)">
                <view class="task-code-box">{{ (todo.boxCode || '').slice(-3) }}</view>
                <view class="plain-copy">
                  <text class="task-code">{{ todo.boxCode }}</text>
                  <text class="plain-title">{{ todo.title }}</text>
                  <text class="plain-meta">{{ todo.installLocation || currentProject.projectName }}</text>
                </view>
                <text v-if="canSubmit" class="task-action">去巡检</text>
                <text class="row-arrow"></text>
              </button>
              <view v-if="!inspectionTodos.length" class="empty-state">
                <text class="empty-mark">✓</text>
                <text class="empty-title">今日巡检已完成</text>
                <text class="empty-desc">当前区域没有待巡检电箱</text>
              </view>
            </view>
          </view>

          <button v-if="canView" class="records-entry pressable" @tap="navigateTo(`/pages/inspection/records?projectId=${currentProject.id}`)">
            <view class="records-icon"><image src="/static/design-preview-icons/safety-records.png" mode="aspectFit" /></view>
            <view class="records-copy"><text>查看巡检记录</text><text>按月份、电箱和结果查询</text></view>
            <text class="row-arrow"></text>
          </button>
        </template>
      </view>
    </scroll-view>
    <AppTabBar active="inspection" />
  </view>
</template>

<style scoped src="../../styles/workspace-page.css"></style>
<style scoped>
.scan-primary { position: relative; display: flex; width: 100%; min-height: 116rpx; align-items: center; gap: 18rpx; overflow: hidden; padding: 19rpx 22rpx; border: 1rpx solid var(--inspection-border); border-radius: 18rpx; background: var(--inspection-soft-strong); box-shadow: var(--inspection-shadow); color: var(--inspection-primary-deep); text-align: left; transition: background-color 100ms ease, box-shadow 100ms ease, transform 100ms ease; }
.scan-primary::after,.records-entry::after { border: 0; }
.scan-primary:active { background: #dcebf7; box-shadow: 0 5rpx 15rpx rgba(49,95,134,.1); transform: scale(.985); }
.scan-primary[disabled] { opacity: .72; }
.scan-icon-wrap { position: relative; display: flex; width: 66rpx; height: 66rpx; align-items: center; justify-content: center; flex-shrink: 0; overflow: hidden; border-radius: 15rpx; background: rgba(255,255,255,.72); }
.scan-icon-wrap image { width: 40rpx; height: 40rpx; filter: hue-rotate(170deg) saturate(.55) brightness(.92); }
.scan-copy { min-width: 0; flex: 1; }
.scan-copy text { display: block; }
.scan-copy text:first-child { font-size: 28rpx; font-weight: 750; }
.scan-copy text:last-child { margin-top: 6rpx; color: #70869b; font-size: 21rpx; }
.scan-arrow { width: 12rpx; height: 12rpx; border-top: 2rpx solid #6d8ba7; border-right: 2rpx solid #6d8ba7; transform: rotate(45deg); }
.section-head > view { display: flex; min-width: 0; flex-direction: column; gap: 4rpx; }
.section-subtitle { color: #98A2B3; font-size: 19rpx; }
.task-row { min-height: 112rpx; }
.task-code-box { display: flex; width: 58rpx; height: 58rpx; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 13rpx; background: var(--inspection-soft); color: var(--inspection-primary-deep); font-size: 20rpx; font-weight: 800; }
.task-code { display: block; margin-bottom: 4rpx; color: #8B95A3; font-size: 18rpx; font-weight: 700; }
.task-action { flex-shrink: 0; padding: 7rpx 12rpx; border: 1rpx solid #cddfec; border-radius: 999rpx; background: var(--inspection-soft); color: var(--inspection-primary-deep); font-size: 20rpx; font-weight: 700; }
.empty-state { display: flex; min-height: 210rpx; align-items: center; justify-content: center; flex-direction: column; padding: 30rpx; }
.empty-mark { display: flex; width: 54rpx; height: 54rpx; align-items: center; justify-content: center; border-radius: 50%; background: #E8F6EE; color: #2F9A66; font-size: 28rpx; font-weight: 800; }
.empty-title { margin-top: 16rpx; color: #344054; font-size: 24rpx; font-weight: 750; }
.empty-desc { margin-top: 6rpx; color: #98A2B3; font-size: 20rpx; }
.records-entry { display: flex; width: 100%; min-height: 94rpx; align-items: center; gap: 15rpx; margin: 0; padding: 16rpx 20rpx; border: 1rpx solid var(--inspection-divider); border-radius: 18rpx; background: #fff; box-shadow: var(--inspection-shadow); color: var(--inspection-text); text-align: left; }
.records-icon { display: flex; width: 52rpx; height: 52rpx; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 14rpx; background: var(--page-tint); }
.records-icon image {
  width: 32rpx;
  height: 32rpx;
  filter: hue-rotate(170deg) saturate(.55) brightness(.92);
}
.records-copy { min-width: 0; flex: 1; }
.records-copy text { display: block; }
.records-copy text:first-child { font-size: 23rpx; font-weight: 750; }
.records-copy text:last-child { margin-top: 4rpx; color: #98a2b3; font-size: 19rpx; }
</style>
