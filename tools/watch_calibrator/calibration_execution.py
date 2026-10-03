"""Shared post-acquisition measurement/calibration execution.

Live acquisition and offline evidence replay must execute the same code path after evidence has
been fixed. Network discovery/acquisition intentionally lives outside this module. Measurement is
resolved through a fail-closed adapter registry so the generic statistical engine contains no
watch-family routing logic.
"""
from __future__ import annotations

import csv
from pathlib import Path

import measurement_adapters
from production_measure import photo_identities
from calibrate import propose as legacy_propose, finalize as legacy_finalize, save as save_legacy
from genuine_envelope import build as build_genuine_envelope, save as save_genuine_envelope

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]


def execute(config: dict, acquisition_root: Path, split_path: Path, geometry_dir: Path,
            calibration_path: Path, base: Path, measurement_adapter_id: str | None = None) -> dict:
    """Measure fixed evidence and produce both audit and genuine-envelope calibrations.

    measurement_adapter_id is intentionally optional so an immutable snapshot can be replayed
    through a candidate adapter without editing the frozen config that defines the evidence.
    """
    configured = str(config.get("measurement_adapter") or "").strip()
    adapter = measurement_adapters.resolve(measurement_adapter_id or configured)
    measure = adapter.measure

    model = str(config["model"]).strip().upper()
    gd = measure(config, acquisition_root, split_path, geometry_dir, "development", "gen")
    gv = measure(config, acquisition_root, split_path, geometry_dir, "validation", "gen")
    gh = measure(config, acquisition_root, split_path, geometry_dir, "holdout", "gen")
    rd = measure(config, acquisition_root, split_path, geometry_dir, "development", "rep")
    rv = measure(config, acquisition_root, split_path, geometry_dir, "validation", "rep")

    pref = f"{model}_"
    rep_watch = geometry_dir / f"{model}_devval_rep_watch.csv"
    with rep_watch.open("w", newline="", encoding="utf-8") as out:
        writer = None
        for part in ("development", "validation"):
            src = geometry_dir / f"{model}_{part}_rep_watch.csv"
            if not src.exists():
                continue
            with src.open(newline="", encoding="utf-8") as fh:
                for row in csv.DictReader(fh):
                    if writer is None:
                        writer = csv.DictWriter(out, fieldnames=list(row))
                        writer.writeheader()
                    writer.writerow(row)

    legacy = legacy_propose(
        config,
        geometry_dir / f"{pref}development_gen_watch.csv",
        geometry_dir / f"{pref}development_gen_repeatability.csv",
        geometry_dir / f"{pref}validation_gen_watch.csv",
        rep_watch if (rd.get("photos") or rv.get("photos")) else None,
        evidence_root=REPO,
    )
    save_legacy(legacy, base / "frozen_before_holdout.json")
    if legacy.get("state") == "FROZEN_PENDING_HOLDOUT":
        legacy = legacy_finalize(legacy, geometry_dir / f"{pref}holdout_gen_watch.csv")
    save_legacy(legacy, base / "legacy_split_calibration.json")

    gen_photo = {
        part: geometry_dir / f"{pref}{part}_gen_photo.csv"
        for part in ("development", "validation", "holdout")
    }
    gen_repeat = {
        part: geometry_dir / f"{pref}{part}_gen_repeatability.csv"
        for part in ("development", "validation", "holdout")
    }
    rep_photo = {
        part: geometry_dir / f"{pref}{part}_rep_photo.csv"
        for part in ("development", "validation")
    }
    final = build_genuine_envelope(
        config, gen_photo, split_path, gen_repeat, rep_photo,
        photo_identity=photo_identities(acquisition_root),
    )
    save_genuine_envelope(final, calibration_path)

    return {
        "measurement_adapter": adapter.info(),
        "configured_measurement_adapter": configured,
        "development": gd,
        "validation": gv,
        "holdout": gh,
        "replica_measured": {"development": rd, "validation": rv},
        "legacy_split_state": legacy.get("state"),
        "final": final,
    }
