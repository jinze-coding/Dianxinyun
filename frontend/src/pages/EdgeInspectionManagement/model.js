export const WEEKDAY_OPTIONS = Object.freeze([
  { value: 1, label: '周一' }, { value: 2, label: '周二' }, { value: 3, label: '周三' },
  { value: 4, label: '周四' }, { value: 5, label: '周五' }, { value: 6, label: '周六' },
  { value: 7, label: '周日' },
]);

export const EDGE_POINT_TYPE_OPTIONS = Object.freeze([
  { code: 'FLOOR_BALCONY_EAVE_EDGE', name: '楼层、阳台及挑檐边' },
  { code: 'STAIR_PLATFORM_FLIGHT_EDGE', name: '楼梯口、平台及梯段边' },
  { code: 'ROOF_EDGE', name: '屋面边' },
  { code: 'PIT_TRENCH_EDGE', name: '基坑、沟槽边' },
  { code: 'OPENING_RESERVED_HOLE', name: '洞口、预留洞' },
  { code: 'ELEVATOR_SHAFT', name: '电梯井口、井道' },
  { code: 'HOIST_LANDING_PLATFORM', name: '升降机、物料提升机停层平台' },
  { code: 'LOADING_UNLOADING_PLATFORM', name: '接料、卸料平台' },
]);

export const EDGE_TASK_STATUS_TEXT = Object.freeze({
  PENDING: '待巡检', OVERDUE: '逾期未检', OVERDUE_PENDING: '逾期未检', OVERDUE_MISSED: '逾期未检', COMPLETED: '按时完成',
  LATE_COMPLETED: '逾期补检', OVERDUE_COMPLETED: '逾期补检', RECTIFICATION_PENDING: '整改中',
  RECTIFYING: '整改中', REVIEW_PENDING: '待复查', CLOSED: '已闭环', CANCELLED: '已取消',
});

export const EDGE_RECTIFICATION_STATUS_TEXT = Object.freeze({
  UNASSIGNED: '待分派', PENDING: '待整改', RECTIFICATION_PENDING: '待整改',
  COMPLETED: '待复查', REVIEW_PENDING: '待复查', REJECTED: '已退回', CLOSED: '已关闭',
  VOIDED: '已作废',
});

export const EDGE_REMINDER_PROJECTION_LABEL = '计划提醒时间';
export const EDGE_REMINDER_PROJECTION_NOTICE = '按当前周期推算，不代表对应巡检任务已经生成；实际任务以巡检记录和小程序为准。';

export function edgeTaskDisplayStatus(task, now = new Date()) {
  const explicit = String(task?.displayStatus || task?.recordStatus || '').toUpperCase();
  if (explicit) return explicit;
  const status = String(task?.status || '').toUpperCase();
  if (status === 'COMPLETED' && (task?.lateSubmission || task?.lateSubmitted || task?.lateFlag || task?.overdueSubmitted)) {
    return 'LATE_COMPLETED';
  }
  if (status === 'PENDING') {
    const due = task?.dueTime || task?.deadline || task?.windowEnd;
    if (due && new Date(due).getTime() < now.getTime()) return 'OVERDUE';
  }
  return status || 'PENDING';
}

export function edgeTaskStatusText(task, now) {
  const status = edgeTaskDisplayStatus(task, now);
  return EDGE_TASK_STATUS_TEXT[status] || status || '-';
}

export function edgePointTypeCode(value) {
  return value?.pointTypeCode || value?.typeCode || value?.categoryCode || value?.code || '';
}

export function edgePointTypeName(value) {
  return value?.pointTypeName || value?.typeName || value?.categoryName || value?.name || edgePointTypeCode(value) || '-';
}

export function formatLocalDate(value = new Date(), timezoneOffsetMinutes) {
  const date = value instanceof Date ? value : new Date(value);
  const time = date.getTime();
  if (!Number.isFinite(time)) return '';
  const offset = timezoneOffsetMinutes ?? date.getTimezoneOffset();
  if (!Number.isFinite(offset)) return '';
  const localDate = new Date(time - offset * 60_000);
  const year = localDate.getUTCFullYear();
  const month = String(localDate.getUTCMonth() + 1).padStart(2, '0');
  const day = String(localDate.getUTCDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function normalizeEdgeSetting(value = {}, projectId, now = new Date(), timezoneOffsetMinutes) {
  return {
    projectId: Number(projectId),
    frequency: value.frequency || 'DAILY',
    weekdays: Array.isArray(value.weekdays) && value.weekdays.length ? value.weekdays.map(Number) : [1, 2, 3, 4, 5, 6, 7],
    monthDay: Number(value.monthDay) || 1,
    monthEnd: Boolean(value.monthEnd),
    effectiveStart: value.effectiveStart || formatLocalDate(now, timezoneOffsetMinutes),
    startTime: String(value.startTime || '08:00').slice(0, 5),
    dueTime: String(value.dueTime || '18:00').slice(0, 5),
    assigneeId: value.assigneeId ?? '',
    rectifierId: value.rectifierId ?? '',
    reviewerId: value.reviewerId ?? '',
    rectificationDays: Number(value.rectificationDays ?? 3),
    enabled: Boolean(value.enabled),
    submissionReminderEnabled: Boolean(value.submissionReminderEnabled),
    reminderEffectiveTime: value.reminderEffectiveTime || '',
    nextReminderTime: value.nextReminderTime || '',
    expectedVersion: Number(value.version ?? value.expectedVersion ?? 0),
  };
}

export function taskTimeRange(task) {
  const start = task?.startTime || task?.windowStart || task?.executeStartTime;
  const due = task?.dueTime || task?.windowEnd || task?.executeEndTime;
  if (!start && !due) return '-';
  const format = (value) => {
    if (!value) return '-';
    const text = String(value);
    return text.includes('T') ? text.replace('T', ' ').slice(0, 16) : text.slice(0, 16);
  };
  return `${format(start)} 至 ${format(due)}`;
}
