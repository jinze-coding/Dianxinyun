<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import AppTabBar from '@/components/AppTabBar.vue';
import WorkspaceAreaSwitcher from '@/components/workspace/WorkspaceAreaSwitcher.vue';
import WorkspaceMetricStrip, { type WorkspaceMetric } from '@/components/workspace/WorkspaceMetricStrip.vue';
import WorkspaceSegmentControl from '@/components/workspace/WorkspaceSegmentControl.vue';
import WorkspaceStatusPill from '@/components/workspace/WorkspaceStatusPill.vue';
import { getWorkspaceOverview } from '@/api/workspace';
import { useProjectStore } from '@/stores/project';
import type { WorkspaceOverview } from '@/types';
import { usePageScrollHeight } from '@/utils/navLayout';
import { showToast } from '@/utils/navigation';

type Panel = 'info' | 'documents' | 'devices';

const ACCENT = '#527AA3';
const TINT = '#EAF1F7';
const projectStore = useProjectStore();
const overview = ref<WorkspaceOverview | null>(null);
const loading = ref(false);
const errorMessage = ref('');
const cameraIndex = ref(0);
const panel = ref<Panel>('info');
const { scrollStyle } = usePageScrollHeight({ bottomRpx: 124, minHeight: 320 });

const projects = computed(() => projectStore.state.projects);
const currentProject = computed(() => projects.value.find((item) => item.id === projectStore.state.currentProjectId));
const cameras = computed(() => overview.value?.cameras || []);
const currentCamera = computed(() => cameras.value[cameraIndex.value % Math.max(cameras.value.length, 1)]);
const metrics = computed<WorkspaceMetric[]>(() => [
  { label: '在场', value: overview.value?.onsitePersonCount || 0 },
  { label: '摄像头', value: `${overview.value?.onlineCameraCount || 0}/${overview.value?.cameraTotal || 0}`, tone: 'green' },
  { label: '今日资料', value: overview.value?.todayFileCount || 0, tone: 'amber' },
  { label: '进度', value: `${overview.value?.projectProgress || 0}%` }
]);

function hideNativeTabBar() {
  uni.hideTabBar({ animation: false, fail: () => undefined });
}

onShow(async () => {
  hideNativeTabBar();
  await refresh();
});

async function refresh() {
  loading.value = true;
  errorMessage.value = '';
  try {
    await projectStore.loadProjects();
    if (!projectStore.state.currentProjectId) {
      overview.value = null;
      return;
    }
    overview.value = await getWorkspaceOverview(projectStore.state.currentProjectId);
    cameraIndex.value = 0;
  } catch (error) {
    overview.value = null;
    errorMessage.value = error instanceof Error ? error.message : '概况加载失败';
  } finally {
    loading.value = false;
  }
}

async function selectProject(projectId: number) {
  projectStore.setCurrentProject(projectId);
  await refresh();
  showToast(`已切换到${currentProject.value?.projectName || '施工区域'}`);
}

function cycleCamera() {
  if (!cameras.value.length) {
    showToast('当前区域暂无摄像头');
    return;
  }
  cameraIndex.value = (cameraIndex.value + 1) % cameras.value.length;
}

function setPanel(value: string) {
  panel.value = value as Panel;
}

function deviceTone(status?: string) {
  const value = (status || '').toLowerCase();
  if (['abnormal', 'alarm', 'danger', '异常', '告警'].includes(value)) return 'red' as const;
  if (['active', 'running', 'normal', '运行中', '正常'].includes(value)) return 'green' as const;
  return 'amber' as const;
}

function display(value?: string, fallback = '未设置') {
  return value && value.trim() ? value : fallback;
}
</script>

<template>
  <view class="workspace-shell overview-page" :style="{ '--page-accent': ACCENT, '--page-accent-deep': '#3E6488', '--page-tint': TINT, '--page-background': '#F3F6F8' }">
    <AppNavBar title="项目概况" :show-back="false" />
    <scroll-view class="workspace-scroll" scroll-y enable-flex :style="scrollStyle">
      <view class="workspace-content">
        <WorkspaceAreaSwitcher :project="currentProject" :projects="projects" :accent="ACCENT" :tint="TINT" @select="selectProject" />

        <view v-if="loading && !overview" class="state-panel"><text class="state-title">正在加载施工区域概况</text></view>
        <view v-else-if="errorMessage" class="state-panel">
          <text class="state-title">概况加载失败</text><text class="state-desc">{{ errorMessage }}</text><button class="retry-button" @tap="refresh">重新加载</button>
        </view>
        <template v-else-if="overview && currentProject">
          <WorkspaceMetricStrip :metrics="metrics" :accent="ACCENT" :motion-key="currentProject.id" />

          <button class="risk-row" @tap="panel = 'devices'">
            <view class="risk-copy"><text class="risk-dot"></text><text class="risk-title">风险提醒</text><text class="risk-text">{{ overview.riskAlert }}</text></view>
            <text class="row-arrow"></text>
          </button>

          <view class="section-block">
            <view class="section-head"><text class="section-title">现场视频</text><text class="section-note">{{ overview.onlineCameraCount }} 路在线</text></view>
            <view v-if="currentCamera" class="video-preview" :class="{ offline: !currentCamera.online }">
              <view class="video-topline"><text class="live-badge">{{ currentCamera.online ? 'LIVE' : 'OFFLINE' }}</text><text>{{ currentCamera.code || '实时资源' }}</text></view>
              <view :key="currentCamera.id" class="video-scene">
                <view class="scene-building left"></view><view class="scene-building right"></view><view class="scene-crane"></view><view class="scene-ground"></view>
                <view class="stream-hint"><text>{{ currentCamera.online ? '视频流需经 WebRTC/HLS 网关播放' : '摄像头当前离线' }}</text></view>
              </view>
              <view class="video-caption"><text>{{ currentCamera.name }}</text><text>{{ currentCamera.area || currentProject.projectName }}</text></view>
            </view>
            <view v-else class="video-empty">当前施工区域暂无摄像头资源</view>
            <view class="video-actions"><button @tap="cycleCamera"><image src="/static/design-preview-icons/overview-switch-camera.png" mode="aspectFit" />切换摄像头</button><button @tap="showToast('摄像头管理请在 PC 端操作')"><image src="/static/design-preview-icons/overview-camera.png" mode="aspectFit" />查看全部</button></view>
          </view>

          <view class="section-block detail-block">
            <WorkspaceSegmentControl :model-value="panel" :options="[{ value: 'info', label: '区域信息' }, { value: 'documents', label: '最近资料' }, { value: 'devices', label: '异常设备' }]" :accent="ACCENT" :tint="TINT" @update:model-value="setPanel" />
            <view v-if="panel === 'info'" class="info-list">
              <view class="info-row"><text>施工阶段</text><text>{{ display(currentProject.stage) }}</text></view>
              <view class="info-row"><text>区域面积</text><text>{{ currentProject.area ? `${currentProject.area} ㎡` : '未设置' }}</text></view>
              <view class="info-row"><text>计划工期</text><text>{{ display(currentProject.period) }}</text></view>
              <view class="info-row"><text>区域负责人</text><text>{{ display(currentProject.manager, '未指定') }}</text></view>
            </view>
            <view v-else-if="panel === 'documents'" class="plain-list">
              <view v-for="file in overview.recentFiles" :key="file.id" class="plain-row"><view class="list-icon file-icon"></view><view class="plain-copy"><text class="plain-title">{{ file.name }}</text><text class="plain-meta">{{ file.type || '资料' }} · {{ file.status || '已上传' }}</text></view></view>
              <view v-if="!overview.recentFiles.length" class="empty-line">暂无资料</view>
            </view>
            <view v-else class="plain-list">
              <view v-for="device in overview.devices" :key="device.id" class="plain-row"><view class="list-icon device-icon"></view><view class="plain-copy"><text class="plain-title">{{ device.name }}</text><text class="plain-meta">{{ device.type || '设备' }} · {{ device.remark || '暂无备注' }}</text></view><WorkspaceStatusPill :label="device.status || '未知'" :tone="deviceTone(device.status)" /></view>
              <view v-if="!overview.devices.length" class="empty-line">暂无设备</view>
            </view>
          </view>
        </template>
      </view>
    </scroll-view>
    <AppTabBar active="overview" />
  </view>
</template>

<style scoped src="../../styles/workspace-page.css"></style>
<style scoped>
.risk-row { display: flex; width: 100%; min-height: 72rpx; align-items: center; justify-content: space-between; gap: 16rpx; padding: 0 21rpx; border-radius: 14rpx; background: #fff5e8; box-shadow: inset 5rpx 0 0 #d79748, 0 7rpx 22rpx rgba(145,93,30,.055); text-align: left; }
.risk-row::after, .video-actions button::after { border: 0; }
.risk-copy { display: flex; min-width: 0; align-items: center; gap: 11rpx; }
.risk-dot { width: 11rpx; height: 11rpx; flex-shrink: 0; border-radius: 50%; background: #cf8631; box-shadow: 0 0 0 7rpx rgba(207,134,49,.12); }
.risk-title { flex-shrink: 0; color: #815623; font-size: 23rpx; font-weight: 800; }
.risk-text { overflow: hidden; color: #8d673b; font-size: 22rpx; text-overflow: ellipsis; white-space: nowrap; }
.video-preview { position: relative; margin: 20rpx 20rpx 0; overflow: hidden; aspect-ratio: 16/9; border-radius: 14rpx; background: #c7d1d9; box-shadow: 0 8rpx 22rpx rgba(35,51,69,.12); }
.video-topline, .video-caption { position: absolute; right: 0; left: 0; z-index: 3; display: flex; align-items: center; justify-content: space-between; padding: 15rpx 17rpx; color: #fff; font-size: 19rpx; }
.video-topline { top: 0; background: linear-gradient(180deg,rgba(13,24,36,.58),transparent); }
.video-caption { bottom: 0; background: linear-gradient(0deg,rgba(13,24,36,.72),transparent); font-size: 22rpx; }
.live-badge { padding: 5rpx 11rpx; border-radius: 999rpx; background: rgba(15,23,42,.62); font-weight: 700; }
.video-scene { position: absolute; inset: 0; overflow: hidden; background: linear-gradient(180deg,#b7c6d3 0%,#dce4e8 56%,#9b9d94 57%,#7e827d 100%); animation: video-in 220ms ease both; }
.scene-building { position: absolute; bottom: 25%; width: 31%; height: 44%; border: 2rpx solid rgba(255,255,255,.45); background: repeating-linear-gradient(0deg,rgba(255,255,255,.22) 0 2rpx,transparent 2rpx 20rpx),#59646c; }
.scene-building.left { left: 8%; } .scene-building.right { right: 7%; height: 57%; }
.scene-crane { position: absolute; bottom: 25%; left: 49%; width: 4rpx; height: 49%; background: #d5a74c; }
.scene-crane::after { position: absolute; top: 0; left: -70rpx; width: 170rpx; height: 4rpx; background: #d5a74c; content: ""; }
.scene-ground { position: absolute; right: 0; bottom: 0; left: 0; height: 25%; background: linear-gradient(165deg,#8b908c,#656c69); }
.stream-hint { position: absolute; right: 20rpx; bottom: 58rpx; left: 20rpx; z-index: 2; color: rgba(255,255,255,.64); font-size: 18rpx; text-align: center; }
.video-empty { display: flex; height: 250rpx; align-items: center; justify-content: center; margin: 20rpx; border-radius: 14rpx; background: #eef2f4; color: #8390a1; font-size: 23rpx; }
.video-actions { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 14rpx; padding: 17rpx 20rpx 20rpx; }
.video-actions button { display: flex; min-height: 62rpx; align-items: center; justify-content: center; gap: 10rpx; border-radius: 12rpx; background: var(--page-tint); color: var(--page-accent-deep); font-size: 22rpx; }
.video-actions image { width: 29rpx; height: 29rpx; }
.detail-block { padding: 18rpx 0 0; }
.detail-block :deep(.segment-control) { margin: 0 20rpx; }
.info-list { padding: 10rpx 20rpx 12rpx; }
.info-row { display: flex; min-height: 70rpx; align-items: center; justify-content: space-between; gap: 24rpx; border-bottom: 1rpx solid #edf0f3; color: #748195; font-size: 23rpx; }
.info-row:last-child { border-bottom: 0; }.info-row text:last-child { color: #263449; font-weight: 650; text-align: right; }
.list-icon { width: 49rpx; height: 49rpx; flex-shrink: 0; border-radius: 12rpx; background: var(--page-tint); }
.file-icon::after { display: block; width: 20rpx; height: 25rpx; margin: 11rpx auto; border: 3rpx solid var(--page-accent); border-radius: 4rpx; content: ""; }
.device-icon::after { display: block; width: 23rpx; height: 18rpx; margin: 13rpx auto; border: 3rpx solid var(--page-accent); border-radius: 4rpx; content: ""; }
.empty-line { padding: 42rpx 0; color: #98a2b3; font-size: 22rpx; text-align: center; }
@keyframes video-in { from { opacity: .2; transform: scale(1.01); } to { opacity: 1; transform: none; } }
</style>
