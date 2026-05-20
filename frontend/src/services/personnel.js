import { get, post, put, del } from './api';

// 获取人员列表
export function getPersonnelList(projectId, params = {}) {
  return get('/personnel', { projectId, ...params });
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
  return put('/personnel/batch/status', null, { params: { ids, status } });
}
