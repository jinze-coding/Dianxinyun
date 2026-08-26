<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import { getPublicGeneralInspectionMonthly, type PublicGeneralInspectionMonthly } from '@/api/generalInspection';

const publicCode = ref('');
const month = ref(currentMonth());
const data = ref<PublicGeneralInspectionMonthly>();
const loading = ref(false);
const errorMessage = ref('');

onLoad((options) => {
  publicCode.value = String(options?.publicCode || options?.code || '').replace(/^P:/i, '');
  if (String(options?.month || '').match(/^\d{4}-\d{2}$/)) month.value = String(options?.month);
  void load();
});

const summary = computed(() => data.value ? [
  { label: '应检', value: data.value.shouldCheckCount },
  { label: '已检', value: data.value.checkedCount },
  { label: '未检', value: data.value.missedCount },
  { label: '异常', value: data.value.abnormalCount }
] : []);

function currentMonth() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

async function load() {
  if (!publicCode.value) { errorMessage.value = '巡检点位码无效'; return; }
  loading.value = true;
  errorMessage.value = '';
  try { data.value = await getPublicGeneralInspectionMonthly(publicCode.value, month.value); }
  catch (error) { data.value = undefined; errorMessage.value = error instanceof Error ? error.message : '月度巡检记录加载失败'; }
  finally { loading.value = false; }
}

function resultLabel(value: string) {
  if (value === 'NORMAL' || value === '正常') return '正常';
  if (value === 'ABNORMAL' || value === '异常') return '异常';
  if (value === 'NA' || value === '不适用') return '不适用';
  return '-';
}

function statusLabel(value: string) {
  if (value === 'PENDING') return '未检';
  if (value === 'COMPLETED') return '已检';
  if (value === 'RECTIFICATION_PENDING') return '整改中';
  if (value === 'CLOSED') return '已闭环';
  return value;
}

function leave() {
  if (getCurrentPages().length > 1) uni.navigateBack();
  else uni.reLaunch({ url: '/pages/login/index' });
}
</script>

<template>
  <view class="public-shell">
    <AppNavBar title="点位月度巡检" @back="leave" />
    <scroll-view class="page-scroll" scroll-y>
      <view class="content">
        <view v-if="loading" class="state">正在加载公开月表…</view>
        <view v-else-if="errorMessage" class="state error">{{ errorMessage }}</view>
        <template v-else-if="data">
          <view class="hero">
            <text class="project">{{ data.projectShortName }}</text>
            <text class="point">{{ data.pointCode }} · {{ data.pointName }}</text>
            <text class="location">{{ data.locationDesc || '未填写位置说明' }}</text>
            <picker mode="date" fields="month" :value="month" @change="month = String($event.detail.value); load()"><view class="month-picker">{{ month }} 月表 ›</view></picker>
          </view>
          <view class="metrics">
            <view v-for="item in summary" :key="item.label"><text>{{ item.value }}</text><text>{{ item.label }}</text></view>
          </view>
          <view v-for="section in data.sections" :key="section.versionLabel" class="section-card">
            <view class="section-head"><text>{{ section.templateName }}</text><text>{{ section.versionLabel }}</text></view>
            <scroll-view class="table-scroll" scroll-x>
              <view class="table" :style="{ width: `${560 + section.itemNames.length * 180}rpx` }">
                <view class="tr th">
                  <text class="date">日期/时段</text><text class="status">状态</text><text class="person">检查人</text>
                  <text v-for="name in section.itemNames" :key="name" class="result">{{ name }}</text><text class="remark">公开备注</text>
                </view>
                <view v-for="row in section.rows" :key="`${row.date}-${row.slotName}`" class="tr">
                  <text class="date">{{ row.date }}\n{{ row.slotName }}</text><text class="status">{{ statusLabel(row.status) }}</text><text class="person">{{ row.inspectorName || '-' }}</text>
                  <text v-for="(value,index) in row.results" :key="index" class="result" :class="{ abnormal: value === 'ABNORMAL' || value === '异常' }">{{ resultLabel(value) }}</text><text class="remark">{{ row.publicRemark || '-' }}</text>
                </view>
              </view>
            </scroll-view>
          </view>
          <view v-if="!data.sections.length" class="state">本月暂无公开巡检记录</view>
          <text class="privacy">公开页面不展示照片、异常详述、整改人员、内部编号和操作日志。</text>
        </template>
      </view>
    </scroll-view>
  </view>
</template>

<style scoped>
.public-shell{min-height:100vh;background:#f4f7fa;color:#26384a}.page-scroll{height:calc(100vh - 120rpx)}.content{padding:24rpx 24rpx 50rpx}.hero,.section-card{margin-bottom:18rpx;padding:24rpx;border:1rpx solid #e2e9ee;border-radius:18rpx;background:#fff}.project,.point,.location{display:block}.project{color:#315f86;font-size:21rpx}.point{margin-top:8rpx;font-size:28rpx;font-weight:780}.location{margin-top:7rpx;color:#7b8996;font-size:20rpx}.month-picker{margin-top:17rpx;padding:14rpx;border-radius:12rpx;background:#eef5fa;color:#315f86;font-size:21rpx}.metrics{display:grid;grid-template-columns:repeat(4,1fr);gap:10rpx;margin-bottom:18rpx}.metrics view{display:flex;align-items:center;flex-direction:column;padding:17rpx 8rpx;border-radius:14rpx;background:#fff}.metrics text:first-child{font-size:28rpx;font-weight:780}.metrics text:last-child{margin-top:4rpx;color:#8a96a2;font-size:18rpx}.section-head{display:flex;align-items:center;justify-content:space-between;margin-bottom:16rpx}.section-head text:first-child{font-size:24rpx;font-weight:750}.section-head text:last-child{color:#8b97a3;font-size:19rpx}.table-scroll{width:100%}.table{border-top:1rpx solid #e2e8ed;border-left:1rpx solid #e2e8ed}.tr{display:flex;min-height:78rpx}.tr text{display:flex;align-items:center;justify-content:center;box-sizing:border-box;flex-shrink:0;padding:10rpx;border-right:1rpx solid #e2e8ed;border-bottom:1rpx solid #e2e8ed;color:#52606d;font-size:18rpx;text-align:center;white-space:pre-line}.tr.th text{background:#f5f8fa;color:#344054;font-weight:720}.date{width:190rpx}.status{width:110rpx}.person{width:120rpx}.result{width:180rpx}.remark{width:140rpx}.result.abnormal{background:#fff0ef;color:#b93b35}.privacy{display:block;padding:8rpx 12rpx;color:#98a2b3;font-size:18rpx;line-height:1.5}.state{padding:120rpx 20rpx;color:#98a2b3;text-align:center}.state.error{color:#b54747}
</style>
