// 所有页面分享小程序入口，避免微信默认截取业务页或转发当前页面参数。
export const appSharing = {
  onShow() {
    uni.showShareMenu({ menus: ['shareAppMessage'] });
  },
  onShareAppMessage() {
    return {
      title: '智慧营造综合管理平台',
      path: '/pages/login/index',
      imageUrl: '/static/brand/zhihui-yingzao-horizontal.png'
    };
  }
};
