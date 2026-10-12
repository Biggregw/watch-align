"""Position-level audit, independent held-out and OOF tables; no watch quality averages."""
import csv,gzip,json,math,shutil,statistics,sys
from collections import Counter,defaultdict
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent))
from calibrate import FAMILIES,PROTOCOL,read_jsonl,dimensions,area,centroid

def write(path,rows,fields=None):
 fields=fields or list(dict.fromkeys(k for r in rows for k in r)) or ['no_rows']
 with Path(path).open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=fields);w.writeheader();w.writerows(rows)
def number(v):return isinstance(v,(int,float)) and not isinstance(v,bool) and math.isfinite(v)
def stats(v):
 v=sorted(v);n=len(v)
 return dict(n=n,min=v[0] if v else '',median=statistics.median(v) if v else '',p90=v[math.ceil(.90*n)-1] if n>=20 else '',p95=v[math.ceil(.95*n)-1] if n>=40 else '',max=v[-1] if v else '',sd=statistics.stdev(v) if n>=2 else '',percentile_note='P90 >=20 groups, P95 >=40 groups; descriptive observations, not factory tolerances')
def flatten(p,m,meta):
 f=m.get('fit') or {};r={k:p.get(k,'') for k in ('photo_id','family','source_sha256','working_width','working_height','pose_valid','pose_reason','dial_radius_px','ellipse_ratio','edge_rms_px','tick_rms_px','ticks','complete_pairs','marker_branch_turn','input_evidence','outer_snapshot_sha256')}
 r.update({k:meta.get(k,'') for k in ('model','physical_watch_id','group_id','split','cv_fold','primary_eligible','primary_photo','primary_exclusion','dealer','source_url','image_url','source_width','source_height','exif_orientation','identity_status')})
 r.update({k:m.get(k,'') for k in ('hour','shape','key','observed_usable','reason','failure_code','method','boundary_spread_px','rotation_spread_deg','edge_coverage','contrast','interference_clean','interference_reason','reference_geometric_groups','reference_observed_groups','reference_status','new_dx_R','new_dy_R','new_direction_deg','old_dx_R','old_dy_R','old_direction_deg','outer_status','outer_rotation_deg','outer_calibrated_rotation_deg','outer_area_reason','outer_total_pct')})
 r['hour']=int(m['hour'])
 r.update({'fit_'+k:f.get(k,'') for k in ('supported','orientation','angle_rad','radius_R','width_R','height_R','symmetry_pct','rms_px','angle_span_deg','center_span_px','radius_span_px','cross_c_R','reason')})
 r['fit_sample_count']=len(f.get('observed') or []);r['fit_segment_count']=len(f.get('segments') or [])
 if f.get('center'):r['fit_centre_x_R'],r['fit_centre_y_R']=f['center']
 if f.get('cross_normal'):r['fit_cross_normal_x'],r['fit_cross_normal_y']=f['cross_normal']
 if f.get('local_ideal'):r.update({'local_'+k:v for k,v in dimensions(m['shape'],f['local_ideal']).items()})
 for prefix in ('old_fitted','old_alignment','old_observed','new_fitted','new_alignment','new_observed'):
  q=m.get(prefix) or {}
  for k in ('expected_area_R2','actual_area_R2','extra_pct','missing_pct','total_pct'):r[prefix+'_'+k]=q.get(k,'')
 r['assessment']='fully_usable' if all(m.get('new_'+k) for k in ('fitted','alignment','observed')) else 'partially_usable' if f.get('supported') or m.get('new_observed') else 'withheld'
 r['geometry_percentage_reason']='measured' if m.get('new_fitted') else f.get('reason','no reliable partial fit') if not f.get('ideal') else 'insufficient development reference or ring unavailable'
 r['observed_percentage_reason']='measured' if m.get('new_observed') else m.get('reason','no reliable full boundary') if not m.get('observed_usable') else 'insufficient development physical-footprint reference'
 return {k:'' if v is None else v for k,v in r.items()}
def distributions(rows,selection):
 result=[];cells=defaultdict(list)
 for r in rows:
  if r['primary_eligible']=='true' and r['primary_photo']=='true':cells[r['family'],r['hour'],r['shape']].append(r)
 fields=['new_dx_R','new_dy_R','new_direction_deg','local_cx_local_R','local_cy_local_R','local_width_R','local_height_R','local_radius_R','local_direction_deg','local_left_side_deg','local_right_side_deg','local_symmetry_pct','local_opposing_side_difference_deg']+[p+'_'+k for p in ('old_fitted','old_alignment','old_observed','new_fitted','new_alignment','new_observed') for k in ('total_pct','extra_pct','missing_pct')]
 for (family,hour,shape),sample in sorted(cells.items()):
  for split in ('development','heldout','all_conditional'):
   rs=[r for r in sample if split=='all_conditional' or r['split']==split]
   for field in fields:
    if shape=='round' and any(k in field for k in ('direction','side','symmetry','width','height')):continue
    if shape!='round' and 'radius' in field:continue
    if shape!='triangle' and 'symmetry' in field:continue
    if shape!='baton' and 'opposing_side_difference' in field:continue
    values=[r[field] for r in rs if number(r.get(field))]
    result.append(dict(family=family,hour=hour,shape=shape,partition=split,metric=field,attempted_groups=len({r['group_id'] for r in rs}),successfully_measured_groups=len(values),withheld_groups=len(rs)-len(values),**stats(values)))
 return result

def main(work,out):
 work=Path(work);out=Path(out);out.mkdir(parents=True,exist_ok=True);meta={r['photo_id']:r for r in csv.DictReader((work/'split_manifest.csv').open())};scored=sum((read_jsonl(work/f'{f}_scored.jsonl') for f in FAMILIES),[])
 rows=[flatten(p,m,meta[p['photo_id']]) for p in scored for m in p['markers']];write(out/'per_marker.csv',rows)
 # Exact canonical polygons and coloured regions are preserved losslessly, separate
 # from the compact scalar CSV. No source photographs are included.
 with gzip.open(out/'exact_geometry.jsonl.gz','wt',encoding='utf8') as f:
  for p in scored:
   copy=dict(p);copy['markers']=[{k:v for k,v in m.items() if k!='diagnostics'} for m in p['markers']];f.write(json.dumps(copy,separators=(',',':'))+'\n')
 write(out/'genuine_variation_by_position.csv',distributions(rows,None))
 withheld=[r for r in rows if r['assessment']!='fully_usable'];write(out/'withheld_audit.csv',withheld)
 reasons=Counter((r['family'],r['hour'],r['split'],r['primary_eligible'],r['geometry_percentage_reason'],r['observed_percentage_reason']) for r in withheld)
 write(out/'withheld_counts.csv',[dict(family=f,hour=h,split=s,primary_eligible=e,geometry_reason=g,physical_reason=p,markers=n) for (f,h,s,e,g,p),n in sorted(reasons.items())])
 # One row per primary cluster/position; repeat photographs are audit and noise evidence only.
 primary=[r for r in rows if r['primary_photo']=='true'];write(out/'physical_watch_marker_summary.csv',primary)
 manifest=list(meta.values());write(out/'development_heldout_provenance.csv',[{k:v for k,v in r.items() if k!='path'} for r in manifest])
 for name in ('calibrated_references.json','selection.json','reference_bootstrap.csv','source_leaveout.csv'):shutil.copy(work/name,out/name)
 # Development-grouped CV does not touch held-out groups or tune estimator settings.
 cv=[]
 for fold in range(PROTOCOL['cross_validation_folds']):
  for family in FAMILIES:
   for p in read_jsonl(work/f'{family}_cv_{fold}.jsonl'):
    mm=meta[p['photo_id']]
    if mm['primary_eligible']!='true' or mm['primary_photo']!='true' or mm['split']!='development' or int(mm['cv_fold'])!=fold:continue
    for marker in p['markers']:r=flatten(p,marker,mm);r['split']='development_OOF';cv.append(r)
 write(out/'development_grouped_oof.csv',cv)
 ooftables=distributions(cv,None)
 for r in ooftables:r['partition']='development_OOF' if r['partition']=='all_conditional' else r['partition']
 write(out/'development_oof_distributions.csv',[r for r in ooftables if r['partition']=='development_OOF'])
 # Previously recorded repeat photos of the same cluster give limited real photo-to-photo evidence.
 keyed={(r['group_id'],r['hour']):r for r in primary};repeat=[]
 for r in rows:
  if r['primary_photo']=='true':continue
  b=keyed[r['group_id'],r['hour']]
  item={k:r[k] for k in ('family','group_id','photo_id','hour','shape','primary_eligible','source_sha256')};item['primary_photo_id']=b['photo_id']
  for k in ('new_dx_R','new_dy_R','new_direction_deg','local_radius_R','local_width_R','local_height_R','new_fitted_total_pct','new_alignment_total_pct','new_observed_total_pct'):
   item[k+'_change']=r[k]-b[k] if number(r.get(k)) and number(b.get(k)) else ''
  item['note']='Repeated original of recorded watch' if r['physical_watch_id']==b['physical_watch_id'] else 'Shared-image identity hypothesis; not proven repeat photography of one physical watch'
  repeat.append(item)
 write(out/'same_cluster_repeat_photos.csv',repeat)
 # Controlled processing is never added to the independent watch distribution.
 processing_meta={r['photo_id']:r for r in csv.DictReader((work/'processing_manifest.csv').open())};native={(r['photo_id'],r['hour']):r for r in rows};processing=[]
 for family in FAMILIES:
  for p in read_jsonl(work/f'{family}_processing_scored.jsonl'):
   pm=processing_meta[p['photo_id']];orig=meta[pm['original_photo_id']];fake=dict(orig,split='processing_control',primary_photo='false')
   for marker in p['markers']:
    r=flatten(p,marker,fake);b=native[pm['original_photo_id'],marker['hour']];r['processing']=pm['processing'];r['original_photo_id']=pm['original_photo_id']
    for k in ('new_dx_R','new_dy_R','new_direction_deg','local_radius_R','new_fitted_total_pct','new_alignment_total_pct','new_observed_total_pct'):
     r[k+'_change']=r[k]-b[k] if number(r.get(k)) and number(b.get(k)) else ''
    if pm['processing']=='identical_original_repeat':
     assert p['source_sha256']==orig['sha256']
     original=next(q for q in scored if q['photo_id']==pm['original_photo_id'])
     assert p['input_evidence']==original['input_evidence'];assert p['H']==original['H'];assert p['ring']==original['ring'];assert p['markers']==original['markers'],p['photo_id']
    processing.append(r)
 write(out/'processing_sensitivity.csv',processing)
 # Triangle systematic differences, stratified source and native resolution; no causal claim.
 triangle=[]
 for family in FAMILIES:
  sample=[r for r in primary if r['family']==family and r['primary_eligible']=='true' and r['shape']=='triangle']
  sources=sorted({r['dealer'] for r in sample})
  for split in ('development','heldout','all_conditional'):
   for dealer in ['all_sources']+sources:
    rs=[r for r in sample if (split=='all_conditional' or r['split']==split) and (dealer=='all_sources' or r['dealer']==dealer)]
    item=dict(family=family,partition=split,dealer=dealer,attempted=len(rs))
    for k in ('old_dx_R','old_dy_R','old_fitted_total_pct','old_alignment_total_pct','new_dx_R','new_dy_R','new_fitted_total_pct','new_alignment_total_pct','local_width_R','local_height_R','local_symmetry_pct'):
     vs=[r[k] for r in rs if number(r.get(k))];item[k+'_n']=len(vs);item[k+'_median']=statistics.median(vs) if vs else ''
    triangle.append(item)
 write(out/'triangle_reference_bias.csv',triangle)
 resolution=[]
 for family in FAMILIES:
  eligible=[p for p in scored if meta[p['photo_id']]['primary_photo']=='true' and meta[p['photo_id']]['primary_eligible']=='true' and p.get('dial_radius_px')];order=sorted(eligible,key=lambda p:(p['dial_radius_px'],p['photo_id']));bands={p['photo_id']:min(3,i*4//len(order))+1 for i,p in enumerate(order)}
  for hour in sorted({r['hour'] for r in primary if r['family']==family}):
   for band in range(1,5):
    rs=[r for r in primary if r['family']==family and r['hour']==hour and bands.get(r['photo_id'])==band];vs=[r['new_fitted_total_pct'] for r in rs if number(r['new_fitted_total_pct'])]
    resolution.append(dict(family=family,hour=hour,native_radius_rank_quartile=band,attempts=len(rs),measured=len(vs),median_fitted_pct=statistics.median(vs) if vs else '',radius_min=min((r['dial_radius_px'] for r in rs),default=''),radius_max=max((r['dial_radius_px'] for r in rs),default=''),note='Source/watch confounded association; processing_sensitivity.csv is controlled'))
 write(out/'resolution_associations.csv',resolution)
 # Extremes are selected after freezing, never removed or used to tune geometry.
 outliers=[];chosen=set()
 for family in FAMILIES:
  for shape in ('triangle','baton','round'):
   subset=[r for r in primary if r['family']==family and r['shape']==shape and r['primary_eligible']=='true']
   held=[r for r in subset if r['split']=='heldout'];pool=held or subset
   for metric in ('new_fitted_total_pct','new_alignment_total_pct','new_observed_total_pct'):
    available=sorted([r for r in pool if number(r[metric])],key=lambda r:(-r[metric],r['photo_id'],r['hour']))[:2 if metric=='new_fitted_total_pct' else 1]
    for r in available:
     key=r['photo_id'],r['hour']
     if key in chosen:continue
     chosen.add(key);outliers.append(dict(r,selection_metric=metric,selection_note='Highest held-out score' if held else 'No held-out score for shape; descriptive development example',image=f"gallery/{r['photo_id']}_{r['hour']}.png",visual_review='pending'))
   for metric in ('new_fitted_total_pct','new_alignment_total_pct','new_observed_total_pct'):
    available=[r for r in subset if number(r[metric])]
    if available:
     r=max(available,key=lambda r:r[metric]);key=r['photo_id'],r['hour']
     if key not in chosen:
      chosen.add(key);outliers.append(dict(r,selection_metric=metric,selection_note='Largest across all eligible primary groups; training/held-out partition retained',image=f"gallery/{r['photo_id']}_{r['hour']}.png",visual_review='pending'))
   unassessed=[r for r in pool if r['assessment']=='withheld']
   if unassessed:
    r=min(unassessed,key=lambda r:(r['photo_id'],r['hour']));key=r['photo_id'],r['hour']
    if key not in chosen:chosen.add(key);outliers.append(dict(r,selection_metric='withheld_example',selection_note='Withheld evidence retained',image=f"gallery/{r['photo_id']}_{r['hour']}.png",visual_review='pending'))
 processing_images=[]
 for family in FAMILIES:
  candidates=[r for r in processing if r['family']==family and number(r.get('new_fitted_total_pct_change')) and r['processing']!='identical_original_repeat']
  if not candidates:continue
  r=max(candidates,key=lambda r:abs(r['new_fitted_total_pct_change']));base=native[r['original_photo_id'],r['hour']]
  processing_images.append(dict(family=family,photo_id=r['photo_id'],original_photo_id=r['original_photo_id'],hour=r['hour'],processing=r['processing'],fitted_change_pp=r['new_fitted_total_pct_change'],image=f"processing_gallery/{r['photo_id']}_{r['hour']}.png",baseline_image=f"processing_gallery/{base['photo_id']}_{r['hour']}.png"))
 write(out/'processing_image_index.csv',processing_images)
 for family in FAMILIES:
  selections=[r for r in processing_images if r['family']==family]
  (work/f'{family}_processing_gallery_select.txt').write_text('\n'.join(f"{r['photo_id']},{r['hour']}" for r in selections)+'\n')
  (work/f'{family}_processing_baseline_select.txt').write_text('\n'.join(f"{r['original_photo_id']},{r['hour']}" for r in selections)+'\n')
 write(out/'outlier_index.csv',outliers)
 for family in FAMILIES:(work/f'{family}_gallery_select.txt').write_text('\n'.join(f"{r['photo_id']},{r['hour']}" for r in outliers if r['family']==family)+'\n')
 summary={'actions_run':'https://github.com/Biggregw/watch-align/actions/runs/'+__import__('os').environ.get('GITHUB_RUN_ID','local'),'frozen_measurement_commit':PROTOCOL['frozen_commit'],'marker_rows_all_originals':len(rows),'primary_marker_rows':len(primary),'eligible_marker_rows':sum(r['primary_eligible']=='true' for r in primary),'by_family':{},'outlier_images':len(outliers),'phone_input_identity':'unverified; attachment 37891.jpg vs user text 37991.jpg; do not infer identical-pixel failure','limitations':['Groups are provisional listing/identity clusters, not serial-authenticated independent watches.','Held-out is for new reference estimation; this collection previously informed detector development.','126610 entirely Bobs Watches: no independent source validation.','Frozen gate clearance and low residual do not prove the correct physical luminous edge.','Processing changes and source/lighting can alter geometry; observed variation is not factory tolerance.','No independent metrology or human replica-acceptance labels support thresholds.']}
 for family in FAMILIES:
  rr=[r for r in primary if r['family']==family and r['primary_eligible']=='true']
  summary['by_family'][family]=dict(eligible_clusters=len({r['group_id'] for r in rr}),groups_with_fitted_geometry=len({r['group_id'] for r in rr if number(r['new_fitted_total_pct'])}),groups_with_supported_alignment=len({r['group_id'] for r in rr if r['fit_supported']}),fitted_markers=sum(number(r['new_fitted_total_pct']) for r in rr),observed_markers=sum(number(r['new_observed_total_pct']) for r in rr),heldout_groups=len({r['group_id'] for r in rr if r['split']=='heldout'}),heldout_fitted_markers=sum(number(r['new_fitted_total_pct']) and r['split']=='heldout' for r in rr))
 (out/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');print(json.dumps(summary,indent=2))
 # Initial factual report; human visual evidence notes are added separately, never fed back to calibration.
 report=['# Frozen partial-edge genuine calibration','',f"Measurement: `{PROTOCOL['frozen_commit']}`. [Actions]({summary['actions_run']}). Research only; Android, both existing reference collections, production QC and PR #52 unchanged.",'','## Independent input and leakage audit','',f"132 verified originals / 130 recorded listing groups / 128 conservative identity clusters. 123 clusters are provenance/identity eligible before any new measurement. Two source-conflicting GMT listings and all pre-existing shared-image identity suspects are quarantined. All originals, repeats, quarantines and withheld markers remain in the audit. Development/held-out split is dealer-stratified and hash-deterministic before measurement. No test image or replica enters the reference estimator.",'','| Family | Eligible provisional clusters | Clusters with fitted geometry | Fitted markers | Observed physical markers | Held-out clusters | Held-out fitted markers |','|---|---:|---:|---:|---:|---:|---:|']
 for family,s in summary['by_family'].items():report.append('| '+family+' | '+' | '.join(str(s[k]) for k in ('eligible_clusters','groups_with_fitted_geometry','fitted_markers','observed_markers','heldout_groups','heldout_fitted_markers'))+' |')
 report+=['','## Reference definitions','', 'Each individual model/hour has its own development-only reference. There is no pooled round reference or borrowed marker-position tolerance. Triangle vertices are independent three-line intersections; baton quadrilaterals retain independently supported skew and nonparallel edges; circles retain free fitted centre/radius. Ring adjustment is removed with the frozen inverse transformation before watch-weighted coordinate medians, then restored exactly for scoring. Minimum three development clusters only permits an experimental geometry; it does not establish a distribution or tolerance. Missing geometry stays absent.','', 'Observed physical luminous footprints have a separate development-only median radial-profile reference from full-boundary detections. Ideal fitted corners are never substituted for observed boundaries. A/B use the fitted genuine polygon; C uses its own genuine physical-footprint polygon. Every score uses `100*(extra+missing)/filled expected area` and retains its exact expected/actual polygons and regions. A, B and C are separate and never added.','', '## Held-out per-position fitted results','', '| Family | Hour / shape | Held-out measured / attempted | Old median fitted % | New median fitted % | New median alignment % | Largest new fitted % |','|---|---|---|---:|---:|---:|---:|']
 fmt=lambda x:f'{x:.6f}' if number(x) else '—'
 for family in FAMILIES:
  for hour in sorted({r['hour'] for r in primary if r['family']==family}):
   ss=[r for r in primary if r['family']==family and r['hour']==hour and r['primary_eligible']=='true' and r['split']=='heldout'];gv=[r['new_fitted_total_pct'] for r in ss if number(r['new_fitted_total_pct'])];ov=[r['old_fitted_total_pct'] for r in ss if number(r['old_fitted_total_pct'])];av=[r['new_alignment_total_pct'] for r in ss if number(r['new_alignment_total_pct'])]
   report.append(f"| {family} | {hour} / {ss[0]['shape'] if ss else '—'} | {len(gv)} / {len(ss)} | {fmt(statistics.median(ov) if ov else None)} | {fmt(statistics.median(gv) if gv else None)} | {fmt(statistics.median(av) if av else None)} | {fmt(max(gv) if gv else None)} |")
 report+=['','Read `genuine_variation_by_position.csv` for separate development, held-out and conditional combined distributions, including extra/missing, displacement, direction, size, symmetry and withheld counts. Tail percentiles require >=20/40 provisional groups; absence of a percentile is intentional. Combined and training distributions are descriptive; held-out and development OOF test reference generalisation.','', '## Stability and photographic uncertainty','', '250 group bootstraps describe reference-estimation variation; five development-grouped folds describe out-of-fold stability without selecting estimator parameters. `source_leaveout.csv` removes each development dealer in turn where other groups support the same position. It cannot assess source bias for the all-Bobs 126610 sample. `same_cluster_repeat_photos.csv` contains two genuine repeated EWC photographs plus known shared-image hypotheses labelled separately. `processing_sensitivity.csv` reruns three preselected held-out originals under exact repeat, JPEG 95/85, 5/6 resize and brightness ±2%, with no change to original bytes. Their transformed copies are never independent watches. Exact-original repeats assert identical input fingerprints, pose, ring, measured polygons and scores.','', '## Outlier inspection','', 'The gallery preserves largest held-out mismatches by model/shape and comparison, plus withheld examples. Each image includes native original crop (presentation scaling), the frozen rectified crop, exact fitted/alignment/observed polygons, orange extra and magenta missing, displacement, rotation and fit diagnostics. Source links/hashes remain in the manifest and index. A high score is not automatically a manufacturing defect; polished-edge choice, lighting, source processing and perspective can still dominate.','']
 for r in outliers:report+= [f"### {r['family']} {r['hour']} — {r['photo_id']}",f"[Original source]({(r['source_url'] if r['source_url'].startswith('http') else r['image_url'])}) · {r['selection_note']} · {r['selection_metric']}",f"![Exact comparison]({r['image']})",'']
 report+=['## Limits and recommendation before visual review','',*['- '+x for x in summary['limitations']],'','References are provisional research outputs, not an Android update, factory tolerance, authentication verdict or replica acceptance band. Review the worst genuine fits before deciding whether the current edge evidence is sufficient for future production integration.','', '## LESSONS LEARNED','', 'Matching the geometry-estimation definition removes a known source of systematic comparison error; it cannot make photographic uncertainty disappear. A source-biased, single-photo collection cannot independently identify physical manufacturing spread. Per-position withholding and frozen held-out evaluation are necessary even when the same-method median fits better.']
 (out/'REPORT.md').write_text('\n'.join(report)+'\n')
if __name__=='__main__':main(*sys.argv[1:3])
