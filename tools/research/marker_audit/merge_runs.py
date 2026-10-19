#!/usr/bin/env python3
"""Join disjoint resumed batches, requiring complete input and marker coverage."""
import argparse
import csv
from pathlib import Path
from compare import index, coverage


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('manifest', type=Path)
    p.add_argument('output_csv', type=Path)
    p.add_argument('output_log', type=Path)
    p.add_argument('batches', type=Path, nargs='+', help='CSV LOG pairs, in original input order')
    a = p.parse_args()
    if len(a.batches) % 2:
        p.error('Each batch requires a CSV and its runner log')
    header, parts, logs = None, [], []
    for csv_path, log_path in zip(a.batches[::2], a.batches[1::2]):
        text = csv_path.read_text()
        first, rest = text.split('\n', 1)
        if header is not None and first != header:
            raise ValueError('CSV headers differ')
        header = first
        parts.append(rest)
        logs.append(log_path.read_text().rstrip('\n') + '\n')
    combined = header + '\n' + ''.join(parts)
    rows, _ = index(csv.DictReader(combined.splitlines()))
    manifest = list(csv.DictReader(a.manifest.open()))
    log = ''.join(logs)
    coverage(rows, manifest, log)  # Reject missing inputs/hours or duplicated batch outcomes.
    a.output_csv.write_text(combined)
    a.output_log.write_text(log)
    print(f'Complete: {len(manifest)} input rows, {len(rows)} unique measured markers')


if __name__ == '__main__':
    main()
