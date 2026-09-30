"""Dataset manifest (corpus list format + provenance/quality columns), per-watch table, and the
JSON + Markdown run reports."""
from __future__ import annotations

import csv
import json

import numpy as np
from collections import Counter
from pathlib import Path

from .decide import dataset_summary, priorities, usable_for
from .state import ACCEPT, QUARANTINE, REJECT, State

# The first columns are the corpus list format read by tools/desktop-harness Batch and
# tools/testset/ingest.py; the rest are added, never renamed.
MANIFEST_COLUMNS = [
    "local_path", "class_label", "physical_watch_id", "model", "factory", "source",
    "source_id", "image_url", "adapter", "provider", "discovered_at", "sha256", "dhash", "phash", "dial_phash",
    "width", "height", "duplicate_of", "suitable", "suitability_reasons", "pose", "dial_diameter_px", "sharpness",
    "perspective_axis", "rehaut_top_bottom_ratio", "rehaut_left_right_ratio", "rehaut_confidence",
    "label_confidence", "provenance", "measurement_status", "measurement_file", "dataset_decision", "sample_role",
]
WATCH_COLUMNS = [
    "physical_watch_id", "class_label", "model", "factory", "provenance", "label_confidence", "dataset_decision",
    "decision_reasons", "images", "usable_images", "value_score", "source", "source_title", "label_evidence", "duplicate_of_watch",
    "sample_role",
]
# Raw Batch measurements summarised per watch (the regression harness column names).
WATCH_MEASURES = ("gap", "rot", "sp59", "sp01", "tri_px", "six_centring", "six_rot", "nine_c", "nine_r")
POSE_RANK = {"GOOD": 3, "CORRECTABLE": 2, "UNASSESSABLE": 1}


def _fmt(v) -> str:
    if v is None:
        return ""
    if isinstance(v, float):
        return f"{v:.4g}"
    if isinstance(v, (list, tuple)):
        return ";".join(str(x) for x in v)
    return str(v)


def manifest_rows(state: State) -> list[dict]:
    rows = []
    for s in sorted(state.sources.values(), key=lambda s: (s.physical_watch_id, s.key)):
        for h in s.image_shas:
            im = state.images.get(h)
            if not im:
                continue
            p = im.suitability.get("perspective", {}) if isinstance(im.suitability, dict) else {}
            rows.append({
                "local_path": im.local_path, "class_label": s.class_label, "physical_watch_id": s.physical_watch_id,
                "model": s.model, "factory": s.factory, "source": s.url or s.key, "source_id": s.source_id,
                "image_url": im.image_url, "adapter": s.adapter, "provider": s.provider, "discovered_at": s.discovered_at,
                "sha256": im.sha256, "dhash": im.dhash, "phash": im.phash, "dial_phash": im.dial_phash,
                "width": im.width, "height": im.height, "duplicate_of": im.duplicate_of, "suitable": "yes" if im.suitable else "no",
                "suitability_reasons": im.reasons, "pose": im.quality.get("pose", ""), "dial_diameter_px": im.quality.get("dial_diameter_px"),
                "sharpness": im.quality.get("sharpness"), "perspective_axis": p.get("perspective_axis", ""),
                "rehaut_top_bottom_ratio": p.get("rehaut_top_bottom_ratio"), "rehaut_left_right_ratio": p.get("rehaut_left_right_ratio"),
                "rehaut_confidence": p.get("rehaut_confidence"), "label_confidence": s.label_confidence, "provenance": s.provenance,
                "measurement_status": im.measurement_status, "measurement_file": im.measurement_file, "dataset_decision": s.decision,
                "sample_role": s.sample_role,
            })
    return rows


def write_csv(path: Path, columns: list[str], rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    with tmp.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=columns, extrasaction="ignore")
        w.writeheader()
        for r in rows:
            w.writerow({k: _fmt(r.get(k)) for k in columns})
    tmp.replace(path)


def read_csv(path: Path) -> list[dict]:
    with path.open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def watch_rows(state: State) -> list[dict]:
    by: dict[str, list] = {}
    for s in state.sources.values():
        if s.physical_watch_id and s.decision:
            by.setdefault(s.physical_watch_id, []).append(s)
    rank = {ACCEPT: 3, QUARANTINE: 2, REJECT: 1}
    rows = []
    for wid, srcs in sorted(by.items()):
        best = max(srcs, key=lambda s: rank.get(s.decision, 0))
        shas = {h for s in srcs for h in s.image_shas}
        good = {h for s in srcs for h in s.image_shas if h in state.images and usable_for(state, s, state.images[h])}
        rows.append({
            "physical_watch_id": wid, "class_label": best.class_label, "model": best.model, "factory": best.factory,
            "provenance": best.provenance, "label_confidence": best.label_confidence, "dataset_decision": best.decision,
            "decision_reasons": best.decision_reasons, "images": len(shas),
            "usable_images": len(good),
            "value_score": best.value_score, "source": best.url or best.key, "source_title": best.title,
            "label_evidence": best.label_evidence, "duplicate_of_watch": best.duplicate_of_watch, "sample_role": best.sample_role,
        })
    return rows


def _num(v) -> float | None:
    try:
        x = float(v)
    except (TypeError, ValueError):
        return None
    return x if x == x and abs(x) != float("inf") else None


def watch_measurement_rows(state: State) -> list[dict]:
    """One row per accepted physical watch: the primary (best) image and, over all its usable,
    non-duplicate images, the median of each raw measurement with its within-watch spread (MAD and
    min-max). Downstream statistics should use these rows, one per independent watch, never the
    image rows as if every view were a separate watch."""
    by: dict[str, list] = {}
    for s in state.sources.values():
        if s.decision == "ACCEPT" and s.physical_watch_id:
            by.setdefault(s.physical_watch_id, []).append(s)
    rows = []
    for wid, srcs in sorted(by.items()):
        ims, seen = [], set()
        for s in srcs:
            for h in s.image_shas:
                im = state.images.get(h)
                if im and h not in seen and usable_for(state, s, im):
                    seen.add(h)
                    ims.append(im)
        if not ims:
            continue
        def score(im):
            return (POSE_RANK.get(im.quality.get("pose", ""), 0), im.quality.get("dial_diameter_px") or 0, im.quality.get("sharpness") or 0)
        best = max(ims, key=score)
        s0 = srcs[0]
        row = {"physical_watch_id": wid, "class_label": s0.class_label, "model": s0.model, "factory": s0.factory,
               "sample_role": s0.sample_role, "usable_images": len(ims), "primary_image": best.local_path,
               "primary_sha256": best.sha256, "primary_pose": best.quality.get("pose", ""),
               "primary_dial_diameter_px": best.quality.get("dial_diameter_px")}
        for m in WATCH_MEASURES:
            vals = [v for v in (_num(im.measurement_row.get(m)) for im in ims) if v is not None]
            row[f"{m}_primary"] = _num(best.measurement_row.get(m))
            row[f"{m}_n"] = len(vals)
            if vals:
                med = float(np.median(vals))
                row[f"{m}_median"] = med
                row[f"{m}_mad"] = float(np.median([abs(v - med) for v in vals]))
                row[f"{m}_min"], row[f"{m}_max"] = min(vals), max(vals)
        rows.append(row)
    return rows


def watch_measurement_columns() -> list[str]:
    cols = ["physical_watch_id", "class_label", "model", "factory", "sample_role", "usable_images", "primary_image",
            "primary_sha256", "primary_pose", "primary_dial_diameter_px"]
    for m in WATCH_MEASURES:
        cols += [f"{m}_primary", f"{m}_n", f"{m}_median", f"{m}_mad", f"{m}_min", f"{m}_max"]
    return cols


def build_report(state: State, run: dict, factories: list[str]) -> dict:
    ds = dataset_summary(state)
    pri = priorities(state, factories, top=12)
    rep = dict(run)
    rep["dataset"] = ds
    rep["next_priorities"] = [{"class": c, "model": m, "factory": f, "accepted_watches": n} for c, m, f, n, _ in pri]
    return rep


def markdown(rep: dict) -> str:
    r, ds = rep, rep["dataset"]
    L = [f"# Dataset harvester{' (dry run)' if r.get('dry_run') else ''}", "",
         f"Run {r.get('run_id', '')} · {r.get('started_at', '')} → {r.get('finished_at', '')}", ""]
    L += ["## Adapters", ""] + [f"- **{k}**: {v}" for k, v in r.get("adapters", {}).items()] + [""]
    for name, pq in (r.get("planned_queries") or {}).items():
        L += [f"## Planned {name} queries" + ("" if pq.get("enabled") else " (adapter disabled: these run once its key is set)"), ""]
        L += [f"- `{q}`" for q in pq.get("queries", [])] + [""]
    if r.get("search_results_filtered"):
        L += ["Search results filtered before storing: " + ", ".join(f"{k} {v}" for k, v in sorted(r["search_results_filtered"].items())), ""]
    L += ["## This run", "",
          f"- {r.get('sources_discovered_new', 0)} new sources discovered ({r.get('sources_already_known', 0)} already known)",
          f"- {r.get('sources_examined', 0)} sources examined ({r.get('sources_deferred', 0)} deferred, {r.get('sources_failed', 0)} failed)",
          f"- {r.get('images_downloaded', 0)} images downloaded, {r.get('images_local', 0)} used from local copies",
          f"- {r.get('exact_duplicates', 0)} exact and {r.get('near_duplicates', 0)} near duplicates"
          + (f" ({r.get('possible_duplicates', 0)} possible, not confirmable)" if r.get("possible_duplicates") else ""),
          f"- {r.get('images_rejected', 0)} images rejected, {r.get('images_inconclusive', 0)} inconclusive",
          f"- {r.get('images_measurement_quality', 0)} measurement-quality images",
          f"- {r.get('watches_accepted', 0)} physical watches accepted",
          f"- {r.get('watches_quarantined', 0)} quarantined",
          f"- {r.get('watches_rejected', 0)} rejected", ""]
    if r.get("images_rejected_by_reason"):
        L += ["Images rejected or inconclusive, by reason:", ""] + [f"- {k}: {v}" for k, v in sorted(r["images_rejected_by_reason"].items(), key=lambda x: -x[1])] + [""]
    if r.get("watch_reasons"):
        L += ["Watch decisions, by reason:", ""] + [f"- {k}: {v}" for k, v in sorted(r["watch_reasons"].items(), key=lambda x: -x[1])] + [""]
    if r.get("errors"):
        L += ["Errors / retries:", ""] + [f"- {e}" for e in r["errors"][:40]] + [""]
    L += ["## Dataset now (accepted, independent physical watches)", "",
          f"- Genuine: {ds['by_class_watches'].get('gen', 0)} independent watches",
          f"- Replica: {ds['by_class_watches'].get('rep', 0)} independent watches",
          f"- Usable images in accepted watches: {ds['accepted_usable_images']} (images are views, not independent samples)",
          f"- Quarantined watches: {ds['quarantined_watches']} · rejected watches: {ds['rejected_watches']}",
          f"- Reference-only (not counted as population): {', '.join(ds.get('reference_only_watches') or []) or 'none'}", ""]
    if ds["by_factory_watches"]:
        L += ["Replica watches by factory:", ""] + [f"- {k}: {v}" for k, v in ds["by_factory_watches"].items()] + [""]
    if ds["by_model_watches"]:
        L += ["Watches by class and model:", ""] + [f"- {k}: {v}" for k, v in ds["by_model_watches"].items()] + [""]
    L += ["## Next acquisition priorities", ""] + [
        f"- {p['class']} {p['model']}" + (f" {p['factory']}" if p["class"] == "rep" else "") + f" ({p['accepted_watches']} accepted)"
        for p in rep.get("next_priorities", [])] + [""]
    return "\n".join(L)


def write_reports(reports_dir: Path, rep: dict) -> tuple[Path, Path]:
    reports_dir.mkdir(parents=True, exist_ok=True)
    stem = rep.get("run_id", "run")
    j, m = reports_dir / f"{stem}.json", reports_dir / f"{stem}.md"
    j.write_text(json.dumps(rep, indent=2, sort_keys=True, default=str), encoding="utf-8")
    m.write_text(markdown(rep), encoding="utf-8")
    (reports_dir / "latest.json").write_text(j.read_text(encoding="utf-8"), encoding="utf-8")
    (reports_dir / "latest.md").write_text(m.read_text(encoding="utf-8"), encoding="utf-8")
    return j, m


def reason_counts(state: State, shas: set[str]) -> Counter:
    c = Counter()
    for h in shas:
        im = state.images.get(h)
        if im:
            for r in im.reasons:
                c[r] += 1
    return c
