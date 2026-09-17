export function temporaryPasswordError(password, confirmation) {
  if (password.length < 8 || password.length > 72) return '临时密码长度必须为8–72位';
  if (!/[A-Za-z]/.test(password) || !/[0-9]/.test(password)) return '临时密码必须同时包含字母和数字';
  if (new TextEncoder().encode(password).length > 72) return '临时密码编码后不能超过72字节，请减少字符';
  if (password !== confirmation) return '两次输入的临时密码不一致';
  return '';
}
