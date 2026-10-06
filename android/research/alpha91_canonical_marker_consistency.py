#!/usr/bin/env python3
"""Research only. Pixel-free canonical-space check of the frozen GMT master shared radii/axes.
Fits H by least squares from the 8 embedded round-marker centres only (oracle, not a production fit),
inverse-projects every marker, and reports radius/angle against the master. Batons and 12 are holdouts.
"""
import sys, math, numpy as np, cv2
import os; sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import alpha91_fairscan_pose_proof as P
R={h:0.816 for h in (1,2,4,5,7,8,10,11)}; R[6]=0.758; R[9]=0.758; R[12]=0.750
def canon(h,r): a=math.radians(h*30); return (r*math.sin(a), -r*math.cos(a))
for name,mk in P.MARKERS.items():
    rounds=[h for h in mk if h in (1,2,4,5,7,8,10,11)]
    src=np.array([canon(h,0.816) for h in rounds],np.float64); dst=np.array([mk[h] for h in rounds],np.float64)
    H,_=cv2.findHomography(src,dst,0)
    Hi=np.linalg.inv(H)
    print(f"\n{name}  (H fit by least squares on {len(rounds)} round centres; batons/12 are holdouts)")
    # px per canonical unit near centre
    c=H@np.array([0,0,1.]);c=c[:2]/c[2]; e=H@np.array([0.01,0,1.]);e=e[:2]/e[2]; s=np.linalg.norm(e-c)/0.01
    for h in sorted(mk):
        p=Hi@np.array([*mk[h],1.]); x,y=p[:2]/p[2]
        r=math.hypot(x,y); ang=(math.degrees(math.atan2(x,-y))-h*30+180)%360-180
        pr=H@np.array([*canon(h,R[h]),1.]); pr=pr[:2]/pr[2]; d=np.array(mk[h])-pr
        print(f"  h{h:2d} r={r:.4f} (master {R[h]:.3f}, d={r-R[h]:+.4f} = {(r-R[h])*s:+.2f}px)  angle err {ang:+.2f}deg ({math.radians(ang)*r*s:+.2f}px)  resid=({d[0]:+.2f},{d[1]:+.2f})")
    # best baton/triangle radius
    for hs,lab in (((6,9),'batons'),((12,),'triangle')):
        rs=[]
        for h in hs:
            if h in mk:
                p=Hi@np.array([*mk[h],1.]); rs.append(math.hypot(*(p[:2]/p[2])))
        print(f"  best {lab} radius {np.mean(rs):.4f}")
