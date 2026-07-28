<script setup lang="ts">
import { ref } from 'vue';
import { onLoad, onReady, onUnload } from '@dcloudio/uni-app';
import { resolveUnifiedCode } from '@/api/electricBox';
import { getToken } from '@/api/request';
import type { UnifiedElectricBoxScan } from '@/types';
import { extractElectricBoxScene } from '@/utils/electricBoxScan';

const message = ref('正在识别电箱巡检码');
const detail = ref('识别成功后将直接进入对应页面');
const failed = ref(false);
let pendingScene = '';
let routing = false;
let readyTimer: ReturnType<typeof setTimeout> | undefined;

onLoad((options) => {
  const webLoginChallenge = String(options?.webLoginChallenge || '');
  if (webLoginChallenge) {
    routing = true;
    uni.reLaunch({
      url: `/pages/web-login-confirm/index?webLoginChallenge=${encodeURIComponent(webLoginChallenge)}`
    });
    return;
  }
  const rawScene = String(options?.scene || options?.q || options?.code || '');
  try {
    pendingScene = extractElectricBoxScene(rawScene);
  } catch (error) {
    pendingScene = '';
  }
});

onReady(() => {
  // 必须等微信页面完成首次渲染后再执行二次跳转，否则开发者工具会让
  // “首页 navigateTo 中转页”和“中转页 redirectTo 目标页”互相抢占并超时。
  readyTimer = setTimeout(() => {
    readyTimer = undefined;
    void beginRouting();
  }, 160);
});

onUnload(() => {
  if (readyTimer) {
    clearTimeout(readyTimer);
    readyTimer = undefined;
  }
});

async function beginRouting() {
  if (routing) return;
  routing = true;
  if (!pendingScene) {
    showFailure('巡检码缺少场景参数');
    return;
  }
  try {
    const result = await resolveUnifiedCode(pendingScene);
    await routeByScanResult(result);
  } catch (error) {
    showFailure(error instanceof Error ? error.message : '巡检码解析失败');
  }
}

function openTarget(url: string) {
  return new Promise<void>((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('目标页面打开超时，请重试')), 5000);
    // 扫码中转页不需要保留在页面栈中。reLaunch 可以同时清理首页→中转页的
    // 半完成导航状态，避免开发者工具残留空白 WebView。
    uni.reLaunch({
      url,
      success: () => {
        clearTimeout(timer);
        resolve();
      },
      fail: (error) => {
        clearTimeout(timer);
        reject(new Error(error.errMsg || '目标页面打开失败'));
      }
    });
  });
}

async function routeByScanResult(result: UnifiedElectricBoxScan) {
  const actions = new Set(result.allowedActions || []);
  const directAction = result.directAction
    || (actions.has('DAILY_INSPECTION') ? 'START_INSPECTION'
      : actions.has('VIEW_RECORDS') ? 'VIEW_RECORDS'
        : actions.has('VIEW_PUBLIC_MONTHLY') ? 'VIEW_PUBLIC_MONTHLY' : 'UNAVAILABLE');

  if (directAction === 'START_INSPECTION' && result.electricBoxId) {
    const recordQuery = result.todayRecordId ? `&recordId=${result.todayRecordId}` : '';
    await openTarget(`/pages/inspection/form?boxId=${result.electricBoxId}${recordQuery}`);
    return;
  }
  if (directAction === 'VIEW_COMPLETED_RECORD') {
    if (result.todayRecordId) {
      await openTarget(`/pages/inspection/detail?id=${result.todayRecordId}`);
      return;
    }
    if (result.projectId && result.electricBoxId) {
      await openTarget(`/pages/inspection/records?projectId=${result.projectId}&boxId=${result.electricBoxId}`);
      return;
    }
  }
  if (directAction === 'VIEW_RECORDS' && result.projectId) {
    await openTarget(`/pages/inspection/records?projectId=${result.projectId}&boxId=${result.electricBoxId || ''}`);
    return;
  }
  if (directAction === 'VIEW_PUBLIC_MONTHLY' && result.publicCode && result.publicAccessEnabled) {
    await openTarget(`/pages/public/box-monthly?publicCode=${encodeURIComponent(result.publicCode)}`);
    return;
  }
  showFailure(result.reason || '当前账号不能操作该电箱');
}

function showFailure(text: string) {
  message.value = text;
  detail.value = '请返回巡检首页后重新扫码';
  failed.value = true;
}

function leave() {
  uni.reLaunch({ url: getToken() ? '/pages/inspection/index' : '/pages/login/index' });
}
</script>

<template>
  <view class="route-page">
    <view class="route-mark" :class="{ failed }"><text>{{ failed ? '!' : '码' }}</text></view>
    <text class="route-title">{{ message }}</text>
    <text class="route-desc">{{ detail }}</text>
    <button v-if="failed" class="route-back" @tap="leave">返回巡检首页</button>
  </view>
</template>

<style scoped>
.route-page { display: flex; min-height: 100vh; align-items: center; justify-content: center; flex-direction: column; padding: 40rpx; background: #f4f7fa; color: #223247; text-align: center; }
.route-mark { display: flex; width: 64rpx; height: 64rpx; align-items: center; justify-content: center; border: 1rpx solid var(--inspection-border); border-radius: 20rpx; background: var(--inspection-soft); }
.route-mark text { color: var(--inspection-primary-deep); font-size: 24rpx; font-weight: 800; }
.route-mark.failed { border-color: #efc9c9; background: var(--inspection-danger-soft); color: var(--inspection-danger); }
.route-mark.failed text { display: flex; align-items: center; justify-content: center; font-size: 30rpx; font-weight: 850; }
.route-title { margin-top: 22rpx; font-size: 25rpx; font-weight: 750; }
.route-desc { margin-top: 8rpx; color: #98a2b3; font-size: 20rpx; }
.route-back { display: flex; min-width: 240rpx; height: 70rpx; align-items: center; justify-content: center; margin-top: 30rpx; padding: 0 24rpx; border: 1rpx solid var(--inspection-border); border-radius: 16rpx; background: var(--inspection-soft); color: var(--inspection-primary-deep); font-size: 22rpx; font-weight: 750; line-height: 1; }
.route-back::after { border: 0; }
.route-back:active { background: var(--inspection-soft-strong); }
</style>
