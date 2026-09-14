export function getQueryNumber(value: string | undefined, fallback: number): number {
  if (!value) {
    return fallback;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

export function navigateTo(url: string) {
  uni.navigateTo({ url });
}

export function switchTab(url: string) {
  if (url.split('?')[0] === '/pages/safety-committee/index') { uni.reLaunch({ url }); return; }
  uni.switchTab({ url, fail: () => uni.reLaunch({ url }) });
}

export function showToast(title: string) {
  uni.showToast({
    title,
    icon: 'none'
  });
}
