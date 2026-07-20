<script setup lang="ts">
import type { PreviewMetric } from '../previewData';

withDefaults(defineProps<{
  metrics: PreviewMetric[];
  accent?: string;
  tint?: string;
  motionKey?: string | number;
}>(), {
  accent: '#527AA3',
  tint: '#EAF1F7',
  motionKey: ''
});
</script>

<template>
  <view class="summary-strip" :style="{ '--summary-accent': accent, '--summary-tint': tint }">
    <view :key="motionKey" class="metric-motion">
      <view v-for="metric in metrics" :key="metric.label" class="metric-cell">
        <text class="metric-value" :class="metric.tone">{{ metric.value }}</text>
        <text class="metric-label">{{ metric.label }}</text>
      </view>
    </view>
  </view>
</template>

<style scoped>
.summary-strip {
  overflow: hidden;
  border-radius: 16rpx;
  background: #ffffff;
  box-shadow: 0 8rpx 26rpx rgba(43, 56, 72, 0.055);
}

.metric-motion {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  animation: metric-refresh 220ms ease both;
}

.metric-cell {
  position: relative;
  min-width: 0;
  padding: 23rpx 6rpx 20rpx;
  text-align: center;
}

.metric-cell + .metric-cell::before {
  position: absolute;
  top: 24rpx;
  bottom: 24rpx;
  left: 0;
  width: 1rpx;
  background: rgba(100, 116, 139, 0.14);
  content: "";
}

.metric-value,
.metric-label {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-value {
  color: var(--summary-accent);
  font-size: 37rpx;
  font-weight: 800;
  line-height: 1.05;
}

.metric-label {
  margin-top: 10rpx;
  color: #647184;
  font-size: 22rpx;
  line-height: 1.1;
}

.metric-value.green {
  color: #2E8B72;
}

.metric-value.amber {
  color: #B87827;
}

.metric-value.red {
  color: #C95B5B;
}

.metric-value.gray {
  color: #68768A;
}

@keyframes metric-refresh {
  from {
    opacity: 0;
    transform: scale(0.97) translateY(5rpx);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .metric-motion {
    animation-duration: 1ms;
  }
}
</style>
