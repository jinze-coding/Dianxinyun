<script setup lang="ts">
import CorrectionNotice from '@/components/CorrectionNotice.vue';
const runtime=uni;
import { ref } from 'vue';import { onLoad,onShow,onHide,onUnload } from '@dcloudio/uni-app';
import CommitteeThumbnail from '@/components/CommitteeThumbnail.vue';
import AppNavBar from '@/components/AppNavBar.vue';import CommitteePreview from '@/components/CommitteePreview.vue';
import { useAuthStore } from '@/stores/auth';import { request } from '@/api/request';
import { usePageScrollHeight } from '@/utils/navLayout';
import { committeeApi,committeeDate,committeeSize,committeeAccessLost,activeCommitteeFiles,type CommitteeRecord,type CommitteeAttachment } from '@/api/safetyCommittee';
const auth=useAuthStore();const id=ref(0);const record=ref<CommitteeRecord>();const preview=ref<CommitteeAttachment>();const error=ref('');const expanded=ref<number>();let timer:ReturnType<typeof setInterval>|undefined;let visible=false;let loading=false;
const {scrollStyle}=usePageScrollHeight({bottomRpx:0,minHeight:180});
async function refresh(){if(!id.value||!visible||loading)return;loading=true;try{const next=await committeeApi.detail(id.value);if(visible)record.value=next;}catch(e){error.value=(e as Error).message;if(committeeAccessLost(e)){record.value=undefined;preview.value=undefined;}}finally{loading=false;}}
onLoad(o=>{id.value=Number(o?.id)||0;void refresh();});onShow(async()=>{visible=true;if(!await auth.ensureRootAccess('/pages/safety-committee/index'))return;await refresh();clearInterval(timer);timer=setInterval(refresh,5000);});
function stop(){visible=false;clearInterval(timer);}onHide(stop);onUnload(stop);
function snapshot(raw:string){try{return JSON.parse(raw) as {category:string;conclusion:string;attachmentIds:number[]};}catch{return null;}}
function historyFiles(raw:string){return record.value?.attachments.filter(a=>snapshot(raw)?.attachmentIds?.includes(a.id))||[];}
async function remove(){try{const impact=await request<any>('/system/deletions/preview',{method:'POST',data:{targetType:'COMMITTEE_INSPECTION',targetId:id.value}});
 const content=`${impact.targetName}\n${impact.items.map((i:any)=>`${i.label}：${i.count}`).join('\n')}\n文件：${impact.fileCount} 个\n永久删除无法恢复，请确认已核对以上影响。`;
 const result=await new Promise<UniApp.ShowModalRes>(resolve=>uni.showModal({title:'删除影响确认',content,confirmText:'已核对删除',confirmColor:'#c34444',success:resolve}));
 if(!result.confirm)return;await request('/system/deletions/execute',{method:'POST',data:{targetType:impact.targetType,targetId:id.value,confirmationToken:impact.confirmationToken,acknowledged:true}});uni.navigateBack();
 }catch(e){error.value=(e as Error).message;}}
</script>
<template>
  <view class="workspace-shell committee-page">
    <AppNavBar title="安委会巡检" @back="runtime.navigateBack({fail:()=>runtime.reLaunch({url:'/pages/safety-committee/index'})})" />
    <scroll-view class="workspace-scroll" scroll-y enable-flex :style="scrollStyle">
    <CorrectionNotice :record="record" />
      <view class="committee-content">
        <view v-if="error" class="committee-error">{{error}}</view>
        <template v-if="record">
          <view class="committee-context">
            <text class="committee-title">{{record.inspectorName}}的巡检记录</text>
            <text class="committee-muted">{{committeeDate(record.inspectedAt)}} · 北京时间</text>
            <text class="committee-muted">第 {{record.version}} 版</text>
            <text class="committee-tag">{{record.category}}</text>
          </view>
          <view class="committee-card">
            <text class="committee-section-title">检查结论</text>
            <text class="committee-conclusion">{{record.conclusion||'未填写检查结论'}}</text>
          </view>
          <view class="committee-card">
            <view class="committee-field-head"><text class="committee-section-title">现场附件</text><text class="committee-field-note">{{activeCommitteeFiles(record).length}} 个</text></view>
            <view v-for="a in activeCommitteeFiles(record)" :key="a.id" class="committee-file" @tap="preview=a">
              <CommitteeThumbnail :attachment="a" @open="preview=a" />
              <view class="committee-file-copy"><text class="committee-file-name">{{a.fileName}}</text><text class="committee-muted">{{committeeSize(a.fileSize)}} · {{a.previewStatus==='READY'?'点击预览':a.previewStatus==='FAILED'?'预览失败，可下载原件':'预览处理中'}}</text></view>
              <text class="committee-arrow"></text>
            </view>
            <text v-if="!activeCommitteeFiles(record).length" class="committee-muted">无现场附件</text>
          </view>
          <view class="committee-card">
            <text class="committee-section-title">修改记录</text>
            <view v-for="log in record.logs" :key="log.id" class="committee-log">
              <text class="committee-log-title">{{log.operatorName}} · {{log.action==='CREATE'?'提交巡检':log.action==='EDIT'?'修改巡检':log.action==='ATTACHMENT_ROTATE'?'调整附件角度':'重试预览'}}</text>
              <text class="committee-muted">{{committeeDate(log.createTime)}}</text>
              <button v-if="log.action==='EDIT'" class="committee-button committee-log-button" @tap="expanded=expanded===log.id?undefined:log.id">{{expanded===log.id?'收起':'查看修改内容'}}</button>
              <template v-if="expanded===log.id">
                <view v-for="part in [{label:'修改前',raw:log.beforeJson},{label:'修改后',raw:log.afterJson}]" :key="part.label" class="committee-history-part">
                  <text class="committee-tag">{{part.label}}</text>
                  <text class="committee-muted">{{snapshot(part.raw)?.category}}</text>
                  <text class="committee-conclusion">{{snapshot(part.raw)?.conclusion||'未填写结论'}}</text>
                  <view v-for="a in historyFiles(part.raw)" :key="a.id" class="committee-file" @tap="preview=a">
                    <view class="committee-file-copy"><text class="committee-file-name">{{a.fileName}}</text><text class="committee-muted">{{a.status==='HISTORICAL'?'历史附件':'当前附件'}}</text></view>
                    <text class="committee-arrow"></text>
                  </view>
                </view>
              </template>
            </view>
          </view>
          <view v-if="record.canEdit||record.canDelete" class="committee-actions">
            <button v-if="record.canEdit" class="committee-button committee-action-button committee-primary" @tap="runtime.navigateTo({url:`/pages/safety-committee/edit?id=${record.id}`})">修改本人记录</button>
            <button v-if="record.canDelete" class="committee-button committee-action-button committee-danger" @tap="remove">永久删除</button>
          </view>
        </template>
      </view>
    </scroll-view>
    <CommitteePreview v-if="preview" :key="preview.id" :attachment="preview" :can-retry="record?.canEdit||record?.canDelete" @updated="a=>{if(record)record.attachments=record.attachments.map(f=>f.id===a.id?a:f);}" @close="preview=undefined" />
  </view>
</template>
<style scoped src="../../styles/workspace-page.css"></style>
<style scoped src="./committee.css"></style>
