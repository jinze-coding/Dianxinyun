<script setup lang="ts">
import { computed, ref, onBeforeUnmount } from 'vue';
import { onHide } from '@dcloudio/uni-app';
import type { SelectedFile } from '@/api/safetyCommittee';
import { committeeLocalMediaKind } from '@/utils/committeeLocalMedia';
import AppNavBar from '@/components/AppNavBar.vue';
const props=defineProps<{file:SelectedFile}>();
const emit=defineEmits<{(e:'close'):void}>();
const kind=computed(()=>committeeLocalMediaKind(props.file));
const message=ref('');let blobUrl='';
// #ifdef H5
if(props.file.file)blobUrl=URL.createObjectURL(props.file.file);
// #endif
const source=computed(()=>props.file.path||blobUrl);
onHide(()=>emit('close'));
onBeforeUnmount(()=>{
  // #ifdef H5
  if(blobUrl)URL.revokeObjectURL(blobUrl);
  // #endif
});
</script>
<template>
  <view class="committee-preview">
    <AppNavBar title="附件预览" @back="emit('close')" />
    <view class="committee-preview-body">
    <view class="committee-row"><text>现场附件预览</text><button class="committee-button committee-preview-button" @tap="emit('close')">关闭预览</button></view>
    <text class="committee-preview-title">{{file.name}}</text>
    <view v-if="message" class="committee-error">{{message}}</view>
    <view class="committee-preview-media">
      <video v-if="kind==='video'" class="committee-preview-video" :src="source" :poster="file.thumbnailPath" controls :autoplay="false" :show-fullscreen-btn="true" @error="message='暂时无法播放，请关闭后重试'" />
      <image v-else class="committee-preview-image" :src="source" mode="aspectFit" @error="message='图片读取失败，请重新拍摄或选择'" />
    </view>
    </view>
  </view>
</template>
<style scoped src="../pages/safety-committee/committee.css"></style>
