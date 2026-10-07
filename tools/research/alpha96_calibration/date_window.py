#!/usr/bin/env python3
"""RESEARCH ONLY. Date-digit centring and tilt inside the (magnified) date window, from DateCrop crops.

Input: DateCrop CSV + crops (upright, dial-plane-rectified 3 o'clock region; canonical x right, y down, dial radius 1).
The window is seen through the cyclops, so everything here is a ratio or an angle inside the magnified image:
  window       the bright date-disc region nearest the expected position (rotated-rectangle fit), then each edge refined
               from the brightness profile (sub-pixel half-way crossing between disc white and frame dark)
  window_tilt  deg, window long axis vs the dial horizontal (+ = clockwise)
  digit        dark ink inside the window (excluding a border band); union bounding box in the window frame
  digit_dx/dy  (ink-box centre - window centre) / window width / height, in the window frame (+ = right / down)
  digit_tilt   deg, digit-row tilt vs the window edges (+ = clockwise): line through the two numerals' vertical
               mid-heights (sub-pixel top/bottom of each glyph, shared cap height and baseline); two-digit dates only (single-digit dates report centring, tilt withheld)
Fail-closed (withheld, with reason): no plausible window; window not rectangular (glare / reflection / occlusion);
ink touching the window's top or bottom band (date mid-change, hand, frame shadow); ink too small or too large.
No thresholds on the measurements themselves, no verdicts.

Usage: date_window.py <crops.csv> <crop_dir> <out.csv> [--debug DIR]
"""
import argparse
import csv
import math
import os

import cv2
import numpy as np

EXP_X, EXP_Y = 0.64, 0.0             # expected window centre, canonical (refined from the crops, not a measurement limit)
AREA_R2 = (0.012, 0.09)              # plausible magnified window area, R^2
ASPECT = (1.3, 2.6)                  # width / height
MIN_RECT_FILL = 0.88                 # contour area / rotated-rect area


def to_canon(px, py, x0, y0, step):
    return x0 + (px + 0.5) * step, y0 + (py + 0.5) * step


def find_window(g, x0, y0, step):
    """Bright filled region nearest the expected window position -> (rotated rect, contour, reason)."""
    h, w = g.shape
    ex, ey = (EXP_X - x0) / step - 0.5, (EXP_Y - y0) / step - 0.5
    blur = cv2.GaussianBlur(g, (5, 5), 0)
    # local threshold: the date disc is the brightest flat area near the expected position
    roi = blur[max(0, int(ey - 0.25 / step)):int(ey + 0.25 / step), max(0, int(ex - 0.3 / step)):int(ex + 0.3 / step)]
    t1, _ = cv2.threshold(roi, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    upper = roi[roi > t1]
    # two-level split: the white date disc is the brightest class; the grey frame / lens interior sits between
    t = cv2.threshold(upper.reshape(-1, 1), 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)[0] if upper.size > 50 else t1
    bw = (blur > t).astype(np.uint8) * 255
    bw = cv2.morphologyEx(bw, cv2.MORPH_OPEN, np.ones((3, 3), np.uint8))
    cnts, _ = cv2.findContours(bw, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    best = None
    for c in cnts:
        area = cv2.contourArea(c) * step * step
        if not (AREA_R2[0] <= area <= AREA_R2[1]):
            continue
        (cx, cy), (rw, rh), ang = cv2.minAreaRect(c)
        if rw < rh:
            rw, rh, ang = rh, rw, ang + 90
        d = math.hypot(cx - ex, cy - ey) * step
        if d > 0.2:
            continue
        if best is None or d < best[0]:
            best = (d, c, ((cx, cy), (rw, rh), ang))
    if best is None:
        return None, None, 'no plausible window', t
    _, c, rect = best
    (cx, cy), (rw, rh), ang = rect
    fill = cv2.contourArea(cv2.convexHull(c)) / max(rw * rh, 1)
    if not (ASPECT[0] <= rw / rh <= ASPECT[1]):
        return rect, c, f'window aspect {rw / rh:.2f} implausible', t
    if fill < MIN_RECT_FILL:
        return rect, c, f'window not rectangular (fill {fill:.2f}: glare / reflection / occlusion)', t
    return rect, c, '', t


def window_frame(g, rect):
    """Resample the window upright (its own frame) at the crop's pixel scale."""
    (cx, cy), (rw, rh), ang = rect
    a = math.radians(ang)
    W, H = int(round(rw)), int(round(rh))
    M = np.array([[math.cos(a), -math.sin(a), cx - (W / 2) * math.cos(a) + (H / 2) * math.sin(a)],
                  [math.sin(a), math.cos(a), cy - (W / 2) * math.sin(a) - (H / 2) * math.cos(a)]], np.float32)
    return cv2.warpAffine(g, M, (W, H), flags=cv2.INTER_CUBIC | cv2.WARP_INVERSE_MAP, borderMode=cv2.BORDER_REPLICATE)


def edge_tilt(img, mask, max_dev=12.0):
    """Tilt (deg, + = clockwise on screen) of near-horizontal and near-vertical edges inside mask, from the
    gradient orientation (magnitude-weighted, sub-pixel; not snapped to the pixel grid). Edges more than max_dev
    from the axes (e.g. the diagonal of a 7) are ignored."""
    f = cv2.GaussianBlur(img.astype(np.float32), (3, 3), 0)
    gx = cv2.Sobel(f, cv2.CV_32F, 1, 0, ksize=3); gy = cv2.Sobel(f, cv2.CV_32F, 0, 1, ksize=3)
    mag = np.hypot(gx, gy)
    sel = (mask > 0) & (mag > 0)
    if sel.sum() < 20:
        return float('nan'), 0
    m = mag[sel]; sel &= mag >= np.percentile(m, 60)
    th = np.degrees(np.arctan2(gy[sel], gx[sel]))            # gradient direction, y down
    dev = (th + 45.0) % 90.0 - 45.0                          # deviation from the nearest axis
    keep = np.abs(dev) <= max_dev
    if keep.sum() < 15:
        return float('nan'), int(keep.sum())
    w = mag[sel][keep]
    # a clockwise-rotated edge (screen, y down) rotates its gradient by +angle
    return float((dev[keep] * w).sum() / w.sum()), int(keep.sum())


def row_tilt(img, ink):
    """Digit-row tilt (deg, + = clockwise on screen) for two-digit dates: angle of the line joining the two numerals'
    vertical mid-heights (sub-pixel glyph top / bottom). NaN for single-digit dates."""
    n, lab, stats, _ = cv2.connectedComponentsWithStats(ink)
    comps = [(stats[i][0], stats[i][0] + stats[i][2], i) for i in range(1, n)]
    if not comps:
        return float('nan'), 0
    comps.sort()
    groups = [[comps[0]]]
    for c in comps[1:]:                         # merge components that overlap in x (pieces of one numeral)
        if c[0] <= max(x1 for _, x1, _ in groups[-1]):
            groups[-1].append(c)
        else:
            groups.append([c])
    if len(groups) != 2:
        return float('nan'), len(groups)
    dark = (255.0 - img.astype(np.float32))
    cs = []
    for g in groups:
        x0 = min(a for a, _, _ in g); x1 = max(b for _, b, _ in g)
        m = np.isin(lab, [i for _, _, i in g])
        prof = (dark * m).sum(axis=1)                       # darkness per row of this numeral
        half = 0.5 * prof.max()
        rows = np.nonzero(prof >= half)[0]
        top, bot = rows[0], rows[-1]
        # sub-pixel 50% crossings at the numeral's top and bottom edges
        t = top - (prof[top] - half) / max(prof[top] - prof[top - 1], 1e-6) if top > 0 else float(top)
        b = bot + (prof[bot] - half) / max(prof[bot] - prof[bot + 1], 1e-6) if bot + 1 < len(prof) else float(bot)
        cs.append(((x0 + x1) / 2.0, (t + b) / 2.0))
    (xl, yl), (xr, yr) = cs
    return math.degrees(math.atan2(yr - yl, xr - xl)), 2


def refine_window(g, rect):
    """Refine the window edges from the brightness profile (sub-pixel), searching outward from the outermost ink.
    The threshold region can hug the numerals when the disc darkens toward its edges; the real window edge is where
    brightness crosses halfway between the disc white and the surrounding frame. Returns a new rotated rect or None."""
    (cx, cy), (rw, rh), ang = rect
    ex = ((cx, cy), (rw * 1.5, rh * 1.8), ang)
    big = window_frame(g, ex).astype(np.float32)
    Hb, Wb = big.shape
    ox, oy = (Wb - rw) / 2.0, (Hb - rh) / 2.0                    # initial window inside the expanded frame
    core = big[int(oy + 0.2 * rh):int(oy + 0.8 * rh), int(ox + 0.1 * rw):int(ox + 0.9 * rw)]
    if core.size == 0:
        return None
    white = float(np.percentile(core, 90))
    t = cv2.threshold(core.astype(np.uint8), 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)[0]
    inkm = (big < t)
    inkm[:int(oy), :] = False; inkm[int(oy + rh):, :] = False; inkm[:, :int(ox)] = False; inkm[:, int(ox + rw):] = False
    n, lab, st, _ = cv2.connectedComponentsWithStats(inkm.astype(np.uint8))
    num = [i for i in range(1, n) if st[i][3] >= 0.25 * rh and st[i][4] >= 6]     # numeral-sized components only
    if not num:
        return None
    iy0 = min(st[i][1] for i in num); iy1 = max(st[i][1] + st[i][3] - 1 for i in num)
    ix0 = min(st[i][0] for i in num); ix1 = max(st[i][0] + st[i][2] - 1 for i in num)
    colband = slice(int(ox + 0.1 * rw), int(ox + 0.9 * rw)); rowband = slice(int(iy0), int(iy1) + 1)
    vprof = np.median(big[:, colband], axis=1)                   # median across columns: numerals do not dominate
    hprof = np.median(big[rowband, :], axis=0)

    def crossing(prof, start, step_dir):
        dark = float(min(prof[:max(1, start)].min() if step_dir < 0 else prof[start:].min(), white))
        mid = 0.5 * (white + dark)
        i = start
        for _ in range(6):                                        # begin on bright disc, not on edge shading
            if prof[i] >= mid or not (0 < i - step_dir < len(prof) - 1):
                break
            i -= step_dir
        while 0 < i < len(prof) - 1 and prof[i] >= mid:
            i += step_dir
        if not (0 < i < len(prof) - 1):
            return None
        j = i - step_dir                                          # last bright sample
        a, b = prof[j], prof[i]
        return j + step_dir * (a - mid) / max(a - b, 1e-6)

    top = crossing(vprof, max(1, int(iy0) - 1), -1); bot = crossing(vprof, min(Hb - 2, int(iy1) + 1), +1)
    lef = crossing(hprof, max(1, int(ix0) - 1), -1); rig = crossing(hprof, min(Wb - 2, int(ix1) + 1), +1)
    if None in (top, bot, lef, rig):
        return None
    nw, nh = rig - lef, bot - top
    if nw <= 0 or nh <= 0:
        return None
    # centre shift in the expanded (window-aligned) frame -> crop coordinates
    dxw, dyw = (lef + rig) / 2.0 - Wb / 2.0, (top + bot) / 2.0 - Hb / 2.0
    a = math.radians(ang)
    ncx = cx + dxw * math.cos(a) - dyw * math.sin(a); ncy = cy + dxw * math.sin(a) + dyw * math.cos(a)
    return ((ncx, ncy), (nw, nh), ang)


def edge_lines_tilt(g, rect, ink_top, ink_bot):
    """Window tilt residual (deg, + = clockwise) from the top and bottom window edges themselves: per column (15-85% of the
    width) the sub-pixel half-way crossing above the numerals' top and below their bottom, robust line fit (Theil-Sen)
    to each edge, mean of the two angles. Returns (deg, n_columns) or (nan, n)."""
    (cx, cy), (rw, rh), ang = rect
    big = window_frame(g, ((cx, cy), (rw, rh * 1.6), ang)).astype(np.float32)
    Hb, Wb = big.shape
    oy = (Hb - rh) / 2.0
    t0, t1 = int(oy + ink_top), int(oy + ink_bot)
    white = float(np.percentile(big[int(oy + 0.2 * rh):int(oy + 0.8 * rh), :], 90))

    def edge(col, start, d):
        prof = big[:, col]
        lim = range(start, 0, -1) if d < 0 else range(start, Hb - 1)
        dark = float(min(prof[:start + 1].min() if d < 0 else prof[start:].min(), white)); mid = 0.5 * (white + dark)
        prev = None
        for i in lim:
            if prof[i] < mid and prev is not None:
                a, b = prof[prev], prof[i]
                return prev + d * (a - mid) / max(a - b, 1e-6)
            if prof[i] >= mid:
                prev = i
        return None

    angs = []; n = 0
    for start, d in ((t0, -1), (t1, +1)):
        pts = [(c, edge(c, start, d)) for c in range(int(0.15 * Wb), int(0.85 * Wb))]
        pts = [(c, y) for c, y in pts if y is not None]
        n += len(pts)
        if len(pts) < 10:
            return float('nan'), n
        sl = [(pts[j][1] - pts[i][1]) / (pts[j][0] - pts[i][0]) for i in range(0, len(pts), 2) for j in range(i + 1, len(pts), 3)]
        angs.append(math.degrees(math.atan(float(np.median(sl)))))
    return float(np.mean(angs)), n


def measure(g, x0, y0, step):
    out = dict(usable=False, reason='')
    rect, cnt, why, _ = find_window(g, x0, y0, step)
    if why:
        out['reason'] = why
        return out, rect, None
    ref = refine_window(g, rect)
    if ref is None:
        out['reason'] = 'window edges not found'
        return out, rect, None
    if not (ASPECT[0] <= ref[1][0] / ref[1][1] <= ASPECT[1]):
        out['reason'] = f'refined window aspect {ref[1][0] / ref[1][1]:.2f} implausible'
        return out, ref, None
    rect = ref
    (cx, cy), (rw, rh), ang = rect
    win = window_frame(g, rect)
    H, W = win.shape
    band = max(2, int(round(0.10 * H))); side = max(2, int(round(0.06 * W)))
    inner = win[band:H - band, side:W - side]
    t, _ = cv2.threshold(inner, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)
    ink_full = (win < t).astype(np.uint8)
    n, lab, stats, _ = cv2.connectedComponentsWithStats(ink_full)
    keep = np.zeros_like(ink_full)
    touch_tb = False
    for i in range(1, n):
        x, y, w_, h_, a = stats[i]
        # digit strokes only: tall enough to be a numeral, not a thin edge shadow or a speck
        if a < max(6, 0.002 * W * H) or h_ < 0.25 * H:
            continue
        if x == 0 or x + w_ >= W:                 # touches the window's left/right boundary: frame, not a digit
            continue
        if y <= 1 or y + h_ >= H - 1:             # cut by the window edge: date mid-change or a hand
            touch_tb = True
        keep[lab == i] = 1
    ys, xs = np.nonzero(keep)
    out.update(window_cx=to_canon(cx, cy, x0, y0, step)[0], window_cy=to_canon(cx, cy, x0, y0, step)[1],
               window_w_R=rw * step, window_h_R=rh * step, window_tilt_deg=ang)
    if len(xs) == 0:
        out['reason'] = 'no digit ink found'
        return out, rect, win
    if touch_tb:
        out['reason'] = 'numeral cut by the window edge (date mid-change / hand)'
        return out, rect, win
    bx0, bx1, by0, by1 = xs.min(), xs.max() + 1, ys.min(), ys.max() + 1
    hfrac = (by1 - by0) / H
    if not (0.35 <= hfrac <= 0.92):
        out['reason'] = f'digit height {hfrac:.2f} of window implausible'
        return out, rect, win
    dtilt, dn = row_tilt(win, keep)
    # window edges: bands around the top and bottom boundary of the upright window
    wres, wn = edge_lines_tilt(g, rect, by0, by1 - 1)
    if not (wn >= 15 and math.isfinite(wres)):
        out['reason'] = 'too few clean window edges for tilt'
        return out, rect, win
    if not (dn == 2 and math.isfinite(dtilt)):
        dtilt = float('nan')                       # single-digit date: centring reported, row tilt withheld
    out.update(usable=True, digit_dx=((bx0 + bx1) / 2 - W / 2) / W, digit_dy=((by0 + by1) / 2 - H / 2) / H,
               digit_h_frac=hfrac, digit_w_frac=(bx1 - bx0) / W, digit_tilt_deg=dtilt - wres if math.isfinite(dtilt) else float('nan'), digit_groups=dn,
               window_tilt_deg=ang + wres, ink_px=int(keep.sum()))
    return out, rect, win


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('crops_csv'); ap.add_argument('crop_dir'); ap.add_argument('out_csv'); ap.add_argument('--debug')
    a = ap.parse_args()
    cols = ['photo_id', 'status', 'usable', 'reason', 'window_cx', 'window_cy', 'window_w_R', 'window_h_R', 'window_tilt_deg',
            'digit_dx', 'digit_dy', 'digit_h_frac', 'digit_w_frac', 'digit_tilt_deg', 'digit_groups', 'ink_px']
    if a.debug:
        os.makedirs(a.debug, exist_ok=True)
    with open(a.out_csv, 'w', newline='') as fh:
        wr = csv.DictWriter(fh, fieldnames=cols, extrasaction='ignore'); wr.writeheader()
        for r in csv.DictReader(open(a.crops_csv)):
            if r['status'] != 'accepted':
                wr.writerow(dict(photo_id=r['photo_id'], status=r['status'], usable=False, reason=r['status'])); continue
            g = cv2.imread(os.path.join(a.crop_dir, r['crop']), cv2.IMREAD_GRAYSCALE)
            res, rect, win = measure(g, float(r['x0']), float(r['y0']), float(r['step']))
            row = dict(photo_id=r['photo_id'], status='accepted', **{k: (f'{v:.5f}' if isinstance(v, float) else v) for k, v in res.items()})
            wr.writerow(row)
            if a.debug:
                dbg = cv2.cvtColor(g, cv2.COLOR_GRAY2BGR)
                if rect is not None:
                    box = cv2.boxPoints(rect).astype(int); cv2.polylines(dbg, [box], True, (0, 255, 255) if res['usable'] else (0, 0, 255), 1)
                txt = (f"dx {res['digit_dx']:+.3f} dy {res['digit_dy']:+.3f} t {res['digit_tilt_deg']:+.1f} w {res['window_tilt_deg']:+.1f}"
                       if res['usable'] else res['reason'][:40])
                cv2.putText(dbg, txt, (2, g.shape[0] - 6), 0, 0.33, (0, 255, 0) if res['usable'] else (0, 0, 255), 1)
                cv2.imwrite(os.path.join(a.debug, r['photo_id'] + '.png'), dbg)
            print(r['photo_id'], 'usable' if res['usable'] else 'withheld: ' + res['reason'],
                  *(f"{k}={res[k]:+.3f}" for k in ('digit_dx', 'digit_dy', 'digit_tilt_deg', 'window_tilt_deg') if k in res and res['usable']))


if __name__ == '__main__':
    main()
