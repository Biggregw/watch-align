"""Translation-stability export: the production detector on tiny translations of each photo.

python3 tools/research/corpus_qc/translate.py <data/harvest> <split_v2.csv> <cycle1 corpus_qc.jsonl> <out dir>
        --store DIR [--shards 2]

Photos: every development/validation photo (split_v2) whose dial the production analysis could read
in cycle 1 (dial located and readable); holdout-v1 watches are not in split_v2.

Variants (all saved identically as JPEG q97 at the original size, so re-encoding is the same for all):
  t0            the photo itself, re-encoded (the control every translation is compared with)
  x+1 x-1 x+2 x-2 y+1 y-1 y+2 y-2   content shifted by that percentage of the image width/height
Translation keeps the canvas size: the content moves, the strip it uncovers is filled with the
median colour of the image border (flat, so it cannot create marker-like edges), and the content
pushed past the far edge is cropped. A point p of the photo is at p + (dx, dy) in the variant.
"""
from __future__ import annotations

import argparse
import csv
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageOps

sys.path.insert(0, str(Path(__file__).resolve().parent))
import metrics as M  # noqa: E402
from corpus import load  # noqa: E402
from export import compile_driver, run  # noqa: E402

SHIFTS = {"t0": (0, 0), "x+1": (1, 0), "x-1": (-1, 0), "x+2": (2, 0), "x-2": (-2, 0),
          "y+1": (0, 1), "y-1": (0, -1), "y+2": (0, 2), "y-2": (0, -2)}


def border_colour(a: np.ndarray) -> tuple:
    edge = np.concatenate([a[0], a[-1], a[:, 0], a[:, -1]])
    return tuple(int(v) for v in np.median(edge, axis=0))


def translate(src: str, key: str, dst: Path) -> tuple[int, int]:
    im = ImageOps.exif_transpose(Image.open(src)).convert("RGB")
    px, py = SHIFTS[key]
    dx, dy = round(im.width * px / 100), round(im.height * py / 100)
    canvas = Image.new("RGB", im.size, border_colour(np.asarray(im)))
    canvas.paste(im, (dx, dy))
    canvas.save(dst, "JPEG", quality=97)
    return dx, dy


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("harvest")
    ap.add_argument("split")
    ap.add_argument("cycle1")
    ap.add_argument("out")
    ap.add_argument("--store", action="append", default=[])
    ap.add_argument("--shards", type=int, default=2)
    a = ap.parse_args()
    split = {r["physical_watch_id"]: r["partition"] for r in csv.DictReader(open(a.split))}
    readable = {r["sha256"] for r in M.load(a.cycle1) if r["variant"] == "original" and M.analysed(r)}
    rows = [r for r in load(Path(a.harvest), [Path(s) for s in a.store])
            if r.sample_role == "population" and r.watch in split and r.sha256 in readable]
    out = Path(a.out)
    (out / "variants").mkdir(parents=True, exist_ok=True)
    classes = compile_driver(out / "classes")
    jobs = {}
    seen = set()
    for r in rows:
        if r.sha256 in seen:
            continue
        seen.add(r.sha256)
        for k in SHIFTS:
            dst = out / "variants" / f"{r.sha256[:16]}_{k}.jpg"
            dx, dy = translate(r.path, k, dst)
            jobs[str(dst)] = {"sha256": r.sha256, "watch": r.watch, "cls": r.cls, "model": r.model, "factory": r.factory,
                              "partition": split[r.watch], "usable": r.usable, "variant": k, "dx": dx, "dy": dy,
                              "sample_role": r.sample_role, "harvest_reasons": r.reasons}
    recs = run(list(jobs), out, classes, a.shards)
    with (out / "translations.jsonl").open("w") as f:
        for rec in recs:
            m = jobs.get(rec["path"])
            if m:
                f.write(json.dumps(dict(rec, **m)) + "\n")
    print(f"{len(seen)} photos x {len(SHIFTS)} variants = {len(recs)} analyses -> {out / 'translations.jsonl'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
