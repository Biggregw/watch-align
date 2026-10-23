"""Offline regression checks for excluding screened originals from private review batches.

No real photographs, OneDrive connections, secrets or external network operations.
"""
from hashlib import sha256
import json
from pathlib import Path
from unittest.mock import patch

import pytest
from tools.image_archive import cloud_jobs as jobs

REPO_ROOT = Path(__file__).resolve().parents[3]
SELECTION = "archive/selections/sel-20261023-screened-373.json"


class MemoryDrive:
    def __init__(self):
        self.writes = {}

    def put(self, key, data):
        self.writes[key] = data

    def get(self, key):
        return self.writes[key]


def archive_fixture(tmp_path):
    """Simulate the real 373 selected SHA identities, plus 44 independent remaining originals."""
    source = REPO_ROOT / SELECTION
    target = tmp_path / SELECTION
    target.parent.mkdir(parents=True)
    target.write_bytes(source.read_bytes())
    screening = json.loads(source.read_text())
    assert len(screening["photos"]) == 373
    screened = {p["sha256"] for p in screening["photos"]}
    remaining = {sha256(f"remaining photo {i}".encode()).hexdigest() for i in range(44)}
    assert not (screened & remaining)
    index = {}
    for digest in screened | remaining:
        # Deliberately mix catalogue-linked and metadata-poor originals.
        assoc = [{"model": "124060", "physical_watch_id": digest[:10]}] if digest in screened else []
        index[digest] = {"sha256": digest, "key": f"unreviewed/objects/{digest[:2]}/{digest}.jpg",
                         "format": "jpg", "size": 1200, "associations": assoc}
    return index, screened, remaining


def batch_without_images(batch, fetch):
    # The existing build_batch_files already verifies original bytes and renders private previews.
    # These tests isolate only the SHA selection and count safety before any OneDrive writes.
    return {"batch.json": json.dumps(batch).encode()}


def test_exactly_44_unscreened_from_417_without_repeating_any_of_373(tmp_path):
    index, screened, remaining = archive_fixture(tmp_path)
    drive = MemoryDrive()
    with patch.object(jobs, "remote_index", return_value=index), \
         patch.object(jobs.R, "build_batch_files", side_effect=batch_without_images):
        report = jobs.create_review_batch(
            drive, tmp_path, size=44, exclude_selection=SELECTION, expected_remaining=44)
    assert report["indexed_originals"] == 417
    assert report["excluded_previously_screened"] == 373
    assert report["remaining_after_selection"] == 44
    assert report["review_photos"] == 44
    batch_file = next((tmp_path / "archive/batches").glob("*.json"))
    doc = json.loads(batch_file.read_text())
    output = {p["sha256"] for p in doc["photos"]}
    assert output == remaining and not (output & screened)
    assert all(not k.startswith("unreviewed/") for k in drive.writes)


def test_mismatched_417_count_refuses_before_creating_copies(tmp_path):
    index, _, remaining = archive_fixture(tmp_path)
    index.pop(next(iter(remaining)))
    drive = MemoryDrive()
    with patch.object(jobs, "remote_index", return_value=index), \
         patch.object(jobs.R, "build_batch_files", side_effect=AssertionError("must not build")):
        with pytest.raises(ValueError, match="expected 44 remaining originals; found 43"):
            jobs.create_review_batch(
                drive, tmp_path, size=44, exclude_selection=SELECTION, expected_remaining=44)
    assert not drive.writes
    assert not (tmp_path / "archive/batches").exists()


def test_missing_screened_sha_refuses_before_any_copy(tmp_path):
    index, screened, _ = archive_fixture(tmp_path)
    index.pop(next(iter(screened)))
    drive = MemoryDrive()
    with patch.object(jobs, "remote_index", return_value=index), \
         patch.object(jobs.R, "build_batch_files", side_effect=AssertionError("must not build")):
        with pytest.raises(ValueError, match="1 hashes absent from live archive"):
            jobs.create_review_batch(
                drive, tmp_path, size=44, exclude_selection=SELECTION, expected_remaining=44)
    assert not drive.writes


@pytest.mark.parametrize("options,match", [
    ({"size": 44, "exclude_selection": "../secrets.json"}, "exclude_selection"),
    ({"size": 44, "expected_remaining": 44}, "expected_remaining"),
    ({"size": 44, "exclude_selection": SELECTION, "expected_remaining": True}, "expected_remaining"),
    ({"size": 44, "exclude_selection": SELECTION, "expected_remaining": "44"}, "expected_remaining"),
    ({"size": 61, "exclude_selection": SELECTION}, "size"),
])
def test_review_request_rejects_unsafe_or_invalid_filters(tmp_path, options, match):
    path = tmp_path / "test-request.json"
    path.write_text(json.dumps({"task": "review_batch", "options": options}))
    with pytest.raises(ValueError, match=match):
        jobs.parse_request(path)


def test_unfiltered_existing_review_batch_still_works(tmp_path):
    digest = sha256(b"unchanged existing flow").hexdigest()
    index = {digest: {"sha256": digest, "key": f"unreviewed/objects/{digest[:2]}/{digest}.jpg",
                      "format": "jpg", "size": 100, "associations": []}}
    drive = MemoryDrive()
    with patch.object(jobs, "remote_index", return_value=index), \
         patch.object(jobs.R, "build_batch_files", side_effect=batch_without_images):
        result = jobs.create_review_batch(drive, tmp_path, size=1)
    assert result["review_photos"] == 1
    assert result["indexed_originals"] == 1
