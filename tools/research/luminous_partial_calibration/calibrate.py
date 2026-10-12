"""Robust position-specific research geometry. Never reads held-out rows to estimate references."""
import csv,hashlib,json,math,random,statistics,sys
from collections import defaultdict
from pathlib import Path
P=Path(__file__).parent
PROTOCOL=json.loads((P/'protocol.json').read_text())
FAMILIES=('gmt_126710','submariner_124060','submariner_126610')
def read_jsonl(path):return [json.loads(s) for s in Path(path).read_text().splitlines() if s]
def area(p):return abs(sum(a[0]*b[1]-b[0]*a[1] for a,b in zip(p,p[1:]+p[:1])))/2
def centroid(p):
 z=[a[0]*b[1]-b[0]*a[1] for a,b in zip(p,p[1:]+p[:1])];den=3*sum(z)
 return [sum((a[k]+b[k])*c for a,b,c in zip(p,p[1:]+p[:1],z))/den for k in (0,1)]
def hull(points):
 ps=sorted(set(tuple(p) for p in points))
 def chain(seq):
  out=[]
  for p in seq:
   while len(out)>1 and (out[-1][0]-out[-2][0])*(p[1]-out[-2][1])-(out[-1][1]-out[-2][1])*(p[0]-out[-2][0])<=0:out.pop()
   out.append(p)
  return out
 return [list(p) for p in chain(ps)[:-1]+chain(ps[::-1])[:-1]]
def profile(p):
 c=centroid(p);rad=[]
 for i in range(64):
  a=i*math.tau/64;dx,dy=math.cos(a),math.sin(a);ts=[]
  for u,v in zip(p,p[1:]+p[:1]):
   ex,ey=v[0]-u[0],v[1]-u[1];den=dx*ey-dy*ex
   if abs(den)<1e-12:continue
   ax,ay=u[0]-c[0],u[1]-c[1];t=(ax*ey-ay*ex)/den;q=(ax*dy-ay*dx)/den
   if t>=0 and -1e-7<=q<=1+1e-7:ts.append(t)
  assert ts;rad.append(min(ts))
 return c+rad
def geometry_vector(shape,p):
 if shape=='round':
  c=centroid(p);return c+[statistics.median(math.dist(v,c) for v in p)]
 if shape=='triangle':
  tip=max(p,key=lambda v:v[1]);base=sorted([v for v in p if v is not tip],key=lambda v:v[0]);ordered=[tip]+base
 else:
  c=centroid(p);ordered=sorted(p,key=lambda v:math.atan2(v[1]-c[1],v[0]-c[0]));start=min(range(len(ordered)),key=lambda i:ordered[i][1]+ordered[i][0]);ordered=ordered[start:]+ordered[:start]
 return [v for point in ordered for v in point]
def reconstruct(shape,v):
 if shape=='round':return [[v[0]+v[2]*math.cos(i*math.tau/96),v[1]+v[2]*math.sin(i*math.tau/96)] for i in range(96)]
 return [[v[i],v[i+1]] for i in range(0,len(v),2)]
def dimensions(shape,p):
 c=centroid(p);out={'cx_local_R':c[0],'cy_local_R':c[1],'area_R2':area(p)}
 if shape=='round':out['radius_R']=statistics.median(math.dist(v,c) for v in p);return out
 v=geometry_vector(shape,p);q=reconstruct(shape,v)
 if shape=='triangle':
  tip,a,b=q;mid=[(a[k]+b[k])/2 for k in (0,1)];width=math.dist(a,b);out.update(width_R=width,height_R=math.dist(tip,mid),direction_deg=math.degrees(math.atan2(tip[1]-mid[1],tip[0]-mid[0]))-90,base_angle_deg=math.degrees(math.atan2(b[1]-a[1],b[0]-a[0])),left_side_deg=math.degrees(math.atan2(tip[1]-a[1],tip[0]-a[0])),right_side_deg=math.degrees(math.atan2(tip[1]-b[1],tip[0]-b[0])),symmetry_pct=100*sum((tip[k]-mid[k])*(b[k]-a[k]) for k in (0,1))/(width*width))
 else:
  a,b,c1,d=q;top=[(a[k]+b[k])/2 for k in (0,1)];bottom=[(c1[k]+d[k])/2 for k in (0,1)];left=math.degrees(math.atan2(d[1]-a[1],d[0]-a[0]))-90;right=math.degrees(math.atan2(c1[1]-b[1],c1[0]-b[0]))-90
  out.update(width_R=(math.dist(a,b)+math.dist(c1,d))/2,height_R=(math.dist(a,d)+math.dist(b,c1))/2,direction_deg=(left+right)/2,left_side_deg=left,right_side_deg=right,opposing_side_difference_deg=left-right,top_angle_deg=math.degrees(math.atan2(b[1]-a[1],b[0]-a[0])),bottom_angle_deg=math.degrees(math.atan2(c1[1]-d[1],c1[0]-d[0])))
 return out

def estimate(entries,shape,field):
 bygroup=defaultdict(list)
 for meta,raw in entries:
  p=(raw.get('fit') or {}).get('local_ideal') if field=='geometric' else raw.get('local_observed')
  if not p:continue
  bygroup[meta['group_id']].append(geometry_vector(shape,p) if field=='geometric' else profile(p))
 # In normal primary inputs there is one photo per cluster. Explicit watch-level
 # medians prevent accidental future multi-photo weighting.
 vectors=[[statistics.median(v[k] for v in values) for k in range(len(values[0]))] for values in bygroup.values()]
 if len(vectors)<PROTOCOL['minimum_development_groups_for_provisional_geometry']:return None,len(vectors),vectors,list(bygroup)
 v=[statistics.median(v[k] for v in vectors) for k in range(len(vectors[0]))]
 p=reconstruct(shape,v) if field=='geometric' else hull([[v[0]+v[i+2]*math.cos(i*math.tau/64),v[1]+v[i+2]*math.sin(i*math.tau/64)] for i in range(64)])
 assert area(p)>0
 return p,len(vectors),vectors,list(bygroup)

def references(raws,meta,exclude_fold=None,exclude_dealer=None):
 cells=defaultdict(list);shapes={}
 for photo in raws:
  m=meta[photo['photo_id']]
  if m['primary_eligible']!='true' or m['primary_photo']!='true' or m['split']!='development':continue
  if exclude_fold is not None and int(m['cv_fold'])==exclude_fold:continue
  if exclude_dealer is not None and m['dealer']==exclude_dealer:continue
  for marker in photo['markers']:
   cell=m['family'],str(marker['hour']);shapes[cell]=marker['shape'];cells[cell].append((m,marker))
 result={'experimental':True,'method':PROTOCOL['estimator'],'frozen_measurement_commit':PROTOCOL['frozen_commit'],'models':{f:{'positions':{}} for f in FAMILIES}}
 for (family,hour),entries in sorted(cells.items()):
  shape=shapes[family,hour];geo,gn,gv,gg=estimate(entries,shape,'geometric');obs,on,ov,og=estimate(entries,shape,'observed')
  result['models'][family]['positions'][hour]=dict(shape=shape,geometric_polygon=geo,observed_polygon=obs,geometric_groups=gn,observed_groups=on,geometric_group_ids=gg,observed_group_ids=og,status='provisional' if geo else 'insufficient_independent_development_geometry',geometric_dimensions=dimensions(shape,geo) if geo else None,physical_footprint_is_separate=True)
 return result,cells

def main(work):
 work=Path(work);meta={r['photo_id']:r for r in csv.DictReader((work/'split_manifest.csv').open())};raws=sum((read_jsonl(work/f'{f}_raw.jsonl') for f in FAMILIES),[])
 refs,cells=references(raws,meta);(work/'calibrated_references.json').write_text(json.dumps(refs,indent=2)+'\n')
 for fold in range(PROTOCOL['cross_validation_folds']):
  r,_=references(raws,meta,exclude_fold=fold);(work/f'cv_{fold}_references.json').write_text(json.dumps(r)+'\n')
 boot=[];rng=random.Random(PROTOCOL['split_seed']);source=[]
 for (family,hour),entries in sorted(cells.items()):
  shape=entries[0][1]['shape'];poly,n,vectors,groups=estimate(entries,shape,'geometric')
  if poly is None:continue
  nominal=dimensions(shape,poly);trials=defaultdict(list)
  for _ in range(PROTOCOL['bootstrap_repetitions']):
   sample=rng.choices(vectors,k=len(vectors));v=[statistics.median(s[k] for s in sample) for k in range(len(sample[0]))]
   for k,x in dimensions(shape,reconstruct(shape,v)).items():trials[k].append(x)
  for k,xs in trials.items():
   xs.sort();boot.append(dict(family=family,hour=hour,shape=shape,groups=n,parameter=k,estimate=nominal[k],bootstrap_sd=statistics.stdev(xs),bootstrap_p025=xs[int(.025*(len(xs)-1))],bootstrap_p975=xs[int(.975*(len(xs)-1))],note='Reference estimator uncertainty conditional on listing independence/source; not photo noise or manufacturing tolerance'))
  for dealer in sorted({m['dealer'] for m,_ in entries}):
   other=[e for e in entries if e[0]['dealer']!=dealer];p,nn,_,_=estimate(other,shape,'geometric')
   source.append(dict(family=family,hour=hour,excluded_dealer=dealer,remaining_groups=nn,assessable=p is not None,centre_change_R=math.dist(centroid(p),centroid(poly)) if p else '',area_change_pct=100*(area(p)/area(poly)-1) if p else '',note='Cannot identify source bias where no other source remains'))
 for name,rows in [('reference_bootstrap.csv',boot),('source_leaveout.csv',source)]:
  with (work/name).open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=list(rows[0]) if rows else ['no_supported_rows']);w.writeheader();w.writerows(rows)
 print('Reference groups:',{f:{h:v['geometric_groups'] for h,v in d['positions'].items()} for f,d in refs['models'].items()})
if __name__=='__main__':main(sys.argv[1])
