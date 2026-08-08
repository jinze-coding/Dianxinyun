<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import {
  cancelRegistrationApplication,
  queryRegistrationApplicationStatus,
  type RegistrationApplicationStatus
} from '@/api/registration';
import { showToast } from '@/utils/navigation';

const STATUS_TOKEN_KEY = 'registration_status_query_token';
const token = ref('');
const status = ref<RegistrationApplicationStatus>();
const loading = ref(false);
const statusLabel = computed(() => status.value?.statusLabel || ({
  PENDING: '待审批',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  CANCELLED: '已取消'
} as Record<string, string>)[status.value?.status || ''] || '未查询');
const statusTone = computed(() => status.value?.status?.toLowerCase() || 'empty');
const isWechatQuick = computed(() => status.value?.registrationMode === 'WECHAT_QUICK');

onLoad((options) => {
  token.value = decodeURIComponent(String(options?.token || uni.getStorageSync(STATUS_TOKEN_KEY) || ''));
  if (token.value) query();
});

function goBack() {
  getCurrentPages().length > 1 ? uni.navigateBack() : uni.reLaunch({ url: '/pages/login/index' });
}

async function query() {
  if (!token.value.trim()) {
    showToast('请输入申请查询凭证');
    return;
  }
  loading.value = true;
  try {
    status.value = await queryRegistrationApplicationStatus(token.value.trim());
    uni.setStorageSync(STATUS_TOKEN_KEY, token.value.trim());
  } catch (error) {
    status.value = undefined;
    showToast(error instanceof Error ? error.message : '查询失败');
  } finally {
    loading.value = false;
  }
}

function backToLogin() {
  uni.reLaunch({ url: '/pages/login/index' });
}

function applyAgain() {
  uni.navigateTo({ url: '/pages/register/index' });
}

async function cancelApplication() {
  if (status.value?.status !== 'PENDING' || !token.value.trim()) return;
  const confirmed = await new Promise<boolean>((resolve) => {
    uni.showModal({
      title: '取消注册申请',
      content: '取消后需要重新提交申请，是否继续？',
      success: (result) => resolve(result.confirm),
      fail: () => resolve(false)
    });
  });
  if (!confirmed) return;
  loading.value = true;
  try {
    status.value = await cancelRegistrationApplication(token.value.trim());
    showToast('申请已取消');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '取消申请失败');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <view class="shell">
    <AppNavBar title="注册申请进度" @back="goBack" />
    <view class="content">
      <view class="query-card">
        <text class="title">查询申请状态</text>
        <text class="hint">查询凭证只保存在当前设备，不支持使用手机号枚举申请记录。</text>
        <input v-model="token" placeholder="申请查询凭证" />
        <button :disabled="loading" @tap="query">{{ loading ? '查询中…' : '查询' }}</button>
      </view>

      <view v-if="status" class="status-card">
        <view class="status-head">
          <view class="status-icon" :class="statusTone">{{ status.status === 'APPROVED' ? '✓' : status.status === 'REJECTED' ? '!' : '…' }}</view>
          <view><text>{{ statusLabel }}</text><text>{{ status.message || '申请状态已更新' }}</text></view>
        </view>
        <view class="row"><text>申请编号</text><text>{{ status.applicationNo || '—' }}</text></view>
        <view class="row"><text>申请账号</text><text>{{ status.username || '—' }}</text></view>
        <view v-if="status.desiredProjects?.length" class="project-row">
          <text>申请项目</text>
          <view><text v-for="project in status.desiredProjects" :key="project.projectId">{{ project.projectName }}</text></view>
        </view>
        <view class="row"><text>提交时间</text><text>{{ status.createTime || '—' }}</text></view>
        <view v-if="status.reviewTime" class="row"><text>审批时间</text><text>{{ status.reviewTime }}</text></view>
        <view v-if="status.reviewComment" class="comment"><text>审批意见</text><text>{{ status.reviewComment }}</text></view>
        <button v-if="status.status === 'PENDING'" class="cancel-link" :disabled="loading" @tap="cancelApplication">取消申请</button>
        <view v-if="status.status === 'APPROVED' && isWechatQuick" class="quick-approved-tip">
          <text>请使用微信登录</text>
          <text>首次登录后需要立即设置登录密码，完成前不能进入业务页面或使用 Web 扫码登录。</text>
        </view>
        <button v-if="status.status === 'APPROVED'" class="login-link" @tap="backToLogin">{{ isWechatQuick ? '去微信登录并设置密码' : '返回登录' }}</button>
        <button v-if="status.status === 'REJECTED' || status.status === 'CANCELLED'" class="login-link" @tap="applyAgain">重新申请</button>
      </view>
    </view>
  </view>
</template>

<style scoped>
.shell{min-height:100vh;background:#f4f7fa}.content{display:flex;flex-direction:column;gap:22rpx;padding:28rpx}.query-card,.status-card{padding:28rpx;border:1rpx solid #e0e8ef;border-radius:21rpx;background:#fff;box-shadow:0 10rpx 30rpx rgba(49,95,134,.05)}.query-card{display:flex;flex-direction:column;gap:16rpx}.title{color:#223247;font-size:29rpx;font-weight:800}.hint{color:#7f8c9c;font-size:20rpx;line-height:1.6}.query-card input{height:78rpx;padding:0 18rpx;border:1rpx solid #d5e0e7;border-radius:13rpx;background:#f9fbfc;font-size:22rpx}.query-card button,.login-link{min-height:74rpx;border-radius:13rpx;background:#315f86;color:#fff;font-size:23rpx;font-weight:750}.status-head{display:flex;align-items:center;gap:18rpx;padding-bottom:24rpx}.status-head text{display:block}.status-head text:first-child{color:#223247;font-size:29rpx;font-weight:800}.status-head text:last-child{margin-top:6rpx;color:#7f8c9c;font-size:20rpx}.status-icon{display:flex;width:64rpx;height:64rpx;align-items:center;justify-content:center;border-radius:50%;background:#fff5e8;color:#a96f2c;font-size:28rpx;font-weight:900}.status-icon.approved{background:#eaf6f1;color:#2f8065}.status-icon.rejected,.status-icon.cancelled{background:#fceeed;color:#b75353}.row{display:flex;min-height:72rpx;align-items:center;justify-content:space-between;border-top:1rpx solid #edf1f4;font-size:21rpx}.row text:first-child{color:#7f8c9c}.row text:last-child{color:#344054;font-weight:650}.comment,.quick-approved-tip{margin-top:16rpx;padding:18rpx;border-radius:12rpx;background:#f6f8fa}.comment text,.quick-approved-tip text{display:block}.comment text:first-child,.quick-approved-tip text:first-child{color:#7f8c9c;font-size:19rpx}.comment text:last-child,.quick-approved-tip text:last-child{margin-top:7rpx;color:#344054;font-size:21rpx;line-height:1.6}.quick-approved-tip{background:#edf7f1}.quick-approved-tip text:first-child{color:#23845a;font-weight:750}.login-link,.cancel-link{margin-top:22rpx}.cancel-link{min-height:74rpx;border:1rpx solid #efc5c5;border-radius:13rpx;background:#fff8f8;color:#b75353;font-size:23rpx;font-weight:750}
.project-row{display:flex;align-items:flex-start;justify-content:space-between;gap:18rpx;padding:18rpx 0;border-top:1rpx solid #edf1f4}.project-row>text{flex-shrink:0;color:#7f8c9c;font-size:21rpx}.project-row view{display:flex;flex-wrap:wrap;justify-content:flex-end;gap:8rpx}.project-row view text{padding:7rpx 11rpx;border-radius:999rpx;background:#edf5fb;color:#315f86;font-size:19rpx;font-weight:650}
</style>
