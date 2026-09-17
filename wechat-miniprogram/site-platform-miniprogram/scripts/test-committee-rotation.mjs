import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {parse,compileScript} from '@vue/compiler-sfc';
import {transform} from 'esbuild';
import * as Vue from 'vue';
const raw=(await readFile(new URL('../src/components/CommitteePreview.vue',import.meta.url),'utf8')).replace(/\/\/ #ifdef H5[\s\S]*?\/\/ #endif/g,'');
const {descriptor}=parse(raw);const {code}=await transform(compileScript(descriptor,{id:'rotation-test'}).content,{loader:'ts',format:'esm'});
const body=code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g,(_,spec,name)=>`const ${spec.replace(/\bas\b/g,':')} = imports(${JSON.stringify(name)});`).replace(/export\s*\{\s*([\w$]+)\s+as\s+default\s*\};?/,'return $1;').replace('export default','return');
const flush=async()=>{for(let i=0;i<20;i++){await Promise.resolve();await Vue.nextTick();}};
function deferred(){let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b;});return{promise,resolve,reject};}
const initial=()=>({id:1,fileName:'photo.png',extension:'png',previewKind:'IMAGE',status:'ACTIVE',previewStatus:'READY',rotationDegrees:0,rotationVersion:1,canRotate:true});
globalThis.uni={getFileSystemManager:()=>({unlink(){}})};
function setup(api,attachment=initial()){
 const hooks={},updates=[];let closed=0;
 const imports={'@/components/AppNavBar.vue':{},'@/utils/committeeMedia':{createCommitteeMediaLoader:()=>({load:(id,o={})=>api.read(id,true,Boolean(o.thumbnail)),clear(){}})},vue:Vue,'@dcloudio/uni-app':{onShow:fn=>hooks.show=fn,onHide:fn=>hooks.hide=fn},'@/utils/moduleNetwork':{moduleDownload(){}},'@/utils/navLayout':{getNavLayoutMetrics:()=>({statusBarHeight:20,navHeight:44})},'@/api/safetyCommittee':{committeeApi:api,committeeAccessLost:e=>e.status===403}};
 const component=new Function('imports',body)(name=>imports[name]);component.render=()=>null;
 const renderer=Vue.createRenderer({createElement:()=>({}),createText:()=>({}),createComment:()=>({}),insert(){},remove(){},setText(){},setElementText(){},patchProp(){},parentNode:()=>null,nextSibling:()=>null});
 const app=renderer.createApp(component,{attachment,onUpdated:a=>updates.push(a),onClose:()=>closed++});app.mount({});
 return {vm:app._instance.setupState,hooks,updates,get closed(){return closed;},close(){app.unmount();}};
}
function apiFixture(){let saved=initial();const commands=[];const api={attachment:async()=>({...saved}),read:async()=>`/media/version-${saved.rotationVersion}`,thumbnail:async()=>({status:'READY'}),rotate:async(id,rotationDegrees,expectedVersion)=>{commands.push({id,rotationDegrees,expectedVersion});assert.equal(expectedVersion,saved.rotationVersion);saved={...saved,rotationDegrees,rotationVersion:expectedVersion+1};return {...saved};}};return{api,commands,get saved(){return saved;}};}
try{
 const f=apiFixture();let t=setup(f.api);await flush();assert.equal(t.vm.url,'/media/version-1');
 await t.vm.rotate(-90);await flush();assert.equal(t.vm.file.rotationDegrees,270);assert.equal(t.vm.url,'/media/version-2');
 await t.vm.rotate(90);await flush();assert.equal(t.vm.file.rotationDegrees,0);assert.equal(t.vm.file.rotationVersion,3);
 await t.vm.rotate(90);await flush();assert.equal(t.vm.file.rotationDegrees,90);t.close();
 t=setup(f.api);await flush();assert.equal(t.vm.file.rotationDegrees,90);assert.equal(t.vm.url,'/media/version-4');t.close();
 {
  const f=apiFixture(),pending=deferred();f.api.rotate=(...args)=>{f.commands.push(args);return pending.promise;};const t=setup(f.api);await flush();
  const saving=t.vm.rotate(90);await t.vm.rotate(90);assert.equal(f.commands.length,1);pending.resolve({...initial(),rotationDegrees:90,rotationVersion:2});await saving;await flush();assert.equal(t.vm.file.rotationDegrees,90);t.close();
 }
 {
  const f=apiFixture(),late=deferred();const t=setup(f.api);await flush();f.api.attachment=()=>late.promise;const stale=t.vm.load();
  await t.vm.rotate(90);late.resolve(initial());await stale;await flush();assert.equal(t.vm.file.rotationDegrees,90);assert.equal(t.vm.file.rotationVersion,2);t.close();
 }
 {
  const f=apiFixture();f.api.rotate=async()=>{throw Error('附件角度已被修改，请重新核对后再旋转');};const t=setup(f.api);await flush();await t.vm.rotate(90);await flush();assert.equal(t.vm.file.rotationDegrees,0);assert.match(t.vm.message,/重新核对/);t.close();
 }
 {
  const f=apiFixture();f.api.attachment=async()=>({...initial(),canRotate:false});const t=setup(f.api);await flush();await t.vm.rotate(90);assert.equal(f.commands.length,0);t.close();
 }
 {
  const f=apiFixture(),late=deferred();f.api.rotate=()=>late.promise;const t=setup(f.api);await flush();const pending=t.vm.rotate(90);const count=t.updates.length;t.close();late.resolve({...initial(),rotationDegrees:90,rotationVersion:2});await pending;await flush();assert.equal(t.updates.length,count);
 }
 {
  const f=apiFixture();const t=setup(f.api);await flush();t.vm.mediaFailed();assert.match(t.vm.mediaError,/图片加载失败/);
  await t.vm.load();assert.match(t.vm.mediaError,/图片加载失败/,'polling must retain the retry state');
  t.vm.retryMedia();await flush();assert.equal(t.vm.mediaError,'');assert.equal(t.vm.url,'/media/version-1');t.close();
 }
 {
  const f=apiFixture();f.api.read=async()=>{throw Error('附件下载失败，请检查网络后重试');};const t=setup(f.api);await flush();
  assert.equal(t.vm.url,'');assert.equal(t.vm.mediaLoading,false);assert.match(t.vm.mediaError,/检查网络/);t.close();
 }
 console.log('旋转页面通过：左右90度、四向循环、保存后重开、防重复、旧响应丢弃、冲突保留、只读与卸载保护。');
}finally{delete globalThis.uni;}
