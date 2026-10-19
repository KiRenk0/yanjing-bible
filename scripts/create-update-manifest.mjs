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
  notes:'启动兼容更新：自动更新改为按需初始化并延后至界面就绪，调度异常不再直接导致主界面退出；系统动态色板缺失时使用内置配色。新增独立启动恢复页，可兼容启动、保留本机数据并导出诊断。补充 Android 17 启动与恢复测试。尚无 Xiaomi 15 Pro／HyperOS 4 的真机堆栈，具体设备问题仍需覆盖安装后验证。'};
fs.writeFileSync(path.join(root,'dist/update.json'),JSON.stringify(metadata,null,2)+'\n');
fs.writeFileSync(path.join(root,'dist/SHA256SUMS.txt'),`${sha256}  ${apkName}\n`);
const mapping=fs.readFileSync(path.join(root,'app/build/outputs/mapping/release/mapping.txt'));
fs.writeFileSync(path.join(root,'dist',`yanjing-${versionName}-mapping.txt.gz`),gzipSync(mapping,{level:9}));
console.log(JSON.stringify({versionName,versionCode,bytes:bytes.length,sha256}));
