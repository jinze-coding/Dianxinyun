import React, { useState } from 'react';
import { login } from '../../services/auth';

// 深色科技风主题
const THEME = {
  pageBg: '#060f1e',
  cardBg: '#0c1a30',
  accent: '#1677ff',
  accent2: '#36a3f7',
  borderColor: '#1a2f50',
  textPrimary: '#e2edff',
  textSecondary: '#9bbde0',
  textMuted: '#4d6d9a',
  success: '#22c55e',
  danger: '#ef4444',
};

export default function LoginPage({ onLogin }) {
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
    <div style={{
      width: '100vw',
      height: '100vh',
      background: THEME.pageBg,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      fontFamily: '"PingFang SC", "Microsoft YaHei", sans-serif',
      position: 'relative',
      overflow: 'hidden',
    }}>
      {/* 背景装饰 */}
      <div style={{
        position: 'absolute',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        background: 'radial-gradient(ellipse at 20% 20%, rgba(22, 119, 255, 0.15) 0%, transparent 50%)',
        pointerEvents: 'none',
      }} />
      <div style={{
        position: 'absolute',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        background: 'radial-gradient(ellipse at 80% 80%, rgba(54, 163, 247, 0.1) 0%, transparent 50%)',
        pointerEvents: 'none',
      }} />

      {/* 登录卡片 */}
      <div style={{
        position: 'relative',
        width: '420px',
        background: THEME.cardBg,
        borderRadius: '16px',
        border: `1px solid ${THEME.borderColor}`,
        padding: '48px 40px',
        boxShadow: '0 25px 50px rgba(0, 0, 0, 0.5)',
      }}>
        {/* Logo区域 */}
        <div style={{ textAlign: 'center', marginBottom: '40px' }}>
          <div style={{
            width: '72px',
            height: '72px',
            borderRadius: '16px',
            background: `linear-gradient(135deg, ${THEME.accent}, ${THEME.accent2})`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            margin: '0 auto 20px',
            fontSize: '32px',
            fontWeight: 'bold',
            color: '#fff',
            boxShadow: `0 8px 24px rgba(22, 119, 255, 0.4)`,
          }}>
            云
          </div>
          <h1 style={{
            fontSize: '28px',
            fontWeight: 'bold',
            color: THEME.textPrimary,
            margin: '0 0 8px',
            letterSpacing: '2px',
          }}>
            电信云平台
          </h1>
          <p style={{
            fontSize: '14px',
            color: THEME.textMuted,
            margin: 0,
            letterSpacing: '1px',
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
              color: THEME.textSecondary,
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
                background: THEME.pageBg,
                border: `1px solid ${THEME.borderColor}`,
                borderRadius: '8px',
                fontSize: '15px',
                color: THEME.textPrimary,
                outline: 'none',
                boxSizing: 'border-box',
                transition: 'border-color 0.2s, box-shadow 0.2s',
              }}
              onFocus={(e) => {
                e.target.style.borderColor = THEME.accent;
                e.target.style.boxShadow = `0 0 0 3px rgba(22, 119, 255, 0.2)`;
              }}
              onBlur={(e) => {
                e.target.style.borderColor = THEME.borderColor;
                e.target.style.boxShadow = 'none';
              }}
            />
          </div>

          <div style={{ marginBottom: '32px' }}>
            <label style={{
              display: 'block',
              fontSize: '14px',
              color: THEME.textSecondary,
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
                background: THEME.pageBg,
                border: `1px solid ${THEME.borderColor}`,
                borderRadius: '8px',
                fontSize: '15px',
                color: THEME.textPrimary,
                outline: 'none',
                boxSizing: 'border-box',
                transition: 'border-color 0.2s, box-shadow 0.2s',
              }}
              onFocus={(e) => {
                e.target.style.borderColor = THEME.accent;
                e.target.style.boxShadow = `0 0 0 3px rgba(22, 119, 255, 0.2)`;
              }}
              onBlur={(e) => {
                e.target.style.borderColor = THEME.borderColor;
                e.target.style.boxShadow = 'none';
              }}
            />
          </div>

          {error && (
            <div style={{
              padding: '12px 16px',
              background: 'rgba(239, 68, 68, 0.1)',
              border: `1px solid rgba(239, 68, 68, 0.3)`,
              borderRadius: '8px',
              color: THEME.danger,
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
                : `linear-gradient(135deg, ${THEME.accent}, ${THEME.accent2})`,
              color: '#fff',
              border: 'none',
              borderRadius: '8px',
              fontSize: '16px',
              fontWeight: '600',
              cursor: loading ? 'not-allowed' : 'pointer',
              transition: 'transform 0.2s, box-shadow 0.2s',
              boxShadow: loading ? 'none' : `0 8px 24px rgba(22, 119, 255, 0.3)`,
            }}
            onMouseOver={(e) => {
              if (!loading) {
                e.target.style.transform = 'translateY(-2px)';
                e.target.style.boxShadow = '0 12px 32px rgba(22, 119, 255, 0.4)';
              }
            }}
            onMouseOut={(e) => {
              e.target.style.transform = 'translateY(0)';
              e.target.style.boxShadow = loading ? 'none' : '0 8px 24px rgba(22, 119, 255, 0.3)';
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
          background: THEME.pageBg,
          borderRadius: '8px',
          border: `1px solid ${THEME.borderColor}`,
        }}>
          <div style={{
            fontSize: '13px',
            color: THEME.textMuted,
            marginBottom: '8px',
            fontWeight: 500,
          }}>
            测试账号
          </div>
          <div style={{
            display: 'flex',
            justifyContent: 'space-between',
            fontSize: '13px',
            color: THEME.textSecondary,
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
