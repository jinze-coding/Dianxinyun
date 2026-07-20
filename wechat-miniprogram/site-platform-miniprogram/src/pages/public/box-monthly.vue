<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import { getPublicElectricBoxMonthly } from '@/api/electricBox';
import type { PublicElectricBoxMonthly } from '@/types';
import { showToast } from '@/utils/navigation';
import { getToken } from '@/api/request';
import { requestWechatProjectAccess, wechatSession } from '@/api/auth';

const publicCode = ref('');
const month = ref('');
const data = ref<PublicElectricBoxMonthly>();
const loading = ref(true);
const errorMessage = ref('');
const wechatBusy = ref(false);
const accessMessage = ref('');
const monthStart = computed(() => {
  const now = new Date(); now.setMonth(now.getMonth() - 11);
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
});
const monthEnd = computed(() => {
  const now = new Date(); return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
});

onLoad(async (options) => { publicCode.value = String(options?.publicCode || options?.code || ''); await load(); });
async function load() {
  loading.value = true;
  errorMessage.value = '';
  try { data.value = await getPublicElectricBoxMonthly(publicCode.value, month.value || undefined); month.value = data.value.month; }
  catch (error) { data.value = undefined; errorMessage.value = error instanceof Error ? error.message : '月表加载失败'; showToast(errorMessage.value); }
  finally { loading.value = false; }
}
function changeMonth(event: { detail: { value: string } }) { month.value = event.detail.value; load(); }
function tone(value: string) { return value === '异常' ? 'bad' : value === '正常' ? 'ok' : value === '未检' ? 'warn' : 'muted'; }
function goBack(){getCurrentPages().length>1?uni.navigateBack():uni.reLaunch({url:'/pages/login/index'})}

async function internalAccess() {
  if (wechatBusy.value) return;
  wechatBusy.value = true;
  const scene = `B:${publicCode.value}`;
  try {
    if (getToken()) {
      const response = await requestWechatProjectAccess(scene);
      accessMessage.value = response.message;
      showToast(response.message);
      return;
    }
    // #ifdef MP-WEIXIN
    const loginResult = await new Promise<UniApp.LoginRes>((resolve, reject) => uni.login({ provider: 'weixin', success: resolve, fail: reject }));
    const response = await wechatSession(loginResult.code, scene);
    accessMessage.value = response.message;
    if (response.bindingStatus === 'BOUND') {
      uni.redirectTo({ url: `/pages/scan-entry/index?scene=${encodeURIComponent(scene)}` });
    } else if (response.bindingStatus === 'UNBOUND' || response.bindingStatus === 'APPLICATION_REJECTED') {
      uni.navigateTo({ url: `/pages/wechat-bind/index?session=${encodeURIComponent(response.wechatSessionToken || '')}&scene=${encodeURIComponent(scene)}` });
    } else if (response.bindingStatus === 'BOUND_NO_PROJECT_ACCESS') {
      const application = await requestWechatProjectAccess(scene);
      accessMessage.value = application.message;
      showToast(application.message);
    } else {
      showToast(response.message);
    }
    // #endif
    // #ifndef MP-WEIXIN
    showToast('请在微信小程序中申请内部权限');
    // #endif
  } catch (error) {
    accessMessage.value = error instanceof Error ? error.message : '微信身份识别失败';
    showToast(accessMessage.value);
  } finally {
    wechatBusy.value = false;
  }
}
</script>

<template>
  <view class="shell">
    <AppNavBar title="电箱检查记录表" @back="goBack" />
    <view class="content">
      <view v-if="loading" class="panel">正在加载月度检查表...</view>
      <view v-else-if="errorMessage" class="panel error-panel"><text>无法查看电箱检查记录表</text><text>{{ errorMessage }}</text></view>
      <template v-else-if="data">
        <view class="panel header">
          <view class="public-head"><text class="project">{{ data.projectShortName }}</text><text class="readonly">微信扫码公开只读</text></view>
          <text class="document-title">电箱检查记录表</text>
          <view class="box-meta"><text>{{ data.boxCode }} · {{ data.boxName }}</text><text>{{ data.installLocation }}</text></view>
          <picker mode="date" fields="month" :value="month" :start="monthStart" :end="monthEnd" @change="changeMonth">
            <view class="month">{{ month }}　切换月份</view>
          </picker>
          <button class="internal-access" :disabled="wechatBusy" @tap="internalAccess">{{ wechatBusy ? '正在识别微信身份' : '内部人员登录 / 申请巡检权限' }}</button>
          <text v-if="accessMessage" class="access-message">{{ accessMessage }}</text>
        </view>
        <view class="stats">
          <view><text>应检</text><text class="stat-number">{{ data.shouldCheckDays }}</text></view><view><text>已检</text><text class="stat-number ok">{{ data.checkedDays }}</text></view>
          <view><text>未检</text><text class="stat-number warn">{{ data.missedDays }}</text></view><view><text>异常</text><text class="stat-number bad">{{ data.abnormalDays }}</text></view>
        </view>
        <view class="legend"><text><i class="dot ok-dot"></i>正常</text><text><i class="dot bad-dot"></i>异常</text><text><i class="dot warn-dot"></i>未检</text><text><i class="dot muted-dot"></i>非巡检范围</text></view>
        <view class="panel table-panel">
          <scroll-view scroll-x class="table-scroll">
            <view class="table">
              <view class="tr th"><text class="date">日期</text><text>内外观</text><text>漏电保护器</text><text>熔断</text><text>保护接零</text><text>220V插座</text><text>380V插座</text><text class="person">检查人</text><text class="remark">备注</text></view>
              <view v-for="row in data.rows" :key="row.date" class="tr">
                <text class="date">{{ row.date.slice(8) }}日</text>
                <text :class="tone(row.appearance)">{{ row.appearance }}</text><text :class="tone(row.leakageProtector)">{{ row.leakageProtector }}</text>
                <text :class="tone(row.fuse)">{{ row.fuse }}</text><text :class="tone(row.protectiveZero)">{{ row.protectiveZero }}</text>
                <text :class="tone(row.socket220v)">{{ row.socket220v }}</text><text :class="tone(row.socket380v)">{{ row.socket380v }}</text>
                <text class="person">{{ row.inspectorName || '—' }}</text><text class="remark">{{ row.remark }}</text>
              </view>
            </view>
          </scroll-view>
        </view>
        <text class="notice">本页不展示手机号、现场照片、内部ID、复核意见和操作日志，无编辑或导出入口。</text>
      </template>
    </view>
  </view>
</template>

<style scoped>
.shell { min-height: 100vh; background: var(--inspection-page); color: var(--inspection-text); }
.content { display: flex; flex-direction: column; gap: 18rpx; padding: 22rpx 24rpx 42rpx; }
.panel { border: 1rpx solid var(--inspection-divider); border-radius: 22rpx; background: #fff; box-shadow: var(--inspection-shadow); }
.header { padding: 24rpx 26rpx; background: linear-gradient(145deg, #edf5fc, #fff); }
.public-head { display: flex; align-items: center; justify-content: space-between; }
.project { color: var(--inspection-primary-deep); font-size: 25rpx; font-weight: 800; }
.readonly { padding: 6rpx 14rpx; border-radius: 999rpx; background: var(--inspection-success-soft); color: var(--inspection-success); font-size: 20rpx; }
.document-title { display: block; margin: 24rpx 0 18rpx; color: var(--inspection-text); font-family: serif; font-size: 40rpx; font-weight: 900; letter-spacing: 4rpx; text-align: center; }
.box-meta { display: flex; justify-content: space-between; gap: 18rpx; color: #586b80; font-size: 22rpx; }
.box-meta text:first-child { color: var(--inspection-text); font-weight: 800; }
.month { margin-top: 20rpx; padding: 17rpx; border: 1rpx solid var(--inspection-border); border-radius: 14rpx; background: var(--inspection-soft); color: var(--inspection-primary-deep); font-size: 23rpx; font-weight: 700; text-align: center; }
.internal-access { min-height: 70rpx; margin-top: 14rpx; border: 1rpx solid var(--inspection-border); border-radius: 14rpx; background: #fff; color: var(--inspection-primary-deep); font-size: 22rpx; font-weight: 750; }
.internal-access::after { border: 0; }
.internal-access[disabled] { opacity: .65; }
.access-message { display: block; margin-top: 10rpx; color: var(--inspection-muted); font-size: 20rpx; line-height: 1.55; text-align: center; }
.stats { display: grid; grid-template-columns: repeat(4,1fr); gap: 10rpx; }
.stats view { padding: 17rpx 6rpx; border: 1rpx solid var(--inspection-divider); border-radius: 16rpx; background: #fff; text-align: center; }
.stats text { display: block; color: var(--inspection-muted); font-size: 21rpx; }
.stats .stat-number { margin-top: 5rpx; color: var(--inspection-text); font-size: 31rpx; font-weight: 900; }
.stats .stat-number.ok { color: var(--inspection-success); }
.stats .stat-number.warn { color: var(--inspection-warning); }
.stats .stat-number.bad { color: var(--inspection-danger); }
.legend { display: flex; gap: 22rpx; flex-wrap: wrap; padding: 0 8rpx; color: #687b90; font-size: 20rpx; }
.legend text { display: flex; align-items: center; gap: 7rpx; }
.dot { width: 12rpx; height: 12rpx; border-radius: 50%; }
.ok-dot { background: #63a88d; }
.bad-dot { background: #c97070; }
.warn-dot { background: #c69a5f; }
.muted-dot { background: #aeb9c5; }
.table-panel { overflow: hidden; padding: 0; }
.table-scroll { width: 100%; }
.table { width: 1950rpx; }
.tr { display: grid; grid-template-columns: 100rpx repeat(6,180rpx) 160rpx 510rpx; min-height: 76rpx; border-bottom: 1rpx solid var(--inspection-divider); background: #fff; }
.tr:nth-child(even) { background: #f9fbfd; }
.tr text { display: flex; align-items: center; justify-content: center; padding: 12rpx; border-right: 1rpx solid var(--inspection-divider); font-size: 22rpx; text-align: center; }
.th { position: sticky; z-index: 3; top: 0; background: #e9f2f9 !important; color: #344c63; font-weight: 800; }
.date { position: sticky; z-index: 2; left: 0; background: #f1f6fa; font-weight: 800; }
.th .date { z-index: 4; background: #dceaf5; }
.remark { justify-content: flex-start !important; text-align: left !important; }
.ok { color: var(--inspection-success); }
.bad { color: var(--inspection-danger) !important; font-weight: 800; }
.warn { color: var(--inspection-warning) !important; }
.muted { color: #95a2b0; }
.notice { padding: 4rpx 8rpx 28rpx; color: var(--inspection-muted); font-size: 21rpx; line-height: 1.65; }
.error-panel { display: flex; min-height: 300rpx; align-items: center; justify-content: center; flex-direction: column; padding: 40rpx; text-align: center; }
.error-panel text:first-child { color: var(--inspection-text); font-size: 28rpx; font-weight: 800; }
.error-panel text:last-child { margin-top: 12rpx; color: var(--inspection-muted); font-size: 21rpx; line-height: 1.6; }
</style>
