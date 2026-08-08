import assert from 'node:assert/strict';
import test from 'node:test';
import { siteVisitDateRange, validateSiteVisitDateRange } from './siteAccessDates.js';

test('day range uses the selected calendar date', () => {
  assert.deepEqual(siteVisitDateRange('DAY', '2026-08-08'), {
    startDate: '2026-08-08',
    endDate: '2026-08-08',
  });
});

test('week range uses Monday through Sunday', () => {
  assert.deepEqual(siteVisitDateRange('WEEK', '2026-08-07'), {
    startDate: '2026-08-03',
    endDate: '2026-08-09',
  });
});

test('month range follows calendar month', () => {
  assert.deepEqual(siteVisitDateRange('MONTH', '2026-02-12'), {
    startDate: '2026-02-01',
    endDate: '2026-02-28',
  });
});

test('custom range keeps the selected boundary dates', () => {
  assert.deepEqual(siteVisitDateRange('CUSTOM', '2026-08-08', '2026-07-01', '2026-08-08'), {
    startDate: '2026-07-01',
    endDate: '2026-08-08',
  });
});

test('range validation limits inclusive dates to 366 days', () => {
  assert.equal(validateSiteVisitDateRange('2026-01-01', '2026-12-31'), '');
  assert.match(validateSiteVisitDateRange('2025-01-01', '2026-12-31'), /366/);
  assert.match(validateSiteVisitDateRange('2026-08-08', '2026-08-07'), /不能晚于/);
});
