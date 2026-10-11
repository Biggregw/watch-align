#!/usr/bin/env python3
"""Independent-watch level audit for exact polygon symmetric-difference percentage.
Exploratory research only; do NOT use resulting percentiles as production tolerances
without held-out review and photo-uncertainty checks.
"""
import csv
import math
import statistics as st
import sys
from collections import Counter, defaultdict

raw=list(csv.DictReader(open(sys.argv[1],newline='')))
readable=[r for r in raw if r.get('pose_status')=='accepted']
reasons=Counter(r['confidence'] for r in readable)
print('Rows with accepted pose:',len(readable),'confidence:',dict(reasons))
by=defaultdict(list)
for r in readable:
    try: val=float(r['mismatch_pct'])
    except (ValueError,TypeError): continue
    if not math.isfinite(val):continue
    key=(r['model'],r['model_shape'],r['hour'],r['physical_watch_id'])
    by[key].append((val,r['confidence'],r.get('dial_R_px','')))
# Correct sampling: first median within a physical watch, then compute across watches.
watch=defaultdict(list)
for (model,shape,hour,wid),rs in by.items():
    eligible=[v for v,reason,R in rs if reason in ('measured','provisional: radial edges of 12 vary with polished-surround lighting')]
    if not eligible:continue
    watch[(model,shape,hour)].append((st.median(eligible),wid,len(eligible),
        ('provisional' if shape=='triangle' else 'measured')))

def pct(vals,p):
    a=sorted(vals)
    if not a:return math.nan
    x=(len(a)-1)*p
    lo=int(math.floor(x));hi=int(math.ceil(x))
    return a[lo]*(hi-x)+a[hi]*(x-lo)

print('\nONE PHOTO/WATCH (repeat photos consolidated):')
print('model,shape,hour,independent_watches,median,p90,p95,p99,max,over4_percent,over4_watches,readout')
lines=[]
for key,rs in sorted(watch.items()):
    vals=[x[0] for x in rs]
    over=sum(v>4 for v in vals)
    line=[*key,str(len(vals)),f'{st.median(vals):.3f}',f'{pct(vals,.9):.3f}',
          f'{pct(vals,.95):.3f}',f'{pct(vals,.99):.3f}',f'{max(vals):.3f}',
          f'{over/len(vals)*100:.1f}',str(over),'PROVISIONAL' if key[1]=='triangle' else ('INSUFFICIENT' if len(vals)<20 else 'RESEARCH')]
    print(','.join(map(str,line)))
    lines.append(line)

print('\nCOVERAGE AND WITHHELD:')
for key,rs in sorted(defaultdict(list,((key,[r for r in readable if (r['model'],r['model_shape'],r['hour'])==key]) for key in {(r['model'],r['model_shape'],r['hour']) for r in readable})).items()):
    x=Counter(r['confidence'] for r in rs)
    print(key,'photos',len(rs),'usable','/'.join(f'{k}:{v}' for k,v in x.most_common()))

print('\nREPEATABILITY (same watch different originals), absolute photo-to-photo mismatch% changes:')
repeat=defaultdict(list)
for (model,shape,hour,wid),rs in by.items():
    good=[v for v,reason,R in rs if reason=='measured']
    if len(good)>1:repeat[(model,shape,hour)].append(max(good)-min(good))
for key,v in sorted(repeat.items()):
    print(key,'watch_pairs',len(v),'median',f'{st.median(v):.3f}','max',f'{max(v):.3f}')

if len(sys.argv)>2:
    with open(sys.argv[2],'w',newline='') as f:
        w=csv.writer(f)
        w.writerow(['model','shape','hour','independent_watches','median','p90','p95','p99','max','over4_pct','over4_watches','caution'])
        w.writerows(lines)
