"""End-to-end archive automation on an in-memory OneDrive: interruption, retry, duplicates, hashes,
manifest recovery, credentials, requests, visual review and the calibration boundary."""
from hashlib import sha256
import io
import json
from pathlib import Path
from unittest.mock import patch

import pytest
import yaml
from PIL import Image

from tools.image_archive import cloud_jobs as jobs
from tools.image_archive import review as R
from tools.image_archive.archive import IntegrityError
from tools.image_archive.tests.fake_drive import FakeDrive
from tools.image_archive.unreviewed import backup, candidates

WORKFLOW = Path(__file__).resolve().parents[3] / ".github/workflows/watch-align-onedrive-archive.yml"
MODELS = ["124060", "126610LN", "126710BLNR"]


def jpeg(seed, size=(320, 240)):
    im = Image.new("RGB", size, ((seed * 37) % 256, (seed * 91) % 256, (seed * 13) % 256))
    out = io.BytesIO()
    im.save(out, "JPEG", quality=90)
    return out.getvalue()


def catalogue(photos):
    """sha256 -> catalogue associations, as unreviewed.provenance would build them from the repository."""
    return {sha256(p).hexdigest(): [{"dataset": "test", "model": MODELS[i % 3], "physical_watch_id": f"watch-{i // 2}",
                                     "provenance": "dealer", "source_url": f"https://dealer.test/{i}.jpg",
                                     "source_name": "dealer"}]
            for i, p in enumerate(photos)}


@pytest.fixture
def originals(tmp_path):
    folder = tmp_path / "recovered"
    folder.mkdir()
    photos = [jpeg(i) for i in range(45)]
    for i, p in enumerate(photos):
        (folder / f"{i:03d}.jpg").write_bytes(p)
    (folder / "duplicate-of-7.jpg").write_bytes(photos[7])       # same bytes twice
    (folder / "notes.txt").write_text("not a photo")
    repo = tmp_path / "repo"
    (repo / "archive").mkdir(parents=True)
    cat = catalogue(photos)
    with patch("tools.image_archive.unreviewed.provenance", return_value=cat), \
         patch.object(jobs, "provenance", return_value=cat):
        yield repo, folder, photos


def records(repo, folder):
    return candidates(repo, [folder])


# ------------------------------------------------------------------------------- transfer integrity

def test_duplicates_are_archived_once(originals):
    repo, folder, photos = originals
    recs = records(repo, folder)
    assert len(recs) == 45                      # duplicate bytes collapse to one SHA-256; text ignored
    drive = FakeDrive()
    backup(drive, recs)
    again = jobs.incremental_backup(drive, recs)
    assert again["already_archived"] == 45 and again["uploaded"] == 0
    assert len(jobs.remote_objects(drive)) == 45 == len(jobs.remote_index(drive))


def test_interrupted_upload_resumes_without_loss_or_duplicates(originals):
    repo, folder, photos = originals
    recs = records(repo, folder)
    drive = FakeDrive()
    drive.fail_put_after = 27                   # network lost after 27 writes (20 photos + manifest + 6 photos)
    with pytest.raises(ConnectionError):
        backup(drive, recs)
    state = jobs.reconcile(drive, repo)
    assert state["indexed_originals"] == 20 and state["orphans"] == 6 and state["missing_objects"] == 0
    drive.fail_put_after = None
    result = jobs.incremental_backup(drive, recs)
    assert result["already_archived"] == 20 and result["uploaded"] == 19 and result["indexed_existing"] == 6
    final = jobs.reconcile(drive, repo)
    assert final["orphans"] == 0 and final["indexed_originals"] == final["stored_originals"] == 45
    assert jobs.verify_remote(drive)["all_indexed_verified"]
    for r in recs:                              # every stored object is the exact original
        assert drive.files[r["key"]] == (folder / Path(r["local_location"][0]).name).read_bytes()


def test_lost_manifests_are_rebuilt_from_verified_objects(originals):
    repo, folder, photos = originals
    drive = FakeDrive()
    backup(drive, records(repo, folder)[:5])
    for key in [k for k in drive.files if "/manifests/" in k]:
        del drive.files[key]                    # simulate manifests that were never written
    assert jobs.reconcile(drive, repo)["orphans"] == 5
    repaired = jobs.reconcile(drive, repo, repair=True)
    assert repaired["repaired"] == 5 and repaired["corrupt_orphans"] == []
    index = jobs.remote_index(drive)
    assert len(index) == 5
    item = next(iter(index.values()))
    assert item["associations"][0]["source_url"].startswith("https://dealer.test/")
    assert item["calibration_approved"] is False and item["review_status"] == "unreviewed"


def test_corrupt_orphan_is_reported_never_listed_or_deleted(originals):
    repo, folder, photos = originals
    drive = FakeDrive()
    backup(drive, records(repo, folder)[:3])
    for key in [k for k in drive.files if "/manifests/" in k]:
        del drive.files[key]
    bad = sorted(k for k in drive.files if "/objects/" in k)[0]
    drive.corrupt.add(bad)
    result = jobs.reconcile(drive, repo, repair=True)
    assert result["repaired"] == 2 and len(result["corrupt_orphans"]) == 1
    assert bad in drive.files


def test_corrupted_remote_original_fails_verification(originals):
    repo, folder, photos = originals
    drive = FakeDrive()
    backup(drive, records(repo, folder)[:4])
    drive.corrupt.add(sorted(k for k in drive.files if "/objects/" in k)[2])
    with pytest.raises(ValueError, match="verification failed"):
        jobs.verify_remote(drive)


def test_different_file_under_same_name_is_never_overwritten(originals):
    repo, folder, photos = originals
    recs = records(repo, folder)[:1]
    drive = FakeDrive()
    drive.files[recs[0]["key"]] = b"\xff\xd8\xffdifferent and longer bytes"
    with pytest.raises(ValueError, match="differs in size"):
        jobs.incremental_backup(drive, recs)
    assert drive.files[recs[0]["key"]] == b"\xff\xd8\xffdifferent and longer bytes"
    with pytest.raises(IntegrityError):
        FakeDrive.put(drive, recs[0]["key"], photos[0])


def test_foreign_object_names_fail_closed():
    drive = FakeDrive()
    drive.files["unreviewed/objects/ab/../../evil.jpg"] = b"x"
    with pytest.raises(ValueError):
        jobs.remote_objects(drive)


# ------------------------------------------------------------------------------- requests and credentials

def write_request(repo, name, doc):
    path = repo / "archive" / "requests" / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(doc))
    return path


@pytest.mark.parametrize("name,doc,match", [
    (".hidden;rm.json", {"task": "verify"}, "file name"),
    ("x.json", {"task": "delete_everything"}, "task must be"),
    ("x.json", {"task": "verify", "options": {"overwrite": True}}, "unsupported options"),
    ("x.json", {"task": "recover", "options": {"sources": []}}, "confirm_no_other_transfer"),
    ("x.json", {"task": "recover", "options": {"confirm_no_other_transfer": True,
                                               "sources": [{"run_id": "1; rm -rf /", "artifact": "a"}]}}, "digits"),
    ("x.json", {"task": "review_batch", "options": {"size": 500}}, "1..60"),
])
def test_unsafe_requests_are_refused(tmp_path, name, doc, match):
    path = tmp_path / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(doc))
    with pytest.raises(ValueError, match=match):
        jobs.parse_request(path)


def run(repo, *paths, env=None):
    with patch.dict("os.environ", env or {}, clear=True):
        args = type("A", (), {"repo": repo, "results": None, "scan": [], "local_copy": None,
                              "request": list(paths)})
        return jobs.run_requests(args)


def test_unconfigured_environment_never_contacts_microsoft(tmp_path):
    repo = tmp_path
    req = write_request(repo, "verify-1.json", {"task": "verify"})
    with patch.object(jobs, "drive_from_protected_environment", side_effect=AssertionError("connected")):
        report = run(repo, req)
    assert report[0]["status"] == "not_configured"
    assert run(repo, req)[0]["status"] == "already_done"      # a request runs once


def test_expired_consent_is_a_reported_failure_without_secrets(tmp_path):
    repo = tmp_path
    req = write_request(repo, "verify-2.json", {"task": "verify"})
    env = {"ONEDRIVE_CLIENT_ID": "id", "ONEDRIVE_MSAL_CACHE": "{refresh-token-secret}",
           "ONEDRIVE_CACHE_WRITE_TOKEN": "github_pat_secret", "CI": "true", "GITHUB_REPOSITORY": "o/r"}
    def expired():
        raise RuntimeError("Microsoft consent expired or revoked; reauthorize")
    with patch.object(jobs, "drive_from_protected_environment", side_effect=expired), pytest.raises(SystemExit):
        run(repo, req, env=env)
    text = (repo / "archive/results/verify-2.json").read_text()
    assert "reauthorize" in text and "secret" not in text


def test_account_pin_must_be_a_digest():
    env = {"ONEDRIVE_CLIENT_ID": "id", "ONEDRIVE_MSAL_CACHE": "{}", "ONEDRIVE_CACHE_WRITE_TOKEN": "t",
           "GITHUB_REPOSITORY": "o/r", "CI": "true", "ONEDRIVE_ACCOUNT_SHA256": "owner@example.test"}
    with pytest.raises(RuntimeError, match="SHA-256"):
        jobs.drive_from_protected_environment(env)


def test_reauthorise_requires_the_owner_pin_and_saves_only_through_it():
    with pytest.raises(RuntimeError, match="ONEDRIVE_ACCOUNT_SHA256"):
        jobs.reauthorize({"ONEDRIVE_CLIENT_ID": "id", "ONEDRIVE_CACHE_WRITE_TOKEN": "t", "GITHUB_REPOSITORY": "o/r"})
    calls = {}
    def login(app, cache, save, display, expected_fingerprint):
        calls["pin"] = expected_fingerprint
        display("Open https://www.microsoft.com/link and enter: TESTCODE")
        save("new-cache")
    saved, shown = [], []
    env = {"ONEDRIVE_CLIENT_ID": "06a3bb10-a5d3-4dde-9654-1e61de7cd064", "ONEDRIVE_CACHE_WRITE_TOKEN": "t",
           "GITHUB_REPOSITORY": "o/r", "ONEDRIVE_ACCOUNT_SHA256": "a" * 64}
    report = jobs.reauthorize(env, device_login=login, writer=saved.append, display=shown.append)
    assert calls["pin"] == "a" * 64 and saved == ["new-cache"] and "TESTCODE" in shown[0]
    assert report["scope"] == "Files.ReadWrite.AppFolder"


def test_keepalive_reports_writer_token_expiry():
    class Drive:
        def _root(self): return "root"
    class Response:
        status_code = 200
        headers = {"github-authentication-token-expiration": "2026-11-10 12:00:00 UTC"}
    class Session:
        def get(self, url, headers, timeout):
            assert headers["Authorization"] == "Bearer pat"
            return Response()
    report = jobs.keepalive(Drive(), {"GITHUB_REPOSITORY": "o/r", "ONEDRIVE_CACHE_WRITE_TOKEN": "pat"}, Session())
    assert report["microsoft_connection"] == "renewed_and_saved"
    assert report["cache_writer_token"] == "valid" and "cache_writer_token_days_left" in report
    assert "pat" not in json.dumps(report)


# ------------------------------------------------------------------------------- visual review

@pytest.fixture
def archived(originals):
    repo, folder, photos = originals
    drive = FakeDrive()
    backup(drive, records(repo, folder))
    return repo, drive, photos


def test_review_batch_uploads_viewing_copies_and_never_touches_originals(archived):
    repo, drive, photos = archived
    before = {k: v for k, v in drive.files.items() if "/objects/" in k}
    first = jobs.create_review_batch(drive, repo, size=9)
    assert first["review_photos"] == 9
    batch_id = first["review_batch"]
    folder = f"review/batches/{batch_id}/"
    names = {k[len(folder):] for k in drive.files if k.startswith(folder)}
    assert {"batch.json", "decisions-template.json", "README.txt", "sheet-01.jpg"} <= names
    assert sum(n.startswith("photos/") for n in names) == 9
    assert {k: v for k, v in drive.files.items() if "/objects/" in k} == before
    index = json.loads((repo / f"archive/batches/{batch_id}.json").read_text())
    assert index["calibration_approved"] is False and len(index["photos"]) == 9
    second = jobs.create_review_batch(drive, repo, size=60)
    second_index = json.loads((repo / f"archive/batches/{second['review_batch']}.json").read_text())
    assert not {p["sha256"] for p in index["photos"]} & {p["sha256"] for p in second_index["photos"]}
    assert second["review_photos"] == 36
    assert jobs.create_review_batch(drive, repo)["review_photos"] == 0


def test_review_batch_refuses_a_corrupt_original(archived):
    repo, drive, photos = archived
    drive.corrupt.add(sorted(k for k in drive.files if "/objects/" in k)[0])
    with pytest.raises(R.ReviewError, match="SHA-256"):
        jobs.create_review_batch(drive, repo, size=60)
    assert not (repo / "archive/batches").exists() or not list((repo / "archive/batches").glob("*.json"))


def decide(repo, batch, reviewer, when, choose, n=1):
    template = R.decisions_template(batch)
    template.update(reviewer=reviewer, reviewed_at=when)
    for p in template["photos"]:
        choose(p)
    path = repo / "archive/decisions" / f"{batch['batch_id']}-{reviewer}-{n}.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(template))
    return template


def approve_all_but_3_and_9(p):
    p["decision"] = "approved"
    p["view"] = "face_on"
    for h, m in p["markers"].items():
        m["decision"] = "excluded" if h == "9" else "approved"


def test_only_approved_photos_and_markers_reach_calibration(archived):
    repo, drive, photos = archived
    batch_id = jobs.create_review_batch(drive, repo, size=6)["review_batch"]
    batch = json.loads((repo / f"archive/batches/{batch_id}.json").read_text())
    excluded = batch["photos"][0]["sha256"]
    def chatgpt(p):
        if p["sha256"] == excluded:
            p["decision"] = "excluded"
            p["reasons"] = ["oblique, glare over 4-5"]
        else:
            approve_all_but_3_and_9(p)
    decide(repo, batch, "chatgpt", "2026-10-23T10:00:00Z", chatgpt)
    result = jobs.ingest_decisions(repo, drive)
    assert result["approved_photos"] == 5 and result["withheld"]["excluded"] == 1
    evidence = json.loads((repo / "archive/approved-evidence.json").read_text())
    assert excluded not in {p["sha256"] for p in evidence["photos"]}
    for p in evidence["photos"]:
        assert 9 not in p["approved_hours"] and 9 in p["excluded_hours"]
        if p["family"] != "submariner_124060":
            assert 3 not in p["approved_hours"] + p["excluded_hours"]
    # The owner overrides an AI approval, and the excluded photo is still archived.
    target = next(p["sha256"] for p in evidence["photos"])
    def owner(p):
        if p["sha256"] == target:
            p["decision"] = "excluded"
        else:
            approve_all_but_3_and_9(p)
    decide(repo, batch, "owner", "2026-10-22T09:00:00Z", owner)   # earlier time still wins: higher authority
    jobs.ingest_decisions(repo, drive)
    evidence = json.loads((repo / "archive/approved-evidence.json").read_text())
    assert target not in {p["sha256"] for p in evidence["photos"]}
    assert any(k.endswith(f"{excluded}.jpg") for k in drive.files)
    assert any(k.startswith("review/decisions/") for k in drive.files)


def test_equal_rank_disagreement_is_withheld(archived):
    repo, drive, photos = archived
    batch_id = jobs.create_review_batch(drive, repo, size=1)["review_batch"]
    batch = json.loads((repo / f"archive/batches/{batch_id}.json").read_text())
    decide(repo, batch, "chatgpt", "2026-10-23T10:00:00Z", approve_all_but_3_and_9, 1)
    decide(repo, batch, "claude", "2026-10-23T10:00:00Z", lambda p: p.update(decision="excluded"), 2)
    assert jobs.ingest_decisions(repo)["withheld"]["conflict"] == 1


@pytest.mark.parametrize("mutate,match", [
    (lambda d: d.update(reviewer="anyone"), "reviewer"),
    (lambda d: d.update(batch_id="b20261023T000000Z-abcdef"), "not a committed"),
    (lambda d: d["photos"][0].update(sha256="0" * 64), "not in batch"),
    (lambda d: d["photos"].append(dict(d["photos"][0])), "decided twice"),
    (lambda d: d["photos"][0].update(decision="maybe"), "decision must be"),
    (lambda d: d.update(reviewed_at="yesterday"), "ISO-8601"),
    (lambda d: d["photos"][0].update(decision="approved", markers={}), "no marker approved"),
    (lambda d: d["photos"][0]["markers"].update({"13": {"decision": "approved"}}), "no physical 13"),
])
def test_malformed_decisions_are_rejected(archived, mutate, match):
    repo, drive, photos = archived
    batch_id = jobs.create_review_batch(drive, repo, size=2)["review_batch"]
    batch = json.loads((repo / f"archive/batches/{batch_id}.json").read_text())
    doc = R.decisions_template(batch)
    doc["reviewed_at"] = "2026-10-23T10:00:00Z"
    mutate(doc)
    with pytest.raises(R.ReviewError, match=match):
        R.validate_decision(doc, {batch_id: batch})


def test_date_models_have_no_three_oclock_marker():
    assert 3 not in R.FAMILY_HOURS["submariner_126610"] and 3 not in R.FAMILY_HOURS["gmt_126710"]
    assert len(R.FAMILY_HOURS["submariner_124060"]) == 12
    assert R.family("126710BLNR") == "gmt_126710" and R.family("124060") == "submariner_124060"
    assert R.family("Daytona 116500") is None


def test_request_round_trip_in_ci(archived):
    repo, drive, photos = archived
    req = write_request(repo, "batch-1.json", {"task": "review_batch", "options": {"size": 3}})
    env = {"ONEDRIVE_CLIENT_ID": "id", "ONEDRIVE_MSAL_CACHE": "{}", "ONEDRIVE_CACHE_WRITE_TOKEN": "t"}
    with patch.object(jobs, "drive_from_protected_environment", return_value=drive):
        report = run(repo, req, env=env)
    assert report[0]["status"] == "done" and report[0]["result"]["review_photos"] == 3
    result = json.loads((repo / "archive/results/batch-1.json").read_text())
    assert result["result"]["review_batch"].startswith("b")


# ------------------------------------------------------------------------------- workflow security

def test_workflow_keeps_secrets_inside_the_protected_environment():
    doc = yaml.safe_load(WORKFLOW.read_text())
    triggers = doc.get(True) or doc.get("on")
    assert "pull_request_target" not in triggers and "issue_comment" not in triggers
    assert triggers["push"]["branches"] == ["experiment/archive-first-onedrive-20261023"]
    assert doc["permissions"] == {"contents": "read"}
    for name, job in doc["jobs"].items():
        text = json.dumps(job)
        uses_secrets = "secrets." in text
        assert not uses_secrets or job.get("environment") == "onedrive-archive", name
        if job.get("environment"):
            assert "github.event_name == 'pull_request'" not in str(job.get("if"))
            assert "pull_request" not in str(job.get("if"))
    automation = doc["jobs"]["automation"]
    commit = next(s for s in automation["steps"] if s.get("name", "").startswith("Commit results"))
    assert "secrets." not in json.dumps(commit) and "refusing to commit image" in commit["run"]
    assert all("secrets." not in json.dumps(s) for s in automation["steps"] if s.get("name") != "Run requests")


def test_setup_hands_over_the_cache_without_printing_it(tmp_path, capsys):
    from tools.image_archive import setup_ci
    from tools.image_archive.auth import account_fingerprint
    cache = tmp_path / "msal-cache.json"
    cache.write_text('{"RefreshToken": {"x": {"secret": "refresh-token-value"}}}')
    calls = []
    def run(cmd, input=None, text=None, capture_output=None):
        calls.append((cmd, input))
        return type("R", (), {"returncode": 0, "stderr": ""})()
    with patch.object(setup_ci, "pin_from_cache", return_value=account_fingerprint("owner@example.test")):
        setup_ci.main(["--cache", str(cache), "--apply"], run=run)
    out = capsys.readouterr().out
    assert "refresh-token-value" not in out and "owner@example.test" not in out
    secret = [c for c in calls if c[0][:3] == ["gh", "secret", "set"]]
    assert len(secret) == 1 and "refresh-token-value" in secret[0][1]
    assert all("refresh-token-value" not in " ".join(c[0]) for c in calls)
    assert len(calls) == 3
    assert all(c[0][:3] in (["gh", "variable", "set"], ["gh", "secret", "set"]) for c in calls), (
        "handover must never overwrite the owner's reviewer/branch protection rules"
    )
    assert not any("api" in c[0] or "-X" in c[0] for c in calls)
    assert all("--env" in c[0] and "onedrive-archive" in c[0] for c in calls)


def test_documented_examples_are_valid():
    examples = Path(__file__).resolve().parents[3] / "docs/image-archive/examples"
    for path in examples.glob("*.json"):
        if path.name == "decision.json":
            doc = json.loads(path.read_text())
            photo = doc["photos"][0]
            batch = R.batch_document(doc["batch_id"], [{"id": photo["id"], "sha256": photo["sha256"], "key": "k",
                                                        "format": "jpg", "size": 1, "family": "gmt_126710",
                                                        "models": [], "physical_watch_ids": [], "source_urls": [],
                                                        "provenance": [], "identity_conflict": False}])
            R.validate_decision(doc, {doc["batch_id"]: batch})
        else:
            jobs.parse_request(path)
