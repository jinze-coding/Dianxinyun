import { reactive } from 'vue';
import type { Project } from '@/types';
import { getProjectList } from '@/api/project';

const CURRENT_PROJECT_KEY = 'site_platform_current_project_id';

const state = reactive<{
  projects: Project[];
  currentProjectId: number;
  loading: boolean;
  errorMessage: string;
}>({
  projects: [],
  currentProjectId: Number(uni.getStorageSync(CURRENT_PROJECT_KEY)) || 0,
  loading: false,
  errorMessage: ''
});

export function useProjectStore() {
  async function loadProjects() {
    state.loading = true;
    state.errorMessage = '';
    try {
      state.projects = await getProjectList();
      const currentExists = state.projects.some((project) => project.id === state.currentProjectId);
      if ((!state.currentProjectId || !currentExists) && state.projects.length > 0) {
        state.currentProjectId = state.projects[0].id;
        uni.setStorageSync(CURRENT_PROJECT_KEY, state.currentProjectId);
      }
    } catch (error) {
      state.projects = [];
      state.errorMessage = error instanceof Error ? error.message : '项目加载失败';
    } finally {
      state.loading = false;
    }
  }

  function setCurrentProject(projectId: number) {
    state.currentProjectId = projectId;
    uni.setStorageSync(CURRENT_PROJECT_KEY, projectId);
  }

  return {
    state,
    loadProjects,
    setCurrentProject,
    currentProject: () => state.projects.find((project) => project.id === state.currentProjectId)
  };
}
