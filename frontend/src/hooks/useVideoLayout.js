import { useState, useCallback } from 'react';
import { VIDEO_LAYOUTS } from '@/constants/dicts';

// 视频布局Hook
export function useVideoLayout(initialLayout = 'quad') {
  const [layout, setLayout] = useState(initialLayout);
  const [fullscreenCamId, setFullscreenCamId] = useState(null);

  const currentLayoutConfig = VIDEO_LAYOUTS[layout.toUpperCase()] || VIDEO_LAYOUTS.QUAD;

  const changeLayout = useCallback((newLayout) => {
    setLayout(newLayout);
    setFullscreenCamId(null);
  }, []);

  const enterFullscreen = useCallback((camId) => {
    setFullscreenCamId(camId);
  }, []);

  const exitFullscreen = useCallback(() => {
    setFullscreenCamId(null);
  }, []);

  const getWindowCount = useCallback(() => {
    return currentLayoutConfig.cols * currentLayoutConfig.rows;
  }, [currentLayoutConfig]);

  return {
    layout,
    layoutConfig: currentLayoutConfig,
    fullscreenCamId,
    changeLayout,
    enterFullscreen,
    exitFullscreen,
    getWindowCount,
    isFullscreen: fullscreenCamId !== null,
  };
}

export default useVideoLayout;
