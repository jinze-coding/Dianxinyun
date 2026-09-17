<script setup lang="ts">
const runtime=uni;
import { ref, nextTick } from 'vue';
import { onLoad,onShow,onHide,onUnload } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import CommitteeThumbnail from '@/components/CommitteeThumbnail.vue';
import CommitteePreview from '@/components/CommitteePreview.vue';
import CommitteeLocalPreview from '@/components/CommitteeLocalPreview.vue';
import { cleanupCommitteeCapture, committeeLocalMediaKind } from '@/utils/committeeLocalMedia';
import { usePageScrollHeight } from '@/utils/navLayout';
import { useAuthStore } from '@/stores/auth';
import { committeeApi,committeeDate,committeeKey,committeeAccessLost,activeCommitteeFiles,chooseCommitteeFiles,uploadCommitteeFile,type SelectedFile,type UploadControl,type CommitteeAttachment } from '@/api/safetyCommittee';
interface UploadItem{key:string;file?:SelectedFile;attachment?:CommitteeAttachment;state:'uploading'|'done'|'failed';message:string;control?:UploadControl}
const preview=ref<UploadItem>();
function openAttachment(item:UploadItem){if(item.attachment||committeeLocalMediaKind(item.file))preview.value=item;}
const pendingUploads=new Map<string,Promise<void>>();
const removing=new Set<string>();
const {scrollStyle}=usePageScrollHeight({bottomRpx:0,minHeight:180});
const auth=useAuthStore();const id=ref<number>();const projectId=ref(0);const version=ref(1);const requestKey=committeeKey();const categories=ref<string[]>([]);const category=ref('');const conclusion=ref('');const inspectedAt=ref('');const files=ref<UploadItem[]>([]);const error=ref('');const saving=ref(false);let disposed=false;let uploadsPaused=false;let timer:ReturnType<typeof setInterval>|undefined;
function pause(){uploadsPaused=true;files.value.filter(f=>f.state==='uploading'&&!f.control).forEach(f=>{f.state='failed';f.message='已暂停，可重试续传';});files.value.forEach(f=>{if(f.control){f.control.cancelled=true;f.control.abort?.();}});}
async function permission(){try{if(projectId.value)await committeeApi.categories(projectId.value);}catch(e){error.value=(e as Error).message;if(committeeAccessLost(e))pause();}}
onLoad(async(options)=>{try{ id.value=Number(options?.id)||undefined;projectId.value=Number(options?.projectId)||0;
 if(id.value){const r=await committeeApi.detail(id.value);if(!r.canEdit)throw new Error('仅本人且拥有修改权限时可编辑');projectId.value=r.projectId;version.value=r.version;category.value=r.category;conclusion.value=r.conclusion;inspectedAt.value=r.inspectedAt;files.value=activeCommitteeFiles(r).map(a=>({key:committeeKey(),attachment:a,state:'done',message:''}));}
 categories.value=await committeeApi.categories(projectId.value);
 }catch(e){error.value=(e as Error).message;committeeAccessLost(e);}});
onShow(async()=>{if(!await auth.ensureRootAccess('/pages/safety-committee/index'))return;void permission();clearInterval(timer);timer=setInterval(permission,5000);});
onHide(()=>{pause();clearInterval(timer);});onUnload(()=>{disposed=true;pause();preview.value=undefined;clearInterval(timer);files.value.forEach(item=>{void (pendingUploads.get(item.key)||Promise.resolve()).finally(async()=>{await nextTick();cleanupCommitteeCapture(item.file);});});});
function upload(item:UploadItem){
 if(disposed||removing.has(item.key))return Promise.resolve();
 const existing=pendingUploads.get(item.key);if(existing)return existing;
 const task=performUpload(item).finally(()=>pendingUploads.delete(item.key));pendingUploads.set(item.key,task);return task;
}
async function performUpload(item:UploadItem){if(!item.file)return;uploadsPaused=false;const control:UploadControl={cancelled:false};item.control=control;item.state='uploading';
 try{item.attachment=await uploadCommitteeFile(item.file,{projectId:projectId.value,draftKey:requestKey,targetRecordId:id.value},auth.state.user!.id,m=>item.message=m,control);item.state='done';item.message='上传完成';}
 catch(e){item.state='failed';item.message=control.cancelled?'已暂停，可重试续传':(e as Error).message||'上传失败，请重试';if(committeeAccessLost(e))pause();}finally{item.control=undefined;}}
async function add(selected:SelectedFile[]){if(disposed){selected.forEach(cleanupCommitteeCapture);return;}if(files.value.length+selected.length>30){selected.forEach(cleanupCommitteeCapture);error.value='每条记录最多 30 个附件';return;}
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
async function remove(item:UploadItem){if(removing.has(item.key))return;removing.add(item.key);item.control&&(item.control.cancelled=true);item.control?.abort?.();try{await pendingUploads.get(item.key);if(item.attachment?.status==='PENDING')await committeeApi.discard(item.attachment.id);if(preview.value?.key===item.key)preview.value=undefined;files.value=files.value.filter(f=>f.key!==item.key);await nextTick();cleanupCommitteeCapture(item.file);}catch(e){error.value=(e as Error).message;}finally{removing.delete(item.key);}}
async function save(){if(saving.value)return;if(!category.value){error.value='请选择安全隐患分类';return;}if(files.value.some(f=>f.state!=='done')){error.value='请重试或移除未成功上传的附件';return;}saving.value=true;error.value='';
 try{const data={projectId:projectId.value,category:category.value,conclusion:conclusion.value,attachmentIds:files.value.map(f=>f.attachment!.id),requestKey,expectedVersion:version.value};const result=await committeeApi.save(id.value,data);uni.redirectTo({url:`/pages/safety-committee/detail?id=${result.id}`});}
 catch(e){error.value=(e as Error).message;committeeAccessLost(e);}finally{saving.value=false;}}
</script>
<template>
  <view class="workspace-shell committee-page">
    <AppNavBar title="安委会巡检" @back="runtime.navigateBack()" />
    <scroll-view class="workspace-scroll" scroll-y enable-flex :style="scrollStyle">
      <view class="committee-content">
        <view class="committee-context">
          <text v-if="id" class="committee-title">修改本人巡检</text>
          <view class="committee-meta-row"><text class="committee-meta-label">检查人</text><text>{{auth.state.user?.realName||auth.state.user?.username}}</text></view>
          <view class="committee-meta-row"><text class="committee-meta-label">检查时间</text><text>{{inspectedAt?committeeDate(inspectedAt):'提交成功时自动记录'}}</text></view>
        </view>
        <view v-if="error" class="committee-error">{{error}}</view>
        <view class="committee-card">
          <view class="committee-field">
            <view class="committee-field-head"><text class="committee-field-label">安全隐患分类</text><text class="committee-field-note committee-field-required">必选</text></view>
            <picker :range="categories" :value="Math.max(0,categories.indexOf(category))" @change="category=categories[Number($event.detail.value)]">
              <view class="committee-picker"><text class="committee-picker-label">{{category||'请选择分类'}}</text><text class="committee-chevron"></text></view>
            </picker>
          </view>
          <view class="committee-field">
            <view class="committee-field-head"><text class="committee-field-label">检查结论</text><text class="committee-field-note">选填</text></view>
            <textarea class="committee-textarea" v-model="conclusion" :maxlength="2000" placeholder="填写现场检查情况…" />
            <text class="committee-muted committee-counter">{{conclusion.length}} / 2000</text>
          </view>
        </view>
        <view class="committee-card">
          <view class="committee-field-head"><text class="committee-field-label">现场附件</text><text class="committee-field-note">选填 · {{files.length}} / 30</text></view>
          <text class="committee-muted">照片 15 MB、办公文件 100 MB、视频 500 MB</text>
          <view class="committee-actions attachment-actions">
            <button class="committee-button attachment-add" @tap="capture"><text class="attachment-plus" aria-hidden="true">＋</text><text>现场拍摄</text></button>
            <button class="committee-button attachment-add" @tap="choose('album')"><text class="attachment-plus" aria-hidden="true">＋</text><text>相册</text></button>
            <button class="committee-button attachment-add" @tap="choose('chat')"><text class="attachment-plus" aria-hidden="true">＋</text><text>选择文件</text></button>
          </view>
          <view class="committee-attachment-grid">
          <view v-for="item in files" :key="item.key" class="committee-attachment-card">
            <CommitteeThumbnail :attachment="item.attachment" :local-file="item.file" large @open="openAttachment(item)" />
            <view class="committee-attachment-copy">
              <text class="committee-file-name">{{item.file?.name||item.attachment?.fileName}}</text>
              <text class="committee-muted">{{item.state==='done'?'上传完成':item.message}}</text>
              <view class="committee-actions">
                <button v-if="item.state==='failed'" class="committee-button committee-action-button" @tap="upload(item)">重试续传</button>
                <button v-if="item.state==='uploading'" class="committee-button committee-action-button" @tap="item.control&&(item.control.cancelled=true);item.control?.abort?.()">暂停</button>
                <button class="committee-button committee-action-button" @tap="remove(item)">移除</button>
              </view>
            </view>
          </view>
          </view>
        </view>
        <button class="committee-button committee-primary committee-submit" :class="{'committee-button-disabled':saving||files.some(f=>f.state!=='done')}" :disabled="saving||files.some(f=>f.state!=='done')" @tap="save">{{saving?'正在保存…':'提交保存'}}</button>
      </view>
    </scroll-view>
    <CommitteePreview v-if="preview?.attachment" :key="preview.key" :attachment="preview.attachment" :local-file="preview.file" :can-retry="true" @updated="a=>{const item=files.find(f=>f.attachment?.id===a.id);if(item)item.attachment=a;}" @close="preview=undefined" />
    <CommitteeLocalPreview v-else-if="preview?.file" :key="preview.key" :file="preview.file" @close="preview=undefined" />
  </view>
</template>
<style scoped src="../../styles/workspace-page.css"></style>
<style scoped src="./committee.css"></style>
<style scoped>
.attachment-actions { flex-wrap: nowrap; gap: 12rpx; }
.attachment-add { flex: 1; flex-direction: column; gap: 10rpx; min-height: 140rpx; padding: 18rpx 4rpx; border: 1rpx dashed #9bb6ca; background: #f1f7fb; white-space: nowrap; }
.attachment-plus { display: flex; align-items: center; justify-content: center; width: 52rpx; height: 52rpx; border-radius: 50%; background: #dfedf7; color: var(--workspace-accent-deep); font-size: 40rpx; font-weight: 400; line-height: 1; }
</style>
