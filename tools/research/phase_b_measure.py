#!/usr/bin/env python3
"""Phase B measurement: run the frozen Phase A detector on every accepted
genuine control-set image.

Reads datasets/gmt_phase_b_genuine/accepted_images.csv (produced by human
visual suitability review -- see docs/research/gmt-phase-b-image-selection.md),
re-fetches each accepted image by its exact recorded image_url (never a
re-scan of the page, so the image measured here is provably the same one that
was visually reviewed), and runs tools/research/phase_a_landmarks.py --
completely unmodified from Phase A -- once per image. No pose/tilt estimation,
no perspective correction, no Stage 3 machinery: identical detector, identical
constraints as Phase A, just run across more images.

Outputs into docs/research/gmt-phase-b-results/:
    per_image_measurements.csv / .json  -- one row per accepted image
    overlays/<source_id>__img<N>.png     -- acceptance overlay per image
                                             (gitignored -- never committed,
                                             uploaded as a CI artifact only)

Source pixels are never committed -- see datasets/gmt_phase_b_genuine/.gitignore
and docs/research/gmt-phase-b-results/.gitignore.
"""
from __future__ import annotations

import csv
import json
import sys
from pathlib import Path

import cv2
import numpy as np

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(Path(__file__).resolve().parent))

import phase_a_harness as pah
import phase_a_landmarks as pal
from phase_b_fetch_genuine import download_image

ACCEPTED_CSV = ROOT / "datasets" / "gmt_phase_b_genuine" / "accepted_images.csv"
OUTDIR = ROOT / "docs" / "research" / "gmt-phase-b-results"
OVERLAY_DIR = OUTDIR / "overlays"


def read_accepted() -> list[dict]:
    with ACCEPTED_CSV.open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def main() -> int:
    if not ACCEPTED_CSV.exists():
        print(f"no {ACCEPTED_CSV} yet -- suitability review not committed; nothing to measure")
        return 0
    only_source = sys.argv[1] if len(sys.argv) > 1 else None
    rows = read_accepted()
    if only_source:
        rows = [r for r in rows if r["source_id"] == only_source]

    OUTDIR.mkdir(parents=True, exist_ok=True)
    OVERLAY_DIR.mkdir(parents=True, exist_ok=True)

    out_rows = []
    for i, r in enumerate(rows, 1):
        label = f"{r['source_id']}__img{r['image_index']}"
        im, native = download_image(r["image_url"])
        if im is None:
            print(f"[{i}/{len(rows)}] {label}: RE-FETCH FAILED for {r['image_url']}")
            out_rows.append({
                "source_id": r["source_id"], "physical_watch_id": r["physical_watch_id"],
                "provenance_class": r["provenance_class"], "image_url": r["image_url"],
                "fetch_ok": False,
            })
            continue
        bgr = cv2.cvtColor(np.array(im), cv2.COLOR_RGB2BGR)
        result = pal.measure(bgr)
        row = pah.result_to_row(result, run_index=0, source_id=r["source_id"],
                                 physical_watch_id=r["physical_watch_id"],
                                 image_path=label)
        row["provenance_class"] = r["provenance_class"]
        row["image_url"] = r["image_url"]
        row["fetch_ok"] = True
        out_rows.append(row)

        overlay = pah.render_acceptance_overlay(bgr, result)
        cv2.imwrite(str(OVERLAY_DIR / f"{label}.png"), overlay)

        n_assessable = sum(1 for n in pah.LANDMARK_NAMES if row.get(f"{n}_assessable"))
        print(f"[{i}/{len(rows)}] {label}: dial={'ok' if result.dial else 'FAIL'} "
              f"landmarks_assessable={n_assessable}/5")

    existing_csv = OUTDIR / "per_image_measurements.csv"
    if only_source and existing_csv.exists():
        # A source-filtered run must not wipe out results for every OTHER
        # already-measured source -- replace only this source_id's rows,
        # keep everything else exactly as previously committed.
        with existing_csv.open(newline="", encoding="utf-8") as f:
            kept = [r for r in csv.DictReader(f) if r["source_id"] != only_source]
        out_rows = kept + out_rows

    keys = []
    for r in out_rows:
        for k in r:
            if k not in keys:
                keys.append(k)
    with existing_csv.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=keys)
        w.writeheader()
        w.writerows(out_rows)
    (OUTDIR / "per_image_measurements.json").write_text(json.dumps(out_rows, indent=2), encoding="utf-8")

    n_ok = sum(1 for r in out_rows if r.get("fetch_ok"))
    print(f"\n{n_ok}/{len(out_rows)} accepted images successfully re-fetched and measured")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
