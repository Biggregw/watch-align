#!/usr/bin/env python3
"""Plot the recorded sample endpoints, not an illustrative replacement polygon."""
import argparse
import csv
import json
from pathlib import Path

import cv2
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np
from fetch_cases import CASES


def check_coverage(directory):
    coverage = json.loads((directory / "coverage.json").read_text())
    photos = coverage["photos"]
    ids = [p["photo_id"] for p in photos]
    if (len(ids) != len(set(ids)) or set(ids) != set(CASES)
            or coverage["traced"] != len(CASES)
            or any(p["status"] != "verified_traced" for p in photos)):
        raise ValueError("Incomplete ten-case audit: " + repr(coverage))
    for id, hour in CASES.items():
        data = json.loads((directory / (id + ".json")).read_text())
        if data["photo_id"] != id or data["model"] != "submariner_126610":
            raise ValueError("Wrong photo/model: " + id)
        if len([m for m in data["markers"] if m["hour"] == hour]) != 1:
            raise ValueError("Missing/duplicate target marker: " + id)


def render(directory, id, hour):
    d = json.loads((directory / (id + ".json")).read_text())
    m = next(m for m in d["markers"] if m["hour"] == hour)
    im = cv2.cvtColor(cv2.imread(str(directory / (id + "_analysis.png"))), cv2.COLOR_BGR2RGB)
    ref = np.asarray(m["reference"])
    centre = ref.mean(axis=0)
    half = max(.10, np.linalg.norm(ref - centre, axis=1).max() * 1.65)
    # Inverse-map the *actual analysis bitmap* using the recorded production H.
    size = 720
    scale = size / (2 * half)
    transform = np.array([[scale, 0, -scale*(centre[0]-half)], [0, scale, -scale*(centre[1]-half)], [0, 0, 1]])
    h = np.asarray(d["homography"]).reshape(3, 3)
    crop = cv2.warpPerspective(im, transform @ np.linalg.inv(h), (size, size))
    extent = [centre[0]-half, centre[0]+half, centre[1]+half, centre[1]-half]
    fig, axes = plt.subplots(1, 3, figsize=(17, 6), gridspec_kw={"width_ratios": [1, 1, .85]})
    for ax in axes[:2]:
        ax.imshow(crop, extent=extent)
        ax.set_xlim(extent[0], extent[1]); ax.set_ylim(extent[2], extent[3]); ax.set_aspect("equal")
        ax.set_xlabel("Canonical x (R)"); ax.set_ylabel("Canonical y (R)")
    axes[0].set_title("Verified photo, production pose")
    ax = axes[1]
    p = np.vstack([ref, ref[0]])
    ax.plot(*p.T, color="yellow", lw=1, label="actual reference")
    fit = np.asarray(m["fit"]["polygon"])
    ax.plot(*np.vstack([fit, fit[0]]).T, color="white", lw=1, ls="--", label="final reconstructed fit")
    # Show detector lines only to their recorded reach. The sample labels below
    # come directly from the code; a drawn line does not decide exclusion.
    for j, (angle, width, reach) in enumerate(d["metal_hands"]):
        a = np.deg2rad(angle); direction = np.array([np.sin(a), -np.cos(a)])
        line = np.array([direction * .1, direction * reach])
        ax.plot(*line.T, color="magenta", lw=.8, alpha=.7, label="Metal hand direction/reach" if j == 0 else None)
    psi = m["alpha99"]["seconds_psi"]
    if psi is not None:
        a = np.deg2rad(psi); line = np.array([[0, 0], [np.sin(a), -np.cos(a)]])
        ax.plot(*line.T, color="cyan", lw=.8, label="Alpha99 seconds direction")
    s = m["iterations"][-1]
    points, normals = np.array(s["points"]), np.array(s["normals"])
    idx = np.array(s["indices"])
    offsets = np.array([np.nan if x is None else x for x in s["offset"]])
    observations = points[idx] + offsets[idx, None] * normals[idx]
    weights, residual = np.array(s["solve_weights"]), np.array(s["residual"]) * d["rpx"]
    used = weights > 0
    consistent = used & (abs(residual) <= 1)
    for mask, color, label in [(consistent, "lime", "used, residual <= 1px"), (used & ~consistent, "orange", "used, residual > 1px"), (~used, "red", "Tukey excluded")]:
        ax.scatter(*observations[mask].T, s=6, color=color, label=label)
    hidden = np.isnan(offsets)
    hand = np.array(s["why"]) == 1
    for mask, color, label in [(hidden & hand, "magenta", "hand mask: search anchor"), (hidden & ~hand, "gray", "other exclusion: search anchor")]:
        ax.scatter(*points[mask].T, s=6, color=color, marker="x", label=label)
    ax.set_title("Final solve: actual edge observations")
    ax.legend(loc="lower center", bbox_to_anchor=(.5, -.52), ncol=2, fontsize=7)
    ar = axes[2]
    for side in sorted(set(s["side"])):
        mask = np.array(s["side"])[idx] == side
        ar.scatter(np.arange(len(idx))[mask], residual[mask], s=8, label="side " + str(side))
    ar.axhline(1, color="gray", ls="--"); ar.axhline(-1, color="gray", ls="--")
    ar.set_xlabel("Available profile index (last solve)"); ar.set_ylabel("Residual (pixels)"); ar.legend(fontsize=8)
    ar.set_title("Residuals, including rejected samples")
    rotation = f"{m['tilt_deg']:+.4f}°" if m['tilt_deg'] is not None else "not applicable"
    fig.suptitle(f"{id}  hour {hour}  {m['state']}  rotation {rotation}\n"
                 f"Alpha99: {m['alpha99']['reason'] or 'clean'}; fit coverage {m['fit']['cover']:.1%}")
    fig.subplots_adjust(top=.82, bottom=.29, wspace=.28)
    fig.savefig(directory / f"{id}_{hour}_trace.png", dpi=160)
    plt.close(fig)
    # All iterations, including exact normals, observation offsets and solve weights.
    with (directory / f"{id}_{hour}_samples.csv").open("w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["iteration", "sample", "side", "anchor_x", "anchor_y", "nx", "ny", "offset_r", "why", "solve_weight", "quality_weight", "residual_px"])
        for it, step in enumerate(m["iterations"]):
            lookup = {i: q for q, i in enumerate(step["indices"] or [])}
            for i, p in enumerate(step["points"]):
                q = lookup.get(i)
                w.writerow([it, i, step["side"][i], *p, *step["normals"][i], step["offset"][i], step["why"][i],
                            step["solve_weights"][q] if q is not None else "",
                            step["weights"][q] if q is not None else "",
                            step["residual"][q]*d["rpx"] if q is not None else ""])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    check_coverage(args.directory)
    for photo, marker in CASES.items():
        render(args.directory, photo, marker)
