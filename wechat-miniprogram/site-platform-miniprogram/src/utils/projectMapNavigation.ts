import type { PublicProjectLocation } from '@/api/siteAccess';

interface NavigationCallbackOptions {
  success?: () => void;
  fail?: (error?: unknown) => void;
}

export interface OpenMapAppOptions extends NavigationCallbackOptions {
  destination: string;
  latitude: number;
  longitude: number;
  preferApplication: 'baidu';
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
}

export type ProjectMapNavigationResult = 'baidu-preferred' | 'system-map';

function finiteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

export function isNavigableProjectLocation(
  location?: PublicProjectLocation
): location is PublicProjectLocation & { latitude: number; longitude: number; coordinateType: 'GCJ02' } {
  return Boolean(
    location?.navigable
    && location.coordinateType === 'GCJ02'
    && finiteNumber(location.latitude)
    && location.latitude >= -90
    && location.latitude <= 90
    && finiteNumber(location.longitude)
    && location.longitude >= -180
    && location.longitude <= 180
  );
}

function openSystemMap(
  location: PublicProjectLocation & { latitude: number; longitude: number },
  projectName: string,
  dependencies: ProjectMapNavigationDependencies
): Promise<ProjectMapNavigationResult> {
  return new Promise((resolve, reject) => {
    try {
      dependencies.openLocation({
        latitude: location.latitude,
        longitude: location.longitude,
        name: projectName,
        address: location.address?.trim() || projectName,
        scale: 16,
        success: () => resolve('system-map'),
        fail: (error) => reject(error instanceof Error ? error : new Error('地图暂时无法打开'))
      });
    } catch (error) {
      reject(error);
    }
  });
}

export async function openProjectMapNavigation(
  location: PublicProjectLocation | undefined,
  projectName: string,
  mapId: string,
  dependencies: ProjectMapNavigationDependencies
): Promise<ProjectMapNavigationResult> {
  if (!isNavigableProjectLocation(location)) {
    throw new Error('项目暂未配置导航坐标');
  }

  let context: ProjectMapContext | undefined;
  try {
    context = dependencies.createMapContext(mapId);
  } catch {
    return openSystemMap(location, projectName, dependencies);
  }
  if (!context?.openMapApp) return openSystemMap(location, projectName, dependencies);

  return new Promise((resolve, reject) => {
    let settled = false;
    const fallback = () => {
      if (settled) return;
      settled = true;
      void openSystemMap(location, projectName, dependencies).then(resolve, reject);
    };
    try {
      context.openMapApp?.({
        destination: projectName,
        latitude: location.latitude,
        longitude: location.longitude,
        preferApplication: 'baidu',
        success: () => {
          if (settled) return;
          settled = true;
          resolve('baidu-preferred');
        },
        fail: fallback
      });
    } catch {
      fallback();
    }
  });
}
