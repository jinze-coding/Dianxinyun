import type {
  PageResult,
  SealApplication,
  SealApplicationDetail,
  SealApplicationFile,
  SealApplicationItem,
  SealApplicationStatus,
  SealCcCandidate,
  SealDefinition,
  SealEntryResolution,
  SealFileRole
} from '@/types';
import { API_BASE_URL, getToken, handleUnauthorized, request, USE_MOCK } from './request';

export interface SealApplicationListParams {
  projectId?: number;
  status?: SealApplicationStatus | '';
  keyword?: string;
  scope?: 'INITIATED' | 'PENDING_FOR_ME' | 'CC_TO_ME';
  pageNo?: number;
  pageSize?: number;
}

export interface SealApplicationSaveInput {
  requestKey: string;
  scene?: string;
  projectId?: number;
  sealId?: number;
  departmentName: string;
  purpose: string;
  items: SealApplicationItem[];
  ccUserIds: number[];
}

export interface SealArchiveInput {
  fileId: number;
  archiveMode: 'NEW_DOCUMENT' | 'NEW_VERSION';
  folderId?: number;
  documentId?: number;
  documentNo?: string;
  title?: string;
  changeNote?: string;
}

const mockApplications: SealApplicationDetail[] = [];

function queryString(params: Record<string, string | number | undefined>) {
  return Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
    .join('&');
}

function mockDetail(input: SealApplicationSaveInput, id = Date.now()): SealApplicationDetail {
  return {
    id,
    requestKey: input.requestKey,
    projectId: Number(input.projectId || 1),
    projectName: '示例施工区域',
    companyName: '上海建工智慧营造有限公司',
    departmentName: input.departmentName,
    sealId: input.sealId,
    sealName: '项目部项目章',
    purpose: input.purpose,
    status: 'DRAFT',
    applicantName: '当前用户',
    items: input.items.map((item, index) => ({ ...item, id: index + 1 })),
    files: [],
    ccRecipients: [],
    logs: [],
    canEdit: true,
    canSubmit: true,
    canCancel: false
  };
}

export async function resolveSealEntry(scene: string): Promise<SealEntryResolution> {
  if (USE_MOCK) {
    return {
      scene,
      projectId: 1,
      projectName: '示例施工区域',
      departmentName: '示例项目部',
      sealId: 1,
      sealName: '示例项目部项目章',
      active: true
    };
  }
  return request<SealEntryResolution>('/seal/entry/resolve', {
    method: 'POST',
    data: { scene }
  });
}

export async function getSealApplications(params: SealApplicationListParams = {}): Promise<PageResult<SealApplication>> {
  if (USE_MOCK) {
    const records = mockApplications.filter((item) => !params.status || item.status === params.status);
    return { pageNo: 1, pageSize: params.pageSize || 20, total: records.length, records };
  }
  const query = queryString({
    projectId: params.projectId,
    status: params.status,
    keyword: params.keyword,
    scope: params.scope,
    pageNo: params.pageNo || 1,
    pageSize: params.pageSize || 20
  });
  const data = await request<PageResult<SealApplication> | SealApplication[]>(`/seal/applications?${query}`);
  if (Array.isArray(data)) {
    return { pageNo: 1, pageSize: data.length || 20, total: data.length, records: data };
  }
  return data;
}

export async function getSealApplication(id: number): Promise<SealApplicationDetail> {
  if (USE_MOCK) {
    const found = mockApplications.find((item) => item.id === id);
    if (!found) throw new Error('用印申请不存在');
    return found;
  }
  return request<SealApplicationDetail>(`/seal/applications/${id}`);
}

export async function createSealApplication(input: SealApplicationSaveInput): Promise<SealApplicationDetail> {
  if (USE_MOCK) {
    const detail = mockDetail(input);
    mockApplications.unshift(detail);
    return detail;
  }
  return request<SealApplicationDetail>('/seal/applications', { method: 'POST', data: input });
}

export async function updateSealApplication(id: number, input: SealApplicationSaveInput): Promise<SealApplicationDetail> {
  if (USE_MOCK) {
    const index = mockApplications.findIndex((item) => item.id === id);
    const detail = mockDetail(input, id);
    if (index >= 0) mockApplications.splice(index, 1, detail);
    else mockApplications.unshift(detail);
    return detail;
  }
  return request<SealApplicationDetail>(`/seal/applications/${id}`, { method: 'PUT', data: input });
}

export async function copySealApplication(id: number, input: { requestKey: string; ccUserIds: number[] }): Promise<SealApplicationDetail> {
  if (USE_MOCK) {
    const source = await getSealApplication(id);
    if (!['REJECTED', 'WITHDRAWN'].includes(source.status)) {
      throw new Error('只有已驳回或已撤回申请可以复制');
    }
    const detail = mockDetail({
      requestKey: input.requestKey,
      projectId: source.projectId,
      sealId: source.sealId,
      departmentName: source.departmentName,
      purpose: source.purpose,
      items: source.items.map((item) => ({ documentName: item.documentName, copies: item.copies })),
      ccUserIds: input.ccUserIds
    });
    detail.sourceApplicationId = source.id;
    mockApplications.unshift(detail);
    return detail;
  }
  return request<SealApplicationDetail>(`/seal/applications/${id}/copy`, { method: 'POST', data: input });
}

export async function submitSealApplication(id: number): Promise<SealApplicationDetail> {
  if (USE_MOCK) {
    const detail = await getSealApplication(id);
    detail.status = 'PENDING_APPROVAL';
    detail.canEdit = false;
    detail.canSubmit = false;
    detail.canCancel = true;
    return detail;
  }
  return request<SealApplicationDetail>(`/seal/applications/${id}/submit`, { method: 'POST' });
}

export async function approveSealApplication(id: number, opinion: string): Promise<SealApplicationDetail> {
  return request<SealApplicationDetail>(`/seal/applications/${id}/approve`, {
    method: 'POST', data: { opinion }
  });
}

export async function rejectSealApplication(id: number, opinion: string): Promise<SealApplicationDetail> {
  return request<SealApplicationDetail>(`/seal/applications/${id}/reject`, {
    method: 'POST', data: { opinion }
  });
}

export async function withdrawSealApplication(id: number): Promise<SealApplicationDetail> {
  if (USE_MOCK) {
    const detail = await getSealApplication(id);
    detail.status = 'WITHDRAWN';
    detail.canCancel = false;
    return detail;
  }
  return request<SealApplicationDetail>(`/seal/applications/${id}/withdraw`, { method: 'POST' });
}

export async function getSealCcCandidates(projectId: number, sealId?: number, keyword = ''): Promise<SealCcCandidate[]> {
  if (USE_MOCK) return [];
  const query = queryString({ projectId, sealId, keyword: keyword.trim() || undefined });
  return request<SealCcCandidate[]>(`/seal/applications/cc-candidates?${query}`);
}

export async function getAvailableSeals(projectId: number): Promise<SealDefinition[]> {
  if (USE_MOCK) return [{ id: 1, projectId, sealName: '示例项目部项目章', status: 'ACTIVE' }];
  return request<SealDefinition[]>(`/seal/seals?projectId=${encodeURIComponent(projectId)}`);
}

export function uploadSealApplicationFile(
  applicationId: number,
  filePath: string,
  fileRole: SealFileRole,
  itemId?: number
): Promise<SealApplicationFile> {
  if (USE_MOCK) {
    return Promise.resolve({
      id: Date.now(), fileRole, itemId, fileName: filePath.split('/').pop() || '用印文件', canDelete: true
    });
  }
  const token = getToken();
  const formData: Record<string, string> = { fileRole };
  if (itemId) formData.itemId = String(itemId);
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${API_BASE_URL}/seal/applications/${applicationId}/files`,
      filePath,
      name: 'file',
      formData,
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: (response) => {
        try {
          const result = JSON.parse(response.data || '{}');
          if (result?.code === 200) { resolve(result.data as SealApplicationFile); return; }
          if (result?.code === 401 || response.statusCode === 401) {
            handleUnauthorized(result?.message || '登录已失效，请重新登录');
          }
          reject(new Error(result?.message || `文件上传失败（${response.statusCode}）`));
        } catch (error) {
          reject(error instanceof Error ? error : new Error('文件上传失败'));
        }
      },
      fail: (error) => reject(new Error(error.errMsg || '文件上传失败'))
    });
  });
}

export async function deleteSealApplicationFile(applicationId: number, fileId: number) {
  if (USE_MOCK) return;
  return request<void>(`/seal/applications/${applicationId}/files/${fileId}`, { method: 'DELETE' });
}

async function readJsonTempFile(filePath: string): Promise<{ code?: number; message?: string } | null> {
  const api = uni as unknown as {
    getFileSystemManager?: () => {
      readFile: (options: {
        filePath: string;
        encoding: 'utf8';
        success: (result: { data: string | ArrayBuffer }) => void;
        fail: () => void;
      }) => void;
    };
  };
  if (!api.getFileSystemManager) return null;
  return new Promise((resolve) => api.getFileSystemManager?.().readFile({
    filePath,
    encoding: 'utf8',
    success: (result) => {
      try { resolve(JSON.parse(String(result.data))); }
      catch { resolve(null); }
    },
    fail: () => resolve(null)
  }));
}

function downloadSealPath(path: string): Promise<string> {
  if (USE_MOCK) return Promise.resolve('/static/mock-photo.svg');
  const token = getToken();
  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url: `${API_BASE_URL}${path}`,
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
      fail: (error) => reject(new Error(error.errMsg || '文件下载失败'))
    });
  });
}

export function downloadSealApplicationFile(applicationId: number, fileId: number, preview = true) {
  return downloadSealPath(`/seal/applications/${applicationId}/files/${fileId}/${preview ? 'preview' : 'download'}`);
}

export function downloadSealApplicationPdf(applicationId: number) {
  return downloadSealPath(`/seal/applications/${applicationId}/form.pdf`);
}

export async function archiveSealApplicationFile(applicationId: number, input: SealArchiveInput) {
  return request<SealApplicationDetail>(`/seal/applications/${applicationId}/archive`, {
    method: 'POST', data: input
  });
}
