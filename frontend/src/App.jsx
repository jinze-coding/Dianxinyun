import React, { useState, useEffect, useCallback } from 'react';
import { ALL_THEMES, DEFAULT_THEME_ID, getThemeById } from './constants/themes';
import { PROJECTS, PROJECT_INFO, DATA_BY_PROJECT } from './constants/mockData';
import { NAV_ITEMS, PAGE_IDS } from './constants/dicts';
import { isLoggedIn, getCurrentUser } from './services/auth';
import { getProjectList, addProject, updateProject, deleteProject } from './services/project';
import { getPersonnelList, addPersonnel, updatePersonnel, deletePersonnel } from './services/personnel';
import { getTrainingList, createTraining, updateTraining, markTrainingComplete, deleteTraining } from './services/safety';
import { getFileList, uploadFile, deleteFile } from './services/file';
import { getCameraList, getDeviceList, getTowerCraneList, createCamera, updateCamera, deleteCamera, createDevice, updateDevice, deleteDevice } from './services/monitor';
import {
  getElectricBoxList,
  createElectricBox,
  updateElectricBox,
  disableElectricBox,
  setElectricBoxPublicAccess,
  removeElectricBox,
  rebindElectricBoxQr,
  recordElectricBoxQrPrint,
  getElectricBoxQrLogs,
  generateElectricBoxQrSvg,
  downloadElectricBoxImportTemplate,
  importElectricBoxes,
  getElectricBoxUnifiedCode,
  rotateElectricBoxUnifiedCode,
  updateElectricBoxInspectionScope,
} from './services/electricBox';
import { getInspectionRecords, getInspectionRecord, getInspectionTodos, reviewInspectionRecord, assignInspectionReviewer, getInspectionReviewLogs, getInspectionSummary, getInspectionRectifications, getInspectionRectification, completeInspectionRectification, closeInspectionRectification, rejectInspectionRectification, assignInspectionRectification, escalateInspectionRectification, exportInspectionRecords, downloadFileAsObjectUrl, getProjectInspectionSetting, updateProjectInspectionSetting } from './services/inspection';
import { getWechatAccessApplications, approveWechatAccessApplication, rejectWechatAccessApplication } from './services/wechatAccess';
import { getWechatUsers, getWechatUserDetail, updateWechatBindingStatus, unbindWechatUser } from './services/wechatUsers';
import { getProjectMembers, getProjectUserOptions, createProjectUser, saveProjectMember, updateProjectMember, removeProjectMember, updateProjectMemberStatus } from './services/projectMembers';
import { getInspectionPermissionCatalog, getInspectionPermissionTemplates, createInspectionPermissionTemplate, updateInspectionPermissionTemplate, updateInspectionPermissionTemplateStatus } from './services/inspectionPermissionTemplates';
import { CameraPage } from './pages/Camera';
import LoginPage from './pages/Login';
import PersonnelManagementPage from './pages/PersonnelManagement';
import QualityManagementPage from './pages/QualityManagement';
import DocumentManagementPage from './pages/DocumentManagement';
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

const OVERVIEW_LEFT_WIDTH_STORAGE_KEY = 'site_platform_overview_left_width';
const OVERVIEW_LEFT_WIDTH_DEFAULT = 280;
const OVERVIEW_LEFT_WIDTH_MIN = 220;
const OVERVIEW_LEFT_WIDTH_MAX = 420;
const OVERVIEW_RIGHT_WIDTH_STORAGE_KEY = 'site_platform_overview_right_width';
const OVERVIEW_RIGHT_WIDTH_DEFAULT = 320;
const OVERVIEW_RIGHT_WIDTH_MIN = 280;
const OVERVIEW_RIGHT_WIDTH_MAX = 520;

const clampOverviewLeftWidth = (value) => {
  if (value === null || value === undefined || value === '') return OVERVIEW_LEFT_WIDTH_DEFAULT;
  const width = Number(value);
  if (!Number.isFinite(width)) return OVERVIEW_LEFT_WIDTH_DEFAULT;
  return Math.min(OVERVIEW_LEFT_WIDTH_MAX, Math.max(OVERVIEW_LEFT_WIDTH_MIN, Math.round(width)));
};

const clampOverviewRightWidth = (value) => {
  if (value === null || value === undefined || value === '') return OVERVIEW_RIGHT_WIDTH_DEFAULT;
  const width = Number(value);
  if (!Number.isFinite(width)) return OVERVIEW_RIGHT_WIDTH_DEFAULT;
  return Math.min(OVERVIEW_RIGHT_WIDTH_MAX, Math.max(OVERVIEW_RIGHT_WIDTH_MIN, Math.round(width)));
};

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
function TopNav({ currentPage, onPageChange, currentProject, onProjectChange, projectList, onRefreshProjects, theme, themeId, onThemeChange, compactMode, onCompactChange, onLogout, currentUser }) {
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
    if (!addForm.projectName.trim()) { alert('请填写作业区域名称'); return; }
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
  const userName = currentUser?.realName || currentUser?.username || '平台管理员';
  const userRoles = currentUser?.roles || [];
  const userRoleName = Number(currentUser?.id || currentUser?.userId) === 1 ? '平台管理员'
    : userRoles.includes('PLATFORM_ADMIN') ? '平台管理员'
    : userRoles.includes('PROJECT_ADMIN') ? '项目管理员'
      : userRoles.includes('SAFETY_ADMIN') ? '安全管理员'
        : '项目成员';

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
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, minWidth: 200 }}>
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

      {/* 作业区域管理 */}
      <div style={{ position: 'relative', marginLeft: 12 }}>
        <button
          onClick={e => { e.stopPropagation(); setShowProjectMgmt(!showProjectMgmt); setShowProjects(false); }}
          style={{
            display: 'flex', alignItems: 'center', gap: 6,
            background: T.activeItemBg, border: `1px solid ${T.accent}`,
            borderRadius: 6, padding: '5px 12px', cursor: 'pointer', color: T.accent,
            fontSize: 12, fontWeight: 500, whiteSpace: 'nowrap',
          }}
        >
          <span>⚙</span>
          <span>作业区域管理</span>
        </button>
        {showProjectMgmt && (
          <div style={{
            position: 'absolute', top: '100%', left: 0, marginTop: 6,
            background: T.dropdownBg, border: `1px solid ${T.borderColor}`,
            borderRadius: 8, overflow: 'hidden', minWidth: 300, zIndex: 200,
            boxShadow: '0 8px 24px rgba(0,0,0,0.4)',
          }} onClick={e => e.stopPropagation()}>
            <div style={{ padding: '8px 10px', borderBottom: `1px solid ${T.borderColor}`, display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
              <span style={{ fontSize: 12, fontWeight: 600, color: T.textPrimary }}>作业区域列表</span>
              <button onClick={() => { setShowAddModal(true); }} style={{
                fontSize: 11, color: T.accent, background: 'none', border: 'none', cursor: 'pointer', fontWeight: 500,
              }}>+ 新增</button>
            </div>
            <div style={{ padding: '6px 8px', borderBottom: `1px solid ${T.borderColor}` }}>
              <input
                placeholder="搜索作业区域名称..."
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
                <div style={{ padding: '20px 12px', textAlign: 'center', fontSize: 12, color: T.textMuted }}>无匹配作业区域</div>
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
            fontSize: 13, minWidth: 150,
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
      <nav style={{
        display: 'flex',
        alignItems: 'center',
        gap: 4,
        marginLeft: 16,
        flex: '1 1 auto',
        minWidth: 0,
        justifyContent: 'center',
        overflowX: 'auto',
        overflowY: 'hidden',
        whiteSpace: 'nowrap',
      }}>
        {NAV_ITEMS.map(item => {
          const active = currentPage === item.id;
          return (
            <button
              key={item.id}
              onClick={() => onPageChange(item.id)}
              style={{
                height: 42,
                minWidth: 82,
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                flex: '0 0 auto',
                padding: '0 10px',
                border: 'none',
                cursor: 'pointer',
                borderRadius: 6,
                fontSize: 14, fontWeight: active ? 600 : 400,
                color: active ? '#fff' : T.textSecondary,
                background: active ? T.accent : 'transparent',
                transition: 'all 0.2s',
                letterSpacing: 0.5,
                lineHeight: 1,
                whiteSpace: 'nowrap',
                wordBreak: 'keep-all',
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
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, minWidth: 340, justifyContent: 'flex-end' }}>
        <div style={{ fontSize: 12, color: T.textMuted, fontVariantNumeric: 'tabular-nums', letterSpacing: 0.5, whiteSpace: 'nowrap' }}>
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
                配色方案 &nbsp;·&nbsp; 白色
              </div>
              <div style={{
                height: 4, borderRadius: 2, marginBottom: 12,
                background: '#f0f4f9',
                border: `1px solid ${T.borderColor}`,
              }}></div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: 8, marginBottom: 12 }}>
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
          }}>{userRoleName.slice(0, 1)}</div>
          <span>{userName}</span>
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

      {/* 新增作业区域弹窗 */}
      {showAddModal && (
        <div style={{
          position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.6)',
          display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1001,
        }} onClick={() => setShowAddModal(false)}>
          <div style={{
            background: T.modalBg, border: `1px solid ${T.borderColor}`,
            borderRadius: 10, padding: 24, width: 500,
          }} onClick={e => e.stopPropagation()}>
            <div style={{ fontSize: 14, fontWeight: 600, color: T.textPrimary, marginBottom: 16 }}>新增作业区域</div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>作业区域名称 *</label>
                <input placeholder="请输入作业区域名称" value={addForm.projectName} onChange={e => setAddForm({ ...addForm, projectName: e.target.value })} style={{
                  width: '100%', background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 5,
                  padding: '8px 10px', fontSize: 12, color: T.textPrimary, outline: 'none',
                }} />
              </div>
              <div>
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>区域简称</label>
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
                <label style={{ fontSize: 12, color: T.textSecondary, marginBottom: 4, display: 'block' }}>区域状态</label>
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

function ProjectPlaceholderPage({ projectId, projectList, theme: T, title, description }) {
  const project = projectList?.find(item => item.id === projectId) || projectList?.[0] || {};
  const projectName = project.projectName || project.shortName || '当前项目';

  return (
    <div style={{
      height: '100%',
      padding: 16,
      background: T.pageBg,
      overflow: 'auto',
    }}>
      <div style={{
        background: T.cardBg,
        border: `1px solid ${T.borderColor}`,
        borderRadius: 8,
        padding: 18,
        maxWidth: 920,
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 16, marginBottom: 18 }}>
          <div>
            <div style={{ fontSize: 12, color: T.textMuted, marginBottom: 6 }}>{projectName}</div>
            <div style={{ fontSize: 20, fontWeight: 800, color: T.textPrimary }}>{title}</div>
          </div>
          <span style={{
            padding: '4px 10px',
            borderRadius: 999,
            background: T.activeItemBg,
            border: `1px solid ${T.accent}`,
            color: T.accent,
            fontSize: 12,
            fontWeight: 700,
            whiteSpace: 'nowrap',
          }}>待建设</span>
        </div>
        <div style={{
          borderTop: `1px solid ${T.borderColor}`,
          paddingTop: 16,
          color: T.textSecondary,
          fontSize: 13,
          lineHeight: 1.8,
        }}>
          {description}
        </div>
      </div>
    </div>
  );
}

const MONITOR_LAYOUT_MAP = {
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

const mapCameraResource = (camera) => ({
  id: camera.id,
  name: camera.name || camera.cameraName,
  code: camera.code || camera.cameraCode,
  area: camera.area || '未分区',
  type: camera.type || camera.cameraType || '海康',
  rtspUrl: camera.rtspUrl || '',
  online: camera.online !== undefined ? camera.online : camera.onlineStatus === 1,
});

const mapDeviceResource = (device) => ({
  id: device.id,
  name: device.deviceName || device.name,
  code: device.deviceCode || device.code,
  type: normalizeDeviceType(device.deviceType || device.type),
  status: normalizeDeviceStatus(device.status),
  height: device.height,
  maxLoad: device.maxLoad,
  lastReport: device.lastReport,
  note: device.remark || device.note,
});

const getDeviceStatusColor = (status, T) => {
  if (status === '运行中') return T.success;
  if (status === '停机' || status === '维修中') return T.warning;
  if (status === '异常') return T.danger;
  return T.textMuted;
};

function CameraDeviceSidebar({
  cameras,
  devices,
  loading,
  videoLayout,
  cameraAssignments = [],
  cameraAreaFilter,
  onCameraAreaFilterChange,
  onEnterCameraPage,
  onAddCamera,
  onExpandCameraList,
  onEditCamera,
  onDeleteCamera,
  sidebarWidth,
  theme: T,
}) {
  const onlineCameras = cameras.filter(camera => camera.online).length;
  const offlineCameras = cameras.length - onlineCameras;
  const runningDevices = devices.filter(device => device.status === '运行中').length;
  const stoppedDevices = devices.filter(device => device.status === '停机').length;
  const abnormalDevices = devices.filter(device => device.status === '异常').length;
  const towerCraneCount = devices.filter(device => device.type === '塔吊').length;
  const areas = ['全部', ...Array.from(new Set(cameras.map(camera => camera.area).filter(Boolean)))];
  const filteredCameras = cameraAreaFilter === '全部'
    ? cameras
    : cameras.filter(camera => camera.area === cameraAreaFilter);
  const hasWindowConfig = cameraAssignments.slice(0, videoLayout).some(Boolean);
  const compactCameraHeader = sidebarWidth < 300;
  const sectionStyle = {
    background: T.surface2,
    border: `1px solid ${T.borderColor}`,
    borderRadius: 6,
    padding: 10,
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10, minHeight: 0 }}>
      <div style={sectionStyle}>
        <div style={{ fontSize: 12, fontWeight: 700, color: T.textPrimary, marginBottom: 10 }}>摄像头概览</div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, minmax(0, 1fr))', gap: 6 }}>
          {[
            ['在线', onlineCameras, T.success],
            ['离线', offlineCameras, T.danger],
            ['总计', cameras.length, T.accent],
          ].map(([label, value, color]) => (
            <div key={label} style={{ background: T.cardBg, borderRadius: 5, padding: '9px 4px', textAlign: 'center' }}>
              <div style={{ fontSize: 20, fontWeight: 800, color, lineHeight: 1 }}>{value}</div>
              <div style={{ fontSize: 10, color: T.textMuted, marginTop: 4 }}>{label}</div>
            </div>
          ))}
        </div>
      </div>

      <div style={sectionStyle}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
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

      <div style={{ ...sectionStyle, flex: '0 0 360px', minHeight: 320, maxHeight: 460, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
        <div style={{
          display: 'flex',
          flexDirection: compactCameraHeader ? 'column' : 'row',
          justifyContent: 'space-between',
          alignItems: compactCameraHeader ? 'stretch' : 'center',
          marginBottom: 8,
          gap: 6,
        }}>
          <span style={{ fontSize: 12, fontWeight: 700, color: T.textPrimary, whiteSpace: 'nowrap', lineHeight: 1.3 }}>摄像头列表</span>
          <div style={{ display: 'flex', gap: 4, flexShrink: 0, flexWrap: 'wrap', justifyContent: compactCameraHeader ? 'flex-start' : 'flex-end' }}>
            <button onClick={onExpandCameraList} style={{ padding: '3px 7px', fontSize: 10, borderRadius: 4, cursor: 'pointer', background: T.cardBg, border: `1px solid ${T.borderColor}`, color: T.textSecondary }}>展开</button>
            <button onClick={onEnterCameraPage} style={{ padding: '3px 7px', fontSize: 10, borderRadius: 4, cursor: 'pointer', background: T.cardBg, border: `1px solid ${T.accent}`, color: T.accent }}>窗口配置</button>
            <button onClick={onAddCamera} style={{ padding: '3px 7px', fontSize: 10, borderRadius: 4, cursor: 'pointer', background: T.accent, border: 'none', color: '#fff' }}>新增</button>
          </div>
        </div>
        <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', marginBottom: 8 }}>
          {areas.map(area => (
            <button key={area} onClick={() => onCameraAreaFilterChange(area)} style={{
              padding: '2px 6px', fontSize: 10, borderRadius: 3, cursor: 'pointer',
              border: `1px solid ${cameraAreaFilter === area ? T.accent : T.borderColor}`,
              background: cameraAreaFilter === area ? T.accent : T.cardBg,
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
            marginBottom: 8,
            fontSize: 10,
            color: T.accent,
          }}>
            已应用镜头管理页的 {MONITOR_LAYOUT_MAP[videoLayout]?.label || `${videoLayout}窗口`} 配置
          </div>
        )}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 6, overflow: 'auto', flex: 1, minHeight: 0, paddingRight: 2 }}>
          {loading && <div style={{ color: T.textMuted, fontSize: 12, textAlign: 'center', padding: 20 }}>加载中...</div>}
          {!loading && filteredCameras.map(camera => (
            <div key={camera.id} style={{
              padding: '7px 8px', borderRadius: 4, background: T.cardBg,
              border: `1px solid ${T.borderColor}`,
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, minWidth: 0 }}>
                  <span style={{ width: 6, height: 6, borderRadius: '50%', background: camera.online ? T.success : T.danger, flexShrink: 0 }} />
                  <span style={{ fontSize: 11, color: T.textPrimary, fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{camera.name}</span>
                </div>
                <span style={{ fontSize: 9, color: T.textMuted, flexShrink: 0 }}>{camera.area}</span>
              </div>
              <div style={{ display: 'flex', gap: 8, marginTop: 5 }}>
                <button onClick={() => onEditCamera(camera)} style={{ fontSize: 10, color: T.accent, background: 'none', border: 'none', padding: 0, cursor: 'pointer' }}>编辑</button>
                <button onClick={() => onDeleteCamera(camera)} style={{ fontSize: 10, color: T.danger, background: 'none', border: 'none', padding: 0, cursor: 'pointer' }}>删除</button>
              </div>
            </div>
          ))}
          {!loading && filteredCameras.length === 0 && (
            <div style={{ color: T.textMuted, fontSize: 12, textAlign: 'center', padding: 20 }}>暂无摄像头</div>
          )}
        </div>
      </div>
    </div>
  );
}

function CameraListModal({
  cameras,
  loading,
  cameraAreaFilter,
  onCameraAreaFilterChange,
  onEnterCameraPage,
  onAddCamera,
  onEditCamera,
  onDeleteCamera,
  onClose,
  theme: T,
}) {
  const areas = ['全部', ...Array.from(new Set(cameras.map(camera => camera.area).filter(Boolean)))];
  const filteredCameras = cameraAreaFilter === '全部'
    ? cameras
    : cameras.filter(camera => camera.area === cameraAreaFilter);
  const onlineCameras = cameras.filter(camera => camera.online).length;

  return (
    <div style={{
      position: 'fixed',
      inset: 0,
      background: 'rgba(15, 23, 42, 0.55)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      zIndex: 1001,
      padding: 20,
    }} onClick={onClose}>
      <div style={{
        width: 860,
        maxWidth: '92vw',
        maxHeight: '84vh',
        background: T.modalBg,
        border: `1px solid ${T.borderColor}`,
        borderRadius: 10,
        boxShadow: '0 18px 60px rgba(15, 23, 42, 0.28)',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
      }} onClick={event => event.stopPropagation()}>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 12,
          padding: '16px 18px',
          borderBottom: `1px solid ${T.borderColor}`,
          flexShrink: 0,
        }}>
          <div>
            <div style={{ fontSize: 15, fontWeight: 800, color: T.textPrimary }}>摄像头列表</div>
            <div style={{ fontSize: 11, color: T.textMuted, marginTop: 4 }}>在线 {onlineCameras}/{cameras.length} · 当前显示 {filteredCameras.length} 个</div>
          </div>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap', justifyContent: 'flex-end' }}>
            <button onClick={onEnterCameraPage} style={{ padding: '7px 12px', fontSize: 12, borderRadius: 5, cursor: 'pointer', background: T.surface2, border: `1px solid ${T.accent}`, color: T.accent }}>窗口配置</button>
            <button onClick={onAddCamera} style={{ padding: '7px 12px', fontSize: 12, borderRadius: 5, cursor: 'pointer', background: T.accent, border: 'none', color: '#fff' }}>新增</button>
            <button onClick={onClose} style={{ padding: '7px 12px', fontSize: 12, borderRadius: 5, cursor: 'pointer', background: 'transparent', border: `1px solid ${T.borderColor}`, color: T.textSecondary }}>关闭</button>
          </div>
        </div>
        <div style={{ padding: '12px 18px 0', flexShrink: 0 }}>
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            {areas.map(area => (
              <button key={area} onClick={() => onCameraAreaFilterChange(area)} style={{
                padding: '4px 10px',
                fontSize: 11,
                borderRadius: 4,
                cursor: 'pointer',
                border: `1px solid ${cameraAreaFilter === area ? T.accent : T.borderColor}`,
                background: cameraAreaFilter === area ? T.accent : T.surface2,
                color: cameraAreaFilter === area ? '#fff' : T.textMuted,
              }}>{area}</button>
            ))}
          </div>
        </div>
        <div style={{ padding: 18, overflow: 'auto', minHeight: 0 }}>
          {loading && <div style={{ color: T.textMuted, fontSize: 13, textAlign: 'center', padding: 36 }}>加载中...</div>}
          {!loading && filteredCameras.length === 0 && (
            <div style={{ color: T.textMuted, fontSize: 13, textAlign: 'center', padding: 36 }}>暂无摄像头</div>
          )}
          {!loading && filteredCameras.length > 0 && (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: 10 }}>
              {filteredCameras.map(camera => (
                <div key={camera.id} style={{
                  border: `1px solid ${T.borderColor}`,
                  borderRadius: 7,
                  background: T.surface2,
                  padding: 12,
                  minWidth: 0,
                }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8, marginBottom: 8 }}>
                    <div style={{ minWidth: 0 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 6, minWidth: 0 }}>
                        <span style={{ width: 7, height: 7, borderRadius: '50%', background: camera.online ? T.success : T.danger, flexShrink: 0 }} />
                        <span style={{ fontSize: 13, color: T.textPrimary, fontWeight: 700, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{camera.name}</span>
                      </div>
                      <div style={{ fontSize: 10, color: T.textMuted, marginTop: 5 }}>{camera.code || '-'}</div>
                    </div>
                    <span style={{ fontSize: 10, color: camera.online ? T.success : T.danger, border: `1px solid ${camera.online ? T.success : T.danger}`, borderRadius: 4, padding: '2px 6px', flexShrink: 0 }}>{camera.online ? '在线' : '离线'}</span>
                  </div>
                  <div style={{ fontSize: 11, color: T.textMuted, lineHeight: 1.7 }}>区域：{camera.area || '-'}</div>
                  <div style={{ fontSize: 11, color: T.textMuted, lineHeight: 1.7 }}>类型：{camera.type || '-'}</div>
                  <div style={{ display: 'flex', gap: 10, marginTop: 10 }}>
                    <button onClick={() => onEditCamera(camera)} style={{ fontSize: 11, color: T.accent, background: 'none', border: 'none', padding: 0, cursor: 'pointer' }}>编辑</button>
                    <button onClick={() => onDeleteCamera(camera)} style={{ fontSize: 11, color: T.danger, background: 'none', border: 'none', padding: 0, cursor: 'pointer' }}>删除</button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function DeviceListPanel({ devices, onExpand, panelWidth, showTopBorder = true, theme: T }) {
  const compactHeader = panelWidth < 340;

  return (
    <div style={{ borderTop: showTopBorder ? `1px solid ${T.borderColor}` : 'none', paddingTop: showTopBorder ? 10 : 0, flexShrink: 0, display: 'flex', flexDirection: 'column' }}>
      <div style={{
        display: 'flex',
        flexDirection: compactHeader ? 'column' : 'row',
        justifyContent: 'space-between',
        alignItems: compactHeader ? 'stretch' : 'center',
        gap: 6,
        marginBottom: 10,
        flexShrink: 0,
      }}>
        <span style={{ fontSize: 12, fontWeight: 700, color: T.textPrimary, whiteSpace: 'nowrap', lineHeight: 1.3 }}>设备列表</span>
        <button onClick={onExpand} style={{
          alignSelf: compactHeader ? 'flex-start' : 'auto',
          padding: '3px 8px',
          fontSize: 10,
          borderRadius: 4,
          cursor: 'pointer',
          background: T.cardBg,
          border: `1px solid ${T.borderColor}`,
          color: T.textSecondary,
        }}>展开</button>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        {devices.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '28px 10px', color: T.textMuted, fontSize: 12 }}>暂无设备</div>
        ) : devices.map(device => {
          const color = getDeviceStatusColor(device.status, T);
          return (
            <div key={device.id} style={{ padding: 11, borderRadius: 7, border: `1px solid ${T.borderColor}`, background: T.surface2 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6, gap: 8 }}>
                <span style={{ fontSize: 12, color: T.textPrimary, fontWeight: 700, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{device.name}</span>
                <span style={{ fontSize: 10, color, border: `1px solid ${color}`, borderRadius: 4, padding: '2px 6px', flexShrink: 0 }}>{device.status}</span>
              </div>
              <div style={{ fontSize: 10, color: T.textMuted, lineHeight: 1.7 }}>编号：{device.code} · {device.type}</div>
              <div style={{ fontSize: 10, color: T.textMuted, lineHeight: 1.7 }}>最近上报：{device.lastReport?.replace('T', ' ').slice(0, 16) || '-'}</div>
              {device.note && <div style={{ marginTop: 6, padding: '4px 6px', background: `${T.warning}22`, color: T.warning, fontSize: 10, borderRadius: 4 }}>{device.note}</div>}
            </div>
          );
        })}
      </div>
    </div>
  );
}

function DeviceListModal({ devices, onClose, theme: T }) {
  const runningDevices = devices.filter(device => device.status === '运行中').length;
  const stoppedDevices = devices.filter(device => device.status === '停机' || device.status === '维修中').length;
  const abnormalDevices = devices.filter(device => device.status === '异常').length;

  return (
    <div style={{
      position: 'fixed',
      inset: 0,
      background: 'rgba(15, 23, 42, 0.55)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      zIndex: 1001,
      padding: 20,
    }} onClick={onClose}>
      <div style={{
        width: 860,
        maxWidth: '92vw',
        maxHeight: '84vh',
        background: T.modalBg,
        border: `1px solid ${T.borderColor}`,
        borderRadius: 10,
        boxShadow: '0 18px 60px rgba(15, 23, 42, 0.28)',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
      }} onClick={event => event.stopPropagation()}>
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 12,
          padding: '16px 18px',
          borderBottom: `1px solid ${T.borderColor}`,
          flexShrink: 0,
        }}>
          <div>
            <div style={{ fontSize: 16, fontWeight: 800, color: T.textPrimary }}>设备列表</div>
            <div style={{ fontSize: 11, color: T.textMuted, marginTop: 4 }}>
              共 {devices.length} 台 · 运行中 {runningDevices} · 停机/维修 {stoppedDevices} · 异常 {abnormalDevices}
            </div>
          </div>
          <button onClick={onClose} style={{
            width: 30,
            height: 30,
            borderRadius: 6,
            cursor: 'pointer',
            background: T.surface2,
            border: `1px solid ${T.borderColor}`,
            color: T.textSecondary,
            fontSize: 16,
          }}>×</button>
        </div>

        <div style={{ padding: 18, overflow: 'auto', minHeight: 0 }}>
          {devices.length === 0 ? (
            <div style={{
              textAlign: 'center',
              padding: '52px 10px',
              color: T.textMuted,
              fontSize: 13,
              border: `1px dashed ${T.borderColor}`,
              borderRadius: 8,
              background: T.surface2,
            }}>暂无设备</div>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))', gap: 10 }}>
              {devices.map(device => {
                const color = getDeviceStatusColor(device.status, T);
                return (
                  <div key={device.id} style={{ padding: 12, borderRadius: 7, border: `1px solid ${T.borderColor}`, background: T.surface2 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8, marginBottom: 8 }}>
                      <div style={{ minWidth: 0 }}>
                        <div style={{ fontSize: 13, color: T.textPrimary, fontWeight: 800, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{device.name || '-'}</div>
                        <div style={{ fontSize: 10, color: T.textMuted, marginTop: 4 }}>{device.code || '-'}</div>
                      </div>
                      <span style={{ fontSize: 10, color, border: `1px solid ${color}`, borderRadius: 4, padding: '2px 6px', flexShrink: 0 }}>{device.status || '-'}</span>
                    </div>
                    <div style={{ fontSize: 11, color: T.textMuted, lineHeight: 1.7 }}>类型：{device.type || '-'}</div>
                    <div style={{ fontSize: 11, color: T.textMuted, lineHeight: 1.7 }}>最近上报：{device.lastReport?.replace('T', ' ').slice(0, 16) || '-'}</div>
                    {device.note && <div style={{ marginTop: 8, padding: '6px 8px', background: `${T.warning}22`, color: T.warning, fontSize: 11, borderRadius: 5 }}>{device.note}</div>}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
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
  const [cameras, setCameras] = useState([]);
  const [devices, setDevices] = useState([]);
  const [monitorLoading, setMonitorLoading] = useState(false);
  const [showDetailModal, setShowDetailModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [editForm, setEditForm] = useState({});
  const [refreshTick, setRefreshTick] = useState(0);
  const [localVideoLayout, setLocalVideoLayout] = useState(cameraConfig?.videoLayout || 4);
  const [cameraAreaFilter, setCameraAreaFilter] = useState('全部');
  const [showCameraListModal, setShowCameraListModal] = useState(false);
  const [showDeviceListModal, setShowDeviceListModal] = useState(false);
  const [leftPanelWidth, setLeftPanelWidth] = useState(() => {
    if (typeof window === 'undefined') return OVERVIEW_LEFT_WIDTH_DEFAULT;
    return clampOverviewLeftWidth(localStorage.getItem(OVERVIEW_LEFT_WIDTH_STORAGE_KEY));
  });
  const [rightPanelWidth, setRightPanelWidth] = useState(() => {
    if (typeof window === 'undefined') return OVERVIEW_RIGHT_WIDTH_DEFAULT;
    return clampOverviewRightWidth(localStorage.getItem(OVERVIEW_RIGHT_WIDTH_STORAGE_KEY));
  });
  const [isResizingLeftPanel, setIsResizingLeftPanel] = useState(false);
  const [isResizingRightPanel, setIsResizingRightPanel] = useState(false);
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

  // 使用 cameraConfig 的配置，但允许本地修改
  const videoLayout = localVideoLayout;
  const cameraAssignments = cameraConfig?.cameraAssignments || [];

  const layouts = MONITOR_LAYOUT_MAP;
  // 计算项目进度
  const progressPercent = calculateProjectProgress(info?.startDate, info?.endDate);
  const stats = { ...(mockData?.stats || { onsite: 0, todayNewOnsite: 0 }), progressPercent };
  const onlineCount = cameras.filter(c => c.online === true).length;
  const totalCameras = cameras.length;
  const runningDeviceCount = devices.filter(device => device.status === '运行中').length;

  const fetchOverviewMonitorData = useCallback(async () => {
    setMonitorLoading(true);
    try {
      const [cameraRes, deviceRes] = await Promise.all([
        getCameraList(projectId),
        getDeviceList(projectId),
      ]);

      if (cameraRes.code === 200 && cameraRes.data) {
        setCameras(cameraRes.data.map(mapCameraResource));
      }

      if (deviceRes.code === 200 && deviceRes.data) {
        setDevices(deviceRes.data.map(mapDeviceResource));
      }
    } catch (e) {
      console.error('获取监控与设备数据失败', e);
      setCameras((mockData?.cameras || []).map(mapCameraResource));
      setDevices((mockData?.devices || []).map(mapDeviceResource));
    } finally {
      setMonitorLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    fetchOverviewMonitorData();
  }, [fetchOverviewMonitorData]);

  const layoutOptions = [
    { n: 1, label: '单屏' },
    { n: 4, label: '四屏' },
    { n: 8, label: '八窗口' },
    { n: 16, label: '十六窗口' },
  ];

  // 项目切换时重置筛选
  useEffect(() => {
    setFullscreenCam(null);
    setCameraAreaFilter('全部');
    setShowCameraListModal(false);
    setShowDeviceListModal(false);
  }, [projectId]);

  const projectStatusStyle = (s) => {
    if (s === '正常' || s === 'normal') return { bg: `${T.success}22`, color: T.success, text: '正常' };
    if (s === '延期' || s === 'warning') return { bg: `${T.warning}22`, color: T.warning, text: '延期' };
    if (s === '停工' || s === 'danger') return { bg: `${T.danger}22`, color: T.danger, text: '停工' };
    return { bg: T.tagBg, color: T.textMuted, text: '未知' };
  };
  const psStyle = projectStatusStyle(info?.projectStatus);

  const { cols: gridCols } = layouts[videoLayout] || layouts[4];

  const handleStartResizeLeftPanel = useCallback((event) => {
    if (event.button !== 0) return;
    event.preventDefault();

    const startX = event.clientX;
    const startWidth = leftPanelWidth;
    const originalUserSelect = document.body.style.userSelect;
    const originalCursor = document.body.style.cursor;

    setIsResizingLeftPanel(true);
    document.body.style.userSelect = 'none';
    document.body.style.cursor = 'col-resize';

    const handleMouseMove = (moveEvent) => {
      setLeftPanelWidth(clampOverviewLeftWidth(startWidth + moveEvent.clientX - startX));
    };

    const handleMouseUp = (upEvent) => {
      const nextWidth = clampOverviewLeftWidth(startWidth + upEvent.clientX - startX);
      setLeftPanelWidth(nextWidth);
      localStorage.setItem(OVERVIEW_LEFT_WIDTH_STORAGE_KEY, String(nextWidth));
      setIsResizingLeftPanel(false);
      document.body.style.userSelect = originalUserSelect;
      document.body.style.cursor = originalCursor;
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };

    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);
  }, [leftPanelWidth]);

  const handleStartResizeRightPanel = useCallback((event) => {
    if (event.button !== 0) return;
    event.preventDefault();

    const startX = event.clientX;
    const startWidth = rightPanelWidth;
    const originalUserSelect = document.body.style.userSelect;
    const originalCursor = document.body.style.cursor;

    setIsResizingRightPanel(true);
    document.body.style.userSelect = 'none';
    document.body.style.cursor = 'col-resize';

    const handleMouseMove = (moveEvent) => {
      setRightPanelWidth(clampOverviewRightWidth(startWidth + startX - moveEvent.clientX));
    };

    const handleMouseUp = (upEvent) => {
      const nextWidth = clampOverviewRightWidth(startWidth + startX - upEvent.clientX);
      setRightPanelWidth(nextWidth);
      localStorage.setItem(OVERVIEW_RIGHT_WIDTH_STORAGE_KEY, String(nextWidth));
      setIsResizingRightPanel(false);
      document.body.style.userSelect = originalUserSelect;
      document.body.style.cursor = originalCursor;
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };

    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);
  }, [rightPanelWidth]);

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
      await fetchOverviewMonitorData();
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
        setCameras(prev => prev.filter(item => item.id !== camera.id));
      } else {
        alert(res.message || '删除失败');
      }
    } catch (err) {
      console.error('删除摄像头失败', err);
      alert('删除失败，请重试');
    }
  };

  const handleRefreshVideo = () => { setRefreshTick(t => t + 1); };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', gap: 12, padding: 16, overflow: 'hidden' }}>
      {/* StatCards */}
      <div style={{ display: 'flex', gap: 10, flexShrink: 0 }}>
        <StatCard label="在场人员" value={stats.onsite} sub={`今日新增 ${stats.todayNewOnsite} 人`} color={T.accent} theme={T} />
        <StatCard label="摄像头在线" value={`${onlineCount}/${totalCameras}`} sub={`${totalCameras - onlineCount} 路离线`} color={T.success} theme={T} />
        <StatCard label="设备运行" value={`${runningDeviceCount}/${devices.length}`} sub={`${devices.length - runningDeviceCount} 台非运行`} color={T.warning} theme={T} />
        <StatCard label="项目进度" value={`${stats.progressPercent}%`} sub={info?.phase} color={T.accent2} theme={T} />
      </div>

      {/* 主内容区 */}
      <div style={{ display: 'flex', gap: 0, flex: 1, minHeight: 0 }}>
        {/* 左：项目基础信息，可拖动调整宽度 */}
        <div style={{
          width: leftPanelWidth,
          minWidth: OVERVIEW_LEFT_WIDTH_MIN,
          maxWidth: OVERVIEW_LEFT_WIDTH_MAX,
          flexShrink: 0,
          background: T.cardBg,
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

          <CameraDeviceSidebar
            cameras={cameras}
            devices={devices}
            loading={monitorLoading}
            videoLayout={videoLayout}
            cameraAssignments={cameraAssignments}
            cameraAreaFilter={cameraAreaFilter}
            onCameraAreaFilterChange={setCameraAreaFilter}
            onEnterCameraPage={onEnterCameraPage}
            onAddCamera={openAddCamera}
            onExpandCameraList={() => setShowCameraListModal(true)}
            onEditCamera={openEditCamera}
            onDeleteCamera={handleDeleteCamera}
            sidebarWidth={leftPanelWidth}
            theme={T}
          />
        </div>

        <div
          role="separator"
          aria-orientation="vertical"
          aria-label="拖动调整左侧栏宽度"
          title="拖动调整左侧栏宽度"
          onMouseDown={handleStartResizeLeftPanel}
          style={{
            width: 12,
            flexShrink: 0,
            cursor: 'col-resize',
            display: 'flex',
            alignItems: 'stretch',
            justifyContent: 'center',
            padding: '0 5px',
            touchAction: 'none',
          }}
        >
          <div style={{
            width: 2,
            borderRadius: 2,
            background: isResizingLeftPanel ? T.accent : T.borderColor,
            opacity: isResizingLeftPanel ? 1 : 0.75,
            boxShadow: isResizingLeftPanel ? `0 0 0 3px ${T.activeItemBg}` : 'none',
          }} />
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

        <div
          role="separator"
          aria-orientation="vertical"
          aria-label="拖动调整右侧栏宽度"
          title="拖动调整右侧栏宽度"
          onMouseDown={handleStartResizeRightPanel}
          style={{
            width: 12,
            flexShrink: 0,
            cursor: 'col-resize',
            display: 'flex',
            alignItems: 'stretch',
            justifyContent: 'center',
            padding: '0 5px',
            touchAction: 'none',
          }}
        >
          <div style={{
            width: 2,
            borderRadius: 2,
            background: isResizingRightPanel ? T.accent : T.borderColor,
            opacity: isResizingRightPanel ? 1 : 0.75,
            boxShadow: isResizingRightPanel ? `0 0 0 3px ${T.activeItemBg}` : 'none',
          }} />
        </div>

        {/* 右：设备列表，可拖动调整宽度 */}
        <div style={{
          width: rightPanelWidth,
          minWidth: OVERVIEW_RIGHT_WIDTH_MIN,
          maxWidth: OVERVIEW_RIGHT_WIDTH_MAX,
          flexShrink: 0,
          background: T.cardBg,
          border: `1px solid ${T.borderColor}`, borderRadius: T.radius,
          padding: 14, display: 'flex', flexDirection: 'column', overflow: 'auto',
        }}>
          <DeviceListPanel
            devices={devices}
            onExpand={() => setShowDeviceListModal(true)}
            panelWidth={rightPanelWidth}
            showTopBorder={false}
            theme={T}
          />
        </div>
      </div>

      {showCameraListModal && (
        <CameraListModal
          cameras={cameras}
          loading={monitorLoading}
          cameraAreaFilter={cameraAreaFilter}
          onCameraAreaFilterChange={setCameraAreaFilter}
          onEnterCameraPage={onEnterCameraPage}
          onAddCamera={openAddCamera}
          onEditCamera={openEditCamera}
          onDeleteCamera={handleDeleteCamera}
          onClose={() => setShowCameraListModal(false)}
          theme={T}
        />
      )}

      {showDeviceListModal && (
        <DeviceListModal
          devices={devices}
          onClose={() => setShowDeviceListModal(false)}
          theme={T}
        />
      )}

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
// 页面：巡检管理
// ============================================
const ELECTRIC_INSPECTION_TABS = [
  { id: 'ledger', label: '巡检台账' },
  { id: 'records', label: '巡检记录' },
  { id: 'permission', label: '用户与权限', permissionOnly: true },
];

const BOX_STATUS_TEXT = { ACTIVE: '启用', INACTIVE: '停用', REMOVED: '已拆除' };
const BOX_TODAY_STATUS_TEXT = { CHECKED: '已检', UNCHECKED: '未检', ABNORMAL: '异常' };
const RECORD_STATUS_TEXT = {
  COMPLETED: '已完成',
  REVIEW_PENDING: '待复核',
  REVIEW_PASSED: '已通过',
  REVIEW_REJECTED: '已退回',
  RECTIFICATION_PENDING: '待整改',
  CLOSED: '已归档',
};
const REVIEW_STATUS_TEXT = {
  PENDING: '待复核',
  PASSED: '通过',
  REJECTED: '退回',
  RECTIFICATION_REQUIRED: '转整改',
};
const REVIEW_LOG_ACTION_TEXT = {
  ASSIGN: '自动分配',
  REASSIGN: '改派复核人',
  UNASSIGN: '进入共享池',
  PASS: '复核通过',
  REJECT: '退回修改',
  RECTIFY: '转整改',
  OVERDUE: '复核逾期',
};
const RECTIFICATION_STATUS_TEXT = {
  PENDING: '待整改',
  COMPLETED: '待复查',
  CLOSED: '已关闭',
  REJECTED: '复查退回',
};
const RECTIFICATION_LOG_ACTION_TEXT = {
  COMPLETE: '提交整改',
  CLOSE: '复查关闭',
  REJECT: '复查退回',
  ASSIGN: '改派整改人',
  REMIND: '跟进提醒',
  ESCALATE: '升级提醒',
};
const SPOT_CHECK_CATEGORIES = [
  { value: 'APPEARANCE', label: '箱体外观', template: '恢复箱门闭合，清理箱体周边杂物，整改后上传外观照片。' },
  { value: 'LEAKAGE_PROTECTOR', label: '漏电保护器', template: '检查漏电保护器动作状态，异常部件需更换，整改后上传内部照片。' },
  { value: 'FUSE', label: '熔断/开关', template: '核查熔断器和开关配置，恢复规范接线并上传整改照片。' },
  { value: 'PROTECTIVE_ZERO', label: '保护接零', template: '补齐保护接零措施，确认连接牢固，整改后上传接线照片。' },
  { value: 'SOCKET', label: '插座/用电', template: '整理插座和临时用电线路，消除私拉乱接，整改后上传照片。' },
  { value: 'ENVIRONMENT', label: '环境/通道', template: '清理电箱周边环境，保持通道畅通，整改后上传现场照片。' },
  { value: 'OTHER', label: '其他', template: '按现场安全要求完成整改，并上传整改前后对比照片。' },
];
const SPOT_CHECK_CATEGORY_TEXT = SPOT_CHECK_CATEGORIES.reduce((acc, item) => ({ ...acc, [item.value]: item.label }), {});
const DEFAULT_SPOT_CHECK_CATEGORY = SPOT_CHECK_CATEGORIES[0];
const SOURCE_TEXT = {
  ELECTRICIAN_DAILY: '电工日检',
  SAFETY_SPOT_CHECK: '安全抽查',
};
const ITEM_RESULT_TEXT = {
  NORMAL: '正常',
  ABNORMAL: '异常',
  NA: '不适用',
};
const PROJECT_ROLE_TEXT = {
  PLATFORM_ADMIN: '平台管理员',
  PROJECT_ADMIN: '项目管理员',
  SAFETY_ADMIN: '项目安全员',
  USER: '项目成员',
};
const PROJECT_ROLE_OPTIONS = [
  { value: 'PROJECT_ADMIN', label: '项目管理员' },
  { value: 'SAFETY_ADMIN', label: '项目安全员' },
  { value: 'USER', label: '项目成员/负责电工' },
];
const GLOBAL_ROLE_OPTIONS = [
  { value: 'USER', label: '普通用户' },
  { value: 'SAFETY_ADMIN', label: '安全管理员' },
  { value: 'PROJECT_ADMIN', label: '项目管理员' },
  { value: 'PLATFORM_ADMIN', label: '平台管理员' },
];
const INSPECTION_PERMISSION_CODES = {
  BOX_VIEW: 'BOX_VIEW',
  BOX_MANAGE: 'BOX_MANAGE',
  BOX_QR_MANAGE: 'BOX_QR_MANAGE',
  BOX_PUBLIC_ACCESS: 'BOX_PUBLIC_ACCESS',
  INSPECTION_DAILY_SUBMIT: 'INSPECTION_DAILY_SUBMIT',
  INSPECTION_REVIEW: 'INSPECTION_REVIEW',
  INSPECTION_RECORD_VIEW: 'INSPECTION_RECORD_VIEW',
  RECTIFICATION_VIEW: 'RECTIFICATION_VIEW',
  RECTIFICATION_REVIEW: 'RECTIFICATION_REVIEW',
  SUMMARY_VIEW: 'SUMMARY_VIEW',
  SUMMARY_EXPORT: 'SUMMARY_EXPORT',
  PERMISSION_MANAGE: 'PERMISSION_MANAGE',
};

const currentMonth = () => {
  const now = new Date();
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}`;
};

const dateAfterDays = (days) => {
  const date = new Date();
  date.setDate(date.getDate() + days);
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
};

const formatDateTime = (value) => {
  if (!value) return '-';
  return String(value).replace('T', ' ').slice(0, 16);
};

const getProjectRoleCode = (user, projectId) => {
  if (!user) return '';
  if (Number(user.id || user.userId) === 1) return 'PLATFORM_ADMIN';
  if ((user.roles || []).includes('PLATFORM_ADMIN')) return 'PLATFORM_ADMIN';
  const item = (user.projectRoles || []).find(role => Number(role.projectId) === Number(projectId));
  return item?.projectRoleCode || '';
};

const isPlatformUser = (user) => Number(user?.id || user?.userId) === 1 || (user?.roles || []).includes('PLATFORM_ADMIN');

const getInspectionPermissionCodes = (user, projectId) => {
  if (isPlatformUser(user)) {
    return Object.values(INSPECTION_PERMISSION_CODES);
  }
  const item = (user?.projectRoles || []).find(role => Number(role.projectId) === Number(projectId));
  return item?.permissionCodes || [];
};

const hasInspectionPermission = (user, projectId, code) => getInspectionPermissionCodes(user, projectId).includes(code);

const canManageProjectMembersByUser = (user, projectId) => {
  return isPlatformUser(user) || hasInspectionPermission(user, projectId, INSPECTION_PERMISSION_CODES.PERMISSION_MANAGE);
};

const getStatusColor = (status, T) => {
  if (['ACTIVE', 'CHECKED', 'COMPLETED', 'REVIEW_PASSED', 'PASSED', 'CLOSED', 'NORMAL'].includes(status)) return T.success;
  if (['UNCHECKED', 'REVIEW_PENDING', 'PENDING'].includes(status)) return T.warning;
  if (['ABNORMAL', 'REVIEW_REJECTED', 'RECTIFICATION_PENDING', 'REJECTED', 'INACTIVE', 'REMOVED'].includes(status)) return T.danger;
  return T.textMuted;
};

function InspectionPill({ children, status, theme: T }) {
  const color = getStatusColor(status, T);
  return (
    <span style={{
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      minWidth: 44,
      padding: '2px 7px',
      borderRadius: 999,
      fontSize: 10,
      fontWeight: 700,
      color,
      background: `${color}1f`,
      border: `1px solid ${color}66`,
      whiteSpace: 'nowrap',
    }}>{children}</span>
  );
}

function InspectionEmpty({ text, theme: T }) {
  return (
    <div style={{
      border: `1px dashed ${T.borderColor}`,
      borderRadius: 8,
      padding: 26,
      textAlign: 'center',
      color: T.textMuted,
      fontSize: 12,
      background: T.surface2,
    }}>{text}</div>
  );
}

function InspectionPhotoStrip({ fileIds = [], theme: T }) {
  const [urls, setUrls] = useState({});

  useEffect(() => {
    let disposed = false;
    const objectUrls = [];
    async function load() {
      const next = {};
      for (const id of fileIds.filter(Boolean)) {
        try {
          const url = await downloadFileAsObjectUrl(id);
          if (disposed) {
            URL.revokeObjectURL(url);
          } else {
            next[id] = url;
            objectUrls.push(url);
          }
        } catch (err) {
          console.error('巡检照片下载失败', err);
        }
      }
      if (!disposed) setUrls(next);
    }
    load();
    return () => {
      disposed = true;
      objectUrls.forEach(url => URL.revokeObjectURL(url));
    };
  }, [fileIds.join(',')]);

  if (!fileIds.length) {
    return <div style={{ color: T.textMuted, fontSize: 12 }}>暂无照片</div>;
  }

  return (
    <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
      {fileIds.map((id, index) => (
        <div key={`${id}-${index}`} style={{
          width: 92,
          height: 70,
          borderRadius: 6,
          border: `1px solid ${T.borderColor}`,
          background: T.surface2,
          overflow: 'hidden',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: T.textMuted,
          fontSize: 11,
        }}>
          {urls[id] ? (
            <img src={urls[id]} alt={`现场照片${index + 1}`} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
          ) : (
            <span>文件 #{id}</span>
          )}
        </div>
      ))}
    </div>
  );
}

function PermissionCollapse({ title, subtitle, meta, defaultOpen = true, theme: T, children }) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <section style={{ background: T.cardBg, border: `1px solid ${T.borderColor}`, borderRadius: 7, overflow: 'hidden', flexShrink: 0 }}>
      <button
        type="button"
        aria-expanded={open}
        onClick={() => setOpen(value => !value)}
        style={{
          width: '100%',
          minHeight: 58,
          padding: '10px 12px',
          border: 0,
          borderBottom: open ? `1px solid ${T.borderColor}` : 0,
          background: T.cardBg,
          color: T.textPrimary,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 12,
          cursor: 'pointer',
          textAlign: 'left',
        }}
      >
        <span style={{ minWidth: 0 }}>
          <span style={{ display: 'block', fontSize: 13, color: T.textPrimary, fontWeight: 850 }}>{title}</span>
          {subtitle && <span style={{ display: 'block', color: T.textMuted, fontSize: 10, marginTop: 3 }}>{subtitle}</span>}
        </span>
        <span style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 9, flexShrink: 0 }}>
          {meta}
          <span style={{ color: T.textMuted, fontSize: 10, whiteSpace: 'nowrap' }}>{open ? '收起' : '展开'}</span>
          <span aria-hidden="true" style={{ color: T.textMuted, fontSize: 18, lineHeight: 1, transform: `rotate(${open ? 180 : 0}deg)`, transition: 'transform 180ms ease' }}>⌄</span>
        </span>
      </button>
      <div hidden={!open}>{children}</div>
    </section>
  );
}

function InspectionBackendPanel({ projectId, theme: T, activeTab, currentUser, onTabChange }) {
  const [boxes, setBoxes] = useState([]);
  const [records, setRecords] = useState([]);
  const [rectifications, setRectifications] = useState([]);
  const [members, setMembers] = useState([]);
  const [userOptions, setUserOptions] = useState([]);
  const [permissionTemplates, setPermissionTemplates] = useState([]);
  const [permissionCatalog, setPermissionCatalog] = useState([]);
  const [summary, setSummary] = useState(null);
  const [todos, setTodos] = useState([]);
  const [inspectionSetting, setInspectionSetting] = useState({ dailyCutoffTime: '18:00', preDueReminderMinutes: 60, reviewDueHours: 24, rectificationDays: 3 });
  const [wechatApplications, setWechatApplications] = useState([]);
  const [wechatApplicationTotal, setWechatApplicationTotal] = useState(0);
  const [pendingWechatApplicationTotal, setPendingWechatApplicationTotal] = useState(0);
  const [wechatUsersPage, setWechatUsersPage] = useState({ records: [], total: 0, page: 1, size: 20 });
  const [permissionUserTab, setPermissionUserTab] = useState('users');
  const [permissionFilters, setPermissionFilters] = useState({ projectId: '', keyword: '', applicationStatus: 'PENDING', bindingStatus: '', projectAccessStatus: '', projectRoleCode: '', permissionTemplateId: '', pageNo: 1 });
  const [permissionLoading, setPermissionLoading] = useState(false);
  const [selectedWechatApplication, setSelectedWechatApplication] = useState(null);
  const [wechatApprovalForm, setWechatApprovalForm] = useState({ accountMode: 'EXISTING', userId: '', projectRoleCode: 'USER', permissionTemplateId: '', comment: '同意加入当前项目' });
  const [selectedWechatUser, setSelectedWechatUser] = useState(null);
  const [loading, setLoading] = useState(false);
  const [errorText, setErrorText] = useState('');
  const [month, setMonth] = useState(currentMonth());
  const [boxKeyword, setBoxKeyword] = useState('');
  const [boxStatus, setBoxStatus] = useState('');
  const [recordStatus, setRecordStatus] = useState('');
  const [reviewScope, setReviewScope] = useState('');
  const [reviewOverdueFilter, setReviewOverdueFilter] = useState('');
  const [rectificationStatus, setRectificationStatus] = useState('');
  const [summaryBoxId, setSummaryBoxId] = useState('');
  const [recordInspectorId, setRecordInspectorId] = useState('');
  const [recordResult, setRecordResult] = useState('');
  const [selectedRecord, setSelectedRecord] = useState(null);
  const [selectedRectification, setSelectedRectification] = useState(null);
  const [rectificationFeedback, setRectificationFeedback] = useState('');
  const [rectificationFiles, setRectificationFiles] = useState([]);
  const [rectificationSubmitting, setRectificationSubmitting] = useState(false);
  const [selectedBox, setSelectedBox] = useState(null);
  const [qrLabelLoading, setQrLabelLoading] = useState(false);
  const [qrLogs, setQrLogs] = useState([]);
  const [qrLogsLoading, setQrLogsLoading] = useState(false);
  const [editingBox, setEditingBox] = useState(null);
  const [showImportModal, setShowImportModal] = useState(false);
  const [importFile, setImportFile] = useState(null);
  const [importResult, setImportResult] = useState(null);
  const [importBusy, setImportBusy] = useState(false);
  const [memberKeyword, setMemberKeyword] = useState('');
  const [memberForm, setMemberForm] = useState({ userId: '', projectRoleCode: 'USER', permissionTemplateId: '' });
  const [showCreateUserModal, setShowCreateUserModal] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState(null);
  const [templateForm, setTemplateForm] = useState({
    templateName: '',
    templateCode: '',
    description: '',
    permissionCodes: [],
    enabled: 1,
  });
  const [userForm, setUserForm] = useState({
    username: '',
    realName: '',
    phone: '',
    email: '',
    password: 'admin123',
    globalRoleCode: 'USER',
    projectRoleCode: 'USER',
    permissionTemplateId: '',
  });
  const [boxForm, setBoxForm] = useState({
    boxCode: '',
    boxName: '',
    installLocation: '',
    responsibleElectricianId: '',
    responsibleElectricianName: '',
    safetyManagerId: '',
    safetyManagerName: '',
    qrCode: '',
    status: 'ACTIVE',
    qrStatus: 'BOUND',
    publicAccessEnabled: 1,
    remark: '',
  });

  const canManageMembers = canManageProjectMembersByUser(currentUser, projectId);
  const isPlatformAdmin = isPlatformUser(currentUser);

  const loadInspectionData = useCallback(async () => {
    if (!projectId) return;
    setLoading(true);
    setErrorText('');
    try {
      const [boxRes, recordRes, summaryRes, memberRes, userRes, settingRes] = await Promise.all([
        getElectricBoxList({ projectId, status: boxStatus || undefined }),
        getInspectionRecords({
          projectId,
          status: 'COMPLETED',
          month,
        }),
        getInspectionSummary({ projectId, month, boxId: summaryBoxId || undefined }),
        getProjectMembers(projectId),
        canManageMembers ? getProjectUserOptions(projectId, memberKeyword || undefined) : Promise.resolve({ code: 200, data: [] }),
        getProjectInspectionSetting(projectId),
      ]);

      if (boxRes.code === 200) setBoxes(boxRes.data || []);
      if (recordRes.code === 200) setRecords(recordRes.data || []);
      setRectifications([]);
      if (summaryRes.code === 200) setSummary(summaryRes.data || null);
      if (memberRes.code === 200) setMembers(memberRes.data || []);
      if (userRes.code === 200) setUserOptions(userRes.data || []);
      setTodos([]);
      if (settingRes.code === 200 && settingRes.data) setInspectionSetting(settingRes.data);
    } catch (err) {
      console.error('加载电箱巡检后台数据失败', err);
      setErrorText(err.message || '电箱巡检数据加载失败');
    } finally {
      setLoading(false);
    }
  }, [projectId, boxStatus, month, summaryBoxId, memberKeyword, canManageMembers]);

  useEffect(() => {
    loadInspectionData();
  }, [loadInspectionData]);

  const loadPermissionData = useCallback(async () => {
    if (activeTab !== 'permission' || !canManageMembers) return;
    setPermissionLoading(true);
    try {
      const scopedProjectId = isPlatformAdmin
        ? (permissionFilters.projectId ? Number(permissionFilters.projectId) : undefined)
        : projectId;
      const applicationStatus = permissionUserTab === 'history' ? undefined : 'PENDING';
      const [templateRes, catalogRes, applicationRes, pendingApplicationRes, userRes] = await Promise.all([
        getInspectionPermissionTemplates(),
        getInspectionPermissionCatalog(),
        getWechatAccessApplications({
          projectId: scopedProjectId,
          status: applicationStatus,
          keyword: permissionFilters.keyword || undefined,
          pageNo: 1,
          pageSize: 100,
        }),
        permissionUserTab === 'history'
          ? getWechatAccessApplications({
            projectId: scopedProjectId,
            status: 'PENDING',
            keyword: permissionFilters.keyword || undefined,
            pageNo: 1,
            pageSize: 1,
          })
          : Promise.resolve(null),
        getWechatUsers({
          projectId: scopedProjectId,
          keyword: permissionFilters.keyword || undefined,
          bindingStatus: permissionFilters.bindingStatus || undefined,
          projectAccessStatus: permissionFilters.projectAccessStatus || undefined,
          projectRoleCode: permissionFilters.projectRoleCode || undefined,
          permissionTemplateId: permissionFilters.permissionTemplateId || undefined,
          pageNo: permissionFilters.pageNo,
          pageSize: 20,
        }),
      ]);
      if (templateRes.code === 200) setPermissionTemplates(templateRes.data || []);
      if (catalogRes.code === 200) setPermissionCatalog(catalogRes.data || []);
      if (applicationRes.code === 200) {
        const applicationRecords = applicationRes.data?.records || [];
        const visibleApplications = permissionUserTab === 'history'
          ? applicationRecords.filter(item => item.status !== 'PENDING')
          : applicationRecords.filter(item => item.status === 'PENDING');
        setWechatApplications(visibleApplications);
        setWechatApplicationTotal(permissionUserTab === 'history' ? visibleApplications.length : (applicationRes.data?.total || 0));
        if (permissionUserTab !== 'history') setPendingWechatApplicationTotal(applicationRes.data?.total || 0);
      }
      if (pendingApplicationRes?.code === 200) setPendingWechatApplicationTotal(pendingApplicationRes.data?.total || 0);
      if (userRes.code === 200) setWechatUsersPage(userRes.data || { records: [], total: 0, page: 1, size: 20 });
    } catch (err) {
      console.error('加载小程序用户与权限失败', err);
      setErrorText(err.message || '小程序用户与权限加载失败');
    } finally {
      setPermissionLoading(false);
    }
  }, [activeTab, canManageMembers, isPlatformAdmin, projectId, permissionFilters, permissionUserTab]);

  useEffect(() => {
    setPermissionFilters(prev => ({ ...prev, projectId: isPlatformAdmin ? '' : String(projectId || ''), pageNo: 1 }));
  }, [isPlatformAdmin, projectId]);

  useEffect(() => {
    loadPermissionData();
  }, [loadPermissionData]);

  const filteredBoxes = boxes.filter(box => {
    if (!boxKeyword.trim()) return true;
    const text = `${box.boxCode || ''}${box.boxName || ''}${box.installLocation || ''}${box.responsibleElectricianName || ''}`;
    return text.includes(boxKeyword.trim());
  });

  const activeBoxes = boxes.filter(box => box.status === 'ACTIVE' && box.inspectionRequired !== false);
  const checkedToday = boxes.filter(box => box.todayStatus === 'CHECKED').length;
  const abnormalToday = boxes.filter(box => box.todayStatus === 'ABNORMAL').length;
  const completedToday = checkedToday + abnormalToday;
  const pendingReview = records.filter(record => record.status === 'REVIEW_PENDING').length;
  const openRectifications = rectifications.filter(item => item.status !== 'CLOSED').length;
  const overdueRectifications = rectifications.filter(item => {
    if (!item.deadline || item.status === 'CLOSED') return false;
    return new Date(item.deadline) < new Date(new Date().toISOString().slice(0, 10));
  }).length;
  const memberDisplayName = (member) => member?.realName || member?.username || '';
  const safetyMembers = members.filter(member => ['PROJECT_ADMIN', 'SAFETY_ADMIN'].includes(member.projectRoleCode));
  const reviewMembers = members.filter(member => (member.permissionCodes || []).includes(INSPECTION_PERMISSION_CODES.INSPECTION_REVIEW));
  const activePermissionTemplates = permissionTemplates.filter(template => Number(template.enabled ?? 1) === 1);
  const templateByCode = (code) => permissionTemplates.find(template => template.templateCode === code);
  const defaultTemplateIdForRole = (roleCode) => templateByCode(roleCode)?.id || templateByCode('USER')?.id || '';
  const boxManageAllowed = hasInspectionPermission(currentUser, projectId, INSPECTION_PERMISSION_CODES.BOX_MANAGE);
  const qrManageAllowed = hasInspectionPermission(currentUser, projectId, INSPECTION_PERMISSION_CODES.BOX_QR_MANAGE);
  const publicAccessAllowed = hasInspectionPermission(currentUser, projectId, INSPECTION_PERMISSION_CODES.BOX_PUBLIC_ACCESS);
  const inspectionReviewAllowed = hasInspectionPermission(currentUser, projectId, INSPECTION_PERMISSION_CODES.INSPECTION_REVIEW);
  const rectificationReviewAllowed = hasInspectionPermission(currentUser, projectId, INSPECTION_PERMISSION_CODES.RECTIFICATION_REVIEW);
  const summaryExportAllowed = hasInspectionPermission(currentUser, projectId, INSPECTION_PERMISSION_CODES.SUMMARY_EXPORT);
  const dailySubmitAllowed = hasInspectionPermission(currentUser, projectId, INSPECTION_PERMISSION_CODES.INSPECTION_DAILY_SUBMIT);
  const singleBoxExportAllowed = Boolean(summaryBoxId) && (summaryExportAllowed || dailySubmitAllowed);
  const recordInspectors = Array.from(new Map((summary?.records || [])
    .filter(record => record.inspectorId)
    .map(record => [String(record.inspectorId), { id: String(record.inspectorId), name: record.inspectorName || `用户${record.inspectorId}` }])).values());
  const filteredSummaryRecords = (summary?.records || []).filter(record => {
    if (recordInspectorId && String(record.inspectorId || '') !== recordInspectorId) return false;
    if (recordResult === 'ABNORMAL' && Number(record.abnormalCount || 0) <= 0) return false;
    if (recordResult === 'NORMAL' && Number(record.abnormalCount || 0) > 0) return false;
    return true;
  });
  const currentUserId = Number(currentUser?.id || currentUser?.userId || 0);
  const canCompleteRectification = (item) => {
    if (!item || !['PENDING', 'REJECTED'].includes(item.status)) return false;
    return rectificationReviewAllowed || (currentUserId > 0 && Number(item.assigneeId || 0) === currentUserId);
  };

  const normalizeQrImageSource = (imageContent) => {
    const content = String(imageContent || '').trim();
    if (!content) return '';
    if (content.startsWith('data:image/')) return content;
    if (content.startsWith('<svg')) {
      return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(content)}`;
    }
    return content;
  };

  const buildQrLabelData = async (box) => {
    const unifiedRes = await getElectricBoxUnifiedCode(box.id);
    if (unifiedRes.code !== 200) throw new Error(unifiedRes.message || '统一巡检码生成失败');
    const data = unifiedRes.data || {};
    return {
      unifiedPayload: data.sceneCode,
      unifiedSvg: normalizeQrImageSource(data.imageContent),
      unifiedCodeType: data.codeType,
      unifiedHint: data.hint,
    };
  };

  const buildLabelHtml = (box, qrData) => `
    <section class="qr-label">
      <header>
        <strong>${box.boxCode || ''}</strong>
        <span>${box.boxName || '现场电箱'}</span>
      </header>
      <p>${box.installLocation || ''}</p>
      <div class="qr-grid">
        <div class="qr-block">
          ${String(qrData.unifiedSvg || '').startsWith('data:image/') ? `<img src="${qrData.unifiedSvg}" alt="统一电箱巡检码" />` : (qrData.unifiedSvg || '')}
          <b>统一电箱巡检码</b>
          <small>内部人员巡检 / 外部人员查看月度记录共用</small>
          <small>${qrData.unifiedPayload || ''}</small>
        </div>
      </div>
      <footer>请勿覆盖、撕毁或转贴。二维码换绑后旧码不可继续巡检。</footer>
    </section>
  `;

  const buildPrintDocument = (labels) => `
    <!doctype html>
    <html>
      <head>
        <meta charset="utf-8" />
        <title>电箱二维码贴纸</title>
        <style>
          * { box-sizing: border-box; }
          body { margin: 0; padding: 18px; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color: #111827; background: #f5f7fb; }
          .sheet { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
          .qr-label { break-inside: avoid; background: #fff; border: 1px solid #cfd8e5; border-radius: 8px; padding: 14px; min-height: 252px; }
          .qr-label header { display: flex; justify-content: space-between; gap: 10px; align-items: baseline; border-bottom: 1px solid #e5edf6; padding-bottom: 8px; }
          .qr-label strong { font-size: 22px; letter-spacing: 0; }
          .qr-label span { color: #334155; font-size: 13px; font-weight: 700; }
          .qr-label p { margin: 8px 0 10px; color: #475569; font-size: 12px; min-height: 18px; }
          .qr-grid { display: grid; grid-template-columns: 1fr; gap: 10px; }
          .qr-block { text-align: center; min-width: 0; }
          .qr-block svg, .qr-block img { width: 168px; height: 168px; object-fit: contain; border: 1px solid #e2e8f0; border-radius: 6px; }
          .qr-block b { display: block; margin-top: 5px; color: #0f766e; font-size: 12px; }
          .qr-block small { display: block; margin-top: 3px; color: #64748b; font-size: 8px; word-break: break-all; line-height: 1.25; }
          footer { margin-top: 8px; color: #94a3b8; font-size: 10px; }
          @media print {
            body { background: #fff; padding: 0; }
            .sheet { gap: 8mm; }
            .qr-label { border-color: #94a3b8; page-break-inside: avoid; }
          }
        </style>
      </head>
      <body><main class="sheet">${labels.join('')}</main></body>
    </html>
  `;

  const setBoxMember = (field, userIdValue) => {
    const userId = userIdValue ? Number(userIdValue) : '';
    const member = members.find(item => Number(item.userId) === Number(userId));
    if (field === 'responsible') {
      setBoxForm({
        ...boxForm,
        responsibleElectricianId: userId,
        responsibleElectricianName: memberDisplayName(member),
      });
      return;
    }
    setBoxForm({
      ...boxForm,
      safetyManagerId: userId,
      safetyManagerName: memberDisplayName(member),
    });
  };

  const openCreateTemplate = () => {
    setEditingTemplate({});
    setTemplateForm({
      templateName: '',
      templateCode: '',
      description: '',
      permissionCodes: [],
      enabled: 1,
    });
  };

  const openEditTemplate = (template) => {
    setEditingTemplate(template);
    setTemplateForm({
      templateName: template.templateName || '',
      templateCode: template.templateCode || '',
      description: template.description || '',
      permissionCodes: template.permissionCodes || [],
      enabled: Number(template.enabled ?? 1),
    });
  };

  const toggleTemplatePermission = (code) => {
    setTemplateForm(prev => {
      const exists = prev.permissionCodes.includes(code);
      return {
        ...prev,
        permissionCodes: exists
          ? prev.permissionCodes.filter(item => item !== code)
          : [...prev.permissionCodes, code],
      };
    });
  };

  const savePermissionTemplate = async () => {
    if (!templateForm.templateName.trim()) {
      alert('请填写权限角色名称');
      return;
    }
    if (!editingTemplate?.id && !templateForm.templateCode.trim()) {
      alert('请填写权限角色编码');
      return;
    }
    try {
      const payload = {
        templateName: templateForm.templateName.trim(),
        templateCode: templateForm.templateCode.trim(),
        description: templateForm.description.trim(),
        permissionCodes: templateForm.permissionCodes,
        enabled: Number(templateForm.enabled) === 1 ? 1 : 0,
      };
      const res = editingTemplate?.id
        ? await updateInspectionPermissionTemplate(editingTemplate.id, payload)
        : await createInspectionPermissionTemplate(payload);
      if (res.code !== 200) {
        alert(res.message || '保存权限角色失败');
        return;
      }
      setEditingTemplate(null);
      await Promise.all([loadPermissionData(), loadInspectionData()]);
    } catch (err) {
      console.error('保存权限角色失败', err);
      alert(err.message || '保存权限角色失败');
    }
  };

  const toggleTemplateStatus = async (template) => {
    try {
      const nextEnabled = Number(template.enabled ?? 1) !== 1;
      const res = await updateInspectionPermissionTemplateStatus(template.id, nextEnabled);
      if (res.code !== 200) {
        alert(res.message || '更新权限角色状态失败');
        return;
      }
      await Promise.all([loadPermissionData(), loadInspectionData()]);
    } catch (err) {
      console.error('更新权限角色状态失败', err);
      alert(err.message || '更新权限角色状态失败');
    }
  };

  const openCreateBox = () => {
    setEditingBox({});
    setBoxForm({
      boxCode: '',
      boxName: '',
      installLocation: '',
      responsibleElectricianId: '',
      responsibleElectricianName: '',
      safetyManagerId: '',
      safetyManagerName: '',
      qrCode: '',
      status: 'ACTIVE',
      qrStatus: 'BOUND',
      publicAccessEnabled: 1,
      remark: '',
    });
  };

  const openEditBox = (box) => {
    setEditingBox(box);
    setBoxForm({
      boxCode: box.boxCode || '',
      boxName: box.boxName || '',
      installLocation: box.installLocation || '',
      responsibleElectricianId: box.responsibleElectricianId || '',
      responsibleElectricianName: box.responsibleElectricianName || '',
      safetyManagerId: box.safetyManagerId || '',
      safetyManagerName: box.safetyManagerName || '',
      qrCode: box.qrCode || '',
      status: box.status || 'ACTIVE',
      qrStatus: box.qrStatus || 'BOUND',
      publicAccessEnabled: box.publicAccessEnabled ?? 1,
      remark: box.remark || '',
    });
  };

  const openQrPanel = async (box) => {
    setSelectedBox({ ...box, qrView: true });
    setQrLogs([]);
    setQrLabelLoading(true);
    try {
      const qrData = await buildQrLabelData(box);
      setSelectedBox(prev => prev && prev.id === box.id ? { ...prev, ...qrData, qrView: true } : prev);
    } catch (err) {
      console.error('二维码生成失败', err);
      alert(err.message || '二维码生成失败');
    } finally {
      setQrLabelLoading(false);
    }
  };

  const saveBox = async () => {
    if (!boxForm.boxCode.trim() || !boxForm.installLocation.trim()) {
      alert('请填写电箱编号和安装位置');
      return;
    }
    const payload = {
      ...boxForm,
      projectId,
      responsibleElectricianId: boxForm.responsibleElectricianId || null,
      safetyManagerId: boxForm.safetyManagerId || null,
      publicAccessEnabled: Number(boxForm.publicAccessEnabled) === 1 ? 1 : 0,
    };
    try {
      const res = editingBox?.id
        ? await updateElectricBox(editingBox.id, payload)
        : await createElectricBox(payload);
      if (res.code !== 200) {
        alert(res.message || '保存电箱失败');
        return;
      }
      setEditingBox(null);
      await loadInspectionData();
    } catch (err) {
      console.error('保存电箱失败', err);
      alert(err.message || '保存电箱失败');
    }
  };

  const disableBox = async (box) => {
    if (!window.confirm(`确认停用电箱「${box.boxCode}」？停用后小程序不能继续日检。`)) return;
    const reason = window.prompt('请输入停用原因（可留空）', '现场停用');
    if (reason === null) return;
    try {
      const res = await disableElectricBox(box.id, { reason });
      if (res.code !== 200) {
        alert(res.message || '停用失败');
        return;
      }
      await loadInspectionData();
    } catch (err) {
      console.error('停用电箱失败', err);
      alert(err.message || '停用失败');
    }
  };

  const removeBox = async (box) => {
    if (!window.confirm(`确认拆除电箱「${box.boxCode}」？拆除后会停用统一巡检码并关闭公开月表。`)) return;
    const reason = window.prompt('请输入拆除原因（可留空）', '现场拆除');
    if (reason === null) return;
    try {
      const res = await removeElectricBox(box.id, { reason });
      if (res.code !== 200) {
        alert(res.message || '拆除失败');
        return;
      }
      setSelectedBox(null);
      await loadInspectionData();
    } catch (err) {
      console.error('拆除电箱失败', err);
      alert(err.message || '拆除失败');
    }
  };

  const rebindQr = async (box) => {
    const reason = window.prompt('更换统一巡检码后旧码立即失效，请填写换码原因', '现场贴纸损坏补换');
    if (reason === null) return;
    try {
      const res = await rotateElectricBoxUnifiedCode(box.id, { reason });
      if (res.code !== 200) {
        alert(res.message || '换绑失败');
        return;
      }
      setSelectedBox(null);
      await loadInspectionData();
    } catch (err) {
      console.error('换绑二维码失败', err);
      alert(err.message || '换绑失败');
    }
  };

  const toggleInspectionScope = async (box) => {
    const included = !Boolean(box.inspectionRequired);
    const effectiveDate = window.prompt(`${included ? '纳入' : '移出'}日检的生效日期（YYYY-MM-DD）`, new Date().toISOString().slice(0, 10));
    if (!effectiveDate) return;
    const reason = window.prompt('请输入巡检范围变更原因', included ? '纳入每日巡检' : '移出每日巡检');
    if (reason === null) return;
    try {
      const res = await updateElectricBoxInspectionScope(box.id, { included, effectiveDate, reason });
      if (res.code !== 200) { alert(res.message || '巡检范围更新失败'); return; }
      await loadInspectionData();
    } catch (err) { alert(err.message || '巡检范围更新失败'); }
  };

  const saveInspectionSetting = async () => {
    try {
      const res = await updateProjectInspectionSetting(projectId, inspectionSetting);
      if (res.code !== 200) { alert(res.message || '巡检设置保存失败'); return; }
      setInspectionSetting(res.data);
      alert('项目巡检设置已保存');
    } catch (err) { alert(err.message || '巡检设置保存失败'); }
  };

  const reviewWechatApplication = async (application, approved) => {
    if (approved) {
      const accountMode = application.matchedUserId ? 'EXISTING' : 'CREATE';
      setSelectedWechatApplication(application);
      setWechatApprovalForm({
        accountMode,
        userId: application.matchedUserId ? String(application.matchedUserId) : '',
        projectRoleCode: 'USER',
        permissionTemplateId: String(defaultTemplateIdForRole('USER') || ''),
        comment: '同意加入当前项目',
      });
      try {
        const optionsRes = await getProjectUserOptions(application.projectId, application.phone || application.realName || undefined);
        if (optionsRes.code === 200) setUserOptions(optionsRes.data || []);
      } catch (err) {
        console.error('加载匹配账号失败', err);
      }
      return;
    }
    const comment = window.prompt('请输入拒绝原因', '资料不符合要求');
    if (comment === null || !comment.trim()) return;
    try {
      const res = await rejectWechatAccessApplication(application.id, { comment });
      if (res.code !== 200) { alert(res.message || '审批失败'); return; }
      await Promise.all([loadPermissionData(), loadInspectionData()]);
    } catch (err) { alert(err.message || '审批失败'); }
  };

  const submitWechatApproval = async () => {
    if (!selectedWechatApplication) return;
    if (!wechatApprovalForm.permissionTemplateId) { alert('请选择权限角色'); return; }
    if (!wechatApprovalForm.comment.trim()) { alert('请填写审批意见'); return; }
    if (wechatApprovalForm.accountMode === 'EXISTING' && !wechatApprovalForm.userId) { alert('请选择要绑定的已有账号'); return; }
    try {
      const res = await approveWechatAccessApplication(selectedWechatApplication.id, {
        accountMode: wechatApprovalForm.accountMode,
        userId: wechatApprovalForm.accountMode === 'EXISTING' ? Number(wechatApprovalForm.userId) : null,
        realName: selectedWechatApplication.realName,
        projectRoleCode: wechatApprovalForm.projectRoleCode,
        permissionTemplateId: Number(wechatApprovalForm.permissionTemplateId),
        comment: wechatApprovalForm.comment.trim(),
      });
      if (res.code !== 200) { alert(res.message || '审批失败'); return; }
      setSelectedWechatApplication(null);
      await Promise.all([loadPermissionData(), loadInspectionData()]);
    } catch (err) { alert(err.message || '审批失败'); }
  };

  const openWechatUserDetail = async (user) => {
    try {
      const res = await getWechatUserDetail(user.userId);
      if (res.code !== 200) { alert(res.message || '用户详情加载失败'); return; }
      setSelectedWechatUser(res.data);
    } catch (err) { alert(err.message || '用户详情加载失败'); }
  };

  const changeWechatBindingStatus = async (binding, status) => {
    if (!selectedWechatUser) return;
    const reason = window.prompt(status === 'DISABLED' ? '请输入停用微信登录原因' : '请输入恢复微信登录原因', status === 'DISABLED' ? '管理员暂停微信登录' : '管理员恢复微信登录');
    if (reason === null || !reason.trim()) return;
    try {
      const res = await updateWechatBindingStatus(selectedWechatUser.userId, binding.id, { status, reason });
      if (res.code !== 200) { alert(res.message || '微信绑定状态更新失败'); return; }
      await Promise.all([openWechatUserDetail({ userId: selectedWechatUser.userId }), loadPermissionData()]);
    } catch (err) { alert(err.message || '微信绑定状态更新失败'); }
  };

  const unbindWechat = async (binding) => {
    if (!selectedWechatUser) return;
    const reason = window.prompt('解绑后用户需重新申请绑定，请输入解绑原因', '更换微信账号');
    if (!reason?.trim()) return;
    if (!window.confirm('确认解绑该用户微信？历史巡检和项目授权会保留。')) return;
    try {
      const res = await unbindWechatUser(selectedWechatUser.userId, binding.id, { reason });
      if (res.code !== 200) { alert(res.message || '解绑失败'); return; }
      await Promise.all([openWechatUserDetail({ userId: selectedWechatUser.userId }), loadPermissionData()]);
    } catch (err) { alert(err.message || '解绑失败'); }
  };

  const changeWechatProjectAccess = async (projectAccess, status) => {
    if (!selectedWechatUser) return;
    const reason = window.prompt(status === 'DISABLED' ? '请输入暂停项目访问原因' : '请输入恢复说明（可留空）', status === 'DISABLED' ? '暂停当前项目访问' : '恢复当前项目访问');
    if (reason === null || (status === 'DISABLED' && !reason.trim())) return;
    try {
      const res = await updateProjectMemberStatus(projectAccess.projectId, selectedWechatUser.userId, { status, reason });
      if (res.code !== 200) { alert(res.message || '项目访问状态更新失败'); return; }
      await Promise.all([openWechatUserDetail({ userId: selectedWechatUser.userId }), loadPermissionData(), loadInspectionData()]);
    } catch (err) { alert(err.message || '项目访问状态更新失败'); }
  };

  const updateWechatProjectPermission = async (projectAccess, patch) => {
    if (!selectedWechatUser) return;
    try {
      const res = await updateProjectMember(projectAccess.projectId, selectedWechatUser.userId, {
        projectRoleCode: patch.projectRoleCode || projectAccess.projectRoleCode || 'USER',
        permissionTemplateId: Number(patch.permissionTemplateId || projectAccess.permissionTemplateId),
      });
      if (res.code !== 200) { alert(res.message || '项目权限修改失败'); return; }
      await Promise.all([openWechatUserDetail({ userId: selectedWechatUser.userId }), loadPermissionData(), loadInspectionData()]);
    } catch (err) { alert(err.message || '项目权限修改失败'); }
  };

  const addWechatProjectAccess = async () => {
    if (!selectedWechatUser) return;
    const availableProjects = (currentUser?.projectRoles || []).filter(project => !(selectedWechatUser.projects || []).some(item => Number(item.projectId) === Number(project.projectId)));
    if (!availableProjects.length) { alert('当前没有可新增授权的项目'); return; }
    const text = availableProjects.map(item => `${item.projectId}. ${item.projectName || item.shortName}`).join('\n');
    const projectInput = window.prompt(`请输入要授权的项目ID：\n${text}`, String(availableProjects[0].projectId));
    if (!projectInput) return;
    const target = availableProjects.find(item => Number(item.projectId) === Number(projectInput));
    if (!target) { alert('请选择列表中的项目'); return; }
    const templateId = defaultTemplateIdForRole('USER');
    if (!templateId) { alert('没有可用的巡检员权限角色'); return; }
    try {
      const res = await saveProjectMember({ projectId: Number(target.projectId), userId: selectedWechatUser.userId, projectRoleCode: 'USER', permissionTemplateId: Number(templateId) });
      if (res.code !== 200) { alert(res.message || '新增项目授权失败'); return; }
      await Promise.all([openWechatUserDetail({ userId: selectedWechatUser.userId }), loadPermissionData(), loadInspectionData()]);
    } catch (err) { alert(err.message || '新增项目授权失败'); }
  };

  const openQrLogs = async (box) => {
    setSelectedBox({ ...box, logView: true });
    setQrLogsLoading(true);
    try {
      const res = await getElectricBoxQrLogs(box.id);
      setQrLogs(res.code === 200 ? (res.data || []) : []);
      if (res.code !== 200) alert(res.message || '二维码日志加载失败');
    } catch (err) {
      console.error('二维码日志加载失败', err);
      alert(err.message || '二维码日志加载失败');
    } finally {
      setQrLogsLoading(false);
    }
  };

  const downloadQrLabel = async (box) => {
    try {
      const qrData = box.unifiedSvg ? box : await buildQrLabelData(box);
      const html = buildPrintDocument([buildLabelHtml(box, qrData)]);
      const blob = new Blob([html], { type: 'text/html;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${box.boxCode || 'electric-box'}-二维码贴纸.html`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
      await recordElectricBoxQrPrint(box.id, { qrTypes: ['UNIFIED'], reason: '下载统一巡检码贴纸' }).catch(() => undefined);
    } catch (err) {
      console.error('下载二维码贴纸失败', err);
      alert(err.message || '下载二维码贴纸失败');
    }
  };

  const printQrLabels = async (targetBoxes) => {
    const candidates = (targetBoxes || []).filter(box => box.status !== 'REMOVED');
    if (!candidates.length) {
      alert('当前没有可打印的电箱');
      return;
    }
    const printWindow = window.open('', '_blank');
    if (!printWindow) {
      alert('浏览器阻止了打印窗口，请允许弹窗后重试');
      return;
    }
    printWindow.document.write('<p style="font-family:sans-serif;padding:20px">正在生成二维码贴纸...</p>');
    try {
      const labels = [];
      for (const box of candidates) {
        const qrData = await buildQrLabelData(box);
        labels.push(buildLabelHtml(box, qrData));
        await recordElectricBoxQrPrint(box.id, { qrTypes: ['UNIFIED'], reason: '批量打印统一巡检码贴纸' }).catch(() => undefined);
      }
      printWindow.document.open();
      printWindow.document.write(buildPrintDocument(labels));
      printWindow.document.close();
      printWindow.focus();
      setTimeout(() => printWindow.print(), 300);
    } catch (err) {
      printWindow.close();
      console.error('批量打印二维码失败', err);
      alert(err.message || '批量打印二维码失败');
    }
  };

  const downloadImportTemplate = async () => {
    try {
      const blob = await downloadElectricBoxImportTemplate();
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = '电箱台账导入模板.xlsx';
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    } catch (err) {
      console.error('下载导入模板失败', err);
      alert(err.message || '下载导入模板失败');
    }
  };

  const submitImport = async (dryRun) => {
    if (!importFile) {
      alert('请先选择 Excel 文件');
      return;
    }
    setImportBusy(true);
    try {
      const res = await importElectricBoxes(projectId, importFile, dryRun);
      if (res.code !== 200) {
        alert(res.message || '导入失败');
        return;
      }
      setImportResult(res.data);
      if (!dryRun && Number(res.data?.errorRows || 0) === 0) {
        await loadInspectionData();
        alert('电箱台账导入完成');
      }
    } catch (err) {
      console.error('导入电箱台账失败', err);
      alert(err.message || '导入电箱台账失败');
    } finally {
      setImportBusy(false);
    }
  };

  const togglePublicAccess = async (box, enabled) => {
    try {
      const res = await setElectricBoxPublicAccess(box.id, enabled);
      if (res.code !== 200) {
        alert(res.message || '公开扫码设置失败');
        return;
      }
      await loadInspectionData();
      setSelectedBox(prev => prev && prev.id === box.id ? { ...prev, publicAccessEnabled: enabled ? 1 : 0 } : prev);
    } catch (err) {
      console.error('公开扫码设置失败', err);
      alert(err.message || '公开扫码设置失败');
    }
  };

  const addProjectMember = async () => {
    if (!memberForm.userId) {
      alert('请选择用户');
      return;
    }
    const memberTemplateId = memberForm.permissionTemplateId || defaultTemplateIdForRole(memberForm.projectRoleCode);
    try {
      const res = await saveProjectMember({
        projectId,
        userId: Number(memberForm.userId),
        projectRoleCode: memberForm.projectRoleCode,
        permissionTemplateId: memberTemplateId ? Number(memberTemplateId) : null,
      });
      if (res.code !== 200) {
        alert(res.message || '添加成员失败');
        return;
      }
      setMemberForm({ userId: '', projectRoleCode: 'USER', permissionTemplateId: '' });
      await loadInspectionData();
    } catch (err) {
      console.error('添加成员失败', err);
      alert(err.message || '添加成员失败');
    }
  };

  const submitCreateUser = async () => {
    if (!userForm.username.trim()) {
      alert('请填写用户名');
      return;
    }
    if (!userForm.realName.trim()) {
      alert('请填写姓名');
      return;
    }
    const userTemplateId = userForm.permissionTemplateId || defaultTemplateIdForRole(userForm.projectRoleCode);
    try {
      const res = await createProjectUser({
        projectId,
        username: userForm.username.trim(),
        realName: userForm.realName.trim(),
        phone: userForm.phone.trim(),
        email: userForm.email.trim(),
        password: userForm.password.trim() || 'admin123',
        globalRoleCode: userForm.globalRoleCode,
        projectRoleCode: userForm.projectRoleCode,
        permissionTemplateId: userTemplateId ? Number(userTemplateId) : null,
      });
      if (res.code !== 200) {
        alert(res.message || '新增用户失败');
        return;
      }
      setShowCreateUserModal(false);
      setUserForm({
        username: '',
        realName: '',
        phone: '',
        email: '',
        password: 'admin123',
        globalRoleCode: 'USER',
        projectRoleCode: 'USER',
        permissionTemplateId: '',
      });
      await loadInspectionData();
    } catch (err) {
      console.error('新增用户失败', err);
      alert(err.message || '新增用户失败');
    }
  };

  const changeProjectMemberRole = async (member, projectRoleCode) => {
    try {
      const res = await updateProjectMember(projectId, member.userId, {
        projectRoleCode,
        permissionTemplateId: member.permissionTemplateId || defaultTemplateIdForRole(projectRoleCode) || null,
      });
      if (res.code !== 200) {
        alert(res.message || '修改授权失败');
        return;
      }
      await loadInspectionData();
    } catch (err) {
      console.error('修改授权失败', err);
      alert(err.message || '修改授权失败');
    }
  };

  const changeProjectMemberTemplate = async (member, permissionTemplateId) => {
    try {
      const res = await updateProjectMember(projectId, member.userId, {
        projectRoleCode: member.projectRoleCode || 'USER',
        permissionTemplateId: permissionTemplateId ? Number(permissionTemplateId) : null,
      });
      if (res.code !== 200) {
        alert(res.message || '分配权限角色失败');
        return;
      }
      await loadInspectionData();
    } catch (err) {
      console.error('分配权限角色失败', err);
      alert(err.message || '分配权限角色失败');
    }
  };

  const deleteProjectMember = async (member) => {
    if (!window.confirm(`确认移除「${memberDisplayName(member)}」的当前项目授权？`)) return;
    try {
      const res = await removeProjectMember(projectId, member.userId);
      if (res.code !== 200) {
        alert(res.message || '移除成员失败');
        return;
      }
      await loadInspectionData();
    } catch (err) {
      console.error('移除成员失败', err);
      alert(err.message || '移除成员失败');
    }
  };

  const openRecordDetail = async (record) => {
    try {
      const [res] = await Promise.all([getInspectionRecord(record.id)]);
      const detail = res.code === 200 ? res.data : record;
      setSelectedRecord({ ...detail, status: 'COMPLETED', reviewLogs: [] });
    } catch (err) {
      console.error('获取巡检记录详情失败', err);
      setSelectedRecord(record);
    }
  };

  const reviewRecord = async (record, action) => {
    const comment = action === 'PASS'
      ? 'PC后台复核通过'
      : window.prompt(action === 'REJECT' ? '请输入退回原因' : '请输入整改问题说明', record.remark || '');
    if (action !== 'PASS' && comment === null) return;
    let requirement;
    let problemCategory;
    let assigneeId;
    let assigneeName;
    let deadline;
    if (action === 'RECTIFY') {
      const categoryPrompt = SPOT_CHECK_CATEGORIES
        .map((item, index) => `${index + 1}. ${item.label}`)
        .join('\n');
      const categoryInput = window.prompt(`请选择问题分类序号：\n${categoryPrompt}`, '1');
      if (categoryInput === null) return;
      const category = SPOT_CHECK_CATEGORIES[Number(categoryInput) - 1] || DEFAULT_SPOT_CHECK_CATEGORY;
      problemCategory = category.value;
      if (!members.length) {
        alert('当前项目暂无成员，无法指定整改人。请先在权限配置中添加项目成员。');
        return;
      }
      const memberPrompt = members
        .map(member => `${member.userId}. ${memberDisplayName(member)}（${PROJECT_ROLE_TEXT[member.projectRoleCode] || member.projectRoleCode || '项目成员'}）`)
        .join('\n');
      const defaultAssignee = members.find(member => member.projectRoleCode === 'USER') || members[0];
      const assigneeInput = window.prompt(`请输入整改人用户ID：\n${memberPrompt}`, defaultAssignee ? String(defaultAssignee.userId) : '');
      if (assigneeInput === null) return;
      const member = members.find(item => Number(item.userId) === Number(assigneeInput));
      if (!member) {
        alert('整改人必须从当前项目成员中选择');
        return;
      }
      assigneeId = member.userId;
      assigneeName = memberDisplayName(member);
      deadline = window.prompt('请输入整改截止日期（YYYY-MM-DD）', dateAfterDays(3));
      if (deadline === null) return;
      deadline = deadline.trim() || dateAfterDays(3);
      requirement = window.prompt('请输入整改要求', category.template);
      if (requirement === null) return;
    }
    try {
      const res = await reviewInspectionRecord(record.id, {
        reviewAction: action,
        comment,
        requirement,
        problemCategory,
        assigneeId,
        assigneeName,
        deadline,
      });
      if (res.code !== 200) {
        alert(res.message || '复核失败');
        return;
      }
      setSelectedRecord(null);
      await loadInspectionData();
    } catch (err) {
      console.error('复核失败', err);
      alert(err.message || '复核失败');
    }
  };

  const assignRecordReviewer = async (record, reviewerId) => {
    const nextReviewerId = reviewerId ? Number(reviewerId) : null;
    const reviewer = reviewMembers.find(member => Number(member.userId) === Number(nextReviewerId));
    try {
      const res = await assignInspectionReviewer(record.id, {
        reviewerId: nextReviewerId,
        comment: nextReviewerId ? `PC后台改派给 ${memberDisplayName(reviewer)}` : 'PC后台转入未分配复核池',
      });
      if (res.code !== 200) {
        alert(res.message || '改派复核人失败');
        return;
      }
      setSelectedRecord(res.data);
      await loadInspectionData();
    } catch (err) {
      console.error('改派复核人失败', err);
      alert(err.message || '改派复核人失败');
    }
  };

  const openRectificationDetail = async (rectification) => {
    try {
      const res = await getInspectionRectification(rectification.id);
      const detail = res.code === 200 ? res.data : rectification;
      setSelectedRectification(detail);
      setRectificationFeedback(detail?.feedback || '');
      setRectificationFiles([]);
    } catch (err) {
      console.error('获取整改详情失败', err);
      setSelectedRectification(rectification);
      setRectificationFeedback(rectification?.feedback || '');
      setRectificationFiles([]);
    }
  };

  const closeRectificationDrawer = () => {
    setSelectedRectification(null);
    setRectificationFeedback('');
    setRectificationFiles([]);
    setRectificationSubmitting(false);
  };

  const submitRectificationFeedback = async (rectification) => {
    if (!rectification || rectificationSubmitting) return;
    const feedback = rectificationFeedback.trim();
    if (!feedback) {
      alert('请填写整改说明');
      return;
    }
    const existingPhotoIds = rectification.rectificationPhotoFileIds || [];
    if (!rectificationFiles.length && !existingPhotoIds.length) {
      alert('请上传至少 1 张整改照片');
      return;
    }
    setRectificationSubmitting(true);
    try {
      const uploadedIds = [];
      for (const file of rectificationFiles) {
        const uploadRes = await uploadFile({
          file,
          projectId: rectification.projectId || projectId,
          fileName: file.name,
          fileType: 'RECTIFICATION_PHOTO',
          businessType: 'inspection_rectification',
          businessId: rectification.id,
          remark: '整改反馈照片',
        });
        if (uploadRes.code !== 200 || !uploadRes.data?.id) {
          alert(uploadRes.message || `${file.name} 上传失败`);
          return;
        }
        uploadedIds.push(uploadRes.data.id);
      }
      const photoFileIds = [...existingPhotoIds, ...uploadedIds];
      const res = await completeInspectionRectification(rectification.id, { feedback, photoFileIds });
      if (res.code !== 200) {
        alert(res.message || '提交整改失败');
        return;
      }
      closeRectificationDrawer();
      await loadInspectionData();
    } catch (err) {
      console.error('提交整改失败', err);
      alert(err.message || '提交整改失败');
    } finally {
      setRectificationSubmitting(false);
    }
  };

  const reviewRectification = async (rectification, action) => {
    const comment = window.prompt(action === 'close' ? '请输入复查关闭意见' : '请输入复查退回原因', action === 'close' ? '整改符合要求，关闭。' : '');
    if (comment === null) return;
    try {
      const res = action === 'close'
        ? await closeInspectionRectification(rectification.id, { comment })
        : await rejectInspectionRectification(rectification.id, { comment });
      if (res.code !== 200) {
        alert(res.message || '整改复查失败');
        return;
      }
      closeRectificationDrawer();
      await loadInspectionData();
    } catch (err) {
      console.error('整改复查失败', err);
      alert(err.message || '整改复查失败');
    }
  };

  const assignRectificationTask = async (rectification) => {
    if (!rectificationReviewAllowed) {
      alert('无整改改派权限');
      return;
    }
    if (rectification.status === 'CLOSED') {
      alert('已关闭整改不可改派');
      return;
    }
    if (!members.length) {
      alert('当前项目暂无成员，无法改派整改人');
      return;
    }
    const memberPrompt = members
      .map(member => `${member.userId}. ${memberDisplayName(member)}（${PROJECT_ROLE_TEXT[member.projectRoleCode] || member.projectRoleCode || '项目成员'}）`)
      .join('\n');
    const assigneeInput = window.prompt(`请输入新的整改人用户ID：\n${memberPrompt}`, rectification.assigneeId ? String(rectification.assigneeId) : '');
    if (assigneeInput === null) return;
    const assignee = members.find(item => Number(item.userId) === Number(assigneeInput));
    if (!assignee) {
      alert('整改人必须从当前项目成员中选择');
      return;
    }
    let deadline = window.prompt('请输入新的整改截止日期（YYYY-MM-DD）', rectification.deadline || dateAfterDays(3));
    if (deadline === null) return;
    deadline = deadline.trim() || (rectification.deadline || dateAfterDays(3));
    const comment = window.prompt('请输入改派说明', `改派给 ${memberDisplayName(assignee)}`);
    if (comment === null) return;
    try {
      const res = await assignInspectionRectification(rectification.id, {
        assigneeId: Number(assignee.userId),
        deadline,
        comment,
      });
      if (res.code !== 200) {
        alert(res.message || '改派整改失败');
        return;
      }
      if (selectedRectification?.id === rectification.id) {
        setSelectedRectification(res.data);
      }
      await loadInspectionData();
    } catch (err) {
      console.error('改派整改失败', err);
      alert(err.message || '改派整改失败');
    }
  };

  const escalateRectificationTask = async (rectification) => {
    if (!rectificationReviewAllowed) {
      alert('无整改升级提醒权限');
      return;
    }
    if (rectification.status === 'CLOSED' || rectification.status === 'COMPLETED') {
      alert('当前状态不需要升级提醒');
      return;
    }
    const overdue = rectification.deadline && new Date(rectification.deadline) < new Date(new Date().toISOString().slice(0, 10));
    const note = window.prompt(overdue ? '请输入逾期升级说明' : '请输入跟进提醒说明',
      overdue ? '整改已逾期，请项目负责人督办整改。' : '请整改人按期完成整改并上传照片。');
    if (note === null) return;
    try {
      const res = await escalateInspectionRectification(rectification.id, { note });
      if (res.code !== 200) {
        alert(res.message || '升级提醒失败');
        return;
      }
      if (selectedRectification?.id === rectification.id) {
        setSelectedRectification(res.data);
      }
      await loadInspectionData();
    } catch (err) {
      console.error('升级提醒失败', err);
      alert(err.message || '升级提醒失败');
    }
  };

  const exportSummary = async () => {
    try {
      const selectedExportBox = boxes.find(item => String(item.id) === String(summaryBoxId));
      const blob = await exportInspectionRecords({
        projectId,
        templateCode: 'ELECTRIC_BOX_DAILY',
        month,
        boxId: summaryBoxId || undefined,
        inspectorId: summaryBoxId ? undefined : recordInspectorId || undefined,
        result: summaryBoxId ? undefined : recordResult || undefined,
      });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = selectedExportBox
        ? `${selectedExportBox.boxCode}-电箱检查记录表-${month}.xlsx`
        : `电箱巡检记录-${projectId}-${month}.xlsx`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(url);
    } catch (err) {
      console.error('导出巡检汇总失败', err);
      alert(err.message || '导出失败');
    }
  };

  const downloadQrCodeText = (box) => {
    const blob = new Blob([
      `电箱编号：${box.boxCode || ''}\n`,
      `统一巡检场景码：B:${box.publicCode || ''}\n`,
      `安装位置：${box.installLocation || ''}\n`,
    ], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `${box.boxCode || 'electric-box'}-二维码编码.txt`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  };

  const fieldStyle = {
    background: T.surface2,
    border: `1px solid ${T.borderColor}`,
    borderRadius: 5,
    padding: '7px 9px',
    color: T.textPrimary,
    fontSize: 12,
    outline: 'none',
  };

  const tableHeaderStyle = {
    display: 'grid',
    gap: 10,
    padding: '9px 12px',
    background: T.surface2,
    borderBottom: `1px solid ${T.borderColor}`,
    color: T.textSecondary,
    fontSize: 11,
    fontWeight: 700,
  };

  const rowBaseStyle = {
    display: 'grid',
    gap: 10,
    padding: '10px 12px',
    borderBottom: `1px solid ${T.borderColor}`,
    alignItems: 'center',
    color: T.textPrimary,
    fontSize: 12,
  };

  const actionButton = (label, onClick, variant = 'primary') => (
    <button onClick={onClick} style={{
      padding: '5px 9px',
      borderRadius: 5,
      border: `1px solid ${variant === 'primary' ? T.accent : variant === 'danger' ? T.danger : T.borderColor}`,
      background: variant === 'primary' ? T.accent : 'transparent',
      color: variant === 'primary' ? '#fff' : variant === 'danger' ? T.danger : T.textSecondary,
      fontSize: 11,
      cursor: 'pointer',
      whiteSpace: 'nowrap',
    }}>{label}</button>
  );

  const renderStats = () => (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, minmax(0, 1fr))', gap: 10 }}>
      {[
        ['电箱总数', boxes.length, T.accent],
        ['今日应检', activeBoxes.length, T.textPrimary],
        ['今日已检', completedToday, T.success],
        ['今日未检', Math.max(activeBoxes.length - completedToday, 0), T.warning],
      ].map(([label, value, color]) => (
        <div key={label} style={{
          background: T.cardBg,
          border: `1px solid ${T.borderColor}`,
          borderRadius: 7,
          padding: '12px 14px',
          minWidth: 0,
        }}>
          <div style={{ fontSize: 11, color: T.textMuted }}>{label}</div>
          <div style={{ marginTop: 5, fontSize: 24, lineHeight: 1, fontWeight: 800, color }}>{value}</div>
        </div>
      ))}
    </div>
  );

  const openSafetyTodo = async (todo) => {
    if (todo.type === 'INSPECTION') {
      const box = boxes.find(item => Number(item.id) === Number(todo.targetId));
      onTabChange?.('ledger');
      if (box) setSelectedBox(box);
      return;
    }
    if (todo.type === 'REVIEW') {
      const record = records.find(item => Number(item.id) === Number(todo.targetId)) || { id: todo.targetId };
      onTabChange?.('review');
      await openRecordDetail(record);
      return;
    }
    const rectification = rectifications.find(item => Number(item.id) === Number(todo.targetId)) || { id: todo.targetId };
    onTabChange?.('rectification');
    await openRectificationDetail(rectification);
  };

  const locateBoxByCode = () => {
    const value = boxKeyword.trim();
    if (!value) return;
    const box = boxes.find(item => [item.boxCode, item.qrCode, item.publicCode].some(code => code && String(code).includes(value)));
    if (!box) {
      alert('未找到匹配的电箱编号或二维码');
      return;
    }
    setSelectedBox(box);
  };

  const renderSafetyOverview = () => (
    <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 1.65fr) minmax(300px, .8fr)', gap: 12, flex: 1, minHeight: 0 }}>
      <div style={{ background: T.cardBg, border: `1px solid ${T.borderColor}`, borderRadius: 7, overflow: 'hidden', display: 'flex', flexDirection: 'column', minHeight: 0 }}>
        <div style={{ padding: '12px 14px', borderBottom: `1px solid ${T.borderColor}`, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div><div style={{ color: T.textPrimary, fontWeight: 800, fontSize: 13 }}>当前用户现场任务</div><div style={{ marginTop: 3, color: T.textMuted, fontSize: 11 }}>与小程序安全页使用同一待办接口</div></div>
          <span style={{ padding: '3px 8px', borderRadius: 999, color: todos.length ? T.warning : T.success, background: todos.length ? `${T.warning}18` : `${T.success}16`, fontSize: 11, fontWeight: 700 }}>{todos.length} 项待办</span>
        </div>
        <div style={{ ...tableHeaderStyle, gridTemplateColumns: '90px 1.3fr .9fr .8fr 90px' }}><span>类型</span><span>任务</span><span>电箱/位置</span><span>时限</span><span>操作</span></div>
        <div style={{ overflow: 'auto', minHeight: 0 }}>
          {todos.map(todo => (
            <div key={`${todo.type}-${todo.targetId}`} style={{ ...rowBaseStyle, gridTemplateColumns: '90px 1.3fr .9fr .8fr 90px' }}>
              <InspectionPill status={todo.type === 'RECTIFICATION' ? 'REJECTED' : todo.type === 'REVIEW' || todo.type === 'RECHECK' ? 'UNCHECKED' : 'ACTIVE'} theme={T}>{todo.type === 'INSPECTION' ? '待巡检' : todo.type === 'REVIEW' ? '待复核' : todo.type === 'RECTIFICATION' ? '待整改' : '待复查'}</InspectionPill>
              <span style={{ color: T.textPrimary, fontWeight: 700 }}>{todo.title}</span>
              <span style={{ color: T.textSecondary }}>{todo.boxCode || '-'} · {todo.installLocation || '-'}</span>
              <span style={{ color: todo.priority === 'danger' ? T.danger : T.textMuted }}>{todo.dueText || '-'}</span>
              {actionButton('处理', () => openSafetyTodo(todo), 'secondary')}
            </div>
          ))}
          {!todos.length && <InspectionEmpty text="当前项目暂无待处理安全任务" theme={T} />}
        </div>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12, minHeight: 0 }}>
        <div style={{ padding: 14, background: T.cardBg, border: `1px solid ${T.borderColor}`, borderRadius: 7 }}>
          <div style={{ color: T.textPrimary, fontWeight: 800, fontSize: 13 }}>电箱编号/二维码查询</div>
          <div style={{ marginTop: 4, color: T.textMuted, fontSize: 11 }}>Web 端使用编号查询对应小程序扫码入口</div>
          <div style={{ display: 'flex', gap: 7, marginTop: 12 }}><input value={boxKeyword} onChange={e => setBoxKeyword(e.target.value)} onKeyDown={e => e.key === 'Enter' && locateBoxByCode()} placeholder="输入电箱编号或二维码内容" style={{ ...fieldStyle, minWidth: 0, flex: 1 }} />{actionButton('查询', locateBoxByCode)}</div>
        </div>
        <div style={{ padding: 14, background: T.cardBg, border: `1px solid ${T.borderColor}`, borderRadius: 7, flex: 1 }}>
          <div style={{ color: T.textPrimary, fontWeight: 800, fontSize: 13, marginBottom: 10 }}>快捷入口</div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
            {[['ledger','巡检台账'],['records','巡检记录']].map(([id,label]) => <button key={id} onClick={() => onTabChange?.(id)} style={{ minHeight: 58, borderRadius: 6, border: `1px solid ${T.borderColor}`, background: T.surface2, color: T.textSecondary, cursor: 'pointer', fontSize: 12, fontWeight: 700 }}>{label}</button>)}
          </div>
        </div>
      </div>
    </div>
  );

  const filterBarStyle = { display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' };

  const renderLedgerFilters = () => (
    <div style={filterBarStyle}>
      <input value={boxKeyword} onChange={e => setBoxKeyword(e.target.value)} placeholder="搜索电箱编号/位置/负责人" style={{ ...fieldStyle, width: 220 }} />
      <select value={boxStatus} onChange={e => setBoxStatus(e.target.value)} style={fieldStyle}>
        <option value="">全部电箱</option>
        <option value="ACTIVE">启用</option>
        <option value="INACTIVE">停用</option>
        <option value="REMOVED">已拆除</option>
      </select>
      {actionButton('刷新', loadInspectionData, 'secondary')}
    </div>
  );

  const renderReviewFilters = () => (
    <div style={filterBarStyle}>
      <select value={recordStatus} onChange={e => setRecordStatus(e.target.value)} style={fieldStyle}>
        <option value="">全部记录</option>
        <option value="REVIEW_PENDING">待复核</option>
        <option value="REVIEW_PASSED">已通过</option>
        <option value="REVIEW_REJECTED">已退回</option>
        <option value="RECTIFICATION_PENDING">待整改</option>
        <option value="CLOSED">已归档</option>
      </select>
      <select value={reviewScope} onChange={e => setReviewScope(e.target.value)} style={fieldStyle}>
        <option value="">全部复核范围</option>
        <option value="MINE">我的复核</option>
        <option value="UNASSIGNED">未分配</option>
        <option value="ASSIGNED">已分配</option>
      </select>
      <select value={reviewOverdueFilter} onChange={e => setReviewOverdueFilter(e.target.value)} style={fieldStyle}>
        <option value="">全部时限</option>
        <option value="true">复核逾期</option>
        <option value="false">未逾期</option>
      </select>
      <input type="month" value={month} onChange={e => setMonth(e.target.value)} style={{ ...fieldStyle, colorScheme: 'dark' }} />
      {actionButton('刷新', loadInspectionData, 'secondary')}
    </div>
  );

  const renderRectificationFilters = () => (
    <div style={filterBarStyle}>
      <select value={rectificationStatus} onChange={e => setRectificationStatus(e.target.value)} style={fieldStyle}>
        <option value="">全部整改</option>
        <option value="PENDING">待整改</option>
        <option value="COMPLETED">待复查</option>
        <option value="REJECTED">复查退回</option>
        <option value="CLOSED">已关闭</option>
      </select>
      {actionButton('刷新', loadInspectionData, 'secondary')}
    </div>
  );

  const renderSummaryFilters = () => (
    <div style={filterBarStyle}>
      <select value={summaryBoxId} onChange={e => setSummaryBoxId(e.target.value)} style={fieldStyle}>
        <option value="">全部电箱</option>
        {boxes.map(box => <option key={box.id} value={box.id}>{box.boxCode} · {box.installLocation}</option>)}
      </select>
      <select value={recordInspectorId} onChange={e => setRecordInspectorId(e.target.value)} style={fieldStyle}>
        <option value="">全部巡检员</option>
        {recordInspectors.map(inspector => <option key={inspector.id} value={inspector.id}>{inspector.name}</option>)}
      </select>
      <select value={recordResult} onChange={e => setRecordResult(e.target.value)} style={fieldStyle}>
        <option value="">全部结果</option>
        <option value="NORMAL">正常</option>
        <option value="ABNORMAL">有异常</option>
      </select>
      <input type="month" value={month} onChange={e => setMonth(e.target.value)} style={{ ...fieldStyle, colorScheme: 'dark' }} />
      {(summaryExportAllowed || singleBoxExportAllowed) && actionButton(summaryBoxId ? '导出本箱月表' : '导出项目月表', exportSummary)}
      {actionButton('刷新', loadInspectionData, 'secondary')}
    </div>
  );

  const renderActiveFilters = () => {
    if (activeTab === 'ledger') return renderLedgerFilters();
    if (activeTab === 'review') return renderReviewFilters();
    if (activeTab === 'rectification') return renderRectificationFilters();
    if (activeTab === 'records') return renderSummaryFilters();
    return null;
  };

  const renderLedgerTab = () => (
    <div style={{ background: T.cardBg, border: `1px solid ${T.borderColor}`, borderRadius: 7, minHeight: 0, overflow: 'hidden', flex: 1, display: 'flex', flexDirection: 'column' }}>
      <div style={{ padding: 12, borderBottom: `1px solid ${T.borderColor}`, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <div style={{ fontSize: 13, color: T.textPrimary, fontWeight: 800 }}>电箱台账</div>
          <div style={{ fontSize: 11, color: T.textMuted, marginTop: 3 }}>项目内电箱、巡检范围、负责人和统一巡检码基础数据</div>
        </div>
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
          {boxManageAllowed && actionButton('下载模板', downloadImportTemplate, 'secondary')}
          {boxManageAllowed && actionButton('导入台账', () => setShowImportModal(true), 'secondary')}
          {qrManageAllowed && actionButton('批量打印二维码', () => printQrLabels(filteredBoxes), 'secondary')}
          {boxManageAllowed && actionButton('新增电箱', openCreateBox)}
        </div>
      </div>
      <div style={{ ...tableHeaderStyle, gridTemplateColumns: '1fr 1.25fr .85fr .6fr 1fr 1.8fr' }}>
        <span>电箱</span><span>位置/巡检员</span><span>日检范围</span><span>今日</span><span>外部访问</span><span>操作</span>
      </div>
      <div style={{ overflow: 'auto', minHeight: 0 }}>
        {filteredBoxes.map(box => {
          const publicEnabled = Number(box.publicAccessEnabled ?? 1) === 1;
          return (
            <div key={box.id} style={{ ...rowBaseStyle, gridTemplateColumns: '1fr 1.25fr .85fr .6fr 1fr 1.8fr' }}>
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
                  <span style={{ fontWeight: 800 }}>{box.boxCode}</span>
                  <InspectionPill status={box.status} theme={T}>{BOX_STATUS_TEXT[box.status] || box.status || '-'}</InspectionPill>
                </div>
                <div style={{ color: T.textMuted, fontSize: 10, marginTop: 3 }}>{box.boxName || '-'}</div>
              </div>
              <div>
                <div style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{box.installLocation || '-'}</div>
                <div style={{ color: T.textMuted, fontSize: 10, marginTop: 3 }}>{box.responsibleElectricianName || '未指定巡检员'}</div>
              </div>
              <div>
                <InspectionPill status={box.inspectionRequired ? 'ACTIVE' : 'INACTIVE'} theme={T}>{box.inspectionRequired ? '已纳入' : '未纳入'}</InspectionPill>
                <div style={{ color: T.textMuted, fontSize: 10, marginTop: 4 }}>{box.scopeEffectiveDate || '沿用历史默认范围'}</div>
              </div>
              <InspectionPill status={box.todayStatus} theme={T}>{BOX_TODAY_STATUS_TEXT[box.todayStatus] || box.todayStatus || '-'}</InspectionPill>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6, alignItems: 'flex-start', minWidth: 0 }}>
                <InspectionPill status={publicEnabled ? 'ACTIVE' : 'INACTIVE'} theme={T}>{publicEnabled ? '公开中' : '已停用'}</InspectionPill>
                <div style={{ width: '100%', color: T.textMuted, fontSize: 10, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>公开码：{box.publicCode || '-'}</div>
                {publicAccessAllowed && box.status !== 'REMOVED' && (publicEnabled
                  ? actionButton('停用公开访问', () => togglePublicAccess(box, false), 'danger')
                  : actionButton('启用公开访问', () => togglePublicAccess(box, true)))}
              </div>
              <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                {actionButton('详情', () => setSelectedBox(box), 'secondary')}
                {boxManageAllowed && actionButton('编辑', () => openEditBox(box), 'secondary')}
                {actionButton('二维码', () => openQrPanel(box), 'secondary')}
                {actionButton('日志', () => openQrLogs(box), 'secondary')}
                {boxManageAllowed && box.status !== 'REMOVED' && actionButton(box.inspectionRequired ? '移出日检' : '纳入日检', () => toggleInspectionScope(box), 'secondary')}
                {qrManageAllowed && box.status !== 'REMOVED' && actionButton('换绑', () => rebindQr(box), 'secondary')}
                {boxManageAllowed && box.status === 'ACTIVE' && actionButton('停用', () => disableBox(box), 'danger')}
                {boxManageAllowed && box.status !== 'REMOVED' && actionButton('拆除', () => removeBox(box), 'danger')}
              </div>
            </div>
          );
        })}
        {filteredBoxes.length === 0 && <InspectionEmpty text="暂无电箱台账数据" theme={T} />}
      </div>
    </div>
  );

  const renderReviewTab = () => (
    <div style={{ background: T.cardBg, border: `1px solid ${T.borderColor}`, borderRadius: 7, minHeight: 0, overflow: 'hidden', flex: 1, display: 'flex', flexDirection: 'column' }}>
      <div style={{ padding: 12, borderBottom: `1px solid ${T.borderColor}` }}>
        <div style={{ fontSize: 13, color: T.textPrimary, fontWeight: 800 }}>安全复核</div>
        <div style={{ fontSize: 11, color: T.textMuted, marginTop: 3 }}>小程序日检/抽查提交后在这里完成安全员复核</div>
      </div>
      <div style={{ ...tableHeaderStyle, gridTemplateColumns: '0.85fr 1fr 1fr 0.9fr 1.05fr 0.8fr 0.9fr' }}>
        <span>日期</span><span>电箱</span><span>来源/人员</span><span>复核人</span><span>时限</span><span>状态</span><span>操作</span>
      </div>
      <div style={{ overflow: 'auto', minHeight: 0 }}>
        {records.map(record => (
          <div key={record.id} style={{ ...rowBaseStyle, gridTemplateColumns: '0.85fr 1fr 1fr 0.9fr 1.05fr 0.8fr 0.9fr' }}>
            <div>{record.checkDate || '-'}</div>
            <div>
              <div style={{ fontWeight: 800 }}>{record.boxCode || '-'}</div>
              <div style={{ color: T.textMuted, fontSize: 10, marginTop: 3 }}>{record.installLocation || '-'}</div>
            </div>
            <div>
                <div>{SOURCE_TEXT[record.source] || record.source || '-'}</div>
                <div style={{ color: T.textMuted, fontSize: 10, marginTop: 3 }}>{record.inspectorName || '-'}</div>
              </div>
            <div>
              <div style={{ fontWeight: 800 }}>{record.assignedReviewerName || '未分配'}</div>
              <div style={{ color: Number(record.abnormalCount) > 0 ? T.danger : T.success, fontSize: 10, marginTop: 3 }}>异常 {record.abnormalCount || 0}</div>
            </div>
            <div style={{ color: Number(record.reviewOverdue) === 1 ? T.danger : T.textSecondary }}>
              <div>{formatDateTime(record.reviewDueTime)}</div>
              {Number(record.reviewOverdue) === 1 && <div style={{ fontSize: 10, marginTop: 3, fontWeight: 800 }}>已逾期</div>}
            </div>
            <InspectionPill status={record.status} theme={T}>{RECORD_STATUS_TEXT[record.status] || REVIEW_STATUS_TEXT[record.reviewStatus] || record.status || '-'}</InspectionPill>
            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
              {actionButton(record.status === 'REVIEW_PENDING' ? '复核' : '查看', () => openRecordDetail(record), 'secondary')}
            </div>
          </div>
        ))}
        {records.length === 0 && <InspectionEmpty text="暂无巡检记录" theme={T} />}
      </div>
    </div>
  );

  const renderRectificationTab = () => (
    <div style={{ background: T.cardBg, border: `1px solid ${T.borderColor}`, borderRadius: 7, overflow: 'hidden', minHeight: 0, flex: 1, display: 'flex', flexDirection: 'column' }}>
      <div style={{ padding: 12, borderBottom: `1px solid ${T.borderColor}` }}>
        <div style={{ fontSize: 13, color: T.textPrimary, fontWeight: 800 }}>整改闭环</div>
        <div style={{ fontSize: 11, color: T.textMuted, marginTop: 3 }}>安全抽查或复核转整改后，PC 后台可提交整改反馈、复查关闭或退回</div>
      </div>
      <div style={{ ...tableHeaderStyle, gridTemplateColumns: '1fr 1fr 1.8fr 1fr 0.9fr 0.8fr 1.2fr' }}>
        <span>整改单</span><span>电箱</span><span>问题/要求</span><span>整改人</span><span>期限</span><span>状态</span><span>操作</span>
      </div>
      <div style={{ overflow: 'auto', minHeight: 0 }}>
        {rectifications.map(item => (
          <div key={item.id} style={{ ...rowBaseStyle, gridTemplateColumns: '1fr 1fr 1.8fr 1fr 0.9fr 0.8fr 1.2fr' }}>
            <div>
              <div style={{ fontWeight: 800 }}>{item.orderNo || `ZG-${item.id}`}</div>
              <div style={{ color: T.textMuted, fontSize: 10, marginTop: 3 }}>{formatDateTime(item.createdAt)}</div>
            </div>
            <div>
              <div style={{ fontWeight: 800 }}>{item.boxCode || '-'}</div>
              <div style={{ color: T.textMuted, fontSize: 10, marginTop: 3 }}>{item.installLocation || '-'}</div>
            </div>
	            <div>
	              <div style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{item.problemDesc || '-'}</div>
	              <div style={{ color: T.warning, fontSize: 10, marginTop: 3 }}>{SPOT_CHECK_CATEGORY_TEXT[item.problemCategory] || item.problemCategory || '未分类'}</div>
	              <div style={{ color: T.textMuted, fontSize: 10, marginTop: 3, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{item.requirement || '-'}</div>
	            </div>
	            <div>{item.assigneeName || '-'}</div>
	            <div style={{ color: item.deadline && new Date(item.deadline) < new Date(new Date().toISOString().slice(0, 10)) && !['CLOSED', 'COMPLETED'].includes(item.status) ? T.danger : T.textSecondary }}>
	              <div>{item.deadline || '-'}</div>
	              {item.escalationStatus && item.escalationStatus !== 'NONE' && <div style={{ fontSize: 10, marginTop: 3, color: item.escalationStatus === 'ESCALATED' ? T.danger : T.accent }}>{item.escalationStatus === 'ESCALATED' ? '已升级' : '已提醒'}</div>}
	            </div>
	            <InspectionPill status={item.status} theme={T}>{RECTIFICATION_STATUS_TEXT[item.status] || item.status || '-'}</InspectionPill>
	            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
	              {actionButton('详情', () => openRectificationDetail(item), 'secondary')}
	              {canCompleteRectification(item) && actionButton('提交整改', () => openRectificationDetail(item))}
	              {rectificationReviewAllowed && item.status !== 'CLOSED' && item.status !== 'COMPLETED' && actionButton('改派', () => assignRectificationTask(item), 'secondary')}
	              {rectificationReviewAllowed && item.status !== 'CLOSED' && item.status !== 'COMPLETED' && actionButton('提醒', () => escalateRectificationTask(item), 'danger')}
	              {rectificationReviewAllowed && item.status === 'COMPLETED' && actionButton('关闭', () => reviewRectification(item, 'close'))}
	              {rectificationReviewAllowed && item.status === 'COMPLETED' && actionButton('退回', () => reviewRectification(item, 'reject'), 'danger')}
	            </div>
          </div>
        ))}
        {rectifications.length === 0 && <InspectionEmpty text="暂无整改任务" theme={T} />}
      </div>
    </div>
  );

  const renderSummaryTab = () => {
    const rate = summary?.shouldCheck ? Math.round((summary.checked || 0) / summary.shouldCheck * 100) : 0;
    return (
      <div style={{ display: 'grid', gridTemplateColumns: '320px 1fr', gap: 12, minHeight: 0, flex: 1 }}>
        <div style={{ background: T.cardBg, border: `1px solid ${T.borderColor}`, borderRadius: 7, padding: 14, minHeight: 0 }}>
          <div style={{ fontSize: 13, color: T.textPrimary, fontWeight: 800, marginBottom: 12 }}>月度巡检记录</div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8 }}>
            {[
              ['应检', summary?.shouldCheck || 0, T.textPrimary],
              ['已检', summary?.checked || 0, T.success],
              ['漏检', summary?.missed || 0, T.warning],
              ['异常', summary?.abnormal || 0, T.danger],
              ['完成率', `${rate}%`, T.accent],
            ].map(([label, value, color]) => (
              <div key={label} style={{ background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 6, padding: 10 }}>
                <div style={{ fontSize: 10, color: T.textMuted }}>{label}</div>
                <div style={{ fontSize: 20, fontWeight: 800, color, marginTop: 4 }}>{value}</div>
              </div>
            ))}
          </div>
          <div style={{ marginTop: 14 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 11, color: T.textSecondary, marginBottom: 6 }}>
              <span>月度完成率</span><span>{rate}%</span>
            </div>
            <div style={{ height: 8, background: T.surface2, borderRadius: 99, overflow: 'hidden' }}>
              <div style={{ width: `${Math.min(rate, 100)}%`, height: '100%', background: T.accent }} />
            </div>
          </div>
        </div>
        <div style={{ background: T.cardBg, border: `1px solid ${T.borderColor}`, borderRadius: 7, overflow: 'hidden', minHeight: 0, display: 'flex', flexDirection: 'column' }}>
          <div style={{ padding: 12, borderBottom: `1px solid ${T.borderColor}`, display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
            <span style={{ fontSize: 13, color: T.textPrimary, fontWeight: 800 }}>巡检记录明细</span>
            <span style={{ color: T.textMuted, fontSize: 11 }}>当前筛选 {filteredSummaryRecords.length} 条</span>
          </div>
          <div style={{ ...tableHeaderStyle, gridTemplateColumns: '1fr 1fr 1fr 0.8fr 1.4fr 70px' }}>
            <span>日期</span><span>电箱</span><span>巡检员</span><span>异常</span><span>备注</span><span>操作</span>
          </div>
          <div style={{ overflow: 'auto', minHeight: 0 }}>
            {filteredSummaryRecords.map(record => (
              <div key={record.id || `${record.electricBoxId}-${record.checkDate}`} style={{ ...rowBaseStyle, gridTemplateColumns: '1fr 1fr 1fr 0.8fr 1.4fr 70px' }}>
                <span>{record.checkDate || '-'}</span>
                <span>{record.boxCode || '-'}</span>
                <span>{record.inspectorName || '-'}</span>
                <span style={{ color: Number(record.abnormalCount) > 0 ? T.danger : T.success, fontWeight: 800 }}>{record.abnormalCount || 0}</span>
                <span style={{ color: T.textMuted, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{record.remark || '-'}</span>
                {actionButton('查看', () => openRecordDetail(record), 'secondary')}
              </div>
            ))}
            {!filteredSummaryRecords.length && <InspectionEmpty text="当前筛选条件下暂无巡检记录" theme={T} />}
          </div>
        </div>
      </div>
    );
  };

  const renderPermissionTab = () => {
    const userTabs = [
      { key: 'users', label: '已注册用户', count: wechatUsersPage.total || 0 },
      { key: 'pending', label: '待审批', count: pendingWechatApplicationTotal || 0 },
      { key: 'history', label: '申请记录', count: permissionUserTab === 'history' ? wechatApplicationTotal : null },
    ];
    const selectUserTab = (tab) => {
      setPermissionUserTab(tab);
      setPermissionFilters(prev => ({ ...prev, pageNo: 1 }));
    };
    const showRegisteredUsers = permissionUserTab === 'users';

    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10, minHeight: 0, flex: 1, overflowY: 'auto', overflowX: 'hidden', paddingRight: 6, paddingBottom: 18, scrollbarGutter: 'stable' }}>
        <PermissionCollapse
          title="小程序用户管理"
          subtitle="统一管理注册用户、待审批申请和历史记录；项目授权在用户详情中维护。"
          meta={<><InspectionPill status={pendingWechatApplicationTotal ? 'REVIEW_PENDING' : 'ACTIVE'} theme={T}>{pendingWechatApplicationTotal} 条待审批</InspectionPill><InspectionPill status="ACTIVE" theme={T}>{wechatUsersPage.total || 0} 个用户</InspectionPill></>}
          theme={T}
        >
          <div style={{ padding: 12, borderBottom: `1px solid ${T.borderColor}` }}>
            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', padding: 4, borderRadius: 8, background: T.surface2, border: `1px solid ${T.borderColor}`, width: 'fit-content' }}>
              {userTabs.map(tab => {
                const active = permissionUserTab === tab.key;
                return (
                  <button key={tab.key} onClick={() => selectUserTab(tab.key)} style={{ border: active ? `1px solid ${T.accent}` : '1px solid transparent', borderRadius: 6, padding: '7px 14px', background: active ? T.activeItemBg : 'transparent', color: active ? T.accent : T.textSecondary, fontSize: 12, fontWeight: active ? 800 : 600, cursor: 'pointer' }}>
                    {tab.label}{tab.count !== null && <span style={{ marginLeft: 5, opacity: .8 }}>{tab.count}</span>}
                  </button>
                );
              })}
            </div>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginTop: 10 }}>
              {isPlatformAdmin && <select value={permissionFilters.projectId} onChange={e => setPermissionFilters({ ...permissionFilters, projectId: e.target.value, pageNo: 1 })} style={{ ...fieldStyle, minWidth: 180 }}><option value="">全部项目</option>{(currentUser?.projectRoles || []).map(item => <option key={item.projectId} value={item.projectId}>{item.projectName || item.shortName || `项目${item.projectId}`}</option>)}</select>}
              <input value={permissionFilters.keyword} onChange={e => setPermissionFilters({ ...permissionFilters, keyword: e.target.value, pageNo: 1 })} placeholder="姓名、手机号或用户名" style={{ ...fieldStyle, minWidth: 230, flex: '1 1 230px' }} />
              {showRegisteredUsers && <select value={permissionFilters.bindingStatus} onChange={e => setPermissionFilters({ ...permissionFilters, bindingStatus: e.target.value, pageNo: 1 })} style={{ ...fieldStyle, minWidth: 140 }}><option value="">全部微信状态</option><option value="ACTIVE">已绑定</option><option value="DISABLED">已停用</option><option value="UNBOUND">已解绑</option></select>}
              {showRegisteredUsers && <select value={permissionFilters.projectAccessStatus} onChange={e => setPermissionFilters({ ...permissionFilters, projectAccessStatus: e.target.value, pageNo: 1 })} style={{ ...fieldStyle, minWidth: 140 }}><option value="">全部项目状态</option><option value="ACTIVE">项目有效</option><option value="DISABLED">项目暂停</option></select>}
              {showRegisteredUsers && <select value={permissionFilters.permissionTemplateId} onChange={e => setPermissionFilters({ ...permissionFilters, permissionTemplateId: e.target.value, pageNo: 1 })} style={{ ...fieldStyle, minWidth: 150 }}><option value="">全部权限角色</option>{activePermissionTemplates.map(template => <option key={template.id} value={template.id}>{template.templateName}</option>)}</select>}
              {actionButton(permissionLoading ? '加载中' : '刷新', loadPermissionData, 'secondary')}
            </div>
            <div style={{ color: T.textMuted, fontSize: 10, marginTop: 8 }}>OpenID、UnionID 不在页面返回；微信停用、解绑和项目权限调整均从用户详情操作。</div>
          </div>

          {showRegisteredUsers ? (
            <>
              <div style={{ overflowX: 'auto' }}>
                <div style={{ ...tableHeaderStyle, minWidth: 1040, gridTemplateColumns: '1.2fr .75fr .85fr .7fr 1fr .8fr .8fr 70px' }}><span>用户</span><span>微信状态</span><span>最近登录</span><span>项目数</span><span>当前项目</span><span>权限角色</span><span>项目状态</span><span>操作</span></div>
                <div style={{ minWidth: 1040 }}>
                  {(wechatUsersPage.records || []).map(item => <div key={item.bindingId} style={{ ...rowBaseStyle, gridTemplateColumns: '1.2fr .75fr .85fr .7fr 1fr .8fr .8fr 70px' }}><div><div style={{ fontWeight: 800 }}>{item.realName || item.username}</div><div style={{ color: T.textMuted, fontSize: 10 }}>{item.username} · {item.phone || '-'}</div></div><InspectionPill status={item.bindingStatus === 'ACTIVE' ? 'ACTIVE' : item.bindingStatus === 'DISABLED' ? 'INACTIVE' : 'UNCHECKED'} theme={T}>{item.bindingStatus === 'ACTIVE' ? '已绑定' : item.bindingStatus === 'DISABLED' ? '已停用' : '已解绑'}</InspectionPill><span>{formatDateTime(item.lastLoginTime)}</span><span>{item.projectCount || 0}</span><span>{item.projectName || '未授权项目'}</span><span>{item.permissionTemplateName || '-'}</span><InspectionPill status={item.projectAccessStatus === 'ACTIVE' ? 'ACTIVE' : 'INACTIVE'} theme={T}>{item.projectAccessStatus === 'ACTIVE' ? '有效' : item.projectAccessStatus === 'DISABLED' ? '已暂停' : '-'}</InspectionPill>{actionButton('详情', () => openWechatUserDetail(item), 'secondary')}</div>)}
                  {!(wechatUsersPage.records || []).length && <InspectionEmpty text="暂无小程序注册用户" theme={T} />}
                </div>
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, padding: 8, borderTop: `1px solid ${T.borderColor}` }}>{actionButton('上一页', () => setPermissionFilters({ ...permissionFilters, pageNo: Math.max(1, permissionFilters.pageNo - 1) }), 'secondary')}<span style={{ color: T.textMuted, fontSize: 11, alignSelf: 'center' }}>第 {wechatUsersPage.page || 1} / {Math.max(1, Math.ceil((wechatUsersPage.total || 0) / (wechatUsersPage.size || 20)))} 页</span>{actionButton('下一页', () => setPermissionFilters({ ...permissionFilters, pageNo: Math.min(Math.max(1, Math.ceil((wechatUsersPage.total || 0) / (wechatUsersPage.size || 20))), permissionFilters.pageNo + 1) }), 'secondary')}</div>
            </>
          ) : (
            <div style={{ overflowX: 'auto' }}>
              <div style={{ ...tableHeaderStyle, minWidth: 980, gridTemplateColumns: '1fr .9fr 1fr .85fr 1fr .9fr 120px' }}><span>申请人</span><span>申请类型</span><span>来源项目/电箱</span><span>匹配账号</span><span>申请时间</span><span>状态</span><span>操作</span></div>
              <div style={{ minWidth: 980 }}>
                {wechatApplications.map(item => <div key={item.id} style={{ ...rowBaseStyle, gridTemplateColumns: '1fr .9fr 1fr .85fr 1fr .9fr 120px' }}><div><div style={{ fontWeight: 800 }}>{item.realName || '未填姓名'}</div><div style={{ color: T.textMuted, fontSize: 10 }}>{item.phone || '-'}</div></div><span>{item.applicationType === 'MULTIPLE_MATCH' ? '多账号待确认' : item.applicationType === 'PROJECT_ACCESS' ? '项目权限申请' : '新用户注册'}</span><span>{item.projectName || '-'} · {item.boxCode || '-'}</span><span>{item.matchedUsername || '未匹配'}</span><span>{formatDateTime(item.createTime)}</span><InspectionPill status={item.status === 'PENDING' ? 'REVIEW_PENDING' : item.status === 'APPROVED' ? 'ACTIVE' : 'INACTIVE'} theme={T}>{item.status === 'PENDING' ? '待审批' : item.status === 'APPROVED' ? '已通过' : '已拒绝'}</InspectionPill><div style={{ display: 'flex', gap: 5 }}>{item.status === 'PENDING' ? <>{actionButton('审批', () => reviewWechatApplication(item, true))}{actionButton('拒绝', () => reviewWechatApplication(item, false), 'danger')}</> : <span style={{ color: T.textMuted }}>—</span>}</div></div>)}
                {!wechatApplications.length && <InspectionEmpty text={permissionUserTab === 'pending' ? '暂无待审批申请' : '暂无历史申请记录'} theme={T} />}
              </div>
            </div>
          )}
        </PermissionCollapse>

        <PermissionCollapse title="权限角色" subtitle="定义巡检员、记录查看人员和项目管理员可以使用的功能" meta={<span style={{ color: T.textMuted, fontSize: 11 }}>{permissionTemplates.length} 个角色</span>} defaultOpen={false} theme={T}>
          {isPlatformAdmin && <div style={{ padding: '10px 12px', borderBottom: `1px solid ${T.borderColor}`, display: 'flex', justifyContent: 'flex-end' }}>{actionButton('+ 新建角色', openCreateTemplate)}</div>}
          <div style={{ maxHeight: 430, overflowY: 'auto', padding: 12, display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: 10 }}>
            {!isPlatformAdmin && <div style={{ gridColumn: '1 / -1', padding: 10, borderRadius: 7, border: `1px solid ${T.borderColor}`, background: T.surface2, color: T.textMuted, fontSize: 11, lineHeight: 1.6 }}>只有平台管理员可以新增、编辑和启停权限角色；项目管理员可在用户详情中分配已启用角色。</div>}
            {permissionTemplates.map(template => {
              const enabled = Number(template.enabled ?? 1) === 1;
              return (
                <div key={template.id} style={{ border: `1px solid ${T.borderColor}`, background: T.surface2, borderRadius: 7, padding: 12 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}><div style={{ minWidth: 0 }}><div style={{ color: T.textPrimary, fontSize: 13, fontWeight: 800, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{template.templateName}</div><div style={{ color: T.textMuted, fontSize: 10, marginTop: 3 }}>{template.templateCode}</div></div><InspectionPill status={enabled ? 'ACTIVE' : 'INACTIVE'} theme={T}>{enabled ? '启用' : '停用'}</InspectionPill></div>
                  <div style={{ color: T.textSecondary, fontSize: 11, marginTop: 8, lineHeight: 1.6 }}>{template.description || '暂无说明'}</div>
                  <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 8 }}><span style={{ color: T.textMuted, fontSize: 10 }}>功能权限 {template.permissionCodes?.length || 0} 项</span>{Number(template.builtin || 0) === 1 && <span style={{ color: T.accent, fontSize: 10 }}>内置</span>}</div>
                  {isPlatformAdmin && <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 10 }}>{actionButton('编辑', () => openEditTemplate(template), 'secondary')}{Number(template.builtin || 0) !== 1 && actionButton(enabled ? '停用' : '启用', () => toggleTemplateStatus(template), enabled ? 'danger' : 'primary')}</div>}
                </div>
              );
            })}
            {permissionTemplates.length === 0 && <InspectionEmpty text="暂无权限角色" theme={T} />}
          </div>
        </PermissionCollapse>
      </div>
    );
  };

  const renderRecordDrawer = () => selectedRecord && (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.62)', zIndex: 1002, display: 'flex', justifyContent: 'flex-end' }} onClick={() => setSelectedRecord(null)}>
      <div style={{ width: 520, height: '100%', background: T.modalBg, borderLeft: `1px solid ${T.borderColor}`, padding: 18, overflow: 'auto' }} onClick={e => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <div>
            <div style={{ fontSize: 16, color: T.textPrimary, fontWeight: 800 }}>{selectedRecord.boxCode} 巡检详情</div>
            <div style={{ fontSize: 12, color: T.textMuted, marginTop: 4 }}>{selectedRecord.checkDate} · 电箱巡检</div>
          </div>
          <button onClick={() => setSelectedRecord(null)} style={{ background: 'none', border: 'none', color: T.textMuted, cursor: 'pointer', fontSize: 18 }}>×</button>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginBottom: 14, fontSize: 12 }}>
          <div style={{ color: T.textSecondary }}>巡检员：<span style={{ color: T.textPrimary }}>{selectedRecord.inspectorName || '-'}</span></div>
          <div style={{ color: T.textSecondary }}>记录状态：<InspectionPill status="ACTIVE" theme={T}>已完成</InspectionPill></div>
          <div style={{ color: T.textSecondary }}>外观照片：{selectedRecord.outerPhotoCount || selectedRecord.outerPhotoFileIds?.length || 0}</div>
          <div style={{ color: T.textSecondary }}>内部照片：{selectedRecord.innerPhotoCount || selectedRecord.innerPhotoFileIds?.length || 0}</div>
        </div>
        <div style={{ color: T.textPrimary, fontSize: 13, fontWeight: 700, marginBottom: 8 }}>外观照片</div>
        <InspectionPhotoStrip fileIds={selectedRecord.outerPhotoFileIds || selectedRecord.problemPhotoFileIds || []} theme={T} />
        <div style={{ color: T.textPrimary, fontSize: 13, fontWeight: 700, margin: '16px 0 8px' }}>内部照片</div>
        <InspectionPhotoStrip fileIds={selectedRecord.innerPhotoFileIds || []} theme={T} />
        <div style={{ color: T.textPrimary, fontSize: 13, fontWeight: 700, margin: '16px 0 8px' }}>检查项</div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
          {(selectedRecord.items || []).map(item => (
            <div key={item.id || item.itemCode} style={{ display: 'grid', gridTemplateColumns: '1fr 70px', gap: 8, padding: 10, background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 6 }}>
              <div>
                <div style={{ color: T.textPrimary, fontSize: 12, fontWeight: 700 }}>{item.itemName}</div>
                {item.description && <div style={{ color: T.textMuted, fontSize: 11, marginTop: 4 }}>{item.description}</div>}
              </div>
              <InspectionPill status={item.result} theme={T}>{ITEM_RESULT_TEXT[item.result] || item.result || '-'}</InspectionPill>
            </div>
          ))}
        </div>
        <div style={{ marginTop: 14, padding: 10, background: T.surface2, borderRadius: 6, color: T.textSecondary, fontSize: 12 }}>备注：{selectedRecord.remark || '-'}</div>
      </div>
    </div>
  );

  const renderRectificationDrawer = () => selectedRectification && (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.62)', zIndex: 1002, display: 'flex', justifyContent: 'flex-end' }} onClick={closeRectificationDrawer}>
      <div style={{ width: 520, height: '100%', background: T.modalBg, borderLeft: `1px solid ${T.borderColor}`, padding: 18, overflow: 'auto' }} onClick={e => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <div>
            <div style={{ fontSize: 16, color: T.textPrimary, fontWeight: 800 }}>{selectedRectification.orderNo || `ZG-${selectedRectification.id}`}</div>
            <div style={{ fontSize: 12, color: T.textMuted, marginTop: 4 }}>{selectedRectification.boxCode} · {selectedRectification.installLocation}</div>
          </div>
          <button onClick={closeRectificationDrawer} style={{ background: 'none', border: 'none', color: T.textMuted, cursor: 'pointer', fontSize: 18 }}>×</button>
        </div>
	        <div style={{ display: 'flex', gap: 8, marginBottom: 14 }}>
	          <InspectionPill status={selectedRectification.status} theme={T}>{RECTIFICATION_STATUS_TEXT[selectedRectification.status] || selectedRectification.status}</InspectionPill>
	          <span style={{ color: T.textSecondary, fontSize: 12 }}>截止：{selectedRectification.deadline || '-'}</span>
	          {Number(selectedRectification.rejectCount || 0) > 0 && <span style={{ color: T.danger, fontSize: 12 }}>退回 {selectedRectification.rejectCount} 次</span>}
	          {selectedRectification.escalationStatus && selectedRectification.escalationStatus !== 'NONE' && <span style={{ color: selectedRectification.escalationStatus === 'ESCALATED' ? T.danger : T.accent, fontSize: 12 }}>{selectedRectification.escalationStatus === 'ESCALATED' ? '已升级' : '已提醒'}</span>}
	        </div>
	        <div style={{ padding: 12, background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 7, color: T.textSecondary, fontSize: 12, lineHeight: 1.8 }}>
	          <div>问题说明：<span style={{ color: T.textPrimary }}>{selectedRectification.problemDesc || '-'}</span></div>
	          <div>问题分类：<span style={{ color: T.textPrimary }}>{SPOT_CHECK_CATEGORY_TEXT[selectedRectification.problemCategory] || selectedRectification.problemCategory || '未分类'}</span></div>
	          <div>整改要求：<span style={{ color: T.textPrimary }}>{selectedRectification.requirement || '-'}</span></div>
	          <div>整改人：<span style={{ color: T.textPrimary }}>{selectedRectification.assigneeName || '-'}</span></div>
          <div>提交时间：<span style={{ color: T.textPrimary }}>{formatDateTime(selectedRectification.completedAt)}</span></div>
          <div>复查时间：<span style={{ color: T.textPrimary }}>{formatDateTime(selectedRectification.reviewTime)}</span></div>
          <div>复查截止：<span style={{ color: selectedRectification.recheckDeadline ? T.warning : T.textPrimary }}>{selectedRectification.recheckDeadline || '-'}</span></div>
	          <div>整改反馈：<span style={{ color: T.textPrimary }}>{selectedRectification.feedback || '-'}</span></div>
	          <div>复查意见：<span style={{ color: T.textPrimary }}>{selectedRectification.reviewComment || '-'}</span></div>
	          <div>升级提醒：<span style={{ color: selectedRectification.escalationStatus === 'ESCALATED' ? T.danger : T.textPrimary }}>{selectedRectification.escalationStatus && selectedRectification.escalationStatus !== 'NONE' ? `${selectedRectification.escalationStatus === 'ESCALATED' ? '已升级' : '已提醒'} · ${formatDateTime(selectedRectification.escalationTime)} · ${selectedRectification.escalationNote || '-'}` : '-'}</span></div>
	        </div>
        <div style={{ color: T.textPrimary, fontSize: 13, fontWeight: 700, margin: '16px 0 8px' }}>整改前照片</div>
        <InspectionPhotoStrip fileIds={selectedRectification.beforePhotoFileIds || []} theme={T} />
        <div style={{ color: T.textPrimary, fontSize: 13, fontWeight: 700, margin: '16px 0 8px' }}>整改后照片</div>
        <InspectionPhotoStrip fileIds={selectedRectification.rectificationPhotoFileIds || []} theme={T} />
        {canCompleteRectification(selectedRectification) && (
          <div style={{ marginTop: 16, padding: 12, background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 7 }}>
            <div style={{ color: T.textPrimary, fontSize: 13, fontWeight: 800, marginBottom: 8 }}>提交整改反馈</div>
            <textarea
              value={rectificationFeedback}
              onChange={e => setRectificationFeedback(e.target.value)}
              placeholder="填写整改说明，说明现场已完成的整改措施"
              maxLength={300}
              style={{ ...fieldStyle, width: '100%', minHeight: 92, resize: 'vertical', lineHeight: 1.6 }}
            />
            <div style={{ marginTop: 10 }}>
              <input
                type="file"
                multiple
                accept="image/*"
                onChange={e => setRectificationFiles(Array.from(e.target.files || []))}
                style={{ color: T.textSecondary, fontSize: 12 }}
              />
              <div style={{ color: T.textMuted, fontSize: 11, marginTop: 6 }}>
                新增照片 {rectificationFiles.length} 张{(selectedRectification.rectificationPhotoFileIds || []).length ? `，已有 ${(selectedRectification.rectificationPhotoFileIds || []).length} 张` : ''}
              </div>
            </div>
            <div style={{ marginTop: 12 }}>
              {actionButton(rectificationSubmitting ? '提交中...' : '提交复查', () => submitRectificationFeedback(selectedRectification))}
            </div>
          </div>
        )}
	        {rectificationReviewAllowed && selectedRectification.status === 'COMPLETED' && (
	          <div style={{ display: 'flex', gap: 10, marginTop: 18 }}>
	            {actionButton('复查关闭', () => reviewRectification(selectedRectification, 'close'))}
	            {actionButton('复查退回', () => reviewRectification(selectedRectification, 'reject'), 'danger')}
	          </div>
	        )}
	        {rectificationReviewAllowed && selectedRectification.status !== 'CLOSED' && selectedRectification.status !== 'COMPLETED' && (
	          <div style={{ display: 'flex', gap: 10, marginTop: 18 }}>
	            {actionButton('改派整改人', () => assignRectificationTask(selectedRectification), 'secondary')}
	            {actionButton('逾期升级提醒', () => escalateRectificationTask(selectedRectification), 'danger')}
	          </div>
	        )}
        <div style={{ color: T.textPrimary, fontSize: 13, fontWeight: 700, margin: '16px 0 8px' }}>整改留痕</div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
          {(selectedRectification.reviewLogs || []).map(log => (
            <div key={log.id || `${log.actionType}-${log.createTime}`} style={{ padding: 10, background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 6, fontSize: 12 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                <span style={{ color: T.textPrimary, fontWeight: 800 }}>{RECTIFICATION_LOG_ACTION_TEXT[log.actionType] || log.actionType}</span>
                <span style={{ color: T.textMuted }}>{formatDateTime(log.createTime)}</span>
              </div>
              <div style={{ color: T.textSecondary, marginTop: 5 }}>
                {log.operatorName || '系统'} · {RECTIFICATION_STATUS_TEXT[log.fromStatus] || log.fromStatus || '-'} → {RECTIFICATION_STATUS_TEXT[log.toStatus] || log.toStatus || '-'}
              </div>
              {log.comment && <div style={{ color: T.textMuted, marginTop: 5, lineHeight: 1.6 }}>{log.comment}</div>}
            </div>
          ))}
          {!(selectedRectification.reviewLogs || []).length && <div style={{ color: T.textMuted, fontSize: 12 }}>暂无整改日志</div>}
        </div>
      </div>
    </div>
  );

  const renderBoxModal = () => editingBox !== null && (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.62)', zIndex: 1002, display: 'flex', alignItems: 'center', justifyContent: 'center' }} onClick={() => setEditingBox(null)}>
      <div style={{ width: 560, background: T.modalBg, border: `1px solid ${T.borderColor}`, borderRadius: 10, padding: 20 }} onClick={e => e.stopPropagation()}>
        <div style={{ fontSize: 15, color: T.textPrimary, fontWeight: 800, marginBottom: 14 }}>{editingBox?.id ? '编辑电箱' : '新增电箱'}</div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          {[
            ['电箱编号', 'boxCode', 'EB-001'],
            ['电箱名称', 'boxName', '二级电箱 1'],
            ['安装位置', 'installLocation', '一层东侧材料通道'],
          ].map(([label, key, placeholder]) => (
            <label key={key} style={{ display: 'flex', flexDirection: 'column', gap: 5, color: T.textSecondary, fontSize: 12 }}>
              {label}
              <input value={boxForm[key]} onChange={e => setBoxForm({ ...boxForm, [key]: e.target.value })} placeholder={placeholder} style={fieldStyle} />
            </label>
          ))}
          <label style={{ display: 'flex', flexDirection: 'column', gap: 5, color: T.textSecondary, fontSize: 12 }}>
            负责巡检员
            <select value={boxForm.responsibleElectricianId || ''} onChange={e => setBoxMember('responsible', e.target.value)} style={fieldStyle}>
              <option value="">未指定</option>
              {members.map(member => (
                <option key={member.userId} value={member.userId}>
                  {memberDisplayName(member)} · {PROJECT_ROLE_TEXT[member.projectRoleCode] || member.projectRoleCode}
                </option>
              ))}
            </select>
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 5, color: T.textSecondary, fontSize: 12 }}>
            电箱状态
            <select value={boxForm.status} onChange={e => setBoxForm({ ...boxForm, status: e.target.value })} style={fieldStyle}>
              <option value="ACTIVE">启用</option>
              <option value="INACTIVE">停用</option>
            </select>
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 5, color: T.textSecondary, fontSize: 12 }}>
            二维码状态
            <select value={boxForm.qrStatus} onChange={e => setBoxForm({ ...boxForm, qrStatus: e.target.value })} style={fieldStyle}>
              <option value="BOUND">已绑定</option>
              <option value="DISABLED">停用</option>
            </select>
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 5, color: T.textSecondary, fontSize: 12 }}>
            外部公开扫码
            <select value={boxForm.publicAccessEnabled} onChange={e => setBoxForm({ ...boxForm, publicAccessEnabled: Number(e.target.value) })} style={fieldStyle}>
              <option value={1}>启用</option>
              <option value={0}>停用</option>
            </select>
          </label>
        </div>
        <label style={{ display: 'flex', flexDirection: 'column', gap: 5, color: T.textSecondary, fontSize: 12, marginTop: 12 }}>
          备注
          <textarea value={boxForm.remark} onChange={e => setBoxForm({ ...boxForm, remark: e.target.value })} rows={3} style={{ ...fieldStyle, resize: 'vertical' }} />
        </label>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 18 }}>
          {actionButton('取消', () => setEditingBox(null), 'secondary')}
          {actionButton('保存', saveBox)}
        </div>
      </div>
    </div>
  );

  const renderBoxDrawer = () => selectedBox && (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.62)', zIndex: 1002, display: 'flex', justifyContent: 'flex-end' }} onClick={() => setSelectedBox(null)}>
      <div style={{ width: 'min(500px, 100vw)', maxWidth: '100vw', height: '100%', background: T.modalBg, borderLeft: `1px solid ${T.borderColor}`, padding: 18, overflowY: 'auto', overflowX: 'hidden' }} onClick={e => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
          <div>
            <div style={{ fontSize: 16, color: T.textPrimary, fontWeight: 800 }}>{selectedBox.boxCode}</div>
            <div style={{ fontSize: 12, color: T.textMuted, marginTop: 4 }}>{selectedBox.boxName || '现场电箱'}</div>
          </div>
          <button onClick={() => setSelectedBox(null)} style={{ background: 'none', border: 'none', color: T.textMuted, cursor: 'pointer', fontSize: 18 }}>×</button>
        </div>
        <div style={{ display: 'flex', gap: 8, marginBottom: 14 }}>
          <InspectionPill status={selectedBox.status} theme={T}>{BOX_STATUS_TEXT[selectedBox.status] || selectedBox.status || '-'}</InspectionPill>
          <InspectionPill status={selectedBox.todayStatus} theme={T}>{BOX_TODAY_STATUS_TEXT[selectedBox.todayStatus] || selectedBox.todayStatus || '-'}</InspectionPill>
        </div>
        <div style={{ padding: 12, background: T.surface2, border: `1px solid ${T.borderColor}`, borderRadius: 7, color: T.textSecondary, fontSize: 12, lineHeight: 1.9 }}>
          <div>安装位置：<span style={{ color: T.textPrimary }}>{selectedBox.installLocation || '-'}</span></div>
          <div>负责巡检员：<span style={{ color: T.textPrimary }}>{selectedBox.responsibleElectricianName || '-'}</span></div>
          <div>最近检查：<span style={{ color: T.textPrimary }}>{selectedBox.lastCheckDate || '-'}</span></div>
          <div>统一巡检码：<span style={{ color: T.textPrimary }}>B:{selectedBox.publicCode || '-'}</span></div>
          <div>公开访问：<span style={{ color: Number(selectedBox.publicAccessEnabled ?? 1) === 1 ? T.success : T.warning }}>{Number(selectedBox.publicAccessEnabled ?? 1) === 1 ? '已启用' : '已停用'}</span></div>
        </div>
        {qrManageAllowed && (
          <div style={{ display: 'flex', gap: 8, marginTop: 12, flexWrap: 'wrap' }}>
            {Number(selectedBox.publicAccessEnabled ?? 1) === 1
              ? actionButton('停用公开扫码', () => togglePublicAccess(selectedBox, false), 'danger')
              : actionButton('启用公开扫码', () => togglePublicAccess(selectedBox, true))}
            {selectedBox.status !== 'REMOVED' && actionButton('换码', () => rebindQr(selectedBox), 'secondary')}
            {selectedBox.status !== 'REMOVED' && actionButton('拆除电箱', () => removeBox(selectedBox), 'danger')}
            {actionButton('二维码日志', () => openQrLogs(selectedBox), 'secondary')}
          </div>
        )}
        {selectedBox.qrView && (
          <div style={{ marginTop: 14, padding: 16, borderRadius: 8, border: `1px dashed ${T.accent}`, background: T.surface2, overflow: 'hidden' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8, marginBottom: 12 }}>
              <div>
                <div style={{ color: T.textPrimary, fontWeight: 800, fontSize: 13 }}>统一巡检码预览</div>
                <div style={{ color: T.textMuted, fontSize: 11, marginTop: 3 }}>内部人员巡检 / 外部人员查看月度记录共用</div>
              </div>
              {qrLabelLoading && <span style={{ color: T.textMuted, fontSize: 11 }}>生成中...</span>}
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr', gap: 12 }}>
              {[
                ['统一电箱巡检码', selectedBox.unifiedSvg, selectedBox.unifiedPayload],
              ].map(([label, svg, payload]) => (
                <div key={label} style={{ padding: 12, background: '#fff', border: `1px solid ${T.borderColor}`, borderRadius: 8, textAlign: 'center', minWidth: 0, overflow: 'hidden' }}>
                  <div style={{ width: 150, height: 150, margin: '0 auto', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    {svg ? (
                      <img src={normalizeQrImageSource(svg)} alt={label} style={{ display: 'block', width: 150, height: 150, maxWidth: '100%', objectFit: 'contain' }} />
                    ) : (
                      <span style={{ color: T.textMuted, fontSize: 11 }}>待生成</span>
                    )}
                  </div>
                  <div style={{ marginTop: 8, color: T.accent, fontSize: 12, fontWeight: 800 }}>{label}</div>
                  <div style={{ marginTop: 4, color: T.textMuted, fontSize: 9, lineHeight: 1.35, wordBreak: 'break-all' }}>{payload || '-'}</div>
                  {selectedBox.unifiedHint && <div style={{ maxWidth: 360, margin: '6px auto 0', color: T.warning, fontSize: 10, lineHeight: 1.5, overflowWrap: 'anywhere' }}>{selectedBox.unifiedHint}</div>}
                </div>
              ))}
            </div>
            <div style={{ display: 'flex', gap: 8, marginTop: 12, flexWrap: 'wrap' }}>
              {actionButton('打印当前贴纸', () => printQrLabels([selectedBox]))}
              {actionButton('下载贴纸 HTML', () => downloadQrLabel(selectedBox), 'secondary')}
              {actionButton('下载编码 TXT', () => downloadQrCodeText(selectedBox), 'secondary')}
            </div>
          </div>
        )}
        {selectedBox.logView && (
          <div style={{ marginTop: 14, padding: 14, borderRadius: 8, border: `1px solid ${T.borderColor}`, background: T.surface2 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8, alignItems: 'center', marginBottom: 10 }}>
              <div>
                <div style={{ color: T.textPrimary, fontWeight: 800, fontSize: 13 }}>二维码操作日志</div>
                <div style={{ color: T.textMuted, fontSize: 11, marginTop: 3 }}>生成、补打、换绑、停用、拆除均在此留痕</div>
              </div>
              {qrLogsLoading && <span style={{ color: T.textMuted, fontSize: 11 }}>加载中...</span>}
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {qrLogs.map(log => (
                <div key={log.id} style={{ padding: 10, borderRadius: 7, border: `1px solid ${T.borderColor}`, background: T.cardBg, color: T.textSecondary, fontSize: 11, lineHeight: 1.7 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                    <span style={{ color: T.textPrimary, fontWeight: 800 }}>{log.actionType} · {log.qrType}</span>
                    <span>{formatDateTime(log.createTime)}</span>
                  </div>
                  <div>旧码：<span style={{ color: T.textPrimary }}>{log.oldQrCode || '-'}</span></div>
                  <div>新码：<span style={{ color: T.textPrimary }}>{log.newQrCode || '-'}</span></div>
                  <div>操作人：<span style={{ color: T.textPrimary }}>{log.operatorUsername || '-'}</span></div>
                  <div>原因：<span style={{ color: T.textPrimary }}>{log.reason || '-'}</span></div>
                </div>
              ))}
              {!qrLogs.length && !qrLogsLoading && <InspectionEmpty text="暂无二维码日志" theme={T} />}
            </div>
          </div>
        )}
      </div>
    </div>
  );

  const renderImportModal = () => showImportModal && (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.62)', zIndex: 1004, display: 'flex', alignItems: 'center', justifyContent: 'center' }} onClick={() => setShowImportModal(false)}>
      <div style={{ width: 760, maxHeight: '86vh', overflow: 'auto', background: T.modalBg, border: `1px solid ${T.borderColor}`, borderRadius: 10, padding: 18, boxShadow: '0 18px 60px rgba(0,0,0,0.45)' }} onClick={e => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
          <div>
            <div style={{ fontSize: 16, color: T.textPrimary, fontWeight: 900 }}>导入电箱台账</div>
            <div style={{ color: T.textMuted, fontSize: 11, marginTop: 4 }}>先预检，存在 ERROR 时不会提交；WARN 可继续导入</div>
          </div>
          <button onClick={() => setShowImportModal(false)} style={{ background: 'transparent', border: 'none', color: T.textMuted, cursor: 'pointer', fontSize: 18 }}>×</button>
        </div>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr auto auto', gap: 10, alignItems: 'center', marginBottom: 12 }}>
          <input
            type="file"
            accept=".xlsx,.xls"
            onChange={e => {
              setImportFile(e.target.files?.[0] || null);
              setImportResult(null);
            }}
            style={fieldStyle}
          />
          {actionButton('下载模板', downloadImportTemplate, 'secondary')}
          {actionButton(importBusy ? '处理中...' : '预检', () => submitImport(true))}
        </div>

        {importResult && (
          <div style={{ border: `1px solid ${T.borderColor}`, borderRadius: 8, overflow: 'hidden', background: T.cardBg }}>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 0, borderBottom: `1px solid ${T.borderColor}` }}>
              {[
                ['总行数', importResult.totalRows || 0, T.textPrimary],
                ['可导入', importResult.successRows || 0, T.success],
                ['警告', importResult.warningRows || 0, T.warning],
                ['错误', importResult.errorRows || 0, T.danger],
              ].map(([label, value, color]) => (
                <div key={label} style={{ padding: 12, borderRight: `1px solid ${T.borderColor}` }}>
                  <div style={{ color: T.textMuted, fontSize: 11 }}>{label}</div>
                  <div style={{ color, fontSize: 22, fontWeight: 900, marginTop: 4 }}>{value}</div>
                </div>
              ))}
            </div>
            <div style={{ ...tableHeaderStyle, gridTemplateColumns: '70px 80px 1fr 1.3fr 1.5fr' }}>
              <span>行号</span><span>级别</span><span>编号</span><span>位置</span><span>说明</span>
            </div>
            <div style={{ maxHeight: 300, overflow: 'auto' }}>
              {(importResult.rows || []).map(row => {
                const color = row.level === 'ERROR' ? T.danger : row.level === 'WARN' ? T.warning : T.success;
                return (
                  <div key={row.rowNumber} style={{ ...rowBaseStyle, gridTemplateColumns: '70px 80px 1fr 1.3fr 1.5fr' }}>
                    <span>{row.rowNumber}</span>
                    <span style={{ color, fontWeight: 800 }}>{row.level}</span>
                    <span>{row.boxCode || '-'}</span>
                    <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{row.installLocation || '-'}</span>
                    <span style={{ color: T.textSecondary }}>{row.message || '-'}</span>
                  </div>
                );
              })}
            </div>
          </div>
        )}

        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10, marginTop: 16 }}>
          <div style={{ color: T.textMuted, fontSize: 11 }}>编号完全自定义，但会校验同项目唯一；未填内部二维码时后端自动生成。</div>
          <div style={{ display: 'flex', gap: 10 }}>
            {actionButton('取消', () => setShowImportModal(false), 'secondary')}
            {actionButton('提交导入', () => submitImport(false), Number(importResult?.errorRows || 0) > 0 ? 'secondary' : 'primary')}
          </div>
        </div>
      </div>
    </div>
  );

  const renderTemplateModal = () => editingTemplate && (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.62)', zIndex: 1005, display: 'flex', alignItems: 'center', justifyContent: 'center' }} onClick={() => setEditingTemplate(null)}>
      <div style={{ width: 760, maxHeight: '86vh', overflow: 'auto', background: T.modalBg, border: `1px solid ${T.borderColor}`, borderRadius: 10, padding: 18, boxShadow: '0 18px 60px rgba(0,0,0,0.45)' }} onClick={e => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
          <div>
            <div style={{ fontSize: 16, color: T.textPrimary, fontWeight: 900 }}>{editingTemplate.id ? '编辑权限角色' : '新建权限角色'}</div>
            <div style={{ color: T.textMuted, fontSize: 11, marginTop: 4 }}>设置这个角色可以查看和操作哪些巡检功能</div>
          </div>
          <button onClick={() => setEditingTemplate(null)} style={{ background: 'transparent', border: 'none', color: T.textMuted, cursor: 'pointer', fontSize: 18 }}>×</button>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 120px', gap: 10, marginBottom: 12 }}>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 5, color: T.textSecondary, fontSize: 11 }}>
            角色名称
            <input value={templateForm.templateName} onChange={e => setTemplateForm({ ...templateForm, templateName: e.target.value })} placeholder="如 外部检查只读" style={fieldStyle} />
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 5, color: T.textSecondary, fontSize: 11 }}>
            角色编码（系统使用）
            <input value={templateForm.templateCode} disabled={Boolean(editingTemplate.id)} onChange={e => setTemplateForm({ ...templateForm, templateCode: e.target.value })} placeholder="如 EXTERNAL_READONLY" style={{ ...fieldStyle, opacity: editingTemplate.id ? 0.7 : 1 }} />
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 5, color: T.textSecondary, fontSize: 11 }}>
            状态
            <select value={templateForm.enabled} onChange={e => setTemplateForm({ ...templateForm, enabled: Number(e.target.value) })} style={fieldStyle}>
              <option value={1}>启用</option>
              <option value={0}>停用</option>
            </select>
          </label>
          <label style={{ gridColumn: '1 / -1', display: 'flex', flexDirection: 'column', gap: 5, color: T.textSecondary, fontSize: 11 }}>
            说明
            <textarea value={templateForm.description} onChange={e => setTemplateForm({ ...templateForm, description: e.target.value })} placeholder="说明这个权限角色适合哪些人使用" style={{ ...fieldStyle, minHeight: 64, resize: 'vertical' }} />
          </label>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', gap: 10 }}>
          {permissionCatalog.map(group => (
            <div key={group.groupCode} style={{ border: `1px solid ${T.borderColor}`, background: T.surface2, borderRadius: 7, padding: 12 }}>
              <div style={{ color: T.textPrimary, fontWeight: 800, fontSize: 12, marginBottom: 8 }}>{group.groupName}</div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {(group.items || []).map(item => (
                  <label key={item.code} style={{ display: 'grid', gridTemplateColumns: '18px 1fr', gap: 8, color: T.textSecondary, fontSize: 11, lineHeight: 1.5, cursor: 'pointer' }}>
                    <input type="checkbox" checked={templateForm.permissionCodes.includes(item.code)} onChange={() => toggleTemplatePermission(item.code)} />
                    <span>
                      <strong style={{ color: T.textPrimary }}>{item.name}</strong>
                      <span style={{ color: T.textMuted }}> · {item.description}</span>
                    </span>
                  </label>
                ))}
              </div>
            </div>
          ))}
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 10, marginTop: 16 }}>
          <div style={{ color: T.textMuted, fontSize: 11 }}>已选择 {templateForm.permissionCodes.length} 项权限。</div>
          <div style={{ display: 'flex', gap: 10 }}>
            {actionButton('取消', () => setEditingTemplate(null), 'secondary')}
            {actionButton('保存角色', savePermissionTemplate)}
          </div>
        </div>
      </div>
    </div>
  );

  const renderCreateUserModal = () => showCreateUserModal && (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.62)', zIndex: 1004, display: 'flex', alignItems: 'center', justifyContent: 'center' }} onClick={() => setShowCreateUserModal(false)}>
      <div style={{ width: 560, background: T.modalBg, border: `1px solid ${T.borderColor}`, borderRadius: 10, padding: 18, boxShadow: '0 18px 60px rgba(0,0,0,0.45)' }} onClick={e => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 14 }}>
          <div>
            <div style={{ fontSize: 16, color: T.textPrimary, fontWeight: 900 }}>新增用户账号</div>
            <div style={{ color: T.textMuted, fontSize: 11, marginTop: 4 }}>创建后自动加入当前项目，并分配巡检权限角色</div>
          </div>
          <button onClick={() => setShowCreateUserModal(false)} style={{ background: 'transparent', border: 'none', color: T.textMuted, cursor: 'pointer', fontSize: 18 }}>×</button>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 5, color: T.textSecondary, fontSize: 11 }}>
            用户名 *
            <input value={userForm.username} onChange={e => setUserForm({ ...userForm, username: e.target.value })} placeholder="如 electrician_01" style={fieldStyle} />
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 5, color: T.textSecondary, fontSize: 11 }}>
            姓名 *
            <input value={userForm.realName} onChange={e => setUserForm({ ...userForm, realName: e.target.value })} placeholder="如 张电工" style={fieldStyle} />
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 5, color: T.textSecondary, fontSize: 11 }}>
            手机号
            <input value={userForm.phone} onChange={e => setUserForm({ ...userForm, phone: e.target.value })} placeholder="用于联系和展示" style={fieldStyle} />
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 5, color: T.textSecondary, fontSize: 11 }}>
            邮箱
            <input value={userForm.email} onChange={e => setUserForm({ ...userForm, email: e.target.value })} placeholder="可选" style={fieldStyle} />
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 5, color: T.textSecondary, fontSize: 11 }}>
            初始密码
            <input value={userForm.password} onChange={e => setUserForm({ ...userForm, password: e.target.value })} placeholder="默认 admin123" style={fieldStyle} />
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 5, color: T.textSecondary, fontSize: 11 }}>
            全局角色
            <select value={userForm.globalRoleCode} onChange={e => setUserForm({ ...userForm, globalRoleCode: e.target.value })} style={fieldStyle}>
              {GLOBAL_ROLE_OPTIONS.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
            </select>
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 5, color: T.textSecondary, fontSize: 11, gridColumn: '1 / -1' }}>
            当前项目职责
            <select value={userForm.projectRoleCode} onChange={e => {
              const roleCode = e.target.value;
              setUserForm({ ...userForm, projectRoleCode: roleCode, permissionTemplateId: defaultTemplateIdForRole(roleCode) });
            }} style={fieldStyle}>
              {PROJECT_ROLE_OPTIONS.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
            </select>
          </label>
          <label style={{ display: 'flex', flexDirection: 'column', gap: 5, color: T.textSecondary, fontSize: 11, gridColumn: '1 / -1' }}>
            巡检权限角色
            <select value={userForm.permissionTemplateId || defaultTemplateIdForRole(userForm.projectRoleCode)} onChange={e => setUserForm({ ...userForm, permissionTemplateId: e.target.value })} style={fieldStyle}>
              <option value="">按职责默认角色</option>
              {activePermissionTemplates.map(template => <option key={template.id} value={template.id}>{template.templateName}</option>)}
            </select>
          </label>
        </div>
        <div style={{ marginTop: 14, padding: 10, borderRadius: 7, background: T.surface2, border: `1px solid ${T.borderColor}`, color: T.textMuted, fontSize: 11, lineHeight: 1.6 }}>
          提示：负责电工通常选择“全局角色：普通用户”“项目职责：项目成员/负责电工”；安全员选择“安全管理员 + 项目安全员”。
        </div>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, marginTop: 16 }}>
          {actionButton('取消', () => setShowCreateUserModal(false), 'secondary')}
          {actionButton('创建并加入项目', submitCreateUser)}
        </div>
      </div>
    </div>
  );

  const renderWechatApprovalModal = () => selectedWechatApplication && (
    <div style={{ position: 'fixed', inset: 0, zIndex: 1010, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'rgba(15,23,42,.58)' }} onClick={() => setSelectedWechatApplication(null)}>
      <div style={{ width: 620, maxHeight: '86vh', overflow: 'auto', padding: 18, borderRadius: 10, border: `1px solid ${T.borderColor}`, background: T.modalBg }} onClick={e => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}><div><div style={{ color: T.textPrimary, fontSize: 16, fontWeight: 900 }}>审批小程序用户申请</div><div style={{ color: T.textMuted, fontSize: 11, marginTop: 4 }}>{selectedWechatApplication.realName || '未填姓名'} · {selectedWechatApplication.phone || '-'} · {selectedWechatApplication.projectName || '-'}</div></div><button onClick={() => setSelectedWechatApplication(null)} style={{ border: 0, background: 'transparent', color: T.textMuted, fontSize: 20, cursor: 'pointer' }}>×</button></div>
        <div style={{ marginTop: 14, padding: 12, borderRadius: 7, background: T.surface2, border: `1px solid ${T.borderColor}`, color: T.textSecondary, fontSize: 12, lineHeight: 1.8 }}>来源电箱：{selectedWechatApplication.boxCode || '-'}<br />匹配账号：{selectedWechatApplication.matchedUsername || '未匹配'}<br />申请类型：{selectedWechatApplication.applicationType === 'MULTIPLE_MATCH' ? '手机号匹配多个账号，必须人工确认' : selectedWechatApplication.applicationType === 'PROJECT_ACCESS' ? '已有账号申请项目权限' : '新用户注册'}</div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10, marginTop: 14 }}>
          <label style={{ color: T.textSecondary, fontSize: 11 }}>账号处理方式<select value={wechatApprovalForm.accountMode} onChange={e => setWechatApprovalForm({ ...wechatApprovalForm, accountMode: e.target.value, userId: e.target.value === 'CREATE' ? '' : wechatApprovalForm.userId })} style={{ ...fieldStyle, width: '100%', marginTop: 5 }}><option value="EXISTING">绑定已有账号</option><option value="CREATE">创建微信专用账号</option></select></label>
          {wechatApprovalForm.accountMode === 'EXISTING' ? <label style={{ color: T.textSecondary, fontSize: 11 }}>选择系统账号<select value={wechatApprovalForm.userId} onChange={e => setWechatApprovalForm({ ...wechatApprovalForm, userId: e.target.value })} style={{ ...fieldStyle, width: '100%', marginTop: 5 }}><option value="">请选择账号</option>{userOptions.map(user => <option key={user.id} value={user.id}>{user.realName || user.username} · {user.phone || user.username}</option>)}</select></label> : <div style={{ padding: 10, borderRadius: 6, background: T.surface2, color: T.textMuted, fontSize: 11 }}>将自动生成微信专用用户名和不可登录随机密码，只允许微信登录。</div>}
          <label style={{ color: T.textSecondary, fontSize: 11 }}>项目职责<select value={wechatApprovalForm.projectRoleCode} onChange={e => setWechatApprovalForm({ ...wechatApprovalForm, projectRoleCode: e.target.value, permissionTemplateId: String(defaultTemplateIdForRole(e.target.value) || '') })} style={{ ...fieldStyle, width: '100%', marginTop: 5 }}>{PROJECT_ROLE_OPTIONS.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}</select></label>
          <label style={{ color: T.textSecondary, fontSize: 11 }}>权限角色 *<select value={wechatApprovalForm.permissionTemplateId} onChange={e => setWechatApprovalForm({ ...wechatApprovalForm, permissionTemplateId: e.target.value })} style={{ ...fieldStyle, width: '100%', marginTop: 5 }}><option value="">必须选择</option>{activePermissionTemplates.map(template => <option key={template.id} value={template.id}>{template.templateName}</option>)}</select></label>
          <label style={{ gridColumn: '1 / -1', color: T.textSecondary, fontSize: 11 }}>审批意见 *<textarea value={wechatApprovalForm.comment} onChange={e => setWechatApprovalForm({ ...wechatApprovalForm, comment: e.target.value })} style={{ ...fieldStyle, width: '100%', minHeight: 76, marginTop: 5, resize: 'vertical' }} /></label>
        </div>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 9, marginTop: 16 }}>{actionButton('取消', () => setSelectedWechatApplication(null), 'secondary')}{actionButton('确认通过', submitWechatApproval)}</div>
      </div>
    </div>
  );

  const renderWechatUserDrawer = () => selectedWechatUser && (
    <div style={{ position: 'fixed', inset: 0, zIndex: 1009, display: 'flex', justifyContent: 'flex-end', background: 'rgba(15,23,42,.52)' }} onClick={() => setSelectedWechatUser(null)}>
      <div style={{ width: 680, height: '100%', overflow: 'auto', padding: 18, borderLeft: `1px solid ${T.borderColor}`, background: T.modalBg }} onClick={e => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12 }}><div><div style={{ color: T.textPrimary, fontSize: 17, fontWeight: 900 }}>{selectedWechatUser.realName || selectedWechatUser.username}</div><div style={{ color: T.textMuted, fontSize: 11, marginTop: 4 }}>{selectedWechatUser.username} · {selectedWechatUser.phone || '未填手机号'}</div></div><button onClick={() => setSelectedWechatUser(null)} style={{ border: 0, background: 'transparent', color: T.textMuted, fontSize: 20, cursor: 'pointer' }}>×</button></div>
        <div style={{ marginTop: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}><div style={{ color: T.textPrimary, fontSize: 13, fontWeight: 800 }}>微信绑定</div>{isPlatformAdmin && actionButton('增加项目授权', addWechatProjectAccess, 'secondary')}</div>
        {(selectedWechatUser.bindings || []).map(binding => <div key={binding.id} style={{ marginTop: 8, padding: 12, borderRadius: 7, border: `1px solid ${T.borderColor}`, background: T.surface2, display: 'grid', gridTemplateColumns: '1fr auto', gap: 10 }}><div style={{ color: T.textSecondary, fontSize: 11, lineHeight: 1.8 }}><b style={{ color: T.textPrimary }}>绑定 #{binding.id}</b> · {binding.status}<br />授权手机号：{binding.phone || '-'}<br />绑定：{formatDateTime(binding.bindTime)} · 最近登录：{formatDateTime(binding.lastLoginTime)}</div>{isPlatformAdmin && <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>{binding.status === 'ACTIVE' && actionButton('停用', () => changeWechatBindingStatus(binding, 'DISABLED'), 'danger')}{binding.status === 'DISABLED' && actionButton('恢复', () => changeWechatBindingStatus(binding, 'ACTIVE'))}{binding.status !== 'UNBOUND' && actionButton('解绑', () => unbindWechat(binding), 'danger')}</div>}</div>)}
        <div style={{ marginTop: 18, color: T.textPrimary, fontSize: 13, fontWeight: 800 }}>项目授权</div>
        {(selectedWechatUser.projects || []).map(access => <div key={access.memberId} style={{ marginTop: 8, padding: 12, borderRadius: 7, border: `1px solid ${T.borderColor}`, background: T.surface2 }}><div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}><b style={{ color: T.textPrimary, fontSize: 12 }}>{access.projectName || `项目${access.projectId}`}</b><InspectionPill status={access.accessStatus === 'ACTIVE' ? 'ACTIVE' : 'INACTIVE'} theme={T}>{access.accessStatus === 'ACTIVE' ? '有效' : '已暂停'}</InspectionPill></div><div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr auto', gap: 8, marginTop: 10 }}><select value={access.projectRoleCode || 'USER'} onChange={e => updateWechatProjectPermission(access, { projectRoleCode: e.target.value })} style={fieldStyle}>{PROJECT_ROLE_OPTIONS.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}</select><select value={access.permissionTemplateId || ''} onChange={e => updateWechatProjectPermission(access, { permissionTemplateId: e.target.value })} style={fieldStyle}>{activePermissionTemplates.map(template => <option key={template.id} value={template.id}>{template.templateName}</option>)}</select>{access.accessStatus === 'ACTIVE' ? actionButton('暂停', () => changeWechatProjectAccess(access, 'DISABLED'), 'danger') : actionButton('恢复', () => changeWechatProjectAccess(access, 'ACTIVE'))}</div>{access.statusReason && <div style={{ color: T.textMuted, fontSize: 10, marginTop: 7 }}>原因：{access.statusReason}</div>}</div>)}
        <div style={{ marginTop: 18, color: T.textPrimary, fontSize: 13, fontWeight: 800 }}>申请历史</div>
        {(selectedWechatUser.applications || []).map(item => <div key={item.id} style={{ marginTop: 7, padding: 10, borderRadius: 6, background: T.surface2, color: T.textSecondary, fontSize: 11 }}>{formatDateTime(item.createTime)} · {item.projectName || `项目${item.projectId}`} · {item.status}{item.reviewComment ? ` · ${item.reviewComment}` : ''}</div>)}
        <div style={{ marginTop: 18, color: T.textPrimary, fontSize: 13, fontWeight: 800 }}>操作记录</div>
        {(selectedWechatUser.operationLogs || []).map(log => <div key={log.id} style={{ marginTop: 7, padding: 10, borderLeft: `3px solid ${T.accent}`, background: T.surface2, color: T.textSecondary, fontSize: 11 }}><b style={{ color: T.textPrimary }}>{log.operationType}</b> · {log.operationDesc}<div style={{ color: T.textMuted, marginTop: 3 }}>{log.operatorName || '-'} · {formatDateTime(log.createTime)}</div></div>)}
      </div>
    </div>
  );

  const activeFilters = renderActiveFilters();

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12, height: '100%', minHeight: 0 }}>
      {activeTab !== 'permission' && renderStats()}
      {activeFilters && (
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
          {activeFilters}
          {loading && <span style={{ color: T.textMuted, fontSize: 12 }}>加载中...</span>}
        </div>
      )}
      {errorText && (
        <div style={{ border: `1px solid ${T.danger}`, color: T.danger, background: `${T.danger}14`, borderRadius: 6, padding: '8px 10px', fontSize: 12 }}>
          {errorText}
        </div>
      )}
      {activeTab === 'rectification'
        ? renderRectificationTab()
        : activeTab === 'records'
          ? renderSummaryTab()
          : activeTab === 'permission'
            ? renderPermissionTab()
            : activeTab === 'review'
              ? renderReviewTab()
              : activeTab === 'overview'
                ? renderSafetyOverview()
                : renderLedgerTab()}
      {renderRecordDrawer()}
      {renderRectificationDrawer()}
      {renderBoxDrawer()}
      {renderBoxModal()}
      {renderImportModal()}
      {renderTemplateModal()}
      {renderCreateUserModal()}
      {renderWechatApprovalModal()}
      {renderWechatUserDrawer()}
    </div>
  );
}

function ElectricInspectionPage({ projectId, theme: T, currentUser }) {
  const [activeTab, setActiveTab] = useState('ledger');
  const canManagePermissions = canManageProjectMembersByUser(currentUser, projectId);
  const visibleTabs = ELECTRIC_INSPECTION_TABS.filter(tab => !tab.permissionOnly || canManagePermissions);

  useEffect(() => {
    if (activeTab === 'permission' && !canManagePermissions) setActiveTab('ledger');
  }, [activeTab, canManagePermissions]);

  return (
    <div style={{
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
      gap: 12,
      padding: 16,
      overflow: 'hidden',
    }}>
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        flexShrink: 0,
        gap: 12,
        background: T.cardBg,
        border: `1px solid ${T.borderColor}`,
        borderRadius: 7,
        padding: '10px 12px',
      }}>
        <div>
          <div style={{ fontSize: 14, fontWeight: 800, color: T.textPrimary }}>巡检管理</div>
          <div style={{ fontSize: 11, color: T.textMuted, marginTop: 3 }}>维护巡检台账、月度记录和小程序用户权限</div>
        </div>
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
          {visibleTabs.map(tab => (
            <button key={tab.id} onClick={() => setActiveTab(tab.id)} style={{
              padding: '7px 14px',
              borderRadius: 5,
              cursor: 'pointer',
              border: `1px solid ${activeTab === tab.id ? T.accent : T.borderColor}`,
              background: activeTab === tab.id ? T.accent : T.surface2,
              color: activeTab === tab.id ? '#fff' : T.textSecondary,
              fontSize: 12,
              fontWeight: activeTab === tab.id ? 700 : 500,
            }}>{tab.label}</button>
          ))}
        </div>
      </div>
      <div style={{ flex: 1, minHeight: 0, overflow: 'hidden' }}>
        <InspectionBackendPanel projectId={projectId} theme={T} activeTab={activeTab} currentUser={currentUser} onTabChange={setActiveTab} />
      </div>
    </div>
  );
}

// ============================================
// 根组件 App
// ============================================
export default function App() {
  const [isAuth, setIsAuth] = useState(isLoggedIn());
  const [themeId, setThemeId] = useState(DEFAULT_THEME_ID);
  const [currentPage, setCurrentPage] = useState(PAGE_IDS.ELECTRIC_INSPECTION);
  const [currentProject, setCurrentProject] = useState(1);
  const [compactMode, setCompactMode] = useState(false);
  const [projectList, setProjectList] = useState([]);
  const [currentUser, setCurrentUser] = useState(null);
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
        if (res.data.length > 0) {
          setCurrentProject(prevProjectId => (
            res.data.some(project => project.id === prevProjectId) ? prevProjectId : res.data[0].id
          ));
        }
      }
    } catch (e) {
      console.error('获取项目列表失败', e);
    }
  }, []);

  const fetchCurrentUser = useCallback(async () => {
    try {
      const res = await getCurrentUser();
      if (res.code === 200 && res.data) {
        setCurrentUser(res.data);
      }
    } catch (e) {
      console.error('获取当前用户信息失败', e);
    }
  }, []);

  useEffect(() => {
    if (isAuth) {
      fetchProjectList();
      fetchCurrentUser();
    }
  }, [isAuth, fetchProjectList, fetchCurrentUser]);

  useEffect(() => {
    const handleAuthExpired = () => {
      setCurrentUser(null);
      setIsAuth(false);
    };
    window.addEventListener('site-platform-auth-expired', handleAuthExpired);
    return () => window.removeEventListener('site-platform-auth-expired', handleAuthExpired);
  }, []);

  const handleLogin = useCallback(() => {
    setIsAuth(true);
    setCurrentPage(PAGE_IDS.ELECTRIC_INSPECTION);
  }, []);

  const handleLogout = useCallback(() => {
    localStorage.removeItem('site_platform_token');
    localStorage.removeItem('site_platform_user');
    setCurrentUser(null);
    setIsAuth(false);
  }, []);

  const renderPage = () => {
    const pageProps = {
      projectId: currentProject,
      theme,
      compactMode,
      projectList,
      cameraConfig,
      currentUser,
      onEnterCameraPage: () => setShowCameraPage(true),
      onRefreshProjects: fetchProjectList,
    };
    switch (currentPage) {
      case PAGE_IDS.PERSON_MANAGEMENT:
        return <PersonnelManagementPage {...pageProps} />;
      case PAGE_IDS.QUALITY_MANAGEMENT:
        return <QualityManagementPage {...pageProps} />;
      case PAGE_IDS.DOCUMENT_MANAGEMENT:
        return <DocumentManagementPage {...pageProps} />;
      case PAGE_IDS.PERSONNEL:
        return <PersonnelPage {...pageProps} />;
      case PAGE_IDS.ELECTRIC_INSPECTION:
        return <ElectricInspectionPage {...pageProps} />;
      default:
        return <ElectricInspectionPage {...pageProps} />;
    }
  };

  if (!isAuth) {
    return <LoginPage onLogin={handleLogin} theme={theme} />;
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
          currentUser={currentUser}
        />
      </div>
    );
  }

  return (
    <div data-theme={themeId} style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', background: theme.pageBg }}>
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
        currentUser={currentUser}
      />
      <main style={{ flex: 1, overflow: 'hidden' }}>
        {renderPage()}
      </main>
    </div>
  );
}
