import React, { useState } from 'react';
import { DEFAULT_THEME_ID, getThemeById } from '../../constants/themes';
import { login } from '../../services/auth';

export default function LoginPage({ onLogin, theme }) {
  const T = theme || getThemeById(DEFAULT_THEME_ID);
  const [username, setUsername] = useState('admin');
  const [password, setPassword] = useState('admin123');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const res = await login(username, password);
      if (res.code === 200) {
        onLogin?.();
      } else {
        setError(res.message || '登录失败');
      }
    } catch (err) {
      setError('网络错误，请检查后端服务是否运行');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div data-theme={T.id} style={{
      width: '100vw',
      height: '100vh',
      padding: 16,
      boxSizing: 'border-box',
      background: T.pageBg,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      fontFamily: '"PingFang SC", "Microsoft YaHei", sans-serif',
      position: 'relative',
      overflow: 'hidden',
    }}>
      {/* 登录卡片 */}
      <div style={{
        position: 'relative',
        width: 'min(420px, calc(100vw - 32px))',
        boxSizing: 'border-box',
        background: T.cardBg,
        borderRadius: T.radius,
        border: `1px solid ${T.borderColor}`,
        padding: '40px',
        boxShadow: '0 18px 40px rgba(15, 26, 46, 0.10)',
      }}>
        {/* Logo区域 */}
        <div style={{ textAlign: 'center', marginBottom: '40px' }}>
          <div style={{
            width: '72px',
            height: '72px',
            borderRadius: T.radius,
            background: T.accent,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            margin: '0 auto 20px',
            fontSize: '32px',
            fontWeight: 'bold',
            color: '#fff',
            boxShadow: '0 8px 20px rgba(22, 119, 255, 0.22)',
          }}>
            云
          </div>
          <h1 style={{
            fontSize: '28px',
            fontWeight: 'bold',
            color: T.textPrimary,
            margin: '0 0 8px',
            letterSpacing: 0,
          }}>
            电信云平台
          </h1>
          <p style={{
            fontSize: '14px',
            color: T.textMuted,
            margin: 0,
            letterSpacing: 0,
          }}>
            项目现场综合管理系统
          </p>
        </div>

        {/* 登录表单 */}
        <form onSubmit={handleSubmit}>
          <div style={{ marginBottom: '24px' }}>
            <label style={{
              display: 'block',
              fontSize: '14px',
              color: T.textSecondary,
              marginBottom: '10px',
              fontWeight: 500,
            }}>
              用户名
            </label>
            <input
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              style={{
                width: '100%',
                padding: '14px 16px',
                background: T.surface2,
                border: `1px solid ${T.borderColor}`,
                borderRadius: '8px',
                fontSize: '15px',
                color: T.textPrimary,
                outline: 'none',
                boxSizing: 'border-box',
                transition: 'border-color 0.2s, box-shadow 0.2s',
              }}
              onFocus={(e) => {
                e.target.style.borderColor = T.accent;
                e.target.style.boxShadow = `0 0 0 3px ${T.activeItemBg}`;
              }}
              onBlur={(e) => {
                e.target.style.borderColor = T.borderColor;
                e.target.style.boxShadow = 'none';
              }}
            />
          </div>

          <div style={{ marginBottom: '32px' }}>
            <label style={{
              display: 'block',
              fontSize: '14px',
              color: T.textSecondary,
              marginBottom: '10px',
              fontWeight: 500,
            }}>
              密码
            </label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              style={{
                width: '100%',
                padding: '14px 16px',
                background: T.surface2,
                border: `1px solid ${T.borderColor}`,
                borderRadius: '8px',
                fontSize: '15px',
                color: T.textPrimary,
                outline: 'none',
                boxSizing: 'border-box',
                transition: 'border-color 0.2s, box-shadow 0.2s',
              }}
              onFocus={(e) => {
                e.target.style.borderColor = T.accent;
                e.target.style.boxShadow = `0 0 0 3px ${T.activeItemBg}`;
              }}
              onBlur={(e) => {
                e.target.style.borderColor = T.borderColor;
                e.target.style.boxShadow = 'none';
              }}
            />
          </div>

          {error && (
            <div style={{
              padding: '12px 16px',
              background: 'rgba(220, 38, 38, 0.06)',
              border: '1px solid rgba(220, 38, 38, 0.22)',
              borderRadius: '8px',
              color: T.danger,
              fontSize: '14px',
              marginBottom: '20px',
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
            }}>
              <span style={{ fontSize: '18px' }}>⚠</span>
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            style={{
              width: '100%',
              padding: '16px',
              background: loading
                ? 'rgba(22, 119, 255, 0.5)'
                : T.accent,
              color: '#fff',
              border: 'none',
              borderRadius: '8px',
              fontSize: '16px',
              fontWeight: '600',
              cursor: loading ? 'not-allowed' : 'pointer',
              transition: 'transform 0.2s, box-shadow 0.2s',
              boxShadow: loading ? 'none' : '0 8px 20px rgba(22, 119, 255, 0.22)',
            }}
            onMouseOver={(e) => {
              if (!loading) {
                e.target.style.transform = 'translateY(-2px)';
                e.target.style.boxShadow = '0 10px 24px rgba(22, 119, 255, 0.28)';
              }
            }}
            onMouseOut={(e) => {
              e.target.style.transform = 'translateY(0)';
              e.target.style.boxShadow = loading ? 'none' : '0 8px 20px rgba(22, 119, 255, 0.22)';
            }}
          >
            {loading ? (
              <span style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '8px' }}>
                <span style={{
                  display: 'inline-block',
                  width: '16px',
                  height: '16px',
                  border: '2px solid rgba(255,255,255,0.3)',
                  borderTopColor: '#fff',
                  borderRadius: '50%',
                  animation: 'spin 1s linear infinite',
                }} />
                登录中...
              </span>
            ) : '登 录'}
          </button>
        </form>

        {/* 底部提示 */}
        <div style={{
          marginTop: '32px',
          padding: '16px',
          background: T.surface2,
          borderRadius: '8px',
          border: `1px solid ${T.borderColor}`,
        }}>
          <div style={{
            fontSize: '13px',
            color: T.textMuted,
            marginBottom: '8px',
            fontWeight: 500,
          }}>
            测试账号
          </div>
          <div style={{
            display: 'flex',
            justifyContent: 'space-between',
            fontSize: '13px',
            color: T.textSecondary,
          }}>
            <span>用户名：admin</span>
            <span>密码：admin123</span>
          </div>
        </div>
      </div>

      <style>{`
        @keyframes spin {
          from { transform: rotate(0deg); }
          to { transform: rotate(360deg); }
        }
      `}</style>
    </div>
  );
}
