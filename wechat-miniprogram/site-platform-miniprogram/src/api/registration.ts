import { request } from './request';

export interface RegistrationApplicationPayload {
  /** 新账号统一以手机号登录；保留此字段仅兼容滚动升级中的旧客户端。 */
  username?: string;
  password?: string;
  realName: string;
  phone?: string;
  email?: string;
  applicationReason?: string;
  desiredProjectIds?: number[];
  sourceType: 'MINI' | 'WEB';
  phoneVerificationType: 'WECHAT' | 'MANUAL' | 'MANUAL_REVIEW';
  registrationMode?: 'STANDARD' | 'WECHAT_QUICK';
  captchaId?: string;
  captchaCode?: string;
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
  registrationMode?: 'STANDARD' | 'WECHAT_QUICK';
  status: RegistrationApplicationResult['status'];
  statusLabel?: string;
  reviewComment?: string;
  createTime?: string;
  reviewTime?: string;
  message?: string;
}

export interface RegistrationCaptcha {
  captchaId: string;
  image: string;
  expiresInSeconds?: number;
}

export function getRegistrationCaptcha() {
  return request<RegistrationCaptcha>('/auth/captcha', { skipAuthRedirect: true });
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
