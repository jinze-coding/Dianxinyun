<script setup lang="ts">
withDefaults(defineProps<{
  label: string;
  tone?: 'green' | 'amber' | 'red' | 'blue' | 'gray';
}>(), { tone: 'gray' });
</script>

<template>
  <text class="status-pill" :class="tone">{{ label }}</text>
</template>

<style scoped>
.status-pill {
  display: inline-flex;
  min-height: 36rpx;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  padding: 0 12rpx;
  border-radius: 999rpx;
  background: #eef1f4;
  color: #697586;
  font-size: 19rpx;
  font-weight: 700;
  line-height: 1;
}

.green { background: #e9f7f1; color: #2e8069; }
.amber { background: #fff3df; color: #a96822; }
.red { background: #fdeceb; color: #b94f4f; }
.blue { background: #eaf1f7; color: #527aa3; }
.gray { background: #eef1f4; color: #697586; }
</style>
