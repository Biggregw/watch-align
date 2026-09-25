"""Research-only automatic landmarks for the human GMT 12 QC contract.

This detector is deliberately narrow: near-frontal QC-style images only.  It
exists to prove that software can recover the same *physical features* a human
marked before any baseline, thresholds, perspective correction or production
QC is attempted.

Outputs map directly to human_qc_geometry.Gmt12Geometry:
  triangle_top_left/right  outer triangle corners nearest minute track
  triangle_tip             inward point nearest printed coronet
  minute_inner_left/right  local line through inner ends of neighbouring ticks
  minute_60_center          centre of the observed 60/top tick

A dial circle is allowed only as a search-window seed.  It is never a reported
landmark and never substitutes for the observed 60 tick.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Optional
import cv2
import numpy as np

from human_qc_geometry import Gmt12Geometry, Point, measure_gmt12


@dataclass(frozen=True)
class Detection:
    geometry: Optional[Gmt12Geometry]
    confidence: float
    reason: str = ""


def _dial_circle(gray: np.ndarray):
    h, w = gray.shape[:2]
    blur = cv2.GaussianBlur(gray, (7, 7), 1.4)
    circles = cv2.HoughCircles(
        blur, cv2.HOUGH_GRADIENT, dp=1.2, minDist=min(h, w) * .25,
        param1=100, param2=35,
        minRadius=int(min(h, w) * .20), maxRadius=int(min(h, w) * .48))
    if circles is None:
        return None

    # Hough returns many large non-dial circles on real watch photographs.
    # A usable search seed must be substantially contained in the image and
    # near the image centre in BOTH axes.  The previous score considered x
    # only and therefore selected circles centred on the top/bottom border.
    # This remains only a search-window seed, never QC evidence.
    candidates = []
    for c in circles[0]:
        x, y, r = map(float, c)
        containment = min(x, y, w - x, h - y) / max(r, 1.0)
        if containment < .85:
            continue
        centre_distance = float(np.hypot(x - w / 2.0, y - h / 2.0))
        score = r - .8 * centre_distance
        candidates.append((score, x, y, r))
    if not candidates:
        return None
    _, x, y, r = max(candidates, key=lambda z: z[0])
    return x, y, r


def _bright_components(gray: np.ndarray, roi):
    x0, y0, x1, y1 = roi
    patch = gray[y0:y1, x0:x1]
    if patch.size == 0:
        return []
    _, bw = cv2.threshold(patch, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    bw = cv2.morphologyEx(bw, cv2.MORPH_OPEN, np.ones((2, 2), np.uint8))
    contours, _ = cv2.findContours(bw, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    out = []
    for c in contours:
        a = cv2.contourArea(c)
        if a < 4:
            continue
        p = c.reshape(-1, 2).astype(float)
        p[:, 0] += x0; p[:, 1] += y0
        out.append((a, p))
    return out


def _triangle_candidate(gray, cx, cy, r):
    roi = (max(0, int(cx-.24*r)), max(0, int(cy-.88*r)),
           min(gray.shape[1], int(cx+.24*r)), min(gray.shape[0], int(cy-.43*r)))
    candidates = []
    for area, p in _bright_components(gray, roi):
        xspan = np.ptp(p[:,0]); yspan = np.ptp(p[:,1])
        if xspan < .06*r or yspan < .10*r or yspan > .42*r:
            continue
        hull = cv2.convexHull(p.astype(np.float32)).reshape(-1,2)
        peri = cv2.arcLength(hull.reshape(-1,1,2), True)
        poly = cv2.approxPolyDP(hull.reshape(-1,1,2), .055*peri, True).reshape(-1,2)
        if len(poly) < 3 or len(poly) > 6:
            continue
        mx, my = p.mean(axis=0)
        score = area - 2.0*abs(mx-cx) + .25*yspan
        candidates.append((score, p))
    if not candidates:
        return None
    p = max(candidates, key=lambda z:z[0])[1]
    y_min, y_max = p[:,1].min(), p[:,1].max()
    outer = p[p[:,1] <= y_min + .28*(y_max-y_min)]
    if len(outer) < 2:
        return None
    left = outer[np.argmin(outer[:,0])]
    right = outer[np.argmax(outer[:,0])]
    inward = p[p[:,1] >= y_max - .12*(y_max-y_min)]
    tip = inward.mean(axis=0)
    return Point(*left), Point(*right), Point(*tip)


def _minute_ticks(gray, cx, cy, r, tri_left: Point, tri_right: Point):
    roi = (max(0, int(cx-.24*r)), max(0, int(cy-.99*r)),
           min(gray.shape[1], int(cx+.24*r)), min(gray.shape[0], int(cy-.73*r)))
    ticks=[]
    for area,p in _bright_components(gray, roi):
        x0,x1=p[:,0].min(),p[:,0].max(); y0,y1=p[:,1].min(),p[:,1].max()
        ww=max(1.,x1-x0); hh=max(1.,y1-y0)
        if hh < .018*r or hh > .16*r or ww > .10*r:
            continue
        ticks.append((float((x0+x1)/2), float(y1), float(area), p))
    if len(ticks) < 3:
        return None
    ticks.sort(key=lambda t:t[0])
    central=min(ticks, key=lambda t:abs(t[0]-cx))
    lefts=[t for t in ticks if t[0] < central[0]-.01*r]
    rights=[t for t in ticks if t[0] > central[0]+.01*r]
    if not lefts or not rights:
        return None
    left=max(lefts,key=lambda t:t[0]); right=min(rights,key=lambda t:t[0])
    return (Point(left[0],left[1]), Point(right[0],right[1]),
            Point(central[0],central[1]))


def detect_gmt12(bgr: np.ndarray) -> Detection:
    if bgr is None or bgr.size == 0:
        return Detection(None,0.0,"empty image")
    gray=cv2.cvtColor(bgr,cv2.COLOR_BGR2GRAY) if bgr.ndim==3 else bgr.copy()
    circle=_dial_circle(gray)
    if circle is None:
        return Detection(None,0.0,"dial search seed not found")
    cx,cy,r=circle
    tri=_triangle_candidate(gray,cx,cy,r)
    if tri is None:
        return Detection(None,0.0,"12 triangle physical contour not found")
    tl,tr,tip=tri
    ticks=_minute_ticks(gray,cx,cy,r,tl,tr)
    if ticks is None:
        return Detection(None,0.0,"60/neighbour minute ticks not all observed")
    ml,mr,m60=ticks
    g=Gmt12Geometry(tl,tr,tip,ml,mr,m60)
    width=((tr.x-tl.x)**2+(tr.y-tl.y)**2)**.5
    centred=abs(((tl.x+tr.x)/2)-m60.x)/max(width,1.)
    conf=float(max(0.0,min(1.0,1.0-.5*centred)))
    return Detection(g,conf,"")


def render_detection(bgr: np.ndarray, d: Detection) -> np.ndarray:
    out=bgr.copy()
    if d.geometry is None:
        return out
    g=d.geometry
    def pt(p): return (int(round(p.x)),int(round(p.y)))
    cv2.line(out,pt(g.minute_inner_left),pt(g.minute_inner_right),(0,255,255),2)
    cv2.line(out,pt(g.triangle_top_left),pt(g.triangle_top_right),(0,255,0),2)
    mid=Point((g.triangle_top_left.x+g.triangle_top_right.x)/2,
              (g.triangle_top_left.y+g.triangle_top_right.y)/2)
    cv2.line(out,pt(mid),pt(g.triangle_tip),(255,255,0),2)
    for p in (g.triangle_top_left,g.triangle_top_right,g.triangle_tip,
              g.minute_inner_left,g.minute_inner_right,g.minute_60_center):
        cv2.circle(out,pt(p),5,(0,0,255),-1)
    return out
