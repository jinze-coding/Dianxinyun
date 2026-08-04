import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { DEFAULT_THEME_ID, getThemeById } from '../../constants/themes';
import {
  createWebQrChallenge,
  exchangeWebQrChallenge,
  getRegistrationCaptcha,
  getWebQrChallengeStatus,
  login,
} from '../../services/auth';
import {
  cancelRegistrationApplication,
  queryRegistrationApplicationStatus,
  submitRegistrationApplication,
} from '../../services/registration';
import './index.css';

const QR_STATUS_TEXT = {
  WAITING: '请使用微信扫一扫',
  SCANNED: '已扫码，请在小程序确认登录',
  CONFIRMED: '已确认，正在登录',
  CANCELLED: '本次登录已取消',
  EXPIRED: '二维码已过期',
  CONSUMED: '二维码已使用',
};

const APPLICATION_STATUS_TEXT = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  CANCELLED: '已取消',
};

const createEmptyRegistration = () => ({
  password: '',
  confirmPassword: '',
  realName: '',
  phone: '',
  email: '',
  desiredProjectName: '',
  applicationReason: '',
  captchaId: '',
  captchaCode: '',
  source: 'WEB',
  sourceType: 'WEB',
  phoneVerificationType: 'MANUAL_REVIEW',
});

function normalizeQrImage(data) {
  const value = data?.qrCodeImage || data?.qrImage || data?.miniProgramCodeBase64 || data?.qrCodeUrl || data?.qrCode;
  if (!value) return '';
  if (/^(data:|https?:|blob:)/.test(value)) return value;
  return `data:image/png;base64,${value}`;
}

function Field({ label, required, children }) {
  return (
    <label className="login-field">
      <span>{label}{required && <em>*</em>}</span>
      {children}
    </label>
  );
}

export default function LoginPage({ onLogin, theme }) {
  const T = theme || getThemeById(DEFAULT_THEME_ID);
  const [mode, setMode] = useState('PASSWORD');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(false);
  const [captcha, setCaptcha] = useState(null);
  const [registration, setRegistration] = useState(createEmptyRegistration);
  const [queryToken, setQueryToken] = useState('');
  const [applicationStatus, setApplicationStatus] = useState(null);
  const [challenge, setChallenge] = useState(null);
  const [qrStatus, setQrStatus] = useState('WAITING');
  const [qrBusy, setQrBusy] = useState(false);
  const qrRequestIdRef = useRef(0);
  const qrPollTimerRef = useRef(null);
  const qrExchangeStartedRef = useRef(false);

  const updateRegistration = (field, value) => {
    setRegistration((current) => ({ ...current, [field]: value }));
  };

  const loadCaptcha = useCallback(async () => {
    try {
      const res = await getRegistrationCaptcha();
      if (res.code !== 200) throw new Error(res.message || '验证码加载失败');
      setCaptcha(res.data || {});
      setRegistration((current) => ({
        ...current,
        captchaId: res.data?.captchaId || res.data?.id || res.data?.key || '',
        captchaCode: '',
      }));
    } catch (err) {
      setError(err.message || '验证码加载失败');
    }
  }, []);

  const createChallenge = useCallback(async () => {
    const requestId = ++qrRequestIdRef.current;
    setQrBusy(true);
    setError('');
    setChallenge(null);
    setQrStatus('WAITING');
    qrExchangeStartedRef.current = false;
    try {
      const res = await createWebQrChallenge({
        browserName: navigator.userAgent,
        websiteName: document.title || '电信云平台',
      });
      if (requestId !== qrRequestIdRef.current) return;
      if (res.code !== 200 || !res.data) throw new Error(res.message || '二维码生成失败');
      setChallenge({
        ...res.data,
        challengeId: res.data.challengeId || res.data.id,
        browserVerifier: res.data.browserVerifier || res.data.browserSecret || res.data.verifier,
      });
      setQrStatus(res.data.status || res.data.state || 'WAITING');
    } catch (err) {
      if (requestId === qrRequestIdRef.current) setError(err.message || '二维码生成失败');
    } finally {
      if (requestId === qrRequestIdRef.current) setQrBusy(false);
    }
  }, []);

  useEffect(() => {
    if (mode === 'REGISTER' && !captcha) loadCaptcha();
  }, [captcha, loadCaptcha, mode]);

  useEffect(() => {
    if (mode !== 'QR') {
      qrRequestIdRef.current += 1;
      return undefined;
    }
    createChallenge();
    return () => {
      qrRequestIdRef.current += 1;
    };
  }, [createChallenge, mode]);

  useEffect(() => {
    if (mode !== 'QR' || !challenge?.challengeId) return undefined;
    if (qrExchangeStartedRef.current) return undefined;
    let cancelled = false;
    let polling = false;

    const stopPolling = () => {
      if (qrPollTimerRef.current) {
        window.clearInterval(qrPollTimerRef.current);
        qrPollTimerRef.current = null;
      }
    };

    const poll = async () => {
      if (polling || cancelled) return;
      polling = true;
      try {
        const res = await getWebQrChallengeStatus(challenge.challengeId, {
          browserVerifier: challenge.browserVerifier,
          browserSecret: challenge.browserVerifier,
        });
        if (cancelled) return;
        if (res.code !== 200) throw new Error(res.message || '扫码状态查询失败');
        const data = res.data || {};
        const nextStatus = data.status || data.state || 'WAITING';
        setQrStatus(nextStatus);
        if (['CANCELLED', 'EXPIRED', 'CONSUMED'].includes(nextStatus)) {
          stopPolling();
          return;
        }
        if (nextStatus === 'CONFIRMED' && data.exchangeCode && !qrExchangeStartedRef.current) {
          qrExchangeStartedRef.current = true;
          stopPolling();
          const exchange = await exchangeWebQrChallenge(challenge.challengeId, {
            browserVerifier: challenge.browserVerifier,
            browserSecret: challenge.browserVerifier,
            exchangeCode: data.exchangeCode,
          });
          if (exchange.code !== 200) throw new Error(exchange.message || '扫码登录失败');
          setQrStatus('CONSUMED');
          onLogin?.();
        }
      } catch (err) {
        if (!cancelled) {
          setError(err.message || '扫码状态查询失败');
          if (qrExchangeStartedRef.current) {
            qrExchangeStartedRef.current = false;
            setQrStatus('EXPIRED');
          }
        }
      } finally {
        polling = false;
      }
    };

    poll();
    qrPollTimerRef.current = window.setInterval(poll, 2000);
    return () => {
      cancelled = true;
      stopPolling();
    };
  }, [challenge, mode, onLogin]);

  const captchaImage = useMemo(() => {
    const value = captcha?.imageBase64 || captcha?.image || captcha?.imageUrl;
    if (!value) return '';
    if (/^(data:|https?:|blob:)/.test(value)) return value;
    return `data:image/png;base64,${value}`;
  }, [captcha]);

  const handlePasswordLogin = async (event) => {
    event.preventDefault();
    setError('');
    setNotice('');
    if (!username.trim() || !password) {
      setError('请输入用户名和密码');
      return;
    }
    setLoading(true);
    try {
      const res = await login(username.trim(), password);
      if (res.code !== 200) throw new Error(res.message || '登录失败');
      onLogin?.();
    } catch (err) {
      setError(err.message || '网络错误，请检查后端服务');
    } finally {
      setLoading(false);
    }
  };

  const handleRegistration = async (event) => {
    event.preventDefault();
    setError('');
    setNotice('');
    if (registration.password.length < 8 || registration.password.length > 72
      || !/[A-Za-z]/.test(registration.password) || !/\d/.test(registration.password)) {
      setError('密码需为 8–72 位，并同时包含字母和数字');
      return;
    }
    if (registration.password !== registration.confirmPassword) {
      setError('两次输入的密码不一致');
      return;
    }
    if (!registration.realName.trim() || !/^1\d{10}$/.test(registration.phone.trim())) {
      setError('请填写真实姓名和正确的手机号码');
      return;
    }
    if (!registration.captchaCode.trim()) {
      setError('请输入验证码');
      return;
    }

    setLoading(true);
    try {
      const payload = { ...registration, username: registration.phone.trim() };
      delete payload.confirmPassword;
      payload.desiredProjectText = registration.desiredProjectName.trim() || undefined;
      payload.applicationReason = registration.applicationReason.trim() || undefined;
      const res = await submitRegistrationApplication(payload);
      if (res.code !== 200 || !res.data) throw new Error(res.message || '注册申请提交失败');
      const token = res.data.statusToken || res.data.queryToken || res.data.statusQueryToken || '';
      setQueryToken(token);
      setApplicationStatus({ ...res.data, status: res.data.status || 'PENDING' });
      setNotice('申请已提交，请妥善保存查询凭证并等待管理员审核。');
      setMode('STATUS');
      setRegistration(createEmptyRegistration());
      setCaptcha(null);
    } catch (err) {
      setError(err.message || '注册申请提交失败');
      loadCaptcha();
    } finally {
      setLoading(false);
    }
  };

  const handleStatusQuery = async (event) => {
    event.preventDefault();
    setError('');
    setNotice('');
    if (!queryToken.trim()) {
      setError('请输入申请状态查询凭证');
      return;
    }
    setLoading(true);
    try {
      const token = queryToken.trim();
      const res = await queryRegistrationApplicationStatus({
        statusToken: token,
        queryToken: token,
        statusQueryToken: token,
      });
      if (res.code !== 200 || !res.data) throw new Error(res.message || '申请状态查询失败');
      setApplicationStatus(res.data);
    } catch (err) {
      setError(err.message || '申请状态查询失败');
    } finally {
      setLoading(false);
    }
  };

  const handleRegistrationCancel = async () => {
    const token = queryToken.trim();
    if (!token || applicationStatus?.status !== 'PENDING') return;
    if (!window.confirm('确认取消这份注册申请吗？取消后需要重新提交申请。')) return;
    setError('');
    setNotice('');
    setLoading(true);
    try {
      const payload = { statusToken: token, queryToken: token, statusQueryToken: token };
      const res = await cancelRegistrationApplication(payload);
      if (res.code !== 200 || !res.data) throw new Error(res.message || '取消申请失败');
      setApplicationStatus(res.data);
      setNotice('注册申请已取消，账号和手机号占用已释放。');
    } catch (err) {
      setError(err.message || '取消申请失败');
    } finally {
      setLoading(false);
    }
  };

  const switchMode = (nextMode) => {
    setMode(nextMode);
    setError('');
    setNotice('');
  };

  return (
    <div data-theme={T.id} className="login-shell">
      <section className="login-brand-panel">
        <div className="login-brand-logo">
          <img src="/brand/zhihui-yingzao-vertical.png" alt="智慧营造" />
        </div>
        <p>项目现场综合管理系统</p>
        <div className="login-brand-points">
          <span>资料协同</span>
          <span>现场巡检</span>
          <span>质量闭环</span>
        </div>
      </section>

      <section className={`login-card login-card-${mode.toLowerCase()}`}>
        <div className="login-card-header">
          <div>
            <h2>{mode === 'REGISTER' ? '申请注册账号' : mode === 'STATUS' ? '查询申请状态' : '欢迎登录'}</h2>
            <p>{mode === 'REGISTER' ? '提交后由系统管理员审核并分配权限' : mode === 'STATUS' ? '使用提交申请后获得的查询凭证' : '请选择安全登录方式'}</p>
          </div>
          {(mode === 'REGISTER' || mode === 'STATUS') && (
            <button className="login-link-button" onClick={() => switchMode('PASSWORD')}>返回登录</button>
          )}
        </div>

        {(mode === 'PASSWORD' || mode === 'QR') && (
          <div className="login-mode-tabs">
            <button className={mode === 'PASSWORD' ? 'active' : ''} onClick={() => switchMode('PASSWORD')}>账号密码</button>
            <button className={mode === 'QR' ? 'active' : ''} onClick={() => switchMode('QR')}>微信扫码</button>
          </div>
        )}

        {mode === 'PASSWORD' && (
          <form onSubmit={handlePasswordLogin} className="login-form">
            <Field label="账号/手机号" required>
              <input autoComplete="username" value={username} onChange={(event) => setUsername(event.target.value)} placeholder="请输入账号或手机号" />
            </Field>
            <Field label="密码" required>
              <input autoComplete="current-password" type="password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="请输入密码" />
            </Field>
            <button className="login-primary-button" type="submit" disabled={loading}>{loading ? '登录中…' : '登录系统'}</button>
            <div className="login-form-links">
              <button type="button" onClick={() => switchMode('REGISTER')}>申请注册账号</button>
              <button type="button" onClick={() => switchMode('STATUS')}>查询申请进度</button>
            </div>
          </form>
        )}

        {mode === 'QR' && (
          <div className="login-qr-panel">
            <div className={`login-qr-box status-${qrStatus.toLowerCase()}`}>
              {qrBusy && <div className="login-placeholder">二维码生成中…</div>}
              {!qrBusy && normalizeQrImage(challenge) && <img src={normalizeQrImage(challenge)} alt="微信扫码登录小程序码" />}
              {!qrBusy && !normalizeQrImage(challenge) && <div className="login-placeholder">暂未取得小程序码</div>}
              {['EXPIRED', 'CANCELLED', 'CONSUMED'].includes(qrStatus) && (
                <div className="login-qr-mask">
                  <span>{QR_STATUS_TEXT[qrStatus]}</span>
                  <button onClick={createChallenge}>刷新二维码</button>
                </div>
              )}
            </div>
            <strong>{QR_STATUS_TEXT[qrStatus] || '等待扫码'}</strong>
            <p>微信扫码后，请在小程序确认登录。二维码 2 分钟内有效。</p>
            <button className="login-secondary-button" onClick={() => switchMode('REGISTER')}>没有账号？申请注册</button>
          </div>
        )}

        {mode === 'REGISTER' && (
          <form onSubmit={handleRegistration} className="login-form registration-form">
            <div className="login-field-grid">
              <Field label="手机号（登录账号）" required><input inputMode="tel" autoComplete="tel" value={registration.phone} onChange={(event) => updateRegistration('phone', event.target.value)} placeholder="审批通过后使用此手机号登录" /></Field>
              <Field label="真实姓名" required><input value={registration.realName} onChange={(event) => updateRegistration('realName', event.target.value)} /></Field>
              <Field label="登录密码" required><input type="password" autoComplete="new-password" value={registration.password} onChange={(event) => updateRegistration('password', event.target.value)} placeholder="至少 8 位" /></Field>
              <Field label="确认密码" required><input type="password" autoComplete="new-password" value={registration.confirmPassword} onChange={(event) => updateRegistration('confirmPassword', event.target.value)} /></Field>
              <Field label="邮箱"><input type="email" value={registration.email} onChange={(event) => updateRegistration('email', event.target.value)} /></Field>
            </div>
            <Field label="期望项目/项目意向"><input value={registration.desiredProjectName} onChange={(event) => updateRegistration('desiredProjectName', event.target.value)} placeholder="选填，例如：智慧工地综合演示项目" /></Field>
            <Field label="申请说明"><textarea rows="3" value={registration.applicationReason} onChange={(event) => updateRegistration('applicationReason', event.target.value)} placeholder="请简要说明所属项目和使用需求" /></Field>
            <Field label="验证码" required>
              <div className="login-captcha-row">
                <input value={registration.captchaCode} onChange={(event) => updateRegistration('captchaCode', event.target.value)} placeholder="请输入验证码" />
                {captchaImage ? <img src={captchaImage} alt="验证码" onClick={loadCaptcha} /> : <button type="button" onClick={loadCaptcha}>获取验证码</button>}
                <button type="button" onClick={loadCaptcha}>换一张</button>
              </div>
            </Field>
            <button className="login-primary-button" type="submit" disabled={loading}>{loading ? '提交中…' : '提交注册申请'}</button>
          </form>
        )}

        {mode === 'STATUS' && (
          <form onSubmit={handleStatusQuery} className="login-form">
            <Field label="查询凭证" required>
              <textarea rows="3" value={queryToken} onChange={(event) => setQueryToken(event.target.value)} placeholder="粘贴注册申请提交后获得的查询凭证" />
            </Field>
            <button className="login-primary-button" type="submit" disabled={loading}>{loading ? '查询中…' : '查询申请状态'}</button>
            {applicationStatus && (
              <div className={`application-result status-${String(applicationStatus.status || '').toLowerCase()}`}>
                <div>
                  <span>当前状态</span>
                  <strong>{APPLICATION_STATUS_TEXT[applicationStatus.status] || applicationStatus.status}</strong>
                </div>
                <p>{applicationStatus.reviewComment || applicationStatus.message || (applicationStatus.status === 'PENDING' ? '管理员审核后才能登录系统。' : '')}</p>
                {applicationStatus.status === 'PENDING' && (
                  <button type="button" className="application-cancel-button" disabled={loading} onClick={handleRegistrationCancel}>
                    取消申请
                  </button>
                )}
              </div>
            )}
          </form>
        )}

        {error && <div className="login-message error">{error}</div>}
        {notice && <div className="login-message notice">{notice}</div>}
      </section>
    </div>
  );
}
