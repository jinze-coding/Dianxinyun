<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app';
import { ref } from 'vue';
import AppNavBar from '@/components/AppNavBar.vue';
import { setupInitialPassword } from '@/api/auth';
import { useAuthStore } from '@/stores/auth';
import { showToast } from '@/utils/navigation';

const auth = useAuthStore();
const password = ref('');
const confirmPassword = ref('');
const submitting = ref(false);

onLoad(async () => {
  try {
    if (!auth.state.user) await auth.loadUser();
    if (!auth.requiresInitialPasswordSetup()) auth.navigateAfterLogin();
  } catch {
    uni.reLaunch({ url: '/pages/login/index' });
  }
});

async function submit() {
  if (password.value.length < 8 || password.value.length > 72
    || !/[A-Za-z]/.test(password.value) || !/\d/.test(password.value)) {
    showToast('密码需为 8–72 位，且同时包含字母和数字');
    return;
  }
  if (password.value !== confirmPassword.value) {
    showToast('两次输入的密码不一致');
    return;
  }
  submitting.value = true;
  try {
    const result = await setupInitialPassword(password.value);
    await auth.completeLogin(result.token);
    password.value = '';
    confirmPassword.value = '';
    showToast('密码设置成功');
    auth.navigateAfterLogin();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '密码设置失败');
  } finally {
    submitting.value = false;
  }
}

async function logout() {
  await auth.logout();
  uni.reLaunch({ url: '/pages/login/index' });
}
</script>

<template>
  <view class="shell">
    <AppNavBar title="设置登录密码" />
    <view class="content">
      <view class="notice">
        <text>请先设置登录密码</text>
        <text>这是首次微信登录的必要步骤。设置完成后，手机号可用于小程序和 Web 账号密码登录。</text>
      </view>

      <view class="form-card">
        <label><text>登录账号</text><view class="readonly">{{ auth.state.user?.username || auth.state.user?.phone || '微信手机号' }}</view></label>
        <label><text>设置密码 *</text><input v-model="password" password placeholder="8–72 位，包含字母和数字" /></label>
        <label><text>确认密码 *</text><input v-model="confirmPassword" password placeholder="再次输入密码" /></label>
      </view>

      <button class="submit" :disabled="submitting" @tap="submit">{{ submitting ? '正在设置…' : '确认并进入系统' }}</button>
      <button class="logout" :disabled="submitting" @tap="logout">退出登录</button>
    </view>
  </view>
</template>

<style scoped>
.shell{min-height:100vh;background:#f4f7fa}.content{display:flex;flex-direction:column;gap:22rpx;padding:28rpx}.notice,.form-card{border:1rpx solid #e0e8ef;border-radius:20rpx;background:#fff}.notice{padding:24rpx;background:#edf5fb}.notice text{display:block}.notice text:first-child{color:#315f86;font-size:27rpx;font-weight:800}.notice text:last-child{margin-top:8rpx;color:#65798c;font-size:21rpx;line-height:1.65}.form-card{padding:28rpx}.form-card label{display:block;margin-top:20rpx}.form-card label:first-child{margin-top:0}.form-card label>text{display:block;margin-bottom:9rpx;color:#52687a;font-size:21rpx}.form-card input,.readonly{box-sizing:border-box;width:100%;height:78rpx;padding:0 20rpx;border:1rpx solid #d5e0e7;border-radius:13rpx;background:#f9fbfc;font-size:23rpx}.form-card input{line-height:78rpx}.readonly{display:flex;align-items:center;color:#66788a}.submit,.logout{min-height:78rpx;border-radius:14rpx;font-size:24rpx;font-weight:750}.submit{background:#315f86;color:#fff}.logout{border:1rpx solid #c8d8e5;background:#fff;color:#52687a}.submit[disabled],.logout[disabled]{opacity:.6}
</style>
