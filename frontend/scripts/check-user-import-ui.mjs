// Optional browser acceptance: existing local frontend, synthetic API responses, no business writes.
// PLAYWRIGHT_PACKAGE can point to the installed desktop runtime's playwright package.
import { createRequire } from 'node:module';
import { mkdir } from 'node:fs/promises';
import assert from 'node:assert/strict';
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_PACKAGE || 'playwright');
const output = process.env.USER_IMPORT_UI_OUTPUT || '/tmp/dianxinyun-user-import-ui';
await mkdir(output, { recursive: true });
const browser = await chromium.launch({channel:'chrome',headless:true});
const modules = ['SITE_ACCESS','DOCUMENT','INSPECTION','QUALITY','SAFETY_COMMITTEE'];
const project = {id:1,projectName:'合成验收项目',projectStatus:'building'};
const context = {projectId:1,accessStatus:'ACTIVE',enabledBusinessModules:modules,moduleConfigVersion:1,menuCodes:[],permissionCodes:[]};
const admin = {id:9,username:'ui-admin',realName:'验收管理员',roles:['PLATFORM_ADMIN'],initialPasswordSetupRequired:false,projectContexts:[context],menus:[],permissionCodes:[]};
const calls = [], errors = [];
let initial = true, invalid = false, confirmed = 0;
let batch = null;
const page = await browser.newPage({viewport:{width:1440,height:1000},deviceScaleFactor:1});
page.on('pageerror', error => errors.push(error.message));
await page.addInitScript(() => localStorage.setItem('site_platform_token','synthetic-ui-session'));
await page.route('**/api/**', async route => {
  const request = route.request(), url = new URL(request.url()), path = url.pathname.replace(/^\/api(?:\/v1)?/,''); calls.push(path);
  let data = {records:[],total:0};
  if (path==='/auth/user-info') data = initial ? {id:19,username:'19999000000',realName:'合成人员',initialPasswordSetupRequired:true,initialPasswordSetupReason:'ADMIN_IMPORT',roles:[],menus:[],projectContexts:[],permissionCodes:[]} : admin;
  else if (path==='/auth/initial-password') {initial=false;data={token:'synthetic-activated-session'};}
  else if (path==='/projects' || path==='/projects/my') data=[project];
  else if (path.endsWith('/business-modules')) data={projectId:1,enabledBusinessModules:modules,moduleConfigVersion:1};
  else if (path==='/system/project-modules') data={records:[{projectId:1,projectName:project.projectName,enabledBusinessModules:modules,moduleConfigVersion:1}],total:1};
  else if (path==='/system/users') data={records:[{id:19,realName:'合成人员',username:'19999000000',phone:'19999000000',status:1,mustChangePassword:true,passwordLoginEnabled:1,temporaryPasswordExpiresAt:'2026-10-14T12:00:00',projectRoles:[]}],total:1};
  else if (path==='/system/user-imports/preview') {
    batch={id:101,status:invalid?'INVALID':'PREVIEW',owned:true,personCount:2,newCount:1,skippedCount:1,errorCount:invalid?1:0,preparedCount:0,createdByName:'验收管理员',createdAt:'2026-09-14T12:00:00',message:invalid?'存在错误，请修正名单后重新上传':'校验完成，请核对新增账号和项目角色',rows:[{rowNumber:2,realName:'合成人员甲',phone:'19999000001',projectLabel:'合成验收项目 [P:1]',roleLabel:'资料员 [R:2]',status:invalid?'ERROR':'NEW',message:invalid?'姓名不一致':'新增账号'},{rowNumber:3,realName:'合成人员乙',phone:'19999000002',projectLabel:'合成验收项目 [P:1]',roleLabel:'资料员 [R:2]',status:'SKIPPED',message:'已有账号，跳过且不修改'}]};data=batch;
  } else if (path==='/system/user-imports') data={records:batch?[batch]:[],total:batch?1:0};
  else if (path.endsWith('/confirm')) {confirmed++;batch={...batch,status:'QUEUED',message:'已确认，等待生成账号'};data=batch;}
  else if (path==='/system/user-imports/101') {if(batch.status==='QUEUED')batch={...batch,status:'SUCCEEDED',preparedCount:1,message:'导入完成，请在24小时内下载账号发放表'};data=batch;}
  else if (path.endsWith('/template')||path.endsWith('/credentials')||path.endsWith('/temporary-password')) {await route.fulfill({status:200,headers:{'content-type':'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet','content-disposition':'attachment; filename="synthetic.xlsx"'},body:'synthetic download fixture'});return;}
  await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({code:200,message:'success',data})});
});
try {
  await page.goto(process.env.USER_IMPORT_UI_URL || 'http://localhost:3002');
  await page.getByRole('heading',{name:'设置个人登录密码'}).waitFor();
  await page.screenshot({path:output+'/initial-desktop.png',fullPage:true});
  await page.waitForTimeout(5200);
  assert.ok(calls.every(path=>path==='/auth/user-info'),'restricted page must not request projects, modules, or to-dos');
  await page.getByLabel('新密码',{exact:true}).fill('PersonalPassword7');await page.getByLabel('确认新密码').fill('PersonalPassword7');
  await page.getByRole('button',{name:'确认并进入系统'}).click();
  await page.getByRole('button',{name:/系统管理/}).first().waitFor();
  await page.getByRole('button',{name:/系统管理/}).first().click();
  await page.getByRole('button',{name:/用户管理/}).click();
  await page.getByRole('button',{name:'批量导入',exact:true}).click();
  await page.getByRole('heading',{name:'批量导入用户'}).waitFor();
  await page.screenshot({path:output+'/empty-desktop.png',fullPage:true});
  const upload=async()=>page.locator('input[type=file]').setInputFiles({name:'users.xlsx',mimeType:'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',buffer:Buffer.from('synthetic fixture handled by intercepted API')});
  invalid=true;await upload();await page.getByText('姓名不一致',{exact:true}).waitFor();
  assert.equal(await page.getByRole('button',{name:/确认导入.*人/}).count(),0,'invalid batch cannot be confirmed');
  await page.screenshot({path:output+'/invalid-desktop.png',fullPage:true});
  invalid=false;await upload();await page.getByRole('button',{name:'确认导入 1 人'}).waitFor();
  await page.screenshot({path:output+'/preview-desktop.png',fullPage:true});
  await page.getByRole('button',{name:'确认导入 1 人'}).click();
  await page.getByRole('button',{name:'下载账号发放表',exact:true}).waitFor();
  const downloadPromise=page.waitForEvent('download');await page.getByRole('button',{name:'下载账号发放表',exact:true}).click();assert.equal((await downloadPromise).suggestedFilename(),'账号发放表.xlsx');assert.equal(confirmed,1);
  await page.screenshot({path:output+'/complete-desktop.png',fullPage:true});
  await page.getByRole('button',{name:'关闭',exact:true}).last().click();
  await page.getByRole('button',{name:'临时密码',exact:true}).click();
  await page.getByRole('heading',{name:/临时密码 -/}).waitFor();await page.screenshot({path:output+'/temporary-password-desktop.png',fullPage:true});
  for(const width of [320,375,430]) {
    initial=true;calls.length=0;await page.setViewportSize({width,height:850});await page.reload();await page.getByRole('heading',{name:'设置个人登录密码'}).waitFor();
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>window.innerWidth),false);await page.screenshot({path:`${output}/initial-${width}.png`,fullPage:true});
  }
  assert.deepEqual(errors,[]);console.log('Web UI passed: restricted startup, first password, import validation, confirmation/progress/download, temporary password dialog, and 320/375/430 widths.');
} catch (error) {
  await page.screenshot({path:output+'/failure.png',fullPage:true});
  console.error((await page.locator('body').innerText()).slice(-6000), errors);
  throw error;
} finally {await browser.close();}
