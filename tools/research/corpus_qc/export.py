"""Run the production analysis (CorpusQc driver) over corpus images of chosen partitions, plus small
perturbations of the usable ones.

python3 tools/research/corpus_qc/export.py <harvest dir> <split.csv> <out dir> --parts development,validation
        [--store DIR ...] [--no-perturb] [--shards 2]

Perturbations (usable images only), each a new file analysed exactly like the original:
  scale90  - resampled to 90% (Lanczos) and saved as JPEG q95
  jpeg75   - re-encoded as JPEG quality 75
  rot1     - rotated 1.0 degree about the image centre (bicubic, same size, edge colour fill)
  shift2   - 2% cropped from the left and top edges (the dial moves in the frame)
These are the sizes of nuisance change a user can produce by re-taking or re-sharing the same photo;
a measurement that moves more than its QC tolerance under them is not trustworthy.
"""
from __future__ import annotations

import argparse
import csv
import json
import os
import shutil
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from PIL import Image, ImageOps

sys.path.insert(0, str(Path(__file__).resolve().parent))
from corpus import REPO_ROOT, load  # noqa: E402

HARNESS = REPO_ROOT / "tools" / "desktop-harness"
JAR = HARNESS / ".cache" / "opencv-4.9.0-0.jar"
PERTURB = ("scale90", "jpeg75", "rot1", "shift2")


def compile_driver(out: Path) -> Path:
    out.mkdir(parents=True, exist_ok=True)
    javac = shutil.which("javac") or str(Path(os.environ.get("JAVA_HOME", "")) / "bin" / "javac")
    cmd = [javac, "-encoding", "UTF-8", "-nowarn", "-cp", str(JAR), "-sourcepath",
           os.pathsep.join(str(p) for p in (HARNESS / "shim", REPO_ROOT / "android/app/src/main/java", HARNESS / "drivers")),
           "-d", str(out)] + [str(HARNESS / "drivers" / f) for f in ("CorpusQc.java", "Load.java", "Suit.java")]
    subprocess.run(cmd, check=True)
    return out


def perturb(src: str, kind: str, dst: Path) -> None:
    im = ImageOps.exif_transpose(Image.open(src)).convert("RGB")
    if kind == "scale90":
        im = im.resize((round(im.width * 0.9), round(im.height * 0.9)), Image.Resampling.LANCZOS)
        im.save(dst, "JPEG", quality=95)
    elif kind == "jpeg75":
        im.save(dst, "JPEG", quality=75)
    elif kind == "rot1":
        edge = im.getpixel((0, 0))
        im.rotate(1.0, resample=Image.Resampling.BICUBIC, fillcolor=edge).save(dst, "JPEG", quality=95)
    elif kind == "shift2":
        dx, dy = round(im.width * 0.02), round(im.height * 0.02)
        im.crop((dx, dy, im.width, im.height)).save(dst, "JPEG", quality=95)


def run(paths: list[str], out: Path, classes: Path, shards: int) -> list[dict]:
    java = shutil.which("java") or str(Path(os.environ.get("JAVA_HOME", "")) / "bin" / "java")
    chunks = [paths[k::shards] for k in range(shards)]

    def one(k: int) -> Path:
        lst, o = out / f"list_{k}.txt", out / f"out_{k}.jsonl"
        lst.write_text("\n".join(chunks[k]) + "\n")
        with (out / f"log_{k}.txt").open("w") as log:
            subprocess.run([java, "-Xmx1500m", "-Dfile.encoding=UTF-8", "-cp", f"{JAR}{os.pathsep}{classes}",
                            "com.watchalign.mobile.CorpusQc", str(lst), str(o)], stdout=log, stderr=subprocess.STDOUT, cwd=REPO_ROOT)
        return o

    with ThreadPoolExecutor(shards) as ex:
        outs = list(ex.map(one, range(shards)))
    recs = []
    for o in outs:
        if o.exists():
            recs += [json.loads(l) for l in o.read_text().splitlines() if l.strip()]
    return recs


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("harvest")
    ap.add_argument("split")
    ap.add_argument("out")
    ap.add_argument("--parts", default="development,validation")
    ap.add_argument("--store", action="append", default=[])
    ap.add_argument("--no-perturb", action="store_true")
    ap.add_argument("--shards", type=int, default=2)
    a = ap.parse_args()
    parts = set(a.parts.split(","))
    split = {r["physical_watch_id"]: r["partition"] for r in csv.DictReader(open(a.split))}
    rows = load(Path(a.harvest), [Path(s) for s in a.store])
    pop = [r for r in rows if r.sample_role == "population" and split.get(r.watch) in parts]
    ref = [r for r in rows if r.sample_role == "reference_only"] if "development" in parts else []
    out = Path(a.out)
    out.mkdir(parents=True, exist_ok=True)
    classes = compile_driver(out / "classes")
    jobs = []   # (path, meta)
    pdir = out / "perturbed"
    pdir.mkdir(exist_ok=True)
    for r in pop + ref:
        meta = {"sha256": r.sha256, "watch": r.watch, "cls": r.cls, "model": r.model, "factory": r.factory,
                "partition": split.get(r.watch, "reference"), "usable": r.usable, "harvest_reasons": r.reasons,
                "sample_role": r.sample_role, "variant": "original"}
        jobs.append((r.path, meta))
        if r.usable and not a.no_perturb and r.sample_role == "population":
            for k in PERTURB:
                dst = pdir / f"{r.sha256[:16]}_{k}.jpg"
                if not dst.exists():
                    perturb(r.path, k, dst)
                jobs.append((str(dst), dict(meta, variant=k)))
    by_path = {p: m for p, m in jobs}
    recs = run([p for p, _ in jobs], out, classes, a.shards)
    with (out / "corpus_qc.jsonl").open("w") as f:
        for rec in recs:
            m = by_path.get(rec["path"])
            if m is None:
                continue
            f.write(json.dumps(dict(rec, **m)) + "\n")
    print(f"{len(recs)} analyses ({len(pop)} population images, {len(ref)} reference) -> {out / 'corpus_qc.jsonl'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
