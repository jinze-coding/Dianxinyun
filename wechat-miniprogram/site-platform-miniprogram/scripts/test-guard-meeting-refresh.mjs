// Exercise the real page setup and lifecycle with controlled I/O; never writes business data.
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { parse, compileScript } from '@vue/compiler-sfc';
import { transform } from 'esbuild';
import * as Vue from 'vue';
import { useVisitorPersonalInfo } from '../src/utils/visitorPersonalInfo.ts';

const flush = async () => { for (let i = 0; i < 20; i++) { await Promise.resolve(); await Vue.nextTick(); } };
const deferred = () => { let resolve; const promise = new Promise(yes => { resolve = yes; }); return { promise, resolve }; };
const renderer = Vue.createRenderer({ createElement: () => ({}), createText: () => ({}), createComment: () => ({}), insert() {}, remove() {}, setText() {}, setElementText() {}, patchProp() {}, parentNode: () => null, nextSibling: () => null });
const hooks = {};
const intervals = new Map();
let intervalId = 0;
const realInterval = globalThis.setInterval;
const realClear = globalThis.clearInterval;
globalThis.setInterval = (fn, delay) => { const id = ++intervalId; intervals.set(id, { fn, delay }); return id; };
globalThis.clearInterval = id => intervals.delete(id);
globalThis.uni = { showToast() {} };

const meeting = (token, title = token, registered = false) => ({ choiceToken: token, title, registered, visitStartTime: '2026-09-11T09:00:00', visitEndTime: '2026-09-11T23:59:00', location: '测试会议室' });
let records = [meeting('a'), meeting('b')];
let fetchMeetings = async () => records;
let fetchState = async () => state;
let issued = 0;
let meetingCalls = 0;
const state = { projectName: '测试项目', projectShortName: '项目', pageState: 'FORM', matchedPasses: [], serverTime: '2026-09-11T13:00:00' };
let createSession = async () => ({ ...state, visitorSessionToken: `synthetic-session-${++issued}` });
const api = {
  createPublicGuardVisitorSession: () => createSession(),
  refreshPublicGuardState: () => fetchState(),
  getPublicGuardMeetings: () => { meetingCalls++; return fetchMeetings(); },
  getPublicGuardVisitorProfiles: async () => [],
  submitPublicGuardVisit: async () => ({ registrationNo: 'synthetic-pass', serverTime: state.serverTime }),
  removePublicProjectProfileImages() {}
};
const imports = {
  vue: Vue,
  '@dcloudio/uni-app': Object.fromEntries(['onLoad', 'onShow', 'onHide', 'onUnload', 'onBackPress'].map(name => [name, fn => { hooks[name] = fn; }])),
  '@/utils/visitorPersonalInfo': { useVisitorPersonalInfo },
  '@/api/siteAccess': api,
  '@/utils/guardVisitorScene': { extractGuardVisitorToken: () => 'synthetic-guard-code' },
  '@/utils/wechat': { getFreshWechatCode: async () => 'synthetic-wechat-code' },
  '@/utils/navigation': { showToast() {} }
};
const source = (await readFile(new URL('../src/pages/public/guard-visitor-register.vue', import.meta.url), 'utf8'))
  .replace(/\/\/ #ifndef MP-WEIXIN[\s\S]*?\/\/ #endif/g, '');
const { descriptor } = parse(source);
const { code } = await transform(compileScript(descriptor, { id: 'guard-refresh-test' }).content, { loader: 'ts', format: 'esm' });
const body = code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g,
  (_, spec, name) => `const ${spec.replace(/\bas\b/g, ':')} = imports(${JSON.stringify(name)});`)
  .replace(/export\s*\{\s*([\w$]+)\s+as\s+default\s*\};?/, 'return $1;').replace('export default', 'return');
const component = new Function('imports', body)(name => imports[name] || {});
component.render = () => null;
const app = renderer.createApp(component);
app.mount({});
const vm = app._instance.setupState;

try {
  await hooks.onLoad({}); await flush();
  assert.equal(meetingCalls, 1);
  vm.contactName = '已编辑姓名'; vm.contactPhone = '13800000000';
  vm.companions = [{ personName: '同行甲', personCompany: '', personPhone: '' }];
  vm.meetingsChange({ detail: { value: ['a'] } });
  const timer = [...intervals.values()].find(item => item.delay === 15000);
  assert.ok(timer, 'active page has a 15-second refresh');
  records = [meeting('a'), meeting('b'), meeting('c', '新会议')];
  timer.fn(); await flush();
  assert.equal(meetingCalls, 2, 'polling updates the meeting list as well as the gate pass');
  assert.equal(vm.meetings.length, 3);
  assert.deepEqual(vm.selectedMeetings, ['a']);
  assert.equal(vm.contactName, '已编辑姓名');
  assert.equal(vm.companions[0].personName, '同行甲');

  await vm.loadMeetings();
  assert.deepEqual(vm.selectedMeetings, ['a'], 'manual refresh retains unchanged selection');
  records = [meeting('a-v2', '会议已改期'), meeting('c')];
  timer.fn(); await flush();
  assert.deepEqual(vm.selectedMeetings, [], 'changed or removed meetings require a new choice');
  assert.match(vm.meetingsNotice, /已变更/);
  vm.meetingsChange({ detail: { value: ['c'] } });
  records = [meeting('a-v2'), meeting('c', '已预约会议', true)];
  await vm.loadMeetings();
  assert.deepEqual(vm.selectedMeetings, [], 'already registered meetings are not submitted again');

  const late = deferred(); fetchMeetings = () => late.promise;
  const pending = vm.loadMeetings(); hooks.onHide(); late.resolve([meeting('late')]); await pending;
  assert.notEqual(vm.meetings[0].choiceToken, 'late', 'hidden-page responses cannot update current content');
  const before = meetingCalls; timer.fn(); await flush(); assert.equal(meetingCalls, before);
  records = [meeting('fresh')]; fetchMeetings = async () => records;
  hooks.onShow(); await flush(); assert.equal(vm.meetings[0].choiceToken, 'fresh');

  vm.meetingsChange({ detail: { value: ['fresh'] } });
  fetchMeetings = async () => { throw new Error('网络中断'); };
  await vm.loadMeetings();
  assert.deepEqual(vm.selectedMeetings, ['fresh'], 'network failures do not silently turn a meeting visit into an ordinary visit');
  assert.match(vm.validate(), /刷新会议列表/);
  fetchMeetings = async () => records; await vm.loadMeetings(); assert.equal(vm.meetingsError, '');
  assert.deepEqual(vm.selectedMeetings, ['fresh']);

  let expired = true;
  fetchMeetings = async () => { if (expired) { expired = false; throw Object.assign(new Error('会话过期'), { code: 401 }); } return [meeting('new-session-choice')]; };
  await vm.loadMeetings(); await flush();
  assert.equal(issued, 2); assert.deepEqual(vm.selectedMeetings, []);
  assert.match(vm.meetingsNotice, /重新选择/); assert.equal(vm.contactPhone, '13800000000');

  fetchMeetings = async () => { throw Object.assign(new Error('门卫登记码已轮换'), { code: 410 }); };
  await vm.loadMeetings(); assert.deepEqual(vm.meetings, []); assert.match(vm.stateError, /已轮换/);

  // Late state responses must not restore an outdated meeting list after returning to the foreground.
  const oldState = deferred(); fetchState = () => oldState.promise;
  const oldRefresh = vm.refreshState(); hooks.onHide();
  fetchState = async () => state; fetchMeetings = async () => [meeting('after-return')];
  hooks.onShow(); await flush(); oldState.resolve({ ...state, projectName: '过期响应' }); await oldRefresh;
  assert.equal(vm.projectName, '测试项目'); assert.equal(vm.meetings[0].choiceToken, 'after-return');
  assert.equal(vm.contactName, '已编辑姓名');
  // A request started before submission cannot replace the newly issued pass with an old form.
  const beforeSubmit = deferred(); fetchState = () => beforeSubmit.promise;
  const pendingBeforeSubmit = vm.refreshState();
  vm.visitorCompany = '测试单位'; vm.privacyAgreed = true;
  await vm.submit(); assert.equal(vm.pass.registrationNo, 'synthetic-pass');
  beforeSubmit.resolve(state); await pendingBeforeSubmit;
  assert.equal(vm.pass.registrationNo, 'synthetic-pass');
  hooks.onUnload(); assert.equal(intervals.size, 0);
  const sessionAfterUnload = deferred(); createSession = () => sessionAfterUnload.promise;
  const secondApp = renderer.createApp(component); secondApp.mount({});
  const loadingPage = hooks.onLoad({}); await flush(); secondApp.unmount();
  sessionAfterUnload.resolve({ ...state, visitorSessionToken: 'late-session' }); await loadingPage; await flush();
  assert.equal(intervals.size, 0, 'unloading during login must not restart polling');
  console.log('guard meetings: polling, selection, version changes, foreground, offline, expiry and stale-response protection PASS');
} finally {
  app.unmount(); globalThis.setInterval = realInterval; globalThis.clearInterval = realClear;
}
