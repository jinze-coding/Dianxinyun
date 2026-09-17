<script setup lang="ts">
import { moduleDownload } from '@/utils/moduleNetwork';

const runtime=uni;
import { computed, ref, onMounted, onBeforeUnmount } from 'vue';
import { onHide, onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import { createCommitteeMediaLoader } from '@/utils/committeeMedia';
import { committeeApi, committeeAccessLost, type CommitteeAttachment, type SelectedFile } from '@/api/safetyCommittee';
const props = defineProps<{ attachment: CommitteeAttachment; canRetry?: boolean; localFile?: SelectedFile }>();
const emit = defineEmits<{ (e:'close'):void; (e:'updated',attachment:CommitteeAttachment):void }>();
const file = ref(props.attachment); const url = ref(''); const message = ref(''); const downloading = ref(false); const downloaded = ref(false);
let timer: ReturnType<typeof setInterval> | undefined; let task: UniApp.DownloadTask | undefined; let alive = true; let nativeDocument = false;
let temporary = ''; let renewedAt = 0; let generation=0;let downloadedName='';
const rotating=ref(false);
const poster=ref('');let localUrl='';
const media=createCommitteeMediaLoader();const posterMedia=createCommitteeMediaLoader();
const mediaLoading=ref(true);const mediaError=ref('');const mediaProgress=ref(0);let loading=false;let forceDownload=false;
// #ifdef H5
if(props.localFile?.file)localUrl=URL.createObjectURL(props.localFile.file);
// #endif
const localVideo=computed(()=>file.value.previewKind==='VIDEO'&&!file.value.rotationDegrees&&(file.value.rotationVersion||1)===1?(props.localFile?.path||localUrl):'');
// #ifdef H5
let h5Download:AbortController|undefined;let h5BlobUrl='';
// #endif
function accept(next:CommitteeAttachment) {
  if((next.rotationVersion||1)<(file.value.rotationVersion||1))return false;
  if(next.rotationVersion!==file.value.rotationVersion||next.previewStatus!=='READY') {media.clear();posterMedia.clear();url.value='';poster.value='';renewedAt=0;mediaError.value='';forceDownload=false;}
  file.value=next;emit('updated',next);return true;
}
async function load() {
  if (!alive || nativeDocument || rotating.value || loading) return;
  loading=true;
  const ticket=++generation;const valid=()=>alive&&ticket===generation&&!rotating.value;
  try {
    const next=await committeeApi.attachment(file.value.id);
    if(!valid()||!accept(next))return;
    if (next.previewStatus === 'READY' && ['IMAGE','HEIF','VIDEO'].includes(next.previewKind) && !mediaError.value && (!url.value || Date.now()-renewedAt > 12*60*1000)) {
      mediaLoading.value=true;mediaProgress.value=0;
      const source=await media.load(next.id,{video:next.previewKind==='VIDEO',forceDownload,onProgress:percent=>{if(valid())mediaProgress.value=percent;}});
      if(valid()){url.value=source;renewedAt=Date.now();mediaLoading.value=false;}
    } else if(next.previewStatus!=='READY' && !poster.value) {
      const state=await committeeApi.thumbnail(next.id);
      if(valid()&&state.status==='READY'){const source=await posterMedia.load(next.id,{thumbnail:true});if(valid())poster.value=source;}
    }
  } catch (e) { if (valid()) { url.value='';mediaLoading.value=false;mediaError.value=(e as Error).message||'附件加载失败，请重试'; if(committeeAccessLost(e)) close(); } }
  finally{loading=false;}
}
function mediaFailed(){mediaLoading.value=false;mediaError.value=file.value.previewKind==='VIDEO'?'视频加载失败，可重试加载后播放':'图片加载失败，请重试';}
function retryMedia(){media.clear();posterMedia.clear();url.value='';poster.value='';mediaError.value='';mediaLoading.value=true;forceDownload=true;void load();}
function previewImage(source:string){
  // #ifdef MP-WEIXIN
  nativeDocument=true;
  // #endif
  runtime.previewImage({urls:[source],current:source,fail:()=>{nativeDocument=false;mediaFailed();}});
}
async function rotate(step:number) {
  if(rotating.value||downloading.value||!file.value.canRotate)return;
  rotating.value=true;generation++;media.clear();posterMedia.clear();url.value='';poster.value='';mediaError.value='';mediaLoading.value=true;message.value='';
  const before=file.value;
  try {
    const next=await committeeApi.rotate(before.id,((before.rotationDegrees||0)+step+360)%360,before.rotationVersion||1);
    if(alive){clearTemporary();accept(next);}
  } catch(e) {if(alive){message.value=(e as Error).message||'角度保存失败，请重新核对';if(committeeAccessLost(e))close();}}
  finally{rotating.value=false;if(alive)void load();}
}
function clearTemporary() {
  // #ifdef MP-WEIXIN
  if (temporary && !nativeDocument) uni.getFileSystemManager().unlink({filePath:temporary,fail:()=>undefined});
  // #endif
  temporary=''; downloaded.value=false;
}
function stop() {
  generation++;
  // #ifdef H5
  h5Download?.abort();if(h5BlobUrl)URL.revokeObjectURL(h5BlobUrl);
  // #endif
  media.clear();posterMedia.clear();url.value=''; task?.abort(); task=undefined; clearInterval(timer); }
function close() { stop(); emit('close'); }
async function download(original = false) {
  if (downloading.value||rotating.value) return; downloading.value=true; message.value='';
  try {
    const source=await committeeApi.read(file.value.id,!original);
    const media=['IMAGE','HEIF','VIDEO'].includes(file.value.previewKind);
    const extension=original?file.value.extension:file.value.previewKind==='VIDEO'?'mp4':file.value.previewKind==='HEIF'?'jpg':file.value.previewKind==='IMAGE'?(file.value.extension==='gif'?'gif':file.value.rotationDegrees?'png':file.value.extension):'pdf';
    downloadedName=original?file.value.fileName:media?`${file.value.fileName.replace(/\.[^.]+$/,'')}_已调整.${extension}`:file.value.fileName;
    // #ifdef H5
    if(original||media){
      h5Download=new AbortController();const response=await fetch(source,{signal:h5Download.signal});if(!response.ok)throw new Error('文件读取失败，请重新打开');
      const blob=await response.blob();if(!alive)return;if(h5BlobUrl)URL.revokeObjectURL(h5BlobUrl);h5BlobUrl=URL.createObjectURL(blob);
      const a=document.createElement('a');a.href=h5BlobUrl;a.download=downloadedName;a.click();
    }else{const a=document.createElement('a');a.href=source;a.target='_blank';a.rel='noopener';a.click();}
    // #endif
    // #ifdef MP-WEIXIN
    const path=await new Promise<string>((resolve,reject)=>{task=moduleDownload({url:source,success:r=>r.statusCode===200?resolve(r.tempFilePath):reject(new Error('文件读取资格已变化，请重新打开')),fail:reject});});
    task=undefined; if(!alive) return;
    clearTemporary(); temporary=path; nativeDocument=true;
    const ext=extension;
    if (!['pdf','doc','docx','xls','xlsx','ppt','pptx'].includes(ext)) { nativeDocument=false; downloaded.value=true; message.value=original?'原件已下载，可保存到相册或导出文件。':'调整后文件已下载，可保存到相册或导出文件。'; return; }
    await new Promise<void>((resolve,reject)=>uni.openDocument({filePath:path,fileType:ext as any,showMenu:true,success:()=>resolve(),fail:reject}));
    // #endif
  } catch(e) { nativeDocument=false; message.value=(e as Error).message || '此格式可下载后使用相应应用打开'; }
  finally { downloading.value=false; }
}
function exportOriginal() {
  // #ifdef MP-WEIXIN
  if (!temporary) return; nativeDocument=true;
  uni.shareFileMessage({filePath:temporary,fileName:downloadedName||file.value.fileName,fail:e=>{nativeDocument=false;message.value=e.errMsg||'文件导出未完成';}});
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
onBeforeUnmount(()=>{alive=false;stop();clearTemporary();
  // #ifdef H5
  if(localUrl)URL.revokeObjectURL(localUrl);
  // #endif
});
</script>
<template>
  <view class="committee-preview">
    <AppNavBar title="附件预览" @back="close" />
    <view class="committee-preview-body">
    <view class="committee-row"><button class="committee-button committee-preview-button" @tap="download(true)">下载原件</button><button class="committee-button committee-preview-button" @tap="close">关闭预览</button></view>
    <view v-if="downloaded" class="committee-actions"><button class="committee-button committee-action-button committee-preview-button" v-if="['VIDEO','IMAGE','HEIF'].includes(file.previewKind)" @tap="saveMedia">保存到相册</button><button class="committee-button committee-action-button committee-preview-button" @tap="exportOriginal">导出文件</button></view>
    <view v-if="['IMAGE','HEIF','VIDEO'].includes(file.previewKind)" class="committee-rotation-toolbar">
      <view v-if="file.canRotate" class="committee-rotation-buttons">
        <button class="committee-button committee-preview-button" :disabled="rotating||downloading" @tap="rotate(-90)">↶ 向左旋转 90°</button>
        <button class="committee-button committee-preview-button" :disabled="rotating||downloading" @tap="rotate(90)">↷ 向右旋转 90°</button>
      </view>
      <text class="committee-muted">{{rotating?'正在保存角度…':`已保存角度 ${file.rotationDegrees||0}°`}}</text>
      <button v-if="file.rotationDegrees" class="committee-button committee-preview-button" :disabled="file.previewStatus!=='READY'||rotating||downloading" @tap="download(false)">下载调整后文件</button>
    </view>
    <text class="committee-preview-title">{{file.fileName}}</text>
    <view v-if="message" class="committee-error">{{message}}</view>
    <view class="committee-preview-media">
      <view v-if="mediaError" class="committee-preview-message"><text>{{mediaError}}</text><button class="committee-button committee-preview-button" @tap="retryMedia">重试加载</button></view>
      <video v-else-if="localVideo||(url && file.previewKind==='VIDEO')" class="committee-preview-video" :src="url||localVideo" controls :autoplay="false" :show-fullscreen-btn="true" @error="mediaFailed" />
      <view v-else-if="file.previewStatus!=='READY'" class="committee-preview-message"><image v-if="poster" :src="poster" mode="aspectFit" class="committee-preview-poster" @error="mediaFailed" @tap="previewImage(poster)" /><text>{{poster?'附件首页 / 封面':file.previewStatus==='FAILED'?'预览生成失败':file.rotationDegrees?'角度已保存，正在生成调整后预览…':'预览处理中…'}}</text><button v-if="file.previewStatus==='WAITING'&&file.previewKind==='OFFICE'" class="committee-button committee-preview-button" @tap="download(true)">打开原文档</button><text>{{file.failureMessage || (file.previewStatus==='WAITING'?'提交记录后生成完整预览，可先查看缩略图或下载原件':'完成后自动显示，可先下载原件')}}</text><button class="committee-button committee-preview-button" v-if="(canRetry||file.canRotate) && file.previewStatus==='FAILED'" @tap="retry">重新生成预览</button></view>
      <image v-else-if="url && ['IMAGE','HEIF'].includes(file.previewKind)" class="committee-preview-image" :src="url" mode="aspectFit" @error="mediaFailed" @tap="previewImage(url)" />
      <view v-else-if="['IMAGE','HEIF','VIDEO'].includes(file.previewKind)" class="committee-preview-message"><text>{{mediaLoading?`正在加载附件${mediaProgress?` ${mediaProgress}%`:''}…`:'附件尚未加载'}}</text></view>
      <view v-else class="committee-preview-message"><text>{{downloading?'正在下载…':'文档预览'}}</text><button class="committee-button committee-preview-button" :class="{'committee-button-disabled':downloading}" :disabled="downloading" @tap="download(false)">打开文档</button></view>
    </view>
    </view>
  </view>
</template>
<style scoped src="../pages/safety-committee/committee.css"></style>

<style scoped>
.committee-rotation-toolbar{display:flex;flex-direction:column;gap:16rpx;padding:20rpx;border-radius:12rpx;background:var(--workspace-surface,#fff);border:1rpx solid var(--workspace-divider,#e4edf4);flex-shrink:0}.committee-rotation-buttons{display:flex;gap:12rpx}.committee-rotation-buttons .committee-preview-button{flex:1;margin:0;padding:0 8rpx;font-size:24rpx;white-space:nowrap}.committee-rotation-toolbar button[disabled]{opacity:.5}
</style>
