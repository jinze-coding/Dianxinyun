<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import { onBackPress, onLoad, onUnload, onShow, onHide } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import { useVisitorPersonalInfo } from '@/utils/visitorPersonalInfo';
import {
  createPublicGuardVisitorSession,
  refreshPublicGuardState,
  getPublicGuardMeetings,
  type PublicGuardMatchedPass,
  type PublicGuardMeetingChoice,
  type PublicGuardVisitorSession,
  disablePublicGuardVisitorProfile,
  downloadPublicGuardProjectProfileImage,
  getPublicGuardProjectProfile,
  getPublicGuardVisitorProfile,
  getPublicGuardVisitorProfiles,
  removePublicProjectProfileImages,
  submitPublicGuardVisit,
  type PublicGuardVisitPass,
  type PublicGuardVisitSubmitPayload,
  type PublicProjectProfile,
  type SiteVisitCompanionInput,
  type SiteVisitorProfile
} from '@/api/siteAccess';
import { extractGuardVisitorToken } from '@/utils/guardVisitorScene';
import { getFreshWechatCode } from '@/utils/wechat';
import { showToast } from '@/utils/navigation';

const sceneToken = ref('');
const loading = ref(true);
const submitting = ref(false);
const errorMessage = ref('');
const projectName = ref('');
const projectShortName = ref('');
const visitorSessionToken = ref('');
const pass = ref<PublicGuardVisitPass>();
const matchedPasses = ref<PublicGuardMatchedPass[]>([]);
const meetings = ref<PublicGuardMeetingChoice[]>([]);
const selectedMeetings = ref<string[]>([]);
const meetingsLoading = ref(false);
const meetingsError = ref('');
const stateError = ref('');
const checkingState = ref(false);
let stateTimer: ReturnType<typeof setInterval> | undefined;
let pageVisible = true;
let ready = false;
let initializationId = 0;
let refreshing = false;
let meetingsRequestId = 0;

const profiles = ref<SiteVisitorProfile[]>([]);
const profilesLoading = ref(false);
const profileNotice = ref('');
const selectedProfile = ref<SiteVisitorProfile>();
const profileDetailLoadingCode = ref('');
const savingProfile = ref(false);
const profileName = ref('');
const visitorCompany = ref('');
const contactName = ref('');
const contactPhone = ref('');
const companions = ref<SiteVisitCompanionInput[]>([]);
const travelMode = ref<'DRIVING' | 'OTHER'>('OTHER');
const vehiclePlate = ref('');
const { rememberInfo, personalInfoApplied, applyPersonalInfo, resetPersonalInfo, rememberChange } =
  useVisitorPersonalInfo({ visitorCompany, contactName, contactPhone, travelMode, vehiclePlate });
const visitorRemark = ref('');
const privacyAgreed = ref(false);
const currentTime = ref(Date.now());
let clockTimer: ReturnType<typeof setInterval> | undefined;
let serverOffset = 0;
let profileRequestId = 0;
const showingProjectProfile = ref(false);
const projectProfile = ref<PublicProjectProfile>();
const projectProfileLoading = ref(false);
const projectProfileError = ref('');
const projectImagePaths = ref<string[]>([]);
const projectImageError = ref('');
let projectProfileRequestId = 0;

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

onLoad(async (options) => {
  // #ifndef MP-WEIXIN
  errorMessage.value = '门卫访客登记仅支持微信小程序扫码';
  loading.value = false;
  return;
  // #endif
  sceneToken.value = extractGuardVisitorToken(options as Record<string, unknown>);
  await initialize();
  ready = true;
  stateTimer = setInterval(() => { if (pageVisible) void refreshState(); }, 15000);
});
onShow(() => { pageVisible = true; if (ready) void refreshState(true); });
onHide(() => { pageVisible = false; });

function applyState(state: PublicGuardVisitorSession) {
  const previouslyPassed = Boolean(pass.value || matchedPasses.value.length);
  projectName.value = state.projectName;
  projectShortName.value = state.projectShortName || '';
  pass.value = state.registration;
  matchedPasses.value = state.matchedPasses || [];
  syncServerClock(state.serverTime || state.registration?.serverTime);
  startClock();
  stateError.value = '';
  if (pass.value || matchedPasses.value.length) clearForm();
  else {
    applyPersonalInfo(state.personalInfo);
    if (previouslyPassed) { void loadProfiles(); void loadMeetings(); }
  }
}

function matchedPassActive(pass: PublicGuardMatchedPass) {
  return currentTime.value >= new Date(pass.visitStartTime).getTime()
    && currentTime.value < new Date(pass.validUntil).getTime();
}

async function refreshState(block = false) {
  if (refreshing || loading.value || submitting.value || !visitorSessionToken.value) return;
  refreshing = true;
  checkingState.value = block;
  const session = visitorSessionToken.value;
  try {
    const state = await refreshPublicGuardState(session);
    if (session !== visitorSessionToken.value) return;
    applyState(state);
  } catch (error) {
    if (session !== visitorSessionToken.value) return;
    const code = Number((error as { code?: number; statusCode?: number })?.code || (error as { statusCode?: number })?.statusCode);
    if (code === 401) await initialize();
    else {
      matchedPasses.value = []; pass.value = undefined;
      stateError.value = error instanceof Error ? error.message : '暂时无法核验放行状态，请刷新后向门卫展示';
    }
  } finally { refreshing = false; checkingState.value = false; }
}

async function loadMeetings() {
  const requestId = ++meetingsRequestId;
  const session = visitorSessionToken.value;
  if (!session) return;
  meetingsLoading.value = true;
  meetingsError.value = '';
  selectedMeetings.value = [];
  try {
    const values = await getPublicGuardMeetings(session);
    if (requestId === meetingsRequestId && session === visitorSessionToken.value) meetings.value = values;
  } catch (error) {
    if (requestId === meetingsRequestId) {
      meetings.value = [];
      meetingsError.value = error instanceof Error ? error.message : '会议列表加载失败，可刷新或按普通来访登记';
    }
  } finally { if (requestId === meetingsRequestId) meetingsLoading.value = false; }
}

function meetingsChange(event: { detail: { value: string[] } }) {
  selectedMeetings.value = event.detail.value.filter((token) => meetings.value.some((item) => item.choiceToken === token && !item.registered));
}


async function initialize() {
  const requestId = ++initializationId;
  loading.value = true;
  errorMessage.value = '';
  stateError.value = '';
  if (!sceneToken.value) {
    errorMessage.value = '门卫访客登记码无效';
    loading.value = false;
    return;
  }
  try {
    const session = await createPublicGuardVisitorSession(sceneToken.value, await getFreshWechatCode());
    if (requestId !== initializationId) return;
    visitorSessionToken.value = session.visitorSessionToken;
    applyState(session);
    if (session.pageState === 'FORM') {
      void loadProfiles();
      void loadMeetings();
    }

  } catch (error) {
    if (requestId !== initializationId) return;
    visitorSessionToken.value = '';
    matchedPasses.value = []; pass.value = undefined;
    errorMessage.value = error instanceof Error ? error.message : '门卫访客登记入口加载失败';
  } finally {
    if (requestId === initializationId) loading.value = false;
  }
}

async function loadProfiles() {
  const requestId = ++profileRequestId;
  profilesLoading.value = true;
  profileNotice.value = '';
  try {
    const result = await getPublicGuardVisitorProfiles(visitorSessionToken.value);
    if (requestId !== profileRequestId) return;
    profiles.value = result;
  } catch {
    if (requestId !== profileRequestId) return;
    profiles.value = [];
    profileNotice.value = '暂未读取到历史常用资料，仍可手工填写并正常登记。';
  } finally {
    if (requestId === profileRequestId) profilesLoading.value = false;
  }
}

async function chooseProfile(profile: SiteVisitorProfile) {
  if (!visitorSessionToken.value || profileDetailLoadingCode.value) return;
  profileDetailLoadingCode.value = profile.profileCode;
  try {
    const detail = await getPublicGuardVisitorProfile(visitorSessionToken.value, profile.profileCode);
    selectedProfile.value = detail;
    profileName.value = detail.profileName || '';
    savingProfile.value = false;
    visitorCompany.value = detail.visitorCompany || '';
    contactName.value = detail.contactName || '';
    contactPhone.value = detail.contactPhone || '';
    travelMode.value = detail.travelMode || 'OTHER';
    vehiclePlate.value = detail.vehiclePlate || '';
    showToast('已带入本人信息，同行人员请按本次来访填写');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '常用资料加载失败');
  } finally {
    profileDetailLoadingCode.value = '';
  }
}

async function disableProfile(profile: SiteVisitorProfile) {
  const confirmed = await new Promise<boolean>((resolve) => uni.showModal({
    title: '停用常用资料', content: `确认停用“${profile.profileName}”吗？`,
    success: (result) => resolve(result.confirm), fail: () => resolve(false)
  }));
  if (!confirmed) return;
  try {
    await disablePublicGuardVisitorProfile(visitorSessionToken.value, profile.profileCode);
    profiles.value = profiles.value.filter((item) => item.profileCode !== profile.profileCode);
    if (selectedProfile.value?.profileCode === profile.profileCode) clearProfileSelection();
    showToast('常用资料已停用');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '停用失败');
  }
}

function clearProfileSelection() {
  selectedProfile.value = undefined;
  savingProfile.value = false;
  profileName.value = '';
}

function addCompanion() {
  if (companions.value.length >= 49) return showToast('一次来访最多登记50人');
  companions.value.push({ personCompany: '', personName: '', personPhone: '' });
}

function hasCompanionContent(item: SiteVisitCompanionInput) {
  return Boolean(item.personCompany.trim() || item.personName.trim() || item.personPhone.trim());
}

const filledCompanionCount = computed(() => companions.value.filter(hasCompanionContent).length);
const passExpired = computed(() => {
  if (!pass.value?.validUntil) return false;
  const deadline = new Date(pass.value.validUntil).getTime();
  return Number.isFinite(deadline) && currentTime.value >= deadline;
});

function validate() {
  if (!visitorSessionToken.value) return '请重新识别微信身份';
  if (selectedMeetings.value.length > 50) return '一次最多选择50场会议';
  if (!visitorCompany.value.trim()) return '请填写单位';
  if (!contactName.value.trim()) return '请填写姓名';
  if (!/^1[3-9]\d{9}$/.test(contactPhone.value.trim())) return '请填写正确的手机号码';
  for (let index = 0; index < companions.value.length; index += 1) {
    const phone = companions.value[index].personPhone.trim();
    if (phone && !/^1[3-9]\d{9}$/.test(phone)) return `请填写第${index + 1}位同行人员的正确手机号码`;
  }
  if (travelMode.value === 'DRIVING' && !vehiclePlate.value.trim()) return '驾车来访请填写车牌号';
  if (!privacyAgreed.value) return '请阅读并同意隐私告知';
  if (savingProfile.value && !visitorSessionToken.value) return '微信身份会话已失效，请重试';
  return '';
}

async function submit() {
  if (submitting.value || pass.value || matchedPasses.value.length) return;
  const validation = validate();
  if (validation) return showToast(validation);
  submitting.value = true;
  errorMessage.value = '';
  const payload: PublicGuardVisitSubmitPayload = {
    visitorCompany: visitorCompany.value.trim(), contactName: contactName.value.trim(),
    contactPhone: contactPhone.value.trim(),
    companions: companions.value.filter(hasCompanionContent).map((person) => ({
      personCompany: person.personCompany.trim(), personName: person.personName.trim(), personPhone: person.personPhone.trim()
    })),
    travelMode: travelMode.value,
    vehiclePlate: travelMode.value === 'DRIVING' ? vehiclePlate.value.trim().toUpperCase() : undefined,
    visitorRemark: visitorRemark.value.trim() || undefined,
    meetingChoiceTokens: selectedMeetings.value,
    privacyAgreed: true,
    rememberInfo: rememberInfo.value,
    profileAction: savingProfile.value ? (selectedProfile.value ? 'UPDATE' : 'CREATE') : 'NONE',
    profileCode: selectedProfile.value?.profileCode,
    profileName: savingProfile.value ? profileName.value.trim() || undefined : undefined,
    profileRetentionAgreed: savingProfile.value || undefined,
    profileVersion: selectedProfile.value?.version
  };
  try {
    pass.value = await submitPublicGuardVisit(payload, visitorSessionToken.value);
    syncServerClock(pass.value.serverTime);
    startClock();
    clearForm();
    showToast('登记成功，请向门卫展示放行页');
  } catch (error) {
    const failedMessage = error instanceof Error ? error.message : '门卫访客登记失败';
    // 响应丢失或短会话过期时，重新静默识别；若服务端已成功，会直接恢复放行页。
    await initialize();
    if (!pass.value && !matchedPasses.value.length) { errorMessage.value = failedMessage; showToast(failedMessage); }
  } finally {
    submitting.value = false;
  }
}

function clearForm() {
  meetingsRequestId += 1;
  meetings.value = [];
  selectedMeetings.value = [];
  meetingsLoading.value = false;
  profileRequestId += 1;
  profiles.value = [];
  selectedProfile.value = undefined;
  savingProfile.value = false;
  profileName.value = '';
  visitorCompany.value = '';
  contactName.value = '';
  contactPhone.value = '';
  companions.value = [];
  vehiclePlate.value = '';
  visitorRemark.value = '';
  privacyAgreed.value = false;
  resetPersonalInfo();
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
  const date = typeof value === 'number' ? new Date(value) : new Date(value);
  if (!Number.isFinite(date.getTime())) return '-';
  const pad = (item: number) => String(item).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}${seconds ? `:${pad(date.getSeconds())}` : ''}`;
}

function privacyChange(event: { detail: { value: string[] } }) {
  privacyAgreed.value = event.detail.value.includes('agreed');
}

function profileSaveChange(event: { detail: { value: string[] } }) {
  savingProfile.value = event.detail.value.includes('save');
  if (savingProfile.value && !profileName.value) {
    profileName.value = selectedProfile.value?.profileName || `${contactName.value.trim() || '我的'}常用资料`;
  }
}

async function openProjectProfile() {
  if (!sceneToken.value || projectProfileLoading.value) return;
  const requestId = ++projectProfileRequestId;
  showingProjectProfile.value = true;
  projectProfileLoading.value = true;
  projectProfileError.value = '';
  projectImageError.value = '';
  removePublicProjectProfileImages(projectImagePaths.value);
  projectImagePaths.value = [];
  try {
    const result = await getPublicGuardProjectProfile(sceneToken.value);
    if (requestId !== projectProfileRequestId || !showingProjectProfile.value) return;
    projectProfile.value = result;
    const imageResults = await Promise.allSettled((result.images || []).map((item) =>
      downloadPublicGuardProjectProfileImage(sceneToken.value, item.imageIndex, item.mimeType)
    ));
    const downloadedPaths = imageResults.flatMap((item) => item.status === 'fulfilled' ? [item.value] : []);
    if (requestId !== projectProfileRequestId || !showingProjectProfile.value) {
      removePublicProjectProfileImages(downloadedPaths);
      return;
    }
    projectImagePaths.value = downloadedPaths;
    const failed = imageResults.length - downloadedPaths.length;
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

function handleBack() {
  if (showingProjectProfile.value) {
    closeProjectProfile();
    return;
  }
  if (getCurrentPages().length > 1) uni.navigateBack();
  else uni.exitMiniProgram();
}

onBackPress(() => {
  if (!showingProjectProfile.value) return false;
  closeProjectProfile();
  return true;
});

function cleanup() {
  initializationId += 1;
  visitorSessionToken.value = '';
  profileRequestId += 1;
  ready = false; pageVisible = false;
  if (stateTimer) clearInterval(stateTimer);
  stateTimer = undefined;
  matchedPasses.value = [];
  meetingsRequestId += 1;
  stopClock();
  projectProfileRequestId += 1;
  removePublicProjectProfileImages(projectImagePaths.value);
  projectImagePaths.value = [];
}

onUnload(cleanup);
onBeforeUnmount(cleanup);
</script>

<template>
  <view class="guard-shell">
    <AppNavBar :title="showingProjectProfile ? '项目信息' : '门卫访客登记'" @back="handleBack" />
    <view v-if="showingProjectProfile" class="project-profile-content">
      <view v-if="projectProfileLoading" class="guard-card state-card">正在加载项目信息...</view>
      <view v-else-if="projectProfileError" class="guard-card state-card error-state">
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
        <view class="public-project-boundary">项目公开简介可通过当前有效门卫登记码查看，不包含联系方式、合同证照及内部管理数据。</view>
      </template>
    </view>
    <view v-else class="guard-content">
      <view v-if="loading || checkingState" class="guard-card state-card">正在安全识别微信身份...</view>
      <view v-else-if="errorMessage && !visitorSessionToken" class="guard-card state-card error-state">
        <text>无法打开门卫登记入口</text><text>{{ errorMessage }}</text>
        <button @tap="initialize()">重新加载</button>
      </view>

      <view v-else-if="stateError" class="guard-card state-card error-state"><text>{{ stateError }}</text><button @tap="initialize()">重新核验</button></view>
      <template v-else-if="matchedPasses.length || pass">
        <view v-for="matched in matchedPasses" :key="matched.sourceNo" class="pass-card" :class="{ expired: !matchedPassActive(matched) }">
          <view class="pass-check">{{ matchedPassActive(matched) ? '✓' : '!' }}</view>
          <text class="pass-label">{{ matchedPassActive(matched) ? '已填写，允许放行' : '本次预约已结束' }}</text>
          <text class="pass-project">{{ projectShortName || projectName }}</text>
          <view class="pass-current"><text>当前时间</text><text>{{ formatTime(currentTime, true) }}</text></view>
          <view class="pass-grid"><text>登记来源</text><text>{{ matched.sourceType === 'MEETING' ? '会议预约' : '预约邀请' }}</text><text>主题</text><text>{{ matched.subject }}</text><text>登记编号</text><text>{{ matched.sourceNo }}</text><text>单位</text><text>{{ matched.visitorCompany }}</text><text>姓名</text><text>{{ matched.contactName }}</text><text>人数</text><text>{{ matched.visitorCount }} 人</text><text>有效时段</text><text>{{ formatTime(matched.visitStartTime) }} 至 {{ formatTime(matched.validUntil) }}</text><text>车辆</text><text>{{ matched.travelMode === 'DRIVING' ? matched.vehiclePlate : '非驾车' }}</text></view>
          <view class="matched-people"><text v-for="(person, index) in matched.people" :key="index">{{ person.personName || '未填写姓名' }}{{ person.personCompany ? ' · ' + person.personCompany : '' }}</text></view>
          <text class="pass-hint">{{ matched.sourceType === 'MEETING' ? '门卫核验后请到会场扫描签到码，逐人确认实际到场人员。' : '请将本页面出示给门卫核验。' }}</text>
          <button class="project-info-button" @tap="openProjectProfile">项目信息</button>
        </view>
        <view v-if="pass" class="pass-card" :class="{ expired: passExpired }">
          <view class="pass-check">{{ passExpired ? '!' : '✓' }}</view>
          <text class="pass-label">{{ passExpired ? '登记已过期' : '已登记 · 门卫放行' }}</text>
          <text class="pass-project">{{ pass.projectShortName || pass.projectName }}</text>
          <view class="pass-current"><text>当前时间</text><text>{{ formatTime(currentTime, true) }}</text></view>
          <view class="pass-grid">
            <text>登记编号</text><text>{{ pass.registrationNo }}</text>
            <text>单位</text><text>{{ pass.visitorCompany }}</text>
            <text>姓名</text><text>{{ pass.contactName }}</text>
            <text>来访人数</text><text>{{ pass.visitorCount }} 人</text>
            <text>车辆</text><text>{{ pass.travelMode === 'DRIVING' ? (pass.vehiclePlate || '驾车') : '非驾车' }}</text>
            <text>登记时间</text><text>{{ formatTime(pass.registeredTime) }}</text>
            <text>有效截止</text><text>{{ formatTime(pass.validUntil) }}</text>
          </view>
          <text class="pass-hint">{{ passExpired ? '本次放行已超过24小时，请重新登记后再向门卫展示。' : '请将本页面出示给门卫核验。登记自提交起 24 小时有效。' }}</text>
          <button class="project-info-button" @tap="openProjectProfile">项目信息</button>
          <button v-if="passExpired" class="pass-reload" @tap="initialize()">重新登记</button>
        </view>
      </template>

      <template v-else-if="visitorSessionToken">
        <view class="guard-card intro-card">
          <text class="intro-tag">门卫室固定登记码</text>
          <text class="intro-title">{{ projectShortName || projectName }}</text>
          <text class="intro-copy">二维码长期有效；每次填写后立即登记成功，无需管理员审批，单次放行自提交起有效 24 小时。</text>
          <button class="project-info-button" @tap="openProjectProfile">项目信息</button>
        </view>

        <view class="guard-card profile-card">
          <view class="section-head">
            <view><text class="section-title">常用来访资料</text><text class="section-subtitle">同一项目下可复用</text></view>
            <button v-if="selectedProfile" @tap="clearProfileSelection">改为手工填写</button>
          </view>
          <text v-if="profilesLoading" class="empty-copy">正在安全读取常用资料...</text>
          <text v-else-if="profileNotice" class="notice-copy">{{ profileNotice }}</text>
          <text v-else-if="!profiles.length" class="empty-copy">暂无常用资料，本次可自愿保存。</text>
          <view v-else class="profile-list">
            <view v-for="profile in profiles" :key="profile.profileCode" class="profile-item" :class="{ selected: selectedProfile?.profileCode === profile.profileCode }" @tap="chooseProfile(profile)">
              <view><text>{{ profile.profileName }}</text><text>{{ profile.visitorCompany }} · {{ profile.contactName }} {{ profile.maskedContactPhone }}</text><text>{{ profile.visitorCount }}人 · {{ profile.travelMode === 'DRIVING' ? (profile.vehiclePlate || '驾车') : '非驾车' }}</text></view>
              <button :disabled="profileDetailLoadingCode === profile.profileCode" @tap.stop="disableProfile(profile)">停用</button>
            </view>
          </view>
        </view>

        <view class="guard-card form-card">
          <text class="section-title">来访人员信息</text>
          <label class="field"><text>单位 *</text><input v-model="visitorCompany" maxlength="200" placeholder="请输入单位全称" /></label>
          <label class="field"><text>姓名 *</text><input v-model="contactName" maxlength="50" placeholder="请输入姓名" /></label>
          <label class="field"><text>手机号码 *</text><input v-model="contactPhone" type="number" maxlength="11" placeholder="仅校验格式，不发送验证码" /></label>
        </view>

        <view class="guard-card form-card">
          <view class="section-head"><text class="section-title">同行人员（选填）</text><button :disabled="companions.length >= 49" @tap="addCompanion">添加</button></view>
          <text v-if="!companions.length" class="empty-copy">没有同行人员可不添加，本人已计入总人数。</text>
          <view v-for="(person, index) in companions" :key="index" class="companion-card">
            <view class="section-head"><text>同行人员 {{ index + 1 }}</text><button @tap="companions.splice(index, 1)">移除</button></view>
            <label class="field"><text>单位（选填）</text><input v-model="person.personCompany" maxlength="200" placeholder="请输入同行人员单位" /></label>
            <label class="field"><text>姓名（选填）</text><input v-model="person.personName" maxlength="50" placeholder="请输入同行人员姓名" /></label>
            <label class="field"><text>手机号码（选填）</text><input v-model="person.personPhone" type="number" maxlength="11" placeholder="填写时校验手机号码格式" /></label>
          </view>
          <text class="empty-copy">本次已填写 {{ filledCompanionCount + 1 }} 人；空白同行卡片不会保存，最多50人。</text>
        </view>

        <view class="guard-card form-card">
          <text class="section-title">车辆与备注</text>
          <radio-group class="travel-options" @change="travelMode = $event.detail.value"><label><radio value="OTHER" :checked="travelMode === 'OTHER'" color="#315f86" />非驾车</label><label><radio value="DRIVING" :checked="travelMode === 'DRIVING'" color="#315f86" />驾车</label></radio-group>
          <label v-if="travelMode === 'DRIVING'" class="field"><text>车牌号 *</text><input v-model="vehiclePlate" maxlength="20" placeholder="请输入本次来访车辆车牌" /></label>
          <label class="field"><text>外访备注</text><textarea v-model="visitorRemark" maxlength="500" placeholder="可填写门卫或项目人员需要了解的事项" /></label>
        </view>

        <view class="guard-card meeting-choice-card">
          <view class="section-head"><text class="section-title">近期会议（可多选）</text><button :disabled="meetingsLoading" @tap="loadMeetings">刷新</button></view>
          <text class="empty-copy">今天及后6天的会议；不选择即按普通来访登记。所选会议共用本次人员信息，到各会场分别扫码签到。</text>
          <text v-if="meetingsLoading" class="empty-copy">正在加载会议...</text>
          <text v-else-if="meetingsError" class="notice-copy">{{ meetingsError }}</text>
          <text v-else-if="!meetings.length" class="empty-copy">近期暂无可预约会议</text>
          <checkbox-group @change="meetingsChange"><label v-for="meeting in meetings" :key="meeting.choiceToken" class="meeting-choice"><checkbox :value="meeting.choiceToken" :checked="meeting.registered || selectedMeetings.includes(meeting.choiceToken)" :disabled="meeting.registered" color="#315f86" /><view><text>{{ meeting.title }}{{ meeting.registered ? '（已预约）' : '' }}</text><text>{{ formatTime(meeting.visitStartTime) }} 至 {{ formatTime(meeting.visitEndTime) }}</text><text>{{ meeting.location }}</text></view></label></checkbox-group>
          <text v-if="selectedMeetings.length" class="empty-copy">本次同时预约 {{ selectedMeetings.length }} 场会议</text>
        </view>

        <view class="guard-card privacy-card">
          <view class="personal-info-note" v-if="personalInfoApplied">已自动带入本人和车辆资料，请核对本次信息；同行人员另行填写。</view>
          <checkbox-group class="remember-info-control" @change="rememberChange"><label><checkbox value="remember" :checked="rememberInfo" color="#315f86" /><text>记住本人和车辆信息，下次同项目扫码自动填写（提交后生效）</text></label></checkbox-group>
          <checkbox-group @change="privacyChange"><label class="privacy-check"><checkbox value="agreed" :checked="privacyAgreed" color="#315f86" /><text>我已阅读并同意隐私告知</text></label></checkbox-group>
          <text class="privacy-copy">系统将收集单位、姓名、手机号码和车辆信息，用于本项目门卫人工核验与外访登记。系统不采集身份证信息，也不会建立系统账号。</text>
        </view>

        <view class="guard-card profile-save-card">
          <checkbox-group @change="profileSaveChange"><label class="privacy-check"><checkbox value="save" :checked="savingProfile" color="#315f86" /><text>{{ selectedProfile ? '用本次修改更新这份常用资料' : '将本次人员和车辆信息保存为常用资料' }}</text></label></checkbox-group>
          <label v-if="savingProfile" class="field"><text>常用资料名称</text><input v-model="profileName" maxlength="100" placeholder="例如：张三来访资料" /></label>
          <text class="privacy-copy">此项为单独、自愿的长期保存同意，仅限当前项目使用，以后可停用。</text>
        </view>

        <view v-if="errorMessage" class="submit-error">{{ errorMessage }}</view>
        <button class="submit-button" :disabled="submitting" @tap="submit">{{ submitting ? '正在登记...' : (selectedMeetings.length ? '登记来访并预约所选会议' : '确认登记并生成放行页') }}</button>
        <text class="submit-hint">提交后立即登记成功，不进入审批流程；有效期内不能自行覆盖。</text>
      </template>
    </view>
  </view>
</template>

<style scoped>
.guard-shell{min-height:100vh;background:var(--workspace-page);color:var(--workspace-text)}.guard-content{display:flex;flex-direction:column;gap:20rpx;padding:20rpx 24rpx calc(48rpx + env(safe-area-inset-bottom))}.guard-card,.pass-card{border:1rpx solid var(--workspace-divider);border-radius:22rpx;background:#fff;box-shadow:var(--workspace-shadow)}.state-card{display:flex;min-height:320rpx;align-items:center;justify-content:center;flex-direction:column;padding:40rpx;color:var(--workspace-text-muted);font-size:23rpx;text-align:center}.error-state text:first-child{color:var(--workspace-text);font-size:29rpx;font-weight:850}.error-state text:nth-child(2){margin-top:14rpx;line-height:1.6}.error-state button{min-height:66rpx;margin-top:24rpx;padding:0 36rpx;border-radius:14rpx;background:var(--workspace-accent-deep);color:#fff;font-size:22rpx}.intro-card{padding:28rpx;background:linear-gradient(145deg,#eaf3fa,#fff)}.intro-tag,.intro-title,.intro-copy{display:block}.intro-tag{color:var(--workspace-accent-deep);font-size:19rpx;font-weight:800}.intro-title{margin-top:12rpx;font-size:32rpx;font-weight:900}.intro-copy{margin-top:14rpx;color:var(--workspace-text-secondary);font-size:21rpx;line-height:1.7}.profile-card,.form-card,.privacy-card,.profile-save-card{padding:26rpx}.section-head{display:flex;align-items:center;justify-content:space-between}.section-head button{min-height:52rpx;padding:0 18rpx;border:1rpx solid var(--workspace-divider);border-radius:12rpx;background:#f7fafc;color:var(--workspace-accent-deep);font-size:20rpx}.section-title{font-size:28rpx;font-weight:850}.section-subtitle{display:block;margin-top:5rpx;color:var(--workspace-text-muted);font-size:19rpx}.empty-copy,.notice-copy{display:block;margin-top:18rpx;color:var(--workspace-text-muted);font-size:21rpx;line-height:1.6}.notice-copy{border-radius:14rpx;padding:14rpx;background:#fff8e9;color:#80602d}.profile-list{display:flex;flex-direction:column;gap:14rpx;margin-top:18rpx}.profile-item{display:flex;align-items:center;justify-content:space-between;gap:14rpx;padding:18rpx;border:1rpx solid var(--workspace-divider);border-radius:16rpx;background:#f9fbfc}.profile-item.selected{border-color:var(--workspace-accent-deep);background:#edf5fa}.profile-item>view{display:flex;min-width:0;flex:1;flex-direction:column;gap:6rpx}.profile-item>view text:first-child{font-size:23rpx;font-weight:800}.profile-item>view text:not(:first-child){color:var(--workspace-text-muted);font-size:19rpx}.profile-item>button{min-height:48rpx;padding:0 15rpx;border:1rpx solid #e8c7c4;border-radius:11rpx;background:#fff7f6;color:#a64d45;font-size:19rpx}.field{display:flex;flex-direction:column;gap:10rpx;margin-top:22rpx}.field>text{color:var(--workspace-text-secondary);font-size:22rpx;font-weight:650}.field input,.field textarea{width:100%;border:1rpx solid #d5e0e7;border-radius:14rpx;background:#f9fbfc;font-size:24rpx}.field input{height:78rpx;padding:0 20rpx}.field textarea{min-height:150rpx;padding:18rpx 20rpx}.companion-card{margin-top:18rpx;padding:18rpx;border:1rpx solid var(--workspace-divider);border-radius:16rpx;background:#f8fafb}.companion-card>.section-head>text{font-size:22rpx;font-weight:750}.travel-options{display:flex;gap:40rpx;margin-top:24rpx}.travel-options label{display:flex;align-items:center;gap:8rpx;font-size:23rpx}.privacy-card{background:#f8fbfd}.profile-save-card{background:#f7fbf8}.privacy-check{display:flex;align-items:center;gap:8rpx;font-size:23rpx;font-weight:750}.privacy-copy{display:block;margin-top:15rpx;color:var(--workspace-text-muted);font-size:20rpx;line-height:1.75}.submit-error{border:1rpx solid #edc8c5;border-radius:14rpx;padding:18rpx;background:#fff4f3;color:#a63f3f;font-size:21rpx}.submit-button{min-height:84rpx;border-radius:16rpx;background:var(--workspace-accent-deep);color:#fff;font-size:26rpx;font-weight:800}.submit-button[disabled]{opacity:.65}.submit-hint{color:var(--workspace-text-muted);font-size:20rpx;text-align:center}.pass-card{display:flex;align-items:center;flex-direction:column;padding:42rpx 30rpx;background:linear-gradient(155deg,#e9f8f0,#fff 56%);border-color:#aad7c3}.pass-check{display:flex;width:112rpx;height:112rpx;align-items:center;justify-content:center;border-radius:50%;background:#2f8065;color:#fff;font-size:62rpx;font-weight:900;box-shadow:0 12rpx 30rpx rgba(47,128,101,.22)}.pass-label{margin-top:22rpx;color:#247157;font-size:36rpx;font-weight:950}.pass-project{margin-top:10rpx;color:var(--workspace-text-secondary);font-size:23rpx;text-align:center}.pass-current{display:flex;width:100%;align-items:center;justify-content:space-between;margin-top:28rpx;border-radius:16rpx;padding:20rpx;background:#236e55;color:#fff}.pass-current text:first-child{font-size:20rpx}.pass-current text:last-child{font-size:27rpx;font-weight:900;font-variant-numeric:tabular-nums}.pass-grid{display:grid;width:100%;grid-template-columns:150rpx 1fr;gap:0;margin-top:24rpx;border-top:1rpx solid #cfe5db}.pass-grid text{padding:16rpx 0;border-bottom:1rpx solid #dcebe4;font-size:22rpx;line-height:1.5}.pass-grid text:nth-child(odd){color:var(--workspace-text-muted)}.pass-grid text:nth-child(even){overflow-wrap:anywhere;font-weight:650}.pass-hint{margin-top:24rpx;color:#44705f;font-size:20rpx;line-height:1.7;text-align:center}.pass-card.expired{border-color:#e7c5c1;background:linear-gradient(155deg,#fceeed,#fff 56%)}.pass-card.expired .pass-check,.pass-card.expired .pass-current{background:#b75353}.pass-card.expired .pass-label{color:#a44444}.pass-reload{min-height:70rpx;margin-top:22rpx;padding:0 34rpx;border-radius:14rpx;background:var(--workspace-accent-deep);color:#fff;font-size:23rpx;font-weight:800}
.project-info-button{min-height:64rpx;margin-top:24rpx;border:1rpx solid #a8c4d7;border-radius:14rpx;background:#fff;color:var(--workspace-accent-deep);font-size:23rpx;font-weight:800}.project-profile-content{display:flex;flex-direction:column;gap:20rpx;padding:20rpx 24rpx calc(46rpx + env(safe-area-inset-bottom))}.public-project-hero,.public-project-card{border:1rpx solid var(--workspace-divider);border-radius:22rpx;background:#fff;box-shadow:var(--workspace-shadow)}.public-project-hero{padding:30rpx;background:linear-gradient(145deg,#eaf3fa,#fff)}.public-project-label,.public-project-title,.public-project-subtitle{display:block}.public-project-label{color:var(--workspace-accent-deep);font-size:18rpx;font-weight:850;letter-spacing:3rpx}.public-project-title{margin-top:12rpx;font-size:34rpx;font-weight:900;line-height:1.45}.public-project-subtitle{margin-top:10rpx;color:var(--workspace-text-muted);font-size:22rpx}.public-project-card{padding:24rpx}.public-project-section-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:18rpx;font-size:27rpx;font-weight:850}.public-project-section-head text:last-child:not(:first-child){color:var(--workspace-text-muted);font-size:20rpx;font-weight:500}.public-project-swiper{height:360rpx;overflow:hidden;border-radius:18rpx;background:#eef3f6}.public-project-swiper image{width:100%;height:100%}.public-project-empty-image{display:flex;height:190rpx;align-items:center;justify-content:center;border:1rpx dashed var(--workspace-divider);border-radius:18rpx;background:#f8fafb;color:var(--workspace-text-muted);font-size:22rpx}.public-project-image-error{display:block;margin-top:12rpx;color:#a64d45;font-size:20rpx}.public-project-fields{border-top:1rpx solid var(--workspace-divider)}.public-project-field{display:grid;grid-template-columns:180rpx 1fr;gap:20rpx;padding:18rpx 0;border-bottom:1rpx solid var(--workspace-divider);font-size:22rpx;line-height:1.65}.public-project-field:last-child{border-bottom:0}.public-project-field text:first-child{color:var(--workspace-text-muted)}.public-project-field text:last-child{overflow-wrap:anywhere;white-space:pre-wrap}.public-project-boundary{padding:8rpx 20rpx 20rpx;color:var(--workspace-text-muted);font-size:19rpx;line-height:1.7;text-align:center}
</style>

<style scoped>
.personal-info-note{padding:20rpx;margin:12rpx 0;border-radius:12rpx;background:#eef6ff;color:#285b83;font-size:25rpx;line-height:1.6}
.remember-info-control{display:block;margin:24rpx 0;font-size:25rpx;color:#385366;line-height:1.7}.remember-info-control label{display:flex;align-items:flex-start;gap:10rpx}.remember-info-control text{flex:1;min-width:0}
</style>

<style scoped>
.meeting-choice{display:flex;align-items:flex-start;gap:14rpx;padding:22rpx 0;border-bottom:1rpx solid #e7edf3}.meeting-choice>view{display:flex;flex:1;min-width:0;flex-direction:column;gap:8rpx}.meeting-choice text:first-child{font-size:29rpx;font-weight:650;color:#203c52}.meeting-choice text{font-size:24rpx;color:#667989;line-height:1.5}.matched-people{display:flex;flex-direction:column;gap:12rpx;text-align:left;margin-top:24rpx;font-size:26rpx;color:#456253}
</style>
