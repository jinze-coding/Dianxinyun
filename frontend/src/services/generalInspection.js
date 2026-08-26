import apiClient, { ensureFileBlob, get, post, put } from './api';

const ROOT = '/general-inspections';

export const getGeneralInspectionFeature = (projectId) => get(`${ROOT}/projects/${projectId}/feature`);
export const updateGeneralInspectionFeature = (projectId, data) => put(`${ROOT}/projects/${projectId}/feature`, data);
export const getGeneralInspectionTemplates = (projectId) => get(`${ROOT}/templates`, { projectId });
export const getGeneralInspectionTemplate = (id) => get(`${ROOT}/templates/${id}`);
export const previewGeneralInspectionTemplate = (id) => get(`${ROOT}/templates/${id}/preview`);
export const createGeneralInspectionTemplate = (data) => post(`${ROOT}/templates`, data);
export const updateGeneralInspectionTemplate = (id, data) => put(`${ROOT}/templates/${id}`, data);
export const copyGeneralInspectionTemplate = (id, data) => post(`${ROOT}/templates/${id}/copy`, data);
export const publishGeneralInspectionTemplate = (id, data) => post(`${ROOT}/templates/${id}/publish`, data);
export const archiveGeneralInspectionTemplate = (id, data) => post(`${ROOT}/templates/${id}/archive`, data);

export const getGeneralInspectionCategories = (projectId) => get(`${ROOT}/point-categories`, { projectId });
export const createGeneralInspectionCategory = (data) => post(`${ROOT}/point-categories`, data);
export const getGeneralInspectionPoints = (projectId, status) => get(`${ROOT}/points`, { projectId, status });
export const createGeneralInspectionPoint = (data) => post(`${ROOT}/points`, data);
export const updateGeneralInspectionPoint = (id, data) => put(`${ROOT}/points/${id}`, data);
export const rotateGeneralInspectionPointCode = (id, data) => post(`${ROOT}/points/${id}/rotate-code`, data);
export const getGeneralInspectionPointQr = (id) => get(`${ROOT}/points/${id}/qr`);
export const changeGeneralInspectionPointStatus = (id, status, data) => post(`${ROOT}/points/${id}/status/${status}`, data);
export const getGeneralInspectionUserOptions = (projectId) => get(`${ROOT}/user-options`, { projectId });

export const getGeneralInspectionPlans = (projectId) => get(`${ROOT}/plans`, { projectId });
export const createGeneralInspectionPlan = (data) => post(`${ROOT}/plans`, data);
export const updateGeneralInspectionPlan = (id, data) => put(`${ROOT}/plans/${id}`, data);
export const previewGeneralInspectionPlan = (data, days = 14) => apiClient.post(`${ROOT}/plans/preview`, data, { params: { days } });
export const publishGeneralInspectionPlan = (id, data) => post(`${ROOT}/plans/${id}/publish`, data);
export const changeGeneralInspectionPlanStatus = (id, status, data) => post(`${ROOT}/plans/${id}/status/${status}`, data);

export const getGeneralInspectionTasks = (params = {}) => get(`${ROOT}/tasks`, params);
export const getGeneralInspectionTask = (id) => get(`${ROOT}/tasks/${id}`);
export const cancelGeneralInspectionTasks = (data) => post(`${ROOT}/tasks/cancel`, data);
export const reassignGeneralInspectionTask = (id, data) => post(`${ROOT}/tasks/${id}/reassign`, data);
export const correctGeneralInspectionTask = (id, data) => post(`${ROOT}/tasks/${id}/correct`, data);
export const appendGeneralInspectionCorrectionNote = (id, data) => post(`${ROOT}/tasks/${id}/correction-note`, data);
export const voidGeneralInspectionForReinspection = (id, data) => post(`${ROOT}/tasks/${id}/void-reinspect`, data);
export const getGeneralInspectionDashboard = (params) => get(`${ROOT}/dashboard`, params);
export const createGeneralInspectionExport = (data) => post(`${ROOT}/exports`, data);
export const getGeneralInspectionExports = (projectId) => get(`${ROOT}/exports`, { projectId });

export async function downloadGeneralInspectionExport(id) {
  const blob = await apiClient.get(`${ROOT}/exports/${id}/download`, { responseType: 'blob' });
  return ensureFileBlob(blob, '通用巡检导出下载失败');
}
