<script setup lang="ts">
import { computed, getCurrentInstance, nextTick, ref } from 'vue';
import { onShow, onHide, onUnload } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import { useAuthStore } from '@/stores/auth';
import { validateCommitteeFile, type SelectedFile } from '@/api/safetyCommittee';

const instance = getCurrentInstance();
const auth = useAuthStore();
const cameraReady = ref(false);
const allowed = ref(false);
const mounted = ref(false);
const devicePosition = ref<'back' | 'front'>('back');
const phase = ref<'idle' | 'authorizing' | 'starting' | 'recording' | 'stopping' | 'processing'>('idle');
const seconds = ref(0);
const result = ref<SelectedFile>();
const kind = ref<'photo' | 'video'>('photo');
const error = ref('');
const permissionError = ref(false);
const configurationError = ref(false);
const busy = computed(() => phase.value !== 'idle');
const recording = computed(() => phase.value === 'recording');
const timerText = computed(() => `${String(Math.floor(seconds.value / 60)).padStart(2, '0')}:${String(seconds.value % 60).padStart(2, '0')}`);
const shutterHint = computed(() => recording.value ? '松手结束录制' : busy.value ? '正在处理，请稍候…' : '点击拍照 · 长按录像');
let camera: ReturnType<typeof uni.createCameraContext> | undefined;
let held = false;
let longPress = false;
let visible = false;
let disposed = false;
let transferred = false;
let showVersion = 0;
let operation = 0;
let settled = false;
let pendingPath = '';
let pendingThumbnailPath = '';
let pressTimer: ReturnType<typeof setTimeout> | undefined;
let durationTimer: ReturnType<typeof setInterval> | undefined;

function context() { return camera ||= uni.createCameraContext(); }
function authorize(scope: string) {
  return new Promise<void>((resolve, reject) => uni.authorize({ scope, success: () => resolve(), fail: reject }));
}
function authorizationFailed(cause: unknown, scope: 'camera' | 'record') {
  const native = cause as { errno?: number; errMsg?: string; message?: string; detail?: { errMsg?: string } };
  const message = native?.detail?.errMsg || native?.errMsg || native?.message || '';
  const missingDeclaration = Number(native?.errno) === 112 || /not declared in the privacy agreement/i.test(message);
  const denied = /auth deny|denied|denial|permission denied|拒绝/i.test(message);
  console.warn('[committee-camera] authorization failed', scope, native?.errno, message);
  configurationError.value = missingDeclaration;
  permissionError.value = !missingDeclaration && denied;
  if (missingDeclaration) {
    error.value = `${scope === 'camera' ? '拍摄' : '录像'}暂不可用：小程序隐私声明未完善，请联系管理员处理。`;
  } else if (denied) {
    error.value = scope === 'camera' ? '请在权限设置中允许使用相机；若微信无相机权限，请在手机设置中开启。' : '录像需要麦克风权限，请在权限设置中开启；照片仍可拍摄。';
  } else {
    error.value = `${scope === 'camera' ? '相机' : '麦克风'}授权失败，请重试${message ? `（${message}）` : '。'}`;
  }
}
function discard(path?: string) {
  if (!path || (transferred && [result.value?.path,result.value?.thumbnailPath].includes(path))) return;
  try { uni.getFileSystemManager().unlink({ filePath: path, fail: () => {} }); } catch { /* Native temporary files also expire with the session. */ }
}
async function prepareCamera() {
  const version = ++showVersion;
  if (!await auth.ensureRootAccess('/pages/safety-committee/index') || disposed || !visible || version !== showVersion) return;
  if (result.value || busy.value) return;
  try {
    await authorize('scope.camera');
    if (disposed || !visible || version !== showVersion) return;
    allowed.value = true;
    cameraReady.value = false;
    mounted.value = false;
    await nextTick();
    if (disposed || !visible || version !== showVersion) return;
    mounted.value = true;
    permissionError.value = false;
    configurationError.value = false;
    error.value = '';
  } catch (cause) {
    if (disposed || !visible || version !== showVersion) return;
    allowed.value = false;
    cameraReady.value = false;
    mounted.value = false;
    authorizationFailed(cause, 'camera');
  }
}
onShow(() => { visible = true; void prepareCamera(); });
function fail(cause: unknown, id = operation) {
  if (disposed || id !== operation || settled) return;
  clearInterval(durationTimer);
  phase.value = 'idle';
  const native = cause as { errMsg?: string; detail?: { errMsg?: string }; message?: string };
  error.value = native?.detail?.errMsg || native?.errMsg || native?.message || '拍摄中断，请重试。';
}
async function accept(path: string, type: 'photo' | 'video', id: number, thumbnailPath?: string) {
  if (disposed || id !== operation || settled) {
    for(const candidate of new Set([path,thumbnailPath])) {
      if(![pendingPath,pendingThumbnailPath,result.value?.path,result.value?.thumbnailPath].includes(candidate)) discard(candidate);
    }
    return;
  }
  settled = true;
  clearInterval(durationTimer);
  phase.value = 'processing';
  pendingPath = path;
  pendingThumbnailPath = thumbnailPath || '';
  try {
    if (!path) throw new Error('没有获取到拍摄文件，请重拍。');
    const info = await new Promise<{ size: number }>((resolve, reject) => uni.getFileInfo({ filePath: path, success: resolve, fail: reject }));
    if (disposed || id !== operation) { discard(path); discard(thumbnailPath); return; }
    const extension = path.match(/\.([a-z0-9]+)$/i)?.[1] || (type === 'video' ? 'mp4' : 'jpg');
    const file: SelectedFile = { path, size: info.size, name: `现场${Date.now()}.${extension}`, thumbnailPath: type === 'photo' ? path : thumbnailPath, capturedTemporary: true };
    validateCommitteeFile(file);
    kind.value = type;
    result.value = file;
    mounted.value = false;
  } catch (cause) {
    discard(path);
    discard(thumbnailPath);
    if (!disposed && id === operation) error.value = (cause as Error).message || '拍摄文件读取失败，请重拍。';
  } finally {
    if (!disposed && id === operation) { pendingPath = ''; pendingThumbnailPath = ''; phase.value = 'idle'; }
  }
}
function photo() {
  const id = ++operation;
  settled = false;
  phase.value = 'processing';
  try { context().takePhoto({ quality: 'high', success: r => void accept(r.tempImagePath, 'photo', id), fail: e => fail(e, id) }); }
  catch (e) { fail(e, id); }
}
async function start() {
  const id = ++operation;
  settled = false;
  longPress = true;
  phase.value = 'authorizing';
  try { await authorize('scope.record'); }
  catch (cause) {
    if (!disposed && id === operation) {
      phase.value = 'idle';
      authorizationFailed(cause, 'record');
    }
    return;
  }
  if (disposed || id !== operation) return;
  if (!held || !visible) { phase.value = 'idle'; error.value = '麦克风已就绪，请再次长按录像。'; return; }
  phase.value = 'starting';
  const options: UniApp.CameraContextStartRecordOptions & { timeout: number } = {
    timeout: 300,
    success: () => {
      if (id !== operation || settled) return;
      phase.value = 'recording';
      seconds.value = 0;
      const started = Date.now();
      durationTimer = setInterval(() => {
        seconds.value = Math.min(300, Math.floor((Date.now() - started) / 1000));
        if (seconds.value >= 300) stop();
      }, 250);
      if (!held || !visible || disposed) stop();
    },
    timeoutCallback: r => void accept(r.tempVideoPath, 'video', id, r.tempThumbPath),
    fail: e => fail(e, id)
  };
  try { context().startRecord(options); } catch (e) { fail(e, id); }
}
function stop() {
  if (!recording.value) return;
  const id = operation;
  phase.value = 'stopping';
  clearInterval(durationTimer);
  try { context().stopRecord({ success: r => void accept(r.tempVideoPath, 'video', id, r.tempThumbPath), fail: e => fail(e, id) }); }
  catch (e) { fail(e, id); }
}
function press() {
  if (!visible || disposed || held || !cameraReady.value || busy.value || result.value) return;
  held = true;
  longPress = false;
  error.value = '';
  permissionError.value = false;
  configurationError.value = false;
  pressTimer = setTimeout(() => void start(), 350);
}
function release() {
  if (!held) return;
  held = false;
  clearTimeout(pressTimer);
  if (longPress) stop();
  else if (visible && !disposed) photo();
}
function cancelPress() { held = false; clearTimeout(pressTimer); stop(); }
function interrupt() {
  visible = false;
  showVersion++;
  if (phase.value === 'recording' || phase.value === 'starting') error.value = '录制已中断，请核对预览后确认使用。';
  cancelPress();
}
function cameraStopped() {
  cameraReady.value = false;
  if (!visible || result.value || settled) return;
  cancelPress();
  error.value = '相机已暂停，请重启相机后继续拍摄。';
}
function cameraFailure(e: unknown) {
  cameraReady.value = false;
  if (disposed || !visible) return;
  fail(e);
  const native = e as { errno?: number; errMsg?: string; detail?: { errMsg?: string } };
  if (Number(native?.errno) === 112 || /auth|permission|privacy/i.test(native?.detail?.errMsg || native?.errMsg || '')) authorizationFailed(e, 'camera');
}
onHide(interrupt);
onUnload(() => {
  disposed = true;
  interrupt();
  clearInterval(durationTimer);
  for (const path of new Set([result.value?.path,result.value?.thumbnailPath,pendingPath,pendingThumbnailPath])) discard(path);
});
function confirm() {
  if (!result.value || transferred || disposed || !visible) return;
  const channel = (instance?.proxy as { getOpenerEventChannel?: () => { emit: (name: string, file: SelectedFile) => void } })?.getOpenerEventChannel?.();
  if (!channel) { error.value = '原上报页面已关闭，请返回后重新拍摄。'; return; }
  transferred = true;
  channel.emit('captured', result.value);
  uni.navigateBack();
}
async function restartCamera() {
  if (busy.value) return;
  cameraReady.value = false;
  mounted.value = false;
  await nextTick();
  await prepareCamera();
}
async function retake() { for(const path of new Set([result.value?.path,result.value?.thumbnailPath]))discard(path); result.value = undefined; settled = false; await restartCamera(); }
function switchCamera() {
  if (busy.value || held || !cameraReady.value) return;
  devicePosition.value = devicePosition.value === 'back' ? 'front' : 'back';
}
function permissions() {
  uni.openSetting({
    success: () => { if (!disposed && visible) void restartCamera(); },
    fail: () => { if (!disposed && visible) error.value = '权限设置打开失败，请稍后重试，或从小程序右上角设置进入。'; }
  });
}
function back() { uni.navigateBack(); }
</script>

<template>
  <view class="capture-page">
    <view class="capture-nav"><AppNavBar title="现场拍摄" @back="back" /></view>
    <view class="capture-preview">
      <template v-if="result">
        <video v-if="kind === 'video'" class="camera-view" :src="result.path" controls :autoplay="false" object-fit="contain" />
        <image v-else class="camera-view" :src="result.path" mode="aspectFit" />
      </template>
      <camera v-else-if="allowed && mounted" class="camera-view" :device-position="devicePosition" flash="off" @initdone="cameraReady = true" @error="cameraFailure" @stop="cameraStopped" />
      <view v-else class="camera-placeholder"><text class="camera-placeholder-icon">◎</text><text>{{ configurationError ? '拍摄功能暂不可用' : '允许使用相机，记录现场情况' }}</text></view>
    </view>
    <view class="capture-controls">
      <view v-if="error" class="capture-error"><text>{{ error }}</text><button v-if="permissionError" class="capture-error-action" @tap="permissions">权限设置</button><button v-else-if="!result && !busy" class="capture-error-action" @tap="restartCamera">{{ configurationError ? '重新检查' : '重启相机' }}</button></view>
      <template v-if="result">
        <text class="capture-hint">{{ kind === 'video' ? '录像已完成' : '照片已拍摄' }}，确认后添加到现场附件</text>
        <view class="capture-confirm-actions"><button class="capture-retake" @tap="retake">重拍</button><button class="capture-confirm" @tap="confirm">确认使用</button></view>
      </template>
      <template v-else>
        <view class="capture-status"><view v-if="recording" class="recording-dot" /><text>{{ recording ? `${timerText} / 05:00` : shutterHint }}</text></view>
        <view class="capture-shutter-row">
          <button class="capture-side" @tap="back">取消</button>
          <view class="shutter" :class="{ recording, 'shutter-disabled': !cameraReady || (busy && !recording) }" role="button" aria-label="点击拍照，长按录像，松手结束" @touchstart.stop.prevent="press" @touchend.stop.prevent="release" @touchcancel.stop.prevent="cancelPress"><view class="shutter-core" /></view>
          <button class="capture-side capture-switch" :disabled="busy || !cameraReady" aria-label="切换前后摄像头" @tap="switchCamera"><text class="capture-switch-icon">↻</text><text>翻转</text></button>
        </view>
        <text class="capture-hint">{{ recording ? '松手结束录制' : '长按最长可录制 5 分钟' }}</text>
      </template>
    </view>
  </view>
</template>

<style scoped>
.capture-page { display: flex; flex-direction: column; height: 100vh; height: 100dvh; min-height: 0; overflow: hidden; background: #101113; color: #fff; }
.capture-nav { flex-shrink: 0; background: #fff; --workspace-text: #24364b; }
.capture-preview { position: relative; flex: 1; min-height: 80px; overflow: hidden; background: #08090b; }
.camera-view { display: block; width: 100%; height: 100%; }
.camera-placeholder { display: flex; height: 100%; flex-direction: column; align-items: center; justify-content: center; gap: 20rpx; color: #c1c7d0; font-size: 26rpx; }
.camera-placeholder-icon { font-size: 80rpx; color: #fff; }
.capture-controls { flex-shrink: 0; box-sizing: border-box; padding: 24rpx 28rpx calc(24rpx + env(safe-area-inset-bottom)); background: #17191d; }
.capture-status { display: flex; min-height: 40rpx; justify-content: center; align-items: center; gap: 14rpx; color: #fff; font-size: 27rpx; line-height: 1.5; font-variant-numeric: tabular-nums; }
.recording-dot { width: 14rpx; height: 14rpx; border-radius: 50%; background: #ff6262; }
.capture-shutter-row { display: flex; align-items: center; justify-content: space-around; padding: 24rpx 0; }
.shutter { display: flex; flex-shrink: 0; width: 144rpx; height: 144rpx; box-sizing: border-box; align-items: center; justify-content: center; border: 10rpx solid #8b8d91; border-radius: 50%; background: #e5e5e5; touch-action: none; transition: border-color .15s, transform .15s; }
.shutter-core { width: 112rpx; height: 112rpx; border-radius: 50%; background: #fff; transition: width .15s, height .15s, border-radius .15s; }
.shutter.recording { border-color: #21c77a; transform: scale(1.08); background: transparent; }
.shutter.recording .shutter-core { width: 54rpx; height: 54rpx; border-radius: 12rpx; }
.shutter-disabled { opacity: .4; }
.capture-side { display: flex; width: 112rpx; min-height: 88rpx; align-items: center; justify-content: center; margin: 0; padding: 0; border: 0; border-radius: 12rpx; background: transparent; color: #fff; font-size: 27rpx; line-height: 1.4; }
.capture-side::after, .capture-retake::after, .capture-confirm::after, .capture-error-action::after { border: 0; }
.capture-side[disabled] { color: #73767e; background: transparent; }
.capture-switch { flex-direction: column; gap: 2rpx; font-size: 22rpx; }
.capture-switch-icon { font-size: 48rpx; line-height: 1; }
.capture-hint { display: block; text-align: center; color: #c0c5cc; font-size: 23rpx; line-height: 1.6; }
.capture-confirm-actions { display: flex; gap: 24rpx; padding: 30rpx 0 10rpx; }
.capture-retake, .capture-confirm { flex: 1; display: flex; align-items: center; justify-content: center; min-height: 96rpx; margin: 0; border-radius: 48rpx; padding: 18rpx; border: 0; font-size: 28rpx; font-weight: 600; line-height: 1.4; }
.capture-retake { color: #fff; background: #35383e; }
.capture-confirm { color: #fff; background: #159c62; }
.capture-error { display: flex; max-height: 100px; overflow-y: auto; align-items: center; gap: 14rpx; margin-bottom: 16rpx; padding: 12rpx 16rpx; border-radius: 12rpx; background: #392d20; color: #ffe0ae; font-size: 23rpx; line-height: 1.5; }
.capture-error-action { flex-shrink: 0; margin: 0; padding: 12rpx; border: 0; border-radius: 8rpx; background: #57422b; color: #fff; font-size: 23rpx; line-height: 1.4; }
</style>
