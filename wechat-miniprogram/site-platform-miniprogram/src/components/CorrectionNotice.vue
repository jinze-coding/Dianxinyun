<script setup lang="ts">
import { computed } from 'vue';
const props = defineProps<{ record?: unknown }>();
const message = computed(() => {
  const value = props.record as { correctionNotice?: string; document?: { correctionNotice?: string }; task?: { correctionNotice?: string } } | undefined;
  return value?.correctionNotice || value?.document?.correctionNotice || value?.task?.correctionNotice || '';
});
</script>
<template><view v-if="message" class="correction-notice">{{ message }}</view></template>
<style scoped>.correction-notice{margin:16rpx 0;padding:20rpx;border:1rpx solid #bfdbfe;border-radius:12rpx;background:#eff6ff;color:#1e40af;font-size:24rpx;line-height:1.6}</style>
