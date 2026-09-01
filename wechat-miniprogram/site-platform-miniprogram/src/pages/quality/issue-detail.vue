<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import WorkspaceStatusPill from '@/components/workspace/WorkspaceStatusPill.vue';
import {
  assignQualityIssue,
  getQualityAssignees,
  getQualityIssue,
  reviewQualityIssue,
  submitQualityRectification,
  voidQualityIssue
} from '@/api/quality';
import { deleteFileResources, downloadFileResults, uploadPhotoIds } from '@/api/file';
import { useAuthStore } from '@/stores/auth';
import { useProjectStore } from '@/stores/project';
import type { QualityAssignee, QualityIssue } from '@/types';
import { getQueryNumber, showToast, switchTab } from '@/utils/navigation';

interface EvidenceGroup {
  key: string;
  title: string;
  operator?: string;
  time?: string;
  fileIds: number[];
  paths: string[];
  failedCount: number;
}

type DialogMode = 'assign' | 'void' | null;

const authStore = useAuthStore();
const projectStore = useProjectStore();
const issueId = ref(0);
const issue = ref<QualityIssue>();
const loading = ref(true);
const errorMessage = ref('');
const submitting = ref(false);
const dialogMode = ref<DialogMode>(null);
const assignees = ref<QualityAssignee[]>([]);
const rectificationText = ref('');
const reviewComment = ref('');
const voidComment = ref('');
const rectificationPhotoPaths = ref<string[]>([]);
const reviewPhotoPaths = ref<string[]>([]);
const evidenceGroups = ref<EvidenceGroup[]>([]);
const comparisonGroups = ref<EvidenceGroup[]>([]);
const evidenceLoading = ref(false);
const assignForm = reactive({ assigneeIndex: -1, deadline: '', comment: '' });

const canManage = computed(() => Boolean(issue.value)
  && authStore.hasProjectPermission(issue.value!.projectId, 'quality.manage'));
const canRectify = computed(() => Boolean(issue.value?.canRectify)
  && authStore.hasProjectPermission(issue.value!.projectId, 'quality.rectify'));
const canReview = computed(() => Boolean(issue.value?.canReview)
  && authStore.hasProjectPermission(issue.value!.projectId, 'quality.review'));
const evidenceFailureCount = computed(() => evidenceGroups.value.reduce((total, group) => total + group.failedCount, 0));

onLoad(async (query) => {
  issueId.value = getQueryNumber(query?.id, 0);
  if (!issueId.value) {
    errorMessage.value = '缺少质量问题编号';
    loading.value = false;
    return;
  }
  if (!await authStore.ensureRootAccess('/pages/quality/index')) return;
  await loadIssue();
});

async function loadIssue() {
  loading.value = true;
  errorMessage.value = '';
  try {
    const value = await getQualityIssue(issueId.value);
    if (!await authStore.ensureProjectPermission('/pages/quality/index', value.projectId, 'quality.view')) return;
    if (projectStore.state.currentProjectId !== value.projectId) projectStore.setCurrentProject(value.projectId);
    issue.value = value;
    await loadEvidence();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '质量问题详情加载失败';
  } finally {
    loading.value = false;
  }
}

function parsePhotoIds(value?: string): number[] {
  return String(value || '').split(',').map((id) => Number(id.trim())).filter((id) => Number.isFinite(id) && id > 0);
}

function evidenceTitle(actionType: string) {
  if (actionType === 'CREATE') return '问题现场';
  if (actionType === 'RECTIFY') return '整改反馈';
  if (actionType === 'REVIEW_PASS') return '复查通过';
  if (actionType === 'REVIEW_REJECT') return '复查退回';
  return actionLabel(actionType);
}

function buildEvidenceGroups(value: QualityIssue): EvidenceGroup[] {
  const fromLogs = (value.logs || []).filter((log) => parsePhotoIds(log.photoFileIds).length > 0).map((log) => ({
    key: `log-${log.id}`,
    title: evidenceTitle(log.actionType),
    operator: log.operatorName,
    time: log.createTime,
    fileIds: parsePhotoIds(log.photoFileIds),
    paths: [],
    failedCount: 0
  }));
  if (fromLogs.length) return fromLogs;
  return [
    { key: 'issue', title: '问题现场', fileIds: value.issuePhotoFileIds || [], paths: [], failedCount: 0 },
    { key: 'rectification', title: '整改反馈', fileIds: value.rectificationPhotoFileIds || [], paths: [], failedCount: 0 },
    { key: 'review', title: '复查记录', fileIds: value.reviewPhotoFileIds || [], paths: [], failedCount: 0 }
  ].filter((group) => group.fileIds.length > 0);
}

async function loadEvidence() {
  if (!issue.value) return;
  const currentId = issue.value.id;
  const groups = buildEvidenceGroups(issue.value);
  const comparisons: EvidenceGroup[] = [
    { key: 'comparison-original', title: '整改前（原始问题照片）', fileIds: issue.value.originalProblemPhotoFileIds || issue.value.issuePhotoFileIds || [], paths: [], failedCount: 0 },
    { key: 'comparison-latest', title: '最新一轮整改后', fileIds: issue.value.latestRectificationPhotoFileIds || [], paths: [], failedCount: 0 }
  ];
  evidenceGroups.value = groups;
  comparisonGroups.value = comparisons;
  evidenceLoading.value = true;
  try {
    await Promise.all([...groups, ...comparisons].map(async (group) => {
      const results = await downloadFileResults(group.fileIds);
      if (issue.value?.id !== currentId) return;
      group.paths = results.flatMap((result) => result.path ? [result.path] : []);
      group.failedCount = results.filter((result) => !result.path).length;
    }));
    if (issue.value?.id === currentId) {
      evidenceGroups.value = [...groups];
      comparisonGroups.value = [...comparisons];
    }
  } finally {
    if (issue.value?.id === currentId) evidenceLoading.value = false;
  }
}

async function openAssign() {
  if (!issue.value || !canManage.value || ['CLOSED', 'VOIDED'].includes(issue.value.status)) return;
  try {
    assignees.value = await getQualityAssignees(issue.value.projectId);
  } catch (error) {
    showToast(error instanceof Error ? error.message : '整改负责人加载失败');
    return;
  }
  Object.assign(assignForm, {
    assigneeIndex: assignees.value.findIndex((item) => item.userId === issue.value?.assigneeId),
    deadline: issue.value.deadline || '',
    comment: ''
  });
  dialogMode.value = 'assign';
}

function openVoid() {
  if (!issue.value || !canManage.value || ['CLOSED', 'VOIDED'].includes(issue.value.status)) return;
  voidComment.value = '';
  dialogMode.value = 'void';
}

async function submitAssign() {
  if (submitting.value || !issue.value) return;
  const assignee = assignees.value[assignForm.assigneeIndex];
  if (!assignee) { showToast('请选择整改负责人'); return; }
  if (!assignForm.deadline) { showToast('请选择闭环期限'); return; }
  submitting.value = true;
  try {
    issue.value = await assignQualityIssue(issue.value.id, { assigneeId: assignee.userId, deadline: assignForm.deadline, comment: assignForm.comment.trim() });
    dialogMode.value = null;
    showToast('整改负责人和期限已更新');
    await loadEvidence();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '调整失败');
  } finally {
    submitting.value = false;
  }
}

async function submitVoid() {
  if (submitting.value || !issue.value) return;
  if (!voidComment.value.trim()) { showToast('请填写作废原因'); return; }
  if (!await confirmAction('确认作废这条质量问题？', '作废后不能继续整改、复查或改派，原因会写入操作留痕。')) return;
  submitting.value = true;
  try {
    issue.value = await voidQualityIssue(issue.value.id, voidComment.value.trim());
    dialogMode.value = null;
    showToast('质量问题已作废');
    await loadEvidence();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '作废失败');
  } finally {
    submitting.value = false;
  }
}

async function submitRectification() {
  if (submitting.value || !issue.value) return;
  if (!rectificationText.value.trim()) { showToast('请填写整改说明'); return; }
  if (!rectificationPhotoPaths.value.length) { showToast('请上传至少一张整改照片'); return; }
  submitting.value = true;
  let photoFileIds: number[] = [];
  try {
    photoFileIds = await uploadPhotoIds(rectificationPhotoPaths.value, '质量整改照片', { projectId: issue.value.projectId, businessType: 'QUALITY_RECTIFICATION_PENDING' });
    issue.value = await submitQualityRectification(issue.value.id, rectificationText.value.trim(), photoFileIds);
    rectificationText.value = '';
    rectificationPhotoPaths.value = [];
    showToast('整改已提交复查');
    await loadEvidence();
  } catch (error) {
    await deleteFileResources(photoFileIds);
    showToast(error instanceof Error ? error.message : '整改提交失败');
  } finally {
    submitting.value = false;
  }
}

async function submitReview(passed: boolean) {
  if (submitting.value || !issue.value) return;
  if (!passed && !reviewComment.value.trim()) { showToast('退回时请填写复查意见'); return; }
  if (evidenceLoading.value) { showToast('整改证据仍在加载，请稍候'); return; }
  if (evidenceFailureCount.value > 0) { showToast(`有 ${evidenceFailureCount.value} 个证据附件加载失败，请重新加载后再复查`); return; }
  if (passed && !await confirmAction('确认复查通过并关闭该问题？', '关闭后不能继续整改或改派。')) return;
  submitting.value = true;
  let photoFileIds: number[] = [];
  try {
    photoFileIds = reviewPhotoPaths.value.length
      ? await uploadPhotoIds(reviewPhotoPaths.value, '质量复查照片', { projectId: issue.value.projectId, businessType: 'QUALITY_REVIEW_PENDING' })
      : [];
    issue.value = await reviewQualityIssue(issue.value.id, passed, reviewComment.value.trim(), photoFileIds);
    reviewComment.value = '';
    reviewPhotoPaths.value = [];
    showToast(passed ? '质量问题已关闭' : '已退回继续整改');
    await loadEvidence();
  } catch (error) {
    await deleteFileResources(photoFileIds);
    showToast(error instanceof Error ? error.message : '复查失败');
  } finally {
    submitting.value = false;
  }
}

function choosePhotos(target: 'rectification' | 'review') {
  if (submitting.value) return;
  uni.chooseImage({ count: 6, sizeType: ['compressed'], sourceType: ['camera', 'album'], success: (result) => {
    const paths = result.tempFilePaths || [];
    if (target === 'rectification') rectificationPhotoPaths.value = [...rectificationPhotoPaths.value, ...paths].slice(0, 6);
    else reviewPhotoPaths.value = [...reviewPhotoPaths.value, ...paths].slice(0, 6);
  } });
}

function removePhoto(target: 'rectification' | 'review', index: number) {
  if (submitting.value) return;
  if (target === 'rectification') rectificationPhotoPaths.value = rectificationPhotoPaths.value.filter((_, current) => current !== index);
  else reviewPhotoPaths.value = reviewPhotoPaths.value.filter((_, current) => current !== index);
}

function preview(paths: string[], current: string) {
  if (paths.length) uni.previewImage({ urls: paths, current });
}

function confirmAction(title: string, content: string): Promise<boolean> {
  return new Promise((resolve) => uni.showModal({ title, content, confirmText: '确认', cancelText: '取消', success: (result) => resolve(Boolean(result.confirm)), fail: () => resolve(false) }));
}

function setAssignAssignee(event: unknown) {
  assignForm.assigneeIndex = Number((event as { detail?: { value?: number | string } }).detail?.value || 0);
}

function setAssignDeadline(event: unknown) {
  assignForm.deadline = String((event as { detail?: { value?: string } }).detail?.value || '');
}

function statusLabel(value: QualityIssue) {
  if (value.status === 'VOIDED') return '已作废';
  if (value.overdue) return '已逾期';
  if (value.status === 'PENDING') return '待整改';
  if (value.status === 'RECHECK') return '待复查';
  return '已关闭';
}

function statusTone(value: QualityIssue) {
  if (value.status === 'VOIDED') return 'gray' as const;
  if (value.overdue) return 'red' as const;
  if (value.status === 'PENDING') return 'amber' as const;
  if (value.status === 'RECHECK') return 'blue' as const;
  return 'green' as const;
}

function actionLabel(value: string) {
  return ({ CREATE: '发起检查', RECTIFY: '提交整改', REVIEW_PASS: '复查通过', REVIEW_REJECT: '复查退回', ASSIGN: '改派/调整期限', VOID: '问题作废' } as Record<string, string>)[value] || value;
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
  <view class="detail-page">
    <AppNavBar title="质量问题详情" @back="goBack" />
    <view v-if="loading" class="state-card">正在加载质量问题详情...</view>
    <view v-else-if="errorMessage" class="state-card error"><text>{{ errorMessage }}</text><button @tap="loadIssue">重新加载</button></view>
    <scroll-view v-else-if="issue" class="detail-scroll" scroll-y>
      <view class="detail-content">
        <view class="hero-card">
          <view><WorkspaceStatusPill :label="statusLabel(issue)" :tone="statusTone(issue)" /><text>{{ issue.issueNo }}</text></view>
          <text class="hero-title">{{ issue.title }}</text>
          <text class="hero-meta">{{ issue.location || '未设置位置' }} · {{ issue.assigneeName || '未指定负责人' }}</text>
          <text class="hero-due">{{ issue.dueText }}</text>
        </view>

        <view class="info-card">
          <view><text>严重程度</text><text>{{ issue.severity === 'DANGER' ? '严重' : issue.severity === 'WARNING' ? '重要' : '一般' }}</text></view>
          <view><text>整改负责人</text><text>{{ issue.assigneeName || '未指定' }}</text></view>
          <view><text>闭环期限</text><text>{{ issue.deadline || '未设置' }}</text></view>
          <view class="description"><text>问题描述</text><text>{{ issue.description || '无' }}</text></view>
        </view>

        <view v-if="canManage && !['CLOSED', 'VOIDED'].includes(issue.status)" class="management-card">
          <button @tap="openAssign">改派 / 调整期限</button><button class="danger" @tap="openVoid">作废问题</button>
        </view>

        <view class="section-card">
          <view class="section-head"><text>整改前后照片对比</text><text>最新一轮</text></view>
          <view class="comparison-grid">
            <view v-for="group in comparisonGroups" :key="group.key" class="comparison-column" :class="{ after: group.key === 'comparison-latest' }">
              <view><text>{{ group.title }}</text><text>{{ group.fileIds.length }} 张</text></view>
              <view v-if="group.paths.length" class="photo-grid"><image v-for="path in group.paths" :key="path" :src="path" mode="aspectFill" @tap="preview(group.paths, path)" /></view>
              <text v-else class="photo-empty">{{ group.key === 'comparison-latest' ? '尚未提交整改照片' : '暂无原始问题照片' }}</text>
              <text v-if="group.failedCount" class="photo-error">{{ group.failedCount }} 张照片加载失败</text>
            </view>
          </view>
        </view>

        <view v-if="canRectify" class="action-card">
          <text class="action-title">提交整改</text>
          <textarea v-model="rectificationText" maxlength="1000" :disabled="submitting" placeholder="填写整改措施和结果" />
          <button class="photo-button" :disabled="submitting" @tap="choosePhotos('rectification')">+ 整改照片 *</button>
          <view class="local-grid"><view v-for="(path, index) in rectificationPhotoPaths" :key="path"><image :src="path" mode="aspectFill" @tap="preview(rectificationPhotoPaths, path)" /><button @tap.stop="removePhoto('rectification', index)">×</button></view></view>
          <button class="primary-button" :disabled="submitting" @tap="submitRectification">提交复查</button>
        </view>

        <view v-if="canReview" class="action-card">
          <text class="action-title">复查处理</text>
          <textarea v-model="reviewComment" maxlength="1000" :disabled="submitting" placeholder="退回时必须填写复查意见" />
          <button class="photo-button" :disabled="submitting" @tap="choosePhotos('review')">+ 复查照片（可选）</button>
          <view class="local-grid"><view v-for="(path, index) in reviewPhotoPaths" :key="path"><image :src="path" mode="aspectFill" @tap="preview(reviewPhotoPaths, path)" /><button @tap.stop="removePhoto('review', index)">×</button></view></view>
          <view class="review-actions"><button :disabled="submitting" @tap="submitReview(false)">退回整改</button><button :disabled="submitting || evidenceLoading || evidenceFailureCount > 0" @tap="submitReview(true)">复查通过</button></view>
        </view>

        <view v-if="issue.rectificationDescription" class="result-card"><text>最近整改说明</text><text>{{ issue.rectificationDescription }}</text></view>

        <view class="section-card evidence-card">
          <view class="section-head"><text>完整证据时间线</text><button v-if="evidenceFailureCount" @tap="loadEvidence">重新加载</button></view>
          <text v-if="evidenceLoading" class="evidence-loading">正在加载证据附件</text>
          <view v-for="group in evidenceGroups" :key="group.key" class="evidence-group">
            <view><text>{{ group.title }}</text><text>{{ group.operator || '-' }} · {{ formatTime(group.time) }}</text></view>
            <view v-if="group.paths.length" class="photo-grid"><image v-for="path in group.paths" :key="path" :src="path" mode="aspectFill" @tap="preview(group.paths, path)" /></view>
            <text v-if="group.failedCount" class="photo-error">{{ group.failedCount }} 个附件加载失败，复查操作已暂停</text>
          </view>
          <text v-if="!evidenceLoading && !evidenceGroups.length" class="photo-empty">暂无证据附件</text>
        </view>

        <view class="section-card timeline-card">
          <view class="section-head"><text>操作留痕</text><text>{{ issue.logs?.length || 0 }} 条</text></view>
          <view v-for="log in issue.logs || []" :key="log.id" class="timeline-row"><text>{{ actionLabel(log.actionType) }}</text><text>{{ log.operatorName || '-' }} · {{ formatTime(log.createTime) }}</text><text>{{ log.comment || '无补充说明' }}</text></view>
          <text v-if="!issue.logs?.length" class="photo-empty">暂无操作留痕</text>
        </view>
      </view>
    </scroll-view>

    <view v-if="dialogMode" class="form-overlay" @tap="!submitting && (dialogMode = null)">
      <view class="form-sheet" @tap.stop>
        <view class="sheet-handle"></view><view class="form-head"><text class="form-title">{{ dialogMode === 'assign' ? '改派与调整期限' : '作废质量问题' }}</text><button class="form-close" :disabled="submitting" @tap="dialogMode = null">×</button></view>
        <template v-if="dialogMode === 'assign'">
          <view class="form-field"><text class="form-label">整改负责人 *</text><picker :range="assignees" range-key="displayName" :value="Math.max(assignForm.assigneeIndex, 0)" @change="setAssignAssignee"><view class="form-picker">{{ assignees[assignForm.assigneeIndex]?.displayName || '请选择整改负责人' }}</view></picker></view>
          <view class="form-field"><text class="form-label">闭环期限 *</text><picker mode="date" :value="assignForm.deadline" @change="setAssignDeadline"><view class="form-picker">{{ assignForm.deadline || '请选择闭环期限' }}</view></picker></view>
          <view class="form-field"><text class="form-label">调整说明</text><textarea v-model="assignForm.comment" class="form-textarea" maxlength="1000" :disabled="submitting" placeholder="填写改派或调整期限原因（可选）" /></view>
          <button class="dialog-submit" :disabled="submitting" @tap="submitAssign">确认调整</button>
        </template>
        <template v-else>
          <view class="form-field"><text class="form-label">作废原因 *</text><textarea v-model="voidComment" class="form-textarea" maxlength="1000" :disabled="submitting" placeholder="说明误建、重复或不属于质量问题等原因" /></view>
          <button class="dialog-submit danger" :disabled="submitting" @tap="submitVoid">确认作废</button>
        </template>
      </view>
    </view>
  </view>
</template>

<style scoped src="../../styles/workspace-page.css"></style>
<style scoped>
.detail-page { min-height: 100vh; background: #f4f6f7; color: var(--workspace-text); }
.detail-page :deep(.app-nav) { background: rgba(255,255,255,.98); box-shadow: 0 1rpx 0 rgba(148,163,184,.18); }
.detail-scroll { height: calc(100vh - 92px); }
.detail-content { display: flex; flex-direction: column; gap: 17rpx; padding: 20rpx 24rpx calc(36rpx + env(safe-area-inset-bottom)); }
.state-card { margin: 26rpx; padding: 70rpx 24rpx; border-radius: 16rpx; background: #fff; color: #718092; font-size: 22rpx; text-align: center; }
.state-card text { display: block; }.state-card button { margin-top: 18rpx; border: 0; background: #e8f1f6; color: #315f86; font-size: 21rpx; }.state-card button::after { border: 0; }
.hero-card,.info-card,.management-card,.section-card,.action-card,.result-card { padding: 21rpx; border-radius: 16rpx; background: #fff; box-shadow: 0 8rpx 26rpx rgba(43,56,72,.055); }
.hero-card { background: linear-gradient(145deg,#315f86,#456f8f); color: #fff; }
.hero-card > view { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; color: rgba(255,255,255,.76); font-size: 19rpx; }
.hero-title { display: block; margin-top: 18rpx; font-size: 29rpx; font-weight: 800; line-height: 1.38; }
.hero-meta,.hero-due { display: block; margin-top: 8rpx; color: rgba(255,255,255,.76); font-size: 20rpx; }.hero-due { color: #fff0ca; }
.info-card > view { display: flex; min-height: 60rpx; align-items: center; justify-content: space-between; gap: 18rpx; border-bottom: 1rpx solid #edf0f3; color: #7e8c9b; font-size: 20rpx; }
.info-card > view:last-child { border-bottom: 0; }.info-card > view text:last-child { color: #304256; text-align: right; }
.info-card > view.description { align-items: flex-start; flex-direction: column; gap: 7rpx; padding: 14rpx 0 3rpx; }.info-card > view.description text:last-child { line-height: 1.55; text-align: left; }
.management-card { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 12rpx; padding: 13rpx; }
.management-card button { min-height: 64rpx; margin: 0; border: 0; border-radius: 11rpx; background: #eaf2f6; color: #315f86; font-size: 20rpx; font-weight: 750; }.management-card button.danger { background: #fff1f1; color: #b94f4f; }.management-card button::after { border: 0; }
.section-head { min-height: 0; padding: 0 0 16rpx; border-bottom: 1rpx solid #edf0f3; background: transparent; }.section-head > text:first-child { color: #2e4256; font-size: 24rpx; font-weight: 800; }.section-head > text:last-child,.section-head button { color: #8794a2; font-size: 18rpx; }.section-head button { margin: 0; border: 0; background: transparent; color: #315f86; }.section-head button::after { border: 0; }
.comparison-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 12rpx; margin-top: 16rpx; }
.comparison-column { min-width: 0; padding: 13rpx; border-radius: 12rpx; background: #f7f9fa; }.comparison-column.after { background: #f0f7f4; }
.comparison-column > view:first-child,.evidence-group > view:first-child { display: flex; align-items: center; justify-content: space-between; gap: 10rpx; color: #667789; font-size: 17rpx; }.comparison-column > view:first-child text:first-child,.evidence-group > view:first-child text:first-child { color: #334a5f; font-weight: 750; }
.photo-grid,.local-grid { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 9rpx; margin-top: 11rpx; }.photo-grid image,.local-grid image { width: 100%; height: 112rpx; border-radius: 9rpx; background: #edf1f4; }
.comparison-column .photo-grid { grid-template-columns: repeat(2,minmax(0,1fr)); }.comparison-column .photo-grid image { height: 104rpx; }
.photo-empty,.evidence-loading { display: block; padding: 31rpx 0; color: #8a989f; font-size: 19rpx; text-align: center; }.photo-error { display: block; margin-top: 8rpx; color: #b55252; font-size: 18rpx; }
.action-title { display: block; margin-bottom: 13rpx; color: #2e4256; font-size: 24rpx; font-weight: 800; }.action-card textarea { box-sizing: border-box; width: 100%; min-height: 128rpx; padding: 15rpx; border: 1rpx solid #e1e7eb; border-radius: 11rpx; background: #f7f9fa; font-size: 21rpx; }
.photo-button { width: 100%; min-height: 60rpx; margin: 12rpx 0 0; border: 1rpx dashed #a9bdca; border-radius: 11rpx; background: #f5f9fb; color: #315f86; font-size: 20rpx; }.photo-button::after,.primary-button::after,.review-actions button::after,.local-grid button::after,.dialog-submit::after { border: 0; }
.local-grid > view { position: relative; }.local-grid button { position: absolute; top: -7rpx; right: -5rpx; display: flex; width: 32rpx; height: 32rpx; align-items: center; justify-content: center; margin: 0; padding: 0; border-radius: 50%; background: rgba(36,49,64,.76); color: #fff; font-size: 22rpx; }
.primary-button,.dialog-submit { width: 100%; min-height: 68rpx; margin: 17rpx 0 0; border: 0; border-radius: 11rpx; background: #315f86; color: #fff; font-size: 22rpx; font-weight: 780; }.dialog-submit.danger { background: #b94f4f; }
.review-actions { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 12rpx; margin-top: 17rpx; }.review-actions button { min-height: 68rpx; margin: 0; border: 0; border-radius: 11rpx; background: #fff1f1; color: #b94f4f; font-size: 21rpx; font-weight: 760; }.review-actions button:last-child { background: #315f86; color: #fff; }
.result-card text { display: block; color: #56697a; font-size: 20rpx; line-height: 1.5; }.result-card text:first-child { margin-bottom: 8rpx; color: #2e4256; font-size: 22rpx; font-weight: 780; }
.evidence-group { padding: 16rpx 0; border-bottom: 1rpx solid #edf0f3; }.evidence-group:last-child { border-bottom: 0; }
.timeline-row { padding: 14rpx 0; border-bottom: 1rpx solid #edf0f3; }.timeline-row text { display: block; color: #7e8c9b; font-size: 19rpx; line-height: 1.45; }.timeline-row text:first-child { color: #315f86; font-weight: 760; }.timeline-row text + text { margin-top: 4rpx; }
@media (max-width: 360px) { .detail-content { padding-right: 18rpx; padding-left: 18rpx; } .comparison-grid { grid-template-columns: 1fr; } .photo-grid { grid-template-columns: repeat(3,minmax(0,1fr)); } }
</style>
