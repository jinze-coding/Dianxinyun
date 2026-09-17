import { sha256 } from '@noble/hashes/sha256';
import { bytesToHex } from '@noble/hashes/utils';
import api from './api';
import { hashMaterialFile, materialSize } from './meetingMaterials';
import { canonicalMaterialContentPath } from './meetingMaterialPath';
const base = '/safety-committee';
const unwrap = (r) => { if (r?.code !== 200) throw new Error(r?.message || '安委会巡检操作失败'); return r.data; };
export const committee = {
  categories: async (projectId) => unwrap(await api.get(`${base}/categories`, { params: { projectId } })),
  list: async (params, signal) => unwrap(await api.get(`${base}/records`, { params, signal })),
  detail: async (id) => unwrap(await api.get(`${base}/records/${id}`)),
  create: async (data) => unwrap(await api.post(`${base}/records`, data)),
  edit: async (id, data) => unwrap(await api.put(`${base}/records/${id}`, data)),
  attachment: async (id) => unwrap(await api.get(`${base}/attachments/${id}`)),
  rotate: async (id, rotationDegrees, expectedVersion) => unwrap(await api.put(`${base}/attachments/${id}/rotation`, { rotationDegrees, expectedVersion })),
  discard: async (id) => unwrap(await api.delete(`${base}/attachments/${id}`)),
  retry: async (id) => unwrap(await api.post(`${base}/attachments/${id}/preview-retry`)),
  read: async (id) => unwrap(await api.post(`${base}/attachments/${id}/read-session`)),
  thumbnail: async (id, retry = false, signal) => unwrap(await api.post(`${base}/attachments/${id}/thumbnail`, {}, {params:{retry}, signal})),
};
export const committeeContent = (id, preview = true, thumbnail = false) => canonicalMaterialContentPath(api.getUri({ url: `${base}/attachments/${id}/content`, params: { preview, ...(thumbnail?{thumbnail:true}:{}) } }));
export { materialSize as committeeSize };
export const committeeDate = (value) => String(value || '').replace('T', ' ').replace(/\.\d+$/, '');
export const committeeKey = () => crypto.randomUUID().replaceAll('-', '');
export const committeeAccept = '.jpg,.jpeg,.png,.gif,.bmp,.webp,.heic,.heif,.mp4,.mov,.m4v,.webm,.mkv,.avi,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.wps,.et,.dps,.rtf,.txt,.csv';
export function validateCommitteeFile(file) {
  const ext = file.name.split('.').pop().toLowerCase();
  if (!committeeAccept.split(',').includes(`.${ext}`)) throw new Error('不支持此文件格式');
  const limit = ['jpg','jpeg','png','gif','bmp','webp','heic','heif'].includes(ext) ? 15 : ['mp4','mov','m4v','webm','mkv','avi'].includes(ext) ? 500 : 100;
  if (!file.size || file.size > limit * 1024 ** 2) throw new Error(`此类附件须大于 0 且不超过 ${limit} MB`);
}
export async function uploadCommitteeFile(file, metadata, userId, progress, signal) {
  validateCommitteeFile(file);
  const digest = await hashMaterialFile(file, (r) => progress(`校验 ${Math.round(r * 100)}%`), signal);
  const payload = { ...metadata, fileName: file.name, totalSize: file.size, sha256: digest };
  const key = `committee-upload:${userId}:${bytesToHex(sha256(new TextEncoder().encode(JSON.stringify(payload))))}`;
  let session; let cached;
  try { cached = JSON.parse(localStorage.getItem(key)); } catch { /* Optional resume cache. */ }
  const params = { projectId: metadata.projectId };
  if (cached?.expiresAt > Date.now()) {
    try { session = unwrap(await api.get(`${base}/uploads/${cached.sessionId}`, { params, signal })); }
    catch (e) { if (![404,410].includes(e.response?.status)) throw e; }
  }
  if (!session) {
    session = unwrap(await api.post(`${base}/uploads`, payload, { signal }));
    try { localStorage.setItem(key, JSON.stringify(session)); } catch { /* Upload can still proceed. */ }
  }
  if (!session.completed) {
    const uploaded = new Set(session.uploadedChunks); const count = Math.ceil(file.size / session.chunkSize);
    for (let i = 0; i < count; i += 1) {
      signal.throwIfAborted();
      if (!uploaded.has(i)) {
        const chunk = file.slice(i * session.chunkSize, (i + 1) * session.chunkSize); const data = new FormData(); data.append('chunk', chunk, 'chunk');
        data.append('sha256', bytesToHex(sha256(new Uint8Array(await chunk.arrayBuffer()))));
        unwrap(await api.put(`${base}/uploads/${session.sessionId}/chunks/${i}`, data, { params, signal, timeout: 180000, headers: { 'Content-Type': undefined } }));
      }
      progress(`上传 ${Math.round((i + 1) / count * 100)}%`);
    }
  }
  progress('合并校验中');
  const result = unwrap(await api.post(`${base}/uploads/${session.sessionId}/complete`, {}, { params, signal, timeout: 1200000 }));
  try { localStorage.removeItem(key); } catch { /* Completion is idempotent. */ }
  return result;
}
