<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import WorkspaceStatusPill from '@/components/workspace/WorkspaceStatusPill.vue';
import {
  archiveProjectDocument, deleteProjectDocument, getDocumentFolders, getProjectDocumentDetail,
  unarchiveProjectDocument, updateProjectDocument, uploadProjectDocumentVersion
} from '@/api/document';
import type { DocumentFolder, ProjectDocumentDetail, ProjectDocumentVersion } from '@/types';
import { chooseMessageDocument, formatFileSize, openProjectDocument, saveProjectDocument, type LocalDocumentFile } from '@/utils/documentFile';
import { getQueryNumber, showToast, switchTab } from '@/utils/navigation';

type SheetMode = 'edit' | 'version' | null;

const documentId = ref(0);
const detail = ref<ProjectDocumentDetail | null>(null);
const folders = ref<DocumentFolder[]>([]);
const loading = ref(true);
const submitting = ref(false);
const errorMessage = ref('');
const sheetMode = ref<SheetMode>(null);
const versionFile = ref<LocalDocumentFile | null>(null);
const versionNote = ref('');
const editForm = reactive({ folderId: 0, title: '', documentNo: '', remark: '' });
const folderOptions = computed(() => [{ id: 0, folderName: '未分类（根目录）' } as DocumentFolder, ...folders.value]);
const folderIndex = computed(() => Math.max(0, folderOptions.value.findIndex((item) => item.id === editForm.folderId)));
const document = computed(() => detail.value?.document);

onLoad(async (query) => {
  documentId.value = getQueryNumber(query?.id, 0);
  await loadDetail();
});

async function loadDetail() {
  if (!documentId.value) { errorMessage.value = '资料参数无效'; loading.value = false; return; }
  loading.value = true;
  errorMessage.value = '';
  try { detail.value = await getProjectDocumentDetail(documentId.value); }
  catch (error) { errorMessage.value = error instanceof Error ? error.message : '资料详情加载失败'; }
  finally { loading.value = false; }
}

function goBack() { getCurrentPages().length > 1 ? uni.navigateBack() : switchTab('/pages/documents/index'); }
function formatTime(value?: string) { return value ? value.replace('T', ' ').slice(0, 16) : '时间未设置'; }

async function preview(version?: ProjectDocumentVersion) {
  if (!document.value) return;
  try { await openProjectDocument(document.value, version, true); }
  catch (error) { showToast(error instanceof Error ? error.message : '资料预览失败'); }
}

async function download(version?: ProjectDocumentVersion) {
  if (!document.value) return;
  try {
    await saveProjectDocument(document.value, version);
    showToast('文件已保存，可从微信文件中打开');
  } catch {
    try { await openProjectDocument(document.value, version, false); }
    catch (error) { showToast(error instanceof Error ? error.message : '资料下载失败'); }
  }
}

async function openEdit() {
  if (!document.value?.canEdit) return;
  try { folders.value = await getDocumentFolders(document.value.projectId); }
  catch { folders.value = []; }
  Object.assign(editForm, {
    folderId: document.value.folderId || 0, title: document.value.title, documentNo: document.value.documentNo || '',
    remark: document.value.remark || ''
  });
  sheetMode.value = 'edit';
}

function openVersion() { versionFile.value = null; versionNote.value = ''; sheetMode.value = 'version'; }

async function chooseVersionFile() {
  try { versionFile.value = await chooseMessageDocument(); }
  catch (error) { const message = error instanceof Error ? error.message : '文件选择失败'; if (message !== '已取消选择') showToast(message); }
}

function setFolder(event: unknown) {
  const index = Number((event as { detail?: { value?: number | string } }).detail?.value || 0);
  editForm.folderId = folderOptions.value[index]?.id || 0;
}

async function submitEdit() {
  if (!document.value || !editForm.title.trim() || submitting.value) { if (!editForm.title.trim()) showToast('请填写资料名称'); return; }
  submitting.value = true;
  try {
    detail.value = await updateProjectDocument(document.value.id, { ...editForm, title: editForm.title.trim(), documentNo: editForm.documentNo.trim(), remark: editForm.remark.trim() });
    sheetMode.value = null; showToast('资料信息已更新');
  } catch (error) { showToast(error instanceof Error ? error.message : '资料更新失败'); }
  finally { submitting.value = false; }
}

async function submitVersion() {
  if (!document.value || !versionFile.value || submitting.value) { if (!versionFile.value) showToast('请选择新版本文件'); return; }
  submitting.value = true;
  try {
    detail.value = await uploadProjectDocumentVersion(document.value.id, versionFile.value.path, versionFile.value.name, versionFile.value.size, versionNote.value.trim());
    sheetMode.value = null; showToast('新版本上传成功');
  } catch (error) { showToast(error instanceof Error ? error.message : '版本上传失败'); }
  finally { submitting.value = false; }
}

function confirmAction(content: string, confirmText: string, action: () => Promise<unknown>) {
  if (submitting.value) return;
  uni.showModal({
    title: '操作确认', content, confirmText, confirmColor: confirmText.includes('删除') ? '#B75353' : '#567B96',
    success: async (result) => {
      if (!result.confirm || submitting.value) return;
      submitting.value = true;
      try { await action(); showToast('操作成功'); await loadDetail(); }
      catch (error) { showToast(error instanceof Error ? error.message : '操作失败'); }
      finally { submitting.value = false; }
    }
  });
}

function archive() {
  if (!document.value) return;
  confirmAction('归档后资料只允许查看和下载，确认继续？', '确认归档', () => archiveProjectDocument(document.value!.id));
}

function unarchive() {
  if (!document.value) return;
  confirmAction('恢复后资料重新进入使用中状态。', '恢复归档', () => unarchiveProjectDocument(document.value!.id));
}

function remove() {
  if (!document.value) return;
  const title = document.value.title;
  uni.showModal({
    title: '移入回收站', content: `确认删除《${title}》？管理员可在回收站恢复。`, confirmText: '删除', confirmColor: '#B75353',
    success: async (result) => {
      if (!result.confirm || submitting.value) return;
      submitting.value = true;
      try { await deleteProjectDocument(document.value!.id); showToast('已移入回收站'); setTimeout(goBack, 450); }
      catch (error) { showToast(error instanceof Error ? error.message : '删除失败'); }
      finally { submitting.value = false; }
    }
  });
}
</script>

<template>
  <view class="detail-page">
    <AppNavBar title="资料详情" @back="goBack" />
    <scroll-view class="detail-scroll" scroll-y>
      <view class="detail-content">
        <view v-if="loading" class="detail-state"><text>正在加载资料详情</text></view>
        <view v-else-if="errorMessage" class="detail-state"><text>{{ errorMessage }}</text><button @tap="loadDetail">重新加载</button></view>
        <template v-else-if="detail && document">
          <view class="detail-hero">
            <view class="document-mark">{{ (document.currentVersion?.fileExtension || 'FILE').slice(0, 5).toUpperCase() }}</view>
            <view class="hero-copy"><text class="hero-no">{{ document.documentNo || '无资料编号' }}</text><text class="hero-title">{{ document.title }}</text><text class="hero-meta">{{ document.currentVersion?.versionLabel }} · {{ formatFileSize(document.currentVersion?.fileSize) }} · {{ document.folderName }}</text></view>
            <WorkspaceStatusPill :label="document.status === 'ARCHIVED' ? '已归档' : '使用中'" :tone="document.status === 'ARCHIVED' ? 'gray' : 'green'" />
          </view>

          <view class="primary-actions"><button @tap="preview()"><text class="action-eye"></text><text>预览</text></button><button @tap="download()"><text class="action-download"></text><text>下载</text></button><button v-if="document.canEdit" @tap="openVersion"><text class="action-version">V+</text><text>新版本</text></button><button v-if="document.canEdit" @tap="openEdit"><text class="action-edit"></text><text>编辑</text></button></view>

          <view class="detail-section">
            <view class="detail-head"><text>资料属性</text><text>基础信息</text></view>
            <view class="property-list"><view><text>所属目录</text><text>{{ document.folderName || '根目录' }}</text></view><view><text>上传人</text><text>{{ document.createdByName || '-' }}</text></view><view><text>上传时间</text><text>{{ formatTime(document.createTime) }}</text></view><view><text>更新时间</text><text>{{ formatTime(document.updateTime) }}</text></view><view class="wide"><text>备注</text><text>{{ document.remark || '无' }}</text></view></view>
          </view>

          <view class="detail-section">
            <view class="detail-head"><text>版本记录</text><text>{{ detail.versions.length }} 个版本</text></view>
            <view class="version-list"><view v-for="version in detail.versions" :key="version.id" class="version-row"><view class="version-badge">{{ version.versionLabel }}</view><view class="version-copy"><text>{{ version.fileName }}</text><text>{{ version.changeNote || '无版本说明' }}</text><text>{{ version.createdByName || '-' }} · {{ formatTime(version.createTime) }}</text></view><view class="version-actions"><button @tap="preview(version)">预览</button><button @tap="download(version)">下载</button></view></view></view>
          </view>

          <view class="detail-section">
            <view class="detail-head"><text>操作记录</text><text>最近 {{ detail.activities.length }} 条</text></view>
            <view class="activity-list"><view v-for="activity in detail.activities" :key="activity.id" class="activity-row"><text class="activity-dot"></text><view><text>{{ activity.operationLabel }}</text><text>{{ activity.description || '无补充说明' }}</text><text>{{ activity.operatorName || '-' }} · {{ formatTime(activity.createTime) }}</text></view></view><view v-if="!detail.activities.length" class="empty-line">暂无操作记录</view></view>
          </view>

          <view v-if="document.canEdit || (document.canManage && document.status === 'ARCHIVED')" class="manage-section">
            <button v-if="document.canEdit && document.status === 'ACTIVE'" @tap="archive">归档资料</button>
            <button v-if="document.canManage && document.status === 'ARCHIVED'" @tap="unarchive">恢复归档</button>
            <button v-if="document.canEdit" class="danger" @tap="remove">移入回收站</button>
          </view>
        </template>
      </view>
    </scroll-view>

    <view v-if="sheetMode" class="form-overlay" @tap="sheetMode = null">
      <view class="form-sheet" @tap.stop>
        <view class="sheet-handle"></view><view class="form-head"><text class="form-title">{{ sheetMode === 'edit' ? '编辑资料属性' : '上传新版本' }}</text><button class="form-close" @tap="sheetMode = null">×</button></view>
        <template v-if="sheetMode === 'edit'">
          <view class="form-field"><text class="form-label">资料名称 *</text><input v-model="editForm.title" class="form-input" maxlength="200" /></view>
          <view class="form-field"><text class="form-label">资料编号</text><input v-model="editForm.documentNo" class="form-input" maxlength="100" /></view>
          <view class="form-field"><text class="form-label">所属目录</text><picker :range="folderOptions" range-key="folderName" :value="folderIndex" @change="setFolder"><view class="form-picker">{{ folderOptions[folderIndex]?.folderName }}</view></picker></view>
          <view class="form-field"><text class="form-label">备注</text><textarea v-model="editForm.remark" class="form-textarea" maxlength="500" /></view>
          <view class="form-actions"><button class="secondary-action" @tap="sheetMode = null">取消</button><button class="primary-action" :disabled="submitting" @tap="submitEdit">保存修改</button></view>
        </template>
        <template v-else>
          <button class="version-picker" :class="{ selected: versionFile }" @tap="chooseVersionFile"><text>{{ versionFile ? '✓' : '＋' }}</text><view><text>{{ versionFile?.name || '选择新版本文件' }}</text><text>{{ versionFile ? formatFileSize(versionFile.size) : '旧版本会继续保留' }}</text></view></button>
          <view class="form-field"><text class="form-label">版本说明</text><textarea v-model="versionNote" class="form-textarea" maxlength="200" placeholder="填写本次版本变更内容" /></view>
          <view class="form-actions"><button class="secondary-action" @tap="sheetMode = null">取消</button><button class="primary-action" :disabled="submitting" @tap="submitVersion">上传新版本</button></view>
        </template>
      </view>
    </view>
  </view>
</template>

<style scoped src="../../styles/workspace-page.css"></style>
<style scoped>
.detail-page { --page-accent: #567b96; --page-accent-deep: #3e6078; --page-tint: #eaf1f5; min-height: 100vh; background: #f4f6f7; color: #263449; }
.detail-page :deep(.app-nav) { background: rgba(255,255,255,.97); box-shadow: 0 1rpx 0 rgba(148,163,184,.16); }
.detail-scroll { height: calc(100vh - 92px); }
.detail-content { display: flex; flex-direction: column; gap: 20rpx; padding: 24rpx 24rpx calc(42rpx + env(safe-area-inset-bottom)); }
.detail-state { display: flex; min-height: 240rpx; align-items: center; justify-content: center; flex-direction: column; color: #788695; font-size: 23rpx; }
.detail-state button { min-height: 60rpx; margin-top: 18rpx; padding: 0 22rpx; border-radius: 12rpx; background: #e5edf2; color: #456b83; font-size: 22rpx; }
.detail-state button::after,.primary-actions button::after,.version-actions button::after,.manage-section button::after,.version-picker::after { border: 0; }
.detail-hero { display: flex; align-items: flex-start; gap: 17rpx; padding: 24rpx; border-radius: 16rpx; background: #eaf1f5; box-shadow: 0 9rpx 28rpx rgba(62,96,120,.07); }
.document-mark { display: flex; width: 72rpx; height: 78rpx; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 13rpx 13rpx 18rpx 13rpx; background: #fff; color: #456b83; font-size: 18rpx; font-weight: 850; }
.hero-copy { min-width: 0; flex: 1; }
.hero-copy text { display: block; }
.hero-no { color: #708696; font-size: 19rpx; font-weight: 700; }
.hero-title { margin-top: 6rpx; color: #243648; font-size: 29rpx; font-weight: 800; line-height: 1.25; }
.hero-meta { margin-top: 9rpx; color: #718392; font-size: 20rpx; }
.primary-actions { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); padding: 13rpx; border-radius: 16rpx; background: #fff; box-shadow: 0 8rpx 26rpx rgba(43,56,72,.055); }
.primary-actions button { min-height: 78rpx; flex-direction: column; gap: 8rpx; color: #52687a; font-size: 20rpx; }
.primary-actions button:active { border-radius: 12rpx; background: #edf3f6; transform: scale(.98); }
.primary-actions button > text:first-child { display: flex; width: 32rpx; height: 30rpx; align-items: center; justify-content: center; color: #4f748e; font-size: 19rpx; font-weight: 850; }
.action-eye { position: relative; border: 3rpx solid #5d7b90; border-radius: 60% 40%; transform: rotate(45deg); }
.action-eye::after { width: 8rpx; height: 8rpx; border-radius: 50%; background: #5d7b90; content: ''; }
.action-download { position: relative; border-bottom: 3rpx solid #5d7b90; }
.action-download::before { position: absolute; top: 0; left: 14rpx; width: 3rpx; height: 19rpx; background: #5d7b90; content: ''; }
.action-download::after { position: absolute; top: 10rpx; left: 9rpx; width: 10rpx; height: 10rpx; border-right: 3rpx solid #5d7b90; border-bottom: 3rpx solid #5d7b90; content: ''; transform: rotate(45deg); }
.action-edit { border: 3rpx solid #5d7b90; border-radius: 5rpx; transform: rotate(-8deg); }
.detail-section { overflow: hidden; border-radius: 16rpx; background: #fff; box-shadow: 0 8rpx 26rpx rgba(43,56,72,.055); }
.detail-head { display: flex; min-height: 76rpx; align-items: center; justify-content: space-between; gap: 15rpx; padding: 0 22rpx; background: #eef3f6; }
.detail-head > text:first-child { color: #2d4053; font-size: 25rpx; font-weight: 780; }
.detail-head > text:last-child { color: #82909d; font-size: 19rpx; }
.property-list { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); padding: 10rpx 22rpx 18rpx; }
.property-list view { min-width: 0; padding: 15rpx 0; border-bottom: 1rpx solid #edf0f3; }
.property-list view:nth-child(odd) { padding-right: 14rpx; }
.property-list view:nth-child(even) { padding-left: 14rpx; }
.property-list .wide { grid-column: 1 / -1; padding-right: 0; padding-left: 0; border-bottom: 0; }
.property-list text { display: block; }
.property-list text:first-child { color: #8a96a3; font-size: 19rpx; }
.property-list text:last-child { overflow: hidden; margin-top: 7rpx; color: #34485a; font-size: 22rpx; line-height: 1.35; text-overflow: ellipsis; }
.version-list,.activity-list { padding: 3rpx 20rpx 12rpx; }
.version-row { display: flex; align-items: center; gap: 13rpx; padding: 18rpx 2rpx; border-bottom: 1rpx solid #edf0f3; }
.version-row:last-child { border-bottom: 0; }
.version-badge { display: flex; width: 54rpx; height: 54rpx; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 12rpx; background: #e7eff4; color: #456b83; font-size: 20rpx; font-weight: 800; }
.version-copy { min-width: 0; flex: 1; }
.version-copy text { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.version-copy text:first-child { color: #304355; font-size: 22rpx; font-weight: 700; }
.version-copy text:nth-child(2),.version-copy text:last-child { margin-top: 5rpx; color: #8b96a2; font-size: 18rpx; }
.version-actions { display: flex; flex-direction: column; gap: 7rpx; }
.version-actions button { min-width: 62rpx; min-height: 37rpx; border-radius: 8rpx; background: #edf3f6; color: #4f7086; font-size: 18rpx; }
.activity-row { display: flex; gap: 14rpx; padding: 17rpx 2rpx; }
.activity-dot { width: 11rpx; height: 11rpx; flex-shrink: 0; margin-top: 9rpx; border-radius: 50%; background: #7394aa; box-shadow: 0 0 0 7rpx #e8f0f4; }
.activity-row view { min-width: 0; }
.activity-row view text { display: block; }
.activity-row view text:first-child { color: #33475a; font-size: 22rpx; font-weight: 730; }
.activity-row view text:nth-child(2) { margin-top: 6rpx; color: #69798a; font-size: 20rpx; line-height: 1.4; }
.activity-row view text:last-child { margin-top: 6rpx; color: #98a2ad; font-size: 18rpx; }
.empty-line { padding: 30rpx; color: #98a2ad; font-size: 21rpx; text-align: center; }
.manage-section { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 13rpx; padding: 14rpx; border-radius: 16rpx; background: #fff; }
.manage-section button { min-height: 68rpx; border-radius: 12rpx; background: #edf3f6; color: #4f7086; font-size: 22rpx; font-weight: 700; }
.manage-section button.danger { background: #fceeed; color: #b75353; }
.form-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 13rpx; }
.version-picker { display: flex; width: 100%; min-height: 112rpx; align-items: center; justify-content: flex-start; gap: 16rpx; margin-bottom: 20rpx; padding: 18rpx; border: 2rpx dashed #bdccd6; border-radius: 14rpx; background: #f6f9fa; text-align: left; }
.version-picker > text { display: flex; width: 50rpx; height: 50rpx; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 12rpx; background: #e5edf2; color: #52758c; font-size: 29rpx; }
.version-picker view { min-width: 0; }
.version-picker view text { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.version-picker view text:first-child { color: #34495b; font-size: 23rpx; font-weight: 700; }
.version-picker view text:last-child { margin-top: 6rpx; color: #8a96a3; font-size: 19rpx; }
.version-picker.selected { border-style: solid; background: #edf4f7; }
@media (max-width: 360px) { .property-list,.form-grid { grid-template-columns: 1fr; } .property-list view,.property-list view:nth-child(odd),.property-list view:nth-child(even) { padding-right: 0; padding-left: 0; } }
</style>
