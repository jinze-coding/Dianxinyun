<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad, onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import {
  getGeneralInspectionTasks,
  resolveGeneralInspectionScan,
  type GeneralInspectionTask
} from '@/api/generalInspection';
import { useAuthStore } from '@/stores/auth';
import { useProjectStore } from '@/stores/project';
import { navigateTo, showToast, switchTab } from '@/utils/navigation';
import { usePageScrollHeight } from '@/utils/navLayout';

const authStore = useAuthStore();
const projectStore = useProjectStore();
const tasks = ref<GeneralInspectionTask[]>([]);
const loading = ref(false);
const errorMessage = ref('');
const scene = ref('');
const activeFilter = ref('ACTIVE');
const { scrollStyle } = usePageScrollHeight({ bottomRpx: 30, minHeight: 260 });

onLoad((options) => {
  scene.value = String(options?.scene || '');
});

onShow(async () => {
  if (!await authStore.ensureRootAccess('/pages/inspection/index')) return;
  await loadTasks();
});

const filteredTasks = computed(() => tasks.value.filter((task) => {
  if (activeFilter.value === 'ALL') return true;
  if (activeFilter.value === 'ACTIVE') return ['PENDING', 'RECTIFICATION_PENDING'].includes(task.status);
  return task.status === activeFilter.value;
}));

async function loadTasks() {
  loading.value = true;
  errorMessage.value = '';
  try {
    if (scene.value) {
      const scan = await resolveGeneralInspectionScan(scene.value);
      tasks.value = scan.eligibleTasks || [];
    } else {
      await projectStore.loadProjects();
      const projectId = projectStore.state.currentProjectId || undefined;
      tasks.value = await getGeneralInspectionTasks({ projectId, mine: true });
    }
  } catch (error) {
    tasks.value = [];
    errorMessage.value = error instanceof Error ? error.message : '通用巡检任务加载失败';
  } finally {
    loading.value = false;
  }
}

function statusLabel(task: GeneralInspectionTask) {
  if (task.displayStatus) return task.displayStatus;
  if (task.status === 'COMPLETED') return task.lateSubmission ? '逾期补检' : '已完成';
  if (task.status === 'RECTIFICATION_PENDING') return '整改中';
  if (task.status === 'CLOSED') return '已闭环';
  if (task.status === 'CANCELLED') return '已取消';
  return task.overdue ? '逾期未检' : '待巡检';
}

function openTask(task: GeneralInspectionTask) {
  if (!task.canExecute && task.status === 'PENDING') {
    showToast(task.overdue ? '该任务可补检，请确认任务已进入执行窗口' : '任务尚未进入提前执行窗口');
    return;
  }
  if (task.qrRequired && !task.scanVerified && task.status === 'PENDING') {
    showToast('该点位必须先扫描现场二维码，二维码损坏请联系管理者换码');
    return;
  }
  const sceneQuery = scene.value ? `&scene=${encodeURIComponent(scene.value)}` : '';
  navigateTo(`/pages/inspection/general-form?id=${task.id}${sceneQuery}`);
}

function goBack() {
  if (getCurrentPages().length > 1) uni.navigateBack();
  else switchTab('/pages/inspection/index');
}
</script>

<template>
  <view class="page-shell">
    <AppNavBar title="我的通用巡检" @back="goBack" />
    <scroll-view class="page-scroll" scroll-y enable-flex :style="scrollStyle">
      <view class="content">
        <view class="filter-row">
          <button v-for="filter in [{ code: 'ACTIVE', name: '待处理' }, { code: 'COMPLETED', name: '已完成' }, { code: 'ALL', name: '全部' }]"
            :key="filter.code" class="filter" :class="{ active: activeFilter === filter.code }" @tap="activeFilter = filter.code">
            {{ filter.name }}
          </button>
        </view>
        <view v-if="scene" class="scan-tip">已核验当前点位二维码，仅展示该点位可执行任务</view>
        <view v-if="loading" class="state">正在加载任务…</view>
        <view v-else-if="errorMessage" class="state error">
          <text>{{ errorMessage }}</text><button @tap="loadTasks">重新加载</button>
        </view>
        <view v-else class="task-list">
          <button v-for="task in filteredTasks" :key="task.id" class="task-card" @tap="openTask(task)">
            <view class="task-head">
              <text class="point">{{ task.pointCode }} · {{ task.pointName }}</text>
              <text class="status" :class="{ danger: task.overdue && task.status === 'PENDING' }">{{ statusLabel(task) }}</text>
            </view>
            <text class="template">{{ task.templateName }}</text>
            <view class="meta"><text>{{ task.occurrenceDate }} {{ task.slotName }}</text><text>{{ task.locationDesc || '未填写位置说明' }}</text></view>
            <view class="foot"><text>截止 {{ task.dueTime }}</text><text>{{ task.qrRequired ? '必须扫码' : '可直接执行' }} ›</text></view>
          </button>
          <view v-if="!filteredTasks.length" class="state">当前筛选下没有通用巡检任务</view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<style scoped>
.page-shell{min-height:100vh;background:#f4f7fa;color:#26384a}.page-scroll{height:calc(100vh - 120rpx)}.content{padding:24rpx}.filter-row{display:flex;gap:12rpx;margin-bottom:18rpx}.filter{height:62rpx;margin:0;padding:0 26rpx;border:1rpx solid #dce5ec;border-radius:999rpx;background:#fff;color:#667085;font-size:22rpx;line-height:62rpx}.filter::after,.task-card::after,.state button::after{border:0}.filter.active{border-color:#315f86;background:#315f86;color:#fff}.scan-tip{margin-bottom:18rpx;padding:18rpx 22rpx;border-radius:14rpx;background:#eaf4fb;color:#315f86;font-size:21rpx}.task-list{display:flex;flex-direction:column;gap:16rpx}.task-card{width:100%;margin:0;padding:24rpx;border:1rpx solid #e3e9ee;border-radius:18rpx;background:#fff;box-shadow:0 5rpx 18rpx rgba(38,56,74,.06);color:inherit;text-align:left}.task-head,.foot{display:flex;align-items:center;justify-content:space-between;gap:16rpx}.point{font-size:25rpx;font-weight:750}.status{flex-shrink:0;padding:5rpx 12rpx;border-radius:999rpx;background:#e9f6ef;color:#2f8f61;font-size:19rpx}.status.danger{background:#fff0ef;color:#cc403c}.template{display:block;margin-top:10rpx;color:#475467;font-size:23rpx}.meta{display:flex;flex-direction:column;gap:5rpx;margin-top:12rpx;color:#7d8997;font-size:20rpx}.foot{margin-top:16rpx;padding-top:14rpx;border-top:1rpx solid #eef1f4;color:#647789;font-size:20rpx}.state{padding:90rpx 20rpx;color:#98a2b3;text-align:center;font-size:22rpx}.state.error{color:#b54747}.state button{width:180rpx;height:64rpx;margin-top:20rpx;border-radius:14rpx;background:#315f86;color:#fff;font-size:21rpx;line-height:64rpx}
</style>
