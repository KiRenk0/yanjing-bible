import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

const run = promisify(execFile);
const revision = 'e1b254cef86d0e65b1a5d1a94b8b112d0f296a2c';
const root = path.resolve(import.meta.dirname, '..');
const out = path.join(root, 'data/raw');
const cache = path.join(root, '.tools/bible-sources');
const names = ['zh-hant/ChiUn', 'lzh/ChiUnL', 'en/KJV'];
await fs.mkdir(out, { recursive: true });
const manifest = JSON.parse(await fs.readFile(path.join(root, 'app/src/main/assets/data-manifest.json'), 'utf8'));
let complete = true;
for (const name of names) {
  const id = name.split('/')[1];
  try {
    const bytes = await fs.readFile(path.join(out, `${id}.json`));
    const expected = manifest.versions.find(v => v.sourceFile.endsWith(`/${id}.json`))?.rawSha256;
    if (crypto.createHash('sha256').update(bytes).digest('hex') !== expected) complete = false;
  } catch { complete = false; }
}
if (complete) {
  console.log('All source files verified against the pinned manifest.');
} else {
  await fs.mkdir(path.dirname(cache), { recursive: true });
  try { await fs.access(path.join(cache, '.git')); }
  catch { await run('git', ['clone', '--filter=blob:none', '--depth', '1', '--sparse', 'https://github.com/scrollmapper/bible_databases.git', cache]); }
  await run('git', ['-C', cache, 'fetch', '--depth', '1', 'origin', revision]);
  await run('git', ['-C', cache, 'sparse-checkout', 'set', ...names.map(n => `sources/${n}`)]);
  await run('git', ['-C', cache, 'checkout', '--detach', revision]);
  for (const name of names) {
    const id = name.split('/')[1];
    const bytes = await fs.readFile(path.join(cache, 'sources', name, `${id}.json`));
    JSON.parse(bytes.toString('utf8'));
    await fs.writeFile(path.join(out, `${id}.json`), bytes);
    console.log(`${id}: ${bytes.length} bytes at ${revision}`);
  }
}
