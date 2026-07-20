<script setup lang="ts">
defineProps<{
  modelValue: string;
  placeholder?: string;
}>();

const emit = defineEmits<{
  (event: 'update:modelValue', value: string): void;
}>();

function updateValue(event: unknown) {
  const inputEvent = event as { detail?: { value?: string }; target?: { value?: string } };
  emit('update:modelValue', inputEvent.detail?.value || inputEvent.target?.value || '');
}
</script>

<template>
  <view class="search">
    <text class="icon">⌕</text>
    <input class="input-inner" :value="modelValue" :placeholder="placeholder || '搜索'" @input="updateValue" />
  </view>
</template>

<style scoped>
.search {
  display: flex;
  min-height: 58rpx;
  align-items: center;
  gap: 12rpx;
  padding: 0 16rpx;
  border: 1rpx solid #cde3fb;
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.9);
  transition: border-color 0.22s ease, box-shadow 0.22s ease;
}

.search:focus-within {
  border-color: #1677ff;
  box-shadow: 0 0 0 6rpx rgba(22, 119, 255, 0.1);
}

.icon {
  color: #5f83aa;
  font-size: 28rpx;
  font-weight: 700;
}

.input-inner {
  flex: 1;
  min-width: 0;
  color: #172033;
  font-size: 23rpx;
}
</style>
