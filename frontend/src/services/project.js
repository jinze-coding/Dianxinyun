import { get, post, put, del } from './api';

// 获取项目列表
export function getProjectList() {
  return get('/projects');
}

// 获取项目详情
export function getProjectDetail(projectId) {
  return get(`/projects/${projectId}`);
}

// 获取项目地图点位
export function getProjectMapPoints() {
  return get('/projects/map-points');
}

// 获取项目地图详情
export function getProjectMapDetail(projectId) {
  return get(`/projects/${projectId}/map-detail`);
}

// 更新项目定位信息
export function updateProjectLocation(projectId, data) {
  return put(`/projects/${projectId}/location`, data);
}

// 获取项目统计数据
export function getProjectStats(projectId) {
  return get(`/projects/${projectId}/stats`);
}

// 添加项目
export function addProject(data) {
  return post('/projects', data);
}

// 删除项目
export function deleteProject(projectId) {
  return del(`/projects/${projectId}`);
}

// 更新项目
export function updateProject(projectId, data) {
  return put(`/projects/${projectId}`, data);
}
