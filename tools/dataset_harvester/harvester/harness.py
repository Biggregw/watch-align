"""Bridge to the app's own analysis, run headlessly through tools/desktop-harness.

The desktop harness compiles the Android app's Java sources against desktop OpenCV 4.9 with a small
android.* shim, so the photo check and the raw measurements are the production code paths, not a
second implementation. Two drivers are used:

* Suit  (new, read-only): dial/12/pose/marker/rehaut/ellipse diagnostics per image, JSON lines.
* Batch (existing, used by the 337-photo regression): the raw measurement table per image.

If Java 17 or the OpenCV jar is unavailable the harness reports why, and the pipeline marks the
affected images inconclusive; it never substitutes guessed values."""
from __future__ import annotations

import csv
import hashlib
import json
import os
import shutil
import subprocess
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from .config import HARNESS_DIR, REPO_ROOT

OPENCV_JAR_URL = "https://repo1.maven.org/maven2/org/openpnp/opencv/4.9.0-0/opencv-4.9.0-0.jar"
DRIVERS = ("Suit.java", "Batch.java", "Load.java", "Six.java")


class HarnessUnavailable(Exception):
    pass


def _tool(name: str) -> str | None:
    home = os.environ.get("JAVA_HOME")
    if home and (Path(home) / "bin" / name).exists():
        return str(Path(home) / "bin" / name)
    return shutil.which(name)


class Harness:
    def __init__(self, cache_dir: Path | None = None, shards: int | None = None, heap: str = "1500m"):
        self.cache = Path(cache_dir or HARNESS_DIR / ".cache")
        self.jar = self.cache / "opencv-4.9.0-0.jar"
        self.shards = shards or max(1, min(4, (os.cpu_count() or 2) // 2))
        self.heap = heap
        self.classes: Path | None = None

    def check(self) -> str:
        java, javac = _tool("java"), _tool("javac")
        if not java or not javac:
            raise HarnessUnavailable("java/javac not found (JDK 17 required; set JAVA_HOME)")
        if not self.jar.exists() or self.jar.stat().st_size < 1_000_000:
            self.cache.mkdir(parents=True, exist_ok=True)
            import requests
            try:
                r = requests.get(OPENCV_JAR_URL, timeout=300)
                r.raise_for_status()
                if len(r.content) < 1_000_000:
                    raise HarnessUnavailable("OpenCV jar download returned a tiny body (Maven Central rate limit?)")
                self.jar.write_bytes(r.content)
            except requests.RequestException as e:
                raise HarnessUnavailable(f"could not download {OPENCV_JAR_URL}: {type(e).__name__}") from e
        return java

    def compile(self) -> Path:
        """Compiles the drivers with the app sources; cached by a hash of every source file."""
        if self.classes:
            return self.classes
        self.check()
        src_dirs = [HARNESS_DIR / "shim", REPO_ROOT / "android" / "app" / "src" / "main" / "java", HARNESS_DIR / "drivers"]
        h = hashlib.sha1()
        for d in src_dirs:
            for p in sorted(d.rglob("*.java")):
                st = p.stat()
                h.update(f"{p}:{st.st_size}:{int(st.st_mtime)}".encode())
        out = self.cache / f"harvest_classes_{h.hexdigest()[:12]}"
        if not (out / "com" / "watchalign" / "mobile" / "Suit.class").exists():
            shutil.rmtree(out, ignore_errors=True)
            out.mkdir(parents=True)
            cmd = [_tool("javac"), "-encoding", "UTF-8", "-nowarn", "-cp", str(self.jar), "-sourcepath",
                   os.pathsep.join(str(d) for d in src_dirs), "-d", str(out)] + [str(HARNESS_DIR / "drivers" / f) for f in DRIVERS]
            res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
            if res.returncode != 0:
                raise HarnessUnavailable("javac failed: " + res.stdout[-800:])
        self.classes = out
        return out

    def _java(self, main: str, args: list[str], log: Path) -> int:
        cmd = [_tool("java"), f"-Xmx{self.heap}", "-Dfile.encoding=UTF-8", "-cp", f"{self.jar}{os.pathsep}{self.classes}",
               f"com.watchalign.mobile.{main}"] + args
        with log.open("w") as f:
            return subprocess.run(cmd, stdout=f, stderr=subprocess.STDOUT, cwd=str(REPO_ROOT)).returncode

    def suitability(self, paths: list[str], work: Path) -> dict[str, dict]:
        """{absolute path: Suit record} for every path (records carry "error" when analysis failed)."""
        if not paths:
            return {}
        self.compile()
        work.mkdir(parents=True, exist_ok=True)
        n = min(self.shards, len(paths))
        chunks = [paths[k::n] for k in range(n)]

        def run(k: int) -> Path:
            lst, out = work / f"suit_{k}.txt", work / f"suit_{k}.jsonl"
            lst.write_text("\n".join(chunks[k]) + "\n", encoding="utf-8")
            self._java("Suit", [str(lst), str(out)], work / f"suit_{k}.log")
            return out

        with ThreadPoolExecutor(n) as ex:
            outs = list(ex.map(run, range(n)))
        recs: dict[str, dict] = {}
        for o in outs:
            if o.exists():
                for line in o.read_text(encoding="utf-8").splitlines():
                    if line.strip():
                        d = json.loads(line)
                        recs[d["path"]] = d
        for p in paths:
            recs.setdefault(p, {"path": p, "error": "no result (analysis crashed or timed out)"})
        return recs

    def measure(self, rows: list[dict], out_dir: Path) -> dict[str, dict]:
        """Runs the existing Batch driver (the regression measurement) on rows with keys
        path/class_label/physical_watch_id/factory. Returns {absolute path: measurement row}."""
        if not rows:
            return {}
        self.compile()
        out_dir.mkdir(parents=True, exist_ok=True)
        lst = out_dir / "list.csv"
        with lst.open("w", newline="", encoding="utf-8") as f:
            w = csv.writer(f)
            w.writerow(["local_path", "class_label", "physical_watch_id", "factory"])
            for r in rows:
                w.writerow([r["path"], r.get("class_label", ""), r.get("physical_watch_id", ""), (r.get("factory") or "").replace(",", ";")])
        n = min(self.shards, len(rows))

        def run(k: int) -> None:
            self._java("Batch", [str(lst), "/", str(out_dir / f"r{k}.csv"), str(out_dir / "crops"), str(n), str(k)], out_dir / f"log{k}")

        with ThreadPoolExecutor(n) as ex:
            list(ex.map(run, range(n)))
        return read_batch(out_dir)


def read_batch(out_dir: Path) -> dict[str, dict]:
    """Batch output (shard 0 carries the header)."""
    hdr, rows = None, []
    for f in sorted(out_dir.glob("r*.csv")):
        if f.name.endswith("_round.csv"):
            continue
        lines = list(csv.reader(f.open(newline="", encoding="utf-8")))
        if lines and lines[0] and lines[0][0] == "path":
            hdr = lines[0]
            lines = lines[1:]
        rows += lines
    if not hdr:
        return {}
    return {r[0]: dict(zip(hdr, r)) for r in rows if r}
