#!/usr/bin/env python3
"""Part 7 plots: per-marker leave-one-out residual / angular residual /
clearance / confidence vs frame and vs approximate tilt, from the video
experiment CSV. Renders even where a signal is mostly absent (e.g. the
outer-envelope leave-one-out signal, which needs >=7/8 round markers
segmented in a single frame to fire at all) -- the absence itself is part
of what Part 7 asks this test to surface honestly.
"""
from __future__ import annotations

import argparse
import csv
from collections import defaultdict
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

HOURS = [1, 2, 4, 5, 6, 7, 8, 9, 10, 11, 12]
SIGNAL_COLS = {
    "loo_outer_residual_px": "outer-envelope leave-one-out residual (px)",
    "angular_residual_deg": "angular residual (deg, canonical frame)",
    "clearance_normalized": "local minute-track clearance (normalised)",
    "confidence": "segmentation confidence",
}


def run(csv_path: Path, out_dir: Path) -> None:
    with csv_path.open(newline="", encoding="utf-8") as f:
        rows = [r for r in csv.DictReader(f) if r.get("hour")]
    out_dir.mkdir(parents=True, exist_ok=True)

    by_hour = defaultdict(list)
    for r in rows:
        by_hour[int(r["hour"])].append(r)

    fig, axes = plt.subplots(len(SIGNAL_COLS), 1, figsize=(9, 3.2 * len(SIGNAL_COLS)), sharex=False)
    for ax, (col, label) in zip(axes, SIGNAL_COLS.items()):
        any_data = False
        for h in HOURS:
            hrows = [r for r in by_hour.get(h, []) if r.get(col)]
            if not hrows:
                continue
            tilts = [float(r["tilt_deg"]) for r in hrows]
            vals = [float(r[col]) for r in hrows]
            ax.plot(tilts, vals, "o-", label=f"hr{h}", alpha=0.8)
            any_data = True
        ax.set_xlabel("approx tilt (deg)")
        ax.set_ylabel(label)
        ax.set_title(label + ("" if any_data else "  [no usable data -- see writeup]"))
        if any_data:
            ax.legend(fontsize=7, ncol=4)
        ax.grid(alpha=0.3)
    fig.tight_layout()
    out_path = out_dir / "video_signals_vs_tilt.png"
    fig.savefig(out_path, dpi=130)
    plt.close(fig)
    print(f"wrote {out_path}")

    # coverage table (which markers were segmented in which frames)
    frames = sorted(set(r["frame"] for r in rows))
    fig2, ax2 = plt.subplots(figsize=(8, 4))
    grid = [[1 if any(rr["frame"] == fr and rr["hour"] == str(h) and rr.get("segmented") == "1"
                       for rr in rows) else 0 for h in HOURS] for fr in frames]
    ax2.imshow(grid, cmap="Greens", vmin=0, vmax=1, aspect="auto")
    ax2.set_xticks(range(len(HOURS)))
    ax2.set_xticklabels([str(h) for h in HOURS])
    ax2.set_yticks(range(len(frames)))
    ax2.set_yticklabels(frames)
    ax2.set_xlabel("hour")
    ax2.set_title("marker segmentation coverage per frame (green = segmented)")
    fig2.tight_layout()
    out_path2 = out_dir / "video_coverage.png"
    fig2.savefig(out_path2, dpi=130)
    plt.close(fig2)
    print(f"wrote {out_path2}")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--csv", required=True, type=Path)
    ap.add_argument("--out-dir", required=True, type=Path)
    args = ap.parse_args()
    run(args.csv, args.out_dir)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
