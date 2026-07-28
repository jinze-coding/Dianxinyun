import { createSSRApp } from 'vue';
import App from './App.vue';
import { canAccessRoot } from '@/stores/auth';

const ROUTE_PERMISSION_RULES: Array<[string, string]> = [
  ['/pages/documents/', '/pages/documents/index'],
  ['/pages/inspection/', '/pages/inspection/index'],
  ['/pages/quality/', '/pages/quality/index']
];
let interceptorsInstalled = false;

function installPermissionNavigationGuards() {
  if (interceptorsInstalled) return;
  interceptorsInstalled = true;
  const guard = {
    invoke(args: { url?: string }) {
      const url = String(args?.url || '').split('?')[0];
      const rule = ROUTE_PERMISSION_RULES.find(([prefix]) => url.startsWith(prefix));
      if (!rule || canAccessRoot(rule[1])) return true;
      uni.showToast({ title: '当前账号无此功能权限', icon: 'none' });
      return false;
    }
  };
  uni.addInterceptor('navigateTo', guard);
  uni.addInterceptor('redirectTo', guard);
  uni.addInterceptor('switchTab', guard);
  uni.addInterceptor('reLaunch', guard);
}

export function createApp() {
  installPermissionNavigationGuards();
  const app = createSSRApp(App);
  return {
    app
  };
}
