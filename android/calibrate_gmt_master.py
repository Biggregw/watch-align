#!/usr/bin/env python3
"""Derive a 126710BLNR visual master directly from the official Rolex front-on fixture.

Master creation is deliberately separate from QC-photo registration. This script measures
Rolex's manufacturer image and emits immutable normalized Java geometry for the app build.
"""
from pathlib import Path
import math
import cv2
import numpy as np

SRC=Path('app/src/androidTest/assets/genuine/126710BLNR/official_00.jpg')
OUT=Path('app/src/main/java/com/watchalign/mobile/Gmt126710BlnrMeasured.java')
HOURS_ROUND=[1,2,4,5,7,8,10,11]

def polar_point(cx,cy,r,hour):
    a=math.radians(hour*30-90); return cx+r*math.cos(a),cy+r*math.sin(a)

def radial_profile(gray,cx,cy,rmin,rmax):
    yy,xx=np.indices(gray.shape); rr=np.sqrt((xx-cx)**2+(yy-cy)**2); bins=np.arange(rmin,rmax+1); prof=np.zeros(len(bins)-1)
    for i in range(len(prof)):
        m=(rr>=bins[i])&(rr<bins[i+1]); prof[i]=gray[m].mean() if m.any() else 0
    return prof,bins[:-1]

def find_dial(gray):
    h,w=gray.shape; blur=cv2.GaussianBlur(gray,(9,9),2)
    circles=cv2.HoughCircles(blur,cv2.HOUGH_GRADIENT,1.2,min(h,w)//8,param1=120,param2=50,minRadius=int(min(h,w)*.18),maxRadius=int(min(h,w)*.38))
    if circles is None: cx,cy=w/2,h/2
    else:
        cx0,cy0=w/2,h/2; cx,cy,_=sorted(circles[0],key=lambda c:(c[0]-cx0)**2+(c[1]-cy0)**2)[0]
    prof,rs=radial_profile(gray,cx,cy,int(min(h,w)*.18),int(min(h,w)*.38)); sm=cv2.GaussianBlur(prof.reshape(-1,1),(1,9),0).ravel(); g=np.gradient(sm)
    lo=int(len(g)*.35); hi=int(len(g)*.95); idx=lo+int(np.argmax(g[lo:hi])); return float(cx),float(cy),float(rs[idx])

def component_contour(gray,cx,cy,radius,kind):
    x0=max(0,int(cx-radius));x1=min(gray.shape[1],int(cx+radius+1));y0=max(0,int(cy-radius));y1=min(gray.shape[0],int(cy+radius+1))
    crop=cv2.GaussianBlur(gray[y0:y1,x0:x1],(3,3),0); t,_=cv2.threshold(crop,0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU); mask=(crop>=max(78,int(t))).astype(np.uint8)*255
    mask=cv2.morphologyEx(mask,cv2.MORPH_CLOSE,np.ones((3,3),np.uint8),iterations=2); n,labels,stats,cent=cv2.connectedComponentsWithStats(mask,8); ex=cx-x0;ey=cy-y0;best=None;score=1e18
    for i in range(1,n):
        area=stats[i,cv2.CC_STAT_AREA]
        if area<25: continue
        d=math.hypot(cent[i][0]-ex,cent[i][1]-ey); s=d*6-math.sqrt(area)
        if d<radius*.72 and s<score: best=i;score=s
    if best is None: raise RuntimeError(f'No marker component for {kind}')
    contours,_=cv2.findContours((labels==best).astype(np.uint8)*255,cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_NONE);cnt=max(contours,key=cv2.contourArea).reshape(-1,2).astype(float);cnt[:,0]+=x0;cnt[:,1]+=y0;return cnt

def contour_center(cnt):
    m=cv2.moments(cnt.astype(np.float32)); return (cnt[:,0].mean(),cnt[:,1].mean()) if abs(m['m00'])<1e-6 else (m['m10']/m['m00'],m['m01']/m['m00'])

def radial_tangent_extents(cnt,cx,cy,hour,R):
    a=math.radians(hour*30-90);ux,uy=math.cos(a),math.sin(a);vx,vy=-uy,ux;x,y=contour_center(cnt);pts=cnt-np.array([x,y]);radial=pts[:,0]*ux+pts[:,1]*uy;tang=pts[:,0]*vx+pts[:,1]*vy
    return math.hypot(x-cx,y-cy)/R,max(abs(radial.min()),abs(radial.max()))/R,max(abs(tang.min()),abs(tang.max()))/R

def path_points(cnt,R,hour,max_points=28):
    mcx,mcy=contour_center(cnt);a=math.radians(hour*30-90);ux,uy=math.cos(a),math.sin(a);vx,vy=-uy,ux;simp=cv2.approxPolyDP(cnt.astype(np.float32),.008*R,True).reshape(-1,2)
    if len(simp)>max_points: simp=simp[::max(1,len(simp)//max_points)][:max_points]
    return [((x-mcx)*vx/R+(y-mcy)*vy/R,(x-mcx)*ux/R+(y-mcy)*uy/R) for x,y in simp]

def sample_local(gray,ex,ey,R,hour,t_half=.11,r_half=.18):
    a=math.radians(hour*30-90);ux,uy=math.cos(a),math.sin(a);vx,vy=-uy,ux
    nt=max(61,int(2*t_half*R)+1); nr=max(101,int(2*r_half*R)+1)
    tv=np.linspace(-t_half*R,t_half*R,nt);rv=np.linspace(-r_half*R,r_half*R,nr);T,RV=np.meshgrid(tv,rv)
    mapx=(ex+vx*T+ux*RV).astype(np.float32);mapy=(ey+vy*T+uy*RV).astype(np.float32)
    patch=cv2.remap(gray,mapx,mapy,cv2.INTER_LINEAR,borderMode=cv2.BORDER_REPLICATE)
    return cv2.GaussianBlur(patch,(3,3),0),tv/R,rv/R

def best_edge_pair(profile,coords,min_half,max_half):
    p=cv2.GaussianBlur(profile.astype(np.float32).reshape(-1,1),(1,7),0).ravel();best=None;bestscore=-1e30
    left=np.where((coords>=-max_half)&(coords<=-min_half))[0];right=np.where((coords>=min_half)&(coords<=max_half))[0]
    for i in left:
        for j in right:
            symmetry=abs(coords[i]+coords[j]);score=float(p[i]+p[j]-4.0*symmetry*p.max())
            if score>bestscore: best=(i,j);bestscore=score
    if best is None:
        raise RuntimeError('No edge pair')
    return best

def measure_baton(gray,cx,cy,R,hour,roughR):
    ex,ey=polar_point(cx,cy,roughR*R,hour);patch,tcoords,rcoords=sample_local(gray,ex,ey,R,hour)
    pr=np.mean(np.abs(np.diff(patch.astype(np.float32),axis=0)),axis=1);pr=np.r_[pr,pr[-1]]
    pt=np.mean(np.abs(np.diff(patch.astype(np.float32),axis=1)),axis=0);pt=np.r_[pt,pt[-1]]
    ri,rj=best_edge_pair(pr,rcoords,.075,.155);ti,tj=best_edge_pair(pt,tcoords,.025,.075)
    r0,r1=float(rcoords[ri]),float(rcoords[rj]);t0,t1=float(tcoords[ti]),float(tcoords[tj]);roff=(r0+r1)/2;toff=(t0+t1)/2;rh=(r1-r0)/2;th=(t1-t0)/2
    centerR=roughR+roff;path=[(t0-toff,r0-roff),(t1-toff,r0-roff),(t1-toff,r1-roff),(t0-toff,r1-roff)];return centerR,rh,th,path

def fmt_arr(points): return ','.join('{%.6ff,%.6ff}'%(x,y) for x,y in points)

def main():
    img=cv2.imread(str(SRC),cv2.IMREAD_COLOR)
    if img is None: raise SystemExit('Missing official Rolex fixture')
    gray=cv2.cvtColor(img,cv2.COLOR_BGR2GRAY);cx,cy,R=find_dial(gray);print(f'CAL dial center=({cx:.2f},{cy:.2f}) R={R:.2f}')
    rough=.79;round_vals=[];round_paths=[]
    for h in HOURS_ROUND:
        try:
            ex,ey=polar_point(cx,cy,rough*R,h);cnt=component_contour(gray,ex,ey,.115*R,f'round {h}');rr,rad,tan=radial_tangent_extents(cnt,cx,cy,h,R)
            if not (.72<rr<.84 and .038<rad<.095 and .038<tan<.095): print(f'CAL skip round {h}: contaminated r={rr:.4f} rad={rad:.4f} tan={tan:.4f}');continue
            round_vals.append((rr,rad,tan));round_paths.append(path_points(cnt,R,h));print(f'CAL round {h}: centerR={rr:.5f} radialHalf={rad:.5f} tangHalf={tan:.5f}')
        except Exception as e: print(f'CAL skip round {h}: {e}')
    if len(round_vals)<4: raise RuntimeError(f'Only {len(round_vals)} clean round markers found')
    arr=np.array(round_vals);round_center=float(np.median(arr[:,0]));round_rad=float(np.median(arr[:,1]));round_tan=float(np.median(arr[:,2]));scores=[abs(v[0]-round_center)+abs(v[1]-round_rad)+abs(v[2]-round_tan) for v in round_vals];rp=round_paths[int(np.argmin(scores))]

    baton=[]
    for h in [6,9]:
        br,rad,tan,path=measure_baton(gray,cx,cy,R,h,round_center);baton.append((br,rad,tan,path));print(f'CAL baton {h}: centerR={br:.5f} radialHalf={rad:.5f} tangHalf={tan:.5f}')
    good=[b for b in baton if .70<b[0]<.84 and .075<b[1]<.155 and .025<b[2]<.075]
    if not good: raise RuntimeError('No plausible measured baton')
    baton_center=float(np.median([b[0] for b in good]));baton_rad=float(np.median([b[1] for b in good]));baton_tan=float(np.median([b[2] for b in good]));bp=min(good,key=lambda b:abs(b[1]-baton_rad)+abs(b[2]-baton_tan))[3]

    ex,ey=polar_point(cx,cy,round_center*R,12);tri=component_contour(gray,ex,ey,.18*R,'triangle 12');tri_center,tri_rad,tri_tan=radial_tangent_extents(tri,cx,cy,12,R)
    if not (.68<tri_center<.86 and .09<tri_rad<.26 and .045<tri_tan<.15): raise RuntimeError(f'Implausible triangle {tri_center:.4f}/{tri_rad:.4f}/{tri_tan:.4f}')
    tri_path=path_points(tri,R,12,max_points=18);print(f'CAL triangle: centerR={tri_center:.5f} radialHalf={tri_rad:.5f} tangHalf={tri_tan:.5f}')

    java=f'''package com.watchalign.mobile;\n\n/** AUTO-GENERATED from first-party Rolex 126710BLNR catalogue fixture by calibrate_gmt_master.py. */\nfinal class Gmt126710BlnrMeasured {{\n  static final String ID="126710BLNR-official-trace-v1";\n  static final double DIAL_EDGE_R=1.0;\n  static final double MARKER_CENTER_R={round_center:.7f};\n  static final double BATON_CENTER_R={baton_center:.7f};\n  static final double TRI_CENTER_R={tri_center:.7f};\n  static final float[][] ROUND_OUTER={{{fmt_arr(rp)}}};\n  static final float[][] BATON_OUTER={{{fmt_arr(bp)}}};\n  static final float[][] TRI_OUTER={{{fmt_arr(tri_path)}}};\n  private Gmt126710BlnrMeasured(){{}}\n}}\n'''
    OUT.write_text(java,encoding='utf-8');print('CAL medians:',round_center,round_rad,round_tan,baton_center,baton_rad,baton_tan);print('CAL wrote',OUT)

if __name__=='__main__': main()
