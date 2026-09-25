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
    h,w=gray.shape[:2]; blur=cv2.GaussianBlur(gray,(7,7),1.4)
    circles=cv2.HoughCircles(blur,cv2.HOUGH_GRADIENT,dp=1.2,minDist=min(h,w)*.25,param1=100,param2=35,minRadius=int(min(h,w)*.20),maxRadius=int(min(h,w)*.48))
    if circles is None: return None
    candidates=[]
    for c in circles[0]:
        x,y,r=map(float,c); containment=min(x,y,w-x,h-y)/max(r,1.0)
        if containment < .85: continue
        d=float(np.hypot(x-w/2.0,y-h/2.0)); candidates.append((r-.8*d,x,y,r))
    if not candidates: return None
    _,x,y,r=max(candidates,key=lambda z:z[0]); return x,y,r

def _bright_components(gray: np.ndarray,roi):
    x0,y0,x1,y1=roi; patch=gray[y0:y1,x0:x1]
    if patch.size==0: return []
    _,bw=cv2.threshold(patch,0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU)
    bw=cv2.morphologyEx(bw,cv2.MORPH_OPEN,np.ones((2,2),np.uint8))
    contours,_=cv2.findContours(bw,cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_NONE); out=[]
    for c in contours:
        a=cv2.contourArea(c)
        if a<4: continue
        p=c.reshape(-1,2).astype(float); p[:,0]+=x0; p[:,1]+=y0; out.append((a,p))
    return out

def _triangle_candidate(gray,cx,cy,r):
    """Locate the visible luminous 12 triangle, not nearby bright geometry."""
    roi=(max(0,int(cx-.18*r)),max(0,int(cy-.72*r)),min(gray.shape[1],int(cx+.18*r)),min(gray.shape[0],int(cy-.30*r)))
    candidates=[]
    for area,p in _bright_components(gray,roi):
        xspan=float(np.ptp(p[:,0])); yspan=float(np.ptp(p[:,1]))
        if xspan<.10*r or xspan>.28*r or yspan<.12*r or yspan>.30*r: continue
        hull=cv2.convexHull(p.astype(np.float32).reshape(-1,1,2)); peri=cv2.arcLength(hull,True)
        if peri<=0: continue
        poly=cv2.approxPolyDP(hull,.03*peri,True).reshape(-1,2)
        if len(poly)!=3: continue
        q=poly[np.argsort(poly[:,1])]; upper=q[:2]; tip=q[2]
        if abs(float(upper[0,1]-upper[1,1]))>.035*r: continue
        left,right=sorted(upper,key=lambda z:float(z[0])); width=float(right[0]-left[0]); height=float(tip[1]-(left[1]+right[1])/2.0)
        if width<=0 or height<=0 or not (.55<=width/height<=1.65): continue
        midx=(float(left[0])+float(right[0]))/2.0
        if abs(float(tip[0])-midx)>.22*width or abs(midx-cx)>.10*r: continue
        candidates.append((area,left,right,tip))
    if not candidates: return None
    _,left,right,tip=max(candidates,key=lambda z:z[0])
    return Point(float(left[0]),float(left[1])),Point(float(right[0]),float(right[1])),Point(float(tip[0]),float(tip[1]))

def _minute_ticks(gray,cx,cy,r,tri_left: Point,tri_right: Point):
    """Observe the physical 60 tick and its immediate neighbours.

    The strip is defined from the already-verified triangle top, not from a
    fitted radial position. This avoids the earlier failure where a slightly
    wrong dial radius put the search band above the printed minute track.
    Triangle position is used only to choose the local top-centre strip and to
    identify which member of a periodic tick sequence is 60. Reported tick
    coordinates always come from observed connected components.
    """
    tri_mid=(tri_left.x+tri_right.x)/2.0
    tri_top=(tri_left.y+tri_right.y)/2.0
    x0=max(0,int(tri_mid-.26*r)); x1=min(gray.shape[1],int(tri_mid+.26*r))
    y0=max(0,int(tri_top-.16*r)); y1=min(gray.shape[0],int(tri_top+.025*r))
    patch=gray[y0:y1,x0:x1]
    if patch.size==0: return None
    _,bw=cv2.threshold(patch,0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU)
    n,labels,stats,_=cv2.connectedComponentsWithStats(bw,8)
    ticks=[]
    for i in range(1,n):
        x,y,w,h,area=map(int,stats[i])
        # Physical minute ticks are narrow, tall bright components. Limits are
        # scale-relative and intentionally broad; sequence regularity below is
        # the stronger discriminator.
        if area<4 or h<.018*r or h>.10*r or w>.045*r or h<1.35*max(w,1): continue
        gx=x0+x+w/2.0; inner_y=y0+y+h-1.0
        ticks.append((gx,inner_y,float(w),float(h),float(area)))
    if len(ticks)<3: return None
    ticks.sort(key=lambda t:t[0])

    # Find a consecutive three-tick sequence straddling the triangle axis with
    # approximately equal pitch. This prevents isolated rehaut highlights from
    # becoming the 60 reference. The triangle is only an identity aid; the
    # resulting 60 x/y are the observed central component itself.
    triples=[]
    for i in range(1,len(ticks)-1):
        l,c,rr=ticks[i-1],ticks[i],ticks[i+1]
        p1=c[0]-l[0]; p2=rr[0]-c[0]
        if p1<=.025*r or p2<=.025*r or p1>.11*r or p2>.11*r: continue
        regularity=abs(p1-p2)/max((p1+p2)/2.0,1.0)
        if regularity>.28: continue
        axis_error=abs(c[0]-tri_mid)/max((p1+p2)/2.0,1.0)
        # Do not silently hop to a neighbouring tick if triangle/tick geometry
        # is grossly inconsistent. Such an image is unassessable at this gate.
        if axis_error>.45: continue
        yspread=max(l[1],c[1],rr[1])-min(l[1],c[1],rr[1])
        score=regularity+.35*axis_error+.20*(yspread/max(r,1.0))
        triples.append((score,l,c,rr))
    if not triples: return None
    _,left,central,right=min(triples,key=lambda z:z[0])
    return Point(left[0],left[1]),Point(right[0],right[1]),Point(central[0],central[1])

def detect_gmt12(bgr: np.ndarray)->Detection:
    if bgr is None or bgr.size==0: return Detection(None,0.0,"empty image")
    gray=cv2.cvtColor(bgr,cv2.COLOR_BGR2GRAY) if bgr.ndim==3 else bgr.copy(); circle=_dial_circle(gray)
    if circle is None: return Detection(None,0.0,"dial search seed not found")
    cx,cy,r=circle; tri=_triangle_candidate(gray,cx,cy,r)
    if tri is None: return Detection(None,0.0,"12 triangle physical contour not found")
    tl,tr,tip=tri; ticks=_minute_ticks(gray,cx,cy,r,tl,tr)
    if ticks is None: return Detection(None,0.0,"physical 60/neighbour minute ticks not verified")
    ml,mr,m60=ticks; g=Gmt12Geometry(tl,tr,tip,ml,mr,m60)
    width=float(np.hypot(tr.x-tl.x,tr.y-tl.y)); centred=abs(((tl.x+tr.x)/2)-m60.x)/max(width,1.)
    return Detection(g,float(max(0.0,min(1.0,1.0-.5*centred))),"")

def render_detection(bgr: np.ndarray,d: Detection)->np.ndarray:
    out=bgr.copy()
    if d.geometry is None: return out
    g=d.geometry
    def pt(p): return int(round(p.x)),int(round(p.y))
    cv2.line(out,pt(g.minute_inner_left),pt(g.minute_inner_right),(0,255,255),2); cv2.line(out,pt(g.triangle_top_left),pt(g.triangle_top_right),(0,255,0),2)
    mid=Point((g.triangle_top_left.x+g.triangle_top_right.x)/2,(g.triangle_top_left.y+g.triangle_top_right.y)/2); cv2.line(out,pt(mid),pt(g.triangle_tip),(255,255,0),2)
    for p in (g.triangle_top_left,g.triangle_top_right,g.triangle_tip,g.minute_inner_left,g.minute_inner_right,g.minute_60_center): cv2.circle(out,pt(p),5,(0,0,255),-1)
    return out
