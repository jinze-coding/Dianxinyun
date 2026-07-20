<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import AppTabBar from '@/components/AppTabBar.vue';
import WorkspaceAreaSwitcher from '@/components/workspace/WorkspaceAreaSwitcher.vue';
import WorkspaceMetricStrip, { type WorkspaceMetric } from '@/components/workspace/WorkspaceMetricStrip.vue';
import WorkspaceSegmentControl from '@/components/workspace/WorkspaceSegmentControl.vue';
import WorkspaceStatusPill from '@/components/workspace/WorkspaceStatusPill.vue';
import { completeSafetyEducation, createPersonnel, createSafetyEducation, enterPersonnel, exitPersonnel, getPersonnelCertificates, getPersonnelMovements, getPersonnelSummary } from '@/api/personnel';
import { useProjectStore } from '@/stores/project';
import type { PersonnelCertificate, PersonnelMovement, PersonnelPerson, PersonnelSummary } from '@/types';
import { usePageScrollHeight } from '@/utils/navLayout';
import { showToast } from '@/utils/navigation';

type PersonnelTab = 'ledger' | 'education' | 'training';
type SheetMode = 'add' | 'person' | 'education' | null;

const ACCENT = '#2F877B';
const TINT = '#EAF6F2';
const projectStore = useProjectStore();
const summary = ref<PersonnelSummary | null>(null);
const loading = ref(false);
const errorMessage = ref('');
const activeTab = ref<PersonnelTab>('ledger');
const keyword = ref('');
const sheetMode = ref<SheetMode>(null);
const selectedPerson = ref<PersonnelPerson | null>(null);
const submitting = ref(false);
const movements = ref<PersonnelMovement[]>([]);
const certificates = ref<PersonnelCertificate[]>([]);
const educationPersonIds = ref<number[]>([]);
const { scrollStyle } = usePageScrollHeight({ bottomRpx: 124, minHeight: 320 });

const personForm = reactive({ name: '', gender: '男', phone: '', idcard: '', unit: '', role: '', remark: '' });
const educationForm = reactive({ batchName: '', trainingDate: '', trainingPlace: '', trainer: '', remark: '' });
const projects = computed(() => projectStore.state.projects);
const currentProject = computed(() => projects.value.find((item) => item.id === projectStore.state.currentProjectId));
const canManage = computed(() => Boolean(summary.value?.canManage));
const filteredPeople = computed(() => {
  const text = keyword.value.trim().toLowerCase();
  return (summary.value?.people || []).filter((person) => {
    if (activeTab.value === 'education' && person.status !== 'WAIT_EDUCATION') return false;
    if (!text) return true;
    return `${person.name}${person.team || ''}${person.trade || ''}`.toLowerCase().includes(text);
  });
});
const metrics = computed<WorkspaceMetric[]>(() => [
  { label: '在场', value: summary.value?.onsiteCount || 0 },
  { label: '今日进场', value: summary.value?.todayEntryCount || 0, tone: 'green' },
  { label: '待教育', value: summary.value?.pendingEducationCount || 0, tone: 'amber' },
  { label: '证件预警', value: summary.value?.certificateWarningCount || 0, tone: 'red' }
]);
const tabs = computed(() => [
  { value: 'ledger', label: '人员台账', badge: (summary.value?.people || []).filter((person) => person.status !== 'LEFT').length },
  { value: 'education', label: '待教育', badge: summary.value?.pendingEducationCount || 0 },
  { value: 'training', label: '培训记录', badge: summary.value?.trainings.length || 0 }
]);

function hideNativeTabBar() { uni.hideTabBar({ animation: false, fail: () => undefined }); }
onShow(async () => { hideNativeTabBar(); await refresh(); });

async function refresh() {
  loading.value = true;
  errorMessage.value = '';
  try {
    await projectStore.loadProjects();
    if (!projectStore.state.currentProjectId) { summary.value = null; return; }
    summary.value = await getPersonnelSummary(projectStore.state.currentProjectId);
  } catch (error) {
    summary.value = null;
    errorMessage.value = error instanceof Error ? error.message : '人员数据加载失败';
  } finally { loading.value = false; }
}

async function selectProject(projectId: number) {
  projectStore.setCurrentProject(projectId);
  keyword.value = '';
  await refresh();
}

function setTab(value: string) { activeTab.value = value as PersonnelTab; }
function tone(status: string) { return status === 'EDUCATED' ? 'green' as const : status === 'WAIT_EDUCATION' ? 'amber' as const : 'gray' as const; }

function openAdd() {
  Object.assign(personForm, { name: '', gender: '男', phone: '', idcard: '', unit: '', role: '', remark: '' });
  sheetMode.value = 'add';
}

async function openPerson(person: PersonnelPerson) {
  selectedPerson.value = person;
  movements.value = [];
  certificates.value = [];
  sheetMode.value = 'person';
  try {
    const [movementResult, certificateResult] = await Promise.all([
      getPersonnelMovements(person.id),
      getPersonnelCertificates(currentProject.value?.id || 0, person.id)
    ]);
    movements.value = movementResult;
    certificates.value = certificateResult;
  } catch { movements.value = []; certificates.value = []; }
}

function openEducation() {
  const date = new Date();
  const day = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  Object.assign(educationForm, { batchName: `${currentProject.value?.shortName || '施工区域'}新进人员三级教育`, trainingDate: day, trainingPlace: '项目会议室', trainer: '', remark: '' });
  educationPersonIds.value = (summary.value?.people || []).filter((person) => person.status === 'WAIT_EDUCATION').map((person) => person.id);
  sheetMode.value = 'education';
}

async function submitPerson() {
  if (!currentProject.value || !personForm.name.trim()) { showToast('请填写人员姓名'); return; }
  submitting.value = true;
  try {
    await createPersonnel({ projectId: currentProject.value.id, ...personForm, name: personForm.name.trim(), entryTime: new Date().toISOString().slice(0, 19), status: '待教育' });
    showToast('人员已新增'); sheetMode.value = null; await refresh();
  } catch (error) { showToast(error instanceof Error ? error.message : '新增失败'); }
  finally { submitting.value = false; }
}

async function changePersonStatus(action: 'ENTRY' | 'EXIT') {
  if (!selectedPerson.value) return;
  submitting.value = true;
  try {
    if (action === 'ENTRY') await enterPersonnel(selectedPerson.value.id);
    else await exitPersonnel(selectedPerson.value.id);
    showToast(action === 'ENTRY' ? '已重新办理进场' : '已办理离场');
    sheetMode.value = null; await refresh();
  } catch (error) { showToast(error instanceof Error ? error.message : '状态更新失败'); }
  finally { submitting.value = false; }
}

async function submitEducation() {
  if (!currentProject.value) return;
  const personIds = educationPersonIds.value;
  if (!personIds.length) { showToast('当前没有待教育人员'); return; }
  if (!educationForm.batchName.trim() || !educationForm.trainingDate || !educationForm.trainer.trim()) { showToast('请填写培训名称、日期和讲师'); return; }
  submitting.value = true;
  try {
    await createSafetyEducation({ projectId: currentProject.value.id, batchName: educationForm.batchName.trim(), eduType: '三级安全教育', trainingTime: `${educationForm.trainingDate}T09:00:00`, trainingPlace: educationForm.trainingPlace, trainer: educationForm.trainer.trim(), personIds, remark: educationForm.remark });
    showToast(`已创建培训，关联${personIds.length}人`); sheetMode.value = null; activeTab.value = 'training'; await refresh();
  } catch (error) { showToast(error instanceof Error ? error.message : '教育发起失败'); }
  finally { submitting.value = false; }
}

async function completeTraining(id: number) {
  submitting.value = true;
  try { await completeSafetyEducation(id); showToast('培训已完成'); await refresh(); }
  catch (error) { showToast(error instanceof Error ? error.message : '操作失败'); }
  finally { submitting.value = false; }
}

function formatTime(value?: string) { return value ? value.replace('T', ' ').slice(0, 16) : '时间未设置'; }
</script>

<template>
  <view class="workspace-shell personnel-page" :style="{ '--page-accent': ACCENT, '--page-accent-deep': '#246D64', '--page-tint': TINT, '--page-background': '#F2F7F5' }">
    <AppNavBar title="人员管理" :show-back="false" />
    <scroll-view class="workspace-scroll" scroll-y enable-flex :style="scrollStyle">
      <view class="workspace-content">
        <WorkspaceAreaSwitcher :project="currentProject" :projects="projects" :accent="ACCENT" :tint="TINT" @select="selectProject" />
        <view v-if="loading && !summary" class="state-panel"><text class="state-title">正在加载人员数据</text></view>
        <view v-else-if="errorMessage" class="state-panel"><text class="state-title">人员数据加载失败</text><text class="state-desc">{{ errorMessage }}</text><button class="retry-button" @tap="refresh">重新加载</button></view>
        <template v-else-if="summary && currentProject">
          <WorkspaceMetricStrip :metrics="metrics" :accent="ACCENT" :motion-key="currentProject.id" />
          <view class="quick-actions personnel-actions">
            <button :disabled="!canManage" @tap="openAdd"><view class="action-icon-wrap"><image src="/static/design-preview-icons/personnel-add.png" mode="aspectFit" /></view><text>新增人员</text></button>
            <button :disabled="!canManage" @tap="showToast('点击人员即可办理进退场')"><view class="action-icon-wrap"><image src="/static/design-preview-icons/personnel-entry.png" mode="aspectFit" /></view><text>办理进退场</text></button>
            <button :disabled="!canManage" @tap="openEducation"><view class="action-icon-wrap"><image src="/static/design-preview-icons/personnel-education.png" mode="aspectFit" /></view><text>发起教育</text></button>
          </view>
          <view class="section-block list-section">
            <WorkspaceSegmentControl :model-value="activeTab" :options="tabs" :accent="ACCENT" :tint="TINT" @update:model-value="setTab" />
            <view v-if="activeTab !== 'training'" class="search-box"><text class="search-icon"></text><input v-model="keyword" class="search-input" placeholder="搜索姓名、班组、工种" placeholder-class="search-placeholder" /></view>
            <view v-if="activeTab !== 'training'" class="plain-list">
              <button v-for="person in filteredPeople" :key="person.id" class="plain-row" @tap="openPerson(person)"><view class="person-avatar">{{ person.name.slice(0, 1) }}</view><view class="plain-copy"><text class="plain-title">{{ person.name }}</text><text class="plain-meta">{{ person.trade || '未设置工种' }} · {{ person.team || '未设置班组' }}</text></view><WorkspaceStatusPill :label="person.statusLabel" :tone="tone(person.status)" /><text class="row-arrow"></text></button>
              <view v-if="!filteredPeople.length" class="empty-line">当前分类暂无人员</view>
            </view>
            <view v-else class="plain-list">
              <view v-for="training in summary.trainings" :key="training.id" class="plain-row training-row"><view class="training-date">{{ training.personCount }}</view><view class="plain-copy"><text class="plain-title">{{ training.title }}</text><text class="plain-meta">{{ training.personCount }} 人 · {{ formatTime(training.trainingTime) }}</text></view><button v-if="canManage && training.status !== 'COMPLETED'" class="complete-button" @tap="completeTraining(training.id)">完成</button><WorkspaceStatusPill v-else :label="training.statusLabel || training.status" :tone="training.status === 'COMPLETED' ? 'green' : 'blue'" /></view>
              <view v-if="!summary.trainings.length" class="empty-line">暂无培训记录</view>
            </view>
          </view>
        </template>
      </view>
    </scroll-view>
    <AppTabBar active="personnel" />

    <view v-if="sheetMode" class="form-overlay" @tap="sheetMode = null">
      <view class="form-sheet" @tap.stop>
        <view class="sheet-handle"></view>
        <view class="form-head"><text class="form-title">{{ sheetMode === 'add' ? '新增人员' : sheetMode === 'education' ? '发起三级教育' : '人员详情' }}</text><button class="form-close" @tap="sheetMode = null">×</button></view>
        <template v-if="sheetMode === 'add'">
          <view class="form-field"><text class="form-label">姓名 *</text><input v-model="personForm.name" class="form-input" placeholder="请输入姓名" /></view>
          <view class="form-grid"><view class="form-field"><text class="form-label">班组</text><input v-model="personForm.unit" class="form-input" placeholder="所属班组" /></view><view class="form-field"><text class="form-label">工种</text><input v-model="personForm.role" class="form-input" placeholder="人员工种" /></view></view>
          <view class="form-field"><text class="form-label">手机号</text><input v-model="personForm.phone" class="form-input" type="number" placeholder="手机号" /></view>
          <view class="form-field"><text class="form-label">身份证号</text><input v-model="personForm.idcard" class="form-input" placeholder="身份证号" /></view>
          <view class="form-actions"><button class="secondary-action" @tap="sheetMode = null">取消</button><button class="primary-action" :disabled="submitting" @tap="submitPerson">{{ submitting ? '提交中' : '确认新增' }}</button></view>
        </template>
        <template v-else-if="sheetMode === 'education'">
          <view class="form-field"><text class="form-label">培训名称 *</text><input v-model="educationForm.batchName" class="form-input" /></view>
          <view class="form-field"><text class="form-label">培训日期 *</text><picker mode="date" :value="educationForm.trainingDate" @change="educationForm.trainingDate = ($event.detail as { value: string }).value"><view class="form-picker">{{ educationForm.trainingDate || '选择日期' }}</view></picker></view>
          <view class="form-grid"><view class="form-field"><text class="form-label">培训地点</text><input v-model="educationForm.trainingPlace" class="form-input" /></view><view class="form-field"><text class="form-label">培训讲师 *</text><input v-model="educationForm.trainer" class="form-input" /></view></view>
          <view class="education-notice">已选择 {{ educationPersonIds.length }} 名待教育人员</view>
          <view class="education-people"><label v-for="person in (summary?.people || []).filter(item => item.status === 'WAIT_EDUCATION')" :key="person.id"><checkbox :checked="educationPersonIds.includes(person.id)" color="#2F877B" @tap="educationPersonIds = educationPersonIds.includes(person.id) ? educationPersonIds.filter(id => id !== person.id) : [...educationPersonIds, person.id]" />{{ person.name }} · {{ person.trade || '未设置工种' }}</label></view>
          <view class="form-actions"><button class="secondary-action" @tap="sheetMode = null">取消</button><button class="primary-action" :disabled="submitting" @tap="submitEducation">发起教育</button></view>
        </template>
        <template v-else-if="selectedPerson">
          <view class="person-detail-head"><view class="person-avatar large">{{ selectedPerson.name.slice(0, 1) }}</view><view><text class="person-detail-name">{{ selectedPerson.name }}</text><text class="person-detail-meta">{{ selectedPerson.trade || '未设置工种' }} · {{ selectedPerson.team || '未设置班组' }}</text></view></view>
          <view class="detail-lines"><view><text>手机号</text><text>{{ selectedPerson.maskedPhone || '未登记' }}</text></view><view><text>身份证</text><text>{{ selectedPerson.maskedIdcard || '未登记' }}</text></view><view><text>进场时间</text><text>{{ formatTime(selectedPerson.entryTime) }}</text></view><view><text>教育状态</text><text>{{ selectedPerson.statusLabel }}</text></view></view>
          <view class="detail-subsection"><text class="detail-subtitle">进退场记录</text><view v-for="item in movements" :key="item.id" class="detail-log"><text>{{ item.actionType === 'ENTRY' ? '进场' : '离场' }}</text><text>{{ formatTime(item.occurredAt) }}</text></view><text v-if="!movements.length" class="detail-empty">暂无记录</text></view>
          <view class="detail-subsection"><text class="detail-subtitle">特种作业/资格证件</text><view v-for="item in certificates" :key="item.id" class="detail-log"><view><text>{{ item.certificateType }}</text><text class="certificate-no">{{ item.certificateNo }}</text></view><WorkspaceStatusPill :label="item.warningLabel" :tone="item.warningLevel === 'EXPIRED' ? 'red' : item.warningLevel === 'WARNING' ? 'amber' : 'green'" /></view><text v-if="!certificates.length" class="detail-empty">暂无证件</text></view>
          <view v-if="canManage" class="form-actions"><button class="secondary-action" @tap="changePersonStatus(selectedPerson.status === 'LEFT' ? 'ENTRY' : 'EXIT')">{{ selectedPerson.status === 'LEFT' ? '重新进场' : '办理离场' }}</button></view>
        </template>
      </view>
    </view>
  </view>
</template>

<style scoped src="../../styles/workspace-page.css"></style>
<style scoped>
.personnel-actions { grid-template-columns: repeat(3,minmax(0,1fr)); }
.quick-actions button:disabled { opacity: .45; }
.list-section { padding-top: 18rpx; }
.list-section :deep(.segment-control) { margin: 0 20rpx; }
.person-avatar { display: flex; width: 62rpx; height: 62rpx; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 50%; background: var(--page-tint); color: var(--page-accent-deep); font-size: 25rpx; font-weight: 750; }
.person-avatar.large { width: 82rpx; height: 82rpx; font-size: 31rpx; }
.training-date { display: flex; width: 58rpx; height: 58rpx; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 13rpx; background: var(--page-tint); color: var(--page-accent-deep); font-size: 24rpx; font-weight: 800; }
.complete-button { min-height: 48rpx; padding: 0 15rpx; border-radius: 999rpx; background: var(--page-tint); color: var(--page-accent-deep); font-size: 20rpx; font-weight: 700; }
.complete-button::after { border: 0; }
.empty-line { padding: 44rpx 0; color: #98a2b3; font-size: 22rpx; text-align: center; }
.form-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 14rpx; }
.education-notice { padding: 18rpx; border-radius: 12rpx; background: var(--page-tint); color: var(--page-accent-deep); font-size: 22rpx; }
.education-people { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); max-height: 210rpx; gap: 10rpx; overflow: auto; margin-top: 12rpx; }.education-people label { display: flex; min-width: 0; align-items: center; gap: 7rpx; color: #526174; font-size: 21rpx; }
.person-detail-head { display: flex; align-items: center; gap: 18rpx; padding: 10rpx 0 24rpx; }
.person-detail-name, .person-detail-meta { display: block; }.person-detail-name { color: #1e293b; font-size: 30rpx; font-weight: 800; }.person-detail-meta { margin-top: 7rpx; color: #758296; font-size: 22rpx; }
.detail-lines { border-top: 1rpx solid #edf0f3; }.detail-lines view { display: flex; min-height: 70rpx; align-items: center; justify-content: space-between; gap: 24rpx; border-bottom: 1rpx solid #edf0f3; color: #758296; font-size: 22rpx; }.detail-lines view text:last-child { color: #263449; text-align: right; }
.detail-subsection { margin-top: 20rpx; padding-top: 16rpx; border-top: 1rpx solid #edf0f3; }.detail-subtitle { display: block; margin-bottom: 8rpx; color: #263449; font-size: 23rpx; font-weight: 750; }.detail-log { display: flex; min-height: 60rpx; align-items: center; justify-content: space-between; gap: 16rpx; color: #66758a; font-size: 21rpx; }.detail-log text { display: block; }.certificate-no { margin-top: 3rpx; color: #98a2b3; font-size: 19rpx; }.detail-empty { color: #98a2b3; font-size: 21rpx; }
</style>
