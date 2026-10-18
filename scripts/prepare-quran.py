"""Prepare offline resources without changing displayed text; originals retain their notices."""
from pathlib import Path
import csv
import hashlib
import json
import re
import unicodedata
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / 'app/src/main/assets'
SOURCE = ASSETS / 'quran'

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def normalize(text, arabic=False):
    text = unicodedata.normalize('NFKD' if arabic else 'NFKC', text).lower()
    if arabic:
        text = text.translate(str.maketrans({'ٱ':'ا','أ':'ا','إ':'ا','آ':'ا','ى':'ي'}))
    return ''.join(c for c in text if c.isalnum() and (not arabic or c not in 'ـۥۦ'))

def main():
    info = json.loads((SOURCE / 'sources.json').read_text(encoding='utf8'))
    chapters = json.loads((SOURCE / 'chapters.json').read_text(encoding='utf8'))
    for key in ['chinese','arabic']:
        assert sha(SOURCE / info[key]['file']) == info[key]['sha256'], 'Source changed; review version and authorization'
    with (SOURCE / info['chinese']['file']).open(encoding='utf-8-sig',newline='') as stream:
        reader = csv.reader(stream)
        transcript_info = next(reader)[0]
        assert 'v1.0.2-csv.1' in transcript_info
        assert next(reader) == ['id','sura','aya','translation','footnotes']
        chinese = [(int(row[1]),int(row[2]),row[3],row[4]) for row in reader]
    arabic = []
    preludes = {}
    for sura in ET.parse(SOURCE / info['arabic']['file']).getroot():
        for aya in sura:
            c,v = int(sura.attrib['index']),int(aya.attrib['index'])
            arabic.append((c,v,aya.attrib['text'],''))
            if 'bismillah' in aya.attrib: preludes[c] = aya.attrib['bismillah']
    assert set(preludes) == set(range(2,115)) - {9}
    expected = [(chapter['number'],v) for chapter in chapters for v in range(1,chapter['verses']+1)]
    assert len(expected) == 6236
    versions = []
    for name,rows,is_arabic in [('quran_zh',chinese,False),('quran_ar',arabic,True)]:
        assert [(c,v) for c,v,_,_ in rows] == expected
        assert all(t and not any(ch in t for ch in '\t\n\r') for _,_,t,_ in rows)
        # This source edition has no footnotes; fail rather than silently lose a future addition.
        assert not any(note for _,_,_,note in rows)
        if is_arabic:
            raw=(SOURCE / info['arabic']['file']).read_text(encoding='utf8')
            header=raw.split('<!--',1)[1].split('-->',1)[0].strip()+'\n'
        else:
            header='# '+transcript_info+'\n# Republishing terms: https://quranenc.com/en/home/about\n'
        data = header + ''.join(f'67\t{c}\t{v}\t{t}\t{normalize(t,is_arabic)}\n' for c,v,t,_ in rows)
        target = ASSETS / f'bibles/{name}.tsv'
        target.write_text(data,encoding='utf8',newline='\n')
        versions.append({'id':name,'verses':6236,'chapters':114,'sha256':sha(target)})
    (SOURCE / 'manifest.json').write_text(json.dumps({'schema':1,'versions':versions},indent=2)+'\n',encoding='utf8')
    counts=','.join(str(c['verses']) for c in chapters)
    names=','.join(json.dumps(c['name'],ensure_ascii=False) for c in chapters)
    transliterations=','.join(json.dumps(c['transliteration']) for c in chapters)
    kotlin=ROOT/'app/src/main/java/org/yanjing/app/core/QuranChapters.kt'
    basmala=json.dumps(next(iter(preludes.values())),ensure_ascii=False)
    variants=','.join(f'{c} to {json.dumps(text,ensure_ascii=False)}' for c,text in preludes.items() if text != next(iter(preludes.values())))
    kotlin.write_text(f'package org.yanjing.app.core\n\n// Generated from the attributed assets/quran/chapters.json and original XML.\ninternal val quranVerseCounts = listOf({counts})\ninternal val quranChapterNames = listOf({names})\ninternal val quranTransliterations = listOf({transliterations})\ninternal const val quranBasmala = {basmala}\ninternal val quranBasmalaVariants = mapOf({variants})\n',encoding='utf8')
    print('Prepared 2 Quran texts, 114 chapters / 6,236 verses each; text preserved verbatim')

if __name__ == '__main__':
    main()
