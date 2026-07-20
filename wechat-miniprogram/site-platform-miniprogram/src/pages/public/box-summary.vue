<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import AppNavBar from '@/components/AppNavBar.vue';
import { getPublicElectricBoxSummary } from '@/api/electricBox';
import type { PublicElectricBoxSummary, PublicInspectionRecord } from '@/types';
import { showToast } from '@/utils/navigation';

const summary = ref<PublicElectricBoxSummary>();
const loading = ref(true);
const ONE_DAY = 24 * 60 * 60 * 1000;

onMounted(async () => {
  const pages = getCurrentPages();
  const current = pages[pages.length - 1] as unknown as { options?: Record<string, string> };
  const publicCode = current.options?.publicCode || current.options?.code || 'PUB-001';
  try {
    summary.value = await getPublicElectricBoxSummary(publicCode);
  } catch (error) {
    showToast(error instanceof Error ? error.message : '公开信息加载失败');
  } finally {
    loading.value = false;
  }
});

const latestRecord = computed<PublicInspectionRecord | undefined>(() => summary.value?.recentRecords?.[0]);

const latestResult = computed(() => {
  if (!latestRecord.value) return { label: '暂无', tone: 'gray' };
  return latestRecord.value.abnormalCount > 0
    ? { label: '异常', tone: 'red' }
    : { label: '正常', tone: 'green' };
});

const rectificationLabel = computed(() => {
  if (!summary.value) return '暂无信息';
  return summary.value.openRectificationCount > 0 ? '当前整改中' : '当前正常';
});

const dateRangeText = computed(() => {
  const end = summary.value?.rangeEndDate || formatDate(new Date());
  const start = summary.value?.rangeStartDate || formatDate(new Date(new Date(`${end}T00:00:00`).getTime() - 29 * ONE_DAY));
  return `(${start} - ${end})`;
});

function formatDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function goBack() {
  if (getCurrentPages().length > 1) {
    uni.navigateBack();
    return;
  }
  uni.reLaunch({
    url: '/pages/login/index',
    fail: () => undefined
  });
}
</script>

<template>
  <view class="public-shell">
    <AppNavBar title="电箱信息（只读）" @back="goBack" />

    <view v-if="loading" class="public-content">
      <view class="panel loading-card">
        <text class="loading-text">正在加载公开巡检信息...</text>
      </view>
    </view>

    <view v-else-if="summary" class="public-content">
      <view class="box-panel">
        <view class="box-header">
          <text class="project-name">{{ summary.projectShortName }}</text>
          <text class="readonly-label">脱敏只读</text>
        </view>
        <view class="box-info">
          <view class="info-row">
            <text class="info-label">电箱编号</text>
            <text class="info-value strong">{{ summary.boxCode }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">安装位置</text>
            <text class="info-value">{{ summary.installLocation }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">电箱类型</text>
            <text class="info-value">{{ summary.boxName || '二级电箱 1' }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">负责人</text>
            <text class="info-value">不公开</text>
          </view>
        </view>
      </view>

      <view class="panel latest-panel">
        <text class="panel-title">最新检查</text>
        <view class="info-row">
          <text class="info-label">检查时间</text>
          <text class="info-value">{{ summary.latestCheckDate || latestRecord?.inspectedAt || '暂无记录' }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">检查人</text>
          <text class="info-value">已脱敏</text>
        </view>
        <view class="info-row result-row">
          <text class="info-label">检查结果</text>
          <text class="result-pill" :class="latestResult.tone">{{ latestResult.label }}</text>
        </view>
      </view>

      <view class="panel stats-panel">
        <view class="stats-title-row">
          <text class="panel-title">近30天统计</text>
          <text class="date-range">{{ dateRangeText }}</text>
        </view>
        <view class="stats-grid">
          <view class="stat-item">
            <text class="stat-label">应检</text>
            <text class="stat-value dark">{{ summary.shouldCheckDays }}</text>
          </view>
          <view class="stat-item">
            <text class="stat-label">已检</text>
            <text class="stat-value green">{{ summary.checkedDays }}</text>
          </view>
          <view class="stat-item">
            <text class="stat-label">异常</text>
            <text class="stat-value red">{{ summary.abnormalCount }}</text>
          </view>
          <view class="stat-item">
            <text class="stat-label">未闭环</text>
            <text class="stat-value dark">{{ summary.openRectificationCount }}</text>
          </view>
        </view>
      </view>

      <view class="panel rectification-panel">
        <text class="panel-title">整改状态</text>
        <text class="rectification-pill">{{ rectificationLabel }}</text>
      </view>

      <view class="notice">
        <text class="notice-icon">i</text>
        <text class="notice-text">此页只读预览，不包含完整信息</text>
      </view>
    </view>
  </view>
</template>

<style scoped>
.public-shell {
  min-height: 100vh;
  background: #eef7ff;
  color: #0f172a;
}

.public-nav {
  padding: 40rpx 58rpx 0;
  background: #ffffff;
}

.status-row {
  display: flex;
  height: 50rpx;
  align-items: center;
  justify-content: space-between;
  padding: 0 8rpx;
}

.clock {
  color: #000000;
  font-size: 30rpx;
  font-weight: 800;
  line-height: 1;
}

.phone-indicators {
  display: flex;
  align-items: center;
  gap: 14rpx;
  color: #000000;
}

.signal-bars {
  position: relative;
  width: 38rpx;
  height: 28rpx;
}

.signal-bars::before {
  position: absolute;
  right: 0;
  bottom: 2rpx;
  width: 7rpx;
  height: 26rpx;
  border-radius: 999rpx;
  background: currentColor;
  box-shadow: -10rpx 6rpx 0 0 currentColor, -20rpx 12rpx 0 0 currentColor, -30rpx 17rpx 0 0 currentColor;
  content: "";
}

.wifi-mark {
  position: relative;
  width: 34rpx;
  height: 25rpx;
  overflow: hidden;
}

.wifi-mark::before {
  position: absolute;
  left: 1rpx;
  top: -7rpx;
  width: 31rpx;
  height: 31rpx;
  border: 7rpx solid currentColor;
  border-right-color: transparent;
  border-bottom-color: transparent;
  border-radius: 50%;
  transform: rotate(45deg);
  content: "";
}

.wifi-mark::after {
  position: absolute;
  left: 12rpx;
  bottom: 2rpx;
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: currentColor;
  content: "";
}

.battery-mark {
  position: relative;
  width: 43rpx;
  height: 22rpx;
  border: 3rpx solid currentColor;
  border-radius: 5rpx;
}

.battery-mark::before {
  position: absolute;
  top: 3rpx;
  right: 3rpx;
  bottom: 3rpx;
  left: 3rpx;
  border-radius: 3rpx;
  background: currentColor;
  content: "";
}

.battery-mark::after {
  position: absolute;
  right: -8rpx;
  top: 6rpx;
  width: 4rpx;
  height: 10rpx;
  border-radius: 0 999rpx 999rpx 0;
  background: currentColor;
  content: "";
}

.nav-row {
  position: relative;
  display: flex;
  height: 136rpx;
  align-items: center;
  justify-content: center;
}

.back-button {
  position: absolute;
  left: 0;
  top: 50%;
  width: 64rpx;
  height: 64rpx;
  margin: 0;
  padding: 0;
  border: 0;
  background: transparent;
  transform: translateY(-50%);
}

.back-button::after {
  border: 0;
}

.back-icon {
  position: absolute;
  left: 16rpx;
  top: 17rpx;
  width: 27rpx;
  height: 27rpx;
  border-bottom: 6rpx solid #0f172a;
  border-left: 6rpx solid #0f172a;
  transform: rotate(45deg);
}

.nav-title {
  color: #111827;
  font-size: 32rpx;
  font-weight: 900;
  line-height: 1;
}

.public-content {
  display: flex;
  flex-direction: column;
  gap: 28rpx;
  padding: 18rpx 36rpx 24rpx;
}

.panel,
.box-panel {
  overflow: hidden;
  border: 2rpx solid #dfe7f2;
  border-radius: 14rpx;
  background: #ffffff;
  box-shadow: 0 8rpx 18rpx rgba(32, 49, 78, 0.035);
}

.loading-card {
  display: flex;
  min-height: 180rpx;
  align-items: center;
  justify-content: center;
}

.loading-text {
  color: #64748b;
  font-size: 26rpx;
}

.box-header {
  display: flex;
  min-height: 102rpx;
  align-items: center;
  justify-content: space-between;
  padding: 0 34rpx;
  background: #0f9f8f;
}

.project-name {
  color: #ffffff;
  font-size: 34rpx;
  font-weight: 900;
  line-height: 1;
}

.readonly-label {
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 800;
  line-height: 1;
}

.box-info,
.latest-panel,
.stats-panel {
  display: flex;
  flex-direction: column;
}

.box-info {
  gap: 34rpx;
  padding: 43rpx 34rpx 48rpx;
}

.latest-panel {
  gap: 34rpx;
  padding: 36rpx 34rpx 40rpx;
}

.info-row {
  display: grid;
  grid-template-columns: 178rpx 1fr;
  align-items: center;
  gap: 28rpx;
}

.info-label {
  color: #64748b;
  font-size: 30rpx;
  font-weight: 600;
  line-height: 1.15;
}

.info-value {
  color: #111827;
  font-size: 30rpx;
  font-weight: 800;
  line-height: 1.15;
}

.info-value.strong {
  font-size: 34rpx;
  font-weight: 900;
}

.panel-title {
  color: #111827;
  font-size: 32rpx;
  font-weight: 900;
  line-height: 1;
}

.result-row {
  align-items: center;
}

.result-pill {
  display: inline-flex;
  width: fit-content;
  min-height: 58rpx;
  align-items: center;
  justify-content: center;
  padding: 0 18rpx;
  border-radius: 9rpx;
  font-size: 30rpx;
  font-weight: 900;
  line-height: 1;
}

.result-pill.green {
  background: #d8f4e8;
  color: #0f9f8f;
}

.result-pill.red {
  background: #fee2e2;
  color: #b80000;
}

.result-pill.gray {
  background: #eef2f7;
  color: #475569;
}

.stats-panel {
  gap: 48rpx;
  padding: 36rpx 34rpx 42rpx;
}

.stats-title-row {
  display: flex;
  align-items: center;
  gap: 28rpx;
}

.date-range {
  color: #64748b;
  font-size: 27rpx;
  font-weight: 600;
  line-height: 1;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 20rpx;
}

.stat-label,
.stat-value {
  display: block;
  text-align: center;
}

.stat-label {
  color: #64748b;
  font-size: 28rpx;
  font-weight: 600;
  line-height: 1;
}

.stat-value {
  margin-top: 26rpx;
  font-size: 42rpx;
  font-weight: 900;
  line-height: 1;
}

.stat-value.dark {
  color: #111827;
}

.stat-value.green {
  color: #0f9f8f;
}

.stat-value.red {
  color: #e00012;
}

.rectification-panel {
  display: flex;
  min-height: 122rpx;
  align-items: center;
  gap: 48rpx;
  padding: 0 34rpx;
}

.rectification-pill {
  display: inline-flex;
  min-height: 58rpx;
  align-items: center;
  justify-content: center;
  padding: 0 20rpx;
  border: 2rpx solid #fed7aa;
  border-radius: 9rpx;
  background: #fff7ed;
  color: #d97706;
  font-size: 28rpx;
  font-weight: 900;
  line-height: 1;
}

.notice {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 22rpx;
  padding: 50rpx 0 0;
}

.notice-icon {
  display: inline-flex;
  width: 44rpx;
  height: 44rpx;
  align-items: center;
  justify-content: center;
  border: 4rpx solid #475569;
  border-radius: 50%;
  color: #475569;
  font-size: 27rpx;
  font-weight: 900;
  line-height: 1;
}

.notice-text {
  color: #64748b;
  font-size: 28rpx;
  font-weight: 600;
  line-height: 1;
}
</style>
