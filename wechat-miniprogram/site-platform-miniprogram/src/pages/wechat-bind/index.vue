<script setup lang="ts">
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import { bindWechatPhone } from '@/api/auth';
import { showToast } from '@/utils/navigation';

const session = ref('');
const scene = ref('');
const realName = ref('');
const phone = ref('');
const submitting = ref(false);
const resultMessage = ref('');

onLoad((options) => {
  session.value = String(options?.session || '');
  scene.value = decodeURIComponent(String(options?.scene || ''));
});

async function submit(phoneCode?: string) {
  if (!realName.value.trim()) { showToast('请填写真实姓名'); return; }
  submitting.value = true;
  try {
    const response = await bindWechatPhone({
      wechatSessionToken: session.value,
      phoneCode,
      phone: phone.value,
      realName: realName.value,
      scene: scene.value
    });
    showToast(response.message);
    resultMessage.value = response.message;
    if (response.token) uni.redirectTo({ url: `/pages/scan-entry/index?scene=${encodeURIComponent(scene.value)}` });
  } catch (error) { showToast(error instanceof Error ? error.message : '提交失败'); }
  finally { submitting.value = false; }
}

function getPhone(event: { detail?: { code?: string; errMsg?: string } }) {
  if (!event.detail?.code) { showToast('需要授权手机号才能匹配内部账号'); return; }
  submit(event.detail.code);
}
</script>

<template>
  <view class="shell">
    <AppNavBar title="内部人员登录 / 注册" />
    <view class="content">
      <view class="card">
        <text class="title">绑定微信身份</text>
        <text class="hint">手机号只用于匹配现有平台账号；未匹配或没有当前项目权限时，将进入后台审批。</text>
        <input v-model="realName" class="input" placeholder="真实姓名" />
        <!-- H5/开发预览使用；正式小程序以 getPhoneNumber 授权结果为准 -->
        <input v-model="phone" class="input" type="number" placeholder="开发预览手机号" />
        <button class="primary" open-type="getPhoneNumber" :disabled="submitting" @getphonenumber="getPhone">微信手机号一键绑定</button>
        <button class="secondary" :disabled="submitting" @tap="submit()">开发预览提交</button>
        <text class="privacy">提交即表示同意平台隐私政策和用户协议。审批通过后，再次扫码即可自动进入有权限的业务页面。</text>
        <view v-if="resultMessage" class="result-message">{{ resultMessage }}</view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.shell{min-height:100vh;background:#eef7ff}.content{padding:32rpx}.card{display:flex;flex-direction:column;gap:22rpx;padding:36rpx;border-radius:24rpx;background:#fff}.title{font-size:36rpx;font-weight:800;color:#10233f}.hint,.privacy{font-size:25rpx;line-height:1.7;color:#71849b}.input{height:88rpx;padding:0 24rpx;border:1rpx solid #d9e5f2;border-radius:16rpx;background:#f8fbff}.primary,.secondary{margin:0;border-radius:18rpx;font-weight:700}.primary{background:#1677ff;color:#fff}.secondary{background:#eff6ff;color:#2563a7}.result-message{padding:18rpx 20rpx;border:1rpx solid #cde3fb;border-radius:16rpx;background:#eff6ff;color:#315f86;font-size:24rpx;line-height:1.6}
</style>
