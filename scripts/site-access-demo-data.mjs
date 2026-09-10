#!/usr/bin/env node
// Only synthetic fixtures in a dedicated local demo database; never targets dianxinyun or production.
import { readFileSync, writeFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { createHash, createDecipheriv, createCipheriv, createHmac, randomBytes } from 'node:crypto';
import { dirname, join } from 'node:path';

const configPath = process.env.DIANXINYUN_VISITOR_DEMO_CONFIG;
if (!configPath || process.env.DIANXINYUN_VISITOR_DEMO_CONFIRM !== 'LOCAL_DEMO_ONLY') {
  throw new Error('请明确设置外部演示配置文件及 DIANXINYUN_VISITOR_DEMO_CONFIRM=LOCAL_DEMO_ONLY');
}
const config = JSON.parse(readFileSync(configPath, 'utf8'));
if (!/^dianxinyun_visitor_demo_\d{8}$/.test(config.database)
    || config.baseUrl !== 'http://127.0.0.1:18080/api/v1') {
  throw new Error('只允许独立本地 visitor_demo 数据库及 18080 演示后端');
}
const sql = (statement) => execFileSync(process.env.MYSQL_BIN || 'mysql', [
  '--protocol=TCP', '-h', '127.0.0.1', '-P', '3306', '-u', 'root', '-N', '--batch', config.database
], { input: statement, encoding: 'utf8', env: { ...process.env, MYSQL_PWD: '' } }).trim();
const quote = (value) => `'${String(value).replaceAll('\\', '\\\\').replaceAll("'", "''")}'`;
const number = (value) => { if (!Number.isSafeInteger(Number(value)) || Number(value) < 1) throw new Error('记录编号无效'); return Number(value); };
let auth = '';
async function api(path, body, visitorSession, method) {
  const headers = { 'Content-Type': 'application/json' };
  if (visitorSession) headers['X-Visitor-Session'] = visitorSession;
  else if (auth && !path.startsWith('/public/')) headers.Authorization = `Bearer ${auth}`;
  const response = await fetch(config.baseUrl + path, {
    method: method || (body === undefined ? 'GET' : 'POST'), headers,
    body: body === undefined ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(20000)
  });
  const value = await response.json();
  if (!response.ok || value.code !== 200) throw new Error(`${path}: ${response.status} ${value.message || '请求失败'}`);
  return value.data;
}
function decrypt(encrypted) {
  if (!encrypted.startsWith('v1:')) throw new Error('演示令牌加密版本不匹配');
  const bytes = Buffer.from(encrypted.slice(3), 'base64url');
  const decipher = createDecipheriv('aes-256-gcm', createHash('sha256').update(config.visitorKey).digest(), bytes.subarray(0, 12));
  decipher.setAuthTag(bytes.subarray(-16)); decipher.setAAD(Buffer.from('v1'));
  return Buffer.concat([decipher.update(bytes.subarray(12, -16)), decipher.final()]).toString('utf8');
}
const encryptionKey = createHash('sha256').update(config.visitorKey).digest();
function encrypt(plain) {
  const iv = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', encryptionKey, iv);
  cipher.setAAD(Buffer.from('v1'));
  const content = Buffer.concat([cipher.update(plain, 'utf8'), cipher.final()]);
  return 'v1:' + Buffer.concat([iv, content, cipher.getAuthTag()]).toString('base64url');
}
const stamp = (date) => new Date(date.getTime() + 8 * 3600000).toISOString().slice(0, 19);
const today = new Date();
const dayStart = new Date(stamp(today).slice(0, 10) + 'T00:00:00+08:00');
const day = (offset, hour) => new Date(dayStart.getTime() + (offset * 24 + hour) * 3600000);
const end = day(0, 23.99);
const marker = '[VISITOR_DEMO_V1]';
const projectName = '访客与会议演示项目';
const login = await api('/auth/login', { username: config.adminUsername, password: config.adminPassword });
auth = login.token;
const projects = await api('/projects');
let project = projects.find((item) => item.projectName === projectName && item.description === marker);
if (!project) project = await api('/projects', { projectName, shortName: '访客会议演示', description: marker, projectStatus: 'NORMAL', phase: '施工阶段', contractor: '演示建设单位' });
const projectId = number(project.id);
const databaseName = sql(`SELECT DATABASE();`);
if (databaseName !== config.database || sql(`SELECT description FROM project_info WHERE id=${projectId}`) !== marker) throw new Error('演示 API 与数据库项目不匹配');
const existing = (await api(`/site-access/invitations?projectId=${projectId}&pageSize=100`)).records;
async function invite(key, title, type, start, finish) {
  const remark = marker + key;
  const saved = existing.find((item) => item.internalRemark === remark);
  if (saved) return saved;
  const value = await api('/site-access/invitations', { inviteType: type, projectId, visitStartTime: stamp(start), visitEndTime: stamp(finish), purpose: title, visitLocation: '演示项目 · 综合会议中心', hostUserId: login.userId, internalRemark: remark });
  existing.push(value); return value;
}
const main = await invite('main', '演示 · 质量安全周例会', 'MEETING', new Date(today.getTime() - 30 * 60000), end);
const tomorrow = await invite('tomorrow', '演示 · 明日施工技术交底', 'MEETING', day(1, 9), day(1, 11));
const third = await invite('third', '演示 · 材料进场协调会', 'MEETING', day(3, 14), day(3, 16));
await invite('sixth', '演示 · 第七日周计划会议', 'MEETING', day(6, 9), day(6, 11));
const closed = await invite('ended', '演示 · 已结束会议', 'MEETING', new Date(today.getTime() - 20 * 60000), end);
const voided = await invite('voided', '演示 · 已取消会议', 'MEETING', day(2, 9), day(2, 11));
if (voided.status !== 'VOIDED') await api(`/site-access/invitations/${voided.id}/void`, { reason: '演示：会议计划取消' });
const pending = await invite('pending', '演示 · 待填写设备来访预约', 'SINGLE', new Date(today.getTime() - 20 * 60000), end);
const active = await invite('active', '演示 · 已登记材料验收来访', 'SINGLE', new Date(today.getTime() - 20 * 60000), end);
const future = await invite('future', '演示 · 明日到访预约', 'SINGLE', day(1, 8), day(1, 18));
const cancelled = await invite('cancelled', '演示 · 已作废来访预约', 'SINGLE', day(2, 8), day(2, 18));
if (cancelled.status !== 'VOIDED') await api(`/site-access/invitations/${cancelled.id}/void`, { reason: '演示：访客取消到访' });
const invitationToken = (value) => decrypt(sql(`SELECT token_encrypted FROM site_visit_invitation WHERE id=${number(value.id)} AND project_id=${projectId} AND internal_remark LIKE '${marker}%'`));
const names = ['张晨','李明','王宁','陈杰','刘洋','赵磊','周敏','吴斌','徐涛','孙悦','朱峰','马超','胡静','郭强','何军','林浩','高翔','罗丹','郑凯','梁宇','谢琳','宋波','唐旭','许文','韩松','冯佳','邓昊','曹鹏','彭程','曾毅','肖博','田锐','董楠','袁航','潘鑫','蒋晨'];
const companies = ['演示建设单位','演示总包项目部','演示监理公司','演示机电安装队','演示幕墙施工队','演示材料供应单位'];
const identity = (id) => `visitor-demo-${config.database}-${id}`;
function person(index, companions = false) {
  return { visitorCompany: companies[index % companies.length], contactName: `${names[index % names.length]}（演示）`, contactPhone: `1990000${String(index + 1000).padStart(4, '0')}`, travelMode: index % 3 === 0 ? 'DRIVING' : 'OTHER', vehiclePlate: index % 3 === 0 ? `苏A${String(index + 80000)}` : '', companions: companions ? [{ personCompany: companies[index % companies.length], personName: `同行${index + 1}（演示）`, personPhone: '' }] : [], privacyAgreed: true, rememberInfo: index !== 35, profileAction: 'NONE', visitorRemark: '合成演示信息，仅供本地查看' };
}
async function registerMeeting(invitation, key, info) {
  const mockOpenid = 'mock_' + createHash('sha256').update(identity(key)).digest('hex').slice(0, 24);
  const hash = createHmac('sha256', encryptionKey).update('site-access:meeting-registration:v1\0touristappid:' + mockOpenid).digest('hex');
  const find = () => sql(`SELECT id FROM site_meeting_visit_registration WHERE invitation_id=${number(invitation.id)} AND project_id=${projectId} AND visitor_identity_hash=${quote(hash)} AND status='REGISTERED' AND deleted=0`);
  if (find()) return number(find());
  // Keep one real public flow for consent/profile reuse; bulk roster fixtures avoid consuming public IP quotas.
  if (key === 'guest-0' && invitation.id === main.id) {
    const session = await api('/public/site-access/meeting/session', { inviteToken: invitationToken(invitation), wechatCode: identity(key) });
    await api('/public/site-access/meeting/submit', info, session.visitorSessionToken);
    return number(find());
  }
  const people = [{ personType: 'CONTACT', personCompany: info.visitorCompany, personName: info.contactName, personPhone: info.contactPhone }, ...info.companions.map((p) => ({ ...p, personType: 'COMPANION' }))];
  const inserts = people.map((p, i) => `INSERT INTO site_meeting_visit_person(registration_id,project_id,person_type,person_company,person_name,phone_encrypted,sort_order) VALUES(@demo_registration,${projectId},${quote(p.personType)},${quote(p.personCompany)},${quote(p.personName)},${p.personPhone ? quote(encrypt(p.personPhone)) : 'NULL'},${i + 1});`).join('\n');
  sql(`START TRANSACTION;
    SELECT id FROM project_info WHERE id=${projectId} AND description=${quote(marker)} FOR UPDATE;
    INSERT INTO site_meeting_visit_registration(registration_no,invitation_id,project_id,wechat_app_id,visitor_identity_hash,status,registration_source,visitor_company,contact_name,contact_phone_encrypted,visitor_count,travel_mode,vehicle_plate,visitor_remark,privacy_agreed_time,registered_time)
    VALUES(${quote(`DEMO-${invitation.id}-${hash.slice(0, 16)}`)},${number(invitation.id)},${projectId},'touristappid',${quote(hash)},'REGISTERED','INVITATION',${quote(info.visitorCompany)},${quote(info.contactName)},${quote(encrypt(info.contactPhone))},${people.length},${quote(info.travelMode)},${quote(info.vehiclePlate)},${quote(marker+'合成预约名单')},NOW(),NOW());
    SET @demo_registration=LAST_INSERT_ID(); ${inserts}
    INSERT INTO site_meeting_visit_audit_log(registration_id,invitation_id,project_id,action_type,operator_name,after_snapshot_encrypted,comment)
    VALUES(@demo_registration,${number(invitation.id)},${projectId},'DEMO_SEED_REGISTRATION','本地演示数据工具',${quote(encrypt(JSON.stringify({ demo: true, people })))},${quote(marker+'独立数据库合成记录，不代表真实微信提交')}); COMMIT;`);
  return number(find());
}
for (let i = 0; i < 36; i++) {
  const info = person(i, i < 4);
  if (i === 0) Object.assign(info, { profileAction: 'CREATE', profileName: '本人常用资料（演示）', profileRetentionAgreed: true });
  const registrationId = await registerMeeting(main, `guest-${i}`, info);
  if (i < 26) {
    const missing = sql(`SELECT p.id,COALESCE(a.version,0) FROM site_meeting_visit_person p LEFT JOIN site_meeting_attendance a ON a.person_id=p.id AND a.deleted=0 WHERE p.registration_id=${registrationId} AND p.project_id=${projectId} AND p.deleted=0 AND (a.id IS NULL OR a.status<>'CHECKED_IN')`);
    if (missing) for (const row of missing.split('\n')) {
      const [personId, version] = row.split('\t').map(Number);
      await api(`/site-access/meeting-attendees/${personId}/manual-check-in`, { version, reason: '演示：合成参会人员签到', occurredTime: stamp(new Date(today.getTime() - (30 - i) * 60000)) });
    }
  }
}
// Staff-created walk-ins demonstrate the separate source and required audit reason.
const walkIns = await api(`/site-access/invitations/${main.id}/meeting-attendees?registrationSource=WALK_IN&pageSize=100`);
for (let i = 0; i < 5; i++) {
  const name = `现场来宾${i + 1}（演示）`;
  if (!walkIns.records.some((item) => item.personName === name || item.personName === '演示·' + name.replace('（演示）', ''))) await api(`/site-access/invitations/${main.id}/meeting-attendees/walk-ins`, { ...person(i + 40), contactName: name, reason: '演示：未提前预约，由工作人员现场补录' });
}
// Keep the synthetic label at the beginning so the screen's name avatar is a letter/name character.
for (const attendee of (await api(`/site-access/invitations/${main.id}/meeting-attendees?pageSize=100`)).records) {
  if (!attendee.personName.endsWith('（演示）')) continue;
  await api(`/site-access/meeting-attendees/${attendee.personId}`, {
    personName: '演示·' + attendee.personName.replace('（演示）', ''), personCompany: attendee.personCompany,
    personPhone: attendee.personPhone, version: attendee.version, reason: '演示：统一测试姓名标记'
  }, undefined, 'PUT');
}
await registerMeeting(tomorrow, 'guest-0', person(0));
for (const [value, index] of [[active, 0], [future, 1]]) {
  if (value.status !== 'SUBMITTED') {
    const inviteToken = invitationToken(value);
    const session = await api('/public/site-access/visitor-sessions', { inviteToken, wechatCode: identity(`guest-${index}`) });
    await api('/public/site-access/invitations/submit', { ...person(index, true), inviteToken }, session.visitorSessionToken);
  }
}
let guard = await api(`/site-access/guard/qr?projectId=${projectId}`);
if (!guard) guard = await api(`/site-access/guard/qr?projectId=${projectId}`, {});
const guardId = number(guard.id ?? guard.guardQrId);
const guardToken = decrypt(sql(`SELECT scene_token_encrypted FROM site_guard_visit_qr WHERE id=${guardId} AND project_id=${projectId}`));
const guardSession = (id) => api('/public/site-access/guard/session', { sceneToken: `G:${guardToken}`, wechatCode: identity(id) });
for (const [key, multi, i] of [['guard-ordinary', false, 46], ['guard-multi', true, 47]]) {
  const session = await guardSession(key);
  if (session.pageState !== 'FORM') continue;
  const choices = multi ? await api('/public/site-access/guard/meetings', {}, session.visitorSessionToken) : [];
  const selected = choices.filter((item) => [tomorrow.purpose, third.purpose].includes(item.title)).map((item) => item.choiceToken);
  if (multi && selected.length !== 2) throw new Error('演示多会议候选校验失败');
  await api('/public/site-access/guard/submit', { ...person(i, true), contactName: multi ? '周工（门卫多会演示）' : '王工（普通来访演示）', meetingChoiceTokens: selected }, session.visitorSessionToken);
}
// Only this explicitly marked synthetic fixture gets a past business window; no historical attendance is fabricated.
sql(`START TRANSACTION; UPDATE site_visit_invitation SET visit_start_time=${quote(stamp(day(-1, 9)))},visit_end_time=${quote(stamp(day(-1, 11)))} WHERE id=${number(closed.id)} AND project_id=${projectId} AND internal_remark=${quote(marker+'ended')}; UPDATE site_meeting_checkin_qr SET checkin_start_time=${quote(stamp(day(-1, 8)))},checkin_end_time=${quote(stamp(day(-1, 11)))} WHERE invitation_id=${number(closed.id)} AND project_id=${projectId}; COMMIT;`);
const matched = await guardSession('guest-0');
const screen = await api(`/site-access/invitations/${main.id}/meeting-attendance/screen?pageNo=1`);
if (screen.reservedPersonCount !== 40 || screen.totalCheckedInCount !== 35 || screen.reservedPendingCount !== 10 || screen.records.length !== 24 || matched.matchedPasses.length !== 2) throw new Error('演示人数或门卫匹配核验失败');
const summary = { projectId, projectName, invitationCount: existing.length, mainMeetingId: main.id, mainMeeting: main.purpose, reserved: 40, checkedIn: 35, pending: 10, walkIn: 5, guardRegistrations: 2, matchedAppointments: matched.matchedPasses.length, pendingInvitationId: pending.id, webUrl: config.webUrl, screenUrl: `${config.webUrl}/?meetingScreen=${main.id}`, createdAt: new Date().toISOString(), mockWechat: true };
writeFileSync(join(dirname(configPath), 'summary.json'), JSON.stringify(summary, null, 2), { mode: 0o600 });
console.log(JSON.stringify(summary, null, 2));
