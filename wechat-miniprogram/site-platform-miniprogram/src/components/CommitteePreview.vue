<script setup lang="ts">
const runtime=uni;
import { ref, onMounted, onBeforeUnmount } from 'vue';
import { onHide, onShow } from '@dcloudio/uni-app';
import { getNavLayoutMetrics } from '@/utils/navLayout';
import { committeeApi, committeeAccessLost, type CommitteeAttachment } from '@/api/safetyCommittee';
const metrics=getNavLayoutMetrics();
const previewStyle={paddingTop:`${metrics.statusBarHeight+metrics.navHeight+12}px`};
const props = defineProps<{ attachment: CommitteeAttachment; canRetry?: boolean }>();
const emit = defineEmits<{ (e:'close'):void }>();
const file = ref(props.attachment); const url = ref(''); const message = ref(''); const downloading = ref(false); const downloaded = ref(false);
let timer: ReturnType<typeof setInterval> | undefined; let task: UniApp.DownloadTask | undefined; let alive = true; let nativeDocument = false;
let temporary = ''; let renewedAt = 0;
// #ifdef H5
let h5Download:AbortController|undefined;let h5BlobUrl='';
// #endif
async function load() {
  if (!alive || nativeDocument) return;
  try {
    file.value = await committeeApi.attachment(file.value.id);
    if (file.value.previewStatus === 'READY' && (!url.value || Date.now()-renewedAt > 12*60*1000)) { const next = await committeeApi.read(file.value.id); if (alive) { url.value = next; renewedAt = Date.now(); } }
  } catch (e) { if (alive) { url.value=''; message.value=(e as Error).message; if(committeeAccessLost(e)) close(); } }
}
function clearTemporary() {
  // #ifdef MP-WEIXIN
  if (temporary && !nativeDocument) uni.getFileSystemManager().unlink({filePath:temporary,fail:()=>undefined});
  // #endif
  temporary=''; downloaded.value=false;
}
function stop() {
  // #ifdef H5
  h5Download?.abort();if(h5BlobUrl)URL.revokeObjectURL(h5BlobUrl);
  // #endif
  url.value=''; task?.abort(); task=undefined; clearInterval(timer); }
function close() { stop(); emit('close'); }
async function download(original = false) {
  if (downloading.value) return; downloading.value=true; message.value='';
  try {
    const source=await committeeApi.read(file.value.id,!original);
    // #ifdef H5
    if(original){
      h5Download=new AbortController();const response=await fetch(source,{signal:h5Download.signal});if(!response.ok)throw new Error('文件读取失败，请重新打开');
      const blob=await response.blob();if(!alive)return;if(h5BlobUrl)URL.revokeObjectURL(h5BlobUrl);h5BlobUrl=URL.createObjectURL(blob);
      const a=document.createElement('a');a.href=h5BlobUrl;a.download=file.value.fileName;a.click();
    }else{const a=document.createElement('a');a.href=source;a.target='_blank';a.rel='noopener';a.click();}
    // #endif
    // #ifdef MP-WEIXIN
    const path=await new Promise<string>((resolve,reject)=>{task=uni.downloadFile({url:source,success:r=>r.statusCode===200?resolve(r.tempFilePath):reject(new Error('文件读取资格已变化，请重新打开')),fail:reject});});
    task=undefined; if(!alive) return;
    clearTemporary(); temporary=path; nativeDocument=true;
    const ext=original?file.value.extension:file.value.previewKind==='OFFICE'?'pdf':file.value.extension;
    if (!['pdf','doc','docx','xls','xlsx','ppt','pptx'].includes(ext)) { nativeDocument=false; downloaded.value=true; message.value='原件已下载，可保存到相册或导出文件。'; return; }
    await new Promise<void>((resolve,reject)=>uni.openDocument({filePath:path,fileType:ext as any,showMenu:true,success:()=>resolve(),fail:reject}));
    // #endif
  } catch(e) { nativeDocument=false; message.value=(e as Error).message || '此格式可下载后使用相应应用打开'; }
  finally { downloading.value=false; }
}
function exportOriginal() {
  // #ifdef MP-WEIXIN
  if (!temporary) return; nativeDocument=true;
  uni.shareFileMessage({filePath:temporary,fileName:file.value.fileName,fail:e=>{nativeDocument=false;message.value=e.errMsg||'文件导出未完成';}});
  // #endif
}
function saveMedia() {
  if (!temporary) return;
  const options={filePath:temporary,success:()=>message.value='已保存到相册',fail:()=>message.value='保存相册失败，请检查相册权限，或使用导出文件。'};
  if(file.value.previewKind==='VIDEO')uni.saveVideoToPhotosAlbum(options);else uni.saveImageToPhotosAlbum(options);
}
async function retry() { try { await committeeApi.retry(file.value.id); message.value=''; await load(); } catch(e) { message.value=(e as Error).message; } }
onMounted(()=>{void load();timer=setInterval(load,5000);});
onShow(()=>{nativeDocument=false;if(alive)void load();});
onHide(()=>{ if(!nativeDocument) close(); });
onBeforeUnmount(()=>{alive=false;stop();clearTemporary();});
</script>
<template>
  <view class="committee-preview" :style="previewStyle">
    <view class="committee-row"><button class="committee-button committee-preview-button" @tap="download(true)">下载原件</button><button class="committee-button committee-preview-button" @tap="close">关闭预览</button></view>
    <view v-if="downloaded" class="committee-actions"><button class="committee-button committee-action-button committee-preview-button" v-if="['VIDEO','IMAGE','HEIF'].includes(file.previewKind)" @tap="saveMedia">保存到相册</button><button class="committee-button committee-action-button committee-preview-button" @tap="exportOriginal">导出文件</button></view>
    <text class="committee-preview-title">{{file.fileName}}</text>
    <view v-if="message" class="committee-error">{{message}}</view>
    <view v-if="file.previewStatus!=='READY'" class="committee-preview-message"><text>{{file.previewStatus==='FAILED'?'预览生成失败':'预览处理中…'}}</text><text>{{file.failureMessage || '完成后自动显示，可先下载原件'}}</text><button class="committee-button committee-preview-button" v-if="canRetry && file.previewStatus==='FAILED'" @tap="retry">重新生成预览</button></view>
    <video v-else-if="url && file.previewKind==='VIDEO'" class="committee-preview-video" :src="url" controls :autoplay="false" :show-fullscreen-btn="true" @error="message='播放中断，请关闭后重新打开'" />
    <image v-else-if="url && ['IMAGE','HEIF'].includes(file.previewKind)" class="committee-preview-image" :src="url" mode="aspectFit" @tap="runtime.previewImage({urls:[url],current:url})" />
    <view v-else class="committee-preview-message"><text>{{downloading?'正在下载…':'文档预览'}}</text><button class="committee-button committee-preview-button" :class="{'committee-button-disabled':downloading}" :disabled="downloading" @tap="download(false)">打开文档</button></view>
  </view>
</template>
<style scoped src="../pages/safety-committee/committee.css"></style>
