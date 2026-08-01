<script setup lang="ts">
import { USE_MOCK } from '@/api/request';
import type { Project } from '@/types';
import WorkspaceStatusPill from './WorkspaceStatusPill.vue';

const props = withDefaults(defineProps<{
  project?: Project;
  projects: Project[];
  accent?: string;
  tint?: string;
  open?: boolean;
}>(), {
  project: undefined,
  accent: '#527AA3',
  tint: '#EAF1F7',
  open: false
});

const emit = defineEmits<{ (event: 'open'): void }>();

function openSheet() {
  if (!props.projects.length) return;
  emit('open');
}

function isWarning(project?: Project) {
  return project?.status === 'warning' || project?.status === 'danger';
}

function displayName(project?: Project) {
  return project?.projectName || project?.shortName || '暂无施工区域';
}
</script>

<template>
  <view class="area-host" :style="{ '--area-accent': accent, '--area-tint': tint }">
    <button class="area-switcher" @tap="openSheet">
      <view class="area-marker"><text></text></view>
      <view class="area-main">
        <view class="area-title-line">
          <text class="area-kicker">当前施工区域</text>
          <view class="area-statuses">
            <WorkspaceStatusPill v-if="USE_MOCK" label="演示数据" tone="amber" />
            <WorkspaceStatusPill v-if="project" :label="isWarning(project) ? '预警' : '正常'" :tone="isWarning(project) ? 'amber' : 'green'" />
          </view>
        </view>
        <view class="area-name-line">
          <text class="area-title">{{ displayName(project) }}</text>
          <text v-if="projects.length" class="area-chevron" :class="{ open }"></text>
        </view>
        <text class="area-meta">{{ project ? `${project.stage || '未设置'} · ${project.contractor || '未设置施工单位'} · ${project.manager || '未指定负责人'}` : '当前账号没有授权施工区域' }}</text>
      </view>
    </button>

  </view>
</template>

<style scoped>
.area-host { position: relative; }
.area-switcher::after { border: 0; }
.area-switcher {
  display: flex;
  width: 100%;
  min-height: 142rpx;
  align-items: stretch;
  gap: 18rpx;
  padding: 23rpx 24rpx;
  overflow: hidden;
  border-radius: 16rpx;
  background: var(--area-tint);
  box-shadow: 0 10rpx 28rpx rgba(43, 56, 72, 0.065);
  text-align: left;
  transition: transform 100ms ease, box-shadow 100ms ease;
}
.area-switcher:active { transform: scale(0.988); }
.area-marker { position: relative; width: 8rpx; flex-shrink: 0; overflow: hidden; border-radius: 999rpx; background: rgba(255,255,255,.72); }
.area-marker text { position: absolute; top: 0; right: 0; left: 0; height: 62%; border-radius: inherit; background: var(--area-accent); }
.area-main { min-width: 0; flex: 1; }
.area-title-line, .area-name-line { display: flex; min-width: 0; align-items: center; }
.area-title-line { justify-content: space-between; gap: 18rpx; }
.area-statuses { display: flex; align-items: center; gap: 8rpx; }
.area-name-line { gap: 14rpx; margin-top: 7rpx; }
.area-kicker { color: var(--area-accent); font-size: 21rpx; font-weight: 700; }
.area-title { overflow: hidden; color: var(--workspace-text, #223247); font-size: 30rpx; font-weight: 800; line-height: 1.25; text-overflow: ellipsis; white-space: nowrap; }
.area-chevron { width: 13rpx; height: 13rpx; flex-shrink: 0; margin-top: -6rpx; border-right: 3rpx solid var(--area-accent); border-bottom: 3rpx solid var(--area-accent); transform: rotate(45deg); transition: transform 180ms ease; }
.area-chevron.open { margin-top: 5rpx; transform: rotate(225deg); }
.area-meta { display: block; overflow: hidden; color: var(--workspace-text-secondary, #52687a); font-size: 21rpx; line-height: 1.35; text-overflow: ellipsis; white-space: nowrap; }
.area-meta { margin-top: 9rpx; }
@media (prefers-reduced-motion: reduce) { .area-chevron { transition-duration: 1ms; } }
</style>
