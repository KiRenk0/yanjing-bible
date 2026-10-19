// Build complete classical texts from pinned originals; modern hints are separate.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {Converter} from 'opencc-js';
const root=path.resolve(import.meta.dirname,'..');
const assets=path.join(root,'app/src/main/assets');
const simplify=Converter({from:'t',to:'cn'});
const sources=JSON.parse(fs.readFileSync(path.join(import.meta.dirname,'classic-sources.json'),'utf8'));
const hash=b=>crypto.createHash('sha256').update(b).digest('hex');
const read=name=>{
 const source=sources.find(s=>s.file===name);if(!source)throw Error(name);
 const b=fs.readFileSync(path.join(root,'data/classics/sources',name));
 if(hash(b)!==source.sha256)throw Error('Source hash mismatch: '+name);
 return b.toString('utf8');
};
const json=name=>JSON.parse(read(name));
const chapters=(name,one=false)=>{
 const d=json('chinese-poetry-'+name+'.json');
 return (one?[d]:d).map(c=>({title:simplify(c.chapter),paragraphs:c.paragraphs}));
};
const dao=json('banghuazhao-道德经_json.json')['道德经'].map((c,i)=>({title:`第${i+1}章`,paragraphs:[c['章节内容'].replace(/[\r\n\t]+/g,' ').trim()]}));
const zhuangzi=['内篇_json','外篇','杂篇'].flatMap(part=>{
 const data=json('banghuazhao-庄子'+part+'.json');
 return Object.values(data)[0].map(c=>({title:c['章节名称'],paragraphs:c['章节内容'].split(/\n\s*\n/).map(p=>p.replace(/[\r\n\t]+/g,' ').trim()).filter(Boolean)}));
});
const heart=read('hooosberg-zh-Hant.md').split(/^---\s*$/m).slice(2).join('---').split('\n')
 .filter(s=>s.trim()&&!s.startsWith('#')&&!s.startsWith('*')).map(s=>s.trim());
const diamondText=read('hooosberg-zh-Hant_kumarajiva.md').split(/^---\s*$/m).slice(2).join('---');
const diamond=[...diamondText.matchAll(/^## ([^\n]+)\n([\s\S]*?)(?=^## |$(?![\s\S]))/gm)].map(m=>({
 title:simplify(m[1].split('『')[0].trim()),
 paragraphs:m[2].trim().split(/\n\s*\n/).map(p=>p.replace(/\r?\n/g,'').trim()).filter(Boolean)
}));
const works=[
 {id:'daxue',enum:'GREAT_LEARNING',book:69,title:'大学',group:'ANALECTS',chapters:chapters('daxue',true),source:'chinese-poetry'},
 {id:'zhongyong',enum:'DOCTRINE_MEAN',book:70,title:'中庸',group:'ANALECTS',chapters:chapters('zhongyong',true),source:'chinese-poetry'},
 {id:'mengzi',enum:'MENCIUS',book:71,title:'孟子',group:'ANALECTS',chapters:chapters('mengzi'),source:'chinese-poetry'},
 {id:'heart_sutra',enum:'HEART_SUTRA',book:72,title:'心经',group:'BUDDHISM',chapters:[{title:'般若波罗蜜多心经',paragraphs:heart}],source:'hooosberg'},
 {id:'diamond_sutra',enum:'DIAMOND_SUTRA',book:73,title:'金刚经',group:'BUDDHISM',chapters:diamond,source:'hooosberg'},
 {id:'tao_te_ching',enum:'TAO_TE_CHING',book:74,title:'道德经',group:'TAOISM',chapters:dao,source:'banghuazhao'},
 {id:'zhuangzi',enum:'ZHUANGZI',book:75,title:'庄子',group:'TAOISM',chapters:zhuangzi,source:'banghuazhao'},
 {id:'lotus_sutra',enum:'LOTUS_SUTRA',book:76,title:'法华经',group:'BUDDHISM',chapters:JSON.parse(fs.readFileSync(path.join(root,'data/classics/lotus-sutra.json'),'utf8')).map(c=>({...c,title:simplify(c.title)})),source:'cbeta'},
];
// Verify the pinned XML even when the intermediate extraction already exists.
read('cbeta-T09n0262.xml');
const expected={daxue:1,zhongyong:1,mengzi:14,heart_sutra:1,diamond_sutra:32,tao_te_ching:81,zhuangzi:33,lotus_sutra:28};
const hints=JSON.parse(fs.readFileSync(path.join(assets,'classics/search-hints.json'),'utf8'));
const expressionGroups=[];
const t2s=new Map(fs.readFileSync(path.join(assets,'t2s.tsv'),'utf8').split('\n').filter(Boolean).map(l=>l.split('\t')));
for(const work of works)for(const chapter of work.chapters)for(const text of chapter.paragraphs)for(const ch of text.normalize('NFKC')) {
 const simplified=simplify(ch);if(ch!==simplified&&[...simplified].length===1)t2s.set(ch,simplified);
}
// Match Android's context-free character conversion, including its UTF-16 filtering.
const normalize=t=>[...t.normalize('NFKC')].map(ch=>t2s.get(ch)||ch).join('').toLowerCase().split('').filter(ch=>/[\p{L}\p{N}]/u.test(ch)).join('');
for(const w of works){
 if(w.chapters.length!==expected[w.id])throw Error('Incomplete chapters: '+w.id+' '+w.chapters.length);
 const lines=[];w.verseCounts=[];w.hints=0;
 for(const [ci,c]of w.chapters.entries()){
  w.verseCounts.push(c.paragraphs.length);
  for(const [vi,text]of c.paragraphs.entries()){
   if(!text.trim()||/[\t\r\n]/.test(text))throw Error('Invalid paragraph '+w.id);
   const matched=hints.filter(h=>h.work===w.id&&normalize(text).includes(normalize(h.anchor)));
   matched.forEach(h=>h.found=(h.found||0)+1);
   if(matched.length) expressionGroups.push({version:w.enum,key:`VerseKey(${w.book},${ci+1},${vi+1})`,phrases:matched.flatMap(h=>[h.anchor,...h.hint.split(/[，。；]/).filter(p=>p.length>=4)])});
   const gloss=matched.map(h=>h.hint).join('；');if(gloss)w.hints++;
   lines.push([w.book,ci+1,vi+1,text,normalize(text),gloss].join('\t'));
  }
 }
 const bytes=Buffer.from(lines.join('\n')+'\n');
 fs.writeFileSync(path.join(assets,'bibles',w.id+'.tsv'),bytes);
 w.verses=lines.length;w.sha256=hash(bytes);console.log(w.id,w.chapters.length,w.verses,'paragraphs; hints',w.hints);
}
for(const hint of hints)if(!hint.found)throw Error('Unmatched hint: '+hint.work+' '+hint.anchor);
fs.writeFileSync(path.join(assets,'t2s.tsv'),[...t2s].map(([a,b])=>a+'\t'+b).join('\n'));
const metadata=works.map(({enum:en,chapters,...w})=>({...w,version:en,chapterNames:chapters.map(c=>c.title)}));
fs.writeFileSync(path.join(assets,'classics/manifest.json'),JSON.stringify({schema:1,sources,works:metadata,hintNotice:'本项目编写的现代汉语检索提示，辅助查找，不是完整翻译或权威注疏。编号为所附版本的篇章与段落序号。'},null,2)+'\n');
const string=s=>JSON.stringify(s);
fs.writeFileSync(path.join(root,'app/src/main/java/org/yanjing/app/core/ClassicCatalog.kt'),
 '// Generated by scripts/prepare-classics.mjs.\npackage org.yanjing.app.core\n\ninternal val classicWorks = listOf(\n'+
 metadata.map(w=>`    ClassicWork(BibleVersion.${w.version}, ${w.book}, ${string(w.title)}, listOf(${w.chapterNames.map(string)}), listOf(${w.verseCounts}), ${string(w.sources||w.source)})`).join(',\n')+'\n)\n');
fs.writeFileSync(path.join(root,'app/src/main/java/org/yanjing/app/core/ClassicExpressions.kt'),
 '// Generated from the separately authored classic search hints.\npackage org.yanjing.app.core\n\ninternal val classicExpressions = mapOf(\n'+
 metadata.map(w=>`    BibleVersion.${w.version} to listOf(\n`+expressionGroups.filter(g=>g.version===w.version).map(g=>`        ExpressionGroup(listOf(${g.phrases.map(string)}),listOf(${g.key}))`).join(',\n')+'\n    )').join(',\n')+'\n)\n');

// A shared normalization table also affects previously shipped corpora. Refresh
// their search-only columns so adding a text cannot break old keyword searches.
// Use UTF-16 map entries exactly as Android's Map<Char, Char> loader does.
const androidMap=new Map([...t2s].map(([a,b])=>[a[0],b[0]]));
const androidNormalize=t=>t.normalize('NFKC').split('').map(ch=>androidMap.get(ch)||ch).join('').toLowerCase().split('').filter(ch=>/[\p{L}\p{N}]/u.test(ch)).join('');
const normalizationChanges=new Map();
for(const name of fs.readdirSync(path.join(assets,'bibles'))){
 if(!name.endsWith('.tsv')||['quran_ar.tsv','vulgate.tsv'].includes(name))continue;
 const file=path.join(assets,'bibles',name),before=fs.readFileSync(file,'utf8');
 const after=before.split('\n').map(line=>{
  if(!line||line.startsWith('#'))return line;
  const fields=line.split('\t');fields[4]=androidNormalize(fields[3]);return fields.join('\t');
 }).join('\n');
 if(after!==before){
  normalizationChanges.set(name.slice(0,-4),{before:hash(before),after:hash(after),bytes:Buffer.byteLength(after)});
  fs.writeFileSync(file,after);console.log('Refreshed search normalization:',name);
 }
}
const updateJson=(name,edit)=>{
 const file=path.join(assets,name),before=fs.readFileSync(file,'utf8'),value=JSON.parse(before);
 edit(value);if(JSON.stringify(JSON.parse(before))!==JSON.stringify(value))fs.writeFileSync(file,JSON.stringify(value,null,2)+'\n');
};
const refreshHash=(metadata,field,id)=>{
 const change=normalizationChanges.get(id);if(!change)return;
 if(metadata[field]!==change.before)throw Error('Rebuild changed source before refreshing normalization: '+id);
 metadata[field]=change.after;
};
updateJson('data-manifest.json',m=>m.versions.forEach(v=>refreshHash(v,'assetSha256',v.id)));
updateJson('quran/manifest.json',m=>m.versions.forEach(v=>refreshHash(v,'sha256',v.id)));
updateJson('analects/manifest.json',m=>{
 refreshHash(m.files['analects.tsv'],'sha256','analects');
 if(normalizationChanges.has('analects'))m.files['analects.tsv'].bytes=normalizationChanges.get('analects').bytes;
});
// Embedding input uses original text and separate hints, never the normalized
// search column. Updating its hash does not require changing existing vectors.
for(const name of fs.readdirSync(path.join(assets,'semantic')).filter(n=>n.endsWith('manifest.json')))
 updateJson('semantic/'+name,m=>refreshHash(m,'corpusSha256',m.sourceVersion));
