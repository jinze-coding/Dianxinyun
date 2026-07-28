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

// 隔离设计预览不再内置模拟业务数据。
export const previewAreas: PreviewArea[] = [];

const emptyProfile = { name: '', roleLabel: '', phone: '', initials: '' };

export const roleProfiles: Record<PreviewRole, {
  name: string;
  roleLabel: string;
  phone: string;
  initials: string;
}> = {
  PROJECT_ADMIN: { ...emptyProfile },
  SAFETY_ADMIN: { ...emptyProfile },
  ELECTRICIAN: { ...emptyProfile }
};
