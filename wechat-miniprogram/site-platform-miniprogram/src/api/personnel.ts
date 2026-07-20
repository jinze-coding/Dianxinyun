import type { PersonnelCertificate, PersonnelMovement, PersonnelSummary } from '@/types';
import { previewAreas } from '@/pages/design-preview/previewData';
import { request, USE_MOCK } from './request';

export interface PersonnelPayload {
  projectId: number;
  name: string;
  gender?: string;
  idcard?: string;
  phone?: string;
  unit?: string;
  role?: string;
  entryTime?: string;
  status?: string;
  remark?: string;
}

export interface EducationPayload {
  projectId: number;
  batchName: string;
  eduType?: string;
  trainingTime?: string;
  trainingPlace?: string;
  trainer?: string;
  personIds: number[];
  remark?: string;
}

export async function getPersonnelSummary(projectId: number): Promise<PersonnelSummary> {
  if (USE_MOCK) {
    const area = previewAreas.find((item) => item.id === projectId) || previewAreas[0];
    return {
      onsiteCount: Number(area.personnel.metrics[0].value),
      todayEntryCount: Number(area.personnel.metrics[1].value),
      pendingEducationCount: Number(area.personnel.metrics[2].value),
      certificateWarningCount: Number(area.personnel.metrics[3].value),
      canManage: true,
      people: area.personnel.people.map((item) => ({ id: item.id, name: item.name, maskedPhone: item.phone, team: item.team, trade: item.trade, status: item.status === '已教育' ? 'EDUCATED' : item.status === '已离场' ? 'LEFT' : 'WAIT_EDUCATION', statusLabel: item.status })),
      trainings: area.personnel.trainings.map((item) => ({ id: item.id, title: item.title, trainingTime: item.time, status: item.status, personCount: item.people }))
    };
  }
  return request<PersonnelSummary>(`/personnel/summary?projectId=${projectId}`);
}

export async function createPersonnel(payload: PersonnelPayload) {
  if (USE_MOCK) return payload;
  return request('/personnel', { method: 'POST', data: payload });
}

export async function updatePersonnelStatus(id: number, status: string) {
  if (USE_MOCK) return { id, status };
  return request(`/personnel/${id}`, { method: 'PUT', data: { status } });
}

export async function enterPersonnel(id: number, remark = '') {
  if (USE_MOCK) return { id, actionType: 'ENTRY' };
  return request<PersonnelMovement>(`/personnel/${id}/entry`, { method: 'POST', data: { remark } });
}

export async function exitPersonnel(id: number, remark = '') {
  if (USE_MOCK) return { id, actionType: 'EXIT' };
  return request<PersonnelMovement>(`/personnel/${id}/exit`, { method: 'POST', data: { remark } });
}

export async function getPersonnelMovements(id: number) {
  if (USE_MOCK) return [] as PersonnelMovement[];
  return request<PersonnelMovement[]>(`/personnel/${id}/movements`);
}

export async function getPersonnelCertificates(projectId: number, personId?: number) {
  if (USE_MOCK) return [] as PersonnelCertificate[];
  const person = personId ? `&personId=${personId}` : '';
  return request<PersonnelCertificate[]>(`/personnel/certificates?projectId=${projectId}${person}`);
}

export async function createSafetyEducation(payload: EducationPayload) {
  if (USE_MOCK) return payload;
  return request('/safety-education', {
    method: 'POST',
    data: {
      ...payload,
      time: payload.trainingTime,
      place: payload.trainingPlace
    }
  });
}

export async function completeSafetyEducation(id: number) {
  if (USE_MOCK) return { id, status: '已完成' };
  return request(`/safety-education/${id}/complete`, { method: 'PUT' });
}
