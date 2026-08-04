<script setup lang="ts">
import { ref } from 'vue';
import { useAuthStore } from '@/stores/auth';
import { miniWechatLogin } from '@/api/auth';
import { showToast } from '@/utils/navigation';
import { getFreshWechatCode } from '@/utils/wechat';

const auth = useAuthStore();
const username = ref('');
const password = ref('');
const loading = ref(false);
const wechatLoading = ref(false);

async function submit() {
  if (!username.value.trim() || !password.value) {
    showToast('请输入账号和密码');
    return;
  }
  loading.value = true;
  try {
    await auth.login(username.value.trim(), password.value);
    showToast('登录成功');
    auth.navigateAfterLogin();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '登录失败');
  } finally {
    loading.value = false;
  }
}

async function wechatLogin() {
  if (wechatLoading.value) return;
  wechatLoading.value = true;
  try {
    const response = await miniWechatLogin(await getFreshWechatCode());
    if (response.token) {
      await auth.completeLogin(response.token);
      showToast('微信登录成功');
      auth.navigateAfterLogin();
      return;
    }
    if (response.bindingStatus === 'UNBOUND' || response.bindingStatus === 'APPLICATION_REJECTED') {
      uni.navigateTo({
        url: `/pages/wechat-bind/index?session=${encodeURIComponent(response.wechatSessionToken || '')}`
      });
      return;
    }
    showToast(response.message || '账号暂不可登录，请联系管理员');
  } catch (error) {
    const message = error instanceof Error ? error.message : '微信登录失败';
    if (message.includes('微信未授权请求地址')) {
      uni.showModal({
        title: '快捷登录连接诊断',
        content: message,
        showCancel: false,
        confirmText: '知道了'
      });
    } else {
      showToast(message);
    }
  } finally {
    wechatLoading.value = false;
  }
}

function openRegistration() {
  uni.navigateTo({ url: '/pages/register/index' });
}

function openRegistrationStatus() {
  uni.navigateTo({ url: '/pages/registration-status/index' });
}
</script>

<template>
  <view class="login-page">
    <view class="brand">
      <image class="logo" src="/static/brand/zhihui-yingzao-horizontal.png" mode="aspectFit" />
      <text class="subtitle">项目现场综合管理系统</text>
    </view>

    <view class="login-card">
      <text class="card-title">账号登录</text>
      <view class="field"><input v-model="username" placeholder="请输入账号" /></view>
      <view class="field"><input v-model="password" password placeholder="请输入密码" confirm-type="done" @confirm="submit" /></view>
      <button class="primary" :disabled="loading" @tap="submit">{{ loading ? '登录中…' : '登录' }}</button>

      <view class="divider"><text></text><label>其他登录方式</label><text></text></view>
      <button class="wechat" :disabled="wechatLoading" @tap="wechatLogin">
        <view class="wechat-mark">微</view>
        {{ wechatLoading ? '正在识别微信身份…' : '微信快捷登录' }}
      </button>

      <view class="links">
        <button @tap="openRegistration">申请注册账号</button>
        <text></text>
        <button @tap="openRegistrationStatus">查询申请进度</button>
      </view>
    </view>

    <text class="privacy">登录或注册即表示同意平台用户协议与隐私政策</text>
  </view>
</template>

<style scoped>
.login-page{min-height:100vh;padding:120rpx 38rpx 50rpx;background:linear-gradient(180deg,#edf5ff 0,#fff 46%);color:#1f2b3d}.brand{display:flex;align-items:center;flex-direction:column}.logo{width:360rpx;height:108rpx}.subtitle{margin-top:12rpx;color:#7f8c9c;font-size:23rpx}.login-card{display:flex;flex-direction:column;gap:20rpx;margin-top:70rpx;padding:34rpx 30rpx;border:1rpx solid #e2eaf0;border-radius:24rpx;background:#fff;box-shadow:0 18rpx 52rpx rgba(49,95,134,.1)}.card-title{font-size:30rpx;font-weight:800}.field{height:84rpx;padding:0 22rpx;border:1rpx solid #d5e0e7;border-radius:14rpx;background:#f9fbfc}.field input{height:82rpx;font-size:25rpx}.primary,.wechat{min-height:80rpx;border-radius:14rpx;font-size:25rpx;font-weight:750}.primary{background:#315f86;color:#fff}.wechat{gap:12rpx;border:1rpx solid #b9dfca;background:#f2fbf5;color:#28784b}.wechat-mark{display:flex;width:42rpx;height:42rpx;align-items:center;justify-content:center;border-radius:50%;background:#26a65b;color:#fff;font-size:19rpx}.primary[disabled],.wechat[disabled]{opacity:.65}.divider{display:flex;align-items:center;gap:14rpx;color:#98a2b3;font-size:20rpx}.divider text{height:1rpx;flex:1;background:#e8edf2}.links{display:flex;align-items:center;justify-content:center;gap:22rpx}.links button{color:#315f86;font-size:22rpx}.links text{width:1rpx;height:22rpx;background:#d9e2ea}.privacy{display:block;margin-top:34rpx;color:#98a2b3;font-size:19rpx;text-align:center}
</style>
