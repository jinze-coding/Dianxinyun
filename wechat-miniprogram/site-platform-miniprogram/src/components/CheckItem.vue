<script setup lang="ts">
import type { CheckResult } from '@/types';

const props = defineProps<{
  itemName: string;
  modelValue: CheckResult;
  description?: string;
}>();

const emit = defineEmits<{
  (event: 'update:modelValue', value: CheckResult): void;
  (event: 'update:description', value: string): void;
}>();

const options: Array<{ label: string; value: CheckResult }> = [
  { label: '正常', value: 'NORMAL' },
  { label: '异常', value: 'ABNORMAL' },
  { label: '不适用', value: 'NA' }
];

function updateDescription(event: unknown) {
  const inputEvent = event as { detail?: { value?: string }; target?: { value?: string } };
  emit('update:description', inputEvent.detail?.value || inputEvent.target?.value || '');
}
</script>

<template>
  <view class="check">
    <view class="row">
      <text class="name">{{ props.itemName }}</text>
    </view>
    <view class="segmented">
      <button
        v-for="option in options"
        :key="option.value"
        class="segment"
        :class="{ active: props.modelValue === option.value, danger: props.modelValue === option.value && option.value === 'ABNORMAL' }"
        @tap="emit('update:modelValue', option.value)"
      >
        {{ option.label }}
      </button>
    </view>
    <textarea
      v-if="props.modelValue === 'ABNORMAL'"
      class="textarea desc"
      :value="props.description"
      placeholder="填写异常说明"
      @input="updateDescription"
    />
  </view>
</template>

<style scoped>
.check {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  padding: 18rpx;
  border: 1rpx solid #cde3fb;
  border-radius: 20rpx;
  background: linear-gradient(145deg, #ffffff, #f7fbff);
}

.name {
  color: #172033;
  font-size: 24rpx;
  font-weight: 800;
}

.segmented {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10rpx;
}

.segment {
  min-height: 54rpx;
  border: 1rpx solid #d4e6f8;
  border-radius: 16rpx;
  background: #f4f9ff;
  color: #5f7187;
  font-size: 22rpx;
  font-weight: 800;
}

.active {
  border-color: rgba(15, 159, 143, 0.32);
  background: #e6fbf6;
  color: #0f9f8f;
}

.danger {
  border-color: rgba(239, 68, 68, 0.32);
  background: #ffe8e8;
  color: #d14343;
}

.desc {
  min-height: 96rpx;
}
</style>
