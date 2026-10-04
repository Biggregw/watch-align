"""Engine-owned calibration outcome sidecar for measurement-contract v2.

This module does not decide measurement reliability and does not alter calibration.json. It records
what the existing calibration engine did with measurements that were already accepted by the shared
family decision core: genuine values are admitted and either retained or rejected as the documented
within-watch obvious-photo outlier; replica values remain stress evidence only and are excluded from
the genuine-derived envelope.
"""
from __future__ import annotations

import hashlib
import json
import math
from collections import Counter
from decimal import Decimal, InvalidOperation
from pathlib import Path

import catalogue_registry
import contracts

SCHEMA = "watch_align.calibration_outcome"
SCHEMA_VERSION = 1
DEFAULT_GROUPS = (
    ("development", "gen"),
    ("validation", "gen"),
    ("holdout", "gen"),
    ("development", "rep"),
    ("validation", "rep"),
)
ADMISSION_POLICY = {"id": "genuine_envelope_admission_v1", "version": 1}
CALIB_REASON_CATALOGUE = Path(__file__).resolve().parent / "catalogues" / "calib_reasons_v1.json"
_HEX = set("0123456789abcdef")


class CalibrationOutcomeError(contracts.ContractError):
    """Raised when accepted measurements cannot be reconciled with the existing engine result."""


def _sha256_text(value: object, where: str) -> str:
    text = str(value or "").strip().lower()
    if len(text) != 64 or any(c not in _HEX for c in text):
        raise CalibrationOutcomeError(f"{where}: lowercase SHA-256 required")
    return text


def _decimal(value: object, where: str) -> Decimal:
    if isinstance(value, bool) or not isinstance(value, (int, float, str)):
        raise CalibrationOutcomeError(f"{where}: finite numeric value required")
    try:
        out = Decimal(str(value))
    except InvalidOperation:
        raise CalibrationOutcomeError(f"{where}: invalid numeric value {value!r}") from None
    if not out.is_finite():
        raise CalibrationOutcomeError(f"{where}: finite numeric value required")
    return out


def _reason_catalogue() -> dict:
    return catalogue_registry.load_reason_catalogue(
        CALIB_REASON_CATALOGUE, expected_namespace="calib"
    )


def _catalogue_identity(catalogue: dict) -> dict:
    return {
        "id": catalogue["catalogue_id"],
        "version": catalogue["version"],
        "sha256": catalogue_registry.catalogue_hash(catalogue),
    }


def _common_identity(validated_contracts: dict[tuple[str, str], dict]) -> dict:
    if set(validated_contracts) != set(DEFAULT_GROUPS):
        missing = sorted(set(DEFAULT_GROUPS) - set(validated_contracts))
        extra = sorted(set(validated_contracts) - set(DEFAULT_GROUPS))
        raise CalibrationOutcomeError(
            f"validated contract groups mismatch; missing={missing} extra={extra}"
        )
    common = None
    for (partition, cls), validated in validated_contracts.items():
        header = validated.get("header") or {}
        if header.get("partition") != partition or header.get("class_label") != cls:
            raise CalibrationOutcomeError(f"contract header does not match group {(partition, cls)}")
        identity = {
            "snapshot_id": header.get("snapshot_id"),
            "model": header.get("claimed_model"),
            "family": header.get("family"),
            "measurement_fingerprint": header.get("measurement_fingerprint"),
        }
        if common is None:
            common = identity
        elif identity != common:
            raise CalibrationOutcomeError(
                f"measurement identity changes across contract groups: {identity!r} != {common!r}"
            )
    if common is None:
        raise CalibrationOutcomeError("no validated contracts supplied")
    _sha256_text(common["measurement_fingerprint"], "measurement_fingerprint")
    return common


def _calibration_metrics(calibration: dict) -> dict[str, dict]:
    metrics = calibration.get("metrics")
    if not isinstance(metrics, dict) or not metrics:
        raise CalibrationOutcomeError("calibration.metrics must be a non-empty object")
    out = {}
    for key, rec in metrics.items():
        if not isinstance(rec, dict):
            raise CalibrationOutcomeError(f"calibration metric {key!r}: object required")
        app_key = str(rec.get("app_key") or rec.get("metric") or key).strip()
        if not app_key or app_key in out:
            raise CalibrationOutcomeError(f"duplicate or empty calibration app_key {app_key!r}")
        rejected = rec.get("obvious_photo_outliers") or []
        if not isinstance(rejected, list):
            raise CalibrationOutcomeError(f"{app_key}: obvious_photo_outliers must be an array")
        declared = rec.get("obvious_photo_outliers_rejected", len(rejected))
        if isinstance(declared, bool) or not isinstance(declared, int) or declared != len(rejected):
            raise CalibrationOutcomeError(f"{app_key}: rejected-outlier count does not match evidence")
        retained = rec.get("genuine_photos")
        if isinstance(retained, bool) or not isinstance(retained, int) or retained < 0:
            raise CalibrationOutcomeError(f"{app_key}: genuine_photos must be a non-negative integer")
        out[app_key] = rec
    return out


def build_records(
    validated_contracts: dict[tuple[str, str], dict],
    calibration: dict,
    *,
    calibration_sha256: str,
) -> list[dict]:
    """Build one deterministic outcome record for every reliability-accepted measurement."""
    identity = _common_identity(validated_contracts)
    calibration_sha256 = _sha256_text(calibration_sha256, "calibration_sha256")
    if str(calibration.get("model") or "").strip() != str(identity["model"] or "").strip():
        raise CalibrationOutcomeError("calibration model does not match measurement contracts")
    if str(calibration.get("family") or "").strip() != str(identity["family"] or "").strip():
        raise CalibrationOutcomeError("calibration family does not match measurement contracts")

    reason_catalogue = _reason_catalogue()
    valid_reasons = {
        r["code"] for r in reason_catalogue["reasons"] if r.get("contract_valid")
    }
    metrics = _calibration_metrics(calibration)

    accepted = []
    all_metric_ids = set()
    for (partition, cls), validated in validated_contracts.items():
        photos = validated.get("photos") or {}
        rows = validated.get("metrics") or {}
        for (photo_key, metric_id), metric in rows.items():
            all_metric_ids.add(metric_id)
            if metric.get("state") != "accepted":
                continue
            photo = photos.get(photo_key)
            if not isinstance(photo, dict):
                raise CalibrationOutcomeError(f"{photo_key} {metric_id}: missing validated photo record")
            accepted.append({
                "photo_key": photo_key,
                "image_sha256": _sha256_text(photo.get("image_sha256"), f"{photo_key}.image_sha256"),
                "physical_watch_id": str(photo.get("physical_watch_id") or "").strip(),
                "partition": partition,
                "class_label": cls,
                "metric_id": metric_id,
                "eligible_value": metric.get("eligible_value"),
            })
    if not accepted:
        raise CalibrationOutcomeError("no reliability-accepted measurements found")
    if not all(r["physical_watch_id"] for r in accepted):
        raise CalibrationOutcomeError("accepted measurement has no physical_watch_id")
    if all_metric_ids != set(metrics):
        raise CalibrationOutcomeError(
            f"calibration/contract metric mismatch; contract={sorted(all_metric_ids)} calibration={sorted(metrics)}"
        )

    outliers = {}
    for metric_id, rec in metrics.items():
        for item in rec.get("obvious_photo_outliers") or []:
            if not isinstance(item, dict):
                raise CalibrationOutcomeError(f"{metric_id}: outlier evidence must be an object")
            token = (
                str(item.get("local_path") or "").strip(),
                _sha256_text(item.get("image_sha256"), f"{metric_id} outlier image_sha256"),
                metric_id,
            )
            if not token[0] or token in outliers:
                raise CalibrationOutcomeError(f"{metric_id}: duplicate or empty outlier identity {token!r}")
            outliers[token] = _decimal(item.get("value"), f"{metric_id} outlier value")

    records = [{
        "record_type": "header",
        "schema": SCHEMA,
        "schema_version": SCHEMA_VERSION,
        "snapshot_id": identity["snapshot_id"],
        "model": identity["model"],
        "family": identity["family"],
        "measurement_fingerprint": identity["measurement_fingerprint"],
        "calibration_sha256": calibration_sha256,
        "calibration_method": str(calibration.get("method") or ""),
        "calibration_state": str(calibration.get("state") or ""),
        "admission_policy": dict(ADMISSION_POLICY),
        "reason_catalogue": _catalogue_identity(reason_catalogue),
    }]

    consumed_outliers = set()
    gen_counts = Counter()
    retained_counts = Counter()
    rejected_counts = Counter()
    for item in sorted(accepted, key=lambda r: (r["class_label"], r["partition"], r["photo_key"], r["metric_id"])):
        if item["class_label"] not in {"gen", "rep"}:
            raise CalibrationOutcomeError(f"unsupported class_label {item['class_label']!r}")
        eligible = _decimal(item["eligible_value"], f"{item['photo_key']} {item['metric_id']} eligible_value")
        admission_state = "admitted" if item["class_label"] == "gen" else "excluded"
        admission_reason = None if admission_state == "admitted" else "calib.replica_stress_only"
        outcome = None
        outcome_reason = None
        if admission_state == "admitted":
            gen_counts[item["metric_id"]] += 1
            token = (item["photo_key"], item["image_sha256"], item["metric_id"])
            if token in outliers:
                if eligible != outliers[token]:
                    raise CalibrationOutcomeError(
                        f"{token}: outlier value {outliers[token]} does not match accepted value {eligible}"
                    )
                outcome = "rejected_outlier"
                outcome_reason = "calib.obvious_within_watch_outlier"
                consumed_outliers.add(token)
                rejected_counts[item["metric_id"]] += 1
            else:
                outcome = "retained"
                retained_counts[item["metric_id"]] += 1
        if admission_reason is not None and admission_reason not in valid_reasons:
            raise CalibrationOutcomeError(f"unregistered admission reason {admission_reason}")
        if outcome_reason is not None and outcome_reason not in valid_reasons:
            raise CalibrationOutcomeError(f"unregistered outcome reason {outcome_reason}")
        records.append({
            "record_type": "outcome",
            **item,
            "eligible_value": float(eligible),
            "admission_state": admission_state,
            "admission_reason_code": admission_reason,
            "outcome": outcome,
            "outcome_reason_code": outcome_reason,
        })

    if consumed_outliers != set(outliers):
        missing = sorted(set(outliers) - consumed_outliers)
        raise CalibrationOutcomeError(f"calibration outlier evidence did not join accepted measurements: {missing[:10]}")

    for metric_id, rec in metrics.items():
        expected_retained = rec["genuine_photos"]
        expected_rejected = rec.get("obvious_photo_outliers_rejected", 0)
        if retained_counts[metric_id] != expected_retained:
            raise CalibrationOutcomeError(
                f"{metric_id}: retained accepted count {retained_counts[metric_id]} != calibration genuine_photos {expected_retained}"
            )
        if rejected_counts[metric_id] != expected_rejected:
            raise CalibrationOutcomeError(
                f"{metric_id}: rejected count {rejected_counts[metric_id]} != calibration {expected_rejected}"
            )
        if gen_counts[metric_id] != expected_retained + expected_rejected:
            raise CalibrationOutcomeError(f"{metric_id}: accepted genuine admission count does not reconcile")
    return records


def validate_records(records: list[dict]) -> None:
    if not records or records[0].get("record_type") != "header":
        raise CalibrationOutcomeError("calibration outcome file requires one header first")
    catalogue = _reason_catalogue()
    valid = {r["code"] for r in catalogue["reasons"] if r.get("contract_valid")}
    if records[0].get("schema") != SCHEMA or records[0].get("schema_version") != SCHEMA_VERSION:
        raise CalibrationOutcomeError("unsupported calibration outcome schema/version")
    _sha256_text(records[0].get("measurement_fingerprint"), "measurement_fingerprint")
    _sha256_text(records[0].get("calibration_sha256"), "calibration_sha256")
    previous = None
    seen = set()
    for i, rec in enumerate(records[1:], start=2):
        required = {
            "record_type", "photo_key", "image_sha256", "physical_watch_id", "partition", "class_label",
            "metric_id", "eligible_value", "admission_state", "admission_reason_code", "outcome", "outcome_reason_code",
        }
        if not isinstance(rec, dict) or set(rec) != required or rec.get("record_type") != "outcome":
            raise CalibrationOutcomeError(f"record {i}: malformed outcome record")
        key = (rec["class_label"], rec["partition"], rec["photo_key"], rec["metric_id"])
        if previous is not None and key <= previous:
            raise CalibrationOutcomeError("outcome records must be strictly sorted")
        previous = key
        identity = (rec["photo_key"], rec["metric_id"])
        if identity in seen:
            raise CalibrationOutcomeError(f"duplicate outcome identity {identity!r}")
        seen.add(identity)
        _sha256_text(rec["image_sha256"], f"record {i}.image_sha256")
        _decimal(rec["eligible_value"], f"record {i}.eligible_value")
        class_label = rec["class_label"]
        if class_label == "gen":
            if rec["admission_state"] != "admitted":
                raise CalibrationOutcomeError(f"record {i}: genuine accepted measurement must be admitted")
        elif class_label == "rep":
            if rec["admission_state"] != "excluded":
                raise CalibrationOutcomeError(f"record {i}: replica accepted measurement must remain stress-only excluded")
        else:
            raise CalibrationOutcomeError(f"record {i}: unsupported class_label {class_label!r}")
        if rec["admission_state"] == "admitted":
            if rec["admission_reason_code"] is not None or rec["outcome"] not in {"retained", "rejected_outlier"}:
                raise CalibrationOutcomeError(f"record {i}: admitted outcome invariant failed")
            if rec["outcome"] == "retained" and rec["outcome_reason_code"] is not None:
                raise CalibrationOutcomeError(f"record {i}: retained outcome cannot have a reason")
            if rec["outcome"] == "rejected_outlier" and rec["outcome_reason_code"] != "calib.obvious_within_watch_outlier":
                raise CalibrationOutcomeError(f"record {i}: rejected outlier requires its registered reason")
        elif rec["admission_state"] == "excluded":
            if rec["admission_reason_code"] != "calib.replica_stress_only" or rec["outcome"] is not None or rec["outcome_reason_code"] is not None:
                raise CalibrationOutcomeError(f"record {i}: excluded stress-only invariant failed")
        else:
            raise CalibrationOutcomeError(f"record {i}: invalid admission_state")
        for code in (rec["admission_reason_code"], rec["outcome_reason_code"]):
            if code is not None and code not in valid:
                raise CalibrationOutcomeError(f"record {i}: unregistered calibration reason {code!r}")


def write_records(path: Path, records: list[dict]) -> None:
    validate_records(records)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("wb") as fh:
        for rec in records:
            fh.write(catalogue_registry.canonical_json_bytes(rec))


def read_canonical(path: Path) -> list[dict]:
    raw = path.read_bytes()
    if not raw or not raw.endswith(b"\n") or b"\r" in raw:
        raise CalibrationOutcomeError("calibration outcome JSONL must be non-empty canonical LF text")
    records = []
    for line_no, line in enumerate(raw.splitlines(keepends=True), start=1):
        try:
            rec = json.loads(line[:-1].decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise CalibrationOutcomeError(f"line {line_no}: invalid JSON") from exc
        if catalogue_registry.canonical_json_bytes(rec) != line:
            raise CalibrationOutcomeError(f"line {line_no}: non-canonical JSON")
        records.append(rec)
    validate_records(records)
    return records


def calibration_file_sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()
