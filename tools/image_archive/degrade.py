"""Controlled counterexamples for the face-on-v3 screen (read-only research).

    python -m tools.image_archive.degrade evidence.jsonl items.csv out.jsonl [--max 8] [--pinned docs/image-archive/degrade-pinned.csv]

Takes reviewed near-frontal photographs that the screen admits (or, with --pinned, the fixed list
of originals the first counterexample run used, so a retest repeats exactly the same copies), checks that the full
Screener (FaceOnAnalyzer, the production analysis) admits the original too (parity
with the offline evaluation), then makes known-bad copies of each and screens them:

  blur_s3 / blur_s8   Gaussian blur, sigma 3 / 8 px at the original's scale
  hand_4              a dark hour-hand-sized bar from the centre fully across the 4 o'clock marker
  glare               a near-opaque bright veil over a third of the dial, across the 9-11 markers
  crop_12             the frame cut through the 12 o'clock marker (incomplete dial)
  small_150           resampled to a 150 px dial radius (below the frozen 200 px floor)

Extended obstruction set (owner, 21 Oct 2026: how robust is the marker obstruction check?):

  hand_8              the same dark bar across the 8 o'clock marker
  hand_4_narrow       a narrower dark bar (0.08 R, minute-hand width) across the 4 o'clock marker
  steel_2 / steel_10  a polished-steel-grey bar (0.14 R) across the 2 / 10 o'clock marker

For every hand-type copy the targeted marker's own evidence (state, gate, outline coverage,
edge fit, hand check, position change) is logged next to the original's.

Every copy must be FAIL or INCONCLUSIVE; one admitted copy is a false accept.
Prints DEGRADE lines; no image is kept or uploaded.
"""
from __future__ import annotations

import argparse
import csv
import json
import math
from collections import Counter
from hashlib import sha256
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageOps

from .faceon import Driver, FaceOnAnalyzer, MODELS, PROFILE, evaluate
from .screening import REQUIRED_CHECKS, Screener


def offline_pass(e):
    if e.get('error') or min(e.get('orig_w') or 0, e.get('orig_h') or 0) < 400:
        return False
    c, m = evaluate(e)
    return all(c.get(k) is True for k in REQUIRED_CHECKS) and all(v is True for v in m.values())


def canon(h, x, y):
    w = h[6] * x + h[7] * y + h[8]
    return (h[0] * x + h[1] * y + h[2]) / w, (h[3] * x + h[4] * y + h[5]) / w


def variants(img, h, k):
    """Known-bad copies; h maps canonical dial coordinates to analysed pixels, k = original px per analysed px."""
    P = lambda r, deg: tuple(v * k for v in canon(h, r * math.sin(math.radians(deg)), -r * math.cos(math.radians(deg))))
    cx, cy = P(0, 0)
    rad = math.dist((cx, cy), P(1, 0))
    out = {'blur_s3': img.filter(ImageFilter.GaussianBlur(3)), 'blur_s8': img.filter(ImageFilter.GaussianBlur(8))}
    hand = img.copy()
    d = ImageDraw.Draw(hand)
    # an hour-hand-sized dark bar (0.14 R wide) from the centre fully across the 4 o'clock marker
    a, b = P(0.0, 120), P(0.92, 120)
    d.line([a, b], fill=(38, 38, 40), width=max(8, int(0.14 * rad)))
    out['hand_4'] = hand
    for name, deg, colour, width in (('hand_8', 240, (38, 38, 40), 0.14), ('hand_4_narrow', 120, (38, 38, 40), 0.08),
                                     ('steel_2', 60, (190, 192, 196), 0.14), ('steel_10', 300, (190, 192, 196), 0.14)):
        v = img.copy()
        ImageDraw.Draw(v).line([P(0.0, deg), P(0.92, deg)], fill=colour, width=max(6, int(width * rad)))
        out[name] = v
    glare = img.copy().convert('RGBA')
    veil = Image.new('RGBA', glare.size, (0, 0, 0, 0))
    # a near-opaque veil over about a third of the dial (0.65 R x 0.5 R half-axes), across the 9-11 markers
    gx, gy = P(0.6, 300)
    ImageDraw.Draw(veil).ellipse([gx - 0.65 * rad, gy - 0.5 * rad, gx + 0.65 * rad, gy + 0.5 * rad], fill=(250, 250, 250, 235))
    out['glare'] = Image.alpha_composite(glare, veil.filter(ImageFilter.GaussianBlur(0.05 * rad))).convert('RGB')
    top = P(0.80, 0)
    # keep everything below a horizontal cut through the 12 marker (rotate first so 12 is up in the frame)
    turn = math.degrees(math.atan2(top[0] - cx, -(top[1] - cy)))
    up = img.rotate(turn, center=(cx, cy), resample=Image.Resampling.BICUBIC, expand=False)
    out['crop_12'] = up.crop((0, int(cy - 0.80 * rad), up.size[0], up.size[1]))
    s = 150.0 / rad
    out['small_150'] = img.resize((max(1, round(img.size[0] * s)), max(1, round(img.size[1] * s))), Image.Resampling.LANCZOS)
    return out


TARGET = {'hand_4': 4, 'hand_8': 8, 'hand_4_narrow': 4, 'steel_2': 2, 'steel_10': 10}
MARKER_KEYS = ('state', 'cover', 'fit_rms_px', 'bad_frac', 'hand_confirmed', 'uncertain', 'pos_pct', 'side_pct', 'inout_pct')


def marker(decision, hour):
    ev = (decision.metrics.get('analysis') or {}).get('evidence') or {}
    m = (ev.get('markers') or {}).get(hour) or (ev.get('markers') or {}).get(str(hour)) or {}
    out = {k: m.get(k) for k in MARKER_KEYS}
    out['withheld'] = (ev.get('withheld') or {}).get(hour) or (ev.get('withheld') or {}).get(str(hour))
    # the screen's own per-marker verdict: True usable, False obstructed / unreliable, None untested
    out['archive_usable'] = ((decision.metrics.get('analysis') or {}).get('markers') or {}).get(str(hour))
    # the photograph's SHA-bound eligibility mask: the obstructed marker must never be ELIGIBLE in it
    out['mask_status'] = ((decision.marker_eligibility.get('markers') or {}).get(str(hour)) or {}).get('status')
    out['credible'] = hour in (ev.get('credible') or [])
    out['reliable'] = hour in (ev.get('reliable') or [])
    return out


def run(evidence_path, items, out, limit, pinned=None):
    paths = {r['photo_id']: r for r in csv.DictReader(open(items))}
    if pinned:
        want = {r['sha256'] for r in csv.DictReader(open(pinned))}
        best = {}
        for e in map(json.loads, open(evidence_path)):
            if e['sha256'] in want and e['photo_id'] in paths and not e.get('error'):
                best.setdefault(e['sha256'], e)
        chosen = sorted(best.values(), key=lambda e: e['photo_id'])
    else:
        chosen = [e for e in map(json.loads, open(evidence_path))
                  if e.get('set', '').startswith('reviewed') and e.get('near_frontal') == 'yes' and offline_pass(e)]
        chosen.sort(key=lambda e: e['photo_id'])
        chosen = chosen[:limit]
    driver = Driver()
    screen = Screener(FaceOnAnalyzer(driver), validated_profiles={PROFILE + '/' + m for m in set(MODELS.values())})
    rows, counts, masked = [], Counter(), Counter()
    for e in chosen:
        path = paths[e['photo_id']]['path']
        data = Path(path).read_bytes()
        d = screen.assess_bytes(data, e['family'])
        dm = screen.assess_bytes(data, e['family'], 'marker_masked')
        rows.append({'photo_id': e['photo_id'], 'variant': 'original', 'status': d.status, 'reasons': d.reasons,
                     'masked_status': dm.status, 'eligible': list(dm.usable_marker_ids),
                     'markers': {h: marker(d, h) for h in sorted(set(TARGET.values()))}})
        original = rows[-1]
        counts[('original', d.status)] += 1
        masked[('original', dm.status)] += 1
        ev = driver.run(MODELS[e['family']], [('o', path)]).get('o') or {}
        if not ev.get('homography'):
            continue
        with Image.open(path) as im:
            img = ImageOps.exif_transpose(im).convert('RGB')
        k = img.size[0] / float(ev['work_w'])
        work = Path(out).parent / 'degrade-tmp'
        work.mkdir(exist_ok=True)
        for name, v in variants(img, ev['homography'], k).items():
            p = work / (e['photo_id'] + '_' + name + '.png')
            v.save(p)
            dv = screen.assess_bytes(p.read_bytes(), e['family'])
            dvm = screen.assess_bytes(p.read_bytes(), e['family'], 'marker_masked')
            p.unlink()
            masked[(name, dvm.status)] += 1
            row = {'photo_id': e['photo_id'], 'variant': name, 'status': dv.status, 'masked_status': dvm.status,
                   'eligible': list(dvm.usable_marker_ids),
                   'reasons': [r for r in dv.reasons if not r.startswith('inconclusive_marker_')][:12]}
            if name in TARGET:
                row['target'] = TARGET[name]
                row['marker'] = marker(dv, TARGET[name])
                row['marker']['mask_status'] = marker(dvm, TARGET[name])['mask_status']
                row['marker_original'] = original['markers'][TARGET[name]]
            rows.append(row)
            counts[(name, dv.status)] += 1
    with open(out, 'w') as f:
        for r in rows:
            f.write(json.dumps(r) + '\n')
    for r in rows:
        print('DEGRADE ' + json.dumps(r, separators=(',', ':')))
    for (name, status), n in sorted(counts.items()):
        print('DEGRADE-SUMMARY %s %s %d' % (name, status, n))
    # marker level, independent of whether the whole original was admitted: a bar over a marker that was usable in
    # the original must make it not usable
    level = Counter()
    for r in rows:
        if 'target' in r:
            if r['marker_original'].get('archive_usable') is not True:
                level[(r['variant'], 'original marker not usable')] += 1
            else:
                level[(r['variant'], {True: 'MISSED', False: 'detected', None: 'withheld (untested)'}[r['marker'].get('archive_usable')])] += 1
    for (name, what), n in sorted(level.items()):
        print('DEGRADE-MARKER %s %s %d' % (name, what, n))
    print('DEGRADE-MARKER missed obstructions %d of %d' % (
        sum(n for (_, w), n in level.items() if w == 'MISSED'),
        sum(n for (_, w), n in level.items() if w != 'original marker not usable')))
    # per-marker masked admission: a photo-wide degradation must not be admitted at all, and a bar over a marker that
    # was ELIGIBLE in the original must leave that marker out of the mask
    for (name, status), n in sorted(masked.items()):
        print('DEGRADE-MASKED %s %s %d' % (name, status, n))
    leaks = [r for r in rows if 'target' in r and r['marker_original'].get('mask_status') == 'ELIGIBLE'
             and r['marker'].get('mask_status') == 'ELIGIBLE']
    tested = [r for r in rows if 'target' in r and r['marker_original'].get('mask_status') == 'ELIGIBLE']
    print('DEGRADE-MASKED obstructed marker left ELIGIBLE %d of %d' % (len(leaks), len(tested)))
    photo_wide = sum(n for (name, status), n in masked.items() if name not in TARGET and name != 'original' and status == 'pass')
    print('DEGRADE-MASKED photo-wide degradations admitted %d' % photo_wide)
    false_accepts = sum(n for (name, status), n in counts.items() if name != 'original' and status == 'pass')
    print('DEGRADE-SUMMARY false accepts %d; originals admitted %d of %d' % (
        false_accepts, counts[('original', 'pass')], len(chosen)))
    return rows


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('evidence')
    ap.add_argument('items')
    ap.add_argument('out')
    ap.add_argument('--max', type=int, default=8)
    ap.add_argument('--pinned')
    a = ap.parse_args()
    run(a.evidence, a.items, a.out, a.max, a.pinned)
