import { useState } from 'react';
import { setupInitialPassword } from '../../services/auth';
import { validatePasswordReset } from '../../utils/passwordReset';
import './initialPassword.css';

export default function InitialPasswordPage({ user, onComplete, onLogout }) {
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [saved, setSaved] = useState(false);
  const submit = async (event) => {
    event.preventDefault(); if (busy) return;
    const issue = validatePasswordReset(password, confirmation);
    if (issue) { setError(issue); return; }
    setBusy(true); setError('');
    try { await setupInitialPassword(password); setPassword(''); setConfirmation(''); setSaved(true); await onComplete(); }
    catch (failure) { setError(failure.message || '密码设置失败，请重试'); }
    finally { setBusy(false); }
  };
  return <main className="initial-password-page"><form className="initial-password-card" onSubmit={submit}>
    <div className="initial-password-brand">智慧营造 · 账号启用</div>
    <h1>设置个人登录密码</h1>
    <p>{user.initialPasswordSetupReason === 'ADMIN_IMPORT' ? '首次登录请更换管理员发放的临时密码。新密码不能与临时密码相同。' : '首次使用请先设置登录密码。'}</p>
    <div className="initial-password-account">登录账号 <strong>{user.username}</strong></div>
    {!saved && <><label>新密码<input autoFocus type="password" autoComplete="new-password" minLength={8} maxLength={72} value={password} onChange={(e) => setPassword(e.target.value)} placeholder="8–72 位，包含字母和数字" disabled={busy} /></label>
      <label>确认新密码<input type="password" autoComplete="new-password" maxLength={72} value={confirmation} onChange={(e) => setConfirmation(e.target.value)} placeholder="再次输入新密码" disabled={busy} /></label></>}
    <p className="initial-password-tip">完成后进入个人待办。微信绑定请在小程序登录后，从“我的”中操作。</p>
    {error && <div role="alert" className="initial-password-error">{error}</div>}
    {saved ? <button type="button" className="initial-password-primary" onClick={onComplete}>密码已设置，重新加载</button> : <button className="initial-password-primary" disabled={busy}>{busy ? '正在设置…' : '确认并进入系统'}</button>}
    <button type="button" disabled={busy} className="initial-password-logout" onClick={onLogout}>退出登录</button>
  </form></main>;
}
