import { get, put } from './api';
const unwrap = (result) => {
  if (result?.code !== 200) throw new Error(result?.message || '项目模块操作失败');
  return result.data;
};
export const getProjectModules = async (projectId) => unwrap(await get(`/projects/${projectId}/business-modules`));
export const listProjectModules = async (params) => unwrap(await get('/system/project-modules', params));
export const saveProjectModules = async (projectId, data) => unwrap(await put(`/system/project-modules/${projectId}`, data));
