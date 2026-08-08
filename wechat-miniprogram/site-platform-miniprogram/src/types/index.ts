export type RoleCode = string;

export interface ProjectRole {
  id: number;
  roleName: string;
  roleCode: string;
  projectManagerRole?: number | boolean;
}

export interface Result<T> {
  code: number;
  message: string;
  data: T;
}

export interface User {
  id: number;
  username: string;
  realName: string;
  phone?: string;
  email?: string;
  status?: number;
  roles: RoleCode[];
  projectRoles?: UserProjectRole[];
  accessibleProjectIds?: number[];
  menus?: UserMenu[];
  permissionCodes?: string[];
  projectContexts?: ProjectPermissionContext[];
  passwordLoginEnabled?: boolean | number | string;
  initialPasswordSetupRequired?: boolean;
  wechatBindingStatus?: 'BOUND' | 'ACTIVE' | 'UNBOUND' | 'DISABLED';
  wechatBound?: boolean;
}

export interface UserMenu {
  id?: number;
  code?: string;
  menuCode?: string;
  name?: string;
  menuName?: string;
  path?: string;
  routePath?: string;
  clientType?: 'WEB' | 'MINI_PROGRAM' | 'COMMON' | string;
  type?: 'DIRECTORY' | 'MENU' | 'BUTTON' | string;
  resourceType?: 'DIRECTORY' | 'MENU' | 'BUTTON' | string;
  children?: UserMenu[];
}

export interface ProjectPermissionContext {
  projectId: number;
  projectName?: string;
  projectRoles?: ProjectRole[];
  menuCodes?: string[];
  permissionCodes?: string[];
  accessStatus?: 'ACTIVE' | 'DISABLED' | string;
  statusReason?: string;
}

export interface UserProjectRole {
  projectId: number;
  projectName?: string;
  shortName?: string;
  /** 多角色并集；旧 projectRoleCode 不再作为权限判断依据。 */
  projectRoles?: ProjectRole[];
  projectRoleCode?: RoleCode;
  menuCodes?: string[];
  permissionCodes?: string[];
  accessStatus?: 'ACTIVE' | 'DISABLED' | string;
  statusReason?: string;
}

export interface ProjectMember {
  memberId?: number;
  projectId: number;
  userId: number;
  username: string;
  realName: string;
  phone?: string;
  email?: string;
  status?: number;
  projectRoles?: ProjectRole[];
  projectRoleCode?: RoleCode;
  permissionCodes?: string[];
  globalRoles?: RoleCode[];
  accessStatus?: 'ACTIVE' | 'DISABLED' | string;
  responsibleBoxCount?: number;
  pendingRectificationCount?: number;
}

export interface QualityAssignee {
  userId: number;
  username: string;
  realName?: string;
  displayName: string;
}

export interface InspectionAssignee {
  userId: number;
  username: string;
  realName?: string;
  displayName: string;
}

export interface Project {
  id: number;
  projectName: string;
  shortName: string;
  area?: string;
  period?: string;
  phase?: string;
  projectStatus?: string;
  safetyGoal?: string;
  qualityGoal?: string;
  address: string;
  manager: string;
  contractor?: string;
  description?: string;
  startDate?: string;
  endDate?: string;
  province?: string;
  city?: string;
  district?: string;
  longitude?: number | string;
  latitude?: number | string;
  coordinateType?: string;
  status: 'normal' | 'warning' | 'danger';
  stage: string;
  electricBoxTotal: number;
  pendingTodoCount: number;
  todayInspectionCount?: number;
  pendingReviewCount?: number;
  pendingRectificationCount?: number;
}

export interface ElectricBox {
  id: number;
  projectId: number;
  boxCode: string;
  boxName: string;
  installLocation: string;
  qrCode?: string;
  publicCode?: string;
  publicAccessEnabled?: boolean;
  responsibleElectricianName: string;
  safetyManagerName: string;
  boxType?: string;
  qrStatus: 'BOUND' | 'UNBOUND' | 'DISABLED' | 'REPLACED';
  status: 'ACTIVE' | 'INACTIVE' | 'REMOVED';
  lastCheckDate?: string;
  todayStatus: 'CHECKED' | 'UNCHECKED' | 'ABNORMAL';
  pendingRectificationCount: number;
  inspectionRequired?: boolean;
  scopeEffectiveDate?: string;
  scopeEndDate?: string;
}

export interface UnifiedElectricBoxScan {
  sceneCode: string;
  mode: 'INTERNAL' | 'PUBLIC_READ_ONLY' | 'UNAVAILABLE';
  reason: string;
  electricBoxId?: number;
  projectId?: number;
  publicCode: string;
  boxCode: string;
  boxName?: string;
  installLocation: string;
  status: ElectricBox['status'];
  publicAccessEnabled: boolean;
  inspectionRequired: boolean;
  authenticated: boolean;
  projectAuthorized: boolean;
  directAction?: 'START_INSPECTION' | 'VIEW_COMPLETED_RECORD' | 'VIEW_RECORDS' | 'VIEW_PUBLIC_MONTHLY' | 'UNAVAILABLE';
  todayRecordId?: number;
  allowedActions: Array<'DAILY_INSPECTION' | 'VIEW_COMPLETED_RECORD' | 'SAFETY_SPOT_CHECK' | 'VIEW_RECORDS' | 'VIEW_PUBLIC_MONTHLY'>;
}

export type CheckResult = 'NORMAL' | 'ABNORMAL' | 'NA';

export interface InspectionItemResult {
  itemCode: string;
  itemName: string;
  result: CheckResult;
  description?: string;
}

export type InspectionSource = 'ELECTRICIAN_DAILY' | 'SAFETY_SPOT_CHECK';
export type InspectionStatus = 'COMPLETED' | 'DRAFT' | 'REVIEW_PENDING' | 'REVIEW_PASSED' | 'REVIEW_REJECTED' | 'RECTIFICATION_PENDING' | 'CLOSED';
export type InspectionPeriodMode = 'MONTH' | 'DAY';

export interface InspectionReviewLog {
  id?: number;
  recordId?: number;
  projectId?: number;
  electricBoxId?: number;
  actionType: 'ASSIGN' | 'REASSIGN' | 'UNASSIGN' | 'PASS' | 'REJECT' | 'RECTIFY' | 'OVERDUE' | string;
  fromReviewerId?: number | null;
  fromReviewerName?: string | null;
  toReviewerId?: number | null;
  toReviewerName?: string | null;
  operatorId?: number | null;
  operatorName?: string | null;
  comment?: string | null;
  createTime?: string;
}

export interface InspectionRecord {
  id?: number | null;
  projectId?: number | null;
  electricBoxId?: number | null;
  boxCode: string;
  boxName?: string;
  installLocation?: string;
  checkDate: string;
  source: InspectionSource;
  problemCategory?: string;
  inspectorName: string;
  inspectedAt?: string;
  status: InspectionStatus;
  reviewStatus?: string;
  reviewComment?: string;
  reviewerName?: string | null;
  reviewTime?: string | null;
  reviewDueTime?: string | null;
  assignedReviewerId?: number | null;
  assignedReviewerName?: string | null;
  reviewOverdue?: number | boolean;
  reviewLogs?: InspectionReviewLog[];
  abnormalCount: number;
  outerPhotoCount: number;
  innerPhotoCount: number;
  problemPhotoCount?: number;
  outerPhotoFileIds?: number[];
  innerPhotoFileIds?: number[];
  problemPhotoFileIds?: number[];
  outerPhotos?: string[];
  innerPhotos?: string[];
  problemPhotos?: string[];
  remark: string;
  items: InspectionItemResult[];
}

export type RectificationStatus = 'PENDING' | 'COMPLETED' | 'CLOSED' | 'REJECTED';

export interface RectificationReviewLog {
  id?: number;
  rectificationId?: number;
  projectId?: number;
  electricBoxId?: number;
  inspectionRecordId?: number;
  actionType: 'COMPLETE' | 'CLOSE' | 'REJECT' | 'ASSIGN' | 'REMIND' | 'ESCALATE' | string;
  fromStatus?: RectificationStatus | string;
  toStatus?: RectificationStatus | string;
  operatorId?: number | null;
  operatorName?: string | null;
  comment?: string | null;
  photoFileIds?: string | null;
  createTime?: string;
}

export interface RectificationTask {
  id: number;
  projectId: number;
  electricBoxId: number;
  assigneeId?: number;
  boxCode: string;
  boxName?: string;
  orderNo?: string;
  inspectorName?: string;
  createdAt?: string;
  problemDesc: string;
  problemCategory?: string;
  requirement: string;
  assigneeName: string;
  responsiblePhone?: string;
  deadline: string;
  status: RectificationStatus;
  feedback?: string;
  completedAt?: string;
  reviewTime?: string;
  reviewComment?: string;
  rejectCount?: number;
  recheckDeadline?: string;
  escalationStatus?: 'NONE' | 'REMINDED' | 'ESCALATED' | string;
  escalationTime?: string;
  escalationNote?: string;
  beforePhotoFileIds?: number[];
  rectificationPhotoFileIds?: number[];
  beforePhotos?: string[];
  rectificationPhotos?: string[];
  reviewLogs?: RectificationReviewLog[];
  canRectify?: boolean;
  canReview?: boolean;
  canAssign?: boolean;
}

export type TodoType = 'INSPECTION' | 'REVIEW' | 'RECTIFICATION' | 'RECHECK' | 'SEAL_APPROVAL';

export interface TodoItem {
  id: number;
  type: TodoType;
  todoKey?: string;
  taskType?: string;
  taskId?: number;
  title: string;
  projectId?: number;
  projectName: string;
  boxCode: string;
  installLocation?: string;
  summary?: string;
  applicantName?: string;
  dueAt?: string;
  dueText: string;
  targetId: number;
  businessType?: string;
  actionUrl?: string;
  routeKey?: string;
  routeCode?: string;
  routeParams?: Record<string, string | number>;
  scope?: 'PENDING' | 'CC' | string;
  createdAt?: string;
  priority?: 'normal' | 'warning' | 'danger';
  reviewDueTime?: string | null;
  assignedReviewerId?: number | null;
  assignedReviewerName?: string | null;
  reviewOverdue?: number | boolean;
}

export interface TodoSummary {
  pendingCount: number;
  ccCount: number;
  unreadNotificationCount: number;
  badgeCount: number;
}

export interface UserNotification {
  id: number;
  projectId?: number;
  projectName?: string;
  businessType?: string;
  businessId?: number;
  eventCode?: string;
  title: string;
  summary?: string;
  isRead: boolean;
  readTime?: string;
  createTime?: string;
  actionUrl?: string;
  routeKey?: string;
  routeCode?: string;
  routeParams?: Record<string, string | number>;
}

export type SealApplicationStatus =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'APPROVED'
  | 'REJECTED'
  | 'WITHDRAWN';

export type SealFileRole = 'SOURCE' | 'STAMPED_RESULT';

export interface SealEntryResolution {
  scene: string;
  projectId: number;
  projectName: string;
  projectShortName?: string;
  companyName?: string;
  departmentName?: string;
  sealId?: number;
  sealName?: string;
  configured?: boolean;
  active?: boolean;
  expiresAt?: string;
  message?: string;
}

export interface SealApplicationItem {
  id?: number;
  documentName: string;
  copies: number;
}

export interface SealApplicationFile {
  id: number;
  fileRole: SealFileRole;
  itemId?: number;
  fileName: string;
  originalFileName?: string;
  fileSize?: number;
  mimeType?: string;
  fileExtension?: string;
  uploaderId?: number;
  uploaderName?: string;
  archivedDocumentId?: number;
  archivedVersionId?: number;
  createTime?: string;
  canPreview?: boolean;
  canDelete?: boolean;
}

export interface SealApplicationLog {
  id: number;
  action: string;
  actionLabel?: string;
  operatorId?: number;
  operatorName?: string;
  opinion?: string;
  description?: string;
  createTime?: string;
}

export interface SealCcCandidate {
  userId: number;
  displayName: string;
  realName?: string;
  username?: string;
  phone?: string;
  defaultSelected?: boolean;
  selected?: boolean;
}

export interface SealCcRecipient extends SealCcCandidate {
  readTime?: string;
}

export interface SealDefinition {
  id: number;
  projectId?: number;
  projectName?: string;
  sealName: string;
  sealCode?: string;
  sealType?: string;
  companyName?: string;
  status?: 'ACTIVE' | 'DISABLED' | string;
  enabled?: boolean;
}

export interface SealApplication {
  id: number;
  applicationNo?: string;
  requestKey?: string;
  sourceApplicationId?: number;
  projectId: number;
  projectName: string;
  companyName?: string;
  departmentName: string;
  sealId?: number;
  sealName: string;
  purpose: string;
  status: SealApplicationStatus;
  statusLabel?: string;
  applicantId?: number;
  applicantName?: string;
  applicantDepartmentName?: string;
  applicantPhone?: string;
  applicationDate?: string;
  submitTime?: string;
  approverId?: number;
  approverName?: string;
  approvalOpinion?: string;
  approvalTime?: string;
  createTime?: string;
  updateTime?: string;
  items?: SealApplicationItem[];
  files?: SealApplicationFile[];
  ccRecipients?: SealCcRecipient[];
  logs?: SealApplicationLog[];
  canEdit?: boolean;
  canSubmit?: boolean;
  canApprove?: boolean;
  canReject?: boolean;
  canTransfer?: boolean;
  canCancel?: boolean;
  canUploadStampedResult?: boolean;
  canArchive?: boolean;
}

export interface SealApplicationDetail extends SealApplication {
  items: SealApplicationItem[];
  files: SealApplicationFile[];
  ccRecipients: SealCcRecipient[];
  logs: SealApplicationLog[];
}

export interface PublicElectricBoxSummary {
  projectShortName: string;
  boxCode: string;
  boxName?: string;
  installLocation: string;
  status: 'ACTIVE' | 'INACTIVE' | 'REMOVED';
  rangeStartDate?: string;
  rangeEndDate?: string;
  latestCheckDate?: string;
  shouldCheckDays: number;
  checkedDays: number;
  missedDays: number;
  abnormalCount: number;
  openRectificationCount: number;
  recentRecords: PublicInspectionRecord[];
}

export interface PublicInspectionRecord {
  checkDate?: string;
  inspectedAt?: string;
  source?: InspectionSource | string;
  status?: InspectionStatus | string;
  abnormalCount: number;
}

export interface PublicInspectionMonthRow {
  date: string;
  required: boolean;
  status: string;
  appearance: string;
  leakageProtector: string;
  fuse: string;
  protectiveZero: string;
  socket220v: string;
  socket380v: string;
  inspectorName?: string;
  remark: string;
}

export interface PublicElectricBoxMonthly {
  projectName: string;
  projectShortName: string;
  boxCode: string;
  boxName?: string;
  installLocation: string;
  status: ElectricBox['status'];
  month: string;
  shouldCheckDays: number;
  checkedDays: number;
  missedDays: number;
  abnormalDays: number;
  openRectificationCount: number;
  rows: PublicInspectionMonthRow[];
}

export interface WorkspaceCamera {
  id: number;
  name: string;
  code?: string;
  area?: string;
  type?: string;
  streamUrl?: string;
  online: boolean;
}

export interface WorkspaceFile {
  id: number;
  name: string;
  type?: string;
  status?: string;
  createTime?: string;
}

export interface WorkspaceDevice {
  id: number;
  name: string;
  code?: string;
  type?: string;
  status?: string;
  lastReport?: string;
  remark?: string;
}

export interface WorkspaceOverview {
  onsitePersonCount: number;
  todayEntryCount: number;
  cameraTotal: number;
  onlineCameraCount: number;
  fileTotal: number;
  todayFileCount: number;
  deviceTotal: number;
  alarmDeviceCount: number;
  projectProgress: number;
  riskAlert: string;
  cameras: WorkspaceCamera[];
  recentFiles: WorkspaceFile[];
  devices: WorkspaceDevice[];
}

export interface PersonnelPerson {
  id: number;
  name: string;
  gender?: string;
  maskedIdcard?: string;
  maskedPhone?: string;
  team?: string;
  trade?: string;
  entryTime?: string;
  phone?: string;
  idcard?: string;
  status: 'WAIT_EDUCATION' | 'EDUCATED' | 'LEFT';
  statusLabel: string;
  remark?: string;
  certificateCount?: number;
  certificateWarningCount?: number;
}

export interface PersonnelMovement {
  id: number;
  projectId: number;
  personId: number;
  actionType: 'ENTRY' | 'EXIT';
  occurredAt: string;
  operatorName?: string;
  remark?: string;
}

export interface PersonnelCertificate {
  id: number;
  projectId: number;
  personId: number;
  certificateType: string;
  certificateNo: string;
  issueDate?: string;
  expiryDate?: string;
  fileId?: number;
  fileName?: string;
  warningLevel: 'NONE' | 'NORMAL' | 'WARNING' | 'EXPIRED';
  warningLabel: string;
}

export interface PersonnelTraining {
  id: number;
  title: string;
  type?: string;
  trainingTime?: string;
  place?: string;
  trainer?: string;
  status: string;
  statusLabel?: string;
  personCount: number;
}

export interface PersonnelSummary {
  onsiteCount: number;
  todayEntryCount: number;
  pendingEducationCount: number;
  certificateWarningCount: number;
  canManage: boolean;
  people: PersonnelPerson[];
  trainings: PersonnelTraining[];
}

export type QualityIssueStatus = 'PENDING' | 'RECHECK' | 'CLOSED' | 'VOIDED';

export interface QualityIssueLog {
  id: number;
  actionType: string;
  fromStatus?: string;
  toStatus?: string;
  operatorName?: string;
  comment?: string;
  photoFileIds?: string;
  createTime?: string;
}

export interface QualityIssue {
  id: number;
  projectId: number;
  issueNo: string;
  title: string;
  location?: string;
  description?: string;
  issuePhotoFileIds?: number[];
  severity: 'NORMAL' | 'WARNING' | 'DANGER';
  status: QualityIssueStatus;
  assigneeId?: number;
  assigneeName?: string;
  deadline?: string;
  rectificationDescription?: string;
  rectificationPhotoFileIds?: number[];
  rectifiedTime?: string;
  reviewerName?: string;
  reviewComment?: string;
  reviewTime?: string;
  reviewPhotoFileIds?: number[];
  createdByName?: string;
  createTime?: string;
  overdue: boolean;
  dueText: string;
  canRectify: boolean;
  canReview: boolean;
  logs?: QualityIssueLog[];
}

export interface QualitySummary {
  todayCheckCount: number;
  pendingCount: number;
  overdueCount: number;
  recheckCount: number;
  closedCount: number;
  closureRate: number;
  canManage: boolean;
}

export interface PageResult<T> {
  pageNo: number;
  pageSize: number;
  total: number;
  records: T[];
}

export type DocumentCategory = 'PROJECT_DATA' | 'DRAWING' | 'FORM' | 'CONSTRUCTION_RECORD' | 'MEETING' | 'OTHER';
export type DocumentStatus = 'ACTIVE' | 'ARCHIVED';

export interface DocumentFolder {
  id: number;
  projectId: number;
  parentId: number;
  folderName: string;
  sortNo?: number;
  documentCount: number;
  updateTime?: string;
}

export interface ProjectDocumentVersion {
  id: number;
  versionNo: number;
  versionLabel: string;
  fileResourceId?: number;
  fileName: string;
  mimeType?: string;
  fileExtension?: string;
  fileSize?: number;
  sha256?: string;
  changeNote?: string;
  createdBy?: number;
  createdByName?: string;
  createTime?: string;
}

export interface ProjectDocumentActivity {
  id: number;
  documentId: number;
  operationType: string;
  operationLabel: string;
  description?: string;
  operatorId?: number;
  operatorName?: string;
  createTime?: string;
}

export interface ProjectDocument {
  id: number;
  projectId: number;
  folderId: number;
  folderName: string;
  documentNo?: string;
  title: string;
  category?: DocumentCategory;
  status: DocumentStatus;
  remark?: string;
  createdBy?: number;
  createdByName?: string;
  createTime?: string;
  updateTime?: string;
  currentVersion?: ProjectDocumentVersion;
  canEdit: boolean;
  canManage: boolean;
}

export interface ProjectDocumentDetail {
  document: ProjectDocument;
  versions: ProjectDocumentVersion[];
  activities: ProjectDocumentActivity[];
}

export interface ProjectDocumentSummary {
  total: number;
  active: number;
  drawings?: number;
  forms?: number;
  archived: number;
  recentUpdates: number;
  canManage: boolean;
}
