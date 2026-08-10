<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import {
  createPublicVisitorSession,
  disablePublicVisitorProfile,
  getPublicVisitorProfile,
  getPublicVisitorProfiles,
  resolvePublicSiteVisit,
  submitPublicSiteVisit,
  type PublicSiteVisitInvitation,
  type PublicSiteVisitSubmitPayload,
  type SiteVisitCompanionInput,
  type SiteVisitorProfile
} from '@/api/siteAccess';
import { extractVisitorInviteToken } from '@/utils/visitorInviteScene';
import {
  LatestProfileSelectionGuard,
  isVisitorSessionAuthorizationError,
  loadOptionalVisitorProfiles,
  submitWithVisitorSessionFallback
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
const contactIdCard = ref('');
const companions = ref<SiteVisitCompanionInput[]>([]);
const travelMode = ref<'DRIVING' | 'OTHER'>('OTHER');
const vehiclePlate = ref('');
const visitorRemark = ref('');
const privacyAgreed = ref(false);
const visitorSessionToken = ref('');
const profiles = ref<SiteVisitorProfile[]>([]);
const profilesLoading = ref(false);
const profileNotice = ref('');
const selectedProfile = ref<SiteVisitorProfile>();
const savingProfile = ref(false);
const profileName = ref('');
const profileDetailLoadingCode = ref('');
const profileSelectionGuard = new LatestProfileSelectionGuard();
let profileBootstrapRequestId = 0;

const statusText = computed(() => {
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
  loading.value = true;
  errorMessage.value = '';
  invalidateProfileBootstrap();
  if (!token.value) {
    errorMessage.value = '邀请小程序码无效';
    loading.value = false;
    return;
  }
  try {
    invitation.value = await resolvePublicSiteVisit(token.value);
    loading.value = false;
    if (invitation.value.status === 'PENDING') void loadProfilesSilently();
  } catch (error) {
    invitation.value = undefined;
    errorMessage.value = error instanceof Error ? error.message : '邀请加载失败';
  } finally {
    loading.value = false;
  }
}

async function loadProfilesSilently() {
  const requestId = ++profileBootstrapRequestId;
  profilesLoading.value = true;
  profileNotice.value = '';
  const result = await loadOptionalVisitorProfiles(
    async () => {
      const wechatCode = await getFreshWechatCode();
      return createPublicVisitorSession(token.value, wechatCode);
    },
    getPublicVisitorProfiles
  );
  if (requestId !== profileBootstrapRequestId) return;
  profilesLoading.value = false;
  if (!result.available) {
    resetVisitorProfileContext('暂未读取到历史常用资料，仍可手工填写并正常提交。', false);
    return;
  }
  visitorSessionToken.value = result.session.visitorSessionToken;
  profiles.value = result.profiles;
}

async function chooseProfile(profile: SiteVisitorProfile) {
  if (!visitorSessionToken.value) return;
  const ticket = profileSelectionGuard.begin(profile.profileCode);
  if (!ticket) return;
  profileDetailLoadingCode.value = profileSelectionGuard.activeKey();
  try {
    const detail = await getPublicVisitorProfile(visitorSessionToken.value, profile.profileCode);
    if (!profileSelectionGuard.isCurrent(ticket)) return;
    selectedProfile.value = detail;
    profileName.value = detail.profileName || '';
    savingProfile.value = false;
    visitorCompany.value = detail.visitorCompany || '';
    contactName.value = detail.contactName || '';
    contactPhone.value = detail.contactPhone || '';
    const people = detail.people || [];
    contactIdCard.value = people.find((person) => person.personType === 'CONTACT')?.idCard || '';
    companions.value = people.filter((person) => person.personType === 'COMPANION').map((person) => ({
      personName: person.personName || '',
      idCard: person.idCard || ''
    }));
    travelMode.value = detail.travelMode || 'OTHER';
    vehiclePlate.value = detail.vehiclePlate || '';
    showToast('已带入常用资料，请核对本次信息');
  } catch (error) {
    if (!profileSelectionGuard.isCurrent(ticket)) return;
    if (isVisitorSessionAuthorizationError(error)) {
      resetVisitorProfileContext('微信身份会话已失效，当前已填写内容仍保留，请按手工信息提交。');
      showToast('常用资料会话已失效，已切换为手工填写');
      return;
    }
    showToast(error instanceof Error ? error.message : '常用资料加载失败');
  } finally {
    if (profileSelectionGuard.finish(ticket)) profileDetailLoadingCode.value = '';
  }
}

function clearProfileSelection() {
  invalidateProfileSelection();
  selectedProfile.value = undefined;
  savingProfile.value = false;
  profileName.value = '';
}

function invalidateProfileSelection() {
  profileSelectionGuard.invalidate();
  profileDetailLoadingCode.value = '';
}

function invalidateProfileBootstrap() {
  profileBootstrapRequestId += 1;
  profilesLoading.value = false;
}

function resetVisitorProfileContext(notice = '', invalidateBootstrap = true) {
  if (invalidateBootstrap) invalidateProfileBootstrap();
  invalidateProfileSelection();
  visitorSessionToken.value = '';
  profiles.value = [];
  selectedProfile.value = undefined;
  savingProfile.value = false;
  profileName.value = '';
  profileNotice.value = notice;
  profilesLoading.value = false;
}

async function disableProfile(profile: SiteVisitorProfile) {
  if (!visitorSessionToken.value) return;
  const confirmed = await new Promise<boolean>((resolve) => {
    uni.showModal({
      title: '停用常用资料',
      content: `确认停用“${profile.profileName}”吗？历史来访记录不会受影响。`,
      success: (result) => resolve(result.confirm),
      fail: () => resolve(false)
    });
  });
  if (!confirmed) return;
  try {
    await disablePublicVisitorProfile(visitorSessionToken.value, profile.profileCode);
    if (profileDetailLoadingCode.value === profile.profileCode) invalidateProfileSelection();
    profiles.value = profiles.value.filter((item) => item.profileCode !== profile.profileCode);
    if (selectedProfile.value?.profileCode === profile.profileCode) clearProfileSelection();
    showToast('常用资料已停用');
  } catch (error) {
    if (isVisitorSessionAuthorizationError(error)) {
      resetVisitorProfileContext('微信身份会话已失效，当前已填写内容仍保留，请按手工信息提交。');
      showToast('常用资料会话已失效，已切换为手工填写');
      return;
    }
    showToast(error instanceof Error ? error.message : '停用失败');
  }
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}

function addCompanion() {
  if (companions.value.length >= 49) {
    showToast('一次来访最多登记50人');
    return;
  }
  companions.value.push({ personName: '', idCard: '' });
}

function removeCompanion(index: number) {
  companions.value.splice(index, 1);
}

function normalizeIdCard(value: string) {
  return value.trim().toUpperCase();
}

function validIdCard(value: string) {
  const normalized = normalizeIdCard(value);
  if (!/^\d{17}[0-9X]$/.test(normalized)) return false;
  const date = normalized.slice(6, 14);
  const year = Number(date.slice(0, 4));
  const month = Number(date.slice(4, 6));
  const day = Number(date.slice(6, 8));
  const birthday = new Date(year, month - 1, day);
  if (birthday.getFullYear() !== year || birthday.getMonth() !== month - 1 || birthday.getDate() !== day) return false;
  const weights = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2];
  const checks = ['1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'];
  const sum = weights.reduce((total, weight, index) => total + Number(normalized[index]) * weight, 0);
  return checks[sum % 11] === normalized[17];
}

function validate() {
  if (!visitorCompany.value.trim()) return '请填写外访单位';
  if (!contactName.value.trim()) return '请填写主联系人姓名';
  if (!/^1[3-9]\d{9}$/.test(contactPhone.value.trim())) return '请填写正确的手机号';
  if (!validIdCard(contactIdCard.value)) return '主联系人身份证号不正确';
  for (let index = 0; index < companions.value.length; index += 1) {
    const item = companions.value[index];
    if (!item.personName.trim()) return `请填写第${index + 1}位同行人员姓名`;
    if (!validIdCard(item.idCard)) return `第${index + 1}位同行人员身份证号不正确`;
  }
  const ids = [contactIdCard.value, ...companions.value.map((item) => item.idCard)].map(normalizeIdCard);
  if (new Set(ids).size !== ids.length) return '同一次来访不能重复登记同一身份证号';
  if (travelMode.value === 'DRIVING' && !vehiclePlate.value.trim()) return '驾车来访请填写车牌号';
  if (!privacyAgreed.value) return '请阅读并同意隐私告知';
  if (savingProfile.value && !visitorSessionToken.value) return '当前无法保存常用资料，请取消保存后提交';
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
    const profileAction = savingProfile.value
      ? (selectedProfile.value ? 'UPDATE' : 'CREATE')
      : 'NONE';
    const payload: PublicSiteVisitSubmitPayload = {
      inviteToken: token.value,
      visitorCompany: visitorCompany.value.trim(),
      contactName: contactName.value.trim(),
      contactPhone: contactPhone.value.trim(),
      contactIdCard: normalizeIdCard(contactIdCard.value),
      companions: companions.value.map((item) => ({
        personName: item.personName.trim(),
        idCard: normalizeIdCard(item.idCard)
      })),
      travelMode: travelMode.value,
      vehiclePlate: travelMode.value === 'DRIVING' ? vehiclePlate.value.trim().toUpperCase() : undefined,
      visitorRemark: visitorRemark.value.trim() || undefined,
      privacyAgreed: true,
      profileAction,
      profileCode: selectedProfile.value?.profileCode,
      profileName: savingProfile.value ? profileName.value.trim() || undefined : undefined,
      profileRetentionAgreed: savingProfile.value || undefined,
      profileVersion: selectedProfile.value?.version
    };
    const result = await submitWithVisitorSessionFallback({
      payload,
      visitorSessionToken: visitorSessionToken.value || undefined,
      submit: submitPublicSiteVisit,
      confirmManualFallback,
      onSessionExpired: () => resetVisitorProfileContext(
        '微信身份会话已失效，当前已填写内容仍保留；本次可按手工信息继续提交。'
      )
    });
    if (result.status === 'cancelled') {
      errorMessage.value = '常用资料会话已失效，已保留当前填写内容，请核对后重新提交。';
      showToast('已保留当前填写内容');
      return;
    }
    invitation.value = result.data;
    clearSensitiveForm();
    showToast(result.status === 'submitted-manually'
      ? '会话已失效，已按本次手工信息提交'
      : '外访信息提交成功');
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '外访信息提交失败';
    showToast(errorMessage.value);
  } finally {
    submitting.value = false;
  }
}

function confirmManualFallback() {
  return new Promise<boolean>((resolve) => {
    uni.showModal({
      title: '常用资料会话已失效',
      content: '你已填写的信息仍会保留。本次将不再使用、保存或更新常用资料，是否按当前手工信息继续提交？',
      confirmText: '继续提交',
      cancelText: '返回核对',
      success: (result) => resolve(result.confirm),
      fail: () => resolve(false)
    });
  });
}

function clearSensitiveForm() {
  invalidateProfileBootstrap();
  invalidateProfileSelection();
  visitorCompany.value = '';
  contactName.value = '';
  contactPhone.value = '';
  contactIdCard.value = '';
  companions.value = [];
  vehiclePlate.value = '';
  visitorRemark.value = '';
  privacyAgreed.value = false;
  profiles.value = [];
  selectedProfile.value = undefined;
  visitorSessionToken.value = '';
  savingProfile.value = false;
  profileName.value = '';
  profileNotice.value = '';
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

function goBack() {
  if (getCurrentPages().length > 1) uni.navigateBack();
  else uni.exitMiniProgram();
}
</script>

<template>
  <view class="visitor-shell">
    <AppNavBar title="外访登记" @back="goBack" />
    <view class="visitor-content">
      <view v-if="loading" class="visitor-card state-card">正在加载外访邀请...</view>
      <view v-else-if="errorMessage && !invitation" class="visitor-card state-card error-state">
        <text>无法打开外访邀请</text>
        <text>{{ errorMessage }}</text>
        <button @tap="load">重新加载</button>
      </view>
      <template v-else-if="invitation">
        <view v-if="invitation.status === 'PENDING'" class="visitor-card invite-card">
          <view class="invite-top"><text>{{ invitation.projectShortName || invitation.projectName }}</text><text>{{ invitation.inviteNo }}</text></view>
          <text class="invite-title">外访人员提前登记</text>
          <view class="invite-info"><text>来访时间</text><text>{{ formatTime(invitation.visitStartTime) }} 至 {{ formatTime(invitation.visitEndTime) }}</text></view>
          <view class="invite-info"><text>来访事由</text><text>{{ invitation.purpose }}</text></view>
          <view class="invite-info"><text>到访地点</text><text>{{ invitation.visitLocation }}</text></view>
          <view class="invite-info"><text>接待人</text><text>{{ invitation.hostName }} {{ invitation.hostPhone || '' }}</text></view>
        </view>

        <view v-if="invitation.status !== 'PENDING'" class="visitor-card result-card" :class="invitation.status.toLowerCase()">
          <text class="result-mark">{{ invitation.status === 'SUBMITTED' ? '✓' : '!' }}</text>
          <text class="result-title">{{ statusText }}</text>
          <text>{{ invitation.status === 'SUBMITTED' ? '提交后信息已锁定，如需修正请联系项目接待人。' : '请联系项目接待人重新创建本次来访邀请。' }}</text>
        </view>

        <template v-else>
          <view class="visitor-card profile-card">
            <view class="section-head">
              <view><text class="section-title">常用来访资料</text><text class="profile-subtitle">同一项目下可复用</text></view>
              <button v-if="selectedProfile" @tap="clearProfileSelection">改为手工填写</button>
            </view>
            <text v-if="profilesLoading" class="empty-copy">正在安全读取历史常用资料...</text>
            <text v-else-if="profileNotice" class="profile-notice">{{ profileNotice }}</text>
            <text v-else-if="!profiles.length" class="empty-copy">暂无常用资料。本次填写时可自愿保存，下一次扫码直接选择。</text>
            <view v-else class="profile-list">
              <view
                v-for="profile in profiles"
                :key="profile.profileCode"
                class="profile-item"
                :class="{
                  selected: selectedProfile?.profileCode === profile.profileCode,
                  loading: profileDetailLoadingCode === profile.profileCode
                }"
                @tap="chooseProfile(profile)"
              >
                <view class="profile-main">
                  <text>{{ profile.profileName }}</text>
                  <text v-if="profileDetailLoadingCode === profile.profileCode" class="profile-loading-copy">正在读取完整资料...</text>
                  <text>{{ profile.visitorCompany }} · {{ profile.contactName }} {{ profile.maskedContactPhone }}</text>
                  <text>{{ profile.visitorCount }}人 · {{ profile.travelMode === 'DRIVING' ? (profile.vehiclePlate || '驾车') : '非驾车' }}</text>
                </view>
                <button :disabled="profileDetailLoadingCode === profile.profileCode" @tap.stop="disableProfile(profile)">停用</button>
              </view>
            </view>
            <view v-if="selectedProfile" class="selected-profile-copy">
              已带入“{{ selectedProfile.profileName }}”。你可以修改本次内容；只有下方主动勾选更新时，才会覆盖这份常用资料。
            </view>
          </view>

          <view class="visitor-card form-card">
            <text class="section-title">主联系人</text>
            <label class="visitor-field"><text>外访单位 *</text><input v-model="visitorCompany" maxlength="200" placeholder="请输入单位全称" /></label>
            <label class="visitor-field"><text>姓名 *</text><input v-model="contactName" maxlength="50" placeholder="请输入主联系人姓名" /></label>
            <label class="visitor-field"><text>手机号 *</text><input v-model="contactPhone" type="number" maxlength="11" placeholder="仅校验格式，不发送验证码" /></label>
            <label class="visitor-field"><text>身份证号 *</text><input v-model="contactIdCard" maxlength="18" placeholder="请输入18位居民身份证号" /></label>
          </view>

          <view class="visitor-card form-card">
            <view class="section-head"><text class="section-title">同行人员</text><button :disabled="companions.length >= 49" @tap="addCompanion">添加</button></view>
            <text v-if="!companions.length" class="empty-copy">没有同行人员可不添加，主联系人已计入总人数。</text>
            <view v-for="(person, index) in companions" :key="index" class="companion-card">
              <view class="companion-title"><text>同行人员 {{ index + 1 }}</text><button @tap="removeCompanion(index)">移除</button></view>
              <input v-model="person.personName" maxlength="50" placeholder="姓名" />
              <input v-model="person.idCard" maxlength="18" placeholder="18位居民身份证号" />
            </view>
            <text class="limit-copy">本次共 {{ companions.length + 1 }} 人，最多登记50人</text>
          </view>

          <view class="visitor-card form-card">
            <text class="section-title">车辆与备注</text>
            <radio-group class="travel-options" @change="travelMode = $event.detail.value">
              <label><radio value="OTHER" :checked="travelMode === 'OTHER'" color="#315f86" />非驾车</label>
              <label><radio value="DRIVING" :checked="travelMode === 'DRIVING'" color="#315f86" />驾车</label>
            </radio-group>
            <label v-if="travelMode === 'DRIVING'" class="visitor-field"><text>车牌号 *</text><input v-model="vehiclePlate" maxlength="20" placeholder="请输入本次来访车辆车牌" /></label>
            <label class="visitor-field"><text>外访备注</text><textarea v-model="visitorRemark" maxlength="500" placeholder="可填写需要接待人提前了解的事项" /></label>
          </view>

          <view class="visitor-card privacy-card">
            <checkbox-group @change="privacyChange">
              <label class="privacy-check"><checkbox value="agreed" :checked="privacyAgreed" color="#315f86" /><text>我已阅读并同意隐私告知</text></label>
            </checkbox-group>
            <text class="privacy-copy">为完成项目现场外访报备及后续门岗核验，系统将收集姓名、手机号、身份证号、单位和车牌信息。手机号、身份证号采用加密方式长期保存，仅授权项目人员可查看或导出。提交后访客不能自行修改。</text>
          </view>

          <view v-if="visitorSessionToken" class="visitor-card profile-save-card">
            <checkbox-group @change="profileSaveChange">
              <label class="privacy-check">
                <checkbox value="save" :checked="savingProfile" color="#315f86" />
                <text>{{ selectedProfile ? '用本次修改更新这份常用资料' : '将本次人员和车辆信息保存为常用资料' }}</text>
              </label>
            </checkbox-group>
            <label v-if="savingProfile" class="visitor-field">
              <text>常用资料名称</text>
              <input v-model="profileName" maxlength="100" placeholder="例如：张三项目会议资料" />
            </label>
            <text class="privacy-copy">此项为单独、自愿的长期保存同意。保存内容仅限当前项目的单位、人员、手机号、身份证和车辆信息，不包含本次来访时间、事由、地点及备注；以后可停用。</text>
          </view>

          <view v-if="errorMessage" class="submit-error">{{ errorMessage }}</view>
          <button class="submit-button" :disabled="submitting" @tap="submit">{{ submitting ? '正在提交...' : '确认提交外访信息' }}</button>
          <text class="submit-hint">请确认所有入场人员信息准确，提交后将立即锁定。</text>
        </template>
      </template>
    </view>
  </view>
</template>

<style scoped>
.visitor-shell{min-height:100vh;background:var(--workspace-page);color:var(--workspace-text)}.visitor-content{display:flex;flex-direction:column;gap:20rpx;padding:20rpx 24rpx calc(46rpx + env(safe-area-inset-bottom))}.visitor-card{border:1rpx solid var(--workspace-divider);border-radius:22rpx;background:#fff;box-shadow:var(--workspace-shadow)}.invite-card{padding:26rpx;background:linear-gradient(145deg,#eaf3fa,#fff)}.invite-top{display:flex;align-items:center;justify-content:space-between;color:var(--workspace-accent-deep);font-size:21rpx;font-weight:750}.invite-title{display:block;margin:26rpx 0 20rpx;font-size:34rpx;font-weight:900;text-align:center}.invite-info{display:grid;grid-template-columns:130rpx 1fr;gap:14rpx;margin-top:12rpx;font-size:22rpx;line-height:1.6}.invite-info text:first-child{color:var(--workspace-text-muted)}.form-card,.profile-card{padding:26rpx}.section-title{font-size:28rpx;font-weight:850}.profile-subtitle{display:block;margin-top:5rpx;color:var(--workspace-text-muted);font-size:19rpx}.section-head,.companion-title{display:flex;align-items:center;justify-content:space-between}.section-head button,.companion-title button{min-height:52rpx;padding:0 18rpx;border:1rpx solid var(--workspace-divider);border-radius:12rpx;background:#f7fafc;color:var(--workspace-accent-deep);font-size:20rpx}.profile-list{display:flex;flex-direction:column;gap:14rpx;margin-top:20rpx}.profile-item{display:flex;align-items:center;justify-content:space-between;gap:14rpx;padding:18rpx;border:1rpx solid var(--workspace-divider);border-radius:16rpx;background:#f9fbfc}.profile-item.selected{border-color:var(--workspace-accent-deep);background:#edf5fa}.profile-item.loading{border-color:#89a9bf;background:#f0f6fa}.profile-main{display:flex;min-width:0;flex:1;flex-direction:column;gap:7rpx}.profile-main text:first-child{font-size:24rpx;font-weight:800}.profile-main text:not(:first-child){color:var(--workspace-text-muted);font-size:20rpx}.profile-main .profile-loading-copy{color:var(--workspace-accent-deep);font-weight:700}.profile-item>button{min-height:48rpx;padding:0 15rpx;border:1rpx solid #e8c7c4;border-radius:11rpx;background:#fff7f6;color:#a64d45;font-size:19rpx}.profile-item>button[disabled]{opacity:.55}.profile-notice,.selected-profile-copy{display:block;margin-top:18rpx;border-radius:14rpx;padding:16rpx;background:#fff8e9;color:#80602d;font-size:20rpx;line-height:1.65}.selected-profile-copy{background:#edf6f2;color:#356b58}.visitor-field{display:flex;flex-direction:column;gap:10rpx;margin-top:22rpx}.visitor-field>text{color:var(--workspace-text-secondary);font-size:22rpx;font-weight:650}.visitor-field input,.visitor-field textarea,.companion-card input{width:100%;border:1rpx solid #d5e0e7;border-radius:14rpx;background:#f9fbfc;font-size:24rpx}.visitor-field input,.companion-card input{height:78rpx;padding:0 20rpx}.visitor-field textarea{min-height:150rpx;padding:18rpx 20rpx}.empty-copy,.limit-copy{display:block;margin-top:18rpx;color:var(--workspace-text-muted);font-size:21rpx;line-height:1.6}.companion-card{display:flex;flex-direction:column;gap:12rpx;margin-top:18rpx;padding:18rpx;border:1rpx solid var(--workspace-divider);border-radius:16rpx;background:#f8fafb}.companion-title text{font-size:22rpx;font-weight:750}.travel-options{display:flex;gap:40rpx;margin-top:24rpx}.travel-options label{display:flex;align-items:center;gap:8rpx;font-size:23rpx}.privacy-card,.profile-save-card{padding:24rpx;background:#f8fbfd}.profile-save-card{background:#f7fbf8}.privacy-check{display:flex;align-items:center;gap:8rpx;font-size:23rpx;font-weight:750}.privacy-copy{display:block;margin-top:15rpx;color:var(--workspace-text-muted);font-size:20rpx;line-height:1.75}.submit-button{min-height:84rpx;border-radius:16rpx;background:var(--workspace-accent-deep);color:#fff;font-size:26rpx;font-weight:800}.submit-button[disabled]{opacity:.65}.submit-hint{color:var(--workspace-text-muted);font-size:20rpx;text-align:center}.submit-error{border:1rpx solid #edc8c5;border-radius:14rpx;padding:18rpx;background:#fff4f3;color:#a63f3f;font-size:21rpx}.state-card{display:flex;min-height:300rpx;align-items:center;justify-content:center;flex-direction:column;padding:40rpx;color:var(--workspace-text-muted);font-size:23rpx;text-align:center}.error-state text:first-child{color:var(--workspace-text);font-size:29rpx;font-weight:850}.error-state text:nth-child(2){margin-top:14rpx;line-height:1.6}.error-state button{min-height:66rpx;margin-top:24rpx;padding:0 36rpx;border-radius:14rpx;background:var(--workspace-accent-deep);color:#fff;font-size:22rpx}.result-card{display:flex;align-items:center;flex-direction:column;padding:54rpx 30rpx;text-align:center}.result-mark{display:flex;width:92rpx;height:92rpx;align-items:center;justify-content:center;border-radius:50%;background:#eaf6f1;color:#2f8065;font-size:50rpx;font-weight:900}.result-card.expired .result-mark,.result-card.voided .result-mark{background:#fceeed;color:#b75353}.result-title{margin:24rpx 0 12rpx;font-size:30rpx;font-weight:850}.result-card text:last-child{color:var(--workspace-text-muted);font-size:21rpx;line-height:1.7}
</style>
