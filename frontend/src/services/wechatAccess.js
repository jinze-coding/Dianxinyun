import { get, post } from './api';

export function getWechatAccessApplications(params = {}) {
  return get('/wechat-access-applications', params);
}

export function approveWechatAccessApplication(id, data = {}) {
  return post(`/wechat-access-applications/${id}/approve`, data);
}

export function rejectWechatAccessApplication(id, data = {}) {
  return post(`/wechat-access-applications/${id}/reject`, data);
}
