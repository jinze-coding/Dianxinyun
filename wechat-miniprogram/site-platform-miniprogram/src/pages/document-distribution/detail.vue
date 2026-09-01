<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import AppNavBar from '@/components/AppNavBar.vue';
import {
  confirmDocumentDistribution,
  disputeDocumentDistribution,
  downloadDistributionFile,
  getMyDocumentDistribution,
  scanDocumentDistribution,
  type DocumentDistributionDetail,
  type DocumentDistributionItem
} from '@/api/documentDistribution';
import { getQueryNumber, showToast } from '@/utils/navigation';

const distributionId = ref(0);
const detail = ref<DocumentDistributionDetail | null>(null);
const loading = ref(true);
const busy = ref(false);
const errorMessage = ref('');
const scannedScene = ref('');
const hasSignature = ref(false);
const disputeOpen = ref(false);
const disputeNote = ref('');
let signatureContext: UniApp.CanvasContext | null = null;
let drawing = false;

const recipient = computed(() => detail.value?.currentRecipient);
const pending = computed(() => recipient.value?.status === 'PENDING');
const mustScan = computed(() => Boolean(detail.value?.scanRequiredForCurrentRecipient));
const scanReady = computed(() => !mustScan.value || Boolean(scannedScene.value));

onLoad(async (options) => {
  distributionId.value = getQueryNumber(options?.id, 0);
  if (!distributionId.value) { errorMessage.value = '缺少发放批次编号'; loading.value = false; return; }
  await loadDetail();
});

async function loadDetail() {
  loading.value = true; errorMessage.value = '';
  try { detail.value = await getMyDocumentDistribution(distributionId.value); }
  catch (error) { errorMessage.value = error instanceof Error ? error.message : '图纸签收任务加载失败'; }
  finally { loading.value = false; }
}

function goBack() {
  getCurrentPages().length > 1 ? uni.navigateBack() : uni.switchTab({ url: '/pages/todo/index' });
}
function formatTime(value?: string) { return value ? value.replace('T', ' ').slice(0, 16) : '—'; }
function statusLabel(value?: string) {
  return ({ PENDING: '待签收', CONFIRMED: '已签收', DISPUTED: '已提出异议' } as Record<string,string>)[String(value)] || value || '-';
}
function typeLabel(value?: string) { return value === 'DRAWING' ? '图纸' : '技术文件'; }
function channelLabel(value?: string) { return ({ ELECTRONIC: '电子', PAPER: '纸质', BOTH: '电子 + 纸质' } as Record<string,string>)[String(value)] || value || '-'; }

async function scanBatchCode() {
  if (busy.value) return;
  try {
    const result = await new Promise<UniApp.ScanCodeSuccessRes>((resolve, reject) => uni.scanCode({
      scanType: ['qrCode'], success: resolve, fail: reject
    }));
    const scene = String(result.result || '').trim();
    if (!scene) throw new Error('未识别到有效二维码');
    busy.value = true;
    const resolved = await scanDocumentDistribution(scene);
    if (resolved.id !== distributionId.value) throw new Error('二维码与当前签收任务不一致');
    scannedScene.value = scene;
    detail.value = resolved;
    showToast('领取二维码核验成功');
  } catch (error) {
    const text = error instanceof Error ? error.message : '扫码失败';
    if (!text.includes('cancel')) showToast(text);
  } finally { busy.value = false; }
}

function ensureContext() {
  if (!signatureContext) signatureContext = uni.createCanvasContext('receiptSignature');
  return signatureContext;
}
function signatureStart(event: any) {
  const touch = event.touches?.[0]; if (!touch) return;
  const context = ensureContext(); drawing = true; context.beginPath(); context.moveTo(touch.x, touch.y);
}
function signatureMove(event: any) {
  if (!drawing) return; const touch = event.touches?.[0]; if (!touch) return;
  const context = ensureContext(); context.setStrokeStyle('#17273a'); context.setLineWidth(3); context.setLineCap('round');
  context.lineTo(touch.x, touch.y); context.stroke(); context.draw(true); hasSignature.value = true;
}
function signatureEnd() { drawing = false; }
function clearSignature() {
  const context = ensureContext(); context.clearRect(0, 0, 700, 240); context.draw(); hasSignature.value = false;
}
function signaturePath() {
  return new Promise<string>((resolve, reject) => uni.canvasToTempFilePath({
    canvasId: 'receiptSignature', fileType: 'png', quality: 1,
    success: (result) => resolve(result.tempFilePath), fail: reject
  }));
}

async function confirmReceipt() {
  if (!detail.value || !pending.value || busy.value) return;
  if (!scanReady.value) { showToast('请先扫描技术员展示的批次二维码'); return; }
  if (detail.value.signatureRequiredForCurrentRecipient && !hasSignature.value) { showToast('请先完成手写签名'); return; }
  const confirmed = await new Promise<boolean>((resolve) => uni.showModal({
    title: '确认签收', content: `确认已核对 ${detail.value?.items.length || 0} 份文件及版本信息？`,
    success: (result) => resolve(Boolean(result.confirm)), fail: () => resolve(false)
  }));
  if (!confirmed) return;
  busy.value = true;
  try {
    const path = detail.value.signatureRequiredForCurrentRecipient ? await signaturePath() : undefined;
    detail.value = await confirmDocumentDistribution(detail.value.id, scannedScene.value || undefined, path);
    showToast('图纸资料签收成功');
  } catch (error) { showToast(error instanceof Error ? error.message : '图纸签收失败'); }
  finally { busy.value = false; }
}

async function submitDispute() {
  if (!detail.value || !disputeNote.value.trim() || busy.value) return;
  busy.value = true;
  try {
    detail.value = await disputeDocumentDistribution(detail.value.id, disputeNote.value.trim());
    disputeOpen.value = false; disputeNote.value = ''; showToast('异议已提交，本次未形成签收');
  } catch (error) { showToast(error instanceof Error ? error.message : '异议提交失败'); }
  finally { busy.value = false; }
}

async function openFile(item: DocumentDistributionItem) {
  if (busy.value) return; busy.value = true;
  try {
    let path: string;
    try {
      path = await downloadDistributionFile(item, distributionId.value);
    } catch (downloadError) {
      const text = downloadError instanceof Error ? downloadError.message : '';
      if (!text.includes('已被替代')) throw downloadError;
      const accepted = await new Promise<boolean>((resolve) => uni.showModal({
        title: '历史版本警告', content: `${text}\n仍要下载本批次精确版本吗？`,
        confirmText: '继续下载', success: (result) => resolve(Boolean(result.confirm)), fail: () => resolve(false)
      }));
      if (!accepted) return;
      path = await downloadDistributionFile(item, distributionId.value, true);
    }
    uni.openDocument({ filePath: path, showMenu: true,
      fail: () => showToast('该格式需在电脑或专业软件中打开，文件已下载到临时目录') });
  } catch (error) { showToast(error instanceof Error ? error.message : '资料文件下载失败'); }
  finally { busy.value = false; }
}
</script>

<template>
  <view class="page">
    <AppNavBar title="图纸签收" @back="goBack" />
    <scroll-view scroll-y class="scroll">
      <view class="content">
        <view v-if="loading" class="state">正在加载签收任务…</view>
        <view v-else-if="errorMessage" class="state error"><text>{{ errorMessage }}</text><button @tap="loadDetail">重新加载</button></view>
        <template v-else-if="detail">
          <view class="status-card" :class="String(recipient?.status || '').toLowerCase()">
            <view class="mark">图</view><view><text>{{ statusLabel(recipient?.status) }}</text><text>{{ detail.distributionNo }} · {{ channelLabel(recipient?.channel) }}</text></view>
            <text v-if="detail.overdue" class="overdue">已逾期</text>
          </view>
          <view class="card info"><view><text>签收期限</text><text>{{ formatTime(detail.deadline) }}</text></view><view><text>接收人</text><text>{{ recipient?.realName }}</text></view><view><text>项目角色</text><text>{{ recipient?.roleNames || '项目成员' }}</text></view><view v-if="detail.notificationTemplate"><text>通知内容</text><text>{{ detail.notificationTemplate }}</text></view><view v-if="detail.messageNote"><text>发放说明</text><text>{{ detail.messageNote }}</text></view></view>
          <view class="card"><view class="section-head"><text>文件与精确版本</text><text>共 {{ detail.items.length }} 份</text></view>
            <view v-for="item in detail.items" :key="item.id" class="file-row">
              <view><text>{{ item.documentNo || '无编号' }} · {{ item.title }}</text><text>{{ typeLabel(item.documentType) }} · V{{ item.systemVersionNo }}<template v-if="item.externalRevision"> / {{ item.externalRevision }}</template></text><text>{{ item.fileName }}<template v-if="item.paperCopyCount"> · 纸质 {{ item.paperCopyCount }} 份</template></text></view>
              <button :disabled="busy" @tap="openFile(item)">下载核对</button>
            </view>
          </view>
          <template v-if="pending">
            <view v-if="mustScan" class="card scan-card"><text>纸质领取需扫描技术员展示的长期有效批次二维码，核验通过后才能确认。</text><button :class="{ ready: scannedScene }" :disabled="busy" @tap="scanBatchCode">{{ scannedScene ? '二维码已核验' : '扫描领取二维码' }}</button></view>
            <view v-if="detail.signatureRequiredForCurrentRecipient" class="card"><view class="section-head"><text>手写签名</text><button @tap="clearSignature">清空</button></view><canvas canvas-id="receiptSignature" class="signature" disable-scroll @touchstart="signatureStart" @touchmove="signatureMove" @touchend="signatureEnd" /></view>
            <button class="confirm" :disabled="busy || !scanReady" @tap="confirmReceipt">{{ busy ? '正在提交…' : '确认签收' }}</button>
            <button class="dispute" :disabled="busy" @tap="disputeOpen = true">文件、版本或份数不符，提交异议</button>
          </template>
          <view v-else-if="recipient?.status === 'CONFIRMED'" class="result success">已于 {{ formatTime(recipient.confirmedTime) }} 完成签收，重复扫码将返回本回执。</view>
          <view v-else class="result warning">已提交异议，本次未形成签收：{{ recipient?.disputeNote }}</view>
        </template>
      </view>
    </scroll-view>
    <view v-if="disputeOpen" class="overlay" @tap.self="disputeOpen = false"><view class="sheet"><view><text>提交签收异议</text><button @tap="disputeOpen = false">×</button></view><textarea v-model="disputeNote" maxlength="500" placeholder="请说明文件、版次或纸质份数哪里不符" /><button class="confirm" :disabled="busy || !disputeNote.trim()" @tap="submitDispute">提交异议（不会签收）</button></view></view>
  </view>
</template>

<style scoped>
.page{min-height:100vh;background:#f3f5f7;color:#233548}.scroll{height:calc(100vh - 92rpx)}.content{display:flex;flex-direction:column;gap:18rpx;padding:22rpx 24rpx calc(42rpx + env(safe-area-inset-bottom))}.state{display:flex;min-height:260rpx;align-items:center;justify-content:center;flex-direction:column;border-radius:18rpx;background:#fff;color:#7d8996}.state.error{color:#b44e4e}.state button{margin-top:18rpx;padding:0 22rpx;min-height:58rpx;border-radius:12rpx;background:#315f86;color:#fff}.status-card{display:flex;align-items:center;gap:16rpx;padding:22rpx;border-radius:18rpx;background:#fff4df}.status-card.confirmed{background:#e7f6ee}.status-card.disputed{background:#fdecec}.mark{display:flex;width:62rpx;height:62rpx;align-items:center;justify-content:center;border-radius:15rpx;background:#315f86;color:#fff;font-size:27rpx;font-weight:900}.status-card>view:nth-child(2){min-width:0;flex:1}.status-card>view:nth-child(2) text{display:block}.status-card>view:nth-child(2) text:first-child{font-size:27rpx;font-weight:850}.status-card>view:nth-child(2) text:last-child{margin-top:5rpx;color:#73808d;font-size:19rpx}.overdue{padding:6rpx 10rpx;border-radius:999rpx;background:#b94d49;color:#fff;font-size:17rpx}.card{padding:20rpx 22rpx;border-radius:18rpx;background:#fff;box-shadow:0 8rpx 24rpx rgba(43,56,72,.04)}.info>view{display:flex;min-height:65rpx;align-items:center;justify-content:space-between;gap:20rpx;border-bottom:1rpx solid #edf0f2}.info>view:last-child{border-bottom:0}.info text:first-child{flex-shrink:0;color:#7d8996;font-size:20rpx}.info text:last-child{color:#35485a;font-size:21rpx;text-align:right}.section-head{display:flex;align-items:center;justify-content:space-between}.section-head>text:first-child{font-size:24rpx;font-weight:820}.section-head>text:last-child,.section-head button{color:#7f8c98;font-size:19rpx}.file-row{display:flex;align-items:center;gap:14rpx;padding:17rpx 0;border-bottom:1rpx solid #edf0f2}.file-row:last-child{border-bottom:0}.file-row>view{min-width:0;flex:1}.file-row text{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.file-row text:first-child{font-size:21rpx;font-weight:750}.file-row text:nth-child(2),.file-row text:nth-child(3){margin-top:6rpx;color:#7f8c98;font-size:18rpx}.file-row button{flex-shrink:0;padding:0 14rpx;min-height:54rpx;border-radius:11rpx;background:#e8eff4;color:#315f86;font-size:18rpx}.scan-card text{display:block;color:#667584;font-size:20rpx;line-height:1.55}.scan-card button{width:100%;min-height:68rpx;margin-top:15rpx;border-radius:12rpx;background:#315f86;color:#fff;font-weight:760}.scan-card button.ready{background:#2d7b5f}.signature{width:100%;height:240rpx;margin-top:15rpx;border:1rpx solid #dce3e8;border-radius:12rpx;background:#fff}.confirm,.dispute{width:100%;min-height:76rpx;border-radius:13rpx;background:#2d7b5f;color:#fff;font-size:23rpx;font-weight:800}.confirm[disabled]{opacity:.5}.dispute{background:#fff;color:#a34c48;border:1rpx solid #e5c2c0;font-size:20rpx}.result{padding:22rpx;border-radius:16rpx;font-size:21rpx;line-height:1.6}.result.success{background:#e7f6ee;color:#267155}.result.warning{background:#fff2dc;color:#885f20}.overlay{position:fixed;z-index:90;inset:0;display:flex;align-items:flex-end;background:rgba(20,32,45,.45)}.sheet{width:100%;padding:22rpx 24rpx calc(26rpx + env(safe-area-inset-bottom));border-radius:24rpx 24rpx 0 0;background:#fff}.sheet>view{display:flex;justify-content:space-between;align-items:center}.sheet>view text{font-size:27rpx;font-weight:820}.sheet>view button{font-size:28rpx}.sheet textarea{box-sizing:border-box;width:100%;min-height:180rpx;margin:18rpx 0;padding:16rpx;border:1rpx solid #dce3e8;border-radius:13rpx;background:#f8fafb;font-size:21rpx}
</style>
