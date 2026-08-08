import { request } from './request';

export type SiteVisitStatus = 'PENDING' | 'SUBMITTED' | 'EXPIRED' | 'VOIDED';

export interface PublicSiteVisitInvitation {
  inviteNo: string;
  status: SiteVisitStatus;
  projectName: string;
  projectShortName?: string;
  visitStartTime: string;
  visitEndTime: string;
  purpose: string;
  visitLocation: string;
  hostName: string;
  hostPhone?: string;
}

export interface SiteVisitCompanionInput {
  personName: string;
  idCard: string;
}

export interface PublicSiteVisitSubmitPayload {
  inviteToken: string;
  visitorCompany: string;
  contactName: string;
  contactPhone: string;
  contactIdCard: string;
  companions: SiteVisitCompanionInput[];
  travelMode: 'DRIVING' | 'OTHER';
  vehiclePlate?: string;
  visitorRemark?: string;
  privacyAgreed: boolean;
}

export function resolvePublicSiteVisit(inviteToken: string) {
  return request<PublicSiteVisitInvitation>('/public/site-access/invitations/resolve', {
    method: 'POST',
    data: { inviteToken },
    skipAuthRedirect: true
  });
}

export function submitPublicSiteVisit(payload: PublicSiteVisitSubmitPayload) {
  return request<PublicSiteVisitInvitation>('/public/site-access/invitations/submit', {
    method: 'POST',
    data: payload,
    skipAuthRedirect: true,
    timeout: 30000
  });
}
