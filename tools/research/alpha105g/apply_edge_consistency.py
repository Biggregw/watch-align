#!/usr/bin/env python3
"""Mark genuine marker readings flagged as one-sided-edge readings (edge_flags.csv) as unusable in a per_photo CSV, so
they do not set a model's genuine reference (QC guardrails 1: a documented measurement-quality exclusion).

The app fits each baton's long sides and the 12 triangle's sides to the outermost edge. When lighting darkens one side's
polished bevel, that side lands on the lume edge and the marker centre shifts. The Alpha105g genuine-photo study
(CI 38054695092) flagged genuine readings whose two-side width deviates from the model median by more than 3 x robust
spread. Only the flagged marker of the flagged photo is withheld (m<h>_usable=false); for the 12 that removes the whole
12 reading of that photo, as edge_filter.py does. Every other marker and photo is untouched. Model-generic, modelled on
../sub124060/apply_interference.py; the output keeps the input's columns and line endings.

Usage: apply_edge_consistency.py --per-photo per_photo_edge.csv --flags edge_flags.csv --model submariner_124060 \
           --out per_photo_edge_consistent.csv
Flags CSV columns: model,photo_id,marker[,reason] (marker = clock hour; `marker_hour` is accepted as well).
"""
import argparse
import csv
from collections import defaultdict


def main():
    ap = argparse.ArgumentParser()
    for k in ('--per-photo', '--flags', '--model', '--out'):
        ap.add_argument(k, required=True)
    a = ap.parse_args()
    flags = defaultdict(dict)
    for r in csv.DictReader(open(a.flags, newline='')):
        if r['model'] == a.model:
            h = int(r.get('marker') or r['marker_hour'])
            flags[r['photo_id']][h] = r.get('reason') or 'one-sided edge'
    raw = open(a.per_photo, newline='').read()
    eol = '\r\n' if '\r\n' in raw.split('\n', 1)[0] + '\n' else '\n'
    rows = list(csv.DictReader(open(a.per_photo, newline='')))
    seen = {r['photo_id'] for r in rows}
    missing = sorted(p for p in flags if p not in seen)
    if missing:
        raise SystemExit(f'flagged photo(s) not in {a.per_photo}: {", ".join(missing)}')
    n = 0; already = 0
    for r in rows:
        for h, why in flags.get(r['photo_id'], {}).items():
            if r.get(f'm{h}_usable') == 'true':
                r[f'm{h}_usable'] = 'false'; r[f'm{h}_reason'] = f'{why}: edge-affected'; n += 1
            else:
                already += 1
    with open(a.out, 'w', newline='') as fh:
        w = csv.DictWriter(fh, fieldnames=list(rows[0].keys()), lineterminator=eol); w.writeheader(); w.writerows(rows)
    print(f'{a.model}: {n} marker reading(s) on {len(flags)} photo(s) marked unusable (one-sided edge); '
          f'{already} flag(s) already unusable')


if __name__ == '__main__':
    main()
