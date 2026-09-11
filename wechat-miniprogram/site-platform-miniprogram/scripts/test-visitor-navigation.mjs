// Run the real SFC setup with Vue and controllable native I/O, without writing business data.
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { parse, compileScript } from '@vue/compiler-sfc';
import { transform } from 'esbuild';
import * as Vue from 'vue';
import { useVisitorPersonalInfo } from '../src/utils/visitorPersonalInfo.ts';
const renderer = Vue.createRenderer({ createElement: () => ({}), createText: () => ({}), createComment: () => ({}), insert() {}, remove() {}, setText() {}, setElementText() {}, patchProp() {}, parentNode: () => null, nextSibling: () => null });
const flush = async () => { for (let i = 0; i < 8; i++) { await Promise.resolve(); await Vue.nextTick(); } };
const defer = () => { let resolve; const promise = new Promise((yes) => { resolve = yes; }); return { promise, resolve }; };
async function component(path, imports) {
  const { descriptor } = parse(await readFile(new URL(path, import.meta.url), 'utf8'));
  const { code } = await transform(compileScript(descriptor, { id: 'lifecycle-test' }).content, { loader: 'ts', format: 'esm' });
  const body = code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g, (_, spec, name) => `const ${spec.replace(/\bas\b/g, ':')} = imports(${JSON.stringify(name)});`).replace(/export\s*\{\s*([\w$]+)\s+as\s+default\s*\};?/, 'return $1;').replace('export default', 'return');
  const c = new Function('imports', body)((name) => name === 'vue' ? Vue : imports[name] || {});
  c.render = () => null; return c;
}
function mount(c, initial = {}) {
  const props = Vue.reactive(initial); const app = renderer.createApp({ setup: () => () => Vue.h(c, props) }); app.mount({});
  return { props, vm: app._instance.subTree.component.setupState, unmount: () => app.unmount() };
}
const hooks = {}; const io = { removed: [], scrolls: [], routes: 0, back: 0 };
const timers = new Map(); let timerId = 0;
const originalSet = globalThis.setInterval, originalClear = globalThis.clearInterval;
globalThis.setInterval = (fn, delay) => { timers.set(++timerId, { fn, delay }); return timerId; };
globalThis.clearInterval = id => timers.delete(id);
globalThis.uni = { hideKeyboard() {}, pageScrollTo: ({ scrollTop }) => io.scrolls.push(scrollTop), navigateBack: () => io.back++, exitMiniProgram: () => io.back++ };
globalThis.getCurrentPages = () => [{}];
const fixture = status => ({ inviteType: 'SINGLE', status, projectName: '测试项目', visitEndTime: new Date(Date.now() + 3600000).toISOString(), serverTime: new Date().toISOString(), projectLocation: { routeImageAvailable: true } });
let current = fixture('PENDING'); let resolveInvite = async () => current;
let download = async () => '/tmp/synthetic-route.png';
class ApiRequestError extends Error { constructor(statusCode) { super('访问已关闭'); this.statusCode = statusCode; } }
const page = await component('../src/pages/public/visitor-invite.vue', {
  '@dcloudio/uni-app': Object.fromEntries(['onLoad', 'onShow', 'onHide', 'onUnload', 'onPageScroll', 'onBackPress'].map(name => [name, fn => { hooks[name] = fn; }])),
  '@/utils/visitorPersonalInfo': { useVisitorPersonalInfo }, '@/utils/navigation': { showToast() {} },
  '@/utils/wechat': { getFreshWechatCode: async () => 'synthetic-code' },
  '@/utils/visitorProfileFlow': { isVisitorSessionAuthorizationError: () => false }, '@/api/request': { ApiRequestError },
  '@/api/siteAccess': {
    resolvePublicSiteVisit: () => resolveInvite(),
    createPublicVisitorSession: async () => ({ visitorSessionToken: 'synthetic-session', personalInfo: { available: false } }),
    downloadPublicProjectRouteImage: () => { io.routes++; return download(); },
    removePublicProjectRouteImage: path => { if (path) io.removed.push(path); }, removePublicProjectProfileImages() {}
  }
});
const pageApp = mount(page);
try {
  const s = pageApp.vm; s.token = 'synthetic-scene'; await s.load(); await flush();
  assert.equal(io.routes, 0, 'initial page does not download route image'); assert.equal(s.navigationVisible, false); assert.ok(s.canShowNavigation);
  s.contactName = '未提交姓名'; s.visitorCompany = '本次单位'; s.visitorRemark = '保留备注'; s.companions = [{ personName: '同行', personCompany: '', personPhone: '' }];
  hooks.onPageScroll({ scrollTop: 360 }); s.openNavigation(); await flush();
  assert.equal(io.routes, 1); assert.ok(s.navigationVerified);
  s.rememberNavigationScroll({ detail: { scrollTop: 280 } }); hooks.onHide(); await flush();
  assert.equal(s.navigationVerified, false); assert.ok(!io.removed.includes('/tmp/synthetic-route.png'), 'native viewer keeps its image until return');
  s.rememberNavigationScroll({ detail: { scrollTop: 0 } }); hooks.onShow(); await flush();
  s.rememberNavigationScroll({ detail: { scrollTop: 120 } }); await s.restoreNavigationScroll(); assert.equal(s.navigationScrollTarget, 280);
  assert.ok(s.navigationVerified); await s.closeNavigation(); await flush();
  assert.equal(io.scrolls.at(-1), 360); assert.ok(io.removed.includes('/tmp/synthetic-route.png'));
  assert.equal(s.contactName, '未提交姓名'); assert.equal(s.visitorRemark, '保留备注'); assert.equal(s.companions[0].personName, '同行');
  let gate = defer(); download = () => gate.promise; s.openNavigation(); await flush(); await s.closeNavigation(); gate.resolve('/tmp/late-route.png'); await flush();
  assert.ok(io.removed.includes('/tmp/late-route.png')); assert.equal(s.navigationVisible, false);
  gate = defer(); resolveInvite = () => gate.promise; s.openNavigation(); await flush(); await s.closeNavigation(); gate.resolve(fixture('SUBMITTED')); await flush();
  assert.equal(s.invitation.status, 'PENDING', 'closed dialog ignores late status');
  resolveInvite = async () => { throw new Error('断网'); }; s.openNavigation(); await flush(); assert.equal(s.navigationVerified, false); assert.equal(s.navigationError, '断网');
  resolveInvite = async () => current; download = async () => { throw new Error('图片失败'); }; await s.refreshNavigation(true); await flush(); assert.ok(s.navigationVerified); assert.match(s.projectRouteImageError, /不影响地图导航/);
  download = async () => '/tmp/synthetic-route.png'; await s.closeNavigation(); s.openNavigation(); await flush();
  assert.equal(hooks.onBackPress(), true); await flush(); assert.equal(s.navigationVisible, false); assert.equal(io.back, 0);
  s.openNavigation(); await flush(); current = fixture('VOIDED');
  for (const timer of [...timers.values()]) if (timer.delay === 30000) timer.fn();
  await flush(); assert.equal(s.navigationVisible, false); assert.equal(s.canShowNavigation, false);
  current = fixture('SUBMITTED'); await s.load(); await flush(); assert.ok(s.canShowNavigation); assert.equal(s.navigationVisible, false);
  s.openNavigation(); await flush(); assert.ok(s.navigationVerified); s.currentTime = Date.now() + 7200000; await flush(); assert.equal(s.navigationVisible, false); assert.equal(s.canShowNavigation, false);
  current = fixture('PENDING'); await s.load(); s.openNavigation(); await flush(); resolveInvite = async () => { throw new ApiRequestError(403); }; await s.refreshNavigation(); await flush(); assert.equal(s.canShowNavigation, false); assert.equal(s.navigationVisible, false);
  resolveInvite = async () => ({ ...fixture('PENDING'), projectLocation: undefined }); await s.load(); await flush(); assert.equal(s.canShowNavigation, false);
  resolveInvite = async () => ({ ...fixture('OPEN'), inviteType: 'MEETING', purpose: '不应显示的会议' }); await s.load(); await flush(); assert.equal(s.invitation, undefined); assert.equal(s.canShowNavigation, false);
  current = fixture('PENDING'); resolveInvite = async () => current; await s.load(); gate = defer(); download = () => gate.promise; s.openNavigation(); await flush(); hooks.onUnload(); gate.resolve('/tmp/unloaded-route.png'); await flush(); assert.ok(io.removed.includes('/tmp/unloaded-route.png')); assert.equal(s.navigationVisible, false); assert.equal(timers.size, 0);
} finally { pageApp.unmount(); globalThis.setInterval = originalSet; globalThis.clearInterval = originalClear; }
console.log('single navigation: lazy load, SINGLE-only, pending/pass, draft/scroll retention, native return, retry, late responses, expiry/void/denial and cleanup passed');
