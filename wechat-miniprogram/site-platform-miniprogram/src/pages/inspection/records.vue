<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import EmptyState from '@/components/EmptyState.vue';
import { getElectricBoxes } from '@/api/electricBox';
import { exportInspectionRecords, getInspectionRecords } from '@/api/inspection';
import type { ElectricBox, InspectionRecord } from '@/types';
import { usePageScrollHeight } from '@/utils/navLayout';
import { getQueryNumber, navigateTo, showToast } from '@/utils/navigation';

type ResultFilter = 'ALL' | 'NORMAL' | 'ABNORMAL';

const projectId = ref(1);
const queryBoxId = ref<number>();
const selectedBoxId = ref<number>();
const selectedMonth = ref(currentMonth());
const resultFilter = ref<ResultFilter>('ALL');
const records = ref<InspectionRecord[]>([]);
const boxes = ref<ElectricBox[]>([]);
const loading = ref(false);
const exporting = ref(false);
const { scrollStyle } = usePageScrollHeight({ minHeight: 260 });

const pageTitle = computed(() => queryBoxId.value ? '本箱巡检记录' : '巡检记录');
const boxOptions = computed(() => [
  { id: undefined, label: '全部电箱' },
  ...boxes.value.map((box) => ({ id: box.id, label: `${box.boxCode} · ${box.boxName || box.installLocation}` }))
]);
const selectedBoxIndex = computed(() => Math.max(0, boxOptions.value.findIndex((item) => item.id === selectedBoxId.value)));
const selectedBox = computed(() => boxes.value.find((box) => box.id === selectedBoxId.value));
const visibleRecords = computed(() => records.value
  .filter((record) => !selectedBoxId.value || record.electricBoxId === selectedBoxId.value)
  .filter((record) => resultFilter.value === 'ALL'
    || (resultFilter.value === 'ABNORMAL' ? Number(record.abnormalCount || 0) > 0 : Number(record.abnormalCount || 0) === 0))
  .slice()
  .sort((left, right) => new Date(right.inspectedAt || right.checkDate).getTime() - new Date(left.inspectedAt || left.checkDate).getTime()));
const abnormalRecords = computed(() => visibleRecords.value.filter((record) => Number(record.abnormalCount || 0) > 0).length);

function currentMonth() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

onShow(async () => {
  const pages = getCurrentPages();
  const current = pages[pages.length - 1] as unknown as { options?: Record<string, string> };
  projectId.value = getQueryNumber(current.options?.projectId, projectId.value || 1);
  const parsedBoxId = Number(current.options?.boxId || '');
  queryBoxId.value = Number.isFinite(parsedBoxId) && parsedBoxId > 0 ? parsedBoxId : undefined;
  selectedBoxId.value = queryBoxId.value;
  await loadData();
});

async function loadData() {
  loading.value = true;
  try {
    const [recordList, boxList] = await Promise.all([
      getInspectionRecords(projectId.value, queryBoxId.value, selectedMonth.value),
      getElectricBoxes(projectId.value)
    ]);
    records.value = recordList;
    boxes.value = boxList;
  } catch (error) {
    records.value = [];
    showToast(error instanceof Error ? error.message : '巡检记录加载失败');
  } finally {
    loading.value = false;
  }
}

async function changeMonth(event: unknown) {
  const value = (event as { detail?: { value?: string } }).detail?.value;
  if (!value || value === selectedMonth.value) return;
  selectedMonth.value = value;
  await loadData();
}

function changeBox(event: unknown) {
  const index = Number((event as { detail?: { value?: string | number } }).detail?.value || 0);
  selectedBoxId.value = boxOptions.value[index]?.id;
}

function displayDate(record: InspectionRecord) {
  return (record.inspectedAt || record.checkDate || '-').replace('T', ' ').slice(0, 16);
}

function openRecord(record: InspectionRecord) {
  record.id ? navigateTo(`/pages/inspection/detail?id=${record.id}`) : showToast('记录详情暂不可查看');
}

async function exportMonthlyTable() {
  if (!selectedBox.value || exporting.value) {
    if (!selectedBox.value) showToast('请先选择需要导出的电箱');
    return;
  }
  exporting.value = true;
  try {
    const result = await exportInspectionRecords({
      projectId: projectId.value,
      boxId: selectedBox.value.id,
      boxCode: selectedBox.value.boxCode,
      month: selectedMonth.value
    });
    if (result.mock) showToast(`演示导出：${result.fileName}`);
  } catch (error) {
    showToast(error instanceof Error ? error.message : '导出月度检查表失败');
  } finally {
    exporting.value = false;
  }
}

function goBack() {
  getCurrentPages().length > 1 ? uni.navigateBack() : uni.switchTab({ url: '/pages/inspection/index' });
}
</script>

<template>
  <view class="flow-page records-page">
    <AppNavBar :title="pageTitle" @back="goBack" />
    <scroll-view class="flow-scroll" scroll-y enable-flex :style="scrollStyle">
      <view class="flow-content records-content">
        <view class="filter-card flow-card">
          <view class="filter-row">
            <picker mode="date" fields="month" :value="selectedMonth" @change="changeMonth">
              <view class="filter-button"><text>{{ selectedMonth }}</text><text class="filter-arrow"></text></view>
            </picker>
            <picker v-if="!queryBoxId" :range="boxOptions" range-key="label" :value="selectedBoxIndex" @change="changeBox">
              <view class="filter-button box-filter"><text>{{ boxOptions[selectedBoxIndex]?.label }}</text><text class="filter-arrow"></text></view>
            </picker>
            <view v-else class="fixed-scope">仅当前电箱</view>
          </view>
          <view class="result-tabs">
            <button :class="{ active: resultFilter === 'ALL' }" @tap="resultFilter = 'ALL'">全部</button>
            <button :class="{ active: resultFilter === 'NORMAL' }" @tap="resultFilter = 'NORMAL'">正常</button>
            <button :class="{ active: resultFilter === 'ABNORMAL', danger: resultFilter === 'ABNORMAL' }" @tap="resultFilter = 'ABNORMAL'">有异常</button>
          </view>
          <button class="export-button" :disabled="!selectedBoxId || exporting" @tap="exportMonthlyTable">
            {{ exporting ? '正在导出...' : selectedBoxId ? '导出本箱月度检查表' : '请先选择电箱后导出' }}
          </button>
        </view>

        <view class="summary-card flow-card">
          <view><text>{{ visibleRecords.length }}</text><text>巡检记录</text></view>
          <view><text class="danger">{{ abnormalRecords }}</text><text>异常记录</text></view>
          <view><text>{{ selectedMonth.slice(5) }}月</text><text>当前月份</text></view>
        </view>

        <view v-if="loading" class="loading-list">
          <view v-for="index in 3" :key="index" class="loading-card flow-card"><view class="flow-skeleton line"></view><view class="flow-skeleton subline"></view></view>
        </view>
        <view v-else-if="visibleRecords.length" class="record-list">
          <view v-for="(record, index) in visibleRecords" :key="record.id || `${record.boxCode}-${record.checkDate}`" class="record-card flow-card pressable stagger-item" :style="{ animationDelay: `${index * 36}ms` }" @tap="openRecord(record)">
            <view class="record-head">
              <view><text class="record-code">{{ record.boxCode }}</text><text class="record-date">{{ displayDate(record) }}</text></view>
              <text class="status" :class="{ abnormal: Number(record.abnormalCount || 0) > 0 }">{{ Number(record.abnormalCount || 0) > 0 ? '有异常' : '正常' }}</text>
            </view>
            <view class="record-meta"><text>{{ record.installLocation || record.boxName || '未记录位置' }}</text><text>巡检员：{{ record.inspectorName || '-' }}</text></view>
            <view class="record-footer"><text class="remark">{{ record.remark || '无备注' }}</text><text class="abnormal-count">异常 {{ record.abnormalCount || 0 }} 项</text><text class="arrow"></text></view>
          </view>
        </view>
        <view v-else class="empty-wrap"><EmptyState title="暂无巡检记录" description="当前筛选条件下没有已完成的巡检记录" /></view>
      </view>
    </scroll-view>
  </view>
</template>

<style scoped src="../../styles/safety-flow.css"></style>
<style scoped>
.records-content { display: flex; flex-direction: column; gap: 15rpx; padding-top: 16rpx; }
.filter-card { padding: 18rpx; }
.filter-row { display: flex; align-items: center; gap: 10rpx; }
.filter-row picker { min-width: 0; flex: 1; }
.filter-row picker:first-child { max-width: 210rpx; }
.filter-button, .fixed-scope { display: flex; height: 62rpx; align-items: center; justify-content: space-between; gap: 12rpx; padding: 0 16rpx; border: 1rpx solid var(--inspection-border); border-radius: 14rpx; background: #f7fafc; color: #465a70; font-size: 20rpx; font-weight: 700; }
.box-filter text:first-child { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fixed-scope { justify-content: center; flex: 1; color: var(--inspection-primary-deep); background: var(--inspection-soft); }
.filter-arrow { width: 9rpx; height: 9rpx; flex-shrink: 0; border-right: 2rpx solid #6d8ba7; border-bottom: 2rpx solid #6d8ba7; transform: rotate(45deg) translateY(-3rpx); }
.result-tabs { display: grid; grid-template-columns: repeat(3, 1fr); gap: 5rpx; margin-top: 13rpx; padding: 5rpx; border-radius: 14rpx; background: #eef3f7; }
.result-tabs button { display: flex; height: 52rpx; align-items: center; justify-content: center; margin: 0; padding: 0; border-radius: 10rpx; background: transparent; color: #77818e; font-size: 20rpx; line-height: 1; }
.result-tabs button::after { border: 0; }
.result-tabs button.active { background: var(--inspection-soft); color: var(--inspection-primary-deep); box-shadow: inset 0 0 0 1rpx var(--inspection-border); font-weight: 750; }
.result-tabs button.active.danger { background: var(--inspection-danger-soft); color: var(--inspection-danger); box-shadow: inset 0 0 0 1rpx rgba(183,83,83,.16); }
.export-button { display: flex; width: 100%; height: 62rpx; min-height: 62rpx; align-items: center; justify-content: center; margin-top: 13rpx; border: 1rpx solid var(--inspection-border); border-radius: 13rpx; background: var(--inspection-soft); color: var(--inspection-primary-deep); font-size: 21rpx; font-weight: 750; line-height: 1; }
.export-button::after { border: 0; }
.export-button:active { background: var(--inspection-soft-strong); }
.export-button[disabled] { border-color: var(--inspection-divider); background: #f1f4f7; color: #98a5b3; opacity: 1; }
.summary-card { display: grid; grid-template-columns: repeat(3, 1fr); padding: 18rpx 8rpx; }
.summary-card view { text-align: center; }
.summary-card view + view { border-left: 1rpx solid var(--inspection-divider); }
.summary-card text { display: block; }
.summary-card text:first-child { color: var(--inspection-primary-deep); font-size: 27rpx; font-weight: 900; }
.summary-card text:first-child.danger { color: var(--inspection-danger); }
.summary-card text:last-child { margin-top: 3rpx; color: #9299a2; font-size: 18rpx; }
.record-list, .loading-list { display: flex; flex-direction: column; gap: 12rpx; }
.record-card { padding: 20rpx; }
.record-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 15rpx; }
.record-code, .record-date { display: block; }
.record-code { font-size: 25rpx; font-weight: 900; }
.record-date { margin-top: 4rpx; color: #9098a2; font-size: 18rpx; }
.status { padding: 5rpx 11rpx; border-radius: 999rpx; background: var(--inspection-success-soft); color: var(--inspection-success); font-size: 18rpx; font-weight: 750; }
.status.abnormal { background: var(--inspection-danger-soft); color: var(--inspection-danger); }
.record-meta { display: flex; align-items: center; justify-content: space-between; gap: 14rpx; margin-top: 13rpx; color: #687586; font-size: 19rpx; }
.record-meta text { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.record-meta text:first-child { flex: 1; }
.record-footer { position: relative; display: flex; align-items: center; gap: 14rpx; margin-top: 14rpx; padding-top: 13rpx; padding-right: 24rpx; border-top: 1rpx solid var(--inspection-divider); }
.remark { min-width: 0; overflow: hidden; flex: 1; color: #8b949f; font-size: 18rpx; text-overflow: ellipsis; white-space: nowrap; }
.abnormal-count { flex-shrink: 0; color: var(--inspection-danger); font-size: 18rpx; }
.arrow { position: absolute; right: 2rpx; width: 9rpx; height: 9rpx; border-top: 2rpx solid #8fa1b3; border-right: 2rpx solid #8fa1b3; transform: rotate(45deg); }
.loading-card { padding: 21rpx; }
.flow-skeleton.line { width: 45%; height: 27rpx; }
.flow-skeleton.subline { width: 76%; height: 19rpx; margin-top: 15rpx; }
.empty-wrap { margin-top: 36rpx; }
</style>
