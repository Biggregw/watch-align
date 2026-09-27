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
    """Pick the seed dial circle from Hough's own candidates.

    cv2.HoughCircles already returns candidates ordered by accumulator
    strength (index 0 = the circle with the most consistent edge evidence in
    the image), which is the most direct, purely geometric signal available
    for "is this really a circle boundary." The content-based re-score below
    (radius, darkness of the interior, closeness to the frame centre) is
    still useful to break ties between similarly-strong candidates, but it
    must not casually override a much stronger accumulator rank: a small,
    off-dial circle sampling only the near-black area around the hands hub
    can score deceptively high on "darkness", and a well-centred photo isn't
    guaranteed (nor is a mis-centred one wrong). Two independent real
    failures observed with this same shape: the correct dial circle was
    Hough's rank-0 candidate, but a smaller circle around the hands hub won
    the content re-score by sampling purely black interior with no
    hands/index/text to dilute it, and by sitting closer to the frame
    centre. The second, larger-margin failure exposed *why* "closer to
    frame centre" is not just an occasional coincidence but a structurally
    weak signal for this specific confusion: the hands hub sits almost
    exactly at the dial's own true centre by construction, so whenever the
    watch is reasonably centred in the photo, a hands-hub circle is nearly
    as close to the frame centre as the real dial circle is -- the distance
    term cannot reliably tell them apart. The rank penalty is set well
    above the largest verified real-photo margin observed for this
    confusion, so a genuinely correct rank-0 circle is not overridden by it.
    """
    h,w=gray.shape[:2]; blur=cv2.GaussianBlur(gray,(7,7),1.4)
    cs=cv2.HoughCircles(blur,cv2.HOUGH_GRADIENT,1.2,min(h,w)*.25,param1=100,param2=35,minRadius=int(min(h,w)*.20),maxRadius=int(min(h,w)*.48))
    if cs is None: return None
    out=[]
    for rank,(x,y,r) in enumerate(cs[0]):
        x,y,r=map(float,(x,y,r)); containment=min(x,y,w-x,h-y)/max(r,1)
        if containment<.82: continue
        yy,xx=np.ogrid[:h,:w]; mask=(xx-x)**2+(yy-y)**2 < (.68*r)**2
        vals=gray[mask]
        if vals.size<100: continue
        dark=255-float(np.median(vals)); d=float(np.hypot(x-w/2,y-h/2))
        out.append((rank,x,y,r,dark,d))
    return _pick_dial_circle(out)


def _pick_dial_circle(candidates):
    """Score already-filtered (rank,x,y,r,dark,d) circle candidates and
    return the winning (x,y,r), or None. Separated from _dial_circle purely
    so the rank-vs-content-heuristic trade-off can be tested directly with
    plain numbers instead of needing to coax cv2.HoughCircles into producing
    a specific ranking on a synthetic image."""
    if not candidates: return None
    scored=[(r-.65*d+.80*dark-40.0*rank,x,y,r) for rank,x,y,r,dark,d in candidates]
    _,x,y,r=max(scored); return x,y,r


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
    """Locate the physical 12-triangle's outer corners and tip.

    A watch hand (hour/minute/GMT) crosses near 12 in a large fraction of
    real photos -- there is no way to choose a photo moment that avoids it.
    Where a hand's edge meets the triangle's bright silhouette, the convex
    hull can pick up one extra vertex along that edge (anti-aliasing/colour
    bleed at the boundary, not a real corner) instead of simplifying back to
    a clean 3-vertex triangle -- a real failure: a hand crossing the right
    edge left a 4-vertex hull and the detector reported no triangle at all,
    although the top-left, top-right and tip corners were all still plainly
    the shape's three extreme points. MAX_POLY_VERTICES allows a bounded
    number of such minor extra vertices (still far short of an unrelated
    blob's typical complexity) and always derives the landmarks from the
    shape's own extremes (topmost two, then the single bottommost point),
    so an extra vertex from a hand crossing an edge is simply not among
    them. This does not loosen the shape-plausibility checks below in any
    way -- a genuinely non-triangular blob is still rejected by those.

    Two further real failures, both traced to this ROI's top edge sitting
    too close to the triangle's true top corners on a meaningful fraction
    of real photos (confirmed directly: at the old cy-.72*r margin, 3 of 13
    already-validated real photos had their triangle's true top edge
    clipped by the ROI boundary -- the resulting fragment still happened to
    look like a plausible, correctly-proportioned triangle and passed every
    shape check, silently reporting a top edge roughly a third of the way
    down the real shape instead of its actual top, and a materially wrong
    clearance measurement, for photos that had previously been treated as
    clean passes). Widening the margin to cy-.85*r fixed those, but a
    smaller-dial photo then hit a second, subtler variant: its true,
    correct, complete triangle top edge landed by pixel-rounding
    coincidence exactly on that boundary, so the (correct) candidate was
    rejected as if clipped, purely because "touches the edge" doesn't
    distinguish a 1px graze from genuine truncation. The margin is now
    cy-.90*r -- confirmed to give that photo 16px of real headroom (not
    just a relocated coincidence) while every previously-verified photo's
    corners and measurements are unchanged.

    Widening the margin alone is not enough for every case, though: when an
    hour/GMT hand points at or near 12 at the moment of capture, its lume
    can optically merge with the triangle's own lume along their whole
    shared edge (not just cross it), producing one bright blob spanning
    from the minute track down past the hands hub -- far taller than a
    real triangle and correctly rejected by the size checks below once the
    ROI is wide enough to reveal its true, implausible extent, rather than
    clipping it into an accidentally plausible-looking fragment.
    _touches_roi_edge is kept as a defence-in-depth safety net for any
    remaining case where even this wider margin isn't enough: a candidate
    that still reaches the ROI boundary is reported as UNASSESSABLE rather
    than risk a repeat of the same silent-clipping failure at a new margin.
    """
    MAX_POLY_VERTICES=6
    roi=(max(0,int(cx-.18*r)),max(0,int(cy-.90*r)),min(gray.shape[1],int(cx+.18*r)),min(gray.shape[0],int(cy-.30*r)))
    out=[]
    for area,p in _bright_components(gray,roi):
        if _touches_roi_edge(p,roi):continue
        xs,ys=float(np.ptp(p[:,0])),float(np.ptp(p[:,1]))
        if xs<.10*r or xs>.28*r or ys<.12*r or ys>.30*r:continue
        hull=cv2.convexHull(p.astype(np.float32).reshape(-1,1,2)); per=cv2.arcLength(hull,True)
        if per<=0:continue
        poly=cv2.approxPolyDP(hull,.03*per,True).reshape(-1,2)
        corners=_polygon_to_triangle_corners(poly,MAX_POLY_VERTICES,cx,r)
        if corners is None:continue
        left,right,tip=corners
        out.append((area,left,right,tip))
    if not out:return None
    _,l,rr,t=max(out,key=lambda z:z[0]);return Point(*map(float,l)),Point(*map(float,rr)),Point(*map(float,t))


def _touches_roi_edge(p,roi):
    """True if a bright component's bounding box reaches the search ROI's
    boundary on any side -- evidence the shape was clipped by the ROI
    itself rather than fully contained within it. Separated from
    _triangle_candidate purely so this can be tested directly with plain
    coordinates."""
    x0,y0,x1,y1=roi
    return (p[:,0].min()<=x0 or p[:,0].max()>=x1-1 or
            p[:,1].min()<=y0 or p[:,1].max()>=y1-1)


def _polygon_to_triangle_corners(poly,max_vertices,cx,r):
    """Derive (left,right,tip) triangle corners from a hull polygon, or None
    if it isn't a plausible triangle. Separated from _triangle_candidate so
    the vertex-count/extremes trade-off (see that function's docstring) can
    be tested directly against a fixed polygon instead of needing to
    reproduce a specific contour-extraction result from raw pixels."""
    poly=np.asarray(poly)
    if len(poly)<3 or len(poly)>max_vertices:return None
    q=poly[np.argsort(poly[:,1])]; upper=q[:2]; tip=q[-1]
    left,right=sorted(upper,key=lambda z:float(z[0]))
    width=float(right[0]-left[0]); height=float(tip[1]-(left[1]+right[1])/2)
    if width<=0 or height<=0 or not(.55<=width/height<=1.65):return None
    mid=(float(left[0])+float(right[0]))/2
    if abs(float(tip[0])-mid)>.22*width or abs(mid-cx)>.10*r:return None
    return left,right,tip


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
        if ninf and len(ak)<4 and observed<2:continue
        # axis agreement is a genuine independent geometric cross-check (x60f
        # derived purely from tick spacing, compared against mid from the
        # separately-detected triangle) -- a coincidentally-good tick-pitch
        # fit is unlikely to also land close to mid by chance. ninf is only a
        # data-completeness proxy. A real failure with the old .25 weight:
        # a poorly-anchored fit built mostly from far-out, low-confidence
        # ticks (ninf=2, axis=0.151, extrapolated over 2-4 pitches) beat a
        # well-centred, visually-correct fit (ninf=3, axis=0.001) purely
        # because one fewer inferred point (.08) outweighed a much larger
        # axis disagreement (.25*0.151=.038) -- axis's weight was too small
        # for its own bound (max .25*.60=.15) to ever dominate a single ninf
        # step. Raised so a strong axis disagreement can outrank a smaller
        # ninf difference, verified against this exact real failure.
        score=float(np.mean(fit))+.80*axis+.08*ninf+.02*abs(yslope/max(pf,1))
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


def _trim_bezel_band(gray,x0,y0,x1,y1):
    """Trim the outer edge of a minute-track search band past any bright
    metal bezel/rehaut ring it contains.

    The band is sized generously (top-.20*r) so it does not miss ticks that
    sit further from the triangle. On some photos that margin reaches far
    enough out to include the bright bezel/rehaut ring above the dial. A
    single Otsu threshold over the whole band is then dominated by that much
    brighter region and can fail to separate the thinner, dimmer ticks from
    the dark dial background underneath it -- not missing evidence, just
    mis-thresholded evidence.

    Row-wise fraction of near-white pixels (>140) is a bezel-vs-dial signal
    robust to the bezel's curvature (it need not span the row's full width).
    A real bezel/rehaut ring is many rows tall (spans a visible arc of the
    ring). A single minute tick is only a few rows tall, but on a wide band
    (spanning several tick positions either side of 60) its curved
    neighbours can still make one row's bright-pixel fraction spike as high
    as a real bezel row -- a real failure shape: a 3-row tick spike was
    trimmed away as if it were bezel, discarding the very evidence the band
    exists to find. Require a bright run of at least MIN_BEZEL_RUN_ROWS
    contiguous rows before treating it as bezel, and only trim past a run
    found in the band's outer half -- otherwise the band has no significant
    bezel content and is left untouched, so this never narrows a search
    that did not need it.

    A bezel's brightness does not end cleanly at the row profile's cutoff
    crossing: on a real photo, tick segmentation stayed unreliable for
    roughly a further ten rows past that crossing (residual bezel/rehaut
    edge influence still skewing the per-band Otsu/CLAHE threshold), then
    became reliable again over a wide, stable range. TRIM_MARGIN_FRAC is set
    inside that verified stable range rather than right at the crossing, and
    _minute_ticks additionally still searches the untrimmed band alongside
    this one, so an imperfect margin on some other photo degrades gracefully
    to the pre-trim behaviour instead of silently losing evidence.

    Both thresholds are expressed as fractions of the band's own height
    (not fixed pixel-row counts) so this scales with dial resolution instead
    of staying calibrated to the one real photo it was tuned against. The
    fractions were chosen to reproduce the originally-verified 8-row/15-row
    behaviour exactly at that photo's band height (109 rows) -- a much
    lower-resolution photo (few dozen rows) no longer risks a fixed margin
    consuming most of the searchable band, and a much higher-resolution one
    no longer risks a margin far too small to clear the bezel's influence.
    """
    MIN_BEZEL_RUN_FRAC=8/109
    TRIM_MARGIN_FRAC=15/109
    h=y1-y0
    if h<12:return y0
    MIN_BEZEL_RUN_ROWS=max(3,round(MIN_BEZEL_RUN_FRAC*h))
    TRIM_MARGIN_ROWS=max(3,round(TRIM_MARGIN_FRAC*h))
    band=gray[y0:y1,x0:x1].astype(np.float32)
    frac=(band>140).mean(axis=1)
    peak=float(frac.max())
    if peak<0.20:return y0
    cutoff=0.5*peak
    run_end=-1;i=0
    while i<h//2:
        if frac[i]>=cutoff:
            j=i
            while j<h//2 and frac[j]>=cutoff:j+=1
            if j-i>=MIN_BEZEL_RUN_ROWS:run_end=j-1
            i=j
        else:
            i+=1
    if run_end<0:return y0
    return y0+min(run_end+TRIM_MARGIN_ROWS,h-1)


def _minute_ticks(gray,cx,cy,r,tl,tr):
    mid=(tl.x+tr.x)/2;top=(tl.y+tr.y)/2;x0=max(0,int(mid-.42*r));x1=min(gray.shape[1],int(mid+.42*r));y0_full=max(0,int(top-.20*r));y1=min(gray.shape[0],int(top+.035*r))
    y0_trim=_trim_bezel_band(gray,x0,y0_full,x1,y1)
    # Search both the full band and, when a bright bezel run was found, the
    # trimmed band, and pool candidates from whichever actually segments
    # ticks cleanly. Trimming can itself land a few rows short of or past
    # the mask/threshold pipeline's sweet spot for a clean direct triple
    # (observed on a real image), so relying on the trimmed band alone can
    # lose a result the untrimmed band would have found, and vice versa --
    # pooling both keeps the existing direct-preferred / lowest-score
    # selection below in charge, rather than trusting one exact boundary.
    bands=[y0_full] if y0_trim==y0_full else [y0_full,y0_trim]
    direct=[];seq=[]
    for y0 in bands:
        p=gray[y0:y1,x0:x1]
        if p.size==0:continue
        for m in _tick_masks(p):
            t=_ticks(m,x0,y0,r)
            if len(t)<3:continue
            d=_direct(t,mid,r)
            if d:direct.append(d)
            s=_sequence(t,mid,r)
            if s:seq.append(s)
    best=_first_regularized(direct,cx,cy)
    if best is not None:
        (l,c,rr),_=best;return Point(*l),Point(*rr),Point(*c),False,0
    best=_first_regularized(seq,cx,cy)
    if best is not None:
        (l,c,rr),(n,)=best;return Point(*l),Point(*rr),Point(*c),True,n
    return None


def _first_regularized(candidates,cx,cy):
    """Try circle-tangent regularisation on each candidate in ascending
    score order, returning ((left,center,right), extra_fields) for the
    first one that passes, or None if none do.

    The single best-scored candidate within a family (direct or sequence)
    can still fail _circle_tangent_landmarks on its own -- an oblique
    circle/60 association -- even when a lower-ranked candidate from the
    same family is perfectly good. Only stopping the family search once no
    candidate passes (rather than giving up after the very first one)
    avoids discarding a genuinely good direct detection in favour of a
    weaker or absent sequence fallback purely because of how one candidate
    happened to score on unrelated regularity/centring terms.
    """
    for score,l,c,rr,*rest in sorted(candidates,key=lambda z:z[0]):
        reg=_circle_tangent_landmarks(l,c,rr,cx,cy)
        if reg is not None:
            return reg,tuple(rest)
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
