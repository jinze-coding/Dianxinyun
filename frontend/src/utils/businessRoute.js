const ROUTE_PARAM_KEYS = Object.freeze({
  SEAL_APPLICATION_DETAIL: ['applicationId', 'sealApplicationId', 'id'],
  QUALITY_ISSUE_DETAIL: ['issueId', 'qualityIssueId', 'id'],
  INSPECTION_FORM: ['boxId', 'electricBoxId', 'id'],
  INSPECTION_RECORD_DETAIL: ['recordId', 'inspectionRecordId', 'id'],
  INSPECTION_RECTIFICATION_DETAIL: ['rectificationId', 'id'],
  EDGE_INSPECTION_TASK_DETAIL: ['taskId', 'id'],
  EDGE_INSPECTION_RECTIFICATION_DETAIL: ['taskId', 'rectificationId', 'id'],
  DOCUMENT_DISTRIBUTION_DETAIL: ['distributionId', 'id'],
});

const ISO_DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

function validIsoDate(value) {
  if (!ISO_DATE_PATTERN.test(value)) return false;
  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year
    && date.getUTCMonth() === month - 1
    && date.getUTCDate() === day;
}

function positiveInteger(value) {
  const number = Number(value);
  return Number.isSafeInteger(number) && number > 0 ? number : null;
}

function parseRouteParams(value) {
  if (!value) return {};
  if (typeof value === 'object' && !Array.isArray(value)) return value;
  if (typeof value !== 'string') return {};
  try {
    const parsed = JSON.parse(value);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
  } catch {
    return {};
  }
}

/**
 * Resolve only server-defined business routes. Deliberately ignores actionUrl
 * and other arbitrary URLs so inbox data cannot become an open redirect.
 */
export function resolveBusinessRoute(item) {
  const routeCode = String(item?.routeCode || item?.routeKey || '').trim().toUpperCase();
  const routeParams = parseRouteParams(item?.routeParams);
  if (routeCode === 'QUALITY_WEEKLY_INSPECTION_WEEK') {
    const itemProjectId = positiveInteger(item?.projectId);
    const paramProjectId = positiveInteger(routeParams.projectId);
    if (itemProjectId && paramProjectId && itemProjectId !== paramProjectId) return null;
    const projectId = itemProjectId || paramProjectId;
    const weekStart = String(routeParams.weekStart || '').trim();
    if (!projectId || !validIsoDate(weekStart)) return null;
    return { routeCode, projectId, weekStart };
  }
  const parameterKeys = ROUTE_PARAM_KEYS[routeCode];
  if (!parameterKeys) return null;

  const id = parameterKeys
    .map((key) => positiveInteger(routeParams[key]))
    .find(Boolean)
    || positiveInteger(item?.targetId)
    || positiveInteger(item?.businessId);
  if (!id) return null;

  return {
    routeCode,
    id,
    projectId: positiveInteger(item?.projectId) || positiveInteger(routeParams.projectId),
  };
}
