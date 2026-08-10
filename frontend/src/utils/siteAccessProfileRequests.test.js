import assert from 'node:assert/strict';
import test from 'node:test';
import {
  createSiteAccessProfileRequestGuard,
  siteAccessProfileBelongsToProject,
} from './siteAccessProfileRequests.js';

test('a later profile request makes an earlier response stale', () => {
  const guard = createSiteAccessProfileRequestGuard();
  const first = guard.begin(1);
  const second = guard.begin(1);

  assert.equal(guard.isCurrent(first, 1), false);
  assert.equal(guard.isCurrent(second, 1), true);
});

test('a profile response cannot cross a project switch', () => {
  const guard = createSiteAccessProfileRequestGuard();
  const request = guard.begin(1);

  assert.equal(guard.isCurrent(request, 2), false);
  assert.equal(guard.isCurrent(request, '1'), true);
});

test('stale profile panel and detail data are not renderable after a project switch', () => {
  const panel = { projectId: 1, records: [] };
  const detail = { id: 7, projectId: 1 };

  assert.equal(siteAccessProfileBelongsToProject(panel, 2), false);
  assert.equal(siteAccessProfileBelongsToProject(detail, 2), false);
});

test('closing the profile panel invalidates its pending response', () => {
  const guard = createSiteAccessProfileRequestGuard();
  const request = guard.begin(1);

  guard.invalidate();

  assert.equal(guard.isCurrent(request, 1), false);
});

test('profile mutations are allowed only in the profile real project context', () => {
  const profile = { id: 7, projectId: 11 };

  assert.equal(siteAccessProfileBelongsToProject(profile, 11), true);
  assert.equal(siteAccessProfileBelongsToProject(profile, '11'), true);
  assert.equal(siteAccessProfileBelongsToProject(profile, 12), false);
  assert.equal(siteAccessProfileBelongsToProject({ id: 7 }, 11), false);
});
