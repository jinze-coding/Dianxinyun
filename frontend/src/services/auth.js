import { get, post } from './api';

const TOKEN_KEY = 'site_platform_token';
const USER_INFO_KEY = 'site_platform_user';

// 保存token
export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token);
}

// 获取token
export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

// 保存用户信息
export function setUserInfo(userInfo) {
  localStorage.setItem(USER_INFO_KEY, JSON.stringify(userInfo));
}

// 获取用户信息
export function getUserInfo() {
  const info = localStorage.getItem(USER_INFO_KEY);
  return info ? JSON.parse(info) : null;
}

// 清除登录信息
export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_INFO_KEY);
}

// 登录
export function login(username, password) {
  return post('/auth/login', { username, password }).then((res) => {
    if (res.code === 200 && res.data) {
      setToken(res.data.token);
      setUserInfo(res.data);
    }
    return res;
  });
}

// 获取 Web 注册验证码
export function getRegistrationCaptcha() {
  return get('/auth/captcha');
}

// 创建 Web 扫码登录挑战
export function createWebQrChallenge(data = {}) {
  return post('/auth/web-qr/challenges', data);
}

// 查询 Web 扫码登录状态
export function getWebQrChallengeStatus(challengeId, data) {
  return post(`/auth/web-qr/challenges/${challengeId}/status`, data);
}

// 使用一次性交换码换取登录 token
export function exchangeWebQrChallenge(challengeId, data) {
  return post(`/auth/web-qr/challenges/${challengeId}/exchange`, data).then((res) => {
    if (res.code === 200 && res.data?.token) {
      setToken(res.data.token);
      setUserInfo(res.data);
    }
    return res;
  });
}

// 获取当前用户信息
export function getCurrentUser() {
  return get('/auth/user-info');
}

// 登出
export function logout() {
  return post('/auth/logout').then((res) => {
    clearAuth();
    return res;
  });
}

// 检查是否已登录
export function isLoggedIn() {
  return !!getToken();
}
