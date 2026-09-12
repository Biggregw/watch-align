#!/usr/bin/env python3
"""Derive a 126710BLNR visual master directly from the official Rolex front-on fixture.

This intentionally separates master creation from QC-photo registration. It measures the
manufacturer image once during CI and emits Java geometry used by the app build.
"""
from pathlib import Path
import math
import cv2
import numpy as np

SRC = Path('app/src/androidTest/assets/genuine/126710BLNR/official_00.jpg')
OUT = Path('app/src/main/java/com/watchalign/mobile/Gmt126710BlnrMeasured.java')
HOURS_ROUND = [1,2,4,5,7,8,10,11]

def polar_point(cx,cy,r,hour):
    a=math.radians(hour*30-90); return cx+r*math.cos(a),cy+r*math.sin(a)

def radial_profile(gray,cx,cy,rmin,rmax):
    yy,xx=np.indices(gray.shape); rr=np.sqrt((xx-cx)**2+(yy-cy)**2); bins=np.arange(rmin,rmax+1)
    prof=np.zeros(len(bins)-1,np.float64)
    for i in range(len(prof)):
        m=(rr>=bins[i])&(rr<bins[i+1]); prof[i]=gray[m].mean() if m.any() else 0
    return prof,bins[:-1]

def find_dial(gray):
    h,w=gray.shape; blur=cv2.GaussianBlur(gray,(9,9),2)
    circles=cv2.HoughCircles(blur,cv2.HOUGH_GRADIENT,dp=1.2,minDist=min(h,w)//8,param1=120,param2=50,minRadius=int(min(h,w)*0.18),maxRadius=int(min(h,w)*0.38))
    if circles is None: cx,cy=w/2,h/2
    else:
        cx0,cy0=w/2,h/2; cand=sorted(circles[0],key=lambda c:(c[0]-cx0)**2+(c[1]-cy0)**2); cx,cy,_=cand[0]
    prof,rs=radial_profile(gray,cx,cy,int(min(h,w)*0.18),int(min(h,w)*0.38)); smooth=cv2.GaussianBlur(prof.reshape(-1,1),(1,9),0).ravel(); grad=np.gradient(smooth)
    lo=int(len(grad)*0.35); hi=int(len(grad)*0.95); idx=lo+int(np.argmax(grad[lo:hi])); return float(cx),float(cy),float(rs[idx])

def component_contour(gray,cx,cy,radius,kind):
    x0=max(0,int(cx-radius)); x1=min(gray.shape[1],int(cx+radius+1)); y0=max(0,int(cy-radius)); y1=min(gray.shape[0],int(cy+radius+1))
    crop=cv2.GaussianBlur(gray[y0:y1,x0:x1],(3,3),0); t,_=cv2.threshold(crop,0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU); thr=max(78,int(t))
    mask=(crop>=thr).astype(np.uint8)*255; mask=cv2.morphologyEx(mask,cv2.MORPH_CLOSE,np.ones((3,3),np.uint8),iterations=2)
    n,labels,stats,cent=cv2.connectedComponentsWithStats(mask,8); ex=cx-x0; ey=cy-y0; best=None; score=1e18
    for i in range(1,n):
        area=stats[i,cv2.CC_STAT_AREA]
        if area<25: continue
        d=math.hypot(cent[i][0]-ex,cent[i][1]-ey); s=d*6-math.sqrt(area)
        if d<radius*0.72 and s<score: best=i;score=s
    if best is None: raise RuntimeError(f'No marker component for {kind}')
    obj=(labels==best).astype(np.uint8)*255; contours,_=cv2.findContours(obj,cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_NONE)
    cnt=max(contours,key=cv2.contourArea).reshape(-1,2).astype(np.float64); cnt[:,0]+=x0;cnt[:,1]+=y0; return cnt

def contour_center(cnt):
    m=cv2.moments(cnt.astype(np.float32)); return (cnt[:,0].mean(),cnt[:,1].mean()) if abs(m['m00'])<1e-6 else (m['m10']/m['m00'],m['m01']/m['m00'])

def radial_tangent_extents(cnt,cx,cy,hour,R):
    a=math.radians(hour*30-90); ux,uy=math.cos(a),math.sin(a); vx,vy=-uy,ux; x,y=contour_center(cnt); pts=cnt-np.array([x,y]); radial=pts[:,0]*ux+pts[:,1]*uy; tang=pts[:,0]*vx+pts[:,1]*vy
    return math.hypot(x-cx,y-cy)/R,max(abs(radial.min()),abs(radial.max()))/R,max(abs(tang.min()),abs(tang.max()))/R

def path_points(cnt,cx,cy,R,hour,max_points=28):
    mcx,mcy=contour_center(cnt); a=math.radians(hour*30-90); ux,uy=math.cos(a),math.sin(a); vx,vy=-uy,ux; simp=cv2.approxPolyDP(cnt.astype(np.float32),0.008*R,True).reshape(-1,2)
    if len(simp)>max_points:
        step=max(1,len(simp)//max_points); simp=simp[::step][:max_points]
    out=[]
    for x,y in simp:
        dx=x-mcx;dy=y-mcy; out.append(((dx*vx+dy*vy)/R,(dx*ux+dy*uy)/R))
    return out

def fmt_arr(points): return ','.join('{%.6ff,%.6ff}'%(x,y) for x,y in points)

def main():
    img=cv2.imread(str(SRC),cv2.IMREAD_COLOR)
    if img is None: raise SystemExit('Missing official Rolex fixture')
    gray=cv2.cvtColor(img,cv2.COLOR_BGR2GRAY); cx,cy,R=find_dial(gray); print(f'CAL dial center=({cx:.2f},{cy:.2f}) R={R:.2f}')
    rough=0.79*R; round_vals=[]; round_paths=[]
    for h in HOURS_ROUND:
        try:
            ex,ey=polar_point(cx,cy,rough,h); cnt=component_contour(gray,ex,ey,0.115*R,f'round {h}'); rr,rad,tan=radial_tangent_extents(cnt,cx,cy,h,R)
            if not (0.72<rr<0.84 and 0.038<rad<0.095 and 0.038<tan<0.095):
                print(f'CAL skip round {h}: contaminated r={rr:.4f} rad={rad:.4f} tan={tan:.4f}'); continue
            round_vals.append((rr,rad,tan)); round_paths.append(path_points(cnt,cx,cy,R,h)); print(f'CAL round {h}: centerR={rr:.5f} radialHalf={rad:.5f} tangHalf={tan:.5f}')
        except Exception as e: print(f'CAL skip round {h}: {e}')
    if len(round_vals)<4: raise RuntimeError(f'Only {len(round_vals)} clean round markers found')
    arr=np.array(round_vals); round_center=float(np.median(arr[:,0])); round_rad=float(np.median(arr[:,1])); round_tan=float(np.median(arr[:,2]))

    baton=[]; baton_paths=[]
    for h in [6,9]:
        try:
            ex,ey=polar_point(cx,cy,rough,h); cnt=component_contour(gray,ex,ey,0.16*R,f'baton {h}'); rr,rad,tan=radial_tangent_extents(cnt,cx,cy,h,R)
            if not (0.70<rr<0.84 and 0.07<rad<0.18 and 0.02<tan<0.09): print(f'CAL skip baton {h}: r={rr:.4f} rad={rad:.4f} tan={tan:.4f}'); continue
            baton.append((rr,rad,tan)); baton_paths.append(path_points(cnt,cx,cy,R,h)); print(f'CAL baton {h}: centerR={rr:.5f} radialHalf={rad:.5f} tangHalf={tan:.5f}')
        except Exception as e: print(f'CAL skip baton {h}: {e}')
    if not baton: raise RuntimeError('No clean baton found')
    ba=np.array(baton); baton_center=float(np.median(ba[:,0])); baton_rad=float(np.median(ba[:,1])); baton_tan=float(np.median(ba[:,2])); bp=baton_paths[int(np.argmin(np.abs(ba[:,1]-baton_rad)+np.abs(ba[:,2]-baton_tan)))]

    ex,ey=polar_point(cx,cy,rough,12); tri=component_contour(gray,ex,ey,0.18*R,'triangle 12'); tri_center,tri_rad,tri_tan=radial_tangent_extents(tri,cx,cy,12,R)
    if not (0.70<tri_center<0.84 and 0.10<tri_rad<0.24 and 0.05<tri_tan<0.13): raise RuntimeError(f'Implausible triangle {tri_center:.4f}/{tri_rad:.4f}/{tri_tan:.4f}')
    tri_path=path_points(tri,cx,cy,R,12,max_points=18); print(f'CAL triangle: centerR={tri_center:.5f} radialHalf={tri_rad:.5f} tangHalf={tri_tan:.5f}')

    scores=[abs(v[0]-round_center)+abs(v[1]-round_rad)+abs(v[2]-round_tan) for v in round_vals]; rp=round_paths[int(np.argmin(scores))]
    java=f'''package com.watchalign.mobile;\n\n/** AUTO-GENERATED from first-party Rolex 126710BLNR catalogue fixture by calibrate_gmt_master.py. */\nfinal class Gmt126710BlnrMeasured {{\n  static final String ID="126710BLNR-official-trace-v1";\n  static final double DIAL_EDGE_R=1.0;\n  static final double MARKER_CENTER_R={round_center:.7f};\n  static final double BATON_CENTER_R={baton_center:.7f};\n  static final double TRI_CENTER_R={tri_center:.7f};\n  static final float[][] ROUND_OUTER={{{fmt_arr(rp)}}};\n  static final float[][] BATON_OUTER={{{fmt_arr(bp)}}};\n  static final float[][] TRI_OUTER={{{fmt_arr(tri_path)}}};\n  private Gmt126710BlnrMeasured(){{}}\n}}\n'''
    OUT.write_text(java,encoding='utf-8'); print('CAL medians:',round_center,round_rad,round_tan,baton_center,baton_rad,baton_tan); print('CAL wrote',OUT)

if __name__=='__main__': main()
