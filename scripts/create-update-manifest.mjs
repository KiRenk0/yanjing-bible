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
  notes:'修复 1.5.0 显示“更新组件暂时不可用”的问题：保留更新数据库通过反射调用的无参构造函数，恢复自动检查、手动检查和下载更新。修复重复检查可能隐藏已下载安装包的问题。新增对最终压缩 APK 的构造函数检查，保留 Android 17 启动保护、全部经典与离线检索。1.5.0 更新器无法使用时，请从 Release 下载此版本覆盖安装，保留收藏、历史与阅读进度。'};
const armName=`yanjing-${versionName}-arm64-v8a.apk`;
const armBytes=fs.readFileSync(path.join(root,'dist',armName));
metadata.arm64={apkUrl:`https://github.com/KiRenk0/yanjing-bible/releases/download/v${versionName}/${armName}`,size:armBytes.length,
  sha256:crypto.createHash('sha256').update(armBytes).digest('hex')};
fs.writeFileSync(path.join(root,'dist/update.json'),JSON.stringify(metadata,null,2)+'\n');
fs.writeFileSync(path.join(root,'dist/SHA256SUMS.txt'),`${sha256}  ${apkName}\n${metadata.arm64.sha256}  ${armName}\n`);
const mapping=fs.readFileSync(path.join(root,'app/build/outputs/mapping/release/mapping.txt'));
fs.writeFileSync(path.join(root,'dist',`yanjing-${versionName}-mapping.txt.gz`),gzipSync(mapping,{level:9}));
console.log(JSON.stringify({versionName,versionCode,bytes:bytes.length,sha256}));
