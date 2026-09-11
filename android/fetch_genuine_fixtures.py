#!/usr/bin/env python3
"""Fetch ephemeral exact-model Rolex brochure images for Android acceptance tests.

The product webpages block generic CI clients, but Rolex's own brochure PDFs are
public manufacturer sources. CI renders the front-cover watch image into test
assets; nothing is committed or redistributed in the repository.
"""
from pathlib import Path
import subprocess
import tempfile
import urllib.request

UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/140 Safari/537.36"
BROCHURES = {
    "126710BLNR": "https://assets.rolex.com/api/brochure/en/gmt-master-ii/m126710blnr-0002.pdf",
    "124060": "https://assets.rolex.com/api/brochure/en/submariner/m124060-0001.pdf",
}


def get(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "en-GB,en;q=0.9"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.read()


def main():
    root = Path("app/src/androidTest/assets/genuine")
    total = 0
    with tempfile.TemporaryDirectory() as td:
        tmp = Path(td)
        for token, url in BROCHURES.items():
            dest = root / token
            dest.mkdir(parents=True, exist_ok=True)
            pdf = tmp / f"{token}.pdf"
            pdf.write_bytes(get(url))
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
            (dest / "sources.tsv").write_text(f"{name}\t{url}#page=1\n", encoding="utf-8")
            print(f"{token}: rendered exact-model Rolex brochure cover")
            total += 1
    print(f"Prepared {total} manufacturer-original fixtures for genuine-image acceptance testing")

if __name__ == "__main__":
    main()
