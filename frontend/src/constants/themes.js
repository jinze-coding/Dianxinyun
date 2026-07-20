// 主题配置：Web 端仅保留白色主题。
const WHITE_THEME = {
  id: 'T6',
  name: '白色主题',
  brightness: '白色',
  pageBg: '#f0f4f9',
  navBg: '#ffffff',
  cardBg: '#ffffff',
  surface2: '#f5f8fc',
  modalBg: '#ffffff',
  dropdownBg: '#ffffff',
  activeItemBg: 'rgba(22,119,255,0.08)',
  hoverBg: 'rgba(0,0,0,0.03)',
  tagBg: 'rgba(0,0,0,0.05)',
  borderColor: '#dce5f0',
  accent: '#1677ff',
  accent2: '#36a3f7',
  success: '#16a34a',
  warning: '#d97706',
  danger: '#dc2626',
  textPrimary: '#0f1a2e',
  textSecondary: '#3a5070',
  textMuted: '#8aa0bc',
  navHeight: '54px',
  radius: '8px',
};

export const ALL_THEMES = [WHITE_THEME];

export const DEFAULT_THEME_ID = WHITE_THEME.id;

export const getThemeById = (id) => {
  return ALL_THEMES.find((th) => th.id === id) || WHITE_THEME;
};
