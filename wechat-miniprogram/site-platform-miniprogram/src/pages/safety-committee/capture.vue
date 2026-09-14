<script setup lang="ts">
const runtime=uni;
import { ref,getCurrentInstance } from 'vue';
import { onShow,onHide,onUnload } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import { useAuthStore } from '@/stores/auth';
import type { SelectedFile } from '@/api/safetyCommittee';
const instance=getCurrentInstance();const auth=useAuthStore();const cameraReady=ref(false);const allowed=ref(false);const recording=ref(false);const busy=ref(false);const seconds=ref(0);const result=ref<SelectedFile>();const kind=ref<'photo'|'video'>('photo');const error=ref('');
let held=false;let longPress=false;let visible=true;let disposed=false;let starting=false;let stopping=false;let pressTimer:ReturnType<typeof setTimeout>|undefined;let durationTimer:ReturnType<typeof setInterval>|undefined;
function context(){return uni.createCameraContext();}
async function authorize(scope:string){await new Promise<void>((resolve,reject)=>uni.authorize({scope,success:()=>resolve(),fail:reject}));}
onShow(async()=>{visible=true;if(!await auth.ensureRootAccess('/pages/safety-committee/index'))return;try{await authorize('scope.camera');allowed.value=true;}catch{error.value='相机未授权，请点击“权限设置”后允许使用相机。';}});
function failure(e:any){busy.value=false;starting=false;recording.value=false;stopping=false;clearInterval(durationTimer);error.value=e?.errMsg||'拍摄中断，请重试或检查相机、麦克风权限';}
async function accept(path:string,type:'photo'|'video'){
 clearInterval(durationTimer);recording.value=false;starting=false;stopping=false;busy.value=false;
 if(!path||disposed)return;kind.value=type;
 try{const info=await new Promise<{size:number}>((resolve,reject)=>uni.getFileInfo({filePath:path,success:resolve,fail:reject}));
 result.value={path,size:info.size,name:`现场${Date.now()}.${path.split('.').pop()||(type==='video'?'mp4':'jpg')}`};
 }catch(e){failure(e);}
}
function photo(){busy.value=true;context().takePhoto({quality:'high',success:r=>void accept(r.tempImagePath,'photo'),fail:failure});}
async function start(){longPress=true;starting=true;busy.value=true;
 try{await authorize('scope.record');if(!held||!visible){busy.value=false;starting=false;error.value='麦克风已授权，请再次长按录像';return;}
 const options:UniApp.CameraContextStartRecordOptions & {timeout:number}={timeout:300,success:()=>{starting=false;recording.value=true;seconds.value=0;durationTimer=setInterval(()=>{seconds.value++;if(seconds.value>=300)stop();},1000);if(!held||!visible)stop();},timeoutCallback:r=>void accept(r.tempVideoPath,'video'),fail:failure};
 context().startRecord(options);
 }catch{starting=false;busy.value=false;error.value='录像需要麦克风权限，请在权限设置中允许后重试。';}}
function stop(){if(!recording.value||stopping)return;stopping=true;clearInterval(durationTimer);context().stopRecord({success:r=>void accept(r.tempVideoPath,'video'),fail:failure});}
function press(){if(!cameraReady.value||busy.value||result.value)return;held=true;longPress=false;error.value='';pressTimer=setTimeout(()=>void start(),350);}
function release(){if(!held)return;held=false;clearTimeout(pressTimer);if(longPress){if(!starting)stop();}else if(visible)photo();}
function cancelPress(){held=false;clearTimeout(pressTimer);if(recording.value)stop();}
function interrupt(){visible=false;held=false;clearTimeout(pressTimer);if(recording.value){error.value='录制已因离开页面结束，请检查预览后确认。';stop();}}
onHide(interrupt);onUnload(()=>{disposed=true;interrupt();clearInterval(durationTimer);});
function confirm(){if(!result.value)return;const page=instance?.proxy as any;page?.getOpenerEventChannel?.().emit('captured',result.value);uni.navigateBack();}
function retake(){visible=true;result.value=undefined;cameraReady.value=false;error.value='';}
function permissions(){uni.openSetting({success:r=>{allowed.value=Boolean(r.authSetting['scope.camera']);if(allowed.value)error.value='';}});}
</script>
<template><view class="committee-page capture-page"><AppNavBar title="现场拍摄" @back="runtime.navigateBack()"/>
 <view v-if="error" class="committee-error">{{error}}<button @tap="permissions">权限设置</button></view>
 <template v-if="result"><video v-if="kind==='video'" class="camera-view" :src="result.path" controls :autoplay="false"/><image v-else class="camera-view" :src="result.path" mode="aspectFit"/><view class="committee-actions"><button @tap="retake">重拍</button><button class="committee-primary" @tap="confirm">确认使用</button></view></template>
 <template v-else><camera v-if="allowed" class="camera-view" device-position="back" flash="off" @initdone="cameraReady=true" @error="failure" @stop="interrupt"/><view v-else class="committee-empty">允许相机权限后开始拍摄</view>
 <view class="shutter-area"><text>{{recording?`正在录像 ${seconds}s / 300s`:'轻点拍照 · 长按录像 · 松手结束'}}</text><view class="shutter" :class="{recording}" @touchstart.stop.prevent="press" @touchend.stop.prevent="release" @touchcancel.stop.prevent="cancelPress"><view/></view><text>{{busy&&!recording?'正在处理…':'录像最长 5 分钟'}}</text></view></template>
 </view></template>
<style>@import "./committee.css";</style>
<style scoped>.capture-page{padding:0 20rpx 40rpx;background:#101e32;color:#fff;box-sizing:border-box}.camera-view{display:block;width:100%;height:62vh;background:#071321;border-radius:16rpx}.shutter-area{display:flex;flex-direction:column;align-items:center;gap:20rpx;padding:30rpx 0;font-size:25rpx}.shutter{width:132rpx;height:132rpx;border:6rpx solid #fff;border-radius:50%;display:flex;align-items:center;justify-content:center;touch-action:none}.shutter>view{background:#fff;width:108rpx;height:108rpx;border-radius:50%}.shutter.recording{border-color:#ff6363}.shutter.recording>view{width:58rpx;height:58rpx;background:#ff6363;border-radius:12rpx}</style>
