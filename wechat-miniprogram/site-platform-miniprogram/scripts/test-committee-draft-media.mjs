import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {parse,compileScript} from '@vue/compiler-sfc';
import {transform} from 'esbuild';
import * as Vue from 'vue';
async function compile(path,sfc=true){
 const source=(await readFile(new URL(path,import.meta.url),'utf8')).replace(/\s*\/\/ #ifdef H5[\s\S]*?\/\/ #endif/g,'');
 const {code}=await transform(sfc?compileScript(parse(source).descriptor,{id:'draft-media-test'}).content:source,{loader:'ts',format:'esm'});
 return code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g,(_,spec,name)=>`const ${spec.replace(/\bas\b/g,':')} = imports(${JSON.stringify(name)});`).replace(/export\s*\{\s*([\w$]+)\s+as\s+default\s*\};?/,'return $1;').replace('export default','return');
}
const helper=(await compile('../src/utils/committeeLocalMedia.ts',false)).replace(/export\s*\{[\s\S]*?\};?/,'return {cleanupCommitteeCapture,committeeLocalMediaKind};');
const localMedia=new Function('imports',helper)(()=>({}));
const edit=await compile('../src/pages/safety-committee/edit.vue'),preview=await compile('../src/components/CommitteeLocalPreview.vue');
const flush=async()=>{for(let i=0;i<20;i++){await Promise.resolve();await Vue.nextTick();}};
function deferred(){let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b;});return {promise,resolve,reject};}
let key=0;
function mount(body,props,api,uniApi){
 globalThis.uni=uniApi;const hooks={};
 const imports={vue:Vue,'@dcloudio/uni-app':Object.fromEntries(['onLoad','onShow','onHide','onUnload'].map(n=>[n,f=>hooks[n]=f])),
  '@/utils/committeeLocalMedia':localMedia,'@/utils/navLayout':{usePageScrollHeight:()=>({scrollStyle:{}}),getNavLayoutMetrics:()=>({statusBarHeight:20,navHeight:44})},
  '@/stores/auth':{useAuthStore:()=>({state:{user:{id:9}},ensureRootAccess:async()=>true})},
  '@/api/safetyCommittee':{committeeKey:()=>String(++key),committeeAccessLost:()=>false,committeeApi:api,...api}};
 const component=new Function('imports',body)(n=>imports[n]||{});component.render=()=>null;
 const renderer=Vue.createRenderer({createElement:()=>({}),createText:()=>({}),createComment:()=>({}),insert(){},remove(){},setText(){},setElementText(){},patchProp(){},parentNode:()=>null,nextSibling:()=>null});
 const app=renderer.createApp(component,props);app.mount({});return {vm:app._instance.setupState,hooks,close(){hooks.onUnload?.();app.unmount();}};
}
function setup(){
 const calls={navigations:[],deleted:[],uploads:[],discarded:[]};
 const uniApi={navigateTo:o=>calls.navigations.push(o),getFileSystemManager:()=>({unlink:o=>calls.deleted.push(o.filePath)})};
 const api={categories:async()=>['施工安全管理'],discard:async id=>calls.discarded.push(id),uploadCommitteeFile:(_file,_meta,_user,progress,control)=>{const task=deferred();calls.uploads.push(task);control.abort=()=>task.reject(Error('cancelled'));progress('上传 12%');return task.promise;}};
 const t=mount(edit,{},api,uniApi);t.hooks.onLoad({projectId:'3'});return {...t,calls,api,uniApi};
}
const file=kind=>({path:`wxfile://capture.${kind==='video'?'mp4':'jpg'}`,name:`现场.${kind==='video'?'mp4':'jpg'}`,thumbnailPath:kind==='video'?'wxfile://cover.jpg':'wxfile://capture.jpg',size:100,capturedTemporary:true});
for(const kind of ['image','video']){
 const t=setup();await flush();t.vm.conclusion='保留填写内容';t.vm.capture();t.calls.navigations[0].events.captured(file(kind));await flush();const item=t.vm.files[0];
 assert.equal(item.state,'uploading');assert.equal(item.attachment,undefined);assert.ok(item.file.thumbnailPath);t.vm.openAttachment(item);assert.equal(t.vm.preview.key,item.key);
 let closed=0;const local=mount(preview,{file:item.file,onClose:()=>closed++},t.api,t.uniApi);assert.equal(local.vm.kind,kind);assert.equal(local.vm.source,file(kind).path);local.hooks.onHide();assert.equal(closed,1);local.close();assert.deepEqual(t.calls.deleted,[],'closing preview keeps draft media');
 t.calls.uploads[0].reject(Error('网络中断'));await flush();assert.equal(item.state,'failed');assert.equal(item.file.thumbnailPath,file(kind).thumbnailPath);assert.equal(t.vm.conclusion,'保留填写内容');
 const retry=t.vm.upload(item);await flush();t.calls.uploads[1].resolve({id:12,status:'PENDING',previewKind:kind.toUpperCase()});await retry;
 assert.equal(item.state,'done');assert.equal(item.attachment.id,12);assert.equal(item.file.path,file(kind).path);t.vm.openAttachment(item);assert.equal(t.vm.preview.attachment.id,12);
 await t.vm.remove(item);assert.equal(t.vm.files.length,0);assert.equal(t.vm.preview,undefined);assert.deepEqual(t.calls.discarded,[12]);assert.deepEqual(new Set(t.calls.deleted),new Set([file(kind).path,file(kind).thumbnailPath]));t.close();await flush();
}
{
 const t=setup();t.vm.capture();t.calls.navigations[0].events.captured(file('video'));await flush();await t.vm.remove(t.vm.files[0]);assert.equal(t.vm.files.length,0);assert.deepEqual(new Set(t.calls.deleted),new Set(['wxfile://capture.mp4','wxfile://cover.jpg']));t.close();
}
{
 const t=setup();t.vm.capture();t.calls.navigations[0].events.captured(file('video'));await flush();t.close();await flush();assert.deepEqual(new Set(t.calls.deleted),new Set(['wxfile://capture.mp4','wxfile://cover.jpg']));
}
{
 const t=setup();t.vm.capture();t.calls.navigations[0].events.captured({...file('image'),capturedTemporary:false});await flush();await t.vm.remove(t.vm.files[0]);assert.deepEqual(t.calls.deleted,[],'do not delete album originals');t.close();
}
{
 const t=setup();t.vm.files=Array.from({length:30},(_,i)=>({key:String(i),state:'done'}));t.vm.capture();t.calls.navigations[0].events.captured(file('video'));await flush();assert.equal(t.vm.files.length,30);assert.equal(t.calls.uploads.length,0);assert.deepEqual(new Set(t.calls.deleted),new Set(['wxfile://capture.mp4','wxfile://cover.jpg']));t.close();
}
delete globalThis.uni;
console.log('拍摄回填表单通过：照片/视频上传前本地预览、上传失败保留、续传衔接、移除/退出/超额清理、相册原件保护。');
