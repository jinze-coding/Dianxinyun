<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import {
  getRegistrationCaptcha,
  searchRegistrationProjects,
  submitRegistrationApplication,
  type RegistrationProjectOption
} from '@/api/registration';
import { showToast } from '@/utils/navigation';
import { getFreshWechatCode } from '@/utils/wechat';

const STATUS_TOKEN_KEY = 'registration_status_query_token';
const form = reactive({
  password: '',
  confirmPassword: '',
  realName: '',
  phone: '',
  email: '',
  reason: ''
});
const submitting = ref(false);
const returnUrl = ref('');
const wechatSessionToken = ref('');
const supportsWechatQuick = ref(false);
const isH5 = ref(false);
const registrationMode = ref<'WECHAT_QUICK' | 'STANDARD'>('STANDARD');
const quickMode = computed(() => registrationMode.value === 'WECHAT_QUICK');
const captcha = reactive({ id: '', image: '', code: '' });
const selectedProjects = ref<RegistrationProjectOption[]>([]);
const projectOptions = ref<RegistrationProjectOption[]>([]);
const projectKeyword = ref('');
const projectSheetOpen = ref(false);
const projectSearchBusy = ref(false);
const projectSearchError = ref('');
const projectSelectionAtLimit = computed(() => selectedProjects.value.length >= 50);
let projectSearchTimer: ReturnType<typeof setTimeout> | undefined;
let projectSearchRequestId = 0;

// #ifdef MP-WEIXIN
supportsWechatQuick.value = true;
registrationMode.value = 'WECHAT_QUICK';
// #endif
// #ifdef H5
isH5.value = true;
// #endif

onLoad((options) => {
  returnUrl.value = decodeURIComponent(String(options?.returnUrl || ''));
  wechatSessionToken.value = String(options?.session || '');
  if (isH5.value) refreshCaptcha();
});

onBeforeUnmount(() => {
  if (projectSearchTimer) clearTimeout(projectSearchTimer);
  projectSearchRequestId += 1;
});

watch(projectKeyword, (value) => {
  if (projectSearchTimer) clearTimeout(projectSearchTimer);
  const keyword = value.trim();
  const requestKeyword = keyword.length >= 2 ? keyword : '';
  const requestId = ++projectSearchRequestId;
  projectSearchBusy.value = true;
  projectSearchError.value = '';
  projectSearchTimer = setTimeout(async () => {
    try {
      const result = await searchRegistrationProjects(requestKeyword);
      if (requestId !== projectSearchRequestId) return;
      projectOptions.value = result;
    } catch (error) {
      if (requestId !== projectSearchRequestId) return;
      projectOptions.value = [];
      projectSearchError.value = error instanceof Error ? error.message : '项目列表加载失败';
    } finally {
      if (requestId === projectSearchRequestId) projectSearchBusy.value = false;
    }
  }, keyword ? 250 : 0);
});

async function loadAvailableProjects() {
  if (projectSearchTimer) clearTimeout(projectSearchTimer);
  const requestId = ++projectSearchRequestId;
  projectSearchBusy.value = true;
  projectSearchError.value = '';
  try {
    const result = await searchRegistrationProjects('');
    if (requestId !== projectSearchRequestId) return;
    projectOptions.value = result;
  } catch (error) {
    if (requestId !== projectSearchRequestId) return;
    projectOptions.value = [];
    projectSearchError.value = error instanceof Error ? error.message : '项目列表加载失败';
  } finally {
    if (requestId === projectSearchRequestId) projectSearchBusy.value = false;
  }
}

function goBack() {
  getCurrentPages().length > 1 ? uni.navigateBack() : uni.reLaunch({ url: '/pages/login/index' });
}

function validateApplicantInfo() {
  if (!form.realName.trim()) {
    showToast('请填写真实姓名');
    return false;
  }
  if (!selectedProjects.value.length) {
    showToast('请至少选择一个申请项目');
    return false;
  }
  if (form.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) {
    showToast('邮箱格式不正确');
    return false;
  }
  return true;
}

function validateManualAccount() {
  if (form.password.length < 8 || form.password.length > 72
    || !/[A-Za-z]/.test(form.password) || !/\d/.test(form.password)) {
    showToast('密码需为 8–72 位，且同时包含字母和数字');
    return false;
  }
  if (form.password !== form.confirmPassword) {
    showToast('两次输入的密码不一致');
    return false;
  }
  if (!/^1\d{10}$/.test(form.phone.trim())) {
    showToast('请输入正确手机号');
    return false;
  }
  if (isH5.value && !captcha.code.trim()) {
    showToast('请输入图形验证码');
    return false;
  }
  return true;
}

async function refreshCaptcha() {
  if (!isH5.value) return;
  try {
    const result = await getRegistrationCaptcha();
    captcha.id = result.captchaId;
    captcha.image = result.image;
    captcha.code = '';
  } catch (error) {
    showToast(error instanceof Error ? error.message : '图形验证码获取失败');
  }
}

async function submitApplication(phoneCode?: string) {
  if (!validateApplicantInfo()) return;
  if (quickMode.value && !phoneCode) {
    showToast('请先授权微信手机号');
    return;
  }
  if (!quickMode.value && !validateManualAccount()) return;

  submitting.value = true;
  try {
    const isQuick = quickMode.value;
    let wechatCode: string | undefined;
    // #ifdef MP-WEIXIN
    wechatCode = await getFreshWechatCode();
    // #endif
    const result = await submitRegistrationApplication({
      // 快捷注册的手机号、登录账号仅由服务端通过微信授权结果写入。
      username: isQuick ? undefined : form.phone.trim(),
      password: isQuick ? undefined : form.password,
      realName: form.realName.trim(),
      phone: isQuick ? undefined : form.phone.trim(),
      email: form.email.trim() || undefined,
      applicationReason: form.reason.trim() || undefined,
      desiredProjectIds: selectedProjects.value.map((project) => Number(project.projectId)),
      sourceType: isH5.value ? 'WEB' : 'MINI',
      phoneVerificationType: isQuick ? 'WECHAT' : isH5.value ? 'MANUAL_REVIEW' : 'MANUAL',
      registrationMode: isQuick ? 'WECHAT_QUICK' : 'STANDARD',
      captchaId: isH5.value ? captcha.id : undefined,
      captchaCode: isH5.value ? captcha.code.trim() : undefined,
      wechatCode,
      wechatSessionToken: wechatSessionToken.value || undefined,
      phoneCode
    });
    if (!result.statusQueryToken) throw new Error('申请已提交，但未返回状态查询凭证，请联系管理员');
    uni.setStorageSync(STATUS_TOKEN_KEY, result.statusQueryToken);
    form.password = '';
    form.confirmPassword = '';
    showToast(result.message || '注册申请已提交');
    uni.redirectTo({
      url: `/pages/registration-status/index?token=${encodeURIComponent(result.statusQueryToken)}`
    });
  } catch (error) {
    if (isH5.value) refreshCaptcha();
    showToast(error instanceof Error ? error.message : '提交失败');
  } finally {
    submitting.value = false;
  }
}

function getPhone(event: { detail?: { code?: string } }) {
  if (!event.detail?.code) {
    showToast('未取得微信手机号，可改用手工手机号注册');
    return;
  }
  submitApplication(event.detail.code);
}

function switchMode() {
  if (!supportsWechatQuick.value) return;
  registrationMode.value = quickMode.value ? 'STANDARD' : 'WECHAT_QUICK';
}

function openProjectSheet() {
  projectSheetOpen.value = true;
  const hadKeyword = Boolean(projectKeyword.value.trim());
  projectKeyword.value = '';
  if (!hadKeyword) loadAvailableProjects();
}

function closeProjectSheet() {
  projectSheetOpen.value = false;
}

function isProjectSelected(projectId: number) {
  return selectedProjects.value.some((project) => Number(project.projectId) === Number(projectId));
}

function toggleProject(project: RegistrationProjectOption) {
  if (isProjectSelected(project.projectId)) {
    selectedProjects.value = selectedProjects.value.filter((item) => Number(item.projectId) !== Number(project.projectId));
    return;
  }
  if (selectedProjects.value.length >= 50) {
    showToast('最多选择50个项目');
    return;
  }
  selectedProjects.value = [...selectedProjects.value, project];
  if (projectKeyword.value.trim()) projectKeyword.value = '';
}

function removeProject(projectId: number) {
  selectedProjects.value = selectedProjects.value.filter((item) => Number(item.projectId) !== Number(projectId));
}
</script>

<template>
  <view class="shell">
    <AppNavBar title="申请注册账号" @back="goBack" />
    <scroll-view scroll-y class="scroll">
      <view class="content">
        <view class="notice">
          <text>{{ quickMode ? '微信授权手机号将作为系统登录账号' : '手机号将作为系统登录账号' }}</text>
          <text v-if="quickMode">审批通过后请使用微信登录，并立即设置登录密码；设置完成前不能进入业务系统。</text>
          <text v-else>管理员会审核申请并分配项目角色、菜单和操作权限；提交申请不代表已获得系统访问权限。</text>
        </view>

        <view class="form-card">
          <text class="group-title">申请人信息</text>
          <label><text>真实姓名 *</text><input v-model="form.realName" placeholder="请填写真实姓名" /></label>

          <template v-if="!quickMode">
            <text class="group-title second">登录账号</text>
            <label><text>手机号（登录账号） *</text><input v-model="form.phone" type="number" maxlength="11" placeholder="请输入手机号" /></label>
            <label><text>登录密码 *</text><input v-model="form.password" password placeholder="至少 8 位，包含字母和数字" /></label>
            <label><text>确认密码 *</text><input v-model="form.confirmPassword" password placeholder="再次输入密码" /></label>
            <label v-if="isH5"><text>图形验证码 *</text><view class="captcha-row"><input v-model="captcha.code" placeholder="请输入验证码" /><image v-if="captcha.image" :src="captcha.image" mode="aspectFit" @tap="refreshCaptcha" /><button v-else @tap="refreshCaptcha">换一张</button></view></label>
          </template>

          <text class="group-title second">项目与补充信息</text>
          <label><text>邮箱</text><input v-model="form.email" placeholder="选填" /></label>
          <label>
            <text>申请项目 *</text>
            <view class="project-select-control" @tap="openProjectSheet">
              <view>
                <text>{{ selectedProjects.length ? `已选择 ${selectedProjects.length} 个项目` : '搜索并选择申请项目' }}</text>
                <text>{{ selectedProjects.length ? '管理员审核时可调整项目和角色' : '至少选择一个，可多选' }}</text>
              </view>
              <text class="project-select-arrow">›</text>
            </view>
            <view v-if="selectedProjects.length" class="selected-projects">
              <view v-for="project in selectedProjects" :key="project.projectId" class="selected-project-tag">
                <text>{{ project.projectName }}</text>
                <button type="button" @tap.stop="removeProject(project.projectId)">×</button>
              </view>
            </view>
          </label>
          <label><text>申请说明</text><textarea v-model="form.reason" maxlength="300" placeholder="可填写岗位、所属单位及申请原因" /></label>
        </view>

        <template v-if="quickMode">
          <button class="wechat-submit" open-type="getPhoneNumber" :disabled="submitting || !selectedProjects.length" @getphonenumber="getPhone">
            {{ submitting ? '正在提交…' : '微信快捷注册' }}
          </button>
          <button class="manual-submit" :disabled="submitting" @tap="switchMode">使用手工手机号注册</button>
        </template>
        <template v-else>
          <button class="manual-submit primary" :disabled="submitting || !selectedProjects.length" @tap="submitApplication()">
            {{ submitting ? '正在提交…' : '提交手工注册申请' }}
          </button>
          <button v-if="supportsWechatQuick" class="manual-submit" :disabled="submitting" @tap="switchMode">返回微信快捷注册</button>
        </template>
        <text class="privacy">手机号仅用于账号识别和审批联系。审批完成后可在登录页查询申请进度。</text>
      </view>
    </scroll-view>

    <view v-if="projectSheetOpen" class="project-sheet-overlay" @tap="closeProjectSheet">
      <view class="project-sheet" @tap.stop>
        <view class="sheet-handle"></view>
        <view class="project-sheet-head">
          <view><text>选择申请项目</text><text>现有可申请项目，可搜索并多选</text></view>
          <button @tap="closeProjectSheet">×</button>
        </view>
        <view class="project-search-box">
          <text>⌕</text>
          <input v-model="projectKeyword" focus placeholder="输入项目全称或简称筛选" />
        </view>
        <view v-if="selectedProjects.length" class="sheet-selected-summary">
          <text>已选择 {{ selectedProjects.length }} / 50 个项目</text>
          <scroll-view scroll-x class="sheet-selected-scroll">
            <view class="sheet-selected-row">
              <button
                v-for="project in selectedProjects"
                :key="project.projectId"
                :aria-label="`移除${project.projectName}`"
                @tap="removeProject(project.projectId)"
              >
                <text>{{ project.projectName }}</text><text>×</text>
              </button>
            </view>
          </scroll-view>
        </view>
        <view v-if="projectSelectionAtLimit" class="project-limit-hint">已达到 50 个项目的选择上限，取消一个已选项目后可继续选择。</view>
        <scroll-view scroll-y class="project-result-scroll">
          <view v-if="projectSearchBusy" class="project-empty">正在加载项目…</view>
          <view v-else-if="projectSearchError" class="project-empty error">{{ projectSearchError }}</view>
          <view v-else-if="!projectOptions.length" class="project-empty">
            {{ projectKeyword.trim().length >= 2 ? '没有匹配的可申请项目' : '暂无可申请项目' }}
          </view>
          <template v-else>
            <view v-if="projectKeyword.trim().length === 1" class="project-search-hint">再输入 1 个字符可搜索；当前仍显示现有项目</view>
            <button
              v-for="project in projectOptions"
              :key="project.projectId"
              class="project-option"
              :class="{ selected: isProjectSelected(project.projectId) }"
              :disabled="projectSelectionAtLimit && !isProjectSelected(project.projectId)"
              @tap="toggleProject(project)"
            >
              <view><text>{{ project.projectName }}</text><text>{{ [project.shortName, project.area].filter(Boolean).join(' · ') || '可申请项目' }}</text></view>
              <text class="project-check">{{ isProjectSelected(project.projectId) ? '✓' : '+' }}</text>
            </button>
          </template>
        </scroll-view>
        <button class="project-confirm" :disabled="!selectedProjects.length" @tap="closeProjectSheet">
          确认选择（{{ selectedProjects.length }}）
        </button>
      </view>
    </view>
  </view>
</template>

<style scoped>
.shell{min-height:100vh;background:#f4f7fa}.scroll{height:calc(100vh - 92rpx)}.content{display:flex;flex-direction:column;gap:20rpx;padding:26rpx 28rpx calc(44rpx + env(safe-area-inset-bottom))}.notice,.form-card{border:1rpx solid #e0e8ef;border-radius:20rpx;background:#fff}.notice{padding:22rpx;background:#edf5fb}.notice text{display:block}.notice text:first-child{color:#315f86;font-size:25rpx;font-weight:800}.notice text:last-child{margin-top:7rpx;color:#65798c;font-size:20rpx;line-height:1.6}.form-card{padding:28rpx}.group-title{display:block;margin-bottom:16rpx;color:#223247;font-size:27rpx;font-weight:800}.group-title.second{margin-top:28rpx}.form-card label{display:block;margin-top:17rpx}.form-card label>text{display:block;margin-bottom:8rpx;color:#52687a;font-size:21rpx}.form-card input,.form-card textarea{width:100%;padding:0 20rpx;border:1rpx solid #d5e0e7;border-radius:13rpx;background:#f9fbfc;font-size:23rpx}.form-card input{height:76rpx}.form-card textarea{height:150rpx;padding-top:18rpx}.captcha-row{display:flex;align-items:center;gap:14rpx}.captcha-row input{flex:1;min-width:0}.captcha-row image{width:160rpx;height:76rpx;border:1rpx solid #d5e0e7;border-radius:13rpx;background:#edf5fb}.captcha-row button{min-width:132rpx;height:76rpx;margin:0;border:1rpx solid #c8d8e5;border-radius:13rpx;background:#fff;color:#315f86;font-size:21rpx}.wechat-submit,.manual-submit{min-height:76rpx;border-radius:14rpx;font-size:23rpx;font-weight:750}.wechat-submit{background:#26a65b;color:#fff}.manual-submit{border:1rpx solid #c8d8e5;background:#fff;color:#315f86}.manual-submit.primary{border-color:#315f86;background:#315f86;color:#fff}.wechat-submit[disabled],.manual-submit[disabled]{opacity:.6}.privacy{padding:0 15rpx;color:#98a2b3;font-size:19rpx;line-height:1.6;text-align:center}
.project-select-control{display:flex;min-height:82rpx;align-items:center;justify-content:space-between;padding:14rpx 18rpx;border:1rpx solid #d5e0e7;border-radius:13rpx;background:#f9fbfc}.project-select-control view>text{display:block}.project-select-control view>text:first-child{color:#315f86;font-size:23rpx;font-weight:750}.project-select-control view>text:last-child{margin-top:5rpx;color:#8997a7;font-size:19rpx}.project-select-arrow{color:#8091a4;font-size:38rpx}.selected-projects{display:flex;flex-wrap:wrap;gap:10rpx;margin-top:12rpx}.selected-project-tag{display:flex;max-width:100%;align-items:center;gap:8rpx;padding:8rpx 9rpx 8rpx 14rpx;border:1rpx solid #c9ddf0;border-radius:999rpx;background:#edf5fb}.selected-project-tag>text{overflow:hidden;color:#315f86;font-size:20rpx;text-overflow:ellipsis;white-space:nowrap}.selected-project-tag button{display:flex;width:34rpx;height:34rpx;align-items:center;justify-content:center;margin:0;padding:0;border-radius:50%;background:#d7e7f4;color:#55748f;font-size:24rpx;line-height:1}.selected-project-tag button::after{border:0}.project-sheet-overlay{position:fixed;z-index:90;inset:0;display:flex;align-items:flex-end;background:rgba(21,35,51,.42)}.project-sheet{box-sizing:border-box;display:flex;width:100%;max-height:84vh;min-height:58vh;flex-direction:column;padding:14rpx 26rpx calc(24rpx + env(safe-area-inset-bottom));border-radius:26rpx 26rpx 0 0;background:#fff;box-shadow:0 -20rpx 60rpx rgba(20,45,70,.18)}.sheet-handle{width:66rpx;height:7rpx;margin:0 auto 18rpx;border-radius:999rpx;background:#d4dce5}.project-sheet-head{display:flex;align-items:flex-start;justify-content:space-between}.project-sheet-head view>text{display:block}.project-sheet-head view>text:first-child{color:#223247;font-size:30rpx;font-weight:850}.project-sheet-head view>text:last-child{margin-top:6rpx;color:#8190a0;font-size:20rpx}.project-sheet-head button{display:flex;width:54rpx;height:54rpx;align-items:center;justify-content:center;margin:0;padding:0;border-radius:50%;background:#f1f4f7;color:#6c7c8e;font-size:31rpx}.project-sheet-head button::after,.project-option::after{border:0}.project-search-box{display:flex;height:76rpx;align-items:center;gap:10rpx;margin-top:20rpx;padding:0 17rpx;border:1rpx solid #d6e1ea;border-radius:14rpx;background:#f7fafc}.project-search-box>text{color:#6f879b;font-size:30rpx}.project-search-box input{flex:1;font-size:22rpx}.sheet-selected-summary{margin-top:16rpx}.sheet-selected-summary>text{color:#52687a;font-size:20rpx;font-weight:700}.sheet-selected-scroll{width:100%;margin-top:9rpx;white-space:nowrap}.sheet-selected-row{display:inline-flex;gap:9rpx}.sheet-selected-row button{display:flex;align-items:center;gap:8rpx;margin:0;padding:8rpx 10rpx 8rpx 13rpx;border-radius:999rpx;background:#edf5fb;color:#315f86;font-size:19rpx;line-height:1.2}.sheet-selected-row button::after{border:0}.sheet-selected-row button text:last-child{color:#55748f;font-size:23rpx}.project-result-scroll{flex:1;min-height:260rpx;margin-top:14rpx}.project-empty{padding:70rpx 20rpx;color:#8b98a7;font-size:21rpx;text-align:center}.project-empty.error{color:#b75353}.project-option{display:flex;width:100%;min-height:96rpx;align-items:center;justify-content:space-between;margin:0 0 11rpx;padding:15rpx 17rpx;border:1rpx solid #dfe7ee;border-radius:14rpx;background:#fff;text-align:left}.project-option.selected{border-color:#8eb9db;background:#edf5fb}.project-option view>text{display:block}.project-option view>text:first-child{color:#25384d;font-size:23rpx;font-weight:800}.project-option view>text:last-child{margin-top:6rpx;color:#8291a0;font-size:19rpx}.project-check{display:flex;width:42rpx;height:42rpx;align-items:center;justify-content:center;border-radius:50%;background:#eef2f6;color:#64798c;font-size:25rpx;font-weight:800}.project-option.selected .project-check{background:#315f86;color:#fff}.project-confirm{flex-shrink:0;min-height:76rpx;margin-top:12rpx;border-radius:14rpx;background:#315f86;color:#fff;font-size:23rpx;font-weight:800}.project-confirm[disabled]{opacity:.45}
.project-search-hint{margin-bottom:11rpx;padding:12rpx 16rpx;border-radius:12rpx;background:#f1f6fb;color:#55748f;font-size:19rpx}
.project-limit-hint{margin-top:14rpx;padding:12rpx 16rpx;border-radius:12rpx;background:#fff8e8;color:#9a5b10;font-size:19rpx;line-height:1.5}.project-option[disabled]{opacity:.48}
</style>
