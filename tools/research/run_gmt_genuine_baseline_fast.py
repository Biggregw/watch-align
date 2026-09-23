#!/usr/bin/env python3
"""Fast fallback run for the genuine GMT baseline. Research-only."""
from __future__ import annotations

import csv
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sys.path.insert(0, str(HERE))
import build_gmt_genuine_baseline as b

# Keep a provenance-diverse subset, but cap network exposure so this run
# completes quickly if dealer CDNs are slow or reject requests.
rows = b.read_manifest()
selected = []
counts = {"watchfinder": 0, "swe": 0, "sothebys": 0, "other": 0}
for r in rows:
    sid = r["source_id"]
    if sid.startswith("wf_") and counts["watchfinder"] < 2:
        selected.append(r); counts["watchfinder"] += 1
    elif sid.startswith("swe_") and counts["swe"] < 6:
        selected.append(r); counts["swe"] += 1
    elif sid.startswith("soth_") and counts["sothebys"] < 4:
        selected.append(r); counts["sothebys"] += 1
    elif (sid.startswith("wos_") or sid.startswith("bobs_")) and counts["other"] < 2:
        selected.append(r); counts["other"] += 1

fast_manifest = Path(tempfile.gettempdir()) / "gmt-genuine-baseline-fast-manifest.csv"
with fast_manifest.open("w", newline="", encoding="utf-8") as f:
    w = csv.DictWriter(f, fieldnames=rows[0].keys())
    w.writeheader(); w.writerows(selected)

orig_get = b.requests.get
def fast_get(*args, **kwargs):
    t = kwargs.get("timeout", 8)
    try:
        kwargs["timeout"] = min(float(t), 8.0)
    except Exception:
        kwargs["timeout"] = 8.0
    return orig_get(*args, **kwargs)

b.requests.get = fast_get
b.MANIFEST = fast_manifest
b.OUTDIR = ROOT / "docs" / "research" / "gmt-genuine-baseline-fast-results"
b.OUTDIR.mkdir(parents=True, exist_ok=True)
b.MAX_CANDIDATE_URLS = 16
b.MAX_IMAGES_PER_SOURCE = 3
print(f"Fast fallback sources: {len(selected)} ({counts})")
b.main()
