import { useState, useCallback } from 'react';
import { ALL_THEMES, DEFAULT_THEME_ID, getThemeById } from '@/constants/themes';

// 主题Hook
export function useTheme() {
  const [themeId, setThemeId] = useState(DEFAULT_THEME_ID);
  const theme = getThemeById(themeId);

  const changeTheme = useCallback((id) => {
    const newTheme = ALL_THEMES.find(th => th.id === id);
    if (newTheme) {
      setThemeId(id);
      document.documentElement.setAttribute('data-theme', id);
    }
  }, []);

  return {
    themeId,
    theme,
    changeTheme,
  };
}

export default useTheme;
