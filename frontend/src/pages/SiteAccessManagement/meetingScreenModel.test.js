import test from 'node:test';
import assert from 'node:assert/strict';
import { parseMeetingScreenTarget, meetingScreenUrl, meetingScreenQrVisible, meetingQrImage, nextMeetingScreenPage } from './meetingScreenModel.js';

test('ordinary login has no screen branch; malformed and unsafe identifiers are rejected', () => {
  assert.equal(parseMeetingScreenTarget(''), null);
  assert.equal(parseMeetingScreenTarget('?meetingScreen=42'), 42);
  for (const value of ['', '-1', '0', '12x', '9007199254740992', '<script>']) assert.equal(parseMeetingScreenTarget(`?meetingScreen=${value}`), 0);
  assert.equal(meetingScreenUrl(42), '/?meetingScreen=42');
});
test('QR disappears after permission loss, voiding, rotation, disabling or meeting end', () => {
  const now = Date.parse('2026-09-10T10:00:00+08:00');
  const data = { canShowQr: true, meetingStatus: 'OPEN', qrStatus: 'ENABLED', visitEndTime: '2026-09-10T11:00:00+08:00' };
  assert.equal(meetingScreenQrVisible(data, now), true);
  for (const override of [{canShowQr:false}, {meetingStatus:'VOIDED'}, {meetingStatus:'ENDED'}, {qrStatus:'DISABLED'}, {qrStatus:'ROTATED'}, {visitEndTime:'2026-09-10T10:00:00+08:00'}]) {
    assert.equal(meetingScreenQrVisible({...data,...override}, now), false);
  }
});
test('QR accepts actual WeChat data URLs and rejects remote or executable sources', () => {
  const code = { codeType:'WECHAT_MINI_PROGRAM_CODE', imageContent:'data:image/png;base64,aGVsbG8=' };
  assert.equal(meetingQrImage(code),code.imageContent);
  for (const source of ['https://example.org/qr.png', 'data:image/svg+xml;base64,aGVsbG8=', 'javascript:alert(1)', 'data:image/png;base64,aGVsbG8=" onerror="bad']) assert.equal(meetingQrImage({...code,imageContent:source}), '');
  assert.equal(meetingQrImage({...code,codeType:'DEVELOPMENT_SCENE'}), '');
});
test('24-person pages cycle and safely contract when attendance is revoked', () => {
  assert.equal(nextMeetingScreenPage(1, 0), 1);
  assert.equal(nextMeetingScreenPage(1, 24), 1);
  assert.equal(nextMeetingScreenPage(1, 25), 2);
  assert.equal(nextMeetingScreenPage(2, 25), 1);
  assert.equal(nextMeetingScreenPage(4, 23), 1);
});
