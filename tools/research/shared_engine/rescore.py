import csv,math,sys,collections
import os
import os
R=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','marker_audit','results')+os.sep
def f(x):
    try: return float(x)
    except: return float('nan')
fin=math.isfinite
def load(path,fam):
    rows=list(csv.DictReader(open(path)))
    for r in rows: r['fam']=fam
    return rows
GEN={'GMT':load(R+'gmt-after.csv','GMT'),'NoDate':load(R+'nodate-after.csv','NoDate'),'Date':load(R+'date-after.csv','Date')}
QC=load(R+'qc/gmt_126710-after.csv','GMT')+load(R+'qc/submariner_124060-after.csv','NoDate')
CUR=dict(roundPos=1.0,batonPos=1.0,batonTilt=1.75,triSide=1.0,triInout=1.25,triGap=2.2,triRot=1.2)
def decide(r,L,tilt_col='tilt_line_deg'):
    if r['state']=='CANT_CHECK' and r['gate']: return 'CANT_CHECK',[]
    if r['gate']: return 'CANT_CHECK',[]
    rpx=f(r['rpx']);allow=50/rpx;sh=r['shape']
    side,io,tilt,gap=f(r['side_pct']),f(r['inout_pct']),f(r[tilt_col]),f(r['gap_pct'])
    off=[]
    if sh=='triangle':
        if fin(side) and abs(side)>L['triSide']+allow: off.append('side')
        if fin(io) and abs(io)>L['triInout']+allow: off.append('inout')
        if fin(gap) and gap<L['triGap']-allow: off.append('gap')
        tl=L['triRot']
    else:
        if fin(side) or fin(io):
            p=math.hypot(side if fin(side) else 0,io if fin(io) else 0)
            if p>(L['batonPos'] if sh=='baton' else L['roundPos'])+allow: off.append('pos')
        tl=L['batonTilt'] if sh=='baton' else float('nan')
    if fin(tl) and fin(tilt) and abs(tilt)>tl: off.append('tilt')
    if off:
        if not r['uncertain'] and not ('gap' in off and r['gap_from_fit'].lower() in('true','1','yes')): return 'CLEARLY_OFF',off
        return 'POSSIBLE',off
    if not fin(side) or not fin(io) or (fin(tl) and not fin(tilt)): return 'CANT_CHECK',[]
    return 'NO_VISIBLE_ISSUE',[]
if __name__=='__main__':
    bad=collections.Counter();n=0
    for rows in list(GEN.values())+[QC]:
        for r in rows:
            s,_=decide(r,CUR);n+=1
            if s!=r['state']: bad[(r['fam'],r['shape'],r['state'],s)]+=1
    print('rows',n,'mismatch',sum(bad.values()));print(bad.most_common(10))
