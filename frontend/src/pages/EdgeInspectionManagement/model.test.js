import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  EDGE_POINT_TYPE_OPTIONS,
  EDGE_REMINDER_PROJECTION_LABEL,
  EDGE_REMINDER_PROJECTION_NOTICE,
  edgePendingPhaseText,
  edgeTaskDisplayStatus,
  edgeTaskStatusText,
  formatLocalDate,
  normalizeEdgeSetting,
  selectTodayOpenPendingTasks,
} from './model.js';

describe('fixed edge inspection model', () => {
  it('exposes exactly the eight immutable system point types', () => {
    assert.equal(EDGE_POINT_TYPE_OPTIONS.length, 8);
    assert.equal(new Set(EDGE_POINT_TYPE_OPTIONS.map((item) => item.code)).size, 8);
  });

  it('keeps late submission and overdue pending statuses distinct', () => {
    assert.equal(edgeTaskDisplayStatus({ status: 'COMPLETED', lateSubmission: true }), 'LATE_COMPLETED');
    assert.equal(edgeTaskStatusText({ status: 'COMPLETED', lateSubmission: true }), '逾期补检');
    assert.equal(edgeTaskDisplayStatus({ status: 'PENDING', dueTime: '2026-08-25T08:00:00' }, new Date('2026-08-26T08:00:00')), 'OVERDUE');
    assert.equal(edgeTaskStatusText({ status: 'PENDING', dueTime: '2026-08-25T08:00:00' }, new Date('2026-08-26T08:00:00')), '逾期未检');
  });

  it('selects only today pending tasks that have not passed their deadline', () => {
    const now = new Date('2026-09-03T09:57:00+08:00');
    const rows = selectTodayOpenPendingTasks([
      { id: 3, pointCode: 'EDGE-03', status: 'PENDING', occurrenceDate: '2026-09-03', dueTime: '2026-09-03T18:00:00+08:00' },
      { id: 1, pointCode: 'EDGE-01', status: 'PENDING', occurrenceDate: '2026-09-03', dueTime: '2026-09-03T18:00:00+08:00' },
      { id: 2, pointCode: 'EDGE-02', status: 'PENDING', occurrenceDate: '2026-09-03', dueTime: '2026-09-03T09:56:59+08:00' },
      { id: 4, pointCode: 'EDGE-04', status: 'PENDING', occurrenceDate: '2026-09-04', dueTime: '2026-09-04T18:00:00+08:00' },
      { id: 5, pointCode: 'EDGE-05', status: 'COMPLETED', occurrenceDate: '2026-09-03', dueTime: '2026-09-03T18:00:00+08:00' },
      { id: 6, pointCode: 'EDGE-06', status: 'PENDING', occurrenceDate: '2026-09-03', dueTime: '2026-09-03T18:00:00+08:00', overdue: true },
    ], '2026-09-03', now);
    assert.deepEqual(rows.map((row) => row.id), [1, 3]);
  });

  it('distinguishes a task waiting for its execution window from one ready for inspection', () => {
    const now = new Date('2026-09-03T09:57:00+08:00');
    assert.equal(edgePendingPhaseText({ availableTime: '2026-09-03T10:00:00+08:00' }, now), '待开始');
    assert.equal(edgePendingPhaseText({ availableTime: '2026-09-03T08:00:00+08:00' }, now), '待巡检');
  });

  it('formats the calendar date using an explicit local timezone offset', () => {
    const instant = new Date('2026-08-25T16:30:00.000Z');
    assert.equal(formatLocalDate(instant, -480), '2026-08-26');
    assert.equal(formatLocalDate(instant, 420), '2026-08-25');
  });

  it('normalizes the one-project one-setting response with a deterministic local date', () => {
    const now = new Date('2026-08-25T16:30:00.000Z');
    assert.deepEqual(normalizeEdgeSetting({ frequency: 'WEEKLY', weekdays: [1, 5], version: 3 }, 9, now, -480), {
      projectId: 9,
      frequency: 'WEEKLY',
      weekdays: [1, 5],
      monthDay: 1,
      monthEnd: false,
      effectiveStart: '2026-08-26',
      startTime: '08:00',
      dueTime: '18:00',
      assigneeId: '',
      rectifierId: '',
      reviewerId: '',
      rectificationDays: 3,
      enabled: false,
      submissionReminderEnabled: false,
      reminderEffectiveTime: '',
      nextReminderTime: '',
      expectedVersion: 3,
    });
  });

  it('preserves the internal overdue-submission reminder lifecycle fields', () => {
    const value = normalizeEdgeSetting({
      submissionReminderEnabled: true,
      reminderEffectiveTime: '2026-08-29T10:00:00',
      nextReminderTime: '2026-08-30T18:00:00',
    }, 3);
    assert.equal(value.submissionReminderEnabled, true);
    assert.equal(value.reminderEffectiveTime, '2026-08-29T10:00:00');
    assert.equal(value.nextReminderTime, '2026-08-30T18:00:00');
  });

  it('describes the projected reminder time without claiming a task exists', () => {
    assert.equal(EDGE_REMINDER_PROJECTION_LABEL, '计划提醒时间');
    assert.match(EDGE_REMINDER_PROJECTION_NOTICE, /不代表对应巡检任务已经生成/);
    assert.match(EDGE_REMINDER_PROJECTION_NOTICE, /巡检记录和小程序为准/);
  });
});
