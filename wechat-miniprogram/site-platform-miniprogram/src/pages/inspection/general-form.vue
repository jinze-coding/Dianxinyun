<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad, onShow, onUnload } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import {
  getGeneralInspectionTask,
  getGeneralInspectionUserOptions,
  submitGeneralInspectionTask,
  verifyGeneralInspectionScan,
  type GeneralInspectionResult,
  type GeneralInspectionTask,
  type GeneralInspectionTaskItem,
  type GeneralInspectionUserOption
} from '@/api/generalInspection';
import { deleteFileResources, downloadFilePaths, uploadPhotoIds } from '@/api/file';
import { useAuthStore } from '@/stores/auth';
import { getQueryNumber, showToast, switchTab } from '@/utils/navigation';
import { usePageScrollHeight } from '@/utils/navLayout';

interface ItemDraft {
  result?: GeneralInspectionResult;
  description: string;
  photos: string[];
  rectifierId?: number;
  deadline?: string;
  requirement: string;
}

interface FormDraft {
  taskId: number;
  version: number;
  overallPhotos: string[];
  remark: string;
  publicRemark: string;
  items: Record<string, ItemDraft>;
  savedAt: number;
}

const authStore = useAuthStore();
const taskId = ref(0);
const scene = ref('');
const task = ref<GeneralInspectionTask>();
const userOptions = ref<GeneralInspectionUserOption[]>([]);
const draft = ref<FormDraft>();
const loading = ref(false);
const submitting = ref(false);
const errorMessage = ref('');
const { scrollStyle } = usePageScrollHeight({ bottomRpx: 170, minHeight: 260 });
let saveTimer: ReturnType<typeof setTimeout> | undefined;

const draftKey = computed(() => `general_inspection_draft_${taskId.value}`);
const rectifierOptions = computed(() => userOptions.value.filter((user) => user.canRectify));
const readonly = computed(() => task.value?.status !== 'PENDING');
const canSubmit = computed(() => task.value?.canExecute === true && task.value?.status === 'PENDING'
  && (!task.value.qrRequired || task.value.scanVerified));
const resultOptions: Array<{ value: GeneralInspectionResult; label: string }> = [
  { value: 'NORMAL', label: '正常' },
  { value: 'ABNORMAL', label: '异常' },
  { value: 'NA', label: '不适用' }
];

onLoad((options) => {
  taskId.value = getQueryNumber(options?.id, 0);
  scene.value = String(options?.scene || '');
});

onShow(async () => {
  if (!await authStore.ensureRootAccess('/pages/inspection/index')) return;
  await loadTask();
});

onUnload(() => {
  if (saveTimer) clearTimeout(saveTimer);
  saveDraftNow();
});

async function loadTask() {
  if (!taskId.value) { errorMessage.value = '缺少巡检任务编号'; return; }
  loading.value = true;
  errorMessage.value = '';
  try {
    task.value = scene.value
      ? await verifyGeneralInspectionScan(taskId.value, scene.value)
      : await getGeneralInspectionTask(taskId.value);
    const stored = uni.getStorageSync(draftKey.value) as FormDraft | undefined;
    draft.value = stored?.taskId === taskId.value && stored.version === task.value.version
      ? normalizeDraft(stored, task.value.items || [])
      : freshDraft(task.value);
    if (readonly.value) await hydrateSubmittedPhotos();
    if (canSubmit.value) {
      userOptions.value = await getGeneralInspectionUserOptions(task.value.projectId).catch(() => []);
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '巡检任务加载失败';
  } finally {
    loading.value = false;
  }
}

async function hydrateSubmittedPhotos() {
  if (!task.value || !draft.value) return;
  draft.value.overallPhotos = await downloadFilePaths(task.value.overallPhotoFileIds || []);
  await Promise.all((task.value.items || []).map(async (item) => {
    itemDraft(item).photos = await downloadFilePaths(item.photoFileIds || []);
  }));
}

function freshDraft(current: GeneralInspectionTask): FormDraft {
  return {
    taskId: current.id,
    version: current.version,
    overallPhotos: [],
    remark: current.remark || '',
    publicRemark: current.publicRemark || '',
    items: Object.fromEntries((current.items || []).map((item) => [String(item.id), {
      result: item.result,
      description: item.description || '',
      photos: [],
      requirement: ''
    }])),
    savedAt: Date.now()
  };
}

function normalizeDraft(stored: FormDraft, items: GeneralInspectionTaskItem[]) {
  const normalized = { ...stored, items: { ...stored.items } };
  for (const item of items) {
    normalized.items[String(item.id)] ||= { description: '', photos: [], requirement: '' };
  }
  return normalized;
}

function itemDraft(item: GeneralInspectionTaskItem) {
  return draft.value!.items[String(item.id)];
}

function chooseResult(item: GeneralInspectionTaskItem, result: GeneralInspectionResult) {
  if (readonly.value) return;
  if (result === 'NA' && !item.allowNa) { showToast('该项不允许选择不适用'); return; }
  const value = itemDraft(item);
  value.result = result;
  if (result !== 'ABNORMAL') {
    value.rectifierId = undefined;
    value.deadline = undefined;
    value.requirement = '';
  }
  scheduleDraftSave();
}

function choosePhotos(item?: GeneralInspectionTaskItem) {
  if (readonly.value || !draft.value) return;
  const target = item ? itemDraft(item).photos : draft.value.overallPhotos;
  const max = item ? Math.min(9, item.photoMax || 9) : Math.min(9, task.value?.overallPhotoMax || 9);
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

function removePhoto(index: number, item?: GeneralInspectionTaskItem) {
  if (!draft.value || readonly.value) return;
  const target = item ? itemDraft(item).photos : draft.value.overallPhotos;
  target.splice(index, 1);
  scheduleDraftSave();
}

function scheduleDraftSave() {
  if (saveTimer) clearTimeout(saveTimer);
  saveTimer = setTimeout(saveDraftNow, 200);
}

function saveDraftNow() {
  if (!draft.value || readonly.value) return;
  draft.value.savedAt = Date.now();
  uni.setStorageSync(draftKey.value, draft.value);
}

function validate() {
  if (!task.value || !draft.value) return '巡检任务尚未加载';
  const overallMin = task.value.overallPhotoMin || 0;
  if (draft.value.overallPhotos.length < overallMin) return `整体照片至少上传${overallMin}张`;
  if (task.value.overallRemarkRequired && !draft.value.remark.trim()) return '请填写总备注';
  for (const item of task.value.items || []) {
    const value = itemDraft(item);
    if (!value.result) return `请选择“${item.itemName}”的检查结果`;
    if (value.result === 'NA' && !value.description.trim()) return `“${item.itemName}”选择不适用时说明必填`;
    const minPhotos = value.result === 'ABNORMAL' ? item.abnormalPhotoMin : value.result === 'NORMAL' ? item.normalPhotoMin : 0;
    if (value.photos.length < minPhotos) return `“${item.itemName}”至少上传${minPhotos}张照片`;
    if (value.result === 'NORMAL' && item.normalDescriptionRequired && !value.description.trim()) return `请填写“${item.itemName}”的检查说明`;
    if (value.result === 'ABNORMAL' && item.abnormalDescriptionRequired && !value.description.trim()) return `请填写“${item.itemName}”的异常说明`;
  }
  return '';
}

async function submit() {
  if (!task.value || !draft.value || submitting.value) return;
  if (!canSubmit.value) { showToast('当前任务暂不可执行'); return; }
  const validation = validate();
  if (validation) { showToast(validation); return; }
  const confirmed = await confirmSubmit();
  if (!confirmed) return;
  submitting.value = true;
  const uploadedIds: number[] = [];
  try {
    const overallIds = await uploadPhotoIds(draft.value.overallPhotos, '通用巡检整体照片', {
      projectId: task.value.projectId,
      businessType: 'INSPECTION_CUSTOM_TASK_PENDING'
    });
    uploadedIds.push(...overallIds);
    const items = [];
    for (const item of task.value.items || []) {
      const value = itemDraft(item);
      const photoIds = await uploadPhotoIds(value.photos, `通用巡检_${item.itemName}`, {
        projectId: task.value.projectId,
        businessType: 'INSPECTION_CUSTOM_TASK_PENDING'
      });
      uploadedIds.push(...photoIds);
      items.push({
        taskItemId: item.id,
        result: value.result!,
        description: value.description.trim() || undefined,
        photoFileIds: photoIds,
        rectifierId: value.result === 'ABNORMAL' ? value.rectifierId : undefined,
        deadline: value.result === 'ABNORMAL' ? value.deadline : undefined,
        requirement: value.result === 'ABNORMAL' ? value.requirement.trim() || undefined : undefined
      });
    }
    await submitGeneralInspectionTask(task.value.id, {
      expectedVersion: task.value.version,
      overallPhotoFileIds: overallIds,
      remark: draft.value.remark.trim() || undefined,
      publicRemark: draft.value.publicRemark.trim() || undefined,
      items
    });
    uni.removeStorageSync(draftKey.value);
    showToast('巡检已提交');
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
    title: '确认提交巡检',
    content: '正式提交后由管理者按审计规则纠错，请确认检查结果和照片无误。',
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
    <AppNavBar title="通用巡检" @back="goBack" />
    <scroll-view class="form-scroll" scroll-y enable-flex :style="scrollStyle">
      <view class="content">
        <view v-if="loading" class="state">正在加载巡检任务…</view>
        <view v-else-if="errorMessage" class="state error">{{ errorMessage }}</view>
        <template v-else-if="task && draft">
          <view class="summary-card">
            <view class="head"><text>{{ task.pointCode }} · {{ task.pointName }}</text><text>{{ task.displayStatus }}</text></view>
            <text class="template">{{ task.templateName }} / {{ task.slotName }}</text>
            <text class="meta">{{ task.locationDesc || '未填写位置说明' }}</text>
            <text class="meta">执行窗口：{{ task.availableTime }} 至 {{ task.dueTime }}</text>
            <text v-if="task.qrRequired" class="scan-badge">{{ task.scanVerified ? '点位二维码已核验' : '本任务必须先扫描点位二维码' }}</text>
          </view>

          <view v-for="(item, index) in task.items" :key="item.id" class="item-card">
            <view class="item-title"><text>{{ index + 1 }}. {{ item.itemName }}</text><text v-if="item.allowNa">允许不适用</text></view>
            <text v-if="item.guidance" class="hint">检查提示：{{ item.guidance }}</text>
            <text v-if="item.standardReference" class="standard">依据：{{ item.standardReference }}</text>
            <view class="result-row">
              <button v-for="option in resultOptions"
                :key="option.value" class="result-button" :class="[option.value.toLowerCase(), { selected: itemDraft(item).result === option.value }]"
                :disabled="readonly || (option.value === 'NA' && !item.allowNa)" @tap="chooseResult(item, option.value)">
                {{ option.label }}
              </button>
            </view>
            <textarea v-model="itemDraft(item).description" class="textarea" :disabled="readonly" maxlength="500"
              :placeholder="itemDraft(item).result === 'ABNORMAL' ? '填写异常现象和位置' : itemDraft(item).result === 'NA' ? '不适用原因（必填）' : '检查说明（按模板要求）'" @input="scheduleDraftSave" />
            <view class="photo-head"><text>检查照片</text><text>{{ itemDraft(item).photos.length }}/{{ item.photoMax || 9 }}</text></view>
            <view class="photos">
              <view v-for="(path, photoIndex) in itemDraft(item).photos" :key="path" class="photo" @tap="removePhoto(photoIndex, item)">
                <image :src="path" mode="aspectFill"/><text v-if="!readonly">×</text>
              </view>
              <button v-if="!readonly && itemDraft(item).photos.length < (item.photoMax || 9)" class="add-photo" @tap="choosePhotos(item)">＋<text>拍照</text></button>
            </view>
            <view v-if="itemDraft(item).result === 'ABNORMAL'" class="rect-config">
              <text class="subhead">逐项整改安排</text>
              <picker :range="rectifierOptions" range-key="userName" :disabled="readonly" @change="itemDraft(item).rectifierId = rectifierOptions[Number($event.detail.value)]?.userId; scheduleDraftSave()">
                <view class="picker-value">整改人：{{ rectifierOptions.find(user => user.userId === itemDraft(item).rectifierId)?.userName || '使用计划默认/待分派' }} ›</view>
              </picker>
              <picker mode="date" :value="itemDraft(item).deadline" :disabled="readonly" @change="itemDraft(item).deadline = String($event.detail.value); scheduleDraftSave()">
                <view class="picker-value">整改期限：{{ itemDraft(item).deadline || '使用计划默认期限' }} ›</view>
              </picker>
              <input v-model="itemDraft(item).requirement" :disabled="readonly" class="input" maxlength="500" placeholder="整改要求（选填）" @input="scheduleDraftSave"/>
            </view>
          </view>

          <view class="item-card">
            <view class="photo-head"><text>整体照片</text><text>{{ draft.overallPhotos.length }}/{{ task.overallPhotoMax || 9 }}</text></view>
            <view class="photos">
              <view v-for="(path, index) in draft.overallPhotos" :key="path" class="photo" @tap="removePhoto(index)">
                <image :src="path" mode="aspectFill"/><text v-if="!readonly">×</text>
              </view>
              <button v-if="!readonly && draft.overallPhotos.length < (task.overallPhotoMax || 9)" class="add-photo" @tap="choosePhotos()">＋<text>拍照</text></button>
            </view>
            <textarea v-model="draft.remark" class="textarea" :disabled="readonly" maxlength="500" placeholder="总备注" @input="scheduleDraftSave" />
            <textarea v-model="draft.publicRemark" class="textarea compact" :disabled="readonly" maxlength="300" placeholder="公开备注（匿名月表可见，请勿填写敏感信息）" @input="scheduleDraftSave" />
          </view>
          <text v-if="!readonly" class="draft-tip">选择、文字和本地照片会自动保存；提交时将联网重新校验任务、权限、二维码和模板快照。</text>
        </template>
      </view>
    </scroll-view>
    <view v-if="task && draft && !readonly" class="bottom-action">
      <button class="submit-button" :disabled="submitting || !canSubmit" @tap="submit">{{ submitting ? '正在上传并提交…' : canSubmit ? '提交巡检' : task.qrRequired && !task.scanVerified ? '请先扫描点位二维码' : '尚未进入执行窗口' }}</button>
    </view>
  </view>
</template>

<style scoped>
.form-shell{min-height:100vh;background:#f4f7fa;color:#26384a}.form-scroll{height:calc(100vh - 120rpx)}.content{padding:24rpx 24rpx 170rpx}.summary-card,.item-card{margin-bottom:18rpx;padding:24rpx;border:1rpx solid #e2e9ee;border-radius:18rpx;background:#fff;box-shadow:0 5rpx 18rpx rgba(38,56,74,.05)}.head,.item-title,.photo-head{display:flex;align-items:center;justify-content:space-between;gap:15rpx}.head text:first-child,.item-title text:first-child{font-size:25rpx;font-weight:760}.head text:last-child,.item-title text:last-child{color:#315f86;font-size:19rpx}.template{display:block;margin-top:10rpx;color:#475467;font-size:22rpx}.meta,.hint,.standard{display:block;margin-top:7rpx;color:#7d8997;font-size:20rpx;line-height:1.55}.scan-badge{display:inline-block;margin-top:14rpx;padding:7rpx 12rpx;border-radius:999rpx;background:#eaf4fb;color:#315f86;font-size:19rpx}.result-row{display:flex;gap:10rpx;margin-top:18rpx}.result-button{flex:1;height:62rpx;margin:0;border:1rpx solid #dfe6eb;border-radius:13rpx;background:#f8fafb;color:#5e6b78;font-size:21rpx;line-height:62rpx}.result-button::after,.add-photo::after,.submit-button::after{border:0}.result-button.selected.normal{border-color:#3aa66f;background:#e9f7ef;color:#277a51}.result-button.selected.abnormal{border-color:#d85b52;background:#fff0ef;color:#b93b35}.result-button.selected.na{border-color:#8091a0;background:#edf1f4;color:#4f6070}.textarea,.input{box-sizing:border-box;width:100%;margin-top:16rpx;border:1rpx solid #e1e7ec;border-radius:13rpx;background:#f9fafb;color:#344054;font-size:21rpx}.textarea{height:130rpx;padding:17rpx}.textarea.compact{height:100rpx}.input{height:70rpx;padding:0 16rpx}.photo-head{margin-top:18rpx;color:#475467;font-size:21rpx}.photo-head text:last-child{color:#98a2b3}.photos{display:flex;flex-wrap:wrap;gap:12rpx;margin-top:12rpx}.photo,.add-photo{position:relative;width:132rpx;height:132rpx;margin:0;overflow:hidden;border-radius:12rpx}.photo image{width:100%;height:100%}.photo>text{position:absolute;top:4rpx;right:6rpx;display:flex;width:32rpx;height:32rpx;align-items:center;justify-content:center;border-radius:50%;background:rgba(0,0,0,.55);color:#fff}.add-photo{display:flex;align-items:center;justify-content:center;flex-direction:column;border:1rpx dashed #bac6cf;background:#f8fafb;color:#7b8996;font-size:32rpx;line-height:1}.add-photo text{margin-top:8rpx;font-size:18rpx}.rect-config{margin-top:18rpx;padding:18rpx;border-radius:14rpx;background:#fff8ee}.subhead{font-size:21rpx;font-weight:750}.picker-value{margin-top:12rpx;padding:14rpx;border-radius:10rpx;background:#fff;color:#5d6874;font-size:20rpx}.draft-tip{display:block;padding:6rpx 12rpx;color:#7b8996;font-size:19rpx;line-height:1.5}.bottom-action{position:fixed;right:0;bottom:0;left:0;padding:18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom));border-top:1rpx solid #e3e8ec;background:#fff}.submit-button{height:76rpx;border-radius:15rpx;background:#315f86;color:#fff;font-size:24rpx;font-weight:750;line-height:76rpx}.submit-button[disabled]{background:#aab6c0}.state{padding:120rpx 20rpx;color:#98a2b3;text-align:center}.state.error{color:#b54747}
</style>
