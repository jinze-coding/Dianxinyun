const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
const DAY_MS = 24 * 60 * 60 * 1000;

const pad = (value) => String(value).padStart(2, '0');

export function formatLocalDate(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function parseLocalDate(value) {
  if (!ISO_DATE.test(String(value || ''))) return null;
  const [year, month, day] = value.split('-').map(Number);
  const date = new Date(year, month - 1, day);
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) return null;
  return date;
}

export function siteVisitDateRange(mode, anchorValue, customStart = '', customEnd = '') {
  if (mode === 'CUSTOM') return { startDate: customStart, endDate: customEnd };
  const anchor = parseLocalDate(anchorValue) || new Date();
  if (mode === 'WEEK') {
    const weekday = anchor.getDay() || 7;
    const start = new Date(anchor);
    start.setDate(anchor.getDate() - weekday + 1);
    const end = new Date(start);
    end.setDate(start.getDate() + 6);
    return { startDate: formatLocalDate(start), endDate: formatLocalDate(end) };
  }
  if (mode === 'MONTH') {
    return {
      startDate: formatLocalDate(new Date(anchor.getFullYear(), anchor.getMonth(), 1)),
      endDate: formatLocalDate(new Date(anchor.getFullYear(), anchor.getMonth() + 1, 0)),
    };
  }
  const date = formatLocalDate(anchor);
  return { startDate: date, endDate: date };
}

export function validateSiteVisitDateRange(startDate, endDate) {
  const start = parseLocalDate(startDate);
  const end = parseLocalDate(endDate);
  if (!start || !end) return '请选择完整、有效的开始日期和结束日期';
  if (start > end) return '开始日期不能晚于结束日期';
  if (Math.round((end - start) / DAY_MS) + 1 > 366) return '日期范围不能超过366天';
  return '';
}
