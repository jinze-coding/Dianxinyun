export function passwordResetRequirements(password, confirmPassword) {
  const value = String(password || '');
  const confirmation = String(confirmPassword || '');
  return {
    validLength: value.length >= 8 && value.length <= 72,
    hasLetter: /[A-Za-z]/.test(value),
    hasNumber: /\d/.test(value),
    matches: Boolean(confirmation) && value === confirmation,
  };
}

export function validatePasswordReset(password, confirmPassword) {
  const value = String(password || '');
  const confirmation = String(confirmPassword || '');
  const requirements = passwordResetRequirements(value, confirmation);
  if (!requirements.validLength) return '密码长度必须为 8–72 位';
  if (!requirements.hasLetter || !requirements.hasNumber) return '密码必须同时包含字母和数字';
  if (!confirmation) return '请再次输入新密码';
  if (!requirements.matches) return '两次输入的密码不一致';
  return '';
}
