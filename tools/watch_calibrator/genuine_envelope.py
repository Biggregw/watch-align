"""Conservative genuine-envelope calibration for Watch Align.

Product rule: Watch Align is replica QC, not a perfection grader. A reliable value already
observed on a genuine watch is part of normal genuine variation for this product, even if it is
an imperfection. Therefore every retained genuine observation contributes to the final envelope.

Only an obvious within-watch measurement failure may be removed: one photo must be a very large
outlier against at least three other reliable photos of the same physical watch. There is no
cross-watch statistical trimming. Development/validation/holdout splits remain useful diagnostics,
but they do not make genuine observations disappear from the final production envelope.

Replica data is stress evidence only and never moves a genuine-derived limit.

Photos are identified by their acquisition-manifest local_path and image SHA-256, never by a
workspace path, so a live run and an offline replay of the same frozen evidence produce identical
calibration bytes.
"""
from __future__ import annotations

import csv
import json
import math
import statistics
from collections import defaultdict
from collections.abc import Mapping
from pathlib import Path

MAD_TO_SIGMA = 1.4826


def _f(v):
    try:
        x = float(v)
        return x if math.isfinite(x) else math.nan
    except (TypeError, ValueError):
        return math.nan


def _rows(path: Path):
    if not path or not path.exists():
        return []
    with path.open(newline="", encoding="utf-8") as fh:
        return list(csv.DictReader(fh))


def _percentile(values: list[float], q: float) -> float:
    vals = sorted(x for x in values if math.isfinite(x))
    if not vals:
        return math.nan
    if len(vals) == 1:
        return vals[0]
    pos = max(0.0, min(1.0, q)) * (len(vals) - 1)
    lo = int(math.floor(pos))
    hi = int(math.ceil(pos))
    if lo == hi:
        return vals[lo]
    w = pos - lo
    return vals[lo] * (1.0 - w) + vals[hi] * w


def _source_map(split_csv: Path) -> dict[str, str]:
    out = {}
    for r in _rows(split_csv):
        wid = (r.get("physical_watch_id") or "").strip()
        if wid:
            out[wid] = (r.get("source_name") or "").strip()
    return out


def _photo(photo_identity: Mapping[str, dict], path_text: str) -> dict:
    identity = photo_identity.get(path_text)
    if identity is None:
        raise ValueError(f"measured photo {path_text!r} has no workspace-independent identity")
    return {"local_path": identity["local_path"], "image_sha256": identity["image_sha256"]}


def _collect(photo_files: dict[str, Path], metrics: list[str],
             photo_identity: Mapping[str, dict]) -> dict[str, list[dict]]:
    out: dict[str, list[dict]] = {m: [] for m in metrics}
    for partition, path in photo_files.items():
        for r in _rows(path):
            wid = (r.get("physical_watch_id") or "").strip()
            if not wid:
                continue
            photo = None
            for metric in metrics:
                value = _f(r.get(metric))
                if math.isfinite(value):
                    if photo is None:
                        photo = _photo(photo_identity, r.get("path") or "")
                    out[metric].append({
                        "physical_watch_id": wid,
                        "partition": partition,
                        "value": value,
                        "photo": photo,
                    })
    return out


def _obvious_outlier_filter(records: list[dict], spec: dict, policy: dict) -> tuple[list[dict], list[dict]]:
    """Remove only a single unmistakable photo-level spike within one physical watch.

    A watch needs at least four reliable photos for the metric. The farthest photo must exceed both
    an eight-robust-sigma rule (configurable) and a metric-sized absolute floor, while at least three
    sibling photos form a tight cluster around the watch median. Single-photo and two/three-photo
    watches are never trimmed, and a watch that is consistently unusual is always retained.
    """
    by_watch: dict[str, list[dict]] = defaultdict(list)
    for rec in records:
        by_watch[rec["physical_watch_id"]].append(rec)

    min_photos = int(policy.get("obvious_outlier_min_photos", 4))
    mad_k = float(policy.get("obvious_outlier_within_watch_mad_k", 8.0))
    floor_mult = float(policy.get("obvious_outlier_minimum_width_multiplier", 4.0))
    minimum_width = abs(float(spec.get("minimum_half_width", 0.0)))
    absolute_floor = max(1e-12, minimum_width * floor_mult)

    rejected_ids: set[int] = set()
    rejected: list[dict] = []

    for wid, group in by_watch.items():
        if len(group) < min_photos:
            continue
        vals = [r["value"] for r in group]
        center = statistics.median(vals)
        deviations = [abs(v - center) for v in vals]
        mad = statistics.median(deviations)
        sigma = MAD_TO_SIGMA * mad
        cutoff = max(absolute_floor, mad_k * sigma if sigma > 0 else 0.0)
        cluster_radius = max(absolute_floor / 2.0, 3.0 * sigma if sigma > 0 else 0.0)

        farthest = max(range(len(group)), key=lambda i: deviations[i])
        if deviations[farthest] <= cutoff:
            continue
        clustered_others = sum(
            i != farthest and deviations[i] <= cluster_radius
            for i in range(len(group))
        )
        if clustered_others < 3:
            continue

        rec = group[farthest]
        rejected_ids.add(id(rec))
        rejected.append({
            "physical_watch_id": wid,
            "partition": rec["partition"],
            "local_path": rec["photo"]["local_path"],
            "image_sha256": rec["photo"]["image_sha256"],
            "value": rec["value"],
            "watch_median": center,
            "absolute_deviation": deviations[farthest],
            "cutoff": cutoff,
            "reason": "single extreme photo disagrees with at least three reliable photos of the same genuine watch",
        })

    return [r for r in records if id(r) not in rejected_ids], rejected


def _source_counts(records: list[dict], source_by_watch: dict[str, str]) -> dict[str, int]:
    watches: dict[str, set[str]] = defaultdict(set)
    for r in records:
        wid = r["physical_watch_id"]
        source = source_by_watch.get(wid, "")
        if source:
            watches[source].add(wid)
    return {source: len(ids) for source, ids in sorted(watches.items())}


def _within_watch_guard(records: list[dict], minimum_width: float, policy: dict) -> tuple[float, dict]:
    by_watch: dict[str, list[float]] = defaultdict(list)
    for r in records:
        by_watch[r["physical_watch_id"]].append(r["value"])

    half_ranges = []
    mads = []
    for vals in by_watch.values():
        if len(vals) < 2:
            continue
        half_ranges.append((max(vals) - min(vals)) / 2.0)
        center = statistics.median(vals)
        mads.append(statistics.median(abs(x - center) for x in vals))

    q = float(policy.get("genuine_envelope_within_watch_quantile", 0.90))
    range_guard = _percentile(half_ranges, q)
    mad_guard = 2.0 * MAD_TO_SIGMA * statistics.median(mads) if mads else math.nan
    candidates = [abs(minimum_width)]
    if math.isfinite(range_guard):
        candidates.append(range_guard)
    if math.isfinite(mad_guard):
        candidates.append(mad_guard)
    guard = max(candidates)
    return guard, {
        "multi_photo_watches": len(half_ranges),
        "within_watch_half_range_p90": range_guard if math.isfinite(range_guard) else None,
        "within_watch_mad_guard": mad_guard if math.isfinite(mad_guard) else None,
    }


def _pose_sensitive_metrics(repeatability_files: dict[str, Path]) -> dict[str, list[str]]:
    out: dict[str, list[str]] = defaultdict(list)
    for partition, path in repeatability_files.items():
        for r in _rows(path):
            if (r.get("pose_sensitive") or "").strip():
                metric = (r.get("metric") or "").strip()
                if metric:
                    out[metric].append(partition)
    return dict(out)


def _split_diagnostics(records: list[dict]) -> dict:
    out = {}
    by_part: dict[str, list[dict]] = defaultdict(list)
    for r in records:
        by_part[r["partition"]].append(r)
    for part in ("development", "validation", "holdout"):
        vals = [r["value"] for r in by_part.get(part, [])]
        out[part] = {
            "photos": len(vals),
            "watches": len({r["physical_watch_id"] for r in by_part.get(part, [])}),
            "observed_min": min(vals) if vals else None,
            "observed_max": max(vals) if vals else None,
        }
    dev = [r["value"] for r in by_part.get("development", [])]
    if dev:
        lo, hi = min(dev), max(dev)
        for part in ("validation", "holdout"):
            vals = [r["value"] for r in by_part.get(part, [])]
            out[part]["outside_raw_development_envelope"] = sum(v < lo or v > hi for v in vals)
    return out


def _replica_stress(records: list[dict], lo, hi, check_lo, check_hi) -> dict:
    def within(v, a, b):
        return (a is None or v >= a) and (b is None or v <= b)
    vals = [r["value"] for r in records]
    return {
        "photos": len(vals),
        "watches": len({r["physical_watch_id"] for r in records}),
        "outside_clear_photos": sum(not within(v, lo, hi) for v in vals),
        "outside_check_photos": sum(not within(v, check_lo, check_hi) for v in vals),
        "outside_clear_rate": (sum(not within(v, lo, hi) for v in vals) / len(vals)) if vals else None,
        "outside_check_rate": (sum(not within(v, check_lo, check_hi) for v in vals) / len(vals)) if vals else None,
    }


def build(config: dict,
          genuine_photo_files: dict[str, Path],
          split_csv: Path,
          repeatability_files: dict[str, Path] | None = None,
          replica_photo_files: dict[str, Path] | None = None,
          *,
          photo_identity: Mapping[str, dict]) -> dict:
    """photo_identity maps each measured photo's workspace path text to its stable identity."""
    metrics = [s["metric"] for s in config["calibration_metrics"]]
    specs = {s["metric"]: s for s in config["calibration_metrics"]}
    genuine = _collect(genuine_photo_files, metrics, photo_identity)
    replica = _collect(replica_photo_files or {}, metrics, photo_identity)
    source_by_watch = _source_map(split_csv)
    pose_sensitive = _pose_sensitive_metrics(repeatability_files or {})
    policy = config.get("calibration_policy") or {}
    diversity = (config.get("discovery") or {}).get("genuine_source_diversity") or {}
    min_watches = int(policy.get("genuine_envelope_min_watches", 8))
    min_sources = int(policy.get("genuine_envelope_min_sources", min(3, int(diversity.get("minimum_sources", 3)))))
    strong_mult = float(policy.get("genuine_envelope_strong_guard_multiplier", 2.0))

    result = {
        "model": config["model"],
        "family": config["family"],
        "state": "NO_CALIBRATABLE_METRICS",
        "method": "all_reliable_genuine_photo_envelope_v1",
        "principle": (
            "all reliable genuine observations define normality; only obvious within-watch measurement failures are removed; "
            "CHECK begins outside the retained genuine envelope plus a repeatability guard; replicas never move limits"
        ),
        "policy": {
            "genuine_envelope_min_watches": min_watches,
            "genuine_envelope_min_sources": min_sources,
            "obvious_outlier_min_photos": int(policy.get("obvious_outlier_min_photos", 4)),
            "obvious_outlier_within_watch_mad_k": float(policy.get("obvious_outlier_within_watch_mad_k", 8.0)),
            "obvious_outlier_minimum_width_multiplier": float(policy.get("obvious_outlier_minimum_width_multiplier", 4.0)),
            "genuine_envelope_within_watch_quantile": float(policy.get("genuine_envelope_within_watch_quantile", 0.90)),
            "genuine_envelope_strong_guard_multiplier": strong_mult,
        },
        "metrics": {},
    }

    ready = 0
    for metric in metrics:
        spec = specs[metric]
        retained, rejected = _obvious_outlier_filter(genuine.get(metric, []), spec, policy)
        vals = [r["value"] for r in retained]
        watches = {r["physical_watch_id"] for r in retained}
        by_source = _source_counts(retained, source_by_watch)
        rec = {
            "metric": metric,
            "app_key": spec.get("app_key", metric),
            "sided": spec.get("sided", "two"),
            "status": "INSUFFICIENT_GENUINE_ENVELOPE",
            "genuine_photos": len(vals),
            "genuine_watches": len(watches),
            "genuine_sources": len(by_source),
            "genuine_by_source": by_source,
            "obvious_photo_outliers_rejected": len(rejected),
            "obvious_photo_outliers": rejected,
            "pose_sensitive_partitions": pose_sensitive.get(metric, []),
            "split_diagnostics": _split_diagnostics(retained),
        }
        if len(watches) < min_watches:
            rec["reason"] = f"only {len(watches)} genuine watches produced a reliable value; need {min_watches}"
            result["metrics"][metric] = rec
            continue
        if len(by_source) < min_sources:
            rec["reason"] = f"only {len(by_source)} genuine sources produced a reliable value; need {min_sources}"
            result["metrics"][metric] = rec
            continue

        observed_min, observed_max = min(vals), max(vals)
        guard, guard_evidence = _within_watch_guard(retained, float(spec.get("minimum_half_width", 0.0)), policy)
        strong_guard = guard * strong_mult
        sided = spec.get("sided", "two")
        if sided == "upper":
            clear_low = check_low = None
        else:
            clear_low = observed_min - guard
            check_low = observed_min - strong_guard
        clear_high = observed_max + guard
        check_high = observed_max + strong_guard

        rec.update({
            "status": "CALIBRATED_GENUINE_ENVELOPE",
            "observed_genuine_min": observed_min,
            "observed_genuine_max": observed_max,
            "repeatability_guard": guard,
            "strong_guard": strong_guard,
            "guard_evidence": guard_evidence,
            "clear_low": clear_low,
            "clear_high": clear_high,
            "check_low": check_low,
            "check_high": check_high,
            "warning_contract": "no retained genuine observation reaches CHECK; CHECK starts beyond observed genuine variation plus the repeatability guard",
        })
        rep_stress = _replica_stress(replica.get(metric, []), clear_low, clear_high, check_low, check_high)
        rec["replica_stress"] = rep_stress
        rec["utility"] = "REPLICA_SEPARATION_OBSERVED" if (rep_stress.get("outside_clear_photos") or 0) > 0 else "NO_REPLICA_SEPARATION_OBSERVED"
        result["metrics"][metric] = rec
        ready += 1

    result["calibrated_metric_count"] = ready
    if ready:
        result["state"] = "GENUINE_ENVELOPE_READY"
    return result


def markdown(result: dict) -> str:
    lines = [
        f"# Watch-family calibration: {result['model']}",
        "",
        f"State: **{result['state']}**",
        "",
        "**Product rule:** a reliable value observed on a genuine watch is normal for replica QC, even when it is a small genuine imperfection. Only obvious within-watch measurement failures are trimmed. CHECK starts outside the observed genuine envelope plus a repeatability guard.",
        "",
        "| metric | status | genuine watches | sources | observed genuine | CLEAR band | CHECK band | replica separation |",
        "|---|---|---:|---:|---|---|---|---|",
    ]
    for metric, rec in result["metrics"].items():
        def band(lo_key, hi_key):
            if hi_key not in rec:
                return "-"
            lo = rec.get(lo_key)
            hi = rec.get(hi_key)
            left = "-inf" if lo is None else f"{lo:.6g}"
            return f"{left} .. {hi:.6g}"
        if "observed_genuine_min" in rec:
            observed = f"{rec['observed_genuine_min']:.6g} .. {rec['observed_genuine_max']:.6g}"
        else:
            observed = "-"
        lines.append(
            f"| {metric} | {rec.get('status')} | {rec.get('genuine_watches',0)} | {rec.get('genuine_sources',0)} | {observed} | {band('clear_low','clear_high')} | {band('check_low','check_high')} | {rec.get('utility','-')} |"
        )
        if rec.get("reason"):
            lines.append(f"\n{rec['reason']}\n")
        if rec.get("obvious_photo_outliers_rejected"):
            lines.append(f"\nObvious photo-level outliers rejected: {rec['obvious_photo_outliers_rejected']}.\n")
    return "\n".join(lines) + "\n"


def save(result: dict, path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    path.with_suffix(".md").write_text(markdown(result), encoding="utf-8")
