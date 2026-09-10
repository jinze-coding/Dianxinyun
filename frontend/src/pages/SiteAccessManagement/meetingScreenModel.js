export function parseMeetingScreenTarget(search) {
  const value = new URLSearchParams(search).get('meetingScreen');
  if (value === null) return null;
  return /^\d+$/.test(value) && Number.isSafeInteger(Number(value)) && Number(value) > 0 ? Number(value) : 0;
}

export function meetingScreenUrl(invitationId, returnContext = '') {
  const params = new URLSearchParams({ meetingScreen: String(invitationId) });
  if (/^[\w-]{1,80}$/.test(returnContext)) params.set('returnContext', returnContext);
  return `/?${params}`;
}

export function meetingScreenQrVisible(data, now) {
  return Boolean(data?.canShowQr && data.meetingStatus === 'OPEN' && data.qrStatus === 'ENABLED'
    && new Date(data.visitEndTime).getTime() > now);
}

export function meetingQrImage(data) {
  const source = data?.imageContent;
  return data?.codeType === 'WECHAT_MINI_PROGRAM_CODE' && typeof source === 'string'
    && /^data:image\/(?:png|jpeg|webp);base64,[A-Za-z0-9+/=\r\n]+$/.test(source) ? source : '';
}

export function nextMeetingScreenPage(page, total, pageSize = 24) {
  return page % Math.max(1, Math.ceil(total / pageSize)) + 1;
}
