#!/usr/bin/env python3
"""Build an empirical genuine-image geometry baseline for Rolex 126710BLNR.

Research-only. Source pages identify the watches as genuine; this is not a Rolex
manufacturing tolerance study and not an authenticity classifier. The physical
watch is the independent statistical unit. Source photographs are downloaded only
inside CI and are never committed.
"""
from __future__ import annotations

import csv
import hashlib
import html as htmlmod
import json
import math
import os
import re
import sys
import tempfile
from collections import defaultdict
from pathlib import Path
from urllib.parse import urljoin, urlparse

import cv2
import numpy as np
import requests
from bs4 import BeautifulSoup
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parents[2]
PYTOOLS = ROOT / "tools" / "watch_align_py"
sys.path.insert(0, str(PYTOOLS))

import geometry
import gmt_proportional_features as gpf
import marker_consensus as mc
import marker_consensus_analysis as mca
import master
import pipeline

MANIFEST = ROOT / "docs" / "research" / "gmt-genuine-baseline-source-manifest.csv"
OUTDIR = ROOT / "docs" / "research" / "gmt-genuine-baseline-results"
OUTDIR.mkdir(parents=True, exist_ok=True)
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/140 Safari/537.36 WatchAlignResearch/1.0"
HEADERS = {"User-Agent": UA, "Accept-Language": "en-GB,en;q=0.9"}
MAX_IMAGES_PER_SOURCE = 8
MAX_CANDIDATE_URLS = 80


def read_manifest():
    with MANIFEST.open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def add_url(out: list[str], seen: set[str], base: str, value):
    if not value or not isinstance(value, str):
        return
    value = htmlmod.unescape(value.strip())
    if value.startswith("//"):
        value = "https:" + value
    value = urljoin(base, value)
    if not value.startswith(("http://", "https://")):
        return
    low = value.lower()
    if any(x in low for x in ("logo", "icon", "sprite", "avatar", "payment", "flag", "trustpilot", "placeholder")):
        return
    if value not in seen:
        seen.add(value)
        out.append(value)


def walk_json_images(obj, out: list[str], seen: set[str], base: str):
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k.lower() in {"image", "images", "contenturl", "thumbnailurl", "url"}:
                if isinstance(v, str):
                    add_url(out, seen, base, v)
                elif isinstance(v, list):
                    for item in v:
                        if isinstance(item, str):
                            add_url(out, seen, base, item)
                        else:
                            walk_json_images(item, out, seen, base)
                else:
                    walk_json_images(v, out, seen, base)
            else:
                walk_json_images(v, out, seen, base)
    elif isinstance(obj, list):
        for item in obj:
            walk_json_images(item, out, seen, base)


def extract_image_urls(page_url: str) -> tuple[list[str], str]:
    try:
        r = requests.get(page_url, headers=HEADERS, timeout=35, allow_redirects=True)
        status = f"http_{r.status_code}"
        r.raise_for_status()
    except Exception as exc:
        return [], f"page_fetch_failed:{type(exc).__name__}:{exc}"
    base = r.url
    soup = BeautifulSoup(r.text, "html.parser")
    out: list[str] = []
    seen: set[str] = set()

    for meta in soup.find_all("meta"):
        key = (meta.get("property") or meta.get("name") or "").lower()
        if key in {"og:image", "og:image:url", "twitter:image", "twitter:image:src"}:
            add_url(out, seen, base, meta.get("content"))

    for tag in soup.find_all("img"):
        for attr in ("src", "data-src", "data-original", "data-lazy-src", "data-zoom-image", "data-image"):
            add_url(out, seen, base, tag.get(attr))
        for attr in ("srcset", "data-srcset"):
            val = tag.get(attr)
            if val:
                parts = [p.strip().split()[0] for p in val.split(",") if p.strip()]
                for p in reversed(parts):
                    add_url(out, seen, base, p)

    for script in soup.find_all("script", attrs={"type": "application/ld+json"}):
        try:
            obj = json.loads(script.string or script.get_text() or "null")
            walk_json_images(obj, out, seen, base)
        except Exception:
            pass

    # Dealer sites often embed gallery URLs in JSON blobs rather than img tags.
    rx = re.compile(r'https?:\\?/\\?/[^"\\\'<> ]+?\\.(?:jpe?g|png|webp)(?:\\?[^"\\\'<> ]*)?', re.I)
    for m in rx.findall(r.text):
        add_url(out, seen, base, m.replace("\\/", "/"))

    def score(u: str):
        low = u.lower()
        s = 0
        if any(x in low for x in ("126710", "gmt", "rolex", "watch", "product", "zoom", "large", "original")):
            s += 5
        if any(x in low for x in ("thumb", "thumbnail", "small", "100x", "150x")):
            s -= 4
        if urlparse(u).netloc == urlparse(base).netloc:
            s += 1
        return s
    out.sort(key=score, reverse=True)
    return out[:MAX_CANDIDATE_URLS], status


def download_image(url: str):
    try:
        r = requests.get(url, headers=HEADERS, timeout=30, allow_redirects=True, stream=True)
        r.raise_for_status()
        ctype = (r.headers.get("content-type") or "").lower()
        data = r.content
        if len(data) < 25_000 or len(data) > 18_000_000:
            return None
        if "image" not in ctype and not re.search(r"\\.(jpe?g|png|webp)(?:$|\\?)", url, re.I):
            return None
        from io import BytesIO
        with Image.open(BytesIO(data)) as im0:
            im = ImageOps.exif_transpose(im0).convert("RGB")
        if min(im.size) < 500 or im.width * im.height < 500_000:
            return None
        im.thumbnail((1800, 1800), Image.Resampling.LANCZOS)
        arr = cv2.cvtColor(np.array(im), cv2.COLOR_RGB2BGR)
        return arr
    except Exception:
        return None


def image_fingerprint(bgr: np.ndarray) -> str:
    small = cv2.resize(cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY), (32, 32), interpolation=cv2.INTER_AREA)
    return hashlib.sha256(small.tobytes()).hexdigest()


def local_rt(x: float, y: float, hour: int):
    a = master.angle_for_hour(hour)
    ca, sa = math.cos(a), math.sin(a)
    return x * ca + y * sa, -x * sa + y * ca


def marker_features(observations, ellipse, roll, dial_radius_px):
    out = {}
    angular = mca.angular_analysis(observations, ellipse, roll) if observations else {}
    for h, obs in observations.items():
        cx, cy = mc.inverse_map(ellipse, roll, obs.centroid_x, obs.centroid_y)
        ox, oy = mc.inverse_map(ellipse, roll, obs.outer_x, obs.outer_y)
        ix, iy = mc.inverse_map(ellipse, roll, obs.inner_x, obs.inner_y)
        cr, ct = local_rt(cx, cy, h)
        or_, ot = local_rt(ox, oy, h)
        ir, it = local_rt(ix, iy, h)
        p = f"h{h:02d}."
        out[p + "centre_r"] = cr
        out[p + "centre_t"] = ct
        out[p + "outer_r"] = or_
        out[p + "inner_r"] = ir
        out[p + "radial_span"] = or_ - ir
        out[p + "outer_t"] = ot
        out[p + "inner_t"] = it
        out[p + "area_norm"] = obs.area_norm
        out[p + "anisotropy"] = obs.anisotropy
        out[p + "diameter_over_dial"] = obs.diameter_px / max(1.0, dial_radius_px)
        out[p + "segmentation_confidence"] = obs.confidence
        ar = angular.get(h)
        if ar:
            out[p + "angular_residual_deg"] = ar.angular_residual_deg
            if math.isfinite(ar.principal_axis_residual_deg):
                out[p + "axis_residual_deg"] = ar.principal_axis_residual_deg
    return out


def detect_date_center(gray, ellipse, roll):
    """Heuristic date-window centring signal. Returns dx,dy normalized by aperture size.

    This is deliberately diagnostic only. The cyclops, date-wheel print and aperture live
    on different optical/physical planes, so success does not imply zero camera tilt.
    """
    basis = mc._basis_at(ellipse, roll, 0.54, master.angle_for_hour(3))
    if basis is None:
        return None
    centre, rx, ry, tx, ty, det = basis
    if abs(det) < 1e-8:
        return None
    # Broad local box around the aperture/cyclops at 3 o'clock.
    corners = []
    for dr in (-0.18, 0.18):
        for dt in (-0.17, 0.17):
            corners.append((centre[0] + dr * rx + dt * tx, centre[1] + dr * ry + dt * ty))
    x0 = max(0, int(min(x for x, _ in corners)))
    x1 = min(gray.shape[1], int(max(x for x, _ in corners)) + 1)
    y0 = max(0, int(min(y for _, y in corners)))
    y1 = min(gray.shape[0], int(max(y for _, y in corners)) + 1)
    if x1 - x0 < 20 or y1 - y0 < 15:
        return None
    roi = gray[y0:y1, x0:x1]
    blur = cv2.GaussianBlur(roi, (5, 5), 0)
    # Bright aperture: threshold high intensities, then choose a plausible rectangle near ROI centre.
    thr = max(145, int(np.percentile(blur, 72)))
    mask = (blur >= thr).astype(np.uint8) * 255
    mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, np.ones((5, 5), np.uint8))
    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    best = None
    best_score = -1e9
    rcx, rcy = roi.shape[1] / 2, roi.shape[0] / 2
    for c in contours:
        x, y, w, h = cv2.boundingRect(c)
        if w < 0.18 * roi.shape[1] or h < 0.16 * roi.shape[0]:
            continue
        if w > 0.95 * roi.shape[1] or h > 0.95 * roi.shape[0]:
            continue
        aspect = w / max(1.0, h)
        if not (0.8 <= aspect <= 3.2):
            continue
        area = cv2.contourArea(c)
        dist = math.hypot(x + w / 2 - rcx, y + h / 2 - rcy)
        score = area - 8.0 * dist
        if score > best_score:
            best_score, best = score, (x, y, w, h)
    if best is None:
        return None
    x, y, w, h = best
    pad_x, pad_y = max(2, int(0.12 * w)), max(2, int(0.12 * h))
    inner = roi[y + pad_y:y + h - pad_y, x + pad_x:x + w - pad_x]
    if inner.size < 50:
        return None
    # Dark date glyph pixels inside the bright aperture.
    p35 = float(np.percentile(inner, 35))
    dark = np.clip(p35 - inner.astype(np.float32), 0, None)
    dark[inner > p35] = 0
    total = float(dark.sum())
    if total <= 1e-6:
        return None
    yy, xx = np.indices(inner.shape)
    gx = float((xx * dark).sum() / total) + pad_x
    gy = float((yy * dark).sum() / total) + pad_y
    dx = (gx - w / 2) / max(1.0, w)
    dy = (gy - h / 2) / max(1.0, h)
    if abs(dx) > 0.55 or abs(dy) > 0.55:
        return None
    return dx, dy, w, h


def finite(v):
    try:
        return v is not None and math.isfinite(float(v))
    except Exception:
        return False


def median_dict(rows):
    keys = sorted({k for r in rows for k in r.keys() if k not in {"source_id", "source_class", "physical_watch_id", "source_url", "image_index", "image_url", "page_status"}})
    out = {}
    for k in keys:
        vals = [float(r[k]) for r in rows if finite(r.get(k))]
        if vals:
            out[k] = float(np.median(vals))
    return out


def robust_stats(vals):
    a = np.asarray([float(v) for v in vals if finite(v)], dtype=float)
    if len(a) == 0:
        return None
    med = float(np.median(a))
    mad = float(np.median(np.abs(a - med)))
    return {
        "n": int(len(a)), "median": med, "mad": mad,
        "p10": float(np.percentile(a, 10)), "p90": float(np.percentile(a, 90)),
        "min": float(a.min()), "max": float(a.max()),
    }


def rankdata(a):
    a = np.asarray(a, dtype=float)
    order = np.argsort(a)
    ranks = np.empty(len(a), dtype=float)
    i = 0
    while i < len(a):
        j = i
        while j + 1 < len(a) and a[order[j + 1]] == a[order[i]]:
            j += 1
        ranks[order[i:j + 1]] = (i + j) / 2 + 1
        i = j + 1
    return ranks


def spearman(x, y):
    if len(x) < 4:
        return math.nan
    rx, ry = rankdata(x), rankdata(y)
    if np.std(rx) < 1e-12 or np.std(ry) < 1e-12:
        return math.nan
    return float(np.corrcoef(rx, ry)[0, 1])


def write_csv(path: Path, rows: list[dict]):
    keys = []
    for r in rows:
        for k in r:
            if k not in keys:
                keys.append(k)
    with path.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=keys)
        w.writeheader()
        w.writerows(rows)


def main():
    sources = read_manifest()
    all_rows = []
    source_rows = []
    global_fp = set()

    for si, src in enumerate(sources, 1):
        urls, page_status = extract_image_urls(src["source_url"])
        accepted = 0
        downloaded = 0
        pose_ok = 0
        low_tilt = 0
        errors = []
        for url in urls:
            if accepted >= min(MAX_IMAGES_PER_SOURCE, int(src.get("max_images") or MAX_IMAGES_PER_SOURCE)):
                break
            bgr = download_image(url)
            if bgr is None:
                continue
            downloaded += 1
            fp = image_fingerprint(bgr)
            if fp in global_fp:
                continue
            global_fp.add(fp)
            try:
                res = pipeline.build(bgr)
            except Exception as exc:
                errors.append(f"pipeline:{type(exc).__name__}")
                continue
            if res.reason or not res.accepted or res.acquisition is None:
                continue
            pose_ok += 1
            ellipse = res.acquisition.dial_ellipse
            roll = res.solved_roll
            tilt = float(res.tilt_deg)
            gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
            obs = {}
            for h in mc.ALL_MARKER_HOURS:
                try:
                    o = mc.segment_marker(gray, ellipse, roll, res.dial_radius_px, h)
                except Exception:
                    o = None
                if o is not None:
                    obs[h] = o
            row = {
                "source_id": src["source_id"], "source_class": src["source_class"],
                "physical_watch_id": src["physical_watch_id"], "source_url": src["source_url"],
                "image_index": accepted, "image_url": url, "page_status": page_status,
                "tilt_deg": tilt, "pose_confidence": float(res.confidence),
                "dial_radius_px": float(res.dial_radius_px), "n_markers_segmented": len(obs),
            }
            row.update(marker_features(obs, ellipse, roll, res.dial_radius_px))
            try:
                tri = gpf.compute(gray, ellipse, roll, res.dial_radius_px, tilt)
                for k, v in tri.features.items():
                    if finite(v):
                        row["h12.stage3_" + k] = float(v)
            except Exception as exc:
                errors.append(f"triangle:{type(exc).__name__}")
            try:
                dc = detect_date_center(gray, ellipse, roll)
                if dc:
                    dx, dy, ww, hh = dc
                    row["date_dx_window"] = dx
                    row["date_dy_window"] = dy
                    row["date_offset_norm"] = math.hypot(dx, dy)
                    row["date_window_w_px"] = ww
                    row["date_window_h_px"] = hh
            except Exception as exc:
                errors.append(f"date:{type(exc).__name__}")
            all_rows.append(row)
            accepted += 1
            if tilt <= 10.0:
                low_tilt += 1
        source_rows.append({
            "source_id": src["source_id"], "source_class": src["source_class"],
            "physical_watch_id": src["physical_watch_id"], "source_url": src["source_url"],
            "page_status": page_status, "candidate_urls": len(urls), "downloaded_images": downloaded,
            "pose_accepted": pose_ok, "measured_images": accepted, "low_tilt_images": low_tilt,
            "notes": ";".join(sorted(set(errors)))[:500],
        })
        print(f"[{si}/{len(sources)}] {src['source_id']}: candidates={len(urls)} downloaded={downloaded} pose={pose_ok} measured={accepted} low_tilt={low_tilt}")

    write_csv(OUTDIR / "source_status.csv", source_rows)
    write_csv(OUTDIR / "per_image_measurements.csv", all_rows)

    # Primary baseline: <=10 deg, physical-watch medians first.
    low = [r for r in all_rows if finite(r.get("tilt_deg")) and float(r["tilt_deg"]) <= 10.0]
    by_watch = defaultdict(list)
    watch_meta = {}
    for r in low:
        by_watch[r["physical_watch_id"]].append(r)
        watch_meta[r["physical_watch_id"]] = r
    watch_rows = []
    for wid, rows in sorted(by_watch.items()):
        agg = median_dict(rows)
        meta = watch_meta[wid]
        wr = {"physical_watch_id": wid, "source_id": meta["source_id"], "source_class": meta["source_class"], "n_images": len(rows)}
        wr.update(agg)
        watch_rows.append(wr)
    write_csv(OUTDIR / "per_physical_watch_medians.csv", watch_rows)

    excluded = {"physical_watch_id", "source_id", "source_class", "n_images"}
    features = sorted({k for r in watch_rows for k in r if k not in excluded})
    baseline_rows = []
    for feat in features:
        vals = [r.get(feat) for r in watch_rows if finite(r.get(feat))]
        st = robust_stats(vals)
        if not st:
            continue
        marker = feat.split(".", 1)[0] if feat.startswith("h") and "." in feat else "global"
        name = feat.split(".", 1)[1] if "." in feat else feat
        baseline_rows.append({"marker": marker, "feature": name, **st})
    write_csv(OUTDIR / "baseline_watch_level.csv", baseline_rows)

    # Source-class medians, useful for detecting provenance/photography bias.
    class_rows = []
    classes = sorted({r["source_class"] for r in watch_rows})
    for cls in classes:
        subset = [r for r in watch_rows if r["source_class"] == cls]
        for feat in features:
            vals = [r.get(feat) for r in subset if finite(r.get(feat))]
            st = robust_stats(vals)
            if st:
                class_rows.append({"source_class": cls, "feature": feat, **st})
    write_csv(OUTDIR / "baseline_by_source_class.csv", class_rows)

    # Date-centering / viewpoint hypothesis.
    date_rows = [r for r in all_rows if finite(r.get("date_offset_norm")) and finite(r.get("tilt_deg"))]
    dx = [abs(float(r["date_dx_window"])) for r in date_rows]
    dy = [abs(float(r["date_dy_window"])) for r in date_rows]
    doff = [float(r["date_offset_norm"]) for r in date_rows]
    tilt = [float(r["tilt_deg"]) for r in date_rows]
    rho_dx = spearman(dx, tilt)
    rho_dy = spearman(dy, tilt)
    rho_off = spearman(doff, tilt)

    # Within-watch centred correlation, to reduce fixed date-wheel printing/position bias.
    centered_off, centered_tilt = [], []
    dby = defaultdict(list)
    for r in date_rows:
        dby[r["physical_watch_id"]].append(r)
    for wid, rows in dby.items():
        if len(rows) < 2:
            continue
        mo = float(np.mean([float(r["date_offset_norm"]) for r in rows]))
        mt = float(np.mean([float(r["tilt_deg"]) for r in rows]))
        for r in rows:
            centered_off.append(float(r["date_offset_norm"]) - mo)
            centered_tilt.append(float(r["tilt_deg"]) - mt)
    within_r = float(np.corrcoef(centered_off, centered_tilt)[0, 1]) if len(centered_off) >= 4 and np.std(centered_off) > 1e-12 and np.std(centered_tilt) > 1e-12 else math.nan

    # Does date centring also predict agreement between simple and projective 12 radial coordinates?
    agree_x, agree_y = [], []
    for r in date_rows:
        a = r.get("h12.stage3_apex_r_simple")
        b = r.get("h12.stage3_apex_r_projective")
        if finite(a) and finite(b):
            agree_x.append(float(r["date_offset_norm"]))
            agree_y.append(abs(float(a) - float(b)))
    rho_agree = spearman(agree_x, agree_y)

    summary = {
        "manifest_sources": len(sources), "sources_with_measurements": sum(int(r["measured_images"]) > 0 for r in source_rows),
        "sources_with_low_tilt": sum(int(r["low_tilt_images"]) > 0 for r in source_rows),
        "all_measured_images": len(all_rows), "low_tilt_images": len(low),
        "independent_low_tilt_watches": len(watch_rows), "source_classes_low_tilt": classes,
        "date_detected_images": len(date_rows), "date_spearman_abs_dx_vs_tilt": rho_dx,
        "date_spearman_abs_dy_vs_tilt": rho_dy, "date_spearman_offset_vs_tilt": rho_off,
        "date_within_watch_centered_pearson_offset_vs_tilt": within_r,
        "date_spearman_offset_vs_12_apex_simple_projective_disagreement": rho_agree,
    }
    (OUTDIR / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")

    def fmt(v, n=4):
        return "n/a" if not finite(v) else f"{float(v):.{n}f}"

    # Compact human report, with primary marker rows.
    key_names = [
        "h12.stage3_apex_r_simple", "h12.stage3_centre_r_projective", "h12.stage3_base_r_projective",
        "h12.stage3_axis_incidence_canonical", "h12.stage3_centroid_tangential_offset_canonical",
        "h06.centre_r", "h06.centre_t", "h06.axis_residual_deg", "h06.radial_span",
        "h09.centre_r", "h09.centre_t", "h09.axis_residual_deg", "h09.radial_span",
    ]
    bmap = {f"{r['marker']}.{r['feature']}": r for r in baseline_rows}
    lines = [
        "# GMT 126710BLNR genuine-image marker baseline", "",
        "Research-only empirical image distribution. It is not a Rolex factory tolerance and not an authenticity classifier.", "",
        f"- Source manifest: **{len(sources)}** independent listed watches",
        f"- Sources yielding at least one measurement: **{summary['sources_with_measurements']}**",
        f"- Sources yielding a <=10° image: **{summary['sources_with_low_tilt']}**",
        f"- <=10° measured images: **{len(low)}**",
        f"- Independent physical watches in primary baseline: **{len(watch_rows)}**",
        f"- Source classes represented: **{', '.join(classes) if classes else 'none'}**", "",
        "## Primary marker baselines (physical-watch medians, <=10°)", "",
        "| Feature | n watches | median | MAD | p10 | p90 |",
        "|---|---:|---:|---:|---:|---:|",
    ]
    for name in key_names:
        r = bmap.get(name)
        if r:
            lines.append(f"| `{name}` | {r['n']} | {fmt(r['median'])} | {fmt(r['mad'])} | {fmt(r['p10'])} | {fmt(r['p90'])} |")
    lines += ["", "Full round-marker and shape-feature distributions are in `baseline_watch_level.csv`.", "", "## Date-centering viewpoint hypothesis", "",
              f"Date detector produced usable centring values on **{len(date_rows)}** measured images.",
              f"- Spearman |date horizontal offset| vs tilt: **{fmt(rho_dx,3)}**",
              f"- Spearman |date vertical offset| vs tilt: **{fmt(rho_dy,3)}**",
              f"- Spearman total date offset vs tilt: **{fmt(rho_off,3)}**",
              f"- Within-watch centred Pearson total date offset vs tilt: **{fmt(within_r,3)}**",
              f"- Spearman total date offset vs 12-apex simple/projective disagreement: **{fmt(rho_agree,3)}**", "",
              "Interpretation rule: date centring may be a useful *supporting* frontalness signal only if the correlations are consistently positive and there are enough repeated views. It must not be treated as proof of perfect perspective because date-wheel print/position and cyclops optics can create or cancel apparent offsets.", "",
              "## Files", "", "- `source_status.csv`", "- `per_image_measurements.csv`", "- `per_physical_watch_medians.csv`", "- `baseline_watch_level.csv`", "- `baseline_by_source_class.csv`", "- `summary.json`", ""]
    (OUTDIR / "README.md").write_text("\n".join(lines), encoding="utf-8")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
