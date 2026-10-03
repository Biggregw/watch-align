"""Submariner 12-series measurement-contract v2 serializer.

This module is deliberately not registered as a live calibration adapter yet. It orchestrates the
v2 desktop decision-stream driver, joins frozen provenance/identity, and writes canonical JSONL.
It contains no watch geometry, threshold, gate, state, reason-precedence or layout-decision logic.
Phase 2C will add the frozen v1/v2 equivalence proof before this route can replace v1.
"""
from __future__ import annotations

import csv
import hashlib
import json
import math
import subprocess
from pathlib import Path

import catalogue_registry
import contracts
import measurement_contract_v2
import production_measure

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
HARNESS = REPO / "tools" / "desktop-harness" / "run.sh"

ADAPTER = {"id": "submariner12_measured_v2", "version": "2"}
RELIABILITY_POLICY = {"id": "sub124060_production_reliability", "version": 1}
FAMILY = "submariner_12"
MODEL = "124060"


class Submariner12V2Error(contracts.ContractError):
    """Raised when the v2 serializer cannot make an exact identity-preserving contract."""


def _safe_photo_key(value: str) -> str:
    text = str(value or "").replace("\\", "/").strip()
    p = Path(text)
    if not text or p.is_absolute() or ".." in p.parts:
        raise Submariner12V2Error(f"photo_key must be a safe manifest-relative path, got {value!r}")
    return text


def _manifest_rows(acq_root: Path) -> dict[str, dict[str, str]]:
    path = acq_root / "acquired_images.csv"
    out: dict[str, dict[str, str]] = {}
    with path.open(newline="", encoding="utf-8") as fh:
        for line_no, row in enumerate(csv.DictReader(fh), start=2):
            if (row.get("acquisition_status") or "acquired") != "acquired" or row.get("exact_duplicate_of"):
                continue
            local = _safe_photo_key(row.get("local_path") or "")
            sha = (row.get("sha256") or "").strip().lower()
            if len(sha) != 64 or any(c not in "0123456789abcdef" for c in sha):
                raise Submariner12V2Error(f"acquired images line {line_no}: invalid sha256")
            if local in out:
                raise Submariner12V2Error(f"acquired images line {line_no}: duplicate local_path {local!r}")
            out[local] = row
    return out


def requested_photos(acq_root: Path, split_csv: Path, partition: str, cls: str,
                     model: str = MODEL) -> list[dict]:
    """Requested photos with only frozen, workspace-independent contract identity/provenance."""
    model = contracts.exact_model(model, "v2 serializer model")
    manifest = _manifest_rows(acq_root)
    requested = []
    for wid, photo in production_measure.photo_list(acq_root, split_csv, partition, cls, model):
        try:
            local = _safe_photo_key(photo.relative_to(acq_root).as_posix())
        except ValueError as exc:
            raise Submariner12V2Error(f"photo is outside acquisition root: {photo}") from exc
        row = manifest.get(local)
        if row is None:
            raise Submariner12V2Error(f"requested photo missing from acquisition manifest: {local}")
        manifest_wid = (row.get("physical_watch_id") or row.get("candidate_id") or "").strip()
        if manifest_wid != wid:
            raise Submariner12V2Error(f"physical watch mismatch for {local}: {manifest_wid!r} != {wid!r}")
        requested.append({
            "photo_key": local,
            "image_sha256": (row.get("sha256") or "").strip().lower(),
            "physical_watch_id": wid,
            "workspace_path": photo,
            "provenance": {
                "source_type": (row.get("source_type") or "").strip(),
                "source_name": (row.get("source_name") or "").strip(),
                "source_url": (row.get("source_url") or "").strip(),
                "image_url": (row.get("image_url") or "").strip(),
                "listing_id": (row.get("listing_id") or "").strip(),
            },
        })
    return requested


def run_harness(model: str, requested: list[dict], out_jsonl: Path) -> None:
    """Invoke shared Java and request no class/partition/calibration-role input."""
    model = contracts.exact_model(model, "v2 decision-stream model")
    if model != MODEL:
        raise Submariner12V2Error(f"submariner12_measured_v2 currently supports only {MODEL}, got {model}")
    out_jsonl.parent.mkdir(parents=True, exist_ok=True)
    lst = out_jsonl.with_suffix(".list.tsv")
    with lst.open("w", encoding="utf-8", newline="\n") as fh:
        for item in requested:
            fh.write(f"{item['photo_key']}\t{item['physical_watch_id']}\t{item['workspace_path']}\n")
    subprocess.run(
        ["bash", str(HARNESS), "CalibDecisionV2", model, str(lst), str(out_jsonl)],
        cwd=REPO,
        check=True,
    )


def read_decision_stream(path: Path) -> list[dict]:
    """Read the Java transport stream. Canonical contract bytes are written later by Python."""
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        raise Submariner12V2Error(f"decision stream not readable: {path}") from exc
    out = []
    seen = set()
    for line_no, line in enumerate(lines, start=1):
        if not line.strip():
            raise Submariner12V2Error(f"decision stream line {line_no}: blank record")
        try:
            value = json.loads(
                line,
                parse_constant=lambda v: (_ for _ in ()).throw(
                    Submariner12V2Error(f"decision stream line {line_no}: non-finite number {v}")
                ),
            )
        except json.JSONDecodeError as exc:
            raise Submariner12V2Error(f"decision stream line {line_no}: invalid JSON: {exc}") from exc
        if not isinstance(value, dict):
            raise Submariner12V2Error(f"decision stream line {line_no}: object required")
        required = {"photo_key", "physical_watch_id", "model", "stages", "layout", "metrics"}
        if set(value) != required:
            raise Submariner12V2Error(
                f"decision stream line {line_no}: keys mismatch; expected {sorted(required)}, got {sorted(value)}"
            )
        key = _safe_photo_key(value["photo_key"])
        if key in seen:
            raise Submariner12V2Error(f"decision stream line {line_no}: duplicate photo_key {key!r}")
        seen.add(key)
        if contracts.exact_model(value.get("model"), f"decision stream line {line_no}") != MODEL:
            raise Submariner12V2Error(f"decision stream line {line_no}: wrong model")
        out.append(value)
    return out


def _checkout_sha() -> str:
    try:
        return subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=REPO, text=True, stderr=subprocess.DEVNULL
        ).strip().lower()
    except (OSError, subprocess.CalledProcessError) as exc:
        raise Submariner12V2Error("cannot resolve checkout SHA for measurement fingerprint") from exc


def measurement_fingerprint(checkout_sha: str | None = None) -> str:
    bundle = catalogue_registry.bundle_metadata()
    identity = {
        "adapter": ADAPTER,
        "reliability_policy": RELIABILITY_POLICY,
        "metric_catalogue": bundle["metric_catalogue"],
        "reason_catalogues": bundle["reason_catalogues"],
        "checkout_sha": (checkout_sha or _checkout_sha()).strip().lower(),
    }
    return hashlib.sha256(catalogue_registry.canonical_json_bytes(identity)).hexdigest()


def _reason_fields(reason: object) -> tuple[object, object, object]:
    if reason is None:
        return None, None, None
    if not isinstance(reason, dict):
        raise Submariner12V2Error("Java decision reason must be an object or null")
    return reason.get("code"), reason.get("subject"), reason.get("detail")


def build_records(config: dict, *, partition: str, cls: str, snapshot_id: str,
                  requested: list[dict], decisions: list[dict], checkout_sha: str | None = None) -> list[dict]:
    """Join Java decisions to frozen identities and return records in canonical contract order.

    State/reason/value/layout content is copied from Java. This function only performs identity joins,
    structural population checks, naming/flattening for the wire schema and deterministic ordering.
    """
    model = contracts.exact_model(config.get("model"), "v2 contract model")
    family = str(config.get("family") or "").strip()
    if model != MODEL or family != FAMILY:
        raise Submariner12V2Error(f"submariner12_measured_v2 requires {MODEL}/{FAMILY}, got {model}/{family}")
    if not snapshot_id or not isinstance(snapshot_id, str):
        raise Submariner12V2Error("snapshot_id is required")
    cls = str(cls or "").strip().lower()
    partition = str(partition or "").strip().lower()
    if not cls or not partition:
        raise Submariner12V2Error("partition and class label are required")

    expected_metrics = {str(m.get("app_key") or "").strip() for m in config.get("calibration_metrics") or []}
    if not expected_metrics or "" in expected_metrics:
        raise Submariner12V2Error("configured calibration metric IDs are required")

    by_request = {item["photo_key"]: item for item in requested}
    if len(by_request) != len(requested):
        raise Submariner12V2Error("requested photo_key values must be unique")
    by_decision = {item["photo_key"]: item for item in decisions}
    if len(by_decision) != len(decisions) or set(by_decision) != set(by_request):
        raise Submariner12V2Error("Java decision population does not exactly match requested photos")

    bundle = catalogue_registry.bundle_metadata()
    header = {
        "record_type": "header",
        "schema": measurement_contract_v2.SCHEMA,
        "schema_version": measurement_contract_v2.SCHEMA_VERSION,
        "snapshot_id": snapshot_id,
        "claimed_model": model,
        "family": family,
        "partition": partition,
        "class_label": cls,
        "adapter": dict(ADAPTER),
        "reliability_policy": dict(RELIABILITY_POLICY),
        "metric_catalogue": bundle["metric_catalogue"],
        "reason_catalogues": bundle["reason_catalogues"],
        "stage_map": list(measurement_contract_v2.PHOTO_STAGES) + list(measurement_contract_v2.METRIC_STAGES),
        "measurement_fingerprint": measurement_fingerprint(checkout_sha),
    }
    measurement_contract_v2.validate_header_foundation(header)
    records = [header]

    for photo_key in sorted(by_request):
        request = by_request[photo_key]
        decision = by_decision[photo_key]
        if decision.get("physical_watch_id") != request.get("physical_watch_id"):
            raise Submariner12V2Error(f"physical watch mismatch for {photo_key}")
        java_metrics = decision.get("metrics")
        if not isinstance(java_metrics, list):
            raise Submariner12V2Error(f"Java decision metrics must be a list for {photo_key}")
        metric_by_id = {m.get("metric_id"): m for m in java_metrics if isinstance(m, dict)}
        if len(metric_by_id) != len(java_metrics) or set(metric_by_id) != expected_metrics:
            raise Submariner12V2Error(f"Java decision metric population mismatch for {photo_key}")

        records.append({
            "record_type": "photo",
            "photo_key": _safe_photo_key(photo_key),
            "image_sha256": request["image_sha256"],
            "physical_watch_id": request["physical_watch_id"],
            "provenance": dict(request["provenance"]),
            "photo_stages": decision.get("stages"),
            "layout_evidence": decision.get("layout"),
        })
        for metric_id in sorted(metric_by_id):
            metric = metric_by_id[metric_id]
            reason_code, reason_subject, reason_detail = _reason_fields(metric.get("reason"))
            secondary = []
            for reason in metric.get("secondary_reasons") or []:
                code, subject, _detail = _reason_fields(reason)
                secondary.append({"code": code, "subject": subject})
            records.append({
                "record_type": "metric",
                "photo_key": photo_key,
                "metric_id": metric_id,
                "state": metric.get("state"),
                "terminal_stage": metric.get("terminal_stage"),
                "origin": metric.get("origin"),
                "reason_code": reason_code,
                "reason_subject": reason_subject,
                "secondary_codes": secondary,
                "reason_detail": reason_detail,
                "raw_value": metric.get("raw_value"),
                "eligible_value": metric.get("eligible_value"),
                "raw_support": metric.get("raw_support"),
                "eligible_support": metric.get("eligible_support"),
                "diagnostics": metric.get("diagnostics"),
            })
    return records


def write_contract(config: dict, *, partition: str, cls: str, snapshot_id: str,
                   requested: list[dict], decisions: list[dict], out_jsonl: Path,
                   checkout_sha: str | None = None) -> dict:
    records = build_records(
        config,
        partition=partition,
        cls=cls,
        snapshot_id=snapshot_id,
        requested=requested,
        decisions=decisions,
        checkout_sha=checkout_sha,
    )
    measurement_contract_v2.write_records(out_jsonl, records)
    reread = measurement_contract_v2.read_canonical(out_jsonl)
    if reread != records:
        raise Submariner12V2Error("canonical JSONL round-trip changed v2 records")
    return {
        "path": str(out_jsonl),
        "photos": len(requested),
        "metric_rows": len(records) - 1 - len(requested),
        "measurement_fingerprint": records[0]["measurement_fingerprint"],
    }


def measure_contract(config: dict, acq_root: Path, split_csv: Path, out_dir: Path,
                     partition: str, cls: str, snapshot_id: str,
                     checkout_sha: str | None = None) -> dict:
    """Produce v2 JSONL beside v1 without registering it as the calibration engine input."""
    model = contracts.exact_model(config.get("model"), "v2 contract model")
    requested = requested_photos(acq_root, split_csv, partition, cls, model)
    stem = out_dir / f"{model}_{partition}_{cls}_measurement_contract_v2"
    decision_stream = stem.with_suffix(".decision.jsonl")
    contract = stem.with_suffix(".jsonl")
    if requested:
        run_harness(model, requested, decision_stream)
        decisions = read_decision_stream(decision_stream)
    else:
        decision_stream.parent.mkdir(parents=True, exist_ok=True)
        decision_stream.write_text("", encoding="utf-8")
        decisions = []
    return write_contract(
        config,
        partition=partition,
        cls=cls,
        snapshot_id=snapshot_id,
        requested=requested,
        decisions=decisions,
        out_jsonl=contract,
        checkout_sha=checkout_sha,
    )
