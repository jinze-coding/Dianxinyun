<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import SafetyPhotoUploader from '@/components/SafetyPhotoUploader.vue';
import { getElectricBoxDetail } from '@/api/electricBox';
import { createDefaultCheckItems, getInspectionRecordDetail, getInspectionRecords, submitInspectionRecord } from '@/api/inspection';
import { deleteFileResources, uploadPhotoIds } from '@/api/file';
import { useAuthStore } from '@/stores/auth';
import type { CheckResult, ElectricBox, InspectionRecord } from '@/types';
import { usePageScrollHeight } from '@/utils/navLayout';
import { getQueryNumber, showToast, switchTab } from '@/utils/navigation';

const PHOTO_MAX = 4;
const authStore = useAuthStore();
const box = ref<ElectricBox>();
const outerPhotos = ref<string[]>([]);
const innerPhotos = ref<string[]>([]);
const remark = ref('');
const items = ref(createDefaultCheckItems());
const loading = ref(true);
const loadError = ref('');
const submitting = ref(false);
const checkingDuplicate = ref(false);
const duplicateDailyRecord = ref<InspectionRecord>();
const showConfirm = ref(false);
const initialized = ref(false);
const choosingPhoto = ref(false);
const routeOptions = ref<Record<string, string>>({});
const { scrollStyle } = usePageScrollHeight({ bottomRpx: 126, minHeight: 240 });

function pad(value: number) { return `${value}`.padStart(2, '0'); }
function toLocalDateString(date: Date) { return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`; }
function addDays(date: Date, days: number) { const next = new Date(date); next.setDate(next.getDate() + days); return next; }

const maxCheckDate = toLocalDateString(new Date());
const minCheckDate = toLocalDateString(addDays(new Date(), -7));
const checkDate = ref(maxCheckDate);
const resultOptions: Array<{ label: string; value: CheckResult }> = [
  { label: '正常', value: 'NORMAL' }, { label: '异常', value: 'ABNORMAL' }, { label: '不适用', value: 'NA' }
];

const completedPhotos = computed(() => outerPhotos.value.length + innerPhotos.value.length);
const completedItems = computed(() => items.value.filter((item) => item.result).length);
const abnormalCount = computed(() => items.value.filter((item) => item.result === 'ABNORMAL').length);
const normalCount = computed(() => items.value.filter((item) => item.result === 'NORMAL').length);
const naCount = computed(() => items.value.filter((item) => item.result === 'NA').length);
const readOnly = computed(() => Boolean(duplicateDailyRecord.value));

onLoad((options) => {
  routeOptions.value = Object.fromEntries(
    Object.entries(options || {}).map(([key, value]) => [key, String(value ?? '')])
  );
  void loadForm();
});

async function loadForm(force = false) {
  if (initialized.value && !force) return;
  initialized.value = true;
  loading.value = true;
  loadError.value = '';
  box.value = undefined;
  try {
    if (!await authStore.ensureRootAccess('/pages/inspection/index')) return;
    const boxId = getQueryNumber(routeOptions.value.boxId, 101);
    const recordId = getQueryNumber(routeOptions.value.recordId, 0);
    duplicateDailyRecord.value = undefined;
    resetDraft();
    const loadedBox = await getElectricBoxDetail(boxId);
    if (!loadedBox) throw new Error('未找到电箱信息');
    box.value = loadedBox;
    if (!await authStore.ensureProjectPermission(
      '/pages/inspection/index',
      Number(loadedBox.projectId),
      'inspection.submit',
      'INSPECTION_DAILY_SUBMIT'
    )) {
      box.value = undefined;
      return;
    }
    if (recordId > 0) {
      const existing = await getInspectionRecordDetail(recordId);
      if (existing) applyExistingRecord(existing);
    } else {
      await checkExistingDailyRecord(false);
    }
  } catch (error) {
    box.value = undefined;
    loadError.value = error instanceof Error ? error.message : '巡检信息加载失败';
  } finally {
    loading.value = false;
  }
}

function goBack() { getCurrentPages().length > 1 ? uni.navigateBack() : switchTab('/pages/inspection/index'); }
function returnToInspectionHome() { switchTab('/pages/inspection/index'); }

function addPhoto(type: 'outer' | 'inner') {
  if (readOnly.value || choosingPhoto.value) return;
  const target = type === 'outer' ? outerPhotos : innerPhotos;
  if (target.value.length >= PHOTO_MAX) { showToast(`最多上传${PHOTO_MAX}张`); return; }
  if (import.meta.env.VITE_USE_MOCK === 'true') {
    target.value = [...target.value, `/static/mock-photo.svg?time=${Date.now()}-${type}`].slice(0, PHOTO_MAX);
    return;
  }
  choosingPhoto.value = true;
  uni.chooseImage({
    count: PHOTO_MAX - target.value.length,
    success: (result) => {
      target.value = [...target.value, ...result.tempFilePaths].slice(0, PHOTO_MAX);
    },
    complete: () => {
      choosingPhoto.value = false;
    }
  });
}

function removePhoto(type: 'outer' | 'inner', index: number) {
  if (readOnly.value) return;
  const target = type === 'outer' ? outerPhotos : innerPhotos;
  target.value = target.value.filter((_, currentIndex) => currentIndex !== index);
}

function updateResult(itemCode: string, result: CheckResult) {
  if (readOnly.value) return;
  const item = items.value.find((current) => current.itemCode === itemCode);
  if (!item) return;
  item.result = result;
  if (result !== 'ABNORMAL') item.description = '';
}

function updateDescription(itemCode: string, event: unknown) {
  const item = items.value.find((current) => current.itemCode === itemCode);
  if (!item) return;
  const inputEvent = event as { detail?: { value?: string }; target?: { value?: string } };
  item.description = inputEvent.detail?.value ?? inputEvent.target?.value ?? '';
}

async function checkExistingDailyRecord(notify = false) {
  if (!box.value) return undefined;
  checkingDuplicate.value = true;
  try {
    const records = await getInspectionRecords(box.value.projectId, box.value.id);
    const existing = records.find((record) => record.source === 'ELECTRICIAN_DAILY' && record.checkDate === checkDate.value);
    if (existing?.id) {
      const detail = await getInspectionRecordDetail(existing.id);
      applyExistingRecord(detail || existing);
    } else {
      duplicateDailyRecord.value = undefined;
    }
    if (existing && notify) showToast('该日期已完成巡检');
    return existing;
  } catch (error) {
    console.warn('查询日检重复记录失败', error);
    duplicateDailyRecord.value = undefined;
    return undefined;
  } finally { checkingDuplicate.value = false; }
}

async function changeCheckDate(event: unknown) {
  const pickerEvent = event as { detail?: { value?: string } };
  const value = pickerEvent.detail?.value || maxCheckDate;
  if (value < minCheckDate || value > maxCheckDate) { showToast('仅允许选择近7天日期'); return; }
  checkDate.value = value;
  resetDraft();
  await checkExistingDailyRecord(true);
}

function resetDraft() {
  duplicateDailyRecord.value = undefined;
  outerPhotos.value = [];
  innerPhotos.value = [];
  remark.value = '';
  items.value = createDefaultCheckItems();
}

function applyExistingRecord(record: InspectionRecord) {
  duplicateDailyRecord.value = record;
  checkDate.value = record.checkDate || checkDate.value;
  outerPhotos.value = record.outerPhotos || [];
  innerPhotos.value = record.innerPhotos || [];
  remark.value = record.remark || '';
  const existingItems = new Map((record.items || []).map((item) => [item.itemCode, item]));
  items.value = createDefaultCheckItems().map((item) => ({ ...item, ...(existingItems.get(item.itemCode) || {}) }));
}

async function requestSubmit() {
  if (!box.value || submitting.value || readOnly.value) return;
  if (box.value.status !== 'ACTIVE') { showToast(box.value.status === 'REMOVED' ? '已拆除电箱不可提交日检' : '停用电箱不可提交日检'); return; }
  if (items.value.some((item) => !item.result)) { showToast('请完成六项检查结果'); return; }
  const missingDescription = items.value.find((item) => item.result === 'ABNORMAL' && !item.description?.trim());
  if (missingDescription) { showToast(`${missingDescription.itemName}异常时请填写说明`); return; }
  if (await checkExistingDailyRecord(false)) { showToast('该日期已提交日检'); return; }
  showConfirm.value = true;
}

async function confirmSubmit() {
  if (!box.value || submitting.value) return;
  showConfirm.value = false;
  submitting.value = true;
  const uploadedFileIds: number[] = [];
  try {
    let outerPhotoFileIds: number[] = [];
    try {
      outerPhotoFileIds = await uploadPhotoIds(outerPhotos.value, 'INSPECTION_OUTER_PHOTO', { projectId: box.value.projectId, businessType: 'inspection_record' });
    } catch (error) {
      throw new Error(`外观照片上传失败：${error instanceof Error ? error.message : '请检查网络后重试'}`);
    }
    uploadedFileIds.push(...outerPhotoFileIds);
    let innerPhotoFileIds: number[] = [];
    try {
      innerPhotoFileIds = await uploadPhotoIds(innerPhotos.value, 'INSPECTION_INNER_PHOTO', { projectId: box.value.projectId, businessType: 'inspection_record' });
    } catch (error) {
      throw new Error(`内部照片上传失败：${error instanceof Error ? error.message : '请检查网络后重试'}`);
    }
    uploadedFileIds.push(...innerPhotoFileIds);
    await submitInspectionRecord({ projectId: box.value.projectId, electricBoxId: box.value.id, boxCode: box.value.boxCode, checkDate: checkDate.value, remark: remark.value, outerPhotoFileIds, innerPhotoFileIds, outerPhotos: outerPhotos.value, innerPhotos: innerPhotos.value, items: items.value });
    resetDraft();
    showToast('巡检已完成');
    setTimeout(() => switchTab('/pages/inspection/index'), 450);
  } catch (error) {
    await deleteFileResources(uploadedFileIds);
    const message = error instanceof Error ? error.message : '提交失败';
    showToast(`${message}；本地草稿已保留`);
  }
  finally { submitting.value = false; }
}
</script>

<template>
  <view class="flow-page inspection-page">
    <AppNavBar title="电箱巡检" @back="goBack" />
    <scroll-view class="flow-scroll" scroll-y enable-flex :style="scrollStyle">
      <view v-if="loading" class="flow-content state-content">
        <view class="flow-card state-card">
          <view class="loading-indicator"></view>
          <text class="state-title">正在加载巡检信息</text>
          <text class="state-description">请稍候，不要重复扫描二维码</text>
        </view>
      </view>
      <view v-else-if="loadError" class="flow-content state-content">
        <view class="flow-card state-card error-state">
          <text class="state-mark">!</text>
          <text class="state-title">巡检页面加载失败</text>
          <text class="state-description">{{ loadError }}</text>
          <text class="state-hint">请确认网络已恢复后重新加载</text>
          <view class="state-actions">
            <button class="state-button secondary" @tap="returnToInspectionHome">返回巡检首页</button>
            <button class="state-button primary" @tap="loadForm(true)">重新加载</button>
          </view>
        </view>
      </view>
      <view v-else-if="box" class="flow-content inspection-content">
        <view class="device-card flow-card">
          <view class="device-top"><view><text class="box-code">{{ box.boxCode }}</text><text class="box-name">{{ box.boxName }}</text></view><text class="scope-pill">{{ box.inspectionRequired === false ? '非日检' : '日检中' }}</text></view>
          <view class="device-meta"><text>{{ box.installLocation }}</text><picker mode="date" :value="checkDate" :start="minCheckDate" :end="maxCheckDate" :disabled="readOnly" @change="changeCheckDate"><view class="date-button">{{ checkDate }}<text class="chevron"></text></view></picker></view>
          <text class="date-hint">可补录近7天巡检</text>
        </view>

          <view v-if="readOnly" class="readonly-banner flow-card"><text class="readonly-icon">✓</text><view><text>今日巡检已完成</text><text>{{ duplicateDailyRecord?.inspectorName || '巡检员' }} · {{ duplicateDailyRecord?.inspectedAt?.replace('T', ' ').slice(0, 16) || checkDate }}</text></view></view>

          <SafetyPhotoUploader title="外观照片" :photos="outerPhotos" :max="PHOTO_MAX" :required="false" :readonly="readOnly" :hint="readOnly ? '巡检记录照片' : '选填，拍摄箱体整体'" @add="addPhoto('outer')" @remove="removePhoto('outer', $event)" />
          <SafetyPhotoUploader title="内部照片" :photos="innerPhotos" :max="PHOTO_MAX" :required="false" :readonly="readOnly" :hint="readOnly ? '巡检记录照片' : '选填，拍摄内部接线'" @add="addPhoto('inner')" @remove="removePhoto('inner', $event)" />

          <view class="check-card flow-card">
            <view class="section-heading"><view><text>检查项</text><text>六项均需确认</text></view><text>{{ completedItems }}/{{ items.length }}</text></view>
            <view v-for="(item, index) in items" :key="item.itemCode" class="check-item stagger-item" :style="{ animationDelay: `${index * 28}ms` }">
              <view class="check-row"><text class="check-name">{{ item.itemName }}</text><view class="result-tabs"><button v-for="option in resultOptions" :key="option.value" :disabled="readOnly" :class="{ active: item.result === option.value, danger: item.result === option.value && option.value === 'ABNORMAL' }" @tap="updateResult(item.itemCode, option.value)">{{ option.label }}</button></view></view>
              <view v-if="item.result === 'ABNORMAL'" class="abnormal-description">
                <view class="abnormal-heading"><text>异常说明</text><text>必填 · {{ item.description?.length || 0 }}/500</text></view>
                <textarea class="abnormal-input" :value="item.description || ''" maxlength="500" :disabled="readOnly" placeholder="请描述异常情况及现场位置" :adjust-position="true" :cursor-spacing="112" @input="updateDescription(item.itemCode, $event)" />
              </view>
            </view>
          </view>

          <view class="remark-card flow-card"><view class="section-heading"><view><text>备注</text><text>{{ readOnly ? '巡检记录' : '选填' }}</text></view><text v-if="!readOnly">{{ remark.length }}/200</text></view><textarea v-model="remark" class="remark-input" maxlength="200" :disabled="readOnly" :placeholder="readOnly ? '无备注' : '补充现场情况'" :adjust-position="true" :cursor-spacing="112" /></view>
      </view>
    </scroll-view>

    <view v-if="box && !readOnly" class="bottom-bar"><view class="completion"><text>已上传照片 {{ completedPhotos }} 张（选填）</text><text>检查项 {{ completedItems }}/{{ items.length }}</text></view><button class="submit-button pressable" :disabled="submitting || checkingDuplicate" @tap="requestSubmit">{{ submitting ? '提交中' : '完成巡检' }}</button></view>

    <view v-if="showConfirm" class="flow-overlay" @tap="showConfirm = false"><view class="flow-sheet" @tap.stop><text class="flow-sheet-title">确认完成巡检？</text><text class="flow-sheet-desc">{{ box?.boxCode }} · {{ checkDate }}</text><view class="confirm-summary"><text>外观照片 {{ outerPhotos.length }} 张</text><text>内部照片 {{ innerPhotos.length }} 张</text><text>正常 {{ normalCount }} 项</text><text class="danger-text">异常 {{ abnormalCount }} 项</text><text>不适用 {{ naCount }} 项</text></view><view class="flow-sheet-actions"><button class="flow-sheet-cancel" @tap="showConfirm = false">再检查一下</button><button class="flow-sheet-confirm" @tap="confirmSubmit">确认完成</button></view></view></view>
  </view>
</template>

<style scoped src="../../styles/safety-flow.css"></style>
<style scoped>
.inspection-content { display: flex; flex-direction: column; gap: 16rpx; padding-bottom: 30rpx; }
.state-content { display: flex; min-height: 62vh; align-items: center; justify-content: center; }
.state-card { display: flex; box-sizing: border-box; width: 100%; align-items: center; flex-direction: column; padding: 52rpx 30rpx; text-align: center; }
.loading-indicator { width: 42rpx; height: 42rpx; border: 5rpx solid #dceaf5; border-top-color: var(--inspection-primary-deep); border-radius: 50%; animation: state-spin .8s linear infinite; }
.state-mark { display: flex; width: 58rpx; height: 58rpx; align-items: center; justify-content: center; border-radius: 18rpx; background: var(--inspection-danger-soft); color: var(--inspection-danger); font-size: 32rpx; font-weight: 900; }
.state-title { margin-top: 22rpx; color: var(--inspection-text); font-size: 27rpx; font-weight: 850; }
.state-description { max-width: 560rpx; margin-top: 10rpx; color: #65778a; font-size: 21rpx; line-height: 1.6; word-break: break-all; }
.state-hint { margin-top: 8rpx; color: var(--inspection-muted); font-size: 19rpx; }
.state-actions { display: grid; width: 100%; grid-template-columns: 1fr 1fr; gap: 14rpx; margin-top: 30rpx; }
.state-button { display: flex; height: 70rpx; align-items: center; justify-content: center; margin: 0; border-radius: 14rpx; font-size: 21rpx; font-weight: 750; line-height: 1; }
.state-button::after { border: 0; }
.state-button.secondary { border: 1rpx solid var(--inspection-border); background: #fff; color: var(--inspection-primary-deep); }
.state-button.primary { background: var(--inspection-primary-deep); color: #fff; }
@keyframes state-spin { to { transform: rotate(360deg); } }
.device-card { padding: 22rpx; background: linear-gradient(135deg, #edf5fc, #fff); }.device-top, .device-meta { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; }.box-code, .box-name, .date-hint { display: block; }.box-code { color: var(--inspection-text); font-size: 29rpx; font-weight: 900; }.box-name { margin-top: 3rpx; color: #65768a; font-size: 21rpx; }.scope-pill { padding: 6rpx 13rpx; border-radius: 999rpx; background: var(--inspection-success-soft); color: var(--inspection-success); font-size: 19rpx; }.device-meta { margin-top: 18rpx; padding-top: 16rpx; border-top: 1rpx solid var(--inspection-divider); color: #6d7f91; font-size: 21rpx; }.device-meta>text { max-width: 54%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.date-button { display: flex; align-items: center; gap: 10rpx; color: #3d5167; font-size: 22rpx; font-weight: 750; }.chevron { width: 10rpx; height: 10rpx; border-right: 2rpx solid #6d8ba7; border-bottom: 2rpx solid #6d8ba7; transform: rotate(45deg) translateY(-3rpx); }.date-hint { margin-top: 9rpx; color: var(--inspection-muted); font-size: 19rpx; }
.readonly-banner { display: flex; align-items: center; gap: 15rpx; padding: 22rpx; border-color: #cee8da; background: #f5fbf8; }.readonly-icon { display: flex; width: 48rpx; height: 48rpx; align-items: center; justify-content: center; border-radius: 50%; background: #dff2e8; color: #168557; font-weight: 900; }.readonly-banner view text { display: block; }.readonly-banner view text:first-child { color: #266048; font-size: 24rpx; font-weight: 800; }.readonly-banner view text:last-child { margin-top: 4rpx; color: #72877d; font-size: 20rpx; }
.check-card, .remark-card { padding: 22rpx; }.section-heading { display: flex; align-items: center; justify-content: space-between; padding-bottom: 16rpx; }.section-heading view text { display: block; }.section-heading view text:first-child { color: var(--inspection-text); font-size: 26rpx; font-weight: 850; }.section-heading view text:last-child { margin-top: 3rpx; color: #929eaa; font-size: 19rpx; }.section-heading>text { color: var(--inspection-primary-deep); font-size: 20rpx; font-weight: 750; }
.check-item { padding: 16rpx 0; border-top: 1rpx solid var(--inspection-divider); }.check-row { display: flex; min-height: 58rpx; align-items: center; justify-content: space-between; gap: 14rpx; }.check-name { display: flex; min-width: 114rpx; align-items: center; color: var(--inspection-text); font-size: 22rpx; font-weight: 700; line-height: 1.25; }.result-tabs { display: grid; grid-template-columns: repeat(3, 1fr); gap: 5rpx; width: 360rpx; padding: 5rpx; border-radius: 13rpx; background: #eef3f7; }.result-tabs button { display: flex; height: 48rpx; min-height: 48rpx; align-items: center; justify-content: center; margin: 0; padding: 0; border-radius: 9rpx; background: transparent; color: #727f8e; font-size: 19rpx; line-height: 1; text-align: center; }.result-tabs button::after { border: 0; }.result-tabs button.active { background: var(--inspection-success-soft); color: var(--inspection-success); box-shadow: inset 0 0 0 1rpx rgba(47,128,101,.14); }.result-tabs button.active.danger { background: var(--inspection-danger-soft); color: var(--inspection-danger); box-shadow: inset 0 0 0 1rpx rgba(183,83,83,.14); }
.result-tabs button[disabled] { opacity: 1; }
.abnormal-description { margin-top: 12rpx; padding: 14rpx; border: 1rpx solid #f0cfca; border-radius: 13rpx; background: #fff8f7; }.abnormal-heading { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; }.abnormal-heading text:first-child { color: var(--inspection-danger); font-size: 20rpx; font-weight: 750; }.abnormal-heading text:last-child { color: #a77b77; font-size: 18rpx; }.abnormal-input { box-sizing: border-box; width: 100%; height: 108rpx; margin-top: 10rpx; padding: 12rpx; border: 1rpx solid #f0cfca; border-radius: 10rpx; background: #fff; color: #3b4657; font-size: 21rpx; line-height: 1.45; }
.remark-input { box-sizing: border-box; width: 100%; height: 122rpx; padding: 16rpx; border: 1rpx solid var(--inspection-divider); border-radius: 14rpx; background: #f7fafc; color: #3b4d61; font-size: 21rpx; }
.remark-input[disabled] { color: #3b4d61; opacity: 1; }
.bottom-bar { position: fixed; z-index: 40; right: 0; bottom: 0; left: 0; display: flex; min-height: 108rpx; align-items: center; gap: 18rpx; padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom)); border-top: 1rpx solid var(--inspection-divider); background: rgba(255, 255, 255, .97); box-shadow: 0 -10rpx 28rpx rgba(49,95,134,.07); }.completion { display: flex; min-width: 0; align-self: stretch; justify-content: center; flex: 1; flex-direction: column; }.completion text { display: block; color: #78899a; font-size: 19rpx; line-height: 1.45; }.submit-button { display: flex; width: 264rpx; height: 76rpx; min-height: 76rpx; align-items: center; justify-content: center; margin: 0; padding: 0 20rpx; border: 1rpx solid var(--inspection-border); border-radius: 15rpx; background: var(--inspection-soft); color: var(--inspection-primary-deep); font-size: 24rpx; font-weight: 800; line-height: 1; text-align: center; box-shadow: 0 6rpx 16rpx rgba(49,95,134,.08); }.submit-button::after { border: 0; }.submit-button:active { background: var(--inspection-soft-strong); }.submit-button[disabled] { background: #eef2f5; color: #98a5b3; opacity: 1; }
.confirm-summary { display: grid; grid-template-columns: 1fr 1fr; gap: 12rpx; margin-top: 22rpx; padding: 18rpx; border-radius: 16rpx; background: #f1f6fa; color: #5f7082; font-size: 21rpx; }.danger-text { color: var(--inspection-danger); }
</style>
