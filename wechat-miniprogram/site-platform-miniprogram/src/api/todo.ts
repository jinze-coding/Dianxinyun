import { getMockTodos } from '@/mock/runtime';
import type { PageResult, TodoItem, TodoSummary, UserNotification } from '@/types';
import { USE_MOCK, request } from './request';

const TODO_TYPES: TodoItem['type'][] = ['INSPECTION', 'REVIEW', 'RECTIFICATION', 'RECHECK', 'SEAL_APPROVAL'];
const TODO_PRIORITIES: NonNullable<TodoItem['priority']>[] = ['normal', 'warning', 'danger'];

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object';
}

function toText(value: unknown, fallback = '') {
  if (value === null || value === undefined) return fallback;
  const text = String(value).trim();
  return text || fallback;
}

function toNumber(value: unknown, fallback: number) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function normalizeRouteParams(value: unknown): Record<string, string | number> | undefined {
  let source = value;
  if (typeof source === 'string') {
    try { source = JSON.parse(source); }
    catch { return undefined; }
  }
  if (!isRecord(source)) return undefined;
  return Object.fromEntries(Object.entries(source)
    .filter(([, item]) => typeof item === 'string' || typeof item === 'number')) as Record<string, string | number>;
}

function normalizeType(value: unknown, businessType?: unknown, routeKey?: unknown): TodoItem['type'] {
  const type = toText(value).toUpperCase();
  if (TODO_TYPES.includes(type as TodoItem['type'])) return type as TodoItem['type'];
  if (toText(businessType).toUpperCase() === 'SEAL_APPLICATION'
    || toText(routeKey).toUpperCase() === 'SEAL_APPLICATION_DETAIL') return 'SEAL_APPROVAL';
  return 'INSPECTION';
}

function normalizePriority(value: unknown): TodoItem['priority'] {
  const priority = toText(value).toLowerCase();
  return TODO_PRIORITIES.includes(priority as NonNullable<TodoItem['priority']>)
    ? priority as TodoItem['priority']
    : undefined;
}

function fallbackTitle(type: TodoItem['type'], marker: string) {
  if (type === 'SEAL_APPROVAL') return `${marker || '用印申请'} 待审批`;
  if (type === 'REVIEW') return `${marker} 待安全复核`;
  if (type === 'RECTIFICATION') return `${marker} 异常整改`;
  if (type === 'RECHECK') return `${marker} 整改完成待复查`;
  return `${marker} 今日待巡检`;
}

function normalizeTodoItem(value: unknown, index: number, scope: 'PENDING' | 'CC' = 'PENDING'): TodoItem {
  const record = isRecord(value) ? value : {};
  const taskType = toText(record.taskType || record.type);
  const type = normalizeType(taskType, record.businessType, record.routeCode || record.routeKey);
  const targetId = toNumber(record.targetId, 0);
  const marker = toText(record.boxCode || record.applicationNo, type === 'SEAL_APPROVAL' ? '用印申请' : '-');
  return {
    id: toNumber(record.id, targetId || index + 1),
    todoKey: toText(record.todoKey, `${scope}-${type}-${targetId || index + 1}`),
    type,
    taskType,
    taskId: record.taskId === null || record.taskId === undefined ? undefined : toNumber(record.taskId, 0),
    title: toText(record.title, fallbackTitle(type, marker)),
    projectId: record.projectId === null || record.projectId === undefined ? undefined : toNumber(record.projectId, 0),
    projectName: toText(record.projectName),
    boxCode: marker,
    installLocation: toText(record.installLocation),
    summary: toText(record.summary),
    applicantName: toText(record.applicantName),
    dueAt: toText(record.dueAt),
    dueText: toText(record.dueText, '请及时处理'),
    targetId,
    businessType: toText(record.businessType),
    actionUrl: toText(record.actionUrl),
    routeKey: toText(record.routeKey),
    routeCode: toText(record.routeCode || record.routeKey),
    routeParams: normalizeRouteParams(record.routeParams),
    scope,
    createdAt: toText(record.createdAt),
    priority: normalizePriority(record.priority),
    reviewDueTime: record.reviewDueTime === null ? null : toText(record.reviewDueTime),
    assignedReviewerId: record.assignedReviewerId === null ? null : toNumber(record.assignedReviewerId, 0),
    assignedReviewerName: record.assignedReviewerName === null ? null : toText(record.assignedReviewerName),
    reviewOverdue: typeof record.reviewOverdue === 'boolean' ? record.reviewOverdue : toNumber(record.reviewOverdue, 0)
  };
}

function normalizeTodoItems(value: unknown, scope: 'PENDING' | 'CC' = 'PENDING'): TodoItem[] {
  const payload = isRecord(value) && Array.isArray(value.records) ? value.records : value;
  if (!Array.isArray(payload)) return [];
  return payload.map((item, index) => normalizeTodoItem(item, index, scope));
}

async function getLegacyTodos(projectId?: number) {
  const suffix = projectId ? `?projectId=${projectId}` : '';
  const [inspectionData, qualityData] = await Promise.all([
    request<unknown>(`/inspection/todos${suffix}`),
    request<unknown>(`/quality/issues/todos${suffix}`)
  ]);
  const inspectionTodos = normalizeTodoItems(inspectionData).map((item) => ({
    ...item,
    routeCode: item.type === 'INSPECTION' ? 'INSPECTION_FORM'
      : item.type === 'REVIEW' ? 'INSPECTION_RECORD_DETAIL' : 'INSPECTION_RECTIFICATION_DETAIL'
  }));
  const qualityTodos = normalizeTodoItems(qualityData).map((item) => ({ ...item, routeCode: 'QUALITY_ISSUE_DETAIL' }));
  return [...inspectionTodos, ...qualityTodos];
}

export async function getTodoItems(projectId?: number): Promise<TodoItem[]> {
  return getScopedTodoItems('PENDING', projectId);
}

export interface TodoPageParams {
  projectId?: number;
  type?: string;
  pageNo?: number;
  pageSize?: number;
}

function normalizeTodoPage(value: unknown, scope: 'PENDING' | 'CC', params: TodoPageParams): PageResult<TodoItem> {
  const record = isRecord(value) ? value : {};
  const records = normalizeTodoItems(value, scope);
  return {
    pageNo: toNumber(record.pageNo, params.pageNo || 1),
    pageSize: toNumber(record.pageSize, params.pageSize || 20),
    total: toNumber(record.total, records.length),
    records
  };
}

function matchesTodoType(item: TodoItem, type?: string) {
  const normalized = toText(type, 'ALL').toUpperCase();
  if (normalized === 'ALL') return true;
  const businessType = toText(item.businessType).toUpperCase();
  if (normalized === 'SEAL' || normalized === 'SEAL_APPLICATION') return businessType === 'SEAL_APPLICATION';
  if (normalized === 'QUALITY' || normalized === 'QUALITY_ISSUE') return businessType === 'QUALITY_ISSUE';
  if (normalized === 'INSPECTION_RECORD') return businessType === 'INSPECTION_RECORD';
  return toText(item.taskType || item.type).toUpperCase() === normalized;
}

function pageTodoItems(items: TodoItem[], params: TodoPageParams): PageResult<TodoItem> {
  const pageNo = Math.max(1, Math.trunc(params.pageNo || 1));
  const pageSize = Math.max(1, Math.min(100, Math.trunc(params.pageSize || 20)));
  const filtered = items.filter((item) => matchesTodoType(item, params.type));
  const start = (pageNo - 1) * pageSize;
  return {
    pageNo,
    pageSize,
    total: filtered.length,
    records: filtered.slice(start, start + pageSize)
  };
}

export async function getScopedTodoPage(
  scope: 'PENDING' | 'CC',
  params: TodoPageParams = {}
): Promise<PageResult<TodoItem>> {
  if (USE_MOCK) {
    const items = scope === 'PENDING' ? normalizeTodoItems(getMockTodos(), scope) : [];
    return pageTodoItems(items, params);
  }
  const query = Object.entries({
    scope,
    projectId: params.projectId,
    type: params.type,
    pageNo: params.pageNo || 1,
    pageSize: params.pageSize || 20
  })
    .filter(([, value]) => value !== undefined && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
    .join('&');
  try {
    return normalizeTodoPage(await request<unknown>(`/me/todos?${query}`), scope, params);
  } catch (error) {
    if (scope === 'CC') return pageTodoItems([], params);
    return getLegacyTodos(params.projectId)
      .then((items) => pageTodoItems(items, params))
      .catch(() => { throw error; });
  }
}

export async function getScopedTodoItems(scope: 'PENDING' | 'CC', projectId?: number): Promise<TodoItem[]> {
  const result = await getScopedTodoPage(scope, { projectId, pageNo: 1, pageSize: 100 });
  return result.records;
}

export async function getTodoSummary(): Promise<TodoSummary> {
  if (USE_MOCK) {
    const pendingCount = getMockTodos().length;
    return { pendingCount, ccCount: 0, unreadNotificationCount: 0, badgeCount: pendingCount };
  }
  let raw: unknown;
  try { raw = await request<unknown>('/me/work-summary'); }
  catch { raw = await request<unknown>('/me/todos/summary'); }
  const value = isRecord(raw) ? raw : {};
  const pendingCount = toNumber(value.pendingCount ?? value.todoCount ?? value.pendingTodoCount, 0);
  const ccCount = toNumber(value.ccCount ?? value.copiedCount, 0);
  const unreadNotificationCount = toNumber(value.unreadNotificationCount ?? value.notificationUnreadCount ?? value.inboxUnreadCount ?? value.unreadCount, 0);
  return {
    pendingCount,
    ccCount,
    unreadNotificationCount,
    badgeCount: toNumber(value.badgeCount ?? value.totalBadgeCount ?? value.totalCount, pendingCount + ccCount + unreadNotificationCount)
  };
}

function normalizeNotification(value: unknown, index: number): UserNotification {
  const record = isRecord(value) ? value : {};
  const isRead = record.isRead === true || Number(record.isRead) === 1;
  return {
    id: toNumber(record.id, index + 1),
    projectId: record.projectId === null || record.projectId === undefined ? undefined : toNumber(record.projectId, 0),
    projectName: toText(record.projectName),
    businessType: toText(record.businessType),
    businessId: record.businessId === null || record.businessId === undefined ? undefined : toNumber(record.businessId, 0),
    eventCode: toText(record.eventCode),
    title: toText(record.title, '业务通知'),
    summary: toText(record.summary),
    isRead,
    readTime: toText(record.readTime),
    createTime: toText(record.createTime),
    actionUrl: toText(record.actionUrl),
    routeKey: toText(record.routeKey),
    routeCode: toText(record.routeCode || record.routeKey),
    routeParams: normalizeRouteParams(record.routeParams)
  };
}

export async function getUserNotifications(params: {
  readStatus?: 'ALL' | 'UNREAD' | 'READ';
  businessType?: string;
  projectId?: number;
  pageNo?: number;
  pageSize?: number;
} = {}): Promise<PageResult<UserNotification>> {
  if (USE_MOCK) return { pageNo: 1, pageSize: 20, total: 0, records: [] };
  const query = Object.entries({ readStatus: params.readStatus || 'ALL', businessType: params.businessType,
    projectId: params.projectId, pageNo: params.pageNo || 1, pageSize: params.pageSize || 50 })
    .filter(([, value]) => value !== undefined && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
    .join('&');
  let raw: unknown;
  try { raw = await request<unknown>(`/me/inbox?${query}`); }
  catch { raw = await request<unknown>(`/me/notifications?${query}`); }
  const value = isRecord(raw) ? raw : {};
  const records = Array.isArray(raw) ? raw
    : Array.isArray(value.records) ? value.records
      : Array.isArray(value.items) ? value.items : [];
  return {
    pageNo: toNumber(value.pageNo, 1),
    pageSize: toNumber(value.pageSize, params.pageSize || 50),
    total: toNumber(value.total, records.length),
    records: records.map(normalizeNotification)
  };
}

export async function getUnreadNotificationCount() {
  if (USE_MOCK) return 0;
  let raw: unknown;
  try { raw = await request<unknown>('/me/inbox/unread-count'); }
  catch { raw = await request<unknown>('/me/notifications/unread-count'); }
  if (typeof raw === 'number') return raw;
  return toNumber(isRecord(raw) ? raw.count ?? raw.unreadCount : raw, 0);
}

export async function markNotificationRead(id: number) {
  if (USE_MOCK) return;
  try { return await request<void>(`/me/inbox/${id}/read`, { method: 'PUT' }); }
  catch { return request<void>(`/me/notifications/${id}/read`, { method: 'PUT' }); }
}

export async function markAllNotificationsRead() {
  if (USE_MOCK) return;
  try { return await request<void>('/me/inbox/read-all', { method: 'PUT' }); }
  catch { return request<void>('/me/notifications/read-all', { method: 'PUT' }); }
}
