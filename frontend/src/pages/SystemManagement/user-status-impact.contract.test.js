import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const pageSource = readFileSync(new URL('./index.jsx', import.meta.url), 'utf8');
const serviceSource = readFileSync(new URL('../../services/systemManagement.js', import.meta.url), 'utf8');

test('disabling a user previews responsibility impact before confirmed status update', () => {
  assert.match(serviceSource, /post\(`\/system\/users\/\$\{id\}\/status\/preview`, data\)/);
  assert.match(pageSource, /if \(active\) \{[\s\S]*previewSystemUserStatusImpact\(getId\(user\), \{ status: nextStatus, reason \}\)/);
  assert.match(pageSource, /responsibilityImpactLines\(impacts\)/);
  assert.match(pageSource, /if \(!window\.confirm\(confirmation\)\) return/);
  assert.match(pageSource, /payload\.confirmResponsibilityRelease = true/);
});

test('restoring a user skips responsibility preview and confirmation flag', () => {
  assert.match(pageSource, /let confirmResponsibilityRelease;[\s\S]*if \(active\) \{[\s\S]*confirmResponsibilityRelease = true;[\s\S]*\}\s*const payload = \{ status: nextStatus, reason \}/);
  assert.match(pageSource, /if \(confirmResponsibilityRelease\) payload\.confirmResponsibilityRelease = true/);
});
