import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { resolveBusinessRoute } from './businessRoute.js';

describe('resolveBusinessRoute', () => {
  it('resolves every supported route from its named route parameter', () => {
    assert.deepEqual(resolveBusinessRoute({ routeCode: 'SEAL_APPLICATION_DETAIL', routeParams: { applicationId: '11' }, projectId: 2 }), {
      routeCode: 'SEAL_APPLICATION_DETAIL', id: 11, projectId: 2,
    });
    assert.equal(resolveBusinessRoute({ routeCode: 'QUALITY_ISSUE_DETAIL', routeParams: { issueId: 12 } }).id, 12);
    assert.equal(resolveBusinessRoute({ routeCode: 'INSPECTION_FORM', routeParams: { boxId: 13 } }).id, 13);
    assert.equal(resolveBusinessRoute({ routeCode: 'INSPECTION_RECORD_DETAIL', routeParams: JSON.stringify({ recordId: 14 }) }).id, 14);
    assert.equal(resolveBusinessRoute({ routeCode: 'INSPECTION_RECTIFICATION_DETAIL', routeParams: { rectificationId: 15 } }).id, 15);
    assert.equal(resolveBusinessRoute({ routeCode: 'DOCUMENT_DISTRIBUTION_DETAIL', routeParams: { distributionId: 16 } }).id, 16);
    assert.equal(resolveBusinessRoute({ routeCode: 'EDGE_INSPECTION_TASK_DETAIL', routeParams: { taskId: 17 } }).id, 17);
    assert.equal(resolveBusinessRoute({ routeCode: 'EDGE_INSPECTION_RECTIFICATION_DETAIL', routeParams: { taskId: 18 } }).id, 18);
    assert.deepEqual(resolveBusinessRoute({
      routeCode: 'QUALITY_WEEKLY_INSPECTION_WEEK',
      routeParams: JSON.stringify({ projectId: 2, weekStart: '2026-08-24' }),
    }), {
      routeCode: 'QUALITY_WEEKLY_INSPECTION_WEEK', projectId: 2, weekStart: '2026-08-24',
    });
  });

  it('keeps targetId as a compatibility fallback for a whitelisted route', () => {
    assert.equal(resolveBusinessRoute({ routeCode: 'QUALITY_ISSUE_DETAIL', targetId: 21 }).id, 21);
  });

  it('rejects unsupported routes, invalid ids, and arbitrary action URLs', () => {
    assert.equal(resolveBusinessRoute({ routeCode: 'EXTERNAL_URL', actionUrl: 'https://example.com' }), null);
    assert.equal(resolveBusinessRoute({ actionUrl: '/admin', targetId: 1 }), null);
    assert.equal(resolveBusinessRoute({ routeCode: 'SEAL_APPLICATION_DETAIL', routeParams: { applicationId: -1 } }), null);
    assert.equal(resolveBusinessRoute({ routeCode: 'QUALITY_WEEKLY_INSPECTION_WEEK', routeParams: { projectId: 2, weekStart: '2026/08/24' } }), null);
    assert.equal(resolveBusinessRoute({ routeCode: 'QUALITY_WEEKLY_INSPECTION_WEEK', routeParams: { projectId: 2, weekStart: '2026-02-31' } }), null);
    assert.equal(resolveBusinessRoute({ routeCode: 'QUALITY_WEEKLY_INSPECTION_WEEK', projectId: 3, routeParams: { projectId: 2, weekStart: '2026-08-24' } }), null);
  });
});
