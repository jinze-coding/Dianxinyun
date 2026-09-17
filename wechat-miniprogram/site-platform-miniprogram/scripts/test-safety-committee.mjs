import assert from 'node:assert/strict';
import { readFile,writeFile,mkdtemp,rm } from 'node:fs/promises';
import { fileURLToPath,pathToFileURL } from 'node:url';
import path from 'node:path';
import ts from 'typescript';
const root=fileURLToPath(new URL('..',import.meta.url));const temporary=await mkdtemp(path.join(root,'.committee-test-'));
try{
 const pages=JSON.parse(await readFile(path.join(root,'src/pages.json'),'utf8'));
 assert.equal(pages.pages[0].path,'pages/login/index');assert.equal(pages.tabBar.list.length,5);assert.ok(pages.pages.some(p=>p.path==='pages/safety-committee/index'));
 let raw=await readFile(path.join(root,'src/api/safetyCommittee.ts'),'utf8');
 raw=raw.replace(/import \{ moduleRequest \} from '@\/utils\/moduleNetwork';/, 'const moduleRequest = (o) => uni.request(o);');
 raw=raw.replace(/\/\/ #ifdef H5[\s\S]*?\/\/ #endif/g,'').replace(/import \{ API_BASE_URL,[^\n]+from '\.\/request';/,"const { API_BASE_URL, request, getToken, ApiRequestError, handleUnauthorized } = globalThis.committeeRequest;");
 const code=ts.transpileModule(raw,{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ESNext}}).outputText;
 await writeFile(path.join(temporary,'api.mjs'),code);
 let reads=[];let sent=0;let progress=[];let storageKeys=[];let statusReads=0;const queries=[];
 const completed={id:7,status:'PENDING',fileName:'test.mp4'};
 globalThis.committeeRequest={API_BASE_URL:'http://127.0.0.1:8080/api/v1',getToken:()=> 'test-session',ApiRequestError:Error,handleUnauthorized:()=>assert.fail('unexpected logout'),request:async(url,options)=>{
   if(url.startsWith('/safety-committee/records?')){queries.push(new URL(url,'http://localhost').searchParams);return {records:[],total:0,latestId:0};}
   if(url.includes('read-session'))return {contentPath:'/api/v1/safety-committee/media/opaque-code'};
   if(url.endsWith('/complete?projectId=3'))return completed;
   if(url.includes('/uploads/resumable')){statusReads++;return {sessionId:'resumable',chunkSize:8*1024**2,uploadedChunks:Array.from({length:31},(_,i)=>i),expiresAt:Date.now()+3600000,completed:false};}
   assert.fail(`Unexpected request: ${url}`);
 }};
 globalThis.uni={getStorageSync:key=>{storageKeys.push(key);return {sessionId:'resumable',expiresAt:Date.now()+3600000};},setStorageSync:()=>{},removeStorageSync:()=>{},
   getFileSystemManager:()=>({readFile:o=>{reads.push([o.position,o.length]);o.success({data:new Uint8Array(o.length).buffer});}}),
   request:o=>{assert.equal(o.method,'PUT');assert.ok(o.data.byteLength<=8*1024**2+1024);assert.equal(o.header.Authorization,'Bearer test-session');sent++;o.success({statusCode:200,data:{code:200}});return {abort:()=>{}};}};
 const api=await import(pathToFileURL(path.join(temporary,'api.mjs')));
 const mediaCalls=[];let nativeError;
 globalThis.wx={chooseMedia:o=>{
   mediaCalls.push(o);
   if(nativeError){o.fail(nativeError);return;}
   if(o.maxDuration!==undefined && (o.maxDuration<3 || o.maxDuration>60)){o.fail({errMsg:'chooseMedia:fail error maxDuration'});return;}
   o.success({tempFiles:[{tempFilePath:'wxfile://long-video.mp4',fileType:'video',duration:300,size:1024}]});
 },chooseMessageFile:o=>nativeError?o.fail(nativeError):o.success({tempFiles:[{name:'文档.pdf',path:'wxfile://document.pdf',size:100}]})};
 const album=await api.chooseCommitteeFiles('album');assert.deepEqual(mediaCalls[0].sourceType,['album']);assert.equal(mediaCalls[0].maxDuration,undefined);assert.equal(album[0].path,'wxfile://long-video.mp4');
 await api.chooseCommitteeFiles('camera');assert.deepEqual(mediaCalls[1].sourceType,['camera']);assert.equal(mediaCalls[1].maxDuration,60);
 const files=await api.chooseCommitteeFiles('chat');assert.equal(files[0].name,'文档.pdf');
 nativeError={errMsg:'chooseMedia:fail cancel'};await assert.rejects(api.chooseCommitteeFiles('album'),e=>e===nativeError);
 nativeError={errno:112,errMsg:'chooseMedia:fail api scope is not declared in the privacy agreement'};
 for(const source of ['album','chat'])await assert.rejects(api.chooseCommitteeFiles(source),/隐私声明未完善/);
 nativeError=undefined;
 await api.committeeApi.list(3,'基坑工程',2);
 assert.deepEqual(Object.fromEntries(queries[0]),{projectId:'3',pageNo:'2',category:'基坑工程'});
 await api.committeeApi.list(3,'',1);assert.equal(queries[1].has('startDate'),false);assert.equal(queries[1].has('endDate'),false);
 for(const [ext,limit] of [['png',15],['docx',100],['mp4',500]]){api.validateCommitteeFile({name:`f.${ext}`,size:limit*1024**2,path:'x'});assert.throws(()=>api.validateCommitteeFile({name:`f.${ext}`,size:limit*1024**2+1,path:'x'}));}
 assert.throws(()=>api.validateCommitteeFile({name:'bad.svg',size:10,path:'x'}));
 const result=await api.uploadCommitteeFile({name:'test.mp4',size:500*1024**2,path:'wxfile://test'},{projectId:3,draftKey:'a'.repeat(32)},7,s=>progress.push(s),{cancelled:false});
 assert.equal(result,completed);assert.equal(statusReads,1);assert.equal(sent,32);assert.equal(reads.length,95);assert.ok(reads.every(r=>r[1]<=8*1024**2));assert.match(storageKeys[0],/^committee-upload:7:3:/);
 const before=reads.length;await assert.rejects(()=>api.uploadCommitteeFile({name:'test.mp4',size:500*1024**2,path:'x'},{projectId:3,draftKey:'a'.repeat(32)},7,()=>{},{cancelled:true}));assert.equal(reads.length,before);
 const url=await api.committeeApi.read(7);assert.equal(url,'http://127.0.0.1:8080/api/v1/safety-committee/media/opaque-code?preview=true');assert.ok(!url.includes('test-session'));
 let launched='';let switched='';globalThis.uni.reLaunch=o=>launched=o.url;globalThis.uni.switchTab=o=>switched=o.url;
 const nav=ts.transpileModule(await readFile(path.join(root,'src/utils/navigation.ts'),'utf8'),{compilerOptions:{target:ts.ScriptTarget.ES2022,module:ts.ModuleKind.ESNext}}).outputText;
 await writeFile(path.join(temporary,'nav.mjs'),nav);const navigation=await import(pathToFileURL(path.join(temporary,'nav.mjs')));
 navigation.switchTab('/pages/safety-committee/index');assert.equal(launched,'/pages/safety-committee/index');assert.equal(switched,'');navigation.switchTab('/pages/todo/index');assert.equal(switched,'/pages/todo/index');
 console.log('安委会专项通过：不带日期的分类分页查询、大小边界、500 MiB 按段读取及31片续传、取消、播放凭证和六入口路由（原生五tab）。');
}finally{await rm(temporary,{recursive:true,force:true});delete globalThis.uni;delete globalThis.wx;delete globalThis.committeeRequest;}
