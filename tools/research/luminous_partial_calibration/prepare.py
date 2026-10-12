"""Identity/split frozen before marker measurements; no image acquisition or score-based selection."""
import csv,hashlib,json,math,sys
from collections import Counter,defaultdict
from pathlib import Path
from urllib.parse import urlparse
from PIL import Image,ImageEnhance
ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/'tools/luminous'))
from prepare_curated import read,write

def run(work,processing=True):
 work=Path(work);protocol=json.loads((Path(__file__).parent/'protocol.json').read_text());rows=read(work/'verified_inputs.csv')
 reviewed={r['photo_id']:r for r in read(ROOT/'tools/luminous/curated_reviewed_provenance.csv')}
 assert len(rows)==len(reviewed)==132
 parent={r['physical_watch_id']:r['physical_watch_id'] for r in rows}
 def find(a):
  while parent[a]!=a:a=parent[a]
  return a
 def union(a,b):
  a,b=find(a),find(b);parent[max(a,b)]=min(a,b)
 pairs=read(Path(__file__).parent/'input_reviews/identity_shared_dial_pairs.csv')
 for p in pairs:union(p['watch_a'],p['watch_b'])
 for key in ('sha256','image_url'):
  seen={}
  for r in rows:
   value=(r['family'],r[key])
   if value in seen:union(r['physical_watch_id'],seen[value])
   seen[value]=r['physical_watch_id']
 seen={}
 for r in rows:
  assert all(r[k]==reviewed[r['photo_id']][k] for k in ('sha256','family','model','physical_watch_id','source_width','source_height'))
  rr=reviewed[r['photo_id']];r.update({k:rr[k] for k in ('model_provenance_status','url_reference_hints','baseline_model_eligible')})
  r['dealer']=urlparse(r['image_url']).hostname.lower().removeprefix('www.')
  if r['listing_sku']:
   key=r['family'],r['dealer'],r['listing_sku']
   if key in seen:union(r['physical_watch_id'],seen[key])
   seen[key]=r['physical_watch_id']
 clusters=defaultdict(list)
 for r in rows:clusters[r['family'],find(r['physical_watch_id'])].append(r)
 for (family,group),photos in clusters.items():
  conflict=any(r['baseline_model_eligible']=='false' for r in photos);shared=any(r['known_shared_dial']=='true' for r in photos)
  eligible=not(conflict or shared)
  chosen=min(photos,key=lambda r:(-min(int(r['source_width']),int(r['source_height'])),-int(r['source_width'])*int(r['source_height']),r['sha256']))
  for r in photos:
   r.update(group_id=group,primary_eligible=str(eligible).lower(),primary_exclusion='model_source_conflict' if conflict else 'preexisting_shared_image_identity_uncertain' if shared else '',primary_photo=str(r is chosen).lower(),split='identity_quarantine' if not eligible else 'pending',cv_fold='',identity_status='provisional_listing_cluster_not_serial_verified')
 strata=defaultdict(list)
 for (family,group),photos in clusters.items():
  r=next(r for r in photos if r['primary_photo']=='true')
  if r['primary_eligible']=='true':strata[family,r['dealer']].append((group,photos))
 for (family,dealer),groups in sorted(strata.items()):
  groups.sort(key=lambda item:hashlib.sha256((protocol['split_seed']+'|'+family+'|'+item[0]).encode()).hexdigest())
  n=len(groups);hold=min(n-2,math.ceil(n*protocol['heldout_fraction'])) if n>=3 else 0
  dev=[item for i,item in enumerate(groups) if i>=hold]
  folds={g:i%protocol['cross_validation_folds'] for i,(g,_) in enumerate(dev)}
  for i,(g,photos) in enumerate(groups):
   for r in photos:r['split']='heldout' if i<hold else 'development';r['cv_fold']=folds.get(g,'')
 # All originals retain an audit role; repeat photographs never enter reference/distribution twice.
 write(work/'split_manifest.csv',rows)
 for family in ('gmt_126710','submariner_124060','submariner_126610'):
  with (work/f'{family}_extract.csv').open('w',newline='') as f:
   w=csv.writer(f);w.writerow(['photo_id','group_id','split','path','cv_fold','primary_eligible','primary_photo'])
   for r in rows:
    if r['family']==family:w.writerow([r['photo_id'],r['group_id'],r['split'],r['path'],r['cv_fold'],r['primary_eligible'],r['primary_photo']])
 variants=[];sensitivity=[]
 for family in ('gmt_126710','submariner_124060','submariner_126610') if processing else ():
  selected=min([r for r in rows if r['family']==family and r['split']=='heldout' and r['primary_photo']=='true'],key=lambda r:(-min(int(r['source_width']),int(r['source_height'])),r['sha256']))
  original=Image.open(selected['path']).convert('RGB');folder=work/'temporary_processing_controls';folder.mkdir(exist_ok=True)
  for method in protocol['processing_checks']:
   if method=='identical_original_repeat':path=Path(selected['path'])
   else:
    path=folder/(selected['photo_id']+'_'+method+('.jpg' if method.startswith('jpeg') else '.png'))
    if method.startswith('jpeg'):original.save(path,quality=int(method[-2:]),subsampling=0)
    elif method.startswith('resize'):original.resize((round(original.width*5/6),round(original.height*5/6)),Image.Resampling.LANCZOS).save(path)
    else:ImageEnhance.Brightness(original).enhance(.98 if '098' in method else 1.02).save(path)
   item=dict(photo_id=selected['photo_id']+'__'+method,original_photo_id=selected['photo_id'],family=family,group_id=selected['group_id'],split='processing_control',path=str(path),processing=method,sha256=hashlib.sha256(path.read_bytes()).hexdigest(),independent_baseline_sample=False)
   variants.append(item)
  sensitivity.append(selected['photo_id'])
 write(work/'processing_manifest.csv',variants)
 for family in ('gmt_126710','submariner_124060','submariner_126610'):
  with (work/f'{family}_processing.csv').open('w',newline='') as f:
   w=csv.writer(f);w.writerow(['photo_id','group_id','split','path'])
   for r in variants:
    if r['family']==family:w.writerow([r['photo_id'],r['group_id'],r['split'],r['path']])
 summary=dict(verified_originals=132,recorded_watch_groups=len({(r['family'],r['physical_watch_id']) for r in rows}),conservative_identity_clusters=len(clusters),eligible_clusters=len({(r['family'],r['group_id']) for r in rows if r['primary_eligible']=='true'}),family_photo_counts=dict(Counter(r['family'] for r in rows)),family_split_counts={f:dict(Counter(r['split'] for r in rows if r['family']==f and r['primary_photo']=='true')) for f in sorted(set(r['family'] for r in rows))},source_counts=dict(Counter(r['family']+' / '+r['dealer'] for r in rows if r['primary_photo']=='true')),processing_originals=sensitivity,protocol=protocol)
 (work/'selection.json').write_text(json.dumps(summary,indent=2)+'\n');print(json.dumps(summary,indent=2))
if __name__=='__main__':run(sys.argv[1],'--split-only' not in sys.argv)
