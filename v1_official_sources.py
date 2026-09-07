from __future__ import annotations

import hashlib
import html
import json
import re
import time
import urllib.request
from pathlib import Path
from typing import Any

import cv2
import numpy as np
from fastapi import HTTPException

OFFICIAL_SOURCES = {
    "126710BLNR": [
        {
            "variant": "Jubilee",
            "model_code": "m126710blnr-0002",
            "page": "https://www.rolex.com/en-gb/watches/gmt-master-ii/m126710blnr-0002",
            "brand": "Rolex",
            "reference": "126710BLNR",
        },
        {
            "variant": "Oyster",
            "model_code": "m126710blnr-0003",
            "page": "https://www.rolex.com/en-gb/watches/gmt-master-ii/m126710blnr-0003",
            "brand": "Rolex",
            "reference": "126710BLNR",
        },
    ]
}

IMG_RE = re.compile(r'https?://[^\"\'<>\\ ]+', re.I)


def _root(backend, model_ref: str) -> Path:
    p = backend.PERSIST_DIR / "runtime" / "reference-packs" / model_ref
    p.mkdir(parents=True, exist_ok=True)
    return p


def _fetch(url: str, timeout: int = 20) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 WatchAlign/1.1"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def _candidate_urls(page_html: str, model_code: str) -> list[str]:
    text = html.unescape(page_html).replace("\\/", "/")
    out = []
    seen = set()
    for raw in IMG_RE.findall(text):
        url = raw.rstrip("),;]")
        low = url.lower()
        if model_code.lower() not in low:
            continue
        if not any(ext in low for ext in (".jpg", ".jpeg", ".png", ".webp")):
            continue
        if url not in seen:
            seen.add(url)
            out.append(url)
    # Prefer Rolex media/CDN and larger-looking assets first.
    out.sort(key=lambda u: (("rolex" not in u.lower()), ("media" not in u.lower()), -len(u)))
    return out


def sync_official_references(backend, model_ref: str, max_images: int = 12) -> dict[str, Any]:
    sources = OFFICIAL_SOURCES.get(model_ref, [])
    if not sources:
        raise HTTPException(status_code=404, detail="No official source manifest for this model")
    root = _root(backend, model_ref)
    saved = []
    errors = []
    for source in sources:
        try:
            page_bytes = _fetch(source["page"])
            page_text = page_bytes.decode("utf-8", "ignore")
            urls = _candidate_urls(page_text, source["model_code"])
            count = 0
            for url in urls:
                if len(saved) >= max_images:
                    break
                try:
                    raw = _fetch(url)
                    arr = np.frombuffer(raw, dtype=np.uint8)
                    image = cv2.imdecode(arr, cv2.IMREAD_COLOR)
                    if image is None or min(image.shape[:2]) < 450:
                        continue
                    digest = hashlib.sha256(raw).hexdigest()
                    ext = ".webp" if ".webp" in url.lower() else ".png" if ".png" in url.lower() else ".jpg"
                    name = f"official-{source['model_code']}-{digest[:12]}{ext}"
                    path = root / name
                    if not path.exists():
                        path.write_bytes(raw)
                    meta = {
                        "filename": name,
                        "source": source["page"],
                        "asset_url": url,
                        "trusted": True,
                        "verification": "official-manufacturer-source",
                        "brand": "Rolex",
                        "reference": model_ref,
                        "variant": source["variant"],
                        "model_code": source["model_code"],
                        "added_at": time.strftime("%Y-%m-%d %H:%M:%S"),
                        "sha256": digest,
                    }
                    path.with_suffix(path.suffix + ".json").write_text(json.dumps(meta, indent=2), encoding="utf-8")
                    saved.append(meta)
                    count += 1
                    if count >= 6:
                        break
                except Exception as exc:
                    errors.append(f"{source['variant']} asset: {exc}")
        except Exception as exc:
            errors.append(f"{source['variant']} page: {exc}")
    return {"model_ref": model_ref, "saved": saved, "count": len(saved), "errors": errors, "sources": sources}


def install(backend, v1_full_module, reference_library_module) -> None:
    if getattr(backend, "_watch_align_official_sources_installed", False):
        return
    backend._watch_align_official_sources_installed = True

    original_choose = reference_library_module.choose_reference

    def choose_reference_with_official(backend_arg, model_ref: str):
        image, name = original_choose(backend_arg, model_ref)
        if image is not None:
            return image, name
        if model_ref in OFFICIAL_SOURCES:
            try:
                sync_official_references(backend_arg, model_ref)
                image, name = original_choose(backend_arg, model_ref)
                if image is not None:
                    return image, name.replace("[user-trusted stored reference]", "[official Rolex source]")
            except Exception:
                pass
        return None, None

    reference_library_module.choose_reference = choose_reference_with_official
    v1_full_module._built_in_reference = choose_reference_with_official

    @backend.app.get("/api/v1/official-sources/{model_ref}")
    def official_sources(model_ref: str):
        return {"model_ref": model_ref, "sources": OFFICIAL_SOURCES.get(model_ref, [])}

    @backend.app.post("/api/v1/official-sources/{model_ref}/sync")
    def sync_sources(model_ref: str):
        return sync_official_references(backend, model_ref)
