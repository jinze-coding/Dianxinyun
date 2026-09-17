import api from './api';
import { hashMaterialFile } from './meetingMaterials';
import { sha256 } from '@noble/hashes/sha256';
import { bytesToHex } from '@noble/hashes/utils';
const base = '/system/data-corrections';
const unwrap = (r) => { if (r?.code !== 200) throw new Error(r?.message || '纠错请求失败'); return r.data; };
export const corrections = {
  catalog: async () => unwrap(await api.get(`${base}/catalog`)),
  list: async (type, params, signal) => unwrap(await api.get(`${base}/records/${type}`, { params, signal })),
  detail: async (type, id) => unwrap(await api.get(`${base}/records/${type}/${id}`)),
  candidates: async (type, id, field) => unwrap(await api.get(`${base}/records/${type}/${id}/candidates`, { params: { field } })),
  preview: async (type, id, body) => unwrap(await api.post(`${base}/records/${type}/${id}/preview`, body)),
  confirm: async (body) => unwrap(await api.post(`${base}/confirm`, body, { timeout: 1200000 })),
  history: async (type, id, pageNo = 1) => unwrap(await api.get(`${base}/records/${type}/${id}/history`, { params: { pageNo } })),
  log: async (id) => unwrap(await api.get(`${base}/logs/${id}`)),
  file: async (type, id, fileId, correctionId, phase) => api.get(`${base}/records/${type}/${id}/files/${fileId}`, { params: { correctionId, phase, inline: true }, responseType: 'blob' }),
};
export async function uploadCorrection(file, record, slotKey, userId, progress, signal) {
  const digest = await hashMaterialFile(file, (p) => progress(`校验 ${Math.round(p * 100)}%`), signal);
  const payload = { targetType: record.targetType, targetId: record.id, slotKey, fileName: file.name, totalSize: file.size, sha256: digest };
  const cacheKey = `correction-upload:${userId}:${bytesToHex(sha256(new TextEncoder().encode(JSON.stringify(payload))))}`;
  const params = { projectId: record.projectId };
  let session; let cached;
  try { cached = JSON.parse(localStorage.getItem(cacheKey)); } catch { /* Optional resume state. */ }
  if (cached?.expiresAt > Date.now()) {
    try { session = unwrap(await api.get(`${base}/uploads/${cached.sessionId}`, { params, signal })); }
    catch (e) { if (![404,410].includes(e.response?.status)) throw e; }
  }
  if (!session) {
    session = unwrap(await api.post(`${base}/uploads`, payload, { signal }));
    try { localStorage.setItem(cacheKey, JSON.stringify(session)); } catch { /* Upload remains usable. */ }
  }
  if (!session.completed) {
    const uploaded = new Set(session.uploadedChunks); const count = Math.ceil(file.size / session.chunkSize);
    for (let index = 0; index < count; index += 1) {
      signal.throwIfAborted();
      if (!uploaded.has(index)) {
        const chunk = file.slice(index * session.chunkSize, (index + 1) * session.chunkSize);
        const form = new FormData(); form.append('file', chunk, file.name);
        form.append('sha256', bytesToHex(sha256(new Uint8Array(await chunk.arrayBuffer()))));
        unwrap(await api.put(`${base}/uploads/${session.sessionId}/chunks/${index}`, form, { params, signal, timeout: 180000, headers: { 'Content-Type': undefined } }));
      }
      progress(`上传 ${Math.round((index + 1) / count * 100)}%`);
    }
  }
  progress('合并校验中');
  const result = unwrap(await api.post(`${base}/uploads/${session.sessionId}/complete`, {}, { params, signal, timeout: 1200000 }));
  try { localStorage.removeItem(cacheKey); } catch { /* Optional cache. */ }
  return result;
}
