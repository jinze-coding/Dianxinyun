<script setup lang="ts">
import { reactive, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import { submitRegistrationApplication } from '@/api/registration';
import { showToast } from '@/utils/navigation';
import { getFreshWechatCode } from '@/utils/wechat';

const STATUS_TOKEN_KEY = 'registration_status_query_token';
const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  realName: '',
  phone: '',
  email: '',
  reason: '',
  requestedProjectId: ''
});
const submitting = ref(false);
const returnUrl = ref('');
const wechatSessionToken = ref('');

onLoad((options) => {
  returnUrl.value = decodeURIComponent(String(options?.returnUrl || ''));
  wechatSessionToken.value = String(options?.session || '');
});

function goBack() {
  getCurrentPages().length > 1 ? uni.navigateBack() : uni.reLaunch({ url: '/pages/login/index' });
}

async function submit(phoneCode?: string) {
  if (!/^[A-Za-z][A-Za-z0-9_-]{3,31}$/.test(form.username.trim())) {
    showToast('账号需以字母开头，长度 4–32 位');
    return;
  }
  if (form.password.length < 8 || form.password.length > 72
    || !/[A-Za-z]/.test(form.password) || !/\d/.test(form.password)) {
    showToast('密码需为 8–72 位，且同时包含字母和数字');
    return;
  }
  if (form.password !== form.confirmPassword) {
    showToast('两次输入的密码不一致');
    return;
  }
  if (!form.realName.trim()) {
    showToast('请填写真实姓名');
    return;
  }
  if (!phoneCode && !/^1\d{10}$/.test(form.phone.trim())) {
    showToast('请输入正确手机号，或使用微信手机号提交');
    return;
  }
  if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
    showToast('邮箱格式不正确');
    return;
  }

  submitting.value = true;
  try {
    const result = await submitRegistrationApplication({
      username: form.username.trim(),
      password: form.password,
      realName: form.realName.trim(),
      phone: form.phone.trim() || undefined,
      email: form.email.trim() || undefined,
      applicationReason: form.reason.trim() || undefined,
      desiredProjectIds: form.requestedProjectId ? [Number(form.requestedProjectId)] : undefined,
      sourceType: 'MINI',
      phoneVerificationType: phoneCode ? 'WECHAT' : 'MANUAL',
      wechatCode: await getFreshWechatCode(),
      wechatSessionToken: wechatSessionToken.value || undefined,
      phoneCode
    });
    if (!result.statusQueryToken) throw new Error('申请已提交，但未返回状态查询凭证，请联系管理员');
    uni.setStorageSync(STATUS_TOKEN_KEY, result.statusQueryToken);
    form.password = '';
    form.confirmPassword = '';
    showToast(result.message || '注册申请已提交');
    uni.redirectTo({
      url: `/pages/registration-status/index?token=${encodeURIComponent(result.statusQueryToken)}`
    });
  } catch (error) {
    showToast(error instanceof Error ? error.message : '提交失败');
  } finally {
    submitting.value = false;
  }
}

function getPhone(event: { detail?: { code?: string } }) {
  if (!event.detail?.code) {
    showToast('未取得微信手机号，可填写手机号后手工提交');
    return;
  }
  submit(event.detail.code);
}
</script>

<template>
  <view class="shell">
    <AppNavBar title="申请注册账号" @back="goBack" />
    <scroll-view scroll-y class="scroll">
      <view class="content">
        <view class="notice">
          <text>账号将在审批通过后创建</text>
          <text>管理员会同时分配菜单、操作权限和项目范围；提交申请不代表已获得系统访问权限。</text>
        </view>

        <view class="form-card">
          <text class="group-title">账号信息</text>
          <label><text>登录账号 *</text><input v-model="form.username" placeholder="字母开头，4–32 位" /></label>
          <label><text>登录密码 *</text><input v-model="form.password" password placeholder="至少 8 位，包含字母和数字" /></label>
          <label><text>确认密码 *</text><input v-model="form.confirmPassword" password placeholder="再次输入密码" /></label>

          <text class="group-title second">申请人信息</text>
          <label><text>真实姓名 *</text><input v-model="form.realName" placeholder="请填写真实姓名" /></label>
          <label><text>手机号 *</text><input v-model="form.phone" type="number" maxlength="11" placeholder="可填写手机号或使用微信手机号" /></label>
          <label><text>邮箱</text><input v-model="form.email" placeholder="选填" /></label>
          <label><text>期望项目 ID</text><input v-model="form.requestedProjectId" type="number" placeholder="选填，由管理员最终确认" /></label>
          <label><text>申请说明</text><textarea v-model="form.reason" maxlength="300" placeholder="可填写岗位、所属单位及申请原因" /></label>
        </view>

        <button class="wechat-submit" open-type="getPhoneNumber" :disabled="submitting" @getphonenumber="getPhone">
          {{ submitting ? '正在提交…' : '使用微信手机号提交' }}
        </button>
        <button class="manual-submit" :disabled="submitting" @tap="submit()">
          使用手工填写手机号提交
        </button>
        <text class="privacy">手机号仅用于账号识别和审批联系。审批完成后可在登录页查询申请进度。</text>
      </view>
    </scroll-view>
  </view>
</template>

<style scoped>
.shell{min-height:100vh;background:#f4f7fa}.scroll{height:calc(100vh - 92rpx)}.content{display:flex;flex-direction:column;gap:20rpx;padding:26rpx 28rpx calc(44rpx + env(safe-area-inset-bottom))}.notice,.form-card{border:1rpx solid #e0e8ef;border-radius:20rpx;background:#fff}.notice{padding:22rpx;background:#edf5fb}.notice text{display:block}.notice text:first-child{color:#315f86;font-size:25rpx;font-weight:800}.notice text:last-child{margin-top:7rpx;color:#65798c;font-size:20rpx;line-height:1.6}.form-card{padding:28rpx}.group-title{display:block;margin-bottom:16rpx;color:#223247;font-size:27rpx;font-weight:800}.group-title.second{margin-top:28rpx}.form-card label{display:block;margin-top:17rpx}.form-card label>text{display:block;margin-bottom:8rpx;color:#52687a;font-size:21rpx}.form-card input,.form-card textarea{width:100%;padding:0 20rpx;border:1rpx solid #d5e0e7;border-radius:13rpx;background:#f9fbfc;font-size:23rpx}.form-card input{height:76rpx}.form-card textarea{height:150rpx;padding-top:18rpx}.wechat-submit,.manual-submit{min-height:76rpx;border-radius:14rpx;font-size:23rpx;font-weight:750}.wechat-submit{background:#26a65b;color:#fff}.manual-submit{border:1rpx solid #c8d8e5;background:#fff;color:#315f86}.wechat-submit[disabled],.manual-submit[disabled]{opacity:.6}.privacy{padding:0 15rpx;color:#98a2b3;font-size:19rpx;line-height:1.6;text-align:center}
</style>
