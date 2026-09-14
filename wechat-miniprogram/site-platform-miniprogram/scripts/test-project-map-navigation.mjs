import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import {
  isNavigableProjectLocation,
  openProjectMapNavigation, projectMapChoices
} from '../src/utils/projectMapNavigation.ts';

const location = {
  address: '上海市浦东新区测试路1号',
  navigable: true,
  longitude: 121.501,
  latitude: 31.201,
  coordinateType: 'GCJ02'
};

assert.equal(isNavigableProjectLocation(location), true);
assert.equal(isNavigableProjectLocation({ ...location, coordinateType: 'BD09' }), false, '小程序不得猜测非 GCJ02 坐标');
assert.equal(isNavigableProjectLocation({ ...location, navigable: false }), false);
assert.equal(isNavigableProjectLocation({ ...location, latitude: 91 }), false);
assert.equal(isNavigableProjectLocation({ ...location, longitude: Number.NaN }), false);

for (const platform of ['ios', 'iOS', 'android', 'devtools', '', undefined]) {
  const choices = projectMapChoices(platform);
  assert.deepEqual(choices.slice(0, 3).map(c => c.provider), ['tencent', 'amap', 'baidu']);
  assert.equal(choices[3].provider, platform?.toLowerCase() === 'ios' ? 'apple' : 'system');
  assert.equal(choices[3].label, platform?.toLowerCase() === 'ios' ? '苹果地图' : '系统地图');
}
const deps = overrides => ({
  createMapContext: () => ({}), openLocation: () => assert.fail('unexpected system map'),
  confirmSystemMapFallback: () => assert.fail('unexpected fallback'), isActive: () => true, ...overrides
});
const navigate = (provider, overrides, point = location) => openProjectMapNavigation(point, '测试项目', 'map-a', provider, deps(overrides));
for (const provider of ['tencent', 'amap', 'baidu', 'apple']) {
  let options;
  const result = await navigate(provider, { createMapContext: id => {
    assert.equal(id, 'map-a');
    return { openMapApp: o => { options = o; o.success(); o.fail({ errMsg: 'late failure' }); } };
  } });
  assert.equal(result, 'preferred-map');
  assert.equal(options.preferApplication, provider); assert.equal(options.destination, '测试项目');
  assert.equal(options.latitude, location.latitude); assert.equal(options.longitude, location.longitude);
}
let systemCalls = 0, confirmations = 0;
const openSystem = options => {
  systemCalls++;
  assert.equal(options.name, '测试项目'); assert.equal(options.address, location.address);
  assert.equal(options.latitude, location.latitude); assert.equal(options.longitude, location.longitude);
  assert.equal(options.scale, 16); options.success();
};
assert.equal(await navigate('system', { createMapContext: () => assert.fail('system needs no context'), openLocation: openSystem }), 'system-map');
for (const createMapContext of [() => ({}), () => undefined, () => { throw Error('unsupported'); },
  () => ({ openMapApp: o => { o.fail({ errMsg: 'failed' }); o.fail({ errMsg: 'duplicate' }); o.success(); } }),
  () => ({ openMapApp: () => { throw Error('unsupported'); } })]) {
  assert.equal(await navigate('amap', { createMapContext, confirmSystemMapFallback: async () => { confirmations++; return false; } }), 'cancelled');
  assert.equal(await navigate('tencent', { createMapContext, confirmSystemMapFallback: async () => { confirmations++; return true; }, openLocation: openSystem }), 'system-map');
}
assert.equal(confirmations, 10); assert.equal(systemCalls, 6);
for (const cancel of [{ errMsg: 'openMapApp:fail cancel' }, new Error('user cancelled'), { cancel: true }, '取消']) {
  for (const callback of ['success', 'fail']) {
    assert.equal(await navigate('baidu', { createMapContext: () => ({ openMapApp: o => o[callback](cancel) }) }), 'cancelled');
  }
  assert.equal(await navigate('system', { openLocation: o => o.fail(cancel) }), 'cancelled');
}
await assert.rejects(navigate('system', { openLocation: o => o.fail({ errMsg: 'failed' }) }), /选择其他地图/);
assert.equal(await navigate('apple', { isActive: () => false, createMapContext: () => assert.fail('inactive') }), 'cancelled');
let active = true;
assert.equal(await navigate('apple', { isActive: () => active, confirmSystemMapFallback: async () => { active = false; return true; } }), 'cancelled', 'late confirmation must not open a map after leaving/expiry');
await assert.rejects(navigate('system', {}, { ...location, navigable: false }), /暂未配置导航坐标/);
await assert.rejects(navigate('amap', {}, { ...location, latitude: undefined }), /暂未配置导航坐标/);

const visitorSource = await readFile(new URL('../src/pages/public/visitor-invite.vue', import.meta.url), 'utf8');
const componentSource = await readFile(new URL('../src/components/ProjectLocationCard.vue', import.meta.url), 'utf8');
const guardSource = await readFile(new URL('../src/pages/public/guard-visitor-register.vue', import.meta.url), 'utf8');
const apiSource = await readFile(new URL('../src/api/siteAccess.ts', import.meta.url), 'utf8');
assert.equal((visitorSource.match(/<ProjectLocationCard/g) || []).length, 1, '登记前和放行页共用弹窗内唯一地图');
const visitorTemplate = visitorSource.slice(visitorSource.indexOf('<template>'), visitorSource.lastIndexOf('</template>'));
assert.doesNotMatch(visitorTemplate, /会议资料|会议时间|会议地点|共享会议/);
assert.match(visitorSource, /navigationVisible && foreground && navigationVerified && canShowNavigation/, '地图仅在弹窗可见且有效邀请核验成功后挂载');
assert.equal((visitorSource.match(/@tap="openNavigation"/g) || []).length, 2, '登记前和放行回执均提供导航按钮');
assert.doesNotMatch(visitorSource, /invitation\.value\s*!==\s*current|invitation\.value\s*===\s*current/, '路线图迟到响应只能使用请求序号判断，不能比较 Vue 原始对象与响应式代理');
assert.match(visitorSource, /function resetProjectRouteImage\(\)[\s\S]*removePublicProjectRouteImage\(projectRouteImagePath\.value\)/, '替换或离开页面时必须删除临时路线图');
// The actual page lifecycle and route cleanup are exercised by test:visitor-navigation.
assert.doesNotMatch(guardSource, /ProjectLocationCard/, '本次不得改动门卫长期登记页面');
assert.match(componentSource, /<map[^>]*class="project-map-bridge"/);
assert.match(componentSource, /width:1px;height:1px;opacity:0;pointer-events:none/);
assert.doesNotMatch(componentSource, /GCJ-02|百度地图优先导航|project-location-map/);
assert.match(componentSource, /访客导航/);
assert.match(componentSource, /到访路线图/);
assert.match(componentSource, /可能不是实际入口/);
assert.match(componentSource, /uni\.previewImage\([\s\S]*routeImagePath/, '路线图应支持微信大图预览');
assert.doesNotMatch(componentSource, /getLocation\s*\(/, '展示项目位置不得申请访客当前位置');
assert.match(apiSource, /routeImageAvailable\?: boolean/);
assert.match(apiSource, /\/public\/site-access\/project-location\/route-image/);
assert.match(apiSource, /data:\s*\{\s*inviteToken\s*\}/, '邀请令牌只能放在 POST 请求体');
assert.match(apiSource, /visitor-route-\$\{Date\.now\(\)\}-\$\{Math\.random\(\)/, '临时文件名应使用随机名称');
const routeFilePathLine = apiSource.split('\n').find((line) => line.includes('const filePath = `${root}/visitor-route-')) || '';
assert.doesNotMatch(routeFilePathLine, /inviteToken/, '临时文件名不得包含邀请令牌');

console.log('project map navigation and visitor page contract: OK');
