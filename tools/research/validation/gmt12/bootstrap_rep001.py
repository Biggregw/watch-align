"""Proof-of-concept downloader for the GMT12 external validation dataset.

Downloads the source post listed in gmt12_sources.txt using gallery-dl.
This is intentionally a bootstrap test, not part of the detector itself.
"""
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parent
SOURCES = ROOT / "gmt12_sources.txt"
DOWNLOADS = ROOT / "downloads" / "REP001"


def main() -> int:
    if not SOURCES.exists():
        print(f"missing source list: {SOURCES}", file=sys.stderr)
        return 2

    exe = shutil.which("gallery-dl")
    if exe is None:
        print("gallery-dl is not installed. Install with: python -m pip install gallery-dl", file=sys.stderr)
        return 3

    DOWNLOADS.mkdir(parents=True, exist_ok=True)
    cmd = [exe, "-D", str(DOWNLOADS), "-i", str(SOURCES)]
    print("Running:", " ".join(cmd))
    completed = subprocess.run(cmd, check=False)
    if completed.returncode:
        return completed.returncode

    images = []
    for ext in ("*.jpg", "*.jpeg", "*.png", "*.webp"):
        images.extend(DOWNLOADS.rglob(ext))
    images = sorted(set(images))
    print(f"Downloaded {len(images)} image file(s)")
    for p in images:
        print(p.relative_to(ROOT))
    return 0 if images else 4


if __name__ == "__main__":
    raise SystemExit(main())
