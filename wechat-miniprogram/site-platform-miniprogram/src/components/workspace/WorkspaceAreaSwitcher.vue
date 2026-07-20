<script setup lang="ts">
import { ref } from 'vue';
import { USE_MOCK } from '@/api/request';
import type { Project } from '@/types';
import WorkspaceStatusPill from './WorkspaceStatusPill.vue';

const props = withDefaults(defineProps<{
  project?: Project;
  projects: Project[];
  accent?: string;
  tint?: string;
}>(), {
  project: undefined,
  accent: '#527AA3',
  tint: '#EAF1F7'
});

const emit = defineEmits<{ (event: 'select', projectId: number): void }>();
const open = ref(false);
const closing = ref(false);

function openSheet() {
  if (!props.projects.length) return;
  closing.value = false;
  open.value = true;
}

function closeSheet(afterClose?: () => void) {
  if (!open.value || closing.value) return;
  closing.value = true;
  setTimeout(() => {
    open.value = false;
    closing.value = false;
    afterClose?.();
  }, 220);
}

function select(projectId: number) {
  closeSheet(() => emit('select', projectId));
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

    <view v-if="open" class="area-overlay" :class="{ closing }" @tap="closeSheet()">
      <view class="area-sheet" :class="{ closing }" @tap.stop>
        <view class="sheet-handle"></view>
        <view class="sheet-head">
          <view>
            <text class="sheet-title">选择施工区域</text>
            <text class="sheet-subtitle">切换后资料、巡检和质量数据同步更新</text>
          </view>
          <button class="sheet-close" aria-label="关闭" @tap="closeSheet()">×</button>
        </view>
        <view class="area-options">
          <button v-for="item in projects" :key="item.id" class="area-option" :class="{ active: item.id === project?.id }" @tap="select(item.id)">
            <text class="option-dot"></text>
            <view class="option-copy">
              <text class="option-name">{{ displayName(item) }}</text>
              <text class="option-meta">{{ item.stage || '未设置' }} · {{ item.contractor || '未设置施工单位' }} · {{ item.manager || '未指定负责人' }}</text>
            </view>
            <text v-if="item.id === project?.id" class="option-check"></text>
            <WorkspaceStatusPill v-else :label="isWarning(item) ? '预警' : '正常'" :tone="isWarning(item) ? 'amber' : 'green'" />
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.area-host { position: relative; }
.area-switcher::after, .sheet-close::after, .area-option::after { border: 0; }
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
.area-main, .option-copy { min-width: 0; flex: 1; }
.area-title-line, .area-name-line { display: flex; min-width: 0; align-items: center; }
.area-title-line { justify-content: space-between; gap: 18rpx; }
.area-statuses { display: flex; align-items: center; gap: 8rpx; }
.area-name-line { gap: 14rpx; margin-top: 7rpx; }
.area-kicker { color: var(--area-accent); font-size: 21rpx; font-weight: 700; }
.area-title { overflow: hidden; color: var(--workspace-text, #223247); font-size: 30rpx; font-weight: 800; line-height: 1.25; text-overflow: ellipsis; white-space: nowrap; }
.area-chevron { width: 13rpx; height: 13rpx; flex-shrink: 0; margin-top: -6rpx; border-right: 3rpx solid var(--area-accent); border-bottom: 3rpx solid var(--area-accent); transform: rotate(45deg); transition: transform 180ms ease; }
.area-chevron.open { margin-top: 5rpx; transform: rotate(225deg); }
.area-meta, .option-meta, .sheet-subtitle { display: block; overflow: hidden; color: var(--workspace-text-secondary, #52687a); font-size: 21rpx; line-height: 1.35; text-overflow: ellipsis; white-space: nowrap; }
.area-meta { margin-top: 9rpx; }
.area-overlay { position: fixed; z-index: 80; inset: 0; display: flex; align-items: flex-end; background: rgba(29,41,57,.34); animation: overlay-in 160ms ease both; }
.area-overlay.closing { animation: overlay-out 220ms ease both; }
.area-sheet { width: 100%; padding: 14rpx 26rpx calc(30rpx + env(safe-area-inset-bottom)); border-radius: 24rpx 24rpx 0 0; background: #fff; box-shadow: 0 -20rpx 50rpx rgba(29,41,57,.14); animation: sheet-in 220ms cubic-bezier(.2,.78,.32,1) both; }
.area-sheet.closing { animation: sheet-out 220ms ease both; }
.sheet-handle { width: 64rpx; height: 7rpx; margin: 0 auto 19rpx; border-radius: 999rpx; background: #d6dce4; }
.sheet-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 22rpx; padding-bottom: 20rpx; }
.sheet-title { display: block; color: #1e293b; font-size: 31rpx; font-weight: 800; }
.sheet-subtitle { margin-top: 8rpx; }
.sheet-close { display: flex; width: 54rpx; height: 54rpx; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 50%; background: #f2f4f7; color: #647184; font-size: 34rpx; }
.area-option { display: flex; width: 100%; min-height: 112rpx; align-items: center; gap: 15rpx; padding: 16rpx 8rpx; border-top: 1rpx solid #edf0f3; background: #fff; text-align: left; }
.area-option.active { margin: 0; padding-right: 16rpx; padding-left: 16rpx; border: 0; border-radius: 14rpx; background: var(--area-tint); }
.option-dot { width: 10rpx; height: 10rpx; flex-shrink: 0; border-radius: 50%; background: #d4dae2; }
.active .option-dot { background: var(--area-accent); box-shadow: 0 0 0 7rpx rgba(100,116,139,.1); }
.option-name { display: block; color: #263449; font-size: 25rpx; font-weight: 750; }
.option-meta { margin-top: 8rpx; }
.option-check { width: 20rpx; height: 11rpx; flex-shrink: 0; border-bottom: 3rpx solid var(--area-accent); border-left: 3rpx solid var(--area-accent); transform: rotate(-45deg); }
@keyframes overlay-in { from { opacity: 0; } to { opacity: 1; } }
@keyframes overlay-out { from { opacity: 1; } to { opacity: 0; } }
@keyframes sheet-in { from { transform: translateY(100%); } to { transform: none; } }
@keyframes sheet-out { from { transform: none; } to { transform: translateY(100%); } }
@media (prefers-reduced-motion: reduce) { .area-overlay, .area-sheet, .area-chevron { animation-duration: 1ms; transition-duration: 1ms; } }
</style>
