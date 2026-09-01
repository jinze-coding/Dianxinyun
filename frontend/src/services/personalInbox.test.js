import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const source = readFileSync(new URL('./personalInbox.js', import.meta.url), 'utf8');

test('notification query contract refuses a group and an exact business type together', () => {
  assert.match(source, /params\.businessType && params\.businessGroup/);
  assert.match(source, /businessType 与 businessGroup 不能同时提交/);
});
