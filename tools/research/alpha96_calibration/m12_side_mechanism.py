#!/usr/bin/env python3
"""RESEARCH ONLY. Does surround brightness or per-side edge position drive the 12 radial offset? Usage: m12_side_mechanism.py <M12Diag CSV>"""
import csv,sys
sys.path.insert(0,'tools/research/alpha96_calibration')
from swe_effect import spearman
from m12_sides import centroid_shift
from statistics import median
S=sys.argv[1]
cat={r['photo_id']:r for r in csv.DictReader(open('tools/research/alpha96_calibration/catalogue_provenance_strong.csv'))}
sh={r['photo_id'] for r in csv.DictReader(open('tools/research/alpha96_calibration/results/dedup/photos.csv')) if r['shared_dial']=='1'}
P=[]
for r in csv.DictReader(open(S)):
    if r['status']!='accepted' or r['photo_id'] in sh or not r['side_left_R'] or not r['m12_local_radial_px']: continue
    f=lambda k: float(r[k])
    h=cat[r['photo_id']]['image_url'].split('/')[2]
    src='SWE' if 'swiss' in h else "Bobs" if 'bobs' in h else 'Phillips' if 'phillips' in h else 'other'
    P.append(dict(src=src,sur=f('surround_minus_dial'),smb=(f('side_left_R')+f('side_right_R'))/2-f('side_base_R'),
        sides=(f('side_left_R')+f('side_right_R'))/2,base=f('side_base_R'),rad=f('m12_local_radial_px')/f('R')))
print(len(P),'photos')
def sp(ds,a,b):
    return spearman([d[a] for d in ds],[d[b] for d in ds])
for grp,ds in (('all',P),("Bobs",[d for d in P if d['src']=="Bobs"]),('Phillips',[d for d in P if d['src']=='Phillips'])):
    print(f"{grp:8s} n={len(ds):3d} surround~(sides-base) {sp(ds,'sur','smb'):+.2f} | surround~12rad {sp(ds,'sur','rad'):+.2f} | (sides-base)~12rad {sp(ds,'smb','rad'):+.2f} | base~12rad {sp(ds,'base','rad'):+.2f} | sides~12rad {sp(ds,'sides','rad'):+.2f}")
s=sorted(P,key=lambda d:d['sur']); n=len(s)
for i,lab in enumerate(('dark surround','mid','bright surround')):
    g=s[i*n//3:(i+1)*n//3]
    cnt={k:sum(1 for d in g if d['src']==k) for k in ('Bobs','Phillips','SWE','other')}
    print(f"{lab:16s} n={len(g)} surround {median(d['sur'] for d in g):+5.0f} sides {median(d['sides'] for d in g):+.4f} base {median(d['base'] for d in g):+.4f} 12rad {median(d['rad'] for d in g):+.4f} {cnt}")
print('geometry per +0.001 R: base only', '%+.5f'%centroid_shift(0,0,0.001)[0], ' both long sides', '%+.5f'%centroid_shift(0.001,0.001,0)[0])
