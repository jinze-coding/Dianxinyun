// uni-app can silently omit <style src>; verify the CSS shipped to WeChat, not only SFC source.
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import assert from 'node:assert/strict';
const directory = resolve(process.argv[2] || 'dist/build/mp-weixin');
for (const page of ['visitor-invite', 'meeting-invite', 'guard-visitor-register', 'meeting-check-in']) {
  const base = resolve(directory, 'pages/public', page);
  const css = await readFile(base + '.wxss', 'utf8');
  const template = await readFile(base + '.wxml', 'utf8');
  const scope = css.match(/\.visitor-field\.(data-v-[a-z0-9]+)/)?.[1];
  assert.ok(scope && template.includes(scope), `${page}: shared style scope must match rendered template`);
  assert.match(css, /height:\s*88rpx;\s*min-height:\s*44px;\s*padding:\s*0 20rpx/, `${page}: native input height and horizontal padding missing`);
  assert.match(css, /height:\s*80rpx;\s*min-height:\s*40px;[\s\S]*border-radius:\s*14rpx/, `${page}: rounded travel buttons missing`);
  assert.match(css, /\.entry-navbar[^{}]*\{position:\s*sticky/, `${page}: sticky safe-area navigation missing`);
  assert.doesNotMatch(template, /常用来访资料|将本次人员和车辆信息保存为常用资料|记住本人和车辆信息/, `${page}: removed profile controls remain in package`);
}
console.log('WeChat package: all four visitor pages contain scoped native-input, travel-button and sticky-navigation styles');
