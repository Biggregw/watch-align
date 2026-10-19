#!/usr/bin/env python3
"""Prepare four hash-pinned emulator regressions; missing fixtures are fatal."""
import json
from pathlib import Path
from fetch_cases import fetch

ROOT = Path(__file__).resolve().parents[3]
manifest = Path(__file__).with_name("android_fixtures.json")
rows = json.loads(manifest.read_text())
if len(rows) != 4 or len({r["photo_id"] for r in rows}) != 4:
    raise SystemExit("Incomplete Android regression manifest")
dest = ROOT / "android/app/src/androidTest/assets/marker_regression"
dest.mkdir(parents=True, exist_ok=True)
if not fetch(rows, dest, dest / "fetch.json"):
    raise SystemExit("Missing verified Android regression fixture")
(dest / "manifest.json").write_text(manifest.read_text())
