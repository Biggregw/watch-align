#!/usr/bin/env python3
"""RESEARCH ONLY: compare current 12 polygon-centroid lateral position with the
long-side symmetry-axis lateral position emitted by M12AxisPosition.

The genuine reference is derived independently for each measure from the same
verified, deduplicated, non-SWE physical watches. One value per watch (median of
its usable photos); leave-one-watch-out residuals are used for genuine spread.
Replica/candidate controls never enter either nominal.
"""
import argparse,csv,math,os
from collections import defaultdict
from statistics import median

def fl(x):
    try:
        v=float(x); return v if math.isfinite(v) else None
    except Exception:return None

def q(a,p):
    a=sorted(a)
    k=(len(a)-1)*p; lo=int(math.floor(k)); hi=min(lo+1,len(a)-1)
    return a[lo]+(a[hi]-a[lo])*(k-lo)

def stats(a):
    return {'n':len(a),'median':median(a),'p90':q(a,.9),'max':max(a)}

def src(url):
    u=(url or '').lower()
    if 'swisswatchexpo' in u:return 'SWE'
    if 'bobswatches' in u:return "Bob's"
    if 'phillips' in u:return 'Phillips'
    return 'other'

def read(p): return list(csv.DictReader(open(p,newline='')))

def axismap(p):
    return {r['photo_id']:r for r in read(p) if r.get('status')=='accepted'}

def watch_values(rows, amap, shared, exclude_swe=True):
    g=defaultdict(list)
    for r in rows:
        if r['photo_id'] in shared: continue
        s=src(r.get('image_url',''))
        if exclude_swe and s=='SWE': continue
        a=amap.get(r['photo_id'])
        if not a or a.get('axis_usable')!='true' or a.get('current_12_usable')!='true': continue
        c=fl(a.get('current_lateral_R')); x=fl(a.get('axis_lateral_R'))
        if c is None or x is None: continue
        g[r['physical_watch_id']].append((c,x,s))
    out=[]
    for wid,vs in sorted(g.items()):
        out.append({'watch':wid,'source':vs[0][2],
                    'current':median(v[0] for v in vs),'axis':median(v[1] for v in vs),'n':len(vs)})
    return out

def nominal(W,k): return median(w[k] for w in W)

def lowo(W,k):
    return [abs(w[k]-median(x[k] for x in W if x is not w)) for w in W]

def fmt(v,n=6): return f'{v:.{n}f}'

def control_rows(manifest, axis_csv, nom, genuine_lowo):
    meta={r['photo_id']:r for r in read(manifest)}
    out=[]
    for a in read(axis_csv):
        m=meta.get(a['photo_id'])
        if not m or a.get('axis_usable')!='true' or a.get('current_12_usable')!='true': continue
        row={'photo':a['photo_id'],'group':m.get('group',''),'class':m.get('class_label','')}
        for k,col in [('current','current_lateral_R'),('axis','axis_lateral_R')]:
            v=fl(a.get(col))
            if v is None: continue
            d=abs(v-nom[k])
            row[k]=v; row[k+'_dev']=d
            row[k+'_atleast']=sum(1 for z in genuine_lowo[k] if z>=d)
        out.append(row)
    return out

def scaled_noise(manifest, axis_csv):
    meta={r['photo_id']:r for r in read(manifest)}
    am=axismap(axis_csv); by=defaultdict(list)
    for pid,m in meta.items():
        a=am.get(pid)
        if not a or a.get('axis_usable')!='true' or a.get('current_12_usable')!='true': continue
        c=fl(a.get('current_lateral_R')); x=fl(a.get('axis_lateral_R'))
        if c is None or x is None: continue
        by[(m.get('base_photo_id',pid),float(m.get('scale') or 1.0))].append((int(m.get('jitter') or 0),c,x))
    rows=[]
    for (base,scale),vs in sorted(by.items()):
        vs=sorted(vs)
        rows.append({'base':base,'scale':scale,'n':len(vs),
                     'current_spread':max(v[1] for v in vs)-min(v[1] for v in vs),
                     'axis_spread':max(v[2] for v in vs)-min(v[2] for v in vs),
                     'current_med':median(v[1] for v in vs),'axis_med':median(v[2] for v in vs)})
    return rows

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--genuine-axis',required=True)
    ap.add_argument('--catalogue',required=True)
    ap.add_argument('--dedup',required=True)
    ap.add_argument('--controls-axis',required=True)
    ap.add_argument('--controls-manifest',required=True)
    ap.add_argument('--scaled-axis')
    ap.add_argument('--scaled-manifest')
    ap.add_argument('--out',required=True)
    a=ap.parse_args()

    shared={r['photo_id'] for r in read(a.dedup) if r.get('shared_dial')=='1'}
    W=watch_values(read(a.catalogue),axismap(a.genuine_axis),shared,True)
    if len(W)<10: raise SystemExit(f'only {len(W)} usable non-SWE genuine watches')
    noms={k:nominal(W,k) for k in ('current','axis')}
    low={k:lowo(W,k) for k in ('current','axis')}
    st={k:stats(low[k]) for k in ('current','axis')}

    sources=sorted(set(w['source'] for w in W))
    controls=control_rows(a.controls_manifest,a.controls_axis,noms,low)
    scaled=scaled_noise(a.scaled_manifest,a.scaled_axis) if a.scaled_axis and a.scaled_manifest else []

    L=['# M12 long-side symmetry-axis position experiment (research only)','',
       'No app code, master geometry, pose, thresholds or verdicts were changed. Both measures use the same usable photos and the same genuine-only calibration logic. SWE is excluded from the reference.','',
       f'Usable verified non-SWE genuine set in this run: **{len(W)} physical watches**.', '',
       '## Genuine leave-one-watch-out lateral spread (R)','',
       '| Measure | nominal signed R | median | P90 | max |','|---|---:|---:|---:|---:|']
    for k,label in [('current','Current polygon centroid'),('axis','Long-side symmetry axis')]:
        s=st[k];L.append(f"| {label} | {noms[k]:+.6f} | {s['median']:.6f} | {s['p90']:.6f} | {s['max']:.6f} |")
    L += ['', 'Smaller is better. The symmetry-axis measure ignores the base edge and equal side widening cancels by construction.','',
          '## Source medians (signed R before re-centring)','',
          '| Source | watches | current centroid | symmetry axis |','|---|---:|---:|---:|']
    for s in sources:
        ws=[w for w in W if w['source']==s]
        L.append(f"| {s} | {len(ws)} | {median(w['current'] for w in ws):+.6f} | {median(w['axis'] for w in ws):+.6f} |")

    L += ['', '## Local controls','',
          'Counts are how many genuine leave-one-watch-out values are at least as far from their own genuine reference. Controls never define the reference.','',
          '| Photo | group | current dev R | genuine as far | axis dev R | genuine as far |','|---|---|---:|---:|---:|---:|']
    for r in controls:
        L.append(f"| {r['photo']} | {r['group']} | {r.get('current_dev',float('nan')):.6f} | {r.get('current_atleast','')}/{len(W)} | "
                 f"{r.get('axis_dev',float('nan')):.6f} | {r.get('axis_atleast','')}/{len(W)} |")

    if scaled:
        L += ['', '## Resampling / resolution stability','',
              'Within-scale spread is max-minus-min across the five deterministic pixel-grid jitters. Smaller is better.','',
              '| Base photo | scale | usable | current spread R | axis spread R |','|---|---:|---:|---:|---:|']
        for r in scaled:
            L.append(f"| {r['base']} | {r['scale']:.2f} | {r['n']} | {r['current_spread']:.6f} | {r['axis_spread']:.6f} |")
        cs=[r['current_spread'] for r in scaled if r['n']>=3]; xs=[r['axis_spread'] for r in scaled if r['n']>=3]
        if cs and xs:
            L += ['', f"Median within-scale spread: current **{median(cs):.6f} R**, symmetry axis **{median(xs):.6f} R**."]

    axis_better=st['axis']['p90']<st['current']['p90'] and st['axis']['max']<st['current']['max']
    L += ['', '## Decision signal','',
          ('The symmetry-axis candidate is cleaner on both P90 and max genuine held-out spread in this run.'
           if axis_better else
           'The symmetry-axis candidate does **not** beat the current centroid on both P90 and max genuine held-out spread in this run.'),
          'This is an experiment, not a production decision.','']
    os.makedirs(os.path.dirname(a.out),exist_ok=True)
    open(a.out,'w').write('\n'.join(L))
    print('\n'.join(L))

if __name__=='__main__': main()
