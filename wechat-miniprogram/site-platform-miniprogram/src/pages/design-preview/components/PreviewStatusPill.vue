<script setup lang="ts">
import type { PreviewTone } from '../previewData';

withDefaults(defineProps<{
  label: string;
  tone?: PreviewTone;
}>(), {
  tone: 'gray'
});
</script>

<template>
  <text class="status-pill" :class="tone">{{ label }}</text>
</template>

<style scoped>
.status-pill {
  display: inline-flex;
  min-height: 42rpx;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  padding: 0 14rpx;
  border-radius: 10rpx;
  font-size: 21rpx;
  font-weight: 700;
  line-height: 1;
  white-space: nowrap;
}

.blue {
  background: #e8f1ff;
  color: #1769d2;
}

.green {
  background: #e5f7f1;
  color: #087d68;
}

.amber {
  background: #fff3d6;
  color: #9a6300;
}

.red {
  background: #fee9e8;
  color: #c43d3d;
}

.gray {
  background: #edf0f3;
  color: #667085;
}
</style>
