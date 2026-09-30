"""Baseline of the current production analysis on the harvested corpus.

python3 tools/research/corpus_qc/baseline.py <corpus_qc.jsonl> --parts development[,validation] [--json out.json]

Reports, for the chosen partitions only (the locked holdout is never read here):
success rates, pose labels and reasons, landmark detection, marker-layout/rectification
confidence, within-watch repeatability, perturbation sensitivity, genuine-vs-replica
distributions per metric (one value per watch) and their separation relative to noise, and
failure causes."""
from __future__ import annotations

import argparse
import json
import math
import re
from collections import Counter, defaultdict

import numpy as np

import metrics as M


def originals(recs, parts):
    return [r for r in recs if r["variant"] == "original" and r["partition"] in parts and r["sample_role"] == "population"]


def pose_reason(r):
    t = r.get("pose_reason") or ""
    t = re.sub(r"\s*Gap direction cue.*$", "", t)
    return re.sub(r"[\d.]+", "#", t.split(" - ", 1)[-1])[:110]


def success(recs):
    n = len(recs)
    ok = [r for r in recs if M.analysed(r)]
    use = [r for r in recs if r["usable"]]
    rnd_found = [sum(1 for m in r.get("round", []) if m.get("found")) for r in ok]
    rnd_total = [len(r.get("round", [])) for r in ok]
    per_hour = defaultdict(lambda: [0, 0])
    for r in ok:
        for m in r.get("round", []):
            per_hour[int(m["hour"])][1] += 1
            per_hour[int(m["hour"])][0] += 1 if m.get("found") else 0
    return {
        "images": n, "watches": len({r["watch"] for r in recs}),
        "dial_analysed": len(ok), "harvest_usable": len(use),
        "usable_rate": len(use) / n if n else float("nan"),
        "pose": dict(Counter(r.get("pose", "?") for r in ok)),
        "pose_retake_reasons": dict(Counter(pose_reason(r) for r in ok if r.get("pose") == "RETAKE").most_common(6)),
        "pose_unassessable_reasons": dict(Counter(pose_reason(r) for r in ok if r.get("pose") == "UNASSESSABLE").most_common(6)),
        "twelve_found_rate": np.mean([bool(r.get("twelve_valid")) for r in ok]) if ok else float("nan"),
        "minute_track_stable_rate": np.mean([bool(r.get("stable_frame")) for r in ok]) if ok else float("nan"),
        "round_found_rate": (sum(rnd_found) / sum(rnd_total)) if rnd_total and sum(rnd_total) else float("nan"),
        "round_found_by_hour": {h: round(v[0] / v[1], 3) for h, v in sorted(per_hour.items())},
        "images_with_6plus_round": np.mean([f >= 6 for f in rnd_found]) if ok else float("nan"),
        "six_baton_rate": np.mean([bool(r.get("six_valid")) for r in ok]) if ok else float("nan"),
        "nine_baton_rate": np.mean([bool(r.get("nine_valid")) for r in ok]) if ok else float("nan"),
        "marker_pose_valid_rate": np.mean([bool(r.get("mpose_valid")) for r in ok]) if ok else float("nan"),
        "marker_pose_resid_median": M.q([r.get("mpose_resid") for r in ok], 50),
        "local_frame_valid_rate": np.mean([bool(r.get("local_frame_valid")) for r in ok]) if ok else float("nan"),
        "ms_median": M.q([r.get("ms") for r in recs], 50),
    }


def failures(recs):
    c = Counter()
    for r in recs:
        if r.get("error"):
            c["analysis error"] += 1
        elif r.get("no_readable_dial"):
            c["no readable dial (outlines found, few clean markers, no stable frame)"] += 1
        elif not r.get("dial_located"):
            c["no dial located"] += 1
        elif not r.get("twelve_valid"):
            c["12 marker not found" + (" (hand at 12)" if r.get("hand_at_twelve") else " (too small)" if r.get("too_small") else "")] += 1
        elif r.get("pose") == "RETAKE":
            c["pose RETAKE"] += 1
        elif not r["usable"]:
            c["harvester: " + ",".join(r["harvest_reasons"]) if r["harvest_reasons"] else "harvester: not usable"] += 1
    return dict(c.most_common())


def repeatability(recs):
    """Within-watch spread across different photos of one watch (usable originals)."""
    use = [r for r in recs if r["usable"] and M.analysed(r)]
    out = {}
    for k in M.QC_METRICS:
        sd, nw, df = M.pooled_within_sd(M.by_watch(use, k))
        out[k] = {"within_sd": sd, "watches": nw, "df": df}
    return out


def perturbation(all_recs, parts):
    base = {r["sha256"]: r for r in all_recs if r["variant"] == "original" and r["partition"] in parts}
    d = defaultdict(list)
    flips = Counter()
    n = Counter()
    for r in all_recs:
        if r["variant"] == "original" or r["partition"] not in parts:
            continue
        b = base.get(r["sha256"])
        if not b or not M.analysed(b):
            continue
        n[r["variant"]] += 1
        for k in M.QC_METRICS:
            if M.finite(b.get(k)) and M.finite(r.get(k)):
                d[k].append(abs(r[k] - b[k]))
        if bool(b.get("twelve_valid")) != bool(r.get("twelve_valid")):
            flips["twelve_found"] += 1
        if b.get("pose") != r.get("pose"):
            flips["pose_label"] += 1
        for f in M.FLAG_FIELDS:
            if (b.get(f) in M.FLAGGED) != (r.get(f) in M.FLAGGED):
                flips[f] += 1
    total = sum(n.values())
    return {"variants": dict(n),
            "abs_change": {k: {"median": M.q(v, 50), "p90": M.q(v, 90), "n": len(v)} for k, v in d.items()},
            "flip_rate": {k: v / total for k, v in flips.items()} if total else {}}


def distributions(recs, rep, pert):
    """Per metric: one value per watch (median of its usable views), genuine vs replica, and the
    separation expressed against measurement noise (within-watch SD and perturbation p90)."""
    use = [r for r in recs if r["usable"] and M.analysed(r)]
    cls = {r["watch"]: r["cls"] for r in use}
    out = {}
    for k in M.QC_METRICS:
        per = {w: float(np.median(v)) for w, v in M.by_watch(use, k).items()}
        g = [v for w, v in per.items() if cls[w] == "gen"]
        p = [v for w, v in per.items() if cls[w] == "rep"]
        noise = max(x for x in (rep[k]["within_sd"], pert["abs_change"].get(k, {}).get("p90", float("nan"))) if M.finite(x)) \
            if any(M.finite(x) for x in (rep[k]["within_sd"], pert["abs_change"].get(k, {}).get("p90", float("nan")))) else float("nan")
        gm, pm = (float(np.median(g)) if g else float("nan")), (float(np.median(p)) if p else float("nan"))
        out[k] = {"gen_watches": len(g), "gen_median": gm, "gen_iqr": [M.q(g, 25), M.q(g, 75)],
                  "gen_between_sd": float(np.std(g, ddof=1)) if len(g) > 1 else float("nan"),
                  "rep_watches": len(p), "rep_median": pm, "rep_iqr": [M.q(p, 25), M.q(p, 75)],
                  "auc_rep_gt_gen": M.auc(g, p), "noise": noise,
                  "median_gap_over_noise": abs(pm - gm) / noise if M.finite(noise) and noise > 0 else float("nan")}
    return out


def flags(recs):
    """QC flag rates per watch class (CHECK/STRONG) on usable originals: on genuine watches a flag is
    a false-positive proxy (genuine should be nominal); replicas are stress cases, not targets."""
    use = [r for r in recs if r["usable"] and M.analysed(r)]
    out = {}
    for f in M.FLAG_FIELDS + ("round_any",):
        for c in ("gen", "rep"):
            rs = [r for r in use if r["cls"] == c]
            if f == "round_any":
                hit = [any(m.get("att") in M.FLAGGED for m in r.get("round", [])) for r in rs]
            else:
                hit = [r.get(f) in M.FLAGGED for r in rs]
            out[f"{f}_{c}"] = {"image_rate": float(np.mean(hit)) if hit else float("nan"), "images": len(hit),
                               "watches_with_any": len({r["watch"] for r, h in zip(rs, hit) if h}), "watches": len({r["watch"] for r in rs})}
    return out


def report(all_recs, parts):
    recs = originals(all_recs, parts)
    rep = repeatability(recs)
    pert = perturbation(all_recs, parts)
    return {"parts": sorted(parts), "success": success(recs), "failures": failures(recs), "repeatability": rep,
            "perturbation": pert, "distributions": distributions(recs, rep, pert), "flags": flags(recs)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("jsonl")
    ap.add_argument("--parts", default="development")
    ap.add_argument("--json")
    a = ap.parse_args()
    parts = set(a.parts.split(","))
    assert "holdout" not in parts or parts == {"holdout"}, "the holdout is evaluated alone, once"
    rep = report(M.load(a.jsonl), parts)
    s = json.dumps(rep, indent=1, default=lambda x: float(x) if isinstance(x, (np.floating,)) else str(x))
    if a.json:
        open(a.json, "w").write(s)
    print(s)


if __name__ == "__main__":
    main()
