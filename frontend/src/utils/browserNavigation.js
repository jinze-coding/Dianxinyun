import { AUTHENTICATED_LANDING_PAGE, NAV_ITEMS, PAGE_IDS } from '../constants/dicts.js';
import { canAccessPage, hasProjectPermission, projectLandingPage } from './permissions.js';

export const BROWSER_NAVIGATION_KEY = 'site_platform_tab_navigation';
const pages = new Set([...NAV_ITEMS.map((item) => item.id), PAGE_IDS.PERSONAL_INBOX,
  PAGE_IDS.SYSTEM_MANAGEMENT, PAGE_IDS.PROJECT_INFORMATION]);
const validId = (value) => Number.isSafeInteger(Number(value)) && Number(value) > 0;

export function browserNavigationStorage() {
  try { return window.sessionStorage; } catch { return null; }
}

export function readBrowserNavigation(storage) {
  try {
    const value = JSON.parse(storage?.getItem(BROWSER_NAVIGATION_KEY) || 'null');
    if (value?.version !== 1 || !validId(value.userId) || !pages.has(value.pageId)
      || (value.projectId !== null && !validId(value.projectId))) return null;
    return { version: 1, userId: Number(value.userId), projectId: value.projectId === null ? null : Number(value.projectId),
      pageId: value.pageId, returnPageId: pages.has(value.returnPageId) ? value.returnPageId : AUTHENTICATED_LANDING_PAGE,
      tabs: Object.fromEntries(Object.entries(value.tabs || {}).filter(([key, item]) =>
        /^[a-zA-Z]+$/.test(key) && key.length <= 40 && typeof item === 'string' && item.length <= 64)) };
  } catch { return null; }
}

export function sameNavigationPage(left, right) {
  return Boolean(left && right && left.userId === right.userId
    && left.projectId === right.projectId && left.pageId === right.pageId);
}

// Only small navigation identifiers are stored; business data is fetched and authorized again.
export function writeBrowserNavigation(storage, context, tabs = {}) {
  if (!validId(context?.userId) || !pages.has(context.pageId)) return;
  const previous = readBrowserNavigation(storage);
  const samePage = sameNavigationPage(previous, context);
  try {
    storage?.setItem(BROWSER_NAVIGATION_KEY, JSON.stringify({ version: 1,
      userId: context.userId, projectId: context.projectId, pageId: context.pageId,
      returnPageId: context.returnPageId ?? (samePage ? previous.returnPageId : AUTHENTICATED_LANDING_PAGE),
      tabs: { ...(samePage ? previous.tabs : {}), ...tabs } }));
  } catch { /* Storage restrictions must not prevent normal navigation. */ }
}

export function clearBrowserNavigation(storage) {
  try { storage?.removeItem(BROWSER_NAVIGATION_KEY); } catch { /* Storage may be disabled. */ }
}

export function canRestorePage(user, pageId, projectId) {
  if (!user || user.initialPasswordSetupRequired || !pages.has(pageId)) return false;
  if (pageId === PAGE_IDS.SYSTEM_MANAGEMENT) return canAccessPage(user, pageId, projectId)
    || hasProjectPermission(user, projectId, 'system.approval.view', 'system.approval.manage');
  if (pageId === PAGE_IDS.PROJECT_INFORMATION) return projectId !== null;
  return (pageId === PAGE_IDS.PERSONAL_INBOX || projectId !== null) && canAccessPage(user, pageId, projectId);
}

export function restoreBrowserNavigation(saved, user, projects) {
  const matchingUser = saved && Number(saved.userId) === Number(user?.id) && !user?.initialPasswordSetupRequired;
  const selected = matchingUser ? projects.find((project) => Number(project.id) === saved.projectId) : null;
  const projectId = selected?.id ?? projects[0]?.id ?? null;
  const projectMatches = Boolean(selected) || (matchingUser && saved.projectId === null && projectId === null);
  const landing = projectLandingPage(user, projectId);
  const pageId = projectMatches && canRestorePage(user, saved.pageId, projectId) ? saved.pageId : landing;
  return { projectId, pageId, returnPageId: projectMatches && canRestorePage(user, saved.returnPageId, projectId)
    ? saved.returnPageId : landing };
}
