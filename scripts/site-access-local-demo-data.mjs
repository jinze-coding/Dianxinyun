#!/usr/bin/env node
// Synthetic visitor fixtures in the existing local development project. No account or configuration changes.
import { execFileSync } from 'node:child_process';
import { createCipheriv, createDecipheriv, createHash, createHmac, randomBytes } from 'node:crypto';

const projectId = Number(process.env.DIANXINYUN_LOCAL_DEMO_PROJECT_ID);
if (process.env.DIANXINYUN_LOCAL_DEMO_CONFIRM !== 'LOCAL_DEVELOPMENT_ONLY'
    || !Number.isSafeInteger(projectId) || projectId < 1) throw new Error('请指定本地项目及 LOCAL_DEVELOPMENT_ONLY 确认值');
if (process.env.DB_URL && !/^jdbc:mysql:\/\/(localhost|127\.0\.0\.1):3306\/dianxinyun(?:\?|$)/.test(process.env.DB_URL)) {
  throw new Error('只允许本机 dianxinyun 开发库');
}
const keyText = process.env.VISITOR_DATA_ENCRYPTION_KEY?.trim();
if (!keyText || Buffer.byteLength(keyText) < 32) throw new Error('必须沿用本地开发环境已配置的访客密钥');
const key = createHash('sha256').update(keyText).digest();
const appId = process.env.WECHAT_MINI_PROGRAM_APP_ID || 'touristappid';
const sql = (statement) => execFileSync('mysql', ['--protocol=TCP', '-h127.0.0.1', '-P3306',
  '-u', process.env.DB_USERNAME || 'root', '-N', '--batch', '--raw', 'dianxinyun'], {
  input: statement, encoding: 'utf8', maxBuffer: 8 * 1024 * 1024,
  env: { ...process.env, MYSQL_PWD: process.env.DB_PASSWORD || '' }, stdio: ['pipe', 'pipe', 'pipe']
}).trim();
const quote = (value) => `CONVERT(X'${Buffer.from(String(value), 'utf8').toString('hex')}' USING utf8mb4)`;
const sha = (value) => createHash('sha256').update(value).digest('hex');
function encrypt(value) {
  const iv = randomBytes(12), cipher = createCipheriv('aes-256-gcm', key, iv);
  cipher.setAAD(Buffer.from('v1'));
  return 'v1:' + Buffer.concat([iv, cipher.update(value, 'utf8'), cipher.final(), cipher.getAuthTag()]).toString('base64url');
}
function decrypt(value) {
  if (!value.startsWith('v1:')) throw new Error('当前访客密文版本不匹配');
  const bytes = Buffer.from(value.slice(3), 'base64url');
  const cipher = createDecipheriv('aes-256-gcm', key, bytes.subarray(0, 12));
  cipher.setAAD(Buffer.from('v1')); cipher.setAuthTag(bytes.subarray(-16));
  return Buffer.concat([cipher.update(bytes.subarray(12, -16)), cipher.final()]).toString();
}
const now = new Date();
const stamp = (date) => new Date(date.getTime() + 8 * 3600000).toISOString().slice(0, 19).replace('T', ' ');
const date = stamp(now).slice(0, 10), compactDate = date.replaceAll('-', '');
const start = new Date(date + 'T00:00:00+08:00');
const day = (offset, hour, minute = 0) => stamp(new Date(start.getTime() + (offset * 24 + hour) * 3600000 + minute * 60000));
const marker = `[LOCAL_VISITOR_DEMO_${compactDate}]`;
const since = stamp(new Date(now.getTime() - 3600000));
const until = day(0, 23, 59);
const prefix = `LD${compactDate}P${projectId}`;
const operatorName = '本地测试数据';
const identity = (index, purpose) => {
  const openid = 'local_demo_' + sha(`${prefix}:${index}`).slice(0, 24);
  const input = (purpose ? `site-access:${purpose}:v1\0` : '') + `${appId}:${openid}`;
  return createHmac('sha256', key).update(input).digest('hex');
};
const requiredMigrations = Number(sql("SELECT COUNT(*) FROM sys_data_migration WHERE migration_key IN ('20260902_SITE_ACCESS_MEETING_CHECKIN_V1','20260910_VISITOR_REUSE_GUARD_MEETING_V1')"));
if (requiredMigrations !== 2) throw new Error('先按顺序应用会议签到及访客复用迁移');
if (sql(`SELECT id FROM project_info WHERE id=${projectId} AND deleted=0`) !== String(projectId)) throw new Error('本地目标项目不存在');
const adminId = Number(sql("SELECT MIN(u.id) FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role r ON r.id=ur.role_id WHERE r.role_code='PLATFORM_ADMIN' AND u.deleted=0 AND u.status=1"));
if (!Number.isSafeInteger(adminId) || adminId < 1) throw new Error('本地缺少有效管理员');
const probe = sql(`SELECT token_encrypted,token_hash FROM site_visit_invitation WHERE project_id=${projectId} LIMIT 1`);
if (probe) {
  const [encrypted, hash] = probe.split('\t');
  if (sha(decrypt(encrypted)) !== hash) throw new Error('当前密钥与已有本地邀请不匹配');
}

function summary() {
  const mainId = Number(sql(`SELECT id FROM site_visit_invitation WHERE project_id=${projectId} AND internal_remark=${quote(marker + 'main')}`));
  if (!mainId) throw new Error('测试会议不存在');
  const counts = sql(`SELECT
    (SELECT COUNT(*) FROM site_visit_invitation WHERE project_id=${projectId} AND internal_remark LIKE ${quote(marker + '%')}),
    (SELECT COUNT(*) FROM site_meeting_visit_person p JOIN site_meeting_visit_registration r ON r.id=p.registration_id WHERE r.invitation_id=${mainId} AND r.registration_source='INVITATION' AND r.deleted=0 AND p.deleted=0),
    (SELECT COUNT(*) FROM site_meeting_attendance WHERE invitation_id=${mainId} AND status='CHECKED_IN' AND deleted=0),
    (SELECT COUNT(*) FROM site_meeting_visit_person p JOIN site_meeting_visit_registration r ON r.id=p.registration_id LEFT JOIN site_meeting_attendance a ON a.person_id=p.id AND a.status='CHECKED_IN' WHERE r.invitation_id=${mainId} AND r.registration_source='INVITATION' AND r.deleted=0 AND p.deleted=0 AND a.id IS NULL),
    (SELECT COUNT(*) FROM site_guard_visit_registration WHERE project_id=${projectId} AND visitor_remark=${quote(marker)}),
    (SELECT COUNT(*) FROM site_guard_meeting_registration l JOIN site_guard_visit_registration g ON g.id=l.guard_registration_id WHERE g.project_id=${projectId} AND g.visitor_remark=${quote(marker)})`).split('\t').map(Number);
  if (counts.join(',') !== '10,40,35,10,2,2') throw new Error(`测试数据计数不符合预期：${counts.join(',')}`);
  return { projectId, mainMeetingId: mainId, invitationCount: counts[0], reserved: counts[1], checkedIn: counts[2], pending: counts[3], guardGroups: counts[4], meetingLinks: counts[5], webUrl: 'http://localhost:3002', screenUrl: `http://localhost:3002/?meetingScreen=${mainId}` };
}
const existing = Number(sql(`SELECT COUNT(*) FROM site_visit_invitation WHERE project_id=${projectId} AND internal_remark LIKE ${quote(marker + '%')}`));
if (existing) {
  console.log(JSON.stringify({ reused: true, ...summary() }, null, 2));
  process.exit(0);
}
const statements = ['START TRANSACTION;', `SELECT id FROM project_info WHERE id=${projectId} AND deleted=0 FOR UPDATE;`,
  `SET @local_demo_guard=IF((SELECT COUNT(*) FROM site_visit_invitation WHERE project_id=${projectId} AND internal_remark LIKE ${quote(marker + '%')})=0,'SELECT 1','SELECT LOCAL_DEMO_ALREADY_EXISTS');`,
  'PREPARE local_demo_statement FROM @local_demo_guard; EXECUTE local_demo_statement; DEALLOCATE PREPARE local_demo_statement;'];
const ref = (name) => ({ sql: '@local_demo_' + name });
const value = (item) => item == null ? 'NULL' : typeof item === 'object' ? item.sql : typeof item === 'number' ? String(item) : quote(item);
function insert(table, fields, name) {
  statements.push(`INSERT INTO ${table}(${Object.keys(fields).join(',')}) VALUES(${Object.values(fields).map(value).join(',')});`);
  if (name) statements.push(`SET ${ref(name).sql}=LAST_INSERT_ID();`);
  return name ? ref(name) : null;
}
const audit = (table, fields) => insert(table, { project_id: projectId, action_type: 'LOCAL_DEMO_SEED', operator_id: adminId,
  operator_name: operatorName, comment: marker + '合成测试数据，仅用于开发预览，不代表真实扫码或到场', ...fields });
const names = ['张晨','李明','王宁','陈杰','刘洋','赵磊','周敏','吴斌','徐涛','孙悦','朱峰','马超','胡静','郭强','何军','林浩','高翔','罗丹','郑凯','梁宇','谢琳','宋波','唐旭','许文','韩松','冯佳','邓昊','曹鹏','彭程','曾毅','肖博','田锐','董楠','袁航','潘鑫','蒋晨'];
const companies = ['演示建设单位','演示总包项目部','演示监理公司','演示机电安装队','演示幕墙施工队','演示材料供应单位'];
const info = (i, companions = false, name) => ({ visitor_company: companies[i % companies.length], contact_name: name || '演示·' + names[i % names.length],
  contact_phone_encrypted: encrypt('1990000' + String(i + 1000).padStart(4, '0')), visitor_count: companions ? 2 : 1,
  travel_mode: i % 3 === 0 ? 'DRIVING' : 'OTHER', vehicle_plate: i % 3 === 0 ? `沪A${i + 80000}` : null,
  visitor_remark: marker, privacy_agreed_time: stamp(now) });
const invites = {};
function invite(code, title, type, begin, end, status, person) {
  const token = randomBytes(16).toString('base64url');
  const fields = { project_id: projectId, invite_no: prefix + '-' + code, token_hash: sha(token), token_encrypted: encrypt(token),
    status, invite_type: type, visit_start_time: begin, visit_end_time: end, purpose: '演示 · ' + title,
    visit_location: '本项目 · 演示会议室', host_user_id: adminId, host_name: operatorName, internal_remark: marker + code,
    created_by_id: adminId, created_by_name: operatorName };
  if (person !== undefined) Object.assign(fields, info(person, true), { submitted_time: stamp(now), wechat_app_id: appId, visitor_identity_hash: identity(person, 'single-registration') });
  if (status === 'VOIDED') Object.assign(fields, { void_reason: '演示：计划取消', voided_by_id: adminId, voided_by_name: operatorName, voided_time: stamp(now) });
  const id = insert('site_visit_invitation', fields, 'invite_' + code);
  const entry = { id, qr: null }; invites[code] = entry;
  if (type === 'MEETING') {
    const scene = randomBytes(20).toString('base64url');
    entry.qr = insert('site_meeting_checkin_qr', { invitation_id: id, project_id: projectId, scene_token_hash: sha(scene), scene_token_encrypted: encrypt(scene),
      checkin_start_time: stamp(new Date(new Date(begin.replace(' ', 'T') + '+08:00').getTime() - 3600000)), checkin_end_time: end,
      created_by_id: adminId, created_by_name: operatorName, updated_by_id: adminId, updated_by_name: operatorName }, 'qr_' + code);
  } else if (person !== undefined) people('site_visit_person', 'invitation_id', id, person, true, 'single_' + code);
  audit('site_visit_audit_log', { invitation_id: id, after_snapshot_encrypted: encrypt(JSON.stringify({ demo: true, title, type, status })) });
  return entry;
}
function people(table, parentColumn, parent, index, companions, code, contactName) {
  const p = info(index, companions, contactName), ids = [];
  for (let i = 0; i < p.visitor_count; i++) ids.push(insert(table, { [parentColumn]: parent, project_id: projectId, person_type: i ? 'COMPANION' : 'CONTACT',
    person_company: p.visitor_company, person_name: i ? `演示·同行${index + 1}` : p.contact_name,
    phone_encrypted: i ? null : p.contact_phone_encrypted, sort_order: i + 1 }, code + '_person_' + i));
  return ids;
}
invite('main', '质量安全周例会', 'MEETING', since, until, 'OPEN');
invite('tomorrow', '明日施工技术交底', 'MEETING', day(1, 9), day(1, 11), 'OPEN');
invite('third', '材料进场协调会', 'MEETING', day(3, 14), day(3, 16), 'OPEN');
invite('sixth', '第七日周计划会议', 'MEETING', day(6, 9), day(6, 11), 'OPEN');
invite('ended', '已结束会议', 'MEETING', stamp(new Date(now.getTime() - 3 * 3600000)), stamp(new Date(now.getTime() - 2 * 3600000)), 'OPEN');
invite('voided', '已取消会议', 'MEETING', since, until, 'VOIDED');
invite('pending', '待填写设备来访预约', 'SINGLE', since, until, 'PENDING');
invite('active', '已登记材料验收来访', 'SINGLE', since, until, 'SUBMITTED', 0);
invite('future', '明日到访预约', 'SINGLE', day(1, 8), day(1, 18), 'SUBMITTED', 1);
invite('cancelled', '已作废来访预约', 'SINGLE', since, until, 'VOIDED');

function registration(invitation, index, companions, code, walkIn = false, contactName) {
  const p = info(index, companions, contactName);
  const id = insert('site_meeting_visit_registration', { registration_no: prefix + '-' + code, invitation_id: invitation.id, project_id: projectId,
    wechat_app_id: walkIn ? null : appId, visitor_identity_hash: walkIn ? null : identity(index, 'meeting-registration'),
    status: 'REGISTERED', registration_source: walkIn ? 'WALK_IN' : 'INVITATION', ...p, registered_time: stamp(now) }, 'registration_' + code);
  const persons = people('site_meeting_visit_person', 'registration_id', id, index, companions, code, contactName);
  audit('site_meeting_visit_audit_log', { registration_id: id, invitation_id: invitation.id, after_snapshot_encrypted: encrypt(JSON.stringify({ demo: true, personCount: p.visitor_count, source: walkIn ? 'WALK_IN' : 'INVITATION' })) });
  return { id, persons };
}
for (let i = 0; i < 41; i++) {
  const walkIn = i >= 36;
  const group = registration(invites.main, i, i < 4, 'main' + i, walkIn, walkIn ? `演示·现场来宾${i - 35}` : undefined);
  if (i < 26 || walkIn) for (const personId of group.persons) {
    const checkinTime = stamp(new Date(now.getTime() - (45 - i) * 60000));
    insert('site_meeting_attendance', { invitation_id: invites.main.id, registration_id: group.id, person_id: personId, project_id: projectId,
      status: 'CHECKED_IN', checkin_method: 'STAFF_MANUAL', checkin_time: checkinTime, location_result: 'MANUAL' });
    audit('site_meeting_visit_audit_log', { invitation_id: invites.main.id, registration_id: group.id, person_id: personId,
      after_snapshot_encrypted: encrypt(JSON.stringify({ demo: true, checkinTime, reason: '合成签到样例' })) });
  }
  if (i < 10 || i === 35) {
    const p = info(i), enabled = i !== 35;
    const personal = insert('site_visitor_personal_profile', { project_id: projectId, wechat_app_id: appId, owner_identity_hash: identity(i, 'personal-profile'),
      remember_enabled: enabled ? 1 : 0, visitor_company: enabled ? p.visitor_company : null, contact_name: enabled ? p.contact_name : null,
      contact_phone_encrypted: enabled ? p.contact_phone_encrypted : null, travel_mode: enabled ? p.travel_mode : null,
      vehicle_plate: enabled ? p.vehicle_plate : null, privacy_agreed_time: stamp(now), last_submitted_time: stamp(now) }, 'personal' + i);
    insert('site_visitor_personal_profile_audit', { profile_id: personal, project_id: projectId, action: 'LOCAL_DEMO_SEED', after_encrypted: encrypt(JSON.stringify({ demo: true, rememberEnabled: enabled })) });
  }
}
registration(invites.tomorrow, 0, false, 'tomorrow0');
const guardQr = Number(sql(`SELECT id FROM site_guard_visit_qr WHERE project_id=${projectId} AND deleted=0 AND qr_status IN ('ENABLED','DISABLED') LIMIT 1`));
if (!guardQr) throw new Error('当前项目需先在场内管理生成门卫码；脚本不会替换既有门卫码');
for (let i = 0; i < 2; i++) {
  const index = 46 + i, name = i ? '演示·门卫多会议来访' : '演示·门卫普通来访';
  const guard = insert('site_guard_visit_registration', { registration_no: prefix + '-guard' + i, project_id: projectId, guard_qr_id: guardQr,
    wechat_app_id: appId, visitor_identity_hash: identity(index, 'guard-registration'), status: 'REGISTERED', ...info(index, true, name),
    registered_time: stamp(now), valid_until: stamp(new Date(now.getTime() + 24 * 3600000)) }, 'guard' + i);
  people('site_guard_visit_person', 'registration_id', guard, index, true, 'guard' + i, name);
  audit('site_guard_visit_audit_log', { registration_id: guard, after_snapshot_encrypted: encrypt(JSON.stringify({ demo: true, meetingCount: i ? 2 : 0 })) });
  if (i) for (const code of ['tomorrow', 'third']) {
    const group = registration(invites[code], index, true, 'guard_' + code, false, name);
    insert('site_guard_meeting_registration', { project_id: projectId, guard_registration_id: guard, invitation_id: invites[code].id, meeting_registration_id: group.id });
  }
}
insert('sys_operation_log', { user_id: adminId, username: operatorName, operation_type: 'LOCAL_DEMO_SEED',
  operation_desc: marker + '本地新增10条邀请、45人主会议名单、35人合成签到及2组门卫登记', business_type: 'SITE_ACCESS', business_id: invites.main.id, ip_address: '127.0.0.1' });
statements.push('COMMIT;');
sql(statements.join('\n'));
console.log(JSON.stringify({ reused: false, ...summary() }, null, 2));
