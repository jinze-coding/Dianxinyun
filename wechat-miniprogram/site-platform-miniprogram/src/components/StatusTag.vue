<script setup lang="ts">
defineProps<{
  label: string;
  tone?: 'green' | 'blue' | 'amber' | 'red' | 'gray';
}>();
</script>

<template>
  <text class="tag" :class="tone || 'gray'">{{ label }}</text>
</template>

<style scoped>
.tag {
  display: inline-flex;
  min-height: 36rpx;
  align-items: center;
  padding: 0 12rpx;
  border-radius: 12rpx;
  font-size: 20rpx;
  font-weight: 800;
  white-space: nowrap;
  transition: background 0.2s ease, color 0.2s ease;
}

.green {
  background: #e6fbf6;
  color: #0f9f8f;
}

.blue {
  background: #e7f1ff;
  color: #1677ff;
}

.amber {
  background: #fff4d8;
  color: #b7791f;
}

.red {
  background: #ffe8e8;
  color: #d14343;
}

.gray {
  background: #edf2f8;
  color: #5f7187;
}
</style>
