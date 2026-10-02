"""Acquired genuine-source diversity gate for watch-family calibration.

Discovery counts are not enough: a dealer only counts after real images from independent physical
watches have been acquired. The gate is deliberately evaluated before the locked split and before
any calibration limit is fitted.
"""
from __future__ import annotations

import csv
import json
from collections import defaultdict
from pathlib import Path


def evaluate(config: dict, candidate_summary: Path) -> dict:
    policy = (config.get("discovery") or {}).get("genuine_source_diversity") or {}
    if not policy:
        return {"required": False, "passed": True, "state": "NOT_CONFIGURED"}

    minimum_sources = int(policy.get("minimum_sources", 1))
    minimum_per_source = int(policy.get("minimum_watches_per_source", 1))
    minimum_total = int(policy.get("minimum_acquired_watches", 1))
    max_share = float(policy.get("max_single_source_share", 1.0))

    watches: dict[str, set[str]] = defaultdict(set)
    if candidate_summary.exists():
        with candidate_summary.open(newline="", encoding="utf-8") as fh:
            for row in csv.DictReader(fh):
                cls = (row.get("class_label") or row.get("class") or "").strip().lower()
                if cls != "gen":
                    continue
                try:
                    n = int(row.get("images_acquired") or 0)
                except ValueError:
                    n = 0
                if n <= 0:
                    continue
                source = (row.get("source_name") or "unknown").strip() or "unknown"
                wid = (row.get("physical_watch_id") or row.get("candidate_id") or "").strip()
                if wid:
                    watches[source].add(wid)

    by_source = {k: len(v) for k, v in sorted(watches.items())}
    total = sum(by_source.values())
    qualifying = {k: n for k, n in by_source.items() if n >= minimum_per_source}
    dominant_source = max(by_source, key=by_source.get) if by_source else ""
    dominant_count = by_source.get(dominant_source, 0)
    dominant_share = dominant_count / total if total else 1.0

    reasons: list[str] = []
    if total < minimum_total:
        reasons.append(f"only {total} acquired genuine watches; need at least {minimum_total}")
    if len(qualifying) < minimum_sources:
        reasons.append(
            f"only {len(qualifying)} genuine sources have at least {minimum_per_source} acquired watches; "
            f"need {minimum_sources}"
        )
    if total and dominant_share > max_share:
        reasons.append(
            f"{dominant_source} supplies {dominant_share:.1%} of acquired genuine watches; "
            f"maximum allowed is {max_share:.1%}"
        )
    elif not total:
        reasons.append("no acquired genuine watches")

    passed = not reasons
    return {
        "required": True,
        "passed": passed,
        "state": "ADEQUATE" if passed else "NEEDS_MORE_SOURCE_DIVERSITY",
        "acquired_genuine_watches": total,
        "by_source": by_source,
        "qualifying_sources": qualifying,
        "qualifying_source_count": len(qualifying),
        "dominant_source": dominant_source,
        "dominant_source_share": round(dominant_share, 6) if total else None,
        "policy": {
            "minimum_sources": minimum_sources,
            "minimum_watches_per_source": minimum_per_source,
            "minimum_acquired_watches": minimum_total,
            "max_single_source_share": max_share,
        },
        "reasons": reasons,
    }


def save(report: dict, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
