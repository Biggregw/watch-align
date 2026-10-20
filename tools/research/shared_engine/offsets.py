from rescore import *
def q(a,p):
    a=sorted(a)
    if not a: return float('nan')
    k=(len(a)-1)*p;i=int(k);return a[i]+(a[min(i+1,len(a)-1)]-a[i])*(k-i)
# watch-level medians per hour, then family median per hour: systematic nominal offset
for fam,rows in GEN.items():
  line=[]
  for h in range(1,13):
    ws=collections.defaultdict(lambda:([],[]))
    for r in rows:
      if int(r['hour'])!=h or r['gate'] : continue
      s,i=f(r['side_pct']),f(r['inout_pct'])
      if fin(s) and fin(i): ws[r['watch']][0].append(s);ws[r['watch']][1].append(i)
    if not ws: line.append(f'{h:2}:  --   '); continue
    S=q([q(a,.5) for a,b in ws.values()],.5);I=q([q(b,.5) for a,b in ws.values()],.5)
    line.append(f'{h:2}:{S:+.2f}/{I:+.2f}')
  print(f'{fam:6} side/inout watch-median %R  '+'  '.join(line))
# effect of re-centring per hour (subtract family per-hour median) on |pos| spread
print()
for fam,rows in GEN.items():
  for shape in ('round','baton'):
    med={}
    for h in range(1,13):
      s=[f(r['side_pct']) for r in rows if int(r['hour'])==h and r['shape']==shape and not r['gate'] and fin(f(r['side_pct']))]
      i=[f(r['inout_pct']) for r in rows if int(r['hour'])==h and r['shape']==shape and not r['gate'] and fin(f(r['inout_pct']))]
      if s: med[h]=(q(s,.5),q(i,.5))
    raw=[];cen=[]
    for r in rows:
      h=int(r['hour'])
      if r['shape']!=shape or r['gate'] or r['uncertain']: continue
      s,i=f(r['side_pct']),f(r['inout_pct'])
      if not(fin(s) and fin(i)): continue
      raw.append(math.hypot(s,i));cen.append(math.hypot(s-med[h][0],i-med[h][1]))
    print(f'{fam:6} {shape:5} clean n={len(raw):4} |pos| raw med {q(raw,.5):.3f} p99 {q(raw,.99):.3f} max {max(raw):.3f}  -> re-centred med {q(cen,.5):.3f} p99 {q(cen,.99):.3f} max {max(cen):.3f}')
