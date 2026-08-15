export function extractGuardVisitorToken(options: Record<string, unknown> = {}) {
  let raw = String(options.scene || options.token || options.q || '');
  try { raw = decodeURIComponent(raw); }
  catch { return ''; }
  const token = raw.startsWith('G:') ? raw.slice(2) : raw;
  return /^[A-Za-z0-9_-]{20,40}$/.test(token) ? token : '';
}
