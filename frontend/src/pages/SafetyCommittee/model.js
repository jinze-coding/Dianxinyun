/** Keep older pages stable while new submissions would shift offset pagination. */
export function mergeCommitteePage(current, incoming, page, latestId) {
  if (page > 1 && latestId !== null && latestId !== incoming.latestId) return { data: current, latestId, hasNew: true };
  return { data: incoming, latestId: incoming.latestId, hasNew: false };
}

export function validateCommitteeDateRange(startDate, endDate) {
  if (!startDate && !endDate) return '';
  if (!startDate || !endDate) return '请选择完整的开始日期和结束日期';
  const valid = (value) => /^[1-9]\d{3}-\d{2}-\d{2}$/.test(value)
    && !Number.isNaN(Date.parse(`${value}T00:00:00Z`))
    && new Date(`${value}T00:00:00Z`).toISOString().slice(0, 10) === value;
  if (!valid(startDate) || !valid(endDate)) return '请选择有效日期';
  return endDate < startDate ? '结束日期不能早于开始日期' : '';
}
