<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { onHide, onShow } from '@dcloudio/uni-app';
import { publicMaterialUrl, resolveMeetingMaterials, type PublicMeetingMaterial } from '@/api/meetingMaterials';
const props = defineProps<{ inviteToken: string }>();
const records = ref<PublicMeetingMaterial[]>([]);
const title = ref('');
const ended = ref(false);
const error = ref('');
const keyword = ref('');
const category = ref('');
const page = ref(1);
const selected = ref<PublicMeetingMaterial>();
const textPreview = ref('');
const textPage = ref(0);
const audioPlaying = ref(false);
const audioTime = ref(0);
const audioDuration = ref(0);
let audio: UniApp.InnerAudioContext | undefined;
const downloading = ref('');
const progress = ref(0);
const categories: Record<string, string> = { PUBLICITY: '宣发通知', AGENDA: '议程课件', MINUTES: '会议纪要', MEDIA: '现场影像', OTHER: '其他' };
const categoryKeys = ['', ...Object.keys(categories)];
const labels = ['全部分类', ...Object.values(categories)];
const filtered = computed(() => records.value.filter((row) => (!category.value || row.category === category.value) && (!keyword.value.trim() || row.title.includes(keyword.value.trim()))));
const visible = computed(() => filtered.value.slice((page.value - 1) * 10, page.value * 10));
let epoch = 0;
let textEpoch = 0;
let timer: ReturnType<typeof setInterval> | undefined;
let task: UniApp.DownloadTask | undefined;
let localPath = '';
let active = true;
function size(bytes: number) { return bytes >= 1073741824 ? `${(bytes / 1073741824).toFixed(1)} GB` : bytes >= 1048576 ? `${(bytes / 1048576).toFixed(1)} MB` : `${Math.ceil(bytes / 1024)} KB`; }
function removeTemporary() {
  // #ifdef MP-WEIXIN
  if (localPath) uni.getFileSystemManager().unlink({ filePath: localPath, fail: () => {} });
  // #endif
  localPath = '';
}
async function refresh() {
  const ticket = ++epoch;
  if (!props.inviteToken) return;
  try {
    const data = await resolveMeetingMaterials(props.inviteToken);
    if (ticket !== epoch || !active) return;
    records.value = data.records; title.value = data.title; ended.value = data.ended; error.value = '';
    if (selected.value && !data.records.some((row) => row.publicCode === selected.value?.publicCode)) selected.value = undefined;
    if (downloading.value && !data.records.some((row) => row.publicCode === downloading.value)) { task?.abort(); downloading.value = ''; removeTemporary(); }
    page.value = Math.min(page.value, Math.max(1, Math.ceil(filtered.value.length / 10)));
  } catch (e) {
    if (ticket !== epoch || !active) return;
    records.value = []; selected.value = undefined; task?.abort(); downloading.value = ''; removeTemporary();
    error.value = e instanceof Error ? e.message : '会议资料读取失败';
  }
}
function start() { active = true; void refresh(); if (timer) clearInterval(timer); timer = setInterval(refresh, 30000); }
function stop() { active = false; epoch += 1; if (timer) clearInterval(timer); timer = undefined; selected.value = undefined; task?.abort(); downloading.value = ''; }
watch(() => props.inviteToken, start, { immediate: true });
watch([keyword, category], () => { page.value = 1; });
watch(() => selected.value?.publicCode, () => {
  audio?.destroy(); audio = undefined; audioPlaying.value = false; audioTime.value = 0; audioDuration.value = 0;
  if (selected.value?.previewKind !== 'AUDIO') return;
  audio = uni.createInnerAudioContext(); audio.src = publicMaterialUrl(selected.value.publicCode);
  audio.onTimeUpdate(() => { audioTime.value = audio?.currentTime || 0; audioDuration.value = audio?.duration || 0; });
  audio.onPlay(() => { audioPlaying.value = true; }); audio.onPause(() => { audioPlaying.value = false; });
  audio.onEnded(() => { audioPlaying.value = false; });
  audio.onError(() => { audioPlaying.value = false; uni.showToast({ title: '音频暂时无法播放，请刷新或下载', icon: 'none' }); });
});
function playAudio() { if (audioPlaying.value) audio?.pause(); else audio?.play(); }
function seekAudio(event: { detail: { value: number } }) { if (audioDuration.value) audio?.seek(event.detail.value * audioDuration.value / 100); }
onShow(start);
onHide(stop);
onBeforeUnmount(() => { stop(); audio?.destroy(); removeTemporary(); });
async function open(row: PublicMeetingMaterial, original = false) {
  if (downloading.value) return;
  await refresh();
  if (!records.value.some((item) => item.publicCode === row.publicCode)) return;
  const inline = ['IMAGE', 'HEIF', 'VIDEO', 'AUDIO'].includes(row.previewKind);
  if (!original && row.previewStatus === 'READY' && inline) { selected.value = row; return; }
  if (!original && row.previewKind === 'TEXT') { selected.value = row; textPage.value = 0; await loadText(row, 0); return; }
  const isDocument = ['PDF', 'OFFICE'].includes(row.previewKind);
  const usePreview = !original && row.previewStatus === 'READY' && isDocument;
  downloading.value = row.publicCode; progress.value = 0; removeTemporary();
  task = uni.downloadFile({
    url: publicMaterialUrl(row.publicCode, usePreview), timeout: 1200000,
    success: async (response) => {
      if (response.statusCode !== 200) { uni.showToast({ title: '资料已撤下或读取失败，请刷新', icon: 'none' }); return; }
      localPath = response.tempFilePath;
      const extension = usePreview ? 'pdf' : row.fileName.split('.').pop()?.toLowerCase();
      if (['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx'].includes(extension || '')) {
        uni.openDocument({ filePath: localPath, fileType: extension, showMenu: true, fail: () => uni.showToast({ title: '当前设备无法预览，请下载查看', icon: 'none' }) });
      } else {
        // #ifdef MP-WEIXIN
        uni.shareFileMessage({ filePath: localPath, fileName: row.fileName, fail: () => uni.showToast({ title: '已取消文件操作', icon: 'none' }) });
        // #endif
      }
    },
    fail: () => { if (active) uni.showToast({ title: '下载中断，请检查网络和设备剩余空间后重试', icon: 'none' }); },
    complete: () => { downloading.value = ''; task = undefined; }
  });
  task.onProgressUpdate((event) => { progress.value = event.progress; });
}
async function loadText(row: PublicMeetingMaterial, part: number) {
  const ticket = ++textEpoch;
  textPreview.value = '正在读取…';
  uni.request({ url: publicMaterialUrl(row.publicCode), dataType: 'text',
    header: { Range: `bytes=${part * 1048576}-${(part + 1) * 1048576 - 1}` },
    success: (response) => { if (ticket !== textEpoch || selected.value?.publicCode !== row.publicCode) return;
      textPreview.value = [200, 206].includes(response.statusCode) ? String(response.data) : '资料已更新或撤下，请刷新'; textPage.value = part; },
    fail: () => { if (ticket === textEpoch && selected.value?.publicCode === row.publicCode) textPreview.value = '内容读取失败，请稍后重试'; }
  });
}
</script>

<template>
  <view class="public-materials">
    <view class="materials-head"><text class="materials-title">会议资料</text><button size="mini" @tap="refresh">刷新</button></view>
    <text v-if="ended" class="materials-note">{{ title }} · 会议已结束，可继续查看公开资料</text>
    <text v-else class="materials-note">无需预约或签到即可查看已公开资料</text>
    <view v-if="error" class="materials-error">{{ error }}</view>
    <view v-else>
      <view class="materials-filter"><input v-model="keyword" placeholder="搜索资料名称" maxlength="200" /><picker :range="labels" @change="category = categoryKeys[Number($event.detail.value)]"><text>{{ categories[category] || '全部分类' }} ▾</text></picker></view>
      <view v-for="row in visible" :key="row.publicCode" class="material-item">
        <text class="material-name">{{ row.title }}</text><text class="materials-note">{{ categories[row.category] }} · V{{ row.versionNo }} · {{ size(row.fileSize) }}</text><text v-if="row.description" class="material-description">{{ row.description }}</text>
        <view class="material-buttons"><button size="mini" :disabled="!!downloading" @tap="open(row)">{{ row.previewStatus === 'READY' && row.previewKind !== 'UNSUPPORTED' ? '查看资料' : '下载查看' }}</button><button size="mini" :disabled="!!downloading" @tap="open(row, true)">下载原文件</button></view>
        <progress v-if="downloading === row.publicCode" :percent="progress" show-info stroke-width="4" />
      </view>
      <text v-if="!filtered.length" class="materials-note">暂无公开资料</text>
      <view v-if="filtered.length > 10" class="materials-pages"><button size="mini" :disabled="page <= 1" @tap="page--">上一页</button><text>{{ page }} / {{ Math.ceil(filtered.length / 10) }}</text><button size="mini" :disabled="page * 10 >= filtered.length" @tap="page++">下一页</button></view>
    </view>
    <view v-if="selected" class="material-overlay"><view class="material-viewer"><view class="materials-head"><text>{{ selected.title }}</text><button size="mini" @tap="selected = undefined">关闭</button></view>
      <image v-if="['IMAGE', 'HEIF'].includes(selected.previewKind)" :src="publicMaterialUrl(selected.publicCode)" mode="widthFix" />
      <video v-else-if="selected.previewKind === 'VIDEO'" :src="publicMaterialUrl(selected.publicCode)" controls />
      <view v-else-if="selected.previewKind === 'AUDIO'" class="material-audio"><button size="mini" @tap="playAudio">{{ audioPlaying ? '暂停' : '播放音频' }}</button><slider :value="audioDuration ? audioTime / audioDuration * 100 : 0" @change="seekAudio" /><text>{{ Math.floor(audioTime) }} / {{ Math.floor(audioDuration) }} 秒</text></view>
      <view v-else-if="selected.previewKind === 'TEXT'"><text class="material-text">{{ textPreview }}</text><view class="materials-pages"><button size="mini" :disabled="textPage === 0" @tap="loadText(selected, textPage - 1)">上一段</button><text>{{ textPage + 1 }}</text><button size="mini" :disabled="(textPage + 1) * 1048576 >= selected.fileSize" @tap="loadText(selected, textPage + 1)">下一段</button></view></view>
      <button size="mini" @tap="open(selected, true)">下载原文件</button>
    </view></view>
  </view>
</template>

<style scoped>
.public-materials{background:#fff;border:1rpx solid #dfe8f2;border-radius:18rpx;padding:28rpx;margin:24rpx 0;color:#263e53;font-size:26rpx}.materials-head{display:flex;align-items:center;justify-content:space-between;gap:20rpx}.materials-head button{margin:0;flex:none}.materials-title{font-size:32rpx;font-weight:700}.materials-note{display:block;color:#71869a;margin:12rpx 0;line-height:1.6;font-size:24rpx}.materials-error{color:#ac4141;padding:20rpx 0}.materials-filter{display:flex;gap:16rpx;align-items:center;margin-top:24rpx}.materials-filter input{flex:1;min-width:0;background:#f3f7fb;padding:16rpx;border-radius:10rpx}.material-item{border-bottom:1rpx solid #e8eef5;padding:26rpx 0;word-break:break-all}.material-name{font-size:29rpx;font-weight:600}.material-description{display:block;white-space:pre-wrap;line-height:1.6}.material-buttons,.materials-pages{display:flex;align-items:center;gap:16rpx;margin-top:18rpx}.material-buttons button{margin:0;color:#1677d5;background:#eff6ff;border:1rpx solid #dfeafa}.material-overlay{position:fixed;inset:0;background:#182f4a80;z-index:1000;display:flex;align-items:center;padding:20rpx}.material-viewer{width:100%;max-height:90vh;overflow:auto;background:#fff;padding:20rpx;border-radius:16rpx;box-sizing:border-box}.material-viewer image,.material-viewer video{width:100%;margin:20rpx 0}.material-viewer audio{margin:30rpx 0}
</style>

<style scoped>.material-text{display:block;white-space:pre-wrap;word-break:break-all;padding:24rpx 0;line-height:1.7}</style>
