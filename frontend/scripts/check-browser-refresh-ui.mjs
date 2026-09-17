// Browser regression against the existing local Web, with synthetic APIs only.
// PLAYWRIGHT_PACKAGE may point to the already-installed desktop runtime package.
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';
import { mkdir } from 'node:fs/promises';
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_PACKAGE || 'playwright');
const output = '/tmp/dianxinyun-refresh-ui';
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ channel: 'chrome', headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
const modules = ['SITE_ACCESS', 'DOCUMENT', 'INSPECTION', 'QUALITY', 'SAFETY_COMMITTEE'];
const projects = [{ id: 1, projectName: '刷新验收甲', projectStatus: 'building' }, { id: 2, projectName: '刷新验收乙', projectStatus: 'building' }];
let user = { id: 9, realName: '刷新验收管理员', roles: ['PLATFORM_ADMIN'], menus: [], permissionCodes: [], projectContexts: projects.map(({ id }) => ({ projectId: id, accessStatus: 'ACTIVE', enabledBusinessModules: modules, moduleConfigVersion: 1, menuCodes: [], permissionCodes: [] })) };
let projectGate = null, failProjects = false, missingRecord = false;
const record = { id: 41, projectId: 2, inspectorName: '刷新验收检查人', category: '施工安全管理', conclusion: '合成巡检记录', attachments: [], logs: [], version: 1 };
const meeting = { id: 71, projectId: 2, projectName: projects[1].projectName, inviteNo: 'UI-MEETING', inviteType: 'MEETING', purpose: '刷新会议详情验收', status: 'OPEN', visitStartTime: '2026-09-15T09:00:00', visitEndTime: '2026-09-15T18:00:00', auditLogs: [] };
const calls = [], errors = [];
await context.route('**/api/**', async route => {
  const url = new URL(route.request().url());
  if (!url.pathname.startsWith('/api/')) return route.continue();
  const path = url.pathname.replace(/^\/api(?:\/v1)?/, '');
  calls.push({ path, projectId: url.searchParams.get('projectId') });
  let data = { records: [], total: 0 };
  if (path === '/auth/user-info') data = user;
  else if (path === '/auth/login') data = { token: 'synthetic-session', ...user };
  else if (path === '/auth/logout') data = null;
  else if (path === '/safety-committee/records') data = missingRecord ? { records: [], total: 0, latestId: null } : { records: [record], total: 1, latestId: 41 };
  else if (path === '/safety-committee/records/41') {
    if (missingRecord) return route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ code: 404, message: '合成记录已删除' }) });
    data = record;
  }
  else if (path === '/site-access/invitations') data = { records: [meeting], total: 1 };
  else if (path === '/site-access/invitations/71') data = meeting;
  else if (path.endsWith('/materials/activities')) data = [];
  else if (path === '/projects' || path === '/projects/my') {
    if (projectGate) await projectGate;
    if (failProjects) return route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 503, message: '合成项目加载失败' }) });
    data = projects;
  } else if (path.endsWith('/business-modules')) {
    const id = Number(path.split('/')[2]);
    data = user.projectContexts.find(item => item.projectId === id) || { projectId: id, enabledBusinessModules: [], moduleConfigVersion: 1 };
  } else if (path.endsWith('/categories') || ['/document-folders', '/files', '/project-members', '/quality/issues/assignees', '/electric-boxes', '/inspection/records', '/inspection/todos', '/inspection/rectifications', '/edge-inspections/tasks', '/edge-inspections/rectifications', '/edge-inspections/points'].includes(path) || /\/(point-types|user-options|users|host-options)$/.test(path) && !path.startsWith('/system/')) data = [];
  else if (path === '/personal-inbox/notifications/unread-count') data = { count: 0 };
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, message: 'success', data }) });
});
context.setDefaultTimeout(10000);
const page = await context.newPage();
page.on('pageerror', e => errors.push(e.message));
const url = process.env.REFRESH_UI_URL || 'http://localhost:3002';
const snapshot = tab => tab.evaluate(() => JSON.parse(sessionStorage.getItem('site_platform_tab_navigation') || 'null'));
const ready = async tab => { await tab.getByRole('button', { name: /刷新验收[甲乙] ▼/ }).waitFor(); await tab.locator('main > *').first().waitFor(); };
const assertPosition = async (tab, projectId, pageId, tabs = {}) => {
  await ready(tab);
  const value = await snapshot(tab);
  assert.equal(value.projectId, projectId); assert.equal(value.pageId, pageId);
  for (const [key, item] of Object.entries(tabs)) assert.equal(value.tabs[key], item);
};
try {
  await page.goto(url);
  await page.evaluate(() => localStorage.setItem('site_platform_token', 'synthetic-session'));
  await page.reload(); await ready(page);
  await page.getByRole('button', { name: '刷新验收甲 ▼' }).click();
  await page.getByText('刷新验收乙', { exact: true }).click();
  await page.getByRole('button', { name: '安委会巡检', exact: true }).click();
  await assertPosition(page, 2, 'safety_committee');
  let release;
  projectGate = new Promise(resolve => { release = resolve; });
  await page.reload();
  await page.getByText('正在加载项目…', { exact: true }).waitFor();
  assert.equal((await snapshot(page)).projectId, 2, 'pending project list must not overwrite the selection');
  assert.equal((await snapshot(page)).pageId, 'safety_committee', 'pending authorization must not write the default inbox');
  assert.equal(await page.getByRole('heading', { name: '个人待办与消息' }).count(), 0);
  projectGate = null; release();
  await assertPosition(page, 2, 'safety_committee');
  await page.getByRole('heading', { name: '安委会巡检' }).waitFor();
  await page.screenshot({ path: `${output}/committee-reloaded.png` });
  await page.getByRole('button', { name: '详情', exact: true }).click();
  await page.getByRole('heading', { name: '刷新验收检查人的巡检记录' }).waitFor();
  await page.reload();
  await page.getByRole('heading', { name: '刷新验收检查人的巡检记录' }).waitFor();
  await assertPosition(page, 2, 'safety_committee', { committeeId: '41' });
  missingRecord = true;
  await page.reload(); await page.getByText('暂无巡检记录', { exact: true }).waitFor();
  await assertPosition(page, 2, 'safety_committee', { committeeId: '' });
  missingRecord = false;


  await page.getByRole('button', { name: '资料管理', exact: true }).click();
  await page.getByRole('tab', { name: /用印管理/ }).click();
  await page.getByRole('tab', { name: /用印台账/ }).click();
  await page.reload();
  await assertPosition(page, 2, 'document_management', { documentTab: 'seal', sealTab: 'ledger' });
  assert.equal(await page.getByRole('tab', { name: /用印台账/ }).getAttribute('aria-selected'), 'true');

  await page.getByRole('button', { name: '巡检管理', exact: true }).click();
  await page.getByRole('button', { name: '临边巡检', exact: true }).click();
  await page.getByRole('button', { name: '巡检记录', exact: true }).click();
  await page.reload();
  await assertPosition(page, 2, 'electric_inspection', { inspectionArea: 'edge', edgeTab: 'records' });
  assert.match(await page.getByRole('button', { name: '巡检记录', exact: true }).getAttribute('class'), /active/);

  await page.getByRole('button', { name: '场内管理', exact: true }).click();
  await page.getByRole('button', { name: '详情', exact: true }).click();
  await page.getByRole('heading', { name: meeting.purpose }).waitFor();
  await page.getByRole('button', { name: '操作记录', exact: true }).click();
  await page.reload();
  await page.getByRole('heading', { name: meeting.purpose }).waitFor();
  await assertPosition(page, 2, 'site_access', { meetingId: '71', meetingTab: 'activity' });
  assert.equal(await page.getByRole('button', { name: '操作记录', exact: true }).getAttribute('class'), 'active');
  await page.getByRole('button', { name: '← 返回邀请列表', exact: true }).click();
  await page.reload();
  await assertPosition(page, 2, 'site_access', { meetingId: '' });
  assert.equal(await page.getByRole('heading', { name: meeting.purpose }).count(), 0);

  await page.getByRole('button', { name: /系统管理/ }).click();
  await page.getByRole('button', { name: /用户管理/ }).click();
  await page.reload();
  await assertPosition(page, 2, 'system_management', { systemTab: 'users' });
  await page.getByRole('button', { name: '批量导入', exact: true }).waitFor();
  await page.screenshot({ path: `${output}/system-users-reloaded.png` });

  const second = await context.newPage();
  await second.goto(url); await ready(second);
  await second.getByRole('button', { name: '质量周检', exact: true }).click();
  await assertPosition(second, 1, 'quality_management');
  await second.getByRole('tab', { name: /质量资料/ }).click();
  await page.reload(); await assertPosition(page, 2, 'system_management', { systemTab: 'users' });
  await second.reload(); await assertPosition(second, 1, 'quality_management', { qualityTab: 'documents' });

  failProjects = true;
  await page.reload(); await page.getByRole('alert').waitFor();
  assert.equal((await snapshot(page)).projectId, 2);
  assert.equal((await snapshot(page)).pageId, 'system_management');
  failProjects = false; await page.getByRole('button', { name: '重新加载', exact: true }).click();
  await assertPosition(page, 2, 'system_management', { systemTab: 'users' });

  await page.getByRole('button', { name: '安委会巡检', exact: true }).click();
  await assertPosition(page, 2, 'safety_committee');
  user = { ...user, projectContexts: user.projectContexts.map(item => ({ ...item, enabledBusinessModules: item.projectId === 2 ? [] : modules })) };
  await page.reload(); await assertPosition(page, 2, 'personal_inbox');
  await page.getByRole('heading', { name: '个人待办与消息' }).waitFor();

  user = { ...user, id: 10 };
  await page.reload(); await assertPosition(page, 1, 'personal_inbox');
  await page.getByRole('button', { name: '退出', exact: true }).click();
  await page.getByRole('button', { name: '登录系统', exact: true }).waitFor();
  assert.equal(await snapshot(page), null, 'logout clears the tab position');

  user = { ...user, initialPasswordSetupRequired: true };
  calls.length = 0;
  await page.evaluate(() => localStorage.setItem('site_platform_token', 'synthetic-session'));
  await page.reload(); await page.getByRole('heading', { name: '设置个人登录密码' }).waitFor();
  assert.ok(calls.every(call => call.path === '/auth/user-info'));
  assert.deepEqual(errors, []);
  console.log('PASS: browser reload, delayed/failed project loading and retry, nested tabs, two independent tabs, revoked module, account change, logout, first-password gate.');
} catch (error) {
  await page.screenshot({ path: `${output}/failure.png` });
  console.error((await page.locator('body').innerText()).slice(-4000), errors, calls.slice(-25));
  throw error;
} finally { await browser.close(); }
