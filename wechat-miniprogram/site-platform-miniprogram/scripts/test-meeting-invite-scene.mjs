import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { extractMeetingInviteToken } from '../src/utils/meetingInviteScene.ts';
import { createMeetingInviteRefreshCoordinator } from '../src/utils/meetingInviteRefresh.ts';

const token = 'AbCdEfGhIjKlMnOpQrStUvWx';

assert.equal(extractMeetingInviteToken({ scene: `M:${token}` }), token);
assert.equal(extractMeetingInviteToken({ scene: encodeURIComponent(`M:${token}`) }), token);
assert.equal(extractMeetingInviteToken({ token }), token);
assert.equal(extractMeetingInviteToken({ scene: `S:${token}` }), '');
assert.equal(extractMeetingInviteToken({ scene: 'M:too-short' }), '');
assert.equal(extractMeetingInviteToken({ scene: '%E0%A4%A' }), '');

const lifecycle = createMeetingInviteRefreshCoordinator();
lifecycle.markLoadStarted();
assert.equal(lifecycle.shouldRefreshOnShow(), false, '首次 onShow 不应重复触发 onLoad 初始化');
assert.equal(lifecycle.shouldRefreshOnShow(), true, '页面恢复时应重新确认会议状态');

let runs = 0;
let releaseFirstRun;
const firstRunBlocked = new Promise((resolve) => { releaseFirstRun = resolve; });
const firstRun = lifecycle.run(async () => {
  runs += 1;
  await firstRunBlocked;
});
const duplicateRun = lifecycle.run(async () => {
  runs += 1;
});
assert.equal(runs, 0, '初始化任务应在微任务中统一调度');
await Promise.resolve();
assert.equal(runs, 1, '并发 onLoad/onShow 只能执行一次初始化');
releaseFirstRun();
await Promise.all([firstRun, duplicateRun]);
await lifecycle.run(async () => { runs += 1; });
assert.equal(runs, 2, '前一次初始化完成后应允许恢复刷新');

const earlyShow = createMeetingInviteRefreshCoordinator();
assert.equal(earlyShow.shouldRefreshOnShow(), false, 'onLoad 前的首次 onShow 也必须安全跳过');
earlyShow.markLoadStarted();
assert.equal(earlyShow.shouldRefreshOnShow(), true, '完成 onLoad 后的下一次恢复必须刷新');

const pageSource = await readFile(new URL('../src/pages/public/meeting-invite.vue', import.meta.url), 'utf8');
assert.match(pageSource, /<AppNavBar title="会议访客登记" @back="goBack" \/>/, '会议页返回箭头必须绑定返回处理');
assert.match(pageSource, /class="meeting-content"/, '会议页内容必须使用统一安全区容器');
assert.doesNotMatch(pageSource, /class="two-columns"/, '真机登记字段不得继续使用固定双列布局');
assert.match(
  pageSource,
  /\.meeting-field input\s*\{[\s\S]*?height:\s*78rpx;[\s\S]*?padding:\s*0 20rpx;/,
  '微信原生 input 必须显式设置高度并取消上下内边距，防止 iOS 真机文字裁切'
);
assert.match(
  pageSource,
  /\.invite-no\s*\{[\s\S]*?word-break:\s*break-all;/,
  '长会议邀请编号必须允许安全换行'
);
assert.match(
  pageSource,
  /\.travel-tabs button\s*\{[\s\S]*?height:\s*72rpx;[\s\S]*?font-size:\s*24rpx;/,
  '出行方式按钮必须覆盖微信原生按钮尺寸'
);
assert.match(
  pageSource,
  /\.submit-button\s*\{[\s\S]*?height:\s*84rpx;[\s\S]*?font-size:\s*26rpx;/,
  '提交按钮必须显式设置真机高度与字号'
);

console.log('meeting invite scene tests passed');
