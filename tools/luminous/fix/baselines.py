import csv,sys
from pathlib import Path
work=Path(sys.argv[1]);source=Path('docs/research/luminous-experiment-2026-10-12')
for model in ('gmt_126710','submariner_124060','submariner_126610'):
 rows=[]
 for split in ('development','heldout'):rows+=list(csv.DictReader(open(source/f'{model}_{split}.csv')))
 p=work/'before'/model;p.mkdir(parents=True,exist_ok=True)
 fields=list(dict.fromkeys(k for r in rows for k in r))
 with (p/'markers.csv').open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=fields);w.writeheader();w.writerows(rows)
