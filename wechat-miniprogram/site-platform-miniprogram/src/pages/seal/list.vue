<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad, onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import { getSealApplications } from '@/api/seal';
import { useProjectStore } from '@/stores/project';
import type { SealApplication, SealApplicationStatus } from '@/types';
import { ensureSealPageAccess } from '@/utils/sealAccess';
import { extractSealSceneFromScanResult, SEAL_SCAN_TYPES } from '@/utils/sealScene';
import { getQueryNumber, navigateTo, showToast } from '@/utils/navigation';

type Scope = 'INITIATED' | 'PENDING_FOR_ME' | 'CC_TO_ME';
const PAGE_SIZE = 20;
const projectStore = useProjectStore();
const scope = ref<Scope>('INITIATED');
const projectId = ref(0);
const status = ref<SealApplicationStatus | ''>('');
const keyword = ref('');
const appliedKeyword = ref('');
const applications = ref<SealApplication[]>([]);
const total = ref(0);
const pageNo = ref(1);
const loading = ref(false);
const loadingMore = ref(false);
const initialized = ref(false);
const errorMessage = ref('');

const projectIndex = computed(() => Math.max(0, projectStore.state.projects.findIndex((item) => item.id === projectId.value)));
const currentProject = computed(() => projectStore.state.projects.find((item) => item.id === projectId.value));
const hasMore = computed(() => applications.value.length < total.value);
const scopeOptions: Array<{ key: Scope; label: string }> = [
  { key: 'INITIATED', label: '我发起的' }, { key: 'PENDING_FOR_ME', label: '待我审批' }, { key: 'CC_TO_ME', label: '抄送我的' }
];
const statusOptions: Array<{ value: SealApplicationStatus | ''; label: string }> = [
  { value: '', label: '全部' }, { value: 'DRAFT', label: '草稿' }, { value: 'PENDING_APPROVAL', label: '审批中' },
  { value: 'APPROVED', label: '已批准' }, { value: 'REJECTED', label: '已驳回' }, { value: 'WITHDRAWN', label: '已撤回' }
];

onLoad((options) => { projectId.value = getQueryNumber(options?.projectId, 0); });
onShow(async () => {
  if (!await ensureSealPageAccess(`/pages/seal/list${projectId.value ? `?projectId=${projectId.value}` : ''}`)) return;
  if (!initialized.value) {
    await projectStore.loadProjects();
    if (!projectStore.state.projects.some((item) => item.id === projectId.value)) projectId.value = projectStore.state.currentProjectId || 0;
    initialized.value = true;
  }
  await refresh();
});

async function refresh() {
  loading.value = true;
  errorMessage.value = '';
  try {
    pageNo.value = 1;
    const result = await getSealApplications({ projectId: projectId.value || undefined, scope: scope.value,
      status: status.value, keyword: appliedKeyword.value, pageNo: 1, pageSize: PAGE_SIZE });
    applications.value = result.records || [];
    total.value = Number(result.total || 0);
  } catch (error) {
    applications.value = [];
    total.value = 0;
    errorMessage.value = error instanceof Error ? error.message : '用印申请加载失败';
  } finally { loading.value = false; }
}

async function loadMore() {
  if (loading.value || loadingMore.value || !hasMore.value) return;
  loadingMore.value = true;
  try {
    const next = pageNo.value + 1;
    const result = await getSealApplications({ projectId: projectId.value || undefined, scope: scope.value,
      status: status.value, keyword: appliedKeyword.value, pageNo: next, pageSize: PAGE_SIZE });
    applications.value = [...applications.value, ...(result.records || [])];
    total.value = Number(result.total || 0);
    pageNo.value = next;
  } finally { loadingMore.value = false; }
}

async function changeProject(event: unknown) {
  const index = Number((event as { detail?: { value?: string | number } }).detail?.value || 0);
  projectId.value = projectStore.state.projects[index]?.id || 0;
  await refresh();
}

async function changeScope(value: Scope) { scope.value = value; await refresh(); }
async function changeStatus(value: SealApplicationStatus | '') { status.value = value; await refresh(); }
async function search() { appliedKeyword.value = keyword.value.trim(); await refresh(); }

function statusLabel(value: SealApplicationStatus) {
  return ({ DRAFT: '草稿', PENDING_APPROVAL: '审批中', APPROVED: '已批准', REJECTED: '已驳回', WITHDRAWN: '已撤回' } as Record<string,string>)[value] || value;
}

function scanSeal() {
  uni.scanCode({
    scanType: SEAL_SCAN_TYPES,
    success: (result) => {
      const scene = extractSealSceneFromScanResult(result);
      if (!scene) { showToast('这不是有效的用印申请码'); return; }
      navigateTo(`/pages/seal/entry?scene=${encodeURIComponent(scene)}`);
    },
    fail: (error) => { if (!String(error.errMsg || '').includes('cancel')) showToast('扫码失败，请重试'); }
  });
}

function directApply() {
  if (!projectId.value) { showToast('请先选择施工区域'); return; }
  navigateTo(`/pages/seal/apply?projectId=${projectId.value}`);
}

function goBack() { getCurrentPages().length > 1 ? uni.navigateBack() : uni.switchTab({ url: '/pages/documents/index' }); }
</script>

<template>
  <view class="list-page">
    <AppNavBar title="用印申请" @back="goBack" />
    <view class="scope-tabs"><button v-for="item in scopeOptions" :key="item.key" :class="{ active: scope === item.key }" @tap="changeScope(item.key)">{{ item.label }}</button></view>
    <view class="toolbar">
      <picker :range="projectStore.state.projects" range-key="projectName" :value="projectIndex" @change="changeProject"><view class="project-picker">{{ currentProject?.projectName || '全部授权项目' }}<text>⌄</text></view></picker>
      <view class="actions"><button @tap="directApply">选择印章</button><button class="scan" @tap="scanSeal">扫码发起</button></view>
    </view>
    <view class="search-row"><view><text>⌕</text><input v-model="keyword" placeholder="申请编号、事由或文件名称" confirm-type="search" @confirm="search" /><button v-if="keyword" @tap="keyword=''; search()">×</button></view></view>
    <scroll-view class="status-scroll" scroll-x><view class="status-row"><button v-for="item in statusOptions" :key="item.value" :class="{ active: status === item.value }" @tap="changeStatus(item.value)">{{ item.label }}</button></view></scroll-view>

    <scroll-view class="application-scroll" scroll-y enable-flex lower-threshold="120" refresher-enabled :refresher-triggered="loading" @refresherrefresh="refresh" @scrolltolower="loadMore">
      <view class="list-content">
        <view v-if="errorMessage" class="state-card error"><text>{{ errorMessage }}</text><button @tap="refresh">重新加载</button></view>
        <button v-for="item in applications" :key="item.id" class="application-card" @tap="navigateTo(`/pages/seal/detail?id=${item.id}`)">
          <view class="card-head"><view><text>{{ item.applicationNo || `草稿 #${item.id}` }}</text><text>{{ item.sealName }}</text></view><text class="status" :class="item.status.toLowerCase()">{{ item.statusLabel || statusLabel(item.status) }}</text></view>
          <text class="purpose">{{ item.purpose || '尚未填写用印事由' }}</text>
          <view class="meta"><text>{{ item.projectName }}</text><text>{{ item.applicantName || '当前申请人' }}</text><text>{{ (item.submitTime || item.createTime || '').replace('T',' ').slice(0,16) }}</text></view>
          <view class="file-summary"><text>用印文件 {{ item.items?.length || 0 }} 项</text><text>›</text></view>
        </button>
        <view v-if="loading && !applications.length" class="state-card">正在加载用印申请…</view>
        <view v-else-if="!applications.length && !errorMessage" class="empty-card"><text>印</text><text>{{ scope === 'PENDING_FOR_ME' ? '暂无待审批申请' : scope === 'CC_TO_ME' ? '暂无抄送申请' : '暂无用印申请' }}</text><text>可选择印章或扫描现场二维码发起</text></view>
        <view v-if="loadingMore" class="load-more">正在加载更多</view><view v-else-if="applications.length && !hasMore" class="load-more">已显示全部 {{ total }} 条</view>
      </view>
    </scroll-view>
  </view>
</template>

<style scoped>
.list-page { height: 100vh; overflow: hidden; background: #f4f6f7; color: #223247; }.scope-tabs { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 8rpx; margin: 8rpx 24rpx 0; padding: 7rpx; border-radius: 16rpx; background: #e7edf1; }.scope-tabs button { min-height: 58rpx; border-radius: 12rpx; color: #6e7d8c; font-size: 21rpx; font-weight: 700; }.scope-tabs button.active { background: #fff; color: #315f86; box-shadow: 0 4rpx 14rpx rgba(49,95,134,.09); }
.toolbar { display: flex; align-items: center; justify-content: space-between; gap: 14rpx; padding: 16rpx 24rpx 10rpx; }.toolbar picker { min-width: 0; flex: 1; }.project-picker { display: flex; min-height: 64rpx; align-items: center; justify-content: space-between; gap: 8rpx; padding: 0 14rpx; border-radius: 13rpx; background: #fff; color: #405468; font-size: 20rpx; font-weight: 700; }.actions { display: flex; gap: 9rpx; }.actions button { min-height: 64rpx; padding: 0 16rpx; border-radius: 13rpx; background: #e7eef3; color: #42667f; font-size: 19rpx; font-weight: 750; }.actions button.scan { background: #8a612c; color: #fff; }
.search-row { padding: 0 24rpx 10rpx; }.search-row>view { display: flex; height: 66rpx; align-items: center; gap: 9rpx; padding: 0 14rpx; border: 1rpx solid #e0e6ea; border-radius: 13rpx; background: #fff; }.search-row input { min-width: 0; flex: 1; font-size: 21rpx; }.search-row button { width: 40rpx; height: 40rpx; border-radius: 50%; background: #eef1f3; color: #778492; font-size: 24rpx; }
.status-scroll { width: 100%; white-space: nowrap; }.status-row { display: inline-flex; gap: 9rpx; padding: 0 24rpx 12rpx; }.status-row button { min-height: 50rpx; padding: 0 18rpx; border-radius: 999rpx; background: #e8edf1; color: #74818e; font-size: 19rpx; }.status-row button.active { background: #dce9f0; color: #315f86; font-weight: 750; }.application-scroll { height: calc(100vh - 420rpx); }.list-content { display: flex; flex-direction: column; gap: 14rpx; padding: 4rpx 24rpx 34rpx; }
.application-card { display: block; width: 100%; padding: 20rpx; border: 1rpx solid #e1e7eb; border-radius: 18rpx; background: #fff; box-shadow: 0 8rpx 24rpx rgba(43,56,72,.05); text-align: left; }.card-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 15rpx; }.card-head view { min-width: 0; }.card-head view text { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.card-head view text:first-child { color: #283a4e; font-size: 23rpx; font-weight: 820; }.card-head view text:last-child { margin-top: 5rpx; color: #8a6a40; font-size: 19rpx; }.status { flex-shrink: 0; padding: 6rpx 10rpx; border-radius: 999rpx; background: #edf1f4; color: #6e7d8c; font-size: 18rpx; }.status.pending_approval { background: #fff2dd; color: #9b681e; }.status.approved { background: #e8f4ee; color: #2f8065; }.status.rejected { background: #fceceb; color: #b75353; }.purpose { display: -webkit-box; overflow: hidden; margin-top: 15rpx; color: #415366; font-size: 22rpx; line-height: 1.5; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }.meta { display: flex; flex-wrap: wrap; gap: 8rpx 14rpx; margin-top: 14rpx; color: #8b97a4; font-size: 18rpx; }.file-summary { display: flex; align-items: center; justify-content: space-between; margin-top: 15rpx; padding-top: 13rpx; border-top: 1rpx solid #edf0f2; color: #6d7f90; font-size: 19rpx; }.file-summary text:last-child { font-size: 28rpx; }
.state-card,.empty-card { display: flex; min-height: 260rpx; align-items: center; justify-content: center; flex-direction: column; padding: 32rpx; border-radius: 18rpx; background: #fff; color: #8b97a4; font-size: 21rpx; text-align: center; }.state-card.error { color: #b75353; }.state-card button { min-height: 58rpx; margin-top: 16rpx; padding: 0 22rpx; border-radius: 11rpx; background: #315f86; color: #fff; }.empty-card>text:first-child { display: flex; width: 64rpx; height: 64rpx; align-items: center; justify-content: center; border: 3rpx solid #9a6b2d; border-radius: 16rpx; color: #9a6b2d; font-size: 27rpx; font-weight: 850; }.empty-card>text:nth-child(2) { margin-top: 16rpx; color: #4b5c6e; font-size: 24rpx; font-weight: 750; }.empty-card>text:last-child { margin-top: 7rpx; }.load-more { padding: 20rpx; color: #97a2ad; font-size: 19rpx; text-align: center; }
</style>
