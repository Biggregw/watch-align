import csv,json,sys,shutil,collections
from pathlib import Path
work=Path(sys.argv[1]);out=Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=True);subset=json.loads((work/'bounded_subset.json').read_text());
# Local absolute runner paths are not source identifiers.
for r in subset:r.pop('path',None)
(out/'subset-provenance.json').write_text(json.dumps(subset,indent=2)+'\n');shutil.copy(work/'input_verification.json',out/'hash-verification.json')
text=['# Experimental inner luminous-marker validation','', 'Frozen outer implementation: PR52 `593966433079d04816e98f78da5b66abd1f4553d`. Eight development and four disjoint held-out recorded genuine groups per family. Listing identity remains provisional. No manufactured tolerance or QC thresholds. Actual seed-connected inner contours, no metal inset. Local contrast levels and closed separator must agree; uncertain boundaries withheld.','', '| Model | Reference key/group count | Held-out assessed / total |','|---|---|---:|']
for model in ('gmt_126710','submariner_124060','submariner_126610'):
    ref=json.loads(Path(f'android/app/src/main/assets/experimental/luminous/{model}.json').read_text());rows=list(csv.DictReader(open(work/model/'heldout'/'markers.csv')))
    assessed=[r for r in rows if r['assessed']=='true'];assert assessed,model
    for r in assessed:
        assert abs(float(r['extra_pct'])+float(r['missing_pct'])-float(r['total_pct']))<1e-9
    text.append(f"| {model} | "+', '.join(f"{k}: {v['recorded_groups']}" for k,v in ref['shapes'].items())+f" | {len(assessed)} / {len(rows)} |")
    shutil.copy(work/model/'heldout'/'markers.csv',out/f'{model}_heldout.csv');shutil.copy(work/model/'development'/'markers.csv',out/f'{model}_development.csv')
    text+=['',f'## {model} held-out close-ups','']
    for image in sorted((work/model/'heldout').glob('*.png')):shutil.copy(image,out/image.name);text.append(f'![Original rectified crop and exact scoring polygons]({image.name})')
text+=['','Orange extra + magenta missing use the exact score polygons. Geometric area is continuous; the boundary stability diagnostic is not a statistical confidence interval. Triangle/ring placement uses the existing frozen pose and ring. Round rotation is not observable. Area-moment orientation is experimental. Failed edge/reference measurements show no percentage.','', 'No original photographs are committed. Source hashes/URLs and provisional watch grouping are in subset-provenance.json. Twelve held-out photographs are a bounded functional test, not a complete manufacturing baseline. Phone rendering and acceptance require human review.']
(out/'README.md').write_text('\n'.join(text)+'\n')
