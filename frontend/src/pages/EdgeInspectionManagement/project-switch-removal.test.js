import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const page = readFileSync(new URL('./index.jsx', import.meta.url), 'utf8');
const styles = readFileSync(new URL('./index.css', import.meta.url), 'utf8');
const service = readFileSync(new URL('../../services/edgeInspection.js', import.meta.url), 'utf8');

test('edge inspection has no project pilot switch in the Web client', () => {
  assert.doesNotMatch(page, /getEdgeInspectionFeature|updateEdgeInspectionFeature|开启试点|关闭试点|项目未启用/);
  assert.doesNotMatch(service, /projects\/\$\{projectId\}\/feature|getEdgeInspectionFeature|updateEdgeInspectionFeature/);
  assert.match(page, /查看固定检查表/);
  assert.match(page, /临边点位/);
  assert.match(page, /当前巡检规则/);
});

test('edge inspection Web workspace defaults to the compact three-page structure', () => {
  assert.doesNotMatch(page, /className="edge-heading edge-card"/);
  assert.match(page, /useState\('todo'\)/);
  assert.match(page, /\{ id: 'todo', label: '待处理' \}/);
  assert.match(page, /\{ id: 'records', label: '巡检记录' \}/);
  assert.match(page, /\{ id: 'settings', label: '基础设置' \}/);
  assert.doesNotMatch(page, /id: 'rectifications'|id: 'statistics'|id: 'points'|id: 'setting'/);
  assert.match(styles, /\.edge-tabs \{[^}]*display:\s*flex/);
  assert.match(styles, /overflow-x:\s*hidden/);
});

test('todo page loads only actionable tasks and rectifications, deduplicates and sorts them', () => {
  assert.match(page, /getEdgeInspectionTasks\(\{ projectId: targetProjectId, mine: false, status: 'PENDING' \}\)/);
  assert.match(page, /getEdgeInspectionRectifications\(\{ projectId: targetProjectId, scope: 'ALL' \}\)/);
  assert.match(page, /rectificationTaskIds/);
  assert.match(page, /\['UNASSIGNED', 'PENDING', 'REJECTED', 'COMPLETED'\]/);
  assert.match(page, /left\.overdue !== right\.overdue/);
  assert.doesNotMatch(page, /Promise\.allSettled/);
});

test('records keep filters visible and opt into batch mode, statistics drawer and export dialog', () => {
  assert.match(page, /edge-record-filters/);
  assert.match(page, /batchMode/);
  assert.match(page, /edge-statistics-drawer/);
  assert.match(page, /openExport/);
  assert.match(page, /displayPointName/);
  assert.match(styles, /\.edge-record-filters/);
});

test('workspace explains the point-to-task flow and uses the project summary endpoint', () => {
  assert.match(page, /getEdgeInspectionWorkspaceSummary/);
  assert.match(service, /projects\/\$\{projectId\}\/workspace-summary/);
  assert.match(page, /点位设置/);
  assert.match(page, /自动生成任务/);
  assert.match(page, /小程序巡检/);
  assert.match(page, /异常整改/);
  assert.match(page, /复查闭环/);
  assert.match(page, /点位不等于任务/);
  assert.match(page, /未来预生成任务不会进入这里/);
  assert.match(styles, /\.edge-flow-strip/);
  assert.match(styles, /\.edge-flow-equation/);
});

test('setting presents reminder time as a projection instead of generated-task evidence', () => {
  assert.match(page, /EDGE_REMINDER_PROJECTION_LABEL/);
  assert.match(page, /EDGE_REMINDER_PROJECTION_NOTICE/);
  assert.match(page, /实际任务到达截止时间后才可能产生提醒/);
  assert.match(page, /若当前时段尚未截止会生成本时段任务/);
  assert.match(page, /新建点位仍从创建后的下一适用时段开始纳入/);
  assert.doesNotMatch(page, /下一次提醒/);
});

test('edge inspection export uses asynchronous jobs, polling and a controlled blob download', () => {
  assert.match(page, /createEdgeInspectionExportJob/);
  assert.match(page, /window\.setInterval\(refresh, 3000\)/);
  assert.match(page, /downloadEdgeInspectionExport/);
  assert.match(page, /edge-drawer-backdrop/);
  assert.match(service, /\/export-jobs/);
  assert.match(service, /ensureFileBlob/);
});
