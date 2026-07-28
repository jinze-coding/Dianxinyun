import { mockUser } from '@/mock/data';
import type { User } from '@/types';
import { request, setToken, clearToken, USE_MOCK } from './request';

export interface LoginPayload {
  username: string;
  password: string;
}

export interface WechatSessionResponse {
  bindingStatus: 'BOUND' | 'UNBOUND' | 'BOUND_NO_PROJECT_ACCESS' | 'PENDING_APPROVAL' | 'BINDING_DISABLED' | 'PROJECT_ACCESS_DISABLED' | 'APPLICATION_REJECTED';
  applicationStatus?: string;
  token?: string;
  user?: User;
  wechatSessionToken?: string;
  projectId?: number;
  sourceId?: number;
  message: string;
}

export interface LoginResponse {
  token: string;
  userId?: number;
  username?: string;
  realName?: string;
  user?: User;
}

export interface MiniWechatLoginResponse extends Partial<LoginResponse> {
  bindingStatus: WechatSessionResponse['bindingStatus'];
  wechatSessionToken?: string;
  applicationStatus?: string;
  message: string;
}

export interface WebQrChallengeInfo {
  challengeId: string;
  status: 'WAITING' | 'SCANNED' | 'CONFIRMED' | 'CANCELLED' | 'EXPIRED' | 'CONSUMED';
  siteName?: string;
  browserName?: string;
  ipRegion?: string;
  expiresAt?: string;
  message?: string;
}

export async function requestWechatProjectAccess(scene: string): Promise<WechatSessionResponse> {
  return request<WechatSessionResponse>('/auth/wechat/project-access', {
    method: 'POST', data: { scene }
  });
}

export async function wechatSession(code: string, scene: string): Promise<WechatSessionResponse> {
  const data = await request<WechatSessionResponse>('/auth/wechat/session', {
    method: 'POST', data: { code, scene }, skipAuthRedirect: true
  });
  if (data.token) setToken(data.token);
  return data;
}

export async function bindWechatPhone(payload: {
  wechatSessionToken: string;
  phoneCode?: string;
  phone?: string;
  realName: string;
  scene: string;
}): Promise<WechatSessionResponse> {
  const data = await request<WechatSessionResponse>('/auth/wechat/phone', {
    method: 'POST', data: payload, skipAuthRedirect: true
  });
  if (data.token) setToken(data.token);
  return data;
}

export async function login(payload: LoginPayload): Promise<User> {
  if (USE_MOCK) {
    setToken('mock-token');
    return mockUser;
  }
  const data = await request<LoginResponse>('/auth/login', {
    method: 'POST',
    data: payload
  });
  setToken(data.token);
  return getCurrentUser();
}

export async function getCurrentUser(): Promise<User> {
  if (USE_MOCK) {
    return mockUser;
  }
  return request<User>('/auth/user-info');
}

export async function logout() {
  try {
    if (!USE_MOCK) {
      await request<void>('/auth/logout', { method: 'POST' });
    }
  } finally {
    clearToken();
  }
}

export async function miniWechatLogin(code: string, scene?: string): Promise<MiniWechatLoginResponse> {
  const data = await request<MiniWechatLoginResponse>('/auth/wechat/mini/login', {
    method: 'POST',
    data: { code, scene },
    skipAuthRedirect: true
  });
  if (data.token) setToken(data.token);
  return data;
}

export async function bindWechatAccount(payload: {
  username: string;
  password: string;
  code: string;
  wechatSessionToken?: string;
}): Promise<LoginResponse> {
  const data = await request<LoginResponse>('/auth/wechat/mini/bind-login', {
    method: 'POST',
    data: { ...payload, wechatCode: payload.code },
    skipAuthRedirect: true
  });
  setToken(data.token);
  return data;
}

export async function bindCurrentUserWechat(code: string, password?: string): Promise<void> {
  await request<void>('/auth/wechat/bind', {
    method: 'POST',
    data: { code, wechatCode: code, password }
  });
}

export async function unbindCurrentUserWechat(password?: string): Promise<void> {
  await request<void>('/auth/wechat/unbind', {
    method: 'POST',
    data: { password }
  });
  clearToken();
}

export async function markWebQrScanned(challengeId: string): Promise<WebQrChallengeInfo> {
  return request<WebQrChallengeInfo>(`/auth/web-qr/challenges/${encodeURIComponent(challengeId)}/mark-scanned`, {
    method: 'POST'
  });
}

export async function confirmWebQr(challengeId: string): Promise<void> {
  await request<void>(`/auth/web-qr/challenges/${encodeURIComponent(challengeId)}/confirm`, { method: 'POST' });
}

export async function cancelWebQr(challengeId: string): Promise<void> {
  await request<void>(`/auth/web-qr/challenges/${encodeURIComponent(challengeId)}/cancel`, { method: 'POST' });
}
