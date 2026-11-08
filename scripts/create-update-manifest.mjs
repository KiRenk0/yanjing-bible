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
  notes:'新增「读经」入口，内置玫瑰经四端、二十个奥迹和十二篇祷词。支持逐步诵念、十遍计数、各组进度保存、三种语言、复制与分享。提供 Android APK 和 iOS 未签名 IPA，Android 保持原签名可覆盖升级。',
  notesLocalized:{
    en:'New offline Devotions tab with twenty Rosary mysteries, a step-by-step guide, Hail Mary counts, saved progress and twelve prayers. Supports English, Simplified Chinese and Traditional Chinese, copy and sharing. APK and unsigned iOS IPA included. Android keeps its existing signing certificate.',
    'zh-CN':'新增「读经」入口，内置玫瑰经四端、二十个奥迹和十二篇祷词。支持逐步诵念、十遍计数、各组进度保存、三种语言、复制与分享。提供 Android APK 和 iOS 未签名 IPA，Android 保持原签名可覆盖升级。',
    'zh-TW':'新增「讀經」入口，內置玫瑰經四端、二十個奧跡和十二篇禱詞。支援逐步誦念、十遍計數、各組進度儲存、三種語言、複製與分享。提供 Android APK 和 iOS 未簽名 IPA，Android 保持原簽名可覆蓋升級。'
  }};
const armName=`yanjing-${versionName}-arm64-v8a.apk`;
const armBytes=fs.readFileSync(path.join(root,'dist',armName));
metadata.arm64={apkUrl:`https://github.com/KiRenk0/yanjing-bible/releases/download/v${versionName}/${armName}`,size:armBytes.length,
  sha256:crypto.createHash('sha256').update(armBytes).digest('hex')};
fs.writeFileSync(path.join(root,'dist/update.json'),JSON.stringify(metadata,null,2)+'\n');
fs.writeFileSync(path.join(root,'dist/SHA256SUMS.txt'),`${sha256}  ${apkName}\n${metadata.arm64.sha256}  ${armName}\n`);
const mapping=fs.readFileSync(path.join(root,'app/build/outputs/mapping/release/mapping.txt'));
fs.writeFileSync(path.join(root,'dist',`yanjing-${versionName}-mapping.txt.gz`),gzipSync(mapping,{level:9}));
console.log(JSON.stringify({versionName,versionCode,bytes:bytes.length,sha256}));
