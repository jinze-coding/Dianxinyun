import { navigateTo, showToast, switchTab } from '@/utils/navigation';

export interface BusinessRouteTarget {
  routeCode?: string;
  routeKey?: string;
  routeParams?: Record<string, string | number>;
  targetId?: number;
  businessId?: number;
  type?: string;
}

function numericParam(target: BusinessRouteTarget, ...keys: string[]) {
  for (const key of keys) {
    const value = Number(target.routeParams?.[key]);
    if (Number.isFinite(value) && value > 0) return value;
  }
  return Number(target.targetId || target.businessId || 0);
}

export function openBusinessRoute(target: BusinessRouteTarget) {
  const routeCode = String(target.routeCode || target.routeKey || '').trim().toUpperCase();
  if (['SEAL_APPLICATION_DETAIL', 'SEAL_DETAIL'].includes(routeCode)) {
    const id = numericParam(target, 'applicationId', 'sealApplicationId', 'id');
    if (id) { navigateTo(`/pages/seal/detail?id=${id}`); return true; }
  }
  if (['QUALITY_ISSUE_DETAIL', 'QUALITY_DETAIL'].includes(routeCode)) {
    const id = numericParam(target, 'issueId', 'qualityIssueId', 'id');
    if (id) {
      uni.setStorageSync('site_platform_quality_issue_id', id);
      switchTab('/pages/quality/index');
      return true;
    }
  }
  if (['INSPECTION_FORM', 'INSPECTION_DAILY_FORM', 'ELECTRIC_BOX_INSPECTION'].includes(routeCode)) {
    const boxId = numericParam(target, 'boxId', 'electricBoxId', 'id');
    if (boxId) { navigateTo(`/pages/inspection/form?boxId=${boxId}`); return true; }
  }
  if (['INSPECTION_RECORD_DETAIL', 'INSPECTION_DETAIL'].includes(routeCode)) {
    const id = numericParam(target, 'recordId', 'inspectionRecordId', 'id');
    if (id) { navigateTo(`/pages/inspection/detail?id=${id}`); return true; }
  }
  if (['INSPECTION_RECTIFICATION_DETAIL', 'RECTIFICATION_DETAIL'].includes(routeCode)) {
    const id = numericParam(target, 'rectificationId', 'id');
    if (id) { navigateTo(`/pages/rectification/detail?id=${id}`); return true; }
  }

  // 仅兼容旧巡检待办；新统一工作台必须由服务端下发 routeCode/routeParams。
  const legacyId = numericParam(target, 'id');
  if (target.type === 'INSPECTION' && legacyId) {
    navigateTo(`/pages/inspection/form?boxId=${legacyId}`);
    return true;
  }
  if (['REVIEW'].includes(String(target.type)) && legacyId) {
    navigateTo(`/pages/inspection/detail?id=${legacyId}`);
    return true;
  }
  if (['RECTIFICATION', 'RECHECK'].includes(String(target.type)) && legacyId) {
    navigateTo(`/pages/rectification/detail?id=${legacyId}`);
    return true;
  }
  showToast('该事项暂未配置移动端详情入口');
  return false;
}
