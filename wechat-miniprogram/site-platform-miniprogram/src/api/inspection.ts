import { checkItems } from '@/mock/data';
import type { CheckResult, InspectionItemResult, InspectionRecord } from '@/types';
import { API_BASE_URL, getToken, request, USE_MOCK } from './request';
import { downloadFilePaths } from './file';
import {
  getMockInspectionRecordDetail,
  getMockInspectionRecords,
  getMockInspectionSummary,
  getMockReviewRecords,
  reviewMockInspectionRecord,
  submitMockInspectionRecord,
  submitMockSafetySpotCheck
} from '@/mock/runtime';

export async function getInspectionRecords(projectId: number, electricBoxId?: number, month?: string): Promise<InspectionRecord[]> {
  if (USE_MOCK) {
    return getMockInspectionRecords(projectId, electricBoxId, month);
  }
  const query = [
    `projectId=${encodeURIComponent(projectId)}`,
    electricBoxId ? `electricBoxId=${encodeURIComponent(electricBoxId)}` : '',
    month ? `month=${encodeURIComponent(month)}` : ''
  ].filter(Boolean).join('&');
  return request<InspectionRecord[]>(`/inspection/records?${query}`);
}

export async function getReviewRecords(params: {
  projectId?: number;
  status?: InspectionRecord['status'];
  reviewScope?: 'MINE' | 'UNASSIGNED' | 'ASSIGNED' | '';
  reviewOverdue?: boolean;
} = {}): Promise<InspectionRecord[]> {
  if (USE_MOCK) {
    return getMockReviewRecords(params.projectId, params.status, params.reviewScope, params.reviewOverdue);
  }
  const query = [
    params.projectId ? `projectId=${encodeURIComponent(params.projectId)}` : '',
    params.status ? `status=${encodeURIComponent(params.status)}` : '',
    params.reviewScope ? `reviewScope=${encodeURIComponent(params.reviewScope)}` : '',
    params.reviewOverdue !== undefined ? `reviewOverdue=${params.reviewOverdue ? 'true' : 'false'}` : ''
  ].filter(Boolean).join('&');
  return request<InspectionRecord[]>(`/inspection/records${query ? `?${query}` : ''}`);
}

export async function getPendingReviewRecords(projectId?: number): Promise<InspectionRecord[]> {
  return getReviewRecords({ projectId, status: 'REVIEW_PENDING' });
}

export async function getInspectionRecordDetail(id: number): Promise<InspectionRecord | undefined> {
  if (USE_MOCK) {
    return getMockInspectionRecordDetail(id);
  }
  const record = await request<InspectionRecord>(`/inspection/records/${id}`);
  return hydrateRecordPhotos(record);
}

export async function submitInspectionRecord(payload: {
  projectId: number;
  electricBoxId: number;
  boxCode: string;
  source?: 'ELECTRICIAN_DAILY' | 'SAFETY_SPOT_CHECK';
  problemCategory?: string;
  assigneeId?: number;
  requirement?: string;
  deadline?: string;
  checkDate?: string;
  remark: string;
  outerPhotoFileIds?: number[];
  innerPhotoFileIds?: number[];
  outerPhotos?: string[];
  innerPhotos?: string[];
  items: Array<{ itemCode: string; itemName: string; result: CheckResult; description?: string }>;
}): Promise<InspectionRecord> {
  if (USE_MOCK) {
    return submitMockInspectionRecord(payload);
  }
  return request<InspectionRecord>('/inspection/records', {
    method: 'POST',
    data: {
      projectId: payload.projectId,
      electricBoxId: payload.electricBoxId,
      templateCode: 'ELECTRIC_BOX_DAILY',
      source: payload.source || 'ELECTRICIAN_DAILY',
      problemCategory: payload.problemCategory,
      assigneeId: payload.assigneeId,
      requirement: payload.requirement,
      deadline: payload.deadline,
      checkDate: payload.checkDate,
      outerPhotoFileIds: payload.outerPhotoFileIds || [],
      innerPhotoFileIds: payload.innerPhotoFileIds || [],
      remark: payload.remark,
      items: payload.items
    }
  });
}

export async function reviewInspectionRecord(
  id: number,
  action: 'PASS' | 'REJECT' | 'RECTIFY',
  options: { comment?: string; assigneeId?: number; assigneeName?: string; requirement?: string; problemCategory?: string; deadline?: string } = {}
) {
  if (USE_MOCK) {
    return reviewMockInspectionRecord(id, action, options.comment, options);
  }
  return request(`/inspection/records/${id}/review`, {
    method: 'POST',
    data: {
      reviewAction: action,
      comment: options.comment,
      assigneeId: options.assigneeId,
      assigneeName: options.assigneeName,
      requirement: options.requirement,
      problemCategory: options.problemCategory,
      deadline: options.deadline
    }
  });
}

export async function submitSafetySpotCheck(payload: {
  projectId: number;
  electricBoxId: number;
  boxCode: string;
  problemDescription: string;
  problemCategory?: string;
  requirement: string;
  deadline: string;
  assigneeId?: number;
  assigneeName?: string;
  problemPhotos: string[];
  problemPhotoFileIds?: number[];
}) {
  if (USE_MOCK) {
    return submitMockSafetySpotCheck(payload);
  }
  const problemPhotoFileIds = payload.problemPhotoFileIds || [];
  const items = createDefaultCheckItems().map((item, index) => ({
    ...item,
    result: index === 0 ? 'ABNORMAL' as CheckResult : 'NORMAL' as CheckResult,
    description: index === 0 ? payload.problemDescription : ''
  }));
  const record = await submitInspectionRecord({
    projectId: payload.projectId,
    electricBoxId: payload.electricBoxId,
    boxCode: payload.boxCode,
    source: 'SAFETY_SPOT_CHECK',
    problemCategory: payload.problemCategory,
    assigneeId: payload.assigneeId,
    requirement: payload.requirement,
    deadline: payload.deadline,
    remark: payload.problemDescription,
    outerPhotoFileIds: problemPhotoFileIds,
    innerPhotoFileIds: [],
    outerPhotos: payload.problemPhotos,
    innerPhotos: [],
    items
  });
  return record;
}

export interface InspectionMonthSummary {
  projectId: number;
  electricBoxId?: number;
  month: string;
  shouldCheck: number;
  checked: number;
  missed: number;
  abnormal: number;
  openRectification: number;
  records: InspectionRecord[];
}

export async function getInspectionSummary(params: {
  projectId: number;
  boxId?: number;
  month: string;
}): Promise<InspectionMonthSummary> {
  if (USE_MOCK) return getMockInspectionSummary(params);
  const query = [
    `projectId=${encodeURIComponent(params.projectId)}`,
    `month=${encodeURIComponent(params.month)}`,
    params.boxId ? `boxId=${encodeURIComponent(params.boxId)}` : ''
  ].filter(Boolean).join('&');
  return request<InspectionMonthSummary>(`/inspection/records/summary?${query}`);
}

export async function exportInspectionRecords(params: {
  projectId: number;
  templateCode?: string;
  month: string;
  boxId?: number;
  boxCode?: string;
}): Promise<{ mock: boolean; fileName: string; filePath?: string }> {
  const fileName = `${params.boxCode || '电箱'}-电箱检查记录表-${params.month}.xlsx`;
  if (USE_MOCK) {
    return { mock: true, fileName };
  }
  const query = [
    `projectId=${encodeURIComponent(params.projectId)}`,
    `templateCode=${encodeURIComponent(params.templateCode || 'ELECTRIC_BOX_DAILY')}`,
    `month=${encodeURIComponent(params.month)}`,
    params.boxId ? `boxId=${encodeURIComponent(params.boxId)}` : ''
  ].filter(Boolean).join('&');
  const token = getToken();
  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url: `${API_BASE_URL}/inspection/records/export?${query}`,
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: (response) => {
        if (response.statusCode < 200 || response.statusCode >= 300) {
          reject(new Error(`导出失败（${response.statusCode}）`));
          return;
        }
        uni.openDocument({
          filePath: response.tempFilePath,
          showMenu: true,
          success: () => resolve({ mock: false, fileName, filePath: response.tempFilePath }),
          fail: async () => {
            const message = await readDownloadedError(response.tempFilePath);
            reject(new Error(message || '文件已下载，但无法打开 Excel，请在微信文件中重试'));
          }
        });
      },
      fail: reject
    });
  });
}

async function readDownloadedError(filePath: string): Promise<string> {
  // #ifdef MP-WEIXIN
  return new Promise((resolve) => {
    uni.getFileSystemManager().readFile({
      filePath,
      encoding: 'utf8',
      success: (response) => {
        try {
          const payload = JSON.parse(String(response.data || ''));
          resolve(payload?.message || '');
        } catch (error) {
          resolve('');
        }
      },
      fail: () => resolve('')
    });
  });
  // #endif

  // #ifndef MP-WEIXIN
  return '';
  // #endif
}

export function createDefaultCheckItems() {
  return checkItems.map((item) => ({
    ...item,
    result: 'NORMAL' as CheckResult,
    description: ''
  }));
}

async function hydrateRecordPhotos(record: InspectionRecord): Promise<InspectionRecord> {
  const isSpotCheck = record.source === 'SAFETY_SPOT_CHECK';
  const [outerPhotos, innerPhotos, problemPhotos] = await Promise.all([
    isSpotCheck ? Promise.resolve([]) : downloadFilePaths(record.outerPhotoFileIds),
    isSpotCheck ? Promise.resolve([]) : downloadFilePaths(record.innerPhotoFileIds),
    downloadFilePaths(record.problemPhotoFileIds || (isSpotCheck ? record.outerPhotoFileIds : []))
  ]);
  return {
    ...record,
    outerPhotos,
    innerPhotos,
    problemPhotos
  };
}
