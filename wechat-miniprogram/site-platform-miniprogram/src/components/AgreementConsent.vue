<script setup lang="ts">
import { openPrivacyPolicy } from '@/utils/agreementConsent';
const props = withDefaults(defineProps<{ modelValue: boolean; disabled?: boolean }>(), { disabled: false });
const emit = defineEmits<{ (event: 'update:modelValue', value: boolean): void }>();
function change(event: { detail?: { value?: string[] } }) {
  if (!props.disabled) emit('update:modelValue', event.detail?.value?.includes('agreed') === true);
}
function openTerms() { uni.navigateTo({ url: '/pages/legal/index' }); }
</script>

<template>
  <view class="agreement-consent">
    <view class="agreement-row">
      <checkbox-group @change="change">
        <label class="agreement-choice"><checkbox class="agreement-checkbox" value="agreed" :checked="modelValue" :disabled="disabled" color="#315f86" /><text>我已阅读并同意</text></label>
      </checkbox-group>
      <button class="agreement-link" @tap.stop="openTerms">《用户服务协议》</button>
      <text>及</text>
      <button class="agreement-link" @tap.stop="openPrivacyPolicy">《隐私政策》</button>
    </view>
    <text v-if="!modelValue" class="agreement-hint">请自主选择；未同意前不会提交登录、注册或绑定信息。</text>
  </view>
</template>

<style scoped>
.agreement-consent{padding:18rpx 0;color:#52687a;font-size:22rpx;line-height:1.7}.agreement-row{display:flex;align-items:center;flex-wrap:wrap;gap:4rpx}.agreement-choice{display:flex;align-items:center;gap:6rpx}.agreement-checkbox{flex-shrink:0;transform:scale(.85);transform-origin:left center}.agreement-link{display:inline;margin:0;padding:4rpx 0;min-height:0;border:0;border-radius:0;background:transparent;color:#246aa5;font-size:22rpx;font-weight:600;line-height:1.7;text-align:left}.agreement-link::after{border:0}.agreement-hint{display:block;margin-top:10rpx;color:#7f8c9c;font-size:20rpx;line-height:1.6}
</style>
