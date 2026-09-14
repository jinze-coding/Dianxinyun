// Execute the real component setup and navigation utility with controlled native callbacks.
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { parse, compileScript } from '@vue/compiler-sfc';
import { transform } from 'esbuild';
import * as Vue from 'vue';
import * as navigation from '../src/utils/projectMapNavigation.ts';
const renderer = Vue.createRenderer({ createElement: () => ({}), createText: () => ({}), createComment: () => ({}), insert() {}, remove() {}, setText() {}, setElementText() {}, patchProp() {}, parentNode: () => null, nextSibling: () => null });
const { descriptor } = parse(await readFile(new URL('../src/components/ProjectLocationCard.vue', import.meta.url), 'utf8'));
const { code } = await transform(compileScript(descriptor, { id: 'card-test' }).content, { loader: 'ts', format: 'esm' });
const body = code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g, (_, spec, name) => `const ${spec.replace(/\bas\b/g, ':')} = imports(${JSON.stringify(name)});`).replace(/export\s*\{\s*([\w$]+)\s+as\s+default\s*\};?/, 'return $1;').replace('export default', 'return');
const toasts = [], previews = [];
const component = new Function('imports', body)(name => name === 'vue' ? Vue : name === '@/utils/projectMapNavigation' ? navigation : { showToast: text => toasts.push(text) });
component.render = () => null;
const flush = async () => { for (let i = 0; i < 8; i++) { await Promise.resolve(); await Vue.nextTick(); } };
let nativeOptions, confirmation, calls = 0, systems = 0, retries = 0, platform = 'ios';
globalThis.uni = {
  getSystemInfoSync: () => ({ platform }),
  createMapContext: () => ({ openMapApp: options => { calls++; nativeOptions = options; } }),
  openLocation: options => { systems++; options.success(); },
  showModal: options => { confirmation = options; }, previewImage: options => previews.push(options)
};
const point = { navigable: true, coordinateType: 'GCJ02', latitude: 31, longitude: 121, routeImageAvailable: true };
function mount() {
  const props = Vue.reactive({ location: { ...point }, projectName: '测试项目', mapId: 'card-map', routeImagePath: '', routeImageLoading: false, routeImageError: '', onRetryRouteImage: () => retries++ });
  const app = renderer.createApp({ setup: () => () => Vue.h(component, props) }); app.mount({});
  return { props, vm: app._instance.subTree.component.setupState, unmount: () => app.unmount() };
}
let card = mount(); const s = card.vm;
assert.equal(s.mapChoices[3].provider, 'apple');
const first = s.navigateToProject('tencent'); await s.navigateToProject('baidu');
assert.equal(calls, 1, 'rapid clicks invoke only one native chooser'); assert.equal(s.opening, true);
nativeOptions.fail({ errMsg: 'openMapApp:fail cancel' }); await first;
assert.equal(s.opening, false); assert.equal(confirmation, undefined); assert.equal(systems, 0); assert.deepEqual(toasts, []);
const failed = s.navigateToProject('amap'); nativeOptions.fail({ errMsg: 'unsupported' }); await flush();
assert.equal(s.opening, true); await s.navigateToProject('system'); assert.equal(systems, 0);
confirmation.success({ confirm: false }); await failed; assert.equal(s.opening, false);
const fallback = s.navigateToProject('baidu'); nativeOptions.fail({ errMsg: 'unsupported' }); await flush();
confirmation.success({ confirm: true }); await fallback; assert.equal(systems, 1);
card.props.location.navigable = false; await s.navigateToProject('system'); assert.equal(systems, 1); assert.equal(s.canNavigate, false);
s.previewRouteImage(); assert.equal(previews.length, 0);
card.props.routeImagePath = '/tmp/route.png'; await flush(); s.previewRouteImage();
assert.deepEqual(previews.at(-1), { urls: ['/tmp/route.png'], current: '/tmp/route.png' });
card.props.routeImageError = '下载失败'; await flush(); s.previewRouteImage(); assert.equal(previews.length, 1); assert.equal(s.imageError, '下载失败');
s.retryRouteImage(); assert.equal(retries, 1); card.props.routeImageLoading = true; await flush(); s.retryRouteImage(); assert.equal(retries, 1);
card.props.routeImageError = ''; card.props.routeImageLoading = false; await flush(); s.imageDecodeError = true;
assert.match(s.imageError, /显示失败/); s.previewRouteImage(); assert.equal(previews.length, 1);
s.retryRouteImage(); assert.equal(retries, 2); assert.match(s.imageError, /显示失败/);
card.props.routeImagePath = '/tmp/route-retry.png'; await flush(); s.previewRouteImage(); assert.equal(previews.at(-1).current, '/tmp/route-retry.png');
card.props.location.navigable = true;
const stale = s.navigateToProject('apple'); nativeOptions.fail({ errMsg: 'unsupported' }); await flush(); card.unmount();
confirmation.success({ confirm: true }); await stale; assert.equal(systems, 1, 'hidden/expired card ignores native confirmation');
card = mount(); const changed = card.vm.navigateToProject('tencent'); nativeOptions.fail({ errMsg: 'unsupported' }); await flush();
card.props.location.latitude = 32; confirmation.success({ confirm: true }); await changed; assert.equal(systems, 1, 'changed destination invalidates pending confirmation'); card.unmount();
platform = 'devtools'; card = mount(); const beforeCalls = calls; const unsupported = card.vm.navigateToProject('baidu'); await flush();
assert.equal(calls, beforeCalls); assert.equal(card.vm.mapChoices[3].provider, 'system'); confirmation.success({ confirm: false }); await unsupported; card.unmount();
assert.deepEqual(toasts, []);
console.log('project location card: native lock/cancel/confirmed fallback, late confirmation, missing/error/retry/preview and no coordinates passed');
