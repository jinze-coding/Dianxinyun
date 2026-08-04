import assert from 'node:assert/strict';
import { readFile, readdir } from 'node:fs/promises';
import path from 'node:path';

const expectedApiBase = 'https://zhihuiyz.xyz/api/v1';
const expectedAppId = 'wxc2c8114ac4b5679a';
const expectedBuildId = '0.1.2-20260803-scan-domain';
const buildRoot = path.resolve('dist/build/mp-weixin');

async function collectJavaScriptFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) files.push(...await collectJavaScriptFiles(fullPath));
    if (entry.isFile() && entry.name.endsWith('.js')) files.push(fullPath);
  }
  return files;
}

const requestCode = await readFile(path.join(buildRoot, 'api/request.js'), 'utf8');
const authCode = await readFile(path.join(buildRoot, 'api/auth.js'), 'utf8');
const scanCode = await readFile(path.join(buildRoot, 'utils/electricBoxScan.js'), 'utf8');
const projectConfig = JSON.parse(await readFile(path.join(buildRoot, 'project.config.json'), 'utf8'));
const allJavaScript = (await Promise.all((await collectJavaScriptFiles(buildRoot)).map((file) => readFile(file, 'utf8')))).join('\n');

assert.equal(projectConfig.appid, expectedAppId, '正式构建 AppID 不正确');
assert.ok(requestCode.includes(expectedApiBase), '正式构建缺少生产 API 地址');
assert.ok(authCode.includes('/auth/wechat/mini/login'), '正式构建缺少微信快捷登录接口');
assert.ok(allJavaScript.includes(expectedBuildId), '正式构建缺少可识别的构建编号');
assert.ok(!/https?:\/\/(?:localhost|127\.0\.0\.1|10\.|192\.168\.|172\.(?:1[6-9]|2\d|3[01])\.)/i.test(allJavaScript), '正式构建包含本机或私网地址');
assert.ok(!requestCode.includes('exports.USE_MOCK=!0'), '正式构建错误启用了 mock');
assert.ok(scanCode.includes('scanCode'), '正式构建缺少微信扫码调用');
assert.ok(scanCode.includes('wxCode'), '正式构建没有启用微信小程序码识别');

console.log(`mp-weixin real artifact verified: ${expectedBuildId}`);
