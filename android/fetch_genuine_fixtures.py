#!/usr/bin/env python3
"""Fetch ephemeral exact-model Rolex Newsroom contact-sheet images for Android acceptance tests.

Rolex's assets.rolex.com brochure endpoints reject GitHub-hosted CI runners with
HTTP 403. Rolex Newsroom exposes official manufacturer contact-sheet PDFs from a
separate first-party host, so CI uses those instead. Fixtures are rendered at CI
time into androidTest assets and discarded with the runner; nothing is committed
or redistributed in the repository.
"""
from pathlib import Path
import subprocess
import tempfile
import urllib.request

UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140 Safari/537.36"

# First-party Rolex Newsroom contact sheets containing the exact references.
# 126710BLNR appears on page 1 of the GMT-Master II sheet.
# 124060 appears on page 1 of the Submariner sheet.
BROCHURES = {
    "126710BLNR": [
        "https://newsroom.rolex.com/services/v1/contactsheet/generatepdf/1837db50-6b25-463c-aaa8-37a36aa889ce/en/gmt-master-ii.pdf",
        "https://newsroom.rolex.com/services/v1/contactsheet/generatepdf/1837db50-6b25-463c-aaa8-37a36aa889ce/fr/gmt-master-ii.pdf",
    ],
    "124060": [
        "https://newsroom.rolex.com/services/v1/contactsheet/generatepdf/750f22e3-44c4-41d1-ab54-365b69fbd6d8/en/submariner.pdf",
    ],
}


def urllib_get(url: str) -> bytes:
    req = urllib.request.Request(url, headers={
        "User-Agent": UA,
        "Accept": "application/pdf,application/octet-stream;q=0.9,*/*;q=0.8",
        "Accept-Language": "en-GB,en;q=0.9",
        "Referer": "https://newsroom.rolex.com/",
        "Cache-Control": "no-cache",
    })
    with urllib.request.urlopen(req, timeout=35) as r:
        data = r.read()
        ctype = (r.headers.get("Content-Type") or "").lower()
    if len(data) < 50_000 or not data.startswith(b"%PDF"):
        raise RuntimeError(f"not a usable PDF ({len(data)} bytes, {ctype})")
    return data


def curl_get(url: str, out: Path) -> bytes:
    proc = subprocess.run([
        "curl", "--fail", "--location", "--silent", "--show-error",
        "--retry", "2", "--retry-all-errors", "--max-time", "45",
        "-A", UA,
        "-H", "Accept: application/pdf,application/octet-stream;q=0.9,*/*;q=0.8",
        "-H", "Accept-Language: en-GB,en;q=0.9",
        "-H", "Referer: https://newsroom.rolex.com/",
        "-o", str(out), url,
    ], text=True, capture_output=True)
    if proc.returncode != 0:
        raise RuntimeError(proc.stderr.strip() or f"curl exit {proc.returncode}")
    data = out.read_bytes()
    if len(data) < 50_000 or not data.startswith(b"%PDF"):
        raise RuntimeError(f"curl returned non-PDF/short body ({len(data)} bytes)")
    return data


def fetch_pdf(urls, token: str, tmp: Path):
    errors = []
    for url in urls:
        try:
            data = urllib_get(url)
            print(f"{token}: fetched official Rolex Newsroom contact sheet via urllib: {url}")
            return data, url
        except Exception as exc:
            errors.append(f"urllib {url}: {exc}")
        curl_tmp = tmp / f"{token}-curl.pdf"
        try:
            data = curl_get(url, curl_tmp)
            print(f"{token}: fetched official Rolex Newsroom contact sheet via curl: {url}")
            return data, url
        except Exception as exc:
            errors.append(f"curl {url}: {exc}")
    raise SystemExit("Could not fetch Rolex Newsroom contact sheet for " + token + "\n" + "\n".join(errors))


def main():
    root = Path("app/src/androidTest/assets/genuine")
    total = 0
    with tempfile.TemporaryDirectory() as td:
        tmp = Path(td)
        for token, urls in BROCHURES.items():
            dest = root / token
            dest.mkdir(parents=True, exist_ok=True)
            pdf = tmp / f"{token}.pdf"
            data, source_url = fetch_pdf(urls, token, tmp)
            pdf.write_bytes(data)
            prefix = tmp / token
            subprocess.run([
                "pdftoppm", "-f", "1", "-singlefile", "-jpeg", "-r", "220",
                str(pdf), str(prefix)
            ], check=True)
            rendered = prefix.with_suffix(".jpg")
            if not rendered.is_file() or rendered.stat().st_size < 50_000:
                raise SystemExit(f"Could not render usable official Rolex Newsroom sheet for {token}")
            name = "official_00.jpg"
            (dest / name).write_bytes(rendered.read_bytes())
            (dest / "sources.tsv").write_text(f"{name}\t{source_url}#page=1\n", encoding="utf-8")
            print(f"{token}: rendered exact-reference first-party Rolex Newsroom fixture")
            total += 1
    print(f"Prepared {total} manufacturer-original fixtures for genuine-image acceptance testing")

if __name__ == "__main__":
    main()
