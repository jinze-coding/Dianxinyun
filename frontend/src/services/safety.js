import { get, post, put, del } from './api';

// 获取培训批次列表
export function getTrainingList(projectId) {
  return get('/safety-education', { projectId });
}

// 获取培训批次详情
export function getTrainingDetail(batchId) {
  return get(`/safety-education/${batchId}`);
}

// 创建培训批次
export function createTraining(data) {
  return post('/safety-education', data);
}

// 更新培训批次
export function updateTraining(id, data) {
  return put(`/safety-education/${id}`, data);
}

// 标记培训完成
export function markTrainingComplete(batchId) {
  return put(`/safety-education/${batchId}/complete`);
}

// 删除培训批次
export function deleteTraining(batchId) {
  return del(`/safety-education/${batchId}`);
}
