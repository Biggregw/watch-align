"""Watch Align 1.6.6 whole-watch classification, research replay (read-only) of the app's rule
(WatchClassification.java) on TrackDump CSVs: replica-QC = the app's existing per-marker state (default limits),
strict genuine = the same decision rule at the strict genuine limits (rescore.decide, 0.5 px allowance once).
Usage: classify.py out.csv [model]   or import classify_rows."""
import csv, collections, math, sys
from rescore import decide, CUR
GENUINE = dict(roundPos=0.671, batonPos=0.752, batonTilt=1.096, triSide=0.437, triInout=0.373, triGap=2.81, triRot=1.174)
REQUIRED = {'gmt_126710': 11, 'submariner_126610': 11, 'submariner_124060': 12}


def _alt_u():
    """the validated one-side refit uncertainty, read from the app (MetalFit.ALT_U_POS_R, ALT_U_ROT_DEG)"""
    import pathlib, re
    src = (pathlib.Path(__file__).resolve().parents[3] / 'android/app/src/main/java/com/watchalign/mobile/MetalFit.java').read_text()
    m = re.search(r'static (?:final )?double ALT_U_POS_R=([^,;]+),ALT_U_ROT_DEG=([^;]+);', src)
    a = re.search(r'static (?:final )?double ALT_LUME_AGREE_R=([^;]+);', src)
    v = [float('nan') if 'NaN' in x else float(x) for x in m.groups() + a.groups()]
    return 100 * v[0], v[1], 100 * v[2]


ALT_U_POS_PCT, ALT_U_ROT_DEG, ALT_LUME_AGREE_PCT = _alt_u()
USE_ALT = True
USE_CREDIBLE = True   # owner, 21 Oct 2026: a credible out-of-range reading decides a failure (False: the earlier rule)


def _f(x):
    try: return float(x)
    except (TypeError, ValueError): return float('nan')


def alt(r):
    """mirror of WatchClassification.alt: NONE / PASS / FAIL / UNDECIDED"""
    if not USE_ALT or r['shape'] != 'baton' or not r.get('alt_side') or r['gate'] or r['state'] != 'NO_VISIBLE_ISSUE': return 'NONE'
    side, io, tilt = _f(r['alt_side_pct']), _f(r['alt_inout_pct']), _f(r['alt_tilt_deg'])
    if not all(math.isfinite(v) for v in (side, io, tilt, ALT_U_POS_PCT, ALT_U_ROT_DEG)): return 'NONE'
    if not _f(r.get('alt_lume_diff_pct')) <= ALT_LUME_AGREE_PCT: return 'NONE'
    pos_lim = GENUINE['batonPos'] + 50 / _f(r['rpx']); tilt_lim = GENUINE['batonTilt']
    pos, tilt = math.hypot(side, io), abs(tilt)
    if pos - ALT_U_POS_PCT > pos_lim or tilt - ALT_U_ROT_DEG > tilt_lim: return 'FAIL'
    if pos + ALT_U_POS_PCT <= pos_lim and tilt + ALT_U_ROT_DEG <= tilt_lim: return 'PASS'
    return 'UNDECIDED'


def reliable(r):
    return r['state'] != 'CANT_CHECK' and not r['gate'] and not r['uncertain'] and not (
        r['shape'] == 'triangle' and r.get('gap_from_fit', '').lower() in ('true', '1', 'yes'))


def credible(r):
    """mirror of WatchClassification.credible: a usable reading that may decide a failure"""
    if reliable(r): return True
    if not USE_CREDIBLE: return False
    if r['state'] == 'CANT_CHECK' or r['gate']: return False
    if not (math.isfinite(_f(r['side_pct'])) or math.isfinite(_f(r['inout_pct']))): return False
    if r.get('hand_confirmed') and r['uncertain']: return False
    return True


def over(r, L):
    """mirror of WatchClassification.over: the limits L a reading is outside (a gap placed from the outline never counts)"""
    off = list(decide(r, L)[1])
    if r.get('gap_from_fit', '').lower() in ('true', '1', 'yes') and 'gap' in off: off.remove('gap')
    return off


def classify_photo(rows, required):
    """mirror of WatchClassification.classify (Java)"""
    by = {int(r['hour']): r for r in rows}
    strict = {h: decide(r, GENUINE)[0] for h, r in by.items()}
    al = {h: ('NONE' if reliable(r) else alt(r)) for h, r in by.items()}
    g_pass = {h: (reliable(r) and strict[h] == 'NO_VISIBLE_ISSUE') or al[h] == 'PASS' for h, r in by.items()}
    g_fail = {h: (credible(r) and bool(over(r, GENUINE))) or al[h] == 'FAIL' for h, r in by.items()}
    assessed = {h: reliable(r) or al[h] in ('PASS', 'FAIL') for h, r in by.items()}
    if any(credible(r) and over(r, CUR) for r in by.values()): return 'REPLICA_QC_ISSUES'
    if any(g_fail.values()): return 'REPLICA'
    if len(by) < required or not all(assessed.values()): return 'CANT_DETERMINE'
    if all(g_pass.values()): return 'GENUINE'
    return 'CANT_DETERMINE'


def classify_rows(rows, model):
    ph = collections.defaultdict(list)
    for r in rows: ph[r['photo_id']].append(r)
    return {p: classify_photo(v, REQUIRED[model]) for p, v in ph.items()}


if __name__ == '__main__':
    if '--before' in sys.argv: USE_CREDIBLE = False; sys.argv.remove('--before')
    rows = list(csv.DictReader(open(sys.argv[1])))
    model = sys.argv[2] if len(sys.argv) > 2 else rows[0]['model']
    res = classify_rows(rows, model)
    print(model, len(res), 'photos', dict(collections.Counter(res.values())))
    for p, c in sorted(res.items()):
        if 'QC' in sys.argv[1] or len(res) < 12: print('  ', p, c)
