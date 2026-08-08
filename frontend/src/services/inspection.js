import apiClient, { ensureFileBlob, get, post, put } from './api';

export function getInspectionTemplates() {
  return get('/inspection/templates');
}

export function getInspectionRecords(params = {}) {
  return get('/inspection/records', params);
}

export function getInspectionRecord(id) {
  return get(`/inspection/records/${id}`);
}

export function reviewInspectionRecord(id, data) {
  return post(`/inspection/records/${id}/review`, data);
}

export function assignInspectionReviewer(id, data = {}) {
  return post(`/inspection/records/${id}/assign-reviewer`, data);
}

export function getInspectionReviewLogs(id) {
  return get(`/inspection/records/${id}/review-logs`);
}

export function getInspectionTodos(projectId) {
  return get('/inspection/todos', projectId ? { projectId } : undefined);
}

export function getInspectionSummary(params = {}) {
  return get('/inspection/records/summary', params);
}

export function getProjectInspectionSetting(projectId) {
  return get(`/inspection/settings/${projectId}`);
}

export function updateProjectInspectionSetting(projectId, data) {
  return put(`/inspection/settings/${projectId}`, data);
}

export function getInspectionRectifications(params = {}) {
  return get('/inspection/rectifications', params);
}

export function getInspectionRectificationAssignees(projectId) {
  return get('/inspection/rectification-assignees', { projectId });
}

export function getInspectionRectification(id) {
  return get(`/inspection/rectifications/${id}`);
}

export function completeInspectionRectification(id, data = {}) {
  return post(`/inspection/rectifications/${id}/complete`, data);
}

export function closeInspectionRectification(id, data = {}) {
  return post(`/inspection/rectifications/${id}/close`, data);
}

export function rejectInspectionRectification(id, data = {}) {
  return post(`/inspection/rectifications/${id}/reject`, data);
}

export function assignInspectionRectification(id, data = {}) {
  return post(`/inspection/rectifications/${id}/assign`, data);
}

export function escalateInspectionRectification(id, data = {}) {
  return post(`/inspection/rectifications/${id}/escalate`, data);
}

export async function exportInspectionRecords(params = {}) {
  try {
    const blob = await apiClient.get('/inspection/records/export', {
      params,
      responseType: 'blob',
    });
    return ensureFileBlob(blob, '导出失败');
  } catch (error) {
    const errorBlob = error?.response?.data;
    if (errorBlob instanceof Blob && String(errorBlob.type || '').toLowerCase().includes('json')) {
      try {
        const result = JSON.parse(await errorBlob.text());
        throw new Error(result.message || '导出失败');
      } catch (parseError) {
        if (!(parseError instanceof SyntaxError)) throw parseError;
      }
    }
    throw error;
  }
}

export async function downloadFileAsObjectUrl(fileId) {
  if (!fileId) return '';
  const blob = await apiClient.get(`/files/${fileId}/download`, { responseType: 'blob' });
  return URL.createObjectURL(blob);
}
