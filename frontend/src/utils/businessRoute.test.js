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
  });

  it('keeps targetId as a compatibility fallback for a whitelisted route', () => {
    assert.equal(resolveBusinessRoute({ routeCode: 'QUALITY_ISSUE_DETAIL', targetId: 21 }).id, 21);
  });

  it('rejects unsupported routes, invalid ids, and arbitrary action URLs', () => {
    assert.equal(resolveBusinessRoute({ routeCode: 'EXTERNAL_URL', actionUrl: 'https://example.com' }), null);
    assert.equal(resolveBusinessRoute({ actionUrl: '/admin', targetId: 1 }), null);
    assert.equal(resolveBusinessRoute({ routeCode: 'SEAL_APPLICATION_DETAIL', routeParams: { applicationId: -1 } }), null);
  });
});
