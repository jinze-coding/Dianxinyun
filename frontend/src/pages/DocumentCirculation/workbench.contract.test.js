import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const pageSource = readFileSync(new URL('./index.jsx', import.meta.url), 'utf8');
const styleSource = readFileSync(new URL('./index.css', import.meta.url), 'utf8');

test('document circulation opens with a task-oriented workbench and explains the complete loop', () => {
  assert.match(pageSource, /useState\('workspace'\)/);
  assert.match(pageSource, /\{ id: 'workspace', label: '工作台' \}/);
  assert.match(pageSource, /收到资料/);
  assert.match(pageSource, /核对版本/);
  assert.match(pageSource, /发放通知/);
  assert.match(pageSource, /签收领取/);
  assert.match(pageSource, /完成追溯/);
  assert.match(pageSource, /需要我处理/);
  assert.match(pageSource, /待完善收文/);
  assert.match(pageSource, /逾期批次/);
  assert.match(styleSource, /\.dc-process-flow/);
  assert.match(styleSource, /\.dc-workbench-tasks/);
});

test('incoming registration uses four explicit progressive steps', () => {
  assert.match(pageSource, /const \[draftStep, setDraftStep\] = useState\(1\)/);
  assert.match(pageSource, /收文信息/);
  assert.match(pageSource, /上传文件/);
  assert.match(pageSource, /核对文件与版本/);
  assert.match(pageSource, /接收人及发布/);
  assert.match(pageSource, /下一步：上传文件/);
  assert.match(pageSource, /下一步：核对文件/);
  assert.match(pageSource, /保存并选择接收人/);
  assert.match(styleSource, /\.dc-wizard-steps/);
  assert.match(styleSource, /\.dc-wizard-footer/);
});

test('distribution detail surfaces progress, next action and controlled secondary actions', () => {
  assert.match(pageSource, /recipientProgress/);
  assert.match(pageSource, /签收进度/);
  assert.match(pageSource, /下一步/);
  assert.match(pageSource, /接收人状态/);
  assert.match(pageSource, /更多操作/);
  assert.match(pageSource, /按当前版本重新发放/);
  assert.match(styleSource, /\.dc-detail-progress/);
  assert.match(styleSource, /\.dc-next-action/);
  assert.match(styleSource, /\.dc-more-actions/);
});
