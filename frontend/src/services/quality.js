import apiClient, { ensureFileBlob, get, post, put } from './api';

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

export function getWeeklyInspectionReminderSetting(projectId) {
  return get(`${WEEKLY_INSPECTIONS_PATH}/reminder-setting/${projectId}`);
}

export function updateWeeklyInspectionReminderSetting(projectId, data) {
  return put(`${WEEKLY_INSPECTIONS_PATH}/reminder-setting/${projectId}`, data);
}

export function getWeeklyInspectionReminderAssignees(projectId) {
  return get(`${WEEKLY_INSPECTIONS_PATH}/reminder-assignees`, { projectId });
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

export function createQualityIssueExportJob(data) {
  return post('/quality/issues/export-jobs', data);
}

export function getQualityIssueExportJobs(projectId) {
  return get('/quality/issues/export-jobs', { projectId });
}

export function getQualityIssueExportJob(id) {
  return get(`/quality/issues/export-jobs/${id}`);
}

export async function downloadQualityIssueExport(id) {
  try {
    const blob = await apiClient.get(`/quality/issues/export-jobs/${id}/download`, {
      responseType: 'blob',
    });
    return ensureFileBlob(blob, '质量问题汇总下载失败');
  } catch (error) {
    const errorBlob = error?.response?.data;
    if (errorBlob instanceof Blob && String(errorBlob.type || '').toLowerCase().includes('json')) {
      try {
        const result = JSON.parse(await errorBlob.text());
        throw new Error(result.message || '质量问题汇总下载失败');
      } catch (parseError) {
        if (!(parseError instanceof SyntaxError)) throw parseError;
      }
    }
    throw error;
  }
}
