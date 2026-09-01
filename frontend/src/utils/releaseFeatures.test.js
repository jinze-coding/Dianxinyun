import test from 'node:test';
import assert from 'node:assert/strict';
import { parseExplicitBoolean, resolveCreatedInvitationType } from './releaseFeatures.js';

test('release feature flags require an explicit true value', () => {
  assert.equal(parseExplicitBoolean('true'), true);
  assert.equal(parseExplicitBoolean(' TRUE '), true);
  assert.equal(parseExplicitBoolean('false'), false);
  assert.equal(parseExplicitBoolean('1'), false);
  assert.equal(parseExplicitBoolean(undefined), false);
  assert.equal(parseExplicitBoolean(undefined, true), true);
});

test('transition releases force new invitations to SINGLE', () => {
  assert.equal(resolveCreatedInvitationType('MEETING', false), 'SINGLE');
  assert.equal(resolveCreatedInvitationType('SINGLE', false), 'SINGLE');
  assert.equal(resolveCreatedInvitationType('MEETING', true), 'MEETING');
  assert.equal(resolveCreatedInvitationType('unexpected', true), 'SINGLE');
});
