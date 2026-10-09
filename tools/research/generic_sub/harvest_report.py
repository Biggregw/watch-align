#!/usr/bin/env python3
"""Bob's Rolex Harvester photo lists (tools/research/harvest_lists/*.csv; links and sha256 only, no images) -> runner
manifests, then a per-family geometry report against the 124060 genuine reference (see README.md in this folder).

  harvest_report.py manifest <lists_dir> <out_dir>
      out_dir/manifest_all.csv, manifest_date.csv, manifest_nodate.csv (photo_id, local_path, image_url, sha256, ref, sku)
  harvest_report.py report <out_dir> <per_photo.csv> <fetch_log.csv> <reference_per_photo.csv>
      per_photo.csv: Alpha96Calib output on manifest_all.csv measured with the 124060 model
"""
import csv
import glob
import os
import statistics as st
import sys
from collections import Counter, defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from geom import per_watch  # noqa: E402

NO_DATE = {'124060', '114060', '14060', '14060M'}


def family(ref):
    r = ref.upper()
    if r.startswith('1267'):
        return 'GMT 126710 (40 mm)'
    if r.startswith('1167'):
        return 'GMT 116710 (40 mm)'
    if r.startswith('1240'):
        return 'A 41 mm no-date'
    if r.startswith('1266'):
        return 'A 41 mm date'
    if r.startswith('1140'):
        return 'B 40 mm no-date'
    if r.startswith('1166'):
        return 'B 40 mm date'
    if r.startswith('16610L') or r == '16610V':
        return 'C Kermit maxi'
    if r in ('14060', '14060M', '16610', '16610T', '16613', '16618'):
        return 'D pre-maxi'
    return 'other ' + r


def manifest(lists_dir, out):
    os.makedirs(out, exist_ok=True)
    rows = {}
    for f in sorted(glob.glob(os.path.join(lists_dir, '*.csv'))):
        for r in csv.DictReader(open(f, newline='')):
            s = (r.get('sha256') or '').lower()
            if len(s) != 64 or not r.get('image_url'):
                continue
            rows.setdefault(s, {'photo_id': s[:16], 'local_path': f'images/{s[:2]}/{s}.jpg', 'image_url': r['image_url'],
                                'sha256': s, 'ref': r.get('reference', ''), 'sku': r.get('sku', '')})
    fields = ['photo_id', 'local_path', 'image_url', 'sha256', 'ref', 'sku']
    for name, keep in (('all', lambda r: True), ('date', lambda r: r['ref'].upper() not in NO_DATE),
                       ('nodate', lambda r: r['ref'].upper() in NO_DATE)):
        sel = [r for r in rows.values() if keep(r)]
        with open(os.path.join(out, f'manifest_{name}.csv'), 'w', newline='') as fh:
            w = csv.DictWriter(fh, fieldnames=fields); w.writeheader(); w.writerows(sel)
    print('photos in lists:', len(rows), dict(Counter(family(r['ref']) for r in rows.values())))


def report(out, per_photo, fetch_log, reference):
    man = {r['photo_id']: r for r in csv.DictReader(open(os.path.join(out, 'manifest_all.csv')))}
    fl = list(csv.DictReader(open(fetch_log)))
    print('fetch:', dict(Counter(r.get('status', '') for r in fl)))
    rows = []
    for r in csv.DictReader(open(per_photo)):
        m = man.get(r['photo_id'])
        if m:
            r['sku'] = m['sku'] or m['photo_id']; r['fam'] = family(m['ref']); r['ref'] = m['ref']; rows.append(r)
    print('measured:', dict(Counter((r['fam'], r['status']) for r in rows)))
    ref = [r for r in csv.DictReader(open(reference)) if r.get('class_label') == 'gen']
    W = per_watch(ref, lambda r: r['physical_watch_id'])
    ks = ['ring_scale_%', 'round_size_%R', 'b6_radial_%R', 'b9_radial_%R', 't12_radial_%R']
    v = {k: [x[k] for x in W.values() if k in x] for k in ks}
    print('\nAll measured with the 124060 model. Group medians; in brackets the distance from the 124060 median in 124060 '
          'per-watch standard deviations.')
    print('group'.ljust(22) + 'watches'.rjust(8) + ''.join(k.rjust(16) for k in ks))
    print('124060 reference'.ljust(22) + str(len(W)).rjust(8) + ''.join(f'{st.median(x):+.2f}±{st.pstdev(x):.2f}'.rjust(16) for x in v.values()))
    g = defaultdict(list)
    for r in rows:
        g[r['fam']].append(r)
    p5 = sorted(v['b6_radial_%R'])[int(.05 * len(v['b6_radial_%R']))]
    for fam in sorted(g):
        pw = per_watch(g[fam], lambda r: r['sku'])
        cells = []
        for k in ks:
            vals = [d[k] for d in pw.values() if k in d]
            cells.append((f'{st.median(vals):+.2f} ({(st.median(vals) - st.median(v[k])) / st.pstdev(v[k]):+.1f})' if vals else '-').rjust(16))
        b6 = [d['b6_radial_%R'] for d in pw.values() if 'b6_radial_%R' in d]
        print(fam.ljust(22) + str(len(pw)).rjust(8) + ''.join(cells) + f'   6 baton below 124060 p5: {sum(x < p5 for x in b6)}/{len(b6)}')


if __name__ == '__main__':
    if sys.argv[1] == 'manifest':
        manifest(sys.argv[2], sys.argv[3])
    else:
        report(*sys.argv[2:6])
