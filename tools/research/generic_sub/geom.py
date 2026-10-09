import csv,sys,statistics as st,collections
ROUNDS=[1,2,4,5,7,8,10,11]
def f(x):
    try: return float(x)
    except: return None
def metrics(r):
    if r.get('status')!='accepted': return None
    R=f(r['dial_radius_px']); o={}
    o['ring_scale_%']=f(r.get('ring_scale_pct')) if r.get('ring_usable')=='true' else None
    o['ring_rot_deg']=f(r.get('ring_rotation_deg')) if r.get('ring_usable')=='true' else None
    sz=[f(r[f'm{h}_radius_err_px'])/R for h in ROUNDS if r.get(f'm{h}_usable')=='true' and f(r.get(f'm{h}_radius_err_px')) is not None]
    o['round_size_%R']=100*st.median(sz) if len(sz)>=5 else None
    for h in (3,6,9):
        if r.get(f'm{h}_usable')=='true':
            o[f'b{h}_rot_deg']=f(r[f'm{h}_rotation_deg']); o[f'b{h}_radial_%R']=100*f(r[f'm{h}_local_radial_px'])/R if f(r.get(f'm{h}_local_radial_px')) is not None else None
    if r.get('m12_usable')=='true':
        o['t12_radial_%R']=100*f(r['m12_local_radial_px'])/R if f(r.get('m12_local_radial_px')) is not None else None
    return {k:v for k,v in o.items() if v is not None}
def per_watch(rows,key):
    w=collections.defaultdict(lambda: collections.defaultdict(list))
    for r in rows:
        m=metrics(r)
        if m:
            for k,v in m.items(): w[key(r)][k].append(v)
    return {g:{k:st.median(v) for k,v in d.items()} for g,d in w.items()}
