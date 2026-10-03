"""Measure calibrator photos through the production app route and summarise per watch.

Replaces the research-path adapter (subresearch.geometry124060 with an empty review), which
measured values the app itself would withhold. Here every value comes from the desktop harness
driver CalibMeasure, which runs WatchAlignCoreV13 for the configured model and keeps only
measurements that pass that route's reliability gates. Gated-out values are blank and never reach
calibration.

The exact model id is passed all the way into the harness. This is deliberately explicit: a new
Submariner reference must never be calibrated by silently running the 124060 route.

Outputs, per (partition, class):
  {model}_{partition}_{class}_photo.csv         one row per photo (gated values)
  {model}_{partition}_{class}_watch.csv         metric, physical_watch_id, source_name, median, photos
  {model}_{partition}_{class}_repeatability.csv metric-level spread and pose-sensitivity evidence
"""
from __future__ import annotations

import csv
import math
import statistics
import subprocess
from collections import Counter, defaultdict
from pathlib import Path

import contracts
import split as locked_split

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
HARNESS = REPO / "tools" / "desktop-harness" / "run.sh"
MIN_POSE_PHOTOS = 8
POSE_RHO = 0.5


def _f(v) -> float:
    try:
        x = float(v)
        return x if math.isfinite(x) else math.nan
    except (TypeError, ValueError):
        return math.nan


def mad(v: list[float]) -> float:
    v = [x for x in v if math.isfinite(x)]
    if not v:
        return math.nan
    m = statistics.median(v)
    return statistics.median(abs(x - m) for x in v)


def _ranks(v: list[float]) -> list[float]:
    order = sorted(range(len(v)), key=lambda i: v[i])
    r = [0.0] * len(v)
    i = 0
    while i < len(order):
        j = i
        while j + 1 < len(order) and v[order[j + 1]] == v[order[i]]:
            j += 1
        for k in range(i, j + 1):
            r[order[k]] = (i + j) / 2.0
        i = j + 1
    return r


def spearman(x: list[float], y: list[float]) -> float:
    if len(x) < 3:
        return math.nan
    rx, ry = _ranks(x), _ranks(y)
    mx, my = statistics.fmean(rx), statistics.fmean(ry)
    num = sum((a - mx) * (b - my) for a, b in zip(rx, ry))
    den = math.sqrt(sum((a - mx) ** 2 for a in rx) * sum((b - my) ** 2 for b in ry))
    return num / den if den > 0 else math.nan


def source_map(split_csv: Path) -> dict[str, str]:
    """Physical watch -> genuine dealer/source recorded in the locked split."""
    out: dict[str, str] = {}
    if not split_csv.exists():
        return out
    with split_csv.open(newline="", encoding="utf-8") as fh:
        for r in csv.DictReader(fh):
            wid = (r.get("physical_watch_id") or "").strip()
            if wid:
                out[wid] = (r.get("source_name") or "").strip()
    return out


def photo_list(acq_root: Path, split_csv: Path, partition: str, cls: str, expected_model: str) -> list[tuple[str, Path]]:
    expected_model = contracts.exact_model(expected_model, "measurement expected model")
    parts = locked_split.load(split_csv)
    out = []
    with (acq_root / "acquired_images.csv").open(newline="", encoding="utf-8") as fh:
        for line_no, r in enumerate(csv.DictReader(fh), start=2):
            actual = contracts.exact_model(r.get("model"), f"acquired images line {line_no}")
            if actual != expected_model:
                raise contracts.ContractError(
                    f"acquired images line {line_no}: model {actual} does not match requested model {expected_model}"
                )
            wid = r.get("physical_watch_id") or r.get("candidate_id")
            if parts.get(wid) != partition or (r.get("class_label") or "").lower() != cls:
                continue
            if (r.get("acquisition_status") or "acquired") != "acquired" or r.get("exact_duplicate_of"):
                continue
            out.append((wid, acq_root / r["local_path"]))
    return out


def run_harness(model: str, photos: list[tuple[str, Path]], out_csv: Path) -> None:
    """Run the exact configured model route; never allow an implicit 124060 fallback."""
    model = (model or "").strip().upper()
    if not model:
        raise ValueError("calibrator model is required")
    out_csv.parent.mkdir(parents=True, exist_ok=True)
    lst = out_csv.with_suffix(".list.tsv")
    lst.write_text("".join(f"{w}\t{p}\n" for w, p in photos), encoding="utf-8")
    subprocess.run(
        ["bash", str(HARNESS), "CalibMeasure", model, str(lst), str(out_csv)],
        cwd=REPO,
        check=True,
    )


def validate_harness_output(photo_csv: Path, model: str, photos: list[tuple[str, Path]]) -> None:
    """Verify that the measurement boundary returned the exact requested population."""
    expected_model = contracts.exact_model(model, "harness expected model")
    contracts.validate_csv_exact_model(photo_csv, expected_model, "measurement output")
    with photo_csv.open(newline="", encoding="utf-8") as fh:
        rows = list(csv.DictReader(fh))
    if len(rows) != len(photos):
        raise contracts.ContractError(
            f"measurement output row count {len(rows)} does not match requested photo count {len(photos)}"
        )
    actual_watches = Counter((row.get("physical_watch_id") or "").strip() for row in rows)
    expected_watches = Counter(wid for wid, _ in photos)
    if actual_watches != expected_watches:
        raise contracts.ContractError(
            f"measurement output physical-watch population {dict(actual_watches)} does not match requested population {dict(expected_watches)}"
        )


def summarise(photo_csv: Path, metrics: list[str], watch_csv: Path, repeat_csv: Path,
              source_by_watch: dict[str, str] | None = None) -> dict:
    with photo_csv.open(newline="", encoding="utf-8") as fh:
        rows = list(csv.DictReader(fh))
    source_by_watch = source_by_watch or {}
    by_watch: dict[str, dict[str, list[tuple[float, float]]]] = defaultdict(lambda: defaultdict(list))
    for r in rows:
        tilt = _f(r.get("pose_tilt_deg"))
        for m in metrics:
            x = _f(r.get(m))
            if math.isfinite(x):
                by_watch[r["physical_watch_id"]][m].append((x, tilt))

    watch_rows, repeat_rows = [], []
    for m in metrics:
        medians, within, dv, dt = [], [], [], []
        for wid, d in sorted(by_watch.items()):
            pts = d.get(m) or []
            if not pts:
                continue
            vals = [x for x, _ in pts]
            med = statistics.median(vals)
            medians.append(med)
            watch_rows.append({
                "metric": m,
                "physical_watch_id": wid,
                "source_name": source_by_watch.get(wid, ""),
                "median": f"{med:.8g}",
                "photos": len(vals),
            })
            if len(vals) >= 2:
                within.append(mad(vals))
                tilts = [t for _, t in pts if math.isfinite(t)]
                if len(tilts) >= 2:
                    tm = statistics.median(tilts)
                    for x, t in pts:
                        if math.isfinite(t):
                            dv.append(x - med)
                            dt.append(t - tm)
        rho = spearman(dv, dt) if len(dv) >= MIN_POSE_PHOTOS else math.nan
        repeat_rows.append({
            "metric": m,
            "watches": len(medians),
            "multi_photo_watches": len(within),
            "between_watch_mad": f"{mad(medians):.8g}" if medians else "",
            "within_watch_mad_median": f"{statistics.median(within):.8g}" if within else "",
            "pose_photos": len(dv),
            "pose_spearman": f"{rho:.4f}" if math.isfinite(rho) else "",
            "pose_sensitive": "1" if math.isfinite(rho) and abs(rho) >= POSE_RHO else "",
            "repeatability_class": "insufficient data" if len(within) < 3 else "measured",
        })

    for path, data, fields in (
        (watch_csv, watch_rows, ["metric", "physical_watch_id", "source_name", "median", "photos"]),
        (repeat_csv, repeat_rows, list(repeat_rows[0]) if repeat_rows else ["metric"]),
    ):
        with path.open("w", newline="", encoding="utf-8") as fh:
            w = csv.DictWriter(fh, fieldnames=fields)
            w.writeheader()
            w.writerows(data)
    return {
        "photos": len(rows),
        "watches": len(by_watch),
        "photos_with_any_gated_value": sum(
            any(math.isfinite(_f(r.get(m))) for m in metrics) for r in rows
        ),
    }


def measure(config: dict, acq_root: Path, split_csv: Path, out_dir: Path, partition: str, cls: str) -> dict:
    model = str(config.get("model") or "").strip().upper()
    if not model:
        raise ValueError("calibrator config must contain an exact model")
    metrics = [s["metric"] for s in config["calibration_metrics"]]
    pref = out_dir / f"{model}_{partition}_{cls}"
    photos = photo_list(acq_root, split_csv, partition, cls, model)
    photo_csv = Path(f"{pref}_photo.csv")
    if photos:
        run_harness(model, photos, photo_csv)
    else:
        out_dir.mkdir(parents=True, exist_ok=True)
        photo_csv.write_text("physical_watch_id,model,path\n", encoding="utf-8")
    validate_harness_output(photo_csv, model, photos)
    return summarise(
        photo_csv,
        metrics,
        Path(f"{pref}_watch.csv"),
        Path(f"{pref}_repeatability.csv"),
        source_map(split_csv),
    )
