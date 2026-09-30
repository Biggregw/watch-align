"""Landmark and pose stability under tiny translations, and the translation-stability gate.

python3 tools/research/corpus_qc/stability.py <translations.jsonl> --parts development [--freeze gate.json | --frozen gate.json] [--json out]

Units: landmark centres are mapped to ORIGINAL-image pixels, the known translation is subtracted,
and distances are divided by the dial radius (from the t0 control), so spreads are in dial radii.
Everything is compared with t0 (the same photo, re-encoded identically, not translated).

Gate evaluation needs an outcome that the translations themselves do not define. It uses the
other photos of the same physical watch: a flag (CHECK/STRONG) on a landmark is "confirmed" when a
DIFFERENT photo of the same watch (its t0) flags the same landmark, "contradicted" when another
photo judges that landmark CLEAR and none flags it, and "unconfirmable" when the watch has no other
photo judging it. Genuine flags are counted separately as the false-positive proxy (genuine watches
should be nominal). Class labels never enter threshold selection.
"""
from __future__ import annotations

import argparse
import json
import math
from collections import Counter, defaultdict
from itertools import combinations

import numpy as np

import metrics as M

SHIFTS = ("x+1", "x-1", "x+2", "x-2", "y+1", "y-1", "y+2", "y-2")
FLAG = {"CHECK", "STRONG"}
JUDGED = {"CLEAR", "CHECK", "STRONG"}
POSE_ORDER = {"GOOD": 0, "CORRECTABLE": 1, "RETAKE": 2}


def to_orig(r, x, y):
    s = r.get("to_orig_scale") or 1.0
    return x * s + (r.get("to_orig_ox") or 0.0) - r["dx"], y * s + (r.get("to_orig_oy") or 0.0) - r["dy"]


def landmarks(r) -> dict:
    """landmark id -> {detected, cx, cy (orig px, translation removed), rot, size (orig px), verdict, conf}."""
    out = {}
    s = r.get("to_orig_scale") or 1.0
    def centre(keys):
        pts = [(r.get(k + "_x"), r.get(k + "_y")) for k in keys]
        pts = [p for p in pts if M.finite(p[0]) and M.finite(p[1])]
        if not pts:
            return None
        x, y = np.mean([p[0] for p in pts]), np.mean([p[1] for p in pts])
        return to_orig(r, x, y)
    ok = M.analysed(r)
    c = centre(("tri_l", "tri_r", "tri_tip")) if ok and r.get("twelve_valid") else None
    out["12"] = {"detected": c is not None, "c": c, "rot": r.get("rot_deg"), "size": (r.get("tri_px") or float("nan")) * s,
                 "verdicts": {"gap": r.get("gap_att"), "align": r.get("align_att")}, "conf": bool(r.get("stable_frame"))}
    for b in ("six", "nine"):
        c = centre((f"{b}_ol", f"{b}_or", f"{b}_il", f"{b}_ir")) if ok and r.get(f"{b}_valid") else None
        out[b] = {"detected": c is not None, "c": c, "rot": r.get(f"{b}_rot"), "size": (r.get(f"{b}_w") or float("nan")) * s,
                  "verdicts": {b: r.get(f"{b}_att")}, "conf": bool(r.get(f"{b}_stable"))}
    for m in r.get("round", []) if ok else []:
        h = int(m["hour"])
        found = bool(m.get("found")) and M.finite(m.get("x"))
        out[f"r{h}"] = {"detected": found, "c": to_orig(r, m["x"], m["y"]) if found else None, "rot": float("nan"),
                        "size": (m.get("r") or float("nan")) * s, "verdicts": {f"r{h}": m.get("att")}, "conf": bool(m.get("stable"))}
    return out


def ltype(lid: str) -> str:
    return "round" if lid.startswith("r") else {"12": "12 triangle", "six": "6 baton", "nine": "3/9 baton"}[lid]


def group(recs, parts):
    by = defaultdict(dict)
    for r in recs:
        if r["partition"] in parts:
            by[r["sha256"]][r["variant"]] = r
    return {k: v for k, v in by.items() if "t0" in v and M.analysed(v["t0"])}


def dial_r(r):
    return math.sqrt(r["dial_a"] * r["dial_b"]) * (r.get("to_orig_scale") or 1.0)


# ----------------------------------------------------------------------------- landmark stability
def landmark_stability(photos) -> dict:
    per = defaultdict(lambda: defaultdict(list))
    flips = defaultdict(Counter)
    for sha, vs in photos.items():
        t0 = vs["t0"]
        R = dial_r(t0)
        L0 = landmarks(t0)
        Ls = {k: landmarks(vs[k]) for k in SHIFTS if k in vs}
        for lid, l0 in L0.items():
            T = ltype(lid)
            seq = [l0] + [L[lid] for L in Ls.values() if lid in L]
            det = [l["detected"] for l in seq]
            flips[T]["instances"] += 1
            if l0["detected"]:
                flips[T]["detected_t0"] += 1
                flips[T]["lost_in_some_translation"] += int(not all(det))
            elif any(det):
                flips[T]["appears_in_some_translation"] += 1
            cs = [l["c"] for l in seq if l["detected"]]
            if len(cs) >= 3:
                med = np.median(np.array(cs), axis=0)
                per[T]["centre_spread"].append(max(math.dist(c, med) for c in cs) / R)
                rots = [l["rot"] for l in seq if l["detected"] and M.finite(l["rot"])]
                if len(rots) >= 3:
                    per[T]["rot_spread_deg"].append(max(rots) - min(rots))
                sz = [l["size"] for l in seq if l["detected"] and M.finite(l["size"])]
                if len(sz) >= 3 and np.median(sz) > 0:
                    per[T]["size_spread_rel"].append((max(sz) - min(sz)) / float(np.median(sz)))
            for vname in l0["verdicts"]:
                vals = [l["verdicts"].get(vname) for l in seq]
                v0 = vals[0]
                if v0 in JUDGED:
                    flips[T]["judged_t0"] += 1
                    flips[T]["judged_verdict_changes"] += int(any(v != v0 for v in vals[1:]))
                if v0 in FLAG:
                    flips[T]["flag_t0"] += 1
                    flips[T]["flag_not_in_all_translations"] += int(any(v not in FLAG for v in vals[1:]))
    out = {}
    for T in sorted(set(per) | set(flips)):
        d = {k: {"median": M.q(v, 50), "p95": M.q(v, 95), "n": len(v)} for k, v in per[T].items()}
        f = flips[T]
        d["counts"] = dict(f)
        d["verdict_flip_rate"] = f["judged_verdict_changes"] / f["judged_t0"] if f["judged_t0"] else float("nan")
        d["flag_instability_rate"] = f["flag_not_in_all_translations"] / f["flag_t0"] if f["flag_t0"] else float("nan")
        out[T] = d
    return out


# ----------------------------------------------------------------------------- pose stability
def pose_stability(photos) -> dict:
    trans = Counter()
    per_photo_changes = []
    params = defaultdict(list)
    rehaut_spread = defaultdict(list)
    for sha, vs in photos.items():
        t0 = vs["t0"]
        labels = [vs[k].get("pose") for k in SHIFTS if k in vs]
        for l in labels:
            trans[(t0.get("pose"), l)] += 1
        per_photo_changes.append(sum(1 for l in labels if l != t0.get("pose")))
        for key in ("mpose_tilt", "ell_tilt", "frame_pitch", "track_roll_deg", "rh_v", "rh_h", "rh_top", "rh_bottom", "rh_left", "rh_right"):
            vals = [vs[k].get(key) for k in ("t0",) + SHIFTS if k in vs and M.finite(vs[k].get(key))]
            if len(vals) >= 3:
                sp = max(vals) - min(vals)
                (rehaut_spread if key.startswith("rh_") else params)[key].append(sp)
    n = len(per_photo_changes)
    return {"photos": n, "transitions_t0_to_translation": {f"{a}->{b}": c for (a, b), c in sorted(trans.items(), key=lambda x: -x[1])},
            "photos_with_any_label_change": sum(1 for c in per_photo_changes if c) / n if n else float("nan"),
            "label_change_rate_per_translation": sum(per_photo_changes) / (8 * n) if n else float("nan"),
            "parameter_spread": {k: {"median": M.q(v, 50), "p95": M.q(v, 95), "n": len(v)} for k, v in params.items()},
            "rehaut_spread": {k: {"median": M.q(v, 50), "p95": M.q(v, 95), "n": len(v)} for k, v in rehaut_spread.items()}}


# GmtHumanPosePolicy boundaries on the local rehaut sectors and the dial ellipse.
POSE_BOUNDARIES = {"rh_maxasym": (0.14,), "rh_min_mean": (0.45, 0.75), "ell_tilt": (8.0, 15.0)}


def _pose_q(r):
    out = {}
    if M.finite(r.get("rh_v")) and M.finite(r.get("rh_h")):
        out["rh_maxasym"] = max(abs(r["rh_v"]), abs(r["rh_h"]))
    for k in ("rh_min_mean", "ell_tilt"):
        if M.finite(r.get(k)):
            out[k] = r[k]
    return out


def pose_threshold_proximity(photos) -> dict:
    """For each pose quantity: how far t0 sits from the nearest policy boundary, versus how much
    the quantity moves under the translations. A label change is 'explained' when some quantity's
    translation range straddles one of its boundaries."""
    near, changed, explained = Counter(), 0, 0
    margins = defaultdict(list)
    for sha, vs in photos.items():
        q0 = _pose_q(vs["t0"])
        qs = [_pose_q(vs[k]) for k in ("t0",) + SHIFTS if k in vs]
        straddle = False
        for k, bounds in POSE_BOUNDARIES.items():
            vals = [q[k] for q in qs if k in q]
            if k not in q0 or len(vals) < 3:
                continue
            spread = max(vals) - min(vals)
            m = min(abs(q0[k] - b) for b in bounds)
            margins[k].append(m / spread if spread > 0 else float("inf"))
            if any(min(vals) < b <= max(vals) for b in bounds):
                straddle = True
                near[k] += 1
        labels = {vs[k].get("pose") for k in ("t0",) + SHIFTS if k in vs}
        if len(labels) > 1:
            changed += 1
            explained += int(straddle)
    return {"photos_with_label_change": changed, "of_which_a_quantity_straddles_a_boundary": explained,
            "photos_straddling_by_quantity": dict(near),
            "margin_over_translation_spread": {k: {"median": M.q(v, 50), "share_below_1": float(np.mean([x < 1 for x in v])) if v else float("nan"), "n": len(v)}
                                              for k, v in margins.items()}}


def consensus(labels):
    """Median of the ordered labels GOOD < CORRECTABLE < RETAKE; UNASSESSABLE ignored unless all are."""
    v = sorted(POSE_ORDER[l] for l in labels if l in POSE_ORDER)
    if not v:
        return "UNASSESSABLE"
    inv = {b: a for a, b in POSE_ORDER.items()}
    return inv[v[(len(v) - 1) // 2]] if len(v) % 2 else inv[v[len(v) // 2]]   # upper median on ties: conservative


def pose_consensus_eval(photos) -> dict:
    """Does a 3-view consensus agree with itself better than a single label does? Two disjoint sets
    of views of the same photo are compared (A = t0,x+1,y+1; B = x-1,y-1,x+2); a single label is
    compared the same way (t0 vs x-1). RETAKE retention: photos whose majority over all nine views is
    RETAKE must stay RETAKE under the rule."""
    single_agree = cons_agree = n = 0
    retake_total = retake_kept_single = retake_kept_cons = 0
    for sha, vs in photos.items():
        if not all(k in vs for k in ("t0", "x+1", "y+1", "x-1", "y-1", "x+2")):
            continue
        n += 1
        a = consensus([vs[k].get("pose") for k in ("t0", "x+1", "y+1")])
        b = consensus([vs[k].get("pose") for k in ("x-1", "y-1", "x+2")])
        cons_agree += int(a == b)
        single_agree += int(vs["t0"].get("pose") == vs["x-1"].get("pose"))
        allv = [vs[k].get("pose") for k in ("t0",) + SHIFTS if k in vs]
        if Counter(allv).most_common(1)[0][0] == "RETAKE":
            retake_total += 1
            retake_kept_single += int(vs["t0"].get("pose") == "RETAKE")
            retake_kept_cons += int(a == "RETAKE")
    return {"photos": n, "single_label_agreement": single_agree / n if n else float("nan"),
            "consensus3_agreement": cons_agree / n if n else float("nan"),
            "majority_retake_photos": retake_total, "retake_kept_single": retake_kept_single, "retake_kept_consensus": retake_kept_cons}


# ----------------------------------------------------------------------------- gate
def flag_table(photos) -> list[dict]:
    """One row per (photo, landmark verdict) flagged on t0, with its translation stability and its
    cross-photo status within the same physical watch."""
    t0v = {}
    for sha, vs in photos.items():
        L = landmarks(vs["t0"])
        for lid, l in L.items():
            for vn, v in l["verdicts"].items():
                t0v[(sha, vn)] = v
    watch = {sha: vs["t0"]["watch"] for sha, vs in photos.items()}
    cls = {sha: vs["t0"]["cls"] for sha, vs in photos.items()}
    rows = []
    for sha, vs in photos.items():
        L0 = landmarks(vs["t0"])
        Ls = {k: landmarks(vs[k]) for k in SHIFTS if k in vs}
        for lid, l0 in L0.items():
            for vn, v0 in l0["verdicts"].items():
                if v0 not in FLAG:
                    continue
                stab = {k: (L[lid]["verdicts"].get(vn) in FLAG) if lid in L else False for k, L in Ls.items()}
                others = [t0v[(o, vn)] for o in photos if o != sha and watch[o] == watch[sha] and (o, vn) in t0v]
                status = "confirmed" if any(o in FLAG for o in others) else "contradicted" if any(o == "CLEAR" for o in others) else "unconfirmable"
                rows.append({"sha": sha, "watch": watch[sha], "cls": cls[sha], "verdict": vn, "type": ltype(lid),
                             "stability": sum(stab.values()) / max(1, len(stab)), "stable_in": stab, "status": status, "t0": v0})
    return rows


def gate_keep(row, rule) -> bool:
    if rule["kind"] == "fraction":
        return row["stability"] >= rule["min_fraction"]
    return all(row["stable_in"].get(k, False) for k in rule["shifts"])


def gate_eval(photos, rule) -> dict:
    rows = flag_table(photos)
    judged = 0
    for sha, vs in photos.items():
        for l in landmarks(vs["t0"]).values():
            judged += sum(1 for v in l["verdicts"].values() if v in JUDGED)
    kept = [r for r in rows if gate_keep(r, rule)]
    def c(rs, **kw):
        return sum(1 for r in rs if all(r[k] == v for k, v in kw.items()))
    return {"rule": rule, "judged_verdicts_before": judged, "flags_before": len(rows), "flags_after": len(kept),
            "withheld_as_not_confidently_measurable": len(rows) - len(kept),
            "judged_verdicts_after": judged - (len(rows) - len(kept)),
            "genuine_flags_before": c(rows, cls="gen"), "genuine_flags_after": c(kept, cls="gen"),
            "confirmed_before": c(rows, status="confirmed"), "confirmed_after": c(kept, status="confirmed"),
            "contradicted_before": c(rows, status="contradicted"), "contradicted_after": c(kept, status="contradicted"),
            "unconfirmable_before": c(rows, status="unconfirmable"), "unconfirmable_after": c(kept, status="unconfirmable"),
            "by_type_before": dict(Counter(r["type"] for r in rows)), "by_type_after": dict(Counter(r["type"] for r in kept))}


def candidate_rules():
    rules = [{"kind": "fraction", "min_fraction": f} for f in (0.25, 0.5, 0.625, 0.75, 0.875, 1.0)]
    for pair in (("x+2", "y+2"), ("x-2", "y-2"), ("x+1", "y+1"), ("x+2", "x-2"), ("y+2", "y-2")):
        rules.append({"kind": "all_of", "shifts": list(pair)})
    return rules


def choose_rule(photos) -> dict:
    """On development only: among rules that keep EVERY confirmed flag, pick the one that withholds
    the most contradicted flags, then the most unconfirmable ones, then the cheapest (fewest extra
    analyses). If no flag is confirmed, the rule must still keep flags stable in all translations."""
    best, key_best = None, None
    for rule in candidate_rules():
        e = gate_eval(photos, rule)
        if e["confirmed_after"] < e["confirmed_before"]:
            continue
        cost = 8 if rule["kind"] == "fraction" else len(rule["shifts"])
        key = (e["contradicted_before"] - e["contradicted_after"], e["unconfirmable_before"] - e["unconfirmable_after"], -cost)
        if key_best is None or key > key_best:
            best, key_best = rule, key
    return best


# ----------------------------------------------------------------------------- same-watch decomposition
def decomposition(photos) -> dict:
    """For QC metrics: translation (detector) SD within a photo, photo-to-photo SD within a watch
    (camera position + detector), and between-watch SD of watch means."""
    keys = ("gap", "rot_deg", "sp59", "sp01", "six_centring", "six_rot", "nine_centring", "nine_rot")
    out = {}
    for k in keys:
        within_photo, photo_means, mads = [], defaultdict(list), []
        for sha, vs in photos.items():
            vals = [vs[v].get(k) for v in ("t0",) + SHIFTS if v in vs and M.finite(vs[v].get(k))]
            if len(vals) >= 3:
                within_photo.append(float(np.var(vals, ddof=1)))
                mads.append(float(np.median(np.abs(np.array(vals) - np.median(vals)))))
                photo_means[vs["t0"]["watch"]].append(float(np.median(vals)))
        wp = math.sqrt(np.mean(within_photo)) if within_photo else float("nan")
        ww, nw, _ = M.pooled_within_sd(photo_means)
        wm = [float(np.mean(v)) for v in photo_means.values()]
        # robust counterparts: a few photos where the detector jumps between edges dominate SDs
        photo_meds = {w: v for w, v in photo_means.items()}
        mad_ww = [float(np.median(np.abs(np.array(v) - np.median(v)))) for v in photo_meds.values() if len(v) >= 2]
        wmed = [float(np.median(v)) for v in photo_meds.values()]
        out[k] = {"translation_mad_within_photo_median": M.q(mads, 50), "translation_mad_within_photo_p95": M.q(mads, 95),
                  "photo_to_photo_mad_within_watch_median": M.q(mad_ww, 50),
                  "between_watch_mad": float(np.median(np.abs(np.array(wmed) - np.median(wmed)))) if wmed else float("nan"),
                  "translation_sd_within_photo": wp, "photo_to_photo_sd_within_watch": ww, "watches_with_2plus_photos": nw,
                  "between_watch_sd": float(np.std(wm, ddof=1)) if len(wm) > 1 else float("nan"), "watches": len(wm)}
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("jsonl")
    ap.add_argument("--parts", default="development")
    ap.add_argument("--freeze")
    ap.add_argument("--frozen")
    ap.add_argument("--json")
    a = ap.parse_args()
    parts = set(a.parts.split(","))
    photos = group(M.load(a.jsonl), parts)
    res = {"parts": sorted(parts), "photos": len(photos), "watches": len({v["t0"]["watch"] for v in photos.values()}),
           "landmarks": landmark_stability(photos), "pose": pose_stability(photos), "pose_consensus": pose_consensus_eval(photos),
           "pose_threshold_proximity": pose_threshold_proximity(photos),
           "decomposition": decomposition(photos)}
    res["gate_all_candidate_rules"] = [gate_eval(photos, r) for r in candidate_rules()]
    if a.freeze:
        rule = choose_rule(photos)
        json.dump({"rule": rule, "frozen_from": sorted(parts)}, open(a.freeze, "w"), indent=1)
        res["frozen_rule"] = rule
    if a.frozen:
        rule = json.load(open(a.frozen))["rule"]
        res["frozen_rule"] = rule
        res["frozen_rule_result"] = gate_eval(photos, rule) if rule else None
    res["flag_rows"] = [{k: v for k, v in r.items() if k != "sha"} for r in flag_table(photos)]
    s = json.dumps(res, indent=1, default=float)
    if a.json:
        open(a.json, "w").write(s)
    print(s)


if __name__ == "__main__":
    main()
