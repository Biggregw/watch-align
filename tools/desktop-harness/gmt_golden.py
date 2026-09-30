#!/usr/bin/env python3
"""Deterministic GMT regression fixture, keyed by image sha256.

The 337-photo GMT regression images are third-party photos and are NOT committed. This fixture
commits only their sha256, class, watch id and the production Batch driver's outputs, so any
shared-code change can be compared exactly wherever the images are available.

    # check: finds the images by sha256 under the given roots, re-runs Batch, compares every column
    python3 tools/desktop-harness/gmt_golden.py check --images datasets/126710BLNR [--images more] [--shards 2]

    # build (maintainers only, from a finished Batch run of the current production code)
    python3 tools/desktop-harness/gmt_golden.py build --list list.csv --run RUN_DIR

Exit status: 0 identical; 1 differences; 2 no images found (reported, not treated as a pass).
"""
from __future__ import annotations

import argparse
import csv
import glob
import hashlib
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
FIXTURE = HERE / "gmt_golden"
REPO = HERE.parent.parent
IGNORED = {"path", "overlay"}     # file locations, not measurements


def sha256(p: Path) -> str:
    h = hashlib.sha256()
    with p.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def read_run(run: Path, round_: bool) -> list[dict]:
    """Batch shard outputs; only some shards carry the header row, as in the 337-photo regression."""
    rows, hdr, raw = [], None, []
    for f in sorted(glob.glob(str(run / "*.csv"))):
        if f.endswith("_round.csv") != round_ or Path(f).name == "list.csv":
            continue
        with open(f, newline="", encoding="utf-8") as fh:
            lines = list(csv.reader(fh))
        if lines and lines[0] and lines[0][0] == "path":
            hdr, lines = lines[0], lines[1:]
        raw += lines
    return [dict(zip(hdr, line)) for line in raw if line] if hdr else rows


def build(list_csv: Path, run: Path) -> None:
    with list_csv.open(newline="", encoding="utf-8") as f:
        items = list(csv.DictReader(f))
    sha_of = {r["local_path"]: sha256(Path(r["local_path"])) for r in items}
    FIXTURE.mkdir(parents=True, exist_ok=True)
    with (FIXTURE / "manifest.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["sha256", "class_label", "physical_watch_id", "name"])
        for r in items:
            w.writerow([sha_of[r["local_path"]], r["class_label"], r["physical_watch_id"], Path(r["local_path"]).name])
    for round_, name in ((False, "expected_batch.csv"), (True, "expected_round.csv")):
        rows = read_run(run, round_)
        for r in rows:
            r["sha256"] = sha_of[r["path"]]
        cols = ["sha256"] + [c for c in rows[0] if c not in IGNORED and c != "sha256"]
        rows.sort(key=lambda r: (r["sha256"], r.get("hour", "")))
        with (FIXTURE / name).open("w", newline="", encoding="utf-8") as f:
            w = csv.DictWriter(f, fieldnames=cols, extrasaction="ignore")
            w.writeheader()
            w.writerows(rows)
    print(f"fixture written: {len(items)} images")


def run_batch(paths: list[tuple[str, dict]], work: Path, shards: int) -> Path:
    sys.path.insert(0, str(REPO / "tools" / "dataset_harvester"))
    from harvester.harness import Harness
    h = Harness(shards=shards)
    h.measure([{"path": p, "class_label": m["class_label"], "physical_watch_id": m["physical_watch_id"], "factory": ""}
               for p, m in paths], work)
    return work


def check(roots: list[Path], shards: int, work: Path | None) -> int:
    with (FIXTURE / "manifest.csv").open(newline="", encoding="utf-8") as f:
        manifest = {r["sha256"]: r for r in csv.DictReader(f)}
    found: dict[str, str] = {}
    for root in roots:
        for p in root.rglob("*"):
            if p.suffix.lower() in (".jpg", ".jpeg", ".png", ".webp") and p.is_file():
                h = sha256(p)
                if h in manifest and h not in found:
                    found[h] = str(p.resolve())
    print(f"images located: {len(found)}/{len(manifest)}")
    if not found:
        print("no fixture images available here; GMT golden comparison NOT run")
        return 2
    work = work or Path(tempfile.mkdtemp(prefix="gmt_golden_"))
    run_batch([(p, manifest[h]) for h, p in sorted(found.items())], work, shards)
    path_sha = {p: h for h, p in found.items()}
    diffs = []
    for round_, name in ((False, "expected_batch.csv"), (True, "expected_round.csv")):
        with (FIXTURE / name).open(newline="", encoding="utf-8") as f:
            exp = {(r["sha256"], r.get("hour", "")): r for r in csv.DictReader(f) if r["sha256"] in found}
        got = {}
        for r in read_run(work, round_):
            h = path_sha.get(str(Path(r["path"]).resolve()))
            if h:
                got[(h, r.get("hour", ""))] = r
        for k, e in exp.items():
            g = got.get(k)
            if g is None:
                diffs.append({"table": name, "key": k, "column": "*", "expected": "row", "got": "missing"})
                continue
            for c, v in e.items():
                if c == "sha256":
                    continue
                if g.get(c) != v:
                    diffs.append({"table": name, "key": k, "column": c, "expected": v, "got": g.get(c)})
    print(json.dumps({"compared_images": len(found), "differences": len(diffs), "first": diffs[:20]}, indent=1, default=str))
    return 1 if diffs else 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    b = sub.add_parser("build")
    b.add_argument("--list", type=Path, required=True)
    b.add_argument("--run", type=Path, required=True)
    c = sub.add_parser("check")
    c.add_argument("--images", type=Path, action="append", required=True)
    c.add_argument("--shards", type=int, default=max(1, (os.cpu_count() or 2) // 2))
    c.add_argument("--work", type=Path)
    a = ap.parse_args()
    if a.cmd == "build":
        build(a.list, a.run)
        return 0
    return check(a.images, a.shards, a.work)


if __name__ == "__main__":
    raise SystemExit(main())
