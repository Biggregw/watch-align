"""EXPERIMENTAL 12-triangle top-edge measurement vs the existing whole-outline fit (read-only research).

Usage: compare.py dump.csv [synth.csv] [label]

dump.csv / synth.csv: tools/desktop-harness/drivers/TopEdge.java output.
  old  = existing production radial (centre of the rigid outline fitted by MetalFit), % R
  new  = measured top edge against the reference circle of the other markers (TriangleTopEdge), % R
No limit is used anywhere here: this compares measurements, not verdicts.
"""
import csv, math, statistics, sys


def f(x):
    try: return float(x)
    except (TypeError, ValueError): return float('nan')


def q(v, p):
    v = sorted(x for x in v if math.isfinite(x))
    return v[min(len(v) - 1, int(p * len(v)))] if v else float('nan')


def stats(v):
    v = [x for x in v if math.isfinite(x)]
    if not v: return 'n 0'
    med = statistics.median(v); mad = statistics.median(abs(x - med) for x in v)
    return 'n %d median %+.3f MAD %.3f p5 %+.3f p95 %+.3f min %+.3f max %+.3f' % (len(v), med, mad, q(v, .05), q(v, .95), min(v), max(v))


def main():
    dump = list(csv.DictReader(open(sys.argv[1])))
    label = sys.argv[3] if len(sys.argv) > 3 else ''
    base = [r for r in dump if r['trial'] == 'none']
    ok = [r for r in base if not r['new_note'] and r['new_radial_pct']]
    print('== %s: %d photos; new method measured %d, not measured %d, ambiguous %d' % (label, len(base), len(ok), len(base) - len(ok),
          sum(1 for r in ok if f(r['second_frac']) >= 0.5)))
    from collections import Counter
    for note, n in Counter(r['new_note'] for r in base if r['new_note']).most_common(6): print('   not measured: %d x %s' % (n, note))
    print('OLD radial (centre of fitted outline)      ', stats([f(r['old_inout_pct']) for r in ok]))
    print('OLD fitted top edge vs reference circle    ', stats([f(r['old_top_pct']) for r in ok]))
    print('NEW measured top edge vs reference circle  ', stats([f(r['new_radial_pct']) for r in ok]))
    print('NEW without the rounds\' edge offset        ', stats([f(r['new_raw_pct']) for r in ok]))
    print('NEW minus OLD                              ', stats([f(r['new_radial_pct']) - f(r['old_inout_pct']) for r in ok]))
    # the triangle's own edge offset beyond the rounds' (px -> % R): a shape-specific outline difference
    d = [(f(r['tri_edge_offset_px']) - f(r['round_edge_offset_px'])) * 100 / f(r['rpx']) for r in ok]
    print('triangle minus round edge offset (% R)     ', stats(d))
    xs = [(dd, f(r['new_radial_pct']) - f(r['old_inout_pct'])) for dd, r in zip(d, ok) if math.isfinite(dd)]
    if len(xs) > 3:
        mx = statistics.mean(a for a, _ in xs); my = statistics.mean(b for _, b in xs)
        sxy = sum((a - mx) * (b - my) for a, b in xs); sxx = sum((a - mx) ** 2 for a, _ in xs); syy = sum((b - my) ** 2 for _, b in xs)
        print('   correlation of (new - old) with that offset: %.2f (slope %.2f)' % (sxy / math.sqrt(sxx * syy) if sxx and syy else float('nan'), sxy / sxx if sxx else float('nan')))
    print('NEW edge angle vs OLD triangle rotation (deg)', stats([f(r['new_angle_deg']) - f(r['old_tilt_deg']) for r in ok]))
    rough = [r for r in ok if r['uncertain']]
    print('   on uncertain (rough / inconsistent) old fits: %d; NEW minus OLD %s' % (len(rough), stats([f(r['new_radial_pct']) - f(r['old_inout_pct']) for r in rough])))
    for r in base:
        if r['photo_id'] in ('d3a2b077fcf388c3',) or (label.startswith('QC')):
            print('ROW %s | state %s | OLD centre %s %% R, fitted top %s, tilt %s, rms %s px, bad %s, seen %s (%s) | NEW %s %% R (%s px), raw %s, angle %s, %s/%s/%s profiles, rms %s px, span %s, 2nd %s at %s px, ring %s from %s rounds%s, edge offsets px: round %s tri %s baton %s | %s' % (
                r['photo_id'], r['state'], r['old_inout_pct'], r['old_top_pct'], r['old_tilt_deg'], r['old_rms_px'], r['old_bad_frac'], r['old_cover'], r['uncertain'],
                r['new_radial_pct'], r['new_radial_px'], r['new_raw_pct'], r['new_angle_deg'], r['inliers'], r['usable'], r['profiles'], r['rms_px'], r['span'],
                r['second_frac'], r['second_offset_px'], r['ring_scale'], r['ring_markers'], ' (incl. uncertain)' if r['ring_uncertain'] else '',
                r['round_edge_offset_px'], r['tri_edge_offset_px'], r['baton_edge_offset_px'], r['new_note'] or 'measured'))
    if len(sys.argv) > 2 and sys.argv[2] != '-':
        syn = list(csv.DictReader(open(sys.argv[2])))
        b = {r['photo_id']: r for r in syn if r['trial'] == 'none'}
        print('== %s known moves in the photo (MetalSynth.move): recovered change minus applied change' % label)
        for kind in ('inout', 'side', 'turn'):
            for amt in sorted({f(r['amount']) for r in syn if r['trial'] == kind}):
                rows = [r for r in syn if r['trial'] == kind and f(r['amount']) == amt and r['photo_id'] in b]
                def err(col, want):
                    out = []
                    for r in rows:
                        v, v0 = f(r[col]), f(b[r['photo_id']][col])
                        if math.isfinite(v) and math.isfinite(v0): out.append(v - v0 - want)
                    return out
                want_r = amt if kind == 'inout' else 0.0
                eo, en = err('old_inout_pct', want_r), err('new_radial_pct', want_r)
                lost = sum(1 for r in rows if r['new_note'])
                line = '%-5s %+5.1f: radial OLD %s | NEW %s | new not measured %d of %d' % (kind, amt, short(eo), short(en), lost, len(rows))
                if kind == 'turn': line += ' | angle NEW %s OLD %s' % (short(err('new_angle_deg', amt)), short(err('old_tilt_deg', amt)))
                print('SYNTH', line)


def short(v):
    v = [x for x in v if math.isfinite(x)]
    if not v: return 'n 0'
    return 'n %d mean %+.3f mean|e| %.3f max|e| %.3f' % (len(v), statistics.mean(v), statistics.mean(abs(x) for x in v), max(abs(x) for x in v))


if __name__ == '__main__':
    main()
