<script setup lang="ts">
defineProps<{
  title: string;
  description?: string;
}>();
</script>

<template>
  <view class="empty">
    <text class="empty-title">{{ title }}</text>
    <text v-if="description" class="empty-desc">{{ description }}</text>
  </view>
</template>

<style scoped>
.empty {
  display: flex;
  min-height: 220rpx;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 12rpx;
  padding: 28rpx;
  border: 1rpx dashed #bdd8f5;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.78);
}

.empty-title {
  color: #26364d;
  font-size: 28rpx;
  font-weight: 700;
}

.empty-desc {
  color: #7a91aa;
  font-size: 24rpx;
}
</style>
