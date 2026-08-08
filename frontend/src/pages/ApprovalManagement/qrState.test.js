import test from 'node:test';
import assert from 'node:assert/strict';
import {
  isQrEntryEnabled,
  mergeSealQrSnapshot,
  normalizeQrState,
  sameSeal,
} from './qrState.js';

test('disabled QR status never falls back to the enabled seal status', () => {
  const seal = { id: 7, enabled: true, qrEnabled: true, qrStatus: 'ENABLED' };
  const disabled = normalizeQrState(seal, { sealId: 7, active: false, qrStatus: 'DISABLED', qrVersion: 2 }, false);

  assert.equal(disabled.id, 7);
  assert.equal(disabled.qrEnabled, false);
  assert.equal(disabled.qrStatus, 'DISABLED');
  assert.equal(disabled.active, false);
  assert.equal(isQrEntryEnabled(disabled, seal), false);

  const miniCodeResultWithoutStatus = normalizeQrState(disabled, {
    sealId: 7,
    scene: 'opaque-scene',
    dataUrl: 'data:image/png;base64,example',
  });
  assert.equal(miniCodeResultWithoutStatus.qrEnabled, false);
  assert.equal(miniCodeResultWithoutStatus.qrStatus, 'DISABLED');
});

test('QR flag stays distinct from the whole seal enabled status', () => {
  assert.equal(isQrEntryEnabled(null, { enabled: true, qrStatus: 'DISABLED' }), false);
  assert.equal(isQrEntryEnabled(null, { enabled: false, qrStatus: 'ENABLED' }), true);

  const disabledSeal = normalizeQrState(
    { id: 8, enabled: false, qrStatus: 'ENABLED' },
    { sealId: 8, active: false, qrStatus: 'ENABLED' },
    true,
  );
  assert.equal(disabledSeal.qrEnabled, true);
  assert.equal(disabledSeal.active, false);
});

test('seal identity guard rejects a response for another dialog', () => {
  assert.equal(sameSeal({ id: 11 }, 11), true);
  assert.equal(sameSeal({ sealId: 11 }, 11), true);
  assert.equal(sameSeal({ id: 12 }, 11), false);
  assert.equal(sameSeal(null, 11), false);
});

test('status response is persisted into the matching seal list snapshot only', () => {
  const disabled = { id: 11, qrStatus: 'DISABLED', qrEnabled: false, qrVersion: 3 };
  assert.deepEqual(
    mergeSealQrSnapshot({ id: 11, qrStatus: 'ENABLED', qrEnabled: true, qrVersion: 2 }, disabled),
    disabled,
  );
  assert.deepEqual(
    mergeSealQrSnapshot({ id: 12, qrStatus: 'ENABLED', qrEnabled: true, qrVersion: 1 }, disabled),
    { id: 12, qrStatus: 'ENABLED', qrEnabled: true, qrVersion: 1 },
  );
});
