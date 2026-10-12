import csv,json,sys,shutil,collections
from pathlib import Path
work=Path(sys.argv[1]);out=Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=True)
summary={};recovered=[]
for model in ('gmt_126710','submariner_124060','submariner_126610'):
 before=list(csv.DictReader(open(work/'before'/model/'markers.csv')));after=list(csv.DictReader(open(work/'after'/model/'markers.csv')))
 keyed={(r['photo_id'],r['hour']):r for r in before};assert len(before)==len(after)
 for r in after:
  b=keyed[r['photo_id'],r['hour']]
  for field in ('outer_status','outer_rotation_deg','outer_calibrated_rotation_deg','outer_area_reason','outer_total_pct'):
   if b.get(field,''):assert r[field]==b[field],(r['photo_id'],r['hour'],field)
  if b['assessed']=='true':
   assert r['assessed']=='true'
   for field in ('total_pct','extra_pct','missing_pct','rotation_deg','dx_R','dy_R','size_pct','detected_polygon','expected_polygon'):
    if b.get(field,'') and b[field]!='NaN':assert r[field]==b[field],(r['photo_id'],r['hour'],field)
  if r['assessed']=='true':assert abs(float(r['extra_pct'])+float(r['missing_pct'])-float(r['total_pct']))<1e-9
  for prefix in ('fitted','alignment'):
   if r[prefix+'_total_pct']:assert abs(float(r[prefix+'_extra_pct'])+float(r[prefix+'_missing_pct'])-float(r[prefix+'_total_pct']))<1e-9
  if b['assessed']=='false' and (r['assessed']=='true' or r['fitted_total_pct']):recovered.append(dict(r,before_reason=b['reason']))
 shutil.copy(work/'before'/model/'markers.csv',out/f'{model}_before.csv');shutil.copy(work/'after'/model/'markers.csv',out/f'{model}_after.csv')
 summary[model]=dict(markers=len(after),before_assessed=sum(r['assessed']=='true' for r in before),after_assessed=sum(r['assessed']=='true' for r in after),fitted_assessed=sum(bool(r['fitted_total_pct']) for r in after),alignment_percentage_assessed=sum(bool(r['alignment_total_pct']) for r in after),partial_alignment_supported=sum(r['alignment_supported']=='true' for r in after),recovered=sum(r['model']==model for r in recovered),remaining_reasons=dict(collections.Counter(r['failure_code'] for r in after if r['assessed']=='false')))
 # Rerender original inputs that have recoveries, then publish only recovered marker images.
 ids={r['photo_id'] for r in recovered if r['model']==model};inputs=list(csv.DictReader(open(work/f'{model}_regression.csv')))
 with (work/f'{model}_recoveries.csv').open('w',newline='') as f:
  w=csv.writer(f);w.writerow(['photo_id','physical_watch_id','split','path'])
  for r in inputs:
   if r['photo_id'] in ids:w.writerow([r['photo_id'],r['physical_watch_id'],'recovery',r['path']])
if recovered:
 with (out/'recovered_markers.csv').open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=list(recovered[0]));w.writeheader();w.writerows(recovered)
(out/'comparison.json').write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps(summary,indent=2))
