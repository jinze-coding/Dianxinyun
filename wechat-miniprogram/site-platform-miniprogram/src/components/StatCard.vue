<script setup lang="ts">
defineProps<{
  value: string | number;
  label: string;
  tone?: 'green' | 'blue' | 'amber' | 'red' | 'gray';
}>();
</script>

<template>
  <view class="stat-card">
    <text class="stat-value" :class="tone || 'green'">{{ value }}</text>
    <text class="stat-label">{{ label }}</text>
  </view>
</template>

<style scoped>
.stat-card {
  min-height: 96rpx;
  padding: 16rpx 12rpx;
  border: 1rpx solid #cde3fb;
  border-radius: 20rpx;
  background: linear-gradient(145deg, #ffffff, #f7fbff);
  box-shadow: 0 12rpx 26rpx rgba(51, 112, 180, 0.1);
}

.stat-value {
  display: block;
  font-size: 36rpx;
  font-weight: 900;
  line-height: 1.1;
  text-align: center;
}

.stat-label {
  display: block;
  margin-top: 6rpx;
  color: #7a91aa;
  font-size: 20rpx;
  text-align: center;
}

.green {
  color: #0f9f8f;
}

.blue {
  color: #1677ff;
}

.amber {
  color: #f59e0b;
}

.red {
  color: #ef4444;
}

.gray {
  color: #6b7f99;
}
</style>
