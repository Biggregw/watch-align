"""Genuine 124060 geometry-calibration study, DEVELOPMENT partition only (research, issue #34).

    python3 tools/dataset_harvester/subresearch/geometry124060.py MEASUREMENT_DIR OUT_DIR \
        --review docs/research/submariner/geometry/sub124060_dev_review.csv

Input: a SubMeasure run (sub_images.csv, sub_landmarks.csv) with the frozen v2 12-triangle detector.
Only rows with family submariner_12, model 124060, class_tag gen, partition development are read;
any other row is ignored, and the script stops if none qualify. Validation and holdout are never read.

Purpose: which measurements on a genuine 124060 are repeatable enough for later research. It is not a
genuine/replica comparison and derives no threshold. The independence unit is physical_watch_id:
several photos of a watch are repeatability observations.

Per metric it separates (robust statistics):
  perturbation  spread of one photo over its synthetic variants (94/88 %, +-1/2 % shifts, +-5 deg),
                only variants whose dial is the original's edge-fitted dial
  within-watch  spread of the original photos of one physical watch (photo-to-photo: pose, light,
                detector)
  between-watch spread of the watch-level medians
and a descriptive usefulness ratio between / within, plus pose dependence:
  * within-watch centred Spearman correlation with dial axis ratio, marker-layout tilt, in-plane
    rotation of the watch in the photo and dial size in pixels (between-watch differences removed);
  * the systematic change under the synthetic 88 % rescale and the +-5 deg in-plane rotations.
Classes are descriptive labels only, never pass/fail.
"""
from __future__ import annotations

import argparse
import csv
import json
import math
import sys
from collections import defaultdict
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from subresearch.tables import forbidden_columns  # noqa: E402
from subresearch.triangle import dial_usable  # noqa: E402

SCOPE = {"family": "submariner_12", "model": "124060", "class_tag": "gen", "partition": "development"}
ROUND_HOURS = (1, 2, 4, 5, 7, 8, 10, 11)
BATON_HOURS = (3, 6, 9)
POSE_COVARIATES = ("dial_axis_ratio", "mpose_tilt_deg", "inplane_rotation_deg", "dial_r_px")
MIN_WATCHES = 5          # fewer watches: the between-watch spread is not interpreted
RATIO_SIMILAR, RATIO_CLEAR = 1.0, 2.0
# Image-frame quantities: recorded per photo, never classified (they describe the photo, not the watch).
DESCRIPTIVE_ONLY = ("dial_cx_px", "dial_cy_px", "dial_semi_major_px", "dial_semi_minor_px", "tick60_image_clock_angle_deg")


def f(v):
    try:
        x = float(v)
        return x if math.isfinite(x) else math.nan
    except (TypeError, ValueError):
        return math.nan


def wrap180(d):
    return (d + 180.0) % 360.0 - 180.0


def mad(v):
    v = np.asarray([x for x in v if math.isfinite(x)])
    return float(np.median(np.abs(v - np.median(v)))) if len(v) else math.nan


def med(v):
    v = [x for x in v if math.isfinite(x)]
    return float(np.median(v)) if v else math.nan


def pct(v, p):
    v = [x for x in v if math.isfinite(x)]
    return float(np.percentile(v, p)) if v else math.nan


def rng(v):
    v = [x for x in v if math.isfinite(x)]
    return max(v) - min(v) if len(v) >= 2 else math.nan


# ------------------------------------------------------------------------------------------ geometry
class Dial:
    """The edge-fitted dial ellipse in original-image pixels; rect() undoes the squash (like DialFrame)."""

    def __init__(self, row):
        self.cx, self.cy = f(row["fit_cx"]), f(row["fit_cy"])
        self.a, self.b = f(row["fit_a"]), f(row["fit_b"])
        t = math.radians(f(row["fit_angle_deg"]))
        self.ct, self.st = math.cos(t), math.sin(t)
        self.r = math.sqrt(self.a * self.b)

    def rect(self, x, y):
        dx, dy = x - self.cx, y - self.cy
        u, v = self.ct * dx + self.st * dy, -self.st * dx + self.ct * dy
        return u / self.a * self.r, v / self.b * self.r

    def polar(self, x, y):
        """(rho / R, clock angle deg in the rectified dial frame)."""
        u, v = self.rect(x, y)
        return math.hypot(u, v) / self.r, math.degrees(math.atan2(u, -v))


def landmark_metrics(lm: dict, dial: Dial, ref12: float, excluded: set) -> tuple[dict, dict]:
    """Per-photo/variant metrics from one variant's landmark rows. Returns (metrics, centres in the
    rectified frame by landmark id)."""
    m, cen = {}, {}
    t = lm.get("12")
    if t and t.get("detected") == "True" and "12" not in excluded:
        cls = "lume" if t.get("outline_class") == "inner" else "surround"
        rho, ang = dial.polar(f(t["x"]), f(t["y"]))
        cen["12"] = (rho, ang)
        p = f"t12_{cls}_"
        m.update({p + "rho": rho, p + "dtheta_from_60tick_deg": f(t["dtheta_from_nominal_deg"]),
                  p + "width_r": f(t["width_over_r"]), p + "height_r": f(t["length_over_r"]), p + "apex_deg": f(t["apex_deg"]),
                  p + "rotation_deg": f(t["rotation_deg"]), p + "gap_to_track_r": f(t["gap_over_r"]),
                  p + "lateral_offset_from_60tick_w": f(t["centring_raw"])})
    for h in BATON_HOURS:
        b = lm.get(f"b{h}")
        if not b or b.get("detected") != "True" or f"b{h}" in excluded or b.get("fit_path") != "edge_refit":
            continue
        rho, ang = dial.polar(f(b["x"]), f(b["y"]))
        cen[f"b{h}"] = (rho, ang)
        p = f"b{h}_"
        m.update({p + "rho": rho, p + "dtheta_deg": wrap180(ang - ref12 - h * 30) if math.isfinite(ref12) else math.nan,
                  p + "length_r": f(b["length_over_r"]), p + "width_r": f(b["width_over_r"]), p + "rotation_deg": f(b["rotation_deg"]),
                  p + "inset": f(b["inset"]), p + "gap_to_track_r": f(b["gap_over_r"])})
    for h in ROUND_HOURS:
        r = lm.get(f"r{h}")
        if not r or r.get("detected") != "True" or f"r{h}" in excluded or f(r.get("reject_fraction")) > 0.25:
            continue
        rho, ang = dial.polar(f(r["x"]), f(r["y"]))
        cen[f"r{h}"] = (rho, ang)
        p = f"r{h}_"
        m.update({p + "rho": rho, p + "dtheta_deg": wrap180(ang - ref12 - h * 30) if math.isfinite(ref12) else math.nan,
                  p + "fitted_edge_radius_r": f(r["radius_over_r"]), p + "surround_radius_r": f(r["surround_radius_over_r"]),
                  p + "lume_radius_r": f(r["inner_ring_over_r"]) if f(r.get("rings_found")) >= 2 else math.nan,
                  p + "inset": f(r["inset"]), p + "gap_to_track_r": f(r["gap_over_r"])})
    return m, cen


def relational(cen: dict, ref12: float, track_r: float) -> dict:
    m = {}
    rr = [cen[f"r{h}"][0] for h in ROUND_HOURS if f"r{h}" in cen]
    if len(rr) >= 4:
        m["ring_round_rho_median"] = med(rr)
        m["ring_round_rho_mad"] = mad(rr)
    if math.isfinite(ref12):
        dts = [wrap180(cen[f"r{h}"][1] - ref12 - h * 30) for h in ROUND_HOURS if f"r{h}" in cen]
        if len(dts) >= 4:
            mean = float(np.mean(dts))
            m["ring_round_mean_angle_offset_deg"] = mean
            m["ring_round_spacing_rms_deg"] = float(np.sqrt(np.mean([(d - mean) ** 2 for d in dts])))
    # Circle through the round-marker centres (rectified frame): offset of its centre from the dial centre.
    pts = [(c[0] * math.sin(math.radians(c[1])), -c[0] * math.cos(math.radians(c[1]))) for k, c in cen.items() if k.startswith("r")]
    if len(pts) >= 5:
        A = np.array([[x, y, 1.0] for x, y in pts]); bvec = np.array([-(x * x + y * y) for x, y in pts])
        sol, *_ = np.linalg.lstsq(A, bvec, rcond=None)
        ox, oy = -sol[0] / 2, -sol[1] / 2
        m["ring_round_circle_centre_offset_r"] = math.hypot(ox, oy)
        m["ring_round_circle_radius_r"] = math.sqrt(max(0.0, ox * ox + oy * oy - sol[2]))
    for a, b in ((1, 7), (2, 8), (4, 10), (5, 11)):
        if f"r{a}" in cen and f"r{b}" in cen:
            m[f"opp_r{a}_r{b}_angle_dev_deg"] = wrap180(cen[f"r{b}"][1] - cen[f"r{a}"][1] - 180)
            m[f"opp_r{a}_r{b}_rho_diff"] = cen[f"r{a}"][0] - cen[f"r{b}"][0]
    for a, b in ((1, 11), (2, 10), (4, 8), (5, 7)):
        if f"r{a}" in cen and f"r{b}" in cen:
            m[f"mirror_r{a}_r{b}_rho_diff"] = cen[f"r{a}"][0] - cen[f"r{b}"][0]

    def line_offset(p, q):
        (r1, a1), (r2, a2) = p, q
        x1, y1 = r1 * math.sin(math.radians(a1)), -r1 * math.cos(math.radians(a1))
        x2, y2 = r2 * math.sin(math.radians(a2)), -r2 * math.cos(math.radians(a2))
        d = math.hypot(x2 - x1, y2 - y1)
        return abs(x1 * y2 - x2 * y1) / d if d > 0 else math.nan, math.degrees(math.atan2(y2 - y1, x2 - x1))

    if "b3" in cen and "b9" in cen:
        m["b3_b9_angle_dev_deg"] = wrap180(cen["b9"][1] - cen["b3"][1] - 180)
        m["b3_b9_rho_diff"] = cen["b3"][0] - cen["b9"][0]
        m["b3_b9_line_centre_offset_r"], ang39 = line_offset(cen["b3"], cen["b9"])
    else:
        ang39 = math.nan
    if "12" in cen and "b6" in cen:
        m["t12_b6_angle_dev_deg"] = wrap180(cen["b6"][1] - cen["12"][1] - 180)
        m["t12_b6_line_centre_offset_r"], ang126 = line_offset(cen["12"], cen["b6"])
        if math.isfinite(ang39):
            m["line12_6_vs_line3_9_orthogonality_deg"] = wrap180(ang126 - ang39 - 90) if abs(wrap180(ang126 - ang39 - 90)) < 90 else wrap180(ang126 - ang39 + 90)
    br = [cen[f"b{h}"][0] for h in BATON_HOURS if f"b{h}" in cen]
    if br and len(rr) >= 4:
        m["baton_minus_round_rho"] = med(br) - med(rr)
    if math.isfinite(track_r):
        if len(rr) >= 4:
            m["track_minus_round_ring_r"] = track_r - med(rr)
        if br:
            m["track_minus_baton_rho_r"] = track_r - med(br)
    return m


def dial_metrics(img: dict, t12: dict | None) -> dict:
    R = f(img["dial_r"])
    a, b = f(img["fit_a"]), f(img["fit_b"])
    m = {"dial_axis_ratio": f(img["fit_axis_ratio"]), "dial_r_px": R, "dial_fit_rms_over_r": f(img["fit_rms_px"]) / R if R > 0 else math.nan,
         "dial_cx_px": f(img["fit_cx"]), "dial_cy_px": f(img["fit_cy"]), "dial_semi_major_px": max(a, b), "dial_semi_minor_px": min(a, b),
         "mpose_tilt_deg": f(img.get("mpose_tilt_deg")), "mpose_residual_r": f(img.get("mpose_residual_over_r"))}
    if t12 and t12.get("detected") == "True":
        m["track_radius_r"] = f(t12.get("track_r_over_r"))
        m["track_pitch_deg"] = f(t12.get("tick_pitch_deg"))
        m["track_spread_r"] = f(t12.get("track_spread_over_r"))
    for k in ("rh_w12_px", "rh_w3_px", "rh_w6_px", "rh_w9_px"):
        if R > 0:
            m["rehaut_" + k.replace("_px", "") + "_r"] = f(img.get(k)) / R
    m["rehaut_min_over_mean"] = f(img.get("rh_min_over_mean"))
    return m


# ------------------------------------------------------------------------------------------ pipeline
def load(measure_dir: Path):
    def rows(name):
        with (measure_dir / name).open(newline="", encoding="utf-8") as fh:
            return [r for r in csv.DictReader(fh) if all(r.get(k) == v for k, v in SCOPE.items())]
    imgs, lms = rows("sub_images.csv"), rows("sub_landmarks.csv")
    if not imgs:
        raise SystemExit("no development genuine 124060 rows in this measurement")
    return imgs, lms


def read_review(path: Path | None) -> dict:
    """Visual-review entries keyed by sha_prefix and by (physical_watch_id, image_index).

    The second key is stable when a dealer CDN serves the same listing photo re-encoded (new bytes,
    same picture), so a review of the fixed local image set still applies to a CI re-download."""
    out = {}
    if path and path.exists():
        with path.open(newline="", encoding="utf-8") as fh:
            for r in csv.DictReader(fh):
                lms = {x.strip() for x in (r.get("excluded_landmarks") or "").split(";") if x.strip()}
                e = {"exclude_photo": "all" in lms, "landmarks": lms, "reason": r.get("reason", ""), "reviewed": True}
                out[r["sha_prefix"]] = e
                if r.get("physical_watch_id") and r.get("image_index") not in (None, ""):
                    out[(r["physical_watch_id"], str(r["image_index"]))] = e
    return out


def review_for(review: dict, sha: str, watch: str, image_index) -> dict:
    e = review.get(sha[:10]) or review.get((watch, str(image_index)))
    return e or {"exclude_photo": False, "landmarks": set(), "reason": "", "reviewed": False}


def photo_records(imgs, lms, review):
    by = defaultdict(dict)
    for r in imgs:
        by[r["sha256"]][r["variant"]] = r
    L = defaultdict(dict)
    for r in lms:
        L[(r["sha256"], r["variant"])][r["landmark"]] = r
    photos, excluded = [], defaultdict(list)
    for sha, vs in sorted(by.items()):
        o = vs.get("orig")
        if o is None or o.get("dial_found") != "True":
            excluded["no_dial"].append(sha)
            continue
        rv = review_for(review, sha, o["physical_watch_id"], o["image_index"])
        if o.get("edge_fit_valid") != "True" or o.get("dial_source") != "edge_fit":
            excluded["dial_not_edge_fitted_or_fallback"].append(sha)
            continue
        if rv["exclude_photo"]:
            excluded["review:" + (rv["reason"] or "excluded")].append(sha)
            continue
        per_variant = {}
        for v, img in vs.items():
            if not dial_usable(img, o):
                continue
            lm = L[(sha, v)]
            t12 = lm.get("12")
            dial = Dial(img)
            ref12 = math.nan
            if t12 and t12.get("detected") == "True" and "12" not in rv["landmarks"]:
                _, ang = dial.polar(f(t12["x"]), f(t12["y"]))
                ref12 = ang - f(t12["dtheta_from_nominal_deg"])
            m, cen = landmark_metrics(lm, dial, ref12, rv["landmarks"])
            m.update(relational(cen, ref12, f(t12.get("track_r_over_r")) if t12 else math.nan))
            m.update(dial_metrics(img, t12))
            # 60-tick direction as an image clock angle (rectified-frame angle + ellipse orientation).
            m["tick60_image_clock_angle_deg"] = wrap180(ref12 + f(img["fit_angle_deg"]))
            if t12 and t12.get("detected") == "True":
                # Image-space rotation of the watch: clock angle of the triangle centre about the dial centre.
                m["inplane_rotation_deg"] = math.degrees(math.atan2(f(t12["x"]) - dial.cx, dial.cy - f(t12["y"])))
            per_variant[v] = m
        if "orig" not in per_variant:
            excluded["original_not_usable"].append(sha)
            continue
        photos.append({"sha256": sha, "physical_watch_id": o["physical_watch_id"], "source_id": o["source_id"],
                       "image_index": o["image_index"], "variants": per_variant, "review": rv})
    return photos, excluded


ANGLE_METRICS_SUFFIX = ("_deg",)


def variation(values: dict, metric: str, base: str = "orig"):
    v = [x[metric] for x in values.values() if metric in x and math.isfinite(x[metric])]
    if metric.endswith("_deg") and v:
        ref = values.get(base, {}).get(metric, v[0])
        v = [ref + wrap180(x - ref) for x in v] if math.isfinite(ref) else v
    return v


def spearman(x, y):
    x, y = np.asarray(x, float), np.asarray(y, float)
    ok = np.isfinite(x) & np.isfinite(y)
    x, y = x[ok], y[ok]
    if len(x) < 6 or np.std(x) == 0 or np.std(y) == 0:
        return math.nan, len(x), math.nan
    rx, ry = np.argsort(np.argsort(x)), np.argsort(np.argsort(y))
    rho = float(np.corrcoef(rx, ry)[0, 1])
    rng_ = np.random.default_rng(0)
    perm = [abs(np.corrcoef(rx, rng_.permutation(ry))[0, 1]) for _ in range(2000)]
    p = (1 + sum(1 for q in perm if q >= abs(rho))) / (1 + len(perm))
    return rho, len(x), p


def analyse(photos):
    metrics = sorted({k for p in photos for k in p["variants"]["orig"]} - set(POSE_COVARIATES) - {"dial_r_px"})
    metrics += [c for c in POSE_COVARIATES if c not in metrics]
    watches = defaultdict(list)
    for p in photos:
        watches[p["physical_watch_id"]].append(p)
    photo_rows, watch_rows, rep_rows = [], [], []
    for mname in metrics:
        per_photo = []
        for p in photos:
            o = p["variants"]["orig"]
            if mname not in o or not math.isfinite(o[mname]):
                continue
            vv = variation(p["variants"], mname)
            shifts = {}
            for v in ("s88", "r+5", "r-5"):
                x = p["variants"].get(v, {}).get(mname)
                if x is not None and math.isfinite(x):
                    d = x - o[mname]
                    shifts[v] = wrap180(d) if mname.endswith("_deg") else d
            row = {"physical_watch_id": p["physical_watch_id"], "sha256": p["sha256"], "source_id": p["source_id"],
                   "image_index": p["image_index"], "visually_reviewed": p["review"].get("reviewed", False),
                   "metric": mname, "value": o[mname],
                   "perturbation_variants": len(vv), "perturbation_range": rng(vv), "perturbation_mad": mad(vv),
                   "shift_s88": shifts.get("s88", math.nan), "shift_r_plus5": shifts.get("r+5", math.nan),
                   "shift_r_minus5": shifts.get("r-5", math.nan)}
            for c in POSE_COVARIATES:
                row["pose_" + c] = o.get(c, math.nan)
            per_photo.append(row)
        photo_rows += per_photo
        byw = defaultdict(list)
        for r in per_photo:
            byw[r["physical_watch_id"]].append(r)
        wmed, within_mad, within_rng = {}, [], []
        for w, rs in sorted(byw.items()):
            vals = [r["value"] for r in rs]
            if mname.endswith("_deg"):
                ref = vals[0]
                vals = [ref + wrap180(v - ref) for v in vals]
            wmed[w] = med(vals)
            wm, wr = (mad(vals), rng(vals)) if len(vals) >= 2 else (math.nan, math.nan)
            if len(vals) >= 2:
                within_mad.append(wm)
                within_rng.append(wr)
            watch_rows.append({"physical_watch_id": w, "metric": mname, "usable_photos": len(vals), "median": wmed[w],
                               "within_watch_mad": wm, "within_watch_range": wr,
                               "perturbation_range_median": med([r["perturbation_range"] for r in rs])})
        between = list(wmed.values())
        if mname.endswith("_deg") and between:
            ref = med(between)
            between = [ref + wrap180(b - ref) for b in between]
        pert = [r["perturbation_range"] for r in per_photo]
        within = med(within_mad)
        between_mad = mad(between)
        ratio = between_mad / within if within and math.isfinite(within) and within > 0 else math.nan
        n_w, n_w2 = len(wmed), len(within_mad)
        if mname in DESCRIPTIVE_ONLY:
            cls = "descriptive only (image frame)"
        elif n_w < MIN_WATCHES or n_w2 < 2 or not math.isfinite(ratio):
            cls = "insufficient data"
        elif ratio < RATIO_SIMILAR:
            cls = "measurement noise dominates"
        elif ratio < RATIO_CLEAR:
            cls = "similar scale"
        else:
            cls = "between-watch variation clearly exceeds measurement noise"
        # Pose dependence, within-watch centred (between-watch differences removed).
        pose = {}
        for c in POSE_COVARIATES:
            if c == mname:
                continue
            xs, ys = [], []
            for w, rs in byw.items():
                if len(rs) < 2:
                    continue
                vx = [r["pose_" + c] for r in rs]
                vy = [r["value"] for r in rs]
                if mname.endswith("_deg"):
                    vy = [vy[0] + wrap180(v - vy[0]) for v in vy]
                mx, my = med(vx), med(vy)
                xs += [x - mx for x in vx]
                ys += [y - my for y in vy]
            rho_s, n, pval = spearman(xs, ys)
            pose[c] = (rho_s, n, pval)
        s88 = med([r["shift_s88"] for r in per_photo])
        rot = med([abs(r["shift_r_plus5"]) for r in per_photo] + [abs(r["shift_r_minus5"]) for r in per_photo])
        if mname in DESCRIPTIVE_ONLY:
            pose, s88, rot = {}, math.nan, math.nan
        sens = [c for c, (rs_, n, pv) in pose.items() if math.isfinite(rs_) and abs(rs_) >= 0.5 and n >= 10 and pv < 0.05]
        synth = []
        if math.isfinite(within) and within > 0:
            if math.isfinite(s88) and abs(s88) > 0.5 * within:
                synth.append("image_scale(88%)")
            if math.isfinite(rot) and rot > 0.5 * within:
                synth.append("inplane_rotation(+-5deg)")
        rep = {"metric": mname, "watches": n_w, "watches_with_2plus_photos": n_w2, "photos": len(per_photo),
               "perturbation_range_median": med(pert), "perturbation_range_p90": pct(pert, 90),
               "within_watch_mad_median": within, "within_watch_range_median": med(within_rng),
               "between_watch_median": med(between), "between_watch_mad": between_mad, "between_watch_range": rng(between),
               "usefulness_ratio_between_over_within": ratio, "repeatability_class": cls,
               "synthetic_shift_s88_median": s88, "synthetic_abs_shift_rot5_median": rot}
        for c in POSE_COVARIATES:
            if c in pose:
                rep[f"pose_spearman_{c}"], rep[f"pose_n_{c}"], rep[f"pose_p_{c}"] = pose[c]
        rep["pose_sensitive"] = ";".join(sens + synth)
        rep_rows.append(rep)
    return photo_rows, watch_rows, rep_rows


def write(path: Path, rows, cols=None):
    cols = cols or list(dict.fromkeys(k for r in rows for k in r))
    bad = forbidden_columns(cols)
    if bad:
        raise ValueError(f"refusing to write decision-like columns: {bad}")
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=cols, extrasaction="ignore")
        w.writeheader()
        for r in rows:
            w.writerow({k: ("" if isinstance(v, float) and not math.isfinite(v) else v) for k, v in r.items()})


def fmt(v, nd=4):
    return "–" if v is None or (isinstance(v, float) and not math.isfinite(v)) else (f"{v:.{nd}f}" if isinstance(v, float) else str(v))


def markdown(summ: dict, rep_rows: list) -> str:
    L = ["# Genuine 124060 geometry calibration (development only)", "", summ["note"], "",
         f"- Scope: {summ['scope']}",
         f"- Photos in scope: {summ['photos_in_scope']}; usable: {summ['usable_photos']} from {summ['watches_used']} physical watches",
         f"- Usable photos by watch: {summ['usable_photos_by_watch']}",
         f"- Excluded photos: {summ['excluded_photos']}",
         f"- Landmark exclusions from visual review: {summ['landmark_exclusions']}",
         f"- Usable photos not covered by the visual review: {summ['usable_photos_not_visually_reviewed'] or 'none'}", "",
         "Ratio = between-watch MAD of watch medians / median within-watch MAD. Descriptive only; the class "
         "labels are not pass/fail and no threshold is derived.", ""]
    for cls in ("between-watch variation clearly exceeds measurement noise", "similar scale", "measurement noise dominates",
                "insufficient data", "descriptive only (image frame)"):
        rows = [r for r in rep_rows if r["repeatability_class"] == cls]
        if not rows:
            continue
        L += [f"## {cls} ({len(rows)})", "",
              "| metric | watches | 2+ photo watches | photos | perturbation range med | within MAD | between MAD | ratio | pose-sensitive |",
              "|---|---|---|---|---|---|---|---|---|"]
        for r in sorted(rows, key=lambda r: -(r["usefulness_ratio_between_over_within"] if math.isfinite(r["usefulness_ratio_between_over_within"]) else -1)):
            L.append(f"| {r['metric']} | {r['watches']} | {r['watches_with_2plus_photos']} | {r['photos']} | {fmt(r['perturbation_range_median'])} | "
                     f"{fmt(r['within_watch_mad_median'])} | {fmt(r['between_watch_mad'])} | {fmt(r['usefulness_ratio_between_over_within'], 2)} | "
                     f"{r['pose_sensitive'] or ''} |")
        L.append("")
    return "\n".join(L)


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("measurements", type=Path)
    ap.add_argument("out", type=Path)
    ap.add_argument("--review", type=Path)
    a = ap.parse_args(argv)
    imgs, lms = load(a.measurements)
    photos, excluded = photo_records(imgs, lms, read_review(a.review))
    photo_rows, watch_rows, rep_rows = analyse(photos)
    a.out.mkdir(parents=True, exist_ok=True)
    write(a.out / "sub124060_photo_geometry.csv", photo_rows)
    write(a.out / "sub124060_watch_geometry.csv", watch_rows)
    write(a.out / "sub124060_metric_repeatability.csv", rep_rows)
    usable = defaultdict(int)
    for p in photos:
        usable[p["physical_watch_id"]] += 1
    summ = {"scope": SCOPE, "note": "Development genuine 124060 only. Descriptive repeatability; no thresholds, no replica comparison.",
            "photos_in_scope": len({r["sha256"] for r in imgs}), "usable_photos": len(photos), "watches_used": len(usable),
            "usable_photos_by_watch": dict(sorted(usable.items())),
            "excluded_photos": {k: len(v) for k, v in excluded.items()},
            "excluded_photo_ids": {k: [s[:10] for s in v] for k, v in excluded.items()},
            "usable_photos_not_visually_reviewed": [p["sha256"][:10] for p in photos if not p["review"].get("reviewed")],
            "landmark_exclusions": {p["sha256"][:10]: sorted(p["review"]["landmarks"]) for p in photos if p["review"]["landmarks"]},
            "classes": {c: [r["metric"] for r in rep_rows if r["repeatability_class"] == c] for c in sorted({r["repeatability_class"] for r in rep_rows})},
            "pose_sensitive": {r["metric"]: r["pose_sensitive"] for r in rep_rows if r["pose_sensitive"]}}
    (a.out / "sub124060_geometry_summary.json").write_text(json.dumps(summ, indent=1, default=str) + "\n", encoding="utf-8")
    (a.out / "sub124060_geometry_summary.md").write_text(markdown(summ, rep_rows) + "\n", encoding="utf-8")
    print(json.dumps({k: summ[k] for k in ("photos_in_scope", "usable_photos", "watches_used", "excluded_photos")}, indent=1))
    return 0


if __name__ == "__main__":
    sys.exit(main())
