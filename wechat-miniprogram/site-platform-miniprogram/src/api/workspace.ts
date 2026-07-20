import type { WorkspaceOverview } from '@/types';
import { previewAreas } from '@/pages/design-preview/previewData';
import { request, USE_MOCK } from './request';

export async function getWorkspaceOverview(projectId: number): Promise<WorkspaceOverview> {
  if (USE_MOCK) {
    const area = previewAreas.find((item) => item.id === projectId) || previewAreas[0];
    const cameraMetric = String(area.overview.metrics[1].value).split('/');
    return {
      onsitePersonCount: Number(area.overview.metrics[0].value),
      todayEntryCount: Number(area.personnel.metrics[1].value),
      cameraTotal: Number(cameraMetric[1] || area.overview.cameras.length),
      onlineCameraCount: Number(cameraMetric[0] || 0),
      fileTotal: area.overview.documents.length,
      todayFileCount: Number(area.overview.metrics[2].value),
      deviceTotal: area.overview.devices.length,
      alarmDeviceCount: area.overview.devices.filter((item) => item.status === '异常').length,
      projectProgress: Number(String(area.overview.metrics[3].value).replace('%', '')),
      riskAlert: area.overview.alert,
      cameras: area.overview.cameras.map((item) => ({ id: item.id, name: item.name, area: item.location, online: item.online })),
      recentFiles: area.overview.documents.map((item) => ({ id: item.id, name: item.name, type: item.type, status: '已上传' })),
      devices: area.overview.devices.map((item) => ({ id: item.id, name: item.name, status: item.status, remark: item.detail }))
    };
  }
  return request<WorkspaceOverview>(`/projects/mini-program/${projectId}/workspace-overview`);
}
