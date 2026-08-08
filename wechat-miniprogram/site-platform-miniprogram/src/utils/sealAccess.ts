import { bindCurrentUserWechat } from '@/api/auth';
import { getToken, USE_MOCK } from '@/api/request';
import { useAuthStore } from '@/stores/auth';
import type { User } from '@/types';
import { getFreshWechatCode } from '@/utils/wechat';

export function isWechatBoundUser(user: User | null) {
  return user?.wechatBound === true
    || user?.wechatBindingStatus === 'BOUND'
    || user?.wechatBindingStatus === 'ACTIVE';
}

function confirmWechatBinding() {
  return new Promise<boolean>((resolve) => {
    uni.showModal({
      title: '请先绑定微信',
      content: '用印申请需要绑定当前微信，便于恢复扫码申请和接收站内通知。是否现在绑定？',
      confirmText: '立即绑定',
      cancelText: '暂不绑定',
      success: (result) => resolve(Boolean(result.confirm)),
      fail: () => resolve(false)
    });
  });
}

export async function ensureSealPageAccess(resumeUrl: string) {
  const auth = useAuthStore();
  if (!getToken() && !USE_MOCK) {
    auth.rememberResumeUrl(resumeUrl);
    uni.reLaunch({ url: '/pages/login/index' });
    return false;
  }
  auth.rememberResumeUrl(resumeUrl);
  if (!await auth.ensureRootAccess('/pages/profile/index')) return false;
  if (USE_MOCK || isWechatBoundUser(auth.state.user)) {
    auth.takeResumeUrl();
    return true;
  }
  if (!await confirmWechatBinding()) return false;
  try {
    await bindCurrentUserWechat(await getFreshWechatCode());
    await auth.loadUser();
    if (!isWechatBoundUser(auth.state.user)) throw new Error('微信绑定状态未生效，请重试');
    // 页面已经恢复，不再保留本次临时回跳地址。
    auth.takeResumeUrl();
    return true;
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '微信绑定失败', icon: 'none' });
    return false;
  }
}
