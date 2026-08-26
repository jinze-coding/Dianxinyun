export interface InspectionTodoIdentity {
  type?: string;
  businessType?: string;
  routeCode?: string;
  routeKey?: string;
}

const ELECTRIC_TODO_TYPES = new Set([
  'INSPECTION',
  'REVIEW',
  'RECTIFICATION',
  'RECTIFICATION_ASSIGN',
  'RECHECK',
  'RECHECK_ASSIGN'
]);

export function isElectricInspectionTodo(todo: InspectionTodoIdentity) {
  const businessType = String(todo.businessType || '').trim().toUpperCase();
  if (businessType) return businessType === 'INSPECTION_RECORD';

  const routeCode = String(todo.routeCode || todo.routeKey || '').trim().toUpperCase();
  if (['QUALITY', 'EDGE_INSPECTION', 'SEAL', 'DOCUMENT'].some((marker) => routeCode.includes(marker))) return false;
  return ELECTRIC_TODO_TYPES.has(String(todo.type || '').trim().toUpperCase());
}
