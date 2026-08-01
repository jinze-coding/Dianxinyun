<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import ElectricBoxPickerSheet from '@/components/ElectricBoxPickerSheet.vue';
import EmptyState from '@/components/EmptyState.vue';
import { getElectricBoxes } from '@/api/electricBox';
import { exportInspectionRecords, getInspectionRecords, getInspectionSummary, type InspectionMonthSummary } from '@/api/inspection';
import { useAuthStore } from '@/stores/auth';
import { useProjectStore } from '@/stores/project';
import type { ElectricBox, InspectionPeriodMode, InspectionRecord } from '@/types';
import { usePageScrollHeight } from '@/utils/navLayout';
import { getQueryNumber, navigateTo, showToast } from '@/utils/navigation';

type ResultFilter = 'ALL' | 'NORMAL' | 'ABNORMAL';

const authStore = useAuthStore();
const projectStore = useProjectStore();
const projectId = ref(0);
const queryBoxId = ref<number>();
const selectedBoxId = ref<number>();
const selectedMonth = ref(currentMonth());
const selectedDate = ref(currentDate());
const periodMode = ref<InspectionPeriodMode>('MONTH');
const resultFilter = ref<ResultFilter>('ALL');
const records = ref<InspectionRecord[]>([]);
const summary = ref<InspectionMonthSummary>();
const boxes = ref<ElectricBox[]>([]);
const loading = ref(false);
const loadError = ref('');
const exporting = ref(false);
const showBoxPicker = ref(false);
const { scrollStyle } = usePageScrollHeight({ minHeight: 260 });

const pageTitle = computed(() => queryBoxId.value ? '本箱巡检记录' : '巡检记录');
const selectedBox = computed(() => boxes.value.find((box) => box.id === selectedBoxId.value));
const selectedBoxLabel = computed(() => selectedBox.value
  ? `${selectedBox.value.boxCode} · ${selectedBox.value.boxName || selectedBox.value.installLocation}`
  : '全部电箱');
const summaryMetricLabels = computed(() => {
  if (periodMode.value === 'DAY') {
    return ['当日应检电箱', '当日已检电箱', '当日未检电箱', '当日异常电箱'];
  }
  return selectedBoxId.value
    ? ['本月应检天数', '本月已检天数', '本月未检天数', '本月异常天数']
    : ['本月应检箱次', '本月已检箱次', '本月未检箱次', '本月异常箱次'];
});
const canExport = computed(() => authStore.hasProjectPermission(
  projectId.value, 'inspection.export', 'SUMMARY_EXPORT'));
const visibleRecords = computed(() => records.value
  .filter((record) => !selectedBoxId.value || record.electricBoxId === selectedBoxId.value)
  .filter((record) => periodMode.value === 'MONTH' || record.checkDate === selectedDate.value)
  .filter((record) => resultFilter.value === 'ALL'
    || (resultFilter.value === 'ABNORMAL' ? Number(record.abnormalCount || 0) > 0 : Number(record.abnormalCount || 0) === 0))
  .slice()
  .sort((left, right) => new Date(right.inspectedAt || right.checkDate).getTime() - new Date(left.inspectedAt || left.checkDate).getTime()));

function currentMonth() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

function currentDate() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

onShow(() => { void initializePage(); });

async function initializePage() {
  loading.value = true;
  loadError.value = '';
  try {
    if (!await authStore.ensureRootAccess('/pages/inspection/index')) return;
    await projectStore.loadProjects();
    const pages = getCurrentPages();
    const current = pages[pages.length - 1] as unknown as { options?: Record<string, string> };
    projectId.value = getQueryNumber(
      current.options?.projectId,
      projectStore.state.currentProjectId || projectId.value
    );
    if (!await authStore.ensureProjectPermission(
      '/pages/inspection/index',
      projectId.value,
      'inspection.view',
      'BOX_VIEW',
      'INSPECTION_RECORD_VIEW',
      'SUMMARY_VIEW'
    )) return;
    const parsedBoxId = Number(current.options?.boxId || '');
    queryBoxId.value = Number.isFinite(parsedBoxId) && parsedBoxId > 0 ? parsedBoxId : undefined;
    selectedBoxId.value = queryBoxId.value;
    await loadData(false);
  } catch (error) {
    records.value = [];
    boxes.value = [];
    loadError.value = error instanceof Error ? error.message : '巡检记录加载失败';
  } finally {
    loading.value = false;
  }
}

async function loadData(manageLoading = true) {
  if (manageLoading) loading.value = true;
  loadError.value = '';
  try {
    const summaryRequest = periodMode.value === 'DAY'
      ? { projectId: projectId.value, boxId: selectedBoxId.value, checkDate: selectedDate.value }
      : { projectId: projectId.value, boxId: selectedBoxId.value, month: selectedMonth.value };
    const [recordList, summaryResult, boxList] = await Promise.all([
      getInspectionRecords(
        projectId.value,
        selectedBoxId.value,
        periodMode.value === 'MONTH' ? selectedMonth.value : undefined,
        periodMode.value === 'DAY' ? selectedDate.value : undefined
      ),
      getInspectionSummary(summaryRequest),
      getElectricBoxes(projectId.value)
    ]);
    records.value = recordList;
    summary.value = summaryResult;
    boxes.value = boxList;
  } catch (error) {
    records.value = [];
    summary.value = undefined;
    loadError.value = error instanceof Error ? error.message : '巡检记录加载失败';
  } finally {
    if (manageLoading) loading.value = false;
  }
}

async function changeMonth(event: unknown) {
  const value = (event as { detail?: { value?: string } }).detail?.value;
  if (!value || value === selectedMonth.value) return;
  selectedMonth.value = value;
  await loadData();
}

async function changeDate(event: unknown) {
  const value = (event as { detail?: { value?: string } }).detail?.value;
  if (!value || value === selectedDate.value) return;
  if (value > currentDate()) { showToast('不能选择未来日期'); return; }
  selectedDate.value = value;
  await loadData();
}

async function switchPeriod(mode: InspectionPeriodMode) {
  if (periodMode.value === mode) return;
  if (mode === 'DAY') {
    selectedDate.value = currentDate();
  } else {
    selectedMonth.value = selectedDate.value.slice(0, 7);
  }
  periodMode.value = mode;
  await loadData();
}

async function selectBox(boxId?: number) {
  if (queryBoxId.value) return;
  selectedBoxId.value = boxId;
  await loadData();
}

function displayDate(record: InspectionRecord) {
  return (record.inspectedAt || record.checkDate || '-').replace('T', ' ').slice(0, 16);
}

function openRecord(record: InspectionRecord) {
  record.id ? navigateTo(`/pages/inspection/detail?id=${record.id}`) : showToast('记录详情暂不可查看');
}

async function exportMonthlyTable() {
  if (!canExport.value) { showToast('当前项目无导出权限'); return; }
  if (periodMode.value !== 'MONTH') { showToast('按日查看时不支持导出，请切换到按月'); return; }
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
        <view v-if="loadError" class="error-card flow-card">
          <text class="error-mark">!</text>
          <text class="error-title">巡检记录加载失败</text>
          <text class="error-description">{{ loadError }}</text>
          <view class="error-actions">
            <button @tap="goBack">返回</button>
            <button class="primary" @tap="initializePage">重新加载</button>
          </view>
        </view>
        <template v-else>
        <view class="filter-card flow-card">
          <view class="period-tabs">
            <button :class="{ active: periodMode === 'MONTH' }" @tap="switchPeriod('MONTH')">按月</button>
            <button :class="{ active: periodMode === 'DAY' }" @tap="switchPeriod('DAY')">按日</button>
          </view>
          <view class="filter-row">
            <picker v-if="periodMode === 'MONTH'" mode="date" fields="month" :value="selectedMonth" @change="changeMonth">
              <view class="filter-button"><text>{{ selectedMonth }}</text><text class="filter-arrow"></text></view>
            </picker>
            <picker v-else mode="date" :value="selectedDate" :end="currentDate()" @change="changeDate">
              <view class="filter-button"><text>{{ selectedDate }}</text><text class="filter-arrow"></text></view>
            </picker>
            <button v-if="!queryBoxId" class="filter-button box-filter" @tap="showBoxPicker = true"><text>{{ selectedBoxLabel }}</text><text class="filter-arrow"></text></button>
            <view v-else class="fixed-scope">仅当前电箱</view>
          </view>
          <view class="result-tabs">
            <button :class="{ active: resultFilter === 'ALL' }" @tap="resultFilter = 'ALL'">全部</button>
            <button :class="{ active: resultFilter === 'NORMAL' }" @tap="resultFilter = 'NORMAL'">正常</button>
            <button :class="{ active: resultFilter === 'ABNORMAL', danger: resultFilter === 'ABNORMAL' }" @tap="resultFilter = 'ABNORMAL'">有异常</button>
          </view>
          <button v-if="canExport && periodMode === 'MONTH'" class="export-button" :disabled="!selectedBoxId || exporting" @tap="exportMonthlyTable">
            {{ exporting ? '正在导出...' : selectedBoxId ? '导出本箱月度检查表' : '请先选择电箱后导出' }}
          </button>
        </view>

        <view class="summary-card flow-card">
          <view><text>{{ summary?.shouldCheck || 0 }}</text><text>{{ summaryMetricLabels[0] }}</text></view>
          <view><text>{{ summary?.checked || 0 }}</text><text>{{ summaryMetricLabels[1] }}</text></view>
          <view><text class="danger">{{ summary?.missed || 0 }}</text><text>{{ summaryMetricLabels[2] }}</text></view>
          <view><text class="warning">{{ summary?.abnormal || 0 }}</text><text>{{ summaryMetricLabels[3] }}</text></view>
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
        </template>
      </view>
    </scroll-view>
    <ElectricBoxPickerSheet
      :visible="showBoxPicker"
      :project-id="projectId"
      :selected-id="selectedBoxId"
      title="筛选电箱"
      @close="showBoxPicker = false"
      @select="selectBox"
    />
  </view>
</template>

<style scoped src="../../styles/safety-flow.css"></style>
<style scoped>
.records-content { display: flex; flex-direction: column; gap: 15rpx; padding-top: 16rpx; }
.filter-card { padding: 18rpx; }
.period-tabs { display: grid; grid-template-columns: 1fr 1fr; gap: 5rpx; margin-bottom: 13rpx; padding: 5rpx; border-radius: 14rpx; background: #eef3f7; }
.period-tabs button { display: flex; height: 50rpx; align-items: center; justify-content: center; margin: 0; padding: 0; border-radius: 10rpx; background: transparent; color: #77818e; font-size: 20rpx; line-height: 1; }
.period-tabs button::after { border: 0; }
.period-tabs button.active { background: #fff; color: var(--inspection-primary-deep); box-shadow: 0 4rpx 12rpx rgba(49,95,134,.08); font-weight: 800; }
.filter-row { display: flex; align-items: center; gap: 10rpx; }
.filter-row picker { min-width: 0; flex: 1; }
.filter-row picker:first-child { max-width: 210rpx; }
.filter-button, .fixed-scope { display: flex; height: 62rpx; align-items: center; justify-content: space-between; gap: 12rpx; padding: 0 16rpx; border: 1rpx solid var(--inspection-border); border-radius: 14rpx; background: #f7fafc; color: #465a70; font-size: 20rpx; font-weight: 700; }
.box-filter { min-width: 0; flex: 1; margin: 0; line-height: 1; text-align: left; }
.box-filter::after { border: 0; }
.box-filter text:first-child { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
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
.summary-card { display: grid; grid-template-columns: repeat(4, 1fr); padding: 18rpx 4rpx; }
.summary-card view { text-align: center; }
.summary-card view + view { border-left: 1rpx solid var(--inspection-divider); }
.summary-card text { display: block; }
.summary-card text:first-child { color: var(--inspection-primary-deep); font-size: 27rpx; font-weight: 900; }
.summary-card text:first-child.danger { color: var(--inspection-danger); }
.summary-card text:first-child.warning { color: #b56b20; }
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
.error-card { display: flex; min-height: 440rpx; align-items: center; justify-content: center; flex-direction: column; padding: 36rpx 28rpx; text-align: center; }
.error-mark { display: flex; width: 58rpx; height: 58rpx; align-items: center; justify-content: center; border-radius: 18rpx; background: var(--inspection-danger-soft); color: var(--inspection-danger); font-size: 32rpx; font-weight: 900; }
.error-title { margin-top: 20rpx; color: var(--inspection-text); font-size: 26rpx; font-weight: 850; }
.error-description { margin-top: 10rpx; color: #6f7f90; font-size: 20rpx; line-height: 1.6; word-break: break-all; }
.error-actions { display: grid; width: 100%; grid-template-columns: 1fr 1fr; gap: 13rpx; margin-top: 28rpx; }
.error-actions button { display: flex; height: 68rpx; align-items: center; justify-content: center; margin: 0; border: 1rpx solid var(--inspection-border); border-radius: 14rpx; background: #fff; color: var(--inspection-primary-deep); font-size: 20rpx; font-weight: 750; line-height: 1; }
.error-actions button::after { border: 0; }
.error-actions button.primary { border-color: var(--inspection-primary-deep); background: var(--inspection-primary-deep); color: #fff; }
</style>
