import assert from 'node:assert/strict';
import { access, readFile, readdir, stat } from 'node:fs/promises';
import path from 'node:path';

const expectedApiBase = 'https://zhihuiyz.xyz/api/v1';
const expectedAppId = 'wxc2c8114ac4b5679a';
const expectedVersion = '0.1.8';
const expectedVersionCode = '108';
const expectedBuildId = '0.1.8-20260901-prod-compat';
const expectedPageCount = 43;
const sourceRoot = path.resolve('src');
const buildRoot = path.resolve('dist/build/mp-weixin');

const criticalPages = [
  'pages/todo/index',
  'pages/documents/index',
  'pages/document-distribution/detail',
  'pages/inspection/index',
  'pages/inspection/edge-tasks',
  'pages/inspection/edge-form',
  'pages/rectification/edge-list',
  'pages/rectification/edge-detail',
  'pages/quality/index',
  'pages/quality/weekly-list',
  'pages/quality/weekly-edit',
  'pages/quality/weekly-detail',
  'pages/quality/issues',
  'pages/quality/issue-detail',
  'pages/quality/documents',
  'pages/public/visitor-invite',
  'pages/public/meeting-invite',
  'pages/public/guard-visitor-register',
  'pages/seal/entry',
  'pages/seal/list',
  'pages/seal/apply',
  'pages/seal/detail',
  'pages/projects/detail',
];

const requiredBusinessStrings = [
  '/quality/weekly-inspections',
  '/me/document-distributions/',
  '/edge-inspections',
  '/public/site-access/meeting/session',
  '/public/site-access/meeting/submit',
  '/me/inbox',
  'QUALITY_ISSUE_DETAIL',
  'DOCUMENT_DISTRIBUTION_DETAIL',
];

async function readJson(file) {
  return JSON.parse(await readFile(file, 'utf8'));
}

async function collectFiles(directory, predicate = () => true) {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) files.push(...await collectFiles(fullPath, predicate));
    if (entry.isFile() && predicate(fullPath)) files.push(fullPath);
  }
  return files;
}

function flattenPages(config) {
  return [
    ...(config.pages || []).map((page) => typeof page === 'string' ? page : page.path),
    ...(config.subPackages || []).flatMap((pack) =>
      (pack.pages || []).map((page) => `${pack.root}/${typeof page === 'string' ? page : page.path}`)),
  ];
}

const packageJson = await readJson(path.resolve('package.json'));
const packageLock = await readJson(path.resolve('package-lock.json'));
const manifest = await readJson(path.join(sourceRoot, 'manifest.json'));
const pagesConfig = await readJson(path.join(sourceRoot, 'pages.json'));
const releaseSource = await readFile(path.join(sourceRoot, 'constants/release.ts'), 'utf8');
const projectConfig = await readJson(path.join(buildRoot, 'project.config.json'));
const builtApp = await readJson(path.join(buildRoot, 'app.json'));
const requestCode = await readFile(path.join(buildRoot, 'api/request.js'), 'utf8');
const authCode = await readFile(path.join(buildRoot, 'api/auth.js'), 'utf8');
const scanCode = await readFile(path.join(buildRoot, 'utils/electricBoxScan.js'), 'utf8');
const javascriptFiles = await collectFiles(buildRoot, (file) => file.endsWith('.js'));
const allJavaScript = (await Promise.all(javascriptFiles.map((file) => readFile(file, 'utf8')))).join('\n');

assert.equal(packageJson.version, expectedVersion, 'package.json 正式版本不正确');
assert.equal(packageLock.version, expectedVersion, 'package-lock 顶层版本不正确');
assert.equal(packageLock.packages?.['']?.version, expectedVersion, 'package-lock 根包版本不正确');
assert.equal(manifest.versionName, expectedVersion, 'manifest versionName 不正确');
assert.equal(String(manifest.versionCode), expectedVersionCode, 'manifest versionCode 不正确');
assert.equal(manifest.uniStatistics?.enable, false, '正式小程序必须显式关闭 DCloud uni 统计');
assert.ok(!String(manifest.description || '').includes('原型'), '正式 manifest 仍标记为原型');
assert.ok(releaseSource.includes(`'${expectedBuildId}'`), '源码构建编号与正式候选不一致');

assert.equal(projectConfig.appid, expectedAppId, '正式构建 AppID 不正确');
assert.equal(projectConfig.setting?.urlCheck, true, '正式构建未启用微信合法域名校验');
assert.ok(requestCode.includes(expectedApiBase), '正式构建缺少生产 API 地址');
assert.ok(authCode.includes('/auth/wechat/mini/login'), '正式构建缺少微信快捷登录接口');
assert.ok(allJavaScript.includes(expectedBuildId), '正式构建缺少可识别的构建编号');
assert.ok(!/https?:\/\/(?:localhost|127\.0\.0\.1|10\.|192\.168\.|172\.(?:1[6-9]|2\d|3[01])\.)/i.test(allJavaScript), '正式构建包含本机或私网 URL');
assert.ok(!/http:\/\//i.test(allJavaScript), '正式构建包含非 HTTPS URL');
assert.ok(!allJavaScript.includes('touristappid'), '正式构建包含游客 AppID');
assert.ok(!allJavaScript.includes('tongji.dcloud.io'), '正式构建仍包含 DCloud 统计上报域名');
assert.ok(!allJavaScript.includes('tongji-collector.dcloud.net.cn'), '正式构建仍包含 DCloud 统计采集域名');
assert.ok(requestCode.includes('exports.USE_MOCK=!1'), '正式构建没有明确关闭 mock');
assert.ok(!requestCode.includes('exports.USE_MOCK=!0'), '正式构建错误启用了 mock');
assert.ok(scanCode.includes('scanCode'), '正式构建缺少微信扫码调用');
assert.ok(scanCode.includes('wxCode'), '正式构建没有启用微信小程序码识别');

const sourcePages = flattenPages(pagesConfig);
const builtPages = flattenPages(builtApp);
assert.equal(sourcePages.length, expectedPageCount, `源码页面数应为 ${expectedPageCount}`);
assert.deepEqual([...new Set(sourcePages)].sort(), [...new Set(builtPages)].sort(), '构建页面清单与源码不一致');
assert.deepEqual(
  (builtApp.tabBar?.list || []).map((item) => item.pagePath),
  (pagesConfig.tabBar?.list || []).map((item) => item.pagePath),
  '正式构建 tabBar 与源码不一致',
);

for (const page of criticalPages) {
  assert.ok(sourcePages.includes(page), `正式源码缺少关键页面：${page}`);
}
for (const page of sourcePages) {
  for (const extension of ['js', 'wxml', 'wxss', 'json']) {
    await access(path.join(buildRoot, `${page}.${extension}`));
  }
}
for (const value of requiredBusinessStrings) {
  assert.ok(allJavaScript.includes(value), `正式构建缺少关键业务链路：${value}`);
}

const artifactFiles = await collectFiles(buildRoot);
const artifactBytes = (await Promise.all(artifactFiles.map((file) => stat(file)))).reduce((sum, value) => sum + value.size, 0);
console.log(`mp-weixin real artifact verified: ${expectedBuildId}; pages=${sourcePages.length}; files=${artifactFiles.length}; bytes=${artifactBytes}`);
