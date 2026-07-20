<script setup lang="ts">
import { computed } from 'vue';
import { getNavLayoutMetrics } from '@/utils/navLayout';

const props = withDefaults(defineProps<{
  title: string;
  showBack?: boolean;
}>(), {
  showBack: true
});

const emit = defineEmits<{
  (event: 'back'): void;
}>();

const metrics = getNavLayoutMetrics();

const shellStyle = computed(() => ({
  paddingTop: `${metrics.statusBarHeight}px`
}));

const innerStyle = computed(() => ({
  height: `${metrics.navHeight}px`
}));

const sideStyle = computed(() => ({
  width: `${metrics.rightWidth}px`
}));

function handleBack() {
  emit('back');
}
</script>

<template>
  <view class="app-nav" :style="shellStyle">
    <view class="app-nav-inner" :style="innerStyle">
      <button v-if="props.showBack" class="app-nav-back" aria-label="返回" @tap="handleBack">
        <text class="app-nav-back-icon"></text>
      </button>
      <view v-else class="app-nav-side" :style="sideStyle"></view>
      <text class="app-nav-title">{{ props.title }}</text>
      <view class="app-nav-side" :style="sideStyle"></view>
    </view>
  </view>
</template>

<style scoped>
.app-nav {
  position: relative;
  z-index: 12;
  width: 100%;
  background: transparent;
}

.app-nav-inner {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24rpx;
}

.app-nav-back {
  display: flex;
  width: 88rpx;
  height: 100%;
  align-items: center;
  justify-content: flex-start;
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.app-nav-back::after {
  border: 0;
}

.app-nav-back-icon {
  width: 28rpx;
  height: 28rpx;
  margin-left: 10rpx;
  border-bottom: 4rpx solid var(--workspace-text, var(--mp-text));
  border-left: 4rpx solid var(--workspace-text, var(--mp-text));
  transform: rotate(45deg);
}

.app-nav-title {
  position: absolute;
  right: 180rpx;
  left: 180rpx;
  overflow: hidden;
  color: var(--workspace-text, var(--mp-text));
  font-size: 30rpx;
  font-weight: var(--mp-weight-title);
  line-height: 1.2;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-nav-side {
  flex-shrink: 0;
  height: 100%;
}
</style>
