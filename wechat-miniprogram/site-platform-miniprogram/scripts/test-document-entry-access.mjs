import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import ts from 'typescript';
import { parse, compileScript } from '@vue/compiler-sfc';
import { transform } from 'esbuild';
import * as Vue from 'vue';

const project = { state: Vue.reactive({ currentProjectId: 1, projects: [{id:1,projectName:'A'}, {id:2,projectName:'B'}] }), loadProjects: async () => {} };
const navigations = [], hooks = {};
let freshUser;
globalThis.uni = { getStorageSync: () => null, setStorageSync() {}, removeStorageSync() {}, hideTabBar() {}, switchTab: o => navigations.push(o.url) };
const authSource = await readFile(new URL('../src/stores/auth.ts', import.meta.url), 'utf8');
const authImports = { vue: Vue, './project': { useProjectStore: () => project }, '@/api/auth': { getCurrentUser: async () => freshUser }, '@/api/request': { getToken: () => 'test-session', USE_MOCK: false } };
const authExports = {};
vm.runInNewContext(ts.transpileModule(authSource, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText,
  { exports: authExports, require: name => authImports[name], uni, console });
const auth = authExports.useAuthStore();
const user = (a = [], b = ['DOCUMENT_SEAL', 'DOCUMENT_CIRCULATION']) => ({roles:['MEMBER'],
  menus: [{menuCode:'DOCUMENT_SEAL'}, {menuCode:'DOCUMENT_CIRCULATION'}],
  projectContexts: [
    {projectId:1, accessStatus:'ACTIVE', enabledBusinessModules:['DOCUMENT'], menuCodes:['MINI_DOCUMENT','DOCUMENT_LIBRARY',...a], permissionCodes:['document.view']},
    {projectId:2, accessStatus:'ACTIVE', enabledBusinessModules:['DOCUMENT'], menuCodes:['MINI_DOCUMENT',...b], permissionCodes:['document.view']}
  ]});
freshUser = user();
auth.state.user = user(['DOCUMENT_SEAL','DOCUMENT_CIRCULATION']); // Stale cached grants before entering the page.
const imports = {
  vue: {...Vue, vModelText: {}}, '@dcloudio/uni-app': { onShow: fn => hooks.show = fn },
  '@/stores/auth': { useAuthStore: () => auth }, '@/stores/project': { useProjectStore: () => project },
  '@/constants/workspaceTheme': { WORKSPACE_THEME: {} }, '@/utils/navLayout': { usePageScrollHeight: () => ({scrollStyle:{}}) },
  '@/utils/navigation': { navigateTo: url => navigations.push(url) },
  '@/api/document': { getDocumentFolders: async () => [], getProjectDocuments: async () => ({records:[],total:0}), getProjectDocumentSummary: async () => ({total:0,active:0,archived:0,recentUpdates:0}) }
};
const source = await readFile(new URL('../src/pages/documents/index.vue', import.meta.url), 'utf8');
const {descriptor} = parse(source);
const {code} = await transform(compileScript(descriptor, {id:'document-access-test',inlineTemplate:true,templateOptions:{compilerOptions:{isCustomElement:tag=>['view','text','button','scroll-view','input'].includes(tag)}}}).content, {loader:'ts',format:'esm'});
const body = code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g,
  (_,spec,name) => `const ${spec.replace(/\bas\b/g, ':')} = imports(${JSON.stringify(name)});`)
  .replace(/export\s*\{\s*([\w$]+)\s+as\s+default\s*\};?/, 'return $1;').replace('export default', 'return');
const component = new Function('imports',body)(name => imports[name] || {render:() => null});
const renderer = Vue.createRenderer({
  createElement: type => ({type,children:[],props:{}}), createText: text => ({text}), createComment: () => ({}),
  insert(node,parent,anchor) { if(node.parent){const previous=node.parent.children;previous.splice(previous.indexOf(node),1);} node.parent=parent; const index=parent.children.indexOf(anchor); parent.children.splice(index<0?parent.children.length:index,0,node); },
  remove(node) { const list=node.parent?.children; if(list)list.splice(list.indexOf(node),1); node.parent=null; },
  setText(node,text){node.text=text;},setElementText(node,text){node.text=text;node.children=[];},patchProp(node,key,_old,value){node.props[key]=value;},
  parentNode: node=>node.parent, nextSibling: node=>node.parent?.children[node.parent.children.indexOf(node)+1]
});
const root={children:[]}; const app=renderer.createApp(component);app.config.warnHandler=()=>{};app.mount(root);
const textOf = node => [node.text || '',...(node.children||[]).map(textOf)].join('');
const buttons = node => [...(node.type==='button'?[node]:[]),...(node.children||[]).flatMap(buttons)];
const entry = name => buttons(root).find(node=>textOf(node).includes(name));
try {
  assert.equal(entry('用印管理'),undefined,'cached grants remain hidden until refreshed');
  await hooks.show(); await Vue.nextTick();
  assert.ok(entry('工程资料')); assert.equal(entry('用印管理'),undefined); assert.equal(entry('图纸收发'),undefined);
  assert.equal(auth.hasProjectMenu(1,'DOCUMENT_SEAL','DOCUMENT'),false,'global and other-project menus cannot restore removed entries');
  project.state.currentProjectId=2; await Vue.nextTick();
  const previousSeal=entry('用印管理'); assert.ok(previousSeal); assert.ok(entry('图纸收发'));
  previousSeal.props.onTap(); assert.match(navigations.pop(),/projectId=2/);
  project.state.currentProjectId=1; await Vue.nextTick();previousSeal.props.onTap(); assert.equal(navigations.length,0,'a stale tap checks the current grant again');
  auth.state.user=user(['DOCUMENT_SEAL']); await Vue.nextTick();assert.ok(entry('用印管理'));assert.equal(entry('图纸收发'),undefined);
  auth.state.user.projectContexts[0].menuCodes=[];await Vue.nextTick();assert.equal(entry('用印管理'),undefined,'explicit empty menu has no fallback');
  auth.state.user=user();auth.state.user.roles=['PLATFORM_ADMIN'];await Vue.nextTick();assert.ok(entry('用印管理'));assert.ok(entry('图纸收发'));
  auth.state.user.projectContexts[0].enabledBusinessModules=[];await Vue.nextTick();assert.equal(entry('用印管理'),undefined);assert.equal(entry('图纸收发'),undefined);
  auth.state.user=user(['DOCUMENT_SEAL']);auth.state.user.projectContexts[0].accessStatus='SUSPENDED';await Vue.nextTick();assert.equal(entry('用印管理'),undefined);
  console.log('资料入口通过：真实模板渲染、进入刷新撤权、当前项目菜单、无全局/其他项目回退、切项目及迟到点击、管理员模块限制、暂停成员。');
} finally { app.unmount(); delete globalThis.uni; }
