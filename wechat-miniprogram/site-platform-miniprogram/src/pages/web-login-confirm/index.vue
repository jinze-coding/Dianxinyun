<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad, onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import { cancelWebQr, confirmWebQr, markWebQrScanned, miniWechatLogin, type WebQrChallengeInfo } from '@/api/auth';
import { useAuthStore } from '@/stores/auth';
import { showToast } from '@/utils/navigation';
import { getFreshWechatCode } from '@/utils/wechat';

const auth = useAuthStore();
const challengeId = ref('');
const info = ref<WebQrChallengeInfo>();
const loading = ref(true);
const acting = ref(false);
const finished = ref<'CONFIRMED' | 'CANCELLED'>();
const errorMessage = ref('');
const displaySite = computed(() => info.value?.siteName || '电信云平台 Web 管理端');
const displayBrowser = computed(() => info.value?.browserName || '发起扫码的浏览器');

onLoad((options) => {
  const rawValue = String(
    options?.challengeId
    || options?.webLoginChallenge
    || options?.scene
    || ''
  );
  let decoded = rawValue;
  try {
    decoded = decodeURIComponent(rawValue);
  } catch {
    decoded = rawValue;
  }
  challengeId.value = decoded.startsWith('L:') ? decoded.slice(2) : decoded;
});

onShow(async () => {
  if (!challengeId.value || finished.value) return;
  await prepare();
});

async function prepare() {
  loading.value = true;
  errorMessage.value = '';
  try {
    // 每次扫码都重新通过微信 code 校验当前微信身份，不能复用设备内可能属于其他微信的旧 Token。
    const response = await miniWechatLogin(await getFreshWechatCode());
    if (!response.token) {
      if (response.bindingStatus === 'UNBOUND' || response.bindingStatus === 'APPLICATION_REJECTED') {
        const returnUrl = `/pages/web-login-confirm/index?challengeId=${encodeURIComponent(challengeId.value)}`;
        uni.redirectTo({
          url: `/pages/wechat-bind/index?session=${encodeURIComponent(response.wechatSessionToken || '')}&returnUrl=${encodeURIComponent(returnUrl)}`
        });
        return;
      }
      throw new Error(response.message || '当前微信账号暂不能确认网页登录');
    }
    await auth.completeLogin(response.token);
    info.value = await markWebQrScanned(challengeId.value);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '扫码登录请求无效';
    showToast(errorMessage.value);
  } finally {
    loading.value = false;
  }
}

async function confirm() {
  if (acting.value) return;
  acting.value = true;
  try {
    await confirmWebQr(challengeId.value);
    finished.value = 'CONFIRMED';
    showToast('已确认网页登录');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '确认失败');
  } finally {
    acting.value = false;
  }
}

async function cancel() {
  if (acting.value) return;
  acting.value = true;
  try {
    await cancelWebQr(challengeId.value);
    finished.value = 'CANCELLED';
    showToast('已取消网页登录');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '取消失败');
  } finally {
    acting.value = false;
  }
}
</script>

<template>
  <view class="shell">
    <AppNavBar title="确认网页登录" :show-back="false" />
    <view class="content">
      <view v-if="loading" class="card state-card">
        <view class="spinner"></view>
        <text>正在验证扫码请求…</text>
      </view>
      <view v-else-if="errorMessage" class="card state-card">
        <view class="result-icon cancelled">!</view>
        <text>无法确认网页登录</text>
        <text>{{ errorMessage }}</text>
      </view>
      <view v-else-if="finished" class="card state-card">
        <view class="result-icon" :class="{ cancelled: finished === 'CANCELLED' }">{{ finished === 'CONFIRMED' ? '✓' : '×' }}</view>
        <text>{{ finished === 'CONFIRMED' ? '网页登录已确认' : '网页登录已取消' }}</text>
        <text>可以关闭此页面并返回电脑。</text>
      </view>
      <view v-else class="card">
        <view class="computer">PC</view>
        <text class="title">是否允许网页登录？</text>
        <text class="security">只有点击“确认登录”后，电脑端才能完成登录。二维码中不包含您的登录凭证。</text>
        <view class="detail"><text>登录站点</text><text>{{ displaySite }}</text></view>
        <view class="detail"><text>浏览器</text><text>{{ displayBrowser }}</text></view>
        <view v-if="info?.ipRegion" class="detail"><text>登录地区</text><text>{{ info.ipRegion }}</text></view>
        <view class="user"><text>当前账号</text><text>{{ auth.state.user?.realName || auth.state.user?.username }}</text></view>
        <button class="confirm" :disabled="acting" @tap="confirm">确认登录</button>
        <button class="cancel" :disabled="acting" @tap="cancel">取消</button>
      </view>
    </view>
  </view>
</template>

<style scoped>
.shell{min-height:100vh;background:#f4f7fa}.content{padding:60rpx 30rpx}.card{display:flex;align-items:center;flex-direction:column;padding:42rpx 32rpx;border:1rpx solid #e0e8ef;border-radius:24rpx;background:#fff;box-shadow:0 16rpx 42rpx rgba(49,95,134,.08)}.computer{display:flex;width:110rpx;height:82rpx;align-items:center;justify-content:center;border:6rpx solid #315f86;border-radius:10rpx;color:#315f86;font-size:27rpx;font-weight:900}.title{margin-top:28rpx;color:#223247;font-size:32rpx;font-weight:850}.security{margin:14rpx 0 28rpx;color:#7f8c9c;font-size:21rpx;line-height:1.65;text-align:center}.detail,.user{display:flex;width:100%;min-height:68rpx;align-items:center;justify-content:space-between;border-top:1rpx solid #edf1f4;font-size:21rpx}.detail text:first-child,.user text:first-child{color:#7f8c9c}.detail text:last-child,.user text:last-child{max-width:65%;color:#344054;font-weight:650;text-align:right}.user{margin-bottom:26rpx}.confirm,.cancel{width:100%;min-height:78rpx;border-radius:14rpx;font-size:24rpx;font-weight:750}.confirm{background:#315f86;color:#fff}.cancel{margin-top:14rpx;border:1rpx solid #d5e0e7;background:#fff;color:#52687a}.state-card{min-height:380rpx;justify-content:center;gap:18rpx;color:#52687a}.spinner{width:54rpx;height:54rpx;border:5rpx solid #dce8f0;border-top-color:#315f86;border-radius:50%}.result-icon{display:flex;width:88rpx;height:88rpx;align-items:center;justify-content:center;border-radius:50%;background:#eaf6f1;color:#2f8065;font-size:42rpx;font-weight:900}.result-icon.cancelled{background:#fceeed;color:#b75353}.state-card text:nth-of-type(1){color:#223247;font-size:28rpx;font-weight:800}.state-card text:nth-of-type(2){color:#7f8c9c;font-size:21rpx}
</style>
