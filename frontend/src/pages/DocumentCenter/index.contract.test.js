import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const pageSource = readFileSync(new URL('./index.jsx', import.meta.url), 'utf8');
const styleSource = readFileSync(new URL('./index.css', import.meta.url), 'utf8');

test('document center groups seal applications and ledger under one business area', () => {
  assert.match(pageSource, /id: 'seal', label: '用印管理'/);
  assert.doesNotMatch(pageSource, /const TABS = \[[^\]]*id: 'ledger'/);
  assert.match(pageSource, /const SEAL_TABS = \[/);
  assert.match(pageSource, /id: 'applications', label: '用印申请'/);
  assert.match(pageSource, /id: 'ledger', label: '用印台账'/);
  assert.match(pageSource, /aria-label="用印管理功能"/);
  assert.match(pageSource, /mode="applications"/);
  assert.match(pageSource, /mode="ledger"/);
});

test('direct seal tasks always open the application subsection', () => {
  assert.match(pageSource, /setActiveTab\('seal'\);\s*setActiveSealTab\('applications'\);/);
  assert.match(pageSource, /if \(tab\.id === 'ledger'\) return canViewLedger/);
});

test('seal subsection remains independently scrollable below the navigation', () => {
  assert.match(styleSource, /\.document-center-seal \{[\s\S]*display: flex;[\s\S]*flex-direction: column;/);
  assert.match(styleSource, /\.document-center-subtabs \{/);
  assert.match(styleSource, /\.document-center-seal > \.seal-page \{[^}]*flex: 1 1 auto;/);
});
