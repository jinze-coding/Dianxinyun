// Run the actual four page setups with controlled identity/session I/O.
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { parse, compileScript } from '@vue/compiler-sfc';
import { transform } from 'esbuild';
import * as Vue from 'vue';
import { useVisitorPersonalInfo } from '../src/utils/visitorPersonalInfo.ts';
import { createMeetingInviteRefreshCoordinator } from '../src/utils/meetingInviteRefresh.ts';
const flush = async () => { for(let i=0;i<25;i++){await Promise.resolve();await Vue.nextTick();} };
const renderer=Vue.createRenderer({createElement:()=>({}),createText:()=>({}),createComment:()=>({}),insert(){},remove(){},setText(){},setElementText(){},patchProp(){},parentNode:()=>null,nextSibling:()=>null});
const set=globalThis.setInterval,clear=globalThis.clearInterval;
globalThis.setInterval=()=>1; globalThis.clearInterval=()=>{};
globalThis.uni={showToast(){},getLocation({fail}){fail({errMsg:'synthetic denied'});}};
const saved={available:true,rememberInfo:false,visitorCompany:'历史单位',contactName:'历史姓名',contactPhone:'13900000000',travelMode:'DRIVING',vehiclePlate:'沪ATEST1'};
const invitation={inviteType:'MEETING',status:'OPEN',visitStartTime:'2099-01-01T00:00:00',visitEndTime:'2099-01-02T00:00:00'};
try {
for(const [name,retry,submit] of [['visitor-invite','loadVisitorIdentity','submit'],['meeting-invite','initialize','submit'],['guard-visitor-register','initialize','submit'],['meeting-check-in','initialize','submitWalkIn']]) {
 const hooks={};let info={available:false};let failIdentity=false;let payload;let mode='FORM';let personReads=0;let responseGate;let issuedToken='synthetic-session';
 const session=async()=>{if(responseGate)await responseGate;if(failIdentity)throw new Error('身份获取失败');return {visitorSessionToken:issuedToken,personalInfo:info,pageState:name==='meeting-check-in'?(mode==='FORM'?'WALK_IN_FORM':'RESERVED'):mode,invitation,meeting:invitation,projectName:'测试项目',matchedPasses:[],attendees:mode==='RESERVED'?[{personId:1,personName:'原预约本人',attendanceStatus:'PENDING'}]:[]};};
 const api=new Proxy({createPublicVisitorSession:session,createPublicMeetingVisitorSession:session,createPublicGuardVisitorSession:session,createPublicMeetingCheckinSession:session,
 resolvePublicSiteVisit:async()=>name==='visitor-invite'?{...invitation,inviteType:'SINGLE',status:'PENDING'}:invitation,
 getPublicGuardMeetings:async()=>[],refreshPublicGuardState:session,
 submitPublicSiteVisit:async p=>{payload=p;throw new Error('合成提交失败');},submitPublicMeetingVisit:async p=>{payload=p;throw new Error('合成提交失败');},submitPublicGuardVisit:async p=>{payload=p;throw new Error('合成提交失败');},submitPublicMeetingCheckinWalkIn:async p=>{payload=p;throw new Error('合成提交失败');}},
 {get(target,key){if(key in target)return target[key];if(/VisitorProfiles?|CheckinProfiles?/.test(key))return()=>{personReads++;throw new Error('should not call named profiles');};return()=>{};}});
 const imports={vue:Vue,'@dcloudio/uni-app':Object.fromEntries(['onLoad','onShow','onHide','onUnload','onBackPress','onPageScroll'].map(key=>[key,fn=>{hooks[key]=fn;}])),'@/api/siteAccess':api,
 '@/utils/visitorPersonalInfo':{useVisitorPersonalInfo},'@/utils/meetingInviteRefresh':{createMeetingInviteRefreshCoordinator},'@/utils/wechat':{getFreshWechatCode:async()=> 'synthetic-code'},'@/utils/navigation':{showToast(){}},'@/utils/visitorProfileFlow':{isVisitorSessionAuthorizationError:()=>false},'@/api/request':{ApiRequestError:Error}};
 for(const [mod,fn] of [['visitorInviteScene','extractVisitorInviteToken'],['meetingInviteScene','extractMeetingInviteToken'],['guardVisitorScene','extractGuardVisitorToken'],['meetingCheckinScene','extractMeetingCheckinToken']])imports[`@/utils/${mod}`]={[fn]:()=> 'synthetic-scene'};
 const source=await readFile(new URL(`../src/pages/public/${name}.vue`,import.meta.url),'utf8');
 const template=source.split('<template>')[1].split('<style')[0];
 assert.doesNotMatch(template,/常用资料|常用来访资料|记住本人|rememberChange|profileSaveChange/);
 const {descriptor}=parse(source.replace(/\/\/ #ifndef MP-WEIXIN[\s\S]*?\/\/ #endif/g,''));
 const {code}=await transform(compileScript(descriptor,{id:name}).content,{loader:'ts',format:'esm'});
 const body=code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g,(_,spec,path)=>`const ${spec.replace(/\bas\b/g,':')} = imports(${JSON.stringify(path)});`).replace(/export\s*\{\s*([\w$]+)\s+as\s+default\s*\};?/,'return $1;').replace('export default','return');
 const component=new Function('imports',body)(path=>imports[path]||{});component.render=()=>null;const app=renderer.createApp(component);app.mount({});const vm=app._instance.setupState;
 await hooks.onLoad({});await flush();assert.equal(vm.contactName,'',name+' new visitor starts empty');
 info=saved;await vm[retry](true);await flush();assert.equal(vm.contactName,'历史姓名',name+' loads history despite old remember=false');
 vm.visitorCompany='';vm.contactName='手工修改';vm.contactPhone='13800000000';vm.travelMode='OTHER';vm.vehiclePlate='本次车牌';vm.companions=[{personName:'本次同行',personCompany:'',personPhone:''}];
 failIdentity=true;await vm[retry](true);await flush();assert.equal(vm.contactName,'手工修改');
 failIdentity=false;await vm[retry](true);await flush();assert.equal(vm.visitorCompany,'');assert.equal(vm.contactName,'手工修改');assert.equal(vm.contactPhone,'13800000000');assert.equal(vm.travelMode,'OTHER');assert.equal(vm.vehiclePlate,'本次车牌');assert.equal(vm.companions[0].personName,'本次同行');
 vm.visitorCompany='本次单位';vm.privacyAgreed=true;await vm[submit]();await flush();assert.ok(payload,name+' submitted form');assert.ok(!Object.hasOwn(payload,'rememberInfo'));assert.ok(!Object.hasOwn(payload,'profileAction'));assert.equal(vm.contactName,'手工修改',name+' failed submit retains form');
 if(name==='meeting-check-in'){mode='RESERVED';await vm.initialize();assert.equal(vm.attendees[0].personName,'原预约本人');assert.equal(vm.visitorCompany,'本次单位');}
 assert.equal(personReads,0,name+' no named profile APIs');
 let release;responseGate=new Promise(resolve=>{release=resolve;});issuedToken='late-session';const pending=vm[retry](true);await flush();hooks.onUnload();release();await pending;await flush();assert.notEqual(vm.visitorSessionToken,'late-session',name+' disposed page ignores late identity');app.unmount();console.log(name+': first entry, autofill, identity retry, edits/companions and failed submission PASS');
}
} finally {globalThis.setInterval=set;globalThis.clearInterval=clear;}
