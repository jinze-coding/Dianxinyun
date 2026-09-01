<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad, onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import EdgeInspectionFlow from '@/components/inspection/EdgeInspectionFlow.vue';
import {
  getEdgeInspectionTasks,
  getEdgeInspectionWorkspaceSummary,
  type EdgeInspectionTask,
  type EdgeInspectionWorkspaceSummary
} from '@/api/edgeInspection';
import { useAuthStore } from '@/stores/auth';
import { useProjectStore } from '@/stores/project';
import {
  isEdgeTaskBeforeWindow,
  isEdgeTaskOverdue,
  selectEdgeTasksForView,
  shanghaiDateKey,
  shiftDateKey,
  type EdgeTaskView
} from '@/utils/edgeInspectionView';
import { navigateTo, switchTab } from '@/utils/navigation';
import { usePageScrollHeight } from '@/utils/navLayout';

const authStore = useAuthStore();
const projectStore = useProjectStore();
const tasks = ref<EdgeInspectionTask[]>([]);
const summary = ref<EdgeInspectionWorkspaceSummary>();
const loading = ref(false);
const errorMessage = ref('');
const activeTab = ref<EdgeTaskView>('TODAY');
const requestedProjectId = ref(0);
const { scrollStyle } = usePageScrollHeight({ bottomRpx: 30, minHeight: 260 });
let requestSequence = 0;

const taskStatusLabels: Record<string, string> = {
  PENDING: '待巡检', OVERDUE: '逾期未检', OVERDUE_PENDING: '逾期未检', OVERDUE_MISSED: '逾期未检',
  COMPLETED: '按时完成', LATE_COMPLETED: '逾期补检', OVERDUE_COMPLETED: '逾期补检',
  RECTIFICATION_PENDING: '整改中', RECTIFYING: '整改中', REVIEW_PENDING: '整改中',
  CLOSED: '已闭环', CANCELLED: '已取消'
};

onLoad((options) => {
  requestedProjectId.value = Number(options?.projectId || 0);
  const tab = String(options?.tab || '').toUpperCase();
  if (['TODAY', 'OVERDUE', 'RECORDS'].includes(tab)) activeTab.value = tab as EdgeTaskView;
});

onShow(async () => {
  if (!await authStore.ensureRootAccess('/pages/inspection/index')) return;
  await projectStore.loadProjects();
  await Promise.all([loadSummary(), loadTasks()]);
});

const tabOptions = computed(() => [
  { value: 'TODAY' as const, label: '今日巡检', count: summary.value?.myTodayDueCount },
  { value: 'OVERDUE' as const, label: '逾期补检', count: summary.value?.myOverdueCount },
  { value: 'RECORDS' as const, label: '巡检记录', count: undefined }
]);

const visibleTasks = computed(() => selectEdgeTasksForView(tasks.value, activeTab.value));
const todayProgress = computed(() => {
  const value = summary.value;
  return value ? {
    total: value.myTodayDueCount,
    pending: value.myTodayPendingCount,
    submitted: value.myTodaySubmittedCount,
    cancelled: value.myTodayCancelledCount
  } : undefined;
});

async function loadSummary() {
  const projectId = requestedProjectId.value || projectStore.state.currentProjectId || 0;
  if (!projectId) { summary.value = undefined; return; }
  try { summary.value = await getEdgeInspectionWorkspaceSummary(projectId); }
  catch { summary.value = undefined; }
}

async function loadTasks() {
  const sequence = ++requestSequence;
  const view = activeTab.value;
  const projectId = requestedProjectId.value || projectStore.state.currentProjectId || undefined;
  const today = shanghaiDateKey();
  loading.value = true;
  errorMessage.value = '';
  try {
    const params = view === 'TODAY'
      ? { projectId, mine: true, startDate: today, endDate: today }
      : view === 'OVERDUE'
        ? { projectId, mine: true, status: 'PENDING', endDate: today }
        : { projectId, mine: true, startDate: shiftDateKey(today, -29), endDate: today };
    const result = await getEdgeInspectionTasks(params);
    if (sequence !== requestSequence || view !== activeTab.value) return;
    tasks.value = result;
  } catch (error) {
    if (sequence !== requestSequence || view !== activeTab.value) return;
    tasks.value = [];
    errorMessage.value = error instanceof Error ? error.message : '临边巡检任务加载失败';
  } finally {
    if (sequence === requestSequence && view === activeTab.value) loading.value = false;
  }
}

async function changeTab(value: EdgeTaskView) {
  if (activeTab.value === value) return;
  activeTab.value = value;
  tasks.value = [];
  await loadTasks();
}

function statusLabel(task: EdgeInspectionTask) {
  if (task.status === 'PENDING' && isEdgeTaskOverdue(task)) return '逾期未检';
  if (task.status === 'PENDING' && isEdgeTaskBeforeWindow(task)) return '待开始';
  const displayStatus = task.displayStatus?.trim().toUpperCase();
  if (displayStatus && taskStatusLabels[displayStatus]) return taskStatusLabels[displayStatus];
  if (task.status === 'COMPLETED') return task.lateSubmission ? '逾期补检' : '按时完成';
  const status = task.status?.trim().toUpperCase();
  return taskStatusLabels[status] || task.displayStatus || task.status || '待巡检';
}

function statusTone(task: EdgeInspectionTask) {
  if (task.status === 'PENDING' && isEdgeTaskOverdue(task)) return 'danger';
  if (task.status === 'RECTIFICATION_PENDING') return 'warning';
  if (['CLOSED', 'COMPLETED'].includes(task.status)) return 'success';
  if (task.status === 'CANCELLED') return 'muted';
  return 'pending';
}

function pointType(task: EdgeInspectionTask) { return task.pointTypeName || task.categoryName || '临边点位'; }
function position(task: EdgeInspectionTask) {
  return [task.buildingName || task.building, task.floorName || task.floor, task.locationDesc]
    .filter(Boolean).join(' · ') || '未填写位置说明';
}
function timePart(value?: string) {
  if (!value) return '';
  const normalized = value.replace('T', ' ');
  return normalized.length >= 16 ? normalized.slice(11, 16) : normalized;
}
function executionSlot(task: EdgeInspectionTask) {
  const start = timePart(task.startTime || task.availableTime);
  const end = timePart(task.dueTime);
  return start && end ? `${start}—${end}` : task.slotName || '单一执行时段';
}
function actionLabel(task: EdgeInspectionTask) {
  if (task.status !== 'PENDING') return '查看记录';
  if (isEdgeTaskOverdue(task)) return '去补检';
  return isEdgeTaskBeforeWindow(task) ? '未到时段' : task.canExecute ? '去巡检' : '查看任务';
}
function emptyText() {
  if (activeTab.value === 'TODAY') {
    const pointCount = summary.value?.enabledPointCount || 0;
    return summary.value?.myTodayDueCount
      ? '今日任务已处理完毕'
      : `项目有 ${pointCount} 个启用点位，但当前账号今日暂无本人巡检任务`;
  }
  if (activeTab.value === 'OVERDUE') return '当前没有需要补检的逾期任务';
  return '最近30天没有已提交或取消的巡检记录';
}
function openTask(task: EdgeInspectionTask) { navigateTo(`/pages/inspection/edge-form?id=${task.id}`); }
function goBack() {
  if (getCurrentPages().length > 1) uni.navigateBack();
  else switchTab('/pages/inspection/index');
}
</script>

<template>
  <view class="page-shell">
    <AppNavBar title="我的临边巡检" @back="goBack" />
    <scroll-view class="page-scroll" scroll-y enable-flex :style="scrollStyle">
      <view class="content">
        <view class="page-tabs">
          <button v-for="tab in tabOptions" :key="tab.value" :class="{ active: activeTab === tab.value }" @tap="changeTab(tab.value)">
            <text>{{ tab.label }}</text><text v-if="tab.count" class="tab-count">{{ tab.count > 99 ? '99+' : tab.count }}</text>
          </button>
        </view>
        <EdgeInspectionFlow :enabled-point-count="summary?.enabledPointCount" />

        <view v-if="activeTab === 'TODAY' && todayProgress" class="today-progress">
          <view><text>{{ todayProgress.total }}</text><text>今日任务</text></view>
          <view><text>{{ todayProgress.pending }}</text><text>待巡检</text></view>
          <view><text>{{ todayProgress.submitted }}</text><text>已提交</text></view>
          <view><text>{{ todayProgress.cancelled }}</text><text>已取消</text></view>
        </view>
        <text v-else-if="activeTab === 'RECORDS'" class="view-note">仅显示最近30天巡检记录；整改和复查请从“临边整改闭环”处理。</text>
        <text v-else-if="activeTab === 'OVERDUE'" class="view-note">超过截止时间仍未提交的任务保留补检入口，并永久记录迟交。</text>

        <view v-if="loading" class="state">正在加载临边任务…</view>
        <view v-else-if="errorMessage" class="state error"><text>{{ errorMessage }}</text><button @tap="loadTasks">重新加载</button></view>
        <view v-else class="task-list">
          <button v-for="task in visibleTasks" :key="task.id" class="task-card" @tap="openTask(task)">
            <text class="point-name">{{ task.pointName }}</text>
            <view class="tag-row">
              <text class="point-type">{{ pointType(task) }}</text>
              <text class="point-code">{{ task.pointCode }}</text>
              <text class="status" :class="`tone-${statusTone(task)}`">{{ statusLabel(task) }}</text>
            </view>
            <view class="info-row"><text>位置</text><text>{{ position(task) }}</text></view>
            <view class="info-row"><text>日期</text><text>{{ task.occurrenceDate }} · {{ executionSlot(task) }}</text></view>
            <view class="info-row"><text>巡检人</text><text>{{ task.assigneeName || '待分配' }}</text></view>
            <view class="card-foot"><text>{{ task.status === 'PENDING' ? `截止 ${timePart(task.dueTime)}` : task.submittedTime ? `提交 ${task.submittedTime.replace('T',' ').slice(0,16)}` : statusLabel(task) }}</text><text>{{ actionLabel(task) }} ›</text></view>
          </button>
          <view v-if="!visibleTasks.length" class="state empty-state">{{ emptyText() }}</view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<style scoped>
.page-shell{min-height:100vh;overflow:hidden;background:#f4f7fa;color:#26384a}.page-scroll{height:calc(100vh - 120rpx)}.content{box-sizing:border-box;width:100%;padding:24rpx}.page-tabs{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10rpx;padding:7rpx;border-radius:16rpx;background:#e9eef2}.page-tabs button{display:flex;min-width:0;height:60rpx;align-items:center;justify-content:center;gap:6rpx;margin:0;border-radius:12rpx;background:transparent;color:#6b7a88;font-size:20rpx;font-weight:750;line-height:60rpx}.page-tabs button::after,.task-card::after,.state button::after{border:0}.page-tabs button.active{background:#fff;color:#315f86;box-shadow:0 4rpx 14rpx rgba(49,95,134,.1)}.tab-count{min-width:28rpx;padding:0 6rpx;border-radius:999rpx;background:#c64d48;color:#fff;font-size:16rpx;line-height:28rpx}.today-progress{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8rpx;margin-top:14rpx}.today-progress view{min-width:0;padding:13rpx 4rpx;border:1rpx solid #e1e8ed;border-radius:12rpx;background:#fff;text-align:center}.today-progress text{display:block}.today-progress text:first-child{color:#315f86;font-size:26rpx;font-weight:900}.today-progress text:last-child{margin-top:3rpx;color:#7d8b98;font-size:16rpx;white-space:nowrap}.view-note{display:block;margin-top:14rpx;padding:14rpx 16rpx;border-radius:12rpx;background:#eef3f6;color:#657789;font-size:18rpx;line-height:1.5}.task-list{display:flex;min-width:0;flex-direction:column;gap:16rpx;margin-top:17rpx}.task-card{box-sizing:border-box;display:block;width:100%;min-width:0;margin:0;padding:22rpx;border:1rpx solid #e2e9ee;border-radius:18rpx;background:#fff;box-shadow:0 5rpx 18rpx rgba(38,56,74,.06);color:inherit;text-align:left}.point-name{display:block;width:100%;color:#26384a;font-size:25rpx;font-weight:820;line-height:1.4;overflow-wrap:anywhere;word-break:break-word}.tag-row{display:flex;align-items:center;flex-wrap:wrap;gap:8rpx;margin-top:12rpx}.point-type,.point-code,.status{display:inline-flex;max-width:100%;padding:5rpx 10rpx;border-radius:999rpx;font-size:18rpx;line-height:1.25;overflow-wrap:anywhere}.point-type{background:#eaf4fb;color:#315f86}.point-code{background:#f1f3f5;color:#687786}.status{margin-left:auto;background:#eef3f6;color:#5f7080}.status.tone-danger{background:#fff0ef;color:#c43f3b}.status.tone-warning{background:#fff3dd;color:#986419}.status.tone-success{background:#e9f6ef;color:#2f8f61}.status.tone-muted{background:#f1f2f3;color:#89939d}.info-row{display:flex;align-items:flex-start;gap:14rpx;margin-top:11rpx;font-size:20rpx;line-height:1.55}.info-row text:first-child{width:72rpx;flex-shrink:0;color:#98a2ad}.info-row text:last-child{min-width:0;flex:1;color:#5b6d7e;overflow-wrap:anywhere;word-break:break-word}.card-foot{display:flex;align-items:center;justify-content:space-between;gap:14rpx;margin-top:16rpx;padding-top:14rpx;border-top:1rpx solid #eef1f4;color:#7e8c99;font-size:19rpx}.card-foot text:last-child{flex-shrink:0;color:#956126;font-weight:750}.state{padding:90rpx 20rpx;color:#98a2b3;font-size:22rpx;line-height:1.6;text-align:center}.state.error{color:#b54747}.state button{width:180rpx;height:64rpx;margin-top:20rpx;border-radius:14rpx;background:#315f86;color:#fff;font-size:21rpx;line-height:64rpx}.empty-state{margin-top:0;padding:100rpx 28rpx;border:1rpx dashed #dce4e9;border-radius:18rpx;background:#fff}
</style>
