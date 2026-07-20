import { get, put, post } from './api';

export function getWechatUsers(params = {}) {
  return get('/wechat-users', params);
}

export function getWechatUserDetail(userId) {
  return get(`/wechat-users/${userId}`);
}

export function updateWechatBindingStatus(userId, bindingId, data) {
  return put(`/wechat-users/${userId}/bindings/${bindingId}/status`, data);
}

export function unbindWechatUser(userId, bindingId, data) {
  return post(`/wechat-users/${userId}/bindings/${bindingId}/unbind`, data);
}
