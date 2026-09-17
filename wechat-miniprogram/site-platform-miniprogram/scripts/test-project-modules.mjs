import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import ts from 'typescript';

async function load(file, imports, globals) {
  let source = await readFile(new URL(`../src/${file}`, import.meta.url), 'utf8');
  if (file.endsWith('.vue')) source = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];
  const code = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText;
  const exports = {};
  vm.runInNewContext(code, { exports, require: (name) => { assert.ok(name in imports, name); return imports[name]; }, console, ...globals });
  return exports;
}
const events = new Map(); const tasks = [];
const uni = { getStorageSync: () => null, setStorageSync() {}, removeStorageSync() {}, $on: (key, fn) => events.set(key, fn),
  request(options) { const task = { aborted: false, abort() { this.aborted = true; options.complete?.({}); }, options }; tasks.push(task); return task; },
};
uni.uploadFile = uni.request; uni.downloadFile = uni.request;
const net = await load('utils/moduleNetwork.ts', {}, { uni, getCurrentPages: () => [{ route: 'pages/quality/index' }] });
net.setNetworkProject(1);
const a = net.moduleRequest({ url: '/api/v1/quality/issues', data: { projectId: 1 } });
const b = net.moduleRequest({ url: '/api/v1/quality/issues', data: { projectId: 2 } });
const c = net.moduleUpload({ url: '/api/v1/files/upload', formData: { projectId: 1, businessType: 'QUALITY_ISSUE_PENDING' } });
const d = net.moduleDownload({ url: '/api/v1/files/100/download' });
const e = net.moduleRequest({ url: '/api/v1/document-folders', data: { projectId: 1 } });
const done = net.moduleRequest({ url: '/api/v1/quality/issues', data: { projectId: 1 } }); done.options.complete({});
events.get('project-module-availability')({ projectId: 1, enabledBusinessModules: ['DOCUMENT'] });
for (const task of [a, c, d]) assert.equal(task.aborted, true);
for (const task of [b, e, done]) assert.equal(task.aborted, false);
assert.equal(net.moduleForRequest('/api/v1/auth/user-info'), null);

const project = { state: { currentProjectId: 1 } };
const pendingUsers = [];
const auth = await load('stores/auth.ts', {
  vue: { reactive: (value) => value }, './project': { useProjectStore: () => project },
  '@/api/auth': { getCurrentUser: () => new Promise(resolve => pendingUsers.push(resolve)) }, '@/api/request': { getToken: () => 'test-session', USE_MOCK: false },
}, { uni });
const store = auth.useAuthStore();
for (const roles of [['PROJECT_MANAGER'], ['PLATFORM_ADMIN']]) {
  store.state.user = { roles, menus: [{ menuCode: 'MINI_QUALITY' }, { menuCode: 'MINI_DOCUMENT' }], projectContexts: [
    { projectId: 1, accessStatus: 'ACTIVE', enabledBusinessModules: ['DOCUMENT'], menuCodes: ['MINI_DOCUMENT'], permissionCodes: ['document.view'] },
    { projectId: 2, accessStatus: 'ACTIVE', enabledBusinessModules: ['QUALITY'], menuCodes: ['MINI_QUALITY'], permissionCodes: ['quality.manage'] },
    { projectId: 3, accessStatus: 'ACTIVE', enabledBusinessModules: [], menuCodes: [], permissionCodes: [] },
  ] };
  project.state.currentProjectId = 1;
  assert.equal(store.canAccessRoot('/pages/quality/index'), false);
  assert.equal(store.canAccessRoot('/pages/documents/index'), true);
  assert.equal(store.hasProjectPermission(1, 'quality.manage'), false);
  project.state.currentProjectId = 2;
  assert.equal(store.canAccessRoot('/pages/quality/index'), true);
  project.state.currentProjectId = 3;
  for (const path of ['documents', 'inspection', 'quality', 'safety-committee']) assert.equal(store.canAccessRoot(`/pages/${path}/index`), false);
  assert.equal(store.canAccessRoot('/pages/todo/index'), true);
  assert.equal(store.canAccessRoot('/pages/profile/index'), true);
  assert.equal(store.firstAuthorizedPage(), '/pages/todo/index');
}
for (const roles of [['PROJECT_MANAGER'], ['PLATFORM_ADMIN']]) {
  store.state.user = { roles, projectContexts: [
    { projectId: 1, inboxEntryVisible: false, enabledBusinessModules: ['DOCUMENT', 'SAFETY_COMMITTEE'], menuCodes: ['MINI_DOCUMENT', 'MINI_SAFETY_COMMITTEE'] },
    { projectId: 2, inboxEntryVisible: true, enabledBusinessModules: [], menuCodes: [] },
    { projectId: 3, inboxEntryVisible: false, enabledBusinessModules: [], menuCodes: [] }
  ] };
  project.state.currentProjectId = 1;
  assert.equal(store.canAccessRoot('/pages/todo/index'), false);
  assert.equal(store.firstAuthorizedPage(), '/pages/safety-committee/index');
  store.state.user.projectContexts[0].enabledBusinessModules = ['DOCUMENT'];
  assert.equal(store.firstAuthorizedPage(), '/pages/documents/index');
  project.state.currentProjectId = 2;
  assert.equal(store.canAccessRoot('/pages/todo/index'), true);
  assert.equal(store.firstAuthorizedPage(), '/pages/todo/index');
  project.state.currentProjectId = 3;
  assert.equal(store.firstAuthorizedPage(), '/pages/profile/index');
}
const firstRefresh = store.loadUser(), latestRefresh = store.loadUser();
pendingUsers[1]({ id: 2, projectContexts: [] }); await latestRefresh;
pendingUsers[0]({ id: 1, projectContexts: [] }); await firstRefresh;
assert.equal(store.state.user.id, 2, '迟到用户信息不能覆盖更新结果');
const logoutRefresh = store.loadUser(); store.clearLocalSession();
pendingUsers[2]({ id: 1, projectContexts: [] }); await logoutRefresh;
assert.equal(store.state.user, null, '退出后迟到响应不能恢复登录');

const lifecycle = {}, moduleReads = [], availability = [], navigations = [];
let interval, watcher, online, userLoads = 0;
const pageAuth = { state: { user: { projectContexts: [{ projectId: 1, moduleConfigVersion: 1 }] } },
  requiresInitialPasswordSetup() { return Boolean(this.state.user?.initialPasswordSetupRequired); },
  async loadUser() { userLoads++; return this.state.user; }, canAccessRoot: () => false,
  isProjectModuleEnabled: () => false, firstAuthorizedPage: () => '/pages/todo/index' };
project.state.currentProjectId = 1;
await load('App.vue', {
  '@dcloudio/uni-app': { onShow: fn => lifecycle.show = fn, onHide: fn => lifecycle.hide = fn },
  vue: { watch: (_source, fn) => watcher = fn },
  '@/utils/moduleNetwork': { setNetworkProject() {} }, '@/stores/project': { useProjectStore: () => project },
  '@/api/project': { getProjectModules: id => new Promise((resolve, reject) => moduleReads.push({ id, resolve, reject })) },
  '@/stores/todo': { useTodoStore: () => ({ loadSummary() {} }) },
  '@/api/request': { getToken: () => 'session' }, '@/stores/auth': { useAuthStore: () => pageAuth },
}, { uni: { onNetworkStatusChange: fn => online = fn, $emit: (_event, data) => availability.push(data), showToast() {},
  switchTab: options => navigations.push(options.url), reLaunch: options => navigations.push(options.url) },
  getCurrentPages: () => [{ route: 'pages/safety-committee/index', options: {} }],
  setInterval: fn => { interval = fn; return 1; }, clearInterval() {}, setTimeout() {},
});
const flush = async () => { for (let i = 0; i < 8; i++) await Promise.resolve(); };
lifecycle.show();
moduleReads[0].resolve({ projectId: 1, moduleConfigVersion: 1, enabledBusinessModules: [] }); await flush();
assert.equal(userLoads, 1, '恢复前台即使当前版本相同也更新其他项目上下文');
assert.equal(navigations.at(-1), '/pages/todo/index');
interval(); // A 请求尚未返回时切换 B。
project.state.currentProjectId = 2; watcher(2);
moduleReads[2].resolve({ projectId: 2, moduleConfigVersion: 2, enabledBusinessModules: ['DOCUMENT'] }); await flush();
moduleReads[1].resolve({ projectId: 1, moduleConfigVersion: 99, enabledBusinessModules: [] }); await flush();
assert.equal(availability.at(-1).projectId, 2);
assert.equal(userLoads, 2, '旧项目响应不能刷新或覆盖新项目');
interval(); lifecycle.hide();
moduleReads[3].resolve({ projectId: 2, moduleConfigVersion: 3, enabledBusinessModules: [] }); await flush();
assert.equal(userLoads, 2, '切后台后忽略未完成请求');
online({ isConnected: true }); assert.equal(moduleReads.length, 4);
lifecycle.show(); moduleReads[4].reject(new Error('offline')); await flush();
assert.equal(userLoads, 2, '断网不清空已有权限上下文');
online({ isConnected: true });
moduleReads[5].resolve({ projectId: 2, moduleConfigVersion: 3, enabledBusinessModules: ['DOCUMENT'] }); await flush();
assert.equal(userLoads, 3, '网络恢复立即重试');
console.log('项目模块专项通过：按当前项目显示、管理员开关、空菜单无回退、基础入口、关闭时取消请求/上传/下载且不影响其他项目。');
console.log('前台/网络恢复刷新、项目切换迟到响应、后台暂停、用户信息并发和退出保护通过。');

const summaryReads = [];
let sessionToken = 'session';
store.state.user = { projectContexts: [{ projectId: 1, inboxEntryVisible: true }, { projectId: 2, inboxEntryVisible: false }] };
project.state.currentProjectId = 1;
const todoModule = await load('stores/todo.ts', {
  vue: { reactive: value => value }, './auth': { useAuthStore: () => store }, './project': { useProjectStore: () => project },
  '@/api/request': { getToken: () => sessionToken },
  '@/api/todo': { getTodoSummary: () => new Promise((resolve, reject) => summaryReads.push({ resolve, reject })) },
}, { uni });
const todos = todoModule.useTodoStore();
const oldSummary = todos.loadSummary();
project.state.currentProjectId = 2;
await todos.loadSummary();
assert.equal(summaryReads.length, 1, '隐藏入口不请求角标');
summaryReads[0].resolve({ pendingCount: 9, ccCount: 0, unreadNotificationCount: 1, badgeCount: 10 });
await oldSummary;
assert.equal(todos.state.summary.badgeCount, 0, '切项目后的迟到结果不能恢复角标');
project.state.currentProjectId = 1;
const freshSummary = todos.loadSummary();
summaryReads[1].resolve({ pendingCount: 3, ccCount: 0, unreadNotificationCount: 1, badgeCount: 4 }); await freshSummary;
assert.equal(todos.state.summary.badgeCount, 4, '切回显示项目重新加载');
const loggedOut = todos.loadSummary(); sessionToken = '';
summaryReads[2].resolve({ badgeCount: 99 }); await loggedOut;
assert.equal(todos.state.summary.badgeCount, 4, '退出后的迟到结果丢弃');
console.log('待办入口开关、默认落点、权限回退、角标暂停与迟到响应保护通过。');
