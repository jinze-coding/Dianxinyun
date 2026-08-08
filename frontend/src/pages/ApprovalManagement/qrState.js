export const enabledValue = (value) => value === true || value === 1 || value === '1'
  || ['ACTIVE', 'ENABLED'].includes(String(value || '').toUpperCase());

export function sameSeal(value, sealId) {
  return Number(value?.id ?? value?.sealId) === Number(sealId);
}

export function isQrEntryEnabled(code, seal) {
  const value = code?.qrEnabled ?? code?.qrStatus ?? seal?.qrEnabled ?? seal?.qrStatus;
  return enabledValue(value);
}

export function normalizeQrState(seal, entry = {}, requestedEnabled) {
  const explicitQrStatus = entry?.qrStatus ?? entry?.qrEnabled;
  const qrEnabled = explicitQrStatus === undefined
    ? (requestedEnabled === undefined ? isQrEntryEnabled(null, seal) : Boolean(requestedEnabled))
    : enabledValue(explicitQrStatus);
  const sealEnabled = enabledValue(seal?.enabled ?? seal?.status);
  const active = entry?.active === undefined ? sealEnabled && qrEnabled : enabledValue(entry.active);
  return {
    ...seal,
    ...entry,
    id: seal?.id ?? entry?.sealId,
    qrStatus: qrEnabled ? 'ENABLED' : 'DISABLED',
    qrEnabled,
    active,
  };
}

export function mergeSealQrSnapshot(seal, qrState) {
  if (!sameSeal(seal, qrState?.id ?? qrState?.sealId)) return seal;
  return {
    ...seal,
    qrStatus: qrState.qrStatus,
    qrEnabled: qrState.qrEnabled,
    qrVersion: qrState.qrVersion ?? seal.qrVersion,
  };
}
