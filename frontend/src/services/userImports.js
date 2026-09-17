import api, { ensureFileBlob, get, post } from './api';
const unwrap = (result) => { if (result?.code !== 200) throw new Error(result?.message || '用户导入操作失败'); return result.data; };
export const listUserImports = async (params) => unwrap(await get('/system/user-imports', params));
export const getUserImport = async (id, includeRows = true) => unwrap(await get(`/system/user-imports/${id}`, { includeRows }));
export const previewUserImport = async (file) => {
  const data = new FormData(); data.append('file', file);
  return unwrap(await api.post('/system/user-imports/preview', data, { headers: { 'Content-Type': undefined } }));
};
export const confirmUserImport = async (id, requestKey, temporaryPassword) => unwrap(await post(`/system/user-imports/${id}/confirm`, { requestKey, temporaryPassword }));
export const regenerateTemporaryPassword = async (id, temporaryPassword) => unwrap(await post(`/system/users/${id}/temporary-password`, { temporaryPassword }));
async function download(url, filename) {
  let blob;
  try { blob = await ensureFileBlob(await api.get(url, { responseType: 'blob' })); }
  catch (error) { if (error.response?.data instanceof Blob) await ensureFileBlob(error.response.data); throw error; }
  const objectUrl = URL.createObjectURL(blob);
  const link = document.createElement('a'); link.href = objectUrl; link.download = filename;
  document.body.appendChild(link); link.click(); link.remove(); window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
}
export const downloadUserImportTemplate = () => download('/system/user-imports/template', '用户批量导入模板.xlsx');
export const downloadUserImportCredentials = (id) => download(`/system/user-imports/${id}/credentials`, '账号发放表.xlsx');
export const downloadTemporaryPassword = (id) => download(`/system/users/${id}/temporary-password`, '个人账号发放表.xlsx');
