<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import IconGrid from '@/components/IconGrid.vue';
import { getProjectDetail } from '@/api/project';
import { workbenchActions, type WorkbenchAction } from '@/constants/workbenchActions';
import type { Project } from '@/types';
import { getQueryNumber, navigateTo } from '@/utils/navigation';

const projectId = ref(1);
const project = ref<Project>();

const actions = workbenchActions;

const projectStats = computed(() => [
  { label: '启用电箱', value: project.value?.electricBoxTotal || 0, tone: 'green' },
  { label: '今日巡检', value: project.value?.todayInspectionCount || 0, tone: 'blue' },
  { label: '待复核', value: project.value?.pendingReviewCount || 0, tone: 'amber' },
  { label: '待整改', value: project.value?.pendingRectificationCount || 0, tone: 'red' }
]);

onShow(async () => {
  const pages = getCurrentPages();
  const current = pages[pages.length - 1] as unknown as { options?: Record<string, string> };
  projectId.value = getQueryNumber(current.options?.projectId, projectId.value || 1);
  project.value = await getProjectDetail(projectId.value);
});

function statusLabel(status?: string) {
  if (status === 'warning') return '预警';
  if (status === 'danger') return '异常';
  return '正常';
}

function statusClass(status?: string) {
  if (status === 'warning') return 'warning';
  if (status === 'danger') return 'danger';
  return 'normal';
}

function display(value?: string, fallback = '未设置') {
  return value && value.trim() ? value : fallback;
}

function appendQuery(url: string, params: Record<string, string | number | undefined>) {
  const query = Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
    .join('&');
  if (!query) return url;
  return `${url}${url.includes('?') ? '&' : '?'}${query}`;
}

function projectTitle() {
  return project.value?.projectName || project.value?.shortName || '项目工作台';
}

function projectSubline() {
  const values = [];
  if (project.value?.area) values.push(`${project.value.area} ㎡`);
  if (project.value?.period) values.push(project.value.period);
  return values.join(' · ') || '系统项目数据';
}

function openAction(action: WorkbenchAction) {
  if (action.targetType === 'switchTab') {
    if (action.todoFilter) {
      uni.setStorageSync('site_platform_todo_filter', action.todoFilter);
    }
    uni.switchTab({ url: action.url });
    return;
  }
  const url = action.appendProjectId ? appendQuery(action.url, { projectId: projectId.value }) : action.url;
  navigateTo(url);
}
</script>

<template>
  <view class="page workbench-page">
    <view class="overview-card">
      <view class="card-kicker"><text>当前施工区域</text><text>数据实时同步</text></view>
      <view class="overview-head">
        <view class="project-title">
          <text class="project-name">{{ projectTitle() }}</text>
          <text class="project-full">{{ projectSubline() }}</text>
          <text class="project-arrow"></text>
        </view>
        <text class="status-pill" :class="statusClass(project?.status)">{{ statusLabel(project?.status) }}</text>
      </view>

      <view class="info-panel">
        <view class="info-row">
          <text class="info-label">阶段</text>
          <text class="info-value">{{ display(project?.stage) }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">项目经理</text>
          <text class="info-value">{{ display(project?.manager, '未指定') }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">施工单位</text>
          <text class="info-value">{{ display(project?.contractor) }}</text>
        </view>
        <view class="info-row address">
          <text class="info-label">地址</text>
          <text class="info-value">{{ display(project?.address) }}</text>
        </view>
      </view>

      <view class="overview-stats">
        <view v-for="stat in projectStats" :key="stat.label" class="overview-stat">
          <text class="stat-value" :class="stat.tone">{{ stat.value }}</text>
          <text class="stat-label">{{ stat.label }}</text>
        </view>
      </view>
    </view>

    <view class="action-section">
      <view class="action-head"><view><text>现场业务</text><text>选择需要处理的工作</text></view><text>共 {{ actions.length }} 项</text></view>
      <IconGrid :actions="actions" @select="openAction" />
    </view>
  </view>
</template>

<style scoped>
.workbench-page {
  display: flex;
  flex-direction: column;
  gap: 28rpx;
  min-height: 100vh;
  padding: 26rpx 24rpx calc(40rpx + env(safe-area-inset-bottom));
}

.overview-card {
  min-height: 500rpx;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 26rpx;
  padding: 32rpx 30rpx;
  border: 1rpx solid #cde3fb;
  border-radius: 28rpx;
  background:
    radial-gradient(circle at 100% 0%, rgba(22, 119, 255, 0.12), transparent 36%),
    linear-gradient(145deg, #ffffff, #f8fbff);
  box-shadow: 0 16rpx 36rpx rgba(51, 112, 180, 0.12);
}

.overview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 22rpx;
}

.project-title {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 8rpx;
}

.project-name,
.project-full {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-name {
  color: #172033;
  font-size: 34rpx;
  font-weight: 900;
  line-height: 1.2;
}

.project-full {
  color: #6b7f99;
  font-size: 24rpx;
}

.project-arrow {
  position: absolute;
  opacity: 0;
  width: 18rpx;
  height: 18rpx;
  border-right: 4rpx solid #5f83aa;
  border-bottom: 4rpx solid #5f83aa;
  transform: rotate(45deg);
}

.status-pill {
  display: inline-flex;
  min-height: 48rpx;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  padding: 0 20rpx;
  border-radius: 14rpx;
  font-size: 25rpx;
  font-weight: 900;
  line-height: 1;
}

.status-pill.normal {
  background: #e6fbf6;
  color: #0f9f8f;
}

.status-pill.warning {
  background: #fff4d8;
  color: #b7791f;
}

.status-pill.danger {
  background: #ffe8e8;
  color: #d14343;
}

.info-panel {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  padding: 22rpx;
  border: 1rpx solid #d9eafb;
  border-radius: 22rpx;
  background: #f5faff;
}

.info-row {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: 18rpx;
}

.info-label {
  width: 126rpx;
  flex-shrink: 0;
  color: #7a91aa;
  font-size: 23rpx;
  line-height: 1.35;
}

.info-value {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: #26364d;
  font-size: 24rpx;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.info-row.address .info-value {
  display: -webkit-box;
  white-space: normal;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.overview-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12rpx;
}

.overview-stat {
  position: relative;
  min-height: 112rpx;
  min-width: 0;
  padding: 18rpx 6rpx;
  border-radius: 20rpx;
  background: #edf6ff;
}

.stat-value,
.stat-label {
  display: block;
  text-align: center;
}

.stat-value {
  font-size: 44rpx;
  font-weight: 900;
  line-height: 1;
  transition: color 0.22s ease, transform 0.22s ease;
}

.stat-label {
  margin-top: 16rpx;
  color: #7a91aa;
  font-size: 21rpx;
  line-height: 1;
}

.green {
  color: #0f9f8f;
}

.blue {
  color: #1677ff;
}

.amber {
  color: #f59e0b;
}

.red {
  color: #ef4444;
}

/* 项目工作台 V2：缩短项目摘要并突出现场业务入口。 */
.workbench-page {
  gap: 16rpx;
  padding: 18rpx 24rpx calc(38rpx + env(safe-area-inset-bottom));
  background: #f5f4f0;
}

.overview-card {
  min-height: 0;
  gap: 16rpx;
  padding: 21rpx;
  border-color: rgba(145, 103, 57, 0.12);
  border-radius: 21rpx;
  background: linear-gradient(135deg, #fff9f1 0%, #ffffff 42%);
  box-shadow: 0 9rpx 26rpx rgba(68, 53, 34, 0.06);
}

.card-kicker {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #a06b39;
  font-size: 17rpx;
}

.card-kicker text:last-child { color: #9a9fa6; }
.overview-head { gap: 15rpx; }
.project-title { gap: 4rpx; }
.project-name { color: #283548; font-size: 29rpx; }
.project-full { color: #858f9c; font-size: 19rpx; }

.status-pill {
  min-height: 38rpx;
  padding: 0 13rpx;
  border-radius: 999rpx;
  font-size: 17rpx;
}

.status-pill.normal { background: #e6f4ec; color: #247d5b; }
.status-pill.warning { background: #fff0df; color: #aa651f; }
.status-pill.danger { background: #fdecea; color: #b94d46; }

.info-panel {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0;
  padding: 0;
  overflow: hidden;
  border-color: #eee9e2;
  border-radius: 14rpx;
  background: #faf9f7;
}

.info-row {
  display: block;
  min-width: 0;
  padding: 12rpx 14rpx;
}

.info-row:nth-child(even) { border-left: 1rpx solid #eee9e2; }
.info-row:nth-child(n+3) { border-top: 1rpx solid #eee9e2; }
.info-label, .info-value { display: block; width: auto; }
.info-label { color: #999fa7; font-size: 16rpx; }
.info-value { margin-top: 4rpx; color: #4a5667; font-size: 19rpx; font-weight: 650; }
.info-row.address .info-value { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.overview-stats { gap: 7rpx; }

.overview-stat {
  min-height: 76rpx;
  padding: 12rpx 4rpx;
  border-radius: 13rpx;
  background: #f4f3f0;
}

.stat-value { font-size: 29rpx; }
.stat-label { margin-top: 7rpx; color: #8c949d; font-size: 16rpx; }
.green { color: #27815e; }
.blue { color: #52758d; }
.amber { color: #b47728; }
.red { color: #bd4e46; }

.action-section {
  padding: 20rpx;
  border: 1rpx solid rgba(145, 103, 57, 0.1);
  border-radius: 21rpx;
  background: #ffffff;
  box-shadow: 0 9rpx 26rpx rgba(68, 53, 34, 0.05);
}

.action-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16rpx;
  margin-bottom: 16rpx;
}

.action-head view text { display: block; }
.action-head view text:first-child { color: #2d394b; font-size: 25rpx; font-weight: 850; }
.action-head view text:last-child { margin-top: 4rpx; color: #969da6; font-size: 17rpx; }
.action-head > text { color: #a07851; font-size: 17rpx; }
</style>
