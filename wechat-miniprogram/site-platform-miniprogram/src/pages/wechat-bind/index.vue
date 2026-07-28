<script setup lang="ts">
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import { bindWechatAccount, requestWechatProjectAccess } from '@/api/auth';
import { useAuthStore } from '@/stores/auth';
import { showToast } from '@/utils/navigation';
import { getFreshWechatCode } from '@/utils/wechat';

const auth = useAuthStore();
const session = ref('');
const scene = ref('');
const returnUrl = ref('');
const username = ref('');
const password = ref('');
const submitting = ref(false);

onLoad((options) => {
  session.value = String(options?.session || '');
  scene.value = decodeURIComponent(String(options?.scene || ''));
  returnUrl.value = decodeURIComponent(String(options?.returnUrl || ''));
});

function goBack() {
  getCurrentPages().length > 1 ? uni.navigateBack() : uni.reLaunch({ url: '/pages/login/index' });
}

async function bindExistingAccount() {
  if (!username.value.trim() || !password.value) {
    showToast('请输入已有系统账号和密码');
    return;
  }
  submitting.value = true;
  try {
    const response = await bindWechatAccount({
      username: username.value.trim(),
      password: password.value,
      code: await getFreshWechatCode(),
      wechatSessionToken: session.value || undefined
    });
    await auth.completeLogin(response.token);
    showToast('微信绑定成功');
    if (returnUrl.value) {
      uni.reLaunch({ url: returnUrl.value });
    } else if (scene.value) {
      try {
        const access = await requestWechatProjectAccess(scene.value);
        if (access.bindingStatus === 'BOUND') {
          uni.redirectTo({ url: `/pages/scan-entry/index?scene=${encodeURIComponent(scene.value)}` });
        } else {
          showToast(access.message || '项目访问申请已提交');
          if (getCurrentPages().length > 1) uni.navigateBack();
          else auth.navigateAfterLogin();
        }
      } catch (accessError) {
        showToast(accessError instanceof Error
          ? `微信已绑定，项目访问申请失败：${accessError.message}`
          : '微信已绑定，项目访问申请失败，请稍后重试');
        if (getCurrentPages().length > 1) uni.navigateBack();
        else auth.navigateAfterLogin();
      }
    } else {
      auth.navigateAfterLogin();
    }
  } catch (error) {
    showToast(error instanceof Error ? error.message : '绑定失败');
  } finally {
    submitting.value = false;
  }
}

function applyForAccount() {
  const query = [
    session.value ? `session=${encodeURIComponent(session.value)}` : '',
    returnUrl.value ? `returnUrl=${encodeURIComponent(returnUrl.value)}` : ''
  ].filter(Boolean).join('&');
  uni.navigateTo({ url: `/pages/register/index${query ? `?${query}` : ''}` });
}
</script>

<template>
  <view class="page-shell">
    <AppNavBar title="绑定系统账号" @back="goBack" />
    <view class="content">
      <view class="identity-card">
        <view class="wechat-logo">微</view>
        <view><text class="title">此微信尚未绑定</text><text class="hint">绑定后可直接使用微信登录，无需重复输入账号密码。</text></view>
      </view>

      <view class="card">
        <text class="section-title">绑定已有账号</text>
        <text class="section-hint">为防止手机号误绑定，必须验证一次系统账号密码。</text>
        <input v-model="username" class="input" placeholder="系统账号" />
        <input v-model="password" class="input" password placeholder="系统密码" confirm-type="done" @confirm="bindExistingAccount" />
        <button class="primary" :disabled="submitting" @tap="bindExistingAccount">{{ submitting ? '正在验证并绑定…' : '验证并绑定' }}</button>
      </view>

      <view class="apply-card">
        <view><text>还没有系统账号？</text><text>提交注册申请，管理员审批并分配菜单及项目权限后即可登录。</text></view>
        <button @tap="applyForAccount">申请注册</button>
      </view>
    </view>
  </view>
</template>

<style scoped>
.page-shell{min-height:100vh;background:#f4f7fa}.content{display:flex;flex-direction:column;gap:22rpx;padding:28rpx}.identity-card,.card,.apply-card{border:1rpx solid #e0e8ef;border-radius:22rpx;background:#fff;box-shadow:0 10rpx 30rpx rgba(49,95,134,.06)}.identity-card{display:flex;align-items:center;gap:20rpx;padding:28rpx}.wechat-logo{display:flex;width:72rpx;height:72rpx;align-items:center;justify-content:center;flex-shrink:0;border-radius:50%;background:#26a65b;color:#fff;font-size:27rpx;font-weight:800}.identity-card text{display:block}.title{color:#223247;font-size:29rpx;font-weight:800}.hint{margin-top:8rpx;color:#7f8c9c;font-size:21rpx;line-height:1.6}.card{display:flex;flex-direction:column;gap:18rpx;padding:30rpx}.section-title{color:#223247;font-size:28rpx;font-weight:800}.section-hint{color:#7f8c9c;font-size:21rpx;line-height:1.6}.input{height:82rpx;padding:0 20rpx;border:1rpx solid #d5e0e7;border-radius:14rpx;background:#f9fbfc;font-size:24rpx}.primary{min-height:78rpx;border-radius:14rpx;background:#315f86;color:#fff;font-size:24rpx;font-weight:750}.primary[disabled]{opacity:.6}.apply-card{display:flex;align-items:center;justify-content:space-between;gap:18rpx;padding:24rpx}.apply-card view{min-width:0;flex:1}.apply-card text{display:block}.apply-card text:first-child{color:#344054;font-size:24rpx;font-weight:750}.apply-card text:last-child{margin-top:6rpx;color:#98a2b3;font-size:20rpx;line-height:1.5}.apply-card button{flex-shrink:0;padding:16rpx 20rpx;border-radius:12rpx;background:#edf4f8;color:#315f86;font-size:22rpx;font-weight:750}
</style>
