import apiClient, { get, post, put } from './api';

export function getElectricBoxList(params = {}) {
  return get('/electric-boxes', params);
}

export function getElectricBoxDetail(id) {
  return get(`/electric-boxes/${id}`);
}

export function createElectricBox(data) {
  return post('/electric-boxes', data);
}

export function updateElectricBox(id, data) {
  return put(`/electric-boxes/${id}`, data);
}

export function disableElectricBox(id, data = {}) {
  return post(`/electric-boxes/${id}/disable`, data);
}

export function setElectricBoxPublicAccess(id, enabled) {
  return post(`/electric-boxes/${id}/public-access`, { enabled });
}

export function resolveElectricBoxQr(qrCode) {
  return get(`/electric-boxes/qr/${encodeURIComponent(qrCode)}`);
}

export function removeElectricBox(id, data = {}) {
  return post(`/electric-boxes/${id}/remove`, data);
}

export function rebindElectricBoxQr(id, data = {}) {
  return post(`/electric-boxes/${id}/qr/rebind`, data);
}

export function recordElectricBoxQrPrint(id, data = {}) {
  return post(`/electric-boxes/${id}/qr/print-log`, data);
}

export function getElectricBoxQrLogs(id) {
  return get(`/electric-boxes/${id}/qr-logs`);
}

export function generateElectricBoxQrSvg(payload) {
  return post('/electric-boxes/qr-svg', { payload });
}

export function getElectricBoxUnifiedCode(id) {
  return get(`/electric-boxes/${id}/unified-code`);
}

export function rotateElectricBoxUnifiedCode(id, data = {}) {
  return post(`/electric-boxes/${id}/unified-code/rotate`, data);
}

export function updateElectricBoxInspectionScope(id, data) {
  return put(`/electric-boxes/${id}/inspection-scope`, data);
}

export function downloadElectricBoxImportTemplate() {
  return apiClient.get('/electric-boxes/import-template', { responseType: 'blob' });
}

export function importElectricBoxes(projectId, file, dryRun = true) {
  const formData = new FormData();
  formData.append('projectId', projectId);
  formData.append('dryRun', dryRun);
  formData.append('file', file);
  return apiClient.post('/electric-boxes/import', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}
