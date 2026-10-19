#!/usr/bin/env python3
"""Review *independent 126610 genuine photographs* through Watch Align 1.6.3.

Data only. Never enables a model or changes a production reference/threshold.
All evaluated photos must be sha256-verified from the pinned Bob's catalogue.
Does not claim inherited 124060 luminous/uncertainty values are independently
calibrated, and does not count marker observations as independent watches.
"""
import argparse
import collections
import csv
import hashlib
import pathlib
import statistics

SHAPES = ("triangle", "baton", "round")
FLAGS = ("CLEARLY_OFF", "POSSIBLE")


def read_csv(path):
    with open(path, encoding="utf-8", newline="") as fh:
        return list(csv.DictReader(fh))


def as_float(x):
    try:
        return float(x)
    except (ValueError, TypeError):
        return float("nan")


def measured(r):
    return (r["state"] != "CANT_CHECK"
            and not r["gate"]
            and r["shape"] in SHAPES
            and r["side_pct"] not in ("", None)
            and r["inout_pct"] not in ("", None))


def main():
    ap = argparse.ArgumentParser()
    for field in ("catalogue", "measurements", "fetch_log", "candidate", "report"):
        ap.add_argument("--" + field.replace("_", "-"), required=True)
    args = ap.parse_args()
    catalogue = read_csv(args.catalogue)
    measurements = read_csv(args.measurements)
    fetch = read_csv(args.fetch_log)
    indexed = {r["photo_id"]: r for r in catalogue}
    verified_photos = {r["photo_id"] for r in fetch
                       if r.get("status", "").startswith("verified")}
    # A measured photo MUST have been restored from the original pinned image.
    unknown = {r["photo_id"] for r in measurements} - verified_photos
    if unknown:
        raise SystemExit("UNSAFE: unverified photos appear in measured output: " + repr(sorted(unknown)[:8]))
    if any(r["photo_id"] not in indexed for r in measurements):
        raise SystemExit("UNSAFE: a measured photo has no provenance catalogue row")
    if any(r["model"] != "submariner_126610" for r in measurements):
        raise SystemExit("UNSAFE: the measurements were made against another model")
    counts = collections.Counter(r["state"] for r in measurements)
    by_shape = collections.Counter(r["shape"] for r in measurements if measured(r))
    watch_ids = {indexed[r["photo_id"]]["physical_watch_id"] for r in measurements}
    by_model = collections.Counter(indexed[r["photo_id"]]["model"] for r in measurements)
    valid = [r for r in measurements if measured(r)]
    independent_by_shape = {shape: len({
        indexed[r["photo_id"]]["physical_watch_id"]
        for r in valid if r["shape"] == shape}) for shape in SHAPES}
    # Separate reproducible audit groups by physical watch, not by photo.
    def group(r):
        wid = indexed[r["photo_id"]]["physical_watch_id"].encode("utf-8")
        return "holdout" if int(hashlib.sha256(wid).hexdigest()[:8], 16) % 4 == 0 else "main"

    summary = [
        "126610 FAMILY / WATCH ALIGN 1.6.3 / PROVISIONAL GENUINE VALIDATION",
        f"Catalogue photos: {len(catalogue)}",
        f"SHA256 verified photographs: {len(verified_photos)}",
        f"Photos assessed: {len({r['photo_id'] for r in measurements})}",
        f"Independent genuine watches assessed: {len(watch_ids)}",
        "Photograph source: Bob's Watches only; other sources still desirable.",
        f"States: {dict(sorted(counts.items()))}",
        f"Usable measured markers: {len(valid)}",
        f"Usable by shape: {dict(by_shape)}",
        f"Independent watches by shape: {independent_by_shape}",
        f"Family listing codes represented: {dict(sorted(by_model.items()))}",
    ]
    for label in ("main", "holdout"):
        subset = [r for r in measurements if group(r) == label]
        cs = collections.Counter(r["state"] for r in subset)
        summary.append(f"{label}: watches={len({indexed[r['photo_id']]['physical_watch_id'] for r in subset})}, rows={len(subset)}, flags={cs['CLEARLY_OFF']} definitive / {cs['POSSIBLE']} possible")
    for shape in SHAPES:
        sub = [r for r in valid if r["shape"] == shape]
        for metric in ("side_pct", "inout_pct", "tilt_line_deg"):
            vals = [abs(as_float(r[metric])) for r in sub if r[metric] not in (None, "")]
            if vals:
                vals.sort()
                summary.append(f"{shape} |{metric}|: p95={vals[min(len(vals)-1,int((len(vals)-1)*.95))]:.4f} max={vals[-1]:.4f} n={len(vals)}")
    flagged = [r for r in measurements if r["state"] in FLAGS]
    summary.append(f"Flagged genuine markers requiring evidence review: {len(flagged)}")
    for r in flagged[:40]:
        ref = indexed[r["photo_id"]]
        summary.append(
            f"FLAG {r['state']} {ref['physical_watch_id']} {ref['model']} photo={r['photo_id']} "
            f"hour={r['hour']} {r['shape']} side={r['side_pct']} inout={r['inout_pct']} "
            f"tilt={r['tilt_line_deg']} gap={r['gap_pct']} uncertainty={r.get('uncertain','')[:90]}"
        )
    missing = [s for s in SHAPES if by_shape[s] < 20 or independent_by_shape[s] < 20]
    if missing:
        verdict = "BLOCKED: too few independent verified genuine watches by shape " + repr(missing)
    elif counts["CLEARLY_OFF"]:
        verdict = "BLOCKED: definitive flags on proven genuine markers; inspect before enabling"
    elif counts["POSSIBLE"]:
        verdict = "REVIEW: possible findings on genuine watches; inspect before enabling"
    else:
        verdict = "CANDIDATE: registry count and genuine finding checks pass; inherited reference still requires evidence review"
    summary += ["", "VERDICT: " + verdict, 
                "No model is enabled or reference altered by this research job.",
                "156/163 outer-metal numbers use shared 124060 luminous/uncertainty geometry; compare fit quality, rather than assuming family-specific calibration.",
                "This audit uses a reference inherited from a different family; it is not a leave-one-out study on a freshly fit date-family reference.",
                "The original model has no calibrated date-window classification; do not claim a date-window pass."]
    pathlib.Path(args.report).write_text("\n".join(summary) + "\n", encoding="utf-8")
    print("\n".join(summary))
    # Generate proposed reference rows in exact registry/calibration CSV format.
    # Records are NOT copied from 124060. They are newly measured 126610 genuine markers.
    with open(args.candidate, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["shape", "rpx", "side_pct", "inout_pct", "tilt_deg", "gap_pct"])
        for r in valid:
            w.writerow([r["shape"], r["rpx"], r["side_pct"], r["inout_pct"],
                        r["tilt_line_deg"], r["gap_pct"]])
    print("CANDIDATE CSV BEGIN")
    for line in pathlib.Path(args.candidate).read_text().splitlines():
        print("GENCSV|" + line)
    print("CANDIDATE CSV END")
    if missing:
        raise SystemExit(2)
    if counts["CLEARLY_OFF"]:
        raise SystemExit(3)
    # Non-definitive possible findings also need review before enabling.
    if counts["POSSIBLE"]:
        raise SystemExit(4)


if __name__ == "__main__":
    main()
