import apiClient, { get, post, put, del } from './api';

// 获取人员列表
export function getPersonnelList(projectId, params = {}) {
  return get('/personnel', { projectId, ...params });
}

export function getPersonnelSummary(projectId) {
  return get('/personnel/summary', { projectId });
}

// 获取人员详情
export function getPersonnelDetail(personnelId) {
  return get(`/personnel/${personnelId}`);
}

// 新增人员
export function addPersonnel(data) {
  return post('/personnel', data);
}

// 更新人员
export function updatePersonnel(personnelId, data) {
  return put(`/personnel/${personnelId}`, data);
}

// 删除人员
export function deletePersonnel(personnelId) {
  return del(`/personnel/${personnelId}`);
}

// 批量更新人员状态
export function batchUpdatePersonnelStatus(ids, status) {
  return apiClient.put('/personnel/batch/status', null, { params: { ids, status } });
}

export function enterPersonnel(personnelId, data = {}) {
  return post(`/personnel/${personnelId}/entry`, data);
}

export function exitPersonnel(personnelId, data = {}) {
  return post(`/personnel/${personnelId}/exit`, data);
}

export function getPersonnelMovements(personnelId) {
  return get(`/personnel/${personnelId}/movements`);
}

export function getPersonnelCertificates(projectId, personId) {
  return get('/personnel/certificates', { projectId, personId });
}

export function createPersonnelCertificate(personnelId, data) {
  return post(`/personnel/${personnelId}/certificates`, data);
}

export function updatePersonnelCertificate(certificateId, data) {
  return put(`/personnel/certificates/${certificateId}`, data);
}

export function deletePersonnelCertificate(certificateId) {
  return del(`/personnel/certificates/${certificateId}`);
}
