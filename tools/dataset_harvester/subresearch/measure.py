"""Runs the research-only SubMeasure Java driver (tools/desktop-harness/drivers/SubMeasure.java).

SubMeasure is compiled ON ITS OWN against the app sources, so javac only pulls in the classes it
references. isolation_report() lists them; the tests assert that GmtHumanQcAnalyzerV2,
GmtHumanPosePolicy, GmtDialLayout, GmtDialCrop and the summary/verdict classes are never among them.
"""
from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from harvester.config import HARNESS_DIR, REPO_ROOT  # noqa: E402
from harvester.harness import Harness, HarnessUnavailable, _tool  # noqa: E402

from .layout import layout_for  # noqa: E402

DRIVER = HARNESS_DIR / "drivers" / "SubMeasure.java"
ALL_VARIANTS = ("orig", "s94", "s88", "x+1", "x-1", "x+2", "x-2", "y+1", "y-1", "y+2", "y-2", "r+5", "r-5")
FORBIDDEN_CLASSES = ("GmtHumanQcAnalyzerV2", "GmtHumanQcAnalyzer", "GmtHumanPosePolicy", "GmtDialLayout", "GmtDialCrop",
                     "GmtHumanSummary", "GmtDirectionalClearancePolicy", "CanonicalGmtGeometryAnalyzer", "QcExtendedAnalyzer",
                     "Load", "Suit", "Batch")


def compile_driver(harness: Harness, out_root: Path | None = None) -> Path:
    harness.check()
    src_dirs = [HARNESS_DIR / "shim", REPO_ROOT / "android" / "app" / "src" / "main" / "java"]
    h = hashlib.sha1()
    for d in src_dirs + [DRIVER.parent]:
        for p in sorted(d.rglob("*.java")):
            st = p.stat()
            h.update(f"{p}:{st.st_size}:{int(st.st_mtime)}".encode())
    out = (out_root or harness.cache) / f"submeasure_classes_{h.hexdigest()[:12]}"
    if not (out / "com" / "watchalign" / "mobile" / "SubMeasure.class").exists():
        shutil.rmtree(out, ignore_errors=True)
        out.mkdir(parents=True)
        # Only the driver is named; the drivers directory is deliberately NOT on the sourcepath.
        cmd = [_tool("javac"), "-encoding", "UTF-8", "-nowarn", "-cp", str(harness.jar), "-sourcepath",
               os.pathsep.join(str(d) for d in src_dirs), "-d", str(out), str(DRIVER)]
        res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        if res.returncode != 0:
            shutil.rmtree(out, ignore_errors=True)
            raise HarnessUnavailable("javac SubMeasure failed: " + res.stdout[-1200:])
    return out


def compiled_classes(classes_dir: Path) -> set[str]:
    return {p.name.split("$")[0].removesuffix(".class") for p in (classes_dir / "com" / "watchalign" / "mobile").glob("*.class")}


def isolation_report(classes_dir: Path) -> dict:
    got = compiled_classes(classes_dir)
    return {"compiled_classes": sorted(got), "forbidden_present": sorted(got & set(FORBIDDEN_CLASSES))}


def job_line(path: str, sha: str, model: str, variants=ALL_VARIANTS) -> str:
    lay = layout_for(model)
    batons = ",".join(str(h) for h in lay["batons"]) or "-"
    dates = ",".join(str(h) for h in lay["date"]) or "-"
    return "\t".join([path, sha, model, batons, dates, ",".join(variants)])


def run(harness: Harness, classes: Path, jobs: list[str], work: Path, shards: int) -> list[dict]:
    work.mkdir(parents=True, exist_ok=True)
    lst = work / "jobs.tsv"
    lst.write_text("\n".join(jobs) + "\n", encoding="utf-8")
    n = max(1, min(shards, len(jobs)))

    def one(k: int) -> Path:
        out = work / f"sub_{k}.jsonl"
        cmd = [_tool("java"), f"-Xmx{harness.heap}", "-Dfile.encoding=UTF-8", "-cp", f"{harness.jar}{os.pathsep}{classes}",
               "com.watchalign.mobile.SubMeasure", str(lst), str(out), str(n), str(k)]
        with (work / f"sub_{k}.log").open("w") as f:
            subprocess.run(cmd, stdout=f, stderr=subprocess.STDOUT, cwd=str(REPO_ROOT))
        return out

    with ThreadPoolExecutor(n) as ex:
        outs = list(ex.map(one, range(n)))
    recs = []
    for o in outs:
        if o.exists():
            for line in o.read_text(encoding="utf-8").splitlines():
                if line.strip():
                    recs.append(json.loads(line))
    return recs


def selftest(harness: Harness, classes: Path) -> str:
    cmd = [_tool("java"), "-cp", f"{harness.jar}{os.pathsep}{classes}", "com.watchalign.mobile.SubMeasure", "--selftest"]
    res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    return res.stdout.strip().splitlines()[-1] if res.stdout.strip() else f"exit {res.returncode}"
