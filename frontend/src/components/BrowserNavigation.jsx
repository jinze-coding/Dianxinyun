import { createContext, useContext, useEffect, useState } from 'react';
import { browserNavigationStorage, readBrowserNavigation, sameNavigationPage, writeBrowserNavigation } from '../utils/browserNavigation';

export const BrowserNavigationContext = createContext(null);

// The provider is keyed by account/project/page, so a new scope cannot reuse old tab state.
export function useNavigationTab(key, fallback, allowed) {
  const context = useContext(BrowserNavigationContext);
  const accepts = (item) => typeof item === 'string' && (typeof allowed === 'function' ? allowed(item) : allowed.includes(item));
  const [value, setValue] = useState(() => {
    const saved = readBrowserNavigation(browserNavigationStorage());
    return sameNavigationPage(saved, context) && accepts(saved.tabs[key]) ? saved.tabs[key] : fallback;
  });
  useEffect(() => {
    if (context && accepts(value)) writeBrowserNavigation(browserNavigationStorage(), context, { [key]: value });
  }, [context, key, value, allowed]);
  return [value, setValue];
}
