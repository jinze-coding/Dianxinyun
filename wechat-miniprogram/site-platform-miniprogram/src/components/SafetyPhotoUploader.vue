<script setup lang="ts">
import { computed } from 'vue';

const props = withDefaults(defineProps<{
  title: string;
  photos: string[];
  max?: number;
  required?: boolean;
  hint?: string;
  readonly?: boolean;
}>(), {
  max: 4,
  required: true,
  hint: '现场拍摄',
  readonly: false
});

const emit = defineEmits<{
  add: [];
  remove: [index: number];
}>();

const countText = computed(() => `${props.photos.length}/${props.max}`);

function preview(index: number) {
  uni.previewImage({ current: index, urls: props.photos });
}
</script>

<template>
  <view class="photo-card flow-card">
    <view class="photo-head">
      <view><text class="photo-title">{{ title }}<text v-if="required" class="required"> *</text></text><text class="photo-hint">{{ hint }}</text></view>
      <text class="photo-count" :class="{ complete: photos.length > 0 }">{{ countText }}</text>
    </view>
    <scroll-view class="photo-scroll" scroll-x enable-flex :show-scrollbar="false">
      <view class="photo-list">
        <view v-for="(photo, index) in photos" :key="`${photo}-${index}`" class="photo-item stagger-item" :style="{ animationDelay: `${index * 35}ms` }" @tap="preview(index)">
          <image :src="photo" mode="aspectFill" />
          <button v-if="!readonly" class="delete-button" @tap.stop="emit('remove', index)">×</button>
        </view>
        <button v-if="!readonly && photos.length < max" class="add-button pressable" @tap="emit('add')">
          <text class="camera-mark"><text></text></text>
          <text>{{ photos.length ? '继续添加' : '拍摄/添加' }}</text>
        </button>
        <view v-if="readonly && !photos.length" class="readonly-empty">本次巡检未上传照片</view>
      </view>
    </scroll-view>
  </view>
</template>

<style scoped src="../styles/safety-flow.css"></style>
<style scoped>
.photo-card { padding: 22rpx; }
.photo-head { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; }
.photo-title, .photo-hint { display: block; }
.photo-title { font-size: 26rpx; font-weight: 800; }
.required { color: #c9554d; }
.photo-hint { margin-top: 4rpx; color: #929aa5; font-size: 20rpx; }
.photo-count { padding: 6rpx 13rpx; border-radius: 999rpx; background: #eef3f7; color: #748398; font-size: 20rpx; font-weight: 750; }
.photo-count.complete { background: #e7f5ee; color: #18855a; }
.photo-scroll { width: 100%; margin-top: 18rpx; white-space: nowrap; }
.photo-list { display: inline-flex; gap: 14rpx; padding-right: 2rpx; }
.photo-item, .add-button { position: relative; width: 146rpx; height: 132rpx; flex: 0 0 146rpx; overflow: hidden; border-radius: 16rpx; }
.photo-item image { width: 100%; height: 100%; }
.delete-button { position: absolute; top: 7rpx; right: 7rpx; display: flex; width: 36rpx; height: 36rpx; min-height: 0; align-items: center; justify-content: center; margin: 0; padding: 0 0 3rpx; border-radius: 50%; background: rgba(24, 31, 42, .72); color: #fff; font-size: 29rpx; line-height: 1; }
.delete-button::after, .add-button::after { border: 0; }
.add-button { display: flex; align-items: center; justify-content: center; flex-direction: column; gap: 10rpx; margin: 0; border: 1rpx dashed var(--inspection-border); background: #f6faff; color: var(--inspection-primary-deep); font-size: 20rpx; }
.camera-mark { position: relative; display: block; width: 40rpx; height: 30rpx; border: 3rpx solid var(--inspection-primary); border-radius: 7rpx; }
.camera-mark::before { position: absolute; top: -8rpx; left: 9rpx; width: 16rpx; height: 7rpx; border-radius: 4rpx 4rpx 0 0; background: var(--inspection-primary); content: ''; }
.camera-mark text { position: absolute; top: 7rpx; left: 12rpx; width: 10rpx; height: 10rpx; border: 3rpx solid var(--inspection-primary); border-radius: 50%; }
.readonly-empty { display: flex; min-width: 280rpx; height: 88rpx; align-items: center; justify-content: center; padding: 0 22rpx; border: 1rpx solid var(--inspection-divider); border-radius: 14rpx; background: #f7fafc; color: var(--inspection-muted); font-size: 20rpx; }
</style>
