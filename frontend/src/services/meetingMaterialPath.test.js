import { test } from 'node:test';
import assert from 'node:assert/strict';
import { canonicalMaterialContentPath } from './meetingMaterialPath.js';

test('file content uses the HttpOnly cookie path through development and production proxies', () => {
  const suffix = '/site-access/material-versions/42/content?preview=true';
  for (const origin of ['', 'http://localhost:3002', 'https://site.example.com']) {
    assert.equal(canonicalMaterialContentPath(`${origin}/api${suffix}`), `${origin}/api/v1${suffix}`);
    assert.equal(canonicalMaterialContentPath(`${origin}/api/v1${suffix}`), `${origin}/api/v1${suffix}`);
  }
  assert.equal(canonicalMaterialContentPath('/custom/api/content'), '/custom/api/content');
});
