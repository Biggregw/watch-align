from __future__ import annotations
from dataclasses import dataclass
from typing import Optional
import cv2, numpy as np
from human_qc_geometry import Gmt12Geometry, Point

@dataclass(frozen=True)
class Detection:
    geometry: Optional[Gmt12Geometry]
    confidence: float
    reason: str = ""


def _dial_circle(gray):
    h,w=gray.shape[:2]; blur=cv2.GaussianBlur(gray,(7,7),1.4)
    cs=cv2.HoughCircles(blur,cv2.HOUGH_GRADIENT,1.2,min(h,w)*.25,param1=100,param2=35,minRadius=int(min(h,w)*.20),maxRadius=int(min(h,w)*.48))
    if cs is None: return None
    out=[]
    for x,y,r in cs[0]:
        x,y,r=map(float,(x,y,r)); containment=min(x,y,w-x,h-y)/max(r,1)
        if containment<.82: continue
        yy,xx=np.ogrid[:h,:w]; mask=(xx-x)**2+(yy-y)**2 < (.68*r)**2
        vals=gray[mask]
        if vals.size<100: continue
        dark=255-float(np.median(vals)); d=float(np.hypot(x-w/2,y-h/2))
        out.append((r-.65*d+.80*dark,x,y,r))
    if not out: return None
    _,x,y,r=max(out); return x,y,r


def _bright_components(gray,roi):
    x0,y0,x1,y1=roi; p=gray[y0:y1,x0:x1]
    if p.size==0:return []
    _,bw=cv2.threshold(p,0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU)
    bw=cv2.morphologyEx(bw,cv2.MORPH_OPEN,np.ones((2,2),np.uint8))
    cs,_=cv2.findContours(bw,cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_NONE); out=[]
    for c in cs:
        a=cv2.contourArea(c)
        if a<4:continue
        q=c.reshape(-1,2).astype(float); q[:,0]+=x0;q[:,1]+=y0;out.append((a,q))
    return out


def _triangle_candidate(gray,cx,cy,r):
    roi=(max(0,int(cx-.18*r)),max(0,int(cy-.72*r)),min(gray.shape[1],int(cx+.18*r)),min(gray.shape[0],int(cy-.30*r)))
    out=[]
    for area,p in _bright_components(gray,roi):
        xs,ys=float(np.ptp(p[:,0])),float(np.ptp(p[:,1]))
        if xs<.10*r or xs>.28*r or ys<.12*r or ys>.30*r:continue
        hull=cv2.convexHull(p.astype(np.float32).reshape(-1,1,2)); per=cv2.arcLength(hull,True)
        if per<=0:continue
        poly=cv2.approxPolyDP(hull,.03*per,True).reshape(-1,2)
        if len(poly)!=3:continue
        q=poly[np.argsort(poly[:,1])]; upper=q[:2]; tip=q[2]
        left,right=sorted(upper,key=lambda z:float(z[0])); width=float(right[0]-left[0]); height=float(tip[1]-(left[1]+right[1])/2)
        if width<=0 or height<=0 or not(.55<=width/height<=1.65):continue
        mid=(float(left[0])+float(right[0]))/2
        if abs(float(tip[0])-mid)>.22*width or abs(mid-cx)>.10*r:continue
        out.append((area,left,right,tip))
    if not out:return None
    _,l,rr,t=max(out,key=lambda z:z[0]);return Point(*map(float,l)),Point(*map(float,rr)),Point(*map(float,t))


def _tick_masks(p):
    out=[];_,a=cv2.threshold(p,0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU);out.append(a)
    c=cv2.createCLAHE(2.0,(8,4)).apply(p);_,a=cv2.threshold(c,0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU);out.append(a)
    kh=max(5,(p.shape[0]//2)|1);kw=max(9,(p.shape[1]//12)|1);k=cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(kw,kh))
    top=cv2.morphologyEx(c,cv2.MORPH_TOPHAT,k);_,a=cv2.threshold(top,0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU);out.append(a)
    return out


def _ticks(mask,x0,y0,r):
    mask=cv2.morphologyEx(mask,cv2.MORPH_CLOSE,np.ones((3,1),np.uint8));n,lab,st,cen=cv2.connectedComponentsWithStats(mask,8);out=[]
    for i in range(1,n):
        x,y,w,h,a=map(int,st[i])
        if a<4 or h<.015*r or h>.11*r or w>.050*r or h<1.2*max(w,1):continue
        out.append((x0+x+w/2,y0+y+h-1,float(w),float(h),float(a)))
    return sorted(out)


def _direct(t,mid,r):
    out=[]
    for i in range(1,len(t)-1):
        l,c,rr=t[i-1:i+2];p1=c[0]-l[0];p2=rr[0]-c[0]
        if p1<=.025*r or p2<=.025*r or p1>.11*r or p2>.11*r:continue
        pitch=(p1+p2)/2;reg=abs(p1-p2)/pitch;axis=abs(c[0]-mid)/pitch
        if reg>.30 or axis>.45:continue
        ys=max(l[1],c[1],rr[1])-min(l[1],c[1],rr[1])
        if ys>.055*r:continue
        out.append((reg+.35*axis+.20*ys/r,l,c,rr))
    return min(out,key=lambda z:z[0]) if out else None


def _robust_line(k,y):
    k=np.asarray(k,float);y=np.asarray(y,float);keep=np.ones(len(k),bool)
    for _ in range(3):
        if keep.sum()<3:break
        A=np.column_stack([np.ones(keep.sum()),k[keep]])
        b=np.linalg.lstsq(A,y[keep],rcond=None)[0];res=y-(b[0]+b[1]*k)
        med=np.median(res[keep]);mad=np.median(np.abs(res[keep]-med));lim=max(1.5,2.8*1.4826*mad)
        nk=np.abs(res-med)<=lim
        if nk.sum()<3 or np.array_equal(nk,keep):break
        keep=nk
    A=np.column_stack([np.ones(keep.sum()),k[keep]]);b=np.linalg.lstsq(A,y[keep],rcond=None)[0]
    return float(b[0]),float(b[1]),keep


def _sequence(t,mid,r):
    if len(t)<3:return None
    xs=np.array([q[0] for q in t]);ys=np.array([q[1] for q in t]);out=[]
    for i in range(len(xs)):
      for j in range(i+1,len(xs)):
       dx=xs[j]-xs[i]
       for steps in range(1,5):
        pitch=dx/steps
        if pitch<=.025*r or pitch>.11*r:continue
        x60=xs[i]+round((mid-xs[i])/pitch)*pitch; kk=np.rint((xs-x60)/pitch).astype(int);use=np.abs(kk)<=5
        if use.sum()<3:continue
        ak,ox,oy=kk[use],xs[use],ys[use];res=np.abs(ox-(x60+ak*pitch));inn=res<=.22*pitch
        if inn.sum()<3:continue
        ak,ox,oy=ak[inn],ox[inn],oy[inn]
        if not ((np.any(ak<0) and np.any(ak>0)) or (np.any(ak==0) and (np.any(ak<0) or np.any(ak>0)))):continue
        A=np.column_stack([np.ones(len(ak)),ak.astype(float)]);beta=np.linalg.lstsq(A,ox,rcond=None)[0];x60f,pf=map(float,beta)
        if pf<=.025*r or pf>.11*r:continue
        fit=np.abs(ox-(x60f+ak*pf))/pf
        if fit.max()>.24:continue
        axis=abs(x60f-mid)/pf
        if axis>.60:continue
        y0,yslope,ykeep=_robust_line(ak,oy)
        if ykeep.sum()<3:continue
        y59,y60,y1=y0-yslope,y0,y0+yslope
        if max(y59,y60,y1)-min(y59,y60,y1)>.055*r:continue
        observed=sum(int(np.any(ak==q)) for q in (-1,0,1));ninf=3-observed
        target_obs=3-ninf
        if ninf and len(ak)<4 and target_obs<2:continue
        score=float(np.mean(fit))+.25*axis+.08*ninf+.02*abs(yslope/max(pf,1))
        out.append((score,(x60f-pf,y59),(x60f,y60),(x60f+pf,y1),ninf))
    return min(out,key=lambda z:z[0]) if out else None


def _circle_tangent_landmarks(l,c,rr,cx,cy):
    """Regularise 59/60/1 inner endpoints onto the physical local dial tangent.

    Glare can shorten an individual white tick and move its detected inner endpoint
    several pixels radially. Using those three raw endpoint y values as an angular
    reference can manufacture a large marker rotation. The minute positions are
    fixed at 6-degree intervals, so once their x positions and the physical 60
    endpoint are observed/reconstructed, the local 59-to-1 chord orientation is
    constrained by the dial centre-to-60 radius. Preserve x and 60 y, and only
    regularise the two neighbouring endpoint y coordinates.
    """
    dx=float(c[0]-cx); dy=float(c[1]-cy)
    if abs(dy)<1e-6:
        return l,c,rr
    slope=-dx/dy
    # Grossly oblique tangents indicate a bad circle/60 association. Do not turn
    # that into a confident QC angle.
    if abs(slope)>0.35:
        return None
    y_l=float(c[1] + slope*(l[0]-c[0]))
    y_r=float(c[1] + slope*(rr[0]-c[0]))
    return (float(l[0]),y_l), (float(c[0]),float(c[1])), (float(rr[0]),y_r)


def _minute_ticks(gray,cx,cy,r,tl,tr):
    mid=(tl.x+tr.x)/2;top=(tl.y+tr.y)/2;x0=max(0,int(mid-.42*r));x1=min(gray.shape[1],int(mid+.42*r));y0=max(0,int(top-.20*r));y1=min(gray.shape[0],int(top+.035*r));p=gray[y0:y1,x0:x1]
    if p.size==0:return None
    direct=[];seq=[]
    for m in _tick_masks(p):
        t=_ticks(m,x0,y0,r)
        if len(t)<3:continue
        d=_direct(t,mid,r)
        if d:direct.append(d)
        s=_sequence(t,mid,r)
        if s:seq.append(s)
    if direct:
        _,l,c,rr=min(direct,key=lambda z:z[0]); reg=_circle_tangent_landmarks(l,c,rr,cx,cy)
        if reg is not None:
            l,c,rr=reg;return Point(*l),Point(*rr),Point(*c),False,0
    if seq:
        _,l,c,rr,n=min(seq,key=lambda z:z[0]); reg=_circle_tangent_landmarks(l,c,rr,cx,cy)
        if reg is not None:
            l,c,rr=reg;return Point(*l),Point(*rr),Point(*c),True,n
    return None


def detect_gmt12(bgr):
    if bgr is None or bgr.size==0:return Detection(None,0,"empty image")
    gray=cv2.cvtColor(bgr,cv2.COLOR_BGR2GRAY) if bgr.ndim==3 else bgr.copy();circle=_dial_circle(gray)
    if circle is None:return Detection(None,0,"dial search seed not found")
    cx,cy,r=circle;tri=_triangle_candidate(gray,cx,cy,r)
    if tri is None:return Detection(None,0,"12 triangle physical contour not found")
    tl,tr,tip=tri;t=_minute_ticks(gray,cx,cy,r,tl,tr)
    if t is None:return Detection(None,0,"minute-track sequence not sufficiently constrained")
    ml,mr,m60,inf,n=t;g=Gmt12Geometry(tl,tr,tip,ml,mr,m60);w=np.hypot(tr.x-tl.x,tr.y-tl.y);cent=abs((tl.x+tr.x)/2-m60.x)/max(w,1);conf=max(0,min(1,1-.5*cent-(.10*n if inf else 0)))
    return Detection(g,float(conf),f"minute landmarks reconstructed from robust 6-degree sequence ({n} inferred)" if inf else "")
