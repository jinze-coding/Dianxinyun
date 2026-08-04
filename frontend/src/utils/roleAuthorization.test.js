import test from 'node:test';
import assert from 'node:assert/strict';
import {
  buildPermissionActions,
  buildRoleDefinitionRequest,
  buildRoleMenuTree,
  menuNodeState,
  pageMenuAllowed,
  permissionIdsForActionKeys,
  selectedActionKeys,
  toggleActionKey,
  toggleMenuChild,
  toggleMenuNode,
} from './roleAuthorization.js';

const menus = [
  { id: 1, menuCode: 'WEB_INSPECTION', menuName: '巡检管理' },
  { id: 2, menuCode: 'MINI_INSPECTION', menuName: '巡检管理' },
  { id: 3, parentId: 1, menuCode: 'INSPECTION_LEDGER', menuName: '电箱台账' },
  { id: 4, parentId: 1, menuCode: 'INSPECTION_RECORDS', menuName: '巡检记录' },
];

test('role definition request leaves role code to the backend', () => {
  const request = buildRoleDefinitionRequest({
    roleName: ' 材料查看员 ',
    roleCode: 'SHOULD_NOT_BE_SENT',
    description: ' 只能查看材料 ',
    enabled: 1,
  });
  assert.deepEqual(request, {
    roleName: '材料查看员',
    scopeType: 'PROJECT',
    description: '只能查看材料',
    enabled: 1,
  });
  assert.equal('roleCode' in request, false);
});

test('business menu tree supports parent, child and indeterminate selection', () => {
  const tree = buildRoleMenuTree(menus, { scopeType: 'PROJECT' });
  const inspection = tree.find((node) => node.moduleCode === 'INSPECTION');
  const selected = toggleMenuNode({ node: inspection, checked: true });
  assert.deepEqual(new Set(selected.businessModuleCodes), new Set(['INSPECTION']));
  assert.deepEqual(new Set(selected.menuIds), new Set([1, 2, 3, 4]));

  const partial = toggleMenuChild({
    parent: inspection,
    child: inspection.children[0],
    checked: false,
    selectedMenuIds: selected.menuIds,
    businessModuleCodes: selected.businessModuleCodes,
  });
  const state = menuNodeState(inspection, partial.menuIds, partial.businessModuleCodes);
  assert.equal(state.checked, true);
  assert.equal(state.indeterminate, true);

  const cleared = toggleMenuChild({
    parent: inspection,
    child: inspection.children[1],
    checked: false,
    selectedMenuIds: partial.menuIds,
    businessModuleCodes: partial.businessModuleCodes,
  });
  assert.deepEqual(cleared.businessModuleCodes, []);
});

test('legacy database keeps virtual page tabs tied to parent module', () => {
  const tree = buildRoleMenuTree([], { scopeType: 'PROJECT' });
  const document = tree.find((node) => node.moduleCode === 'DOCUMENT');
  assert.equal(document.children.every((child) => child.legacy), true);
  const state = menuNodeState(document, [], ['DOCUMENT']);
  assert.deepEqual(state.childStates, [true, true]);
});

test('inspection export action expands view and technical permissions', () => {
  const permissions = [
    ['SUMMARY_EXPORT', 1], ['SUMMARY_VIEW', 2], ['inspection.export', 3], ['inspection.view', 4],
  ].map(([permissionCode, id]) => ({ id, permissionCode, permissionName: permissionCode, moduleCode: 'WEB_INSPECTION' }));
  const actions = buildPermissionActions(permissions);
  const selected = toggleActionKey(new Set(), 'inspection.summary.export', true, actions);
  assert.deepEqual(selected, new Set(['inspection.summary.export', 'inspection.summary.view']));
  assert.deepEqual(new Set(permissionIdsForActionKeys(selected, actions)), new Set([1, 2, 3, 4]));
});

test('inspection QR action includes ledger view without granting ledger management', () => {
  const permissions = [
    ['BOX_QR_MANAGE', 1], ['BOX_VIEW', 2], ['inspection.view', 3], ['inspection.manage', 4],
  ].map(([permissionCode, id]) => ({ id, permissionCode, permissionName: permissionCode, moduleCode: 'WEB_INSPECTION' }));
  const actions = buildPermissionActions(permissions);
  const selected = toggleActionKey(new Set(), 'inspection.ledger.qr', true, actions);
  assert.deepEqual(selected, new Set(['inspection.ledger.qr', 'inspection.ledger.view']));
  assert.deepEqual(new Set(permissionIdsForActionKeys(selected, actions)), new Set([1, 2, 3]));
});

test('removing a prerequisite also removes dependent actions', () => {
  const permissions = [
    ['document.view', 1], ['document.upload', 2], ['document.manage', 3],
  ].map(([permissionCode, id]) => ({ id, permissionCode, permissionName: permissionCode, moduleCode: 'WEB_DOCUMENT' }));
  const actions = buildPermissionActions(permissions);
  let selected = selectedActionKeys([1, 2, 3], actions);
  selected = toggleActionKey(selected, 'document.view', false, actions);
  assert.deepEqual(selected, new Set());
});

test('legacy parent fallback is evaluated per business module', () => {
  const codes = ['WEB_DOCUMENT', 'QUALITY_ISSUES'];
  assert.equal(pageMenuAllowed(codes, ['DOCUMENT_LIBRARY'], ['WEB_DOCUMENT']), true);
  assert.equal(pageMenuAllowed(['WEB_DOCUMENT', 'DOCUMENT_RECYCLE'], ['DOCUMENT_LIBRARY'], ['WEB_DOCUMENT']), false);
});
