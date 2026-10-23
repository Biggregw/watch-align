"""Unit tests for the CI archive bridge: no Graph, GitHub, secrets or photographs."""
import hashlib
import json
from pathlib import Path
from unittest.mock import patch

import pytest
from tools.image_archive import cloud_jobs as jobs


def picture():
    return b"\xff\xd8\xff" + b"original image" * 50


def item():
    raw = picture()
    h = hashlib.sha256(raw).hexdigest()
    return {"sha256": h, "format": "jpg", "size": len(raw),
            "key": f"unreviewed/objects/{h[:2]}/{h}.jpg",
            "review_status": "unreviewed", "calibration_approved": False,
            "physical_watch_identity_conflict": False,
            "associations": [{"model": "124060", "provenance": "official",
                              "source_url": "https://example.test/photograph",
                              "physical_watch_id": "example-watch"}]}


class Drive:
    def __init__(self, records=None):
        self.records = records or [item()]
        self.payloads = {self.records[0]["key"]: picture()}
        self.manifest_keys = ["unreviewed/manifests/runs/test-manifest.json"]
        self.requests = []
    def _item(self, key):
        return "https://graph.microsoft.com/v1.0/me/drive/items/root:/" + key
    def _request(self, method, url, allowed):
        self.requests.append((method, url))
        class Response:
            status_code = 200
            def json(s):
                return {"value": [{"name": "test-manifest.json", "file": {}}]}
        return Response()
    def get(self, key):
        if key in self.manifest_keys:
            return json.dumps({"schema_version": 1,
                               "purpose": "unaltered_original_backup_only",
                               "calibration_approved": False, "review_status": "unreviewed",
                               "items": self.records}).encode()
        return self.payloads[key]


def test_missing_protected_credentials_never_connects():
    with pytest.raises(RuntimeError, match="not been configured"):
        jobs.drive_from_protected_environment({"CI": "true"})


def test_remote_manifest_collects_exact_originals():
    remote = Drive()
    index = jobs.remote_index(remote)
    assert len(index) == 1
    assert next(iter(index.values()))["calibration_approved"] is False
    assert jobs.verify_remote(remote)["all_indexed_verified"]


def test_invalid_sha_key_is_rejected():
    remote = Drive()
    remote.records[0] = {**remote.records[0], "key": "unreviewed/objects/ff/fake.jpg"}
    with pytest.raises(ValueError, match="mismatched"):
        jobs.remote_index(remote)


def test_remote_corruption_fails_closed():
    remote = Drive()
    remote.payloads[item()["key"]] = b"tampered"
    with pytest.raises(ValueError, match="verification failed"):
        jobs.verify_remote(remote)


def test_unapproved_export_includes_original_and_catalogue(tmp_path):
    remote = Drive()
    output = tmp_path / "review.zip"
    result = jobs.export_review_batch(remote, output, limit=3)
    assert result["review_photos"] == 1
    assert result["calibration_approved"] is False
    import zipfile
    with zipfile.ZipFile(output) as f:
        files = f.namelist()
        assert "catalogue.csv" in files
        assert "catalogue.json" in files
        image_name = next(name for name in files if name.startswith("originals/"))
        assert f.read(image_name) == picture()
        data = json.loads(f.read("catalogue.json"))
        assert data["calibration_approved"] is False
        assert data["images"][0]["associations"][0]["model"] == "124060"


def test_bad_manifest_cannot_assert_approval():
    remote = Drive()
    remote.records[0]["calibration_approved"] = True
    with pytest.raises(ValueError, match="may not claim"):
        jobs.remote_index(remote)


def test_preflight_needs_no_one_drive_credentials(tmp_path):
    root = tmp_path / "repo"
    root.mkdir()
    (root / "x.jpg").write_bytes(picture())
    with patch("tools.image_archive.unreviewed.catalogue_rows", return_value=iter([])), \
         patch.object(jobs, "drive_from_protected_environment",
                      side_effect=AssertionError("unexpected cloud connection")):
        report = jobs.main(["preflight", "--repo", str(root)])
    assert report["unique_originals"] == 1


def test_paginated_manifest_refuses_foreign_server():
    remote = Drive()
    class Bad:
        status_code = 200
        def json(self):
            return {"value": [], "@odata.nextLink": "https://attacker.test/leak"}
    remote._request = lambda *args, **kwargs: Bad()
    with pytest.raises(RuntimeError, match="pagination host"):
        jobs.manifest_keys(remote)
