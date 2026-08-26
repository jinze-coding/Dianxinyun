<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import AppTabBar from '@/components/AppTabBar.vue';
import WorkspaceAreaSheet from '@/components/workspace/WorkspaceAreaSheet.vue';
import WorkspaceAreaSwitcher from '@/components/workspace/WorkspaceAreaSwitcher.vue';
import { WORKSPACE_THEME } from '@/constants/workspaceTheme';
import { getTodoItems } from '@/api/todo';
import { getEdgeInspectionTasks, type EdgeInspectionTask } from '@/api/edgeInspection';
import { useProjectStore } from '@/stores/project';
import { useAuthStore } from '@/stores/auth';
import type { TodoItem } from '@/types';
import { isElectricInspectionTodo } from '@/utils/electricInspectionTodo';
import { usePageScrollHeight } from '@/utils/navLayout';
import { navigateTo, showToast } from '@/utils/navigation';
import { startElectricBoxScan } from '@/utils/electricBoxScan';

const ACCENT = WORKSPACE_THEME.accent;
const TINT = WORKSPACE_THEME.tint;
const projectStore = useProjectStore();
const authStore = useAuthStore();
const todos = ref<TodoItem[]>([]);
const edgeTasks = ref<EdgeInspectionTask[]>([]);
const loading = ref(false);
const errorMessage = ref('');
const edgeUnavailableReason = ref('');
const scanBusy = ref(false);
const areaSheetOpen = ref(false);
const { scrollStyle } = usePageScrollHeight({ bottomRpx: 124, minHeight: 320 });

const projects = computed(() => projectStore.state.projects);
const currentProject = computed(() => projects.value.find((item) => item.id === projectStore.state.currentProjectId));
const canViewElectric = computed(() => Boolean(currentProject.value)
  && authStore.hasProjectPermission(currentProject.value!.id, 'inspection.view', 'BOX_VIEW', 'INSPECTION_RECORD_VIEW', 'SUMMARY_VIEW'));
const canSubmitElectric = computed(() => Boolean(currentProject.value)
  && authStore.hasProjectPermission(currentProject.value!.id, 'inspection.submit', 'INSPECTION_DAILY_SUBMIT'));
const canRectifyElectric = computed(() => Boolean(currentProject.value)
  && authStore.hasProjectPermission(currentProject.value!.id, 'inspection.rectify'));
const canReviewElectric = computed(() => Boolean(currentProject.value)
  && authStore.hasProjectPermission(currentProject.value!.id, 'inspection.review'));
const canAccessElectric = computed(() => canViewElectric.value || canSubmitElectric.value
  || canRectifyElectric.value || canReviewElectric.value);
const canViewEdge = computed(() => Boolean(currentProject.value)
  && authStore.hasProjectPermission(currentProject.value!.id, 'EDGE_INSPECTION_VIEW'));
const canSubmitEdge = computed(() => Boolean(currentProject.value)
  && authStore.hasProjectPermission(currentProject.value!.id, 'EDGE_INSPECTION_SUBMIT'));
const canRectifyEdge = computed(() => Boolean(currentProject.value)
  && authStore.hasProjectPermission(currentProject.value!.id, 'EDGE_INSPECTION_RECTIFY'));
const canReviewEdge = computed(() => Boolean(currentProject.value)
  && authStore.hasProjectPermission(currentProject.value!.id, 'EDGE_INSPECTION_REVIEW'));
const canAccessEdge = computed(() => canViewEdge.value || canSubmitEdge.value || canRectifyEdge.value || canReviewEdge.value);

const electricTodos = computed(() => todos.value.filter((todo) => isElectricInspectionTodo(todo) && todo.type === 'INSPECTION'
  && (!todo.projectId || todo.projectId === currentProject.value?.id)));
const electricRectificationTodos = computed(() => todos.value.filter((todo) => isElectricInspectionTodo(todo) && todo.type === 'RECTIFICATION'
  && (!todo.projectId || todo.projectId === currentProject.value?.id)));
const electricRecheckTodos = computed(() => todos.value.filter((todo) => isElectricInspectionTodo(todo) && todo.type === 'RECHECK'
  && (!todo.projectId || todo.projectId === currentProject.value?.id)));
const pendingEdgeTasks = computed(() => edgeTasks.value.filter((task) => task.status === 'PENDING'));

function hideNativeTabBar() { uni.hideTabBar({ animation: false, fail: () => undefined }); }

onShow(async () => {
  hideNativeTabBar();
  if (!await authStore.ensureRootAccess('/pages/inspection/index')) return;
  await refresh();
});

async function refresh() {
  loading.value = true;
  errorMessage.value = '';
  edgeUnavailableReason.value = '';
  try {
    await projectStore.loadProjects();
    if (!currentProject.value) {
      todos.value = [];
      edgeTasks.value = [];
      return;
    }
    const todoPromise = canAccessElectric.value
      ? getTodoItems(currentProject.value.id)
      : Promise.resolve([] as TodoItem[]);
    const edgePromise = canAccessEdge.value
      ? getEdgeInspectionTasks({ projectId: currentProject.value.id, mine: true }).catch((error: unknown) => {
        edgeUnavailableReason.value = error instanceof Error ? error.message : '当前项目未开启临边巡检';
        return [] as EdgeInspectionTask[];
      })
      : Promise.resolve([] as EdgeInspectionTask[]);
    const [todoResult, edgeResult] = await Promise.all([todoPromise, edgePromise]);
    todos.value = todoResult;
    edgeTasks.value = edgeResult;
  } catch (error) {
    todos.value = [];
    edgeTasks.value = [];
    errorMessage.value = error instanceof Error ? error.message : '巡检首页加载失败';
  } finally { loading.value = false; }
}

async function selectProject(projectId: number) {
  projectStore.setCurrentProject(projectId);
  await refresh();
}

async function scanElectricBox() {
  if (scanBusy.value) return;
  if (!canViewElectric.value) { showToast('当前项目无电箱巡检查看权限'); return; }
  scanBusy.value = true;
  try { await startElectricBoxScan(currentProject.value?.id || 1); }
  catch (error) { showToast(error instanceof Error ? error.message : '扫码失败'); }
  finally { scanBusy.value = false; }
}

function startElectricInspection(todo: TodoItem) {
  if (!canSubmitElectric.value) { showToast('当前项目无电箱巡检提交权限'); return; }
  if (!todo.targetId) { showToast('未找到对应电箱'); return; }
  navigateTo(`/pages/inspection/form?boxId=${todo.targetId}`);
}

function startEdgeInspection(task: EdgeInspectionTask) {
  if (!canSubmitEdge.value) { showToast('当前项目无临边巡检提交权限'); return; }
  navigateTo(`/pages/inspection/edge-form?id=${task.id}`);
}

function timePart(value?: string) {
  if (!value) return '';
  const normalized = value.replace('T', ' ');
  return normalized.length >= 16 ? normalized.slice(11, 16) : normalized;
}

function edgeExecutionSlot(task: EdgeInspectionTask) {
  const start = timePart(task.startTime || task.availableTime);
  const end = timePart(task.dueTime);
  return start && end ? `${start}—${end}` : task.slotName || '单一执行时段';
}

function openEdgeTasks() {
  if (!canAccessEdge.value || edgeUnavailableReason.value) { showToast(edgeUnavailableReason.value || '当前项目无临边巡检权限'); return; }
  navigateTo(`/pages/inspection/edge-tasks?projectId=${currentProject.value?.id || ''}`);
}
</script>

<template>
  <view class="workspace-shell inspection-home" :style="{ '--page-accent': ACCENT, '--page-accent-deep': WORKSPACE_THEME.accentDeep, '--page-tint': TINT, '--page-background': WORKSPACE_THEME.page }">
    <AppNavBar title="巡检" :show-back="false" />
    <scroll-view class="workspace-scroll" scroll-y enable-flex :style="scrollStyle">
      <view class="workspace-content">
        <WorkspaceAreaSwitcher :project="currentProject" :projects="projects" :accent="ACCENT" :tint="TINT" :open="areaSheetOpen" @open="areaSheetOpen = true" />

        <view v-if="loading && !currentProject" class="state-panel"><text class="state-title">正在加载巡检任务</text></view>
        <view v-else-if="errorMessage" class="state-panel"><text class="state-title">巡检首页加载失败</text><text class="state-desc">{{ errorMessage }}</text><button class="retry-button" @tap="refresh">重新加载</button></view>

        <template v-else-if="currentProject">
          <view class="inspection-zone electric-zone">
            <view class="zone-head">
              <view class="zone-icon">电</view>
              <view class="zone-title"><text>电箱巡检</text><text>电箱台账 · 六项日检 · 专用记录与整改</text></view>
              <view class="zone-count"><text>{{ electricTodos.length }}</text><text>待巡检</text></view>
            </view>

            <button class="scan-entry" :disabled="scanBusy || !canViewElectric" @tap="scanElectricBox">
              <image src="/static/design-preview-icons/safety-scan.png" mode="aspectFit" />
              <view><text>{{ scanBusy ? '正在读取二维码' : '扫描电箱二维码' }}</text><text>仅用于电箱巡检、记录和公开月表</text></view><text>›</text>
            </button>

            <view class="task-list">
              <button v-for="todo in electricTodos.slice(0, 3)" :key="todo.targetId" class="task-row" :disabled="!canSubmitElectric" @tap="startElectricInspection(todo)">
                <view class="code-mark">{{ (todo.boxCode || '').slice(-3) }}</view>
                <view><text>{{ todo.boxCode || '电箱任务' }}</text><text>{{ todo.title }} · {{ todo.installLocation || currentProject.projectName }}</text></view>
                <text class="task-action">去巡检</text>
              </button>
              <view v-if="!electricTodos.length" class="empty-row">当前没有待巡检电箱</view>
            </view>

            <view class="zone-actions">
              <button :disabled="!canViewElectric" @tap="navigateTo(`/pages/inspection/records?projectId=${currentProject.id}`)">电箱记录</button>
              <button :disabled="!(canRectifyElectric || canReviewElectric)" @tap="navigateTo(`/pages/rectification/index?projectId=${currentProject.id}`)">电箱整改 {{ electricRectificationTodos.length + electricRecheckTodos.length }}</button>
            </view>
          </view>

          <view class="inspection-zone edge-zone" :class="{ unavailable: !canAccessEdge || edgeUnavailableReason }">
            <view class="zone-head">
              <view class="zone-icon">边</view>
              <view class="zone-title"><text>临边巡检</text><text>8类系统固定检查表 · 独立任务与闭环</text></view>
              <view class="zone-count"><text>{{ pendingEdgeTasks.length }}</text><text>待巡检</text></view>
            </view>

            <view v-if="!canAccessEdge || edgeUnavailableReason" class="edge-disabled">{{ edgeUnavailableReason || '当前账号无临边巡检权限' }}</view>
            <view v-else class="task-list">
              <button v-for="task in pendingEdgeTasks.slice(0, 3)" :key="task.id" class="task-row" :disabled="!canSubmitEdge" @tap="startEdgeInspection(task)">
                <view class="code-mark">{{ (task.pointCode || '').slice(-3) }}</view>
                <view><text>{{ task.pointName }} · {{ task.pointTypeName || task.categoryName || '临边点位' }}</text><text>{{ [task.buildingName || task.building, task.floorName || task.floor, task.locationDesc].filter(Boolean).join(' · ') || '位置待补充' }} · {{ edgeExecutionSlot(task) }}</text></view>
                <text class="task-action">{{ task.overdue ? '去补检' : '去巡检' }}</text>
              </button>
              <view v-if="!pendingEdgeTasks.length" class="empty-row">当前没有待执行的临边任务</view>
            </view>

            <view class="zone-actions">
              <button :disabled="!canAccessEdge || Boolean(edgeUnavailableReason)" @tap="openEdgeTasks">临边任务与记录</button>
              <button :disabled="!(canRectifyEdge || canReviewEdge) || Boolean(edgeUnavailableReason)" @tap="navigateTo(`/pages/rectification/edge-list?projectId=${currentProject.id}`)">临边整改闭环</button>
            </view>
          </view>
        </template>
      </view>
    </scroll-view>
    <WorkspaceAreaSheet :open="areaSheetOpen" :project="currentProject" :projects="projects" :accent="ACCENT" :tint="TINT" @close="areaSheetOpen = false" @select="selectProject" />
    <AppTabBar v-if="!areaSheetOpen" active="inspection" />
  </view>
</template>

<style scoped src="../../styles/workspace-page.css"></style>
<style scoped>
.inspection-zone{margin-top:18rpx;padding:22rpx;border:1rpx solid #dce5ec;border-radius:20rpx;background:#fff;box-shadow:var(--inspection-shadow)}.electric-zone{border-top:7rpx solid #315f86}.edge-zone{border-top:7rpx solid #a56b2d}.inspection-zone.unavailable{opacity:.75}.zone-head{display:flex;align-items:center;gap:15rpx}.zone-icon{display:flex;width:58rpx;height:58rpx;align-items:center;justify-content:center;flex-shrink:0;border-radius:15rpx;background:#eaf2f7;color:#315f86;font-size:23rpx;font-weight:850}.edge-zone .zone-icon{background:#fff4e5;color:#9a611e}.zone-title{min-width:0;flex:1}.zone-title text{display:block}.zone-title text:first-child{color:#25364a;font-size:27rpx;font-weight:850}.zone-title text:last-child{margin-top:4rpx;color:#8896a5;font-size:18rpx}.zone-count{min-width:72rpx;text-align:center}.zone-count text{display:block}.zone-count text:first-child{color:#315f86;font-size:32rpx;font-weight:900}.edge-zone .zone-count text:first-child{color:#9a611e}.zone-count text:last-child{color:#8c98a4;font-size:17rpx}.scan-entry{display:flex;width:100%;min-height:92rpx;align-items:center;gap:14rpx;margin:18rpx 0 0;padding:15rpx 18rpx;border:1rpx solid #d3e2ec;border-radius:15rpx;background:#f4f9fc;color:#315f86;text-align:left}.scan-entry::after,.task-row::after,.zone-actions button::after{border:0}.scan-entry image{width:44rpx;height:44rpx}.scan-entry>view{min-width:0;flex:1}.scan-entry text{display:block}.scan-entry>view text:first-child{font-size:22rpx;font-weight:780}.scan-entry>view text:last-child{margin-top:4rpx;color:#7b8fa0;font-size:17rpx}.scan-entry>text{font-size:30rpx}.task-list{margin-top:15rpx;border-top:1rpx solid #edf1f4}.task-row{display:flex;width:100%;min-height:94rpx;align-items:center;gap:13rpx;margin:0;padding:14rpx 0;border-bottom:1rpx solid #edf1f4;background:transparent;color:inherit;text-align:left}.code-mark{display:flex;width:50rpx;height:50rpx;align-items:center;justify-content:center;flex-shrink:0;border-radius:12rpx;background:#edf3f7;color:#315f86;font-size:18rpx;font-weight:800}.edge-zone .code-mark{background:#fff4e5;color:#9a611e}.task-row>view:nth-child(2){min-width:0;flex:1}.task-row>view:nth-child(2) text{display:block}.task-row>view:nth-child(2) text:first-child{color:#35485b;font-size:21rpx;font-weight:760}.task-row>view:nth-child(2) text:last-child{margin-top:4rpx;color:#8794a1;font-size:17rpx;line-height:1.45}.task-action{flex-shrink:0;padding:6rpx 10rpx;border-radius:999rpx;background:#eaf2f7;color:#315f86;font-size:17rpx}.edge-zone .task-action{background:#fff4e5;color:#9a611e}.empty-row,.edge-disabled{padding:28rpx 12rpx;color:#929eaa;font-size:20rpx;text-align:center}.edge-disabled{margin-top:16rpx;border-radius:14rpx;background:#f4f5f6}.zone-actions{display:grid;grid-template-columns:1fr 1fr;gap:12rpx;margin-top:17rpx}.zone-actions button{height:62rpx;margin:0;border:1rpx solid #dce5ec;border-radius:13rpx;background:#f8fafb;color:#435a70;font-size:19rpx;line-height:62rpx}.edge-zone .zone-actions button{border-color:#ead8c0;background:#fffaf3;color:#865a25}.zone-actions button[disabled]{opacity:.48}
</style>
