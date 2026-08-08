import test from 'node:test';
import assert from 'node:assert/strict';
import {
  assignmentChanges,
  assignmentChangeSummary,
  assignmentRoleIdsForNode,
  buildProjectMemberNodes,
  buildUserProjectNodes,
  filterAssignmentNodes,
  mergeAssignmentDrafts,
  toggleAssignmentNode,
  toggleAssignmentRole,
} from './projectRoleAssignments.js';

const roles = [
  { id: 1, roleCode: 'USER', roleName: '项目成员', enabled: 1 },
  { id: 2, roleCode: 'QUALITY_EDITOR', roleName: '质量编辑员', enabled: 1 },
];

test('tree safely normalizes missing or malformed role ids', () => {
  assert.deepEqual(assignmentRoleIdsForNode(undefined), []);
  assert.deepEqual(assignmentRoleIdsForNode({}), []);
  assert.deepEqual(assignmentRoleIdsForNode({ roleIds: ['2', 1, 2, null] }), [1, 2]);
});

test('first parent selection waits for an explicit role and removing last role cancels the draft', () => {
  const original = buildProjectMemberNodes([{ userId: 8, username: 'worker', assigned: false }])[0];
  const selected = toggleAssignmentNode(original, true);
  assert.deepEqual(selected.roleIds, []);
  const joined = toggleAssignmentRole(selected, 2, true);
  assert.deepEqual(assignmentChanges([joined]), [{ userId: 8, operation: 'UPSERT', roleIds: [2] }]);

  const removed = toggleAssignmentRole(joined, 2, false);
  assert.equal(removed.assigned, false);
  assert.deepEqual(assignmentChangeSummary([removed]), { added: 0, updated: 0, removed: 0 });
});

test('pending removal can be cancelled by restoring original roles', () => {
  const original = buildProjectMemberNodes([{
    userId: 9,
    realName: '金泽',
    assigned: true,
    projectRoles: [{ id: 1, roleName: '项目成员' }, { id: 2, roleName: '质量编辑员' }],
  }])[0];
  const removed = toggleAssignmentNode(original, false);
  assert.deepEqual(assignmentChanges([removed]), [{ userId: 9, operation: 'REMOVE', roleIds: [] }]);
  const restored = toggleAssignmentNode(removed, true);
  assert.deepEqual(restored.roleIds, [1, 2]);
  assert.deepEqual(assignmentChanges([restored]), []);
});

test('user project tree includes unassigned projects and builds cross-project changes', () => {
  const nodes = buildUserProjectNodes(
    [{ id: 10, projectName: '智慧工地' }, { id: 11, projectName: '测试区域' }],
    [{ projectId: 10, projectName: '智慧工地', accessStatus: 'DISABLED', projectRoles: [{ id: 1, roleName: '项目成员' }] }],
  );
  const joined = nodes.map((node) => (node.id === 11
    ? toggleAssignmentRole(toggleAssignmentNode(node, true), 2, true) : node));
  assert.deepEqual(assignmentChanges(joined, 'projectId'), [{ projectId: 11, operation: 'UPSERT', roleIds: [2] }]);
  assert.equal(nodes.find((node) => node.id === 10).accessStatus, 'DISABLED');
});

test('merge keeps dirty nodes across server pages and search matches roles', () => {
  const original = buildProjectMemberNodes([{ userId: 7, realName: '施工员', username: 'worker', assigned: false }])[0];
  const draft = toggleAssignmentRole(toggleAssignmentNode(original, true), 2, true);
  const merged = mergeAssignmentDrafts(
    [{ userId: 7, realName: '施工员', username: 'worker', assigned: false }],
    new Map([[7, draft]]),
  );
  assert.equal(merged[0].assigned, true);
  assert.deepEqual(merged[0].roleIds, [2]);

  const searchable = buildUserProjectNodes([{ id: 1, projectName: '综合项目' }], [{
    projectId: 1,
    projectRoles: [{ id: 2, roleName: '质量编辑员' }],
  }]);
  assert.equal(filterAssignmentNodes(searchable, '质量').length, 1);
});

test('new membership can be selected before its explicit role is chosen', () => {
  const node = buildProjectMemberNodes([{ userId: 5, assigned: false }])[0];
  assert.deepEqual(toggleAssignmentNode(node, true).roleIds, []);
});
