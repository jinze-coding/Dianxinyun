<script setup lang="ts">
const runtime=uni;
import { computed, ref } from 'vue';
import { onShow, onHide, onUnload } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import CommitteeThumbnail from '@/components/CommitteeThumbnail.vue';
import CommitteePreview from '@/components/CommitteePreview.vue';
import AppTabBar from '@/components/AppTabBar.vue';
import { useAuthStore } from '@/stores/auth';
import { useProjectStore } from '@/stores/project';
import { committeeApi, committeeDate, committeeAccessLost, activeCommitteeFiles, type CommitteeAttachment, type CommitteePage } from '@/api/safetyCommittee';
const auth=useAuthStore();const projects=useProjectStore();
const categories=ref<string[]>([]);const category=ref('');const page=ref(1);const data=ref<CommitteePage>({records:[],total:0,latestId:null});
const preview=ref<CommitteeAttachment>();const error=ref('');const lastSync=ref('');const hasNew=ref(false);const latest=ref<number|null>(null);let timer:ReturnType<typeof setInterval>|undefined;let visible=false;let loading=false;let generation=0;
const projectId=computed(()=>projects.state.currentProjectId);const canSubmit=computed(()=>auth.hasProjectPermission(projectId.value,'safety_committee.submit'));
const authorizedProjects=computed(()=>projects.state.projects.filter(p=>auth.hasProjectPermission(p.id,'safety_committee.view')));
const currentProject=computed(()=>projects.state.projects.find(p=>p.id===projectId.value));
async function refresh(){if(!visible||loading||!projectId.value)return;loading=true;const current=++generation;const requestedProject=projectId.value;const requestedCategory=category.value;const requestedPage=page.value;
 try{const next=await committeeApi.list(projectId.value,category.value,page.value);if(!visible||current!==generation||projectId.value!==requestedProject||category.value!==requestedCategory||page.value!==requestedPage)return;
 if(page.value>1&&latest.value!==null&&latest.value!==next.latestId)hasNew.value=true;else{data.value=next;latest.value=next.latestId;}
 lastSync.value=new Date().toLocaleTimeString('zh-CN',{hour12:false});error.value='';
 }catch(e){if(visible&&current===generation&&projectId.value===requestedProject){error.value=(e as Error).message;if(committeeAccessLost(e))data.value={records:[],total:0,latestId:null};}}finally{loading=false;}}
function stop(){visible=false;generation++;clearInterval(timer);}
onShow(async()=>{visible=true;uni.hideTabBar({fail:()=>undefined});if(!await auth.ensureRootAccess('/pages/safety-committee/index'))return;
 await projects.loadProjects();if(!auth.hasProjectPermission(projectId.value,'safety_committee.view')&&authorizedProjects.value.length)projects.setCurrentProject(authorizedProjects.value[0].id);
 try{categories.value=await committeeApi.categories(projectId.value);}catch(e){error.value=(e as Error).message;committeeAccessLost(e);}
 await refresh();clearInterval(timer);timer=setInterval(refresh,5000);});onHide(stop);onUnload(stop);
function newest(){page.value=1;latest.value=null;hasNew.value=false;void refresh();}
function filter(e:any){category.value=['',...categories.value][Number(e.detail.value)]||'';newest();}
function selectProject(e:any){projects.setCurrentProject(authorizedProjects.value[Number(e.detail.value)].id);data.value={records:[],total:0,latestId:null};newest();}
function changePage(step:number){page.value+=step;void refresh();uni.pageScrollTo({scrollTop:0,duration:0});}
function open(id:number){uni.navigateTo({url:`/pages/safety-committee/detail?id=${id}`});}
</script>
<template><view class="committee-page"><AppNavBar title="安委会巡检" :show-back="false"/><view class="committee-content">
  <view class="committee-card"><view class="committee-row"><picker :range="authorizedProjects" range-key="projectName" @change="selectProject"><view class="committee-picker">{{currentProject?.projectName||'请选择施工区域'}} ▾</view></picker><button v-if="canSubmit" class="committee-primary" @tap="runtime.navigateTo({url:`/pages/safety-committee/edit?projectId=${projectId}`})">上报巡检</button></view><text class="committee-muted">共享项目现场记录，按检查时间倒序展示</text></view>
  <view v-if="error" class="committee-error">{{error}}<text v-if="lastSync"> · 上次更新 {{lastSync}}</text></view>
  <view class="committee-card"><picker :range="['全部分类',...categories]" @change="filter"><view class="committee-picker">{{category||'全部安全隐患分类'}} ▾</view></picker><text class="committee-muted">{{lastSync?`${lastSync} 更新 · 每 5 秒同步`:'正在加载记录…'}}</text></view>
  <button v-if="hasNew" @tap="newest">有新记录，点击查看最新</button>
  <view v-for="r in data.records" :key="r.id" class="committee-card" @tap="open(r.id)"><view class="committee-row"><text>{{r.inspectorName}}</text><text class="committee-muted">查看详情 ›</text></view><text class="committee-muted">{{committeeDate(r.inspectedAt)}}</text><text class="committee-tag">{{r.category}}</text><text class="committee-excerpt">{{r.conclusion||'未填写检查结论'}}</text><view class="committee-thumbnails"><CommitteeThumbnail v-for="a in activeCommitteeFiles(r).slice(0,3)" :key="a.id" :attachment="a" @open="preview=a"/><text class="committee-muted">{{activeCommitteeFiles(r).length}} 个附件</text></view></view>
  <view v-if="!data.records.length" class="committee-empty">暂无巡检记录</view><view class="committee-row committee-pagination"><button :disabled="page<=1" @tap="changePage(-1)">上一页</button><text class="committee-muted">{{page}} / {{Math.max(1,Math.ceil(data.total/20))}} · {{data.total}} 条</text><button :disabled="page*20>=data.total" @tap="changePage(1)">下一页</button></view>
</view><AppTabBar active="committee"/><CommitteePreview v-if="preview" :key="preview.id" :attachment="preview" @close="preview=undefined"/></view></template>
<style>@import "./committee.css";</style>
