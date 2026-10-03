"""Reproducible manifest helpers for calibration runs."""
from __future__ import annotations

import hashlib
import json
import os
import subprocess
from datetime import datetime, timezone
from pathlib import Path

SCHEMA_VERSION = 1
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


def repo_commit(repo: Path) -> str:
    env = (os.environ.get("GITHUB_SHA") or "").strip()
    if env:
        return env
    try:
        return subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=repo, text=True, stderr=subprocess.DEVNULL
        ).strip()
    except (OSError, subprocess.SubprocessError):
        return "unknown"


def metric_definition_fingerprint(config: dict) -> str:
    return sha256_bytes(_canonical(config.get("calibration_metrics") or []))


def measurement_fingerprint(config: dict, commit: str) -> str:
    """Conservative v1 fingerprint for the code/contract that produces measurements.

    v1 deliberately includes the repository commit, so a code change forces explicit review rather
    than silently reusing an older calibration. A later adapter contract can narrow this to exact
    detector blobs without weakening the fail-closed behaviour.
    """
    payload = {
        "version": 1,
        "repo_commit": commit,
        "model": str(config.get("model") or "").strip().upper(),
        "family": config.get("family"),
        "measurement_adapter": config.get("measurement_adapter"),
        "metric_definition_fingerprint": metric_definition_fingerprint(config),
    }
    return sha256_bytes(_canonical(payload))


def freeze_config(config_path: Path, base: Path) -> tuple[Path, str]:
    data = config_path.read_bytes()
    target = base / "frozen_config.json"
    target.write_bytes(data)
    return target, sha256_bytes(data)


def evidence_manifest_identity(acquired_csv: Path | None) -> dict:
    if acquired_csv is None or not acquired_csv.exists():
        return {"path": None, "sha256": None}
    return {"path": str(acquired_csv), "sha256": sha256_file(acquired_csv)}


def build(config: dict, config_path: Path, base: Path, repo: Path, *, acquired_csv: Path | None = None) -> dict:
    frozen, config_hash = freeze_config(config_path, base)
    commit = repo_commit(repo)
    return {
        "schema_version": SCHEMA_VERSION,
        "created_at_utc": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        "requested_model": str(config.get("model") or "").strip().upper(),
        "family": config.get("family"),
        "repo_commit": commit,
        "config": {
            "source_path": str(config_path),
            "frozen_path": str(frozen),
            "sha256": config_hash,
        },
        "adapters": {
            "acquisition": config.get("acquisition_adapter"),
            "measurement": config.get("measurement_adapter"),
        },
        "versions": {
            "calibration_engine": CALIBRATION_ENGINE_VERSION,
            "metric_definition_fingerprint": metric_definition_fingerprint(config),
            "measurement_fingerprint": measurement_fingerprint(config, commit),
        },
        "evidence_manifest": evidence_manifest_identity(acquired_csv),
    }


def save(manifest: dict, path: Path) -> None:
    path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
