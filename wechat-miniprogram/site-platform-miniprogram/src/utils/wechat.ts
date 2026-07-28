export function getFreshWechatCode(): Promise<string> {
  return new Promise((resolve, reject) => {
    // #ifdef MP-WEIXIN
    uni.login({
      provider: 'weixin',
      success: (result) => result.code ? resolve(result.code) : reject(new Error('微信登录凭证为空，请重试')),
      fail: () => reject(new Error('微信身份获取失败，请检查微信授权'))
    });
    // #endif
    // #ifndef MP-WEIXIN
    reject(new Error('微信快捷登录仅支持微信小程序'));
    // #endif
  });
}
