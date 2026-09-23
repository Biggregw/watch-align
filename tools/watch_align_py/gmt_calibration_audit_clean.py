#!/usr/bin/env python3
"""Filters the raw calibration-corpus audit CSV
(gmt_calibration_audit_per_image.csv) down to the rows that are safe to
feed into physical-watch-level population statistics: drops rows
flagged as duplicate/near-duplicate views (same moment reshot/recropped,
which would silently inflate within-watch repeatability if counted as a
second independent observation) and rows with no local_path at all
(sources that produced zero fetched images -- already captured in the
per-source funnel, nothing to measure here). Every dropped row stays
fully visible in the raw audit CSV; this step only prepares the input
analyze_gmt_proportional_calibration.py expects.
"""
from __future__ import annotations

import csv
from pathlib import Path


def clean(in_csv: Path, out_csv: Path) -> None:
    rows = list(csv.DictReader(in_csv.open(newline="", encoding="utf-8")))
    kept = [r for r in rows if r.get("local_path")
            and r.get("failure_category") != "duplicate/near-duplicate view"]
    out_csv.parent.mkdir(parents=True, exist_ok=True)
    fieldnames = list(rows[0].keys()) if rows else []
    with out_csv.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fieldnames, restval="")
        w.writeheader()
        w.writerows(kept)
    print(f"kept {len(kept)}/{len(rows)} rows (dropped zero-fetch placeholders and flagged duplicates)")


def main() -> int:
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("--in-csv", required=True, type=Path)
    ap.add_argument("--out-csv", required=True, type=Path)
    args = ap.parse_args()
    clean(args.in_csv, args.out_csv)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
