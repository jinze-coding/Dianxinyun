<script setup lang="ts">
const runtime=uni;
import { computed, nextTick, ref } from 'vue';
import { onShow, onHide, onUnload } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import CommitteeThumbnail from '@/components/CommitteeThumbnail.vue';
import CommitteePreview from '@/components/CommitteePreview.vue';
import AppTabBar from '@/components/AppTabBar.vue';
import WorkspaceAreaSwitcher from '@/components/workspace/WorkspaceAreaSwitcher.vue';
import WorkspaceAreaSheet from '@/components/workspace/WorkspaceAreaSheet.vue';
import { WORKSPACE_THEME } from '@/constants/workspaceTheme';
import { usePageScrollHeight } from '@/utils/navLayout';
import { useAuthStore } from '@/stores/auth';
import { useProjectStore } from '@/stores/project';
import { committeeApi, committeeDate, committeeAccessLost, activeCommitteeFiles, type CommitteeAttachment, type CommitteePage } from '@/api/safetyCommittee';
const auth=useAuthStore();const projects=useProjectStore();
const categories=ref<string[]>([]);const category=ref('');const page=ref(1);const data=ref<CommitteePage>({records:[],total:0,latestId:null});
const preview=ref<CommitteeAttachment>();const error=ref('');const lastSync=ref('');const hasNew=ref(false);const latest=ref<number|null>(null);let timer:ReturnType<typeof setInterval>|undefined;let visible=false;let loading=false;let generation=0;
const projectId=computed(()=>projects.state.currentProjectId);const canSubmit=computed(()=>auth.hasProjectPermission(projectId.value,'safety_committee.submit'));
const authorizedProjects=computed(()=>projects.state.projects.filter(p=>auth.hasProjectPermission(p.id,'safety_committee.view')));
const currentProject=computed(()=>projects.state.projects.find(p=>p.id===projectId.value));
const areaSheetOpen=ref(false);
const scrollTop=ref(0);
const { scrollStyle }=usePageScrollHeight({bottomRpx:124,minHeight:180});
async function refresh(){if(!visible||loading||!projectId.value)return;loading=true;const current=++generation;const requestedProject=projectId.value;const requestedCategory=category.value;const requestedPage=page.value;
 try{const next=await committeeApi.list(requestedProject,requestedCategory,requestedPage);if(!visible||current!==generation||projectId.value!==requestedProject||category.value!==requestedCategory||page.value!==requestedPage)return;
 if(page.value>1&&latest.value!==null&&latest.value!==next.latestId)hasNew.value=true;else{data.value=next;latest.value=next.latestId;}
 lastSync.value=new Date().toLocaleTimeString('zh-CN',{hour12:false});error.value='';
 }catch(e){if(visible&&current===generation&&projectId.value===requestedProject){error.value=(e as Error).message;if(committeeAccessLost(e))data.value={records:[],total:0,latestId:null};}}finally{if(current===generation)loading=false;}}
function stop(){visible=false;generation++;loading=false;clearInterval(timer);}
onShow(async()=>{visible=true;uni.hideTabBar({fail:()=>undefined});if(!await auth.ensureRootAccess('/pages/safety-committee/index'))return;
 await projects.loadProjects();if(!auth.hasProjectPermission(projectId.value,'safety_committee.view')&&authorizedProjects.value.length)projects.setCurrentProject(authorizedProjects.value[0].id);
 try{categories.value=await committeeApi.categories(projectId.value);}catch(e){error.value=(e as Error).message;committeeAccessLost(e);}
 await refresh();clearInterval(timer);timer=setInterval(refresh,5000);});onHide(stop);onUnload(stop);
async function scrollToTop(){await nextTick();scrollTop.value=0;}
function reload(){generation++;loading=false;void refresh();}
function newest(){page.value=1;latest.value=null;hasNew.value=false;reload();void scrollToTop();}
function filter(e:any){category.value=['',...categories.value][Number(e.detail.value)]||'';newest();}
function selectProject(id:number){projects.setCurrentProject(id);data.value={records:[],total:0,latestId:null};newest();}
function changePage(step:number){page.value+=step;reload();void scrollToTop();}
function open(id:number){uni.navigateTo({url:`/pages/safety-committee/detail?id=${id}`});}
</script>
<template>
  <view class="workspace-shell committee-page">
    <AppNavBar title="安委会巡检" :show-back="false" />
    <scroll-view class="workspace-scroll" scroll-y enable-flex :style="scrollStyle" :scroll-top="scrollTop" @scroll="scrollTop=$event.detail.scrollTop">
      <view class="committee-content">
        <WorkspaceAreaSwitcher :project="currentProject" :projects="authorizedProjects" :accent="WORKSPACE_THEME.accent" :tint="WORKSPACE_THEME.tint" :open="areaSheetOpen" @open="areaSheetOpen=true" />
        <button v-if="canSubmit" class="committee-report" @tap="runtime.navigateTo({url:`/pages/safety-committee/edit?projectId=${projectId}`})">
          <view class="committee-report-icon"><image class="committee-report-image" src="/static/design-preview-icons/quality-inspect.png" mode="aspectFit" /></view>
          <view class="committee-report-copy"><text class="committee-report-title">提交巡检</text><text class="committee-report-desc">记录现场检查情况，支持照片、视频和文件</text></view>
          <text class="committee-arrow committee-arrow-light"></text>
        </button>
        <view v-if="error" class="committee-error">{{error}}<text v-if="lastSync"> · 上次更新 {{lastSync}}</text></view>
        <view class="committee-card committee-filter-card">
          <view class="committee-row">
            <view class="committee-list-heading"><text class="committee-section-title">巡检记录</text><text class="committee-count">{{data.total}} 条</text></view>
            <picker class="committee-filter" :range="['全部分类',...categories]" :value="Math.max(0,categories.indexOf(category)+1)" @change="filter">
              <view class="committee-filter-value"><text class="committee-filter-label">{{category||'全部分类'}}</text><text class="committee-chevron"></text></view>
            </picker>
          </view>
          <text class="committee-sync">{{lastSync?`自动同步 · 最近更新 ${lastSync}`:'正在加载记录…'}}</text>
        </view>
        <button v-if="hasNew" class="committee-button committee-new" @tap="newest">有新记录，点击刷新列表</button>
        <view v-for="r in data.records" :key="r.id" class="committee-card committee-record" @tap="open(r.id)">
          <view class="committee-row"><text class="committee-record-name">{{r.inspectorName}}</text><view class="committee-detail-link"><text>查看详情</text><text class="committee-arrow"></text></view></view>
          <text class="committee-record-date">{{committeeDate(r.inspectedAt)}}</text>
          <text class="committee-tag">{{r.category}}</text>
          <text class="committee-excerpt">{{r.conclusion||'未填写检查结论'}}</text>
          <view v-if="activeCommitteeFiles(r).length" class="committee-thumbnails"><CommitteeThumbnail v-for="a in activeCommitteeFiles(r).slice(0,3)" :key="a.id" :attachment="a" @open="preview=a"/><text class="committee-muted">{{activeCommitteeFiles(r).length}} 个附件</text></view>
        </view>
        <view v-if="!data.records.length&&!error" class="committee-card committee-empty">
          <view class="committee-empty-mark"><image class="committee-empty-image" src="/static/design-preview-icons/quality-inspect.png" mode="aspectFit" /></view>
          <text class="committee-empty-title">{{lastSync?'暂无巡检记录':'正在加载巡检记录'}}</text>
          <text v-if="lastSync" class="committee-empty-desc">{{category?'可切换其他隐患分类查看':canSubmit?'点击上方“提交巡检”，记录现场检查情况':'当前施工区域的巡检记录将在这里显示'}}</text>
        </view>
        <view v-if="data.total>20" class="committee-row committee-pagination"><button class="committee-button committee-page-button" :class="{'committee-button-disabled':page<=1}" :disabled="page<=1" @tap="changePage(-1)">上一页</button><text class="committee-muted">{{page}} / {{Math.max(1,Math.ceil(data.total/20))}}</text><button class="committee-button committee-page-button" :class="{'committee-button-disabled':page*20>=data.total}" :disabled="page*20>=data.total" @tap="changePage(1)">下一页</button></view>
      </view>
    </scroll-view>
    <WorkspaceAreaSheet :open="areaSheetOpen" :project="currentProject" :projects="authorizedProjects" :accent="WORKSPACE_THEME.accent" :tint="WORKSPACE_THEME.tint" @close="areaSheetOpen=false" @select="selectProject" />
    <AppTabBar v-if="!areaSheetOpen" active="committee" />
    <CommitteePreview v-if="preview" :key="preview.id" :attachment="preview" @close="preview=undefined" />
  </view>
</template>
<style scoped src="../../styles/workspace-page.css"></style>
<style scoped src="./committee.css"></style>
