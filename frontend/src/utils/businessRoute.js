const ROUTE_PARAM_KEYS = Object.freeze({
  SEAL_APPLICATION_DETAIL: ['applicationId', 'sealApplicationId', 'id'],
  QUALITY_ISSUE_DETAIL: ['issueId', 'qualityIssueId', 'id'],
  INSPECTION_FORM: ['boxId', 'electricBoxId', 'id'],
  INSPECTION_RECORD_DETAIL: ['recordId', 'inspectionRecordId', 'id'],
  INSPECTION_RECTIFICATION_DETAIL: ['rectificationId', 'id'],
});

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
  const parameterKeys = ROUTE_PARAM_KEYS[routeCode];
  if (!parameterKeys) return null;

  const routeParams = parseRouteParams(item?.routeParams);
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
