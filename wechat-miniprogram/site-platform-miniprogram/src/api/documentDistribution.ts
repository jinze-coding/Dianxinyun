import type { Result } from '@/types';
import { API_BASE_URL, getToken, handleUnauthorized, request } from './request';

export type DistributionChannel = 'ELECTRONIC' | 'PAPER' | 'BOTH';
export type RecipientStatus = 'PENDING' | 'CONFIRMED' | 'DISPUTED';

export interface DocumentDistributionItem {
  id: number;
  documentId: number;
  versionId: number;
  documentNo?: string;
  title: string;
  documentType: 'DRAWING' | 'TECHNICAL_DOCUMENT';
  systemVersionNo: number;
  externalRevision?: string;
  fileName: string;
  sha256?: string;
  paperCopyCount?: number;
}

export interface DocumentDistributionRecipient {
  id: number;
  userId: number;
  realName: string;
  phone?: string;
  roleNames?: string;
  memberStatus: string;
  channel: DistributionChannel;
  status: RecipientStatus;
  mandatory: boolean;
  confirmedTime?: string;
  disputeNote?: string;
  disputeTime?: string;
  items: DocumentDistributionItem[];
}

export interface DocumentDistributionDetail {
  id: number;
  projectId: number;
  distributionNo: string;
  deadline: string;
  notificationTemplate?: string;
  messageNote?: string;
  status: string;
  overdue: boolean;
  signatureRequiredForCurrentRecipient: boolean;
  scanRequiredForCurrentRecipient: boolean;
  items: DocumentDistributionItem[];
  currentRecipient: DocumentDistributionRecipient;
}

export function getMyDocumentDistribution(id: number) {
  return request<DocumentDistributionDetail>(`/me/document-distributions/${id}`);
}

export function scanDocumentDistribution(scene: string) {
  return request<DocumentDistributionDetail>('/me/document-distributions/scan', {
    method: 'POST', data: { scene }
  });
}

export function disputeDocumentDistribution(id: number, note: string) {
  return request<DocumentDistributionDetail>(`/me/document-distributions/${id}/dispute`, {
    method: 'POST', data: { note }
  });
}

export function confirmDocumentDistribution(id: number, scene?: string, signaturePath?: string) {
  if (!signaturePath) {
    return request<DocumentDistributionDetail>(`/me/document-distributions/${id}/confirm`, {
      method: 'POST',
      header: { 'Content-Type': 'application/x-www-form-urlencoded' },
      data: scene ? { scene } : {}
    });
  }
  return new Promise<DocumentDistributionDetail>((resolve, reject) => {
    uni.uploadFile({
      url: `${API_BASE_URL}/me/document-distributions/${id}/confirm`,
      filePath: signaturePath,
      name: 'signature',
      formData: scene ? { scene } : {},
      header: getToken() ? { Authorization: `Bearer ${getToken()}` } : {},
      success: (response) => {
        try {
          const result = JSON.parse(response.data) as Result<DocumentDistributionDetail>;
          if (result.code === 200) { resolve(result.data); return; }
          if (result.code === 401) handleUnauthorized(result.message);
          reject(new Error(result.message || '图纸签收失败'));
        } catch { reject(new Error('图纸签收响应解析失败')); }
      },
      fail: (error) => reject(new Error(error.errMsg || '签名上传失败'))
    });
  });
}

async function readDownloadError(filePath: string): Promise<string | null> {
  const api = uni as unknown as { getFileSystemManager?: () => { readFile: (options: {
    filePath: string; encoding: 'utf8'; success: (result: { data: string | ArrayBuffer }) => void; fail: () => void
  }) => void } };
  if (!api.getFileSystemManager) return null;
  return new Promise((resolve) => api.getFileSystemManager?.().readFile({
    filePath, encoding: 'utf8',
    success: (result) => {
      try { resolve((JSON.parse(String(result.data)) as Result<unknown>)?.message || null); }
      catch { resolve(null); }
    },
    fail: () => resolve(null)
  }));
}

export function downloadDistributionFile(item: DocumentDistributionItem, distributionBatchId: number,
                                         acknowledgeSuperseded = false) {
  const query = `versionId=${encodeURIComponent(item.versionId)}`
    + `&distributionBatchId=${encodeURIComponent(distributionBatchId)}`
    + (acknowledgeSuperseded ? '&acknowledgeSuperseded=true' : '');
  return new Promise<string>((resolve, reject) => {
    uni.downloadFile({
      url: `${API_BASE_URL}/project-documents/${item.documentId}/download?${query}`,
      header: getToken() ? { Authorization: `Bearer ${getToken()}` } : {},
      success: async (response) => {
        if (response.statusCode === 200) resolve(response.tempFilePath);
        else reject(new Error(await readDownloadError(response.tempFilePath)
          || `资料文件下载失败（${response.statusCode}）`));
      },
      fail: (error) => reject(new Error(error.errMsg || '资料文件下载失败'))
    });
  });
}
