import type {
  ElectricBox,
  InspectionRecord,
  Project,
  PublicElectricBoxSummary,
  RectificationTask,
  TodoItem,
  User
} from '@/types';

// 真实联调模式不再内置业务 Mock 数据。以下空导出仅兼容现有 Mock 运行时接口。
export const mockUser: User = {
  id: 0,
  username: '',
  realName: '',
  roles: [],
  projectRoles: [],
  accessibleProjectIds: []
};

export const mockProjects: Project[] = [];
export const mockElectricBoxes: ElectricBox[] = [];
export const mockInspectionRecords: InspectionRecord[] = [];
export const mockRectifications: RectificationTask[] = [];
export const mockTodos: TodoItem[] = [];

// 六项检查是业务模板，不属于演示记录。
export const checkItems = [
  { itemCode: 'APPEARANCE', itemName: '内外观' },
  { itemCode: 'LEAKAGE_PROTECTOR', itemName: '漏电保护器' },
  { itemCode: 'FUSE', itemName: '熔断' },
  { itemCode: 'PROTECTIVE_ZERO', itemName: '保护接零' },
  { itemCode: 'SOCKET_220V', itemName: '220V插座' },
  { itemCode: 'SOCKET_380V', itemName: '380V插座' }
];

export const mockPublicElectricBoxSummary: PublicElectricBoxSummary = {
  projectShortName: '',
  boxCode: '',
  boxName: '',
  installLocation: '',
  status: 'INACTIVE',
  shouldCheckDays: 0,
  checkedDays: 0,
  missedDays: 0,
  abnormalCount: 0,
  openRectificationCount: 0,
  recentRecords: []
};
