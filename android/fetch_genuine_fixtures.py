#!/usr/bin/env python3
"""Fetch ephemeral exact-model Rolex brochure images for Android acceptance tests.

The fixtures are downloaded at CI time from Rolex-owned assets only, rendered into
androidTest assets, then discarded with the runner. Nothing is committed or
redistributed in the repository.
"""
from pathlib import Path
import subprocess
import tempfile
import urllib.error
import urllib.request

UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140 Safari/537.36"
BROCHURES = {
    "126710BLNR": [
        "https://assets.rolex.com/watches/gmt-master-ii/m126710blnr-0002.pdf",
        "https://assets.rolex.com/api/brochure/en/gmt-master-ii/m126710blnr-0002.pdf",
    ],
    "124060": [
        "https://assets.rolex.com/watches/submariner/m124060-0001.pdf",
        "https://assets.rolex.com/api/brochure/en/submariner/m124060-0001.pdf",
    ],
}


def urllib_get(url: str) -> bytes:
    req = urllib.request.Request(url, headers={
        "User-Agent": UA,
        "Accept": "application/pdf,application/octet-stream;q=0.9,*/*;q=0.8",
        "Accept-Language": "en-GB,en;q=0.9",
        "Referer": "https://www.rolex.com/",
        "Cache-Control": "no-cache",
    })
    with urllib.request.urlopen(req, timeout=35) as r:
        data = r.read()
        ctype = (r.headers.get("Content-Type") or "").lower()
    if len(data) < 100_000 or not data.startswith(b"%PDF"):
        raise RuntimeError(f"not a usable PDF ({len(data)} bytes, {ctype})")
    return data


def curl_get(url: str, out: Path) -> bytes:
    proc = subprocess.run([
        "curl", "--fail", "--location", "--silent", "--show-error",
        "--retry", "2", "--retry-all-errors", "--max-time", "45",
        "-A", UA,
        "-H", "Accept: application/pdf,application/octet-stream;q=0.9,*/*;q=0.8",
        "-H", "Accept-Language: en-GB,en;q=0.9",
        "-H", "Referer: https://www.rolex.com/",
        "-o", str(out), url,
    ], text=True, capture_output=True)
    if proc.returncode != 0:
        raise RuntimeError(proc.stderr.strip() or f"curl exit {proc.returncode}")
    data = out.read_bytes()
    if len(data) < 100_000 or not data.startswith(b"%PDF"):
        raise RuntimeError(f"curl returned non-PDF/short body ({len(data)} bytes)")
    return data


def fetch_pdf(urls, token: str, tmp: Path):
    errors = []
    for url in urls:
        try:
            data = urllib_get(url)
            print(f"{token}: fetched official brochure via urllib: {url}")
            return data, url
        except Exception as exc:
            errors.append(f"urllib {url}: {exc}")
        curl_tmp = tmp / f"{token}-curl.pdf"
        try:
            data = curl_get(url, curl_tmp)
            print(f"{token}: fetched official brochure via curl: {url}")
            return data, url
        except Exception as exc:
            errors.append(f"curl {url}: {exc}")
    raise SystemExit("Could not fetch Rolex official brochure for " + token + "\n" + "\n".join(errors))


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
                "pdftoppm", "-f", "1", "-singlefile", "-jpeg", "-r", "180",
                str(pdf), str(prefix)
            ], check=True)
            rendered = prefix.with_suffix(".jpg")
            if not rendered.is_file() or rendered.stat().st_size < 50_000:
                raise SystemExit(f"Could not render usable official brochure cover for {token}")
            name = "official_00.jpg"
            (dest / name).write_bytes(rendered.read_bytes())
            (dest / "sources.tsv").write_text(f"{name}\t{source_url}#page=1\n", encoding="utf-8")
            print(f"{token}: rendered exact-model Rolex brochure cover")
            total += 1
    print(f"Prepared {total} manufacturer-original fixtures for genuine-image acceptance testing")

if __name__ == "__main__":
    main()
