"""Guardrail 8.5: held-out genuine validation. Each genuine photo is judged as the app judges a single photo, against a
reference rebuilt without its own physical watch (leave-one-watch-out). SWE photos never enter the marker reference, so
they are an external held-out set judged against the full reference."""
import csv, os, sys, importlib.util
from collections import defaultdict
from statistics import median
C = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location('b', os.path.join(C, 'build_alpha98_reference.py')); b = importlib.util.module_from_spec(spec); spec.loader.exec_module(b)
unc = {}
for line in open(os.path.join(C, 'alpha99_uncertainty.properties')):
    if '=' in line and not line.startswith('#'):
        k, v = line.strip().split('='); unc[k] = float(v)
K = unc['k_sigma']
FEAT = ('six_rot', 'six_off', 'nine_rot', 'nine_off', 'rounds_off', 'ring_rot', 'ring_shift', 'rounds_size', 'round_size_rel')
NEW = ('rounds_size', 'round_size_rel')
ANG = ('six_rot', 'nine_rot', 'ring_rot')
def sigma(k, R):
    if k in ANG: return max(unc[k + '.deg'], unc[k + '.degR'] / R)
    return max(unc[k + '.R'], unc[k + '.px'] / R)
cat = {r['photo_id']: r for r in csv.DictReader(open(os.path.join(C, 'catalogue_provenance_strong.csv')))}
shared = {r['photo_id'] for r in csv.DictReader(open(os.path.join(C, 'results/dedup/photos.csv'))) if r['shared_dial'] == '1'}
pri = {r['photo_id']: r for r in csv.DictReader(open(os.path.join(C, 'priority_genuine.csv'))) if r['include'] == 'yes'}
photos = []   # (photo_id, watch, features, heldout_external)
for r in csv.DictReader(open(os.path.join(C, 'results/ci_run_37500197377/per_photo.csv'))):
    if r['group'] != 'genuine_population' or r['photo_id'] in shared: continue
    f = b.photo_features(r)
    if f: photos.append((r['photo_id'], r['physical_watch_id'], f, 'swisswatchexpo' in cat[r['photo_id']]['image_url']))
for r in csv.DictReader(open(os.path.join(C, 'results/priority_genuine_runner.csv'))):
    if r['photo_id'] not in pri: continue
    f = b.photo_features(r)
    if not f: continue
    if r['photo_id'] == 'U8_126715CHNR_CPO': f.pop('six_rot', None); f.pop('six_off', None)
    photos.append((r['photo_id'], pri[r['photo_id']]['physical_watch_id'], f, False))
# per-watch medians (reference side), excluding SWE exactly as the builder does
pw = defaultdict(lambda: defaultdict(list))
for pid, w, f, ext in photos:
    if ext: continue
    for k, v in f.items(): pw[w][k].append(v)
W = {k: {w: median(d[k]) for w, d in pw.items() if d.get(k)} for k in FEAT}
WR = {w: median(d['R']) for w, d in pw.items()}
def judge(k, v, R, excl):
    vals = {w: x for w, x in W[k].items() if w != excl}
    if k in b.SIGNED:
        nom = median(vals.values()); val = abs(v - nom)
        far = {w: abs(x - median([y for ww, y in vals.items() if ww != w])) for w, x in vals.items()}
    else:
        val = abs(v); far = {w: abs(x) for w, x in vals.items()}
    if k in b.RES_MATCHED:
        pool = [x for w, x in far.items() if WR.get(w) and WR[w] <= b.RES_MATCH * R]
        if len(pool) < b.MIN_MATCHED: return 'NOT_ASSESSED', val, None
    else:
        pool = list(far.values())
    mx = max(pool)
    if val <= mx: return 'WITHIN', val, mx
    return ('CLEAR' if val - mx > K * sigma(k, R) else 'WORTH'), val, mx
out = []; tally = defaultdict(lambda: defaultdict(int)); watches = defaultdict(lambda: defaultdict(set))
for pid, w, f, ext in photos:
    for k in FEAT:
        if k not in f: continue
        st, val, mx = judge(k, f[k], f['R'], None if ext else w)
        grp = 'swe_external' if ext else 'lowo'
        tally[(grp, k)][st] += 1; watches[(grp, k)][st].add(w)
        out.append([grp, pid, w, k, f"{f['R']:.0f}", st, f'{val:.5f}', '' if mx is None else f'{mx:.5f}'])
with open(os.path.join(C, "results", "alpha101", "heldout_genuine.csv"), 'w', newline='') as fh:
    wr = csv.writer(fh); wr.writerow(['set', 'photo_id', 'physical_watch_id', 'feature', 'dial_radius_px', 'status', 'value', 'genuine_max']); wr.writerows(out)
print(f"{'set':13s} {'feature':15s} {'assessed':>8s} {'within':>7s} {'worth':>6s} {'clear':>6s} {'n/a':>5s}  watches worth/clear")
for (grp, k), t in sorted(tally.items()):
    a = t['WITHIN'] + t['WORTH'] + t['CLEAR']
    print(f"{grp:13s} {k+(' *' if k in NEW else ''):15s} {a:8d} {t['WITHIN']:7d} {t['WORTH']:6d} {t['CLEAR']:6d} {t['NOT_ASSESSED']:5d}  {len(watches[(grp,k)]['WORTH'])}/{len(watches[(grp,k)]['CLEAR'])}")
