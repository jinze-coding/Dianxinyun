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
  profileAction?: 'NONE' | 'CREATE' | 'UPDATE';
  profileCode?: string;
  profileName?: string;
  profileRetentionAgreed?: boolean;
  profileVersion?: number;
}

export interface PublicVisitorSession {
  visitorSessionToken: string;
  expiresInSeconds: number;
}

export interface SiteVisitorProfilePerson {
  personType: 'CONTACT' | 'COMPANION';
  personName: string;
  idCard?: string;
  maskedIdCard?: string;
  sortOrder: number;
}

export interface SiteVisitorProfile {
  id?: number;
  profileCode: string;
  profileName: string;
  visitorCompany: string;
  contactName: string;
  contactPhone?: string;
  maskedContactPhone?: string;
  visitorCount: number;
  travelMode: 'DRIVING' | 'OTHER';
  vehiclePlate?: string;
  status: 'ACTIVE' | 'DISABLED';
  version: number;
  lastUsedTime?: string;
  people?: SiteVisitorProfilePerson[];
}

export function resolvePublicSiteVisit(inviteToken: string) {
  return request<PublicSiteVisitInvitation>('/public/site-access/invitations/resolve', {
    method: 'POST',
    data: { inviteToken },
    skipAuthRedirect: true
  });
}

export function createPublicVisitorSession(inviteToken: string, wechatCode: string) {
  return request<PublicVisitorSession>('/public/site-access/visitor-sessions', {
    method: 'POST',
    data: { inviteToken, wechatCode },
    skipAuthRedirect: true
  });
}

function visitorSessionHeader(visitorSessionToken: string) {
  return { 'X-Visitor-Session': visitorSessionToken };
}

export function getPublicVisitorProfiles(visitorSessionToken: string) {
  return request<SiteVisitorProfile[]>('/public/site-access/visitor-profiles/list', {
    method: 'POST',
    data: {},
    header: visitorSessionHeader(visitorSessionToken),
    skipAuthRedirect: true
  });
}

export function getPublicVisitorProfile(visitorSessionToken: string, profileCode: string) {
  return request<SiteVisitorProfile>('/public/site-access/visitor-profiles/detail', {
    method: 'POST',
    data: { profileCode },
    header: visitorSessionHeader(visitorSessionToken),
    skipAuthRedirect: true
  });
}

export function disablePublicVisitorProfile(visitorSessionToken: string, profileCode: string) {
  return request<void>('/public/site-access/visitor-profiles/disable', {
    method: 'POST',
    data: { profileCode },
    header: visitorSessionHeader(visitorSessionToken),
    skipAuthRedirect: true
  });
}

export function submitPublicSiteVisit(payload: PublicSiteVisitSubmitPayload, visitorSessionToken?: string) {
  return request<PublicSiteVisitInvitation>('/public/site-access/invitations/submit', {
    method: 'POST',
    data: payload,
    header: visitorSessionToken ? visitorSessionHeader(visitorSessionToken) : undefined,
    skipAuthRedirect: true,
    timeout: 30000
  });
}
