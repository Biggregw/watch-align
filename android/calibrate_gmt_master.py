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
    if best is None: raise RuntimeError('No edge pair')
    return best

def measure_baton(gray,cx,cy,R,hour,roughR):
    ex,ey=polar_point(cx,cy,roughR*R,hour);patch,tcoords,rcoords=sample_local(gray,ex,ey,R,hour)
    pr=np.mean(np.abs(np.diff(patch.astype(np.float32),axis=0)),axis=1);pr=np.r_[pr,pr[-1]]
    pt=np.mean(np.abs(np.diff(patch.astype(np.float32),axis=1)),axis=0);pt=np.r_[pt,pt[-1]]
    ri,rj=best_edge_pair(pr,rcoords,.075,.155);ti,tj=best_edge_pair(pt,tcoords,.025,.075)
    r0,r1=float(rcoords[ri]),float(rcoords[rj]);t0,t1=float(tcoords[ti]),float(tcoords[tj]);roff=(r0+r1)/2;toff=(t0+t1)/2;rh=(r1-r0)/2;th=(t1-t0)/2
    centerR=roughR+roff;path=[(t0-toff,r0-roff),(t1-toff,r0-roff),(t1-toff,r1-roff),(t0-toff,r1-roff)];return centerR,rh,th,path

def measure_triangle(gray,cx,cy,R,roughR):
    """Measure the three outer 12-marker vertices from edge energy in the official image."""
    ex,ey=polar_point(cx,cy,roughR*R,12);patch,tcoords,rcoords=sample_local(gray,ex,ey,R,12,t_half=.15,r_half=.23)
    edges=cv2.Canny(patch,45,125,L2gradient=True)
    # Score a horizontal base edge in the outward half of the local patch. We require
    # balanced left/right support so minute-track arcs cannot win merely by being long.
    base_best=None;base_score=-1e30
    for yi,r in enumerate(rcoords):
        if not (.035<=r<=.125): continue
        xs=np.where(edges[yi]>0)[0]
        if len(xs)<2: continue
        tv=tcoords[xs]; left=tv[(tv>=-.125)&(tv<=-.050)]; right=tv[(tv>=.050)&(tv<=.125)]
        if len(left)==0 or len(right)==0: continue
        l=float(left[np.argmin(np.abs(left+.085))]); rr=float(right[np.argmin(np.abs(right-.085))])
        half=(rr-l)/2; symmetry=abs(rr+l)
        if not (.055<=half<=.115): continue
        # Horizontal edge density between endpoints strongly favours the triangle base.
        lo=np.searchsorted(tcoords,l);hi=np.searchsorted(tcoords,rr); density=float(np.count_nonzero(edges[yi,max(0,lo):min(edges.shape[1],hi+1)]))
        score=density*4.0-900*symmetry-160*abs(r-.085)
        if score>base_score: base_best=(l,rr,float(r));base_score=score
    if base_best is None: raise RuntimeError('Could not measure 12 triangle base')
    left_t,right_t,base_r=base_best

    # Find the inward apex. Search central edge pixels only, then prefer the inward-most
    # plausible point that also lies close to the triangle centreline.
    apex_best=None;apex_score=-1e30
    ys,xs=np.where(edges>0)
    for yi,xi in zip(ys,xs):
        t=float(tcoords[xi]);r=float(rcoords[yi])
        if not (-.205<=r<=-.075 and abs(t)<=.050): continue
        score=(-r)*1000-abs(t)*1200
        if score>apex_score: apex_best=(t,r);apex_score=score
    if apex_best is None: raise RuntimeError('Could not measure 12 triangle apex')
    apex_t,apex_r=apex_best

    # Use the measured base midpoint as the lateral centre and measured apex radial position.
    tc=(left_t+right_t+apex_t)/3.0; rc=(base_r+base_r+apex_r)/3.0
    local=[(left_t-tc,base_r-rc),(right_t-tc,base_r-rc),(apex_t-tc,apex_r-rc)]
    centerR=roughR+rc;rad=max(abs(y) for x,y in local);tang=max(abs(x) for x,y in local)
    height=base_r-apex_r
    if not (.70<centerR<.86 and .16<height<.34 and .055<tang<.14): raise RuntimeError(f'Implausible measured triangle {centerR:.4f}/height={height:.4f}/half={tang:.4f}')
    print(f'CAL triangle vertices: base=({left_t:.5f},{base_r:.5f})..({right_t:.5f},{base_r:.5f}) apex=({apex_t:.5f},{apex_r:.5f})')
    return centerR,rad,tang,local

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

    tri_center,tri_rad,tri_tan,tri_path=measure_triangle(gray,cx,cy,R,round_center);print(f'CAL triangle: centerR={tri_center:.5f} radialHalf={tri_rad:.5f} tangHalf={tri_tan:.5f}')

    java=f'''package com.watchalign.mobile;\n\n/** AUTO-GENERATED from first-party Rolex 126710BLNR catalogue fixture by calibrate_gmt_master.py. */\nfinal class Gmt126710BlnrMeasured {{\n  static final String ID="126710BLNR-official-trace-v1";\n  static final double DIAL_EDGE_R=1.0;\n  static final double MARKER_CENTER_R={round_center:.7f};\n  static final double BATON_CENTER_R={baton_center:.7f};\n  static final double TRI_CENTER_R={tri_center:.7f};\n  static final float[][] ROUND_OUTER={{{fmt_arr(rp)}}};\n  static final float[][] BATON_OUTER={{{fmt_arr(bp)}}};\n  static final float[][] TRI_OUTER={{{fmt_arr(tri_path)}}};\n  private Gmt126710BlnrMeasured(){{}}\n}}\n'''
    OUT.write_text(java,encoding='utf-8');print('CAL medians:',round_center,round_rad,round_tan,baton_center,baton_rad,baton_tan);print('CAL wrote',OUT)

if __name__=='__main__': main()
