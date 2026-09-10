<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import {
  confirmPublicMeetingCheckin,
  createPublicMeetingCheckinSession,
  getPublicMeetingCheckinProfile,
  getPublicMeetingCheckinProfiles,
  submitPublicMeetingCheckinWalkIn,
  type MeetingCheckinLocationPayload,
  type PublicMeetingCheckinAttendee,
  type PublicMeetingCheckinMeeting,
  type PublicMeetingCheckinReceipt,
  type SiteVisitCompanionInput,
  type SiteVisitorProfile
} from '@/api/siteAccess';
import { extractMeetingCheckinToken } from '@/utils/meetingCheckinScene';
import { getFreshWechatCode } from '@/utils/wechat';
import { showToast } from '@/utils/navigation';

const sceneToken = ref('');
const visitorSessionToken = ref('');
const meeting = ref<PublicMeetingCheckinMeeting>();
const attendees = ref<PublicMeetingCheckinAttendee[]>([]);
const pageState = ref('');
const registrationNo = ref('');
const registrationSource = ref('');
const selectedIds = ref<number[]>([]);
const completedNames = ref<Record<number, string>>({});
const receipt = ref<PublicMeetingCheckinReceipt>();
const loading = ref(true);
const submitting = ref(false);
const errorMessage = ref('');
const locationNotice = ref('签到时会申请一次定位，仅用于计算与项目的距离，不保存原始经纬度。');

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

const pendingAttendees = computed(() => attendees.value.filter((person) => person.attendanceStatus !== 'CHECKED_IN'));
const completed = computed(() => attendees.value.length > 0 && pendingAttendees.value.length === 0);
const reservedMode = computed(() => pageState.value !== 'WALK_IN_FORM');

onLoad(async (options) => {
  // #ifndef MP-WEIXIN
  errorMessage.value = '会议现场签到仅支持微信小程序扫码';
  loading.value = false;
  return;
  // #endif
  sceneToken.value = extractMeetingCheckinToken(options as Record<string, unknown>);
  await initialize();
});

async function initialize() {
  loading.value = true;
  errorMessage.value = '';
  if (!sceneToken.value) {
    errorMessage.value = '当前二维码不是有效的会议现场签到码';
    loading.value = false;
    return;
  }
  try {
    const session = await createPublicMeetingCheckinSession(sceneToken.value, await getFreshWechatCode());
    visitorSessionToken.value = session.visitorSessionToken;
    meeting.value = session.meeting;
    attendees.value = session.attendees || [];
    pageState.value = session.pageState;
    registrationNo.value = session.registrationNo || '';
    registrationSource.value = session.registrationSource || '';
    selectedIds.value = pendingAttendees.value.map((person) => person.personId);
    completedNames.value = Object.fromEntries(pendingAttendees.value.map((person) => [person.personId, person.personName || '']));
    if (session.pageState === 'WALK_IN_FORM') void loadProfiles();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '会议签到入口加载失败';
  } finally {
    loading.value = false;
  }
}

async function loadProfiles() {
  profilesLoading.value = true;
  try { profiles.value = await getPublicMeetingCheckinProfiles(visitorSessionToken.value); }
  catch { profiles.value = []; }
  finally { profilesLoading.value = false; }
}

async function chooseProfile(profile: SiteVisitorProfile) {
  try {
    const detail = await getPublicMeetingCheckinProfile(visitorSessionToken.value, profile.profileCode);
    visitorCompany.value = detail.visitorCompany || '';
    contactName.value = detail.contactName || '';
    contactPhone.value = detail.contactPhone || '';
    travelMode.value = detail.travelMode || 'OTHER';
    vehiclePlate.value = detail.vehiclePlate || '';
    showToast('已带入本人信息，同行人员请按本次到场填写');
  } catch (error) { showToast(error instanceof Error ? error.message : '常用资料加载失败'); }
}

function togglePerson(personId: number) {
  selectedIds.value = selectedIds.value.includes(personId)
    ? selectedIds.value.filter((id) => id !== personId)
    : [...selectedIds.value, personId];
}

function addCompanion() {
  if (companions.value.length >= 49) return showToast('同行人员最多49位');
  companions.value.push({ personCompany: '', personName: '', personPhone: '' });
}

function removeCompanion(index: number) { companions.value.splice(index, 1); }

async function collectLocation(): Promise<MeetingCheckinLocationPayload> {
  return new Promise((resolve) => {
    uni.getLocation({
      type: 'gcj02',
      isHighAccuracy: true,
      highAccuracyExpireTime: 5000,
      success: (result) => {
        const accuracy = Math.max(0, Math.round(Number(result.accuracy || 0)));
        locationNotice.value = accuracy > 100
          ? `定位精度约 ${accuracy} 米，结果将标记为低精度证据，但不影响签到。`
          : `已取得定位，精度约 ${accuracy} 米。`;
        resolve({ locationAvailable: true, latitude: result.latitude, longitude: result.longitude, accuracyMeters: accuracy });
      },
      fail: () => {
        locationNotice.value = '未取得定位，系统会记录“定位不可用”，仍可继续签到。';
        resolve({ locationAvailable: false });
      }
    });
  });
}

async function confirmReserved() {
  if (submitting.value) return;
  const selected = pendingAttendees.value.filter((person) => selectedIds.value.includes(person.personId));
  if (!selected.length) return showToast('请勾选本次实际到场人员');
  for (const person of selected) {
    if (!person.personName?.trim() && !completedNames.value[person.personId]?.trim()) {
      return showToast('请补全所有已勾选到场人员姓名');
    }
  }
  submitting.value = true;
  errorMessage.value = '';
  try {
    const location = await collectLocation();
    const result = await confirmPublicMeetingCheckin(selected.map((person) => ({
      personId: person.personId,
      completedName: person.personName?.trim() ? undefined : completedNames.value[person.personId].trim()
    })), location, visitorSessionToken.value);
    applyReceipt(result);
    showToast(result.pendingCount ? '本批人员签到成功，可稍后复扫补签' : '本组人员已全部签到');
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : '签到失败'; }
  finally { submitting.value = false; }
}

function validateWalkIn() {
  if (!visitorCompany.value.trim()) return '请填写单位';
  if (!contactName.value.trim()) return '请填写姓名';
  if (!/^1[3-9]\d{9}$/.test(contactPhone.value.trim())) return '请填写正确的手机号码';
  for (let index = 0; index < companions.value.length; index += 1) {
    const person = companions.value[index];
    const hasContent = person.personCompany.trim() || person.personName.trim() || person.personPhone.trim();
    if (!hasContent) continue;
    if (!person.personName.trim()) return `请填写第${index + 1}位同行人员姓名`;
    if (person.personPhone.trim() && !/^1[3-9]\d{9}$/.test(person.personPhone.trim())) return `第${index + 1}位同行人员手机号码不正确`;
  }
  if (travelMode.value === 'DRIVING' && !vehiclePlate.value.trim()) return '驾车到场请填写车牌号';
  if (!privacyAgreed.value) return '请阅读并同意隐私告知';
  return '';
}

async function submitWalkIn() {
  if (submitting.value) return;
  const validation = validateWalkIn();
  if (validation) return showToast(validation);
  submitting.value = true;
  errorMessage.value = '';
  try {
    const location = await collectLocation();
    const result = await submitPublicMeetingCheckinWalkIn({
      visitorCompany: visitorCompany.value.trim(), contactName: contactName.value.trim(),
      contactPhone: contactPhone.value.trim(),
      companions: companions.value.filter((person) => person.personCompany.trim() || person.personName.trim() || person.personPhone.trim()).map((person) => ({
        personCompany: person.personCompany.trim(), personName: person.personName.trim(), personPhone: person.personPhone.trim()
      })),
      travelMode: travelMode.value,
      vehiclePlate: travelMode.value === 'DRIVING' ? vehiclePlate.value.trim().toUpperCase() : undefined,
      visitorRemark: visitorRemark.value.trim() || undefined,
      privacyAgreed: true,
      location
    }, visitorSessionToken.value);
    pageState.value = 'COMPLETED';
    applyReceipt(result);
    showToast('现场补录并签到成功');
  } catch (error) { errorMessage.value = error instanceof Error ? error.message : '现场签到失败'; }
  finally { submitting.value = false; }
}

function applyReceipt(value: PublicMeetingCheckinReceipt) {
  receipt.value = value;
  attendees.value = value.attendees;
  registrationNo.value = value.registrationNo;
  registrationSource.value = value.registrationSource;
  selectedIds.value = pendingAttendees.value.map((person) => person.personId);
  pageState.value = value.pendingCount > 0 ? 'RESERVED_PARTIAL' : 'COMPLETED';
}

function formatTime(value?: string) { return value ? String(value).replace('T', ' ').slice(0, 16) : '-'; }
function locationLabel(value?: string) {
  return ({ IN_RANGE: '定位在会场范围内', OUT_OF_RANGE: '定位超出会场范围', UNAVAILABLE: '未取得定位', NO_REFERENCE: '项目未配置定位', MANUAL: '工作人员人工签到' } as Record<string, string>)[value || ''] || '定位结果待记录';
}
</script>

<template>
  <view class="checkin-shell">
    <AppNavBar title="会议现场签到" :show-back="true" />
    <view v-if="loading" class="checkin-card state-card">正在识别会议和微信身份...</view>
    <view v-else-if="errorMessage && !meeting" class="checkin-card state-card error"><text>无法进入签到</text><text>{{ errorMessage }}</text><button @click="initialize">重新识别</button></view>
    <view v-else-if="meeting" class="checkin-content">
      <view class="checkin-card meeting-card"><text class="eyebrow">会场签到专用入口</text><text class="meeting-title">{{ meeting.purpose }}</text><view class="meeting-grid"><text>项目</text><text>{{ meeting.projectShortName || meeting.projectName }}</text><text>会议时间</text><text>{{ formatTime(meeting.visitStartTime) }} 至 {{ formatTime(meeting.visitEndTime) }}</text><text>会议地点</text><text>{{ meeting.visitLocation }}</text><text>接待人</text><text>{{ meeting.hostName }}</text><text>签到窗口</text><text>{{ formatTime(meeting.checkinStartTime) }} 至 {{ formatTime(meeting.checkinEndTime) }}</text></view></view>
      <view class="location-notice"><text>定位说明</text><text>{{ locationNotice }}</text></view>
      <view v-if="errorMessage" class="error-banner" @click="errorMessage = ''">{{ errorMessage }}</view>

      <view v-if="reservedMode" class="checkin-card">
        <view class="section-head"><view><text>{{ registrationSource === 'WALK_IN' ? '本次现场登记人员' : '我的预约人员' }}</text><text>登记编号 {{ registrationNo }}</text></view><text>{{ attendees.length - pendingAttendees.length }}/{{ attendees.length }} 已签到</text></view>
        <view v-for="person in attendees" :key="person.personId" class="attendee-row" :class="{ checked: person.attendanceStatus === 'CHECKED_IN' }" @click="person.attendanceStatus !== 'CHECKED_IN' && togglePerson(person.personId)">
          <checkbox :checked="person.attendanceStatus === 'CHECKED_IN' || selectedIds.includes(person.personId)" :disabled="person.attendanceStatus === 'CHECKED_IN'" />
          <view><text>{{ person.personName || '姓名待补全' }}</text><text>{{ person.personType === 'CONTACT' ? '本人' : '同行人员' }}{{ person.personCompany ? ` · ${person.personCompany}` : '' }}</text><input v-if="!person.personName && person.attendanceStatus !== 'CHECKED_IN'" v-model="completedNames[person.personId]" maxlength="50" placeholder="请填写实际到场人员姓名" @click.stop /></view>
          <text class="attendee-status">{{ person.attendanceStatus === 'CHECKED_IN' ? '已签到' : '待签到' }}</text>
        </view>
        <button v-if="!completed" class="primary-button" :disabled="submitting" @click="confirmReserved">{{ submitting ? '签到处理中...' : '确认本批到场人员' }}</button>
      </view>

      <template v-if="!reservedMode">
        <view class="checkin-card"><view class="section-head"><view><text>现场补录</text><text>未查到当前微信的预约登记，请补充到场信息</text></view></view></view>
        <view v-if="profilesLoading" class="checkin-card profile-card">正在读取常用资料...</view>
        <view v-else-if="profiles.length" class="checkin-card profile-card"><text class="block-title">选择常用资料快速填写</text><scroll-view scroll-x><view class="profile-row"><button v-for="profile in profiles" :key="profile.profileCode" @click="chooseProfile(profile)"><text>{{ profile.profileName }}</text><text>{{ profile.visitorCompany }} · {{ profile.visitorCount }}人</text></button></view></scroll-view></view>
        <view class="checkin-card form-card"><text class="block-title">来访人员信息</text><label><text>单位 *</text><input v-model="visitorCompany" maxlength="200" placeholder="请输入单位" /></label><view class="two-columns"><label><text>姓名 *</text><input v-model="contactName" maxlength="50" placeholder="请输入姓名" /></label><label><text>手机号码 *</text><input v-model="contactPhone" type="number" maxlength="11" placeholder="请输入手机号码" /></label></view></view>
        <view class="checkin-card form-card"><view class="section-head"><view><text>同行到场人员</text><text>每位实际到场人员都必须填写姓名</text></view><button @click="addCompanion">添加</button></view><view v-for="(person, index) in companions" :key="index" class="companion-card"><input v-model="person.personCompany" maxlength="200" placeholder="单位（选填）" /><input v-model="person.personName" maxlength="50" placeholder="姓名（必填）" /><input v-model="person.personPhone" type="number" maxlength="11" placeholder="手机号码（选填）" /><button @click="removeCompanion(index)">移除</button></view></view>
        <view class="checkin-card form-card"><text class="block-title">出行信息</text><view class="travel-tabs"><button :class="{ active: travelMode === 'OTHER' }" @click="travelMode = 'OTHER'">非驾车</button><button :class="{ active: travelMode === 'DRIVING' }" @click="travelMode = 'DRIVING'">驾车</button></view><label v-if="travelMode === 'DRIVING'"><text>车牌号 *</text><input v-model="vehiclePlate" maxlength="20" placeholder="请输入车牌号" /></label><label><text>现场备注</text><textarea v-model="visitorRemark" maxlength="500" placeholder="选填" /></label></view>
        <view class="checkin-card privacy-card"><label><checkbox :checked="privacyAgreed" @click="privacyAgreed = !privacyAgreed" /><text>我已阅读并同意现场签到隐私告知</text></label><text>系统保存姓名、单位、手机号码、签到时间、距离与定位精度；原始经纬度仅用于本次距离计算，不写入数据库或普通日志。定位拒绝、失败或超距均不阻断签到。</text></view>
        <button class="primary-button page-button" :disabled="submitting" @click="submitWalkIn">{{ submitting ? '提交中...' : '现场补录并完成签到' }}</button>
      </template>

      <view v-if="receipt || completed" class="checkin-card success-card"><text class="success-mark">✓</text><text class="success-title">{{ completed ? '签到已完成' : '本批签到成功' }}</text><text>登记编号 {{ registrationNo }}</text><text v-if="receipt">{{ locationLabel(receipt.locationResult) }}{{ receipt.distanceMeters == null ? '' : ` · ${receipt.distanceMeters}米` }}{{ receipt.accuracyMeters == null ? '' : ` · 精度${receipt.accuracyMeters}米` }}</text><text v-if="receipt?.pendingCount">本组仍有 {{ receipt.pendingCount }} 人未签到，可稍后再次扫描会场码补签。</text></view>
    </view>
  </view>
</template>

<style scoped>
.checkin-shell{min-height:100vh;background:#f2f6fa;color:#172033;padding-bottom:48rpx}.checkin-content{display:flex;flex-direction:column;gap:20rpx;padding:20rpx 24rpx}.checkin-card{border:1rpx solid #dce5ed;border-radius:22rpx;background:#fff;padding:28rpx;box-shadow:0 8rpx 28rpx rgba(25,52,75,.07)}.state-card{display:flex;min-height:360rpx;align-items:center;justify-content:center;flex-direction:column;gap:20rpx;margin:24rpx;text-align:center;color:#607086}.state-card.error text:first-child{color:#a63f3f;font-size:32rpx;font-weight:850}.state-card button{min-height:68rpx;padding:0 32rpx;border-radius:14rpx;background:#1c628f;color:#fff;font-size:23rpx}.meeting-card{background:linear-gradient(145deg,#163853,#256b95);color:#fff}.eyebrow,.meeting-title{display:block}.eyebrow{font-size:20rpx;opacity:.76}.meeting-title{margin:14rpx 0 26rpx;font-size:36rpx;font-weight:900}.meeting-grid{display:grid;grid-template-columns:145rpx 1fr;gap:15rpx 12rpx;font-size:23rpx;line-height:1.5}.meeting-grid text:nth-child(odd){opacity:.65}.location-notice{display:flex;gap:14rpx;border-radius:16rpx;padding:18rpx 22rpx;background:#fff7e7;color:#795b24;font-size:21rpx;line-height:1.55}.location-notice text:first-child{flex:none;font-weight:800}.error-banner{border:1rpx solid #eabdb9;border-radius:14rpx;padding:18rpx;background:#fff1ef;color:#a33d37;font-size:22rpx}.section-head{display:flex;align-items:center;justify-content:space-between;gap:16rpx}.section-head>view{display:flex;flex-direction:column;gap:6rpx}.section-head>view text:first-child,.block-title{font-size:28rpx;font-weight:850}.section-head>view text:last-child{color:#7b8999;font-size:20rpx}.section-head>text{color:#25704f;font-size:22rpx;font-weight:750}.section-head>button{min-height:54rpx;padding:0 18rpx;border-radius:12rpx;background:#eef5fa;color:#1d628e;font-size:20rpx}.attendee-row{display:flex;align-items:center;gap:14rpx;margin-top:18rpx;border:1rpx solid #dce5ed;border-radius:16rpx;padding:18rpx;background:#fafcfe}.attendee-row.checked{background:#f0faf4;border-color:#b8dfc8}.attendee-row>view{display:flex;min-width:0;flex:1;flex-direction:column;gap:6rpx}.attendee-row>view>text:first-child{font-size:25rpx;font-weight:800}.attendee-row>view>text:nth-child(2){color:#718091;font-size:20rpx}.attendee-row input{height:62rpx;margin-top:5rpx;border:1rpx solid #d5e0e7;border-radius:11rpx;padding:0 14rpx;background:#fff;font-size:22rpx}.attendee-status{color:#277655;font-size:20rpx}.primary-button{min-height:82rpx;margin-top:26rpx;border-radius:16rpx;background:#176b9f;color:#fff;font-size:25rpx;font-weight:850}.primary-button[disabled]{opacity:.6}.page-button{width:calc(100% - 48rpx);margin:0 24rpx}.profile-card{color:#607086;font-size:22rpx}.profile-row{display:flex;gap:14rpx;margin-top:18rpx}.profile-row button{display:flex;width:360rpx;flex:none;flex-direction:column;align-items:flex-start;gap:8rpx;border:1rpx solid #d5e2ec;border-radius:14rpx;padding:18rpx;background:#f7fafc;font-size:21rpx}.profile-row button text:first-child{font-weight:800}.profile-row button text:last-child{color:#718091}.form-card label>text{display:block;margin:20rpx 0 9rpx;color:#536375;font-size:22rpx}.form-card input,.form-card textarea{box-sizing:border-box;width:100%;border:1rpx solid #d5e0e7;border-radius:13rpx;padding:17rpx;background:#f9fbfc;font-size:23rpx}.form-card textarea{height:130rpx}.two-columns{display:grid;grid-template-columns:1fr 1fr;gap:14rpx}.companion-card{display:grid;grid-template-columns:1fr 1fr;gap:10rpx;margin-top:16rpx;border:1rpx solid #e0e8ee;border-radius:15rpx;padding:14rpx;background:#f9fbfc}.companion-card input:first-child{grid-column:1/3}.companion-card button{color:#ad443e;font-size:20rpx}.travel-tabs{display:flex;gap:12rpx;margin-top:18rpx}.travel-tabs button{flex:1;background:#eef3f7;color:#526476}.travel-tabs button.active{background:#176b9f;color:#fff}.privacy-card label{display:flex;align-items:center;gap:8rpx;font-size:23rpx;font-weight:800}.privacy-card>text{display:block;margin-top:15rpx;color:#6d7d8d;font-size:20rpx;line-height:1.7}.success-card{display:flex;align-items:center;flex-direction:column;gap:12rpx;border-color:#a8d8bc;background:linear-gradient(145deg,#ecfaf2,#fff);text-align:center;color:#3a6a52}.success-mark{display:grid;width:88rpx;height:88rpx;place-items:center;border-radius:50%;background:#22845b;color:#fff;font-size:50rpx;font-weight:900}.success-title{font-size:32rpx;font-weight:900;color:#237052}
</style>
