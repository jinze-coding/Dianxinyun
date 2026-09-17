import test from 'node:test';
import assert from 'node:assert/strict';
import { BROWSER_NAVIGATION_KEY, clearBrowserNavigation, readBrowserNavigation, restoreBrowserNavigation, writeBrowserNavigation } from './browserNavigation.js';
import { PAGE_IDS } from '../constants/dicts.js';

const projects = [{ id: 1 }, { id: 2 }];
const admin = { id: 9, roles: ['PLATFORM_ADMIN'], projectContexts: projects.map(({ id }) => ({ projectId: id, enabledBusinessModules: ['DOCUMENT', 'INSPECTION', 'SAFETY_COMMITTEE'] })) };
const position = { userId: 9, projectId: 2, pageId: PAGE_IDS.DOCUMENT_MANAGEMENT };
function storage() { const values = new Map(); return { getItem: (key) => values.get(key), setItem: (key, value) => values.set(key, value), removeItem: (key) => values.delete(key) }; }
function saved() { const tab = storage(); writeBrowserNavigation(tab, position, { documentTab: 'seal', sealTab: 'ledger' }); return readBrowserNavigation(tab); }

test('refresh restores the selected project, page and nested tabs instead of the first project', () => {
  const snapshot = saved();
  assert.deepEqual(restoreBrowserNavigation(snapshot, admin, projects), { projectId: 2, pageId: PAGE_IDS.DOCUMENT_MANAGEMENT, returnPageId: PAGE_IDS.PERSONAL_INBOX });
  assert.deepEqual(snapshot.tabs, { documentTab: 'seal', sealTab: 'ledger' });
});

test('separate tabs do not overwrite each other, and changing scope discards old nested state', () => {
  const a = storage(), b = storage();
  writeBrowserNavigation(a, position, { documentTab: 'seal' });
  writeBrowserNavigation(b, { ...position, projectId: 1, pageId: PAGE_IDS.SYSTEM_MANAGEMENT }, { systemTab: 'users' });
  writeBrowserNavigation(a, position, { sealTab: 'ledger' });
  writeBrowserNavigation(a, position); // App writes after child tab effects.
  assert.equal(readBrowserNavigation(a).projectId, 2);
  assert.equal(readBrowserNavigation(a).tabs.sealTab, 'ledger');
  assert.equal(readBrowserNavigation(b).projectId, 1);
  writeBrowserNavigation(a, { ...position, projectId: 1 });
  assert.deepEqual(readBrowserNavigation(a).tabs, {});
});

test('a new account or first-password session cannot inherit another session navigation', () => {
  for (const user of [{ ...admin, id: 10 }, { ...admin, initialPasswordSetupRequired: true }]) {
    assert.deepEqual(restoreBrowserNavigation(saved(), user, projects), { projectId: 1, pageId: PAGE_IDS.PERSONAL_INBOX, returnPageId: PAGE_IDS.PERSONAL_INBOX });
  }
  const tab = storage(); writeBrowserNavigation(tab, position); clearBrowserNavigation(tab);
  assert.equal(readBrowserNavigation(tab), null);
});

test('removed project falls back safely while revoked module keeps the project and returns to inbox', () => {
  assert.equal(restoreBrowserNavigation(saved(), admin, [{ id: 1 }]).projectId, 1);
  assert.equal(restoreBrowserNavigation(saved(), admin, [{ id: 1 }]).pageId, PAGE_IDS.PERSONAL_INBOX);
  const disabled = { ...admin, projectContexts: [{ projectId: 2, enabledBusinessModules: [] }] };
  assert.deepEqual(restoreBrowserNavigation(saved(), disabled, projects), { projectId: 2, pageId: PAGE_IDS.PERSONAL_INBOX, returnPageId: PAGE_IDS.PERSONAL_INBOX });
});

test('fresh project menus control restoration; a cached navigation entry grants no access', () => {
  const user = { id: 9, roles: [], menus: [{ menuCode: 'WEB_DOCUMENT' }], projectContexts: [{ projectId: 2, menuCodes: [], enabledBusinessModules: ['DOCUMENT'] }] };
  assert.equal(restoreBrowserNavigation(saved(), user, projects).pageId, PAGE_IDS.PERSONAL_INBOX);
  user.projectContexts[0].menuCodes = ['WEB_DOCUMENT'];
  assert.equal(restoreBrowserNavigation(saved(), user, projects).pageId, PAGE_IDS.DOCUMENT_MANAGEMENT);
});

test('project information return page is checked, and system management also works without projects', () => {
  const tab = storage();
  writeBrowserNavigation(tab, { ...position, pageId: PAGE_IDS.PROJECT_INFORMATION, returnPageId: PAGE_IDS.DOCUMENT_MANAGEMENT });
  assert.equal(restoreBrowserNavigation(readBrowserNavigation(tab), admin, projects).returnPageId, PAGE_IDS.DOCUMENT_MANAGEMENT);
  writeBrowserNavigation(tab, { ...position, projectId: null, pageId: PAGE_IDS.SYSTEM_MANAGEMENT });
  assert.equal(restoreBrowserNavigation(readBrowserNavigation(tab), admin, []).pageId, PAGE_IDS.SYSTEM_MANAGEMENT);
});

test('corrupt, legacy and retired-page snapshots cannot reopen hidden pages', () => {
  const tab = storage();
  for (const value of ['broken json', 'null', JSON.stringify({ ...position, version: 0 }), JSON.stringify({ ...position, version: 1, pageId: PAGE_IDS.OVERVIEW })]) {
    tab.setItem(BROWSER_NAVIGATION_KEY, value);
    assert.equal(readBrowserNavigation(tab), null);
    assert.equal(restoreBrowserNavigation(readBrowserNavigation(tab), admin, projects).pageId, PAGE_IDS.PERSONAL_INBOX);
  }
});

test('unavailable browser storage does not break loading or navigation', () => {
  const denied = { getItem() { throw Error('denied'); }, setItem() { throw Error('denied'); }, removeItem() { throw Error('denied'); } };
  assert.equal(readBrowserNavigation(denied), null);
  assert.doesNotThrow(() => writeBrowserNavigation(denied, position));
  assert.doesNotThrow(() => clearBrowserNavigation(denied));
});

test('hidden inbox is not restored and falls back to authorized committee without losing the project', () => {
  const user = { ...admin, projectContexts: admin.projectContexts.map((item) => ({ ...item, inboxEntryVisible: false })) };
  const snapshot = { ...saved(), pageId: PAGE_IDS.PERSONAL_INBOX, returnPageId: PAGE_IDS.PERSONAL_INBOX };
  assert.deepEqual(restoreBrowserNavigation(snapshot, user, projects), { projectId: 2, pageId: PAGE_IDS.SAFETY_COMMITTEE, returnPageId: PAGE_IDS.SAFETY_COMMITTEE });
  assert.equal(restoreBrowserNavigation(null, user, projects).pageId, PAGE_IDS.SAFETY_COMMITTEE);
  assert.equal(restoreBrowserNavigation(saved(), user, projects).pageId, PAGE_IDS.DOCUMENT_MANAGEMENT);
});
