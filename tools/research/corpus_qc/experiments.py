"""Corpus experiments: rehaut/perspective, rectification, suitability gates, marker failure modes.

python3 tools/research/corpus_qc/experiments.py <corpus_qc.jsonl> --out DIR --parts development,validation

Every candidate is chosen on DEVELOPMENT only and then scored, unchanged, on VALIDATION.
The locked holdout is refused unless --final is given together with a frozen candidate file.

Error target (what a better pose/suitability decision should reduce): for photos of watches with
several usable views, each measurement's distance from the median of the watch's OTHER views
(leave-one-out), divided by that metric's pooled within-watch SD on development; the photo's
error is the median over its available metrics. A photo whose geometry disagrees with the other
photos of the same physical watch is a photo whose measurements should not be trusted. Class
labels are never used.
"""
from __future__ import annotations

import argparse
import json
import math
from collections import Counter, defaultdict
from pathlib import Path

import numpy as np

import metrics as M

ERR_METRICS = ("gap", "rot_deg", "sp59", "sp01", "six_centring", "six_rot", "nine_centring", "nine_rot")


def enrich(r: dict) -> dict:
    if not M.analysed(r):
        return r
    r = dict(r)
    r.update(M.rehaut(r))
    r.update(M.ellipse_components(r))
    r.update(M.bias(r))
    r.update(M.projective(r))
    if M.finite(r.get("rh_tb")) and M.finite(r.get("rh_lr")):
        r["rh_asym"] = math.hypot(r["rh_tb"], r["rh_lr"])
    if M.finite(r.get("keystone_x")) and M.finite(r.get("keystone_y")):
        r["keystone"] = math.hypot(r["keystone_x"], r["keystone_y"])
    if M.finite(r.get("frame_score")):
        r["neg_frame_score"] = -r["frame_score"]
    r["unstable_frame"] = 0.0 if r.get("stable_frame") else 1.0
    return r


def scales(dev_use) -> dict[str, float]:
    return {k: M.pooled_within_sd(M.by_watch(dev_use, k))[0] for k in ERR_METRICS}


def loo_error(use: list[dict], sc: dict) -> dict[str, float]:
    """sha -> composite leave-one-out disagreement with the other views of the same watch."""
    by = defaultdict(list)
    for r in use:
        by[r["watch"]].append(r)
    out = {}
    for w, rs in by.items():
        if len(rs) < 2:
            continue
        for r in rs:
            errs = []
            for k in ERR_METRICS:
                others = [o[k] for o in rs if o is not r and M.finite(o.get(k))]
                if M.finite(r.get(k)) and others and M.finite(sc.get(k)) and sc[k] > 0:
                    errs.append(abs(r[k] - float(np.median(others))) / sc[k])
            if errs:
                out[r["sha256"]] = float(np.median(errs))
    return out


def pert_error(all_recs, sc) -> dict[str, float]:
    """sha -> median normalised change of the QC metrics under the four perturbations."""
    base = {r["sha256"]: r for r in all_recs if r["variant"] == "original"}
    d = defaultdict(list)
    for r in all_recs:
        if r["variant"] == "original":
            continue
        b = base.get(r["sha256"])
        if not b:
            continue
        for k in ERR_METRICS:
            if M.finite(b.get(k)) and M.finite(r.get(k)) and M.finite(sc.get(k)) and sc[k] > 0:
                d[r["sha256"]].append(abs(r[k] - b[k]) / sc[k])
    return {k: float(np.median(v)) for k, v in d.items() if v}


# ------------------------------------------------------------------ rehaut / perspective study
def rehaut_study(recs: list[dict], err: dict, perr: dict) -> dict:
    ok = [r for r in recs if M.finite(r.get("rh_tb"))]
    hi = [r for r in ok if (r.get("rh_cov") or 0) >= 0.6]
    def rho(rs, a, b, fa=lambda x: x, fb=lambda x: x):
        xs = [fa(r[a]) for r in rs if M.finite(r.get(a)) and M.finite(r.get(b))]
        ys = [fb(r[b]) for r in rs if M.finite(r.get(a)) and M.finite(r.get(b))]
        return {"rho": M.spearman(xs, ys), "n": len(xs)}
    def sign_agree(rs, a, b, dead=0.0):
        pairs = [(r[a], r[b]) for r in rs if M.finite(r.get(a)) and M.finite(r.get(b)) and abs(r[a]) > dead and abs(r[b]) > 1e-9]
        return {"agree": float(np.mean([np.sign(x) == np.sign(y) for x, y in pairs])) if pairs else float("nan"), "n": len(pairs)}
    ab = abs
    out = {"images_with_rehaut": len(ok), "with_coverage_ge_0.6": len(hi)}
    for name, rs in (("all", ok), ("cov>=0.6", hi)):
        out[name] = {
            "|rh_tb| vs ellipse tilt (vertical part)": rho(rs, "rh_tb", "ell_tilt_v", ab),
            "|rh_lr| vs ellipse tilt (horizontal part)": rho(rs, "rh_lr", "ell_tilt_h", ab),
            "rh_asym vs ellipse tilt": rho(rs, "rh_asym", "ell_tilt"),
            "rh_asym vs marker-layout tilt": rho(rs, "rh_asym", "mpose_tilt"),
            "rh_tb vs marker keystone_y (signed)": rho(rs, "rh_tb", "keystone_y"),
            "rh_lr vs marker keystone_x (signed)": rho(rs, "rh_lr", "keystone_x"),
            "rh_tb vs marker top/bottom bias (signed)": rho(rs, "rh_tb", "tb_bias"),
            "rh_lr vs marker left/right bias (signed)": rho(rs, "rh_lr", "lr_bias"),
            "keystone_y vs tb_bias (signed, marker-only check)": rho(rs, "keystone_y", "tb_bias"),
            "sign(rh_tb)==sign(keystone_y) when |rh_tb|>0.05": sign_agree(rs, "rh_tb", "keystone_y", 0.05),
            "sign(rh_lr)==sign(keystone_x) when |rh_lr|>0.05": sign_agree(rs, "rh_lr", "keystone_x", 0.05),
            "sign(rh_tb)==sign(tb_bias) when |rh_tb|>0.05": sign_agree(rs, "rh_tb", "tb_bias", 0.05),
        }
    # Does rehaut say anything about which photos disagree with the other photos of their watch?
    preds = ("rh_asym", "ell_tilt", "mpose_tilt", "keystone", "affine_resid", "stab_gap_spread", "stab_rot_spread", "frame_score")
    out["predicts_within_watch_disagreement"] = {p: rho([dict(r, _e=err.get(r["sha256"])) for r in recs], p, "_e") for p in preds}
    out["predicts_perturbation_sensitivity"] = {p: rho([dict(r, _e=perr.get(r["sha256"])) for r in recs], p, "_e") for p in preds}
    # Pose labels.
    retake = [r for r in ok if r.get("pose") == "RETAKE"]
    fine = [r for r in ok if r.get("pose") in ("GOOD", "CORRECTABLE")]
    out["pose_label_auc"] = {p: {"auc_retake_gt_ok": M.auc([r.get(p) for r in fine], [r.get(p) for r in retake]),
                                 "retake": len(retake), "ok": len(fine)} for p in ("rh_asym", "ell_tilt", "mpose_tilt", "keystone")}
    return out


def fit_line(xs, ys):
    xs, ys = np.asarray(xs, float), np.asarray(ys, float)
    ok = np.isfinite(xs) & np.isfinite(ys)
    if ok.sum() < 5:
        return None
    A = np.vstack([xs[ok], np.ones(ok.sum())]).T
    (a, b), *_ = np.linalg.lstsq(A, ys[ok], rcond=None)
    return float(a), float(b)


def r2(xs, ys, ab):
    xs, ys = np.asarray(xs, float), np.asarray(ys, float)
    ok = np.isfinite(xs) & np.isfinite(ys)
    if ok.sum() < 3 or ab is None:
        return float("nan")
    pred = ab[0] * xs[ok] + ab[1]
    ss = np.sum((ys[ok] - ys[ok].mean()) ** 2)
    return float(1 - np.sum((ys[ok] - pred) ** 2) / ss) if ss > 0 else float("nan")


def rehaut_fit(dev, val) -> dict:
    """Fitted (not assumed) relationships, dev -> val."""
    out = {}
    for x, y in (("rh_tb", "keystone_y"), ("rh_lr", "keystone_x"), ("rh_tb", "tb_bias"), ("rh_lr", "lr_bias")):
        get = lambda rs, k: [r.get(k, float("nan")) for r in rs if (r.get("rh_cov") or 0) >= 0.6]
        ab = fit_line(get(dev, x), get(dev, y))
        out[f"{y} ~ {x}"] = {"slope_intercept_dev": ab, "r2_dev": r2(get(dev, x), get(dev, y), ab), "r2_val": r2(get(val, x), get(val, y), ab)}
    return out


# ------------------------------------------------------------------ rectification study
def rectification(use: list[dict], all_recs: list[dict]) -> dict:
    """Is marker geometry more repeatable across photos of one watch after ellipse / affine /
    projective normalisation? For each found round marker, its position residual against the
    master layout is computed four ways; the within-watch SD of each hour's residual (same
    marker, different photos) is pooled. Affine and projective models are fitted leave-one-out on
    the OTHER markers of the photo, so a marker never corrects itself."""
    def resid(r):
        out = {}
        src, dst, hours = M._norm_pts(r)
        if len(src) < 6:
            return out
        a, b, th = r["dial_a"] / M.dial_radius(r), r["dial_b"] / M.dial_radius(r), math.radians(r.get("dial_angle_deg") or 0)
        c, s = math.cos(th), math.sin(th)
        for i, h in enumerate(hours):
            m = src[i]            # master position, already at the round-marker radius (driver)
            o = dst[i]
            circ = o - m
            # ellipse normalisation: undo the dial ellipse (axes a,b at angle th) before comparing
            u = np.array([c * o[0] + s * o[1], -s * o[0] + c * o[1]])
            u = np.array([u[0] / a, u[1] / b])
            e = np.array([c * u[0] - s * u[1], s * u[0] + c * u[1]]) - m
            keep = [k for k in range(len(src)) if k != i]
            P = M.fit_affine(src[keep], dst[keep])
            aff = o - M.apply_affine(P, m[None, :])[0]
            H = M.fit_homography(src[keep], dst[keep])
            hom = o - M.apply_h(H, m[None, :])[0] if H is not None else np.array([np.nan, np.nan])
            out[h] = {"circle": circ, "ellipse": e, "affine_loo": aff, "homography_loo": hom}
        return out
    per = defaultdict(lambda: defaultdict(list))      # method -> (watch,hour) -> [vec]
    for r in use:
        for h, d in resid(r).items():
            for meth, v in d.items():
                if np.all(np.isfinite(v)):
                    per[meth][(r["watch"], h)].append(v)
    res = {}
    for meth, groups in per.items():
        ss, df, n = 0.0, 0, 0
        for vs in groups.values():
            if len(vs) >= 2:
                V = np.array(vs)
                ss += float(np.sum((V - V.mean(axis=0)) ** 2))
                df += 2 * (len(vs) - 1)
                n += 1
        res[meth] = {"within_watch_sd_radius_units": math.sqrt(ss / df) if df else float("nan"), "marker_groups": n}
    # perturbation stability of the same residuals
    base = {r["sha256"]: r for r in all_recs if r["variant"] == "original"}
    pd = defaultdict(list)
    for r in all_recs:
        if r["variant"] == "original" or not M.analysed(r) or r["sha256"] not in base or not M.analysed(base[r["sha256"]]):
            continue
        a, b = resid(base[r["sha256"]]), resid(r)
        for h in set(a) & set(b):
            for meth in a[h]:
                v = a[h][meth] - b[h][meth]
                if np.all(np.isfinite(v)):
                    pd[meth].append(float(np.linalg.norm(v)))
    for meth, v in pd.items():
        res[meth]["perturbation_p90"] = M.q(v, 90)
    return res


# ------------------------------------------------------------------ candidate gates
def gate_eval(recs, err, score_key, thr, higher_is_worse=True):
    """Among photos the current pipeline keeps (usable), what happens if photos with score beyond
    thr are also withheld: retention and mean error of what is kept."""
    kept_before = [r for r in recs if r["sha256"] in err]
    def bad(r):
        v = r.get(score_key)
        return M.finite(v) and ((v > thr) if higher_is_worse else (v < thr))
    kept_after = [r for r in kept_before if not bad(r)]
    eb = [err[r["sha256"]] for r in kept_before]
    ea = [err[r["sha256"]] for r in kept_after]
    removed = [err[r["sha256"]] for r in kept_before if bad(r)]
    return {"n_before": len(eb), "n_after": len(ea), "retention": len(ea) / len(eb) if eb else float("nan"),
            "mean_err_before": float(np.mean(eb)) if eb else float("nan"), "mean_err_after": float(np.mean(ea)) if ea else float("nan"),
            "p90_err_before": M.q(eb, 90), "p90_err_after": M.q(ea, 90),
            "mean_err_removed": float(np.mean(removed)) if removed else float("nan"), "removed": len(removed),
            "watches_losing_all_views": len({r["watch"] for r in kept_before} - {r["watch"] for r in kept_after})}


def choose_threshold(recs, err, key, min_retention=0.8):
    vals = sorted({r[key] for r in recs if r["sha256"] in err and M.finite(r.get(key))})
    best = None
    for t in vals:
        g = gate_eval(recs, err, key, t)
        if g["retention"] < min_retention or g["watches_losing_all_views"] > 0:
            continue
        gain = g["mean_err_before"] - g["mean_err_after"]
        if best is None or gain > best[1]:
            best = (t, gain)
    return best[0] if best else None


# ------------------------------------------------------------------ marker failure modes
def marker_failures(recs):
    c = Counter()
    per_hour = defaultdict(Counter)
    for r in recs:
        if not M.analysed(r):
            continue
        for m in r.get("round", []):
            if not m.get("found"):
                reason = (m.get("reason") or "?").split(":")[0]
                import re
                reason = re.sub(r"[\d.]+", "#", reason)[:80]
                c[reason] += 1
                per_hour[int(m["hour"])][reason] += 1
    return {"not_found_reasons": dict(c.most_common(10)),
            "by_hour_top": {h: dict(v.most_common(2)) for h, v in sorted(per_hour.items())}}


def run(all_recs, parts):
    all_recs = [enrich(r) for r in all_recs]
    dev_all = [r for r in all_recs if r["partition"] == "development"]
    val_all = [r for r in all_recs if r["partition"] == "validation"]
    orig = lambda rs: [r for r in rs if r["variant"] == "original" and r["sample_role"] == "population"]
    use = lambda rs: [r for r in orig(rs) if r["usable"] and M.analysed(r)]
    sc = scales(use(dev_all))
    out = {"error_scales_from_dev": sc}
    err_dev, err_val = loo_error(use(dev_all), sc), loo_error(use(val_all), sc)
    perr_dev, perr_val = pert_error(dev_all, sc), pert_error(val_all, sc)
    out["rehaut_dev"] = rehaut_study(orig(dev_all), err_dev, perr_dev)
    if "validation" in parts:
        out["rehaut_val"] = rehaut_study(orig(val_all), err_val, perr_val)
        out["rehaut_fit_dev_to_val"] = rehaut_fit([r for r in orig(dev_all) if M.analysed(r)], [r for r in orig(val_all) if M.analysed(r)])
    out["rectification_dev"] = rectification(use(dev_all), dev_all)
    if "validation" in parts:
        out["rectification_val"] = rectification(use(val_all), val_all)
    # Candidate gates: threshold chosen on dev, evaluated unchanged on val.
    cands = {}
    for key in ("rh_asym", "ell_tilt", "mpose_tilt", "mpose_tilt_high", "keystone", "affine_resid", "stab_gap_spread",
                "stab_rot_spread", "neg_frame_score", "unstable_frame"):
        t = choose_threshold(use(dev_all), err_dev, key)
        cands[key] = {"threshold_from_dev": t,
                      "dev": gate_eval(use(dev_all), err_dev, key, t) if t is not None else None,
                      "val": gate_eval(use(val_all), err_val, key, t) if (t is not None and "validation" in parts) else None,
                      "dev_perturbation": gate_eval(use(dev_all), perr_dev, key, t) if t is not None else None,
                      "val_perturbation": gate_eval(use(val_all), perr_val, key, t) if (t is not None and "validation" in parts) else None}
    out["candidate_gates"] = cands
    out["marker_failures_dev"] = marker_failures(orig(dev_all))
    out["error_summary"] = {"dev_photos_with_loo_error": len(err_dev), "val_photos_with_loo_error": len(err_val),
                            "dev_photos_with_perturbation": len(perr_dev), "val_photos_with_perturbation": len(perr_val)}
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("jsonl")
    ap.add_argument("--out", required=True)
    ap.add_argument("--parts", default="development,validation")
    a = ap.parse_args()
    parts = set(a.parts.split(","))
    if "holdout" in parts:
        raise SystemExit("experiments never read the holdout; use final_holdout.py with the frozen candidate")
    recs = [r for r in M.load(a.jsonl) if r["partition"] in parts | {"reference"}]
    res = run(recs, parts)
    Path(a.out).mkdir(parents=True, exist_ok=True)
    s = json.dumps(res, indent=1, default=lambda x: float(x) if isinstance(x, np.floating) else (x.tolist() if isinstance(x, np.ndarray) else str(x)))
    (Path(a.out) / "experiments.json").write_text(s)
    print(s)


if __name__ == "__main__":
    main()
