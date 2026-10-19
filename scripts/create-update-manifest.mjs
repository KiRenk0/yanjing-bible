// Run after building and signing the APK. The Release must be marked latest.
import fs from 'node:fs';
import crypto from 'node:crypto';
import path from 'node:path';
import {gzipSync} from 'node:zlib';
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
  notes:'切换经典、同类书目或译本时保留搜索词，并自动在新文本中重新搜索、更新结果，无需再次输入或提交。保留搜索模式，仍可主动清空搜索。沿用原签名，可直接覆盖安装，保留收藏、历史、阅读进度和设置。继续保留更新组件修复、Android 17 启动保护、全部经典与离线检索。'};
const armName=`yanjing-${versionName}-arm64-v8a.apk`;
const armBytes=fs.readFileSync(path.join(root,'dist',armName));
metadata.arm64={apkUrl:`https://github.com/KiRenk0/yanjing-bible/releases/download/v${versionName}/${armName}`,size:armBytes.length,
  sha256:crypto.createHash('sha256').update(armBytes).digest('hex')};
fs.writeFileSync(path.join(root,'dist/update.json'),JSON.stringify(metadata,null,2)+'\n');
fs.writeFileSync(path.join(root,'dist/SHA256SUMS.txt'),`${sha256}  ${apkName}\n${metadata.arm64.sha256}  ${armName}\n`);
const mapping=fs.readFileSync(path.join(root,'app/build/outputs/mapping/release/mapping.txt'));
fs.writeFileSync(path.join(root,'dist',`yanjing-${versionName}-mapping.txt.gz`),gzipSync(mapping,{level:9}));
console.log(JSON.stringify({versionName,versionCode,bytes:bytes.length,sha256}));
