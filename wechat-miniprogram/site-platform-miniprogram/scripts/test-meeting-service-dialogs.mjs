// Run the real SFC setup with Vue and controllable native I/O, without writing business data.
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { parse, compileScript } from '@vue/compiler-sfc';
import { transform } from 'esbuild';
import * as Vue from 'vue';
import { createMeetingInviteRefreshCoordinator } from '../src/utils/meetingInviteRefresh.ts';
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
function platform() {
  const hooks = { show: [], hide: [], scroll: [] }; const io = { downloads: [], texts: [], documents: [], deleted: [], audioDestroyed: 0, scrolls: [] };
  globalThis.uni = {
    hideKeyboard() {}, showToast() {}, pageScrollTo: ({ scrollTop }) => io.scrolls.push(scrollTop),
    getFileSystemManager: () => ({ unlink: ({ filePath }) => io.deleted.push(filePath) }),
    downloadFile(options) { const task = { options, aborted: false, abort() { this.aborted = true; options.fail?.(); options.complete?.(); }, onProgressUpdate() {} }; io.downloads.push(task); return task; },
    request(options) { const task = { options, abort() {} }; io.texts.push(task); return task; },
    openDocument: (options) => io.documents.push(options), shareFileMessage() {},
    createInnerAudioContext: () => ({ destroy() { io.audioDestroyed++; }, onTimeUpdate() {}, onPlay() {}, onPause() {}, onEnded() {}, onError() {}, play() {}, pause() {} })
  };
  return { hooks, io, lifecycle: { onShow: (fn) => hooks.show.push(fn), onHide: (fn) => hooks.hide.push(fn), onPageScroll: (fn) => hooks.scroll.push(fn), onLoad() {}, onUnload() {} } };
}
const row = (id, previewKind = 'IMAGE') => ({ title: `测试资料 ${id}`, category: 'MEDIA', publicCode: String(id).padStart(32, '0'), previewKind, previewStatus: 'READY', fileName: 'notice.docx', fileSize: 2097152, versionNo: 1 });
const rows = Array.from({ length: 23 }, (_, i) => row(i + 1));
const data = (records = rows) => ({ title: '测试会议', ended: false, records });
const p = platform(); let calls = 0; let fetch = async () => data();
const materials = await component('../src/components/PublicMeetingMaterials.vue', { '@dcloudio/uni-app': p.lifecycle, '@/api/meetingMaterials': { resolveMeetingMaterials: () => { calls++; return fetch(); }, publicMaterialUrl: (code) => `https://example.invalid/${code}` } });
const app = mount(materials, { visible: false, inviteToken: 'synthetic-invitation' });
try {
  const { vm, props } = app; const { io, hooks } = p;
  await flush(); assert.equal(calls, 0, 'closed dialogs do not load'); props.visible = true; await flush(); assert.equal(calls, 1);
  vm.keyword = '测试'; vm.category = 'MEDIA'; await flush(); vm.page = 2; await flush(); vm.savedScrollTop = 275;
  props.visible = false; await flush(); props.visible = true; await flush();
  assert.equal(vm.keyword, '测试'); assert.equal(vm.category, 'MEDIA'); assert.equal(vm.page, 2); assert.equal(vm.scrollTarget, 275);
  await vm.open(rows[10]); assert.ok(vm.selected); vm.close(); await flush(); assert.equal(vm.selected, undefined); assert.equal(vm.scrollTarget, 275);
  const late = defer(); fetch = () => late.promise; const opening = vm.open(rows[10]); props.visible = false; await flush(); late.resolve(data()); await opening; await flush();
  assert.equal(vm.selected, undefined, 'late response must not reopen preview'); assert.equal(io.downloads.length, 0);
  fetch = async () => data([row(1, 'OFFICE'), row(2, 'AUDIO'), row(3, 'TEXT')]); props.visible = true; await flush();
  await vm.open(row(1, 'OFFICE')); const cancelled = io.downloads.at(-1); props.visible = false; await flush(); assert.ok(cancelled.aborted);
  props.visible = true; await flush(); await vm.open(row(1, 'OFFICE')); const current = io.downloads.at(-1);
  cancelled.options.success({ statusCode: 200, tempFilePath: '/tmp/late.pdf' }); cancelled.options.complete();
  assert.equal(io.documents.length, 0); assert.ok(vm.downloading, 'old completion cannot clear current download');
  current.options.success({ statusCode: 200, tempFilePath: '/tmp/current.pdf' }); current.options.complete(); assert.equal(io.documents.length, 1);
  hooks.hide.forEach((fn) => fn()); await flush(); assert.ok(!io.deleted.includes('/tmp/current.pdf'), 'native viewer retains its file');
  hooks.show.forEach((fn) => fn()); await flush(); assert.equal(props.visible, true); assert.equal(vm.keyword, '测试');
  await vm.open(row(2, 'AUDIO')); fetch = async () => data([]); await vm.refresh(); await flush(); assert.equal(vm.selected, undefined); assert.ok(io.audioDestroyed > 0);
  fetch = async () => data([row(3, 'TEXT')]); await vm.refresh(); await vm.open(row(3, 'TEXT')); const text = io.texts.at(-1);
  props.visible = false; await flush(); text.options.success({ statusCode: 206, data: 'late text' }); assert.notEqual(vm.textPreview, 'late text');
  fetch = async () => { throw new Error('offline'); }; props.visible = true; await flush(); assert.equal(vm.error, 'offline'); assert.equal(vm.records.length, 0);
  fetch = async () => data(); await vm.refresh(); assert.equal(vm.error, ''); assert.equal(vm.records.length, 23);
} finally { app.unmount(); }
const q = platform();
const invitation = { inviteType: 'MEETING', status: 'OPEN', projectName: '测试项目', visitEndTime: new Date(Date.now() + 3600000).toISOString(), projectLocation: { routeImageAvailable: true } };
let meeting = { ...invitation }; let routeCalls = 0; let routeDownload = async () => '/tmp/route.png'; const removed = [];
class ApiRequestError extends Error {}
const page = await component('../src/pages/public/meeting-invite.vue', {
  '@dcloudio/uni-app': q.lifecycle, '@/api/request': { ApiRequestError }, '@/utils/navigation': { showToast() {} }, '@/utils/wechat': { getFreshWechatCode: async () => 'code' },
  '@/utils/visitorPersonalInfo': { useVisitorPersonalInfo: () => ({ rememberInfo: Vue.ref(false), personalInfoApplied: Vue.ref(false), applyPersonalInfo() {}, resetPersonalInfo() {}, rememberChange() {} }) },
  '@/utils/meetingInviteRefresh': { createMeetingInviteRefreshCoordinator },
  '@/api/siteAccess': { resolvePublicSiteVisit: async () => meeting, createPublicMeetingVisitorSession: async () => ({ invitation: meeting, pageState: 'FORM', visitorSessionToken: 'session' }), getPublicMeetingVisitorProfiles: async () => [], downloadPublicProjectRouteImage: () => { routeCalls++; return routeDownload(); }, removePublicProjectRouteImage: (path) => removed.push(path) }
});
const pageApp = mount(page);
try {
  const s = pageApp.vm; s.token = 'synthetic-invitation'; await s.initialize(false); await flush(); assert.equal(routeCalls, 0);
  s.visitorCompany = '测试单位'; s.contactName = '未提交访客'; s.visitorRemark = '保留草稿'; q.hooks.scroll.forEach((fn) => fn({ scrollTop: 380 }));
  s.openPanel('navigation'); await flush(); assert.equal(routeCalls, 1); assert.equal(s.navigationVerified, true);
  s.rememberNavigationScroll({ detail: { scrollTop: 280 } });
  q.hooks.hide.forEach((fn) => fn()); s.rememberNavigationScroll({ detail: { scrollTop: 0 } });
  s.foreground = true; await s.refreshPanelStatus(true); await flush();
  s.rememberNavigationScroll({ detail: { scrollTop: 120 } }); await s.restoreNavigationScroll();
  assert.equal(s.navigationScrollTarget, 280, 'native viewer suspension must not replace the saved navigation position with zero');
  await s.closePanel(); assert.equal(q.io.scrolls.at(-1), 380); assert.equal(s.visitorRemark, '保留草稿');
  const late = defer(); routeDownload = () => late.promise; s.openPanel('navigation'); await flush(); await s.closePanel(); late.resolve('/tmp/late-route.png'); await flush();
  assert.equal(s.activePanel, ''); assert.ok(removed.includes('/tmp/late-route.png'));
  routeDownload = async () => '/tmp/new-route.png'; s.openPanel('navigation'); await flush(); s.currentTime = Date.now() + 7200000; await flush(); assert.equal(s.activePanel, '');
  meeting = { ...invitation, status: 'EXPIRED' }; s.openPanel('materials'); await flush(); assert.equal(s.activePanel, 'materials'); assert.equal(s.canShowNavigation, false); assert.equal(s.canShowMaterials, true);
  meeting = { ...invitation, status: 'VOIDED' }; await s.refreshPanelStatus(); await flush(); assert.equal(s.activePanel, ''); assert.equal(s.canShowMaterials, false);
  assert.equal(s.visitorCompany, '测试单位'); assert.equal(s.contactName, '未提交访客'); assert.equal(s.visitorRemark, '保留草稿');
} finally { pageApp.unmount(); }
console.log('meeting dialogs: lazy load, state retention, stale response/download, native return, withdrawal, expiry and voiding passed');
