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
  if (isPlatformAdmin(user)) return true;
  const expected = new Set(menuCodes.filter(Boolean));
  return flattenMenus(user?.menus || []).some((menu) => {
    if (menu?.enabled === false || Number(menu?.enabled) === 0
      || String(menu?.status || '').toUpperCase() === 'DISABLED') {
      return false;
    }
    const candidates = [menu?.pageId, menu?.menuCode, menu?.code, menu?.routeKey, menu?.routePath, menu?.path];
    return candidates.some((candidate) => expected.has(candidate));
  });
}

export function canAccessPage(user, pageId) {
  if (!user) return false;
  if (isPlatformAdmin(user)) return true;
  const rule = PAGE_ACCESS_RULES[pageId];
  if (!rule) return false;
  return hasAssignedMenu(user, pageId, ...rule.menuCodes);
}
