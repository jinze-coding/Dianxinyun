<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import { getReviewRecords, reviewInspectionRecord } from '@/api/inspection';
import { useAuthStore } from '@/stores/auth';
import { useProjectStore } from '@/stores/project';
import type { InspectionRecord } from '@/types';
import { usePageScrollHeight } from '@/utils/navLayout';
import { getQueryNumber, navigateTo, showToast, switchTab } from '@/utils/navigation';

const authStore = useAuthStore();
const projectStore = useProjectStore();
const records = ref<InspectionRecord[]>([]);
const filter = ref('PENDING');
const projectId = ref<number>();
const fromProject = ref(false);
const { scrollStyle } = usePageScrollHeight({ extraRpx: 102, minHeight: 240 });

const reviewStatuses: Array<InspectionRecord['status']> = [
  'REVIEW_PENDING',
  'REVIEW_PASSED',
  'REVIEW_REJECTED',
  'RECTIFICATION_PENDING',
  'CLOSED'
];

function readRouteContext() {
  const pages = getCurrentPages();
  const current = pages[pages.length - 1] as unknown as { options?: Record<string, string> };
  const parsedProjectId = getQueryNumber(current.options?.projectId, 0);
  projectId.value = parsedProjectId > 0
    ? parsedProjectId
    : projectStore.state.currentProjectId || undefined;
  fromProject.value = current.options?.from === 'project' || parsedProjectId > 0;
}

async function loadRecords() {
  if (!await authStore.ensureRootAccess('/pages/inspection/index')) return;
  await projectStore.loadProjects();
  readRouteContext();
  if (!projectId.value || !await authStore.ensureProjectPermission(
    '/pages/inspection/index', projectId.value, 'inspection.manage', 'INSPECTION_REVIEW')) {
    records.value = [];
    return;
  }
  const nextRecords = await getReviewRecords({ projectId: projectId.value });
  records.value = nextRecords.filter((item) => reviewStatuses.includes(item.status));
}

onShow(loadRecords);

const pendingCount = computed(() => records.value.filter((item) => item.status === 'REVIEW_PENDING').length);

const options = computed(() => [
  { label: '待复核', value: 'PENDING', badge: pendingCount.value },
  { label: '已复核', value: 'DONE' },
  { label: '全部', value: 'ALL' }
]);

const visibleRecords = computed(() => records.value.filter((item) => {
  if (filter.value === 'ALL') return true;
  if (filter.value === 'PENDING') return item.status === 'REVIEW_PENDING';
  return item.status !== 'REVIEW_PENDING';
}));

function goBack() {
  const pages = getCurrentPages();
  if (pages.length > 1) {
    uni.navigateBack();
    return;
  }
  if (fromProject.value && projectId.value) {
    navigateTo(`/pages/project-workbench/index?projectId=${projectId.value}`);
    return;
  }
  switchTab('/pages/inspection/index');
}

function statusMeta(record: InspectionRecord) {
  if (record.status === 'REVIEW_PENDING') return { label: '待复核', className: 'status-pending' };
  if (record.status === 'REVIEW_PASSED') return { label: '已通过', className: 'status-pass' };
  if (record.status === 'REVIEW_REJECTED') return { label: '已退回', className: 'status-reject' };
  if (record.status === 'CLOSED') return { label: '已关闭', className: 'status-pass' };
  return { label: '整改中', className: 'status-rectify' };
}

function photoCount(record: InspectionRecord) {
  return record.outerPhotoCount + record.innerPhotoCount + (record.problemPhotoCount || 0);
}

function formatDateTime(value?: string | null) {
  if (!value) return '-';
  return value.replace('T', ' ').slice(0, 16);
}

function isOverdue(record: InspectionRecord) {
  return record.reviewOverdue === true || Number(record.reviewOverdue) === 1;
}

function inspectorName(record: InspectionRecord) {
  const name = record.inspectorName || '-';
  if (name.length <= 2) return name;
  return `${name.slice(0, 1)}*${name.slice(-1)}`;
}

async function reviewRecord(record: InspectionRecord, action: 'PASS' | 'REJECT' | 'RECTIFY') {
  if (!record.id) {
    showToast('记录ID不存在');
    return;
  }
  if (action === 'RECTIFY') {
    showToast('请在详情中选择整改人');
    openDetail(record);
    return;
  }
  await reviewInspectionRecord(record.id, action, {
    comment: action === 'PASS' ? '复核通过' : action === 'REJECT' ? '请电工补充或修正后重新提交' : '检查发现异常，转整改闭环',
    requirement: undefined
  });
  await loadRecords();
  showToast(action === 'PASS' ? '已通过' : action === 'REJECT' ? '已退回' : '已转整改');
}

function openDetail(record: InspectionRecord) {
  if (!record.id) {
    showToast('记录ID不存在');
    return;
  }
  const query = [
    `id=${record.id}`,
    fromProject.value ? 'from=project' : '',
    projectId.value ? `projectId=${projectId.value}` : ''
  ].filter(Boolean).join('&');
  navigateTo(`/pages/inspection/review-detail?${query}`);
}
</script>

<template>
  <view class="review-shell">
    <view class="phone-frame">
      <AppNavBar title="安全复核" @back="goBack" />

      <view class="filter-tabs">
        <button
          v-for="option in options"
          :key="option.value"
          class="filter-tab"
          :class="{ active: filter === option.value }"
          @tap="filter = option.value"
        >
          <text>{{ option.label }}</text>
          <text v-if="option.badge !== undefined" class="filter-badge">{{ option.badge }}</text>
        </button>
      </view>

      <scroll-view class="record-scroll" scroll-y enable-flex :style="scrollStyle">
        <view v-if="visibleRecords.length" class="review-list">
          <view v-for="record in visibleRecords" :key="record.id || `${record.boxCode}-${record.checkDate}`" class="review-card" @tap="openDetail(record)">
            <view class="record-head">
              <view class="title-line">
                <text class="box-code">{{ record.boxCode }}</text>
                <text class="box-name">{{ record.boxName || '二级电箱' }}</text>
              </view>
              <text class="record-status" :class="statusMeta(record).className">{{ statusMeta(record).label }}</text>
            </view>

            <view class="meta-list">
              <view class="meta-row">
                <text class="meta-label">巡检人</text>
                <text class="meta-value">{{ inspectorName(record) }}</text>
              </view>
              <view class="meta-row">
                <text class="meta-label">巡检时间</text>
                <text class="meta-value">{{ record.inspectedAt || record.checkDate }}</text>
              </view>
              <view class="meta-row">
                <text class="meta-label">复核人</text>
                <text class="meta-value">{{ record.assignedReviewerName || '未分配共享池' }}</text>
              </view>
              <view class="meta-row">
                <text class="meta-label">截止时间</text>
                <text class="meta-value" :class="{ overdue: isOverdue(record) }">
                  {{ formatDateTime(record.reviewDueTime) }}
                  <text v-if="isOverdue(record)" class="overdue-tag">逾期</text>
                </text>
              </view>
              <view class="meta-grid">
                <view class="meta-row">
                  <text class="meta-label">照片</text>
                  <text class="meta-value strong">{{ photoCount(record) }} 张</text>
                </view>
                <view class="meta-row right">
                  <text class="meta-label">异常项</text>
                  <text class="meta-value strong">{{ record.abnormalCount }} 项</text>
                </view>
              </view>
            </view>

            <view v-if="record.status === 'REVIEW_PENDING'" class="action-grid">
              <button class="pass-button" @tap.stop="reviewRecord(record, 'PASS')">通过</button>
              <button class="reject-button" @tap.stop="reviewRecord(record, 'REJECT')">退回</button>
              <button class="rectify-button" @tap.stop="reviewRecord(record, 'RECTIFY')">转整改</button>
            </view>
            <text v-else class="done-text">复核人 {{ record.reviewerName || '张安全' }} · {{ record.reviewTime || '2024-06-11 11:00' }}</text>
          </view>
        </view>

        <view v-else class="empty-card">
          <text class="empty-title">暂无记录</text>
          <text class="empty-desc">当前筛选下没有复核记录</text>
        </view>
      </scroll-view>

    </view>
  </view>
</template>

<style scoped>
.review-shell {
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
  height: 92rpx;
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
  font-size: 32rpx;
  font-weight: 900;
}

.menu-capsule {
  position: absolute;
  right: 28rpx;
  display: flex;
  width: 164rpx;
  height: 60rpx;
  align-items: center;
  justify-content: space-around;
  border: 1rpx solid #e5e7eb;
  border-radius: 999rpx;
  background: #ffffff;
  box-shadow: 0 4rpx 12rpx rgba(31, 46, 76, 0.04);
}

.dot-group {
  display: flex;
  gap: 8rpx;
}

.dot-group text {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: #000000;
}

.capsule-divider {
  width: 1rpx;
  height: 34rpx;
  background: #e5e7eb;
}

.capsule-ring {
  width: 32rpx;
  height: 32rpx;
  border: 8rpx solid #000000;
  border-radius: 50%;
}

.filter-tabs {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  align-items: center;
  padding: 20rpx 28rpx 20rpx;
}

.filter-tab {
  min-width: 0;
  width: 100%;
  height: 62rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14rpx;
  border: 0;
  border-radius: 999rpx;
  background: transparent;
  color: #75849a;
  font-size: 28rpx;
  font-weight: 900;
}

.filter-tab::after {
  border: 0;
}

.filter-tab.active {
  background: linear-gradient(135deg, #0f9b86 0%, #087b70 100%);
  color: #ffffff;
  box-shadow: 0 10rpx 20rpx rgba(15, 118, 110, 0.18);
}

.filter-badge {
  font-size: 28rpx;
  font-weight: 900;
}

.record-scroll {
  box-sizing: border-box;
  padding: 0 24rpx;
}

.review-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  padding-bottom: 26rpx;
}

.review-card,
.empty-card {
  border: 1rpx solid #dfe7f0;
  border-radius: 14rpx;
  background: #ffffff;
  box-shadow: 0 8rpx 20rpx rgba(31, 46, 76, 0.035);
}

.review-card {
  padding: 26rpx 30rpx;
}

.record-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18rpx;
  margin-bottom: 20rpx;
}

.title-line {
  display: flex;
  min-width: 0;
  align-items: baseline;
  gap: 22rpx;
}

.box-code {
  color: #111827;
  font-size: 42rpx;
  font-weight: 900;
  line-height: 1;
  white-space: nowrap;
}

.box-name {
  min-width: 0;
  overflow: hidden;
  color: #111827;
  font-size: 28rpx;
  font-weight: 900;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-status {
  min-width: 96rpx;
  height: 54rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 8rpx;
  font-size: 27rpx;
  font-weight: 900;
}

.status-pending {
  background: #fef3c7;
  color: #b7791f;
}

.status-pass {
  background: #dff7ed;
  color: #0f9f8f;
}

.status-reject,
.status-rectify {
  background: #fee2e2;
  color: #e11d1d;
}

.meta-list {
  display: flex;
  flex-direction: column;
  gap: 17rpx;
}

.meta-row {
  display: grid;
  grid-template-columns: 170rpx minmax(0, 1fr);
  align-items: center;
}

.meta-row.right {
  grid-template-columns: 110rpx minmax(0, 1fr);
}

.meta-label {
  color: #75849a;
  font-size: 27rpx;
  font-weight: 700;
}

.meta-value {
  min-width: 0;
  color: #172033;
  font-size: 27rpx;
  font-weight: 500;
}

.meta-value.strong {
  font-weight: 900;
}

.meta-value.overdue {
  color: #e11d1d;
  font-weight: 800;
}

.overdue-tag {
  display: inline-flex;
  margin-left: 10rpx;
  padding: 4rpx 10rpx;
  border-radius: 999rpx;
  background: #fee2e2;
  color: #e11d1d;
  font-size: 22rpx;
  font-weight: 900;
}

.meta-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 28rpx;
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 38rpx;
  margin-top: 24rpx;
}

.pass-button,
.reject-button,
.rectify-button {
  width: 100%;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8rpx;
  font-size: 28rpx;
  font-weight: 900;
}

.pass-button::after,
.reject-button::after,
.rectify-button::after {
  border: 0;
}

.pass-button {
  background: linear-gradient(135deg, #0f9b86 0%, #057d72 100%);
  color: #ffffff;
  box-shadow: 0 10rpx 18rpx rgba(15, 118, 110, 0.16);
}

.reject-button {
  border: 1rpx solid #fb923c;
  background: #ffffff;
  color: #f97316;
}

.rectify-button {
  border: 1rpx solid #3b82f6;
  background: #ffffff;
  color: #2563eb;
}

.done-text {
  display: block;
  margin-top: 26rpx;
  color: #75849a;
  font-size: 24rpx;
}

.empty-card {
  display: flex;
  min-height: 220rpx;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 12rpx;
  margin-top: 16rpx;
}

.empty-title {
  color: #172033;
  font-size: 30rpx;
  font-weight: 900;
}

.empty-desc {
  color: #75849a;
  font-size: 24rpx;
}

</style>
