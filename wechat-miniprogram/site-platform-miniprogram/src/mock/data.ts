import type {
  ElectricBox,
  InspectionRecord,
  Project,
  PublicElectricBoxSummary,
  PublicInspectionRecord,
  RectificationTask,
  TodoItem,
  User
} from '@/types';

const PHOTO = '/static/mock-photo.svg';
const RECTIFICATION_BEFORE_PHOTOS = [
  '/static/rectification-before-1.svg',
  '/static/rectification-before-2.svg',
  '/static/rectification-before-3.svg'
];

function toPublicRecord(record: InspectionRecord): PublicInspectionRecord {
  return {
    checkDate: record.checkDate,
    inspectedAt: record.inspectedAt,
    source: record.source,
    status: record.status,
    abnormalCount: record.abnormalCount
  };
}

export const mockUser: User = {
  id: 3,
  username: 'safety_admin',
  realName: '张安全',
  phone: '138****1234',
  roles: ['SAFETY_ADMIN'],
  projectRoles: [
    { projectId: 1, projectName: 'A1作业区域', shortName: 'A1区域', projectRoleCode: 'SAFETY_ADMIN' },
    { projectId: 2, projectName: 'B2施工板块', shortName: 'B2板块', projectRoleCode: 'USER' },
    { projectId: 3, projectName: 'C3作业区域', shortName: 'C3区域', projectRoleCode: 'SAFETY_ADMIN' }
  ],
  accessibleProjectIds: [1, 2, 3]
};

export const mockProjects: Project[] = [
  {
    id: 1,
    projectName: 'A1作业区域',
    shortName: 'A1区域',
    area: '12000',
    period: '2026.01-2026.12',
    phase: '主体施工',
    projectStatus: 'normal',
    safetyGoal: '零事故',
    qualityGoal: '区域样板',
    description: '项目内部A1作业区域，覆盖主入口、材料通道和临电巡检点位。',
    address: '项目现场A1作业区域',
    manager: '张区域',
    contractor: '土建一队',
    status: 'normal',
    stage: '主体施工',
    electricBoxTotal: 4,
    pendingTodoCount: 7,
    todayInspectionCount: 3,
    pendingReviewCount: 2,
    pendingRectificationCount: 1
  },
  {
    id: 2,
    projectName: 'B2施工板块',
    shortName: 'B2板块',
    area: '13200',
    period: '2026.03-2027.02',
    phase: '交叉施工',
    projectStatus: 'warning',
    safetyGoal: '零事故',
    qualityGoal: '优良',
    description: '项目内部B2施工板块，覆盖二次结构、临电设施和安全复核重点区域。',
    address: '项目现场B2施工板块',
    manager: '陈板块',
    contractor: '结构二队',
    status: 'warning',
    stage: '交叉施工',
    electricBoxTotal: 2,
    pendingTodoCount: 2,
    todayInspectionCount: 1,
    pendingReviewCount: 1,
    pendingRectificationCount: 1
  },
  {
    id: 3,
    projectName: 'C3作业区域',
    shortName: 'C3区域',
    area: '7600',
    period: '2026.06-2026.10',
    phase: '设备安装',
    projectStatus: 'normal',
    safetyGoal: '零事故',
    qualityGoal: '安装达标',
    description: '项目内部C3作业区域，覆盖设备安装、配电箱巡检和通道管理。',
    address: '项目现场C3作业区域',
    manager: '黄区域',
    contractor: '机电二队',
    status: 'normal',
    stage: '设备安装',
    electricBoxTotal: 6,
    pendingTodoCount: 5,
    todayInspectionCount: 4,
    pendingReviewCount: 0,
    pendingRectificationCount: 1
  }
];

export const mockElectricBoxes: ElectricBox[] = [
  {
    id: 101,
    projectId: 1,
    boxCode: 'EB-001',
    boxName: '二级电箱 1',
    installLocation: '一层东侧材料通道',
    boxType: '二级电箱',
    qrCode: 'EBQR-001',
    publicCode: 'PUB-001',
    responsibleElectricianName: '张电工',
    safetyManagerName: '王安全',
    qrStatus: 'BOUND',
    status: 'ACTIVE',
    lastCheckDate: '2026-07-11',
    todayStatus: 'CHECKED',
    pendingRectificationCount: 0,
    inspectionRequired: true
  },
  {
    id: 102,
    projectId: 1,
    boxCode: 'EB-002',
    boxName: '二级电箱 2',
    installLocation: '二层西侧材料通道',
    boxType: '二级电箱',
    qrCode: 'EBQR-002',
    publicCode: 'PUB-002',
    responsibleElectricianName: '张电工',
    safetyManagerName: '王安全',
    qrStatus: 'BOUND',
    status: 'ACTIVE',
    lastCheckDate: '2026-07-11',
    todayStatus: 'ABNORMAL',
    pendingRectificationCount: 1,
    inspectionRequired: true
  },
  {
    id: 103,
    projectId: 1,
    boxCode: 'EB-003',
    boxName: '临时照明电箱',
    installLocation: '地下室通道入口',
    boxType: '照明电箱',
    qrCode: 'EBQR-003',
    publicCode: 'PUB-003',
    responsibleElectricianName: '王电工',
    safetyManagerName: '王安全',
    qrStatus: 'BOUND',
    status: 'ACTIVE',
    lastCheckDate: '2026-07-10',
    todayStatus: 'UNCHECKED',
    pendingRectificationCount: 0,
    inspectionRequired: true
  },
  {
    id: 104,
    projectId: 1,
    boxCode: 'EB-004',
    boxName: '备用电箱',
    installLocation: '配电间',
    boxType: '备用电箱',
    qrCode: 'EBQR-004',
    publicCode: 'PUB-004',
    responsibleElectricianName: '王电工',
    safetyManagerName: '王安全',
    qrStatus: 'DISABLED',
    status: 'INACTIVE',
    lastCheckDate: '2026-07-08',
    todayStatus: 'UNCHECKED',
    pendingRectificationCount: 0,
    inspectionRequired: false
  },
  {
    id: 201,
    projectId: 2,
    boxCode: 'EB-LG-001',
    boxName: '园区二级电箱',
    installLocation: '3 号楼一层',
    boxType: '二级电箱',
    qrCode: 'EBQR-LG-001',
    publicCode: 'PUB-LG-001',
    responsibleElectricianName: '赵电工',
    safetyManagerName: '周安全',
    qrStatus: 'BOUND',
    status: 'ACTIVE',
    lastCheckDate: '2026-07-10',
    todayStatus: 'ABNORMAL',
    pendingRectificationCount: 1
  },
  {
    id: 202,
    projectId: 2,
    boxCode: 'EB-LG-002',
    boxName: '办公区临电箱',
    installLocation: '项目部办公区',
    boxType: '三级电箱',
    qrCode: 'EBQR-LG-002',
    publicCode: 'PUB-LG-002',
    responsibleElectricianName: '赵电工',
    safetyManagerName: '周安全',
    qrStatus: 'BOUND',
    status: 'ACTIVE',
    lastCheckDate: '2026-07-11',
    todayStatus: 'CHECKED',
    pendingRectificationCount: 0
  }
];

export const checkItems = [
  { itemCode: 'APPEARANCE', itemName: '内外观' },
  { itemCode: 'LEAKAGE_PROTECTOR', itemName: '漏电保护器' },
  { itemCode: 'FUSE', itemName: '熔断' },
  { itemCode: 'PROTECTIVE_ZERO', itemName: '保护接零' },
  { itemCode: 'SOCKET_220V', itemName: '220V插座' },
  { itemCode: 'SOCKET_380V', itemName: '380V插座' }
];

function items(abnormalCodes: string[] = []) {
  return checkItems.map((item) => ({
    ...item,
    result: abnormalCodes.includes(item.itemCode) ? 'ABNORMAL' as const : 'NORMAL' as const,
    description: abnormalCodes.includes(item.itemCode) ? '现场检查不符合要求' : ''
  }));
}

export const mockInspectionRecords: InspectionRecord[] = [
  {
    id: 5001,
    projectId: 1,
    electricBoxId: 101,
    boxCode: 'EB-001',
    boxName: '二级电箱 1',
    installLocation: '一层东侧材料通道',
    checkDate: '2026-07-11',
    source: 'ELECTRICIAN_DAILY',
    inspectorName: '张电工',
    inspectedAt: '2026-07-11 09:30',
    status: 'REVIEW_PENDING',
    reviewStatus: 'PENDING',
    reviewDueTime: '2026-07-12 09:30',
    assignedReviewerName: '张安全',
    reviewOverdue: 0,
    abnormalCount: 0,
    outerPhotoCount: 2,
    innerPhotoCount: 2,
    outerPhotos: [PHOTO, PHOTO],
    innerPhotos: [PHOTO, PHOTO],
    remark: '现场正常，箱体周边清理完成',
    items: items(),
    reviewLogs: [{
      actionType: 'ASSIGN',
      operatorName: '系统',
      toReviewerName: '张安全',
      comment: '提交巡检后自动分配安全复核',
      createTime: '2026-07-11 09:30'
    }]
  },
  {
    id: 5002,
    projectId: 1,
    electricBoxId: 102,
    boxCode: 'EB-002',
    boxName: '二级电箱 2',
    installLocation: '二层西侧材料通道',
    checkDate: '2026-07-11',
    source: 'SAFETY_SPOT_CHECK',
    inspectorName: '李申明',
    inspectedAt: '2026-07-11 10:15',
    status: 'RECTIFICATION_PENDING',
    reviewStatus: 'NOT_REQUIRED',
    reviewOverdue: 0,
    abnormalCount: 2,
    outerPhotoCount: 2,
    innerPhotoCount: 2,
    problemPhotoCount: 3,
    outerPhotos: [PHOTO, PHOTO],
    innerPhotos: [PHOTO, PHOTO],
    problemPhotos: [PHOTO, PHOTO, PHOTO],
    remark: '漏电保护器测试按钮无响应，箱门未锁',
    items: items(['LEAKAGE_PROTECTOR', 'APPEARANCE']),
    reviewLogs: [{
      actionType: 'RECTIFY',
      operatorName: '李申明',
      comment: '现场安全抽查直接生成整改任务',
      createTime: '2026-07-11 10:15'
    }]
  },
  {
    id: 5003,
    projectId: 1,
    electricBoxId: 103,
    boxCode: 'EB-003',
    boxName: '临时照明电箱',
    installLocation: '地下室通道入口',
    checkDate: '2026-07-10',
    source: 'ELECTRICIAN_DAILY',
    inspectorName: '王明电',
    inspectedAt: '2026-07-10 10:45',
    status: 'REVIEW_PENDING',
    reviewStatus: 'PENDING',
    reviewDueTime: '2026-07-11 10:45',
    assignedReviewerName: '王安全',
    reviewOverdue: 0,
    abnormalCount: 0,
    outerPhotoCount: 3,
    innerPhotoCount: 2,
    outerPhotos: [PHOTO, PHOTO, PHOTO],
    innerPhotos: [PHOTO, PHOTO],
    remark: '照明回路正常',
    items: items(),
    reviewLogs: [{
      actionType: 'ASSIGN',
      operatorName: '系统',
      toReviewerName: '王安全',
      comment: '提交巡检后自动分配安全复核',
      createTime: '2026-07-10 10:45'
    }]
  },
  {
    id: 5004,
    projectId: 1,
    electricBoxId: 101,
    boxCode: 'EB-001',
    boxName: '二级电箱 1',
    installLocation: '一层东侧材料通道',
    checkDate: '2026-07-10',
    source: 'ELECTRICIAN_DAILY',
    inspectorName: '张电工',
    inspectedAt: '2026-07-10 09:20',
    status: 'REVIEW_PASSED',
    reviewStatus: 'PASS',
    reviewerName: '张安全',
    reviewTime: '2026-07-10 11:00',
    reviewDueTime: '2026-07-11 09:20',
    assignedReviewerName: '张安全',
    reviewOverdue: 0,
    abnormalCount: 0,
    outerPhotoCount: 2,
    innerPhotoCount: 2,
    remark: '复核通过',
    items: items(),
    reviewLogs: [
      {
        actionType: 'ASSIGN',
        operatorName: '系统',
        toReviewerName: '张安全',
        comment: '提交巡检后自动分配安全复核',
        createTime: '2026-07-10 09:20'
      },
      {
        actionType: 'PASS',
        operatorName: '张安全',
        comment: '复核通过',
        createTime: '2026-07-10 11:00'
      }
    ]
  },
  {
    id: 5005,
    projectId: 1,
    electricBoxId: 102,
    boxCode: 'EB-002',
    boxName: '二级电箱 2',
    installLocation: '二层西侧材料通道',
    checkDate: '2026-07-09',
    source: 'SAFETY_SPOT_CHECK',
    inspectorName: '张安全',
    inspectedAt: '2026-07-09 14:10',
    status: 'RECTIFICATION_PENDING',
    reviewStatus: 'RECTIFY',
    reviewerName: '张安全',
    reviewTime: '2026-07-09 15:20',
    reviewDueTime: '2026-07-10 14:10',
    assignedReviewerName: '张安全',
    reviewOverdue: 0,
    abnormalCount: 1,
    outerPhotoCount: 1,
    innerPhotoCount: 2,
    problemPhotoCount: 3,
    remark: '保护接零不可靠，已转整改',
    items: items(['PROTECTIVE_ZERO']),
    reviewLogs: [
      {
        actionType: 'ASSIGN',
        operatorName: '系统',
        toReviewerName: '张安全',
        comment: '提交巡检后自动分配安全复核',
        createTime: '2026-07-09 14:10'
      },
      {
        actionType: 'RECTIFY',
        operatorName: '张安全',
        comment: '保护接零不可靠，已转整改',
        createTime: '2026-07-09 15:20'
      }
    ]
  }
];

export const mockPublicElectricBoxSummary: PublicElectricBoxSummary = {
  projectShortName: 'A1区域',
  boxCode: 'EB-001',
  boxName: '二级电箱 1',
  installLocation: '一层东侧材料通道',
  status: 'ACTIVE',
  rangeStartDate: '2026-06-12',
  rangeEndDate: '2026-07-11',
  latestCheckDate: '2026-07-11 09:30',
  shouldCheckDays: 30,
  checkedDays: 28,
  missedDays: 1,
  abnormalCount: 2,
  openRectificationCount: 1,
  recentRecords: mockInspectionRecords.slice(0, 3).map(toPublicRecord)
};

export const mockRectifications: RectificationTask[] = [
  {
    id: 7001,
    projectId: 1,
    electricBoxId: 102,
    boxCode: 'EB-002',
    boxName: '二级电箱 2',
    orderNo: 'ZG-20260711-001',
    inspectorName: '张安全',
    createdAt: '2026-07-11 10:20',
    problemDesc: '漏电保护器测试跳闸，需要更换；\n保护接零螺栓松动。',
    requirement: '3天内（2026-07-14 24:00前）',
    assigneeName: '张电工',
    responsiblePhone: '138****1234',
    deadline: '2026-07-14',
    status: 'PENDING',
    rejectCount: 0,
    beforePhotos: RECTIFICATION_BEFORE_PHOTOS,
    reviewLogs: []
  },
  {
    id: 7004,
    projectId: 1,
    electricBoxId: 103,
    boxCode: 'EB-003',
    boxName: '临时照明电箱',
    orderNo: 'ZG-20260710-004',
    inspectorName: '张安全',
    createdAt: '2026-07-10 14:20',
    problemDesc: '插座防护盖破损，雨水环境存在触电风险。',
    requirement: '更换防护盖并上传整改后照片。',
    assigneeName: '王电工',
    deadline: '2026-07-13',
    status: 'COMPLETED',
    feedback: '已更换防护盖，绝缘测试正常。',
    completedAt: '2026-07-11 11:10',
    rejectCount: 0,
    beforePhotos: [PHOTO],
    rectificationPhotos: [PHOTO, PHOTO],
    reviewLogs: [{ actionType: 'COMPLETE', fromStatus: 'PENDING', toStatus: 'COMPLETED', operatorName: '王电工', comment: '已更换防护盖，绝缘测试正常。', createTime: '2026-07-11 11:10' }]
  },
  {
    id: 7005,
    projectId: 1,
    electricBoxId: 102,
    boxCode: 'EB-002',
    boxName: '二级电箱 2',
    orderNo: 'ZG-20260708-005',
    inspectorName: '张安全',
    createdAt: '2026-07-08 15:00',
    problemDesc: '箱内线缆端头裸露。',
    requirement: '重新压接并做好绝缘防护。',
    assigneeName: '张电工',
    deadline: '2026-07-11',
    status: 'REJECTED',
    feedback: '已使用绝缘胶带临时包扎。',
    reviewTime: '2026-07-11 09:10',
    reviewComment: '临时包扎不符合要求，需要使用端子重新压接。',
    rejectCount: 1,
    beforePhotos: [PHOTO],
    rectificationPhotos: [PHOTO],
    reviewLogs: [{ actionType: 'REJECT', fromStatus: 'COMPLETED', toStatus: 'REJECTED', operatorName: '张安全', comment: '需要使用端子重新压接。', createTime: '2026-07-11 09:10' }]
  },
  {
    id: 7002,
    projectId: 2,
    electricBoxId: 201,
    boxCode: 'EB-LG-001',
    boxName: '园区二级电箱',
    orderNo: 'ZG-20260710-002',
    inspectorName: '周安全',
    createdAt: '2026-07-10 11:00',
    problemDesc: '箱门未关闭，线缆外露。',
    requirement: '整理线缆并恢复箱门闭合，复查前保持现场照片。',
    assigneeName: '赵电工',
    responsiblePhone: '139****5678',
    deadline: '2026-07-13',
    status: 'COMPLETED',
    feedback: '已整理线缆并关闭箱门，现场复测正常。',
    completedAt: '2026-07-11 08:50',
    rejectCount: 0,
    beforePhotos: [PHOTO, PHOTO],
    rectificationPhotos: [PHOTO, PHOTO],
    reviewLogs: [
      {
        actionType: 'COMPLETE',
        fromStatus: 'PENDING',
        toStatus: 'COMPLETED',
        operatorName: '赵电工',
        comment: '已整理线缆并关闭箱门，现场复测正常。',
        createTime: '2026-07-11 08:50'
      }
    ]
  },
  {
    id: 7003,
    projectId: 3,
    electricBoxId: 301,
    boxCode: 'EB-PD-003',
    boxName: '材料堆场三级电箱',
    orderNo: 'ZG-20260708-003',
    inspectorName: '宋安全',
    createdAt: '2026-07-08 15:10',
    problemDesc: '电箱周边材料堆放影响操作空间。',
    requirement: '清理周边 1 米范围堆料。',
    assigneeName: '吴电工',
    responsiblePhone: '137****2468',
    deadline: '2026-07-11',
    status: 'CLOSED',
    feedback: '周边材料已清理。',
    completedAt: '2026-07-10 16:00',
    reviewTime: '2026-07-11 09:30',
    reviewComment: '现场复查通过，关闭。',
    rejectCount: 0,
    beforePhotos: [PHOTO],
    rectificationPhotos: [PHOTO],
    reviewLogs: [
      {
        actionType: 'COMPLETE',
        fromStatus: 'PENDING',
        toStatus: 'COMPLETED',
        operatorName: '吴电工',
        comment: '周边材料已清理。',
        createTime: '2026-07-10 16:00'
      },
      {
        actionType: 'CLOSE',
        fromStatus: 'COMPLETED',
        toStatus: 'CLOSED',
        operatorName: '宋安全',
        comment: '现场复查通过，关闭。',
        createTime: '2026-07-11 09:30'
      }
    ]
  }
];

export const mockTodos: TodoItem[] = [
  {
    id: 1,
    projectId: 1,
    type: 'INSPECTION',
    title: 'EB-003 今日待巡检',
    projectName: 'A1区域',
    boxCode: 'EB-003',
    installLocation: '地下室通道入口',
    dueText: '2026-07-11',
    targetId: 103,
    priority: 'warning'
  },
  {
    id: 2,
    projectId: 1,
    type: 'INSPECTION',
    title: 'EB-002 今日待巡检',
    projectName: 'A1区域',
    boxCode: 'EB-002',
    installLocation: '二层西侧材料通道',
    dueText: '2026-07-11',
    targetId: 102,
    priority: 'warning'
  }
];
