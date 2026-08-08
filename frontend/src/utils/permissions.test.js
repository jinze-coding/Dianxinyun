import test from 'node:test';
import assert from 'node:assert/strict';
import { PAGE_IDS } from '../constants/dicts.js';
import { canAccessPage } from './permissions.js';

test('stale project system menu cannot reopen system management', () => {
  const user = {
    roles: ['PROJECT_MANAGER'],
    menus: [{ menuCode: 'WEB_SYSTEM' }, { menuCode: 'SYSTEM_PROJECT' }],
    projectContexts: [{
      projectId: 7,
      accessStatus: 'ACTIVE',
      menuCodes: ['WEB_SYSTEM', 'SYSTEM_PROJECT'],
    }],
  };
  assert.equal(canAccessPage(user, PAGE_IDS.SYSTEM_MANAGEMENT, 7), false);
});

test('platform administrator keeps system management access', () => {
  const user = {
    roles: ['PLATFORM_ADMIN'],
    menus: [{ menuCode: 'WEB_SYSTEM' }],
    projectContexts: [{ projectId: 7, accessStatus: 'ACTIVE', menuCodes: ['WEB_SYSTEM'] }],
  };
  assert.equal(canAccessPage(user, PAGE_IDS.SYSTEM_MANAGEMENT, 7), true);
});
