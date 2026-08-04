import { getElectricBoxes } from '@/api/electricBox';
import { USE_MOCK } from '@/api/request';
import {
  extractElectricBoxScene,
  extractElectricBoxSceneFromScanResult,
  type WechatScanCodeResult
} from '@/utils/electricBoxScene';
import jsQR from 'jsqr';

let scanStarting = false;

function waitForUiRelease(milliseconds: number) {
  return new Promise<void>((resolve) => setTimeout(resolve, milliseconds));
}

function isWechatDevtools() {
  try {
    return String(uni.getSystemInfoSync().platform || '').toLowerCase() === 'devtools';
  } catch {
    return false;
  }
}

function openScanResult(scene: string) {
  return new Promise<void>((resolve, reject) => {
    uni.navigateTo({
      url: `/pages/scan-entry/index?scene=${encodeURIComponent(scene)}`,
      success: () => resolve(),
      fail: (error) => reject(new Error(error.errMsg || '扫码结果页面打开失败'))
    });
  });
}

function restartWechatDevtoolsScan(scene: string) {
  const wxApi = (globalThis as typeof globalThis & { wx?: any }).wx;
  if (!wxApi?.restartMiniProgram) return openScanResult(scene);
  return new Promise<void>((resolve, reject) => {
    wxApi.restartMiniProgram({
      path: `/pages/scan-entry/index?scene=${encodeURIComponent(scene)}`,
      success: () => resolve(),
      fail: (error: { errMsg?: string }) => reject(new Error(error?.errMsg || '扫码结果页面重新打开失败'))
    });
  });
}

async function startMockElectricBoxScan(projectId: number) {
  const boxes = await getElectricBoxes(projectId || 1);
  const target = boxes.find((item) => item.status === 'ACTIVE' && item.inspectionRequired !== false && item.todayStatus === 'UNCHECKED')
    || boxes.find((item) => item.status === 'ACTIVE' && item.inspectionRequired !== false)
    || boxes.find((item) => item.status === 'ACTIVE')
    || boxes[0];
  if (!target) throw new Error('当前项目暂无可演示电箱');
  await openScanResult(`B:${target.publicCode || target.boxCode}`);
}

function chooseQrImage() {
  return new Promise<string | undefined>((resolve, reject) => {
    uni.chooseImage({
      count: 1,
      sizeType: ['original'],
      sourceType: ['album'],
      success: (result) => resolve(result.tempFilePaths?.[0]),
      fail: (error) => {
        if (String(error.errMsg || '').toLowerCase().includes('cancel')) {
          resolve(undefined);
          return;
        }
        reject(new Error('二维码图片选择失败，请重试'));
      }
    });
  });
}

function getImageInfo(path: string) {
  return new Promise<UniApp.GetImageInfoSuccessData>((resolve, reject) => {
    uni.getImageInfo({
      src: path,
      success: resolve,
      fail: (error) => reject(new Error(error.errMsg || '二维码图片读取失败'))
    });
  });
}

async function decodeQrImage(path: string) {
  const wxApi = (globalThis as typeof globalThis & { wx?: any }).wx;
  if (!wxApi?.createOffscreenCanvas) {
    throw new Error('当前微信开发者工具基础库不支持图片二维码解析，请升级开发者工具后重试');
  }
  const info = await getImageInfo(path);
  const sourceWidth = Math.max(1, Number(info.width || 1));
  const sourceHeight = Math.max(1, Number(info.height || 1));
  const scale = Math.min(1, 1600 / Math.max(sourceWidth, sourceHeight));
  const width = Math.max(1, Math.round(sourceWidth * scale));
  const height = Math.max(1, Math.round(sourceHeight * scale));
  const canvas = wxApi.createOffscreenCanvas({ type: '2d', width, height });
  const context = canvas?.getContext?.('2d');
  const image = canvas?.createImage?.();
  if (!context || !image) throw new Error('二维码图片解析组件初始化失败');
  await new Promise<void>((resolve, reject) => {
    image.onload = () => resolve();
    image.onerror = () => reject(new Error('二维码图片加载失败'));
    image.src = info.path || path;
  });
  context.clearRect(0, 0, width, height);
  context.drawImage(image, 0, 0, width, height);
  const imageData = context.getImageData(0, 0, width, height);
  const result = jsQR(imageData.data, width, height, { inversionAttempts: 'attemptBoth' });
  if (!result?.data) throw new Error('图片中未识别到二维码，请选择清晰完整的电箱二维码');
  return result.data;
}

async function startWechatDevtoolsImageScan() {
  const imagePath = await chooseQrImage();
  if (!imagePath) return;
  const decoded = await decodeQrImage(imagePath);
  const scene = extractElectricBoxScene(decoded);
  if (!scene) throw new Error('所选图片不是有效的电箱巡检二维码');
  // 开发者工具关闭本机文件选择器并完成离屏画布解码后，需要先把渲染线程
  // 交还给模拟器。随后重启到中转页，避免文件选择器留下失活的空白 WebView。
  await waitForUiRelease(450);
  await restartWechatDevtoolsScan(scene);
}

async function runElectricBoxScan(projectId: number) {
  // #ifdef MP-WEIXIN
  if (isWechatDevtools()) {
    await startWechatDevtoolsImageScan();
    return;
  }
  await new Promise<void>((resolve, reject) => {
    const wxApi = (globalThis as typeof globalThis & { wx?: any }).wx;
    if (!wxApi?.scanCode) {
      reject(new Error('微信扫码组件不可用，请退出小程序后重试'));
      return;
    }
    // 使用微信原生接口识别普通二维码和 wxCode。正式电箱码会在
    // success.path 返回业务页面及 scene 参数。
    wxApi.scanCode({
      scanType: ['qrCode', 'wxCode'],
      onlyFromCamera: false,
      success: async (scanResult: WechatScanCodeResult) => {
        const scene = extractElectricBoxSceneFromScanResult(scanResult);
        if (!scene) {
          reject(new Error(String(scanResult.scanType || '').toUpperCase() === 'WX_CODE'
            ? '该小程序码不是电箱巡检码，请扫描电箱台账生成的统一巡检码'
            : '未识别到有效的电箱巡检码'));
          return;
        }
        try {
          await openScanResult(scene);
          resolve();
        } catch (error) {
          reject(error);
        }
      },
      fail: (error: { errMsg?: string }) => {
        if (String(error.errMsg || '').toLowerCase().includes('cancel')) {
          resolve();
          return;
        }
        reject(new Error(`扫码失败：${error.errMsg || '请退出小程序后重试'}`));
      }
    });
  });
  return;
  // #endif

  // #ifndef MP-WEIXIN
  if (USE_MOCK) {
    await startMockElectricBoxScan(projectId);
    return;
  }
  throw new Error('请在微信小程序中使用扫码功能');
  // #endif
}

export async function startElectricBoxScan(projectId = 1) {
  if (scanStarting) return;
  scanStarting = true;
  try {
    await runElectricBoxScan(projectId);
  } finally {
    scanStarting = false;
  }
}

export { extractElectricBoxScene };
