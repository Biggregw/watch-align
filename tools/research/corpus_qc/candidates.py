"""Verdict-level evaluation of the candidate changes, with thresholds frozen from development.

python3 tools/research/corpus_qc/candidates.py <corpus_qc.jsonl> --freeze frozen.json       (development only)
python3 tools/research/corpus_qc/candidates.py <corpus_qc.jsonl> --frozen frozen.json --parts validation
python3 tools/research/corpus_qc/candidates.py <holdout.jsonl>   --frozen frozen.json --parts holdout   (once)

A candidate is simulated on the app's own outputs: it can only withdraw a verdict (turn it into
"not judged"); it never invents one. For each partition it reports:
  verdicts        judged verdicts (gap, alignment, 6, 9) before/after
  loo_err         mean normalised disagreement of judged values with the watch's other views
  unstable        judged verdicts on originals that change under a trivial perturbation
                  (CLEAR<->CHECK, or judged<->not judged), before/after
  genuine_flags   CHECK/STRONG verdicts on genuine watches (false-positive proxy), before/after
"""
from __future__ import annotations

import argparse
import json
from collections import defaultdict

import numpy as np

import experiments as E
import metrics as M

FIELDS = {"gap_att": "gap", "align_att": "rot_deg", "six_att": "six_centring", "nine_att": "nine_centring"}
JUDGED = {"CLEAR", "CHECK", "STRONG"}


def freeze(recs) -> dict:
    """All thresholds from DEVELOPMENT: gates from experiments.choose_threshold (dev LOO error,
    >= 80% retention, no watch loses all views); rotation margin = p90 of |change| of JUDGED
    rotation values under the perturbations."""
    dev = [E.enrich(r) for r in recs if r["partition"] == "development"]
    use = [r for r in dev if r["variant"] == "original" and r["usable"] and M.analysed(r)]
    sc = E.scales(use)
    err = E.loo_error(use, sc)
    base = {r["sha256"]: r for r in use}
    d = []
    for r in dev:
        b = base.get(r["sha256"])
        if r["variant"] != "original" and b and b.get("align_att") in JUDGED and M.finite(r.get("rot_deg")):
            d.append(abs(r["rot_deg"] - b["rot_deg"]))
    return {
        "scales": sc,
        "C1_rehaut_asym_gate": {"key": "rh_asym", "above": E.choose_threshold(use, err, "rh_asym")},
        "C2_marker_tilt_gate": {"key": "mpose_tilt", "above": E.choose_threshold(use, err, "mpose_tilt")},
        "C3_resize_spread_gate": {"key": "stab_gap_spread", "above": E.choose_threshold(use, err, "stab_gap_spread")},
        "C6_rotation_flag_margin_deg": M.q(d, 90),
        "frozen_from": "development",
    }


def apply(r: dict, cand: str, fz: dict) -> dict:
    """Verdicts after the candidate (only ever withdraws)."""
    v = {f: r.get(f) for f in FIELDS}
    if cand == "baseline":
        return v
    if cand in ("C1_rehaut_asym_gate", "C2_marker_tilt_gate", "C3_resize_spread_gate"):
        g = fz[cand]
        x = r.get(g["key"])
        if g["above"] is not None and M.finite(x) and x > g["above"]:
            v = {f: ("UNASSESSABLE" if s in JUDGED else s) for f, s in v.items()}
        return v
    if cand == "C6_rotation_flag_margin":
        m = fz["C6_rotation_flag_margin_deg"]
        if v["align_att"] in ("CHECK", "STRONG") and M.finite(r.get("rot_deg")) and abs(r["rot_deg"]) < 1.0 + m:
            v["align_att"] = "UNASSESSABLE"
        return v
    raise KeyError(cand)


CANDS = ("baseline", "C1_rehaut_asym_gate", "C2_marker_tilt_gate", "C3_resize_spread_gate", "C6_rotation_flag_margin")


def evaluate(recs, part, fz) -> dict:
    rs = [E.enrich(r) for r in recs if r["partition"] == part]
    orig = [r for r in rs if r["variant"] == "original" and r["usable"] and M.analysed(r)]
    by_sha = {r["sha256"]: r for r in orig}
    variants = defaultdict(list)
    for r in rs:
        if r["variant"] != "original" and r["sha256"] in by_sha:
            variants[r["sha256"]].append(r)
    out = {}
    for c in CANDS:
        verd = {r["sha256"]: apply(r, c, fz) for r in orig}
        n_j = sum(1 for v in verd.values() for s in v.values() if s in JUDGED)
        # disagreement of judged values with the other views (any view) of the same watch
        errs = []
        by_w = defaultdict(list)
        for r in orig:
            by_w[r["watch"]].append(r)
        for r in orig:
            for f, k in FIELDS.items():
                if verd[r["sha256"]][f] not in JUDGED or not M.finite(r.get(k)):
                    continue
                others = [o[k] for o in by_w[r["watch"]] if o is not r and M.finite(o.get(k)) and verd[o["sha256"]][f] in JUDGED]
                if others and fz["scales"].get(k):
                    errs.append(abs(r[k] - float(np.median(others))) / fz["scales"][k])
        unstable = 0
        for sha, vs in variants.items():
            b = verd[sha]
            for vr in vs:
                a = apply(vr, c, fz)
                for f in FIELDS:
                    if b[f] in JUDGED and a[f] != b[f]:
                        unstable += 1
        gen_flags = sum(1 for r in orig if r["cls"] == "gen" for s in verd[r["sha256"]].values() if s in ("CHECK", "STRONG"))
        all_flags = sum(1 for v in verd.values() for s in v.values() if s in ("CHECK", "STRONG"))
        out[c] = {"judged_verdicts": n_j, "judged_pairs_compared": len(errs),
                  "loo_err_mean": float(np.mean(errs)) if errs else float("nan"), "loo_err_p90": M.q(errs, 90),
                  "unstable_judged_under_perturbation": unstable, "flags_total": all_flags, "genuine_flags": gen_flags,
                  "photos": len(orig), "watches": len(by_w)}
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("jsonl")
    ap.add_argument("--freeze")
    ap.add_argument("--frozen")
    ap.add_argument("--parts", default="development")
    a = ap.parse_args()
    recs = M.load(a.jsonl)
    if a.freeze:
        fz = freeze(recs)
        open(a.freeze, "w").write(json.dumps(fz, indent=1, default=float))
    else:
        fz = json.load(open(a.frozen))
    res = {p: evaluate(recs, p, fz) for p in a.parts.split(",")}
    print(json.dumps({"frozen": fz, "results": res}, indent=1, default=float))


if __name__ == "__main__":
    main()
