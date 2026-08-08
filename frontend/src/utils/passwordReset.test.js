import test from 'node:test';
import assert from 'node:assert/strict';
import { passwordResetRequirements, validatePasswordReset } from './passwordReset.js';

test('password reset validation matches the backend strength and confirmation rules', () => {
  assert.equal(validatePasswordReset('short1', 'short1'), '密码长度必须为 8–72 位');
  assert.equal(validatePasswordReset('abcdefgh', 'abcdefgh'), '密码必须同时包含字母和数字');
  assert.equal(validatePasswordReset('12345678', '12345678'), '密码必须同时包含字母和数字');
  assert.equal(validatePasswordReset('Secure123', ''), '请再次输入新密码');
  assert.equal(validatePasswordReset('Secure123', 'Secure124'), '两次输入的密码不一致');
  assert.equal(validatePasswordReset('Secure123', 'Secure123'), '');
  assert.deepEqual(passwordResetRequirements('Secure123', 'Secure123'), {
    validLength: true,
    hasLetter: true,
    hasNumber: true,
    matches: true,
  });
});
