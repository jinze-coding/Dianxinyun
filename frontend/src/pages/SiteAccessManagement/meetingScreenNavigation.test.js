import test from 'node:test';
import assert from 'node:assert/strict';
import { saveMeetingScreenReturn, readMeetingScreenReturn } from './meetingScreenNavigation.js';
import { meetingScreenUrl } from './meetingScreenModel.js';

const memoryStorage = () => {
  const values = new Map();
  return {
    get length() { return values.size; },
    key: (index) => [...values.keys()][index],
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
  };
};
const context = {
  userId: 1, projectId: 3, invitationId: 19,
  filters: { periodMode: 'CUSTOM', anchorDate: '2026-09-10', customStart: '2026-09-01', customEnd: '2026-09-16',
    inviteType: 'MEETING', status: 'OPEN', keyword: '质量安全', keywordInput: '质量安全 ', pageNo: 3, scrollTop: 210 },
};
const search = (key = 'first') => new URL(meetingScreenUrl(19, key), 'http://localhost:3002').search;

test('screen return restores the originating project, filters, input, page and scroll position', () => {
  const storage = memoryStorage();
  assert.equal(saveMeetingScreenReturn(storage, 'first', context, 1000), 'first');
  assert.deepEqual(readMeetingScreenReturn(storage, search(), 1, 3, 2000), { projectId: 3, filters: context.filters });
  assert.equal(search().includes('质量'), false);
  assert.equal(search().includes('keyword'), false);
  assert.equal(readMeetingScreenReturn(storage, '?meetingScreen=19', 1, 3, 2000), null);
});

test('a screen opened from meeting details returns to the same meeting tab and keeps the list context', () => {
  const storage = memoryStorage();
  saveMeetingScreenReturn(storage, 'first', { ...context, filters: { ...context.filters, detailId: 19, detailTab: 'materials' } }, 1000);
  assert.deepEqual(readMeetingScreenReturn(storage, search(), 1, 3, 2000).filters,
    { ...context.filters, detailId: 19, detailTab: 'materials' });
});

test('separate screen windows retain their own originating list instead of overwriting one another', () => {
  const storage = memoryStorage();
  saveMeetingScreenReturn(storage, 'first', context, 1000);
  saveMeetingScreenReturn(storage, 'second', { ...context, filters: { ...context.filters, pageNo: 1, keyword: '材料' } }, 1000);
  assert.equal(readMeetingScreenReturn(storage, search('first'), 1, 3, 2000).filters.pageNo, 3);
  assert.equal(readMeetingScreenReturn(storage, search('second'), 1, 3, 2000).filters.keyword, '材料');
});

test('another user, meeting or project cannot reuse a return context; expired and malformed state is ignored', () => {
  const storage = memoryStorage();
  saveMeetingScreenReturn(storage, 'first', context, 1000);
  assert.equal(readMeetingScreenReturn(storage, search(), 2, 3, 2000), null);
  assert.equal(readMeetingScreenReturn(storage, search(), 1, 4, 2000), null);
  assert.equal(readMeetingScreenReturn(storage, search().replace('meetingScreen=19', 'meetingScreen=20'), 1, 3, 2000), null);
  assert.equal(readMeetingScreenReturn(storage, search(), 1, 3, 1000 + 86400001), null);
  assert.equal(readMeetingScreenReturn(storage, search(), 1, 3, 999), null);
  storage.setItem(storage.key(0), '{broken');
  assert.equal(readMeetingScreenReturn(storage, search(), 1, 3, 2000), null);
});

test('blocked storage does not prevent opening a screen and arbitrary destinations are not persisted', () => {
  const blocked = { get length() { throw new Error('storage blocked'); }, getItem() { throw new Error('storage blocked'); } };
  assert.equal(saveMeetingScreenReturn(blocked, 'first', context, 1000), '');
  assert.equal(readMeetingScreenReturn(blocked, search(), 1, 3, 2000), null);
  const storage = memoryStorage();
  saveMeetingScreenReturn(storage, 'first', { ...context, returnUrl: 'https://example.org', filters: { ...context.filters, token: 'private' } }, 1000);
  assert.equal(storage.getItem(storage.key(0)).includes('example.org'), false);
  assert.equal(storage.getItem(storage.key(0)).includes('private'), false);
});
