<script setup lang="ts">
defineProps<{
  title: string;
  subtitle?: string;
}>();
</script>

<template>
  <view class="page-title">
    <text class="title">{{ title }}</text>
    <text v-if="subtitle" class="subtitle">{{ subtitle }}</text>
  </view>
</template>

<style scoped>
.page-title {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  padding: 10rpx 0 18rpx;
}

.title {
  color: #111827;
  font-size: 40rpx;
  font-weight: 800;
}

.subtitle {
  color: #64748b;
  font-size: 25rpx;
  line-height: 1.5;
}
</style>
