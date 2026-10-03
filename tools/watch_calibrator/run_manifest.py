"""Reproducible manifest helpers for calibration runs."""
from __future__ import annotations

import hashlib
import json
import os
import subprocess
from datetime import datetime, timezone
from pathlib import Path

SCHEMA_VERSION = 2
CALIBRATION_ENGINE_VERSION = "genuine_envelope_v1"


def _canonical(value: object) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode("utf-8")


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def _git(repo: Path, *args: str) -> str:
    try:
        return subprocess.check_output(
            ["git", *args], cwd=repo, text=True, stderr=subprocess.DEVNULL
        ).strip()
    except (OSError, subprocess.SubprocessError):
        return ""


def repo_commit(repo: Path) -> str:
    """Exact checked-out commit, not an assumed PR head."""
    return _git(repo, "rev-parse", "HEAD") or (os.environ.get("GITHUB_SHA") or "").strip() or "unknown"


def repo_tree(repo: Path) -> str:
    return _git(repo, "rev-parse", "HEAD^{tree}") or "unknown"


def source_provenance(repo: Path) -> dict:
    """Keep execution checkout identity separate from review/head identity.

    Pull-request workflows normally execute a generated merge commit. WATCH_ALIGN_HEAD_SHA and
    WATCH_ALIGN_BASE_SHA are explicitly populated by the workflow so the manifest can preserve all
    three identities without confusing one for another.
    """
    checkout = repo_commit(repo)
    head = (os.environ.get("WATCH_ALIGN_HEAD_SHA") or os.environ.get("GITHUB_HEAD_SHA") or "").strip()
    base = (os.environ.get("WATCH_ALIGN_BASE_SHA") or "").strip()
    return {
        "checkout_sha": checkout,
        "checkout_tree_sha": repo_tree(repo),
        "head_sha": head or checkout,
        "base_sha": base or None,
        "workflow_run_id": (os.environ.get("GITHUB_RUN_ID") or "").strip() or None,
        "workflow_run_attempt": (os.environ.get("GITHUB_RUN_ATTEMPT") or "").strip() or None,
        "workflow_event": (os.environ.get("GITHUB_EVENT_NAME") or "").strip() or None,
        "workflow_ref": (os.environ.get("GITHUB_WORKFLOW_REF") or "").strip() or None,
    }


def metric_definition_fingerprint(config: dict) -> str:
    return sha256_bytes(_canonical(config.get("calibration_metrics") or []))


def measurement_fingerprint(config: dict, commit: str, tree_sha: str | None = None) -> str:
    """Conservative v2 fingerprint for the code/contract that produces measurements.

    It deliberately includes the checked-out source identity. The later versioned adapter contract
    can narrow this to adapter-specific source blobs without weakening fail-closed invalidation.
    """
    payload = {
        "version": 2,
        "checkout_sha": commit,
        "checkout_tree_sha": tree_sha or "unknown",
        "model": str(config.get("model") or "").strip().upper(),
        "family": config.get("family"),
        "measurement_adapter": config.get("measurement_adapter"),
        "metric_definition_fingerprint": metric_definition_fingerprint(config),
    }
    return sha256_bytes(_canonical(payload))


def _relative(path: Path, root: Path) -> str:
    try:
        return path.resolve().relative_to(root.resolve()).as_posix()
    except ValueError:
        return path.name


def freeze_config(config_path: Path, base: Path) -> tuple[Path, str]:
    data = config_path.read_bytes()
    target = base / "frozen_config.json"
    target.write_bytes(data)
    return target, sha256_bytes(data)


def evidence_manifest_identity(acquired_csv: Path | None, base: Path | None = None) -> dict:
    if acquired_csv is None or not acquired_csv.exists():
        return {"path": None, "sha256": None}
    root = base or acquired_csv.parent
    return {"path": _relative(acquired_csv, root), "sha256": sha256_file(acquired_csv)}


def build(config: dict, config_path: Path, base: Path, repo: Path, *, acquired_csv: Path | None = None) -> dict:
    frozen, config_hash = freeze_config(config_path, base)
    provenance = source_provenance(repo)
    checkout = provenance["checkout_sha"]
    tree_sha = provenance["checkout_tree_sha"]
    return {
        "schema_version": SCHEMA_VERSION,
        "created_at_utc": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        "requested_model": str(config.get("model") or "").strip().upper(),
        "family": config.get("family"),
        "source": provenance,
        "config": {
            "source_path": _relative(config_path, repo),
            "frozen_path": _relative(frozen, base),
            "sha256": config_hash,
        },
        "adapters": {
            "acquisition": config.get("acquisition_adapter"),
            "measurement": config.get("measurement_adapter"),
        },
        "versions": {
            "calibration_engine": CALIBRATION_ENGINE_VERSION,
            "metric_definition_fingerprint": metric_definition_fingerprint(config),
            "measurement_fingerprint": measurement_fingerprint(config, checkout, tree_sha),
        },
        "evidence_manifest": evidence_manifest_identity(acquired_csv, base),
        "evidence_snapshot": None,
    }


def attach_snapshot(manifest: dict, snapshot: dict, snapshot_path: Path, base: Path) -> None:
    manifest["evidence_snapshot"] = {
        "schema_version": snapshot.get("schema_version"),
        "snapshot_id": snapshot.get("snapshot_id"),
        "path": _relative(snapshot_path, base),
        "acquired_images_sha256": snapshot.get("acquired_images_sha256"),
        "locked_split_sha256": snapshot.get("locked_split_sha256"),
        "unique_image_objects": (snapshot.get("counts") or {}).get("unique_image_objects"),
        "unique_image_bytes": (snapshot.get("counts") or {}).get("unique_image_bytes"),
    }


def save(manifest: dict, path: Path) -> None:
    path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
