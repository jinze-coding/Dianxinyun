import React, { useState, useEffect } from 'react';
import { ALL_THEMES } from '@/constants/themes';
import { PROJECTS, PROJECT_INFO } from '@/constants/mockData';
import { NAV_ITEMS } from '@/constants/dicts';

// 工具函数
const pad = (n) => String(n).padStart(2, '0');

const formatTime = (d) => {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
};

// 顶部导航组件
export function TopNav({
  currentPage,
  onPageChange,
  currentProject,
  onProjectChange,
  theme,
  themeId,
  onThemeChange,
  compactMode,
  onCompactChange,
}) {
  const [showProjects, setShowProjects] = useState(false);
  const [showThemePicker, setShowThemePicker] = useState(false);
  const [time, setTime] = useState(new Date());

  useEffect(() => {
    const timer = setInterval(() => setTime(new Date()), 1000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    const close = () => {
      setShowProjects(false);
      setShowThemePicker(false);
    };
    if (showProjects || showThemePicker) {
      document.addEventListener('click', close);
      return () => document.removeEventListener('click', close);
    }
  }, [showProjects, showThemePicker]);

  const T = theme;
  const proj = PROJECTS.find(p => p.id === currentProject) || PROJECTS[0];

  return (
    <header
      style={{
        height: T.navHeight,
        background: T.navBg,
        borderBottom: `1px solid ${T.borderColor}`,
        display: 'flex',
        alignItems: 'center',
        padding: '0 20px',
        position: 'relative',
        zIndex: 100,
        flexShrink: 0,
      }}
    >
      {/* Logo */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, minWidth: 260 }}>
        <div
          style={{
            width: 32,
            height: 32,
            borderRadius: 6,
            background: `linear-gradient(135deg, ${T.accent}, ${T.accent2})`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 16,
            fontWeight: 700,
            color: '#fff',
            flexShrink: 0,
          }}
        >
          云
        </div>
        <div>
          <div style={{ fontSize: 14, fontWeight: 700, color: T.textPrimary, letterSpacing: 1, lineHeight: 1.2 }}>
            电信云平台
          </div>
          <div style={{ fontSize: 10, color: T.textMuted, letterSpacing: 0.5 }}>项目现场综合管理系统</div>
        </div>
      </div>

      {/* 项目切换 */}
      <div style={{ position: 'relative', marginLeft: 16 }}>
        <button
          onClick={e => {
            e.stopPropagation();
            setShowProjects(!showProjects);
            setShowThemePicker(false);
          }}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            background: T.cardBg,
            border: `1px solid ${T.borderColor}`,
            borderRadius: 6,
            padding: '5px 12px',
            cursor: 'pointer',
            color: T.textPrimary,
            fontSize: 13,
            minWidth: 200,
          }}
        >
          <span
            style={{
              width: 6,
              height: 6,
              borderRadius: '50%',
              flexShrink: 0,
              background: proj.status === 'normal' ? T.success : T.warning,
            }}
          ></span>
          <span style={{ flex: 1, textAlign: 'left' }}>{proj.name}</span>
          <span style={{ color: T.textMuted, fontSize: 10 }}>▼</span>
        </button>
        {showProjects && (
          <div
            style={{
              position: 'absolute',
              top: '100%',
              left: 0,
              marginTop: 4,
              background: T.dropdownBg,
              border: `1px solid ${T.borderColor}`,
              borderRadius: 8,
              overflow: 'hidden',
              minWidth: 220,
              zIndex: 200,
              boxShadow: '0 8px 24px rgba(0,0,0,0.4)',
            }}
            onClick={e => e.stopPropagation()}
          >
            {PROJECTS.map(p => (
              <div
                key={p.id}
                onClick={() => {
                  onProjectChange(p.id);
                  setShowProjects(false);
                }}
                style={{
                  padding: '10px 14px',
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                  fontSize: 13,
                  color: T.textPrimary,
                  background: p.id === currentProject ? T.activeItemBg : 'transparent',
                  transition: 'background 0.15s',
                }}
                onMouseEnter={e => (e.currentTarget.style.background = T.hoverBg)}
                onMouseLeave={e =>
                  (e.currentTarget.style.background = p.id === currentProject ? T.activeItemBg : 'transparent')
                }
              >
                <span
                  style={{
                    width: 6,
                    height: 6,
                    borderRadius: '50%',
                    background: p.status === 'normal' ? T.success : T.warning,
                    flexShrink: 0,
                  }}
                ></span>
                <span style={{ flex: 1 }}>{p.name}</span>
                <span
                  style={{
                    fontSize: 10,
                    color: T.textMuted,
                    background: T.tagBg,
                    padding: '1px 6px',
                    borderRadius: 3,
                  }}
                >
                  {p.phase}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 主导航 */}
      <nav
        style={{
          display: 'flex',
          gap: 2,
          marginLeft: 32,
          flex: 1,
          justifyContent: 'center',
        }}
      >
        {NAV_ITEMS.map(item => {
          const active = currentPage === item.id;
          return (
            <button
              key={item.id}
              onClick={() => onPageChange(item.id)}
              style={{
                padding: '6px 24px',
                border: 'none',
                cursor: 'pointer',
                borderRadius: 6,
                fontSize: 14,
                fontWeight: active ? 600 : 400,
                color: active ? '#fff' : T.textSecondary,
                background: active ? T.accent : 'transparent',
                transition: 'all 0.2s',
                letterSpacing: 0.5,
              }}
              onMouseEnter={e => {
                if (!active) e.currentTarget.style.background = T.hoverBg;
              }}
              onMouseLeave={e => {
                if (!active) e.currentTarget.style.background = 'transparent';
              }}
            >
              {item.label}
            </button>
          );
        })}
      </nav>

      {/* 右侧 */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 14,
          minWidth: 340,
          justifyContent: 'flex-end',
        }}
      >
        <div
          style={{
            fontSize: 12,
            color: T.textMuted,
            fontVariantNumeric: 'tabular-nums',
            letterSpacing: 0.5,
          }}
        >
          {formatTime(time)}
        </div>

        {/* 主题切换 */}
        <div style={{ position: 'relative' }}>
          <button
            onClick={e => {
              e.stopPropagation();
              setShowThemePicker(!showThemePicker);
              setShowProjects(false);
            }}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              background: T.cardBg,
              border: `1px solid ${T.borderColor}`,
              borderRadius: 6,
              padding: '5px 10px',
              cursor: 'pointer',
              color: T.textSecondary,
              fontSize: 12,
            }}
            title="主题配色"
          >
            <span
              style={{
                width: 12,
                height: 12,
                borderRadius: '50%',
                background: `linear-gradient(135deg, ${T.accent}, ${T.accent2})`,
                border: `1px solid ${T.borderColor}`,
                flexShrink: 0,
              }}
            ></span>
            <span>主题</span>
            <span style={{ color: T.textMuted, fontSize: 10 }}>▼</span>
          </button>
          {showThemePicker && (
            <div
              style={{
                position: 'absolute',
                top: '100%',
                right: 0,
                marginTop: 6,
                background: T.dropdownBg,
                border: `1px solid ${T.borderColor}`,
                borderRadius: 10,
                width: 320,
                zIndex: 200,
                padding: 14,
                boxShadow: '0 10px 30px rgba(0,0,0,0.5)',
              }}
              onClick={e => e.stopPropagation()}
            >
              <div
                style={{
                  fontSize: 11,
                  color: T.textMuted,
                  marginBottom: 10,
                  letterSpacing: 0.5,
                }}
              >
                配色方案 &nbsp;·&nbsp; 白色
              </div>
              <div
                style={{
                  height: 4,
                  borderRadius: 2,
                  marginBottom: 12,
                  background: '#f0f4f9',
                  border: `1px solid ${T.borderColor}`,
                }}
              ></div>
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: '1fr',
                  gap: 8,
                  marginBottom: 12,
                }}
              >
                {ALL_THEMES.map(th => {
                  const active = themeId === th.id;
                  return (
                    <button
                      key={th.id}
                      onClick={() => onThemeChange(th.id)}
                      style={{
                        padding: '10px 6px 8px',
                        borderRadius: 8,
                        cursor: 'pointer',
                        border: `2px solid ${active ? th.accent : 'transparent'}`,
                        background: th.pageBg,
                        outline: active ? `1px solid ${th.accent}` : 'none',
                        outlineOffset: 1,
                        transition: 'all 0.15s',
                        position: 'relative',
                      }}
                    >
                      <div style={{ marginBottom: 6 }}>
                        <div
                          style={{
                            height: 4,
                            borderRadius: 1,
                            background: th.navBg,
                            border: `1px solid ${th.borderColor}`,
                            marginBottom: 3,
                          }}
                        ></div>
                        <div style={{ display: 'flex', gap: 2 }}>
                          <div
                            style={{
                              width: '35%',
                              height: 22,
                              background: th.cardBg,
                              borderRadius: 2,
                              border: `1px solid ${th.borderColor}`,
                            }}
                          ></div>
                          <div
                            style={{
                              flex: 1,
                              display: 'flex',
                              flexDirection: 'column',
                              gap: 2,
                            }}
                          >
                            <div
                              style={{
                                height: 10,
                                background: th.cardBg,
                                borderRadius: 2,
                                border: `1px solid ${th.borderColor}`,
                              }}
                            ></div>
                            <div
                              style={{
                                height: 10,
                                background: th.cardBg,
                                borderRadius: 2,
                                border: `1px solid ${th.borderColor}`,
                              }}
                            ></div>
                          </div>
                        </div>
                        <div
                          style={{
                            width: 10,
                            height: 3,
                            background: th.accent,
                            borderRadius: 1,
                            margin: '3px auto 0',
                          }}
                        ></div>
                      </div>
                      <div
                        style={{
                          fontSize: 10,
                          color: th.textPrimary,
                          fontWeight: active ? 700 : 400,
                          lineHeight: 1.3,
                        }}
                      >
                        {th.name}
                      </div>
                      <div style={{ fontSize: 8, color: th.textMuted, marginTop: 1 }}>{th.brightness}</div>
                      {active && (
                        <div
                          style={{
                            position: 'absolute',
                            top: 4,
                            right: 4,
                            width: 6,
                            height: 6,
                            borderRadius: '50%',
                            background: th.accent,
                          }}
                        ></div>
                      )}
                    </button>
                  );
                })}
              </div>
              <div style={{ height: 1, background: T.borderColor, marginBottom: 10 }}></div>
              <div style={{ fontSize: 10, color: T.textMuted, marginBottom: 6, letterSpacing: 0.5 }}>信息密度</div>
              <div style={{ display: 'flex', gap: 6 }}>
                {[{ label: '舒适型', v: false }, { label: '紧凑型', v: true }].map(opt => (
                  <button
                    key={opt.label}
                    onClick={() => onCompactChange(opt.v)}
                    style={{
                      flex: 1,
                      padding: '6px',
                      borderRadius: 5,
                      cursor: 'pointer',
                      fontSize: 11,
                      border: `1px solid ${compactMode === opt.v ? T.accent : T.borderColor}`,
                      background: compactMode === opt.v ? T.activeItemBg : 'transparent',
                      color: compactMode === opt.v ? T.accent : T.textMuted,
                    }}
                  >
                    {opt.label}
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12, color: T.textSecondary }}>
          <div
            style={{
              width: 28,
              height: 28,
              borderRadius: '50%',
              background: T.accent,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 11,
              color: '#fff',
              fontWeight: 600,
            }}
          >
            管
          </div>
          <span>平台管理员</span>
        </div>
      </div>
    </header>
  );
}

export default TopNav;
