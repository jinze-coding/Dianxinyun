export const RESPONSIBILITY_IMPACT_FIELDS = [
  ['responsibleElectricBoxCount', '电箱日检负责人'],
  ['safetyManagedElectricBoxCount', '电箱安全负责人'],
  ['pendingInspectionReviewCount', '电箱待复核记录'],
  ['openRectificationCount', '电箱整改任务'],
  ['pendingGeneralInspectionTaskCount', '临边待提交任务'],
  ['openGeneralRectificationCount', '临边整改任务'],
  ['pendingGeneralReviewCount', '临边待复查任务'],
  ['openQualityIssueCount', '质量整改问题'],
  ['qualityWeeklyReminderSettingCount', '质量周检提醒责任'],
  ['pendingSealApprovalCount', '待处理用印审批'],
  ['sealApprovalConfigCount', '用印审批配置'],
];

const countOf = (value) => {
  const count = Number(value || 0);
  return Number.isFinite(count) && count > 0 ? count : 0;
};

export function responsibilityImpactTotal(impacts = []) {
  return impacts.reduce((total, impact) => {
    const detailTotal = RESPONSIBILITY_IMPACT_FIELDS.reduce(
      (sum, [field]) => sum + countOf(impact?.[field]),
      0,
    );
    return total + Math.max(detailTotal, countOf(impact?.totalCount));
  }, 0);
}

export function responsibilityImpactLines(impacts = []) {
  return impacts.map((impact) => {
    const details = RESPONSIBILITY_IMPACT_FIELDS
      .map(([field, label]) => [label, countOf(impact?.[field])])
      .filter(([, count]) => count > 0)
      .map(([label, count]) => `${label}${count}项`);
    if (!details.length) return null;
    const projectName = impact?.projectName || (impact?.projectId ? `项目 ${impact.projectId}` : '未识别项目');
    return `${projectName}：${details.join('、')}`;
  }).filter(Boolean);
}
