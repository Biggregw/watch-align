"""Photo-suitability study over ALL harvested images whose dial was located (accepted, quarantined
and rejected), from the harvester's stored app analysis (Suit driver) and pixel checks.

python3 tools/research/corpus_qc/suitability.py <data/harvest> <split.csv> [--json out]

Question: which cheap, early signals separate photos that end measurement-quality from photos that
do not, so the app can refuse bad input with a clear reason instead of producing weak numbers?

Images of locked-holdout watches are excluded entirely. Accepted development/validation watches
keep their partition; watches outside the split (quarantined/rejected) are assigned to
development or validation by a fixed hash (60/40), so rules chosen on development are checked on
images they were not chosen on.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
from collections import Counter
from pathlib import Path

import numpy as np

import metrics as M


def part_of(watch: str, split: dict) -> str:
    if watch in split:
        return split[watch]
    h = int(hashlib.sha256(("suitability-v1" + watch).encode()).hexdigest()[:8], 16) % 10
    return "development" if h < 6 else "validation"


def rows(harvest: Path, split: dict):
    man = list(csv.DictReader((harvest / "manifest.csv").open(newline="", encoding="utf-8")))
    state = {}
    for l in (harvest / "state" / "images.jsonl").open(encoding="utf-8"):
        d = json.loads(l)
        state[d["sha256"]] = d
    seen, out = set(), []
    for r in man:
        if r["sha256"] in seen:
            continue
        seen.add(r["sha256"])
        p = part_of(r["physical_watch_id"], split)
        if p == "holdout":
            continue
        s = state.get(r["sha256"], {})
        app = (s.get("suitability") or {}).get("app") or {}
        q = s.get("quality") or {}
        if not app.get("dial_located") and not app.get("dial_found"):
            continue
        out.append({"sha": r["sha256"], "watch": r["physical_watch_id"], "part": p, "decision": r["dataset_decision"],
                    "good": r["suitable"] == "yes" and r["measurement_status"] == "measured",
                    "reasons": [x for x in r["suitability_reasons"].split(";") if x],
                    "dial_px": q.get("dial_diameter_px"), "sharp": q.get("sharpness"), "twelve": bool(app.get("twelve_found")),
                    "pose": app.get("pose"), "round_found": app.get("round_found"), "ell_tilt": app.get("ellipse_tilt_deg"),
                    "mpose_tilt": app.get("marker_tilt_deg"), "tri_px": app.get("triangle_px"),
                    "rh_cov": min([app.get(f"rehaut_{k}_cov") for k in ("top", "bottom", "left", "right")], default=None)
                    if all(M.finite(app.get(f"rehaut_{k}_cov")) for k in ("top", "bottom", "left", "right")) else None,
                    "rh_asym": _asym(app), "glare": q.get("glare_fraction"), "blown": q.get("blown_fraction")})
    return out


def _asym(app):
    ks = [app.get(f"rehaut_{k}_px") for k in ("top", "bottom", "left", "right")]
    if not all(M.finite(k) for k in ks):
        return None
    t, b, l, r = ks
    m = (t + b + l + r) / 4
    return float(np.hypot((t - b) / m, (l - r) / m))


def rule_eval(rs, key, thr, below=True):
    """Early reject when key < thr (below=True) or > thr."""
    rej = [r for r in rs if M.finite(r.get(key)) and ((r[key] < thr) if below else (r[key] > thr))]
    good = [r for r in rs if r["good"]]
    bad = [r for r in rs if not r["good"]]
    return {"rejected": len(rej), "bad_caught": sum(1 for r in rej if not r["good"]), "good_lost": sum(1 for r in rej if r["good"]),
            "bad_total": len(bad), "good_total": len(good),
            "recall_of_bad": sum(1 for r in rej if not r["good"]) / len(bad) if bad else float("nan"),
            "precision": sum(1 for r in rej if not r["good"]) / len(rej) if rej else float("nan")}


def choose(rs, key, below):
    """Most bad photos caught with ZERO good photos lost on development (confident rejection only)."""
    vals = sorted({r[key] for r in rs if M.finite(r.get(key))}, reverse=not below)
    best = None
    for t in vals:
        e = rule_eval(rs, key, t, below)
        if e["good_lost"] == 0 and (best is None or e["bad_caught"] > best[1]["bad_caught"]):
            best = (t, e)
    return best


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("harvest")
    ap.add_argument("split")
    ap.add_argument("--json")
    a = ap.parse_args()
    split = {r["physical_watch_id"]: r["partition"] for r in csv.DictReader(open(a.split))}
    rs = rows(Path(a.harvest), split)
    dev = [r for r in rs if r["part"] == "development"]
    val = [r for r in rs if r["part"] == "validation"]
    out = {"dial_located_images": {"development": len(dev), "validation": len(val)},
           "good_rate": {"development": float(np.mean([r["good"] for r in dev])), "validation": float(np.mean([r["good"] for r in val]))},
           "why_not_good_dev": dict(Counter(x for r in dev if not r["good"] for x in (r["reasons"] or ["(accepted image, not measured)"])).most_common()),
           "rules": {}}
    for key, below in (("dial_px", True), ("tri_px", True), ("sharp", True), ("round_found", True), ("rh_cov", True),
                       ("ell_tilt", False), ("mpose_tilt", False), ("rh_asym", False), ("glare", False), ("blown", False)):
        b = choose(dev, key, below)
        if b is None:
            out["rules"][key] = {"threshold": None}
            continue
        t, e = b
        out["rules"][key] = {"threshold": t, "direction": "reject below" if below else "reject above", "dev": e, "val": rule_eval(val, key, t, below)}
    s = json.dumps(out, indent=1, default=float)
    if a.json:
        Path(a.json).write_text(s)
    print(s)


if __name__ == "__main__":
    main()
