<script setup lang="ts">
import { computed, ref } from 'vue';

const props = defineProps<{
  enabledPointCount?: number;
}>();

const open = ref(false);
const pointCount = computed(() => Number.isFinite(props.enabledPointCount)
  ? Number(props.enabledPointCount)
  : undefined);
</script>

<template>
  <button class="edge-flow" @tap="open = true">
    <view class="flow-steps">
      <text class="flow-step-name">点位</text><text class="flow-step-arrow">›</text>
      <text class="flow-step-name">任务</text><text class="flow-step-arrow">›</text>
      <text class="flow-step-name">巡检</text><text class="flow-step-arrow">›</text>
      <text class="flow-step-name">整改</text><text class="flow-step-arrow">›</text>
      <text class="flow-step-name">复查</text>
    </view>
    <text class="flow-help">流程说明</text>
  </button>

  <view v-if="open" class="flow-overlay" @tap="open = false">
    <view class="flow-sheet" @tap.stop>
      <view class="sheet-handle"></view>
      <view class="sheet-head">
        <view class="sheet-head-copy"><text class="sheet-title">临边巡检怎么运转</text><text class="sheet-subtitle">点位不等于任务：点位是现场位置，任务是某一天需要完成的一次巡检</text></view>
        <button class="sheet-close" @tap="open = false">×</button>
      </view>

      <view class="equation">
        <text class="equation-part">{{ pointCount ?? '项目' }} 个启用点位</text><text class="equation-part">× 每个计划日期 1 次</text><text class="equation-result">= 每日 {{ pointCount ?? '对应' }} 项任务</text>
      </view>
      <view class="sheet-step"><text class="sheet-step-index">1</text><view class="sheet-step-copy"><text class="sheet-step-title">Web 设置点位和周期</text><text class="sheet-step-description">管理员登记点位，并统一设置时段、巡检人、整改人和复查人。</text></view></view>
      <view class="sheet-step"><text class="sheet-step-index">2</text><view class="sheet-step-copy"><text class="sheet-step-title">系统自动生成任务</text><text class="sheet-step-description">一个启用点位在一个计划日期生成一项任务，未来任务不会提前进入个人待办。</text></view></view>
      <view class="sheet-step"><text class="sheet-step-index">3</text><view class="sheet-step-copy"><text class="sheet-step-title">指定人员在小程序巡检</text><text class="sheet-step-description">上传现场全景照片，逐项选择正常或异常；逾期后仍可补检并保留迟交标记。</text></view></view>
      <view class="sheet-step"><text class="sheet-step-index">4</text><view class="sheet-step-copy"><text class="sheet-step-title">异常整改并复查闭环</text><text class="sheet-step-description">同次巡检的异常合成一张整改单，整改人反馈后由复查人统一关闭或退回。</text></view></view>
      <button class="sheet-confirm" @tap="open = false">我知道了</button>
    </view>
  </view>
</template>

<style scoped>
.edge-flow{box-sizing:border-box;display:flex;width:100%;min-height:62rpx;align-items:center;justify-content:space-between;gap:12rpx;margin:16rpx 0 0;padding:10rpx 15rpx;border:1rpx solid #ead8c0;border-radius:14rpx;background:#fffaf3;color:#7f5727;text-align:left}.edge-flow::after,.sheet-close::after,.sheet-confirm::after{border:0}.flow-steps{display:flex;min-width:0;align-items:center;gap:7rpx;flex:1}.flow-step-name{font-size:18rpx;font-weight:800;white-space:nowrap}.flow-step-arrow{color:#c79a64;font-size:22rpx}.flow-help{flex-shrink:0;color:#956126;font-size:18rpx;font-weight:750}.flow-overlay{position:fixed;z-index:1200;inset:0;display:flex;align-items:flex-end;background:rgba(29,41,57,.4)}.flow-sheet{box-sizing:border-box;width:100%;max-height:88vh;padding:14rpx 26rpx calc(28rpx + env(safe-area-inset-bottom));overflow-y:auto;border-radius:26rpx 26rpx 0 0;background:#fff;box-shadow:0 -20rpx 50rpx rgba(29,41,57,.16)}.sheet-handle{width:64rpx;height:7rpx;margin:0 auto 19rpx;border-radius:999rpx;background:#d6dce4}.sheet-head{display:flex;align-items:flex-start;justify-content:space-between;gap:18rpx}.sheet-head-copy{min-width:0;flex:1}.sheet-title,.sheet-subtitle{display:block}.sheet-title{color:#26384a;font-size:30rpx;font-weight:850}.sheet-subtitle{margin-top:7rpx;color:#7c8997;font-size:20rpx;line-height:1.5}.sheet-close{display:flex;width:52rpx;height:52rpx;align-items:center;justify-content:center;flex-shrink:0;margin:0;border-radius:50%;background:#f2f4f7;color:#647184;font-size:32rpx}.equation{display:flex;align-items:center;justify-content:center;flex-wrap:wrap;gap:8rpx;margin:22rpx 0;padding:18rpx;border-radius:14rpx;background:#fff5e8;color:#80531f;font-size:20rpx;font-weight:750}.equation-result{color:#9b621e}.sheet-step{display:flex;align-items:flex-start;gap:15rpx;padding:15rpx 2rpx;border-bottom:1rpx solid #edf0f3}.sheet-step-index{display:flex;width:42rpx;height:42rpx;align-items:center;justify-content:center;flex-shrink:0;border-radius:50%;background:#fff1df;color:#956126;font-size:20rpx;font-weight:850}.sheet-step-copy{min-width:0;flex:1}.sheet-step-title,.sheet-step-description{display:block}.sheet-step-title{color:#34495e;font-size:22rpx;font-weight:700}.sheet-step-description{margin-top:5rpx;color:#788795;font-size:19rpx;line-height:1.55}.sheet-confirm{height:70rpx;margin-top:22rpx;border-radius:14rpx;background:#966421;color:#fff;font-size:23rpx;font-weight:800;line-height:70rpx}
</style>
