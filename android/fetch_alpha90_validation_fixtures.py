#!/usr/bin/env python3
"""Fetch public Reddit-hosted images for the frozen Alpha90 validation queue.

No third-party image bytes are committed. The script reads alpha90_validation_cases.tsv,
resolves each Reddit post through the public JSON endpoint, downloads every directly
hosted post/gallery image it can find, and writes a runtime fixture index for Android
instrumentation. A failed post is recorded rather than silently substituted.
"""
from __future__ import annotations

import csv
import html
import json
import mimetypes
import pathlib
import sys
import time
import urllib.parse
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent
CASES = ROOT / "alpha90_validation_cases.tsv"
OUT = ROOT / "app" / "src" / "androidTest" / "assets" / "alpha90_validation"
UA = "WatchAlign-Alpha90-Validation/1.0 (+github.com/Biggregw/watch-align)"
KEEP = {"VISION_REVIEW_PROMPT.md"}


def request(url: str, accept: str = "*/*"):
    req = urllib.request.Request(url, headers={
        "User-Agent": UA,
        "Accept": accept,
        "Cache-Control": "no-cache",
    })
    return urllib.request.urlopen(req, timeout=30)


def fetch_json(thread_id: str):
    urls = [
        f"https://www.reddit.com/comments/{thread_id}.json?raw_json=1",
        f"https://old.reddit.com/comments/{thread_id}.json?raw_json=1",
    ]
    last = None
    for u in urls:
        try:
            with request(u, "application/json") as r:
                return json.loads(r.read().decode("utf-8")), u
        except Exception as e:
            last = e
            time.sleep(1.0)
    raise RuntimeError(f"Reddit JSON unavailable for {thread_id}: {last}")


def post_data(doc):
    return doc[0]["data"]["children"][0]["data"]


def image_urls(post: dict) -> list[str]:
    found: list[str] = []

    def add(u):
        if not u:
            return
        u = html.unescape(str(u)).strip()
        if not u.startswith("http"):
            return
        host = urllib.parse.urlparse(u).netloc.lower()
        if host.endswith("redd.it") or host.endswith("reddit.com") or host.endswith("redditmedia.com"):
            if u not in found:
                found.append(u)

    media = post.get("media_metadata") or {}
    gallery = post.get("gallery_data") or {}
    for item in gallery.get("items") or []:
        m = media.get(str(item.get("media_id"))) or {}
        src = m.get("s") or {}
        add(src.get("u") or src.get("gif"))

    add(post.get("url_overridden_by_dest"))
    preview = post.get("preview") or {}
    for im in preview.get("images") or []:
        src = im.get("source") or {}
        add(src.get("url"))

    return found


def extension(url: str, content_type: str) -> str:
    ct = (content_type or "").split(";", 1)[0].strip().lower()
    if ct == "image/jpeg": return ".jpg"
    if ct == "image/png": return ".png"
    if ct == "image/webp": return ".webp"
    ext = pathlib.Path(urllib.parse.urlparse(url).path).suffix.lower()
    if ext in {".jpg", ".jpeg", ".png", ".webp"}: return ext
    return mimetypes.guess_extension(ct) or ".img"


def clean_generated_files():
    OUT.mkdir(parents=True, exist_ok=True)
    for p in OUT.iterdir():
        if p.name in KEEP:
            continue
        if p.is_file():
            p.unlink()


def main() -> int:
    clean_generated_files()

    with CASES.open(newline="", encoding="utf-8") as f:
        cases = list(csv.DictReader(f, delimiter="\t"))

    runtime = []
    errors = []
    for c in cases:
        cid = c["case_id"]
        tid = c["thread_id"]
        try:
            doc, _json_url = fetch_json(tid)
            post = post_data(doc)
            urls = image_urls(post)
            if not urls:
                raise RuntimeError("no directly hosted post/gallery images found")
            downloaded = 0
            for i, u in enumerate(urls):
                try:
                    with request(u, "image/*") as r:
                        data = r.read()
                        ctype = r.headers.get("Content-Type", "")
                    if len(data) < 10_000:
                        raise RuntimeError(f"download too small ({len(data)} bytes)")
                    ext = extension(u, ctype)
                    if ext not in {".jpg", ".jpeg", ".png", ".webp"}:
                        raise RuntimeError(f"unsupported content type {ctype!r}")
                    name = f"{cid}_{i:02d}{ext}"
                    (OUT / name).write_bytes(data)
                    runtime.append({**c, "asset": name, "source_url": u, "status": "downloaded"})
                    downloaded += 1
                except Exception as e:
                    errors.append(f"{cid} image {i}: {e}")
            if downloaded == 0:
                raise RuntimeError("all discovered images failed to download")
            print(f"{cid}: downloaded {downloaded}/{len(urls)} images")
        except Exception as e:
            print(f"WARNING {cid}: {e}", file=sys.stderr)
            runtime.append({**c, "asset": "", "source_url": f"https://www.reddit.com/comments/{tid}/", "status": f"fetch_error: {e}"})
            errors.append(f"{cid}: {e}")

    fields = ["case_id", "class", "thread_id", "target", "notes", "asset", "source_url", "status"]
    with (OUT / "runtime_manifest.tsv").open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fields, delimiter="\t", extrasaction="ignore")
        w.writeheader(); w.writerows(runtime)
    (OUT / "fetch_errors.txt").write_text("\n".join(errors) + ("\n" if errors else ""), encoding="utf-8")

    n = sum(1 for r in runtime if r["asset"])
    print(f"Prepared {n} Alpha90 validation images in {OUT}")
    return 0 if n else 2


if __name__ == "__main__":
    raise SystemExit(main())
