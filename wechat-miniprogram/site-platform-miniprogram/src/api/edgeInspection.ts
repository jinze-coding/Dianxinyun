import { request } from './request';

export type EdgeInspectionResult = 'NORMAL' | 'ABNORMAL';

export interface EdgeInspectionTaskItem {
  id: number;
  itemKey?: string;
  itemName: string;
  guidance?: string;
  standardReference?: string;
  sortOrder?: number;
  result?: EdgeInspectionResult;
  description?: string;
  photoFileIds?: number[];
}

export interface EdgeInspectionTask {
  id: number;
  projectId: number;
  pointId: number;
  pointCode: string;
  pointName: string;
  pointTypeCode?: string;
  pointTypeName?: string;
  categoryCode?: string;
  categoryName?: string;
  buildingName?: string;
  floorName?: string;
  building?: string;
  floor?: string;
  locationDesc?: string;
  occurrenceDate: string;
  slotName?: string;
  availableTime?: string;
  startTime?: string;
  dueTime: string;
  assigneeId?: number;
  assigneeName?: string;
  rectifierId?: number;
  rectifierName?: string;
  reviewerId?: number;
  reviewerName?: string;
  status: string;
  displayStatus?: string;
  overdue?: boolean;
  lateSubmission?: boolean;
  canExecute?: boolean;
  canManage?: boolean;
  canReassign?: boolean;
  submittedTime?: string;
  overallPhotoFileIds?: number[];
  remark?: string;
  abnormalCount?: number;
  version: number;
  items?: EdgeInspectionTaskItem[];
}

export interface EdgeInspectionUserOption {
  userId: number;
  userName: string;
  canSubmit?: boolean;
  canRectify?: boolean;
  canReview?: boolean;
}

export interface EdgeInspectionRectificationItem {
  id?: number;
  rectificationId?: number;
  taskItemId?: number;
  itemName: string;
  problemDesc?: string;
  description?: string;
  requirement?: string;
  evidencePhotoFileIds?: number[];
  status?: string;
  feedback?: string;
  rectificationPhotoFileIds?: number[];
  photoFileIds?: number[];
  completedTime?: string;
  reviewComment?: string;
  reviewTime?: string;
  rejectCount?: number;
  version: number;
}

export interface EdgeInspectionRectificationSheet {
  id?: number;
  taskId: number;
  projectId: number;
  pointId?: number;
  pointCode?: string;
  pointName: string;
  pointTypeCode?: string;
  pointTypeName?: string;
  categoryName?: string;
  buildingName?: string;
  floorName?: string;
  occurrenceDate?: string;
  locationDesc?: string;
  status: string;
  reviewComment?: string;
  assigneeId?: number;
  assigneeName?: string;
  reviewerId?: number;
  reviewerName?: string;
  deadline?: string;
  overdue?: boolean;
  canRectify?: boolean;
  canReview?: boolean;
  canAssign?: boolean;
  version: number;
  items: EdgeInspectionRectificationItem[];
}

const ROOT = '/edge-inspections';

function query(params: Record<string, unknown>) {
  const value = Object.entries(params)
    .filter(([, item]) => item !== undefined && item !== null && item !== '')
    .map(([key, item]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(item))}`)
    .join('&');
  return value ? `?${value}` : '';
}

export function getEdgeInspectionTasks(params: {
  projectId?: number;
  status?: string;
  startDate?: string;
  endDate?: string;
  mine?: boolean;
} = {}) {
  return request<EdgeInspectionTask[]>(`${ROOT}/tasks${query(params)}`);
}

export function getEdgeInspectionTask(id: number) {
  return request<EdgeInspectionTask>(`${ROOT}/tasks/${id}`);
}

export interface EdgeInspectionSubmitPayload {
  expectedVersion: number;
  overallPhotoFileIds: number[];
  remark?: string;
  items: Array<{
    taskItemId: number;
    result: EdgeInspectionResult;
    description?: string;
    photoFileIds: number[];
  }>;
}

export function submitEdgeInspectionTask(id: number, data: EdgeInspectionSubmitPayload) {
  return request<EdgeInspectionTask>(`${ROOT}/tasks/${id}/submit`, { method: 'POST', data });
}

export interface EdgeInspectionTaskReassignPayload {
  expectedVersion: number;
  assigneeId: number;
  reason: string;
}

export function reassignEdgeInspectionTask(id: number, data: EdgeInspectionTaskReassignPayload) {
  return request<EdgeInspectionTask>(`${ROOT}/tasks/${id}/reassign`, { method: 'POST', data });
}

export function getEdgeInspectionRectifications(params: {
  projectId?: number;
  status?: string;
  scope?: string;
} = {}) {
  return request<EdgeInspectionRectificationSheet[]>(`${ROOT}/rectifications${query(params)}`);
}

export function getEdgeInspectionRectification(taskId: number) {
  return request<EdgeInspectionRectificationSheet>(`${ROOT}/rectifications/${taskId}`);
}

export function getEdgeInspectionUserOptions(projectId: number) {
  return request<EdgeInspectionUserOption[]>(`${ROOT}/user-options?projectId=${encodeURIComponent(String(projectId))}`);
}

export function reassignEdgeInspectionRectification(taskId: number, data: {
  expectedVersion: number;
  assigneeId?: number;
  reviewerId?: number;
  reason: string;
}) {
  return request<EdgeInspectionRectificationSheet>(`${ROOT}/rectifications/${taskId}/reassign`, { method: 'POST', data });
}

export function completeEdgeInspectionRectification(taskId: number, data: {
  expectedVersion: number;
  items: Array<{
    rectificationId: number;
    expectedVersion: number;
    feedback: string;
    photoFileIds: number[];
  }>;
}) {
  return request<EdgeInspectionRectificationSheet>(`${ROOT}/rectifications/${taskId}/complete`, { method: 'POST', data });
}

export function reviewEdgeInspectionRectification(taskId: number, approve: boolean, data: {
  expectedVersion: number;
  comment?: string;
}) {
  return request<EdgeInspectionRectificationSheet>(`${ROOT}/rectifications/${taskId}/${approve ? 'close' : 'reject'}`, {
    method: 'POST',
    data
  });
}
