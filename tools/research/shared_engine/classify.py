"""Watch Align 1.6.6 whole-watch classification, research replay (read-only) of the app's rule
(WatchClassification.java) on TrackDump CSVs: replica-QC = the app's existing per-marker state (default limits),
strict genuine = the same decision rule at the strict genuine limits (rescore.decide, 0.5 px allowance once).
Usage: classify.py out.csv [model]   or import classify_rows."""
import csv, collections, sys
from rescore import decide, CUR
GENUINE = dict(roundPos=0.671, batonPos=0.752, batonTilt=1.096, triSide=0.437, triInout=0.373, triGap=2.81, triRot=1.174)
REQUIRED = {'gmt_126710': 11, 'submariner_126610': 11, 'submariner_124060': 12}


def reliable(r):
    return r['state'] != 'CANT_CHECK' and not r['gate'] and not r['uncertain'] and not (
        r['shape'] == 'triangle' and r.get('gap_from_fit', '').lower() in ('true', '1', 'yes'))


def classify_photo(rows, required):
    """mirror of WatchClassification.classify (Java)"""
    by = {int(r['hour']): r for r in rows}
    strict = {h: decide(r, GENUINE)[0] for h, r in by.items()}
    if any(reliable(r) and r['state'] == 'CLEARLY_OFF' for r in by.values()): return 'REPLICA_QC_ISSUES'
    if any(reliable(r) and strict[h] == 'CLEARLY_OFF' for h, r in by.items()): return 'REPLICA'
    if len(by) < required or not all(reliable(r) for r in by.values()): return 'CANT_DETERMINE'
    if all(v == 'NO_VISIBLE_ISSUE' for v in strict.values()): return 'GENUINE'
    return 'CANT_DETERMINE'


def classify_rows(rows, model):
    ph = collections.defaultdict(list)
    for r in rows: ph[r['photo_id']].append(r)
    return {p: classify_photo(v, REQUIRED[model]) for p, v in ph.items()}


if __name__ == '__main__':
    rows = list(csv.DictReader(open(sys.argv[1])))
    model = sys.argv[2] if len(sys.argv) > 2 else rows[0]['model']
    res = classify_rows(rows, model)
    print(model, len(res), 'photos', dict(collections.Counter(res.values())))
    for p, c in sorted(res.items()):
        if 'QC' in sys.argv[1] or len(res) < 12: print('  ', p, c)
