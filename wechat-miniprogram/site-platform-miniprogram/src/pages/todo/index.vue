<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import { useTodoStore } from '@/stores/todo';
import type { TodoItem } from '@/types';
import { usePageScrollHeight } from '@/utils/navLayout';
import { navigateTo, showToast, switchTab } from '@/utils/navigation';

const todoStore = useTodoStore();
const filter = ref<TodoItem['type']>('INSPECTION');
const loading = ref(false);
const loadError = ref('');
const { scrollStyle } = usePageScrollHeight({ extraRpx: 90, minHeight: 220 });

onShow(async () => {
  const storedFilter = uni.getStorageSync('site_platform_todo_filter') as TodoItem['type'] | '';
  if (storedFilter && ['INSPECTION', 'REVIEW', 'RECTIFICATION', 'RECHECK'].includes(storedFilter)) {
    filter.value = storedFilter;
    uni.removeStorageSync('site_platform_todo_filter');
  }
  await loadTodos();
});

async function loadTodos() {
  loading.value = true;
  loadError.value = '';
  try {
    await todoStore.loadTodos();
  } catch (error) {
    const message = error instanceof Error ? error.message : '待办加载失败';
    loadError.value = message;
    showToast(message);
  } finally {
    loading.value = false;
  }
}

const options = computed(() => [
  { label: '待巡检', value: 'INSPECTION' as const, badge: todoStore.state.todos.filter((item) => item.type === 'INSPECTION').length },
  { label: '待复核', value: 'REVIEW' as const, badge: todoStore.state.todos.filter((item) => item.type === 'REVIEW').length },
  { label: '待整改', value: 'RECTIFICATION' as const, badge: todoStore.state.todos.filter((item) => item.type === 'RECTIFICATION').length },
  { label: '待复查', value: 'RECHECK' as const, badge: todoStore.state.todos.filter((item) => item.type === 'RECHECK').length }
]);

const todos = computed(() => todoStore.state.todos.filter((item) => item.type === filter.value));
const totalCount = computed(() => todoStore.state.todos.length);
const urgentCount = computed(() => todoStore.state.todos.filter((item) => item.priority === 'danger' || isReviewOverdue(item)).length);
const currentOption = computed(() => options.value.find((item) => item.value === filter.value));

function typeLabel(type: TodoItem['type']) {
  const map = {
    INSPECTION: '待巡检',
    REVIEW: '待复核',
    RECTIFICATION: '待整改',
    RECHECK: '待复查'
  };
  return map[type] || '待处理';
}

function typeClass(type: TodoItem['type']) {
  if (type === 'INSPECTION') return 'tag-blue';
  if (type === 'RECTIFICATION') return 'tag-red';
  if (type === 'RECHECK') return 'tag-amber';
  return 'tag-green';
}

function toneClass(type: TodoItem['type']) {
  if (type === 'INSPECTION') return 'tone-inspection';
  if (type === 'REVIEW') return 'tone-review';
  if (type === 'RECTIFICATION') return 'tone-rectification';
  return 'tone-recheck';
}

function typeIcon(type: TodoItem['type']) {
  if (type === 'INSPECTION') return '检';
  if (type === 'REVIEW') return '核';
  if (type === 'RECTIFICATION') return '改';
  return '查';
}

function actionLabel(type: TodoItem['type']) {
  if (type === 'INSPECTION') return '开始巡检';
  if (type === 'REVIEW') return '立即复核';
  if (type === 'RECTIFICATION') return '提交整改';
  return '进行复查';
}

function priorityClass(todo: TodoItem) {
  if (todo.priority === 'danger' || isReviewOverdue(todo)) return 'priority-danger';
  if (todo.priority === 'warning') return 'priority-warning';
  return 'priority-normal';
}

function displayTitle(todo: TodoItem) {
  const title = String(todo.title || '');
  const boxCode = String(todo.boxCode || '');
  const cleaned = title
    .replace(boxCode, '')
    .replace(/日检记录待复核|抽查记录待复核|巡检确认|未巡检|待确认|待复查|补录|异常整改|整改完成/g, '')
    .replace(/\s+/g, ' ')
    .trim();
  if (cleaned) return cleaned;
  if (todo.type === 'REVIEW') return '日检记录';
  if (todo.type === 'RECTIFICATION') return '整改任务';
  if (todo.type === 'RECHECK') return '整改复查';
  return title || '巡检待办';
}

function timeLabel(type: TodoItem['type']) {
  if (type === 'INSPECTION') return '应检日期';
  if (type === 'REVIEW') return '复核截止';
  if (type === 'RECTIFICATION') return '整改截止';
  return '复查截止';
}

function isReviewOverdue(todo: TodoItem) {
  return todo.type === 'REVIEW' && (todo.reviewOverdue === true || Number(todo.reviewOverdue) === 1 || todo.priority === 'danger');
}

function goBack() {
  const pages = getCurrentPages();
  if (pages.length > 1) {
    uni.navigateBack();
    return;
  }
  switchTab('/pages/profile/index');
}

function openTodo(todo: TodoItem) {
  if (!todo.targetId) {
    showToast('待办详情暂不可查看');
    return;
  }
  if (todo.type === 'INSPECTION') {
    navigateTo(`/pages/inspection/form?boxId=${todo.targetId}`);
    return;
  }
  if (todo.businessType === 'QUALITY_ISSUE') {
    uni.setStorageSync('site_platform_quality_issue_id', todo.targetId);
    switchTab('/pages/quality/index');
    return;
  }
  if (todo.type === 'REVIEW') {
    navigateTo(`/pages/inspection/review-detail?id=${todo.targetId}`);
    return;
  }
  navigateTo(`/pages/rectification/detail?id=${todo.targetId}`);
}
</script>

<template>
  <view class="todo-shell">
    <view class="phone-frame">
      <AppNavBar title="我的待办" @back="goBack" />

      <view class="filter-tabs">
        <button
          v-for="option in options"
          :key="option.value"
          class="filter-tab"
          :class="[toneClass(option.value), { active: filter === option.value }]"
          @tap="filter = option.value"
        >
          <text>{{ option.label }}</text>
          <text class="filter-badge">{{ option.badge }}</text>
        </button>
      </view>

      <scroll-view class="todo-scroll" scroll-y enable-flex :style="scrollStyle">
        <view class="list-context">
          <view><text>{{ currentOption?.label || '待处理' }}</text><text>当前 {{ todos.length }} 项</text></view>
          <view><text>全部 {{ totalCount }}</text><text v-if="urgentCount" class="urgent-count">优先 {{ urgentCount }}</text></view>
        </view>

        <view v-if="loading" class="todo-list skeleton-list">
          <view v-for="index in 2" :key="index" class="todo-card skeleton-card"><view class="skeleton-line skeleton-title"></view><view class="skeleton-line"></view><view class="skeleton-line short"></view></view>
        </view>

        <view v-else-if="loadError" class="empty-card error-card">
          <text class="empty-mark">!</text><text class="empty-title">待办加载失败</text><text class="empty-desc">{{ loadError }}</text><button class="retry-button" @tap="loadTodos">重新加载</button>
        </view>

        <view v-else-if="todos.length" :key="filter" class="todo-list">
          <button v-for="(todo, index) in todos" :key="todo.id" class="todo-card" :class="[toneClass(todo.type), priorityClass(todo)]" :style="{ animationDelay: `${index * 45}ms` }" @tap="openTodo(todo)">
            <view class="card-accent"></view>
            <view class="card-head">
              <view class="type-mark">{{ typeIcon(todo.type) }}</view>
              <view class="title-block">
                <view class="title-line"><text class="todo-code">{{ todo.boxCode }}</text><text class="project-chip">{{ todo.projectName }}</text></view>
                <text class="todo-name">{{ displayTitle(todo) }}</text>
              </view>
              <text class="todo-status" :class="typeClass(todo.type)">{{ typeLabel(todo.type) }}</text>
            </view>

            <view class="meta-panel">
              <view class="meta-row"><text class="meta-icon location-icon"></text><text class="meta-label">安装位置</text><text class="meta-value">{{ todo.installLocation || '-' }}</text></view>
              <view class="meta-row"><text class="meta-icon time-icon"></text><text class="meta-label">{{ timeLabel(todo.type) }}</text><view class="meta-value due-value" :class="{ overdue: isReviewOverdue(todo) }"><text>{{ todo.dueText }}</text><text v-if="isReviewOverdue(todo)" class="overdue-tag">逾期</text></view></view>
              <view v-if="todo.type === 'REVIEW'" class="meta-row"><text class="meta-icon reviewer-icon"></text><text class="meta-label">复核人</text><text class="meta-value">{{ todo.assignedReviewerName || '未分配共享池' }}</text></view>
            </view>

            <view class="card-footer"><text :class="{ danger: todo.priority === 'danger' || isReviewOverdue(todo) }">{{ todo.priority === 'danger' || isReviewOverdue(todo) ? '请优先处理' : '按时完成任务' }}</text><view><text>{{ actionLabel(todo.type) }}</text><text class="row-arrow"></text></view></view>
          </button>
        </view>

        <view v-else class="empty-card">
          <text class="empty-mark">✓</text><text class="empty-title">当前没有{{ currentOption?.label }}</text><text class="empty-desc">已处理完成的任务不会继续显示在这里</text>
          </view>
      </scroll-view>

    </view>
  </view>
</template>

<style scoped>
.todo-shell {
  height: 100vh;
  min-height: 100vh;
  overflow: hidden;
  background: #eef7ff;
}

.phone-frame {
  display: flex;
  height: 100vh;
  min-height: 0;
  position: relative;
  width: 100%;
  overflow: hidden;
  flex-direction: column;
  border: 0;
  border-radius: 0;
  background: #eef7ff;
  box-shadow: none;
}

.status-bar {
  display: flex;
  height: 88rpx;
  align-items: center;
  justify-content: space-between;
  padding: 18rpx 54rpx 0;
  color: #000000;
}

.status-time {
  font-size: 28rpx;
  font-weight: 500;
}

.status-icons {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.signal {
  display: flex;
  height: 24rpx;
  align-items: flex-end;
  gap: 4rpx;
}

.bar {
  width: 6rpx;
  border-radius: 999rpx;
  background: #000000;
}

.bar-1 {
  height: 9rpx;
}

.bar-2 {
  height: 14rpx;
}

.bar-3 {
  height: 19rpx;
}

.bar-4 {
  height: 24rpx;
}

.wifi {
  position: relative;
  width: 30rpx;
  height: 24rpx;
}

.wifi-arc {
  position: absolute;
  right: 0;
  left: 0;
  margin: auto;
  border-top: 5rpx solid #000000;
  border-right: 5rpx solid transparent;
  border-left: 5rpx solid transparent;
  border-radius: 50%;
}

.arc-1 {
  top: 0;
  width: 30rpx;
  height: 18rpx;
}

.arc-2 {
  top: 8rpx;
  width: 18rpx;
  height: 12rpx;
}

.wifi-dot {
  position: absolute;
  right: 0;
  bottom: 1rpx;
  left: 0;
  width: 6rpx;
  height: 6rpx;
  margin: auto;
  border-radius: 50%;
  background: #000000;
}

.battery {
  position: relative;
  width: 43rpx;
  height: 22rpx;
  border: 3rpx solid #000000;
  border-radius: 6rpx;
}

.battery::after {
  position: absolute;
  top: 5rpx;
  right: -7rpx;
  width: 4rpx;
  height: 8rpx;
  border-radius: 0 4rpx 4rpx 0;
  background: #000000;
  content: "";
}

.battery-fill {
  position: absolute;
  top: 3rpx;
  left: 3rpx;
  width: 31rpx;
  height: 10rpx;
  border-radius: 3rpx;
  background: #000000;
}

.nav-bar {
  position: relative;
  display: flex;
  height: 96rpx;
  align-items: center;
  justify-content: center;
  padding: 0 36rpx;
}

.back-button {
  position: absolute;
  left: 28rpx;
  display: flex;
  width: 68rpx;
  height: 68rpx;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.back-button::after {
  border: 0;
}

.back-icon {
  width: 28rpx;
  height: 28rpx;
  border-bottom: 6rpx solid #111827;
  border-left: 6rpx solid #111827;
  transform: rotate(45deg);
}

.nav-title {
  color: #111827;
  font-size: var(--mp-font-page-title);
  font-weight: var(--mp-weight-title);
}

.filter-tabs {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12rpx;
  align-items: center;
  margin: 0 28rpx;
  padding: 10rpx;
  border: 1rpx solid #cde3fb;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 10rpx 24rpx rgba(51, 112, 180, 0.08);
}

.filter-tab {
  min-width: 0;
  height: 58rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7rpx;
  padding: 0;
  border: 1rpx solid transparent;
  border-radius: 18rpx;
  background: transparent;
  color: #6b7f99;
  font-size: var(--mp-font-helper);
  font-weight: var(--mp-weight-label);
  line-height: 1;
  white-space: nowrap;
}

.filter-tab::after {
  border: 0;
}

.filter-tab.active {
  border-color: rgba(22, 119, 255, 0.2);
  background: #e7f1ff;
  color: #1677ff;
  transform: none;
}

.filter-badge {
  font-size: var(--mp-font-helper);
  font-weight: var(--mp-weight-emphasis);
}

.filter-tab text {
  white-space: nowrap;
}

.todo-scroll {
  box-sizing: border-box;
  padding: 32rpx 0 0;
}

.todo-list {
  display: flex;
  flex-direction: column;
  gap: 22rpx;
  padding: 0 28rpx 32rpx;
}

.todo-card,
.empty-card {
  border: 1rpx solid #cde3fb;
  border-radius: 24rpx;
  background: linear-gradient(145deg, #ffffff, #f7fbff);
  box-shadow: 0 14rpx 32rpx rgba(51, 112, 180, 0.1);
}

.todo-card {
  min-height: 194rpx;
  padding: 28rpx 28rpx;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.todo-card:active {
  transform: scale(0.985);
  box-shadow: 0 8rpx 22rpx rgba(51, 112, 180, 0.12);
}

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18rpx;
  margin-bottom: 26rpx;
}

.title-line {
  display: flex;
  flex: 1;
  min-width: 0;
  align-items: baseline;
  gap: 18rpx;
}

.todo-code {
  color: #111827;
  font-size: 34rpx;
  font-weight: var(--mp-weight-title);
  line-height: 1;
  white-space: nowrap;
}

.todo-name {
  min-width: 0;
  overflow: hidden;
  color: #111827;
  font-size: var(--mp-font-body);
  font-weight: var(--mp-weight-body);
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.todo-status {
  min-width: 84rpx;
  height: 44rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 999rpx;
  font-size: var(--mp-font-tag);
  font-weight: var(--mp-weight-emphasis);
}

.tag-blue {
  background: #dbeafe;
  color: #2563eb;
}

.tag-green {
  background: #dff7ed;
  color: #0f9f8f;
}

.tag-red {
  background: #fee2e2;
  color: #e11d1d;
}

.tag-amber {
  background: #fef3c7;
  color: #b7791f;
}

.info-row {
  display: grid;
  grid-template-columns: 154rpx minmax(0, 1fr);
  align-items: center;
  margin-top: 22rpx;
}

.info-label {
  color: #6b7f99;
  font-size: var(--mp-font-helper);
  font-weight: var(--mp-weight-label);
}

.info-value {
  min-width: 0;
  color: #172033;
  font-size: var(--mp-font-body);
  font-weight: var(--mp-weight-body);
  line-height: 1.3;
}

.info-value.overdue {
  color: #e11d1d;
  font-weight: var(--mp-weight-emphasis);
}

.overdue-tag {
  display: inline-flex;
  margin-left: 10rpx;
  padding: 4rpx 10rpx;
  border-radius: 999rpx;
  background: #fee2e2;
  color: #e11d1d;
  font-size: 22rpx;
  font-weight: var(--mp-weight-emphasis);
}

.empty-card {
  display: flex;
  min-height: 280rpx;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 16rpx;
  margin: 0 28rpx;
}

.empty-title {
  color: #172033;
  font-size: var(--mp-font-card-title);
  font-weight: var(--mp-weight-title);
}

.empty-desc {
  color: #6b7f99;
  font-size: var(--mp-font-helper);
  font-weight: var(--mp-weight-body);
}

.page-tabbar {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  min-height: 168rpx;
  align-items: start;
  padding-top: 24rpx;
  border-top: 1rpx solid #cde3fb;
  background: rgba(242, 248, 255, 0.96);
  box-shadow: 0 -14rpx 34rpx rgba(51, 112, 180, 0.1);
}

.tab-item {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  flex-direction: column;
  gap: 12rpx;
  color: #6b7f99;
}

.tab-icon {
  position: relative;
  width: 40rpx;
  height: 40rpx;
  line-height: 1;
  border: 4rpx solid currentColor;
  border-radius: 7rpx;
}

.tab-icon::before,
.tab-icon::after {
  position: absolute;
  content: "";
  box-sizing: border-box;
}

.project {
  width: 42rpx;
  height: 42rpx;
  border: none;
}

.project::before {
  top: 1rpx;
  left: 1rpx;
  width: 16rpx;
  height: 38rpx;
  border: 4rpx solid currentColor;
  border-radius: 4rpx;
  box-shadow: 25rpx 0 0 -4rpx #f2f8ff, 25rpx 0 0 0 currentColor;
}

.scan {
  width: 40rpx;
  height: 40rpx;
  border: none;
}

.scan::before {
  width: 40rpx;
  height: 40rpx;
  border-radius: 8rpx;
  background:
    linear-gradient(currentColor, currentColor) left top / 16rpx 16rpx no-repeat,
    linear-gradient(currentColor, currentColor) right top / 16rpx 16rpx no-repeat,
    linear-gradient(currentColor, currentColor) left bottom / 16rpx 16rpx no-repeat,
    linear-gradient(currentColor, currentColor) right bottom / 16rpx 16rpx no-repeat;
}

.scan::after {
  top: 15rpx;
  left: 15rpx;
  width: 12rpx;
  height: 12rpx;
  border-radius: 4rpx;
  background: #f2f8ff;
  box-shadow: 17rpx 0 0 #f2f8ff, 0 17rpx 0 #f2f8ff, 17rpx 17rpx 0 #f2f8ff;
}

.todo::before {
  left: 11rpx;
  top: 6rpx;
  width: 16rpx;
  height: 23rpx;
  border-right: 5rpx solid currentColor;
  border-bottom: 5rpx solid currentColor;
  transform: rotate(40deg);
}

.profile {
  width: 42rpx;
  height: 42rpx;
  border: none;
  border-radius: 50%;
}

.profile::before {
  left: 12rpx;
  top: 0;
  width: 22rpx;
  height: 22rpx;
  border: 5rpx solid currentColor;
  border-radius: 50%;
}

.profile::after {
  left: 3rpx;
  bottom: 0;
  width: 40rpx;
  height: 23rpx;
  border: 5rpx solid currentColor;
  border-bottom: none;
  border-radius: 999rpx 999rpx 0 0;
}

.tab-text {
  font-size: var(--mp-font-helper);
  font-weight: var(--mp-weight-body);
  line-height: 1;
}

.tab-item.active {
  color: #1677ff;
  font-weight: var(--mp-weight-emphasis);
}

.home-indicator {
  position: absolute;
  right: 118rpx;
  bottom: 18rpx;
  left: 118rpx;
  height: 8rpx;
  border-radius: 999rpx;
  background: #000000;
}

/* 待办页 V2：统一安全模块的暖白卡片与紧凑信息层级。 */
.todo-shell,
.phone-frame {
  background: #f5f4f0;
}

.filter-tabs {
  gap: 5rpx;
  margin: 0 24rpx;
  padding: 6rpx;
  border-color: rgba(145, 103, 57, 0.1);
  border-radius: 18rpx;
  background: #ffffff;
  box-shadow: 0 8rpx 24rpx rgba(68, 53, 34, 0.055);
}

.filter-tab {
  --todo-tone: #a96527;
  --todo-soft: #fff0df;
  height: 58rpx;
  gap: 6rpx;
  border-radius: 13rpx;
  color: #7d8793;
  font-size: 20rpx;
  font-weight: 650;
}

.tone-inspection { --todo-tone: #a96527; --todo-soft: #fff0df; }
.tone-review { --todo-tone: #238361; --todo-soft: #e6f4ec; }
.tone-rectification { --todo-tone: #bd4e46; --todo-soft: #fdecea; }
.tone-recheck { --todo-tone: #52758d; --todo-soft: #e9f1f5; }

.filter-tab.active {
  border-color: transparent;
  background: var(--todo-soft);
  color: var(--todo-tone);
  box-shadow: inset 0 0 0 1rpx rgba(145, 103, 57, 0.12);
}

.filter-badge {
  display: flex;
  min-width: 27rpx;
  height: 27rpx;
  align-items: center;
  justify-content: center;
  padding: 0 5rpx;
  border-radius: 999rpx;
  background: #f1efeb;
  color: #858079;
  font-size: 16rpx;
  line-height: 1;
}

.filter-tab.active .filter-badge {
  background: #ffffff;
  color: var(--todo-tone);
}

.todo-scroll {
  padding-top: 16rpx;
}

.list-context {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28rpx 14rpx;
}

.list-context > view {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.list-context > view:first-child text:first-child {
  color: #2b384b;
  font-size: 23rpx;
  font-weight: 850;
}

.list-context text {
  color: #969da6;
  font-size: 18rpx;
}

.list-context .urgent-count {
  padding: 5rpx 10rpx;
  border-radius: 999rpx;
  background: #fdecea;
  color: #b94d46;
  font-weight: 750;
}

.todo-list {
  gap: 14rpx;
  padding: 0 24rpx 38rpx;
}

.todo-card {
  --todo-tone: #a96527;
  --todo-soft: #fff0df;
  position: relative;
  display: block;
  width: 100%;
  min-height: 0;
  overflow: hidden;
  padding: 20rpx;
  border: 1rpx solid rgba(145, 103, 57, 0.1);
  border-radius: 21rpx;
  background: #ffffff;
  box-shadow: 0 9rpx 26rpx rgba(68, 53, 34, 0.055);
  text-align: left;
  animation: todo-card-in 220ms ease both;
}

.todo-card::after { border: 0; }

.todo-card:active {
  transform: scale(0.986);
  box-shadow: 0 5rpx 16rpx rgba(68, 53, 34, 0.08);
}

.card-accent {
  position: absolute;
  top: 23rpx;
  bottom: 23rpx;
  left: 0;
  width: 5rpx;
  border-radius: 0 999rpx 999rpx 0;
  background: var(--todo-tone);
  opacity: 0.68;
}

.priority-danger .card-accent { background: #c44740; opacity: 1; }
.priority-warning .card-accent { opacity: 0.8; }
.priority-normal .card-accent { opacity: 0.5; }

.card-head {
  align-items: center;
  gap: 13rpx;
  margin-bottom: 0;
}

.type-mark {
  display: flex;
  width: 48rpx;
  height: 48rpx;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 13rpx;
  background: var(--todo-soft);
  color: var(--todo-tone);
  font-size: 21rpx;
  font-weight: 900;
  line-height: 1;
}

.title-block {
  min-width: 0;
  flex: 1;
}

.title-line {
  align-items: center;
  gap: 9rpx;
}

.todo-code {
  color: #263449;
  font-size: 27rpx;
  font-weight: 900;
  line-height: 1.1;
}

.project-chip {
  overflow: hidden;
  padding: 4rpx 8rpx;
  border-radius: 7rpx;
  background: #f3f1ed;
  color: #918579;
  font-size: 15rpx;
  font-weight: 650;
  line-height: 1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.todo-name {
  display: block;
  margin-top: 5rpx;
  color: #677384;
  font-size: 20rpx;
  font-weight: 650;
  line-height: 1.3;
}

.todo-status {
  min-width: 74rpx;
  height: 38rpx;
  padding: 0 11rpx;
  font-size: 17rpx;
  line-height: 1;
}

.meta-panel {
  margin-top: 16rpx;
  padding: 0 14rpx;
  border-radius: 14rpx;
  background: #faf9f7;
}

.meta-row {
  display: grid;
  grid-template-columns: 22rpx 88rpx minmax(0, 1fr);
  min-height: 51rpx;
  align-items: center;
  gap: 9rpx;
}

.meta-row + .meta-row { border-top: 1rpx solid #eeebe6; }

.meta-icon {
  position: relative;
  width: 17rpx;
  height: 17rpx;
  color: var(--todo-tone);
}

.location-icon {
  border: 2rpx solid currentColor;
  border-radius: 50% 50% 50% 0;
  transform: rotate(-45deg) scale(0.68);
}

.time-icon {
  border: 2rpx solid currentColor;
  border-radius: 50%;
}

.time-icon::before,
.time-icon::after {
  position: absolute;
  top: 7rpx;
  left: 7rpx;
  width: 5rpx;
  height: 2rpx;
  border-radius: 999rpx;
  background: currentColor;
  content: '';
  transform-origin: left center;
}

.time-icon::after { transform: rotate(-90deg); }

.reviewer-icon::before {
  position: absolute;
  top: 0;
  left: 5rpx;
  width: 8rpx;
  height: 8rpx;
  border: 2rpx solid currentColor;
  border-radius: 50%;
  content: '';
}

.reviewer-icon::after {
  position: absolute;
  bottom: 0;
  left: 1rpx;
  width: 15rpx;
  height: 7rpx;
  border: 2rpx solid currentColor;
  border-bottom: 0;
  border-radius: 999rpx 999rpx 0 0;
  content: '';
}

.meta-label {
  color: #969da6;
  font-size: 17rpx;
  font-weight: 550;
}

.meta-value {
  min-width: 0;
  overflow: hidden;
  color: #465365;
  font-size: 19rpx;
  font-weight: 650;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.due-value {
  display: flex;
  align-items: center;
  gap: 7rpx;
}

.overdue-tag {
  margin-left: 0;
  padding: 4rpx 8rpx;
  font-size: 15rpx;
  line-height: 1;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 14rpx;
  padding: 0 3rpx;
  color: #9a9fa6;
  font-size: 17rpx;
}

.card-footer > text.danger { color: #bd4e46; font-weight: 750; }

.card-footer > view {
  display: flex;
  align-items: center;
  gap: 10rpx;
  color: var(--todo-tone);
  font-size: 18rpx;
  font-weight: 800;
}

.row-arrow {
  width: 9rpx;
  height: 9rpx;
  border-top: 2rpx solid currentColor;
  border-right: 2rpx solid currentColor;
  transform: rotate(45deg);
}

.empty-card {
  min-height: 300rpx;
  gap: 11rpx;
  margin: 0 24rpx;
  border-color: rgba(145, 103, 57, 0.1);
  background: #ffffff;
  box-shadow: 0 9rpx 26rpx rgba(68, 53, 34, 0.05);
}

.empty-mark {
  display: flex;
  width: 62rpx;
  height: 62rpx;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #e8f4ec;
  color: #27815e;
  font-size: 27rpx;
  font-weight: 900;
}

.error-card .empty-mark { background: #fdecea; color: #bd4e46; }
.empty-title { font-size: 25rpx; }
.empty-desc { max-width: 78%; color: #9299a3; font-size: 19rpx; line-height: 1.5; text-align: center; }

.retry-button {
  display: flex;
  min-width: 154rpx;
  height: 58rpx;
  align-items: center;
  justify-content: center;
  margin-top: 12rpx;
  padding: 0 20rpx;
  border-radius: 13rpx;
  background: #a96527;
  color: #ffffff;
  font-size: 20rpx;
  font-weight: 750;
  line-height: 1;
}

.skeleton-card { pointer-events: none; }
.skeleton-line { height: 20rpx; margin-top: 18rpx; border-radius: 999rpx; background: linear-gradient(100deg, #eeeae4 20%, #faf8f5 40%, #eeeae4 60%); background-size: 240% 100%; animation: todo-shimmer 1.2s ease infinite; }
.skeleton-title { width: 44%; height: 28rpx; margin-top: 0; }
.skeleton-line.short { width: 62%; }

@keyframes todo-card-in { from { opacity: 0; transform: translateY(12rpx); } to { opacity: 1; transform: translateY(0); } }
@keyframes todo-shimmer { from { background-position: 100% 0; } to { background-position: -100% 0; } }

@media (prefers-reduced-motion: reduce) {
  .todo-card,
  .skeleton-line { animation-duration: 1ms !important; }
}
</style>
