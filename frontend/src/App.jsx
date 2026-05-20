import React, { useState, useEffect, useCallback } from 'react';
import { ALL_THEMES, DEFAULT_THEME_ID, getThemeById } from './constants/themes';
import { PROJECTS, PROJECT_INFO, DATA_BY_PROJECT } from './constants/mockData';
import { NAV_ITEMS, PAGE_IDS } from './constants/dicts';
import { isLoggedIn, getUserInfo } from './services/auth';
import { getProjectList, addProject, updateProject, deleteProject } from './services/project';
import { getPersonnelList, addPersonnel, updatePersonnel, deletePersonnel } from './services/personnel';
import { getTrainingList, createTraining, updateTraining, markTrainingComplete, deleteTraining } from './services/safety';
import { getFileList, uploadFile, updateFileStatus, deleteFile, getFileDetail } from './services/file';
import { getCameraList, getDeviceList, getTowerCraneList, createCamera, updateCamera, deleteCamera, createDevice, updateDevice, deleteDevice } from './services/monitor';
import { CameraPage } from './pages/Camera';
import LoginPage from './pages/Login';
import MapDashboard from './components/MapDashboard';
import StatusBadge from './components/StatusBadge';
import SectionCard from './components/SectionCard';
import PersonFormModal from './components/PersonFormModal';

// ============================================
// 工具函数
// ============================================
const pad = (n) => String(n).padStart(2, '0');

const formatTime = (d) => {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
};

const parseDateOnly = (value) => {
  if (!value) return null;
  const [datePart] = String(value).split('T');
  const parts = datePart.split('-').map(Number);
  if (parts.length !== 3 || parts.some(Number.isNaN)) return null;
  return new Date(parts[0], parts[1] - 1, parts[2]);
};

const calculateProjectProgress = (startDate, endDate, now = new Date()) => {
  const start = parseDateOnly(startDate);
  const end = parseDateOnly(endDate);
  if (!start || !end || end <= start) return 0;
  if (now <= start) return 0;
  if (now >= end) return 100;
  return Math.round(((now - start) / (end - start)) * 100);
};

// 项目ID映射 (p1 -> 1)
const PROJECT_ID_MAP = { p1: 1, p2: 2, p3: 3 };

// ============================================
// 通用小组件
// ============================================
function StatCard({ label, value, sub, color, theme: T }) {
  return (
    <div style={{
      background: T.cardBg,
      border: `1px solid ${T.borderColor}`,
      borderRadius: T.radius,
      padding: '14px 16px',
      flex: 1,
    }}>
      <div style={{ fontSize: 11, color: T.textMuted, marginBottom: 4 }}>{label}</div>
      <div style={{ fontSize: 26, fontWeight: 700, color: color || T.textPrimary, lineHeight: 1 }}>{value}</div>
      {sub && <div style={{ fontSize: 11, color: T.textMuted, marginTop: 4 }}>{sub}</div>}
    </div>
  );
}

function VideoCell({ cam, theme: T, onFullscreen, fullscreen }) {
  const baseStyle = fullscreen ? { width: '100%', height: '100%' } : { aspectRatio: '16/9' };
  return (
    <div style={{
      background: '#060e1c',
      border: `1px solid ${T.borderColor}`,
      borderRadius: 6,
      overflow: 'hidden',
      position: 'relative',
      ...baseStyle,
    }}>
      {cam.online ? (
        <>
          <div style={{
            width: '100%',
            height: '100%',
            background: 'linear-gradient(135deg, #060e1c 0%, #0a1a2e 40%, #071520 100%)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}>
            <div style={{ textAlign: 'center', opacity: 0.3 }}>
              <svg width={fullscreen ? 64 : 32} height={fullscreen ? 64 : 32} viewBox="0 0 24 24" fill="none" stroke={T.accent} strokeWidth="1.5">
                <path d="M15 10l4.553-2.069A1 1 0 0121 8.82v6.36a1 1 0 01-1.447.889L15 14M3 8a2 2 0 012-2h8a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2V8z" />
              </svg>
              <div style={{ fontSize: fullscreen ? 14 : 10, color: T.textMuted, marginTop: 4 }}>实时视频流</div>
            </div>
          </div>
          <div style={{ position: 'absolute', top: 6, left: 6, display: 'flex', alignItems: 'center', gap: 4, background: 'rgba(0,0,0,0.6)', padding: '2px 6px', borderRadius: 3 }}>
            <span style={{ width: 5, height: 5, borderRadius: '50%', background: T.success, display: 'block' }}></span>
            <span style={{ fontSize: 10, color: '#fff' }}>LIVE</span>
          </div>
          <div style={{ position: 'absolute', bottom: 0, left: 0, right: 0, background: 'linear-gradient(transparent, rgba(0,0,0,0.7))', padding: '8px 6px 4px', fontSize: fullscreen ? 13 : 10, color: 'rgba(255,255,255,0.7)', display: 'flex', justifyContent: 'space-between' }}>
            <span>{cam.name}</span>
            <span style={{ color: T.textMuted }}>{cam.area}</span>
          </div>
          {!fullscreen && (
            <button onClick={onFullscreen} title="全屏" style={{ position: 'absolute', top: 6, right: 6, background: 'rgba(0,0,0,0.5)', border: 'none', borderRadius: 3, padding: '2px 5px', cursor: 'pointer', color: '#ccc', fontSize: 10 }}>⛶</button>
          )}
        </>
      ) : (
        <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 6, background: '#08111e' }}>
          <svg width={fullscreen ? 48 : 24} height={fullscreen ? 48 : 24} viewBox="0 0 24 24" fill="none" stroke="#444" strokeWidth="1.5">
            <line x1="1" y1="1" x2="23" y2="23"/><path d="M16.5 16.5A7 7 0 0 1 5.5 5.5M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h4"/>
          </svg>
          <span style={{ fontSize: fullscreen ? 14 : 10, color: '#444' }}>离线</span>
        </div>
      )}
    </div>
  );
}

// ============================================
// 顶部导航
// ============================================
function TopNav({ currentPage, onPageChange, currentProject, onProjectChange, projectList, onRefreshProjects, theme, themeId, onThemeChange, compactMode, onCompactChange, onLogout }) {
  const [showProjects, setShowProjects] = useState(false);
  const [showThemePicker, setShowThemePicker] = useState(false);
  const [showProjectMgmt, setShowProjectMgmt] = useState(false);
  const [showAddModal, setShowAddModal] = useState(false);
  const [addForm, setAddForm] = useState({ projectName: '', shortName: '', projectStatus: 'normal', startDate: '', endDate: '' });
  const [projectSearch, setProjectSearch] = useState('');
  const [projectMgmtSearch, setProjectMgmtSearch] = useState('');
  const [time, setTime] = useState(new Date());

  // 搜索过滤
  const filteredProjects = projectList.filter(p =>
    !projectSearch || p.projectName?.includes(projectSearch) || p.shortName?.includes(projectSearch)
  );
  const filteredMgmtProjects = projectList.filter(p =>
    !projectMgmtSearch || p.projectName?.includes(projectMgmtSearch) || p.shortName?.includes(projectMgmtSearch)
  );

  useEffect(() => {
    const timer = setInterval(() => setTime(new Date()), 1000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    const close = () => { setShowProjects(false); setShowThemePicker(false); setShowProjectMgmt(false); setProjectSearch(''); setProjectMgmtSearch(''); };
    if (showProjects || showThemePicker || showProjectMgmt) {
      document.addEventListener('click', close);
      return () => document.removeEventListener('click', close);
    }
  }, [showProjects, showThemePicker, showProjectMgmt]);

  const handleAddProject = async () => {
    if (!addForm.projectName.trim()) { alert('请填写项目名称'); return; }
    try {
      const res = await addProject(addForm);
      if (res.code === 200) {
        setShowAddModal(false);
        setAddForm({ projectName: '', shortName: '', projectStatus: 'normal', startDate: '', endDate: '' });
        onRefreshProjects();
      } else { alert(res.message || '添加失败'); }
    } catch (e) { alert('添加失败，请重试'); }
  };

  const handleDeleteProject = async (id) => {
    if (!window.confirm('确认删除此项目？')) return;
    try {
      const res = await deleteProject(id);
      if (res.code === 200) {
        onRefreshProjects();
        // 如果删除的是当前选中的项目，切换到第一个
        if (currentProject === id && projectList.length > 1) {
          const remaining = projectList.find(p => p.id !== id);
          if (remaining) onProjectChange(remaining.id);
        }
      } else { alert(res.message || '删除失败'); }
    } catch (e) { alert('删除失败，请重试'); }
  };

  const T = theme;
  const proj = projectList.find(p => p.id === currentProject) || projectList[0] || {};

  return (
    <header style={{
      height: T.navHeight,
      background: T.navBg,
      borderBottom: `1px solid ${T.borderColor}`,
      display: 'flex',
      alignItems: 'center',
      padding: '0 20px',
      position: 'relative',
      zIndex: 100,
      flexShrink: 0,
    }}>
      {/* Logo */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, minWidth: 260 }}>
        <div style={{
          width: 32, height: 32, borderRadius: 6,
          background: `linear-gradient(135deg, ${T.accent}, ${T.accent2})`,
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: 16, fontWeight: 700, color: '#fff', flexShrink: 0,
        }}>云</div>
        <div>
          <div style={{ fontSize: 14, fontWeight: 700, color: T.textPrimary, letterSpacing: 1, lineHeight: 1.2 }}>电信云平台</div>
          <div style={{ fontSize: 10, color: T.textMuted, letterSpacing: 0.5 }}>项目现场综合管理系统</div>
        </div>
      </div>

      {/* 项目管理 */}
      <div style={{ position: 'relative', marginLeft: 12 }}>
        <button
          onClick={e => { e.stopPropagation(); setShowProjectMgmt(!showProjectMgmt); setShowProjects(false); }}
          style={{
            display: 'flex', alignItems: 'center', gap: 6,
            background: T.activeItemBg, border: `1px solid ${T.accent}`,
            borderRadius: 6, padding: '5px 12px', cursor: 'pointer', color: T.accent,
            fontSize: 12, fontWeight: 500,
          }}
        >
          <span>⚙</span>
          <span>项目管理</span>
        </button>
        {showProjectMgmt && (
          <div style={{
            position: 'absolute', top: '100%', left: 0, marginTop: 6,
            background: T.dropdownBg, border: `1px solid ${T.borderColor}`,
            borderRadius: 8, overflow: 'hidden', minWidth: 300, zIndex: 200,
            boxShadow: '0 8px 24px rgba(0,0,0,0.4)',
          }} onClick={e => e.stopPropagation()}>
            <div style={{ padding: '8px 10px', borderBottom: `1px solid ${T.borderColor}`, display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
              <span style={{ fontSize: 12, fontWeight: 600, color: T.textPrimary }}>项目列表</span>
              <button onClick={() => { setShowAddModal(true); }} style={{
                fontSize: 11, color: T.accent, background: 'none', border: 'none', cursor: 'pointer', fontWeight: 500,
              }}>+ 新增</button>
            </div>
            <div style={{ padding: '6px 8px', borderBottom: `1px solid ${T.borderColor}` }}>
              <input
                placeholder="搜索项目名称..."
                value={projectMgmtSearch}
                onChange={e => setProjectMgmtSearch(e.target.value)}
                style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`,
                  borderRadius: 4, padding: '5px 8px', fontSize: 12, color: T.textPrimary,
                  outline: 'none', boxSizing: 'border-box',
                }}
              />
            </div>
            <div style={{ maxHeight: 280, overflow: 'auto' }}>
              {filteredMgmtProjects.length === 0 ? (
                <div style={{ padding: '20px 12px', textAlign: 'center', fontSize: 12, color: T.textMuted }}>无匹配项目</div>
              ) : filteredMgmtProjects.map(p => (
                <div key={p.id} style={{
                  padding: '8px 12px', display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                  fontSize: 12, color: T.textPrimary, borderBottom: `1px solid ${T.borderColor}`,
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <span style={{
                      width: 6, height: 6, borderRadius: '50%', flexShrink: 0,
                      background: p.projectStatus === 'normal' ? T.success : p.projectStatus === 'warning' ? T.warning : T.danger,
                    }}></span>
                    <span>{p.projectName}</span>
                  </div>
                  <button onClick={() => handleDeleteProject(p.id)} style={{
                    fontSize: 10, color: T.danger, background: 'none', border: 'none', cursor: 'pointer',
                  }}>删除</button>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* 项目切换 */}
      <div style={{ position: 'relative', marginLeft: 16 }}>
        <button
          onClick={e => { e.stopPropagation(); setShowProjects(!showProjects); setShowThemePicker(false); }}
          style={{
            display: 'flex', alignItems: 'center', gap: 8,
            background: T.cardBg, border: `1px solid ${T.borderColor}`,
            borderRadius: 6, padding: '5px 12px', cursor: 'pointer', color: T.textPrimary,
            fontSize: 13, minWidth: 200,
          }}
        >
          <span style={{ width: 6, height: 6, borderRadius: '50%', flexShrink: 0, background: proj.projectStatus === 'normal' ? T.success : T.warning }}></span>
          <span style={{ flex: 1, textAlign: 'left' }}>{proj.projectName || '请选择项目'}</span>
          <span style={{ color: T.textMuted, fontSize: 10 }}>▼</span>
        </button>
        {showProjects && (
          <div style={{
            position: 'absolute', top: '100%', left: 0, marginTop: 4,
            background: T.dropdownBg, border: `1px solid ${T.borderColor}`,
            borderRadius: 8, overflow: 'hidden', minWidth: 240, zIndex: 200,
            boxShadow: '0 8px 24px rgba(0,0,0,0.4)',
          }} onClick={e => e.stopPropagation()}>
            <div style={{ padding: '8px 10px', borderBottom: `1px solid ${T.borderColor}` }}>
              <input
                placeholder="搜索项目名称..."
                value={projectSearch}
                onChange={e => setProjectSearch(e.target.value)}
                autoFocus
                style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`,
                  borderRadius: 4, padding: '6px 8px', fontSize: 12, color: T.textPrimary,
                  outline: 'none', boxSizing: 'border-box',
                }}
              />
            </div>
            <div style={{ maxHeight: 240, overflow: 'auto' }}>
              {filteredProjects.length === 0 ? (
                <div style={{ padding: '20px 14px', textAlign: 'center', fontSize: 12, color: T.textMuted }}>无匹配项目</div>
              ) : filteredProjects.map(p => (
                <div
                  key={p.id}
                  onClick={() => { onProjectChange(p.id); setShowProjects(false); setProjectSearch(''); }}
                  style={{
                    padding: '10px 14px', cursor: 'pointer', display: 'flex',
                    alignItems: 'center', gap: 8, fontSize: 13, color: T.textPrimary,
                    background: p.id === currentProject ? T.activeItemBg : 'transparent',
                    transition: 'background 0.15s',
                  }}
                  onMouseEnter={e => e.currentTarget.style.background = T.hoverBg}
                  onMouseLeave={e => e.currentTarget.style.background = p.id === currentProject ? T.activeItemBg : 'transparent'}
                >
                  <span style={{ width: 6, height: 6, borderRadius: '50%', background: p.projectStatus === 'normal' ? T.success : T.warning, flexShrink: 0 }}></span>
                  <span style={{ flex: 1 }}>{p.projectName}</span>
                  <span style={{ fontSize: 10, color: T.textMuted, background: T.tagBg, padding: '1px 6px', borderRadius: 3 }}>{p.phase || ''}</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>

      {/* 主导航 */}
      <nav style={{ display: 'flex', gap: 2, marginLeft: 32, flex: 1, justifyContent: 'center' }}>
        {NAV_ITEMS.map(item => {
          const active = currentPage === item.id;
          return (
            <button
              key={item.id}
              onClick={() => onPageChange(item.id)}
              style={{
                padding: '6px 24px', border: 'none', cursor: 'pointer', borderRadius: 6,
                fontSize: 14, fontWeight: active ? 600 : 400,
                color: active ? '#fff' : T.textSecondary,
                background: active ? T.accent : 'transparent',
                transition: 'all 0.2s', letterSpacing: 0.5,
              }}
              onMouseEnter={e => { if (!active) e.currentTarget.style.background = T.hoverBg; }}
              onMouseLeave={e => { if (!active) e.currentTarget.style.background = 'transparent'; }}
            >
              {item.label}
            </button>
          );
        })}
      </nav>

      {/* 右侧 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 14, minWidth: 340, justifyContent: 'flex-end' }}>
        <div style={{ fontSize: 12, color: T.textMuted, fontVariantNumeric: 'tabular-nums', letterSpacing: 0.5 }}>
          {formatTime(time)}
        </div>

        {/* 主题切换 */}
        <div style={{ position: 'relative' }}>
          <button
            onClick={e => { e.stopPropagation(); setShowThemePicker(!showThemePicker); setShowProjects(false); }}
            style={{
              display: 'flex', alignItems: 'center', gap: 6,
              background: T.cardBg, border: `1px solid ${T.borderColor}`,
              borderRadius: 6, padding: '5px 10px', cursor: 'pointer',
              color: T.textSecondary, fontSize: 12,
            }}
            title="主题配色"
          >
            <span style={{
              width: 12, height: 12, borderRadius: '50%',
              background: `linear-gradient(135deg, ${T.accent}, ${T.accent2})`,
              border: `1px solid ${T.borderColor}`, flexShrink: 0,
            }}></span>
            <span>主题</span>
            <span style={{ color: T.textMuted, fontSize: 10 }}>▼</span>
          </button>
          {showThemePicker && (
            <div style={{
              position: 'absolute', top: '100%', right: 0, marginTop: 6,
              background: T.dropdownBg, border: `1px solid ${T.borderColor}`,
              borderRadius: 10, width: 320, zIndex: 200, padding: 14,
              boxShadow: '0 10px 30px rgba(0,0,0,0.5)',
            }} onClick={e => e.stopPropagation()}>
              <div style={{ fontSize: 11, color: T.textMuted, marginBottom: 10, letterSpacing: 0.5 }}>
                配色方案 &nbsp;·&nbsp; 深色 → 浅色
              </div>
              <div style={{
                height: 4, borderRadius: 2, marginBottom: 12,
                background: 'linear-gradient(to right, #060f1e, #0c0814, #0e0e0e, #071412, #1a1f2e, #f0f4f9)',
                border: `1px solid ${T.borderColor}`,
              }}></div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 8, marginBottom: 12 }}>
                {ALL_THEMES.map(th => {
                  const active = themeId === th.id;
                  return (
                    <button key={th.id} onClick={() => onThemeChange(th.id)} style={{
                      padding: '10px 6px 8px', borderRadius: 8, cursor: 'pointer',
                      border: `2px solid ${active ? th.accent : 'transparent'}`,
                      background: th.pageBg, outline: active ? `1px solid ${th.accent}` : 'none',
                      outlineOffset: 1, transition: 'all 0.15s', position: 'relative',
                    }}>
                      <div style={{ marginBottom: 6 }}>
                        <div style={{ height: 4, borderRadius: 1, background: th.navBg, border: `1px solid ${th.borderColor}`, marginBottom: 3 }}></div>
                        <div style={{ display: 'flex', gap: 2 }}>
                          <div style={{ width: '35%', height: 22, background: th.cardBg, borderRadius: 2, border: `1px solid ${th.borderColor}` }}></div>
                          <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 2 }}>
                            <div style={{ height: 10, background: th.cardBg, borderRadius: 2, border: `1px solid ${th.borderColor}` }}></div>
                            <div style={{ height: 10, background: th.cardBg, borderRadius: 2, border: `1px solid ${th.borderColor}` }}></div>
                          </div>
                        </div>
                        <div style={{ width: 10, height: 3, background: th.accent, borderRadius: 1, margin: '3px auto 0' }}></div>
                      </div>
                      <div style={{ fontSize: 10, color: th.textPrimary, fontWeight: active ? 700 : 400, lineHeight: 1.3 }}>{th.name}</div>
                      <div style={{ fontSize: 8, color: th.textMuted, marginTop: 1 }}>{th.brightness}</div>
                      {active && (
                        <div style={{ position: 'absolute', top: 4, right: 4, width: 6, height: 6, borderRadius: '50%', background: th.accent }}></div>
                      )}
                    </button>
                  );
                })}
              </div>
              <div style={{ height: 1, background: T.borderColor, marginBottom: 10 }}></div>
              <div style={{ fontSize: 10, color: T.textMuted, marginBottom: 6, letterSpacing: 0.5 }}>信息密度</div>
              <div style={{ display: 'flex', gap: 6 }}>
                {[{ label: '舒适型', v: false }, { label: '紧凑型', v: true }].map(opt => (
                  <button key={opt.label} onClick={() => onCompactChange(opt.v)} style={{
                    flex: 1, padding: '6px', borderRadius: 5, cursor: 'pointer', fontSize: 11,
                    border: `1px solid ${compactMode === opt.v ? T.accent : T.borderColor}`,
                    background: compactMode === opt.v ? T.activeItemBg : 'transparent',
                    color: compactMode === opt.v ? T.accent : T.textMuted,
                  }}>{opt.label}</button>
                ))}
              </div>
            </div>
          )}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 12, color: T.textSecondary }}>
          <div style={{
            width: 28, height: 28, borderRadius: '50%', background: T.accent,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: 11, color: '#fff', fontWeight: 600,
          }}>管</div>
          <span>平台管理员</span>
          <button
            onClick={onLogout}
            style={{
              marginLeft: 8,
              padding: '4px 12px',
              background: 'transparent',
              border: `1px solid ${T.borderColor}`,
              borderRadius: 4,
              color: T.textSecondary,
              fontSize: 12,
              cursor: 'pointer',
            }}
          >
            退出
          </button>
        </div>
      </div>

      {/* 新增项目弹窗 */}
      {showAddModal && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1001,
        }} onClick={() => setShowAddModal(false)}>
          <div style={{
            background: T.modalBg, border: `1px solid ${T.borderColor}`,
            borderRadius: 10, padding: 24, width: 500,
          }} onClick={e => e.stopPropagation()}>
            <div style={{ fontSize: 14, fontWeight: 600, color: T.textPrimary, marginBottom: 16 }}>新增项目</div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>项目名称 *</label>
                <input placeholder="请输入项目名称" value={addForm.projectName} onChange={e => setAddForm({ ...addForm, projectName: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }} />
              </div>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>项目简称</label>
                <input placeholder="请输入简称" value={addForm.shortName} onChange={e => setAddForm({ ...addForm, shortName: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }} />
              </div>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>工期</label>
                <input placeholder="如：2025.06-2026.12" value={addForm.period} onChange={e => setAddForm({ ...addForm, period: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }} />
              </div>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>当前阶段</label>
                <input placeholder="如：施工中" value={addForm.phase} onChange={e => setAddForm({ ...addForm, phase: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }} />
              </div>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>建筑面积(㎡)</label>
                <input placeholder="如：50000" value={addForm.area} onChange={e => setAddForm({ ...addForm, area: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }} />
              </div>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>项目状态</label>
                <select value={addForm.projectStatus} onChange={e => setAddForm({ ...addForm, projectStatus: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }}>
                  <option value="normal">正常</option>
                  <option value="warning">延期</option>
                  <option value="danger">停工</option>
                </select>
              </div>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>项目经理</label>
                <input placeholder="请输入项目经理" value={addForm.manager} onChange={e => setAddForm({ ...addForm, manager: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }} />
              </div>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>施工单位</label>
                <input placeholder="请输入施工单位" value={addForm.contractor} onChange={e => setAddForm({ ...addForm, contractor: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }} />
              </div>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>安全目标</label>
                <input placeholder="如：零事故" value={addForm.safetyGoal} onChange={e => setAddForm({ ...addForm, safetyGoal: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }} />
              </div>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>质量目标</label>
                <input placeholder="如：优良" value={addForm.qualityGoal} onChange={e => setAddForm({ ...addForm, qualityGoal: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }} />
              </div>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>开工日期</label>
                <input type="date" value={addForm.startDate} onChange={e => setAddForm({ ...addForm, startDate: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }} />
              </div>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>预计截止日期</label>
                <input type="date" value={addForm.endDate} onChange={e => setAddForm({ ...addForm, endDate: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }} />
              </div>
              <div style={{ gridColumn: '1 / -1' }}>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>项目描述</label>
                <textarea placeholder="请输入项目描述" value={addForm.description} onChange={e => setAddForm({ ...addForm, description: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none', minHeight: 60,
                }} />
              </div>
            </div>
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 20 }}>
              <button onClick={() => setShowAddModal(false)} style={{
                padding: '8px 16px', fontSize: 12, borderRadius: 5, cursor: 'pointer',
                background: 'transparent', border: `1px solid ${T.borderColor}`, color: T.textSecondary,
              }}>取消</button>
              <button onClick={handleAddProject} style={{
                padding: '8px 16px', fontSize: 12, borderRadius: 5, cursor: 'pointer',
                background: T.accent, border: 'none', color: '#fff',
              }}>保存</button>
            </div>
          </div>
        </div>
      )}
    </header>
  );
}

// ============================================
// 页面：项目概况
// ============================================
function OverviewPage({ projectId, theme: T, compactMode, projectList, onEnterCameraPage, cameraConfig, onRefreshProjects }) {
  const currentProjectInfo = projectList?.find(p => p.id === projectId) || {};
  const info = currentProjectInfo;
  const mockData = DATA_BY_PROJECT[projectId] || {};

  const [fullscreenCam, setFullscreenCam] = useState(null);
  const [docs, setDocs] = useState([]);
  const [cameras, setCameras] = useState([]);
  const [loading, setLoading] = useState(false);
  const [showUploadModal, setShowUploadModal] = useState(false);
  const [showDetailModal, setShowDetailModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [editForm, setEditForm] = useState({});
  const [refreshTick, setRefreshTick] = useState(0);
  const [localVideoLayout, setLocalVideoLayout] = useState(cameraConfig?.videoLayout || 4);

  // 使用 cameraConfig 的配置，但允许本地修改
  const videoLayout = localVideoLayout;
  const cameraAssignments = cameraConfig?.cameraAssignments || [];

  // 搜索草稿/应用双状态
  const [draftSearch, setDraftSearch] = useState('');
  const [draftType, setDraftType] = useState('全部');
  const [appliedSearch, setAppliedSearch] = useState('');
  const [appliedType, setAppliedType] = useState('全部');

  const [uploadForm, setUploadForm] = useState({ name: '', type: '培训资料', businessRef: '', note: '' });
  const [selectedFile, setSelectedFile] = useState(null);
  const [uploadLoading, setUploadLoading] = useState(false);

  const layouts = { 1: { cols: 1, rows: 1 }, 4: { cols: 2, rows: 2 }, 8: { cols: 4, rows: 2 }, 16: { cols: 4, rows: 4 } };
  // 计算项目进度
  const progressPercent = calculateProjectProgress(info?.startDate, info?.endDate);
  const stats = { ...(mockData?.stats || { onsite: 0, todayNewOnsite: 0 }), progressPercent };
  const onlineCount = cameras.filter(c => c.online === true).length;
  const totalCameras = cameras.length;

  const docTypes = ['全部', '培训资料', '施工日志', '签字文件', '会议纪要', '凭证文件'];
  const filteredDocs = docs.filter(d => {
    const matchType = appliedType === '全部' || d.type === appliedType;
    const matchName = !appliedSearch || d.name.includes(appliedSearch);
    return matchType && matchName;
  });

  // 获取真实摄像头数据
  useEffect(() => {
    const fetchCameras = async () => {
      try {
        const res = await getCameraList(projectId);
        if (res.code === 200 && res.data) {
          setCameras(res.data);
          // 默认选中前4个在线的摄像头
          const defaultSelected = res.data.filter(c => c.online).slice(0, 4).map(c => c.id);
          setSelectedCameras(defaultSelected);
        }
      } catch (e) {
        console.error('获取摄像头失败', e);
      }
    };
    fetchCameras();
  }, [projectId]);

  // 切换摄像头选中状态
  const toggleCamera = (camId) => {
    setSelectedCameras(prev => {
      if (prev.includes(camId)) {
        return prev.filter(id => id !== camId);
      } else {
        // 如果选中数量已达到布局数量，替换第一个
        if (prev.length >= videoLayout) {
          return [...prev.slice(1), camId];
        }
        return [...prev, camId];
      }
    });
  };

  // 显示的摄像头（目前直接显示前N个，后续镜头管理页面做好后可选择）
  const displayCams = cameras.slice(0, videoLayout);
  const layoutOptions = [
    { n: 1, label: '单屏' },
    { n: 4, label: '四屏' },
    { n: 8, label: '八窗口' },
    { n: 16, label: '十六窗口' },
  ];

  // 获取真实资料数据
  useEffect(() => {
    const fetchDocs = async () => {
      setLoading(true);
      try {
        const res = await getFileList(projectId);
        if (res.code === 200 && res.data) {
          setDocs(res.data.map(d => ({
            id: d.id,
            name: d.fileName,
            type: d.fileType,
            uploader: d.uploaderId,
            time: d.createTime,
            status: d.status,
            businessType: d.businessType,
            businessId: d.businessId,
            remark: d.remark,
          })));
        }
      } catch (e) {
        setDocs(mockData?.docs || []);
      } finally {
        setLoading(false);
      }
    };
    fetchDocs();
  }, [projectId]);

  // 项目切换时重置筛选
  useEffect(() => {
    setDraftSearch('');
    setDraftType('全部');
    setAppliedSearch('');
    setAppliedType('全部');
    setFullscreenCam(null);
  }, [projectId]);

  const statusColor = (s) => {
    if (s === '已归档') return T.success;
    if (s === '待确认') return T.warning;
    return T.accent;
  };

  const projectStatusStyle = (s) => {
    if (s === '正常' || s === 'normal') return { bg: `${T.success}22`, color: T.success, text: '正常' };
    if (s === '延期' || s === 'warning') return { bg: `${T.warning}22`, color: T.warning, text: '延期' };
    if (s === '停工' || s === 'danger') return { bg: `${T.danger}22`, color: T.danger, text: '停工' };
    return { bg: T.tagBg, color: T.textMuted, text: '未知' };
  };
  const psStyle = projectStatusStyle(info?.projectStatus);

  const { cols: gridCols } = layouts[videoLayout] || layouts[4];

  const handleQuery = () => { setAppliedSearch(draftSearch); setAppliedType(draftType); };
  const handleReset = () => { setDraftSearch(''); setDraftType('全部'); setAppliedSearch(''); setAppliedType('全部'); };

  const handleArchive = async (id) => {
    try {
      const res = await updateFileStatus(id, '已归档');
      if (res.code === 200) {
        setDocs(prev => prev.map(d => d.id === id ? { ...d, status: '已归档' } : d));
      } else {
        alert('归档失败：' + (res.message || '未知错误'));
      }
    } catch (e) {
      alert('归档失败，请重试');
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('确认删除此资料？此操作不可撤销。')) return;
    try {
      const res = await deleteFile(id);
      if (res.code === 200) {
        setDocs(prev => prev.filter(d => d.id !== id));
      } else {
        alert('删除失败：' + (res.message || '未知错误'));
      }
    } catch (e) {
      alert('删除失败，请重试');
    }
  };

  const handleUploadSubmit = async () => {
    if (!uploadForm.name.trim()) { alert('请填写资料名称'); return; }
    if (!selectedFile) { alert('请选择要上传的文件'); return; }

    setUploadLoading(true);
    try {
      const res = await uploadFile({
        file: selectedFile,
        projectId: projectId,
        fileName: uploadForm.name,
        fileType: uploadForm.type,
        businessType: uploadForm.businessRef,
        remark: uploadForm.note,
      });
      if (res.code === 200) {
        setDocs(prev => [{
          id: res.data.id,
          name: res.data.fileName,
          type: res.data.fileType,
          uploader: '平台管理员',
          time: res.data.createTime,
          status: res.data.status,
          filePath: res.data.filePath,
        }, ...prev]);
        setUploadForm({ name: '', type: '培训资料', businessRef: '', note: '' });
        setSelectedFile(null);
        setShowUploadModal(false);
        alert('上传成功');
      } else {
        alert(res.message || '上传失败');
      }
    } catch (e) {
      alert('上传失败：' + e.message);
    } finally {
      setUploadLoading(false);
    }
  };

  const handleFileSelect = (e) => {
    const file = e.target.files[0];
    if (file) {
      setSelectedFile(file);
      if (!uploadForm.name.trim()) {
        setUploadForm({ ...uploadForm, name: file.name });
      }
    }
  };

  const handleDownload = (doc) => {
    const token = localStorage.getItem('site_platform_token');
    const url = `/api/files/${doc.id}/download`;
    fetch(url, { headers: { Authorization: `Bearer ${token}` } })
      .then(r => r.blob())
      .then(blob => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = doc.name || '下载文件';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      })
      .catch(err => {
        console.error('下载失败:', err);
        alert('下载失败，请重试');
      });
  };

  const handlePreview = (doc) => {
    const token = localStorage.getItem('site_platform_token');
    const url = `/api/files/${doc.id}/download`;
    fetch(url, { headers: { Authorization: `Bearer ${token}` } })
      .then(r => r.blob())
      .then(blob => {
        const objectUrl = URL.createObjectURL(blob);
        window.open(objectUrl, '_blank');
        setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
      })
      .catch(err => {
        console.error('预览失败:', err);
        alert('预览失败，请重试');
      });
  };

  const handleRefreshVideo = () => { setRefreshTick(t => t + 1); };

  // 混合动态流
  const activities = [
    { type: 'upload', color: T.accent, text: '04-24 11:00  刘安全 上传了《特种作业证书-焊工.jpg》' },
    { type: 'alert', color: T.danger, text: '04-24 10:42  [告警] 材料仓库摄像头离线' },
    { type: 'flow', color: T.success, text: '04-24 10:15  《第一批培训资料.pdf》已归档' },
    { type: 'upload', color: T.accent, text: '04-24 09:11  王安全 上传了《工人签字确认表-B组.pdf》' },
    { type: 'alert', color: T.warning, text: '04-24 08:15  [告警] 混凝土泵车液压系统异常' },
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', gap: 12, padding: 16, overflow: 'hidden' }}>
      {/* StatCards */}
      <div style={{ display: 'flex', gap: 10, flexShrink: 0 }}>
        <StatCard label="在场人员" value={stats.onsite} sub={`今日新增 ${stats.todayNewOnsite} 人`} color={T.accent} theme={T} />
        <StatCard label="摄像头在线" value={`${onlineCount}/${totalCameras}`} sub={`${totalCameras - onlineCount} 路离线`} color={T.success} theme={T} />
        <StatCard label="今日资料" value={docs.length} sub={`待确认 ${docs.filter(d=>d.status==='待确认').length} 份`} color={T.warning} theme={T} />
        <StatCard label="项目进度" value={`${stats.progressPercent}%`} sub={info?.phase} color={T.accent2} theme={T} />
      </div>

      {/* 主内容区 */}
      <div style={{ display: 'flex', gap: 12, flex: 1, minHeight: 0 }}>
        {/* 左：项目基础信息 220px */}
        <div style={{
          width: 220, flexShrink: 0, background: T.cardBg,
          border: `1px solid ${T.borderColor}`, borderRadius: T.radius,
          padding: 14, overflow: 'auto',
          display: 'flex', flexDirection: 'column', gap: 12,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: `1px solid ${T.borderColor}`, paddingBottom: 8 }}>
            <span style={{ fontSize: 13, fontWeight: 600, color: T.textPrimary }}>项目基础信息</span>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{
                fontSize: 10, padding: '2px 8px', borderRadius: 10,
                background: psStyle.bg, color: psStyle.color, fontWeight: 600,
              }}>{psStyle.text}</span>
              <button onClick={() => { setEditForm({ ...info }); setShowEditModal(true); }} style={{
                fontSize: 10, padding: '2px 6px', borderRadius: 4,
                background: T.accent, border: 'none', color: '#fff', cursor: 'pointer',
              }}>编辑</button>
            </div>
          </div>
          {[
            ['项目名称', info?.projectName], ['建筑面积', info?.area], ['工期', info?.period],
            ['当前阶段', info?.phase], ['安全目标', info?.safetyGoal], ['质量目标', info?.qualityGoal],
            ['开工日期', info?.startDate], ['预计截止', info?.endDate],
          ].map(([k, v]) => (
            <div key={k}>
              <div style={{ fontSize: 10, color: T.textMuted, marginBottom: 2 }}>{k}</div>
              <div style={{ fontSize: 12, color: T.textSecondary, lineHeight: 1.4 }}>{v || '-'}</div>
            </div>
          ))}
          {/* 项目进度 */}
          {info?.startDate && info?.endDate && (() => {
            const progress = calculateProjectProgress(info.startDate, info.endDate);
            return (
              <div>
                <div style={{ fontSize: 10, color: T.textMuted, marginBottom: 4 }}>项目进度</div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <div style={{ flex: 1, height: 6, background: T.borderColor, borderRadius: 3, overflow: 'hidden' }}>
                    <div style={{ width: `${progress}%`, height: '100%', background: progress >= 100 ? T.success : T.accent, borderRadius: 3 }}></div>
                  </div>
                  <span style={{ fontSize: 12, color: T.textPrimary, fontWeight: 600, minWidth: 36 }}>{progress}%</span>
                </div>
              </div>
            );
          })()}
          <div>
            <div style={{ fontSize: 10, color: T.textMuted, marginBottom: 4 }}>项目简介</div>
            <div style={{ fontSize: 11, color: T.textMuted, lineHeight: 1.6 }}>{info?.description || '-'}</div>
          </div>
          <button onClick={() => setShowDetailModal(true)} style={{
            padding: '6px 0', background: 'transparent', border: `1px solid ${T.borderColor}`,
            borderRadius: 5, color: T.textSecondary, fontSize: 12, cursor: 'pointer',
          }}>查看详情 →</button>

          <div style={{
            marginTop: 4, background: T.surface2, border: `1px solid ${T.borderColor}`,
            borderRadius: 6, padding: 10,
          }}>
            <div style={{ fontSize: 11, color: T.textMuted, marginBottom: 6 }}>原人员管理系统</div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8 }}>
              <span style={{ width: 6, height: 6, borderRadius: '50%', background: T.success }}></span>
              <span style={{ fontSize: 11, color: T.success }}>系统可用</span>
            </div>
            <button style={{
              width: '100%', padding: '7px 0',
              background: `linear-gradient(135deg, ${T.accent}, ${T.accent2})`,
              border: 'none', borderRadius: 5, color: '#fff', fontSize: 12,
              cursor: 'pointer', fontWeight: 500,
            }}>进入人员管理系统 →</button>
          </div>

          <div style={{
            background: T.surface2, border: `1px solid ${T.borderColor}`,
            borderRadius: 6, padding: 10,
          }}>
            <div style={{ fontSize: 11, color: T.textMuted, marginBottom: 6 }}>镜头管理系统</div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8 }}>
              <span style={{ width: 6, height: 6, borderRadius: '50%', background: T.accent }}></span>
              <span style={{ fontSize: 11, color: T.accent }}>{cameras.length} 个镜头</span>
            </div>
            <button onClick={onEnterCameraPage} style={{
              width: '100%', padding: '7px 0',
              background: `linear-gradient(135deg, ${T.accent}, ${T.accent2})`,
              border: 'none', borderRadius: 5, color: '#fff', fontSize: 12,
              cursor: 'pointer', fontWeight: 500,
            }}>进入镜头管理 →</button>
          </div>
        </div>

        {/* 中：视频总览 flex:1 */}
        <div style={{
          flex: 1, background: T.cardBg, border: `1px solid ${T.borderColor}`,
          borderRadius: T.radius, padding: 14,
          display: 'flex', flexDirection: 'column', gap: 10, minWidth: 0,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexShrink: 0, flexWrap: 'wrap', gap: 6 }}>
            <span style={{ fontSize: 13, fontWeight: 600, color: T.textPrimary }}>视频总览</span>
            <div style={{ display: 'flex', gap: 4, alignItems: 'center', flexWrap: 'wrap' }}>
              {layoutOptions.map(opt => (
                <button key={opt.n} onClick={() => { setLocalVideoLayout(opt.n); setFullscreenCam(null); }} style={{
                  padding: '3px 10px', fontSize: 11, borderRadius: 4, cursor: 'pointer',
                  border: `1px solid ${localVideoLayout === opt.n && !fullscreenCam ? T.accent : T.borderColor}`,
                  background: localVideoLayout === opt.n && !fullscreenCam ? T.accent : 'transparent',
                  color: localVideoLayout === opt.n && !fullscreenCam ? '#fff' : T.textMuted,
                }}>{opt.label}</button>
              ))}
              <div style={{ width: 1, height: 16, background: T.borderColor, margin: '0 2px' }}></div>
              <button onClick={handleRefreshVideo} title="刷新视频" style={{
                padding: '3px 10px', fontSize: 11, borderRadius: 4, cursor: 'pointer',
                border: `1px solid ${T.borderColor}`, background: 'transparent', color: T.textMuted,
              }}>↻ 刷新</button>
            </div>
          </div>
          {fullscreenCam ? (
            <div style={{ flex: 1, position: 'relative', minHeight: 0 }}>
              <VideoCell key={`fs-${refreshTick}`} cam={fullscreenCam} theme={T} onFullscreen={() => setFullscreenCam(null)} fullscreen />
              <button onClick={() => setFullscreenCam(null)} style={{
                position: 'absolute', top: 10, right: 10,
                padding: '4px 10px', fontSize: 11, borderRadius: 4,
                background: 'rgba(0,0,0,0.6)', border: `1px solid ${T.borderColor}`,
                color: '#fff', cursor: 'pointer',
              }}>✕ 退出全屏</button>
            </div>
          ) : (
            <div style={{
              flex: 1, display: 'grid',
              gridTemplateColumns: `repeat(${gridCols}, 1fr)`,
              gap: 8, minHeight: 0, overflow: 'auto', alignContent: 'start',
            }}>
              {cameraAssignments.length > 0 ? (
                cameraAssignments.slice(0, videoLayout).map((camId, idx) => {
                  const cam = cameras.find(c => c.id === camId);
                  return cam ? (
                    <VideoCell
                      key={`${cam.id}-${refreshTick}`}
                      cam={cam}
                      theme={T}
                      onFullscreen={() => setFullscreenCam(cam)}
                    />
                  ) : (
                    <div key={`empty-${idx}`} style={{
                      aspectRatio: '16/9',
                      background: '#08111e',
                      borderRadius: 6,
                      border: `1px solid ${T.borderColor}`,
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                    }}>
                      <span style={{ fontSize: 11, color: '#444' }}>未分配</span>
                    </div>
                  );
                })
              ) : (
                cameras.slice(0, videoLayout).map(cam => (
                  <VideoCell
                    key={`${cam.id}-${refreshTick}`}
                    cam={cam}
                    theme={T}
                    onFullscreen={() => setFullscreenCam(cam)}
                  />
                ))
              )}
            </div>
          )}
        </div>

        {/* 右：资料管理 320px */}
        <div style={{
          width: 320, flexShrink: 0, background: T.cardBg,
          border: `1px solid ${T.borderColor}`, borderRadius: T.radius,
          padding: 14, display: 'flex', flexDirection: 'column', gap: 10, overflow: 'hidden',
        }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexShrink: 0 }}>
            <span style={{ fontSize: 13, fontWeight: 600, color: T.textPrimary }}>资料管理</span>
            <button onClick={() => setShowUploadModal(true)} style={{
              padding: '4px 12px', fontSize: 11, borderRadius: 5, cursor: 'pointer',
              background: T.accent, border: 'none', color: '#fff',
            }}>+ 上传资料</button>
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6, flexShrink: 0 }}>
            <div style={{ display: 'flex', gap: 4 }}>
              <input
                placeholder="搜索文件名称..."
                value={draftSearch}
                onChange={e => setDraftSearch(e.target.value)}
                onKeyDown={e => { if (e.key === 'Enter') handleQuery(); }}
                style={{
                  flex: 1, background: T.surface2, border: `1px solid ${T.borderColor}`,
                  borderRadius: 5, padding: '5px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }}
              />
              <button onClick={handleQuery} style={{
                padding: '5px 10px', fontSize: 11, borderRadius: 4, cursor: 'pointer',
                background: T.accent, border: 'none', color: '#fff',
              }}>查询</button>
              <button onClick={handleReset} style={{
                padding: '5px 10px', fontSize: 11, borderRadius: 4, cursor: 'pointer',
                background: 'transparent', border: `1px solid ${T.borderColor}`, color: T.textMuted,
              }}>重置</button>
            </div>
            <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
              {docTypes.map(t => (
                <button key={t} onClick={() => { setDraftType(t); setAppliedType(t); }} style={{
                  padding: '2px 8px', fontSize: 10, borderRadius: 3, cursor: 'pointer',
                  border: `1px solid ${draftType === t ? T.accent : T.borderColor}`,
                  background: draftType === t ? T.accent : 'transparent',
                  color: draftType === t ? '#fff' : T.textMuted,
                }}>{t}</button>
              ))}
            </div>
          </div>
          <div style={{ flex: 1, overflow: 'auto', display: 'flex', flexDirection: 'column', gap: 6 }}>
            {filteredDocs.length === 0 ? (
              <div style={{ textAlign: 'center', padding: '40px 10px', color: T.textMuted, fontSize: 12 }}>
                暂无符合条件的资料
              </div>
            ) : filteredDocs.map(doc => (
              <div key={doc.id} style={{
                background: T.surface2, border: `1px solid ${T.borderColor}`,
                borderRadius: 6, padding: '8px 10px',
              }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 4 }}>
                  <span style={{ fontSize: 12, color: T.textPrimary, flex: 1, marginRight: 6, lineHeight: 1.3 }}>{doc.name}</span>
                  <span style={{
                    fontSize: 9, padding: '1px 5px', borderRadius: 3,
                    background: 'transparent', border: `1px solid ${statusColor(doc.status)}`,
                    color: statusColor(doc.status), flexShrink: 0,
                  }}>{doc.status}</span>
                </div>
                <div style={{ fontSize: 10, color: T.textMuted, marginBottom: 2 }}>{doc.type} · 上传人ID:{doc.uploader}</div>
                {doc.businessType && <div style={{ fontSize: 10, color: T.textMuted, marginBottom: 2 }}>关联业务：{doc.businessType === 'safety_education' ? '安全教育培训' : doc.businessType === 'person' ? '人员管理' : doc.businessType}</div>}
                {doc.remark && <div style={{ fontSize: 10, color: T.textMuted, marginBottom: 2 }}>备注：{doc.remark}</div>}
                <div style={{ fontSize: 9, color: T.textMuted, marginBottom: 4 }}>{doc.time}</div>
                <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                  <button onClick={() => handlePreview(doc)} style={{ fontSize: 10, color: T.accent, background: 'none', border: 'none', cursor: 'pointer', padding: '0 2px' }}>查看</button>
                  <button onClick={() => handleDownload(doc)} style={{ fontSize: 10, color: T.textSecondary, background: 'none', border: 'none', cursor: 'pointer', padding: '0 2px' }}>下载</button>
                  {doc.status !== '已归档' && (
                    <button onClick={() => handleArchive(doc.id)} style={{ fontSize: 10, color: T.success, background: 'none', border: 'none', cursor: 'pointer', padding: '0 2px' }}>归档</button>
                  )}
                  <button onClick={() => handleDelete(doc.id)} style={{ fontSize: 10, color: T.danger, background: 'none', border: 'none', cursor: 'pointer', padding: '0 2px' }}>删除</button>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* 底部动态 */}
      <div style={{
        flexShrink: 0, background: T.cardBg, border: `1px solid ${T.borderColor}`,
        borderRadius: T.radius, padding: '10px 16px',
        display: 'flex', gap: 16, alignItems: 'center', overflow: 'hidden',
      }}>
        <span style={{ fontSize: 11, color: T.accent, fontWeight: 600, flexShrink: 0 }}>最新动态</span>
        <div style={{ display: 'flex', gap: 20, overflow: 'hidden', flex: 1 }}>
          {activities.map((item, i) => (
            <span key={i} style={{ fontSize: 11, color: T.textMuted, flexShrink: 0, display: 'flex', alignItems: 'center', gap: 6 }}>
              <span style={{ width: 6, height: 6, borderRadius: '50%', background: item.color, flexShrink: 0 }}></span>
              {item.text}
            </span>
          ))}
        </div>
      </div>

      {/* 上传弹窗 */}
      {showUploadModal && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.7)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000,
        }} onClick={() => setShowUploadModal(false)}>
          <div style={{
            background: T.modalBg, border: `1px solid ${T.borderColor}`,
            borderRadius: 12, padding: 24, width: 480, maxWidth: '90vw',
          }} onClick={e => e.stopPropagation()}>
            <div style={{ fontSize: 15, fontWeight: 600, color: T.textPrimary, marginBottom: 20 }}>上传资料</div>
            <div style={{ marginBottom: 14 }}>
              <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>资料名称 *</div>
              <input placeholder="请输入资料名称"
                value={uploadForm.name}
                onChange={e => setUploadForm({ ...uploadForm, name: e.target.value })}
                style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`,
                  borderRadius: 6, padding: '8px 10px', fontSize: 13, color: T.textPrimary, outline: 'none',
                }}/>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14, marginBottom: 14 }}>
              <div>
                <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>资料类型</div>
                <select
                  value={uploadForm.type}
                  onChange={e => setUploadForm({ ...uploadForm, type: e.target.value })}
                  style={{
                    width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`,
                    borderRadius: 6, padding: '8px 10px', fontSize: 13, color: T.textPrimary, outline: 'none',
                  }}>
                  {['培训资料','施工日志','签字文件','会议纪要','凭证文件','其他'].map(t => <option key={t}>{t}</option>)}
                </select>
              </div>
              <div>
                <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>关联业务</div>
                <input placeholder="如：批次名称/人员"
                  value={uploadForm.businessRef}
                  onChange={e => setUploadForm({ ...uploadForm, businessRef: e.target.value })}
                  style={{
                    width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`,
                    borderRadius: 6, padding: '8px 10px', fontSize: 13, color: T.textPrimary, outline: 'none',
                  }}/>
              </div>
            </div>
            <div style={{ marginBottom: 14 }}>
              <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>备注</div>
              <input placeholder="可选"
                value={uploadForm.note}
                onChange={e => setUploadForm({ ...uploadForm, note: e.target.value })}
                style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`,
                  borderRadius: 6, padding: '8px 10px', fontSize: 13, color: T.textPrimary, outline: 'none',
                }}/>
            </div>
            <div style={{ marginBottom: 20 }}>
              <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>上传附件 *</div>
              <label style={{
                border: `2px dashed ${T.borderColor}`, borderRadius: 6, padding: '16px',
                textAlign: 'center', color: T.textMuted, fontSize: 12, cursor: 'pointer',
                display: 'block', background: T.surface2,
              }}>
                <input type="file" style={{ display: 'none' }} onChange={handleFileSelect} />
                {selectedFile ? (
                  <div>
                    <div style={{ color: T.textPrimary, fontSize: 13, marginBottom: 4 }}>{selectedFile.name}</div>
                    <div style={{ fontSize: 11 }}>点击更换文件</div>
                  </div>
                ) : (
                  <div>
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" style={{ margin: '0 auto 8px', display: 'block' }}>
                      <path d="M21 15v4a2 2 0 01-2 2H5a2 2 0 01-2-2v-4M17 8l-5-5-5 5M12 3v12" />
                    </svg>
                    点击或拖拽文件至此处上传
                  </div>
                )}
              </label>
            </div>
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
              <button onClick={() => { setShowUploadModal(false); setSelectedFile(null); setUploadForm({ name: '', type: '培训资料', businessRef: '', note: '' }); }} style={{
                padding: '8px 20px', borderRadius: 6, border: `1px solid ${T.borderColor}`,
                background: 'none', color: T.textSecondary, cursor: 'pointer', fontSize: 13,
              }}>取消</button>
              <button onClick={handleUploadSubmit} disabled={uploadLoading} style={{
                padding: '8px 20px', borderRadius: 6, border: 'none',
                background: uploadLoading ? T.textMuted : T.accent, color: '#fff', cursor: uploadLoading ? 'not-allowed' : 'pointer', fontSize: 13,
              }}>{uploadLoading ? '上传中...' : '确认上传'}</button>
            </div>
          </div>
        </div>
      )}

      {/* 项目详情弹窗 */}
      {showDetailModal && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.7)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000,
        }} onClick={() => setShowDetailModal(false)}>
          <div style={{
            background: T.modalBg, border: `1px solid ${T.borderColor}`,
            borderRadius: 12, padding: 28, width: 560, maxWidth: '92vw', maxHeight: '86vh', overflow: 'auto',
          }} onClick={e => e.stopPropagation()}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 18 }}>
              <span style={{ fontSize: 16, fontWeight: 700, color: T.textPrimary }}>{info?.name}</span>
              <span style={{
                fontSize: 11, padding: '3px 10px', borderRadius: 12,
                background: psStyle.bg, color: psStyle.color, fontWeight: 600,
              }}>{info?.projectStatus}</span>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px 20px', marginBottom: 16 }}>
              {[
                ['项目简称', info?.shortName], ['建筑面积', info?.area],
                ['工期', info?.period], ['当前阶段', info?.phase],
                ['项目经理', info?.manager], ['承建单位', info?.contractor],
                ['安全目标', info?.safetyGoal], ['质量目标', info?.qualityGoal],
                ['开工日期', info?.startDate], ['预计截止', info?.endDate],
              ].map(([k, v]) => (
                <div key={k}>
                  <div style={{ fontSize: 11, color: T.accent, marginBottom: 3, fontWeight: 500 }}>{k}</div>
                  <div style={{ fontSize: 13, color: T.textPrimary, lineHeight: 1.4 }}>{v}</div>
                </div>
              ))}
            </div>
            <div style={{ marginBottom: 20 }}>
              <div style={{ fontSize: 11, color: T.accent, marginBottom: 4, fontWeight: 500 }}>项目简介</div>
              <div style={{ fontSize: 13, color: T.textSecondary, lineHeight: 1.7 }}>{info?.desc}</div>
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <button onClick={() => setShowDetailModal(false)} style={{
                padding: '8px 24px', borderRadius: 6, border: 'none',
                background: T.accent, color: '#fff', cursor: 'pointer', fontSize: 13,
              }}>关闭</button>
            </div>
          </div>
        </div>
      )}

      {/* 编辑项目弹窗 */}
      {showEditModal && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1001,
        }} onClick={() => setShowEditModal(false)}>
          <div style={{
            background: T.modalBg, border: `1px solid ${T.borderColor}`,
            borderRadius: 10, padding: 24, width: 500,
          }} onClick={e => e.stopPropagation()}>
            <div style={{ fontSize: 14, fontWeight: 600, color: T.textPrimary, marginBottom: 16 }}>编辑项目信息</div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>项目名称</label>
                <input value={editForm.projectName || ''} onChange={e => setEditForm({ ...editForm, projectName: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }} />
              </div>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>项目简称</label>
                <input value={editForm.shortName || ''} onChange={e => setEditForm({ ...editForm, shortName: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }} />
              </div>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>工期</label>
                <input value={editForm.period || ''} onChange={e => setEditForm({ ...editForm, period: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }} />
              </div>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>当前阶段</label>
                <input value={editForm.phase || ''} onChange={e => setEditForm({ ...editForm, phase: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }} />
              </div>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>建筑面积(㎡)</label>
                <input value={editForm.area || ''} onChange={e => setEditForm({ ...editForm, area: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }} />
              </div>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>项目状态</label>
                <select value={editForm.projectStatus || 'normal'} onChange={e => setEditForm({ ...editForm, projectStatus: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }}>
                  <option value="normal">正常</option>
                  <option value="warning">延期</option>
                  <option value="danger">停工</option>
                </select>
              </div>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>项目经理</label>
                <input value={editForm.manager || ''} onChange={e => setEditForm({ ...editForm, manager: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }} />
              </div>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>施工单位</label>
                <input value={editForm.contractor || ''} onChange={e => setEditForm({ ...editForm, contractor: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }} />
              </div>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>安全目标</label>
                <input value={editForm.safetyGoal || ''} onChange={e => setEditForm({ ...editForm, safetyGoal: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }} />
              </div>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>质量目标</label>
                <input value={editForm.qualityGoal || ''} onChange={e => setEditForm({ ...editForm, qualityGoal: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }} />
              </div>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>开工日期</label>
                <input type="date" value={editForm.startDate || ''} onChange={e => setEditForm({ ...editForm, startDate: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }} />
              </div>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>预计截止日期</label>
                <input type="date" value={editForm.endDate || ''} onChange={e => setEditForm({ ...editForm, endDate: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }} />
              </div>
              <div style={{ gridColumn: '1 / -1' }}>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>项目描述</label>
                <textarea value={editForm.description || ''} onChange={e => setEditForm({ ...editForm, description: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none', minHeight: 60,
                }} />
              </div>
            </div>
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', marginTop: 20 }}>
              <button onClick={() => setShowEditModal(false)} style={{
                padding: '8px 16px', fontSize: 12, borderRadius: 5, cursor: 'pointer',
                background: 'transparent', border: `1px solid ${T.borderColor}`, color: T.textSecondary,
              }}>取消</button>
              <button onClick={async () => {
                try {
                  const res = await updateProject(projectId, editForm);
                  if (res.code === 200) {
                    setShowEditModal(false);
                    // 通知父组件刷新项目列表
                    if (typeof onRefreshProjects === 'function') onRefreshProjects();
                  } else {
                    alert(res.message || '保存失败');
                  }
                } catch (e) {
                  alert('保存失败，请重试');
                }
              }} style={{
                padding: '8px 16px', fontSize: 12, borderRadius: 5, cursor: 'pointer',
                background: T.accent, border: 'none', color: '#fff',
              }}>保存</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// 页面：人员与安全
// ============================================
function PersonnelPage({ projectId, theme: T, compactMode }) {
  const mockData = DATA_BY_PROJECT[projectId];

  const [personnel, setPersonnel] = useState([]);
  const [trainings, setTrainings] = useState([]);
  const [files, setFiles] = useState([]);
  const [loading, setLoading] = useState(false);

  // 搜索草稿/应用双状态
  const [draftSearch, setDraftSearch] = useState('');
  const [draftStatus, setDraftStatus] = useState('全部');
  const [appliedSearch, setAppliedSearch] = useState('');
  const [appliedStatus, setAppliedStatus] = useState('全部');

  const [showAddModal, setShowAddModal] = useState(false);
  const [addForm, setAddForm] = useState({ name: '', gender: '男', idcard: '', phone: '', unit: '', role: '普工', note: '' });
  const [editingPerson, setEditingPerson] = useState(null);
  const [selectedPerson, setSelectedPerson] = useState(null);

  const [showTrainingModal, setShowTrainingModal] = useState(false);
  const [showEditTrainingModal, setShowEditTrainingModal] = useState(false);
  const [editTrainingForm, setEditTrainingForm] = useState({});
  const [trainingForm, setTrainingForm] = useState({ name: '', eduType: '临时人员安全三级教育', time: '', place: '', trainer: '', personIds: [], note: '', courseHours: '', trainingMaterial: '', examType: '' });
  const [trainingFiles, setTrainingFiles] = useState([]);
  const [trainingFile, setTrainingFile] = useState(null);
  const [trainingRecordView, setTrainingRecordView] = useState(null);
  const [trainingTimeFocused, setTrainingTimeFocused] = useState(false);
  const [editTrainingTimeFocused, setEditTrainingTimeFocused] = useState(false);

  const [fileTypeFilter, setFileTypeFilter] = useState('全部');
  const [previewFile, setPreviewFile] = useState(null);

  const statusFilters = ['全部', '待教育', '已教育', '已离场'];
  const fileTypes = ['全部', '培训资料', '签字文件', '证书'];

  const toTrainingInputValue = (value) => {
    if (!value) return '';
    const text = String(value).replace(' ', 'T');
    return text.length >= 16 ? text.slice(0, 16) : text;
  };

  const toTrainingSubmitTime = (value) => {
    const text = toTrainingInputValue(value);
    return text ? `${text}:00` : '';
  };

  const formatTrainingTime = (value) => {
    if (!value) return '-';
    return String(value).replace('T', ' ').slice(0, 16);
  };

  const makeTrainingFileMeta = (apiFile, fallbackFile) => ({
    id: apiFile?.id,
    name: apiFile?.fileName || fallbackFile?.name,
    fileName: apiFile?.fileName || fallbackFile?.name,
    fileType: apiFile?.fileType || '培训资料',
    fileSize: apiFile?.fileSize || fallbackFile?.size,
  });

  const buildTrainingPayload = (form, fileIds = []) => ({
    batchName: form.name,
    eduType: form.eduType || form.type,
    time: toTrainingSubmitTime(form.time),
    place: form.place,
    trainer: form.trainer,
    personIds: form.personIds || [],
    courseHours: form.courseHours ? form.courseHours : undefined,
    examType: form.examType,
    trainingMaterial: form.trainingMaterial,
    remark: form.note || form.remark,
    projectId,
    fileIds,
  });

  const renderCalendarIcon = (active) => (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <rect x="3" y="5" width="18" height="16" rx="2" stroke={active ? T.accent : T.textMuted} strokeWidth="2" />
      <path d="M8 3v4M16 3v4M3 10h18" stroke={active ? T.accent : T.textMuted} strokeWidth="2" strokeLinecap="round" />
    </svg>
  );

  const getFileId = (file, idx, record) => file?.id || (record?.uploadedFileIds && record.uploadedFileIds[idx]);

  const downloadTrainingFile = async (file) => {
    if (!file?.id) {
      alert('文件未保存');
      return;
    }
    const token = localStorage.getItem('site_platform_token');
    const response = await fetch(`/api/files/${file.id}/download`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!response.ok) throw new Error('文件下载失败');
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = file.name || file.fileName || `training-file-${file.id}`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  };

  const previewTrainingFile = async (file) => {
    if (!file?.id) {
      alert('文件未保存');
      return;
    }
    const token = localStorage.getItem('site_platform_token');
    const response = await fetch(`/api/files/${file.id}/download`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!response.ok) throw new Error('文件查看失败');
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    window.open(url, '_blank', 'noopener,noreferrer');
    setTimeout(() => URL.revokeObjectURL(url), 60000);
  };

  const handleDownloadTrainingFiles = async (training) => {
    const savedFiles = (training.files || []).map((file, idx) => ({
      ...file,
      id: getFileId(file, idx, training),
    })).filter(file => file.id);
    if (savedFiles.length === 0) {
      alert('暂无可下载的培训附件');
      return;
    }
    try {
      for (const file of savedFiles) {
        await downloadTrainingFile(file);
      }
    } catch (err) {
      console.error('培训附件下载失败', err);
      alert('培训附件下载失败，请重试');
    }
  };

  const openEditTraining = (training) => {
    setEditTrainingForm({ ...training, time: toTrainingInputValue(training.time) });
    setShowEditTrainingModal(true);
  };

  // 获取真实数据
  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      try {
        const [personnelRes, trainingRes, fileRes] = await Promise.all([
          getPersonnelList(projectId),
          getTrainingList(projectId),
          getFileList(projectId),
        ]);

        if (personnelRes.code === 200 && personnelRes.data) {
          setPersonnel(personnelRes.data.map(p => ({
            id: p.id,
            name: p.name,
            gender: p.gender,
            idcard: p.idcard,
            phone: p.phone,
            unit: p.unit,
            role: p.role,
            entryTime: p.entryTime,
            status: p.status,
          })));
        }

        if (trainingRes.code === 200 && trainingRes.data) {
          setTrainings(trainingRes.data.map(t => ({
            id: t.id,
            name: t.batchName,
            eduType: t.eduType,
            time: t.time,
            place: t.place || t.trainingPlace,
            trainer: t.trainer,
            status: t.status,
            personIds: t.personIds || [],
            files: t.files || [],
            courseHours: t.courseHours,
            examType: t.examType,
            trainingMaterial: t.trainingMaterial,
            note: t.remark,
          })));
        }

        if (fileRes.code === 200 && fileRes.data) {
          setFiles(fileRes.data.map(f => ({
            id: f.id,
            name: f.fileName,
            fileType: f.fileType,
            batchName: f.businessType,
            uploader: f.uploaderId,
            time: f.createTime,
          })));
        }
      } catch (e) {
        setPersonnel(mockData?.personnel || []);
        setTrainings(mockData?.trainings || []);
        setFiles(mockData?.files || []);
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, [projectId]);

  const filteredPersonnel = personnel.filter(p => {
    const matchStatus = appliedStatus === '全部' || p.status === appliedStatus;
    const matchName = !appliedSearch || p.name.includes(appliedSearch) || (p.idcard && p.idcard.includes(appliedSearch));
    return matchStatus && matchName;
  });

  const filteredFiles = fileTypeFilter === '全部' ? files : files.filter(f => f.fileType === fileTypeFilter);

  const eduRate = personnel.length === 0 ? 0 : Math.round((personnel.filter(p => p.status === '已教育').length / personnel.length) * 100);
  const todayNew = personnel.filter(p => p.entryTime?.startsWith(new Date().toISOString().slice(0, 10))).length;
  const todayEducated = personnel.filter(p => p.status === '已教育').length;
  const pendingCount = personnel.filter(p => p.status === '待教育').length;

  const handleQueryPersonnel = () => { setAppliedSearch(draftSearch); setAppliedStatus(draftStatus); };
  const handleResetPersonnel = () => { setDraftSearch(''); setDraftStatus('全部'); setAppliedSearch(''); setAppliedStatus('全部'); };

  const handleExportCSV = () => {
    const header = ['姓名', '性别', '身份证号', '手机号', '所属单位', '工种', '入场时间', '状态'];
    const rows = filteredPersonnel.map(p => [p.name, p.gender, p.idcard, p.phone, p.unit, p.role, p.entryTime, p.status]);
    const csv = '﻿' + [header, ...rows].map(r => r.map(c => `"${c ?? ''}"`).join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `临时人员_${new Date().toISOString().slice(0,10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleAddPerson = async () => {
    if (!addForm.name?.trim()) { alert('请填写姓名'); return; }
    try {
      const now = new Date();
      const pad = n => String(n).padStart(2, '0');
      const entryTime = `${now.getFullYear()}-${pad(now.getMonth()+1)}-${pad(now.getDate())}T${pad(now.getHours())}:${pad(now.getMinutes())}:00`;
      const res = await addPersonnel({ ...addForm, projectId, entryTime, status: '待教育' });
      if (res.code === 200) {
        setPersonnel(prev => [...prev, {
          id: res.data.id || res.data,
          ...addForm,
          entryTime,
          status: '待教育',
        }]);
        setAddForm({ name: '', gender: '男', idcard: '', phone: '', unit: '', role: '普工', note: '' });
        setShowAddModal(false);
      } else {
        alert(res.message || '添加失败');
      }
    } catch (e) {
      alert('添加失败，请重试');
    }
  };

  const handleEditPerson = () => {
    setPersonnel(personnel.map(p => p.id === editingPerson.id ? editingPerson : p));
    setEditingPerson(null);
  };

  const handleDeletePerson = (p) => {
    if (window.confirm(`确认删除 ${p.name}？此操作不可撤销。`)) {
      setPersonnel(personnel.filter(x => p.id !== x.id));
    }
  };

  const handleCreateTraining = async () => {
    if (!trainingForm.name.trim()) { alert('请填写批次名称'); return; }
    if (trainingForm.personIds.length === 0) { alert('请至少关联一位人员'); return; }

    let uploadedFileIds = [];
    let uploadedFiles = [];
    try {
      for (const file of trainingFiles) {
        const res = await uploadFile({
          file,
          projectId,
          fileName: file.name,
          fileType: '培训资料',
          businessType: 'safety_education',
        });
        if (res.code === 200 && res.data?.id) {
          uploadedFileIds.push(res.data.id);
          uploadedFiles.push(makeTrainingFileMeta(res.data, file));
        }
      }
    } catch (e) {
      console.error('文件上传失败', e);
      alert('培训附件上传失败，请重试');
      return;
    }

    try {
      const res = await createTraining(buildTrainingPayload(trainingForm, uploadedFileIds));
      if (res.code === 200) {
        const newTraining = {
          id: res.data.id,
          ...trainingForm,
          count: trainingForm.personIds.length,
          status: '进行中',
          type: trainingForm.eduType,
          files: uploadedFiles,
          uploadedFileIds,
        };
        setTrainings(prev => [...prev, newTraining]);
        setTrainingForm({ name: '', eduType: '临时人员安全三级教育', time: '', place: '', trainer: '', personIds: [], note: '', courseHours: '', trainingMaterial: '', examType: '' });
        setTrainingFiles([]);
        setTrainingFile(null);
        setShowTrainingModal(false);
      } else {
        alert(res.message || '创建失败');
      }
    } catch (e) {
      alert('创建失败，请重试');
    }
  };

  const handleTrainingFileChange = (e) => {
    const files = Array.from(e.target.files);
    setTrainingFiles(prev => [...prev, ...files]);
  };

  const handleRemoveTrainingFile = (idx) => {
    setTrainingFiles(prev => prev.filter((_, i) => i !== idx));
  };

  const handleDeleteTraining = async (training) => {
    if (!window.confirm(`确认删除「${training.name}」这条安全三级教育培训记录？`)) return;
    try {
      const res = await deleteTraining(training.id);
      if (res.code === 200) {
        setTrainings(prev => prev.filter(t => t.id !== training.id));
        if (trainingRecordView?.id === training.id) setTrainingRecordView(null);
        if (editTrainingForm?.id === training.id) setShowEditTrainingModal(false);
      } else {
        alert(res.message || '删除失败');
      }
    } catch (err) {
      console.error('删除培训记录失败', err);
      alert('删除失败，请重试');
    }
  };

  const uploadTrainingAttachments = async (rawFiles, businessId) => {
    const newFiles = [];
    for (const file of rawFiles) {
      const res = await uploadFile({
        file,
        projectId,
        fileName: file.name,
        fileType: '培训资料',
        businessType: 'safety_education',
        businessId,
      });
      if (res.code === 200 && res.data?.id) {
        newFiles.push(makeTrainingFileMeta(res.data, file));
      }
    }
    return newFiles;
  };

  const handleMarkComplete = (training) => {
    if (!window.confirm(`确认标记「${training.name}」为已完成？\n关联的 ${training.personIds.length} 人状态将自动更新为"已教育"`)) return;
    setTrainings(trainings.map(t => t.id === training.id ? { ...t, status: '已完成' } : t));
    setPersonnel(personnel.map(p => training.personIds.includes(p.id) ? { ...p, status: '已教育' } : p));
  };

  const eligiblePersonnel = personnel.filter(p => p.status === '待教育');

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', padding: 16, gap: 12, overflow: 'hidden' }}>
      {/* StatCards */}
      <div style={{ display: 'flex', gap: 10, flexShrink: 0 }}>
        {[
          { label: '今日新增人员', value: todayNew, color: T.accent },
          { label: '已完成教育', value: todayEducated, color: T.success },
          { label: '待教育人员', value: pendingCount, color: T.warning },
          { label: '教育完成率', value: `${eduRate}%`, color: T.accent2 },
        ].map(s => (
          <div key={s.label} style={{
            flex: 1, background: T.cardBg, border: `1px solid ${T.borderColor}`,
            borderRadius: T.radius, padding: '14px 16px',
          }}>
            <div style={{ fontSize: 11, color: T.textMuted, marginBottom: 4 }}>{s.label}</div>
            <div style={{ fontSize: 26, fontWeight: 700, color: s.color, lineHeight: 1 }}>{s.value}</div>
          </div>
        ))}
      </div>

      {/* 三栏内容区 */}
      <div style={{ display: 'flex', gap: 12, flex: 1, minHeight: 0 }}>
        {/* 左：临时人员管理 flex:1.2 */}
        <div style={{ flex: 1.2, minWidth: 0 }}>
          <SectionCard
            title="临时人员管理"
            theme={T}
            action={
              <div style={{ display: 'flex', gap: 6 }}>
                <button onClick={handleExportCSV} style={{
                  padding: '4px 10px', fontSize: 11, borderRadius: 5, cursor: 'pointer',
                  background: 'transparent', border: `1px solid ${T.borderColor}`, color: T.textSecondary,
                }}>导出</button>
                <button onClick={() => setShowAddModal(true)} style={{
                  padding: '4px 12px', fontSize: 11, borderRadius: 5, cursor: 'pointer',
                  background: T.accent, border: 'none', color: '#fff',
                }}>+ 新增人员</button>
              </div>
            }
          >
            <div style={{ display: 'flex', gap: 6, marginBottom: 10, flexWrap: 'wrap' }}>
              <input
                placeholder="搜索姓名/身份证..."
                value={draftSearch}
                onChange={e => setDraftSearch(e.target.value)}
                onKeyDown={e => { if (e.key === 'Enter') handleQueryPersonnel(); }}
                style={{
                  flex: 1, minWidth: 120, background: T.surface2, border: `1px solid ${T.borderColor}`,
                  borderRadius: 5, padding: '5px 8px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }}
              />
              <button onClick={handleQueryPersonnel} style={{
                padding: '4px 10px', fontSize: 11, borderRadius: 4, cursor: 'pointer',
                background: T.accent, border: 'none', color: '#fff',
              }}>查询</button>
              <button onClick={handleResetPersonnel} style={{
                padding: '4px 10px', fontSize: 11, borderRadius: 4, cursor: 'pointer',
                background: 'transparent', border: `1px solid ${T.borderColor}`, color: T.textMuted,
              }}>重置</button>
            </div>
            <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', marginBottom: 10 }}>
              {statusFilters.map(f => (
                <button key={f} onClick={() => { setDraftStatus(f); setAppliedStatus(f); }} style={{
                  padding: '3px 10px', fontSize: 10, borderRadius: 3, cursor: 'pointer',
                  border: `1px solid ${draftStatus === f ? T.accent : T.borderColor}`,
                  background: draftStatus === f ? T.accent : 'transparent',
                  color: draftStatus === f ? '#fff' : T.textMuted,
                }}>{f}</button>
              ))}
            </div>
            <div style={{ overflow: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 12 }}>
                <thead>
                  <tr>
                    {['姓名', '工种', '所属单位', '入场时间', '状态', '操作'].map(h => (
                      <th key={h} style={{
                        textAlign: 'left', padding: '6px 8px', color: T.textMuted,
                        borderBottom: `1px solid ${T.borderColor}`, fontWeight: 500, whiteSpace: 'nowrap',
                      }}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {filteredPersonnel.length === 0 ? (
                    <tr><td colSpan={6} style={{ padding: '30px 0', textAlign: 'center', color: T.textMuted }}>暂无数据</td></tr>
                  ) : filteredPersonnel.map((p, i) => (
                    <tr key={p.id} style={{ background: i % 2 === 0 ? 'transparent' : T.surface2 }}>
                      <td style={{ padding: '7px 8px', color: T.textPrimary }}>{p.name}</td>
                      <td style={{ padding: '7px 8px', color: T.textSecondary }}>{p.role}</td>
                      <td style={{ padding: '7px 8px', color: T.textMuted, fontSize: 11 }}>{p.unit}</td>
                      <td style={{ padding: '7px 8px', color: T.textMuted, fontSize: 11 }}>{p.entryTime?.split(' ')[0] || ''}</td>
                      <td style={{ padding: '7px 8px' }}><StatusBadge status={p.status} theme={T} /></td>
                      <td style={{ padding: '7px 8px' }}>
                        <div style={{ display: 'flex', gap: 8 }}>
                          <button onClick={() => setSelectedPerson(p)} style={{ fontSize: 10, color: T.accent, background: 'none', border: 'none', cursor: 'pointer' }}>详情</button>
                          <button onClick={() => setEditingPerson({ ...p })} style={{ fontSize: 10, color: T.textSecondary, background: 'none', border: 'none', cursor: 'pointer' }}>编辑</button>
                          <button onClick={() => handleDeletePerson(p)} style={{ fontSize: 10, color: T.danger, background: 'none', border: 'none', cursor: 'pointer' }}>删除</button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </SectionCard>
        </div>

        {/* 中：安全三级教育 flex:1 */}
        <div style={{ flex: 1, minWidth: 0 }}>
          <SectionCard
            title="安全三级教育"
            theme={T}
            action={
              <button onClick={() => setShowTrainingModal(true)} style={{
                padding: '4px 12px', fontSize: 11, borderRadius: 5, cursor: 'pointer',
                background: T.accent, border: 'none', color: '#fff',
              }}>+ 新建培训</button>
            }
          >
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {trainings.length === 0 && (
                <div style={{ textAlign: 'center', padding: '40px 0', color: T.textMuted, fontSize: 12 }}>暂无培训批次</div>
              )}
              {trainings.map(t => {
                const linkedNames = t.personIds.map(id => personnel.find(p => p.id === id)?.name).filter(Boolean);
                return (
                  <div key={t.id} style={{
                    background: T.surface2, border: `1px solid ${T.borderColor}`,
                    borderRadius: 6, padding: 12,
                  }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 8 }}>
                      <span style={{ fontSize: 12, fontWeight: 600, color: T.textPrimary, lineHeight: 1.3, flex: 1, marginRight: 8 }}>{t.name}</span>
                      <StatusBadge status={t.status} theme={T} />
                    </div>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '4px 0', fontSize: 11, color: T.textMuted }}>
                      <span>时间：{formatTrainingTime(t.time)}</span>
                      <span>地点：{t.place}</span>
                      <span>讲师：{t.trainer}</span>
                      <span>人数：{t.personIds.length} 人</span>
                    </div>
                    {linkedNames.length > 0 && (
                      <div style={{ marginTop: 6, fontSize: 10, color: T.textSecondary, lineHeight: 1.4 }}>
                        关联：{linkedNames.slice(0, 4).join('、')}{linkedNames.length > 4 ? ` 等 ${linkedNames.length} 人` : ''}
                      </div>
                    )}
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8, marginTop: 8 }}>
                      <div style={{ display: 'flex', gap: 10 }}>
                        <button onClick={() => setTrainingRecordView(t)} style={{ fontSize: 10, color: T.accent, background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}>查看</button>
                        <button onClick={() => openEditTraining(t)} style={{ fontSize: 10, color: T.textSecondary, background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}>编辑</button>
                        <button onClick={() => handleDownloadTrainingFiles(t)} style={{ fontSize: 10, color: T.textSecondary, background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}>下载</button>
                        <button onClick={() => handleDeleteTraining(t)} style={{ fontSize: 10, color: T.danger, background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}>删除</button>
                      </div>
                      {t.status !== '已完成' && (
                        <button onClick={() => handleMarkComplete(t)} style={{ fontSize: 10, color: T.success, background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}>标记完成</button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </SectionCard>
        </div>

        {/* 右：培训资料 + 最近动态 280px */}
        <div style={{ width: 280, flexShrink: 0, display: 'flex', flexDirection: 'column', gap: 10, overflow: 'hidden' }}>
          <div style={{ flex: 1, minHeight: 0 }}>
            <div style={{ background: T.cardBg, border: `1px solid ${T.borderColor}`, borderRadius: T.radius, display: 'flex', flexDirection: 'column', overflow: 'hidden', height: '100%' }}>
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '12px 16px', borderBottom: `1px solid ${T.borderColor}`, flexShrink: 0 }}>
                <span style={{ fontSize: 13, fontWeight: 600, color: T.textPrimary }}>培训资料上传</span>
                <button type="button" onClick={() => {
                  setUploadForm({ name: '', type: '培训资料', businessRef: '', note: '' });
                  setSelectedFile(null);
                  setShowUploadModal(true);
                }} style={{
                  padding: '4px 10px', fontSize: 10, borderRadius: 4, cursor: 'pointer',
                  background: T.accent, border: 'none', color: '#fff',
                }}>上传文件</button>
              </div>
              <div style={{ flex: 1, overflow: 'auto', padding: 12 }}>
                <div style={{ display: 'flex', gap: 3, flexWrap: 'wrap', marginBottom: 8 }}>
                  {fileTypes.map(f => (
                    <button key={f} onClick={() => setFileTypeFilter(f)} style={{
                      padding: '2px 7px', fontSize: 10, borderRadius: 3, cursor: 'pointer',
                      border: `1px solid ${fileTypeFilter === f ? T.accent : T.borderColor}`,
                      background: fileTypeFilter === f ? T.accent : 'transparent',
                      color: fileTypeFilter === f ? '#fff' : T.textMuted,
                    }}>{f}</button>
                  ))}
                </div>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                  {filteredFiles.map(f => (
                    <div key={f.id} style={{
                      background: T.surface2, border: `1px solid ${T.borderColor}`,
                      borderRadius: 5, padding: '8px 10px',
                    }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 3 }}>
                        <span style={{ fontSize: 11, color: T.textPrimary, flex: 1, marginRight: 4, lineHeight: 1.3 }}>{f.name}</span>
                        <span style={{ fontSize: 9, color: T.accent, flexShrink: 0 }}>{f.fileType}</span>
                      </div>
                      <div style={{ fontSize: 10, color: T.textMuted }}>{f.batchName}</div>
                      <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 4 }}>
                        <span style={{ fontSize: 10, color: T.textMuted }}>{f.uploader} · {f.time?.split(' ')[0] || ''}</span>
                        <div style={{ display: 'flex', gap: 6 }}>
                          <button onClick={() => setPreviewFile(f)} style={{ fontSize: 10, color: T.accent, background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}>预览</button>
                          <button onClick={() => handleDownload(f)} style={{ fontSize: 10, color: T.textMuted, background: 'none', border: 'none', cursor: 'pointer', padding: 0 }}>下载</button>
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
                <label style={{
                  border: `2px dashed ${T.borderColor}`, borderRadius: 6, padding: '12px',
                  textAlign: 'center', cursor: 'pointer', display: 'block', marginTop: 6,
                }}>
                  <input
                    type="file"
                    multiple
                    accept=".pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.jpg,.jpeg,.png"
                    style={{ display: 'none' }}
                    onChange={(e) => {
                      const files = Array.from(e.target.files);
                      if (files.length > 0) {
                        setSelectedFile(files[0]);
                        setUploadForm({ name: files[0].name, type: '培训资料', businessRef: '', note: '' });
                        setShowUploadModal(true);
                      }
                    }}
                  />
                  <div style={{ fontSize: 11, color: T.textMuted }}>点击或拖拽上传</div>
                  <div style={{ fontSize: 10, color: T.textMuted, marginTop: 2 }}>支持 PDF / Word / JPG / PNG</div>
                </label>
              </div>
            </div>
          </div>

          {/* 最近动态 mini */}
          <div style={{
            background: T.cardBg, border: `1px solid ${T.borderColor}`,
            borderRadius: T.radius, padding: '10px 12px', flexShrink: 0,
          }}>
            <div style={{ fontSize: 11, color: T.accent, fontWeight: 600, marginBottom: 6 }}>最近动态</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
              {trainings.slice(-2).reverse().map(t => (
                <div key={`t-${t.id}`} style={{ fontSize: 10, color: T.textMuted, lineHeight: 1.4 }}>
                  <span style={{ color: T.accent }}>·</span> {t.time?.split(' ')[0] || ''} 新建《{t.name}》
                </div>
              ))}
              {files.slice(-2).map(f => (
                <div key={`f-${f.id}`} style={{ fontSize: 10, color: T.textMuted, lineHeight: 1.4 }}>
                  <span style={{ color: T.success }}>·</span> {f.time?.split(' ')[0] || ''} 上传《{f.name}》
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* 新增人员弹窗 */}
      {showAddModal && (
        <PersonFormModal
          title="新增临时人员" form={addForm} setForm={setAddForm}
          onCancel={() => { setAddForm({ name: '', gender: '男', idcard: '', phone: '', unit: '', role: '普工', note: '' }); setShowAddModal(false); }}
          onConfirm={handleAddPerson} confirmLabel="确认添加" theme={T}
        />
      )}

      {/* 编辑人员弹窗 */}
      {editingPerson && (
        <PersonFormModal
          title="编辑人员" form={editingPerson} setForm={setEditingPerson}
          onCancel={() => setEditingPerson(null)} onConfirm={handleEditPerson}
          confirmLabel="保存" theme={T}
        />
      )}

      {/* 人员详情弹窗 */}
      {selectedPerson && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.7)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000,
        }} onClick={() => setSelectedPerson(null)}>
          <div style={{
            background: T.modalBg, border: `1px solid ${T.borderColor}`,
            borderRadius: 12, padding: 24, width: 420,
          }} onClick={e => e.stopPropagation()}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 20 }}>
              <span style={{ fontSize: 15, fontWeight: 600, color: T.textPrimary }}>人员详情</span>
              <StatusBadge status={selectedPerson.status} theme={T} />
            </div>
            {[
              ['姓名', selectedPerson.name], ['性别', selectedPerson.gender],
              ['身份证号', selectedPerson.idcard], ['手机号', selectedPerson.phone],
              ['所属单位', selectedPerson.unit], ['工种', selectedPerson.role],
              ['入场时间', selectedPerson.entryTime], ['备注', selectedPerson.note || '—'],
            ].map(([k, v]) => (
              <div key={k} style={{ display: 'flex', padding: '7px 0', borderBottom: `1px solid ${T.borderColor}` }}>
                <span style={{ width: 80, fontSize: 12, color: T.textMuted }}>{k}</span>
                <span style={{ fontSize: 12, color: T.textPrimary, flex: 1 }}>{v}</span>
              </div>
            ))}
            <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 20 }}>
              <button onClick={() => setSelectedPerson(null)} style={{
                padding: '8px 20px', borderRadius: 6, border: 'none',
                background: T.accent, color: '#fff', cursor: 'pointer', fontSize: 13,
              }}>关闭</button>
            </div>
          </div>
        </div>
      )}

      {/* 新建培训批次弹窗 */}
      {showTrainingModal && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.7)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000,
        }} onClick={() => setShowTrainingModal(false)}>
          <div style={{
            background: T.modalBg, border: `1px solid ${T.borderColor}`,
            borderRadius: 12, padding: 24, width: 560, maxWidth: '92vw', maxHeight: '88vh', overflow: 'auto',
          }} onClick={e => e.stopPropagation()}>
            <div style={{ fontSize: 15, fontWeight: 600, color: T.textPrimary, marginBottom: 20 }}>新建培训批次</div>
            <div style={{ marginBottom: 14 }}>
              <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>批次名称 *</div>
              <input placeholder="例如：2026年4月第三批安全三级教育"
                value={trainingForm.name}
                onChange={e => setTrainingForm({ ...trainingForm, name: e.target.value })}
                style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`,
                  borderRadius: 6, padding: '8px 10px', fontSize: 13, color: T.textPrimary, outline: 'none',
                }}/>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14, marginBottom: 14 }}>
              <div>
                <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>教育类型</div>
                <select
                  value={trainingForm.eduType}
                  onChange={e => setTrainingForm({ ...trainingForm, eduType: e.target.value })}
                  style={{
                    width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`,
                    borderRadius: 6, padding: '8px 10px', fontSize: 13, color: T.textPrimary, outline: 'none',
                  }}>
                  {['临时人员安全三级教育', '复工教育', '专项教育', '其他'].map(t => <option key={t}>{t}</option>)}
                </select>
              </div>
              <div>
                <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>培训时间</div>
                <div style={{ position: 'relative' }}>
                  <input type="datetime-local"
                    value={trainingForm.time}
                    onChange={e => setTrainingForm({ ...trainingForm, time: e.target.value })}
                    onFocus={() => setTrainingTimeFocused(true)}
                    onBlur={() => setTrainingTimeFocused(false)}
                    style={{
                      width: '100%', background: T.surface2, border: `1px solid ${(trainingForm.time || trainingTimeFocused) ? T.accent : T.borderColor}`,
                      borderRadius: 6, padding: '7px 34px 7px 10px', fontSize: 13, color: T.textPrimary, outline: 'none',
                      colorScheme: 'dark',
                    }}/>
                  <span style={{
                    position: 'absolute', right: 10, top: '50%', transform: 'translateY(-50%)',
                    display: 'flex', pointerEvents: 'none',
                  }}>{renderCalendarIcon(trainingForm.time || trainingTimeFocused)}</span>
                </div>
              </div>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14, marginBottom: 14 }}>
              <div>
                <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>培训地点</div>
                <input placeholder="项目部会议室"
                  value={trainingForm.place}
                  onChange={e => setTrainingForm({ ...trainingForm, place: e.target.value })}
                  style={{
                    width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`,
                    borderRadius: 6, padding: '8px 10px', fontSize: 13, color: T.textPrimary, outline: 'none',
                  }}/>
              </div>
              <div>
                <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>培训讲师</div>
                <input placeholder="姓名"
                  value={trainingForm.trainer}
                  onChange={e => setTrainingForm({ ...trainingForm, trainer: e.target.value })}
                  style={{
                    width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`,
                    borderRadius: 6, padding: '8px 10px', fontSize: 13, color: T.textPrimary, outline: 'none',
                  }}/>
              </div>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 14, marginBottom: 14 }}>
              <div>
                <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>课程学时</div>
                <input type="number" placeholder="如：8"
                  value={trainingForm.courseHours}
                  onChange={e => setTrainingForm({ ...trainingForm, courseHours: e.target.value })}
                  style={{
                    width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`,
                    borderRadius: 6, padding: '8px 10px', fontSize: 13, color: T.textPrimary, outline: 'none',
                  }}/>
              </div>
              <div>
                <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>考试形式</div>
                <select
                  value={trainingForm.examType}
                  onChange={e => setTrainingForm({ ...trainingForm, examType: e.target.value })}
                  style={{
                    width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`,
                    borderRadius: 6, padding: '8px 10px', fontSize: 13, color: T.textPrimary, outline: 'none',
                  }}>
                  <option value="">请选择</option>
                  <option value="笔试">笔试</option>
                  <option value="口试">口试</option>
                  <option value="实操">实操</option>
                  <option value="开卷">开卷</option>
                  <option value="考查">考查</option>
                </select>
              </div>
              <div>
                <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>培训课件</div>
                <input placeholder="课件名称"
                  value={trainingForm.trainingMaterial}
                  onChange={e => setTrainingForm({ ...trainingForm, trainingMaterial: e.target.value })}
                  style={{
                    width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`,
                    borderRadius: 6, padding: '8px 10px', fontSize: 13, color: T.textPrimary, outline: 'none',
                  }}/>
              </div>
            </div>
            {/* 培训资料上传 */}
            <div style={{ marginBottom: 14 }}>
              <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>培训附件</div>
              <div style={{
                background: T.surface2, border: `1px solid ${T.borderColor}`,
                borderRadius: 6, padding: 10,
              }}>
                <input
                  type="file"
                  multiple
                  accept=".pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.jpg,.jpeg,.png"
                  onChange={handleTrainingFileChange}
                  style={{ fontSize: 12, color: T.textPrimary, marginBottom: 8 }}
                />
                {trainingFiles.length > 0 && (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 4, marginTop: 8 }}>
                    {trainingFiles.map((file, idx) => (
                      <div key={idx} style={{
                        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                        padding: '4px 8px', background: T.cardBg, borderRadius: 4,
                        fontSize: 11, color: T.textSecondary,
                      }}>
                        <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{file.name}</span>
                        <button onClick={() => handleRemoveTrainingFile(idx)} style={{
                          background: 'none', border: 'none', color: T.danger,
                          cursor: 'pointer', fontSize: 12, padding: '0 4px',
                        }}>×</button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
            <div style={{ marginBottom: 14 }}>
              <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>
                关联人员 * <span style={{ color: T.textMuted }}>（共 {eligiblePersonnel.length} 人待教育，已选 {trainingForm.personIds.length}）</span>
              </div>
              {eligiblePersonnel.length === 0 ? (
                <div style={{
                  background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 6,
                  padding: 14, textAlign: 'center', color: T.textMuted, fontSize: 12,
                }}>暂无待教育人员</div>
              ) : (
                <div style={{
                  background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 6,
                  padding: 8, maxHeight: 160, overflow: 'auto',
                  display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 4,
                }}>
                  {eligiblePersonnel.map(p => {
                    const selected = trainingForm.personIds.includes(p.id);
                    return (
                      <label key={p.id} style={{
                        display: 'flex', alignItems: 'center', gap: 6,
                        padding: '5px 8px', borderRadius: 4, cursor: 'pointer',
                        background: selected ? T.activeItemBg : 'transparent',
                      }}>
                        <input type="checkbox" checked={selected} onChange={() => {
                          setTrainingForm({
                            ...trainingForm,
                            personIds: selected
                              ? trainingForm.personIds.filter(x => x !== p.id)
                              : [...trainingForm.personIds, p.id],
                          });
                        }} />
                        <span style={{ fontSize: 12, color: T.textPrimary, flex: 1 }}>{p.name}</span>
                        <span style={{ fontSize: 10, color: T.textMuted }}>{p.role}</span>
                      </label>
                    );
                  })}
                </div>
              )}
              {eligiblePersonnel.length > 0 && (
                <div style={{ display: 'flex', gap: 8, marginTop: 5 }}>
                  <button onClick={() => setTrainingForm({ ...trainingForm, personIds: eligiblePersonnel.map(p => p.id) })} style={{
                    fontSize: 10, padding: '3px 8px', borderRadius: 3, cursor: 'pointer',
                    background: 'transparent', border: `1px solid ${T.borderColor}`, color: T.textSecondary,
                  }}>全选</button>
                  <button onClick={() => setTrainingForm({ ...trainingForm, personIds: [] })} style={{
                    fontSize: 10, padding: '3px 8px', borderRadius: 3, cursor: 'pointer',
                    background: 'transparent', border: `1px solid ${T.borderColor}`, color: T.textSecondary,
                  }}>清空</button>
                </div>
              )}
            </div>
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
              <button onClick={() => setShowTrainingModal(false)} style={{
                padding: '8px 20px', borderRadius: 6, border: `1px solid ${T.borderColor}`,
                background: 'none', color: T.textSecondary, cursor: 'pointer', fontSize: 13,
              }}>取消</button>
              <button onClick={handleCreateTraining} style={{
                padding: '8px 20px', borderRadius: 6, border: 'none',
                background: T.accent, color: '#fff', cursor: 'pointer', fontSize: 13,
              }}>创建批次</button>
            </div>
          </div>
        </div>
      )}

      {/* 培训记录查看弹窗 */}
      {trainingRecordView && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.7)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000,
        }} onClick={() => setTrainingRecordView(null)}>
          <div style={{
            background: T.modalBg, border: `1px solid ${T.borderColor}`,
            borderRadius: 12, padding: 24, width: 520, maxWidth: '92vw', maxHeight: '86vh', overflow: 'auto',
          }} onClick={e => e.stopPropagation()}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 18 }}>
              <span style={{ fontSize: 15, fontWeight: 600, color: T.textPrimary }}>培训记录</span>
              <StatusBadge status={trainingRecordView.status} theme={T} />
            </div>
            <div style={{ fontSize: 13, color: T.textPrimary, fontWeight: 600, marginBottom: 10 }}>{trainingRecordView.name}</div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px 16px', fontSize: 12, color: T.textSecondary, marginBottom: 14 }}>
              <div><span style={{ color: T.textMuted }}>类型：</span>{trainingRecordView.eduType || trainingRecordView.type}</div>
              <div><span style={{ color: T.textMuted }}>时间：</span>{formatTrainingTime(trainingRecordView.time)}</div>
              <div><span style={{ color: T.textMuted }}>地点：</span>{trainingRecordView.place}</div>
              <div><span style={{ color: T.textMuted }}>讲师：</span>{trainingRecordView.trainer}</div>
              <div><span style={{ color: T.textMuted }}>课程学时：</span>{trainingRecordView.courseHours || '-'}</div>
              <div><span style={{ color: T.textMuted }}>考试形式：</span>{trainingRecordView.examType || '-'}</div>
              <div style={{ gridColumn: '1 / -1' }}><span style={{ color: T.textMuted }}>培训课件：</span>{trainingRecordView.trainingMaterial || '-'}</div>
            </div>
            <div style={{ fontSize: 12, color: T.textMuted, marginBottom: 6 }}>关联人员（{trainingRecordView.personIds?.length || 0}）</div>
            <div style={{
              background: T.surface2, borderRadius: 6, padding: 10,
              display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 14,
            }}>
              {(trainingRecordView.personIds || []).map(id => {
                const p = personnel.find(x => x.id === id);
                if (!p) return null;
                return (
                  <span key={id} style={{
                    fontSize: 11, padding: '3px 10px', borderRadius: 12,
                    background: T.cardBg, border: `1px solid ${T.borderColor}`, color: T.textPrimary,
                  }}>{p.name} · {p.role}</span>
                );
              })}
            </div>
            {/* 培训资料 */}
            <div style={{ marginBottom: 14 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                <span style={{ fontSize: 12, color: T.textMuted }}>培训资料</span>
                <label style={{
                  fontSize: 11, color: T.accent, cursor: 'pointer',
                }}>
                  + 添加资料
                  <input
                    type="file"
                    multiple
                    accept=".pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.jpg,.jpeg,.png"
                    style={{ display: 'none' }}
                    onChange={async (e) => {
                      const rawFiles = Array.from(e.target.files);
                      // 先上传文件
                      let newFiles = [];
                      for (const file of rawFiles) {
                        try {
                          const uploaded = await uploadTrainingAttachments([file], trainingRecordView.id);
                          newFiles.push(...uploaded);
                        } catch (err) {
                          console.error('上传失败', err);
                        }
                      }
                      setTrainingRecordView(prev => ({
                        ...prev,
                        files: [...(prev.files || []), ...newFiles],
                      }));
                      setTrainings(prev => prev.map(t => t.id === trainingRecordView.id
                        ? { ...t, files: [...(t.files || []), ...newFiles] }
                        : t
                      ));
                      e.target.value = '';
                    }}
                  />
                </label>
              </div>
              {trainingRecordView.files?.length > 0 ? (
                <div style={{
                  background: T.surface2, borderRadius: 6, padding: 8,
                  display: 'flex', flexDirection: 'column', gap: 6,
                }}>
                  {trainingRecordView.files.map((file, idx) => (
                    <div key={idx} style={{
                      display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                      padding: '8px 10px', background: T.cardBg, borderRadius: 4,
                      fontSize: 12, color: T.textPrimary,
                    }}>
                      <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{file.name || file.fileName || `文件${idx + 1}`}</span>
                      <div style={{ display: 'flex', gap: 8, marginLeft: 8 }}>
                        <button onClick={() => {
                          const fileId = file.id || (trainingRecordView.uploadedFileIds && trainingRecordView.uploadedFileIds[idx]);
                          if (fileId) {
                            previewTrainingFile({ id: fileId, name: file.name || file.fileName }).catch(err => {
                              console.error('文件查看失败', err);
                              alert('文件查看失败，请重试');
                            });
                          } else {
                            alert('文件未保存');
                          }
                        }} style={{ background: 'none', border: 'none', color: T.accent, cursor: 'pointer', fontSize: 11 }}>查看</button>
                        <button onClick={() => {
                          const fileId = file.id || (trainingRecordView.uploadedFileIds && trainingRecordView.uploadedFileIds[idx]);
                          if (fileId) {
                            downloadTrainingFile({ id: fileId, name: file.name || file.fileName }).catch(err => {
                              console.error('文件下载失败', err);
                              alert('文件下载失败，请重试');
                            });
                          } else {
                            alert('文件未保存');
                          }
                        }} style={{ background: 'none', border: 'none', color: T.textSecondary, cursor: 'pointer', fontSize: 11 }}>下载</button>
                        <button onClick={() => {
                          const fileId = file.id;
                          if (fileId) {
                            deleteFile(fileId).catch(console.error);
                          }
                          setTrainingRecordView(prev => ({
                            ...prev,
                            files: prev.files.filter((_, i) => i !== idx),
                          }));
                          setTrainings(prev => prev.map(t => t.id === trainingRecordView.id
                            ? { ...t, files: (t.files || []).filter((_, i) => i !== idx) }
                            : t
                          ));
                        }} style={{ background: 'none', border: 'none', color: T.danger, cursor: 'pointer', fontSize: 11 }}>删除</button>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div style={{
                  background: T.surface2, borderRadius: 6, padding: 20,
                  textAlign: 'center', color: T.textMuted, fontSize: 12,
                }}>
                  暂无培训资料
                </div>
              )}
            </div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 16 }}>
              <button onClick={() => { openEditTraining(trainingRecordView); setTrainingRecordView(null); }} style={{
                padding: '8px 20px', borderRadius: 6, border: `1px solid ${T.accent}`,
                background: 'none', color: T.accent, cursor: 'pointer', fontSize: 13,
              }}>编辑</button>
              <button onClick={() => setTrainingRecordView(null)} style={{
                padding: '8px 20px', borderRadius: 6, border: 'none',
                background: T.accent, color: '#fff', cursor: 'pointer', fontSize: 13,
              }}>关闭</button>
            </div>
          </div>
        </div>
      )}

      {/* 编辑培训弹窗 */}
      {showEditTrainingModal && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.7)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1001,
        }} onClick={() => setShowEditTrainingModal(false)}>
          <div style={{
            background: T.modalBg, border: `1px solid ${T.borderColor}`,
            borderRadius: 12, padding: 24, width: 560, maxWidth: '92vw', maxHeight: '88vh', overflow: 'auto',
          }} onClick={e => e.stopPropagation()}>
            <div style={{ fontSize: 15, fontWeight: 600, color: T.textPrimary, marginBottom: 20 }}>编辑培训批次</div>
            <div style={{ marginBottom: 14 }}>
              <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>批次名称 *</div>
              <input placeholder="例如：2026年4月第三批安全三级教育"
                value={editTrainingForm.name || ''}
                onChange={e => setEditTrainingForm({ ...editTrainingForm, name: e.target.value })}
                style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`,
                  borderRadius: 6, padding: '8px 10px', fontSize: 13, color: T.textPrimary, outline: 'none',
                }}/>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14, marginBottom: 14 }}>
              <div>
                <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>教育类型</div>
                <select
                  value={editTrainingForm.eduType || editTrainingForm.type || '临时人员安全三级教育'}
                  onChange={e => setEditTrainingForm({ ...editTrainingForm, eduType: e.target.value })}
                  style={{
                    width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`,
                    borderRadius: 6, padding: '8px 10px', fontSize: 13, color: T.textPrimary, outline: 'none',
                  }}>
                  {['临时人员安全三级教育', '复工教育', '专项教育', '其他'].map(t => <option key={t}>{t}</option>)}
                </select>
              </div>
              <div>
                <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>培训时间</div>
                <div style={{ position: 'relative' }}>
                  <input type="datetime-local"
                    value={toTrainingInputValue(editTrainingForm.time)}
                    onChange={e => setEditTrainingForm({ ...editTrainingForm, time: e.target.value })}
                    onFocus={() => setEditTrainingTimeFocused(true)}
                    onBlur={() => setEditTrainingTimeFocused(false)}
                    style={{
                      width: '100%', background: T.surface2, border: `1px solid ${(editTrainingForm.time || editTrainingTimeFocused) ? T.accent : T.borderColor}`,
                      borderRadius: 6, padding: '7px 34px 7px 10px', fontSize: 13, color: T.textPrimary, outline: 'none',
                      colorScheme: 'dark',
                    }}/>
                  <span style={{
                    position: 'absolute', right: 10, top: '50%', transform: 'translateY(-50%)',
                    display: 'flex', pointerEvents: 'none',
                  }}>{renderCalendarIcon(editTrainingForm.time || editTrainingTimeFocused)}</span>
                </div>
              </div>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 14, marginBottom: 14 }}>
              <div>
                <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>培训地点</div>
                <input placeholder="项目部会议室"
                  value={editTrainingForm.place || ''}
                  onChange={e => setEditTrainingForm({ ...editTrainingForm, place: e.target.value })}
                  style={{
                    width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`,
                    borderRadius: 6, padding: '8px 10px', fontSize: 13, color: T.textPrimary, outline: 'none',
                  }}/>
              </div>
              <div>
                <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>培训讲师</div>
                <input placeholder="姓名"
                  value={editTrainingForm.trainer || ''}
                  onChange={e => setEditTrainingForm({ ...editTrainingForm, trainer: e.target.value })}
                  style={{
                    width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`,
                    borderRadius: 6, padding: '8px 10px', fontSize: 13, color: T.textPrimary, outline: 'none',
                  }}/>
              </div>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 14, marginBottom: 14 }}>
              <div>
                <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>课程学时</div>
                <input type="number" placeholder="如：8"
                  value={editTrainingForm.courseHours || ''}
                  onChange={e => setEditTrainingForm({ ...editTrainingForm, courseHours: e.target.value })}
                  style={{
                    width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`,
                    borderRadius: 6, padding: '8px 10px', fontSize: 13, color: T.textPrimary, outline: 'none',
                  }}/>
              </div>
              <div>
                <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>考试形式</div>
                <select
                  value={editTrainingForm.examType || ''}
                  onChange={e => setEditTrainingForm({ ...editTrainingForm, examType: e.target.value })}
                  style={{
                    width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`,
                    borderRadius: 6, padding: '8px 10px', fontSize: 13, color: T.textPrimary, outline: 'none',
                  }}>
                  <option value="">请选择</option>
                  <option value="笔试">笔试</option>
                  <option value="口试">口试</option>
                  <option value="实操">实操</option>
                  <option value="开卷">开卷</option>
                  <option value="考查">考查</option>
                </select>
              </div>
              <div>
                <div style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5 }}>培训课件</div>
                <input placeholder="课件名称"
                  value={editTrainingForm.trainingMaterial || ''}
                  onChange={e => setEditTrainingForm({ ...editTrainingForm, trainingMaterial: e.target.value })}
                  style={{
                    width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`,
                    borderRadius: 6, padding: '8px 10px', fontSize: 13, color: T.textPrimary, outline: 'none',
                  }}/>
              </div>
            </div>
            <div style={{ marginBottom: 14 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                <span style={{ fontSize: 12, color: T.textSecondary }}>培训附件</span>
                <label style={{ fontSize: 11, color: T.accent, cursor: 'pointer' }}>
                  + 上传附件
                  <input
                    type="file"
                    multiple
                    accept=".pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.jpg,.jpeg,.png"
                    style={{ display: 'none' }}
                    onChange={async (e) => {
                      const rawFiles = Array.from(e.target.files || []);
                      if (rawFiles.length === 0) return;
                      try {
                        const newFiles = await uploadTrainingAttachments(rawFiles, editTrainingForm.id);
                        setEditTrainingForm(prev => ({
                          ...prev,
                          files: [...(prev.files || []), ...newFiles],
                        }));
                        setTrainings(prev => prev.map(t => t.id === editTrainingForm.id
                          ? { ...t, files: [...(t.files || []), ...newFiles] }
                          : t
                        ));
                        e.target.value = '';
                      } catch (err) {
                        console.error('上传培训附件失败', err);
                        alert('上传培训附件失败，请重试');
                      }
                    }}
                  />
                </label>
              </div>
              {(editTrainingForm.files || []).length > 0 ? (
                <div style={{
                  background: T.surface2, border: `1px solid ${T.borderColor}`,
                  borderRadius: 6, padding: 8, display: 'flex', flexDirection: 'column', gap: 6,
                }}>
                  {(editTrainingForm.files || []).map((file, idx) => {
                    const fileId = getFileId(file, idx, editTrainingForm);
                    return (
                      <div key={`${fileId || 'local'}-${idx}`} style={{
                        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                        padding: '8px 10px', background: T.cardBg, borderRadius: 4,
                        fontSize: 12, color: T.textPrimary,
                      }}>
                        <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                          {file.name || file.fileName || `文件${idx + 1}`}
                        </span>
                        <div style={{ display: 'flex', gap: 8, marginLeft: 8 }}>
                          <button onClick={() => {
                            previewTrainingFile({ id: fileId, name: file.name || file.fileName }).catch(err => {
                              console.error('文件查看失败', err);
                              alert('文件查看失败，请重试');
                            });
                          }} style={{ background: 'none', border: 'none', color: T.accent, cursor: 'pointer', fontSize: 11 }}>查看</button>
                          <button onClick={() => {
                            downloadTrainingFile({ id: fileId, name: file.name || file.fileName }).catch(err => {
                              console.error('文件下载失败', err);
                              alert('文件下载失败，请重试');
                            });
                          }} style={{ background: 'none', border: 'none', color: T.textSecondary, cursor: 'pointer', fontSize: 11 }}>下载</button>
                          <button onClick={() => {
                            if (fileId) {
                              deleteFile(fileId).catch(console.error);
                            }
                            setEditTrainingForm(prev => ({
                              ...prev,
                              files: (prev.files || []).filter((_, i) => i !== idx),
                            }));
                            setTrainings(prev => prev.map(t => t.id === editTrainingForm.id
                              ? { ...t, files: (t.files || []).filter((_, i) => i !== idx) }
                              : t
                            ));
                          }} style={{ background: 'none', border: 'none', color: T.danger, cursor: 'pointer', fontSize: 11 }}>删除</button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              ) : (
                <div style={{
                  background: T.surface2, border: `1px solid ${T.borderColor}`,
                  borderRadius: 6, padding: 14, textAlign: 'center',
                  color: T.textMuted, fontSize: 12,
                }}>暂无培训附件</div>
              )}
            </div>
            <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end' }}>
              <button onClick={() => setShowEditTrainingModal(false)} style={{
                padding: '8px 20px', borderRadius: 6, border: `1px solid ${T.borderColor}`,
                background: 'none', color: T.textSecondary, cursor: 'pointer', fontSize: 13,
              }}>取消</button>
              <button onClick={() => {
                const payload = buildTrainingPayload(editTrainingForm);
                updateTraining(editTrainingForm.id, payload).then(() => {
                  setTrainings(prev => prev.map(t => t.id === editTrainingForm.id ? {
                    ...t,
                    ...editTrainingForm,
                    eduType: payload.eduType,
                    time: payload.time,
                    place: payload.place,
                    trainer: payload.trainer,
                    note: payload.remark,
                  } : t));
                  setShowEditTrainingModal(false);
                  setTrainingRecordView(null);
                }).catch(err => {
                  console.error('保存失败:', err);
                  alert('保存失败，请重试');
                });
              }} style={{
                padding: '8px 20px', borderRadius: 6, border: 'none',
                background: T.accent, color: '#fff', cursor: 'pointer', fontSize: 13,
              }}>保存</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// ============================================
// 页面：设备与监控
// ============================================
function MonitorPage({ projectId, theme: T, compactMode, cameraConfig, onEnterCameraPage }) {
  const mockData = DATA_BY_PROJECT[projectId];

  const [cameras, setCameras] = useState([]);
  const [devices, setDevices] = useState([]);
  const [loading, setLoading] = useState(false);
  const [videoLayout, setVideoLayout] = useState(cameraConfig?.videoLayout || 8);
  const [cameraAreaFilter, setCameraAreaFilter] = useState('全部');
  const [showCameraModal, setShowCameraModal] = useState(false);
  const [editingCameraId, setEditingCameraId] = useState(null);
  const [cameraForm, setCameraForm] = useState({
    cameraName: '',
    cameraCode: '',
    area: '',
    cameraType: '海康',
    rtspUrl: '',
    onlineStatus: 1,
  });

  const layoutMap = {
    1: { cols: 1, rows: 1, label: '单屏' },
    4: { cols: 2, rows: 2, label: '4宫格' },
    8: { cols: 4, rows: 2, label: '8窗口' },
    16: { cols: 4, rows: 4, label: '16窗口' },
  };

  const normalizeDeviceStatus = (status) => {
    const map = { running: '运行中', stopped: '停机', abnormal: '异常', maintenance: '维修中' };
    return map[status] || status || '未知';
  };

  const normalizeDeviceType = (type) => {
    const map = { tower_crane: '塔吊', elevator: '施工电梯', pump: '泵车' };
    return map[type] || type || '其他';
  };

  const mapCamera = (c) => ({
    id: c.id,
    name: c.name || c.cameraName,
    code: c.code || c.cameraCode,
    area: c.area || '未分区',
    type: c.type || c.cameraType || '海康',
    rtspUrl: c.rtspUrl || '',
    online: c.online !== undefined ? c.online : c.onlineStatus === 1,
  });

  const fetchMonitorData = async () => {
    setLoading(true);
    try {
      const [cameraRes, deviceRes] = await Promise.all([
        getCameraList(projectId),
        getDeviceList(projectId),
      ]);

      if (cameraRes.code === 200 && cameraRes.data) {
        setCameras(cameraRes.data.map(mapCamera));
      }

      if (deviceRes.code === 200 && deviceRes.data) {
        setDevices(deviceRes.data.map(d => ({
          id: d.id,
          name: d.deviceName || d.name,
          code: d.deviceCode || d.code,
          type: normalizeDeviceType(d.deviceType || d.type),
          status: normalizeDeviceStatus(d.status),
          height: d.height,
          maxLoad: d.maxLoad,
          lastReport: d.lastReport,
          note: d.remark || d.note,
        })));
      }

    } catch (e) {
      setCameras((mockData?.cameras || []).map(mapCamera));
      setDevices((mockData?.devices || []).map(d => ({
        ...d,
        type: normalizeDeviceType(d.type),
        status: normalizeDeviceStatus(d.status),
      })));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchMonitorData();
  }, [projectId]);

  useEffect(() => {
    setVideoLayout(cameraConfig?.videoLayout || 8);
  }, [cameraConfig?.videoLayout]);

  const onlineCameras = cameras.filter(c => c.online).length;
  const offlineCameras = cameras.length - onlineCameras;
  const runningDevices = devices.filter(d => d.status === '运行中').length;
  const stoppedDevices = devices.filter(d => d.status === '停机').length;
  const abnormalDevices = devices.filter(d => d.status === '异常').length;
  const towerCraneCount = devices.filter(d => d.type === '塔吊').length;
  const areas = ['全部', ...Array.from(new Set(cameras.map(c => c.area).filter(Boolean)))];
  const filteredCameras = cameraAreaFilter === '全部'
    ? cameras
    : cameras.filter(c => c.area === cameraAreaFilter);
  const cameraAssignments = cameraConfig?.cameraAssignments || [];
  const assignedCameras = cameraAssignments.slice(0, videoLayout).map(id => cameras.find(c => c.id === id) || null);
  const hasWindowConfig = assignedCameras.some(Boolean);
  const displayCameras = hasWindowConfig ? assignedCameras : filteredCameras.slice(0, videoLayout);
  const activeLayout = layoutMap[videoLayout] || layoutMap[8];

  const getDeviceStatusColor = (status) => {
    if (status === '运行中') return T.success;
    if (status === '停机' || status === '维修中') return T.warning;
    if (status === '异常') return T.danger;
    return T.textMuted;
  };

  const openAddCamera = () => {
    setEditingCameraId(null);
    setCameraForm({ cameraName: '', cameraCode: '', area: '', cameraType: '海康', rtspUrl: '', onlineStatus: 1 });
    setShowCameraModal(true);
  };

  const openEditCamera = (camera) => {
    setEditingCameraId(camera.id);
    setCameraForm({
      cameraName: camera.name || '',
      cameraCode: camera.code || '',
      area: camera.area || '',
      cameraType: camera.type || '海康',
      rtspUrl: camera.rtspUrl || '',
      onlineStatus: camera.online ? 1 : 0,
    });
    setShowCameraModal(true);
  };

  const handleSaveCamera = async () => {
    if (!cameraForm.cameraName.trim()) {
      alert('请填写摄像头名称');
      return;
    }
    const payload = { ...cameraForm, projectId };
    try {
      if (editingCameraId) {
        const res = await updateCamera(editingCameraId, { ...payload, id: editingCameraId });
        if (res.code !== 200) {
          alert(res.message || '保存失败');
          return;
        }
      } else {
        const res = await createCamera(payload);
        if (res.code !== 200) {
          alert(res.message || '保存失败');
          return;
        }
      }
      setShowCameraModal(false);
      await fetchMonitorData();
    } catch (err) {
      console.error('保存摄像头失败', err);
      alert('保存失败，请重试');
    }
  };

  const handleDeleteCamera = async (camera) => {
    if (!window.confirm(`确认删除摄像头「${camera.name}」？`)) return;
    try {
      const res = await deleteCamera(camera.id);
      if (res.code === 200) {
        setCameras(prev => prev.filter(c => c.id !== camera.id));
      } else {
        alert(res.message || '删除失败');
      }
    } catch (err) {
      console.error('删除摄像头失败', err);
      alert('删除失败，请重试');
    }
  };

  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: compactMode ? '220px 1fr 260px' : '220px 1fr 260px',
      gap: 12,
      padding: 16,
      height: '100%',
      overflow: 'hidden',
    }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10, minHeight: 0 }}>
        <div style={{ background: T.cardBg, border: `1px solid ${T.borderColor}`, borderRadius: 6, padding: 14 }}>
          <div style={{ fontSize: 12, fontWeight: 700, color: T.textPrimary, marginBottom: 12 }}>摄像头概览</div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 8 }}>
            {[
              ['在线', onlineCameras, T.success],
              ['离线', offlineCameras, T.danger],
              ['总计', cameras.length, T.accent],
            ].map(([label, value, color]) => (
              <div key={label} style={{ background: T.surface2, borderRadius: 5, padding: '10px 6px', textAlign: 'center' }}>
                <div style={{ fontSize: 22, fontWeight: 800, color, lineHeight: 1 }}>{value}</div>
                <div style={{ fontSize: 10, color: T.textMuted, marginTop: 4 }}>{label}</div>
              </div>
            ))}
          </div>
        </div>

        <div style={{ background: T.cardBg, border: `1px solid ${T.borderColor}`, borderRadius: 6, padding: 14 }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
            <span style={{ fontSize: 12, fontWeight: 700, color: T.textPrimary }}>设备状态</span>
            <span style={{ fontSize: 10, color: T.accent, background: T.activeItemBg, padding: '2px 7px', borderRadius: 4 }}>塔吊 {towerCraneCount}</span>
          </div>
          {[
            ['设备总数', devices.length, T.accent],
            ['运行中', runningDevices, T.success],
            ['停机', stoppedDevices, T.warning],
            ['异常', abnormalDevices, T.danger],
          ].map(([label, value, color]) => (
            <div key={label} style={{
              display: 'flex', justifyContent: 'space-between', alignItems: 'center',
              padding: '7px 6px', borderBottom: `1px solid ${T.borderColor}`,
              background: label === '设备总数' ? T.activeItemBg : 'transparent',
            }}>
              <span style={{ fontSize: 11, color: label === '设备总数' ? T.accent : T.textMuted }}>{label}</span>
              <span style={{ fontSize: 12, color, fontWeight: 700 }}>{value}</span>
            </div>
          ))}
        </div>

        <div style={{ background: T.cardBg, border: `1px solid ${T.borderColor}`, borderRadius: 6, padding: 14, flex: 1, minHeight: 0, overflow: 'hidden' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
            <span style={{ fontSize: 12, fontWeight: 700, color: T.textPrimary }}>摄像头列表</span>
            <div style={{ display: 'flex', gap: 6 }}>
              <button onClick={onEnterCameraPage} style={{ padding: '3px 9px', fontSize: 10, borderRadius: 4, cursor: 'pointer', background: T.surface2, border: `1px solid ${T.accent}`, color: T.accent }}>窗口配置</button>
              <button onClick={openAddCamera} style={{ padding: '3px 9px', fontSize: 10, borderRadius: 4, cursor: 'pointer', background: T.accent, border: 'none', color: '#fff' }}>新增</button>
            </div>
          </div>
          <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', marginBottom: 10 }}>
            {areas.map(area => (
              <button key={area} onClick={() => setCameraAreaFilter(area)} style={{
                padding: '2px 6px', fontSize: 10, borderRadius: 3, cursor: 'pointer',
                border: `1px solid ${cameraAreaFilter === area ? T.accent : T.borderColor}`,
                background: cameraAreaFilter === area ? T.accent : T.surface2,
                color: cameraAreaFilter === area ? '#fff' : T.textMuted,
              }}>{area}</button>
            ))}
          </div>
          {hasWindowConfig && (
            <div style={{
              background: T.activeItemBg,
              border: `1px solid ${T.accent}`,
              borderRadius: 5,
              padding: '7px 8px',
              marginBottom: 10,
              fontSize: 10,
              color: T.accent,
            }}>
              已应用镜头管理页的 {layoutMap[videoLayout]?.label || `${videoLayout}窗口`} 配置
            </div>
          )}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6, overflow: 'auto', height: hasWindowConfig ? 'calc(100% - 100px)' : 'calc(100% - 64px)', paddingRight: 2 }}>
            {loading && <div style={{ color: T.textMuted, fontSize: 12, textAlign: 'center', padding: 20 }}>加载中...</div>}
            {!loading && filteredCameras.map(cam => (
              <div key={cam.id} style={{
                padding: '7px 8px', borderRadius: 4, background: T.surface2,
                border: `1px solid ${T.borderColor}`,
              }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, minWidth: 0 }}>
                    <span style={{ width: 6, height: 6, borderRadius: '50%', background: cam.online ? T.success : T.danger, flexShrink: 0 }} />
                    <span style={{ fontSize: 11, color: T.textPrimary, fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{cam.name}</span>
                  </div>
                  <span style={{ fontSize: 9, color: T.textMuted, flexShrink: 0 }}>{cam.area}</span>
                </div>
                <div style={{ display: 'flex', gap: 8, marginTop: 5 }}>
                  <button onClick={() => openEditCamera(cam)} style={{ fontSize: 10, color: T.accent, background: 'none', border: 'none', padding: 0, cursor: 'pointer' }}>编辑</button>
                  <button onClick={() => handleDeleteCamera(cam)} style={{ fontSize: 10, color: T.danger, background: 'none', border: 'none', padding: 0, cursor: 'pointer' }}>删除</button>
                </div>
              </div>
            ))}
            {!loading && filteredCameras.length === 0 && (
              <div style={{ color: T.textMuted, fontSize: 12, textAlign: 'center', padding: 20 }}>暂无摄像头</div>
            )}
          </div>
        </div>
      </div>

      <div style={{ background: T.cardBg, border: `1px solid ${T.borderColor}`, borderRadius: 6, padding: 12, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
          <div>
            <div style={{ fontSize: 13, fontWeight: 700, color: T.textPrimary }}>实时监控</div>
            <div style={{ fontSize: 11, color: T.textMuted, marginTop: 3 }}>
              在线：{onlineCameras}/{cameras.length}{hasWindowConfig ? ' · 已按窗口配置显示' : ''}
            </div>
          </div>
          <div style={{ display: 'flex', gap: 5 }}>
            {[1, 4, 8, 16].map(n => (
              <button key={n} onClick={() => setVideoLayout(n)} style={{
                padding: '4px 10px', borderRadius: 4, fontSize: 11, cursor: 'pointer',
                border: `1px solid ${videoLayout === n ? T.accent : T.borderColor}`,
                background: videoLayout === n ? T.accent : T.surface2,
                color: videoLayout === n ? '#fff' : T.textMuted,
              }}>{layoutMap[n].label}</button>
            ))}
            <button onClick={onEnterCameraPage} style={{ padding: '4px 10px', borderRadius: 4, fontSize: 11, cursor: 'pointer', border: `1px solid ${T.accent}`, background: T.surface2, color: T.accent }}>窗口配置</button>
            <button onClick={fetchMonitorData} style={{ padding: '4px 10px', borderRadius: 4, fontSize: 11, cursor: 'pointer', border: `1px solid ${T.borderColor}`, background: T.surface2, color: T.textMuted }}>刷新</button>
          </div>
        </div>
        <div style={{
          flex: 1,
          minHeight: 0,
          display: 'grid',
          gridTemplateColumns: `repeat(${activeLayout.cols}, minmax(0, 1fr))`,
          gridTemplateRows: `repeat(${activeLayout.rows}, minmax(0, 1fr))`,
          gap: 8,
        }}>
          {Array.from({ length: videoLayout }).map((_, idx) => {
            const cam = displayCameras[idx];
            return cam ? (
              <VideoCell key={`${cam.id}-${idx}`} cam={{ ...cam, name: `${cam.name} · #${idx + 1}` }} theme={T} />
            ) : (
              <div key={`empty-${idx}`} style={{ background: '#07111e', border: `1px solid ${T.borderColor}`, borderRadius: 6, display: 'flex', alignItems: 'center', justifyContent: 'center', color: T.textMuted, fontSize: 11 }}>
                窗口 #{idx + 1} 未分配
              </div>
            );
          })}
        </div>
      </div>

      <div style={{ background: T.cardBg, border: `1px solid ${T.borderColor}`, borderRadius: 6, padding: 14, minHeight: 0, overflow: 'auto' }}>
        <div style={{ fontSize: 12, fontWeight: 700, color: T.textPrimary, marginBottom: 12 }}>设备列表</div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 9 }}>
          {devices.map(device => {
            const color = getDeviceStatusColor(device.status);
            return (
              <div key={device.id} style={{ padding: 12, borderRadius: 7, border: `1px solid ${T.borderColor}`, background: T.surface2 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                  <span style={{ fontSize: 12, color: T.textPrimary, fontWeight: 700 }}>{device.name}</span>
                  <span style={{ fontSize: 10, color, border: `1px solid ${color}`, borderRadius: 4, padding: '2px 6px' }}>{device.status}</span>
                </div>
                <div style={{ fontSize: 10, color: T.textMuted, lineHeight: 1.7 }}>编号：{device.code} · {device.type}</div>
                <div style={{ fontSize: 10, color: T.textMuted, lineHeight: 1.7 }}>最近上报：{device.lastReport?.replace('T', ' ').slice(0, 16) || '-'}</div>
                {device.note && <div style={{ marginTop: 6, padding: '4px 6px', background: `${T.warning}22`, color: T.warning, fontSize: 10, borderRadius: 4 }}>{device.note}</div>}
              </div>
            );
          })}
        </div>
      </div>

      {showCameraModal && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.65)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1001,
        }} onClick={() => setShowCameraModal(false)}>
          <div style={{ background: T.modalBg, border: `1px solid ${T.borderColor}`, borderRadius: 10, padding: 24, width: 440 }} onClick={e => e.stopPropagation()}>
            <div style={{ fontSize: 15, fontWeight: 700, color: T.textPrimary, marginBottom: 16 }}>{editingCameraId ? '编辑摄像头' : '新增摄像头'}</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5, display: 'block' }}>摄像头名称 *</label>
                <input value={cameraForm.cameraName} onChange={e => setCameraForm({ ...cameraForm, cameraName: e.target.value })} placeholder="如：大门入口" style={{ width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5, padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none' }} />
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                <div>
                  <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5, display: 'block' }}>摄像头编号</label>
                  <input value={cameraForm.cameraCode} onChange={e => setCameraForm({ ...cameraForm, cameraCode: e.target.value })} placeholder="CAM-001" style={{ width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5, padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none' }} />
                </div>
                <div>
                  <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5, display: 'block' }}>所属区域</label>
                  <input value={cameraForm.area} onChange={e => setCameraForm({ ...cameraForm, area: e.target.value })} placeholder="出入口" style={{ width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5, padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none' }} />
                </div>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                <div>
                  <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5, display: 'block' }}>摄像头类型</label>
                  <input value={cameraForm.cameraType} onChange={e => setCameraForm({ ...cameraForm, cameraType: e.target.value })} placeholder="海康" style={{ width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5, padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none' }} />
                </div>
                <div>
                  <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5, display: 'block' }}>在线状态</label>
                  <select value={cameraForm.onlineStatus} onChange={e => setCameraForm({ ...cameraForm, onlineStatus: Number(e.target.value) })} style={{ width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5, padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none' }}>
                    <option value={1}>在线</option>
                    <option value={0}>离线</option>
                  </select>
                </div>
              </div>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 5, display: 'block' }}>RTSP地址</label>
                <input value={cameraForm.rtspUrl} onChange={e => setCameraForm({ ...cameraForm, rtspUrl: e.target.value })} placeholder="rtsp://user:password@ip:port/path" style={{ width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5, padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none' }} />
              </div>
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 20 }}>
              <button onClick={() => setShowCameraModal(false)} style={{ padding: '8px 18px', borderRadius: 5, cursor: 'pointer', background: 'transparent', border: `1px solid ${T.borderColor}`, color: T.textSecondary, fontSize: 12 }}>取消</button>
              <button onClick={handleSaveCamera} style={{ padding: '8px 18px', borderRadius: 5, cursor: 'pointer', background: T.accent, border: 'none', color: '#fff', fontSize: 12 }}>保存</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// ============================================
// 根组件 App
// ============================================
export default function App() {
  const [isAuth, setIsAuth] = useState(isLoggedIn());
  const [themeId, setThemeId] = useState(DEFAULT_THEME_ID);
  const [currentPage, setCurrentPage] = useState(PAGE_IDS.MAP_DASHBOARD);
  const [currentProject, setCurrentProject] = useState(1);
  const [compactMode, setCompactMode] = useState(false);
  const [projectList, setProjectList] = useState([]);
  const [showCameraPage, setShowCameraPage] = useState(false);
  const [cameraConfig, setCameraConfig] = useState({
    videoLayout: 4,
    cameraAssignments: [],
  });

  const theme = getThemeById(themeId);

  // 获取项目列表
  const fetchProjectList = useCallback(async () => {
    try {
      const res = await getProjectList();
      if (res.code === 200 && res.data) {
        setProjectList(res.data);
      }
    } catch (e) {
      console.error('获取项目列表失败', e);
    }
  }, []);

  useEffect(() => {
    if (isAuth) {
      fetchProjectList();
    }
  }, [isAuth, fetchProjectList]);

  const handleLogin = useCallback(() => {
    setIsAuth(true);
    setCurrentPage(PAGE_IDS.MAP_DASHBOARD);
  }, []);

  const handleLogout = useCallback(() => {
    localStorage.removeItem('site_platform_token');
    localStorage.removeItem('site_platform_user');
    setIsAuth(false);
  }, []);

  const handleNavigateFromMap = useCallback((pageId, projectId) => {
    if (projectId) setCurrentProject(projectId);
    setCurrentPage(pageId);
  }, []);

  const renderPage = () => {
    const pageProps = {
      projectId: currentProject,
      theme,
      compactMode,
      projectList,
      cameraConfig,
      onEnterCameraPage: () => setShowCameraPage(true),
      onRefreshProjects: fetchProjectList,
    };
    switch (currentPage) {
      case PAGE_IDS.MAP_DASHBOARD:
        return <MapDashboard theme={theme} onNavigate={handleNavigateFromMap} projectList={projectList} onRefreshProjects={fetchProjectList} />;
      case PAGE_IDS.OVERVIEW:
        return <OverviewPage {...pageProps} />;
      case PAGE_IDS.PERSONNEL:
        return <PersonnelPage {...pageProps} />;
      case PAGE_IDS.MONITOR:
        return <MonitorPage {...pageProps} />;
      default:
        return <MapDashboard theme={theme} onNavigate={handleNavigateFromMap} projectList={projectList} onRefreshProjects={fetchProjectList} />;
    }
  };

  if (!isAuth) {
    return <LoginPage onLogin={handleLogin} />;
  }

  // 如果进入了镜头管理页面，单独渲染
  if (showCameraPage) {
    return (
      <div data-theme={themeId} style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', background: theme.pageBg }}>
        <CameraPage
          projectId={currentProject}
          theme={theme}
          compactMode={compactMode}
          cameraConfig={cameraConfig}
          onSaveConfig={(config) => setCameraConfig(config)}
          onBack={() => setShowCameraPage(false)}
        />
      </div>
    );
  }

  return (
    <div data-theme={themeId} style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', background: theme.pageBg }}>
      {currentPage !== PAGE_IDS.MAP_DASHBOARD && (
        <TopNav
          currentPage={currentPage}
          onPageChange={setCurrentPage}
          currentProject={currentProject}
          onProjectChange={setCurrentProject}
          projectList={projectList}
          onRefreshProjects={fetchProjectList}
          theme={theme}
          themeId={themeId}
          onThemeChange={setThemeId}
          compactMode={compactMode}
          onCompactChange={setCompactMode}
          onLogout={handleLogout}
        />
      )}
      <main style={{ flex: 1, overflow: 'hidden' }}>
        {renderPage()}
      </main>
    </div>
  );
}
