import test from 'node:test';
import assert from 'node:assert/strict';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import config from './vite.config.js';

test('Vite alias uses a native filesystem path in Unicode directories', () => {
  const expected = path.join(path.dirname(fileURLToPath(import.meta.url)), 'src');
  assert.equal(config.resolve.alias['@'], expected);
  assert.ok(!config.resolve.alias['@'].includes('%'));
});
