"""Research-only automatic landmarks for the human GMT 12 QC contract.

Near-frontal QC images only. The detector observes the physical 12 triangle and
uses the regular minute-track sequence around 12. Directly observed 59/60/1
landmarks are preferred. When glare hides one of those ticks, its position may
be reconstructed from other observed members of the same 6-degree minute
sequence. Inferred landmarks are accepted only when multiple observed ticks
support one regular sequence; otherwise detection fails closed.
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


def _tick_masks(patch: np.ndarray):
    masks=[]
    _,otsu=cv2.threshold(patch,0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU); masks.append(otsu)
    clahe=cv2.createCLAHE(clipLimit=2.0,tileGridSize=(8,4)).apply(patch)
    _,co=cv2.threshold(clahe,0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU); masks.append(co)
    kh=max(5,(patch.shape[0]//2)|1); kw=max(9,(patch.shape[1]//12)|1)
    kernel=cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(kw,kh))
    top=cv2.morphologyEx(clahe,cv2.MORPH_TOPHAT,kernel)
    _,th=cv2.threshold(top,0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU); masks.append(th)
    return masks


def _ticks_from_mask(mask,x0,y0,r):
    mask=cv2.morphologyEx(mask,cv2.MORPH_CLOSE,np.ones((3,1),np.uint8))
    n,labels,stats,_=cv2.connectedComponentsWithStats(mask,8); ticks=[]
    for i in range(1,n):
        x,y,w,h,area=map(int,stats[i])
        if area<4 or h<.015*r or h>.11*r or w>.050*r or h<1.20*max(w,1): continue
        ticks.append((x0+x+w/2.0,y0+y+h-1.0,float(w),float(h),float(area)))
    ticks.sort(key=lambda t:t[0]); return ticks


def _direct_triple(ticks,tri_mid,r):
    triples=[]
    for i in range(1,len(ticks)-1):
        l,c,rr=ticks[i-1],ticks[i],ticks[i+1]
        p1=c[0]-l[0]; p2=rr[0]-c[0]
        if p1<=.025*r or p2<=.025*r or p1>.11*r or p2>.11*r: continue
        pitch=max((p1+p2)/2.0,1.0); regularity=abs(p1-p2)/pitch
        if regularity>.30: continue
        axis_error=abs(c[0]-tri_mid)/pitch
        if axis_error>.45: continue
        yspread=max(l[1],c[1],rr[1])-min(l[1],c[1],rr[1])
        if yspread>.055*r: continue
        triples.append((regularity+.35*axis_error+.20*(yspread/max(r,1.0)),l,c,rr,False))
    return min(triples,key=lambda z:z[0]) if triples else None


def _sequence_with_missing_tick(ticks,tri_mid,r):
    """Fit a local equal-pitch minute sequence and reconstruct missing 59/60/1.

    Near 12 o'clock, equal 6-degree angular spacing projects to an almost equal
    x pitch in a near-frontal QC image. We use at least three actually observed
    tick components and allow integer pitch gaps, so one glare-obscured tick
    does not make the watch unassessable. The central 60 phase is selected by
    proximity to the already-observed triangle axis, but is not forced onto it.
    """
    if len(ticks)<3: return None
    xs=np.array([t[0] for t in ticks],dtype=float)
    ys=np.array([t[1] for t in ticks],dtype=float)
    candidates=[]
    # Derive plausible one-minute pitches from observed pair separations divided
    # by 1..4 minute intervals. This permits one or more hidden local strokes.
    for i in range(len(xs)):
        for j in range(i+1,len(xs)):
            dx=xs[j]-xs[i]
            for steps in range(1,5):
                pitch=dx/steps
                if pitch<=.025*r or pitch>.11*r: continue
                # Choose the integer sequence phase whose 60 position is nearest
                # the triangle axis. Observed ticks are then assigned integers.
                k0=int(round((tri_mid-xs[i])/pitch))
                x60=xs[i]+k0*pitch
                assigned=np.rint((xs-x60)/pitch).astype(int)
                # Only local evidence around 12 is relevant.
                keep=np.abs(assigned)<=5
                if np.count_nonzero(keep)<3: continue
                ax=assigned[keep]; ox=xs[keep]; oy=ys[keep]
                pred=x60+ax*pitch
                residual=np.abs(ox-pred)
                inlier=residual<=.22*pitch
                if np.count_nonzero(inlier)<3: continue
                ax=ax[inlier]; ox=ox[inlier]; oy=oy[inlier]
                # Need evidence on both sides of 60, or an observed 60 plus
                # evidence on at least one side. This prevents a glare streak on
                # one side from defining the whole minute phase.
                has_left=np.any(ax<0); has_right=np.any(ax>0); has_zero=np.any(ax==0)
                if not ((has_left and has_right) or (has_zero and (has_left or has_right))): continue
                # Refine x60 and pitch by least squares on the integer sequence.
                A=np.column_stack([np.ones(len(ax)),ax.astype(float)])
                beta,_,_,_=np.linalg.lstsq(A,ox,rcond=None); x60_fit=float(beta[0]); pitch_fit=float(beta[1])
                if pitch_fit<=.025*r or pitch_fit>.11*r: continue
                fit=np.abs(ox-(x60_fit+ax*pitch_fit))/pitch_fit
                if float(np.max(fit))>.24: continue
                axis_error=abs(x60_fit-tri_mid)/pitch_fit
                if axis_error>.60: continue
                # Inner tick y changes smoothly across this tiny arc. Fit y as a
                # local quadratic/line and evaluate it at -1,0,+1.
                deg=2 if len(ax)>=3 and len(np.unique(ax))>=3 else 1
                coeff=np.polyfit(ax.astype(float),oy,deg)
                y59=float(np.polyval(coeff,-1.0)); y60=float(np.polyval(coeff,0.0)); y1=float(np.polyval(coeff,1.0))
                yspread=max(y59,y60,y1)-min(y59,y60,y1)
                if yspread>.065*r: continue
                observed_targets=sum(int(np.any(ax==k)) for k in (-1,0,1))
                inferred_count=3-observed_targets
                score=float(np.mean(fit))+.25*axis_error+.08*inferred_count
                candidates.append((score,(x60_fit-pitch_fit,y59),(x60_fit,y60),(x60_fit+pitch_fit,y1),True,inferred_count))
    return min(candidates,key=lambda z:z[0]) if candidates else None


def _minute_ticks(gray,cx,cy,r,tri_left: Point,tri_right: Point):
    tri_mid=(tri_left.x+tri_right.x)/2.0; tri_top=(tri_left.y+tri_right.y)/2.0
    # Wider strip exposes 57..3 so hidden 59/60/1 can be reconstructed from the
    # exact minute sequence rather than forcing image processing to see glare.
    x0=max(0,int(tri_mid-.42*r)); x1=min(gray.shape[1],int(tri_mid+.42*r))
    y0=max(0,int(tri_top-.20*r)); y1=min(gray.shape[0],int(tri_top+.035*r))
    patch=gray[y0:y1,x0:x1]
    if patch.size==0: return None
    direct=[]; inferred=[]
    for mask in _tick_masks(patch):
        ticks=_ticks_from_mask(mask,x0,y0,r)
        if len(ticks)<3: continue
        d=_direct_triple(ticks,tri_mid,r)
        if d is not None: direct.append(d)
        q=_sequence_with_missing_tick(ticks,tri_mid,r)
        if q is not None: inferred.append(q)
    if direct:
        _,l,c,rr,_=min(direct,key=lambda z:z[0]); return Point(l[0],l[1]),Point(rr[0],rr[1]),Point(c[0],c[1]),False,0
    if inferred:
        _,l,c,rr,_,ninf=min(inferred,key=lambda z:z[0]); return Point(l[0],l[1]),Point(rr[0],rr[1]),Point(c[0],c[1]),True,ninf
    return None


def detect_gmt12(bgr: np.ndarray)->Detection:
    if bgr is None or bgr.size==0: return Detection(None,0.0,"empty image")
    gray=cv2.cvtColor(bgr,cv2.COLOR_BGR2GRAY) if bgr.ndim==3 else bgr.copy(); circle=_dial_circle(gray)
    if circle is None: return Detection(None,0.0,"dial search seed not found")
    cx,cy,r=circle; tri=_triangle_candidate(gray,cx,cy,r)
    if tri is None: return Detection(None,0.0,"12 triangle physical contour not found")
    tl,tr,tip=tri; ticks=_minute_ticks(gray,cx,cy,r,tl,tr)
    if ticks is None: return Detection(None,0.0,"minute-track sequence not sufficiently constrained")
    ml,mr,m60,inferred,ninf=ticks; g=Gmt12Geometry(tl,tr,tip,ml,mr,m60)
    width=float(np.hypot(tr.x-tl.x,tr.y-tl.y)); centred=abs(((tl.x+tr.x)/2)-m60.x)/max(width,1.)
    confidence=float(max(0.0,min(1.0,1.0-.5*centred-(.10*ninf if inferred else 0.0))))
    reason=(f"minute landmarks reconstructed from regular 6-degree sequence ({ninf} of 59/60/1 inferred)" if inferred else "")
    return Detection(g,confidence,reason)


def render_detection(bgr: np.ndarray,d: Detection)->np.ndarray:
    out=bgr.copy()
    if d.geometry is None: return out
    g=d.geometry
    def pt(p): return int(round(p.x)),int(round(p.y))
    cv2.line(out,pt(g.minute_inner_left),pt(g.minute_inner_right),(0,255,255),2)
    cv2.line(out,pt(g.triangle_top_left),pt(g.triangle_top_right),(0,255,0),2)
    mid=Point((g.triangle_top_left.x+g.triangle_top_right.x)/2,(g.triangle_top_left.y+g.triangle_top_right.y)/2)
    cv2.line(out,pt(mid),pt(g.triangle_tip),(255,255,0),2)
    for p in (g.triangle_top_left,g.triangle_top_right,g.triangle_tip,g.minute_inner_left,g.minute_inner_right,g.minute_60_center): cv2.circle(out,pt(p),5,(0,0,255),-1)
    return out
