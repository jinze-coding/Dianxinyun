<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import SafetyPhotoUploader from '@/components/SafetyPhotoUploader.vue';
import { getElectricBoxDetail } from '@/api/electricBox';
import { submitSafetySpotCheck } from '@/api/inspection';
import { uploadPhotoIds } from '@/api/file';
import { getProjectMembers } from '@/api/projectMember';
import { spotCheckCategories } from '@/constants/spotCheck';
import { useAuthStore } from '@/stores/auth';
import type { ElectricBox, ProjectMember } from '@/types';
import { usePageScrollHeight } from '@/utils/navLayout';
import { getQueryNumber, navigateTo, showToast, switchTab } from '@/utils/navigation';

const PHOTO_MAX = 4;
const authStore = useAuthStore();
const firstCategory = spotCheckCategories[0];
const box = ref<ElectricBox>();
const members = ref<ProjectMember[]>([]);
const membersLoading = ref(false);
const selectedAssigneeId = ref<number>();
const selectedAssigneeName = ref('');
const problemCategory = ref(firstCategory.value);
const problemPhotos = ref<string[]>([]);
const problemDescription = ref(firstCategory.sampleProblem);
const requirement = ref(firstCategory.template);
const deadline = ref(defaultDeadline());
const submitting = ref(false);
const showConfirm = ref(false);
const { scrollStyle } = usePageScrollHeight({ bottomRpx: 132, minHeight: 240 });

onShow(async () => {
  if (!await authStore.ensureRootAccess('/pages/inspection/index')) return;
  const pages = getCurrentPages();
  const current = pages[pages.length - 1] as unknown as { options?: Record<string, string> };
  const loadedBox = await getElectricBoxDetail(getQueryNumber(current.options?.boxId, 102));
  if (!loadedBox) { showToast('未找到电箱信息'); return; }
  box.value = loadedBox;
  if (!await authStore.ensureProjectPermission(
    '/pages/inspection/index',
    Number(loadedBox.projectId),
    'inspection.submit',
    'INSPECTION_REVIEW'
  )) {
    box.value = undefined;
    return;
  }
  await loadProjectMembers();
});

const canSubmit = computed(() => box.value?.status === 'ACTIVE');
const assigneeOptions = computed(() => members.value.filter((member) => member.status !== 0).map((member) => ({ label: `${member.realName || member.username} · ${roleLabel(member.projectRoleCode)}`, member })));
const assigneeIndex = computed(() => Math.max(assigneeOptions.value.findIndex((option) => option.member.userId === selectedAssigneeId.value), 0));
const selectedAssigneeLabel = computed(() => selectedAssigneeName.value || (membersLoading.value ? '正在加载...' : box.value?.responsibleElectricianName || '请选择整改人'));
const selectedCategoryLabel = computed(() => spotCheckCategories.find((item) => item.value === problemCategory.value)?.label || '其他');

function defaultDeadline() {
  const date = new Date();
  date.setDate(date.getDate() + 3);
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}

function roleLabel(role?: string) { return role === 'PROJECT_ADMIN' ? '项目管理员' : role === 'SAFETY_ADMIN' ? '安全员' : '电工'; }

async function loadProjectMembers() {
  if (!box.value?.projectId) return;
  membersLoading.value = true;
  try {
    members.value = await getProjectMembers(box.value.projectId);
    const responsible = members.value.find((member) => (member.realName || member.username) === box.value?.responsibleElectricianName);
    const target = responsible || members.value.find((member) => member.projectRoleCode === 'USER') || members.value[0];
    if (target && !selectedAssigneeId.value) { selectedAssigneeId.value = target.userId; selectedAssigneeName.value = target.realName || target.username; }
  } catch (error) { members.value = []; selectedAssigneeName.value = box.value?.responsibleElectricianName || ''; }
  finally { membersLoading.value = false; }
}

function selectCategory(value: string) {
  const category = spotCheckCategories.find((item) => item.value === value);
  if (!category) return;
  problemCategory.value = category.value;
  problemDescription.value = category.sampleProblem;
  requirement.value = category.template;
}

function onAssigneeChange(event: { detail: { value: number | string } }) {
  const option = assigneeOptions.value[Number(event.detail.value)];
  if (!option) return;
  selectedAssigneeId.value = option.member.userId;
  selectedAssigneeName.value = option.member.realName || option.member.username;
}

function goBack() { getCurrentPages().length > 1 ? uni.navigateBack() : switchTab('/pages/safety/index'); }

function addPhoto() {
  if (problemPhotos.value.length >= PHOTO_MAX) { showToast(`最多上传${PHOTO_MAX}张`); return; }
  if (import.meta.env.VITE_USE_MOCK === 'true') { problemPhotos.value = [...problemPhotos.value, `/static/mock-photo.svg?spot=${Date.now()}`].slice(0, PHOTO_MAX); return; }
  uni.chooseImage({ count: PHOTO_MAX - problemPhotos.value.length, success: (result) => { problemPhotos.value = [...problemPhotos.value, ...result.tempFilePaths].slice(0, PHOTO_MAX); } });
}
function removePhoto(index: number) { problemPhotos.value = problemPhotos.value.filter((_, currentIndex) => currentIndex !== index); }

function requestSubmit() {
  if (!box.value || submitting.value) return;
  if (!canSubmit.value) { showToast(box.value.status === 'REMOVED' ? '已拆除电箱不可发起抽查整改' : '停用电箱不可发起抽查整改'); return; }
  if (!problemDescription.value.trim()) { showToast('请填写问题说明'); return; }
  if (!problemPhotos.value.length) { showToast('请上传问题照片'); return; }
  if (!requirement.value.trim()) { showToast('请填写整改要求'); return; }
  if (!selectedAssigneeLabel.value) { showToast('请选择整改人'); return; }
  showConfirm.value = true;
}

async function confirmSubmit() {
  if (!box.value || submitting.value) return;
  showConfirm.value = false;
  submitting.value = true;
  try {
    const problemPhotoFileIds = await uploadPhotoIds(problemPhotos.value, 'INSPECTION_PROBLEM_PHOTO', { projectId: box.value.projectId, businessType: 'inspection_record' });
    await submitSafetySpotCheck({ projectId: box.value.projectId, electricBoxId: box.value.id, boxCode: box.value.boxCode, problemCategory: problemCategory.value, problemDescription: problemDescription.value, requirement: requirement.value, deadline: deadline.value, assigneeId: selectedAssigneeId.value, assigneeName: selectedAssigneeName.value || box.value.responsibleElectricianName, problemPhotos: problemPhotos.value, problemPhotoFileIds });
    showToast('已派发整改任务');
    setTimeout(() => navigateTo(`/pages/rectification/index?projectId=${box.value?.projectId}`), 450);
  } catch (error) { showToast(error instanceof Error ? error.message : '派发失败'); }
  finally { submitting.value = false; }
}
</script>

<template>
  <view class="flow-page spot-page">
    <AppNavBar title="安全抽查" @back="goBack" />
    <scroll-view class="flow-scroll" scroll-y enable-flex :style="scrollStyle">
      <view v-if="box" class="flow-content spot-content">
        <view class="box-banner flow-card"><view class="box-mark">电</view><view class="box-copy"><view><text>{{ box.boxCode }}</text><text class="status" :class="{ disabled: !canSubmit }">{{ canSubmit ? '可抽查' : '已停用' }}</text></view><text>{{ box.boxName }} · {{ box.installLocation }}</text></view></view>

        <view class="step-card flow-card">
          <view class="step-title"><text class="step-no">1</text><view><text>记录问题</text><text>选择分类后自动带出模板</text></view></view>
          <scroll-view class="category-scroll" scroll-x :show-scrollbar="false"><view class="category-list"><button v-for="category in spotCheckCategories" :key="category.value" class="category-chip pressable" :class="{ active: problemCategory === category.value }" @tap="selectCategory(category.value)">{{ category.label }}</button></view></scroll-view>
          <view class="textarea-wrap"><textarea v-model="problemDescription" maxlength="200" placeholder="请描述现场不合规问题" :adjust-position="true" :cursor-spacing="112" /><text>{{ problemDescription.length }}/200</text></view>
        </view>

        <view class="step-photo"><view class="step-label"><text class="step-no">2</text><view><text>上传问题照片</text><text>至少1张，用于整改前后对比</text></view></view><SafetyPhotoUploader title="问题照片" :photos="problemPhotos" :max="PHOTO_MAX" hint="清晰呈现不合规部位" @add="addPhoto" @remove="removePhoto" /></view>

        <view class="step-card flow-card">
          <view class="step-title"><text class="step-no">3</text><view><text>确认整改派发</text><text>整改期限由系统固定</text></view></view>
          <text class="field-label">整改要求</text><textarea v-model="requirement" class="requirement-input" maxlength="180" placeholder="填写整改要求" :adjust-position="true" :cursor-spacing="112" />
          <view class="dispatch-row"><text>整改人</text><picker mode="selector" :range="assigneeOptions" range-key="label" :value="assigneeIndex" :disabled="!assigneeOptions.length" @change="onAssigneeChange"><view class="picker-value">{{ selectedAssigneeLabel }}<text class="row-chevron"></text></view></picker></view>
          <view class="deadline-block"><view><text>整改截止</text><text>{{ deadline }} 23:59</text></view><text>第三个自然日</text></view>
        </view>
      </view>
    </scroll-view>

    <view v-if="box" class="bottom-bar"><view><text>{{ selectedAssigneeLabel }}</text><text>截止 {{ deadline }} 23:59</text></view><button class="dispatch-button pressable" :disabled="!canSubmit || submitting" @tap="requestSubmit">{{ submitting ? '派发中' : '派发整改' }}</button></view>

    <view v-if="showConfirm" class="flow-overlay" @tap="showConfirm = false"><view class="flow-sheet" @tap.stop><text class="flow-sheet-title">确认派发整改？</text><text class="flow-sheet-desc">{{ box?.boxCode }} · {{ selectedCategoryLabel }}</text><view class="confirm-list"><view><text>整改人</text><text>{{ selectedAssigneeLabel }}</text></view><view><text>截止时间</text><text>{{ deadline }} 23:59</text></view><view><text>问题照片</text><text>{{ problemPhotos.length }} 张</text></view></view><view class="flow-sheet-actions"><button class="flow-sheet-cancel" @tap="showConfirm = false">取消</button><button class="flow-sheet-confirm danger-confirm" @tap="confirmSubmit">确认派发</button></view></view></view>
  </view>
</template>

<style scoped src="../../styles/safety-flow.css"></style>
<style scoped>
.spot-content { display: flex; flex-direction: column; gap: 16rpx; padding-bottom: 30rpx; }
.box-banner { display: flex; align-items: center; gap: 15rpx; padding: 19rpx 21rpx; background: linear-gradient(135deg, #fff9f2, #fff); }.box-mark { display: flex; width: 54rpx; height: 54rpx; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 14rpx; background: #ad6723; color: #fff; font-size: 24rpx; font-weight: 900; }.box-copy { min-width: 0; flex: 1; }.box-copy view { display: flex; align-items: center; justify-content: space-between; }.box-copy view>text:first-child { font-size: 27rpx; font-weight: 900; }.box-copy>text { display: block; margin-top: 4rpx; overflow: hidden; color: #788493; font-size: 20rpx; text-overflow: ellipsis; white-space: nowrap; }.status { padding: 5rpx 12rpx; border-radius: 999rpx; background: #e4f5eb; color: #188950; font-size: 18rpx; }.status.disabled { background: #f1efeb; color: #888178; }
.step-card { padding: 22rpx; }.step-title, .step-label { display: flex; align-items: center; gap: 13rpx; }.step-title view text, .step-label view text { display: block; }.step-title view text:first-child, .step-label view text:first-child { font-size: 25rpx; font-weight: 850; }.step-title view text:last-child, .step-label view text:last-child { margin-top: 3rpx; color: #929aa4; font-size: 19rpx; }.step-no { display: flex; width: 38rpx; height: 38rpx; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 50%; background: #fff0df; color: #ac6726; font-size: 20rpx; font-weight: 900; }
.category-scroll { width: 100%; margin-top: 19rpx; white-space: nowrap; }.category-list { display: inline-flex; gap: 9rpx; padding-right: 2rpx; }.category-chip { height: 56rpx; margin: 0; padding: 0 20rpx; border: 1rpx solid #e9e4dc; border-radius: 999rpx; background: #faf9f7; color: #697382; font-size: 20rpx; line-height: 56rpx; }.category-chip::after { border: 0; }.category-chip.active { border-color: #e7bc91; background: #fff0df; color: #a85f1f; font-weight: 750; }
.textarea-wrap { position: relative; margin-top: 16rpx; }.textarea-wrap textarea, .requirement-input { box-sizing: border-box; width: 100%; height: 142rpx; padding: 16rpx 16rpx 36rpx; border-radius: 14rpx; background: #f8f7f5; color: #3b4657; font-size: 21rpx; line-height: 1.5; }.textarea-wrap>text { position: absolute; right: 13rpx; bottom: 10rpx; color: #a3a9b1; font-size: 18rpx; }
.step-photo { display: flex; flex-direction: column; gap: 11rpx; }.step-label { padding: 2rpx 7rpx; }
.field-label { display: block; margin: 20rpx 0 10rpx; color: #667181; font-size: 20rpx; }.requirement-input { height: 126rpx; padding-bottom: 16rpx; }
.dispatch-row { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; margin-top: 16rpx; padding: 16rpx 0; border-top: 1rpx solid #f0ede8; color: #657080; font-size: 21rpx; }.picker-value { display: flex; align-items: center; gap: 10rpx; color: #283548; font-weight: 750; }.row-chevron { width: 9rpx; height: 9rpx; border-right: 2rpx solid #99714d; border-bottom: 2rpx solid #99714d; transform: rotate(45deg) translateY(-2rpx); }
.deadline-block { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; margin-top: 2rpx; padding: 17rpx; border-radius: 14rpx; background: #fff6ea; }.deadline-block view text { display: block; }.deadline-block view text:first-child { color: #a5784b; font-size: 18rpx; }.deadline-block view text:last-child { margin-top: 3rpx; color: #6f431d; font-size: 24rpx; font-weight: 850; }.deadline-block>text { color: #b06b29; font-size: 19rpx; }
.bottom-bar { position: fixed; z-index: 40; right: 0; bottom: 0; left: 0; display: flex; min-height: 106rpx; align-items: center; gap: 18rpx; padding: 15rpx 24rpx calc(15rpx + env(safe-area-inset-bottom)); border-top: 1rpx solid #ebe6df; background: rgba(255, 255, 255, .96); box-shadow: 0 -10rpx 28rpx rgba(39, 35, 29, .08); }.bottom-bar>view { display: flex; min-width: 0; align-self: stretch; justify-content: center; flex: 1; flex-direction: column; }.bottom-bar>view text { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.bottom-bar>view text:first-child { font-size: 20rpx; font-weight: 750; line-height: 1.3; }.bottom-bar>view text:last-child { margin-top: 3rpx; color: #9098a2; font-size: 18rpx; line-height: 1.3; }.dispatch-button { display: flex; width: 246rpx; height: 76rpx; min-height: 76rpx; align-items: center; justify-content: center; margin: 0; padding: 0 18rpx; border-radius: 15rpx; background: #b44b43; color: #fff; font-size: 24rpx; font-weight: 800; line-height: 1; text-align: center; }.dispatch-button::after { border: 0; }.dispatch-button[disabled] { opacity: .5; }
.confirm-list { margin-top: 22rpx; padding: 6rpx 18rpx; border-radius: 16rpx; background: #f7f5f1; }.confirm-list view { display: flex; align-items: center; justify-content: space-between; padding: 14rpx 0; color: #707a87; font-size: 21rpx; }.confirm-list view+view { border-top: 1rpx solid #ebe7e0; }.confirm-list view text:last-child { color: #2f3c4f; font-weight: 750; }.danger-confirm { background: #b44b43; }
</style>
