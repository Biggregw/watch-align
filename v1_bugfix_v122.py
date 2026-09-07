from __future__ import annotations

import json
import math
from pathlib import Path
from typing import Any

import cv2

V122_VERSION = "1.2.2"

SUBMARINER_SOURCE = {
    "variant": "Oyster",
    "model_code": "m124060-0001",
    "page": "https://www.rolex.com/en-gb/watches/submariner/m124060-0001",
    "brochure": "https://assets.rolex.com/api/brochure/en/submariner/m124060-0001.pdf",
    "brand": "Rolex",
    "reference": "124060",
}


def _perspective_aware_score(backend, perspective_diagnostics, candidate_image, ref_image) -> float:
    def sig(img):
        h, w = img.shape[:2]
        c = backend.detect_watch_circle(img)
        if c is None:
            return (0.5, 0.5, 0.25, w / max(h, 1), 45.0)
        cx, cy, r = map(float, c)
        try:
            p = perspective_diagnostics(img, (cx, cy, r)) or {}
            tilt = float(p.get("tilt_deg", 45.0)) if p.get("available") else 45.0
        except Exception:
            tilt = 45.0
        return (cx / max(w, 1), cy / max(h, 1), r / max(min(w, h), 1), w / max(h, 1), tilt)

    a, b = sig(candidate_image), sig(ref_image)
    framing = abs(a[0]-b[0])*2.0 + abs(a[1]-b[1])*2.0 + abs(a[2]-b[2])*3.0 + abs(a[3]-b[3])*0.35
    perspective = abs(a[4]-b[4]) / 12.0
    return float(framing + perspective)


def _marker_disagreement(results: list[dict[str, Any]]) -> dict[str, Any]:
    per_hour: dict[int, list[float]] = {}
    for result in results:
        for marker in result.get("metrics", {}).get("markers", []) or []:
            if marker.get("reliable") is False or marker.get("available") is False:
                continue
            value = marker.get("angular_error_deg")
            if value is None:
                continue
            per_hour.setdefault(int(marker.get("hour", 0)), []).append(float(value))
    disagreements = []
    for hour, values in sorted(per_hour.items()):
        if len(values) < 2:
            continue
        spread = max(values) - min(values)
        if spread >= 1.5:
            disagreements.append({"hour": hour, "spread_deg": round(spread, 2), "values": [round(v, 2) for v in values]})
    return {"present": bool(disagreements), "items": disagreements}


def _consensus(results: list[dict[str, Any]]) -> dict[str, Any]:
    if len(results) < 2:
        return {"level": "n/a", "summary": "Single-reference comparison; multi-reference consensus is not available yet.", "detail": "1 suitable reference", "marker_disagreement": False}
    ranks = {"low": 0, "medium": 1, "high": 2}
    visuals = [str(r.get("metrics", {}).get("visual_alignment_confidence") or r.get("metrics", {}).get("overall_confidence") or "low") for r in results]
    avg = sum(ranks.get(v, 0) for v in visuals) / len(visuals)
    marker_check = _marker_disagreement(results)

    bezel_vals = []
    date_vals = []
    for r in results:
        m = r.get("metrics", {})
        b = m.get("bezel") or {}
        if b.get("reliable") is not False and b.get("offset_deg") is not None:
            bezel_vals.append(float(b["offset_deg"]))
        d = m.get("date_window") or {}
        if d.get("reliable") is not False and d.get("x_offset_percent") is not None and d.get("y_offset_percent") is not None:
            date_vals.append((float(d["x_offset_percent"]), float(d["y_offset_percent"])))

    inconsistent = marker_check["present"]
    if len(bezel_vals) >= 2 and max(bezel_vals)-min(bezel_vals) >= 1.5:
        inconsistent = True
    if len(date_vals) >= 2:
        xs = [v[0] for v in date_vals]; ys = [v[1] for v in date_vals]
        if max(xs)-min(xs) >= 2.5 or max(ys)-min(ys) >= 2.5:
            inconsistent = True

    if avg >= 1.5 and not inconsistent:
        level = "high"
        summary = f"Strong match across {len(results)} suitable references. No defect should be flagged unless it repeats across references."
    elif avg >= 0.75:
        level = "medium"
        summary = f"Mixed result across {len(results)} references. Treat small differences as inconclusive and inspect the individual views."
    else:
        level = "low"
        summary = f"Reference agreement is weak across {len(results)} comparisons; this image is not suitable for a confident conclusion."
    if marker_check["present"]:
        hours = ", ".join(str(x["hour"]) for x in marker_check["items"])
        summary = f"Reference disagreement at hour marker(s) {hours}. Do not treat those marker differences as a watch defect."
        if level == "high":
            level = "medium"
    return {
        "level": level,
        "summary": summary,
        "detail": f"{len(results)} suitable references · visual {', '.join(visuals)}",
        "marker_disagreement": marker_check["present"],
        "marker_disagreement_detail": marker_check["items"],
    }


def _is_selected_cached_reference(backend, model_ref: str, filename: str) -> bool:
    if not filename:
        return False
    root = backend.PERSIST_DIR / "runtime" / "reference-packs" / model_ref
    path = root / Path(filename).name
    if not path.exists():
        return False
    meta = path.with_suffix(path.suffix + ".json")
    if not meta.exists():
        return False
    try:
        data = json.loads(meta.read_text(encoding="utf-8"))
    except Exception:
        return False
    return data.get("verification") in {"official-manufacturer-source", "user-asserted"}


def install(backend, v1_full_module, ux_module, official_sources_module, perspective_diagnostics) -> None:
    if getattr(backend, "_watch_align_v122_installed", False):
        return
    backend._watch_align_v122_installed = True
    backend.app.version = V122_VERSION
    v1_full_module.V1_FULL_VERSION = V122_VERSION

    official_sources_module.OFFICIAL_SOURCES.setdefault("124060", [SUBMARINER_SOURCE.copy()])
    ux_module._reference_score = lambda b, c, r: _perspective_aware_score(b, perspective_diagnostics, c, r)
    ux_module._consensus = _consensus

    # Preserve provenance when a cached selected reference is sent by the UI as a file.
    for route in getattr(backend.app, "routes", []):
        if getattr(route, "path", None) == "/api/v1/analyse" and hasattr(route, "dependant"):
            original = route.dependant.call
            def wrapped(*args, __original=original, **kwargs):
                result = __original(*args, **kwargs)
                ref = kwargs.get("reference")
                model_ref = kwargs.get("model_ref")
                name = getattr(ref, "filename", "") if ref is not None else ""
                if isinstance(result, dict) and model_ref and _is_selected_cached_reference(backend, model_ref, name):
                    result["reference_status"] = f"selected cached reference: {name}"
                return result
            route.dependant.call = wrapped
            route.endpoint = wrapped
            break

    html_path = backend.STATIC_DIR / "v1.html"
    js_path = backend.STATIC_DIR / "v1-full.js"
    html = html_path.read_text(encoding="utf-8").replace("V1 1.2.1", "V1 1.2.2").replace("Watch Align V1.2.1", "Watch Align V1.2.2")
    html_path.write_text(html, encoding="utf-8")

    js = js_path.read_text(encoding="utf-8")
    # Make the visible reference selector real: fetch the selected cached image and submit it as the reference file.
    old = "const ref=$('reference').files[0];if(ref)f.append('reference',ref);"
    new = "let ref=$('reference').files[0];if(!ref&&state.task==='gen'&&!$('refSelector').classList.contains('hidden')&&$('refSelector').value){const fn=decodeURIComponent($('refSelector').value);const rr=await fetch('/api/v1/ux/reference-image/'+$('model').value+'/'+encodeURIComponent(fn));if(rr.ok){const blob=await rr.blob();ref=new File([blob],fn,{type:blob.type||'image/jpeg'});}}if(ref)f.append('reference',ref);"
    js = js.replace(old, new)
    # If only one cached reference exists, still submit it rather than silently using a different automatic choice.
    old2 = "let ref=$('reference').files[0];if(ref)f.append('reference',ref);"
    js = js.replace(old2, new)
    # Surface consensus disagreement directly in the result area.
    js += "\nconst waShow122=show;show=function(r){waShow122(r);const c=r?.metrics?.reference_consensus;if(c?.marker_disagreement){const n=document.createElement('div');n.className='notice warn';n.textContent=c.summary;$('plainFinding').prepend(n);}};\n"
    js_path.write_text(js, encoding="utf-8")
