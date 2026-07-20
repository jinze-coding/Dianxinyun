import apiClient, { ensureFileBlob } from './api';
import { get, del } from './api';

// 获取文件列表
export function getFileList(projectId, params = {}) {
  return get('/files', { projectId, ...params });
}

// 获取文件详情
export function getFileDetail(fileId) {
  return get(`/files/${fileId}`);
}

// 获取项目资料操作记录
export function getFileActivities(projectId, params = {}) {
  return get('/files/activities', { projectId, ...params });
}

// 上传文件 - 使用FormData
export function uploadFile({ file, projectId, fileName, fileType, businessType, businessId, remark }) {
  const formData = new FormData();
  formData.append('file', file);
  if (projectId) formData.append('projectId', projectId);
  if (fileName) formData.append('fileName', fileName);
  if (fileType) formData.append('fileType', fileType);
  if (businessType) formData.append('businessType', businessType);
  if (businessId) formData.append('businessId', businessId);
  if (remark) formData.append('remark', remark);

  // 使用apiClient但手动设置Content-Type为undefined，让浏览器自动处理multipart boundary
  return apiClient.post('/files', formData, {
    headers: {
      'Content-Type': undefined,
    },
  });
}

// 更新文件信息
export function updateFile(fileId, data) {
  return apiClient.put(`/files/${fileId}`, data);
}

// 替换文件内容，保留资料记录 ID 和管理信息
export function replaceFileContent(fileId, file) {
  const formData = new FormData();
  formData.append('file', file);
  return apiClient.put(`/files/${fileId}/content`, formData, {
    headers: { 'Content-Type': undefined },
  });
}

export async function previewFile(fileId) {
  const blob = await apiClient.get(`/files/${fileId}/preview`, { responseType: 'blob' });
  return ensureFileBlob(blob, '文件预览失败');
}

export async function downloadFile(fileId) {
  const blob = await apiClient.get(`/files/${fileId}/download`, { responseType: 'blob' });
  return ensureFileBlob(blob, '文件下载失败');
}

// 更新文件状态
export function updateFileStatus(fileId, status) {
  return apiClient.put(`/files/${fileId}/status`, null, { params: { status } });
}

// 删除文件
export function deleteFile(fileId) {
  return del(`/files/${fileId}`);
}
