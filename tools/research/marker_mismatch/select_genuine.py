#!/usr/bin/env python3
"""Research: select one independent, provenance-strong photo per genuine watch, plus optional repeats.
No photos are modified. Does not change production reference sets.
"""
import argparse
import csv
from collections import Counter

def main():
    p=argparse.ArgumentParser()
    p.add_argument('catalogue');p.add_argument('out')
    p.add_argument('--watches',type=int,default=85)
    p.add_argument('--repeats',type=int,default=15)
    a=p.parse_args()
    rows=list(csv.DictReader(open(a.catalogue,newline='')))
    rows=[r for r in rows if r.get('class_label','gen')=='gen' and r.get('physical_watch_id') and r.get('sha256')]
    # SWE studio renders were excluded by the GMT reference audit for a consistent source-direction effect.
    rows=[r for r in rows if not r['physical_watch_id'].lower().startswith('swe_')]
    def priority(r):
        label=(r.get('notes','')+' '+r.get('provenance','')+' '+r.get('split','')).lower()
        return (
            0 if 'rolex_cpo' in label or 'official' in label else 1 if 'auction_house' in label else 2,
            0 if 'decision=accept' in label else 1,
            0 if 'validation' in label or 'heldout' in label else 1,
            r['photo_id'],
        )
    # Select representatives from distinct watches. Prefer accepted evidence; no source repeats count as new watches.
    per={}
    for r in rows:per.setdefault(r['physical_watch_id'],[]).append(r)
    watch_keys=sorted(per,key=lambda k:(priority(sorted(per[k],key=priority)[0]),k))
    chosen=watch_keys[:a.watches]
    output=[sorted(per[w],key=priority)[0] for w in chosen]
    # Repeat photos from the same physical watches, useful only for photo-to-photo consistency.
    repeats=0
    for w in chosen:
        group=sorted(per[w],key=priority)
        if len(group)>1 and repeats<a.repeats:
            output.append(group[1]);repeats+=1
    if not output:raise SystemExit('No genuine photos selected')
    with open(a.out,'w',newline='') as f:
        writer=csv.DictWriter(f,fieldnames=list(rows[0].keys()),extrasaction='ignore')
        writer.writeheader();writer.writerows(output)
    print('selected',len(chosen),'independent genuine watches and',repeats,'repeat photographs',flush=True)
    print('source categories',dict(Counter(r.get('provenance','') for r in output)),flush=True)
if __name__=='__main__':main()
