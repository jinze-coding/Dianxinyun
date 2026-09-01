export function parseExplicitBoolean(value, fallback = false) {
  if (value === undefined || value === null || value === '') return fallback;
  return String(value).trim().toLowerCase() === 'true';
}

export function resolveCreatedInvitationType(requestedType, meetingCreationEnabled) {
  return meetingCreationEnabled && String(requestedType || '').trim().toUpperCase() === 'MEETING'
    ? 'MEETING'
    : 'SINGLE';
}

const viteEnv = import.meta.env || {};

export const SITE_ACCESS_MEETING_CREATION_ENABLED = parseExplicitBoolean(
  viteEnv.VITE_SITE_ACCESS_MEETING_CREATION_ENABLED,
  Boolean(viteEnv.DEV),
);

export const WEB_RELEASE_VARIANT = String(viteEnv.VITE_RELEASE_VARIANT || 'development').trim();

export const WEB_RELEASE_MARKER = String(
  import.meta.env?.VITE_WEB_RELEASE_MARKER || 'dianxinyun-web-development',
).trim();
