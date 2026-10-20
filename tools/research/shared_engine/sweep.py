from rescore import *
POOL=dict(roundPos=0.671,batonPos=0.752,batonTilt=1.096,triSide=0.437,triInout=0.373,triGap=2.81,triRot=1.174)
SETS={'current':CUR,'pooled':POOL}
def one(k,v): d=dict(CUR);d[k]=v;return d
for k in POOL: SETS['cur+'+k]=one(k,POOL[k])
elig={}
for fam,fn in [('GMT','gmt'),('NoDate','nodate'),('Date','date')]:
    for r in csv.DictReader(open(R+fn+'-suitability.csv')): elig[r['photo_id']]=r['eligible']=='yes'
print('%-16s'%'set',end='')
for fam in GEN: print('| %-28s'%(fam+' mk flag / watch flag'),end='')
print('| QC controls (hour:state)')
for name,L in SETS.items():
    print('%-16s'%name,end='')
    for fam,rows in GEN.items():
        mk=fl=0;w=collections.defaultdict(bool);ph=set()
        for r in rows:
            s,_=decide(r,L)
            if s=='CANT_CHECK': continue
            mk+=1;o=s in('CLEARLY_OFF','POSSIBLE');fl+=o;w[r['watch']]|=o
        print('| %4d/%4d %4.1f%%  %3d/%3d %4.0f%% '%(fl,mk,100*fl/mk,sum(w.values()),len(w),100*sum(w.values())/len(w)),end='')
    q=[]
    for r in QC:
        s,off=decide(r,L)
        if s in('CLEARLY_OFF','POSSIBLE'): q.append(r['photo_id'].split('_')[1][:6]+r['photo_id'][-2:]+':'+r['hour']+('C' if s=='CLEARLY_OFF' else 'P')+'/'.join(off))
    print('| '+' '.join(q))
