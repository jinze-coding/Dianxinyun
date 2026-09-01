import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { createProjectRequestGuard } from './projectRequestContext.js';

const appSource = readFileSync(new URL('../App.jsx', import.meta.url), 'utf8');
const edgeSource = readFileSync(new URL('../pages/EdgeInspectionManagement/index.jsx', import.meta.url), 'utf8');

test('a later request invalidates an earlier response in the same project', () => {
  const guard = createProjectRequestGuard();
  const first = guard.begin(3);
  const second = guard.begin(3);

  assert.equal(guard.isCurrent(first, 3), false);
  assert.equal(guard.isCurrent(second, 3), true);
});

test('a response cannot cross a project switch and explicit invalidation closes the context', () => {
  const guard = createProjectRequestGuard();
  const request = guard.begin(3);

  assert.equal(guard.isCurrent(request, 4), false);
  assert.equal(guard.isCurrent(request, '3'), true);
  guard.invalidate();
  assert.equal(guard.isCurrent(request, 3), false);
});

test('electric inspection loading uses a project request ticket before applying responses', () => {
  assert.match(appSource, /inspectionDataRequestGuardRef/);
  assert.match(appSource, /begin\(targetProjectId\)/);
  assert.match(appSource, /isCurrent\(requestTicket, currentInspectionProjectIdRef\.current\)/);
});

test('electric inspection setting is only requested for setting managers', () => {
  assert.match(appSource, /inspectionSettingManageAllowed\s*\? getProjectInspectionSetting\(targetProjectId\)\s*: Promise\.resolve\(\{ code: 200, data: null \}\)/);
  assert.match(appSource, /if \(!inspectionSettingManageAllowed\) setInspectionSetting\(normalizeProjectInspectionSetting\(\)\)/);
  assert.match(appSource, /if \(inspectionSettingManageAllowed && settingRes\.code === 200 && settingRes\.data\)/);
});

test('edge task, todo, setting, statistics and export loaders all have project guards', () => {
  for (const guardName of [
    'taskRequestGuardRef',
    'todoRequestGuardRef',
    'settingRequestGuardRef',
    'statisticsRequestGuardRef',
    'exportRequestGuardRef',
  ]) {
    assert.match(edgeSource, new RegExp(`${guardName}\\.current`));
  }
  assert.match(edgeSource, /currentEdgeProjectIdRef\.current/);
});
