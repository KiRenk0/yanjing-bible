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
// Source packaging/verification includes exactly Git-tracked files. Untracked
// personal work stays local and does not prevent publishing the reviewed commit.
if((await git(['status','--porcelain','--untracked-files=no'])).trim()) throw Error('Commit reviewed source before publishing');
if((await git(['branch','--show-current'])).trim()!=='main') throw Error('Publish from the verified main branch');
const sourceHead=(await git(['rev-parse','HEAD'])).trim();
if(verified.sourceCommit!==sourceHead)throw Error('Verified source does not match the release commit');
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
const body=`${metadata.notes}\n\n**法华经**：完整内置 CBETA T0262《妙法莲华经》，支持二十八品选择、上下文与独立语义索引。可以输入“法华经 25:1”“《妙法蓮華經》第二十五品第一段”“如来寿量品”“普门品1”，或用白话描述“尊重每一个人不轻视别人”。段号按所附正文／偈颂区块顺序。白话提示单独标注，仅辅助查找，不是完整翻译或权威注疏。\n\n**代码审查修复**：经典键和对照查询改由目录识别，避免新增经典漏掉收藏或被归入圣经；出处解析支持品／分与完整经名，并拒绝溢出的段号／范围；源码 ZIP 只收录 Git 已跟踪文件，避免夹带本地未审查文件。\n\n**来源**：[CBETA XML P5 固定版本](https://github.com/cbeta-org/xml-p5/blob/dbdea41071e1e260ad84b72faefd4587333cf76d/T/T09/T09n0262.xml)，CC BY-NC-SA 3.0 TW 及所附非营利使用条件。原始 XML、完整来源 header、授权和版本说明均随源码／应用保存，正文和对应索引与应用自有 MIT 代码分别授权。\n\n**升级方法**：沿用原签名，直接覆盖安装，无需卸载，保留收藏、历史、阅读进度和设置。ARM64 手机可用 ARM64 包；其他架构或不确定时使用通用包。\n\n[ARM64 安装包](${metadata.arm64.apkUrl}) · [通用安装包](${metadata.apkUrl})\n\n**验证**：${verified.tests} 项自动测试通过，lint 无错误。检查全部经典出处往返、真实模型白话检索、独立索引切换，以及 Android 15/16 Compose 中的法华经搜索、收藏、复制、阅读和位置恢复。原有搜索、启动和更新回归继续通过。逐包核对版本、原签名、SHA-256、16 KB 对齐、全部资源及压缩 DEX 中的更新数据库反射入口，并从固定 XML 重新提取、逐段核对法华经全文。未进行物理设备测试。详细检查见附件 verification.json。\n\nARM64 ${(verified.arm64.apkBytes/1e6).toFixed(1)} MB；通用版 ${(size.currentBytes/1e6).toFixed(1)} MB。Android 8.0 及以上。\n\nARM64 SHA-256：\`${metadata.arm64.sha256}\`\n\n通用包 SHA-256：\`${metadata.sha256}\`\n\n附件包含两种安装包、完整源码与数据、更新清单、校验和、验证记录及 R8 映射。`;
let release=await api('GET',`/repos/${repository}/releases/tags/${tag}`);
if(release && !release.draft)throw Error('This release is already published; refusing to overwrite it');
if(!release)release=await api('POST',`/repos/${repository}/releases`,{tag_name:tag,target_commitish:sourceHead,name:`言·经 ${tag} · 完整法华经与代码审查修复`,body,draft:true,prerelease:false});
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
