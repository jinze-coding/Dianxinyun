<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import {
  createSealApplication,
  deleteSealApplicationFile,
  getAvailableSeals,
  getSealApplication,
  getSealCcCandidates,
  resolveSealEntry,
  submitSealApplication,
  updateSealApplication,
  uploadSealApplicationFile
} from '@/api/seal';
import { useAuthStore } from '@/stores/auth';
import { useProjectStore } from '@/stores/project';
import type { SealApplicationFile, SealApplicationItem, SealCcCandidate, SealDefinition, SealEntryResolution } from '@/types';
import { chooseMessageDocument, formatFileSize, type LocalDocumentFile } from '@/utils/documentFile';
import { ensureSealPageAccess } from '@/utils/sealAccess';
import { extractSealScene } from '@/utils/sealScene';
import { getQueryNumber, navigateTo, showToast } from '@/utils/navigation';

interface PendingFile extends LocalDocumentFile { key: string }

const auth = useAuthStore();
const projectStore = useProjectStore();
const scene = ref('');
const applicationId = ref(0);
const requestKey = ref(`seal-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`);
const entry = ref<SealEntryResolution | null>(null);
const companyName = ref('');
const seals = ref<SealDefinition[]>([]);
const selectedSealId = ref(0);
const ccCandidates = ref<SealCcCandidate[]>([]);
const selectedCcUserIds = ref<number[]>([]);
const existingFiles = ref<SealApplicationFile[]>([]);
const pendingFiles = ref<PendingFile[]>([]);
const loading = ref(true);
const saving = ref(false);
const ccSheetOpen = ref(false);
const ccSelectionInitialized = ref(false);
const ccCandidatesReady = ref(false);
const form = reactive({
  projectId: 0,
  departmentName: '',
  purpose: '',
  items: [{ documentName: '', copies: 1 }] as SealApplicationItem[]
});

const currentProject = computed(() => projectStore.state.projects.find((item) => item.id === form.projectId));
const sceneLocked = computed(() => Boolean(scene.value && entry.value));
const projectSealLocked = computed(() => sceneLocked.value || Boolean(applicationId.value));
const sealIndex = computed(() => Math.max(0, seals.value.findIndex((item) => item.id === selectedSealId.value)));
const projectIndex = computed(() => Math.max(0, projectStore.state.projects.findIndex((item) => item.id === form.projectId)));
const selectedSeal = computed(() => entry.value?.sealId
  ? { id: entry.value.sealId, sealName: entry.value.sealName || '项目印章' }
  : seals.value.find((item) => item.id === selectedSealId.value));
const selectedCc = computed(() => ccCandidates.value.filter((item) => selectedCcUserIds.value.includes(item.userId)));
const sourceFiles = computed(() => existingFiles.value.filter((item) => item.fileRole === 'SOURCE'));

onLoad(async (options) => {
  scene.value = extractSealScene(String(options?.scene || ''));
  applicationId.value = getQueryNumber(options?.id, 0);
  const directProjectId = getQueryNumber(options?.projectId, 0);
  const query = [scene.value ? `scene=${encodeURIComponent(scene.value)}` : '', applicationId.value ? `id=${applicationId.value}` : '',
    directProjectId ? `projectId=${directProjectId}` : ''].filter(Boolean).join('&');
  if (!await ensureSealPageAccess(`/pages/seal/apply${query ? `?${query}` : ''}`)) { loading.value = false; return; }
  try {
    await projectStore.loadProjects();
    if (applicationId.value) {
      await loadExisting(applicationId.value);
    } else if (scene.value) {
      entry.value = await resolveSealEntry(scene.value);
      companyName.value = entry.value.companyName || '';
      form.projectId = entry.value.projectId;
      form.departmentName = entry.value.projectName;
      selectedSealId.value = Number(entry.value.sealId || 0);
    } else {
      form.projectId = directProjectId || projectStore.state.currentProjectId || projectStore.state.projects[0]?.id || 0;
      form.departmentName = currentProject.value?.projectName || '';
    }
    await loadProjectOptions();
  } catch (error) {
    showToast(error instanceof Error ? error.message : '用印申请加载失败');
  } finally {
    loading.value = false;
  }
});

async function loadExisting(id: number) {
  const detail = await getSealApplication(id);
  if (detail.canEdit === false) {
    navigateTo(`/pages/seal/detail?id=${id}`);
    return;
  }
  form.projectId = detail.projectId;
  companyName.value = detail.companyName || '';
  form.departmentName = detail.departmentName || '';
  form.purpose = detail.purpose || '';
  form.items = (detail.items?.length ? detail.items : [{ documentName: '', copies: 1 }])
    .map((item) => ({ id: item.id, documentName: item.documentName, copies: Number(item.copies || 1) }));
  selectedSealId.value = Number(detail.sealId || 0);
  entry.value = detail.sealId ? {
    scene: '', projectId: detail.projectId, projectName: detail.projectName, sealId: detail.sealId, sealName: detail.sealName
  } : null;
  requestKey.value = detail.requestKey || requestKey.value;
  existingFiles.value = detail.files || [];
  selectedCcUserIds.value = (detail.ccRecipients || []).map((item) => item.userId);
  ccSelectionInitialized.value = true;
}

async function loadProjectOptions() {
  if (!form.projectId) return;
  const sealOptions = sceneLocked.value ? [] : await getAvailableSeals(form.projectId);
  seals.value = sealOptions;
  if (!sceneLocked.value && !seals.value.some((item) => item.id === selectedSealId.value)) {
    selectedSealId.value = seals.value[0]?.id || 0;
  }
  if (!companyName.value) companyName.value = selectedSeal.value?.companyName || '';
  form.departmentName = selectedSeal.value?.projectName || currentProject.value?.projectName || form.departmentName;
  await loadCcCandidates();
}

async function loadCcCandidates() {
  ccCandidatesReady.value = false;
  const candidates = await getSealCcCandidates(form.projectId, selectedSeal.value?.id);
  ccCandidates.value = candidates;
  if (!ccSelectionInitialized.value) {
    selectedCcUserIds.value = candidates.filter((item) => item.defaultSelected || item.selected).map((item) => item.userId);
    ccSelectionInitialized.value = true;
  } else {
    selectedCcUserIds.value = selectedCcUserIds.value.filter((id) => candidates.some((item) => item.userId === id));
  }
  ccCandidatesReady.value = true;
}

async function changeProject(event: unknown) {
  const index = Number((event as { detail?: { value?: string | number } }).detail?.value || 0);
  form.projectId = projectStore.state.projects[index]?.id || 0;
  form.departmentName = projectStore.state.projects[index]?.projectName || '';
  selectedSealId.value = 0;
  selectedCcUserIds.value = [];
  ccSelectionInitialized.value = false;
  try { await loadProjectOptions(); }
  catch (error) { showToast(error instanceof Error ? error.message : '抄送人配置加载失败'); }
}

async function changeSeal(event: unknown) {
  const index = Number((event as { detail?: { value?: string | number } }).detail?.value || 0);
  selectedSealId.value = seals.value[index]?.id || 0;
  companyName.value = selectedSeal.value?.companyName || '';
  form.departmentName = selectedSeal.value?.projectName || currentProject.value?.projectName || '';
  ccSelectionInitialized.value = false;
  try { await loadCcCandidates(); }
  catch (error) { showToast(error instanceof Error ? error.message : '抄送人配置加载失败'); }
}

async function openCcSheet() {
  if (!ccCandidatesReady.value) {
    try { await loadCcCandidates(); }
    catch (error) { showToast(error instanceof Error ? error.message : '抄送人配置加载失败'); return; }
  }
  ccSheetOpen.value = true;
}

function addItem() {
  if (form.items.length >= 20) { showToast('用印文件清单最多 20 项'); return; }
  form.items.push({ documentName: '', copies: 1 });
}

function removeItem(index: number) {
  if (form.items.length === 1) { showToast('至少保留一项用印文件'); return; }
  form.items.splice(index, 1);
}

async function chooseFile() {
  if (pendingFiles.value.length + sourceFiles.value.length >= 20) { showToast('每次申请最多上传 20 个文件'); return; }
  try {
    const file = await chooseMessageDocument();
    pendingFiles.value.push({ ...file, key: `${Date.now()}-${Math.random()}` });
  } catch (error) {
    const message = error instanceof Error ? error.message : '文件选择失败';
    if (message !== '已取消选择') showToast(message);
  }
}

async function removeExistingFile(file: SealApplicationFile) {
  if (!applicationId.value || !file.canDelete) return;
  try {
    await deleteSealApplicationFile(applicationId.value, file.id);
    existingFiles.value = existingFiles.value.filter((item) => item.id !== file.id);
  } catch (error) { showToast(error instanceof Error ? error.message : '附件删除失败'); }
}

function toggleCc(userId: number) {
  selectedCcUserIds.value = selectedCcUserIds.value.includes(userId)
    ? selectedCcUserIds.value.filter((id) => id !== userId)
    : [...selectedCcUserIds.value, userId];
}

function validate(submit: boolean) {
  if (!form.projectId) return '请选择施工区域';
  if (!selectedSeal.value?.id) return '当前项目没有可申请的印章';
  if (!form.departmentName.trim()) return '未获取到印章所属项目，请重新进入';
  if (!ccCandidatesReady.value) return '抄送人配置尚未加载，请重试';
  if (!form.purpose.trim()) return '请填写用印事由';
  if (form.items.some((item) => !item.documentName.trim())) return '请完整填写用印文件名称';
  if (form.items.some((item) => !Number.isInteger(Number(item.copies)) || Number(item.copies) < 1 || Number(item.copies) > 999)) return '每项份数应为 1–999 的整数';
  if (submit && !sourceFiles.value.length && !pendingFiles.value.length) return '提交前请上传需要盖章的资料';
  return '';
}

async function save(submit: boolean) {
  if (saving.value) return;
  const message = validate(submit);
  if (message) { showToast(message); return; }
  saving.value = true;
  try {
    const input = {
      requestKey: requestKey.value,
      scene: scene.value || undefined,
      projectId: form.projectId,
      sealId: selectedSeal.value?.id,
      departmentName: form.departmentName.trim(),
      purpose: form.purpose.trim(),
      items: form.items.map((item) => ({ id: item.id, documentName: item.documentName.trim(), copies: Number(item.copies) })),
      ccUserIds: selectedCcUserIds.value
    };
    const detail = applicationId.value
      ? await updateSealApplication(applicationId.value, input)
      : await createSealApplication(input);
    applicationId.value = detail.id;
    existingFiles.value = detail.files || existingFiles.value;
    for (const file of [...pendingFiles.value]) {
      const uploaded = await uploadSealApplicationFile(detail.id, file.path, 'SOURCE');
      existingFiles.value = [...existingFiles.value, uploaded];
      pendingFiles.value = pendingFiles.value.filter((item) => item.key !== file.key);
    }
    if (submit) await submitSealApplication(detail.id);
    showToast(submit ? '用印申请已提交' : '草稿已保存');
    setTimeout(() => uni.redirectTo({ url: `/pages/seal/detail?id=${detail.id}` }), 450);
  } catch (error) {
    const prefix = applicationId.value ? '草稿已保留，' : '';
    showToast(`${prefix}${error instanceof Error ? error.message : '保存失败'}`);
  } finally {
    saving.value = false;
  }
}

function goBack() { getCurrentPages().length > 1 ? uni.navigateBack() : uni.reLaunch({ url: '/pages/seal/list' }); }
</script>

<template>
  <view class="apply-page">
    <AppNavBar :title="applicationId ? '编辑用印申请' : '用印申请'" @back="goBack" />
    <scroll-view class="form-scroll" scroll-y>
      <view class="form-content">
        <view v-if="loading" class="state-card">正在加载申请信息…</view>
        <template v-else>
          <view class="identity-card">
            <view><text>申请人</text><text>{{ auth.state.user?.realName || auth.state.user?.username || '当前用户' }}</text></view>
            <view><text>联系方式</text><text>{{ auth.state.user?.phone || '以账号资料为准' }}</text></view>
            <view><text>申请日期</text><text>提交时自动记录</text></view>
          </view>

          <view class="form-card">
            <text class="section-title">用印基本信息</text>
            <view class="field"><text>公司名称</text><view class="readonly">{{ companyName || '以用印配置为准' }}</view></view>
            <view class="field"><text>施工区域 *</text><view v-if="projectSealLocked" class="readonly">{{ entry?.projectName || currentProject?.projectName }}</view><picker v-else :range="projectStore.state.projects" range-key="projectName" :value="projectIndex" @change="changeProject"><view class="picker-value">{{ currentProject?.projectName || '请选择施工区域' }}</view></picker></view>
            <view class="field"><text>申请部门 / 项目部</text><view class="readonly">{{ form.departmentName || currentProject?.projectName || '以印章所属项目为准' }}</view></view>
            <view class="field"><text>使用印章 *</text><view v-if="projectSealLocked" class="readonly"><text>{{ selectedSeal?.sealName || '二维码绑定印章' }}</text><text class="locked">不可更改</text></view><picker v-else :range="seals" range-key="sealName" :value="sealIndex" @change="changeSeal"><view class="picker-value">{{ selectedSeal?.sealName || '当前项目暂无可用印章' }}</view></picker></view>
            <view class="field"><text>用印事由 *</text><textarea v-model="form.purpose" maxlength="1000" placeholder="请说明文件用途、送交单位及其他必要信息" /></view>
          </view>

          <view class="form-card">
            <view class="section-head"><view><text class="section-title">用印文件清单</text><text>可填写多项，份数为盖章份数</text></view><button @tap="addItem">＋ 添加</button></view>
            <view v-for="(item, index) in form.items" :key="item.id || index" class="item-row">
              <text class="item-no">{{ index + 1 }}</text><input v-model="item.documentName" maxlength="200" placeholder="用印文件名称" /><input v-model.number="item.copies" class="copies" type="number" /><text>份</text><button @tap="removeItem(index)">×</button>
            </view>
          </view>

          <view class="form-card">
            <view class="section-head"><view><text class="section-title">待盖章资料</text><text>支持 Word、Excel、PDF、图片等，单个不超过 50MB</text></view><button @tap="chooseFile">选择文件</button></view>
            <view v-for="file in sourceFiles" :key="file.id" class="file-row"><view><text>{{ file.originalFileName || file.fileName }}</text><text>{{ formatFileSize(file.fileSize) }} · 已上传</text></view><button v-if="file.canDelete" @tap="removeExistingFile(file)">删除</button></view>
            <view v-for="file in pendingFiles" :key="file.key" class="file-row pending"><view><text>{{ file.name }}</text><text>{{ formatFileSize(file.size) }} · 待上传</text></view><button @tap="pendingFiles = pendingFiles.filter((item) => item.key !== file.key)">移除</button></view>
            <view v-if="!sourceFiles.length && !pendingFiles.length" class="empty-line">尚未选择待盖章资料</view>
          </view>

          <button class="cc-card" @tap="openCcSheet"><view><text>通知抄送人</text><text>{{ !ccCandidatesReady ? '配置未加载，点击重试' : selectedCc.length ? selectedCc.map((item) => item.displayName).join('、') : '未选择，状态变化时不额外抄送' }}</text></view><text>›</text></button>

          <view class="submit-row"><button class="draft" :disabled="saving" @tap="save(false)">{{ saving ? '处理中…' : '保存草稿' }}</button><button class="submit" :disabled="saving" @tap="save(true)">{{ saving ? '处理中…' : '提交审批' }}</button></view>
        </template>
      </view>
    </scroll-view>

    <view v-if="ccSheetOpen" class="overlay" @tap="ccSheetOpen = false"><view class="sheet" @tap.stop><view class="sheet-head"><view><text>选择抄送人</text><text>仅显示当前项目有效成员</text></view><button @tap="ccSheetOpen = false">×</button></view><scroll-view class="candidate-list" scroll-y><button v-for="item in ccCandidates" :key="item.userId" :class="{ selected: selectedCcUserIds.includes(item.userId) }" @tap="toggleCc(item.userId)"><text>{{ item.displayName }}</text><text>{{ selectedCcUserIds.includes(item.userId) ? '✓' : '+' }}</text></button><view v-if="!ccCandidates.length" class="empty-line">当前项目暂无可选抄送人</view></scroll-view><button class="confirm" @tap="ccSheetOpen = false">确认（{{ selectedCcUserIds.length }}）</button></view></view>
  </view>
</template>

<style scoped>
.apply-page { min-height: 100vh; background: #f4f6f7; color: #223247; }.form-scroll { height: calc(100vh - 92rpx); }.form-content { display: flex; flex-direction: column; gap: 18rpx; padding: 22rpx 24rpx calc(38rpx + env(safe-area-inset-bottom)); }.state-card { padding: 70rpx 20rpx; border-radius: 18rpx; background: #fff; color: #7e8b98; text-align: center; }
.identity-card { display: grid; grid-template-columns: repeat(3,minmax(0,1fr)); gap: 1rpx; overflow: hidden; border-radius: 16rpx; background: #dfe7ec; }.identity-card view { min-width: 0; padding: 16rpx 13rpx; background: #edf3f6; }.identity-card text { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.identity-card text:first-child { color: #82909d; font-size: 18rpx; }.identity-card text:last-child { margin-top: 6rpx; color: #34475a; font-size: 20rpx; font-weight: 750; }
.form-card { padding: 22rpx; border-radius: 18rpx; background: #fff; box-shadow: 0 8rpx 24rpx rgba(43,56,72,.05); }.section-title { color: #26384c; font-size: 25rpx; font-weight: 820; }.field { margin-top: 20rpx; }.field>text { display: block; margin-bottom: 9rpx; color: #617284; font-size: 21rpx; font-weight: 700; }.field input,.field textarea,.picker-value,.readonly { box-sizing: border-box; width: 100%; min-height: 74rpx; padding: 16rpx 17rpx; border: 1rpx solid #dfe6eb; border-radius: 12rpx; background: #f8fafb; color: #304255; font-size: 22rpx; }.field textarea { min-height: 150rpx; }.readonly { display: flex; align-items: center; justify-content: space-between; color: #536678; }.locked { padding: 5rpx 9rpx; border-radius: 999rpx; background: #f4eadc; color: #966421; font-size: 17rpx; }
.section-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 18rpx; }.section-head view { min-width: 0; }.section-head view>text { display: block; }.section-head view>text:last-child { margin-top: 5rpx; color: #8b98a4; font-size: 18rpx; }.section-head>button { min-height: 52rpx; padding: 0 15rpx; border-radius: 11rpx; background: #e7eff4; color: #3e657e; font-size: 19rpx; font-weight: 750; }
.item-row { display: flex; min-height: 78rpx; align-items: center; gap: 9rpx; margin-top: 14rpx; padding-top: 14rpx; border-top: 1rpx solid #edf0f2; }.item-no { display: flex; width: 34rpx; height: 34rpx; align-items: center; justify-content: center; flex-shrink: 0; border-radius: 50%; background: #e7eef3; color: #456a82; font-size: 18rpx; font-weight: 800; }.item-row input { min-width: 0; height: 66rpx; flex: 1; padding: 0 13rpx; border: 1rpx solid #e1e6ea; border-radius: 11rpx; background: #fafbfc; font-size: 21rpx; }.item-row input.copies { max-width: 86rpx; text-align: center; }.item-row>text:nth-last-child(2) { color: #718090; font-size: 19rpx; }.item-row>button { width: 42rpx; height: 42rpx; flex-shrink: 0; border-radius: 50%; color: #aa5a55; font-size: 26rpx; }
.file-row { display: flex; min-height: 76rpx; align-items: center; justify-content: space-between; gap: 16rpx; margin-top: 12rpx; padding: 12rpx 0; border-top: 1rpx solid #edf0f2; }.file-row view { min-width: 0; flex: 1; }.file-row text { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.file-row text:first-child { color: #34475a; font-size: 21rpx; font-weight: 700; }.file-row text:last-child { margin-top: 5rpx; color: #8d99a5; font-size: 18rpx; }.file-row button { flex-shrink: 0; color: #aa5a55; font-size: 19rpx; }.file-row.pending text:last-child { color: #9a6b2e; }.empty-line { padding: 32rpx 10rpx; color: #98a2ad; font-size: 20rpx; text-align: center; }
.cc-card { display: flex; width: 100%; min-height: 92rpx; align-items: center; justify-content: space-between; gap: 18rpx; padding: 18rpx 22rpx; border-radius: 17rpx; background: #fff; text-align: left; }.cc-card view { min-width: 0; flex: 1; }.cc-card view text { display: block; }.cc-card view text:first-child { color: #34475a; font-size: 23rpx; font-weight: 780; }.cc-card view text:last-child { overflow: hidden; margin-top: 6rpx; color: #8a97a4; font-size: 19rpx; text-overflow: ellipsis; white-space: nowrap; }.cc-card>text { color: #9ba6b0; font-size: 32rpx; }
.submit-row { display: grid; grid-template-columns: 1fr 2fr; gap: 13rpx; }.submit-row button { min-height: 78rpx; border-radius: 14rpx; font-size: 23rpx; font-weight: 780; }.draft { border: 1rpx solid #cbd7df; background: #fff; color: #536a7c; }.submit { background: #8a612c; color: #fff; }.submit-row button[disabled] { opacity: .6; }
.overlay { position: fixed; z-index: 90; inset: 0; display: flex; align-items: flex-end; background: rgba(23,35,48,.42); }.sheet { display: flex; width: 100%; max-height: 75vh; flex-direction: column; padding: 16rpx 24rpx calc(24rpx + env(safe-area-inset-bottom)); border-radius: 24rpx 24rpx 0 0; background: #fff; }.sheet-head { display: flex; align-items: flex-start; justify-content: space-between; padding: 8rpx 0 18rpx; }.sheet-head view text { display: block; }.sheet-head view text:first-child { font-size: 27rpx; font-weight: 820; }.sheet-head view text:last-child { margin-top: 5rpx; color: #8b98a5; font-size: 19rpx; }.sheet-head>button { width: 50rpx; height: 50rpx; border-radius: 50%; background: #eef2f5; color: #687889; font-size: 27rpx; }.candidate-list { min-height: 260rpx; flex: 1; }.candidate-list>button { display: flex; width: 100%; min-height: 72rpx; align-items: center; justify-content: space-between; padding: 0 15rpx; border-bottom: 1rpx solid #edf0f2; color: #3b4d60; font-size: 22rpx; }.candidate-list>button.selected { background: #edf4f7; color: #315f86; font-weight: 750; }.confirm { min-height: 76rpx; margin-top: 16rpx; border-radius: 13rpx; background: #315f86; color: #fff; font-size: 23rpx; font-weight: 780; }
</style>
