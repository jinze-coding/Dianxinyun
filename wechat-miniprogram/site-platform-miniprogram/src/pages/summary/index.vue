<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import AnimatedNumber from '@/components/AnimatedNumber.vue';
import ElectricBoxPickerSheet from '@/components/ElectricBoxPickerSheet.vue';
import MiniTrend from '@/components/MiniTrend.vue';
import ProgressMeter from '@/components/ProgressMeter.vue';
import { getElectricBoxes } from '@/api/electricBox';
import { exportInspectionRecords, getInspectionSummary, type InspectionMonthSummary } from '@/api/inspection';
import type { ElectricBox, InspectionPeriodMode, InspectionRecord } from '@/types';
import { usePageScrollHeight } from '@/utils/navLayout';
import { getQueryNumber, navigateTo, showToast, switchTab } from '@/utils/navigation';

const now = new Date();
const initialMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
const projectId = ref(1);
const boxId = ref<number>();
const queryBoxId = ref<number>();
const selectedMonth = ref(initialMonth);
const selectedDate = ref(currentDate());
const periodMode = ref<InspectionPeriodMode>('MONTH');
const summary = ref<InspectionMonthSummary>();
const boxes = ref<ElectricBox[]>([]);
const loading = ref(true);
const showBoxPicker = ref(false);
const { scrollStyle } = usePageScrollHeight({ minHeight: 240 });

const pageTitle = computed(() => boxId.value
  ? `本箱${periodMode.value === 'MONTH' ? '月度' : '当日'}汇总`
  : `项目${periodMode.value === 'MONTH' ? '月度' : '当日'}汇总`);
const monthLabel = computed(() => `${selectedMonth.value.slice(0, 4)}年${selectedMonth.value.slice(5, 7)}月`);
const selectedBox = computed(() => boxes.value.find((box) => box.id === boxId.value));
const selectedBoxLabel = computed(() => selectedBox.value
  ? `${selectedBox.value.boxCode} · ${selectedBox.value.boxName || selectedBox.value.installLocation}`
  : '全部电箱');
const activeSummary = computed<InspectionMonthSummary | undefined>(() => summary.value);
const completionRate = computed(() => activeSummary.value?.shouldCheck ? Number(((activeSummary.value.checked / activeSummary.value.shouldCheck) * 100).toFixed(1)) : 0);
const abnormalRate = computed(() => activeSummary.value?.checked ? Number(((activeSummary.value.abnormal / activeSummary.value.checked) * 100).toFixed(1)) : 0);
const missedRate = computed(() => activeSummary.value?.shouldCheck ? Number(((activeSummary.value.missed / activeSummary.value.shouldCheck) * 100).toFixed(1)) : 0);
const completionScopeText = computed(() => {
  const shouldCheck = activeSummary.value?.shouldCheck || 0;
  const checked = activeSummary.value?.checked || 0;
  if (periodMode.value === 'DAY') {
    return `应检 ${shouldCheck} 个电箱 · 已检 ${checked} 个电箱`;
  }
  if (boxId.value) {
    return `应检 ${shouldCheck} 天 · 已检 ${checked} 天`;
  }
  return `应检 ${shouldCheck} 箱次 · 已检 ${checked} 箱次`;
});
const secondaryStats = computed(() => [
  { label: periodMode.value === 'DAY' ? '已检电箱' : boxId.value ? '已检天数' : '已检箱次', value: activeSummary.value?.checked || 0, tone: 'green' },
  { label: periodMode.value === 'DAY' ? '未检电箱' : boxId.value ? '未检天数' : '未检箱次', value: activeSummary.value?.missed || 0, tone: 'red' },
  { label: periodMode.value === 'DAY' ? '异常电箱' : boxId.value ? '异常天数' : '异常箱次', value: activeSummary.value?.abnormal || 0, tone: 'amber' }
]);
const trendMeters = computed(() => [
  { label: '巡检完成率', value: completionRate.value, tone: 'green' as const },
  { label: '异常记录率', value: abnormalRate.value, tone: 'amber' as const },
  { label: periodMode.value === 'DAY' ? '未检率' : '漏检率', value: missedRate.value, tone: 'red' as const }
]);
const monthDays = computed(() => new Date(Number(selectedMonth.value.slice(0, 4)), Number(selectedMonth.value.slice(5, 7)), 0).getDate());
const dailyTrend = computed(() => {
  const values = Array.from({ length: monthDays.value }, () => 0);
  (activeSummary.value?.records || []).forEach((record) => {
    const day = Number(record.checkDate.slice(8, 10));
    if (day > 0 && day <= values.length) values[day - 1] += 1;
  });
  return values;
});
const dailyLabels = computed(() => Array.from({ length: monthDays.value }, (_, index) => `${selectedMonth.value.slice(5, 7)}-${String(index + 1).padStart(2, '0')}`));
const riskBoxes = computed(() => {
  const grouped = new Map<number, { id: number; code: string; name: string; abnormal: number; records: number }>();
  (activeSummary.value?.records || []).filter((record) => record.abnormalCount > 0 && record.electricBoxId).forEach((record) => {
    const id = Number(record.electricBoxId);
    const current = grouped.get(id) || { id, code: record.boxCode, name: record.boxName || '现场电箱', abnormal: 0, records: 0 };
    current.abnormal += record.abnormalCount;
    current.records += 1;
    grouped.set(id, current);
  });
  return [...grouped.values()].sort((left, right) => right.abnormal - left.abnormal).slice(0, 3);
});

onShow(async () => {
  const pages = getCurrentPages();
  const current = pages[pages.length - 1] as unknown as { options?: Record<string, string> };
  projectId.value = getQueryNumber(current.options?.projectId, 1);
  const parsedBoxId = Number(current.options?.boxId || '');
  queryBoxId.value = Number.isFinite(parsedBoxId) && parsedBoxId > 0 ? parsedBoxId : undefined;
  boxId.value = queryBoxId.value;
  if (current.options?.month && /^\d{4}-\d{2}$/.test(current.options.month)) selectedMonth.value = current.options.month;
  await loadSummary();
});

async function loadSummary() {
  loading.value = true;
  try {
    const summaryRequest = periodMode.value === 'DAY'
      ? { projectId: projectId.value, boxId: boxId.value, checkDate: selectedDate.value }
      : { projectId: projectId.value, boxId: boxId.value, month: selectedMonth.value };
    const [summaryResult, boxList] = await Promise.all([
      getInspectionSummary(summaryRequest),
      getElectricBoxes(projectId.value)
    ]);
    summary.value = summaryResult;
    boxes.value = boxList;
  }
  catch (error) { summary.value = undefined; showToast(error instanceof Error ? error.message : '巡检汇总加载失败'); }
  finally { loading.value = false; }
}

async function changeMonth(event: { detail?: { value?: string } }) { if (event.detail?.value) selectedMonth.value = event.detail.value.slice(0, 7); await loadSummary(); }
async function changeDate(event: { detail?: { value?: string } }) {
  const value = event.detail?.value;
  if (!value || value === selectedDate.value) return;
  if (value > currentDate()) { showToast('不能选择未来日期'); return; }
  selectedDate.value = value;
  await loadSummary();
}
async function switchPeriod(mode: InspectionPeriodMode) {
  if (periodMode.value === mode) return;
  if (mode === 'DAY') selectedDate.value = currentDate();
  else selectedMonth.value = selectedDate.value.slice(0, 7);
  periodMode.value = mode;
  await loadSummary();
}
async function selectBox(selectedId?: number) {
  if (queryBoxId.value) return;
  boxId.value = selectedId;
  await loadSummary();
}
function currentDate() {
  const date = new Date();
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}
function openRiskBox(id: number) { navigateTo(`/pages/inspection/records?projectId=${projectId.value}&boxId=${id}`); }
function openRecord(record: InspectionRecord) {
  if (record.id) navigateTo(`/pages/inspection/detail?id=${record.id}`);
}
function goBack() { getCurrentPages().length > 1 ? uni.navigateBack() : switchTab('/pages/inspection/index'); }
async function handleExport() {
  if (periodMode.value !== 'MONTH') return;
  try { const result = await exportInspectionRecords({ projectId: projectId.value, templateCode: 'ELECTRIC_BOX_DAILY', month: selectedMonth.value, boxId: boxId.value }); showToast(result.mock ? `已生成模拟导出：${result.fileName}` : '导出文件已生成'); }
  catch (error) { showToast(error instanceof Error ? error.message : '导出失败'); }
}
</script>

<template>
  <view class="flow-page summary-page">
    <AppNavBar :title="pageTitle" @back="goBack" />
    <scroll-view class="flow-scroll" scroll-y enable-flex :style="scrollStyle">
      <view class="flow-content summary-content">
        <view class="period-tabs">
          <button :class="{ active: periodMode === 'MONTH' }" @tap="switchPeriod('MONTH')">按月</button>
          <button :class="{ active: periodMode === 'DAY' }" @tap="switchPeriod('DAY')">按日</button>
        </view>
        <view class="tool-row">
          <picker v-if="periodMode === 'MONTH'" mode="date" fields="month" :value="selectedMonth" @change="changeMonth"><view class="month-button pressable"><text>{{ monthLabel }}</text><text class="month-arrow"></text></view></picker>
          <picker v-else mode="date" :value="selectedDate" :end="currentDate()" @change="changeDate"><view class="month-button pressable"><text>{{ selectedDate }}</text><text class="month-arrow"></text></view></picker>
          <button v-if="!queryBoxId" class="box-button pressable" @tap="showBoxPicker = true"><text>{{ selectedBoxLabel }}</text><text class="month-arrow"></text></button>
          <view v-else class="fixed-box">仅当前电箱</view>
          <button v-if="periodMode === 'MONTH'" class="export-button pressable" @tap="handleExport">导出</button>
        </view>

        <template v-if="loading"><view class="hero-skeleton flow-card"><view class="flow-skeleton hero-line"></view><view class="flow-skeleton hero-number"></view><view class="flow-skeleton hero-track"></view></view><view class="panel-skeleton flow-card flow-skeleton"></view><view class="panel-skeleton flow-card flow-skeleton"></view></template>

        <template v-else-if="activeSummary">
          <view class="completion-card flow-card"><view class="completion-copy"><text>{{ boxId ? '本箱完成率' : '项目完成率' }}</text><AnimatedNumber :value="completionRate" :decimals="1" suffix="%" /><text>{{ completionScopeText }}</text></view><view class="completion-badge" :class="{ warning: completionRate < 80 }"><text>{{ completionRate >= 90 ? '良好' : completionRate >= 80 ? '关注' : '待提升' }}</text></view><view class="completion-track"><view :style="{ width: `${Math.min(completionRate, 100)}%` }"></view></view></view>

          <view class="secondary-stats flow-card"><view v-for="stat in secondaryStats" :key="stat.label"><AnimatedNumber :value="stat.value" :class="stat.tone" /><text>{{ stat.label }}</text></view></view>

          <view class="panel flow-card"><view class="panel-head"><view><text>{{ periodMode === 'MONTH' ? '本月指标' : '当日指标' }}</text><text>数据随所选{{ periodMode === 'MONTH' ? '月份' : '日期' }}更新</text></view><text>{{ periodMode === 'MONTH' ? monthLabel : selectedDate }}</text></view><view class="meter-list"><ProgressMeter v-for="meter in trendMeters" :key="meter.label" :label="meter.label" :value="meter.value" :tone="meter.tone" /></view></view>

          <view v-if="periodMode === 'MONTH'" class="panel flow-card"><view class="panel-head"><view><text>每日巡检趋势</text><text>按记录日期自动聚合</text></view><text>共 {{ activeSummary.records.length }} 条</text></view><MiniTrend :key="selectedMonth" :values="dailyTrend" :labels="dailyLabels" /></view>

          <view v-else class="panel day-record-panel flow-card">
            <view class="panel-head"><view><text>当日巡检记录</text><text>{{ selectedDate }} 已完成的巡检</text></view><text>{{ activeSummary.records.length }} 条</text></view>
            <view v-if="activeSummary.records.length" class="day-record-list">
              <button v-for="record in activeSummary.records" :key="record.id || `${record.boxCode}-${record.checkDate}`" class="day-record pressable" @tap="openRecord(record)">
                <view><text>{{ record.boxCode }}</text><text>{{ record.boxName || record.installLocation || '现场电箱' }} · {{ record.inspectorName || '未记录巡检员' }}</text></view>
                <text class="day-result" :class="{ abnormal: Number(record.abnormalCount || 0) > 0 }">{{ Number(record.abnormalCount || 0) > 0 ? `异常 ${record.abnormalCount} 项` : '正常' }}</text>
                <text class="risk-arrow"></text>
              </button>
            </view>
            <view v-else class="no-risk">当日暂无巡检记录</view>
          </view>

          <view v-if="!boxId" class="panel risk-panel flow-card"><view class="panel-head"><view><text>{{ periodMode === 'MONTH' ? '本月' : '当日' }}风险电箱</text><text>按异常项数排名</text></view><text>TOP 3</text></view><view v-if="riskBoxes.length" class="risk-list"><button v-for="(risk, index) in riskBoxes" :key="risk.id" class="risk-row pressable stagger-item" :style="{ animationDelay: `${index * 45}ms` }" @tap="openRiskBox(risk.id)"><text class="risk-rank">{{ index + 1 }}</text><view><text>{{ risk.code }}</text><text>{{ risk.name }} · {{ risk.records }} 次异常记录</text></view><text class="risk-count">{{ risk.abnormal }} 项</text><text class="risk-arrow"></text></button></view><view v-else class="no-risk">{{ periodMode === 'MONTH' ? '当月' : '当日' }}暂无异常电箱</view></view>
        </template>
      </view>
    </scroll-view>
    <ElectricBoxPickerSheet
      :visible="showBoxPicker"
      :project-id="projectId"
      :selected-id="boxId"
      title="筛选汇总电箱"
      @close="showBoxPicker = false"
      @select="selectBox"
    />
  </view>
</template>

<style scoped src="../../styles/safety-flow.css"></style>
<style scoped>
.summary-content { display: flex; flex-direction: column; gap: 15rpx; padding-top: 16rpx; padding-bottom: 36rpx; }
.period-tabs { display: grid; grid-template-columns: 1fr 1fr; gap: 5rpx; padding: 5rpx; border-radius: 14rpx; background: #e8edf1; }
.period-tabs button { display: flex; height: 51rpx; align-items: center; justify-content: center; margin: 0; padding: 0; border-radius: 10rpx; background: transparent; color: #76818d; font-size: 20rpx; line-height: 1; }
.period-tabs button::after { border: 0; }
.period-tabs button.active { background: #fff; color: #9e5e24; box-shadow: 0 4rpx 12rpx rgba(99,70,42,.08); font-weight: 800; }
.tool-row { display: flex; align-items: center; gap: 10rpx; }
.tool-row picker { flex-shrink: 0; }
.month-button, .box-button, .fixed-box { display: flex; height: 64rpx; align-items: center; justify-content: space-between; gap: 14rpx; padding: 0 18rpx; border: 1rpx solid #e8e2da; border-radius: 15rpx; background: #fff; color: #3b4759; font-size: 21rpx; font-weight: 750; }
.box-button { min-width: 0; flex: 1; margin: 0; line-height: 1; text-align: left; }
.box-button text:first-child { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.box-button::after { border: 0; }
.fixed-box { justify-content: center; flex: 1; color: #9e5e24; background: #fff8ef; }
.month-arrow { width: 9rpx; height: 9rpx; flex-shrink: 0; border-right: 2rpx solid #9b7651; border-bottom: 2rpx solid #9b7651; transform: rotate(45deg) translateY(-3rpx); }
.export-button { width: 104rpx; height: 64rpx; flex-shrink: 0; margin: 0; border: 1rpx solid #e4d5c4; border-radius: 15rpx; background: #fff8ef; color: #a05d20; font-size: 21rpx; font-weight: 750; }
.export-button::after { border: 0; }
.completion-card { position: relative; overflow: hidden; padding: 24rpx; background: linear-gradient(135deg, #9e5e24, #bf7937); color: #fff; }.completion-card::after { position: absolute; top: -70rpx; right: -50rpx; width: 220rpx; height: 220rpx; border: 30rpx solid rgba(255,255,255,.07); border-radius: 50%; content: ''; }.completion-copy { position: relative; z-index: 1; }.completion-copy>text, .completion-copy :deep(text) { display: block; }.completion-copy>text:first-child { color: rgba(255,255,255,.76); font-size: 20rpx; }.completion-copy :deep(text) { margin-top: 6rpx; font-size: 50rpx; font-weight: 900; line-height: 1.08; }.completion-copy>text:last-child { margin-top: 8rpx; color: rgba(255,255,255,.72); font-size: 19rpx; }.completion-badge { position: absolute; z-index: 2; top: 25rpx; right: 24rpx; padding: 6rpx 13rpx; border-radius: 999rpx; background: rgba(229, 255, 241, .18); color: #e9fff3; font-size: 18rpx; }.completion-badge.warning { background: rgba(255, 239, 208, .17); color: #fff0d4; }.completion-track { position: relative; z-index: 2; height: 8rpx; margin-top: 22rpx; overflow: hidden; border-radius: 999rpx; background: rgba(255,255,255,.2); }.completion-track view { height: 100%; border-radius: 999rpx; background: #fff3df; transition: width 520ms cubic-bezier(.2,.8,.2,1); }
.secondary-stats { display: grid; grid-template-columns: repeat(3, 1fr); padding: 18rpx 7rpx; }.secondary-stats view { position: relative; text-align: center; }.secondary-stats view+view::before { position: absolute; top: 7rpx; bottom: 7rpx; left: 0; width: 1rpx; background: #eeeae4; content: ''; }.secondary-stats :deep(text) { display: block; color: #238361; font-size: 28rpx; font-weight: 900; }.secondary-stats :deep(text.red) { color: #bd4e46; }.secondary-stats :deep(text.amber) { color: #bd701f; }.secondary-stats view>text:last-child { margin-top: 4rpx; color: #858f9b; font-size: 18rpx; font-weight: 400; }
.panel { padding: 22rpx; }.panel-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 18rpx; }.panel-head view text { display: block; }.panel-head view text:first-child { font-size: 25rpx; font-weight: 850; }.panel-head view text:last-child { margin-top: 4rpx; color: #959ca5; font-size: 18rpx; }.panel-head>text { color: #a4774b; font-size: 18rpx; }.meter-list { display: flex; flex-direction: column; gap: 22rpx; margin-top: 22rpx; padding-top: 19rpx; border-top: 1rpx solid #f0ede8; }
.risk-list { margin-top: 16rpx; }.risk-row { display: grid; grid-template-columns: 39rpx 1fr auto 12rpx; align-items: center; gap: 12rpx; width: 100%; min-height: 78rpx; margin: 0; padding: 11rpx 0; background: transparent; text-align: left; }.risk-row+.risk-row { border-top: 1rpx solid #f0ede8; }.risk-row::after { border: 0; }.risk-rank { display: flex; width: 35rpx; height: 35rpx; align-items: center; justify-content: center; border-radius: 10rpx; background: #fff0df; color: #a96324; font-size: 18rpx; font-weight: 900; }.risk-row view text { display: block; }.risk-row view text:first-child { color: #303d50; font-size: 22rpx; font-weight: 800; }.risk-row view text:last-child { margin-top: 3rpx; color: #9299a3; font-size: 18rpx; }.risk-count { color: #bc4e46; font-size: 20rpx; font-weight: 800; }.risk-arrow { width: 9rpx; height: 9rpx; border-top: 2rpx solid #a49c93; border-right: 2rpx solid #a49c93; transform: rotate(45deg); }.no-risk { padding: 34rpx 0 12rpx; color: #8b958f; font-size: 21rpx; text-align: center; }
.day-record-list { margin-top: 16rpx; }
.day-record { display: grid; grid-template-columns: minmax(0, 1fr) auto 12rpx; align-items: center; gap: 12rpx; width: 100%; min-height: 82rpx; margin: 0; padding: 14rpx 0; background: transparent; text-align: left; }
.day-record + .day-record { border-top: 1rpx solid #f0ede8; }
.day-record::after { border: 0; }
.day-record view { min-width: 0; }
.day-record view text { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.day-record view text:first-child { color: #303d50; font-size: 22rpx; font-weight: 800; }
.day-record view text:last-child { margin-top: 4rpx; color: #9299a3; font-size: 18rpx; }
.day-result { flex-shrink: 0; padding: 5rpx 11rpx; border-radius: 999rpx; background: #e8f5ef; color: #24815f; font-size: 18rpx; font-weight: 750; }
.day-result.abnormal { background: #faecea; color: #b94c46; }
.hero-skeleton { padding: 25rpx; }.hero-line { width: 30%; height: 20rpx; }.hero-number { width: 38%; height: 54rpx; margin-top: 15rpx; }.hero-track { width: 100%; height: 8rpx; margin-top: 26rpx; }.panel-skeleton { height: 180rpx; } @media (prefers-reduced-motion: reduce) { .completion-track view { transition-duration: 1ms; } }
</style>
