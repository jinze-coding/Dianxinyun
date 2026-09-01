import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const pageSource = readFileSync(new URL('./index.jsx', import.meta.url), 'utf8');
const styleSource = readFileSync(new URL('./index.css', import.meta.url), 'utf8');
const authorizationSource = readFileSync(new URL('../../utils/roleAuthorization.js', import.meta.url), 'utf8');

test('permission dialog blocks an unsafe full-table save and routes back to menu assignment', () => {
  assert.match(pageSource, /permissionSelectionIssues\(\{/);
  assert.match(pageSource, /const blockingIssues = \[\.\.\.selectionIssues\.hiddenActions, \.\.\.selectionIssues\.orphanPermissions\]/);
  assert.match(pageSource, /blockingIssues\.length > 0/);
  assert.match(pageSource, /为避免整表保存时静默清除，当前禁止保存/);
  assert.match(pageSource, /打开菜单分配/);
  assert.match(pageSource, /onOpenMenus=\{\(\) => \{ setRolePermissionDialog\(null\); setRoleMenuDialog\(rolePermissionDialog\); \}\}/);
});

test('menu dialog requires an explicit second confirmation before clearing hidden actions', () => {
  assert.match(pageSource, /const \[removalImpact, setRemovalImpact\] = useState\(\[\]\)/);
  assert.match(pageSource, /permissionSelectionIssues\(\{/);
  assert.match(pageSource, /确认保存并清理 \$\{removalImpact\.length\} 项/);
  assert.match(pageSource, /保存后将清理以下操作权限/);
  assert.match(pageSource, /selectedPermissionIds=\{rolePermissionIdsFor\(roleMenuDialog\)\}/);
  assert.match(styleSource, /\.system-authorization-warning/);
});

test('standard authorization catalog includes circulation and edge pages with a visible health status', () => {
  assert.match(authorizationSource, /menuCode: 'DOCUMENT_CIRCULATION', label: '图纸收发'/);
  assert.match(authorizationSource, /menuCode: 'INSPECTION_EDGE', label: '临边巡检'/);
  assert.match(authorizationSource, /const SYSTEM_MENU_ORDER = \[/);
  assert.match(authorizationSource, /const SYSTEM_PERMISSION_CODES = \[/);
  assert.match(authorizationSource, /group: '巡检管理 · 电箱巡检'/);
  assert.match(authorizationSource, /group: '巡检管理 · 临边巡检'/);
  assert.match(authorizationSource, /primaryCodes: \['EDGE_INSPECTION_EXPORT'\]/);
  assert.doesNotMatch(authorizationSource, /巡检管理 · 共用统计与导出/);
  assert.match(pageSource, /authorizationCatalogStatus\(menus, permissions\)/);
  assert.match(pageSource, /业务授权目录一致性/);
  assert.match(styleSource, /\.system-catalog-health/);
});

test('menu assignment renders electric-box and edge inspection as separate visual groups', () => {
  assert.match(authorizationSource, /displayGroups: \[/);
  assert.match(authorizationSource, /key: 'ELECTRIC_BOX_INSPECTION'/);
  assert.match(authorizationSource, /key: 'EDGE_INSPECTION'/);
  assert.match(pageSource, /menu-assignment-groups/);
  assert.match(pageSource, /menuDisplayGroupState\(node, group, menuIds, moduleCodes\)/);
  assert.match(pageSource, /toggleMenuDisplayGroup\(\{/);
  assert.match(styleSource, /\.system-tree-row\.menu-group-row/);
});

test('roles with menus but no operation permissions are marked for manual completion', () => {
  assert.match(pageSource, /const authorizationPending = roleMenus\.length > 0 && rolePermissions\.length === 0/);
  assert.match(pageSource, /authorizationPending && <em>授权待完善<\/em>/);
  assert.match(styleSource, /\.system-role-permission-status em/);
});
