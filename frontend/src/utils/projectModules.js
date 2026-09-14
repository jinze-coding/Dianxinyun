import { PAGE_IDS } from '../constants/dicts.js';

export const PROJECT_MODULES = [
  { code: 'SITE_ACCESS', label: '场内管理' },
  { code: 'DOCUMENT', label: '资料管理' },
  { code: 'INSPECTION', label: '巡检管理' },
  { code: 'QUALITY', label: '质量周检' },
  { code: 'SAFETY_COMMITTEE', label: '安委会巡检' },
];
export const PAGE_MODULES = {
  [PAGE_IDS.SITE_ACCESS]: 'SITE_ACCESS',
  [PAGE_IDS.DOCUMENT_MANAGEMENT]: 'DOCUMENT',
  [PAGE_IDS.ELECTRIC_INSPECTION]: 'INSPECTION',
  [PAGE_IDS.QUALITY_MANAGEMENT]: 'QUALITY',
  [PAGE_IDS.SAFETY_COMMITTEE]: 'SAFETY_COMMITTEE',
};
export function permissionModule(code) {
  const value = String(code || '').toUpperCase();
  if (value.startsWith('SITE_ACCESS.')) return 'SITE_ACCESS';
  if (/^(DOCUMENT\.|SEAL\.)/.test(value)) return 'DOCUMENT';
  if (/^(INSPECTION\.|BOX_|INSPECTION_|EDGE_INSPECTION_|CUSTOM_INSPECTION_|SUMMARY_|RECTIFICATION_)/.test(value)) return 'INSPECTION';
  if (value.startsWith('QUALITY.')) return 'QUALITY';
  if (value.startsWith('SAFETY_COMMITTEE.')) return 'SAFETY_COMMITTEE';
  return null;
}
export function projectContext(user, projectId) {
  return (user?.projectContexts || user?.projectRoles || []).find((item) => Number(item.projectId) === Number(projectId));
}
export function isProjectModuleEnabled(user, projectId, moduleCode) {
  if (!moduleCode) return true;
  const context = projectContext(user, projectId);
  // Older response shapes remain readable; an explicit empty array must never fall back.
  return !Array.isArray(context?.enabledBusinessModules) || context.enabledBusinessModules.includes(moduleCode);
}
