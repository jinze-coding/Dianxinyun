import type { TodoItem } from '@/types';

export type WorkbenchActionTarget = 'navigate' | 'switchTab';
export type WorkbenchActionTone = 'green' | 'blue' | 'amber' | 'red' | 'purple' | 'slate';

export interface WorkbenchAction {
  title: string;
  icon: string;
  tone: WorkbenchActionTone;
  url: string;
  targetType: WorkbenchActionTarget;
  appendProjectId?: boolean;
  todoFilter?: TodoItem['type'];
}

export const workbenchActions: WorkbenchAction[] = [
  {
    title: '电箱台账',
    icon: 'ledger',
    tone: 'green',
    url: '/pages/electric-box/index',
    targetType: 'navigate',
    appendProjectId: true
  },
  {
    title: '检查记录',
    icon: 'ledger',
    tone: 'blue',
    url: '/pages/inspection/records',
    targetType: 'navigate',
    appendProjectId: true
  },
  {
    title: '安全复核',
    icon: 'review',
    tone: 'amber',
    url: '/pages/inspection/review?from=project',
    targetType: 'navigate',
    appendProjectId: true
  },
  {
    title: '项目巡检汇总',
    icon: 'summary',
    tone: 'purple',
    url: '/pages/summary/index',
    targetType: 'navigate',
    appendProjectId: true
  },
  {
    title: '整改闭环',
    icon: 'rectify',
    tone: 'red',
    url: '/pages/rectification/index',
    targetType: 'navigate',
    appendProjectId: true
  },
  {
    title: '我的待办',
    icon: 'todo',
    tone: 'slate',
    url: '/pages/todo/index',
    targetType: 'switchTab',
    todoFilter: 'INSPECTION'
  }
];
