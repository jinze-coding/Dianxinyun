import { reactive } from 'vue';
import { useProjectStore } from './project';
import type { User } from '@/types';
import { getCurrentUser, login as loginApi, logout as logoutApi } from '@/api/auth';
import { getToken, setToken } from '@/api/request';
import { USE_MOCK } from '@/api/request';

const USER_CACHE_KEY = 'site_platform_current_user';
const RESUME_URL_KEY = 'site_platform_auth_resume_url';
const CURRENT_PROJECT_KEY = 'site_platform_current_project_id';
const PERSONAL_TODO_PAGE = '/pages/todo/index';

const state = reactive<{
  user: User | null;
  tokenReady: boolean;
}>({
  user: uni.getStorageSync(USER_CACHE_KEY) || null,
  tokenReady: USE_MOCK || Boolean(getToken())
});

const ROOT_PAGE_RULES = [
  {
    path: PERSONAL_TODO_PAGE,
    miniMenuCodes: [],
    legacyMenuCodes: []
  },
  {
    path: '/pages/documents/index',
    miniMenuCodes: ['MINI_DOCUMENT'],
    legacyMenuCodes: ['WEB_DOCUMENT', 'DOCUMENT_MANAGEMENT']
  },
  {
    path: '/pages/inspection/index',
    miniMenuCodes: ['MINI_INSPECTION'],
    legacyMenuCodes: ['WEB_INSPECTION', 'ELECTRIC_INSPECTION']
  },
  {
    path: '/pages/quality/index',
    miniMenuCodes: ['MINI_QUALITY'],
    legacyMenuCodes: ['WEB_QUALITY', 'QUALITY_MANAGEMENT']
  },
  { path: '/pages/safety-committee/index', miniMenuCodes: ['MINI_SAFETY_COMMITTEE'], legacyMenuCodes: ['WEB_SAFETY_COMMITTEE'] },
  { path: '/pages/profile/index', miniMenuCodes: [], legacyMenuCodes: [] }
] as const;

type UserMenuItem = NonNullable<User['menus']>[number];

function flattenMenus(user: User | null): UserMenuItem[] {
  const result: UserMenuItem[] = [];
  const walk = (menus: User['menus'] = []) => {
    (menus || []).forEach((menu) => {
      result.push(menu);
      walk(menu.children);
    });
  };
  walk(user?.menus);
  return result;
}

function menuCode(menu: UserMenuItem): string {
  return String(menu.menuCode || menu.code || '').trim().toUpperCase();
}

function normalizedRoutePath(path?: string): string {
  const value = String(path || '').trim();
  return value && !value.startsWith('/') && value.startsWith('pages/') ? `/${value}` : value;
}

function isMiniProgramMenu(menu: UserMenuItem): boolean {
  const clientType = String(menu.clientType || '').trim().toUpperCase();
  return clientType === 'MINI_PROGRAM'
    || clientType === 'MINIPROGRAM'
    || clientType === 'MINI'
    || menuCode(menu).startsWith('MINI_');
}

function isActiveProjectContext(context: { accessStatus?: string }): boolean {
  const accessStatus = String(context.accessStatus || '').trim().toUpperCase();
  return !accessStatus || accessStatus === 'ACTIVE';
}

function projectContexts(user: User, projectId: number) {
  return [
    ...(user.projectContexts || []),
    ...(user.projectRoles || [])
  ].filter((context) => Number(context.projectId) === Number(projectId)
    && isActiveProjectContext(context));
}

function hasProjectScope(user: User, projectId: number): boolean {
  const matchingContexts = projectContexts(user, projectId);
  if (matchingContexts.length) return true;

  const hasKnownContext = [...(user.projectContexts || []), ...(user.projectRoles || [])]
    .some((context) => Number(context.projectId) === Number(projectId));
  if (hasKnownContext) return false;

  return (user.accessibleProjectIds || []).some((id) => Number(id) === Number(projectId));
}

function collectProjectPermissionCodes(user: User, projectId: number): string[] {
  if (!hasProjectScope(user, projectId)) return [];
  const codes = new Set(user.permissionCodes || []);
  projectContexts(user, projectId).forEach((context) => {
    (context.permissionCodes || []).forEach((code) => codes.add(code));
  });
  return Array.from(codes);
}

function collectProjectMenuCodes(user: User, projectId: number): string[] {
  if (!hasProjectScope(user, projectId)) return [];
  const codes = new Set<string>();
  projectContexts(user, projectId).forEach((context) => {
    (context.menuCodes || []).forEach((code) => codes.add(String(code).trim().toUpperCase()));
  });
  return Array.from(codes);
}

function moduleForPermission(code: string): string | undefined {
  const value = String(code || '').toUpperCase();
  if (/^(DOCUMENT\.|SEAL\.)/.test(value)) return 'DOCUMENT';
  if (/^(INSPECTION\.|BOX_|INSPECTION_|EDGE_INSPECTION_|CUSTOM_INSPECTION_|SUMMARY_|RECTIFICATION_)/.test(value)) return 'INSPECTION';
  if (value.startsWith('QUALITY.')) return 'QUALITY';
  if (value.startsWith('SAFETY_COMMITTEE.')) return 'SAFETY_COMMITTEE';
  return undefined;
}

function isProjectModuleEnabled(user: User | null, projectId: number, module?: string): boolean {
  if (!module) return true;
  const context = (user?.projectContexts || user?.projectRoles || []).find((item) => Number(item.projectId) === Number(projectId));
  return !Array.isArray(context?.enabledBusinessModules) || context.enabledBusinessModules.includes(module);
}

function hasProjectPermission(user: User | null, projectId: number, ...permissionCodes: string[]): boolean {
  if (!user || !projectId) return false;
  const granted = new Set(collectProjectPermissionCodes(user, projectId));
  return permissionCodes.filter(Boolean).some((code) => isProjectModuleEnabled(user, projectId, moduleForPermission(code))
    && (user.roles?.includes('PLATFORM_ADMIN') || granted.has(code)));
}

function storedProjectId(): number {
  const value = Number(uni.getStorageSync(CURRENT_PROJECT_KEY));
  return Number.isFinite(value) && value > 0 ? value : 0;
}

function activeProjectIds(user: User): number[] {
  return Array.from(new Set(
    [...(user.projectContexts || []), ...(user.projectRoles || [])]
      .filter(isActiveProjectContext)
      .map((context) => Number(context.projectId))
      .filter((projectId) => Number.isFinite(projectId) && projectId > 0)
  ));
}

function canAccessRoot(path: string, user: User | null = state.user, projectId = useProjectStore().state.currentProjectId): boolean {
  const rule = ROOT_PAGE_RULES.find((item) => item.path === path);
  if (!rule || rule.path === '/pages/profile/index') return true;
  if (!user) return false;
  // 个人待办是所有已登录用户的基础工作台，不依赖项目业务菜单。
  if (rule.path === PERSONAL_TODO_PAGE) return true;
  const module = ({ '/pages/documents/index': 'DOCUMENT', '/pages/inspection/index': 'INSPECTION', '/pages/quality/index': 'QUALITY', '/pages/safety-committee/index': 'SAFETY_COMMITTEE' } as Record<string, string>)[path];
  if (!isProjectModuleEnabled(user, projectId, module)) return false;
  if (user.roles?.includes('PLATFORM_ADMIN')) return true;
  const projectIds = projectId > 0 ? [projectId] : activeProjectIds(user);
  const projectMenus = projectIds.flatMap((projectId) => collectProjectMenuCodes(user, projectId));
  const menus = flattenMenus(user);
  const hasMiniMenuCatalog = projectMenus.some((code) => code.startsWith('MINI_'))
    || menus.some(isMiniProgramMenu);
  const allowedCodes = new Set(
    (hasMiniMenuCatalog ? rule.miniMenuCodes : rule.legacyMenuCodes)
      .map((code) => code.toUpperCase())
  );
  if (projectIds.some((id) => projectContexts(user, id).some((context) => Array.isArray(context.menuCodes))) || projectMenus.length) return projectMenus.some((code) => allowedCodes.has(code));
  // 兼容旧会话；服务端对项目范围和操作权限仍会强制校验。
  return menus.some((menu) => {
    if (hasMiniMenuCatalog && !isMiniProgramMenu(menu)) return false;
    return allowedCodes.has(menuCode(menu))
      || normalizedRoutePath(menu.routePath || menu.path) === rule.path;
  });
}

function firstAuthorizedPage(user: User | null = state.user): string {
  return ROOT_PAGE_RULES.find((item) => canAccessRoot(item.path, user))?.path || '/pages/profile/index';
}

function requiresInitialPasswordSetup(user: User | null = state.user): boolean {
  return user?.initialPasswordSetupRequired === true;
}

let userRefreshVersion = 0;
function persistUser(user: User | null) {
  userRefreshVersion += 1;
  state.user = user;
  if (user) uni.setStorageSync(USER_CACHE_KEY, user);
  else uni.removeStorageSync(USER_CACHE_KEY);
}

export function useAuthStore() {
  async function login(username: string, password: string) {
    const user = await loginApi({ username, password });
    state.tokenReady = true;
    persistUser(user);
    return user;
  }

  async function completeLogin(token?: string) {
    if (token) setToken(token);
    state.tokenReady = Boolean(getToken()) || USE_MOCK;
    if (!state.tokenReady) throw new Error('登录凭证无效，请重新登录');
    const user = await getCurrentUser();
    persistUser(user);
    return user;
  }

  async function loadUser() {
    if (!state.tokenReady) {
      return null;
    }
    const request = ++userRefreshVersion;
    const token = getToken();
    const user = await getCurrentUser();
    if (request === userRefreshVersion && token === getToken() && state.tokenReady) persistUser(user);
    return state.user;
  }

  async function logout() {
    try {
      await logoutApi();
    } finally {
      persistUser(null);
      state.tokenReady = false;
    }
  }

  function clearLocalSession() {
    persistUser(null);
    state.tokenReady = false;
  }

  function rememberResumeUrl(url: string) {
    uni.setStorageSync(RESUME_URL_KEY, url);
  }

  function takeResumeUrl(): string {
    const value = String(uni.getStorageSync(RESUME_URL_KEY) || '');
    uni.removeStorageSync(RESUME_URL_KEY);
    return value;
  }

  function navigateAfterLogin() {
    if (requiresInitialPasswordSetup()) {
      uni.reLaunch({ url: '/pages/initial-password/index' });
      return;
    }
    const resumeUrl = takeResumeUrl();
    if (resumeUrl) {
      uni.reLaunch({ url: resumeUrl });
      return;
    }
    uni.switchTab({ url: firstAuthorizedPage(), fail: () => uni.reLaunch({ url: firstAuthorizedPage() }) });
  }

  async function ensureRootAccess(path: string, projectId?: number): Promise<boolean> {
    if (!getToken() && !USE_MOCK) {
      uni.reLaunch({ url: '/pages/login/index' });
      return false;
    }
    try {
      if (!state.user) await loadUser();
      if (requiresInitialPasswordSetup()) {
        uni.reLaunch({ url: '/pages/initial-password/index' });
        return false;
      }
      if (canAccessRoot(path, state.user, projectId)) return true;
      uni.showToast({ title: '当前账号无此功能权限', icon: 'none' });
      const target = firstAuthorizedPage();
      uni.switchTab({ url: target, fail: () => uni.reLaunch({ url: target }) });
      return false;
    } catch {
      uni.reLaunch({ url: '/pages/login/index' });
      return false;
    }
  }

  async function ensurePageAccess(
    path: string,
    projectId?: number,
    ...permissionCodes: string[]
  ): Promise<boolean> {
    if (!await ensureRootAccess(path, projectId)) return false;
    if (!permissionCodes.filter(Boolean).length) return true;
    const resolvedProjectId = Number(projectId) > 0 ? Number(projectId) : storedProjectId();
    if (hasProjectPermission(state.user, resolvedProjectId, ...permissionCodes)) return true;
    uni.showToast({ title: '当前项目无此操作权限', icon: 'none' });
    const target = firstAuthorizedPage();
    uni.switchTab({ url: target, fail: () => uni.reLaunch({ url: target }) });
    return false;
  }

  async function ensureProjectPermission(
    path: string,
    projectId: number,
    ...permissionCodes: string[]
  ): Promise<boolean> {
    return ensurePageAccess(path, projectId, ...permissionCodes);
  }

  return {
    state,
    isProjectModuleEnabled: (projectId: number, module: string) => isProjectModuleEnabled(state.user, projectId, module),
    login,
    completeLogin,
    loadUser,
    logout,
    clearLocalSession,
    canAccessRoot,
    firstAuthorizedPage,
    requiresInitialPasswordSetup,
    navigateAfterLogin,
    rememberResumeUrl,
    takeResumeUrl,
    ensureRootAccess,
    ensurePageAccess,
    hasProjectPermission: (projectId: number, ...permissionCodes: string[]) =>
      hasProjectPermission(state.user, projectId, ...permissionCodes),
    ensureProjectPermission
  };
}

export {
  ROOT_PAGE_RULES,
  canAccessRoot,
  firstAuthorizedPage,
  requiresInitialPasswordSetup,
  hasProjectPermission
};
