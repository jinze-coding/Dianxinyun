<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import WorkspaceStatusPill from '@/components/workspace/WorkspaceStatusPill.vue';
import { getQualityWeeklyInspection } from '@/api/quality';
import { downloadFileResults } from '@/api/file';
import { useAuthStore } from '@/stores/auth';
import type { QualityIssue, QualityWeeklyInspection } from '@/types';
import { getQueryNumber, showToast, switchTab } from '@/utils/navigation';

interface IssuePhotoComparison {
  issueId: number;
  beforePaths: string[];
  afterPaths: string[];
  beforeFailed: number;
  afterFailed: number;
}

const authStore = useAuthStore();
const loading = ref(true);
const errorMessage = ref('');
const inspection = ref<QualityWeeklyInspection>();
const overviewPaths = ref<string[]>([]);
const overviewFailed = ref(0);
const comparisons = ref<IssuePhotoComparison[]>([]);

const canManage = computed(() => Boolean(inspection.value)
  && authStore.hasProjectPermission(inspection.value!.projectId, 'quality.manage'));
const totalOpen = computed(() => (inspection.value?.pendingCount || 0) + (inspection.value?.recheckCount || 0));

onLoad(async (query) => {
  const id = getQueryNumber(query?.id, 0);
  if (!id) { errorMessage.value = '缺少周检编号'; loading.value = false; return; }
  try {
    if (!await authStore.ensureRootAccess('/pages/quality/index')) return;
    const value = await getQualityWeeklyInspection(id);
    if (!await authStore.ensureProjectPermission('/pages/quality/index', value.projectId, 'quality.view')) return;
    inspection.value = value;
    await loadPhotos(value);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '周检详情加载失败';
  } finally {
    loading.value = false;
  }
});

async function resolvePaths(ids: number[]) {
  const results = await downloadFileResults(ids || []);
  return {
    paths: results.flatMap((result) => result.path ? [result.path] : []),
    failed: results.filter((result) => !result.path).length
  };
}

async function loadPhotos(value: QualityWeeklyInspection) {
  const overview = await resolvePaths(value.overviewPhotoFileIds || []);
  overviewPaths.value = overview.paths;
  overviewFailed.value = overview.failed;
  comparisons.value = await Promise.all((value.issues || []).map(async (issue) => {
    const [before, after] = await Promise.all([
      resolvePaths(issue.originalProblemPhotoFileIds || issue.issuePhotoFileIds || []),
      resolvePaths(issue.latestRectificationPhotoFileIds || [])
    ]);
    return {
      issueId: issue.id,
      beforePaths: before.paths,
      afterPaths: after.paths,
      beforeFailed: before.failed,
      afterFailed: after.failed
    };
  }));
}

function goBack() {
  if (getCurrentPages().length > 1) uni.navigateBack();
  else switchTab('/pages/quality/index');
}

function editDraft() {
  if (!inspection.value || inspection.value.status !== 'DRAFT' || !canManage.value) return;
  uni.redirectTo({ url: `/pages/quality/weekly-edit?projectId=${inspection.value.projectId}&weekStart=${inspection.value.weekStart}` });
}

function comparison(issueId: number) {
  return comparisons.value.find((item) => item.issueId === issueId);
}

function preview(paths: string[], current: string) {
  if (paths.length) uni.previewImage({ urls: paths, current });
}

function openIssue(issue: QualityIssue) {
  uni.navigateTo({ url: `/pages/quality/issue-detail?id=${issue.id}` });
}

function issueStatusLabel(issue: QualityIssue) {
  if (issue.status === 'VOIDED') return '已作废';
  if (issue.overdue) return '已逾期';
  if (issue.status === 'PENDING') return '待整改';
  if (issue.status === 'RECHECK') return '待复查';
  return '已关闭';
}

function issueStatusTone(issue: QualityIssue) {
  if (issue.status === 'VOIDED') return 'gray' as const;
  if (issue.overdue) return 'red' as const;
  if (issue.status === 'PENDING') return 'amber' as const;
  if (issue.status === 'RECHECK') return 'blue' as const;
  return 'green' as const;
}

function severityLabel(value: string) {
  return value === 'DANGER' ? '严重' : value === 'WARNING' ? '重要' : '一般';
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}
</script>

<template>
  <view class="weekly-detail-page">
    <AppNavBar title="质量周检详情" @back="goBack" />
    <view v-if="loading" class="state-card">正在加载周检详情...</view>
    <view v-else-if="errorMessage" class="state-card error"><text>{{ errorMessage }}</text><button @tap="goBack">返回质量周检</button></view>
    <scroll-view v-else-if="inspection" class="detail-scroll" scroll-y>
      <view class="detail-content">
        <view class="hero-card">
          <view class="hero-head"><WorkspaceStatusPill :label="inspection.status === 'DRAFT' ? '共享草稿' : '已提交'" :tone="inspection.status === 'DRAFT' ? 'amber' : 'green'" /><text v-if="inspection.lateSubmission" class="late-tag">补录</text></view>
          <text class="inspection-no">{{ inspection.inspectionNo || '草稿尚未生成周检编号' }}</text>
          <text class="week-range">{{ inspection.weekStart }} 至 {{ inspection.weekEnd }}</text>
          <view class="hero-grid"><view><text>问题</text><text>{{ inspection.status === 'DRAFT' ? inspection.draftItems.length : inspection.submittedIssueCount }}</text></view><view><text>未闭环</text><text>{{ totalOpen }}</text></view><view><text>已关闭</text><text>{{ inspection.closedCount }}</text></view><view><text>已作废</text><text>{{ inspection.voidedCount }}</text></view></view>
          <button v-if="inspection.status === 'DRAFT' && canManage" class="edit-button" @tap="editDraft">继续编辑共享草稿</button>
        </view>

        <view class="info-card">
          <view><text>实际检查日期</text><text>{{ inspection.inspectionDate || '未填写' }}</text></view>
          <view><text>创建人</text><text>{{ inspection.createdByName || '-' }}</text></view>
          <view><text>最近编辑人</text><text>{{ inspection.lastEditedByName || '-' }}</text></view>
          <view v-if="inspection.status === 'SUBMITTED'"><text>提交人</text><text>{{ inspection.submittedByName || '-' }}</text></view>
          <view v-if="inspection.status === 'SUBMITTED'"><text>提交时间</text><text>{{ formatTime(inspection.submittedTime) }}</text></view>
          <view class="conclusion"><text>检查结论</text><text>{{ inspection.conclusion || '未填写' }}</text></view>
        </view>

        <view v-if="inspection.overviewPhotoFileIds.length" class="photo-card">
          <view class="section-title"><text>周检现场照片</text><text>{{ inspection.overviewPhotoFileIds.length }} 张</text></view>
          <view class="photo-grid"><image v-for="path in overviewPaths" :key="path" :src="path" mode="aspectFill" @tap="preview(overviewPaths, path)" /></view>
          <text v-if="overviewFailed" class="photo-error">{{ overviewFailed }} 张照片加载失败</text>
        </view>

        <template v-if="inspection.status === 'DRAFT'">
          <view class="section-banner"><text>草稿问题</text><text>正式提交后才会进入整改闭环</text></view>
          <view v-for="(item, index) in inspection.draftItems" :key="item.itemKey" class="issue-card draft-item">
            <view class="issue-head"><text>问题 {{ index + 1 }}</text><text>{{ severityLabel(item.severity || 'NORMAL') }}</text></view>
            <text class="issue-title">{{ item.title || '标题待补充' }}</text>
            <view class="issue-lines"><text>{{ item.location || '位置待补充' }}</text><text>{{ item.assigneeName || '负责人待选择' }} · {{ item.deadline || '期限待选择' }}</text><text>{{ item.beforePhotoFileIds.length }} 张整改前照片</text></view>
          </view>
          <view v-if="!inspection.draftItems.length" class="empty-card">当前草稿暂未录入问题</view>
        </template>

        <template v-else>
          <view class="section-banner"><text>问题与整改对比</text><text>每个问题独立整改、复查和关闭</text></view>
          <view v-for="(issue, index) in inspection.issues" :key="issue.id" class="issue-card">
            <view class="issue-head"><view><text>问题 {{ index + 1 }}</text><text class="severity">{{ severityLabel(issue.severity) }}</text></view><WorkspaceStatusPill :label="issueStatusLabel(issue)" :tone="issueStatusTone(issue)" /></view>
            <text class="issue-title">{{ issue.title }}</text>
            <view class="issue-lines"><text>{{ issue.location || '未设置位置' }}</text><text>{{ issue.assigneeName || '未指定负责人' }} · {{ issue.deadline || '未设置期限' }}</text></view>
            <view class="comparison-grid">
              <view class="comparison-column"><view class="comparison-title"><text>整改前</text><text>{{ (issue.originalProblemPhotoFileIds || issue.issuePhotoFileIds || []).length }} 张</text></view><view class="photo-grid"><image v-for="path in comparison(issue.id)?.beforePaths || []" :key="path" :src="path" mode="aspectFill" @tap="preview(comparison(issue.id)?.beforePaths || [], path)" /></view><text v-if="comparison(issue.id)?.beforeFailed" class="photo-error">{{ comparison(issue.id)?.beforeFailed }} 张加载失败</text></view>
              <view class="comparison-column after"><view class="comparison-title"><text>最新整改后</text><text>{{ issue.latestRectificationPhotoFileIds?.length || 0 }} 张</text></view><view v-if="comparison(issue.id)?.afterPaths.length" class="photo-grid"><image v-for="path in comparison(issue.id)?.afterPaths || []" :key="path" :src="path" mode="aspectFill" @tap="preview(comparison(issue.id)?.afterPaths || [], path)" /></view><text v-else class="pending-photo">{{ issue.status === 'PENDING' ? '等待整改上传' : '暂无整改照片' }}</text><text v-if="comparison(issue.id)?.afterFailed" class="photo-error">{{ comparison(issue.id)?.afterFailed }} 张加载失败</text></view>
            </view>
            <button class="issue-link" @tap="openIssue(issue)">查看整改详情与全部操作留痕</button>
          </view>
          <view v-if="!inspection.issues.length" class="empty-card">本周检查未发现质量问题</view>
        </template>
      </view>
    </scroll-view>
  </view>
</template>

<style scoped>
.weekly-detail-page { min-height: 100vh; background: #f4f6f7; color: #263449; }
.weekly-detail-page :deep(.app-nav) { background: rgba(255,255,255,.98); box-shadow: 0 1rpx 0 rgba(148,163,184,.18); }
.detail-scroll { height: calc(100vh - 92px); }
.detail-content { display: flex; flex-direction: column; gap: 18rpx; padding: 22rpx 24rpx calc(38rpx + env(safe-area-inset-bottom)); }
.state-card { margin: 26rpx; padding: 70rpx 24rpx; border-radius: 16rpx; background: #fff; color: #718092; font-size: 23rpx; text-align: center; }
.state-card text { display: block; }
.state-card button { display: inline-flex; min-height: 60rpx; align-items: center; margin-top: 20rpx; padding: 0 24rpx; border: 0; border-radius: 12rpx; background: #e8f1f6; color: #315f86; font-size: 21rpx; }
.state-card button::after { border: 0; }
.hero-card,.info-card,.photo-card,.issue-card,.empty-card { padding: 22rpx; border-radius: 16rpx; background: #fff; box-shadow: 0 8rpx 26rpx rgba(43,56,72,.055); }
.hero-card { background: linear-gradient(145deg,#315f86,#456f8f); color: #fff; }
.hero-head { display: flex; align-items: center; justify-content: space-between; }
.late-tag { padding: 5rpx 12rpx; border-radius: 999rpx; background: rgba(255,255,255,.18); color: #fff; font-size: 19rpx; font-weight: 750; }
.inspection-no { display: block; margin-top: 18rpx; font-size: 29rpx; font-weight: 800; }
.week-range { display: block; margin-top: 7rpx; color: rgba(255,255,255,.78); font-size: 21rpx; }
.hero-grid { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 8rpx; margin-top: 22rpx; padding-top: 18rpx; border-top: 1rpx solid rgba(255,255,255,.18); }
.hero-grid view { text-align: center; }
.hero-grid text { display: block; color: rgba(255,255,255,.72); font-size: 18rpx; }
.hero-grid text:last-child { margin-top: 4rpx; color: #fff; font-size: 28rpx; font-weight: 800; }
.edit-button { width: 100%; min-height: 66rpx; margin-top: 20rpx; border: 0; border-radius: 12rpx; background: rgba(255,255,255,.94); color: #315f86; font-size: 22rpx; font-weight: 800; }
.edit-button::after,.issue-link::after { border: 0; }
.info-card > view { display: flex; min-height: 62rpx; align-items: center; justify-content: space-between; gap: 22rpx; border-bottom: 1rpx solid #edf0f3; color: #7e8c9b; font-size: 21rpx; }
.info-card > view:last-child { border-bottom: 0; }
.info-card > view text:last-child { color: #304256; text-align: right; }
.info-card > view.conclusion { align-items: flex-start; flex-direction: column; gap: 8rpx; padding: 16rpx 0 4rpx; }
.info-card > view.conclusion text:last-child { line-height: 1.55; text-align: left; }
.section-title,.section-banner,.issue-head,.comparison-title { display: flex; align-items: center; justify-content: space-between; gap: 14rpx; }
.section-title text:first-child,.section-banner text:first-child { color: #2e4256; font-size: 25rpx; font-weight: 800; }
.section-title text:last-child,.section-banner text:last-child { color: #8794a2; font-size: 19rpx; }
.section-banner { padding: 8rpx 4rpx 0; }
.photo-grid { display: grid; grid-template-columns: repeat(4,minmax(0,1fr)); gap: 9rpx; margin-top: 12rpx; }
.photo-grid image { width: 100%; height: 116rpx; border-radius: 9rpx; background: #edf1f4; }
.photo-error { display: block; margin-top: 9rpx; color: #b55252; font-size: 19rpx; }
.issue-head > text:first-child,.issue-head > view > text:first-child { color: #315f86; font-size: 21rpx; font-weight: 800; }
.issue-head > text:last-child,.severity { margin-left: 9rpx; color: #7f8d9b; font-size: 18rpx; }
.issue-title { display: block; margin: 14rpx 0 9rpx; color: #293c50; font-size: 26rpx; font-weight: 800; line-height: 1.4; }
.issue-lines text { display: block; color: #7e8c9b; font-size: 20rpx; line-height: 1.5; }
.comparison-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 12rpx; margin-top: 18rpx; }
.comparison-column { min-width: 0; padding: 14rpx; border-radius: 12rpx; background: #f7f9fa; }
.comparison-column.after { background: #f0f7f4; }
.comparison-title text { color: #617286; font-size: 19rpx; }
.comparison-title text:first-child { color: #334a5f; font-weight: 750; }
.comparison-column .photo-grid { grid-template-columns: repeat(2,minmax(0,1fr)); }
.comparison-column .photo-grid image { height: 104rpx; }
.pending-photo { display: block; padding: 36rpx 0; color: #8a989f; font-size: 19rpx; text-align: center; }
.issue-link { width: 100%; min-height: 62rpx; margin-top: 16rpx; border: 0; border-radius: 11rpx; background: #eaf2f6; color: #3e6881; font-size: 20rpx; font-weight: 750; }
.empty-card { color: #82909e; font-size: 21rpx; text-align: center; }
@media (max-width: 360px) { .comparison-grid { grid-template-columns: 1fr; } .photo-grid { grid-template-columns: repeat(3,minmax(0,1fr)); } }
</style>
