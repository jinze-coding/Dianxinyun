import { get, post, put } from './api';

const WEEKLY_INSPECTIONS_PATH = '/quality/weekly-inspections';

export function getWeeklyInspectionPage(projectId, params = {}) {
  return get(`${WEEKLY_INSPECTIONS_PATH}/page`, { projectId, ...params });
}

export function getWeeklyInspectionSummary(projectId) {
  return get(`${WEEKLY_INSPECTIONS_PATH}/summary`, { projectId });
}

export function getWeeklyInspection(id) {
  return get(`${WEEKLY_INSPECTIONS_PATH}/${id}`);
}

export function createOrResumeWeeklyInspectionDraft(data) {
  return post(`${WEEKLY_INSPECTIONS_PATH}/drafts`, data);
}

export function saveWeeklyInspectionDraft(id, data) {
  return put(`${WEEKLY_INSPECTIONS_PATH}/${id}/draft`, data);
}

export function submitWeeklyInspection(id, expectedVersion) {
  return post(`${WEEKLY_INSPECTIONS_PATH}/${id}/submit`, { expectedVersion });
}

export function discardWeeklyInspectionDraft(id, expectedVersion) {
  return post(`${WEEKLY_INSPECTIONS_PATH}/${id}/discard`, { expectedVersion });
}

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
