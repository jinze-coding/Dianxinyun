<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import { getInspectionRecordDetail } from '@/api/inspection';
import type { InspectionRecord } from '@/types';
import { usePageScrollHeight } from '@/utils/navLayout';
import { getQueryNumber, showToast, switchTab } from '@/utils/navigation';

const record = ref<InspectionRecord>();
const loading = ref(true);
const { scrollStyle } = usePageScrollHeight({ minHeight: 260 });
const photos = computed(() => [...(record.value?.outerPhotos || []), ...(record.value?.innerPhotos || [])]);

onShow(async () => {
  const pages = getCurrentPages();
  const current = pages[pages.length - 1] as unknown as { options?: Record<string, string> };
  loading.value = true;
  try {
    record.value = await getInspectionRecordDetail(getQueryNumber(current.options?.id, 0));
  } catch (error) {
    record.value = undefined;
    showToast(error instanceof Error ? error.message : '巡检详情加载失败');
  } finally {
    loading.value = false;
  }
});

function goBack() { getCurrentPages().length > 1 ? uni.navigateBack() : switchTab('/pages/inspection/index'); }
function resultLabel(value: string) { return value === 'NORMAL' ? '正常' : value === 'ABNORMAL' ? '异常' : value === 'NA' ? '不适用' : '未填写'; }
function preview(index: number) { if (photos.value.length) uni.previewImage({ current: index, urls: photos.value }); }
</script>

<template>
  <view class="flow-page detail-page">
    <AppNavBar title="巡检详情" @back="goBack" />
    <scroll-view class="flow-scroll" scroll-y enable-flex :style="scrollStyle">
      <view v-if="loading" class="flow-content"><view class="flow-card loading-card"><view class="flow-skeleton line"></view><view class="flow-skeleton block"></view></view></view>
      <view v-else-if="record" class="flow-content detail-content">
        <view class="record-hero flow-card">
          <view><text class="box-code">{{ record.boxCode }}</text><text class="status-pill">已完成</text></view>
          <text class="box-name">{{ record.boxName || '现场电箱' }}</text>
          <text class="record-meta">{{ record.installLocation || '未记录位置' }}</text>
          <view class="hero-footer"><text>{{ record.checkDate }}</text><text>{{ record.inspectorName || '—' }}</text></view>
        </view>

        <view class="result-card flow-card">
          <view class="section-head"><text>六项检查结果</text><text>异常 {{ record.abnormalCount || 0 }} 项</text></view>
          <view v-for="item in record.items" :key="item.itemCode" class="result-row">
            <text>{{ item.itemName }}</text>
            <text :class="{ danger: item.result === 'ABNORMAL', muted: item.result === 'NA' }">{{ resultLabel(item.result) }}</text>
          </view>
        </view>

        <view class="photo-card flow-card">
          <view class="section-head"><text>现场照片</text><text>{{ photos.length }} 张</text></view>
          <view v-if="photos.length" class="photo-grid"><image v-for="(photo,index) in photos" :key="photo" :src="photo" mode="aspectFill" @tap="preview(index)" /></view>
          <text v-else class="empty-text">本次巡检未上传照片</text>
        </view>

        <view class="remark-card flow-card"><view class="section-head"><text>备注</text><text>选填</text></view><text class="remark-text">{{ record.remark || '无备注' }}</text></view>
      </view>
    </scroll-view>
  </view>
</template>

<style scoped src="../../styles/safety-flow.css"></style>
<style scoped>
.detail-content { display: flex; flex-direction: column; gap: 15rpx; padding-top: 16rpx; padding-bottom: 36rpx; }
.record-hero,.result-card,.photo-card,.remark-card { padding: 22rpx; }
.record-hero { background: linear-gradient(135deg,#eaf3fb,#fff); }
.record-hero>view:first-child { display: flex; align-items: center; justify-content: space-between; }
.box-code { color: #253247; font-size: 30rpx; font-weight: 900; }
.status-pill { padding: 5rpx 12rpx; border-radius: 999rpx; background: var(--inspection-success-soft); color: var(--inspection-success); font-size: 18rpx; font-weight: 750; }
.box-name,.record-meta { display: block; }
.box-name { margin-top: 7rpx; color: #4C596B; font-size: 22rpx; font-weight: 700; }
.record-meta { margin-top: 5rpx; color: #8B95A2; font-size: 19rpx; }
.hero-footer { display: flex; justify-content: space-between; margin-top: 17rpx; padding-top: 15rpx; border-top: 1rpx solid var(--inspection-divider); color: #66778a; font-size: 20rpx; }
.section-head { display: flex; align-items: center; justify-content: space-between; padding-bottom: 14rpx; }
.section-head text:first-child { color: #253247; font-size: 25rpx; font-weight: 820; }
.section-head text:last-child { color: #98A2B3; font-size: 18rpx; }
.result-row { display: flex; min-height: 68rpx; align-items: center; justify-content: space-between; border-top: 1rpx solid var(--inspection-divider); }
.result-row text:first-child { color: #445065; font-size: 21rpx; }
.result-row text:last-child { padding: 5rpx 11rpx; border-radius: 999rpx; background: var(--inspection-success-soft); color: var(--inspection-success); font-size: 18rpx; font-weight: 700; }
.result-row text:last-child.danger { background: var(--inspection-danger-soft); color: var(--inspection-danger); }
.result-row text:last-child.muted { background: #eef2f5; color: #738092; }
.photo-grid { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 10rpx; }
.photo-grid image { width: 100%; height: 150rpx; border-radius: 12rpx; background: #f0f5f9; }
.empty-text,.remark-text { display: block; padding: 18rpx; border: 1rpx solid var(--inspection-divider); border-radius: 12rpx; background: #f7fafc; color: #748398; font-size: 20rpx; line-height: 1.6; }
.loading-card { padding: 24rpx; }.flow-skeleton.line { width: 42%; height: 30rpx; }.flow-skeleton.block { width: 100%; height: 260rpx; margin-top: 20rpx; }
</style>
