#!/usr/bin/env python3
"""Run the frozen GMT measurement pipeline, blind, against the predeclared
RepTimeQC replica control set (docs/research/gmt-replica-ground-truth-control-set.csv).

Fetches via gallery-dl (reddit.com's own public JSON endpoints), the same
tool datasets/126710BLNR/fetch_images.py already uses successfully for
reddit/imgur sources in this project -- a first attempt using
build_gmt_genuine_baseline.py's plain requests.get()-based generic page
scraper got HTTP 403 from Reddit on every control (Reddit blocks
unauthenticated generic scraping; gallery-dl's reddit extractor uses
Reddit's own public post JSON, which is not blocked the same way).

Feature computation (pose + marker_features + gpf.compute()) reuses
build_gmt_genuine_baseline.py's functions unchanged, operating on the
locally-downloaded image bytes rather than re-fetching over HTTP.

Each control_id is one physical watch by construction (the control-set
protocol already deduplicated albums/reposts of the same watch before
assigning control_ids). Repeated images within one control's own source
post are collapsed the same way build_gmt_genuine_baseline.py collapses
repeated dealer-page images: per-image rows are kept in full, and a
per-watch (here, per-control) median row is written separately -- image
count must never be read as independent-watch count.

This script does not compute deviation-from-genuine or a discrimination
verdict; that is a separate, auditable step (analyze_replica_control_set.py)
so the raw measurement is never mixed with the interpretation that consumes
it.
"""
from __future__ import annotations

import csv
import importlib.util
import shutil
import subprocess
import sys
import tempfile
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "tools" / "research" / "build_gmt_genuine_baseline.py"
CONTROLS = ROOT / "docs" / "research" / "gmt-replica-ground-truth-control-set.csv"
OUTDIR = ROOT / "docs" / "research" / "gmt-replica-control-set-results"
MAX_IMAGES_PER_CONTROL = 6
VALID_EXT = {".jpg", ".jpeg", ".png", ".webp"}

spec = importlib.util.spec_from_file_location("genuine_baseline", BASE)
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)


def read_controls():
    with CONTROLS.open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def fetch_via_gallery_dl(post_url: str, raw_dir: Path) -> tuple[str, str]:
    """Returns (page_status, log_tail) -- log_tail is gallery-dl's own error text (truncated),
    preserved so a fetch failure's real cause is inspectable rather than just a return code."""
    if shutil.which("gallery-dl") is None:
        return "gallery-dl_not_installed", ""
    cmd = ["gallery-dl", "-v", "--no-mtime", "--range", f"1-{MAX_IMAGES_PER_CONTROL}", "-D", str(raw_dir), post_url]
    try:
        result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=240)
    except subprocess.TimeoutExpired:
        return "gallery-dl_timeout", ""
    (raw_dir / "fetch.log").write_text(result.stdout, encoding="utf-8", errors="replace")
    status = "gallery_dl_ok" if result.returncode == 0 else f"gallery-dl_returncode_{result.returncode}"
    return status, result.stdout[-800:]


def local_image_candidates(raw_dir: Path):
    for path in sorted(raw_dir.rglob("*")):
        if path.is_file() and path.suffix.lower() in VALID_EXT:
            yield path


def decode_local(path: Path):
    try:
        raw = m.Image.open(path)
        raw = m.ImageOps.exif_transpose(raw).convert("RGB")
    except Exception:
        return None
    if min(raw.size) < 300 or raw.width * raw.height < 180_000:
        return None
    raw.thumbnail((1800, 1800), m.Image.Resampling.LANCZOS)
    arr = m.cv2.cvtColor(m.np.array(raw), m.cv2.COLOR_RGB2BGR)
    return arr


def main() -> int:
    controls = read_controls()
    OUTDIR.mkdir(parents=True, exist_ok=True)

    all_rows = []
    source_rows = []
    global_fp = set()

    with tempfile.TemporaryDirectory(prefix="watch-align-replica-controls-") as tmp:
        tmp_root = Path(tmp)
        for ci, ctrl in enumerate(controls, 1):
            control_id = ctrl["control_id"]
            page_url = ctrl["reddit_post_url"]
            print(f"[{ci}/{len(controls)}] {control_id}: {page_url}")
            raw_dir = tmp_root / control_id
            raw_dir.mkdir(parents=True, exist_ok=True)
            page_status, log_tail = fetch_via_gallery_dl(page_url, raw_dir)
            candidates = list(local_image_candidates(raw_dir))

            accepted = 0
            downloaded = 0
            pose_ok = 0
            low_tilt = 0
            errors = []
            for path in candidates:
                if accepted >= MAX_IMAGES_PER_CONTROL:
                    break
                bgr = decode_local(path)
                if bgr is None:
                    continue
                downloaded += 1
                fp = m.image_fingerprint(bgr)
                if fp in global_fp:
                    continue
                global_fp.add(fp)
                try:
                    res = m.pipeline.build(bgr)
                except Exception as exc:
                    errors.append(f"pipeline:{type(exc).__name__}")
                    continue
                if res.reason or not res.accepted or res.acquisition is None:
                    errors.append(f"pose_rejected:{res.reason or 'not accepted'}")
                    continue
                pose_ok += 1
                ellipse = res.acquisition.dial_ellipse
                roll = res.solved_roll
                tilt = float(res.tilt_deg)
                gray = m.cv2.cvtColor(bgr, m.cv2.COLOR_BGR2GRAY)
                obs = {}
                for h in m.mc.ALL_MARKER_HOURS:
                    try:
                        o = m.mc.segment_marker(gray, ellipse, roll, res.dial_radius_px, h)
                    except Exception:
                        o = None
                    if o is not None:
                        obs[h] = o
                row = {
                    "control_id": control_id, "physical_watch_id": control_id,
                    "reference": ctrl.get("reference", ""), "factory": ctrl.get("factory", ""),
                    "variant": ctrl.get("variant", ""), "human_label": ctrl.get("human_label", ""),
                    "label_strength": ctrl.get("label_strength", ""),
                    "expected_primary_metric": ctrl.get("expected_primary_metric", ""),
                    "expected_supporting_metrics": ctrl.get("expected_supporting_metrics", ""),
                    "source_url": page_url, "image_index": accepted, "image_path": path.name,
                    "page_status": page_status, "tilt_deg": tilt, "pose_confidence": float(res.confidence),
                    "dial_radius_px": float(res.dial_radius_px), "n_markers_segmented": len(obs),
                }
                row.update(m.marker_features(obs, ellipse, roll, res.dial_radius_px))
                try:
                    tri = m.gpf.compute(gray, ellipse, roll, res.dial_radius_px, tilt)
                    for k, v in tri.features.items():
                        if m.finite(v):
                            row["h12.stage3_" + k] = float(v)
                except Exception as exc:
                    errors.append(f"triangle:{type(exc).__name__}")
                all_rows.append(row)
                accepted += 1
                if tilt <= 10.0:
                    low_tilt += 1
            source_rows.append({
                "control_id": control_id, "reference": ctrl.get("reference", ""),
                "factory": ctrl.get("factory", ""), "human_label": ctrl.get("human_label", ""),
                "label_strength": ctrl.get("label_strength", ""), "source_url": page_url,
                "page_status": page_status, "candidate_files": len(candidates), "downloaded_images": downloaded,
                "pose_accepted": pose_ok, "measured_images": accepted, "low_tilt_images": low_tilt,
                "notes": ";".join(sorted(set(errors)))[:500],
                "fetch_log_tail": log_tail.replace("\n", " | ")[:800],
            })
            print(f"  page_status={page_status} candidates={len(candidates)} downloaded={downloaded} "
                  f"pose={pose_ok} measured={accepted} low_tilt={low_tilt}")

    m.write_csv(OUTDIR / "control_status.csv", source_rows)
    if all_rows:
        m.write_csv(OUTDIR / "per_image_measurements.csv", all_rows)
    else:
        (OUTDIR / "per_image_measurements.csv").write_text("", encoding="utf-8")

    # Per-control (= per physical watch) median, <=10deg images only, matching
    # the genuine baseline's own pose gate so replica-vs-genuine comparisons
    # are not confounded by different tilt admixtures.
    low = [r for r in all_rows if m.finite(r.get("tilt_deg")) and float(r["tilt_deg"]) <= 10.0]
    by_control = defaultdict(list)
    meta = {}
    for r in low:
        by_control[r["control_id"]].append(r)
        meta[r["control_id"]] = r
    control_rows = []
    for cid, rows in sorted(by_control.items()):
        agg = m.median_dict(rows)
        meta_row = meta[cid]
        cr = {"control_id": cid, "n_images_low_tilt": len(rows),
              "human_label": meta_row["human_label"], "label_strength": meta_row["label_strength"],
              "expected_primary_metric": meta_row["expected_primary_metric"],
              "expected_supporting_metrics": meta_row["expected_supporting_metrics"]}
        cr.update(agg)
        control_rows.append(cr)
    if control_rows:
        m.write_csv(OUTDIR / "per_control_medians.csv", control_rows)
    else:
        (OUTDIR / "per_control_medians.csv").write_text("", encoding="utf-8")

    n_with_any_measurement = sum(1 for r in source_rows if int(r["measured_images"]) > 0)
    n_with_low_tilt = sum(1 for r in source_rows if int(r["low_tilt_images"]) > 0)
    print(f"\ncontrols attempted: {len(controls)}")
    print(f"controls with >=1 measured image: {n_with_any_measurement}")
    print(f"controls with >=1 <=10deg image (usable for genuine-profile comparison): {n_with_low_tilt}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
