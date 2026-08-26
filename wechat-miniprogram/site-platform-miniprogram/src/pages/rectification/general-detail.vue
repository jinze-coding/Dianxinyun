<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad, onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import {
  assignGeneralInspectionRectification,
  completeGeneralInspectionRectification,
  getGeneralInspectionRectification,
  getGeneralInspectionTask,
  getGeneralInspectionUserOptions,
  reassignGeneralInspectionTask,
  reviewGeneralInspectionRectification,
  type GeneralInspectionRectification,
  type GeneralInspectionUserOption
} from '@/api/generalInspection';
import { deleteFileResources, downloadFilePaths, uploadPhotoIds } from '@/api/file';
import { useAuthStore } from '@/stores/auth';
import { getQueryNumber, showToast, switchTab } from '@/utils/navigation';
import { usePageScrollHeight } from '@/utils/navLayout';

const authStore = useAuthStore();
const id = ref(0);
const task = ref<GeneralInspectionRectification>();
const feedback = ref('');
const photoPaths = ref<string[]>([]);
const existingPhotoPaths = ref<string[]>([]);
const options = ref<GeneralInspectionUserOption[]>([]);
const loading = ref(false);
const submitting = ref(false);
const { scrollStyle } = usePageScrollHeight({ bottomRpx: 180, minHeight: 260 });

onLoad((query) => { id.value = getQueryNumber(query?.id, 0); });
onShow(async () => {
  if (!await authStore.ensureRootAccess('/pages/inspection/index')) return;
  await load();
});

const actionsVisible = computed(() => task.value?.canRectify || task.value?.canReview || task.value?.canAssign);

async function load() {
  if (!id.value) return;
  loading.value = true;
  try {
    task.value = await getGeneralInspectionRectification(id.value);
    feedback.value = task.value.feedback || '';
    existingPhotoPaths.value = await downloadFilePaths(task.value.rectificationPhotoFileIds || []);
    photoPaths.value = [...existingPhotoPaths.value];
    if (task.value.canAssign) options.value = await getGeneralInspectionUserOptions(task.value.projectId).catch(() => []);
  } catch (error) {
    showToast(error instanceof Error ? error.message : '整改详情加载失败');
  } finally { loading.value = false; }
}

function statusLabel(status?: string) {
  if (status === 'UNASSIGNED') return '待分派';
  if (status === 'PENDING') return '待整改';
  if (status === 'COMPLETED') return '待复查';
  if (status === 'REJECTED') return '已退回';
  if (status === 'CLOSED') return '已关闭';
  if (status === 'VOIDED') return '已作废';
  return status || '-';
}

function choosePhoto() {
  if (!task.value?.canRectify) return;
  if (photoPaths.value.length >= 9) { showToast('最多上传9张整改照片'); return; }
  uni.chooseImage({ count: 9 - photoPaths.value.length, sizeType: ['compressed'], sourceType: ['camera', 'album'], success: (result) => {
    photoPaths.value.push(...(result.tempFilePaths || []));
  }});
}

function removePhoto(index: number) {
  if (!task.value?.canRectify) return;
  if (index < existingPhotoPaths.value.length) { showToast('已提交照片不能在本次反馈中移除'); return; }
  photoPaths.value.splice(index, 1);
}

async function complete() {
  if (!task.value?.canRectify || submitting.value) return;
  if (!feedback.value.trim()) { showToast('请填写整改说明'); return; }
  if (!photoPaths.value.length) { showToast('请上传整改照片'); return; }
  const newPaths = photoPaths.value.slice(existingPhotoPaths.value.length);
  let newIds: number[] = [];
  submitting.value = true;
  try {
    newIds = await uploadPhotoIds(newPaths, '通用巡检整改照片', {
      projectId: task.value.projectId,
      businessType: 'INSPECTION_CUSTOM_RECTIFICATION_PENDING'
    });
    await completeGeneralInspectionRectification(task.value.id, {
      expectedVersion: task.value.version,
      comment: feedback.value.trim(),
      photoFileIds: [...(task.value.rectificationPhotoFileIds || []), ...newIds]
    });
    showToast('整改已提交复查');
    await load();
  } catch (error) {
    await deleteFileResources(newIds);
    showToast(error instanceof Error ? error.message : '整改提交失败');
  } finally { submitting.value = false; }
}

async function assign() {
  if (!task.value?.canAssign) return;
  const candidates = options.value.filter((user) => user.canRectify);
  if (!candidates.length) { showToast('当前项目没有可选整改人'); return; }
  const index = await selectCandidate(candidates);
  if (index < 0) return;
  const deadline = await inputText('整改期限', '请输入 YYYY-MM-DD');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(deadline)) { showToast('整改期限格式不正确'); return; }
  const reason = await inputText('分派说明', '请填写分派或改派原因');
  if (!reason) return;
  try {
    task.value = await assignGeneralInspectionRectification(task.value.id, {
      expectedVersion: task.value.version,
      assigneeId: candidates[index].userId,
      deadline,
      requirement: task.value.requirement,
      comment: reason
    });
    showToast('整改任务已分派');
  } catch (error) { showToast(error instanceof Error ? error.message : '分派失败'); }
}

async function assignReviewer() {
  if (!task.value?.canAssign) return;
  const candidates = options.value.filter((user) => user.canReview);
  if (!candidates.length) { showToast('当前项目没有可选复查人'); return; }
  const index = await selectCandidate(candidates);
  if (index < 0) return;
  const reason = await inputText('复查人改派', '请填写改派原因');
  if (!reason) return;
  try {
    const sourceTask = await getGeneralInspectionTask(task.value.taskId);
    await reassignGeneralInspectionTask(sourceTask.id, {
      expectedVersion: sourceTask.version,
      reviewerId: candidates[index].userId,
      reason
    });
    showToast('复查人已改派');
    await load();
  } catch (error) { showToast(error instanceof Error ? error.message : '复查人改派失败'); }
}

async function review(approve: boolean) {
  if (!task.value?.canReview) return;
  const comment = await inputText(approve ? '复查通过' : '复查退回', approve ? '请填写复查意见' : '请填写退回原因');
  if (!comment) return;
  try {
    task.value = await reviewGeneralInspectionRectification(task.value.id, approve, {
      expectedVersion: task.value.version,
      comment
    });
    showToast(approve ? '整改已关闭' : '已退回继续整改');
    await load();
  } catch (error) { showToast(error instanceof Error ? error.message : '复查操作失败'); }
}

function selectCandidate(candidates: GeneralInspectionUserOption[]) {
  return new Promise<number>((resolve) => uni.showActionSheet({ itemList: candidates.map((item) => item.userName), success: (result) => resolve(result.tapIndex), fail: () => resolve(-1) }));
}

function inputText(title: string, placeholder: string) {
  return new Promise<string>((resolve) => uni.showModal({ title, editable: true, placeholderText: placeholder, success: (result) => resolve(result.confirm ? String(result.content || '').trim() : ''), fail: () => resolve('') }));
}

function goBack() {
  if (getCurrentPages().length > 1) uni.navigateBack();
  else switchTab('/pages/inspection/index');
}
</script>

<template>
  <view class="page-shell">
    <AppNavBar title="通用巡检整改" @back="goBack" />
    <scroll-view class="page-scroll" scroll-y enable-flex :style="scrollStyle">
      <view class="content">
        <view v-if="loading" class="state">正在加载整改详情…</view>
        <template v-else-if="task">
          <view class="card">
            <view class="head"><text>{{ task.pointName }} · {{ task.itemName }}</text><text>{{ statusLabel(task.status) }}</text></view>
            <view class="row"><text>异常描述</text><text>{{ task.problemDesc || '-' }}</text></view>
            <view class="row"><text>整改要求</text><text>{{ task.requirement || '-' }}</text></view>
            <view class="row"><text>整改负责人</text><text>{{ task.assigneeName || '待分派' }}</text></view>
            <view class="row"><text>整改期限</text><text :class="{ danger: task.overdue }">{{ task.deadline || '-' }}</text></view>
            <view class="row"><text>复查负责人</text><text>{{ task.reviewerName || '待指定' }}</text></view>
            <button v-if="task.canAssign" class="secondary" @tap="assign">{{ task.assigneeId ? '改派整改人' : '分派整改人' }}</button>
            <button v-if="task.canAssign && task.status === 'COMPLETED'" class="secondary" @tap="assignReviewer">{{ task.reviewerId ? '改派复查人' : '指定复查人' }}</button>
          </view>
          <view class="card">
            <text class="section-title">整改反馈</text>
            <textarea v-model="feedback" class="textarea" :disabled="!task.canRectify" maxlength="500" placeholder="填写采取的整改措施和完成情况" />
            <view class="photos">
              <view v-for="(path,index) in photoPaths" :key="path" class="photo" @tap="removePhoto(index)"><image :src="path" mode="aspectFill"/><text v-if="task.canRectify && index >= existingPhotoPaths.length">×</text></view>
              <button v-if="task.canRectify && photoPaths.length < 9" class="add-photo" @tap="choosePhoto">＋<text>照片</text></button>
            </view>
            <text v-if="task.reviewComment" class="review-note">最近复查意见：{{ task.reviewComment }}</text>
          </view>
        </template>
      </view>
    </scroll-view>
    <view v-if="task && actionsVisible" class="bottom-action">
      <button v-if="task.canRectify" class="primary" :disabled="submitting" @tap="complete">{{ submitting ? '正在提交…' : '提交整改' }}</button>
      <button v-if="task.canReview" class="reject" @tap="review(false)">退回复改</button>
      <button v-if="task.canReview" class="primary" @tap="review(true)">复查关闭</button>
    </view>
  </view>
</template>

<style scoped>
.page-shell{min-height:100vh;background:#f4f7fa;color:#26384a}.page-scroll{height:calc(100vh - 120rpx)}.content{padding:24rpx 24rpx 170rpx}.card{margin-bottom:18rpx;padding:24rpx;border:1rpx solid #e2e9ee;border-radius:18rpx;background:#fff}.head{display:flex;align-items:center;justify-content:space-between;gap:14rpx;padding-bottom:16rpx;border-bottom:1rpx solid #edf1f4}.head text:first-child,.section-title{font-size:25rpx;font-weight:760}.head text:last-child{padding:5rpx 12rpx;border-radius:999rpx;background:#fff3dd;color:#a56811;font-size:19rpx}.row{display:flex;gap:18rpx;padding-top:15rpx;font-size:21rpx;line-height:1.5}.row text:first-child{width:130rpx;flex-shrink:0;color:#7c8997}.row text:last-child{flex:1;color:#344054}.danger{color:#c43d39!important}.secondary{height:66rpx;margin-top:20rpx;border:1rpx solid #315f86;border-radius:13rpx;background:#fff;color:#315f86;font-size:21rpx;line-height:66rpx}.textarea{box-sizing:border-box;width:100%;height:150rpx;margin-top:16rpx;padding:17rpx;border:1rpx solid #e0e7ec;border-radius:13rpx;background:#f9fafb;font-size:21rpx}.photos{display:flex;flex-wrap:wrap;gap:12rpx;margin-top:15rpx}.photo,.add-photo{position:relative;width:132rpx;height:132rpx;margin:0;overflow:hidden;border-radius:12rpx}.photo image{width:100%;height:100%}.photo text{position:absolute;top:5rpx;right:6rpx;display:flex;width:32rpx;height:32rpx;align-items:center;justify-content:center;border-radius:50%;background:rgba(0,0,0,.55);color:#fff}.add-photo{display:flex;align-items:center;justify-content:center;flex-direction:column;border:1rpx dashed #bac6cf;background:#f8fafb;color:#7b8996;font-size:32rpx}.add-photo text{font-size:18rpx}.review-note{display:block;margin-top:18rpx;padding:15rpx;border-radius:10rpx;background:#fff8ee;color:#805d2d;font-size:20rpx}.bottom-action{position:fixed;right:0;bottom:0;left:0;display:flex;gap:12rpx;padding:18rpx 24rpx calc(18rpx + env(safe-area-inset-bottom));border-top:1rpx solid #e3e8ec;background:#fff}.bottom-action button{height:74rpx;margin:0;border-radius:14rpx;font-size:22rpx;line-height:74rpx}.bottom-action button::after,.secondary::after,.add-photo::after{border:0}.primary{flex:1;background:#315f86;color:#fff}.reject{width:190rpx;border:1rpx solid #d55a53;background:#fff;color:#b8403b}.state{padding:120rpx 20rpx;color:#98a2b3;text-align:center}
</style>
