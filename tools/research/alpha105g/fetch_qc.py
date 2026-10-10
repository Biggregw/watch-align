#!/usr/bin/env python3
"""Alpha105g: fetch the nine public Reddit QC photos used by alpha105f-qc-controls.yml (same URLs and sha256), keep only
byte-identical files, and write <out>/<model>/manifest.csv (photo_id, local_path relative to <out>). Never committed."""
import csv
import hashlib
import pathlib
import sys

import requests

CASES = [
    ('GMT_crooked6_1s4cszq', 'gmt_126710', 'https://i.redd.it/zsgyaaky0frg1.jpg', 'c36f1af94be604ec9153e77b42322d0396634af0eb955c2ab7d3841dce4bb6cd'),
    ('GMT_accepted6_18vdgh0', 'gmt_126710', 'https://i.redd.it/tqmt3k1f8o9c1.jpg', 'a041d20d14d7c9f105991496b539bb73d63182d75e3b61b6d5a8f0590ecd6c13'),
    ('GMT_accepted6_18vdgh0_photo2', 'gmt_126710', 'https://i.redd.it/fobj8k1f8o9c1.jpg', '77245a16744f3fb1369bdaef2101593bd6b52de3a0f031946e3cd84ea9fe8217'),
    ('GMT_accepted6_18vdgh0_photo3', 'gmt_126710', 'https://i.redd.it/lltuak1f8o9c1.jpg', '04c83496a5b4f15911b04d21e5f010d65844dbf6aca1ce8fb903099925a9b88b'),
    ('SUB_rejected12_1wgfy7k', 'submariner_124060', 'https://i.redd.it/0rx4t01hujph1.jpg', '7c9f2b50f8e89b860bf567cebdc7a86f3268c5c5e04397f44314bbf5f3f2e0ba'),
    ('SUB_accepted12_1w848xb', 'submariner_124060', 'https://i.redd.it/wdaw7dp93qnh1.jpg', '2fe59ac6f06f8effed25c288075ee931b608c874c66d536c6255524ae7a7f91f'),
    ('SUB_accepted12_1w848xb_photo5', 'submariner_124060', 'https://i.redd.it/zm5wgcp93qnh1.jpg', '4c4f8cd97ef5e2c3aa8fe2f3a729f3e462b0bf3cd83ea38793419ea7313b2561'),
    ('SUB_control12_1kqn8ub', 'submariner_124060', 'https://i.redd.it/o3xibqc50t1f1.jpg', '471e927748db033d97e3a0087f3cf66dbe2a374246185c72852488c18157a3b2'),
    ('SUB_control12_1kqn8ub_photo2', 'submariner_124060', 'https://i.redd.it/9a6p65g50t1f1.jpg', '234937eb33a3e203031f5423ecf20116a5cba6c034bf15bfd43dd51ddadd5769'),
]


def main(out):
    out = pathlib.Path(out)
    s = requests.Session()
    s.headers.update({'User-Agent': 'Mozilla/5.0 WatchAlignResearch/1.0', 'Referer': 'https://www.reddit.com/'})
    rows = {}
    for name, model, url, sha in CASES:
        d = out / model
        d.mkdir(parents=True, exist_ok=True)
        try:
            r = s.get(url, timeout=20)
            r.raise_for_status()
            if hashlib.sha256(r.content).hexdigest() != sha:
                raise ValueError('sha256 changed')
            (d / (name + '.jpg')).write_bytes(r.content)
            rows.setdefault(model, []).append((name, f'{model}/{name}.jpg'))
            print('verified', name)
        except Exception as e:  # noqa: BLE001
            print('unavailable', name, repr(e)[:120])
    for model, rs in rows.items():
        with open(out / model / 'manifest.csv', 'w', newline='') as fh:
            w = csv.writer(fh)
            w.writerow(['photo_id', 'local_path'])
            w.writerows(rs)


if __name__ == '__main__':
    main(sys.argv[1])
