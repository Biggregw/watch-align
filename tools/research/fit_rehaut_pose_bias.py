from __future__ import annotations

"""Fit same-watch GMT12 measurement bias against rehaut pose signals.

Input is the CSV emitted by run_rehaut_tilt_series.py. This is deliberately a
research fitter, not production calibration. It estimates how much the raw
GMT12 top-clearance measurement moves as V/H rehaut asymmetry changes while
the physical watch geometry is fixed.

The intercept is the fitted zero-asymmetry top clearance. Correction for one
row is therefore:

    corrected = raw - (predicted_pose_bias)
              = raw - (model(V,H) - model(0,0))

No rejection threshold is inferred here.
"""

import argparse
import csv
from pathlib import Path

import numpy as np


def _features(v: float, h: float, model: str):
    if model == "linear":
        return [1.0, v, h]
    if model == "quadratic":
        return [1.0, v, h, v * v, h * h, v * h]
    raise ValueError(model)


def _names(model: str):
    if model == "linear":
        return ["intercept", "V", "H"]
    return ["intercept", "V", "H", "V2", "H2", "VH"]


def _usable(rows, watch_id=None):
    out = []
    for r in rows:
        if watch_id and r.get("watch_id") != watch_id:
            continue
        if r.get("rehaut_status") != "MEASURED" or r.get("gmt12_status") != "MEASURED":
            continue
        try:
            v = float(r["vertical_asymmetry"])
            h = float(r["horizontal_asymmetry"])
            y = float(r["raw_top_clearance"])
        except (KeyError, TypeError, ValueError):
            continue
        out.append((r.get("image_id", ""), v, h, y))
    return out


def fit(data, model):
    X = np.asarray([_features(v, h, model) for _, v, h, _ in data], float)
    y = np.asarray([y for *_, y in data], float)
    if len(data) < X.shape[1] + 2:
        raise ValueError(
            f"need at least {X.shape[1] + 2} usable images for {model} fit; got {len(data)}"
        )
    beta = np.linalg.lstsq(X, y, rcond=None)[0]
    pred = X @ beta
    residual = y - pred

    # Leave-one-out is intentionally simple and transparent for this small
    # controlled experiment. A model that only looks good in-sample is not a
    # candidate for production correction.
    loo = []
    for i in range(len(data)):
        keep = np.arange(len(data)) != i
        b = np.linalg.lstsq(X[keep], y[keep], rcond=None)[0]
        loo.append(float(y[i] - X[i] @ b))
    loo = np.asarray(loo)
    return beta, residual, loo


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("csv_path", type=Path)
    ap.add_argument("--watch-id")
    ap.add_argument("--model", choices=("linear", "quadratic"), default="linear")
    args = ap.parse_args()

    with args.csv_path.open(newline="", encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    data = _usable(rows, args.watch_id)
    beta, residual, loo = fit(data, args.model)

    print(f"model={args.model} n={len(data)}")
    for name, value in zip(_names(args.model), beta):
        print(f"{name}={value:.9g}")
    print(f"in_sample_rmse={np.sqrt(np.mean(residual ** 2)):.9g}")
    print(f"loo_rmse={np.sqrt(np.mean(loo ** 2)):.9g}")
    print(f"loo_p95_abs={np.percentile(np.abs(loo), 95):.9g}")
    print("rows:")
    for image_id, v, h, y in data:
        x = np.asarray(_features(v, h, args.model), float)
        zero = np.asarray(_features(0.0, 0.0, args.model), float)
        pose_bias = float(x @ beta - zero @ beta)
        corrected = y - pose_bias
        print(
            f"  {image_id}: V={v:+.5f} H={h:+.5f} raw={y:.6f} "
            f"bias={pose_bias:+.6f} corrected={corrected:.6f}"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
