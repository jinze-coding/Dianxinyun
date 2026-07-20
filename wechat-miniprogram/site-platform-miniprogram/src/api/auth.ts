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
  const data = await request<{ token: string; userId: number; username: string; realName: string }>('/auth/login', {
    method: 'POST',
    data: payload
  });
  setToken(data.token);
  try {
    return await getCurrentUser();
  } catch {
    return {
      id: data.userId,
      username: data.username,
      realName: data.realName,
      roles: ['USER']
    };
  }
}

export async function getCurrentUser(): Promise<User> {
  if (USE_MOCK) {
    return mockUser;
  }
  return request<User>('/auth/user-info');
}

export async function logout() {
  if (!USE_MOCK) {
    await request<void>('/auth/logout', { method: 'POST' });
  }
  clearToken();
}
