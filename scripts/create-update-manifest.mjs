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
  notes:'软件名称更改为“盐筋”：桌面标签、首页、设置页、启动恢复和诊断报告统一使用新名称。原 logo、包名及签名不变，可直接覆盖安装，保留收藏、历史、阅读进度和设置。保留全部经典、离线检索及更新功能。'};
const armName=`yanjing-${versionName}-arm64-v8a.apk`;
const armBytes=fs.readFileSync(path.join(root,'dist',armName));
metadata.arm64={apkUrl:`https://github.com/KiRenk0/yanjing-bible/releases/download/v${versionName}/${armName}`,size:armBytes.length,
  sha256:crypto.createHash('sha256').update(armBytes).digest('hex')};
fs.writeFileSync(path.join(root,'dist/update.json'),JSON.stringify(metadata,null,2)+'\n');
fs.writeFileSync(path.join(root,'dist/SHA256SUMS.txt'),`${sha256}  ${apkName}\n${metadata.arm64.sha256}  ${armName}\n`);
const mapping=fs.readFileSync(path.join(root,'app/build/outputs/mapping/release/mapping.txt'));
fs.writeFileSync(path.join(root,'dist',`yanjing-${versionName}-mapping.txt.gz`),gzipSync(mapping,{level:9}));
console.log(JSON.stringify({versionName,versionCode,bytes:bytes.length,sha256}));
