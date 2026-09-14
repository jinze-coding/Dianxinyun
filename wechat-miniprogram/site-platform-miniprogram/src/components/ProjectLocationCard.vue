<script setup lang="ts">
import { computed, getCurrentInstance, onBeforeUnmount, ref, watch } from 'vue';
import type { PublicProjectLocation } from '@/api/siteAccess';
import {
  isNavigableProjectLocation, openProjectMapNavigation, projectMapChoices,
  type OpenLocationOptions, type ProjectMapContext, type ProjectNavigationProvider
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
const emit = defineEmits<{
  (event: 'routeImageLoaded'): void;
  (event: 'retryRouteImage'): void;
}>();
const componentInstance = getCurrentInstance();
const opening = ref(false);
let disposed = false;
onBeforeUnmount(() => { disposed = true; });
const canNavigate = computed(() => isNavigableProjectLocation(props.location));
const addressText = computed(() => props.location.address?.trim() || '项目地址暂未维护');
let platform = '';
try { platform = uni.getSystemInfoSync().platform; } catch { /* Unknown devices use system maps. */ }
const mapChoices = projectMapChoices(platform);
const imageDecodeError = ref(false);
watch(() => [props.routeImagePath, props.routeImageLoading], () => { imageDecodeError.value = false; });
const imageError = computed(() => props.routeImageError || (imageDecodeError.value ? '路线图显示失败，请重试' : ''));

async function navigateToProject(provider: ProjectNavigationProvider) {
  if (!canNavigate.value || opening.value || disposed) return;
  opening.value = true;
  const location = { ...props.location };
  const isActive = () => !disposed && canNavigate.value
    && props.location.latitude === location.latitude && props.location.longitude === location.longitude;
  try {
    await openProjectMapNavigation(location, props.projectName, props.mapId, provider, {
      // DevTools exposes an openMapApp stub that never invokes callbacks. It cannot launch phone map apps.
      createMapContext: mapId => platform === 'devtools' ? undefined
        : uni.createMapContext(mapId, componentInstance?.proxy as unknown) as unknown as ProjectMapContext,
      openLocation: (options: OpenLocationOptions) => uni.openLocation(options as UniNamespace.OpenLocationOptions),
      isActive,
      confirmSystemMapFallback: () => new Promise(resolve => {
        uni.showModal({
          title: '暂时无法打开所选地图',
          content: '可返回改选其他地图，或打开系统地图继续导航。',
          confirmText: '系统地图', cancelText: '返回改选',
          success: result => resolve(result.confirm === true), fail: () => resolve(false)
        });
      })
    });
  } catch (error) {
    if (isActive()) showToast(error instanceof Error ? error.message : '地图暂时无法打开，请重试');
  } finally { opening.value = false; }
}
function retryRouteImage() {
  if (disposed || props.routeImageLoading) return;
  emit('retryRouteImage');
}
function previewRouteImage() {
  if (!props.routeImagePath || imageError.value || props.routeImageLoading || disposed) return;
  uni.previewImage({ urls: [props.routeImagePath], current: props.routeImagePath });
}
</script>

<template>
  <view class="project-location-card" :class="{ 'is-embedded': embedded }">
    <text v-if="!embedded" class="project-location-title">访客导航</text>
    <!-- Only provides the WeChat MapContext. The parent mounts this card after foreground invitation validation. -->
    <map v-if="canNavigate" :id="props.mapId" class="project-map-bridge" aria-hidden="true"
      :latitude="props.location.latitude" :longitude="props.location.longitude" :show-location="false"
      :enable-scroll="false" :enable-zoom="false" :enable-rotate="false" />
    <view class="project-location-copy">
      <image class="project-location-pin" src="/static/navigation/location.png" mode="aspectFit" />
      <text>{{ addressText }}</text>
    </view>
    <view class="project-route-image-section">
      <view class="project-route-image-head">
        <text class="project-route-image-title">到访路线图</text>
        <text v-if="props.routeImagePath && !imageError && !props.routeImageLoading" class="project-route-image-hint">点击查看大图</text>
      </view>
      <view v-if="props.routeImageLoading" class="project-route-image-state">正在加载路线图…</view>
      <view v-else-if="imageError" class="project-route-image-error">
        <text>{{ imageError }}</text>
        <button class="project-route-retry" @tap="retryRouteImage">重新加载</button>
      </view>
      <image v-else-if="props.routeImagePath" class="project-route-image" :src="props.routeImagePath" mode="widthFix"
        @load="emit('routeImageLoaded')" @error="imageDecodeError = true" @tap="previewRouteImage" />
      <view v-else class="project-route-image-state">{{ props.location.routeImageAvailable ? '路线图暂未加载' : '暂未上传到访路线图' }}
        <button v-if="props.location.routeImageAvailable" class="project-route-retry" @tap="retryRouteImage">加载路线图</button>
      </view>
    </view>
    <text class="project-location-warning">导航终点可能不是实际入口，请结合路线图及现场指引到访。</text>
    <view class="project-map-heading">选择导航地图</view>
    <text v-if="!canNavigate" class="project-location-disabled">项目暂未配置有效导航坐标，暂不能打开地图；仍可查看路线图。</text>
    <view class="project-map-choices">
      <button v-for="choice in mapChoices" :key="choice.provider" class="project-map-choice"
        :disabled="!canNavigate || opening" @tap.stop="navigateToProject(choice.provider)">
        <image class="project-map-icon" :src="`/static/navigation/${choice.provider}.png`" mode="aspectFit" />
        <text>{{ choice.label }}</text><text class="project-map-arrow">›</text>
      </button>
    </view>
    <text v-if="canNavigate" class="project-location-hint">{{ opening ? '正在打开地图…' : '请选择常用地图，微信可能会显示地图选择面板。' }}</text>
  </view>
</template>

<style scoped>
.project-location-card{position:relative;overflow:hidden;border:1rpx solid var(--workspace-divider);border-radius:22rpx;padding:24rpx;background:#fff;box-shadow:var(--workspace-shadow)}
.project-location-card.is-embedded{border:0;border-radius:0;padding:0;box-shadow:none}
.project-location-title{display:block;margin-bottom:16rpx;color:var(--workspace-text);font-size:28rpx;font-weight:800}
.project-map-bridge{position:absolute;left:0;top:0;width:1px;height:1px;opacity:0;pointer-events:none;z-index:-1}
.project-location-copy{display:flex;align-items:flex-start;gap:12rpx;color:#315f86;font-size:26rpx;line-height:1.6;word-break:break-all}
.project-location-pin{flex-shrink:0;width:34rpx;height:38rpx;margin-top:2rpx}
.project-route-image-section{margin-top:22rpx;border:1rpx solid #e1e9f0;border-radius:16rpx;padding:18rpx;background:#f8fafc}
.project-route-image-head{display:flex;align-items:center;justify-content:space-between;gap:12rpx;margin-bottom:14rpx}
.project-route-image-title{color:#314a60;font-size:25rpx;font-weight:700}
.project-route-image-hint{color:#61768b;font-size:21rpx}
.project-route-image{display:block;width:100%;border-radius:12rpx;background:#edf2f5}
.project-route-image-state{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:8rpx;padding:20rpx 8rpx;color:#687c8d;font-size:23rpx;line-height:1.6}
.project-route-image-error{display:flex;flex-direction:column;align-items:center;gap:8rpx;color:#a64d45;font-size:23rpx;line-height:1.6}
.project-route-retry{min-height:44px;display:flex;align-items:center;justify-content:center;margin:4rpx 0 0;padding:0 24rpx;border-radius:12rpx;background:#e5efff;color:#1759ad;font-size:24rpx}
.project-route-retry::after{border:0}
.project-location-warning{display:block;margin-top:18rpx;padding:14rpx 16rpx;border-radius:12rpx;background:#fff8e9;color:#80602d;font-size:22rpx;line-height:1.6}
.project-map-heading{margin-top:24rpx;margin-bottom:16rpx;color:#263e54;font-size:27rpx;font-weight:700}
.project-map-choices{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16rpx}
.project-map-choice{display:flex;align-items:center;gap:12rpx;min-width:0;width:100%;min-height:52px;padding:16rpx 14rpx;margin:0;border-radius:14rpx;background:#f1f6ff;color:#213c5a;font-size:26rpx;line-height:1.5;font-weight:700}
.project-map-choice::after{border:1rpx solid #d5e3f6;border-radius:14rpx}
.project-map-icon{width:48rpx;height:48rpx;flex-shrink:0}
.project-map-arrow{margin-left:auto;color:#49709c;font-size:32rpx}
.project-map-choice[disabled]{opacity:.5;color:#697d91;background:#f0f3f6}
.project-location-disabled{display:block;margin-bottom:16rpx;color:#80602d;font-size:23rpx;line-height:1.6}
.project-location-hint{display:block;margin-top:16rpx;color:#687c8d;font-size:21rpx;line-height:1.6}
</style>
