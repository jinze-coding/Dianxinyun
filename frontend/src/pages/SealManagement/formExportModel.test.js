import test from 'node:test';
import assert from 'node:assert/strict';
import { canSelectForm, selectPageForms, formExportRequest } from './formExportModel.js';

const approved = (id) => ({ id, status: 'APPROVED', canExportForm: true });
test('selection keeps other pages and never includes unapproved or unauthorized records', () => {
  const rows = [approved(1), approved(2), { id: 3, status: 'PENDING_APPROVAL', canExportForm: false }, { ...approved(4), canExportForm: false }];
  assert.equal(canSelectForm(rows[3]), false);
  assert.deepEqual(selectPageForms([10, 1], rows, true), [10, 1, 2]);
  assert.deepEqual(selectPageForms([10, 1, 2], rows, false), [10]);
  assert.deepEqual(selectPageForms([10], [approved(11)], true), [10, 11]);
});
test('filter export carries the applied scope and dates without pagination', () => {
  const request = formExportRequest(3, 'CC_TO_ME', { keyword: '方案', status: 'APPROVED', startDate: '2026-09-01', endDate: '2026-09-11' }, 'FILTER', [10]);
  assert.deepEqual(request, { projectId: 3, selectionMode: 'FILTER', scope: 'CC_TO_ME', keyword: '方案', status: 'APPROVED', startDate: '2026-09-01', endDate: '2026-09-11' });
});
test('selected export sends each application once without applying draft filter inputs', () => {
  assert.deepEqual(formExportRequest(3, 'ALL', { keyword: '尚未查询' }, 'SELECTED', [4, 2, 4]), { projectId: 3, selectionMode: 'SELECTED', applicationIds: [2, 4] });
});
