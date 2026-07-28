import { request } from './request';

export interface RegistrationApplicationPayload {
  username: string;
  password: string;
  realName: string;
  phone?: string;
  email?: string;
  applicationReason?: string;
  desiredProjectIds?: number[];
  sourceType: 'MINI';
  phoneVerificationType: 'WECHAT' | 'MANUAL';
  wechatCode?: string;
  wechatSessionToken?: string;
  phoneCode?: string;
}

export interface RegistrationApplicationResult {
  applicationId?: number;
  applicationNo?: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
  statusQueryToken: string;
  statusToken?: string;
  queryToken?: string;
  message?: string;
}

export interface RegistrationApplicationStatus {
  applicationNo?: string;
  username?: string;
  status: RegistrationApplicationResult['status'];
  statusLabel?: string;
  reviewComment?: string;
  createTime?: string;
  reviewTime?: string;
  message?: string;
}

export function submitRegistrationApplication(payload: RegistrationApplicationPayload) {
  return request<RegistrationApplicationResult>('/registration-applications', {
    method: 'POST',
    data: {
      ...payload,
      // 同时携带旧版字段，便于前后端滚动升级。
      source: 'MINI_PROGRAM',
      reason: payload.applicationReason,
      requestedProjectId: payload.desiredProjectIds?.[0]
    },
    skipAuthRedirect: true
  }).then((result) => ({
    ...result,
    statusQueryToken: result.statusQueryToken || result.statusToken || result.queryToken || ''
  }));
}

export function queryRegistrationApplicationStatus(statusToken: string) {
  return request<RegistrationApplicationStatus>('/registration-applications/status', {
    method: 'POST',
    data: { statusToken, statusQueryToken: statusToken },
    skipAuthRedirect: true
  });
}

export function cancelRegistrationApplication(statusToken: string) {
  return request<RegistrationApplicationStatus>('/registration-applications/cancel', {
    method: 'POST',
    data: { statusToken, statusQueryToken: statusToken },
    skipAuthRedirect: true
  });
}
