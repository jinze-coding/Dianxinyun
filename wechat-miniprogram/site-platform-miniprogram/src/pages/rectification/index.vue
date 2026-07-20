<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import EmptyState from '@/components/EmptyState.vue';
import { getRectificationList } from '@/api/rectification';
import { formatSpotCheckCategory } from '@/constants/spotCheck';
import type { RectificationStatus, RectificationTask } from '@/types';
import { usePageScrollHeight } from '@/utils/navLayout';
import { getQueryNumber, navigateTo, showToast, switchTab } from '@/utils/navigation';

type RectificationFilter = '' | RectificationStatus;

const projectId = ref(1);
const loading = ref(false);
const filter = ref<RectificationFilter>('');
const tasks = ref<RectificationTask[]>([]);
const { scrollStyle } = usePageScrollHeight({ minHeight: 260 });

const filters: Array<{ label: string; value: RectificationFilter }> = [
  { label: '全部', value: '' },
  { label: '整改中', value: 'PENDING' },
  { label: '待复查', value: 'COMPLETED' },
  { label: '已关闭', value: 'CLOSED' },
  { label: '已退回', value: 'REJECTED' }
];

const stats = computed(() => [
  { label: '整改中', value: countStatus('PENDING'), tone: 'red' },
  { label: '待复查', value: countStatus('COMPLETED'), tone: 'amber' },
  { label: '已关闭', value: countStatus('CLOSED'), tone: 'green' },
  { label: '已退回', value: countStatus('REJECTED'), tone: 'slate' }
]);

const visibleTasks = computed(() => {
  const filtered = filter.value ? tasks.value.filter((item) => item.status === filter.value) : tasks.value;
  return [...filtered].sort((a, b) => {
    if (a.status !== b.status) {
      return statusWeight(a.status) - statusWeight(b.status);
    }
    const aTime = parseTime(a.deadline || a.createdAt) || 0;
    const bTime = parseTime(b.deadline || b.createdAt) || 0;
    return aTime - bTime || b.id - a.id;
  });
});

onShow(async () => {
  const pages = getCurrentPages();
  const current = pages[pages.length - 1] as unknown as { options?: Record<string, string> };
  projectId.value = getQueryNumber(current.options?.projectId, projectId.value || 1);
  await loadTasks();
});

async function loadTasks() {
  loading.value = true;
  try {
    tasks.value = await getRectificationList({ projectId: projectId.value });
  } catch (error) {
    tasks.value = [];
    showToast(error instanceof Error ? error.message : '整改任务加载失败');
  } finally {
    loading.value = false;
  }
}

function countStatus(status: RectificationStatus) {
  return tasks.value.filter((item) => item.status === status).length;
}

function statusWeight(status: RectificationStatus) {
  if (status === 'PENDING') return 1;
  if (status === 'REJECTED') return 2;
  if (status === 'COMPLETED') return 3;
  return 4;
}

function statusLabel(status: RectificationStatus) {
  if (status === 'COMPLETED') return '待复查';
  if (status === 'CLOSED') return '已关闭';
  if (status === 'REJECTED') return '已退回';
  return '整改中';
}

function statusClass(status: RectificationStatus) {
  if (status === 'COMPLETED') return 'amber';
  if (status === 'CLOSED') return 'green';
  if (status === 'REJECTED') return 'slate';
  return 'red';
}

function escalationLabel(task: RectificationTask) {
  if (task.escalationStatus === 'ESCALATED') return '已升级';
  if (task.escalationStatus === 'REMINDED') return '已提醒';
  return '';
}

function isOverdue(task: RectificationTask) {
  if (!['PENDING', 'REJECTED'].includes(task.status) || !task.deadline) {
    return false;
  }
  const deadline = parseTime(task.deadline, true);
  return Boolean(deadline) && deadline < Date.now();
}

function parseTime(value?: string, endOfDay = false) {
  if (!value) return 0;
  const hasTime = value.includes('T') || value.includes(' ');
  const normalized = (hasTime ? value : `${value}${endOfDay ? ' 23:59:59' : ' 00:00:00'}`)
    .replace('T', ' ')
    .replace(/-/g, '/');
  const time = new Date(normalized).getTime();
  return Number.isFinite(time) ? time : 0;
}

function dateText(value?: string) {
  if (!value) return '-';
  return value.replace('T', ' ').slice(0, 16);
}

function goBack() {
  if (getCurrentPages().length > 1) {
    uni.navigateBack();
    return;
  }
  switchTab('/pages/safety/index');
}

function openTask(task: RectificationTask) {
  navigateTo(`/pages/rectification/detail?id=${task.id}&from=project&projectId=${projectId.value}`);
}
</script>

<template>
  <view class="rect-list-shell">
    <AppNavBar title="整改闭环" @back="goBack" />

    <scroll-view class="rect-scroll" scroll-y enable-flex :style="scrollStyle">
      <view class="rect-content">
        <view class="summary-card">
          <view class="summary-head">
            <view>
              <text class="summary-title">项目整改闭环</text>
              <text class="summary-subtitle">当前项目全部整改任务</text>
            </view>
            <button class="refresh-button" @tap="loadTasks">刷新</button>
          </view>

          <view class="stats-grid">
            <view v-for="stat in stats" :key="stat.label" class="stat-item">
              <text class="stat-value" :class="stat.tone">{{ stat.value }}</text>
              <text class="stat-label">{{ stat.label }}</text>
            </view>
          </view>
        </view>

        <view class="filter-tabs">
          <button
            v-for="item in filters"
            :key="item.label"
            class="filter-tab"
            :class="{ active: filter === item.value }"
            @tap="filter = item.value"
          >
            {{ item.label }}
          </button>
        </view>

        <view v-if="loading" class="state-card">
          <text class="state-title">正在加载整改任务</text>
          <text class="state-desc">正在同步当前项目整改闭环数据</text>
        </view>

        <view v-else-if="visibleTasks.length" class="task-list">
          <view
            v-for="task in visibleTasks"
            :key="task.id"
            class="task-card"
            @tap="openTask(task)"
          >
            <view class="task-head">
              <view class="task-title-block">
                <text class="box-code">{{ task.boxCode }}</text>
                <text class="box-name">{{ task.boxName || '电箱整改' }}</text>
              </view>
              <view class="status-stack">
                <text class="status-pill" :class="statusClass(task.status)">{{ statusLabel(task.status) }}</text>
                <text v-if="isOverdue(task)" class="overdue-pill">逾期</text>
                <text v-if="escalationLabel(task)" class="escalation-pill">{{ escalationLabel(task) }}</text>
              </view>
            </view>

            <view class="problem-box">
              <text class="problem-label">问题描述 · {{ formatSpotCheckCategory(task.problemCategory) }}</text>
              <text class="problem-text">{{ task.problemDesc || '未填写问题描述' }}</text>
            </view>

            <view class="meta-grid">
              <view class="meta-item">
                <text class="meta-label">整改人</text>
                <text class="meta-value">{{ task.assigneeName || '未指定' }}</text>
              </view>
              <view class="meta-item">
                <text class="meta-label">截止时间</text>
                <text class="meta-value" :class="{ danger: isOverdue(task) }">{{ dateText(task.deadline) }}</text>
              </view>
              <view class="meta-item wide">
                <text class="meta-label">整改单号</text>
                <text class="meta-value">{{ task.orderNo || `ZG-${task.id}` }}</text>
              </view>
            </view>

            <text class="card-arrow"></text>
          </view>
        </view>

        <EmptyState
          v-else
          title="暂无整改任务"
          description="当前筛选条件下没有整改闭环记录"
        />
      </view>
    </scroll-view>

  </view>
</template>

<style scoped>
.rect-list-shell {
  height: 100vh;
  min-height: 100vh;
  overflow: hidden;
  background: #f4f3ef;
  color: #172033;
}

.rect-scroll {
  width: 100%;
}

.rect-content {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 24rpx 24rpx 28rpx;
  animation: page-fade-up 0.32s cubic-bezier(0.21, 0.75, 0.32, 1) both;
}

.summary-card,
.task-card,
.state-card {
  border: 1rpx solid #eadfd2;
  border-radius: 28rpx;
  background:
    radial-gradient(circle at 100% 0%, rgba(190, 111, 38, 0.09), transparent 34%),
    linear-gradient(145deg, #ffffff, #fffaf5);
  box-shadow: 0 14rpx 34rpx rgba(117, 79, 40, 0.08);
}

.summary-card {
  padding: 28rpx;
}

.summary-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18rpx;
}

.summary-title,
.summary-subtitle {
  display: block;
}

.summary-title {
  color: #172033;
  font-size: 32rpx;
  font-weight: 800;
  line-height: 1.25;
}

.summary-subtitle {
  margin-top: 8rpx;
  color: #6b7f99;
  font-size: 24rpx;
  font-weight: 500;
}

.refresh-button {
  min-width: 96rpx;
  min-height: 52rpx;
  padding: 0 22rpx;
  border: 1rpx solid #9bc7fb;
  border-radius: 18rpx;
  background: #eef7ff;
  color: #1677ff;
  font-size: 24rpx;
  font-weight: 700;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14rpx;
  margin-top: 26rpx;
}

.stat-item {
  display: flex;
  min-height: 108rpx;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 10rpx;
  border-radius: 22rpx;
  background: #edf6ff;
}

.stat-value {
  font-size: 36rpx;
  font-weight: 900;
  line-height: 1;
}

.stat-value.red {
  color: #ef4444;
}

.stat-value.amber {
  color: #f59e0b;
}

.stat-value.green {
  color: #0f9f8f;
}

.stat-value.slate {
  color: #64748b;
}

.stat-label {
  color: #6b7f99;
  font-size: 23rpx;
  font-weight: 600;
  line-height: 1;
}

.filter-tabs {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10rpx;
  padding: 8rpx;
  border: 1rpx solid #cde3fb;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.72);
}

.filter-tab {
  min-height: 58rpx;
  border-radius: 18rpx;
  color: #64748b;
  font-size: 23rpx;
  font-weight: 700;
}

.filter-tab.active {
  background: linear-gradient(135deg, #1677ff, #0f9f8f);
  color: #ffffff;
  box-shadow: 0 10rpx 24rpx rgba(22, 119, 255, 0.18);
}

.state-card {
  display: flex;
  min-height: 210rpx;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 12rpx;
  padding: 32rpx;
}

.state-title {
  color: #172033;
  font-size: 28rpx;
  font-weight: 800;
}

.state-desc {
  color: #6b7f99;
  font-size: 24rpx;
  font-weight: 500;
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.task-card {
  position: relative;
  padding: 26rpx 28rpx;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.task-card:active {
  transform: scale(0.985);
  box-shadow: 0 8rpx 22rpx rgba(51, 112, 180, 0.1);
}

.task-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18rpx;
}

.task-title-block {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 8rpx;
}

.box-code {
  color: #172033;
  font-size: 34rpx;
  font-weight: 900;
  line-height: 1.1;
}

.box-name {
  overflow: hidden;
  color: #49627f;
  font-size: 24rpx;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-stack {
  display: flex;
  align-items: flex-end;
  flex-direction: column;
  gap: 8rpx;
}

.status-pill,
.overdue-pill,
.escalation-pill {
  display: inline-flex;
  min-height: 46rpx;
  align-items: center;
  justify-content: center;
  padding: 0 18rpx;
  border-radius: 16rpx;
  font-size: 23rpx;
  font-weight: 800;
  line-height: 1;
}

.status-pill.red {
  background: #ffe8e8;
  color: #dc2626;
}

.status-pill.amber {
  background: #fff4d8;
  color: #b7791f;
}

.status-pill.green {
  background: #e6fbf6;
  color: #0f9f8f;
}

.status-pill.slate {
  background: #edf2f7;
  color: #64748b;
}

.overdue-pill {
  background: #ef4444;
  color: #ffffff;
}

.escalation-pill {
  background: #e6efff;
  color: #1d4ed8;
}

.problem-box {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
  margin-top: 22rpx;
  padding: 18rpx;
  border: 1rpx solid #d8e9fb;
  border-radius: 20rpx;
  background: rgba(238, 247, 255, 0.72);
}

.problem-label,
.meta-label {
  color: #7a91aa;
  font-size: 23rpx;
  font-weight: 700;
}

.problem-text {
  color: #172033;
  font-size: 26rpx;
  font-weight: 600;
  line-height: 1.45;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16rpx 22rpx;
  margin-top: 22rpx;
  padding-right: 18rpx;
}

.meta-item {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 8rpx;
}

.meta-item.wide {
  grid-column: span 2;
}

.meta-value {
  overflow: hidden;
  color: #172033;
  font-size: 25rpx;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta-value.danger {
  color: #ef4444;
}

.card-arrow {
  position: absolute;
  right: 26rpx;
  bottom: 32rpx;
  width: 16rpx;
  height: 16rpx;
  border-top: 4rpx solid #9bb0c8;
  border-right: 4rpx solid #9bb0c8;
  transform: rotate(45deg);
}
</style>
