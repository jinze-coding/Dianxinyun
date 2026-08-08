import test from 'node:test';
import assert from 'node:assert/strict';
import {
  buildPermissionActions,
  buildRoleDefinitionRequest,
  buildRoleMenuTree,
  isDuplicateRoleName,
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
  { id: 5, parentId: 1, menuCode: 'INSPECTION_RECTIFICATIONS', menuName: '整改闭环' },
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

test('role name duplicate check ignores surrounding spaces and letter case', () => {
  const roles = [
    { id: 1, roleName: '电工' },
    { id: 2, roleName: 'Quality Manager' },
  ];
  assert.equal(isDuplicateRoleName('  电工  ', roles), true);
  assert.equal(isDuplicateRoleName('quality manager', roles), true);
  assert.equal(isDuplicateRoleName('安全员', roles), false);
  assert.equal(isDuplicateRoleName('Quality Manager', roles, { id: 2 }), false);
});

test('business menu tree supports parent, child and indeterminate selection', () => {
  const tree = buildRoleMenuTree(menus, { scopeType: 'PROJECT' });
  const inspection = tree.find((node) => node.moduleCode === 'INSPECTION');
  const selected = toggleMenuNode({ node: inspection, checked: true });
  assert.deepEqual(new Set(selected.businessModuleCodes), new Set(['INSPECTION']));
  assert.deepEqual(new Set(selected.menuIds), new Set([1, 2, 3, 4, 5]));

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

  const stillPartial = toggleMenuChild({
    parent: inspection,
    child: inspection.children[1],
    checked: false,
    selectedMenuIds: partial.menuIds,
    businessModuleCodes: partial.businessModuleCodes,
  });
  const cleared = toggleMenuChild({
    parent: inspection,
    child: inspection.children[2],
    checked: false,
    selectedMenuIds: stillPartial.menuIds,
    businessModuleCodes: stillPartial.businessModuleCodes,
  });
  assert.deepEqual(cleared.businessModuleCodes, []);
});

test('legacy database keeps virtual page tabs tied to parent module', () => {
  const tree = buildRoleMenuTree([], { scopeType: 'PROJECT' });
  const document = tree.find((node) => node.moduleCode === 'DOCUMENT');
  assert.equal(document.children.every((child) => child.legacy), true);
  const state = menuNodeState(document, [], ['DOCUMENT']);
  assert.deepEqual(state.childStates, [true, true, true]);
});

test('site access is a Web-only internal module with one visitor page', () => {
  const tree = buildRoleMenuTree([
    { id: 20, menuCode: 'WEB_SITE_ACCESS', menuName: '场内管理' },
    { id: 21, parentId: 20, menuCode: 'SITE_VISITOR', menuName: '外访管理' },
  ], { scopeType: 'PROJECT' });
  const siteAccess = tree.find((node) => node.moduleCode === 'SITE_ACCESS');
  const selected = toggleMenuNode({ node: siteAccess, checked: true });
  assert.deepEqual(selected.businessModuleCodes, ['SITE_ACCESS']);
  assert.deepEqual(selected.menuIds, [20, 21]);
  assert.equal(siteAccess.description.includes('公开填报不纳入角色'), true);
});

test('site access manage and export actions both require visitor view', () => {
  const permissions = [
    ['site_access.view', 20], ['site_access.manage', 21], ['site_access.export', 22],
  ].map(([permissionCode, id]) => ({ id, permissionCode, permissionName: permissionCode, moduleCode: 'WEB_SITE_ACCESS' }));
  const actions = buildPermissionActions(permissions);
  const manage = toggleActionKey(new Set(), 'site_access.manage', true, actions);
  const exportVisitors = toggleActionKey(new Set(), 'site_access.export', true, actions);
  assert.deepEqual(manage, new Set(['site_access.manage', 'site_access.view']));
  assert.deepEqual(exportVisitors, new Set(['site_access.export', 'site_access.view']));
  assert.deepEqual(new Set(permissionIdsForActionKeys(manage, actions)), new Set([20, 21]));
  assert.deepEqual(new Set(permissionIdsForActionKeys(exportVisitors, actions)), new Set([20, 22]));
});

test('document menu and role actions expose seal view, manage and export independently', () => {
  const tree = buildRoleMenuTree([
    { id: 30, menuCode: 'WEB_DOCUMENT', menuName: '资料管理' },
    { id: 31, menuCode: 'MINI_DOCUMENT', menuName: '资料管理' },
    { id: 32, parentId: 30, menuCode: 'DOCUMENT_LIBRARY', menuName: '资料库' },
    { id: 33, parentId: 30, menuCode: 'DOCUMENT_SEAL', menuName: '用印申请' },
    { id: 34, parentId: 30, menuCode: 'DOCUMENT_RECYCLE', menuName: '回收站' },
  ], { scopeType: 'PROJECT' });
  const document = tree.find((node) => node.moduleCode === 'DOCUMENT');
  assert.deepEqual(document.children.map((node) => node.menuCode), [
    'DOCUMENT_LIBRARY', 'DOCUMENT_SEAL', 'DOCUMENT_RECYCLE',
  ]);

  const permissions = [
    ['seal.view', 40], ['seal.manage', 41], ['seal.export', 42],
  ].map(([permissionCode, id]) => ({ id, permissionCode, permissionName: permissionCode, moduleCode: 'WEB_DOCUMENT' }));
  const actions = buildPermissionActions(permissions);
  const manage = toggleActionKey(new Set(), 'seal.manage', true, actions);
  const exportLedger = toggleActionKey(new Set(), 'seal.export', true, actions);
  assert.deepEqual(manage, new Set(['seal.manage', 'seal.view']));
  assert.deepEqual(exportLedger, new Set(['seal.export', 'seal.view']));
  assert.deepEqual(new Set(permissionIdsForActionKeys(manage, actions)), new Set([40, 41]));
  assert.deepEqual(new Set(permissionIdsForActionKeys(exportLedger, actions)), new Set([40, 42]));
});

test('platform role system tree includes approval management', () => {
  const tree = buildRoleMenuTree([
    { id: 50, menuCode: 'WEB_SYSTEM', menuName: '系统管理' },
    { id: 51, parentId: 50, menuCode: 'SYSTEM_APPROVAL', menuName: '用印审批' },
  ], { scopeType: 'GLOBAL' });
  const system = tree.find((node) => node.menuCode === 'WEB_SYSTEM');
  assert.deepEqual(system.children.map((node) => node.menuCode), ['SYSTEM_APPROVAL']);
});

test('project roles never receive the retired system management tree', () => {
  const systemMenus = [
    { id: 10, menuCode: 'WEB_SYSTEM', menuName: '系统管理' },
    { id: 11, parentId: 10, menuCode: 'SYSTEM_USER', menuName: '用户管理' },
    { id: 12, parentId: 10, menuCode: 'SYSTEM_PROJECT', menuName: '项目成员与权限' },
  ];
  const tree = buildRoleMenuTree(systemMenus, {
    scopeType: 'PROJECT',
    roleCode: 'PROJECT_MANAGER',
    projectManagerRole: 1,
  });
  assert.equal(tree.some((node) => node.menuCode === 'WEB_SYSTEM'), false);
});

test('platform role system tree excludes the retired project member page', () => {
  const systemMenus = [
    { id: 10, menuCode: 'WEB_SYSTEM', menuName: '系统管理' },
    { id: 11, parentId: 10, menuCode: 'SYSTEM_USER', menuName: '用户管理' },
    { id: 12, parentId: 10, menuCode: 'SYSTEM_PROJECT', menuName: '项目成员与权限' },
  ];
  const systemRoot = buildRoleMenuTree(systemMenus, {
    scopeType: 'GLOBAL',
    roleCode: 'PLATFORM_ADMIN',
  }).find((node) => node.menuCode === 'WEB_SYSTEM');
  assert.deepEqual(systemRoot.children.map((node) => node.menuCode), ['SYSTEM_USER']);
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

test('inspection rectification actions grant only their stable operation permissions and view', () => {
  const permissions = [
    ['inspection.rectify', 1], ['inspection.review', 2], ['inspection.view', 3], ['inspection.manage', 4],
  ].map(([permissionCode, id]) => ({ id, permissionCode, permissionName: permissionCode, moduleCode: 'WEB_INSPECTION' }));
  const actions = buildPermissionActions(permissions);
  const electrician = toggleActionKey(new Set(), 'inspection.rectify', true, actions);
  const safetyOfficer = toggleActionKey(new Set(), 'inspection.review', true, actions);
  assert.deepEqual(new Set(permissionIdsForActionKeys(electrician, actions)), new Set([1, 3]));
  assert.deepEqual(new Set(permissionIdsForActionKeys(safetyOfficer, actions)), new Set([2, 3]));
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
