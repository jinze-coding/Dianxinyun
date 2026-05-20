import apiClient from './api';
import { get, del } from './api';

// 获取摄像头列表
export function getCameraList(projectId) {
  return get('/cameras', { projectId });
}

// 获取摄像头详情
export function getCameraDetail(cameraId) {
  return get(`/cameras/${cameraId}`);
}

export function createCamera(data) {
  return apiClient.post('/cameras', data);
}

export function updateCamera(id, data) {
  return apiClient.put(`/cameras/${id}`, data);
}

export function deleteCamera(id) {
  return del(`/cameras/${id}`);
}

// 获取设备列表
export function getDeviceList(projectId, params = {}) {
  return get('/devices', { projectId, ...params });
}

// 获取设备详情
export function getDeviceDetail(deviceId) {
  return get(`/devices/${deviceId}`);
}

// 获取塔吊设备列表
export function getTowerCraneList(projectId) {
  return get('/devices/tower-cranes', { projectId });
}

// 创建设备
export function createDevice(data) {
  return apiClient.post('/devices', data);
}

// 更新设备
export function updateDevice(id, data) {
  return apiClient.put(`/devices/${id}`, data);
}

// 删除设备
export function deleteDevice(id) {
  return del(`/devices/${id}`);
}
