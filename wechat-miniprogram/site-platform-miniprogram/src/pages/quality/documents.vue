<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import { downloadFileToTempPath, getFileResources, type FileResourceItem } from '@/api/file';
import { useAuthStore } from '@/stores/auth';
import { useProjectStore } from '@/stores/project';
import { extensionOf } from '@/utils/documentFile';
import { getQueryNumber, showToast, switchTab } from '@/utils/navigation';

const authStore = useAuthStore();
const projectStore = useProjectStore();
const projectId = ref(0);
const documents = ref<FileResourceItem[]>([]);
const loading = ref(true);
const errorMessage = ref('');
const openingId = ref<number>();
const currentProject = computed(() => projectStore.state.projects.find((item) => item.id === projectId.value));

onLoad(async (query) => {
  if (!await authStore.ensureRootAccess('/pages/quality/index')) return;
  await projectStore.loadProjects();
  const resolved = getQueryNumber(query?.projectId, projectStore.state.currentProjectId || 0);
  if (!resolved || !await authStore.ensureProjectPermission('/pages/quality/index', resolved, 'quality.view')) return;
  projectId.value = resolved;
  await refresh();
});

async function refresh() {
  if (!projectId.value) return;
  loading.value = true;
  errorMessage.value = '';
  try {
    documents.value = await getFileResources(projectId.value, 'QUALITY_DOCUMENT', 'UPLOADED');
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '质量资料加载失败';
  } finally {
    loading.value = false;
  }
}

async function openDocument(file: FileResourceItem) {
  if (openingId.value !== undefined) return;
  openingId.value = file.id;
  try {
    const path = await downloadFileToTempPath(file.id);
    const extension = extensionOf(file.fileName) || String(file.fileType || '').replace(/^\./, '').toLowerCase();
    if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'].includes(extension)) {
      uni.previewImage({ urls: [path], current: path });
      return;
    }
    if (!['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx'].includes(extension)) throw new Error('该格式暂不支持在小程序内打开');
    await new Promise<void>((resolve, reject) => uni.openDocument({
      filePath: path,
      fileType: extension,
      showMenu: true,
      success: () => resolve(),
      fail: (error) => reject(new Error(error.errMsg || '文件打开失败'))
    }));
  } catch (error) {
    showToast(error instanceof Error ? error.message : '文件打开失败');
  } finally {
    openingId.value = undefined;
  }
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '时间未设置';
}

function goBack() {
  if (getCurrentPages().length > 1) uni.navigateBack();
  else switchTab('/pages/quality/index');
}
</script>

<template>
  <view class="document-page">
    <AppNavBar title="质量资料" @back="goBack" />
    <scroll-view class="document-scroll" scroll-y>
      <view class="document-content">
        <view class="context-card"><text>{{ currentProject?.projectName || currentProject?.shortName || '当前施工区域' }}</text><text>由项目质量管理员在 Web 端维护，小程序仅支持查看</text></view>
        <view v-if="loading" class="state-card">正在加载质量资料...</view>
        <view v-else-if="errorMessage" class="state-card error"><text>{{ errorMessage }}</text><button @tap="refresh">重新加载</button></view>
        <view v-else class="document-list">
          <button v-for="file in documents" :key="file.id" :disabled="openingId !== undefined" @tap="openDocument(file)">
            <view class="document-icon">文</view>
            <view><text>{{ file.fileName }}</text><text>{{ file.fileType || '质量资料' }} · {{ formatTime(file.createTime) }}</text></view>
            <text class="row-arrow"></text>
          </button>
          <view v-if="!documents.length" class="empty-state"><view>文</view><text>当前施工区域暂无有效质量资料</text><text>资料由质量管理员在 Web 端维护</text></view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<style scoped>
.document-page { min-height: 100vh; background: #f4f6f7; color: var(--workspace-text); }
.document-page :deep(.app-nav) { background: rgba(255,255,255,.98); box-shadow: 0 1rpx 0 rgba(148,163,184,.18); }
.document-scroll { height: calc(100vh - 92px); }
.document-content { display: flex; flex-direction: column; gap: 16rpx; padding: 20rpx 24rpx calc(36rpx + env(safe-area-inset-bottom)); }
.context-card { padding: 17rpx 20rpx; border-radius: 14rpx; background: #eaf2f6; }
.context-card text { display: block; color: #617486; font-size: 19rpx; }
.context-card text:first-child { overflow: hidden; color: #293d50; font-size: 23rpx; font-weight: 760; text-overflow: ellipsis; white-space: nowrap; }.context-card text + text { margin-top: 5rpx; }
.state-card { padding: 70rpx 24rpx; border-radius: 16rpx; background: #fff; color: #718092; font-size: 21rpx; text-align: center; }.state-card text { display: block; }.state-card button { margin-top: 18rpx; border: 0; background: #e8f1f6; color: #315f86; font-size: 20rpx; }.state-card button::after { border: 0; }
.document-list { overflow: hidden; border-radius: 16rpx; background: #fff; box-shadow: 0 8rpx 26rpx rgba(43,56,72,.055); }
.document-list > button { box-sizing: border-box; display: flex; width: 100%; min-height: 96rpx; align-items: center; gap: 14rpx; margin: 0; padding: 15rpx 18rpx; border: 0; border-bottom: 1rpx solid #edf0f3; background: #fff; text-align: left; }.document-list > button::after { border: 0; }
.document-icon,.empty-state > view { display: flex; width: 48rpx; height: 48rpx; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 12rpx; background: #eaf2f6; color: #315f86; font-size: 21rpx; font-weight: 800; }
.document-list > button > view:nth-child(2) { min-width: 0; flex: 1; }.document-list > button > view:nth-child(2) text { display: block; color: #7d8b99; font-size: 18rpx; }.document-list > button > view:nth-child(2) text:first-child { overflow: hidden; color: #2e4256; font-size: 22rpx; font-weight: 740; text-overflow: ellipsis; white-space: nowrap; }.document-list > button > view:nth-child(2) text + text { margin-top: 6rpx; }
.row-arrow { width: 12rpx; height: 12rpx; flex-shrink: 0; border-top: 2rpx solid #9aa6b6; border-right: 2rpx solid #9aa6b6; transform: rotate(45deg); }
.empty-state { display: flex; min-height: 260rpx; align-items: center; justify-content: center; flex-direction: column; padding: 32rpx; color: #82909e; text-align: center; }.empty-state > view { width: 62rpx; height: 62rpx; margin-bottom: 16rpx; }.empty-state text { display: block; font-size: 20rpx; }.empty-state text:first-of-type { color: #526579; font-size: 22rpx; font-weight: 740; }.empty-state text + text { margin-top: 7rpx; }
@media (max-width: 360px) { .document-content { padding-right: 18rpx; padding-left: 18rpx; } }
</style>
