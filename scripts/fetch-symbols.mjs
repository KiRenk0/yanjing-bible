import fs from 'node:fs/promises';
import path from 'node:path';
const icons = { search:'search', auto_awesome:'auto_awesome', menu_book:'menu_book', bookmark:'bookmark', bookmark_filled:'bookmark', settings:'settings', arrow_back:'arrow_back', arrow_forward:'arrow_forward', close:'close', content_copy:'content_copy', share:'share', chevron_right:'chevron_right', expand_more:'expand_more', history:'history', info:'info', check:'check' };
const out = path.resolve(import.meta.dirname, '../app/src/main/res/drawable');
await fs.mkdir(out, { recursive: true });
for (const [name, icon] of Object.entries(icons)) {
  const fill = name === 'bookmark_filled' ? 1 : 0;
  const url = `https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/${icon}/materialsymbolsoutlined/${icon}${fill ? '_fill1' : ''}_24px.xml`;
  const result = await fetch(url);
  if (!result.ok) throw Error(`${url}: ${result.status}`);
  const xml = (await result.text()).replace(/\s*android:tint="[^"]*"/g, '');
  await fs.writeFile(path.join(out, `sym_${name}.xml`), xml);
}
console.log('Material Symbols downloaded.');
