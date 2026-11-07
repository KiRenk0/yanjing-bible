import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
const root=path.resolve(import.meta.dirname,'..');
const read=name=>fs.readFileSync(path.join(root,name),'utf8');
const placeholders=value=>[...value.matchAll(/%(\d+)\$[sd]|%[ds@]/g)].map(m=>m[0]).sort();
const android=[...JSON.parse(read('localization/android.json')),...Object.entries(JSON.parse(read('localization/formatting.json'))).map(([key,[en,source]])=>({key,en,source}))];
assert.equal(new Set(android.map(x=>x.key)).size,android.length);
for(const record of android) {
  assert(record.source && record.en,record.key);
  if(record.key!=='chapter_with_title')assert.deepEqual(placeholders(record.source),placeholders(record.en),record.key);
}
for(const directory of ['values','values-zh-rCN','values-zh-rTW']) {
  const contents=read(`app/src/main/res/${directory}/strings.xml`);
  const keys=[...contents.matchAll(/<string name="([^"]+)"/g)].map(m=>m[1]);
  assert.equal(new Set(keys).size,keys.length);
  for(const record of android) assert(keys.includes(record.key),`${directory}/${record.key}`);
}
const ios=JSON.parse(read('localization/ios.json'));
for(const [key,values] of Object.entries(ios)) {assert(values[0]&&values[1],key);assert.deepEqual(placeholders(values[0]),placeholders(values[1]),key);}
for(const language of ['en','zh-Hans','zh-Hant']) {
  const contents=read(`ios/YanJing/Resources/${language}.lproj/Localizable.strings`);
  for(const key of Object.keys(ios)) assert(contents.includes(JSON.stringify(key)+' = '),`${language}/${key}`);
}
console.log(`Localization verified: ${android.length} Android and ${Object.keys(ios).length} iOS keys in three languages; format placeholders match.`);
