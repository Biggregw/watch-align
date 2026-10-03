#!/usr/bin/env python3
"""Fail-closed acceptance checks for a candidate-adapter replay of a live calibration run.

A byte comparison of calibration.json only proves equivalence if both runs are the runs we think
they are. This verifier makes that explicit: the replay must have measured exactly the frozen
evidence bytes of the live run, from the same checkout and config, through the requested candidate
measurement adapter, and the candidate's measurement contract must be non-empty, re-validate
against the frozen photo population and be the only source of the values the engine consumed.

Nothing here is watch-family specific: adapters are resolved from the registry by id, and the
model, family and photo population all come from the verified snapshot.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import sys
import tempfile
from pathlib import Path

import contracts
import evidence_snapshot
import measurement_adapters
import measurement_contract
import production_measure
import run_manifest as manifest_tools

GENUINE_PARTITIONS = ("development", "validation", "holdout")
REPLAY_MODE = "offline_snapshot_replay"


class AcceptanceError(contracts.ContractError):
    """Raised when a replay cannot be accepted as a like-for-like comparison."""


def _json(path: Path, label: str) -> dict:
    if not path.is_file():
        raise AcceptanceError(f"{label} missing: {path}")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise AcceptanceError(f"{label} is not valid JSON: {exc}") from None
    if not isinstance(value, dict):
        raise AcceptanceError(f"{label} must be a JSON object")
    return value


def _sha(path: Path, label: str) -> str:
    if not path.is_file():
        raise AcceptanceError(f"{label} missing: {path}")
    return manifest_tools.sha256_file(path)


def _measured_partitions(status: dict) -> list[tuple[str, str, dict]]:
    """Every partition/class summary the shared calibration execution recorded."""
    out = []
    for part in GENUINE_PARTITIONS:
        out.append((part, "gen", status.get(part)))
    replica = status.get("replica_measured")
    if not isinstance(replica, dict):
        raise AcceptanceError("replay run_status has no replica_measured summaries")
    for part in sorted(replica):
        out.append((part, "rep", replica[part]))
    return out


def verify(live_base: Path, replay_base: Path, live_adapter_id: str, replay_adapter_id: str) -> dict:
    failures: list[str] = []

    def require(condition: bool, message: str) -> None:
        if not condition:
            failures.append(message)

    live_adapter = measurement_adapters.resolve(live_adapter_id).info()
    replay_adapter = measurement_adapters.resolve(replay_adapter_id).info()
    if live_adapter["id"] == replay_adapter["id"]:
        raise AcceptanceError("live and replay adapters are identical; the comparison would be vacuous")
    if replay_adapter.get("contract_schema_version") is None:
        raise AcceptanceError(f"replay adapter {replay_adapter['id']} does not emit a measurement contract")

    lm = _json(live_base / "run_manifest.json", "live run manifest")
    ls = _json(live_base / "run_status.json", "live run status")
    rm = _json(replay_base / "run_manifest.json", "replay run manifest")
    rs = _json(replay_base / "run_status.json", "replay run status")
    # Contract photo identities are workspace path text, so the replay workspace must be addressed
    # exactly as the replay recorded it rather than through an equivalent spelling.
    recorded_base = Path(str(rs.get("calibration") or "")).parent
    if recorded_base != replay_base:
        raise AcceptanceError(f"replay workspace must be passed as recorded by the replay: {recorded_base}")
    source_copy = _json(replay_base / "snapshot_source.json", "replay snapshot copy")

    # Evidence identity: re-verify the live run's frozen snapshot byte-for-byte.
    snap_rel = str((lm.get("evidence_snapshot") or {}).get("path") or "").strip()
    if not snap_rel:
        raise AcceptanceError("live run manifest does not reference an evidence snapshot")
    snapshot_dir = live_base / snap_rel
    snapshot = evidence_snapshot.verify(snapshot_dir)
    snapshot_id = snapshot["snapshot_id"]
    model = contracts.exact_model(snapshot.get("model"), "snapshot model")
    family = str(snapshot.get("family") or "").strip()

    ids = {
        "live run_status": ls.get("snapshot_id"),
        "live manifest": (lm.get("evidence_snapshot") or {}).get("snapshot_id"),
        "replay run_status": rs.get("snapshot_id"),
        "replay manifest replay_snapshot_id": rm.get("replay_snapshot_id"),
        "replay manifest evidence_snapshot": (rm.get("evidence_snapshot") or {}).get("snapshot_id"),
        "replay snapshot_source.json": source_copy.get("snapshot_id"),
    }
    for where, value in ids.items():
        require(value == snapshot_id, f"{where} snapshot_id {value!r} != verified snapshot {snapshot_id}")
    for key in ("acquired_images_sha256", "locked_split_sha256", "unique_image_objects", "unique_image_bytes"):
        live_v = (lm.get("evidence_snapshot") or {}).get(key)
        replay_v = (rm.get("evidence_snapshot") or {}).get(key)
        require(live_v == replay_v, f"evidence_snapshot.{key} differs: live {live_v!r}, replay {replay_v!r}")
    require((lm.get("evidence_manifest") or {}).get("sha256") == snapshot.get("acquired_images_sha256"),
            "live acquired-image manifest is not the one frozen in the snapshot")
    require((rm.get("evidence_manifest") or {}).get("sha256") == snapshot.get("acquired_images_sha256"),
            "replay acquired-image manifest is not the one frozen in the snapshot")
    require(_sha(replay_base / "locked_split.csv", "replay locked split") == snapshot.get("locked_split_sha256"),
            "replay locked split is not the one frozen in the snapshot")

    # Model, family, config and code identity.
    for where, value in (("live manifest", lm.get("requested_model")), ("live run_status", ls.get("model")),
                         ("replay manifest", rm.get("requested_model")), ("replay run_status", rs.get("model"))):
        require(str(value or "").strip().upper() == model, f"{where} model {value!r} != snapshot model {model}")
    for where, value in (("live manifest", lm.get("family")), ("replay manifest", rm.get("family")),
                         ("replay run_status", rs.get("family"))):
        require(str(value or "").strip() == family, f"{where} family {value!r} != snapshot family {family}")
    config_sha = snapshot.get("config_sha256")
    require((lm.get("config") or {}).get("sha256") == config_sha, "live config is not the frozen snapshot config")
    require((rm.get("config") or {}).get("sha256") == config_sha, "replay config is not the frozen snapshot config")
    require(_sha(replay_base / "frozen_config.json", "replay frozen config") == config_sha,
            "replay frozen_config.json bytes differ from the snapshot config")
    config = json.loads((replay_base / "frozen_config.json").read_text(encoding="utf-8"))
    for key in ("checkout_sha", "checkout_tree_sha"):
        live_v = (lm.get("source") or {}).get(key)
        replay_v = (rm.get("source") or {}).get(key)
        require(bool(live_v) and live_v != "unknown" and live_v == replay_v,
                f"source.{key} differs or is unknown: live {live_v!r}, replay {replay_v!r}")

    # Adapter identity: the frozen config is never edited; the override is explicit and effective.
    configured = str(config.get("measurement_adapter") or "").strip()
    require(configured == live_adapter["id"],
            f"frozen config measurement_adapter {configured!r} != expected live adapter {live_adapter['id']}")
    require((lm.get("adapters") or {}).get("measurement_effective") == live_adapter,
            f"live effective adapter {(lm.get('adapters') or {}).get('measurement_effective')!r} != {live_adapter}")
    require(ls.get("measurement_adapter") == live_adapter, "live run_status adapter is not the expected live adapter")
    require(rm.get("mode") == REPLAY_MODE and rs.get("mode") == REPLAY_MODE, "replay is not an offline snapshot replay")
    require(rm.get("requested_measurement_adapter_override") == replay_adapter["id"],
            f"replay override {rm.get('requested_measurement_adapter_override')!r} != {replay_adapter['id']}")
    require((rm.get("adapters") or {}).get("measurement") == configured,
            "replay manifest configured adapter differs from the frozen config")
    require((rm.get("adapters") or {}).get("measurement_effective") == replay_adapter,
            f"replay effective adapter {(rm.get('adapters') or {}).get('measurement_effective')!r} != {replay_adapter}")
    require(rs.get("measurement_adapter") == replay_adapter, "replay run_status adapter is not the candidate adapter")
    require(rs.get("configured_measurement_adapter") == configured,
            "replay run_status configured adapter differs from the frozen config")
    source = rm.get("source") or {}
    expected_fp = manifest_tools.measurement_fingerprint(
        config, str(source.get("checkout_sha") or "unknown"), str(source.get("checkout_tree_sha") or "unknown"),
        replay_adapter,
    )
    replay_fp = (rm.get("versions") or {}).get("measurement_fingerprint")
    live_fp = (lm.get("versions") or {}).get("measurement_fingerprint")
    require(replay_fp == expected_fp, "replay measurement fingerprint is not bound to the candidate adapter")
    require(replay_fp != live_fp, "replay measurement fingerprint was inherited from the live adapter")

    # Exact calibration equivalence.
    live_cal_sha = _sha(live_base / "calibration.json", "live calibration")
    replay_cal_sha = _sha(replay_base / "calibration.json", "replay calibration")
    require(live_cal_sha == replay_cal_sha,
            f"calibration.json SHA-256 differs: live {live_cal_sha}, replay {replay_cal_sha}")
    require(rs.get("calibration_sha256") == replay_cal_sha,
            "replay run_status calibration_sha256 does not match the replay calibration.json")

    # The measured bytes are still exactly the frozen bytes.
    acquired_csv = replay_base / "dataset" / "acquired_images.csv"
    require(_sha(acquired_csv, "replay acquired images") == snapshot.get("acquired_images_sha256"),
            "replay acquired_images.csv differs from the snapshot")
    object_hashes = {str(o.get("sha256") or "").lower() for o in snapshot.get("objects") or []}
    verified_images = 0
    with acquired_csv.open(newline="", encoding="utf-8") as fh:
        for line_no, row in enumerate(csv.DictReader(fh), start=2):
            if (row.get("acquisition_status") or "").strip() != "acquired":
                continue
            expected = (row.get("sha256") or "").strip().lower()
            image = replay_base / "dataset" / (row.get("local_path") or "")
            actual = hashlib.sha256(image.read_bytes()).hexdigest() if image.is_file() else None
            require(expected in object_hashes and actual == expected,
                    f"replay image line {line_no} is not the frozen snapshot object")
            verified_images += 1

    # Contract: present for every measured partition, re-validates against the frozen population,
    # and is the only source of the wide values the statistical engine consumed.
    split_csv = replay_base / "locked_split.csv"
    geometry = replay_base / "geometry"
    totals = {"photos": 0, "metric_rows": 0, "raw_values": 0, "eligible_values": 0}
    partitions = {}
    for part, cls, summary in _measured_partitions(rs):
        label = f"{part}/{cls}"
        if not isinstance(summary, dict):
            failures.append(f"{label}: no measurement summary recorded")
            continue
        require(summary.get("measurement_adapter") == replay_adapter, f"{label}: summary adapter mismatch")
        recorded = summary.get("measurement_contract")
        contract_csv = measurement_contract.output_path(geometry, model, part, cls)
        photos = production_measure.photo_list(replay_base / "dataset", split_csv, part, cls, model)
        try:
            revalidated = measurement_contract.validate(contract_csv, config, replay_adapter, photos)
        except contracts.ContractError as exc:
            failures.append(f"{label}: contract does not re-validate: {exc}")
            continue
        require(recorded == revalidated, f"{label}: recorded contract summary {recorded!r} != re-validated {revalidated!r}")
        wide = geometry / f"{model}_{part}_{cls}_photo.csv"
        with tempfile.TemporaryDirectory() as td:
            expected_wide = Path(td) / "photo.csv"
            measurement_contract.materialize_eligible_wide(contract_csv, config, photos, expected_wide)
            require(wide.is_file() and wide.read_bytes() == expected_wide.read_bytes(),
                    f"{label}: engine input {wide.name} is not exactly the contract's eligible values")
        if cls == "gen":
            require(revalidated["photos"] > 0, f"{label}: genuine measurement contract is empty")
            require(revalidated["eligible_values"] > 0, f"{label}: genuine contract has no eligible values")
        for key in totals:
            totals[key] += int(revalidated[key])
        partitions[label] = revalidated
    require(totals["metric_rows"] > 0 and totals["eligible_values"] > 0, "measurement contract is empty")

    if failures:
        raise AcceptanceError("replay acceptance failed:\n- " + "\n- ".join(failures))
    return {
        "state": "ACCEPTED",
        "model": model,
        "family": family,
        "snapshot_id": snapshot_id,
        "verified_images": verified_images,
        "calibration_sha256": replay_cal_sha,
        "checkout_sha": source.get("checkout_sha"),
        "live_adapter": live_adapter,
        "replay_adapter": replay_adapter,
        "live_measurement_fingerprint": live_fp,
        "replay_measurement_fingerprint": replay_fp,
        "contract_totals": totals,
        "contract_partitions": partitions,
    }


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--live", type=Path, required=True, help="live calibration workspace for one model")
    ap.add_argument("--replay", type=Path, required=True, help="offline replay workspace for the same model")
    ap.add_argument("--live-adapter", required=True)
    ap.add_argument("--replay-adapter", required=True)
    ap.add_argument("--out", type=Path, help="optional JSON report path")
    a = ap.parse_args(argv)
    try:
        report = verify(a.live, a.replay, a.live_adapter, a.replay_adapter)
    except ValueError as exc:  # contract, snapshot and adapter-registry failures
        print(str(exc), file=sys.stderr)
        return 1
    text = json.dumps(report, indent=2, sort_keys=True) + "\n"
    if a.out:
        a.out.write_text(text, encoding="utf-8")
    print(text, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
