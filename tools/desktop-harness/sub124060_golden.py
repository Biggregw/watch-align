#!/usr/bin/env python3
"""Deterministic Rolex Submariner 124060 image regression fixture.

The fixture is deliberately model-specific and uses the production 124060 route through the
same desktop harness used by the watch-family calibrator. Third-party image bytes are not
committed. The repository stores only SHA-256 identity/provenance and the frozen production
outputs for each photo.

Typical use:

    # Compare every fixture image found under one or more roots.
    python3 tools/desktop-harness/sub124060_golden.py check \
        --images datasets/watch_calibrator/124060/dataset/images

    # Require the complete corpus rather than accepting a partial local subset.
    python3 tools/desktop-harness/sub124060_golden.py check --require-all --images /path/to/images

    # Validate the committed fixture without needing image bytes.
    python3 tools/desktop-harness/sub124060_golden.py verify

    # Maintainers only: rebuild a fixture from a reviewed image list. The CSV must contain
    # physical_watch_id and local_path; class_label/factory are optional provenance columns.
    python3 tools/desktop-harness/sub124060_golden.py build --list reviewed_images.csv

Exit status: 0 identical/valid; 1 differences or invalid fixture; 2 no fixture images found.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import subprocess
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
FIXTURE = HERE / "sub124060_golden"
HARNESS = HERE / "run.sh"
DRIVER = "Sub124060Golden"
IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".webp"}

VALUE_KEYS = [
    "twelve.rotation_deg",
    "twelve.gap_r",
    "twelve.centring_w",
    "round.ring_rho",
    "round.spacing_rms_deg",
    "baton.3_9_line_offset_r",
    "axis.12_6_line_offset_r",
]
EXPECTED_COLUMNS = ["sha256", "dial_source", "dial_reproducible", "pose_tilt_deg"]
for _key in VALUE_KEYS:
    EXPECTED_COLUMNS.extend([_key, _key + ".label"])
EXPECTED_COLUMNS.extend([
    "twelve.overall",
    "rounds.overall",
    "baton.3_9.overall",
    "axis.12_6.overall",
])


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def read_csv(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def write_csv(path: Path, fieldnames: list[str], rows: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=fieldnames, extrasaction="ignore")
        w.writeheader()
        w.writerows(rows)


def run_driver(items: list[tuple[str, Path]], work: Path) -> list[dict[str, str]]:
    work.mkdir(parents=True, exist_ok=True)
    list_tsv = work / "sub124060_golden.list.tsv"
    out_csv = work / "sub124060_golden.out.csv"
    with list_tsv.open("w", encoding="utf-8") as f:
        for watch_id, path in items:
            f.write(f"{watch_id}\t{path.resolve()}\n")
    subprocess.run(
        ["bash", str(HARNESS), DRIVER, str(list_tsv), str(out_csv)],
        cwd=REPO,
        check=True,
    )
    return read_csv(out_csv)


def fixture_rows() -> tuple[list[dict[str, str]], list[dict[str, str]]]:
    manifest = read_csv(FIXTURE / "manifest.csv")
    expected = read_csv(FIXTURE / "expected.csv")
    return manifest, expected


def verify_fixture() -> int:
    try:
        manifest, expected = fixture_rows()
    except (OSError, csv.Error) as e:
        print(json.dumps({"valid": False, "error": str(e)}, indent=1))
        return 1

    errors: list[str] = []
    if not manifest:
        errors.append("manifest is empty")
    if not expected:
        errors.append("expected output is empty")

    manifest_sha = [r.get("sha256", "") for r in manifest]
    expected_sha = [r.get("sha256", "") for r in expected]
    if any(len(h) != 64 for h in manifest_sha):
        errors.append("manifest contains a missing or non-SHA256 key")
    if len(set(manifest_sha)) != len(manifest_sha):
        errors.append("manifest contains duplicate SHA256 keys")
    if len(set(expected_sha)) != len(expected_sha):
        errors.append("expected.csv contains duplicate SHA256 keys")
    if set(manifest_sha) != set(expected_sha):
        errors.append("manifest and expected.csv do not contain the same image SHA256 set")

    actual_columns = list(expected[0].keys()) if expected else []
    if actual_columns != EXPECTED_COLUMNS:
        errors.append(
            "expected.csv schema differs from the production golden schema: "
            + json.dumps({"expected": EXPECTED_COLUMNS, "actual": actual_columns})
        )

    allowed_labels = {"CLEAR", "CHECK", "CHECK CLOSELY", "MEASURED / NOT YET JUDGED", "NOT JUDGED"}
    for i, row in enumerate(expected, start=2):
        for c in [x for x in EXPECTED_COLUMNS if x.endswith(".label") or x.endswith(".overall")]:
            if row.get(c, "") not in allowed_labels:
                errors.append(f"{c} has invalid label on expected.csv line {i}: {row.get(c)!r}")
                if len(errors) >= 20:
                    break
        if len(errors) >= 20:
            break

    print(json.dumps({
        "valid": not errors,
        "fixture_images": len(manifest),
        "errors": errors[:20],
    }, indent=1))
    return 1 if errors else 0


def check(roots: list[Path], require_all: bool, work: Path | None) -> int:
    manifest, expected_rows = fixture_rows()
    manifest_by_sha = {r["sha256"]: r for r in manifest}
    expected = {r["sha256"]: r for r in expected_rows}

    found: dict[str, Path] = {}
    for root in roots:
        if root.is_file() and root.suffix.lower() in IMAGE_SUFFIXES:
            candidates = [root]
        elif root.exists():
            candidates = (p for p in root.rglob("*") if p.is_file() and p.suffix.lower() in IMAGE_SUFFIXES)
        else:
            continue
        for p in candidates:
            h = sha256(p)
            if h in manifest_by_sha and h not in found:
                found[h] = p.resolve()

    if not found:
        print(json.dumps({
            "fixture_images": len(manifest),
            "images_located": 0,
            "differences": 0,
            "status": "no fixture images available; 124060 golden comparison NOT run",
        }, indent=1))
        return 2

    work = work or Path(tempfile.mkdtemp(prefix="sub124060_golden_"))
    items = [(manifest_by_sha[h]["physical_watch_id"], p) for h, p in sorted(found.items())]
    got_rows = run_driver(items, work)

    path_sha = {str(p.resolve()): h for h, p in found.items()}
    got: dict[str, dict[str, str]] = {}
    for row in got_rows:
        p = str(Path(row.get("path", "")).resolve())
        h = path_sha.get(p)
        if h:
            got[h] = row

    diffs: list[dict[str, str]] = []
    for h in sorted(found):
        exp = expected[h]
        row = got.get(h)
        if row is None:
            diffs.append({"sha256": h, "column": "*", "expected": "row", "got": "missing"})
            continue
        for col in EXPECTED_COLUMNS[1:]:
            want = exp.get(col, "")
            have = row.get(col, "")
            if have != want:
                diffs.append({"sha256": h, "column": col, "expected": want, "got": have})

    missing = len(manifest) - len(found)
    if require_all and missing:
        diffs.append({
            "sha256": "*",
            "column": "fixture_coverage",
            "expected": str(len(manifest)),
            "got": str(len(found)),
        })

    print(json.dumps({
        "fixture_images": len(manifest),
        "images_located": len(found),
        "missing_images": missing,
        "differences": len(diffs),
        "first": diffs[:20],
    }, indent=1))
    return 1 if diffs else 0


def build(list_csv: Path, work: Path | None) -> int:
    rows = read_csv(list_csv)
    if not rows:
        raise SystemExit("build list is empty")
    required = {"physical_watch_id", "local_path"}
    missing = required - set(rows[0])
    if missing:
        raise SystemExit("build list is missing required columns: " + ", ".join(sorted(missing)))

    items: list[tuple[str, Path]] = []
    meta_by_path: dict[str, dict[str, str]] = {}
    seen_sha: set[str] = set()
    manifest: list[dict[str, str]] = []
    for row in rows:
        path = Path(row["local_path"]).resolve()
        if not path.is_file():
            raise SystemExit(f"image not found: {path}")
        h = sha256(path)
        if h in seen_sha:
            continue
        seen_sha.add(h)
        items.append((row["physical_watch_id"], path))
        meta_by_path[str(path)] = {**row, "sha256": h}
        manifest.append({
            "sha256": h,
            "class_label": row.get("class_label", ""),
            "physical_watch_id": row["physical_watch_id"],
            "factory": row.get("factory", ""),
            "name": path.name,
        })

    work = work or Path(tempfile.mkdtemp(prefix="sub124060_golden_build_"))
    measured = run_driver(items, work)
    expected: list[dict[str, str]] = []
    for row in measured:
        p = str(Path(row["path"]).resolve())
        meta = meta_by_path.get(p)
        if meta is None:
            raise SystemExit(f"driver returned unexpected path: {p}")
        out = {"sha256": meta["sha256"]}
        for col in EXPECTED_COLUMNS[1:]:
            out[col] = row.get(col, "")
        expected.append(out)

    manifest.sort(key=lambda r: r["sha256"])
    expected.sort(key=lambda r: r["sha256"])
    write_csv(FIXTURE / "manifest.csv", ["sha256", "class_label", "physical_watch_id", "factory", "name"], manifest)
    write_csv(FIXTURE / "expected.csv", EXPECTED_COLUMNS, expected)
    print(json.dumps({"fixture_written": len(expected), "directory": str(FIXTURE)}, indent=1))
    return verify_fixture()


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)

    c = sub.add_parser("check")
    c.add_argument("--images", type=Path, action="append", required=True)
    c.add_argument("--require-all", action="store_true")
    c.add_argument("--work", type=Path)

    b = sub.add_parser("build")
    b.add_argument("--list", type=Path, required=True)
    b.add_argument("--work", type=Path)

    sub.add_parser("verify")
    a = ap.parse_args()
    if a.cmd == "check":
        return check(a.images, a.require_all, a.work)
    if a.cmd == "build":
        return build(a.list, a.work)
    return verify_fixture()


if __name__ == "__main__":
    raise SystemExit(main())
