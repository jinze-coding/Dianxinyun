export function filterSiteVisitHosts(hosts = [], keyword = '') {
  const normalized = String(keyword || '').trim().toLocaleLowerCase('zh-CN');
  if (!normalized) return hosts;
  return hosts.filter((host) => [host?.realName, host?.phone]
    .some((value) => String(value || '').toLocaleLowerCase('zh-CN').includes(normalized)));
}
