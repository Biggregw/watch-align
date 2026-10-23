"""Reproduce face-on-v3 screen evidence (full-watch and per-marker masked decisions) against the existing twelve public originals.

No network and no image output. Requires JDK 17 and the existing desktop harness.
"""
from concurrent.futures import ThreadPoolExecutor
from hashlib import sha256
from pathlib import Path
import csv
import json
import zipfile
from .diagnostics import LegacyGMTDiagnostics
from .faceon import production_screener
from .screening import Screener


def validate(repo, output):
    repo = Path(repo)
    results = repo / 'tools/research/marker_audit/results'
    labels = {r['sha256']: r for p in results.glob('*-suitability.csv') for r in csv.DictReader(p.open())}
    catalogues = [results / 'ten.csv', repo / 'tools/research/sub124060/catalogue_124060.csv',
                  repo / 'tools/research/alpha96_calibration/catalogue_provenance_strong.csv']
    catalogue = {r['sha256']: r for p in catalogues for r in csv.DictReader(p.open())}
    originals = {p.name: p.read_bytes() for p in (results.parent / 'fixtures').glob('*.jpg')}
    with zipfile.ZipFile(results.parent / 'review-bundle.zip') as z:
        for name in z.namelist():
            if name.startswith('originals/'):
                originals[Path(name).name] = z.read(name)
    screen = production_screener()
    # Compile once before sharing the read-only harness classes across workers.
    screen.analyzer.driver._compile()
    def assess(item):
        name, data = item
        digest = sha256(data).hexdigest()
        row = catalogue[digest]
        decision = screen.assess_bytes(data, row['model'])
        record = {'photo_id': name, 'reviewed_label': labels.get(digest, {}), 'decision': decision.record(),
                  'masked_decision': screen.assess_bytes(data, row['model'], 'marker_masked').record()}
        if row['model'].startswith('126710'):
            record['legacy_gmt_comparison'] = Screener(LegacyGMTDiagnostics(screen.analyzer.driver.harness)).assess_bytes(data, row['model']).record()
        return record
    with ThreadPoolExecutor(3) as pool:
        records = list(pool.map(assess, sorted(originals.items())))
    Path(output).write_text(json.dumps(records, indent=2) + '\n')
    return records


if __name__ == '__main__':
    import argparse
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--repo', type=Path, default=Path.cwd())
    p.add_argument('--output', type=Path, required=True)
    a = p.parse_args()
    records = validate(a.repo, a.output)
    from collections import Counter
    print(dict(Counter(r['decision']['status'] for r in records)))
