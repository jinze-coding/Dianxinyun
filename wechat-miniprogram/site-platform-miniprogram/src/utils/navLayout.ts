import { computed } from 'vue';

export interface NavLayoutMetrics {
  statusBarHeight: number;
  navHeight: number;
  navTotalHeight: number;
  rightWidth: number;
  windowHeight: number;
  windowWidth: number;
  safeBottom: number;
  rpxToPx: (value: number) => number;
}

export interface PageScrollOptions {
  extraRpx?: number;
  bottomRpx?: number;
  minHeight?: number;
  includeSafeBottom?: boolean;
}

export function getNavLayoutMetrics(): NavLayoutMetrics {
  const fallbackWindowWidth = 375;
  const fallback = {
    statusBarHeight: 24,
    navHeight: 48,
    rightWidth: 96,
    windowHeight: 667,
    windowWidth: fallbackWindowWidth,
    safeBottom: 0
  };

  try {
    const systemInfo = uni.getSystemInfoSync();
    const windowWidth = systemInfo.windowWidth || systemInfo.screenWidth || fallback.windowWidth;
    let windowHeight = systemInfo.windowHeight || systemInfo.screenHeight || fallback.windowHeight;
    // #ifdef H5
    if (typeof window !== 'undefined' && window.innerHeight) windowHeight = window.innerHeight;
    // #endif
    const statusBarHeight = systemInfo.statusBarHeight || fallback.statusBarHeight;
    let safeBottom = systemInfo.safeArea && systemInfo.screenHeight
      ? Math.max(systemInfo.screenHeight - systemInfo.safeArea.bottom, 0)
      : fallback.safeBottom;
    // H5 的 windowHeight 已是可视区域高度，自定义底栏也会自行适配 CSS 安全区。
    // 再使用 uni 返回的 safeArea 会重复扣减浏览器底部区域，造成列表被裁切。
    // #ifdef H5
    safeBottom = 0;
    // #endif
    const menuButton = typeof uni.getMenuButtonBoundingClientRect === 'function'
      ? uni.getMenuButtonBoundingClientRect()
      : undefined;

    if (!menuButton || !menuButton.width || !menuButton.height) {
      return {
        ...fallback,
        statusBarHeight,
        navTotalHeight: statusBarHeight + fallback.navHeight,
        windowHeight,
        windowWidth,
        safeBottom,
        rpxToPx: (value: number) => value * windowWidth / 750
      };
    }

    const topGap = Math.max(menuButton.top - statusBarHeight, 6);
    const navHeight = menuButton.height + topGap * 2;
    return {
      statusBarHeight,
      navHeight,
      navTotalHeight: statusBarHeight + navHeight,
      rightWidth: Math.max(windowWidth - menuButton.left + 8, fallback.rightWidth),
      windowHeight,
      windowWidth,
      safeBottom,
      rpxToPx: (value: number) => value * windowWidth / 750
    };
  } catch (error) {
    return {
      ...fallback,
      navTotalHeight: fallback.statusBarHeight + fallback.navHeight,
      rpxToPx: (value: number) => value * fallbackWindowWidth / 750
    };
  }
}

export function usePageScrollHeight(options: PageScrollOptions = {}) {
  const metrics = getNavLayoutMetrics();
  const scrollHeight = computed(() => {
    const extraHeight = metrics.rpxToPx((options.extraRpx || 0) + (options.bottomRpx || 0));
    const safeBottom = options.includeSafeBottom === false ? 0 : metrics.safeBottom;
    return Math.max(options.minHeight || 180, metrics.windowHeight - metrics.navTotalHeight - extraHeight - safeBottom);
  });
  const scrollStyle = computed(() => `height: ${scrollHeight.value}px;`);

  return {
    metrics,
    scrollHeight,
    scrollStyle
  };
}
