"""Supported independent parameters and noise summaries, without constructing missing geometry."""
import csv,gzip,json,math,statistics,sys
from collections import defaultdict
from pathlib import Path
sys.path.insert(0,str(Path(__file__).parent))
from analyze import write,stats
from calibrate import PROTOCOL,centroid
ROOT=Path(__file__).resolve().parents[3]
def wrap(x):
 while x>90:x-=180
 while x<=-90:x+=180
 return x

def run(out):
 out=Path(out);meta={r['photo_id']:r for r in csv.DictReader((out/'development_heldout_provenance.csv').open())};models={f:{m['hour']:m for m in json.loads((ROOT/f'android/app/src/main/assets/models/{f}/model.json').read_text())['markers']} for f in ('gmt_126710','submariner_124060','submariner_126610')}
 measurements=[]
 with gzip.open(out/'exact_geometry.jsonl.gz','rt') as file:
  for line in file:
   p=json.loads(line);pm=meta[p['photo_id']];ring=p.get('ring')
   if not ring:continue
   k,s=1+ring[2],ring[3];scale=math.hypot(k,s);phi=math.atan2(s,k)
   for m in p['markers']:
    f=m.get('fit') or {};hour=int(m['hour']);spec=models[p['family']][hour];a=math.radians(hour*30);co,si=math.cos(a),math.sin(a);r=spec.get('area_centroid_r',spec.get('centre_r'));mc=[math.sin(a)*r,-math.cos(a)*r] if hour!=12 else [0,-r]
    def local(pt):
     x,y=pt[0]-ring[0],pt[1]-ring[1];x,y=(k*x+s*y)/(scale*scale)-mc[0],(-s*x+k*y)/(scale*scale)-mc[1];return [co*x+si*y,-si*x+co*y]
    values={}
    if f.get('orientation') and f.get('angle_rad') is not None:
     parameter='side_direction_from_radial_deg' if m['shape']=='triangle' and not f.get('ideal') else 'single_long_edge_direction_deg' if m['shape']=='baton' and 'one long edge' in f.get('reason','') else 'mean_long_edge_direction_deg' if m['shape']=='baton' else 'direction_from_radial_deg'
     values[parameter]=wrap(math.degrees(f['angle_rad']-a-phi)-90)
    if f.get('tip'):
     t=local(f['tip']);values['tip_u_R'],values['tip_v_R']=t
    if f.get('center'):
     c=local(f['center']);values['centre_u_R'],values['centre_v_R']=c
    for name in ('radius','width','height'):
     if f.get(name+'_R') is not None:values[name+'_R']=f[name+'_R']/scale
    if f.get('symmetry_pct') is not None:values['symmetry_pct']=f['symmetry_pct']
    if f.get('cross_c_R') is not None and f.get('cross_normal'):
     nx,ny=f['cross_normal'];ax,ay=k*mc[0]-s*mc[1]+ring[0],s*mc[0]+k*mc[1]+ring[1];normal_u=(k*co-s*si)*nx+(s*co+k*si)*ny;sign=1 if normal_u>=0 else -1
     values['transverse_centreline_offset_R']=sign*(f['cross_c_R']-nx*ax-ny*ay)/scale
    side_values=defaultdict(list)
    for seg in f.get('segments') or []:
     x,y=local(seg[0]),local(seg[1]);angle=wrap(math.degrees(math.atan2(y[1]-x[1],y[0]-x[0])))
     if m['shape']=='triangle':
      role=min([('base_angle_deg',0),('left_side_deg',60),('right_side_deg',-60)],key=lambda item:abs(wrap(angle-item[1])))
      side_values[role[0]].append(angle)
     elif m['shape']=='baton':
      side_values['long_side_angle_deg' if abs(angle)>45 else 'end_angle_deg'].append(wrap(angle-90) if abs(angle)>45 else angle)
    for name,xs in side_values.items():
     xs.sort()
     for i,x in enumerate(xs):values[name+'_'+str(i+1)]=x
    for parameter,value in values.items():measurements.append(dict(photo_id=p['photo_id'],family=p['family'],hour=hour,shape=m['shape'],group_id=pm['group_id'],split=pm['split'],primary_eligible=pm['primary_eligible'],primary_photo=pm['primary_photo'],parameter=parameter,value=value,fit_reason=f.get('reason'),fit_rms_px=f.get('rms_px')))
 write(out/'supported_parameter_measurements.csv',measurements)
 cells=defaultdict(list)
 for r in measurements:
  if r['primary_eligible']=='true' and r['primary_photo']=='true':cells[r['family'],r['hour'],r['shape'],r['parameter']].append(r)
 ref={'experimental':True,'method':'Development watch-cluster medians of independently identifiable parameters; NEVER combine partial dimensions into an unsupported polygon','coordinates':'Hour-upright R-normalised offset from frozen ModelSpec.masterPoint; frozen marker-ring correction removed','positions':{}};tables=[]
 for (family,hour,shape,parameter),rs in sorted(cells.items()):
  dev=[r for r in rs if r['split']=='development'];bygroup=defaultdict(list)
  for r in dev:bygroup[r['group_id']].append(r['value'])
  values=[statistics.median(v) for v in bygroup.values()];nominal=statistics.median(values) if len(values)>=3 else None
  ref['positions'].setdefault(family,{}).setdefault(str(hour),{})[parameter]=dict(value=nominal,development_groups=len(values),status='provisional_parameter' if nominal is not None else 'insufficient_evidence',group_ids=sorted(bygroup))
  for split in ('development','heldout','all_conditional'):
   ss=[r for r in rs if split=='all_conditional' or r['split']==split];v=[r['value'] for r in ss]
   tables.append(dict(family=family,hour=hour,shape=shape,parameter=parameter,partition=split,reference=nominal,**stats(v),median_deviation=statistics.median(v)-nominal if v and nominal is not None else ''))
 (out/'supported_parameter_references.json').write_text(json.dumps(ref,indent=2)+'\n');write(out/'supported_parameter_variation.csv',tables)
 refs=json.loads((out/'calibrated_references.json').read_text());canonical=[]
 for family,model in refs['models'].items():
  for hour,entry in model['positions'].items():
   h=int(hour);spec=models[family][h];r=spec.get('area_centroid_r',spec.get('centre_r'));a=math.radians(h*30);mc=[math.sin(a)*r,-math.cos(a)*r] if h!=12 else [0,-r];poly=entry.get('geometric_polygon')
   if not poly:continue
   c=centroid(poly);x,y=mc[0]+math.cos(a)*c[0]-math.sin(a)*c[1],mc[1]+math.sin(a)*c[0]+math.cos(a)*c[1]
   canonical.append(dict(family=family,hour=h,shape=entry['shape'],groups=entry['geometric_groups'],centre_x_dial_R=x,centre_y_dial_R=y,**entry['geometric_dimensions']))
 write(out/'reference_geometry_dimensions.csv',canonical)
 processing=list(csv.DictReader((out/'processing_sensitivity.csv').open()));proc=[]
 for family in sorted(set(r['family'] for r in processing)):
  rr=[r for r in processing if r['family']==family];source=rr[0]['original_photo_id']
  for metric in ('new_fitted_total_pct','new_alignment_total_pct','new_observed_total_pct','new_direction_deg','new_dx_R','new_dy_R'):
   values=[r for r in rr if r.get(metric+'_change')]
   worst=max(values,key=lambda r:abs(float(r[metric+'_change']))) if values else None
   proc.append(dict(family=family,original_photo_id=source,metric=metric,paired_marker_variants=len(values),maximum_abs_change=float(worst[metric+'_change']) if worst else '',marker_hour=worst['hour'] if worst else '',processing=worst['processing'] if worst else '',note='Percent changes are percentage points; R offsets are fractions of radius. Three preselected originals only, not a calibrated noise envelope.'))
 write(out/'processing_sensitivity_summary.csv',proc)
 # Correct statistical applicability: round rotation and non-round radius are N/A, not withheld.
 def applicable(shape,metric):
  if shape=='round' and any(k in metric for k in ('direction','side','symmetry','width','height')):return False
  if shape!='round' and 'radius' in metric:return False
  if shape!='triangle' and 'symmetry' in metric:return False
  if shape!='baton' and 'opposing_side_difference' in metric:return False
  return True
 for name in ('genuine_variation_by_position.csv','development_oof_distributions.csv'):
  rows=list(csv.DictReader((out/name).open()));write(out/name,[r for r in rows if applicable(r['shape'],r['metric'])])
 print('Supported parameter baselines, canonical centres and processing summaries published; reference polygons unchanged')
if __name__=='__main__':run(sys.argv[1])
