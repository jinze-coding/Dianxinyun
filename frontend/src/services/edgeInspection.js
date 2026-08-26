import { get, post, put } from './api';

const ROOT = '/edge-inspections';

export const getEdgeInspectionFeature = (projectId) => get(`${ROOT}/projects/${projectId}/feature`);
export const updateEdgeInspectionFeature = (projectId, data) => put(`${ROOT}/projects/${projectId}/feature`, data);

export const getEdgeInspectionPointTypes = (projectId) => get(`${ROOT}/point-types`, { projectId });
export const getEdgeInspectionPoints = (projectId, status) => get(`${ROOT}/points`, { projectId, status });
export const createEdgeInspectionPoint = (data) => post(`${ROOT}/points`, data);
export const updateEdgeInspectionPoint = (id, data) => put(`${ROOT}/points/${id}`, data);
export const changeEdgeInspectionPointStatus = (id, status, data) => post(`${ROOT}/points/${id}/status/${status}`, data);

export const getEdgeInspectionSetting = (projectId) => get(`${ROOT}/projects/${projectId}/setting`);
export const updateEdgeInspectionSetting = (projectId, data) => put(`${ROOT}/projects/${projectId}/setting`, data);
export const getEdgeInspectionUserOptions = (projectId) => get(`${ROOT}/user-options`, { projectId });

export const getEdgeInspectionTasks = (params = {}) => get(`${ROOT}/tasks`, params);
export const getEdgeInspectionTask = (id) => get(`${ROOT}/tasks/${id}`);
export const cancelEdgeInspectionTasks = (data) => post(`${ROOT}/tasks/cancel`, data);
export const reassignEdgeInspectionTask = (id, data) => post(`${ROOT}/tasks/${id}/reassign`, data);

export const getEdgeInspectionRectifications = (params = {}) => get(`${ROOT}/rectifications`, params);
export const getEdgeInspectionRectification = (taskId) => get(`${ROOT}/rectifications/${taskId}`);
export const reassignEdgeInspectionRectification = (taskId, data) => post(`${ROOT}/rectifications/${taskId}/reassign`, data);

export const getEdgeInspectionStatistics = (params) => get(`${ROOT}/statistics`, params);
