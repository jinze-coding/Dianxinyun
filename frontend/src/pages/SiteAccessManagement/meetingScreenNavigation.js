const STORAGE_PREFIX = 'site-platform:meeting-screen-return:';
const MAX_AGE = 24 * 60 * 60 * 1000;
const validId = (value) => Number.isSafeInteger(Number(value)) && Number(value) > 0;
const dateValue = (value) => /^\d{4}-\d{2}-\d{2}$/.test(value || '') ? value : '';

function listState(value = {}) {
  return {
    periodMode: ['DAY', 'WEEK', 'MONTH', 'CUSTOM'].includes(value.periodMode) ? value.periodMode : 'DAY',
    anchorDate: dateValue(value.anchorDate),
    customStart: dateValue(value.customStart),
    customEnd: dateValue(value.customEnd),
    inviteType: ['', 'SINGLE', 'MEETING'].includes(value.inviteType) ? value.inviteType : '',
    status: ['', 'PENDING', 'SUBMITTED', 'OPEN', 'EXPIRED', 'VOIDED'].includes(value.status) ? value.status : '',
    keyword: String(value.keyword || '').slice(0, 500),
    keywordInput: String(value.keywordInput || '').slice(0, 500),
    pageNo: validId(value.pageNo) ? Number(value.pageNo) : 1,
    scrollTop: Math.max(0, Math.min(Number(value.scrollTop) || 0, 1000000)),
  };
}

// Only an opaque lookup key enters the URL. Search text and page state stay in this browser.
export function saveMeetingScreenReturn(storage, key, context, now = Date.now()) {
  if (!/^[\w-]{1,80}$/.test(key) || !validId(context.userId)
      || !validId(context.projectId) || !validId(context.invitationId)) return '';
  try {
    const expired = [];
    for (let i = 0; i < storage.length; i += 1) {
      const entryKey = storage.key(i);
      if (!entryKey?.startsWith(STORAGE_PREFIX)) continue;
      try {
        const entry = JSON.parse(storage.getItem(entryKey));
        if (!Number.isFinite(entry?.createdAt) || now - entry.createdAt > MAX_AGE) expired.push(entryKey);
      } catch { expired.push(entryKey); }
    }
    expired.forEach((entryKey) => storage.removeItem(entryKey));
    storage.setItem(STORAGE_PREFIX + key, JSON.stringify({
      userId: Number(context.userId), projectId: Number(context.projectId),
      invitationId: Number(context.invitationId), createdAt: now, filters: listState(context.filters),
    }));
    return key;
  } catch { return ''; }
}

export function readMeetingScreenReturn(storage, search, userId, projectId, now = Date.now()) {
  const params = new URLSearchParams(search);
  const key = params.get('returnContext');
  if (!/^[\w-]{1,80}$/.test(key || '') || !validId(userId)) return null;
  try {
    const value = JSON.parse(storage.getItem(STORAGE_PREFIX + key));
    if (!value || value.userId !== Number(userId) || value.invitationId !== Number(params.get('meetingScreen'))
        || !validId(value.projectId) || (projectId && value.projectId !== Number(projectId))
        || !Number.isFinite(value.createdAt) || now < value.createdAt || now - value.createdAt > MAX_AGE) return null;
    return { projectId: value.projectId, filters: listState(value.filters) };
  } catch { return null; }
}
