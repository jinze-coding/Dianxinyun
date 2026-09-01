import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const pageSource = readFileSync(new URL('./index.jsx', import.meta.url), 'utf8');

test('edge image export uses only the dedicated edge view and export permissions', () => {
  const canExportSource = pageSource.match(/const canExport = platformAdmin \|\| \([\s\S]*?\n  \);/)?.[0] || '';
  assert.match(canExportSource, /EDGE_INSPECTION_VIEW/);
  assert.match(canExportSource, /EDGE_INSPECTION_EXPORT/);
  assert.doesNotMatch(canExportSource, /inspection\.export/);
  assert.doesNotMatch(canExportSource, /SUMMARY_EXPORT/);
});
