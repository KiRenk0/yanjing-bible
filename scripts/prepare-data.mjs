import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { Converter } from 'opencc-js';

const root = path.resolve(import.meta.dirname, '..');
const out = path.join(root, 'app/src/main/assets');
const simplify = Converter({ from: 't', to: 'cn' });
const merged = JSON.parse(fs.readFileSync(path.join(root, 'data/cuv-verse-mappings.json'), 'utf8'));
const mergedKeys = new Set(merged.mappings.map(v => v.from));
const bridgesSource = 'package org.yanjing.app.core\n\n// Generated from data/cuv-verse-mappings.json; verified verse bridges, never inferred at runtime.\ninternal val cuvVerseBridges = mapOf(\n' + merged.mappings.map(v => `        VerseKey(${v.from.replaceAll(':', ',')}) to VerseKey(${v.to.replaceAll(':', ',')})`).join(',\n') + '\n)\n';
fs.writeFileSync(path.join(root, 'app/src/main/java/org/yanjing/app/core/CuvVerseBridges.kt'), bridgesSource);
const clean = s => s.replace(/<[^>]*>/g, '').replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/[\t\r\n]+/g, ' ').trim();
const normalize = s => s.normalize('NFKC').toLowerCase().replace(/[^\p{L}\p{N}]/gu, '');
const mapping = {};
const manifest = { source: 'https://github.com/scrollmapper/bible_databases', revision: 'e1b254cef86d0e65b1a5d1a94b8b112d0f296a2c', versions: [] };
manifest.cuvMergedVerses = { verifiedAgainst: merged.source, license: merged.license, archiveSha256: merged.archiveSha256, mappings: merged.mappings };
fs.mkdirSync(path.join(out, 'bibles'), { recursive: true });
for (const [source, id, name, script] of [
  ['ChiUn', 'cuvs', '和合本·简体', 'simplified'],
  ['ChiUn', 'cuvt', '和合本·繁體', 'traditional'],
  ['ChiUnL', 'wenli', '文理和合本', 'traditional'],
  ['KJV', 'kjv', 'King James Version', 'english'],
]) {
  const raw = fs.readFileSync(path.join(root, `data/raw/${source}.json`));
  const data = JSON.parse(raw);
  if (data.books.length !== 66) throw Error(`${source}: expected 66 books, got ${data.books.length}`);
  const lines = [];
  for (const [b, book] of data.books.entries()) for (const chapter of book.chapters) for (const verse of chapter.verses) {
    const original = script === 'english' ? clean(verse.text) : clean(verse.text).replace(/\s+/gu, '');
    if (source === 'ChiUn' && mergedKeys.has(`${b + 1}:${verse.chapter}:${verse.verse}`)) {
      if (original !== 'a') throw Error('Upstream changed: re-review merged verse');
      continue; // SWORD's "a" denotes a verse bridge, not scripture text.
    }
    if (original === 'a') throw Error('Unmapped verse bridge in corpus');
    if (!original) continue; // Some upstream versifications have explicitly empty verses.
    const text = script === 'simplified' ? simplify(original) : original;
    const normalized = normalize(simplify(text));
    lines.push(`${b + 1}\t${verse.chapter}\t${verse.verse}\t${text}\t${normalized}`);
    for (const ch of text) {
      const converted = simplify(ch);
      if (ch !== converted && [...converted].length === 1) mapping[ch] = converted;
    }
  }
  if (lines.length < 30000) throw Error(`${id}: incomplete corpus (${lines.length})`);
  const asset = lines.join('\n') + '\n';
  fs.writeFileSync(path.join(out, `bibles/${id}.tsv`), asset);
  manifest.versions.push({ id, name, verses: lines.length, books: 66, sourceFile: `sources/${source === 'ChiUn' ? 'zh-hant' : source === 'ChiUnL' ? 'lzh' : 'en'}/${source}/${source}.json`, rawSha256: crypto.createHash('sha256').update(raw).digest('hex'), assetSha256: crypto.createHash('sha256').update(asset).digest('hex') });
  console.log(`${name}: ${lines.length} verses`);
}
fs.writeFileSync(path.join(out, 't2s.tsv'), Object.entries(mapping).map(([a,b]) => `${a}\t${b}`).join('\n'));
fs.writeFileSync(path.join(out, 'data-manifest.json'), JSON.stringify(manifest, null, 2));
for (const file of ['scrollmapper-LICENSE.txt', 'Apache-2.0.txt', 'NOTICE.md']) {
  fs.copyFileSync(path.join(root, 'third_party', file), path.join(out, file));
}
fs.copyFileSync(path.join(root, 'node_modules/opencc-js/LICENSE'), path.join(root, 'third_party/opencc-js-LICENSE.txt'));
fs.copyFileSync(path.join(root, 'third_party/opencc-js-LICENSE.txt'), path.join(out, 'opencc-js-LICENSE.txt'));
