// Exercise the real page setup; native APIs and business requests are isolated test doubles.
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { parse, compileScript } from '@vue/compiler-sfc';
import { transform } from 'esbuild';
import * as Vue from 'vue';

const renderer = Vue.createRenderer({
  createElement: () => ({}), createText: () => ({}), createComment: () => ({}),
  insert() {}, remove() {}, setText() {}, setElementText() {}, patchProp() {},
  parentNode: () => null, nextSibling: () => null,
});
const calls = [];
const messages = [];
const application = (patch = {}) => ({
  id: 901, projectId: 9, sealId: 3, status: 'DRAFT', files: [], items: [{ documentName: '申请文件', copies: 1 }],
  ccRecipients: [], logs: [], canSubmit: true, canApprove: true, ...patch,
});
globalThis.uni = {
  redirectTo() {}, showModal: ({ success }) => success({ confirm: true }),
};
let uploadFails = false;
const api = {
  createSealApplication: async (input) => { calls.push(['create', input]); return application(); },
  updateSealApplication: async (id, input) => { calls.push(['save', id, input]); return application(); },
  submitSealApplication: async (id) => { calls.push(['submit', id]); return application({ status: 'PENDING_APPROVAL' }); },
  uploadSealApplicationFile: async () => {
    calls.push(['upload']);
    if (uploadFails) throw new Error('文件上传失败');
    return { id: 51, fileRole: 'SOURCE' };
  },
  approveSealApplication: async (id, opinion, required) => {
    calls.push(['approve', id, opinion, required]);
    return application({ status: 'APPROVED', stampedResultRequired: required });
  },
  rejectSealApplication: async (id, opinion) => {
    calls.push(['reject', id, opinion]); return application({ status: 'REJECTED' });
  },
};
async function mountPage(name) {
  const { descriptor } = parse(await readFile(new URL(`../src/pages/seal/${name}.vue`, import.meta.url), 'utf8'));
  const { code } = await transform(compileScript(descriptor, { id: `seal-${name}-test` }).content, { loader: 'ts', format: 'esm' });
  const body = code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g,
    (_, spec, path) => `const ${spec.replace(/\bas\b/g, ':')} = imports(${JSON.stringify(path)});`)
    .replace(/export\s*\{\s*([\w$]+)\s+as\s+default\s*\};?/, 'return $1;').replace('export default', 'return');
  const imports = {
    vue: Vue, '@dcloudio/uni-app': { onLoad() {} }, '@/components/AppNavBar.vue': {},
    '@/api/seal': api, '@/api/document': {}, '@/types': {},
    '@/stores/auth': { useAuthStore: () => ({ state: { user: { id: 7 } } }) },
    '@/stores/project': { useProjectStore: () => ({ state: { projects: [] } }) },
    '@/utils/documentFile': {}, '@/utils/sealAccess': {}, '@/utils/sealScene': {},
    '@/utils/navigation': { showToast: (message) => messages.push(message) },
  };
  const component = new Function('imports', body)((path) => imports[path] || {});
  component.render = () => null;
  const app = renderer.createApp(component);
  app.mount({});
  return { state: app._instance.setupState, unmount: () => app.unmount() };
}

const apply = await mountPage('apply');
try {
  const s = apply.state;
  s.form.projectId = 9;
  s.form.departmentName = '测试项目';
  s.form.purpose = '提交纸质文件申请用印';
  s.form.items = [{ documentName: '申请文件', copies: 1 }];
  s.entry = { projectId: 9, sealId: 3, sealName: '项目章' };
  s.ccCandidatesReady = true;
  await s.save(true);
  assert.deepEqual(calls.map(([action]) => action), ['create', 'submit'], 'empty attachments must not block submission');
  assert.equal(s.saving, false);

  calls.length = 0;
  s.form.items[0].documentName = '';
  await s.save(true);
  assert.equal(calls.length, 0, 'logical file names remain required');
  s.form.items[0].documentName = '申请文件';
  s.pendingFiles = [{ key: 'pending', name: '申请文件.pdf', path: '/test/optional.pdf', size: 10 }];
  uploadFails = true;
  await s.save(true);
  assert.deepEqual(calls.map(([action]) => action), ['save', 'upload'], 'a selected file must finish uploading before submission');
  assert.equal(s.pendingFiles.length, 1, 'failed selected file remains available for retry or removal');
  assert.equal(s.saving, false);
} finally { apply.unmount(); }

calls.length = 0;
const detail = await mountPage('detail');
try {
  const s = detail.state;
  s.applicationId = 901;
  s.detail = application();
  await s.submitDraft();
  assert.deepEqual(calls, [['submit', 901]], 'the detail-page submit path also permits no attachment');

  for (const required of [false, true]) {
    s.openOpinion('APPROVE');
    assert.equal(s.stampedResultRequired, false, 'each approval starts with optional upload');
    s.stampedResultRequired = required;
    s.opinion = ' 同意用印 ';
    await s.submitOpinion();
    assert.deepEqual(calls.at(-1), ['approve', 901, '同意用印', required]);
    assert.equal(s.detail.stampedResultRequired, required);
    assert.equal(s.opinionAction, null);
  }
  s.openOpinion('REJECT');
  assert.equal(s.stampedResultRequired, false, 'a previous approval selection does not carry into rejection');
  s.opinion = '补充文件名称';
  await s.submitOpinion();
  assert.deepEqual(calls.at(-1), ['reject', 901, '补充文件名称']);
} finally { detail.unmount(); }
console.log('Seal attachment checks passed: both submit paths, document validation, upload failure, approval choices and reset.');
