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
const names=[`yanjing-${metadata.versionName}.apk`,'update.json','SHA256SUMS.txt',`yanjing-${metadata.versionName}-source.zip`,'verification.json',`yanjing-${metadata.versionName}-mapping.txt.gz`];
const hashes=new Map(names.map(name=>[name,crypto.createHash('sha256').update(fs.readFileSync(path.join(root,'dist',name))).digest('hex')]));
if(hashes.get(names[0])!==metadata.sha256) throw Error('APK does not match update.json');
const verified=JSON.parse(fs.readFileSync(path.join(root,'dist/verification.json'),'utf8'));
if(verified.version!==metadata.versionName || verified.versionCode!==metadata.versionCode || verified.apkSha256!==metadata.sha256 || verified.sourceSha256!==hashes.get(names[3]) || verified.tests<1 || verified.failures!==0 || verified.lint?.errors!==0) throw Error('Release artifacts do not match successful verification');
if(verified.mappingSha256!==hashes.get(names[5])) throw Error('R8 mapping does not match verified build');
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
await api('PATCH',`/repos/${repository}`,{description:'言·经：Material 3 Expressive Android 经典查询，内置圣经、古兰经与论语，支持用现代汉语离线搜索文言原文、多模式检索与安全更新。'});
if(!(await git(['remote'])).split(/\r?\n/).includes('origin'))await git(['remote','add','origin',repo.clone_url]);
if((await git(['remote','get-url','origin'])).trim()!==repo.clone_url)throw Error('Unexpected git remote');
await git(['push','-u','origin','main']);
if(!(await git(['tag','--list',tag])).trim())await git(['tag','-a',tag,'-m',`言·经 ${tag}`]);
await git(['push','origin',tag]);
const size=verified.apkSizeComparison;
const body=`${metadata.notes}\n\n此次先处理 Xiaomi 15 Pro／HyperOS 4／Android 17 的启动反馈。没有该设备的异常堆栈，也没有真机复现；本版修正已验证的启动失败路径，仍需在反馈设备上覆盖安装确认。\n\n如果仍在打开时退出，请再次打开，进入“启动恢复”后尝试“兼容启动”，或点击“导出本机诊断”。也可长按桌面图标选择“启动恢复与诊断”。兼容启动关闭动态配色与自动检查更新，保留收藏、历史及阅读进度。诊断由用户选择分享，不会自动上传。\n\n${verified.tests} 项自动测试通过，lint 无错误。包含 Android 17 框架／原生绘图启动检查，Android 16/17 恢复与调度异常测试，以及原有搜索、阅读、收藏、真实 ONNX 检索、更新与签名校验回归。Android 17 尚未进行 Espresso 触控全流程或物理设备验证。\n\n通用 APK：${(size.currentBytes/1e6).toFixed(1)} MB，保留四种架构、全部文本和离线模型。Android 8.0 及以上；原签名，可直接覆盖旧版并保留本机数据。\n\n镜像下载：[线路一](https://gh-proxy.com/${metadata.apkUrl}) · [线路二](https://ghfast.top/${metadata.apkUrl})。\n\nAPK SHA-256：\`${metadata.sha256}\`\n\n附件包含安装包、源码、更新信息、校验和、验证摘要与 R8 映射。`;
let release=await api('GET',`/repos/${repository}/releases/tags/${tag}`);
if(release && !release.draft)throw Error('This release is already published; refusing to overwrite it');
if(!release)release=await api('POST',`/repos/${repository}/releases`,{tag_name:tag,target_commitish:sourceHead,name:`言·经 ${tag} · 启动兼容与恢复`,body,draft:true,prerelease:false});
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
