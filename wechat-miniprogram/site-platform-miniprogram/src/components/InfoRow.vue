<script setup lang="ts">
defineProps<{
  label: string;
  value?: string | number;
}>();
</script>

<template>
  <view class="info-row">
    <text class="label">{{ label }}</text>
    <text class="value">{{ value || '-' }}</text>
  </view>
</template>

<style scoped>
.info-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20rpx;
  padding: 10rpx 0;
}

.label {
  color: #718096;
  font-size: 22rpx;
}

.value {
  flex: 1;
  color: #172033;
  font-size: 23rpx;
  font-weight: 800;
  line-height: 1.45;
  text-align: right;
}
</style>
