from __future__ import annotations

import io
import json
import math
import time
import uuid
from pathlib import Path
from typing import Any

import cv2
import numpy as np
from fastapi import File, Form, HTTPException, UploadFile
from fastapi.responses import FileResponse
from PIL import Image, ImageDraw, ImageFont

V1_FULL_VERSION = "1.0.0"

MODEL_GEOMETRY: dict[str, dict[str, Any]] = {
    "126710BLNR": {
        "brand": "Rolex",
        "family": "GMT-Master II",
        "name": "GMT-Master II 126710BLNR",
        "nickname": "Batgirl / Batman",
        "profile": "rolex-sports-date",
        "has_date": True,
        "has_cyclops": True,
        "dial_marker_inner": 0.55,
        "dial_marker_outer": 0.86,
        "bezel_inner": 0.98,
        "bezel_outer": 1.25,
        "date_roi": [0.48, -0.15, 0.83, 0.16],
        "exclude_angle_ranges": [[67, 113]],
        "canonical_notes": "Measurements are normalised to detected crystal radius. They are image-geometry diagnostics, not millimetre metrology.",
    },
    "124060": {
        "brand": "Rolex",
        "family": "Submariner",
        "name": "Submariner 124060 No-Date",
        "nickname": "Submariner No-Date",
        "profile": "rolex-sports-no-date",
        "has_date": False,
        "has_cyclops": False,
        "dial_marker_inner": 0.55,
        "dial_marker_outer": 0.86,
        "bezel_inner": 0.98,
        "bezel_outer": 1.25,
        "date_roi": None,
        "exclude_angle_ranges": [],
        "canonical_notes": "Measurements are normalised to detected crystal radius. They are image-geometry diagnostics, not millimetre metrology.",
    },
}

V1_HTML = r'''<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width,initial-scale=1" />
<title>Watch Align V1</title>
<style>
:root{color-scheme:dark;--bg:#08111f;--panel:#101b2d;--border:#253857;--text:#f4f7fb;--muted:#9eb0c9;--cyan:#32d5f2;--red:#ff8585;--amber:#ffc96b;--green:#7fe1aa}*{box-sizing:border-box}body{margin:0;background:#08111f;color:var(--text);font-family:Inter,system-ui,Segoe UI,sans-serif}.wrap{max-width:1500px;margin:auto;padding:28px}.top{display:flex;justify-content:space-between;gap:20px;align-items:flex-start}.eyebrow{color:var(--cyan);font-size:12px;font-weight:800;letter-spacing:.18em}h1{margin:6px 0 8px;font-size:clamp(2rem,5vw,3.4rem)}p{color:var(--muted)}.pill{border:1px solid var(--border);padding:8px 12px;border-radius:999px;color:var(--muted)}.grid{display:grid;grid-template-columns:minmax(0,1fr) 360px;gap:16px}.card{background:#101b2d;border:1px solid var(--border);border-radius:18px;padding:18px}.setup{grid-column:1/-1}.row{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}.field{display:grid;gap:7px}label{font-size:13px;color:var(--muted);font-weight:700}select,input,button{font:inherit}select,input[type=file]{width:100%;background:#0b1627;color:var(--text);border:1px solid #315076;border-radius:10px;padding:10px}button{border:0;border-radius:11px;padding:11px 14px;font-weight:800;cursor:pointer}.primary{background:var(--cyan);color:#04202a}.secondary{background:#192b45;color:var(--text);border:1px solid #2b4569}.actions{display:flex;gap:10px;margin-top:14px;flex-wrap:wrap}.hidden{display:none!important}.viewer{min-height:580px;display:grid;place-items:center;background:#0b1627;border:1px solid var(--border);border-radius:14px;overflow:hidden;position:relative}.viewer img{max-width:100%;max-height:76vh;display:block}.tabs{display:flex;gap:7px;flex-wrap:wrap;margin-bottom:12px}.tab.active{background:var(--cyan);color:#04202a}.tab{background:#182943;color:var(--muted)}.metrics{display:grid;gap:9px}.metric{background:#0b1627;border:1px solid var(--border);border-radius:12px;padding:10px}.metric strong{display:block;margin-bottom:3px}.good{color:var(--green)}.warn{color:var(--amber)}.bad{color:var(--red)}table{width:100%;border-collapse:collapse;font-size:13px}th,td{padding:7px;border-bottom:1px solid var(--border);text-align:right}th:first-child,td:first-child{text-align:left}.note{font-size:12px;color:var(--muted)}@media(max-width:900px){.grid{grid-template-columns:1fr}.row{grid-template-columns:1fr}.viewer{min-height:400px}.top{display:block}.pill{display:inline-block;margin-top:8px}}
</style>
</head>
<body><div class="wrap">
<div class="top"><div><div class="eyebrow">MODEL-AWARE QC + GEN COMPARE</div><h1>Watch Align V1</h1><p>Analyse internal watch geometry or align a QC photo to an exact-model genuine/reference image.</p></div><div class="pill">V1 1.0.0</div></div>
<div class="grid">
<section class="card setup"><div class="row">
<div class="field"><label>Mode</label><select id="mode"><option value="qc">QC Analysis</option><option value="gen">Gen Compare</option></select></div>
<div class="field"><label>Watch model</label><select id="model"></select></div>
<div class="field"><label>QC / candidate image</label><input id="candidate" type="file" accept="image/*" /></div>
</div><div id="genRow" class="row hidden" style="margin-top:12px"><div class="field" style="grid-column:1/-1"><label>Genuine/reference image <span class="note">Required until a verified built-in reference is installed for this model</span></label><input id="reference" type="file" accept="image/*" /></div></div>
<div class="actions"><button id="analyse" class="primary">Analyse watch</button><button id="legacy" class="secondary">Open Manual Compare</button></div><p id="status">Choose a model and QC image.</p></section>
<section id="result" class="card hidden"><div id="measurementWarning" role="alert" class="metric warn hidden" style="border:2px solid var(--amber);margin-bottom:16px;padding:16px"></div><div class="tabs"><button class="tab active" data-view="annotated">QC analysis</button><button class="tab" data-view="reference">Reference</button><button class="tab" data-view="aligned">Aligned</button><button class="tab" data-view="overlay">Overlay</button><button class="tab" data-view="edges">Edges</button></div><div class="viewer"><img id="view" alt="Watch comparison" /></div><div class="actions"><button id="report" class="secondary">Download QC report PNG</button></div></section>
<aside id="side" class="card hidden"><h2 style="margin-top:0">Analysis</h2><div id="summary" class="metrics"></div><h3>Hour-marker geometry</h3><div style="overflow:auto"><table><thead><tr><th>Hour</th><th>Angular</th><th>Radial</th><th>Confidence</th></tr></thead><tbody id="markers"></tbody></table></div><p class="note">Angular and radial values are relative image-geometry measurements after removing overall dial rotation. They are intended for QC comparison, not physical millimetre measurement.</p></aside>
</div></div><script src="/static/v1-full.js"></script></body></html>'''

V1_JS = r'''const state={models:[],result:null,view:'annotated'};const $=id=>document.getElementById(id);async function api(url,opt={}){const r=await fetch(url,opt);const b=(r.headers.get('content-type')||'').includes('json')?await r.json():await r.text();if(!r.ok)throw new Error(b.detail||b||`Request failed ${r.status}`);return b}async function loadModels(){const d=await api('/api/v1/models/full');state.models=d.models;$('model').innerHTML=d.models.map(m=>`<option value="${m.reference}">${m.brand} ${m.name}</option>`).join('')}function updateMode(){$('genRow').classList.toggle('hidden',$('mode').value!=='gen')}function confClass(v){return v==='high'?'good':v==='medium'?'warn':'bad'}function metric(name,value,cls=''){return `<div class="metric"><strong>${name}</strong><span class="${cls}">${value}</span></div>`}function show(r){state.result=r;$('result').classList.remove('hidden');$('side').classList.remove('hidden');const warning=r.metrics.measurement_warning;$('measurementWarning').textContent=warning||'';$('measurementWarning').classList.toggle('hidden',!warning);const p=r.metrics.perspective||{};const regions=r.metrics.region_confidence||{};let h='';h+=metric('Model',`${r.model.reference} · ${r.model.name}`);h+=metric('Mode',r.mode==='gen'?'Gen Compare':'QC Analysis');h+=metric('Overall confidence',r.metrics.overall_confidence,confClass(r.metrics.overall_confidence));if(p.candidate?.available)h+=metric('Apparent camera tilt',`${p.candidate.tilt_deg}°`,p.warning?'warn':'');if(p.mismatch_deg!=null)h+=metric('Perspective mismatch',`${p.mismatch_deg}°`,p.warning?'warn':'good');if(r.metrics.bezel)h+=metric('Bezel 12 estimate',r.metrics.bezel.reliable===false?'Unreliable — measurement withheld':`${r.metrics.bezel.offset_deg>=0?'+':''}${r.metrics.bezel.offset_deg}°`,confClass(r.metrics.bezel.confidence));if(r.metrics.date_window)h+=metric('Date-window position',r.metrics.date_window.reliable===false?'Unreliable — measurement withheld':`x ${r.metrics.date_window.x_offset_percent>=0?'+':''}${r.metrics.date_window.x_offset_percent}% · y ${r.metrics.date_window.y_offset_percent>=0?'+':''}${r.metrics.date_window.y_offset_percent}%`,confClass(r.metrics.date_window.confidence));h+=metric('Region confidence',Object.entries(regions).map(([k,v])=>`${k==='perspective'?'Perspective estimate confidence (not perspective quality)':k}: ${v}`).join(' · '));if(r.reference_status)h+=metric('Reference',r.reference_status,r.reference_status.includes('verified')?'good':'warn');$('summary').innerHTML=h;$('markers').innerHTML=(r.metrics.markers||[]).map(m=>`<tr><td>${m.hour}</td><td>${m.reliable===false?'Unreliable':`${m.angular_error_deg>=0?'+':''}${m.angular_error_deg}°`}</td><td>${m.reliable===false?'Withheld':`${m.radial_error_percent>=0?'+':''}${m.radial_error_percent}%`}</td><td class="${confClass(m.confidence)}">${m.confidence}</td></tr>`).join('');switchView('annotated')}function switchView(v){state.view=v;document.querySelectorAll('.tab').forEach(b=>b.classList.toggle('active',b.dataset.view===v));const u=state.result?.images?.[v]||state.result?.images?.annotated;if(u)$('view').src=u}document.querySelectorAll('.tab').forEach(b=>b.addEventListener('click',()=>switchView(b.dataset.view)));$('mode').addEventListener('change',updateMode);$('legacy').addEventListener('click',()=>location.href='/');$('analyse').addEventListener('click',async()=>{const c=$('candidate').files[0];if(!c){$('status').textContent='Choose a QC image first.';return}const f=new FormData();f.append('mode',$('mode').value);f.append('model_ref',$('model').value);f.append('candidate',c);const ref=$('reference').files[0];if(ref)f.append('reference',ref);$('status').textContent='Analysing geometry…';$('analyse').disabled=true;try{const r=await api('/api/v1/analyse',{method:'POST',body:f});show(r);$('status').textContent='Analysis complete.'}catch(e){$('status').textContent=e.message}finally{$('analyse').disabled=false}});$('report').addEventListener('click',()=>{if(!state.result)return;const a=document.createElement('a');a.href=state.result.report_url;a.download=`WatchAlign-${state.result.model.reference}-QC-report.png`;a.click()});loadModels().then(updateMode).catch(e=>$('status').textContent=e.message);'''


def model_info(reference: str) -> dict[str, Any]:
    model = MODEL_GEOMETRY.get(reference)
    if model is None:
        raise HTTPException(status_code=404, detail=f"Unknown model reference: {reference}")
    return {"reference": reference, **model}


def _read_image(upload: UploadFile, backend) -> np.ndarray:
    return backend.resize_max(backend.read_upload(upload))


def _full_circle(image: np.ndarray, backend) -> tuple[float, float, float] | None:
    try:
        return backend.detect_refined_circle_full(image)
    except Exception:
        initial = backend.detect_watch_circle(image)
        if initial is None:
            return None
        return tuple(float(v) for v in initial)


def _angle_distance(a: np.ndarray, target: float) -> np.ndarray:
    return ((a - target + 180.0) % 360.0) - 180.0


def marker_measurements(image: np.ndarray, circle: tuple[float, float, float], model: dict[str, Any]) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    cx, cy, radius = circle
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    gray = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8)).apply(gray)
    blur = cv2.GaussianBlur(gray.astype(np.float32), (0, 0), 3.0)
    highpass = np.clip(gray.astype(np.float32) - blur, 0.0, None)
    gx = cv2.Sobel(gray, cv2.CV_32F, 1, 0, ksize=3)
    gy = cv2.Sobel(gray, cv2.CV_32F, 0, 1, ksize=3)
    edge = cv2.magnitude(gx, gy)
    yy, xx = np.indices(gray.shape, dtype=np.float32)
    dx, dy = xx - cx, yy - cy
    radial = np.sqrt(dx * dx + dy * dy)
    angle = np.degrees(np.arctan2(dx, -dy)) % 360.0
    inner = radius * float(model["dial_marker_inner"])
    outer = radius * float(model["dial_marker_outer"])
    raw: list[dict[str, Any]] = []
    for hour in range(1, 13):
        target = 0.0 if hour == 12 else hour * 30.0
        delta = _angle_distance(angle, target)
        sector = (np.abs(delta) <= 10.5) & (radial >= inner) & (radial <= outer)
        values = highpass[sector]
        if values.size < 80:
            raw.append({"hour": hour, "available": False})
            continue
        threshold = max(float(np.percentile(values, 88)), 2.0)
        weights = np.where(sector & (highpass >= threshold), highpass + edge * 0.15, 0.0)
        total = float(weights.sum())
        if total <= 1.0:
            raw.append({"hour": hour, "available": False})
            continue
        measured_angle = float(np.sum(weights * angle) / total)
        # Circular mean near 0/360 needs vector treatment.
        radians = np.radians(angle)
        s = float(np.sum(weights * np.sin(radians)))
        c = float(np.sum(weights * np.cos(radians)))
        measured_angle = math.degrees(math.atan2(s, c)) % 360.0
        measured_radius = float(np.sum(weights * radial) / total)
        peak = float(np.percentile(weights[weights > 0], 85)) if np.any(weights > 0) else 0.0
        raw.append({"hour": hour, "available": True, "target": target, "angle": measured_angle, "radius": measured_radius, "weight": total, "peak": peak})
    available = [r for r in raw if r.get("available")]
    if len(available) < 6:
        return [], {"available": False, "reason": "fewer than six reliable marker sectors", "count": len(available)}
    offsets = np.array([((r["angle"] - r["target"] + 180.0) % 360.0) - 180.0 for r in available], dtype=np.float32)
    overall_rotation = float(np.median(offsets))
    radii = np.array([r["radius"] for r in available], dtype=np.float32)
    ring_radius = float(np.median(radii))
    median_weight = float(np.median([r["weight"] for r in available]))
    result = []
    for r in raw:
        if not r.get("available"):
            result.append({"hour": r["hour"], "angular_error_deg": 0.0, "radial_error_percent": 0.0, "confidence": "low", "available": False})
            continue
        angular_error = ((float(r["angle"]) - float(r["target"]) - overall_rotation + 180.0) % 360.0) - 180.0
        radial_error = (float(r["radius"]) - ring_radius) / max(radius, 1e-6) * 100.0
        quality = float(r["weight"]) / max(median_weight, 1e-6)
        confidence = "high" if quality >= 0.70 else "medium" if quality >= 0.40 else "low"
        result.append({"hour": r["hour"], "angular_error_deg": round(angular_error, 3), "radial_error_percent": round(radial_error, 3), "confidence": confidence, "available": True})
    spread = float(np.median(np.abs(np.array([r["angular_error_deg"] for r in result if r["available"]], dtype=np.float32))))
    return result, {"available": True, "count": len(available), "overall_rotation_deg": round(overall_rotation, 3), "median_abs_marker_error_deg": round(spread, 3), "ring_radius_ratio": round(ring_radius / max(radius, 1e-6), 4)}


def bezel_top_measurement(image: np.ndarray, circle: tuple[float, float, float], model: dict[str, Any]) -> dict[str, Any]:
    cx, cy, radius = circle
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    gx = cv2.Sobel(gray, cv2.CV_32F, 1, 0, ksize=3)
    gy = cv2.Sobel(gray, cv2.CV_32F, 0, 1, ksize=3)
    mag = cv2.GaussianBlur(cv2.magnitude(gx, gy), (0, 0), 1.1)
    yy, xx = np.indices(gray.shape, dtype=np.float32)
    dx, dy = xx - cx, yy - cy
    radial = np.sqrt(dx * dx + dy * dy)
    angle = np.degrees(np.arctan2(dx, -dy))
    sector = (np.abs(angle) <= 13.0) & (radial >= radius * float(model["bezel_inner"])) & (radial <= radius * float(model["bezel_outer"]))
    values = mag[sector]
    if values.size < 100:
        return {"available": False, "offset_deg": 0.0, "confidence": "low", "reason": "upper bezel region unavailable"}
    threshold = float(np.percentile(values, 94))
    weights = np.where(sector & (mag >= threshold), mag, 0.0)
    total = float(weights.sum())
    if total <= 1.0:
        return {"available": False, "offset_deg": 0.0, "confidence": "low", "reason": "upper bezel feature weak"}
    radians = np.radians(angle)
    measured = math.degrees(math.atan2(float(np.sum(weights * np.sin(radians))), float(np.sum(weights * np.cos(radians)))))
    concentration = float(np.max(values)) / max(float(np.mean(values)) + 1e-6, 1e-6)
    confidence = "high" if concentration >= 7.0 and abs(measured) <= 5 else "medium" if concentration >= 4.0 and abs(measured) <= 8 else "low"
    return {"available": True, "offset_deg": round(float(measured), 3), "confidence": confidence, "feature_concentration": round(concentration, 2), "note": "Image estimate of dominant upper-bezel feature; inspect visually before treating as a QC defect."}


def date_window_measurement(image: np.ndarray, circle: tuple[float, float, float], model: dict[str, Any]) -> dict[str, Any] | None:
    roi = model.get("date_roi")
    if not roi:
        return None
    cx, cy, radius = circle
    x0 = int(round(cx + roi[0] * radius)); y0 = int(round(cy + roi[1] * radius)); x1 = int(round(cx + roi[2] * radius)); y1 = int(round(cy + roi[3] * radius))
    h, w = image.shape[:2]; x0=max(0,x0);y0=max(0,y0);x1=min(w,x1);y1=min(h,y1)
    if x1-x0 < 20 or y1-y0 < 15:
        return {"available": False, "confidence": "low", "reason": "date ROI unavailable"}
    crop = cv2.cvtColor(image[y0:y1, x0:x1], cv2.COLOR_BGR2GRAY)
    edge = cv2.Canny(crop, 55, 145)
    contours,_ = cv2.findContours(edge, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
    candidates=[]
    for contour in contours:
        bx,by,bw,bh=cv2.boundingRect(contour)
        area=bw*bh
        aspect=bw/max(bh,1)
        if area >= crop.size*0.03 and 0.7 <= aspect <= 3.2:
            candidates.append((area,bx,by,bw,bh))
    if not candidates:
        return {"available": False, "confidence": "low", "reason": "date-window rectangle not confidently detected"}
    _,bx,by,bw,bh=max(candidates,key=lambda t:t[0])
    detected_x=x0+bx+bw/2; detected_y=y0+by+bh/2
    expected_x=cx+((roi[0]+roi[2])/2)*radius; expected_y=cy+((roi[1]+roi[3])/2)*radius
    xoff=(detected_x-expected_x)/max(radius,1e-6)*100; yoff=(detected_y-expected_y)/max(radius,1e-6)*100
    confidence="high" if abs(xoff)<=2 and abs(yoff)<=2 else "medium" if abs(xoff)<=4 and abs(yoff)<=4 else "low"
    return {"available": True, "x_offset_percent": round(float(xoff),2), "y_offset_percent": round(float(yoff),2), "confidence": confidence, "bbox": [int(x0+bx),int(y0+by),int(bw),int(bh)], "note": "Relative to the model's canonical image ROI, not a factory dimensional tolerance."}


def region_confidence(markers: list[dict[str, Any]], marker_summary: dict[str, Any], perspective: dict[str, Any], bezel: dict[str, Any], date: dict[str, Any] | None) -> dict[str, str]:
    marker_count = int(marker_summary.get("count", 0))
    dial = "high" if marker_count >= 10 else "medium" if marker_count >= 7 else "low"
    tilt = float((perspective.get("candidate") or {}).get("tilt_deg", 0.0) or 0.0)
    if tilt >= 15 and dial == "high": dial = "medium"
    if tilt >= 22: dial = "low"
    result = {"dial": dial, "markers": dial, "bezel": bezel.get("confidence", "low"), "perspective": (perspective.get("candidate") or {}).get("confidence", "low")}
    if date is not None: result["date/cyclops"] = date.get("confidence", "low")
    return result


def overall_confidence(regions: dict[str, str], perspective_warning: str | None) -> str:
    score_map={"high":2,"medium":1,"low":0}; values=[score_map.get(v,0) for k,v in regions.items() if k != "perspective"]
    avg=sum(values)/max(len(values),1)
    if perspective_warning: avg-=0.35
    return "high" if avg>=1.55 else "medium" if avg>=0.75 else "low"


def gate_measurements(metrics: dict[str, Any], mode: str) -> None:
    """Withhold unsafe image measurements before JSON, annotation and report output."""
    regions = metrics.get("region_confidence", {})
    perspective = metrics.get("perspective", {})
    perspective["confidence_meaning"] = "Confidence in the perspective estimate, not good perspective"
    mismatch = perspective.get("mismatch_deg")
    severe = mode == "gen" and (
        (mismatch is not None and mismatch >= 6)
        or any((perspective.get(k) or {}).get("tilt_deg", 0) >= 18 for k in ("candidate", "reference"))
    )
    alignment_low = mode == "gen" and metrics.get("base_alignment", {}).get("confidence", "high") not in {"high", "medium"}
    if severe or alignment_low:
        metrics["overall_confidence"] = "low"
    blocked = metrics.get("overall_confidence") not in {"high", "medium"}
    if blocked:
        metrics["overall_confidence"] = "low"
    withheld = []
    def gate(item, region, fields):
        if item is None:
            return
        reliable = (not blocked and regions.get(region) in {"high", "medium"}
                    and item.get("confidence") in {"high", "medium"}
                    and item.get("available", False))
        item["reliable"] = bool(reliable)
        if not reliable:
            item["available"] = False
            item["reason"] = "Measurement unreliable: low or unavailable confidence"
            for field in fields:
                item[field] = None
            withheld.append(region)
    for marker in metrics.get("markers", []):
        gate(marker, "markers", ("angular_error_deg", "radial_error_percent"))
    gate(metrics.get("bezel"), "bezel", ("offset_deg",))
    gate(metrics.get("date_window"), "date/cyclops", ("x_offset_percent", "y_offset_percent", "bbox"))
    if blocked or regions.get("markers") not in {"high", "medium"}:
        summary = metrics.get("marker_summary", {})
        for field in ("overall_rotation_deg", "median_abs_marker_error_deg", "ring_radius_ratio"):
            summary[field] = None
        summary["available"] = False
    metrics["measurement_warning"] = (
        "Measurement unreliable — reference and QC images have incompatible perspective or excessive camera tilt. Use straighter, similarly framed photos. Precise measurements are withheld."
        if severe else "Measurement unreliable — overall confidence is low. Precise measurements are withheld. Use clearer, straighter photos."
        if blocked else "Some measurements are unreliable and have been withheld because their region or feature confidence is low."
        if withheld else None
    )
    if blocked:
        metrics.setdefault("base_alignment", {})["measurements_reliable"] = False


def _annotated_image(image: np.ndarray, circle: tuple[float,float,float], markers: list[dict[str,Any]], bezel: dict[str,Any], date: dict[str,Any] | None, model_ref: str, confidence: str) -> np.ndarray:
    out=image.copy(); cx,cy,r=map(float,circle)
    cv2.circle(out,(int(cx),int(cy)),int(r),(255,255,255),2,cv2.LINE_AA)
    for m in markers:
        if not m.get("available"): continue
        target=0 if m["hour"]==12 else m["hour"]*30
        theta=math.radians(target); p1=(int(cx+math.sin(theta)*r*.54),int(cy-math.cos(theta)*r*.54));p2=(int(cx+math.sin(theta)*r*.91),int(cy-math.cos(theta)*r*.91))
        err=abs(float(m["angular_error_deg"])); color=(80,220,120) if err<0.5 else (70,190,255) if err<1.0 else (90,90,255)
        cv2.line(out,p1,p2,color,2,cv2.LINE_AA)
    if bezel.get("available"):
        a=math.radians(float(bezel["offset_deg"])); p=(int(cx+math.sin(a)*r*1.16),int(cy-math.cos(a)*r*1.16)); cv2.line(out,(int(cx),int(cy-r*.94)),p,(255,180,60),2,cv2.LINE_AA)
    if date and date.get("available") and date.get("bbox"):
        x,y,w,h=date["bbox"]; cv2.rectangle(out,(x,y),(x+w,y+h),(255,180,60),2)
    cv2.rectangle(out,(10,10),(min(out.shape[1]-10,520),76),(12,20,32),-1)
    cv2.putText(out,f"Watch Align V1  {model_ref}",(24,36),cv2.FONT_HERSHEY_SIMPLEX,.72,(255,255,255),2,cv2.LINE_AA)
    cv2.putText(out,f"QC geometry confidence: {confidence.upper()}",(24,63),cv2.FONT_HERSHEY_SIMPLEX,.56,(210,225,240),1,cv2.LINE_AA)
    return out


def _report_image(annotated: np.ndarray, metrics: dict[str,Any], model: dict[str,Any]) -> np.ndarray:
    h,w=annotated.shape[:2]; panel=360; canvas=np.full((h,w+panel,3),18,dtype=np.uint8); canvas[:,:w]=annotated
    x=w+22; y=34
    def text(s,size=.56,step=28,color=(235,240,245)):
        nonlocal y; cv2.putText(canvas,str(s),(x,y),cv2.FONT_HERSHEY_SIMPLEX,size,color,1,cv2.LINE_AA); y+=step
    text("WATCH ALIGN V1 QC REPORT",.65,34);
    if metrics.get("measurement_warning"):
        text("UNRELIABLE measurements withheld",.46,28,(90,190,255))
    text(f"Model: {model['reference']}"); text(f"Confidence: {metrics['overall_confidence'].upper()}");
    p=metrics.get('perspective',{}).get('candidate',{}); text(f"Camera tilt: {p.get('tilt_deg','n/a')} deg")
    text(f"Tilt estimate confidence: {p.get('confidence','low')}",.46)
    text("Confidence in estimate, not good perspective",.38)
    b=metrics.get('bezel',{}); text("Bezel top: UNRELIABLE" if b.get("reliable") is False else f"Bezel top: {b.get('offset_deg','n/a')} deg")
    d=metrics.get('date_window');
    if d and d.get("reliable") is False: text("Date: UNRELIABLE")
    elif d: text(f"Date: x {d.get('x_offset_percent','n/a')}%, y {d.get('y_offset_percent','n/a')}%")
    text("Marker errors:",.58,30)
    for m in metrics.get('markers',[]):
        if m.get('available'): text(f"{m['hour']:>2}: {m['angular_error_deg']:+.2f} deg  {m['radial_error_percent']:+.2f}%",.46,21)
    y=min(h-58,y+12); cv2.putText(canvas,"Image geometry aid only - not proof of authenticity",(x,y),cv2.FONT_HERSHEY_SIMPLEX,.42,(170,185,205),1,cv2.LINE_AA)
    return canvas


def _built_in_reference(backend, model_ref: str) -> tuple[np.ndarray | None, str | None]:
    root=backend.BASE_DIR/"references"/model_ref/"references"
    if not root.exists(): return None,None
    for path in sorted(root.iterdir()):
        if path.suffix.lower() in {".png",".jpg",".jpeg",".webp"}:
            image=cv2.imread(str(path))
            if image is not None: return backend.resize_max(image),path.name
    return None,None


def _save_result_assets(backend, folder: Path, candidate: np.ndarray, annotated: np.ndarray, report: np.ndarray, gen_render: dict[str,Any] | None) -> dict[str,str]:
    paths={"annotated":folder/"v1_annotated.png","report":folder/"v1_report.png"};cv2.imwrite(str(paths['annotated']),annotated);cv2.imwrite(str(paths['report']),report)
    urls={k:f"/files/{folder.name}/{p.name}?v={int(time.time()*1000)}" for k,p in paths.items()}
    if gen_render:
        urls.update({k:v for k,v in gen_render.get('urls',{}).items() if k in {'reference','aligned','overlay','edges','heatmap'}})
    else:
        cv2.imwrite(str(folder/"v1_candidate.png"),candidate);urls['aligned']=f"/files/{folder.name}/v1_candidate.png?v={int(time.time()*1000)}";urls['reference']=urls['annotated'];urls['overlay']=urls['annotated'];urls['edges']=urls['annotated']
    return urls


def install_full(backend, perspective_diagnostics) -> None:
    if getattr(backend,"_watch_align_v1_full_installed",False): return
    backend._watch_align_v1_full_installed=True
    backend.app.version=V1_FULL_VERSION
    (backend.STATIC_DIR/"v1.html").write_text(V1_HTML,encoding="utf-8");(backend.STATIC_DIR/"v1-full.js").write_text(V1_JS,encoding="utf-8")

    @backend.app.get("/v1")
    def v1_home(): return FileResponse(backend.STATIC_DIR/"v1.html")

    @backend.app.get("/api/v1/models/full")
    def full_models():
        models=[]
        for ref in MODEL_GEOMETRY:
            m=model_info(ref); built,name=_built_in_reference(backend,ref); m["built_in_reference"]=bool(built is not None);m["built_in_reference_name"]=name;models.append(m)
        return {"version":V1_FULL_VERSION,"models":models}

    @backend.app.post("/api/v1/analyse")
    def analyse(mode: str=Form(...), model_ref: str=Form(...), candidate: UploadFile=File(...), reference: UploadFile|None=File(None)):
        if mode not in {"qc","gen"}: raise HTTPException(status_code=400,detail="mode must be qc or gen")
        from comparison_progress import report
        report('Checking watch geometry…')
        model=model_info(model_ref);candidate_image=_read_image(candidate,backend);circle=_full_circle(candidate_image,backend)
        if circle is None: raise HTTPException(status_code=422,detail="Watch Align could not reliably detect the watch/crystal boundary in the QC image.")
        cand_p=perspective_diagnostics(candidate_image,circle);perspective={"candidate":cand_p,"reference":None,"mismatch_deg":None,"warning":None}
        if cand_p.get("available") and float(cand_p.get("tilt_deg",0))>=18: perspective["warning"]="QC image is strongly off-axis; small alignment differences are unreliable."
        markers,marker_summary=marker_measurements(candidate_image,circle,model);bezel=bezel_top_measurement(candidate_image,circle,model);date=date_window_measurement(candidate_image,circle,model)
        gen_render=None;reference_status=None;base_metrics={}
        if mode=="gen":
            ref_image=None;ref_name=None
            if reference is not None and getattr(reference,"filename",""):
                ref_image=_read_image(reference,backend);ref_name=reference.filename;reference_status=f"user-supplied reference: {ref_name}"
            else:
                ref_image,ref_name=_built_in_reference(backend,model_ref)
                if ref_image is not None: reference_status=f"verified built-in reference: {ref_name}"
            if ref_image is None: raise HTTPException(status_code=422,detail="Gen Compare needs a genuine/reference image for this model. Upload one, or install a verified reference pack.")
            report('Aligning watch with the genuine reference…')
            matrix,base_metrics=backend.auto_align(ref_image,candidate_image);ref_circle=_full_circle(ref_image,backend);ref_p=perspective_diagnostics(ref_image,ref_circle);perspective["reference"]=ref_p
            if ref_p.get("available") and cand_p.get("available"):
                mismatch=abs(float(ref_p['tilt_deg'])-float(cand_p['tilt_deg']));perspective['mismatch_deg']=round(mismatch,2)
                if mismatch>=6: perspective['warning']="Large perspective mismatch between reference and QC image; precision comparison is limited."
                elif mismatch>=3: perspective['warning']="Moderate perspective mismatch; inspect small differences cautiously."
            report('Rendering comparison images…')
            session_id=str(uuid.uuid4());folder=backend.SESSIONS_DIR/session_id;folder.mkdir(parents=True);cv2.imwrite(str(folder/"reference.png"),ref_image);cv2.imwrite(str(folder/"candidate.png"),candidate_image);np.save(folder/"base_transform.npy",matrix);(folder/"metrics.json").write_text(json.dumps(base_metrics,indent=2));request=backend.RenderRequest(session_id=session_id);gen_render=backend.render_assets(folder,ref_image,candidate_image,matrix,request,base_metrics)
        else:
            session_id=str(uuid.uuid4());folder=backend.SESSIONS_DIR/session_id;folder.mkdir(parents=True);cv2.imwrite(str(folder/"candidate.png"),candidate_image)
        regions=region_confidence(markers,marker_summary,perspective,bezel,date);overall=overall_confidence(regions,perspective.get('warning'))
        metrics={"overall_confidence":overall,"region_confidence":regions,"perspective":perspective,"markers":markers,"marker_summary":marker_summary,"bezel":bezel,"date_window":date,"base_alignment":base_metrics,"model_geometry_version":1}
        gate_measurements(metrics, mode)
        overall=metrics["overall_confidence"]
        annotated=_annotated_image(candidate_image,circle,markers,bezel,date,model_ref,overall);report=_report_image(annotated,metrics,model);images=_save_result_assets(backend,folder,candidate_image,annotated,report,gen_render);(folder/"v1_metrics.json").write_text(json.dumps(metrics,indent=2))
        return {"version":V1_FULL_VERSION,"mode":mode,"model":model,"session_id":session_id,"reference_status":reference_status,"metrics":metrics,"images":images,"report_url":images['report']}
