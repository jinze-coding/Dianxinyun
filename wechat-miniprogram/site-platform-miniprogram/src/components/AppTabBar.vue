<script setup lang="ts">
import { computed } from 'vue';
import { WORKSPACE_THEME } from '@/constants/workspaceTheme';
import { switchTab } from '@/utils/navigation';

export type WorkspaceTabKey = 'documents' | 'inspection' | 'quality' | 'profile';
type CompatibleTabKey = WorkspaceTabKey | 'overview' | 'personnel' | 'safety' | 'scan' | 'project' | 'todo';

const props = defineProps<{
  active: CompatibleTabKey;
}>();

const tabs: Array<{
  key: WorkspaceTabKey;
  label: string;
  url: string;
  color: string;
  tint: string;
  icon: string;
  activeIcon: string;
}> = [
  { key: 'documents', label: '资料', url: '/pages/documents/index', color: WORKSPACE_THEME.accentDeep, tint: WORKSPACE_THEME.tint, icon: '/static/design-preview-icons/quality-files.png', activeIcon: '/static/design-preview-icons/quality-files.png' },
  { key: 'inspection', label: '巡检', url: '/pages/inspection/index', color: WORKSPACE_THEME.accentDeep, tint: WORKSPACE_THEME.tint, icon: '/static/design-preview-icons/nav-safety.png', activeIcon: '/static/design-preview-icons/nav-safety-active.png' },
  { key: 'quality', label: '质量', url: '/pages/quality/index', color: WORKSPACE_THEME.accentDeep, tint: WORKSPACE_THEME.tint, icon: '/static/design-preview-icons/nav-quality.png', activeIcon: '/static/design-preview-icons/nav-quality-active.png' },
  { key: 'profile', label: '我的', url: '/pages/profile/index', color: WORKSPACE_THEME.accentDeep, tint: WORKSPACE_THEME.tint, icon: '/static/design-preview-icons/nav-profile.png', activeIcon: '/static/design-preview-icons/nav-profile-active.png' }
];

const canonicalActive = computed<WorkspaceTabKey>(() => {
  return tabs.some((tab) => tab.key === props.active) ? props.active as WorkspaceTabKey : 'inspection';
});

function openTab(tab: (typeof tabs)[number]) {
  if (canonicalActive.value === tab.key) return;
  switchTab(tab.url);
}
</script>

<template>
  <view class="workspace-tabbar">
    <button
      v-for="tab in tabs"
      :key="tab.key"
      class="workspace-tab"
      :class="{ active: canonicalActive === tab.key }"
      :style="canonicalActive === tab.key ? { color: tab.color } : undefined"
      @tap="openTab(tab)"
    >
      <view class="tab-halo" :style="canonicalActive === tab.key ? { backgroundColor: tab.tint } : undefined">
        <image class="tab-icon" :src="canonicalActive === tab.key ? tab.activeIcon : tab.icon" mode="aspectFit" />
      </view>
      <text class="tab-label">{{ tab.label }}</text>
    </button>
  </view>
</template>

<style scoped>
.workspace-tabbar {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 40;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  min-height: 116rpx;
  padding: 8rpx 12rpx env(safe-area-inset-bottom);
  border-top: 1rpx solid rgba(148, 163, 184, 0.2);
  background: rgba(255, 255, 255, 0.97);
  box-shadow: 0 -12rpx 34rpx rgba(43, 56, 72, 0.07);
}

.workspace-tab {
  display: flex;
  min-width: 0;
  min-height: 104rpx;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 5rpx;
  background: transparent;
  color: #6f7d8d;
  transition: color 160ms ease, transform 100ms ease;
}

.workspace-tab::after {
  border: 0;
}

.workspace-tab:active {
  transform: scale(0.96);
}

.workspace-tab.active {
  font-weight: 700;
}

.tab-halo {
  display: flex;
  width: 54rpx;
  height: 48rpx;
  align-items: center;
  justify-content: center;
  border-radius: 999rpx;
  transition: background-color 160ms ease, transform 160ms cubic-bezier(0.2, 0.8, 0.3, 1.2);
}

.active .tab-halo {
  transform: scale(1.06);
}

.tab-icon {
  width: 35rpx;
  height: 35rpx;
}

.workspace-tab .tab-icon { opacity: .7; filter: grayscale(.9) saturate(.25) brightness(.9); }
.workspace-tab.active .tab-icon { opacity: 1; filter: grayscale(.8) sepia(.22) hue-rotate(158deg) saturate(1.35) brightness(.78); }

.tab-label {
  font-size: 22rpx;
  line-height: 1;
}

@media (prefers-reduced-motion: reduce) {
  .workspace-tab,
  .tab-halo {
    transition-duration: 1ms;
  }
}
</style>
