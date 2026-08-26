import type { PageResult, QualityAssignee, QualityIssue, QualityIssueStatus, QualitySummary, QualityWeeklyInspection, QualityWeeklyInspectionStatus, QualityWeeklySummary, TodoItem } from '@/types';
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

export interface QualityWeeklyDraftItemPayload {
  itemKey: string;
  itemOrder: number;
  title?: string;
  location?: string;
  description?: string;
  severity?: 'NORMAL' | 'WARNING' | 'DANGER';
  assigneeId?: number;
  deadline?: string;
  beforePhotoFileIds: number[];
}

export interface QualityWeeklyDraftPayload {
  expectedVersion: number;
  inspectionDate?: string;
  conclusion?: string;
  overviewPhotoFileIds: number[];
  items: QualityWeeklyDraftItemPayload[];
}

const mockWeeklyInspections: QualityWeeklyInspection[] = [];

function localDate(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function weekEnd(weekStart: string) {
  const [year, month, day] = weekStart.split('-').map(Number);
  const date = new Date(year, month - 1, day + 6);
  return localDate(date);
}

function mockWeeklyInspection(projectId: number, weekStart: string): QualityWeeklyInspection {
  return {
    id: Date.now(), projectId, weekStart, weekEnd: weekEnd(weekStart), status: 'DRAFT',
    overviewPhotoFileIds: [], submittedIssueCount: 0, pendingCount: 0, recheckCount: 0,
    closedCount: 0, voidedCount: 0, version: 0, lateSubmission: false, draftItems: [], issues: []
  };
}

export async function getQualityWeeklyInspectionPage(
  projectId: number,
  pageNo = 1,
  pageSize = 20,
  status?: QualityWeeklyInspectionStatus,
  keyword = ''
) {
  if (USE_MOCK) {
    const items = mockWeeklyInspections
      .filter((item) => item.projectId === projectId && (!status || item.status === status))
      .filter((item) => !keyword.trim() || `${item.inspectionNo || ''}${item.weekStart}${item.conclusion || ''}`.includes(keyword.trim()));
    const start = (pageNo - 1) * pageSize;
    return { pageNo, pageSize, total: items.length, records: items.slice(start, start + pageSize) } as PageResult<QualityWeeklyInspection>;
  }
  const query = [
    `projectId=${projectId}`, `pageNo=${pageNo}`, `pageSize=${pageSize}`,
    status ? `status=${status}` : '', keyword.trim() ? `keyword=${encodeURIComponent(keyword.trim())}` : ''
  ].filter(Boolean).join('&');
  return request<PageResult<QualityWeeklyInspection>>(`/quality/weekly-inspections/page?${query}`);
}

export async function getQualityWeeklySummary(projectId: number) {
  if (USE_MOCK) return {} as QualityWeeklySummary;
  return request<QualityWeeklySummary>(`/quality/weekly-inspections/summary?projectId=${projectId}`);
}

export async function getQualityWeeklyInspection(id: number) {
  if (USE_MOCK) return mockWeeklyInspections.find((item) => item.id === id) as QualityWeeklyInspection;
  return request<QualityWeeklyInspection>(`/quality/weekly-inspections/${id}`);
}

export async function createOrRestoreQualityWeeklyDraft(projectId: number, weekStart: string) {
  if (USE_MOCK) {
    const existing = mockWeeklyInspections.find((item) => item.projectId === projectId && item.weekStart === weekStart);
    if (existing) return existing;
    const created = mockWeeklyInspection(projectId, weekStart);
    mockWeeklyInspections.unshift(created);
    return created;
  }
  return request<QualityWeeklyInspection>('/quality/weekly-inspections/drafts', {
    method: 'POST', data: { projectId, weekStart }
  });
}

export async function saveQualityWeeklyDraft(id: number, payload: QualityWeeklyDraftPayload) {
  if (USE_MOCK) {
    const draft = mockWeeklyInspections.find((item) => item.id === id) as QualityWeeklyInspection;
    Object.assign(draft, payload, { version: draft.version + 1, updateTime: new Date().toISOString(), draftItems: payload.items });
    return draft;
  }
  return request<QualityWeeklyInspection>(`/quality/weekly-inspections/${id}/draft`, { method: 'PUT', data: payload });
}

export async function submitQualityWeeklyInspection(id: number, expectedVersion: number) {
  if (USE_MOCK) {
    const draft = mockWeeklyInspections.find((item) => item.id === id) as QualityWeeklyInspection;
    const draftItems = [...draft.draftItems];
    const createdIssues = draftItems.map((item, index) => ({
      id: Date.now() + index + 1,
      projectId: draft.projectId,
      weeklyInspectionId: draft.id,
      inspectionItemOrder: item.itemOrder,
      issueNo: `Q-MOCK-${Date.now()}-${index + 1}`,
      title: item.title || `周检问题 ${index + 1}`,
      location: item.location,
      description: item.description,
      issuePhotoFileIds: [...item.beforePhotoFileIds],
      originalProblemPhotoFileIds: [...item.beforePhotoFileIds],
      severity: item.severity || 'NORMAL',
      status: 'PENDING' as const,
      assigneeId: item.assigneeId,
      assigneeName: item.assigneeName,
      deadline: item.deadline,
      rectificationPhotoFileIds: [],
      latestRectificationPhotoFileIds: [],
      overdue: false,
      dueText: item.deadline || '尽快处理',
      canRectify: true,
      canReview: false,
      logs: []
    }));
    mockQualityIssues.unshift(...createdIssues);
    Object.assign(draft, {
      status: 'SUBMITTED', submittedIssueCount: createdIssues.length, pendingCount: createdIssues.length,
      submittedTime: new Date().toISOString(), version: draft.version + 1, issues: createdIssues, draftItems: []
    });
    return draft;
  }
  return request<QualityWeeklyInspection>(`/quality/weekly-inspections/${id}/submit`, {
    method: 'POST', data: { expectedVersion }
  });
}

export async function discardQualityWeeklyDraft(id: number, expectedVersion: number) {
  if (USE_MOCK) {
    const index = mockWeeklyInspections.findIndex((item) => item.id === id);
    if (index >= 0) mockWeeklyInspections.splice(index, 1);
    return;
  }
  return request<void>(`/quality/weekly-inspections/${id}/discard`, {
    method: 'POST', data: { expectedVersion }
  });
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
  pageSize = 20,
  source: 'ALL' | 'WEEKLY' | 'HISTORICAL' = 'ALL'
) {
  if (USE_MOCK) {
    const items = (await getQualityIssues(projectId, status, keyword))
      .filter((item) => source === 'ALL' || (source === 'WEEKLY' ? Boolean(item.weeklyInspectionId) : !item.weeklyInspectionId));
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
    source !== 'ALL' ? `source=${source}` : '',
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

export async function submitQualityRectification(id: number, description: string, photoFileIds: number[] = []) {
  if (USE_MOCK) {
    const issue = mockQualityIssues.find((item) => item.id === id) as QualityIssue;
    issue.status = 'RECHECK'; issue.rectificationDescription = description; issue.rectificationPhotoFileIds = photoFileIds; issue.latestRectificationPhotoFileIds = photoFileIds; issue.canRectify = false; issue.canReview = true; issue.dueText = '等待复查';
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
