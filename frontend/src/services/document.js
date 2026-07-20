import apiClient, { del, ensureFileBlob, get, post, put } from './api';

export const getDocumentFolders = (projectId) => get('/document-folders', { projectId });
export const createDocumentFolder = (data) => post('/document-folders', data);
export const updateDocumentFolder = (id, data) => put(`/document-folders/${id}`, data);
export const deleteDocumentFolder = (id) => del(`/document-folders/${id}`);

export const getProjectDocuments = (params) => get('/project-documents', params);
export const getProjectDocumentSummary = (projectId) => get('/project-documents/summary', { projectId });
export const getProjectDocumentRecycleBin = (params) => get('/project-documents/recycle-bin', params);
export const getProjectDocumentDetail = (id) => get(`/project-documents/${id}`);

export function createProjectDocument(data) {
  const formData = new FormData();
  formData.append('file', data.file);
  formData.append('projectId', data.projectId);
  formData.append('folderId', data.folderId || 0);
  formData.append('title', data.title);
  if (data.documentNo) formData.append('documentNo', data.documentNo);
  if (data.remark) formData.append('remark', data.remark);
  if (data.changeNote) formData.append('changeNote', data.changeNote);
  return apiClient.post('/project-documents', formData, { headers: { 'Content-Type': undefined } });
}

export function uploadProjectDocumentVersion(id, file, changeNote) {
  const formData = new FormData();
  formData.append('file', file);
  if (changeNote) formData.append('changeNote', changeNote);
  return apiClient.post(`/project-documents/${id}/versions`, formData, { headers: { 'Content-Type': undefined } });
}

export const updateProjectDocument = (id, data) => put(`/project-documents/${id}`, data);
export const archiveProjectDocument = (id) => post(`/project-documents/${id}/archive`);
export const unarchiveProjectDocument = (id) => post(`/project-documents/${id}/unarchive`);
export const deleteProjectDocument = (id) => del(`/project-documents/${id}`);
export const restoreProjectDocument = (id) => post(`/project-documents/${id}/restore`);
export const purgeProjectDocument = (id) => del(`/project-documents/${id}/purge`);
export const batchProjectDocuments = (data) => post('/project-documents/batch', data);

export const previewProjectDocument = async (id, versionId) => ensureFileBlob(
  await apiClient.get(`/project-documents/${id}/preview`, {
    params: versionId ? { versionId } : undefined,
    responseType: 'blob',
  }),
  '资料预览失败',
);

export const downloadProjectDocument = async (id, versionId) => ensureFileBlob(
  await apiClient.get(`/project-documents/${id}/download`, {
    params: versionId ? { versionId } : undefined,
    responseType: 'blob',
  }),
  '资料下载失败',
);
