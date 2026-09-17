// Existing local Web; every API request is intercepted with synthetic data.
import { createRequire } from 'node:module';
import { mkdir } from 'node:fs/promises';
import assert from 'node:assert/strict';
const { chromium } = createRequire(import.meta.url)(process.env.PLAYWRIGHT_PACKAGE || 'playwright');
const output = '/tmp/dianxinyun-quality-return-ui';
await mkdir(output, { recursive: true });
const browser = await chromium.launch({ channel: 'chrome', headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
page.setDefaultTimeout(10000);
const modules = ['SITE_ACCESS', 'DOCUMENT', 'INSPECTION', 'QUALITY', 'SAFETY_COMMITTEE'];
const project = { id: 1, projectName: '质量回退合成项目' };
const user = { id: 9999, username: 'ui-admin', realName: '合成管理员', roles: ['PLATFORM_ADMIN'], menus: [], permissionCodes: [], projectContexts: [{ projectId: 1, accessStatus: 'ACTIVE', enabledBusinessModules: modules, moduleConfigVersion: 1, menuCodes: ['WEB_QUALITY', 'QUALITY_ISSUES'], permissionCodes: ['quality.view', 'quality.manage'] }] };
const submitted = { id: 100, projectId: 1, inspectionNo: 'QA-WEEKLY-100', weekStart: '2026-09-14', weekEnd: '2026-09-20', inspectionDate: '2026-09-16', status: 'SUBMITTED', conclusion: '原检查结论', submittedIssueCount: 1, version: 150, submittedByName: '原提交人', submittedTime: '2026-09-16T10:00:00', overviewPhotoFileIds: [801], issues: [{ id: 501, title: '原问题', location: '原位置', status: 'PENDING', assigneeName: '合成整改人', deadline: '2026-09-20' }] };
const restored = { ...submitted, inspectionNo: null, status: 'DRAFT', version: 151, submittedIssueCount: 0, issues: [], lastEditedByName: '合成管理员', updateTime: '2026-09-16T11:00:00', draftItems: [{ id: 601, itemKey: 'returned-501-synthetic', itemOrder: 1, title: '原问题', description: '原问题说明', location: '原位置', severity: 'NORMAL', assigneeId: 99, assigneeName: '合成整改人', deadline: '2026-09-20', beforePhotoFileIds: [802] }] };
let current = submitted, conflict = false;
const requests = [], errors = [];
page.on('pageerror', (error) => errors.push(error.message));
await page.addInitScript(() => localStorage.setItem('site_platform_token', 'synthetic-quality-return'));
await page.route('**/api/**', async (route) => {
  const request = route.request(), url = new URL(request.url()), path = url.pathname.replace(/^\/api(?:\/v1)?/, '');
  requests.push({ path, method: request.method(), body: request.postDataJSON() });
  let data = { records: [], total: 0 };
  if (path === '/auth/user-info') data = user;
  else if (path === '/projects' || path === '/projects/my') data = [project];
  else if (path.endsWith('/business-modules')) data = { projectId: 1, enabledBusinessModules: modules, moduleConfigVersion: 1 };
  else if (path === '/quality/weekly-inspections/page') data = { records: [current], total: 1 };
  else if (path === '/quality/weekly-inspections/summary') data = { hasInspection: true, status: current.status, draftItemCount: current.draftItems?.length || 0 };
  else if (path === '/quality/weekly-inspections/100') data = current;
  else if (path === '/quality/issues/assignees') data = [{ userId: 99, displayName: '合成整改人' }];
  else if (path === '/quality/weekly-inspections/100/return-to-draft') {
    if (conflict) {
      await route.fulfill({ status: 409, contentType: 'application/json', body: JSON.stringify({ code: 409, message: '已有问题发生整改、复查或作废，不能退回草稿' }) });
      return;
    }
    current = restored; data = current;
  } else if (path === '/quality/weekly-inspections/100/draft') {
    const body = request.postDataJSON();
    current = { ...current, ...body, version: current.version + 1, draftItems: body.items };
    data = current;
  } else if (/^\/files\/\d+\/download$/.test(path)) {
    await route.fulfill({ contentType: 'image/png', body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+j9xkAAAAASUVORK5CYII=', 'base64') }); return;
  }
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, data }) });
});
const returns = () => requests.filter((request) => request.path.endsWith('/return-to-draft'));
try {
  await page.goto(process.env.WEB_BASE_URL || 'http://localhost:3002');
  await page.getByRole('button', { name: '质量周检', exact: true }).click();
  await page.getByRole('button', { name: '退回草稿', exact: true }).click();
  await page.getByRole('button', { name: '确认退回草稿', exact: true }).click();
  await page.getByRole('alert').filter({ hasText: '请填写退回原因' }).waitFor();
  assert.equal(returns().length, 0);
  await page.getByLabel('退回原因', { exact: true }).fill('误点提交，需要补充问题');
  await page.screenshot({ path: output + '/return-confirmation.png', fullPage: true });
  await page.getByRole('button', { name: '取消', exact: true }).click();
  assert.equal(returns().length, 0);
  await page.getByRole('button', { name: '查看', exact: true }).click();
  await page.getByText('质量周检详情 · QA-WEEKLY-100', { exact: true }).waitFor();
  await page.getByRole('button', { name: '退回草稿', exact: true }).last().click();
  const reason = page.getByLabel('退回原因', { exact: true });
  assert.equal(await reason.inputValue(), '');
  await reason.fill('误点提交，需要补充问题');
  conflict = true;
  await page.getByRole('button', { name: '确认退回草稿', exact: true }).click();
  await page.getByRole('alert').filter({ hasText: '不能退回草稿' }).waitFor();
  assert.equal(await reason.inputValue(), '误点提交，需要补充问题');
  assert.equal(current.status, 'SUBMITTED');
  assert.deepEqual(returns()[0].body, { expectedVersion: 150, reason: '误点提交，需要补充问题' });
  conflict = false;
  await page.getByRole('button', { name: '确认退回草稿', exact: true }).click();
  await page.getByText('编辑质量周检 · 2026-09-14 至 2026-09-20', { exact: true }).waitFor();
  assert.equal(await page.getByLabel('检查结论（无问题时必填）').inputValue(), '原检查结论');
  assert.equal(await page.getByLabel('问题标题 *', { exact: true }).inputValue(), '原问题');
  assert.equal(await page.getByLabel(/^整改负责人/).inputValue(), '99');
  await page.getByText(/^已保存照片 #801/).waitFor();
  await page.getByText(/^已保存照片 #802/).waitFor();
  assert.equal(await page.getByRole('button', { name: '退回草稿', exact: true }).count(), 0);
  await page.getByRole('button', { name: '添加问题', exact: true }).click();
  assert.equal(await page.getByLabel('问题标题 *', { exact: true }).count(), 2);
  await page.getByLabel('问题标题 *', { exact: true }).last().fill('补充问题');
  await page.getByRole('button', { name: '保存草稿', exact: true }).click();
  await page.getByText('共享草稿 · 版本 152 · 已同步', { exact: true }).waitFor();
  const saved = requests.find((request) => request.path.endsWith('/100/draft')).body;
  assert.equal(saved.expectedVersion, 151);
  assert.equal(saved.items[1].title, '补充问题');
  assert.deepEqual(saved.items[0].beforePhotoFileIds, [802]);
  assert.deepEqual(saved.overviewPhotoFileIds, [801]);
  await page.screenshot({ path: output + '/restored-draft.png', fullPage: true });
  // Reload as an ordinary quality manager: view/edit authority must not expose return.
  current = submitted; user.roles = ['QUALITY_MANAGER'];
  await page.reload();
  await page.getByRole('button', { name: '质量周检', exact: true }).click();
  await page.getByText('QA-WEEKLY-100', { exact: true }).waitFor();
  assert.equal(await page.getByRole('button', { name: '退回草稿', exact: true }).count(), 0);
  await page.getByRole('button', { name: '查看', exact: true }).click();
  await page.getByText('质量周检详情 · QA-WEEKLY-100', { exact: true }).waitFor();
  assert.equal(await page.getByRole('button', { name: '退回草稿', exact: true }).count(), 0);
  assert.deepEqual(errors, []);
  console.log('Passed: admin row/detail entry, required reason, cancellation, conflict preservation, restored contents/photos, add-and-save draft, ordinary manager cannot return. Synthetic API only.');
} catch (error) {
  await page.screenshot({ path: output + '/failure.png', fullPage: true });
  console.error((await page.locator('body').innerText()).slice(-4500), errors); throw error;
} finally { await browser.close(); }
