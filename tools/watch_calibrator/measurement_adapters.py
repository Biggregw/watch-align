"""Fail-closed registry for calibration measurement adapters.

The generic calibration execution layer resolves an adapter by id and never contains watch-family
routing logic. Family-specific adapters own their geometry, layout and reliability semantics.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Callable

import production_measure
import submariner12_measurement_adapter


@dataclass(frozen=True)
class MeasurementAdapter:
    id: str
    version: str
    reliability_policy: str
    contract_schema_version: int | None
    measure: Callable

    def info(self) -> dict:
        return {
            "id": self.id,
            "version": self.version,
            "reliability_policy": self.reliability_policy,
            "contract_schema_version": self.contract_schema_version,
        }


_ADAPTERS = {
    "production_app_route_v1": MeasurementAdapter(
        id="production_app_route_v1",
        version="1",
        reliability_policy="legacy_production_gate_v1",
        contract_schema_version=None,
        measure=production_measure.measure,
    ),
    submariner12_measurement_adapter.ADAPTER_INFO["id"]: MeasurementAdapter(
        id=submariner12_measurement_adapter.ADAPTER_INFO["id"],
        version=submariner12_measurement_adapter.ADAPTER_INFO["version"],
        reliability_policy=submariner12_measurement_adapter.ADAPTER_INFO["reliability_policy"],
        contract_schema_version=submariner12_measurement_adapter.ADAPTER_INFO["contract_schema_version"],
        measure=submariner12_measurement_adapter.measure,
    ),
}


def resolve(adapter_id: str) -> MeasurementAdapter:
    key = str(adapter_id or "").strip()
    if key not in _ADAPTERS:
        raise ValueError(f"Unsupported measurement adapter {key!r}")
    return _ADAPTERS[key]


def supported_ids() -> set[str]:
    return set(_ADAPTERS)
