<script setup lang="ts">
interface SegmentOption {
  label: string;
  value: string;
  badge?: number;
}

defineProps<{
  modelValue: string;
  options: SegmentOption[];
}>();

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void;
}>();
</script>

<template>
  <view class="tabs">
    <button
      v-for="option in options"
      :key="option.value"
      class="tab"
      :class="{ active: modelValue === option.value }"
      @tap="emit('update:modelValue', option.value)"
    >
      <text>{{ option.label }}</text>
      <text v-if="option.badge !== undefined" class="badge">{{ option.badge }}</text>
    </button>
  </view>
</template>

<style scoped>
.tabs {
  display: flex;
  gap: 8rpx;
  overflow-x: auto;
}

.tab {
  display: inline-flex;
  min-height: 50rpx;
  flex-shrink: 0;
  align-items: center;
  gap: 8rpx;
  padding: 0 16rpx;
  border: 1rpx solid #cde3fb;
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.72);
  color: #5f7187;
  font-size: 22rpx;
  font-weight: 800;
  transition: transform 0.2s ease, background 0.2s ease, color 0.2s ease, border-color 0.2s ease;
}

.active {
  border-color: rgba(22, 119, 255, 0.36);
  background: #e7f1ff;
  color: #1677ff;
}

.badge {
  min-width: 26rpx;
  padding: 2rpx 7rpx;
  border-radius: 999rpx;
  background: rgba(22, 119, 255, 0.12);
  font-size: 19rpx;
}
</style>
