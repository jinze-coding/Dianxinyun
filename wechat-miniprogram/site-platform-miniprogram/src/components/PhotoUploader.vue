<script setup lang="ts">
const props = defineProps<{
  label: string;
  modelValue: string[];
}>();

const emit = defineEmits<{
  (event: 'update:modelValue', value: string[]): void;
}>();

function choosePhoto() {
  if (import.meta.env.VITE_USE_MOCK === 'true') {
    emit('update:modelValue', [...props.modelValue, `/static/mock-photo.svg?time=${Date.now()}`]);
    return;
  }
  uni.chooseImage({
    count: 1,
    success: (result) => {
      const filePath = result.tempFilePaths[0];
      emit('update:modelValue', [...props.modelValue, filePath]);
    }
  });
}

function removePhoto(index: number) {
  emit('update:modelValue', props.modelValue.filter((_, currentIndex) => currentIndex !== index));
}
</script>

<template>
  <view class="photo card">
    <view class="row">
      <text class="photo-label">{{ label }}</text>
      <button class="ghost-button small" @tap="choosePhoto">添加</button>
    </view>
    <view v-if="props.modelValue.length" class="thumbs">
      <view v-for="(path, index) in props.modelValue" :key="path" class="thumb">
        <image class="thumb-img" :src="path" mode="aspectFill" />
        <button class="remove" @tap="removePhoto(index)">删除</button>
      </view>
    </view>
    <text v-else class="muted">尚未上传照片</text>
  </view>
</template>

<style scoped>
.photo {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.photo-label {
  color: #172033;
  font-size: 28rpx;
  font-weight: 700;
}

.small {
  min-height: 56rpx;
  padding: 0 18rpx;
  font-size: 24rpx;
}

.thumbs {
  display: flex;
  gap: 14rpx;
  flex-wrap: wrap;
}

.thumb {
  position: relative;
  width: 150rpx;
}

.thumb-img {
  width: 150rpx;
  height: 120rpx;
  border-radius: 18rpx;
  background: #edf6ff;
}

.remove {
  margin-top: 8rpx;
  color: #ef4444;
  font-size: 22rpx;
}
</style>
