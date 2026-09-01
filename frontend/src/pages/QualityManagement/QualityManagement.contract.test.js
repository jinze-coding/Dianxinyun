import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const pageSource = readFileSync(new URL('./index.jsx', import.meta.url), 'utf8');
const weeklySource = readFileSync(new URL('./WeeklyInspectionPanel.jsx', import.meta.url), 'utf8');
const styleSource = readFileSync(new URL('./quality-management.css', import.meta.url), 'utf8');
const serviceSource = readFileSync(new URL('../../services/quality.js', import.meta.url), 'utf8');

test('quality workspace keeps one accessible navigation contract for all three views', () => {
  assert.match(
    pageSource,
    /const QUALITY_TABS = \[[\s\S]*key: "weekly"[\s\S]*key: "issues"[\s\S]*key: "documents"/,
    '三个页签的顺序必须保持为周检记录、整改闭环、质量资料',
  );
  assert.match(pageSource, /role="tablist" aria-label="质量周检页面"/);
  assert.match(pageSource, /role="tab"[\s\S]*aria-selected=\{active\}[\s\S]*aria-controls=\{`quality-panel-\$\{tab\.key\}`\}/);
  assert.match(pageSource, /role="tabpanel"[\s\S]*id=\{`quality-panel-\$\{activeTab\}`\}[\s\S]*aria-labelledby=\{`quality-tab-\$\{activeTab\}`\}/);
});

test('quality views share the same visual hierarchy and scrolling table region', () => {
  assert.match(pageSource, /className="quality-workspace-header"/);
  assert.match(pageSource, /className="quality-content-card"/);
  assert.match(pageSource, /className="quality-panel-header"/);
  assert.match(pageSource, /className="quality-table-region"/);
  assert.match(weeklySource, /className="quality-panel-header"/);
  assert.match(weeklySource, /className="quality-table-region"/);
  assert.match(pageSource, /<QualityMetricGrid T=\{T\} summary=\{currentSummary\} \/>/,
    '整改统计必须位于整改页内容区，不能再把总页签向下顶');
});

test('quality document upload and responsive layout use the coordinated controls', () => {
  assert.match(pageSource, /className="quality-file-picker"/);
  assert.match(pageSource, /className="quality-scope-switch" role="group"/);
  assert.match(styleSource, /\.quality-file-picker input \{[\s\S]*clip-path: inset\(50%\)/);
  assert.match(styleSource, /@media \(max-width: 1180px\)/);
  assert.match(styleSource, /@media \(max-width: 760px\)/);
  assert.match(styleSource, /\.quality-table-region \{[\s\S]*overflow: auto/);
});

test('rectification ledger filters by a complete business-date range and displays recordDate', () => {
  assert.match(pageSource, /aria-label="问题开始日期"/);
  assert.match(pageSource, /aria-label="问题结束日期"/);
  assert.match(pageSource, /startDate: targetStartDate \|\| undefined/);
  assert.match(pageSource, /endDate: targetEndDate \|\| undefined/);
  assert.match(pageSource, /"问题日期"/);
  assert.match(pageSource, /issue\.recordDate \|\| "-"/);
  assert.match(pageSource, /日期范围最长为 366 天/);
});

test('quality export uses asynchronous jobs, progress polling and controlled blob download', () => {
  assert.match(pageSource, /质量问题月度汇总导出/);
  assert.match(pageSource, /createQualityIssueExportJob/);
  assert.match(pageSource, /window\.setInterval[\s\S]*3000/);
  assert.match(pageSource, /job\.downloadable/);
  assert.match(serviceSource, /\/quality\/issues\/export-jobs/);
  assert.match(serviceSource, /responseType: 'blob'/);
  assert.match(styleSource, /\.quality-export-form/);
});

test('weekly inspection exposes versioned internal reminder configuration', () => {
  assert.match(weeklySource, /质量周检未提交提醒/);
  assert.match(weeklySource, /getWeeklyInspectionReminderSetting/);
  assert.match(weeklySource, /getWeeklyInspectionReminderAssignees/);
  assert.match(weeklySource, /updateWeeklyInspectionReminderSetting/);
  assert.match(weeklySource, /expectedVersion: Number\(reminderSetting\.version/);
  assert.match(weeklySource, /QUALITY_WEEKLY_INSPECTION_WEEK/);
});
