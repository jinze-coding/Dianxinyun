<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount } from 'vue';
import { onHide, onShow } from '@dcloudio/uni-app';
import { committeeApi, committeeAccessLost, type CommitteeAttachment, type SelectedFile } from '@/api/safetyCommittee';
import { createCommitteeMediaLoader } from '@/utils/committeeMedia';
const props=defineProps<{attachment?:CommitteeAttachment;localFile?:SelectedFile;large?:boolean}>();
const emit=defineEmits<{(e:'open'):void}>();
const url=ref('');const failed=ref(false);const visible=ref(true);let generation=0;let timer:ReturnType<typeof setTimeout>|undefined;let localUrl='';
const media=createCommitteeMediaLoader();
const extension=computed(()=>(props.attachment?.extension||props.localFile?.name.split('.').pop()||'').toUpperCase());
const video=computed(()=>props.attachment?.previewKind==='VIDEO'||['MP4','MOV','M4V','WEBM','MKV','AVI'].includes(extension.value));
const photo=computed(()=>['JPG','JPEG','PNG','GIF','BMP','WEBP','HEIC','HEIF'].includes(extension.value));
const local=ref('');
watch(()=>props.localFile, file=>{
  // #ifdef H5
  if(localUrl)URL.revokeObjectURL(localUrl);localUrl='';
  if(file?.file&&photo.value)localUrl=URL.createObjectURL(file.file);
  // #endif
  local.value=file?.thumbnailPath||(photo.value?(file?.path||localUrl):'')||'';
},{immediate:true});
const image=computed(()=>visible.value?((!props.attachment?.rotationDegrees?local.value:'')||url.value):'');
async function load(retry=false){
  clearTimeout(timer);const current=++generation;const id=props.attachment?.id;
  if(!visible.value||!id)return;
  failed.value=false;
  try{
    const state=await committeeApi.thumbnail(id,retry);
    if(!visible.value||current!==generation)return;
    if(state.status==='READY'){
      const next=await media.load(id,{thumbnail:true});
      if(visible.value&&current===generation)url.value=next;
    }else if(state.status==='FAILED')failed.value=true;
    else timer=setTimeout(()=>void load(),2000);
  }catch(e){if(visible.value&&current===generation){url.value='';failed.value=true;committeeAccessLost(e);}}
}
function open(){if(failed.value&&!image.value)void load(true);else emit('open');}
function imageError(){if(image.value===local.value)local.value='';else url.value='';failed.value=!image.value;}
watch(()=>[props.attachment?.id,props.attachment?.rotationVersion],()=>{media.clear();url.value='';failed.value=false;void load();},{immediate:true});
onShow(()=>{visible.value=true;void load();});
onHide(()=>{visible.value=false;generation++;clearTimeout(timer);media.clear();url.value='';});
onBeforeUnmount(()=>{visible.value=false;generation++;clearTimeout(timer);media.clear();
  // #ifdef H5
  if(localUrl)URL.revokeObjectURL(localUrl);
  // #endif
});
</script>
<template>
  <view class="committee-thumbnail" :class="{'committee-thumbnail-large':large}" role="button" :aria-label="failed&&!image?'重试缩略图':'预览附件'" @tap.stop="open">
    <image v-if="image" class="committee-thumbnail-image" :src="image" :mode="photo||video?'aspectFill':'aspectFit'" @error="imageError" />
    <view v-else class="committee-thumbnail-placeholder"><text class="committee-thumbnail-format">{{extension||'附件'}}</text><text v-if="large" class="committee-thumbnail-state">{{failed?'点击重试':attachment?'生成缩略图…':'等待上传'}}</text></view>
    <text v-if="image&&video" class="committee-thumbnail-play">▶</text>
    <text v-if="image&&!photo&&!video" class="committee-thumbnail-badge">{{extension}} · 首页</text>
  </view>
</template>
<style scoped>
.committee-thumbnail{position:relative;width:92rpx;height:92rpx;flex:0 0 92rpx;border-radius:12rpx;background:#edf3f8;color:var(--workspace-accent-deep,#315f86);display:flex;align-items:center;justify-content:center;font-size:22rpx;overflow:hidden}
.committee-thumbnail-large{width:100%;height:220rpx;flex:none;border-radius:12rpx 12rpx 0 0}
.committee-thumbnail-image{width:100%;height:100%}
.committee-thumbnail-placeholder{display:flex;flex-direction:column;align-items:center;gap:12rpx;padding:8rpx}
.committee-thumbnail-format{font-weight:700}.committee-thumbnail-state{font-size:20rpx;color:#788b9f}
.committee-thumbnail-play{position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);display:flex;align-items:center;justify-content:center;width:50rpx;height:50rpx;border-radius:50%;background:#132a48a8;color:#fff;font-size:25rpx}
.committee-thumbnail-badge{position:absolute;right:6rpx;bottom:6rpx;padding:3rpx 7rpx;background:#132a48b8;color:#fff;font-size:17rpx;border-radius:5rpx}
</style>
