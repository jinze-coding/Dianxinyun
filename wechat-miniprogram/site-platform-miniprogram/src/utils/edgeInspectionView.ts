export type EdgeTaskView = 'TODAY' | 'OVERDUE' | 'RECORDS';

export interface EdgeTaskViewItem {
  id?: number;
  pointCode?: string;
  occurrenceDate?: string;
  availableTime?: string;
  dueTime?: string;
  status?: string;
  overdue?: boolean;
}

const EDGE_DEMO_MARKER = /\[EDGE_DEMO_[^\]]+\]\s*/gi;

export function cleanEdgeDisplayText(value?: string | null, fallback = '') {
  const cleaned = String(value || '').replace(EDGE_DEMO_MARKER, '').trim();
  return cleaned || fallback;
}

function pad(value: number) {
  return String(value).padStart(2, '0');
}

export function shanghaiDateTimeKey(timestamp = Date.now()) {
  const date = new Date(timestamp + 8 * 60 * 60 * 1000);
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`
    + `T${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}:${pad(date.getUTCSeconds())}`;
}

export function shanghaiDateKey(timestamp = Date.now()) {
  return shanghaiDateTimeKey(timestamp).slice(0, 10);
}

export function shiftDateKey(dateKey: string, days: number) {
  const [year, month, day] = dateKey.split('-').map(Number);
  if (!year || !month || !day) return dateKey;
  const date = new Date(Date.UTC(year, month - 1, day + days));
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`;
}

function dateTimeKey(value?: string) {
  if (!value) return '';
  return value.trim().replace(' ', 'T').slice(0, 19);
}

export function isEdgeTaskOverdue(task: EdgeTaskViewItem, nowKey = shanghaiDateTimeKey()) {
  if (String(task.status || '').toUpperCase() !== 'PENDING') return false;
  if (task.overdue === true) return true;
  const due = dateTimeKey(task.dueTime);
  return Boolean(due) && due < nowKey;
}

export function isEdgeTaskBeforeWindow(task: EdgeTaskViewItem, nowKey = shanghaiDateTimeKey()) {
  if (String(task.status || '').toUpperCase() !== 'PENDING') return false;
  const available = dateTimeKey(task.availableTime);
  return Boolean(available) && available > nowKey;
}

export function selectEdgeTasksForView<T extends EdgeTaskViewItem>(
  tasks: T[],
  view: EdgeTaskView,
  today = shanghaiDateKey(),
  nowKey = shanghaiDateTimeKey()
) {
  const recordStart = shiftDateKey(today, -29);
  const selected = tasks.filter((task) => {
    const status = String(task.status || '').toUpperCase();
    if (view === 'TODAY') return task.occurrenceDate === today;
    if (view === 'OVERDUE') return isEdgeTaskOverdue(task, nowKey);
    return status !== 'PENDING'
      && Boolean(task.occurrenceDate)
      && task.occurrenceDate! >= recordStart
      && task.occurrenceDate! <= today;
  });

  return selected.sort((left, right) => {
    if (view === 'TODAY') {
      return String(left.pointCode || '').localeCompare(String(right.pointCode || ''), 'zh-CN');
    }
    if (view === 'OVERDUE') {
      return dateTimeKey(left.dueTime).localeCompare(dateTimeKey(right.dueTime));
    }
    const byDate = String(right.occurrenceDate || '').localeCompare(String(left.occurrenceDate || ''));
    return byDate || dateTimeKey(right.dueTime).localeCompare(dateTimeKey(left.dueTime));
  });
}
