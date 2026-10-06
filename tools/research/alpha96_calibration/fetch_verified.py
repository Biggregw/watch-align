#!/usr/bin/env python3
"""RESEARCH ONLY. Restore the catalogued provenance-strong genuine photos, byte-for-byte.

For every row of catalogue_provenance_strong.csv this re-fetches exactly the catalogued image_url
with the dataset harvester's own HTTP client (same User-Agent, robots.txt respected, per-host
interval, no retries around a refusal) and keeps the file ONLY if sha256(bytes) equals the
catalogued sha256, i.e. the same evidence the harvester judged, not a new source. A changed or
unreachable listing is recorded with a reason and never replaced.

Usage: fetch_verified.py <catalogue.csv> <image_root> <fetch_log.csv>
Images are written to <image_root>/<local_path>; they must never be committed or uploaded.
"""
import csv
import hashlib
import os
import sys
from collections import Counter

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'dataset_harvester'))
from harvester.http import FetchError, Http  # noqa: E402


def main(catalogue, root, log_path):
    rows = list(csv.DictReader(open(catalogue)))
    http = Http()
    by_url = {}
    out = []
    for i, r in enumerate(rows, 1):
        dest = os.path.join(root, r['local_path'])
        want = r['sha256'].lower()
        status, got, nbytes = '', '', ''
        if os.path.exists(dest) and hashlib.sha256(open(dest, 'rb').read()).hexdigest() == want:
            status = 'verified_cached'
        else:
            if os.path.exists(dest):
                os.remove(dest)  # fail closed: only verified bytes may sit where the runner looks
            url = r['image_url']
            if url not in by_url:
                try:
                    by_url[url] = ('ok', http.get(url).content)
                except FetchError as e:
                    by_url[url] = (e.args[0] if e.args else 'fetch_error', None)
            st, data = by_url[url]
            if data is None:
                status = st
            else:
                got, nbytes = hashlib.sha256(data).hexdigest(), len(data)
                if got == want:
                    os.makedirs(os.path.dirname(dest), exist_ok=True)
                    with open(dest, 'wb') as fh:
                        fh.write(data)
                    status = 'verified'
                else:
                    status = 'hash_mismatch'
        out.append(dict(photo_id=r['photo_id'], physical_watch_id=r['physical_watch_id'], model=r['model'],
                        provenance=r['provenance'], host=r['image_url'].split('/')[2] if '//' in r['image_url'] else '',
                        status=status, bytes=nbytes, sha256_expected=want, sha256_got=got))
        print(f'{i}/{len(rows)} {status} {r["photo_id"]}', flush=True)
    with open(log_path, 'w', newline='') as fh:
        w = csv.DictWriter(fh, fieldnames=list(out[0].keys())); w.writeheader(); w.writerows(out)
    ok = [o for o in out if o['status'].startswith('verified')]
    print('status:', dict(Counter(o['status'] for o in out)))
    print('by host:', dict(Counter((o['host'], o['status']) for o in out)))
    print(f'verified photos {len(ok)} / {len(out)}; watches {len({o["physical_watch_id"] for o in ok})} / '
          f'{len({o["physical_watch_id"] for o in out})}')


if __name__ == '__main__':
    main(*sys.argv[1:4])
