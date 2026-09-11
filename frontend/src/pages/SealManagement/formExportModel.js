export const canSelectForm = (row) => row.status === 'APPROVED' && row.canExportForm === true;

export function selectPageForms(selected, rows, checked) {
  const pageIds = rows.filter(canSelectForm).map((row) => row.id);
  return checked
    ? [...new Set([...selected, ...pageIds])]
    : selected.filter((id) => !pageIds.includes(id));
}

export function formExportRequest(projectId, scope, appliedFilters, mode, selected) {
  if (mode === 'SELECTED') return { projectId, selectionMode: mode, applicationIds: [...new Set(selected)].sort((a, b) => a - b) };
  return {
    projectId, selectionMode: 'FILTER', scope,
    keyword: appliedFilters.keyword || undefined,
    status: appliedFilters.status || undefined,
    startDate: appliedFilters.startDate || undefined,
    endDate: appliedFilters.endDate || undefined,
  };
}
