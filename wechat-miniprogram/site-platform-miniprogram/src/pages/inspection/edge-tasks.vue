<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad, onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import { getEdgeInspectionTasks, type EdgeInspectionTask } from '@/api/edgeInspection';
import { useAuthStore } from '@/stores/auth';
import { useProjectStore } from '@/stores/project';
import { navigateTo, switchTab } from '@/utils/navigation';
import { usePageScrollHeight } from '@/utils/navLayout';

const authStore = useAuthStore();
const projectStore = useProjectStore();
const tasks = ref<EdgeInspectionTask[]>([]);
const loading = ref(false);
const errorMessage = ref('');
const activeFilter = ref('ACTIVE');
const requestedProjectId = ref(0);
const { scrollStyle } = usePageScrollHeight({ bottomRpx: 30, minHeight: 260 });
const taskStatusLabels: Record<string, string> = {
  PENDING: '待巡检',
  OVERDUE: '逾期未检',
  OVERDUE_PENDING: '逾期未检',
  OVERDUE_MISSED: '逾期未检',
  COMPLETED: '按时完成',
  LATE_COMPLETED: '逾期补检',
  OVERDUE_COMPLETED: '逾期补检',
  RECTIFICATION_PENDING: '整改中',
  RECTIFYING: '整改中',
  REVIEW_PENDING: '整改中',
  CLOSED: '已闭环',
  CANCELLED: '已取消'
};

onLoad((options) => { requestedProjectId.value = Number(options?.projectId || 0); });
onShow(async () => {
  if (!await authStore.ensureRootAccess('/pages/inspection/index')) return;
  await loadTasks();
});

const filteredTasks = computed(() => tasks.value.filter((task) => {
  if (activeFilter.value === 'ALL') return true;
  if (activeFilter.value === 'ACTIVE') return ['PENDING', 'RECTIFICATION_PENDING'].includes(task.status);
  if (activeFilter.value === 'COMPLETED') return ['COMPLETED', 'CLOSED'].includes(task.status);
  return task.status === activeFilter.value;
}));

async function loadTasks() {
  loading.value = true;
  errorMessage.value = '';
  try {
    await projectStore.loadProjects();
    const projectId = requestedProjectId.value || projectStore.state.currentProjectId || undefined;
    tasks.value = await getEdgeInspectionTasks({ projectId, mine: true });
  } catch (error) {
    tasks.value = [];
    errorMessage.value = error instanceof Error ? error.message : '临边巡检任务加载失败';
  } finally {
    loading.value = false;
  }
}

function statusLabel(task: EdgeInspectionTask) {
  const displayStatus = task.displayStatus?.trim().toUpperCase();
  if (displayStatus && taskStatusLabels[displayStatus]) return taskStatusLabels[displayStatus];
  if (task.status === 'COMPLETED') return task.lateSubmission ? '逾期补检' : '按时完成';
  const status = task.status?.trim().toUpperCase();
  if (status && taskStatusLabels[status]) return taskStatusLabels[status];
  if (task.overdue) return '逾期未检';
  return task.displayStatus || task.status || '待巡检';
}

function pointType(task: EdgeInspectionTask) {
  return task.pointTypeName || task.categoryName || '临边点位';
}

function position(task: EdgeInspectionTask) {
  return [task.buildingName || task.building, task.floorName || task.floor, task.locationDesc].filter(Boolean).join(' · ') || '未填写位置说明';
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

function openTask(task: EdgeInspectionTask) {
  navigateTo(`/pages/inspection/edge-form?id=${task.id}`);
}

function openRectifications() {
  const projectId = requestedProjectId.value || projectStore.state.currentProjectId || '';
  navigateTo(`/pages/rectification/edge-list?projectId=${projectId}`);
}

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
        <view class="toolbar">
          <view class="filter-row">
            <button v-for="filter in [{ code: 'ACTIVE', name: '待处理' }, { code: 'COMPLETED', name: '已完成' }, { code: 'ALL', name: '全部' }]"
              :key="filter.code" class="filter" :class="{ active: activeFilter === filter.code }" @tap="activeFilter = filter.code">{{ filter.name }}</button>
          </view>
          <button class="rectification-link" @tap="openRectifications">整改闭环</button>
        </view>
        <view v-if="loading" class="state">正在加载临边任务…</view>
        <view v-else-if="errorMessage" class="state error"><text>{{ errorMessage }}</text><button @tap="loadTasks">重新加载</button></view>
        <view v-else class="task-list">
          <button v-for="task in filteredTasks" :key="task.id" class="task-card" @tap="openTask(task)">
            <view class="task-head">
              <view><text class="point">{{ task.pointName }}</text><text class="point-type">{{ pointType(task) }}</text></view>
              <text class="status" :class="{ danger: task.overdue && task.status === 'PENDING', warning: task.status === 'RECTIFICATION_PENDING' }">{{ statusLabel(task) }}</text>
            </view>
            <text class="code">{{ task.pointCode }}</text>
            <text class="position">{{ position(task) }}</text>
            <view class="foot"><text>{{ task.occurrenceDate }} · {{ executionSlot(task) }}</text><text>截止 {{ timePart(task.dueTime) }} ›</text></view>
          </button>
          <view v-if="!filteredTasks.length" class="state">当前筛选下没有临边巡检任务</view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<style scoped>
.page-shell{min-height:100vh;background:#f4f7fa;color:#26384a}.page-scroll{height:calc(100vh - 120rpx)}.content{padding:24rpx}.toolbar{display:flex;align-items:center;justify-content:space-between;gap:12rpx;margin-bottom:18rpx}.filter-row{display:flex;gap:10rpx}.filter,.rectification-link{height:60rpx;margin:0;padding:0 22rpx;border:1rpx solid #dce5ec;border-radius:999rpx;background:#fff;color:#667085;font-size:20rpx;line-height:60rpx}.filter::after,.task-card::after,.state button::after,.rectification-link::after{border:0}.filter.active{border-color:#315f86;background:#315f86;color:#fff}.rectification-link{padding:0 18rpx;border-color:#e6cda9;background:#fff8ee;color:#946025}.task-list{display:flex;flex-direction:column;gap:16rpx}.task-card{width:100%;margin:0;padding:24rpx;border:1rpx solid #e3e9ee;border-radius:18rpx;background:#fff;box-shadow:0 5rpx 18rpx rgba(38,56,74,.06);color:inherit;text-align:left}.task-head,.foot{display:flex;align-items:center;justify-content:space-between;gap:16rpx}.task-head>view{min-width:0;display:flex;align-items:center;gap:10rpx}.point{font-size:25rpx;font-weight:750}.point-type{padding:5rpx 10rpx;border-radius:999rpx;background:#eaf4fb;color:#315f86;font-size:18rpx}.status{flex-shrink:0;padding:5rpx 12rpx;border-radius:999rpx;background:#e9f6ef;color:#2f8f61;font-size:19rpx}.status.danger{background:#fff0ef;color:#cc403c}.status.warning{background:#fff3dd;color:#9b6718}.code{display:block;margin-top:12rpx;color:#667085;font-size:20rpx;font-weight:700}.position{display:block;margin-top:6rpx;color:#7d8997;font-size:20rpx;line-height:1.5}.foot{margin-top:16rpx;padding-top:14rpx;border-top:1rpx solid #eef1f4;color:#647789;font-size:19rpx}.state{padding:90rpx 20rpx;color:#98a2b3;text-align:center;font-size:22rpx}.state.error{color:#b54747}.state button{width:180rpx;height:64rpx;margin-top:20rpx;border-radius:14rpx;background:#315f86;color:#fff;font-size:21rpx;line-height:64rpx}
</style>
