// Reproducible shared devotional resources. Does not download or rewrite corpus texts.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import {Converter} from 'opencc-js';
const root = path.resolve(import.meta.dirname, '..');
const traditional = Converter({from: 'cn', to: 'tw'});
const source = fs.readFileSync(path.join(root, 'data/devotions.json'));
const data = JSON.parse(source);
function localize(value) {
  if (Array.isArray(value)) return value.map(localize);
  if (value && typeof value === 'object') {
    if (value.en && value.zhHans) return {...value, zhHant: value.zhHant ?? traditional(value.zhHans)};
    return Object.fromEntries(Object.entries(value).map(([key, child]) => [key, localize(child)]));
  }
  return value;
}
const prayers = new Set(data.prayers.map(p => p.id));
assert.equal(prayers.size, 12);
assert.equal(data.sets.length, 4);
assert.deepEqual(data.sets.flatMap(s => s.days).sort(), [1,2,3,4,5,6,7]);
for (const id of [...data.opening, ...data.closing, 'our_father', 'hail_mary', 'glory_be', 'fatima']) assert(prayers.has(id), id);
for (const set of data.sets) assert.equal(set.mysteries.length, 5, set.id);
const output = path.join(root, 'app/src/main/assets/devotions');
fs.mkdirSync(output, {recursive: true});
fs.writeFileSync(path.join(output, 'catalog.json'), JSON.stringify(localize(data), null, 2) + '\n');
fs.writeFileSync(path.join(output, 'manifest.json'), JSON.stringify({schema: 1, source: 'data/devotions.json', sourceSha256: crypto.createHash('sha256').update(source).digest('hex'), prayers: prayers.size, sets: data.sets.length, mysteries: 20, languages: ['en', 'zhHans', 'zhHant']}, null, 2) + '\n');
fs.copyFileSync(path.join(root, 'third_party/DEVOTIONS-NOTICE.txt'), path.join(root, 'app/src/main/assets/DEVOTIONS-NOTICE.txt'));
console.log('Devotions ready: 12 prayers, 4 sets, 20 mysteries, three languages.');
