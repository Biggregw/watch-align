"""Suitable Photos collection: exact copies of reviewed suitable originals, offline on an in-memory OneDrive."""
from hashlib import sha256
import json
from pathlib import Path
from unittest.mock import patch

import pytest

from tools.image_archive import cloud_jobs as jobs
from tools.image_archive import suitable as S
from tools.image_archive.archive import IntegrityError
from tools.image_archive.tests.fake_drive import FakeDrive
from tools.image_archive.unreviewed import backup

REPO_ROOT = Path(__file__).resolve().parents[3]
CATALOGUE = ["124060", "126610LN", "126710BLNR", "124060", ""]   # the last photo has no catalogue model


def jpeg(i):
    return b"\xff\xd8\xff" + f"original photograph {i} ".encode() * (20 + i)


@pytest.fixture
def archive(tmp_path):
    """Five archived originals (verified index) and a repository with archive/selections/."""
    photos = [jpeg(i) for i in range(5)]
    records = []
    for i, raw in enumerate(photos):
        d = sha256(raw).hexdigest()
        records.append({"sha256": d, "key": f"unreviewed/objects/{d[:2]}/{d}.jpg", "size": len(raw), "format": "jpg",
                        "review_status": "unreviewed", "calibration_approved": False,
                        "physical_watch_identity_conflict": False,
                        "associations": ([{"model": CATALOGUE[i], "physical_watch_id": f"w{i}", "provenance": "dealer",
                                           "source_url": f"https://dealer.test/{i}.jpg"}] if CATALOGUE[i] else []),
                        "local_location": None})
    drive = FakeDrive()
    with patch("tools.image_archive.unreviewed.read_original", side_effect=lambda loc: photos[loc]):
        for i, r in enumerate(records):
            r["local_location"] = i
        backup(drive, records)
    repo = tmp_path / "repo"
    (repo / "archive" / "selections").mkdir(parents=True)
    originals = {k: v for k, v in drive.files.items() if k.startswith("unreviewed/")}
    return drive, repo, photos, [r["sha256"] for r in records], originals


def selection(repo, sid, entries, reviewer="owner"):
    doc = {"schema": S.SCHEMA, "selection_id": sid, "reviewer": reviewer, "reviewed_at": "2026-10-24T09:00:00Z",
           "photos": entries}
    path = repo / "archive" / "selections" / f"{sid}.json"
    path.write_text(json.dumps(doc, indent=1))
    return f"archive/selections/{sid}.json"


def entry(digest, model, suitability="suitable", reasons=(), comment=""):
    return {"sha256": digest, "model": model, "suitability": suitability, "reasons": list(reasons), "comment": comment}


def standard(repo, h):
    return selection(repo, "sel-20261024-first", [
        entry(h[0], "submariner_124060"),
        entry(h[1], "submariner_126610"),
        entry(h[2], "gmt_126710", "not_suitable", ["oblique"]),
        entry(h[3], "submariner_124060", "uncertain"),
        entry(h[4], "unknown"),
    ])


def copies(drive):
    return {k: v for k, v in drive.files.items() if k.startswith("Suitable Photos/") and "/_audit/" not in k}


def test_only_explicitly_suitable_originals_are_copied_exactly(archive):
    drive, repo, photos, h, originals = archive
    audit = S.apply(drive, repo, standard(repo, h))
    assert audit["status"] == "complete"
    got = copies(drive)
    assert set(got) == {f"Suitable Photos/Submariner 124060/{h[0]}.jpg",
                        f"Suitable Photos/Submariner Date 126610/{h[1]}.jpg",
                        f"Suitable Photos/Unknown model/{h[4]}.jpg"}
    for key, data in got.items():
        assert sha256(data).hexdigest() == Path(key).stem      # exact original bytes
    assert {k: v for k, v in drive.files.items() if k.startswith("unreviewed/")} == originals
    s = audit["summary"]
    assert (s["copied"], s["excluded"], s["held"], s["failed"]) == (3, 2, 0, 0)
    # the audit trail in OneDrive records every entry, exclusions included, and the exact selection applied
    runs = [k for k in drive.files if k.startswith("Suitable Photos/_audit/sel-20261024-first/run-")]
    assert len(runs) == 1
    stored = json.loads(drive.files[runs[0]])
    assert {p["sha256"]: p["action"] for p in stored["photos"]}[h[2]] == "excluded"
    assert drive.files["Suitable Photos/_audit/sel-20261024-first/selection.json"] == \
        (repo / "archive/selections/sel-20261024-first.json").read_bytes()


def test_rerun_is_a_verified_no_op(archive):
    drive, repo, photos, h, originals = archive
    path = standard(repo, h)
    S.apply(drive, repo, path)
    before = copies(drive)
    puts = drive.puts
    second = S.apply(drive, repo, path)
    assert second["summary"]["already_present"] == 3 and second["summary"]["copied"] == 0
    assert copies(drive) == before
    assert drive.puts == puts + 1        # only the new run audit is written


def test_interrupted_run_is_completed_by_retrying(archive):
    drive, repo, photos, h, originals = archive
    path = standard(repo, h)
    drive.fail_put_after = drive.puts + 2      # selection.json + one copy, then the connection drops
    first = S.apply(drive, repo, path)
    assert first["status"] == "incomplete" and first["summary"]["copied"] == 1 and first["summary"]["failed"] == 2
    assert first["audit_written"] is False      # OneDrive unreachable: the repository result keeps the audit
    drive.fail_put_after = None
    second = S.apply(drive, repo, path)
    assert second["status"] == "complete"
    assert (second["summary"]["copied"], second["summary"]["already_present"]) == (2, 1)
    assert len(copies(drive)) == 3
    assert {k: v for k, v in drive.files.items() if k.startswith("unreviewed/")} == originals


def test_corrupt_source_is_never_copied_and_others_continue(archive):
    drive, repo, photos, h, originals = archive
    drive.corrupt.add(f"unreviewed/objects/{h[0][:2]}/{h[0]}.jpg")
    audit = S.apply(drive, repo, standard(repo, h))
    rec = {p["sha256"]: p for p in audit["photos"]}
    assert rec[h[0]]["result"] == "failed" and "source original" in rec[h[0]]["error"]
    assert not any(h[0] in k for k in copies(drive))
    assert audit["summary"]["copied"] == 2 and audit["status"] == "incomplete"


def test_corrupt_destination_read_back_is_reported(archive):
    drive, repo, photos, h, originals = archive
    drive.corrupt.add(f"Suitable Photos/Submariner 124060/{h[0]}.jpg")
    audit = S.apply(drive, repo, standard(repo, h))
    rec = {p["sha256"]: p for p in audit["photos"]}
    assert rec[h[0]]["result"] == "failed" and "destination copy" in rec[h[0]]["error"]
    assert audit["status"] == "incomplete"


def test_different_file_at_destination_is_never_overwritten(archive):
    drive, repo, photos, h, originals = archive
    key = f"Suitable Photos/Submariner 124060/{h[0]}.jpg"
    drive.files[key] = b"\xff\xd8\xffsomething else"
    audit = S.apply(drive, repo, standard(repo, h))
    rec = {p["sha256"]: p for p in audit["photos"]}
    assert rec[h[0]]["result"] == "failed" and "IntegrityError" in rec[h[0]]["error"]
    assert drive.files[key] == b"\xff\xd8\xffsomething else"


def test_duplicate_entries_are_merged_and_contradictions_refused(archive):
    drive, repo, photos, h, originals = archive
    path = selection(repo, "sel-20261024-dupes", [entry(h[0], "submariner_124060"), entry(h[0], "submariner_124060")])
    audit = S.apply(drive, repo, path)
    assert audit["duplicate_entries"] == 1 and audit["summary"]["copied"] == 1
    bad = selection(repo, "sel-20261024-contra", [entry(h[1], "submariner_126610"),
                                                  entry(h[1], "submariner_126610", "not_suitable", ["blurred"])])
    puts = drive.puts
    with pytest.raises(S.SelectionError, match="listed twice"):
        S.apply(drive, repo, bad)
    assert drive.puts == puts


def test_unknown_or_conflicting_models_are_never_invented(archive):
    drive, repo, photos, h, originals = archive
    path = selection(repo, "sel-20261024-models", [
        entry(h[4], "unknown"),                      # no catalogue model: filed as Unknown model
        entry(h[3], "unknown"),                      # catalogue says 124060, reviewer said unknown: kept unknown
        entry(h[2], "submariner_124060"),            # catalogue says GMT: held, not copied
        entry("f" * 64, "gmt_126710"),               # not in the archive: held
    ])
    audit = S.apply(drive, repo, path)
    rec = {p["sha256"]: p for p in audit["photos"]}
    assert rec[h[4]]["destination"].startswith("Suitable Photos/Unknown model/")
    assert rec[h[3]]["destination"].startswith("Suitable Photos/Unknown model/")
    assert rec[h[3]]["catalogue_families"] == ["submariner_124060"]
    assert rec[h[2]]["action"] == "held" and "conflicts" in rec[h[2]]["reason"]
    assert rec["f" * 64]["action"] == "held" and "not in the verified archive" in rec["f" * 64]["reason"]
    assert audit["status"] == "complete" and audit["summary"]["held"] == 2


def test_a_photo_is_never_filed_under_two_models(archive):
    drive, repo, photos, h, originals = archive
    S.apply(drive, repo, selection(repo, "sel-20261024-a", [entry(h[4], "unknown")]))
    audit = S.apply(drive, repo, selection(repo, "sel-20261024-b", [entry(h[4], "gmt_126710")]))
    rec = audit["photos"][0]
    assert rec["action"] == "held" and "another model folder" in rec["reason"]
    assert [k for k in copies(drive) if h[4] in k] == [f"Suitable Photos/Unknown model/{h[4]}.jpg"]


def test_later_exclusion_never_deletes_an_earlier_copy(archive):
    drive, repo, photos, h, originals = archive
    S.apply(drive, repo, selection(repo, "sel-20261024-in", [entry(h[0], "submariner_124060")]))
    audit = S.apply(drive, repo, selection(repo, "sel-20261024-out",
                                           [entry(h[0], "submariner_124060", "not_suitable", ["heavy_glare"])]))
    assert "kept" in audit["photos"][0]["note"]
    assert f"Suitable Photos/Submariner 124060/{h[0]}.jpg" in drive.files


def test_selection_ids_are_immutable(archive):
    drive, repo, photos, h, originals = archive
    path = selection(repo, "sel-20261024-fixed", [entry(h[0], "submariner_124060")])
    S.apply(drive, repo, path)
    selection(repo, "sel-20261024-fixed", [entry(h[1], "submariner_126610")])
    with pytest.raises(IntegrityError):
        S.apply(drive, repo, path)
    assert not any(h[1] in k for k in copies(drive))


def test_dry_run_writes_nothing(archive):
    drive, repo, photos, h, originals = archive
    before = dict(drive.files)
    audit = S.apply(drive, repo, standard(repo, h), dry_run=True)
    assert audit["status"] == "dry_run" and audit["summary"]["would_copy"] == 3
    assert drive.files == before


@pytest.mark.parametrize("mutate,match", [
    (lambda e: e.update(markers={"3": "covered"}), "hour-marker"),
    (lambda e: e.update(suitability="not_suitable", reasons=["hands_cover_markers"]), "covered or unclear hour markers"),
    (lambda e: e.update(reasons=["blurred"]), "suitable photo has no"),
    (lambda e: e.update(suitability="not_suitable", reasons=[]), "at least one"),
    (lambda e: e.update(suitability="not_suitable", reasons=["other"]), "needs a comment"),
    (lambda e: e.update(model="rolex"), "model must be"),
    (lambda e: e.update(model=None), "model must be"),
    (lambda e: e.update(sha256="ABC"), "sha256"),
    (lambda e: e.update(suitability="maybe"), "suitability must be"),
])
def test_malformed_entries_are_refused(mutate, match):
    e = entry("a" * 64, "submariner_124060")
    mutate(e)
    doc = {"schema": S.SCHEMA, "selection_id": "sel-20261024-x", "reviewer": "chatgpt",
           "reviewed_at": "2026-10-24T09:00:00Z", "photos": [e]}
    with pytest.raises(S.SelectionError, match=match):
        S.validate_selection(doc)


def test_covered_markers_do_not_make_a_clear_photo_unsuitable():
    doc = {"schema": S.SCHEMA, "selection_id": "sel-20261024-hands", "reviewer": "owner",
           "reviewed_at": "2026-10-24T09:00:00Z",
           "photos": [entry("b" * 64, "gmt_126710", comment="face-on and sharp; hands cover 10 and 2")]}
    chosen, _ = S.validate_selection(doc)
    assert chosen["b" * 64]["suitability"] == "suitable"


def test_selection_must_live_in_the_selection_folder(archive, tmp_path):
    drive, repo, photos, h, originals = archive
    outside = tmp_path / "sel-20261024-evil.json"
    outside.write_text("{}")
    with pytest.raises(S.SelectionError, match="archive/selections"):
        S.load_selection(str(outside), repo)
    with pytest.raises(S.SelectionError, match="archive/selections"):
        S.load_selection("archive/selections/../../sel-20261024-evil.json", repo)
    selection(repo, "sel-20261024-named", [entry(h[0], "submariner_124060")])
    (repo / "archive/selections/sel-20261024-renamed.json").write_text(
        (repo / "archive/selections/sel-20261024-named.json").read_text())
    with pytest.raises(S.SelectionError, match="file name"):
        S.load_selection("archive/selections/sel-20261024-renamed.json", repo)


@pytest.mark.parametrize("options,match", [
    ({}, "options.selection"),
    ({"selection": "../secrets.json"}, "options.selection"),
    ({"selection": "archive/selections/sel-20261024-a.json", "dry_run": "yes"}, "dry_run"),
    ({"selection": "archive/selections/sel-20261024-a.json", "overwrite": True}, "unsupported"),
])
def test_unsafe_copy_requests_are_refused(tmp_path, options, match):
    path = tmp_path / "copy.json"
    path.write_text(json.dumps({"task": "suitable_copy", "options": options}))
    with pytest.raises(ValueError, match=match):
        jobs.parse_request(path)


def run_request(repo, name, options, drive):
    req = repo / "archive/requests" / name
    req.parent.mkdir(parents=True, exist_ok=True)
    req.write_text(json.dumps({"task": "suitable_copy", "options": options}))
    env = {"ONEDRIVE_CLIENT_ID": "id", "ONEDRIVE_MSAL_CACHE": '{"rt": "REFRESH-TOKEN-VALUE"}',
           "ONEDRIVE_CACHE_WRITE_TOKEN": "WRITER-TOKEN-VALUE"}
    args = type("A", (), {"repo": repo, "results": None, "scan": [], "local_copy": None, "request": [req]})
    with patch.dict("os.environ", env, clear=True), \
         patch.object(jobs, "drive_from_protected_environment", return_value=drive):
        try:
            jobs.run_requests(args)
            code = 0
        except SystemExit as exc:
            code = exc.code
    return code, json.loads((repo / "archive/results" / name).read_text())


def test_protected_request_round_trip_and_visible_partial_failure(archive):
    drive, repo, photos, h, originals = archive
    path = standard(repo, h)
    drive.corrupt.add(f"unreviewed/objects/{h[1][:2]}/{h[1]}.jpg")
    code, result = run_request(repo, "suitable-first.json", {"selection": path}, drive)
    assert code == 1 and result["status"] == "incomplete"     # the Actions run turns red
    drive.corrupt.clear()
    code, result = run_request(repo, "suitable-first-retry1.json", {"selection": path}, drive)
    assert code == 0 and result["status"] == "done" and result["result"]["status"] == "complete"
    assert result["result"]["summary"]["copied"] == 1 and result["result"]["summary"]["already_present"] == 2
    text = json.dumps(result)
    assert "REFRESH-TOKEN-VALUE" not in text and "WRITER-TOKEN-VALUE" not in text


def test_committed_selections_validate():
    for path in sorted((REPO_ROOT / "archive" / "selections").glob("*.json")):
        doc, _ = S.load_selection(path, REPO_ROOT)
        S.validate_selection(doc, name=path.name)


def test_real_graph_client_encodes_folder_names_with_spaces():
    from tools.image_archive.onedrive import OneDriveBackend
    class Response:
        def __init__(self, status, doc=None):
            self.status_code, self.doc, self.headers, self.content = status, doc or {}, {}, b""
        def json(self): return self.doc
    class Session:
        def __init__(self): self.urls, self.docs = [], [Response(200, {"id": "root"}), Response(404)]
        def request(self, method, url, **kw):
            self.urls.append(url)
            return self.docs.pop(0)
    session = Session()
    drive = OneDriveBackend(lambda: "token", authorized=True, session=session, sleep=lambda _: None)
    assert drive.list_children("Suitable Photos/GMT-Master II 126710") == []
    assert session.urls[-1].endswith(":/WatchAlign/Suitable%20Photos/GMT-Master%20II%20126710:/children")


def test_documented_selection_example_is_valid():
    path = REPO_ROOT / "docs/image-archive/examples/selections/sel-20261024-example.json"
    chosen, duplicates = S.validate_selection(json.loads(path.read_text()), name=path.name)
    assert len(chosen) == 4 and duplicates == 0
