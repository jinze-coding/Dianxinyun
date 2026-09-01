<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue';
import { onLoad, onShow, onUnload } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import ProjectLocationCard from '@/components/ProjectLocationCard.vue';
import {
  createPublicMeetingVisitorSession,
  disablePublicMeetingVisitorProfile,
  downloadPublicProjectRouteImage,
  getPublicMeetingVisitorProfile,
  getPublicMeetingVisitorProfiles,
  removePublicProjectRouteImage,
  resolvePublicSiteVisit,
  submitPublicMeetingVisit,
  type PublicMeetingVisitPass,
  type PublicMeetingVisitSubmitPayload,
  type PublicSiteVisitInvitation,
  type SiteVisitCompanionInput,
  type SiteVisitorProfile
} from '@/api/siteAccess';
import { extractMeetingInviteToken } from '@/utils/meetingInviteScene';
import { createMeetingInviteRefreshCoordinator } from '@/utils/meetingInviteRefresh';
import { getFreshWechatCode } from '@/utils/wechat';
import { showToast } from '@/utils/navigation';

const token = ref('');
const invitation = ref<PublicSiteVisitInvitation>();
const pass = ref<PublicMeetingVisitPass>();
const visitorSessionToken = ref('');
const loading = ref(true);
const submitting = ref(false);
const errorMessage = ref('');
const terminalState = ref<'EXPIRED' | 'VOIDED' | ''>('');
const visitorCompany = ref('');
const contactName = ref('');
const contactPhone = ref('');
const companions = ref<SiteVisitCompanionInput[]>([]);
const travelMode = ref<'DRIVING' | 'OTHER'>('OTHER');
const vehiclePlate = ref('');
const visitorRemark = ref('');
const privacyAgreed = ref(false);
const profiles = ref<SiteVisitorProfile[]>([]);
const profilesLoading = ref(false);
const selectedProfile = ref<SiteVisitorProfile>();
const savingProfile = ref(false);
const profileName = ref('');
const profileNotice = ref('');
const routeImagePath = ref('');
const routeImageLoading = ref(false);
const routeImageError = ref('');
const currentTime = ref(Date.now());
let serverOffset = 0;
let clockTimer: ReturnType<typeof setInterval> | undefined;
let profileRequestId = 0;
let routeRequestId = 0;
const refreshCoordinator = createMeetingInviteRefreshCoordinator();

onLoad(async (options) => {
  // #ifndef MP-WEIXIN
  errorMessage.value = '会议访客登记仅支持微信小程序扫码';
  loading.value = false;
  return;
  // #endif
  token.value = extractMeetingInviteToken(options as Record<string, unknown>);
  refreshCoordinator.markLoadStarted();
  await initialize(false);
});

onShow(() => {
  if (refreshCoordinator.shouldRefreshOnShow()) void initialize(true);
});

const filledCompanionCount = computed(() => companions.value.filter(hasCompanionContent).length);
const expired = computed(() => {
  if (terminalState.value || !invitation.value?.visitEndTime) return Boolean(terminalState.value);
  const end = new Date(invitation.value.visitEndTime).getTime();
  return Number.isFinite(end) && currentTime.value >= end;
});

async function initialize(keepForm: boolean) {
  await refreshCoordinator.run(() => performInitialize(keepForm));
}

async function performInitialize(keepForm: boolean) {
  loading.value = true;
  errorMessage.value = '';
  terminalState.value = '';
  if (!token.value) {
    errorMessage.value = '会议邀请码无效';
    loading.value = false;
    return;
  }
  try {
    const publicInvitation = await resolvePublicSiteVisit(token.value);
    invitation.value = publicInvitation;
    syncClock(publicInvitation.serverTime);
    startClock();
    if (publicInvitation.inviteType !== 'MEETING') throw new Error('当前二维码不是会议邀请');
    if (publicInvitation.status === 'EXPIRED' || publicInvitation.status === 'VOIDED') {
      enterTerminalState(publicInvitation.status);
      return;
    }
    const session = await createPublicMeetingVisitorSession(token.value, await getFreshWechatCode());
    visitorSessionToken.value = session.visitorSessionToken;
    invitation.value = session.invitation;
    pass.value = session.registration;
    syncClock(session.registration?.serverTime || session.invitation.serverTime);
    await loadRouteImage();
    if (session.pageState === 'FORM') {
      if (!keepForm) pass.value = undefined;
      void loadProfiles();
    } else {
      clearForm();
    }
  } catch (error) {
    visitorSessionToken.value = '';
    const message = error instanceof Error ? error.message : '会议邀请加载失败';
    if (message.includes('过期')) enterTerminalState('EXPIRED');
    else if (message.includes('作废')) enterTerminalState('VOIDED');
    else errorMessage.value = message;
  } finally {
    loading.value = false;
  }
}

async function loadProfiles() {
  const requestId = ++profileRequestId;
  profilesLoading.value = true;
  profileNotice.value = '';
  try {
    const result = await getPublicMeetingVisitorProfiles(visitorSessionToken.value);
    if (requestId !== profileRequestId) return;
    profiles.value = result;
  } catch {
    if (requestId !== profileRequestId) return;
    profiles.value = [];
    profileNotice.value = '暂未读取到常用资料，可继续手工填写。';
  } finally {
    if (requestId === profileRequestId) profilesLoading.value = false;
  }
}

async function chooseProfile(profile: SiteVisitorProfile) {
  try {
    const detail = await getPublicMeetingVisitorProfile(visitorSessionToken.value, profile.profileCode);
    selectedProfile.value = detail;
    profileName.value = detail.profileName || '';
    visitorCompany.value = detail.visitorCompany || '';
    contactName.value = detail.contactName || '';
    contactPhone.value = detail.contactPhone || '';
    companions.value = (detail.people || []).filter((person) => person.personType === 'COMPANION').map((person) => ({
      personCompany: person.personCompany || '', personName: person.personName || '', personPhone: person.personPhone || ''
    }));
    travelMode.value = detail.travelMode || 'OTHER';
    vehiclePlate.value = detail.vehiclePlate || '';
    showToast('已带入常用资料，请核对本次信息');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '常用资料加载失败');
  }
}

async function disableProfile(profile: SiteVisitorProfile) {
  const confirmed = await new Promise<boolean>((resolve) => uni.showModal({
    title: '停用常用资料', content: `确认停用“${profile.profileName}”吗？`,
    success: (result) => resolve(result.confirm), fail: () => resolve(false)
  }));
  if (!confirmed) return;
  try {
    await disablePublicMeetingVisitorProfile(visitorSessionToken.value, profile.profileCode);
    profiles.value = profiles.value.filter((item) => item.profileCode !== profile.profileCode);
    if (selectedProfile.value?.profileCode === profile.profileCode) selectedProfile.value = undefined;
    showToast('常用资料已停用');
  } catch (error) {
    showToast(error instanceof Error ? error.message : '停用失败');
  }
}

function addCompanion() {
  if (companions.value.length >= 49) return showToast('一组最多登记50人');
  companions.value.push({ personCompany: '', personName: '', personPhone: '' });
}

function hasCompanionContent(item: SiteVisitCompanionInput) {
  return Boolean(item.personCompany.trim() || item.personName.trim() || item.personPhone.trim());
}

function validate() {
  if (expired.value) return '会议邀请已过期';
  if (!visitorSessionToken.value) return '微信身份会话已失效，请重新识别';
  if (!visitorCompany.value.trim()) return '请填写外访单位';
  if (!contactName.value.trim()) return '请填写主联系人姓名';
  if (!/^1[3-9]\d{9}$/.test(contactPhone.value.trim())) return '请填写正确的手机号';
  for (let index = 0; index < companions.value.length; index += 1) {
    const phone = companions.value[index].personPhone.trim();
    if (phone && !/^1[3-9]\d{9}$/.test(phone)) return `请填写第${index + 1}位同行人员的正确手机号`;
  }
  if (travelMode.value === 'DRIVING' && !vehiclePlate.value.trim()) return '驾车来访请填写车牌号';
  if (!privacyAgreed.value) return '请阅读并同意隐私告知';
  return '';
}

async function submit() {
  if (submitting.value || pass.value) return;
  const validation = validate();
  if (validation) return showToast(validation);
  submitting.value = true;
  errorMessage.value = '';
  const payload: PublicMeetingVisitSubmitPayload = {
    visitorCompany: visitorCompany.value.trim(), contactName: contactName.value.trim(),
    contactPhone: contactPhone.value.trim(),
    companions: companions.value.filter(hasCompanionContent).map((person) => ({
      personCompany: person.personCompany.trim(), personName: person.personName.trim(), personPhone: person.personPhone.trim()
    })),
    travelMode: travelMode.value,
    vehiclePlate: travelMode.value === 'DRIVING' ? vehiclePlate.value.trim().toUpperCase() : undefined,
    visitorRemark: visitorRemark.value.trim() || undefined,
    privacyAgreed: true,
    profileAction: savingProfile.value ? (selectedProfile.value ? 'UPDATE' : 'CREATE') : 'NONE',
    profileCode: selectedProfile.value?.profileCode,
    profileName: savingProfile.value ? profileName.value.trim() || undefined : undefined,
    profileRetentionAgreed: savingProfile.value || undefined,
    profileVersion: selectedProfile.value?.version
  };
  try {
    pass.value = await submitPublicMeetingVisit(payload, visitorSessionToken.value);
    syncClock(pass.value.serverTime);
    clearForm();
    showToast('登记成功，请向门卫展示放行页');
  } catch (error) {
    const submitErrorMessage = error instanceof Error ? error.message : '会议登记失败';
    await initialize(true);
    if (!pass.value && !terminalState.value) showToast(submitErrorMessage);
  } finally {
    submitting.value = false;
  }
}

async function loadRouteImage() {
  if (!invitation.value?.projectLocation?.routeImageAvailable || expired.value) return clearRouteImage();
  const requestId = ++routeRequestId;
  routeImageLoading.value = true;
  routeImageError.value = '';
  try {
    const path = await downloadPublicProjectRouteImage(token.value);
    if (requestId !== routeRequestId) return removePublicProjectRouteImage(path);
    removePublicProjectRouteImage(routeImagePath.value);
    routeImagePath.value = path;
  } catch {
    if (requestId === routeRequestId) routeImageError.value = '到访路线图加载失败，不影响地图导航';
  } finally {
    if (requestId === routeRequestId) routeImageLoading.value = false;
  }
}

function clearRouteImage() {
  routeRequestId += 1;
  removePublicProjectRouteImage(routeImagePath.value);
  routeImagePath.value = '';
  routeImageLoading.value = false;
  routeImageError.value = '';
}

function enterTerminalState(state: 'EXPIRED' | 'VOIDED') {
  terminalState.value = state;
  visitorSessionToken.value = '';
  pass.value = undefined;
  clearRouteImage();
}

function clearForm() {
  profileRequestId += 1;
  profiles.value = [];
  selectedProfile.value = undefined;
  savingProfile.value = false;
  profileName.value = '';
  visitorCompany.value = '';
  contactName.value = '';
  contactPhone.value = '';
  companions.value = [];
  travelMode.value = 'OTHER';
  vehiclePlate.value = '';
  visitorRemark.value = '';
  privacyAgreed.value = false;
}

function syncClock(serverTime?: string) {
  const parsed = serverTime ? new Date(serverTime).getTime() : NaN;
  serverOffset = Number.isFinite(parsed) ? parsed - Date.now() : 0;
  currentTime.value = Date.now() + serverOffset;
}

function startClock() {
  if (clockTimer) clearInterval(clockTimer);
  clockTimer = setInterval(() => {
    currentTime.value = Date.now() + serverOffset;
    if (expired.value) clearRouteImage();
  }, 1000);
}

function formatTime(value?: string | number, seconds = false) {
  if (!value) return '-';
  const date = new Date(value);
  if (!Number.isFinite(date.getTime())) return '-';
  const pad = (item: number) => String(item).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}${seconds ? `:${pad(date.getSeconds())}` : ''}`;
}

function privacyChange(event: { detail: { value: string[] } }) {
  privacyAgreed.value = event.detail.value.includes('agreed');
}

function profileSaveChange(event: { detail: { value: string[] } }) {
  savingProfile.value = event.detail.value.includes('save');
  if (savingProfile.value && !profileName.value) profileName.value = `${contactName.value.trim() || '我的'}常用资料`;
}

function cleanup() {
  if (clockTimer) clearInterval(clockTimer);
  clockTimer = undefined;
  clearRouteImage();
}

function goBack() {
  if (getCurrentPages().length > 1) uni.navigateBack();
  else uni.exitMiniProgram();
}
onUnload(cleanup);
onBeforeUnmount(cleanup);
</script>

<template>
  <view class="meeting-shell">
    <AppNavBar title="会议访客登记" @back="goBack" />
    <view class="meeting-content">
    <view v-if="loading" class="meeting-card state-card"><text>正在识别微信身份...</text><text>确认登记状态后再显示页面</text></view>
    <view v-else-if="errorMessage" class="meeting-card state-card error"><text>{{ errorMessage }}</text><button @tap="initialize(true)">重新识别</button></view>
    <view v-else-if="terminalState || expired" class="meeting-card state-card expired"><text class="state-mark">!</text><text class="state-title">{{ terminalState === 'VOIDED' ? '会议邀请已作废' : '会议邀请已过期' }}</text><text>截止后不再展示历史放行凭证，请联系项目接待人。</text></view>
    <template v-else-if="invitation">
      <view class="meeting-card invite-card">
        <view class="invite-top">
          <text class="invite-kind">共享会议邀请</text>
          <text class="invite-no">{{ invitation.inviteNo }}</text>
        </view>
        <text class="invite-title">{{ invitation.purpose }}</text>
        <view class="invite-grid">
          <text>会议时间</text><text>{{ formatTime(invitation.visitStartTime) }} 至 {{ formatTime(invitation.visitEndTime) }}</text>
          <text>会议地点</text><text>{{ invitation.visitLocation }}</text>
          <text>接待人</text><text>{{ invitation.hostName }} {{ invitation.hostPhone || '' }}</text>
        </view>
      </view>

      <view v-if="pass" class="meeting-card pass-card"><text class="pass-check">✓</text><text class="pass-label">已登记 · 门卫放行</text><text class="pass-project">{{ pass.projectShortName || pass.projectName }}</text><view class="pass-current"><text>当前时间</text><text>{{ formatTime(currentTime, true) }}</text></view><view class="invite-grid"><text>登记编号</text><text>{{ pass.registrationNo }}</text><text>外访单位</text><text>{{ pass.visitorCompany }}</text><text>联系人</text><text>{{ pass.contactName }}</text><text>来访人数</text><text>{{ pass.visitorCount }} 人</text><text>车辆</text><text>{{ pass.travelMode === 'DRIVING' ? (pass.vehiclePlate || '驾车') : '非驾车' }}</text><text>登记时间</text><text>{{ formatTime(pass.registeredTime) }}</text><text>有效截止</text><text>{{ formatTime(pass.validUntil) }}</text></view><view class="pass-people"><text class="pass-people-title">本次登记人员</text><view v-for="(person, index) in pass.visitors" :key="`${person.personType}-${index}`" class="pass-person"><text>{{ person.personType === 'CONTACT' ? '主联系人' : `同行${index}` }}</text><text>{{ person.personName || '未填写姓名' }}{{ person.personCompany ? ` · ${person.personCompany}` : '' }}</text></view></view><text class="pass-hint">本凭证仅属于当前微信用户登记组；请向门卫出示本页。</text></view>

      <template v-else>
        <view class="meeting-card profile-card">
          <view class="section-head"><text>常用资料</text><text>{{ profilesLoading ? '读取中' : `${profiles.length} 条` }}</text></view>
          <text v-if="profileNotice" class="notice">{{ profileNotice }}</text>
          <text v-else-if="!profilesLoading && !profiles.length" class="field-hint">暂无常用资料，可继续手工填写。</text>
          <view v-if="profiles.length" class="profile-list">
            <view v-for="profile in profiles" :key="profile.profileCode" class="profile-item" :class="{ selected: selectedProfile?.profileCode === profile.profileCode }" @tap="chooseProfile(profile)">
              <view class="profile-main">
                <text class="profile-name">{{ profile.profileName }}</text>
                <text class="profile-summary">{{ profile.visitorCompany }} · {{ profile.contactName }}</text>
              </view>
              <button class="profile-disable" @tap.stop="disableProfile(profile)">停用</button>
            </view>
          </view>
          <text v-if="selectedProfile" class="selected-profile-copy">已带入“{{ selectedProfile.profileName }}”，请核对本次登记信息。</text>
        </view>
        <view class="meeting-card form-card">
          <view class="section-head"><text>登记信息</text><text>本人及同行人</text></view>
          <label class="meeting-field"><text>外访单位 *</text><input v-model="visitorCompany" maxlength="200" placeholder="请输入单位名称" /></label>
          <label class="meeting-field"><text>主联系人 *</text><input v-model="contactName" maxlength="50" placeholder="请输入姓名" /></label>
          <label class="meeting-field"><text>手机号 *</text><input v-model="contactPhone" type="number" maxlength="11" placeholder="请输入手机号" /></label>

          <view class="companion-head"><text>同行人员（{{ filledCompanionCount }}）</text><button :disabled="companions.length >= 49" @tap="addCompanion">添加</button></view>
          <text v-if="!companions.length" class="field-hint">没有同行人员可不添加，主联系人已计入总人数。</text>
          <view v-for="(person, index) in companions" :key="index" class="companion-card">
            <view class="companion-title"><text>同行人员 {{ index + 1 }}</text><button @tap="companions.splice(index, 1)">移除</button></view>
            <label class="meeting-field"><text>单位（选填）</text><input v-model="person.personCompany" maxlength="200" placeholder="请输入同行人员单位" /></label>
            <label class="meeting-field"><text>姓名（选填）</text><input v-model="person.personName" maxlength="50" placeholder="请输入同行人员姓名" /></label>
            <label class="meeting-field"><text>手机号（选填）</text><input v-model="person.personPhone" type="number" maxlength="11" placeholder="填写时校验手机号格式" /></label>
          </view>
          <text class="field-hint">本次已填写 {{ filledCompanionCount + 1 }} 人，最多登记50人；空白同行项不会保存。</text>

          <view class="travel-section">
            <text class="field-label">出行方式</text>
            <view class="travel-tabs"><button :class="{ active: travelMode === 'OTHER' }" @tap="travelMode = 'OTHER'; vehiclePlate = ''">非驾车</button><button :class="{ active: travelMode === 'DRIVING' }" @tap="travelMode = 'DRIVING'">驾车</button></view>
          </view>
          <label v-if="travelMode === 'DRIVING'" class="meeting-field"><text>车牌号 *</text><input v-model="vehiclePlate" maxlength="20" placeholder="请输入车牌号" /></label>
          <label class="meeting-field"><text>来访备注</text><textarea v-model="visitorRemark" maxlength="500" placeholder="可填写需要接待人提前了解的事项" /></label>

          <view class="save-profile">
            <checkbox-group @change="profileSaveChange"><label class="check-row"><checkbox value="save" :checked="savingProfile" color="#315f86" /><text>保存为当前项目常用资料</text></label></checkbox-group>
            <label v-if="savingProfile" class="meeting-field"><text>常用资料名称</text><input v-model="profileName" maxlength="100" placeholder="例如：张三会议资料" /></label>
          </view>
          <checkbox-group @change="privacyChange"><label class="check-row privacy"><checkbox value="agreed" :checked="privacyAgreed" color="#315f86" /><text>我已阅读并同意访客隐私告知，仅用于本次来访登记与门卫核验。</text></label></checkbox-group>
          <button class="submit-button" :disabled="submitting" @tap="submit">{{ submitting ? '正在登记...' : '提交登记并生成放行凭证' }}</button>
        </view>
      </template>

      <ProjectLocationCard v-if="invitation.projectLocation" :location="invitation.projectLocation" :project-name="invitation.projectShortName || invitation.projectName" map-id="meeting-project-map" :route-image-path="routeImagePath" :route-image-loading="routeImageLoading" :route-image-error="routeImageError" />
    </template>
    </view>
  </view>
</template>

<style scoped>
.meeting-shell{min-height:100vh;background:#f3f6fa;color:#172033;padding-bottom:40rpx}.meeting-card{margin:24rpx;border-radius:22rpx;background:#fff;padding:28rpx;box-shadow:0 8rpx 28rpx rgba(24,48,68,.08)}.state-card{min-height:300rpx;display:flex;flex-direction:column;justify-content:center;align-items:center;gap:20rpx;text-align:center;color:#617086}.state-card.error{color:#b42318}.state-card.expired{color:#7a4f12}.state-mark,.pass-check{display:flex;width:92rpx;height:92rpx;border-radius:50%;align-items:center;justify-content:center;background:#fff1d6;font-size:52rpx;font-weight:800}.state-title,.pass-label{font-size:38rpx;font-weight:800}.invite-card{background:linear-gradient(135deg,#132e48,#1f5277);color:#fff}.invite-top,.section-head,.companion-head,.pass-current{display:flex;justify-content:space-between;align-items:center}.invite-top{font-size:22rpx;opacity:.78}.invite-title{display:block;font-size:38rpx;font-weight:800;margin:24rpx 0}.invite-grid{display:grid;grid-template-columns:150rpx 1fr;gap:18rpx 16rpx;font-size:26rpx}.invite-grid>text:nth-child(odd){color:#8290a0}.invite-card .invite-grid>text:nth-child(odd){color:rgba(255,255,255,.65)}.pass-card{text-align:center;border:2rpx solid #8ed2a7}.pass-check{margin:0 auto;background:#e6f8ec;color:#15803d}.pass-label{display:block;color:#15803d;margin:18rpx 0}.pass-project{display:block;font-size:30rpx;font-weight:700;margin-bottom:20rpx}.pass-current{background:#effbf3;padding:18rpx;border-radius:14rpx;margin-bottom:20rpx}.pass-card .invite-grid{text-align:left}.pass-people{margin-top:24rpx;padding-top:20rpx;border-top:2rpx solid #e7efe9;text-align:left}.pass-people-title{display:block;margin-bottom:12rpx;font-weight:700}.pass-person{display:grid;grid-template-columns:120rpx 1fr;gap:12rpx;padding:10rpx 0;font-size:25rpx}.pass-person>text:first-child{color:#708079}.pass-hint{display:block;margin-top:22rpx;color:#607263;font-size:24rpx}.section-head{font-size:30rpx;font-weight:700;margin-bottom:22rpx}.notice{display:block;color:#86621f;font-size:24rpx}.profile-list{white-space:nowrap}.profile-row{display:flex;gap:16rpx}.profile-item{display:inline-flex;vertical-align:top;flex-direction:column;gap:10rpx;width:390rpx;border:2rpx solid #dbe3eb;border-radius:16rpx;padding:20rpx;white-space:normal}.profile-item.selected{border-color:#1677c8;background:#eff8ff}.form-card label>text{display:block;margin:20rpx 0 10rpx;color:#506071;font-size:25rpx}.form-card input,.form-card textarea{box-sizing:border-box;width:100%;border:2rpx solid #dbe3eb;border-radius:12rpx;padding:18rpx;background:#fafcfe}.form-card textarea{height:150rpx}.two-columns{display:grid;grid-template-columns:1fr 1fr;gap:16rpx}.companion-head{margin:28rpx 0 10rpx}.companion-card{display:grid;grid-template-columns:1fr 1fr;gap:12rpx;border:2rpx solid #e4eaf0;border-radius:14rpx;padding:14rpx;margin-bottom:14rpx}.companion-card input:first-child{grid-column:1/3}.travel-tabs{display:flex;gap:16rpx;margin:24rpx 0}.travel-tabs button{flex:1;background:#f0f4f8}.travel-tabs button.active{background:#1677c8;color:#fff}.save-profile{display:block;margin:20rpx 0}.privacy{display:flex;align-items:flex-start;font-size:24rpx;color:#5d6875}.submit-button{margin-top:28rpx;background:#1677c8;color:#fff;font-weight:700}
</style>

<style scoped>
/* 会议页真机适配：覆盖微信原生 input/button 默认高度、字号与横向挤压。 */
.meeting-shell {
  padding-bottom: 0;
}

.meeting-content {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  padding: 20rpx 24rpx calc(46rpx + env(safe-area-inset-bottom));
}

.meeting-card {
  box-sizing: border-box;
  width: 100%;
  margin: 0;
  border: 1rpx solid #dfe7ee;
  padding: 26rpx;
}

.state-card button {
  min-height: 66rpx;
  padding: 0 34rpx;
  border-radius: 14rpx;
  background: #315f86;
  color: #fff;
  font-size: 22rpx;
}

.invite-top {
  display: flex;
  min-height: 72rpx;
  align-items: flex-start;
  flex-direction: column;
  justify-content: flex-start;
  gap: 6rpx;
  padding-right: 160rpx;
  font-size: inherit;
  opacity: 1;
}

.invite-kind {
  color: rgba(255, 255, 255, .72);
  font-size: 21rpx;
}

.invite-no {
  display: block;
  width: 100%;
  color: rgba(255, 255, 255, .84);
  font-size: 19rpx;
  line-height: 1.45;
  overflow-wrap: anywhere;
  word-break: break-all;
}

.invite-title {
  margin: 20rpx 0;
  font-size: 34rpx;
  font-weight: 850;
  line-height: 1.35;
}

.invite-grid {
  grid-template-columns: 128rpx minmax(0, 1fr);
  gap: 14rpx 16rpx;
  font-size: 22rpx;
  line-height: 1.6;
}

.invite-grid > text {
  min-width: 0;
  overflow-wrap: anywhere;
}

.state-title,
.pass-label {
  font-size: 34rpx;
}

.pass-project {
  font-size: 28rpx;
}

.pass-person {
  font-size: 23rpx;
}

.pass-hint {
  font-size: 22rpx;
  line-height: 1.6;
}

.section-head,
.companion-head,
.companion-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-head {
  font-size: 28rpx;
  font-weight: 800;
}

.section-head > text:last-child:not(:first-child) {
  color: #687789;
  font-size: 21rpx;
  font-weight: 500;
}

.notice,
.selected-profile-copy {
  display: block;
  margin-top: 16rpx;
  border-radius: 12rpx;
  padding: 14rpx;
  background: #fff8e9;
  color: #80602d;
  font-size: 21rpx;
  line-height: 1.6;
}

.selected-profile-copy {
  background: #edf6f2;
  color: #356b58;
}

.profile-list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
  white-space: normal;
}

.profile-item {
  display: flex;
  width: auto;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  flex-direction: row;
  gap: 14rpx;
  border: 1rpx solid #dbe3eb;
  padding: 18rpx;
  background: #f9fbfc;
}

.profile-main {
  display: flex;
  min-width: 0;
  flex: 1;
  flex-direction: column;
  gap: 6rpx;
}

.profile-name {
  font-size: 24rpx;
  font-weight: 800;
  overflow-wrap: anywhere;
}

.profile-summary {
  color: #687789;
  font-size: 20rpx;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.profile-disable {
  flex-shrink: 0;
  min-height: 48rpx;
  margin: 0;
  padding: 0 15rpx;
  border: 1rpx solid #e8c7c4;
  border-radius: 11rpx;
  background: #fff7f6;
  color: #a64d45;
  font-size: 19rpx;
  line-height: 1.2;
}

.meeting-field {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
  margin-top: 22rpx;
}

.form-card .meeting-field > text,
.field-label {
  display: block;
  margin: 0;
  color: #506071;
  font-size: 22rpx;
  font-weight: 650;
}

.meeting-field input,
.meeting-field textarea {
  box-sizing: border-box;
  width: 100%;
  border: 1rpx solid #d5e0e7;
  border-radius: 14rpx;
  background: #f9fbfc;
  font-size: 24rpx;
}

.meeting-field input {
  height: 78rpx;
  padding: 0 20rpx;
  line-height: normal;
}

.meeting-field textarea {
  height: 160rpx;
  min-height: 160rpx;
  padding: 18rpx 20rpx;
  line-height: 1.6;
}

.companion-head {
  margin: 28rpx 0 0;
}

.companion-head > text {
  font-size: 24rpx;
  font-weight: 750;
}

.companion-head button,
.companion-title button {
  min-height: 52rpx;
  margin: 0;
  padding: 0 18rpx;
  border: 1rpx solid #d5e0e7;
  border-radius: 12rpx;
  background: #f7fafc;
  color: #315f86;
  font-size: 20rpx;
  line-height: 1.2;
}

.companion-card {
  display: flex;
  grid-template-columns: none;
  flex-direction: column;
  gap: 6rpx;
  margin: 18rpx 0 0;
  border: 1rpx solid #e4eaf0;
  border-radius: 16rpx;
  padding: 18rpx;
  background: #f8fafb;
}

.companion-title text {
  font-size: 22rpx;
  font-weight: 750;
}

.companion-card .meeting-field {
  margin-top: 10rpx;
}

.field-hint {
  display: block;
  margin-top: 16rpx;
  color: #718093;
  font-size: 20rpx;
  line-height: 1.6;
}

.travel-section {
  margin-top: 26rpx;
}

.travel-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12rpx;
  margin: 12rpx 0 0;
}

.travel-tabs button {
  height: 72rpx;
  margin: 0;
  padding: 0;
  border-radius: 12rpx;
  background: #f0f4f8;
  color: #27384a;
  font-size: 24rpx;
  line-height: 1.2;
}

.save-profile {
  display: block;
  margin: 22rpx 0;
  border: 1rpx solid #dce8e1;
  border-radius: 14rpx;
  padding: 18rpx;
  background: #f7fbf8;
}

.check-row {
  display: flex;
  align-items: flex-start;
  gap: 10rpx;
  color: #27384a;
  font-size: 22rpx;
  line-height: 1.6;
}

.form-card .check-row > text {
  display: block;
  margin: 0;
  color: inherit;
  font-size: inherit;
}

.check-row checkbox {
  flex-shrink: 0;
}

.privacy {
  color: #5d6875;
  font-size: 22rpx;
}

.submit-button {
  height: 84rpx;
  margin-top: 28rpx;
  padding: 0;
  border-radius: 16rpx;
  background: #1677c8;
  color: #fff;
  font-size: 26rpx;
  font-weight: 800;
  line-height: 1.2;
}

.submit-button[disabled] {
  opacity: .65;
}

.profile-disable::after,
.companion-head button::after,
.companion-title button::after,
.travel-tabs button::after,
.submit-button::after,
.state-card button::after {
  border: 0;
}
</style>
