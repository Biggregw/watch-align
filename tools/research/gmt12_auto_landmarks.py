"""Research-only automatic landmarks for the human GMT 12 QC contract.

Near-frontal QC images only. The detector observes physical triangle and
59/60/1 minute-track landmarks. It fails closed when those landmarks cannot be
verified. Crystal reflections are handled with several local contrast masks;
a result is accepted only when the same regular three-tick geometry is still
supported by the image.
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
    h,w=gray.shape[:2]
    blur=cv2.GaussianBlur(gray,(7,7),1.4)
    circles=cv2.HoughCircles(blur,cv2.HOUGH_GRADIENT,dp=1.2,minDist=min(h,w)*.25,param1=100,param2=35,minRadius=int(min(h,w)*.20),maxRadius=int(min(h,w)*.48))
    if circles is None: return None
    candidates=[]
    for c in circles[0]:
        x,y,r=map(float,c)
        containment=min(x,y,w-x,h-y)/max(r,1.0)
        if containment < .85: continue
        d=float(np.hypot(x-w/2.0,y-h/2.0))
        candidates.append((r-.8*d,x,y,r))
    if not candidates: return None
    _,x,y,r=max(candidates,key=lambda z:z[0])
    return x,y,r


def _bright_components(gray: np.ndarray,roi):
    x0,y0,x1,y1=roi
    patch=gray[y0:y1,x0:x1]
    if patch.size==0: return []
    _,bw=cv2.threshold(patch,0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU)
    bw=cv2.morphologyEx(bw,cv2.MORPH_OPEN,np.ones((2,2),np.uint8))
    contours,_=cv2.findContours(bw,cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_NONE)
    out=[]
    for c in contours:
        a=cv2.contourArea(c)
        if a<4: continue
        p=c.reshape(-1,2).astype(float); p[:,0]+=x0; p[:,1]+=y0
        out.append((a,p))
    return out


def _triangle_candidate(gray,cx,cy,r):
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
        left,right=sorted(upper,key=lambda z:float(z[0]))
        width=float(right[0]-left[0]); height=float(tip[1]-(left[1]+right[1])/2.0)
        if width<=0 or height<=0 or not (.55<=width/height<=1.65): continue
        midx=(float(left[0])+float(right[0]))/2.0
        if abs(float(tip[0])-midx)>.22*width or abs(midx-cx)>.10*r: continue
        candidates.append((area,left,right,tip))
    if not candidates: return None
    _,left,right,tip=max(candidates,key=lambda z:z[0])
    return Point(float(left[0]),float(left[1])),Point(float(right[0]),float(right[1])),Point(float(tip[0]),float(tip[1]))


def _tick_masks(patch: np.ndarray):
    """Return complementary masks so glare does not erase narrow minute ticks."""
    masks=[]
    # Original behaviour remains the first hypothesis.
    _,otsu=cv2.threshold(patch,0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU)
    masks.append(otsu)

    # CLAHE restores local tick/dial contrast when a broad crystal reflection
    # raises the whole top strip. It does not invent geometry; triple validation
    # below is unchanged and remains mandatory.
    clahe=cv2.createCLAHE(clipLimit=2.0,tileGridSize=(8,4)).apply(patch)
    _,co=cv2.threshold(clahe,0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU)
    masks.append(co)

    # White top-hat removes slowly varying glare while retaining narrow bright
    # radial strokes. Kernel is deliberately much wider than a minute tick.
    kh=max(5,(patch.shape[0]//2)|1); kw=max(9,(patch.shape[1]//12)|1)
    kernel=cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(kw,kh))
    top=cv2.morphologyEx(clahe,cv2.MORPH_TOPHAT,kernel)
    _,th=cv2.threshold(top,0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU)
    masks.append(th)
    return masks


def _ticks_from_mask(mask,x0,y0,r):
    # Small vertical close reconnects a tick split by a reflection boundary.
    mask=cv2.morphologyEx(mask,cv2.MORPH_CLOSE,np.ones((3,1),np.uint8))
    n,labels,stats,_=cv2.connectedComponentsWithStats(mask,8)
    ticks=[]
    for i in range(1,n):
        x,y,w,h,area=map(int,stats[i])
        if area<4 or h<.015*r or h>.11*r or w>.050*r or h<1.20*max(w,1): continue
        gx=x0+x+w/2.0; inner_y=y0+y+h-1.0
        ticks.append((gx,inner_y,float(w),float(h),float(area)))
    ticks.sort(key=lambda t:t[0])
    return ticks


def _best_tick_triple(ticks,tri_mid,r):
    triples=[]
    for i in range(1,len(ticks)-1):
        l,c,rr=ticks[i-1],ticks[i],ticks[i+1]
        p1=c[0]-l[0]; p2=rr[0]-c[0]
        if p1<=.025*r or p2<=.025*r or p1>.11*r or p2>.11*r: continue
        pitch=max((p1+p2)/2.0,1.0)
        regularity=abs(p1-p2)/pitch
        if regularity>.30: continue
        axis_error=abs(c[0]-tri_mid)/pitch
        if axis_error>.45: continue
        yspread=max(l[1],c[1],rr[1])-min(l[1],c[1],rr[1])
        if yspread>.055*r: continue
        score=regularity+.35*axis_error+.20*(yspread/max(r,1.0))
        triples.append((score,l,c,rr))
    return min(triples,key=lambda z:z[0]) if triples else None


def _minute_ticks(gray,cx,cy,r,tri_left: Point,tri_right: Point):
    """Observe physical 59/60/1 ticks, with glare-tolerant local contrast."""
    tri_mid=(tri_left.x+tri_right.x)/2.0
    tri_top=(tri_left.y+tri_right.y)/2.0
    x0=max(0,int(tri_mid-.26*r)); x1=min(gray.shape[1],int(tri_mid+.26*r))
    y0=max(0,int(tri_top-.18*r)); y1=min(gray.shape[0],int(tri_top+.030*r))
    patch=gray[y0:y1,x0:x1]
    if patch.size==0: return None

    hypotheses=[]
    for mask in _tick_masks(patch):
        ticks=_ticks_from_mask(mask,x0,y0,r)
        if len(ticks)<3: continue
        best=_best_tick_triple(ticks,tri_mid,r)
        if best is not None: hypotheses.append(best)
    if not hypotheses: return None

    # Prefer the strongest geometrically verified hypothesis. Multiple image
    # masks are only alternative ways of seeing the same physical strokes.
    _,left,central,right=min(hypotheses,key=lambda z:z[0])
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
    if ticks is None: return Detection(None,0.0,"physical 60/neighbour minute ticks not verified")
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
