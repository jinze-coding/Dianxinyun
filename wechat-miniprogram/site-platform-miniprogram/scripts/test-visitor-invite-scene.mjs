import assert from 'node:assert/strict';
import { extractVisitorInviteToken } from '../src/utils/visitorInviteScene.ts';

const token = 'abcdefghijklmnopqrstuv';

assert.equal(extractVisitorInviteToken({ scene: `V:${token}` }), token);
assert.equal(extractVisitorInviteToken({ scene: `V%3A${token}` }), token);
assert.equal(extractVisitorInviteToken({ token }), token);
assert.equal(extractVisitorInviteToken({ scene: 'V:too-short' }), '');
assert.equal(extractVisitorInviteToken({ scene: `X:${token}` }), '');
assert.equal(extractVisitorInviteToken({ scene: '%E0%A4%A' }), '');
assert.equal(extractVisitorInviteToken({}), '');

console.log('visitor invite scene parsing: OK');
