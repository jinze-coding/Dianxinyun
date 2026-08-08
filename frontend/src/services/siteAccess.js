import apiClient, { ensureFileBlob, get, post, put } from './api';

export function getSiteVisitInvitations(params = {}) {
  return get('/site-access/invitations', params);
}

export function getSiteVisitInvitation(id) {
  return get(`/site-access/invitations/${id}`);
}

export function getSiteVisitHostOptions(projectId) {
  return get('/site-access/host-options', { projectId });
}

export function createSiteVisitInvitation(data) {
  return post('/site-access/invitations', data);
}

export function updateSiteVisitInvitation(id, data) {
  return put(`/site-access/invitations/${id}`, data);
}

export function voidSiteVisitInvitation(id, reason) {
  return post(`/site-access/invitations/${id}/void`, { reason });
}

export function getSiteVisitMiniCode(id) {
  return get(`/site-access/invitations/${id}/mini-code`);
}

export async function exportSiteVisitVisitors(params = {}) {
  try {
    const blob = await apiClient.get('/site-access/visitors/export', {
      params,
      responseType: 'blob',
    });
    return ensureFileBlob(blob, '外访人员导出失败');
  } catch (error) {
    const errorBlob = error?.response?.data;
    if (errorBlob instanceof Blob && String(errorBlob.type || '').toLowerCase().includes('json')) {
      try {
        const result = JSON.parse(await errorBlob.text());
        throw new Error(result.message || '外访人员导出失败');
      } catch (parseError) {
        if (!(parseError instanceof SyntaxError)) throw parseError;
      }
    }
    throw error;
  }
}
