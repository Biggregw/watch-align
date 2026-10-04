"""Generic schema, canonical JSONL and validation for measurement contract v2.

The generic core owns wire structure, identity and invariants only. It never knows watch geometry,
marker types, detector thresholds or family reliability rules. Family meaning arrives through the
versioned metric/reason catalogues and the shared Java decision stream.
"""
from __future__ import annotations

import json
import math
import re
from pathlib import Path, PurePosixPath

import catalogue_registry
import contracts

SCHEMA = "watch_align.measurement_contract"
SCHEMA_VERSION = 2
RELIABILITY_STATES = {"accepted", "withheld", "unavailable"}
PHOTO_STAGES = ("readable", "preflight", "region", "geometry", "layout")
METRIC_STAGES = ("metric.raw", "metric.reliability")
LAYOUT_STATES = {"compatible", "unresolved", "incompatible", "conflicting"}
PHOTO_OUTCOMES = {"pass", "flag", "fail", "not_applicable", "blocked"}
LAYOUT_OUTCOMES = LAYOUT_STATES | {"blocked"}
ORIGINS = {"photo", "metric"}
_HEX64 = re.compile(r"^[0-9a-f]{64}$")
_WINDOWS_ABSOLUTE = re.compile(r"^[A-Za-z]:[\\/]")


class MeasurementContractV2Error(contracts.ContractError):
    """Raised when v2 JSONL is malformed, non-canonical or internally inconsistent."""


def canonical_line(record: dict) -> bytes:
    if not isinstance(record, dict):
        raise MeasurementContractV2Error("JSONL records must be objects")
    try:
        return catalogue_registry.canonical_json_bytes(record)
    except catalogue_registry.CatalogueError as exc:
        raise MeasurementContractV2Error(str(exc)) from exc


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
            record = json.loads(
                text,
                parse_constant=lambda value: (_ for _ in ()).throw(
                    MeasurementContractV2Error(f"line {line_no}: non-finite number {value}")
                ),
            )
        except json.JSONDecodeError as exc:
            raise MeasurementContractV2Error(f"line {line_no}: invalid JSON: {exc}") from exc
        if not isinstance(record, dict):
            raise MeasurementContractV2Error(f"line {line_no}: record must be an object")
        if canonical_line(record) != line:
            raise MeasurementContractV2Error(f"line {line_no}: record is not canonical JSON")
        records.append(record)
    return records


def _exact_keys(value: object, expected: set[str], where: str) -> dict:
    if not isinstance(value, dict):
        raise MeasurementContractV2Error(f"{where}: object required")
    actual = set(value)
    if actual != expected:
        raise MeasurementContractV2Error(
            f"{where}: keys mismatch; missing={sorted(expected - actual)} extra={sorted(actual - expected)}"
        )
    return value


def _string(value: object, where: str, *, allow_empty: bool = False) -> str:
    if not isinstance(value, str) or (not allow_empty and not value):
        raise MeasurementContractV2Error(f"{where}: {'string' if allow_empty else 'non-empty string'} required")
    return value


def _positive_version(value: object, where: str) -> object:
    if isinstance(value, bool) or not isinstance(value, (int, str)):
        raise MeasurementContractV2Error(f"{where}: version must be a positive integer or non-empty version string")
    if isinstance(value, int):
        if value < 1:
            raise MeasurementContractV2Error(f"{where}: version must be positive")
    elif not value.strip():
        raise MeasurementContractV2Error(f"{where}: version string cannot be empty")
    return value


def _sha256(value: object, where: str) -> str:
    text = _string(value, where)
    if not _HEX64.fullmatch(text):
        raise MeasurementContractV2Error(f"{where}: lowercase SHA-256 required")
    return text


def _safe_photo_key(value: object, where: str) -> str:
    text = _string(value, where)
    if "\\" in text:
        raise MeasurementContractV2Error(f"{where}: photo_key must use forward slashes")
    p = PurePosixPath(text)
    if p.is_absolute() or any(part in {"", ".", ".."} for part in p.parts):
        raise MeasurementContractV2Error(f"{where}: manifest-relative photo_key required")
    return text


def _walk_no_workspace_paths(value: object, where: str) -> None:
    """Reject persisted workspace/absolute-path material without mistaking URLs for paths."""
    if isinstance(value, dict):
        for key, item in value.items():
            if not isinstance(key, str):
                raise MeasurementContractV2Error(f"{where}: object keys must be strings")
            if key.lower() in {"workspace_path", "absolute_path", "filesystem_path"}:
                raise MeasurementContractV2Error(f"{where}: workspace path field {key!r} is forbidden")
            _walk_no_workspace_paths(item, f"{where}.{key}")
    elif isinstance(value, list):
        for i, item in enumerate(value):
            _walk_no_workspace_paths(item, f"{where}[{i}]")
    elif isinstance(value, str):
        if value.startswith("/") or _WINDOWS_ABSOLUTE.match(value):
            raise MeasurementContractV2Error(f"{where}: absolute filesystem path is forbidden")


def _identity_object(value: object, where: str) -> dict:
    out = _exact_keys(value, {"id", "version"}, where)
    _string(out["id"], f"{where}.id")
    _positive_version(out["version"], f"{where}.version")
    return out


def _catalogue_identity(value: object, where: str) -> dict:
    out = _exact_keys(value, {"id", "version", "sha256"}, where)
    _string(out["id"], f"{where}.id")
    if isinstance(out["version"], bool) or not isinstance(out["version"], int) or out["version"] < 1:
        raise MeasurementContractV2Error(f"{where}.version: positive integer required")
    _sha256(out["sha256"], f"{where}.sha256")
    return out


def validate_header_foundation(header: dict) -> None:
    """Validate the family-agnostic v2 header shape and primitive identity fields."""
    expected = {
        "record_type", "schema", "schema_version", "snapshot_id", "claimed_model", "family",
        "partition", "class_label", "adapter", "reliability_policy", "metric_catalogue",
        "reason_catalogues", "stage_map", "measurement_fingerprint",
    }
    _exact_keys(header, expected, "header")
    if header["record_type"] != "header":
        raise MeasurementContractV2Error("first record must be a header")
    if header["schema"] != SCHEMA or header["schema_version"] != SCHEMA_VERSION:
        raise MeasurementContractV2Error("unsupported measurement-contract schema/version")
    for key in ("snapshot_id", "claimed_model", "family", "partition", "class_label"):
        _string(header[key], f"header.{key}")
    _identity_object(header["adapter"], "header.adapter")
    _identity_object(header["reliability_policy"], "header.reliability_policy")
    _catalogue_identity(header["metric_catalogue"], "header.metric_catalogue")
    reasons = header["reason_catalogues"]
    if not isinstance(reasons, list) or not reasons:
        raise MeasurementContractV2Error("header.reason_catalogues: non-empty array required")
    ids = []
    for i, item in enumerate(reasons):
        cat = _catalogue_identity(item, f"header.reason_catalogues[{i}]")
        ids.append(cat["id"])
    if len(ids) != len(set(ids)):
        raise MeasurementContractV2Error("header.reason_catalogues: duplicate catalogue IDs")
    expected_stages = list(PHOTO_STAGES) + list(METRIC_STAGES)
    if header["stage_map"] != expected_stages:
        raise MeasurementContractV2Error(
            f"header.stage_map: expected {expected_stages!r}, got {header['stage_map']!r}"
        )
    _sha256(header["measurement_fingerprint"], "header.measurement_fingerprint")
    _walk_no_workspace_paths(header, "header")


def _metadata_for_catalogues(metric_catalogue: dict, reason_catalogues: list[dict]) -> tuple[dict, list[dict]]:
    metric = {
        "id": metric_catalogue["catalogue_id"],
        "version": metric_catalogue["version"],
        "sha256": catalogue_registry.catalogue_hash(metric_catalogue),
    }
    reasons = [
        {
            "id": catalogue["catalogue_id"],
            "version": catalogue["version"],
            "sha256": catalogue_registry.catalogue_hash(catalogue),
        }
        for catalogue in reason_catalogues
    ]
    return metric, reasons


def _registered_reason(code: object, registry: dict[str, dict], where: str) -> str:
    text = _string(code, where)
    spec = registry.get(text)
    if spec is None:
        raise MeasurementContractV2Error(f"{where}: unregistered reason code {text!r}")
    if not spec.get("contract_valid"):
        raise MeasurementContractV2Error(f"{where}: reason code {text!r} is not valid in persisted contracts")
    return text


def _reason_object(value: object, registry: dict[str, dict], where: str, *, required: bool) -> tuple[str | None, str | None, str | None]:
    if value is None:
        if required:
            raise MeasurementContractV2Error(f"{where}: reason is required")
        return None, None, None
    reason = _exact_keys(value, {"code", "subject", "detail"}, where)
    code = _registered_reason(reason["code"], registry, f"{where}.code")
    subject = reason["subject"]
    detail = reason["detail"]
    if subject is not None:
        _string(subject, f"{where}.subject")
    if detail is not None and not isinstance(detail, str):
        raise MeasurementContractV2Error(f"{where}.detail: string or null required")
    return code, subject, detail


def _flat_reason(record: dict, registry: dict[str, dict], where: str, *, required: bool) -> tuple[str | None, str | None, str | None]:
    code, subject, detail = record["reason_code"], record["reason_subject"], record["reason_detail"]
    if code is None:
        if required:
            raise MeasurementContractV2Error(f"{where}: reason_code is required")
        if subject is not None or detail is not None:
            raise MeasurementContractV2Error(f"{where}: reason subject/detail cannot exist without reason_code")
        return None, None, None
    code = _registered_reason(code, registry, f"{where}.reason_code")
    if subject is not None:
        _string(subject, f"{where}.reason_subject")
    if detail is not None and not isinstance(detail, str):
        raise MeasurementContractV2Error(f"{where}.reason_detail: string or null required")
    return code, subject, detail


def _typed_value(value: object, spec: dict, where: str) -> None:
    if value is None:
        return
    kind = spec["type"]
    if kind == "boolean":
        if not isinstance(value, bool):
            raise MeasurementContractV2Error(f"{where}: boolean or null required")
    elif kind == "integer":
        if isinstance(value, bool) or not isinstance(value, int):
            raise MeasurementContractV2Error(f"{where}: integer or null required")
    elif kind == "number":
        if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
            raise MeasurementContractV2Error(f"{where}: finite JSON number or null required")
    elif kind == "string":
        if not isinstance(value, str):
            raise MeasurementContractV2Error(f"{where}: string or null required")
    else:
        raise MeasurementContractV2Error(f"{where}: unsupported declared field type {kind!r}")


def _declared_map(value: object, declarations: dict, where: str) -> dict:
    if not isinstance(value, dict):
        raise MeasurementContractV2Error(f"{where}: object required")
    extra = set(value) - set(declarations)
    if extra:
        raise MeasurementContractV2Error(f"{where}: undeclared fields {sorted(extra)}")
    for key, item in value.items():
        _typed_value(item, declarations[key], f"{where}.{key}")
    return value


def _finite_number_or_none(value: object, where: str) -> float | int | None:
    if value is None:
        return None
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        raise MeasurementContractV2Error(f"{where}: finite JSON number or null required")
    return value


def _validate_stages(stages: object, registry: dict[str, dict], where: str) -> tuple[dict[str, dict], dict | None]:
    if not isinstance(stages, list) or len(stages) != len(PHOTO_STAGES):
        raise MeasurementContractV2Error(f"{where}: exactly {len(PHOTO_STAGES)} photo stages required")
    by_id: dict[str, dict] = {}
    failed: dict | None = None
    for i, expected_id in enumerate(PHOTO_STAGES):
        stage = _exact_keys(stages[i], {"stage", "outcome", "reason", "diagnostics"}, f"{where}[{i}]")
        if stage["stage"] != expected_id:
            raise MeasurementContractV2Error(
                f"{where}[{i}].stage: expected {expected_id!r}, got {stage['stage']!r}"
            )
        outcome = stage["outcome"]
        allowed = LAYOUT_OUTCOMES if expected_id == "layout" else PHOTO_OUTCOMES
        if outcome not in allowed:
            raise MeasurementContractV2Error(f"{where}[{i}].outcome: invalid {outcome!r}")
        if not isinstance(stage["diagnostics"], dict):
            raise MeasurementContractV2Error(f"{where}[{i}].diagnostics: object required")
        _walk_no_workspace_paths(stage["diagnostics"], f"{where}[{i}].diagnostics")

        if failed is not None:
            if outcome != "blocked" or stage["reason"] is not None:
                raise MeasurementContractV2Error(
                    f"{where}[{i}]: every stage after a failure must be blocked with no reason"
                )
        else:
            if outcome == "blocked":
                raise MeasurementContractV2Error(f"{where}[{i}]: stage cannot be blocked before a failure")
            reason_required = outcome in {"fail", "flag", "not_applicable"}
            _reason_object(stage["reason"], registry, f"{where}[{i}].reason", required=reason_required)
            if outcome not in {"fail", "flag", "not_applicable"} and stage["reason"] is not None:
                raise MeasurementContractV2Error(f"{where}[{i}]: outcome {outcome!r} cannot carry a reason")
            if outcome == "fail":
                failed = stage
        by_id[expected_id] = stage
    return by_id, failed


def _string_array(value: object, where: str) -> list[str]:
    if not isinstance(value, list):
        raise MeasurementContractV2Error(f"{where}: array required")
    out = []
    for i, item in enumerate(value):
        text = _string(item, f"{where}[{i}]")
        if text in out:
            raise MeasurementContractV2Error(f"{where}: duplicate value {text!r}")
        out.append(text)
    return out


def _validate_layout(layout: object, layout_stage: dict, where: str) -> dict:
    value = _exact_keys(
        layout,
        {"expected", "observations", "hypotheses", "compatibility", "reliability", "quarantined"},
        where,
    )
    expected = _exact_keys(value["expected"], {"layout_id", "version"}, f"{where}.expected")
    expected_id = _string(expected["layout_id"], f"{where}.expected.layout_id")
    if isinstance(expected["version"], bool) or not isinstance(expected["version"], int) or expected["version"] < 1:
        raise MeasurementContractV2Error(f"{where}.expected.version: positive integer required")
    if value["reliability"] not in {"low", "high"}:
        raise MeasurementContractV2Error(f"{where}.reliability: expected 'low' or 'high'")
    if not isinstance(value["quarantined"], bool):
        raise MeasurementContractV2Error(f"{where}.quarantined: boolean required")

    if layout_stage["outcome"] == "blocked":
        if value["compatibility"] is not None:
            raise MeasurementContractV2Error(f"{where}: blocked layout must have null compatibility")
        if value["observations"] or value["hypotheses"] or value["quarantined"]:
            raise MeasurementContractV2Error(f"{where}: blocked layout cannot contain decisions or quarantine")
        return value

    observations = value["observations"]
    hypotheses = value["hypotheses"]
    if not isinstance(observations, list) or not isinstance(hypotheses, list) or not hypotheses:
        raise MeasurementContractV2Error(f"{where}: unblocked layout requires observation and hypothesis arrays")

    parsed_obs = []
    for i, item in enumerate(observations):
        obs = _exact_keys(
            item,
            {"feature", "present", "high_confidence", "detector_id", "detector_version", "supports", "contradicts"},
            f"{where}.observations[{i}]",
        )
        feature = _string(obs["feature"], f"{where}.observations[{i}].feature")
        _string(obs["detector_id"], f"{where}.observations[{i}].detector_id")
        if not isinstance(obs["present"], bool) or not isinstance(obs["high_confidence"], bool):
            raise MeasurementContractV2Error(f"{where}.observations[{i}]: present/high_confidence must be boolean")
        if obs["high_confidence"] and not obs["present"]:
            raise MeasurementContractV2Error(
                f"{where}.observations[{i}]: absent observation cannot be high-confidence evidence"
            )
        if isinstance(obs["detector_version"], bool) or not isinstance(obs["detector_version"], int) or obs["detector_version"] < 1:
            raise MeasurementContractV2Error(f"{where}.observations[{i}].detector_version: positive integer required")
        supports = _string_array(obs["supports"], f"{where}.observations[{i}].supports")
        contradicts = _string_array(obs["contradicts"], f"{where}.observations[{i}].contradicts")
        if set(supports) & set(contradicts):
            raise MeasurementContractV2Error(f"{where}.observations[{i}]: a layout cannot be both supported and contradicted")
        parsed_obs.append((feature, obs["present"] and obs["high_confidence"], supports, contradicts))

    hypothesis_ids = []
    parsed_hypotheses = []
    for i, item in enumerate(hypotheses):
        hyp = _exact_keys(item, {"layout_id", "state", "decided_by"}, f"{where}.hypotheses[{i}]")
        layout_id = _string(hyp["layout_id"], f"{where}.hypotheses[{i}].layout_id")
        if layout_id in hypothesis_ids:
            raise MeasurementContractV2Error(f"{where}.hypotheses: duplicate layout_id {layout_id!r}")
        hypothesis_ids.append(layout_id)
        if hyp["state"] not in {"supported", "contradicted", "unknown"}:
            raise MeasurementContractV2Error(f"{where}.hypotheses[{i}].state: invalid {hyp['state']!r}")
        decided_by = _string_array(hyp["decided_by"], f"{where}.hypotheses[{i}].decided_by")
        parsed_hypotheses.append((layout_id, hyp["state"], decided_by))

    if expected_id not in hypothesis_ids:
        raise MeasurementContractV2Error(f"{where}: expected layout {expected_id!r} missing from hypotheses")
    candidates = set(hypothesis_ids)
    for i, (_feature, _decides, supports, contradicts) in enumerate(parsed_obs):
        unknown = (set(supports) | set(contradicts)) - candidates
        if unknown:
            raise MeasurementContractV2Error(
                f"{where}.observations[{i}]: references unknown layouts {sorted(unknown)}"
            )

    for i, (layout_id, state, decided_by) in enumerate(parsed_hypotheses):
        support = [feature for feature, decides, supports, _ in parsed_obs if decides and layout_id in supports]
        contradiction = [feature for feature, decides, _, contradicts in parsed_obs if decides and layout_id in contradicts]
        expected_state = "supported" if support else "contradicted" if contradiction else "unknown"
        expected_by = support if support else contradiction if contradiction else []
        if state != expected_state or decided_by != expected_by:
            raise MeasurementContractV2Error(
                f"{where}.hypotheses[{i}]: does not match positive high-confidence observations"
            )

    supported = {layout_id for layout_id, state, _ in parsed_hypotheses if state == "supported"}
    expected_supported = expected_id in supported
    other_supported = bool(supported - {expected_id})
    recomputed = (
        "conflicting" if expected_supported and other_supported
        else "compatible" if expected_supported
        else "incompatible" if other_supported
        else "unresolved"
    )
    if value["compatibility"] != recomputed:
        raise MeasurementContractV2Error(
            f"{where}.compatibility: {value['compatibility']!r} != recomputed {recomputed!r}"
        )
    if layout_stage["outcome"] != recomputed:
        raise MeasurementContractV2Error(
            f"{where}: layout stage outcome {layout_stage['outcome']!r} != compatibility {recomputed!r}"
        )
    expected_quarantine = recomputed in {"incompatible", "conflicting"}
    if value["quarantined"] != expected_quarantine:
        raise MeasurementContractV2Error(
            f"{where}.quarantined: {value['quarantined']!r} != {expected_quarantine!r}"
        )
    return value


def _expected_photo_index(expected_photos: list[dict] | None) -> dict[str, dict] | None:
    if expected_photos is None:
        return None
    out: dict[str, dict] = {}
    for i, item in enumerate(expected_photos):
        if not isinstance(item, dict):
            raise MeasurementContractV2Error(f"expected_photos[{i}]: object required")
        key = _safe_photo_key(item.get("photo_key"), f"expected_photos[{i}].photo_key")
        if key in out:
            raise MeasurementContractV2Error(f"expected_photos: duplicate photo_key {key!r}")
        out[key] = item
    return out


def validate_contract(
    records: list[dict],
    *,
    metric_catalogue: dict,
    reason_catalogues: list[dict],
    expected_identity: dict | None = None,
    expected_photos: list[dict] | None = None,
) -> dict:
    """Validate a complete canonical-v2 record population without re-deciding family meaning.

    `metric_catalogue` and `reason_catalogues` supply schema declarations only. `expected_identity`
    and `expected_photos` are optional frozen-evidence joins. The function returns indexes useful to
    downstream reporting; it never changes a state, reason or value.
    """
    if not isinstance(records, list) or not records:
        raise MeasurementContractV2Error("measurement contract must contain at least one record")
    header = records[0]
    validate_header_foundation(header)

    if not isinstance(metric_catalogue, dict) or "metrics" not in metric_catalogue:
        raise MeasurementContractV2Error("metric catalogue object is required")
    if not isinstance(reason_catalogues, list) or not reason_catalogues:
        raise MeasurementContractV2Error("at least one reason catalogue is required")

    metric_meta, reason_meta = _metadata_for_catalogues(metric_catalogue, reason_catalogues)
    if header["metric_catalogue"] != metric_meta:
        raise MeasurementContractV2Error("header.metric_catalogue does not match supplied catalogue bytes")
    if header["reason_catalogues"] != reason_meta:
        raise MeasurementContractV2Error("header.reason_catalogues do not match supplied catalogue bytes/order")

    if expected_identity is not None:
        if not isinstance(expected_identity, dict):
            raise MeasurementContractV2Error("expected_identity must be an object")
        for key, expected in expected_identity.items():
            if key not in header:
                raise MeasurementContractV2Error(f"expected_identity references unknown header field {key!r}")
            if header[key] != expected:
                raise MeasurementContractV2Error(
                    f"header.{key} mismatch: {header[key]!r} != {expected!r}"
                )

    metric_specs = catalogue_registry.metric_index(metric_catalogue)
    metric_ids = sorted(metric_specs)
    registry = catalogue_registry.reason_index(*reason_catalogues)
    expected_index = _expected_photo_index(expected_photos)

    photos: dict[str, dict] = {}
    metrics: dict[tuple[str, str], dict] = {}
    i = 1
    previous_photo = None
    while i < len(records):
        photo = records[i]
        if not isinstance(photo, dict) or photo.get("record_type") != "photo":
            raise MeasurementContractV2Error(f"record {i + 1}: expected photo record")
        photo = _exact_keys(
            photo,
            {
                "record_type", "photo_key", "image_sha256", "physical_watch_id", "provenance",
                "photo_stages", "layout_evidence",
            },
            f"record {i + 1} photo",
        )
        photo_key = _safe_photo_key(photo["photo_key"], f"record {i + 1}.photo_key")
        if previous_photo is not None and photo_key <= previous_photo:
            raise MeasurementContractV2Error("photo records must be strictly sorted by photo_key")
        previous_photo = photo_key
        if photo_key in photos:
            raise MeasurementContractV2Error(f"duplicate photo record {photo_key!r}")
        _sha256(photo["image_sha256"], f"{photo_key}.image_sha256")
        _string(photo["physical_watch_id"], f"{photo_key}.physical_watch_id")
        if not isinstance(photo["provenance"], dict):
            raise MeasurementContractV2Error(f"{photo_key}.provenance: object required")
        _walk_no_workspace_paths(photo["provenance"], f"{photo_key}.provenance")
        stages, failed = _validate_stages(photo["photo_stages"], registry, f"{photo_key}.photo_stages")
        _validate_layout(photo["layout_evidence"], stages["layout"], f"{photo_key}.layout_evidence")

        if expected_index is not None:
            expected = expected_index.get(photo_key)
            if expected is None:
                raise MeasurementContractV2Error(f"unexpected photo record {photo_key!r}")
            for field in ("image_sha256", "physical_watch_id"):
                if field in expected and photo[field] != expected[field]:
                    raise MeasurementContractV2Error(
                        f"{photo_key}.{field}: {photo[field]!r} != frozen {expected[field]!r}"
                    )
            if "provenance" in expected and photo["provenance"] != expected["provenance"]:
                raise MeasurementContractV2Error(f"{photo_key}.provenance does not match frozen evidence")

        photos[photo_key] = photo
        i += 1
        seen_metric_ids = []
        for expected_metric_id in metric_ids:
            if i >= len(records):
                raise MeasurementContractV2Error(f"{photo_key}: missing metric {expected_metric_id!r}")
            metric = records[i]
            if not isinstance(metric, dict) or metric.get("record_type") != "metric":
                raise MeasurementContractV2Error(
                    f"{photo_key}: expected metric {expected_metric_id!r} directly after photo"
                )
            metric = _exact_keys(
                metric,
                {
                    "record_type", "photo_key", "metric_id", "state", "terminal_stage", "origin",
                    "reason_code", "reason_subject", "secondary_codes", "reason_detail",
                    "raw_value", "eligible_value", "raw_support", "eligible_support", "diagnostics",
                },
                f"{photo_key} metric record {i + 1}",
            )
            if metric["photo_key"] != photo_key:
                raise MeasurementContractV2Error(
                    f"{photo_key}: metric record belongs to {metric['photo_key']!r}"
                )
            metric_id = metric["metric_id"]
            if metric_id != expected_metric_id:
                raise MeasurementContractV2Error(
                    f"{photo_key}: expected metric {expected_metric_id!r}, got {metric_id!r}"
                )
            seen_metric_ids.append(metric_id)
            spec = metric_specs[metric_id]
            state = metric["state"]
            if state not in RELIABILITY_STATES:
                raise MeasurementContractV2Error(f"{photo_key} {metric_id}: invalid state {state!r}")
            terminal_stage = metric["terminal_stage"]
            if terminal_stage not in set(PHOTO_STAGES) | set(METRIC_STAGES):
                raise MeasurementContractV2Error(
                    f"{photo_key} {metric_id}: invalid terminal_stage {terminal_stage!r}"
                )
            if metric["origin"] not in ORIGINS:
                raise MeasurementContractV2Error(
                    f"{photo_key} {metric_id}: invalid origin {metric['origin']!r}"
                )

            raw = _finite_number_or_none(metric["raw_value"], f"{photo_key} {metric_id}.raw_value")
            eligible = _finite_number_or_none(metric["eligible_value"], f"{photo_key} {metric_id}.eligible_value")
            reason_required = state != "accepted"
            primary = _flat_reason(metric, registry, f"{photo_key} {metric_id}", required=reason_required)

            secondary = metric["secondary_codes"]
            if not isinstance(secondary, list):
                raise MeasurementContractV2Error(f"{photo_key} {metric_id}.secondary_codes: array required")
            seen_secondary = set()
            for j, item in enumerate(secondary):
                sec = _exact_keys(item, {"code", "subject"}, f"{photo_key} {metric_id}.secondary_codes[{j}]")
                code = _registered_reason(sec["code"], registry, f"{photo_key} {metric_id}.secondary_codes[{j}].code")
                subject = sec["subject"]
                if subject is not None:
                    _string(subject, f"{photo_key} {metric_id}.secondary_codes[{j}].subject")
                token = (code, subject)
                if token == primary[:2]:
                    raise MeasurementContractV2Error(f"{photo_key} {metric_id}: secondary repeats primary reason")
                if token in seen_secondary:
                    raise MeasurementContractV2Error(f"{photo_key} {metric_id}: duplicate secondary reason {token!r}")
                seen_secondary.add(token)

            _declared_map(metric["raw_support"], spec["support"], f"{photo_key} {metric_id}.raw_support")
            _declared_map(metric["eligible_support"], spec["support"], f"{photo_key} {metric_id}.eligible_support")
            _declared_map(metric["diagnostics"], spec["diagnostics"], f"{photo_key} {metric_id}.diagnostics")
            _walk_no_workspace_paths(metric, f"{photo_key} {metric_id}")

            if state == "accepted":
                if raw is None or eligible is None:
                    raise MeasurementContractV2Error(f"{photo_key} {metric_id}: accepted requires raw and eligible values")
                if primary[0] is not None or secondary:
                    raise MeasurementContractV2Error(f"{photo_key} {metric_id}: accepted cannot carry reasons")
                if terminal_stage != "metric.reliability" or metric["origin"] != "metric":
                    raise MeasurementContractV2Error(
                        f"{photo_key} {metric_id}: accepted must terminate at metric.reliability with metric origin"
                    )
                if spec["eligible_relation"] == "identical_to_raw" and raw != eligible:
                    raise MeasurementContractV2Error(
                        f"{photo_key} {metric_id}: eligible must equal raw for identical_to_raw metric"
                    )
            elif state == "withheld":
                if raw is None or eligible is not None:
                    raise MeasurementContractV2Error(
                        f"{photo_key} {metric_id}: withheld requires raw and forbids eligible value"
                    )
                if terminal_stage != "metric.reliability" or metric["origin"] != "metric":
                    raise MeasurementContractV2Error(
                        f"{photo_key} {metric_id}: withheld must terminate at metric.reliability with metric origin"
                    )
            else:
                if raw is not None or eligible is not None:
                    raise MeasurementContractV2Error(
                        f"{photo_key} {metric_id}: unavailable forbids raw and eligible values"
                    )
                if metric["origin"] == "photo":
                    if failed is None:
                        raise MeasurementContractV2Error(
                            f"{photo_key} {metric_id}: photo-origin unavailable requires a failed photo stage"
                        )
                    if terminal_stage != failed["stage"]:
                        raise MeasurementContractV2Error(
                            f"{photo_key} {metric_id}: terminal stage does not match failed photo stage"
                        )
                    fail_reason = failed["reason"]
                    if fail_reason is None or (
                        primary[0], primary[1], primary[2]
                    ) != (
                        fail_reason.get("code"), fail_reason.get("subject"), fail_reason.get("detail")
                    ):
                        raise MeasurementContractV2Error(
                            f"{photo_key} {metric_id}: inherited reason does not match failed photo stage"
                        )
                    if secondary:
                        raise MeasurementContractV2Error(
                            f"{photo_key} {metric_id}: inherited photo failure cannot have secondary reasons"
                        )
                else:
                    if terminal_stage != "metric.raw":
                        raise MeasurementContractV2Error(
                            f"{photo_key} {metric_id}: metric-origin unavailable must terminate at metric.raw"
                        )

            key = (photo_key, metric_id)
            if key in metrics:
                raise MeasurementContractV2Error(f"duplicate metric record {key!r}")
            metrics[key] = metric
            i += 1

        if seen_metric_ids != metric_ids:
            raise MeasurementContractV2Error(f"{photo_key}: metric population/order mismatch")

    if expected_index is not None and set(photos) != set(expected_index):
        missing = sorted(set(expected_index) - set(photos))
        extra = sorted(set(photos) - set(expected_index))
        raise MeasurementContractV2Error(
            f"photo population mismatch; missing={missing[:10]} extra={extra[:10]}"
        )
    return {"header": header, "photos": photos, "metrics": metrics}


def validate_file(
    path: Path,
    *,
    metric_catalogue: dict,
    reason_catalogues: list[dict],
    expected_identity: dict | None = None,
    expected_photos: list[dict] | None = None,
) -> dict:
    """Read canonical bytes and validate the full v2 contract."""
    return validate_contract(
        read_canonical(path),
        metric_catalogue=metric_catalogue,
        reason_catalogues=reason_catalogues,
        expected_identity=expected_identity,
        expected_photos=expected_photos,
    )
