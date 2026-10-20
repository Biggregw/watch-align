from rescore import *
def decide2(r,L,clean_tilt):
    s,off=decide(r,L)
    if s=='NO_VISIBLE_ISSUE' and r['shape']=='baton' and not r['uncertain']:
        t=f(r['tilt_line_deg'])
        if fin(t) and abs(t)>clean_tilt: return 'CLEARLY_OFF',['tilt(clean)']
    return s,off
for ct in (0.9,1.0,1.1,1.2,1.3,1.4,1.75):
    out=[]
    for fam,rows in GEN.items():
        fl=sum(decide2(r,CUR,ct)[0] in('CLEARLY_OFF','POSSIBLE') for r in rows)
        w=len({r['watch'] for r in rows if decide2(r,CUR,ct)[0] in('CLEARLY_OFF','POSSIBLE')})
        out.append(f'{fam} {fl} mk/{w} w')
    qc=[r['photo_id'][:14]+':'+r['hour'] for r in QC if decide2(r,CUR,ct)[0]!=r['state']]
    print(f'clean baton tilt {ct:4}: genuine flagged '+', '.join(out)+' | QC changes: '+(' '.join(qc) or 'none'))
# all baton tilts in QC set with edge status
print([(r['photo_id'][:16],r['hour'],r['tilt_line_deg'],'clean' if not r['uncertain'] else 'uncert') for r in QC if r['shape']=='baton' and abs(f(r['tilt_line_deg']))>0.8])
# top clean genuine baton tilts
top=sorted([(abs(f(r['tilt_line_deg'])),r['fam'],r['photo_id'],r['hour'],r['rpx'][:5]) for rows in GEN.values() for r in rows if r['shape']=='baton' and not r['gate'] and not r['uncertain'] and fin(f(r['tilt_line_deg']))],reverse=True)[:6]
print(top)
