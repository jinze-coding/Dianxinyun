export const INSPECTION_TODO_TASK_TYPES = Object.freeze([
  'INSPECTION',
  'REVIEW',
  'EDGE_INSPECTION_TASK',
  'EDGE_INSPECTION_TASK_ASSIGN',
  'EDGE_INSPECTION_RECTIFICATION',
  'EDGE_INSPECTION_RECTIFICATION_ASSIGN',
  'EDGE_INSPECTION_REVIEW',
]);

export function inspectionTodoCount(byTaskType = {}) {
  return INSPECTION_TODO_TASK_TYPES.reduce((total, taskType) => {
    const count = Number(byTaskType?.[taskType] ?? 0);
    return total + (Number.isFinite(count) ? Math.max(0, count) : 0);
  }, 0);
}
