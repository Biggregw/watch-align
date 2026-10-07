#!/usr/bin/env python3
"""RESEARCH ONLY. Synthetic checks for date_window.py: rotate the whole crop (window tilt must follow, digit tilt must not),
rotate / shift only the window interior (digit tilt / centring must follow). Usage: date_window_synth_check.py <dir with crops/>"""
import sys, math, cv2, numpy as np
sys.path.insert(0, 'tools/research/alpha96_calibration')
import date_window as dw
D=sys.argv[1]; x0,y0,st=0.22,-0.38,0.0025
for pid in ('b1e67888-image','c743d9fb-image','4967ffd5-image','b0b0c79c-image'):
    g=cv2.imread(f'{D}/crops/{pid}.png',0)
    base,rect,_=dw.measure(g,x0,y0,st)
    if not base['usable']: print(pid,'base withheld',base['reason']); continue
    (cx,cy),(rw,rh),ang=rect
    line=[f"{pid[:8]} base: dx {base['digit_dx']:+.3f} dy {base['digit_dy']:+.3f} dtilt {base['digit_tilt_deg']:+.2f} wtilt {base['window_tilt_deg']:+.2f}"]
    for a in (-2.0,1.0,2.0):     # whole crop rotated clockwise on screen by a (cv2 angle is CCW-positive)
        M=cv2.getRotationMatrix2D((cx,cy),-a,1.0); r=cv2.warpAffine(g,M,g.shape[::-1],flags=cv2.INTER_CUBIC,borderMode=cv2.BORDER_REPLICATE)
        m,_,_=dw.measure(r,x0,y0,st)
        line.append(f"crop+{a:+.0f}: w {m.get('window_tilt_deg',float('nan'))-base['window_tilt_deg']:+.2f} d {m.get('digit_tilt_deg',float('nan'))-base['digit_tilt_deg']:+.2f}" if m['usable'] else f"crop{a:+.0f}: {m['reason'][:20]}")
    # rotate / shift only the window interior (the digit), inside a margin
    x_0,x_1,y_0,y_1=int(cx-rw*0.42),int(cx+rw*0.42),int(cy-rh*0.40),int(cy+rh*0.40)
    for a,sx in ((2.0,0),(-1.0,0),(0,3)):
        r=g.copy(); sub=g[y_0:y_1,x_0:x_1]
        M=cv2.getRotationMatrix2D(((x_1-x_0)/2,(y_1-y_0)/2),-a,1.0); M[0,2]+=sx
        r[y_0:y_1,x_0:x_1]=cv2.warpAffine(sub,M,sub.shape[::-1],flags=cv2.INTER_CUBIC,borderMode=cv2.BORDER_REPLICATE)
        m,_,_=dw.measure(r,x0,y0,st)
        line.append(f"digit rot{a:+.0f} shift{sx}px: d {m['digit_tilt_deg']-base['digit_tilt_deg']:+.2f} dx {m['digit_dx']-base['digit_dx']:+.4f} (exp {sx/rw:+.4f}) w {m['window_tilt_deg']-base['window_tilt_deg']:+.2f}" if m['usable'] else f"digit {a}: {m['reason'][:25]}")
    print(' | '.join(line))
