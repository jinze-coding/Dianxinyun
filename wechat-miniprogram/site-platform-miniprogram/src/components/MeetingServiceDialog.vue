<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { onResize } from '@dcloudio/uni-app';
import { getNavLayoutMetrics } from '@/utils/navLayout';

const props = withDefaults(defineProps<{
  visible: boolean;
  title: string;
  closeLabel?: string;
}>(), { closeLabel: '关闭' });
const emit = defineEmits<{ (event: 'close'): void }>();
const metrics = ref(getNavLayoutMetrics());
const stageStyle = computed(() => ({
  paddingTop: `${metrics.value.navTotalHeight}px`,
  paddingBottom: `${metrics.value.safeBottom}px`
}));
const panelStyle = computed(() => ({
  height: `${Math.floor((metrics.value.windowHeight - metrics.value.navTotalHeight - metrics.value.safeBottom) * 0.85)}px`
}));
watch(() => props.visible, (visible) => { if (visible) metrics.value = getNavLayoutMetrics(); });
onResize(() => { metrics.value = getNavLayoutMetrics(); });
</script>

<template>
  <view v-if="visible" class="meeting-dialog">
    <view class="meeting-dialog-mask" @tap="emit('close')" @touchmove.stop.prevent />
    <view class="meeting-dialog-stage" :style="stageStyle" @tap="emit('close')">
      <view class="meeting-dialog-panel" :style="panelStyle" role="dialog" aria-modal="true" :aria-label="title" @tap.stop>
        <view class="meeting-dialog-head" @touchmove.stop.prevent>
          <text class="meeting-dialog-title">{{ title }}</text>
          <button class="meeting-dialog-close" :aria-label="closeLabel" @tap="emit('close')">{{ closeLabel }}</button>
        </view>
        <view class="meeting-dialog-body"><slot /></view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.meeting-dialog{position:fixed;inset:0;z-index:1000}
.meeting-dialog-mask{position:absolute;inset:0;background:rgba(19,38,59,.48)}
.meeting-dialog-stage{position:absolute;inset:0;box-sizing:border-box;display:flex;align-items:center;justify-content:center;pointer-events:none}
.meeting-dialog-panel{pointer-events:auto;width:92%;min-height:0;display:flex;flex-direction:column;overflow:hidden;border-radius:24rpx;background:#fff;color:var(--workspace-text,#263e53);box-shadow:0 24rpx 80rpx rgba(18,43,66,.24)}
.meeting-dialog-head{flex-shrink:0;display:flex;align-items:center;justify-content:space-between;gap:20rpx;padding:22rpx 26rpx;border-bottom:1rpx solid var(--workspace-divider,#dfe8f2);background:#f7faff}
.meeting-dialog-title{min-width:0;font-size:32rpx;font-weight:750;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.meeting-dialog-close{flex-shrink:0;margin:0;padding:0 22rpx;min-width:96rpx;line-height:64rpx;font-size:25rpx;border-radius:12rpx;background:#edf5ff;color:var(--workspace-accent-deep,#315f86)}
.meeting-dialog-close::after{border:0}
.meeting-dialog-body{flex:1;min-height:0;overflow:hidden}
</style>
