#!/usr/bin/env python3
"""Prepare four hash-pinned emulator regressions; missing fixtures are fatal."""
import json
import hashlib
import shutil
from pathlib import Path
from fetch_cases import fetch

ROOT = Path(__file__).resolve().parents[3]
manifest = Path(__file__).with_name("android_fixtures.json")
rows = json.loads(manifest.read_text())
if len(rows) != 4 or len({r["photo_id"] for r in rows}) != 4:
    raise SystemExit("Incomplete Android regression manifest")
dest = ROOT / "android/app/src/androidTest/assets/marker_regression"
dest.mkdir(parents=True, exist_ok=True)
# Keep the four small, public-source regression originals reproducible even
# when the dealer CDN changes its encoding. These are the catalogued bytes,
# not replacement renditions. Both preparation and Android verify their hashes.
for row in rows:
    pinned = manifest.parent / "fixtures" / (row["photo_id"] + ".jpg")
    if pinned.exists():
        if hashlib.sha256(pinned.read_bytes()).hexdigest() != row["sha256"]:
            raise SystemExit("Corrupt pinned Android fixture: " + row["photo_id"])
        target = dest / row["local_path"]
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(pinned, target)
if not fetch(rows, dest, dest / "fetch.json"):
    raise SystemExit("Missing verified Android regression fixture")
(dest / "manifest.json").write_text(manifest.read_text())
