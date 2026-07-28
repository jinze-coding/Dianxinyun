<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import AppTabBar from '@/components/AppTabBar.vue';
import WorkspaceAreaSwitcher from '@/components/workspace/WorkspaceAreaSwitcher.vue';
import WorkspaceMetricStrip, { type WorkspaceMetric } from '@/components/workspace/WorkspaceMetricStrip.vue';
import WorkspaceSegmentControl from '@/components/workspace/WorkspaceSegmentControl.vue';
import WorkspaceStatusPill from '@/components/workspace/WorkspaceStatusPill.vue';
import { WORKSPACE_THEME } from '@/constants/workspaceTheme';
import { createQualityIssue, getQualityIssue, getQualityIssues, getQualitySummary, reviewQualityIssue, submitQualityRectification } from '@/api/quality';
import { deleteFileResources, downloadFilePaths, downloadFileToTempPath, getFileResources, uploadPhotoIds, type FileResourceItem } from '@/api/file';
import { getProjectMembers } from '@/api/projectMember';
import { useProjectStore } from '@/stores/project';
import { useAuthStore } from '@/stores/auth';
import type { ProjectMember, QualityIssue, QualityIssueStatus, QualitySummary } from '@/types';
import { usePageScrollHeight } from '@/utils/navLayout';
import { showToast } from '@/utils/navigation';

type QualityFilter = 'ALL' | QualityIssueStatus;
type SheetMode = 'create' | 'detail' | 'documents' | null;

const ACCENT = WORKSPACE_THEME.accent;
const TINT = WORKSPACE_THEME.tint;
const projectStore = useProjectStore();
const authStore = useAuthStore();
const summary = ref<QualitySummary | null>(null);
const issues = ref<QualityIssue[]>([]);
const members = ref<ProjectMember[]>([]);
const selectedIssue = ref<QualityIssue | null>(null);
const activeFilter = ref<QualityFilter>('ALL');
const keyword = ref('');
const loading = ref(false);
const submitting = ref(false);
const errorMessage = ref('');
const sheetMode = ref<SheetMode>(null);
const rectificationText = ref('');
const reviewComment = ref('');
const qualityDocuments = ref<FileResourceItem[]>([]);
const createPhotoPaths = ref<string[]>([]);
const rectificationPhotoPaths = ref<string[]>([]);
const reviewPhotoPaths = ref<string[]>([]);
const detailPhotoPaths = ref<string[]>([]);
const { scrollStyle } = usePageScrollHeight({ bottomRpx: 124, minHeight: 320 });

const createForm = reactive({ title: '', location: '', description: '', severity: 'NORMAL' as 'NORMAL' | 'WARNING' | 'DANGER', assigneeIndex: 0, deadline: '' });
const projects = computed(() => projectStore.state.projects);
const currentProject = computed(() => projects.value.find((item) => item.id === projectStore.state.currentProjectId));
const canManage = computed(() => Boolean(summary.value?.canManage) && Boolean(currentProject.value)
  && authStore.hasProjectPermission(currentProject.value!.id, 'quality.manage'));
const canRectify = computed(() => Boolean(selectedIssue.value)
  && authStore.hasProjectPermission(selectedIssue.value!.projectId, 'quality.rectify'));
const canReview = computed(() => Boolean(selectedIssue.value)
  && authStore.hasProjectPermission(selectedIssue.value!.projectId, 'quality.review'));
const filteredIssues = computed(() => {
  const text = keyword.value.trim().toLowerCase();
  if (!text) return issues.value;
  return issues.value.filter((issue) => `${issue.title}${issue.location || ''}${issue.assigneeName || ''}`.toLowerCase().includes(text));
});
const metrics = computed<WorkspaceMetric[]>(() => [
  { label: '今日检查', value: summary.value?.todayCheckCount || 0 },
  { label: '待整改', value: summary.value?.pendingCount || 0, tone: 'amber' },
  { label: '已逾期', value: summary.value?.overdueCount || 0, tone: 'red' },
  { label: '闭环率', value: `${summary.value?.closureRate || 0}%`, tone: 'green' }
]);
const filters = computed(() => [
  { value: 'ALL', label: '全部' },
  { value: 'PENDING', label: '待整改', badge: summary.value?.pendingCount || 0 },
  { value: 'RECHECK', label: '待复查', badge: summary.value?.recheckCount || 0 },
  { value: 'CLOSED', label: '已关闭', badge: summary.value?.closedCount || 0 }
]);

function hideNativeTabBar() { uni.hideTabBar({ animation: false, fail: () => undefined }); }
onShow(async () => {
  hideNativeTabBar();
  if (!await authStore.ensureRootAccess('/pages/quality/index')) return;
  await refresh();
});

async function refresh() {
  loading.value = true;
  errorMessage.value = '';
  try {
    await projectStore.loadProjects();
    if (!projectStore.state.currentProjectId) { summary.value = null; issues.value = []; return; }
    const projectId = projectStore.state.currentProjectId;
    if (!authStore.hasProjectPermission(projectId, 'quality.view')) {
      summary.value = null; issues.value = []; qualityDocuments.value = [];
      errorMessage.value = '当前项目无质量管理查看权限，可切换到其他施工区域';
      return;
    }
    const [summaryResult, issueResult, documentResult] = await Promise.all([getQualitySummary(projectId), getQualityIssues(projectId, activeFilter.value), getFileResources(projectId, 'QUALITY_DOCUMENT')]);
    summary.value = summaryResult;
    issues.value = issueResult;
    qualityDocuments.value = documentResult;
  } catch (error) {
    summary.value = null;
    issues.value = [];
    errorMessage.value = error instanceof Error ? error.message : '质量数据加载失败';
  } finally { loading.value = false; }
}

async function selectProject(projectId: number) {
  projectStore.setCurrentProject(projectId);
  activeFilter.value = 'ALL'; keyword.value = '';
  await refresh();
}

async function setFilter(value: string) { activeFilter.value = value as QualityFilter; await refresh(); }

function statusLabel(issue: QualityIssue) { return issue.overdue ? '已逾期' : issue.status === 'PENDING' ? '待整改' : issue.status === 'RECHECK' ? '待复查' : '已关闭'; }
function statusTone(issue: QualityIssue) { return issue.overdue ? 'red' as const : issue.status === 'PENDING' ? 'amber' as const : issue.status === 'RECHECK' ? 'blue' as const : 'green' as const; }

async function openCreate() {
  if (!canManage.value || !currentProject.value) { showToast('当前账号无质量问题发起权限'); return; }
  try { members.value = await getProjectMembers(currentProject.value.id); }
  catch { members.value = []; }
  const date = new Date(Date.now() + 3 * 86400000);
  Object.assign(createForm, { title: '', location: '', description: '', severity: 'NORMAL', assigneeIndex: 0, deadline: `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}` });
  createPhotoPaths.value = [];
  sheetMode.value = 'create';
}

async function openDetail(issue: QualityIssue) {
  try {
    selectedIssue.value = await getQualityIssue(issue.id);
    rectificationText.value = ''; reviewComment.value = ''; rectificationPhotoPaths.value = []; reviewPhotoPaths.value = [];
    detailPhotoPaths.value = await downloadFilePaths([...(selectedIssue.value.issuePhotoFileIds || []), ...(selectedIssue.value.rectificationPhotoFileIds || []), ...(selectedIssue.value.reviewPhotoFileIds || [])]);
    sheetMode.value = 'detail';
  }
  catch (error) { showToast(error instanceof Error ? error.message : '详情加载失败'); }
}

async function submitCreate() {
  if (!currentProject.value || !createForm.title.trim()) { showToast('请填写质量问题标题'); return; }
  if (!createPhotoPaths.value.length) { showToast('请上传至少一张问题照片'); return; }
  submitting.value = true;
  let photoFileIds: number[] = [];
  try {
    const assignee = members.value[createForm.assigneeIndex];
    photoFileIds = await uploadPhotoIds(createPhotoPaths.value, '质量问题照片', { projectId: currentProject.value.id, businessType: 'QUALITY_PENDING' });
    await createQualityIssue({ projectId: currentProject.value.id, title: createForm.title.trim(), location: createForm.location, description: createForm.description, severity: createForm.severity, assigneeId: assignee?.userId, deadline: createForm.deadline, photoFileIds });
    showToast('质量问题已发起'); sheetMode.value = null; await refresh();
  } catch (error) { await deleteFileResources(photoFileIds); showToast(error instanceof Error ? error.message : '发起失败'); }
  finally { submitting.value = false; }
}

async function submitRectification() {
  if (!selectedIssue.value || !rectificationText.value.trim()) { showToast('请填写整改说明'); return; }
  if (!rectificationPhotoPaths.value.length) { showToast('请上传至少一张整改照片'); return; }
  submitting.value = true;
  let photoFileIds: number[] = [];
  try {
    photoFileIds = await uploadPhotoIds(rectificationPhotoPaths.value, '质量整改照片', { projectId: selectedIssue.value.projectId, businessType: 'QUALITY_RECTIFICATION_PENDING' });
    selectedIssue.value = await submitQualityRectification(selectedIssue.value.id, rectificationText.value.trim(), photoFileIds); showToast('整改已提交复查'); sheetMode.value = null; await refresh();
  }
  catch (error) { await deleteFileResources(photoFileIds); showToast(error instanceof Error ? error.message : '整改提交失败'); }
  finally { submitting.value = false; }
}

async function submitReview(passed: boolean) {
  if (!selectedIssue.value) return;
  if (!passed && !reviewComment.value.trim()) { showToast('退回时请填写复查意见'); return; }
  submitting.value = true;
  let photoFileIds: number[] = [];
  try {
    photoFileIds = reviewPhotoPaths.value.length ? await uploadPhotoIds(reviewPhotoPaths.value, '质量复查照片', { projectId: selectedIssue.value.projectId, businessType: 'QUALITY_REVIEW_PENDING' }) : [];
    selectedIssue.value = await reviewQualityIssue(selectedIssue.value.id, passed, reviewComment.value.trim(), photoFileIds); showToast(passed ? '质量问题已关闭' : '已退回继续整改'); sheetMode.value = null; await refresh();
  }
  catch (error) { await deleteFileResources(photoFileIds); showToast(error instanceof Error ? error.message : '复查失败'); }
  finally { submitting.value = false; }
}

function setAssignee(event: unknown) { createForm.assigneeIndex = Number((event as { detail?: { value?: number | string } }).detail?.value || 0); }
function setDeadline(event: unknown) { createForm.deadline = String((event as { detail?: { value?: string } }).detail?.value || ''); }
function severityLabel(value: string) { return value === 'DANGER' ? '严重' : value === 'WARNING' ? '重要' : '一般'; }
function formatTime(value?: string) { return value ? value.replace('T', ' ').slice(0, 16) : '时间未设置'; }
function actionLabel(value: string) { return ({ CREATE: '发起检查', RECTIFY: '提交整改', REVIEW_PASS: '复查通过', REVIEW_REJECT: '复查退回', ASSIGN: '改派/调整期限' } as Record<string,string>)[value] || value; }
function previewDetailPhoto(path: string) { uni.previewImage({ urls: detailPhotoPaths.value, current: path }); }

function choosePhotos(target: 'create' | 'rectification' | 'review') {
  uni.chooseImage({ count: 6, sizeType: ['compressed'], sourceType: ['camera', 'album'], success: (result) => {
    const paths = result.tempFilePaths || [];
    if (target === 'create') createPhotoPaths.value = [...createPhotoPaths.value, ...paths].slice(0, 6);
    if (target === 'rectification') rectificationPhotoPaths.value = [...rectificationPhotoPaths.value, ...paths].slice(0, 6);
    if (target === 'review') reviewPhotoPaths.value = [...reviewPhotoPaths.value, ...paths].slice(0, 6);
  } });
}

async function openDocument(file: FileResourceItem) {
  try {
    const path = await downloadFileToTempPath(file.id);
    uni.openDocument({ filePath: path, showMenu: true, fail: () => uni.previewImage({ urls: [path] }) });
  } catch (error) { showToast(error instanceof Error ? error.message : '文件打开失败'); }
}
</script>

<template>
  <view class="workspace-shell quality-page" :style="{ '--page-accent': ACCENT, '--page-accent-deep': WORKSPACE_THEME.accentDeep, '--page-tint': TINT, '--page-tint-strong': WORKSPACE_THEME.tintStrong, '--page-background': WORKSPACE_THEME.page }">
    <AppNavBar title="质量管理" :show-back="false" />
    <scroll-view class="workspace-scroll" scroll-y enable-flex :style="scrollStyle">
      <view class="workspace-content">
        <WorkspaceAreaSwitcher :project="currentProject" :projects="projects" :accent="ACCENT" :tint="TINT" @select="selectProject" />
        <view v-if="loading && !summary" class="state-panel"><text class="state-title">正在加载质量数据</text></view>
        <view v-else-if="errorMessage" class="state-panel"><text class="state-title">质量数据加载失败</text><text class="state-desc">{{ errorMessage }}</text><button class="retry-button" @tap="refresh">重新加载</button></view>
        <template v-else-if="summary && currentProject">
          <WorkspaceMetricStrip :metrics="metrics" :accent="ACCENT" :motion-key="currentProject.id" />
          <view class="quick-actions quality-actions">
            <button class="quality-primary" :disabled="!canManage" @tap="openCreate"><view class="action-icon-wrap"><image src="/static/design-preview-icons/quality-inspect.png" mode="aspectFit" /></view><view><text>发起检查</text><text>记录现场质量问题</text></view></button>
            <button @tap="setFilter('PENDING')"><view class="action-icon-wrap"><image src="/static/design-preview-icons/quality-rectify.png" mode="aspectFit" /></view><text>问题整改</text></button>
            <button @tap="setFilter('RECHECK')"><view class="action-icon-wrap"><image src="/static/design-preview-icons/quality-accept.png" mode="aspectFit" /></view><text>待复查</text></button>
            <button @tap="sheetMode = 'documents'"><view class="action-icon-wrap"><image src="/static/design-preview-icons/quality-files.png" mode="aspectFit" /></view><text>质量资料</text></button>
          </view>
          <view class="section-block list-section">
            <view class="section-head"><view><text class="section-title">优先处理</text><text class="section-subtitle">按逾期和整改期限排序</text></view><WorkspaceStatusPill :label="`${summary.pendingCount + summary.recheckCount} 项未闭环`" tone="amber" /></view>
            <WorkspaceSegmentControl :model-value="activeFilter" :options="filters" :accent="ACCENT" :tint="TINT" @update:model-value="setFilter" />
            <view class="search-box"><text class="search-icon"></text><input v-model="keyword" class="search-input" placeholder="搜索问题、位置、负责人" placeholder-class="search-placeholder" /></view>
            <view class="plain-list">
              <button v-for="issue in filteredIssues" :key="issue.id" class="plain-row issue-row" @tap="openDetail(issue)"><text class="issue-indicator" :class="statusTone(issue)"></text><view class="plain-copy"><text class="plain-title">{{ issue.title }}</text><text class="plain-meta">{{ issue.location || '未设置位置' }} · {{ issue.assigneeName || '未指定负责人' }}</text><text class="due-text" :class="statusTone(issue)">{{ issue.dueText }}</text></view><WorkspaceStatusPill :label="statusLabel(issue)" :tone="statusTone(issue)" /><text class="row-arrow"></text></button>
              <view v-if="!filteredIssues.length" class="empty-line">当前分类暂无质量问题</view>
            </view>
          </view>
          <view class="flow-hint"><text>质量闭环</text><text>检查 → 整改 → 复查 → 关闭</text></view>
        </template>
      </view>
    </scroll-view>
    <AppTabBar active="quality" />

    <view v-if="sheetMode" class="form-overlay" @tap="sheetMode = null">
      <view class="form-sheet" @tap.stop>
        <view class="sheet-handle"></view><view class="form-head"><text class="form-title">{{ sheetMode === 'create' ? '发起质量检查' : sheetMode === 'documents' ? '质量资料' : '质量问题详情' }}</text><button class="form-close" @tap="sheetMode = null">×</button></view>
        <template v-if="sheetMode === 'create'">
          <view class="form-field"><text class="form-label">问题标题 *</text><input v-model="createForm.title" class="form-input" placeholder="例如：防水层收口不完整" /></view>
          <view class="form-field"><text class="form-label">问题位置</text><input v-model="createForm.location" class="form-input" placeholder="楼层、轴线或作业面" /></view>
          <view class="form-field"><text class="form-label">问题描述</text><textarea v-model="createForm.description" class="form-textarea" placeholder="描述检查发现和整改要求" /></view>
          <view class="form-grid"><view class="form-field"><text class="form-label">整改负责人</text><picker :range="members" range-key="realName" :value="createForm.assigneeIndex" @change="setAssignee"><view class="form-picker">{{ members[createForm.assigneeIndex]?.realName || '默认当前用户' }}</view></picker></view><view class="form-field"><text class="form-label">整改期限</text><picker mode="date" :value="createForm.deadline" @change="setDeadline"><view class="form-picker">{{ createForm.deadline }}</view></picker></view></view>
          <view class="severity-row"><button v-for="item in ['NORMAL','WARNING','DANGER']" :key="item" :class="{ active: createForm.severity === item }" @tap="createForm.severity = item as typeof createForm.severity">{{ severityLabel(item) }}</button></view>
          <view class="form-field photo-field"><text class="form-label">问题照片 *</text><button class="photo-picker" @tap="choosePhotos('create')">+ 拍摄/选择照片</button><view class="quality-photo-grid"><image v-for="path in createPhotoPaths" :key="path" :src="path" mode="aspectFill" /></view></view>
          <view class="form-actions"><button class="secondary-action" @tap="sheetMode = null">取消</button><button class="primary-action" :disabled="submitting" @tap="submitCreate">确认发起</button></view>
        </template>
        <template v-else-if="sheetMode === 'documents'">
          <view class="document-list"><button v-for="file in qualityDocuments" :key="file.id" @tap="openDocument(file)"><view class="document-icon">文</view><view><text>{{ file.fileName }}</text><text>{{ file.fileType || '质量资料' }} · {{ formatTime(file.createTime) }}</text></view><text class="row-arrow"></text></button><view v-if="!qualityDocuments.length" class="empty-line">当前区域暂无质量资料</view></view>
        </template>
        <template v-else-if="selectedIssue">
          <view class="issue-detail-head"><WorkspaceStatusPill :label="statusLabel(selectedIssue)" :tone="statusTone(selectedIssue)" /><text>{{ selectedIssue.issueNo }}</text></view>
          <text class="issue-detail-title">{{ selectedIssue.title }}</text>
          <view class="detail-lines"><view><text>问题位置</text><text>{{ selectedIssue.location || '未设置' }}</text></view><view><text>整改负责人</text><text>{{ selectedIssue.assigneeName || '未指定' }}</text></view><view><text>整改期限</text><text>{{ selectedIssue.deadline || '未设置' }}</text></view><view><text>问题描述</text><text>{{ selectedIssue.description || '无' }}</text></view></view>
          <view v-if="detailPhotoPaths.length" class="quality-photo-grid detail-photos"><image v-for="path in detailPhotoPaths" :key="path" :src="path" mode="aspectFill" @tap="previewDetailPhoto(path)" /></view>
          <view v-if="selectedIssue.canRectify && canRectify" class="form-field action-field"><text class="form-label">整改说明 *</text><textarea v-model="rectificationText" class="form-textarea" placeholder="填写整改措施和结果" /><button class="photo-picker" @tap="choosePhotos('rectification')">+ 整改照片 *</button><view class="quality-photo-grid"><image v-for="path in rectificationPhotoPaths" :key="path" :src="path" mode="aspectFill" /></view><button class="primary-action single-action" :disabled="submitting" @tap="submitRectification">提交复查</button></view>
          <view v-if="selectedIssue.canReview && canReview" class="form-field action-field"><text class="form-label">复查意见</text><textarea v-model="reviewComment" class="form-textarea" placeholder="退回时必须填写意见" /><button class="photo-picker" @tap="choosePhotos('review')">+ 复查照片（可选）</button><view class="quality-photo-grid"><image v-for="path in reviewPhotoPaths" :key="path" :src="path" mode="aspectFill" /></view><view class="form-actions"><button class="secondary-action reject" :disabled="submitting" @tap="submitReview(false)">退回整改</button><button class="primary-action" :disabled="submitting" @tap="submitReview(true)">复查通过</button></view></view>
          <view v-if="selectedIssue.rectificationDescription" class="rectification-result"><text>最近整改说明</text><text>{{ selectedIssue.rectificationDescription }}</text></view>
          <view class="quality-timeline"><text class="timeline-title">操作留痕</text><view v-for="log in selectedIssue.logs || []" :key="log.id"><text>{{ actionLabel(log.actionType) }}</text><text>{{ log.operatorName || '-' }} · {{ formatTime(log.createTime) }}</text><text>{{ log.comment || '无补充说明' }}</text></view><text v-if="!(selectedIssue.logs || []).length" class="empty-line">暂无留痕</text></view>
        </template>
      </view>
    </view>
  </view>
</template>

<style scoped src="../../styles/workspace-page.css"></style>
<style scoped>
.quality-actions {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12rpx;
  overflow: visible;
  padding: 0;
  background: transparent;
  box-shadow: none;
}

.quality-actions > button:not(.quality-primary) {
  min-height: 90rpx;
  border: 1rpx solid var(--workspace-divider);
  border-radius: 16rpx;
  background: #fff;
  box-shadow: var(--workspace-shadow);
  color: var(--workspace-text);
}

.quality-actions > button:not(.quality-primary) image {
  filter: grayscale(.65) sepia(.18) hue-rotate(158deg) saturate(1.25) brightness(.82);
}

.quality-primary {
  grid-column: 1 / -1;
  min-height: 104rpx !important;
  justify-content: flex-start !important;
  flex-direction: row !important;
  gap: 16rpx !important;
  padding: 17rpx 20rpx !important;
  border: 1rpx solid #c7d9e6;
  border-radius: 16rpx !important;
  background: var(--page-tint-strong) !important;
  box-shadow: var(--workspace-shadow);
  color: var(--page-accent-deep) !important;
  text-align: left;
}

.quality-primary .action-icon-wrap {
  width: 58rpx;
  height: 58rpx;
  border-radius: 14rpx;
  background: rgba(255, 255, 255, .78);
}

.quality-primary image {
  width: 40rpx;
  height: 40rpx;
  filter: grayscale(.7) sepia(.2) hue-rotate(158deg) saturate(1.3) brightness(.78);
}

.quality-primary view:last-child { display: flex; min-width: 0; flex-direction: column; }
.quality-primary view:last-child text:first-child { font-size: 26rpx; font-weight: 800; }
.quality-primary view:last-child text:last-child { margin-top: 5rpx; color: #668095; font-size: 20rpx; font-weight: 500; }
.quality-primary:disabled { opacity: .48; }

.list-section .section-head { border-radius: 16rpx 16rpx 0 0; }
.list-section :deep(.segment-control) { margin: 16rpx 20rpx 0; }
.list-section .search-box { margin-top: 13rpx; }
.issue-row { min-height: 118rpx; align-items: flex-start; }
.issue-indicator { width: 7rpx; height: 64rpx; flex-shrink: 0; margin-top: 3rpx; border-radius: 999rpx; background: var(--page-accent); }
.issue-indicator.amber { background: #d39439; }
.issue-indicator.red { background: #c95b5b; }
.issue-indicator.green { background: #2e8b72; }
.issue-row :deep(.status-pill) { flex-shrink: 0; margin-top: 2rpx; }
.due-text { display: block; margin-top: 7rpx; font-size: 20rpx; line-height: 1.3; }
.due-text.blue { color: var(--page-accent-deep); }
.due-text.amber { color: #a96822; }
.due-text.red { color: #b94f4f; }
.due-text.green { color: #2e8069; }
.empty-line { padding: 44rpx 0; color: var(--workspace-text-muted); font-size: 21rpx; text-align: center; }

.flow-hint {
  display: flex;
  min-height: 70rpx;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  padding: 0 20rpx;
  border: 1rpx solid var(--workspace-divider);
  border-radius: 16rpx;
  background: #fff;
  box-shadow: var(--workspace-shadow);
  color: var(--workspace-text-secondary);
  font-size: 21rpx;
}

.flow-hint text:first-child { color: var(--page-accent-deep); font-weight: 800; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14rpx; }
.severity-row { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10rpx; }
.severity-row button { min-height: 60rpx; border: 1rpx solid transparent; border-radius: 12rpx; background: #f2f5f7; color: var(--workspace-text-secondary); font-size: 22rpx; }
.severity-row button::after { border: 0; }
.severity-row button.active { border-color: #c7d9e6; background: var(--page-tint); color: var(--page-accent-deep); font-weight: 750; }
.issue-detail-head { display: flex; align-items: center; justify-content: space-between; color: var(--workspace-text-muted); font-size: 20rpx; }
.issue-detail-title { display: block; margin: 18rpx 0; color: var(--workspace-text); font-size: 29rpx; font-weight: 800; line-height: 1.35; }
.detail-lines { border-top: 1rpx solid var(--workspace-divider); }
.detail-lines view { display: flex; min-height: 70rpx; align-items: flex-start; justify-content: space-between; gap: 24rpx; padding: 16rpx 0; border-bottom: 1rpx solid var(--workspace-divider); color: var(--workspace-text-muted); font-size: 22rpx; line-height: 1.4; }
.detail-lines view text:last-child { max-width: 70%; color: var(--workspace-text); text-align: right; }
.action-field { margin-top: 24rpx; }
.single-action { width: 100%; min-height: 72rpx; margin-top: 14rpx; border-radius: 12rpx; font-size: 23rpx; font-weight: 750; }
.single-action::after { border: 0; }
.reject { color: #b94f4f; }
.rectification-result { margin-top: 22rpx; padding: 18rpx; border-radius: 12rpx; background: var(--page-tint); }
.rectification-result text { display: block; color: var(--workspace-text-secondary); font-size: 22rpx; }
.rectification-result text:first-child { color: var(--page-accent-deep); font-weight: 750; }
.rectification-result text:last-child { margin-top: 8rpx; line-height: 1.5; }
.photo-field { margin-top: 16rpx; }
.photo-picker { width: 100%; min-height: 64rpx; border: 1rpx solid #c7d9e6; border-radius: 12rpx; background: var(--page-tint); color: var(--page-accent-deep); font-size: 22rpx; font-weight: 700; }
.photo-picker::after { border: 0; }
.quality-photo-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10rpx; margin-top: 10rpx; }
.quality-photo-grid image { width: 100%; height: 120rpx; border-radius: 10rpx; background: #eef2f4; }
.detail-photos { margin: 18rpx 0; }
.document-list button { display: flex; width: 100%; min-height: 92rpx; align-items: center; gap: 14rpx; border-bottom: 1rpx solid var(--workspace-divider); background: #fff; text-align: left; }
.document-list button::after { border: 0; }
.document-icon { display: flex; width: 52rpx; height: 52rpx; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 12rpx; background: var(--page-tint); color: var(--page-accent-deep); font-size: 22rpx; font-weight: 800; }
.document-list button view:nth-child(2) { min-width: 0; flex: 1; }
.document-list button text { display: block; overflow: hidden; color: var(--workspace-text); font-size: 22rpx; text-overflow: ellipsis; white-space: nowrap; }
.document-list button text + text { margin-top: 5rpx; color: var(--workspace-text-muted); font-size: 19rpx; }
.quality-timeline { margin-top: 22rpx; padding-top: 18rpx; border-top: 1rpx solid var(--workspace-divider); }
.timeline-title { display: block; margin-bottom: 8rpx; color: var(--workspace-text); font-size: 23rpx; font-weight: 800; }
.quality-timeline > view { padding: 12rpx 0; border-bottom: 1rpx solid var(--workspace-divider); }
.quality-timeline > view text { display: block; color: var(--workspace-text-muted); font-size: 20rpx; line-height: 1.4; }
.quality-timeline > view text:first-child { color: var(--page-accent-deep); font-weight: 750; }
.quality-timeline > view text:last-child { margin-top: 5rpx; color: var(--workspace-text-secondary); }

@media (max-width: 360px) {
  .quality-actions { gap: 9rpx; }
  .quality-actions > button:not(.quality-primary) { padding-right: 4rpx; padding-left: 4rpx; font-size: 21rpx; }
  .form-grid { grid-template-columns: 1fr; }
}
</style>
