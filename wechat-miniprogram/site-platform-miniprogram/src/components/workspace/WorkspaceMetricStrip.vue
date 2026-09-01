<script setup lang="ts">
export interface WorkspaceMetric {
  key?: string;
  label: string;
  value: string | number;
  tone?: 'green' | 'amber' | 'red' | 'gray';
}

withDefaults(defineProps<{
  metrics: WorkspaceMetric[];
  accent?: string;
  motionKey?: string | number;
  interactive?: boolean;
}>(), {
  accent: '#527AA3',
  motionKey: '',
  interactive: false
});

const emit = defineEmits<{ (event: 'select', metric: WorkspaceMetric): void }>();
</script>

<template>
  <view class="metric-strip" :style="{ '--metric-accent': accent }">
    <view :key="motionKey" class="metric-grid" :style="{ '--metric-columns': Math.max(metrics.length, 1) }">
      <template v-if="interactive">
        <button
          v-for="metric in metrics"
          :key="metric.key || metric.label"
          class="metric-cell interactive"
          @tap="emit('select', metric)"
        >
          <text class="metric-value" :class="metric.tone">{{ metric.value }}</text>
          <text class="metric-label">{{ metric.label }}</text>
        </button>
      </template>
      <template v-else>
        <view v-for="metric in metrics" :key="metric.key || metric.label" class="metric-cell">
          <text class="metric-value" :class="metric.tone">{{ metric.value }}</text>
          <text class="metric-label">{{ metric.label }}</text>
        </view>
      </template>
    </view>
  </view>
</template>

<style scoped>
.metric-strip {
  overflow: hidden;
  border-radius: 16rpx;
  background: #fff;
  box-shadow: 0 8rpx 26rpx rgba(43, 56, 72, 0.055);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(var(--metric-columns), minmax(0, 1fr));
  animation: metric-refresh 220ms ease both;
}

.metric-cell {
  box-sizing: border-box;
  position: relative;
  width: auto;
  min-width: 0;
  margin: 0;
  padding: 23rpx 6rpx 20rpx;
  border: 0;
  border-radius: 0;
  background: transparent;
  line-height: normal;
  text-align: center;
}

.metric-cell::after { border: 0; }
.metric-cell.interactive { transition: background-color 100ms ease, transform 100ms ease; }
.metric-cell.interactive:active { background: rgba(49, 95, 134, .06); transform: scale(.98); }

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
  color: var(--metric-accent);
  font-size: 35rpx;
  font-weight: 800;
  line-height: 1.05;
}

.metric-label {
  margin-top: 10rpx;
  color: #647184;
  font-size: 21rpx;
}

.green { color: #2e8b72; }
.amber { color: #b87827; }
.red { color: #c95b5b; }
.gray { color: #68768a; }

@keyframes metric-refresh {
  from { opacity: 0; transform: scale(0.97) translateY(5rpx); }
  to { opacity: 1; transform: none; }
}

@media (prefers-reduced-motion: reduce) {
  .metric-grid { animation-duration: 1ms; }
}
</style>
