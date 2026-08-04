import { PAGE_IDS } from '../constants/dicts';

const PAGE_ACCESS_RULES = {
  [PAGE_IDS.DOCUMENT_MANAGEMENT]: {
    menuCodes: ['WEB_DOCUMENT', 'DOCUMENT_MANAGEMENT'],
  },
  [PAGE_IDS.ELECTRIC_INSPECTION]: {
    menuCodes: ['WEB_INSPECTION', 'ELECTRIC_INSPECTION'],
  },
  [PAGE_IDS.QUALITY_MANAGEMENT]: {
    menuCodes: ['WEB_QUALITY', 'QUALITY_MANAGEMENT'],
  },
  [PAGE_IDS.SYSTEM_MANAGEMENT]: {
    menuCodes: [
      'WEB_SYSTEM',
      'SYSTEM_MANAGEMENT',
      'SYSTEM_REGISTRATION',
      'SYSTEM_USER',
      'SYSTEM_ROLE',
      'SYSTEM_MENU',
      'SYSTEM_PROJECT',
      'SYSTEM_WECHAT',
      'SYSTEM_AUDIT',
    ],
  },
};

export function isPlatformAdmin(user) {
  return (user?.roles || []).includes('PLATFORM_ADMIN');
}

export function collectPermissionCodes(user, projectId = null) {
  const codes = new Set(user?.permissionCodes || []);
  if (projectId === null || projectId === undefined) return codes;
  (user?.projectContexts || user?.projectRoles || []).forEach((context) => {
    if (Number(context?.projectId) !== Number(projectId)) return;
    if (String(context?.accessStatus || 'ACTIVE').toUpperCase() !== 'ACTIVE') return;
    (context?.permissionCodes || []).forEach((code) => codes.add(code));
  });
  return codes;
}

function flattenMenus(menus = [], result = []) {
  menus.forEach((menu) => {
    result.push(menu);
    flattenMenus(menu?.children || [], result);
  });
  return result;
}

function menuIdentity(menu) {
  return menu?.pageId || menu?.menuCode || menu?.code || menu?.routeKey || menu?.routePath || menu?.path;
}

export function collectAssignedMenuCodes(user) {
  return [...new Set(flattenMenus(user?.menus || [])
    .filter((menu) => menu?.enabled !== false && Number(menu?.enabled) !== 0
      && String(menu?.status || '').toUpperCase() !== 'DISABLED')
    .map(menuIdentity)
    .filter(Boolean))];
}

export function collectProjectMenuCodes(user, projectId) {
  const result = new Set();
  (user?.projectContexts || user?.projectRoles || []).forEach((context) => {
    if (Number(context?.projectId) !== Number(projectId)) return;
    if (String(context?.accessStatus || 'ACTIVE').toUpperCase() !== 'ACTIVE') return;
    (context?.menuCodes || []).forEach((code) => result.add(code));
  });
  // 兼容尚未返回按项目菜单的旧会话；后端权限校验仍是最终安全边界。
  if (!result.size) collectAssignedMenuCodes(user).forEach((code) => result.add(code));
  return [...result];
}

export function hasPermission(user, ...codes) {
  if (isPlatformAdmin(user)) return true;
  const granted = collectPermissionCodes(user);
  return codes.filter(Boolean).some((code) => granted.has(code));
}

export function hasProjectPermission(user, projectId, ...codes) {
  if (isPlatformAdmin(user)) return true;
  const granted = collectPermissionCodes(user, projectId);
  return codes.filter(Boolean).some((code) => granted.has(code));
}

export function hasAssignedMenu(user, ...menuCodes) {
  const expected = new Set(menuCodes.filter(Boolean));
  return collectAssignedMenuCodes(user).some((code) => expected.has(code));
}

export function hasAssignedProjectMenu(user, projectId, ...menuCodes) {
  const expected = new Set(menuCodes.filter(Boolean));
  const contexts = user?.projectContexts || user?.projectRoles || [];
  const hasActiveContext = contexts.some((item) => Number(item?.projectId) === Number(projectId)
    && String(item?.accessStatus || 'ACTIVE').toUpperCase() === 'ACTIVE');
  if (!hasActiveContext) return false;
  const projectMenus = collectProjectMenuCodes(user, projectId);
  if (projectMenus.length) return projectMenus.some((code) => expected.has(code));
  // 兼容尚未返回按项目菜单的旧会话，服务端仍是最终安全边界。
  return hasAssignedMenu(user, ...menuCodes);
}

export function canAccessPage(user, pageId, projectId = null) {
  if (!user) return false;
  const rule = PAGE_ACCESS_RULES[pageId];
  if (!rule) return false;
  if (projectId !== null && projectId !== undefined) {
    return hasAssignedProjectMenu(user, projectId, pageId, ...rule.menuCodes);
  }
  return hasAssignedMenu(user, pageId, ...rule.menuCodes);
}
