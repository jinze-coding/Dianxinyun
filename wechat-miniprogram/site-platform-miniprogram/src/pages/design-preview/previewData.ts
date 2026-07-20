export type PreviewTone = 'blue' | 'green' | 'amber' | 'red' | 'gray';
export type PreviewRole = 'PROJECT_ADMIN' | 'SAFETY_ADMIN' | 'ELECTRICIAN';

export interface PreviewMetric {
  label: string;
  value: string | number;
  tone: PreviewTone;
}

export interface PreviewPerson {
  id: number;
  name: string;
  trade: string;
  team: string;
  status: '已教育' | '待教育' | '已离场';
  phone: string;
  certificate?: string;
}

export interface PreviewTraining {
  id: number;
  title: string;
  time: string;
  people: number;
  status: '进行中' | '已完成';
}

export interface PreviewQualityIssue {
  id: number;
  title: string;
  location: string;
  owner: string;
  dueText: string;
  status: 'PENDING' | 'OVERDUE' | 'RECHECK' | 'CLOSED';
}

export interface PreviewSafetyTask {
  id: number;
  code: string;
  title: string;
  meta: string;
  action: string;
  type: 'TODAY' | 'REVIEW' | 'RECTIFICATION' | 'RECHECK';
}

export interface PreviewArea {
  id: number;
  name: string;
  shortName: string;
  status: 'normal' | 'warning';
  stage: string;
  contractor: string;
  manager: string;
  area: string;
  period: string;
  overview: {
    metrics: PreviewMetric[];
    alert: string;
    cameras: Array<{ id: number; name: string; location: string; online: boolean }>;
    documents: Array<{ id: number; name: string; type: string; time: string }>;
    devices: Array<{ id: number; name: string; status: string; detail: string }>;
  };
  personnel: {
    metrics: PreviewMetric[];
    people: PreviewPerson[];
    trainings: PreviewTraining[];
  };
  quality: {
    metrics: PreviewMetric[];
    issues: PreviewQualityIssue[];
  };
  safety: {
    metrics: PreviewMetric[];
    tasks: PreviewSafetyTask[];
  };
}

interface AreaSeed {
  id: number;
  name: string;
  shortName: string;
  status: PreviewArea['status'];
  stage: string;
  contractor: string;
  manager: string;
  area: string;
  period: string;
  onsite: number;
  cameraOnline: number;
  cameraTotal: number;
  todayDocs: number;
  progress: number;
  todayEntry: number;
  pendingEducation: number;
  certificateWarnings: number;
  qualityChecks: number;
  qualityPending: number;
  qualityOverdue: number;
  qualityClosure: number;
  boxes: number;
  inspected: number;
  pendingReview: number;
  pendingRectification: number;
}

const names = [
  ['王小红', '电工', '安装班组'],
  ['张伟', '管工', '安装班组'],
  ['刘芳', '普工', '防水班组'],
  ['陈建国', '钢筋工', '土建一班'],
  ['李明辉', '焊工', '机电班组']
] as const;

function buildArea(seed: AreaSeed): PreviewArea {
  const prefix = seed.shortName.replace(/区域|板块/g, '');
  const people = names.map((item, index): PreviewPerson => ({
    id: seed.id * 100 + index + 1,
    name: item[0],
    trade: item[1],
    team: item[2],
    status: index < seed.pendingEducation ? '待教育' : index === 4 ? '已离场' : '已教育',
    phone: `13${8 + (index % 2)}****${1234 + seed.id * 10 + index}`,
    certificate: index === 0 ? '低压电工作业证 · 28天后到期' : index === 4 ? '焊工作业证 · 有效' : undefined
  }));

  return {
    id: seed.id,
    name: seed.name,
    shortName: seed.shortName,
    status: seed.status,
    stage: seed.stage,
    contractor: seed.contractor,
    manager: seed.manager,
    area: seed.area,
    period: seed.period,
    overview: {
      metrics: [
        { label: '在场', value: seed.onsite, tone: 'blue' },
        { label: '摄像头', value: `${seed.cameraOnline}/${seed.cameraTotal}`, tone: 'green' },
        { label: '今日资料', value: seed.todayDocs, tone: 'amber' },
        { label: '进度', value: `${seed.progress}%`, tone: 'blue' }
      ],
      alert: `${seed.cameraTotal - seed.cameraOnline}路摄像头离线 · ${seed.status === 'warning' ? '2' : '1'}台设备状态异常`,
      cameras: [
        { id: seed.id * 10 + 1, name: `${prefix}主入口`, location: '人员车辆入口', online: true },
        { id: seed.id * 10 + 2, name: `${prefix}材料通道`, location: '材料运输通道', online: true },
        { id: seed.id * 10 + 3, name: `${prefix}塔吊作业面`, location: '起重作业区', online: seed.cameraOnline >= 3 },
        { id: seed.id * 10 + 4, name: `${prefix}配电区域`, location: '临时用电区', online: seed.cameraOnline >= 4 }
      ],
      documents: [
        { id: seed.id * 10 + 1, name: `${prefix}施工日志-${seed.id + 9}日.pdf`, type: '施工日志', time: '今天 11:20' },
        { id: seed.id * 10 + 2, name: `${prefix}班前教育签字表.pdf`, type: '签字文件', time: '今天 09:10' },
        { id: seed.id * 10 + 3, name: `${prefix}质量验收记录.pdf`, type: '验收资料', time: '昨天 17:45' }
      ],
      devices: [
        { id: seed.id * 10 + 1, name: `${prefix}1号塔吊`, status: '运行中', detail: '最近上报 2分钟前' },
        { id: seed.id * 10 + 2, name: `${prefix}施工电梯`, status: seed.status === 'warning' ? '异常' : '待检修', detail: seed.status === 'warning' ? '门锁传感器异常' : '明日计划保养' }
      ]
    },
    personnel: {
      metrics: [
        { label: '在场', value: seed.onsite, tone: 'blue' },
        { label: '今日进场', value: seed.todayEntry, tone: 'green' },
        { label: '待教育', value: seed.pendingEducation, tone: 'amber' },
        { label: '证件预警', value: seed.certificateWarnings, tone: 'red' }
      ],
      people,
      trainings: [
        { id: seed.id * 10 + 1, title: `${prefix}本周新进人员三级教育`, time: '今天 14:00', people: Math.max(seed.pendingEducation, 3), status: '进行中' },
        { id: seed.id * 10 + 2, title: `${prefix}临时用电专项教育`, time: '周一 09:30', people: 18 + seed.id, status: '已完成' }
      ]
    },
    quality: {
      metrics: [
        { label: '今日检查', value: seed.qualityChecks, tone: 'blue' },
        { label: '待整改', value: seed.qualityPending, tone: 'amber' },
        { label: '已逾期', value: seed.qualityOverdue, tone: 'red' },
        { label: '闭环率', value: `${seed.qualityClosure}%`, tone: 'green' }
      ],
      issues: [
        { id: seed.id * 10 + 1, title: '防水层收口不完整', location: `${prefix}二层卫生间`, owner: '防水班组', dueText: '明天到期', status: 'PENDING' },
        { id: seed.id * 10 + 2, title: '钢筋保护层偏差', location: `${prefix}三层结构面`, owner: '土建一班', dueText: '已逾期2天', status: 'OVERDUE' },
        { id: seed.id * 10 + 3, title: '模板垂直度复查', location: `${prefix}核心筒`, owner: '木工班组', dueText: '等待复查', status: 'RECHECK' },
        { id: seed.id * 10 + 4, title: '机电套管封堵', location: `${prefix}设备层`, owner: '机电班组', dueText: '昨日关闭', status: 'CLOSED' }
      ]
    },
    safety: {
      metrics: [
        { label: '电箱', value: seed.boxes, tone: 'blue' },
        { label: '今日巡检', value: `${seed.inspected}/${seed.boxes}`, tone: 'green' },
        { label: '待复核', value: seed.pendingReview, tone: 'amber' },
        { label: '待整改', value: seed.pendingRectification, tone: 'red' }
      ],
      tasks: [
        { id: seed.id * 10 + 1, code: `${prefix}-EB-${String(seed.boxes).padStart(3, '0')}`, title: '今日尚未巡检', meta: '材料通道二级电箱', action: '立即巡检', type: 'TODAY' },
        { id: seed.id * 10 + 2, code: `${prefix}-EB-002`, title: '日检记录待确认', meta: '提交于今天 10:24', action: '去复核', type: 'REVIEW' },
        { id: seed.id * 10 + 3, code: `${prefix}-EB-003`, title: '漏电保护器异常', meta: '整改期限 明天 18:00', action: '去整改', type: 'RECTIFICATION' },
        { id: seed.id * 10 + 4, code: `${prefix}-EB-001`, title: '整改结果待复查', meta: '已提交整改照片2张', action: '去复查', type: 'RECHECK' }
      ]
    }
  };
}

export const previewAreas: PreviewArea[] = [
  buildArea({
    id: 1, name: 'A1作业区域', shortName: 'A1区域', status: 'normal', stage: '主体施工', contractor: '土建一队', manager: '张区域', area: '12000 ㎡', period: '2026.01-2026.12', onsite: 128, cameraOnline: 3, cameraTotal: 4, todayDocs: 3, progress: 70, todayEntry: 12, pendingEducation: 3, certificateWarnings: 2, qualityChecks: 8, qualityPending: 3, qualityOverdue: 1, qualityClosure: 92, boxes: 4, inspected: 3, pendingReview: 2, pendingRectification: 1
  }),
  buildArea({
    id: 2, name: 'A2作业区域', shortName: 'A2区域', status: 'normal', stage: '机电安装', contractor: '安装一队', manager: '李区域', area: '9800 ㎡', period: '2026.02-2026.11', onsite: 86, cameraOnline: 4, cameraTotal: 4, todayDocs: 2, progress: 58, todayEntry: 6, pendingEducation: 2, certificateWarnings: 1, qualityChecks: 5, qualityPending: 2, qualityOverdue: 0, qualityClosure: 95, boxes: 3, inspected: 3, pendingReview: 1, pendingRectification: 1
  }),
  buildArea({
    id: 3, name: 'B1施工板块', shortName: 'B1板块', status: 'normal', stage: '结构施工', contractor: '结构一队', manager: '王板块', area: '15000 ㎡', period: '2026.01-2027.03', onsite: 164, cameraOnline: 5, cameraTotal: 6, todayDocs: 5, progress: 46, todayEntry: 18, pendingEducation: 4, certificateWarnings: 3, qualityChecks: 11, qualityPending: 4, qualityOverdue: 1, qualityClosure: 89, boxes: 6, inspected: 4, pendingReview: 3, pendingRectification: 2
  }),
  buildArea({
    id: 4, name: 'B2施工板块', shortName: 'B2板块', status: 'warning', stage: '交叉施工', contractor: '结构二队', manager: '陈板块', area: '13200 ㎡', period: '2026.03-2027.02', onsite: 142, cameraOnline: 3, cameraTotal: 5, todayDocs: 4, progress: 38, todayEntry: 9, pendingEducation: 3, certificateWarnings: 4, qualityChecks: 9, qualityPending: 5, qualityOverdue: 2, qualityClosure: 84, boxes: 5, inspected: 3, pendingReview: 2, pendingRectification: 3
  })
];

export const roleProfiles: Record<PreviewRole, { name: string; roleLabel: string; phone: string; initials: string }> = {
  PROJECT_ADMIN: { name: '张区域', roleLabel: '项目管理员', phone: '138****1201', initials: '张' },
  SAFETY_ADMIN: { name: '张安全', roleLabel: '项目安全员', phone: '138****1234', initials: '安' },
  ELECTRICIAN: { name: '王小红', roleLabel: '负责电工', phone: '139****1258', initials: '王' }
};
