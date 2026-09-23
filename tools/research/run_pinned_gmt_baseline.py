#!/usr/bin/env python3
"""Run the existing genuine GMT baseline against only manually pinned images.

This deliberately reuses build_gmt_genuine_baseline.py unchanged, but replaces
its source discovery with the curated pinned-image manifest. No dealer page is
crawled during measurement.
"""
from __future__ import annotations
import csv
import importlib.util
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "tools" / "research" / "build_gmt_genuine_baseline.py"
PINNED = ROOT / "docs" / "research" / "gmt-genuine-pinned-image-manifest.csv"
OUT = ROOT / "docs" / "research" / "gmt-genuine-pinned-pilot-results"

spec = importlib.util.spec_from_file_location("genuine_baseline", BASE)
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

with PINNED.open(newline="", encoding="utf-8") as f:
    raw = [r for r in csv.DictReader(f) if r.get("selection_status") == "pinned" and r.get("image_url")]

sources = []
url_by_page = {}
for r in raw:
    page = r["source_page"]
    url_by_page[page] = r["image_url"]
    sources.append({
        "source_id": r["physical_watch_id"],
        "source_class": r["source"],
        "physical_watch_id": r["physical_watch_id"],
        "source_url": page,
        "max_images": "1",
    })

def read_pinned():
    return sources

def pinned_urls(page_url):
    u = url_by_page.get(page_url)
    return ([u] if u else []), "pinned_direct_image"

m.read_manifest = read_pinned
m.extract_image_urls = pinned_urls
m.OUTDIR = OUT
m.OUTDIR.mkdir(parents=True, exist_ok=True)
m.MAX_IMAGES_PER_SOURCE = 1

print(f"Pinned pilot: {len(sources)} independent physical watches; network discovery disabled")
m.main()
