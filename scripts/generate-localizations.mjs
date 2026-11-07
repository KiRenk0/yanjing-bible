import fs from 'node:fs';
import path from 'node:path';
import {Converter} from 'opencc-js';
const root=path.resolve(import.meta.dirname,'..');
const traditional=Converter({from:'cn',to:'tw'});
const read=name=>fs.readFileSync(path.join(root,name),'utf8');
const android=JSON.parse(read('localization/android.json'));
const ios=JSON.parse(read('localization/ios.json'));
const xml=value=>value.replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll("'","\\'").replaceAll('"','\\"').replaceAll('\n','\\n');
const bible=read('app/src/main/java/org/yanjing/app/core/Bible.kt');
const list=key=>JSON.parse('['+bible.match(new RegExp(`val ${key} = listOf\\(([^\\n]+)\\)`))[1]+']');
const names=list('names'), english=list('english');
for(const [qualifier,language] of [['values','en'],['values-zh-rCN','zh-Hans'],['values-zh-rTW','zh-Hant']]) {
  const target=path.join(root,'app/src/main/res',qualifier);fs.mkdirSync(target,{recursive:true});
  const value=record=>language==='en'?record.en:language==='zh-Hant'?traditional(record.source):record.source;
  const lines=android.map(record=>`    <string name="${record.key}">${xml(value(record))}</string>`);
  lines.unshift(`    <string name="app_name">${language==='en'?'YanJing':'盐筋'}</string>`);
  lines.push('    <string-array name="book_names">',...(language==='en'?english:language==='zh-Hant'?names.map(traditional):names).map(name=>`        <item>${xml(name)}</item>`),'    </string-array>');
  fs.writeFileSync(path.join(target,'strings.xml'),`<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated from localization/android.json. -->\n<resources>\n${lines.join('\n')}\n</resources>\n`);
}
const fallback=android.map(record=>`        R.string.${record.key} to ${JSON.stringify(record.source).replaceAll('$','\\$')}`).join(',\n');
fs.writeFileSync(path.join(root,'app/src/main/java/org/yanjing/app/i18n/I18nFallback.kt'),`// Generated from localization/android.json; keeps platform-free retrieval tests deterministic.\npackage org.yanjing.app.i18n\n\nimport org.yanjing.app.R\n\ninternal object I18nFallback {\n    val values = mapOf(\n${fallback}\n    )\n}\n`);
for(const language of ['en','zh-Hans','zh-Hant']) {
  const target=path.join(root,'ios/YanJing/Resources',language+'.lproj');fs.mkdirSync(target,{recursive:true});
  const value=entry=>language==='en'?entry[0]:language==='zh-Hant'?(entry[2]??traditional(entry[1])):entry[1];
  fs.writeFileSync(path.join(target,'Localizable.strings'),'// Generated from localization/ios.json.\n'+Object.entries(ios).map(([key,entry])=>`${JSON.stringify(key)} = ${JSON.stringify(value(entry))};`).join('\n')+'\n');
  fs.writeFileSync(path.join(target,'InfoPlist.strings'),`"CFBundleDisplayName" = "${language==='en'?'YanJing':'盐筋'}";\n`);
}
console.log(`Generated ${android.length} Android and ${Object.keys(ios).length} iOS strings for en, zh-Hans and zh-Hant.`);
