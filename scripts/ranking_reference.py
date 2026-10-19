"""Portable reference for the Kotlin second-stage semantic ranker, used by evaluation."""
from pathlib import Path
from collections import Counter, defaultdict
import math
import re
import unicodedata

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app/src/main/assets"
STOP = set("的了是我你他她它们在有和与及就也都这那吗呢吧啊呀不着把被个地得为以而其之乎者也要能会应该不能一个自己别人什么怎么为什么是否时候")

def expression_hints():
    text = (ROOT / "app/src/main/java/org/yanjing/app/core/ExpressionLexicon.kt").read_text(encoding="utf-8")
    hints = defaultdict(list)
    for m in re.finditer(r'group\("([^"]+)",\s*((?:"[0-9:]+"(?:,\s*)?)+)\)',text):
        for key in re.findall(r'"([0-9:]+)"',m[2]): hints[key].extend(m[1].split('|'))
    for m in re.finditer(r'ExpressionGroup\(listOf\((.*?)\), listOf\((.*?)\)\)',text):
        phrases = re.findall(r'"([^"]+)"',m[1])
        for b,c,v in re.findall(r'VerseKey\((\d+),(\d+),(\d+)\)',m[2]): hints[f"{b}:{c}:{v}"].extend(phrases)
    return hints

class Ranker:
    def __init__(self,version):
        self.mapping = dict(line.split('\t') for line in (ASSETS / "t2s.tsv").read_text(encoding="utf-8").splitlines())
        self.groups = [line.split('|') for line in (ASSETS / "semantic/concepts.txt").read_text(encoding="utf-8").splitlines() if line and not line.startswith('#')]
        terms = {term:group[0] for group in self.groups for term in group}
        self.term_pattern = re.compile('|'.join(re.escape(term) for term in sorted(terms,key=lambda s:-len(s))))
        self.terms = terms
        hints = expression_hints()
        self.docs={}; self.df=Counter(); self.postings=defaultdict(set); self.concept_postings=defaultdict(set)
        for line in (ASSETS / f"bibles/{version}.tsv").read_text(encoding="utf-8").splitlines():
            if not line or line.startswith('#'): continue
            row=line.split('\t'); key=':'.join(row[:3])
            texts=[row[3]]+([row[5]] if len(row)>5 else [])+hints[key]
            windows=[]
            for text in texts:
                normalized=self.normalize(text)
                windows.extend(normalized[i:i+180] for i in range(0,len(normalized),120))
            self.docs[key]=[(self.fold(text),self.concepts(text)) for text in windows]
            unique=set(''.join(t for t,_ in self.docs[key]))
            self.df.update(unique)
            for char in unique: self.postings[char].add(key)
            for group in set().union(*(groups for _,groups in self.docs[key])): self.concept_postings[group].add(key)
        self.idf={char:math.log(1+len(self.docs)/(1+count)) for char,count in self.df.items()}
        self.unseen=math.log(1+len(self.docs))

    def normalize(self,text):
        return ''.join(self.mapping.get(c,c).lower() for c in unicodedata.normalize('NFKC',text) if c.isalnum())

    def fold(self,text): return self.term_pattern.sub(lambda m:self.terms[m[0]],text)
    def concepts(self,text): return {i for i,g in enumerate(self.groups) if any(term in text for term in g)}

    def query(self,raw):
        normalized=self.normalize(raw); folded=self.fold(normalized)
        chars=set(folded)-STOP
        grams={folded[i:i+2] for i in range(len(folded)-1) if any(c not in STOP for c in folded[i:i+2])}
        weights={c:self.idf.get(c,self.unseen) for c in chars}
        gram_weights={g:sum(self.idf.get(c,self.unseen) for c in g)/2 for g in grams}
        return weights,gram_weights,self.concepts(normalized)

    def evidence(self,query,key):
        chars,grams,concepts=query
        best=0
        for text,doc_concepts in self.docs.get(key,[]):
            char_coverage=sum(w for c,w in chars.items() if c in text)/max(sum(chars.values()),1e-9)
            gram_coverage=sum(w for g,w in grams.items() if g in text)/max(sum(grams.values()),1e-9)
            concept_coverage=len(concepts & doc_concepts)/max(len(concepts),1)
            best=max(best,0.08*char_coverage+0.13*gram_coverage+0.12*concept_coverage)
        return best

    def seeds(self,raw,limit=32):
        query=self.query(raw); chars,_,concepts=query
        rare=sorted((c for c in chars if c in self.postings),key=lambda c:(len(self.postings[c]),c))[:2]
        possible=set().union(*(self.postings[c] for c in rare),*(self.concept_postings[g] for g in concepts))
        weighted=sorted(((key,self.evidence(query,key)) for key in possible),key=lambda p:(-p[1],p[0]))
        return [key for key,score in weighted[:limit] if score>=0.1]

    def rank(self,raw,candidates,limit=60):
        query=self.query(raw)
        # Bound lexical influence so explicit wording cannot overpower a strong semantic match.
        scored=[dict(c,score=c['cosine']+0.5*self.evidence(query,c['key'])) for c in candidates if c['key'] in self.docs]
        scored.sort(key=lambda c:(-c['score'],c['key']))
        if not scored: return []
        best=scored[0]['score']
        return [c for c in scored if c['cosine']>=0.42 and c['score']>=max(0.5,best-0.15)][:limit]
