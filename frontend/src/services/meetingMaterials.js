import { sha256 } from '@noble/hashes/sha256';
import { bytesToHex } from '@noble/hashes/utils';
import api from './api';
import { canonicalMaterialContentPath } from './meetingMaterialPath';

const unwrap = (response) => {
  if (response?.code !== 200) throw new Error(response?.message || '会议资料操作失败');
  return response.data;
};
export const materialCategories = { PUBLICITY: '宣发通知', AGENDA: '议程课件', MINUTES: '会议纪要', MEDIA: '现场影像', OTHER: '其他' };
export const getMeetingMaterials = async (id, params = {}) => unwrap(await api.get(`/site-access/invitations/${id}/materials`, { params }));
export const getMaterialActivities = async (id) => unwrap(await api.get(`/site-access/invitations/${id}/materials/activities`));
export const getMaterialVersions = async (id) => unwrap(await api.get(`/site-access/materials/${id}/versions`));
export const getMaterialVersion = async (id) => unwrap(await api.get(`/site-access/material-versions/${id}`));
export const editMaterial = async (id, data) => unwrap(await api.put(`/site-access/materials/${id}`, data));
export const publishMaterial = async (id, data) => unwrap(await api.put(`/site-access/materials/${id}/publication`, data));
export const setMaterialState = async (id, data) => unwrap(await api.put(`/site-access/materials/${id}/state`, data));
export const retryMaterialPreview = async (id) => unwrap(await api.post(`/site-access/material-versions/${id}/preview-retry`));
export const openMaterialReadSession = async (id) => unwrap(await api.post(`/site-access/material-versions/${id}/read-session`));
// Keep content on the canonical API path so the HttpOnly cookie has the same scope in Vite and production.
export const materialContentUrl = (id, preview = true) => canonicalMaterialContentPath(api.getUri({ url: `/site-access/material-versions/${id}/content`, params: { preview } }));
export async function downloadMeetingMaterial(version) {
  await openMaterialReadSession(version.id);
  const link = document.createElement('a');
  link.href = materialContentUrl(version.id, false);
  link.download = version.fileName;
  document.body.appendChild(link); link.click(); link.remove();
}
export const materialSize = (size = 0) => size >= 1024 ** 3 ? `${(size / 1024 ** 3).toFixed(2)} GB`
  : size >= 1024 ** 2 ? `${(size / 1024 ** 2).toFixed(1)} MB` : `${Math.ceil(size / 1024)} KB`;
export async function hashMaterialFile(file, onProgress = () => {}, signal) {
  const hash = sha256.create();
  for (let offset = 0; offset < file.size; offset += 8 * 1024 * 1024) {
    signal?.throwIfAborted();
    hash.update(new Uint8Array(await file.slice(offset, offset + 8 * 1024 * 1024).arrayBuffer()));
    onProgress(Math.min(file.size, offset + 8 * 1024 * 1024) / file.size);
  }
  return bytesToHex(hash.digest());
}
export async function uploadMeetingMaterial(meetingId, userId, file, metadata, onProgress, signal) {
  if (!file.size || file.size > 1024 ** 3) throw new Error('请选择不超过 1GB 的文件');
  const digest = await hashMaterialFile(file, (ratio) => onProgress({ stage: '校验文件', percent: Math.round(ratio * 100) }), signal);
  const payload = { ...metadata, fileName: file.name, totalSize: file.size, sha256: digest };
  const fingerprint = bytesToHex(sha256(new TextEncoder().encode(JSON.stringify(payload))));
  const key = `meeting-material-upload:${userId}:${meetingId}:${fingerprint}`;
  const base = `/site-access/invitations/${meetingId}/material-uploads`;
  let session;
  let cached;
  try { cached = JSON.parse(localStorage.getItem(key)); } catch { /* Storage is optional for uploading. */ }
  if (cached?.expiresAt > Date.now()) {
    try { session = unwrap(await api.get(`${base}/${cached.sessionId}`, { signal })); }
    catch (error) { if (error?.response?.status !== 410) throw error; }
  }
  if (!session) {
    session = unwrap(await api.post(base, payload, { signal }));
    try { localStorage.setItem(key, JSON.stringify(session)); } catch { /* The current upload remains usable. */ }
  }
  if (!session.completed) {
    const uploaded = new Set(session.uploadedChunks);
    const count = Math.ceil(file.size / session.chunkSize);
    for (let i = 0; i < count; i += 1) {
      signal?.throwIfAborted();
      if (!uploaded.has(i)) {
        const chunk = file.slice(i * session.chunkSize, (i + 1) * session.chunkSize);
        const data = new FormData(); data.append('chunk', chunk, 'chunk');
        data.append('sha256', bytesToHex(sha256(new Uint8Array(await chunk.arrayBuffer()))));
        unwrap(await api.put(`${base}/${session.sessionId}/chunks/${i}`, data,
          { signal, timeout: 180000, headers: { 'Content-Type': undefined } }));
      }
      onProgress({ stage: '上传中', percent: Math.round((i + 1) / count * 100) });
    }
  }
  onProgress({ stage: '合并并留档', percent: 100 });
  const result = unwrap(await api.post(`${base}/${session.sessionId}/complete`, {}, { signal, timeout: 1200000 }));
  try { localStorage.removeItem(key); } catch { /* Completed session is idempotent. */ }
  return result;
}
