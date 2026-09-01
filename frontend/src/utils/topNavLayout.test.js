import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { TOP_NAV_ITEMS_STYLE, TOP_NAV_SCROLLER_STYLE } from './topNavLayout.js';

const app = readFileSync(new URL('../App.jsx', import.meta.url), 'utf8');

test('top navigation keeps its first item reachable when items overflow', () => {
  assert.equal(TOP_NAV_SCROLLER_STYLE.overflowX, 'auto');
  assert.equal(TOP_NAV_SCROLLER_STYLE.justifyContent, undefined);
  assert.equal(TOP_NAV_ITEMS_STYLE.minWidth, 'max-content');
  assert.equal(TOP_NAV_ITEMS_STYLE.margin, '0 auto');
});

test('App separates the scroll container from the centered navigation items', () => {
  assert.match(app, /<nav aria-label="主导航" style=\{TOP_NAV_SCROLLER_STYLE\}>/);
  assert.match(app, /<div style=\{TOP_NAV_ITEMS_STYLE\}>/);
  assert.doesNotMatch(app, /justifyContent:\s*'center',[\s\S]{0,160}overflowX:\s*'auto'/);
});
