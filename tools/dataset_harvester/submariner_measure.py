#!/usr/bin/env python3
"""Research-only Submariner measurement (issue #34). Replaces the first Python-only harness.

    python3 tools/dataset_harvester/submariner_measure.py --root datasets/submariner_research --out OUT
        [--split docs/research/submariner/split_sub_v1.csv] [--create-split] [--include-holdout]
        [--variants orig,s94,...] [--shards N] [--limit N]

Stages
  1. DATASET  subresearch.dataset: gen/rep labels, physical_watch_id, family, exact + near duplicates,
              cross-watch duplicate quarantine, ACCEPT / QUARANTINE / REJECT per photo and per watch.
  2. SPLIT    subresearch.split: the locked watch-level dev / validation / holdout split. Holdout photos
              are NOT measured unless --include-holdout is given (the holdout stays unseen).
  3. MEASURE  SubMeasure.java (research driver; never GmtHumanQcAnalyzerV2, pose policy, verdict
              thresholds, the no-readable-dial decision or GmtDialLayout) on every ACCEPTED photo of an
              ACCEPTED watch, original plus the perturbation variants.
  4. TABLES   sub_images.csv, sub_landmarks.csv, sub_landmark_stability.csv, summary JSON and Markdown.
Nothing here produces a verdict, a pose label, an authenticity decision or a threshold.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from PIL import Image, ImageOps

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from harvester.config import REPO_ROOT  # noqa: E402
from harvester.harness import Harness, HarnessUnavailable  # noqa: E402
from harvester.quality import pixel_metrics  # noqa: E402
from subresearch import dataset as D  # noqa: E402
from subresearch import measure as MS  # noqa: E402
from subresearch import split as SP  # noqa: E402
from subresearch import tables as TB  # noqa: E402
from subresearch import triangle as TRI  # noqa: E402

DEFAULT_ROOT = REPO_ROOT / "datasets" / "submariner_research"
DEFAULT_SPLIT = REPO_ROOT / "docs" / "research" / "submariner" / "split_sub_v2.csv"


def dataset_stage(root: Path, out: Path) -> tuple[list[dict], list[dict], dict]:
    rows = D.read_csv(root / "acquired_images.csv")
    img_rows, watch_rows = D.build(rows, root)
    cand = root / "candidate_summary.csv"
    if cand.exists():
        watch_rows = D.add_unacquired(watch_rows, D.read_csv(cand))
    acq = {}
    rep = root / "acquisition_report.json"
    if rep.exists():
        acq = json.loads(rep.read_text(encoding="utf-8"))
    return img_rows, watch_rows, acq


def pixels(root: Path, local_path: str, rec: dict) -> dict:
    if rec.get("dial_found") is not True:
        return {}
    try:
        with Image.open(root / local_path) as raw:
            im = ImageOps.exif_transpose(raw).convert("RGB")
        return pixel_metrics(im, {"cx": float(rec["dial_cx"]), "cy": float(rec["dial_cy"]), "radius": float(rec["dial_r"])})
    except Exception:
        return {}


def markdown(s: dict) -> str:
    L = ["# Submariner research measurement", "", s["note"], ""]
    im, pw = s["images"], s["physical_watches"]
    L += ["## Data", "",
          f"- Manifest rows: {im['manifest_rows']}; unique photos (sha256): {im['unique_sha256']}",
          f"- Photo research states: {im['by_research_state']}",
          f"- Photo reasons (not accepted): {im['reasons']}",
          f"- Physical watches: {pw['total']}; states: {pw['by_research_state']}",
          f"- Accepted watches by model/class: {pw['accepted_by_model_class']}",
          f"- Accepted watches by partition: {pw['accepted_by_partition']}",
          f"- Accepted watches with 2+ accepted photos: {pw['accepted_with_2plus_accepted_photos']}",
          f"- Watches not accepted: {pw['not_accepted_reasons']}",
          f"- Measured photos: {im['measured_photos']} (dial located on {im['measured_photos_with_dial']}); variant analyses: {im['variant_analyses']}",
          "", "## Detector coverage (original photo)", ""]
    for k, v in s["detector_coverage_orig"].items():
        L.append(f"- {k}: {v['k']}/{v['n']}" + (f" ({v['rate']:.0%})" if v["rate"] is not None else ""))
    L += ["", "Landmark detected, given a located dial:", ""]
    for k, v in s["landmark_detection_orig_given_dial"].items():
        L.append(f"- {k}: {v['k']}/{v['n']}" + (f" ({v['rate']:.0%})" if v["rate"] is not None else ""))
    L += ["", f"12 triangle fit paths: {s['triangle_fit_paths_orig']}; apex angle (deg) {s['triangle_apex_deg_orig']}", "",
          "## Stability over variants (descriptive, no pass/fail)", "",
          "| kind | photo-landmarks | lost in some variant | centre spread / R median | p95 | rotation spread deg median | p95 |",
          "|---|---|---|---|---|---|---|"]
    for k, v in s["stability_descriptive"].items():
        L.append(f"| {k} | {v['photo_landmarks']} | {v['lost_in_some_variant']} | {v['centre_spread_over_r_median']:.4f} | "
                 f"{v['centre_spread_over_r_p95']:.4f} | {v['rotation_spread_deg_median']:.2f} | {v['rotation_spread_deg_p95']:.2f} |"
                 .replace("nan", "–"))
    return "\n".join(L) + "\n"


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--root", type=Path, default=DEFAULT_ROOT)
    ap.add_argument("--out", type=Path, default=DEFAULT_ROOT / "measurements")
    ap.add_argument("--split", type=Path, default=DEFAULT_SPLIT)
    ap.add_argument("--create-split", action="store_true", help="create the locked split if it does not exist yet")
    ap.add_argument("--extend-from", type=Path, help="with --create-split: new split version = this split + new watches")
    ap.add_argument("--include-holdout", action="store_true")
    ap.add_argument("--variants", default=",".join(MS.ALL_VARIANTS))
    ap.add_argument("--shards", type=int, default=2)
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--dataset-only", action="store_true")
    ap.add_argument("--only-12", action="store_true", help="triangle study: dial + 12 detectors only; perturb only edge-fitted dials")
    ap.add_argument("--partition", action="append", help="measure only these partitions (e.g. development)")
    ap.add_argument("--model", action="append", help="measure only these models (e.g. 124060)")
    ap.add_argument("--class-tag", action="append", help="measure only these class tags (gen / rep)")
    ap.add_argument("--reuse-work", action="store_true", help="rebuild the tables from OUT/work/*.jsonl without re-running the driver")
    a = ap.parse_args(argv)
    out = a.out
    out.mkdir(parents=True, exist_ok=True)

    img_rows, watch_rows, acq = dataset_stage(a.root, out)
    accepted = [w for w in watch_rows if w["research_state"] == D.ACCEPT]
    if a.create_split and not a.split.exists():
        if a.extend_from:
            SP.extend(a.extend_from, accepted, a.split, a.split.stem.replace("split_sub_", ""))
        else:
            SP.create(accepted, a.split)
    parts = SP.assign([w["physical_watch_id"] for w in watch_rows], SP.load(a.split))
    for w in watch_rows:
        w["partition"] = parts[w["physical_watch_id"]] if w["research_state"] == D.ACCEPT else ""
    for r in img_rows:
        r["partition"] = parts.get(r["physical_watch_id"], "")
    D.write_csv(out / "sub_dataset_images.csv", img_rows, D.IMAGE_FIELDS + ["partition"])
    D.write_csv(out / "sub_dataset_watches.csv", watch_rows, D.WATCH_FIELDS + ["partition"])
    if a.dataset_only:
        print(json.dumps({"watches": len(watch_rows), "accepted_watches": len(accepted)}, indent=1))
        return 0

    wstate = {w["physical_watch_id"]: w["research_state"] for w in watch_rows}
    todo = [r for r in img_rows if r["research_state"] == D.ACCEPT and wstate.get(r["physical_watch_id"]) == D.ACCEPT
            and (a.include_holdout or r["partition"] != "holdout")]
    if a.partition:
        todo = [r for r in todo if r["partition"] in set(a.partition)]
    if a.model:
        todo = [r for r in todo if r["model"] in {m.upper() for m in a.model}]
    if a.class_tag:
        todo = [r for r in todo if r["class_label"] in set(a.class_tag)]
    if a.limit:
        todo = todo[:a.limit]
    variants = [v.strip() for v in a.variants.split(",") if v.strip()]
    harness = Harness(shards=a.shards)
    try:
        classes = MS.compile_driver(harness)
    except HarnessUnavailable as e:
        print(f"measurement unavailable: {e}", file=sys.stderr)
        return 2
    iso = MS.isolation_report(classes)
    if iso["forbidden_present"]:
        print(f"refusing to run: forbidden classes compiled into the research driver: {iso['forbidden_present']}", file=sys.stderr)
        return 3
    st = MS.selftest(harness, classes)
    if st != "SELFTEST OK":
        print(f"driver selftest failed: {st}", file=sys.stderr)
        return 4
    jobs = [MS.job_line(str((a.root / r["local_path"]).resolve()), r["sha256"], r["model"], variants) for r in todo]
    if a.reuse_work:
        recs = [json.loads(line) for p in sorted((out / "work").glob("sub_*.jsonl")) for line in p.read_text(encoding="utf-8").splitlines() if line.strip()]
    else:
        recs = MS.run(harness, classes, jobs, out / "work", a.shards, only12=a.only_12)
    meta = {r["sha256"]: r for r in img_rows if r["research_state"] == D.ACCEPT}
    pix = {}
    for rec in recs:
        if rec.get("variant") == "orig" and rec.get("sha256") in meta:
            pix[rec["sha256"]] = pixels(a.root, meta[rec["sha256"]]["local_path"], rec)
    tri_diag = TRI.apply_consensus(recs)
    TB.write(out / "sub_triangle_candidates.csv", TB.candidate_rows(recs, meta), TB.CANDIDATE_COLS)
    irows = TB.image_rows(recs, meta, pix)
    lrows = TB.landmark_rows(recs, meta)
    srows = TB.stability_rows(irows, lrows)
    TB.write(out / "sub_images.csv", irows, TB.IMAGE_COLS)
    TB.write(out / "sub_landmarks.csv", lrows, TB.LANDMARK_COLS)
    TB.write(out / "sub_landmark_stability.csv", srows, TB.STABILITY_COLS)
    summ = TB.summary(watch_rows, img_rows, irows, lrows, srows, acq)
    summ["driver_isolation"] = iso
    summ["triangle_consensus"] = {"photos": len(tri_diag), "with_consensus": sum(1 for d in tri_diag.values() if d.get("consensus")),
                                  "variants_excluded_fallback_dial": sum(d.get("variants_excluded_fallback_dial", 0) for d in tri_diag.values())}
    summ["variants"] = variants
    summ["holdout_measured"] = bool(a.include_holdout)
    (out / "sub_summary.json").write_text(json.dumps(summ, indent=1, default=str) + "\n", encoding="utf-8")
    (out / "sub_summary.md").write_text(markdown(summ), encoding="utf-8")
    print(json.dumps({k: summ[k] for k in ("images", "physical_watches", "detector_coverage_orig")}, indent=1, default=str))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
