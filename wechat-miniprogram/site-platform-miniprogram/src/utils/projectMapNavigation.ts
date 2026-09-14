import type { PublicProjectLocation } from '@/api/siteAccess';

export type ProjectNavigationProvider = 'tencent' | 'amap' | 'baidu' | 'apple' | 'system';
export function projectMapChoices(platform?: string) {
  const fourth = platform?.toLowerCase() === 'ios' ? 'apple' : 'system';
  return [
    { provider: 'tencent', label: '腾讯地图' },
    { provider: 'amap', label: '高德地图' },
    { provider: 'baidu', label: '百度地图' },
    { provider: fourth, label: fourth === 'apple' ? '苹果地图' : '系统地图' }
  ] as { provider: ProjectNavigationProvider; label: string }[];
}

interface NavigationCallbackOptions {
  success?: (result?: unknown) => void;
  fail?: (error?: unknown) => void;
}
export interface OpenMapAppOptions extends NavigationCallbackOptions {
  destination: string;
  latitude: number;
  longitude: number;
  preferApplication: Exclude<ProjectNavigationProvider, 'system'>;
}
export interface OpenLocationOptions extends NavigationCallbackOptions {
  latitude: number;
  longitude: number;
  name: string;
  address: string;
  scale: number;
}
export interface ProjectMapContext {
  openMapApp?: (options: OpenMapAppOptions) => void;
}
export interface ProjectMapNavigationDependencies {
  createMapContext: (mapId: string) => ProjectMapContext | undefined;
  openLocation: (options: OpenLocationOptions) => void;
  confirmSystemMapFallback: () => Promise<boolean>;
  isActive: () => boolean;
}
export type ProjectMapNavigationResult = 'preferred-map' | 'system-map' | 'cancelled';

function finiteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}
export function isNavigableProjectLocation(
  location?: PublicProjectLocation
): location is PublicProjectLocation & { latitude: number; longitude: number; coordinateType: 'GCJ02' } {
  return Boolean(
    location?.navigable && location.coordinateType === 'GCJ02'
    && finiteNumber(location.latitude) && location.latitude >= -90 && location.latitude <= 90
    && finiteNumber(location.longitude) && location.longitude >= -180 && location.longitude <= 180
  );
}
function isCancellation(value: unknown): boolean {
  if (!value || typeof value !== 'object') return /cancel|取消/i.test(String(value || ''));
  const result = value as { cancel?: boolean; errMsg?: string; message?: string };
  return result.cancel === true || /cancel|取消/i.test(result.errMsg || result.message || '');
}

// Native callbacks can arrive more than once; only the first outcome may trigger follow-up UI.
function invokeMap(call: (options: NavigationCallbackOptions) => void): Promise<'opened' | 'cancelled'> {
  return new Promise((resolve, reject) => {
    let settled = false;
    const finish = (failed: boolean, result?: unknown) => {
      if (settled) return;
      settled = true;
      if (isCancellation(result)) resolve('cancelled');
      else if (failed) reject(new Error('地图暂时无法打开，请选择其他地图重试'));
      else resolve('opened');
    };
    try { call({ success: result => finish(false, result), fail: error => finish(true, error) }); }
    catch (error) { finish(true, error); }
  });
}

export async function openProjectMapNavigation(
  location: PublicProjectLocation | undefined,
  projectName: string,
  mapId: string,
  provider: ProjectNavigationProvider,
  dependencies: ProjectMapNavigationDependencies
): Promise<ProjectMapNavigationResult> {
  if (!dependencies.isActive()) return 'cancelled';
  if (!isNavigableProjectLocation(location)) throw new Error('项目暂未配置导航坐标');
  const openSystemMap = async (): Promise<ProjectMapNavigationResult> => {
    if (!dependencies.isActive()) return 'cancelled';
    const result = await invokeMap(callbacks => dependencies.openLocation({
      latitude: location.latitude, longitude: location.longitude,
      name: projectName, address: location.address?.trim() || projectName, scale: 16, ...callbacks
    }));
    return result === 'cancelled' ? result : 'system-map';
  };
  if (provider === 'system') return openSystemMap();
  try {
    const context = dependencies.createMapContext(mapId);
    if (!context?.openMapApp) throw new Error('当前微信不支持指定地图');
    const result = await invokeMap(callbacks => context.openMapApp!({
      destination: projectName, latitude: location.latitude, longitude: location.longitude,
      preferApplication: provider, ...callbacks
    }));
    return result === 'cancelled' ? result : 'preferred-map';
  } catch {
    if (!dependencies.isActive()) return 'cancelled';
    const confirmed = await dependencies.confirmSystemMapFallback();
    if (!confirmed || !dependencies.isActive()) return 'cancelled';
    return openSystemMap();
  }
}
