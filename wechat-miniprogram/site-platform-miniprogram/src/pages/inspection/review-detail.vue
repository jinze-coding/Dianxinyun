<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import { getInspectionRecordDetail, reviewInspectionRecord } from '@/api/inspection';
import { getProjectMembers } from '@/api/projectMember';
import { spotCheckCategories } from '@/constants/spotCheck';
import type { CheckResult, InspectionRecord, InspectionReviewLog, ProjectMember } from '@/types';
import { usePageScrollHeight } from '@/utils/navLayout';
import { getQueryNumber, navigateTo, showToast, switchTab } from '@/utils/navigation';

const record = ref<InspectionRecord>();
const comment = ref('');
const requirement = ref(spotCheckCategories[0].template);
const problemCategory = ref(spotCheckCategories[0].value);
const deadline = ref(defaultDeadline());
const submitting = ref(false);
const projectId = ref<number>();
const fromProject = ref(false);
const members = ref<ProjectMember[]>([]);
const assigneeLoading = ref(false);
const assigneeError = ref('');
const selectedAssigneeId = ref<number>();
const selectedAssigneeName = ref('');
const { scrollStyle } = usePageScrollHeight({ bottomRpx: 150, minHeight: 240 });

onShow(async () => {
  const pages = getCurrentPages();
  const current = pages[pages.length - 1] as unknown as { options?: Record<string, string> };
  const id = getQueryNumber(current.options?.id || current.options?.recordId, 5001);
  const parsedProjectId = getQueryNumber(current.options?.projectId, 0);
  projectId.value = parsedProjectId > 0 ? parsedProjectId : undefined;
  fromProject.value = current.options?.from === 'project' || Boolean(projectId.value);
  selectedAssigneeId.value = undefined;
  selectedAssigneeName.value = '';
  record.value = await getInspectionRecordDetail(id);
  if (record.value?.problemCategory) {
    problemCategory.value = record.value.problemCategory;
  }
  if (!projectId.value && record.value?.projectId) {
    projectId.value = Number(record.value.projectId);
  }
  comment.value = record.value?.reviewComment || '';
  await loadProjectMembers();
});

const allPhotos = computed(() => [
  ...(record.value?.outerPhotos || []),
  ...(record.value?.innerPhotos || []),
  ...(record.value?.problemPhotos || [])
]);
const checkResults = computed(() => record.value?.items || []);

const canReview = computed(() => record.value?.status === 'REVIEW_PENDING');
const categoryOptions = computed(() => spotCheckCategories.map((item) => item.label));
const categoryIndex = computed(() => Math.max(spotCheckCategories.findIndex((item) => item.value === problemCategory.value), 0));
const selectedCategoryLabel = computed(() => categoryOptions.value[categoryIndex.value] || '请选择问题分类');
const assigneeOptions = computed(() => members.value
  .filter((member) => member.status !== 0)
  .map((member) => ({
    label: `${member.realName || member.username}${member.projectRoleCode === 'USER' ? ' · 负责电工/成员' : ` · ${roleLabel(member.projectRoleCode)}`}`,
    member
  })));
const selectedAssigneeIndex = computed(() => {
  const index = assigneeOptions.value.findIndex((option) => option.member.userId === selectedAssigneeId.value);
  return index >= 0 ? index : 0;
});
const selectedAssigneeLabel = computed(() => {
  if (selectedAssigneeName.value) return selectedAssigneeName.value;
  if (assigneeError.value) return '无法加载整改人';
  if (!assigneeOptions.value.length) return '暂无项目成员可选';
  return '请选择整改人';
});

async function loadProjectMembers() {
  if (!projectId.value) return;
  assigneeLoading.value = true;
  assigneeError.value = '';
  try {
    members.value = await getProjectMembers(projectId.value);
  } catch (error) {
    members.value = [];
    assigneeError.value = error instanceof Error ? error.message : '项目成员加载失败';
  } finally {
    assigneeLoading.value = false;
  }
}

function formatDateTime(value?: string | null) {
  if (!value) return '-';
  return value.replace('T', ' ').slice(0, 16);
}

function isOverdue() {
  return record.value?.reviewOverdue === true || Number(record.value?.reviewOverdue) === 1;
}

function goBack() {
  if (getCurrentPages().length > 1) {
    uni.navigateBack();
    return;
  }
  if (fromProject.value && projectId.value) {
    navigateTo(`/pages/inspection/review?from=project&projectId=${projectId.value}`);
    return;
  }
  switchTab('/pages/profile/index');
}

function resultLabel(result: CheckResult) {
  if (result === 'ABNORMAL') return '异常';
  if (result === 'NA') return '不适用';
  return '正常';
}

function resultClass(result: CheckResult) {
  if (result === 'ABNORMAL') return 'danger';
  if (result === 'NA') return 'muted';
  return 'normal';
}

function roleLabel(role: string) {
  if (role === 'PROJECT_ADMIN') return '项目管理员';
  if (role === 'SAFETY_ADMIN') return '项目安全员';
  return '项目成员';
}

function defaultDeadline() {
  const date = new Date();
  date.setDate(date.getDate() + 3);
  return date.toISOString().slice(0, 10);
}

function logLabel(log: InspectionReviewLog) {
  const map: Record<string, string> = {
    ASSIGN: '自动分配',
    REASSIGN: '改派复核人',
    UNASSIGN: '进入共享池',
    PASS: '复核通过',
    REJECT: '退回修改',
    RECTIFY: '转整改',
    OVERDUE: '复核逾期'
  };
  return map[log.actionType] || log.actionType;
}

async function submit(action: 'PASS' | 'REJECT' | 'RECTIFY') {
  if (!record.value?.id || submitting.value) return;
  if (action !== 'PASS' && !comment.value.trim()) {
    showToast(action === 'REJECT' ? '请填写退回原因' : '请填写整改原因');
    return;
  }
  if (action === 'RECTIFY') {
    if (!selectedAssigneeId.value || !selectedAssigneeName.value) {
      showToast('请选择整改人');
      return;
    }
    if (!requirement.value.trim()) {
      showToast('请填写整改要求');
      return;
    }
    if (!deadline.value) {
      showToast('请选择整改期限');
      return;
    }
  }
  submitting.value = true;
  try {
    await reviewInspectionRecord(record.value.id, action, {
      comment: comment.value,
      requirement: action === 'RECTIFY' ? requirement.value : undefined,
      assigneeId: action === 'RECTIFY' ? selectedAssigneeId.value : undefined,
      assigneeName: action === 'RECTIFY' ? selectedAssigneeName.value : undefined,
      problemCategory: action === 'RECTIFY' ? problemCategory.value : undefined,
      deadline: action === 'RECTIFY' ? deadline.value : undefined
    });
    uni.setStorageSync('site_platform_todo_filter', action === 'RECTIFY' ? 'RECTIFICATION' : action === 'REJECT' ? 'INSPECTION' : 'REVIEW');
    showToast(action === 'PASS' ? '复核通过' : action === 'REJECT' ? '已退回电工修改' : '已生成整改任务');
    goBack();
  } finally {
    submitting.value = false;
  }
}

function onAssigneeChange(event: unknown) {
  const pickerEvent = event as { detail?: { value?: number | string } };
  const index = Number(pickerEvent.detail?.value || 0);
  const option = assigneeOptions.value[index];
  if (!option) return;
  selectedAssigneeId.value = option.member.userId;
  selectedAssigneeName.value = option.member.realName || option.member.username;
}

function onCategoryChange(event: unknown) {
  const pickerEvent = event as { detail?: { value?: number | string } };
  const category = spotCheckCategories[Number(pickerEvent.detail?.value || 0)];
  if (!category) return;
  problemCategory.value = category.value;
  requirement.value = category.template;
}

function onTemplateChange(event: unknown) {
  const pickerEvent = event as { detail?: { value?: number | string } };
  const category = spotCheckCategories[Number(pickerEvent.detail?.value || 0)];
  if (!category) return;
  requirement.value = category.template;
}

function onDeadlineChange(event: unknown) {
  const pickerEvent = event as { detail?: { value?: string } };
  deadline.value = pickerEvent.detail?.value || deadline.value;
}
</script>

<template>
  <view class="review-detail-page">
    <AppNavBar title="复核详情" @back="goBack" />

    <scroll-view class="scroll" scroll-y enable-flex :style="scrollStyle">
      <view v-if="record" class="stack">
        <view class="card">
          <view class="head-row">
            <view>
              <text class="box-code">{{ record.boxCode }}</text>
              <text class="box-name">{{ record.boxName || '电箱巡检' }}</text>
            </view>
            <text class="status" :class="{ pending: canReview }">{{ canReview ? '待复核' : '已处理' }}</text>
          </view>
          <view class="info-grid">
            <text class="label">巡检人</text>
            <text>{{ record.inspectorName }}</text>
            <text class="label">巡检时间</text>
            <text>{{ record.inspectedAt || record.checkDate }}</text>
            <text class="label">安装位置</text>
            <text>{{ record.installLocation || '-' }}</text>
            <text class="label">异常项</text>
            <text>{{ record.abnormalCount }} 项</text>
            <text class="label">当前复核人</text>
            <text>{{ record.assignedReviewerName || '未分配共享池' }}</text>
            <text class="label">复核截止</text>
            <text :class="{ overdue: isOverdue() }">
              {{ formatDateTime(record.reviewDueTime) }}
              <text v-if="isOverdue()" class="overdue-tag">已逾期</text>
            </text>
            <text class="label">复核状态</text>
            <text>{{ record.reviewStatus || record.status }}</text>
          </view>
        </view>

        <view class="card">
          <view class="section-head">
            <text class="section-title">现场照片</text>
            <text class="hint">共 {{ allPhotos.length }} 张</text>
          </view>
          <view class="photo-grid">
            <image v-for="(photo, index) in allPhotos" :key="`${photo}-${index}`" class="photo" :src="photo" mode="aspectFill" />
            <view v-if="!allPhotos.length" class="photo empty-photo">无照片</view>
          </view>
        </view>

        <view class="card">
          <text class="section-title">检查项结果</text>
          <view class="item-list">
            <view v-for="item in checkResults" :key="item.itemCode" class="item-row">
              <view>
                <text class="item-name">{{ item.itemName }}</text>
                <text v-if="item.description" class="item-desc">{{ item.description }}</text>
              </view>
              <text class="result" :class="resultClass(item.result)">{{ resultLabel(item.result) }}</text>
            </view>
          </view>
        </view>

        <view class="card">
          <view class="section-head">
            <text class="section-title">复核留痕</text>
            <text class="hint">{{ record.reviewLogs?.length || 0 }} 条</text>
          </view>
          <view v-if="record.reviewLogs?.length" class="log-list">
            <view v-for="log in record.reviewLogs" :key="log.id || `${log.actionType}-${log.createTime}`" class="log-row">
              <view class="log-main">
                <text class="log-title">{{ logLabel(log) }}</text>
                <text class="log-time">{{ formatDateTime(log.createTime) }}</text>
              </view>
              <text class="log-operator">
                {{ log.operatorName || '系统' }}
                <text v-if="log.toReviewerName"> → {{ log.toReviewerName }}</text>
                <text v-if="log.fromReviewerName && log.toReviewerName">（原 {{ log.fromReviewerName }}）</text>
              </text>
              <text v-if="log.comment" class="log-comment">{{ log.comment }}</text>
            </view>
          </view>
          <view v-else class="empty-log">暂无复核日志</view>
        </view>

        <view class="card">
          <text class="section-title">复核意见</text>
          <textarea
            v-model="comment"
            class="textarea"
            maxlength="200"
            placeholder="通过可不填；退回或转整改时必填原因"
            :adjust-position="true"
            :cursor-spacing="112"
          />
          <view v-if="canReview" class="requirement-box">
            <text class="label">问题分类</text>
            <picker
              class="picker"
              mode="selector"
              :range="categoryOptions"
              :value="categoryIndex"
              @change="onCategoryChange"
            >
              <view class="picker-display">
                <text>{{ selectedCategoryLabel }}</text>
                <text class="picker-arrow">›</text>
              </view>
            </picker>
            <text class="label">要求模板</text>
            <picker
              class="picker"
              mode="selector"
              :range="categoryOptions"
              :value="categoryIndex"
              @change="onTemplateChange"
            >
              <view class="picker-display">
                <text>套用 {{ selectedCategoryLabel }}</text>
                <text class="picker-arrow">›</text>
              </view>
            </picker>
            <text class="label">整改人</text>
            <picker
              class="picker"
              mode="selector"
              :range="assigneeOptions"
              range-key="label"
              :value="selectedAssigneeIndex"
              @change="onAssigneeChange"
            >
              <view class="picker-display" :class="{ placeholder: !selectedAssigneeName }">
                <text>{{ assigneeLoading ? '正在加载项目成员...' : selectedAssigneeLabel }}</text>
                <text class="picker-arrow">›</text>
              </view>
            </picker>
            <text v-if="assigneeError" class="field-tip error-tip">{{ assigneeError }}</text>
            <text v-else class="field-tip">转整改时必须指定整改人，整改任务会进入该人员待办。</text>
            <text class="label">整改期限</text>
            <picker class="picker" mode="date" :value="deadline" @change="onDeadlineChange">
              <view class="picker-display">
                <text>{{ deadline }}</text>
                <text class="picker-arrow">›</text>
              </view>
            </picker>
            <text class="label">整改要求</text>
            <input v-model="requirement" class="input" />
          </view>
        </view>
      </view>
      <view v-else class="card empty-state">
        <text class="section-title">记录不存在</text>
        <text class="hint">请返回复核列表重新选择</text>
      </view>
    </scroll-view>

    <view v-if="record && canReview" class="bottom-actions">
      <button class="pass" :disabled="submitting" @tap="submit('PASS')">通过</button>
      <button class="reject" :disabled="submitting" @tap="submit('REJECT')">退回</button>
      <button class="rectify" :disabled="submitting" @tap="submit('RECTIFY')">转整改</button>
    </view>
  </view>
</template>

<style scoped>
.review-detail-page {
  height: 100vh;
  min-height: 100vh;
  overflow: hidden;
  padding-bottom: 0;
  background: #eef7ff;
  color: #172033;
}

.nav {
  display: grid;
  grid-template-columns: 88rpx 1fr 88rpx;
  align-items: center;
  height: 116rpx;
  padding: 18rpx 24rpx 0;
  background: #eef7ff;
}

.back {
  font-size: 56rpx;
  font-weight: 300;
}

.title {
  text-align: center;
  font-size: 32rpx;
  font-weight: 900;
}

.scroll {
  box-sizing: border-box;
  padding: 22rpx 24rpx;
}

.stack {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
  padding-bottom: 28rpx;
}

.card {
  padding: 24rpx;
  border: 1rpx solid #dfe7f0;
  border-radius: 12rpx;
  background: #ffffff;
}

.head-row,
.section-head,
.item-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
}

.box-code {
  display: block;
  color: #0f172a;
  font-size: 38rpx;
  font-weight: 900;
}

.box-name,
.hint,
.label,
.item-desc {
  color: #718096;
  font-size: 24rpx;
}

.status {
  padding: 10rpx 16rpx;
  border-radius: 8rpx;
  background: #e2e8f0;
  color: #475569;
  font-size: 24rpx;
  font-weight: 800;
}

.status.pending {
  background: #dff7ed;
  color: #0f9f8f;
}

.info-grid {
  display: grid;
  grid-template-columns: 136rpx minmax(0, 1fr);
  gap: 16rpx 20rpx;
  margin-top: 22rpx;
  font-size: 26rpx;
}

.overdue {
  color: #dc2626;
  font-weight: 800;
}

.overdue-tag {
  display: inline-flex;
  margin-left: 10rpx;
  padding: 4rpx 10rpx;
  border-radius: 999rpx;
  background: #fee2e2;
  color: #dc2626;
  font-size: 22rpx;
  font-weight: 900;
}

.section-title {
  display: block;
  color: #0f172a;
  font-size: 28rpx;
  font-weight: 900;
}

.photo-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12rpx;
  margin-top: 18rpx;
}

.photo {
  width: 100%;
  height: 118rpx;
  border-radius: 8rpx;
  background: #eef2f7;
}

.empty-photo {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #718096;
  font-size: 22rpx;
}

.item-list {
  margin-top: 16rpx;
}

.item-row {
  min-height: 78rpx;
  border-bottom: 1rpx solid #edf2f7;
}

.item-row:last-child {
  border-bottom: 0;
}

.item-name {
  display: block;
  font-size: 26rpx;
  font-weight: 800;
}

.item-desc {
  display: block;
  margin-top: 6rpx;
}

.result {
  min-width: 88rpx;
  padding: 9rpx 14rpx;
  border-radius: 8rpx;
  text-align: center;
  font-size: 24rpx;
  font-weight: 900;
}

.result.normal {
  background: #dff7ed;
  color: #0f9f8f;
}

.result.danger {
  background: #fee2e2;
  color: #dc2626;
}

.result.muted {
  background: #e2e8f0;
  color: #475569;
}

.log-list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
  margin-top: 18rpx;
}

.log-row {
  padding: 16rpx;
  border: 1rpx solid #d9e8fb;
  border-radius: 14rpx;
  background: #f7fbff;
}

.log-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
}

.log-title {
  color: #0f172a;
  font-size: 25rpx;
  font-weight: 900;
}

.log-time,
.log-operator,
.log-comment,
.empty-log {
  display: block;
  color: #718096;
  font-size: 23rpx;
  line-height: 1.55;
}

.log-operator,
.log-comment {
  margin-top: 8rpx;
}

.empty-log {
  margin-top: 18rpx;
}

.textarea {
  width: 100%;
  min-height: 156rpx;
  margin-top: 18rpx;
  padding: 18rpx;
  border: 1rpx solid #d9e2ec;
  border-radius: 10rpx;
  font-size: 25rpx;
}

.requirement-box {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-top: 18rpx;
}

.input {
  width: 100%;
  height: 72rpx;
  padding: 0 18rpx;
  border: 1rpx solid #d9e2ec;
  border-radius: 10rpx;
  font-size: 25rpx;
}

.picker {
  width: 100%;
}

.picker-display {
  display: flex;
  height: 76rpx;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
  padding: 0 20rpx;
  border: 1rpx solid #cde3fb;
  border-radius: 16rpx;
  background: #f5faff;
  color: #172033;
  font-size: 25rpx;
  font-weight: 700;
}

.picker-display.placeholder {
  color: #7a91aa;
  font-weight: 600;
}

.picker-arrow {
  color: #5f83aa;
  font-size: 38rpx;
  line-height: 1;
  transform: rotate(90deg);
}

.field-tip {
  color: #718096;
  font-size: 22rpx;
  line-height: 1.45;
}

.error-tip {
  color: #dc2626;
}

.bottom-actions {
  position: fixed;
  right: 24rpx;
  bottom: 0;
  left: 24rpx;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16rpx;
  padding: 18rpx 0 calc(20rpx + env(safe-area-inset-bottom));
  background: linear-gradient(180deg, rgba(244, 247, 251, 0), #eef7ff 22rpx, #eef7ff);
}

.bottom-actions button {
  display: flex;
  height: 86rpx;
  min-height: 86rpx;
  align-items: center;
  justify-content: center;
  padding: 0 12rpx;
  border-radius: 10rpx;
  font-size: 28rpx;
  font-weight: 900;
  line-height: 1;
  text-align: center;
}

.pass {
  background: #0f9f8f;
  color: #ffffff;
}

.reject {
  border: 1rpx solid #fb923c;
  background: #ffffff;
  color: #f97316;
}

.rectify {
  border: 1rpx solid #2563eb;
  background: #ffffff;
  color: #2563eb;
}

.empty-state {
  margin: 24rpx;
  text-align: center;
}
</style>
