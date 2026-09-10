from __future__ import annotations

import hashlib
import html
import json
import math
import re
import time
import urllib.request
from pathlib import Path
from typing import Any

import cv2
import fitz
import numpy as np
from fastapi import HTTPException

OFFICIAL_SOURCES = {
    "126710BLNR": [
        {
            "variant": "Jubilee",
            "model_code": "m126710blnr-0002",
            "page": "https://www.rolex.com/en-gb/watches/gmt-master-ii/m126710blnr-0002",
            "brochure": "https://assets.rolex.com/api/brochure/en/gmt-master-ii/m126710blnr-0002.pdf",
            "brand": "Rolex",
            "reference": "126710BLNR",
        },
        {
            "variant": "Oyster",
            "model_code": "m126710blnr-0003",
            "page": "https://www.rolex.com/en-gb/watches/gmt-master-ii/m126710blnr-0003",
            "brochure": "https://assets.rolex.com/api/brochure/en/gmt-master-ii/m126710blnr-0003.pdf",
            "brand": "Rolex",
            "reference": "126710BLNR",
        },
    ]
}

IMG_RE = re.compile(r'https?://[^\"\'<>\\ ]+', re.I)
MIN_REFERENCE_SIZE = 450
MIN_WATCH_HEAD_RATIO = 0.32
MAX_WATCH_HEAD_RATIO = 0.92
MIN_CIRCLE_EDGE_SUPPORT = 0.42


def _root(backend, model_ref: str) -> Path:
    p = backend.PERSIST_DIR / "runtime" / "reference-packs" / model_ref
    p.mkdir(parents=True, exist_ok=True)
    return p


def _fetch(url: str, timeout: int = 12) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 WatchAlign/1.1.1"})
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
    out.sort(key=lambda u: (("rolex" not in u.lower()), ("media" not in u.lower()), -len(u)))
    return out


def _pdf_product_image(pdf_bytes: bytes) -> bytes:
    doc = fitz.open(stream=pdf_bytes, filetype="pdf")
    try:
        best: tuple[int, bytes] | None = None
        for page_index in range(min(len(doc), 2)):
            page = doc[page_index]
            for image_info in page.get_images(full=True):
                xref = int(image_info[0])
                extracted = doc.extract_image(xref)
                raw = extracted.get("image")
                if not raw:
                    continue
                arr = cv2.imdecode(np.frombuffer(raw, dtype=np.uint8), cv2.IMREAD_COLOR)
                if arr is None:
                    continue
                h, w = arr.shape[:2]
                area = int(h * w)
                if _is_usable_watch_reference(arr) and (best is None or area > best[0]):
                    best = (area, raw)
        if best is not None:
            return best[1]

        page = doc[0]
        pix = page.get_pixmap(matrix=fitz.Matrix(2.0, 2.0), alpha=False)
        arr = np.frombuffer(pix.samples, dtype=np.uint8).reshape(pix.height, pix.width, pix.n)
        arr = cv2.cvtColor(arr, cv2.COLOR_RGBA2BGR if pix.n == 4 else cv2.COLOR_RGB2BGR)
        ok, encoded = cv2.imencode(".jpg", arr, [int(cv2.IMWRITE_JPEG_QUALITY), 94])
        if not ok:
            raise ValueError("Could not render official Rolex brochure")
        return encoded.tobytes()
    finally:
        doc.close()


def _is_usable_watch_reference(image: np.ndarray) -> bool:
    if image is None or min(image.shape[:2]) < MIN_REFERENCE_SIZE:
        return False

    h, w = image.shape[:2]
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
    gray = cv2.GaussianBlur(gray, (9, 9), 1.8)
    min_dim = min(h, w)
    min_radius = max(80, int(min_dim * 0.16))
    max_radius = int(min_dim * 0.46)
    if max_radius <= min_radius:
        return False

    circles = cv2.HoughCircles(
        gray,
        cv2.HOUGH_GRADIENT,
        dp=1.2,
        minDist=max(120, min_dim // 3),
        param1=90,
        param2=28,
        minRadius=min_radius,
        maxRadius=max_radius,
    )
    if circles is None:
        return False

    edges = cv2.Canny(gray, 50, 130)
    center = np.array([w / 2.0, h / 2.0], dtype=np.float32)
    margin = min_dim * 0.08
    for x, y, r in np.round(circles[0]).astype(int):
        diameter_ratio = (2.0 * float(r)) / float(min_dim)
        if not (MIN_WATCH_HEAD_RATIO <= diameter_ratio <= MAX_WATCH_HEAD_RATIO):
            continue
        if x - r < -margin or y - r < -margin or x + r > w + margin or y + r > h + margin:
            continue
        if np.linalg.norm(np.array([x, y], dtype=np.float32) - center) > min_dim * 0.28:
            continue
        if _circle_edge_support(edges, x, y, r) < MIN_CIRCLE_EDGE_SUPPORT:
            continue
        return True
    return False


def _circle_edge_support(edges: np.ndarray, x: int, y: int, r: int) -> float:
    if r <= 0:
        return 0.0
    h, w = edges.shape[:2]
    hits = 0
    total = 0
    for angle in np.linspace(0.0, 2.0 * math.pi, 144, endpoint=False):
        px = int(round(x + math.cos(angle) * r))
        py = int(round(y + math.sin(angle) * r))
        if px < 0 or py < 0 or px >= w or py >= h:
            continue
        total += 1
        y0 = max(0, py - 2)
        y1 = min(h, py + 3)
        x0 = max(0, px - 2)
        x1 = min(w, px + 3)
        if np.any(edges[y0:y1, x0:x1] > 0):
            hits += 1
    return hits / total if total else 0.0


def _save_official_raw(root: Path, source: dict[str, Any], raw: bytes, asset_url: str, source_kind: str) -> dict[str, Any] | None:
    image = cv2.imdecode(np.frombuffer(raw, dtype=np.uint8), cv2.IMREAD_COLOR)
    if not _is_usable_watch_reference(image):
        return None
    digest = hashlib.sha256(raw).hexdigest()
    name = f"official-{source['model_code']}-{digest[:12]}.jpg"
    path = root / name
    if not path.exists():
        if asset_url.lower().endswith((".jpg", ".jpeg")):
            path.write_bytes(raw)
        else:
            cv2.imwrite(str(path), image, [int(cv2.IMWRITE_JPEG_QUALITY), 96])
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
    meta = {
        "filename": name,
        "source": source["page"],
        "asset_url": asset_url,
        "source_kind": source_kind,
        "trusted": True,
        "verification": "official-manufacturer-source",
        "brand": "Rolex",
        "reference": source["reference"],
        "variant": source["variant"],
        "model_code": source["model_code"],
        "added_at": time.strftime("%Y-%m-%d %H:%M:%S"),
        "sha256": digest,
    }
    path.with_suffix(path.suffix + ".json").write_text(json.dumps(meta, indent=2), encoding="utf-8")
    return meta


def sync_official_references(backend, model_ref: str, max_images: int = 12) -> dict[str, Any]:
    sources = OFFICIAL_SOURCES.get(model_ref, [])
    if not sources:
        raise HTTPException(status_code=404, detail="No official source manifest for this model")
    root = _root(backend, model_ref)
    saved: list[dict[str, Any]] = []
    errors: list[str] = []

    for source in sources:
        count = 0
        try:
            page_text = _fetch(source["page"]).decode("utf-8", "ignore")
            urls = _candidate_urls(page_text, source["model_code"])
            for url in urls:
                if len(saved) >= max_images or count >= 6:
                    break
                try:
                    meta = _save_official_raw(root, source, _fetch(url), url, "product-page-asset")
                    if meta is not None:
                        saved.append(meta)
                        count += 1
                except Exception as exc:
                    errors.append(f"{source['variant']} asset: {exc}")
        except Exception as exc:
            errors.append(f"{source['variant']} page: {exc}")

        if count == 0 and len(saved) < max_images:
            try:
                pdf = _fetch(source["brochure"])
                raw = _pdf_product_image(pdf)
                meta = _save_official_raw(root, source, raw, source["brochure"], "official-brochure")
                if meta is None:
                    raise ValueError("No usable product image found in brochure")
                saved.append(meta)
            except Exception as exc:
                errors.append(f"{source['variant']} brochure: {exc}")

    return {"model_ref": model_ref, "saved": saved, "count": len(saved), "errors": errors, "sources": sources}


def _choose_cached_official(backend, model_ref: str):
    root = _root(backend, model_ref)
    for path in sorted(root.iterdir()):
        if path.suffix.lower() not in {".png", ".jpg", ".jpeg", ".webp"}:
            continue
        meta_path = path.with_suffix(path.suffix + ".json")
        if not meta_path.exists():
            continue
        try:
            meta = json.loads(meta_path.read_text(encoding="utf-8"))
        except Exception:
            continue
        if meta.get("verification") != "official-manufacturer-source":
            continue
        image = cv2.imread(str(path))
        if image is not None:
            variant = meta.get("variant", "official")
            kind = meta.get("source_kind", "official")
            return backend.resize_max(image), f"{path.name} [official Rolex source · {variant} · {kind}]"
    return None, None


def install(backend, v1_full_module, reference_library_module) -> None:
    if getattr(backend, "_watch_align_official_sources_installed", False):
        return
    backend._watch_align_official_sources_installed = True

    original_choose = reference_library_module.choose_reference

    # Cached official images are eligible everywhere, but ordinary page/model
    # rendering must never trigger a network fetch. This keeps startup and the
    # smoke test instant. Network sync is initiated only by a Gen Compare
    # analysis that actually needs a reference.
    def choose_cached_or_original(backend_arg, model_ref: str):
        image, name = _choose_cached_official(backend_arg, model_ref)
        if image is not None:
            return image, name
        return original_choose(backend_arg, model_ref)

    reference_library_module.choose_reference = choose_cached_or_original
    v1_full_module._built_in_reference = choose_cached_or_original

    for route in getattr(backend.app, "routes", []):
        if getattr(route, "path", None) == "/api/v1/analyse" and hasattr(route, "dependant"):
            original_analyse = route.dependant.call

            def analyse_with_official_sync(*args, __original=original_analyse, **kwargs):
                mode = kwargs.get("mode")
                model_ref = kwargs.get("model_ref")
                reference = kwargs.get("reference")
                has_uploaded_ref = reference is not None and bool(getattr(reference, "filename", ""))
                if mode == "gen" and model_ref in OFFICIAL_SOURCES and not has_uploaded_ref:
                    cached, _ = _choose_cached_official(backend, model_ref)
                    if cached is None:
                        result = sync_official_references(backend, model_ref)
                        cached, _ = _choose_cached_official(backend, model_ref)
                        if cached is None:
                            raise HTTPException(status_code=422, detail="Official Rolex reference lookup is blocked right now. Upload your own genuine/reference image in Advanced, or add a trusted reference in the Reference library.")
                return __original(*args, **kwargs)

            route.dependant.call = analyse_with_official_sync
            route.endpoint = analyse_with_official_sync
            break

    @backend.app.get("/api/v1/official-sources/{model_ref}")
    def official_sources(model_ref: str):
        return {"model_ref": model_ref, "sources": OFFICIAL_SOURCES.get(model_ref, [])}

    @backend.app.post("/api/v1/official-sources/{model_ref}/sync")
    def sync_sources(model_ref: str):
        return sync_official_references(backend, model_ref)
