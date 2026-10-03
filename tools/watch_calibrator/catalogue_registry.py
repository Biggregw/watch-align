"""Versioned metric/reason catalogue loading and canonical hashing for measurement contract v2.

This module owns catalogue structure only. It does not know any watch geometry or reliability rule.
Family meaning remains in the shared Java core; the catalogues describe that meaning for validation,
fingerprinting and reporting.
"""
from __future__ import annotations

import hashlib
import json
import math
import re
import unicodedata
from pathlib import Path

import contracts

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]

CORE_REASON_CATALOGUE = HERE / "catalogues" / "core_reasons_v1.json"
SUB12_CATALOGUE_DIR = REPO / "android" / "app" / "src" / "main" / "java" / "com" / "watchalign" / "mobile" / "catalogues"
SUB12_REASON_CATALOGUE = SUB12_CATALOGUE_DIR / "sub12_reasons_v1.json"
SUB12_124060_METRIC_CATALOGUE = SUB12_CATALOGUE_DIR / "sub12_124060_metrics_v1.json"

REASON_SCHEMA = "watch_align.reason_catalogue"
METRIC_SCHEMA = "watch_align.metric_catalogue"
REASON_CLASSES = {
    "input", "not_found", "occluded", "out_of_range_geometry",
    "unstable", "layout_conflict", "policy", "internal_error",
}
ELIGIBLE_RELATIONS = {"identical_to_raw", "recomputed_on_reliable_support"}
FIELD_TYPES = {"boolean", "integer", "number", "string"}


class CatalogueError(contracts.ContractError):
    """Raised when a versioned catalogue is malformed or self-inconsistent."""


def _normalise(value):
    if isinstance(value, str):
        return unicodedata.normalize("NFC", value)
    if isinstance(value, bool) or value is None or isinstance(value, int):
        return value
    if isinstance(value, float):
        if not math.isfinite(value):
            raise CatalogueError("canonical JSON cannot contain NaN or infinity")
        return 0.0 if value == 0.0 else value
    if isinstance(value, list):
        return [_normalise(v) for v in value]
    if isinstance(value, dict):
        out = {}
        for key, item in value.items():
            if not isinstance(key, str):
                raise CatalogueError("canonical JSON object keys must be strings")
            k = unicodedata.normalize("NFC", key)
            if k in out:
                raise CatalogueError(f"duplicate key after NFC normalisation: {k!r}")
            out[k] = _normalise(item)
        return out
    raise CatalogueError(f"unsupported canonical JSON value type: {type(value).__name__}")


def canonical_json_bytes(value) -> bytes:
    """Canonical UTF-8 JSON object bytes used for catalogue hashing (single final LF)."""
    normal = _normalise(value)
    text = json.dumps(normal, ensure_ascii=True, sort_keys=True, separators=(",", ":"), allow_nan=False)
    return (text + "\n").encode("utf-8")


def catalogue_hash(value) -> str:
    return hashlib.sha256(canonical_json_bytes(value)).hexdigest()


def _load_json(path: Path) -> dict:
    try:
        raw = path.read_text(encoding="utf-8")
    except OSError as exc:
        raise CatalogueError(f"catalogue not readable: {path}") from exc
    try:
        data = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise CatalogueError(f"catalogue is not valid JSON: {path}: {exc}") from exc
    if not isinstance(data, dict):
        raise CatalogueError(f"catalogue root must be an object: {path}")
    return data


def _exact_keys(obj: dict, expected: set[str], where: str) -> None:
    actual = set(obj)
    if actual != expected:
        raise CatalogueError(f"{where}: keys mismatch; missing={sorted(expected-actual)} extra={sorted(actual-expected)}")


def _positive_version(value, where: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < 1:
        raise CatalogueError(f"{where}: version must be a positive integer")
    return value


def _validate_declared_fields(fields: object, where: str) -> None:
    if not isinstance(fields, dict):
        raise CatalogueError(f"{where}: fields must be an object")
    for name, spec in fields.items():
        if not isinstance(name, str) or not name:
            raise CatalogueError(f"{where}: field names must be non-empty strings")
        if not isinstance(spec, dict):
            raise CatalogueError(f"{where}.{name}: field declaration must be an object")
        _exact_keys(spec, {"type", "unit"}, f"{where}.{name}")
        if spec["type"] not in FIELD_TYPES:
            raise CatalogueError(f"{where}.{name}: unsupported type {spec['type']!r}")
        if spec["unit"] is not None and (not isinstance(spec["unit"], str) or not spec["unit"]):
            raise CatalogueError(f"{where}.{name}: unit must be null or a non-empty string")


def load_reason_catalogue(path: Path, *, expected_namespace: str | None = None) -> dict:
    data = _load_json(path)
    _exact_keys(data, {"schema", "catalogue_id", "version", "namespace", "reasons"}, str(path))
    if data["schema"] != REASON_SCHEMA:
        raise CatalogueError(f"{path}: unsupported reason catalogue schema {data['schema']!r}")
    _positive_version(data["version"], str(path))
    namespace = data["namespace"]
    if not isinstance(namespace, str) or not namespace:
        raise CatalogueError(f"{path}: namespace must be a non-empty string")
    if expected_namespace is not None and namespace != expected_namespace:
        raise CatalogueError(f"{path}: namespace {namespace!r} does not match {expected_namespace!r}")
    if not isinstance(data["catalogue_id"], str) or not data["catalogue_id"]:
        raise CatalogueError(f"{path}: catalogue_id must be a non-empty string")
    reasons = data["reasons"]
    if not isinstance(reasons, list) or not reasons:
        raise CatalogueError(f"{path}: reasons must be a non-empty list")
    codes = []
    for i, reason in enumerate(reasons):
        where = f"{path}: reasons[{i}]"
        if not isinstance(reason, dict):
            raise CatalogueError(f"{where}: reason must be an object")
        _exact_keys(reason, {"code", "class", "meaning", "contract_valid"}, where)
        code = reason["code"]
        if not isinstance(code, str) or not code.startswith(namespace + "."):
            raise CatalogueError(f"{where}: reason code must use namespace {namespace!r}")
        if reason["class"] not in REASON_CLASSES:
            raise CatalogueError(f"{where}: unsupported reason class {reason['class']!r}")
        if not isinstance(reason["meaning"], str) or not reason["meaning"].strip():
            raise CatalogueError(f"{where}: meaning must be a non-empty string")
        if not isinstance(reason["contract_valid"], bool):
            raise CatalogueError(f"{where}: contract_valid must be boolean")
        codes.append(code)
    if codes != sorted(codes) or len(codes) != len(set(codes)):
        raise CatalogueError(f"{path}: reason codes must be unique and lexicographically sorted")
    catalogue_hash(data)
    return data


def load_metric_catalogue(path: Path, *, expected_family: str | None = None, expected_model: str | None = None) -> dict:
    data = _load_json(path)
    _exact_keys(data, {"schema", "catalogue_id", "version", "family", "model", "metrics"}, str(path))
    if data["schema"] != METRIC_SCHEMA:
        raise CatalogueError(f"{path}: unsupported metric catalogue schema {data['schema']!r}")
    _positive_version(data["version"], str(path))
    for field in ("catalogue_id", "family", "model"):
        if not isinstance(data[field], str) or not data[field]:
            raise CatalogueError(f"{path}: {field} must be a non-empty string")
    if expected_family is not None and data["family"] != expected_family:
        raise CatalogueError(f"{path}: family {data['family']!r} does not match {expected_family!r}")
    if expected_model is not None and data["model"] != expected_model:
        raise CatalogueError(f"{path}: model {data['model']!r} does not match {expected_model!r}")
    metrics = data["metrics"]
    if not isinstance(metrics, list) or not metrics:
        raise CatalogueError(f"{path}: metrics must be a non-empty list")
    ids = []
    for i, metric in enumerate(metrics):
        where = f"{path}: metrics[{i}]"
        if not isinstance(metric, dict):
            raise CatalogueError(f"{where}: metric must be an object")
        _exact_keys(metric, {"metric_id", "metric_version", "unit", "raw_definition", "eligible_definition", "eligible_relation", "support", "diagnostics"}, where)
        metric_id = metric["metric_id"]
        if not isinstance(metric_id, str) or not metric_id:
            raise CatalogueError(f"{where}: metric_id must be a non-empty string")
        _positive_version(metric["metric_version"], where)
        if not isinstance(metric["unit"], str) or not metric["unit"]:
            raise CatalogueError(f"{where}: unit must be a non-empty string")
        for field in ("raw_definition", "eligible_definition"):
            if not isinstance(metric[field], str) or not metric[field].strip():
                raise CatalogueError(f"{where}: {field} must be a non-empty string")
        if metric["eligible_relation"] not in ELIGIBLE_RELATIONS:
            raise CatalogueError(f"{where}: unsupported eligible_relation {metric['eligible_relation']!r}")
        _validate_declared_fields(metric["support"], f"{where}.support")
        _validate_declared_fields(metric["diagnostics"], f"{where}.diagnostics")
        ids.append(metric_id)
    if ids != sorted(ids) or len(ids) != len(set(ids)):
        raise CatalogueError(f"{path}: metric IDs must be unique and lexicographically sorted")
    catalogue_hash(data)
    return data


def reason_index(*catalogues: dict) -> dict[str, dict]:
    out = {}
    for catalogue in catalogues:
        for reason in catalogue["reasons"]:
            code = reason["code"]
            if code in out:
                raise CatalogueError(f"reason code appears in more than one catalogue: {code}")
            out[code] = reason
    return out


def metric_index(catalogue: dict) -> dict[str, dict]:
    return {m["metric_id"]: m for m in catalogue["metrics"]}


_JAVA_REASON = re.compile(r'^\s*static\s+final\s+String\s+[A-Z0-9_]+\s*=\s*"([^"]+)";', re.MULTILINE)


def java_reason_codes(path: Path) -> set[str]:
    """Extract direct String constants from CoreReasons/Sub12Reasons for repository consistency tests."""
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as exc:
        raise CatalogueError(f"Java reason source not readable: {path}") from exc
    return set(_JAVA_REASON.findall(text))


def bundle_metadata() -> dict:
    """Load approved v2 catalogues and return immutable identities/hashes."""
    core = load_reason_catalogue(CORE_REASON_CATALOGUE, expected_namespace="core")
    sub12 = load_reason_catalogue(SUB12_REASON_CATALOGUE, expected_namespace="sub12")
    metrics = load_metric_catalogue(SUB12_124060_METRIC_CATALOGUE, expected_family="submariner_12", expected_model="124060")
    return {
        "metric_catalogue": {"id": metrics["catalogue_id"], "version": metrics["version"], "sha256": catalogue_hash(metrics)},
        "reason_catalogues": [
            {"id": c["catalogue_id"], "version": c["version"], "sha256": catalogue_hash(c)}
            for c in (core, sub12)
        ],
    }
