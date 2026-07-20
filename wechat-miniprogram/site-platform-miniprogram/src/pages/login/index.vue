<script setup lang="ts">
import { ref } from 'vue';
import { useAuthStore } from '@/stores/auth';
import { showToast, switchTab } from '@/utils/navigation';

const auth = useAuthStore();
const username = ref('');
const password = ref('');
const remember = ref(false);
const loading = ref(false);

async function submit() {
  if (!username.value || !password.value) {
    showToast('请输入用户名和密码');
    return;
  }
  loading.value = true;
  try {
    await auth.login(username.value, password.value);
    showToast('登录成功');
    switchTab('/pages/documents/index');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '登录失败');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <view class="login-shell">
    <view class="login-card">
      <view class="brand">
        <view class="brand-row">
          <view class="logo">
            <view class="cloud-shape"></view>
          </view>
          <text class="brand-title">电信云平台现场端</text>
        </view>
        <text class="brand-subtitle">电箱巡检 · 现场作业助手</text>
        <view class="env-pill">
          <text class="dot"></text>
          <text>环境：生产环境</text>
          <text class="chevron"></text>
        </view>
      </view>

      <view class="form">
        <view class="field">
          <text class="field-icon user-icon"></text>
          <input v-model="username" class="field-input" placeholder="请输入用户名" />
        </view>
        <view class="field">
          <text class="field-icon lock-icon"></text>
          <input v-model="password" class="field-input" password placeholder="请输入密码" />
          <text class="eye-icon"></text>
        </view>
        <view class="form-row">
          <label class="remember" @tap="remember = !remember">
            <text class="box" :class="{ checked: remember }"></text>
            <text>记住密码</text>
          </label>
          <button class="link" @tap="showToast('请联系平台管理员重置密码')">忘记密码?</button>
        </view>
        <button class="primary-button login-button" :disabled="loading" @tap="submit">
          {{ loading ? '登录中' : '登录' }}
        </button>
      </view>

      <view class="footer">
        <text>登录即表示同意</text>
        <text class="blue">《用户协议》</text>
        <text>和</text>
        <text class="blue">《隐私政策》</text>
      </view>
      <text class="version">v2.0.0</text>
    </view>
  </view>
</template>

<style scoped>
.login-shell {
  display: flex;
  min-height: 100vh;
  align-items: stretch;
  justify-content: center;
  padding: 0;
  background: #ffffff;
}

.login-card {
  position: relative;
  width: 100%;
  min-height: 100vh;
  height: 100vh;
  overflow: hidden;
  border: 0;
  border-radius: 0;
  background: #ffffff;
  box-shadow: none;
}

.brand {
  position: absolute;
  top: 18%;
  right: 0;
  left: 0;
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 11rpx;
}

.brand-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14rpx;
}

.logo {
  position: relative;
  display: flex;
  width: 76rpx;
  height: 58rpx;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.cloud-shape,
.cloud-shape::before,
.cloud-shape::after {
  position: absolute;
  border: 6rpx solid #1d4ed8;
  background: #ffffff;
}

.cloud-shape {
  bottom: 8rpx;
  left: 6rpx;
  width: 56rpx;
  height: 30rpx;
  border-radius: 25rpx;
}

.cloud-shape::before {
  content: "";
  left: 6rpx;
  bottom: 11rpx;
  width: 28rpx;
  height: 28rpx;
  border-radius: 50%;
}

.cloud-shape::after {
  content: "";
  right: 2rpx;
  bottom: 7rpx;
  width: 34rpx;
  height: 34rpx;
  border-radius: 50%;
}

.brand-title {
  color: #111827;
  font-size: 41rpx;
  font-weight: 900;
  line-height: 1;
}

.brand-subtitle {
  margin-top: 8rpx;
  color: #8a97aa;
  font-size: 26rpx;
  line-height: 1.2;
}

.env-pill {
  display: flex;
  align-items: center;
  gap: 9rpx;
  min-height: 50rpx;
  margin-top: 26rpx;
  padding: 0 22rpx;
  border: 1rpx solid #e5eaf2;
  border-radius: 999rpx;
  background: #ffffff;
  color: #475569;
  font-size: 22rpx;
  box-shadow: 0 6rpx 14rpx rgba(31, 46, 76, 0.035);
}

.dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 999rpx;
  background: #0f9f8f;
}

.chevron {
  width: 9rpx;
  height: 9rpx;
  margin-top: -4rpx;
  border-right: 2rpx solid #7b8798;
  border-bottom: 2rpx solid #7b8798;
  transform: rotate(45deg);
}

.form {
  position: absolute;
  top: 41%;
  right: 38rpx;
  left: 38rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  margin-top: 0;
}

.field {
  display: flex;
  height: 72rpx;
  align-items: center;
  gap: 14rpx;
  padding: 0 20rpx;
  border: 1rpx solid #dfe6ef;
  border-radius: 6rpx;
  background: #ffffff;
}

.field-icon {
  position: relative;
  width: 26rpx;
  height: 26rpx;
  flex-shrink: 0;
  color: #9aa7b8;
}

.field-input {
  flex: 1;
  height: 70rpx;
  color: #172033;
  font-size: 25rpx;
}

.user-icon::before {
  position: absolute;
  top: 1rpx;
  left: 8rpx;
  width: 10rpx;
  height: 10rpx;
  border: 2rpx solid currentColor;
  border-radius: 50%;
  content: "";
}

.user-icon::after {
  position: absolute;
  left: 3rpx;
  bottom: 0;
  width: 20rpx;
  height: 12rpx;
  border: 2rpx solid currentColor;
  border-radius: 12rpx 12rpx 2rpx 2rpx;
  content: "";
}

.lock-icon::before {
  position: absolute;
  top: 0;
  left: 6rpx;
  width: 14rpx;
  height: 13rpx;
  border: 2rpx solid currentColor;
  border-bottom: 0;
  border-radius: 9rpx 9rpx 0 0;
  content: "";
}

.lock-icon::after {
  position: absolute;
  left: 3rpx;
  bottom: 0;
  width: 20rpx;
  height: 16rpx;
  border: 2rpx solid currentColor;
  border-radius: 3rpx;
  content: "";
}

.eye-icon {
  position: relative;
  width: 32rpx;
  height: 20rpx;
  flex-shrink: 0;
  color: #4b5563;
}

.eye-icon::before {
  position: absolute;
  top: 4rpx;
  left: 2rpx;
  width: 25rpx;
  height: 12rpx;
  border: 2rpx solid currentColor;
  border-radius: 50%;
  content: "";
}

.eye-icon::after {
  position: absolute;
  top: 8rpx;
  left: 12rpx;
  width: 6rpx;
  height: 6rpx;
  border-radius: 50%;
  background: currentColor;
  content: "";
}

.form-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 30rpx;
  margin-top: -2rpx;
}

.remember {
  display: flex;
  align-items: center;
  gap: 8rpx;
  color: #7b8798;
  font-size: 23rpx;
}

.box {
  width: 24rpx;
  height: 24rpx;
  border: 1rpx solid #dfe6ef;
  border-radius: 4rpx;
}

.box.checked {
  border-color: #0f9f8f;
  background: #0f9f8f;
}

.link {
  color: #1d4ed8;
  font-size: 23rpx;
  line-height: 1;
}

.login-button {
  min-height: 72rpx;
  margin-top: 18rpx;
  border-radius: 6rpx;
  background: linear-gradient(135deg, #0f9f8f 0%, #1677ff 100%);
  box-shadow: 0 10rpx 18rpx rgba(15, 118, 110, 0.2);
  font-size: 27rpx;
  font-weight: 800;
}

.footer {
  position: absolute;
  right: 0;
  bottom: 58rpx;
  left: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 4rpx;
  color: #7b8798;
  font-size: 18rpx;
}

.blue {
  color: #1d4ed8;
}

.version {
  position: absolute;
  right: 0;
  bottom: 24rpx;
  left: 0;
  display: block;
  color: #94a3b8;
  font-size: 18rpx;
  text-align: center;
}
</style>
