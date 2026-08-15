import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const editorSource = readFileSync(new URL('./ProjectLocationEditor.jsx', import.meta.url), 'utf8');
const helperSource = readFileSync(new URL('./projectLocation.js', import.meta.url), 'utf8');
const pageSource = readFileSync(new URL('./index.jsx', import.meta.url), 'utf8');

test('project location editor exposes the manual-coordinate confirmation contract', () => {
  assert.match(editorSource, /<option value="BD09">/);
  assert.match(editorSource, /<option value="GCJ02">/);
  assert.match(editorSource, /<option value="WGS84">/);
  assert.match(editorSource, /previewCoordinateOnMap\(form, \{ confirmed: false/);
  assert.match(editorSource, /setForm\(\(current\) => \(\{ \.\.\.current, longitude, latitude, coordinateType: 'BD09' \}\)\)/,
    '地图点选或拖动后必须明确切换为 BD09');
  assert.match(editorSource, /extractBaiduEventLngLat\(event\)/,
    'BMapGL 地图点击必须读取 geographic latlng，不能直接写入墨卡托 event.point');
  assert.match(editorSource, /marker\.getPosition\?\.\(\)/,
    '标记拖动后必须优先读取标记的地理经纬度');
  assert.doesNotMatch(editorSource, /selectMapPointRef\.current\?\.\(event\.point/,
    '任何地图事件都不得把 event.point 未校验地写入经纬度');
  assert.match(editorSource, /mapState === 'error'[\s\S]*已明确确认手工坐标/,
    'AK 缺失时仍必须提供显式手工坐标确认');
  assert.match(editorSource, /disabled=\{saving \|\| routeImageUploading \|\| !pointConfirmed \|\| !dirty\}/,
    '保存不得仅因地图加载失败被禁用');
  assert.match(editorSource, /Math\.max\([\s\S]*Number\(location\?\.profileVersion\)[\s\S]*Number\(profile\?\.profileVersion\)/,
    '定位保存必须使用 map-detail 与主档案中的最新版本号');
});

test('project route image stays isolated from project effect images and pending files are cleaned', () => {
  assert.match(editorSource, /businessType: 'PROJECT_ROUTE_IMAGE_PENDING'/);
  assert.doesNotMatch(editorSource, /businessType: 'PROJECT_PROFILE_IMAGE_PENDING'/,
    '到访路线图不得混入项目效果图');
  assert.match(editorSource, /accept="image\/jpeg,image\/png,image\/webp"/);
  assert.match(editorSource, /const resetEditor = useCallback\(async \(\) => \{[\s\S]*await cleanupPendingRouteImage\(\)/,
    '撤销或退出主编辑必须清理本次暂存路线图');
  assert.match(editorSource, /useImperativeHandle\(ref, \(\) => \(\{[\s\S]*discardChanges: async[\s\S]*await resetEditor\(\)/,
    '父级取消必须能等待位置暂存清理完成');
  assert.match(editorSource, /const previousPendingId = pendingRouteImageIdRef\.current;[\s\S]*if \(previousPendingId\) await deleteFile\(previousPendingId\)/,
    '替换路线图必须清理上一个本次暂存文件');
  assert.match(editorSource, /routeImageAction/);
  assert.match(helperSource, /routeImageFileId/);
});

test('project information page makes the location description read-only outside the location editor', () => {
  assert.match(pageSource, /\['address', '地标及到访说明'/);
  assert.match(pageSource, /const locationLocked = editing && key === 'address'/);
  assert.match(pageSource, /位置说明需在上方“项目位置与导航点”中修改/);
});

test('project information edit mode controls the location editor through the single top entry', () => {
  assert.doesNotMatch(editorSource, /编辑项目位置/,
    '位置卡不得保留独立编辑入口');
  assert.match(editorSource, /editing = false/,
    '位置编辑状态必须由项目档案页受控');
  assert.doesNotMatch(editorSource, /const \[editing, setEditing\] = useState/,
    '位置编辑器不得维护与主编辑脱节的本地编辑状态');
  assert.match(pageSource, /<ProjectLocationEditor ref=\{locationEditorRef\}[\s\S]*editing=\{editing\}/,
    '顶部项目编辑状态必须同步传给位置编辑器');
  assert.match(pageSource, /await locationEditorRef\.current\?\.discardChanges\?\.\(\)/,
    '取消主编辑前必须等待路线图暂存清理');
  assert.match(pageSource, /if \(locationEditorState\.dirty\)[\s\S]*请先在位置区域保存或撤销/,
    '主档案保存不得静默丢弃未保存的位置修改');
  assert.match(pageSource, /profileVersion: nextVersion \?\? current\.profileVersion/,
    '位置独立保存后必须把最新档案版本合并回主编辑草稿');
  assert.match(pageSource, /setProjectLocation\(\(current\) => current \? \{[\s\S]*profileVersion: normalized\.profileVersion/,
    '主档案保存后必须同步位置编辑器的乐观锁版本');
});
