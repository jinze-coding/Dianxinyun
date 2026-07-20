<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import { useProjectStore } from '@/stores/project';

const projectStore = useProjectStore();
const keyword = ref('');
const allProjects = computed(() => projectStore.state.projects || []);
const projectCount = computed(() => allProjects.value.length);
const currentProjectId = computed(() => projectStore.state.currentProjectId);

const projects = computed(() => {
  const text = keyword.value.trim();
  const source = allProjects.value;
  if (!text) return source;
  return source.filter((item) => [
    item.projectName,
    item.shortName,
    item.manager,
    item.contractor,
    item.address,
    item.stage,
    item.phase,
    item.projectStatus,
    item.area,
    item.period
  ].join('').includes(text));
});

onShow(async () => {
  await projectStore.loadProjects();
});

function openProject(projectId: number) {
  projectStore.setCurrentProject(projectId);
  uni.switchTab({ url: '/pages/documents/index' });
}

function updateKeyword(event: unknown) {
  const inputEvent = event as { detail?: { value?: string }; target?: { value?: string } };
  keyword.value = inputEvent.detail?.value || inputEvent.target?.value || '';
}

function statusLabel(status: string) {
  if (status === 'normal') return '正常';
  if (status === 'warning') return '预警';
  return '异常';
}

function statusClass(status: string) {
  if (status === 'normal') return 'status-normal';
  if (status === 'warning') return 'status-warning';
  return 'status-danger';
}

function display(value?: string, fallback = '未设置') {
  return value && value.trim() ? value : fallback;
}

function projectTitle(project: { projectName?: string; shortName?: string }) {
  return project.projectName || project.shortName || '未命名项目';
}

function projectSubline(project: { area?: string; period?: string }) {
  const values = [];
  if (project.area) values.push(`${project.area} ㎡`);
  if (project.period) values.push(project.period);
  return values.join(' · ') || '系统项目';
}
</script>

<template>
  <view class="page project-page">
    <view class="page-head">
      <view>
        <text class="head-title">施工区域</text>
        <text class="head-subtitle">选择后将切换巡检台账、任务和记录</text>
      </view>
      <view class="sync-pill"><text class="sync-dot"></text><text>{{ projectCount }} 个授权</text></view>
    </view>

    <view class="project-search">
      <view class="search-icon"></view>
      <input
        class="search-input"
        :value="keyword"
        placeholder="搜索项目名称、负责人、施工单位、地址"
        placeholder-class="search-placeholder"
        @input="updateKeyword"
      />
    </view>

    <view v-if="projectStore.state.loading" class="state-card">
      <text class="state-title">正在同步项目</text>
      <text class="state-desc">正在从 PC 系统读取项目数据...</text>
    </view>

    <view v-else-if="projectStore.state.errorMessage" class="state-card error">
      <text class="state-title">项目加载失败</text>
      <text class="state-desc">{{ projectStore.state.errorMessage }}</text>
      <text class="state-tip">请重新登录，或确认后端 8080 已启动</text>
    </view>

    <view v-else-if="projects.length" class="project-list">
      <view
        v-for="(project, index) in projects"
        :key="project.id"
        class="project-card stagger-in"
        :class="{ current: project.id === currentProjectId }"
        :style="{ '--delay': `${index * 55}ms` }"
        hover-class="project-card-hover"
        @tap="openProject(project.id)"
      >
        <view class="project-top">
          <view class="title-block">
            <view class="project-title-line"><text class="project-name">{{ projectTitle(project) }}</text><text v-if="project.id === currentProjectId" class="current-pill">当前</text></view>
            <text class="project-full-name">{{ projectSubline(project) }}</text>
          </view>
          <text class="project-status" :class="statusClass(project.status)">{{ statusLabel(project.status) }}</text>
        </view>

        <view class="detail-lines">
          <view class="detail-line">
            <text class="detail-label">阶段</text>
            <text class="detail-value">{{ display(project.stage) }}</text>
          </view>
          <view class="detail-line">
            <text class="detail-label">项目经理</text>
            <text class="detail-value">{{ display(project.manager, '未指定') }}</text>
          </view>
          <view class="detail-line">
            <text class="detail-label">施工单位</text>
            <text class="detail-value">{{ display(project.contractor) }}</text>
          </view>
          <view class="detail-line address-line">
            <text class="detail-label">地址</text>
            <text class="detail-value">{{ display(project.address) }}</text>
          </view>
        </view>

        <view class="meta-grid">
          <view class="meta-cell">
            <text class="meta-value accent-blue">{{ project.electricBoxTotal }}</text>
            <text class="meta-label">电箱</text>
          </view>
          <view class="meta-cell">
            <text class="meta-value accent-green">{{ project.todayInspectionCount || 0 }}</text>
            <text class="meta-label">今日巡检</text>
          </view>
          <view class="meta-cell">
            <text class="meta-value accent-amber">{{ project.pendingTodoCount }}</text>
            <text class="meta-label">待办</text>
          </view>
        </view>
        <view class="card-entry"><text>{{ project.id === currentProjectId ? '进入当前区域巡检' : '切换并进入巡检' }}</text><text class="entry-arrow"></text></view>
      </view>
    </view>
    <view v-else class="state-card">
      <text class="state-title">暂无项目</text>
      <text class="state-desc">当前账号没有可访问项目</text>
    </view>
  </view>
</template>

<style scoped>
.project-page {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 24rpx 26rpx calc(148rpx + env(safe-area-inset-bottom));
}

.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24rpx;
  padding: 8rpx 2rpx 2rpx;
}

.head-title,
.head-subtitle {
  display: block;
}

.head-title {
  color: #172033;
  font-size: 40rpx;
  font-weight: 900;
  line-height: 1.2;
}

.head-subtitle {
  margin-top: 10rpx;
  color: #49627f;
  font-size: 23rpx;
}

.sync-pill {
  min-width: 84rpx;
  height: 48rpx;
  border: 1rpx solid rgba(15, 159, 143, 0.22);
  border-radius: 999rpx;
  background: #e6fbf6;
  color: #0f9f8f;
  font-size: 23rpx;
  font-weight: 800;
  line-height: 48rpx;
  text-align: center;
}

.project-search {
  display: flex;
  height: 84rpx;
  align-items: center;
  padding: 0 28rpx;
  border: 1rpx solid #cde3fb;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 10rpx 26rpx rgba(51, 112, 180, 0.08);
  transition: border-color 0.22s ease, box-shadow 0.22s ease, background 0.22s ease;
}

.project-search:focus-within {
  border-color: #1677ff;
  background: #ffffff;
  box-shadow: 0 0 0 6rpx rgba(22, 119, 255, 0.1);
}

.search-icon {
  position: relative;
  width: 34rpx;
  height: 34rpx;
  flex: 0 0 34rpx;
  margin-right: 28rpx;
  border: 4rpx solid #5f83aa;
  border-radius: 50%;
}

.search-icon::after {
  position: absolute;
  right: -12rpx;
  bottom: -6rpx;
  width: 16rpx;
  height: 4rpx;
  border-radius: 999rpx;
  background: #5f83aa;
  content: "";
  transform: rotate(45deg);
}

.search-input {
  flex: 1;
  min-width: 0;
  height: 84rpx;
  color: #172033;
  font-size: 27rpx;
  line-height: 84rpx;
}

.search-placeholder {
  color: #8aa0b8;
  font-size: 27rpx;
}

.project-list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.state-card {
  display: flex;
  min-height: 220rpx;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 14rpx;
  padding: 30rpx;
  border: 1rpx dashed #bdd8f5;
  border-radius: 26rpx;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 12rpx 28rpx rgba(51, 112, 180, 0.08);
}

.state-card.error {
  border-color: #ffb4b4;
  background: #fff8f8;
}

.state-title {
  color: #172033;
  font-size: 29rpx;
  font-weight: 800;
  line-height: 1.35;
}

.state-desc,
.state-tip {
  color: #6b7f99;
  font-size: 24rpx;
  line-height: 1.45;
  text-align: center;
}

.state-tip {
  color: #d14343;
}

.project-card {
  display: flex;
  min-height: 356rpx;
  flex-direction: column;
  justify-content: flex-start;
  padding: 30rpx;
  border: 1rpx solid #cde3fb;
  border-radius: 26rpx;
  background:
    radial-gradient(circle at 100% 0%, rgba(22, 119, 255, 0.1), transparent 34%),
    linear-gradient(145deg, #ffffff, #f8fbff);
  box-shadow: 0 16rpx 36rpx rgba(51, 112, 180, 0.12);
  transition: transform 0.22s ease, border-color 0.22s ease, box-shadow 0.22s ease;
}

.project-card-hover {
  border-color: rgba(22, 119, 255, 0.66);
  transform: translateY(4rpx) scale(0.99);
  box-shadow: 0 10rpx 28rpx rgba(51, 112, 180, 0.14);
}

.project-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24rpx;
  margin-bottom: 22rpx;
}

.title-block {
  flex: 1;
  min-width: 0;
}

.project-name,
.project-full-name {
  display: block;
  overflow: hidden;
  flex: 1;
  min-width: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-name {
  color: #172033;
  font-size: 32rpx;
  font-weight: 900;
  line-height: 1.24;
}

.project-full-name {
  margin-top: 9rpx;
  color: #6b7f99;
  font-size: 23rpx;
}

.project-status {
  display: inline-flex;
  min-width: 84rpx;
  height: 46rpx;
  align-items: center;
  justify-content: center;
  padding: 0 18rpx;
  border-radius: 14rpx;
  font-size: 24rpx;
  font-weight: 900;
  line-height: 46rpx;
  white-space: nowrap;
}

.status-normal {
  background: #e6fbf6;
  color: #0f9f8f;
}

.status-warning {
  background: #fff4d8;
  color: #b7791f;
}

.status-danger {
  background: #ffe8e8;
  color: #d14343;
}

.detail-lines {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-bottom: 24rpx;
  padding: 20rpx;
  border: 1rpx solid #d9eafb;
  border-radius: 20rpx;
  background: #f5faff;
}

.detail-line {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: 18rpx;
}

.detail-label {
  width: 112rpx;
  flex-shrink: 0;
  color: #7a91aa;
  font-size: 22rpx;
  line-height: 1.35;
}

.detail-value {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: #26364d;
  font-size: 23rpx;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.address-line .detail-value {
  display: -webkit-box;
  overflow: hidden;
  white-space: normal;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  align-items: stretch;
  gap: 10rpx;
}

.meta-cell {
  position: relative;
  display: flex;
  min-width: 0;
  min-height: 86rpx;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  border-radius: 18rpx;
  background: #edf6ff;
}

.meta-label {
  display: block;
  margin-top: 8rpx;
  color: #7a91aa;
  font-size: 20rpx;
  line-height: 1.2;
  white-space: nowrap;
}

.meta-value {
  display: block;
  color: #172033;
  font-size: 28rpx;
  font-weight: 900;
  line-height: 1.18;
}

.accent-blue {
  color: #60a5fa;
}

.accent-green {
  color: #34d399;
}

.accent-amber {
  color: #fbbf24;
}

.accent-orange {
  color: #fb923c;
}

.accent-red {
  color: #f87171;
}

/* 施工区域列表 V2：压缩信息密度并明确当前区域与进入动作。 */
.project-page {
  gap: 16rpx;
  padding: 18rpx 24rpx calc(38rpx + env(safe-area-inset-bottom));
  background: #f5f4f0;
}

.page-head {
  padding: 3rpx 2rpx 1rpx;
}

.head-title {
  color: #263449;
  font-size: 31rpx;
  font-weight: 850;
}

.head-subtitle {
  max-width: 520rpx;
  margin-top: 5rpx;
  color: #8a929d;
  font-size: 19rpx;
  line-height: 1.45;
}

.sync-pill {
  display: flex;
  min-width: 0;
  height: 44rpx;
  align-items: center;
  gap: 8rpx;
  padding: 0 13rpx;
  border: 0;
  border-radius: 999rpx;
  background: #e7f4ec;
  color: #277c5c;
  font-size: 18rpx;
  line-height: 1;
}

.sync-dot {
  width: 9rpx;
  height: 9rpx;
  border-radius: 50%;
  background: #32a173;
}

.project-search {
  height: 68rpx;
  padding: 0 19rpx;
  border-color: rgba(145, 103, 57, 0.12);
  border-radius: 16rpx;
  background: #ffffff;
  box-shadow: 0 8rpx 22rpx rgba(68, 53, 34, 0.045);
}

.project-search:focus-within {
  border-color: #c89b6e;
  box-shadow: 0 0 0 5rpx rgba(169, 101, 39, 0.08);
}

.search-icon {
  width: 24rpx;
  height: 24rpx;
  flex-basis: 24rpx;
  margin-right: 17rpx;
  border-width: 3rpx;
  border-color: #95785d;
}

.search-icon::after {
  right: -9rpx;
  bottom: -5rpx;
  width: 12rpx;
  height: 3rpx;
  background: #95785d;
}

.search-input {
  height: 66rpx;
  color: #344154;
  font-size: 22rpx;
  line-height: 66rpx;
}

.search-placeholder { color: #a1a7af; font-size: 22rpx; }
.project-list { gap: 14rpx; }

.project-card {
  position: relative;
  min-height: 0;
  overflow: hidden;
  padding: 21rpx;
  border-color: rgba(145, 103, 57, 0.1);
  border-radius: 21rpx;
  background: #ffffff;
  box-shadow: 0 9rpx 26rpx rgba(68, 53, 34, 0.055);
}

.project-card.current {
  border-color: rgba(169, 101, 39, 0.25);
  box-shadow: inset 5rpx 0 0 #a96527, 0 9rpx 26rpx rgba(68, 53, 34, 0.065);
}

.project-card-hover {
  border-color: rgba(169, 101, 39, 0.28);
  transform: scale(0.988);
  box-shadow: 0 5rpx 17rpx rgba(68, 53, 34, 0.08);
}

.project-top { gap: 15rpx; margin-bottom: 15rpx; }
.project-title-line { display: flex; min-width: 0; align-items: center; gap: 9rpx; }

.project-name {
  color: #283548;
  font-size: 27rpx;
  font-weight: 900;
}

.current-pill {
  flex-shrink: 0;
  padding: 5rpx 9rpx;
  border-radius: 999rpx;
  background: #fff0df;
  color: #a76224;
  font-size: 15rpx;
  font-weight: 800;
  line-height: 1;
}

.project-full-name { margin-top: 5rpx; color: #8b949f; font-size: 19rpx; }

.project-status {
  min-width: 68rpx;
  height: 38rpx;
  padding: 0 12rpx;
  border-radius: 999rpx;
  font-size: 17rpx;
  line-height: 1;
}

.status-normal { background: #e6f4ec; color: #247d5b; }
.status-warning { background: #fff0df; color: #aa651f; }
.status-danger { background: #fdecea; color: #b94d46; }

.detail-lines {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0;
  margin-bottom: 15rpx;
  padding: 0;
  overflow: hidden;
  border-color: #eee9e2;
  border-radius: 14rpx;
  background: #faf9f7;
}

.detail-line {
  display: block;
  min-width: 0;
  padding: 12rpx 14rpx;
}

.detail-line:nth-child(even) { border-left: 1rpx solid #eee9e2; }
.detail-line:nth-child(n+3) { border-top: 1rpx solid #eee9e2; }
.detail-label, .detail-value { display: block; width: auto; }
.detail-label { color: #9a9fa6; font-size: 16rpx; }
.detail-value { margin-top: 4rpx; color: #4a5667; font-size: 19rpx; font-weight: 650; }
.address-line .detail-value { -webkit-line-clamp: 1; }

.meta-grid { gap: 6rpx; }

.meta-cell {
  min-height: 69rpx;
  padding: 10rpx 3rpx;
  border-radius: 12rpx;
  background: #f5f4f1;
}

.meta-value { font-size: 25rpx; }
.meta-label { margin-top: 5rpx; color: #8e959e; font-size: 16rpx; }
.accent-blue { color: #52758d; }
.accent-green { color: #27815e; }
.accent-amber { color: #b47728; }
.accent-orange { color: #a96527; }
.accent-red { color: #bd4e46; }

.card-entry {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 9rpx;
  margin-top: 13rpx;
  color: #9a652f;
  font-size: 17rpx;
  font-weight: 750;
}

.entry-arrow {
  width: 8rpx;
  height: 8rpx;
  border-top: 2rpx solid currentColor;
  border-right: 2rpx solid currentColor;
  transform: rotate(45deg);
}

.state-card {
  border-color: rgba(145, 103, 57, 0.16);
  background: #ffffff;
  box-shadow: 0 9rpx 26rpx rgba(68, 53, 34, 0.05);
}
</style>
