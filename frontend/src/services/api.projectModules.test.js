import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { setImmediate } from 'node:timers/promises';
import vm from 'node:vm';
import ts from 'typescript';
import axios from 'axios';

test('project disablement cancels generic file transfers without cancelling other projects or modules', async () => {
  const source = (await readFile(new URL('./api.js', import.meta.url), 'utf8')).replaceAll('import.meta.env', '({ VITE_API_BASE_URL: "/api/v1" })');
  const code = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText;
  const exports = {}, window = new EventTarget();
  vm.runInNewContext(code, { exports, require: () => ({ default: axios }), window, FormData, AbortController, CustomEvent, console,
    localStorage: { getItem: () => null } });
  const client = exports.default, pending = [];
  client.defaults.adapter = config => new Promise((resolve, reject) => {
    pending.push({ config, resolve: () => resolve({ config, data: { code: 200 }, status: 200, headers: {} }) });
    config.signal.addEventListener('abort', () => reject(new axios.CanceledError('Module disabled', config)), { once: true });
  });
  exports.setApiProjectContext(1, 'QUALITY');
  const form = new FormData(); form.set('projectId', '1'); form.set('businessType', 'QUALITY_ISSUE_PENDING');
  const requests = [
    client.post('/files/upload', form), client.get('/files/100/download'),
    client.get('/files/101/download', { params: { projectId: 2 } }),
    client.get('/files', { params: { projectId: 1, businessType: 'PROJECT_DOCUMENT' } }),
    client.get('/files/102/download'),
  ].map(request => request.then(() => 'complete', () => 'cancelled'));
  await setImmediate();
  pending[4].resolve(); await requests[4];
  window.dispatchEvent(new CustomEvent('project-module-availability', { detail: { projectId: 1, enabledBusinessModules: ['DOCUMENT'] } }));
  assert.deepEqual(pending.map(item => item.config.signal.aborted), [true, true, false, false, false]);
  pending[2].resolve(); pending[3].resolve();
  assert.deepEqual(await Promise.all(requests), ['cancelled', 'cancelled', 'complete', 'complete', 'complete']);
});
