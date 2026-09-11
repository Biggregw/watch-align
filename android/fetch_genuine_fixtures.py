#!/usr/bin/env python3
"""Fetch ephemeral exact-model Rolex manufacturer images for Android acceptance tests.

The files are created only in the CI workspace under androidTest assets; they are
not committed or redistributed in the repository.
"""
from pathlib import Path
import html as html_lib
import re
import urllib.request

UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/140 Safari/537.36"
MODELS = {
    "126710BLNR": "https://www.rolex.com/watches/gmt-master-ii/m126710blnr-0002",
    "124060": "https://www.rolex.com/watches/submariner/m124060-0001",
}
IMG_RE = re.compile(r"https://[^\"'<>\s]+?(?:\.jpg|\.jpeg|\.png|\.webp)(?:\?[^\"'<>\s]*)?", re.I)
REF_RE = re.compile(r"m?(\d{6}[a-z]{0,6})(?:-\d{4})?", re.I)


def get(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "en-GB,en;q=0.9"})
    with urllib.request.urlopen(req, timeout=25) as r:
        return r.read()


def exact_context(context: str, token: str) -> bool:
    refs = [m.group(1).lower() for m in REF_RE.finditer(context.lower())]
    return token.lower() in refs and all(r == token.lower() for r in refs)


def candidates(page: str, token: str):
    raw = html_lib.unescape(get(page).decode("utf-8", "replace")).replace("\\u002F", "/").replace("\\/", "/")
    out = []
    for m in IMG_RE.finditer(raw):
        url = m.group(0)
        context = raw[max(0, m.start()-320):min(len(raw), m.end()+320)]
        low = url.lower()
        if token.lower() in low or ("m" + token.lower()) in low or exact_context(context, token):
            if url not in out:
                out.append(url)
    return out


def main():
    root = Path("app/src/androidTest/assets/genuine")
    total = 0
    for token, page in MODELS.items():
        dest = root / token
        dest.mkdir(parents=True, exist_ok=True)
        urls = candidates(page, token)
        if not urls:
            raise SystemExit(f"No exact-model official image URLs discovered for {token}")
        saved = 0
        manifest = []
        for idx, url in enumerate(urls[:16]):
            try:
                data = get(url)
                if len(data) < 50_000:
                    continue
                name = f"official_{idx:02d}.img"
                (dest / name).write_bytes(data)
                manifest.append(f"{name}\t{url}")
                saved += 1
                if saved >= 6:
                    break
            except Exception as exc:
                print(f"skip {url}: {exc}")
        if saved < 2:
            raise SystemExit(f"Only fetched {saved} usable official assets for {token}")
        (dest / "sources.tsv").write_text("\n".join(manifest), encoding="utf-8")
        print(f"{token}: fetched {saved} official exact-model assets")
        total += saved
    print(f"Fetched {total} manufacturer assets for genuine-image acceptance testing")

if __name__ == "__main__":
    main()
