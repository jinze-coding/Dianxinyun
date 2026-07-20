<script setup lang="ts">
import { ref } from 'vue';
import type { PreviewArea } from '../previewData';
import PreviewStatusPill from './PreviewStatusPill.vue';

const props = withDefaults(defineProps<{
  area: PreviewArea;
  areas: PreviewArea[];
  accent?: string;
  tint?: string;
}>(), {
  accent: '#527AA3',
  tint: '#EAF1F7'
});

const emit = defineEmits<{
  (event: 'select', areaId: number): void;
}>();

const open = ref(false);
const closing = ref(false);

function openSheet() {
  closing.value = false;
  open.value = true;
}

function closeSheet(afterClose?: () => void) {
  if (!open.value || closing.value) return;
  closing.value = true;
  setTimeout(() => {
    open.value = false;
    closing.value = false;
    afterClose?.();
  }, 220);
}

function select(areaId: number) {
  closeSheet(() => emit('select', areaId));
}
</script>

<template>
  <view class="area-switcher-host" :style="{ '--area-accent': accent, '--area-tint': tint }">
    <button class="area-switcher" hover-class="area-switcher-hover" @tap="openSheet">
      <view class="area-marker"><text></text></view>
      <view class="area-main">
        <view class="area-title-line">
          <text class="area-kicker">当前施工区域</text>
          <PreviewStatusPill :label="props.area.status === 'warning' ? '预警' : '正常'" :tone="props.area.status === 'warning' ? 'amber' : 'green'" />
        </view>
        <view class="area-name-line">
          <text class="area-title">{{ props.area.name }}</text>
          <text class="area-chevron" :class="{ open }"></text>
        </view>
        <text class="area-meta">{{ props.area.stage }} · {{ props.area.contractor }} · {{ props.area.manager }}</text>
      </view>
    </button>

    <view v-if="open" class="area-overlay" :class="{ closing }" @tap="closeSheet()">
      <view class="area-sheet" :class="{ closing }" @tap.stop>
        <view class="sheet-handle"></view>
        <view class="sheet-head">
          <view>
            <text class="sheet-title">选择施工区域</text>
            <text class="sheet-subtitle">切换后概况、人员、质量和安全数据同步更新</text>
          </view>
          <button class="sheet-close" aria-label="关闭" @tap="closeSheet()">×</button>
        </view>
        <view class="area-options">
          <button
            v-for="item in props.areas"
            :key="item.id"
            class="area-option"
            :class="{ active: item.id === props.area.id }"
            @tap="select(item.id)"
          >
            <text class="option-dot"></text>
            <view class="option-copy">
              <text class="option-name">{{ item.name }}</text>
              <text class="option-meta">{{ item.stage }} · {{ item.contractor }} · {{ item.manager }}</text>
            </view>
            <text v-if="item.id === props.area.id" class="option-check"></text>
            <PreviewStatusPill v-else :label="item.status === 'warning' ? '预警' : '正常'" :tone="item.status === 'warning' ? 'amber' : 'green'" />
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped>
.area-switcher-host {
  position: relative;
}

.area-switcher::after,
.sheet-close::after,
.area-option::after {
  border: 0;
}

.area-switcher {
  display: flex;
  width: 100%;
  min-height: 142rpx;
  align-items: stretch;
  gap: 18rpx;
  padding: 23rpx 24rpx;
  overflow: hidden;
  border-radius: 16rpx;
  background: var(--area-tint);
  box-shadow: 0 10rpx 28rpx rgba(43, 56, 72, 0.065);
  text-align: left;
  transition: transform 100ms ease, box-shadow 100ms ease;
}

.area-switcher:active,
.area-switcher-hover {
  box-shadow: 0 5rpx 16rpx rgba(43, 56, 72, 0.06);
  transform: scale(0.988);
}

.area-marker {
  position: relative;
  width: 8rpx;
  flex-shrink: 0;
  overflow: hidden;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.72);
}

.area-marker text {
  position: absolute;
  top: 0;
  right: 0;
  left: 0;
  height: 62%;
  border-radius: inherit;
  background: var(--area-accent);
}

.area-main,
.option-copy {
  min-width: 0;
  flex: 1;
}

.area-title-line,
.area-name-line {
  display: flex;
  min-width: 0;
  align-items: center;
}

.area-title-line {
  justify-content: space-between;
  gap: 18rpx;
}

.area-name-line {
  gap: 14rpx;
  margin-top: 7rpx;
}

.area-kicker {
  color: var(--area-accent);
  font-size: 21rpx;
  font-weight: 700;
}

.area-title {
  overflow: hidden;
  color: #1E293B;
  font-size: 32rpx;
  font-weight: 800;
  line-height: 1.2;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.area-chevron {
  width: 13rpx;
  height: 13rpx;
  flex-shrink: 0;
  margin-top: -6rpx;
  border-right: 3rpx solid var(--area-accent);
  border-bottom: 3rpx solid var(--area-accent);
  transform: rotate(45deg);
  transition: transform 180ms ease;
}

.area-chevron.open {
  margin-top: 5rpx;
  transform: rotate(225deg);
}

.area-meta,
.option-meta,
.sheet-subtitle {
  display: block;
  overflow: hidden;
  color: #647184;
  font-size: 22rpx;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.area-meta {
  margin-top: 9rpx;
}

.area-overlay {
  position: fixed;
  z-index: 80;
  inset: 0;
  display: flex;
  align-items: flex-end;
  background: rgba(29, 41, 57, 0.34);
  animation: overlay-in 160ms ease both;
}

.area-overlay.closing {
  animation: overlay-out 220ms ease both;
}

.area-sheet {
  width: 100%;
  padding: 14rpx 26rpx calc(30rpx + env(safe-area-inset-bottom));
  border-radius: 24rpx 24rpx 0 0;
  background: #FFFFFF;
  box-shadow: 0 -20rpx 50rpx rgba(29, 41, 57, 0.14);
  animation: sheet-in 220ms cubic-bezier(0.2, 0.78, 0.32, 1) both;
}

.area-sheet.closing {
  animation: sheet-out 220ms ease both;
}

.sheet-handle {
  width: 64rpx;
  height: 7rpx;
  margin: 0 auto 19rpx;
  border-radius: 999rpx;
  background: #D6DCE4;
}

.sheet-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 22rpx;
  padding-bottom: 20rpx;
}

.sheet-title {
  display: block;
  color: #1E293B;
  font-size: 31rpx;
  font-weight: 800;
}

.sheet-subtitle {
  margin-top: 8rpx;
}

.sheet-close {
  display: flex;
  width: 60rpx;
  height: 60rpx;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 50%;
  background: #F1F4F7;
  color: #667085;
  font-size: 36rpx;
  line-height: 1;
  transition: transform 100ms ease;
}

.sheet-close:active {
  transform: scale(0.92);
}

.area-options {
  overflow: hidden;
  border-top: 1rpx solid #E7EBF0;
}

.area-option {
  display: flex;
  width: 100%;
  min-height: 108rpx;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding: 18rpx 6rpx;
  border-bottom: 1rpx solid #E7EBF0;
  text-align: left;
  transition: background-color 120ms ease, transform 100ms ease;
}

.area-option:active {
  background: #F7F9FB;
  transform: scale(0.99);
}

.area-option.active {
  background: var(--area-tint);
}

.option-dot {
  width: 12rpx;
  height: 12rpx;
  flex-shrink: 0;
  border-radius: 50%;
  background: #C8D0DA;
}

.area-option.active .option-dot {
  background: var(--area-accent);
  box-shadow: 0 0 0 8rpx rgba(82, 122, 163, 0.1);
}

.option-name {
  display: block;
  overflow: hidden;
  color: #283548;
  font-size: 26rpx;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.area-option.active .option-name {
  color: var(--area-accent);
}

.option-meta {
  margin-top: 7rpx;
}

.option-check {
  width: 24rpx;
  height: 13rpx;
  margin-right: 12rpx;
  border-bottom: 4rpx solid var(--area-accent);
  border-left: 4rpx solid var(--area-accent);
  transform: rotate(-45deg);
}

@keyframes overlay-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes overlay-out {
  from { opacity: 1; }
  to { opacity: 0; }
}

@keyframes sheet-in {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}

@keyframes sheet-out {
  from { transform: translateY(0); }
  to { transform: translateY(100%); }
}

@media (prefers-reduced-motion: reduce) {
  .area-switcher,
  .area-chevron,
  .area-overlay,
  .area-sheet,
  .area-option,
  .sheet-close {
    animation-duration: 1ms;
    transition-duration: 1ms;
  }
}
</style>
