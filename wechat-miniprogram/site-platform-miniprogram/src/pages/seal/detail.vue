<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import {
  approveSealApplication,
  archiveSealApplicationFile,
  copySealApplication,
  deleteSealApplicationFile,
  downloadSealApplicationFile,
  downloadSealApplicationPdf,
  getSealApplication,
  rejectSealApplication,
  submitSealApplication,
  uploadSealApplicationFile,
  withdrawSealApplication
} from '@/api/seal';
import { getDocumentFolders, getProjectDocuments } from '@/api/document';
import type { DocumentFolder, ProjectDocument, SealApplicationDetail, SealApplicationFile } from '@/types';
import { useAuthStore } from '@/stores/auth';
import { chooseMessageDocument, extensionOf, formatFileSize } from '@/utils/documentFile';
import { ensureSealPageAccess } from '@/utils/sealAccess';
import { getQueryNumber, navigateTo, showToast } from '@/utils/navigation';

type OpinionAction = 'APPROVE' | 'REJECT' | null;
type ArchiveMode = 'NEW_DOCUMENT' | 'NEW_VERSION';
const ARCHIVE_DOCUMENT_PAGE_SIZE = 50;

const auth = useAuthStore();
const applicationId = ref(0);
const detail = ref<SealApplicationDetail | null>(null);
const loading = ref(true);
const busy = ref(false);
const errorMessage = ref('');
const opinionAction = ref<OpinionAction>(null);
const opinion = ref('');
const archiveOpen = ref(false);
const archiveFile = ref<SealApplicationFile | null>(null);
const archiveMode = ref<ArchiveMode>('NEW_DOCUMENT');
const folders = ref<DocumentFolder[]>([]);
const documents = ref<ProjectDocument[]>([]);
const archiveDocumentKeyword = ref('');
const archiveDocumentAppliedKeyword = ref('');
const archiveDocumentPageNo = ref(1);
const archiveDocumentTotal = ref(0);
const archiveDocumentsLoading = ref(false);
const archiveDocumentsLoadingMore = ref(false);
const archiveForm = reactive({ folderId: 0, documentId: 0, title: '', documentNo: '', changeNote: '' });
let archiveDocumentRequestSequence = 0;

const sourceFiles = computed(() => detail.value?.files.filter((item) => item.fileRole === 'SOURCE') || []);
const stampedFiles = computed(() => detail.value?.files.filter((item) => item.fileRole === 'STAMPED_RESULT') || []);
const folderOptions = computed(() => [{ id: 0, folderName: '未分类（根目录）' } as DocumentFolder, ...folders.value]);
const folderIndex = computed(() => Math.max(0, folderOptions.value.findIndex((item) => item.id === archiveForm.folderId)));
const documentIndex = computed(() => Math.max(0, documents.value.findIndex((item) => item.id === archiveForm.documentId)));
const selectedArchiveDocument = computed(() => documents.value.find((item) => item.id === archiveForm.documentId));
const hasMoreArchiveDocuments = computed(() => documents.value.length < archiveDocumentTotal.value);
const canCopy = computed(() => Boolean(detail.value
  && ['REJECTED', 'WITHDRAWN'].includes(detail.value.status)
  && Number(detail.value.applicantId) === Number(auth.state.user?.id)));

onLoad(async (options) => {
  applicationId.value = getQueryNumber(options?.id, 0);
  if (!applicationId.value) { errorMessage.value = '缺少用印申请编号'; loading.value = false; return; }
  if (!await ensureSealPageAccess(`/pages/seal/detail?id=${applicationId.value}`)) { loading.value = false; return; }
  await loadDetail();
});

async function loadDetail() {
  loading.value = true;
  errorMessage.value = '';
  try { detail.value = await getSealApplication(applicationId.value); }
  catch (error) { errorMessage.value = error instanceof Error ? error.message : '用印申请加载失败'; }
  finally { loading.value = false; }
}

function statusLabel(value?: string) {
  return ({ DRAFT: '草稿', PENDING_APPROVAL: '审批中', APPROVED: '已批准', REJECTED: '已驳回', WITHDRAWN: '已撤回' } as Record<string,string>)[String(value)] || value || '-';
}

function formatTime(value?: string) { return value ? value.replace('T', ' ').slice(0, 16) : '—'; }
function fileName(file: SealApplicationFile) { return file.originalFileName || file.fileName || '用印文件'; }

function goBack() { getCurrentPages().length > 1 ? uni.navigateBack() : uni.reLaunch({ url: '/pages/seal/list' }); }

function confirmModal(title: string, content: string) {
  return new Promise<boolean>((resolve) => uni.showModal({ title, content, success: (result) => resolve(Boolean(result.confirm)), fail: () => resolve(false) }));
}

async function submitDraft() {
  if (!detail.value?.canSubmit || busy.value) return;
  if (!sourceFiles.value.length) { showToast('请先编辑草稿并上传待盖章资料'); return; }
  if (!await confirmModal('提交用印审批', '提交后申请内容和原始资料将不可修改，确认继续？')) return;
  busy.value = true;
  try { detail.value = await submitSealApplication(applicationId.value); showToast('申请已提交审批'); }
  catch (error) { showToast(error instanceof Error ? error.message : '提交失败'); }
  finally { busy.value = false; }
}

function openOpinion(action: Exclude<OpinionAction, null>) { opinion.value = ''; opinionAction.value = action; }

async function submitOpinion() {
  if (!detail.value || !opinionAction.value || busy.value) return;
  if (!opinion.value.trim()) { showToast(opinionAction.value === 'APPROVE' ? '请填写项目经理审批意见' : '请填写驳回原因'); return; }
  busy.value = true;
  try {
    detail.value = opinionAction.value === 'APPROVE'
      ? await approveSealApplication(detail.value.id, opinion.value.trim())
      : await rejectSealApplication(detail.value.id, opinion.value.trim());
    showToast(opinionAction.value === 'APPROVE' ? '审批已通过' : '申请已驳回');
    opinionAction.value = null;
  } catch (error) { showToast(error instanceof Error ? error.message : '审批操作失败'); }
  finally { busy.value = false; }
}

async function withdraw() {
  if (!detail.value?.canCancel || busy.value) return;
  if (!await confirmModal('撤回用印申请', '撤回后本次申请终止，如需再次申请可复制原内容。')) return;
  busy.value = true;
  try { detail.value = await withdrawSealApplication(detail.value.id); showToast('申请已撤回'); }
  catch (error) { showToast(error instanceof Error ? error.message : '撤回失败'); }
  finally { busy.value = false; }
}

async function copyApplication() {
  if (!detail.value || !canCopy.value || busy.value) return;
  if (!await confirmModal('复制用印申请', '将复制申请内容和抄送人，待盖章资料不会复制。是否继续？')) return;
  busy.value = true;
  try {
    const copied = await copySealApplication(detail.value.id, {
      requestKey: `seal-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`,
      ccUserIds: detail.value.ccRecipients.map((item) => item.userId)
    });
    showToast('已生成新草稿，请补充本次待盖章资料');
    setTimeout(() => uni.redirectTo({ url: `/pages/seal/apply?id=${copied.id}` }), 450);
  } catch (error) { showToast(error instanceof Error ? error.message : '复制申请失败'); }
  finally { busy.value = false; }
}

async function uploadStampedFile() {
  if (!detail.value?.canUploadStampedResult || busy.value) return;
  if (stampedFiles.value.length >= 20) { showToast('盖章件最多上传 20 个文件'); return; }
  try {
    const file = await chooseMessageDocument();
    busy.value = true;
    await uploadSealApplicationFile(detail.value.id, file.path, 'STAMPED_RESULT');
    await loadDetail();
    showToast('盖章件已上传');
  } catch (error) {
    const message = error instanceof Error ? error.message : '盖章件上传失败';
    if (message !== '已取消选择') showToast(message);
  } finally { busy.value = false; }
}

async function removeFile(file: SealApplicationFile) {
  if (!file.canDelete || busy.value || !await confirmModal('删除附件', `确认删除《${fileName(file)}》？`)) return;
  busy.value = true;
  try { await deleteSealApplicationFile(applicationId.value, file.id); await loadDetail(); showToast('附件已删除'); }
  catch (error) { showToast(error instanceof Error ? error.message : '附件删除失败'); }
  finally { busy.value = false; }
}

async function openFile(file: SealApplicationFile) {
  if (busy.value) return;
  busy.value = true;
  try {
    const path = await downloadSealApplicationFile(applicationId.value, file.id, Boolean(file.canPreview));
    const extension = extensionOf(fileName(file)) || String(file.fileExtension || '').replace(/^\./, '').toLowerCase();
    if (['jpg','jpeg','png','gif','webp','bmp'].includes(extension)) {
      uni.previewImage({ urls: [path], current: path });
    } else {
      await new Promise<void>((resolve, reject) => uni.openDocument({ filePath: path, fileType: extension || undefined,
        showMenu: true, success: () => resolve(), fail: (error) => reject(new Error(error.errMsg || '文件打开失败')) }));
    }
  } catch (error) { showToast(error instanceof Error ? error.message : '文件打开失败'); }
  finally { busy.value = false; }
}

async function openApplicationForm() {
  if (busy.value) return;
  busy.value = true;
  try {
    const path = await downloadSealApplicationPdf(applicationId.value);
    await new Promise<void>((resolve, reject) => uni.openDocument({ filePath: path, fileType: 'pdf', showMenu: true,
      success: () => resolve(), fail: (error) => reject(new Error(error.errMsg || '申请单打开失败')) }));
  } catch (error) { showToast(error instanceof Error ? error.message : '申请单导出失败'); }
  finally { busy.value = false; }
}

async function loadArchiveDocuments(reset: boolean) {
  if (!detail.value || archiveDocumentsLoading.value || archiveDocumentsLoadingMore.value) return;
  const requestSequence = ++archiveDocumentRequestSequence;
  const pageNo = reset ? 1 : archiveDocumentPageNo.value + 1;
  if (reset) archiveDocumentsLoading.value = true;
  else archiveDocumentsLoadingMore.value = true;
  try {
    const result = await getProjectDocuments({
      projectId: detail.value.projectId,
      status: 'ACTIVE',
      keyword: archiveDocumentAppliedKeyword.value || undefined,
      pageNo,
      pageSize: ARCHIVE_DOCUMENT_PAGE_SIZE
    });
    if (requestSequence !== archiveDocumentRequestSequence) return;
    const records = result.records || [];
    if (reset) documents.value = records;
    else {
      const loadedIds = new Set(documents.value.map((item) => item.id));
      documents.value = [...documents.value, ...records.filter((item) => !loadedIds.has(item.id))];
    }
    archiveDocumentPageNo.value = pageNo;
    archiveDocumentTotal.value = Number(result.total ?? records.length);
  } catch (error) {
    if (requestSequence !== archiveDocumentRequestSequence) return;
    if (reset) { documents.value = []; archiveDocumentTotal.value = 0; }
    showToast(error instanceof Error ? error.message : '工程资料加载失败');
  } finally {
    if (requestSequence === archiveDocumentRequestSequence) {
      archiveDocumentsLoading.value = false;
      archiveDocumentsLoadingMore.value = false;
    }
  }
}

async function searchArchiveDocuments() {
  if (archiveDocumentsLoading.value || archiveDocumentsLoadingMore.value) return;
  archiveDocumentAppliedKeyword.value = archiveDocumentKeyword.value.trim();
  archiveForm.documentId = 0;
  await loadArchiveDocuments(true);
}

async function loadMoreArchiveDocuments() {
  if (!hasMoreArchiveDocuments.value) return;
  await loadArchiveDocuments(false);
}

function closeArchive() {
  archiveDocumentRequestSequence += 1;
  archiveDocumentsLoading.value = false;
  archiveDocumentsLoadingMore.value = false;
  archiveOpen.value = false;
}

async function openArchive(file: SealApplicationFile) {
  if (!detail.value?.canArchive || file.archivedDocumentId) return;
  archiveFile.value = file;
  archiveMode.value = 'NEW_DOCUMENT';
  archiveForm.folderId = 0;
  archiveForm.documentId = 0;
  archiveForm.title = fileName(file).replace(/\.[^.]+$/, '');
  archiveForm.documentNo = detail.value.applicationNo || '';
  archiveForm.changeNote = '用印审批通过后归档盖章件';
  archiveDocumentKeyword.value = '';
  archiveDocumentAppliedKeyword.value = '';
  archiveDocumentPageNo.value = 1;
  archiveDocumentTotal.value = 0;
  documents.value = [];
  archiveOpen.value = true;
  const folderRequest = getDocumentFolders(detail.value.projectId);
  const documentRequest = loadArchiveDocuments(true);
  try { folders.value = await folderRequest; }
  catch (error) { folders.value = []; showToast(error instanceof Error ? error.message : '资料目录加载失败'); }
  await documentRequest;
}

function changeFolder(event: unknown) {
  const index = Number((event as { detail?: { value?: string | number } }).detail?.value || 0);
  archiveForm.folderId = folderOptions.value[index]?.id || 0;
}
function changeDocument(event: unknown) {
  const index = Number((event as { detail?: { value?: string | number } }).detail?.value || 0);
  archiveForm.documentId = documents.value[index]?.id || 0;
}

async function confirmArchive() {
  if (!detail.value || !archiveFile.value || busy.value) return;
  if (archiveMode.value === 'NEW_DOCUMENT' && !archiveForm.title.trim()) { showToast('请填写归档资料名称'); return; }
  if (archiveMode.value === 'NEW_VERSION' && !archiveForm.documentId) { showToast('请选择要追加版本的现有资料'); return; }
  busy.value = true;
  try {
    detail.value = await archiveSealApplicationFile(detail.value.id, {
      fileId: archiveFile.value.id,
      archiveMode: archiveMode.value,
      folderId: archiveMode.value === 'NEW_DOCUMENT' ? archiveForm.folderId : undefined,
      documentId: archiveMode.value === 'NEW_VERSION' ? archiveForm.documentId : undefined,
      documentNo: archiveMode.value === 'NEW_DOCUMENT' ? archiveForm.documentNo.trim() || undefined : undefined,
      title: archiveMode.value === 'NEW_DOCUMENT' ? archiveForm.title.trim() : undefined,
      changeNote: archiveForm.changeNote.trim() || undefined
    });
    closeArchive();
    showToast(archiveMode.value === 'NEW_DOCUMENT' ? '已归档为新资料' : '已追加为资料新版本');
  } catch (error) { showToast(error instanceof Error ? error.message : '归档失败'); }
  finally { busy.value = false; }
}
</script>

<template>
  <view class="detail-page">
    <AppNavBar title="用印详情" @back="goBack" />
    <scroll-view class="detail-scroll" scroll-y>
      <view class="detail-content">
        <view v-if="loading" class="state-card">正在加载用印申请…</view>
        <view v-else-if="errorMessage" class="state-card error"><text>{{ errorMessage }}</text><button @tap="loadDetail">重新加载</button></view>
        <template v-else-if="detail">
          <view class="status-card" :class="detail.status.toLowerCase()"><view class="seal-mark">印</view><view><text>{{ statusLabel(detail.status) }}</text><text>{{ detail.applicationNo || `草稿 #${detail.id}` }}</text></view><button v-if="detail.status === 'APPROVED'" @tap="openApplicationForm">申请单 PDF</button></view>

          <view class="info-card">
            <view class="section-title">申请信息</view>
            <view class="row"><text>公司名称</text><text>{{ detail.companyName || '—' }}</text></view><view class="row"><text>申请部门 / 项目部</text><text>{{ detail.departmentName || detail.projectName }}</text></view><view class="row"><text>使用印章</text><text>{{ detail.sealName }}</text></view><view class="row"><text>申请人</text><text>{{ detail.applicantName || '—' }}</text></view><view class="row"><text>联系方式</text><text>{{ detail.applicantPhone || '—' }}</text></view><view class="row"><text>申请日期</text><text>{{ formatTime(detail.submitTime || detail.applicationDate || detail.createTime) }}</text></view>
            <view class="purpose"><text>用印事由</text><text>{{ detail.purpose }}</text></view>
          </view>

          <view class="info-card"><view class="section-title">用印文件清单</view><view v-for="(item,index) in detail.items" :key="item.id || index" class="item"><text>{{ index + 1 }}</text><text>{{ item.documentName }}</text><text>{{ item.copies }} 份</text></view></view>

          <view class="info-card"><view class="section-head"><view><text class="section-title">待盖章资料</text><text>{{ sourceFiles.length }} 个源文件</text></view></view><view v-for="file in sourceFiles" :key="file.id" class="file-row"><button class="file-main" @tap="openFile(file)"><view><text>{{ fileName(file) }}</text><text>{{ formatFileSize(file.fileSize) }} · {{ file.uploaderName || '申请人' }}</text></view><text>打开</text></button><button v-if="file.canDelete" class="delete" @tap="removeFile(file)">删除</button></view><view v-if="!sourceFiles.length" class="empty-line">尚未上传待盖章资料</view></view>

          <view class="info-card result-card"><view class="section-head"><view><text class="section-title">盖章件与资料归档</text><text>审批通过后补传，归档时可新建资料或追加版本</text></view><button v-if="detail.canUploadStampedResult" :disabled="busy" @tap="uploadStampedFile">＋ 补传盖章件</button></view><view v-for="file in stampedFiles" :key="file.id" class="result-file"><button class="file-main" @tap="openFile(file)"><view><text>{{ fileName(file) }}</text><text>{{ formatFileSize(file.fileSize) }} · {{ file.archivedDocumentId ? '已归档资料库' : '待归档' }}</text></view><text>打开</text></button><view class="file-actions"><button v-if="detail.canArchive && !file.archivedDocumentId" @tap="openArchive(file)">归档</button><button v-if="file.archivedDocumentId" @tap="navigateTo(`/pages/documents/detail?id=${file.archivedDocumentId}`)">查看资料</button><button v-if="file.canDelete" class="delete" @tap="removeFile(file)">删除</button></view></view><view v-if="!stampedFiles.length" class="empty-line">{{ detail.status === 'APPROVED' ? '审批已通过，请补传最终盖章件' : '审批通过后可补传盖章件' }}</view></view>

          <view v-if="detail.approvalOpinion || detail.approverName" class="opinion-card"><text>项目经理审批意见</text><text>{{ detail.approvalOpinion || '无' }}</text><text>{{ detail.approverName || '审批人' }} · {{ formatTime(detail.approvalTime) }}</text></view>
          <view v-if="detail.ccRecipients.length" class="info-card"><view class="section-title">通知抄送</view><view class="cc-list"><text v-for="item in detail.ccRecipients" :key="item.userId">{{ item.displayName }}</text></view></view>
          <view v-if="detail.logs.length" class="info-card"><view class="section-title">流转记录</view><view v-for="log in detail.logs" :key="log.id" class="log-row"><view></view><view><text>{{ log.actionLabel || log.action }}</text><text v-if="log.opinion || log.description">{{ log.opinion || log.description }}</text><text>{{ log.operatorName || '系统' }} · {{ formatTime(log.createTime) }}</text></view></view></view>

          <view class="action-row"><button v-if="detail.canEdit" @tap="navigateTo(`/pages/seal/apply?id=${detail.id}`)">继续填写</button><button v-if="detail.canSubmit" class="primary" :disabled="busy" @tap="submitDraft">提交审批</button><button v-if="detail.canApprove" class="approve" :disabled="busy" @tap="openOpinion('APPROVE')">审批通过</button><button v-if="detail.canReject" class="reject" :disabled="busy" @tap="openOpinion('REJECT')">驳回</button><button v-if="detail.canCancel" class="reject" :disabled="busy" @tap="withdraw">撤回</button><button v-if="canCopy" :disabled="busy" @tap="copyApplication">复制申请</button></view>
        </template>
      </view>
    </scroll-view>

    <view v-if="opinionAction" class="overlay" @tap="opinionAction = null"><view class="sheet" @tap.stop><view class="sheet-head"><view><text>{{ opinionAction === 'APPROVE' ? '填写项目经理审批意见' : '填写驳回原因' }}</text><text>审批意见会进入申请单和流转记录</text></view><button @tap="opinionAction = null">×</button></view><textarea v-model="opinion" maxlength="1000" :placeholder="opinionAction === 'APPROVE' ? '请输入项目经理审批意见' : '请明确填写需修改的内容'" /><button class="confirm" :class="{ danger: opinionAction === 'REJECT' }" :disabled="busy" @tap="submitOpinion">确认{{ opinionAction === 'APPROVE' ? '通过' : '驳回' }}</button></view></view>

    <view v-if="archiveOpen" class="overlay" @tap="closeArchive">
      <view class="sheet archive-sheet" @tap.stop>
        <view class="sheet-head"><view><text>归档到工程资料</text><text>{{ archiveFile ? fileName(archiveFile) : '' }}</text></view><button @tap="closeArchive">×</button></view>
        <view class="mode-tabs"><button :class="{ active: archiveMode === 'NEW_DOCUMENT' }" @tap="archiveMode='NEW_DOCUMENT'">新建资料</button><button :class="{ active: archiveMode === 'NEW_VERSION' }" @tap="archiveMode='NEW_VERSION'">追加现有资料版本</button></view>
        <template v-if="archiveMode === 'NEW_DOCUMENT'">
          <label><text>资料名称 *</text><input v-model="archiveForm.title" maxlength="200" /></label>
          <label><text>资料编号</text><input v-model="archiveForm.documentNo" maxlength="100" placeholder="选填" /></label>
          <label><text>资料目录</text><picker :range="folderOptions" range-key="folderName" :value="folderIndex" @change="changeFolder"><view class="picker-value">{{ folderOptions[folderIndex]?.folderName }}</view></picker></label>
        </template>
        <template v-else>
          <label><text>选择现有资料 *</text></label>
          <view class="archive-document-search">
            <input v-model="archiveDocumentKeyword" maxlength="100" confirm-type="search" placeholder="按资料名称、编号或备注搜索" @confirm="searchArchiveDocuments" />
            <button :disabled="archiveDocumentsLoading || archiveDocumentsLoadingMore" @tap="searchArchiveDocuments">搜索</button>
          </view>
          <picker :disabled="archiveDocumentsLoading || !documents.length" :range="documents" range-key="title" :value="documentIndex" @change="changeDocument">
            <view class="picker-value">{{ archiveDocumentsLoading ? '正在查询工程资料…' : (selectedArchiveDocument?.title || '请选择可追加版本的使用中资料') }}</view>
          </picker>
          <view class="archive-document-meta">
            <text>{{ archiveDocumentsLoading ? '服务端查询中…' : `已加载 ${documents.length} / ${archiveDocumentTotal} 条` }}</text>
            <button v-if="hasMoreArchiveDocuments" :disabled="archiveDocumentsLoading || archiveDocumentsLoadingMore" @tap="loadMoreArchiveDocuments">{{ archiveDocumentsLoadingMore ? '加载中…' : '加载更多' }}</button>
          </view>
          <text v-if="!archiveDocumentsLoading && !documents.length" class="archive-document-empty">未找到可追加版本的使用中资料，请更换关键词</text>
        </template>
        <label><text>版本说明</text><input v-model="archiveForm.changeNote" maxlength="500" /></label>
        <button class="confirm" :disabled="busy || (archiveMode === 'NEW_VERSION' && archiveDocumentsLoading)" @tap="confirmArchive">确认归档</button>
      </view>
    </view>
  </view>
</template>

<style scoped>
.detail-page { min-height: 100vh; background: #f4f6f7; color: #223247; }.detail-scroll { height: calc(100vh - 92rpx); }.detail-content { display: flex; flex-direction: column; gap: 18rpx; padding: 22rpx 24rpx calc(38rpx + env(safe-area-inset-bottom)); }.state-card { display: flex; min-height: 260rpx; align-items: center; justify-content: center; flex-direction: column; padding: 30rpx; border-radius: 18rpx; background: #fff; color: #84919e; }.state-card.error { color: #b75353; }.state-card button { min-height: 58rpx; margin-top: 16rpx; padding: 0 22rpx; border-radius: 11rpx; background: #315f86; color: #fff; }
.status-card { display: flex; align-items: center; gap: 17rpx; padding: 22rpx; border-radius: 18rpx; background: #fff4e4; box-shadow: 0 8rpx 24rpx rgba(90,63,28,.06); }.status-card.approved { background: #eaf6f1; }.status-card.rejected,.status-card.withdrawn { background: #fceeed; }.seal-mark { display: flex; width: 62rpx; height: 62rpx; align-items: center; justify-content: center; border: 3rpx solid #946625; border-radius: 14rpx; color: #946625; font-size: 26rpx; font-weight: 900; }.status-card>view:nth-child(2) { min-width: 0; flex: 1; }.status-card>view:nth-child(2) text { display: block; }.status-card>view:nth-child(2) text:first-child { font-size: 27rpx; font-weight: 850; }.status-card>view:nth-child(2) text:last-child { margin-top: 5rpx; color: #7c8794; font-size: 19rpx; }.status-card>button { min-height: 54rpx; padding: 0 14rpx; border-radius: 11rpx; background: rgba(255,255,255,.8); color: #5d6d7d; font-size: 18rpx; font-weight: 700; }
.info-card,.opinion-card { padding: 20rpx 22rpx; border-radius: 18rpx; background: #fff; box-shadow: 0 8rpx 24rpx rgba(43,56,72,.045); }.section-title { color: #293b4e; font-size: 24rpx; font-weight: 820; }.row { display: flex; min-height: 68rpx; align-items: center; justify-content: space-between; gap: 22rpx; border-bottom: 1rpx solid #edf0f2; }.row text:first-child { flex-shrink: 0; color: #7b8996; font-size: 20rpx; }.row text:last-child { color: #34475a; font-size: 21rpx; font-weight: 650; text-align: right; }.purpose { padding-top: 16rpx; }.purpose text { display: block; }.purpose text:first-child { color: #7b8996; font-size: 20rpx; }.purpose text:last-child { margin-top: 7rpx; color: #3d4f61; font-size: 21rpx; line-height: 1.6; }
.item { display: flex; min-height: 68rpx; align-items: center; gap: 13rpx; border-bottom: 1rpx solid #edf0f2; }.item:last-child { border-bottom: 0; }.item text:first-child { display: flex; width: 32rpx; height: 32rpx; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 50%; background: #e7eef3; color: #42667e; font-size: 17rpx; }.item text:nth-child(2) { min-width: 0; flex: 1; color: #384a5d; font-size: 21rpx; }.item text:last-child { flex-shrink: 0; color: #7c8996; font-size: 20rpx; }.section-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 15rpx; }.section-head>view>text { display: block; }.section-head>view>text:last-child { margin-top: 5rpx; color: #8b98a5; font-size: 18rpx; }.section-head>button { min-height: 52rpx; padding: 0 14rpx; border-radius: 11rpx; background: #f4eada; color: #8d6127; font-size: 19rpx; font-weight: 750; }
.file-row,.result-file { display: flex; align-items: center; gap: 9rpx; margin-top: 12rpx; padding-top: 12rpx; border-top: 1rpx solid #edf0f2; }.file-main { display: flex; min-width: 0; min-height: 66rpx; align-items: center; justify-content: space-between; flex: 1; gap: 14rpx; text-align: left; }.file-main>view { min-width: 0; flex: 1; }.file-main text { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.file-main view text:first-child { color: #34475a; font-size: 21rpx; font-weight: 720; }.file-main view text:last-child { margin-top: 5rpx; color: #8b98a5; font-size: 18rpx; }.file-main>text { color: #41667f; font-size: 19rpx; }.delete { flex-shrink: 0; color: #aa5752; font-size: 18rpx; }.result-file { display: block; }.file-actions { display: flex; justify-content: flex-end; gap: 10rpx; padding-bottom: 8rpx; }.file-actions button { min-height: 48rpx; padding: 0 14rpx; border-radius: 10rpx; background: #e8f0f4; color: #41667f; font-size: 18rpx; }.file-actions button.delete { background: #fff0ef; color: #aa5752; }.empty-line { padding: 30rpx 8rpx 12rpx; color: #98a2ad; font-size: 20rpx; text-align: center; }
.opinion-card { border-left: 6rpx solid #8a612c; }.opinion-card text { display: block; }.opinion-card text:first-child { color: #8a612c; font-size: 21rpx; font-weight: 800; }.opinion-card text:nth-child(2) { margin-top: 10rpx; color: #3f5061; font-size: 22rpx; line-height: 1.55; }.opinion-card text:last-child { margin-top: 9rpx; color: #8a96a2; font-size: 18rpx; }.cc-list { display: flex; flex-wrap: wrap; gap: 9rpx; margin-top: 14rpx; }.cc-list text { padding: 7rpx 12rpx; border-radius: 999rpx; background: #e9f0f4; color: #456981; font-size: 19rpx; }
.log-row { display: flex; gap: 14rpx; padding-top: 16rpx; }.log-row>view:first-child { width: 12rpx; height: 12rpx; flex-shrink: 0; margin-top: 8rpx; border-radius: 50%; background: #8aa4b6; }.log-row>view:last-child { min-width: 0; flex: 1; padding-bottom: 14rpx; border-bottom: 1rpx solid #edf0f2; }.log-row text { display: block; color: #83909c; font-size: 18rpx; line-height: 1.45; }.log-row text:first-child { color: #394b5d; font-size: 21rpx; font-weight: 730; }.log-row text:nth-child(2) { margin-top: 5rpx; color: #5f7182; font-size: 19rpx; }
.action-row { display: flex; flex-wrap: wrap; gap: 11rpx; }.action-row button { min-width: 180rpx; min-height: 70rpx; flex: 1; padding: 0 17rpx; border: 1rpx solid #cbd7df; border-radius: 13rpx; background: #fff; color: #52697b; font-size: 21rpx; font-weight: 750; }.action-row button.primary { border-color: #8a612c; background: #8a612c; color: #fff; }.action-row button.approve { border-color: #2f8065; background: #2f8065; color: #fff; }.action-row button.reject { border-color: #e4bcbc; color: #ad514c; }
.overlay { position: fixed; z-index: 90; inset: 0; display: flex; align-items: flex-end; background: rgba(23,35,48,.43); }.sheet { width: 100%; padding: 18rpx 24rpx calc(24rpx + env(safe-area-inset-bottom)); border-radius: 24rpx 24rpx 0 0; background: #fff; }.sheet-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 18rpx; }.sheet-head view text { display: block; }.sheet-head view text:first-child { font-size: 27rpx; font-weight: 820; }.sheet-head view text:last-child { margin-top: 5rpx; color: #8b98a5; font-size: 19rpx; }.sheet-head>button { width: 50rpx; height: 50rpx; border-radius: 50%; background: #eef2f5; color: #687889; font-size: 27rpx; }.sheet textarea { box-sizing: border-box; width: 100%; min-height: 180rpx; margin-top: 20rpx; padding: 16rpx; border: 1rpx solid #dce4e9; border-radius: 13rpx; background: #f8fafb; font-size: 22rpx; }.confirm { width: 100%; min-height: 76rpx; margin-top: 18rpx; border-radius: 13rpx; background: #2f8065; color: #fff; font-size: 23rpx; font-weight: 780; }.confirm.danger { background: #b75353; }.archive-sheet label { display: block; margin-top: 16rpx; }.archive-sheet label>text { display: block; margin-bottom: 8rpx; color: #617284; font-size: 20rpx; font-weight: 700; }.archive-sheet input,.picker-value { box-sizing: border-box; width: 100%; min-height: 70rpx; padding: 15rpx 16rpx; border: 1rpx solid #dfe6eb; border-radius: 12rpx; background: #f8fafb; font-size: 21rpx; }.mode-tabs { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 9rpx; margin-top: 18rpx; padding: 7rpx; border-radius: 14rpx; background: #e8edf1; }.mode-tabs button { min-height: 56rpx; border-radius: 10rpx; color: #6b7a89; font-size: 20rpx; }.mode-tabs button.active { background: #fff; color: #315f86; font-weight: 750; }
.archive-document-search { display: flex; gap: 10rpx; }.archive-document-search input { min-width: 0; flex: 1; }.archive-document-search button { width: 112rpx; flex-shrink: 0; border-radius: 12rpx; background: #e8f0f4; color: #315f86; font-size: 20rpx; }.archive-document-meta { display: flex; min-height: 54rpx; align-items: center; justify-content: space-between; gap: 12rpx; }.archive-document-meta text { color: #8a97a4; font-size: 18rpx; }.archive-document-meta button { min-height: 44rpx; padding: 0 12rpx; border-radius: 10rpx; background: #edf2f5; color: #41667f; font-size: 18rpx; }.archive-document-empty { display: block; padding: 12rpx 0 2rpx; color: #ad514c; font-size: 18rpx; }.archive-sheet { max-height: 88vh; overflow-y: auto; }
</style>
