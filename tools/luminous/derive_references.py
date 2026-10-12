"""Own inner reference contours, median profiles with equal contribution per genuine watch.
At least three distinct recorded genuine groups per key; no outer inset or QC limit.
"""
import csv,json,math,statistics,sys
from pathlib import Path
work=Path(sys.argv[1]); dest=Path('android/app/src/main/assets/experimental/luminous');dest.mkdir(parents=True,exist_ok=True)
subset=json.loads((work/'bounded_subset.json').read_text());metadata={r['photo_id']:r for r in subset}
def hull(points):
    p=sorted(set(map(tuple,points)))
    def cross(a,b,c):return (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0])
    def chain(ps):
        out=[]
        for v in ps:
            while len(out)>1 and cross(out[-2],out[-1],v)<=0:out.pop()
            out.append(v)
        return out
    return chain(p)[:-1]+chain(p[::-1])[:-1]
def centroid(p):
    z=[a[0]*b[1]-b[0]*a[1] for a,b in zip(p,p[1:]+p[:1])];den=3*sum(z)
    return [sum((a[k]+b[k])*c for a,b,c in zip(p,p[1:]+p[:1],z))/den for k in (0,1)]
def profile(p):
    c=centroid(p); result=[]
    for k in range(64):
        a=k*math.tau/64;dx,dy=math.cos(a),math.sin(a);ts=[]
        for u,v in zip(p,p[1:]+p[:1]):
            ex,ey=v[0]-u[0],v[1]-u[1];den=dx*ey-dy*ex
            if abs(den)<1e-12:continue
            ax,ay=u[0]-c[0],u[1]-c[1];t=(ax*ey-ay*ex)/den;q=(ax*dy-ay*dx)/den
            if t>=0 and -.000001<=q<=1.000001:ts.append(t)
        assert ts;result.append(min(ts))
    return c+result
for family in ('gmt_126710','submariner_124060','submariner_126610'):
    rows=list(csv.DictReader(open(work/family/'development'/'markers.csv')));bykey={}
    for r in rows:
        assert r['split']=='development'
        if r['assessed']=='true':bykey.setdefault(r['key'],{}).setdefault(r['physical_watch_id'],[]).append((r['photo_id'],profile(json.loads(r['local_polygon']))))
    shapes={}
    for key,watches in bykey.items():
        if len(watches)<3:continue
        watch_profiles=[[statistics.median(p[1][k] for p in values) for k in range(66)] for values in watches.values()]
        med=[statistics.median(p[k] for p in watch_profiles) for k in range(66)]
        polygon=hull([[med[0]+med[2+k]*math.cos(k*math.tau/64),med[1]+med[2+k]*math.sin(k*math.tau/64)] for k in range(64)])
        photos=sorted({p[0] for values in watches.values() for p in values})
        shapes[key]=dict(polygon=polygon,recorded_groups=len(watches),photo_ids=photos,provenance=[{k:metadata[id][k] for k in ('photo_id','physical_watch_id','sha256','model','source_url','image_url','identity_status')} for id in photos])
    expected={'rounds','twelve','six','nine'}|({'three'} if family=='submariner_124060' else set())
    # Unsupported reference keys stay absent; do not silently infer a metal inset.
    report=dict(experimental=True,model=family,method='Median 64-ray inner contour profiles; one equal contribution per recorded group; minimum 3 groups; ring removed; no metal inset; no manufacturing tolerances',development_groups=sum(r["family"]==family and r["split"]=="development" for r in subset),heldout_groups=4,unsupported_keys=sorted(expected-shapes.keys()),shapes=shapes)
    (dest/(family+'.json')).write_text(json.dumps(report,indent=2)+'\n');print(family,{k:v['recorded_groups'] for k,v in shapes.items()},'unsupported',report['unsupported_keys'])
