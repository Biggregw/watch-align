#!/usr/bin/env python3
"""Fetch exact-model first-party Rolex catalogue images for Android acceptance tests.

The PDF endpoints reject GitHub-hosted CI runners with HTTP 403. The Rolex product
pages themselves use media.rolex.com catalogue imagery, so CI downloads those
first-party image assets directly. Fixtures are ephemeral and discarded with the
runner; nothing is committed or redistributed in the repository.
"""
from pathlib import Path
import subprocess
import urllib.request

UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140 Safari/537.36"

IMAGES = {
    "126710BLNR": "https://media.rolex.com/image/upload/q_auto/f_jpg/t_v7-cover-majesty-landscape/c_limit,w_1920/v1/a677b2c664f6/catalogue/2026/upright-c/m126710blnr-0002",
    "124060": "https://media.rolex.com/image/upload/q_auto/f_jpg/t_v7-cover-majesty-landscape/c_limit,w_1920/v1/a677b2c664f6/catalogue/2026/upright-c/m124060-0001",
}


def valid_image(data: bytes) -> bool:
    if len(data) < 50_000:
        return False
    return (
        data.startswith(b"\xff\xd8\xff") or
        data.startswith(b"\x89PNG\r\n\x1a\n") or
        data.startswith(b"RIFF")
    )


def urllib_get(url: str) -> bytes:
    req = urllib.request.Request(url, headers={
        "User-Agent": UA,
        "Accept": "image/avif,image/webp,image/apng,image/jpeg,image/*,*/*;q=0.8",
        "Accept-Language": "en-GB,en;q=0.9",
        "Referer": "https://www.rolex.com/",
        "Cache-Control": "no-cache",
    })
    with urllib.request.urlopen(req, timeout=35) as r:
        data = r.read()
    if not valid_image(data):
        raise RuntimeError(f"not a usable image ({len(data)} bytes)")
    return data


def curl_get(url: str, out: Path) -> bytes:
    proc = subprocess.run([
        "curl", "--fail", "--location", "--silent", "--show-error",
        "--retry", "2", "--retry-all-errors", "--max-time", "45",
        "-A", UA,
        "-H", "Accept: image/avif,image/webp,image/apng,image/jpeg,image/*,*/*;q=0.8",
        "-H", "Accept-Language: en-GB,en;q=0.9",
        "-H", "Referer: https://www.rolex.com/",
        "-o", str(out), url,
    ], text=True, capture_output=True)
    if proc.returncode != 0:
        raise RuntimeError(proc.stderr.strip() or f"curl exit {proc.returncode}")
    data = out.read_bytes()
    if not valid_image(data):
        raise RuntimeError(f"curl returned unusable image ({len(data)} bytes)")
    return data


def fetch_image(url: str, token: str, tmp: Path) -> bytes:
    errors = []
    try:
        data = urllib_get(url)
        print(f"{token}: fetched first-party Rolex catalogue image via urllib")
        return data
    except Exception as exc:
        errors.append(f"urllib: {exc}")
    out = tmp / f"{token}.jpg"
    try:
        data = curl_get(url, out)
        print(f"{token}: fetched first-party Rolex catalogue image via curl")
        return data
    except Exception as exc:
        errors.append(f"curl: {exc}")
    raise SystemExit("Could not fetch first-party Rolex catalogue image for " + token + "\n" + "\n".join(errors))


def main():
    root = Path("app/src/androidTest/assets/genuine")
    tmp = Path("build/genuine-fixture-tmp")
    tmp.mkdir(parents=True, exist_ok=True)
    total = 0
    for token, url in IMAGES.items():
        dest = root / token
        dest.mkdir(parents=True, exist_ok=True)
        data = fetch_image(url, token, tmp)
        name = "official_00.jpg"
        (dest / name).write_bytes(data)
        (dest / "sources.tsv").write_text(f"{name}\t{url}\n", encoding="utf-8")
        print(f"{token}: prepared exact-model manufacturer-original fixture")
        total += 1
    print(f"Prepared {total} manufacturer-original fixtures for genuine-image acceptance testing")

if __name__ == "__main__":
    main()
