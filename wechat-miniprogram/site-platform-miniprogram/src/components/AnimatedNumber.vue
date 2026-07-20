<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';

const props = withDefaults(defineProps<{ value: number; decimals?: number; duration?: number; suffix?: string }>(), { decimals: 0, duration: 240, suffix: '' });
const displayed = ref(0);
let timer: ReturnType<typeof setInterval> | undefined;

watch(() => props.value, (next, previous) => {
  if (timer) clearInterval(timer);
  const start = Number.isFinite(previous) ? Number(previous) : 0;
  const target = Number.isFinite(next) ? Number(next) : 0;
  const startedAt = Date.now();
  timer = setInterval(() => {
    const progress = Math.min((Date.now() - startedAt) / props.duration, 1);
    const eased = 1 - Math.pow(1 - progress, 3);
    displayed.value = start + (target - start) * eased;
    if (progress >= 1 && timer) { clearInterval(timer); timer = undefined; }
  }, 16);
}, { immediate: true });

onBeforeUnmount(() => { if (timer) clearInterval(timer); });
</script>
<template><text>{{ displayed.toFixed(decimals) }}{{ suffix }}</text></template>
