import { del, get, post, put } from './api';

export function getSystemRegistrationApplications(params = {}) {
  return get('/system/registration-applications', params);
}

export function getSystemRegistrationApplication(id) {
  return get(`/system/registration-applications/${id}`);
}

export function approveSystemRegistrationApplication(id, data) {
  return post(`/system/registration-applications/${id}/approve`, data);
}

export function rejectSystemRegistrationApplication(id, data) {
  return post(`/system/registration-applications/${id}/reject`, data);
}

export function getSystemUsers(params = {}) {
  return get('/system/users', params);
}

export function getSystemUser(id) {
  return get(`/system/users/${id}`);
}

export function updateSystemUserStatus(id, data) {
  return put(`/system/users/${id}/status`, data);
}

export function resetSystemUserPassword(id, data) {
  return post(`/system/users/${id}/reset-password`, data);
}

export function updateSystemUserRoles(id, data) {
  return put(`/system/users/${id}/roles`, data);
}

export function updateSystemUserProjectRoleAssignments(id, data) {
  return put(`/system/users/${id}/project-role-assignments`, data);
}

export function previewSystemUserProjectRoleAssignments(id, data) {
  return post(`/system/users/${id}/project-role-assignments/preview`, data);
}

export function getSystemRoles(params = {}) {
  return get('/system/roles', params);
}

export function createSystemRole(data) {
  return post('/system/roles', data);
}

export function updateSystemRole(id, data) {
  return put(`/system/roles/${id}`, data);
}

export function deleteSystemRole(id) {
  return del(`/system/roles/${id}`);
}

export function updateSystemRolePermissions(id, data) {
  return put(`/system/roles/${id}/permissions`, data);
}

export function updateSystemRoleMenus(id, data) {
  return put(`/system/roles/${id}/menus`, data);
}

export function updateSystemRoleOperationPermissions(id, data) {
  return put(`/system/roles/${id}/operation-permissions`, data);
}

export function getSystemMenus(params = {}) {
  return get('/system/menus', params);
}

export function updateSystemMenuStatus(id, enabled) {
  return put(`/system/menus/${id}/status`, { enabled: enabled ? 1 : 0 });
}

export function createSystemMenu(data) {
  return post('/system/menus', data);
}

export function updateSystemMenu(id, data) {
  return put(`/system/menus/${id}`, data);
}

export function getSystemPermissions(params = {}) {
  return get('/system/permissions', params);
}

export function createSystemPermission(data) {
  return post('/system/permissions', data);
}

export function updateSystemPermission(id, data) {
  return put(`/system/permissions/${id}`, data);
}

export function getSystemAuditLogs(params = {}) {
  return get('/system/audit-logs', params);
}

export function getSystemWechatBindings(params = {}) {
  return get('/system/wechat-bindings', params);
}

export function updateSystemWechatBindingStatus(userId, bindingId, data) {
  return put(`/system/wechat-bindings/${userId}/${bindingId}/status`, data);
}

export function unbindSystemWechatBinding(userId, bindingId, data) {
  return post(`/system/wechat-bindings/${userId}/${bindingId}/unbind`, data);
}
