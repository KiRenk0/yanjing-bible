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
  notes:'新增 SwiftUI 原生 iOS 版，并为 Android / iOS 加入英语、简体中文、繁体中文界面，可跟随系统或独立切换。iOS 提供可由免费 Apple ID 重新签名侧载的未签名 IPA。Android 保持原签名，覆盖升级保留本机数据。',
  notesLocalized:{
    en:'Native SwiftUI iOS app and English, Simplified Chinese and Traditional Chinese interfaces on both platforms. Includes all offline texts and search resources. An unsigned iOS IPA is available for local sideload signing with a free Apple ID. Android keeps its signing certificate for in-place upgrades.',
    'zh-CN':'新增 SwiftUI 原生 iOS 版，并为 Android / iOS 加入英语、简体中文、繁体中文界面，可跟随系统或独立切换。iOS 提供免费 Apple ID 本机签名侧载用的未签名 IPA。Android 保持原签名，覆盖升级保留本机数据。',
    'zh-TW':'新增 SwiftUI 原生 iOS 版，並為 Android / iOS 加入英語、簡體中文、繁體中文介面，可跟隨系統或獨立切換。iOS 提供免費 Apple ID 本機簽名側載用的未簽名 IPA。Android 保持原簽名，覆蓋升級保留本機資料。'
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
