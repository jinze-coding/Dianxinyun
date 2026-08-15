import assert from 'node:assert/strict';
import { extractGuardVisitorToken } from '../src/utils/guardVisitorScene.ts';

const token = 'AbCdEfGhIjKlMnOpQrStUvWxYz1';
assert.equal(extractGuardVisitorToken({ scene: `G:${token}` }), token);
assert.equal(extractGuardVisitorToken({ scene: encodeURIComponent(`G:${token}`) }), token);
assert.equal(extractGuardVisitorToken({ token }), token);
assert.equal(extractGuardVisitorToken({ scene: `V:${token}` }), '');
assert.equal(extractGuardVisitorToken({ scene: 'G:short' }), '');
assert.equal(extractGuardVisitorToken({ scene: '%E0%A4%A' }), '');
console.log('guard visitor scene tests passed');
