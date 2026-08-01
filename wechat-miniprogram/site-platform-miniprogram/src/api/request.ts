import type { Result } from '@/types';

function resolveApiBaseUrl() {
  // H5 默认使用同源代理，避免手机扫码后把 127.0.0.1 误认为手机自身。
  // 微信小程序请求必须使用绝对地址，未配置时仅保留本机开发兜底。
  const configured = import.meta.env.VITE_API_BASE_URL || '/api/v1';
  // #ifdef MP-WEIXIN
  if (import.meta.env.MODE === 'mp-real') return configured.replace(/\/$/, '');
  if (!/^https?:\/\//i.test(configured)) return 'http://127.0.0.1:8080/api/v1';
  // #endif
  return configured.replace(/\/$/, '');
}

export const API_BASE_URL = resolveApiBaseUrl();
export const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true';

const TOKEN_KEY = 'site_platform_token';
let authRedirecting = false;

export function getToken(): string {
  return uni.getStorageSync(TOKEN_KEY) || '';
}

export function setToken(token: string) {
  uni.setStorageSync(TOKEN_KEY, token);
}

export function clearToken() {
  uni.removeStorageSync(TOKEN_KEY);
}

export function handleUnauthorized(message: string) {
  if (authRedirecting) return;
  authRedirecting = true;
  clearToken();
  uni.showToast({
    title: message || '登录已失效，请重新登录',
    icon: 'none'
  });
  setTimeout(() => {
    uni.reLaunch({
      url: '/pages/login/index',
      complete: () => {
        authRedirecting = false;
      }
    });
  }, 500);
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  data?: unknown;
  header?: Record<string, string>;
  skipAuthRedirect?: boolean;
  timeout?: number;
}

export function request<T>(url: string, options: RequestOptions = {}): Promise<T> {
  const token = getToken();
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${API_BASE_URL}${url}`,
      method: options.method || 'GET',
      data: options.data as Record<string, unknown>,
      timeout: options.timeout,
      header: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(options.header || {})
      },
      success: (response) => {
        const result = response.data as Result<T>;
        if (result && result.code === 200) {
          resolve(result.data);
          return;
        }
        const message = result?.message || '请求失败';
        if (result?.code === 401 && !options.skipAuthRedirect) {
          handleUnauthorized(message);
        }
        reject(new Error(message));
      },
      fail: (error) => {
        const errMsg = typeof error?.errMsg === 'string' ? error.errMsg : '';
        const message = errMsg.toLowerCase().includes('timeout')
          ? '请求超时，请检查网络后重试'
          : errMsg
          ? `无法连接后端服务：${errMsg}`
          : `无法连接后端服务，请确认 ${API_BASE_URL} 可访问`;
        reject(new Error(message));
      }
    });
  });
}
