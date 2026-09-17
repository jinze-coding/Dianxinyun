import test from 'node:test';
import assert from 'node:assert/strict';
import { temporaryPasswordError } from './temporaryPassword.js';

test('管理员设置密码允许简单字母数字组合和72位边界', () => {
  for (const value of ['Import12', 'A1' + 'x'.repeat(70)]) assert.equal(temporaryPasswordError(value, value), '');
});
test('空值、过短、过长以及纯数字或纯字母不能提交', () => {
  for (const value of ['', 'Aa12345', 'abcdefgh', '12345678', 'A1' + 'x'.repeat(71)]) assert.ok(temporaryPasswordError(value, value));
});
test('重复输入必须完全相同，不默默裁剪或更改密码', () => {
  assert.match(temporaryPasswordError('Import12', 'Import13'), /不一致/);
  assert.match(temporaryPasswordError('Import12 ', 'Import12'), /不一致/);
});
test('多字节密码在BCrypt上限内可用，超过上限不能提交', () => {
  const accepted = 'A1' + '中'.repeat(23); const rejected = 'A1' + '中'.repeat(24);
  assert.equal(temporaryPasswordError(accepted, accepted), '');
  assert.match(temporaryPasswordError(rejected, rejected), /72字节/);
});
