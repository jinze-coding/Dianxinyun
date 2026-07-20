<script setup lang="ts">
defineProps<{ label: string; value: number; tone?: 'green' | 'amber' | 'red' }>();
</script>
<template><view class="meter"><text class="label">{{ label }}</text><view class="track"><view class="bar" :class="tone || 'green'" :style="{ width: `${Math.max(0, Math.min(value, 100))}%` }"></view></view><text class="percent">{{ value.toFixed(1) }}%</text></view></template>
<style scoped>
.meter { display: grid; grid-template-columns: 132rpx 1fr 84rpx; align-items: center; gap: 14rpx; }.label { color: #727d8b; font-size: 21rpx; }.percent { color: #293548; font-size: 22rpx; font-weight: 800; text-align: right; }.track { height: 9rpx; overflow: hidden; border-radius: 999rpx; background: #e8e8e6; }.bar { height: 100%; border-radius: 999rpx; transition: width 520ms cubic-bezier(.2,.8,.2,1); }.green { background: #2b9673; }.amber { background: #bf732c; }.red { background: #bd5047; } @media (prefers-reduced-motion: reduce) { .bar { transition-duration: 1ms; } }
</style>
