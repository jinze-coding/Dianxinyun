<script setup lang="ts">
import { computed, nextTick, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import { usePageScrollHeight } from '@/utils/navLayout';
import { navigateTo, showToast, switchTab } from '@/utils/navigation';
import PreviewAreaSwitcher from './components/PreviewAreaSwitcher.vue';
import PreviewSegmentControl from './components/PreviewSegmentControl.vue';
import PreviewStatusPill from './components/PreviewStatusPill.vue';
import PreviewSummaryStrip from './components/PreviewSummaryStrip.vue';
import PreviewTabBar, { type PreviewPageKey } from './components/PreviewTabBar.vue';
import {
  previewAreas,
  roleProfiles,
  type PreviewQualityIssue,
  type PreviewRole,
  type PreviewSafetyTask,
  type PreviewTone
} from './previewData';

type OverviewPanel = 'info' | 'documents' | 'devices';
type PersonnelTab = 'ledger' | 'education' | 'training';
type QualityFilter = 'ALL' | PreviewQualityIssue['status'];
type SafetyFilter = 'ALL' | PreviewSafetyTask['type'];

interface PageTheme {
  accent: string;
  accentDeep: string;
  tint: string;
  tintStrong: string;
  page: string;
}

const pageTitleMap: Record<PreviewPageKey, string> = {
  overview: '项目概况',
  personnel: '人员管理',
  quality: '质量管理',
  safety: '安全管理',
  profile: '我的'
};

const pageThemes: Record<PreviewPageKey, PageTheme> = {
  overview: { accent: '#527AA3', accentDeep: '#3E6488', tint: '#EAF1F7', tintStrong: '#DDE8F1', page: '#F3F6F8' },
  personnel: { accent: '#2F877B', accentDeep: '#246D64', tint: '#EAF6F2', tintStrong: '#D8ECE7', page: '#F2F7F5' },
  quality: { accent: '#5B68A8', accentDeep: '#47558E', tint: '#EFF1FA', tintStrong: '#E0E4F4', page: '#F5F5F9' },
  safety: { accent: '#C07A32', accentDeep: '#9D6024', tint: '#FFF3E6', tintStrong: '#F7E3CB', page: '#F8F5F1' },
  profile: { accent: '#61778F', accentDeep: '#4D6278', tint: '#EDF2F7', tintStrong: '#DEE7EF', page: '#F3F5F7' }
};

const roleOptions: Array<{ value: PreviewRole; label: string; description: string }> = [
  { value: 'PROJECT_ADMIN', label: '项目管理员', description: '查看区域全量数据和管理入口' },
  { value: 'SAFETY_ADMIN', label: '项目安全员', description: '处理检查、复核和整改闭环' },
  { value: 'ELECTRICIAN', label: '负责电工', description: '执行巡检和本人整改任务' }
];

const activePage = ref<PreviewPageKey>('overview');
const currentAreaId = ref(previewAreas[0].id);
const previewRole = ref<PreviewRole>('SAFETY_ADMIN');
const cameraIndex = ref(0);
const overviewPanel = ref<OverviewPanel>('info');
const personnelTab = ref<PersonnelTab>('ledger');
const personnelSearch = ref('');
const qualityFilter = ref<QualityFilter>('ALL');
const safetyFilter = ref<SafetyFilter>('ALL');
const rolePickerOpen = ref(false);
const rolePickerClosing = ref(false);
const scrollTop = ref(0);
const pageMotionKey = ref(0);
const scanAnimating = ref(false);

const { scrollStyle } = usePageScrollHeight({ minHeight: 320, includeSafeBottom: false });

const currentArea = computed(() => previewAreas.find((item) => item.id === currentAreaId.value) || previewAreas[0]);
const currentCamera = computed(() => {
  const cameras = currentArea.value.overview.cameras;
  return cameras[cameraIndex.value % cameras.length];
});
const pageTitle = computed(() => pageTitleMap[activePage.value]);
const activeTheme = computed(() => pageThemes[activePage.value]);
const shellStyle = computed(() => ({
  '--page-accent': activeTheme.value.accent,
  '--page-accent-deep': activeTheme.value.accentDeep,
  '--page-tint': activeTheme.value.tint,
  '--page-tint-strong': activeTheme.value.tintStrong,
  '--page-background': activeTheme.value.page
}));
const currentProfile = computed(() => roleProfiles[previewRole.value]);
const canManage = computed(() => previewRole.value !== 'ELECTRICIAN');

const personnelTabs = computed(() => [
  { value: 'ledger' as const, label: '人员台账', badge: currentArea.value.personnel.people.filter((item) => item.status !== '已离场').length },
  { value: 'education' as const, label: '待教育', badge: currentArea.value.personnel.people.filter((item) => item.status === '待教育').length },
  { value: 'training' as const, label: '培训记录', badge: currentArea.value.personnel.trainings.length }
]);

const filteredPeople = computed(() => {
  const keyword = personnelSearch.value.trim().toLowerCase();
  return currentArea.value.personnel.people.filter((person) => {
    if (personnelTab.value === 'education' && person.status !== '待教育') return false;
    if (!keyword) return true;
    return `${person.name}${person.trade}${person.team}`.toLowerCase().includes(keyword);
  });
});

const qualityFilters: Array<{ value: QualityFilter; label: string }> = [
  { value: 'ALL', label: '全部' },
  { value: 'PENDING', label: '待整改' },
  { value: 'RECHECK', label: '待复查' },
  { value: 'CLOSED', label: '已关闭' }
];

const filteredQualityIssues = computed(() => currentArea.value.quality.issues.filter((issue) => {
  if (qualityFilter.value === 'ALL') return true;
  return issue.status === qualityFilter.value;
}));

const visibleSafetyTasks = computed(() => currentArea.value.safety.tasks.filter((task) => {
  if (previewRole.value === 'ELECTRICIAN' && ['REVIEW', 'RECHECK'].includes(task.type)) return false;
  if (safetyFilter.value === 'ALL') return true;
  return task.type === safetyFilter.value;
}));

const safetyTabs = computed(() => {
  const available = currentArea.value.safety.tasks.filter((task) => previewRole.value !== 'ELECTRICIAN' || !['REVIEW', 'RECHECK'].includes(task.type));
  return [
    { value: 'ALL' as const, label: previewRole.value === 'ELECTRICIAN' ? '我的任务' : '今日任务', badge: available.length },
    { value: 'REVIEW' as const, label: '待复核', badge: available.filter((item) => item.type === 'REVIEW').length },
    { value: 'RECTIFICATION' as const, label: '整改', badge: available.filter((item) => item.type === 'RECTIFICATION').length }
  ].filter((item) => previewRole.value !== 'ELECTRICIAN' || item.value !== 'REVIEW');
});

const todoCount = computed(() => {
  const safetyMetrics = currentArea.value.safety.metrics;
  return Number(safetyMetrics[2]?.value || 0) + Number(safetyMetrics[3]?.value || 0) + Number(currentArea.value.quality.metrics[1]?.value || 0);
});

onLoad((options) => {
  const storedAreaId = Number(uni.getStorageSync('site_platform_design_preview_area_id'));
  if (previewAreas.some((item) => item.id === storedAreaId)) {
    currentAreaId.value = storedAreaId;
  }

  const tab = options?.tab as PreviewPageKey | undefined;
  if (tab && Object.prototype.hasOwnProperty.call(pageTitleMap, tab)) {
    activePage.value = tab;
  }

  const areaId = Number(options?.areaId);
  if (previewAreas.some((item) => item.id === areaId)) {
    currentAreaId.value = areaId;
  }

  const role = options?.role as PreviewRole | undefined;
  if (role && Object.prototype.hasOwnProperty.call(roleProfiles, role)) {
    previewRole.value = role;
  }
});

function selectArea(areaId: number) {
  currentAreaId.value = areaId;
  cameraIndex.value = 0;
  uni.setStorageSync('site_platform_design_preview_area_id', areaId);
  showToast(`已切换到${currentArea.value.name}`);
}

async function selectPage(page: PreviewPageKey) {
  if (activePage.value === page) return;
  activePage.value = page;
  pageMotionKey.value += 1;
  scrollTop.value = 1;
  await nextTick();
  scrollTop.value = 0;
}

function cycleCamera() {
  cameraIndex.value = (cameraIndex.value + 1) % currentArea.value.overview.cameras.length;
}

function setOverviewPanel(value: string) {
  overviewPanel.value = value as OverviewPanel;
}

function setPersonnelTab(value: string) {
  personnelTab.value = value as PersonnelTab;
}

function setQualityFilter(value: string) {
  qualityFilter.value = value as QualityFilter;
}

function setSafetyFilter(value: string) {
  safetyFilter.value = value as SafetyFilter;
}

function previewAction(label: string) {
  showToast(`设计预览：${label}`);
}

function openSafetyRoute(route: 'scan' | 'ledger' | 'records' | 'summary') {
  const projectId = currentArea.value.id;
  if (route === 'scan') {
    scanAnimating.value = true;
    setTimeout(() => {
      scanAnimating.value = false;
      navigateTo('/pages/scan-entry/index?scene=B%3APUB-A1-001');
    }, 260);
    return;
  }
  if (route === 'ledger') {
    navigateTo(`/pages/electric-box/index?projectId=${projectId}`);
    return;
  }
  if (route === 'records') {
    navigateTo(`/pages/inspection/records?projectId=${projectId}`);
    return;
  }
  navigateTo(`/pages/summary/index?projectId=${projectId}`);
}

function personTone(status: string): PreviewTone {
  if (status === '已教育') return 'green';
  if (status === '待教育') return 'amber';
  return 'gray';
}

function qualityLabel(issue: PreviewQualityIssue) {
  if (issue.status === 'PENDING') return '待整改';
  if (issue.status === 'OVERDUE') return '已逾期';
  if (issue.status === 'RECHECK') return '待复查';
  return '已关闭';
}

function qualityTone(issue: PreviewQualityIssue): PreviewTone {
  if (issue.status === 'OVERDUE') return 'red';
  if (issue.status === 'PENDING') return 'amber';
  if (issue.status === 'RECHECK') return 'blue';
  return 'green';
}

function safetyTone(task: PreviewSafetyTask): PreviewTone {
  if (task.type === 'RECTIFICATION') return 'red';
  if (task.type === 'REVIEW' || task.type === 'RECHECK') return 'amber';
  return 'blue';
}

function selectRole(role: PreviewRole) {
  closeRolePicker(() => {
    previewRole.value = role;
    safetyFilter.value = 'ALL';
    showToast(`已切换${roleProfiles[role].roleLabel}预览`);
  });
}

function openRolePicker() {
  rolePickerClosing.value = false;
  rolePickerOpen.value = true;
}

function closeRolePicker(afterClose?: () => void) {
  if (!rolePickerOpen.value || rolePickerClosing.value) return;
  rolePickerClosing.value = true;
  setTimeout(() => {
    rolePickerOpen.value = false;
    rolePickerClosing.value = false;
    afterClose?.();
  }, 220);
}

function staggerStyle(index: number) {
  return { animationDelay: `${index * 24}ms` };
}
</script>

<template>
  <view class="preview-shell" :class="`page-${activePage}`" :style="shellStyle">
    <AppNavBar :title="pageTitle" :show-back="false" />

    <scroll-view
      class="preview-scroll"
      scroll-y
      enable-flex
      :scroll-top="scrollTop"
      :style="scrollStyle"
    >
      <view :key="pageMotionKey" class="preview-content page-motion">
        <template v-if="activePage === 'overview'">
          <PreviewAreaSwitcher :area="currentArea" :areas="previewAreas" :accent="activeTheme.accent" :tint="activeTheme.tint" @select="selectArea" />
          <PreviewSummaryStrip :metrics="currentArea.overview.metrics" :accent="activeTheme.accent" :tint="activeTheme.tint" :motion-key="`${currentArea.id}-${activePage}`" />

          <button class="alert-row" @tap="overviewPanel = 'devices'">
            <view class="alert-copy">
              <text class="alert-dot"></text>
              <text class="alert-title">风险提醒</text>
              <text class="alert-text">{{ currentArea.overview.alert }}</text>
            </view>
            <text class="row-arrow"></text>
          </button>

          <view class="section-block video-section">
            <view class="section-head">
              <text class="section-title">现场视频</text>
              <text class="section-note">{{ currentArea.overview.cameras.filter((item) => item.online).length }} 路在线</text>
            </view>
            <view class="video-preview" :class="{ offline: !currentCamera.online }">
              <view class="video-topline">
                <view class="live-badge">
                  <text class="live-dot"></text>
                  <text>{{ currentCamera.online ? 'LIVE' : 'OFFLINE' }}</text>
                </view>
                <text class="video-time">13:32:18</text>
              </view>
              <view :key="currentCamera.id" class="construction-scene camera-motion">
                <view class="scene-building building-left"></view>
                <view class="scene-building building-right"></view>
                <view class="scene-crane"></view>
                <view class="scene-ground"></view>
                <view v-if="!currentCamera.online" class="offline-layer">
                  <text class="offline-icon"></text>
                  <text>视频信号离线</text>
                </view>
              </view>
              <view class="video-caption">
                <text class="camera-name">{{ currentCamera.name }}</text>
                <text class="camera-location">{{ currentCamera.location }}</text>
              </view>
            </view>
            <view class="video-actions">
              <button class="secondary-action" @tap="cycleCamera">
                <image class="inline-action-icon" src="/static/design-preview-icons/overview-switch-camera.png" mode="aspectFit" />
                <text>切换摄像头</text>
              </button>
              <button class="text-action" @tap="previewAction('查看全部摄像头')">
                <image class="inline-action-icon" src="/static/design-preview-icons/overview-camera.png" mode="aspectFit" />
                <text>查看全部</text>
              </button>
            </view>
          </view>

          <view class="section-block detail-section">
            <PreviewSegmentControl
              :model-value="overviewPanel"
              :options="[
                { value: 'info', label: '区域信息' },
                { value: 'documents', label: '最近资料' },
                { value: 'devices', label: '异常设备' }
              ]"
              :accent="activeTheme.accent"
              :tint="activeTheme.tint"
              @update:model-value="setOverviewPanel"
            />

            <view v-if="overviewPanel === 'info'" class="info-list">
              <view class="info-row"><text>施工阶段</text><text>{{ currentArea.stage }}</text></view>
              <view class="info-row"><text>区域面积</text><text>{{ currentArea.area }}</text></view>
              <view class="info-row"><text>计划工期</text><text>{{ currentArea.period }}</text></view>
              <view class="info-row"><text>区域负责人</text><text>{{ currentArea.manager }}</text></view>
            </view>

            <view v-else-if="overviewPanel === 'documents'" class="plain-list">
              <button v-for="document in currentArea.overview.documents" :key="document.id" class="plain-row" @tap="previewAction(`查看${document.name}`)">
                <view class="file-icon"></view>
                <view class="plain-copy">
                  <text class="plain-title">{{ document.name }}</text>
                  <text class="plain-meta">{{ document.type }} · {{ document.time }}</text>
                </view>
                <text class="row-arrow"></text>
              </button>
            </view>

            <view v-else class="plain-list">
              <button v-for="device in currentArea.overview.devices" :key="device.id" class="plain-row" @tap="previewAction(`查看${device.name}`)">
                <view class="device-icon"></view>
                <view class="plain-copy">
                  <text class="plain-title">{{ device.name }}</text>
                  <text class="plain-meta">{{ device.detail }}</text>
                </view>
                <PreviewStatusPill :label="device.status" :tone="device.status === '运行中' ? 'green' : device.status === '异常' ? 'red' : 'amber'" />
              </button>
            </view>
          </view>
        </template>

        <template v-else-if="activePage === 'personnel'">
          <PreviewAreaSwitcher :area="currentArea" :areas="previewAreas" :accent="activeTheme.accent" :tint="activeTheme.tint" @select="selectArea" />
          <PreviewSummaryStrip :metrics="currentArea.personnel.metrics" :accent="activeTheme.accent" :tint="activeTheme.tint" :motion-key="`${currentArea.id}-${activePage}`" />

          <view class="quick-actions personnel-actions" :class="{ compact: !canManage }">
            <button v-if="canManage" @tap="previewAction('新增人员')"><view class="action-icon-wrap"><image src="/static/design-preview-icons/personnel-add.png" mode="aspectFit" /></view><text>新增人员</text></button>
            <button v-if="canManage" @tap="previewAction('办理进退场')"><view class="action-icon-wrap"><image src="/static/design-preview-icons/personnel-entry.png" mode="aspectFit" /></view><text>办理进退场</text></button>
            <button v-if="canManage" @tap="previewAction('发起三级教育')"><view class="action-icon-wrap"><image src="/static/design-preview-icons/personnel-education.png" mode="aspectFit" /></view><text>发起教育</text></button>
            <button v-if="!canManage" @tap="previewAction('查看本人档案')"><view class="action-icon-wrap"><image src="/static/design-preview-icons/personnel-profile.png" mode="aspectFit" /></view><text>本人档案</text></button>
            <button v-if="!canManage" @tap="previewAction('查看教育记录')"><view class="action-icon-wrap"><image src="/static/design-preview-icons/personnel-record.png" mode="aspectFit" /></view><text>教育记录</text></button>
          </view>

          <view class="section-block list-section">
            <PreviewSegmentControl
              :model-value="personnelTab"
              :options="personnelTabs"
              :accent="activeTheme.accent"
              :tint="activeTheme.tint"
              @update:model-value="setPersonnelTab"
            />

            <view v-if="personnelTab !== 'training'" class="search-box">
              <text class="search-icon"></text>
              <input v-model="personnelSearch" class="search-input" placeholder="搜索姓名、班组、工种" placeholder-class="search-placeholder" />
              <button v-if="personnelSearch" class="clear-search" aria-label="清空" @tap="personnelSearch = ''">×</button>
            </view>

            <view v-if="personnelTab !== 'training'" class="plain-list personnel-list">
              <button v-for="(person, index) in filteredPeople" :key="person.id" class="plain-row person-row row-motion" :style="staggerStyle(index)" @tap="previewAction(`查看${person.name}档案`)">
                <view class="person-avatar">{{ person.name.slice(0, 1) }}</view>
                <view class="plain-copy">
                  <view class="person-title-line">
                    <text class="plain-title">{{ person.name }}</text>
                    <text v-if="person.certificate" class="warning-mark">证</text>
                  </view>
                  <text class="plain-meta">{{ person.trade }} · {{ person.team }}</text>
                </view>
                <PreviewStatusPill :label="person.status" :tone="personTone(person.status)" />
                <text class="row-arrow"></text>
              </button>
              <view v-if="!filteredPeople.length" class="empty-state">暂无匹配人员</view>
            </view>

            <view v-else class="plain-list training-list">
              <button v-for="(training, index) in currentArea.personnel.trainings" :key="training.id" class="plain-row training-row row-motion" :style="staggerStyle(index)" @tap="previewAction(`查看${training.title}`)">
                <view class="training-date"><text>{{ training.time.split(' ')[0] }}</text><text>{{ training.time.split(' ')[1] }}</text></view>
                <view class="plain-copy">
                  <text class="plain-title two-line">{{ training.title }}</text>
                  <text class="plain-meta">{{ training.people }} 人 · {{ training.time }}</text>
                </view>
                <PreviewStatusPill :label="training.status" :tone="training.status === '已完成' ? 'green' : 'blue'" />
                <text class="row-arrow"></text>
              </button>
            </view>
          </view>
        </template>

        <template v-else-if="activePage === 'quality'">
          <PreviewAreaSwitcher :area="currentArea" :areas="previewAreas" :accent="activeTheme.accent" :tint="activeTheme.tint" @select="selectArea" />
          <PreviewSummaryStrip :metrics="currentArea.quality.metrics" :accent="activeTheme.accent" :tint="activeTheme.tint" :motion-key="`${currentArea.id}-${activePage}`" />

          <view class="quick-actions quality-actions">
            <button v-if="canManage" class="quality-primary-action" @tap="previewAction('发起质量检查')"><view class="action-icon-wrap"><image src="/static/design-preview-icons/quality-inspect.png" mode="aspectFit" /></view><view class="action-copy"><text>发起检查</text><text>记录现场质量问题</text></view></button>
            <button @tap="qualityFilter = 'PENDING'"><view class="action-icon-wrap"><image src="/static/design-preview-icons/quality-rectify.png" mode="aspectFit" /></view><text>问题整改</text></button>
            <button @tap="qualityFilter = 'RECHECK'"><view class="action-icon-wrap"><image src="/static/design-preview-icons/quality-accept.png" mode="aspectFit" /></view><text>验收记录</text></button>
            <button @tap="previewAction('查看质量资料')"><view class="action-icon-wrap"><image src="/static/design-preview-icons/quality-files.png" mode="aspectFit" /></view><text>质量资料</text></button>
          </view>

          <view class="section-block list-section">
            <view class="section-head priority-head">
              <view>
                <text class="section-title">优先处理</text>
                <text class="section-subtitle">按逾期和截止时间排序</text>
              </view>
              <PreviewStatusPill :label="`${currentArea.quality.issues.filter((item) => item.status !== 'CLOSED').length} 项未闭环`" tone="amber" />
            </view>

            <PreviewSegmentControl
              :model-value="qualityFilter"
              :options="qualityFilters"
              :accent="activeTheme.accent"
              :tint="activeTheme.tint"
              @update:model-value="setQualityFilter"
            />

            <view class="plain-list issue-list">
              <button v-for="(issue, index) in filteredQualityIssues" :key="issue.id" class="plain-row issue-row row-motion" :style="staggerStyle(index)" @tap="previewAction(`查看质量问题：${issue.title}`)">
                <view class="issue-indicator" :class="qualityTone(issue)"></view>
                <view class="plain-copy">
                  <text class="plain-title">{{ issue.title }}</text>
                  <text class="plain-meta">{{ issue.location }} · {{ issue.owner }}</text>
                  <text class="due-text" :class="qualityTone(issue)">{{ issue.dueText }}</text>
                </view>
                <PreviewStatusPill :label="qualityLabel(issue)" :tone="qualityTone(issue)" />
                <text class="row-arrow"></text>
              </button>
              <view v-if="!filteredQualityIssues.length" class="empty-state">当前分类暂无问题</view>
            </view>
          </view>

          <view class="flow-hint">
            <text class="flow-title">质量闭环</text>
            <text class="flow-text">检查 → 整改 → 复查 → 关闭</text>
          </view>
        </template>

        <template v-else-if="activePage === 'safety'">
          <PreviewAreaSwitcher :area="currentArea" :areas="previewAreas" :accent="activeTheme.accent" :tint="activeTheme.tint" @select="selectArea" />
          <PreviewSummaryStrip :metrics="currentArea.safety.metrics" :accent="activeTheme.accent" :tint="activeTheme.tint" :motion-key="`${currentArea.id}-${activePage}`" />

          <button class="scan-primary" :class="{ scanning: scanAnimating }" @tap="openSafetyRoute('scan')">
            <view class="scan-icon-wrap">
              <image class="scan-icon-image" src="/static/design-preview-icons/safety-scan-light.png" mode="aspectFit" />
              <text class="scan-line"></text>
            </view>
            <view class="scan-copy">
              <text class="scan-title">扫描现场二维码</text>
              <text class="scan-desc">日检、抽查和电箱信息从这里开始</text>
            </view>
            <text class="scan-arrow"></text>
          </button>

          <view class="section-block list-section safety-list-section">
            <view class="section-head safety-head">
              <text class="section-title">现场任务</text>
              <text class="role-view">{{ currentProfile.roleLabel }}视图</text>
            </view>
            <PreviewSegmentControl
              :model-value="safetyFilter"
              :options="safetyTabs"
              :accent="activeTheme.accent"
              :tint="activeTheme.tint"
              @update:model-value="setSafetyFilter"
            />

            <view class="plain-list task-list">
              <button v-for="(task, index) in visibleSafetyTasks" :key="task.id" class="plain-row task-row row-motion" :style="staggerStyle(index)" @tap="previewAction(`${task.action}：${task.code}`)">
                <view class="task-code-box" :class="safetyTone(task)">{{ task.code.split('-').slice(-1)[0] }}</view>
                <view class="plain-copy">
                  <text class="task-code">{{ task.code }}</text>
                  <text class="plain-title">{{ task.title }}</text>
                  <text class="plain-meta">{{ task.meta }}</text>
                </view>
                <text class="task-action" :class="safetyTone(task)">{{ task.action }}</text>
                <text class="row-arrow"></text>
              </button>
              <view v-if="!visibleSafetyTasks.length" class="empty-state">当前分类暂无任务</view>
            </view>
          </view>

          <view class="shortcut-row">
            <button @tap="openSafetyRoute('ledger')"><view class="shortcut-icon-wrap"><image src="/static/design-preview-icons/safety-ledger.png" mode="aspectFit" /></view><text>电箱台账</text></button>
            <button @tap="openSafetyRoute('records')"><view class="shortcut-icon-wrap"><image src="/static/design-preview-icons/safety-records.png" mode="aspectFit" /></view><text>检查记录</text></button>
            <button @tap="openSafetyRoute('summary')"><view class="shortcut-icon-wrap"><image src="/static/design-preview-icons/safety-summary.png" mode="aspectFit" /></view><text>巡检汇总</text></button>
          </view>
        </template>

        <template v-else>
          <view class="profile-hero">
            <view class="profile-panel">
              <view class="profile-avatar">{{ currentProfile.initials }}</view>
              <view class="profile-copy">
                <text class="profile-name">{{ currentProfile.name }}</text>
                <button class="role-button" @tap="openRolePicker">
                  <text>{{ currentProfile.roleLabel }}</text>
                  <text class="mini-chevron"></text>
                </button>
                <text class="profile-phone">{{ currentProfile.phone }}</text>
              </view>
            </view>

            <view class="profile-summary">
              <button @tap="previewAction('查看我的待办')">
                <text class="profile-number">{{ todoCount }}</text>
                <text class="profile-label">我的待办</text>
              </button>
              <button @tap="previewAction('查看授权施工区域')">
                <text class="profile-number">{{ previewAreas.length }}</text>
                <text class="profile-label">授权施工区域</text>
              </button>
            </view>
          </view>

          <button class="current-area-row" @tap="selectPage('overview')">
            <view>
              <text class="current-area-label">当前施工区域</text>
              <text class="current-area-name">{{ currentArea.name }} · {{ currentArea.stage }}</text>
            </view>
            <text class="row-arrow"></text>
          </button>

          <view class="service-section">
            <text class="section-title">账号与服务</text>
            <view class="service-grid">
              <button @tap="previewAction('查看我的待办')"><view class="service-icon-wrap"><image src="/static/design-preview-icons/profile-todo.png" mode="aspectFit" /></view><view class="service-copy"><text>我的待办</text><text>巡检、整改和质量事项</text></view><PreviewStatusPill :label="String(todoCount)" tone="red" /></button>
              <button @tap="previewAction('查看消息通知')"><view class="service-icon-wrap"><image src="/static/design-preview-icons/profile-notice.png" mode="aspectFit" /></view><view class="service-copy"><text>消息通知</text><text>查看业务提醒</text></view><text class="row-arrow"></text></button>
              <button @tap="previewAction('查看区域与权限')"><view class="service-icon-wrap"><image src="/static/design-preview-icons/profile-permission.png" mode="aspectFit" /></view><view class="service-copy"><text>区域与权限</text><text>{{ previewAreas.length }} 个授权施工区域</text></view><text class="row-arrow"></text></button>
              <button @tap="previewAction('查看个人信息')"><view class="service-icon-wrap"><image src="/static/design-preview-icons/profile-user.png" mode="aspectFit" /></view><view class="service-copy"><text>个人信息</text><text>手机号与账号资料</text></view><text class="row-arrow"></text></button>
              <button @tap="previewAction('修改密码')"><view class="service-icon-wrap"><image src="/static/design-preview-icons/profile-password.png" mode="aspectFit" /></view><view class="service-copy"><text>修改密码</text><text>更新登录密码</text></view><text class="row-arrow"></text></button>
              <button @tap="previewAction('帮助与反馈')"><view class="service-icon-wrap"><image src="/static/design-preview-icons/profile-help.png" mode="aspectFit" /></view><view class="service-copy"><text>帮助反馈</text><text>使用帮助与问题反馈</text></view><text class="row-arrow"></text></button>
            </view>
          </view>

          <button class="logout-button" @tap="previewAction('退出登录')">退出登录</button>
        </template>
      </view>
    </scroll-view>

    <PreviewTabBar :active="activePage" @select="selectPage" />

    <view v-if="rolePickerOpen" class="role-overlay" :class="{ closing: rolePickerClosing }" @tap="closeRolePicker()">
      <view class="role-sheet" :class="{ closing: rolePickerClosing }" @tap.stop>
        <view class="role-sheet-head">
          <view>
            <text class="role-sheet-title">切换角色预览</text>
            <text class="role-sheet-subtitle">仅影响设计预览中的操作入口</text>
          </view>
          <button class="role-close" aria-label="关闭" @tap="closeRolePicker()">×</button>
        </view>
        <button v-for="role in roleOptions" :key="role.value" class="role-option" :class="{ active: previewRole === role.value }" @tap="selectRole(role.value)">
          <view>
            <text class="role-option-name">{{ role.label }}</text>
            <text class="role-option-desc">{{ role.description }}</text>
          </view>
          <text v-if="previewRole === role.value" class="role-check"></text>
        </button>
      </view>
    </view>
  </view>
</template>

<style scoped>
.preview-shell {
  position: relative;
  display: flex;
  height: 100vh;
  min-height: 100vh;
  overflow: hidden;
  flex-direction: column;
  background: #f4f6f8;
  color: #172033;
}

.preview-shell :deep(.app-nav) {
  border-bottom: 1rpx solid #e1e6ec;
  background: #ffffff;
}

.preview-scroll {
  box-sizing: border-box;
  scrollbar-width: none;
}

.preview-scroll ::-webkit-scrollbar {
  display: none;
  width: 0;
  height: 0;
}

.preview-content {
  display: flex;
  min-height: 100%;
  flex-direction: column;
  gap: 18rpx;
  padding: 20rpx 24rpx calc(142rpx + env(safe-area-inset-bottom));
}

.section-block {
  padding: 22rpx;
  border: 1rpx solid #e1e6ec;
  border-radius: 16rpx;
  background: #ffffff;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
  margin-bottom: 18rpx;
}

.section-title {
  display: block;
  color: #172033;
  font-size: 28rpx;
  font-weight: 800;
  line-height: 1.2;
}

.section-note,
.section-subtitle,
.role-view {
  display: block;
  color: #667085;
  font-size: 21rpx;
  line-height: 1.3;
}

.section-subtitle {
  margin-top: 6rpx;
}

.alert-row {
  display: flex;
  width: 100%;
  min-height: 66rpx;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding: 0 20rpx;
  border: 1rpx solid #f0d59b;
  border-radius: 14rpx;
  background: #fffaf0;
  text-align: left;
}

.alert-copy {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10rpx;
}

.alert-dot {
  width: 12rpx;
  height: 12rpx;
  flex-shrink: 0;
  border-radius: 50%;
  background: #d98b00;
}

.alert-title {
  flex-shrink: 0;
  color: #8a5a05;
  font-size: 22rpx;
  font-weight: 800;
}

.alert-text {
  overflow: hidden;
  color: #7a5b1f;
  font-size: 21rpx;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-arrow,
.scan-arrow {
  width: 13rpx;
  height: 13rpx;
  flex-shrink: 0;
  border-top: 3rpx solid #98a2b3;
  border-right: 3rpx solid #98a2b3;
  transform: rotate(45deg);
}

.video-preview {
  position: relative;
  overflow: hidden;
  aspect-ratio: 16 / 9;
  border-radius: 12rpx;
  background: #17253a;
  color: #ffffff;
}

.video-topline,
.video-caption {
  position: absolute;
  right: 16rpx;
  left: 16rpx;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
}

.video-topline {
  top: 14rpx;
}

.video-caption {
  bottom: 14rpx;
}

.live-badge {
  display: inline-flex;
  min-height: 34rpx;
  align-items: center;
  gap: 8rpx;
  padding: 0 10rpx;
  border-radius: 7rpx;
  background: rgba(7, 13, 23, 0.76);
  font-size: 18rpx;
  font-weight: 700;
}

.live-dot {
  width: 9rpx;
  height: 9rpx;
  border-radius: 50%;
  background: #28c884;
}

.offline .live-dot {
  background: #f05252;
}

.video-time,
.camera-location {
  color: rgba(255, 255, 255, 0.76);
  font-size: 18rpx;
}

.camera-name {
  overflow: hidden;
  color: #ffffff;
  font-size: 22rpx;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.construction-scene {
  position: absolute;
  inset: 0;
  overflow: hidden;
  background: linear-gradient(180deg, #38536d 0%, #7d91a1 52%, #4d4e49 53%, #373933 100%);
}

.construction-scene::before {
  position: absolute;
  right: 0;
  bottom: 28%;
  left: 0;
  height: 2rpx;
  background: rgba(255, 255, 255, 0.28);
  box-shadow: 0 -58rpx 0 rgba(255, 255, 255, 0.08);
  content: "";
}

.scene-building {
  position: absolute;
  bottom: 28%;
  border: 3rpx solid rgba(224, 232, 238, 0.62);
  background: rgba(58, 68, 74, 0.82);
}

.scene-building::before {
  position: absolute;
  inset: 14rpx;
  background: repeating-linear-gradient(90deg, transparent 0 24rpx, rgba(210, 221, 229, 0.44) 25rpx 28rpx), repeating-linear-gradient(0deg, transparent 0 21rpx, rgba(210, 221, 229, 0.34) 22rpx 25rpx);
  content: "";
}

.building-left {
  left: 8%;
  width: 34%;
  height: 43%;
}

.building-right {
  right: 9%;
  width: 29%;
  height: 57%;
}

.scene-crane {
  position: absolute;
  bottom: 28%;
  left: 49%;
  width: 4rpx;
  height: 52%;
  background: #d6a63a;
}

.scene-crane::before {
  position: absolute;
  top: 8rpx;
  left: -70rpx;
  width: 172rpx;
  height: 4rpx;
  background: #d6a63a;
  content: "";
}

.scene-crane::after {
  position: absolute;
  top: 10rpx;
  left: 74rpx;
  width: 2rpx;
  height: 58rpx;
  background: #d6a63a;
  content: "";
}

.scene-ground {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  height: 28%;
  background: repeating-linear-gradient(160deg, rgba(255, 255, 255, 0.04) 0 12rpx, rgba(0, 0, 0, 0.03) 13rpx 23rpx);
}

.offline-layer {
  position: absolute;
  z-index: 2;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 12rpx;
  background: rgba(14, 22, 32, 0.78);
  color: rgba(255, 255, 255, 0.82);
  font-size: 22rpx;
}

.offline-icon {
  width: 38rpx;
  height: 28rpx;
  border: 4rpx solid currentColor;
  border-radius: 6rpx;
}

.video-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 16rpx;
}

.secondary-action,
.text-action {
  display: inline-flex;
  min-height: 58rpx;
  align-items: center;
  justify-content: center;
  padding: 0 20rpx;
  border-radius: 12rpx;
  font-size: 22rpx;
  font-weight: 700;
}

.secondary-action {
  border: 1rpx solid #cbdcf3;
  background: #f2f7ff;
  color: #1769d2;
}

.text-action {
  color: #1769d2;
}

.compact-tabs {
  display: flex;
  gap: 8rpx;
  overflow-x: auto;
}

.compact-tabs button {
  display: inline-flex;
  min-height: 54rpx;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  gap: 7rpx;
  padding: 0 18rpx;
  border: 1rpx solid #dce2e9;
  border-radius: 12rpx;
  background: #ffffff;
  color: #667085;
  font-size: 21rpx;
  font-weight: 700;
}

.compact-tabs button.active {
  border-color: #b7d2f5;
  background: #eaf2ff;
  color: #1769d2;
}

.three-tabs button,
.four-tabs button {
  min-width: 0;
  flex: 1;
  padding: 0 8rpx;
}

.info-list,
.plain-list {
  margin-top: 18rpx;
  border-top: 1rpx solid #e8ecf1;
}

.info-row {
  display: flex;
  min-height: 68rpx;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  border-bottom: 1rpx solid #e8ecf1;
  color: #667085;
  font-size: 23rpx;
}

.info-row text:last-child {
  overflow: hidden;
  color: #263244;
  font-weight: 600;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.plain-row {
  display: flex;
  width: 100%;
  min-height: 88rpx;
  align-items: center;
  gap: 16rpx;
  padding: 14rpx 0;
  border-bottom: 1rpx solid #e8ecf1;
  text-align: left;
}

.plain-copy {
  min-width: 0;
  flex: 1;
}

.plain-title,
.plain-meta,
.due-text,
.task-code {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.plain-title {
  color: #263244;
  font-size: 24rpx;
  font-weight: 700;
  line-height: 1.3;
}

.plain-title.two-line {
  display: -webkit-box;
  white-space: normal;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.plain-meta {
  margin-top: 7rpx;
  color: #7a8493;
  font-size: 20rpx;
  line-height: 1.25;
}

.file-icon,
.device-icon {
  position: relative;
  width: 42rpx;
  height: 48rpx;
  flex-shrink: 0;
  border: 3rpx solid #1769d2;
  border-radius: 5rpx;
}

.file-icon::after {
  position: absolute;
  right: 7rpx;
  bottom: 9rpx;
  left: 7rpx;
  height: 3rpx;
  background: #1769d2;
  box-shadow: 0 -9rpx 0 #1769d2;
  content: "";
}

.device-icon {
  height: 42rpx;
  border-color: #667085;
  border-radius: 8rpx;
}

.device-icon::after {
  position: absolute;
  right: 7rpx;
  bottom: -10rpx;
  left: 7rpx;
  height: 3rpx;
  background: #667085;
  content: "";
}

.quick-actions {
  display: grid;
  gap: 10rpx;
}

.personnel-actions {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.personnel-actions.compact {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.quality-actions {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.quick-actions button {
  display: flex;
  min-width: 0;
  min-height: 84rpx;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 8rpx;
  padding: 10rpx 4rpx;
  border: 1rpx solid #e1e6ec;
  border-radius: 14rpx;
  background: #ffffff;
  color: #344054;
  font-size: 20rpx;
  font-weight: 700;
}

.action-symbol {
  position: relative;
  display: block;
  width: 30rpx;
  height: 30rpx;
  color: #1769d2;
}

.plus-symbol {
  font-size: 38rpx;
  font-weight: 400;
  line-height: 27rpx;
  text-align: center;
}

.gate-symbol,
.education-symbol,
.person-symbol,
.inspect-symbol,
.rectify-symbol,
.accept-symbol,
.file-symbol {
  border: 3rpx solid currentColor;
  border-radius: 5rpx;
}

.gate-symbol::after {
  position: absolute;
  top: 4rpx;
  bottom: 4rpx;
  left: 12rpx;
  width: 3rpx;
  background: currentColor;
  content: "";
}

.education-symbol::after,
.inspect-symbol::after,
.file-symbol::after {
  position: absolute;
  right: 5rpx;
  bottom: 7rpx;
  left: 5rpx;
  height: 3rpx;
  background: currentColor;
  box-shadow: 0 -7rpx 0 currentColor;
  content: "";
}

.person-symbol {
  border-radius: 50%;
}

.rectify-symbol {
  border-radius: 50%;
}

.rectify-symbol::after {
  position: absolute;
  top: 5rpx;
  left: 12rpx;
  width: 3rpx;
  height: 15rpx;
  background: currentColor;
  box-shadow: 0 18rpx 0 -1rpx currentColor;
  content: "";
}

.accept-symbol::after {
  position: absolute;
  top: 6rpx;
  left: 7rpx;
  width: 15rpx;
  height: 8rpx;
  border-bottom: 3rpx solid currentColor;
  border-left: 3rpx solid currentColor;
  content: "";
  transform: rotate(-45deg);
}

.personnel-tabs,
.safety-tabs {
  margin-bottom: 16rpx;
}

.tab-count {
  min-width: 28rpx;
  padding: 2rpx 7rpx;
  border-radius: 999rpx;
  background: #eef1f4;
  font-size: 18rpx;
  text-align: center;
}

.compact-tabs button.active .tab-count {
  background: #d8e8ff;
}

.search-box {
  display: flex;
  height: 68rpx;
  align-items: center;
  gap: 14rpx;
  padding: 0 18rpx;
  border: 1rpx solid #dce2e9;
  border-radius: 12rpx;
  background: #f8fafb;
}

.search-icon {
  position: relative;
  width: 25rpx;
  height: 25rpx;
  flex-shrink: 0;
  border: 3rpx solid #7f8a99;
  border-radius: 50%;
}

.search-icon::after {
  position: absolute;
  right: -9rpx;
  bottom: -5rpx;
  width: 11rpx;
  height: 3rpx;
  border-radius: 999rpx;
  background: #7f8a99;
  content: "";
  transform: rotate(45deg);
}

.search-input {
  min-width: 0;
  height: 68rpx;
  flex: 1;
  color: #263244;
  font-size: 23rpx;
}

.search-placeholder {
  color: #98a2b3;
}

.clear-search {
  display: flex;
  width: 38rpx;
  height: 38rpx;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #e8ecf1;
  color: #667085;
  font-size: 28rpx;
}

.personnel-list {
  margin-top: 12rpx;
}

.person-avatar {
  display: flex;
  width: 54rpx;
  height: 54rpx;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 50%;
  background: #eaf2ff;
  color: #1769d2;
  font-size: 23rpx;
  font-weight: 800;
}

.person-title-line {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.warning-mark {
  display: inline-flex;
  width: 28rpx;
  height: 28rpx;
  align-items: center;
  justify-content: center;
  border-radius: 6rpx;
  background: #fff3d6;
  color: #9a6300;
  font-size: 17rpx;
  font-weight: 800;
}

.training-date {
  display: flex;
  width: 70rpx;
  min-height: 64rpx;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 3rpx;
  border-radius: 10rpx;
  background: #f1f4f7;
  color: #475467;
  font-size: 18rpx;
}

.priority-head,
.safety-head {
  margin-bottom: 16rpx;
}

.issue-list {
  margin-top: 12rpx;
}

.issue-row {
  min-height: 112rpx;
}

.issue-indicator {
  width: 7rpx;
  height: 62rpx;
  flex-shrink: 0;
  border-radius: 999rpx;
  background: #98a2b3;
}

.issue-indicator.blue {
  background: #1769d2;
}

.issue-indicator.green {
  background: #087d68;
}

.issue-indicator.amber {
  background: #d98b00;
}

.issue-indicator.red {
  background: #c43d3d;
}

.due-text {
  margin-top: 7rpx;
  font-size: 20rpx;
}

.due-text.blue {
  color: #1769d2;
}

.due-text.green {
  color: #087d68;
}

.due-text.amber {
  color: #9a6300;
}

.due-text.red {
  color: #c43d3d;
}

.flow-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  padding: 18rpx 20rpx;
  border-left: 6rpx solid #1769d2;
  border-radius: 0 12rpx 12rpx 0;
  background: #eef4fc;
}

.flow-title {
  color: #263244;
  font-size: 22rpx;
  font-weight: 800;
}

.flow-text {
  color: #52657c;
  font-size: 21rpx;
}

.scan-primary {
  display: flex;
  width: 100%;
  min-height: 108rpx;
  align-items: center;
  gap: 20rpx;
  padding: 20rpx 22rpx;
  border-radius: 16rpx;
  background: #1769d2;
  box-shadow: 0 12rpx 28rpx rgba(23, 105, 210, 0.22);
  color: #ffffff;
  text-align: left;
}

.scan-symbol {
  position: relative;
  width: 50rpx;
  height: 50rpx;
  flex-shrink: 0;
  background:
    linear-gradient(currentColor, currentColor) left top / 17rpx 4rpx no-repeat,
    linear-gradient(currentColor, currentColor) left top / 4rpx 17rpx no-repeat,
    linear-gradient(currentColor, currentColor) right top / 17rpx 4rpx no-repeat,
    linear-gradient(currentColor, currentColor) right top / 4rpx 17rpx no-repeat,
    linear-gradient(currentColor, currentColor) left bottom / 17rpx 4rpx no-repeat,
    linear-gradient(currentColor, currentColor) left bottom / 4rpx 17rpx no-repeat,
    linear-gradient(currentColor, currentColor) right bottom / 17rpx 4rpx no-repeat,
    linear-gradient(currentColor, currentColor) right bottom / 4rpx 17rpx no-repeat;
}

.scan-symbol::after {
  position: absolute;
  top: 24rpx;
  left: 5rpx;
  width: 40rpx;
  height: 3rpx;
  border-radius: 999rpx;
  background: currentColor;
  content: "";
}

.scan-copy {
  min-width: 0;
  flex: 1;
}

.scan-title,
.scan-desc {
  display: block;
}

.scan-title {
  font-size: 28rpx;
  font-weight: 800;
}

.scan-desc {
  margin-top: 7rpx;
  color: rgba(255, 255, 255, 0.78);
  font-size: 20rpx;
}

.scan-arrow {
  border-color: rgba(255, 255, 255, 0.8);
}

.role-view {
  padding: 5rpx 10rpx;
  border-radius: 8rpx;
  background: #f0f2f5;
}

.task-row {
  min-height: 112rpx;
}

.task-code-box {
  display: flex;
  width: 52rpx;
  height: 52rpx;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 10rpx;
  background: #eaf2ff;
  color: #1769d2;
  font-size: 19rpx;
  font-weight: 800;
}

.task-code-box.amber {
  background: #fff3d6;
  color: #9a6300;
}

.task-code-box.red {
  background: #fee9e8;
  color: #c43d3d;
}

.task-code {
  margin-bottom: 4rpx;
  color: #667085;
  font-size: 18rpx;
  font-weight: 700;
}

.task-action {
  flex-shrink: 0;
  font-size: 21rpx;
  font-weight: 800;
}

.task-action.blue {
  color: #1769d2;
}

.task-action.amber {
  color: #9a6300;
}

.task-action.red {
  color: #c43d3d;
}

.shortcut-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  overflow: hidden;
  border: 1rpx solid #e1e6ec;
  border-radius: 16rpx;
  background: #ffffff;
}

.shortcut-row button {
  position: relative;
  display: flex;
  min-height: 92rpx;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 9rpx;
  color: #344054;
  font-size: 20rpx;
  font-weight: 700;
}

.shortcut-row button + button::before {
  position: absolute;
  top: 18rpx;
  bottom: 18rpx;
  left: 0;
  width: 1rpx;
  background: #e8ecf1;
  content: "";
}

.shortcut-icon {
  position: relative;
  width: 31rpx;
  height: 31rpx;
  border: 3rpx solid #1769d2;
  border-radius: 5rpx;
}

.record-icon::after,
.ledger-icon::after {
  position: absolute;
  right: 5rpx;
  bottom: 6rpx;
  left: 5rpx;
  height: 3rpx;
  background: #1769d2;
  box-shadow: 0 -7rpx 0 #1769d2;
  content: "";
}

.summary-icon {
  border: 0;
  border-bottom: 3rpx solid #1769d2;
  border-left: 3rpx solid #1769d2;
  border-radius: 0;
}

.summary-icon::before,
.summary-icon::after {
  position: absolute;
  bottom: 0;
  width: 6rpx;
  background: #1769d2;
  content: "";
}

.summary-icon::before {
  left: 6rpx;
  height: 14rpx;
  box-shadow: 10rpx -8rpx 0 #1769d2, 20rpx -16rpx 0 #1769d2;
}

.empty-state {
  padding: 44rpx 0;
  color: #98a2b3;
  font-size: 22rpx;
  text-align: center;
}

.profile-panel {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 28rpx 22rpx;
  border-bottom: 1rpx solid #e1e6ec;
  background: #ffffff;
}

.profile-avatar {
  display: flex;
  width: 104rpx;
  height: 104rpx;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 50%;
  background: #1769d2;
  color: #ffffff;
  font-size: 40rpx;
  font-weight: 800;
}

.profile-copy {
  min-width: 0;
  flex: 1;
}

.profile-name {
  display: block;
  color: #172033;
  font-size: 34rpx;
  font-weight: 800;
}

.role-button {
  display: inline-flex;
  min-height: 44rpx;
  align-items: center;
  gap: 9rpx;
  margin-top: 8rpx;
  padding: 0 12rpx;
  border-radius: 10rpx;
  background: #eaf2ff;
  color: #1769d2;
  font-size: 20rpx;
  font-weight: 700;
}

.mini-chevron {
  width: 10rpx;
  height: 10rpx;
  margin-top: -5rpx;
  border-right: 2rpx solid currentColor;
  border-bottom: 2rpx solid currentColor;
  transform: rotate(45deg);
}

.profile-phone {
  display: block;
  margin-top: 9rpx;
  color: #667085;
  font-size: 22rpx;
}

.profile-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  overflow: hidden;
  border: 1rpx solid #e1e6ec;
  border-radius: 16rpx;
  background: #ffffff;
}

.profile-summary button {
  position: relative;
  min-height: 112rpx;
  padding: 18rpx;
}

.profile-summary button + button::before {
  position: absolute;
  top: 22rpx;
  bottom: 22rpx;
  left: 0;
  width: 1rpx;
  background: #e8ecf1;
  content: "";
}

.profile-number,
.profile-label {
  display: block;
  text-align: center;
}

.profile-number {
  color: #1769d2;
  font-size: 36rpx;
  font-weight: 800;
}

.profile-label {
  margin-top: 8rpx;
  color: #667085;
  font-size: 21rpx;
}

.current-area-row {
  display: flex;
  width: 100%;
  min-height: 88rpx;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
  padding: 16rpx 20rpx;
  border: 1rpx solid #e1e6ec;
  border-radius: 16rpx;
  background: #ffffff;
  text-align: left;
}

.current-area-label,
.current-area-name {
  display: block;
}

.current-area-label {
  color: #667085;
  font-size: 20rpx;
}

.current-area-name {
  margin-top: 6rpx;
  color: #263244;
  font-size: 24rpx;
  font-weight: 700;
}

.service-section {
  padding: 22rpx;
  border: 1rpx solid #e1e6ec;
  border-radius: 16rpx;
  background: #ffffff;
}

.service-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 18rpx;
  border-top: 1rpx solid #e8ecf1;
  border-left: 1rpx solid #e8ecf1;
}

.service-grid button {
  display: flex;
  min-width: 0;
  min-height: 88rpx;
  align-items: center;
  gap: 13rpx;
  padding: 0 15rpx;
  border-right: 1rpx solid #e8ecf1;
  border-bottom: 1rpx solid #e8ecf1;
  color: #344054;
  font-size: 21rpx;
  font-weight: 700;
  text-align: left;
}

.service-grid button > text:nth-child(2) {
  min-width: 0;
  flex: 1;
}

.service-icon {
  position: relative;
  display: flex;
  width: 30rpx;
  height: 30rpx;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border: 3rpx solid #667085;
  border-radius: 5rpx;
  color: #667085;
}

.notice-service {
  border-radius: 50% 50% 8rpx 8rpx;
}

.permission-service {
  border-radius: 50%;
}

.profile-service {
  border-radius: 50%;
}

.lock-service::before {
  position: absolute;
  top: -16rpx;
  left: 4rpx;
  width: 16rpx;
  height: 16rpx;
  border: 3rpx solid #667085;
  border-bottom: 0;
  border-radius: 10rpx 10rpx 0 0;
  content: "";
}

.help-service {
  border-radius: 50%;
  font-size: 21rpx;
  font-weight: 800;
}

.logout-button {
  display: flex;
  min-height: 76rpx;
  align-items: center;
  justify-content: center;
  border: 1rpx solid #efc3c3;
  border-radius: 16rpx;
  background: #ffffff;
  color: #c43d3d;
  font-size: 24rpx;
  font-weight: 800;
}

.role-overlay {
  position: fixed;
  z-index: 90;
  inset: 0;
  display: flex;
  align-items: flex-end;
  background: rgba(15, 23, 42, 0.36);
}

.role-sheet {
  width: 100%;
  padding: 26rpx 24rpx calc(30rpx + env(safe-area-inset-bottom));
  border-radius: 24rpx 24rpx 0 0;
  background: #ffffff;
}

.role-sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
  padding-bottom: 18rpx;
  border-bottom: 1rpx solid #e8ecf1;
}

.role-sheet-title,
.role-sheet-subtitle,
.role-option-name,
.role-option-desc {
  display: block;
}

.role-sheet-title {
  color: #172033;
  font-size: 29rpx;
  font-weight: 800;
}

.role-sheet-subtitle,
.role-option-desc {
  margin-top: 6rpx;
  color: #667085;
  font-size: 20rpx;
}

.role-close {
  display: flex;
  width: 58rpx;
  height: 58rpx;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #f0f2f5;
  color: #475467;
  font-size: 36rpx;
}

.role-option {
  display: flex;
  width: 100%;
  min-height: 96rpx;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
  padding: 16rpx 6rpx;
  border-bottom: 1rpx solid #e8ecf1;
  text-align: left;
}

.role-option-name {
  color: #263244;
  font-size: 25rpx;
  font-weight: 700;
}

.role-option.active .role-option-name {
  color: #1769d2;
}

.role-check {
  width: 24rpx;
  height: 13rpx;
  margin-right: 10rpx;
  border-bottom: 4rpx solid #1769d2;
  border-left: 4rpx solid #1769d2;
  transform: rotate(-45deg);
}
</style>

<style scoped src="./preview-v2.css"></style>
