import apiClient, { del, ensureFileBlob, get, post, put } from './api';

export const listIncomingBatches = (params) => get('/document-incoming-batches', params);
export const getIncomingBatch = (id) => get(`/document-incoming-batches/${id}`);
export const createIncomingBatch = (data) => post('/document-incoming-batches', data);
export const updateIncomingBatch = (id, data) => put(`/document-incoming-batches/${id}`, data);
export const getDocumentMatchCandidates = (params) => get('/document-incoming-batches/match-candidates', params);
export const publishIncomingBatch = (id, data) => post(`/document-incoming-batches/${id}/publish`, data);
export const voidIncomingBatch = (id, reason) => post(`/document-incoming-batches/${id}/void`, { reason });

export const listDocumentDistributions = (params) => get('/document-distributions', params);
export const getDocumentDistribution = (id) => get(`/document-distributions/${id}`);
export const createDocumentDistribution = (data) => post('/document-distributions', data);
export const getDocumentRecipientCandidates = (projectId, versionIds = []) => get(
  '/document-distributions/recipient-candidates',
  { projectId, versionIds: versionIds.length ? versionIds : undefined },
);
export const voidDocumentDistribution = (id, reason) => post(`/document-distributions/${id}/void`, { reason });

export async function getDistributionQrSvg(id) {
  return apiClient.get(`/document-distributions/${id}/qr`, { responseType: 'text' });
}

export const getMyDocumentDistribution = (id) => get(`/me/document-distributions/${id}`);
export const scanDocumentDistribution = (scene) => post('/me/document-distributions/scan', { scene });
export const disputeDocumentDistribution = (id, note) => post(`/me/document-distributions/${id}/dispute`, { note });

export function confirmDocumentDistribution(id, { scene, signature } = {}) {
  const form = new FormData();
  if (scene) form.append('scene', scene);
  if (signature) form.append('signature', signature, 'signature.png');
  return apiClient.post(`/me/document-distributions/${id}/confirm`, form, {
    headers: { 'Content-Type': undefined },
  });
}

export const initializeDocumentUpload = (data) => post('/document-uploads', data);
export const getDocumentUpload = (sessionId) => get(`/document-uploads/${sessionId}`);
export const completeDocumentUpload = (sessionId) => post(`/document-uploads/${sessionId}/complete`);
export const cancelDocumentUpload = (sessionId) => del(`/document-uploads/${sessionId}`);

const hex = (buffer) => [...new Uint8Array(buffer)].map((value) => value.toString(16).padStart(2, '0')).join('');
export const sha256Blob = async (blob) => hex(await crypto.subtle.digest('SHA-256', await blob.arrayBuffer()));

export async function uploadDocumentChunk(sessionId, index, chunk, sha256, onProgress) {
  const form = new FormData();
  form.append('sha256', sha256);
  form.append('chunk', chunk, `chunk-${index}`);
  return apiClient.put(`/document-uploads/${sessionId}/chunks/${index}`, form, {
    headers: { 'Content-Type': undefined },
    onUploadProgress: onProgress,
  });
}

export async function uploadCirculationFile({ projectId, incomingBatchId, file, onProgress }) {
  const resumeKey = `document-upload:${projectId}:${incomingBatchId}:${encodeURIComponent(file.name)}:${file.size}:${file.lastModified || 0}`;
  let session;
  const savedSessionId = typeof window !== 'undefined' ? window.localStorage?.getItem(resumeKey) : null;
  if (savedSessionId) {
    try {
      const status = await getDocumentUpload(savedSessionId);
      if (Number(status?.code) === 200 && status.data?.fileName === file.name && Number(status.data?.totalSize) === file.size) {
        session = status.data;
      }
    } catch {
      window.localStorage?.removeItem(resumeKey);
    }
  }
  if (!session) {
    const initialized = await initializeDocumentUpload({
      projectId,
      incomingBatchId,
      fileName: file.name,
      totalSize: file.size,
    });
    if (Number(initialized?.code) !== 200) throw new Error(initialized?.message || '上传初始化失败');
    session = initialized.data;
    if (typeof window !== 'undefined') window.localStorage?.setItem(resumeKey, session.sessionId);
  }
  if (session.fileResourceId) {
    if (typeof window !== 'undefined') window.localStorage?.removeItem(resumeKey);
    onProgress?.(100);
    return session;
  }
  const uploaded = new Set(session.uploadedChunks || []);
  try {
    for (let index = 0; index < session.chunkCount; index += 1) {
      if (uploaded.has(index)) continue;
      const start = index * session.chunkSize;
      const chunk = file.slice(start, Math.min(start + session.chunkSize, file.size));
      const chunkSha256 = await sha256Blob(chunk);
      let completed = false;
      let lastError;
      for (let attempt = 0; attempt < 3 && !completed; attempt += 1) {
        try {
          const result = await uploadDocumentChunk(session.sessionId, index, chunk, chunkSha256, (event) => {
            const current = start + Number(event.loaded || 0);
            onProgress?.(Math.min(99, Math.round((current / file.size) * 100)));
          });
          if (Number(result?.code) !== 200) throw new Error(result?.message || `第${index + 1}个分片上传失败`);
          completed = true;
        } catch (error) {
          lastError = error;
          const status = await getDocumentUpload(session.sessionId).catch(() => null);
          if (Number(status?.code) === 200 && status.data?.uploadedChunks?.includes(index)) completed = true;
          else if (attempt < 2) await new Promise((resolve) => {
            setTimeout(resolve, 300 * (attempt + 1));
          });
        }
      }
      if (!completed) throw lastError || new Error(`第${index + 1}个分片上传失败`);
      uploaded.add(index);
    }
    const completed = await completeDocumentUpload(session.sessionId);
    if (Number(completed?.code) !== 200) throw new Error(completed?.message || '文件合并失败');
    if (typeof window !== 'undefined') window.localStorage?.removeItem(resumeKey);
    onProgress?.(100);
    return completed.data;
  } catch (error) {
    throw error;
  }
}

export async function exportDocumentCirculationLedger(projectId) {
  return ensureFileBlob(await apiClient.get('/document-circulation-ledger/export', {
    params: { projectId },
    responseType: 'blob',
    timeout: 120000,
  }), '图纸收发综合台账导出失败');
}
