<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import AppTabBar from '@/components/AppTabBar.vue';
import { WORKSPACE_THEME } from '@/constants/workspaceTheme';
import { useAuthStore } from '@/stores/auth';
import { useProjectStore } from '@/stores/project';
import { usePageScrollHeight } from '@/utils/navLayout';
import { navigateTo } from '@/utils/navigation';
import { bindCurrentUserWechat, unbindCurrentUserWechat } from '@/api/auth';
import { getFreshWechatCode } from '@/utils/wechat';

const ACCENT = WORKSPACE_THEME.accent;
const TINT = WORKSPACE_THEME.tint;
const authStore = useAuthStore();
const projectStore = useProjectStore();
const loading = ref(false);
const errorMessage = ref('');
const bindingBusy = ref(false);
const showUnbindForm = ref(false);
const unbindPassword = ref('');
const { scrollStyle } = usePageScrollHeight({ bottomRpx: 124, minHeight: 320 });

const user = computed(() => authStore.state.user);
const currentProject = computed(() => projectStore.state.projects.find((item) => item.id === projectStore.state.currentProjectId));
const displayName = computed(() => user.value?.realName || user.value?.username || '当前用户');
const initials = computed(() => displayName.value.slice(-1));
const maskedPhone = computed(() => {
  const phone = user.value?.phone || '';
  if (phone.length < 7) return phone || '未绑定手机号';
  return `${phone.slice(0, 3)}****${phone.slice(-4)}`;
});
const currentRole = computed(() => user.value?.projectRoles?.find((item) => item.projectId === currentProject.value?.id));
const roleLabel = computed(() => {
  if (user.value?.roles?.includes('PLATFORM_ADMIN')) return '平台管理员';
  if (currentRole.value?.projectRoleCode === 'PROJECT_ADMIN') return '项目领导';
  if (currentRole.value?.projectRoleCode === 'SAFETY_ADMIN') return '项目领导';
  return '普通用户';
});
const authorizedProjectCount = computed(() => projectStore.state.projects.length);
const wechatBound = computed(() => user.value?.wechatBound === true
  || user.value?.wechatBindingStatus === 'BOUND'
  || user.value?.wechatBindingStatus === 'ACTIVE');
const requiresPasswordForUnbind = computed(() => {
  const value = user.value?.passwordLoginEnabled;
  return value === true || value === 1 || value === '1'
    || String(value || '').toUpperCase() === 'TRUE'
    || String(value || '').toUpperCase() === 'ENABLED';
});

function hideNativeTabBar() {
  uni.hideTabBar({ animation: false, fail: () => undefined });
}

onShow(async () => {
  hideNativeTabBar();
  loading.value = true;
  errorMessage.value = '';
  try {
    await Promise.all([authStore.loadUser(), projectStore.loadProjects()]);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '账号信息加载失败';
  } finally {
    loading.value = false;
  }
});

async function logout() {
  try {
    await authStore.logout();
  } finally {
    uni.reLaunch({ url: '/pages/login/index' });
  }
}

async function bindWechat() {
  if (bindingBusy.value) return;
  bindingBusy.value = true;
  try {
    await bindCurrentUserWechat(await getFreshWechatCode());
    await authStore.loadUser();
    uni.showToast({ title: '微信绑定成功', icon: 'none' });
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '绑定失败', icon: 'none' });
  } finally {
    bindingBusy.value = false;
  }
}

async function unbindWechat() {
  if (requiresPasswordForUnbind.value && !unbindPassword.value) {
    uni.showToast({ title: '请输入当前账号密码', icon: 'none' });
    return;
  }
  bindingBusy.value = true;
  try {
    await unbindCurrentUserWechat(unbindPassword.value || undefined);
    authStore.clearLocalSession();
    uni.showToast({ title: '微信已解绑，请重新登录', icon: 'none' });
    setTimeout(() => uni.reLaunch({ url: '/pages/login/index' }), 500);
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '解绑失败', icon: 'none' });
  } finally {
    bindingBusy.value = false;
  }
}
</script>

<template>
  <view class="workspace-shell profile-page" :style="{ '--page-accent': ACCENT, '--page-accent-deep': WORKSPACE_THEME.accentDeep, '--page-tint': TINT, '--page-background': WORKSPACE_THEME.page }">
    <AppNavBar title="我的" :show-back="false" />
    <scroll-view class="workspace-scroll" scroll-y enable-flex :style="scrollStyle">
      <view class="workspace-content">
        <view class="profile-hero">
          <view class="profile-avatar">{{ initials }}</view>
          <view class="profile-copy">
            <text class="profile-name">{{ displayName }}</text>
            <view class="profile-tags"><text>{{ roleLabel }}</text><text>{{ maskedPhone }}</text></view>
          </view>
        </view>

        <view v-if="errorMessage" class="error-line">{{ errorMessage }}</view>

        <button class="area-card" @tap="navigateTo('/pages/projects/index')">
          <view class="area-icon">区</view>
          <view class="area-copy">
            <text class="area-label">当前施工区域</text>
            <text class="area-name">{{ currentProject?.projectName || '暂无授权区域' }}</text>
            <text class="area-meta">点击切换 · 共 {{ authorizedProjectCount }} 个授权区域</text>
          </view>
          <text class="row-arrow"></text>
        </button>

        <view class="account-card">
          <view class="card-title"><text>账号与权限</text><text>当前登录信息</text></view>
          <view class="info-row"><text>登录账号</text><text>{{ user?.username || '—' }}</text></view>
          <view class="info-row"><text>当前角色</text><text>{{ roleLabel }}</text></view>
          <view class="info-row"><text>授权项目</text><text>{{ authorizedProjectCount }} 个</text></view>
        </view>

        <view class="account-card">
          <view class="card-title"><text>微信快捷登录</text><text>{{ wechatBound ? '已绑定' : '未绑定' }}</text></view>
          <view class="binding-body">
            <view class="binding-copy">
              <text>{{ wechatBound ? '当前微信可直接登录小程序和确认 Web 扫码登录。' : '绑定当前微信后，可使用微信快捷登录。' }}</text>
              <text v-if="wechatBound">解绑会立即注销当前账号的全部登录会话。</text>
            </view>
            <button v-if="!wechatBound" class="bind-button" :disabled="bindingBusy" @tap="bindWechat">
              {{ bindingBusy ? '绑定中…' : '绑定当前微信' }}
            </button>
            <template v-else>
              <button class="unbind-entry" @tap="showUnbindForm = !showUnbindForm">{{ showUnbindForm ? '取消解绑' : '解除绑定' }}</button>
              <view v-if="showUnbindForm" class="unbind-form">
                <template v-if="requiresPasswordForUnbind">
                  <text>请输入当前账号密码完成身份复核</text>
                  <input v-model="unbindPassword" password placeholder="当前账号密码" />
                </template>
                <text v-else class="blocked-tip">当前账号未启用密码登录，请联系平台管理员处理微信解绑。</text>
                <button v-if="requiresPasswordForUnbind" :disabled="bindingBusy" @tap="unbindWechat">确认解绑并退出</button>
              </view>
            </template>
          </view>
        </view>

        <button class="logout-button" :disabled="loading" @tap="logout">退出登录</button>
      </view>
    </scroll-view>
    <AppTabBar active="profile" />
  </view>
</template>

<style scoped src="../../styles/workspace-page.css"></style>
<style scoped>
.profile-hero { display: flex; align-items: center; gap: 22rpx; padding: 28rpx 24rpx; border-radius: 18rpx; background: var(--page-tint); box-shadow: var(--workspace-shadow); }
.profile-avatar { display: flex; width: 92rpx; height: 92rpx; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 50%; background: var(--page-accent); color: #fff; font-size: 34rpx; font-weight: 800; }
.profile-copy { min-width: 0; flex: 1; }
.profile-name { display: block; color: #1F2B3D; font-size: 32rpx; font-weight: 800; }
.profile-tags { display: flex; align-items: center; gap: 10rpx; margin-top: 10rpx; }
.profile-tags text { padding: 5rpx 10rpx; border-radius: 999rpx; background: rgba(255,255,255,.82); color: var(--page-accent-deep); font-size: 20rpx; }
.error-line { padding: 15rpx 18rpx; border-radius: 12rpx; background: #FFF3F2; color: #B94F4F; font-size: 21rpx; }
.area-card { display: flex; width: 100%; min-height: 130rpx; align-items: center; gap: 18rpx; padding: 20rpx 22rpx; border-radius: 18rpx; background: #fff; box-shadow: 0 9rpx 30rpx rgba(43,56,72,.06); text-align: left; }
.area-card::after,.logout-button::after { border: 0; }
.area-icon { display: flex; width: 62rpx; height: 62rpx; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 16rpx; background: var(--page-tint); color: var(--page-accent-deep); font-size: 22rpx; font-weight: 800; }
.area-copy { min-width: 0; flex: 1; }
.area-copy text { display: block; }
.area-label { color: #7A8798; font-size: 20rpx; }
.area-name { margin-top: 5rpx; color: #253247; font-size: 26rpx; font-weight: 750; }
.area-meta { margin-top: 6rpx; color: #98A2B3; font-size: 19rpx; }
.account-card { overflow: hidden; border-radius: 18rpx; background: #fff; box-shadow: 0 9rpx 30rpx rgba(43,56,72,.06); }
.card-title { display: flex; align-items: center; justify-content: space-between; padding: 22rpx; border-bottom: 1rpx solid #EEF1F4; }
.card-title text:first-child { color: #253247; font-size: 27rpx; font-weight: 760; }
.card-title text:last-child { color: #98A2B3; font-size: 19rpx; }
.info-row { display: flex; min-height: 78rpx; align-items: center; justify-content: space-between; margin: 0 22rpx; border-bottom: 1rpx solid #F0F2F4; }
.info-row:last-child { border-bottom: 0; }
.info-row text:first-child { color: #7A8798; font-size: 22rpx; }
.info-row text:last-child { color: #344054; font-size: 23rpx; font-weight: 650; }
.logout-button { min-height: 78rpx; border-radius: 15rpx; background: #fff; box-shadow: 0 7rpx 22rpx rgba(43,56,72,.04); color: #BE5555; font-size: 23rpx; font-weight: 750; }
.binding-body { padding: 22rpx; }
.binding-copy text { display: block; color: #6F7D8D; font-size: 21rpx; line-height: 1.55; }
.binding-copy text + text { margin-top: 4rpx; color: #B06A50; }
.bind-button,.unbind-entry { width: 100%; min-height: 68rpx; margin-top: 18rpx; border-radius: 13rpx; font-size: 22rpx; font-weight: 750; }
.bind-button { background: #EAF6F1; color: #2F8065; }
.unbind-entry { border: 1rpx solid #E9CFCF; background: #FFF8F8; color: #B75353; }
.unbind-form { margin-top: 14rpx; padding: 18rpx; border-radius: 13rpx; background: #FAF6F6; }
.unbind-form > text { display: block; color: #7A6464; font-size: 20rpx; }
.unbind-form input { height: 70rpx; margin-top: 12rpx; padding: 0 16rpx; border: 1rpx solid #E2CCCC; border-radius: 11rpx; background: #fff; font-size: 21rpx; }
.unbind-form button { width: 100%; min-height: 64rpx; margin-top: 12rpx; border-radius: 11rpx; background: #B75353; color: #fff; font-size: 21rpx; font-weight: 750; }
.blocked-tip { color: #B75353 !important; }
</style>
