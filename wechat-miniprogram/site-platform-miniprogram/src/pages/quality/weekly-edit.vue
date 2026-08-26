<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { onBackPress, onLoad } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import {
  createOrRestoreQualityWeeklyDraft,
  discardQualityWeeklyDraft,
  getQualityAssignees,
  saveQualityWeeklyDraft,
  submitQualityWeeklyInspection,
  type QualityWeeklyDraftItemPayload
} from '@/api/quality';
import { ApiRequestError } from '@/api/request';
import { deleteFileResources, downloadFileResults, uploadPhotoIds } from '@/api/file';
import { useAuthStore } from '@/stores/auth';
import { useProjectStore } from '@/stores/project';
import type { QualityAssignee, QualityWeeklyDraftItem, QualityWeeklyInspection } from '@/types';
import { getQueryNumber, showToast } from '@/utils/navigation';

const MAX_ITEMS = 50;
const MAX_PHOTOS = 20;

interface EditableDraftItem {
  itemKey: string;
  title: string;
  location: string;
  description: string;
  severity: 'NORMAL' | 'WARNING' | 'DANGER';
  assigneeId?: number;
  deadline: string;
  beforePhotoFileIds: number[];
  beforePhotoPaths: string[];
  newBeforePhotoPaths: string[];
}

const authStore = useAuthStore();
const projectStore = useProjectStore();
const projectId = ref(0);
const draft = ref<QualityWeeklyInspection>();
const assignees = ref<QualityAssignee[]>([]);
const loading = ref(true);
const loadError = ref('');
const saving = ref(false);
const submitting = ref(false);
const discarding = ref(false);
const dirty = ref(false);
const hydrating = ref(false);
const allowBack = ref(false);
const selectedWeekDate = ref('');
const form = reactive({ inspectionDate: '', conclusion: '' });
const overviewPhotoFileIds = ref<number[]>([]);
const overviewPhotoPaths = ref<string[]>([]);
const newOverviewPhotoPaths = ref<string[]>([]);
const items = ref<EditableDraftItem[]>([]);
const initialQuery = ref<Record<string, string | undefined>>({});

const currentProject = computed(() => projectStore.state.projects.find((item) => item.id === projectId.value));
const today = computed(() => formatLocalDate(new Date()));
const overviewCount = computed(() => overviewPhotoFileIds.value.length + newOverviewPhotoPaths.value.length);
const isBusy = computed(() => loading.value || saving.value || submitting.value || discarding.value);
const weekRangeText = computed(() => draft.value ? `${draft.value.weekStart} 至 ${draft.value.weekEnd}` : '');
const inspectionDateEnd = computed(() => draft.value?.weekEnd && draft.value.weekEnd < today.value
  ? draft.value.weekEnd : today.value);

watch([form, overviewPhotoFileIds, newOverviewPhotoPaths, items], () => {
  if (!hydrating.value && !loading.value) dirty.value = true;
}, { deep: true });

onLoad(async (query) => {
  initialQuery.value = { ...(query || {}) };
  await initialize(initialQuery.value);
});

async function initialize(query: Record<string, string | undefined>) {
  loading.value = true;
  loadError.value = '';
  try {
    await projectStore.loadProjects();
    projectId.value = getQueryNumber(query?.projectId, projectStore.state.currentProjectId || 0);
    if (!projectId.value) throw new Error('未找到当前施工区域');
    const requestedWeek = normalizeWeekStart(String(query?.weekStart || today.value));
    if (requestedWeek > normalizeWeekStart(today.value)) throw new Error('不能创建未来周的质量周检');
    if (!await authStore.ensureProjectPermission('/pages/quality/index', projectId.value, 'quality.manage')) {
      loadError.value = '当前账号暂无质量周检管理权限';
      return;
    }
    assignees.value = await getQualityAssignees(projectId.value);
    await loadWeek(requestedWeek);
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '周检草稿加载失败';
    showToast(loadError.value);
  } finally {
    loading.value = false;
  }
}

async function retryLoad() {
  if (loading.value) return;
  await initialize(initialQuery.value);
}

function formatLocalDate(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function parseLocalDate(value: string) {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year, month - 1, day);
}

function normalizeWeekStart(value: string) {
  const date = parseLocalDate(value);
  const day = date.getDay();
  date.setDate(date.getDate() - (day === 0 ? 6 : day - 1));
  return formatLocalDate(date);
}

function createItem(): EditableDraftItem {
  return {
    itemKey: `mini-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`,
    title: '', location: '', description: '', severity: 'NORMAL', deadline: '',
    beforePhotoFileIds: [], beforePhotoPaths: [], newBeforePhotoPaths: []
  };
}

async function downloadPaths(ids: number[]) {
  const results = await downloadFileResults(ids);
  return results.map((result) => result.path || '');
}

async function hydrateDraft(value: QualityWeeklyInspection) {
  hydrating.value = true;
  draft.value = value;
  selectedWeekDate.value = value.weekStart;
  form.inspectionDate = value.inspectionDate || (value.weekEnd < today.value ? value.weekEnd : today.value);
  form.conclusion = value.conclusion || '';
  overviewPhotoFileIds.value = [...(value.overviewPhotoFileIds || [])];
  overviewPhotoPaths.value = await downloadPaths(overviewPhotoFileIds.value);
  newOverviewPhotoPaths.value = [];
  items.value = await Promise.all((value.draftItems || []).map(async (item: QualityWeeklyDraftItem) => ({
    itemKey: item.itemKey,
    title: item.title || '',
    location: item.location || '',
    description: item.description || '',
    severity: item.severity || 'NORMAL',
    assigneeId: item.assigneeId,
    deadline: item.deadline || '',
    beforePhotoFileIds: [...(item.beforePhotoFileIds || [])],
    beforePhotoPaths: await downloadPaths(item.beforePhotoFileIds || []),
    newBeforePhotoPaths: []
  })));
  dirty.value = false;
  hydrating.value = false;
}

async function loadWeek(weekStart: string) {
  loading.value = true;
  try {
    const value = await createOrRestoreQualityWeeklyDraft(projectId.value, weekStart);
    if (value.status === 'SUBMITTED') {
      uni.redirectTo({ url: `/pages/quality/weekly-detail?id=${value.id}` });
      return;
    }
    await hydrateDraft(value);
  } finally {
    loading.value = false;
  }
}

async function changeWeek(event: unknown) {
  if (isBusy.value) { showToast('正在处理周检，请稍候'); return; }
  const value = String((event as { detail?: { value?: string } }).detail?.value || '');
  if (!value) return;
  const weekStart = normalizeWeekStart(value);
  if (weekStart > normalizeWeekStart(today.value)) {
    showToast('不能选择未来周');
    return;
  }
  if (weekStart === draft.value?.weekStart) return;
  if (dirty.value && !await confirmDialog('切换周次', '当前页面有未保存内容，切换后将保留服务器上最近一次草稿。是否继续？')) return;
  await loadWeek(weekStart).catch((error) => showToast(error instanceof Error ? error.message : '周次切换失败'));
}

function setInspectionDate(event: unknown) {
  form.inspectionDate = String((event as { detail?: { value?: string } }).detail?.value || '');
}

function addItem() {
  if (items.value.length >= MAX_ITEMS) { showToast(`每次周检最多录入 ${MAX_ITEMS} 个问题`); return; }
  items.value.push(createItem());
}

async function removeItem(index: number) {
  const item = items.value[index];
  if (!item) return;
  const hasContent = Boolean(item.title.trim() || item.location.trim() || item.description.trim()
    || item.beforePhotoFileIds.length || item.newBeforePhotoPaths.length);
  if (hasContent && !await confirmDialog('删除问题', `确认删除问题 ${index + 1}？保存草稿后服务器中的该问题也会移除。`)) return;
  items.value.splice(index, 1);
}

function copyPrevious(index: number) {
  if (index <= 0) { showToast('没有上一题可复制'); return; }
  const previous = items.value[index - 1];
  const current = items.value[index];
  current.severity = previous.severity;
  current.assigneeId = previous.assigneeId;
  current.deadline = previous.deadline;
  showToast('已复制上一题的等级、负责人和期限');
}

function assigneeIndex(item: EditableDraftItem) {
  return Math.max(0, assignees.value.findIndex((assignee) => assignee.userId === item.assigneeId));
}

function setItemAssignee(index: number, event: unknown) {
  const optionIndex = Number((event as { detail?: { value?: number | string } }).detail?.value || 0);
  items.value[index].assigneeId = assignees.value[optionIndex]?.userId;
}

function setItemDeadline(index: number, event: unknown) {
  items.value[index].deadline = String((event as { detail?: { value?: string } }).detail?.value || '');
}

function chooseImages(remaining: number): Promise<string[]> {
  if (remaining <= 0) { showToast(`最多上传 ${MAX_PHOTOS} 张照片`); return Promise.resolve([]); }
  return new Promise((resolve) => {
    uni.chooseImage({
      count: Math.min(9, remaining), sizeType: ['compressed'], sourceType: ['camera', 'album'],
      success: (result) => resolve(Array.isArray(result.tempFilePaths)
        ? result.tempFilePaths
        : result.tempFilePaths ? [result.tempFilePaths] : []),
      fail: () => resolve([])
    });
  });
}

async function chooseOverviewPhotos() {
  const paths = await chooseImages(MAX_PHOTOS - overviewCount.value);
  newOverviewPhotoPaths.value.push(...paths.slice(0, MAX_PHOTOS - overviewCount.value));
}

async function chooseItemPhotos(index: number) {
  const item = items.value[index];
  const paths = await chooseImages(MAX_PHOTOS - item.beforePhotoFileIds.length - item.newBeforePhotoPaths.length);
  item.newBeforePhotoPaths.push(...paths.slice(0, MAX_PHOTOS - item.beforePhotoFileIds.length - item.newBeforePhotoPaths.length));
}

function removeOverviewPhoto(index: number) {
  if (index < overviewPhotoFileIds.value.length) {
    overviewPhotoFileIds.value.splice(index, 1);
    overviewPhotoPaths.value.splice(index, 1);
  } else {
    newOverviewPhotoPaths.value.splice(index - overviewPhotoFileIds.value.length, 1);
  }
}

function removeItemPhoto(item: EditableDraftItem, index: number) {
  if (index < item.beforePhotoFileIds.length) {
    item.beforePhotoFileIds.splice(index, 1);
    item.beforePhotoPaths.splice(index, 1);
  } else {
    item.newBeforePhotoPaths.splice(index - item.beforePhotoFileIds.length, 1);
  }
}

function overviewPaths() { return [...overviewPhotoPaths.value, ...newOverviewPhotoPaths.value]; }
function itemPaths(item: EditableDraftItem) { return [...item.beforePhotoPaths, ...item.newBeforePhotoPaths]; }

function preview(paths: string[], current: string) {
  if (!current) return;
  const available = paths.filter(Boolean);
  if (available.length) uni.previewImage({ urls: available, current });
}

function draftPayloadItems(uploadedByKey: Map<string, number[]>): QualityWeeklyDraftItemPayload[] {
  return items.value.map((item, index) => ({
    itemKey: item.itemKey,
    itemOrder: index + 1,
    title: item.title.trim(),
    location: item.location.trim(),
    description: item.description.trim(),
    severity: item.severity,
    assigneeId: item.assigneeId,
    deadline: item.deadline || undefined,
    beforePhotoFileIds: [...item.beforePhotoFileIds, ...(uploadedByKey.get(item.itemKey) || [])]
  }));
}

async function persistDraft(showSuccess = true): Promise<QualityWeeklyInspection | null> {
  if (!draft.value || saving.value) return null;
  if (items.value.length > MAX_ITEMS) { showToast(`每次周检最多录入 ${MAX_ITEMS} 个问题`); return null; }
  saving.value = true;
  const uploadedIds: number[] = [];
  const uploadedByKey = new Map<string, number[]>();
  try {
    const newOverviewIds = await uploadPhotoIds(newOverviewPhotoPaths.value, '质量周检现场照片', {
      projectId: projectId.value, businessType: 'QUALITY_WEEKLY_PENDING'
    });
    uploadedIds.push(...newOverviewIds);
    for (const item of items.value) {
      const ids = await uploadPhotoIds(item.newBeforePhotoPaths, '质量周检问题照片', {
        projectId: projectId.value, businessType: 'QUALITY_WEEKLY_ITEM_PENDING'
      });
      uploadedIds.push(...ids);
      uploadedByKey.set(item.itemKey, ids);
    }
    const saved = await saveQualityWeeklyDraft(draft.value.id, {
      expectedVersion: draft.value.version,
      inspectionDate: form.inspectionDate || undefined,
      conclusion: form.conclusion.trim(),
      overviewPhotoFileIds: [...overviewPhotoFileIds.value, ...newOverviewIds],
      items: draftPayloadItems(uploadedByKey)
    });
    await hydrateDraft(saved);
    if (showSuccess) showToast('周检草稿已保存');
    return saved;
  } catch (error) {
    await deleteFileResources(uploadedIds);
    if (error instanceof ApiRequestError && error.statusCode === 409) {
      showToast('草稿已被其他人员更新，本页内容仍保留，请返回后重新进入核对');
    } else {
      showToast(error instanceof Error ? error.message : '草稿保存失败');
    }
    return null;
  } finally {
    saving.value = false;
  }
}

function validateForSubmit() {
  if (!form.inspectionDate) return '请选择检查日期';
  if (form.inspectionDate > today.value) return '检查日期不能晚于今天';
  if (draft.value && (form.inspectionDate < draft.value.weekStart || form.inspectionDate > draft.value.weekEnd)) return '检查日期必须位于所选周次内';
  if (!items.value.length) {
    if (!form.conclusion.trim()) return '无问题周检必须填写检查结论';
    if (!overviewCount.value) return '无问题周检必须上传至少一张现场照片';
  }
  for (let index = 0; index < items.value.length; index += 1) {
    const item = items.value[index];
    const label = `问题 ${index + 1}`;
    if (!item.title.trim()) return `${label}请填写标题`;
    if (!item.assigneeId) return `${label}请选择整改负责人`;
    if (!item.deadline) return `${label}请选择闭环期限`;
    if (item.deadline < today.value) return `${label}闭环期限不能早于提交日`;
    const photoCount = item.beforePhotoFileIds.length + item.newBeforePhotoPaths.length;
    if (!photoCount) return `${label}至少需要一张整改前照片`;
    if (photoCount > MAX_PHOTOS) return `${label}照片不能超过 ${MAX_PHOTOS} 张`;
  }
  return '';
}

async function submitInspection() {
  if (isBusy.value || !draft.value) return;
  const validationMessage = validateForSubmit();
  if (validationMessage) { showToast(validationMessage); return; }
  const confirmed = await confirmDialog('提交质量周检', `将一次提交 ${items.value.length} 个问题。提交后周检不可修改，各问题将独立进入整改闭环。`);
  if (!confirmed) return;
  const saved = await persistDraft(false);
  if (!saved) return;
  submitting.value = true;
  try {
    const result = await submitQualityWeeklyInspection(saved.id, saved.version);
    dirty.value = false;
    showToast('质量周检已提交');
    setTimeout(() => uni.redirectTo({ url: `/pages/quality/weekly-detail?id=${result.id}` }), 350);
  } catch (error) {
    if (error instanceof ApiRequestError && error.statusCode === 409) showToast('草稿版本已变化，请返回后重新进入核对');
    else showToast(error instanceof Error ? error.message : '周检提交失败');
  } finally {
    submitting.value = false;
  }
}

async function discardDraft() {
  if (!draft.value || isBusy.value) return;
  if (!await confirmDialog('放弃共享草稿', '放弃后当前项目成员都无法再查看本周草稿，已上传的草稿照片也会清理。')) return;
  discarding.value = true;
  try {
    await discardQualityWeeklyDraft(draft.value.id, draft.value.version);
    dirty.value = false;
    allowBack.value = true;
    showToast('周检草稿已放弃');
    setTimeout(() => uni.navigateBack(), 300);
  } catch (error) {
    showToast(error instanceof Error ? error.message : '放弃草稿失败');
  } finally {
    discarding.value = false;
  }
}

function confirmDialog(title: string, content: string): Promise<boolean> {
  return new Promise((resolve) => uni.showModal({
    title, content, confirmText: '确认', cancelText: '取消',
    success: (result) => resolve(Boolean(result.confirm)), fail: () => resolve(false)
  }));
}

function goBack() {
  if (isBusy.value) { showToast('正在保存或提交，请稍候'); return; }
  if (!dirty.value) { uni.navigateBack(); return; }
  void confirmDialog('尚未保存', '当前整理内容尚未保存到共享草稿，确认离开？').then((confirmed) => {
    if (!confirmed) return;
    allowBack.value = true;
    uni.navigateBack();
  });
}

onBackPress(() => {
  if (allowBack.value || !dirty.value) return false;
  goBack();
  return true;
});
</script>

<template>
  <view class="weekly-edit-page">
    <AppNavBar title="编辑质量周检" @back="goBack" />
    <view v-if="loading" class="state-card">正在加载共享草稿...</view>
    <view v-else-if="loadError" class="state-card error-state"><text>{{ loadError }}</text><button @tap="retryLoad">重新加载</button></view>
    <scroll-view v-else-if="draft" class="editor-scroll" scroll-y>
      <view class="editor-content">
        <view class="project-card">
          <text>{{ currentProject?.projectName || currentProject?.shortName || '当前施工区域' }}</text>
          <text>共享草稿 · 版本 {{ draft.version }}</text>
          <text v-if="draft.lastEditedByName">最近由 {{ draft.lastEditedByName }} 编辑 · {{ String(draft.updateTime || '').replace('T', ' ').slice(0, 16) }}</text>
        </view>

        <view class="form-card">
          <view class="field"><text class="label">周检周次 *</text><picker mode="date" :disabled="isBusy" :value="selectedWeekDate" :end="today" @change="changeWeek"><view class="picker-value">{{ weekRangeText || '请选择周次' }}</view></picker><text class="hint">选择任意日期后按周一至周日归入周检，禁止未来周</text></view>
          <view class="field"><text class="label">实际检查日期 *</text><picker mode="date" :disabled="isBusy" :value="form.inspectionDate" :start="draft.weekStart" :end="inspectionDateEnd" @change="setInspectionDate"><view class="picker-value">{{ form.inspectionDate || '请选择检查日期' }}</view></picker></view>
          <view class="field"><text class="label">检查结论{{ items.length ? '' : ' *' }}</text><textarea v-model="form.conclusion" :disabled="isBusy" maxlength="1000" placeholder="总结本周检查情况；无问题时必填" /></view>
          <view class="field"><text class="label">周检现场照片{{ items.length ? '（可选）' : ' *' }} · {{ overviewCount }}/{{ MAX_PHOTOS }}</text><button class="photo-picker" :disabled="isBusy || overviewCount >= MAX_PHOTOS" @tap="chooseOverviewPhotos">+ 拍摄/选择现场照片</button><view class="photo-grid"><view v-for="(path, index) in overviewPaths()" :key="`${path}-${index}`" class="photo"><image v-if="path" :src="path" mode="aspectFill" @tap="preview(overviewPaths(), path)" /><view v-else class="photo-failed">加载失败</view><button :disabled="isBusy" @tap.stop="removeOverviewPhoto(index)">×</button></view></view></view>
        </view>

        <view class="problem-head"><view><text>本次问题</text><text>{{ items.length }}/{{ MAX_ITEMS }} · 每题独立进入整改闭环</text></view><button :disabled="isBusy || items.length >= MAX_ITEMS" @tap="addItem">+ 添加问题</button></view>
        <view v-if="!items.length" class="empty-card"><text>本次可提交“未发现问题”周检</text><text>请填写检查结论并上传至少一张现场照片</text></view>

        <view v-for="(item, index) in items" :key="item.itemKey" class="problem-card">
          <view class="problem-title"><text>问题 {{ index + 1 }}</text><view><button v-if="index" :disabled="isBusy" @tap="copyPrevious(index)">复制上一题设置</button><button class="remove" :disabled="isBusy" @tap="removeItem(index)">删除</button></view></view>
          <view class="field"><text class="label">问题标题 *</text><input v-model="item.title" :disabled="isBusy" maxlength="200" placeholder="简要描述发现的问题" /></view>
          <view class="field"><text class="label">问题位置</text><input v-model="item.location" :disabled="isBusy" maxlength="200" placeholder="楼层、轴线或作业面" /></view>
          <view class="field"><text class="label">问题描述</text><textarea v-model="item.description" :disabled="isBusy" maxlength="1000" placeholder="说明现状和整改要求" /></view>
          <view class="field"><text class="label">严重程度 *</text><view class="severity-row"><button v-for="severity in ['NORMAL','WARNING','DANGER']" :key="severity" :disabled="isBusy" :class="{ active: item.severity === severity }" @tap="item.severity = severity as typeof item.severity">{{ severity === 'DANGER' ? '严重' : severity === 'WARNING' ? '重要' : '一般' }}</button></view></view>
          <view class="field-grid">
            <view class="field"><text class="label">整改负责人 *</text><picker :disabled="isBusy" :range="assignees" range-key="displayName" :value="assigneeIndex(item)" @change="setItemAssignee(index, $event)"><view class="picker-value">{{ assignees.find((option) => option.userId === item.assigneeId)?.displayName || '请选择负责人' }}</view></picker></view>
            <view class="field"><text class="label">闭环期限 *</text><picker mode="date" :disabled="isBusy" :value="item.deadline" :start="today" @change="setItemDeadline(index, $event)"><view class="picker-value">{{ item.deadline || '请选择期限' }}</view></picker></view>
          </view>
          <view class="field"><text class="label">整改前照片 * · {{ item.beforePhotoFileIds.length + item.newBeforePhotoPaths.length }}/{{ MAX_PHOTOS }}</text><button class="photo-picker" :disabled="isBusy || item.beforePhotoFileIds.length + item.newBeforePhotoPaths.length >= MAX_PHOTOS" @tap="chooseItemPhotos(index)">+ 拍摄/选择问题照片</button><view class="photo-grid"><view v-for="(path, photoIndex) in itemPaths(item)" :key="`${path}-${photoIndex}`" class="photo"><image v-if="path" :src="path" mode="aspectFill" @tap="preview(itemPaths(item), path)" /><view v-else class="photo-failed">加载失败</view><button :disabled="isBusy" @tap.stop="removeItemPhoto(item, photoIndex)">×</button></view></view></view>
        </view>

        <view class="danger-zone"><button :disabled="isBusy" @tap="discardDraft">放弃共享草稿</button></view>
      </view>
    </scroll-view>
    <view v-if="draft && !loading" class="action-bar"><button class="save" :disabled="isBusy" @tap="persistDraft(true)">{{ saving ? '正在保存...' : '保存草稿' }}</button><button class="submit" :disabled="isBusy" @tap="submitInspection">{{ submitting ? '正在提交...' : `提交周检（${items.length}个问题）` }}</button></view>
  </view>
</template>

<style scoped>
.weekly-edit-page { min-height: 100vh; background: #f4f6f7; color: #263449; }
.weekly-edit-page :deep(.app-nav) { background: rgba(255,255,255,.98); box-shadow: 0 1rpx 0 rgba(148,163,184,.18); }
.editor-scroll { height: calc(100vh - 154px); }
.editor-content { display: flex; flex-direction: column; gap: 18rpx; padding: 22rpx 24rpx calc(36rpx + env(safe-area-inset-bottom)); }
.state-card { margin: 26rpx; padding: 70rpx 24rpx; border-radius: 16rpx; background: #fff; color: #718092; font-size: 23rpx; text-align: center; }
.error-state text { display: block; margin-bottom: 24rpx; }
.error-state button { width: 240rpx; min-height: 68rpx; border: 0; border-radius: 12rpx; background: #e7f0f5; color: #3f6982; font-size: 22rpx; }
.error-state button::after { border: 0; }
.project-card,.form-card,.problem-card,.empty-card { padding: 22rpx; border-radius: 16rpx; background: #fff; box-shadow: 0 8rpx 26rpx rgba(43,56,72,.055); }
.project-card text { display: block; color: #7d8b9a; font-size: 20rpx; line-height: 1.5; }
.project-card text:first-child { color: #2d4054; font-size: 26rpx; font-weight: 800; }
.field { min-width: 0; margin-bottom: 20rpx; }
.field:last-child { margin-bottom: 0; }
.label { display: block; margin-bottom: 9rpx; color: #58697b; font-size: 22rpx; font-weight: 700; }
.field input,.field textarea,.picker-value { box-sizing: border-box; width: 100%; min-height: 74rpx; padding: 16rpx 18rpx; border: 1rpx solid #e1e7ec; border-radius: 12rpx; background: #f7f9fa; color: #2b3d50; font-size: 23rpx; }
.field textarea { min-height: 128rpx; }
.hint { display: block; margin-top: 8rpx; color: #8a98a7; font-size: 19rpx; line-height: 1.4; }
.field-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 12rpx; }
.problem-head { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; }
.problem-head view text { display: block; color: #7c8a99; font-size: 20rpx; }
.problem-head view text:first-child { color: #2b3e51; font-size: 27rpx; font-weight: 800; }
.problem-head button,.problem-title button { min-height: 58rpx; margin: 0; padding: 0 18rpx; border: 0; border-radius: 11rpx; background: #e7f0f5; color: #3f6982; font-size: 21rpx; font-weight: 750; }
.problem-head button::after,.problem-title button::after,.photo-picker::after,.photo button::after,.action-bar button::after,.danger-zone button::after { border: 0; }
.empty-card { text-align: center; }
.empty-card text { display: block; color: #83909f; font-size: 21rpx; line-height: 1.5; }
.empty-card text:first-child { color: #435a70; font-size: 24rpx; font-weight: 750; }
.problem-title { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; margin-bottom: 18rpx; padding-bottom: 14rpx; border-bottom: 1rpx solid #edf0f3; }
.problem-title > text { color: #315f86; font-size: 25rpx; font-weight: 800; }
.problem-title > view { display: flex; gap: 8rpx; }
.problem-title button { min-height: 48rpx; padding: 0 12rpx; font-size: 18rpx; }
.problem-title button.remove { background: #fff1f1; color: #af4d4d; }
.severity-row { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 9rpx; }
.severity-row button { min-height: 60rpx; border: 1rpx solid transparent; border-radius: 11rpx; background: #f1f4f6; color: #677789; font-size: 21rpx; }
.severity-row button::after { border: 0; }
.severity-row button.active { border-color: #bdd1df; background: #e8f1f6; color: #315f86; font-weight: 750; }
.photo-picker { width: 100%; min-height: 64rpx; border: 1rpx dashed #b7cbd8; border-radius: 12rpx; background: #f3f8fa; color: #456d84; font-size: 21rpx; font-weight: 700; }
.photo-grid { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 10rpx; margin-top: 12rpx; }
.photo { position: relative; min-width: 0; }
.photo image { width: 100%; height: 120rpx; border-radius: 10rpx; background: #eef2f4; }
.photo-failed { display: flex; width: 100%; height: 120rpx; align-items: center; justify-content: center; border-radius: 10rpx; background: #f2f4f5; color: #9a6d6d; font-size: 18rpx; }
.photo button { position: absolute; top: -8rpx; right: -8rpx; display: flex; width: 36rpx; height: 36rpx; align-items: center; justify-content: center; margin: 0; padding: 0; border: 2rpx solid #fff; border-radius: 50%; background: rgba(34,50,71,.82); color: #fff; font-size: 25rpx; }
.danger-zone { padding: 8rpx 0 14rpx; text-align: center; }
.danger-zone button { display: inline-flex; min-height: 58rpx; align-items: center; padding: 0 22rpx; background: transparent; color: #ad5252; font-size: 20rpx; }
.action-bar { position: fixed; right: 0; bottom: 0; left: 0; z-index: 20; display: grid; grid-template-columns: .78fr 1.35fr; gap: 12rpx; padding: 16rpx 22rpx calc(16rpx + env(safe-area-inset-bottom)); border-top: 1rpx solid #e4e9ed; background: rgba(255,255,255,.98); }
.action-bar button { min-height: 76rpx; border-radius: 13rpx; font-size: 23rpx; font-weight: 800; }
.action-bar .save { background: #edf3f6; color: #456b82; }
.action-bar .submit { background: #315f86; color: #fff; box-shadow: 0 8rpx 20rpx rgba(49,95,134,.2); }
.action-bar button[disabled] { opacity: .6; }
@media (max-width: 360px) { .field-grid { grid-template-columns: 1fr; gap: 0; } .photo-grid { grid-template-columns: repeat(3,minmax(0,1fr)); } }
</style>
