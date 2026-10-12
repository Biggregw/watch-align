"""Reuse EXACTLY the Alpha107 forty-photo subset; no reference derivation or source acquisition."""
import csv,json,sys
from pathlib import Path
work=Path(sys.argv[1]);snapshot=json.loads(Path('docs/research/luminous-experiment-2026-10-12/subset-provenance.json').read_text());verified={r['sha256']:r for r in csv.DictReader(open(work/'verified_inputs.csv'))}
assert len(snapshot)==40
for model in ('gmt_126710','submariner_124060','submariner_126610'):
 with (work/f'{model}_regression.csv').open('w',newline='') as f:
  w=csv.writer(f);w.writerow(['photo_id','physical_watch_id','split','path'])
  for r in snapshot:
   if r['family']!=model:continue
   v=verified[r['sha256']];assert r['physical_watch_id']==v['physical_watch_id'] and r['model']==v['model']
   w.writerow([r['photo_id'],r['physical_watch_id'],r['split'],v['path']])
