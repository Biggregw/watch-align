#!/usr/bin/env python3
"""Build Phase 3 calibration outcomes and coverage reports from validated v2 sidecars.

This runs after v2 sidecar measurement has already completed. It does not measure images, change
calibration input, change calibration.json, or make any product decision. It validates the v2
contracts against the same frozen evidence, writes engine-owned accepted-measurement outcomes, and
then writes the reconciled coverage/sufficiency report.
"""
from __future__ import annotations

import argparse
import csv
import json
import os
from pathlib import Path

import calibration_outcome_v1
import catalogue_registry
import contracts
import coverage_funnel_v1
import evidence_snapshot
import measurement_contract_v2
import submariner12_measurement_adapter_v2

GROUPS = calibration_outcome_v1.DEFAULT_GROUPS


class Phase3ReportingError(contracts.ContractError):
    """Raised when Phase 3 reporting cannot be built from proven sidecar evidence."""


def _rows(path: Path) -> list[dict]:
    if not path.is_file():
        raise Phase3ReportingError(f"missing required CSV {path}")
    with path.open(newline="", encoding="utf-8") as fh:
        return list(csv.DictReader(fh))


def _catalogues() -> tuple[dict, list[dict]]:
    metric = catalogue_registry.load_metric_catalogue(
        catalogue_registry.SUB12_124060_METRIC_CATALOGUE,
        expected_family=submariner12_measurement_adapter_v2.FAMILY,
        expected_model=submariner12_measurement_adapter_v2.MODEL,
    )
    core = catalogue_registry.load_reason_catalogue(
        catalogue_registry.CORE_REASON_CATALOGUE, expected_namespace="core"
    )
    sub12 = catalogue_registry.load_reason_catalogue(
        catalogue_registry.SUB12_REASON_CATALOGUE, expected_namespace="sub12"
    )
    return metric, [core, sub12]


def _v2_path(geometry: Path, model: str, partition: str, cls: str) -> Path:
    return geometry / f"{model}_{partition}_{cls}_measurement_contract_v2.jsonl"


def load_validated_contracts(
    config: dict,
    snapshot_id: str,
    acq_root: Path,
    split_csv: Path,
    geometry: Path,
    *,
    checkout_sha: str | None = None,
) -> dict[tuple[str, str], dict]:
    model = contracts.exact_model(config.get("model"), "Phase 3 model")
    family = str(config.get("family") or "").strip()
    if model != submariner12_measurement_adapter_v2.MODEL or family != submariner12_measurement_adapter_v2.FAMILY:
        raise Phase3ReportingError(
            f"Phase 3 reporting currently supports only "
            f"{submariner12_measurement_adapter_v2.MODEL}/{submariner12_measurement_adapter_v2.FAMILY}"
        )
    metric_catalogue, reason_catalogues = _catalogues()
    fingerprint = submariner12_measurement_adapter_v2.measurement_fingerprint(checkout_sha)
    validated = {}
    for partition, cls in GROUPS:
        requested = submariner12_measurement_adapter_v2.requested_photos(
            acq_root, split_csv, partition, cls, model
        )
        path = _v2_path(geometry, model, partition, cls)
        if not path.is_file():
            raise Phase3ReportingError(f"missing v2 sidecar contract {path}")
        expected_identity = {
            "snapshot_id": snapshot_id,
            "claimed_model": model,
            "family": family,
            "partition": partition,
            "class_label": cls,
            "adapter": submariner12_measurement_adapter_v2.ADAPTER,
            "reliability_policy": submariner12_measurement_adapter_v2.RELIABILITY_POLICY,
            "measurement_fingerprint": fingerprint,
        }
        validated[(partition, cls)] = measurement_contract_v2.validate_file(
            path,
            metric_catalogue=metric_catalogue,
            reason_catalogues=reason_catalogues,
            expected_identity=expected_identity,
            expected_photos=requested,
        )
    return validated


def build(
    snapshot_dir: Path,
    replay_root: Path,
    *,
    candidate_summary: Path | None = None,
    checkout_sha: str | None = None,
) -> dict:
    snapshot = evidence_snapshot.verify(snapshot_dir)
    model = contracts.exact_model(snapshot.get("model"), "Phase 3 snapshot model")
    config = json.loads((snapshot_dir / snapshot["files"]["config"]).read_text(encoding="utf-8"))
    contracts.validate_config(config, model, None)

    base = replay_root / model
    acq_root = base / "dataset"
    split_csv = base / "locked_split.csv"
    geometry = base / "geometry"
    calibration_path = base / "calibration.json"
    for required in (acq_root / "acquired_images.csv", split_csv, geometry, calibration_path):
        if not required.exists():
            raise Phase3ReportingError(f"Phase 3 requires completed v1/v2 replay evidence: missing {required}")

    validated = load_validated_contracts(
        config,
        snapshot["snapshot_id"],
        acq_root,
        split_csv,
        geometry,
        checkout_sha=checkout_sha,
    )
    calibration = json.loads(calibration_path.read_text(encoding="utf-8"))
    calibration_sha = calibration_outcome_v1.calibration_file_sha256(calibration_path)

    outcome_records = calibration_outcome_v1.build_records(
        validated,
        calibration,
        calibration_sha256=calibration_sha,
    )
    outcome_path = geometry / "calibration_outcome_v1.jsonl"
    calibration_outcome_v1.write_records(outcome_path, outcome_records)
    if calibration_outcome_v1.read_canonical(outcome_path) != outcome_records:
        raise Phase3ReportingError("calibration outcome canonical round-trip changed records")

    acquired_rows = _rows(acq_root / "acquired_images.csv")
    candidate_rows = _rows(candidate_summary) if candidate_summary is not None else None
    report = coverage_funnel_v1.build_report(
        validated,
        outcome_records,
        calibration,
        config,
        acquired_rows=acquired_rows,
        candidate_summary_rows=candidate_rows,
    )
    report_path = geometry / "coverage_funnel_v1.json"
    coverage_funnel_v1.write_report(report_path, report)
    if coverage_funnel_v1.read_canonical(report_path) != report:
        raise Phase3ReportingError("coverage report canonical round-trip changed content")

    return {
        "schema": "watch_align.phase3_reporting",
        "version": 1,
        "status": "PASS",
        "snapshot_id": snapshot["snapshot_id"],
        "model": model,
        "measurement_fingerprint": report["measurement_fingerprint"],
        "calibration_sha256": calibration_sha,
        "calibration_outcomes": len(outcome_records) - 1,
        "metrics": len(report["metrics"]),
        "stage0_acquisition_ledger_available": report["acquisition"]["stage0_acquisition_ledger_available"],
        "outcome_path": str(outcome_path),
        "coverage_path": str(report_path),
    }


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("snapshot", type=Path, help="immutable evidence_snapshot_v1 directory")
    ap.add_argument("--replay-root", type=Path, default=Path("datasets/watch_calibrator_replay"))
    ap.add_argument("--candidate-summary", type=Path, default=None)
    args = ap.parse_args(argv)
    checkout_sha = (os.environ.get("WATCH_ALIGN_HEAD_SHA") or "").strip() or None
    summary = build(
        args.snapshot,
        args.replay_root,
        candidate_summary=args.candidate_summary,
        checkout_sha=checkout_sha,
    )
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
