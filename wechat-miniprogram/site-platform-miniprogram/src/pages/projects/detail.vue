<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { getProjectProfile } from '@/api/project';
import { downloadFilePaths } from '@/api/file';
import type { ProjectProfile } from '@/types';

type ProfileKey = keyof ProjectProfile;
interface Field { key: ProfileKey; label: string; suffix?: string }
interface Group { title: string; fields: Field[] }

const groups: Group[] = [
  { title: '基本信息', fields: [
    { key: 'projectName', label: '项目全称' }, { key: 'shortName', label: '项目简称' },
    { key: 'directCompany', label: '直属公司' }, { key: 'manager', label: '项目经理' },
    { key: 'managerPhone', label: '经理联系方式' }, { key: 'spaceCapacity', label: '空间容量' },
    { key: 'engineeringType', label: '工程类型' }, { key: 'phase', label: '工程状态' },
    { key: 'description', label: '项目简介' }
  ] },
  { title: '工期与位置', fields: [
    { key: 'startDate', label: '计划开工日期' }, { key: 'endDate', label: '计划竣工日期' },
    { key: 'actualStartDate', label: '实际开工日期' }, { key: 'actualEndDate', label: '实际竣工日期' },
    { key: 'address', label: '项目地点' }, { key: 'fixedIpAddress', label: '固定 IP' }
  ] },
  { title: '参建及合同', fields: [
    { key: 'ownerUnit', label: '建设单位' }, { key: 'supervisionUnit', label: '监理单位' },
    { key: 'designUnit', label: '设计单位' }, { key: 'contractor', label: '施工单位' },
    { key: 'contractorCreditCode', label: '统一社会信用代码' }, { key: 'contractorLicenseNumber', label: '安全生产许可证号' },
    { key: 'generalContractNumber', label: '总承包合同编号' }, { key: 'projectClassification', label: '项目分类' },
    { key: 'investmentEntity', label: '投资主体' }, { key: 'contractingMode', label: '承建模式' },
    { key: 'contractAmount', label: '合同金额', suffix: ' 元' }
  ] },
  { title: '规模指标', fields: [
    { key: 'buildingArea', label: '建筑面积', suffix: ' ㎡' }, { key: 'landArea', label: '用地面积', suffix: ' ㎡' },
    { key: 'buildingHeight', label: '建筑高度', suffix: ' m' }, { key: 'excavationDepth', label: '开挖深度', suffix: ' m' },
    { key: 'undergroundFloorCount', label: '地下层数', suffix: ' 层' }, { key: 'abovegroundFloorCount', label: '地上层数', suffix: ' 层' },
    { key: 'projectScale', label: '项目规模' }, { key: 'projectCategory', label: '项目类别' }, { key: 'projectLevel', label: '项目级别' }
  ] },
  { title: '目标与人员', fields: [
    { key: 'projectTarget', label: '项目目标' }, { key: 'qualityGoal', label: '质量目标' },
    { key: 'safetyGoal', label: '安全目标' }, { key: 'greenConstructionGoal', label: '绿色建造目标' },
    { key: 'managementStaffCount', label: '管理人员数', suffix: ' 人' }, { key: 'attendanceCount', label: '考勤人数', suffix: ' 人' },
    { key: 'partyMemberCount', label: '党员人数', suffix: ' 人' }
  ] }
];

const projectId = ref(0);
const profile = ref<ProjectProfile>();
const imagePaths = ref<string[]>([]);
const loading = ref(true);
const errorMessage = ref('');
const imageError = ref('');
const title = computed(() => profile.value?.projectName || '项目信息');

onLoad((query) => {
  projectId.value = Number(query?.projectId || 0);
  load();
});

async function load() {
  if (!projectId.value) {
    errorMessage.value = '项目参数无效';
    loading.value = false;
    return;
  }
  loading.value = true;
  errorMessage.value = '';
  imageError.value = '';
  try {
    const result = await getProjectProfile(projectId.value);
    profile.value = result;
    if (result.images?.length) {
      imagePaths.value = await downloadFilePaths(result.images.map((item) => item.fileId));
      if (imagePaths.value.length !== result.images.length) imageError.value = '部分效果图加载失败';
    } else {
      imagePaths.value = [];
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '项目信息加载失败';
  } finally {
    loading.value = false;
  }
}

function display(field: Field) {
  const value = profile.value?.[field.key];
  if (value === null || value === undefined || value === '') return '未填写';
  return `${String(value)}${field.suffix || ''}`;
}

function preview(index: number) {
  if (!imagePaths.value.length) return;
  uni.previewImage({ urls: imagePaths.value, current: imagePaths.value[index] });
}
</script>

<template>
  <view class="project-detail-page">
    <view v-if="loading" class="state-card"><text>正在加载项目信息…</text></view>
    <view v-else-if="errorMessage" class="state-card error">
      <text>{{ errorMessage }}</text><button @tap="load">重新加载</button>
    </view>
    <template v-else-if="profile">
      <view class="hero-card">
        <text class="hero-label">当前项目</text><text class="hero-title">{{ title }}</text>
        <text class="hero-subtitle">{{ profile.shortName || '未填写简称' }} · {{ profile.phase || '未填写工程状态' }}</text>
      </view>

      <view class="detail-card gallery-card">
        <view class="section-title"><text>项目效果图</text><text>{{ imagePaths.length }} 张</text></view>
        <swiper v-if="imagePaths.length" class="profile-swiper" indicator-dots circular>
          <swiper-item v-for="(path, index) in imagePaths" :key="path">
            <image :src="path" mode="aspectFill" @tap="preview(index)" />
          </swiper-item>
        </swiper>
        <view v-else class="empty-image">未上传项目效果图</view>
        <text v-if="imageError" class="image-error">{{ imageError }}</text>
      </view>

      <view v-for="group in groups" :key="group.title" class="detail-card">
        <view class="section-title"><text>{{ group.title }}</text></view>
        <view class="field-list">
          <view v-for="field in group.fields" :key="field.key" class="field-row">
            <text class="field-label">{{ field.label }}</text>
            <text class="field-value" :class="{ empty: display(field) === '未填写' }">{{ display(field) }}</text>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<style scoped>
.project-detail-page{min-height:100vh;padding:22rpx 24rpx 56rpx;background:#f5f4f0;box-sizing:border-box;color:#283548}.state-card{display:flex;min-height:300rpx;align-items:center;justify-content:center;flex-direction:column;gap:24rpx;border:1rpx solid rgba(145,103,57,.12);border-radius:22rpx;background:#fff;color:#8a929d}.state-card.error{color:#b84c4c}.state-card button{min-width:170rpx;height:66rpx;border:0;border-radius:14rpx;background:#a96527;color:#fff;font-size:23rpx;line-height:66rpx}.hero-card,.detail-card{margin-bottom:18rpx;border:1rpx solid rgba(145,103,57,.1);border-radius:22rpx;background:#fff;box-shadow:0 9rpx 26rpx rgba(68,53,34,.055)}.hero-card{padding:28rpx;background:linear-gradient(145deg,#fff,#fff8ef)}.hero-label,.hero-title,.hero-subtitle{display:block}.hero-label{color:#a96527;font-size:20rpx;font-weight:700}.hero-title{margin-top:10rpx;color:#263449;font-size:31rpx;font-weight:900;line-height:1.45}.hero-subtitle{margin-top:10rpx;color:#7e8997;font-size:22rpx}.detail-card{padding:22rpx}.section-title{display:flex;align-items:center;justify-content:space-between;margin-bottom:15rpx;color:#283548;font-size:27rpx;font-weight:900}.section-title text:last-child:not(:first-child){color:#9a7b5c;font-size:20rpx;font-weight:500}.profile-swiper{height:360rpx;overflow:hidden;border-radius:17rpx;background:#edf1f5}.profile-swiper image{width:100%;height:100%}.empty-image{display:flex;height:190rpx;align-items:center;justify-content:center;border:1rpx dashed #d7cec3;border-radius:17rpx;background:#faf9f7;color:#9fa5ad;font-size:23rpx}.image-error{display:block;margin-top:10rpx;color:#b84c4c;font-size:21rpx}.field-list{border-top:1rpx solid #eee8e1}.field-row{display:flex;align-items:flex-start;gap:20rpx;padding:19rpx 0;border-bottom:1rpx solid #f0ebe5}.field-row:last-child{border-bottom:0}.field-label{width:190rpx;flex:0 0 190rpx;color:#7b8796;font-size:22rpx;line-height:1.6}.field-value{flex:1;min-width:0;color:#2d3a4e;font-size:23rpx;line-height:1.65;overflow-wrap:anywhere;white-space:pre-wrap}.field-value.empty{color:#b0b4ba}
</style>
