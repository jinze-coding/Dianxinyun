import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import ts from 'typescript';
import * as Vue from 'vue';
import { parse, compileScript } from '@vue/compiler-sfc';

const requests = [], navigations = [], toasts = [];
let savedToken = 'restricted-token';
const auth = { state: { user: { username: '19999000000', initialPasswordSetupRequired: true, initialPasswordSetupReason: 'ADMIN_IMPORT' } },
  requiresInitialPasswordSetup: () => auth.state.user?.initialPasswordSetupRequired === true,
  loadUser: async () => {}, completeLogin: async token => { savedToken = token; auth.state.user = {...auth.state.user, initialPasswordSetupRequired:false}; },
  navigateAfterLogin: () => navigations.push('todo'), logout: async () => {} };
const hooks = {};
let outcome = async () => ({token:'fresh-token'});
const uni = {showModal: opts => { toasts.push(opts.content); opts.complete(); }, reLaunch: opts => navigations.push(opts.url)};
const source = await readFile(new URL('../src/pages/initial-password/index.vue', import.meta.url), 'utf8');
const script = compileScript(parse(source).descriptor, {id:'initial-password-test'}).content;
const exports = {};
const imports = {vue:Vue, '@dcloudio/uni-app':{onLoad:fn=>hooks.load=fn}, '@/stores/auth':{useAuthStore:()=>auth}, '@/api/auth':{setupInitialPassword: async value => { requests.push(value); return outcome(); }}, '@/utils/navigation':{showToast: value=>toasts.push(value)}};
vm.runInNewContext(ts.transpileModule(script,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText,{exports,require:name=>imports[name]||{},uni,console,Error});
const page = exports.default.setup({}, {expose() {}});
await hooks.load();
page.password.value='weak'; page.confirmPassword.value='weak'; await page.submit(); assert.equal(requests.length,0);
page.password.value='NewPassword7';page.confirmPassword.value='Mismatch7';await page.submit();assert.equal(requests.length,0);
page.confirmPassword.value='NewPassword7'; outcome=async()=>{throw new Error('临时密码已过期，请联系管理员重新生成');}; await page.submit();
assert.equal(savedToken,'restricted-token');assert.equal(page.password.value,'NewPassword7');assert.equal(navigations.length,0);assert.ok(toasts.at(-1).includes('已过期'));
let finish;outcome=()=>new Promise(resolve=>{finish=resolve;});const first=page.submit();await Promise.resolve();const count=requests.length;await page.submit();assert.equal(requests.length,count,'repeated tap must not submit twice');
finish({token:'fresh-token'});await first;assert.equal(savedToken,'fresh-token');assert.equal(page.password.value,'');assert.equal(page.confirmPassword.value,'');assert.deepEqual(navigations,['todo']);assert.ok(toasts.at(-1).includes('绑定微信'));

const appSource=await readFile(new URL('../src/App.vue',import.meta.url),'utf8');
const appScript=compileScript(parse(appSource).descriptor,{id:'initial-password-app-test'}).content;
const callbacks={},calls=[];auth.state.user.initialPasswordSetupRequired=true;
const appImports={vue:{...Vue,watch:()=>{}},'@dcloudio/uni-app':{onShow:fn=>callbacks.show=fn,onHide:fn=>callbacks.hide=fn},'@/stores/auth':{useAuthStore:()=>auth},'@/stores/project':{useProjectStore:()=>({state:{currentProjectId:1}})},'@/stores/todo':{useTodoStore:()=>({loadSummary:()=>calls.push('summary')})},'@/api/project':{getProjectModules:()=>calls.push('modules')},'@/api/request':{getToken:()=>savedToken},'@/utils/moduleNetwork':{setNetworkProject:()=>{}}};
const appExports={};let timer;
vm.runInNewContext(ts.transpileModule(appScript,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText,{exports:appExports,require:name=>appImports[name]||{},Error,uni:{...uni,onNetworkStatusChange(){},$emit(){}},getCurrentPages:()=>[{route:'pages/quality/index'}],setInterval:fn=>{timer=fn;return 1;},clearInterval(){},setTimeout:fn=>fn(),console});
appExports.default.setup({}, {expose(){}});callbacks.show();await Promise.resolve();timer();await Promise.resolve();assert.deepEqual(calls,[],'initial setup must not poll project modules or to-do data');assert.equal(navigations.at(-1),'/pages/initial-password/index');callbacks.hide();
console.log('首次改密通过：校验、失败保留、重复点击、会话更新、清空密码、微信引导及后台业务轮询拦截。');
