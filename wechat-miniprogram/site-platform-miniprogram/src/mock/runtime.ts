import {
  mockElectricBoxes,
  mockInspectionRecords,
  mockProjects,
  mockPublicElectricBoxSummary,
  mockRectifications,
  mockTodos
} from '@/mock/data';
import type {
  CheckResult,
  ElectricBox,
  InspectionItemResult,
  InspectionRecord,
  Project,
  PublicElectricBoxSummary,
  PublicElectricBoxMonthly,
  UnifiedElectricBoxScan,
  PublicInspectionRecord,
  RectificationTask,
  TodoItem
} from '@/types';

const MOCK_DATE = '2026-07-11';
const MOCK_TIME = '2026-07-11 09:30';
const REVIEW_DUE_TIME = '2026-07-12 09:30';
const MOCK_REVIEWER_NAME = '张安全';
const RECHECK_DAYS = 3;

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T;
}

function nextId(items: Array<{ id?: number | null }>, fallback: number) {
  return items.reduce((max, item) => Math.max(max, item.id || 0), fallback) + 1;
}

function todoProjectName(projectId: number) {
  return mockRuntime.projects.find((item) => item.id === projectId)?.shortName || '现场项目';
}

function removeTodo(type: TodoItem['type'], targetId: number) {
  mockRuntime.todos = mockRuntime.todos.filter((item) => !(item.type === type && item.targetId === targetId));
}

function addTodo(todo: Omit<TodoItem, 'id'>) {
  const exists = mockRuntime.todos.some((item) => item.type === todo.type && item.targetId === todo.targetId);
  if (exists) return;
  mockRuntime.todos.unshift({
    id: nextId(mockRuntime.todos, 100),
    ...todo
  });
}

function plusDays(dateText: string | undefined, days: number) {
  if (!dateText) return MOCK_DATE;
  const [year, month, day] = dateText.slice(0, 10).split('-').map(Number);
  const date = new Date(year, month - 1, day);
  if (Number.isNaN(date.getTime())) return MOCK_DATE;
  date.setDate(date.getDate() + days);
  const yyyy = date.getFullYear();
  const mm = String(date.getMonth() + 1).padStart(2, '0');
  const dd = String(date.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}

function appendRectificationLog(task: RectificationTask, actionType: string, fromStatus: RectificationTask['status'], toStatus: RectificationTask['status'], comment = '') {
  task.reviewLogs = [
    ...(task.reviewLogs || []),
    {
      actionType,
      fromStatus,
      toStatus,
      operatorName: actionType === 'COMPLETE' ? task.assigneeName : MOCK_REVIEWER_NAME,
      comment,
      createTime: MOCK_TIME
    }
  ];
}

function toPublicRecord(record: InspectionRecord): PublicInspectionRecord {
  return {
    checkDate: record.checkDate,
    inspectedAt: record.inspectedAt,
    source: record.source,
    status: 'COMPLETED',
    abnormalCount: record.abnormalCount
  };
}

function updateProjectCounts() {
  mockRuntime.projects = mockRuntime.projects.map((project) => {
    const boxes = mockRuntime.electricBoxes.filter((box) => box.projectId === project.id);
    const todos = mockRuntime.todos.filter((todo) => todo.type === 'INSPECTION' && todo.projectName === project.shortName);
    const todayRecords = mockRuntime.inspectionRecords.filter((record) => record.projectId === project.id
      && record.source === 'ELECTRICIAN_DAILY' && record.checkDate === MOCK_DATE);
    return {
      ...project,
      electricBoxTotal: boxes.length || project.electricBoxTotal,
      pendingTodoCount: todos.length,
      todayInspectionCount: todayRecords.length,
      pendingReviewCount: 0,
      pendingRectificationCount: 0
    };
  });
}

export const mockRuntime: {
  projects: Project[];
  electricBoxes: ElectricBox[];
  inspectionRecords: InspectionRecord[];
  rectifications: RectificationTask[];
  todos: TodoItem[];
} = {
  projects: clone(mockProjects),
  electricBoxes: clone(mockElectricBoxes),
  inspectionRecords: clone(mockInspectionRecords),
  rectifications: clone(mockRectifications),
  todos: clone(mockTodos)
};

updateProjectCounts();

export function getMockProjects() {
  updateProjectCounts();
  return clone(mockRuntime.projects);
}

export function getMockProjectDetail(projectId: number) {
  updateProjectCounts();
  return clone(mockRuntime.projects.find((item) => item.id === projectId));
}

export function getMockElectricBoxes(projectId: number, keyword?: string) {
  const normalizedKeyword = keyword?.trim().toLowerCase() || '';
  return clone(mockRuntime.electricBoxes.filter((item) => item.projectId === projectId
    && (!normalizedKeyword || [
      item.boxCode,
      item.boxName,
      item.installLocation,
      item.responsibleElectricianName
    ].some((value) => String(value || '').toLowerCase().includes(normalizedKeyword)))));
}

export function getMockElectricBoxDetail(id: number) {
  return clone(mockRuntime.electricBoxes.find((item) => item.id === id));
}

export function resolveMockQrCode(qrCode: string) {
  const normalized = (qrCode || 'EBQR-001').trim().toUpperCase();
  const box = mockRuntime.electricBoxes.find((item) => {
    return item.qrCode?.toUpperCase() === normalized || item.boxCode.toUpperCase() === normalized || item.publicCode?.toUpperCase() === normalized;
  });
  return box ? clone(box) : undefined;
}

export function resolveMockUnifiedCode(sceneCode: string, role: 'ELECTRICIAN' | 'SAFETY' | 'EXTERNAL' = 'ELECTRICIAN'): UnifiedElectricBoxScan {
  const normalized = decodeURIComponent(sceneCode || '').replace(/^B:/i, '').trim().toUpperCase();
  // 兼容已经从 Web 端下载到桌面的早期演示统一码，避免重新生成二维码后
  // 开发者工具无法继续演示同一码进入巡检页面。该映射只存在于 mock 运行时，
  // 正式环境仍以数据库 electric_box.public_code 的真实绑定关系为准。
  const legacyMockAliases: Record<string, string> = {
    'PUB-6E9CF2675C8F4903': 'PUB-001'
  };
  const effectiveCode = legacyMockAliases[normalized] || normalized;
  const numericSuffix = effectiveCode.match(/(\d{3})$/)?.[1];
  const box = mockRuntime.electricBoxes.find((item) => item.publicCode?.toUpperCase() === effectiveCode || item.boxCode.toUpperCase() === effectiveCode)
    || (numericSuffix
      ? mockRuntime.electricBoxes.find((item) => item.boxCode.toUpperCase().endsWith(numericSuffix))
      : undefined);
  if (!box) throw new Error('未找到该巡检码对应的电箱');
  const internal = role !== 'EXTERNAL';
  const todayRecord = mockRuntime.inspectionRecords.find((record) => record.electricBoxId === box.id
    && record.source === 'ELECTRICIAN_DAILY' && record.checkDate === MOCK_DATE && record.status !== 'DRAFT');
  const allowedActions: UnifiedElectricBoxScan['allowedActions'] = ['VIEW_PUBLIC_MONTHLY'];
  if (internal && box.status === 'ACTIVE') {
    if (box.inspectionRequired !== false) allowedActions.unshift(todayRecord ? 'VIEW_COMPLETED_RECORD' : 'DAILY_INSPECTION');
    allowedActions.unshift('VIEW_RECORDS');
  }
  return {
    sceneCode: `B:${box.publicCode}`,
    mode: internal ? 'INTERNAL' : 'PUBLIC_READ_ONLY',
    reason: role === 'EXTERNAL' ? '外部扫码仅可查看月度检查表' : '已识别巡检权限，可填写日检或查看记录',
    electricBoxId: internal ? box.id : undefined,
    projectId: internal ? box.projectId : undefined,
    publicCode: box.publicCode || box.boxCode,
    boxCode: box.boxCode,
    boxName: box.boxName,
    installLocation: box.installLocation,
    status: box.status,
    publicAccessEnabled: true,
    inspectionRequired: box.inspectionRequired !== false,
    authenticated: internal,
    projectAuthorized: internal,
    directAction: role === 'EXTERNAL'
      ? 'VIEW_PUBLIC_MONTHLY'
      : box.inspectionRequired !== false
          ? 'START_INSPECTION'
          : 'VIEW_RECORDS',
    todayRecordId: internal ? (todayRecord?.id || undefined) : undefined,
    allowedActions
  };
}

export function getMockInspectionRecords(projectId: number, electricBoxId?: number, month?: string, checkDate?: string) {
  if (month && checkDate) throw new Error('月份和日期不能同时筛选');
  return clone(mockRuntime.inspectionRecords.filter((item) => {
    return item.source === 'ELECTRICIAN_DAILY'
      && item.status !== 'DRAFT'
      && item.projectId === projectId
      && (checkDate ? item.checkDate === checkDate : !month || item.checkDate.startsWith(month))
      && (!electricBoxId || item.electricBoxId === electricBoxId);
  }).map((item) => ({
    ...item,
    status: 'COMPLETED' as const,
    reviewStatus: 'NOT_REQUIRED',
    remark: /复核|整改|安全员/.test(item.remark || '')
      ? (Number(item.abnormalCount || 0) > 0 ? '巡检发现异常项' : '电箱巡检已完成')
      : item.remark,
    reviewLogs: []
  })));
}

export function getMockInspectionSummary(params: {
  projectId: number;
  boxId?: number;
  month?: string;
  checkDate?: string;
}) {
  if (Boolean(params.month) === Boolean(params.checkDate)) {
    throw new Error('月份和日期必须且只能选择一个');
  }
  const periodType = params.checkDate ? 'DAY' as const : 'MONTH' as const;
  const periodValue = params.checkDate || String(params.month);
  const month = params.checkDate?.slice(0, 7) || String(params.month);
  const boxes = mockRuntime.electricBoxes.filter((item) => item.projectId === params.projectId
    && (!params.boxId || item.id === params.boxId)
    && item.status === 'ACTIVE'
    && item.inspectionRequired !== false
    && (!params.checkDate || (!item.scopeEffectiveDate || params.checkDate >= item.scopeEffectiveDate))
    && (!params.checkDate || (!item.scopeEndDate || params.checkDate <= item.scopeEndDate)));
  const records = mockRuntime.inspectionRecords.filter((item) => item.projectId === params.projectId
    && (!params.boxId || item.electricBoxId === params.boxId)
    && item.source === 'ELECTRICIAN_DAILY'
    && item.status !== 'DRAFT'
    && (params.checkDate ? item.checkDate === params.checkDate : item.checkDate.startsWith(month)));
  const dailyRecords = records;
  const targetYear = Number(month.slice(0, 4));
  const targetMonth = Number(month.slice(5, 7));
  const mockYear = Number(MOCK_DATE.slice(0, 4));
  const mockMonth = Number(MOCK_DATE.slice(5, 7));
  const monthDays = new Date(targetYear, targetMonth, 0).getDate();
  const elapsedDays = targetYear === mockYear && targetMonth === mockMonth
    ? Number(MOCK_DATE.slice(8, 10))
    : new Date(targetYear, targetMonth - 1, 1).getTime() > new Date(mockYear, mockMonth - 1, 1).getTime() ? 0 : monthDays;
  const shouldCheck = params.checkDate ? boxes.length : boxes.length * elapsedDays;
  const requiredBoxIds = new Set(boxes.map((item) => item.id));
  const checkedKeys = new Set(dailyRecords
    .filter((item) => requiredBoxIds.has(Number(item.electricBoxId)))
    .map((item) => `${item.electricBoxId}-${item.checkDate}`));
  const abnormalKeys = new Set(dailyRecords
    .filter((item) => requiredBoxIds.has(Number(item.electricBoxId)) && item.abnormalCount > 0)
    .map((item) => `${item.electricBoxId}-${item.checkDate}`));
  return clone({
    projectId: params.projectId,
    electricBoxId: params.boxId,
    month,
    periodType,
    periodValue,
    shouldCheck,
    checked: checkedKeys.size,
    missed: Math.max(shouldCheck - checkedKeys.size, 0),
    abnormal: params.checkDate
      ? abnormalKeys.size
      : dailyRecords.filter((item) => requiredBoxIds.has(Number(item.electricBoxId)) && item.abnormalCount > 0).length,
    openRectification: 0,
    records
  });
}

export function getMockInspectionRecordDetail(id: number) {
  const record = mockRuntime.inspectionRecords.find((item) => item.id === id);
  return record ? clone({
    ...record,
    status: 'COMPLETED' as const,
    reviewStatus: 'NOT_REQUIRED',
    remark: /复核|整改|安全员/.test(record.remark || '')
      ? (Number(record.abnormalCount || 0) > 0 ? '巡检发现异常项' : '电箱巡检已完成')
      : record.remark,
    reviewLogs: []
  }) : undefined;
}

export function getMockReviewRecords(
  projectId?: number,
  status?: InspectionRecord['status'],
  reviewScope?: 'MINE' | 'UNASSIGNED' | 'ASSIGNED' | '',
  reviewOverdue?: boolean
) {
  return clone(mockRuntime.inspectionRecords.filter((item) => {
    const inProject = projectId === undefined || item.projectId === projectId;
    const inStatus = status
      ? item.status === status
      : ['REVIEW_PENDING', 'REVIEW_PASSED', 'REVIEW_REJECTED', 'RECTIFICATION_PENDING', 'CLOSED'].includes(item.status);
    const inScope = reviewScope === 'UNASSIGNED'
      ? !item.assignedReviewerName
      : reviewScope === 'ASSIGNED'
        ? Boolean(item.assignedReviewerName)
        : reviewScope === 'MINE'
          ? !item.assignedReviewerName || item.assignedReviewerName === MOCK_REVIEWER_NAME
          : true;
    const inOverdue = reviewOverdue === undefined || Boolean(item.reviewOverdue) === reviewOverdue;
    return inProject && inStatus && inScope && inOverdue;
  }));
}

export function submitMockInspectionRecord(payload: {
  projectId: number;
  electricBoxId: number;
  boxCode: string;
  checkDate?: string;
  remark: string;
  outerPhotoFileIds?: number[];
  innerPhotoFileIds?: number[];
  outerPhotos?: string[];
  innerPhotos?: string[];
  items: Array<{ itemCode: string; itemName: string; result: CheckResult; description?: string }>;
}) {
  const box = mockRuntime.electricBoxes.find((item) => item.id === payload.electricBoxId);
  const abnormalCount = payload.items.filter((item) => item.result === 'ABNORMAL').length;
  const record: InspectionRecord = {
    id: nextId(mockRuntime.inspectionRecords, 5000),
    projectId: payload.projectId,
    electricBoxId: payload.electricBoxId,
    boxCode: payload.boxCode,
    boxName: box?.boxName,
    installLocation: box?.installLocation,
    checkDate: payload.checkDate || MOCK_DATE,
    source: 'ELECTRICIAN_DAILY',
    inspectorName: box?.responsibleElectricianName || '张电工',
    inspectedAt: MOCK_TIME,
    status: 'COMPLETED',
    reviewStatus: 'NOT_REQUIRED',
    reviewOverdue: 0,
    abnormalCount,
    outerPhotoCount: payload.outerPhotos?.length || payload.outerPhotoFileIds?.length || 0,
    innerPhotoCount: payload.innerPhotos?.length || payload.innerPhotoFileIds?.length || 0,
    outerPhotos: payload.outerPhotos,
    innerPhotos: payload.innerPhotos,
    remark: payload.remark || '',
    items: clone(payload.items) as InspectionItemResult[],
    reviewLogs: []
  };
  mockRuntime.inspectionRecords.unshift(record);
  if (box) {
    box.todayStatus = abnormalCount ? 'ABNORMAL' : 'CHECKED';
    box.lastCheckDate = payload.checkDate || MOCK_DATE;
  }
  removeTodo('INSPECTION', payload.electricBoxId);
  updateProjectCounts();
  return clone(record);
}

export function reviewMockInspectionRecord(
  id: number,
  action: 'PASS' | 'REJECT' | 'RECTIFY',
  comment = '',
  options: { assigneeName?: string; requirement?: string } = {}
) {
  const record = mockRuntime.inspectionRecords.find((item) => item.id === id);
  if (!record) return { id, action };
  const box = mockRuntime.electricBoxes.find((item) => item.id === record.electricBoxId);
  removeTodo('REVIEW', id);
  record.reviewerName = '张安全';
  record.reviewTime = MOCK_TIME;
  record.reviewStatus = action;
  record.reviewComment = comment;
  record.reviewLogs = [
    ...(record.reviewLogs || []),
    {
      actionType: action,
      operatorName: MOCK_REVIEWER_NAME,
      comment,
      createTime: MOCK_TIME
    }
  ];
  if (action === 'PASS') {
    record.status = 'REVIEW_PASSED';
    if (box && box.pendingRectificationCount === 0) {
      box.todayStatus = 'CHECKED';
    }
  }
  if (action === 'REJECT') {
    record.status = 'REVIEW_REJECTED';
    addTodo({
      type: 'INSPECTION',
      title: `${record.boxCode} 复核退回补录`,
      projectName: todoProjectName(record.projectId || 1),
      boxCode: record.boxCode,
      installLocation: record.installLocation,
      dueText: '今天',
      targetId: record.electricBoxId || 0,
      priority: 'warning'
    });
  }
  if (action === 'RECTIFY') {
    record.status = 'RECTIFICATION_PENDING';
    const rectification = createRectificationFromRecord(record, {
      problemDesc: comment,
      requirement: options.requirement,
      assigneeName: options.assigneeName
    });
    addTodo({
      type: 'RECTIFICATION',
      title: `${record.boxCode} 异常整改`,
      projectName: todoProjectName(record.projectId || 1),
      boxCode: record.boxCode,
      installLocation: record.installLocation,
      dueText: `${rectification.deadline} 前`,
      targetId: rectification.id,
      priority: 'danger'
    });
    if (box) {
      box.todayStatus = 'ABNORMAL';
      box.pendingRectificationCount += 1;
    }
  }
  updateProjectCounts();
  return { id, action };
}

function createRectificationFromRecord(record: InspectionRecord, options: {
  problemDesc?: string;
  requirement?: string;
  assigneeName?: string;
  deadline?: string;
  beforePhotos?: string[];
} = {}) {
  const existing = mockRuntime.rectifications.find((item) => item.electricBoxId === record.electricBoxId && item.status !== 'CLOSED');
  if (existing) return existing;
  const task: RectificationTask = {
    id: nextId(mockRuntime.rectifications, 7000),
    projectId: record.projectId || 1,
    electricBoxId: record.electricBoxId || 0,
    boxCode: record.boxCode,
    boxName: record.boxName,
    orderNo: `ZG-${MOCK_DATE.replace(/-/g, '')}-${String(nextId(mockRuntime.rectifications, 0)).padStart(3, '0')}`,
    inspectorName: record.inspectorName,
    createdAt: MOCK_TIME,
    problemDesc: options.problemDesc || record.remark || '检查项存在异常，请按要求整改。',
    requirement: options.requirement || '3天内完成整改，上传整改说明和整改照片后提交复查。',
    assigneeName: options.assigneeName || '张电工',
    responsiblePhone: '138****1234',
    deadline: options.deadline || '2026-07-14',
    status: 'PENDING',
    rejectCount: 0,
    beforePhotos: options.beforePhotos || record.problemPhotos || record.outerPhotos || ['/static/mock-photo.svg'],
    reviewLogs: []
  };
  mockRuntime.rectifications.unshift(task);
  return task;
}

export function submitMockSafetySpotCheck(payload: {
  projectId: number;
  electricBoxId: number;
  boxCode: string;
  problemDescription: string;
  requirement: string;
  deadline: string;
  assigneeName?: string;
  problemPhotos: string[];
}) {
  const box = mockRuntime.electricBoxes.find((item) => item.id === payload.electricBoxId);
  const record: InspectionRecord = {
    id: nextId(mockRuntime.inspectionRecords, 5000),
    projectId: payload.projectId,
    electricBoxId: payload.electricBoxId,
    boxCode: payload.boxCode,
    boxName: box?.boxName,
    installLocation: box?.installLocation,
    checkDate: MOCK_DATE,
    source: 'SAFETY_SPOT_CHECK',
    inspectorName: '张安全',
    inspectedAt: MOCK_TIME,
    status: 'RECTIFICATION_PENDING',
    reviewStatus: 'RECTIFY',
    reviewerName: MOCK_REVIEWER_NAME,
    reviewTime: MOCK_TIME,
    assignedReviewerName: MOCK_REVIEWER_NAME,
    reviewDueTime: MOCK_TIME,
    reviewOverdue: 0,
    abnormalCount: 1,
    outerPhotoCount: 0,
    innerPhotoCount: 0,
    problemPhotoCount: payload.problemPhotos.length,
    problemPhotos: payload.problemPhotos,
    remark: payload.problemDescription,
    items: mockRuntime.inspectionRecords[0]?.items?.map((item, index) => ({
      ...item,
      result: index === 0 ? 'ABNORMAL' : 'NORMAL',
      description: index === 0 ? payload.problemDescription : ''
    })) as InspectionItemResult[],
    reviewLogs: [{
      actionType: 'RECTIFY',
      operatorName: MOCK_REVIEWER_NAME,
      comment: payload.problemDescription,
      createTime: MOCK_TIME
    }]
  };
  mockRuntime.inspectionRecords.unshift(record);
  const rectification = createRectificationFromRecord(record, {
    problemDesc: payload.problemDescription,
    requirement: payload.requirement,
    deadline: payload.deadline,
    assigneeName: payload.assigneeName,
    beforePhotos: payload.problemPhotos
  });
  addTodo({
    type: 'RECTIFICATION',
    title: `${payload.boxCode} 安全抽查整改`,
    projectName: todoProjectName(payload.projectId),
    boxCode: payload.boxCode,
    installLocation: box?.installLocation,
    dueText: `${rectification.deadline} 前`,
    targetId: rectification.id,
    priority: 'danger'
  });
  if (box) {
    box.todayStatus = 'ABNORMAL';
    box.pendingRectificationCount += 1;
  }
  updateProjectCounts();
  return clone(record);
}

export function getMockRectificationDetail(id: number) {
  return clone(mockRuntime.rectifications.find((item) => item.id === id));
}

export function getMockRectifications(projectId?: number, status?: RectificationTask['status'] | '') {
  updateProjectCounts();
  return clone(mockRuntime.rectifications.filter((item) => {
    const inProject = projectId === undefined || item.projectId === projectId;
    const inStatus = !status || item.status === status;
    return inProject && inStatus;
  }));
}

export function completeMockRectification(id: number, feedback: string, photos: string[] = []) {
  const task = mockRuntime.rectifications.find((item) => item.id === id);
  if (!task) return { id, feedback };
  const fromStatus = task.status;
  task.status = 'COMPLETED';
  task.feedback = feedback;
  task.completedAt = MOCK_TIME;
  task.rectificationPhotos = photos.length ? photos : task.rectificationPhotos || ['/static/mock-photo.svg'];
  appendRectificationLog(task, 'COMPLETE', fromStatus, 'COMPLETED', feedback);
  removeTodo('RECTIFICATION', id);
  addTodo({
    type: 'RECHECK',
    title: `${task.boxCode} 整改完成待复查`,
    projectName: todoProjectName(task.projectId),
    boxCode: task.boxCode,
    installLocation: mockRuntime.electricBoxes.find((box) => box.id === task.electricBoxId)?.installLocation,
    dueText: '今天',
    targetId: id,
    priority: 'warning'
  });
  updateProjectCounts();
  return { id, feedback };
}

export function closeMockRectification(id: number) {
  const task = mockRuntime.rectifications.find((item) => item.id === id);
  if (!task) return { id };
  const fromStatus = task.status;
  task.status = 'CLOSED';
  task.reviewTime = MOCK_TIME;
  task.reviewComment = '复查通过';
  appendRectificationLog(task, 'CLOSE', fromStatus, 'CLOSED', task.reviewComment);
  removeTodo('RECHECK', id);
  removeTodo('RECTIFICATION', id);
  const box = mockRuntime.electricBoxes.find((item) => item.id === task.electricBoxId);
  if (box) {
    box.pendingRectificationCount = Math.max(0, box.pendingRectificationCount - 1);
    if (box.pendingRectificationCount === 0 && box.status === 'ACTIVE') {
      box.todayStatus = 'CHECKED';
    }
  }
  updateProjectCounts();
  return { id };
}

export function rejectMockRectification(id: number, comment = '整改不符合要求，请继续处理') {
  const task = mockRuntime.rectifications.find((item) => item.id === id);
  if (!task) return { id, comment };
  const fromStatus = task.status;
  task.status = 'REJECTED';
  task.reviewTime = MOCK_TIME;
  task.reviewComment = comment;
  task.rejectCount = (task.rejectCount || 0) + 1;
  task.recheckDeadline = plusDays(task.recheckDeadline || task.deadline, RECHECK_DAYS);
  appendRectificationLog(task, 'REJECT', fromStatus, 'REJECTED', comment);
  removeTodo('RECHECK', id);
  addTodo({
    type: 'RECTIFICATION',
    title: `${task.boxCode} 复查退回继续整改`,
    projectName: todoProjectName(task.projectId),
    boxCode: task.boxCode,
    installLocation: mockRuntime.electricBoxes.find((box) => box.id === task.electricBoxId)?.installLocation,
    dueText: `${task.deadline} 前`,
    targetId: id,
    priority: 'danger'
  });
  updateProjectCounts();
  return { id, comment };
}

export function getMockTodos() {
  updateProjectCounts();
  return clone(mockRuntime.todos.filter((item) => item.type === 'INSPECTION'));
}

export function getMockPublicElectricBoxSummary(publicCode: string): PublicElectricBoxSummary {
  const box = mockRuntime.electricBoxes.find((item) => item.publicCode === publicCode || item.boxCode === publicCode);
  if (!box) return clone(mockPublicElectricBoxSummary);
  const recentRecords = mockRuntime.inspectionRecords.filter((item) => item.electricBoxId === box.id).slice(0, 3).map(toPublicRecord);
  return {
    ...clone(mockPublicElectricBoxSummary),
    projectShortName: todoProjectName(box.projectId),
    boxCode: box.boxCode,
    boxName: box.id === 101 ? '二级电箱 1' : (box.boxType || box.boxName),
    installLocation: box.id === 101 ? '一层东侧材料通道' : box.installLocation,
    status: box.status,
    latestCheckDate: box.lastCheckDate ? `${box.lastCheckDate} 09:30` : mockPublicElectricBoxSummary.latestCheckDate,
    shouldCheckDays: 30,
    checkedDays: 28,
    abnormalCount: box.id === 101 ? 2 : (recentRecords.reduce((sum, item) => sum + item.abnormalCount, 0) || mockPublicElectricBoxSummary.abnormalCount),
    openRectificationCount: 0,
    recentRecords: clone(recentRecords.length ? recentRecords : mockPublicElectricBoxSummary.recentRecords)
  };
}

export function getMockPublicElectricBoxMonthly(publicCode: string, month?: string): PublicElectricBoxMonthly {
  const box = mockRuntime.electricBoxes.find((item) => item.publicCode === publicCode || item.boxCode === publicCode) || mockRuntime.electricBoxes[0];
  const targetMonth = month || '2026-07';
  const days = new Date(Number(targetMonth.slice(0, 4)), Number(targetMonth.slice(5, 7)), 0).getDate();
  const rows = Array.from({ length: days }, (_, index) => {
    const day = index + 1;
    const date = `${targetMonth}-${String(day).padStart(2, '0')}`;
    if (day > 11) return { date, required: true, status: 'FUTURE', appearance: '—', leakageProtector: '—', fuse: '—', protectiveZero: '—', socket220v: '—', socket380v: '—', inspectorName: '', remark: '尚未到巡检日期' };
    if (day === 4) return { date, required: false, status: 'NON_SCOPE', appearance: '非巡检范围', leakageProtector: '非巡检范围', fuse: '非巡检范围', protectiveZero: '非巡检范围', socket220v: '非巡检范围', socket380v: '非巡检范围', inspectorName: '', remark: '临时停用维护' };
    if (day === 7) return { date, required: true, status: 'MISSED', appearance: '未检', leakageProtector: '未检', fuse: '未检', protectiveZero: '未检', socket220v: '未检', socket380v: '未检', inspectorName: '', remark: '超过18:00未提交，计为漏检' };
    const abnormal = day === 9;
    return { date, required: true, status: 'COMPLETED', appearance: '正常', leakageProtector: abnormal ? '异常' : '正常', fuse: '正常', protectiveZero: '正常', socket220v: '正常', socket380v: '正常', inspectorName: day % 2 ? '张电工' : '王电工', remark: abnormal ? '漏电保护器测试无响应' : '检查正常' };
  });
  const effectiveRows = rows.filter((item) => item.required && item.date <= '2026-07-11');
  return {
    projectName: '',
    projectShortName: '',
    boxCode: box.boxCode,
    boxName: box.boxName,
    installLocation: box.installLocation,
    status: box.status,
    month: targetMonth,
    shouldCheckDays: effectiveRows.length,
    checkedDays: effectiveRows.filter((item) => item.status !== 'MISSED').length,
    missedDays: effectiveRows.filter((item) => item.status === 'MISSED').length,
    abnormalDays: effectiveRows.filter((item) => item.leakageProtector === '异常').length,
    openRectificationCount: 0,
    rows
  };
}
