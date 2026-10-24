#!/usr/bin/env python3
"""Dropbox-verified genuine marker variation (RESEARCH ONLY; never changes production or the archive).

    # optional: make the offline cache of the approved selection (Dropbox read-only credentials in the environment)
    python tools/watch_calibrator/dropbox_genuine.py fetch-cache --cache /private/wa-cache
    # measure and report, from Dropbox or from the cache
    python tools/watch_calibrator/dropbox_genuine.py run --dropbox --out research-output/dropbox-genuine-<date>
    python tools/watch_calibrator/dropbox_genuine.py run --cache /private/wa-cache --out research-output/...

Photographs come ONLY from the curated archive's "Suitable Photos" after every gate in dropbox_inputs passes; each is
measured by the production app route (desktop harness GenuineMarkerDump, parity-checked against the app entry point),
one run per explicitly supported app geometry. Results are written to a separate research directory with traceability
to each photograph's SHA-256 and physical-watch identity. Nothing is approved for production calibration.
"""
from __future__ import annotations

import argparse
import csv
from datetime import datetime, timezone
from hashlib import sha256
import json
import math
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
sys.path.insert(0, str(HERE))
import dropbox_inputs as gates  # noqa: E402
import marker_variation as mv  # noqa: E402
from dropbox_source import CacheSource, DropboxSource, DEFAULT_ROOT, cache_approved  # noqa: E402

HARNESS = REPO / "tools" / "desktop-harness" / "run.sh"
PROTECTED = ("android", "calibration", "datasets")
FROZEN_NOTE = ("Production limits are quoted from the app's frozen ClearlyOff.Limits (replica QC defaults and the strict "
               "genuine standard), as the production code applied them to each photo; nothing is changed.")


def output_dir(path, extra_forbidden=()):
    out = Path(path).resolve()
    for name in PROTECTED:
        if out == REPO / name or (REPO / name) in out.parents:
            raise SystemExit(f"refusing to write research output inside {REPO / name}")
    for f in extra_forbidden:
        f = Path(f).resolve()
        if out == f or f in out.parents:
            raise SystemExit("refusing to write research output inside the photograph cache")
    if out.exists() and any(out.iterdir()):
        raise SystemExit(f"{out} is not empty; choose a new research output directory")
    out.mkdir(parents=True, exist_ok=True)
    return out


def harness_measure(model, manifest, image_root, out_csv, parity=True):
    """Run the production per-marker driver for one app geometry, then (default) its parity proof on every photo."""
    env = {**os.environ, "JAVA_TOOL_OPTIONS": os.environ.get("JAVA_TOOL_OPTIONS", "") + f" -Dwatchalign.model={model}"}
    subprocess.run(["bash", str(HARNESS), "GenuineMarkerDump", str(manifest), str(image_root), str(out_csv)],
                   cwd=REPO, check=True, env=env)
    if parity:
        done = subprocess.run(["bash", str(HARNESS), "GenuineMarkerDumpParity", str(manifest), str(image_root),
                               str(out_csv)], cwd=REPO, env=env, capture_output=True, text=True)
        line = next((l for l in done.stdout.splitlines() if l.startswith("PARITY compared=")), "no parity output")
        if done.returncode:
            raise SystemExit(f"{model}: measurement parity failed ({line}); stopping (fail closed)")
        return line
    return "not run"


def measure_all(source, accepted, workdir, measure=harness_measure):
    """Stage each accepted photograph (bytes re-verified), measure per app geometry, return all dump rows."""
    rows, parity = [], {}
    by_model = {}
    for rec in accepted:
        by_model.setdefault(rec["app_model"], []).append(rec)
    for model, recs in sorted(by_model.items()):
        img = Path(workdir) / model
        img.mkdir(parents=True, exist_ok=True)
        lines = ["photo_id,sha256,local_path"]
        for rec in recs:
            data = source.read(rec["archive_path"])
            if sha256(data).hexdigest() != rec["sha256"]:
                raise SystemExit(f"{rec['archive_path']} failed SHA-256 verification; stopping (fail closed)")
            name = rec["sha256"] + Path(rec["archive_path"]).suffix
            (img / name).write_bytes(data)
            lines.append(f"{rec['sha256'][:16]},{rec['sha256']},{name}")
        manifest = Path(workdir) / f"{model}.csv"
        manifest.write_text("\n".join(lines) + "\n", encoding="utf-8")
        out = Path(workdir) / f"{model}_markers.csv"
        parity[model] = measure(model, manifest, img, out)
        with out.open(newline="", encoding="utf-8") as fh:
            rows += list(csv.DictReader(fh))
    return rows, parity


def write_csv(path, rows, fields=None):
    fields = fields or (list(rows[0]) if rows else ["empty"])
    with path.open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=fields, extrasaction="ignore")
        w.writeheader()
        for r in rows:
            w.writerow({k: ("" if isinstance(v, float) and not math.isfinite(v) else
                            f"{v:.6g}" if isinstance(v, float) else v) for k, v in r.items()})


def clean(o):
    if isinstance(o, float):
        return o if math.isfinite(o) else None
    if isinstance(o, dict):
        return {k: clean(v) for k, v in o.items()}
    if isinstance(o, list):
        return [clean(v) for v in o]
    return o


def g(x, nd=3):
    return "–" if not isinstance(x, (int, float)) or (isinstance(x, float) and not math.isfinite(x)) else (
        str(x) if isinstance(x, int) else f"{x:.{nd}f}")


def report(audit, accepted, rejected, result, meta):
    from collections import Counter
    L = ["# Dropbox-verified genuine marker variation — research report", "",
         "RESEARCH ONLY. Production measurement, reference geometry, tolerances, classifications, marker eligibility "
         "and the Android app are unchanged. Photographic suitability is not evidence of authenticity; authenticity "
         "comes only from the source and review index. Nothing here is approved for production calibration.", "",
         f"Source: {meta['source']['mode']} · index `{audit['index_path']}` (SHA-256 {audit['index_sha256'][:16]}…) · "
         f"selections applied: {', '.join(audit['selections_applied']) or 'none'} · harness parity: "
         f"{meta.get('parity', 'run GenuineMarkerDumpParity separately')}", "",
         "## 1. Input audit", "",
         f"Photographs in Suitable Photos: {audit['photos_in_suitable_folders']} · accepted: {len(accepted)} "
         f"({audit['accepted_watches']} independent physical watches) · rejected: {len(rejected)}", "",
         "| Reference | Accepted photos | Independent watches | Dealers | Provenance |", "|---|---:|---:|---|---|"]
    by_ref = {}
    for a in accepted:
        by_ref.setdefault(a["reference"], []).append(a)
    for ref, recs in sorted(by_ref.items()):
        L.append(f"| {ref} | {len(recs)} | {len({r['physical_watch_id'] for r in recs})} | "
                 f"{', '.join(f'{k} ({v})' for k, v in Counter(r['dealer'] for r in recs).most_common())} | "
                 f"{', '.join(sorted({r['provenance'] for r in recs}))} |")
    L += ["", "Rejections by reason:", ""]
    for why, n in Counter(r["reason"] for r in rejected).most_common():
        L.append(f"- {n} × {why}")
    L += ["", "## 2. Individual markers measured and withheld", "",
          "| Reference | Hour | Shape | Credible photos | Withheld | Withheld because |", "|---|---:|---|---:|---:|---|"]
    for m in result["markers"]:
        L.append(f"| {m['reference']} | {m['hour']} | {m['shape']} | {m['credible_photos']} | {m['withheld_photos']} | "
                 f"{m['withheld_reasons'] or '–'} |")
    L += ["", "Per-photo, per-marker readings: `marker_measurements.csv`.", "",
          "## 3. Genuine watch-to-watch distributions (unit: independent physical watch)", "",
          "Each watch is represented by the median of its photographs. Ranges are given only from 3 or more watches; "
          "p5/p95 below about 20 watches are interpolated extremes, not stable percentiles. Unusual genuine readings "
          "are flagged and kept.", "",
          "| Ref | Hour | Shape | Dimension | Watches | Median | MAD | Min | Max | p5 | p95 | Median 95 % CI | "
          "Unusual (kept) | Evidence |", "|---|---:|---|---|---:|---:|---:|---:|---:|---:|---:|---|---:|---|"]
    for d in result["distributions"]:
        ci = (f"{g(d['median_ci95_low'])} – {g(d['median_ci95_high'])}"
              if math.isfinite(d["median_ci95_low"]) else "–")
        L.append(f"| {d['reference']} | {d['hour']} | {d['shape']} | {d['dimension']} | {d['independent_watches']} | "
                 f"{g(d['median'])} | {g(d['mad'])} | {g(d['min'])} | {g(d['max'])} | {g(d['p5'])} | {g(d['p95'])} | "
                 f"{ci} | {d['unusual_watches_kept']} | {d['evidence']} |")
    L += ["", "## 4. Within-watch photographic repeatability", "",
          "| Ref | Hour | Dimension | Multi-photo watches | Within-watch SD (photographic) | Between-watch SD | "
          "Estimated physical SD | Median reported uncertainty |", "|---|---:|---|---:|---:|---:|---:|---:|"]
    for d in result["distributions"]:
        L.append(f"| {d['reference']} | {d['hour']} | {d['dimension']} | {d['multi_photo_watches']} | "
                 f"{g(d['within_watch_sd'])} | {g(d['between_watch_sd'])} | {g(d['estimated_physical_sd'])} | "
                 f"{g(d['median_reported_uncertainty'])} |")
    L += ["", "Per-watch detail: `within_watch_repeatability.csv`.", "",
          "## 5. Uncertainty and evidence limitations", "",
          "- Estimated physical SD = sqrt(max(0, variance of watch means − within-watch variance / harmonic mean photos "
          "per watch)); with few multi-photo watches it is an estimate, not a measurement.",
          "- Photographs of one watch are not independent; watches from one dealer share photography conditions.",
          "- Desktop decoding uses OpenCV with the app's resampling rule; Android's decoder can differ by sub-pixel "
          "amounts (not measured here).",
          "- Readings are only those production treats as credible; withheld markers are listed in section 2.",
          "", "## 6. Comparison with the frozen production calibration (unchanged)", "", FROZEN_NOTE, "",
          "| Ref | Hour | Dimension | Watches | Replica-QC limit | Watches outside | Strict genuine limit | "
          "Watches outside |", "|---|---:|---|---:|---:|---:|---:|---:|"]
    for c in result["comparison"]:
        L.append(f"| {c['reference']} | {c['hour']} | {c['dimension']} | {c['independent_watches']} | "
                 f"{g(c['replica_qc_limit'])} | {c['replica_qc_watches_outside']} | {g(c['strict_genuine_limit'])} | "
                 f"{c['strict_genuine_watches_outside']} |")
    L += ["", "## 7. Recommendations", ""]
    ready = [d for d in result["distributions"] if d["evidence"].startswith("sufficient")]
    prelim = [d for d in result["distributions"] if d["evidence"].startswith("preliminary")]
    diag = [d for d in result["distributions"] if d["evidence"].startswith("measurement-quality")]
    L.append(f"- {len(ready)} marker dimensions have enough independent genuine evidence (≥ 30 watches, ≥ 5 with repeat "
             "photographs, ≥ 2 dealers) to support a future, owner-approved calibration decision.")
    L.append(f"- {len(prelim)} are preliminary (10–29 watches, too few repeat photographs, or a single dealer); "
             f"{len(diag)} are measurement-quality or photo-level diagnostics; the rest are descriptive or insufficient.")
    for d in prelim:
        if "one dealer" in d["evidence"]:
            L.append("- Single-dealer evidence: add genuine watches from other dealers before relying on these ranges.")
            break
    L.append("- No tolerance should be derived from dimensions marked descriptive or insufficient; add independent "
             "genuine watches (not more photographs of the same watches) first.")
    return "\n".join(L) + "\n"


def run(args):
    source = CacheSource(args.cache) if args.cache else DropboxSource(args.root)
    out = output_dir(args.out, [args.cache] if args.cache else [])
    cmap = json.loads(Path(args.column_map).read_text()) if args.column_map else None
    accepted, rejected, audit, _ = gates.select_inputs(source, args.index, cmap)
    measure = (lambda *x: harness_measure(*x, parity=False)) if args.skip_parity else harness_measure
    with tempfile.TemporaryDirectory(prefix="wa-dropbox-research-") as work:
        rows, parity = measure_all(source, accepted, work, measure) if accepted else ([], {})
    photos = {a["sha256"]: a for a in accepted}
    result = mv.analyse(rows, photos)
    meta = {"schema": "watch-align-dropbox-genuine-research/1", "created": datetime.now(timezone.utc).isoformat(),
            "source": source.describe(), "git_commit": subprocess.run(["git", "rev-parse", "HEAD"], cwd=REPO,
                                                                      capture_output=True, text=True).stdout.strip(),
            "production_calibration_approved": False, "input_audit": audit,
            "parity": "; ".join(f"{m}: {v}" for m, v in sorted(parity.items())) or "no photographs measured"}
    for r in rows:
        p = photos.get(r.get("sha256"), {})
        r.update({k: p.get(k, "") for k in ("reference", "dial_generation", "physical_watch_id", "dealer",
                                            "provenance", "archive_path", "selection_id")})
    write_csv(out / "input_accepted.csv", accepted)
    write_csv(out / "input_rejected.csv", rejected)
    write_csv(out / "marker_measurements.csv", rows)
    write_csv(out / "marker_withheld_summary.csv", result["markers"])
    write_csv(out / "genuine_distributions.csv", result["distributions"])
    write_csv(out / "watch_representatives.csv", result["representatives"])
    write_csv(out / "within_watch_repeatability.csv", result["repeatability"])
    write_csv(out / "production_comparison.csv", result["comparison"])
    (out / "results.json").write_text(json.dumps(clean({**meta, **result}), indent=1) + "\n", encoding="utf-8")
    (out / "REPORT.md").write_text(report(audit, accepted, rejected, result, meta), encoding="utf-8")
    print(json.dumps({"out": str(out), **audit}, indent=1))
    return out


def fetch_cache(args):
    source = DropboxSource(args.root)
    cmap = json.loads(Path(args.column_map).read_text()) if args.column_map else None
    accepted, rejected, audit, index_raw = gates.select_inputs(source, args.index, cmap)
    support = {args.index: index_raw}
    for sid, is_file, _ in source.list(gates.AUDIT):
        if not is_file:
            for name, f, _ in source.list(f"{gates.AUDIT}/{sid}"):
                if f and name.endswith(".json"):
                    support[f"{gates.AUDIT}/{sid}/{name}"] = source.read(f"{gates.AUDIT}/{sid}/{name}")
    print(json.dumps({**cache_approved(source, accepted, support, args.cache), **audit}, indent=1))


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    for name in ("run", "fetch-cache"):
        p = sub.add_parser(name)
        p.add_argument("--root", default=DEFAULT_ROOT, help="Dropbox archive root (read-only)")
        p.add_argument("--index", default=gates.DEFAULT_INDEX, help="source and review index, relative to the root")
        p.add_argument("--column-map", help="JSON {logical field: index column} when the index uses other headers")
    r = sub.choices["run"]
    src = r.add_mutually_exclusive_group(required=True)
    src.add_argument("--dropbox", action="store_true", help="read the archive from Dropbox (read-only)")
    src.add_argument("--cache", help="offline cache made by fetch-cache")
    r.add_argument("--out", required=True, help="new, separate research output directory")
    r.add_argument("--skip-parity", action="store_true", help="skip the per-photo production parity re-measurement")
    sub.choices["fetch-cache"].add_argument("--cache", required=True)
    a = ap.parse_args(argv)
    return run(a) if a.cmd == "run" else fetch_cache(a)


if __name__ == "__main__":
    main()
