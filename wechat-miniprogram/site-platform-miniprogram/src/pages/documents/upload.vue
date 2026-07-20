<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import { createProjectDocument, getDocumentFolders } from '@/api/document';
import { useProjectStore } from '@/stores/project';
import type { DocumentFolder } from '@/types';
import { chooseDocumentImage, chooseMessageDocument, formatFileSize, type LocalDocumentFile } from '@/utils/documentFile';
import { getQueryNumber, showToast } from '@/utils/navigation';

const projectStore = useProjectStore();
const projectId = ref(0);
const folders = ref<DocumentFolder[]>([]);
const selectedFile = ref<LocalDocumentFile | null>(null);
const submitting = ref(false);
const loading = ref(true);
const form = reactive({ folderId: 0, title: '', documentNo: '', changeNote: '', remark: '' });
const folderOptions = computed(() => [{ id: 0, folderName: '未分类（根目录）' } as DocumentFolder, ...folders.value]);
const folderIndex = computed(() => Math.max(0, folderOptions.value.findIndex((item) => item.id === form.folderId)));
const currentProject = computed(() => projectStore.state.projects.find((item) => item.id === projectId.value));

onLoad(async (query) => {
  projectId.value = getQueryNumber(query?.projectId, projectStore.state.currentProjectId || 0);
  form.folderId = getQueryNumber(query?.folderId, 0);
  try {
    await projectStore.loadProjects();
    if (!projectId.value) projectId.value = projectStore.state.currentProjectId;
    folders.value = projectId.value ? await getDocumentFolders(projectId.value) : [];
  } catch (error) { showToast(error instanceof Error ? error.message : '目录加载失败'); }
  finally { loading.value = false; }
});

function goBack() { uni.navigateBack(); }

function chooseFileSource() {
  uni.showActionSheet({
    itemList: ['从微信文件选择', '拍摄照片', '从相册选择'],
    success: async ({ tapIndex }) => {
      try {
        const file = tapIndex === 0 ? await chooseMessageDocument() : await chooseDocumentImage(tapIndex === 1 ? 'camera' : 'album');
        selectedFile.value = file;
        if (!form.title.trim()) form.title = file.name.replace(/\.[^.]+$/, '');
      } catch (error) {
        const message = error instanceof Error ? error.message : '文件选择失败';
        if (message !== '已取消选择') showToast(message);
      }
    }
  });
}

function setFolder(event: unknown) {
  const index = Number((event as { detail?: { value?: number | string } }).detail?.value || 0);
  form.folderId = folderOptions.value[index]?.id || 0;
}

async function submit() {
  if (submitting.value) return;
  if (!projectId.value) { showToast('未找到当前施工区域'); return; }
  if (!selectedFile.value) { showToast('请先选择需要上传的文件'); return; }
  if (!form.title.trim()) { showToast('请填写资料名称'); return; }
  submitting.value = true;
  try {
    await createProjectDocument({
      filePath: selectedFile.value.path, fileName: selectedFile.value.name, fileSize: selectedFile.value.size,
      projectId: projectId.value, folderId: form.folderId, title: form.title.trim(), documentNo: form.documentNo.trim(),
      changeNote: form.changeNote.trim(), remark: form.remark.trim()
    });
    showToast('资料上传成功');
    setTimeout(() => uni.navigateBack(), 500);
  } catch (error) { showToast(error instanceof Error ? error.message : '资料上传失败'); }
  finally { submitting.value = false; }
}
</script>

<template>
  <view class="document-form-page">
    <AppNavBar title="上传资料" @back="goBack" />
    <scroll-view class="form-scroll" scroll-y>
      <view class="form-content">
        <view class="project-banner"><text>上传到</text><text>{{ currentProject?.projectName || currentProject?.shortName || '当前施工区域' }}</text></view>

        <button class="file-picker" :class="{ selected: selectedFile }" :disabled="loading || submitting" @tap="chooseFileSource">
          <view class="picker-mark">{{ selectedFile ? '✓' : '＋' }}</view>
          <view class="picker-copy"><text>{{ selectedFile?.name || '选择资料文件' }}</text><text>{{ selectedFile ? `${formatFileSize(selectedFile.size)} · 点击可重新选择` : '支持微信文件、拍照和相册，单文件不超过 50MB' }}</text></view>
        </button>

        <view class="form-panel">
          <view class="field"><text class="label">资料名称 *</text><input v-model="form.title" maxlength="200" placeholder="请输入资料名称" /></view>
          <view class="field"><text class="label">资料编号</text><input v-model="form.documentNo" maxlength="100" placeholder="例如：TZ-A1-001" /></view>
          <view class="field"><text class="label">资料目录</text><picker :range="folderOptions" range-key="folderName" :value="folderIndex" @change="setFolder"><view class="picker-value">{{ folderOptions[folderIndex]?.folderName }}</view></picker></view>
          <view class="field"><text class="label">版本说明</text><input v-model="form.changeNote" maxlength="200" placeholder="例如：首次上传" /></view>
          <view class="field"><text class="label">备注</text><textarea v-model="form.remark" maxlength="500" placeholder="补充资料用途、范围或注意事项" /></view>
        </view>

        <view class="submit-bar"><button class="cancel-button" :disabled="submitting" @tap="goBack">取消</button><button class="submit-button" :disabled="loading || submitting" @tap="submit">{{ submitting ? '正在上传...' : '上传 V1' }}</button></view>
      </view>
    </scroll-view>
  </view>
</template>

<style scoped>
.document-form-page { min-height: 100vh; background: #f4f6f7; color: #263449; }
.document-form-page :deep(.app-nav) { background: rgba(255,255,255,.97); box-shadow: 0 1rpx 0 rgba(148,163,184,.16); }
.form-scroll { height: calc(100vh - 92px); }
.form-content { display: flex; flex-direction: column; gap: 20rpx; padding: 24rpx 24rpx calc(40rpx + env(safe-area-inset-bottom)); }
.project-banner { display: flex; min-height: 72rpx; align-items: center; justify-content: space-between; gap: 20rpx; padding: 0 20rpx; border-radius: 14rpx; background: #eaf1f5; color: #60778a; font-size: 21rpx; }
.project-banner text:last-child { overflow: hidden; color: #365b73; font-size: 24rpx; font-weight: 750; text-overflow: ellipsis; white-space: nowrap; }
.file-picker { display: flex; width: 100%; min-height: 150rpx; align-items: center; justify-content: flex-start; gap: 18rpx; padding: 24rpx; border: 2rpx dashed #b9cbd6; border-radius: 16rpx; background: #f8fbfc; text-align: left; }
.file-picker::after,.cancel-button::after,.submit-button::after { border: 0; }
.file-picker.selected { border-style: solid; border-color: #bfd2dc; background: #edf4f7; }
.file-picker:active { transform: scale(.99); }
.picker-mark { display: flex; width: 64rpx; height: 64rpx; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 16rpx; background: #dfeaf0; color: #456b83; font-size: 35rpx; font-weight: 700; }
.selected .picker-mark { background: #e4f3ec; color: #2e8069; }
.picker-copy { min-width: 0; flex: 1; }
.picker-copy text { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.picker-copy text:first-child { color: #2a3b4e; font-size: 25rpx; font-weight: 750; }
.picker-copy text:last-child { margin-top: 9rpx; color: #81909f; font-size: 20rpx; }
.form-panel { padding: 24rpx; border-radius: 16rpx; background: #fff; box-shadow: 0 9rpx 30rpx rgba(43,56,72,.055); }
.field { min-width: 0; margin-bottom: 21rpx; }
.field:last-child { margin-bottom: 0; }
.field-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 14rpx; }
.label { display: block; margin-bottom: 9rpx; color: #576779; font-size: 22rpx; font-weight: 700; }
.field input,.field textarea,.picker-value { box-sizing: border-box; width: 100%; min-height: 76rpx; padding: 17rpx 18rpx; border: 1rpx solid #e1e6eb; border-radius: 12rpx; background: #f7f8fa; color: #2b3b4d; font-size: 23rpx; }
.field textarea { min-height: 140rpx; }
.picker-value { overflow: hidden; padding-right: 12rpx; text-overflow: ellipsis; white-space: nowrap; }
.submit-bar { display: grid; grid-template-columns: 1fr 2fr; gap: 14rpx; }
.submit-bar button { min-height: 78rpx; border-radius: 13rpx; font-size: 24rpx; font-weight: 750; }
.cancel-button { background: #fff; color: #5f6f80; }
.submit-button { background: #567b96; color: #fff; box-shadow: 0 10rpx 22rpx rgba(62,96,120,.17); }
.submit-button[disabled] { opacity: .65; }
@media (max-width: 360px) { .field-grid { grid-template-columns: 1fr; gap: 0; } }
</style>
