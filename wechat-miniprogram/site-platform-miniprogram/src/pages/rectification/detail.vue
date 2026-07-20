<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import { closeRectification, completeRectification, getRectificationDetail, rejectRectification } from '@/api/rectification';
import { uploadPhotoIds } from '@/api/file';
import { formatSpotCheckCategory } from '@/constants/spotCheck';
import type { RectificationTask } from '@/types';
import { usePageScrollHeight } from '@/utils/navLayout';
import { getQueryNumber, navigateTo, showToast, switchTab } from '@/utils/navigation';

const MAX_PHOTOS = 5;
const task = ref<RectificationTask>();
const fromProjectList = ref(false);
const sourceProjectId = ref<number>();
const feedback = ref('');
const photos = ref<string[]>([]);
const uploadSlots = [0, 1, 2];
const { scrollStyle } = usePageScrollHeight({ bottomRpx: 160, minHeight: 240 });

onShow(async () => {
  const pages = getCurrentPages();
  const current = pages[pages.length - 1] as unknown as { options?: Record<string, string> };
  const id = getQueryNumber(current.options?.id, 7001);
  fromProjectList.value = current.options?.from === 'project';
  sourceProjectId.value = current.options?.projectId ? getQueryNumber(current.options.projectId, 0) : undefined;
  task.value = await getRectificationDetail(id);
  feedback.value = task.value?.feedback || '';
  photos.value = task.value?.rectificationPhotos || [];
});

const beforePhotos = computed(() => task.value?.beforePhotos || []);
const canSubmitFeedback = computed(() => ['PENDING', 'REJECTED'].includes(task.value?.status || ''));
const canReview = computed(() => task.value?.status === 'COMPLETED');
const isReadonly = computed(() => task.value?.status === 'CLOSED');
const boxText = computed(() => {
  if (!task.value) return '';
  return `${task.value.boxCode}  ${task.value.boxName || ''}`.trim();
});

function statusLabel(status?: string) {
  if (status === 'COMPLETED') return '待复查';
  if (status === 'CLOSED') return '已关闭';
  if (status === 'REJECTED') return '已退回';
  return '整改中';
}

function logActionLabel(action?: string) {
  if (action === 'COMPLETE') return '提交整改';
  if (action === 'CLOSE') return '复查关闭';
  if (action === 'REJECT') return '复查退回';
  if (action === 'ASSIGN') return '改派整改人';
  if (action === 'REMIND') return '跟进提醒';
  if (action === 'ESCALATE') return '升级提醒';
  return action || '操作';
}

function goBack() {
  if (getCurrentPages().length > 1) {
    uni.navigateBack();
    return;
  }
  if (fromProjectList.value && sourceProjectId.value) {
    navigateTo(`/pages/rectification/index?projectId=${sourceProjectId.value}`);
    return;
  }
  switchTab('/pages/profile/index');
}

function afterAction() {
  if (fromProjectList.value) {
    if (getCurrentPages().length > 1) {
      uni.navigateBack();
      return;
    }
    if (sourceProjectId.value) {
      navigateTo(`/pages/rectification/index?projectId=${sourceProjectId.value}`);
      return;
    }
  }
  switchTab('/pages/profile/index');
}

function chooseRectificationPhoto(index: number) {
  if (!canSubmitFeedback.value) {
    showToast('当前状态不可修改整改照片');
    return;
  }
  if (photos.value[index]) {
    photos.value = photos.value.filter((_, currentIndex) => currentIndex !== index);
    return;
  }
  if (photos.value.length >= MAX_PHOTOS) {
    showToast(`最多上传${MAX_PHOTOS}张`);
    return;
  }
  if (import.meta.env.VITE_USE_MOCK === 'true') {
    photos.value = [...photos.value, `/static/mock-photo.svg?time=${Date.now()}`].slice(0, MAX_PHOTOS);
    return;
  }
  uni.chooseImage({
    count: 1,
    success: (result) => {
      photos.value = [...photos.value, result.tempFilePaths[0]].slice(0, MAX_PHOTOS);
    }
  });
}

async function submitFeedback() {
  if (!task.value) return;
  if (!canSubmitFeedback.value) {
    showToast('当前状态不可提交整改');
    return;
  }
  if (!feedback.value.trim()) {
    showToast('请填写整改说明');
    return;
  }
  if (!photos.value.length) {
    showToast('请上传整改照片');
    return;
  }
  const existingPhotos = task.value.rectificationPhotos || [];
  const existingIds = task.value.rectificationPhotoFileIds || [];
  const newPhotos = photos.value.filter((path) => !existingPhotos.includes(path));
  const newPhotoIds = await uploadPhotoIds(newPhotos, 'RECTIFICATION_PHOTO', {
    projectId: task.value.projectId,
    businessType: 'inspection_rectification',
    businessId: task.value.id
  });
  await completeRectification(task.value.id, feedback.value, photos.value, [...existingIds, ...newPhotoIds]);
  showToast('已提交复查');
  afterAction();
}

async function closeTask() {
  if (!task.value) return;
  if (!canReview.value) {
    showToast('待复查状态才可关闭');
    return;
  }
  await closeRectification(task.value.id);
  showToast('整改已关闭');
  afterAction();
}

async function rejectTask() {
  if (!task.value) return;
  if (!canReview.value) {
    showToast('待复查状态才可退回');
    return;
  }
  await rejectRectification(task.value.id, '整改照片或现场情况仍不符合要求，请继续整改');
  showToast('已退回继续整改');
  if (!fromProjectList.value) {
    uni.setStorageSync('site_platform_todo_filter', 'RECTIFICATION');
  }
  afterAction();
}
</script>

<template>
  <view class="rect-shell">
    <AppNavBar title="整改详情" @back="goBack" />

    <scroll-view class="rect-scroll" scroll-y enable-flex :style="scrollStyle">
      <view v-if="task" class="rect-content">
        <view class="detail-card">
          <view class="info-row first-row">
            <text class="info-label">整改单号</text>
            <text class="info-value order-value">{{ task.orderNo || `ZG-${task.id}` }}</text>
            <text class="status-pill">{{ statusLabel(task.status) }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">关联电箱</text>
            <text class="info-value">{{ boxText }}</text>
          </view>
          <view class="info-row problem-row">
            <text class="info-label">问题描述</text>
            <text class="info-value problem-text">{{ task.problemDesc }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">问题分类</text>
            <text class="info-value">{{ formatSpotCheckCategory(task.problemCategory) }}</text>
          </view>
          <view class="info-row">
            <text class="info-label wide">要求整改期限</text>
            <text class="info-value deadline-value">{{ task.requirement }}</text>
          </view>
          <view class="info-row">
            <text class="info-label">整改负责人</text>
            <text class="info-value">{{ task.assigneeName }}（{{ task.responsiblePhone || '未登记' }}）</text>
          </view>
          <view v-if="task.completedAt" class="info-row">
            <text class="info-label">完成时间</text>
            <text class="info-value">{{ task.completedAt }}</text>
          </view>
          <view v-if="task.reviewTime" class="info-row">
            <text class="info-label">复查时间</text>
            <text class="info-value">{{ task.reviewTime }}</text>
          </view>
          <view v-if="task.recheckDeadline" class="info-row">
            <text class="info-label wide">复查截止</text>
            <text class="info-value deadline-value">{{ task.recheckDeadline }}</text>
          </view>
          <view v-if="task.rejectCount" class="info-row">
            <text class="info-label">退回次数</text>
            <text class="info-value">{{ task.rejectCount }} 次</text>
          </view>
          <view v-if="task.reviewComment" class="info-row">
            <text class="info-label">复查意见</text>
            <text class="info-value problem-text">{{ task.reviewComment }}</text>
          </view>
          <view v-if="task.escalationStatus && task.escalationStatus !== 'NONE'" class="info-row">
            <text class="info-label">升级提醒</text>
            <text class="info-value problem-text">
              {{ task.escalationStatus === 'ESCALATED' ? '已升级' : '已提醒' }}
              {{ task.escalationTime ? ` · ${task.escalationTime}` : '' }}
              {{ task.escalationNote ? ` · ${task.escalationNote}` : '' }}
            </text>
          </view>

          <view class="card-divider"></view>

          <view class="section-title-row">
            <text class="section-title">整改前照片</text>
          </view>
          <view class="before-photo-row">
            <image
              v-for="(path, index) in beforePhotos"
              :key="`${path}-${index}`"
              class="before-photo"
              :src="path"
              mode="aspectFill"
            />
            <text class="before-count">共 {{ beforePhotos.length }} 张</text>
          </view>

          <view class="feedback-section">
            <text class="section-title">整改反馈</text>
            <view class="feedback-box">
              <textarea
                v-model="feedback"
                maxlength="200"
                class="feedback-input"
                :disabled="!canSubmitFeedback"
                :placeholder="canSubmitFeedback ? '请填写整改过程说明' : '当前状态不可编辑整改反馈'"
                placeholder-class="feedback-placeholder"
                :adjust-position="true"
                :cursor-spacing="112"
              />
              <text class="feedback-count">{{ feedback.length }}/200</text>
            </view>
          </view>

          <view class="upload-section">
            <view class="upload-title-row">
              <text class="section-title">整改照片</text>
              <text class="upload-hint">（上传整改后凭证）</text>
            </view>
            <view class="upload-row">
              <view
                v-for="index in uploadSlots"
                :key="index"
                class="upload-slot"
                @tap="chooseRectificationPhoto(index)"
              >
                <image v-if="photos[index]" class="upload-image" :src="photos[index]" mode="aspectFill" />
                <text v-else class="camera-icon"></text>
              </view>
              <text class="upload-count">{{ photos.length }}/{{ MAX_PHOTOS }}</text>
            </view>
          </view>

          <view v-if="task.reviewLogs?.length" class="log-section">
            <text class="section-title">整改留痕</text>
            <view
              v-for="log in task.reviewLogs"
              :key="`${log.actionType}-${log.createTime}`"
              class="log-item"
            >
              <view class="log-head">
                <text class="log-action">{{ logActionLabel(log.actionType) }}</text>
                <text class="log-time">{{ log.createTime || '' }}</text>
              </view>
              <text class="log-operator">{{ log.operatorName || '系统' }}</text>
              <text v-if="log.comment" class="log-comment">{{ log.comment }}</text>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>

    <view v-if="task && !isReadonly && (canSubmitFeedback || canReview)" class="bottom-actions" :class="{ single: canSubmitFeedback }">
      <button v-if="canSubmitFeedback" class="finish-button" @tap="submitFeedback">完成整改</button>
      <button v-if="canReview" class="reject-review-button" @tap="rejectTask">复查退回</button>
      <button v-if="canReview" class="close-button" @tap="closeTask">复查关闭</button>
    </view>
  </view>
</template>

<style scoped>
.rect-shell {
  height: 100vh;
  min-height: 100vh;
  overflow: hidden;
  padding-bottom: 0;
  background: #eef7ff;
  color: #0f172a;
}

.rect-nav {
  padding: 18rpx 24rpx 0;
  background: #ffffff;
}

.status-row {
  display: flex;
  height: 48rpx;
  align-items: center;
  justify-content: space-between;
  padding: 0 13rpx;
}

.clock {
  color: #000000;
  font-size: 27rpx;
  font-weight: 700;
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
  border: 6rpx solid currentColor;
  border-color: currentColor transparent transparent transparent;
  border-radius: 50%;
  content: "";
}

.wifi-mark::after {
  position: absolute;
  left: 13rpx;
  bottom: 0;
  width: 9rpx;
  height: 9rpx;
  border-radius: 50%;
  background: currentColor;
  content: "";
}

.battery-mark {
  position: relative;
  width: 46rpx;
  height: 24rpx;
  border: 3rpx solid currentColor;
  border-radius: 6rpx;
}

.battery-mark::before {
  position: absolute;
  right: -7rpx;
  top: 6rpx;
  width: 4rpx;
  height: 9rpx;
  border-radius: 0 4rpx 4rpx 0;
  background: currentColor;
  content: "";
}

.battery-mark::after {
  position: absolute;
  top: 4rpx;
  left: 4rpx;
  width: 31rpx;
  height: 10rpx;
  border-radius: 2rpx;
  background: currentColor;
  content: "";
}

.nav-row {
  position: relative;
  display: flex;
  height: 92rpx;
  align-items: center;
  justify-content: space-between;
}

.back-button {
  display: flex;
  width: 56rpx;
  height: 56rpx;
  align-items: center;
  justify-content: flex-start;
}

.back-icon {
  width: 27rpx;
  height: 27rpx;
  margin-left: 5rpx;
  border-left: 4rpx solid #0f172a;
  border-bottom: 4rpx solid #0f172a;
  transform: rotate(45deg);
}

.nav-title {
  position: absolute;
  right: 160rpx;
  left: 160rpx;
  color: #0f172a;
  font-size: 29rpx;
  font-weight: 800;
  line-height: 1;
  text-align: center;
}

.capsule,
.capsule-spacer {
  width: 162rpx;
  height: 58rpx;
}

.capsule {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  border: 1rpx solid #e2e8f0;
  border-radius: 999rpx;
  background: #ffffff;
  box-shadow: 0 5rpx 13rpx rgba(15, 23, 42, 0.06);
}

.capsule-dot {
  width: 11rpx;
  height: 11rpx;
  border-radius: 50%;
  background: #000000;
}

.capsule-divider {
  width: 1rpx;
  height: 31rpx;
  margin: 0 10rpx;
  background: #e5e7eb;
}

.capsule-circle {
  position: relative;
  width: 34rpx;
  height: 34rpx;
  border: 5rpx solid #000000;
  border-radius: 50%;
}

.capsule-circle::after {
  position: absolute;
  top: 8rpx;
  left: 8rpx;
  width: 8rpx;
  height: 8rpx;
  border-radius: 50%;
  background: #000000;
  content: "";
}

.rect-scroll {
  box-sizing: border-box;
}

.rect-content {
  padding: 18rpx 24rpx calc(28rpx + env(safe-area-inset-bottom));
}

.detail-card {
  padding: 30rpx 30rpx 30rpx;
  border: 1rpx solid #dfe7f1;
  border-radius: 11rpx;
  background: #ffffff;
  box-shadow: 0 6rpx 18rpx rgba(31, 46, 76, 0.025);
}

.info-row {
  position: relative;
  display: grid;
  grid-template-columns: 164rpx minmax(0, 1fr);
  column-gap: 18rpx;
  min-height: 70rpx;
  align-items: start;
}

.first-row {
  padding-right: 96rpx;
}

.problem-row {
  min-height: 128rpx;
}

.info-label {
  color: #60708a;
  font-size: 26rpx;
  font-weight: 700;
  line-height: 1.4;
}

.info-label.wide {
  letter-spacing: -1rpx;
}

.info-value {
  color: #0f172a;
  font-size: 27rpx;
  font-weight: 500;
  line-height: 1.45;
}

.order-value,
.deadline-value {
  white-space: nowrap;
}

.problem-text {
  white-space: pre-line;
}

.status-pill {
  position: absolute;
  top: -2rpx;
  right: 0;
  display: inline-flex;
  min-width: 80rpx;
  height: 48rpx;
  align-items: center;
  justify-content: center;
  border-radius: 8rpx;
  background: #fee2e2;
  color: #d92d20;
  font-size: 26rpx;
  font-weight: 900;
  line-height: 1;
}

.card-divider {
  height: 1rpx;
  margin: 20rpx 0 30rpx;
  background: #e2e8f0;
}

.section-title-row {
  margin-bottom: 20rpx;
}

.section-title {
  color: #0f172a;
  font-size: 27rpx;
  font-weight: 900;
  line-height: 1;
}

.before-photo-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr) 82rpx;
  gap: 22rpx;
  align-items: center;
}

.before-photo {
  width: 100%;
  height: 150rpx;
  border-radius: 8rpx;
  background: #e2e8f0;
}

.before-count {
  color: #60708a;
  font-size: 25rpx;
  font-weight: 500;
  white-space: nowrap;
}

.feedback-section {
  margin-top: 34rpx;
}

.feedback-box {
  position: relative;
  height: 174rpx;
  margin-top: 18rpx;
  border: 1rpx solid #dfe7f1;
  border-radius: 9rpx;
  background: #ffffff;
}

.feedback-input {
  width: 100%;
  height: 100%;
  padding: 24rpx 24rpx 48rpx;
  color: #0f172a;
  font-size: 25rpx;
  line-height: 1.4;
}

.feedback-placeholder {
  color: #8a97aa;
}

.feedback-count {
  position: absolute;
  right: 20rpx;
  bottom: 17rpx;
  color: #60708a;
  font-size: 24rpx;
}

.upload-section {
  margin-top: 34rpx;
}

.upload-title-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.upload-hint {
  color: #718096;
  font-size: 23rpx;
  font-weight: 600;
}

.upload-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr) 72rpx;
  gap: 25rpx;
  align-items: end;
  margin-top: 24rpx;
}

.upload-slot {
  display: flex;
  height: 144rpx;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 8rpx;
  background: #f1f4f8;
}

.upload-image {
  width: 100%;
  height: 100%;
}

.camera-icon {
  position: relative;
  width: 42rpx;
  height: 32rpx;
  border: 4rpx solid #60708a;
  border-radius: 5rpx;
}

.camera-icon::before {
  position: absolute;
  top: -11rpx;
  left: 9rpx;
  width: 16rpx;
  height: 10rpx;
  border: 4rpx solid #60708a;
  border-bottom: 0;
  border-radius: 7rpx 7rpx 0 0;
  content: "";
}

.camera-icon::after {
  position: absolute;
  top: 6rpx;
  left: 10rpx;
  width: 12rpx;
  height: 12rpx;
  border: 4rpx solid #60708a;
  border-radius: 50%;
  content: "";
}

.upload-count {
  padding-bottom: 8rpx;
  color: #60708a;
  font-size: 27rpx;
  font-weight: 500;
  white-space: nowrap;
}

.log-section {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  margin-top: 34rpx;
}

.log-item {
  padding: 18rpx 20rpx;
  border: 1rpx solid #dfe7f1;
  border-radius: 9rpx;
  background: #f8fbff;
}

.log-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
}

.log-action {
  color: #0f172a;
  font-size: 25rpx;
  font-weight: 900;
}

.log-time,
.log-operator,
.log-comment {
  display: block;
  color: #60708a;
  font-size: 23rpx;
  font-weight: 600;
  line-height: 1.5;
}

.log-operator,
.log-comment {
  margin-top: 8rpx;
}

.log-comment {
  color: #1f2a44;
  white-space: pre-line;
}

.bottom-actions {
  position: fixed;
  right: 24rpx;
  bottom: 0;
  left: 24rpx;
  z-index: 10;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 28rpx;
  padding: 22rpx 0 calc(24rpx + env(safe-area-inset-bottom));
  background: #ffffff;
}

.bottom-actions.single {
  grid-template-columns: 1fr;
}

.finish-button,
.close-button,
.reject-review-button {
  display: flex;
  width: 100%;
  height: 110rpx;
  align-items: center;
  justify-content: center;
  border-radius: 9rpx;
  font-size: 31rpx;
  font-weight: 900;
  line-height: 1;
}

.finish-button {
  background: linear-gradient(135deg, #0f9f8f 0%, #14b8a6 100%);
  color: #ffffff;
  box-shadow: 0 10rpx 18rpx rgba(15, 118, 110, 0.18);
}

.close-button,
.reject-review-button {
  border: 1rpx solid #b8c5d6;
  background: #ffffff;
  color: #0f172a;
}

.reject-review-button {
  border-color: #fb923c;
  color: #f97316;
}
</style>
