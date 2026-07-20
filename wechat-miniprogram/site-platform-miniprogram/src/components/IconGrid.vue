<script setup lang="ts">
import type { WorkbenchAction } from '@/constants/workbenchActions';

defineProps<{
  actions: WorkbenchAction[];
}>();

const emit = defineEmits<{
  (event: 'select', value: WorkbenchAction): void;
}>();
</script>

<template>
  <view class="grid">
    <button
      v-for="(action, index) in actions"
      :key="action.title"
      class="action stagger-in"
      :class="action.tone || 'green'"
      :style="{ '--delay': `${index * 45}ms` }"
      hover-class="action-hover"
      @tap="emit('select', action)"
    >
      <view class="line-icon" :class="[action.icon, action.tone || 'green']"></view>
      <text class="title">{{ action.title }}</text>
    </button>
  </view>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24rpx;
}

.action {
  display: flex;
  width: 100%;
  height: 246rpx;
  min-height: 246rpx;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  padding: 30rpx 10rpx 28rpx;
  border: 1rpx solid #cde3fb;
  border-radius: 26rpx;
  background: linear-gradient(145deg, #ffffff, #f7fbff);
  text-align: center;
  box-shadow: 0 14rpx 30rpx rgba(51, 112, 180, 0.1);
  transition: transform 0.22s ease, border-color 0.22s ease, box-shadow 0.22s ease, background 0.22s ease;
}

.action.green {
  background: linear-gradient(145deg, #ffffff, #e6fbf6);
}

.action.blue {
  background: linear-gradient(145deg, #ffffff, #e7f1ff);
}

.action.amber {
  background: linear-gradient(145deg, #ffffff, #fff4d8);
}

.action.red {
  background: linear-gradient(145deg, #ffffff, #ffe8e8);
}

.action.purple {
  background: linear-gradient(145deg, #ffffff, #f0ebff);
}

.action.slate {
  background: linear-gradient(145deg, #ffffff, #edf2f8);
}

.action-hover {
  transform: translateY(4rpx) scale(0.98);
  box-shadow: 0 8rpx 20rpx rgba(51, 112, 180, 0.14);
}

.line-icon {
  position: relative;
  display: flex;
  width: 98rpx;
  height: 98rpx;
  align-items: center;
  justify-content: center;
  margin: 0 auto 28rpx;
  border-radius: 20rpx;
  border: 6rpx solid currentColor;
  background: rgba(255, 255, 255, 0.58);
}

.line-icon::before,
.line-icon::after {
  position: absolute;
  content: "";
  box-sizing: border-box;
}

.ledger::before {
  width: 44rpx;
  height: 58rpx;
  border: 5rpx solid currentColor;
  border-radius: 6rpx;
}

.ledger::after {
  width: 30rpx;
  height: 6rpx;
  border-radius: 999rpx;
  background: currentColor;
  box-shadow: 0 17rpx 0 currentColor, 0 -17rpx 0 currentColor;
}

.scan {
  border: none;
}

.scan::before {
  width: 74rpx;
  height: 62rpx;
  border-radius: 12rpx;
  box-shadow:
    inset 0 0 0 0 currentColor,
    -2rpx -2rpx 0 -1rpx #ffffff,
    0 0 0 0 currentColor;
  background:
    linear-gradient(currentColor, currentColor) left top / 22rpx 7rpx no-repeat,
    linear-gradient(currentColor, currentColor) left top / 7rpx 22rpx no-repeat,
    linear-gradient(currentColor, currentColor) right top / 22rpx 7rpx no-repeat,
    linear-gradient(currentColor, currentColor) right top / 7rpx 22rpx no-repeat,
    linear-gradient(currentColor, currentColor) left bottom / 22rpx 7rpx no-repeat,
    linear-gradient(currentColor, currentColor) left bottom / 7rpx 22rpx no-repeat,
    linear-gradient(currentColor, currentColor) right bottom / 22rpx 7rpx no-repeat,
    linear-gradient(currentColor, currentColor) right bottom / 7rpx 22rpx no-repeat;
}

.scan::after {
  width: 50rpx;
  height: 7rpx;
  border-radius: 999rpx;
  background: currentColor;
}

.review,
.todo {
  border-radius: 16rpx;
}

.review::after,
.todo::after {
  top: -14rpx;
  width: 30rpx;
  height: 18rpx;
  border: 7rpx solid currentColor;
  border-bottom: none;
  border-radius: 10rpx 10rpx 0 0;
  background: #ffffff;
}

.review::before {
  width: 28rpx;
  height: 45rpx;
  border-right: 9rpx solid currentColor;
  border-bottom: 9rpx solid currentColor;
  transform: rotate(42deg) translate(-3rpx, -8rpx);
}

.summary {
  border-radius: 14rpx;
}

.summary::before {
  top: -13rpx;
  width: 30rpx;
  height: 17rpx;
  border: 7rpx solid currentColor;
  border-bottom: none;
  border-radius: 9rpx 9rpx 0 0;
  background: #ffffff;
}

.summary::after {
  width: 34rpx;
  height: 6rpx;
  border-radius: 999rpx;
  background: currentColor;
  box-shadow: 0 18rpx 0 currentColor, 0 -18rpx 0 currentColor;
}

.rectify {
  border: none;
  border-radius: 50%;
}

.rectify::before {
  width: 64rpx;
  height: 64rpx;
  border: 9rpx solid currentColor;
  border-left-color: transparent;
  border-radius: 50%;
}

.rectify::after {
  top: 22rpx;
  right: 11rpx;
  width: 20rpx;
  height: 20rpx;
  border-top: 9rpx solid currentColor;
  border-right: 9rpx solid currentColor;
  transform: rotate(32deg);
}

.todo::before {
  width: 34rpx;
  height: 23rpx;
  border-left: 8rpx solid currentColor;
  border-bottom: 8rpx solid currentColor;
  transform: rotate(-45deg) translate(3rpx, -4rpx);
}

.green {
  color: #0f9f8f;
}

.blue {
  color: #1677ff;
}

.amber {
  color: #f59e0b;
}

.red {
  color: #ef4444;
}

.purple {
  color: #7c3aed;
}

.slate {
  color: #6b7f99;
}

.title {
  display: block;
  color: #172033;
  font-size: 29rpx;
  font-weight: 900;
  line-height: 1.1;
}

/* 项目工作台紧凑业务入口。 */
.grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10rpx;
}

.action {
  height: 112rpx;
  min-height: 112rpx;
  justify-content: flex-start;
  flex-direction: row;
  gap: 5rpx;
  padding: 10rpx 13rpx;
  border-color: rgba(145, 103, 57, 0.1);
  border-radius: 15rpx;
  background: #faf9f7;
  box-shadow: none;
  text-align: left;
}

.action.green { background: #f1faf6; }
.action.blue { background: #f2f7fa; }
.action.amber { background: #fff8ef; }
.action.red { background: #fff4f2; }
.action.purple { background: #f7f4fb; }
.action.slate { background: #f4f6f8; }

.action-hover {
  transform: scale(0.98);
  box-shadow: 0 5rpx 14rpx rgba(68, 53, 34, 0.07);
}

.line-icon {
  width: 68rpx;
  height: 68rpx;
  flex: 0 0 68rpx;
  margin: 0;
  border-width: 6rpx;
  transform: scale(0.66);
}

.title {
  overflow: hidden;
  color: #354255;
  font-size: 21rpx;
  font-weight: 800;
  line-height: 1.3;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
