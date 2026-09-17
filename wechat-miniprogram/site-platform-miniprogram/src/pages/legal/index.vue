<script setup lang="ts">
import AppNavBar from '@/components/AppNavBar.vue';
import { openPrivacyPolicy } from '@/utils/agreementConsent';
function goBack() { getCurrentPages().length > 1 ? uni.navigateBack() : uni.reLaunch({ url: '/pages/login/index' }); }
const sections = [
  { title: '一、服务范围', content: '智慧营造助手为项目现场管理提供场内来访、工程资料、用印、图纸收发、电箱与临边巡检、质量周检、安委会巡检及项目协作服务。可用功能由当前项目配置和账号权限决定，访客可通过有效邀请使用相应登记、签到及公开资料功能。' },
  { title: '二、您的自主选择', content: '请在登录、注册或绑定账号前阅读本协议及《隐私政策》，自行决定是否勾选同意。选项默认不勾选，打开或返回协议页面不会视为同意。您可以取消勾选或离开页面；未同意时不提交本次账号登录、注册或绑定信息。相机、麦克风、相册、手机号及定位等授权另由您在使用相关功能时决定。' },
  { title: '三、账号申请与使用', content: '自主注册由管理员审核并分配项目及角色；管理员也可按授权导入账号。请使用真实、准确的申请信息，妥善保管密码，不向他人提供登录凭据。导入账号首次登录须设置个人密码。微信绑定须本人主动操作并验证账号，不因手机号相同而自动绑定。若发现账号被冒用，请及时联系项目管理员处理。' },
  { title: '四、项目资料与业务记录', content: '请在授权范围内使用项目资料，确保您提交的信息、照片、视频和文件真实且有权提供，不得上传与项目无关或侵犯他人权益的内容，不得冒用他人身份、伪造业务记录或尝试越权访问。业务修改、审批、整改及必要操作会按对应流程留痕；账号退出、停用或移出项目不等于删除已形成的业务档案。' },
  { title: '五、隐私与权限', content: '个人信息的处理目的、种类、使用范围及运营者联系方式，以本小程序在微信平台公布的《隐私保护指引》为准，可通过本页下方《隐私政策》入口查看。勾选本协议不代替微信的隐私授权或设备权限授权。您可在微信设置中管理相关权限；拒绝某项权限时，仅按具体业务情况限制依赖该权限的操作。' },
  { title: '六、帮助与反馈', content: '账号、项目权限、资料纠错或停用申请，请联系所属项目管理员；个人信息相关问题可按微信平台《隐私保护指引》中的联系方式向运营者提出。页面操作提示及系统权限控制不代替项目现场管理职责。服务内容或个人信息处理规则发生调整时，请以届时展示的说明和隐私指引为准。' }
];
</script>

<template>
  <view class="legal-page">
    <AppNavBar title="用户服务协议" @back="goBack" />
    <view class="legal-content">
      <text class="legal-title">智慧营造助手用户服务协议</text>
      <text class="legal-date">更新日期：2026年9月15日</text>
      <view v-for="section in sections" :key="section.title" class="legal-section"><text class="legal-heading">{{ section.title }}</text><text>{{ section.content }}</text></view>
      <button class="legal-privacy" @tap="openPrivacyPolicy">查看《隐私政策》</button>
      <button class="legal-back" @tap="goBack">返回继续选择</button>
      <text class="legal-note">返回不会自动勾选或表示同意。</text>
    </view>
  </view>
</template>

<style scoped>
.legal-page{min-height:100vh;background:#f4f7fa;color:#223247}.legal-content{padding:30rpx 30rpx calc(50rpx + env(safe-area-inset-bottom))}.legal-title,.legal-date,.legal-heading,.legal-note{display:block}.legal-title{font-size:32rpx;font-weight:800}.legal-date{margin:14rpx 0 24rpx;font-size:22rpx;color:#7f8c9c}.legal-section{padding:24rpx;margin-bottom:20rpx;border-radius:16rpx;background:#fff;font-size:25rpx;line-height:1.9;overflow-wrap:anywhere}.legal-heading{margin-bottom:12rpx;font-size:27rpx;font-weight:700}.legal-privacy,.legal-back{margin:20rpx 0 0;min-height:80rpx;border-radius:14rpx;font-size:25rpx}.legal-privacy{border:1rpx solid #b9d1e3;background:#fff;color:#315f86}.legal-back{background:#315f86;color:#fff}.legal-note{margin-top:18rpx;text-align:center;color:#7f8c9c;font-size:22rpx}
</style>
