"""Versioned generic measurement-contract validation and compatibility materialisation.

The calibration engine consumes only reliability-accepted values, but adapters must preserve the
raw measured value and the reason a value was withheld. This module is deliberately watch-family
agnostic: model-specific geometry and reliability logic live in the selected measurement adapter.
"""
from __future__ import annotations

import csv
import math
from collections import Counter, defaultdict
from pathlib import Path

import contracts

SCHEMA_VERSION = "1"
RELIABILITY_STATES = {"accepted", "withheld", "unavailable"}
FIELDS = [
    "schema_version",
    "physical_watch_id",
    "model",
    "family",
    "path",
    "adapter_id",
    "adapter_version",
    "reliability_policy",
    "dial_source",
    "dial_reproducible",
    "pose_tilt_deg",
    "expected_layout",
    "observed_layout_state",
    "metric",
    "raw_value",
    "reliability_state",
    "reliability_reason",
    "eligible_value",
]


class MeasurementContractError(contracts.ContractError):
    """Raised when an adapter output violates the generic measurement contract."""


def _finite_or_blank(value: object, where: str) -> float | None:
    text = str(value or "").strip()
    if not text:
        return None
    try:
        number = float(text)
    except ValueError:
        raise MeasurementContractError(f"{where}: expected finite number or blank") from None
    if not math.isfinite(number):
        raise MeasurementContractError(f"{where}: expected finite number or blank")
    return number


def _path_text(path: Path) -> str:
    # The desktop harness uses a deliberately simple CSV writer and replaces commas in paths.
    return str(path).replace(",", ";")


def output_path(out_dir: Path, model: str, partition: str, cls: str) -> Path:
    """Canonical location of one partition/class contract inside a calibration geometry directory."""
    return out_dir / f"{model}_{partition}_{cls}_measurement_contract.csv"


def write_empty(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as fh:
        csv.DictWriter(fh, fieldnames=FIELDS).writeheader()


def validate(path: Path, config: dict, adapter: dict, photos: list[tuple[str, Path]]) -> dict:
    """Validate one long-form adapter output before any value can reach calibration."""
    if not path.is_file():
        raise MeasurementContractError(f"measurement contract missing: {path}")

    model = contracts.exact_model(config.get("model"), "measurement contract model")
    family = str(config.get("family") or "").strip()
    if not family:
        raise MeasurementContractError("measurement contract family is required")
    expected_metrics = [str(m.get("app_key") or "").strip() for m in config.get("calibration_metrics") or []]
    if not expected_metrics or any(not m for m in expected_metrics):
        raise MeasurementContractError("measurement contract requires configured metric app_keys")

    expected_adapter_id = str(adapter.get("id") or "").strip()
    expected_adapter_version = str(adapter.get("version") or "").strip()
    expected_policy = str(adapter.get("reliability_policy") or "").strip()
    if not all((expected_adapter_id, expected_adapter_version, expected_policy)):
        raise MeasurementContractError("adapter id/version/reliability_policy are required")

    expected_photos = Counter((wid, _path_text(photo)) for wid, photo in photos)
    rows_by_photo: dict[tuple[str, str], list[dict[str, str]]] = defaultdict(list)
    state_counts = Counter()
    raw_values = eligible_values = 0

    with path.open(newline="", encoding="utf-8") as fh:
        reader = csv.DictReader(fh)
        if reader.fieldnames != FIELDS:
            raise MeasurementContractError(
                f"measurement contract header mismatch: expected {FIELDS}, got {reader.fieldnames}"
            )
        for line_no, row in enumerate(reader, start=2):
            where = f"measurement contract line {line_no}"
            if row.get("schema_version") != SCHEMA_VERSION:
                raise MeasurementContractError(f"{where}: unsupported schema_version {row.get('schema_version')!r}")
            actual_model = contracts.exact_model(row.get("model"), where)
            if actual_model != model:
                raise MeasurementContractError(f"{where}: model {actual_model} does not match {model}")
            if (row.get("family") or "").strip() != family:
                raise MeasurementContractError(f"{where}: family does not match {family}")
            if (row.get("adapter_id") or "").strip() != expected_adapter_id:
                raise MeasurementContractError(f"{where}: adapter_id mismatch")
            if (row.get("adapter_version") or "").strip() != expected_adapter_version:
                raise MeasurementContractError(f"{where}: adapter_version mismatch")
            if (row.get("reliability_policy") or "").strip() != expected_policy:
                raise MeasurementContractError(f"{where}: reliability_policy mismatch")
            if not (row.get("expected_layout") or "").strip():
                raise MeasurementContractError(f"{where}: expected_layout is required")
            if not (row.get("observed_layout_state") or "").strip():
                raise MeasurementContractError(f"{where}: observed_layout_state is required")

            metric = (row.get("metric") or "").strip()
            if metric not in expected_metrics:
                raise MeasurementContractError(f"{where}: unexpected metric {metric!r}")
            state = (row.get("reliability_state") or "").strip()
            if state not in RELIABILITY_STATES:
                raise MeasurementContractError(f"{where}: unsupported reliability_state {state!r}")
            raw = _finite_or_blank(row.get("raw_value"), f"{where} raw_value")
            eligible = _finite_or_blank(row.get("eligible_value"), f"{where} eligible_value")
            if state == "accepted":
                if raw is None or eligible is None:
                    raise MeasurementContractError(f"{where}: accepted metric requires raw and eligible values")
            elif eligible is not None:
                raise MeasurementContractError(f"{where}: non-accepted metric cannot have eligible_value")
            if state != "accepted" and not (row.get("reliability_reason") or "").strip():
                raise MeasurementContractError(f"{where}: withheld/unavailable metric requires a reason")

            key = ((row.get("physical_watch_id") or "").strip(), (row.get("path") or "").strip())
            if not key[0] or not key[1]:
                raise MeasurementContractError(f"{where}: physical_watch_id and path are required")
            rows_by_photo[key].append(row)
            state_counts[state] += 1
            raw_values += raw is not None
            eligible_values += eligible is not None

    # Each grouped contract photo represents one requested photo. Do not pass the grouped row lists
    # themselves into Counter: Counter(mapping) treats mapping values as counts, which would compare
    # lists of metric rows against the expected integer multiplicities.
    actual_photos = Counter(rows_by_photo.keys())
    if actual_photos != expected_photos:
        raise MeasurementContractError(
            f"measurement contract photo population {dict(actual_photos)} does not match requested {dict(expected_photos)}"
        )
    metric_set = set(expected_metrics)
    for key, rows in rows_by_photo.items():
        metrics = [r["metric"] for r in rows]
        if len(metrics) != len(expected_metrics) or set(metrics) != metric_set:
            raise MeasurementContractError(f"measurement contract photo {key} does not contain each configured metric exactly once")

    return {
        "schema_version": int(SCHEMA_VERSION),
        "photos": len(rows_by_photo),
        "metric_rows": sum(len(v) for v in rows_by_photo.values()),
        "raw_values": raw_values,
        "eligible_values": eligible_values,
        "reliability_states": dict(sorted(state_counts.items())),
    }


def materialize_eligible_wide(contract_csv: Path, config: dict, photos: list[tuple[str, Path]], out_csv: Path) -> None:
    """Create the existing wide calibration input using only contract eligible_value fields.

    This bridge lets the statistical engine remain unchanged while proving that the new adapter
    contract is numerically equivalent to the established 124060 production-gated route.
    """
    metrics = [str(m["app_key"]) for m in config["calibration_metrics"]]
    groups: dict[tuple[str, str], dict] = {}
    with contract_csv.open(newline="", encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            key = ((row.get("physical_watch_id") or "").strip(), (row.get("path") or "").strip())
            g = groups.setdefault(key, {
                "physical_watch_id": key[0],
                "model": row.get("model", ""),
                "path": key[1],
                "dial_source": row.get("dial_source", ""),
                "dial_reproducible": row.get("dial_reproducible", ""),
                "pose_tilt_deg": row.get("pose_tilt_deg", ""),
            })
            if (row.get("reliability_state") or "") == "accepted":
                g[row["metric"]] = row.get("eligible_value", "")

    out_csv.parent.mkdir(parents=True, exist_ok=True)
    fields = ["physical_watch_id", "model", "path", "dial_source", "dial_reproducible", "pose_tilt_deg", *metrics]
    with out_csv.open("w", newline="", encoding="utf-8") as fh:
        writer = csv.DictWriter(fh, fieldnames=fields)
        writer.writeheader()
        for wid, photo in photos:
            key = (wid, _path_text(photo))
            row = groups.get(key)
            if row is None:
                raise MeasurementContractError(f"missing validated contract photo {key}")
            writer.writerow({field: row.get(field, "") for field in fields})
