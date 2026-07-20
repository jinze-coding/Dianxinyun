import { API_BASE_URL, getToken, handleUnauthorized, request, USE_MOCK } from './request';

export interface FileResourceItem {
  id: number;
  projectId: number;
  fileName: string;
  fileType?: string;
  status?: string;
  businessType?: string;
  businessId?: number;
  createTime?: string;
}

export interface UploadResult {
  id: number;
  fileName: string;
  url?: string;
}

export interface UploadPhotoOptions {
  projectId?: number;
  businessType?: 'inspection_record' | 'inspection_rectification' | string;
  businessId?: number;
  fileName?: string;
}

export async function uploadPhoto(filePath: string, fileType: string, options: UploadPhotoOptions = {}): Promise<UploadResult> {
  if (USE_MOCK) {
    return {
      id: Date.now(),
      fileName: fileType,
      url: filePath
    };
  }
  const token = getToken();
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${API_BASE_URL}/files`,
      filePath,
      name: 'file',
      header: token ? { Authorization: `Bearer ${token}` } : {},
      formData: {
        fileType,
        projectId: options.projectId,
        businessType: options.businessType,
        businessId: options.businessId,
        fileName: options.fileName || `${fileType}_${Date.now()}_${Math.random().toString(36).slice(2)}`
      },
      success: (response) => {
        try {
          const parsed = JSON.parse(response.data);
          if (parsed?.code === 200) {
            resolve(parsed.data);
            return;
          }
          if (parsed?.code === 401 || response.statusCode === 401) {
            handleUnauthorized(parsed?.message || '登录已失效，请重新登录');
          }
          reject(new Error(parsed?.message || '上传失败'));
        } catch (error) {
          reject(error);
        }
      },
      fail: reject
    });
  });
}

export async function uploadPhotoIds(filePaths: string[], fileType: string, options: UploadPhotoOptions = {}): Promise<number[]> {
  const ids: number[] = [];
  try {
    for (const filePath of filePaths) {
      const result = await uploadPhoto(filePath, fileType, options);
      if (result.id) ids.push(result.id);
    }
    return ids;
  } catch (error) {
    await deleteFileResources(ids);
    throw error;
  }
}

export async function deleteFileResource(id: number) {
  if (USE_MOCK) return;
  return request<void>(`/files/${id}`, { method: 'DELETE' });
}

export async function deleteFileResources(ids: number[] = []) {
  await Promise.allSettled(ids.map((id) => deleteFileResource(id)));
}

export async function downloadFileToTempPath(id: number): Promise<string> {
  if (USE_MOCK) {
    return `/static/mock-photo.svg?file=${id}`;
  }
  const token = getToken();
  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url: `${API_BASE_URL}/files/${id}/download`,
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: async (response) => {
        const headers = (response as unknown as { header?: Record<string, string> }).header || {};
        const contentType = Object.entries(headers).find(([key]) => key.toLowerCase() === 'content-type')?.[1] || '';
        if (response.statusCode >= 200 && response.statusCode < 300 && !contentType.includes('application/json')) {
          resolve(response.tempFilePath);
          return;
        }
        const result = await readJsonTempFile(response.tempFilePath);
        if (result?.code === 401 || response.statusCode === 401) {
          handleUnauthorized(result?.message || '登录已失效，请重新登录');
        }
        reject(new Error(result?.message || `文件下载失败（${response.statusCode}）`));
      },
      fail: reject
    });
  });
}

async function readJsonTempFile(filePath: string): Promise<{ code?: number; message?: string } | null> {
  const api = uni as unknown as { getFileSystemManager?: () => { readFile: (options: { filePath: string; encoding: 'utf8'; success: (result: { data: string | ArrayBuffer }) => void; fail: () => void }) => void } };
  if (!api.getFileSystemManager) return null;
  return new Promise((resolve) => {
    api.getFileSystemManager?.().readFile({
      filePath,
      encoding: 'utf8',
      success: (result) => {
        try { resolve(JSON.parse(String(result.data))); }
        catch { resolve(null); }
      },
      fail: () => resolve(null)
    });
  });
}

export async function downloadFilePaths(ids: number[] = []): Promise<string[]> {
  const paths = await Promise.all(ids.map((id) => downloadFileToTempPath(id).catch(() => '')));
  return paths.filter(Boolean);
}

export async function getFileResources(projectId: number, businessType?: string) {
  if (USE_MOCK) return [] as FileResourceItem[];
  return request<FileResourceItem[]>(`/files?projectId=${projectId}${businessType ? `&businessType=${encodeURIComponent(businessType)}` : ''}`);
}
