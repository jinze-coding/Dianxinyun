import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import {
  createSiteAccessRequestGuard,
  isMeetingInvitationOpen,
  meetingRequestContext,
  normalizeInvitationStatusForType,
  normalizePageAfterRemoval,
} from './meetingManagement.js';

const pageSource = readFileSync(new URL('./index.jsx', import.meta.url), 'utf8');
const panelSource = readFileSync(new URL('./MeetingRegistrationPanel.jsx', import.meta.url), 'utf8');
const checkinSource = readFileSync(new URL('./MeetingCheckinPanel.jsx', import.meta.url), 'utf8');
const siteAccessServiceSource = readFileSync(new URL('../../services/siteAccess.js', import.meta.url), 'utf8');
const guardSource = readFileSync(new URL('./GuardVisitPanel.jsx', import.meta.url), 'utf8');
const baseStyles = readFileSync(new URL('./index.css', import.meta.url), 'utf8');
const meetingStyles = readFileSync(new URL('./meetingVisits.css', import.meta.url), 'utf8');

test('site access requests reject stale responses and project switches', () => {
  const guard = createSiteAccessRequestGuard();
  const first = guard.begin(3);
  const second = guard.begin(3);

  assert.equal(guard.isCurrent(first, 3), false);
  assert.equal(guard.isCurrent(second, 3), true);
  assert.equal(guard.isCurrent(second, 4), false);
  guard.invalidate();
  assert.equal(guard.isCurrent(second, 3), false);
});

test('meeting request context isolates both project and invitation', () => {
  assert.equal(meetingRequestContext(3, 7), '3:7');
  assert.notEqual(meetingRequestContext(3, 7), meetingRequestContext(3, 8));
  assert.notEqual(meetingRequestContext(3, 7), meetingRequestContext(4, 7));
});

test('invitation status is normalized when the invitation type changes', () => {
  assert.equal(normalizeInvitationStatusForType('MEETING', 'SUBMITTED'), '');
  assert.equal(normalizeInvitationStatusForType('MEETING', 'OPEN'), 'OPEN');
  assert.equal(normalizeInvitationStatusForType('SINGLE', 'OPEN'), '');
  assert.equal(normalizeInvitationStatusForType('SINGLE', 'PENDING'), 'PENDING');
  assert.equal(normalizeInvitationStatusForType('', 'OPEN'), 'OPEN');
});

test('removing the last record normalizes an out-of-range page', () => {
  assert.equal(normalizePageAfterRemoval(2, 20, 20), 1);
  assert.equal(normalizePageAfterRemoval(3, 41, 20), 3);
  assert.equal(normalizePageAfterRemoval(1, 0, 20), 1);
});

test('meeting open state requires OPEN status and a future cutoff', () => {
  const now = new Date('2026-08-31T10:00:00').getTime();
  assert.equal(isMeetingInvitationOpen({ status: 'OPEN', visitEndTime: '2026-08-31T10:01:00' }, now), true);
  assert.equal(isMeetingInvitationOpen({ status: 'OPEN', visitEndTime: '2026-08-31T10:00:00' }, now), false);
  assert.equal(isMeetingInvitationOpen({ status: 'VOIDED', visitEndTime: '2026-08-31T10:01:00' }, now), false);
});

test('site access Web sources apply request guards and correction field limits', () => {
  assert.match(pageSource, /invitationListRequestGuardRef/);
  assert.match(pageSource, /invitationDetailRequestGuardRef/);
  assert.match(panelSource, /registrationListRequestGuardRef/);
  assert.match(panelSource, /disabled=\{editing\.form\.companions\.length >= 49\}/);
  assert.match(panelSource, /maxLength="500" value=\{editing\.form\.visitorRemark\}/);
  assert.match(panelSource, /if \(!open\) return/);
});

test('meeting registration drawer content keeps the same horizontal inset as detail sections', () => {
  assert.match(
    meetingStyles,
    /\.site-access-meeting-panel\s*\{[^}]*margin:\s*18px 16px 0;/,
  );
});

test('visitor detail views share a wide readable layout across invitations and guard registrations', () => {
  assert.match(pageSource, /site-access-drawer site-access-visitor-drawer/);
  assert.match(guardSource, /site-access-drawer site-access-visitor-drawer/);
  assert.match(guardSource, /site-access-person-contact-fields/);
  assert.match(panelSource, /className="site-access-visitor-modal"/);
  assert.match(panelSource, /width=\{920\}/);
  assert.match(panelSource, /site-access-person-contact-fields/);
  assert.match(baseStyles, /\.site-access-visitor-drawer\s*\{[^}]*width:\s*min\(1120px, 96vw\)/s);
  assert.match(baseStyles, /\.site-access-visitor-drawer,\s*\.site-access-visitor-modal\s*\{[^}]*font-size:\s*14px/s);
  assert.match(meetingStyles, /min-width:\s*980px/);
  assert.match(meetingStyles, /white-space:\s*normal/);
  assert.match(meetingStyles, /font-size:\s*14px/);
});

test('meeting detail explains the complete reservation and venue check-in loop', () => {
  assert.match(panelSource, /创建会议/);
  assert.match(panelSource, /分享邀请/);
  assert.match(panelSource, /预约登记/);
  assert.match(panelSource, /会场签到/);
  assert.match(panelSource, /导出名单/);
  assert.match(panelSource, /预约登记管理/);
  assert.match(panelSource, /会场签到管理/);
  assert.match(meetingStyles, /\.meeting-closed-loop/);
  assert.match(meetingStyles, /\.meeting-workbench-tabs/);
});

test('venue workbench exposes settings statistics evidence and controlled staff actions', () => {
  assert.match(checkinSource, /loadRequestGuardRef/);
  assert.match(checkinSource, /mutationRequestGuardRef/);
  assert.match(checkinSource, /exportRequestGuardRef/);
  assert.match(checkinSource, /meetingRequestContext\(projectId, invitation\.id\)/);
  assert.match(checkinSource, /会场签到工作台/);
  assert.match(checkinSource, /预约人数/);
  assert.match(checkinSource, /预约已签到/);
  assert.match(checkinSource, /预约未签到/);
  assert.match(checkinSource, /现场补录/);
  assert.match(checkinSource, /预约签到率/);
  assert.match(checkinSource, /定位异常仅标识，不阻断签到/);
  assert.match(checkinSource, /人工补签/);
  assert.match(checkinSource, /撤销签到/);
  assert.match(checkinSource, /更正参会人员/);
  assert.match(checkinSource, /导出签到名单/);
});

test('Web service routes keep invitation qr and attendance export distinct', () => {
  assert.match(siteAccessServiceSource, /meeting-check-in\/settings/);
  assert.match(siteAccessServiceSource, /meeting-check-in\/mini-code/);
  assert.match(siteAccessServiceSource, /meeting-attendees\/walk-ins/);
  assert.match(siteAccessServiceSource, /meeting-attendance\/export/);
});

test('invitation list leads with the topic while keeping reference numbers outside the list', () => {
  const invitationTableSource = pageSource.slice(
    pageSource.indexOf('<section className="site-access-table-card">'),
    pageSource.indexOf('<div className="site-access-pagination">'),
  );

  assert.match(pageSource, /placeholder="邀请主题\/来访事由、邀请编号、单位、姓名、车牌、接待人"/);
  assert.match(invitationTableSource, /邀请主题 \/ 类型/);
  assert.match(invitationTableSource, /const invitationTopic = String\(item\.purpose \|\| ''\)\.trim\(\) \|\| '未填写主题'/);
  assert.match(invitationTableSource, /className="link site-access-invitation-topic"/);
  assert.match(invitationTableSource, /title=\{invitationTopic\}/);
  assert.match(invitationTableSource, />\{invitationTopic\}<\/button>/);
  assert.doesNotMatch(invitationTableSource, /\{item\.inviteNo\}/);
  assert.match(pageSource, /<strong>\{detail\.inviteNo\}<\/strong>/);
  assert.match(baseStyles, /\.site-access-page button\.site-access-invitation-topic\s*\{[^}]*-webkit-line-clamp:\s*2/s);
  assert.match(baseStyles, /\.site-access-page button\.site-access-invitation-topic\s*\{[^}]*overflow-wrap:\s*anywhere/s);
});
