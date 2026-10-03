"""Immutable, content-addressed calibration evidence snapshots.

A calibration replay must never depend on a mutable dealer/CDN URL or an Actions cache. This
module freezes the exact acquired image bytes, acquisition manifest, locked watch split and model
config into a portable snapshot. Replay verifies every byte before materialising a workspace.
"""
from __future__ import annotations

import csv
import hashlib
import json
import os
import shutil
import tempfile
from pathlib import Path

SCHEMA_VERSION = 1
HASH_ALGORITHM = "sha256"


class SnapshotError(ValueError):
    """Raised when evidence cannot be frozen or replayed without ambiguity."""


def _sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def _canonical(value: object) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode("utf-8")


def _safe_relative(value: str, label: str) -> Path:
    p = Path((value or "").strip())
    if not value or p.is_absolute() or ".." in p.parts:
        raise SnapshotError(f"{label}: expected a safe relative path, got {value!r}")
    return p


def _object_rel(sha256: str) -> Path:
    if len(sha256) != 64 or any(c not in "0123456789abcdef" for c in sha256.lower()):
        raise SnapshotError(f"invalid sha256 {sha256!r}")
    sha256 = sha256.lower()
    return Path("objects") / HASH_ALGORITHM / sha256[:2] / sha256


def _copy_verified(source: Path, target: Path, expected_sha: str) -> None:
    if not source.is_file():
        raise SnapshotError(f"missing acquired image {source}")
    actual = _sha256_file(source)
    if actual != expected_sha.lower():
        raise SnapshotError(f"image hash mismatch for {source}: expected {expected_sha}, got {actual}")
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        if _sha256_file(target) != expected_sha.lower():
            raise SnapshotError(f"content-addressed object collision at {target}")
        return
    shutil.copyfile(source, target)


def _read_rows(path: Path) -> tuple[list[str], list[dict[str, str]]]:
    with path.open(newline="", encoding="utf-8") as fh:
        reader = csv.DictReader(fh)
        if not reader.fieldnames:
            raise SnapshotError(f"{path}: CSV header is required")
        return list(reader.fieldnames), list(reader)


def _identity_payload(model: str, family: str, config_sha: str, acquired_sha: str,
                      split_sha: str, objects: list[dict]) -> dict:
    return {
        "schema_version": SCHEMA_VERSION,
        "model": model,
        "family": family,
        "config_sha256": config_sha,
        "acquired_images_sha256": acquired_sha,
        "locked_split_sha256": split_sha,
        "objects": sorted(objects, key=lambda o: o["sha256"]),
    }


def _snapshot_id(payload: dict) -> str:
    return hashlib.sha256(_canonical(payload)).hexdigest()


def create(model: str, family: str, config_path: Path, acquired_csv: Path,
           acquisition_root: Path, split_csv: Path, snapshot_dir: Path) -> dict:
    """Freeze an acquisition workspace into an immutable portable snapshot.

    The snapshot is built in a sibling temporary directory and atomically renamed into place. An
    existing snapshot is never overwritten.
    """
    model = (model or "").strip().upper()
    family = (family or "").strip()
    if not model or not family:
        raise SnapshotError("model and family are required")
    if snapshot_dir.exists():
        raise SnapshotError(f"snapshot already exists: {snapshot_dir}")
    for required in (config_path, acquired_csv, split_csv):
        if not required.is_file():
            raise SnapshotError(f"missing snapshot input {required}")

    fields, rows = _read_rows(acquired_csv)
    required_fields = {"model", "physical_watch_id", "local_path", "sha256", "acquisition_status"}
    missing = sorted(required_fields - set(fields))
    if missing:
        raise SnapshotError(f"acquired manifest missing fields: {', '.join(missing)}")

    snapshot_dir.parent.mkdir(parents=True, exist_ok=True)
    tmp = Path(tempfile.mkdtemp(prefix=f".{snapshot_dir.name}.", dir=snapshot_dir.parent))
    try:
        shutil.copyfile(config_path, tmp / "frozen_config.json")
        shutil.copyfile(acquired_csv, tmp / "acquired_images.csv")
        shutil.copyfile(split_csv, tmp / "locked_split.csv")

        objects: dict[str, dict] = {}
        acquired_rows = 0
        for line_no, row in enumerate(rows, start=2):
            actual_model = (row.get("model") or "").strip().upper()
            if actual_model != model:
                raise SnapshotError(
                    f"acquired manifest line {line_no}: model {actual_model!r} does not match {model}"
                )
            if (row.get("acquisition_status") or "").strip() != "acquired":
                continue
            acquired_rows += 1
            rel = _safe_relative(row.get("local_path") or "", f"acquired line {line_no} local_path")
            sha = (row.get("sha256") or "").strip().lower()
            obj_rel = _object_rel(sha)
            source = acquisition_root / rel
            _copy_verified(source, tmp / obj_rel, sha)
            try:
                expected_bytes = int(row.get("bytes") or source.stat().st_size)
            except ValueError:
                raise SnapshotError(f"acquired manifest line {line_no}: invalid bytes value") from None
            actual_bytes = source.stat().st_size
            if expected_bytes != actual_bytes:
                raise SnapshotError(
                    f"acquired manifest line {line_no}: byte count {expected_bytes} does not match file {actual_bytes}"
                )
            objects.setdefault(sha, {
                "sha256": sha,
                "bytes": actual_bytes,
                "object_path": obj_rel.as_posix(),
            })

        config_sha = _sha256_file(tmp / "frozen_config.json")
        acquired_sha = _sha256_file(tmp / "acquired_images.csv")
        split_sha = _sha256_file(tmp / "locked_split.csv")
        payload = _identity_payload(model, family, config_sha, acquired_sha, split_sha, list(objects.values()))
        snapshot_id = _snapshot_id(payload)
        manifest = {
            **payload,
            "snapshot_id": snapshot_id,
            "hash_algorithm": HASH_ALGORITHM,
            "files": {
                "config": "frozen_config.json",
                "acquired_images": "acquired_images.csv",
                "locked_split": "locked_split.csv",
            },
            "counts": {
                "manifest_rows": len(rows),
                "acquired_rows": acquired_rows,
                "unique_image_objects": len(objects),
                "unique_image_bytes": sum(o["bytes"] for o in objects.values()),
            },
        }
        (tmp / "snapshot.json").write_text(
            json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
        verify(tmp)
        os.replace(tmp, snapshot_dir)
        return manifest
    except Exception:
        shutil.rmtree(tmp, ignore_errors=True)
        raise


def load(snapshot_dir: Path) -> dict:
    path = snapshot_dir / "snapshot.json"
    if not path.is_file():
        raise SnapshotError(f"missing snapshot manifest {path}")
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise SnapshotError(f"invalid snapshot manifest: {exc}") from exc
    if not isinstance(value, dict) or value.get("schema_version") != SCHEMA_VERSION:
        raise SnapshotError(f"unsupported snapshot schema {value.get('schema_version') if isinstance(value, dict) else None}")
    return value


def verify(snapshot_dir: Path) -> dict:
    """Verify manifest identity and every content-addressed image object."""
    manifest = load(snapshot_dir)
    files = manifest.get("files") or {}
    config = snapshot_dir / _safe_relative(files.get("config") or "", "snapshot config path")
    acquired = snapshot_dir / _safe_relative(files.get("acquired_images") or "", "snapshot acquired path")
    split = snapshot_dir / _safe_relative(files.get("locked_split") or "", "snapshot split path")
    for p in (config, acquired, split):
        if not p.is_file():
            raise SnapshotError(f"snapshot file missing: {p}")

    if _sha256_file(config) != manifest.get("config_sha256"):
        raise SnapshotError("snapshot config hash mismatch")
    if _sha256_file(acquired) != manifest.get("acquired_images_sha256"):
        raise SnapshotError("snapshot acquired manifest hash mismatch")
    if _sha256_file(split) != manifest.get("locked_split_sha256"):
        raise SnapshotError("snapshot split hash mismatch")

    objects = manifest.get("objects")
    if not isinstance(objects, list):
        raise SnapshotError("snapshot objects must be a list")
    seen = set()
    for obj in objects:
        sha = str((obj or {}).get("sha256") or "").lower()
        if sha in seen:
            raise SnapshotError(f"duplicate snapshot object {sha}")
        seen.add(sha)
        rel = _safe_relative(str((obj or {}).get("object_path") or ""), "snapshot object path")
        if rel != _object_rel(sha):
            raise SnapshotError(f"snapshot object path does not match content hash {sha}")
        path = snapshot_dir / rel
        if not path.is_file():
            raise SnapshotError(f"snapshot object missing: {rel}")
        if _sha256_file(path) != sha:
            raise SnapshotError(f"snapshot object hash mismatch: {rel}")
        if path.stat().st_size != int(obj.get("bytes") or -1):
            raise SnapshotError(f"snapshot object byte count mismatch: {rel}")

    payload = _identity_payload(
        str(manifest.get("model") or ""), str(manifest.get("family") or ""),
        str(manifest.get("config_sha256") or ""), str(manifest.get("acquired_images_sha256") or ""),
        str(manifest.get("locked_split_sha256") or ""), objects,
    )
    if _snapshot_id(payload) != manifest.get("snapshot_id"):
        raise SnapshotError("snapshot identity mismatch")

    _, rows = _read_rows(acquired)
    object_hashes = set(seen)
    for line_no, row in enumerate(rows, start=2):
        if (row.get("model") or "").strip().upper() != manifest.get("model"):
            raise SnapshotError(f"snapshot acquired line {line_no}: model mismatch")
        if (row.get("acquisition_status") or "").strip() == "acquired":
            sha = (row.get("sha256") or "").strip().lower()
            if sha not in object_hashes:
                raise SnapshotError(f"snapshot acquired line {line_no}: missing object {sha}")
            _safe_relative(row.get("local_path") or "", f"snapshot acquired line {line_no} local_path")
    return manifest


def materialize(snapshot_dir: Path, acquisition_root: Path, split_path: Path,
                config_path: Path | None = None) -> dict:
    """Restore a verified snapshot to the workspace shape expected by the measurement pipeline."""
    manifest = verify(snapshot_dir)
    if acquisition_root.exists() and any(acquisition_root.iterdir()):
        raise SnapshotError(f"replay acquisition root must be empty: {acquisition_root}")
    acquisition_root.mkdir(parents=True, exist_ok=True)
    split_path.parent.mkdir(parents=True, exist_ok=True)

    acquired_source = snapshot_dir / manifest["files"]["acquired_images"]
    acquired_target = acquisition_root / "acquired_images.csv"
    shutil.copyfile(acquired_source, acquired_target)
    shutil.copyfile(snapshot_dir / manifest["files"]["locked_split"], split_path)
    if config_path is not None:
        config_path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(snapshot_dir / manifest["files"]["config"], config_path)

    _, rows = _read_rows(acquired_target)
    for line_no, row in enumerate(rows, start=2):
        if (row.get("acquisition_status") or "").strip() != "acquired":
            continue
        rel = _safe_relative(row.get("local_path") or "", f"replay line {line_no} local_path")
        sha = (row.get("sha256") or "").strip().lower()
        source = snapshot_dir / _object_rel(sha)
        target = acquisition_root / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        try:
            os.link(source, target)
        except OSError:
            shutil.copyfile(source, target)
        if _sha256_file(target) != sha:
            raise SnapshotError(f"materialized image hash mismatch: {rel}")
    return manifest
