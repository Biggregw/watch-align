#!/usr/bin/env python3
"""Compare matching photo/hour measurements; never count duplicated image rows twice."""
import argparse
from collections import Counter, defaultdict
import csv
import hashlib
import json
import math
from pathlib import Path

METRICS = ("side_pct", "inout_pct", "tilt_line_deg", "cover", "fit_rms_px", "bad_frac")


def index(rows):
    out, duplicates = {}, []
    for row in rows:
        key = (row["photo_id"], row["hour"])
        if key in out:
            # The GMT catalogue lists one exact photo under two physical-watch
            # aliases. Collapse only identical measurements, recording aliases.
            changed = [k for k in row if k != "watch" and row[k] != out[key][k]]
            if changed:
                raise ValueError(f"Conflicting duplicate {key}: {changed}")
            duplicates.append({"key": key, "watch_aliases": [out[key]["watch"], row["watch"]]})
        else:
            out[key] = row
    if not out:
        raise ValueError("No measured markers")
    return out, duplicates


def reliable(row):
    return row["state"] != "CANT_CHECK" and not row["gate"] and not row["uncertain"]


def coverage(measurements, manifest, log):
    """Require an explicit complete analysis or NOPOSE outcome for every input."""
    expected = Counter(r["photo_id"] for r in manifest)
    outcomes = defaultdict(list)
    for line in log.splitlines():
        parts = line.split()
        if len(parts) == 2 and parts[0] in ("TRACK", "NOPOSE"):
            outcomes[parts[1]].append(parts[0])
    if Counter({p: len(v) for p, v in outcomes.items()}) != expected:
        raise ValueError("Runner did not account for every cohort image")
    for p, statuses in outcomes.items():
        if len(set(statuses)) != 1:
            raise ValueError("Conflicting duplicate image outcomes: " + p)
        rows = [r for (photo, _), r in measurements.items() if photo == p]
        if statuses[0] == "NOPOSE":
            if rows:
                raise ValueError("NOPOSE has measurements: " + p)
        else:
            models = {r["model"] for r in rows}
            if len(models) != 1:
                raise ValueError("Missing/mixed model measurements: " + p)
            model = next(iter(models))
            hours = set(range(1, 13))
            if model in ("gmt_126710", "submariner_126610"):
                hours.remove(3)
            elif model != "submariner_124060":
                raise ValueError("Unknown model: " + model)
            if {int(r["hour"]) for r in rows} != hours:
                raise ValueError("Incomplete marker hours: " + p)
    return {p: v[0] for p, v in outcomes.items()}


def compare(before, after):
    if set(before) != set(after):
        raise ValueError(f"Unmatched marker keys: lost={sorted(set(before)-set(after))}, added={sorted(set(after)-set(before))}")
    report = {"photos": len({k[0] for k in before}), "markers": len(before),
              "before_states": dict(Counter(r["state"] for r in before.values())),
              "after_states": dict(Counter(r["state"] for r in after.values())),
              "transitions": dict(Counter(before[k]["state"] + " -> " + after[k]["state"] for k in before)),
              "reliable_before": sum(map(reliable, before.values())), "reliable_after": sum(map(reliable, after.values())),
              "reliable_retained": sum(reliable(before[k]) and reliable(after[k]) for k in before),
              "new_uncertain": [{"photo_id": k[0], "hour": k[1], "after": after[k]}
                                for k in before if reliable(before[k]) and not reliable(after[k])],
              "new_cant_check": [], "state_changes": [], "metric_changes": {}, "by_shape": {}}
    changes = defaultdict(list)
    for key, old in before.items():
        new = after[key]
        if old["shape"] != new["shape"] or old["model"] != new["model"]:
            raise ValueError("Model/shape mismatch: " + repr(key))
        if old["state"] != new["state"]:
            entry = {"photo_id": key[0], "hour": key[1], "before": old, "after": new}
            report["state_changes"].append(entry)
            if new["state"] == "CANT_CHECK":
                report["new_cant_check"].append(entry)
        for metric in METRICS:
            if old[metric] and new[metric]:
                delta = float(new[metric]) - float(old[metric])
                if not math.isfinite(delta):
                    raise ValueError("Non-finite metric: " + repr(key))
                if delta:
                    changes[metric].append((key, delta))
    for metric, values in changes.items():
        report["metric_changes"][metric] = {"changed": len(values), "max_absolute_change": max(abs(d) for _, d in values),
            "largest": sorted([{"photo_id": k[0], "hour": k[1], "delta": d} for k, d in values], key=lambda r: -abs(r["delta"]))[:10]}
    for shape in sorted({r["shape"] for r in before.values()}):
        keys = [k for k in before if before[k]["shape"] == shape]
        report["by_shape"][shape] = {"markers": len(keys),
            "before": dict(Counter(before[k]["state"] for k in keys)), "after": dict(Counter(after[k]["state"] for k in keys)),
            "reliable_before": sum(reliable(before[k]) for k in keys), "reliable_after": sum(reliable(after[k]) for k in keys)}
    return report


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("before", type=Path); p.add_argument("after", type=Path)
    p.add_argument("manifest", type=Path); p.add_argument("image_root", type=Path); p.add_argument("output", type=Path)
    p.add_argument("--before-log", type=Path, required=True)
    p.add_argument("--after-log", type=Path, required=True)
    p.add_argument("--suitability", type=Path, help="Reviewed photo suitability CSV; full-cohort results are always retained")
    a = p.parse_args()
    before, bd = index(csv.DictReader(a.before.open()))
    after, ad = index(csv.DictReader(a.after.open()))
    manifest = list(csv.DictReader(a.manifest.open()))
    if coverage(before, manifest, a.before_log.read_text()) != coverage(after, manifest, a.after_log.read_text()):
        raise ValueError("Before/after analysis availability differs")
    photos = {r["photo_id"]: r for r in manifest}
    for id, row in photos.items():
        path = a.image_root / row["local_path"]
        if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != row["sha256"]:
            raise ValueError("Missing/unverified cohort image: " + id)
    if {k[0] for k in before} - set(photos):
        raise ValueError("Measurement outside verified cohort")
    report = compare(before, after)
    report["duplicate_rows"] = {"before": bd, "after": ad}
    report["verified_cohort_photos"] = len(photos)
    report["unassessed_photos"] = sorted(set(photos) - {k[0] for k in before})
    # Merge physical-watch aliases sharing a byte-identical photo, rather than
    # inflating the independent genuine watch count.
    parent = {r["physical_watch_id"]: r["physical_watch_id"] for r in manifest}
    def find(w):
        while parent[w] != w:
            w = parent[w]
        return w
    aliases = defaultdict(list)
    for r in manifest:
        aliases[r["sha256"]].append(r["physical_watch_id"])
    for ws in aliases.values():
        for w in ws[1:]:
            parent[find(w)] = find(ws[0])
    report["independent_watches_assessed"] = len({find(photos[k[0]]["physical_watch_id"]) for k in before})
    if a.suitability:
        labels = {r["photo_id"]: r for r in csv.DictReader(a.suitability.open())}
        assessed = {k[0] for k in before}
        if set(labels) != assessed:
            raise ValueError("Suitability review does not cover exactly the assessed photographs")
        for photo, label in labels.items():
            if label["sha256"] != photos[photo]["sha256"] or label["eligible"] not in ("yes", "no"):
                raise ValueError("Invalid suitability record: " + photo)
        selected = {p for p, r in labels.items() if r["eligible"] == "yes"}
        report["qc_suitable"] = compare({k: r for k, r in before.items() if k[0] in selected},
                                         {k: r for k, r in after.items() if k[0] in selected})
        report["qc_suitable"]["independent_watches_assessed"] = len({find(photos[p]["physical_watch_id"]) for p in selected})
        report["suitability_exclusions"] = [r for r in labels.values() if r["eligible"] == "no"]
    a.output.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({k: v for k, v in report.items() if k not in ("state_changes", "new_cant_check", "metric_changes", "duplicate_rows")}, indent=2))


if __name__ == "__main__":
    main()
