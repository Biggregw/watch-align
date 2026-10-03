#!/usr/bin/env python3
"""Replay calibration from an immutable evidence snapshot with no network acquisition.

The snapshot supplies the exact config, acquired-image manifest, locked split and image bytes. The
current checked-out measurement/calibration code is deliberately used so detector or architecture
changes can be evaluated against identical evidence. A candidate measurement adapter may be
selected explicitly without editing the frozen evidence config.
"""
from __future__ import annotations

import argparse
import json
import shutil
from pathlib import Path

import calibration_execution
import contracts
import evidence_snapshot
import run_manifest as manifest_tools

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]


def paths(model: str, root: Path) -> dict[str, Path]:
    base = root / model.upper()
    return {
        "base": base,
        "acq": base / "dataset",
        "split": base / "locked_split.csv",
        "geom": base / "geometry",
        "cal": base / "calibration.json",
        "manifest": base / "run_manifest.json",
        "snapshot_copy": base / "snapshot_source.json",
    }


def replay(snapshot_dir: Path, root: Path, fresh: bool = False,
           measurement_adapter_id: str | None = None) -> dict:
    snapshot = evidence_snapshot.verify(snapshot_dir)
    model = contracts.exact_model(snapshot.get("model"), "snapshot model")
    family = str(snapshot.get("family") or "").strip()
    config_source = snapshot_dir / snapshot["files"]["config"]
    config = json.loads(config_source.read_text(encoding="utf-8"))
    contracts.validate_config(config, model, None)
    if str(config.get("family") or "").strip() != family:
        raise evidence_snapshot.SnapshotError(
            f"snapshot family {family!r} does not match frozen config family {config.get('family')!r}"
        )

    P = paths(model, root)
    if fresh and P["base"].exists():
        shutil.rmtree(P["base"])
    if P["base"].exists() and any(P["base"].iterdir()):
        raise evidence_snapshot.SnapshotError(f"replay output already exists: {P['base']}; use --fresh")
    P["base"].mkdir(parents=True, exist_ok=True)

    # Materialise only verified content-addressed bytes. This function has no discovery/acquisition
    # imports or network fallback.
    evidence_snapshot.materialize(snapshot_dir, P["acq"], P["split"])
    contracts.validate_csv_exact_model(P["acq"] / "acquired_images.csv", model, "replay acquired images")

    manifest = manifest_tools.build(config, config_source, P["base"], REPO, acquired_csv=P["acq"] / "acquired_images.csv")
    manifest_tools.attach_snapshot(manifest, snapshot, snapshot_dir, P["base"])
    manifest["mode"] = "offline_snapshot_replay"
    manifest["replay_snapshot_id"] = snapshot["snapshot_id"]
    manifest["requested_measurement_adapter_override"] = measurement_adapter_id
    manifest_tools.save(manifest, P["manifest"])
    P["snapshot_copy"].write_text(
        json.dumps(snapshot, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )

    execution = calibration_execution.execute(
        config, P["acq"], P["split"], P["geom"], P["cal"], P["base"],
        measurement_adapter_id=measurement_adapter_id,
    )
    final = execution.pop("final")
    manifest_tools.bind_measurement_adapter(manifest, config, execution["measurement_adapter"])
    manifest_tools.save(manifest, P["manifest"])

    calibration_sha = manifest_tools.sha256_file(P["cal"])
    status = {
        "mode": "offline_snapshot_replay",
        "model": model,
        "family": family,
        "snapshot_id": snapshot["snapshot_id"],
        "state": final["state"],
        "calibration": str(P["cal"]),
        "calibration_sha256": calibration_sha,
        "run_manifest": str(P["manifest"]),
        **execution,
    }
    (P["base"] / "run_status.json").write_text(
        json.dumps(status, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    return status


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("snapshot", type=Path, help="immutable evidence_snapshot_v1 directory")
    ap.add_argument("--root", type=Path, default=REPO / "datasets" / "watch_calibrator_replay")
    ap.add_argument("--fresh", action="store_true")
    ap.add_argument("--measurement-adapter", default=None,
                    help="candidate adapter id to evaluate without changing the frozen snapshot config")
    a = ap.parse_args(argv)
    print(json.dumps(replay(a.snapshot, a.root, a.fresh, a.measurement_adapter), indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
