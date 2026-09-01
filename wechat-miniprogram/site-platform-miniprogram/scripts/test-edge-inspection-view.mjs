import assert from 'node:assert/strict';
import {
  cleanEdgeDisplayText,
  isEdgeTaskBeforeWindow,
  selectEdgeTasksForView,
  shanghaiDateKey,
  shiftDateKey
} from '../src/utils/edgeInspectionView.ts';

assert.equal(cleanEdgeDisplayText('[EDGE_DEMO_20260828] 北侧接料平台'), '北侧接料平台');
assert.equal(cleanEdgeDisplayText('点位 [EDGE_DEMO_LOCAL] 屋面边'), '点位 屋面边');
assert.equal(cleanEdgeDisplayText('[EDGE_DEMO_X]', '未命名点位'), '未命名点位');

assert.equal(shanghaiDateKey(Date.UTC(2026, 7, 27, 16, 30)), '2026-08-28');
assert.equal(shiftDateKey('2026-03-01', -1), '2026-02-28');
assert.equal(isEdgeTaskBeforeWindow({ status: 'PENDING', availableTime: '2026-08-28T16:00:00' }, '2026-08-28T15:00:00'), true);

const tasks = [
  { id: 1, pointCode: 'EDGE-02', occurrenceDate: '2026-08-28', dueTime: '2026-08-28T18:00:00', status: 'PENDING' },
  { id: 2, pointCode: 'EDGE-01', occurrenceDate: '2026-08-28', dueTime: '2026-08-28T18:00:00', status: 'COMPLETED' },
  { id: 3, pointCode: 'EDGE-03', occurrenceDate: '2026-08-27', dueTime: '2026-08-27T18:00:00', status: 'PENDING', overdue: true },
  { id: 4, pointCode: 'EDGE-04', occurrenceDate: '2026-07-29', dueTime: '2026-07-29T18:00:00', status: 'COMPLETED' },
  { id: 5, pointCode: 'EDGE-05', occurrenceDate: '2026-08-29', dueTime: '2026-08-29T18:00:00', status: 'PENDING' },
  { id: 6, pointCode: 'EDGE-06', occurrenceDate: '2026-08-26', dueTime: '2026-08-26T18:00:00', status: 'CANCELLED' }
];

assert.deepEqual(selectEdgeTasksForView(tasks, 'TODAY', '2026-08-28').map((item) => item.id), [2, 1]);
assert.deepEqual(selectEdgeTasksForView(tasks, 'OVERDUE', '2026-08-28', '2026-08-28T15:00:00').map((item) => item.id), [3]);
assert.deepEqual(selectEdgeTasksForView(tasks, 'RECORDS', '2026-08-28').map((item) => item.id), [2, 6]);

console.log('edge inspection view tests passed');
