export interface WechatScanCodeResult {
  result?: string;
  path?: string;
  scanType?: string;
}

export function extractElectricBoxScene(rawValue: string) {
  const raw = (rawValue || '').trim();
  if (!raw) return '';
  try {
    const decoded = decodeURIComponent(raw);
    if (/^B:/i.test(decoded)) return decoded;

    const sceneMatch = decoded.match(/[?&]scene=([^&#]+)/i);
    if (sceneMatch?.[1]) {
      const scene = decodeURIComponent(sceneMatch[1]);
      return /^B:/i.test(scene) ? scene : `B:${scene}`;
    }

    const publicCodeMatch = decoded.match(/[?&]publicCode=([^&#]+)/i);
    if (publicCodeMatch?.[1]) return `B:${decodeURIComponent(publicCodeMatch[1])}`;

    const pageMatch = decoded.match(/pages\/scan-entry\/index\?(?:[^#]*&)?scene=([^&#]+)/i);
    if (pageMatch?.[1]) {
      const scene = decodeURIComponent(pageMatch[1]);
      return /^B:/i.test(scene) ? scene : `B:${scene}`;
    }

    const publicPathMatch = decoded.match(/\/public\/electric-boxes\/([^/?#]+)\/(?:summary|monthly-records)/i);
    if (publicPathMatch?.[1]) return `B:${decodeURIComponent(publicPathMatch[1])}`;

    if (/^PUB[-_]/i.test(decoded)) return `B:${decoded}`;
  } catch (error) {
    console.warn('巡检码解析失败', error);
  }
  return '';
}

export function extractElectricBoxSceneFromScanResult(scanResult: WechatScanCodeResult) {
  // 微信小程序码（scanType=WX_CODE）的业务参数位于 path。result 可能同时
  // 存在但不可信，因此 path 一旦存在就不能再回退到 result。
  const path = String(scanResult.path || '').trim();
  if (path) return extractElectricBoxScene(path);
  return extractElectricBoxScene(String(scanResult.result || ''));
}
