<script setup lang="ts">
import { computed } from 'vue';

interface SegmentOption {
  value: string;
  label: string;
  badge?: number;
}

const props = withDefaults(defineProps<{
  modelValue: string;
  options: SegmentOption[];
  accent?: string;
  tint?: string;
}>(), {
  accent: '#527AA3',
  tint: '#EAF1F7'
});

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void;
}>();

const activeIndex = computed(() => Math.max(props.options.findIndex((item) => item.value === props.modelValue), 0));
const indicatorStyle = computed(() => ({
  width: `calc((100% - 8rpx) / ${Math.max(props.options.length, 1)})`,
  transform: `translateX(${activeIndex.value * 100}%)`,
  backgroundColor: props.tint,
  borderColor: `${props.accent}22`
}));
</script>

<template>
  <view class="segment-control" :style="{ '--segment-accent': accent }">
    <text class="segment-indicator" :style="indicatorStyle"></text>
    <button
      v-for="option in options"
      :key="option.value"
      class="segment-button"
      :class="{ active: modelValue === option.value }"
      @tap="emit('update:modelValue', option.value)"
    >
      <text>{{ option.label }}</text>
      <text v-if="option.badge !== undefined" class="segment-badge">{{ option.badge }}</text>
    </button>
  </view>
</template>

<style scoped>
.segment-control {
  position: relative;
  display: flex;
  min-height: 62rpx;
  padding: 4rpx;
  border-radius: 14rpx;
  background: #F2F4F7;
}

.segment-indicator {
  position: absolute;
  top: 4rpx;
  bottom: 4rpx;
  left: 4rpx;
  z-index: 0;
  box-sizing: border-box;
  border: 1rpx solid transparent;
  border-radius: 11rpx;
  box-shadow: 0 4rpx 12rpx rgba(43, 56, 72, 0.06);
  transition: transform 200ms cubic-bezier(0.2, 0.75, 0.3, 1), background-color 160ms ease;
}

.segment-button {
  position: relative;
  z-index: 1;
  display: flex;
  min-width: 0;
  min-height: 54rpx;
  align-items: center;
  justify-content: center;
  flex: 1;
  gap: 6rpx;
  color: #6B7280;
  background: transparent;
  font-size: 22rpx;
  font-weight: 600;
  transition: color 160ms ease, transform 100ms ease;
}

.segment-button::after {
  border: 0;
}

.segment-button:active {
  transform: scale(0.97);
}

.segment-button.active {
  color: var(--segment-accent);
  font-weight: 700;
}

.segment-badge {
  min-width: 27rpx;
  padding: 2rpx 7rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.72);
  font-size: 18rpx;
  text-align: center;
}

@media (prefers-reduced-motion: reduce) {
  .segment-indicator,
  .segment-button {
    transition-duration: 1ms;
  }
}
</style>
