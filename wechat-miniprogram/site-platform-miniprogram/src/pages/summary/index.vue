<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import AnimatedNumber from '@/components/AnimatedNumber.vue';
import MiniTrend from '@/components/MiniTrend.vue';
import ProgressMeter from '@/components/ProgressMeter.vue';
import { exportInspectionRecords, getInspectionSummary, type InspectionMonthSummary } from '@/api/inspection';
import { usePageScrollHeight } from '@/utils/navLayout';
import { getQueryNumber, navigateTo, showToast, switchTab } from '@/utils/navigation';

const now = new Date();
const initialMonth = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
const projectId = ref(1);
const boxId = ref<number>();
const selectedMonth = ref(initialMonth);
const summary = ref<InspectionMonthSummary>();
const loading = ref(true);
const { scrollStyle } = usePageScrollHeight({ minHeight: 240 });

const pageTitle = computed(() => boxId.value ? '本箱月度汇总' : '项目月度汇总');
const monthLabel = computed(() => `${selectedMonth.value.slice(0, 4)}年${selectedMonth.value.slice(5, 7)}月`);
const completionRate = computed(() => summary.value?.shouldCheck ? Number(((summary.value.checked / summary.value.shouldCheck) * 100).toFixed(1)) : 0);
const abnormalRate = computed(() => summary.value?.checked ? Number(((summary.value.abnormal / summary.value.checked) * 100).toFixed(1)) : 0);
const missedRate = computed(() => summary.value?.shouldCheck ? Number(((summary.value.missed / summary.value.shouldCheck) * 100).toFixed(1)) : 0);
const secondaryStats = computed(() => [
  { label: '已检', value: summary.value?.checked || 0, tone: 'green' },
  { label: '漏检', value: summary.value?.missed || 0, tone: 'red' },
  { label: '异常', value: summary.value?.abnormal || 0, tone: 'amber' }
]);
const trendMeters = computed(() => [
  { label: '巡检完成率', value: completionRate.value, tone: 'green' as const },
  { label: '异常记录率', value: abnormalRate.value, tone: 'amber' as const },
  { label: '漏检率', value: missedRate.value, tone: 'red' as const }
]);
const monthDays = computed(() => new Date(Number(selectedMonth.value.slice(0, 4)), Number(selectedMonth.value.slice(5, 7)), 0).getDate());
const dailyTrend = computed(() => {
  const values = Array.from({ length: monthDays.value }, () => 0);
  (summary.value?.records || []).forEach((record) => {
    const day = Number(record.checkDate.slice(8, 10));
    if (day > 0 && day <= values.length) values[day - 1] += 1;
  });
  return values;
});
const dailyLabels = computed(() => Array.from({ length: monthDays.value }, (_, index) => `${selectedMonth.value.slice(5, 7)}-${String(index + 1).padStart(2, '0')}`));
const riskBoxes = computed(() => {
  const grouped = new Map<number, { id: number; code: string; name: string; abnormal: number; records: number }>();
  (summary.value?.records || []).filter((record) => record.abnormalCount > 0 && record.electricBoxId).forEach((record) => {
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
  boxId.value = Number.isFinite(parsedBoxId) && parsedBoxId > 0 ? parsedBoxId : undefined;
  if (current.options?.month && /^\d{4}-\d{2}$/.test(current.options.month)) selectedMonth.value = current.options.month;
  await loadSummary();
});

async function loadSummary() {
  loading.value = true;
  try { summary.value = await getInspectionSummary({ projectId: projectId.value, boxId: boxId.value, month: selectedMonth.value }); }
  catch (error) { summary.value = undefined; showToast(error instanceof Error ? error.message : '巡检汇总加载失败'); }
  finally { loading.value = false; }
}

async function changeMonth(event: { detail?: { value?: string } }) { if (event.detail?.value) selectedMonth.value = event.detail.value.slice(0, 7); await loadSummary(); }
function openRiskBox(id: number) { navigateTo(`/pages/inspection/records?projectId=${projectId.value}&boxId=${id}`); }
function goBack() { getCurrentPages().length > 1 ? uni.navigateBack() : switchTab('/pages/inspection/index'); }
async function handleExport() {
  try { const result = await exportInspectionRecords({ projectId: projectId.value, templateCode: 'ELECTRIC_BOX_DAILY', month: selectedMonth.value, boxId: boxId.value }); showToast(result.mock ? `已生成模拟导出：${result.fileName}` : '导出文件已生成'); }
  catch (error) { showToast(error instanceof Error ? error.message : '导出失败'); }
}
</script>

<template>
  <view class="flow-page summary-page">
    <AppNavBar :title="pageTitle" @back="goBack" />
    <scroll-view class="flow-scroll" scroll-y enable-flex :style="scrollStyle">
      <view class="flow-content summary-content">
        <view class="tool-row"><picker mode="date" fields="month" :value="selectedMonth" @change="changeMonth"><view class="month-button pressable"><text>{{ monthLabel }}</text><text class="month-arrow"></text></view></picker><button class="export-button pressable" @tap="handleExport">导出</button></view>

        <template v-if="loading"><view class="hero-skeleton flow-card"><view class="flow-skeleton hero-line"></view><view class="flow-skeleton hero-number"></view><view class="flow-skeleton hero-track"></view></view><view class="panel-skeleton flow-card flow-skeleton"></view><view class="panel-skeleton flow-card flow-skeleton"></view></template>

        <template v-else-if="summary">
          <view class="completion-card flow-card"><view class="completion-copy"><text>{{ boxId ? '本箱完成率' : '项目完成率' }}</text><AnimatedNumber :value="completionRate" :decimals="1" suffix="%" /><text>应检 {{ summary.shouldCheck }} 次 · 已检 {{ summary.checked }} 次</text></view><view class="completion-badge" :class="{ warning: completionRate < 80 }"><text>{{ completionRate >= 90 ? '良好' : completionRate >= 80 ? '关注' : '待提升' }}</text></view><view class="completion-track"><view :style="{ width: `${Math.min(completionRate, 100)}%` }"></view></view></view>

          <view class="secondary-stats flow-card"><view v-for="stat in secondaryStats" :key="stat.label"><AnimatedNumber :value="stat.value" :class="stat.tone" /><text>{{ stat.label }}</text></view></view>

          <view class="panel flow-card"><view class="panel-head"><view><text>本月趋势</text><text>数据随选定月份更新</text></view><text>{{ monthLabel }}</text></view><view class="meter-list"><ProgressMeter v-for="meter in trendMeters" :key="meter.label" :label="meter.label" :value="meter.value" :tone="meter.tone" /></view></view>

          <view class="panel flow-card"><view class="panel-head"><view><text>每日巡检趋势</text><text>按记录日期自动聚合</text></view><text>共 {{ summary.records.length }} 条</text></view><MiniTrend :key="selectedMonth" :values="dailyTrend" :labels="dailyLabels" /></view>

          <view v-if="!boxId" class="panel risk-panel flow-card"><view class="panel-head"><view><text>本月风险电箱</text><text>按异常项数排名</text></view><text>TOP 3</text></view><view v-if="riskBoxes.length" class="risk-list"><button v-for="(risk, index) in riskBoxes" :key="risk.id" class="risk-row pressable stagger-item" :style="{ animationDelay: `${index * 45}ms` }" @tap="openRiskBox(risk.id)"><text class="risk-rank">{{ index + 1 }}</text><view><text>{{ risk.code }}</text><text>{{ risk.name }} · {{ risk.records }} 次异常记录</text></view><text class="risk-count">{{ risk.abnormal }} 项</text><text class="risk-arrow"></text></button></view><view v-else class="no-risk">当月暂无异常电箱</view></view>
        </template>
      </view>
    </scroll-view>
  </view>
</template>

<style scoped src="../../styles/safety-flow.css"></style>
<style scoped>
.summary-content { display: flex; flex-direction: column; gap: 15rpx; padding-top: 16rpx; padding-bottom: 36rpx; }.tool-row { display: flex; align-items: center; justify-content: space-between; gap: 14rpx; }.month-button { display: flex; height: 64rpx; align-items: center; gap: 14rpx; padding: 0 18rpx; border: 1rpx solid #e8e2da; border-radius: 15rpx; background: #fff; color: #3b4759; font-size: 22rpx; font-weight: 750; }.month-arrow { width: 9rpx; height: 9rpx; border-right: 2rpx solid #9b7651; border-bottom: 2rpx solid #9b7651; transform: rotate(45deg) translateY(-3rpx); }.export-button { width: 112rpx; height: 64rpx; margin: 0; border: 1rpx solid #e4d5c4; border-radius: 15rpx; background: #fff8ef; color: #a05d20; font-size: 21rpx; font-weight: 750; }.export-button::after { border: 0; }
.completion-card { position: relative; overflow: hidden; padding: 24rpx; background: linear-gradient(135deg, #9e5e24, #bf7937); color: #fff; }.completion-card::after { position: absolute; top: -70rpx; right: -50rpx; width: 220rpx; height: 220rpx; border: 30rpx solid rgba(255,255,255,.07); border-radius: 50%; content: ''; }.completion-copy { position: relative; z-index: 1; }.completion-copy>text, .completion-copy :deep(text) { display: block; }.completion-copy>text:first-child { color: rgba(255,255,255,.76); font-size: 20rpx; }.completion-copy :deep(text) { margin-top: 6rpx; font-size: 50rpx; font-weight: 900; line-height: 1.08; }.completion-copy>text:last-child { margin-top: 8rpx; color: rgba(255,255,255,.72); font-size: 19rpx; }.completion-badge { position: absolute; z-index: 2; top: 25rpx; right: 24rpx; padding: 6rpx 13rpx; border-radius: 999rpx; background: rgba(229, 255, 241, .18); color: #e9fff3; font-size: 18rpx; }.completion-badge.warning { background: rgba(255, 239, 208, .17); color: #fff0d4; }.completion-track { position: relative; z-index: 2; height: 8rpx; margin-top: 22rpx; overflow: hidden; border-radius: 999rpx; background: rgba(255,255,255,.2); }.completion-track view { height: 100%; border-radius: 999rpx; background: #fff3df; transition: width 520ms cubic-bezier(.2,.8,.2,1); }
.secondary-stats { display: grid; grid-template-columns: repeat(3, 1fr); padding: 18rpx 7rpx; }.secondary-stats view { position: relative; text-align: center; }.secondary-stats view+view::before { position: absolute; top: 7rpx; bottom: 7rpx; left: 0; width: 1rpx; background: #eeeae4; content: ''; }.secondary-stats :deep(text) { display: block; color: #238361; font-size: 28rpx; font-weight: 900; }.secondary-stats :deep(text.red) { color: #bd4e46; }.secondary-stats :deep(text.amber) { color: #bd701f; }.secondary-stats view>text:last-child { margin-top: 4rpx; color: #858f9b; font-size: 18rpx; font-weight: 400; }
.panel { padding: 22rpx; }.panel-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 18rpx; }.panel-head view text { display: block; }.panel-head view text:first-child { font-size: 25rpx; font-weight: 850; }.panel-head view text:last-child { margin-top: 4rpx; color: #959ca5; font-size: 18rpx; }.panel-head>text { color: #a4774b; font-size: 18rpx; }.meter-list { display: flex; flex-direction: column; gap: 22rpx; margin-top: 22rpx; padding-top: 19rpx; border-top: 1rpx solid #f0ede8; }
.risk-list { margin-top: 16rpx; }.risk-row { display: grid; grid-template-columns: 39rpx 1fr auto 12rpx; align-items: center; gap: 12rpx; width: 100%; min-height: 78rpx; margin: 0; padding: 11rpx 0; background: transparent; text-align: left; }.risk-row+.risk-row { border-top: 1rpx solid #f0ede8; }.risk-row::after { border: 0; }.risk-rank { display: flex; width: 35rpx; height: 35rpx; align-items: center; justify-content: center; border-radius: 10rpx; background: #fff0df; color: #a96324; font-size: 18rpx; font-weight: 900; }.risk-row view text { display: block; }.risk-row view text:first-child { color: #303d50; font-size: 22rpx; font-weight: 800; }.risk-row view text:last-child { margin-top: 3rpx; color: #9299a3; font-size: 18rpx; }.risk-count { color: #bc4e46; font-size: 20rpx; font-weight: 800; }.risk-arrow { width: 9rpx; height: 9rpx; border-top: 2rpx solid #a49c93; border-right: 2rpx solid #a49c93; transform: rotate(45deg); }.no-risk { padding: 34rpx 0 12rpx; color: #8b958f; font-size: 21rpx; text-align: center; }
.hero-skeleton { padding: 25rpx; }.hero-line { width: 30%; height: 20rpx; }.hero-number { width: 38%; height: 54rpx; margin-top: 15rpx; }.hero-track { width: 100%; height: 8rpx; margin-top: 26rpx; }.panel-skeleton { height: 180rpx; } @media (prefers-reduced-motion: reduce) { .completion-track view { transition-duration: 1ms; } }
</style>
