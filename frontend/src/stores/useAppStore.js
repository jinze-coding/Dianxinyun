import { create } from 'zustand';
import { DEFAULT_THEME_ID, getThemeById } from '@/constants/themes';
import { PAGE_IDS } from '@/constants/dicts';

// 全局状态管理
export const useAppStore = create((set, get) => ({
  // 主题状态
  themeId: DEFAULT_THEME_ID,
  theme: getThemeById(DEFAULT_THEME_ID),
  compactMode: false,

  // 页面状态
  currentPage: PAGE_IDS.OVERVIEW,

  // 项目状态
  currentProjectId: 'p1',

  // 用户状态
  userInfo: {
    name: '平台管理员',
    role: 'admin',
  },

  // 设置主题
  setTheme: (themeId) => {
    const theme = getThemeById(themeId);
    set({ themeId, theme });
    document.documentElement.setAttribute('data-theme', themeId);
  },

  // 设置紧凑模式
  setCompactMode: (compactMode) => {
    set({ compactMode });
  },

  // 设置当前页面
  setCurrentPage: (page) => {
    set({ currentPage: page });
  },

  // 设置当前项目
  setCurrentProject: (projectId) => {
    set({ currentProjectId: projectId });
  },

  // 设置用户信息
  setUserInfo: (userInfo) => {
    set({ userInfo });
  },

  // 登出
  logout: () => {
    localStorage.removeItem('token');
    window.location.href = '/login';
  },
}));

export default useAppStore;
