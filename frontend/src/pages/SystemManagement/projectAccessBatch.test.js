import test from 'node:test';
import assert from 'node:assert/strict';
import { changeBatchSelection, canSelectBatchUser, buildAccessBatchRequest, accessBatchFormError } from './projectAccessBatch.js';
const user = (id) => ({ id, status: 1, roles: [] });
const form = { operation: 'ADD_ROLES', projectId: '9', sourceProjectId: '8', targetProjectId: '9', roleSource: 'SOURCE', roleIds: [20] };

test('cross-page selection preserves prior users and supports clearing just one page', () => {
  let current = changeBatchSelection([user(1)], [user(2), user(3)], true).users;
  assert.deepEqual(current.map((u) => u.id), [1, 2, 3]);
  current = changeBatchSelection(current, [user(2), user(3)], false).users;
  assert.deepEqual(current.map((u) => u.id), [1]);
});
test('disabled users and platform admins cannot be selected, repeat selection deduplicates', () => {
  assert.equal(canSelectBatchUser({ ...user(1), status: 0 }), false);
  assert.equal(canSelectBatchUser({ ...user(1), roles: ['PLATFORM_ADMIN'] }), false);
  const result = changeBatchSelection([user(1)], [user(1), { ...user(2), status: 0 }], true);
  assert.deepEqual(result.users, [user(1)]);
});
test('selection limit rejects an oversized page without partially selecting it', () => {
  const current = Array.from({ length: 199 }, (_, i) => user(i + 1));
  const result = changeBatchSelection(current, [user(200), user(201)], true);
  assert.equal(result.users, current); assert.ok(result.error.includes('200'));
});
test('same-project request never carries stale transfer fields', () => {
  assert.deepEqual(buildAccessBatchRequest(['2', 3], form), { userIds: [2, 3], operation: 'ADD_ROLES', projectId: 9, roleIds: [20] });
});
test('source roles and selected roles produce distinct transfer requests', () => {
  const copy = { ...form, operation: 'COPY_PROJECT' };
  assert.deepEqual(buildAccessBatchRequest([2], copy), { userIds: [2], operation: 'COPY_PROJECT', sourceProjectId: 8, targetProjectId: 9, roleSource: 'SOURCE', roleIds: [] });
  assert.deepEqual(buildAccessBatchRequest([2], { ...copy, roleSource: 'SELECTED' }).roleIds, [20]);
});
test('validates recipients, projects, distinct transfer ends, and nonempty explicit roles', () => {
  assert.ok(accessBatchFormError(0, form)); assert.ok(accessBatchFormError(201, form));
  assert.ok(accessBatchFormError(1, { ...form, roleIds: [] }));
  assert.ok(accessBatchFormError(1, { ...form, operation: 'MOVE_PROJECT', sourceProjectId: '9' }));
  assert.equal(accessBatchFormError(1, { ...form, operation: 'COPY_PROJECT', roleIds: [] }), '');
  assert.equal(accessBatchFormError(2, form), '');
});
