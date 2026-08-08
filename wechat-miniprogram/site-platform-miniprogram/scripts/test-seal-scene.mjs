import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import {
  extractSealScene,
  extractSealSceneFromScanResult,
  SEAL_SCAN_TYPES
} from '../src/utils/sealScene.ts';

const token = 'abcdefghijklmnopqrstuv';
const scene = `S:${token}`;

assert.deepEqual(SEAL_SCAN_TYPES, ['qrCode', 'wxCode']);
assert.equal(extractSealScene(scene), scene);
assert.equal(extractSealScene(encodeURIComponent(scene)), scene);
assert.equal(extractSealSceneFromScanResult({
  path: `pages/seal/entry?scene=${encodeURIComponent(scene)}`,
  result: '微信小程序码的 result 不作为业务 scene'
}), scene);
assert.equal(extractSealSceneFromScanResult({
  result: `https://example.invalid/pages/seal/entry?scene=${encodeURIComponent(scene)}`
}), scene);
assert.equal(extractSealSceneFromScanResult({ path: 'pages/seal/entry?scene=invalid' }), '');

for (const file of ['src/pages/seal/entry.vue', 'src/pages/seal/list.vue']) {
  const source = await readFile(new URL(`../${file}`, import.meta.url), 'utf8');
  assert.match(source, /scanType:\s*SEAL_SCAN_TYPES/, `${file} 未使用统一用印扫码类型`);
}

console.log('seal scene and scan type contract: OK');
