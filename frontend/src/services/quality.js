import { get, post } from './api';

export function getQualityIssues(projectId, params = {}) {
  return get('/quality/issues', { projectId, ...params });
}

export function getQualityIssuePage(projectId, params = {}) {
  return get('/quality/issues/page', { projectId, ...params });
}

export function getQualitySummary(projectId) {
  return get('/quality/issues/summary', { projectId });
}

export function getQualityAssignees(projectId) {
  return get('/quality/issues/assignees', { projectId });
}

export function getQualityIssue(id) {
  return get(`/quality/issues/${id}`);
}

export function createQualityIssue(data) {
  return post('/quality/issues', data);
}

export function submitQualityRectification(id, data) {
  return post(`/quality/issues/${id}/rectify`, data);
}

export function reviewQualityIssue(id, data) {
  return post(`/quality/issues/${id}/review`, data);
}

export function assignQualityIssue(id, data) {
  return post(`/quality/issues/${id}/assign`, data);
}

export function voidQualityIssue(id, data) {
  return post(`/quality/issues/${id}/void`, data);
}
