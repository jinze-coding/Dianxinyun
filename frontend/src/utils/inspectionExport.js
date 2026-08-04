const ISO_DATE_PATTERN = /^(\d{4})-(\d{2})-(\d{2})$/;
const DAY_MILLIS = 24 * 60 * 60 * 1000;

const parseIsoDate = (value) => {
  const match = ISO_DATE_PATTERN.exec(String(value || ''));
  if (!match) return null;
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const timestamp = Date.UTC(year, month - 1, day);
  const date = new Date(timestamp);
  if (date.getUTCFullYear() !== year
    || date.getUTCMonth() !== month - 1
    || date.getUTCDate() !== day) return null;
  return { year, month, day, timestamp };
};

const lastDateOfMonth = (monthValue) => {
  const match = /^(\d{4})-(\d{2})$/.exec(String(monthValue || ''));
  if (!match) return '';
  const year = Number(match[1]);
  const month = Number(match[2]);
  if (month < 1 || month > 12) return '';
  const day = new Date(Date.UTC(year, month, 0)).getUTCDate();
  return `${match[1]}-${match[2]}-${String(day).padStart(2, '0')}`;
};

export function getInspectionExportDefaults({ periodMode, month, checkDate, today }) {
  const normalizedToday = parseIsoDate(today) ? today : '';
  if (!normalizedToday) return { startDate: '', endDate: '' };

  if (periodMode === 'DAY') {
    const selectedDate = parseIsoDate(checkDate) && checkDate <= normalizedToday
      ? checkDate
      : normalizedToday;
    return { startDate: selectedDate, endDate: selectedDate };
  }

  const normalizedMonth = lastDateOfMonth(month) ? month : normalizedToday.slice(0, 7);
  const startDate = `${normalizedMonth}-01`;
  if (startDate > normalizedToday) {
    return { startDate: normalizedToday, endDate: normalizedToday };
  }
  const monthEndDate = lastDateOfMonth(normalizedMonth);
  return {
    startDate,
    endDate: monthEndDate < normalizedToday ? monthEndDate : normalizedToday,
  };
}

export function validateInspectionExportRange(startDate, endDate, today) {
  const start = parseIsoDate(startDate);
  const end = parseIsoDate(endDate);
  const current = parseIsoDate(today);
  if (!start || !end) return '请选择完整、有效的开始日期和结束日期';
  if (!current) return '当前日期无效，请刷新页面后重试';
  if (start.timestamp > end.timestamp) return '开始日期不能晚于结束日期';
  if (end.timestamp > current.timestamp) return '结束日期不能晚于今天';
  const inclusiveDays = Math.round((end.timestamp - start.timestamp) / DAY_MILLIS) + 1;
  if (inclusiveDays > 366) return '导出日期范围不能超过366天';
  return '';
}

export function buildInspectionExportParams({ projectId, startDate, endDate, allBoxes, selectedBoxIds }) {
  const normalizedIds = Array.from(new Set((selectedBoxIds || [])
    .map(id => String(id).trim())
    .filter(id => /^\d+$/.test(id) && Number(id) > 0)));
  return {
    projectId,
    templateCode: 'ELECTRIC_BOX_DAILY',
    startDate,
    endDate,
    boxIds: allBoxes ? undefined : normalizedIds.join(','),
  };
}

export function filterInspectionExportBoxes(boxes, keyword) {
  const normalizedKeyword = String(keyword || '').trim().toLowerCase();
  if (!normalizedKeyword) return boxes || [];
  return (boxes || []).filter(box => [
    box.boxCode,
    box.boxName,
    box.installLocation,
    box.responsibleElectricianName,
  ].some(value => String(value || '').toLowerCase().includes(normalizedKeyword)));
}

export function toggleInspectionExportBoxSelection(selectedBoxIds, boxId) {
  const normalizedId = String(boxId);
  const selected = new Set((selectedBoxIds || []).map(String));
  if (selected.has(normalizedId)) selected.delete(normalizedId);
  else selected.add(normalizedId);
  return Array.from(selected);
}
