from __future__ import annotations

from typing import Any

V110_VERSION = "1.1.1"


def _rank(value: str | None) -> int:
    return {"low": 0, "medium": 1, "high": 2}.get(str(value or "low").lower(), 0)


def _label(score: float) -> str:
    return "high" if score >= 1.5 else "medium" if score >= 0.75 else "low"


def perspective_suitability(perspective: dict[str, Any]) -> str:
    mismatch = perspective.get("mismatch_deg")
    mismatch = float(mismatch) if mismatch is not None else 0.0
    tilts = []
    for key in ("candidate", "reference"):
        item = perspective.get(key) or {}
        if item.get("available") and item.get("tilt_deg") is not None:
            tilts.append(float(item["tilt_deg"]))
    max_tilt = max(tilts) if tilts else 0.0
    if mismatch >= 10.0 or max_tilt >= 28.0:
        return "low"
    if mismatch >= 4.0 or max_tilt >= 14.0:
        return "medium"
    return "high"


def region_confidence(markers, marker_summary, perspective, bezel, date):
    marker_count = int(marker_summary.get("count", 0))
    dial = "high" if marker_count >= 10 else "medium" if marker_count >= 7 else "low"
    result = {
        "dial": dial,
        "markers": dial,
        "bezel": bezel.get("confidence", "low"),
        "perspective": (perspective.get("candidate") or {}).get("confidence", "low"),
    }
    if date is not None:
        result["date/cyclops"] = date.get("confidence", "low")
    return result


def visual_alignment_confidence(metrics: dict[str, Any], mode: str) -> str:
    regions = metrics.get("region_confidence", {})
    dial = _rank(regions.get("dial"))
    markers = _rank(regions.get("markers"))
    if mode == "gen":
        base = _rank((metrics.get("base_alignment") or {}).get("confidence", "medium"))
        score = base * 0.50 + dial * 0.25 + markers * 0.25
    else:
        score = (dial + markers) / 2.0
    return _label(score)


def gate_measurements(metrics: dict[str, Any], mode: str) -> None:
    regions = metrics.get("region_confidence", {})
    perspective = metrics.get("perspective", {})
    perspective["confidence_meaning"] = "Confidence in the distortion estimate, not camera-angle quality"
    perspective["measurement_label"] = "Perspective distortion estimate"
    suitability = perspective_suitability(perspective)
    visual = visual_alignment_confidence(metrics, mode)

    metrics["visual_alignment_confidence"] = visual
    metrics["overall_confidence"] = visual
    metrics["perspective_suitability"] = suitability

    withheld: list[str] = []

    def gate(item: dict[str, Any] | None, region: str, fields, perspective_sensitive: bool = False):
        if item is None:
            return
        reliable = (
            visual in {"high", "medium"}
            and regions.get(region) in {"high", "medium"}
            and item.get("confidence") in {"high", "medium"}
            and item.get("available", False)
            and (not perspective_sensitive or suitability in {"high", "medium"})
        )
        item["reliable"] = bool(reliable)
        if not reliable:
            item["available"] = False
            item["reason"] = (
                "Measurement withheld: perspective is unsuitable for this region"
                if perspective_sensitive and suitability == "low"
                else "Measurement withheld: low or unavailable feature confidence"
            )
            for field in fields:
                item[field] = None
            withheld.append(region)

    for marker in metrics.get("markers", []):
        gate(marker, "markers", ("angular_error_deg", "radial_error_percent"), False)
    gate(metrics.get("bezel"), "bezel", ("offset_deg",), True)
    gate(metrics.get("date_window"), "date/cyclops", ("x_offset_percent", "y_offset_percent", "bbox"), True)

    if regions.get("markers") not in {"high", "medium"} or visual == "low":
        summary = metrics.get("marker_summary", {})
        for field in ("overall_rotation_deg", "median_abs_marker_error_deg", "ring_radius_ratio"):
            summary[field] = None
        summary["available"] = False

    reliable_regions = [k for k in ("markers", "bezel", "date/cyclops") if k not in set(withheld)]
    metrics["measurement_reliability"] = (
        "high" if not withheld and visual == "high" and suitability == "high"
        else "medium" if reliable_regions
        else "low"
    )

    if suitability == "low":
        metrics["measurement_warning"] = (
            f"Visual alignment confidence is {visual.upper()}, but the photos have substantial perspective distortion or mismatch. "
            "Perspective-sensitive bezel/date measurements are withheld; this does not mean the watch itself is poorly aligned."
        )
    elif withheld:
        metrics["measurement_warning"] = (
            "Some measurements are withheld because their own region/feature confidence is low. "
            "This does not reduce otherwise strong visual alignment confidence."
        )
    else:
        metrics["measurement_warning"] = None

    metrics.setdefault("base_alignment", {})["measurements_reliable"] = metrics["measurement_reliability"] != "low"


def install(v1_full_module) -> None:
    v1_full_module.V1_FULL_VERSION = V110_VERSION
    v1_full_module.region_confidence = region_confidence
    v1_full_module.gate_measurements = gate_measurements

    v1_full_module.V1_HTML = v1_full_module.V1_HTML.replace("V1 1.0.0", "V1 1.1.1")
    v1_full_module.V1_HTML = v1_full_module.V1_HTML.replace(
        "Required until a verified built-in reference is installed for this model",
        "Optional — Watch Align automatically uses an official Rolex reference when available"
    )
    js = v1_full_module.V1_JS
    js = js.replace("metric('Overall confidence',r.metrics.overall_confidence,confClass(r.metrics.overall_confidence))", "metric('Visual alignment confidence',r.metrics.visual_alignment_confidence||r.metrics.overall_confidence,confClass(r.metrics.visual_alignment_confidence||r.metrics.overall_confidence))+metric('Perspective suitability',r.metrics.perspective_suitability||'n/a',confClass(r.metrics.perspective_suitability||'low'))+metric('Measurement reliability',r.metrics.measurement_reliability||'n/a',confClass(r.metrics.measurement_reliability||'low'))")
    js = js.replace("metric('Apparent camera tilt',`${p.candidate.tilt_deg}°`,p.warning?'warn':'')", "metric('Perspective distortion estimate',`${p.candidate.tilt_deg}° equivalent`,p.warning?'warn':'')")
    v1_full_module.V1_JS = js
