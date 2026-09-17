import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { parse, compileScript } from '@vue/compiler-sfc';
import { transform } from 'esbuild';
import * as Vue from 'vue';

const source = await readFile(new URL('../src/pages/safety-committee/capture.vue', import.meta.url), 'utf8');
const { descriptor } = parse(source);
const { code } = await transform(compileScript(descriptor, { id: 'capture-test' }).content, { loader: 'ts', format: 'esm' });
const body = code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g,
  (_, spec, name) => `const ${spec.replace(/\bas\b/g, ':')} = imports(${JSON.stringify(name)});`)
  .replace(/export\s*\{\s*([\w$]+)\s+as\s+default\s*\};?/, 'return $1;').replace('export default', 'return');
const original = { setTimeout, clearTimeout, setInterval, clearInterval, now: Date.now };
const flush = async () => { for (let i = 0; i < 15; i++) { await Promise.resolve(); await Vue.nextTick(); } };
let now = 0;
const timers = new Map();
globalThis.setTimeout = (fn, ms) => { const id = Symbol(); timers.set(id, { fn, at: now + ms }); return id; };
globalThis.setInterval = (fn, ms) => { const id = Symbol(); timers.set(id, { fn, at: now + ms, repeat: ms }); return id; };
globalThis.clearTimeout = globalThis.clearInterval = id => timers.delete(id);
Date.now = () => now;
async function tick(ms) {
  const end = now + ms;
  while (true) {
    const entry = [...timers.entries()].filter(([, t]) => t.at <= end).sort((a, b) => a[1].at - b[1].at)[0];
    if (!entry) break;
    const [id, task] = entry; now = task.at;
    if (task.repeat) task.at += task.repeat; else timers.delete(id);
    task.fn(); await flush();
  }
  now = end; await flush();
}
async function setup(overrides = {}) {
  const hooks = {}, calls = { photo: [], start: [], stop: [], deleted: [], emitted: [], info: [], back: 0, authorization: [] };
  const uniApi = {
    authorize(o) { calls.authorization.push(o.scope); o.success(); },
    createCameraContext: () => ({ takePhoto: o => calls.photo.push(o), startRecord: o => calls.start.push(o), stopRecord: o => calls.stop.push(o) }),
    getFileInfo(o) { calls.info.push(o.filePath); o.success({ size: 1000 }); },
    getFileSystemManager: () => ({ unlink: o => calls.deleted.push(o.filePath) }),
    openSetting: o => o.success(), navigateBack: () => calls.back++, ...overrides
  };
  globalThis.uni = uniApi;
  const imports = {
    vue: Vue,
    '@dcloudio/uni-app': Object.fromEntries(['onShow', 'onHide', 'onUnload'].map(name => [name, fn => hooks[name] = fn])),
    '@/stores/auth': { useAuthStore: () => ({ ensureRootAccess: async () => true }) },
    '@/api/safetyCommittee': { validateCommitteeFile: f => { if (f.size > 500 * 1024 ** 2) throw new Error('文件过大'); } }
  };
  const component = new Function('imports', body)(name => imports[name] || {});
  component.render = () => null;
  const renderer = Vue.createRenderer({ createElement: () => ({}), createText: () => ({}), createComment: () => ({}), insert() {}, remove() {}, setText() {}, setElementText() {}, patchProp() {}, parentNode: () => null, nextSibling: () => null });
  const app = renderer.createApp(component);
  app.config.globalProperties.getOpenerEventChannel = () => ({ emit: (...args) => calls.emitted.push(args) });
  app.mount({}); const vm = app._instance.setupState;
  hooks.onShow(); await flush(); vm.cameraReady = true;
  return { vm, calls, hooks, uniApi, close() { hooks.onUnload(); app.unmount(); timers.clear(); } };
}
try {
  {
    const t = await setup({ authorize(o) { o.fail({errno:112,errMsg:'authorize:fail api scope is not declared in the privacy agreement'}); } });
    assert.equal(t.vm.allowed, false); assert.equal(t.vm.mounted, false);
    assert.equal(t.vm.permissionError, false, 'missing platform declaration is not a user denial');
    assert.equal(t.vm.configurationError, true); assert.match(t.vm.error, /隐私声明未完善/);
    t.uniApi.authorize = o => o.success(); await t.vm.restartCamera();
    assert.equal(t.vm.allowed, true); assert.equal(t.vm.configurationError, false); assert.equal(t.vm.error, ''); t.close();
  }
  {
    const t = await setup({ authorize(o) { o.fail({errMsg:'authorize:fail auth deny'}); } });
    assert.equal(t.vm.permissionError, true); assert.equal(t.vm.configurationError, false); assert.match(t.vm.error, /权限设置/); t.close();
  }
  {
    const t = await setup({ authorize(o) { if(o.scope==='scope.record')o.fail({errno:112,errMsg:'not declared in the privacy agreement'});else o.success(); } });
    t.vm.press(); await tick(350); t.vm.release();
    assert.equal(t.calls.start.length, 0); assert.equal(t.vm.configurationError, true);
    t.vm.press(); t.vm.release(); assert.equal(t.calls.photo.length, 1, 'missing microphone declaration still permits photos with camera grant'); t.close();
  }
  {
    const t = await setup(); t.vm.press(); await tick(100); t.vm.release(); t.vm.release();
    assert.equal(t.calls.photo.length, 1); assert.equal(t.calls.start.length, 0);
    t.calls.photo[0].success({ tempImagePath: 'wxfile://photo.jpg' }); await flush();
    assert.equal(t.vm.kind, 'photo'); assert.equal(t.vm.result.path, 'wxfile://photo.jpg');assert.equal(t.vm.result.thumbnailPath,'wxfile://photo.jpg');assert.equal(t.vm.result.capturedTemporary,true);
    t.vm.confirm(); t.vm.confirm(); assert.equal(t.calls.emitted.length, 1); assert.equal(t.calls.back, 1);
    assert.equal(t.calls.emitted[0][0], 'captured'); t.close(); assert.equal(t.calls.deleted.length, 0, 'confirmed file remains available for upload');
  }
  {
    const t = await setup(); t.vm.press(); t.vm.press(); await tick(350);
    assert.equal(t.calls.start.length, 1); assert.equal(t.calls.start[0].timeout, 300);
    t.calls.start[0].success(); await tick(2100); assert.equal(t.vm.timerText, '00:02');
    t.vm.release(); t.vm.release(); assert.equal(t.calls.stop.length, 1); assert.equal(t.calls.photo.length, 0);
    t.calls.stop[0].success({ tempVideoPath: 'wxfile://video.mp4',tempThumbPath:'wxfile://cover.jpg' });
    t.calls.start[0].timeoutCallback({ tempVideoPath: 'wxfile://video.mp4',tempThumbPath:'wxfile://cover.jpg' }); await flush();
    assert.equal(t.calls.info.length, 1); assert.equal(t.vm.kind, 'video');
    assert.equal(t.vm.result.thumbnailPath,'wxfile://cover.jpg');assert.deepEqual(t.calls.deleted,[]);
    await t.vm.retake(); assert.ok(t.calls.deleted.includes('wxfile://video.mp4')); assert.equal(t.vm.result, undefined); assert.equal(t.vm.mounted, true); t.close();
    assert.ok(t.calls.deleted.includes('wxfile://cover.jpg'));
  }
  {
    const t=await setup();t.vm.press();await tick(350);t.calls.start[0].success();t.vm.release();
    t.calls.stop[0].success({tempVideoPath:'wxfile://confirmed.mp4',tempThumbPath:'wxfile://confirmed.jpg'});await flush();t.vm.confirm();
    assert.equal(t.calls.emitted[0][1].thumbnailPath,'wxfile://confirmed.jpg');t.close();assert.deepEqual(t.calls.deleted,[],'capture unload preserves both confirmed files');
  }
  {
    const t = await setup(); t.vm.press(); await tick(350); t.vm.release();
    assert.equal(t.calls.stop.length, 0); t.calls.start[0].success(); assert.equal(t.calls.stop.length, 1, 'release during native start stops once start completes'); t.close();
    t.calls.stop[0].success({ tempVideoPath: 'wxfile://late.mp4',tempThumbPath:'wxfile://late.jpg' }); await flush(); assert.ok(t.calls.deleted.includes('wxfile://late.mp4'));assert.ok(t.calls.deleted.includes('wxfile://late.jpg'));
  }
  {
    const t = await setup(); t.vm.press(); t.vm.cancelPress(); await tick(400); t.vm.release();
    assert.equal(t.calls.photo.length + t.calls.start.length, 0, 'cancelled short touch never captures');
    t.vm.press(); await tick(350); t.calls.start[0].success(); t.hooks.onHide();
    assert.equal(t.calls.stop.length, 1); t.calls.stop[0].success({ tempVideoPath: 'wxfile://interrupted.mp4' }); await flush();
    t.hooks.onShow(); await flush(); assert.equal(t.vm.result.path, 'wxfile://interrupted.mp4'); assert.match(t.vm.error, /中断/); t.close();
  }
  {
    let permission;
    const t = await setup({ authorize(o) { if (o.scope === 'scope.record') permission = o; else o.success(); } });
    t.vm.press(); await tick(350); t.vm.release(); permission.success(); await flush();
    assert.equal(t.calls.start.length, 0); assert.match(t.vm.error, /再次长按/); t.close();
  }
  {
    const t = await setup({ authorize(o) { if (o.scope === 'scope.record') o.fail({errMsg:'denied'}); else o.success(); } });
    t.vm.press(); await tick(350); t.vm.release(); assert.equal(t.calls.photo.length, 0); assert.equal(t.vm.permissionError, true);
    t.vm.press(); await tick(50); t.vm.release(); assert.equal(t.calls.photo.length, 1, 'microphone denial does not block photos'); t.close();
  }
  {
    let permission;
    const t = await setup({ authorize(o) { permission = o; } }); t.hooks.onHide(); permission.success(); await flush(); assert.equal(t.vm.mounted, false, 'late camera permission cannot mount a hidden page'); t.close();
  }
  {
    const t = await setup(); t.vm.press(); await tick(350); t.calls.start[0].success(); await tick(300000);
    assert.equal(t.calls.stop.length, 1); assert.equal(t.vm.seconds, 300);
    t.calls.start[0].timeoutCallback({ tempVideoPath: 'wxfile://limit.mp4',tempThumbPath:'wxfile://limit.jpg' }); await flush(); assert.equal(t.vm.kind, 'video');assert.equal(t.vm.result.thumbnailPath,'wxfile://limit.jpg'); t.close();assert.ok(t.calls.deleted.includes('wxfile://limit.jpg'));
  }
  {
    let info;
    const t = await setup({ getFileInfo(o) { info = o; } }); t.vm.press(); t.vm.release(); t.calls.photo[0].success({ tempImagePath: 'wxfile://pending.jpg' });
    t.close(); info.success({ size: 1000 }); await flush(); assert.equal(t.vm.result, undefined); assert.ok(t.calls.deleted.includes('wxfile://pending.jpg'));
  }
  {
    const t = await setup(); t.vm.cameraFailure({detail:{errMsg:'camera unavailable'}}); assert.equal(t.vm.cameraReady, false); assert.match(t.vm.error, /unavailable/);
    await t.vm.restartCamera(); assert.equal(t.vm.mounted, true); t.vm.cameraReady = true;
    t.vm.press(); t.vm.release(); t.calls.photo[0].fail({errMsg:'photo failed'}); assert.equal(t.vm.busy, false);
    t.vm.press(); t.vm.release(); assert.equal(t.calls.photo.length, 2); t.close();
  }
  {
    const t = await setup(); t.hooks.onHide(); t.vm.cameraStopped();
    t.hooks.onShow(); await flush(); assert.equal(t.vm.mounted, true); assert.equal(t.vm.cameraReady, false, 'foreground remount waits for native camera initialization');
    t.vm.press(); t.vm.release(); assert.equal(t.calls.photo.length, 0);
    t.vm.cameraReady = true; t.vm.press(); t.vm.release(); assert.equal(t.calls.photo.length, 1); t.close();
  }
  console.log('安委会拍摄页通过：轻点照片、长按录像、松手/取消、防重复、启动竞态、首次授权、拒绝麦克风、后台中断、300秒停止、预览回填、重拍及迟到临时文件清理。');
} finally {
  Object.assign(globalThis, { setTimeout: original.setTimeout, clearTimeout: original.clearTimeout, setInterval: original.setInterval, clearInterval: original.clearInterval });
  Date.now = original.now; delete globalThis.uni;
}
