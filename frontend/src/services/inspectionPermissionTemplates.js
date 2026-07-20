import { get, post, put } from './api';

export function getInspectionPermissionTemplates() {
  return get('/inspection-permission-templates');
}

export function getInspectionPermissionCatalog() {
  return get('/inspection-permission-templates/catalog');
}

export function createInspectionPermissionTemplate(data) {
  return post('/inspection-permission-templates', data);
}

export function updateInspectionPermissionTemplate(id, data) {
  return put(`/inspection-permission-templates/${id}`, data);
}

export function updateInspectionPermissionTemplateStatus(id, enabled) {
  return post(`/inspection-permission-templates/${id}/status`, { enabled });
}
