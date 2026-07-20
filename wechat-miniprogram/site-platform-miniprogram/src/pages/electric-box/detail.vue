<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import { getElectricBoxDetail } from '@/api/electricBox';
import type { ElectricBox } from '@/types';
import { usePageScrollHeight } from '@/utils/navLayout';
import { getQueryNumber, navigateTo, showToast, switchTab } from '@/utils/navigation';

const box = ref<ElectricBox>();
const loading = ref(true);
const { scrollStyle } = usePageScrollHeight({ minHeight: 240 });

const statusMeta = computed(() => {
  if (!box.value) return { label: '', tone: 'muted' };
  if (box.value.status === 'REMOVED') return { label: '已拆除', tone: 'muted' };
  if (box.value.status === 'INACTIVE') return { label: '停用', tone: 'muted' };
  if (box.value.todayStatus === 'ABNORMAL') return { label: '异常', tone: 'danger' };
  if (box.value.todayStatus === 'UNCHECKED') return { label: '未检', tone: 'warning' };
  return { label: '已检', tone: 'success' };
});

const qrText = computed(() => {
  const map: Record<ElectricBox['qrStatus'], string> = { BOUND: '已绑定', UNBOUND: '未绑定', DISABLED: '已停用', REPLACED: '已换码' };
  return box.value ? map[box.value.qrStatus] : '-';
});
const scopeText = computed(() => box.value?.inspectionRequired === false ? '未纳入日检' : '纳入日检');
const canInspect = computed(() => box.value?.status === 'ACTIVE' && box.value?.inspectionRequired !== false);
const unifiedCode = computed(() => box.value?.publicCode || box.value?.boxCode || '-');

onShow(async () => {
  const pages = getCurrentPages();
  const current = pages[pages.length - 1] as unknown as { options?: Record<string, string> };
  loading.value = true;
  try { box.value = await getElectricBoxDetail(getQueryNumber(current.options?.id, 101)); }
  catch (error) { box.value = undefined; showToast(error instanceof Error ? error.message : '电箱详情加载失败'); }
  finally { loading.value = false; }
});

function goBack() { getCurrentPages().length > 1 ? uni.navigateBack() : switchTab('/pages/inspection/index'); }
function openInspection() {
  if (!box.value) return;
  if (!canInspect.value) { showToast(box.value.status === 'REMOVED' ? '已拆除电箱不可巡检' : box.value.status === 'INACTIVE' ? '停用电箱不可巡检' : '当前电箱未纳入日检'); return; }
  navigateTo(`/pages/inspection/form?boxId=${box.value.id}`);
}
function openSummary() { if (box.value) navigateTo(`/pages/summary/index?boxId=${box.value.id}&projectId=${box.value.projectId}`); }
function openRecords() { if (box.value) navigateTo(`/pages/inspection/records?projectId=${box.value.projectId}&boxId=${box.value.id}`); }
function openPublicMonthly() { if (box.value) navigateTo(`/pages/public/box-monthly?publicCode=${encodeURIComponent(box.value.publicCode || box.value.boxCode)}`); }
</script>

<template>
  <view class="flow-page detail-page">
    <AppNavBar title="电箱详情" @back="goBack" />
    <scroll-view class="flow-scroll" scroll-y enable-flex :style="scrollStyle">
      <view v-if="loading" class="flow-content skeleton-stack">
        <view class="flow-card skeleton-card"><view class="flow-skeleton code-line"></view><view class="flow-skeleton name-line"></view><view class="flow-skeleton meta-line"></view></view>
        <view class="flow-card skeleton-card qr-skeleton"><view class="flow-skeleton qr-block"></view></view>
      </view>

      <view v-else-if="box" class="flow-content detail-content">
        <view class="hero-card flow-card">
          <view class="hero-main"><view class="box-mark">电</view><view class="hero-copy"><view><text class="box-code">{{ box.boxCode }}</text><text class="status-pill" :class="statusMeta.tone">{{ statusMeta.label }}</text></view><text class="box-name">{{ box.boxName }}</text><text class="location">位置：{{ box.installLocation }}</text></view></view>
          <view class="metric-strip"><view><text>{{ statusMeta.label }}</text><text>今日状态</text></view><view><text>{{ scopeText }}</text><text>巡检范围</text></view></view>
        </view>

        <view class="info-card flow-card">
          <view class="section-head"><view><text>负责信息</text><text>项目内人员与设备绑定</text></view></view>
          <view class="info-grid"><view><text>负责巡检员</text><text>{{ box.responsibleElectricianName || '未配置' }}</text></view><view><text>电箱类型</text><text>{{ box.boxType || '现场电箱' }}</text></view><view><text>二维码状态</text><text>{{ qrText }}</text></view><view><text>公开月表</text><text>{{ box.publicAccessEnabled === false ? '已关闭' : '已开启' }}</text></view></view>
        </view>

        <view class="qr-card flow-card">
          <view class="section-head"><view><text>统一巡检码</text><text>现场贴纸由 Web 后台生成</text></view><text class="bound-pill">{{ qrText }}</text></view>
          <button class="unified-code-row pressable" @tap="openPublicMonthly"><view class="code-mark"><text>码</text><text></text></view><view class="unified-copy"><text>一码两用已配置</text><text>场景码：B:{{ unifiedCode }}</text><text>微信扫描现场贴纸后，系统按身份显示巡检或公示数据</text></view><text class="record-arrow"></text></button>
          <button class="public-link pressable" @tap="openPublicMonthly">预览外部月度公示</button>
        </view>

        <button class="latest-card flow-card pressable" @tap="openRecords"><view class="section-head"><view><text>最近巡检</text><text>查看本箱全部检查记录</text></view><text class="record-arrow"></text></view><view class="latest-row"><view class="calendar-mark"><text>{{ (box.lastCheckDate || '--').slice(-2) }}</text><text>日</text></view><view><text>{{ box.lastCheckDate || '暂无巡检记录' }}</text><text>{{ box.lastCheckDate ? `${box.responsibleElectricianName || '负责电工'} · ${statusMeta.label}` : '完成日检后将在这里显示' }}</text></view></view></button>
        <view class="action-row flow-card"><button class="summary-button pressable" @tap="openSummary">本箱汇总</button><button class="inspection-button pressable" :class="{ disabled: !canInspect }" :disabled="!canInspect" @tap="openInspection">{{ box.todayStatus === 'CHECKED' ? '今日记录' : '开始巡检' }}</button></view>
      </view>
    </scroll-view>
  </view>
</template>

<style scoped src="../../styles/safety-flow.css"></style>
<style scoped>
.detail-content, .skeleton-stack { display: flex; flex-direction: column; gap: 15rpx; padding-top: 16rpx; padding-bottom: 34rpx; }
.hero-card { overflow: hidden; }.hero-main { display: flex; align-items: center; gap: 16rpx; padding: 23rpx; background: linear-gradient(135deg, #fff8ef, #fff); }.box-mark { display: flex; width: 62rpx; height: 62rpx; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 16rpx; background: #ad6723; color: #fff; font-size: 27rpx; font-weight: 900; }.hero-copy { min-width: 0; flex: 1; }.hero-copy>view { display: flex; align-items: center; justify-content: space-between; gap: 14rpx; }.box-code { font-size: 29rpx; font-weight: 900; }.status-pill { padding: 5rpx 12rpx; border-radius: 999rpx; font-size: 18rpx; font-weight: 750; }.status-pill.success { background: #e4f5eb; color: #168653; }.status-pill.warning { background: #fff0df; color: #b66a20; }.status-pill.danger { background: #fde9e6; color: #bc4d45; }.status-pill.muted { background: #f0efec; color: #7e7972; }.box-name, .location { display: block; }.box-name { margin-top: 4rpx; color: #4c596b; font-size: 21rpx; font-weight: 700; }.location { margin-top: 6rpx; overflow: hidden; color: #858f9c; font-size: 19rpx; text-overflow: ellipsis; white-space: nowrap; }
.metric-strip { display: grid; grid-template-columns: repeat(2, 1fr); padding: 16rpx 8rpx; border-top: 1rpx solid #f0ebe4; }.metric-strip view { position: relative; text-align: center; }.metric-strip view+view::before { position: absolute; top: 4rpx; bottom: 4rpx; left: 0; width: 1rpx; background: #eee9e2; content: ''; }.metric-strip text { display: block; }.metric-strip text:first-child { overflow: hidden; color: #344154; font-size: 20rpx; font-weight: 800; text-overflow: ellipsis; white-space: nowrap; }.metric-strip text:first-child.danger { color: #bc4d45; }.metric-strip text:last-child { margin-top: 4rpx; color: #969da6; font-size: 17rpx; }
.info-card, .qr-card { padding: 21rpx; }.section-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 15rpx; }.section-head view text { display: block; }.section-head view text:first-child { font-size: 25rpx; font-weight: 850; }.section-head view text:last-child { margin-top: 4rpx; color: #969da6; font-size: 18rpx; }.info-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0; margin-top: 17rpx; border: 1rpx solid #efeae3; border-radius: 15rpx; background: #faf9f7; }.info-grid view { min-width: 0; padding: 16rpx; }.info-grid view:nth-child(even) { border-left: 1rpx solid #ece7e0; }.info-grid view:nth-child(n+3) { border-top: 1rpx solid #ece7e0; }.info-grid text { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.info-grid text:first-child { color: #989fa8; font-size: 17rpx; }.info-grid text:last-child { margin-top: 5rpx; color: #344154; font-size: 21rpx; font-weight: 750; }
.bound-pill { padding: 5rpx 11rpx; border-radius: 999rpx; background: #e4f5eb; color: #168653; font-size: 17rpx; font-weight: 750; }.unified-code-row { display: grid; grid-template-columns: 60rpx 1fr 12rpx; align-items: center; gap: 14rpx; width: 100%; margin: 17rpx 0 0; padding: 17rpx; border-radius: 15rpx; background: #faf9f7; text-align: left; }.unified-code-row::after, .public-link::after { border: 0; }.code-mark { position: relative; display: flex; width: 54rpx; height: 54rpx; align-items: center; justify-content: center; border: 1rpx solid #e4c6a5; border-radius: 14rpx; background: #fff0df; color: #a76224; font-size: 20rpx; font-weight: 900; }.code-mark::before, .code-mark::after, .code-mark text:last-child { position: absolute; width: 10rpx; height: 10rpx; border-color: #b66f2d; content: ''; }.code-mark::before { top: 7rpx; left: 7rpx; border-top: 2rpx solid; border-left: 2rpx solid; }.code-mark::after { right: 7rpx; bottom: 7rpx; border-right: 2rpx solid; border-bottom: 2rpx solid; }.unified-copy { min-width: 0; }.unified-copy text { display: block; }.unified-copy text:first-child { color: #344154; font-size: 21rpx; font-weight: 800; }.unified-copy text:nth-child(2) { margin-top: 3rpx; color: #a76224; font-size: 18rpx; }.unified-copy text:last-child { margin-top: 6rpx; color: #9299a2; font-size: 17rpx; line-height: 1.45; }.public-link { display: flex; height: 58rpx; min-height: 58rpx; align-items: center; justify-content: center; margin: 10rpx 0 0; padding: 0 16rpx; border-radius: 12rpx; background: #fff7ed; color: #a76224; font-size: 19rpx; font-weight: 750; line-height: 1; text-align: center; }
.latest-card { width: 100%; margin: 0; padding: 21rpx; text-align: left; }.latest-card::after { border: 0; }.record-arrow { width: 10rpx; height: 10rpx; margin: 10rpx 4rpx 0 0; border-top: 2rpx solid #a39c93; border-right: 2rpx solid #a39c93; transform: rotate(45deg); }.latest-row { display: flex; align-items: center; gap: 14rpx; margin-top: 17rpx; padding: 15rpx; border-radius: 14rpx; background: #faf9f7; }.calendar-mark { display: flex; width: 49rpx; height: 49rpx; align-items: center; justify-content: center; flex-direction: column; flex-shrink: 0; border-radius: 13rpx; background: #fff0df; color: #a76224; }.calendar-mark text:first-child { font-size: 20rpx; font-weight: 900; }.calendar-mark text:last-child { margin-top: -2rpx; font-size: 14rpx; }.latest-row>view:last-child text { display: block; }.latest-row>view:last-child text:first-child { color: #384558; font-size: 21rpx; font-weight: 800; }.latest-row>view:last-child text:last-child { margin-top: 4rpx; color: #9299a2; font-size: 18rpx; }
.action-row { display: grid; grid-template-columns: .9fr 1.25fr; gap: 12rpx; padding: 12rpx; }.action-row button { display: flex; height: 66rpx; min-height: 66rpx; align-items: center; justify-content: center; margin: 0; padding: 0 16rpx; border-radius: 13rpx; font-size: 21rpx; font-weight: 780; line-height: 1; text-align: center; }.action-row button::after { border: 0; }.summary-button { border: 1rpx solid #dfcfbe; background: #fffaf4; color: #9b5d25; }.inspection-button { background: #a96527; color: #fff; }.inspection-button.disabled { background: #c9c2ba; color: #fff; opacity: .72; }
.skeleton-card { padding: 23rpx; }.flow-skeleton.code-line { width: 38%; height: 30rpx; }.flow-skeleton.name-line { width: 58%; height: 20rpx; margin-top: 14rpx; }.flow-skeleton.meta-line { width: 100%; height: 72rpx; margin-top: 26rpx; }.qr-skeleton { display: flex; justify-content: center; }.flow-skeleton.qr-block { width: 220rpx; height: 220rpx; }
</style>
