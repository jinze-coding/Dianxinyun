import { showToast } from '@/utils/navigation';

declare const wx: {
  requirePrivacyAuthorize(options: { success: () => void; fail: () => void }): void;
  openPrivacyContract(options: { fail: () => void }): void;
};

export function requireAgreement(accepted: boolean): boolean {
  if (accepted) return true;
  showToast('请先阅读并自主勾选同意用户服务协议和隐私政策');
  return false;
}

// 只在用户主动提交且勾选后调用；微信自身的隐私授权仍由用户决定。
export async function requestPrivacyAuthorization(): Promise<void> {
  // #ifdef MP-WEIXIN
  if (typeof wx === 'undefined' || typeof wx.requirePrivacyAuthorize !== 'function') {
    throw new Error('当前微信版本不支持隐私授权，请升级微信后重试');
  }
  await new Promise<void>((resolve, reject) => {
    wx.requirePrivacyAuthorize({
      success: () => resolve(),
      fail: () => reject(new Error('未完成微信隐私授权，操作未提交；您可以阅读后重新选择'))
    });
  });
  // #endif
}

export function openPrivacyPolicy(): void {
  // #ifdef MP-WEIXIN
  if (typeof wx !== 'undefined' && typeof wx.openPrivacyContract === 'function') {
    wx.openPrivacyContract({ fail: () => showToast('隐私政策暂时无法打开，请稍后重试或联系管理员') });
  } else {
    showToast('当前微信版本不支持查看隐私政策，请升级微信后重试');
  }
  // #endif
  // #ifdef H5
  uni.showModal({ title: '查看隐私政策', content: '请在微信中打开“智慧营造助手”，点击《隐私政策》查看本小程序运营者在微信平台配置的完整《隐私保护指引》。此提示不会代替您同意任何协议。', showCancel: false, confirmText: '知道了' });
  // #endif
}
