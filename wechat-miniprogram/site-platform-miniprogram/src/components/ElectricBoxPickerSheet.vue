<script setup lang="ts">
import { ref, watch } from 'vue';
import { getElectricBoxes } from '@/api/electricBox';
import type { ElectricBox } from '@/types';

const props = withDefaults(defineProps<{
  visible: boolean;
  projectId: number;
  selectedId?: number;
  includeAll?: boolean;
  title?: string;
}>(), {
  includeAll: true,
  title: '选择电箱'
});

const emit = defineEmits<{
  close: [];
  select: [boxId?: number];
}>();

const keyword = ref('');
const boxes = ref<ElectricBox[]>([]);
const loading = ref(false);
const loadError = ref('');
let requestSequence = 0;

watch(() => props.visible, (visible) => {
  if (visible) {
    keyword.value = '';
    void loadBoxes('');
  } else {
    requestSequence += 1;
  }
});

watch(() => props.projectId, () => {
  if (!props.visible) return;
  keyword.value = '';
  void loadBoxes('');
});

watch(keyword, (value, _, onCleanup) => {
  if (!props.visible) return;
  requestSequence += 1;
  loading.value = true;
  loadError.value = '';
  const timer = setTimeout(() => {
    void loadBoxes(value);
  }, 250);
  onCleanup(() => clearTimeout(timer));
});

async function loadBoxes(searchKeyword = keyword.value) {
  if (!props.visible || !props.projectId) return;
  const requestId = ++requestSequence;
  loading.value = true;
  loadError.value = '';
  try {
    const result = await getElectricBoxes(props.projectId, searchKeyword);
    if (requestId !== requestSequence) return;
    boxes.value = result;
  } catch (error) {
    if (requestId !== requestSequence) return;
    boxes.value = [];
    loadError.value = error instanceof Error ? error.message : '电箱列表加载失败';
  } finally {
    if (requestId === requestSequence) loading.value = false;
  }
}

function selectBox(boxId?: number) {
  emit('select', boxId);
  emit('close');
}
</script>

<template>
  <view v-if="visible" class="box-sheet-overlay" @tap.self="emit('close')">
    <view class="box-sheet">
      <view class="box-sheet-head">
        <view>
          <text class="box-sheet-title">{{ title }}</text>
          <text class="box-sheet-subtitle">可按编号、名称、位置或责任电工搜索</text>
        </view>
        <button class="box-sheet-close" @tap="emit('close')">关闭</button>
      </view>

      <view class="box-search">
        <text class="search-icon"></text>
        <input
          v-model="keyword"
          type="text"
          confirm-type="search"
          placeholder="搜索电箱"
          :adjust-position="true"
          :cursor-spacing="24"
        />
        <button v-if="keyword" class="search-clear" @tap="keyword = ''">清除</button>
      </view>

      <scroll-view class="box-options" scroll-y enable-flex>
        <button
          v-if="includeAll && !keyword && !loading && !loadError"
          class="box-option"
          :class="{ active: selectedId === undefined }"
          @tap="selectBox(undefined)"
        >
          <view class="option-copy">
            <text class="option-code">全部电箱</text>
            <text class="option-meta">查看当前项目所有电箱</text>
          </view>
          <text v-if="selectedId === undefined" class="option-check">✓</text>
        </button>
        <view v-if="loading" class="box-state">
          <view class="box-loading"></view>
          <text>正在{{ keyword ? '搜索' : '加载' }}电箱</text>
        </view>
        <view v-else-if="loadError" class="box-state error">
          <text class="state-mark">!</text>
          <text>{{ loadError }}</text>
          <button class="retry-button" @tap="loadBoxes()">重新加载</button>
        </view>
        <button
          v-for="box in boxes"
          v-else
          :key="box.id"
          class="box-option"
          :class="{ active: selectedId === box.id }"
          @tap="selectBox(box.id)"
        >
          <view class="option-copy">
            <view class="option-title-line">
              <text class="option-code">{{ box.boxCode }}</text>
              <text class="option-status" :class="{ disabled: box.status !== 'ACTIVE' }">
                {{ box.status === 'ACTIVE' ? '使用中' : box.status === 'INACTIVE' ? '已停用' : '已拆除' }}
              </text>
            </view>
            <text class="option-name">{{ box.boxName || '未命名电箱' }}</text>
            <text class="option-meta">{{ box.installLocation || '未记录安装位置' }}</text>
            <text class="option-meta">责任电工：{{ box.responsibleElectricianName || '未指定' }}</text>
          </view>
          <text v-if="selectedId === box.id" class="option-check">✓</text>
        </button>
        <view v-if="!loading && !loadError && !boxes.length" class="box-empty">
          <text>未找到匹配的电箱</text>
          <text>{{ keyword ? '请尝试更换关键词' : '当前项目暂无可查看电箱' }}</text>
        </view>
      </scroll-view>
    </view>
  </view>
</template>

<style scoped>
.box-sheet-overlay {
  position: fixed;
  z-index: 220;
  inset: 0;
  display: flex;
  align-items: flex-end;
  background: rgba(24, 37, 52, .48);
}
.box-sheet {
  box-sizing: border-box;
  width: 100%;
  max-height: 82vh;
  padding: 24rpx 24rpx calc(24rpx + env(safe-area-inset-bottom));
  border-radius: 28rpx 28rpx 0 0;
  background: #fff;
  box-shadow: 0 -18rpx 48rpx rgba(24, 52, 78, .18);
}
.box-sheet-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18rpx;
}
.box-sheet-head view text,
.option-copy > text {
  display: block;
}
.box-sheet-title {
  color: #23374d;
  font-size: 29rpx;
  font-weight: 900;
}
.box-sheet-subtitle {
  margin-top: 5rpx;
  color: #8b99a8;
  font-size: 18rpx;
}
.box-sheet-close,
.search-clear {
  flex-shrink: 0;
  margin: 0;
  padding: 0;
  border: 0;
  background: transparent;
  color: #315f86;
  font-size: 20rpx;
  line-height: 1;
}
.box-sheet-close {
  height: 48rpx;
  padding: 0 8rpx;
}
.box-sheet-close::after,
.search-clear::after,
.box-option::after {
  border: 0;
}
.box-search {
  display: flex;
  height: 68rpx;
  align-items: center;
  gap: 12rpx;
  margin-top: 22rpx;
  padding: 0 18rpx;
  border: 1rpx solid #dbe5ed;
  border-radius: 15rpx;
  background: #f6f9fb;
}
.box-search input {
  min-width: 0;
  height: 100%;
  flex: 1;
  color: #34495f;
  font-size: 21rpx;
}
.search-icon {
  position: relative;
  width: 22rpx;
  height: 22rpx;
  flex-shrink: 0;
  border: 3rpx solid #8194a8;
  border-radius: 50%;
}
.search-icon::after {
  position: absolute;
  right: -8rpx;
  bottom: -5rpx;
  width: 9rpx;
  height: 3rpx;
  border-radius: 3rpx;
  background: #8194a8;
  content: '';
  transform: rotate(45deg);
}
.box-options {
  height: 58vh;
  max-height: 720rpx;
  margin-top: 18rpx;
}
.box-option {
  display: flex;
  box-sizing: border-box;
  width: 100%;
  min-height: 112rpx;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin: 0 0 12rpx;
  padding: 18rpx;
  border: 1rpx solid #e2e9ef;
  border-radius: 16rpx;
  background: #fff;
  text-align: left;
}
.box-option.active {
  border-color: #8bb9dc;
  background: #eef6fc;
  box-shadow: inset 0 0 0 1rpx rgba(49, 95, 134, .08);
}
.option-copy {
  min-width: 0;
  flex: 1;
}
.option-title-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14rpx;
}
.option-code {
  overflow: hidden;
  color: #273c52;
  font-size: 23rpx;
  font-weight: 850;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.option-status {
  flex-shrink: 0;
  padding: 4rpx 9rpx;
  border-radius: 999rpx;
  background: #e7f5ee;
  color: #26815e;
  font-size: 16rpx;
}
.option-status.disabled {
  background: #f1f2f3;
  color: #88929d;
}
.option-name {
  margin-top: 5rpx;
  color: #4f6277;
  font-size: 20rpx;
  font-weight: 700;
}
.option-meta {
  margin-top: 4rpx;
  overflow: hidden;
  color: #8794a2;
  font-size: 18rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.option-check {
  display: flex;
  width: 42rpx;
  height: 42rpx;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 50%;
  background: #315f86;
  color: #fff;
  font-size: 21rpx;
  font-weight: 900;
}
.box-empty {
  display: flex;
  min-height: 220rpx;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  color: #7d8d9e;
  font-size: 21rpx;
}
.box-empty text:last-child {
  margin-top: 8rpx;
  color: #a1acb7;
  font-size: 18rpx;
}
.box-state {
  display: flex;
  min-height: 240rpx;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  flex-direction: column;
  color: #718397;
  font-size: 20rpx;
  text-align: center;
}
.box-loading {
  width: 38rpx;
  height: 38rpx;
  border: 4rpx solid #dce7ef;
  border-top-color: #315f86;
  border-radius: 50%;
  animation: box-spin .8s linear infinite;
}
.state-mark {
  display: flex;
  width: 50rpx;
  height: 50rpx;
  align-items: center;
  justify-content: center;
  border-radius: 15rpx;
  background: #faecea;
  color: #b94c46;
  font-size: 27rpx;
  font-weight: 900;
}
.retry-button {
  display: flex;
  height: 58rpx;
  align-items: center;
  justify-content: center;
  margin: 4rpx 0 0;
  padding: 0 28rpx;
  border: 1rpx solid #b9d0e1;
  border-radius: 13rpx;
  background: #eef6fc;
  color: #315f86;
  font-size: 19rpx;
  line-height: 1;
}
.retry-button::after {
  border: 0;
}
@keyframes box-spin {
  to { transform: rotate(360deg); }
}
</style>
