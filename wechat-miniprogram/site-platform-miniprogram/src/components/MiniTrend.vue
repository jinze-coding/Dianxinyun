<script setup lang="ts">
import { computed } from 'vue';

const props = withDefaults(defineProps<{
  values: number[];
  labels?: string[];
  maxValue?: number;
  tone?: 'green' | 'amber' | 'red';
}>(), { labels: () => [], maxValue: 0, tone: 'green' });

const plotWidth = 540;
const plotHeight = 220;
const chartMax = computed(() => {
  if (props.maxValue > 0) return props.maxValue;
  const raw = Math.max(...props.values, 1);
  return Math.max(4, Math.ceil(raw / 4) * 4);
});
const yTicks = computed(() => [chartMax.value, chartMax.value * .75, chartMax.value * .5, chartMax.value * .25, 0].map((value) => Math.round(value)));
const points = computed(() => {
  const values = props.values.length ? props.values : [0];
  const lastIndex = Math.max(values.length - 1, 1);
  return values.map((rawValue, index) => ({ x: (index / lastIndex) * plotWidth, y: (1 - Math.max(0, Math.min(rawValue, chartMax.value)) / chartMax.value) * plotHeight }));
});
const segments = computed(() => points.value.slice(0, -1).map((point, index) => {
  const next = points.value[index + 1];
  const dx = next.x - point.x;
  const dy = next.y - point.y;
  return { left: point.x, top: point.y, width: Math.sqrt(dx * dx + dy * dy), angle: Math.atan2(dy, dx) * 180 / Math.PI };
}));
const xTicks = computed(() => {
  const labels = props.labels.length ? props.labels : props.values.map((_, index) => `${index + 1}`);
  if (!labels.length) return [];
  const indexes = Array.from(new Set([0, Math.floor((labels.length - 1) * .25), Math.floor((labels.length - 1) * .5), Math.floor((labels.length - 1) * .75), labels.length - 1]));
  return indexes.map((index) => ({ label: labels[index], left: labels.length === 1 ? 0 : (index / (labels.length - 1)) * 100 }));
});
</script>

<template>
  <view class="trend" :class="tone">
    <view class="chart-row"><view class="y-axis"><text v-for="tick in yTicks" :key="tick">{{ tick }}</text></view><view class="plot"><text v-for="tick in yTicks" :key="`grid-${tick}`" class="grid-line"></text><text v-for="(segment, index) in segments" :key="`segment-${index}`" class="segment" :style="{ left: `${segment.left}rpx`, top: `${segment.top}rpx`, width: `${segment.width}rpx`, transform: `rotate(${segment.angle}deg)`, animationDelay: `${index * 10}ms` }"></text><text v-for="(point, index) in points" :key="`point-${index}`" class="point" :style="{ left: `${point.x}rpx`, top: `${point.y}rpx`, animationDelay: `${100 + index * 10}ms` }"></text></view></view>
    <view class="x-axis"><text v-for="tick in xTicks" :key="`${tick.label}-${tick.left}`" class="x-label" :style="{ left: `${tick.left}%` }">{{ tick.label }}</text></view>
  </view>
</template>

<style scoped>
.trend { --chart-color: #2b9673; margin-top: 26rpx; }.trend.amber { --chart-color: #bf732c; }.trend.red { --chart-color: #bd5047; }
.chart-row { display: flex; align-items: stretch; }.y-axis { display: flex; width: 52rpx; height: 226rpx; justify-content: space-between; flex-direction: column; }.y-axis text { color: #8b94a0; font-size: 19rpx; line-height: 1; }
.plot { position: relative; width: 540rpx; height: 226rpx; border-bottom: 1rpx solid #e3e6e9; }.grid-line { position: relative; display: block; width: 100%; height: 1rpx; border-top: 1rpx dashed #e6e7e8; }.grid-line+.grid-line { margin-top: 54rpx; }.grid-line:last-of-type { border-top-style: solid; }
.segment { position: absolute; height: 4rpx; border-radius: 999rpx; background: var(--chart-color); transform-origin: left center; animation: draw-segment 240ms ease both; }.point { position: absolute; width: 11rpx; height: 11rpx; border: 3rpx solid #fff; border-radius: 50%; background: var(--chart-color); box-shadow: 0 2rpx 7rpx rgba(42, 85, 69, .18); transform: translate(-50%, -50%); animation: pop-point 180ms ease both; }
.x-axis { position: relative; width: 540rpx; height: 40rpx; margin-left: 52rpx; }.x-label { position: absolute; top: 14rpx; color: #8b94a0; font-size: 18rpx; line-height: 1; transform: translateX(-50%); white-space: nowrap; }.x-label:first-child { transform: translateX(0); }.x-label:last-child { transform: translateX(-100%); }
@keyframes draw-segment { from { opacity: 0; transform: rotate(var(--angle, 0deg)) scaleX(0); } to { opacity: 1; } }
@keyframes pop-point { from { opacity: 0; transform: translate(-50%, -50%) scale(.4); } to { opacity: 1; transform: translate(-50%, -50%) scale(1); } }
@media (prefers-reduced-motion: reduce) { .segment, .point { animation-duration: 1ms; } }
</style>
