#!/usr/bin/env python3
"""Synthetic perturbation experiment for the frozen GMT Stage 3 baseline.

Consumes the committed genuine-baseline JSON. It never changes baseline values or
production QC. Each frozen feature is perturbed independently with deterministic
normalized deltas so sensitivity can be compared in feature units.
"""
from __future__ import annotations
import csv, json, math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / 'docs/research/gmt-genuine-baseline-results/genuine-baseline-v1.json'
OUT = ROOT / 'docs/research/gmt-genuine-baseline-results/synthetic-perturbation.csv'
LEVELS = (-0.02, -0.01, -0.005, 0.0, 0.005, 0.01, 0.02)


def numeric_features(obj):
    if isinstance(obj, dict):
        # Prefer the explicit frozen feature record if present.
        for key in ('features', 'feature_values', 'baseline_features'):
            v = obj.get(key)
            if isinstance(v, dict) and v:
                nums = {str(k): float(x) for k, x in v.items()
                        if isinstance(x, (int, float)) and math.isfinite(float(x))}
                if nums:
                    return nums
        found = {}
        def walk(x, prefix=''):
            if isinstance(x, dict):
                for k, v in x.items(): walk(v, f'{prefix}.{k}' if prefix else str(k))
            elif isinstance(x, (int, float)) and not isinstance(x, bool) and math.isfinite(float(x)):
                found[prefix] = float(x)
        walk(obj)
        return found
    raise ValueError('baseline JSON root must be an object')


def main():
    data = json.loads(BASE.read_text())
    feats = numeric_features(data)
    if len(feats) < 13:
        raise SystemExit(f'Expected at least 13 numeric frozen features, found {len(feats)}')
    # If more than 13 numeric metadata fields exist, retain Stage-3-like names first.
    stage = {k:v for k,v in feats.items() if any(t in k for t in ('stage3','h06','h09','h12'))}
    if len(stage) >= 13:
        feats = dict(sorted(stage.items())[:13])
    elif len(feats) != 13:
        raise SystemExit(f'Could not identify exactly 13 frozen features safely; found {len(feats)} numerics')
    OUT.parent.mkdir(parents=True, exist_ok=True)
    rows=[]
    for name, base in sorted(feats.items()):
        scale = max(abs(base), 1e-9)
        for delta in LEVELS:
            value = base + delta * scale
            rows.append((name, base, delta, value, value-base, abs(value-base)/scale))
    with OUT.open('w', newline='') as f:
        w=csv.writer(f); w.writerow(['feature','baseline','relative_perturbation','perturbed','absolute_delta','normalized_delta']); w.writerows(rows)
    print(f'wrote {len(rows)} rows for {len(feats)} frozen features to {OUT}')

if __name__ == '__main__': main()
