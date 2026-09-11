export const BUSINESS_MENU_DEFINITIONS = [
  {
    moduleCode: 'SITE_ACCESS',
    label: '场内管理',
    description: '只控制 Web 内部管理；小程序公开填报不纳入角色',
    backingMenuCodes: ['WEB_SITE_ACCESS'],
    pages: [
      { menuCode: 'SITE_VISITOR', label: '外访管理' },
    ],
  },
  {
    moduleCode: 'DOCUMENT',
    label: '资料管理',
    description: 'Web 与小程序共用模块开关',
    backingMenuCodes: ['WEB_DOCUMENT', 'MINI_DOCUMENT'],
    pages: [
      { menuCode: 'DOCUMENT_LIBRARY', label: '资料库' },
      { menuCode: 'DOCUMENT_SEAL', label: '用印申请' },
      { menuCode: 'DOCUMENT_CIRCULATION', label: '图纸收发' },
      { menuCode: 'DOCUMENT_RECYCLE', label: '回收站' },
    ],
  },
  {
    moduleCode: 'INSPECTION',
    label: '巡检管理',
    description: 'Web 与小程序共用模块开关',
    backingMenuCodes: ['WEB_INSPECTION', 'MINI_INSPECTION'],
    pages: [
      { menuCode: 'INSPECTION_LEDGER', label: '电箱台账' },
      { menuCode: 'INSPECTION_RECORDS', label: '电箱巡检记录' },
      { menuCode: 'INSPECTION_RECTIFICATIONS', label: '电箱整改闭环' },
      { menuCode: 'INSPECTION_EDGE', label: '临边巡检' },
    ],
    displayGroups: [
      {
        key: 'ELECTRIC_BOX_INSPECTION',
        label: '电箱巡检',
        description: '电箱台账、巡检记录与整改闭环',
        menuCodes: ['INSPECTION_LEDGER', 'INSPECTION_RECORDS', 'INSPECTION_RECTIFICATIONS'],
      },
      {
        key: 'EDGE_INSPECTION',
        label: '临边巡检',
        description: '临边点位、任务、记录与整改闭环',
        menuCodes: ['INSPECTION_EDGE'],
        direct: true,
      },
    ],
  },
  {
    moduleCode: 'QUALITY',
    label: '质量周检',
    description: 'Web 与小程序共用模块开关',
    backingMenuCodes: ['WEB_QUALITY', 'MINI_QUALITY'],
    pages: [
      { menuCode: 'QUALITY_ISSUES', label: '周检与整改闭环' },
      { menuCode: 'QUALITY_DOCUMENTS', label: '质量资料' },
    ],
  },
];

const SYSTEM_MENU_ORDER = [
  'SYSTEM_REGISTRATION',
  'SYSTEM_USER',
  'SYSTEM_ROLE',
  'SYSTEM_MENU',
  'SYSTEM_WECHAT',
  'SYSTEM_AUDIT',
  'SYSTEM_APPROVAL',
];

const SYSTEM_PERMISSION_CODES = [
  'system.registration.review',
  'system.user.view',
  'system.user.manage',
  'system.user.status',
  'system.user.reset_password',
  'system.role.manage',
  'system.menu.manage',
  'system.wechat.manage',
  'system.audit.view',
  'system.approval.view',
  'system.approval.manage',
];

const ACTION_DEFINITIONS = [
  { key: 'site_access.view', label: '查看完整外访信息', group: '场内管理 · 外访管理', menuCodes: ['SITE_VISITOR'], primaryCodes: ['site_access.view'], codes: ['site_access.view'] },
  { key: 'site_access.manage', label: '创建、修改、作废及生成小程序码', group: '场内管理 · 外访管理', menuCodes: ['SITE_VISITOR'], primaryCodes: ['site_access.manage'], codes: ['site_access.manage', 'site_access.view'], requiresActions: ['site_access.view'] },
  { key: 'site_access.export', label: '导出完整外访人员信息', group: '场内管理 · 外访管理', menuCodes: ['SITE_VISITOR'], primaryCodes: ['site_access.export'], codes: ['site_access.export', 'site_access.view'], requiresActions: ['site_access.view'] },

  { key: 'document.view', label: '查看资料', group: '资料管理 · 通用操作', menuCodes: ['DOCUMENT_LIBRARY', 'DOCUMENT_RECYCLE'], primaryCodes: ['document.view'], codes: ['document.view'] },
  { key: 'document.upload', label: '上传资料及新版本', group: '资料管理 · 通用操作', menuCodes: ['DOCUMENT_LIBRARY'], primaryCodes: ['document.upload'], codes: ['document.upload', 'document.view'], requiresActions: ['document.view'] },
  { key: 'document.manage', label: '管理目录、归档和回收站', group: '资料管理 · 通用操作', menuCodes: ['DOCUMENT_LIBRARY', 'DOCUMENT_RECYCLE'], primaryCodes: ['document.manage'], codes: ['document.manage', 'document.view'], requiresActions: ['document.view'] },
  { key: 'document.circulation.view', label: '查看图纸收发记录及台账', group: '资料管理 · 图纸收发', menuCodes: ['DOCUMENT_CIRCULATION'], primaryCodes: ['document.circulation.view'], codes: ['document.circulation.view', 'document.view'], requiresActions: ['document.view'] },
  { key: 'document.receive', label: '登记收文、匹配版本并发布', group: '资料管理 · 图纸收发', menuCodes: ['DOCUMENT_CIRCULATION'], primaryCodes: ['document.receive'], codes: ['document.receive', 'document.circulation.view', 'document.view'], requiresActions: ['document.circulation.view'] },
  { key: 'document.issue', label: '再次发放、二维码与批次作废', group: '资料管理 · 图纸收发', menuCodes: ['DOCUMENT_CIRCULATION'], primaryCodes: ['document.issue'], codes: ['document.issue', 'document.circulation.view', 'document.view'], requiresActions: ['document.circulation.view'] },
  { key: 'document.circulation.export', label: '导出图纸收发综合台账', group: '资料管理 · 图纸收发', menuCodes: ['DOCUMENT_CIRCULATION'], primaryCodes: ['document.circulation.export'], codes: ['document.circulation.export', 'document.circulation.view', 'document.view'], requiresActions: ['document.circulation.view'] },
  { key: 'seal.view', label: '查看项目全部用印申请', group: '资料管理 · 用印申请', menuCodes: ['DOCUMENT_SEAL'], primaryCodes: ['seal.view'], codes: ['seal.view'] },
  { key: 'seal.manage', label: '管理项目用印与盖章件', group: '资料管理 · 用印申请', menuCodes: ['DOCUMENT_SEAL'], primaryCodes: ['seal.manage'], codes: ['seal.manage', 'seal.view'], requiresActions: ['seal.view'] },
  { key: 'seal.export', label: '导出项目用印台账', group: '资料管理 · 用印申请', menuCodes: ['DOCUMENT_SEAL'], primaryCodes: ['seal.export'], codes: ['seal.export', 'seal.view'], requiresActions: ['seal.view'] },
  { key: 'seal.application.export', label: '导出用印申请单', group: '资料管理 · 用印申请', menuCodes: ['DOCUMENT_SEAL'], primaryCodes: ['seal.application.export'], codes: ['seal.application.export'] },

  { key: 'system.approval.view', label: '查看印章与审批配置', group: '系统管理 · 用印审批', menuCodes: ['SYSTEM_APPROVAL'], primaryCodes: ['system.approval.view'], codes: ['system.approval.view'] },
  { key: 'system.approval.manage', label: '维护印章、审批配置与二维码', group: '系统管理 · 用印审批', menuCodes: ['SYSTEM_APPROVAL'], primaryCodes: ['system.approval.manage'], codes: ['system.approval.manage'] },

  { key: 'inspection.ledger.view', label: '查看电箱台账', group: '巡检管理 · 电箱巡检', menuCodes: ['INSPECTION_LEDGER'], primaryCodes: ['BOX_VIEW'], codes: ['BOX_VIEW', 'inspection.view'] },
  { key: 'inspection.ledger.manage', label: '新增、编辑、停用和导入电箱', group: '巡检管理 · 电箱巡检', menuCodes: ['INSPECTION_LEDGER'], primaryCodes: ['BOX_MANAGE'], codes: ['BOX_MANAGE', 'BOX_VIEW', 'inspection.manage', 'inspection.view'], requiresActions: ['inspection.ledger.view'] },
  { key: 'inspection.ledger.qr', label: '管理电箱二维码与贴纸', group: '巡检管理 · 电箱巡检', menuCodes: ['INSPECTION_LEDGER'], primaryCodes: ['BOX_QR_MANAGE'], codes: ['BOX_QR_MANAGE', 'BOX_VIEW', 'inspection.view'], requiresActions: ['inspection.ledger.view'] },
  { key: 'inspection.ledger.public', label: '启停电箱外部公开访问', group: '巡检管理 · 电箱巡检', menuCodes: ['INSPECTION_LEDGER'], primaryCodes: ['BOX_PUBLIC_ACCESS'], codes: ['BOX_PUBLIC_ACCESS', 'BOX_VIEW', 'inspection.view'], requiresActions: ['inspection.ledger.view'] },
  { key: 'inspection.records.submit', label: '提交电箱日检', group: '巡检管理 · 电箱巡检', menuCodes: ['INSPECTION_RECORDS'], primaryCodes: ['INSPECTION_DAILY_SUBMIT'], codes: ['INSPECTION_DAILY_SUBMIT', 'inspection.submit'] },
  { key: 'inspection.records.view', label: '查看电箱巡检记录', group: '巡检管理 · 电箱巡检', menuCodes: ['INSPECTION_RECORDS'], primaryCodes: ['INSPECTION_RECORD_VIEW'], codes: ['INSPECTION_RECORD_VIEW', 'inspection.view'] },
  { key: 'inspection.summary.view', label: '查看电箱巡检统计', group: '巡检管理 · 电箱巡检', menuCodes: ['INSPECTION_RECORDS'], primaryCodes: ['SUMMARY_VIEW'], codes: ['SUMMARY_VIEW', 'inspection.view'] },
  { key: 'inspection.summary.export', label: '导出电箱巡检汇总', group: '巡检管理 · 电箱巡检', menuCodes: ['INSPECTION_RECORDS'], primaryCodes: ['SUMMARY_EXPORT'], codes: ['SUMMARY_EXPORT', 'SUMMARY_VIEW', 'inspection.export', 'inspection.view'], requiresActions: ['inspection.summary.view'] },
  { key: 'inspection.rectify', label: '提交分配给自己的电箱整改', group: '巡检管理 · 电箱巡检', menuCodes: ['INSPECTION_RECTIFICATIONS'], primaryCodes: ['inspection.rectify'], codes: ['inspection.rectify', 'inspection.view'] },
  { key: 'inspection.review', label: '复查、退回和改派电箱整改', group: '巡检管理 · 电箱巡检', menuCodes: ['INSPECTION_RECTIFICATIONS'], primaryCodes: ['inspection.review'], codes: ['inspection.review', 'inspection.view'] },
  { key: 'inspection.edge.view', label: '查看临边点位、记录、整改和统计', group: '巡检管理 · 临边巡检', menuCodes: ['INSPECTION_EDGE'], primaryCodes: ['EDGE_INSPECTION_VIEW'], codes: ['EDGE_INSPECTION_VIEW'] },
  { key: 'inspection.edge.manage', label: '管理临边点位、周期、取消和改派', group: '巡检管理 · 临边巡检', menuCodes: ['INSPECTION_EDGE'], primaryCodes: ['EDGE_INSPECTION_MANAGE'], codes: ['EDGE_INSPECTION_MANAGE', 'EDGE_INSPECTION_VIEW'], requiresActions: ['inspection.edge.view'] },
  { key: 'inspection.edge.submit', label: '提交分配给自己的临边巡检', group: '巡检管理 · 临边巡检', menuCodes: ['INSPECTION_EDGE'], primaryCodes: ['EDGE_INSPECTION_SUBMIT'], codes: ['EDGE_INSPECTION_SUBMIT', 'EDGE_INSPECTION_VIEW'], requiresActions: ['inspection.edge.view'] },
  { key: 'inspection.edge.rectify', label: '提交分配给自己的临边整改', group: '巡检管理 · 临边巡检', menuCodes: ['INSPECTION_EDGE'], primaryCodes: ['EDGE_INSPECTION_RECTIFY'], codes: ['EDGE_INSPECTION_RECTIFY', 'EDGE_INSPECTION_VIEW'], requiresActions: ['inspection.edge.view'] },
  { key: 'inspection.edge.review', label: '复查分配给自己的临边整改', group: '巡检管理 · 临边巡检', menuCodes: ['INSPECTION_EDGE'], primaryCodes: ['EDGE_INSPECTION_REVIEW'], codes: ['EDGE_INSPECTION_REVIEW', 'EDGE_INSPECTION_VIEW'], requiresActions: ['inspection.edge.view'] },
  { key: 'inspection.edge.export', label: '导出临边巡检含图报表', group: '巡检管理 · 临边巡检', menuCodes: ['INSPECTION_EDGE'], primaryCodes: ['EDGE_INSPECTION_EXPORT'], codes: ['EDGE_INSPECTION_EXPORT', 'EDGE_INSPECTION_VIEW'], requiresActions: ['inspection.edge.view'] },

  { key: 'quality.view', label: '查看质量周检、问题和质量资料', group: '质量周检 · 通用操作', menuCodes: ['QUALITY_ISSUES', 'QUALITY_DOCUMENTS'], primaryCodes: ['quality.view'], codes: ['quality.view'] },
  { key: 'quality.manage', label: '编辑提交周检、改派及管理质量资料', group: '质量周检 · 通用操作', menuCodes: ['QUALITY_ISSUES', 'QUALITY_DOCUMENTS'], primaryCodes: ['quality.manage'], codes: ['quality.manage', 'quality.view'], requiresActions: ['quality.view'] },
  { key: 'quality.rectify', label: '提交质量整改', group: '质量周检 · 整改闭环', menuCodes: ['QUALITY_ISSUES'], primaryCodes: ['quality.rectify'], codes: ['quality.rectify', 'quality.view'], requiresActions: ['quality.view'] },
  { key: 'quality.review', label: '复查质量问题', group: '质量周检 · 整改闭环', menuCodes: ['QUALITY_ISSUES'], primaryCodes: ['quality.review'], codes: ['quality.review', 'quality.view'], requiresActions: ['quality.view'] },
];

const normalize = (value) => String(value || '').trim().toUpperCase();
const itemId = (item) => item?.id ?? item?.menuId ?? item?.permissionId;
const menuCode = (menu) => normalize(menu?.menuCode || menu?.code);
const permissionCode = (permission) => String(permission?.permissionCode || permission?.code || '').trim();

const comparableRoleName = (value) => String(value || '').trim().toLocaleLowerCase();

export function isDuplicateRoleName(candidate, roles = [], currentRole = null) {
  const normalizedCandidate = comparableRoleName(candidate);
  if (!normalizedCandidate) return false;
  const currentRoleId = currentRole?.id ?? currentRole?.roleId;
  return roles.some((role) => {
    const roleId = role?.id ?? role?.roleId;
    if (currentRoleId != null && String(roleId) === String(currentRoleId)) return false;
    return comparableRoleName(role?.roleName || role?.name) === normalizedCandidate;
  });
}

export function buildRoleDefinitionRequest(form, role = null) {
  return {
    roleName: String(form?.roleName || '').trim(),
    scopeType: role ? (role.scopeType || role.scope || 'PROJECT') : 'PROJECT',
    description: String(form?.description || '').trim(),
    enabled: Number(form?.enabled ?? 1),
  };
}

export function buildRoleMenuTree(menus = [], role = null) {
  const byCode = new Map(menus.map((menu) => [menuCode(menu), menu]));
  const projectRole = normalize(role?.scopeType || role?.scope) === 'PROJECT';
  const businessNodes = BUSINESS_MENU_DEFINITIONS.map((definition) => {
    const backingMenus = definition.backingMenuCodes.map((code) => byCode.get(code)).filter(Boolean);
    const pageMenus = definition.pages.map((page) => byCode.get(page.menuCode));
    const hasPageCatalog = pageMenus.some(Boolean);
    const pages = definition.pages.map((page) => {
      const menu = byCode.get(page.menuCode);
      return {
        key: `menu:${page.menuCode}`,
        type: 'PAGE',
        label: page.label,
        menuCode: page.menuCode,
        menuId: itemId(menu),
        // The old parent-only catalog is compatible only when the whole module has
        // no page menu at all. Treating individual holes in a partially migrated
        // catalog as legacy makes the UI offer permissions the backend must reject.
        legacy: !menu && !hasPageCatalog,
        unavailable: !menu && hasPageCatalog,
      };
    });
    const pageByCode = new Map(pages.map((page) => [page.menuCode, page]));
    const displayGroups = (definition.displayGroups || []).map((group) => ({
      key: `menu-group:${definition.moduleCode}:${group.key}`,
      type: 'PAGE_GROUP',
      label: group.label,
      description: group.description,
      direct: Boolean(group.direct),
      children: group.menuCodes.map((code) => pageByCode.get(code)).filter(Boolean),
    }));
    return {
      key: `module:${definition.moduleCode}`,
      type: 'BUSINESS_MODULE',
      label: definition.label,
      description: definition.description,
      moduleCode: definition.moduleCode,
      backingMenuIds: backingMenus.map(itemId).filter(Boolean),
      children: pages,
      displayGroups,
    };
  });

  const systemRoot = byCode.get('WEB_SYSTEM');
  const systemChildren = SYSTEM_MENU_ORDER.map((code) => byCode.get(code)).filter(Boolean)
    .map((menu) => ({
      key: `menu:${menuCode(menu)}`,
      type: 'PAGE',
      label: menu.menuName || menu.name,
      menuCode: menuCode(menu),
      menuId: itemId(menu),
    }));
  if (!projectRole && systemRoot && systemChildren.length) {
    businessNodes.push({
      key: 'system:WEB_SYSTEM',
      type: 'SYSTEM_ROOT',
      label: systemRoot.menuName || systemRoot.name || '系统管理',
      description: 'Web 平台管理页面',
      menuCode: 'WEB_SYSTEM',
      menuId: itemId(systemRoot),
      children: systemChildren,
    });
  }
  return businessNodes;
}

export function menuNodeState(node, selectedMenuIds = [], businessModuleCodes = []) {
  const selectedIds = new Set(selectedMenuIds.map(Number));
  const selectedModules = new Set(businessModuleCodes.map(normalize));
  const childStates = (node.children || []).map((child) => {
    if (child.unavailable) return false;
    if (child.legacy) return selectedModules.has(normalize(node.moduleCode));
    return selectedIds.has(Number(child.menuId));
  });
  let checked;
  if (node.type === 'BUSINESS_MODULE') checked = selectedModules.has(normalize(node.moduleCode));
  else if (node.children?.length) checked = childStates.every(Boolean) && selectedIds.has(Number(node.menuId));
  else checked = selectedIds.has(Number(node.menuId));
  return {
    checked,
    indeterminate: childStates.some(Boolean) && !childStates.every(Boolean),
    childStates,
  };
}

export function menuDisplayGroupState(node, group, selectedMenuIds = [], businessModuleCodes = []) {
  const nodeState = menuNodeState(node, selectedMenuIds, businessModuleCodes);
  const childStates = (group.children || []).map((child) => {
    const index = (node.children || []).findIndex((item) => item.key === child.key);
    return index >= 0 ? nodeState.childStates[index] : false;
  });
  return {
    checked: childStates.length > 0 && childStates.every(Boolean),
    indeterminate: childStates.some(Boolean) && !childStates.every(Boolean),
    disabled: !(group.children || []).some((child) => !child.legacy && !child.unavailable),
    childStates,
  };
}

export function toggleMenuNode({ node, checked, selectedMenuIds = [], businessModuleCodes = [] }) {
  const ids = new Set(selectedMenuIds.map(Number));
  const modules = new Set(businessModuleCodes.map(normalize));
  const addId = (id) => { if (Number.isFinite(Number(id))) ids.add(Number(id)); };
  const removeId = (id) => { if (Number.isFinite(Number(id))) ids.delete(Number(id)); };
  if (node.type === 'BUSINESS_MODULE') {
    if (checked) {
      modules.add(normalize(node.moduleCode));
      node.backingMenuIds?.forEach(addId);
      node.children?.filter((child) => !child.legacy && !child.unavailable)
        .forEach((child) => addId(child.menuId));
    } else {
      modules.delete(normalize(node.moduleCode));
      node.backingMenuIds?.forEach(removeId);
      node.children?.forEach((child) => removeId(child.menuId));
    }
  } else if (node.type === 'SYSTEM_ROOT') {
    if (checked) {
      addId(node.menuId);
      node.children?.forEach((child) => addId(child.menuId));
    } else {
      removeId(node.menuId);
      node.children?.forEach((child) => removeId(child.menuId));
    }
  } else if (checked) addId(node.menuId);
  else removeId(node.menuId);
  return { menuIds: [...ids], businessModuleCodes: [...modules] };
}

export function toggleMenuChild({ parent, child, checked, selectedMenuIds = [], businessModuleCodes = [] }) {
  if (child.legacy || child.unavailable) return { menuIds: selectedMenuIds, businessModuleCodes };
  const ids = new Set(selectedMenuIds.map(Number));
  const modules = new Set(businessModuleCodes.map(normalize));
  if (checked) {
    ids.add(Number(child.menuId));
    if (parent.type === 'BUSINESS_MODULE') {
      modules.add(normalize(parent.moduleCode));
      parent.backingMenuIds?.forEach((id) => ids.add(Number(id)));
    } else if (parent.type === 'SYSTEM_ROOT') ids.add(Number(parent.menuId));
  } else {
    ids.delete(Number(child.menuId));
    const remaining = parent.children?.some((item) => !item.legacy && !item.unavailable
      && ids.has(Number(item.menuId)));
    if (!remaining) {
      if (parent.type === 'BUSINESS_MODULE') {
        modules.delete(normalize(parent.moduleCode));
        parent.backingMenuIds?.forEach((id) => ids.delete(Number(id)));
      } else if (parent.type === 'SYSTEM_ROOT') ids.delete(Number(parent.menuId));
    }
  }
  return { menuIds: [...ids], businessModuleCodes: [...modules] };
}

export function toggleMenuDisplayGroup({
  parent,
  group,
  checked,
  selectedMenuIds = [],
  businessModuleCodes = [],
}) {
  let next = { menuIds: selectedMenuIds, businessModuleCodes };
  (group.children || []).forEach((child) => {
    next = toggleMenuChild({
      parent,
      child,
      checked,
      selectedMenuIds: next.menuIds,
      businessModuleCodes: next.businessModuleCodes,
    });
  });
  return next;
}

export function selectedLogicalMenuCodes(tree, selectedMenuIds = [], businessModuleCodes = []) {
  const result = new Set();
  tree.forEach((node) => {
    const state = menuNodeState(node, selectedMenuIds, businessModuleCodes);
    if (state.checked || state.indeterminate) result.add(node.moduleCode || node.menuCode);
    (node.children || []).forEach((child, index) => {
      if (state.childStates[index]) result.add(child.menuCode);
    });
  });
  return result;
}

export function buildPermissionActions(permissions = []) {
  const byCode = new Map(permissions.map((permission) => [normalize(permissionCode(permission)), permission]));
  const knownCodes = new Set(ACTION_DEFINITIONS.flatMap((action) => action.codes.map(normalize)));
  const actions = ACTION_DEFINITIONS.filter((action) => action.primaryCodes.every((code) => byCode.has(normalize(code))))
    .map((action) => ({
      ...action,
      standard: true,
      permissionIds: action.codes.map((code) => itemId(byCode.get(normalize(code)))).filter(Boolean),
      primaryPermissionIds: action.primaryCodes.map((code) => itemId(byCode.get(normalize(code)))).filter(Boolean),
    }));
  permissions.forEach((permission) => {
    const code = permissionCode(permission);
    const normalizedCode = normalize(code);
    if (!code || knownCodes.has(normalizedCode)) return;
    const module = normalize(permission.moduleCode);
    let menuCodes = [module];
    let group = `${permission.moduleCode || '其他'} · 其他操作`;
    if (module === 'WEB_DOCUMENT') menuCodes = ['DOCUMENT_LIBRARY', 'DOCUMENT_RECYCLE'];
    if (module === 'WEB_INSPECTION') menuCodes = ['INSPECTION_LEDGER', 'INSPECTION_RECORDS', 'INSPECTION_EDGE'];
    if (module === 'WEB_QUALITY') menuCodes = ['QUALITY_ISSUES', 'QUALITY_DOCUMENTS'];
    actions.push({
      key: `custom:${normalizedCode}`,
      label: permission.permissionName || permission.name || code,
      description: permission.description || '自定义操作权限',
      group,
      menuCodes,
      primaryCodes: [code],
      codes: [code],
      standard: false,
      permissionIds: [itemId(permission)].filter(Boolean),
      primaryPermissionIds: [itemId(permission)].filter(Boolean),
    });
  });
  return actions;
}

export function filterActionsByMenus(actions = [], logicalMenuCodes = new Set()) {
  return actions.filter((action) => action.menuCodes.some((code) => logicalMenuCodes.has(code)));
}

const catalogItemEnabled = (item) => item && item.enabled !== 0 && item.enabled !== '0'
  && item.enabled !== false && item.visible !== 0 && item.visible !== '0'
  && item.visible !== false && !integerLikeDeleted(item.deleted);

function integerLikeDeleted(value) {
  return value === 1 || value === '1' || value === true;
}

function menuLabels(menuTree = []) {
  const result = new Map();
  BUSINESS_MENU_DEFINITIONS.forEach((definition) => {
    result.set(normalize(definition.moduleCode), definition.label);
    definition.pages.forEach((page) => result.set(normalize(page.menuCode), page.label));
  });
  menuTree.forEach((node) => {
    if (node.moduleCode || node.menuCode) result.set(normalize(node.moduleCode || node.menuCode), node.label);
    (node.children || []).forEach((child) => result.set(normalize(child.menuCode), child.label));
  });
  return result;
}

/**
 * Detect permissions that cannot be represented safely in the permission dialog.
 * Hidden actions have a selected primary permission but no assigned page menu;
 * orphan permissions are selected catalog entries not covered by any selected action.
 */
export function permissionSelectionIssues({
  selectedPermissionIds = [],
  actions = [],
  logicalMenuCodes = new Set(),
  permissions = [],
  menuTree = [],
} = {}) {
  const selectedIds = new Set(selectedPermissionIds.map(Number).filter(Number.isFinite));
  const selectedCodes = new Set([...logicalMenuCodes].map(normalize));
  const visibleKeys = new Set(filterActionsByMenus(actions, selectedCodes).map((action) => action.key));
  const labels = menuLabels(menuTree);
  const candidateSelectedActions = actions.filter((action) => action.primaryPermissionIds.length
    && action.primaryPermissionIds.every((id) => selectedIds.has(Number(id))));
  const visibleSelectedActions = candidateSelectedActions.filter((action) => visibleKeys.has(action.key));
  const visibleDependencyIds = new Set(visibleSelectedActions
    .flatMap((action) => action.permissionIds.map(Number)));
  // A shared technical permission may also be the primary permission of another
  // workflow action. When a visible selected action already requires it, do not
  // misclassify that shadow action as a hidden grant merely because its page menu
  // is absent.
  const selectedActions = candidateSelectedActions.filter((action) => visibleKeys.has(action.key)
    || !action.primaryPermissionIds.every((id) => visibleDependencyIds.has(Number(id))));
  const hiddenActions = selectedActions.filter((action) => !visibleKeys.has(action.key)).map((action) => {
    const missingMenuCodes = action.menuCodes.filter((code) => !selectedCodes.has(normalize(code)));
    return {
      ...action,
      missingMenuCodes,
      missingMenuLabels: missingMenuCodes.map((code) => labels.get(normalize(code)) || code),
    };
  });
  const coveredPermissionIds = new Set(selectedActions.flatMap((action) => action.permissionIds.map(Number)));
  const permissionById = new Map(permissions.map((permission) => [Number(itemId(permission)), permission]));
  const orphanPermissions = [...selectedIds]
    .filter((id) => !coveredPermissionIds.has(id) && permissionById.has(id))
    .map((id) => {
      const permission = permissionById.get(id);
      const relatedMenuCodes = [...new Set(actions.filter((action) => action.permissionIds.map(Number).includes(id))
        .flatMap((action) => action.menuCodes))];
      return {
        id,
        permissionCode: permissionCode(permission),
        label: permission.permissionName || permission.name || permissionCode(permission),
        missingMenuCodes: relatedMenuCodes.filter((code) => !selectedCodes.has(normalize(code))),
        missingMenuLabels: relatedMenuCodes.filter((code) => !selectedCodes.has(normalize(code)))
          .map((code) => labels.get(normalize(code)) || code),
      };
    });
  return { selectedActions, hiddenActions, orphanPermissions };
}

/** A compact health check for the standard business authorization catalog. */
export function authorizationCatalogStatus(menus = [], permissions = []) {
  const menuByCode = new Map(menus.map((menu) => [menuCode(menu), menu]));
  const permissionByCode = new Map(permissions.map((permission) => [normalize(permissionCode(permission)), permission]));
  const expectedMenuCodes = [...new Set(BUSINESS_MENU_DEFINITIONS.flatMap((definition) => [
    ...definition.backingMenuCodes,
    ...definition.pages.map((page) => page.menuCode),
  ]).concat(['WEB_SYSTEM', ...SYSTEM_MENU_ORDER]).map(normalize))];
  const expectedPermissionCodes = [...new Set([
    ...ACTION_DEFINITIONS.flatMap((action) => action.primaryCodes),
    ...SYSTEM_PERMISSION_CODES,
  ].map(normalize))];
  const missingMenuCodes = expectedMenuCodes.filter((code) => !menuByCode.has(code));
  const disabledMenuCodes = expectedMenuCodes.filter((code) => menuByCode.has(code) && !catalogItemEnabled(menuByCode.get(code)));
  const missingPermissionCodes = expectedPermissionCodes.filter((code) => !permissionByCode.has(code));
  const disabledPermissionCodes = expectedPermissionCodes.filter((code) => permissionByCode.has(code)
    && !catalogItemEnabled(permissionByCode.get(code)));
  return {
    healthy: !missingMenuCodes.length && !disabledMenuCodes.length
      && !missingPermissionCodes.length && !disabledPermissionCodes.length,
    expectedMenuCount: expectedMenuCodes.length,
    availableMenuCount: expectedMenuCodes.length - missingMenuCodes.length - disabledMenuCodes.length,
    expectedPermissionCount: expectedPermissionCodes.length,
    availablePermissionCount: expectedPermissionCodes.length - missingPermissionCodes.length - disabledPermissionCodes.length,
    missingMenuCodes,
    disabledMenuCodes,
    missingPermissionCodes,
    disabledPermissionCodes,
  };
}

export function selectedActionKeys(permissionIds = [], actions = []) {
  const selected = new Set(permissionIds.map(Number));
  return new Set(actions.filter((action) => action.primaryPermissionIds.length
    && action.primaryPermissionIds.every((id) => selected.has(Number(id)))).map((action) => action.key));
}

export function toggleActionKey(selectedKeys, actionKey, checked, actions = []) {
  const result = new Set(selectedKeys);
  const byKey = new Map(actions.map((action) => [action.key, action]));
  const addWithRequirements = (key) => {
    const action = byKey.get(key);
    if (!action || result.has(key)) return;
    result.add(key);
    (action.requiresActions || []).forEach(addWithRequirements);
  };
  if (checked) addWithRequirements(actionKey);
  else {
    result.delete(actionKey);
    let changed = true;
    while (changed) {
      changed = false;
      for (const key of [...result]) {
        const requirements = byKey.get(key)?.requiresActions || [];
        if (requirements.some((required) => !result.has(required))) {
          result.delete(key);
          changed = true;
        }
      }
    }
  }
  return result;
}

export function permissionIdsForActionKeys(selectedKeys, actions = []) {
  const ids = new Set();
  actions.filter((action) => selectedKeys.has(action.key))
    .forEach((action) => action.permissionIds.forEach((id) => ids.add(Number(id))));
  return [...ids];
}

export function groupPermissionActions(actions = []) {
  const grouped = new Map();
  actions.forEach((action) => {
    if (!grouped.has(action.group)) grouped.set(action.group, []);
    grouped.get(action.group).push(action);
  });
  return [...grouped.entries()].map(([label, items]) => ({ label, items }));
}

function secondaryGroupLabel(action) {
  const parts = String(action?.group || '').split('·').map((part) => part.trim()).filter(Boolean);
  return parts.length > 1 ? parts.slice(1).join(' · ') : (parts[0] || '其他操作');
}

/**
 * Build the operation-permission view from the same first/second-level menu tree
 * used by menu assignment. Actions that genuinely span multiple page menus stay
 * in a clearly labelled shared group under their real first-level menu.
 */
export function buildPermissionActionTree(menuTree = [], actions = [], logicalMenuCodes = new Set()) {
  const selectedCodes = new Set([...logicalMenuCodes].map(normalize));
  const visibleActions = filterActionsByMenus(actions, selectedCodes);
  const assigned = new Set();

  const nodes = menuTree.map((node) => {
    const childCodes = new Set((node.children || [])
      .filter((child) => selectedCodes.has(normalize(child.menuCode)))
      .map((child) => normalize(child.menuCode)));
    const rootCodes = new Set([
      normalize(node.moduleCode),
      normalize(node.menuCode),
      ...childCodes,
    ].filter(Boolean));
    const nodeActions = visibleActions.filter((action) => action.menuCodes
      .some((code) => rootCodes.has(normalize(code))));
    nodeActions.forEach((action) => assigned.add(action.key));

    const grouped = new Map();
    nodeActions.forEach((action) => {
      const label = secondaryGroupLabel(action);
      const matchingChild = (node.children || []).find((child) => (
        selectedCodes.has(normalize(child.menuCode))
        && normalize(child.label) === normalize(label)
        && action.menuCodes.some((code) => normalize(code) === normalize(child.menuCode))
      ));
      const key = matchingChild ? `page:${matchingChild.menuCode}` : `group:${normalize(label)}`;
      if (!grouped.has(key)) {
        grouped.set(key, {
          key: `${node.key}:${key}`,
          label,
          menuCode: matchingChild?.menuCode || null,
          items: [],
        });
      }
      grouped.get(key).items.push(action);
    });

    return {
      key: `permissions:${node.key}`,
      label: node.label,
      description: node.description,
      groups: [...grouped.values()],
      items: nodeActions,
    };
  }).filter((node) => node.items.length > 0);

  const unassigned = visibleActions.filter((action) => !assigned.has(action.key));
  if (unassigned.length) {
    nodes.push({
      key: 'permissions:other',
      label: '其他操作',
      description: '未绑定到当前标准菜单层级的自定义权限',
      groups: [{ key: 'permissions:other:custom', label: '自定义操作', menuCode: null, items: unassigned }],
      items: unassigned,
    });
  }
  return nodes;
}

export function missingMenuCatalogCodes(menuTree = []) {
  return menuTree.flatMap((node) => (node.children || [])
    .filter((child) => child.unavailable)
    .map((child) => child.menuCode));
}

export function hasPageMenuCatalog(menuCodes = [], pageCodes = []) {
  const normalizedCodes = new Set(menuCodes.map(normalize));
  return pageCodes.some((code) => normalizedCodes.has(normalize(code)));
}

export function pageMenuAllowed(menuCodes = [], pageCodes = [], parentCodes = []) {
  const normalizedCodes = new Set(menuCodes.map(normalize));
  if (pageCodes.some((code) => normalizedCodes.has(normalize(code)))) return true;
  const targetDefinition = BUSINESS_MENU_DEFINITIONS.find((definition) => definition.pages
    .some((page) => pageCodes.some((code) => normalize(code) === normalize(page.menuCode))));
  const knownPageCodes = targetDefinition?.pages.map((page) => page.menuCode) || pageCodes;
  const catalogPresent = knownPageCodes.some((code) => normalizedCodes.has(normalize(code)));
  return !catalogPresent && parentCodes.some((code) => normalizedCodes.has(normalize(code)));
}
