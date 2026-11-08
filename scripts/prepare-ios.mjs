// Assemble the iOS bundle from the same reviewed, byte-identical Android resources.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {Converter} from 'opencc-js';
const root = path.resolve(import.meta.dirname, '..');
const assets = path.join(root, 'app/src/main/assets');
const output = path.join(root, 'ios/YanJing/Resources/Corpus');
const traditional = Converter({from:'cn',to:'tw'});
const read = name => fs.readFileSync(path.join(root,name),'utf8');
const json = name => JSON.parse(fs.readFileSync(path.join(assets,name),'utf8'));
const sha = bytes => crypto.createHash('sha256').update(bytes).digest('hex');
fs.mkdirSync(output,{recursive:true});
const copied = [];
function copyTree(from, prefix='') {
  for (const file of fs.readdirSync(path.join(assets,from),{withFileTypes:true})) {
    const name = path.posix.join(from,file.name);
    if(file.isDirectory())copyTree(name);
    else copy(name);
  }
}
function copy(name) {
  const bytes = fs.readFileSync(path.join(assets,name));
  const target = path.join(output,name);
  fs.mkdirSync(path.dirname(target),{recursive:true});fs.writeFileSync(target,bytes);
  copied.push({path:name,bytes:bytes.length,sha256:sha(bytes)});
}
for(const directory of ['bibles','semantic','latin','quran','analects','classics','devotions'])copyTree(directory);
copy('t2s.tsv');copy('data-manifest.json');
const licenses = fs.readdirSync(assets).filter(name=>/LICENSE|NOTICE|TERMS|Apache-2\.0|GPL-2\.0/.test(name)).sort();
fs.writeFileSync(path.join(output,'Licenses.txt'),licenses.map(name=>`=== ${name} ===\n${fs.readFileSync(path.join(assets,name),'utf8')}`).join('\n\n'));
const bible = read('app/src/main/java/org/yanjing/app/core/Bible.kt');
function list(source,key) {return JSON.parse('['+source.match(new RegExp(`val ${key} = listOf\\(([^\\n]+)\\)`))[1]+']');}
const names = list(bible,'names'), english = list(bible,'english'), short = list(bible,'shortNames');
const books = names.map((zh,i)=>({id:i+1,zh,traditional:traditional(zh),en:english[i],aliases:[zh,short[i],english[i]],chapters:[]}));
const latin = json('latin/manifest.json');
for(const book of latin.books) books.push({id:book.book,zh:book.title,traditional:traditional(book.title),en:book.english,aliases:[book.title,book.english,book.osis],chapters:[]});
const analects = read('app/src/main/java/org/yanjing/app/core/Analects.kt');
books.push({id:67,zh:'古兰经',traditional:'古蘭經',en:'Quran',aliases:['Quran','Koran','古兰经'],chapters:json('quran/chapters.json').map(x=>`${x.transliteration} · ${x.arabicName}`)});
books.push({id:68,zh:'论语',traditional:'論語',en:'Analects',aliases:['论语','Analects'],chapters:list(analects,'names')});
const englishClassics={daxue:'Great Learning',zhongyong:'Doctrine of the Mean',mengzi:'Mencius',heart_sutra:'Heart Sutra',diamond_sutra:'Diamond Sutra',lotus_sutra:'Lotus Sutra',tao_te_ching:'Tao Te Ching',zhuangzi:'Zhuangzi'};
for(const work of json('classics/manifest.json').works)books.push({id:work.book,zh:work.title,traditional:traditional(work.title),en:englishClassics[work.id],aliases:[work.title,englishClassics[work.id],...(work.id==='lotus_sutra'?['妙法莲华经']:[])],chapters:work.chapterNames});
fs.writeFileSync(path.join(output,'books.json'),JSON.stringify(books));
const bridgeSource = read('app/src/main/java/org/yanjing/app/core/CuvVerseBridges.kt');
const bridges = Object.fromEntries([...bridgeSource.matchAll(/VerseKey\((\d+),(\d+),(\d+)\) to VerseKey\((\d+),(\d+),(\d+)\)/g)].map(m=>[m.slice(1,4).join(':'),m.slice(4,7).join(':')]));
fs.writeFileSync(path.join(output,'bridges.json'),JSON.stringify(bridges));
const expressions=[];
const expressionSource=read('app/src/main/java/org/yanjing/app/core/ExpressionLexicon.kt');
for(const match of expressionSource.matchAll(/group\("([^"\n]+)"((?:,\s*"\d+:\d+:\d+")+?)\)/g)) {
  const keys=[...match[2].matchAll(/"([\d:]+)"/g)].map(m=>m[1]);
  const book=Number(keys[0].split(':')[0]);
  expressions.push({version:book===68?'analects':book===67?'quran':'bible',phrases:match[1].split('|'),keys});
}
const classicSource=read('app/src/main/java/org/yanjing/app/core/ClassicExpressions.kt');
for(const match of classicSource.matchAll(/ExpressionGroup\(listOf\(([^\n]*?)\),listOf\(([^\n]*?)\)\)/g)) {
  const phrases=[...match[1].matchAll(/"([^"\n]+)"/g)].map(m=>m[1]);
  const keys=[...match[2].matchAll(/VerseKey\((\d+),(\d+),(\d+)\)/g)].map(m=>m.slice(1,4).join(':'));
  const book=Number(keys[0]?.split(':')[0]);
  const work=json('classics/manifest.json').works.find(x=>x.book===book);
  if(work)expressions.push({version:work.id,phrases,keys});
}
fs.writeFileSync(path.join(output,'expressions.json'),JSON.stringify(expressions));
fs.writeFileSync(path.join(output,'tokenizer-fixtures.json'),read('app/src/test/resources/tokenizer-fixtures.json'));
fs.writeFileSync(path.join(output,'bundle-manifest.json'),JSON.stringify({schema:1,files:copied},null,2)+'\n');
console.log(`iOS corpus ready: ${copied.length} byte-identical files, ${books.length} book entries, ${expressions.length} expression groups.`);
