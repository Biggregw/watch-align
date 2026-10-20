from rescore import *
import statistics as st
def q(a,p):
    a=sorted(a);
    if not a: return float('nan')
    k=(len(a)-1)*p;i=int(k);return a[i]+(a[min(i+1,len(a)-1)]-a[i])*(k-i)
meas=lambda r:{'tilt':abs(f(r['tilt_line_deg'])),'pos':math.hypot(f(r['side_pct']),f(r['inout_pct'])) if r['shape']!='triangle' else float('nan'),
 'side':abs(f(r['side_pct'])),'inout':abs(f(r['inout_pct'])),'gap':f(r['gap_pct'])}
print('== genuine distribution by shape/family, measurable rows; clean = no uncertainty note')
for shape,keys in [('round',['pos']),('baton',['pos','tilt']),('triangle',['side','inout','tilt','gap'])]:
  for k in keys:
    for fam,rows in GEN.items():
      for cl in ('clean','uncert'):
        v=[meas(r)[k] for r in rows if r['shape']==shape and not r['gate'] and (not r['uncertain'])==(cl=='clean')]
        v=[x for x in v if fin(x)]
        if not v: continue
        print(f'{shape:8} {k:5} {fam:6} {cl:6} n={len(v):4} med={q(v,.5):.3f} p95={q(v,.95):.3f} p99={q(v,.99):.3f} max={max(v):.3f} min={min(v):.3f}')
print('\n== repeatability: same physical watch, same hour, >=2 photos (SD across photos, pooled)')
for shape,k in [('round','side'),('round','inout'),('baton','side'),('baton','inout'),('baton','tilt'),('triangle','tilt'),('triangle','gap'),('triangle','side'),('triangle','inout')]:
  for fam,rows in GEN.items():
    g=collections.defaultdict(list)
    for r in rows:
      if r['shape']!=shape or r['gate']: continue
      x={'side':f(r['side_pct']),'inout':f(r['inout_pct']),'tilt':f(r['tilt_line_deg']),'gap':f(r['gap_pct'])}[k]
      if fin(x): g[(r['watch'],r['hour'])].append(x)
    ss=[];dfree=0
    for v in g.values():
      if len(v)>=2: m=sum(v)/len(v);ss.append(sum((x-m)**2 for x in v));dfree+=len(v)-1
    if dfree: print(f'{shape:8} {k:5} {fam:6} groups={sum(1 for v in g.values() if len(v)>=2):4} within-watch SD={math.sqrt(sum(ss)/dfree):.3f}')
print('\n== signed medians (systematic offsets) baton 6/9 and triangle, per family')
for fam,rows in GEN.items():
  for h in ('6','9','12'):
    s=[f(r['side_pct']) for r in rows if r['hour']==h and not r['gate'] and fin(f(r['side_pct']))]
    i=[f(r['inout_pct']) for r in rows if r['hour']==h and not r['gate'] and fin(f(r['inout_pct']))]
    t=[f(r['tilt_line_deg']) for r in rows if r['hour']==h and not r['gate'] and fin(f(r['tilt_line_deg']))]
    print(f'{fam:6} h{h:2} side med {q(s,.5):+.3f} inout med {q(i,.5):+.3f} tilt med {q(t,.5):+.3f}  |side| med {q([abs(x) for x in s],.5):.3f} |inout| med {q([abs(x) for x in i],.5):.3f} n={len(s)}')
