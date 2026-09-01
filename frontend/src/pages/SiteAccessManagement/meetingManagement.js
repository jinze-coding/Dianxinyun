const contextKey = (value) => value === null || value === undefined ? '' : String(value);

export function createSiteAccessRequestGuard() {
  let latestSequence = 0;

  return {
    begin(context) {
      latestSequence += 1;
      return {
        contextKey: contextKey(context),
        sequence: latestSequence,
      };
    },
    invalidate() {
      latestSequence += 1;
    },
    isCurrent(ticket, currentContext) {
      return Boolean(ticket)
        && ticket.sequence === latestSequence
        && ticket.contextKey === contextKey(currentContext);
    },
  };
}

const STATUS_BY_TYPE = {
  SINGLE: new Set(['', 'PENDING', 'SUBMITTED', 'EXPIRED', 'VOIDED']),
  MEETING: new Set(['', 'OPEN', 'EXPIRED', 'VOIDED']),
};

export function normalizeInvitationStatusForType(inviteType, status) {
  const normalizedType = String(inviteType || '').trim().toUpperCase();
  const normalizedStatus = String(status || '').trim().toUpperCase();
  if (!STATUS_BY_TYPE[normalizedType]) return normalizedStatus;
  return STATUS_BY_TYPE[normalizedType].has(normalizedStatus) ? normalizedStatus : '';
}

export function normalizePageAfterRemoval(pageNo, remainingTotal, pageSize) {
  const size = Math.max(1, Number(pageSize) || 1);
  const total = Math.max(0, Number(remainingTotal) || 0);
  const lastPage = Math.max(1, Math.ceil(total / size));
  return Math.min(Math.max(1, Number(pageNo) || 1), lastPage);
}

export function isMeetingInvitationOpen(invitation, now = Date.now()) {
  if (invitation?.status !== 'OPEN') return false;
  const endTime = new Date(invitation.visitEndTime).getTime();
  return Number.isFinite(endTime) && endTime > now;
}

export function meetingRequestContext(projectId, invitationId) {
  return `${contextKey(projectId)}:${contextKey(invitationId)}`;
}
