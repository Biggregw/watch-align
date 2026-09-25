"""Research-only automatic landmarks for the human GMT 12 QC contract.

This detector is deliberately narrow: near-frontal QC-style images only. It
exists to prove that software can recover the same physical features a human
marked before any baseline, thresholds, perspective correction or production
QC is attempted.
"""
from __future__ import annotations
from dataclasses import dataclass
from typing import Optional
import cv2
import numpy as np
from human_qc_geometry import Gmt12Geometry, Point

@dataclass(frozen=True)
class Detection:
    geometry: Optional[Gmt12Geometry]
    confidence: float
    reason: str = ""

def _dial_circle(gray: np.ndarray):
    h, w = gray.shape[:2]
    blur = cv2.GaussianBlur(gray, (7, 7), 1.4)
    circles = cv2.HoughCircles(blur, cv2.HOUGH_GRADIENT, dp=1.2,
        minDist=min(h,w)*.25, param1=100, param2=35,
        minRadius=int(min(h,w)*.20), maxRadius=int(min(h,w)*.48))
    if circles is None: return None
    candidates=[]
    for c in circles[0]:
        x,y,r=map(float,c)
        containment=min(x,y,w-x,h-y)/max(r,1.0)
        if containment < .85: continue
        centre_distance=float(np.hypot(x-w/2.0,y-h/2.0))
        candidates.append((r-.8*centre_distance,x,y,r))
    if not candidates: return None
    _,x,y,r=max(candidates,key=lambda z:z[0])
    return x,y,r

def _bright_components(gray: np.ndarray, roi):
    x0,y0,x1,y1=roi
    patch=gray[y0:y1,x0:x1]
    if patch.size == 0: return []
    _,bw=cv2.threshold(patch,0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU)
    bw=cv2.morphologyEx(bw,cv2.MORPH_OPEN,np.ones((2,2),np.uint8))
    contours,_=cv2.findContours(bw,cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_NONE)
    out=[]
    for c in contours:
        a=cv2.contourArea(c)
        if a < 4: continue
        p=c.reshape(-1,2).astype(float); p[:,0]+=x0; p[:,1]+=y0
        out.append((a,p))
    return out

def _triangle_candidate(gray,cx,cy,r):
    """Locate the visible luminous 12 triangle, not nearby bright geometry.

    The previous broad ROI admitted rehaut/minute-track structures and scored
    primarily by area. On the two human-reviewed genuine controls that could
    select a wider unrelated component. Here the search is deliberately below
    the minute track and a candidate must itself reduce to a downward-pointing
    three-vertex polygon. These are physical image observations only.
    """
    roi=(max(0,int(cx-.18*r)),max(0,int(cy-.72*r)),
         min(gray.shape[1],int(cx+.18*r)),min(gray.shape[0],int(cy-.30*r)))
    candidates=[]
    for area,p in _bright_components(gray,roi):
        xspan=float(np.ptp(p[:,0])); yspan=float(np.ptp(p[:,1]))
        if xspan < .10*r or xspan > .28*r or yspan < .12*r or yspan > .30*r:
            continue
        contour=p.astype(np.float32).reshape(-1,1,2)
        hull=cv2.convexHull(contour)
        peri=cv2.arcLength(hull,True)
        if peri <= 0: continue
        poly=cv2.approxPolyDP(hull,.03*peri,True).reshape(-1,2)
        if len(poly) != 3: continue
        # Sort by y: two upper vertices must form the top edge and the third
        # must be the inward/downward tip. This rejects text, hands and ticks.
        q=poly[np.argsort(poly[:,1])]
        upper=q[:2]; tip=q[2]
        if abs(float(upper[0,1]-upper[1,1])) > .035*r: continue
        left,right=sorted(upper,key=lambda z:float(z[0]))
        width=float(right[0]-left[0]); height=float(tip[1]-(left[1]+right[1])/2.0)
        if width <= 0 or height <= 0: continue
        if not (.55 <= width/height <= 1.65): continue
        midx=(float(left[0])+float(right[0]))/2.0
        if abs(float(tip[0])-midx) > .22*width: continue
        if abs(midx-cx) > .10*r: continue
        candidates.append((area,p,left,right,tip))
    if not candidates: return None
    _,_,left,right,tip=max(candidates,key=lambda z:z[0])
    return Point(float(left[0]),float(left[1])), Point(float(right[0]),float(right[1])), Point(float(tip[0]),float(tip[1]))

def _minute_ticks(gray,cx,cy,r,tri_left: Point,tri_right: Point):
    roi=(max(0,int(cx-.24*r)),max(0,int(cy-.99*r)),
         min(gray.shape[1],int(cx+.24*r)),min(gray.shape[0],int(cy-.73*r)))
    ticks=[]
    for area,p in _bright_components(gray,roi):
        x0,x1=p[:,0].min(),p[:,0].max(); y0,y1=p[:,1].min(),p[:,1].max()
        ww=max(1.,x1-x0); hh=max(1.,y1-y0)
        if hh < .018*r or hh > .16*r or ww > .10*r: continue
        ticks.append((float((x0+x1)/2),float(y1),float(area),p))
    if len(ticks)<3: return None
    ticks.sort(key=lambda t:t[0]); central=min(ticks,key=lambda t:abs(t[0]-cx))
    lefts=[t for t in ticks if t[0]<central[0]-.01*r]
    rights=[t for t in ticks if t[0]>central[0]+.01*r]
    if not lefts or not rights: return None
    left=max(lefts,key=lambda t:t[0]); right=min(rights,key=lambda t:t[0])
    return Point(left[0],left[1]),Point(right[0],right[1]),Point(central[0],central[1])

def detect_gmt12(bgr: np.ndarray)->Detection:
    if bgr is None or bgr.size==0: return Detection(None,0.0,"empty image")
    gray=cv2.cvtColor(bgr,cv2.COLOR_BGR2GRAY) if bgr.ndim==3 else bgr.copy()
    circle=_dial_circle(gray)
    if circle is None: return Detection(None,0.0,"dial search seed not found")
    cx,cy,r=circle
    tri=_triangle_candidate(gray,cx,cy,r)
    if tri is None: return Detection(None,0.0,"12 triangle physical contour not found")
    tl,tr,tip=tri
    ticks=_minute_ticks(gray,cx,cy,r,tl,tr)
    if ticks is None: return Detection(None,0.0,"60/neighbour minute ticks not all observed")
    ml,mr,m60=ticks
    g=Gmt12Geometry(tl,tr,tip,ml,mr,m60)
    width=float(np.hypot(tr.x-tl.x,tr.y-tl.y))
    centred=abs(((tl.x+tr.x)/2)-m60.x)/max(width,1.)
    return Detection(g,float(max(0.0,min(1.0,1.0-.5*centred))),"")

def render_detection(bgr: np.ndarray,d: Detection)->np.ndarray:
    out=bgr.copy()
    if d.geometry is None: return out
    g=d.geometry
    def pt(p): return int(round(p.x)),int(round(p.y))
    cv2.line(out,pt(g.minute_inner_left),pt(g.minute_inner_right),(0,255,255),2)
    cv2.line(out,pt(g.triangle_top_left),pt(g.triangle_top_right),(0,255,0),2)
    mid=Point((g.triangle_top_left.x+g.triangle_top_right.x)/2,(g.triangle_top_left.y+g.triangle_top_right.y)/2)
    cv2.line(out,pt(mid),pt(g.triangle_tip),(255,255,0),2)
    for p in (g.triangle_top_left,g.triangle_top_right,g.triangle_tip,g.minute_inner_left,g.minute_inner_right,g.minute_60_center):
        cv2.circle(out,pt(p),5,(0,0,255),-1)
    return out
