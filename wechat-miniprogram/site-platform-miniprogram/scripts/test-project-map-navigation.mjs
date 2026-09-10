import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import {
  isNavigableProjectLocation,
  openProjectMapNavigation
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

let preferredOptions;
let preferredFallbackCalls = 0;
const preferredResult = await openProjectMapNavigation(location, '测试项目', 'map-a', {
  createMapContext: (mapId) => ({
    openMapApp: (options) => {
      assert.equal(mapId, 'map-a');
      preferredOptions = options;
      options.success?.();
    }
  }),
  openLocation: () => { preferredFallbackCalls += 1; }
});
assert.equal(preferredResult, 'baidu-preferred');
assert.equal(preferredOptions.preferApplication, 'baidu');
assert.equal(preferredOptions.destination, '测试项目');
assert.equal(preferredOptions.latitude, location.latitude);
assert.equal(preferredOptions.longitude, location.longitude);
assert.equal(preferredFallbackCalls, 0, '百度优先调起成功时不得重复打开系统地图');

let fallbackOptions;
const fallbackResult = await openProjectMapNavigation(location, '测试项目', 'map-b', {
  createMapContext: () => ({ openMapApp: (options) => options.fail?.(new Error('not supported')) }),
  openLocation: (options) => {
    fallbackOptions = options;
    options.success?.();
  }
});
assert.equal(fallbackResult, 'system-map');
assert.equal(fallbackOptions.name, '测试项目');
assert.equal(fallbackOptions.address, location.address);
assert.equal(fallbackOptions.scale, 16);

let missingApiFallbackCalls = 0;
assert.equal(await openProjectMapNavigation(location, '测试项目', 'map-c', {
  createMapContext: () => ({}),
  openLocation: (options) => {
    missingApiFallbackCalls += 1;
    options.success?.();
  }
}), 'system-map');
assert.equal(missingApiFallbackCalls, 1, 'openMapApp 不存在时必须降级');

await assert.rejects(
  openProjectMapNavigation({ ...location, navigable: false }, '测试项目', 'map-d', {
    createMapContext: () => { throw new Error('不应创建地图上下文'); },
    openLocation: () => { throw new Error('不应打开地图'); }
  }),
  /暂未配置导航坐标/
);

const visitorSource = await readFile(new URL('../src/pages/public/visitor-invite.vue', import.meta.url), 'utf8');
const componentSource = await readFile(new URL('../src/components/ProjectLocationCard.vue', import.meta.url), 'utf8');
const guardSource = await readFile(new URL('../src/pages/public/guard-visitor-register.vue', import.meta.url), 'utf8');
const apiSource = await readFile(new URL('../src/api/siteAccess.ts', import.meta.url), 'utf8');
assert.equal((visitorSource.match(/<ProjectLocationCard/g) || []).length, 2, '预约登记前和放行页应各挂载一次地图卡片');
assert.match(visitorSource, /invitation\.status === 'PENDING' && !invitationExpired && invitation\.projectLocation/);
assert.match(visitorSource, /!invitationPassExpired && invitation\.projectLocation/);
assert.match(visitorSource, /\['PENDING', 'SUBMITTED'\]\.includes\(invitation\.value\.status\)[\s\S]*startClock\(\)/, '待登记和已提交状态都必须按服务端时间持续判断过期');
assert.match(visitorSource, /watch\(invitationExpired,[\s\S]*resetProjectRouteImage\(\)[\s\S]*closeProjectProfile\(\)/, '页面停留至预约过期时必须清理路线图并关闭项目信息');
assert.equal((visitorSource.match(/:route-image-path="projectRouteImagePath"/g) || []).length, 2, '登记前和放行页应复用同一临时路线图');
assert.match(visitorSource, /invitation\.value = await resolvePublicSiteVisit\(token\.value\);\s*void loadProjectRouteImage\(invitation\.value\)/, '解析邀请后应异步加载路线图');
assert.match(visitorSource, /invitation\.value = data;[\s\S]*?void loadProjectRouteImage\(invitation\.value\)/, '提交成功后必须使用 ref 中的当前响应式对象刷新路线图');
assert.doesNotMatch(visitorSource, /invitation\.value\s*!==\s*current|invitation\.value\s*===\s*current/, '路线图迟到响应只能使用请求序号判断，不能比较 Vue 原始对象与响应式代理');
assert.match(visitorSource, /function resetProjectRouteImage\(\)[\s\S]*removePublicProjectRouteImage\(projectRouteImagePath\.value\)/, '替换或离开页面时必须删除临时路线图');
assert.match(visitorSource, /function cleanup\(\)[\s\S]*resetProjectRouteImage\(\)/, '页面卸载必须执行路线图清理');
assert.doesNotMatch(guardSource, /ProjectLocationCard/, '本次不得改动门卫长期登记页面');
assert.match(componentSource, /<map[\s\S]*@tap="navigateToProject"/);
assert.match(componentSource, /访客导航/);
assert.match(componentSource, /附近地标与到访路线图/);
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
