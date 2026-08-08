export interface SealScanCodeResult {
  result?: string;
  path?: string;
}

export const SEAL_SCAN_TYPES: Array<'qrCode' | 'wxCode'> = ['qrCode', 'wxCode'];

function decoded(value: string) {
  try { return decodeURIComponent(value); }
  catch { return ''; }
}

function normalizeScene(value: string) {
  const candidate = decoded(value.trim());
  if (!/^S:[A-Za-z0-9_-]{20,40}$/.test(candidate)) return '';
  return candidate;
}

export function extractSealScene(rawValue: string) {
  const raw = String(rawValue || '').trim();
  if (!raw) return '';
  const direct = normalizeScene(raw);
  if (direct) return direct;
  const value = decoded(raw);
  if (!value) return '';
  const sceneMatch = value.match(/[?&]scene=([^&#]+)/i);
  if (sceneMatch?.[1]) return normalizeScene(sceneMatch[1]);
  const pathMatch = value.match(/pages\/seal\/entry\?(?:[^#]*&)?scene=([^&#]+)/i);
  return pathMatch?.[1] ? normalizeScene(pathMatch[1]) : '';
}

export function extractSealSceneFromScanResult(result: SealScanCodeResult) {
  const path = String(result.path || '').trim();
  if (path) return extractSealScene(path);
  return extractSealScene(String(result.result || ''));
}
