import type { ProjectMember } from '@/types';
import { request, USE_MOCK } from './request';

const mockMembers: ProjectMember[] = [];

export async function getProjectMembers(projectId: number): Promise<ProjectMember[]> {
  if (USE_MOCK) {
    return mockMembers.filter((member) => member.projectId === projectId || projectId <= 0);
  }
  return request<ProjectMember[]>(`/project-members?projectId=${encodeURIComponent(projectId)}`);
}
