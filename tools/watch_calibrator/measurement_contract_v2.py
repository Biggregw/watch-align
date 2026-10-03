"""Generic schema/canonical-JSONL foundation for measurement contract v2.

No watch-family decision logic belongs here. Phase 2B will make the desktop harness serialize the
shared Java decisions into these records; this module only defines deterministic wire mechanics and
generic structural checks.
"""
from __future__ import annotations

import json
from pathlib import Path

import catalogue_registry
import contracts

SCHEMA = "watch_align.measurement_contract"
SCHEMA_VERSION = 2
RELIABILITY_STATES = {"accepted", "withheld", "unavailable"}
PHOTO_STAGES = ("readable", "preflight", "region", "geometry", "layout")
METRIC_STAGES = ("metric.raw", "metric.reliability")
LAYOUT_STATES = {"compatible", "unresolved", "incompatible", "conflicting"}


class MeasurementContractV2Error(contracts.ContractError):
    """Raised when v2 JSONL is malformed or non-canonical."""


def canonical_line(record: dict) -> bytes:
    if not isinstance(record, dict):
        raise MeasurementContractV2Error("JSONL records must be objects")
    return catalogue_registry.canonical_json_bytes(record)


def write_records(path: Path, records: list[dict]) -> None:
    """Write already-ordered v2 records with one canonical object per line."""
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("wb") as fh:
        for record in records:
            fh.write(canonical_line(record))


def read_canonical(path: Path) -> list[dict]:
    """Read JSONL and require byte-for-byte canonical reserialization."""
    try:
        raw = path.read_bytes()
    except OSError as exc:
        raise MeasurementContractV2Error(f"measurement contract not readable: {path}") from exc
    if raw.startswith(b"\xef\xbb\xbf"):
        raise MeasurementContractV2Error("measurement contract must be UTF-8 without BOM")
    if b"\r" in raw:
        raise MeasurementContractV2Error("measurement contract must use LF line endings")
    if raw and not raw.endswith(b"\n"):
        raise MeasurementContractV2Error("measurement contract must end with LF")
    if not raw:
        raise MeasurementContractV2Error("measurement contract is empty")
    records = []
    for line_no, line in enumerate(raw.splitlines(keepends=True), start=1):
        if line == b"\n":
            raise MeasurementContractV2Error(f"line {line_no}: blank records are not allowed")
        try:
            text = line[:-1].decode("utf-8")
        except UnicodeDecodeError as exc:
            raise MeasurementContractV2Error(f"line {line_no}: invalid UTF-8") from exc
        try:
            record = json.loads(text, parse_constant=lambda value: (_ for _ in ()).throw(MeasurementContractV2Error(f"line {line_no}: non-finite number {value}")))
        except json.JSONDecodeError as exc:
            raise MeasurementContractV2Error(f"line {line_no}: invalid JSON: {exc}") from exc
        if not isinstance(record, dict):
            raise MeasurementContractV2Error(f"line {line_no}: record must be an object")
        if canonical_line(record) != line:
            raise MeasurementContractV2Error(f"line {line_no}: record is not canonical JSON")
        records.append(record)
    return records


def validate_header_foundation(header: dict) -> None:
    """Validate fields whose meaning is family-agnostic; identity joins land in Phase 2B."""
    if header.get("record_type") != "header":
        raise MeasurementContractV2Error("first record must be a header")
    if header.get("schema") != SCHEMA or header.get("schema_version") != SCHEMA_VERSION:
        raise MeasurementContractV2Error("unsupported measurement-contract schema/version")
    for key in (
        "snapshot_id", "claimed_model", "family", "partition", "class_label",
        "adapter", "reliability_policy", "metric_catalogue", "reason_catalogues",
        "stage_map", "measurement_fingerprint",
    ):
        if key not in header:
            raise MeasurementContractV2Error(f"header missing required field {key!r}")
