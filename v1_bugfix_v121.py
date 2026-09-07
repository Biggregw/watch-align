from __future__ import annotations

import ssl
import urllib.request

from fastapi import HTTPException

V121_VERSION = "1.2.1"


def _browser_fetch(url: str, timeout: int = 18) -> bytes:
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Safari/537.36",
        "Accept": "text/html,application/xhtml+xml,application/pdf,image/avif,image/webp,image/apng,image/*,*/*;q=0.8",
        "Accept-Language": "en-GB,en;q=0.9",
        "Cache-Control": "no-cache",
        "Pragma": "no-cache",
        "Referer": "https://www.rolex.com/",
    }
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=timeout, context=ssl.create_default_context()) as response:
        return response.read()


def install(backend, v1_full_module, official_sources_module) -> None:
    if getattr(backend, "_watch_align_v121_installed", False):
        return
    backend._watch_align_v121_installed = True
    backend.app.version = V121_VERSION
    v1_full_module.V1_FULL_VERSION = V121_VERSION

    # Rolex intermittently rejects the old WatchAlign-specific user agent.
    official_sources_module._fetch = _browser_fetch

    # Give the manual online-reference action a stable error contract.
    for route in getattr(backend.app, "routes", []):
        if getattr(route, "path", None) == "/api/v1/official-sources/{model_ref}/sync" and hasattr(route, "dependant"):
            def safe_sync(model_ref: str):
                try:
                    result = official_sources_module.sync_official_references(backend, model_ref)
                except HTTPException:
                    raise
                except Exception as exc:
                    raise HTTPException(status_code=502, detail=f"Official reference lookup failed: {type(exc).__name__}: {exc}")
                if result.get("count", 0) < 1:
                    detail = "; ".join(result.get("errors", [])[-4:]) or "manufacturer returned no usable images"
                    raise HTTPException(status_code=502, detail=f"No official reference image could be downloaded. {detail}")
                return result
            route.dependant.call = safe_sync
            route.endpoint = safe_sync
            break

    html_path = backend.STATIC_DIR / "v1.html"
    js_path = backend.STATIC_DIR / "v1-full.js"
    html = html_path.read_text(encoding="utf-8")

    # V1.2's dashed oval was a fixed CSS hint, not a detected watch boundary.
    # It was misleading because it could be far away from the actual watch.
    html = html.replace("V1 1.2.0", "V1 1.2.1").replace("Watch Align V1.2", "Watch Align V1.2.1")
    html = html.replace(
        ".guideWrap{position:relative;display:inline-block}.guideWrap:after{content:\"\";position:absolute;left:18%;top:8%;width:64%;height:84%;border:2px dashed rgba(50,213,242,.5);border-radius:50%;pointer-events:none}",
        ".guideWrap{position:relative;display:inline-block}"
    )
    html = html.replace(
        "Framing guide: keep the watch head inside the dashed oval and as straight to camera as possible.",
        "Photo preview. Keep the full watch head visible, centred and as straight to camera as possible."
    )
    html_path.write_text(html, encoding="utf-8")

    js = js_path.read_text(encoding="utf-8")
    old_sync = "$('syncRef').onclick=async()=>{$('refText').textContent='Finding official manufacturer references…';try{await api('/api/v1/official-sources/'+$('model').value+'/sync',{method:'POST'});await referenceStatus()}catch(e){$('refText').textContent=e.message}};"
    new_sync = "$('syncRef').onclick=async()=>{$('refText').textContent='Finding official manufacturer references…';$('syncRef').disabled=true;try{const d=await api('/api/v1/official-sources/'+$('model').value+'/sync',{method:'POST'});await referenceStatus();$('refText').textContent=`Official reference ready · ${d.count||0} image${(d.count||0)===1?'':'s'} cached locally.`}catch(e){$('refHeadline').textContent='Official lookup failed';$('refText').textContent=e.message||'Could not download an official reference.'}finally{$('syncRef').disabled=false}};"
    js = js.replace(old_sync, new_sync)

    # Revoke preview blob URLs and clear stale results whenever inputs change.
    old_preview = "$('candidate').onchange=()=>{const f=$('candidate').files[0];if(f){$('photoPreviewImg').src=URL.createObjectURL(f);$('photoPreview').classList.remove('hidden');preflight(f)}};"
    new_preview = "let previewUrl=null;$('candidate').onchange=()=>{$('result').classList.add('hidden');$('side').classList.add('hidden');state.result=null;const f=$('candidate').files[0];if(f){if(previewUrl)URL.revokeObjectURL(previewUrl);previewUrl=URL.createObjectURL(f);$('photoPreviewImg').src=previewUrl;$('photoPreview').classList.remove('hidden');preflight(f)}};"
    js = js.replace(old_preview, new_preview)
    js = js.replace("function setTask(t){state.task=t;", "function setTask(t){state.task=t;$('result').classList.add('hidden');$('side').classList.add('hidden');state.result=null;")
    js_path.write_text(js, encoding="utf-8")
