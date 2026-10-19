// Explicitly publishes the already reviewed, tested build. No credentials are saved.
// Usage: node --use-system-ca scripts/publish-github.mjs
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {spawn} from 'node:child_process';
const root=path.resolve(import.meta.dirname,'..');
const repository='KiRenk0/yanjing-bible';
const metadata=JSON.parse(fs.readFileSync(path.join(root,'dist/update.json'),'utf8'));
const tag=`v${metadata.versionName}`;
const names=[`yanjing-${metadata.versionName}.apk`,'update.json','SHA256SUMS.txt',`yanjing-${metadata.versionName}-source.zip`,'verification.json',`yanjing-${metadata.versionName}-mapping.txt.gz`,`yanjing-${metadata.versionName}-arm64-v8a.apk`];
const hashes=new Map(names.map(name=>[name,crypto.createHash('sha256').update(fs.readFileSync(path.join(root,'dist',name))).digest('hex')]));
if(hashes.get(names[0])!==metadata.sha256) throw Error('APK does not match update.json');
const verified=JSON.parse(fs.readFileSync(path.join(root,'dist/verification.json'),'utf8'));
if(verified.version!==metadata.versionName || verified.versionCode!==metadata.versionCode || verified.apkSha256!==metadata.sha256 || verified.sourceSha256!==hashes.get(names[3]) || verified.tests<1 || verified.failures!==0 || verified.lint?.errors!==0) throw Error('Release artifacts do not match successful verification');
if(verified.mappingSha256!==hashes.get(names[5])) throw Error('R8 mapping does not match verified build');
if(verified.arm64?.apkSha256!==hashes.get(names[6]) || verified.arm64.apkSha256!==metadata.arm64?.sha256)throw Error('ARM64 artifact does not match successful verification');
if(verified.updateDatabase?.publicNoArgumentConstructor!==true || verified.updateDatabase.checkedArtifact!==names[0])throw Error('Minified update database constructor was not verified');
const gradle=fs.readFileSync(path.join(root,'app/build.gradle.kts'),'utf8');
if(gradle.match(/versionName\s*=\s*"([0-9.]+)"/)[1]!==metadata.versionName || Number(gradle.match(/versionCode\s*=\s*(\d+)/)[1])!==metadata.versionCode) throw Error('Release metadata does not match source version');

async function git(args,input){
 return await new Promise((resolve,reject)=>{
  const process=spawn('git',args,{cwd:root,env:{...globalThis.process.env,GCM_INTERACTIVE:'Never',GIT_TERMINAL_PROMPT:'0'},windowsHide:true});
  let stdout='',stderr='';process.stdout.on('data',d=>stdout+=d);process.stderr.on('data',d=>stderr+=d);
  process.on('error',reject);process.on('exit',code=>code===0?resolve(stdout):reject(Error(`git ${args[0]} failed: ${stderr.slice(0,1200)}`)));
  process.stdin.end(input);
 });
}
if((await git(['status','--porcelain'])).trim()) throw Error('Commit reviewed source before publishing');
if((await git(['branch','--show-current'])).trim()!=='main') throw Error('Publish from the verified main branch');
const sourceHead=(await git(['rev-parse','HEAD'])).trim();
if((await git(['tag','--list',tag])).trim() && (await git(['rev-parse',`${tag}^{commit}`])).trim()!==sourceHead) throw Error('Existing release tag does not match verified source');
const tracked=(await git(['ls-files','-z'])).split('\0').filter(Boolean);
if(tracked.some(n=>/(^|\/)(\.tools|local.properties|signing.properties|dist)(\/|$)|\.(jks|keystore|p12)$/i.test(n))) throw Error('Private/build files must not be tracked');
let token=process.env.GITHUB_TOKEN;
if(!token){
 const credentials=await git(['credential','fill'],'protocol=https\nhost=github.com\n\n');
 token=credentials.split(/\r?\n/).find(s=>s.startsWith('password='))?.slice(9);
}
if(!token) throw Error('GitHub authentication unavailable');
const headers={Authorization:`Bearer ${token}`,Accept:'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28','User-Agent':'YanJing-Publisher'};
async function api(method,route,body){
 const response=await fetch('https://api.github.com'+route,{method,headers:{...headers,...(body?{'Content-Type':'application/json'}:{})},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(30000)});
 if(response.status===404)return null;
 const value=response.status===204?{}:await response.json();
 if(!response.ok)throw Error(`GitHub ${method} ${route}: ${response.status} ${value.message??''}`);
 return value;
}
const user=await api('GET','/user');
if(user.login!=='KiRenk0')throw Error('Authenticated account is not the intended repository owner');
let repo=await api('GET',`/repos/${repository}`);
if(!repo)repo=await api('POST','/user/repos',{name:'yanjing-bible',description:'言·经：Material 3 Expressive 中文圣经 Android 应用，离线句意检索、多模式搜索与安全更新。',private:false,has_wiki:false,auto_init:false});
console.log('Repository ready: '+repo.html_url);
await api('PATCH',`/repos/${repository}`,{description:'言·经：离线查询圣经（中英拉丁文）、古兰经及儒释道经典，用现代汉语查找文言原文；原生 Android、Material 3、多模式检索与安全更新。'});
if(!(await git(['remote'])).split(/\r?\n/).includes('origin'))await git(['remote','add','origin',repo.clone_url]);
if((await git(['remote','get-url','origin'])).trim()!==repo.clone_url)throw Error('Unexpected git remote');
await git(['push','-u','origin','main']);
if(!(await git(['tag','--list',tag])).trim())await git(['tag','-a',tag,'-m',`言·经 ${tag}`]);
await git(['push','origin',tag]);
const size=verified.apkSizeComparison;
const body=`${metadata.notes}\n\n**原因与修复**：用户提供的诊断指向 \`NoSuchMethodException: androidx.work.impl.WorkDatabase_Impl.<init> []\`。核查 1.5.0 实际发布 APK，确认 R8 移除了 Room 反射创建数据库所需的 public 无参构造函数。现在为这个入口添加精确保留规则，继续保留代码与资源压缩，并在发布验证中直接检查最终 APK 的 DEX；旧包会被此检查拒绝。同步修复检查更新覆盖 READY 状态、隐藏安装按钮的问题。\n\n**升级方法**：若 1.5.0 应用内更新不可用，请下载下方安装包直接覆盖安装，无需卸载。沿用原签名，保留收藏、历史、阅读进度和设置。小米 15 Pro 使用 ARM64 包；其他架构或不确定时使用通用包。\n\n[ARM64 安装包](${metadata.arm64.apkUrl}) · [通用安装包](${metadata.apkUrl})\n\n**验证**：${verified.tests} 项自动测试通过，lint 无错误；Android 16/17 按需初始化与定期任务入队、下载/READY 状态保护、原有搜索与阅读流程均有回归检查。两个压缩发布包的 DEX 内容一致，数据库 public 无参构造函数存在；逐包核对版本、签名、SHA-256 与 16 KB 对齐。没有物理设备测试，仍需在所反馈设备确认。详情见 [修复与复查记录](https://github.com/${repository}/blob/${tag}/docs/update-component-review.md) 和附件 verification.json。\n\nARM64 ${(verified.arm64.apkBytes/1e6).toFixed(1)} MB；通用版 ${(size.currentBytes/1e6).toFixed(1)} MB。保留 1.5.0 全部经典、离线模型、语义索引，以及 Android 17 启动保护。Android 8.0 及以上。\n\nARM64 SHA-256：\`${metadata.arm64.sha256}\`\n\n通用包 SHA-256：\`${metadata.sha256}\`\n\n附件包含两种安装包、完整源码与数据、更新清单、校验和、验证记录及 R8 映射。`;
let release=await api('GET',`/repos/${repository}/releases/tags/${tag}`);
if(release && !release.draft)throw Error('This release is already published; refusing to overwrite it');
if(!release)release=await api('POST',`/repos/${repository}/releases`,{tag_name:tag,target_commitish:sourceHead,name:`言·经 ${tag} · 修复更新组件不可用`,body,draft:true,prerelease:false});
for(const name of names){
 const existing=release.assets?.find(a=>a.name===name);
 if(existing?.digest===`sha256:${hashes.get(name)}`){console.log('Verified existing asset: '+name);continue;}
 if(existing)await api('DELETE',`/repos/${repository}/releases/assets/${existing.id}`);
 const bytes=fs.readFileSync(path.join(root,'dist',name));
 console.log(`Uploading ${name} (${bytes.length} bytes)`);
 const response=await fetch(release.upload_url.replace(/\{.*$/,'')+'?name='+encodeURIComponent(name),{method:'POST',headers:{...headers,'Content-Type':name.endsWith('.json')?'application/json':'application/octet-stream','Content-Length':String(bytes.length)},body:bytes,signal:AbortSignal.timeout(300000)});
 const asset=await response.json();
 if(!response.ok || asset.size!==bytes.length || asset.state!=='uploaded' || asset.digest!==`sha256:${hashes.get(name)}`)throw Error(`Upload verification failed: ${name} (${response.status})`);
 console.log('Verified asset: '+name);
}
const refreshed=await api('GET',`/repos/${repository}/releases/${release.id}`);
if(!names.every(name=>refreshed.assets.some(a=>a.name===name && a.digest===`sha256:${hashes.get(name)}`)))throw Error('Incomplete draft release');
release=await api('PATCH',`/repos/${repository}/releases/${release.id}`,{draft:false,prerelease:false,make_latest:'true',body});
await api('PUT',`/repos/${repository}/topics`,{names:['android','bible','quran','analects','chinese','arabic','jetpack-compose','material3','offline','semantic-search']});
fs.writeFileSync(path.join(root,'dist/publication.json'),JSON.stringify({repository:repo.html_url,release:release.html_url,tag,assets:release.assets.map(a=>({name:a.name,size:a.size,digest:a.digest,url:a.browser_download_url}))},null,2));
console.log('Published: '+release.html_url);
