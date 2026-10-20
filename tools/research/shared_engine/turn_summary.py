import os
import os
import csv,collections,statistics as st
rows=[r for r in csv.reader(open(os.path.join(os.path.dirname(os.path.abspath(__file__)),'results','known_turns.csv'))) if r[0]=='TURN']
by=collections.defaultdict(list)
for r in rows: by[(r[1][:8],r[2][:14],r[4],r[5])].append(r)
errs=collections.defaultdict(list)
for k,v in by.items():
    z=[r for r in v if float(r[6])==0][0];m0=float(z[8])
    if k[1].startswith('submariner_126'): print(*k,f'{m0:+.2f}','|',' '.join(f"{float(r[6]):+.2f}:{float(r[8])-m0:+.2f}{r[9][0]}{r[10][0]}" for r in v))
    for r in v:
        t=float(r[6])
        if t: errs[k[3]].append(float(r[8])-m0-t)
for sh,e in errs.items(): print(sh,'n',len(e),'mean %+.3f'%st.mean(e),'sd %.3f'%st.pstdev(e),'max|e| %.3f'%max(map(abs,e)))
print('markers',len(by),'edges',collections.Counter(r[10] for r in rows))
