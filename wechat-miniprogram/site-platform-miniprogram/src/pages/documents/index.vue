<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import AppTabBar from '@/components/AppTabBar.vue';
import WorkspaceAreaSwitcher from '@/components/workspace/WorkspaceAreaSwitcher.vue';
import WorkspaceMetricStrip, { type WorkspaceMetric } from '@/components/workspace/WorkspaceMetricStrip.vue';
import WorkspaceStatusPill from '@/components/workspace/WorkspaceStatusPill.vue';
import { WORKSPACE_THEME } from '@/constants/workspaceTheme';
import { getDocumentFolders, getProjectDocuments, getProjectDocumentSummary } from '@/api/document';
import { useProjectStore } from '@/stores/project';
import type { DocumentFolder, DocumentStatus, ProjectDocument, ProjectDocumentSummary } from '@/types';
import { extensionOf, formatFileSize } from '@/utils/documentFile';
import { usePageScrollHeight } from '@/utils/navLayout';
import { navigateTo } from '@/utils/navigation';

type SheetMode = 'folder' | 'filter' | null;

const ACCENT = WORKSPACE_THEME.accent;
const TINT = WORKSPACE_THEME.tint;
const PAGE_SIZE = 20;
const projectStore = useProjectStore();
const summary = ref<ProjectDocumentSummary | null>(null);
const folders = ref<DocumentFolder[]>([]);
const documents = ref<ProjectDocument[]>([]);
const selectedFolderId = ref<number | undefined>();
const keyword = ref('');
const appliedKeyword = ref('');
const status = ref<DocumentStatus | ''>('');
const pageNo = ref(1);
const total = ref(0);
const loading = ref(false);
const loadingMore = ref(false);
const refreshing = ref(false);
const errorMessage = ref('');
const sheetMode = ref<SheetMode>(null);
const { scrollStyle } = usePageScrollHeight({ bottomRpx: 124, minHeight: 320, includeSafeBottom: false });

const projects = computed(() => projectStore.state.projects);
const currentProject = computed(() => projects.value.find((item) => item.id === projectStore.state.currentProjectId));
const selectedFolder = computed(() => folders.value.find((item) => item.id === selectedFolderId.value));
const hasMore = computed(() => documents.value.length < total.value);
const metrics = computed<WorkspaceMetric[]>(() => [
  { label: '全部资料', value: summary.value?.total || 0 },
  { label: '使用中', value: summary.value?.active || 0, tone: 'green' },
  { label: '已归档', value: summary.value?.archived || 0, tone: 'gray' },
  { label: '近7日更新', value: summary.value?.recentUpdates || 0 }
]);

const statusOptions: Array<{ value: DocumentStatus | ''; label: string }> = [
  { value: '', label: '全部归档状态' },
  { value: 'ACTIVE', label: '使用中' },
  { value: 'ARCHIVED', label: '已归档' }
];

function hideNativeTabBar() { uni.hideTabBar({ animation: false, fail: () => undefined }); }

onShow(async () => {
  hideNativeTabBar();
  await refreshAll();
});

async function refreshAll() {
  loading.value = true;
  errorMessage.value = '';
  try {
    await projectStore.loadProjects();
    if (!currentProject.value) {
      summary.value = null; folders.value = []; documents.value = []; total.value = 0;
      return;
    }
    pageNo.value = 1;
    const projectId = currentProject.value.id;
    const [summaryResult, folderResult, documentResult] = await Promise.all([
      getProjectDocumentSummary(projectId),
      getDocumentFolders(projectId),
      getProjectDocuments({ projectId, folderId: selectedFolderId.value, keyword: appliedKeyword.value, status: status.value, pageNo: 1, pageSize: PAGE_SIZE })
    ]);
    summary.value = summaryResult;
    folders.value = folderResult;
    documents.value = documentResult.records || [];
    total.value = Number(documentResult.total || 0);
  } catch (error) {
    summary.value = null; documents.value = []; total.value = 0;
    errorMessage.value = error instanceof Error ? error.message : '资料加载失败';
  } finally {
    loading.value = false;
    refreshing.value = false;
  }
}

async function loadMore() {
  if (!currentProject.value || loading.value || loadingMore.value || !hasMore.value) return;
  loadingMore.value = true;
  try {
    const nextPage = pageNo.value + 1;
    const result = await getProjectDocuments({
      projectId: currentProject.value.id, folderId: selectedFolderId.value, keyword: appliedKeyword.value,
      status: status.value, pageNo: nextPage, pageSize: PAGE_SIZE
    });
    documents.value = [...documents.value, ...(result.records || [])];
    total.value = Number(result.total || 0);
    pageNo.value = nextPage;
  } finally { loadingMore.value = false; }
}

async function selectProject(projectId: number) {
  projectStore.setCurrentProject(projectId);
  selectedFolderId.value = undefined; keyword.value = ''; appliedKeyword.value = ''; status.value = '';
  await refreshAll();
}

async function selectFolder(folderId?: number) {
  selectedFolderId.value = folderId;
  sheetMode.value = null;
  await refreshAll();
}

async function applySearch() {
  appliedKeyword.value = keyword.value.trim();
  await refreshAll();
}

async function clearFilters() {
  status.value = ''; sheetMode.value = null;
  await refreshAll();
}

async function applyFilters() {
  sheetMode.value = null;
  await refreshAll();
}

async function refreshByPull() {
  refreshing.value = true;
  await refreshAll();
}

function formatTime(value?: string) { return value ? value.replace('T', ' ').slice(0, 16) : '时间未设置'; }
function fileMark(document: ProjectDocument) { return (extensionOf(document.currentVersion?.fileName) || document.currentVersion?.fileExtension || 'FILE').slice(0, 5).toUpperCase(); }
function openUpload() { navigateTo(`/pages/documents/upload?projectId=${currentProject.value?.id || 0}&folderId=${selectedFolderId.value || 0}`); }
function openDetail(document: ProjectDocument) { navigateTo(`/pages/documents/detail?id=${document.id}`); }
</script>

<template>
  <view class="workspace-shell document-page" :style="{ '--page-accent': ACCENT, '--page-accent-deep': WORKSPACE_THEME.accentDeep, '--page-tint': TINT, '--page-background': WORKSPACE_THEME.page }">
    <AppNavBar title="资料管理" :show-back="false" />
    <scroll-view class="workspace-scroll" scroll-y enable-flex :style="scrollStyle" lower-threshold="120"
      refresher-enabled :refresher-triggered="refreshing" @refresherrefresh="refreshByPull" @scrolltolower="loadMore">
      <view class="workspace-content">
        <WorkspaceAreaSwitcher :project="currentProject" :projects="projects" :accent="ACCENT" :tint="TINT" @select="selectProject" />

        <view v-if="loading && !summary" class="state-panel"><text class="state-title">正在加载工程资料</text></view>
        <view v-else-if="errorMessage" class="state-panel"><text class="state-title">资料加载失败</text><text class="state-desc">{{ errorMessage }}</text><button class="retry-button" @tap="refreshAll">重新加载</button></view>
        <template v-else-if="summary && currentProject">
          <WorkspaceMetricStrip :metrics="metrics" :accent="ACCENT" :motion-key="`${currentProject.id}-${summary.total}`" />

          <view class="document-toolbar">
            <button class="upload-entry" @tap="openUpload"><text class="upload-plus">＋</text><view><text>上传资料</text><text>微信文件、拍照或相册</text></view></button>
            <button v-if="summary.canManage" class="tool-entry" @tap="navigateTo(`/pages/documents/folders?projectId=${currentProject.id}`)"><text class="tool-icon folder-icon"></text><text>目录</text></button>
            <button v-if="summary.canManage" class="tool-entry" @tap="navigateTo(`/pages/documents/recycle?projectId=${currentProject.id}`)"><text class="tool-icon recycle-icon"></text><text>回收站</text></button>
          </view>

          <view class="section-block document-section">
            <view class="section-head">
              <view><text class="section-title">工程资料库</text><text class="section-subtitle">{{ selectedFolder?.folderName || '全部目录' }} · 共 {{ total }} 项</text></view>
              <WorkspaceStatusPill :label="`近7日 ${summary.recentUpdates || 0} 项更新`" tone="blue" />
            </view>

            <view class="search-box"><text class="search-icon"></text><input v-model="keyword" class="search-input" placeholder="搜索名称、编号或备注" placeholder-class="search-placeholder" confirm-type="search" @confirm="applySearch" /><button v-if="keyword" class="search-clear" @tap="keyword = ''; applySearch()">×</button></view>
            <view class="filter-row">
              <button :class="{ active: selectedFolderId !== undefined }" @tap="sheetMode = 'folder'">{{ selectedFolder?.folderName || '全部目录' }}<text class="filter-chevron"></text></button>
              <button :class="{ active: status }" @tap="sheetMode = 'filter'">{{ status ? '已筛选' : '归档状态' }}<text class="filter-chevron"></text></button>
            </view>

            <view class="plain-list document-list">
              <button v-for="document in documents" :key="document.id" class="plain-row document-row" @tap="openDetail(document)">
                <view class="file-mark" :class="document.status === 'ARCHIVED' ? 'archived' : ''">{{ fileMark(document) }}</view>
                <view class="plain-copy">
                  <view class="title-line"><text class="plain-title">{{ document.title }}</text><WorkspaceStatusPill :label="document.status === 'ARCHIVED' ? '已归档' : '使用中'" :tone="document.status === 'ARCHIVED' ? 'gray' : 'green'" /></view>
                  <text class="plain-meta">{{ document.documentNo || '无资料编号' }} · {{ document.folderName || '未分类' }}</text>
                  <text class="file-meta">{{ document.currentVersion?.versionLabel || '-' }} · {{ formatFileSize(document.currentVersion?.fileSize) }} · {{ document.createdByName || '未知上传人' }}</text>
                  <text class="file-time">更新于 {{ formatTime(document.updateTime) }}</text>
                </view>
                <text class="row-arrow"></text>
              </button>
              <view v-if="!documents.length && !loading" class="empty-state"><text class="empty-file">文</text><text class="empty-title">当前目录暂无资料</text><text class="empty-desc">可以调整筛选条件或上传第一份资料</text></view>
              <view v-if="loadingMore" class="load-more">正在加载更多资料</view>
              <view v-else-if="documents.length && !hasMore" class="load-more">已加载全部 {{ total }} 项资料</view>
            </view>
          </view>
        </template>
      </view>
    </scroll-view>
    <AppTabBar active="documents" />

    <view v-if="sheetMode" class="form-overlay" @tap="sheetMode = null">
      <view class="form-sheet compact-sheet" @tap.stop>
        <view class="sheet-handle"></view>
        <view class="form-head"><text class="form-title">{{ sheetMode === 'folder' ? '选择资料目录' : '筛选资料' }}</text><button class="form-close" @tap="sheetMode = null">×</button></view>
        <template v-if="sheetMode === 'folder'">
          <button class="sheet-option" :class="{ active: selectedFolderId === undefined }" @tap="selectFolder(undefined)"><text>全部目录</text><text>{{ summary?.total || 0 }} 项</text></button>
          <button class="sheet-option" :class="{ active: selectedFolderId === 0 }" @tap="selectFolder(0)"><text>未分类</text><text>根目录</text></button>
          <button v-for="folder in folders" :key="folder.id" class="sheet-option" :class="{ active: selectedFolderId === folder.id }" @tap="selectFolder(folder.id)"><text>{{ folder.folderName }}</text><text>{{ folder.documentCount }} 项</text></button>
        </template>
        <template v-else>
          <text class="filter-label">归档状态</text>
          <view class="choice-grid status-grid"><button v-for="item in statusOptions" :key="item.value" :class="{ active: status === item.value }" @tap="status = item.value">{{ item.label }}</button></view>
          <view class="form-actions"><button class="secondary-action" @tap="clearFilters">重置</button><button class="primary-action" @tap="applyFilters">查看结果</button></view>
        </template>
      </view>
    </view>
  </view>
</template>

<style scoped src="../../styles/workspace-page.css"></style>
<style scoped>
.document-toolbar { display: flex; gap: 12rpx; }
.document-toolbar button::after,.filter-row button::after,.search-clear::after,.sheet-option::after,.choice-grid button::after { border: 0; }
.upload-entry { display: flex; min-height: 92rpx; align-items: center; justify-content: flex-start; flex: 1; gap: 14rpx; padding: 14rpx 18rpx; border-radius: 16rpx; background: var(--workspace-tint-strong); color: var(--page-accent-deep); text-align: left; box-shadow: var(--workspace-shadow); }
.upload-entry:active,.tool-entry:active { transform: scale(.985); }
.upload-entry view { min-width: 0; }
.upload-entry text { display: block; }
.upload-entry view text:first-child { font-size: 25rpx; font-weight: 800; }
.upload-entry view text:last-child { margin-top: 5rpx; color: #6c8493; font-size: 19rpx; }
.upload-plus { display: flex !important; width: 48rpx; height: 48rpx; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 13rpx; background: #fff; color: #4f748e; font-size: 34rpx; font-weight: 500; }
.tool-entry { display: flex; width: 92rpx; min-height: 92rpx; align-items: center; justify-content: center; flex-shrink: 0; flex-direction: column; gap: 7rpx; border-radius: 16rpx; background: #fff; box-shadow: 0 8rpx 24rpx rgba(43,56,72,.055); color: #536879; font-size: 19rpx; white-space: nowrap; }
.tool-entry > text:last-child { display: block; width: 100%; white-space: nowrap; text-align: center; }
.tool-icon { position: relative; display: block; width: 29rpx; height: 25rpx; border: 3rpx solid #68869b; border-radius: 5rpx; }
.folder-icon::before { position: absolute; top: -8rpx; left: 0; width: 14rpx; height: 7rpx; border-radius: 4rpx 4rpx 0 0; background: #68869b; content: ''; }
.recycle-icon { border-width: 0 3rpx 3rpx; border-radius: 0 0 5rpx 5rpx; }
.recycle-icon::before { position: absolute; top: -6rpx; left: -4rpx; width: 37rpx; height: 3rpx; border-radius: 99rpx; background: #68869b; content: ''; }
.recycle-icon::after { position: absolute; top: -11rpx; left: 9rpx; width: 12rpx; height: 5rpx; border: 3rpx solid #68869b; border-bottom: 0; border-radius: 4rpx 4rpx 0 0; content: ''; }
.document-section { overflow: visible; }
.document-section .section-head { border-radius: 16rpx 16rpx 0 0; }
.search-box { margin-top: 15rpx; }
.search-clear { width: 42rpx; height: 42rpx; flex-shrink: 0; border-radius: 50%; background: #e3e7eb; color: #7c8795; font-size: 28rpx; }
.filter-row { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 12rpx; padding: 10rpx 20rpx 9rpx; }
.filter-row button { min-height: 58rpx; gap: 9rpx; border-radius: 10rpx; background: #f3f5f7; color: #667586; font-size: 21rpx; }
.filter-row button.active { background: #e8f0f4; color: #42677f; font-weight: 700; }
.filter-chevron { width: 9rpx; height: 9rpx; margin-top: -5rpx; border-right: 2rpx solid currentColor; border-bottom: 2rpx solid currentColor; transform: rotate(45deg); }
.document-list { padding-top: 2rpx; }
.document-row { min-height: 144rpx; align-items: flex-start; }
.file-mark { display: flex; width: 66rpx; height: 70rpx; align-items: center; justify-content: center; flex-shrink: 0; margin-top: 2rpx; border-radius: 11rpx 11rpx 16rpx 11rpx; background: #e6eff4; color: #42677f; font-size: 18rpx; font-weight: 850; }
.file-mark.archived { background: #edf0f2; color: #7a8490; }
.title-line { display: flex; min-width: 0; align-items: flex-start; justify-content: space-between; gap: 10rpx; }
.title-line .plain-title { flex: 1; }
.file-meta,.file-time { display: block; margin-top: 7rpx; color: #748293; font-size: 19rpx; line-height: 1.3; }
.file-time { color: #9aa3ae; }
.empty-state { display: flex; min-height: 270rpx; align-items: center; justify-content: center; flex-direction: column; padding: 36rpx; }
.empty-file { display: flex; width: 64rpx; height: 70rpx; align-items: center; justify-content: center; border-radius: 12rpx; background: #edf2f5; color: #718797; font-size: 22rpx; font-weight: 800; }
.empty-title { margin-top: 18rpx; color: #455568; font-size: 25rpx; font-weight: 750; }
.empty-desc { margin-top: 8rpx; color: #98a2ad; font-size: 20rpx; }
.load-more { padding: 24rpx 0 18rpx; color: #98a2ad; font-size: 20rpx; text-align: center; }
.compact-sheet { max-height: 78vh; }
.sheet-option { display: flex; width: 100%; min-height: 76rpx; align-items: center; justify-content: space-between; padding: 0 16rpx; border-bottom: 1rpx solid #edf0f3; color: #344456; font-size: 23rpx; }
.sheet-option text:last-child { color: #98a2ad; font-size: 20rpx; }
.sheet-option.active { border-radius: 12rpx; background: #eaf1f5; color: #3e6078; font-weight: 750; }
.filter-label { display: block; margin: 20rpx 0 12rpx; color: #596879; font-size: 22rpx; font-weight: 700; }
.choice-grid { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 10rpx; }
.choice-grid button { min-height: 64rpx; padding: 0 8rpx; border-radius: 11rpx; background: #f3f5f7; color: #667586; font-size: 21rpx; }
.choice-grid button.active { background: #dfeaf0; color: #365b73; font-weight: 750; }
.status-grid { grid-template-columns: repeat(3,minmax(0,1fr)); }
</style>
