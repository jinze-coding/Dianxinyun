import assert from 'node:assert/strict';
import test from 'node:test';

import {
  responsibilityImpactLines,
  responsibilityImpactTotal,
} from './responsibilityImpact.js';

test('responsibility impact summary covers electric box, edge, quality reminder and seal duties', () => {
  const impacts = [{
    projectId: 3,
    projectName: '示范项目',
    responsibleElectricBoxCount: 1,
    pendingGeneralInspectionTaskCount: 2,
    openGeneralRectificationCount: 3,
    pendingGeneralReviewCount: 4,
    qualityWeeklyReminderSettingCount: 1,
    pendingSealApprovalCount: 2,
    sealApprovalConfigCount: 1,
  }];

  assert.equal(responsibilityImpactTotal(impacts), 14);
  assert.deepEqual(responsibilityImpactLines(impacts), [
    '示范项目：电箱日检负责人1项、临边待提交任务2项、临边整改任务3项、临边待复查任务4项、质量周检提醒责任1项、待处理用印审批2项、用印审批配置1项',
  ]);
});

test('responsibility impact summary ignores zero-count projects and honors server totals', () => {
  const impacts = [
    { projectId: 1, totalCount: 0 },
    { projectId: 2, projectName: '项目二', totalCount: 5, openQualityIssueCount: 2 },
  ];

  assert.equal(responsibilityImpactTotal(impacts), 5);
  assert.deepEqual(responsibilityImpactLines(impacts), ['项目二：质量整改问题2项']);
});
