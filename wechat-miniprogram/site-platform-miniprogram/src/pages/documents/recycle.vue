<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import WorkspaceStatusPill from '@/components/workspace/WorkspaceStatusPill.vue';
import { getProjectDocumentRecycleBin, purgeProjectDocument, restoreProjectDocument } from '@/api/document';
import { useAuthStore } from '@/stores/auth';
import { useProjectStore } from '@/stores/project';
import type { ProjectDocument } from '@/types';
import { extensionOf, formatFileSize } from '@/utils/documentFile';
import { getQueryNumber, showToast, switchTab } from '@/utils/navigation';

const PAGE_SIZE = 20;
const authStore = useAuthStore();
const projectStore = useProjectStore();
const projectId = ref(0);
const documents = ref<ProjectDocument[]>([]);
const keyword = ref('');
const appliedKeyword = ref('');
const pageNo = ref(1);
const total = ref(0);
const loading = ref(true);
const loadingMore = ref(false);
const refreshing = ref(false);
const errorMessage = ref('');
const submittingId = ref(0);
const currentProject = computed(() => projectStore.state.projects.find((item) => item.id === projectId.value));
const hasMore = computed(() => documents.value.length < total.value);

onLoad(async (query) => {
  await projectStore.loadProjects();
  projectId.value = getQueryNumber(query?.projectId, projectStore.state.currentProjectId || 0);
  if (!await authStore.ensureProjectPermission(
    '/pages/documents/index', projectId.value, 'document.manage')) return;
  await load(true);
});

async function load(reset = false) {
  if (!projectId.value) return;
  if (reset) { pageNo.value = 1; loading.value = true; errorMessage.value = ''; }
  else loadingMore.value = true;
  try {
    const targetPage = reset ? 1 : pageNo.value + 1;
    const result = await getProjectDocumentRecycleBin(projectId.value, appliedKeyword.value, targetPage, PAGE_SIZE);
    documents.value = reset ? result.records || [] : [...documents.value, ...(result.records || [])];
    total.value = Number(result.total || 0);
    pageNo.value = targetPage;
  } catch (error) {
    if (reset) documents.value = [];
    errorMessage.value = error instanceof Error ? error.message : '回收站加载失败';
  } finally { loading.value = false; loadingMore.value = false; refreshing.value = false; }
}

function goBack() { getCurrentPages().length > 1 ? uni.navigateBack() : switchTab('/pages/documents/index'); }
async function search() { appliedKeyword.value = keyword.value.trim(); await load(true); }
async function refresh() { refreshing.value = true; await load(true); }
function formatTime(value?: string) { return value ? value.replace('T', ' ').slice(0, 16) : '时间未设置'; }
function fileMark(document: ProjectDocument) { return (extensionOf(document.currentVersion?.fileName) || document.currentVersion?.fileExtension || 'FILE').slice(0, 5).toUpperCase(); }

function restore(document: ProjectDocument) {
  uni.showModal({
    title: '恢复资料', content: `将《${document.title}》恢复到原目录？`, confirmText: '恢复', confirmColor: '#567B96',
    success: async (result) => {
      if (!result.confirm || submittingId.value) return;
      submittingId.value = document.id;
      try { await restoreProjectDocument(document.id); showToast('资料已恢复'); await load(true); }
      catch (error) { showToast(error instanceof Error ? error.message : '恢复失败'); }
      finally { submittingId.value = 0; }
    }
  });
}

function purge(document: ProjectDocument) {
  uni.showModal({
    title: '永久删除', content: `永久删除《${document.title}》及全部历史版本？此操作无法撤销。`, confirmText: '永久删除', confirmColor: '#B75353',
    success: async (result) => {
      if (!result.confirm || submittingId.value) return;
      submittingId.value = document.id;
      try { await purgeProjectDocument(document.id); showToast('资料已永久删除'); await load(true); }
      catch (error) { showToast(error instanceof Error ? error.message : '永久删除失败'); }
      finally { submittingId.value = 0; }
    }
  });
}
</script>

<template>
  <view class="recycle-page">
    <AppNavBar title="资料回收站" @back="goBack" />
    <scroll-view class="recycle-scroll" scroll-y lower-threshold="120" refresher-enabled :refresher-triggered="refreshing" @refresherrefresh="refresh" @scrolltolower="hasMore && load(false)">
      <view class="recycle-content">
        <view class="recycle-head"><view><text>当前施工区域</text><text>{{ currentProject?.projectName || currentProject?.shortName || '未选择' }}</text></view><WorkspaceStatusPill :label="`${total} 项待处理`" :tone="total ? 'amber' : 'gray'" /></view>
        <view class="recycle-tip">回收站仅项目管理员可见。恢复后回到原目录，永久删除会同步清理全部版本文件。</view>
        <view class="search-box"><text class="search-icon"></text><input v-model="keyword" class="search-input" placeholder="搜索已删除资料" confirm-type="search" @confirm="search" /><button v-if="keyword" @tap="keyword = ''; search()">×</button></view>

        <view class="recycle-panel">
          <view v-if="loading" class="state">正在加载回收站</view>
          <view v-else-if="errorMessage" class="state"><text>{{ errorMessage }}</text><button @tap="load(true)">重新加载</button></view>
          <view v-else-if="!documents.length" class="state"><text class="empty-mark">✓</text><text class="empty-title">回收站为空</text><text class="empty-desc">当前施工区域没有待处理资料</text></view>
          <view v-for="document in documents" v-else :key="document.id" class="recycle-row">
            <view class="file-mark">{{ fileMark(document) }}</view>
            <view class="file-copy"><text>{{ document.title }}</text><text>{{ document.documentNo || '无资料编号' }} · {{ document.folderName || '根目录' }}</text><text>{{ document.currentVersion?.versionLabel || '-' }} · {{ formatFileSize(document.currentVersion?.fileSize) }} · 删除于 {{ formatTime(document.updateTime) }}</text></view>
            <view class="row-actions"><button :disabled="submittingId === document.id" @tap="restore(document)">恢复</button><button class="danger" :disabled="submittingId === document.id" @tap="purge(document)">永久删除</button></view>
          </view>
          <view v-if="loadingMore" class="load-more">正在加载更多</view>
          <view v-else-if="documents.length && !hasMore" class="load-more">已加载全部 {{ total }} 项</view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<style scoped src="../../styles/workspace-page.css"></style>
<style scoped>
.recycle-page { min-height: 100vh; background: #f4f6f7; color: #263449; }
.recycle-page :deep(.app-nav) { background: rgba(255,255,255,.97); box-shadow: 0 1rpx 0 rgba(148,163,184,.16); }
.recycle-scroll { height: calc(100vh - 92px); }
.recycle-content { display: flex; flex-direction: column; gap: 17rpx; padding: 24rpx 24rpx calc(40rpx + env(safe-area-inset-bottom)); }
.recycle-head { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; padding: 20rpx; border-radius: 16rpx; background: #eaf1f5; }
.recycle-head view { min-width: 0; }
.recycle-head view text { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.recycle-head view text:first-child { color: #7b8f9f; font-size: 19rpx; }
.recycle-head view text:last-child { margin-top: 6rpx; color: #365b73; font-size: 25rpx; font-weight: 750; }
.recycle-tip { padding: 0 4rpx; color: #87939f; font-size: 20rpx; line-height: 1.5; }
.search-box { margin: 0; background: #fff; box-shadow: 0 7rpx 22rpx rgba(43,56,72,.045); }
.search-box button { width: 42rpx; height: 42rpx; border-radius: 50%; background: #e8ebee; color: #798592; font-size: 28rpx; }
.search-box button::after,.state button::after,.row-actions button::after { border: 0; }
.recycle-panel { overflow: hidden; border-radius: 16rpx; background: #fff; box-shadow: 0 9rpx 28rpx rgba(43,56,72,.055); }
.recycle-row { display: flex; align-items: flex-start; gap: 14rpx; padding: 20rpx; border-bottom: 1rpx solid #edf0f3; }
.recycle-row:last-child { border-bottom: 0; }
.file-mark { display: flex; width: 62rpx; height: 66rpx; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 11rpx; background: #edf0f2; color: #788592; font-size: 17rpx; font-weight: 850; }
.file-copy { min-width: 0; flex: 1; }
.file-copy text { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-copy text:first-child { color: #33475a; font-size: 23rpx; font-weight: 730; }
.file-copy text:nth-child(2),.file-copy text:last-child { margin-top: 6rpx; color: #84909d; font-size: 18rpx; }
.row-actions { display: flex; flex-direction: column; gap: 8rpx; }
.row-actions button { min-width: 86rpx; min-height: 40rpx; padding: 0 9rpx; border-radius: 8rpx; background: #eaf1f5; color: #4e7086; font-size: 18rpx; }
.row-actions button.danger { background: #fceeed; color: #b75353; }
.row-actions button[disabled] { opacity: .55; }
.state { display: flex; min-height: 280rpx; align-items: center; justify-content: center; flex-direction: column; padding: 30rpx; color: #85919e; font-size: 22rpx; }
.state button { min-height: 58rpx; margin-top: 16rpx; padding: 0 18rpx; border-radius: 10rpx; background: #eaf1f5; color: #456b83; font-size: 20rpx; }
.empty-mark { display: flex; width: 58rpx; height: 58rpx; align-items: center; justify-content: center; border-radius: 50%; background: #e9f5ef; color: #2e8069; font-size: 28rpx; font-weight: 800; }
.empty-title { margin-top: 16rpx; color: #445669; font-size: 25rpx; font-weight: 750; }
.empty-desc { margin-top: 7rpx; color: #98a2ad; font-size: 20rpx; }
.load-more { padding: 23rpx; color: #98a2ad; font-size: 20rpx; text-align: center; }
</style>
