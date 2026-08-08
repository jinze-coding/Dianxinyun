import apiClient, { del, ensureFileBlob, get, post, put } from './api';

export const SEAL_BUSINESS_CODE = 'SEAL_APPLICATION';

export const getSealApplications = (params = {}) => get('/seal/applications', params);
export const getSealApplicationCcCandidates = (params = {}) => get('/seal/applications/cc-candidates', params);
export const getSealApplication = (id) => get(`/seal/applications/${id}`);
export const createSealApplication = (data) => post('/seal/applications', data);
export const updateSealApplication = (id, data) => put(`/seal/applications/${id}`, data);
export const submitSealApplication = (id) => post(`/seal/applications/${id}/submit`);
export const approveSealApplication = (id, opinion) => post(`/seal/applications/${id}/approve`, { opinion });
export const rejectSealApplication = (id, opinion) => post(`/seal/applications/${id}/reject`, { opinion });
export const withdrawSealApplication = (id) => post(`/seal/applications/${id}/withdraw`);
export const copySealApplication = (id, data) => post(`/seal/applications/${id}/copy`, data);
export const transferSealApplication = (id, data) => post(`/seal/applications/${id}/transfer`, data);
export const getSealTransferCandidates = (id, keyword) => get(
  `/seal/applications/${id}/transfer-candidates`,
  keyword ? { keyword } : undefined,
);
export const archiveSealApplicationFile = (id, data) => post(`/seal/applications/${id}/archive`, data);

export function uploadSealApplicationFile(id, file, fileRole, itemId) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('fileRole', fileRole);
  if (itemId !== undefined && itemId !== null && itemId !== '') formData.append('itemId', itemId);
  return apiClient.post(`/seal/applications/${id}/files`, formData, {
    headers: { 'Content-Type': undefined },
  });
}

export const deleteSealApplicationFile = (id, fileId) => del(`/seal/applications/${id}/files/${fileId}`);

async function getFileBlob(url, fallbackMessage) {
  return ensureFileBlob(await apiClient.get(url, { responseType: 'blob' }), fallbackMessage);
}

export const previewSealApplicationFile = (id, fileId) => getFileBlob(
  `/seal/applications/${id}/files/${fileId}/preview`,
  '附件预览失败',
);
export const downloadSealApplicationFile = (id, fileId) => getFileBlob(
  `/seal/applications/${id}/files/${fileId}/download`,
  '附件下载失败',
);
export const downloadSealApplicationPdf = (id) => getFileBlob(`/seal/applications/${id}/form.pdf`, '用印申请单下载失败');

export const exportSealApplicationLedger = async (params = {}) => ensureFileBlob(
  await apiClient.get('/seal/ledger/export', { params, responseType: 'blob' }),
  '用印台账导出失败',
);

export const getApprovalConfigs = (params = {}) => get('/system/approval-configs', params);
export const saveApprovalConfig = (data) => put('/system/approval-configs', data);
export const getApprovalCandidates = (params = {}) => get('/system/approval-configs/candidates', params);

export const getSystemSeals = (params = {}) => get('/system/seals', params);
export const createSystemSeal = (data) => post('/system/seals', data);
export const updateSystemSeal = (id, data) => put(`/system/seals/${id}`, data);

export const getSealEntryMiniCode = (sealId) => get(`/system/seals/${sealId}/mini-code`);
export const rotateSealEntryCode = (sealId) => post(`/system/seals/${sealId}/rotate-code`);
export const updateSealEntryCodeStatus = (sealId, enabled, reason) => put(
  `/system/seals/${sealId}/status`,
  { enabled: Boolean(enabled), reason: reason || undefined },
);
