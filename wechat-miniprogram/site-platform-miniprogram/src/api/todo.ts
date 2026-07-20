import { getMockTodos } from '@/mock/runtime';
import type { TodoItem } from '@/types';
import { USE_MOCK, request } from './request';

const TODO_TYPES: TodoItem['type'][] = ['INSPECTION'];
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

function normalizeType(value: unknown): TodoItem['type'] {
  return TODO_TYPES.includes(value as TodoItem['type']) ? value as TodoItem['type'] : 'INSPECTION';
}

function normalizePriority(value: unknown): TodoItem['priority'] {
  return TODO_PRIORITIES.includes(value as NonNullable<TodoItem['priority']>)
    ? value as TodoItem['priority']
    : undefined;
}

function fallbackTitle(type: TodoItem['type'], boxCode: string) {
  if (type === 'REVIEW') return `${boxCode} 待安全复核`;
  if (type === 'RECTIFICATION') return `${boxCode} 异常整改`;
  if (type === 'RECHECK') return `${boxCode} 整改完成待复查`;
  return `${boxCode} 今日待巡检`;
}

function normalizeTodoItem(value: unknown, index: number): TodoItem {
  const record = isRecord(value) ? value : {};
  const type = normalizeType(record.type);
  const boxCode = toText(record.boxCode, '-');
  return {
    id: toNumber(record.id, index + 1),
    type,
    title: toText(record.title, fallbackTitle(type, boxCode)),
    projectId: record.projectId === null || record.projectId === undefined ? undefined : toNumber(record.projectId, 0),
    projectName: toText(record.projectName, ''),
    boxCode,
    installLocation: toText(record.installLocation, '-'),
    dueText: toText(record.dueText, '-'),
    targetId: toNumber(record.targetId, 0),
    priority: normalizePriority(record.priority),
    reviewDueTime: record.reviewDueTime === null ? null : toText(record.reviewDueTime, ''),
    assignedReviewerId: record.assignedReviewerId === null ? null : toNumber(record.assignedReviewerId, 0),
    assignedReviewerName: record.assignedReviewerName === null ? null : toText(record.assignedReviewerName, ''),
    reviewOverdue: typeof record.reviewOverdue === 'boolean' ? record.reviewOverdue : toNumber(record.reviewOverdue, 0)
  };
}

function normalizeTodoItems(value: unknown): TodoItem[] {
  if (!Array.isArray(value)) return [];
  return value.map(normalizeTodoItem);
}

export async function getTodoItems(projectId?: number): Promise<TodoItem[]> {
  if (USE_MOCK) {
    return normalizeTodoItems(getMockTodos()).filter((item) => item.type === 'INSPECTION');
  }
  const data = await request<unknown>(`/inspection/todos${projectId ? `?projectId=${projectId}` : ''}`);
  return normalizeTodoItems(data).filter((item) => item.type === 'INSPECTION');
}
