<script setup lang="ts">
const props = withDefaults(defineProps<{
  label: string;
  modelValue: string[];
  max?: number;
  required?: boolean;
}>(), {
  max: 4,
  required: false
});

const emit = defineEmits<{
  (event: 'update:modelValue', value: string[]): void;
}>();

function choosePhoto(index: number) {
  if (props.modelValue[index]) {
    emit('update:modelValue', props.modelValue.filter((_, currentIndex) => currentIndex !== index));
    return;
  }
  if (import.meta.env.VITE_USE_MOCK === 'true') {
    emit('update:modelValue', [...props.modelValue, `/static/mock-photo.svg?time=${Date.now()}`].slice(0, props.max));
    return;
  }
  uni.chooseImage({
    count: 1,
    success: (result) => {
      emit('update:modelValue', [...props.modelValue, result.tempFilePaths[0]].slice(0, props.max));
    }
  });
}
</script>

<template>
  <view class="photo-card card">
    <view class="row">
      <text class="label">{{ label }}</text>
      <text class="count" :class="{ ok: modelValue.length > 0 }">{{ modelValue.length ? '已上传' : required ? '必传' : '可选' }} {{ modelValue.length }}/{{ max }}</text>
    </view>
    <view class="grid-4 photo-grid">
      <button v-for="index in max" :key="index" class="slot" @tap="choosePhoto(index - 1)">
        <image v-if="modelValue[index - 1]" class="image" :src="modelValue[index - 1]" mode="aspectFill" />
        <text v-else class="camera">⌑</text>
      </button>
    </view>
  </view>
</template>

<style scoped>
.photo-card {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.label {
  color: #172033;
  font-size: 27rpx;
  font-weight: 800;
}

.count {
  color: #f59e0b;
  font-size: 22rpx;
  font-weight: 700;
}

.count.ok {
  color: #0f9f8f;
}

.photo-grid {
  gap: 14rpx;
}

.slot {
  display: flex;
  height: 112rpx;
  align-items: center;
  justify-content: center;
  border: 1rpx solid #d4e6f8;
  border-radius: 18rpx;
  background: #f4f9ff;
  overflow: hidden;
}

.camera {
  color: #6b7f99;
  font-size: 30rpx;
}

.image {
  width: 100%;
  height: 100%;
}
</style>
