"""Add test photos collected with the app's Collect screen (alpha66) to datasets/collected/.

python3 tools/testset/ingest.py <export.zip | folder with manifest(s) and images/>...

Accepts the zip from "Export zip", or a checkout of the upload branch (testset/manifests/*.csv and
testset/images/). Images are copied to datasets/collected/images/ (not committed; see .gitignore)
and rows are appended to datasets/collected/manifest.csv (committed), in the corpus list format
(local_path, class_label, physical_watch_id, ...), with local_path relative to the repo root.
Exact duplicates (same bytes) of photos already collected are skipped. Watch ids are prefixed
with "collected_" so they never merge with corpus watch ids; the phone keeps its ids across exports, so a watch tagged "same watch" in a later batch stays one watch.
"""
import csv, hashlib, io, os, sys, zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(ROOT, 'datasets', 'collected')
IMG = os.path.join(OUT, 'images')
MAN = os.path.join(OUT, 'manifest.csv')
COLS = ['local_path', 'class_label', 'physical_watch_id', 'model', 'factory', 'source', 'notes', 'captured_at',
        'app_version', 'dial_found', 'twelve_found', 'pose', 'marker_tilt_deg', 'suitable', 'batch', 'sha256']

def existing():
    rows = list(csv.DictReader(open(MAN))) if os.path.exists(MAN) else []
    return rows, {r['sha256'] for r in rows}

def sources(arg):
    """Yield (batch, manifest rows, reader(name) -> bytes)."""
    if zipfile.is_zipfile(arg):
        z = zipfile.ZipFile(arg)
        rows = list(csv.DictReader(io.TextIOWrapper(z.open('manifest.csv'), 'utf-8')))
        yield os.path.splitext(os.path.basename(arg))[0], rows, lambda n: z.read(n)
    else:
        base = arg
        if os.path.isdir(os.path.join(arg, 'testset')): base = os.path.join(arg, 'testset')
        mans = sorted(os.path.join(base, 'manifests', f) for f in os.listdir(os.path.join(base, 'manifests'))) \
            if os.path.isdir(os.path.join(base, 'manifests')) else [os.path.join(base, 'manifest.csv')]
        for m in mans:
            rows = list(csv.DictReader(open(m, encoding='utf-8')))
            yield os.path.splitext(os.path.basename(m))[0], rows, lambda n, b=base: open(os.path.join(b, n), 'rb').read()

def main(args):
    os.makedirs(IMG, exist_ok=True)
    rows, seen = existing()
    added = dup = missing = 0
    for a in args:
        for batch, man, read in sources(a):
            for r in man:
                try: data = read(r['local_path'])
                except (KeyError, FileNotFoundError): missing += 1; continue
                h = hashlib.sha256(data).hexdigest()
                if h in seen: dup += 1; continue
                seen.add(h)
                name = f"{batch}_{os.path.basename(r['local_path'])}"
                open(os.path.join(IMG, name), 'wb').write(data)
                o = {c: r.get(c, '') for c in COLS}
                o.update(local_path=os.path.relpath(os.path.join(IMG, name), ROOT), batch=batch, sha256=h,
                         physical_watch_id=f"collected_{r.get('physical_watch_id', '')}")
                rows.append(o); added += 1
    with open(MAN, 'w', newline='') as f:
        w = csv.DictWriter(f, fieldnames=COLS); w.writeheader(); w.writerows(rows)
    by = {}
    for r in rows: by[r['class_label']] = by.get(r['class_label'], 0) + 1
    print(f'added {added}, skipped {dup} duplicates, {missing} missing; collected set now {by}')

if __name__ == '__main__':
    if len(sys.argv) < 2: print(__doc__); sys.exit(1)
    main(sys.argv[1:])
