<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad, onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import WorkspaceSegmentControl from '@/components/workspace/WorkspaceSegmentControl.vue';
import WorkspaceStatusPill from '@/components/workspace/WorkspaceStatusPill.vue';
import { WORKSPACE_THEME } from '@/constants/workspaceTheme';
import { getQualityWeeklyInspectionPage } from '@/api/quality';
import { useAuthStore } from '@/stores/auth';
import { useProjectStore } from '@/stores/project';
import type { QualityWeeklyInspection, QualityWeeklyInspectionStatus } from '@/types';
import { usePageScrollHeight } from '@/utils/navLayout';
import { getQueryNumber, showToast, switchTab } from '@/utils/navigation';

type WeeklyFilter = 'ALL' | QualityWeeklyInspectionStatus;

const authStore = useAuthStore();
const projectStore = useProjectStore();
const projectId = ref(0);
const activeFilter = ref<WeeklyFilter>('ALL');
const keyword = ref('');
const submittedKeyword = ref('');
const records = ref<QualityWeeklyInspection[]>([]);
const pageNo = ref(1);
const total = ref(0);
const loading = ref(false);
const loadingMore = ref(false);
const errorMessage = ref('');
const ready = ref(false);
const { scrollStyle } = usePageScrollHeight({ bottomRpx: 0, minHeight: 320 });
let requestSequence = 0;

const currentProject = computed(() => projectStore.state.projects.find((item) => item.id === projectId.value));
const canManage = computed(() => projectId.value > 0
  && authStore.hasProjectPermission(projectId.value, 'quality.manage'));
const filters = computed(() => [
  { value: 'ALL', label: '全部' },
  ...(canManage.value ? [{ value: 'DRAFT', label: '共享草稿' }] : []),
  { value: 'SUBMITTED', label: '已提交' }
]);
const hasMore = computed(() => records.value.length < total.value);

onLoad(async (query) => {
  if (!await authStore.ensureRootAccess('/pages/quality/index')) return;
  await projectStore.loadProjects();
  const resolved = getQueryNumber(query?.projectId, projectStore.state.currentProjectId || 0);
  if (!resolved || !await authStore.ensureProjectPermission('/pages/quality/index', resolved, 'quality.view')) return;
  projectId.value = resolved;
  ready.value = true;
  await refresh();
});

onShow(async () => {
  if (ready.value) await refresh();
});

async function refresh() {
  if (!projectId.value || loading.value) return;
  const sequence = ++requestSequence;
  loading.value = true;
  errorMessage.value = '';
  try {
    const result = await getQualityWeeklyInspectionPage(
      projectId.value,
      1,
      20,
      activeFilter.value === 'ALL' ? undefined : activeFilter.value,
      submittedKeyword.value
    );
    if (sequence !== requestSequence) return;
    records.value = result.records;
    pageNo.value = result.pageNo;
    total.value = result.total;
  } catch (error) {
    if (sequence === requestSequence) errorMessage.value = error instanceof Error ? error.message : '周检记录加载失败';
  } finally {
    if (sequence === requestSequence) loading.value = false;
  }
}

async function setFilter(value: string) {
  activeFilter.value = value as WeeklyFilter;
  records.value = [];
  await refresh();
}

async function applySearch() {
  submittedKeyword.value = keyword.value.trim();
  records.value = [];
  await refresh();
}

async function loadMore() {
  if (loading.value || loadingMore.value || !hasMore.value) return;
  const sequence = requestSequence;
  const nextPage = pageNo.value + 1;
  loadingMore.value = true;
  try {
    const result = await getQualityWeeklyInspectionPage(
      projectId.value,
      nextPage,
      20,
      activeFilter.value === 'ALL' ? undefined : activeFilter.value,
      submittedKeyword.value
    );
    if (sequence !== requestSequence) return;
    const knownIds = new Set(records.value.map((item) => item.id));
    records.value = [...records.value, ...result.records.filter((item) => !knownIds.has(item.id))];
    pageNo.value = result.pageNo;
    total.value = result.total;
  } catch (error) {
    showToast(error instanceof Error ? error.message : '更多周检记录加载失败');
  } finally {
    loadingMore.value = false;
  }
}

function openRecord(item: QualityWeeklyInspection) {
  const path = item.status === 'DRAFT' && canManage.value ? 'weekly-edit' : 'weekly-detail';
  const query = path === 'weekly-edit' ? `projectId=${item.projectId}&weekStart=${item.weekStart}` : `id=${item.id}`;
  uni.navigateTo({ url: `/pages/quality/${path}?${query}` });
}

function statusLabel(item: QualityWeeklyInspection) {
  return item.status === 'DRAFT' ? '共享草稿' : item.lateSubmission ? '已提交 · 补录' : '已提交';
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '时间未设置';
}

function goBack() {
  if (getCurrentPages().length > 1) uni.navigateBack();
  else switchTab('/pages/quality/index');
}
</script>

<template>
  <view class="workspace-shell child-page" :style="{ '--page-accent': WORKSPACE_THEME.accent, '--page-accent-deep': WORKSPACE_THEME.accentDeep, '--page-tint': WORKSPACE_THEME.tint, '--page-background': WORKSPACE_THEME.page }">
    <AppNavBar title="周检记录" @back="goBack" />
    <scroll-view class="workspace-scroll" scroll-y enable-flex lower-threshold="80" :style="scrollStyle" @scrolltolower="loadMore">
      <view class="child-content">
        <view class="context-card"><text>{{ currentProject?.projectName || currentProject?.shortName || '当前施工区域' }}</text><text>周检共享草稿与已提交记录</text></view>
        <WorkspaceSegmentControl :model-value="activeFilter" :options="filters" :accent="WORKSPACE_THEME.accent" :tint="WORKSPACE_THEME.tint" @update:model-value="setFilter" />
        <view class="search-box"><text class="search-icon"></text><input v-model="keyword" class="search-input" confirm-type="search" placeholder="搜索周检编号、结论或编辑人" placeholder-class="search-placeholder" @confirm="applySearch" /><button @tap="applySearch">搜索</button></view>
        <view v-if="loading && !records.length" class="state-panel"><text class="state-title">正在加载周检记录</text></view>
        <view v-else-if="errorMessage" class="state-panel"><text class="state-title">周检记录加载失败</text><text class="state-desc">{{ errorMessage }}</text><button class="retry-button" @tap="refresh">重新加载</button></view>
        <view v-else class="record-card">
          <button v-for="item in records" :key="item.id" class="record-row" @tap="openRecord(item)">
            <view><text>{{ item.weekStart }} 至 {{ item.weekEnd }}</text><text>{{ item.status === 'DRAFT' ? `共享整理中 · ${item.lastEditedByName || item.createdByName || '项目成员'}` : `${item.submittedIssueCount} 个问题 · ${item.submittedByName || '已提交'}` }}</text><text>{{ item.status === 'DRAFT' ? `最近保存 ${formatTime(item.updateTime)}` : `提交时间 ${formatTime(item.submittedTime)}` }}</text></view>
            <WorkspaceStatusPill :label="statusLabel(item)" :tone="item.status === 'DRAFT' ? 'amber' : 'green'" /><text class="row-arrow"></text>
          </button>
          <view v-if="!records.length" class="empty-line">{{ submittedKeyword ? '没有匹配的周检记录' : '当前施工区域暂无周检记录' }}</view>
          <view v-else class="page-tail">{{ loadingMore ? '正在加载更多' : hasMore ? '继续上拉加载更多' : `共 ${total} 条周检记录` }}</view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<style scoped src="../../styles/workspace-page.css"></style>
<style scoped>
.child-content { display: flex; min-height: 100%; flex-direction: column; gap: 16rpx; padding: 20rpx 24rpx calc(34rpx + env(safe-area-inset-bottom)); }
.context-card { padding: 17rpx 20rpx; border-radius: 14rpx; background: var(--page-tint); }
.context-card text { display: block; color: var(--workspace-text-secondary); font-size: 19rpx; }
.context-card text:first-child { overflow: hidden; color: var(--workspace-text); font-size: 23rpx; font-weight: 760; text-overflow: ellipsis; white-space: nowrap; }
.context-card text + text { margin-top: 5rpx; }
.search-box { margin: 0; background: #fff; }
.search-box button { min-height: 54rpx; margin: 0; padding: 0 6rpx; border: 0; background: transparent; color: var(--page-accent-deep); font-size: 20rpx; font-weight: 750; }
.search-box button::after,.record-row::after { border: 0; }
.record-card { overflow: hidden; border-radius: 16rpx; background: #fff; box-shadow: var(--workspace-shadow); }
.record-row { box-sizing: border-box; display: flex; width: 100%; min-height: 120rpx; align-items: center; gap: 13rpx; padding: 18rpx 20rpx; border-bottom: 1rpx solid var(--workspace-divider); background: #fff; text-align: left; }
.record-row > view { min-width: 0; flex: 1; }
.record-row > view text { display: block; color: var(--workspace-text-muted); font-size: 18rpx; line-height: 1.4; }
.record-row > view text:first-child { color: var(--workspace-text); font-size: 24rpx; font-weight: 760; }
.record-row > view text + text { margin-top: 5rpx; }
.record-row :deep(.status-pill) { flex-shrink: 0; }
.empty-line,.page-tail { padding: 42rpx 20rpx; color: var(--workspace-text-muted); font-size: 20rpx; text-align: center; }
.page-tail { padding: 22rpx 20rpx; }
@media (max-width: 360px) { .child-content { padding-right: 18rpx; padding-left: 18rpx; } .record-row { padding-right: 16rpx; padding-left: 16rpx; } }
</style>
