<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad, onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import {
  completeEdgeInspectionRectification,
  getEdgeInspectionRectification,
  getEdgeInspectionUserOptions,
  reassignEdgeInspectionRectification,
  reviewEdgeInspectionRectification,
  type EdgeInspectionRectificationItem,
  type EdgeInspectionRectificationSheet,
  type EdgeInspectionUserOption
} from '@/api/edgeInspection';
import { deleteFileResources, downloadFilePaths, uploadPhotoIds } from '@/api/file';
import { useAuthStore } from '@/stores/auth';
import { getQueryNumber, showToast, switchTab } from '@/utils/navigation';
import { usePageScrollHeight } from '@/utils/navLayout';

interface ItemFeedbackDraft {
  feedback: string;
  evidencePhotoPaths: string[];
  existingPhotoFileIds: number[];
  existingPhotoPaths: string[];
  newPhotoPaths: string[];
}

const authStore = useAuthStore();
const taskId = ref(0);
const sheet = ref<EdgeInspectionRectificationSheet>();
const drafts = ref<Record<string, ItemFeedbackDraft>>({});
const options = ref<EdgeInspectionUserOption[]>([]);
const loading = ref(false);
const submitting = ref(false);
const { scrollStyle } = usePageScrollHeight({ bottomRpx: 180, minHeight: 260 });

onLoad((query) => { taskId.value = getQueryNumber(query?.id, 0); });
onShow(async () => {
  if (!await authStore.ensureRootAccess('/pages/inspection/index')) return;
  await load();
});

const actionsVisible = computed(() => sheet.value?.canRectify || sheet.value?.canReview);

function itemId(item: EdgeInspectionRectificationItem) { return Number(item.rectificationId || item.id || 0); }
function itemDraft(item: EdgeInspectionRectificationItem) { return drafts.value[String(itemId(item))]; }
function allPhotoPaths(item: EdgeInspectionRectificationItem) {
  const value = itemDraft(item);
  return value ? [...value.existingPhotoPaths, ...value.newPhotoPaths] : [];
}
function photoCount(item: EdgeInspectionRectificationItem) {
  const value = itemDraft(item);
  return value ? value.existingPhotoFileIds.length + value.newPhotoPaths.length : 0;
}

async function load() {
  if (!taskId.value) return;
  loading.value = true;
  try {
    sheet.value = await getEdgeInspectionRectification(taskId.value);
    const next: Record<string, ItemFeedbackDraft> = {};
    await Promise.all((sheet.value.items || []).map(async (item) => {
      const ids = item.rectificationPhotoFileIds || item.photoFileIds || [];
      next[String(itemId(item))] = {
        feedback: item.feedback || '',
        evidencePhotoPaths: await downloadFilePaths(item.evidencePhotoFileIds || []),
        existingPhotoFileIds: ids,
        existingPhotoPaths: await downloadFilePaths(ids),
        newPhotoPaths: []
      };
    }));
    drafts.value = next;
    if (sheet.value.canAssign) options.value = await getEdgeInspectionUserOptions(sheet.value.projectId).catch(() => []);
  } catch (error) {
    showToast(error instanceof Error ? error.message : '临边整改单加载失败');
  } finally { loading.value = false; }
}

function statusLabel(status?: string) {
  if (status === 'UNASSIGNED') return '待分派';
  if (status === 'PENDING') return '待整改';
  if (status === 'COMPLETED') return '待复查';
  if (status === 'REJECTED') return '已退回';
  if (status === 'CLOSED') return '已闭环';
  if (status === 'VOIDED') return '已作废';
  return status || '-';
}

function choosePhoto(item: EdgeInspectionRectificationItem) {
  if (!sheet.value?.canRectify) return;
  const value = itemDraft(item);
  const total = photoCount(item);
  if (total >= 9) { showToast('每项最多上传9张整改后照片'); return; }
  uni.chooseImage({ count: 9 - total, sizeType: ['compressed'], sourceType: ['camera', 'album'], success: (result) => {
    value.newPhotoPaths.push(...(result.tempFilePaths || []));
  }});
}

function removeNewPhoto(item: EdgeInspectionRectificationItem, visualIndex: number) {
  if (!sheet.value?.canRectify) return;
  const value = itemDraft(item);
  const newIndex = visualIndex - value.existingPhotoPaths.length;
  if (newIndex < 0) { showToast('历史整改照片不能移除'); return; }
  value.newPhotoPaths.splice(newIndex, 1);
}

function previewRectificationPhotos(item: EdgeInspectionRectificationItem, current: string) {
  const urls = allPhotoPaths(item);
  if (urls.length) uni.previewImage({ current, urls });
}

function previewEvidence(item: EdgeInspectionRectificationItem, current: string) {
  const urls = itemDraft(item)?.evidencePhotoPaths || [];
  if (urls.length) uni.previewImage({ current, urls });
}

function validateFeedback() {
  if (!sheet.value) return '整改单尚未加载';
  for (const item of sheet.value.items || []) {
    const value = itemDraft(item);
    if (!itemId(item)) return `“${item.itemName}”缺少整改项编号`;
    if (!value.feedback.trim()) return `请填写“${item.itemName}”的整改说明`;
    if (!value.existingPhotoFileIds.length && !value.newPhotoPaths.length) return `请上传“${item.itemName}”的整改后照片`;
  }
  return '';
}

async function complete() {
  if (!sheet.value?.canRectify || submitting.value) return;
  const validation = validateFeedback();
  if (validation) { showToast(validation); return; }
  const uploadedIds: number[] = [];
  submitting.value = true;
  try {
    const items = [];
    for (const item of sheet.value.items || []) {
      const value = itemDraft(item);
      const newIds = await uploadPhotoIds(value.newPhotoPaths, `临边整改_${item.itemName}`, {
        projectId: sheet.value.projectId,
        businessType: 'EDGE_INSPECTION_RECTIFICATION_PENDING'
      });
      uploadedIds.push(...newIds);
      items.push({
        rectificationId: itemId(item),
        expectedVersion: item.version,
        feedback: value.feedback.trim(),
        photoFileIds: [...value.existingPhotoFileIds, ...newIds]
      });
    }
    sheet.value = await completeEdgeInspectionRectification(sheet.value.taskId, {
      expectedVersion: sheet.value.version,
      items
    });
    showToast('整单整改已提交复查');
    await load();
  } catch (error) {
    await deleteFileResources(uploadedIds);
    showToast(error instanceof Error ? error.message : '整单整改提交失败');
  } finally { submitting.value = false; }
}

async function assign(kind: 'RECTIFIER' | 'REVIEWER') {
  if (!sheet.value?.canAssign) return;
  const candidates = options.value.filter((user) => kind === 'RECTIFIER' ? user.canRectify : user.canReview);
  if (!candidates.length) { showToast(kind === 'RECTIFIER' ? '当前项目没有可选整改人' : '当前项目没有可选复查人'); return; }
  const index = await selectCandidate(candidates);
  if (index < 0) return;
  const reason = await inputText(kind === 'RECTIFIER' ? '改派整改人' : '改派复查人', '请填写改派原因');
  if (!reason) return;
  try {
    sheet.value = await reassignEdgeInspectionRectification(sheet.value.taskId, {
      expectedVersion: sheet.value.version,
      assigneeId: kind === 'RECTIFIER' ? candidates[index].userId : undefined,
      reviewerId: kind === 'REVIEWER' ? candidates[index].userId : undefined,
      reason
    });
    showToast('改派成功');
    await load();
  } catch (error) { showToast(error instanceof Error ? error.message : '改派失败'); }
}

async function review(approve: boolean) {
  if (!sheet.value?.canReview) return;
  let comment = '';
  if (approve) {
    const confirmed = await confirmClose();
    if (!confirmed) return;
  } else {
    comment = await inputText('退回整改', '请填写退回原因（必填）');
    if (!comment) { showToast('退回原因必填'); return; }
  }
  try {
    sheet.value = await reviewEdgeInspectionRectification(sheet.value.taskId, approve, {
      expectedVersion: sheet.value.version,
      comment: comment || undefined
    });
    showToast(approve ? '全部复查通过，整单已闭环' : '已整单退回整改');
    await load();
  } catch (error) { showToast(error instanceof Error ? error.message : '复查操作失败'); }
}

function selectCandidate(candidates: EdgeInspectionUserOption[]) {
  return new Promise<number>((resolve) => uni.showActionSheet({ itemList: candidates.map((item) => item.userName), success: (result) => resolve(result.tapIndex), fail: () => resolve(-1) }));
}

function inputText(title: string, placeholder: string) {
  return new Promise<string>((resolve) => uni.showModal({ title, editable: true, placeholderText: placeholder, success: (result) => resolve(result.confirm ? String(result.content || '').trim() : ''), fail: () => resolve('') }));
}

function confirmClose() {
  return new Promise<boolean>((resolve) => uni.showModal({ title: '全部通过并关闭', content: '确认全部异常项均已整改到位？通过后本次临边巡检将闭环。', success: (result) => resolve(result.confirm), fail: () => resolve(false) }));
}

function goBack() {
  if (getCurrentPages().length > 1) uni.navigateBack();
  else switchTab('/pages/inspection/index');
}
</script>

<template>
  <view class="page-shell">
    <AppNavBar title="临边巡检整改单" @back="goBack" />
    <scroll-view class="page-scroll" scroll-y enable-flex :style="scrollStyle">
      <view class="content">
        <view v-if="loading" class="state">正在加载整改单…</view>
        <template v-else-if="sheet">
          <view class="card summary-card">
            <view class="head"><view><text>{{ sheet.pointName }}</text><text class="type">{{ sheet.pointTypeName || sheet.categoryName || '临边点位' }}</text></view><text>{{ statusLabel(sheet.status) }}</text></view>
            <view class="row"><text>巡检日期</text><text>{{ sheet.occurrenceDate || '-' }}</text></view>
            <view class="row"><text>异常项目</text><text>{{ sheet.items.length }} 项（合并为一张整改单）</text></view>
            <view class="row"><text>整改负责人</text><text>{{ sheet.assigneeName || '待分派' }}</text></view>
            <view class="row"><text>整改期限</text><text :class="{ danger: sheet.overdue }">{{ sheet.deadline || '-' }}</text></view>
            <view class="row"><text>复查负责人</text><text>{{ sheet.reviewerName || '待指定' }}</text></view>
            <view v-if="sheet.canAssign" class="assign-row"><button class="secondary" @tap="assign('RECTIFIER')">改派整改人</button><button class="secondary" @tap="assign('REVIEWER')">改派复查人</button></view>
          </view>

          <view v-for="(item,index) in sheet.items" :key="itemId(item)" class="card item-card">
            <view class="item-title"><text>{{ index + 1 }}. {{ item.itemName }}</text><text>{{ statusLabel(item.status || sheet.status) }}</text></view>
            <text class="problem">异常描述：{{ item.problemDesc || item.description || '-' }}</text>
            <text v-if="item.requirement" class="requirement">整改要求：{{ item.requirement }}</text>
            <template v-if="itemDraft(item).evidencePhotoPaths.length">
              <view class="photo-head"><text>巡检异常证据</text><text>{{ itemDraft(item).evidencePhotoPaths.length }} 张</text></view>
              <view class="photos evidence-photos">
                <view v-for="path in itemDraft(item).evidencePhotoPaths" :key="path" class="photo" @tap="previewEvidence(item,path)"><image :src="path" mode="aspectFill"/></view>
              </view>
            </template>
            <textarea v-model="itemDraft(item).feedback" class="textarea" :disabled="!sheet.canRectify" maxlength="500" placeholder="逐项填写整改措施和完成情况（必填）" />
            <view class="photo-head"><text>整改后照片 <text class="required">*</text></text><text>{{ photoCount(item) }}/9</text></view>
            <view class="photos">
              <view v-for="(path,photoIndex) in allPhotoPaths(item)" :key="`${path}-${photoIndex}`" class="photo" @tap="previewRectificationPhotos(item,path)">
                <image :src="path" mode="aspectFill"/>
                <text v-if="sheet.canRectify && photoIndex >= itemDraft(item).existingPhotoPaths.length" @tap.stop="removeNewPhoto(item,photoIndex)">×</text>
              </view>
              <button v-if="sheet.canRectify && photoCount(item) < 9" class="add-photo" @tap="choosePhoto(item)">＋<text>整改照片</text></button>
            </view>
            <text v-if="item.reviewComment" class="item-review">最近复查意见：{{ item.reviewComment }}</text>
          </view>
          <view v-if="sheet.reviewComment" class="card sheet-review">最近整单复查意见：{{ sheet.reviewComment }}</view>
        </template>
      </view>
    </scroll-view>
    <view v-if="sheet && actionsVisible" class="bottom-action">
      <button v-if="sheet.canRectify" class="primary" :disabled="submitting" @tap="complete">{{ submitting ? '正在提交整单…' : '提交整单整改' }}</button>
      <button v-if="sheet.canReview" class="reject" @tap="review(false)">整单退回</button>
      <button v-if="sheet.canReview" class="primary" @tap="review(true)">全部通过并关闭</button>
    </view>
  </view>
</template>

<style scoped>
.page-shell{min-height:100vh;background:#f4f7fa;color:#26384a}.page-scroll{height:calc(100vh - 120rpx)}.content{padding:24rpx 24rpx 180rpx}.card{margin-bottom:18rpx;padding:24rpx;border:1rpx solid #e2e9ee;border-radius:18rpx;background:#fff}.summary-card{border-color:#d7e3eb}.head,.item-title,.photo-head{display:flex;align-items:center;justify-content:space-between;gap:14rpx;padding-bottom:16rpx;border-bottom:1rpx solid #edf1f4}.head>view{min-width:0;display:flex;align-items:center;gap:10rpx}.head>view>text:first-child,.item-title text:first-child{font-size:25rpx;font-weight:760}.head>text,.item-title text:last-child{padding:5rpx 12rpx;border-radius:999rpx;background:#fff3dd;color:#a56811;font-size:19rpx}.type{padding:5rpx 10rpx;border-radius:999rpx;background:#eaf4fb;color:#315f86;font-size:18rpx}.row{display:flex;gap:18rpx;padding-top:15rpx;font-size:21rpx;line-height:1.5}.row text:first-child{width:130rpx;flex-shrink:0;color:#7c8997}.row text:last-child{flex:1;color:#344054}.danger{color:#c43d39!important}.assign-row{display:grid;grid-template-columns:1fr 1fr;gap:12rpx}.secondary{height:66rpx;margin-top:20rpx;border:1rpx solid #315f86;border-radius:13rpx;background:#fff;color:#315f86;font-size:21rpx;line-height:66rpx}.problem{display:block;margin-top:16rpx;padding:15rpx;border-radius:12rpx;background:#fff5f4;color:#93413e;font-size:20rpx;line-height:1.55}.requirement{display:block;margin-top:10rpx;padding:14rpx;border-radius:12rpx;background:#fff8ee;color:#805d2d;font-size:20rpx;line-height:1.55}.textarea{box-sizing:border-box;width:100%;height:140rpx;margin-top:16rpx;padding:17rpx;border:1rpx solid #e0e7ec;border-radius:13rpx;background:#f9fafb;font-size:21rpx}.photo-head{margin-top:17rpx;padding-bottom:0;border:0;color:#475467;font-size:21rpx}.required{color:#c43d39}.photos{display:flex;flex-wrap:wrap;gap:12rpx;margin-top:12rpx}.evidence-photos{padding-bottom:6rpx}.photo,.add-photo{position:relative;width:132rpx;height:132rpx;margin:0;overflow:hidden;border-radius:12rpx}.photo image{width:100%;height:100%}.photo text{position:absolute;top:5rpx;right:6rpx;display:flex;width:32rpx;height:32rpx;align-items:center;justify-content:center;border-radius:50%;background:rgba(0,0,0,.55);color:#fff}.add-photo{display:flex;align-items:center;justify-content:center;flex-direction:column;border:1rpx dashed #bac6cf;background:#f8fafb;color:#7b8996;font-size:32rpx}.add-photo text{font-size:17rpx}.item-review{display:block;margin-top:16rpx;padding:14rpx;border-radius:10rpx;background:#fff8ee;color:#805d2d;font-size:20rpx}.sheet-review{background:#fff8ee;color:#805d2d;font-size:20rpx}.bottom-action{position:fixed;z-index:20;right:0;bottom:0;left:0;display:flex;gap:12rpx;padding:18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom));border-top:1rpx solid #e3e8ec;background:#fff}.bottom-action button{height:74rpx;margin:0;border-radius:14rpx;font-size:21rpx;line-height:74rpx}.bottom-action button::after,.secondary::after,.add-photo::after{border:0}.primary{flex:1;background:#315f86;color:#fff}.reject{width:180rpx;border:1rpx solid #d55a53;background:#fff;color:#b8403b}.state{padding:120rpx 20rpx;color:#98a2b3;text-align:center}
</style>
