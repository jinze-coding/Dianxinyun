<script setup lang="ts">
import { computed, getCurrentInstance, ref } from 'vue';
import type { PublicProjectLocation } from '@/api/siteAccess';
import {
  isNavigableProjectLocation,
  openProjectMapNavigation,
  type OpenLocationOptions,
  type ProjectMapContext
} from '@/utils/projectMapNavigation';
import { showToast } from '@/utils/navigation';

const props = defineProps<{
  location: PublicProjectLocation;
  projectName: string;
  mapId: string;
  routeImagePath?: string;
  routeImageLoading?: boolean;
  routeImageError?: string;
  embedded?: boolean;
}>();
const emit = defineEmits<{ (event: 'routeImageLoaded'): void }>();

const componentInstance = getCurrentInstance();
const opening = ref(false);
const canNavigate = computed(() => isNavigableProjectLocation(props.location));
const addressText = computed(() => props.location.address?.trim() || '项目地址暂未维护');
const markers = computed(() => {
  if (!canNavigate.value) return [];
  return [{
    id: 1,
    latitude: props.location.latitude as number,
    longitude: props.location.longitude as number,
    title: props.projectName,
    iconPath: '/static/tabbar/project-active.png',
    width: 34,
    height: 34,
    anchor: { x: 0.5, y: 1 },
    callout: {
      content: props.projectName,
      display: 'ALWAYS',
      padding: 6,
      borderRadius: 6,
      color: '#183044',
      bgColor: '#ffffff',
      fontSize: 12
    }
  }];
});

async function navigateToProject() {
  if (!canNavigate.value || opening.value) {
    if (!canNavigate.value) showToast('项目暂未配置导航坐标');
    return;
  }
  opening.value = true;
  try {
    await openProjectMapNavigation(props.location, props.projectName, props.mapId, {
      createMapContext: (mapId) => uni.createMapContext(
        mapId,
        componentInstance?.proxy as unknown
      ) as unknown as ProjectMapContext,
      openLocation: (options: OpenLocationOptions) => {
        uni.openLocation(options as UniNamespace.OpenLocationOptions);
      }
    });
  } catch (error) {
    showToast(error instanceof Error ? error.message : '地图暂时无法打开');
  } finally {
    opening.value = false;
  }
}

function previewRouteImage() {
  if (!props.routeImagePath) return;
  uni.previewImage({
    urls: [props.routeImagePath],
    current: props.routeImagePath
  });
}
</script>

<template>
  <view class="project-location-card" :class="{ 'is-embedded': embedded }">
    <view class="project-location-head">
      <view>
        <text v-if="!embedded" class="project-location-title">访客导航</text>
        <text class="project-location-subtitle">项目地址、附近地标与到访路线参考</text>
      </view>
      <text class="project-location-badge">{{ canNavigate ? 'GCJ-02' : '未定位' }}</text>
    </view>

    <map
      v-if="canNavigate"
      :id="props.mapId"
      class="project-location-map"
      :latitude="props.location.latitude"
      :longitude="props.location.longitude"
      :markers="markers"
      :scale="16"
      :show-location="false"
      @tap="navigateToProject"
      @markertap="navigateToProject"
    />

    <view class="project-location-copy" :class="{ enabled: canNavigate }" @tap="navigateToProject">
      <text class="project-location-pin">⌖</text>
      <text>{{ addressText }}</text>
    </view>

    <view
      v-if="props.location.routeImageAvailable || props.routeImagePath || props.routeImageLoading || props.routeImageError"
      class="project-route-image-section"
    >
      <view class="project-route-image-head">
        <text>附近地标与到访路线图</text>
        <text v-if="props.routeImagePath">点击查看大图</text>
      </view>
      <view v-if="props.routeImageLoading" class="project-route-image-state">正在加载到访路线图...</view>
      <image
        v-else-if="props.routeImagePath"
        class="project-route-image"
        :src="props.routeImagePath"
        mode="widthFix"
        @load="emit('routeImageLoaded')"
        @tap="previewRouteImage"
      />
      <text v-if="props.routeImageError" class="project-route-image-error">{{ props.routeImageError }}</text>
    </view>

    <text class="project-location-warning">地图导航终点来自项目坐标，可能不是实际入口；请结合附近地标、路线图和现场指引到访。</text>
    <text v-if="!canNavigate" class="project-location-disabled">项目暂未配置导航坐标，暂不能打开地图导航</text>
    <button
      class="project-location-button"
      :disabled="!canNavigate || opening"
      @tap.stop="navigateToProject"
    >{{ opening ? '正在打开地图...' : '百度地图优先导航' }}</button>
    <text v-if="canNavigate" class="project-location-hint">将优先调起百度地图；当前设备不支持时自动打开系统地图。</text>
  </view>
</template>

<style scoped>
.project-location-card{overflow:hidden;border:1rpx solid var(--workspace-divider);border-radius:22rpx;padding:24rpx;background:#fff;box-shadow:var(--workspace-shadow)}
.project-location-card.is-embedded{border:0;border-radius:0;padding:0;box-shadow:none}
.project-location-head{display:flex;align-items:flex-start;justify-content:space-between;gap:18rpx}
.project-location-head>view{display:flex;min-width:0;flex-direction:column;gap:6rpx}
.project-location-title{color:var(--workspace-text);font-size:28rpx;font-weight:850}
.project-location-subtitle{color:var(--workspace-text-muted);font-size:19rpx;line-height:1.5}
.project-location-badge{flex-shrink:0;border-radius:999rpx;padding:7rpx 12rpx;background:#edf5fa;color:var(--workspace-accent-deep);font-size:17rpx;font-weight:750}
.project-location-map{width:100%;height:310rpx;margin-top:20rpx;border-radius:16rpx;background:#edf2f5}
.project-location-copy{display:flex;align-items:flex-start;gap:12rpx;margin-top:20rpx;color:var(--workspace-text-secondary);font-size:22rpx;line-height:1.65}
.project-location-copy.enabled{color:var(--workspace-accent-deep)}
.project-location-pin{flex-shrink:0;font-size:28rpx;font-weight:900;line-height:1.25}
.project-route-image-section{margin-top:20rpx;border:1rpx solid var(--workspace-divider);border-radius:16rpx;padding:18rpx;background:#f8fafb}
.project-route-image-head{display:flex;align-items:center;justify-content:space-between;gap:16rpx;margin-bottom:14rpx;color:var(--workspace-text-secondary);font-size:21rpx;font-weight:800}
.project-route-image-head text:last-child{color:var(--workspace-text-muted);font-size:17rpx;font-weight:550}
.project-route-image{display:block;width:100%;border-radius:12rpx;background:#edf2f5}
.project-route-image-state{display:flex;min-height:150rpx;align-items:center;justify-content:center;border-radius:12rpx;background:#edf2f5;color:var(--workspace-text-muted);font-size:20rpx}
.project-route-image-error{display:block;border-radius:12rpx;padding:14rpx;background:#fff4f3;color:#a64d45;font-size:19rpx;line-height:1.55}
.project-location-warning{display:block;margin-top:16rpx;border-radius:12rpx;padding:13rpx 15rpx;background:#fff8e9;color:#80602d;font-size:19rpx;line-height:1.65}
.project-location-disabled{display:block;margin-top:12rpx;border-radius:12rpx;padding:13rpx 15rpx;background:#fff7e9;color:#80602d;font-size:19rpx;line-height:1.6}
.project-location-button{min-height:70rpx;margin-top:20rpx;border-radius:14rpx;background:var(--workspace-accent-deep);color:#fff;font-size:23rpx;font-weight:800}
.project-location-button[disabled]{background:#dfe7ec;color:#8294a1;opacity:1}
.project-location-hint{display:block;margin-top:12rpx;color:var(--workspace-text-muted);font-size:18rpx;line-height:1.55;text-align:center}
</style>
