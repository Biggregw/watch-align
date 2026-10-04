"""Coverage funnel and evidence-sufficiency reporting for measurement contract v2.

Reporting is downstream-only. It joins already-validated measurement contracts with the engine-owned
calibration-outcome sidecar and the existing calibration result. It never changes measurement state,
reliability, admission, outlier handling, limits or verdicts.
"""
from __future__ import annotations

import json
from collections import Counter, defaultdict
from decimal import Decimal, InvalidOperation
from pathlib import Path

import calibration_outcome_v1
import catalogue_registry
import contracts

SCHEMA = "watch_align.coverage_funnel"
SCHEMA_VERSION = 1
DEDUP_POLICY = {"id": "exact_sha256_only", "version": 1}
GROUPS = calibration_outcome_v1.DEFAULT_GROUPS


class CoverageFunnelError(contracts.ContractError):
    """Raised when reporting inputs cannot be reconciled exactly."""


def _decimal(value: object, where: str) -> Decimal:
    if isinstance(value, bool) or not isinstance(value, (int, float, str)):
        raise CoverageFunnelError(f"{where}: finite numeric value required")
    try:
        out = Decimal(str(value))
    except InvalidOperation:
        raise CoverageFunnelError(f"{where}: invalid numeric value {value!r}") from None
    if not out.is_finite():
        raise CoverageFunnelError(f"{where}: finite numeric value required")
    return out


def _calibration_metrics(calibration: dict) -> dict[str, dict]:
    rows = calibration.get("metrics")
    if not isinstance(rows, dict) or not rows:
        raise CoverageFunnelError("calibration.metrics must be a non-empty object")
    out = {}
    for key, rec in rows.items():
        if not isinstance(rec, dict):
            raise CoverageFunnelError(f"calibration metric {key!r}: object required")
        metric_id = str(rec.get("app_key") or rec.get("metric") or key).strip()
        if not metric_id or metric_id in out:
            raise CoverageFunnelError(f"duplicate or empty calibration metric {metric_id!r}")
        out[metric_id] = rec
    return out


def _common_contract_identity(validated_contracts: dict[tuple[str, str], dict]) -> dict:
    if set(validated_contracts) != set(GROUPS):
        missing = sorted(set(GROUPS) - set(validated_contracts))
        extra = sorted(set(validated_contracts) - set(GROUPS))
        raise CoverageFunnelError(f"validated contract groups mismatch; missing={missing} extra={extra}")
    common = None
    for group in GROUPS:
        partition, cls = group
        validated = validated_contracts[group]
        header = validated.get("header") or {}
        if header.get("partition") != partition or header.get("class_label") != cls:
            raise CoverageFunnelError(f"contract header does not match group {group}")
        identity = {
            "snapshot_id": header.get("snapshot_id"),
            "model": header.get("claimed_model"),
            "family": header.get("family"),
            "measurement_fingerprint": header.get("measurement_fingerprint"),
        }
        if common is None:
            common = identity
        elif identity != common:
            raise CoverageFunnelError(f"measurement identity changes across groups: {identity!r} != {common!r}")
    return common or {}


def _outcome_index(records: list[dict], identity: dict) -> dict[tuple[str, str], dict]:
    calibration_outcome_v1.validate_records(records)
    header = records[0]
    for field in ("snapshot_id", "model", "family", "measurement_fingerprint"):
        expected = identity.get(field)
        if header.get(field) != expected:
            raise CoverageFunnelError(
                f"calibration outcome {field} {header.get(field)!r} != measurement {expected!r}"
            )
    out = {}
    for rec in records[1:]:
        key = (rec["photo_key"], rec["metric_id"])
        if key in out:
            raise CoverageFunnelError(f"duplicate calibration outcome {key!r}")
        out[key] = rec
    return out


def _photo_source(photo: dict) -> str:
    provenance = photo.get("provenance") or {}
    return str(provenance.get("source_name") or "").strip()


def _acquisition_index(acquired_rows: list[dict] | None) -> dict[str, dict]:
    out = {}
    for i, row in enumerate(acquired_rows or []):
        if not isinstance(row, dict):
            raise CoverageFunnelError(f"acquired_rows[{i}]: object required")
        key = str(row.get("local_path") or "").strip().replace("\\", "/")
        if not key:
            continue
        if key in out:
            raise CoverageFunnelError(f"duplicate acquisition local_path {key!r}")
        out[key] = row
    return out


def _acquisition_summary(acquired_rows: list[dict] | None, candidate_summary_rows: list[dict] | None) -> dict:
    acquired_rows = list(acquired_rows or [])
    candidate_summary_rows = list(candidate_summary_rows or [])
    acquired = [r for r in acquired_rows if (r.get("acquisition_status") or "acquired") == "acquired"]
    unique = [r for r in acquired if not (r.get("exact_duplicate_of") or "").strip()]
    status_counts = Counter(str(r.get("acquisition_status") or "").strip() or "unknown" for r in candidate_summary_rows)
    return {
        "stage0_acquisition_ledger_available": bool(candidate_summary_rows),
        "candidate_rows": len(candidate_summary_rows) if candidate_summary_rows else None,
        "candidate_status_counts": dict(sorted(status_counts.items())) if candidate_summary_rows else None,
        "candidates_with_images": (
            sum(int(r.get("images_acquired") or 0) > 0 for r in candidate_summary_rows)
            if candidate_summary_rows else None
        ),
        "candidates_without_images": (
            sum(int(r.get("images_acquired") or 0) == 0 for r in candidate_summary_rows)
            if candidate_summary_rows else None
        ),
        "acquired_manifest_rows": len(acquired_rows),
        "acquired_image_rows": len(acquired),
        "exact_duplicate_rows": sum(bool((r.get("exact_duplicate_of") or "").strip()) for r in acquired),
        "effective_acquired_images": len(unique),
        "effective_acquired_watches": len({
            str(r.get("physical_watch_id") or "").strip() for r in unique
            if str(r.get("physical_watch_id") or "").strip()
        }),
        "dedup_policy": dict(DEDUP_POLICY),
    }


def _stage_counts(photos: dict[str, dict]) -> dict:
    out: dict[str, Counter] = defaultdict(Counter)
    layout = Counter()
    for photo in photos.values():
        for stage in photo.get("photo_stages") or []:
            out[str(stage.get("stage") or "")][str(stage.get("outcome") or "")] += 1
        evidence = photo.get("layout_evidence") or {}
        layout[str(evidence.get("compatibility")) if evidence.get("compatibility") is not None else "blocked"] += 1
    return {
        "photo_stages": {stage: dict(sorted(counts.items())) for stage, counts in sorted(out.items())},
        "layout_compatibility": dict(sorted(layout.items())),
    }


def _metric_group(
    metric_id: str,
    partition: str,
    cls: str,
    validated: dict,
    outcomes: dict[tuple[str, str], dict],
) -> dict:
    photos = validated.get("photos") or {}
    metric_rows = {
        key: rec for key, rec in (validated.get("metrics") or {}).items()
        if key[1] == metric_id
    }
    if len(metric_rows) != len(photos):
        raise CoverageFunnelError(
            f"{partition}/{cls} {metric_id}: metric population {len(metric_rows)} != photos {len(photos)}"
        )

    states = Counter()
    reasons = Counter()
    raw_photos = set()
    accepted_photos = set()
    raw_watches = set()
    accepted_watches = set()
    admitted = excluded = retained = rejected = 0

    for (photo_key, _), metric in metric_rows.items():
        photo = photos.get(photo_key)
        if not isinstance(photo, dict):
            raise CoverageFunnelError(f"{partition}/{cls} {metric_id}: missing photo {photo_key!r}")
        state = str(metric.get("state") or "")
        if state not in {"accepted", "withheld", "unavailable"}:
            raise CoverageFunnelError(f"{photo_key} {metric_id}: invalid state {state!r}")
        states[state] += 1
        wid = str(photo.get("physical_watch_id") or "").strip()
        if state in {"accepted", "withheld"}:
            raw_photos.add(photo_key)
            if wid:
                raw_watches.add(wid)
        if state == "accepted":
            accepted_photos.add(photo_key)
            if wid:
                accepted_watches.add(wid)
            outcome = outcomes.get((photo_key, metric_id))
            if outcome is None:
                raise CoverageFunnelError(f"{photo_key} {metric_id}: accepted measurement has no calibration outcome")
            if outcome.get("partition") != partition or outcome.get("class_label") != cls:
                raise CoverageFunnelError(f"{photo_key} {metric_id}: outcome group mismatch")
            if str(outcome.get("physical_watch_id") or "") != wid:
                raise CoverageFunnelError(f"{photo_key} {metric_id}: outcome physical watch mismatch")
            if _decimal(outcome.get("eligible_value"), f"{photo_key} {metric_id} outcome") != _decimal(
                metric.get("eligible_value"), f"{photo_key} {metric_id} metric"
            ):
                raise CoverageFunnelError(f"{photo_key} {metric_id}: outcome eligible value mismatch")
            if outcome["admission_state"] == "admitted":
                admitted += 1
                if outcome["outcome"] == "retained":
                    retained += 1
                elif outcome["outcome"] == "rejected_outlier":
                    rejected += 1
                else:
                    raise CoverageFunnelError(f"{photo_key} {metric_id}: invalid admitted calibration outcome")
            elif outcome["admission_state"] == "excluded":
                excluded += 1
            else:
                raise CoverageFunnelError(f"{photo_key} {metric_id}: invalid admission state")
        else:
            reason = str(metric.get("reason_code") or "").strip()
            if not reason:
                raise CoverageFunnelError(f"{photo_key} {metric_id}: non-accepted metric has no reason")
            reasons[reason] += 1
            if (photo_key, metric_id) in outcomes:
                raise CoverageFunnelError(f"{photo_key} {metric_id}: non-accepted metric has calibration outcome")

    population = len(metric_rows)
    if population != states["accepted"] + states["withheld"] + states["unavailable"]:
        raise CoverageFunnelError(f"{partition}/{cls} {metric_id}: reliability population does not reconcile")
    if states["accepted"] != admitted + excluded:
        raise CoverageFunnelError(f"{partition}/{cls} {metric_id}: accepted != admitted + excluded")
    if cls == "gen":
        if excluded:
            raise CoverageFunnelError(f"{partition}/{cls} {metric_id}: genuine accepted measurement was excluded")
        if admitted != retained + rejected:
            raise CoverageFunnelError(f"{partition}/{cls} {metric_id}: admitted genuine != retained + rejected")
    elif cls == "rep":
        if admitted or retained or rejected:
            raise CoverageFunnelError(f"{partition}/{cls} {metric_id}: replica stress evidence entered genuine calibration")

    return {
        "partition": partition,
        "class_label": cls,
        "population": population,
        "photos_with_raw": len(raw_photos),
        "accepted": states["accepted"],
        "withheld": states["withheld"],
        "unavailable": states["unavailable"],
        "watches_with_raw": len(raw_watches),
        "watches_with_accepted": len(accepted_watches),
        "admitted": admitted,
        "excluded": excluded,
        "retained": retained,
        "rejected_outlier": rejected,
        "reason_counts": dict(sorted(reasons.items())),
        **_stage_counts(photos),
    }


def _source_watch_counts(metric_id: str, validated_contracts: dict, outcomes: dict) -> dict[str, set[str]]:
    by_source: dict[str, set[str]] = defaultdict(set)
    for (partition, cls), validated in validated_contracts.items():
        if cls != "gen":
            continue
        photos = validated.get("photos") or {}
        for (photo_key, mid), metric in (validated.get("metrics") or {}).items():
            if mid != metric_id or metric.get("state") != "accepted":
                continue
            outcome = outcomes[(photo_key, metric_id)]
            if outcome.get("admission_state") != "admitted":
                continue
            photo = photos[photo_key]
            source = _photo_source(photo)
            wid = str(photo.get("physical_watch_id") or "").strip()
            if source and wid:
                by_source[source].add(wid)
    return by_source


def _partition_support(metric_id: str, validated_contracts: dict, outcomes: dict) -> dict:
    result = {}
    for partition in ("development", "validation", "holdout"):
        validated = validated_contracts[(partition, "gen")]
        photos = validated.get("photos") or {}
        rows = []
        for (photo_key, mid), metric in (validated.get("metrics") or {}).items():
            if mid != metric_id or metric.get("state") != "accepted":
                continue
            outcome = outcomes[(photo_key, metric_id)]
            if outcome.get("admission_state") != "admitted":
                continue
            rows.append((photo_key, str(photos[photo_key].get("physical_watch_id") or "").strip(), outcome))
        result[partition] = {
            "accepted_admitted_photos": len(rows),
            "accepted_admitted_watches": len({wid for _p, wid, _o in rows if wid}),
            "retained_photos": sum(o.get("outcome") == "retained" for _p, _w, o in rows),
            "rejected_outlier_photos": sum(o.get("outcome") == "rejected_outlier" for _p, _w, o in rows),
        }
    return result


def _calibration_sufficiency(metric_id: str, rec: dict, config: dict,
                             validated_contracts: dict, outcomes: dict) -> dict:
    by_source = _source_watch_counts(metric_id, validated_contracts, outcomes)
    source_counts = {source: len(watches) for source, watches in sorted(by_source.items())}

    all_watches = set()
    photo_counts = Counter()
    retained = rejected = 0
    for (partition, cls), validated in validated_contracts.items():
        if cls != "gen":
            continue
        photos = validated.get("photos") or {}
        for (photo_key, mid), metric in (validated.get("metrics") or {}).items():
            if mid != metric_id or metric.get("state") != "accepted":
                continue
            outcome = outcomes[(photo_key, metric_id)]
            if outcome.get("admission_state") != "admitted":
                continue
            wid = str(photos[photo_key].get("physical_watch_id") or "").strip()
            if wid:
                all_watches.add(wid)
                photo_counts[wid] += 1
            retained += outcome.get("outcome") == "retained"
            rejected += outcome.get("outcome") == "rejected_outlier"

    policy = config.get("calibration_policy") or {}
    diversity = (config.get("discovery") or {}).get("genuine_source_diversity") or {}
    min_watches = int(policy.get("genuine_envelope_min_watches", 8))
    min_sources = int(policy.get("genuine_envelope_min_sources", min(3, int(diversity.get("minimum_sources", 3)))))
    qualifying_min = int(diversity.get("minimum_watches_per_source", 1))
    qualifying_sources = sorted(source for source, count in source_counts.items() if count >= qualifying_min)
    dominant_share = (max(source_counts.values()) / len(all_watches)) if all_watches and source_counts else None
    max_share = diversity.get("max_single_source_share")
    source_dominance_ok = (
        dominant_share <= float(max_share)
        if dominant_share is not None and max_share is not None
        else None
    )

    declared_watches = rec.get("genuine_watches")
    declared_sources = rec.get("genuine_sources")
    if declared_watches is not None and int(declared_watches) != len(all_watches):
        raise CoverageFunnelError(
            f"{metric_id}: report contributing watches {len(all_watches)} != calibration {declared_watches}"
        )
    if declared_sources is not None and int(declared_sources) != len(source_counts):
        raise CoverageFunnelError(
            f"{metric_id}: report contributing sources {len(source_counts)} != calibration {declared_sources}"
        )
    if int(rec.get("genuine_photos", -1)) != retained:
        raise CoverageFunnelError(f"{metric_id}: retained-photo count does not match calibration")
    if int(rec.get("obvious_photo_outliers_rejected", 0)) != rejected:
        raise CoverageFunnelError(f"{metric_id}: rejected-outlier count does not match calibration")

    return {
        "status": rec.get("status"),
        "contributing_watches": len(all_watches),
        "retained_photos": retained,
        "rejected_outlier_photos": rejected,
        "contributing_sources": len(source_counts),
        "watches_by_source": source_counts,
        "qualifying_sources": qualifying_sources,
        "dominant_source_share_by_watches": dominant_share,
        "multi_photo_watches": sum(count > 1 for count in photo_counts.values()),
        "partition_support": _partition_support(metric_id, validated_contracts, outcomes),
        "criteria": {
            "engine_min_watches": min_watches,
            "engine_min_watches_met": len(all_watches) >= min_watches,
            "engine_min_sources": min_sources,
            "engine_min_sources_met": len(source_counts) >= min_sources,
            "acquisition_min_watches_per_source": qualifying_min,
            "acquisition_max_single_source_share": max_share,
            "acquisition_source_dominance_met": source_dominance_ok,
        },
        "engine_calibration_sufficient": (
            rec.get("status") == "CALIBRATED_GENUINE_ENVELOPE"
            and len(all_watches) >= min_watches
            and len(source_counts) >= min_sources
        ),
    }


def _qc_usefulness(metric_id: str, rec: dict, validated_contracts: dict,
                   outcomes: dict, acquired_index: dict[str, dict]) -> dict:
    watches = set()
    photos = set()
    factories = set()
    for (partition, cls), validated in validated_contracts.items():
        if cls != "rep":
            continue
        photo_map = validated.get("photos") or {}
        for (photo_key, mid), metric in (validated.get("metrics") or {}).items():
            if mid != metric_id or metric.get("state") != "accepted":
                continue
            outcome = outcomes[(photo_key, metric_id)]
            if outcome.get("admission_state") != "excluded":
                raise CoverageFunnelError(f"{photo_key} {metric_id}: replica accepted row not stress-only excluded")
            photos.add(photo_key)
            wid = str(photo_map[photo_key].get("physical_watch_id") or "").strip()
            if wid:
                watches.add(wid)
            factory = str((acquired_index.get(photo_key) or {}).get("factory") or "").strip()
            if factory:
                factories.add(factory)

    stress = rec.get("replica_stress") or {}
    declared_photos = stress.get("photos")
    declared_watches = stress.get("watches")
    if declared_photos is not None and int(declared_photos) != len(photos):
        raise CoverageFunnelError(
            f"{metric_id}: accepted replica photos {len(photos)} != calibration replica stress {declared_photos}"
        )
    if declared_watches is not None and int(declared_watches) != len(watches):
        raise CoverageFunnelError(
            f"{metric_id}: accepted replica watches {len(watches)} != calibration replica stress {declared_watches}"
        )
    return {
        "accepted_replica_photos": len(photos),
        "accepted_replica_watches": len(watches),
        "factories": sorted(factories),
        "factory_count": len(factories),
        "factory_coverage_available": bool(acquired_index),
        "outside_clear_photos": stress.get("outside_clear_photos"),
        "outside_check_photos": stress.get("outside_check_photos"),
        "outside_clear_rate": stress.get("outside_clear_rate"),
        "outside_check_rate": stress.get("outside_check_rate"),
        "utility": rec.get("utility"),
        "note": "Replica evidence is stress/usefulness evidence only and never moves genuine-derived limits.",
    }


def build_report(
    validated_contracts: dict[tuple[str, str], dict],
    outcome_records: list[dict],
    calibration: dict,
    config: dict,
    *,
    acquired_rows: list[dict] | None = None,
    candidate_summary_rows: list[dict] | None = None,
) -> dict:
    """Build a reconciled report without feeding anything back into calibration."""
    identity = _common_contract_identity(validated_contracts)
    outcomes = _outcome_index(outcome_records, identity)
    metrics = _calibration_metrics(calibration)
    acquired_index = _acquisition_index(acquired_rows)

    all_contract_metrics = set()
    accepted_keys = set()
    for validated in validated_contracts.values():
        for key, metric in (validated.get("metrics") or {}).items():
            all_contract_metrics.add(key[1])
            if metric.get("state") == "accepted":
                accepted_keys.add(key)
    if all_contract_metrics != set(metrics):
        raise CoverageFunnelError(
            f"contract/calibration metric mismatch; contract={sorted(all_contract_metrics)} calibration={sorted(metrics)}"
        )
    if set(outcomes) != accepted_keys:
        missing = sorted(accepted_keys - set(outcomes))[:10]
        extra = sorted(set(outcomes) - accepted_keys)[:10]
        raise CoverageFunnelError(f"accepted/outcome identity mismatch; missing={missing} extra={extra}")

    metric_reports = {}
    for metric_id in sorted(metrics):
        groups = [
            _metric_group(metric_id, partition, cls, validated_contracts[(partition, cls)], outcomes)
            for partition, cls in GROUPS
        ]
        totals = Counter()
        for group in groups:
            for key in ("population", "photos_with_raw", "accepted", "withheld", "unavailable",
                        "admitted", "excluded", "retained", "rejected_outlier"):
                totals[key] += group[key]
        if totals["population"] != totals["accepted"] + totals["withheld"] + totals["unavailable"]:
            raise CoverageFunnelError(f"{metric_id}: aggregate reliability population does not reconcile")
        if totals["accepted"] != totals["admitted"] + totals["excluded"]:
            raise CoverageFunnelError(f"{metric_id}: aggregate accepted != admitted + excluded")

        rec = metrics[metric_id]
        metric_reports[metric_id] = {
            "coverage": {
                "totals": dict(totals),
                "groups": groups,
            },
            "calibration_sufficiency": _calibration_sufficiency(
                metric_id, rec, config, validated_contracts, outcomes
            ),
            "qc_usefulness": _qc_usefulness(
                metric_id, rec, validated_contracts, outcomes, acquired_index
            ),
        }

    return {
        "schema": SCHEMA,
        "schema_version": SCHEMA_VERSION,
        "snapshot_id": identity.get("snapshot_id"),
        "model": identity.get("model"),
        "family": identity.get("family"),
        "measurement_fingerprint": identity.get("measurement_fingerprint"),
        "calibration_sha256": outcome_records[0].get("calibration_sha256"),
        "calibration_state": calibration.get("state"),
        "dedup_policy": dict(DEDUP_POLICY),
        "acquisition": _acquisition_summary(acquired_rows, candidate_summary_rows),
        "metrics": metric_reports,
        "report_only": True,
    }


def write_report(path: Path, report: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(catalogue_registry.canonical_json_bytes(report))


def read_canonical(path: Path) -> dict:
    raw = path.read_bytes()
    if not raw or not raw.endswith(b"\n") or b"\r" in raw:
        raise CoverageFunnelError("coverage report must be non-empty canonical LF JSON")
    try:
        report = json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise CoverageFunnelError("coverage report is invalid JSON") from exc
    if catalogue_registry.canonical_json_bytes(report) != raw:
        raise CoverageFunnelError("coverage report is not canonical JSON")
    if report.get("schema") != SCHEMA or report.get("schema_version") != SCHEMA_VERSION:
        raise CoverageFunnelError("unsupported coverage report schema/version")
    return report
