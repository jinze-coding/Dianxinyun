<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import { getRegistrationCaptcha, submitRegistrationApplication } from '@/api/registration';
import { showToast } from '@/utils/navigation';
import { getFreshWechatCode } from '@/utils/wechat';

const STATUS_TOKEN_KEY = 'registration_status_query_token';
const form = reactive({
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
const supportsWechatQuick = ref(false);
const isH5 = ref(false);
const registrationMode = ref<'WECHAT_QUICK' | 'STANDARD'>('STANDARD');
const quickMode = computed(() => registrationMode.value === 'WECHAT_QUICK');
const captcha = reactive({ id: '', image: '', code: '' });

// #ifdef MP-WEIXIN
supportsWechatQuick.value = true;
registrationMode.value = 'WECHAT_QUICK';
// #endif
// #ifdef H5
isH5.value = true;
// #endif

onLoad((options) => {
  returnUrl.value = decodeURIComponent(String(options?.returnUrl || ''));
  wechatSessionToken.value = String(options?.session || '');
  if (isH5.value) refreshCaptcha();
});

function goBack() {
  getCurrentPages().length > 1 ? uni.navigateBack() : uni.reLaunch({ url: '/pages/login/index' });
}

function validateApplicantInfo() {
  if (!form.realName.trim()) {
    showToast('请填写真实姓名');
    return false;
  }
  if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
    showToast('邮箱格式不正确');
    return false;
  }
  return true;
}

function validateManualAccount() {
  if (form.password.length < 8 || form.password.length > 72
    || !/[A-Za-z]/.test(form.password) || !/\d/.test(form.password)) {
    showToast('密码需为 8–72 位，且同时包含字母和数字');
    return false;
  }
  if (form.password !== form.confirmPassword) {
    showToast('两次输入的密码不一致');
    return false;
  }
  if (!/^1\d{10}$/.test(form.phone.trim())) {
    showToast('请输入正确手机号');
    return false;
  }
  if (isH5.value && !captcha.code.trim()) {
    showToast('请输入图形验证码');
    return false;
  }
  return true;
}

async function refreshCaptcha() {
  if (!isH5.value) return;
  try {
    const result = await getRegistrationCaptcha();
    captcha.id = result.captchaId;
    captcha.image = result.image;
    captcha.code = '';
  } catch (error) {
    showToast(error instanceof Error ? error.message : '图形验证码获取失败');
  }
}

async function submitApplication(phoneCode?: string) {
  if (!validateApplicantInfo()) return;
  if (quickMode.value && !phoneCode) {
    showToast('请先授权微信手机号');
    return;
  }
  if (!quickMode.value && !validateManualAccount()) return;

  submitting.value = true;
  try {
    const isQuick = quickMode.value;
    let wechatCode: string | undefined;
    // #ifdef MP-WEIXIN
    wechatCode = await getFreshWechatCode();
    // #endif
    const result = await submitRegistrationApplication({
      // 快捷注册的手机号、登录账号仅由服务端通过微信授权结果写入。
      username: isQuick ? undefined : form.phone.trim(),
      password: isQuick ? undefined : form.password,
      realName: form.realName.trim(),
      phone: isQuick ? undefined : form.phone.trim(),
      email: form.email.trim() || undefined,
      applicationReason: form.reason.trim() || undefined,
      desiredProjectIds: form.requestedProjectId ? [Number(form.requestedProjectId)] : undefined,
      sourceType: isH5.value ? 'WEB' : 'MINI',
      phoneVerificationType: isQuick ? 'WECHAT' : isH5.value ? 'MANUAL_REVIEW' : 'MANUAL',
      registrationMode: isQuick ? 'WECHAT_QUICK' : 'STANDARD',
      captchaId: isH5.value ? captcha.id : undefined,
      captchaCode: isH5.value ? captcha.code.trim() : undefined,
      wechatCode,
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
    if (isH5.value) refreshCaptcha();
    showToast(error instanceof Error ? error.message : '提交失败');
  } finally {
    submitting.value = false;
  }
}

function getPhone(event: { detail?: { code?: string } }) {
  if (!event.detail?.code) {
    showToast('未取得微信手机号，可改用手工手机号注册');
    return;
  }
  submitApplication(event.detail.code);
}

function switchMode() {
  if (!supportsWechatQuick.value) return;
  registrationMode.value = quickMode.value ? 'STANDARD' : 'WECHAT_QUICK';
}
</script>

<template>
  <view class="shell">
    <AppNavBar title="申请注册账号" @back="goBack" />
    <scroll-view scroll-y class="scroll">
      <view class="content">
        <view class="notice">
          <text>{{ quickMode ? '微信授权手机号将作为系统登录账号' : '手机号将作为系统登录账号' }}</text>
          <text v-if="quickMode">审批通过后请使用微信登录，并立即设置登录密码；设置完成前不能进入业务系统。</text>
          <text v-else>管理员会审核申请并分配项目角色、菜单和操作权限；提交申请不代表已获得系统访问权限。</text>
        </view>

        <view class="form-card">
          <text class="group-title">申请人信息</text>
          <label><text>真实姓名 *</text><input v-model="form.realName" placeholder="请填写真实姓名" /></label>

          <template v-if="!quickMode">
            <text class="group-title second">登录账号</text>
            <label><text>手机号（登录账号） *</text><input v-model="form.phone" type="number" maxlength="11" placeholder="请输入手机号" /></label>
            <label><text>登录密码 *</text><input v-model="form.password" password placeholder="至少 8 位，包含字母和数字" /></label>
            <label><text>确认密码 *</text><input v-model="form.confirmPassword" password placeholder="再次输入密码" /></label>
            <label v-if="isH5"><text>图形验证码 *</text><view class="captcha-row"><input v-model="captcha.code" placeholder="请输入验证码" /><image v-if="captcha.image" :src="captcha.image" mode="aspectFit" @tap="refreshCaptcha" /><button v-else @tap="refreshCaptcha">换一张</button></view></label>
          </template>

          <text class="group-title second">补充信息（选填）</text>
          <label><text>邮箱</text><input v-model="form.email" placeholder="选填" /></label>
          <label><text>期望项目 ID</text><input v-model="form.requestedProjectId" type="number" placeholder="选填，由管理员最终确认" /></label>
          <label><text>申请说明</text><textarea v-model="form.reason" maxlength="300" placeholder="可填写岗位、所属单位及申请原因" /></label>
        </view>

        <template v-if="quickMode">
          <button class="wechat-submit" open-type="getPhoneNumber" :disabled="submitting" @getphonenumber="getPhone">
            {{ submitting ? '正在提交…' : '微信快捷注册' }}
          </button>
          <button class="manual-submit" :disabled="submitting" @tap="switchMode">使用手工手机号注册</button>
        </template>
        <template v-else>
          <button class="manual-submit primary" :disabled="submitting" @tap="submitApplication()">
            {{ submitting ? '正在提交…' : '提交手工注册申请' }}
          </button>
          <button v-if="supportsWechatQuick" class="manual-submit" :disabled="submitting" @tap="switchMode">返回微信快捷注册</button>
        </template>
        <text class="privacy">手机号仅用于账号识别和审批联系。审批完成后可在登录页查询申请进度。</text>
      </view>
    </scroll-view>
  </view>
</template>

<style scoped>
.shell{min-height:100vh;background:#f4f7fa}.scroll{height:calc(100vh - 92rpx)}.content{display:flex;flex-direction:column;gap:20rpx;padding:26rpx 28rpx calc(44rpx + env(safe-area-inset-bottom))}.notice,.form-card{border:1rpx solid #e0e8ef;border-radius:20rpx;background:#fff}.notice{padding:22rpx;background:#edf5fb}.notice text{display:block}.notice text:first-child{color:#315f86;font-size:25rpx;font-weight:800}.notice text:last-child{margin-top:7rpx;color:#65798c;font-size:20rpx;line-height:1.6}.form-card{padding:28rpx}.group-title{display:block;margin-bottom:16rpx;color:#223247;font-size:27rpx;font-weight:800}.group-title.second{margin-top:28rpx}.form-card label{display:block;margin-top:17rpx}.form-card label>text{display:block;margin-bottom:8rpx;color:#52687a;font-size:21rpx}.form-card input,.form-card textarea{width:100%;padding:0 20rpx;border:1rpx solid #d5e0e7;border-radius:13rpx;background:#f9fbfc;font-size:23rpx}.form-card input{height:76rpx}.form-card textarea{height:150rpx;padding-top:18rpx}.captcha-row{display:flex;align-items:center;gap:14rpx}.captcha-row input{flex:1;min-width:0}.captcha-row image{width:160rpx;height:76rpx;border:1rpx solid #d5e0e7;border-radius:13rpx;background:#edf5fb}.captcha-row button{min-width:132rpx;height:76rpx;margin:0;border:1rpx solid #c8d8e5;border-radius:13rpx;background:#fff;color:#315f86;font-size:21rpx}.wechat-submit,.manual-submit{min-height:76rpx;border-radius:14rpx;font-size:23rpx;font-weight:750}.wechat-submit{background:#26a65b;color:#fff}.manual-submit{border:1rpx solid #c8d8e5;background:#fff;color:#315f86}.manual-submit.primary{border-color:#315f86;background:#315f86;color:#fff}.wechat-submit[disabled],.manual-submit[disabled]{opacity:.6}.privacy{padding:0 15rpx;color:#98a2b3;font-size:19rpx;line-height:1.6;text-align:center}
</style>
