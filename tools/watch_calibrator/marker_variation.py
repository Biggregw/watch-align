"""Genuine marker-to-marker variation from production per-marker readings (research only).

Input: GenuineMarkerDump rows (one per photo and marker) joined to the verified photo records (exact reference,
physical-watch identity). The statistical unit is the independent PHYSICAL WATCH, never the photograph:

  1. each reading is used only when production itself would use it (credible; triangle gap measured, not placed);
  2. within each watch, the photographs give the photographic (measurement) variation;
  3. each watch is represented by the median of its photographs;
  4. the distribution across watches describes manufacturing variation plus residual photographic error, and an
     estimate of the physical part is given separately (variance of the watch means minus the within-watch
     variance divided by the photos per watch);
  5. unusual genuine readings are flagged and KEPT; nothing is trimmed, averaged across markers or pooled across
     references. Fewer than 3 independent watches give no range at all.
"""
from __future__ import annotations

from collections import defaultdict
import math
import statistics

MIN_WATCHES_FOR_RANGE = 3
ROBUST_Z_FLAG = 3.5
# dimension -> (column, shapes, extra condition, replica-QC limit column, strict genuine limit column, limit sense)
DIMENSIONS = {
    "sideways_pct": ("side_pct", None, None, "side_limit_pct", "strict_side_limit_pct", "abs_max"),
    "radial_pct": ("inout_pct", ("round", "baton"), None, None, None, None),
    "radial_top_edge_pct": ("inout_pct", ("triangle",), ("radial_from_top_edge", "1"), "inout_limit_pct",
                            "strict_inout_limit_pct", "abs_max"),
    "radial_centre_fallback_pct": ("inout_pct", ("triangle",), ("radial_from_top_edge", "0"), "inout_limit_pct",
                                   "strict_inout_limit_pct", "abs_max"),
    "position_replica_qc_pct": ("pos_pct", ("round", "baton"), None, "pos_limit_pct", None, "abs_max"),
    "position_strict_pct": ("strict_pos_pct", ("round", "baton"), None, None, "strict_pos_limit_pct", "abs_max"),
    "rotation_deg": ("tilt_deg", ("baton", "triangle"), None, "tilt_limit_deg", "strict_tilt_limit_deg", "abs_max"),
    "gap_pct": ("gap_pct", ("triangle",), ("gap_from_fit", "0"), "gap_limit_pct", "strict_gap_limit_pct", "min"),
    "outline_fit_rms_px": ("fit_rms_px", None, ("source", "outer metal"), None, None, None),
    "outline_inconsistent_share": ("bad_frac", None, ("source", "outer metal"), None, None, None),
    "outline_seen_share": ("cover", None, ("source", "outer metal"), None, None, None),
}
PHOTO_DIMENSIONS = {"minute_track_scale": "track_scale", "marker_ring_turn_pct": "ring_turn_pct",
                    "marker_ring_scale": "ring_scale"}
# Quality diagnostics and photo-level geometry: reported, never recommended as manufacturing tolerances.
DIAGNOSTIC = {"outline_fit_rms_px", "outline_inconsistent_share", "outline_seen_share",
              "minute_track_scale", "marker_ring_turn_pct", "marker_ring_scale"}
MIN_DEALERS_FOR_SUFFICIENT = 2
UNCERTAINTY = {"position_replica_qc_pct": "se_pos_pct", "position_strict_pct": "se_pos_pct",
               "rotation_deg": "se_rot_deg"}


def order(item):
    """sort by reference, then hour numerically (photo-level last), then dimension"""
    k = item[0] if isinstance(item, tuple) and isinstance(item[0], tuple) else item
    hour = k[1]
    return (k[0], hour if isinstance(hour, int) else 99, k[3] if len(k) > 3 else "")


def num(v):
    try:
        x = float(v)
    except (TypeError, ValueError):
        return math.nan
    return x if math.isfinite(x) else math.nan


def percentile(values, q):
    vals = sorted(values)
    if not vals:
        return math.nan
    pos = q * (len(vals) - 1)
    lo, hi = math.floor(pos), math.ceil(pos)
    return vals[lo] if lo == hi else vals[lo] * (hi - pos) + vals[hi] * (pos - lo)


def mad(values):
    if not values:
        return math.nan
    m = statistics.median(values)
    return statistics.median(abs(x - m) for x in values)


def median_ci95(values):
    """Distribution-free 95 % interval for the median from order statistics (None below 6 values)."""
    v = sorted(values)
    n = len(v)
    if n < 6:
        return None
    best = None
    for k in range(0, n // 2):
        cover = sum(math.comb(n, i) for i in range(k + 1, n - k)) / 2 ** n
        if cover >= 0.95:
            best = (v[k], v[n - 1 - k])
    return best


def evidence_level(watches, multi_photo_watches, dealers=MIN_DEALERS_FOR_SUFFICIENT, dimension=""):
    if dimension in DIAGNOSTIC:
        return "measurement-quality or photo-level diagnostic (not a manufacturing dimension)"
    if watches < MIN_WATCHES_FOR_RANGE:
        return "insufficient: individual values only, no range"
    if watches < 10:
        return "descriptive only: too few watches for a tolerance"
    if watches < 30 or multi_photo_watches < 5:
        return "preliminary: could inform, not set, a future calibration decision"
    if dealers < MIN_DEALERS_FOR_SUFFICIENT:
        return "preliminary: one dealer only, so dealer photography cannot be separated from manufacturing"
    return "sufficient to support a future calibration decision (owner review required)"


def usable(row, dim):
    col, shapes, cond, *_ = DIMENSIONS[dim]
    if row.get("status") != "MEASURED" or row.get("credible") != "1":
        return math.nan
    if shapes and row.get("shape") not in shapes:
        return math.nan
    if cond and row.get(cond[0]) != cond[1]:
        return math.nan
    return num(row.get(col))


def analyse(rows, photos):
    """rows: dump rows; photos: sha256 -> verified photo record. Returns tables for the report and outputs."""
    groups = defaultdict(lambda: defaultdict(list))          # (reference, hour, shape, dim) -> watch -> [(value, row)]
    withheld = defaultdict(lambda: defaultdict(int))         # (reference, hour, shape) -> reason -> photos
    measured_markers = defaultdict(int)
    for r in rows:
        p = photos.get(r.get("sha256"))
        if p is None:
            continue
        ref, watch = p["reference"], p["physical_watch_id"]
        if r.get("status") != "MEASURED":
            withheld[(ref, "photo", "")][f"{r.get('status')}: {r.get('photo_reason')}"] += 1
            continue
        key = (ref, int(r["hour"]), r["shape"])
        if r.get("credible") != "1":
            withheld[key][r.get("withheld_reason") or r.get("state") or "not credible"] += 1
        else:
            measured_markers[key] += 1
        for dim in DIMENSIONS:
            x = usable(r, dim)
            if math.isfinite(x):
                groups[(ref, int(r["hour"]), r["shape"], dim)][watch].append((x, r))
    photo_level = defaultdict(lambda: defaultdict(list))
    seen = set()
    for r in rows:
        p = photos.get(r.get("sha256"))
        if p is None or r.get("status") != "MEASURED" or r.get("photo_reason") or r["sha256"] in seen:
            continue
        seen.add(r["sha256"])
        for dim, col in PHOTO_DIMENSIONS.items():
            x = num(r.get(col))
            if math.isfinite(x):
                photo_level[(p["reference"], "photo", "", dim)][p["physical_watch_id"]].append((x, r))
    groups.update(photo_level)

    photos_by_watch = {(p["reference"], p["physical_watch_id"]): p.get("dealer", "") for p in photos.values()}
    distributions, representatives, repeatability = [], [], []
    for (ref, hour, shape, dim), by_watch in sorted(groups.items(), key=order):
        reps, within_dev, ss, dof, ks = [], [], 0.0, 0, []
        for watch, pts in sorted(by_watch.items()):
            vals = [x for x, _ in pts]
            rep = statistics.median(vals)
            reps.append((watch, rep, len(vals)))
            ks.append(len(vals))
            if len(vals) >= 2:
                mean = statistics.fmean(vals)
                ss += sum((x - mean) ** 2 for x in vals)
                dof += len(vals) - 1
                within_dev += [x - rep for x in vals]
                repeatability.append({"reference": ref, "hour": hour, "shape": shape, "dimension": dim,
                                      "physical_watch_id": watch, "photos": len(vals), "median": rep,
                                      "range": max(vals) - min(vals), "mad": mad(vals),
                                      "photo_sha256": ";".join(r["sha256"] for _, r in pts)})
        values = [v for _, v, _ in reps]
        n = len(values)
        multi = sum(k >= 2 for k in ks)
        within_sd = math.sqrt(ss / dof) if dof else math.nan
        between_sd = statistics.stdev(values) if n >= 2 else math.nan
        # the variance decomposition uses watch MEANS (its noise term is within_sd^2 / photos); medians are reported
        means = [statistics.fmean([x for x, _ in pts]) for pts in by_watch.values()]
        between_mean_sd = statistics.stdev(means) if n >= 2 else math.nan
        k_h = len(ks) / sum(1 / k for k in ks) if ks else math.nan
        physical_sd = (math.sqrt(max(0.0, between_mean_sd ** 2 - within_sd ** 2 / k_h))
                       if math.isfinite(between_mean_sd) and math.isfinite(within_sd) else math.nan)
        m, md = (statistics.median(values), mad(values)) if values else (math.nan, math.nan)
        rng = n >= MIN_WATCHES_FOR_RANGE
        ci = median_ci95(values) if rng else None
        se_col = UNCERTAINTY.get(dim)
        ses = [num(r.get(se_col)) for pts in by_watch.values() for _, r in pts] if se_col else []
        ses = [s for s in ses if math.isfinite(s)]
        for watch, v, k in reps:
            z = abs(v - m) / (1.4826 * md) if rng and md and md > 0 else math.nan
            representatives.append({"reference": ref, "hour": hour, "shape": shape, "dimension": dim,
                                    "physical_watch_id": watch, "photos": k, "representative": v,
                                    "robust_z": z, "unusual_kept": bool(math.isfinite(z) and z > ROBUST_Z_FLAG),
                                    "photo_sha256": ";".join(r["sha256"] for _, r in by_watch[watch])})
        distributions.append({
            "reference": ref, "hour": hour, "shape": shape, "dimension": dim, "independent_watches": n,
            "photos": sum(ks), "multi_photo_watches": multi,
            "median": m, "mad": md if rng else math.nan,
            "min": min(values) if rng else math.nan, "max": max(values) if rng else math.nan,
            "p5": percentile(values, 0.05) if rng else math.nan, "p95": percentile(values, 0.95) if rng else math.nan,
            "median_ci95_low": ci[0] if ci else math.nan, "median_ci95_high": ci[1] if ci else math.nan,
            "between_watch_sd": between_sd, "within_watch_sd": within_sd,
            "estimated_physical_sd": physical_sd,
            "median_reported_uncertainty": statistics.median(ses) if ses else math.nan,
            "unusual_watches_kept": sum(1 for x in representatives[-n:] if x["unusual_kept"]),
            "dealers": len({photos_by_watch.get((ref, w), "") for w in by_watch}),
            "evidence": evidence_level(n, multi, len({photos_by_watch.get((ref, w), "") for w in by_watch}), dim),
            "individual_values": "" if rng else ";".join(f"{w}={v:.4f}" for w, v, _ in reps),
        })
    comparison = compare(groups)
    measured_table = [{"reference": k[0], "hour": k[1], "shape": k[2], "credible_photos": measured_markers.get(k, 0),
                       "withheld_photos": sum(withheld.get(k, {}).values()),
                       "withheld_reasons": "; ".join(f"{why} ({c})" for why, c in sorted(withheld.get(k, {}).items()))}
                      for k in sorted(set(measured_markers) | set(withheld), key=order)]
    return {"distributions": distributions, "representatives": representatives, "repeatability": repeatability,
            "comparison": comparison, "markers": measured_table}


def compare(groups):
    """Independent watches whose representative reading lies outside the FROZEN production limits (unchanged)."""
    out = []
    for (ref, hour, shape, dim), by_watch in sorted(groups.items(), key=order):
        if dim not in DIMENSIONS:
            continue
        _, _, _, qc_col, strict_col, sense = DIMENSIONS[dim]
        if not sense or not any(math.isfinite(num(r.get(c))) for c in (qc_col, strict_col) if c
                                for pts in by_watch.values() for _, r in pts):
            continue
        reps = [statistics.median([x for x, _ in pts]) for pts in by_watch.values()]
        row = {"reference": ref, "hour": hour, "shape": shape, "dimension": dim, "independent_watches": len(reps)}
        for label, col in (("replica_qc", qc_col), ("strict_genuine", strict_col)):
            limits = [num(r.get(col)) for pts in by_watch.values() for _, r in pts] if col else []
            limits = [x for x in limits if math.isfinite(x)]
            if not limits:
                row[f"{label}_limit"] = math.nan
                row[f"{label}_watches_outside"] = ""
                continue
            lim = statistics.median(limits)
            outside = sum((v < lim) if sense == "min" else (abs(v) > lim) for v in reps)
            row[f"{label}_limit"] = lim
            row[f"{label}_watches_outside"] = outside
        out.append(row)
    return out
