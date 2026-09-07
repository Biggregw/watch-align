from __future__ import annotations

import io
import json
import shutil
from pathlib import Path
from typing import Any

import cv2
from fastapi import HTTPException, UploadFile

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
        return {"level": "n/a", "summary": "Single-reference comparison; multi-reference consensus is not available yet.", "detail": "1 suitable reference", "marker_disagreement": False, "bezel_disagreement": False, "date_disagreement": False}
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

    bezel_disagreement = len(bezel_vals) >= 2 and max(bezel_vals)-min(bezel_vals) >= 1.5
    date_disagreement = False
    if len(date_vals) >= 2:
        xs = [v[0] for v in date_vals]; ys = [v[1] for v in date_vals]
        date_disagreement = max(xs)-min(xs) >= 2.5 or max(ys)-min(ys) >= 2.5
    inconsistent = marker_check["present"] or bezel_disagreement or date_disagreement

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
        level = "medium" if level == "high" else level
    elif bezel_disagreement:
        summary = "Official references disagree on the bezel estimate. Treat the bezel measurement as inconclusive rather than a defect."
        level = "medium" if level == "high" else level
    elif date_disagreement:
        summary = "Official references disagree on the date/cyclops position. Treat the date measurement as inconclusive rather than a defect."
        level = "medium" if level == "high" else level
    return {
        "level": level,
        "summary": summary,
        "detail": f"{len(results)} suitable references · visual {', '.join(visuals)}",
        "marker_disagreement": marker_check["present"],
        "marker_disagreement_detail": marker_check["items"],
        "bezel_disagreement": bezel_disagreement,
        "date_disagreement": date_disagreement,
    }


def _cached_meta(backend, model_ref: str, filename: str) -> dict[str, Any] | None:
    if not filename:
        return None
    root = backend.PERSIST_DIR / "runtime" / "reference-packs" / model_ref
    path = root / Path(filename).name
    meta = path.with_suffix(path.suffix + ".json")
    if not path.exists() or not meta.exists():
        return None
    try:
        data = json.loads(meta.read_text(encoding="utf-8"))
    except Exception:
        return None
    if data.get("verification") not in {"official-manufacturer-source", "user-asserted"}:
        return None
    data["_path"] = path
    return data


def _other_cached_references(backend, model_ref: str, selected: str, limit: int = 2):
    root = backend.PERSIST_DIR / "runtime" / "reference-packs" / model_ref
    if not root.exists():
        return []
    out = []
    for path in sorted(root.iterdir()):
        if path.name == selected or path.suffix.lower() not in {".jpg", ".jpeg", ".png", ".webp"}:
            continue
        meta_path = path.with_suffix(path.suffix + ".json")
        if not meta_path.exists():
            continue
        try:
            meta = json.loads(meta_path.read_text(encoding="utf-8"))
        except Exception:
            continue
        if meta.get("verification") not in {"official-manufacturer-source", "user-asserted"}:
            continue
        out.append((path, meta))
        if len(out) >= limit:
            break
    return out


def _save_history_result(backend, result: dict[str, Any]) -> None:
    session_id = result.get("session_id")
    if not session_id:
        return
    folder = backend.SESSIONS_DIR / str(session_id)
    if not folder.exists():
        return
    try:
        (folder / "v1_result.json").write_text(json.dumps(result), encoding="utf-8")
    except Exception:
        pass


def install(backend, v1_full_module, ux_module, official_sources_module, perspective_diagnostics) -> None:
    if getattr(backend, "_watch_align_v122_installed", False):
        return
    backend._watch_align_v122_installed = True
    backend.app.version = V122_VERSION
    v1_full_module.V1_FULL_VERSION = V122_VERSION

    official_sources_module.OFFICIAL_SOURCES.setdefault("124060", [SUBMARINER_SOURCE.copy()])
    ux_module._reference_score = lambda b, c, r: _perspective_aware_score(b, perspective_diagnostics, c, r)
    ux_module._consensus = _consensus

    # Selected cached references are uploaded by the browser so FastAPI cannot silently
    # discard the selector value. Rebuild multi-reference consensus here and clean up
    # secondary sessions so the consensus feature does not leak disk space.
    for route in getattr(backend.app, "routes", []):
        if getattr(route, "path", None) == "/api/v1/analyse" and hasattr(route, "dependant"):
            original = route.dependant.call
            def wrapped(*args, __original=original, **kwargs):
                result = __original(*args, **kwargs)
                ref = kwargs.get("reference")
                model_ref = kwargs.get("model_ref")
                candidate = kwargs.get("candidate")
                name = getattr(ref, "filename", "") if ref is not None else ""
                meta = _cached_meta(backend, model_ref, name) if model_ref else None
                if isinstance(result, dict) and meta is not None:
                    label = "official Rolex reference" if meta.get("verification") == "official-manufacturer-source" else "user-trusted reference"
                    result["reference_status"] = f"{label}: {meta.get('variant','')} · {name}"
                    comparisons = [result]
                    for path, _other_meta in _other_cached_references(backend, model_ref, name, limit=2):
                        try:
                            if candidate is not None and hasattr(candidate, "file"):
                                candidate.file.seek(0)
                            alt = UploadFile(filename=path.name, file=io.BytesIO(path.read_bytes()))
                            secondary = __original(*args, **{**kwargs, "candidate": candidate, "reference": alt})
                            if isinstance(secondary, dict):
                                comparisons.append(secondary)
                                sid = secondary.get("session_id")
                                if sid:
                                    shutil.rmtree(backend.SESSIONS_DIR / str(sid), ignore_errors=True)
                        except Exception:
                            continue
                    result.setdefault("metrics", {})["reference_consensus"] = _consensus(comparisons)
                    result["metrics"]["reference_selection"] = {"strategy": "user-selected cached reference + perspective-aware consensus", "selected": name, "candidate_count": len(comparisons)}
                if isinstance(result, dict):
                    _save_history_result(backend, result)
                return result
            route.dependant.call = wrapped
            route.endpoint = wrapped
            break

    @backend.app.get("/api/v1/history/{session_id}")
    def history_result(session_id: str):
        if not session_id or any(ch not in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_" for ch in session_id):
            raise HTTPException(status_code=404, detail="Comparison not found")
        path = backend.SESSIONS_DIR / session_id / "v1_result.json"
        if not path.exists():
            raise HTTPException(status_code=404, detail="Comparison not found")
        try:
            return json.loads(path.read_text(encoding="utf-8"))
        except Exception:
            raise HTTPException(status_code=422, detail="Saved comparison is unreadable")

    html_path = backend.STATIC_DIR / "v1.html"
    js_path = backend.STATIC_DIR / "v1-full.js"
    html = html_path.read_text(encoding="utf-8").replace("V1 1.2.1", "V1 1.2.2").replace("Watch Align V1.2.1", "Watch Align V1.2.2")
    html_path.write_text(html, encoding="utf-8")

    js = js_path.read_text(encoding="utf-8")
    old = "const ref=$('reference')?.files?.[0];if(ref)f.append('reference',ref);if(state.task==='gen'&&$('refSelector').value)f.append('preferred_reference',decodeURIComponent($('refSelector').value));"
    new = "let ref=$('reference')?.files?.[0];if(!ref&&state.task==='gen'&&$('refSelector').value){const fn=decodeURIComponent($('refSelector').value);const rr=await fetch('/api/v1/ux/reference-image/'+$('model').value+'/'+encodeURIComponent(fn));if(!rr.ok)throw new Error('Selected reference could not be loaded');const blob=await rr.blob();ref=new File([blob],fn,{type:blob.type||'image/jpeg'});}if(ref)f.append('reference',ref);"
    js = js.replace(old, new)
    js += "\nconst waShow122=show;show=function(r){waShow122(r);const c=r?.metrics?.reference_consensus;if(c?.marker_disagreement||c?.bezel_disagreement||c?.date_disagreement){const n=document.createElement('div');n.className='notice warn';n.textContent=c.summary;$('plainFinding').prepend(n);const cards=[...document.querySelectorAll('#regions .region')];for(const el of cards){const t=el.textContent||'';if((c.marker_disagreement&&t.includes('Hour markers'))||(c.bezel_disagreement&&t.includes('Bezel'))||(c.date_disagreement&&t.includes('Date / cyclops'))){const d=el.querySelector('div');if(d){d.className='warn';d.textContent='Cannot judge · references disagree';}}}}};\nconst waRenderHistory122=renderHistory;renderHistory=function(){waRenderHistory122();document.querySelectorAll('.historyItem').forEach(el=>el.onclick=async()=>{let h=[];try{h=JSON.parse(localStorage.getItem('wa-history')||'[]')}catch{}const x=h[Number(el.dataset.i)];if(!x)return;try{const r=await api('/api/v1/history/'+encodeURIComponent(x.session_id));show(r);$('status').textContent='Previous comparison reopened.'}catch(e){$('status').textContent=x.summary||e.message;}})};\n"
    js_path.write_text(js, encoding="utf-8")
