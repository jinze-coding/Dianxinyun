import { request } from './request';

export type GeneralInspectionResult = 'NORMAL' | 'ABNORMAL' | 'NA';

export interface GeneralInspectionTaskItem {
  id: number;
  itemKey: string;
  itemName: string;
  guidance?: string;
  standardReference?: string;
  allowNa: boolean;
  normalPhotoMin: number;
  abnormalPhotoMin: number;
  photoMax: number;
  normalDescriptionRequired: boolean;
  abnormalDescriptionRequired: boolean;
  sortOrder: number;
  result?: GeneralInspectionResult;
  description?: string;
  photoFileIds?: number[];
}

export interface GeneralInspectionTask {
  id: number;
  projectId: number;
  pointId: number;
  revisionNo: number;
  replacesTaskId?: number;
  planName: string;
  templateName: string;
  pointCode: string;
  pointName: string;
  locationDesc?: string;
  slotCode: string;
  slotName: string;
  occurrenceDate: string;
  availableTime: string;
  startTime: string;
  dueTime: string;
  assigneeId?: number;
  assigneeName?: string;
  reviewerId?: number;
  reviewerName?: string;
  qrRequired: boolean;
  scanVerified: boolean;
  status: string;
  displayStatus: string;
  overdue: boolean;
  lateSubmission: boolean;
  canExecute: boolean;
  canManage: boolean;
  submittedTime?: string;
  overallPhotoMin: number;
  overallPhotoMax: number;
  overallRemarkRequired: boolean;
  overallPhotoFileIds?: number[];
  remark?: string;
  publicRemark?: string;
  abnormalCount: number;
  version: number;
  items?: GeneralInspectionTaskItem[];
}

export interface GeneralInspectionScan {
  mode: string;
  publicCode: string;
  projectId: number;
  pointId: number;
  pointCode: string;
  pointName: string;
  locationDesc?: string;
  reason?: string;
  publicAccessEnabled: boolean;
  eligibleTasks: GeneralInspectionTask[];
}

export interface GeneralInspectionRectification {
  id: number;
  projectId: number;
  taskId: number;
  taskItemId: number;
  pointId: number;
  pointName: string;
  itemName: string;
  problemDesc?: string;
  requirement?: string;
  assigneeId?: number;
  assigneeName?: string;
  deadline?: string;
  reviewerId?: number;
  reviewerName?: string;
  status: string;
  feedback?: string;
  rectificationPhotoFileIds?: number[];
  completedTime?: string;
  reviewComment?: string;
  reviewTime?: string;
  rejectCount: number;
  closeTime?: string;
  version: number;
  overdue: boolean;
  canRectify: boolean;
  canReview: boolean;
  canAssign: boolean;
}

export interface GeneralInspectionUserOption {
  userId: number;
  userName: string;
  canSubmit: boolean;
  canRectify: boolean;
  canReview: boolean;
}

export interface PublicGeneralInspectionMonthly {
  projectShortName: string;
  pointCode: string;
  pointName: string;
  locationDesc?: string;
  month: string;
  shouldCheckCount: number;
  checkedCount: number;
  missedCount: number;
  abnormalCount: number;
  sections: Array<{
    versionLabel: string;
    templateName: string;
    itemNames: string[];
    rows: Array<{ date: string; slotName: string; status: string; inspectorName?: string; publicRemark?: string; results: string[] }>;
  }>;
}

const ROOT = '/general-inspections';

function query(params: Record<string, unknown>) {
  const value = Object.entries(params).filter(([, item]) => item !== undefined && item !== null && item !== '')
    .map(([key, item]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(item))}`).join('&');
  return value ? `?${value}` : '';
}

export function getGeneralInspectionTasks(params: { projectId?: number; status?: string; startDate?: string; endDate?: string; mine?: boolean } = {}) {
  return request<GeneralInspectionTask[]>(`${ROOT}/tasks${query(params)}`);
}

export function getGeneralInspectionTask(id: number) {
  return request<GeneralInspectionTask>(`${ROOT}/tasks/${id}`);
}

export function resolveGeneralInspectionScan(sceneCode: string) {
  return request<GeneralInspectionScan>(`${ROOT}/scan/resolve`, { method: 'POST', data: { sceneCode } });
}

export function verifyGeneralInspectionScan(taskId: number, sceneCode: string) {
  return request<GeneralInspectionTask>(`${ROOT}/tasks/${taskId}/scan`, { method: 'POST', data: { sceneCode } });
}

export interface GeneralInspectionSubmitPayload {
  expectedVersion: number;
  overallPhotoFileIds: number[];
  remark?: string;
  publicRemark?: string;
  items: Array<{ taskItemId: number; result: GeneralInspectionResult; description?: string; photoFileIds: number[]; rectifierId?: number; deadline?: string; requirement?: string }>;
}

export function submitGeneralInspectionTask(id: number, data: GeneralInspectionSubmitPayload) {
  return request<GeneralInspectionTask>(`${ROOT}/tasks/${id}/submit`, { method: 'POST', data });
}

export function reassignGeneralInspectionTask(id: number, data: { expectedVersion: number; assigneeId?: number; reviewerId?: number; reason: string }) {
  return request<GeneralInspectionTask>(`${ROOT}/tasks/${id}/reassign`, { method: 'POST', data });
}

export function getGeneralInspectionRectifications(params: { projectId?: number; status?: string; scope?: string } = {}) {
  return request<GeneralInspectionRectification[]>(`${ROOT}/rectifications${query(params)}`);
}

export function getGeneralInspectionRectification(id: number) {
  return request<GeneralInspectionRectification>(`${ROOT}/rectifications/${id}`);
}

export function getGeneralInspectionUserOptions(projectId: number) {
  return request<GeneralInspectionUserOption[]>(`${ROOT}/user-options?projectId=${projectId}`);
}

export function assignGeneralInspectionRectification(id: number, data: { expectedVersion: number; assigneeId: number; deadline: string; requirement?: string; comment?: string }) {
  return request<GeneralInspectionRectification>(`${ROOT}/rectifications/${id}/assign`, { method: 'POST', data });
}

export function completeGeneralInspectionRectification(id: number, data: { expectedVersion: number; comment: string; photoFileIds: number[] }) {
  return request<GeneralInspectionRectification>(`${ROOT}/rectifications/${id}/complete`, { method: 'POST', data });
}

export function reviewGeneralInspectionRectification(id: number, approve: boolean, data: { expectedVersion: number; comment: string }) {
  return request<GeneralInspectionRectification>(`${ROOT}/rectifications/${id}/${approve ? 'close' : 'reject'}`, { method: 'POST', data });
}

export function getPublicGeneralInspectionMonthly(code: string, month?: string) {
  return request<PublicGeneralInspectionMonthly>(`/public/general-inspection-points/${encodeURIComponent(code.replace(/^P:/i, ''))}/monthly-records${query({ month })}`, { skipAuthRedirect: true });
}
