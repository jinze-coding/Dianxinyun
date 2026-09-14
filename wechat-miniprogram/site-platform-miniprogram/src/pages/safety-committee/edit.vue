<script setup lang="ts">
const runtime=uni;
import { ref } from 'vue';
import { onLoad,onShow,onHide,onUnload } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import { useAuthStore } from '@/stores/auth';
import { committeeApi,committeeDate,committeeKey,committeeAccessLost,activeCommitteeFiles,chooseCommitteeFiles,uploadCommitteeFile,type SelectedFile,type UploadControl,type CommitteeAttachment } from '@/api/safetyCommittee';
interface UploadItem{key:string;file?:SelectedFile;attachment?:CommitteeAttachment;state:'uploading'|'done'|'failed';message:string;control?:UploadControl}
const auth=useAuthStore();const id=ref<number>();const projectId=ref(0);const version=ref(1);const requestKey=committeeKey();const categories=ref<string[]>([]);const category=ref('');const conclusion=ref('');const inspectedAt=ref('');const files=ref<UploadItem[]>([]);const error=ref('');const saving=ref(false);let disposed=false;let uploadsPaused=false;let timer:ReturnType<typeof setInterval>|undefined;
function pause(){uploadsPaused=true;files.value.filter(f=>f.state==='uploading'&&!f.control).forEach(f=>{f.state='failed';f.message='已暂停，可重试续传';});files.value.forEach(f=>{if(f.control){f.control.cancelled=true;f.control.abort?.();}});}
async function permission(){try{if(projectId.value)await committeeApi.categories(projectId.value);}catch(e){error.value=(e as Error).message;if(committeeAccessLost(e))pause();}}
onLoad(async(options)=>{try{ id.value=Number(options?.id)||undefined;projectId.value=Number(options?.projectId)||0;
 if(id.value){const r=await committeeApi.detail(id.value);if(!r.canEdit)throw new Error('仅本人且拥有修改权限时可编辑');projectId.value=r.projectId;version.value=r.version;category.value=r.category;conclusion.value=r.conclusion;inspectedAt.value=r.inspectedAt;files.value=activeCommitteeFiles(r).map(a=>({key:committeeKey(),attachment:a,state:'done',message:''}));}
 categories.value=await committeeApi.categories(projectId.value);
 }catch(e){error.value=(e as Error).message;committeeAccessLost(e);}});
onShow(async()=>{if(!await auth.ensureRootAccess('/pages/safety-committee/index'))return;void permission();clearInterval(timer);timer=setInterval(permission,5000);});
onHide(()=>{pause();clearInterval(timer);});onUnload(()=>{disposed=true;pause();clearInterval(timer);});
async function upload(item:UploadItem){if(!item.file)return;uploadsPaused=false;const control:UploadControl={cancelled:false};item.control=control;item.state='uploading';
 try{item.attachment=await uploadCommitteeFile(item.file,{projectId:projectId.value,draftKey:requestKey,targetRecordId:id.value},auth.state.user!.id,m=>item.message=m,control);item.state='done';item.message='上传完成';}
 catch(e){item.state='failed';item.message=control.cancelled?'已暂停，可重试续传':(e as Error).message||'上传失败，请重试';if(committeeAccessLost(e))pause();}finally{item.control=undefined;}}
async function add(selected:SelectedFile[]){if(files.value.length+selected.length>30){error.value='每条记录最多 30 个附件';return;}
 uploadsPaused=false;const added:UploadItem[]=selected.map(file=>({key:committeeKey(),file,state:'uploading',message:'等待上传'}));files.value.push(...added);
 for(const item of added){if(disposed||uploadsPaused)break;if(!files.value.some(f=>f.key===item.key))continue;await upload(files.value.find(f=>f.key===item.key)!);}}
async function choose(source:'album'|'chat'){try{await add(await chooseCommitteeFiles(source));}catch(e){const message=(e as any).errMsg||(e as Error).message;if(!/cancel/i.test(message))error.value=message||'未能选择文件';}}
function capture(){
 // #ifdef MP-WEIXIN
 uni.navigateTo({url:'/pages/safety-committee/capture',events:{captured:(file:SelectedFile)=>void add([file])}});
 // #endif
 // #ifdef H5
 void chooseCommitteeFiles('camera').then(add).catch(e=>error.value=e.message);
 // #endif
}
async function remove(item:UploadItem){item.control&&(item.control.cancelled=true);item.control?.abort?.();try{if(item.attachment?.status==='PENDING')await committeeApi.discard(item.attachment.id);files.value=files.value.filter(f=>f.key!==item.key);}catch(e){error.value=(e as Error).message;}}
async function save(){if(saving.value)return;if(!category.value){error.value='请选择安全隐患分类';return;}if(files.value.some(f=>f.state!=='done')){error.value='请重试或移除未成功上传的附件';return;}saving.value=true;error.value='';
 try{const data={projectId:projectId.value,category:category.value,conclusion:conclusion.value,attachmentIds:files.value.map(f=>f.attachment!.id),requestKey,expectedVersion:version.value};const result=await committeeApi.save(id.value,data);uni.redirectTo({url:`/pages/safety-committee/detail?id=${result.id}`});}
 catch(e){error.value=(e as Error).message;committeeAccessLost(e);}finally{saving.value=false;}}
</script>
<template><view class="committee-page"><AppNavBar title="安委会巡检" @back="runtime.navigateBack()"/><view class="committee-content"><view class="committee-card"><text class="committee-title">{{id?'修改本人巡检':'上报巡检'}}</text><text class="committee-muted">检查人：{{auth.state.user?.realName||auth.state.user?.username}}</text><text class="committee-muted">检查时间：{{inspectedAt?committeeDate(inspectedAt):'提交成功时自动记录'}}</text><view v-if="error" class="committee-error">{{error}}</view>
 <view class="committee-field"><text>安全隐患分类 · 必选</text><picker :range="categories" @change="category=categories[Number($event.detail.value)]"><view class="committee-picker">{{category||'请选择分类'}} ▾</view></picker></view>
 <view class="committee-field"><text>检查结论 · 选填</text><textarea v-model="conclusion" :maxlength="2000" placeholder="填写现场检查情况…"/><text class="committee-muted">{{conclusion.length}} / 2000</text></view>
 <view class="committee-field"><text>现场附件 · 选填 {{files.length}}/30</text><text class="committee-muted">照片 15 MB、办公文件 100 MB、视频 500 MB</text><view class="committee-actions"><button @tap="capture">现场拍摄</button><button @tap="choose('album')">相册</button><button @tap="choose('chat')">选择文件</button></view></view>
 <view v-for="item in files" :key="item.key" class="committee-file"><view><text>{{item.file?.name||item.attachment?.fileName}}</text><text class="committee-muted">{{item.state==='done'?'上传完成':item.message}}</text><view class="committee-actions"><button v-if="item.state==='failed'" @tap="upload(item)">重试续传</button><button v-if="item.state==='uploading'" @tap="item.control&&(item.control.cancelled=true);item.control?.abort?.()">暂停</button><button @tap="remove(item)">移除</button></view></view></view>
 <button class="committee-primary" :disabled="saving||files.some(f=>f.state!=='done')" @tap="save">{{saving?'正在保存…':'提交保存'}}</button>
</view></view></view></template>
<style>@import "./committee.css";</style>
