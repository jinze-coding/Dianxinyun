import test from 'node:test';
import assert from 'node:assert/strict';
import { mergeCommitteePage, validateCommitteeDateRange } from './model.js';
import { buildRoleMenuTree, toggleMenuNode, buildPermissionActions, toggleActionKey } from '../../utils/roleAuthorization.js';
import { canAccessPage } from '../../utils/permissions.js';
import { PAGE_IDS } from '../../constants/dicts.js';

test('inspection dates are optional as a pair, include a single day and have no arbitrary span cap', () => {
  for (const [start,end] of [['',''],['2024-02-29','2024-02-29'],['2024-02-29','2026-09-14'],['1000-01-01','9999-12-31']]) assert.equal(validateCommitteeDateRange(start,end),'');
  for (const [start,end] of [['2026-09-01',''],['','2026-09-14'],['2026-09-14','2026-09-01'],['2026-02-29','2026-03-01'],['2026-13-01','2027-01-01'],['invalid','2026-09-14']]) assert.ok(validateCommitteeDateRange(start,end));
});

test('new submissions do not shift an older page; returning to page one releases the snapshot', () => {
  const current={records:[{id:20}],total:40}; const incoming={records:[{id:21}],total:41,latestId:41};
  const older=mergeCommitteePage(current,incoming,2,40);assert.equal(older.data,current);assert.equal(older.hasNew,true);assert.equal(older.latestId,40);
  assert.equal(mergeCommitteePage(current,incoming,1,40).data,incoming);
  assert.equal(mergeCommitteePage(current,incoming,2,null).hasNew,false);
});
test('committee menu assignment selects both clients and its record page without quality grants', () => {
  const menus=[['WEB_SAFETY_COMMITTEE',1,null],['MINI_SAFETY_COMMITTEE',2,null],['SAFETY_COMMITTEE_RECORDS',3,1]].map(([menuCode,id,parentId])=>({menuCode,id,parentId,enabled:1,visible:1}));
  const tree=buildRoleMenuTree(menus,{scopeType:'PROJECT'});const node=tree.find(n=>n.moduleCode==='SAFETY_COMMITTEE');assert.ok(node);
  const selected=toggleMenuNode({node,checked:true});assert.deepEqual([...selected.menuIds].sort(),[1,2,3]);assert.ok(selected.businessModuleCodes.includes('SAFETY_COMMITTEE'));assert.equal(selected.businessModuleCodes.includes('QUALITY'),false);
});
test('view is required independently for submission and own edits', () => {
  const permissions=['view','submit','edit_own'].map((a,i)=>({id:i+1,permissionCode:`safety_committee.${a}`,enabled:1}));
  const actions=buildPermissionActions(permissions);let selected=toggleActionKey(new Set(),'safety_committee.edit_own',true,actions);
  assert.deepEqual(selected,new Set(['safety_committee.edit_own','safety_committee.view']));assert.equal(selected.has('safety_committee.submit'),false);
  selected=toggleActionKey(selected,'safety_committee.view',false,actions);assert.equal(selected.size,0);
  const user={roles:[],projectContexts:[{projectId:1,accessStatus:'ACTIVE',menuCodes:['WEB_QUALITY']}]};assert.equal(canAccessPage(user,PAGE_IDS.SAFETY_COMMITTEE,1),false);
});
