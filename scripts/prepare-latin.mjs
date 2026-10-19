import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
const root=path.resolve(import.meta.dirname,'..');
const assets=path.join(root,'app/src/main/assets');
const revision='e1b254cef86d0e65b1a5d1a94b8b112d0f296a2c';
const sourceUrl=`https://raw.githubusercontent.com/scrollmapper/bible_databases/${revision}/sources/la/VulgClementine/VulgClementine.json`;
const out=path.join(assets,'latin');fs.mkdirSync(out,{recursive:true});
const rawDir=path.join(root,'data/latin');fs.mkdirSync(rawDir,{recursive:true});
const rawFile=path.join(rawDir,'VulgClementine.json');
if(!fs.existsSync(rawFile)) {
 const response=await fetch(sourceUrl);if(!response.ok)throw Error('Latin source: '+response.status);
 fs.writeFileSync(rawFile,Buffer.from(await response.arrayBuffer()));
}
const raw=fs.readFileSync(rawFile);const data=JSON.parse(raw);
if(crypto.createHash('sha256').update(raw).digest('hex')!=='742a5d5aa2bb6342bd89bf38b109900a7db9342ada55385e40c82fb905a33ef8')throw Error('Pinned Latin source hash mismatch');
const bibleSource=fs.readFileSync(path.join(root,'app/src/main/java/org/yanjing/app/core/Bible.kt'),'utf8');
const list=name=>JSON.parse('['+bibleSource.match(new RegExp(`val ${name} = listOf\\(([^\\n]+)\\)`))[1]+']');
const english=list('english'),names=list('names');
const osis='Gen Exod Lev Num Deut Josh Judg Ruth 1Sam 2Sam 1Kgs 2Kgs 1Chr 2Chr Ezra Neh Esth Job Ps Prov Eccl Song Isa Jer Lam Ezek Dan Hos Joel Amos Obad Jonah Mic Nah Hab Zeph Hag Zech Mal Matt Mark Luke John Acts Rom 1Cor 2Cor Gal Eph Phil Col 1Thess 2Thess 1Tim 2Tim Titus Phlm Heb Jas 1Pet 2Pet 1John 2John 3John Jude Rev'.split(' ');
const extra={Tobit:['多比传','Tob'],Judith:['友弟德传','Jdt'],Wisdom:['智慧篇','Wis'],Sirach:['德训篇','Sir'],Baruch:['巴录书','Bar'],'I Maccabees':['玛加伯上','1Macc'],'II Maccabees':['玛加伯下','2Macc']};
const metadata=[],lines=[];
const normalize=t=>t.normalize('NFKD').toLowerCase().replaceAll('æ','ae').replaceAll('œ','oe').replace(/[^\p{L}\p{N}]/gu,'');
for(const [i,b]of data.books.entries()) {
 const nonempty=b.chapters.flatMap(c=>c.verses).filter(v=>v.text.trim());
 if(!nonempty.length)continue; // Upstream includes five entirely empty appendix placeholders.
 const en=b.name.replace(/^III /,'3 ').replace(/^II /,'2 ').replace(/^I /,'1 ').replace('Revelation of John','Revelation');
 const ci=english.indexOf(en);
 const book=i+101,title=ci>=0?names[ci]:extra[b.name]?.[0],osisId=ci>=0?osis[ci]:extra[b.name]?.[1];
 if(!title||!osisId)throw Error('Unknown book '+b.name);
 const counts=b.chapters.map(c=>Math.max(...c.verses.map(v=>v.verse)));
 metadata.push({book,title,english:b.name,osis:osisId,canonicalBook:ci>=0?ci+1:null,chapters:counts.length,verseCounts:counts,verses:nonempty.length});
 for(const c of b.chapters)for(const v of c.verses) {
  const text=v.text.replace(/<[^>]*>/g,'').replace(/&amp;/g,'&').replace(/[\t\r\n]+/g,' ').trim();if(!text)continue;
  if(text==='a')throw Error('Unresolved verse bridge');
  lines.push([book,c.chapter,v.verse,text,normalize(text)].join('\t'));
 }
}
if(metadata.length!==73||lines.length!==35809)throw Error('Incomplete Latin corpus');
fs.writeFileSync(path.join(assets,'bibles/vulgate.tsv'),lines.join('\n')+'\n');
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
// Piecewise reference anchors are data, kept separately under their source license.
const mappingSource=JSON.parse(fs.readFileSync(path.join(rawDir,'versification-source.json'),'utf8'));
const header=fs.readFileSync(path.join(rawDir,'canon_vulg.h'),'utf8');
if(hash(path.join(rawDir,'canon_vulg.h'))!=='8a3f6d059f460b8e424fdba0d6cbbfc6ba927951ee429bd3c8660bf8012c41f7')throw Error('Pinned versification source hash mismatch');
const body=header.match(/mappings_vulg\[\] = \{([\s\S]*?)\};/)[1];
const rules=body.split('\n').map(l=>l.trim()).filter(l=>/^\d+,/.test(l)).map(l=>l.match(/\d+/g).map(Number)).filter(r=>r.length>=7);
const kjv=new Map(fs.readFileSync(path.join(assets,'bibles/kjv.tsv'),'utf8').trim().split('\n').map(l=>{const p=l.split('\t');return [p.slice(0,3).join(':'),true];}));
const mappings=[];
for(const line of lines){
 const [book,c,v]=line.split('\t').slice(0,3).map(Number), b=book-100;
 const meta=metadata.find(m=>m.book===book);if(!meta.canonicalBook)continue;
 const anchors=rules.filter(r=>(r[0]===b||(r.length===8&&r[7]===b))&&r[1]===c&&r[2]<=v);
 const anchor=anchors.at(-1);let chapter=c,start=v,end=v;
 if(anchor){
  if(anchor[0]>78)continue; // Material with no shared canonical book is never guessed.
  const [, ,fromStart,fromEnd,toChapter,toStart,toEnd]=anchor;
  chapter=toChapter;
  if(v<=Math.max(fromStart,fromEnd)){start=toStart;end=Math.max(toStart,toEnd);}
  else {start=end=Math.max(toStart,toEnd)+v-Math.max(fromStart,fromEnd);}
 }else if(meta.osis==='Ps' && rules.some(r=>r[0]===b&&r[1]===c&&r[2]===2&&r[4]===c&&r[5]===1)){
  continue; // A separate superscription before the first verse has no KJV verse number.
 }
 const keys=[];for(let number=start;number<=end;number++){const key=`${meta.canonicalBook}:${chapter}:${number}`;if(kjv.has(key))keys.push(key);}
 if(keys.length)mappings.push(`${book}:${c}:${v}\t${keys.join('|')}`);
}
fs.writeFileSync(path.join(out,'mapping.tsv'),'# CrossWire Vulg versification data, GPL-2.0-only; source and complete original included in source archive.\n'+mappings.join('\n')+'\n');
fs.writeFileSync(path.join(out,'manifest.json'),JSON.stringify({schema:1,sourceUrl,revision,sourceSha256:hash(rawFile),license:'Public domain text; Scrollmapper MIT data',books:metadata,verses:lines.length,assetSha256:hash(path.join(assets,'bibles/vulgate.tsv')),excludedEmptyAppendices:data.books.slice(73).map(b=>b.name),versificationSource:{...mappingSource,sourceSha256:hash(path.join(rawDir,'canon_vulg.h'))},mappedVerses:mappings.length,mappingSha256:hash(path.join(out,'mapping.tsv'))},null,2)+'\n');
fs.writeFileSync(path.join(root,'app/src/main/java/org/yanjing/app/core/VulgateCatalog.kt'),
 '// Generated by scripts/prepare-latin.mjs. Source numbering is preserved.\npackage org.yanjing.app.core\n\ninternal val vulgateBooks = listOf(\n'+metadata.map(b=>`    LatinBook(${b.book},${JSON.stringify(b.title)},${JSON.stringify(b.english)},${b.canonicalBook||'null'},listOf(${b.verseCounts}))`).join(',\n')+'\n)\n');
console.log('Latin:',metadata.length,'books,',lines.length,'verses');
