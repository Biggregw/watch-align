#!/usr/bin/env python3
"""
Synthetic control for opposing-minute-marker perspective recovery.

This does NOT validate real watch photos. It tests the geometry only.

After an affine/ellipse normalisation, a residual planar projective transform can
be written as:

    y = x / (1 + c.x)

For an opposite pair x and -x at the same true radius:

    k = |y(x)| / |y(-x)| = (1 - c.x) / (1 + c.x)

so:

    c.x = (1 - k) / (1 + k)

Twenty-four non-hour minute-tick pairs give an overdetermined robust estimate
of the two projective components. The experiment compares this with only four
hour-marker pairs and measures how much the recovered transform stabilises a
known local radial clearance.
"""
import math
import numpy as np

SEED = 20261004
TRIALS = 5000
NOISE_SIGMA_R = 0.0015
OUTLIER_POINTS = 2
MAX_PERSPECTIVE = 0.12
TRUE_GAP = 0.12

rng = np.random.default_rng(SEED)


def project(points, c):
    den = 1.0 + points @ c
    return points / den[:, None]


def rectify(points, c):
    den = 1.0 - points @ c
    return points / den[:, None]


def robust_fit(A, b):
    c = np.linalg.lstsq(A, b, rcond=None)[0]
    for _ in range(30):
        r = b - A @ c
        med = np.median(r)
        mad = np.median(np.abs(r - med))
        scale = max(1.4826 * mad, 1e-6)
        delta = 1.5 * scale
        w = np.ones_like(r)
        bad = np.abs(r) > delta
        w[bad] = delta / np.abs(r[bad])
        sw = np.sqrt(w)
        nxt = np.linalg.lstsq(A * sw[:, None], b * sw, rcond=None)[0]
        if np.linalg.norm(nxt - c) < 1e-12:
            return nxt
        c = nxt
    return c


def estimate_from_pairs(observed, pair_indices):
    rows, vals = [], []
    for i in pair_indices:
        j = i + 30
        rp = np.linalg.norm(observed[i])
        rm = np.linalg.norm(observed[j])
        k = rp / rm
        vals.append((1.0 - k) / (1.0 + k))
        theta = 2.0 * math.pi * i / 60.0
        rows.append((math.cos(theta), math.sin(theta)))
    return robust_fit(np.asarray(rows), np.asarray(vals))


minor_pairs = [i for i in range(30) if i % 5 != 0]  # 24 pairs
hour_pairs = [5, 10, 20, 25]  # 1<->7, 2<->8, 4<->10, 5<->11

theta = np.arange(60) * 2.0 * math.pi / 60.0
minute_ring = np.column_stack((np.cos(theta), np.sin(theta)))

minor_err = []
hour_err = []
raw_gap = []
corrected_gap = []

for _ in range(TRIALS):
    direction = rng.uniform(0.0, 2.0 * math.pi)
    magnitude = rng.uniform(0.0, MAX_PERSPECTIVE)
    c_true = magnitude * np.array((math.cos(direction), math.sin(direction)))

    observed = project(minute_ring, c_true)
    observed += rng.normal(0.0, NOISE_SIGMA_R, observed.shape)

    out = rng.choice(60, OUTLIER_POINTS, replace=False)
    observed[out] += rng.normal(0.0, 0.02, (OUTLIER_POINTS, 2))

    c_minor = estimate_from_pairs(observed, minor_pairs)
    c_hour = estimate_from_pairs(observed, hour_pairs)
    minor_err.append(np.linalg.norm(c_minor - c_true))
    hour_err.append(np.linalg.norm(c_hour - c_true))

    track = np.array([[1.0, 0.0]])
    feature = np.array([[1.0 - TRUE_GAP, 0.0]])
    y_track = project(track, c_true)[0] + rng.normal(0.0, NOISE_SIGMA_R, 2)
    y_feature = project(feature, c_true)[0] + rng.normal(0.0, NOISE_SIGMA_R, 2)

    raw_gap.append(np.linalg.norm(y_track - y_feature))
    x_track = rectify(y_track[None, :], c_minor)[0]
    x_feature = rectify(y_feature[None, :], c_minor)[0]
    corrected_gap.append(np.linalg.norm(x_track - x_feature))

minor_err = np.asarray(minor_err)
hour_err = np.asarray(hour_err)
raw_gap = np.asarray(raw_gap)
corrected_gap = np.asarray(corrected_gap)


def pct(a):
    return np.percentile(a, [50, 95, 99])


print("Synthetic opposing-minute perspective control")
print(f"trials={TRIALS} noise_sigma={NOISE_SIGMA_R:.4f}R outlier_points={OUTLIER_POINTS}")
print("projective-vector absolute error (median / p95 / p99)")
print("  24 minor-tick pairs:", " / ".join(f"{x:.6f}" for x in pct(minor_err)))
print("   4 hour-marker pairs:", " / ".join(f"{x:.6f}" for x in pct(hour_err)))
print()
print(f"true local radial gap: {TRUE_GAP:.6f}R")
print(f"raw gap:       mean={raw_gap.mean():.6f}R sd={raw_gap.std():.6f}R")
print(f"corrected gap: mean={corrected_gap.mean():.6f}R sd={corrected_gap.std():.6f}R")
print(f"spread reduction: {(1.0-corrected_gap.std()/raw_gap.std())*100.0:.1f}%")
