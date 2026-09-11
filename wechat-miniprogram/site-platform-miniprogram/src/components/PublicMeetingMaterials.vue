<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { onHide, onShow } from '@dcloudio/uni-app';
import MeetingServiceDialog from '@/components/MeetingServiceDialog.vue';
import { publicMaterialUrl, resolveMeetingMaterials, type PublicMeetingMaterial } from '@/api/meetingMaterials';

const props = defineProps<{ inviteToken: string; visible: boolean }>();
const emit = defineEmits<{ (event: 'close'): void; (event: 'revalidate'): void }>();
const foreground = ref(true);
const records = ref<PublicMeetingMaterial[]>([]);
const title = ref('');
const ended = ref(false);
const error = ref('');
const loading = ref(false);
const keyword = ref('');
const category = ref('');
const page = ref(1);
const selected = ref<PublicMeetingMaterial>();
const textPreview = ref('');
const textPage = ref(0);
const audioPlaying = ref(false);
const audioTime = ref(0);
const audioDuration = ref(0);
const opening = ref('');
const downloading = ref('');
const progress = ref(0);
const scrollTarget = ref(0);
let savedScrollTop = 0;
let audio: UniApp.InnerAudioContext | undefined;
let epoch = 0;
let actionEpoch = 0;
let textEpoch = 0;
let timer: ReturnType<typeof setInterval> | undefined;
let refreshTask: Promise<boolean> | undefined;
let task: UniApp.DownloadTask | undefined;
let textTask: UniApp.RequestTask | undefined;
let localPath = '';
let active = false;
const categories: Record<string, string> = { PUBLICITY: '宣发通知', AGENDA: '议程课件', MINUTES: '会议纪要', MEDIA: '现场影像', OTHER: '其他' };
const categoryKeys = ['', ...Object.keys(categories)];
const labels = ['全部分类', ...Object.values(categories)];
const filtered = computed(() => records.value.filter((row) => (!category.value || row.category === category.value) && (!keyword.value.trim() || row.title.includes(keyword.value.trim()))));
const visibleRecords = computed(() => filtered.value.slice((page.value - 1) * 10, page.value * 10));
const busy = computed(() => !!opening.value || !!downloading.value);
function size(bytes: number) {
  return bytes >= 1073741824 ? `${(bytes / 1073741824).toFixed(1)} GB` : bytes >= 1048576 ? `${(bytes / 1048576).toFixed(1)} MB` : `${Math.ceil(bytes / 1024)} KB`;
}
function removeTemporary(path = localPath) {
  // #ifdef MP-WEIXIN
  if (path) uni.getFileSystemManager().unlink({ filePath: path, fail: () => {} });
  // #endif
  if (path === localPath) localPath = '';
}
async function restoreScroll() {
  scrollTarget.value = -1;
  await nextTick();
  scrollTarget.value = savedScrollTop;
}
function cancelDownload() {
  actionEpoch += 1;
  const pending = task;
  task = undefined;
  downloading.value = '';
  opening.value = '';
  pending?.abort();
}
function closePreview() {
  textEpoch += 1;
  textTask?.abort();
  textTask = undefined;
  selected.value = undefined;
  void restoreScroll();
}
function close() {
  if (selected.value) closePreview();
  else emit('close');
}
function refresh(): Promise<boolean> {
  if (!active || !props.inviteToken) return Promise.resolve(false);
  if (refreshTask) return refreshTask;
  const ticket = epoch;
  loading.value = true;
  let pending: Promise<boolean>;
  pending = resolveMeetingMaterials(props.inviteToken).then((data) => {
    if (!active || ticket !== epoch) return false;
    records.value = data.records;
    title.value = data.title;
    ended.value = data.ended;
    error.value = '';
    if (selected.value && !data.records.some((row) => row.publicCode === selected.value?.publicCode)) closePreview();
    if (downloading.value && !data.records.some((row) => row.publicCode === downloading.value)) { cancelDownload(); removeTemporary(); }
    page.value = Math.min(page.value, Math.max(1, Math.ceil(filtered.value.length / 10)));
    return true;
  }).catch((e: unknown) => {
    if (!active || ticket !== epoch) return false;
    records.value = [];
    closePreview();
    cancelDownload();
    removeTemporary();
    error.value = e instanceof Error ? e.message : '会议资料读取失败';
    emit('revalidate');
    return false;
  }).finally(() => {
    if (ticket === epoch) loading.value = false;
    if (refreshTask === pending) refreshTask = undefined;
  });
  refreshTask = pending;
  return pending;
}
function stop() {
  active = false;
  epoch += 1;
  refreshTask = undefined;
  loading.value = false;
  if (timer) clearInterval(timer);
  timer = undefined;
  cancelDownload();
  closePreview();
}
function start() {
  stop();
  active = true;
  void refresh();
  void restoreScroll();
  timer = setInterval(() => { void refresh(); }, 30000);
}
watch([() => props.visible, foreground, () => props.inviteToken], ([shown, inForeground], previous) => {
  if (previous && previous[2] !== props.inviteToken) {
    records.value = []; keyword.value = ''; category.value = ''; page.value = 1; savedScrollTop = 0;
    removeTemporary();
  }
  if (shown && inForeground) start();
  else stop();
  // 原生文档查看会触发 onHide，不能在此时删除正在被查看的临时文件。
  if (!shown) removeTemporary();
}, { immediate: true, flush: 'sync' });
watch([keyword, category], () => { page.value = 1; savedScrollTop = 0; void restoreScroll(); });
watch(page, () => { savedScrollTop = 0; void restoreScroll(); });
watch(() => selected.value?.publicCode, () => {
  audio?.destroy(); audio = undefined;
  audioPlaying.value = false; audioTime.value = 0; audioDuration.value = 0;
  if (selected.value?.previewKind !== 'AUDIO') return;
  audio = uni.createInnerAudioContext();
  audio.src = publicMaterialUrl(selected.value.publicCode);
  audio.onTimeUpdate(() => { audioTime.value = audio?.currentTime || 0; audioDuration.value = audio?.duration || 0; });
  audio.onPlay(() => { audioPlaying.value = true; });
  audio.onPause(() => { audioPlaying.value = false; });
  audio.onEnded(() => { audioPlaying.value = false; });
  audio.onError(() => { audioPlaying.value = false; uni.showToast({ title: '音频暂时无法播放，请刷新或下载', icon: 'none' }); });
}, { flush: 'sync' });
function playAudio() { if (audioPlaying.value) audio?.pause(); else audio?.play(); }
function seekAudio(event: { detail: { value: number } }) { if (audioDuration.value) audio?.seek(event.detail.value * audioDuration.value / 100); }
onShow(() => { foreground.value = true; });
onHide(() => { foreground.value = false; });
onBeforeUnmount(() => { stop(); audio?.destroy(); removeTemporary(); });

async function open(row: PublicMeetingMaterial, original = false) {
  if (!active || busy.value) return;
  const action = ++actionEpoch;
  const isCurrent = () => active && action === actionEpoch;
  opening.value = row.publicCode;
  const refreshed = await refresh();
  if (!isCurrent()) return;
  opening.value = '';
  const current = records.value.find((item) => item.publicCode === row.publicCode);
  if (!refreshed || !current) return;
  if (!original && current.previewStatus === 'READY' && ['IMAGE', 'HEIF', 'VIDEO', 'AUDIO'].includes(current.previewKind)) {
    selected.value = current; return;
  }
  if (!original && current.previewKind === 'TEXT') { selected.value = current; loadText(current, 0); return; }
  const usePreview = !original && current.previewStatus === 'READY' && ['PDF', 'OFFICE'].includes(current.previewKind);
  downloading.value = current.publicCode; progress.value = 0; removeTemporary();
  task = uni.downloadFile({
    url: publicMaterialUrl(current.publicCode, usePreview), timeout: 1200000,
    success: (response) => {
      if (!isCurrent()) { removeTemporary(response.tempFilePath); return; }
      if (response.statusCode !== 200) {
        removeTemporary(response.tempFilePath);
        uni.showToast({ title: '资料已撤下或读取失败，请刷新', icon: 'none' });
        void refresh(); return;
      }
      localPath = response.tempFilePath;
      const extension = usePreview ? 'pdf' : current.fileName.split('.').pop()?.toLowerCase();
      if (['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx'].includes(extension || '')) {
        uni.openDocument({ filePath: localPath, fileType: extension, showMenu: true,
          fail: () => { if (isCurrent()) uni.showToast({ title: '当前设备无法预览，请下载查看', icon: 'none' }); } });
      } else {
        // #ifdef MP-WEIXIN
        uni.shareFileMessage({ filePath: localPath, fileName: current.fileName });
        // #endif
      }
    },
    fail: () => { if (isCurrent()) uni.showToast({ title: '下载中断，请检查网络和设备剩余空间后重试', icon: 'none' }); },
    complete: () => { if (isCurrent()) { downloading.value = ''; task = undefined; } }
  });
  task.onProgressUpdate((event) => { if (isCurrent()) progress.value = event.progress; });
}
function loadText(row: PublicMeetingMaterial, part: number) {
  const ticket = ++textEpoch;
  textTask?.abort();
  textPreview.value = '正在读取…';
  textTask = uni.request({ url: publicMaterialUrl(row.publicCode), dataType: 'text',
    header: { Range: `bytes=${part * 1048576}-${(part + 1) * 1048576 - 1}` },
    success: (response) => {
      if (!active || ticket !== textEpoch || selected.value?.publicCode !== row.publicCode) return;
      textPreview.value = [200, 206].includes(response.statusCode) ? String(response.data) : '资料已更新或撤下，请刷新';
      textPage.value = part;
      if (![200, 206].includes(response.statusCode)) void refresh();
    },
    fail: () => { if (active && ticket === textEpoch && selected.value?.publicCode === row.publicCode) textPreview.value = '内容读取失败，请稍后重试'; }
  });
}
</script>

<template>
  <MeetingServiceDialog :visible="visible" :title="selected?.title || '会议资料'" :close-label="selected ? '返回资料' : '关闭'" @close="close">
    <scroll-view v-if="!selected" class="materials-scroll" scroll-y :scroll-top="scrollTarget" @scroll="savedScrollTop = $event.detail.scrollTop" @touchmove.stop>
      <view class="public-materials">
        <view class="materials-head">
          <text class="materials-note">{{ ended ? `${title} · 会议已结束，可继续查看公开资料` : '无需预约或签到即可查看已公开资料' }}</text>
          <button class="material-button" size="mini" :disabled="loading" @tap="refresh">{{ loading ? '刷新中' : '刷新' }}</button>
        </view>
        <view class="materials-filter">
          <input v-model="keyword" class="materials-search" placeholder="搜索资料名称" maxlength="200" />
          <picker :range="labels" :value="categoryKeys.indexOf(category)" @change="category = categoryKeys[Number($event.detail.value)]"><text class="material-category">{{ categories[category] || '全部分类' }} ▾</text></picker>
        </view>
        <view v-if="error" class="materials-error">{{ error }}</view>
        <text v-else-if="loading && !records.length" class="materials-note">正在读取会议资料…</text>
        <view v-else>
          <view v-for="row in visibleRecords" :key="row.publicCode" class="material-item">
            <text class="material-name">{{ row.title }}</text>
            <text class="materials-note">{{ categories[row.category] }} · V{{ row.versionNo }} · {{ size(row.fileSize) }}</text>
            <text v-if="row.description" class="material-description">{{ row.description }}</text>
            <view class="material-buttons">
              <button class="material-button" size="mini" :disabled="busy" @tap="open(row)">{{ opening === row.publicCode ? '正在读取…' : row.previewStatus === 'READY' && row.previewKind !== 'UNSUPPORTED' ? '查看资料' : '下载查看' }}</button>
              <button class="material-button" size="mini" :disabled="busy" @tap="open(row, true)">下载原文件</button>
            </view>
            <progress v-if="downloading === row.publicCode" :percent="progress" show-info stroke-width="4" />
          </view>
          <text v-if="!filtered.length" class="materials-note">{{ records.length ? '没有符合条件的资料' : '暂无公开资料' }}</text>
          <view v-if="filtered.length > 10" class="materials-pages">
            <button class="material-button" size="mini" :disabled="page <= 1" @tap="page--">上一页</button>
            <text>{{ page }} / {{ Math.ceil(filtered.length / 10) }}</text>
            <button class="material-button" size="mini" :disabled="page * 10 >= filtered.length" @tap="page++">下一页</button>
          </view>
        </view>
      </view>
    </scroll-view>
    <scroll-view v-else class="materials-scroll" scroll-y @touchmove.stop>
      <view class="material-preview">
        <image v-if="['IMAGE', 'HEIF'].includes(selected.previewKind)" class="material-image" :src="publicMaterialUrl(selected.publicCode)" mode="widthFix" />
        <video v-else-if="selected.previewKind === 'VIDEO'" class="material-video" :src="publicMaterialUrl(selected.publicCode)" controls />
        <view v-else-if="selected.previewKind === 'AUDIO'" class="material-audio">
          <button class="material-button" @tap="playAudio">{{ audioPlaying ? '暂停' : '播放音频' }}</button>
          <slider :value="audioDuration ? audioTime / audioDuration * 100 : 0" @change="seekAudio" />
          <text>{{ Math.floor(audioTime) }} / {{ Math.floor(audioDuration) }} 秒</text>
        </view>
        <view v-else-if="selected.previewKind === 'TEXT'">
          <text class="material-text">{{ textPreview }}</text>
          <view class="materials-pages">
            <button class="material-button" size="mini" :disabled="textPage === 0" @tap="loadText(selected, textPage - 1)">上一段</button>
            <text>{{ textPage + 1 }}</text>
            <button class="material-button" size="mini" :disabled="(textPage + 1) * 1048576 >= selected.fileSize" @tap="loadText(selected, textPage + 1)">下一段</button>
          </view>
        </view>
        <button class="material-button" :disabled="busy" @tap="open(selected, true)">下载原文件</button>
        <progress v-if="downloading" :percent="progress" show-info stroke-width="4" />
      </view>
    </scroll-view>
  </MeetingServiceDialog>
</template>

<style scoped>
.materials-scroll{height:100%;width:100%}
.public-materials,.material-preview{box-sizing:border-box;padding:26rpx;color:#263e53;font-size:26rpx}
.materials-head{display:flex;align-items:center;justify-content:space-between;gap:20rpx}
.materials-note{display:block;color:#71869a;margin:12rpx 0;line-height:1.6;font-size:24rpx;word-break:break-word}
.materials-error{color:#ac4141;padding:26rpx 0;line-height:1.6;word-break:break-word}
.materials-filter{display:flex;gap:14rpx;align-items:center;margin:20rpx 0 6rpx}
.materials-search{flex:1;min-width:0;background:#f3f7fb;height:68rpx;box-sizing:border-box;padding:0 16rpx;border-radius:10rpx;font-size:25rpx}
.material-category{font-size:24rpx;white-space:nowrap;color:#315f86}
.material-item{border-bottom:1rpx solid #e8eef5;padding:24rpx 0;word-break:break-word}
.material-name{font-size:29rpx;font-weight:650;line-height:1.5}
.material-description{display:block;white-space:pre-wrap;line-height:1.6}
.material-buttons,.materials-pages{display:flex;align-items:center;justify-content:flex-start;flex-wrap:wrap;gap:16rpx;margin-top:18rpx}
.material-button{flex-shrink:0;margin:0;padding:0 20rpx;line-height:64rpx;font-size:24rpx;color:#1677d5;background:#eff6ff;border-radius:10rpx}
.material-button::after{border:1rpx solid #dfeafa}
.material-image,.material-video{display:block;width:100%;margin:0 0 26rpx}
.material-video{height:360rpx}
.material-audio{padding:28rpx 0}
.material-text{display:block;white-space:pre-wrap;word-break:break-all;padding:8rpx 0 24rpx;line-height:1.7}
</style>
