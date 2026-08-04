import { get, post, put, del } from './api';

export function getProjectMembers(projectId) {
  return get('/project-members', { projectId });
}

export function getProjectMemberAssignmentOptions(projectId, params = {}) {
  return get('/project-members/assignment-options', { projectId, ...params });
}

export function getProjectUserOptions(projectId, keyword) {
  return get('/project-members/users', { projectId, keyword });
}

export function getAssignableProjectRoles(projectId) {
  return get('/project-members/roles', { projectId });
}

export function createProjectUser(data) {
  return post('/project-members/users', data);
}

export function saveProjectMember(data) {
  return post('/project-members', data);
}

export function updateProjectMember(projectId, userId, data) {
  return put(`/project-members/${projectId}/${userId}`, data);
}

export function removeProjectMember(projectId, userId) {
  return del(`/project-members/${projectId}/${userId}`);
}

export function updateProjectMemberStatus(projectId, userId, data) {
  return put(`/project-members/${projectId}/${userId}/status`, data);
}

export function updateProjectRoleAssignments(projectId, data) {
  return put(`/project-members/${projectId}/role-assignments`, data);
}
