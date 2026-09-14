<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue';
import { onHide, onShow } from '@dcloudio/uni-app';
import { committeeApi, committeeAccessLost, type CommitteeAttachment } from '@/api/safetyCommittee';
const props=defineProps<{attachment:CommitteeAttachment}>();
const emit=defineEmits<{(e:'open'):void}>();
const url=ref('');let visible=true;let generation=0;
async function load(){const current=++generation;const file=props.attachment;
 if(!visible||file.previewStatus!=='READY'||!['IMAGE','HEIF'].includes(file.previewKind))return;
 try{const next=await committeeApi.read(file.id);if(visible&&current===generation)url.value=next;}
 catch(e){if(current===generation){url.value='';committeeAccessLost(e);}}
}
watch(()=>[props.attachment.id,props.attachment.previewStatus],load,{immediate:true});
onShow(()=>{visible=true;void load();});onHide(()=>{visible=false;generation++;url.value='';});
onBeforeUnmount(()=>{visible=false;generation++;});
</script>
<template><view class="committee-thumbnail" @tap.stop="emit('open')"><image class="committee-thumbnail-image" v-if="url" :src="url" mode="aspectFill"/><text v-else>{{attachment.previewKind==='VIDEO'?'▶':attachment.extension.toUpperCase()}}</text></view></template>
<style scoped>.committee-thumbnail{width:92rpx;height:92rpx;flex:0 0 92rpx;border-radius:12rpx;background:#eaf2fc;color:#476a95;display:flex;align-items:center;justify-content:center;font-size:22rpx;overflow:hidden}.committee-thumbnail-image{width:100%;height:100%}</style>
