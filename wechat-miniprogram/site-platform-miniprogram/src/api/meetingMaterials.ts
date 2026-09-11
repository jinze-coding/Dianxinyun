import { API_BASE_URL, request } from './request';

export interface PublicMeetingMaterial {
  title: string; category: string; description: string; versionNo: number; publicCode: string;
  fileName: string; fileSize: number; previewKind: string; previewStatus: string; updatedAt: string;
}
export interface PublicMeetingMaterials { title: string; ended: boolean; records: PublicMeetingMaterial[] }
export const resolveMeetingMaterials = (inviteToken: string) => request<PublicMeetingMaterials>('/public/site-access/meeting/materials/resolve', {
  method: 'POST', data: { inviteToken }, skipAuthRedirect: true
});
export function publicMaterialUrl(code: string, preview = true) {
  if (!/^[a-f0-9]{32}$/.test(code)) throw new Error('资料地址无效');
  return `${API_BASE_URL}/public/site-access/meeting/materials/content/${code}?preview=${preview}`;
}
