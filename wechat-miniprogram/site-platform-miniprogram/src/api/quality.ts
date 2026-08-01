import type { PageResult, QualityAssignee, QualityIssue, QualityIssueStatus, QualitySummary, TodoItem } from '@/types';
import { previewAreas } from '@/pages/design-preview/previewData';
import { request, USE_MOCK } from './request';

const mockQualityIssues: QualityIssue[] = previewAreas.flatMap((area) => area.quality.issues.map((item) => ({
  id: item.id,
  projectId: area.id,
  issueNo: `Q-MOCK-${item.id}`,
  title: item.title,
  location: item.location,
  severity: item.status === 'OVERDUE' ? 'DANGER' : 'NORMAL',
  status: item.status === 'OVERDUE' ? 'PENDING' : item.status,
  assigneeName: item.owner,
  overdue: item.status === 'OVERDUE',
  dueText: item.dueText,
  canRectify: item.status === 'PENDING' || item.status === 'OVERDUE',
  canReview: item.status === 'RECHECK',
  logs: []
})));

export interface QualityIssuePayload {
  projectId: number;
  requestKey?: string;
  title: string;
  location?: string;
  description?: string;
  severity?: 'NORMAL' | 'WARNING' | 'DANGER';
  assigneeId?: number;
  deadline?: string;
  photoFileIds: number[];
}

export async function getQualityIssues(projectId: number, status: 'ALL' | 'OVERDUE' | QualityIssueStatus = 'ALL', keyword = '') {
  if (USE_MOCK) {
    return mockQualityIssues.filter((item) => item.projectId === projectId)
      .filter((item) => status === 'ALL' || (status === 'OVERDUE' ? item.overdue : item.status === status))
      .filter((item) => !keyword.trim() || `${item.title}${item.location || ''}${item.assigneeName || ''}`.includes(keyword.trim()));
  }
  const query = [
    `projectId=${projectId}`,
    status !== 'ALL' ? `status=${status}` : '',
    keyword.trim() ? `keyword=${encodeURIComponent(keyword.trim())}` : ''
  ].filter(Boolean).join('&');
  return request<QualityIssue[]>(`/quality/issues?${query}`);
}

export async function getQualityIssuePage(
  projectId: number,
  status: 'ALL' | 'OVERDUE' | QualityIssueStatus = 'ALL',
  keyword = '',
  pageNo = 1,
  pageSize = 20
) {
  if (USE_MOCK) {
    const items = await getQualityIssues(projectId, status, keyword);
    const start = (pageNo - 1) * pageSize;
    return {
      pageNo,
      pageSize,
      total: items.length,
      records: items.slice(start, start + pageSize)
    } as PageResult<QualityIssue>;
  }
  const query = [
    `projectId=${projectId}`,
    `pageNo=${pageNo}`,
    `pageSize=${pageSize}`,
    status !== 'ALL' ? `status=${status}` : '',
    keyword.trim() ? `keyword=${encodeURIComponent(keyword.trim())}` : ''
  ].filter(Boolean).join('&');
  return request<PageResult<QualityIssue>>(`/quality/issues/page?${query}`);
}

export async function getQualitySummary(projectId: number) {
  if (USE_MOCK) {
    const items = mockQualityIssues.filter((item) => item.projectId === projectId);
    const closed = items.filter((item) => item.status === 'CLOSED').length;
    return {
      todayCheckCount: items.length,
      pendingCount: items.filter((item) => item.status === 'PENDING').length,
      overdueCount: items.filter((item) => item.overdue).length,
      recheckCount: items.filter((item) => item.status === 'RECHECK').length,
      closedCount: closed,
      closureRate: items.length ? Math.round(closed * 100 / items.length) : 0,
      canManage: true
    };
  }
  return request<QualitySummary>(`/quality/issues/summary?projectId=${projectId}`);
}

export async function getQualityAssignees(projectId: number) {
  if (USE_MOCK) {
    return [{ userId: 1, username: 'mock-user', realName: '演示整改人', displayName: '演示整改人' }] as QualityAssignee[];
  }
  return request<QualityAssignee[]>(`/quality/issues/assignees?projectId=${projectId}`);
}

export async function getQualityTodos(projectId?: number) {
  if (USE_MOCK) return [] as TodoItem[];
  return request<TodoItem[]>(`/quality/issues/todos${projectId ? `?projectId=${projectId}` : ''}`);
}

export async function getQualityIssue(id: number) {
  if (USE_MOCK) return mockQualityIssues.find((item) => item.id === id) as QualityIssue;
  return request<QualityIssue>(`/quality/issues/${id}`);
}

export async function createQualityIssue(payload: QualityIssuePayload) {
  if (USE_MOCK) {
    const issue: QualityIssue = { id: Date.now(), projectId: payload.projectId, issueNo: `Q-MOCK-${Date.now()}`, title: payload.title, location: payload.location, description: payload.description, severity: payload.severity || 'NORMAL', status: 'PENDING', assigneeId: payload.assigneeId, deadline: payload.deadline, overdue: false, dueText: payload.deadline || '尽快处理', canRectify: true, canReview: false, logs: [] };
    mockQualityIssues.unshift(issue);
    return issue;
  }
  return request<QualityIssue>('/quality/issues', { method: 'POST', data: payload });
}

export async function submitQualityRectification(id: number, description: string, photoFileIds: number[] = []) {
  if (USE_MOCK) {
    const issue = mockQualityIssues.find((item) => item.id === id) as QualityIssue;
    issue.status = 'RECHECK'; issue.rectificationDescription = description; issue.rectificationPhotoFileIds = photoFileIds; issue.canRectify = false; issue.canReview = true; issue.dueText = '等待复查';
    return issue;
  }
  return request<QualityIssue>(`/quality/issues/${id}/rectify`, {
    method: 'POST',
    data: { description, photoFileIds }
  });
}

export async function reviewQualityIssue(id: number, passed: boolean, comment = '', photoFileIds: number[] = []) {
  if (USE_MOCK) {
    const issue = mockQualityIssues.find((item) => item.id === id) as QualityIssue;
    issue.status = passed ? 'CLOSED' : 'PENDING'; issue.reviewComment = comment; issue.canReview = false; issue.canRectify = !passed; issue.dueText = passed ? '已关闭' : '继续整改';
    return issue;
  }
  return request<QualityIssue>(`/quality/issues/${id}/review`, {
    method: 'POST',
    data: { passed, comment, photoFileIds }
  });
}

export async function assignQualityIssue(
  id: number,
  payload: { assigneeId?: number; deadline?: string; comment?: string }
) {
  if (USE_MOCK) {
    const issue = mockQualityIssues.find((item) => item.id === id) as QualityIssue;
    if (payload.assigneeId) issue.assigneeId = payload.assigneeId;
    if (payload.deadline) issue.deadline = payload.deadline;
    return issue;
  }
  return request<QualityIssue>(`/quality/issues/${id}/assign`, {
    method: 'POST',
    data: payload
  });
}

export async function voidQualityIssue(id: number, comment: string) {
  if (USE_MOCK) {
    const issue = mockQualityIssues.find((item) => item.id === id) as QualityIssue;
    issue.status = 'VOIDED';
    issue.canRectify = false;
    issue.canReview = false;
    issue.dueText = '已作废';
    return issue;
  }
  return request<QualityIssue>(`/quality/issues/${id}/void`, {
    method: 'POST',
    data: { comment }
  });
}
