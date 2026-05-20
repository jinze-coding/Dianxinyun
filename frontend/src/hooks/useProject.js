import { useState, useCallback, useMemo } from 'react';
import { PROJECTS, PROJECT_INFO, DATA_BY_PROJECT } from '@/constants/mockData';

// 项目Hook
export function useProject() {
  const [currentProjectId, setCurrentProjectId] = useState(1);

  const currentProject = useMemo(() => {
    return PROJECTS.find(p => p.id === currentProjectId) || PROJECTS[0];
  }, [currentProjectId]);

  const currentProjectInfo = useMemo(() => {
    return PROJECT_INFO[currentProjectId] || PROJECT_INFO[1];
  }, [currentProjectId]);

  const currentProjectData = useMemo(() => {
    return DATA_BY_PROJECT[currentProjectId] || DATA_BY_PROJECT[1];
  }, [currentProjectId]);

  const changeProject = useCallback((projectId) => {
    if (PROJECTS.find(p => p.id === projectId)) {
      setCurrentProjectId(projectId);
    }
  }, []);

  return {
    projects: PROJECTS,
    currentProjectId,
    currentProject,
    currentProjectInfo,
    currentProjectData,
    changeProject,
  };
}

export default useProject;
