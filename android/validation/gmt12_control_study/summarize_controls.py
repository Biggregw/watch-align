#!/usr/bin/env python3
"""Regenerate study summaries without altering the production metric or ranges."""
import csv
import math
import statistics
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent
METRICS = ("base_to_60", "apex_to_crown", "rotation_deg")

def read(name):
    with (ROOT / name).open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))

def fmt(value):
    return "" if value is None else f"{value:.6f}"

def stats(values):
    values = sorted(values)
    return {
        "n": len(values), "mean": statistics.fmean(values),
        "sd": statistics.stdev(values) if len(values) > 1 else 0.0,
        "median": statistics.median(values), "min": values[0], "max": values[-1],
    }

rows = read("raw_per_image.csv")
usable = [r for r in rows if r["measurement_status"].startswith("historical_metric")]

watch_values = defaultdict(lambda: defaultdict(list))
watch_meta = {}
for row in usable:
    key = (row["group"], row["watch_id"])
    watch_meta[key] = row
    for metric in METRICS:
        if row[metric]: watch_values[key][metric].append(float(row[metric]))

per_watch = []
for key, values in sorted(watch_values.items()):
    meta = watch_meta[key]
    out = {"group": key[0], "watch_id": key[1], "reference": meta["reference"], "factory": meta["factory"], "usable_images": 1}
    for metric in METRICS: out[metric] = statistics.fmean(values[metric])
    per_watch.append(out)

with (ROOT / "per_watch.csv").open("w", newline="", encoding="utf-8") as handle:
    fields = ("group", "watch_id", "reference", "factory", "usable_images") + METRICS
    writer = csv.DictWriter(handle, fieldnames=fields); writer.writeheader(); writer.writerows(per_watch)

def write_distributions(name, source):
    grouped = defaultdict(list)
    for row in source:
        for metric in METRICS: grouped[(row["group"], metric)].append(float(row[metric]))
    with (ROOT / name).open("w", newline="", encoding="utf-8") as handle:
        fields = ("group", "metric", "n", "mean", "sd", "median", "min", "max")
        writer = csv.DictWriter(handle, fieldnames=fields); writer.writeheader()
        for key, values in sorted(grouped.items()): writer.writerow({"group": key[0], "metric": key[1], **{k: fmt(v) if k != "n" else v for k, v in stats(values).items()}})

write_distributions("distributions_per_image.csv", usable)
write_distributions("distributions_per_watch.csv", per_watch)

with (ROOT / "breakdowns.csv").open("w", newline="", encoding="utf-8") as handle:
    fields = ("group", "breakdown", "value", "metric", "n", "mean", "median")
    writer = csv.DictWriter(handle, fieldnames=fields); writer.writeheader()
    for group, field in (("genuine", "reference"), ("replica", "factory")):
        for value in sorted({r[field] for r in per_watch if r["group"] == group}):
            selected = [r for r in per_watch if r["group"] == group and r[field] == value]
            for metric in METRICS:
                vals = [float(r[metric]) for r in selected]
                writer.writerow({"group": group, "breakdown": field, "value": value, "metric": metric, "n": len(vals), "mean": fmt(statistics.fmean(vals)), "median": fmt(statistics.median(vals))})

sensitivity = {r["metric"]: r for r in read("repeatability_sensitivity.csv")}
genuine = next(r for r in per_watch if r["group"] == "genuine")
replica = next(r for r in per_watch if r["group"] == "replica")
with (ROOT / "separation_vs_sensitivity.csv").open("w", newline="", encoding="utf-8") as handle:
    fields = ("metric", "genuine_value", "replica_value", "absolute_separation", "median_error", "p95_error", "max_error", "separation_over_median", "separation_over_p95", "separation_over_max", "distinguishable_beyond_max")
    writer = csv.DictWriter(handle, fieldnames=fields); writer.writeheader()
    for metric in METRICS:
        gap = abs(float(genuine[metric]) - float(replica[metric])); s = sensitivity[metric]
        med, p95, maximum = (float(s[x]) for x in ("median_abs_error", "p95_abs_error", "max_abs_error"))
        writer.writerow({"metric": metric, "genuine_value": fmt(float(genuine[metric])), "replica_value": fmt(float(replica[metric])), "absolute_separation": fmt(gap), "median_error": fmt(med), "p95_error": fmt(p95), "max_error": fmt(maximum), "separation_over_median": fmt(gap/med), "separation_over_p95": fmt(gap/p95), "separation_over_max": fmt(gap/maximum), "distinguishable_beyond_max": str(gap > maximum).lower()})

report = """# GMT 12-triangle validation report

## Accepted sample

- Genuine: 1 independent watch/reference image.
- Replica: 1 independent physical watch.
- Minimum 10 + 10 target: not reached. Nine additional independent genuine watches and nine additional independent replica watches remain required.

The D195/PHS image and Infamous_QC screenshots remain grouped as `rep-001-greg-D195` and are not counted independently.

## Raw results and separation

| Metric | Genuine | Greg replica | Separation | Median error | P95 error | Maximum error | Beyond maximum? |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| Base-to-60 | 0.164 | 0.086 | 0.078 | 0.002077 | 0.005457 | 0.008031 | Yes |
| Apex-to-crown | 0.291 | 0.284 | 0.007 | 0.002392 | 0.006552 | 0.009271 | No |
| Rotation | 0.000 degrees | -0.290 degrees | 0.290 degrees | 0.137654 degrees | 0.364686 degrees | 0.536056 degrees | No |

Greg's base-to-60 separation is 37.55 times median perturbation error, 14.29 times p95, and 9.71 times the maximum error. Under this defined perturbation model, `0.086` is clearly distinguishable from the accepted genuine control. Apex-to-crown only narrowly exceeds p95 and does not exceed maximum error. Rotation does not exceed p95 or maximum error.

## Reference consistency and subgroup analysis

Reference pooling cannot be tested with one accepted genuine BLNR. There are no accepted BLRO, GRNR or CHNR controls. Factory effects cannot be tested with one accepted ARF replica. The generated breakdown file records these one-case strata without implying a distribution.

## Limitations

The minimum valid sample was not reached. Reddit candidates were retained in the inventory but rejected from measurement when a confidently corrected nine-point set was unavailable. Sprite/VTNR and materially different references were rejected by design. Historical app builds retained the three raw outputs for the two accepted cases but not their exact corrected point coordinates, preventing coordinate-level replay. The result therefore supports a strong within-method finding for Greg's base-to-60 value, not a calibrated genuine-versus-replica population claim.

No range, threshold, or classification behavior was changed.
"""
(ROOT / "VALIDATION_REPORT.md").write_text(report, encoding="utf-8")
print(f"accepted watches: genuine={sum(r['group']=='genuine' for r in per_watch)}, replica={sum(r['group']=='replica' for r in per_watch)}")
