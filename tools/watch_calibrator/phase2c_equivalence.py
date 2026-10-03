#!/usr/bin/env python3
"""Frozen-evidence v1/v2 measurement-contract equivalence proof.

Phase 2C deliberately leaves the calibration engine on the proven v1 adapter. It runs the v2
serializer beside the already-completed v1 replay, then compares every requested photo/metric.
The proof is fail-closed:

* the accepted set must be identical;
* every accepted eligible value must be identical at the six-decimal measurement boundary;
* every raw value that exists in v1 must exist with the same value in v2;
* the only permitted state change is twelve.* unavailable -> withheld when v2 exposes the same
  intended pre-reliability triangle quantity that v1 could not represent;
* every non-accepted v1 row must land on a registered, contract-valid v2 reason code;
* canonical v2 identity, photo population and metric population must match the same frozen evidence.

No v2 value is fed into calibration here. This is migration proof only.
"""
from __future__ import annotations

import argparse
import csv
import json
import math
import os
from collections import Counter, defaultdict
from decimal import Decimal, InvalidOperation
from pathlib import Path

import catalogue_registry
import contracts
import evidence_snapshot
import measurement_contract
import measurement_contract_v2
import submariner12_measurement_adapter
import submariner12_measurement_adapter_v2

GROUPS = (
    ("development", "gen"),
    ("validation", "gen"),
    ("holdout", "gen"),
    ("development", "rep"),
    ("validation", "rep"),
)
TWELVE_METRICS = {
    "twelve.rotation_deg",
    "twelve.gap_r",
    "twelve.centring_w",
}


class Phase2CError(contracts.ContractError):
    """Raised when frozen v1/v2 measurement evidence is not migration-equivalent."""


def _v1_number(value: object, where: str) -> Decimal | None:
    text = str(value or "").strip()
    if not text:
        return None
    try:
        out = Decimal(text)
    except InvalidOperation:
        raise Phase2CError(f"{where}: invalid v1 number {value!r}") from None
    if not out.is_finite():
        raise Phase2CError(f"{where}: v1 number must be finite")
    return out


def _v2_number(value: object, where: str) -> Decimal | None:
    if value is None:
        return None
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise Phase2CError(f"{where}: v2 value must be a JSON number or null")
    if isinstance(value, float) and not math.isfinite(value):
        raise Phase2CError(f"{where}: v2 number must be finite")
    return Decimal(str(value))


def _reason_registry() -> dict[str, dict]:
    core = catalogue_registry.load_reason_catalogue(
        catalogue_registry.CORE_REASON_CATALOGUE, expected_namespace="core"
    )
    sub12 = catalogue_registry.load_reason_catalogue(
        catalogue_registry.SUB12_REASON_CATALOGUE, expected_namespace="sub12"
    )
    return catalogue_registry.reason_index(core, sub12)


def _require_reason(code: object, registry: dict[str, dict], where: str) -> str:
    text = str(code or "").strip()
    spec = registry.get(text)
    if spec is None:
        raise Phase2CError(f"{where}: unregistered v2 reason code {text!r}")
    if not spec.get("contract_valid"):
        raise Phase2CError(f"{where}: reason code {text!r} is not valid in persisted contracts")
    return text


def compare_population(v1: dict[tuple[str, str], dict], v2: dict[tuple[str, str], dict],
                       registry: dict[str, dict]) -> dict:
    """Compare one partition/class metric population after identity has already been joined."""
    if set(v1) != set(v2):
        missing = sorted(set(v1) - set(v2))[:10]
        extra = sorted(set(v2) - set(v1))[:10]
        raise Phase2CError(f"v1/v2 metric population mismatch; missing={missing} extra={extra}")

    accepted_v1, accepted_v2 = set(), set()
    allowed = Counter()
    states_v1, states_v2 = Counter(), Counter()
    reason_map: dict[tuple[str, str], Counter] = defaultdict(Counter)
    raw_compared = eligible_compared = 0

    for key in sorted(v1):
        photo_key, metric_id = key
        a, b = v1[key], v2[key]
        where = f"{photo_key} {metric_id}"
        s1 = str(a.get("state") or "").strip()
        s2 = str(b.get("state") or "").strip()
        if s1 not in measurement_contract.RELIABILITY_STATES:
            raise Phase2CError(f"{where}: invalid v1 state {s1!r}")
        if s2 not in measurement_contract_v2.RELIABILITY_STATES:
            raise Phase2CError(f"{where}: invalid v2 state {s2!r}")
        states_v1[s1] += 1
        states_v2[s2] += 1

        raw1 = _v1_number(a.get("raw_value"), f"{where} raw v1")
        raw2 = _v2_number(b.get("raw_value"), f"{where} raw v2")
        eligible1 = _v1_number(a.get("eligible_value"), f"{where} eligible v1")
        eligible2 = _v2_number(b.get("eligible_value"), f"{where} eligible v2")

        if s1 == "accepted":
            accepted_v1.add(key)
            if raw1 is None or eligible1 is None:
                raise Phase2CError(f"{where}: accepted v1 row lacks raw/eligible value")
        elif eligible1 is not None:
            raise Phase2CError(f"{where}: non-accepted v1 row has eligible value")

        if s2 == "accepted":
            accepted_v2.add(key)
            if raw2 is None or eligible2 is None:
                raise Phase2CError(f"{where}: accepted v2 row lacks raw/eligible value")
            if b.get("reason_code") is not None:
                raise Phase2CError(f"{where}: accepted v2 row has a reason code")
        elif s2 == "withheld":
            if raw2 is None or eligible2 is not None:
                raise Phase2CError(f"{where}: withheld v2 row must have raw and no eligible value")
            _require_reason(b.get("reason_code"), registry, where)
        else:
            if raw2 is not None or eligible2 is not None:
                raise Phase2CError(f"{where}: unavailable v2 row cannot have raw/eligible value")
            _require_reason(b.get("reason_code"), registry, where)

        transition_allowed = (
            s1 == "unavailable"
            and s2 == "withheld"
            and metric_id in TWELVE_METRICS
            and raw1 is None
            and raw2 is not None
            and eligible2 is None
        )
        if s1 != s2:
            if not transition_allowed:
                raise Phase2CError(f"{where}: unapproved state change {s1} -> {s2}")
            allowed[metric_id] += 1

        if raw1 is not None:
            raw_compared += 1
            if raw2 is None or raw1 != raw2:
                raise Phase2CError(f"{where}: raw changed from {raw1} to {raw2}")
        elif raw2 is not None and not transition_allowed:
            raise Phase2CError(f"{where}: new v2 raw value is outside the approved twelve.* observability change")

        if s1 == "accepted":
            eligible_compared += 1
            if eligible1 != eligible2:
                raise Phase2CError(f"{where}: eligible changed from {eligible1} to {eligible2}")

        if s1 != "accepted":
            text = str(a.get("reason_text") or "").strip()
            if not text:
                raise Phase2CError(f"{where}: non-accepted v1 row has no free-text reason to map")
            code = _require_reason(b.get("reason_code"), registry, where)
            subject = b.get("reason_subject")
            token = code if subject is None else f"{code} [{subject}]"
            reason_map[(metric_id, text)][token] += 1

    if accepted_v1 != accepted_v2:
        missing = sorted(accepted_v1 - accepted_v2)[:10]
        extra = sorted(accepted_v2 - accepted_v1)[:10]
        raise Phase2CError(f"accepted set changed; missing={missing} extra={extra}")

    mappings = []
    for (metric_id, text), targets in sorted(reason_map.items()):
        mappings.append({
            "metric_id": metric_id,
            "v1_reason": text,
            "v2_targets": dict(sorted(targets.items())),
        })
    return {
        "metric_rows": len(v1),
        "accepted_rows": len(accepted_v1),
        "raw_values_compared": raw_compared,
        "eligible_values_compared": eligible_compared,
        "states_v1": dict(sorted(states_v1.items())),
        "states_v2": dict(sorted(states_v2.items())),
        "allowed_state_changes": dict(sorted(allowed.items())),
        "v1_reason_mappings": mappings,
    }


def _v1_rows(path: Path, requested: list[dict], config: dict) -> dict[tuple[str, str], dict]:
    photos = [(item["physical_watch_id"], item["workspace_path"]) for item in requested]
    measurement_contract.validate(
        path, config, submariner12_measurement_adapter.ADAPTER_INFO, photos
    )
    path_to_key = {
        str(item["workspace_path"]).replace(",", ";"): item["photo_key"]
        for item in requested
    }
    out: dict[tuple[str, str], dict] = {}
    with path.open(newline="", encoding="utf-8") as fh:
        for line_no, row in enumerate(csv.DictReader(fh), start=2):
            workspace_path = (row.get("path") or "").strip()
            photo_key = path_to_key.get(workspace_path)
            if photo_key is None:
                raise Phase2CError(f"{path}:{line_no}: v1 path is not one of the requested frozen photos")
            expected_wid = next(item["physical_watch_id"] for item in requested if item["photo_key"] == photo_key)
            if (row.get("physical_watch_id") or "").strip() != expected_wid:
                raise Phase2CError(f"{path}:{line_no}: v1 physical watch identity mismatch")
            metric_id = (row.get("metric") or "").strip()
            key = (photo_key, metric_id)
            if key in out:
                raise Phase2CError(f"{path}:{line_no}: duplicate v1 photo/metric {key}")
            out[key] = {
                "state": (row.get("reliability_state") or "").strip(),
                "raw_value": row.get("raw_value"),
                "eligible_value": row.get("eligible_value"),
                "reason_text": row.get("reliability_reason"),
            }
    return out


def _v2_rows(path: Path, requested: list[dict], config: dict, *, snapshot_id: str,
             partition: str, cls: str) -> tuple[dict[tuple[str, str], dict], str]:
    records = measurement_contract_v2.read_canonical(path)
    if not records:
        raise Phase2CError(f"{path}: empty v2 contract")
    header = records[0]
    measurement_contract_v2.validate_header_foundation(header)
    expected_bundle = catalogue_registry.bundle_metadata()
    exact_header = {
        "snapshot_id": snapshot_id,
        "claimed_model": str(config["model"]).strip().upper(),
        "family": str(config["family"]).strip(),
        "partition": partition,
        "class_label": cls,
        "adapter": submariner12_measurement_adapter_v2.ADAPTER,
        "reliability_policy": submariner12_measurement_adapter_v2.RELIABILITY_POLICY,
        "metric_catalogue": expected_bundle["metric_catalogue"],
        "reason_catalogues": expected_bundle["reason_catalogues"],
    }
    for field, expected in exact_header.items():
        if header.get(field) != expected:
            raise Phase2CError(f"{path}: header {field} mismatch: {header.get(field)!r} != {expected!r}")

    expected_photos = {item["photo_key"]: item for item in requested}
    photos_seen: dict[str, dict] = {}
    out: dict[tuple[str, str], dict] = {}
    expected_metrics = {str(m["app_key"]) for m in config["calibration_metrics"]}
    for record in records[1:]:
        kind = record.get("record_type")
        if kind == "photo":
            photo_key = str(record.get("photo_key") or "")
            item = expected_photos.get(photo_key)
            if item is None or photo_key in photos_seen:
                raise Phase2CError(f"{path}: unexpected or duplicate photo record {photo_key!r}")
            if record.get("image_sha256") != item["image_sha256"]:
                raise Phase2CError(f"{path}: image hash mismatch for {photo_key}")
            if record.get("physical_watch_id") != item["physical_watch_id"]:
                raise Phase2CError(f"{path}: physical watch mismatch for {photo_key}")
            photos_seen[photo_key] = record
        elif kind == "metric":
            photo_key = str(record.get("photo_key") or "")
            metric_id = str(record.get("metric_id") or "")
            if photo_key not in expected_photos or metric_id not in expected_metrics:
                raise Phase2CError(f"{path}: unexpected metric record {(photo_key, metric_id)}")
            key = (photo_key, metric_id)
            if key in out:
                raise Phase2CError(f"{path}: duplicate metric record {key}")
            out[key] = record
        else:
            raise Phase2CError(f"{path}: unsupported record_type {kind!r}")

    if set(photos_seen) != set(expected_photos):
        raise Phase2CError(f"{path}: photo population does not match requested frozen photos")
    expected_keys = {(p, m) for p in expected_photos for m in expected_metrics}
    if set(out) != expected_keys:
        raise Phase2CError(f"{path}: metric population does not contain each configured metric exactly once per photo")
    return out, str(header["measurement_fingerprint"])


def prove(snapshot_dir: Path, replay_root: Path, out_path: Path) -> dict:
    snapshot = evidence_snapshot.verify(snapshot_dir)
    model = contracts.exact_model(snapshot.get("model"), "Phase 2C snapshot model")
    config_path = snapshot_dir / snapshot["files"]["config"]
    config = json.loads(config_path.read_text(encoding="utf-8"))
    contracts.validate_config(config, model, None)
    if model != submariner12_measurement_adapter_v2.MODEL:
        raise Phase2CError(f"Phase 2C v2 proof currently supports only 124060, got {model}")

    base = replay_root / model
    acq_root = base / "dataset"
    split_csv = base / "locked_split.csv"
    geometry = base / "geometry"
    for required in (acq_root / "acquired_images.csv", split_csv, geometry):
        if not required.exists():
            raise Phase2CError(f"Phase 2C requires the completed v1 replay workspace: missing {required}")

    registry = _reason_registry()
    checkout_sha = (os.environ.get("WATCH_ALIGN_HEAD_SHA") or "").strip() or None
    groups = []
    fingerprints = set()
    total_rows = total_accepted = total_raw = total_eligible = 0
    allowed_total = Counter()

    for partition, cls in GROUPS:
        requested = submariner12_measurement_adapter_v2.requested_photos(
            acq_root, split_csv, partition, cls, model
        )
        v1_path = measurement_contract.output_path(geometry, model, partition, cls)
        if not v1_path.is_file():
            raise Phase2CError(f"missing v1 replay contract {v1_path}")

        v2_summary = submariner12_measurement_adapter_v2.measure_contract(
            config, acq_root, split_csv, geometry, partition, cls,
            snapshot["snapshot_id"], checkout_sha=checkout_sha,
        )
        v2_path = Path(v2_summary["path"])
        rows1 = _v1_rows(v1_path, requested, config)
        rows2, fingerprint = _v2_rows(
            v2_path, requested, config,
            snapshot_id=snapshot["snapshot_id"], partition=partition, cls=cls,
        )
        fingerprints.add(fingerprint)
        result = compare_population(rows1, rows2, registry)
        result.update({
            "partition": partition,
            "class_label": cls,
            "photos": len(requested),
            "v1_contract": str(v1_path),
            "v2_contract": str(v2_path),
        })
        groups.append(result)
        total_rows += result["metric_rows"]
        total_accepted += result["accepted_rows"]
        total_raw += result["raw_values_compared"]
        total_eligible += result["eligible_values_compared"]
        allowed_total.update(result["allowed_state_changes"])

    if len(fingerprints) != 1:
        raise Phase2CError(f"v2 measurement fingerprint changed across groups: {sorted(fingerprints)}")

    report = {
        "schema": "watch_align.phase2c_v1_v2_equivalence",
        "version": 1,
        "status": "PASS",
        "snapshot_id": snapshot["snapshot_id"],
        "model": model,
        "family": str(config.get("family") or ""),
        "v1_adapter": dict(submariner12_measurement_adapter.ADAPTER_INFO),
        "v2_adapter": {
            "adapter": dict(submariner12_measurement_adapter_v2.ADAPTER),
            "reliability_policy": dict(submariner12_measurement_adapter_v2.RELIABILITY_POLICY),
            "contract_schema_version": 2,
        },
        "measurement_fingerprint": next(iter(fingerprints)),
        "totals": {
            "metric_rows": total_rows,
            "accepted_rows": total_accepted,
            "v1_raw_values_compared": total_raw,
            "accepted_eligible_values_compared": total_eligible,
            "allowed_state_changes": dict(sorted(allowed_total.items())),
        },
        "groups": groups,
    }
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return report


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("snapshot", type=Path, help="immutable evidence_snapshot_v1 directory")
    ap.add_argument("--replay-root", type=Path, default=Path("datasets/watch_calibrator_replay"))
    ap.add_argument("--out", type=Path, required=True, help="Phase 2C proof report JSON")
    args = ap.parse_args(argv)
    report = prove(args.snapshot, args.replay_root, args.out)
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
