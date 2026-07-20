<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import { createDocumentFolder, deleteDocumentFolder, getDocumentFolders, updateDocumentFolder } from '@/api/document';
import { useProjectStore } from '@/stores/project';
import type { DocumentFolder } from '@/types';
import { getQueryNumber, showToast, switchTab } from '@/utils/navigation';

const projectStore = useProjectStore();
const projectId = ref(0);
const folders = ref<DocumentFolder[]>([]);
const loading = ref(true);
const submitting = ref(false);
const editorOpen = ref(false);
const editor = reactive<{ mode: 'create' | 'rename'; folder?: DocumentFolder; name: string }>({ mode: 'create', name: '' });
const currentProject = computed(() => projectStore.state.projects.find((item) => item.id === projectId.value));

onLoad(async (query) => {
  projectId.value = getQueryNumber(query?.projectId, projectStore.state.currentProjectId || 0);
  await projectStore.loadProjects();
  await loadFolders();
});

async function loadFolders() {
  loading.value = true;
  try { folders.value = projectId.value ? await getDocumentFolders(projectId.value) : []; }
  catch (error) { showToast(error instanceof Error ? error.message : '资料目录加载失败'); }
  finally { loading.value = false; }
}

function goBack() { getCurrentPages().length > 1 ? uni.navigateBack() : switchTab('/pages/documents/index'); }
function openCreate() { Object.assign(editor, { mode: 'create', folder: undefined, name: '' }); editorOpen.value = true; }
function openRename(folder: DocumentFolder) { Object.assign(editor, { mode: 'rename', folder, name: folder.folderName }); editorOpen.value = true; }

async function submitEditor() {
  if (!editor.name.trim()) { showToast('请输入目录名称'); return; }
  if (submitting.value) return;
  submitting.value = true;
  try {
    if (editor.mode === 'create') await createDocumentFolder(projectId.value, editor.name.trim());
    else await updateDocumentFolder(editor.folder!.id, editor.name.trim());
    editorOpen.value = false; showToast(editor.mode === 'create' ? '目录已创建' : '目录已重命名'); await loadFolders();
  } catch (error) { showToast(error instanceof Error ? error.message : '目录保存失败'); }
  finally { submitting.value = false; }
}

function remove(folder: DocumentFolder) {
  uni.showModal({
    title: '删除目录', content: `确认删除“${folder.folderName}”？仅空目录可以删除。`, confirmText: '删除', confirmColor: '#B75353',
    success: async (result) => {
      if (!result.confirm || submitting.value) return;
      submitting.value = true;
      try { await deleteDocumentFolder(folder.id); showToast('目录已删除'); await loadFolders(); }
      catch (error) { showToast(error instanceof Error ? error.message : '目录删除失败'); }
      finally { submitting.value = false; }
    }
  });
}
</script>

<template>
  <view class="folder-page">
    <AppNavBar title="资料目录" @back="goBack" />
    <scroll-view class="folder-scroll" scroll-y>
      <view class="folder-content">
        <view class="folder-summary"><view><text>当前施工区域</text><text>{{ currentProject?.projectName || currentProject?.shortName || '未选择' }}</text></view><button @tap="openCreate">＋ 新建一级目录</button></view>
        <view class="folder-hint">资料目录只保留一级；存在资料的目录不能删除。</view>
        <view class="folder-panel">
          <view v-if="loading" class="folder-state">正在加载资料目录</view>
          <view v-else-if="!folders.length" class="folder-state"><text>尚未创建资料目录</text><button @tap="openCreate">创建第一个目录</button></view>
          <view v-for="row in folders" :key="row.id" class="folder-row">
            <view class="folder-branch"><text class="folder-icon"></text></view>
            <view class="folder-copy"><text>{{ row.folderName }}</text><text>{{ row.documentCount || 0 }} 项资料</text></view>
            <view class="folder-actions"><button title="重命名" @tap="openRename(row)">改</button><button class="danger" title="删除" @tap="remove(row)">删</button></view>
          </view>
        </view>
      </view>
    </scroll-view>

    <view v-if="editorOpen" class="editor-overlay" @tap="editorOpen = false">
      <view class="editor-sheet" @tap.stop>
        <view class="editor-handle"></view><view class="editor-head"><text>{{ editor.mode === 'create' ? '新建资料目录' : '重命名目录' }}</text><button @tap="editorOpen = false">×</button></view>
        <view class="parent-tip">{{ editor.mode === 'create' ? '一级目录将直接显示在资料库筛选列表中' : `当前目录：${editor.folder?.folderName}` }}</view>
        <text class="field-label">目录名称</text><input v-model="editor.name" maxlength="100" placeholder="请输入目录名称" focus />
        <view class="editor-actions"><button @tap="editorOpen = false">取消</button><button :disabled="submitting" @tap="submitEditor">保存</button></view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.folder-page { min-height: 100vh; background: #f4f6f7; color: #263449; }
.folder-page :deep(.app-nav) { background: rgba(255,255,255,.97); box-shadow: 0 1rpx 0 rgba(148,163,184,.16); }
.folder-scroll { height: calc(100vh - 92px); }
.folder-content { display: flex; flex-direction: column; gap: 18rpx; padding: 24rpx 24rpx calc(40rpx + env(safe-area-inset-bottom)); }
.folder-summary { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; padding: 20rpx; border-radius: 16rpx; background: #eaf1f5; }
.folder-summary view { min-width: 0; }
.folder-summary view text { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.folder-summary view text:first-child { color: #7890a1; font-size: 19rpx; }
.folder-summary view text:last-child { margin-top: 6rpx; color: #34576e; font-size: 25rpx; font-weight: 760; }
.folder-summary button { min-height: 58rpx; padding: 0 16rpx; border-radius: 11rpx; background: #fff; color: #456b83; font-size: 20rpx; font-weight: 700; }
.folder-summary button::after,.folder-actions button::after,.folder-state button::after,.editor-head button::after,.editor-actions button::after { border: 0; }
.folder-hint { padding: 0 4rpx; color: #87939f; font-size: 20rpx; line-height: 1.5; }
.folder-panel { overflow: hidden; border-radius: 16rpx; background: #fff; box-shadow: 0 9rpx 28rpx rgba(43,56,72,.055); }
.folder-row { display: flex; min-height: 94rpx; align-items: center; gap: 13rpx; padding: 14rpx 16rpx 14rpx 18rpx; border-bottom: 1rpx solid #edf0f3; }
.folder-row:last-child { border-bottom: 0; }
.folder-branch { display: flex; width: 48rpx; height: 48rpx; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 12rpx; background: #eaf1f5; }
.folder-icon { position: relative; width: 27rpx; height: 20rpx; border: 3rpx solid #66859a; border-radius: 4rpx; }
.folder-icon::before { position: absolute; top: -8rpx; left: -3rpx; width: 15rpx; height: 7rpx; border-radius: 4rpx 4rpx 0 0; background: #66859a; content: ''; }
.folder-copy { min-width: 0; flex: 1; }
.folder-copy text { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.folder-copy text:first-child { color: #304456; font-size: 23rpx; font-weight: 730; }
.folder-copy text:last-child { margin-top: 6rpx; color: #8b97a3; font-size: 18rpx; }
.folder-actions { display: flex; gap: 7rpx; }
.folder-actions button { width: 46rpx; height: 42rpx; border-radius: 9rpx; background: #edf3f6; color: #55768c; font-size: 18rpx; }
.folder-actions button.danger { background: #fceeed; color: #b75353; }
.folder-state { display: flex; min-height: 260rpx; align-items: center; justify-content: center; flex-direction: column; color: #87939f; font-size: 22rpx; }
.folder-state button { min-height: 58rpx; margin-top: 16rpx; padding: 0 18rpx; border-radius: 10rpx; background: #eaf1f5; color: #456b83; font-size: 20rpx; }
.editor-overlay { position: fixed; z-index: 90; inset: 0; display: flex; align-items: flex-end; background: rgba(29,41,57,.34); }
.editor-sheet { width: 100%; padding: 14rpx 26rpx calc(30rpx + env(safe-area-inset-bottom)); border-radius: 24rpx 24rpx 0 0; background: #fff; box-shadow: 0 -20rpx 50rpx rgba(29,41,57,.14); }
.editor-handle { width: 64rpx; height: 7rpx; margin: 0 auto 18rpx; border-radius: 99rpx; background: #d6dce4; }
.editor-head { display: flex; align-items: center; justify-content: space-between; }
.editor-head > text { color: #243649; font-size: 30rpx; font-weight: 800; }
.editor-head button { width: 52rpx; height: 52rpx; border-radius: 50%; background: #f2f4f7; color: #6d7a88; font-size: 32rpx; }
.parent-tip { margin: 18rpx 0; padding: 13rpx 16rpx; border-radius: 10rpx; background: #edf3f6; color: #64798a; font-size: 20rpx; }
.field-label { display: block; margin-bottom: 9rpx; color: #58697a; font-size: 21rpx; font-weight: 700; }
.editor-sheet input { min-height: 76rpx; padding: 0 17rpx; border: 1rpx solid #e1e6eb; border-radius: 12rpx; background: #f7f8fa; font-size: 23rpx; }
.editor-actions { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 13rpx; margin-top: 24rpx; }
.editor-actions button { min-height: 72rpx; border-radius: 12rpx; background: #f1f3f5; color: #5f6e7d; font-size: 23rpx; font-weight: 730; }
.editor-actions button:last-child { background: #567b96; color: #fff; }
</style>
