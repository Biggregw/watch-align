"""Stability metrics for the 12-triangle detectors on one partition (research only).

    python3 tools/dataset_harvester/subresearch/triangle_eval.py MEASUREMENT_DIR --partition development [--json out.json]

Compares "12_legacy" (v1 research fit), "12_top" (v2, each variant's own top candidate) and "12"
(v2 with cross-variant consensus). Only photos whose ORIGINAL dial was edge-fitted take part, and only
variants whose dial was edge-fitted; the rest are counted as excluded. The holdout partition is refused.

Per photo: detection repeat rate, fit-path repeat (any switch), centre spread (max distance from the
median centre / dial radius), rotation spread, apex-angle spread (max - min over detected variants),
and for "12" the rank consistency (selected candidate was the variant's own top) and consensus support.
"""
from __future__ import annotations

import argparse
import csv
import json
import math
import sys
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from subresearch.triangle import dial_usable  # noqa: E402

DETECTORS = ("12_legacy", "12_top", "12")


def f(v):
    try:
        x = float(v)
        return x if math.isfinite(x) else math.nan
    except (TypeError, ValueError):
        return math.nan


def q(v, p):
    v = [x for x in v if math.isfinite(x)]
    return float(np.percentile(v, p)) if v else math.nan


def per_photo(images: list[dict], landmarks: list[dict], partition: str) -> tuple[dict, dict]:
    if partition == "holdout":
        raise SystemExit("the holdout partition stays unseen")
    imgs = [r for r in images if r["partition"] == partition]
    by_sha = defaultdict(dict)
    for r in imgs:
        by_sha[r["sha256"]][r["variant"]] = r
    lms = defaultdict(dict)
    for r in landmarks:
        if r["partition"] == partition and r["landmark"] in DETECTORS:
            lms[(r["sha256"], r["landmark"])][r["variant"]] = r
    photos, excluded = {}, Counter()
    for sha, vs in by_sha.items():
        o = vs.get("orig")
        if o is None or o.get("dial_found") != "True":
            excluded["no_dial_in_original"] += 1
            continue
        if o.get("edge_fit_valid") != "True":
            excluded["original_dial_not_edge_fitted"] += 1
            continue
        ok = sorted(v for v, r in vs.items() if dial_usable(r, o))
        excluded["variants_fallback_or_other_dial"] += len(vs) - len(ok)
        base_r = f(o["dial_r"])
        res = {"sha256": sha, "model": o["model"], "class_tag": o["class_tag"], "physical_watch_id": o["physical_watch_id"],
               "variants": len(ok)}
        for d in DETECTORS:
            rows = [lms[(sha, d)].get(v) for v in ok]
            det = [r for r in rows if r is not None and r.get("detected") == "True"]
            x = {"detected_orig": lms[(sha, d)].get("orig", {}).get("detected") == "True",
                 "repeat_rate": len(det) / len(ok) if ok else math.nan,
                 "paths": sorted({r.get("fit_path", "") for r in det})}
            if len(det) >= 2:
                pts = np.array([[f(r["x"]), f(r["y"])] for r in det])
                med = np.median(pts, axis=0)
                x["centre_spread"] = float(np.max(np.hypot(*(pts - med).T)) / base_r)
                for k, col in (("rotation_spread", "rotation_deg"), ("apex_spread", "apex_deg")):
                    v = [f(r.get(col)) for r in det]
                    v = [t for t in v if math.isfinite(t)]
                    x[k] = max(v) - min(v) if len(v) >= 2 else math.nan
                x["path_switch"] = len(x["paths"]) > 1
            if d == "12" and det:
                x["rank_consistency"] = sum(1 for r in det if r.get("selected_is_variant_top") == "True") / len(det)
                x["consensus_support"] = f(det[0].get("consensus_support"))
            res[d] = x
        photos[sha] = res
    return photos, dict(excluded)


def summary(photos: dict, excluded: dict) -> dict:
    out = {"photos_edge_fitted_original": len(photos), "watches": len({p["physical_watch_id"] for p in photos.values()}),
           "excluded": excluded}
    for d in DETECTORS:
        xs = [p[d] for p in photos.values()]
        multi = [x for x in xs if "centre_spread" in x]
        out[d] = {
            "detection_rate_orig": sum(x["detected_orig"] for x in xs) / len(xs) if xs else math.nan,
            "detection_repeat_rate_median": q([x["repeat_rate"] for x in xs], 50),
            "photos_with_2plus_detections": len(multi),
            "centre_spread_median": q([x["centre_spread"] for x in multi], 50),
            "centre_spread_p95": q([x["centre_spread"] for x in multi], 95),
            "rotation_spread_median": q([x["rotation_spread"] for x in multi], 50),
            "rotation_spread_p95": q([x["rotation_spread"] for x in multi], 95),
            "apex_spread_median": q([x["apex_spread"] for x in multi], 50),
            "apex_spread_p95": q([x["apex_spread"] for x in multi], 95),
            "path_switch_rate": (sum(x["path_switch"] for x in multi) / len(multi)) if multi else math.nan,
        }
        if d == "12":
            out[d]["rank_consistency_median"] = q([x.get("rank_consistency", math.nan) for x in xs], 50)
            out[d]["consensus_support_median"] = q([x.get("consensus_support", math.nan) for x in xs], 50)
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("measurements", type=Path)
    ap.add_argument("--partition", required=True)
    ap.add_argument("--json", type=Path)
    a = ap.parse_args()
    read = lambda n: list(csv.DictReader((a.measurements / n).open(newline="", encoding="utf-8")))
    photos, excluded = per_photo(read("sub_images.csv"), read("sub_landmarks.csv"), a.partition)
    s = summary(photos, excluded)
    s["partition"] = a.partition
    text = json.dumps(s, indent=1, default=str)
    print(text)
    if a.json:
        a.json.write_text(json.dumps({"summary": s, "photos": photos}, indent=1, default=str), encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
