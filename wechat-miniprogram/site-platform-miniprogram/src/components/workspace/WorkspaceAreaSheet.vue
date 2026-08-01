<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import type { Project } from '@/types';
import WorkspaceStatusPill from './WorkspaceStatusPill.vue';

const props = withDefaults(defineProps<{
  open: boolean;
  project?: Project;
  projects: Project[];
  accent?: string;
  tint?: string;
}>(), {
  project: undefined,
  accent: '#527AA3',
  tint: '#EAF1F7'
});

const emit = defineEmits<{
  (event: 'close'): void;
  (event: 'select', projectId: number): void;
}>();
const closing = ref(false);
let closeTimer: ReturnType<typeof setTimeout> | undefined;
const optionListStyle = computed(() => ({
  height: `${Math.min(props.projects.length, 5) * 112}rpx`
}));

watch(() => props.open, (value) => {
  if (value) closing.value = false;
});

onBeforeUnmount(() => {
  if (closeTimer) clearTimeout(closeTimer);
});

function closeSheet(afterClose?: () => void) {
  if (!props.open || closing.value) return;
  closing.value = true;
  closeTimer = setTimeout(() => {
    closeTimer = undefined;
    closing.value = false;
    emit('close');
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
  <view
    v-if="open"
    class="area-overlay"
    :class="{ closing }"
    :style="{ '--area-accent': accent, '--area-tint': tint }"
    @tap="closeSheet()"
  >
    <view class="area-sheet" :class="{ closing }" @tap.stop>
      <view class="sheet-handle"></view>
      <view class="sheet-head">
        <view>
          <text class="sheet-title">选择施工区域</text>
          <text class="sheet-subtitle">切换后资料、巡检和质量数据同步更新</text>
        </view>
        <button class="sheet-close" aria-label="关闭" @tap="closeSheet()">×</button>
      </view>
      <scroll-view class="area-options" scroll-y :style="optionListStyle">
        <button
          v-for="item in projects"
          :key="item.id"
          class="area-option"
          :class="{ active: item.id === project?.id }"
          @tap="select(item.id)"
        >
          <text class="option-dot"></text>
          <view class="option-copy">
            <text class="option-name">{{ displayName(item) }}</text>
            <text class="option-meta">{{ item.stage || '未设置' }} · {{ item.contractor || '未设置施工单位' }} · {{ item.manager || '未指定负责人' }}</text>
          </view>
          <text v-if="item.id === project?.id" class="option-check"></text>
          <WorkspaceStatusPill v-else :label="isWarning(item) ? '预警' : '正常'" :tone="isWarning(item) ? 'amber' : 'green'" />
        </button>
      </scroll-view>
    </view>
  </view>
</template>

<style scoped>
.area-overlay { position: fixed; z-index: 1000; inset: 0; display: flex; align-items: flex-end; overflow: hidden; background: rgba(29,41,57,.34); animation: overlay-in 160ms ease both; }
.area-overlay.closing { animation: overlay-out 220ms ease both; }
.area-sheet { box-sizing: border-box; display: flex; width: 100%; max-height: 88vh; padding: 14rpx 26rpx calc(30rpx + env(safe-area-inset-bottom)); overflow: hidden; flex-direction: column; border-radius: 24rpx 24rpx 0 0; background: #fff; box-shadow: 0 -20rpx 50rpx rgba(29,41,57,.14); animation: sheet-in 220ms cubic-bezier(.2,.78,.32,1) both; }
.area-sheet.closing { animation: sheet-out 220ms ease both; }
.sheet-handle { width: 64rpx; height: 7rpx; margin: 0 auto 19rpx; border-radius: 999rpx; background: #d6dce4; }
.sheet-head { display: flex; align-items: flex-start; justify-content: space-between; flex-shrink: 0; gap: 22rpx; padding-bottom: 20rpx; }
.sheet-title { display: block; color: #1e293b; font-size: 31rpx; font-weight: 800; }
.sheet-subtitle { display: block; overflow: hidden; margin-top: 8rpx; color: var(--workspace-text-secondary, #52687a); font-size: 21rpx; line-height: 1.35; text-overflow: ellipsis; white-space: nowrap; }
.sheet-close { display: flex; width: 54rpx; height: 54rpx; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 50%; background: #f2f4f7; color: #647184; font-size: 34rpx; }
.sheet-close::after, .area-option::after { border: 0; }
.area-options { width: 100%; min-height: 0; max-height: 48vh; flex-shrink: 1; }
.area-option { box-sizing: border-box; display: flex; width: 100%; min-height: 112rpx; align-items: center; gap: 15rpx; padding: 16rpx 8rpx; border-top: 1rpx solid #edf0f3; background: #fff; text-align: left; }
.area-option.active { margin: 0; padding-right: 16rpx; padding-left: 16rpx; border: 0; border-radius: 14rpx; background: var(--area-tint); }
.option-copy { min-width: 0; flex: 1; }
.option-dot { width: 10rpx; height: 10rpx; flex-shrink: 0; border-radius: 50%; background: #d4dae2; }
.active .option-dot { background: var(--area-accent); box-shadow: 0 0 0 7rpx rgba(100,116,139,.1); }
.option-name { display: block; color: #263449; font-size: 25rpx; font-weight: 750; }
.option-meta { display: block; overflow: hidden; margin-top: 8rpx; color: var(--workspace-text-secondary, #52687a); font-size: 21rpx; line-height: 1.35; text-overflow: ellipsis; white-space: nowrap; }
.option-check { width: 20rpx; height: 11rpx; flex-shrink: 0; border-bottom: 3rpx solid var(--area-accent); border-left: 3rpx solid var(--area-accent); transform: rotate(-45deg); }
@keyframes overlay-in { from { opacity: 0; } to { opacity: 1; } }
@keyframes overlay-out { from { opacity: 1; } to { opacity: 0; } }
@keyframes sheet-in { from { transform: translateY(100%); } to { transform: none; } }
@keyframes sheet-out { from { transform: none; } to { transform: translateY(100%); } }
@media (prefers-reduced-motion: reduce) { .area-overlay, .area-sheet { animation-duration: 1ms; } }
</style>
