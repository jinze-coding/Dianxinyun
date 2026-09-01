import { navigateTo, showToast, switchTab } from '@/utils/navigation';

export interface BusinessRouteTarget {
  routeCode?: string;
  routeKey?: string;
  routeParams?: Record<string, string | number>;
  targetId?: number;
  businessId?: number;
  type?: string;
  projectId?: number;
}

const ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

function validIsoDate(value: string) {
  if (!ISO_DATE_PATTERN.test(value)) return false;
  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year
    && date.getUTCMonth() === month - 1
    && date.getUTCDate() === day;
}

function positiveInteger(value: unknown) {
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : 0;
}

function numericParam(target: BusinessRouteTarget, ...keys: string[]) {
  for (const key of keys) {
    const value = positiveInteger(target.routeParams?.[key]);
    if (value) return value;
  }
  return positiveInteger(target.targetId || target.businessId);
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
      navigateTo(`/pages/quality/issue-detail?id=${id}`);
      return true;
    }
  }
  if (routeCode === 'QUALITY_WEEKLY_INSPECTION_WEEK') {
    const itemProjectId = positiveInteger(target.projectId);
    const paramProjectId = positiveInteger(target.routeParams?.projectId);
    if (itemProjectId && paramProjectId && itemProjectId !== paramProjectId) {
      showToast('消息项目参数不一致');
      return false;
    }
    const projectId = paramProjectId || itemProjectId;
    const weekStart = String(target.routeParams?.weekStart || '').trim();
    if (projectId && validIsoDate(weekStart)) {
      navigateTo(`/pages/quality/weekly-edit?projectId=${projectId}&weekStart=${encodeURIComponent(weekStart)}`);
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
  if (routeCode === 'EDGE_INSPECTION_TASK_DETAIL') {
    const id = numericParam(target, 'taskId', 'id');
    if (id) { navigateTo(`/pages/inspection/edge-form?id=${id}`); return true; }
  }
  if (routeCode === 'EDGE_INSPECTION_RECTIFICATION_DETAIL') {
    const id = numericParam(target, 'taskId', 'rectificationId', 'id');
    if (id) { navigateTo(`/pages/rectification/edge-detail?id=${id}`); return true; }
  }
  if (routeCode === 'EDGE_INSPECTION_TASK_LIST') {
    navigateTo('/pages/inspection/edge-tasks');
    return true;
  }
  if (routeCode === 'DOCUMENT_DISTRIBUTION_DETAIL') {
    const id = numericParam(target, 'distributionId', 'id');
    if (id) { navigateTo(`/pages/document-distribution/detail?id=${id}`); return true; }
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
