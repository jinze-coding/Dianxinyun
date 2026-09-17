<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { QualityAssignee } from '@/types';

const props = withDefaults(defineProps<{
  visible: boolean;
  options: QualityAssignee[];
  selectedId?: number;
  title?: string;
}>(), { title: '选择整改负责人' });
const emit = defineEmits<{
  close: [];
  select: [userId: number];
}>();
const keyword = ref('');
const keyboardHeight = ref(0);
const results = computed(() => {
  const words = keyword.value.trim().toLowerCase().split(/\s+/).filter(Boolean);
  return props.options.filter((person) => {
    const text = [person.displayName, person.realName, person.username].filter(Boolean).join(' ').toLowerCase();
    return words.every((word) => text.includes(word));
  });
});

watch(() => props.visible, () => {
  keyword.value = '';
  keyboardHeight.value = 0;
});

function hideKeyboard() {
  uni.hideKeyboard();
}

function close() {
  hideKeyboard();
  emit('close');
}

function select(userId: number) {
  if (!props.visible || !props.options.some((person) => person.userId === userId)) return;
  emit('select', userId);
  close();
}

function keyboardChanged(event: unknown) {
  const height = Number((event as { detail?: { height?: number } }).detail?.height || 0);
  keyboardHeight.value = Math.max(0, height);
}
</script>

<template>
  <view v-if="visible" class="assignee-overlay" :style="{ bottom: `${keyboardHeight}px` }">
    <view class="assignee-backdrop" @tap="close" @touchmove.stop.prevent></view>
    <view class="assignee-sheet" role="dialog" :aria-label="title" @tap.stop>
      <view class="assignee-head"><text class="assignee-title">{{ title }}</text><button class="assignee-cancel" @tap="close">取消</button></view>
      <view class="assignee-search">
        <input v-model="keyword" class="assignee-search-input" placeholder="搜索姓名或账号" :maxlength="80"
          confirm-type="search" :adjust-position="false" @keyboardheightchange="keyboardChanged" @confirm="hideKeyboard" />
        <button v-if="keyword" class="assignee-clear" @tap="keyword = ''">清空</button>
      </view>
      <text class="assignee-count">{{ keyword.trim() ? `找到 ${results.length} 人` : `共 ${options.length} 位可选人员` }} · 点击姓名选择</text>
      <scroll-view :key="keyword" class="assignee-options" scroll-y enable-flex>
        <button v-for="person in results" :key="person.userId" class="assignee-option"
          :class="{ selected: person.userId === selectedId }" @tap="select(person.userId)">
          <view class="assignee-copy"><text class="assignee-name">{{ person.displayName || person.realName || person.username }}</text><text class="assignee-account">账号：{{ person.username }}</text></view>
          <text v-if="person.userId === selectedId" class="assignee-selected">✓ 已选</text>
        </button>
        <view v-if="!results.length" class="assignee-empty"><text>{{ options.length ? '未找到匹配人员' : '当前项目暂无可选整改人' }}</text><text v-if="options.length">换个姓名或账号试试</text></view>
      </scroll-view>
    </view>
  </view>
</template>

<style scoped>
.assignee-overlay { position: fixed; z-index: 1200; top: 0; right: 0; left: 0; display: flex; align-items: flex-end; }
.assignee-backdrop { position: absolute; inset: 0; background: rgba(24,37,52,.46); }
.assignee-sheet { position: relative; box-sizing: border-box; display: flex; flex-direction: column; width: 100%; height: 72vh; max-height: calc(100% - 24rpx); padding: 26rpx 24rpx calc(22rpx + env(safe-area-inset-bottom)); overflow: hidden; border-radius: 26rpx 26rpx 0 0; background: #fff; box-shadow: 0 -16rpx 44rpx rgba(24,52,78,.16); }
.assignee-head { display: flex; align-items: center; justify-content: space-between; gap: 20rpx; flex-shrink: 0; }
.assignee-title { min-width: 0; color: #263e54; font-size: 30rpx; font-weight: 800; }
.assignee-cancel,.assignee-clear { display: flex; align-items: center; justify-content: center; flex-shrink: 0; margin: 0; padding: 0 10rpx; min-height: 64rpx; border: 0; background: transparent; color: #315f86; font-size: 24rpx; line-height: 1.3; }
.assignee-cancel::after,.assignee-clear::after,.assignee-option::after { border: 0; }
.assignee-search { display: flex; align-items: center; flex-shrink: 0; gap: 12rpx; margin-top: 20rpx; padding: 0 18rpx; border: 1rpx solid #dbe5ed; border-radius: 13rpx; background: #f4f8fa; }
.assignee-search-input { height: 80rpx; min-width: 0; flex: 1; color: #2b3d50; font-size: 26rpx; }
.assignee-count { flex-shrink: 0; padding: 18rpx 0; color: #708296; font-size: 22rpx; }
.assignee-options { height: 0; min-height: 0; flex: 1; }
.assignee-option { box-sizing: border-box; display: flex; width: 100%; min-height: 108rpx; align-items: center; gap: 20rpx; margin: 0 0 10rpx; padding: 18rpx; border: 1rpx solid #e6edf2; border-radius: 12rpx; background: #fff; text-align: left; line-height: 1.4; }
.assignee-option.selected { border-color: #bdd1df; background: #edf5fa; }
.assignee-copy { min-width: 0; flex: 1; }
.assignee-name { display: block; color: #2b4055; font-size: 28rpx; font-weight: 700; overflow-wrap: anywhere; }
.assignee-account { display: block; margin-top: 6rpx; color: #718295; font-size: 22rpx; overflow-wrap: anywhere; }
.assignee-selected { flex-shrink: 0; color: #315f86; font-size: 23rpx; font-weight: 700; }
.assignee-empty { padding: 72rpx 18rpx; color: #6f8193; font-size: 25rpx; text-align: center; }
.assignee-empty text { display: block; margin-bottom: 12rpx; }
.assignee-empty text + text { color: #8c9aaa; font-size: 22rpx; }
</style>
