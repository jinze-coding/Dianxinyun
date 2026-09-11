import test from 'node:test';
import assert from 'node:assert/strict';
import {
  authorizationCatalogStatus,
  buildPermissionActions,
  buildPermissionActionTree,
  buildRoleDefinitionRequest,
  buildRoleMenuTree,
  isDuplicateRoleName,
  menuDisplayGroupState,
  menuNodeState,
  missingMenuCatalogCodes,
  pageMenuAllowed,
  permissionSelectionIssues,
  permissionIdsForActionKeys,
  selectedActionKeys,
  selectedLogicalMenuCodes,
  toggleActionKey,
  toggleMenuChild,
  toggleMenuDisplayGroup,
  toggleMenuNode,
} from './roleAuthorization.js';

test('form export authorization does not grant all-application viewing or ledger export', () => {
  const permissions = [
    ['seal.view', 40], ['seal.export', 42], ['seal.application.export', 43],
  ].map(([permissionCode, id]) => ({ id, permissionCode, enabled: 1 }));
  const actions = buildPermissionActions(permissions);
  const selected = toggleActionKey(new Set(), 'seal.application.export', true, actions);
  assert.deepEqual(selected, new Set(['seal.application.export']));
  assert.deepEqual(permissionIdsForActionKeys(selected, actions), [43]);
});

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

test('inspection menu assignment exposes two display groups while preserving flat page ids', () => {
  const tree = buildRoleMenuTree([
    ...menus,
    { id: 6, parentId: 1, menuCode: 'INSPECTION_EDGE', menuName: '临边巡检' },
  ], { scopeType: 'PROJECT' });
  const inspection = tree.find((node) => node.moduleCode === 'INSPECTION');

  assert.deepEqual(inspection.children.map((child) => child.menuCode), [
    'INSPECTION_LEDGER', 'INSPECTION_RECORDS', 'INSPECTION_RECTIFICATIONS', 'INSPECTION_EDGE',
  ]);
  assert.deepEqual(inspection.displayGroups.map((group) => group.label), ['电箱巡检', '临边巡检']);
  assert.deepEqual(inspection.displayGroups[0].children.map((child) => child.menuCode), [
    'INSPECTION_LEDGER', 'INSPECTION_RECORDS', 'INSPECTION_RECTIFICATIONS',
  ]);
  assert.equal(inspection.displayGroups[1].direct, true);
  assert.deepEqual(inspection.displayGroups[1].children.map((child) => child.menuCode), ['INSPECTION_EDGE']);
});

test('inspection display groups select electric and edge menus independently', () => {
  const tree = buildRoleMenuTree([
    ...menus,
    { id: 6, parentId: 1, menuCode: 'INSPECTION_EDGE', menuName: '临边巡检' },
  ], { scopeType: 'PROJECT' });
  const inspection = tree.find((node) => node.moduleCode === 'INSPECTION');
  const [electricGroup, edgeGroup] = inspection.displayGroups;

  const electric = toggleMenuDisplayGroup({ parent: inspection, group: electricGroup, checked: true });
  assert.deepEqual(new Set(electric.menuIds), new Set([1, 2, 3, 4, 5]));
  assert.deepEqual(electric.businessModuleCodes, ['INSPECTION']);
  assert.deepEqual(menuDisplayGroupState(inspection, electricGroup, electric.menuIds, electric.businessModuleCodes), {
    checked: true,
    indeterminate: false,
    disabled: false,
    childStates: [true, true, true],
  });
  assert.equal(menuDisplayGroupState(inspection, edgeGroup, electric.menuIds, electric.businessModuleCodes).checked, false);

  const edge = toggleMenuDisplayGroup({
    parent: inspection,
    group: edgeGroup,
    checked: true,
    selectedMenuIds: electric.menuIds,
    businessModuleCodes: electric.businessModuleCodes,
  });
  assert.deepEqual(new Set(edge.menuIds), new Set([1, 2, 3, 4, 5, 6]));

  const withoutElectric = toggleMenuDisplayGroup({
    parent: inspection,
    group: electricGroup,
    checked: false,
    selectedMenuIds: edge.menuIds,
    businessModuleCodes: edge.businessModuleCodes,
  });
  assert.deepEqual(new Set(withoutElectric.menuIds), new Set([1, 2, 6]));
  assert.deepEqual(withoutElectric.businessModuleCodes, ['INSPECTION']);
  assert.equal(menuDisplayGroupState(inspection, edgeGroup, withoutElectric.menuIds, withoutElectric.businessModuleCodes).checked, true);
});

test('legacy database keeps virtual page tabs tied to parent module', () => {
  const tree = buildRoleMenuTree([], { scopeType: 'PROJECT' });
  const document = tree.find((node) => node.moduleCode === 'DOCUMENT');
  assert.equal(document.children.every((child) => child.legacy), true);
  const state = menuNodeState(document, [], ['DOCUMENT']);
  assert.deepEqual(state.childStates, [true, true, true, true]);
});

test('partially migrated menu catalog does not pretend missing pages are legacy tabs', () => {
  const tree = buildRoleMenuTree([
    { id: 30, menuCode: 'WEB_DOCUMENT', menuName: '资料管理' },
    { id: 31, menuCode: 'MINI_DOCUMENT', menuName: '资料管理' },
    { id: 33, parentId: 30, menuCode: 'DOCUMENT_SEAL', menuName: '用印申请' },
  ], { scopeType: 'PROJECT' });
  const document = tree.find((node) => node.moduleCode === 'DOCUMENT');
  const state = menuNodeState(document, [30, 31, 33], ['DOCUMENT']);

  assert.deepEqual(state.childStates, [false, true, false, false]);
  assert.deepEqual(missingMenuCatalogCodes(tree), [
    'DOCUMENT_LIBRARY', 'DOCUMENT_CIRCULATION', 'DOCUMENT_RECYCLE',
  ]);
  assert.deepEqual(selectedLogicalMenuCodes(tree, [30, 31, 33], ['DOCUMENT']), new Set([
    'DOCUMENT', 'DOCUMENT_SEAL',
  ]));

  const actions = buildPermissionActions([
    { id: 40, permissionCode: 'document.view', permissionName: '查看资料', moduleCode: 'WEB_DOCUMENT' },
    { id: 41, permissionCode: 'seal.view', permissionName: '查看用印', moduleCode: 'WEB_DOCUMENT' },
  ]);
  const permissionTree = buildPermissionActionTree(
    tree,
    actions,
    selectedLogicalMenuCodes(tree, [30, 31, 33], ['DOCUMENT']),
  );
  assert.deepEqual(permissionTree.flatMap((node) => node.items.map((item) => item.key)), ['seal.view']);
});

test('permission actions follow assigned first and second level menu hierarchy', () => {
  const tree = buildRoleMenuTree(menus, { scopeType: 'PROJECT' });
  const selectedMenuIds = menus.map((menu) => menu.id);
  const logicalCodes = selectedLogicalMenuCodes(tree, selectedMenuIds, ['INSPECTION']);
  const permissions = [
    ['BOX_VIEW', 1], ['inspection.view', 2],
    ['INSPECTION_RECORD_VIEW', 3], ['INSPECTION_DAILY_SUBMIT', 4], ['inspection.submit', 5],
    ['inspection.rectify', 6], ['inspection.review', 7],
  ].map(([permissionCode, id]) => ({ id, permissionCode, permissionName: permissionCode, moduleCode: 'WEB_INSPECTION' }));

  const permissionTree = buildPermissionActionTree(tree, buildPermissionActions(permissions), logicalCodes);
  const inspection = permissionTree.find((node) => node.label === '巡检管理');

  assert.deepEqual(inspection.groups.map((group) => group.label), ['电箱巡检']);
  assert.deepEqual(inspection.groups[0].items.map((item) => item.label), [
    '查看电箱台账', '提交电箱日检', '查看电箱巡检记录',
    '提交分配给自己的电箱整改', '复查、退回和改派电箱整改',
  ]);
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

test('document menu and role actions expose seal and circulation permissions independently', () => {
  const tree = buildRoleMenuTree([
    { id: 30, menuCode: 'WEB_DOCUMENT', menuName: '资料管理' },
    { id: 31, menuCode: 'MINI_DOCUMENT', menuName: '资料管理' },
    { id: 32, parentId: 30, menuCode: 'DOCUMENT_LIBRARY', menuName: '资料库' },
    { id: 33, parentId: 30, menuCode: 'DOCUMENT_SEAL', menuName: '用印申请' },
    { id: 34, parentId: 30, menuCode: 'DOCUMENT_RECYCLE', menuName: '回收站' },
    { id: 35, parentId: 30, menuCode: 'DOCUMENT_CIRCULATION', menuName: '图纸收发' },
  ], { scopeType: 'PROJECT' });
  const document = tree.find((node) => node.moduleCode === 'DOCUMENT');
  assert.deepEqual(document.children.map((node) => node.menuCode), [
    'DOCUMENT_LIBRARY', 'DOCUMENT_SEAL', 'DOCUMENT_CIRCULATION', 'DOCUMENT_RECYCLE',
  ]);

  const permissions = [
    ['document.view', 47],
    ['seal.view', 40], ['seal.manage', 41], ['seal.export', 42],
    ['document.circulation.view', 43], ['document.receive', 44],
    ['document.issue', 45], ['document.circulation.export', 46],
  ].map(([permissionCode, id]) => ({ id, permissionCode, permissionName: permissionCode, moduleCode: 'WEB_DOCUMENT' }));
  const actions = buildPermissionActions(permissions);
  const manage = toggleActionKey(new Set(), 'seal.manage', true, actions);
  const exportLedger = toggleActionKey(new Set(), 'seal.export', true, actions);
  const receive = toggleActionKey(new Set(), 'document.receive', true, actions);
  const circulationExport = toggleActionKey(new Set(), 'document.circulation.export', true, actions);
  assert.deepEqual(manage, new Set(['seal.manage', 'seal.view']));
  assert.deepEqual(exportLedger, new Set(['seal.export', 'seal.view']));
  assert.deepEqual(new Set(permissionIdsForActionKeys(manage, actions)), new Set([40, 41]));
  assert.deepEqual(new Set(permissionIdsForActionKeys(exportLedger, actions)), new Set([40, 42]));
  assert.deepEqual(receive, new Set(['document.receive', 'document.circulation.view', 'document.view']));
  assert.deepEqual(circulationExport,
    new Set(['document.circulation.export', 'document.circulation.view', 'document.view']));
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

test('edge inspection uses an independent menu and six fixed operation permissions', () => {
  const tree = buildRoleMenuTree([
    { id: 60, menuCode: 'WEB_INSPECTION', menuName: '巡检管理' },
    { id: 61, parentId: 60, menuCode: 'INSPECTION_LEDGER', menuName: '电箱台账' },
    { id: 62, parentId: 60, menuCode: 'INSPECTION_EDGE', menuName: '临边巡检' },
  ], { scopeType: 'PROJECT' });
  const inspection = tree.find((node) => node.moduleCode === 'INSPECTION');
  assert.equal(inspection.children.some((node) => node.menuCode === 'INSPECTION_EDGE' && !node.unavailable), true);

  const permissions = [
    ['EDGE_INSPECTION_VIEW', 70], ['EDGE_INSPECTION_MANAGE', 71], ['EDGE_INSPECTION_SUBMIT', 72],
    ['EDGE_INSPECTION_RECTIFY', 73], ['EDGE_INSPECTION_REVIEW', 74], ['EDGE_INSPECTION_EXPORT', 75],
  ].map(([permissionCode, id]) => ({ id, permissionCode, permissionName: permissionCode, moduleCode: 'WEB_INSPECTION' }));
  const actions = buildPermissionActions(permissions);
  const logicalCodes = selectedLogicalMenuCodes(tree, [60, 61, 62], ['INSPECTION']);
  const permissionTree = buildPermissionActionTree(tree, actions, logicalCodes);
  const edgeGroup = permissionTree.find((node) => node.label === '巡检管理')?.groups
    .find((group) => group.label === '临边巡检');
  assert.deepEqual(edgeGroup?.items.map((action) => action.key), [
    'inspection.edge.view',
    'inspection.edge.manage',
    'inspection.edge.submit',
    'inspection.edge.rectify',
    'inspection.edge.review',
    'inspection.edge.export',
  ]);
  const selected = toggleActionKey(new Set(), 'inspection.edge.review', true, actions);
  assert.deepEqual(selected, new Set(['inspection.edge.review', 'inspection.edge.view']));
  assert.deepEqual(new Set(permissionIdsForActionKeys(selected, actions)), new Set([70, 74]));
  const exportReport = toggleActionKey(new Set(), 'inspection.edge.export', true, actions);
  assert.deepEqual(exportReport, new Set(['inspection.edge.export', 'inspection.edge.view']));
  assert.deepEqual(new Set(permissionIdsForActionKeys(exportReport, actions)), new Set([70, 75]));
});

test('inspection permissions render as two explicit electric-box and edge groups without shared actions', () => {
  const tree = buildRoleMenuTree([
    { id: 60, menuCode: 'WEB_INSPECTION', menuName: '巡检管理' },
    { id: 61, menuCode: 'MINI_INSPECTION', menuName: '巡检管理' },
    { id: 62, parentId: 60, menuCode: 'INSPECTION_LEDGER', menuName: '电箱台账' },
    { id: 63, parentId: 60, menuCode: 'INSPECTION_RECORDS', menuName: '巡检记录' },
    { id: 64, parentId: 60, menuCode: 'INSPECTION_RECTIFICATIONS', menuName: '整改闭环' },
    { id: 65, parentId: 60, menuCode: 'INSPECTION_EDGE', menuName: '临边巡检' },
  ], { scopeType: 'PROJECT' });
  const permissions = [
    ['BOX_VIEW', 70], ['BOX_MANAGE', 71], ['BOX_QR_MANAGE', 72], ['BOX_PUBLIC_ACCESS', 73],
    ['INSPECTION_DAILY_SUBMIT', 74], ['INSPECTION_RECORD_VIEW', 75],
    ['SUMMARY_VIEW', 76], ['SUMMARY_EXPORT', 77],
    ['inspection.view', 78], ['inspection.manage', 79], ['inspection.submit', 80],
    ['inspection.export', 81], ['inspection.rectify', 82], ['inspection.review', 83],
    ['EDGE_INSPECTION_VIEW', 90], ['EDGE_INSPECTION_MANAGE', 91], ['EDGE_INSPECTION_SUBMIT', 92],
    ['EDGE_INSPECTION_RECTIFY', 93], ['EDGE_INSPECTION_REVIEW', 94], ['EDGE_INSPECTION_EXPORT', 95],
  ].map(([permissionCode, id]) => ({
    id, permissionCode, permissionName: permissionCode, moduleCode: 'WEB_INSPECTION',
  }));
  const actions = buildPermissionActions(permissions);
  const logicalCodes = selectedLogicalMenuCodes(tree, [60, 61, 62, 63, 64, 65], ['INSPECTION']);
  const inspection = buildPermissionActionTree(tree, actions, logicalCodes)
    .find((node) => node.label === '巡检管理');
  assert.deepEqual(inspection.groups.map((group) => group.label), ['电箱巡检', '临边巡检']);
  assert.equal(inspection.groups.some((group) => group.label.includes('共用')), false);

  const electricActions = actions.filter((action) => action.group === '巡检管理 · 电箱巡检');
  const edgeActions = actions.filter((action) => action.group === '巡检管理 · 临边巡检');
  assert.equal(electricActions.every((action) => action.label.includes('电箱')), true);
  assert.equal(edgeActions.every((action) => action.label.includes('临边')), true);
  const electricCodes = new Set(electricActions.flatMap((action) => action.codes));
  const edgeCodes = new Set(edgeActions.flatMap((action) => action.codes));
  assert.deepEqual([...edgeCodes].filter((code) => electricCodes.has(code)), []);
  assert.deepEqual(edgeCodes, new Set([
    'EDGE_INSPECTION_VIEW', 'EDGE_INSPECTION_MANAGE', 'EDGE_INSPECTION_SUBMIT',
    'EDGE_INSPECTION_RECTIFY', 'EDGE_INSPECTION_REVIEW', 'EDGE_INSPECTION_EXPORT',
  ]));
});

test('selected edge permissions are blocked when their page menu is missing', () => {
  const menuCatalog = [
    { id: 60, menuCode: 'WEB_INSPECTION', menuName: '巡检管理' },
    { id: 61, menuCode: 'MINI_INSPECTION', menuName: '巡检管理' },
    { id: 62, parentId: 60, menuCode: 'INSPECTION_RECORDS', menuName: '巡检记录' },
    { id: 63, parentId: 60, menuCode: 'INSPECTION_EDGE', menuName: '临边巡检' },
  ];
  const permissions = [
    ['EDGE_INSPECTION_VIEW', 70], ['EDGE_INSPECTION_MANAGE', 71], ['EDGE_INSPECTION_EXPORT', 75],
  ].map(([permissionCode, id]) => ({
    id, permissionCode, permissionName: permissionCode, moduleCode: 'WEB_INSPECTION',
  }));
  const tree = buildRoleMenuTree(menuCatalog, { scopeType: 'PROJECT' });
  const actions = buildPermissionActions(permissions);
  const missingEdge = permissionSelectionIssues({
    selectedPermissionIds: [70, 71, 75],
    actions,
    logicalMenuCodes: selectedLogicalMenuCodes(tree, [60, 61, 62], ['INSPECTION']),
    permissions,
    menuTree: tree,
  });
  assert.deepEqual(missingEdge.hiddenActions.map((action) => action.key), [
    'inspection.edge.view', 'inspection.edge.manage', 'inspection.edge.export',
  ]);
  assert.deepEqual(missingEdge.hiddenActions[0].missingMenuLabels, ['临边巡检']);
  assert.deepEqual(missingEdge.orphanPermissions, []);

  const assignedEdge = permissionSelectionIssues({
    selectedPermissionIds: [70, 71, 75],
    actions,
    logicalMenuCodes: selectedLogicalMenuCodes(tree, [60, 61, 62, 63], ['INSPECTION']),
    permissions,
    menuTree: tree,
  });
  assert.deepEqual(assignedEdge.hiddenActions, []);
  assert.deepEqual(assignedEdge.orphanPermissions, []);
});

test('edge permissions do not absorb legacy electric-box technical permissions', () => {
  const menuCatalog = [
    { id: 60, menuCode: 'WEB_INSPECTION', menuName: '巡检管理' },
    { id: 61, menuCode: 'MINI_INSPECTION', menuName: '巡检管理' },
    { id: 63, parentId: 60, menuCode: 'INSPECTION_EDGE', menuName: '临边巡检' },
  ];
  const permissions = [
    ['EDGE_INSPECTION_VIEW', 70],
    ['EDGE_INSPECTION_SUBMIT', 72],
    ['EDGE_INSPECTION_RECTIFY', 73],
    ['inspection.view', 80],
    ['inspection.submit', 81],
    ['inspection.rectify', 82],
  ].map(([permissionCode, id]) => ({
    id, permissionCode, permissionName: permissionCode, moduleCode: 'WEB_INSPECTION',
  }));
  const tree = buildRoleMenuTree(menuCatalog, { scopeType: 'PROJECT' });
  const actions = buildPermissionActions(permissions);
  const issues = permissionSelectionIssues({
    selectedPermissionIds: permissions.map((permission) => permission.id),
    actions,
    logicalMenuCodes: selectedLogicalMenuCodes(tree, [60, 61, 63], ['INSPECTION']),
    permissions,
    menuTree: tree,
  });

  assert.deepEqual(issues.selectedActions.map((action) => action.key), [
    'inspection.rectify', 'inspection.edge.view', 'inspection.edge.submit', 'inspection.edge.rectify',
  ]);
  assert.deepEqual(issues.hiddenActions.map((action) => action.key), ['inspection.rectify']);
  assert.deepEqual(issues.hiddenActions[0].missingMenuLabels, ['电箱整改闭环']);
  assert.deepEqual(issues.orphanPermissions.map((permission) => permission.permissionCode), ['inspection.submit']);
  assert.deepEqual(new Set(permissionIdsForActionKeys(
    new Set(['inspection.edge.view', 'inspection.edge.submit', 'inspection.edge.rectify']),
    actions,
  )), new Set([70, 72, 73]));
});

test('circulation view dependency does not masquerade as a hidden document-library action', () => {
  const menuCatalog = [
    { id: 30, menuCode: 'WEB_DOCUMENT', menuName: '资料管理' },
    { id: 31, menuCode: 'MINI_DOCUMENT', menuName: '资料管理' },
    { id: 32, parentId: 30, menuCode: 'DOCUMENT_LIBRARY', menuName: '资料库' },
    { id: 35, parentId: 30, menuCode: 'DOCUMENT_CIRCULATION', menuName: '图纸收发' },
  ];
  const permissions = [
    { id: 47, permissionCode: 'document.view', permissionName: '查看资料', moduleCode: 'WEB_DOCUMENT' },
    { id: 43, permissionCode: 'document.circulation.view', permissionName: '查看图纸收发', moduleCode: 'WEB_DOCUMENT' },
  ];
  const tree = buildRoleMenuTree(menuCatalog, { scopeType: 'PROJECT' });
  const actions = buildPermissionActions(permissions);
  const issues = permissionSelectionIssues({
    selectedPermissionIds: [43, 47],
    actions,
    logicalMenuCodes: selectedLogicalMenuCodes(tree, [30, 31, 35], ['DOCUMENT']),
    permissions,
    menuTree: tree,
  });

  assert.deepEqual(issues.selectedActions.map((action) => action.key), ['document.circulation.view']);
  assert.deepEqual(issues.hiddenActions, []);
  assert.deepEqual(issues.orphanPermissions, []);
});

test('standalone dependency permission is reported as an orphan instead of being silently removed', () => {
  const permissions = [
    { id: 90, permissionCode: 'inspection.view', permissionName: '巡检基础查看', moduleCode: 'WEB_INSPECTION' },
  ];
  const issues = permissionSelectionIssues({
    selectedPermissionIds: [90],
    actions: buildPermissionActions(permissions),
    logicalMenuCodes: new Set(['INSPECTION_RECORDS']),
    permissions,
    menuTree: buildRoleMenuTree(menus, { scopeType: 'PROJECT' }),
  });
  assert.deepEqual(issues.hiddenActions, []);
  assert.deepEqual(issues.orphanPermissions.map((permission) => permission.permissionCode), ['inspection.view']);
});

test('authorization catalog status reports missing edge and circulation pages and disabled permissions', () => {
  const status = authorizationCatalogStatus([
    { id: 1, menuCode: 'WEB_SITE_ACCESS', enabled: 1 },
    { id: 2, menuCode: 'SITE_VISITOR', enabled: 1 },
    { id: 3, menuCode: 'WEB_DOCUMENT', enabled: 1 },
    { id: 4, menuCode: 'MINI_DOCUMENT', enabled: 1 },
    { id: 5, menuCode: 'DOCUMENT_LIBRARY', enabled: 1 },
    { id: 6, menuCode: 'DOCUMENT_SEAL', enabled: 1 },
    { id: 7, menuCode: 'DOCUMENT_RECYCLE', enabled: 1 },
    { id: 8, menuCode: 'WEB_INSPECTION', enabled: 1 },
    { id: 9, menuCode: 'MINI_INSPECTION', enabled: 1 },
    { id: 10, menuCode: 'INSPECTION_LEDGER', enabled: 1 },
    { id: 11, menuCode: 'INSPECTION_RECORDS', enabled: 1 },
    { id: 12, menuCode: 'INSPECTION_RECTIFICATIONS', enabled: 1 },
    { id: 13, menuCode: 'WEB_QUALITY', enabled: 1 },
    { id: 14, menuCode: 'MINI_QUALITY', enabled: 1 },
    { id: 15, menuCode: 'QUALITY_ISSUES', enabled: 1 },
    { id: 16, menuCode: 'QUALITY_DOCUMENTS', enabled: 1 },
  ], [
    { id: 30, permissionCode: 'EDGE_INSPECTION_VIEW', enabled: 0 },
  ]);
  assert.equal(status.healthy, false);
  assert.equal(status.missingMenuCodes.includes('DOCUMENT_CIRCULATION'), true);
  assert.equal(status.missingMenuCodes.includes('INSPECTION_EDGE'), true);
  assert.deepEqual(status.disabledPermissionCodes, ['EDGE_INSPECTION_VIEW']);
  assert.equal(status.missingPermissionCodes.includes('EDGE_INSPECTION_MANAGE'), true);
  assert.equal(status.missingPermissionCodes.includes('EDGE_INSPECTION_EXPORT'), true);
});

test('authorization catalog status covers current system menus and permissions but excludes retired project authorization', () => {
  const status = authorizationCatalogStatus([], []);
  assert.equal(status.missingMenuCodes.includes('WEB_SYSTEM'), true);
  assert.deepEqual(status.missingMenuCodes.filter((code) => code.startsWith('SYSTEM_')), [
    'SYSTEM_REGISTRATION',
    'SYSTEM_USER',
    'SYSTEM_ROLE',
    'SYSTEM_MENU',
    'SYSTEM_WECHAT',
    'SYSTEM_AUDIT',
    'SYSTEM_APPROVAL',
  ]);
  assert.equal(status.missingMenuCodes.includes('SYSTEM_PROJECT'), false);
  assert.equal(status.missingPermissionCodes.includes('SYSTEM.USER.VIEW'), true);
  assert.equal(status.missingPermissionCodes.includes('SYSTEM.USER.RESET_PASSWORD'), true);
  assert.equal(status.missingPermissionCodes.includes('SYSTEM.APPROVAL.MANAGE'), true);
  assert.equal(status.missingPermissionCodes.includes('SYSTEM.PROJECT.MANAGE'), false);
  assert.equal(status.missingPermissionCodes.includes('PROJECT.MEMBER.MANAGE'), false);
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
