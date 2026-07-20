let loadPromise = null;

export function loadBaiduMap() {
  if (loadPromise) return loadPromise;

  const ak = import.meta.env.VITE_BAIDU_MAP_AK;
  const placeholderAks = ['YOUR_BAIDU_MAP_AK', 'REPLACE_WITH_YOUR_BAIDU_MAP_AK', '请填写你的百度地图浏览器端AK'];
  if (!ak || placeholderAks.includes(ak)) {
    loadPromise = Promise.reject(new Error('百度地图 AK 未配置，请在 .env 中设置 VITE_BAIDU_MAP_AK'));
    return loadPromise;
  }

  loadPromise = new Promise((resolve, reject) => {
    if (window.BMapGL) {
      resolve(window.BMapGL);
      return;
    }

    window.baiduMapLoadCallback = () => {
      if (window.BMapGL) {
        resolve(window.BMapGL);
      } else {
        loadPromise = null;
        reject(new Error('百度地图加载失败'));
      }
      delete window.baiduMapLoadCallback;
    };

    const script = document.createElement('script');
    script.src = `https://api.map.baidu.com/api?type=webgl&v=1.0&ak=${encodeURIComponent(ak)}&callback=baiduMapLoadCallback`;
    script.async = true;
    script.onerror = () => {
      loadPromise = null;
      delete window.baiduMapLoadCallback;
      reject(new Error('百度地图脚本加载失败'));
    };
    document.head.appendChild(script);
  });

  return loadPromise;
}
