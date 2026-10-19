#!/usr/bin/env python3
"""Summarise the exact final solves; geometry diagnostics are not ground truth."""
import argparse
from collections import Counter
import json
from pathlib import Path
import numpy as np
from fetch_cases import CASES
from render import check_coverage


def describe(d, hour):
    m = next(m for m in d["markers"] if m["hour"] == hour)
    s = m["iterations"][-1]
    idx = np.array(s["indices"])
    w = np.array(s["solve_weights"])
    p = np.array(s["points"])[idx]
    n = np.array(s["normals"])[idx]
    off = np.array(s["offset"], dtype=float)[idx]
    observed = p + off[:, None] * n
    J = np.array(s["jacobian"])
    A = J * np.sqrt(w[:, None])
    seconds = m["alpha99"]["seconds_psi"]
    in_shaft = np.zeros(len(idx), dtype=bool)
    if seconds is not None:
        a = np.radians(seconds)
        cross = observed[:, 0] * np.cos(a) + observed[:, 1] * np.sin(a)
        along = observed[:, 0] * np.sin(a) - observed[:, 1] * np.cos(a)
        in_shaft = (along > 0) & (np.abs(cross) <= .006 + .75 / d["rpx"])
    sides = np.array(s["side"])[idx]
    # Controlled local sensitivity: retain the actual geometry and solve weights,
    # but give every observed normal a uniform +2 px offset. This exposes how
    # a fixed-size outline and asymmetric visibility can turn edge offset into
    # apparent translation/rotation. It is NOT an estimate of the true defect.
    shock = np.linalg.lstsq(A, np.sqrt(w) * 2 / d["rpx"], rcond=None)[0]
    result = {k: m[k] for k in ("state", "side_pct", "inout_pct", "tilt_deg", "uncertain", "gate", "alpha99", "fit")}
    result.update(rpx=d["rpx"], metal_hands=d["metal_hands"],
                  search_profiles=len(s["points"]), exclusion_counts=dict(Counter(s["why"])),
                  available=len(idx), positive_solve_weights=int(sum(w > 0)),
                  zero_solve_weights=int(sum(w == 0)),
                  positive_in_seconds_corridor=int(sum(in_shaft & (w > 0))),
                  weighted_rank=int(np.linalg.matrix_rank(A)),
                  parameters=J.shape[1],
                  positive_by_side={str(side): int(sum((sides == side) & (w > 0))) for side in sorted(set(sides))},
                  uniform_2px_offset_sensitivity={"dx_pct": 100 * shock[0], "dy_pct": 100 * shock[1],
                                                  "rotation_deg": float(np.degrees(shock[2])) if len(shock) == 3 else None})
    return result


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("before", type=Path); p.add_argument("after", type=Path); p.add_argument("output", type=Path)
    a = p.parse_args()
    check_coverage(a.before); check_coverage(a.after)
    out = []
    for photo, hour in CASES.items():
        old = json.loads((a.before / (photo + ".json")).read_text())
        new = json.loads((a.after / (photo + ".json")).read_text())
        if old["sha256"] != new["sha256"] or old["homography"] != new["homography"]:
            raise ValueError("Different photograph/pose: " + photo)
        out.append({"photo_id": photo, "hour": hour, "sha256": old["sha256"], "pose_identical": True,
                    "before": describe(old, hour), "after": describe(new, hour)})
    a.output.write_text(json.dumps(out, indent=2) + "\n")


if __name__ == "__main__":
    main()
