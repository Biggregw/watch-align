"""Watch-family configuration for dataset acquisition.

This module deliberately separates model membership from family-specific geometry and QC.
Adding a model here does not make the GMT harvester measure it with GMT assumptions.
"""
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class FamilyConfig:
    key: str
    display_name: str
    models: tuple[str, ...]
    unsupported_predecessors: tuple[str, ...] = ()
    date_models: tuple[str, ...] = ()
    no_date_models: tuple[str, ...] = ()
    acquisition_target_genuine: int = 0
    acquisition_target_replica: int = 0

    def contains(self, model: str) -> bool:
        return model.upper() in {m.upper() for m in self.models}


GMT_12 = FamilyConfig(
    key="gmt_12",
    display_name="Rolex GMT-Master II 12-series",
    models=(
        "126710BLNR", "126710BLRO", "126710GRNR", "126711CHNR", "126713GRNR",
        "126715CHNR", "126718GRNR", "126719BLRO", "126720VTNR", "126729VTNR",
    ),
    unsupported_predecessors=(
        "16710", "16713", "16718", "16760", "116710", "116713", "116718", "116719", "116759",
    ),
)


SUBMARINER_12 = FamilyConfig(
    key="submariner_12",
    display_name="Rolex Submariner 12-series",
    models=("124060", "126610LN", "126610LV"),
    unsupported_predecessors=("114060", "116610LN", "116610LV"),
    date_models=("126610LN", "126610LV"),
    no_date_models=("124060",),
    acquisition_target_genuine=15,
    acquisition_target_replica=20,
)


FAMILIES = {
    GMT_12.key: GMT_12,
    SUBMARINER_12.key: SUBMARINER_12,
}


def family_for_model(model: str) -> FamilyConfig | None:
    model = (model or "").upper()
    for family in FAMILIES.values():
        if family.contains(model):
            return family
    return None


def models_for_family(key: str) -> tuple[str, ...]:
    family = FAMILIES.get(key)
    return family.models if family else ()
