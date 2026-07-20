import type { ProjectMember } from '@/types';
import { request, USE_MOCK } from './request';

const mockMembers: ProjectMember[] = [
  {
    memberId: 1,
    projectId: 1,
    userId: 2,
    username: 'manager',
    realName: '项目经理',
    phone: '13800138001',
    status: 1,
    projectRoleCode: 'PROJECT_ADMIN',
    permissionTemplateName: '项目管理员',
    responsibleBoxCount: 1,
    pendingRectificationCount: 0
  },
  {
    memberId: 2,
    projectId: 1,
    userId: 3,
    username: 'electrician_121440',
    realName: '测试电工',
    phone: '13900000001',
    status: 1,
    projectRoleCode: 'USER',
    permissionTemplateName: '项目成员/负责电工',
    responsibleBoxCount: 1,
    pendingRectificationCount: 0
  }
];

export async function getProjectMembers(projectId: number): Promise<ProjectMember[]> {
  if (USE_MOCK) {
    return mockMembers.filter((member) => member.projectId === projectId || projectId <= 0);
  }
  return request<ProjectMember[]>(`/project-members?projectId=${encodeURIComponent(projectId)}`);
}
