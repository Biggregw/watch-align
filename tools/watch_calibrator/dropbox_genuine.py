#!/usr/bin/env python3
"""Dropbox-verified genuine-candidate marker variation (RESEARCH ONLY; never changes production or the archive).

    # optional: make the offline cache of the approved selection (Dropbox read-only credentials in the environment)
    python tools/watch_calibrator/dropbox_genuine.py fetch-cache --cache /private/wa-cache
    # measure and report, from Dropbox or from the cache
    python tools/watch_calibrator/dropbox_genuine.py run --dropbox --out research-output/dropbox-genuine-<date> \
        --expect tools/watch_calibrator/snapshots/dropbox_index_20261024.json
    python tools/watch_calibrator/dropbox_genuine.py run --cache /private/wa-cache --out research-output/... --expect ...

The measured population is the index's provenance_status "dealer_attributed_genuine_candidate": dealer-attributed
genuine CANDIDATES, not independently authenticated watches. That designation is carried verbatim into every output.

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
FROZEN_NOTE = ("Each count is the production code's OWN decision for that photograph: its own reading against its own "
               "frozen limit (ClearlyOff.Limits replica-QC defaults or the strict genuine standard), including that "
               "photograph's resolution allowance. Limits are quoted as the range over photographs; nothing is "
               "re-derived or changed. Watch counts are derived only from those photo decisions (a watch with at least "
               "one photograph outside) and are not a watch-level threshold test.")


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
    D = gates.CANDIDATE
    L = [f"# Marker variation of {D} watches (Dropbox archive) — research report", "",
         "RESEARCH ONLY. Production measurement, reference geometry, tolerances, classifications, marker eligibility "
         "and the Android app are unchanged. Nothing here is approved for production calibration.", "",
         f"**Population: provenance_status = `{D}`** — watches attributed as genuine by the dealer, NOT independently "
         "authenticated. Photographic suitability is not evidence of authenticity. Every 'watch' below is such a "
         "candidate.", "",
         f"Source: {meta['source']['mode']} · index `{audit['index_path']}` (SHA-256 {audit['index_sha256']}) · "
         f"{audit['index_rows']} rows · selections applied: {', '.join(audit['selections_applied']) or 'none'} · "
         f"snapshot check: {meta.get('snapshot', 'not requested')} · parity: {meta.get('parity', 'not run')}", "",
         "## 1. Input audit", "",
         f"In the Suitable Photos folders: {audit['in_suitable_folders']['photos']} photographs, "
         f"{audit['in_suitable_folders']['watches']} watches · index initial_photo_suitability = suitable: "
         f"{audit['suitable_in_index']['photos']} · accepted: {audit['accepted']['photos']} photographs "
         f"({audit['accepted']['watches']} independent physical watches) · rejected: {len(rejected)}", "",
         "| specific_model | In Suitable Photos (photos / watches) | Accepted (photos / watches) | Dealers (accepted) |",
         "|---|---:|---:|---|"]
    by_ref = {}
    for a in accepted:
        by_ref.setdefault(a["reference"], []).append(a)
    refs = sorted(set(audit["in_suitable_folders"]["by_reference"]) | set(audit["accepted"]["by_reference"]))
    for ref in refs:
        si = audit["in_suitable_folders"]["by_reference"].get(ref, {"photos": 0, "watches": 0})
        ac = audit["accepted"]["by_reference"].get(ref, {"photos": 0, "watches": 0})
        dealers = Counter(r["dealer"] for r in by_ref.get(ref, []))
        L.append(f"| {ref} | {si['photos']} / {si['watches']} | {ac['photos']} / {ac['watches']} | "
                 f"{', '.join(f'{k} ({v})' for k, v in dealers.most_common()) or '–'} |")
    L += ["", "Rejections by reason:", ""]
    for why, n in Counter(r["reason"] for r in rejected).most_common():
        L.append(f"- {n} × {why}")
    L += ["", "### Unsupported or unspecific references (reported, not measured, not pooled)", "",
          "| SHA-256 | specific_model | physical_watch_id | Actual folder | Gate result |", "|---|---|---|---|---|"]
    for r in audit["unsupported_references"]:
        L.append(f"| {r['sha256'][:16]}… | {r['specific_model'] or '(blank)'} | {r['physical_watch_id'] or '–'} | "
                 f"{r['actual_paths'].rsplit('/', 1)[0] or '–'} | {r['gate_result']} |")
    if not audit["unsupported_references"]:
        L.append("| – | none | | | |")
    L += ["", "## 2. Individual markers measured and withheld", "",
          "| Reference | Hour | Shape | Credible photos | Withheld | Withheld because |", "|---|---:|---|---:|---:|---|"]
    for m in result["markers"]:
        L.append(f"| {m['reference']} | {m['hour']} | {m['shape']} | {m['credible_photos']} | {m['withheld_photos']} | "
                 f"{m['withheld_reasons'] or '–'} |")
    L += ["", "Per-photo, per-marker readings: `marker_measurements.csv`.", "",
          f"## 3. Watch-to-watch distributions of {gates.CANDIDATE} watches (unit: independent physical watch)", "",
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
          "", "## 6. Frozen production decisions on these photographs (unchanged)", "", FROZEN_NOTE, "",
          "| Ref | Hour | Dimension | Replica QC: photos outside / evaluated | limit range | watches with a photo "
          "outside / watches | Strict genuine: photos outside / evaluated | limit range | watches with a photo "
          "outside / watches | Re-derived disagreements |", "|---|---:|---|---:|---|---:|---:|---|---:|---:|"]
    for c in result["comparison"]:
        def cell(p):
            if c[f"{p}_photos_evaluated"] == "":
                return "–", "–", "–"
            return (f"{c[f'{p}_photos_outside']} / {c[f'{p}_photos_evaluated']}",
                    f"{g(c[f'{p}_limit_min'])}–{g(c[f'{p}_limit_max'])}",
                    f"{c[f'{p}_watches_with_a_photo_outside']} / {c[f'{p}_watches_evaluated']}")
        q, st = cell("replica_qc"), cell("strict_genuine")
        dis = [c[f"{p}_recomputed_disagreements"] for p in ("replica_qc", "strict_genuine")
               if c[f"{p}_recomputed_disagreements"] != ""]
        L.append(f"| {c['reference']} | {c['hour']} | {c['dimension']} | {q[0]} | {q[1]} | {q[2]} | {st[0]} | {st[1]} | "
                 f"{st[2]} | {sum(dis)} |")
    L += ["", "## 7. Recommendations", ""]
    ready = [d for d in result["distributions"] if d["evidence"].startswith("sufficient")]
    prelim = [d for d in result["distributions"] if d["evidence"].startswith("preliminary")]
    diag = [d for d in result["distributions"] if d["evidence"].startswith("measurement-quality")]
    L.append(f"- {len(ready)} marker dimensions have enough independent genuine-candidate evidence (≥ 30 watches, ≥ 5 with repeat "
             "photographs, ≥ 2 dealers) to support a future, owner-approved calibration decision.")
    L.append(f"- {len(prelim)} are preliminary (10–29 watches, too few repeat photographs, or a single dealer); "
             f"{len(diag)} are measurement-quality or photo-level diagnostics; the rest are descriptive or insufficient.")
    for d in prelim:
        if "one dealer" in d["evidence"]:
            L.append("- Single-dealer evidence: add genuine-candidate watches from other dealers before relying on these ranges.")
            break
    L.append("- No tolerance should be derived from dimensions marked descriptive or insufficient; add independent "
             "genuine-candidate watches (not more photographs of the same watches) first.")
    return "\n".join(L) + "\n"


def run(args):
    source = CacheSource(args.cache) if args.cache else DropboxSource(args.root)
    out = output_dir(args.out, [args.cache] if args.cache else [])
    accepted, rejected, audit, _ = gates.select_inputs(source, args.index)
    write_csv(out / "input_accepted.csv", accepted)
    write_csv(out / "input_rejected.csv", rejected)
    (out / "input_audit.json").write_text(json.dumps(audit, indent=1) + "\n", encoding="utf-8")
    snapshot = "not requested"
    if args.expect:
        problems = gates.check_snapshot(audit, json.loads(Path(args.expect).read_text()))
        if problems:
            (out / "FAILED.md").write_text("# Run stopped: the eligible population differs from the source snapshot\n\n"
                                           + "\n".join(f"- {p}" for p in problems) + "\n", encoding="utf-8")
            raise SystemExit("eligible population differs from the source snapshot: " + "; ".join(problems))
        snapshot = f"matches {Path(args.expect).name}"
    if not accepted:
        (out / "FAILED.md").write_text("# Run stopped: no photograph passed the gates\n\nSee input_rejected.csv.\n",
                                       encoding="utf-8")
        raise SystemExit("no photograph passed the verification gates; nothing measured (see input_rejected.csv)")
    measure = (lambda *x: harness_measure(*x, parity=False)) if args.skip_parity else harness_measure
    with tempfile.TemporaryDirectory(prefix="wa-dropbox-research-") as work:
        rows, parity = measure_all(source, accepted, work, measure) if accepted else ([], {})
    photos = {a["sha256"]: a for a in accepted}
    result = mv.analyse(rows, photos)
    meta = {"schema": "watch-align-dropbox-genuine-research/1", "created": datetime.now(timezone.utc).isoformat(),
            "source": source.describe(), "git_commit": subprocess.run(["git", "rev-parse", "HEAD"], cwd=REPO,
                                                                      capture_output=True, text=True).stdout.strip(),
            "production_calibration_approved": False, "input_audit": audit,
            "snapshot": snapshot, "provenance_designation": gates.CANDIDATE,
            "parity": "; ".join(f"{m}: {v}" for m, v in sorted(parity.items())) or "no photographs measured"}
    for r in rows:
        p = photos.get(r.get("sha256"), {})
        r.update({k: p.get(k, "") for k in ("reference", "physical_watch_id", "provenance_status", "dealer",
                                            "archive_path", "selection_id")})
    write_csv(out / "marker_measurements.csv", rows)
    write_csv(out / "marker_withheld_summary.csv", result["markers"])
    write_csv(out / "marker_distributions.csv", result["distributions"])
    write_csv(out / "watch_representatives.csv", result["representatives"])
    write_csv(out / "within_watch_repeatability.csv", result["repeatability"])
    write_csv(out / "production_comparison.csv", result["comparison"])
    (out / "results.json").write_text(json.dumps(clean({**meta, **result}), indent=1) + "\n", encoding="utf-8")
    (out / "REPORT.md").write_text(report(audit, accepted, rejected, result, meta), encoding="utf-8")
    print(json.dumps({"out": str(out), **audit}, indent=1))
    return out


def fetch_cache(args):
    source = DropboxSource(args.root)
    accepted, rejected, audit, index_raw = gates.select_inputs(source, args.index)
    if not accepted:
        raise SystemExit("no photograph passed the verification gates; cache not written")
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
    r = sub.choices["run"]
    src = r.add_mutually_exclusive_group(required=True)
    src.add_argument("--dropbox", action="store_true", help="read the archive from Dropbox (read-only)")
    src.add_argument("--cache", help="offline cache made by fetch-cache")
    r.add_argument("--out", required=True, help="new, separate research output directory")
    r.add_argument("--expect", help="source snapshot JSON: the run stops unless the eligible population matches it")
    r.add_argument("--skip-parity", action="store_true", help="skip the per-photo production parity re-measurement")
    sub.choices["fetch-cache"].add_argument("--cache", required=True)
    a = ap.parse_args(argv)
    return run(a) if a.cmd == "run" else fetch_cache(a)


if __name__ == "__main__":
    main()
