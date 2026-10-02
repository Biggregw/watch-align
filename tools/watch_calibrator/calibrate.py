"""Robust automatic Watch Align calibration.

Thresholds are derived only from genuine development watches. Validation can reject them but never
move them. Holdout can confirm or reject frozen thresholds but never change them. Replica watches are
reported only as stress tests and never influence a limit.

Limits (repaired after run 36927036008):
* centre and spread come from development watches after robust (Hampel) outlier rejection; the
  most extreme development watch no longer sets the band (the old dev_max term is gone);
* one-sided metrics (RMS, absolute offsets) get an upper limit only;
* when genuine-source diversity is configured, every metric must itself retain enough independent
  dealer/source coverage after reliability gating and development outlier rejection;
* a metric is CALIBRATED only with sensitivity evidence: either its clear band is no wider than
  the product-set max_clear_half_width, or configured known-defect cases fall outside clear.
  Passing holdout without that evidence ends as HOLDOUT_PASSED_SENSITIVITY_UNPROVEN, never as a
  usable tolerance. The sensitivity requirement is a product decision and is never derived here.
"""
from __future__ import annotations

import csv, json, math, statistics
from collections import defaultdict
from pathlib import Path

MAD_TO_SIGMA = 1.4826


def f(v):
    try:
        x = float(v)
        return x if math.isfinite(x) else math.nan
    except Exception:
        return math.nan


def rows(path: Path):
    if not path or not path.exists():
        return []
    with path.open(newline="", encoding="utf-8") as fh:
        return list(csv.DictReader(fh))


def med(v):
    v = [x for x in v if math.isfinite(x)]
    return statistics.median(v) if v else math.nan


def metric_watch_records(path: Path):
    """metric -> [(physical_watch_id, finite median, source_name)]."""
    out = {}
    for r in rows(path):
        x = f(r.get("median"))
        m = r.get("metric", "")
        if m and math.isfinite(x):
            out.setdefault(m, []).append((
                r.get("physical_watch_id", ""),
                x,
                (r.get("source_name") or "").strip(),
            ))
    return out


def metric_watch_values(path: Path):
    """Backward-compatible two-field view used by older callers/tests."""
    return {
        m: [(wid, x) for wid, x, _source in recs]
        for m, recs in metric_watch_records(path).items()
    }


def repeatability(path: Path):
    return {r.get("metric", ""): r for r in rows(path) if r.get("metric")}


def within(x, lo, hi):
    return math.isfinite(x) and (lo is None or lo <= x) and (hi is None or x <= hi)


def hampel(vals, k):
    """Drop values more than k robust sigmas from the median (repeated until stable)."""
    keep = [x for x in vals if math.isfinite(x)]
    for _ in range(5):
        if len(keep) < 3:
            break
        m = statistics.median(keep)
        s = MAD_TO_SIGMA * statistics.median(abs(x - m) for x in keep)
        if not s > 0:
            break
        nxt = [x for x in keep if abs(x - m) <= k * s]
        if len(nxt) == len(keep):
            break
        keep = nxt
    return keep


def hampel_records(records, k):
    """Hampel filter while preserving watch/source identity."""
    keep = [r for r in records if math.isfinite(r[1])]
    for _ in range(5):
        if len(keep) < 3:
            break
        values = [r[1] for r in keep]
        m = statistics.median(values)
        s = MAD_TO_SIGMA * statistics.median(abs(x - m) for x in values)
        if not s > 0:
            break
        nxt = [r for r in keep if abs(r[1] - m) <= k * s]
        if len(nxt) == len(keep):
            break
        keep = nxt
    return keep


def source_counts(records, minimum_watches: int = 1) -> dict[str, int]:
    """Independent physical-watch count per named source for one metric."""
    watches: dict[str, set[str]] = defaultdict(set)
    for wid, _value, source in records:
        if source and wid:
            watches[source].add(wid)
    return {
        source: len(ids)
        for source, ids in sorted(watches.items())
        if len(ids) >= minimum_watches
    }


def metric_diversity_policy(config: dict) -> dict | None:
    """Per-metric defaults derived from the acquired-source gate, with optional overrides."""
    d = (config.get("discovery") or {}).get("genuine_source_diversity") or {}
    if not d:
        return None
    overall_sources = max(1, int(d.get("minimum_sources", 1)))
    return {
        "development_sources": int(d.get("metric_min_development_sources", min(3, overall_sources))),
        "validation_sources": int(d.get("metric_min_validation_sources", min(2, overall_sources))),
        "holdout_sources": int(d.get("metric_min_holdout_sources", min(2, overall_sources))),
        "development_watches_per_source": int(d.get("metric_min_development_watches_per_source", 2)),
        "validation_watches_per_source": int(d.get("metric_min_validation_watches_per_source", 1)),
        "holdout_watches_per_source": int(d.get("metric_min_holdout_watches_per_source", 1)),
    }


def defect_values(spec, base: Path | None):
    p = spec.get("defect_evidence")
    if not p:
        return []
    path = Path(p) if Path(p).is_absolute() or base is None else base / p
    return [
        f(r.get("value"))
        for r in rows(path)
        if r.get("metric", spec["metric"]) == spec["metric"] and math.isfinite(f(r.get("value")))
    ]


def propose(config: dict, dev_watch: Path, dev_repeat: Path, val_watch: Path,
            rep_watch: Path | None = None, evidence_root: Path | None = None) -> dict:
    dw = metric_watch_records(dev_watch)
    vw = metric_watch_records(val_watch)
    rw = metric_watch_records(rep_watch) if rep_watch else {}
    dr = repeatability(dev_repeat)
    policy = config["calibration_policy"]
    diversity = metric_diversity_policy(config)
    result = {
        "model": config["model"],
        "family": config["family"],
        "state": "NO_CALIBRATABLE_METRICS",
        "metrics": {},
        "policy": policy,
        "metric_source_diversity_policy": diversity,
        "principle": "development genuine fixes limits; validation may reject only; replica never moves limits; sensitivity evidence required",
    }
    ready = 0
    k = float(policy.get("outlier_mad_k", 3.5))
    for spec in config["calibration_metrics"]:
        name = spec["metric"]
        raw_records = dw.get(name, [])
        val_records = vw.get(name, [])
        raw = [x for _wid, x, _source in raw_records]
        val = [x for _wid, x, _source in val_records]
        rr = dr.get(name, {})
        sided = spec.get("sided", "two")
        rec = {
            "metric": name,
            "app_key": spec.get("app_key", name),
            "status": "INSUFFICIENT",
            "sided": sided,
            "development_watches": len(raw),
            "validation_watches": len(val),
        }
        if rr.get("repeatability_class") == "insufficient data":
            rec["reason"] = "repeatability study insufficient"
            result["metrics"][name] = rec
            continue
        if rr.get("pose_sensitive") and not spec.get("allow_pose_sensitive", False):
            rec["reason"] = "metric is pose/scale sensitive in development"
            rec["pose_sensitive"] = rr.get("pose_sensitive")
            result["metrics"][name] = rec
            continue

        inlier_records = hampel_records(raw_records, k)
        vals = [x for _wid, x, _source in inlier_records]
        rec["development_inliers"] = len(vals)
        rec["development_outliers_rejected"] = len(raw) - len(vals)

        if len(vals) < int(policy["min_development_watches"]) or len(val) < int(policy["min_validation_watches"]):
            rec["reason"] = "not enough independent genuine watches"
            result["metrics"][name] = rec
            continue

        if diversity:
            dev_sources = source_counts(inlier_records, diversity["development_watches_per_source"])
            val_sources = source_counts(val_records, diversity["validation_watches_per_source"])
            rec["development_sources"] = len(dev_sources)
            rec["development_by_source"] = dev_sources
            rec["validation_sources"] = len(val_sources)
            rec["validation_by_source"] = val_sources
            if len(dev_sources) < diversity["development_sources"]:
                rec["reason"] = (
                    "not enough genuine source diversity for this metric after development "
                    f"reliability/outlier gates ({len(dev_sources)} sources; need {diversity['development_sources']})"
                )
                result["metrics"][name] = rec
                continue
            if len(val_sources) < diversity["validation_sources"]:
                rec["reason"] = (
                    "not enough genuine source diversity for this metric in validation "
                    f"({len(val_sources)} sources; need {diversity['validation_sources']})"
                )
                result["metrics"][name] = rec
                continue

        center = statistics.median(vals)
        sigma = MAD_TO_SIGMA * statistics.median(abs(x - center) for x in vals)
        within_mad = f(rr.get("within_watch_mad_median"))
        pert90 = f(rr.get("perturbation_range_p90"))
        noise = max(
            MAD_TO_SIGMA * within_mad if math.isfinite(within_mad) else 0.0,
            0.5 * pert90 if math.isfinite(pert90) else 0.0,
        )
        spread = max(sigma, noise)
        floor = float(spec.get("minimum_half_width", 0.0))
        clear_half = max(floor, float(policy["clear_sigma"]) * spread)
        check_half = max(
            clear_half * float(policy["check_over_clear"]),
            float(policy["check_sigma"]) * spread,
        )
        if sided == "upper":
            lo, clo = None, None
        else:
            lo, clo = center - clear_half, center - check_half
        hi, chi = center + clear_half, center + check_half
        vclear = sum(within(x, lo, hi) for x in val) / len(val)
        vcheck = sum(within(x, clo, chi) for x in val) / len(val)
        rec.update({
            "center": center,
            "clear_low": lo,
            "clear_high": hi,
            "check_low": clo,
            "check_high": chi,
            "noise_floor": noise,
            "between_watch_sigma_robust": sigma,
            "clear_half_width": clear_half,
            "validation_clear_rate": vclear,
            "validation_check_rate": vcheck,
        })

        basis = []
        ok = True
        cap = spec.get("max_clear_half_width")
        if cap is not None:
            basis.append(f"clear half-width {clear_half:.6g} vs product cap {float(cap):.6g}")
            ok = ok and clear_half <= float(cap)
        defects = defect_values(spec, evidence_root)
        if defects:
            rate = sum(not within(x, lo, hi) for x in defects) / len(defects)
            rec["defect_cases"] = len(defects)
            rec["defect_outside_clear_rate"] = rate
            basis.append(f"{len(defects)} known-defect cases, {rate:.0%} outside clear")
            ok = ok and rate >= float(policy.get("defect_flag_rate_min", 1.0))
        rec["sensitivity_basis"] = basis
        rec["sensitivity_proven"] = bool(basis) and ok
        if vcheck < float(policy["validation_check_rate_min"]) or vclear < float(policy["validation_clear_rate_min"]):
            rec["status"] = "REJECTED_VALIDATION"
            rec["reason"] = "frozen genuine-development limits did not validate"
            result["metrics"][name] = rec
            continue
        if basis and not ok:
            rec["status"] = "REJECTED_SENSITIVITY"
            rec["reason"] = "band too wide to flag the required deviation / known defects"
            result["metrics"][name] = rec
            continue
        rec["status"] = "FROZEN_PENDING_HOLDOUT"
        ready += 1
        rep = [x for _wid, x, _source in rw.get(name, [])]
        if rep:
            rec["replica_stress_watches"] = len(rep)
            rec["replica_outside_clear_rate"] = sum(not within(x, lo, hi) for x in rep) / len(rep)
            rec["replica_outside_check_rate"] = sum(not within(x, clo, chi) for x in rep) / len(rep)
        result["metrics"][name] = rec
    if ready:
        result["state"] = "FROZEN_PENDING_HOLDOUT"
    return result


def finalize(frozen: dict, holdout_watch: Path) -> dict:
    hw = metric_watch_records(holdout_watch)
    policy = frozen["policy"]
    diversity = frozen.get("metric_source_diversity_policy")
    calibrated = 0
    unproven = 0
    out = json.loads(json.dumps(frozen))
    for name, rec in out["metrics"].items():
        if rec.get("status") != "FROZEN_PENDING_HOLDOUT":
            continue
        hold_records = hw.get(name, [])
        vals = [x for _wid, x, _source in hold_records]
        rec["holdout_watches"] = len(vals)
        if len(vals) < int(policy["min_holdout_watches"]):
            rec["status"] = "INSUFFICIENT_HOLDOUT"
            rec["reason"] = "not enough independent holdout watches"
            continue
        if diversity:
            hold_sources = source_counts(hold_records, diversity["holdout_watches_per_source"])
            rec["holdout_sources"] = len(hold_sources)
            rec["holdout_by_source"] = hold_sources
            if len(hold_sources) < diversity["holdout_sources"]:
                rec["status"] = "INSUFFICIENT_HOLDOUT_SOURCE_DIVERSITY"
                rec["reason"] = (
                    "not enough genuine source diversity for this metric in untouched holdout "
                    f"({len(hold_sources)} sources; need {diversity['holdout_sources']})"
                )
                continue
        clear = sum(within(x, rec["clear_low"], rec["clear_high"]) for x in vals) / len(vals)
        check = sum(within(x, rec["check_low"], rec["check_high"]) for x in vals) / len(vals)
        rec["holdout_clear_rate"] = clear
        rec["holdout_check_rate"] = check
        if not (
            check >= float(policy["holdout_check_rate_min"])
            and clear >= float(policy["holdout_clear_rate_min"])
        ):
            rec["status"] = "REJECTED_HOLDOUT"
            rec["reason"] = "frozen thresholds failed untouched holdout"
        elif rec.get("sensitivity_proven"):
            rec["status"] = "CALIBRATED"
            calibrated += 1
        else:
            rec["status"] = "HOLDOUT_PASSED_SENSITIVITY_UNPROVEN"
            unproven += 1
            rec["reason"] = (
                "genuine watches stay clear, but nothing shows this band can flag a deviation; not usable as a tolerance"
            )
    out["state"] = "CALIBRATED" if calibrated else (
        "SENSITIVITY_UNPROVEN" if unproven else "NO_CALIBRATABLE_METRICS"
    )
    out["calibrated_metric_count"] = calibrated
    return out


def markdown(r: dict) -> str:
    L = [
        f"# Watch-family calibration: {r['model']}",
        "",
        f"State: **{r['state']}**",
        "",
        "Limits are fixed from genuine development watches after outlier rejection. Validation and holdout can only reject them. Replica data is stress-test evidence only. A metric is CALIBRATED only with sensitivity evidence.",
        "",
    ]
    if r.get("metric_source_diversity_policy"):
        L += [
            "Per-metric genuine-source diversity is required after reliability gating and outlier rejection.",
            "",
        ]
    L += [
        "| metric | status | dev | val | holdout | clear band | check band |",
        "|---|---|---:|---:|---:|---|---|",
    ]
    for m, x in r["metrics"].items():
        def band(a, b):
            if a not in x:
                return "–"
            lo = "-inf" if x[a] is None else f"{x[a]:.6g}"
            return f"{lo} .. {x[b]:.6g}"
        L.append(
            f"| {m} | {x.get('status')} | {x.get('development_watches',0)} | {x.get('validation_watches',0)} | {x.get('holdout_watches',0)} | {band('clear_low','clear_high')} | {band('check_low','check_high')} |"
        )
        if x.get("reason"):
            L.append(f"\n{x['reason']}\n")
    return "\n".join(L) + "\n"


def save(obj: dict, path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    path.with_suffix(".md").write_text(markdown(obj), encoding="utf-8")


if __name__ == "__main__":
    import argparse
    ap = argparse.ArgumentParser()
    ap.add_argument("config", type=Path)
    ap.add_argument("dev_watch", type=Path)
    ap.add_argument("dev_repeat", type=Path)
    ap.add_argument("val_watch", type=Path)
    ap.add_argument("out", type=Path)
    ap.add_argument("--rep-watch", type=Path)
    ap.add_argument("--holdout-watch", type=Path)
    a = ap.parse_args()
    c = json.loads(a.config.read_text())
    r = propose(c, a.dev_watch, a.dev_repeat, a.val_watch, a.rep_watch)
    if a.holdout_watch:
        r = finalize(r, a.holdout_watch)
    save(r, a.out)
