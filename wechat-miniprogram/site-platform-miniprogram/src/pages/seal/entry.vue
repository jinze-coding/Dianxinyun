<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import { resolveSealEntry } from '@/api/seal';
import { useAuthStore } from '@/stores/auth';
import type { SealEntryResolution } from '@/types';
import { ensureSealPageAccess } from '@/utils/sealAccess';
import { extractSealScene, extractSealSceneFromScanResult, SEAL_SCAN_TYPES } from '@/utils/sealScene';
import { navigateTo, showToast } from '@/utils/navigation';

const auth = useAuthStore();
const scene = ref('');
const entry = ref<SealEntryResolution | null>(null);
const loading = ref(true);
const errorMessage = ref('');
const configured = computed(() => entry.value?.configured !== false && entry.value?.active !== false);

onLoad(async (options) => {
  scene.value = extractSealScene(String(options?.scene || options?.q || options?.code || ''));
  await initialize();
});

async function initialize() {
  loading.value = true;
  errorMessage.value = '';
  const resumeUrl = `/pages/seal/entry${scene.value ? `?scene=${encodeURIComponent(scene.value)}` : ''}`;
  if (!await ensureSealPageAccess(resumeUrl)) { loading.value = false; return; }
  if (!scene.value) {
    errorMessage.value = '未识别到有效的用印二维码';
    loading.value = false;
    return;
  }
  try {
    entry.value = await resolveSealEntry(scene.value);
    if (entry.value.active === false) errorMessage.value = entry.value.message || '该印章二维码已停用';
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '用印二维码解析失败';
  } finally {
    loading.value = false;
  }
}

function startApplication() {
  if (!entry.value || !configured.value) return;
  navigateTo(`/pages/seal/apply?scene=${encodeURIComponent(scene.value)}`);
}

function scanAgain() {
  uni.scanCode({
    scanType: SEAL_SCAN_TYPES,
    success: async (result) => {
      const nextScene = extractSealSceneFromScanResult(result);
      if (!nextScene) { showToast('这不是有效的用印申请码'); return; }
      scene.value = nextScene;
      await initialize();
    },
    fail: (error) => {
      if (!String(error.errMsg || '').includes('cancel')) showToast('扫码失败，请重试');
    }
  });
}

function goBack() {
  const pages = getCurrentPages();
  if (pages.length > 1) uni.navigateBack();
  else uni.reLaunch({ url: '/pages/documents/index' });
}
</script>

<template>
  <view class="entry-page">
    <AppNavBar title="扫码用印" @back="goBack" />
    <view class="entry-content">
      <view class="seal-hero" :class="{ error: errorMessage }">
        <view class="seal-mark">印</view>
        <text>{{ loading ? '正在核验用印二维码' : errorMessage ? '二维码暂不可用' : '印章信息已核验' }}</text>
        <text>{{ loading ? '请稍候' : errorMessage || '请确认项目和印章后填写申请' }}</text>
      </view>

      <view v-if="entry" class="info-card">
        <view class="info-row"><text>公司</text><text>{{ entry.companyName || '以用印配置为准' }}</text></view>
        <view class="info-row"><text>项目部</text><text>{{ entry.projectName }}</text></view>
        <view class="info-row"><text>使用印章</text><text>{{ entry.sealName }}</text></view>
        <view class="info-row"><text>申请人</text><text>{{ auth.state.user?.realName || auth.state.user?.username }}</text></view>
      </view>

      <view v-if="entry && !configured" class="warning-card">该印章尚未配置审批人，请联系系统管理员完成审批配置后再申请。</view>

      <view class="flow-card">
        <text>手机申请流程</text>
        <view><text>1</text><text>填写事由和用印文件清单</text></view>
        <view><text>2</text><text>上传需要盖章的原始资料</text></view>
        <view><text>3</text><text>提交项目经理审批并接收通知</text></view>
        <view><text>4</text><text>审批后补传盖章件，可归档到资料库</text></view>
      </view>

      <button v-if="entry && configured" class="primary" :disabled="loading" @tap="startApplication">发起用印申请</button>
      <button v-if="!entry || errorMessage" class="secondary" :disabled="loading" @tap="scanAgain">重新扫码</button>
      <button class="link" @tap="navigateTo('/pages/seal/list')">查看我的用印申请</button>
    </view>
  </view>
</template>

<style scoped>
.entry-page { min-height: 100vh; background: #f4f6f7; color: #223247; }.entry-content { display: flex; flex-direction: column; gap: 20rpx; padding: 30rpx 26rpx calc(40rpx + env(safe-area-inset-bottom)); }
.seal-hero { display: flex; align-items: center; flex-direction: column; padding: 34rpx 24rpx; border-radius: 22rpx; background: linear-gradient(145deg,#f7efe4,#fff); box-shadow: 0 12rpx 30rpx rgba(107,76,35,.08); text-align: center; }.seal-hero.error { background: #fff5f4; }.seal-mark { display: flex; width: 86rpx; height: 86rpx; align-items: center; justify-content: center; border: 4rpx solid #9b6929; border-radius: 20rpx; color: #9b6929; font-size: 38rpx; font-weight: 900; transform: rotate(-4deg); }.error .seal-mark { border-color: #b75353; color: #b75353; }.seal-hero>text:nth-child(2) { margin-top: 20rpx; font-size: 28rpx; font-weight: 850; }.seal-hero>text:last-child { margin-top: 8rpx; color: #7b8794; font-size: 21rpx; line-height: 1.5; }
.info-card,.flow-card { padding: 5rpx 22rpx; border-radius: 18rpx; background: #fff; box-shadow: 0 8rpx 26rpx rgba(43,56,72,.055); }.info-row { display: flex; min-height: 78rpx; align-items: center; justify-content: space-between; gap: 24rpx; border-bottom: 1rpx solid #edf0f2; }.info-row:last-child { border-bottom: 0; }.info-row text:first-child { flex-shrink: 0; color: #7e8b98; font-size: 21rpx; }.info-row text:last-child { color: #344054; font-size: 22rpx; font-weight: 700; text-align: right; }
.warning-card { padding: 19rpx; border: 1rpx solid #efceca; border-radius: 14rpx; background: #fff7f6; color: #a8544b; font-size: 21rpx; line-height: 1.55; }.flow-card { padding-top: 22rpx; padding-bottom: 22rpx; }.flow-card>text { display: block; margin-bottom: 10rpx; font-size: 24rpx; font-weight: 800; }.flow-card>view { display: flex; align-items: center; gap: 14rpx; min-height: 54rpx; color: #667586; font-size: 21rpx; }.flow-card>view text:first-child { display: flex; width: 34rpx; height: 34rpx; align-items: center; justify-content: center; border-radius: 50%; background: #e7eef3; color: #3f657e; font-size: 18rpx; font-weight: 800; }
.primary,.secondary,.link { min-height: 78rpx; border-radius: 14rpx; font-size: 24rpx; font-weight: 780; }.primary { background: #8a612c; color: #fff; }.secondary { border: 1rpx solid #cbd7df; background: #fff; color: #42667f; }.link { min-height: 60rpx; color: #42667f; font-size: 21rpx; }
</style>
