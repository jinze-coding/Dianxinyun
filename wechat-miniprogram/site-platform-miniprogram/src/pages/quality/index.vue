<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import AppTabBar from '@/components/AppTabBar.vue';
import WorkspaceAreaSheet from '@/components/workspace/WorkspaceAreaSheet.vue';
import WorkspaceAreaSwitcher from '@/components/workspace/WorkspaceAreaSwitcher.vue';
import WorkspaceMetricStrip, { type WorkspaceMetric } from '@/components/workspace/WorkspaceMetricStrip.vue';
import WorkspaceSegmentControl from '@/components/workspace/WorkspaceSegmentControl.vue';
import WorkspaceStatusPill from '@/components/workspace/WorkspaceStatusPill.vue';
import { WORKSPACE_THEME } from '@/constants/workspaceTheme';
import { assignQualityIssue, createQualityIssue, getQualityAssignees, getQualityIssue, getQualityIssuePage, getQualitySummary, getQualityTodos, reviewQualityIssue, submitQualityRectification, voidQualityIssue } from '@/api/quality';
import { deleteFileResources, downloadFileResults, downloadFileToTempPath, getFileResources, uploadPhotoIds, type FileResourceItem } from '@/api/file';
import { useProjectStore } from '@/stores/project';
import { useAuthStore } from '@/stores/auth';
import type { QualityAssignee, QualityIssue, QualityIssueStatus, QualitySummary, TodoItem } from '@/types';
import { extensionOf } from '@/utils/documentFile';
import { usePageScrollHeight } from '@/utils/navLayout';
import { showToast } from '@/utils/navigation';

type QualityFilter = 'ALL' | QualityIssueStatus;
type SheetMode = 'create' | 'detail' | 'assign' | 'void' | 'todos' | 'documents' | null;
interface EvidenceGroup {
  key: string;
  title: string;
  operator?: string;
  time?: string;
  fileIds: number[];
  paths: string[];
  failedCount: number;
}

const ACCENT = WORKSPACE_THEME.accent;
const TINT = WORKSPACE_THEME.tint;
const projectStore = useProjectStore();
const authStore = useAuthStore();
const summary = ref<QualitySummary | null>(null);
const issues = ref<QualityIssue[]>([]);
const assignees = ref<QualityAssignee[]>([]);
const selectedIssue = ref<QualityIssue | null>(null);
const activeFilter = ref<QualityFilter>('ALL');
const keyword = ref('');
const submittedKeyword = ref('');
const loading = ref(false);
const loadingMore = ref(false);
const submitting = ref(false);
const errorMessage = ref('');
const sheetMode = ref<SheetMode>(null);
const areaSheetOpen = ref(false);
const rectificationText = ref('');
const reviewComment = ref('');
const voidComment = ref('');
const qualityDocuments = ref<FileResourceItem[]>([]);
const documentsLoading = ref(false);
const documentsError = ref('');
const openingDocumentId = ref<number | null>(null);
const issuePageNo = ref(1);
const issueTotal = ref(0);
const ISSUE_PAGE_SIZE = 20;
const qualityTodos = ref<TodoItem[]>([]);
const todosLoading = ref(false);
const todosError = ref('');
const createPhotoPaths = ref<string[]>([]);
const rectificationPhotoPaths = ref<string[]>([]);
const reviewPhotoPaths = ref<string[]>([]);
const createRequestKey = ref('');
const evidenceGroups = ref<EvidenceGroup[]>([]);
const evidenceLoading = ref(false);
const { scrollStyle } = usePageScrollHeight({ bottomRpx: 124, minHeight: 320 });
let qualityRequestSequence = 0;
let documentRequestSequence = 0;
let todoRequestSequence = 0;

const createForm = reactive({ title: '', location: '', description: '', severity: 'NORMAL' as 'NORMAL' | 'WARNING' | 'DANGER', assigneeIndex: -1, deadline: '' });
const assignForm = reactive({ assigneeIndex: -1, deadline: '', comment: '' });
const projects = computed(() => projectStore.state.projects);
const currentProject = computed(() => projects.value.find((item) => item.id === projectStore.state.currentProjectId));
const canManage = computed(() => Boolean(summary.value?.canManage) && Boolean(currentProject.value)
  && authStore.hasProjectPermission(currentProject.value!.id, 'quality.manage'));
const canRectify = computed(() => Boolean(selectedIssue.value)
  && authStore.hasProjectPermission(selectedIssue.value!.projectId, 'quality.rectify'));
const canReview = computed(() => Boolean(selectedIssue.value)
  && authStore.hasProjectPermission(selectedIssue.value!.projectId, 'quality.review'));
const filteredIssues = computed(() => issues.value);
const hasMoreIssues = computed(() => issues.value.length < issueTotal.value);
const evidenceFailureCount = computed(() =>
  evidenceGroups.value.reduce((total, group) => total + group.failedCount, 0));
const metrics = computed<WorkspaceMetric[]>(() => [
  { label: '今日新增问题', value: summary.value?.todayCheckCount || 0 },
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
  const pendingIssueId = Number(uni.getStorageSync('site_platform_quality_issue_id'));
  if (Number.isFinite(pendingIssueId) && pendingIssueId > 0) {
    uni.removeStorageSync('site_platform_quality_issue_id');
    const issue = issues.value.find((item) => item.id === pendingIssueId);
    await openDetail(issue || { id: pendingIssueId } as QualityIssue);
  }
});

async function refresh() {
  const requestSequence = ++qualityRequestSequence;
  loading.value = true;
  errorMessage.value = '';
  try {
    await projectStore.loadProjects();
    if (requestSequence !== qualityRequestSequence) return;
    if (projectStore.state.errorMessage) {
      summary.value = null;
      issues.value = [];
      errorMessage.value = projectStore.state.errorMessage;
      return;
    }
    if (!projectStore.state.currentProjectId) {
      summary.value = null;
      issues.value = [];
      errorMessage.value = '当前账号暂无可访问的施工区域';
      return;
    }
    let projectId = projectStore.state.currentProjectId;
    if (!authStore.hasProjectPermission(projectId, 'quality.view')) {
      const authorizedProject = projects.value.find((project) =>
        authStore.hasProjectPermission(project.id, 'quality.view'));
      if (!authorizedProject) {
        summary.value = null;
        issues.value = [];
        errorMessage.value = '当前账号暂无质量管理查看权限';
        return;
      }
      projectStore.setCurrentProject(authorizedProject.id);
      projectId = authorizedProject.id;
    }
    const [summaryResult, issuePage] = await Promise.all([
      getQualitySummary(projectId),
      getQualityIssuePage(projectId, activeFilter.value, submittedKeyword.value, 1, ISSUE_PAGE_SIZE)
    ]);
    if (requestSequence !== qualityRequestSequence
      || projectStore.state.currentProjectId !== projectId) return;
    summary.value = summaryResult;
    issues.value = issuePage.records;
    issuePageNo.value = issuePage.pageNo;
    issueTotal.value = issuePage.total;
    void loadQualityTodos(projectId);
  } catch (error) {
    if (requestSequence !== qualityRequestSequence) return;
    summary.value = null;
    issues.value = [];
    errorMessage.value = error instanceof Error ? error.message : '质量数据加载失败';
  } finally {
    if (requestSequence === qualityRequestSequence) loading.value = false;
  }
}

async function selectProject(projectId: number) {
  ++qualityRequestSequence;
  ++documentRequestSequence;
  ++todoRequestSequence;
  projectStore.setCurrentProject(projectId);
  activeFilter.value = 'ALL'; keyword.value = ''; submittedKeyword.value = '';
  summary.value = null;
  issues.value = [];
  issuePageNo.value = 1;
  issueTotal.value = 0;
  selectedIssue.value = null;
  qualityDocuments.value = [];
  qualityTodos.value = [];
  todosError.value = '';
  assignees.value = [];
  documentsError.value = '';
  await refresh();
}

async function loadQualityTodos(projectId = currentProject.value?.id) {
  if (!projectId) return;
  const requestSequence = ++todoRequestSequence;
  todosLoading.value = true;
  todosError.value = '';
  try {
    const result = await getQualityTodos(projectId);
    if (requestSequence !== todoRequestSequence
      || projectStore.state.currentProjectId !== projectId) return;
    qualityTodos.value = result;
  } catch (error) {
    if (requestSequence !== todoRequestSequence) return;
    qualityTodos.value = [];
    todosError.value = error instanceof Error ? error.message : '质量待办加载失败';
  } finally {
    if (requestSequence === todoRequestSequence) todosLoading.value = false;
  }
}

function openQualityTodos() {
  sheetMode.value = 'todos';
  if (!qualityTodos.value.length && !todosLoading.value) void loadQualityTodos();
}

async function openTodoIssue(todo: TodoItem) {
  if (!todo.targetId) return;
  sheetMode.value = null;
  await openDetail({ id: todo.targetId } as QualityIssue);
}

async function openDocuments() {
  if (!currentProject.value) return;
  sheetMode.value = 'documents';
  const projectId = currentProject.value.id;
  const requestSequence = ++documentRequestSequence;
  documentsLoading.value = true;
  documentsError.value = '';
  qualityDocuments.value = [];
  try {
    const result = await getFileResources(projectId, 'QUALITY_DOCUMENT', 'ACTIVE');
    if (requestSequence !== documentRequestSequence
      || projectStore.state.currentProjectId !== projectId) return;
    qualityDocuments.value = result;
  } catch (error) {
    if (requestSequence !== documentRequestSequence) return;
    documentsError.value = error instanceof Error ? error.message : '质量资料加载失败';
  } finally {
    if (requestSequence === documentRequestSequence) documentsLoading.value = false;
  }
}

async function setFilter(value: string) {
  activeFilter.value = value as QualityFilter;
  summary.value = null;
  issues.value = [];
  issuePageNo.value = 1;
  issueTotal.value = 0;
  await refresh();
}

async function applySearch() {
  const nextKeyword = keyword.value.trim();
  if (nextKeyword === submittedKeyword.value && issues.value.length) return;
  submittedKeyword.value = nextKeyword;
  issues.value = [];
  issuePageNo.value = 1;
  issueTotal.value = 0;
  await refresh();
}

async function loadMoreIssues() {
  if (loading.value || loadingMore.value || !hasMoreIssues.value || !currentProject.value) return;
  const requestSequence = qualityRequestSequence;
  const projectId = currentProject.value.id;
  const filter = activeFilter.value;
  const search = submittedKeyword.value;
  const nextPage = issuePageNo.value + 1;
  loadingMore.value = true;
  try {
    const result = await getQualityIssuePage(projectId, filter, search, nextPage, ISSUE_PAGE_SIZE);
    if (requestSequence !== qualityRequestSequence
      || projectStore.state.currentProjectId !== projectId
      || activeFilter.value !== filter
      || submittedKeyword.value !== search) return;
    const knownIds = new Set(issues.value.map((item) => item.id));
    issues.value = [...issues.value, ...result.records.filter((item) => !knownIds.has(item.id))];
    issuePageNo.value = result.pageNo;
    issueTotal.value = result.total;
  } catch (error) {
    showToast(error instanceof Error ? error.message : '更多质量问题加载失败');
  } finally {
    loadingMore.value = false;
  }
}

function statusLabel(issue: QualityIssue) { return issue.status === 'VOIDED' ? '已作废' : issue.overdue ? '已逾期' : issue.status === 'PENDING' ? '待整改' : issue.status === 'RECHECK' ? '待复查' : '已关闭'; }
function statusTone(issue: QualityIssue) { return issue.status === 'VOIDED' ? 'gray' as const : issue.overdue ? 'red' as const : issue.status === 'PENDING' ? 'amber' as const : issue.status === 'RECHECK' ? 'blue' as const : 'green' as const; }

async function openCreate() {
  if (!canManage.value || !currentProject.value) { showToast('当前账号无质量问题发起权限'); return; }
  try {
    await loadAssignees(currentProject.value.id);
  } catch (error) {
    showToast(error instanceof Error ? error.message : '整改负责人加载失败');
    return;
  }
  if (!assignees.value.length) {
    showToast('当前项目没有具备质量整改权限的负责人');
    return;
  }
  const date = new Date(Date.now() + 3 * 86400000);
  Object.assign(createForm, { title: '', location: '', description: '', severity: 'NORMAL', assigneeIndex: -1, deadline: `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}` });
  createRequestKey.value = `q-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 14)}`;
  createPhotoPaths.value = [];
  sheetMode.value = 'create';
}

async function loadAssignees(projectId: number) {
  assignees.value = await getQualityAssignees(projectId);
}

async function openAssign() {
  if (!selectedIssue.value || selectedIssue.value.status === 'CLOSED' || !canManage.value) return;
  try {
    await loadAssignees(selectedIssue.value.projectId);
  } catch (error) {
    showToast(error instanceof Error ? error.message : '整改负责人加载失败');
    return;
  }
  const currentIndex = assignees.value.findIndex((item) => item.userId === selectedIssue.value?.assigneeId);
  Object.assign(assignForm, {
    assigneeIndex: currentIndex,
    deadline: selectedIssue.value.deadline || '',
    comment: ''
  });
  sheetMode.value = 'assign';
}

async function openDetail(issue: QualityIssue) {
  try {
    const detail = await getQualityIssue(issue.id);
    if (projectStore.state.currentProjectId !== detail.projectId) {
      projectStore.setCurrentProject(detail.projectId);
      activeFilter.value = 'ALL';
      keyword.value = '';
      await refresh();
    }
    selectedIssue.value = detail;
    rectificationText.value = ''; reviewComment.value = ''; rectificationPhotoPaths.value = []; reviewPhotoPaths.value = [];
    sheetMode.value = 'detail';
    await loadEvidenceGroups();
  }
  catch (error) { showToast(error instanceof Error ? error.message : '详情加载失败'); }
}

function parsePhotoIds(value?: string): number[] {
  return String(value || '').split(',')
    .map((id) => Number(id.trim()))
    .filter((id) => Number.isFinite(id) && id > 0);
}

function evidenceTitle(actionType: string) {
  if (actionType === 'CREATE') return '问题现场';
  if (actionType === 'RECTIFY') return '整改反馈';
  if (actionType === 'REVIEW_PASS') return '复查通过';
  if (actionType === 'REVIEW_REJECT') return '复查退回';
  return actionLabel(actionType);
}

function buildEvidenceGroups(issue: QualityIssue): EvidenceGroup[] {
  const fromLogs = (issue.logs || [])
    .filter((log) => parsePhotoIds(log.photoFileIds).length > 0)
    .map((log) => ({
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
    { key: 'issue', title: '问题现场', fileIds: issue.issuePhotoFileIds || [], paths: [], failedCount: 0 },
    { key: 'rectification', title: '整改反馈', fileIds: issue.rectificationPhotoFileIds || [], paths: [], failedCount: 0 },
    { key: 'review', title: '复查记录', fileIds: issue.reviewPhotoFileIds || [], paths: [], failedCount: 0 }
  ].filter((group) => group.fileIds.length > 0);
}

async function loadEvidenceGroups() {
  if (!selectedIssue.value) return;
  const issueId = selectedIssue.value.id;
  const groups = buildEvidenceGroups(selectedIssue.value);
  evidenceGroups.value = groups;
  evidenceLoading.value = true;
  try {
    await Promise.all(groups.map(async (group) => {
      const results = await downloadFileResults(group.fileIds);
      if (selectedIssue.value?.id !== issueId) return;
      group.paths = results.flatMap((result) => result.path ? [result.path] : []);
      group.failedCount = results.filter((result) => !result.path).length;
    }));
    evidenceGroups.value = [...groups];
  } finally {
    if (selectedIssue.value?.id === issueId) evidenceLoading.value = false;
  }
}

async function submitCreate() {
  if (submitting.value) return;
  if (!currentProject.value || !createForm.title.trim()) { showToast('请填写质量问题标题'); return; }
  const assignee = assignees.value[createForm.assigneeIndex];
  if (!assignee) { showToast('请选择整改负责人'); return; }
  if (!createPhotoPaths.value.length) { showToast('请上传至少一张问题照片'); return; }
  submitting.value = true;
  let photoFileIds: number[] = [];
  try {
    photoFileIds = await uploadPhotoIds(createPhotoPaths.value, '质量问题照片', { projectId: currentProject.value.id, businessType: 'QUALITY_PENDING' });
    await createQualityIssue({ projectId: currentProject.value.id, requestKey: createRequestKey.value, title: createForm.title.trim(), location: createForm.location, description: createForm.description, severity: createForm.severity, assigneeId: assignee.userId, deadline: createForm.deadline, photoFileIds });
    showToast('质量问题已发起'); sheetMode.value = null; await refresh();
  } catch (error) { await deleteFileResources(photoFileIds); showToast(error instanceof Error ? error.message : '发起失败'); }
  finally { submitting.value = false; }
}

async function submitAssign() {
  if (submitting.value || !selectedIssue.value) return;
  const assignee = assignees.value[assignForm.assigneeIndex];
  if (!assignee) { showToast('请选择整改负责人'); return; }
  if (!assignForm.deadline) { showToast('请选择闭环期限'); return; }
  submitting.value = true;
  try {
    selectedIssue.value = await assignQualityIssue(selectedIssue.value.id, {
      assigneeId: assignee.userId,
      deadline: assignForm.deadline,
      comment: assignForm.comment.trim()
    });
    showToast('整改负责人和期限已更新');
    sheetMode.value = null;
    await refresh();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '调整失败');
  } finally {
    submitting.value = false;
  }
}

function openVoid() {
  if (!selectedIssue.value || !canManage.value
    || ['CLOSED', 'VOIDED'].includes(selectedIssue.value.status)) return;
  voidComment.value = '';
  sheetMode.value = 'void';
}

async function submitVoid() {
  if (submitting.value || !selectedIssue.value) return;
  if (!voidComment.value.trim()) { showToast('请填写作废原因'); return; }
  if (!await confirmAction('确认作废这条质量问题？', '作废后不能继续整改、复查或改派，原因会写入操作留痕。')) return;
  submitting.value = true;
  try {
    selectedIssue.value = await voidQualityIssue(selectedIssue.value.id, voidComment.value.trim());
    showToast('质量问题已作废');
    sheetMode.value = null;
    await refresh();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '作废失败');
  } finally {
    submitting.value = false;
  }
}

async function submitRectification() {
  if (submitting.value) return;
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
  if (submitting.value) return;
  if (!selectedIssue.value) return;
  if (!passed && !reviewComment.value.trim()) { showToast('退回时请填写复查意见'); return; }
  if (evidenceLoading.value) { showToast('整改证据仍在加载，请稍候'); return; }
  if (evidenceFailureCount.value > 0) {
    showToast(`有 ${evidenceFailureCount.value} 个证据附件加载失败，请重新加载后再复查`);
    return;
  }
  if (passed && !await confirmAction('确认复查通过并关闭该问题？', '关闭后不能继续整改或改派。')) return;
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
function setAssignAssignee(event: unknown) { assignForm.assigneeIndex = Number((event as { detail?: { value?: number | string } }).detail?.value || 0); }
function setDeadline(event: unknown) { createForm.deadline = String((event as { detail?: { value?: string } }).detail?.value || ''); }
function setAssignDeadline(event: unknown) { assignForm.deadline = String((event as { detail?: { value?: string } }).detail?.value || ''); }
function severityLabel(value: string) { return value === 'DANGER' ? '严重' : value === 'WARNING' ? '重要' : '一般'; }
function formatTime(value?: string) { return value ? value.replace('T', ' ').slice(0, 16) : '时间未设置'; }
function actionLabel(value: string) { return ({ CREATE: '发起检查', RECTIFY: '提交整改', REVIEW_PASS: '复查通过', REVIEW_REJECT: '复查退回', ASSIGN: '改派/调整期限', VOID: '问题作废' } as Record<string,string>)[value] || value; }
function previewEvidencePhoto(group: EvidenceGroup, path: string) {
  uni.previewImage({ urls: group.paths, current: path });
}

function localPhotoPaths(target: 'create' | 'rectification' | 'review') {
  if (target === 'create') return createPhotoPaths;
  if (target === 'rectification') return rectificationPhotoPaths;
  return reviewPhotoPaths;
}

function previewLocalPhoto(target: 'create' | 'rectification' | 'review', path: string) {
  uni.previewImage({ urls: localPhotoPaths(target).value, current: path });
}

function removeLocalPhoto(target: 'create' | 'rectification' | 'review', index: number) {
  if (submitting.value) return;
  const paths = localPhotoPaths(target);
  paths.value = paths.value.filter((_, currentIndex) => currentIndex !== index);
}

function confirmAction(title: string, content: string): Promise<boolean> {
  return new Promise((resolve) => {
    uni.showModal({
      title,
      content,
      confirmText: '确认',
      cancelText: '取消',
      success: (result) => resolve(Boolean(result.confirm)),
      fail: () => resolve(false)
    });
  });
}

function closeSheet() {
  if (submitting.value) {
    showToast('正在提交，请稍候');
    return;
  }
  sheetMode.value = null;
}

function choosePhotos(target: 'create' | 'rectification' | 'review') {
  if (submitting.value) return;
  uni.chooseImage({ count: 6, sizeType: ['compressed'], sourceType: ['camera', 'album'], success: (result) => {
    const paths = result.tempFilePaths || [];
    if (target === 'create') createPhotoPaths.value = [...createPhotoPaths.value, ...paths].slice(0, 6);
    if (target === 'rectification') rectificationPhotoPaths.value = [...rectificationPhotoPaths.value, ...paths].slice(0, 6);
    if (target === 'review') reviewPhotoPaths.value = [...reviewPhotoPaths.value, ...paths].slice(0, 6);
  } });
}

async function openDocument(file: FileResourceItem) {
  if (openingDocumentId.value !== null) return;
  openingDocumentId.value = file.id;
  try {
    const path = await downloadFileToTempPath(file.id);
    const extension = extensionOf(file.fileName)
      || String(file.fileType || '').replace(/^\./, '').toLowerCase();
    if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'].includes(extension)) {
      uni.previewImage({ urls: [path], current: path });
      return;
    }
    if (!['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx'].includes(extension)) {
      throw new Error('该格式暂不支持在小程序内打开');
    }
    await new Promise<void>((resolve, reject) => {
      uni.openDocument({
        filePath: path,
        fileType: extension,
        showMenu: true,
        success: () => resolve(),
        fail: (error) => reject(new Error(error.errMsg || '文件打开失败'))
      });
    });
  } catch (error) {
    showToast(error instanceof Error ? error.message : '文件打开失败');
  } finally {
    openingDocumentId.value = null;
  }
}
</script>

<template>
  <view class="workspace-shell quality-page" :style="{ '--page-accent': ACCENT, '--page-accent-deep': WORKSPACE_THEME.accentDeep, '--page-tint': TINT, '--page-tint-strong': WORKSPACE_THEME.tintStrong, '--page-background': WORKSPACE_THEME.page }">
    <AppNavBar title="质量管理" :show-back="false" />
    <scroll-view class="workspace-scroll" scroll-y enable-flex lower-threshold="80" :style="scrollStyle" @scrolltolower="loadMoreIssues">
      <view class="workspace-content">
        <WorkspaceAreaSwitcher
          :project="currentProject"
          :projects="projects"
          :accent="ACCENT"
          :tint="TINT"
          :open="areaSheetOpen"
          @open="areaSheetOpen = true"
        />
        <view v-if="loading && !summary" class="state-panel"><text class="state-title">正在加载质量数据</text></view>
        <view v-else-if="errorMessage" class="state-panel"><text class="state-title">质量数据加载失败</text><text class="state-desc">{{ errorMessage }}</text><button class="retry-button" @tap="refresh">重新加载</button></view>
        <template v-else-if="summary && currentProject">
          <WorkspaceMetricStrip :metrics="metrics" :accent="ACCENT" :motion-key="currentProject.id" />
          <button class="quality-primary" :disabled="!canManage" @tap="openCreate">
            <view class="quality-primary-main">
              <view class="action-icon-wrap"><image src="/static/design-preview-icons/quality-inspect.png" mode="aspectFit" /></view>
              <view class="quality-primary-copy">
                <text class="quality-primary-title">发起质量检查</text>
                <text class="quality-primary-subtitle">{{ canManage ? '记录现场问题并指派整改' : '当前账号暂无质量问题发起权限' }}</text>
              </view>
            </view>
            <view class="quality-primary-cta">
              <text>{{ canManage ? '立即发起' : '暂无权限' }}</text>
              <text v-if="canManage" class="quality-primary-arrow"></text>
            </view>
          </button>
          <button class="my-todo-banner" @tap="openQualityTodos">
            <view><text>我的质量待办</text><text>{{ qualityTodos.length ? `${qualityTodos.length} 项需要处理` : '当前没有待处理任务' }}</text></view>
            <view><text>{{ qualityTodos.length }}</text><text class="quality-document-arrow"></text></view>
          </button>
          <view class="section-block list-section">
            <view class="section-head">
              <view class="section-heading">
                <view class="section-title-line"><text class="section-title">优先处理</text><WorkspaceStatusPill :label="`${summary.pendingCount + summary.recheckCount} 项未闭环`" tone="amber" /></view>
                <text class="section-subtitle">按逾期和闭环期限排序</text>
              </view>
              <button class="quality-document-link" @tap="openDocuments"><text>质量资料</text><text class="quality-document-arrow"></text></button>
            </view>
            <WorkspaceSegmentControl :model-value="activeFilter" :options="filters" :accent="ACCENT" :tint="TINT" @update:model-value="setFilter" />
            <view class="search-box"><text class="search-icon"></text><input v-model="keyword" class="search-input" confirm-type="search" placeholder="搜索问题、位置、负责人" placeholder-class="search-placeholder" @confirm="applySearch" /><button class="search-submit" @tap="applySearch">搜索</button></view>
            <view class="plain-list">
              <button v-for="issue in filteredIssues" :key="issue.id" class="plain-row issue-row" @tap="openDetail(issue)"><text class="issue-indicator" :class="statusTone(issue)"></text><view class="plain-copy"><view class="issue-title-line"><text class="plain-title">{{ issue.title }}</text><text class="severity-tag" :class="issue.severity.toLowerCase()">{{ severityLabel(issue.severity) }}</text></view><text class="plain-meta">{{ issue.location || '未设置位置' }} · {{ issue.assigneeName || '未指定负责人' }}</text><text class="due-text" :class="statusTone(issue)">{{ issue.dueText }}</text></view><WorkspaceStatusPill :label="statusLabel(issue)" :tone="statusTone(issue)" /><text class="row-arrow"></text></button>
              <view v-if="!filteredIssues.length" class="empty-line">{{ submittedKeyword ? '没有匹配当前搜索条件的质量问题' : '当前分类暂无质量问题' }}</view>
              <view v-else class="page-tail">{{ loadingMore ? '正在加载更多' : hasMoreIssues ? '继续上拉加载更多' : `已显示全部 ${issueTotal} 项` }}</view>
            </view>
          </view>
          <view class="flow-hint"><text>质量闭环</text><text>检查 → 整改 → 复查 → 关闭</text></view>
        </template>
      </view>
    </scroll-view>
    <WorkspaceAreaSheet
      :open="areaSheetOpen"
      :project="currentProject"
      :projects="projects"
      :accent="ACCENT"
      :tint="TINT"
      @close="areaSheetOpen = false"
      @select="selectProject"
    />
    <AppTabBar v-if="!areaSheetOpen" active="quality" />

    <view v-if="sheetMode" class="form-overlay" @tap="closeSheet">
      <view class="form-sheet" @tap.stop>
        <view class="sheet-handle"></view><view class="form-head"><text class="form-title">{{ sheetMode === 'create' ? '发起质量检查' : sheetMode === 'assign' ? '改派与调整期限' : sheetMode === 'void' ? '作废质量问题' : sheetMode === 'todos' ? '我的质量待办' : sheetMode === 'documents' ? '质量资料' : '质量问题详情' }}</text><button class="form-close" :disabled="submitting" @tap="closeSheet">×</button></view>
        <template v-if="sheetMode === 'create'">
          <view class="form-field"><text class="form-label">问题标题 *</text><input v-model="createForm.title" class="form-input" maxlength="200" placeholder="例如：防水层收口不完整" /></view>
          <view class="form-field"><text class="form-label">问题位置</text><input v-model="createForm.location" class="form-input" maxlength="200" placeholder="楼层、轴线或作业面" /></view>
          <view class="form-field"><text class="form-label">问题描述</text><textarea v-model="createForm.description" class="form-textarea" maxlength="1000" placeholder="描述检查发现和整改要求" /></view>
          <view class="form-grid"><view class="form-field"><text class="form-label">整改负责人 *</text><picker :range="assignees" range-key="displayName" :value="Math.max(createForm.assigneeIndex, 0)" @change="setAssignee"><view class="form-picker">{{ assignees[createForm.assigneeIndex]?.displayName || '请选择整改负责人' }}</view></picker></view><view class="form-field"><text class="form-label">闭环期限</text><picker mode="date" :value="createForm.deadline" @change="setDeadline"><view class="form-picker">{{ createForm.deadline }}</view></picker></view></view>
          <view class="severity-row"><button v-for="item in ['NORMAL','WARNING','DANGER']" :key="item" :class="{ active: createForm.severity === item }" @tap="createForm.severity = item as typeof createForm.severity">{{ severityLabel(item) }}</button></view>
          <view class="form-field photo-field"><text class="form-label">问题照片 *</text><button class="photo-picker" :disabled="submitting" @tap="choosePhotos('create')">+ 拍摄/选择照片</button><view class="quality-photo-grid"><view v-for="(path, index) in createPhotoPaths" :key="path" class="local-photo"><image :src="path" mode="aspectFill" @tap="previewLocalPhoto('create', path)" /><button @tap.stop="removeLocalPhoto('create', index)">×</button></view></view></view>
          <view class="form-actions"><button class="secondary-action" :disabled="submitting" @tap="closeSheet">取消</button><button class="primary-action" :disabled="submitting" @tap="submitCreate">确认发起</button></view>
        </template>
        <template v-else-if="sheetMode === 'documents'">
          <view class="document-source-note"><text>资料来源</text><text>由项目质量管理员在 PC 端维护，小程序仅支持查看</text></view>
          <view v-if="documentsLoading" class="empty-line">正在加载质量资料</view>
          <view v-else-if="documentsError" class="document-error"><text>{{ documentsError }}</text><button @tap="openDocuments">重新加载</button></view>
          <view v-else class="document-list"><button v-for="file in qualityDocuments" :key="file.id" :disabled="openingDocumentId !== null" @tap="openDocument(file)"><view class="document-icon">文</view><view><text>{{ file.fileName }}</text><text>{{ file.fileType || '质量资料' }} · {{ formatTime(file.createTime) }}</text></view><text class="row-arrow"></text></button><view v-if="!qualityDocuments.length" class="empty-line">当前区域暂无有效质量资料</view></view>
        </template>
        <template v-else-if="sheetMode === 'todos'">
          <view v-if="todosLoading" class="empty-line">正在加载质量待办</view>
          <view v-else-if="todosError" class="document-error"><text>{{ todosError }}</text><button @tap="loadQualityTodos()">重新加载</button></view>
          <view v-else class="todo-list">
            <button v-for="todo in qualityTodos" :key="`${todo.type}-${todo.targetId}`" @tap="openTodoIssue(todo)">
              <view class="todo-mark" :class="{ recheck: todo.type === 'RECHECK' }">{{ todo.type === 'RECHECK' ? '查' : '改' }}</view>
              <view><text>{{ todo.title }}</text><text>{{ todo.projectName }} · {{ todo.installLocation || '未设置位置' }}</text><text :class="{ overdue: todo.priority === 'danger' }">{{ todo.dueText }}</text></view>
              <text class="row-arrow"></text>
            </button>
            <view v-if="!qualityTodos.length" class="empty-line">当前项目暂无质量待办</view>
          </view>
        </template>
        <template v-else-if="sheetMode === 'assign' && selectedIssue">
          <view class="assign-summary"><text>{{ selectedIssue.title }}</text><text>{{ selectedIssue.issueNo }} · {{ statusLabel(selectedIssue) }}</text></view>
          <view class="form-field"><text class="form-label">整改负责人 *</text><picker :range="assignees" range-key="displayName" :value="Math.max(assignForm.assigneeIndex, 0)" @change="setAssignAssignee"><view class="form-picker">{{ assignees[assignForm.assigneeIndex]?.displayName || '请选择整改负责人' }}</view></picker></view>
          <view class="form-field"><text class="form-label">闭环期限 *</text><picker mode="date" :value="assignForm.deadline" @change="setAssignDeadline"><view class="form-picker">{{ assignForm.deadline || '请选择闭环期限' }}</view></picker></view>
          <view class="form-field"><text class="form-label">调整说明</text><textarea v-model="assignForm.comment" class="form-textarea" maxlength="1000" :disabled="submitting" placeholder="填写改派或调整期限原因（可选）" /></view>
          <view class="form-actions"><button class="secondary-action" :disabled="submitting" @tap="closeSheet">取消</button><button class="primary-action" :disabled="submitting" @tap="submitAssign">确认调整</button></view>
        </template>
        <template v-else-if="sheetMode === 'void' && selectedIssue">
          <view class="assign-summary"><text>{{ selectedIssue.title }}</text><text>{{ selectedIssue.issueNo }} · {{ statusLabel(selectedIssue) }}</text></view>
          <view class="form-field"><text class="form-label">作废原因 *</text><textarea v-model="voidComment" class="form-textarea" maxlength="1000" :disabled="submitting" placeholder="说明误建、重复或不属于质量问题等原因" /></view>
          <view class="form-actions"><button class="secondary-action" :disabled="submitting" @tap="closeSheet">取消</button><button class="danger-action" :disabled="submitting" @tap="submitVoid">确认作废</button></view>
        </template>
        <template v-else-if="selectedIssue">
          <view class="issue-detail-head"><WorkspaceStatusPill :label="statusLabel(selectedIssue)" :tone="statusTone(selectedIssue)" /><text>{{ selectedIssue.issueNo }}</text></view>
          <text class="issue-detail-title">{{ selectedIssue.title }}</text>
          <view class="detail-lines"><view><text>问题位置</text><text>{{ selectedIssue.location || '未设置' }}</text></view><view><text>整改负责人</text><text>{{ selectedIssue.assigneeName || '未指定' }}</text></view><view><text>闭环期限</text><text>{{ selectedIssue.deadline || '未设置' }}</text></view><view><text>问题描述</text><text>{{ selectedIssue.description || '无' }}</text></view></view>
          <view v-if="canManage && !['CLOSED', 'VOIDED'].includes(selectedIssue.status)" class="management-actions"><button class="outline-action" @tap="openAssign">改派 / 调整闭环期限</button><button class="outline-action danger" @tap="openVoid">作废问题</button></view>
          <view class="evidence-section">
            <view class="evidence-heading"><text>现场与整改证据</text><button v-if="evidenceFailureCount" @tap="loadEvidenceGroups">重新加载</button></view>
            <view v-if="evidenceLoading" class="evidence-loading">正在加载证据附件</view>
            <view v-for="group in evidenceGroups" :key="group.key" class="evidence-group">
              <view class="evidence-group-head"><text>{{ group.title }}</text><text>{{ group.operator || '-' }} · {{ formatTime(group.time) }}</text></view>
              <view v-if="group.paths.length" class="quality-photo-grid"><image v-for="path in group.paths" :key="path" :src="path" mode="aspectFill" @tap="previewEvidencePhoto(group, path)" /></view>
              <text v-if="group.failedCount" class="evidence-error">{{ group.failedCount }} 个附件加载失败，复查操作已暂停</text>
            </view>
            <view v-if="!evidenceLoading && !evidenceGroups.length" class="empty-line">暂无证据附件</view>
          </view>
          <view v-if="selectedIssue.canRectify && canRectify" class="form-field action-field"><text class="form-label">整改说明 *</text><textarea v-model="rectificationText" class="form-textarea" maxlength="1000" :disabled="submitting" placeholder="填写整改措施和结果" /><button class="photo-picker" :disabled="submitting" @tap="choosePhotos('rectification')">+ 整改照片 *</button><view class="quality-photo-grid"><view v-for="(path, index) in rectificationPhotoPaths" :key="path" class="local-photo"><image :src="path" mode="aspectFill" @tap="previewLocalPhoto('rectification', path)" /><button @tap.stop="removeLocalPhoto('rectification', index)">×</button></view></view><button class="primary-action single-action" :disabled="submitting" @tap="submitRectification">提交复查</button></view>
          <view v-if="selectedIssue.canReview && canReview" class="form-field action-field"><text class="form-label">复查意见</text><textarea v-model="reviewComment" class="form-textarea" maxlength="1000" :disabled="submitting" placeholder="退回时必须填写意见" /><button class="photo-picker" :disabled="submitting" @tap="choosePhotos('review')">+ 复查照片（可选）</button><view class="quality-photo-grid"><view v-for="(path, index) in reviewPhotoPaths" :key="path" class="local-photo"><image :src="path" mode="aspectFill" @tap="previewLocalPhoto('review', path)" /><button @tap.stop="removeLocalPhoto('review', index)">×</button></view></view><view class="form-actions"><button class="secondary-action reject" :disabled="submitting" @tap="submitReview(false)">退回整改</button><button class="primary-action" :disabled="submitting || evidenceLoading || evidenceFailureCount > 0" @tap="submitReview(true)">复查通过</button></view></view>
          <view v-if="selectedIssue.rectificationDescription" class="rectification-result"><text>最近整改说明</text><text>{{ selectedIssue.rectificationDescription }}</text></view>
          <view class="quality-timeline"><text class="timeline-title">操作留痕</text><view v-for="log in selectedIssue.logs || []" :key="log.id"><text>{{ actionLabel(log.actionType) }}</text><text>{{ log.operatorName || '-' }} · {{ formatTime(log.createTime) }}</text><text>{{ log.comment || '无补充说明' }}</text></view><text v-if="!(selectedIssue.logs || []).length" class="empty-line">暂无留痕</text></view>
        </template>
      </view>
    </view>
  </view>
</template>

<style scoped src="../../styles/workspace-page.css"></style>
<style scoped>
.quality-primary {
  box-sizing: border-box;
  display: flex;
  width: 100%;
  min-height: 116rpx;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  margin: 0;
  padding: 20rpx 22rpx;
  border: 0;
  border-radius: 18rpx;
  background: var(--page-accent-deep);
  box-shadow: 0 12rpx 28rpx rgba(49, 95, 134, .24);
  color: #fff;
  text-align: left;
  transition: box-shadow 100ms ease, transform 100ms ease;
}

.quality-primary::after { border: 0; }
.quality-primary:active { box-shadow: 0 7rpx 18rpx rgba(49, 95, 134, .2); transform: scale(.985); }
.quality-primary-main { display: flex; min-width: 0; align-items: center; flex: 1; gap: 16rpx; }
.quality-primary .action-icon-wrap {
  width: 62rpx;
  height: 62rpx;
  flex-shrink: 0;
  border-radius: 14rpx;
  background: rgba(255, 255, 255, .16);
}

.quality-primary image {
  width: 40rpx;
  height: 40rpx;
  filter: brightness(0) invert(1);
}

.quality-primary-copy { display: flex; min-width: 0; flex: 1; flex-direction: column; }
.quality-primary-title { color: #fff; font-size: 27rpx; font-weight: 800; line-height: 1.3; }
.quality-primary-subtitle { margin-top: 6rpx; color: rgba(255, 255, 255, .78); font-size: 20rpx; font-weight: 500; line-height: 1.35; }
.quality-primary-cta { display: flex; align-items: center; flex-shrink: 0; gap: 8rpx; color: #fff; font-size: 21rpx; font-weight: 750; white-space: nowrap; }
.quality-primary-arrow,
.quality-document-arrow {
  width: 11rpx;
  height: 11rpx;
  flex-shrink: 0;
  border-top: 2rpx solid currentColor;
  border-right: 2rpx solid currentColor;
  transform: rotate(45deg);
}

.quality-primary[disabled] {
  background: #d8e0e6;
  box-shadow: none;
  color: #6f7f8e;
  opacity: 1;
}

.quality-primary[disabled] .action-icon-wrap { background: rgba(255, 255, 255, .46); }
.quality-primary[disabled] image { filter: grayscale(1) brightness(.86); opacity: .65; }
.quality-primary[disabled] .quality-primary-title { color: #5e6f7e; }
.quality-primary[disabled] .quality-primary-subtitle { color: #7a8996; }
.quality-primary[disabled] .quality-primary-cta { color: #7a8996; }
.my-todo-banner { display: flex; width: 100%; min-height: 72rpx; align-items: center; justify-content: space-between; margin: 0; padding: 12rpx 18rpx; border: 1rpx solid var(--workspace-divider); border-radius: 14rpx; background: #fff; color: var(--workspace-text); text-align: left; }
.my-todo-banner::after { border: 0; }
.my-todo-banner > view { display: flex; align-items: center; gap: 10rpx; }
.my-todo-banner > view:first-child { min-width: 0; flex: 1; flex-direction: column; align-items: flex-start; gap: 2rpx; }
.my-todo-banner > view:first-child text:first-child { font-size: 21rpx; font-weight: 750; }
.my-todo-banner > view:first-child text:last-child { color: var(--workspace-text-muted); font-size: 18rpx; }
.my-todo-banner > view:last-child { color: var(--page-accent-deep); font-size: 21rpx; font-weight: 800; }

.list-section .section-head { border-radius: 16rpx 16rpx 0 0; }
.section-heading { min-width: 0; flex: 1; }
.section-title-line { display: flex; min-width: 0; align-items: center; flex-wrap: wrap; gap: 10rpx; }
.quality-document-link {
  display: flex;
  min-height: 56rpx;
  align-items: center;
  flex-shrink: 0;
  gap: 7rpx;
  margin: 0;
  padding: 8rpx 0 8rpx 12rpx;
  border: 0;
  background: transparent;
  color: var(--page-accent-deep);
  font-size: 21rpx;
  font-weight: 750;
  line-height: 1;
}
.quality-document-link::after { border: 0; }
.quality-document-link:active { opacity: .62; }
.list-section :deep(.segment-control) { margin: 16rpx 20rpx 0; }
.list-section .search-box { margin-top: 13rpx; }
.search-submit { min-height: 54rpx; margin: 0; padding: 0 12rpx; border: 0; background: transparent; color: var(--page-accent-deep); font-size: 20rpx; font-weight: 750; }
.search-submit::after { border: 0; }
.page-tail { padding: 22rpx 0 10rpx; color: var(--workspace-text-muted); font-size: 19rpx; text-align: center; }
.issue-row { min-height: 118rpx; align-items: flex-start; }
.issue-title-line { display: flex; min-width: 0; align-items: center; gap: 9rpx; }
.issue-title-line .plain-title { min-width: 0; }
.severity-tag { flex-shrink: 0; padding: 3rpx 8rpx; border-radius: 999rpx; background: #eef3f7; color: #647586; font-size: 17rpx; font-weight: 700; line-height: 1.25; }
.severity-tag.warning { background: #fff2dd; color: #9b621d; }
.severity-tag.danger { background: #fde8e8; color: #ac4646; }
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
.local-photo { position: relative; min-width: 0; }
.local-photo button { position: absolute; top: -8rpx; right: -8rpx; display: flex; width: 36rpx; height: 36rpx; align-items: center; justify-content: center; margin: 0; padding: 0; border: 2rpx solid #fff; border-radius: 999rpx; background: rgba(34, 50, 71, .82); color: #fff; font-size: 25rpx; line-height: 1; }
.local-photo button::after { border: 0; }
.detail-photos { margin: 18rpx 0; }
.evidence-section { margin: 18rpx 0; padding: 16rpx 18rpx; border-radius: 14rpx; background: #f7f9fb; }
.evidence-heading { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; }
.evidence-heading > text { color: var(--workspace-text); font-size: 22rpx; font-weight: 750; }
.evidence-heading button { min-height: 50rpx; margin: 0; padding: 0 14rpx; border: 0; background: var(--page-tint); color: var(--page-accent-deep); font-size: 19rpx; }
.evidence-heading button::after { border: 0; }
.evidence-loading { padding: 24rpx 0; color: var(--workspace-text-muted); font-size: 20rpx; text-align: center; }
.evidence-group { padding: 15rpx 0; border-bottom: 1rpx solid var(--workspace-divider); }
.evidence-group:last-child { border-bottom: 0; }
.evidence-group-head text { display: block; color: var(--workspace-text-muted); font-size: 19rpx; line-height: 1.4; }
.evidence-group-head text:first-child { color: var(--page-accent-deep); font-size: 21rpx; font-weight: 750; }
.evidence-error { display: block; margin-top: 10rpx; color: #b94f4f; font-size: 19rpx; line-height: 1.4; }
.document-source-note { margin-bottom: 8rpx; padding: 16rpx 18rpx; border-radius: 12rpx; background: var(--page-tint); }
.document-source-note text { display: block; color: var(--workspace-text-secondary); font-size: 20rpx; line-height: 1.5; }
.document-source-note text:first-child { margin-bottom: 4rpx; color: var(--page-accent-deep); font-weight: 750; }
.assign-summary { margin-bottom: 18rpx; padding: 16rpx 18rpx; border-radius: 12rpx; background: var(--page-tint); }
.assign-summary text { display: block; color: var(--workspace-text-muted); font-size: 20rpx; line-height: 1.45; }
.assign-summary text:first-child { color: var(--workspace-text); font-size: 23rpx; font-weight: 750; }
.outline-action { width: 100%; min-height: 64rpx; margin: 14rpx 0 0; border: 1rpx solid var(--page-accent); border-radius: 12rpx; background: #fff; color: var(--page-accent-deep); font-size: 21rpx; font-weight: 750; }
.outline-action::after { border: 0; }
.management-actions { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, .62fr); gap: 12rpx; }
.outline-action.danger { border-color: #e1aaaa; color: #ad4545; }
.danger-action { min-height: 68rpx; border: 0; border-radius: 12rpx; background: #b94f4f; color: #fff; font-size: 22rpx; font-weight: 750; }
.danger-action::after { border: 0; }
.document-error { padding: 34rpx 0; text-align: center; }
.document-error text { display: block; color: var(--workspace-text-secondary); font-size: 21rpx; }
.document-error button { display: inline-flex; min-height: 58rpx; align-items: center; margin-top: 16rpx; padding: 0 24rpx; border: 0; border-radius: 12rpx; background: var(--page-tint); color: var(--page-accent-deep); font-size: 21rpx; font-weight: 750; }
.document-error button::after { border: 0; }
.todo-list button { display: flex; width: 100%; min-height: 104rpx; align-items: center; gap: 14rpx; margin: 0; padding: 14rpx 0; border: 0; border-bottom: 1rpx solid var(--workspace-divider); background: #fff; text-align: left; }
.todo-list button::after { border: 0; }
.todo-list button > view:nth-child(2) { min-width: 0; flex: 1; }
.todo-list button > view:nth-child(2) text { display: block; color: var(--workspace-text-muted); font-size: 19rpx; line-height: 1.4; }
.todo-list button > view:nth-child(2) text:first-child { color: var(--workspace-text); font-size: 22rpx; font-weight: 750; }
.todo-list button > view:nth-child(2) text.overdue { color: #b94f4f; }
.todo-mark { display: flex; width: 48rpx; height: 48rpx; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 12rpx; background: #fff1df; color: #a96822; font-size: 21rpx; font-weight: 800; }
.todo-mark.recheck { background: var(--page-tint); color: var(--page-accent-deep); }
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
  .quality-primary { gap: 14rpx; padding-right: 18rpx; padding-left: 18rpx; }
  .quality-primary-cta { font-size: 20rpx; }
  .form-grid { grid-template-columns: 1fr; }
}
</style>
