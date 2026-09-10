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

export function getMeetingVisitRegistrations(invitationId, params = {}) {
  return get(`/site-access/invitations/${invitationId}/meeting-registrations`, params);
}

export function getMeetingVisitRegistration(id) {
  return get(`/site-access/meeting-registrations/${id}`);
}

export function updateMeetingVisitRegistration(id, data) {
  return put(`/site-access/meeting-registrations/${id}`, data);
}

export function voidMeetingVisitRegistration(id, reason, version) {
  return post(`/site-access/meeting-registrations/${id}/void`, { reason, version });
}

export async function exportMeetingVisitRegistrations(params = {}) {
  try {
    const blob = await apiClient.get('/site-access/meeting-registrations/export', {
      params,
      responseType: 'blob',
    });
    return ensureFileBlob(blob, '会议登记导出失败');
  } catch (error) {
    const errorBlob = error?.response?.data;
    if (errorBlob instanceof Blob && String(errorBlob.type || '').toLowerCase().includes('json')) {
      try {
        const result = JSON.parse(await errorBlob.text());
        throw new Error(result.message || '会议登记导出失败');
      } catch (parseError) {
        if (!(parseError instanceof SyntaxError)) throw parseError;
      }
    }
    throw error;
  }
}

export function getMeetingCheckinSettings(invitationId) {
  return get(`/site-access/invitations/${invitationId}/meeting-check-in/settings`);
}

export function updateMeetingCheckinSettings(invitationId, data) {
  return put(`/site-access/invitations/${invitationId}/meeting-check-in/settings`, data);
}

export function updateMeetingCheckinStatus(invitationId, data) {
  return post(`/site-access/invitations/${invitationId}/meeting-check-in/status`, data);
}

export function rotateMeetingCheckinQr(invitationId, version) {
  return post(`/site-access/invitations/${invitationId}/meeting-check-in/rotate`, { version });
}

export function getMeetingCheckinMiniCode(invitationId) {
  return get(`/site-access/invitations/${invitationId}/meeting-check-in/mini-code`);
}

export function getMeetingAttendanceSummary(invitationId) {
  return get(`/site-access/invitations/${invitationId}/meeting-attendance/summary`);
}

export function getMeetingAttendanceScreen(invitationId, pageNo = 1) {
  return get(`/site-access/invitations/${invitationId}/meeting-attendance/screen`, { pageNo });
}

export function getMeetingAttendees(invitationId, params = {}) {
  return get(`/site-access/invitations/${invitationId}/meeting-attendees`, params);
}

export function createMeetingWalkIn(invitationId, data) {
  return post(`/site-access/invitations/${invitationId}/meeting-attendees/walk-ins`, data);
}

export function manualMeetingCheckIn(personId, data) {
  return post(`/site-access/meeting-attendees/${personId}/manual-check-in`, data);
}

export function revokeMeetingCheckIn(personId, data) {
  return post(`/site-access/meeting-attendees/${personId}/revoke`, data);
}

export function updateMeetingAttendee(personId, data) {
  return put(`/site-access/meeting-attendees/${personId}`, data);
}

export async function exportMeetingAttendance(params = {}) {
  try {
    const blob = await apiClient.get('/site-access/meeting-attendance/export', {
      params,
      responseType: 'blob',
    });
    return ensureFileBlob(blob, '会议签到导出失败');
  } catch (error) {
    const errorBlob = error?.response?.data;
    if (errorBlob instanceof Blob && String(errorBlob.type || '').toLowerCase().includes('json')) {
      try {
        const result = JSON.parse(await errorBlob.text());
        throw new Error(result.message || '会议签到导出失败');
      } catch (parseError) {
        if (!(parseError instanceof SyntaxError)) throw parseError;
      }
    }
    throw error;
  }
}

export function getSiteVisitorProfiles(params = {}) {
  return get('/site-access/visitor-profiles', params);
}

export function getSiteVisitorProfile(id) {
  return get(`/site-access/visitor-profiles/${id}`);
}

export function disableSiteVisitorProfile(id) {
  return post(`/site-access/visitor-profiles/${id}/disable`);
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

export function getGuardVisitQr(projectId) {
  return get('/site-access/guard/qr', { projectId });
}

export function createGuardVisitQr(projectId) {
  return post(`/site-access/guard/qr?projectId=${encodeURIComponent(projectId)}`);
}

export function updateGuardVisitQrStatus(id, data) {
  return post(`/site-access/guard/qr/${id}/status`, data);
}

export function rotateGuardVisitQr(id, version) {
  return post(`/site-access/guard/qr/${id}/rotate`, { version });
}

export function getGuardVisitMiniCode(id) {
  return get(`/site-access/guard/qr/${id}/mini-code`);
}

export function getGuardVisitRegistrations(params = {}) {
  return get('/site-access/guard/registrations', params);
}

export function getGuardVisitRegistration(id) {
  return get(`/site-access/guard/registrations/${id}`);
}

export function updateGuardVisitRegistration(id, data) {
  return put(`/site-access/guard/registrations/${id}`, data);
}

export function voidGuardVisitRegistration(id, reason) {
  return post(`/site-access/guard/registrations/${id}/void`, { reason });
}

export async function exportGuardVisitRegistrations(params = {}) {
  try {
    const blob = await apiClient.get('/site-access/guard/registrations/export', {
      params,
      responseType: 'blob',
    });
    return ensureFileBlob(blob, '门卫访客登记导出失败');
  } catch (error) {
    const errorBlob = error?.response?.data;
    if (errorBlob instanceof Blob && String(errorBlob.type || '').toLowerCase().includes('json')) {
      try {
        const result = JSON.parse(await errorBlob.text());
        throw new Error(result.message || '门卫访客登记导出失败');
      } catch (parseError) {
        if (!(parseError instanceof SyntaxError)) throw parseError;
      }
    }
    throw error;
  }
}
