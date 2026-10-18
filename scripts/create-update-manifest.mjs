// Run after building and signing the APK. The Release must be marked latest.
import fs from 'node:fs';
import crypto from 'node:crypto';
import path from 'node:path';
const root=path.resolve(import.meta.dirname,'..');
const gradle=fs.readFileSync(path.join(root,'app/build.gradle.kts'),'utf8');
const versionName=gradle.match(/versionName\s*=\s*"([0-9.]+)"/)[1];
const versionCode=Number(gradle.match(/versionCode\s*=\s*(\d+)/)[1]);
const apkName=`yanjing-${versionName}.apk`;
const apk=path.join(root,'dist',apkName);
const bytes=fs.readFileSync(apk);
const sha256=crypto.createHash('sha256').update(bytes).digest('hex');
const metadata={schema:1,applicationId:'org.yanjing.app',versionCode,versionName,minSdk:26,
  apkUrl:`https://github.com/KiRenk0/yanjing-bible/releases/download/v${versionName}/${apkName}`,
  size:bytes.length,sha256,
  notes:'新增古兰经查询：完整 114 章、6,236 节，内置马坚中文经义译本与阿拉伯文。支持离线句意搜索、多模式合并、章号节号定位、中阿对照、整章阅读和收藏。圣经与古兰经分别检索并保存阅读位置；保留镜像自动更新。'};
fs.writeFileSync(path.join(root,'dist/update.json'),JSON.stringify(metadata,null,2)+'\n');
fs.writeFileSync(path.join(root,'dist/SHA256SUMS.txt'),`${sha256}  ${apkName}\n`);
console.log(JSON.stringify({versionName,versionCode,bytes:bytes.length,sha256}));
