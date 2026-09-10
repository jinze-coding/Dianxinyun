import assert from 'node:assert/strict';
import { extractMeetingCheckinToken } from '../src/utils/meetingCheckinScene.ts';

const token = 'AbCdEfGhIjKlMnOpQrStUv';
assert.equal(extractMeetingCheckinToken({ scene: `MC:${token}` }), token);
assert.equal(extractMeetingCheckinToken({ scene: encodeURIComponent(`MC:${token}`) }), token);
assert.equal(extractMeetingCheckinToken({ q: `https://example.test/pages/public/meeting-check-in?scene=${encodeURIComponent(`MC:${token}`)}` }), token);
assert.equal(extractMeetingCheckinToken({ scene: `M:${token}` }), '');
assert.equal(extractMeetingCheckinToken({ scene: 'MC:short' }), '');
assert.equal(extractMeetingCheckinToken({ scene: `MC:${token}%ZZ` }), '');

console.log('meeting check-in scene tests passed');
