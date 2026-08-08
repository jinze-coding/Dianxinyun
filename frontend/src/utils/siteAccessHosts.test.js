import assert from 'node:assert/strict';
import test from 'node:test';
import { filterSiteVisitHosts } from './siteAccessHosts.js';

const hosts = [
  { userId: 1, realName: '系统管理员', phone: '19900001000' },
  { userId: 2, realName: '张三', phone: '13812345678' },
  { userId: 3, realName: '李四', phone: '' },
];

test('host search matches name and ignores surrounding spaces', () => {
  assert.deepEqual(filterSiteVisitHosts(hosts, ' 张三 ').map((host) => host.userId), [2]);
});

test('host search supports partial phone matching', () => {
  assert.deepEqual(filterSiteVisitHosts(hosts, '001000').map((host) => host.userId), [1]);
});

test('empty host search keeps all project members', () => {
  assert.deepEqual(filterSiteVisitHosts(hosts, '').map((host) => host.userId), [1, 2, 3]);
});
