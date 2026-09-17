import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { parse, compileScript } from '@vue/compiler-sfc';
import { transform } from 'esbuild';
import * as Vue from 'vue';
const {descriptor}=parse(await readFile(new URL('../src/components/CommitteeThumbnail.vue',import.meta.url),'utf8'));
const {code}=await transform(compileScript(descriptor,{id:'thumbnail-test'}).content,{loader:'ts',format:'esm'});
const body=code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g,(_,spec,name)=>`const ${spec.replace(/\bas\b/g,':')} = imports(${JSON.stringify(name)});`).replace(/export\s*\{\s*([\w$]+)\s+as\s+default\s*\};?/,'return $1;').replace('export default','return');
const saved={setTimeout,clearTimeout};const timers=new Map();globalThis.setTimeout=(fn)=>{const id=Symbol();timers.set(id,fn);return id;};globalThis.clearTimeout=id=>timers.delete(id);
const flush=async()=>{for(let i=0;i<20;i++){await Promise.resolve();await Vue.nextTick();}};
function deferred(){let resolve;const promise=new Promise(r=>resolve=r);return {promise,resolve};}
const attachment=(id,kind='OFFICE')=>({id,fileName:'inspection.docx',extension:kind==='VIDEO'?'mp4':kind==='IMAGE'?'jpg':'docx',previewKind:kind,previewStatus:'WAITING',status:'PENDING'});
function setup(props,api){const hooks={},calls={lost:0,open:0};const imports={'@/components/AppNavBar.vue':{},'@/utils/committeeMedia':{createCommitteeMediaLoader:()=>({load:(id,o={})=>api.read(id,true,Boolean(o.thumbnail)),clear(){}})},vue:Vue,'@dcloudio/uni-app':{onShow:fn=>hooks.show=fn,onHide:fn=>hooks.hide=fn},'@/api/safetyCommittee':{committeeApi:api,committeeAccessLost:()=>{calls.lost++;return true;}}};
 const component=new Function('imports',body)(name=>imports[name]);component.render=()=>null;
 const renderer=Vue.createRenderer({createElement:()=>({}),createText:()=>({}),createComment:()=>({}),insert(){},remove(){},setText(){},setElementText(){},patchProp(){},parentNode:()=>null,nextSibling:()=>null});
 const app=renderer.createApp(component,{...props,onOpen:()=>calls.open++});app.mount({});return {vm:app._instance.setupState,props:app._instance.props,hooks,calls,close(){app.unmount();timers.clear();}};
}
try{
 for(const kind of ['IMAGE','VIDEO','PDF','OFFICE','TEXT','HEIF']){
  let args;const t=setup({attachment:attachment(1,kind),large:true},{thumbnail:async()=>({status:'READY'}),read:async(...a)=>{args=a;return '/authorized/thumbnail.jpg';}});
  await flush();assert.deepEqual(args,[1,true,true]);assert.equal(t.vm.image,'/authorized/thumbnail.jpg');t.vm.open();assert.equal(t.calls.open,1);t.hooks.hide();assert.equal(t.vm.image,'');t.hooks.show();await flush();assert.equal(t.vm.image,'/authorized/thumbnail.jpg');t.close();
 }
 {
  let state='QUEUED';const t=setup({attachment:attachment(2)},{thumbnail:async()=>({status:state}),read:async()=>'/ready.jpg'});await flush();assert.equal(timers.size,1);state='READY';const fn=[...timers.values()][0];timers.clear();fn();await flush();assert.equal(t.vm.image,'/ready.jpg');assert.equal(timers.size,0);t.close();
 }
 {
  let first=true,retry;const t=setup({attachment:attachment(3)},{thumbnail:async(_id,r)=>{retry=r;return {status:first?'FAILED':'READY'};},read:async()=>'/retry.jpg'});await flush();assert.equal(t.vm.failed,true);first=false;t.vm.open();await flush();assert.equal(retry,true);assert.equal(t.vm.image,'/retry.jpg');assert.equal(t.calls.open,0);t.close();
 }
 {
  const late=deferred();const t=setup({attachment:attachment(4)},{thumbnail:async()=>({status:'READY'}),read:id=>id===4?late.promise:Promise.resolve('/new.jpg')});await flush();t.props.attachment=attachment(5);await flush();late.resolve('/old.jpg');await flush();assert.equal(t.vm.image,'/new.jpg');t.close();
 }
 {
  const late=deferred();const t=setup({attachment:attachment(6)},{thumbnail:()=>late.promise,read:async()=>'/stale.jpg'});t.hooks.hide();late.resolve({status:'READY'});await flush();assert.equal(t.vm.image,'');assert.equal(timers.size,0);t.close();
 }
 {
  const t=setup({localFile:{name:'local.jpg',path:'wxfile://local.jpg'}},{thumbnail:()=>{throw Error('not uploaded');}});await flush();assert.equal(t.vm.image,'wxfile://local.jpg');t.hooks.hide();assert.equal(t.vm.image,'');t.close();
 }
 {
  const captured={name:'现场.mp4',path:'wxfile://capture.mp4',thumbnailPath:'wxfile://capture-cover.jpg'};
  const t=setup({localFile:captured},{thumbnail:async()=>{throw Error('offline');}});await flush();assert.equal(t.vm.image,captured.thumbnailPath);assert.equal(t.vm.video,true);t.vm.open();assert.equal(t.calls.open,1);
  t.props.attachment=attachment(8,'VIDEO');await flush();assert.equal(t.vm.image,captured.thumbnailPath,'failed server request must retain local cover');
  t.hooks.hide();t.hooks.show();await flush();assert.equal(t.vm.image,captured.thumbnailPath);t.close();
 }
 {
  const t=setup({localFile:{name:'现场.jpg',path:'wxfile://photo.jpg'},attachment:attachment(9,'IMAGE')},{thumbnail:async()=>({status:'READY'}),read:async()=>'/server.jpg'});await flush();
  assert.equal(t.vm.image,'wxfile://photo.jpg');t.vm.imageError();assert.equal(t.vm.image,'/server.jpg','local error can fall back to authorized thumbnail');t.close();
 }
 console.log('附件缩略图专项通过：六种预览类型、暂存附件、本地照片/视频封面、上传前打开、断网保持、生成轮询、失败重试、隐藏恢复、切换附件与迟到响应。');
}finally{globalThis.setTimeout=saved.setTimeout;globalThis.clearTimeout=saved.clearTimeout;}
