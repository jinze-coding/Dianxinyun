import { API_BASE_URL, request } from './request';

export type SiteVisitStatus = 'PENDING' | 'SUBMITTED' | 'EXPIRED' | 'VOIDED';

export interface PublicProjectLocation {
  address?: string;
  navigable: boolean;
  longitude?: number;
  latitude?: number;
  coordinateType?: 'GCJ02';
  routeImageAvailable?: boolean;
}

export interface PublicSiteVisitInvitation {
  inviteNo: string;
  status: SiteVisitStatus;
  projectName: string;
  projectShortName?: string;
  visitStartTime: string;
  visitEndTime: string;
  purpose: string;
  visitLocation: string;
  hostName: string;
  hostPhone?: string;
  visitorCompany?: string;
  contactName?: string;
  visitorCount?: number;
  travelMode?: 'DRIVING' | 'OTHER';
  vehiclePlate?: string;
  submittedTime?: string;
  serverTime: string;
  projectLocation?: PublicProjectLocation;
}

export interface SiteVisitCompanionInput {
  personCompany: string;
  personName: string;
  personPhone: string;
}

export interface PublicSiteVisitSubmitPayload {
  inviteToken: string;
  visitorCompany: string;
  contactName: string;
  contactPhone: string;
  companions: SiteVisitCompanionInput[];
  travelMode: 'DRIVING' | 'OTHER';
  vehiclePlate?: string;
  visitorRemark?: string;
  privacyAgreed: boolean;
  profileAction?: 'NONE' | 'CREATE' | 'UPDATE';
  profileCode?: string;
  profileName?: string;
  profileRetentionAgreed?: boolean;
  profileVersion?: number;
}

export interface PublicVisitorSession {
  visitorSessionToken: string;
  expiresInSeconds: number;
}

export interface PublicGuardVisitPass {
  registrationNo: string;
  status: 'REGISTERED' | 'EXPIRED';
  projectName: string;
  projectShortName?: string;
  visitorCompany: string;
  contactName: string;
  visitorCount: number;
  travelMode: 'DRIVING' | 'OTHER';
  vehiclePlate?: string;
  registeredTime: string;
  validUntil: string;
  serverTime: string;
}

export interface PublicGuardVisitorSession extends PublicVisitorSession {
  pageState: 'FORM' | 'REGISTERED';
  projectName: string;
  projectShortName?: string;
  registration?: PublicGuardVisitPass;
}

export type PublicGuardVisitSubmitPayload = Omit<PublicSiteVisitSubmitPayload, 'inviteToken'>;

export interface SiteVisitorProfilePerson {
  personType: 'CONTACT' | 'COMPANION';
  personCompany?: string;
  personName?: string;
  personPhone?: string;
  sortOrder: number;
}

export interface PublicProjectProfileImage {
  imageIndex: number;
  mimeType: string;
  cover: boolean;
}

export interface PublicProjectProfile {
  projectName: string;
  shortName?: string;
  phase?: string;
  address?: string;
  engineeringType?: string;
  startDate?: string;
  endDate?: string;
  actualStartDate?: string;
  actualEndDate?: string;
  description?: string;
  directCompany?: string;
  ownerUnit?: string;
  supervisionUnit?: string;
  designUnit?: string;
  contractor?: string;
  buildingArea?: number;
  landArea?: number;
  buildingHeight?: number;
  excavationDepth?: number;
  undergroundFloorCount?: number;
  abovegroundFloorCount?: number;
  projectScale?: string;
  projectClassification?: string;
  projectLevel?: string;
  projectTarget?: string;
  qualityGoal?: string;
  safetyGoal?: string;
  greenConstructionGoal?: string;
  images: PublicProjectProfileImage[];
}

export interface SiteVisitorProfile {
  id?: number;
  profileCode: string;
  profileName: string;
  visitorCompany: string;
  contactName: string;
  contactPhone?: string;
  maskedContactPhone?: string;
  visitorCount: number;
  travelMode: 'DRIVING' | 'OTHER';
  vehiclePlate?: string;
  status: 'ACTIVE' | 'DISABLED';
  version: number;
  lastUsedTime?: string;
  people?: SiteVisitorProfilePerson[];
}

export function resolvePublicSiteVisit(inviteToken: string) {
  return request<PublicSiteVisitInvitation>('/public/site-access/invitations/resolve', {
    method: 'POST',
    data: { inviteToken },
    skipAuthRedirect: true
  });
}

export function getPublicProjectProfile(inviteToken: string) {
  return request<PublicProjectProfile>('/public/site-access/project-profile', {
    method: 'POST',
    data: { inviteToken },
    skipAuthRedirect: true
  });
}

export function getPublicGuardProjectProfile(sceneToken: string) {
  return request<PublicProjectProfile>('/public/site-access/guard/project-profile', {
    method: 'POST',
    data: { sceneToken },
    skipAuthRedirect: true
  });
}

function decodeArrayBuffer(value: ArrayBuffer): string {
  if (typeof TextDecoder !== 'undefined') return new TextDecoder('utf-8').decode(value);
  const bytes = new Uint8Array(value);
  let result = '';
  for (let offset = 0; offset < bytes.length; offset += 8192) {
    result += String.fromCharCode(...bytes.subarray(offset, Math.min(offset + 8192, bytes.length)));
  }
  try { return decodeURIComponent(escape(result)); }
  catch { return result; }
}

function extensionForMimeType(mimeType?: string) {
  if (mimeType === 'image/png') return 'png';
  if (mimeType === 'image/webp') return 'webp';
  return 'jpg';
}

function downloadProjectProfileImage(
  path: string,
  requestData: Record<string, unknown>,
  imageIndex: number,
  mimeType?: string
): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${API_BASE_URL}${path}`,
      method: 'POST',
      data: { ...requestData, imageIndex },
      header: { 'Content-Type': 'application/json' },
      responseType: 'arraybuffer',
      success: (response) => {
        const data = response.data as ArrayBuffer;
        if (response.statusCode >= 200 && response.statusCode < 300 && data instanceof ArrayBuffer) {
          // #ifdef MP-WEIXIN
          const wxApi = (globalThis as typeof globalThis & { wx?: any }).wx;
          const fs = wxApi?.getFileSystemManager?.();
          const root = wxApi?.env?.USER_DATA_PATH;
          if (!fs || !root) {
            reject(new Error('当前微信环境无法保存项目效果图'));
            return;
          }
          const filePath = `${root}/public-project-${Date.now()}-${Math.random().toString(36).slice(2)}.${extensionForMimeType(mimeType)}`;
          fs.writeFile({
            filePath,
            data,
            success: () => resolve(filePath),
            fail: () => reject(new Error('项目效果图保存失败'))
          });
          return;
          // #endif
          // #ifndef MP-WEIXIN
          reject(new Error('项目效果图仅支持微信小程序查看'));
          return;
          // #endif
        }
        try {
          const result = JSON.parse(decodeArrayBuffer(data)) as { message?: string };
          reject(new Error(result?.message || `项目效果图加载失败（${response.statusCode}）`));
        } catch {
          reject(new Error(`项目效果图加载失败（${response.statusCode}）`));
        }
      },
      fail: () => reject(new Error('项目效果图加载失败，请检查网络'))
    });
  });
}

export function downloadPublicProjectProfileImage(
  inviteToken: string,
  imageIndex: number,
  mimeType?: string
) {
  return downloadProjectProfileImage(
    '/public/site-access/project-profile/images', { inviteToken }, imageIndex, mimeType
  );
}

export function downloadPublicGuardProjectProfileImage(
  sceneToken: string,
  imageIndex: number,
  mimeType?: string
) {
  return downloadProjectProfileImage(
    '/public/site-access/guard/project-profile/images', { sceneToken }, imageIndex, mimeType
  );
}

export function downloadPublicProjectRouteImage(inviteToken: string): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${API_BASE_URL}/public/site-access/project-location/route-image`,
      method: 'POST',
      data: { inviteToken },
      header: { 'Content-Type': 'application/json' },
      responseType: 'arraybuffer',
      success: (response) => {
        const data = response.data as ArrayBuffer;
        if (response.statusCode >= 200 && response.statusCode < 300 && data instanceof ArrayBuffer) {
          // #ifdef MP-WEIXIN
          const wxApi = (globalThis as typeof globalThis & { wx?: any }).wx;
          const fs = wxApi?.getFileSystemManager?.();
          const root = wxApi?.env?.USER_DATA_PATH;
          if (!fs || !root) {
            reject(new Error('当前微信环境无法保存到访路线图'));
            return;
          }
          const responseHeaders = (response.header || {}) as Record<string, string>;
          const contentType = responseHeaders['content-type'] || responseHeaders['Content-Type'];
          const filePath = `${root}/visitor-route-${Date.now()}-${Math.random().toString(36).slice(2)}.${extensionForMimeType(contentType?.split(';')[0])}`;
          fs.writeFile({
            filePath,
            data,
            success: () => resolve(filePath),
            fail: () => reject(new Error('到访路线图保存失败'))
          });
          return;
          // #endif
          // #ifndef MP-WEIXIN
          reject(new Error('到访路线图仅支持微信小程序查看'));
          return;
          // #endif
        }
        try {
          const result = JSON.parse(decodeArrayBuffer(data)) as { message?: string };
          reject(new Error(result?.message || `到访路线图加载失败（${response.statusCode}）`));
        } catch {
          reject(new Error(`到访路线图加载失败（${response.statusCode}）`));
        }
      },
      fail: () => reject(new Error('到访路线图加载失败，请检查网络'))
    });
  });
}

export function removePublicProjectProfileImages(paths: string[]) {
  // #ifdef MP-WEIXIN
  const wxApi = (globalThis as typeof globalThis & { wx?: any }).wx;
  const fs = wxApi?.getFileSystemManager?.();
  if (!fs) return;
  paths.forEach((filePath) => fs.unlink({ filePath, fail: () => undefined }));
  // #endif
}

export function removePublicProjectRouteImage(filePath?: string) {
  if (filePath) removePublicProjectProfileImages([filePath]);
}

export function createPublicVisitorSession(inviteToken: string, wechatCode: string) {
  return request<PublicVisitorSession>('/public/site-access/visitor-sessions', {
    method: 'POST',
    data: { inviteToken, wechatCode },
    skipAuthRedirect: true
  });
}

function visitorSessionHeader(visitorSessionToken: string) {
  return { 'X-Visitor-Session': visitorSessionToken };
}

export function getPublicVisitorProfiles(visitorSessionToken: string) {
  return request<SiteVisitorProfile[]>('/public/site-access/visitor-profiles/list', {
    method: 'POST',
    data: {},
    header: visitorSessionHeader(visitorSessionToken),
    skipAuthRedirect: true
  });
}

export function getPublicVisitorProfile(visitorSessionToken: string, profileCode: string) {
  return request<SiteVisitorProfile>('/public/site-access/visitor-profiles/detail', {
    method: 'POST',
    data: { profileCode },
    header: visitorSessionHeader(visitorSessionToken),
    skipAuthRedirect: true
  });
}

export function disablePublicVisitorProfile(visitorSessionToken: string, profileCode: string) {
  return request<void>('/public/site-access/visitor-profiles/disable', {
    method: 'POST',
    data: { profileCode },
    header: visitorSessionHeader(visitorSessionToken),
    skipAuthRedirect: true
  });
}

export function submitPublicSiteVisit(payload: PublicSiteVisitSubmitPayload, visitorSessionToken?: string) {
  return request<PublicSiteVisitInvitation>('/public/site-access/invitations/submit', {
    method: 'POST',
    data: payload,
    header: visitorSessionToken ? visitorSessionHeader(visitorSessionToken) : undefined,
    skipAuthRedirect: true,
    timeout: 30000
  });
}

export function createPublicGuardVisitorSession(sceneToken: string, wechatCode: string) {
  return request<PublicGuardVisitorSession>('/public/site-access/guard/session', {
    method: 'POST',
    data: { sceneToken, wechatCode },
    skipAuthRedirect: true
  });
}

export function submitPublicGuardVisit(payload: PublicGuardVisitSubmitPayload, visitorSessionToken: string) {
  return request<PublicGuardVisitPass>('/public/site-access/guard/submit', {
    method: 'POST',
    data: payload,
    header: visitorSessionHeader(visitorSessionToken),
    skipAuthRedirect: true,
    timeout: 30000
  });
}

export function getPublicGuardVisitorProfiles(visitorSessionToken: string) {
  return request<SiteVisitorProfile[]>('/public/site-access/guard/profiles/list', {
    method: 'POST', data: {}, header: visitorSessionHeader(visitorSessionToken), skipAuthRedirect: true
  });
}

export function getPublicGuardVisitorProfile(visitorSessionToken: string, profileCode: string) {
  return request<SiteVisitorProfile>('/public/site-access/guard/profiles/detail', {
    method: 'POST', data: { profileCode }, header: visitorSessionHeader(visitorSessionToken), skipAuthRedirect: true
  });
}

export function disablePublicGuardVisitorProfile(visitorSessionToken: string, profileCode: string) {
  return request<void>('/public/site-access/guard/profiles/disable', {
    method: 'POST', data: { profileCode }, header: visitorSessionHeader(visitorSessionToken), skipAuthRedirect: true
  });
}
