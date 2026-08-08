import { post } from './api';
import { requestAdministrativeDeletionConfirmation } from '../components/AdministrativeDeletionDialog';

export function previewAdministrativeDeletion(targetType, targetId) {
  return post('/system/deletions/preview', { targetType, targetId });
}

export function executeAdministrativeDeletion(impact) {
  return post('/system/deletions/execute', {
    targetType: impact.targetType,
    targetId: impact.targetId,
    confirmationToken: impact.confirmationToken,
    acknowledged: true,
  });
}

/** 共用的管理员强制删除交互。返回 true 表示删除成功，false 表示用户取消。 */
export async function confirmAdministrativeDeletion(targetType, targetId) {
  const response = await previewAdministrativeDeletion(targetType, targetId);
  if (!response || response.code !== 200) throw new Error(response?.message || '删除影响加载失败');
  const impact = response.data;
  const confirmed = await requestAdministrativeDeletionConfirmation(impact);
  if (confirmed === null) return false;
  const result = await executeAdministrativeDeletion(impact);
  if (!result || result.code !== 200) throw new Error(result?.message || '永久删除失败');
  return true;
}
