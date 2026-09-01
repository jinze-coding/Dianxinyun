<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad, onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import { getEdgeInspectionRectifications, type EdgeInspectionRectificationSheet } from '@/api/edgeInspection';
import { useAuthStore } from '@/stores/auth';
import { useProjectStore } from '@/stores/project';
import { navigateTo, switchTab } from '@/utils/navigation';
import { usePageScrollHeight } from '@/utils/navLayout';

const authStore = useAuthStore();
const projectStore = useProjectStore();
const projectId = ref(0);
const activeFilter = ref('ACTIVE');
const sheets = ref<EdgeInspectionRectificationSheet[]>([]);
const loading = ref(false);
const errorMessage = ref('');
const { scrollStyle } = usePageScrollHeight({ bottomRpx: 30, minHeight: 260 });

onLoad((options) => {
  projectId.value = Number(options?.projectId || 0);
  const filter = String(options?.filter || '').toUpperCase();
  if (['RECTIFY', 'REVIEW'].includes(filter)) activeFilter.value = filter;
});
onShow(async () => {
  if (!await authStore.ensureRootAccess('/pages/inspection/index')) return;
  await load();
});

const filteredSheets = computed(() => sheets.value.filter((sheet) => {
  if (activeFilter.value === 'ALL') return true;
  if (activeFilter.value === 'ACTIVE') return !['CLOSED', 'VOIDED'].includes(sheet.status);
  if (activeFilter.value === 'RECTIFY') return ['UNASSIGNED', 'PENDING', 'REJECTED'].includes(sheet.status);
  if (activeFilter.value === 'REVIEW') return sheet.status === 'COMPLETED';
  return sheet.status === activeFilter.value;
}));

async function load() {
  loading.value = true;
  errorMessage.value = '';
  try {
    await projectStore.loadProjects();
    projectId.value ||= projectStore.state.currentProjectId || 0;
    sheets.value = await getEdgeInspectionRectifications({ projectId: projectId.value || undefined, scope: 'mine' });
  } catch (error) {
    sheets.value = [];
    errorMessage.value = error instanceof Error ? error.message : '临边整改单加载失败';
  } finally { loading.value = false; }
}

function statusLabel(status: string) {
  if (status === 'UNASSIGNED') return '待分派';
  if (status === 'PENDING') return '待整改';
  if (status === 'COMPLETED') return '待复查';
  if (status === 'REJECTED') return '已退回';
  if (status === 'CLOSED') return '已闭环';
  if (status === 'VOIDED') return '已作废';
  return status || '-';
}

function pointType(sheet: EdgeInspectionRectificationSheet) {
  return sheet.pointTypeName || sheet.categoryName || '临边点位';
}

function openSheet(sheet: EdgeInspectionRectificationSheet) {
  navigateTo(`/pages/rectification/edge-detail?id=${sheet.taskId}`);
}

function goBack() {
  if (getCurrentPages().length > 1) uni.navigateBack();
  else switchTab('/pages/inspection/index');
}
</script>

<template>
  <view class="page-shell">
    <AppNavBar title="临边整改闭环" @back="goBack" />
    <scroll-view class="page-scroll" scroll-y enable-flex :style="scrollStyle">
      <view class="content">
        <view class="filter-row">
          <button v-for="filter in [{ code: 'ACTIVE', name: '待处理' }, { code: 'RECTIFY', name: '待整改' }, { code: 'REVIEW', name: '待复查' }, { code: 'CLOSED', name: '已闭环' }, { code: 'ALL', name: '全部' }]"
            :key="filter.code" class="filter" :class="{ active: activeFilter === filter.code }" @tap="activeFilter = filter.code">{{ filter.name }}</button>
        </view>
        <view v-if="loading" class="state">正在加载临边整改单…</view>
        <view v-else-if="errorMessage" class="state error"><text>{{ errorMessage }}</text><button @tap="load">重新加载</button></view>
        <view v-else class="sheet-list">
          <button v-for="sheet in filteredSheets" :key="sheet.taskId" class="sheet-card" @tap="openSheet(sheet)">
            <view class="head"><view><text class="point">{{ sheet.pointName }}</text><text class="type">{{ pointType(sheet) }}</text></view><text class="status" :class="{ danger: sheet.overdue }">{{ statusLabel(sheet.status) }}</text></view>
            <text class="meta">{{ sheet.occurrenceDate || '巡检日期待确认' }} · {{ sheet.items.length }} 项异常</text>
            <view class="row"><text>整改负责人</text><text>{{ sheet.assigneeName || '待分派' }}</text></view>
            <view class="row"><text>复查负责人</text><text>{{ sheet.reviewerName || '待指定' }}</text></view>
            <view class="foot"><text :class="{ danger: sheet.overdue }">期限 {{ sheet.deadline || '-' }}</text><text>查看整单 ›</text></view>
          </button>
          <view v-if="!filteredSheets.length" class="state">当前筛选下没有临边整改单</view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<style scoped>
.page-shell{min-height:100vh;background:#f4f7fa;color:#26384a}.page-scroll{height:calc(100vh - 120rpx)}.content{padding:24rpx}.filter-row{display:flex;gap:10rpx;overflow-x:auto;margin-bottom:18rpx}.filter{height:60rpx;flex-shrink:0;margin:0;padding:0 22rpx;border:1rpx solid #dce5ec;border-radius:999rpx;background:#fff;color:#667085;font-size:20rpx;line-height:60rpx}.filter::after,.sheet-card::after,.state button::after{border:0}.filter.active{border-color:#315f86;background:#315f86;color:#fff}.sheet-list{display:flex;flex-direction:column;gap:16rpx}.sheet-card{width:100%;margin:0;padding:24rpx;border:1rpx solid #e3e9ee;border-radius:18rpx;background:#fff;box-shadow:0 5rpx 18rpx rgba(38,56,74,.06);color:inherit;text-align:left}.head,.foot,.row{display:flex;align-items:center;justify-content:space-between;gap:16rpx}.head>view{min-width:0;display:flex;align-items:center;gap:10rpx}.point{font-size:25rpx;font-weight:760}.type{padding:5rpx 10rpx;border-radius:999rpx;background:#eaf4fb;color:#315f86;font-size:18rpx}.status{flex-shrink:0;padding:5rpx 12rpx;border-radius:999rpx;background:#fff3dd;color:#9b6718;font-size:19rpx}.meta{display:block;margin-top:12rpx;color:#667085;font-size:20rpx}.row{margin-top:10rpx;color:#7d8997;font-size:20rpx}.row text:last-child{color:#344054}.foot{margin-top:16rpx;padding-top:14rpx;border-top:1rpx solid #eef1f4;color:#647789;font-size:19rpx}.danger{color:#c43f3b!important}.state{padding:90rpx 20rpx;color:#98a2b3;text-align:center;font-size:22rpx}.state.error{color:#b54747}.state button{width:180rpx;height:64rpx;margin-top:20rpx;border-radius:14rpx;background:#315f86;color:#fff;font-size:21rpx;line-height:64rpx}
</style>
