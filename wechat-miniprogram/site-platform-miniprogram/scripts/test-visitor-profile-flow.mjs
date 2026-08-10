import assert from 'node:assert/strict';
import {
  LatestProfileSelectionGuard,
  loadOptionalVisitorProfiles,
  submitWithVisitorSessionFallback,
  withoutVisitorProfileContext
} from '../src/utils/visitorProfileFlow.ts';

const selectionGuard = new LatestProfileSelectionGuard();
const first = selectionGuard.begin('VP_A');
assert.ok(first);
assert.equal(selectionGuard.begin('VP_A'), null, '同一资料加载中不得重复发起请求');
const second = selectionGuard.begin('VP_B');
assert.ok(second);
assert.equal(selectionGuard.isCurrent(first), false, 'A 的迟到响应不得覆盖 B');
assert.equal(selectionGuard.isCurrent(second), true);
assert.equal(selectionGuard.finish(first), false);
assert.equal(selectionGuard.activeKey(), 'VP_B');
assert.equal(selectionGuard.finish(second), true);
assert.equal(selectionGuard.activeKey(), '');

let listCalled = false;
const unavailableAtSession = await loadOptionalVisitorProfiles(
  async () => { throw new Error('微信身份不可用'); },
  async () => {
    listCalled = true;
    return [];
  }
);
assert.deepEqual(unavailableAtSession, { available: false });
assert.equal(listCalled, false);

const unavailableAtList = await loadOptionalVisitorProfiles(
  async () => ({ visitorSessionToken: 'session-token' }),
  async () => { throw Object.assign(new Error('会话过期'), { code: 401 }); }
);
assert.deepEqual(unavailableAtList, { available: false });

const originalPayload = {
  inviteToken: 'invite-token',
  visitorCompany: '测试单位',
  contactName: '测试联系人',
  contactPhone: 'phone-value',
  contactIdCard: 'contact-id-value',
  companions: [{ personName: '同行人员', idCard: 'companion-id-value' }],
  travelMode: 'OTHER',
  visitorRemark: '本次填写内容必须保留',
  privacyAgreed: true,
  profileAction: 'UPDATE',
  profileCode: 'VP_A',
  profileName: '历史资料',
  profileRetentionAgreed: true,
  profileVersion: 3
};
const manualPayload = withoutVisitorProfileContext(originalPayload);
assert.equal(manualPayload.visitorCompany, originalPayload.visitorCompany);
assert.deepEqual(manualPayload.companions, originalPayload.companions);
assert.equal(manualPayload.visitorRemark, originalPayload.visitorRemark);
for (const key of ['profileAction', 'profileCode', 'profileName', 'profileRetentionAgreed', 'profileVersion']) {
  assert.equal(key in manualPayload, false, `手工回退载荷不得保留 ${key}`);
}
assert.equal(originalPayload.profileCode, 'VP_A', '回退不得修改原始表单快照');

const submitCalls = [];
let expiredCallbackCount = 0;
const fallbackResult = await submitWithVisitorSessionFallback({
  payload: originalPayload,
  visitorSessionToken: 'expired-session',
  submit: async (payload, sessionToken) => {
    submitCalls.push({ payload, sessionToken });
    if (submitCalls.length === 1) throw Object.assign(new Error('会话失效'), { code: 401 });
    return { status: 'SUBMITTED' };
  },
  confirmManualFallback: async () => true,
  onSessionExpired: () => { expiredCallbackCount += 1; }
});
assert.equal(fallbackResult.status, 'submitted-manually');
assert.equal(expiredCallbackCount, 1);
assert.equal(submitCalls.length, 2);
assert.equal(submitCalls[1].sessionToken, undefined);
assert.equal(submitCalls[1].payload.contactPhone, originalPayload.contactPhone);
assert.equal('profileCode' in submitCalls[1].payload, false);

let cancelledSubmitCount = 0;
const cancelled = await submitWithVisitorSessionFallback({
  payload: originalPayload,
  visitorSessionToken: 'expired-session',
  submit: async () => {
    cancelledSubmitCount += 1;
    throw Object.assign(new Error('会话失效'), { statusCode: 403 });
  },
  confirmManualFallback: async () => false,
  onSessionExpired: () => undefined
});
assert.equal(cancelled.status, 'cancelled');
assert.equal(cancelledSubmitCount, 1, '用户返回核对时不得再次提交');

const sourceOnlyCalls = [];
const sourceOnlyResult = await submitWithVisitorSessionFallback({
  payload: { ...originalPayload, profileAction: 'NONE' },
  visitorSessionToken: 'expired-session',
  submit: async (payload, sessionToken) => {
    sourceOnlyCalls.push({ payload, sessionToken });
    if (sourceOnlyCalls.length === 1) throw Object.assign(new Error('会话失效'), { code: 403 });
    return { status: 'SUBMITTED' };
  },
  confirmManualFallback: async () => true,
  onSessionExpired: () => undefined
});
assert.equal(sourceOnlyResult.status, 'submitted-manually');
assert.equal(sourceOnlyCalls.length, 2);
assert.equal('profileCode' in sourceOnlyCalls[1].payload, false, '资料来源也必须在无会话回退时清理');

const plainManualCalls = [];
const plainManualResult = await submitWithVisitorSessionFallback({
  payload: manualPayload,
  visitorSessionToken: 'expired-session',
  submit: async (payload, sessionToken) => {
    plainManualCalls.push({ payload, sessionToken });
    if (plainManualCalls.length === 1) throw Object.assign(new Error('会话失效'), { statusCode: 401 });
    return { status: 'SUBMITTED' };
  },
  confirmManualFallback: async () => true,
  onSessionExpired: () => undefined
});
assert.equal(plainManualResult.status, 'submitted-manually');
assert.equal(plainManualCalls.length, 2, '未使用资料但携带过期会话时也应去除会话后重试');
assert.equal(plainManualCalls[1].sessionToken, undefined);
assert.equal(plainManualCalls[1].payload.contactName, originalPayload.contactName);

console.log('visitor profile race, optional loading and session fallback: OK');
