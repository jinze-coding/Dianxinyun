import { sha256 } from '@noble/hashes/sha256';
import { bytesToHex } from '@noble/hashes/utils';
import { API_BASE_URL, request, getToken, ApiRequestError, handleUnauthorized } from './request';
export interface CommitteeAttachment { id: number; fileName: string; fileSize: number; extension: string; status: string; previewKind: string; previewStatus: string; failureMessage?: string }
export interface CommitteeLog { id: number; operatorName: string; action: string; beforeJson: string; afterJson: string; createTime: string }
export interface CommitteeRecord { id: number; projectId: number; inspectorId: number; inspectorName: string; inspectedAt: string; category: string; conclusion: string; version: number; updatedAt: string; canEdit: boolean; canDelete: boolean; attachments: CommitteeAttachment[]; logs: CommitteeLog[] }
export interface CommitteePage { records: CommitteeRecord[]; total: number; latestId: number | null }
export interface SelectedFile { name: string; size: number; path: string; file?: File }
export interface UploadControl { cancelled: boolean; abort?: () => void }
interface UploadSession { sessionId: string; uploadedChunks: number[]; chunkSize: number; expiresAt: number; completed: boolean }
const base = '/safety-committee';
export const committeeKey = () => `${Date.now().toString(36)}${Math.random().toString(36).slice(2)}${Math.random().toString(36).slice(2)}`;
export const committeeDate = (v: string) => String(v || '').replace('T',' ').replace(/\.\d+$/, '');
export const committeeSize = (v: number) => v >= 1024 ** 2 ? `${(v / 1024 ** 2).toFixed(1)} MB` : `${Math.ceil(v / 1024)} KB`;
export const activeCommitteeFiles = (r?: CommitteeRecord) => (r?.attachments || []).filter(a => a.status === 'ACTIVE');
export const committeeApi = {
  categories: (projectId: number) => request<string[]>(`${base}/categories?projectId=${projectId}`),
  list: (projectId: number, category: string, page: number) => request<CommitteePage>(`${base}/records?projectId=${projectId}&pageNo=${page}&category=${encodeURIComponent(category)}`),
  detail: (id: number) => request<CommitteeRecord>(`${base}/records/${id}`),
  save: (id: number | undefined, data: unknown) => request<CommitteeRecord>(`${base}/records${id ? `/${id}` : ''}`, { method: id ? 'PUT' : 'POST', data }),
  attachment: (id: number) => request<CommitteeAttachment>(`${base}/attachments/${id}`),
  discard: (id: number) => request<void>(`${base}/attachments/${id}`, { method: 'DELETE' }),
  retry: (id: number) => request<void>(`${base}/attachments/${id}/preview-retry`, { method: 'POST' }),
  read: async (id: number, preview = true) => {
    const grant = await request<{ contentPath: string; expiresAt: number }>(`${base}/attachments/${id}/read-session?nativePlayback=true`, { method: 'POST' });
    // Grant belongs to the original session; no JWT is put in a player URL.
    return `${API_BASE_URL.replace(/\/api(?:\/v1)?$/, '')}${grant.contentPath}?preview=${preview}`;
  }
};
export function committeeAccessLost(e: unknown) {
  const error = e as ApiRequestError;
  if ([401,403].includes(Number(error.statusCode || error.code))) {
    if (Number(error.statusCode || error.code) === 403) uni.reLaunch({ url: '/pages/todo/index' });
    return true;
  }
  return false;
}
const images = ['jpg','jpeg','png','gif','bmp','webp','heic','heif'];
const videos = ['mp4','mov','m4v','webm','mkv','avi'];
const offices = ['pdf','doc','docx','xls','xlsx','ppt','pptx','wps','et','dps','rtf','txt','csv'];
export const committeeAccept = [...images,...videos,...offices].map(e => `.${e}`).join(',');
export function validateCommitteeFile(file: SelectedFile) {
  const ext = file.name.split('.').pop()?.toLowerCase() || '';
  const limit = images.includes(ext) ? 15 : videos.includes(ext) ? 500 : offices.includes(ext) ? 100 : 0;
  if (!limit) throw new Error('不支持此文件格式');
  if (!file.size || file.size > limit * 1024 ** 2) throw new Error(`此类附件须大于 0 且不超过 ${limit} MB`);
}
function check(control: UploadControl) { if (control.cancelled) throw new Error('已暂停，重试可继续上传'); }
export async function readCommitteeChunk(file: SelectedFile, offset: number, length: number): Promise<Uint8Array> {
  // #ifdef H5
  if (file.file) return new Uint8Array(await file.file.slice(offset, offset + length).arrayBuffer());
  throw new Error('请重新选择文件后续传');
  // #endif
  // #ifdef MP-WEIXIN
  return new Promise((resolve, reject) => uni.getFileSystemManager().readFile({ filePath: file.path, position: offset, length,
    success: r => resolve(new Uint8Array(r.data as ArrayBuffer)), fail: reject }));
  // #endif
}
function ascii(text: string) { return Uint8Array.from([...text].map(c => c.charCodeAt(0))); }
async function sendChunk(url: string, chunk: Uint8Array, control: UploadControl) {
  const boundary = `committee${committeeKey()}`;
  const head = ascii(`--${boundary}\r\nContent-Disposition: form-data; name="sha256"\r\n\r\n${bytesToHex(sha256(chunk))}\r\n--${boundary}\r\nContent-Disposition: form-data; name="chunk"; filename="chunk"\r\nContent-Type: application/octet-stream\r\n\r\n`);
  const tail = ascii(`\r\n--${boundary}--\r\n`);
  const body = new Uint8Array(head.length + chunk.length + tail.length); body.set(head); body.set(chunk,head.length); body.set(tail,head.length+chunk.length);
  check(control);
  await new Promise<void>((resolve,reject) => {
    const task = uni.request({ url: `${API_BASE_URL}${url}`, method: 'PUT', data: body.buffer, timeout: 180000,
      header: { Authorization: `Bearer ${getToken()}`, 'Content-Type': `multipart/form-data; boundary=${boundary}` },
      success: r => { const data = r.data as { code: number; message: string }; if (r.statusCode === 200 && data.code === 200) resolve(); else { if (r.statusCode === 401) handleUnauthorized(data.message); reject(new ApiRequestError(data.message || '分片上传失败',data.code,r.statusCode)); } }, fail: reject });
    control.abort = () => task.abort();
  });
  control.abort = undefined;
}
export async function uploadCommitteeFile(file: SelectedFile, metadata: { projectId: number; draftKey: string; targetRecordId?: number }, userId: number, progress: (v: string) => void, control: UploadControl): Promise<CommitteeAttachment> {
  validateCommitteeFile(file); const size = 8 * 1024 ** 2; const hash = sha256.create();
  for (let offset=0; offset<file.size; offset+=size) { check(control); hash.update(await readCommitteeChunk(file,offset,Math.min(size,file.size-offset))); progress(`校验 ${Math.round(Math.min(offset+size,file.size)/file.size*100)}%`); }
  check(control);
  const payload = { ...metadata, fileName: file.name, totalSize: file.size, sha256: bytesToHex(hash.digest()) };
  const key = `committee-upload:${userId}:${metadata.projectId}:${metadata.draftKey}:${payload.sha256}:${file.name}`;
  const cached = uni.getStorageSync(key) as UploadSession | undefined; let session: UploadSession | undefined;
  const query = `?projectId=${metadata.projectId}`;
  if (cached && cached.expiresAt > Date.now()) { try { session = await request<UploadSession>(`${base}/uploads/${cached.sessionId}${query}`); } catch (e) { if (![404,410].includes(Number((e as ApiRequestError).statusCode))) throw e; } }
  check(control);
  if (!session) { session = await request<UploadSession>(`${base}/uploads`,{method:'POST',data:payload}); uni.setStorageSync(key,session); }
  const uploaded = new Set(session.uploadedChunks);
  if (!session.completed) for(let i=0;i<Math.ceil(file.size/size);i++) {
    check(control); if (!uploaded.has(i)) await sendChunk(`${base}/uploads/${session.sessionId}/chunks/${i}${query}`,await readCommitteeChunk(file,i*size,Math.min(size,file.size-i*size)),control);
    progress(`上传 ${Math.round(Math.min((i+1)*size,file.size)/file.size*100)}%`);
  }
  check(control); progress('合并校验中');
  const result = await request<CommitteeAttachment>(`${base}/uploads/${session.sessionId}/complete${query}`,{method:'POST',timeout:1200000});
  uni.removeStorageSync(key); return result;
}
export function chooseCommitteeFiles(source: 'album' | 'chat' | 'camera'): Promise<SelectedFile[]> {
  // #ifdef H5
  return new Promise(resolve => {
    const input = document.createElement('input'); input.type = 'file'; input.multiple = source !== 'camera'; input.accept = source === 'camera' ? 'image/*,video/*' : committeeAccept;
    if (source === 'camera') input.setAttribute('capture','environment');
    input.onchange = () => resolve(Array.from(input.files || []).map(file => ({name:file.name,size:file.size,path:'',file})));
    input.oncancel = () => resolve([]); input.click();
  });
  // #endif
  // #ifdef MP-WEIXIN
  const wxApi = (globalThis as any).wx;
  if (source === 'chat') return new Promise((resolve,reject) => wxApi.chooseMessageFile({ count:30,type:'all',success:(r:any)=>resolve(r.tempFiles.map((f:any)=>({name:f.name,size:f.size,path:f.path}))),fail:reject }));
  return new Promise((resolve,reject) => wxApi.chooseMedia({count:9,mediaType:['image','video'],sourceType:source==='camera'?['camera']:['album'],maxDuration:300,
    success:(r:any)=>resolve(r.tempFiles.map((f:any)=>({name:`现场${Date.now()}-${Math.random().toString(36).slice(2,6)}.${f.tempFilePath.split('.').pop() || (f.fileType==='video'?'mp4':'jpg')}`,size:f.size,path:f.tempFilePath}))), fail:reject }));
  // #endif
}
