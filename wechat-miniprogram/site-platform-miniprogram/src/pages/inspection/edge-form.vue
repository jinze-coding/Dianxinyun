<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad, onShow, onUnload } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import {
  getEdgeInspectionTask,
  getEdgeInspectionUserOptions,
  reassignEdgeInspectionTask,
  submitEdgeInspectionTask,
  type EdgeInspectionResult,
  type EdgeInspectionTask,
  type EdgeInspectionTaskItem,
  type EdgeInspectionUserOption
} from '@/api/edgeInspection';
import { deleteFileResources, downloadFilePaths, uploadPhotoIds } from '@/api/file';
import { useAuthStore } from '@/stores/auth';
import { isEdgeTaskBeforeWindow, isEdgeTaskOverdue } from '@/utils/edgeInspectionView';
import { getQueryNumber, showToast, switchTab } from '@/utils/navigation';
import { usePageScrollHeight } from '@/utils/navLayout';

interface ItemDraft {
  result?: EdgeInspectionResult;
  description: string;
  photos: string[];
}

interface FormDraft {
  taskId: number;
  version: number;
  overallPhotos: string[];
  remark: string;
  items: Record<string, ItemDraft>;
  savedAt: number;
}

const authStore = useAuthStore();
const taskId = ref(0);
const task = ref<EdgeInspectionTask>();
const draft = ref<FormDraft>();
const loading = ref(false);
const submitting = ref(false);
const reassigning = ref(false);
const loadingCandidates = ref(false);
const reassignCandidates = ref<EdgeInspectionUserOption[]>([]);
const selectedAssigneeId = ref(0);
const reassignReason = ref('');
const candidateError = ref('');
const errorMessage = ref('');
const { scrollStyle } = usePageScrollHeight({ bottomRpx: 170, minHeight: 260 });
let saveTimer: ReturnType<typeof setTimeout> | undefined;
let suppressDraftSave = false;

const draftKey = computed(() => `edge_inspection_draft_${taskId.value}`);
const canSubmit = computed(() => task.value?.status === 'PENDING' && task.value?.canExecute === true);
const readonly = computed(() => !canSubmit.value);
const canReassign = computed(() => task.value?.status === 'PENDING'
  && task.value?.canManage === true
  && task.value?.canReassign === true);
const selectedCandidate = computed(() => reassignCandidates.value.find((item) => item.userId === selectedAssigneeId.value));
const pointTypeName = computed(() => task.value?.pointTypeName || task.value?.categoryName || '临边点位');
const executionSlot = computed(() => task.value ? formatExecutionSlot(task.value) : '-');
const taskStatusLabels: Record<string, string> = {
  PENDING: '待巡检',
  OVERDUE: '逾期未检',
  OVERDUE_PENDING: '逾期未检',
  OVERDUE_MISSED: '逾期未检',
  COMPLETED: '按时完成',
  LATE_COMPLETED: '逾期补检',
  OVERDUE_COMPLETED: '逾期补检',
  RECTIFICATION_PENDING: '整改中',
  RECTIFYING: '整改中',
  REVIEW_PENDING: '整改中',
  CLOSED: '已闭环',
  CANCELLED: '已取消'
};
const resultOptions: Array<{ value: EdgeInspectionResult; label: string }> = [
  { value: 'NORMAL', label: '正常' },
  { value: 'ABNORMAL', label: '异常' }
];

onLoad((options) => { taskId.value = getQueryNumber(options?.id, 0); });

onShow(async () => {
  if (!await authStore.ensureRootAccess('/pages/inspection/index')) return;
  await loadTask();
});

onUnload(() => {
  if (saveTimer) clearTimeout(saveTimer);
  if (!suppressDraftSave) saveDraftNow();
});

async function loadTask() {
  if (!taskId.value) { errorMessage.value = '缺少临边巡检任务编号'; return; }
  loading.value = true;
  errorMessage.value = '';
  try {
    task.value = await getEdgeInspectionTask(taskId.value);
    const stored = uni.getStorageSync(draftKey.value) as FormDraft | undefined;
    draft.value = !readonly.value && stored?.taskId === taskId.value && stored.version === task.value.version
      ? normalizeDraft(stored, task.value.items || [])
      : freshDraft(task.value);
    if (readonly.value) await hydrateSubmittedPhotos();
    if (canReassign.value) await loadReassignCandidates();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '临边巡检任务加载失败';
  } finally {
    loading.value = false;
  }
}

async function loadReassignCandidates() {
  if (!task.value || !canReassign.value || loadingCandidates.value) return;
  loadingCandidates.value = true;
  candidateError.value = '';
  try {
    reassignCandidates.value = (await getEdgeInspectionUserOptions(task.value.projectId))
      .filter((item) => item.canSubmit === true && item.userId !== task.value?.assigneeId);
    if (!reassignCandidates.value.some((item) => item.userId === selectedAssigneeId.value)) {
      selectedAssigneeId.value = 0;
    }
  } catch (error) {
    reassignCandidates.value = [];
    selectedAssigneeId.value = 0;
    candidateError.value = error instanceof Error ? error.message : '主巡检人候选加载失败';
  } finally {
    loadingCandidates.value = false;
  }
}

function selectAssignee(event: { detail?: { value?: string | number } }) {
  const index = Number(event.detail?.value);
  const candidate = reassignCandidates.value[index];
  selectedAssigneeId.value = candidate?.userId || 0;
}

async function reassignAssignee() {
  if (!task.value || reassigning.value) return;
  if (!canReassign.value) { showToast('当前任务不可改派，请刷新后重试'); return; }
  const candidate = selectedCandidate.value;
  if (!candidate) { showToast('请选择新的主巡检人'); return; }
  const reason = reassignReason.value.trim();
  if (!reason) { showToast('请填写改派原因'); return; }
  const confirmed = await new Promise<boolean>((resolve) => uni.showModal({
    title: '确认改派主巡检人',
    content: `将本任务改派给“${candidate.userName}”，是否继续？`,
    success: (result) => resolve(result.confirm),
    fail: () => resolve(false)
  }));
  if (!confirmed) return;
  reassigning.value = true;
  try {
    const updated = await reassignEdgeInspectionTask(task.value.id, {
      expectedVersion: task.value.version,
      assigneeId: candidate.userId,
      reason
    });
    task.value = updated;
    suppressDraftSave = true;
    uni.removeStorageSync(draftKey.value);
    draft.value = freshDraft(updated);
    suppressDraftSave = false;
    selectedAssigneeId.value = 0;
    reassignReason.value = '';
    showToast(`已改派给${updated.assigneeName || candidate.userName}`);
    await loadReassignCandidates();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '主巡检人改派失败');
  } finally {
    reassigning.value = false;
  }
}

async function hydrateSubmittedPhotos() {
  if (!task.value || !draft.value) return;
  draft.value.overallPhotos = await downloadFilePaths(task.value.overallPhotoFileIds || []);
  await Promise.all((task.value.items || []).map(async (item) => {
    itemDraft(item).photos = await downloadFilePaths(item.photoFileIds || []);
  }));
}

function freshDraft(current: EdgeInspectionTask): FormDraft {
  return {
    taskId: current.id,
    version: current.version,
    overallPhotos: [],
    remark: current.remark || '',
    items: Object.fromEntries((current.items || []).map((item) => [String(item.id), {
      result: item.result,
      description: item.description || '',
      photos: []
    }])),
    savedAt: Date.now()
  };
}

function normalizeDraft(stored: FormDraft, items: EdgeInspectionTaskItem[]) {
  const normalized = { ...stored, items: { ...stored.items } };
  for (const item of items) normalized.items[String(item.id)] ||= { description: '', photos: [] };
  return normalized;
}

function itemDraft(item: EdgeInspectionTaskItem) {
  return draft.value!.items[String(item.id)];
}

function timePart(value?: string) {
  if (!value) return '';
  const normalized = value.replace('T', ' ');
  return normalized.length >= 16 ? normalized.slice(11, 16) : normalized;
}

function formatExecutionSlot(current: EdgeInspectionTask) {
  const start = timePart(current.startTime || current.availableTime);
  const end = timePart(current.dueTime);
  return start && end ? `${start}—${end}` : current.slotName || '单一执行时段';
}

function statusLabel(current: EdgeInspectionTask) {
  if (current.status === 'PENDING' && isEdgeTaskOverdue(current)) return '逾期未检';
  if (current.status === 'PENDING' && isEdgeTaskBeforeWindow(current)) return '待开始';
  const displayStatus = current.displayStatus?.trim().toUpperCase();
  if (displayStatus && taskStatusLabels[displayStatus]) return taskStatusLabels[displayStatus];
  if (current.status === 'COMPLETED' && current.lateSubmission) return '逾期补检';
  const status = current.status?.trim().toUpperCase();
  if (status && taskStatusLabels[status]) return taskStatusLabels[status];
  if (current.overdue) return '逾期未检';
  return current.displayStatus || current.status || '待巡检';
}

function chooseResult(item: EdgeInspectionTaskItem, result: EdgeInspectionResult) {
  if (readonly.value) return;
  const value = itemDraft(item);
  value.result = result;
  if (result === 'NORMAL') {
    value.description = '';
    value.photos = [];
  }
  scheduleDraftSave();
}

function choosePhotos(item?: EdgeInspectionTaskItem) {
  if (readonly.value || !draft.value) return;
  const target = item ? itemDraft(item).photos : draft.value.overallPhotos;
  const max = 9;
  if (target.length >= max) { showToast(`最多上传${max}张照片`); return; }
  uni.chooseImage({
    count: max - target.length,
    sizeType: ['compressed'],
    sourceType: ['camera', 'album'],
    success: async (result) => {
      const paths: string[] = [];
      for (const path of result.tempFilePaths || []) paths.push(await persistLocalPhoto(path));
      target.push(...paths);
      scheduleDraftSave();
    }
  });
}

function persistLocalPhoto(filePath: string) {
  return new Promise<string>((resolve) => {
    uni.saveFile({ tempFilePath: filePath, success: (result) => resolve(result.savedFilePath), fail: () => resolve(filePath) });
  });
}

function removePhoto(index: number, item?: EdgeInspectionTaskItem) {
  if (!draft.value || readonly.value) return;
  const target = item ? itemDraft(item).photos : draft.value.overallPhotos;
  target.splice(index, 1);
  scheduleDraftSave();
}

function previewPhotos(urls: string[], current: string) {
  if (urls.length) uni.previewImage({ current, urls });
}

function scheduleDraftSave() {
  if (saveTimer) clearTimeout(saveTimer);
  saveTimer = setTimeout(saveDraftNow, 200);
}

function saveDraftNow() {
  if (suppressDraftSave || !draft.value || readonly.value) return;
  draft.value.savedAt = Date.now();
  uni.setStorageSync(draftKey.value, draft.value);
}

function validate() {
  if (!task.value || !draft.value) return '临边巡检任务尚未加载';
  if (!draft.value.overallPhotos.length) return '请至少上传1张现场全景照片';
  for (const item of task.value.items || []) {
    const value = itemDraft(item);
    if (!value.result) return `请选择“${item.itemName}”的检查结果`;
    if (value.result === 'ABNORMAL' && !value.description.trim()) return `请填写“${item.itemName}”的异常说明`;
    if (value.result === 'ABNORMAL' && !value.photos.length) return `请上传“${item.itemName}”的异常证据照片`;
  }
  return '';
}

async function revalidateBeforeSubmit() {
  const latest = await getEdgeInspectionTask(taskId.value);
  if (latest.status !== 'PENDING') throw new Error('任务状态已变化，请返回任务列表刷新');
  if (!latest.canExecute) throw new Error('任务当前不在可执行时段');
  if (!task.value || latest.version !== task.value.version) throw new Error('任务配置已变化，请重新进入后填写');
  task.value = latest;
}

async function submit() {
  if (!task.value || !draft.value || submitting.value) return;
  if (!canSubmit.value) { showToast('当前任务暂不可执行'); return; }
  const validation = validate();
  if (validation) { showToast(validation); return; }
  if (!await confirmSubmit()) return;
  submitting.value = true;
  const uploadedIds: number[] = [];
  try {
    await revalidateBeforeSubmit();
    const overallIds = await uploadPhotoIds(draft.value.overallPhotos, '临边巡检现场全景照片', {
      projectId: task.value.projectId,
      businessType: 'EDGE_INSPECTION_TASK_PENDING'
    });
    uploadedIds.push(...overallIds);
    const items = [];
    for (const item of task.value.items || []) {
      const value = itemDraft(item);
      const photoIds = value.result === 'ABNORMAL'
        ? await uploadPhotoIds(value.photos, `临边巡检异常_${item.itemName}`, {
          projectId: task.value.projectId,
          businessType: 'EDGE_INSPECTION_TASK_PENDING'
        })
        : [];
      uploadedIds.push(...photoIds);
      items.push({
        taskItemId: item.id,
        result: value.result!,
        description: value.result === 'ABNORMAL' ? value.description.trim() : undefined,
        photoFileIds: photoIds
      });
    }
    await submitEdgeInspectionTask(task.value.id, {
      expectedVersion: task.value.version,
      overallPhotoFileIds: overallIds,
      remark: draft.value.remark.trim() || undefined,
      items
    });
    suppressDraftSave = true;
    if (saveTimer) {
      clearTimeout(saveTimer);
      saveTimer = undefined;
    }
    uni.removeStorageSync(draftKey.value);
    showToast('临边巡检已提交');
    setTimeout(() => switchTab('/pages/inspection/index'), 500);
  } catch (error) {
    await deleteFileResources(uploadedIds);
    saveDraftNow();
    showToast(error instanceof Error ? error.message : '提交失败，草稿已保留');
  } finally {
    submitting.value = false;
  }
}

function confirmSubmit() {
  return new Promise<boolean>((resolve) => uni.showModal({
    title: '确认提交临边巡检',
    content: '请确认所有固定检查项、现场全景照片和异常证据均真实完整。',
    success: (result) => resolve(result.confirm),
    fail: () => resolve(false)
  }));
}

function goBack() {
  saveDraftNow();
  if (getCurrentPages().length > 1) uni.navigateBack();
  else switchTab('/pages/inspection/index');
}
</script>

<template>
  <view class="form-shell">
    <AppNavBar title="临边巡检" @back="goBack" />
    <scroll-view class="form-scroll" scroll-y enable-flex :style="scrollStyle">
      <view class="content">
        <view v-if="loading" class="state">正在加载临边巡检任务…</view>
        <view v-else-if="errorMessage" class="state error">{{ errorMessage }}</view>
        <template v-else-if="task && draft">
          <view class="summary-card">
            <view class="head"><text>{{ task.pointCode }} · {{ task.pointName }}</text><text>{{ statusLabel(task) }}</text></view>
            <text class="type">{{ pointTypeName }}</text>
            <text class="meta">{{ [task.buildingName || task.building, task.floorName || task.floor, task.locationDesc].filter(Boolean).join(' · ') || '未填写位置说明' }}</text>
            <text class="meta">执行时段：{{ executionSlot }}</text>
          </view>

          <view v-if="canReassign" class="item-card reassign-card">
            <view class="reassign-head"><view><text class="section-title">改派主巡检人</text><text class="hint">仅具备临边管理权限的人员可操作，候选人资格由服务端返回。</text></view><text class="manage-tag">管理操作</text></view>
            <text class="current-assignee">当前主巡检人：{{ task.assigneeName || '待重新分配' }}</text>
            <picker :disabled="loadingCandidates || !reassignCandidates.length" :range="reassignCandidates" range-key="userName" @change="selectAssignee">
              <view class="picker-field" :class="{ placeholder: !selectedCandidate }">
                <text>{{ loadingCandidates ? '正在加载候选人…' : selectedCandidate?.userName || (reassignCandidates.length ? '请选择新的主巡检人' : '暂无可改派的巡检人') }}</text><text>⌄</text>
              </view>
            </picker>
            <view v-if="candidateError" class="candidate-error"><text>{{ candidateError }}</text><button @tap="loadReassignCandidates">重新加载</button></view>
            <textarea v-model="reassignReason" class="textarea reassign-reason" maxlength="500" placeholder="填写改派原因（必填）" />
            <button class="reassign-button" :disabled="reassigning || loadingCandidates || !selectedCandidate || !reassignReason.trim()" @tap="reassignAssignee">{{ reassigning ? '正在改派…' : '确认改派主巡检人' }}</button>
          </view>

          <view class="item-card panorama-card">
            <view class="photo-head"><text>现场全景照片 <text class="required">*</text></text><text>{{ draft.overallPhotos.length }}/9</text></view>
            <text class="hint">每次任务至少上传1张，需清晰反映点位整体防护现状。</text>
            <view class="photos">
              <view v-for="(path, index) in draft.overallPhotos" :key="path" class="photo" @tap="previewPhotos(draft.overallPhotos,path)">
                <image :src="path" mode="aspectFill"/><text v-if="!readonly" @tap.stop="removePhoto(index)">×</text>
              </view>
              <button v-if="!readonly && draft.overallPhotos.length < 9" class="add-photo" @tap="choosePhotos()">＋<text>拍全景</text></button>
            </view>
          </view>

          <view v-for="(item, index) in task.items" :key="item.id" class="item-card">
            <view class="item-title"><text>{{ index + 1 }}. {{ item.itemName }}</text><text>固定检查项</text></view>
            <text v-if="item.guidance" class="hint">检查提示：{{ item.guidance }}</text>
            <text v-if="item.standardReference" class="standard">参考依据：{{ item.standardReference }}</text>
            <view class="result-row">
              <button v-for="option in resultOptions" :key="option.value" class="result-button"
                :class="[option.value.toLowerCase(), { selected: itemDraft(item).result === option.value }]"
                :disabled="readonly" @tap="chooseResult(item, option.value)">{{ option.label }}</button>
            </view>
            <view v-if="itemDraft(item).result === 'ABNORMAL' || (readonly && (item.description || item.photoFileIds?.length))" class="abnormal-box">
              <textarea v-model="itemDraft(item).description" class="textarea" :disabled="readonly" maxlength="500"
                placeholder="填写异常现象、具体位置和风险（必填）" @input="scheduleDraftSave" />
              <view class="photo-head"><text>异常证据照片 <text class="required">*</text></text><text>{{ itemDraft(item).photos.length }}/9</text></view>
              <view class="photos">
                <view v-for="(path, photoIndex) in itemDraft(item).photos" :key="path" class="photo" @tap="previewPhotos(itemDraft(item).photos,path)">
                  <image :src="path" mode="aspectFill"/><text v-if="!readonly" @tap.stop="removePhoto(photoIndex, item)">×</text>
                </view>
                <button v-if="!readonly && itemDraft(item).photos.length < 9" class="add-photo" @tap="choosePhotos(item)">＋<text>拍证据</text></button>
              </view>
            </view>
          </view>

          <view class="item-card">
            <text class="section-title">总备注（选填）</text>
            <textarea v-model="draft.remark" class="textarea remark" :disabled="readonly" maxlength="500" placeholder="填写本次巡检的补充说明" @input="scheduleDraftSave" />
          </view>
          <text v-if="!readonly" class="draft-tip">选择、文字和本地照片会自动保存；正式提交前会联网重新校验任务、权限和固定检查表快照。</text>
        </template>
      </view>
    </scroll-view>
    <view v-if="task && draft && !readonly" class="bottom-action">
      <button class="submit-button" :disabled="submitting || !canSubmit" @tap="submit">{{ submitting ? '正在上传并提交…' : canSubmit ? '提交临边巡检' : '当前任务暂不可提交' }}</button>
    </view>
  </view>
</template>

<style scoped>
.form-shell{min-height:100vh;background:#f4f7fa;color:#26384a}.form-scroll{height:calc(100vh - 120rpx)}.content{padding:24rpx 24rpx 170rpx}.summary-card,.item-card{margin-bottom:18rpx;padding:24rpx;border:1rpx solid #e2e9ee;border-radius:18rpx;background:#fff;box-shadow:0 5rpx 18rpx rgba(38,56,74,.05)}.head,.item-title,.photo-head{display:flex;align-items:center;justify-content:space-between;gap:15rpx}.head text:first-child,.item-title text:first-child,.section-title{font-size:25rpx;font-weight:760}.head text:last-child,.item-title text:last-child{color:#315f86;font-size:19rpx}.type{display:inline-block;margin-top:12rpx;padding:6rpx 13rpx;border-radius:999rpx;background:#eaf4fb;color:#315f86;font-size:20rpx;font-weight:720}.meta,.hint,.standard{display:block;margin-top:8rpx;color:#7d8997;font-size:20rpx;line-height:1.55}.panorama-card{border-color:#cddfec;background:#fbfdff}.required{color:#c8433f!important}.result-row{display:flex;gap:12rpx;margin-top:18rpx}.result-button{flex:1;height:64rpx;margin:0;border:1rpx solid #dfe6eb;border-radius:13rpx;background:#f8fafb;color:#5e6b78;font-size:22rpx;line-height:64rpx}.result-button::after,.add-photo::after,.submit-button::after{border:0}.result-button.selected.normal{border-color:#3aa66f;background:#e9f7ef;color:#277a51}.result-button.selected.abnormal{border-color:#d85b52;background:#fff0ef;color:#b93b35}.abnormal-box{margin-top:16rpx;padding:18rpx;border:1rpx solid #f0cfca;border-radius:14rpx;background:#fff8f7}.textarea{box-sizing:border-box;width:100%;height:130rpx;padding:17rpx;border:1rpx solid #e1e7ec;border-radius:13rpx;background:#fff;color:#344054;font-size:21rpx}.textarea.remark{margin-top:15rpx;background:#f9fafb}.photo-head{margin-top:18rpx;color:#475467;font-size:21rpx}.panorama-card .photo-head{margin-top:0}.photo-head text:last-child{color:#98a2b3}.photos{display:flex;flex-wrap:wrap;gap:12rpx;margin-top:12rpx}.photo,.add-photo{position:relative;width:132rpx;height:132rpx;margin:0;overflow:hidden;border-radius:12rpx}.photo image{width:100%;height:100%}.photo>text{position:absolute;top:4rpx;right:6rpx;display:flex;width:32rpx;height:32rpx;align-items:center;justify-content:center;border-radius:50%;background:rgba(0,0,0,.55);color:#fff}.add-photo{display:flex;align-items:center;justify-content:center;flex-direction:column;border:1rpx dashed #bac6cf;background:#f8fafb;color:#7b8996;font-size:32rpx;line-height:1}.add-photo text{margin-top:8rpx;font-size:18rpx}.draft-tip{display:block;padding:6rpx 12rpx;color:#7b8996;font-size:19rpx;line-height:1.5}.bottom-action{position:fixed;right:0;bottom:0;left:0;padding:18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom));border-top:1rpx solid #e3e8ec;background:#fff}.submit-button{height:76rpx;border-radius:15rpx;background:#315f86;color:#fff;font-size:24rpx;font-weight:750;line-height:76rpx}.submit-button[disabled]{background:#aab6c0}.state{padding:120rpx 20rpx;color:#98a2b3;text-align:center}.state.error{color:#b54747}
.reassign-card{border-color:#ead8c0;background:#fffdf9}.reassign-head{display:flex;align-items:flex-start;justify-content:space-between;gap:16rpx}.reassign-head>view{min-width:0}.manage-tag{flex-shrink:0;padding:6rpx 12rpx;border-radius:999rpx;background:#fff1df;color:#8d5b22;font-size:18rpx;font-weight:750}.current-assignee{display:block;margin-top:18rpx;color:#475467;font-size:21rpx}.picker-field{display:flex;height:72rpx;align-items:center;justify-content:space-between;margin-top:14rpx;padding:0 18rpx;border:1rpx solid #d9e1e7;border-radius:13rpx;background:#fff;color:#344054;font-size:22rpx}.picker-field.placeholder{color:#98a2b3}.reassign-reason{height:112rpx;margin-top:14rpx}.reassign-button{height:68rpx;margin-top:14rpx;border-radius:13rpx;background:#966421;color:#fff;font-size:22rpx;font-weight:750;line-height:68rpx}.reassign-button::after,.candidate-error button::after{border:0}.reassign-button[disabled]{background:#c9b9a4;color:#fff}.candidate-error{display:flex;align-items:center;justify-content:space-between;gap:12rpx;margin-top:10rpx;color:#b54747;font-size:19rpx}.candidate-error button{flex-shrink:0;min-height:48rpx;margin:0;padding:0 14rpx;border-radius:10rpx;background:#fff0ef;color:#b54747;font-size:18rpx;line-height:48rpx}
</style>
