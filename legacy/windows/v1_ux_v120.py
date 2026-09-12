from __future__ import annotations

import io
import json
import math
from pathlib import Path
from typing import Any

import cv2
import numpy as np
from fastapi import File, HTTPException, UploadFile
from fastapi.responses import FileResponse

V120_VERSION = "1.2.0"

UX_HTML = r'''<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Watch Align V1.2</title>
<style>
:root{color-scheme:dark;--bg:#08111f;--panel:#101b2d;--panel2:#0b1627;--border:#263b5c;--text:#f4f7fb;--muted:#9eb0c9;--cyan:#32d5f2;--red:#ff8585;--amber:#ffc96b;--green:#7fe1aa}*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font-family:Inter,system-ui,Segoe UI,sans-serif}.wrap{max-width:1500px;margin:auto;padding:26px}.top{display:flex;justify-content:space-between;gap:18px;align-items:flex-start}.eyebrow{color:var(--cyan);font-size:12px;font-weight:800;letter-spacing:.18em}h1{font-size:clamp(2rem,5vw,3.25rem);margin:5px 0 5px}p,.muted{color:var(--muted)}.pill{border:1px solid var(--border);padding:8px 12px;border-radius:999px;color:#c9d8ec}.card{background:var(--panel);border:1px solid var(--border);border-radius:18px;padding:18px}.taskbar{display:grid;grid-template-columns:repeat(3,1fr);gap:10px;margin:18px 0}.task{background:var(--panel2);border:1px solid var(--border);color:var(--text);text-align:left;padding:16px;border-radius:14px;cursor:pointer}.task b{display:block;font-size:16px}.task span{display:block;color:var(--muted);font-size:12px;margin-top:4px}.task.active{border-color:var(--cyan);box-shadow:inset 0 0 0 1px var(--cyan)}.setupgrid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.field{display:grid;gap:7px}label{font-size:13px;color:#c2d0e3;font-weight:700}select,input,button{font:inherit}select,input[type=file]{width:100%;background:var(--panel2);color:var(--text);border:1px solid #315076;border-radius:10px;padding:10px}button{border:0;border-radius:11px;padding:11px 14px;font-weight:800;cursor:pointer}.primary{background:var(--cyan);color:#04202a}.secondary{background:#192b45;color:var(--text);border:1px solid #2b4569}.ghost{background:transparent;color:#cfe1f7;border:1px solid var(--border)}.actions{display:flex;gap:9px;flex-wrap:wrap;margin-top:13px}.notice{background:var(--panel2);border:1px solid var(--border);border-radius:12px;padding:12px;margin-top:12px}.good{color:var(--green)}.warn{color:var(--amber)}.bad{color:var(--red)}.hidden{display:none!important}details{margin-top:12px;border-top:1px solid var(--border);padding-top:10px}summary{cursor:pointer;color:#cbd9eb;font-weight:700}.main{display:grid;grid-template-columns:minmax(0,1fr) 390px;gap:16px;margin-top:16px}.viewerCard{min-width:0}.verdicts{display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin-bottom:12px}.verdict{background:var(--panel2);border:1px solid var(--border);border-radius:12px;padding:11px}.verdict small{display:block;color:var(--muted);margin-bottom:4px}.verdict strong{font-size:15px}.tabs{display:flex;gap:7px;flex-wrap:wrap;margin:10px 0}.tab{background:#182943;color:var(--muted)}.tab.active{background:var(--cyan);color:#04202a}.viewer{min-height:570px;background:var(--panel2);border:1px solid var(--border);border-radius:14px;overflow:hidden;position:relative;display:grid;place-items:center}.viewer img{max-width:100%;max-height:76vh;display:block}.viewer .layer{position:absolute;inset:0;margin:auto;max-width:100%;max-height:76vh}.controls{display:flex;gap:12px;align-items:center;flex-wrap:wrap;margin-top:10px}.controls input[type=range]{width:190px}.metrics{display:grid;gap:8px}.metric{background:var(--panel2);border:1px solid var(--border);border-radius:12px;padding:10px}.metric strong{display:block;margin-bottom:4px}.regionGrid{display:grid;grid-template-columns:1fr 1fr;gap:8px}.region{border:1px solid var(--border);border-radius:10px;padding:10px;background:var(--panel2)}.historyItem{padding:8px 0;border-top:1px solid var(--border);cursor:pointer}.preview{display:flex;gap:12px;align-items:center}.preview img{width:92px;height:92px;object-fit:contain;background:white;border-radius:10px}.preflight{display:grid;grid-template-columns:repeat(4,1fr);gap:8px}.preflight .box{background:var(--panel2);border:1px solid var(--border);border-radius:10px;padding:9px}.guideWrap{position:relative;display:inline-block}.guideWrap:after{content:"";position:absolute;left:18%;top:8%;width:64%;height:84%;border:2px dashed rgba(50,213,242,.5);border-radius:50%;pointer-events:none}@media(max-width:980px){.main{grid-template-columns:1fr}.verdicts,.preflight{grid-template-columns:1fr 1fr}.setupgrid,.taskbar{grid-template-columns:1fr}.wrap{padding:16px}}
</style></head><body><div class="wrap">
<div class="top"><div><div class="eyebrow">WATCH ALIGN · GUIDED QC</div><h1>What do you want to check?</h1><p>Pick a task, add your watch photo, and Watch Align handles the technical setup.</p></div><div class="pill">V1 1.2.0</div></div>
<div class="taskbar"><button class="task active" data-task="qc"><b>Check my watch</b><span>Analyse dial, markers, bezel and date geometry.</span></button><button class="task" data-task="gen"><b>Compare with genuine</b><span>Automatically use the best official reference available.</span></button><button class="task" data-task="manual"><b>Manual overlay</b><span>Open the original fine-control comparison tool.</span></button></div>
<section class="card" id="setup"><div class="setupgrid"><div class="field"><label>Watch model</label><select id="model"></select><div id="modelSuggestion" class="muted"></div></div><div class="field"><label>Your watch photo</label><input id="candidate" type="file" accept="image/*"></div></div>
<div id="preflight" class="notice hidden"><b>Photo pre-check</b><div class="preflight" id="preflightBoxes"></div><div id="preflightAdvice" class="muted" style="margin-top:8px"></div></div>
<div id="referenceBox" class="notice hidden"><div class="preview"><div id="refThumb" class="hidden"><img id="refImg" alt="Official reference preview"></div><div><b id="refHeadline">Official reference</b><div id="refText" class="muted">Checking availability…</div><div class="actions"><button id="syncRef" class="ghost" type="button">Find official reference</button><select id="refSelector" class="hidden"></select></div></div></div><details><summary>Advanced · use my own genuine/reference image</summary><div class="field" style="margin-top:10px"><label>Custom reference image</label><input id="reference" type="file" accept="image/*"></div></details></div>
<div class="actions"><button id="analyse" class="primary">Analyse watch</button><button id="references" class="secondary">Reference library</button></div><div id="status" class="muted" style="margin-top:10px">Choose a photo to begin.</div></section>
<div class="main"><section id="result" class="card viewerCard hidden"><div id="measurementWarning" class="notice warn hidden"></div><div id="verdicts" class="verdicts"></div><div class="tabs"><button class="tab active" data-view="annotated">QC view</button><button class="tab" data-view="reference">Genuine</button><button class="tab" data-view="aligned">Aligned</button><button class="tab" data-view="overlay">Overlay</button><button class="tab" data-view="edges">Edges</button></div><div class="viewer"><img id="view" alt="Watch analysis"><img id="layer" class="layer hidden" alt="Overlay layer"></div><div class="controls"><label id="opacityWrap" class="hidden">Overlay opacity <input id="opacity" type="range" min="0" max="100" value="50"></label><button id="blink" class="secondary hidden">Blink genuine / watch</button><button id="report" class="secondary">Download report</button></div></section>
<aside id="side" class="card hidden"><h2 style="margin-top:0">Result</h2><div id="plainFinding" class="notice"></div><h3>Areas</h3><div id="regions" class="regionGrid"></div><details open><summary>Technical details</summary><div id="summary" class="metrics" style="margin-top:10px"></div></details><details><summary>Hour-marker details</summary><div id="markers" class="metrics" style="margin-top:10px"></div></details><details><summary>Recent comparisons</summary><div id="history"></div></details></aside></div>
</div><script src="/static/v1-full.js"></script></body></html>'''

UX_JS = r'''const state={models:[],result:null,view:'annotated',task:'qc',blinkTimer:null};const $=id=>document.getElementById(id);async function api(url,opt={}){const r=await fetch(url,opt);const ct=r.headers.get('content-type')||'';const b=ct.includes('json')?await r.json():await r.text();if(!r.ok)throw new Error(b.detail||b||`Request failed ${r.status}`);return b}function cls(v){return v==='high'||v==='good'?'good':v==='medium'||v==='partial'||v==='acceptable'?'warn':'bad'}function titleCase(s){return String(s||'').replaceAll('_',' ').replace(/\b\w/g,c=>c.toUpperCase())}async function loadModels(){const d=await api('/api/v1/models/full');state.models=d.models;$('model').innerHTML=d.models.map(m=>`<option value="${m.reference}">${m.brand} ${m.name}</option>`).join('');const saved=localStorage.getItem('wa-model');if(saved&&d.models.some(m=>m.reference===saved))$('model').value=saved;await referenceStatus();renderHistory()}function setTask(t){state.task=t;document.querySelectorAll('.task').forEach(b=>b.classList.toggle('active',b.dataset.task===t));if(t==='manual'){location.href='/';return}$('referenceBox').classList.toggle('hidden',t!=='gen');$('analyse').textContent=t==='gen'?'Compare with genuine':'Analyse watch';localStorage.setItem('wa-task',t);if(t==='gen')referenceStatus()}document.querySelectorAll('.task').forEach(b=>b.onclick=()=>setTask(b.dataset.task));$('model').onchange=()=>{localStorage.setItem('wa-model',$('model').value);referenceStatus()};$('references').onclick=()=>location.href='/v1/references';async function preflight(file){const f=new FormData();f.append('image',file);$('preflight').classList.remove('hidden');$('preflightBoxes').innerHTML='<div class="box">Checking…</div>';try{const d=await api('/api/v1/ux/preflight',{method:'POST',body:f});$('preflightBoxes').innerHTML=[['Resolution',d.resolution,d.resolution_ok?'good':'bad'],['Sharpness',d.sharpness_label,d.sharpness_ok?'good':'warn'],['Watch framing',d.circle_detected?'Detected':'Not detected',d.circle_detected?'good':'bad'],['Photo angle',d.perspective_label,d.perspective_suitability==='high'?'good':d.perspective_suitability==='medium'?'warn':'bad']].map(x=>`<div class="box"><small class="muted">${x[0]}</small><div class="${x[2]}">${x[1]}</div></div>`).join('');$('preflightAdvice').textContent=d.advice||'';if(d.model_suggestion){$('modelSuggestion').innerHTML=`Suggested model: <b>${d.model_suggestion}</b> · ${d.model_confidence} confidence`;if(d.model_confidence==='high'&&state.models.some(m=>m.reference===d.model_suggestion)){$('model').value=d.model_suggestion;localStorage.setItem('wa-model',d.model_suggestion);referenceStatus()}}}catch(e){$('preflightBoxes').innerHTML='';$('preflightAdvice').textContent=e.message}}$('candidate').onchange=()=>{const f=$('candidate').files[0];if(f)preflight(f)};async function referenceStatus(){if(state.task!=='gen')return;const ref=$('model').value;if(!ref)return;$('refText').textContent='Checking official/reference library…';$('refThumb').classList.add('hidden');$('refSelector').classList.add('hidden');try{const d=await api('/api/v1/ux/reference-status/'+ref);if(d.references.length){$('refHeadline').textContent=d.official_count?'Official genuine reference ready':'Stored reference ready';$('refText').textContent=d.message;$('refSelector').innerHTML=d.references.map(r=>`<option value="${encodeURIComponent(r.filename)}">${r.label}</option>`).join('');$('refSelector').classList.toggle('hidden',d.references.length<2);showRefPreview(d.references[0])}else{$('refHeadline').textContent='No cached official image yet';$('refText').textContent=d.message}}catch(e){$('refText').textContent=e.message}}function showRefPreview(r){if(!r||!r.preview_url)return;$('refImg').src=r.preview_url;$('refThumb').classList.remove('hidden')}$('refSelector').onchange=async()=>{const d=await api('/api/v1/ux/reference-status/'+$('model').value);const val=decodeURIComponent($('refSelector').value);showRefPreview(d.references.find(r=>r.filename===val))};$('syncRef').onclick=async()=>{$('refText').textContent='Finding official manufacturer references…';try{await api('/api/v1/official-sources/'+$('model').value+'/sync',{method:'POST'});await referenceStatus()}catch(e){$('refText').textContent=e.message}};function metric(n,v,c=''){return `<div class="metric" title="Click technical sections only when you need the detail"><strong>${n}</strong><span class="${c}">${v}</span></div>`}function regionVerdict(name,conf,reliable=true){const text=!reliable?'Cannot judge':conf==='high'?'Looks good':conf==='medium'?'Check visually':'Possible issue';return `<div class="region"><b>${name}</b><div class="${!reliable?'warn':cls(conf)}">${text}</div></div>`}function verdict(label,value,help){return `<div class="verdict" title="${help||''}"><small>${label}</small><strong class="${cls(value)}">${titleCase(value)}</strong></div>`}function plainFinding(r){const c=r.metrics.reference_consensus;const visual=r.metrics.visual_alignment_confidence||r.metrics.overall_confidence;const rel=r.metrics.measurement_reliability;if(c?.summary)return c.summary;if(visual==='high'&&rel!=='low')return 'Strong visual match. No obvious geometry problem is being flagged by this image.';if(visual==='high')return 'Strong visual match, but this photo is not suitable for every precise measurement.';if(visual==='medium')return 'Broad geometry looks usable, but inspect amber areas rather than treating small differences as defects.';return 'This image is not strong enough for a reliable overall judgement.'}function show(r){state.result=r;$('result').classList.remove('hidden');$('side').classList.remove('hidden');const m=r.metrics,p=m.perspective||{},regions=m.region_confidence||{};$('measurementWarning').textContent=m.measurement_warning||'';$('measurementWarning').classList.toggle('hidden',!m.measurement_warning);$('verdicts').innerHTML=verdict('Visual match',m.visual_alignment_confidence||m.overall_confidence,'How well stable watch geometry aligns')+verdict('Measurements',m.measurement_reliability||'low','Whether precise measurements are safe')+verdict('Photo suitability',m.perspective_suitability||'low','Whether camera geometry supports tiny measurements')+verdict('Multi-reference',m.reference_consensus?.level||'n/a','Agreement across suitable official references');$('plainFinding').textContent=plainFinding(r);const markerReliable=(m.markers||[]).some(x=>x.reliable!==false&&x.available);$('regions').innerHTML=regionVerdict('Dial',regions.dial,regions.dial!=='low')+regionVerdict('Hour markers',regions.markers,markerReliable)+regionVerdict('Bezel',regions.bezel,m.bezel?.reliable!==false)+((m.date_window)?regionVerdict('Date / cyclops',regions['date/cyclops'],m.date_window?.reliable!==false):'');let h='';h+=metric('Model',`${r.model.reference} · ${r.model.name}`);if(r.reference_status)h+=metric('Reference used',r.reference_status,r.reference_status.toLowerCase().includes('official')?'good':'warn');if(m.reference_consensus)h+=metric('Reference consensus',m.reference_consensus.detail||m.reference_consensus.summary,cls(m.reference_consensus.level));if(p.candidate?.available)h+=metric('Perspective distortion estimate',`${p.candidate.tilt_deg}° equivalent`,cls(m.perspective_suitability));if(p.mismatch_deg!=null)h+=metric('Perspective mismatch',`${p.mismatch_deg}°`,p.mismatch_deg<4?'good':p.mismatch_deg<10?'warn':'bad');if(m.bezel?.reliable!==false&&m.bezel?.offset_deg!=null)h+=metric('Bezel 12 estimate',`${m.bezel.offset_deg>=0?'+':''}${m.bezel.offset_deg}°`,cls(m.bezel.confidence));if(m.date_window?.reliable!==false&&m.date_window?.x_offset_percent!=null)h+=metric('Date-window position',`x ${m.date_window.x_offset_percent>=0?'+':''}${m.date_window.x_offset_percent}% · y ${m.date_window.y_offset_percent>=0?'+':''}${m.date_window.y_offset_percent}%`,cls(m.date_window.confidence));$('summary').innerHTML=h;$('markers').innerHTML=(m.markers||[]).filter(x=>x.reliable!==false&&x.angular_error_deg!=null).map(x=>metric(`${x.hour} o'clock`,`${x.angular_error_deg>=0?'+':''}${x.angular_error_deg}° · radial ${x.radial_error_percent>=0?'+':''}${x.radial_error_percent}%`,cls(x.confidence))).join('')||'<span class="muted">No precise marker measurements available for this image.</span>';saveHistory(r);switchView('annotated');renderHistory()}function switchView(v){state.view=v;document.querySelectorAll('.tab').forEach(b=>b.classList.toggle('active',b.dataset.view===v));clearInterval(state.blinkTimer);state.blinkTimer=null;$('blink').textContent='Blink genuine / watch';const base=state.result?.images?.[v]||state.result?.images?.annotated;$('view').src=base;$('layer').classList.add('hidden');$('opacityWrap').classList.add('hidden');$('blink').classList.add('hidden');if(v==='overlay'&&state.result?.images?.reference&&state.result?.images?.aligned){$('view').src=state.result.images.reference;$('layer').src=state.result.images.aligned;$('layer').classList.remove('hidden');$('layer').style.opacity=String(Number($('opacity').value)/100);$('opacityWrap').classList.remove('hidden');$('blink').classList.remove('hidden')}}document.querySelectorAll('.tab').forEach(b=>b.onclick=()=>switchView(b.dataset.view));$('opacity').oninput=()=>{$('layer').style.opacity=String(Number($('opacity').value)/100)};$('blink').onclick=()=>{if(state.blinkTimer){clearInterval(state.blinkTimer);state.blinkTimer=null;$('blink').textContent='Blink genuine / watch';return}let on=false;$('blink').textContent='Stop blinking';state.blinkTimer=setInterval(()=>{on=!on;$('layer').style.opacity=on?'1':'0'},650)};$('analyse').onclick=async()=>{const c=$('candidate').files[0];if(!c){$('status').textContent='Choose your watch photo first.';return}const f=new FormData();f.append('mode',state.task==='gen'?'gen':'qc');f.append('model_ref',$('model').value);f.append('candidate',c);const ref=$('reference')?.files?.[0];if(ref)f.append('reference',ref);if(state.task==='gen'&&$('refSelector').value)f.append('preferred_reference',decodeURIComponent($('refSelector').value));$('status').textContent=state.task==='gen'?'Selecting the closest genuine reference and comparing…':'Analysing watch geometry…';$('analyse').disabled=true;try{const r=await api('/api/v1/analyse',{method:'POST',body:f});show(r);$('status').textContent='Analysis complete.'}catch(e){$('status').textContent=e.message}finally{$('analyse').disabled=false}};$('report').onclick=()=>{if(!state.result)return;const a=document.createElement('a');a.href=state.result.report_url;a.download=`WatchAlign-${state.result.model.reference}-report.png`;a.click()};function saveHistory(r){try{let h=JSON.parse(localStorage.getItem('wa-history')||'[]');h.unshift({when:new Date().toISOString(),model:r.model.reference,mode:r.mode,session_id:r.session_id,visual:r.metrics.visual_alignment_confidence||r.metrics.overall_confidence,reliability:r.metrics.measurement_reliability,summary:plainFinding(r),result:r});h=h.slice(0,10);localStorage.setItem('wa-history',JSON.stringify(h))}catch{}}function renderHistory(){let h=[];try{h=JSON.parse(localStorage.getItem('wa-history')||'[]')}catch{}$('history').innerHTML=h.length?h.map((x,i)=>`<div class="historyItem" data-i="${i}"><b>${x.model}</b> · ${titleCase(x.visual)}<br><small class="muted">${new Date(x.when).toLocaleString()} · ${x.summary}</small></div>`).join(''):'<span class="muted">No previous comparisons on this machine.</span>';document.querySelectorAll('.historyItem').forEach(el=>el.onclick=()=>{const x=h[Number(el.dataset.i)];if(x?.result)show(x.result)})}const savedTask=localStorage.getItem('wa-task');setTask(savedTask==='gen'?'gen':'qc');loadModels().catch(e=>$('status').textContent=e.message);'''


def _sharpness(image: np.ndarray) -> float:
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    return float(cv2.Laplacian(gray, cv2.CV_64F).var())


def _model_suggestion(image: np.ndarray, circle) -> tuple[str | None, str]:
    if circle is None:
        return None, "low"
    cx, cy, r = circle
    hsv = cv2.cvtColor(image, cv2.COLOR_BGR2HSV)
    yy, xx = np.indices(image.shape[:2])
    rr = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
    ring = (rr >= r * 0.92) & (rr <= r * 1.28)
    h, s, v = cv2.split(hsv)
    blue = ring & (h >= 90) & (h <= 135) & (s >= 70) & (v >= 45)
    denom = max(1, int(ring.sum()))
    ratio = float(blue.sum()) / denom
    if ratio >= 0.035:
        return "126710BLNR", "high"
    if ratio >= 0.012:
        return "126710BLNR", "medium"
    return "124060", "medium"


def _cached_reference_rows(backend, reference_library_module, model_ref: str) -> list[dict[str, Any]]:
    rows = []
    for meta in reference_library_module.list_references(backend, model_ref):
        filename = str(meta.get("filename", ""))
        if not filename:
            continue
        official = meta.get("verification") == "official-manufacturer-source"
        trusted = bool(meta.get("trusted"))
        if not (official or trusted):
            continue
        variant = meta.get("variant") or ("official" if official else "stored")
        label = f"{variant} · {'official manufacturer' if official else 'trusted stored reference'}"
        rows.append({**meta, "official": official, "label": label, "preview_url": f"/api/v1/ux/reference-image/{model_ref}/{filename}"})
    return rows


def _reference_label(meta: dict[str, Any]) -> str:
    if meta.get("official"):
        return f"official Rolex reference: {meta.get('variant','official')} · {meta.get('model_code','')} · {meta.get('source_kind','manufacturer source')}"
    return f"user-trusted reference: {meta.get('variant','stored')} · {meta.get('filename','reference')}"


def _decode_upload(upload: UploadFile, backend) -> tuple[bytes, np.ndarray]:
    raw = upload.file.read(backend.MAX_UPLOAD_BYTES + 1)
    upload.file.seek(0)
    if len(raw) > backend.MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=413, detail="Image is too large")
    image = cv2.imdecode(np.frombuffer(raw, dtype=np.uint8), cv2.IMREAD_COLOR)
    if image is None:
        raise HTTPException(status_code=422, detail="Image is not readable")
    return raw, backend.resize_max(image)


def _reference_score(backend, candidate_image: np.ndarray, ref_image: np.ndarray) -> float:
    def sig(img):
        c = backend.detect_watch_circle(img)
        h, w = img.shape[:2]
        if c is None:
            return (0.5, 0.5, 0.25, w / max(h, 1))
        cx, cy, r = map(float, c)
        return (cx / w, cy / h, r / max(w, h), w / max(h, 1))
    a, b = sig(candidate_image), sig(ref_image)
    return abs(a[0]-b[0])*.6 + abs(a[1]-b[1])*.6 + abs(a[2]-b[2])*2.2 + abs(a[3]-b[3])*.15


def _upload_from_bytes(name: str, raw: bytes) -> UploadFile:
    return UploadFile(filename=name, file=io.BytesIO(raw))


def _consensus(results: list[dict[str, Any]]) -> dict[str, Any]:
    if len(results) < 2:
        return {"level": "n/a", "summary": "Single-reference comparison; multi-reference consensus is not available yet.", "detail": "1 suitable reference"}
    ranks = {"low": 0, "medium": 1, "high": 2}
    visuals = [str(r.get("metrics", {}).get("visual_alignment_confidence") or r.get("metrics", {}).get("overall_confidence") or "low") for r in results]
    avg = sum(ranks.get(v, 0) for v in visuals) / len(visuals)
    level = "high" if avg >= 1.65 else "medium" if avg >= 1.0 else "low"
    bezel = [r.get("metrics", {}).get("bezel", {}).get("offset_deg") for r in results if r.get("metrics", {}).get("bezel", {}).get("reliable") is not False and r.get("metrics", {}).get("bezel", {}).get("offset_deg") is not None]
    datex = [r.get("metrics", {}).get("date_window", {}).get("x_offset_percent") for r in results if isinstance(r.get("metrics", {}).get("date_window"), dict) and r.get("metrics", {}).get("date_window", {}).get("reliable") is not False and r.get("metrics", {}).get("date_window", {}).get("x_offset_percent") is not None]
    bezel_stable = len(bezel) >= 2 and (max(bezel)-min(bezel)) <= 0.75
    date_stable = len(datex) >= 2 and (max(datex)-min(datex)) <= 2.5
    stable_bits = []
    if bezel_stable: stable_bits.append("bezel")
    if date_stable: stable_bits.append("date")
    if level == "high":
        summary = f"Strong match across {len(results)} suitable references. No defect should be flagged unless it repeats across references."
    elif level == "medium":
        summary = f"Broad agreement across {len(results)} references, but small differences are reference-dependent."
    else:
        summary = f"References disagree materially. Treat apparent defects as inconclusive rather than watch faults."
    return {"level": level, "summary": summary, "detail": f"{len(results)} references · stable: {', '.join(stable_bits) if stable_bits else 'visual geometry only'}", "visual_votes": visuals, "bezel_stable": bezel_stable, "date_stable": date_stable}


def install(backend, v1_full_module, reference_library_module, official_sources_module, perspective_diagnostics) -> None:
    if getattr(backend, "_watch_align_v120_installed", False):
        return
    backend._watch_align_v120_installed = True
    v1_full_module.V1_FULL_VERSION = V120_VERSION
    backend.app.version = V120_VERSION
    (backend.STATIC_DIR / "v1.html").write_text(UX_HTML, encoding="utf-8")
    (backend.STATIC_DIR / "v1-full.js").write_text(UX_JS, encoding="utf-8")

    @backend.app.post("/api/v1/ux/preflight")
    def preflight(image: UploadFile = File(...)):
        _, img = _decode_upload(image, backend)
        h, w = img.shape[:2]
        circle = None
        try:
            circle = backend.detect_refined_circle_full(img)
        except Exception:
            circle = backend.detect_watch_circle(img)
        sharp = _sharpness(img)
        perspective = perspective_diagnostics(img, circle) if circle is not None else {"available": False}
        tilt = float(perspective.get("tilt_deg", 0.0)) if perspective.get("available") else None
        suitability = "high" if tilt is not None and tilt < 12 else "medium" if tilt is not None and tilt < 24 else "low"
        suggestion, suggestion_conf = _model_suggestion(img, circle)
        resolution_ok = min(h, w) >= 700
        sharp_ok = sharp >= 70
        advice = []
        if not resolution_ok: advice.append("Use the original higher-resolution QC photo if possible.")
        if not sharp_ok: advice.append("The image looks soft; a sharper photo will improve marker measurements.")
        if circle is None: advice.append("Keep the full watch head visible and close to the centre of the image.")
        if suitability == "low": advice.append("A straighter photo would make bezel/date measurements safer.")
        if not advice: advice.append("Photo looks suitable for analysis.")
        return {"resolution": f"{w}×{h}", "resolution_ok": resolution_ok, "sharpness": round(sharp, 1), "sharpness_ok": sharp_ok, "sharpness_label": "Good" if sharp_ok else "Soft", "circle_detected": circle is not None, "perspective_distortion": tilt, "perspective_suitability": suitability, "perspective_label": "Unknown" if tilt is None else f"{tilt:.1f}° equivalent", "model_suggestion": suggestion, "model_confidence": suggestion_conf, "advice": " ".join(advice)}

    @backend.app.get("/api/v1/ux/reference-status/{model_ref}")
    def reference_status(model_ref: str):
        if model_ref not in v1_full_module.MODEL_GEOMETRY:
            raise HTTPException(status_code=404, detail="Unknown model")
        rows = _cached_reference_rows(backend, reference_library_module, model_ref)
        official_count = sum(1 for r in rows if r.get("official"))
        if rows:
            message = f"{official_count} official and {len(rows)-official_count} trusted stored reference(s) available. Watch Align will choose the closest photographic geometry automatically."
        elif model_ref in official_sources_module.OFFICIAL_SOURCES:
            message = "Official manufacturer sources are supported and will be fetched automatically when you compare."
        else:
            message = "No official automatic source is configured for this model yet; add a trusted reference in Advanced or the reference library."
        return {"model_ref": model_ref, "official_count": official_count, "references": rows, "message": message}

    @backend.app.get("/api/v1/ux/reference-image/{model_ref}/{filename}")
    def reference_image(model_ref: str, filename: str):
        root = reference_library_module._root(backend, model_ref).resolve()
        path = (root / Path(filename).name).resolve()
        if path.parent != root or not path.exists() or path.suffix.lower() not in {".png", ".jpg", ".jpeg", ".webp"}:
            raise HTTPException(status_code=404, detail="Reference image not found")
        return FileResponse(path)

    # Wrap the already-installed Gen Compare route. Automatic comparisons use
    # the cached official image whose framing is closest to the candidate, and
    # then sample other official references for a consensus result.
    for route in getattr(backend.app, "routes", []):
        if getattr(route, "path", None) == "/api/v1/analyse" and hasattr(route, "dependant"):
            previous = route.dependant.call

            def analyse_v120(*args, __previous=previous, **kwargs):
                mode = kwargs.get("mode")
                model_ref = kwargs.get("model_ref")
                candidate = kwargs.get("candidate")
                uploaded_ref = kwargs.get("reference")
                auto = mode == "gen" and model_ref in official_sources_module.OFFICIAL_SOURCES and not (uploaded_ref is not None and bool(getattr(uploaded_ref, "filename", "")))
                candidate_raw = None
                candidate_image = None
                chosen_meta = None
                candidates = []
                sync_error = None
                if auto and candidate is not None:
                    from comparison_progress import report
                    report('Selecting the closest genuine reference…')
                    candidate_raw, candidate_image = _decode_upload(candidate, backend)
                    rows = _cached_reference_rows(backend, reference_library_module, model_ref)
                    if not any(r.get("official") for r in rows):
                        report('Downloading genuine reference images…')
                        try:
                            official_sources_module.sync_official_references(backend, model_ref)
                        except Exception as exc:
                            sync_error = exc
                        rows = _cached_reference_rows(backend, reference_library_module, model_ref)
                    root = reference_library_module._root(backend, model_ref)
                    for meta in rows:
                        path = root / meta["filename"]
                        ref_img = cv2.imread(str(path))
                        if ref_img is None:
                            continue
                        score = _reference_score(backend, candidate_image, ref_img)
                        candidates.append((score, meta, path))
                    candidates.sort(key=lambda x: x[0])
                    preferred = kwargs.pop("preferred_reference", None)
                    if preferred:
                        candidates.sort(key=lambda x: (0 if x[1].get("filename") == preferred else 1, x[0]))
                    if candidates:
                        _, chosen_meta, chosen_path = candidates[0]
                        kwargs["reference"] = _upload_from_bytes(chosen_path.name, chosen_path.read_bytes())
                    elif sync_error is not None:
                        raise HTTPException(status_code=422, detail="Official Rolex reference lookup is blocked right now. Upload your own genuine/reference image in Advanced, or add a trusted reference in the Reference library.")
                else:
                    kwargs.pop("preferred_reference", None)

                result = __previous(*args, **kwargs)
                if auto and chosen_meta is not None and isinstance(result, dict):
                    result["reference_status"] = _reference_label(chosen_meta)
                    result.setdefault("metrics", {})["reference_selection"] = {"strategy": "closest photographic geometry", "selected": chosen_meta.get("filename"), "candidate_count": len(candidates)}
                    consensus_results = [result]
                    # Two additional references are enough to detect whether an
                    # apparent issue is stable without making normal use too slow.
                    for _, meta, path in candidates[1:3]:
                        try:
                            report('Checking agreement with another genuine reference…')
                            clone_candidate = _upload_from_bytes(getattr(candidate, "filename", "candidate.jpg") or "candidate.jpg", candidate_raw)
                            clone_reference = _upload_from_bytes(path.name, path.read_bytes())
                            rr = __previous(mode="gen", model_ref=model_ref, candidate=clone_candidate, reference=clone_reference)
                            if isinstance(rr, dict):
                                consensus_results.append(rr)
                        except Exception:
                            continue
                    result["metrics"]["reference_consensus"] = _consensus(consensus_results)
                elif isinstance(result, dict):
                    result.setdefault("metrics", {})["reference_consensus"] = {"level": "n/a", "summary": "Manual or single-reference analysis; multi-reference consensus was not applied.", "detail": "single/manual reference"}
                return result

            route.dependant.call = analyse_v120
            route.endpoint = analyse_v120
            break
