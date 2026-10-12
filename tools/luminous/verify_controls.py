"""Check bounded recorded original/repeatability regressions; no source image harvesting."""
import csv, math
from pathlib import Path
rows=list(csv.DictReader(open('docs/research/luminous-experiment-2026-10-12/replica_repeatability_and_pepsi_control.csv')))
nine={r['photo_id']:r for r in rows if r['hour']=='9'}
original=nine['batgirl_37828'];repeat=nine['batgirl_repeat'];pepsi=nine['pepsi_3cf5442a']
assert abs(float(original['outer_calibrated_rotation_deg'])+1.1746053217373582)<1e-9
assert original['outer_status']=='WORTH' and original['outer_total_pct']==''
assert abs(float(pepsi['outer_total_pct'])-2.4212784736240094)<1e-9
assert pepsi['outer_status']=='WITHIN' and pepsi['assessed']=='false'
for key in ('detected_polygon','expected_polygon','total_pct','rotation_deg','outer_status','outer_calibrated_rotation_deg'):
    assert original[key]==repeat[key],key
for r in rows:
    if r['assessed']=='true':
        assert abs(float(r['extra_pct'])+float(r['missing_pct'])-float(r['total_pct']))<1e-9
        assert r['expected_polygon'] and r['detected_polygon']
    else: assert r['total_pct']==''
print('Original/repeat controls, unchanged Pepsi/Batgirl outer results and filled-template consistency passed')
