import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import ts from 'typescript';
import * as Vue from 'vue';
import { parse, compileScript } from '@vue/compiler-sfc';
const root = new URL('../src/', import.meta.url);
const toasts = [], calls = [], navigations = [];
let denied = false, pendingPrivacy;
const wx = {
  requirePrivacyAuthorize: options => { calls.push('privacy'); if (pendingPrivacy) pendingPrivacy(options); else if (denied) options.fail(); else options.success(); },
  openPrivacyContract: options => { calls.push('readPrivacy'); if (denied) options.fail(); }
};
const uni = { navigateTo: options => navigations.push(options.url), redirectTo: options => navigations.push(options.url), reLaunch: options => navigations.push(options.url), showModal: () => {}, setStorageSync: () => calls.push('storage') };
function compile(source, dependencies) {
  const exports = {};
  const mp = source.replace(/\/\/ #ifdef H5[\s\S]*?\/\/ #endif/g, '');
  vm.runInNewContext(ts.transpileModule(mp, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText, {
    exports, require: name => { assert.ok(name in dependencies, `missing mock ${name}`); return dependencies[name]; },
    uni, wx, console, Error, getCurrentPages: () => [], setTimeout, clearTimeout
  });
  return exports;
}
const consent = compile(await readFile(new URL('utils/agreementConsent.ts', root), 'utf8'), { '@/utils/navigation': { showToast: message => toasts.push(message) } });
const auth = { login: async () => calls.push('login'), completeLogin: async () => calls.push('complete'), navigateAfterLogin: () => calls.push('navigate'), loadUser: async () => calls.push('restore'), clearLocalSession() {} };
async function page(path, extra = {}) {
  const hooks = {};
  const source = await readFile(new URL(path, root), 'utf8');
  const script = compileScript(parse(source).descriptor, { id: path }).content;
  const dependencies = { vue: { ...Vue, onBeforeUnmount: fn => hooks.unmount = fn }, '@dcloudio/uni-app': { onLoad: fn => hooks.load = fn, onShow: fn => hooks.show = fn },
    '@/stores/auth': { useAuthStore: () => auth }, '@/api/request': { getToken: () => '' },
    '@/api/auth': { miniWechatLogin: async () => { calls.push('wechatLogin'); return { token: 'synthetic' }; }, bindWechatAccount: async () => { calls.push('bind'); return { token: 'synthetic' }; }, requestWechatProjectAccess: async () => ({}) },
    '@/api/registration': { getRegistrationCaptcha: async () => ({}), searchRegistrationProjects: async () => [], submitRegistrationApplication: async () => { calls.push('register'); return { statusQueryToken: 'synthetic' }; } },
    '@/utils/navigation': { showToast: message => toasts.push(message) }, '@/utils/wechat': { getFreshWechatCode: async () => { calls.push('wxCode'); return 'synthetic'; } },
    '@/utils/agreementConsent': consent, '@/components/AgreementConsent.vue': {}, '@/components/AppNavBar.vue': {}, ...extra };
  const result = compile(script, dependencies).default.setup({}, { expose() {} });
  return { state: result, hooks, source };
}
const login = await page('pages/login/index.vue');
assert.equal(login.state.agreementsAccepted.value, false);
login.state.username.value = 'synthetic-user'; login.state.password.value = 'Synthetic7';
await login.hooks.show(); await login.state.submit(); await login.state.wechatLogin(); assert.deepEqual(calls, [], 'no auth/WeChat/privacy calls before opt-in');
consent.openPrivacyPolicy(); assert.deepEqual(calls, ['readPrivacy']); assert.equal(login.state.agreementsAccepted.value, false, 'reading privacy does not opt in'); calls.length = 0;
login.state.agreementsAccepted.value = true; denied = true; await login.state.submit(); assert.deepEqual(calls, ['privacy'], 'denying native privacy cannot log in'); calls.length = 0;
denied = false; pendingPrivacy = () => {}; let finish; pendingPrivacy = options => finish = options;
const waiting = login.state.submit(); await login.state.wechatLogin(); assert.deepEqual(calls, ['privacy'], 'parallel login is blocked while native dialog is pending');
finish.success(); await waiting; assert.deepEqual(calls, ['privacy', 'login', 'navigate']); pendingPrivacy = undefined; calls.length = 0;
await login.state.wechatLogin(); assert.deepEqual(calls, ['privacy', 'wxCode', 'wechatLogin', 'complete', 'navigate']); calls.length = 0;
login.state.agreementsAccepted.value = false; await login.state.submit(); assert.deepEqual(calls, []);
assert.equal((await page('pages/login/index.vue')).state.agreementsAccepted.value, false, 'new login page never inherits consent');
const register = await page('pages/register/index.vue');
register.state.form.realName = '合成验证'; register.state.selectedProjects.value = [{ projectId: 1 }];
await register.state.submitApplication('synthetic-phone-code'); register.state.getPhone({ detail: { code: 'synthetic-phone-code' } }); assert.deepEqual(calls, []);
assert.match(register.source, /:open-type="agreementsAccepted \? 'getPhoneNumber' : ''"/);
assert.match(register.source, /:disabled="submitting \|\| !agreementsAccepted \|\| !selectedProjects.length"/);
register.state.agreementsAccepted.value = true; denied = true; await register.state.submitApplication('synthetic-phone-code'); assert.deepEqual(calls, ['privacy']); calls.length = 0;
denied = false; await register.state.submitApplication('synthetic-phone-code'); assert.deepEqual(calls, ['privacy', 'wxCode', 'register', 'storage']); calls.length = 0;
register.state.agreementsAccepted.value = false; register.state.switchMode(); register.state.form.phone = '19999000000'; register.state.form.password = 'Synthetic7'; register.state.form.confirmPassword = 'Synthetic7'; await register.state.submitApplication(); assert.deepEqual(calls, []);
register.state.agreementsAccepted.value = true; await register.state.submitApplication(); assert.deepEqual(calls, ['privacy', 'wxCode', 'register', 'storage']); calls.length = 0;
const bind = await page('pages/wechat-bind/index.vue'); bind.state.username.value = 'synthetic'; bind.state.password.value = 'Synthetic7';
await bind.state.bindExistingAccount(); assert.deepEqual(calls, []); bind.state.agreementsAccepted.value = true; await bind.state.bindExistingAccount(); assert.deepEqual(calls, ['privacy', 'wxCode', 'bind', 'complete', 'navigate']); calls.length = 0;
const component = await readFile(new URL('components/AgreementConsent.vue', root), 'utf8'); const emits = [];
const compiled = compile(compileScript(parse(component).descriptor, { id: 'consent' }).content, { vue: Vue, '@/utils/agreementConsent': consent });
const control = compiled.default.setup({ modelValue: false, disabled: false }, { expose() {}, emit: (event, value) => emits.push([event, value]) });
control.openTerms(); assert.deepEqual(emits, []); assert.equal(navigations.at(-1), '/pages/legal/index');
control.change({ detail: { value: ['agreed'] } }); control.change({ detail: { value: [] } }); assert.deepEqual(emits, [['update:modelValue', true], ['update:modelValue', false]]);
assert.doesNotMatch(component, /setStorageSync|@tap[^\n]*modelValue\s*=/);
for (const name of ['visitor-invite', 'meeting-invite', 'meeting-check-in', 'guard-visitor-register']) {
  const source = await readFile(new URL(`pages/public/${name}.vue`, root), 'utf8'); assert.match(source, /privacyAgreed = ref\(false\)/); assert.match(source, /if \(!privacyAgreed.value\)/);
}
const app = await readFile(new URL('App.vue', root), 'utf8'); assert.match(app, /'pages\/legal\/'/);
assert.equal(JSON.parse(await readFile(new URL('pages.json', root), 'utf8')).pages[0].path, 'pages/login/index');
console.log('协议同意通过：默认未选、阅读不勾选、取消、再次进入、密码/微信登录、手工/微信注册、原生手机号前置阻止、微信拒绝阻断、重复提交、绑定及四种访客入口。');
