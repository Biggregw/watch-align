"""Fail-closed contracts for the watch calibration pipeline.

These checks are intentionally independent of any watch family. They protect the calibration
platform from silent model substitution, malformed metric definitions and contradictory layouts.
Watch-specific measurement semantics belong in adapters, not here.
"""
from __future__ import annotations

import csv
import math
from pathlib import Path

SUPPORTED_ACQUISITION_ADAPTERS = {"submariner_acquire_v3"}
SUPPORTED_MEASUREMENT_ADAPTERS = {"production_app_route_v1"}
SUPPORTED_SIDEDNESS = {"two", "upper", "lower"}
LAYOUT_MARKER_KEYS = ("triangle", "batons", "rounds")


class ContractError(ValueError):
    """Raised when calibration input violates a fail-closed platform contract."""


def exact_model(value: object, where: str) -> str:
    model = str(value or "").strip().upper()
    if not model:
        raise ContractError(f"{where}: exact model is required")
    if any(c.isspace() for c in model):
        raise ContractError(f"{where}: invalid model {model!r}")
    return model


def _finite_nonnegative(value: object, where: str) -> float:
    try:
        x = float(value)
    except (TypeError, ValueError):
        raise ContractError(f"{where}: expected a finite non-negative number") from None
    if not math.isfinite(x) or x < 0:
        raise ContractError(f"{where}: expected a finite non-negative number")
    return x


def validate_config(config: dict, requested_model: str, config_path: Path | None = None) -> dict:
    """Validate the generic parts of a model config before discovery or measurement starts.

    Returns the same dict for convenient use by callers. No defaults that can change model
    identity, metric semantics or adapter routing are invented here.
    """
    if not isinstance(config, dict):
        raise ContractError("config: expected a JSON object")

    requested = exact_model(requested_model, "requested model")
    configured = exact_model(config.get("model"), "config model")
    if configured != requested:
        raise ContractError(f"config model {configured} does not match requested model {requested}")

    if config_path is not None:
        stem = config_path.stem.strip().upper()
        if stem != configured:
            raise ContractError(f"config filename model {stem} does not match config model {configured}")

    family = str(config.get("family") or "").strip()
    if not family:
        raise ContractError("config family is required")

    acq = str(config.get("acquisition_adapter") or "").strip()
    if acq not in SUPPORTED_ACQUISITION_ADAPTERS:
        raise ContractError(f"unsupported acquisition adapter {acq!r}")
    measure = str(config.get("measurement_adapter") or "").strip()
    if measure not in SUPPORTED_MEASUREMENT_ADAPTERS:
        raise ContractError(f"unsupported measurement adapter {measure!r}")

    layout = config.get("layout")
    if not isinstance(layout, dict):
        raise ContractError("layout must be an object")
    occupied: dict[int, str] = {}
    for key in LAYOUT_MARKER_KEYS:
        values = layout.get(key)
        if not isinstance(values, list):
            raise ContractError(f"layout.{key} must be a list")
        seen = set()
        for raw in values:
            if isinstance(raw, bool) or not isinstance(raw, int) or raw < 1 or raw > 12:
                raise ContractError(f"layout.{key} contains invalid hour {raw!r}")
            if raw in seen:
                raise ContractError(f"layout.{key} contains duplicate hour {raw}")
            seen.add(raw)
            if raw in occupied:
                raise ContractError(f"layout hour {raw} is declared as both {occupied[raw]} and {key}")
            occupied[raw] = key

    metrics = config.get("calibration_metrics")
    if not isinstance(metrics, list) or not metrics:
        raise ContractError("calibration_metrics must be a non-empty list")
    metric_ids, app_keys = set(), set()
    for i, metric in enumerate(metrics):
        where = f"calibration_metrics[{i}]"
        if not isinstance(metric, dict):
            raise ContractError(f"{where}: expected an object")
        metric_id = str(metric.get("metric") or "").strip()
        app_key = str(metric.get("app_key") or "").strip()
        if not metric_id or not app_key:
            raise ContractError(f"{where}: metric and app_key are required")
        if metric_id in metric_ids:
            raise ContractError(f"duplicate metric id {metric_id}")
        if app_key in app_keys:
            raise ContractError(f"duplicate app_key {app_key}")
        metric_ids.add(metric_id)
        app_keys.add(app_key)
        sided = str(metric.get("sided") or "").strip().lower()
        if sided not in SUPPORTED_SIDEDNESS:
            raise ContractError(f"{where}: unsupported sidedness {sided!r}")
        _finite_nonnegative(metric.get("minimum_half_width"), f"{where}.minimum_half_width")
        if not isinstance(metric.get("allow_pose_sensitive"), bool):
            raise ContractError(f"{where}.allow_pose_sensitive must be boolean")

    discovery = config.get("discovery")
    if not isinstance(discovery, dict):
        raise ContractError("discovery must be an object")
    diversity = discovery.get("genuine_source_diversity")
    if not isinstance(diversity, dict):
        raise ContractError("discovery.genuine_source_diversity must be an object")
    for key in ("minimum_sources", "minimum_watches_per_source", "minimum_acquired_watches"):
        value = diversity.get(key)
        if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
            raise ContractError(f"discovery.genuine_source_diversity.{key} must be a positive integer")
    share = diversity.get("max_single_source_share")
    try:
        share = float(share)
    except (TypeError, ValueError):
        raise ContractError("discovery.genuine_source_diversity.max_single_source_share must be between 0 and 1") from None
    if not math.isfinite(share) or not 0 < share <= 1:
        raise ContractError("discovery.genuine_source_diversity.max_single_source_share must be between 0 and 1")

    return config


def validate_csv_exact_model(path: Path, expected_model: str, label: str, *, allow_empty: bool = True) -> int:
    """Require every row in a pipeline CSV to carry the exact requested model."""
    expected = exact_model(expected_model, f"{label} expected model")
    if not path.exists():
        raise ContractError(f"{label}: missing file {path}")
    with path.open(newline="", encoding="utf-8") as fh:
        reader = csv.DictReader(fh)
        if not reader.fieldnames or "model" not in reader.fieldnames:
            raise ContractError(f"{label}: model column is required")
        count = 0
        for line_no, row in enumerate(reader, start=2):
            count += 1
            actual = exact_model(row.get("model"), f"{label} line {line_no}")
            if actual != expected:
                raise ContractError(
                    f"{label} line {line_no}: model {actual} does not match requested model {expected}"
                )
    if count == 0 and not allow_empty:
        raise ContractError(f"{label}: no rows")
    return count
