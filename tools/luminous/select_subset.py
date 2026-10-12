"""Fixed bounded split; no detector result or mismatch influences source selection."""
import csv, json, sys
from pathlib import Path
work=Path(sys.argv[1]); known={r['photo_id']:r for r in csv.DictReader(open('tools/luminous/curated_reviewed_provenance.csv'))}
shared=set()
for r in csv.DictReader(open('tools/luminous/shared_pairs.csv')): shared.update([r['photo_a'],r['photo_b']])
records=list(csv.DictReader(open(work/'verified_inputs.csv'))); selected=[]
for family in ('gmt_126710','submariner_124060','submariner_126610'):
    pool=[r for r in records if r['family']==family and r['selected_for_measurement']=='true' and known[r['photo_id']]['baseline_model_eligible']=='true' and r['photo_id'] not in shared and r['known_shared_dial']=='false' and min(int(r['source_width']),int(r['source_height']))>=800]
    pool.sort(key=lambda r:r['sha256']); assert len(pool)>=12,(family,len(pool))
    # Eight recorded genuine groups for development; four disjoint for evaluation.
    chosen=pool[:16] if family=="gmt_126710" else pool[:12]
    assert len(chosen)==(16 if family=="gmt_126710" else 12)
    for i,r in enumerate(chosen): selected.append(dict(r,identity_status=known[r['photo_id']]['identity_status'],split='heldout' if 8<=i<12 else 'development'))
    for split in ('development','heldout'):
        with (work/f'{family}_{split}.csv').open('w',newline='') as f:
            w=csv.writer(f);w.writerow(['photo_id','physical_watch_id','split','path'])
            for r in selected:
                if r['family']==family and r['split']==split:w.writerow([r['photo_id'],r['physical_watch_id'],split,r['path']])
assert len({r['physical_watch_id'] for r in selected})==40
(work/'bounded_subset.json').write_text(json.dumps(selected,indent=2)+'\n')
print('Selected GMT 12 development + 4 held-out; Subs 8 development + 4 held-out each; fixed held-out identities remain disjoint and provisional')
