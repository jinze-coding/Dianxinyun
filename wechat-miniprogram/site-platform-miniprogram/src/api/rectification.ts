import type { RectificationTask } from '@/types';
import { request, USE_MOCK } from './request';
import { downloadFilePaths } from './file';
import {
  closeMockRectification,
  completeMockRectification,
  getMockRectificationDetail,
  getMockRectifications,
  rejectMockRectification
} from '@/mock/runtime';

export async function getRectificationList(params: {
  projectId?: number;
  status?: RectificationTask['status'] | '';
} = {}): Promise<RectificationTask[]> {
  if (USE_MOCK) {
    return getMockRectifications(params.projectId, params.status);
  }
  const query = [
    params.projectId ? `projectId=${encodeURIComponent(params.projectId)}` : '',
    params.status ? `status=${encodeURIComponent(params.status)}` : ''
  ].filter(Boolean).join('&');
  return request<RectificationTask[]>(`/inspection/rectifications${query ? `?${query}` : ''}`);
}

export async function getRectificationDetail(id: number): Promise<RectificationTask | undefined> {
  if (USE_MOCK) {
    return getMockRectificationDetail(id);
  }
  const task = await request<RectificationTask>(`/inspection/rectifications/${id}`);
  const [beforePhotos, rectificationPhotos] = await Promise.all([
    downloadFilePaths(task.beforePhotoFileIds),
    downloadFilePaths(task.rectificationPhotoFileIds)
  ]);
  return {
    ...task,
    beforePhotos,
    rectificationPhotos
  };
}

export async function completeRectification(id: number, feedback: string, photos: string[] = [], photoFileIds: number[] = []) {
  if (USE_MOCK) {
    return completeMockRectification(id, feedback, photos);
  }
  return request(`/inspection/rectifications/${id}/complete`, {
    method: 'POST',
    data: { feedback, photoFileIds }
  });
}

export async function assignRectification(id: number, payload: { assigneeId?: number; deadline?: string; comment?: string }) {
  if (USE_MOCK) {
    return getMockRectificationDetail(id);
  }
  return request<RectificationTask>(`/inspection/rectifications/${id}/assign`, {
    method: 'POST',
    data: payload
  });
}

export async function escalateRectification(id: number, note = '') {
  if (USE_MOCK) {
    return getMockRectificationDetail(id);
  }
  return request<RectificationTask>(`/inspection/rectifications/${id}/escalate`, {
    method: 'POST',
    data: { note }
  });
}

export async function closeRectification(id: number) {
  if (USE_MOCK) {
    return closeMockRectification(id);
  }
  return request(`/inspection/rectifications/${id}/close`, {
    method: 'POST',
    data: { comment: '复查通过' }
  });
}

export async function rejectRectification(id: number, comment = '整改不符合要求，请继续处理') {
  if (USE_MOCK) {
    return rejectMockRectification(id, comment);
  }
  return request(`/inspection/rectifications/${id}/reject`, {
    method: 'POST',
    data: { comment }
  });
}
