"""Explicit dial layouts for the phase-1 Submariner models.

The layout comes from the model, never from GMT date-side inference (GmtDialLayout):
  124060            triangle at 12, batons at 3, 6 and 9, round markers at 1 2 4 5 7 8 10 11
  126610LN/LV       triangle at 12, batons at 6 and 9, date window at 3, same eight round markers
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from harvester.families import SUBMARINER_12, family_for_model  # noqa: E402

ROUND_HOURS = (1, 2, 4, 5, 7, 8, 10, 11)
TRIANGLE_HOURS = (12,)

_LAYOUTS = {
    "124060": {"triangle": TRIANGLE_HOURS, "batons": (3, 6, 9), "date": (), "round": ROUND_HOURS},
    "126610LN": {"triangle": TRIANGLE_HOURS, "batons": (6, 9), "date": (3,), "round": ROUND_HOURS},
    "126610LV": {"triangle": TRIANGLE_HOURS, "batons": (6, 9), "date": (3,), "round": ROUND_HOURS},
}


class UnsupportedModel(ValueError):
    pass


def layout_for(model: str) -> dict:
    """Layout of a phase-1 Submariner. Anything outside submariner_12 (GMTs, 11-series) raises."""
    m = (model or "").strip().upper()
    fam = family_for_model(m)
    if fam is None or fam.key != SUBMARINER_12.key or m not in _LAYOUTS:
        raise UnsupportedModel(f"{model!r} is not a phase-1 submariner_12 model")
    return dict(_LAYOUTS[m])


def kind_at(model: str, hour: int) -> str:
    lay = layout_for(model)
    for kind, key in (("triangle", "triangle"), ("baton", "batons"), ("date", "date"), ("round", "round")):
        if hour in lay[key]:
            return kind
    raise ValueError(f"hour {hour} not in layout")


def expected_landmarks(model: str) -> list[str]:
    """Landmark ids the measurement driver reports for this model, in output order."""
    lay = layout_for(model)
    return ["12"] + [f"b{h}" for h in lay["batons"]] + [f"date{h}" for h in lay["date"]] + [f"r{h}" for h in lay["round"]]
