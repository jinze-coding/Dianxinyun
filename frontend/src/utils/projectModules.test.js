import test from 'node:test';
import assert from 'node:assert/strict';
import { PAGE_IDS } from '../constants/dicts.js';
import { canAccessPage, hasProjectPermission, projectLandingPage } from './permissions.js';

const contexts = [
  { projectId: 1, accessStatus: 'ACTIVE', enabledBusinessModules: ['DOCUMENT', 'SITE_ACCESS', 'SAFETY_COMMITTEE'],
    menuCodes: ['WEB_DOCUMENT', 'WEB_SITE_ACCESS', 'WEB_SAFETY_COMMITTEE'], permissionCodes: ['document.view', 'safety_committee.view'] },
  { projectId: 2, accessStatus: 'ACTIVE', enabledBusinessModules: ['INSPECTION', 'QUALITY'],
    menuCodes: ['WEB_INSPECTION', 'WEB_QUALITY'], permissionCodes: ['quality.manage', 'BOX_MANAGE'] },
  { projectId: 3, accessStatus: 'ACTIVE', enabledBusinessModules: [], menuCodes: [], permissionCodes: [] },
];
for (const roles of [['PROJECT_MANAGER'], ['PLATFORM_ADMIN']]) test(`${roles[0]} observes independent project caps`, () => {
  const user = { roles, projectContexts: contexts, menus: [{ menuCode: 'WEB_QUALITY' }, { menuCode: 'WEB_DOCUMENT' }] };
  assert.equal(canAccessPage(user, PAGE_IDS.QUALITY_MANAGEMENT, 1), false);
  assert.equal(canAccessPage(user, PAGE_IDS.QUALITY_MANAGEMENT, 2), true);
  assert.equal(canAccessPage(user, PAGE_IDS.SAFETY_COMMITTEE, 1), true);
  assert.equal(hasProjectPermission(user, 1, 'quality.manage'), false);
  assert.equal(hasProjectPermission(user, 2, 'quality.manage'), true);
  assert.equal(hasProjectPermission(user, 3, 'seal.manage', 'BOX_MANAGE', 'quality.manage'), false);
  assert.equal(canAccessPage(user, PAGE_IDS.DOCUMENT_MANAGEMENT, 3), false);
  assert.equal(canAccessPage(user, PAGE_IDS.PERSONAL_INBOX, 3), true);
});

test('empty project menu cannot fall back to another project global menu', () => {
  const user = { roles: ['PROJECT_MANAGER'], menus: [{ menuCode: 'WEB_DOCUMENT' }],
    projectContexts: [{ projectId: 4, enabledBusinessModules: ['DOCUMENT'], menuCodes: [] }] };
  assert.equal(canAccessPage(user, PAGE_IDS.DOCUMENT_MANAGEMENT, 4), false);
});

for (const roles of [['PROJECT_MANAGER'], ['PLATFORM_ADMIN']]) test(`${roles[0]} hides inbox per project and uses authorized landing`, () => {
  const user = { roles, projectContexts: contexts.map((item) => ({ ...item, inboxEntryVisible: item.projectId === 2 })) };
  assert.equal(canAccessPage(user, PAGE_IDS.PERSONAL_INBOX, 1), false);
  assert.equal(canAccessPage(user, PAGE_IDS.PERSONAL_INBOX, 2), true);
  assert.equal(projectLandingPage(user, 1), PAGE_IDS.SAFETY_COMMITTEE);
  assert.equal(projectLandingPage(user, 2), PAGE_IDS.PERSONAL_INBOX);
  assert.equal(projectLandingPage(user, 3), PAGE_IDS.PROJECT_INFORMATION);
  user.projectContexts[0].enabledBusinessModules = ['DOCUMENT'];
  assert.equal(projectLandingPage(user, 1), PAGE_IDS.DOCUMENT_MANAGEMENT);
  if (!roles.includes('PLATFORM_ADMIN')) {
    user.projectContexts[0].menuCodes = [];
    assert.equal(projectLandingPage(user, 1), PAGE_IDS.PROJECT_INFORMATION);
  }
});
