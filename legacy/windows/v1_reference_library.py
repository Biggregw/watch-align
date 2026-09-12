from __future__ import annotations

import hashlib
import json
import re
import time
from pathlib import Path
from typing import Any

import cv2
from fastapi import File, Form, HTTPException, UploadFile
from fastapi.responses import HTMLResponse

REFERENCE_MANAGER_HTML = r'''<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Watch Align V1 References</title><style>body{font-family:system-ui;background:#08111f;color:#f4f7fb;max-width:1000px;margin:auto;padding:28px}.card{background:#101b2d;border:1px solid #253857;border-radius:16px;padding:18px;margin:14px 0}label{display:block;margin:10px 0 5px;color:#9eb0c9}select,input,button{font:inherit;padding:10px;border-radius:9px}select,input{width:100%;background:#0b1627;color:white;border:1px solid #315076}button{background:#32d5f2;border:0;font-weight:800;cursor:pointer;margin-top:12px}.ref{border-top:1px solid #253857;padding:10px 0}.muted{color:#9eb0c9}.ok{color:#7fe1aa}.warn{color:#ffc96b}a{color:#32d5f2}</style></head><body><p><a href="/v1">← Watch Align V1</a></p><h1>Model reference library</h1><p class="muted">Store multiple genuine/reference photos locally. Images stay on this machine. Provenance is recorded separately so Watch Align never silently labels an unknown image as verified genuine. Only references you explicitly mark as checked/trusted are eligible for automatic Gen Compare selection.</p><div class="card"><label>Model</label><select id="model"></select><label>Reference image</label><input id="image" type="file" accept="image/*"><label>Source / provenance</label><input id="source" placeholder="e.g. my genuine watch photo, Rolex product page, dealer listing"><label><input id="trusted" type="checkbox" style="width:auto"> I have checked this is a genuine/reference image suitable for comparison</label><button id="add">Add reference</button><p id="status" class="muted"></p></div><div class="card"><h2>Stored references</h2><div id="list"></div></div><script>const $=id=>document.getElementById(id);async function api(u,o={}){const r=await fetch(u,o),b=await r.json();if(!r.ok)throw Error(b.detail||'Request failed');return b}async function models(){const d=await api('/api/v1/models/full');$('model').innerHTML=d.models.map(m=>`<option value="${m.reference}">${m.reference} · ${m.name}</option>`).join('');load()}async function load(){const d=await api('/api/v1/references/'+$('model').value);$('list').innerHTML=d.references.length?d.references.map(r=>`<div class="ref"><b>${r.filename}</b> <span class="${r.trusted?'ok':'warn'}">${r.trusted?'trusted by user · auto-eligible':'unverified · not auto-used'}</span><br><span class="muted">${r.source||'No provenance supplied'} · added ${r.added_at}</span></div>`).join(''):'<span class="muted">No stored references for this model.</span>'}$('model').addEventListener('change',load);$('add').onclick=async()=>{const f=new FormData();if(!$('image').files[0])return;$('status').textContent='Adding…';f.append('image',$('image').files[0]);f.append('source',$('source').value);f.append('trusted',$('trusted').checked?'true':'false');try{await api('/api/v1/references/'+$('model').value,{method:'POST',body:f});$('status').textContent='Reference added.';load()}catch(e){$('status').textContent=e.message}};models()</script></body></html>'''


def _safe_name(name: str) -> str:
    stem = re.sub(r"[^A-Za-z0-9._-]+", "-", Path(name or "reference.jpg").name).strip(".-")
    return stem[:120] or "reference.jpg"


def _root(backend, model_ref: str) -> Path:
    path = backend.PERSIST_DIR / "runtime" / "reference-packs" / model_ref
    path.mkdir(parents=True, exist_ok=True)
    return path


def _metadata_path(path: Path) -> Path:
    return path.with_suffix(path.suffix + ".json")


def list_references(backend, model_ref: str) -> list[dict[str, Any]]:
    root = _root(backend, model_ref)
    items = []
    for path in sorted(root.iterdir()):
        if path.suffix.lower() not in {".png", ".jpg", ".jpeg", ".webp"}:
            continue
        meta = {"filename": path.name, "source": "", "trusted": False, "added_at": "unknown", "sha256": None}
        mp = _metadata_path(path)
        if mp.exists():
            try:
                meta.update(json.loads(mp.read_text(encoding="utf-8")))
            except Exception:
                pass
        items.append(meta)
    return items


def choose_reference(backend, model_ref: str):
    """Auto-select only user-trusted persistent references, then packaged refs."""
    root = _root(backend, model_ref)
    trusted = [item for item in list_references(backend, model_ref) if bool(item.get("trusted"))]
    for meta in sorted(trusted, key=lambda x: x.get("filename", "")):
        path = root / meta["filename"]
        image = cv2.imread(str(path))
        if image is not None:
            return backend.resize_max(image), f"{path.name} [user-trusted stored reference]"
    packaged = backend.BASE_DIR / "references" / model_ref / "references"
    if packaged.exists():
        for path in sorted(packaged.iterdir()):
            if path.suffix.lower() in {".png", ".jpg", ".jpeg", ".webp"}:
                image = cv2.imread(str(path))
                if image is not None:
                    return backend.resize_max(image), f"{path.name} [packaged reference]"
    return None, None


def _fix_reference_status(result):
    if not isinstance(result, dict):
        return result
    status = result.get("reference_status")
    if not isinstance(status, str):
        return result
    if "[user-trusted stored reference]" in status:
        result["reference_status"] = status.replace("verified built-in reference:", "user-trusted stored reference:")
    elif "[packaged reference]" in status:
        result["reference_status"] = status.replace("verified built-in reference:", "packaged model reference:")
    return result


def install_reference_library(backend, v1_full_module) -> None:
    if getattr(backend, "_watch_align_reference_library_installed", False):
        return
    backend._watch_align_reference_library_installed = True
    v1_full_module._built_in_reference = choose_reference

    # FastAPI has already built the dependency graph for /api/v1/analyse.
    # Wrap its resolved callable only to correct provenance wording after the
    # stored-reference selector has supplied a reference.
    for route in getattr(backend.app, "routes", []):
        if getattr(route, "path", None) == "/api/v1/analyse" and hasattr(route, "dependant"):
            original = route.dependant.call
            def wrapped_analyse(*args, __original=original, **kwargs):
                return _fix_reference_status(__original(*args, **kwargs))
            route.dependant.call = wrapped_analyse
            route.endpoint = wrapped_analyse
            break

    @backend.app.get("/v1/references", response_class=HTMLResponse)
    def reference_manager():
        return HTMLResponse(REFERENCE_MANAGER_HTML)

    @backend.app.get("/api/v1/references/{model_ref}")
    def references(model_ref: str):
        if model_ref not in v1_full_module.MODEL_GEOMETRY:
            raise HTTPException(status_code=404, detail="Unknown model")
        return {"model_ref": model_ref, "references": list_references(backend, model_ref)}

    @backend.app.post("/api/v1/references/{model_ref}")
    def add_reference(model_ref: str, image: UploadFile = File(...), source: str = Form(""), trusted: bool = Form(False)):
        if model_ref not in v1_full_module.MODEL_GEOMETRY:
            raise HTTPException(status_code=404, detail="Unknown model")
        raw = image.file.read(backend.MAX_UPLOAD_BYTES + 1)
        if len(raw) > backend.MAX_UPLOAD_BYTES:
            raise HTTPException(status_code=413, detail="Reference image is too large")
        np = __import__("numpy")
        decoded = cv2.imdecode(np.frombuffer(raw, dtype=np.uint8), cv2.IMREAD_COLOR)
        if decoded is None:
            raise HTTPException(status_code=422, detail="Reference file is not a readable image")
        root = _root(backend, model_ref)
        digest = hashlib.sha256(raw).hexdigest()
        name = f"{int(time.time())}-{digest[:10]}-{_safe_name(image.filename or 'reference.jpg')}"
        path = root / name
        path.write_bytes(raw)
        meta = {
            "filename": name,
            "source": source.strip()[:500],
            "trusted": bool(trusted),
            "verification": "user-asserted" if trusted else "unverified",
            "added_at": time.strftime("%Y-%m-%d %H:%M:%S"),
            "sha256": digest,
            "original_filename": image.filename,
        }
        _metadata_path(path).write_text(json.dumps(meta, indent=2), encoding="utf-8")
        return meta
