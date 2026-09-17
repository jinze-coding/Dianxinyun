// Run the real page setup with controlled requests; no backend or business data is changed.
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { parse, compileScript } from '@vue/compiler-sfc';
import { transform } from 'esbuild';
import * as Vue from 'vue';

const hooks = {};
const intervals = new Map();
const realInterval = globalThis.setInterval;
const realClear = globalThis.clearInterval;
const flush = async () => { for (let i=0;i<20;i++) { await Promise.resolve(); await Vue.nextTick(); } };
const deferred = () => { let resolve; const promise = new Promise(done => { resolve=done; }); return {promise,resolve}; };
globalThis.setInterval = (fn, delay) => { const id=Symbol(); intervals.set(id,{fn,delay}); return id; };
globalThis.clearInterval = id => intervals.delete(id);
globalThis.uni = { hideTabBar() {}, navigateTo() {} };
const projects = { state:Vue.reactive({currentProjectId:3,projects:[{id:3,name:'测试项目'}]}),loadProjects:async()=>{},setCurrentProject(id){this.state.currentProjectId=id;} };
let response = {records:[{id:22}],total:22,latestId:22};
let fetchPage = async () => response;
const requests = [];
const imports = {
  vue:Vue,
  '@dcloudio/uni-app':Object.fromEntries(['onShow','onHide','onUnload'].map(name=>[name,fn=>{hooks[name]=fn;}])),
  '@/utils/navLayout':{usePageScrollHeight:()=>({scrollStyle:Vue.ref({})})},
  '@/stores/auth':{useAuthStore:()=>({hasProjectPermission:()=>true,ensureRootAccess:async()=>true})},
  '@/stores/project':{useProjectStore:()=>projects},
  '@/constants/workspaceTheme':{WORKSPACE_THEME:{}},
  '@/api/safetyCommittee':{committeeAccessLost:()=>false,committeeApi:{categories:async()=>['其他','基坑工程'],list:(...args)=>{requests.push(args);return fetchPage();}}}
};
const source=await readFile(new URL('../src/pages/safety-committee/index.vue',import.meta.url),'utf8');
const {descriptor}=parse(source);
const {code}=await transform(compileScript(descriptor,{id:'committee-filter-test'}).content,{loader:'ts',format:'esm'});
const body=code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g,
  (_,spec,name)=>`const ${spec.replace(/\bas\b/g,':')} = imports(${JSON.stringify(name)});`)
  .replace(/export\s*\{\s*([\w$]+)\s+as\s+default\s*\};?/,'return $1;').replace('export default','return');
const component=new Function('imports',body)(name=>imports[name]||{});
component.render=()=>null;
const renderer=Vue.createRenderer({createElement:()=>({}),createText:()=>({}),createComment:()=>({}),insert(){},remove(){},setText(){},setElementText(){},patchProp(){},parentNode:()=>null,nextSibling:()=>null});
const app=renderer.createApp(component);app.mount({});const vm=app._instance.setupState;
try {
  await hooks.onShow();await flush();
  assert.deepEqual(requests.at(-1),[3,'',1]);
  const timer=[...intervals.values()].find(item=>item.delay===5000);assert.ok(timer);
  vm.scrollTop=128;await timer.fn();assert.deepEqual(requests.at(-1),[3,'',1]);assert.equal(vm.scrollTop,128);
  vm.page=2;vm.filter({detail:{value:2}});await flush();
  assert.deepEqual(requests.at(-1),[3,'基坑工程',1]);assert.equal(vm.page,1);
  vm.changePage(1);await flush();assert.equal(requests.at(-1)[2],2);
  vm.scrollTop=210;await timer.fn();assert.equal(vm.scrollTop,210);assert.equal(requests.at(-1)[2],2);

  const late=deferred();fetchPage=()=>late.promise;const pending=vm.refresh();
  response={records:[{id:30}],total:1,latestId:30};fetchPage=async()=>response;
  vm.filter({detail:{value:1}});await flush();assert.equal(vm.data.records[0].id,30);
  late.resolve({records:[{id:999}],total:999,latestId:999});await pending;assert.equal(vm.data.records[0].id,30,'old category response cannot replace the new query');

  vm.scrollTop=154;hooks.onHide();const count=requests.length;await timer.fn();assert.equal(requests.length,count);
  await hooks.onShow();await flush();assert.deepEqual(requests.at(-1),[3,'其他',1]);assert.equal(vm.scrollTop,154);
  fetchPage=async()=>{throw new Error('网络中断');};await vm.refresh();assert.match(vm.error,/网络/);assert.equal(vm.data.records[0].id,30);assert.equal(vm.category,'其他');
  fetchPage=async()=>response;await vm.refresh();assert.equal(vm.error,'');
  vm.filter({detail:{value:0}});await flush();assert.deepEqual(requests.at(-1),[3,'',1]);assert.equal(vm.hasNew,false);
  response={records:[{id:8,category:'施工安全管理'},{id:90,category:'其他'}],total:22,latestId:90};await vm.refresh();
  assert.deepEqual(vm.data.records.map(row=>row.id),[8,90],'preserve server category order instead of sorting by id');
  vm.changePage(1);await flush();
  response={records:[{id:8,category:'施工安全管理'},{id:91,category:'其他'}],total:23,latestId:91};await vm.refresh();
  assert.equal(vm.hasNew,true);assert.deepEqual(vm.data.records.map(row=>row.id),[8,90],'hold older page when a later category receives a record');
  vm.newest();await flush();assert.deepEqual(requests.at(-1),[3,'',1]);assert.equal(vm.hasNew,false);
  assert.deepEqual(vm.data.records.map(row=>row.id),[8,91]);assert.equal(vm.latest,91);
  console.log('安委会筛选页通过：分类分页、保留服务端顺序、末尾分类新增提示与回第一页刷新、不带隐藏日期条件、滚动恢复、迟到响应丢弃、前台与断网重试。');
} finally {
  hooks.onUnload();app.unmount();globalThis.setInterval=realInterval;globalThis.clearInterval=realClear;delete globalThis.uni;
}
