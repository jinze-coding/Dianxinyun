<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { onBackPress, onHide, onLoad, onPageScroll, onShow, onUnload } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import { useVisitorPersonalInfo } from '@/utils/visitorPersonalInfo';
import ProjectLocationCard from '@/components/ProjectLocationCard.vue';
import NavigationDialog from '@/components/MeetingServiceDialog.vue';
import { ApiRequestError } from '@/api/request';
import {
  createPublicVisitorSession,
  downloadPublicProjectProfileImage,
  downloadPublicProjectRouteImage,
  getPublicProjectProfile,
  removePublicProjectProfileImages,
  removePublicProjectRouteImage,
  resolvePublicSiteVisit,
  submitPublicSiteVisit,
  type PublicSiteVisitInvitation,
  type PublicSiteVisitSubmitPayload,
  type PublicProjectProfile,
  type SiteVisitCompanionInput,
} from '@/api/siteAccess';
import { extractVisitorInviteToken } from '@/utils/visitorInviteScene';
import {
  isVisitorSessionAuthorizationError
} from '@/utils/visitorProfileFlow';
import { showToast } from '@/utils/navigation';
import { getFreshWechatCode } from '@/utils/wechat';

const token = ref('');
const invitation = ref<PublicSiteVisitInvitation>();
const loading = ref(true);
const submitting = ref(false);
const errorMessage = ref('');
const visitorCompany = ref('');
const contactName = ref('');
const contactPhone = ref('');
const companions = ref<SiteVisitCompanionInput[]>([]);
const travelMode = ref<'DRIVING' | 'OTHER'>('OTHER');
const vehiclePlate = ref('');
const { personalInfoApplied, applyPersonalInfo, resetPersonalInfo } =
  useVisitorPersonalInfo({ visitorCompany, contactName, contactPhone, travelMode, vehiclePlate });
const visitorRemark = ref('');
const privacyAgreed = ref(false);
const visitorSessionToken = ref('');
const identityLoading = ref(false);
const identityNotice = ref('');
let identityRequestId = 0;
let loadRequestId = 0;
let disposed = false;
const showingProjectProfile = ref(false);
const projectProfile = ref<PublicProjectProfile>();
const projectProfileLoading = ref(false);
const projectProfileError = ref('');
const projectImagePaths = ref<string[]>([]);
const projectImageError = ref('');
let projectProfileRequestId = 0;
const projectRouteImagePath = ref('');
const projectRouteImageLoading = ref(false);
const projectRouteImageError = ref('');
let projectRouteImageRequestId = 0;
const currentTime = ref(Date.now());
let clockTimer: ReturnType<typeof setInterval> | undefined;
let serverOffset = 0;
const navigationVisible = ref(false);
const foreground = ref(true);
const navigationVerified = ref(false);
const navigationError = ref('');
const navigationScrollTarget = ref(0);
const publicAccessDenied = ref(false);
let navigationScrollTop = 0;
let navigationRestoreTop: number | undefined;
let pageScrollTop = 0;
let returnScrollTop = 0;
let navigationEpoch = 0;
let navigationTask: Promise<void> | undefined;
let navigationTimer: ReturnType<typeof setInterval> | undefined;

type PublicProjectProfileKey = keyof PublicProjectProfile;
interface PublicProfileField { key: PublicProjectProfileKey; label: string; suffix?: string }
interface PublicProfileGroup { title: string; fields: PublicProfileField[] }

const publicProfileGroups: PublicProfileGroup[] = [
  { title: '基本信息', fields: [
    { key: 'projectName', label: '项目名称' }, { key: 'shortName', label: '项目简称' },
    { key: 'phase', label: '工程状态' }, { key: 'engineeringType', label: '工程类型' },
    { key: 'address', label: '项目地址' }, { key: 'description', label: '项目简介' }
  ] },
  { title: '建设周期', fields: [
    { key: 'startDate', label: '计划开工' }, { key: 'endDate', label: '计划竣工' },
    { key: 'actualStartDate', label: '实际开工' }, { key: 'actualEndDate', label: '实际竣工' }
  ] },
  { title: '参建单位', fields: [
    { key: 'directCompany', label: '直属公司' }, { key: 'ownerUnit', label: '建设单位' },
    { key: 'supervisionUnit', label: '监理单位' }, { key: 'designUnit', label: '设计单位' },
    { key: 'contractor', label: '施工单位' }
  ] },
  { title: '规模指标', fields: [
    { key: 'buildingArea', label: '建筑面积', suffix: ' ㎡' }, { key: 'landArea', label: '用地面积', suffix: ' ㎡' },
    { key: 'buildingHeight', label: '建筑高度', suffix: ' m' }, { key: 'excavationDepth', label: '开挖深度', suffix: ' m' },
    { key: 'undergroundFloorCount', label: '地下层数', suffix: ' 层' }, { key: 'abovegroundFloorCount', label: '地上层数', suffix: ' 层' },
    { key: 'projectScale', label: '项目规模' }, { key: 'projectClassification', label: '项目分类' },
    { key: 'projectLevel', label: '项目级别' }
  ] },
  { title: '建设目标', fields: [
    { key: 'projectTarget', label: '项目目标' }, { key: 'qualityGoal', label: '质量目标' },
    { key: 'safetyGoal', label: '安全目标' }, { key: 'greenConstructionGoal', label: '绿色建造目标' }
  ] }
];

const invitationExpired = computed(() => {
  const current = invitation.value;
  if (!current || !['PENDING', 'SUBMITTED'].includes(current.status)) return current?.status === 'EXPIRED';
  const endTime = new Date(current.visitEndTime).getTime();
  return Number.isFinite(endTime) && currentTime.value >= endTime;
});

const statusText = computed(() => {
  if (invitationExpired.value) return '本次邀请已过期';
  if (invitation.value?.status === 'SUBMITTED') return '本次外访信息已提交';
  if (invitation.value?.status === 'EXPIRED') return '本次邀请已过期';
  if (invitation.value?.status === 'VOIDED') return '本次邀请已作废';
  return '';
});

onLoad(async (options) => {
  // #ifndef MP-WEIXIN
  errorMessage.value = '外访登记仅支持微信小程序扫码填写';
  loading.value = false;
  return;
  // #endif
  token.value = extractVisitorInviteToken(options as Record<string, unknown>);
  await load();
});

async function load() {
  const requestId = ++loadRequestId;
  loading.value = true;
  errorMessage.value = '';
  invalidateIdentity();
  void closeNavigation();
  publicAccessDenied.value = false;
  if (!token.value) {
    errorMessage.value = '邀请小程序码无效';
    loading.value = false;
    return;
  }
  try {
    const current = await resolvePublicSiteVisit(token.value);
    if (disposed || requestId !== loadRequestId) return;
    if (current.inviteType === 'MEETING') throw new Error('请使用单次预约邀请的小程序码');
    invitation.value = current;
    loading.value = false;
    if (['PENDING', 'SUBMITTED'].includes(invitation.value.status)) {
      syncServerClock(invitation.value.serverTime);
      startClock();
      if (invitation.value.status === 'PENDING') void loadVisitorIdentity();
    } else {
      stopClock();
    }
  } catch (error) {
    if (disposed || requestId !== loadRequestId) return;
    invitation.value = undefined;
    errorMessage.value = error instanceof Error ? error.message : '邀请加载失败';
  } finally {
    if (!disposed && requestId === loadRequestId) loading.value = false;
  }
}

async function loadVisitorIdentity() {
  const requestId = ++identityRequestId;
  identityLoading.value = true;
  identityNotice.value = '';
  try {
    const session = await createPublicVisitorSession(token.value, await getFreshWechatCode());
    if (requestId !== identityRequestId) return;
    visitorSessionToken.value = session.visitorSessionToken;
    applyPersonalInfo(session.personalInfo);

  } catch (error) {
    if (requestId !== identityRequestId) return;
    resetVisitorIdentity('微信身份识别失败，已保留填写内容，请重新识别后提交。', false);
  } finally {
    if (requestId === identityRequestId) identityLoading.value = false;
  }
}

function invalidateIdentity() {
  identityRequestId += 1;
  identityLoading.value = false;
}

function resetVisitorIdentity(notice = '', invalidateBootstrap = true) {
  if (invalidateBootstrap) invalidateIdentity();
  visitorSessionToken.value = '';
  identityNotice.value = notice;
  identityLoading.value = false;
}

function syncServerClock(serverTime?: string) {
  const parsed = serverTime ? new Date(serverTime).getTime() : NaN;
  serverOffset = Number.isFinite(parsed) ? parsed - Date.now() : 0;
  currentTime.value = Date.now() + serverOffset;
}

function startClock() {
  stopClock();
  clockTimer = setInterval(() => { currentTime.value = Date.now() + serverOffset; }, 1000);
}

function stopClock() {
  if (clockTimer) clearInterval(clockTimer);
  clockTimer = undefined;
}

function formatTime(value?: string | number, seconds = false) {
  if (!value) return '-';
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return '-';
  const pad = (item: number) => String(item).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}${seconds ? `:${pad(date.getSeconds())}` : ''}`;
}

function addCompanion() {
  if (companions.value.length >= 49) {
    showToast('一次来访最多登记50人');
    return;
  }
  companions.value.push({ personCompany: '', personName: '', personPhone: '' });
}

function removeCompanion(index: number) {
  companions.value.splice(index, 1);
}

function hasCompanionContent(item: SiteVisitCompanionInput) {
  return Boolean(item.personCompany.trim() || item.personName.trim() || item.personPhone.trim());
}

const filledCompanionCount = computed(() => companions.value.filter(hasCompanionContent).length);

function validate() {
  if (!visitorSessionToken.value) return '请先重新识别微信身份';
  if (!visitorCompany.value.trim()) return '请填写单位';
  if (!contactName.value.trim()) return '请填写姓名';
  if (!/^1[3-9]\d{9}$/.test(contactPhone.value.trim())) return '请填写正确的手机号码';
  for (let index = 0; index < companions.value.length; index += 1) {
    const phone = companions.value[index].personPhone.trim();
    if (phone && !/^1[3-9]\d{9}$/.test(phone)) return `请填写第${index + 1}位同行人员的正确手机号码`;
  }
  if (travelMode.value === 'DRIVING' && !vehiclePlate.value.trim()) return '驾车来访请填写车牌号';
  if (!privacyAgreed.value) return '请阅读并同意隐私告知';

  return '';
}

async function submit() {
  if (submitting.value || invitation.value?.status !== 'PENDING') return;
  const message = validate();
  if (message) {
    showToast(message);
    return;
  }
  submitting.value = true;
  errorMessage.value = '';
  try {
    const payload: PublicSiteVisitSubmitPayload = {
      inviteToken: token.value,
      visitorCompany: visitorCompany.value.trim(),
      contactName: contactName.value.trim(),
      contactPhone: contactPhone.value.trim(),
      companions: companions.value.filter(hasCompanionContent).map((item) => ({
        personCompany: item.personCompany.trim(),
        personName: item.personName.trim(),
        personPhone: item.personPhone.trim()
      })),
      travelMode: travelMode.value,
      vehiclePlate: travelMode.value === 'DRIVING' ? vehiclePlate.value.trim().toUpperCase() : undefined,
      visitorRemark: visitorRemark.value.trim() || undefined,
      privacyAgreed: true,

    };
    const data = await submitPublicSiteVisit(payload, visitorSessionToken.value);
    invitation.value = data;
    resetProjectRouteImage();
    syncServerClock(data.serverTime);
    startClock();
    clearSensitiveForm();
    showToast('外访信息提交成功');
  } catch (error) {
    if (isVisitorSessionAuthorizationError(error)) resetVisitorIdentity('微信身份已过期，请重新识别后提交；已填写内容仍保留。');
    errorMessage.value = error instanceof Error ? error.message : '外访信息提交失败';
    showToast(errorMessage.value);
  } finally {
    submitting.value = false;
  }
}

function clearSensitiveForm() {
  invalidateIdentity();
  visitorCompany.value = '';
  contactName.value = '';
  contactPhone.value = '';
  companions.value = [];
  vehiclePlate.value = '';
  visitorRemark.value = '';
  privacyAgreed.value = false;
  visitorSessionToken.value = '';
  identityNotice.value = '';
  resetPersonalInfo();
}

function privacyChange(event: { detail: { value: string[] } }) {
  privacyAgreed.value = event.detail.value.includes('agreed');
}

const canViewProjectProfile = computed(() => {
  const current = invitation.value;
  if (!current || !['PENDING', 'SUBMITTED'].includes(current.status)) return false;
  const endTime = new Date(current.visitEndTime).getTime();
  return Number.isFinite(endTime) && endTime > currentTime.value;
});

const invitationPassExpired = computed(() => {
  const current = invitation.value;
  if (!current || current.status !== 'SUBMITTED') return false;
  return invitationExpired.value;
});

const canShowNavigation = computed(() => canViewProjectProfile.value
  && invitation.value?.inviteType !== 'MEETING'
  && Boolean(invitation.value?.projectLocation) && !publicAccessDenied.value);

async function restoreNavigationScroll() {
  const target = navigationRestoreTop ?? navigationScrollTop;
  navigationScrollTarget.value = -1;
  await nextTick();
  navigationScrollTarget.value = target;
}

function rememberNavigationScroll(event: { detail: { scrollTop: number } }) {
  if (!foreground.value || !navigationVerified.value || projectRouteImageLoading.value) return;
  // 地图/大图返回及图片布局时，不用临时归零的滚动事件覆盖原位置。
  if (navigationRestoreTop !== undefined && Math.abs(event.detail.scrollTop - navigationRestoreTop) > 2) return;
  navigationRestoreTop = undefined;
  navigationScrollTop = event.detail.scrollTop;
}

function openNavigation() {
  if (!canShowNavigation.value || navigationVisible.value) return;
  uni.hideKeyboard();
  returnScrollTop = pageScrollTop;
  navigationRestoreTop = navigationScrollTop || undefined;
  navigationVisible.value = true;
  navigationVerified.value = false;
  navigationError.value = '';
  void refreshNavigation(true);
}

async function closeNavigation() {
  const wasVisible = navigationVisible.value;
  navigationVisible.value = false;
  navigationVerified.value = false;
  navigationEpoch += 1;
  navigationTask = undefined;
  resetProjectRouteImage();
  await nextTick();
  if (wasVisible && !disposed && !navigationVisible.value) uni.pageScrollTo({ scrollTop: returnScrollTop, duration: 0 });
}

function refreshNavigation(reloadRoute = false): Promise<void> {
  if (disposed || !navigationVisible.value || !foreground.value) return Promise.resolve();
  if (navigationTask) return navigationTask;
  const ticket = navigationEpoch;
  let pending: Promise<void>;
  pending = resolvePublicSiteVisit(token.value).then(async (current) => {
    if (ticket !== navigationEpoch || !foreground.value || !navigationVisible.value) return;
    if (current.inviteType === 'MEETING') {
      publicAccessDenied.value = true;
      void closeNavigation();
      return;
    }
    invitation.value = current;
    syncServerClock(current.serverTime);
    if (!canShowNavigation.value) {
      void closeNavigation();
      return;
    }
    navigationError.value = '';
    navigationVerified.value = true;
    if (reloadRoute) await loadProjectRouteImage();
    if (ticket === navigationEpoch) void restoreNavigationScroll();
  }).catch((error: unknown) => {
    if (ticket !== navigationEpoch || !foreground.value || !navigationVisible.value) return;
    navigationVerified.value = false;
    navigationError.value = error instanceof Error ? error.message : '邀请状态核验失败，请重试';
    resetProjectRouteImage();
    if (error instanceof ApiRequestError && [400, 401, 403, 404, 410].includes(error.statusCode || error.code || 0)) {
      publicAccessDenied.value = true;
      void closeNavigation();
    }
  }).finally(() => { if (navigationTask === pending) navigationTask = undefined; });
  navigationTask = pending;
  return pending;
}

onShow(() => {
  foreground.value = true;
  currentTime.value = Date.now() + serverOffset;
  if (invitation.value && !invitationExpired.value) startClock();
  if (navigationVisible.value) void refreshNavigation(true);
});
onHide(() => {
  if (navigationVisible.value) navigationRestoreTop = navigationScrollTop || undefined;
  foreground.value = false;
  navigationVerified.value = false;
  navigationEpoch += 1;
  navigationTask = undefined;
  projectRouteImageRequestId += 1;
  stopClock();
});
onPageScroll((event) => { if (!navigationVisible.value) pageScrollTop = event.scrollTop; });
watch([navigationVisible, foreground], ([visible, shown]) => {
  if (navigationTimer) clearInterval(navigationTimer);
  navigationTimer = undefined;
  if (visible && shown) navigationTimer = setInterval(() => { void refreshNavigation(); }, 30000);
});
watch(canShowNavigation, (available) => {
  if (!available && navigationVisible.value) void closeNavigation();
}, { flush: 'sync' });

async function openProjectProfile() {
  if (!canViewProjectProfile.value || projectProfileLoading.value) return;
  const requestId = ++projectProfileRequestId;
  showingProjectProfile.value = true;
  projectProfileLoading.value = true;
  projectProfileError.value = '';
  projectImageError.value = '';
  removePublicProjectProfileImages(projectImagePaths.value);
  projectImagePaths.value = [];
  try {
    const result = await getPublicProjectProfile(token.value);
    if (requestId !== projectProfileRequestId || !showingProjectProfile.value) return;
    projectProfile.value = result;
    const imageResults = await Promise.allSettled((result.images || []).map((item) =>
      downloadPublicProjectProfileImage(token.value, item.imageIndex, item.mimeType)
    ));
    const downloadedPaths = imageResults.flatMap((item) => item.status === 'fulfilled' ? [item.value] : []);
    if (requestId !== projectProfileRequestId || !showingProjectProfile.value) {
      removePublicProjectProfileImages(downloadedPaths);
      return;
    }
    projectImagePaths.value = downloadedPaths;
    const failed = imageResults.length - projectImagePaths.value.length;
    if (failed > 0) projectImageError.value = `${failed} 张效果图加载失败，可稍后重试`;
  } catch (error) {
    if (requestId !== projectProfileRequestId || !showingProjectProfile.value) return;
    projectProfile.value = undefined;
    projectProfileError.value = error instanceof Error ? error.message : '项目信息加载失败';
  } finally {
    if (requestId === projectProfileRequestId) projectProfileLoading.value = false;
  }
}

function closeProjectProfile() {
  projectProfileRequestId += 1;
  showingProjectProfile.value = false;
  projectProfileLoading.value = false;
  removePublicProjectProfileImages(projectImagePaths.value);
  projectImagePaths.value = [];
  projectProfile.value = undefined;
  projectProfileError.value = '';
  projectImageError.value = '';
}

function publicFieldValue(field: PublicProfileField) {
  const value = projectProfile.value?.[field.key];
  if (value === null || value === undefined || value === '' || Array.isArray(value)) return '';
  return `${String(value)}${field.suffix || ''}`;
}

function visiblePublicFields(group: PublicProfileGroup) {
  return group.fields.filter((field) => Boolean(publicFieldValue(field)));
}

function previewProjectImage(index: number) {
  if (!projectImagePaths.value.length) return;
  uni.previewImage({ urls: projectImagePaths.value, current: projectImagePaths.value[index] });
}

function resetProjectRouteImage() {
  projectRouteImageRequestId += 1;
  projectRouteImageLoading.value = false;
  projectRouteImageError.value = '';
  removePublicProjectRouteImage(projectRouteImagePath.value);
  projectRouteImagePath.value = '';
}

async function loadProjectRouteImage() {
  if (!navigationVisible.value || !foreground.value || !navigationVerified.value || !canShowNavigation.value) return;
  const requestId = ++projectRouteImageRequestId;
  removePublicProjectRouteImage(projectRouteImagePath.value);
  projectRouteImagePath.value = '';
  projectRouteImageError.value = '';
  projectRouteImageLoading.value = false;
  if (!invitation.value?.projectLocation?.routeImageAvailable) return;

  projectRouteImageLoading.value = true;
  try {
    const filePath = await downloadPublicProjectRouteImage(token.value);
    if (requestId !== projectRouteImageRequestId) {
      removePublicProjectRouteImage(filePath);
      return;
    }
    projectRouteImagePath.value = filePath;
  } catch {
    if (requestId === projectRouteImageRequestId) {
      projectRouteImageError.value = '到访路线图加载失败，不影响地图导航';
    }
  } finally {
    if (requestId === projectRouteImageRequestId) projectRouteImageLoading.value = false;
  }
}

function handleBack() {
  if (navigationVisible.value) {
    void closeNavigation();
    return;
  }
  if (showingProjectProfile.value) {
    closeProjectProfile();
    return;
  }
  goBack();
}

onBackPress(() => {
  if (navigationVisible.value) {
    void closeNavigation();
    return true;
  }
  if (!showingProjectProfile.value) return false;
  closeProjectProfile();
  return true;
});

function cleanup() {
  disposed = true;
  loadRequestId += 1;
  invalidateIdentity();
  stopClock();
  void closeNavigation();
  if (navigationTimer) clearInterval(navigationTimer);
  navigationTimer = undefined;
  projectProfileRequestId += 1;
  removePublicProjectProfileImages(projectImagePaths.value);
  projectImagePaths.value = [];
}

watch(invitationExpired, (expired) => {
  if (!expired) return;
  invalidateIdentity();
  resetProjectRouteImage();
  if (showingProjectProfile.value) closeProjectProfile();
});

onUnload(cleanup);
onBeforeUnmount(cleanup);

function goBack() {
  if (getCurrentPages().length > 1) uni.navigateBack();
  else uni.exitMiniProgram();
}
</script>

<template>
  <page-meta :page-style="navigationVisible ? 'overflow:hidden' : ''" />
  <view class="visitor-shell">
    <view class="entry-navbar"><AppNavBar :title="showingProjectProfile ? '项目信息' : '外访登记'" @back="handleBack" /></view>
    <view v-if="showingProjectProfile" class="project-profile-content">
      <view v-if="projectProfileLoading" class="visitor-card state-card">正在加载项目信息...</view>
      <view v-else-if="projectProfileError" class="visitor-card state-card error-state">
        <text>项目信息暂时无法查看</text>
        <text>{{ projectProfileError }}</text>
        <button @tap="openProjectProfile">重新加载</button>
      </view>
      <template v-else-if="projectProfile">
        <view class="public-project-hero">
          <text class="public-project-label">PROJECT PROFILE</text>
          <text class="public-project-title">{{ projectProfile.projectName }}</text>
          <text class="public-project-subtitle">{{ projectProfile.shortName || '项目简介' }} · {{ projectProfile.phase || '建设信息' }}</text>
        </view>
        <view class="public-project-card public-project-gallery">
          <view class="public-project-section-head"><text>项目效果图</text><text>{{ projectImagePaths.length }} 张</text></view>
          <swiper v-if="projectImagePaths.length" class="public-project-swiper" indicator-dots circular>
            <swiper-item v-for="(path, index) in projectImagePaths" :key="path">
              <image :src="path" mode="aspectFill" @tap="previewProjectImage(index)" />
            </swiper-item>
          </swiper>
          <view v-else class="public-project-empty-image">暂无可展示的项目效果图</view>
          <text v-if="projectImageError" class="public-project-image-error">{{ projectImageError }}</text>
        </view>
        <view v-for="group in publicProfileGroups" v-show="visiblePublicFields(group).length" :key="group.title" class="public-project-card">
          <view class="public-project-section-head"><text>{{ group.title }}</text></view>
          <view class="public-project-fields">
            <view v-for="field in visiblePublicFields(group)" :key="field.key" class="public-project-field">
              <text>{{ field.label }}</text><text>{{ publicFieldValue(field) }}</text>
            </view>
          </view>
        </view>
        <view class="public-project-boundary">公开简介仅在本次来访结束前可查看，不包含联系方式、合同证照及内部管理数据。</view>
      </template>
    </view>
    <view v-else class="visitor-content">
      <view v-if="loading" class="visitor-card state-card">正在加载外访邀请...</view>
      <view v-else-if="errorMessage && !invitation" class="visitor-card state-card error-state">
        <text>无法打开外访邀请</text>
        <text>{{ errorMessage }}</text>
        <button @tap="load">重新加载</button>
      </view>
      <template v-else-if="invitation">
        <view v-if="invitation.status === 'PENDING' && !invitationExpired" class="visitor-card invite-card">
          <view class="invite-top"><text>{{ invitation.projectShortName || invitation.projectName }}</text><text>{{ invitation.inviteNo }}</text></view>
          <text class="invite-title">外访人员提前登记</text>
          <view class="invite-info"><text>来访时间</text><text>{{ formatTime(invitation.visitStartTime) }} 至 {{ formatTime(invitation.visitEndTime) }}</text></view>
          <view class="invite-info"><text>来访事由</text><text>{{ invitation.purpose }}</text></view>
          <view class="invite-info"><text>到访地点</text><text>{{ invitation.visitLocation }}</text></view>
          <view class="invite-info"><text>接待人</text><text>{{ invitation.hostName }} {{ invitation.hostPhone || '' }}</text></view>
          <button v-if="canViewProjectProfile" class="project-info-button" @tap="openProjectProfile">项目信息</button>
        </view>

        <button v-if="invitation.status === 'PENDING' && canShowNavigation" class="visitor-navigation-button" @tap="openNavigation">
          访客导航 <text class="visitor-navigation-arrow">›</text>
        </button>

        <template v-if="invitation.status === 'SUBMITTED'">
          <view class="visitor-card pass-card" :class="{ expired: invitationPassExpired }">
            <view class="pass-check">{{ invitationPassExpired ? '!' : '✓' }}</view>
            <text class="pass-label">{{ invitationPassExpired ? '预约放行已过期' : '已登记 · 门卫放行' }}</text>
            <text class="pass-project">{{ invitation.projectShortName || invitation.projectName }}</text>
            <view class="pass-current"><text>当前时间</text><text>{{ formatTime(currentTime, true) }}</text></view>
            <view class="pass-grid">
              <text>邀请编号</text><text>{{ invitation.inviteNo }}</text>
              <text>单位</text><text>{{ invitation.visitorCompany || '-' }}</text>
              <text>姓名</text><text>{{ invitation.contactName || '-' }}</text>
              <text>来访人数</text><text>{{ invitation.visitorCount || 0 }} 人</text>
              <text>车辆</text><text>{{ invitation.travelMode === 'DRIVING' ? (invitation.vehiclePlate || '驾车') : '非驾车' }}</text>
              <text>登记时间</text><text>{{ formatTime(invitation.submittedTime) }}</text>
              <text>计划到场</text><text>{{ formatTime(invitation.visitStartTime) }}</text>
              <text>有效截止</text><text>{{ formatTime(invitation.visitEndTime) }}</text>
            </view>
            <text class="pass-hint">{{ invitationPassExpired ? '本次预约已超过计划离场时间，请联系项目接待人重新创建邀请。' : '本次预约提交后立即登记成功，无需审批；请将本页面出示给门卫核验。' }}</text>
            <button v-if="canViewProjectProfile" class="project-info-button" @tap="openProjectProfile">项目信息</button>
          </view>
          <button v-if="canShowNavigation" class="visitor-navigation-button" @tap="openNavigation">
            访客导航 <text class="visitor-navigation-arrow">›</text>
          </button>
        </template>

        <view v-else-if="invitationExpired || invitation.status !== 'PENDING'" class="visitor-card result-card" :class="invitationExpired ? 'expired' : invitation.status.toLowerCase()">
          <text class="result-mark">!</text>
          <text class="result-title">{{ statusText }}</text>
          <text>请联系项目接待人重新创建本次来访邀请。</text>
        </view>

        <template v-else>
          <view v-if="!visitorSessionToken" class="visitor-card identity-status">
            <text>{{ identityNotice || '正在识别微信身份...' }}</text>
            <button :disabled="identityLoading" @tap="loadVisitorIdentity">{{ identityLoading ? '识别中...' : '重新识别微信身份' }}</button>
          </view>

          <view class="visitor-card form-card">
            <text class="section-title">来访人员信息</text>
            <label class="visitor-field"><text>单位 *</text><input v-model="visitorCompany" maxlength="200" placeholder="请输入单位全称" placeholder-class="entry-placeholder" :cursor-spacing="24" /></label>
            <label class="visitor-field"><text>姓名 *</text><input v-model="contactName" maxlength="50" placeholder="请输入姓名" placeholder-class="entry-placeholder" :cursor-spacing="24" /></label>
            <label class="visitor-field"><text>手机号码 *</text><input v-model="contactPhone" type="number" maxlength="11" placeholder="仅校验格式，不发送验证码" placeholder-class="entry-placeholder" :cursor-spacing="24" /></label>
          </view>

          <view class="visitor-card form-card">
            <view class="section-head"><text class="section-title">同行人员（选填）</text><button :disabled="companions.length >= 49" @tap="addCompanion">添加</button></view>
            <text v-if="!companions.length" class="empty-copy">没有同行人员可不添加，本人已计入总人数。</text>
            <view v-for="(person, index) in companions" :key="index" class="companion-card">
              <view class="companion-title"><text>同行人员 {{ index + 1 }}</text><button @tap="removeCompanion(index)">移除</button></view>
              <label class="visitor-field companion-field"><text>单位（选填）</text><input v-model="person.personCompany" maxlength="200" placeholder="请输入同行人员单位" placeholder-class="entry-placeholder" :cursor-spacing="24" /></label>
              <label class="visitor-field companion-field"><text>姓名（选填）</text><input v-model="person.personName" maxlength="50" placeholder="请输入同行人员姓名" placeholder-class="entry-placeholder" :cursor-spacing="24" /></label>
              <label class="visitor-field companion-field"><text>手机号码（选填）</text><input v-model="person.personPhone" type="number" maxlength="11" placeholder="填写时校验手机号码格式" placeholder-class="entry-placeholder" :cursor-spacing="24" /></label>
            </view>
            <text class="limit-copy">本次已填写 {{ filledCompanionCount + 1 }} 人，最多登记50人；空白同行卡片不会保存。</text>
          </view>

          <view class="visitor-card form-card">
            <text class="section-title">车辆与备注</text>
            <view class="travel-tabs"><button :class="{ active: travelMode === 'OTHER' }" @tap="travelMode = 'OTHER'">非驾车</button><button :class="{ active: travelMode === 'DRIVING' }" @tap="travelMode = 'DRIVING'">驾车</button></view>
            <label v-if="travelMode === 'DRIVING'" class="visitor-field"><text>车牌号 *</text><input v-model="vehiclePlate" maxlength="20" placeholder="请输入本次来访车辆车牌" placeholder-class="entry-placeholder" :cursor-spacing="24" /></label>
            <label class="visitor-field"><text>外访备注</text><textarea v-model="visitorRemark" maxlength="500" placeholder="可填写需要接待人提前了解的事项" /></label>
          </view>

          <view class="visitor-card privacy-card">
            <view class="personal-info-note" v-if="personalInfoApplied">已自动带入本人和车辆资料，请核对本次信息；同行人员另行填写。</view>

          <checkbox-group @change="privacyChange">
              <label class="privacy-check"><checkbox value="agreed" :checked="privacyAgreed" color="#315f86" /><text>我已阅读并同意隐私告知</text></label>
            </checkbox-group>
            <text class="privacy-copy">为完成项目现场外访报备及后续接待，系统将收集姓名、手机号码、单位和车牌信息，仅授权项目人员可查看。本人单位、姓名、手机号码、出行方式和车牌在提交成功后保存，用于本小程序后续跨项目扫码自动填写。系统不采集身份证信息，提交后访客不能自行修改。</text>
          </view>

          <view v-if="errorMessage" class="submit-error">{{ errorMessage }}</view>
          <button class="submit-button" :disabled="submitting || identityLoading || !visitorSessionToken" @tap="submit">{{ submitting ? '正在提交...' : '确认提交外访信息' }}</button>
          <text class="submit-hint">请确认所有入场人员信息准确，提交后将立即锁定。</text>
        </template>
      </template>
    </view>
    <NavigationDialog :visible="navigationVisible" title="访客导航" @close="closeNavigation">
      <scroll-view class="visitor-navigation-scroll" scroll-y :scroll-top="navigationScrollTarget" @scroll="rememberNavigationScroll" @touchstart="navigationRestoreTop = undefined" @touchmove.stop>
        <view class="visitor-navigation-content">
          <view v-if="navigationError" class="visitor-navigation-state">
            <text>{{ navigationError }}</text><button class="visitor-navigation-button" @tap="refreshNavigation(true)">重新加载</button>
          </view>
          <ProjectLocationCard
            v-else-if="navigationVisible && foreground && navigationVerified && canShowNavigation && invitation?.projectLocation"
            :location="invitation.projectLocation"
            :project-name="invitation.projectShortName || invitation.projectName"
            map-id="visitor-project-map"
            :route-image-path="projectRouteImagePath"
            :route-image-loading="projectRouteImageLoading"
            :route-image-error="projectRouteImageError"
            embedded
            @route-image-loaded="restoreNavigationScroll"
          />
          <view v-else class="visitor-navigation-state">正在核验来访及导航信息…</view>
        </view>
      </scroll-view>
    </NavigationDialog>
  </view>
</template>

<style scoped>
.visitor-navigation-button{width:100%;display:flex;align-items:center;justify-content:center;gap:20rpx;min-height:84rpx;line-height:1.5;margin:0;padding:16rpx 20rpx;border-radius:16rpx;background:#edf5ff;color:var(--workspace-accent-deep,#315f86);font-size:28rpx;font-weight:700}
.visitor-navigation-button::after{border:1rpx solid #d6e7f7;border-radius:16rpx}
.visitor-navigation-arrow{font-size:38rpx;line-height:1;font-weight:400}
.visitor-navigation-scroll{height:100%;width:100%}
.visitor-navigation-content{padding:26rpx}
.visitor-navigation-state{display:flex;flex-direction:column;gap:24rpx;padding:40rpx 0;color:#617086;font-size:26rpx;line-height:1.7;word-break:break-word}
</style>

<style scoped>
.visitor-shell{min-height:100vh;background:var(--workspace-page);color:var(--workspace-text)}.visitor-content{display:flex;flex-direction:column;gap:20rpx;padding:20rpx 24rpx calc(46rpx + env(safe-area-inset-bottom))}.visitor-card{border:1rpx solid var(--workspace-divider);border-radius:22rpx;background:#fff;box-shadow:var(--workspace-shadow)}.invite-card{padding:26rpx;background:linear-gradient(145deg,#eaf3fa,#fff)}.invite-top{display:flex;align-items:center;justify-content:space-between;color:var(--workspace-accent-deep);font-size:21rpx;font-weight:750}.invite-title{display:block;margin:26rpx 0 20rpx;font-size:34rpx;font-weight:900;text-align:center}.invite-info{display:grid;grid-template-columns:130rpx 1fr;gap:14rpx;margin-top:12rpx;font-size:22rpx;line-height:1.6}.invite-info text:first-child{color:var(--workspace-text-muted)}.form-card,.profile-card{padding:26rpx}.section-title{font-size:28rpx;font-weight:850}.profile-subtitle{display:block;margin-top:5rpx;color:var(--workspace-text-muted);font-size:19rpx}.section-head,.companion-title{display:flex;align-items:center;justify-content:space-between}.section-head button,.companion-title button{min-height:52rpx;padding:0 18rpx;border:1rpx solid var(--workspace-divider);border-radius:12rpx;background:#f7fafc;color:var(--workspace-accent-deep);font-size:20rpx}.profile-list{display:flex;flex-direction:column;gap:14rpx;margin-top:20rpx}.profile-item{display:flex;align-items:center;justify-content:space-between;gap:14rpx;padding:18rpx;border:1rpx solid var(--workspace-divider);border-radius:16rpx;background:#f9fbfc}.profile-item.selected{border-color:var(--workspace-accent-deep);background:#edf5fa}.profile-item.loading{border-color:#89a9bf;background:#f0f6fa}.profile-main{display:flex;min-width:0;flex:1;flex-direction:column;gap:7rpx}.profile-main text:first-child{font-size:24rpx;font-weight:800}.profile-main text:not(:first-child){color:var(--workspace-text-muted);font-size:20rpx}.profile-main .profile-loading-copy{color:var(--workspace-accent-deep);font-weight:700}.profile-item>button{min-height:48rpx;padding:0 15rpx;border:1rpx solid #e8c7c4;border-radius:11rpx;background:#fff7f6;color:#a64d45;font-size:19rpx}.profile-item>button[disabled]{opacity:.55}.profile-notice,.selected-profile-copy{display:block;margin-top:18rpx;border-radius:14rpx;padding:16rpx;background:#fff8e9;color:#80602d;font-size:20rpx;line-height:1.65}.selected-profile-copy{background:#edf6f2;color:#356b58}.visitor-field{display:flex;flex-direction:column;gap:10rpx;margin-top:22rpx}.visitor-field>text{color:var(--workspace-text-secondary);font-size:22rpx;font-weight:650}.visitor-field input,.visitor-field textarea,.companion-card input{width:100%;border:1rpx solid #d5e0e7;border-radius:14rpx;background:#f9fbfc;font-size:24rpx}.visitor-field input,.companion-card input{height:78rpx;padding:0 20rpx}.visitor-field textarea{min-height:150rpx;padding:18rpx 20rpx}.companion-field{margin-top:4rpx}.empty-copy,.limit-copy{display:block;margin-top:18rpx;color:var(--workspace-text-muted);font-size:21rpx;line-height:1.6}.companion-card{display:flex;flex-direction:column;gap:12rpx;margin-top:18rpx;padding:18rpx;border:1rpx solid var(--workspace-divider);border-radius:16rpx;background:#f8fafb}.companion-title text{font-size:22rpx;font-weight:750}.travel-options{display:flex;gap:40rpx;margin-top:24rpx}.travel-options label{display:flex;align-items:center;gap:8rpx;font-size:23rpx}.privacy-card,.profile-save-card{padding:24rpx;background:#f8fbfd}.profile-save-card{background:#f7fbf8}.privacy-check{display:flex;align-items:center;gap:8rpx;font-size:23rpx;font-weight:750}.privacy-copy{display:block;margin-top:15rpx;color:var(--workspace-text-muted);font-size:20rpx;line-height:1.75}.submit-button{min-height:84rpx;border-radius:16rpx;background:var(--workspace-accent-deep);color:#fff;font-size:26rpx;font-weight:800}.submit-button[disabled]{opacity:.65}.submit-hint{color:var(--workspace-text-muted);font-size:20rpx;text-align:center}.submit-error{border:1rpx solid #edc8c5;border-radius:14rpx;padding:18rpx;background:#fff4f3;color:#a63f3f;font-size:21rpx}.state-card{display:flex;min-height:300rpx;align-items:center;justify-content:center;flex-direction:column;padding:40rpx;color:var(--workspace-text-muted);font-size:23rpx;text-align:center}.error-state text:first-child{color:var(--workspace-text);font-size:29rpx;font-weight:850}.error-state text:nth-child(2){margin-top:14rpx;line-height:1.6}.error-state button{min-height:66rpx;margin-top:24rpx;padding:0 36rpx;border-radius:14rpx;background:var(--workspace-accent-deep);color:#fff;font-size:22rpx}.result-card{display:flex;align-items:center;flex-direction:column;padding:54rpx 30rpx;text-align:center}.result-mark{display:flex;width:92rpx;height:92rpx;align-items:center;justify-content:center;border-radius:50%;background:#eaf6f1;color:#2f8065;font-size:50rpx;font-weight:900}.result-card.expired .result-mark,.result-card.voided .result-mark{background:#fceeed;color:#b75353}.result-title{margin:24rpx 0 12rpx;font-size:30rpx;font-weight:850}.result-card text:last-child{color:var(--workspace-text-muted);font-size:21rpx;line-height:1.7}
.project-info-button{min-height:64rpx;margin-top:24rpx;border:1rpx solid #a8c4d7;border-radius:14rpx;background:#fff;color:var(--workspace-accent-deep);font-size:23rpx;font-weight:800}.result-project-button{min-width:220rpx}.project-profile-content{display:flex;flex-direction:column;gap:20rpx;padding:20rpx 24rpx calc(46rpx + env(safe-area-inset-bottom))}.public-project-hero,.public-project-card{border:1rpx solid var(--workspace-divider);border-radius:22rpx;background:#fff;box-shadow:var(--workspace-shadow)}.public-project-hero{padding:30rpx;background:linear-gradient(145deg,#eaf3fa,#fff)}.public-project-label,.public-project-title,.public-project-subtitle{display:block}.public-project-label{color:var(--workspace-accent-deep);font-size:18rpx;font-weight:850;letter-spacing:3rpx}.public-project-title{margin-top:12rpx;font-size:34rpx;font-weight:900;line-height:1.45}.public-project-subtitle{margin-top:10rpx;color:var(--workspace-text-muted);font-size:22rpx}.public-project-card{padding:24rpx}.public-project-section-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:18rpx;font-size:27rpx;font-weight:850}.public-project-section-head text:last-child:not(:first-child){color:var(--workspace-text-muted);font-size:20rpx;font-weight:500}.public-project-swiper{height:360rpx;overflow:hidden;border-radius:18rpx;background:#eef3f6}.public-project-swiper image{width:100%;height:100%}.public-project-empty-image{display:flex;height:190rpx;align-items:center;justify-content:center;border:1rpx dashed var(--workspace-divider);border-radius:18rpx;background:#f8fafb;color:var(--workspace-text-muted);font-size:22rpx}.public-project-image-error{display:block;margin-top:12rpx;color:#a64d45;font-size:20rpx}.public-project-fields{border-top:1rpx solid var(--workspace-divider)}.public-project-field{display:grid;grid-template-columns:180rpx 1fr;gap:20rpx;padding:18rpx 0;border-bottom:1rpx solid var(--workspace-divider);font-size:22rpx;line-height:1.65}.public-project-field:last-child{border-bottom:0}.public-project-field text:first-child{color:var(--workspace-text-muted)}.public-project-field text:last-child{overflow-wrap:anywhere;white-space:pre-wrap}.public-project-boundary{padding:8rpx 20rpx 20rpx;color:var(--workspace-text-muted);font-size:19rpx;line-height:1.7;text-align:center}
.pass-card{display:flex;align-items:center;flex-direction:column;padding:42rpx 30rpx;background:linear-gradient(155deg,#e9f8f0,#fff 56%);border-color:#aad7c3}.pass-check{display:flex;width:112rpx;height:112rpx;align-items:center;justify-content:center;border-radius:50%;background:#2f8065;color:#fff;font-size:62rpx;font-weight:900;box-shadow:0 12rpx 30rpx rgba(47,128,101,.22)}.pass-label{margin-top:22rpx;color:#247157;font-size:36rpx;font-weight:950}.pass-project{margin-top:10rpx;color:var(--workspace-text-secondary);font-size:23rpx;text-align:center}.pass-current{display:flex;width:100%;align-items:center;justify-content:space-between;margin-top:28rpx;border-radius:16rpx;padding:20rpx;background:#236e55;color:#fff}.pass-current text:first-child{font-size:20rpx}.pass-current text:last-child{font-size:27rpx;font-weight:900;font-variant-numeric:tabular-nums}.pass-grid{display:grid;width:100%;grid-template-columns:150rpx 1fr;gap:0;margin-top:24rpx;border-top:1rpx solid #cfe5db}.pass-grid text{padding:16rpx 0;border-bottom:1rpx solid #dcebe4;font-size:22rpx;line-height:1.5}.pass-grid text:nth-child(odd){color:var(--workspace-text-muted)}.pass-grid text:nth-child(even){overflow-wrap:anywhere;font-weight:650}.pass-hint{margin-top:24rpx;color:#44705f;font-size:20rpx;line-height:1.7;text-align:center}.pass-card.expired{border-color:#e7c5c1;background:linear-gradient(155deg,#fceeed,#fff 56%)}.pass-card.expired .pass-check,.pass-card.expired .pass-current{background:#b75353}.pass-card.expired .pass-label{color:#a44444}
</style>
<style scoped>
.result-card > text:last-of-type {
  color: var(--workspace-text-muted);
  font-size: 21rpx;
  line-height: 1.7;
}
</style>

<style scoped>
.personal-info-note{padding:20rpx;margin:12rpx 0;border-radius:12rpx;background:#eef6ff;color:#285b83;font-size:25rpx;line-height:1.6}
</style>

<style scoped>
@import "../../styles/publicVisitorForms.css";
</style>
