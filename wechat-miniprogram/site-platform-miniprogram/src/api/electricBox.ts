import type { ElectricBox, PublicElectricBoxMonthly, PublicElectricBoxSummary, UnifiedElectricBoxScan } from '@/types';
import { request, USE_MOCK } from './request';
import { getMockElectricBoxDetail, getMockElectricBoxes, getMockPublicElectricBoxMonthly, getMockPublicElectricBoxSummary, resolveMockQrCode, resolveMockUnifiedCode } from '@/mock/runtime';

export async function getElectricBoxes(projectId: number): Promise<ElectricBox[]> {
  if (USE_MOCK) {
    return getMockElectricBoxes(projectId);
  }
  return request<ElectricBox[]>(`/electric-boxes?projectId=${projectId}`);
}

export async function getElectricBoxDetail(id: number): Promise<ElectricBox | undefined> {
  if (USE_MOCK) {
    return getMockElectricBoxDetail(id);
  }
  return request<ElectricBox>(`/electric-boxes/${id}`);
}

export async function resolveQrCode(qrCode: string): Promise<ElectricBox | undefined> {
  if (USE_MOCK) {
    return resolveMockQrCode(qrCode);
  }
  return request<ElectricBox>(`/electric-boxes/qr/${encodeURIComponent(qrCode)}`);
}

export async function getPublicElectricBoxSummary(publicCode: string): Promise<PublicElectricBoxSummary> {
  if (USE_MOCK) {
    return getMockPublicElectricBoxSummary(publicCode);
  }
  return request<PublicElectricBoxSummary>(`/public/electric-boxes/${publicCode}/summary`);
}

export async function resolveUnifiedCode(sceneCode: string): Promise<UnifiedElectricBoxScan> {
  if (USE_MOCK) {
    return resolveMockUnifiedCode(sceneCode, 'ELECTRICIAN');
  }
  return request<UnifiedElectricBoxScan>(`/scan/electric-boxes/${encodeURIComponent(sceneCode)}`, {
    skipAuthRedirect: true,
    timeout: 6000
  });
}

export async function getPublicElectricBoxMonthly(publicCode: string, month?: string): Promise<PublicElectricBoxMonthly> {
  if (USE_MOCK) return getMockPublicElectricBoxMonthly(publicCode, month);
  const query = month ? `?month=${encodeURIComponent(month)}` : '';
  return request<PublicElectricBoxMonthly>(`/public/electric-boxes/${encodeURIComponent(publicCode)}/monthly-records${query}`, {
    skipAuthRedirect: true
  });
}
