#!/usr/bin/env python3
"""Build a small flat cache for rapid downstream Watch Align experiments.

This does not rerun pose acquisition. It combines the committed corpus analysis
with any already-computed alternative-measurement artifact. The resulting CSV is
intended for experiments that alter scoring, filtering, aggregation, or marker
measurement interpretation while holding acquisition fixed.
"""
from __future__ import annotations
import argparse
from pathlib import Path
import pandas as pd


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--per-image-csv', type=Path, required=True)
    ap.add_argument('--projective-csv', type=Path)
    ap.add_argument('--out', type=Path, required=True)
    args = ap.parse_args()

    base = pd.read_csv(args.per_image_csv)
    base = base.loc[base['pipeline_outcome'].eq('accepted')].copy()
    key = ['source_id', 'physical_watch_id', 'local_path']

    if args.projective_csv and args.projective_csv.exists():
        alt = pd.read_csv(args.projective_csv)
        alt_keep = key + [
            c for c in alt.columns
            if c not in key and (
                'projective_' in c or 'baseline_' in c
                or c in ['markers_detected', 'suspicious_named_case']
            )
        ]
        alt = alt[alt_keep]
        base = base.merge(
            alt, on=key, how='left', validate='one_to_one', suffixes=('', '_alt')
        )

    args.out.parent.mkdir(parents=True, exist_ok=True)
    base.to_csv(args.out, index=False)
    print(f'rows={len(base)} cols={len(base.columns)} out={args.out}')


if __name__ == '__main__':
    main()
