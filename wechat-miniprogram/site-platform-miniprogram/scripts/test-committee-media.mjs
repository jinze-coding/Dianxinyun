import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

const source = await readFile(new URL('../src/utils/committeeMedia.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, {compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.CommonJS}}).outputText;
function deferred(){let resolve,reject;const promise=new Promise((a,b)=>{resolve=a;reject=b;});return {promise,resolve,reject};}
const flush = async()=>{for(let i=0;i<10;i++)await Promise.resolve();};
class ApiRequestError extends Error {constructor(message,code,statusCode){super(message);Object.assign(this,{code,statusCode});}}
function setup(origin='http://192.168.1.2:8080') {
  const calls={reads:[],downloads:[],unlinks:[]};let grant;
  const api={read:async(...args)=>{calls.reads.push(args);return grant?grant.promise:`${origin}/api/v1/safety-committee/media/opaque?preview=true`;}};
  globalThis.uni={getFileSystemManager:()=>({unlink:o=>calls.unlinks.push(o.filePath)})};
  const imports={
    '@/api/safetyCommittee':{committeeApi:api},'@/api/request':{ApiRequestError},
    '@/utils/moduleNetwork':{moduleDownload:options=>{const entry={options,aborted:false,progress:undefined};calls.downloads.push(entry);return {abort(){entry.aborted=true;options.fail({errMsg:'aborted'});},onProgressUpdate(fn){entry.progress=fn;}};}},
  };
  const exports={};new Function('require','exports',compiled)(name=>imports[name],exports);
  return {loader:exports.createCommitteeMediaLoader(),calls,setGrant(value){grant=value;}};
}
try {
  for(const thumbnail of [false,true]) {
    const t=setup();const loading=t.loader.load(3,{thumbnail});await flush();
    assert.deepEqual(t.calls.reads,[[3,true,thumbnail]]);
    assert.equal(t.calls.downloads.length,1);
    t.calls.downloads[0].options.success({statusCode:200,tempFilePath:'wxfile://temporary.jpg'});
    assert.equal(await loading,'wxfile://temporary.jpg');
    t.loader.clear();assert.deepEqual(t.calls.unlinks,['wxfile://temporary.jpg']);
  }
  {
    const t=setup('https://example.test');assert.match(await t.loader.load(4,{video:true}),/^https:/);assert.equal(t.calls.downloads.length,0);
    const loading=t.loader.load(4,{video:true,forceDownload:true});await flush();assert.equal(t.calls.downloads.length,1);
    t.calls.downloads[0].options.success({statusCode:200,tempFilePath:'wxfile://video.mp4'});assert.equal(await loading,'wxfile://video.mp4');t.loader.clear();
  }
  {
    const t=setup();let progress;const loading=t.loader.load(4,{video:true,onProgress:p=>progress=p});await flush();
    assert.equal(t.calls.downloads[0].options.timeout,600000);t.calls.downloads[0].progress({progress:42});assert.equal(progress,42);
    t.calls.downloads[0].options.success({statusCode:200,tempFilePath:'wxfile://local-video.mp4'});await loading;t.loader.clear();
  }
  for(const status of [401,403,409,500]) {
    const t=setup();const loading=t.loader.load(5);await flush();t.calls.downloads[0].options.success({statusCode:status,tempFilePath:'wxfile://error-body'});
    await assert.rejects(loading,e=>e.statusCode===status);assert.deepEqual(t.calls.unlinks,['wxfile://error-body']);t.loader.clear();
  }
  {
    const t=setup(),grant=deferred();t.setGrant(grant);const loading=t.loader.load(6);t.loader.clear();grant.resolve('http://example.test/opaque');await assert.rejects(loading,/取消/);assert.equal(t.calls.downloads.length,0);
  }
  {
    const t=setup();const loading=t.loader.load(7);await flush();t.loader.clear();await assert.rejects(loading,/取消/);assert.equal(t.calls.downloads[0].aborted,true);
    t.calls.downloads[0].options.success({statusCode:200,tempFilePath:'wxfile://late.jpg'});assert.deepEqual(t.calls.unlinks,['wxfile://late.jpg']);
  }
  {
    const t=setup();const loading=t.loader.load(8);await flush();t.calls.downloads[0].options.fail({errMsg:'failed http://host/media/private-grant'});
    await assert.rejects(loading,e=>/检查网络/.test(e.message)&&!e.message.includes('private-grant'));t.loader.clear();
  }
  console.log('小程序媒体读取通过：图片/缩略图本地文件、HTTP视频下载、HTTPS Range保留、显式重试、进度、失败状态、取消与迟到文件清理。');
} finally {delete globalThis.uni;}
