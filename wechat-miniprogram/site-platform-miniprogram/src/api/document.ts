import type {
  DocumentCategory,
  DocumentFolder,
  DocumentStatus,
  PageResult,
  ProjectDocument,
  ProjectDocumentDetail,
  ProjectDocumentSummary,
  ProjectDocumentVersion
} from '@/types';
import { API_BASE_URL, getToken, handleUnauthorized, request, USE_MOCK } from './request';

export interface DocumentListParams {
  projectId: number;
  folderId?: number;
  keyword?: string;
  status?: DocumentStatus | '';
  startDate?: string;
  endDate?: string;
  pageNo?: number;
  pageSize?: number;
}

export interface DocumentUploadInput {
  filePath: string;
  fileName: string;
  fileSize?: number;
  projectId: number;
  folderId: number;
  title: string;
  documentNo?: string;
  remark?: string;
  changeNote?: string;
}

export interface DocumentUpdateInput {
  folderId: number;
  documentNo?: string;
  title: string;
  remark?: string;
}

let mockDocumentId = 100;
let mockFolderId = 20;
let mockVersionId = 200;
const mockFolders: DocumentFolder[] = [];
const mockDocuments: ProjectDocument[] = [];
const mockRecycleDocuments: ProjectDocument[] = [];

function mockDocument(id: number, projectId: number, folderId: number, folderName: string, title: string,
  documentNo: string, category: DocumentCategory, fileName: string, extension: string,
  fileSize: number, status: DocumentStatus): ProjectDocument {
  return {
    id, projectId, folderId, folderName, title, documentNo, category, status,
    remark: '',
    createdBy: 0, createdByName: '', createTime: '', updateTime: '',
    currentVersion: { id: id + 100, versionNo: 1, versionLabel: 'V1', fileName, fileExtension: extension, fileSize, createdByName: '', createTime: '' },
    canEdit: status === 'ACTIVE', canManage: true
  };
}

function queryString(params: Record<string, string | number | undefined>) {
  return Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
    .join('&');
}

function filteredMockDocuments(params: DocumentListParams) {
  const keyword = params.keyword?.trim().toLowerCase() || '';
  return mockDocuments
    .filter((item) => item.projectId === params.projectId)
    .filter((item) => params.folderId === undefined || item.folderId === params.folderId)
    .filter((item) => !params.status || item.status === params.status)
    .filter((item) => !keyword || `${item.title}${item.documentNo || ''}${item.remark || ''}`.toLowerCase().includes(keyword));
}

function pageResult<T>(items: T[], pageNo = 1, pageSize = 20): PageResult<T> {
  const start = (pageNo - 1) * pageSize;
  return { pageNo, pageSize, total: items.length, records: items.slice(start, start + pageSize) };
}

function mockDetail(document: ProjectDocument): ProjectDocumentDetail {
  const version = document.currentVersion as ProjectDocumentVersion;
  return {
    document,
    versions: [version],
    activities: [{ id: document.id, documentId: document.id, operationType: 'DOCUMENT_UPLOAD', operationLabel: '上传资料', description: `上传资料《${document.title}》V1`, operatorName: document.createdByName, createTime: document.createTime }]
  };
}

export async function getDocumentFolders(projectId: number) {
  if (USE_MOCK) return mockFolders.filter((item) => item.projectId === projectId);
  return request<DocumentFolder[]>(`/document-folders?projectId=${projectId}`);
}

export async function createDocumentFolder(projectId: number, folderName: string) {
  if (USE_MOCK) {
    const folder: DocumentFolder = { id: ++mockFolderId, projectId, parentId: 0, folderName, documentCount: 0, updateTime: new Date().toISOString() };
    mockFolders.push(folder);
    return folder;
  }
  return request<DocumentFolder>('/document-folders', { method: 'POST', data: { projectId, parentId: 0, folderName } });
}

export async function updateDocumentFolder(id: number, folderName: string) {
  if (USE_MOCK) {
    const folder = mockFolders.find((item) => item.id === id) as DocumentFolder;
    folder.folderName = folderName;
    mockDocuments.filter((item) => item.folderId === id).forEach((item) => { item.folderName = folderName; });
    return folder;
  }
  return request<DocumentFolder>(`/document-folders/${id}`, { method: 'PUT', data: { folderName } });
}

export async function deleteDocumentFolder(id: number) {
  if (USE_MOCK) {
    const index = mockFolders.findIndex((item) => item.id === id);
    if (index >= 0) mockFolders.splice(index, 1);
    return;
  }
  return request<void>(`/document-folders/${id}`, { method: 'DELETE' });
}

export async function getProjectDocuments(params: DocumentListParams) {
  if (USE_MOCK) return pageResult(filteredMockDocuments(params), params.pageNo, params.pageSize);
  return request<PageResult<ProjectDocument>>(`/project-documents?${queryString(params as unknown as Record<string, string | number | undefined>)}`);
}

export async function getProjectDocumentSummary(projectId: number) {
  if (USE_MOCK) {
    const items = mockDocuments.filter((item) => item.projectId === projectId);
    return {
      total: items.length,
      active: items.filter((item) => item.status === 'ACTIVE').length,
      archived: items.filter((item) => item.status === 'ARCHIVED').length,
      recentUpdates: items.length,
      canManage: true
    } as ProjectDocumentSummary;
  }
  return request<ProjectDocumentSummary>(`/project-documents/summary?projectId=${projectId}`);
}

export async function getProjectDocumentDetail(id: number) {
  if (USE_MOCK) return mockDetail(mockDocuments.find((item) => item.id === id) as ProjectDocument);
  return request<ProjectDocumentDetail>(`/project-documents/${id}`);
}

export async function getProjectDocumentRecycleBin(projectId: number, keyword = '', pageNo = 1, pageSize = 20) {
  if (USE_MOCK) {
    const normalizedKeyword = keyword.trim().toLowerCase();
    const items = mockRecycleDocuments
      .filter((item) => item.projectId === projectId)
      .filter((item) => !normalizedKeyword || `${item.title}${item.documentNo || ''}`.toLowerCase().includes(normalizedKeyword));
    return pageResult(items, pageNo, pageSize);
  }
  return request<PageResult<ProjectDocument>>(`/project-documents/recycle-bin?${queryString({ projectId, keyword, pageNo, pageSize })}`);
}

function uploadMultipart<T>(path: string, filePath: string, formData: Record<string, string | number | undefined>): Promise<T> {
  const token = getToken();
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${API_BASE_URL}${path}`,
      filePath,
      name: 'file',
      header: token ? { Authorization: `Bearer ${token}` } : {},
      formData: Object.fromEntries(Object.entries(formData).filter(([, value]) => value !== undefined && value !== '')),
      success: (response) => {
        try {
          const result = JSON.parse(response.data || '{}');
          if (result?.code === 200) { resolve(result.data as T); return; }
          if (result?.code === 401) handleUnauthorized(result.message || '登录已失效，请重新登录');
          reject(new Error(result?.message || `上传失败（${response.statusCode}）`));
        } catch (error) { reject(error); }
      },
      fail: (error) => reject(new Error(error.errMsg || '文件上传失败'))
    });
  });
}

export async function createProjectDocument(input: DocumentUploadInput) {
  if (USE_MOCK) {
    const folder = mockFolders.find((item) => item.id === input.folderId);
    const extension = input.fileName.includes('.') ? input.fileName.split('.').pop() || '' : '';
    const document = mockDocument(++mockDocumentId, input.projectId, input.folderId, folder?.folderName || '根目录', input.title,
      input.documentNo || '', 'PROJECT_DATA', input.fileName, extension, input.fileSize || 0, 'ACTIVE');
    document.remark = input.remark;
    mockDocuments.unshift(document);
    if (folder) folder.documentCount += 1;
    return mockDetail(document);
  }
  return uploadMultipart<ProjectDocumentDetail>('/project-documents', input.filePath, {
    projectId: input.projectId, folderId: input.folderId, title: input.title, documentNo: input.documentNo,
    remark: input.remark, changeNote: input.changeNote
  });
}

export async function uploadProjectDocumentVersion(id: number, filePath: string, fileName: string, fileSize?: number, changeNote?: string) {
  if (USE_MOCK) {
    const document = mockDocuments.find((item) => item.id === id) as ProjectDocument;
    const next = (document.currentVersion?.versionNo || 0) + 1;
    document.currentVersion = { id: ++mockVersionId, versionNo: next, versionLabel: `V${next}`, fileName, fileSize, fileExtension: fileName.split('.').pop(), changeNote, createdByName: '当前用户', createTime: new Date().toISOString() };
    document.updateTime = new Date().toISOString();
    return mockDetail(document);
  }
  return uploadMultipart<ProjectDocumentDetail>(`/project-documents/${id}/versions`, filePath, { changeNote });
}

export async function updateProjectDocument(id: number, input: DocumentUpdateInput) {
  if (USE_MOCK) {
    const document = mockDocuments.find((item) => item.id === id) as ProjectDocument;
    const folder = mockFolders.find((item) => item.id === input.folderId);
    Object.assign(document, input, { folderName: folder?.folderName || '根目录', updateTime: new Date().toISOString() });
    return mockDetail(document);
  }
  return request<ProjectDocumentDetail>(`/project-documents/${id}`, { method: 'PUT', data: input });
}

async function documentAction(path: string, method: 'POST' | 'DELETE' = 'POST') {
  if (USE_MOCK) return;
  return request<void>(path, { method });
}

export async function archiveProjectDocument(id: number) {
  if (USE_MOCK) { const item = mockDocuments.find((document) => document.id === id); if (item) { item.status = 'ARCHIVED'; item.canEdit = false; } return; }
  return documentAction(`/project-documents/${id}/archive`);
}

export async function unarchiveProjectDocument(id: number) {
  if (USE_MOCK) {
    const item = mockDocuments.find((document) => document.id === id);
    if (item) {
      item.status = 'ACTIVE';
      item.canEdit = true;
      item.updateTime = new Date().toISOString();
    }
    return;
  }
  return documentAction(`/project-documents/${id}/unarchive`);
}

export async function deleteProjectDocument(id: number) {
  if (USE_MOCK) {
    const index = mockDocuments.findIndex((item) => item.id === id);
    if (index >= 0) {
      const [item] = mockDocuments.splice(index, 1);
      mockRecycleDocuments.unshift(item);
      const folder = mockFolders.find((candidate) => candidate.id === item.folderId);
      if (folder) folder.documentCount = Math.max(0, folder.documentCount - 1);
    }
    return;
  }
  return documentAction(`/project-documents/${id}`, 'DELETE');
}

export async function restoreProjectDocument(id: number) {
  if (USE_MOCK) {
    const index = mockRecycleDocuments.findIndex((item) => item.id === id);
    if (index >= 0) {
      const [item] = mockRecycleDocuments.splice(index, 1);
      item.updateTime = new Date().toISOString();
      mockDocuments.unshift(item);
      const folder = mockFolders.find((candidate) => candidate.id === item.folderId);
      if (folder) folder.documentCount += 1;
    }
    return;
  }
  return documentAction(`/project-documents/${id}/restore`);
}

export async function purgeProjectDocument(id: number) {
  if (USE_MOCK) {
    const index = mockRecycleDocuments.findIndex((item) => item.id === id);
    if (index >= 0) mockRecycleDocuments.splice(index, 1);
    return;
  }
  return documentAction(`/project-documents/${id}/purge`, 'DELETE');
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

export async function downloadProjectDocumentFile(id: number, versionId?: number, preview = true) {
  if (USE_MOCK) return '/static/mock-photo.svg';
  const token = getToken();
  const query = versionId ? `?versionId=${versionId}` : '';
  return new Promise<string>((resolve, reject) => {
    uni.downloadFile({
      url: `${API_BASE_URL}/project-documents/${id}/${preview ? 'preview' : 'download'}${query}`,
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: async (response) => {
        const headers = (response as unknown as { header?: Record<string, string> }).header || {};
        const contentType = Object.entries(headers).find(([key]) => key.toLowerCase() === 'content-type')?.[1] || '';
        if (response.statusCode >= 200 && response.statusCode < 300 && !contentType.includes('application/json')) {
          resolve(response.tempFilePath);
          return;
        }
        const result = await readJsonTempFile(response.tempFilePath);
        if (result?.code === 401 || response.statusCode === 401) handleUnauthorized(result?.message || '登录已失效，请重新登录');
        reject(new Error(result?.message || `文件下载失败（${response.statusCode}）`));
      },
      fail: (error) => reject(new Error(error.errMsg || '文件下载失败'))
    });
  });
}
