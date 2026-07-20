import type { Project } from '@/types';
import { request, USE_MOCK } from './request';
import { getMockProjectDetail, getMockProjects } from '@/mock/runtime';

function normalizeStatus(value?: string): Project['status'] {
  if (value === 'normal' || value === 'warning' || value === 'danger') return value;
  if (!value) return 'normal';
  if (['stopped', 'stop', 'disabled', 'danger', 'abnormal'].includes(value)) return 'danger';
  if (value.includes('停') || value.includes('异常') || value.includes('严重')) return 'danger';
  if (value.includes('警') || value.includes('延') || value.includes('风险')) return 'warning';
  return 'normal';
}

function normalizeProject(project: Project): Project {
  const projectName = project.projectName || project.shortName || '未命名项目';
  const phase = project.phase || project.stage || '';
  const projectStatus = project.projectStatus || project.status;
  return {
    ...project,
    projectName,
    shortName: project.shortName || projectName,
    phase,
    projectStatus,
    address: project.address || '',
    manager: project.manager || '',
    contractor: project.contractor || '',
    area: project.area || '',
    period: project.period || '',
    safetyGoal: project.safetyGoal || '',
    qualityGoal: project.qualityGoal || '',
    description: project.description || '',
    province: project.province || '',
    city: project.city || '',
    district: project.district || '',
    coordinateType: project.coordinateType || '',
    status: normalizeStatus(projectStatus),
    stage: phase || '未设置',
    electricBoxTotal: Number(project.electricBoxTotal || 0),
    pendingTodoCount: Number(project.pendingTodoCount || 0),
    todayInspectionCount: Number(project.todayInspectionCount || 0),
    pendingReviewCount: Number(project.pendingReviewCount || 0),
    pendingRectificationCount: Number(project.pendingRectificationCount || 0)
  };
}

export async function getProjectList(): Promise<Project[]> {
  if (USE_MOCK) {
    return getMockProjects();
  }
  const projects = await request<Project[]>('/projects/mini-program/list');
  return projects.map(normalizeProject);
}

export async function getProjectDetail(projectId: number): Promise<Project | undefined> {
  if (USE_MOCK) {
    return getMockProjectDetail(projectId);
  }
  return normalizeProject(await request<Project>(`/projects/mini-program/${projectId}`));
}
