// 格式化工具函数

// 数字补零
export function padZero(num, length = 2) {
  return String(num).padStart(length, '0');
}

// 格式化日期时间
export function formatDateTime(date, format = 'YYYY-MM-DD HH:mm:ss') {
  if (!date) return '';
  const d = new Date(date);
  const year = d.getFullYear();
  const month = padZero(d.getMonth() + 1);
  const day = padZero(d.getDate());
  const hours = padZero(d.getHours());
  const minutes = padZero(d.getMinutes());
  const seconds = padZero(d.getSeconds());

  return format
    .replace('YYYY', year)
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hours)
    .replace('mm', minutes)
    .replace('ss', seconds);
}

// 格式化日期
export function formatDate(date, format = 'YYYY-MM-DD') {
  return formatDateTime(date, format);
}

// 格式化时间
export function formatTime(date, format = 'HH:mm:ss') {
  return formatDateTime(date, format);
}

// 格式化文件大小
export function formatFileSize(bytes) {
  if (!bytes || bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
}

// 格式化手机号（脱敏）
export function formatPhone(phone) {
  if (!phone) return '';
  if (phone.length === 11) {
    return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2');
  }
  return phone;
}

// 格式化身份证号（脱敏）
export function formatIdCard(idCard) {
  if (!idCard) return '';
  if (idCard.length === 18) {
    return idCard.replace(/(\d{6})\d{8}(\d{4})/, '$1********$2');
  }
  if (idCard.length === 15) {
    return idCard.replace(/(\d{6})\d{6}(\d{3})/, '$1******$2');
  }
  return idCard;
}

// 格式化数字（千分位）
export function formatNumber(num) {
  if (!num && num !== 0) return '';
  return String(num).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

// 格式化百分比
export function formatPercent(value, total, decimals = 0) {
  if (!total) return '0%';
  return ((value / total) * 100).toFixed(decimals) + '%';
}
