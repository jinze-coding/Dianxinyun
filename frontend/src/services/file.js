import apiClient from './api';
import { get, del } from './api';

// 获取文件列表
export function getFileList(projectId, params = {}) {
  return get('/files', { projectId, ...params });
}

// 获取文件详情
export function getFileDetail(fileId) {
  return get(`/files/${fileId}`);
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

// 更新文件状态
export function updateFileStatus(fileId, status) {
  return apiClient.put(`/files/${fileId}/status`, null, { params: { status } });
}

// 删除文件
export function deleteFile(fileId) {
  return del(`/files/${fileId}`);
}
