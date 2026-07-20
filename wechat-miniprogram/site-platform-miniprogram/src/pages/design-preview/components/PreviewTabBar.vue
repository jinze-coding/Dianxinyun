<script setup lang="ts">
export type PreviewPageKey = 'overview' | 'personnel' | 'quality' | 'safety' | 'profile';

const props = defineProps<{
  active: PreviewPageKey;
}>();

const emit = defineEmits<{
  (event: 'select', page: PreviewPageKey): void;
}>();

const tabs: Array<{
  key: PreviewPageKey;
  label: string;
  color: string;
  tint: string;
  icon: string;
  activeIcon: string;
}> = [
  { key: 'overview', label: '概况', color: '#527AA3', tint: '#EAF1F7', icon: '/static/design-preview-icons/nav-overview.png', activeIcon: '/static/design-preview-icons/nav-overview-active.png' },
  { key: 'personnel', label: '人员', color: '#2F877B', tint: '#EAF6F2', icon: '/static/design-preview-icons/nav-personnel.png', activeIcon: '/static/design-preview-icons/nav-personnel-active.png' },
  { key: 'quality', label: '质量', color: '#5B68A8', tint: '#EFF1FA', icon: '/static/design-preview-icons/nav-quality.png', activeIcon: '/static/design-preview-icons/nav-quality-active.png' },
  { key: 'safety', label: '安全', color: '#C07A32', tint: '#FFF3E6', icon: '/static/design-preview-icons/nav-safety.png', activeIcon: '/static/design-preview-icons/nav-safety-active.png' },
  { key: 'profile', label: '我的', color: '#61778F', tint: '#EDF2F7', icon: '/static/design-preview-icons/nav-profile.png', activeIcon: '/static/design-preview-icons/nav-profile-active.png' }
];
</script>

<template>
  <view class="preview-tabbar">
    <button
      v-for="tab in tabs"
      :key="tab.key"
      class="preview-tab"
      :class="{ active: props.active === tab.key }"
      :style="props.active === tab.key ? { color: tab.color } : undefined"
      @tap="emit('select', tab.key)"
    >
      <view class="icon-halo" :style="props.active === tab.key ? { backgroundColor: tab.tint } : undefined">
        <image class="tab-icon" :src="props.active === tab.key ? tab.activeIcon : tab.icon" mode="aspectFit" />
      </view>
      <text class="tab-label">{{ tab.label }}</text>
    </button>
  </view>
</template>

<style scoped>
.preview-tabbar {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 40;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  min-height: 116rpx;
  padding: 8rpx 12rpx env(safe-area-inset-bottom);
  border-top: 1rpx solid rgba(148, 163, 184, 0.2);
  background: rgba(255, 255, 255, 0.97);
  box-shadow: 0 -12rpx 34rpx rgba(43, 56, 72, 0.07);
}

.preview-tab {
  display: flex;
  min-width: 0;
  min-height: 104rpx;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 5rpx;
  color: #667085;
  background: transparent;
  transition: color 160ms ease, transform 100ms ease;
}

.preview-tab::after {
  border: 0;
}

.preview-tab:active {
  transform: scale(0.96);
}

.preview-tab.active {
  font-weight: 700;
}

.icon-halo {
  display: flex;
  width: 54rpx;
  height: 48rpx;
  align-items: center;
  justify-content: center;
  border-radius: 999rpx;
  transition: background-color 160ms ease, transform 160ms cubic-bezier(0.2, 0.8, 0.3, 1.2);
}

.active .icon-halo {
  transform: scale(1.06);
}

.tab-icon {
  width: 35rpx;
  height: 35rpx;
}

.tab-label {
  font-size: 21rpx;
  line-height: 1;
}

@media (prefers-reduced-motion: reduce) {
  .preview-tab,
  .icon-halo {
    transition-duration: 1ms;
  }
}
</style>
