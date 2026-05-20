// 字典配置

// 项目状态
export const PROJECT_STATUS = {
  NORMAL: 'normal',
  WARNING: 'warning',
  DANGER: 'danger',
};

export const PROJECT_STATUS_TEXT = {
  normal: '正常',
  warning: '延期',
  danger: '停工',
};

// 人员状态
export const PERSONNEL_STATUS = {
  PENDING: '待教育',
  TRAINED: '已教育',
  LEFT: '已离场',
};

// 教育状态
export const TRAINING_STATUS = {
  NOT_START: '未开始',
  IN_PROGRESS: '进行中',
  COMPLETED: '已完成',
};

// 文件状态
export const FILE_STATUS = {
  UPLOADED: '已上传',
  PENDING: '待确认',
  ARCHIVED: '已归档',
};

// 文件类型
export const FILE_TYPES = {
  TRAINING: '培训资料',
  SIGN: '签字文件',
  CERT: '证书',
  PHOTO: '照片',
  OTHER: '其他',
};

// 设备状态
export const DEVICE_STATUS = {
  RUNNING: '运行中',
  STOPPED: '停机',
  ABNORMAL: '异常',
};

// 教育类型
export const EDU_TYPES = {
  SAFETY: '临时人员安全三级教育',
  RETURN: '复工教育',
  SPECIAL: '专项教育',
};

// 性别
export const GENDER = {
  MALE: '男',
  FEMALE: '女',
};

// 视频布局模式
export const VIDEO_LAYOUTS = {
  SINGLE: { id: 'single', name: '单屏', cols: 1, rows: 1 },
  QUAD: { id: 'quad', name: '四宫格', cols: 2, rows: 2 },
  EIGHT: { id: 'eight', name: '八窗口', cols: 4, rows: 2 },
  SIXTEEN: { id: 'sixteen', name: '十六窗口', cols: 4, rows: 4 },
};

// 页面ID
export const PAGE_IDS = {
  MAP_DASHBOARD: 'map_dashboard',
  OVERVIEW: 'overview',
  PERSONNEL: 'personnel',
  MONITOR: 'monitor',
};

// 导航菜单
export const NAV_ITEMS = [
  { id: PAGE_IDS.MAP_DASHBOARD, label: '地图总览' },
  { id: PAGE_IDS.OVERVIEW, label: '项目概况' },
  { id: PAGE_IDS.PERSONNEL, label: '人员与安全' },
  { id: PAGE_IDS.MONITOR, label: '设备与监控' },
];
