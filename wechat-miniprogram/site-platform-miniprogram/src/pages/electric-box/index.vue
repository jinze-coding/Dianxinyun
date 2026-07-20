<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import EmptyState from '@/components/EmptyState.vue';
import { getElectricBoxes } from '@/api/electricBox';
import type { ElectricBox } from '@/types';
import { usePageScrollHeight } from '@/utils/navLayout';
import { getQueryNumber, navigateTo, showToast, switchTab } from '@/utils/navigation';

type FilterValue = 'ALL' | 'UNCHECKED' | 'CHECKED' | 'ABNORMAL' | 'INACTIVE';
const projectId = ref(1);
const boxes = ref<ElectricBox[]>([]);
const keyword = ref('');
const filter = ref<FilterValue>('ALL');
const loading = ref(true);
const { scrollStyle } = usePageScrollHeight({ minHeight: 280 });

const filterOptions: Array<{ label: string; value: FilterValue }> = [
  { label: '全部', value: 'ALL' }, { label: '今日未检', value: 'UNCHECKED' }, { label: '已检', value: 'CHECKED' }, { label: '异常', value: 'ABNORMAL' }, { label: '停用', value: 'INACTIVE' }
];

onShow(async () => {
  const pages = getCurrentPages();
  const current = pages[pages.length - 1] as unknown as { options?: Record<string, string> };
  projectId.value = getQueryNumber(current.options?.projectId, projectId.value || 1);
  await loadBoxes();
});

async function loadBoxes() {
  loading.value = true;
  try { boxes.value = await getElectricBoxes(projectId.value); }
  catch (error) { boxes.value = []; showToast(error instanceof Error ? error.message : '电箱台账加载失败'); }
  finally { loading.value = false; }
}

const activeCount = computed(() => boxes.value.filter((item) => item.status === 'ACTIVE').length);
const dueCount = computed(() => boxes.value.filter((item) => item.status === 'ACTIVE' && item.inspectionRequired !== false).length);
const uncheckedCount = computed(() => boxes.value.filter((item) => item.status === 'ACTIVE' && item.inspectionRequired !== false && item.todayStatus === 'UNCHECKED').length);
const openRectifications = computed(() => boxes.value.reduce((sum, item) => sum + (item.pendingRectificationCount || 0), 0));
const filteredBoxes = computed(() => boxes.value.filter((box) => {
  const text = keyword.value.trim().toLowerCase();
  const keywordHit = !text || `${box.boxCode}${box.boxName}${box.installLocation}${box.responsibleElectricianName}`.toLowerCase().includes(text);
  const statusHit = filter.value === 'ALL'
    || (filter.value === 'CHECKED' && box.todayStatus === 'CHECKED' && box.status === 'ACTIVE')
    || (filter.value === 'UNCHECKED' && box.todayStatus === 'UNCHECKED' && box.status === 'ACTIVE')
    || (filter.value === 'ABNORMAL' && box.todayStatus === 'ABNORMAL')
    || (filter.value === 'INACTIVE' && box.status !== 'ACTIVE');
  return keywordHit && statusHit;
}));

function setFilter(value: FilterValue) { filter.value = value; }
function updateKeyword(event: unknown) { const inputEvent = event as { detail?: { value?: string }; target?: { value?: string } }; keyword.value = inputEvent.detail?.value || inputEvent.target?.value || ''; }
function openDetail(id: number) { navigateTo(`/pages/electric-box/detail?id=${id}`); }
function goBack() { getCurrentPages().length > 1 ? uni.navigateBack() : switchTab('/pages/inspection/index'); }
function statusTag(box: ElectricBox) {
  if (box.status === 'REMOVED') return { label: '已拆除', tone: 'muted' };
  if (box.status === 'INACTIVE') return { label: '停用', tone: 'muted' };
  if (box.todayStatus === 'ABNORMAL') return { label: '异常', tone: 'danger' };
  if (box.todayStatus === 'CHECKED') return { label: '已检', tone: 'success' };
  return { label: '未检', tone: 'warning' };
}
</script>

<template>
  <view class="flow-page ledger-page">
    <AppNavBar title="电箱台账" @back="goBack" />
    <scroll-view class="flow-scroll" scroll-y enable-flex :style="scrollStyle">
      <view class="flow-content ledger-content">
        <view class="search-row"><view class="search-box"><text class="search-icon"></text><input :value="keyword" placeholder="搜索编号、位置或负责人" placeholder-class="search-placeholder" @input="updateKeyword" /><button v-if="keyword" @tap="keyword = ''">×</button></view></view>

        <view class="metric-strip flow-card">
          <view><text>{{ activeCount }}</text><text>启用</text></view><view><text>{{ dueCount }}</text><text>今日应检</text></view><view><text class="warning">{{ uncheckedCount }}</text><text>待检</text></view><view><text class="danger">{{ openRectifications }}</text><text>未闭环</text></view>
        </view>

        <scroll-view class="filter-scroll" scroll-x :show-scrollbar="false"><view class="filter-list"><button v-for="option in filterOptions" :key="option.value" class="filter-chip pressable" :class="{ active: filter === option.value }" @tap="setFilter(option.value)">{{ option.label }}<text v-if="option.value === 'UNCHECKED' && uncheckedCount">{{ uncheckedCount }}</text></button></view></scroll-view>

        <view v-if="loading" class="skeleton-list"><view v-for="index in 3" :key="index" class="skeleton-card flow-card"><view class="flow-skeleton line wide"></view><view class="flow-skeleton line"></view><view class="flow-skeleton foot"></view></view></view>

        <view v-else-if="filteredBoxes.length" :key="`${filter}-${keyword}`" class="box-list">
          <view v-for="(box, index) in filteredBoxes" :key="box.id" class="box-card flow-card pressable stagger-item" :style="{ animationDelay: `${index * 42}ms` }" @tap="openDetail(box.id)">
            <view class="card-head"><view><text class="box-code">{{ box.boxCode }}</text><text class="box-name">{{ box.boxName }}</text></view><text class="status-pill" :class="statusTag(box).tone">{{ statusTag(box).label }}</text></view>
            <view class="location-row"><text class="pin"></text><text>{{ box.installLocation }}</text></view>
            <view class="card-footer"><view><text>负责电工</text><text>{{ box.responsibleElectricianName || '未配置' }}</text></view><view class="mini-tags"><text>{{ box.inspectionRequired === false ? '未纳入日检' : '纳入日检' }}</text><text v-if="box.pendingRectificationCount" class="danger-tag">整改 {{ box.pendingRectificationCount }}</text></view><text class="row-arrow"></text></view>
          </view>
        </view>

        <view v-else class="empty-wrap"><EmptyState title="暂无匹配电箱" description="试试切换筛选或清空搜索关键词" /></view>
      </view>
    </scroll-view>
  </view>
</template>

<style scoped src="../../styles/safety-flow.css"></style>
<style scoped>
.ledger-content { padding-top: 16rpx; }.search-row { display: flex; gap: 12rpx; }.search-box { display: flex; height: 68rpx; align-items: center; gap: 14rpx; flex: 1; padding: 0 18rpx; border: 1rpx solid #ebe6df; border-radius: 16rpx; background: #fff; }.search-box input { height: 66rpx; flex: 1; color: #344054; font-size: 22rpx; }.search-placeholder { color: #a1a7af; }.search-box button { width: 42rpx; height: 42rpx; min-height: 0; margin: 0; padding: 0 0 3rpx; border-radius: 50%; background: #f1efeb; color: #7c8490; font-size: 26rpx; line-height: 42rpx; }.search-box button::after { border: 0; }.search-icon { position: relative; width: 25rpx; height: 25rpx; flex-shrink: 0; border: 3rpx solid #8d765e; border-radius: 50%; }.search-icon::after { position: absolute; right: -8rpx; bottom: -5rpx; width: 11rpx; height: 3rpx; border-radius: 999rpx; background: #8d765e; transform: rotate(45deg); content: ''; }
.metric-strip { display: grid; grid-template-columns: repeat(4, 1fr); margin-top: 16rpx; padding: 17rpx 8rpx; }.metric-strip view { position: relative; text-align: center; }.metric-strip view+view::before { position: absolute; top: 7rpx; bottom: 7rpx; left: 0; width: 1rpx; background: #eeeae4; content: ''; }.metric-strip text { display: block; }.metric-strip text:first-child { color: #1e7f5a; font-size: 28rpx; font-weight: 900; }.metric-strip text:first-child.warning { color: #bd6c1d; }.metric-strip text:first-child.danger { color: #bd4e46; }.metric-strip text:last-child { margin-top: 4rpx; color: #8a929d; font-size: 18rpx; }
.filter-scroll { width: 100%; margin-top: 16rpx; white-space: nowrap; }.filter-list { display: inline-flex; gap: 9rpx; padding: 1rpx 2rpx 3rpx; }.filter-chip { display: flex; height: 54rpx; align-items: center; gap: 7rpx; margin: 0; padding: 0 19rpx; border: 1rpx solid #e8e3dc; border-radius: 999rpx; background: #faf9f7; color: #687382; font-size: 20rpx; line-height: 54rpx; }.filter-chip::after { border: 0; }.filter-chip.active { border-color: #e2b788; background: #fff0df; color: #a55f22; font-weight: 750; }.filter-chip text { display: flex; min-width: 27rpx; height: 27rpx; align-items: center; justify-content: center; border-radius: 999rpx; background: #c17630; color: #fff; font-size: 16rpx; }
.box-list, .skeleton-list { display: flex; flex-direction: column; gap: 13rpx; margin-top: 16rpx; }.box-card { padding: 20rpx; }.card-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 14rpx; }.box-code, .box-name { display: block; }.box-code { font-size: 27rpx; font-weight: 900; }.box-name { margin-top: 3rpx; color: #657181; font-size: 21rpx; }.status-pill { padding: 5rpx 12rpx; border-radius: 999rpx; font-size: 18rpx; font-weight: 750; }.status-pill.success { background: #e4f5eb; color: #168653; }.status-pill.warning { background: #fff0df; color: #b56a21; }.status-pill.danger { background: #fde9e6; color: #bc4d45; }.status-pill.muted { background: #f0efec; color: #7f7b75; }
.location-row { display: flex; align-items: center; gap: 10rpx; margin-top: 14rpx; color: #788493; font-size: 20rpx; }.pin { width: 9rpx; height: 9rpx; flex-shrink: 0; border: 3rpx solid #b07a46; border-radius: 50% 50% 50% 0; transform: rotate(-45deg); }
.card-footer { position: relative; display: flex; align-items: center; gap: 16rpx; margin-top: 16rpx; padding-top: 14rpx; padding-right: 23rpx; border-top: 1rpx solid #f0ede8; }.card-footer>view:first-child { min-width: 0; flex: 1; }.card-footer>view:first-child text { display: block; }.card-footer>view:first-child text:first-child { color: #999fa7; font-size: 17rpx; }.card-footer>view:first-child text:last-child { margin-top: 3rpx; color: #485466; font-size: 20rpx; font-weight: 700; }.mini-tags { display: flex; align-items: center; gap: 7rpx; }.mini-tags text { padding: 5rpx 9rpx; border-radius: 8rpx; background: #f3f1ed; color: #84786b; font-size: 17rpx; }.mini-tags .danger-tag { background: #fdecea; color: #b54d46; }.row-arrow { position: absolute; right: 2rpx; width: 10rpx; height: 10rpx; border-top: 2rpx solid #a19a91; border-right: 2rpx solid #a19a91; transform: rotate(45deg); }
.skeleton-card { padding: 21rpx; }.flow-skeleton.line { width: 55%; height: 24rpx; }.flow-skeleton.line.wide { width: 37%; height: 30rpx; margin-bottom: 15rpx; }.flow-skeleton.foot { width: 100%; height: 48rpx; margin-top: 24rpx; }.empty-wrap { margin-top: 42rpx; }
</style>
