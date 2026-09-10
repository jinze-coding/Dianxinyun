export function extractMeetingCheckinToken(options: Record<string, unknown> = {}) {
  let raw = String(options.scene || options.token || options.q || '');
  try {
    raw = decodeURIComponent(raw);
    const sceneMatch = raw.match(/[?&]scene=([^&#]+)/i);
    if (sceneMatch?.[1]) raw = decodeURIComponent(sceneMatch[1]);
  } catch {
    return '';
  }
  if (!raw.startsWith('MC:')) return '';
  const token = raw.slice(3);
  return /^[A-Za-z0-9_-]{20,32}$/.test(token) ? token : '';
}
