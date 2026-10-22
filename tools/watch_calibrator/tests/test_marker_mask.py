"""Per-marker eligibility masks in the watch-family calibrator: enforced at consumption, fail closed."""
import csv
import hashlib
import json
import sys
from pathlib import Path

import pytest
from PIL import Image

HERE = Path(__file__).resolve().parents[1]
REPO = HERE.parents[1]
for p in (str(HERE), str(REPO)):
    if p not in sys.path:
        sys.path.insert(0, p)
import genuine_envelope  # noqa: E402
import marker_mask  # noqa: E402
import marker_population  # noqa: E402
import production_measure as pm  # noqa: E402
from tools.image_archive import eligibility as el  # noqa: E402
from tools.image_archive.faceon import REQUIRED  # noqa: E402

LEGACY = json.loads((REPO / "calibration/models/124060.json").read_text())
METRICS = [s["metric"] for s in LEGACY["calibration_metrics"]]
MASKED = dict(LEGACY, marker_eligibility={"policy": el.MASK_POLICY_VERSION})
MODEL = "submariner_124060"


def statuses(hands=(), model=MODEL):
    req = list(REQUIRED[model])
    s = {"required": req, "present": req, "credible": req, "reliable": req, "cover": {h: 1.0 for h in req},
         "withheld": {}, "hand_confirmed": list(hands), "soft": {}}
    return el.marker_statuses(s, model)


def write(path, rows, fields=None):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=fields or list(rows[0]))
        w.writeheader()
        w.writerows(rows)


def read(path):
    with path.open(newline="", encoding="utf-8") as fh:
        return list(csv.DictReader(fh))


@pytest.fixture
def acq(tmp_path):
    """Acquisition root: photos of watches a, a, b (2 obstructed in a/0), c; masks; raw CalibMeasure rows."""
    root = tmp_path / "acq"
    plan = [("a", 0, (2,)), ("a", 1, ()), ("b", 0, ()), ("c", 0, (2, 6))]
    rows, masks, raw = [], [], []
    for i, (wid, n, hands) in enumerate(plan):
        rel = f"images/{wid}/{n}.png"
        (root / rel).parent.mkdir(parents=True, exist_ok=True)
        Image.new("RGB", (8, 8), (i * 40, 10, 10)).save(root / rel)
        digest = hashlib.sha256((root / rel).read_bytes()).hexdigest()
        rows.append({"sha256": digest, "physical_watch_id": wid, "class_label": "gen", "model": "124060",
                     "source_name": "S" + wid, "local_path": rel, "acquisition_status": "acquired"})
        masks.append(el.build(digest, "124060", statuses(hands), "face-on-v3", "face-on-v3/" + MODEL))
        raw.append(dict({"physical_watch_id": wid, "path": str(root / rel), "dial_source": "edge",
                         "dial_reproducible": "true", "pose_tilt_deg": "1.0"},
                        **{m: f"{0.1 * (i + 1) + k:.3f}" for k, m in enumerate(METRICS)}))
    write(root / "acquired_images.csv", rows)
    (root / marker_mask.MASKS_FILE).write_text("".join(json.dumps(m) + "\n" for m in masks))
    write(tmp_path / "raw.csv", raw)
    return root


def masked(tmp_path, acq):
    out = tmp_path / "photo.csv"
    summary = marker_mask.apply(MASKED, acq, tmp_path / "raw.csv", out, tmp_path / "excl.csv")
    return out, summary


# --- masked inputs ------------------------------------------------------------------------------------------------

def test_an_obstructed_2_never_reaches_round_statistics_but_its_other_markers_do(tmp_path, acq):
    out, summary = masked(tmp_path, acq)
    rows = {(r["physical_watch_id"], Path(r["path"]).name): r for r in read(out)}
    a0 = rows[("a", "0.png")]
    assert a0["round.ring_rho"] == "" and a0["round.spacing_rms_deg"] == ""             # depend on the 2
    assert a0["twelve.rotation_deg"] and a0["baton.3_9_line_offset_r"] and a0["axis.12_6_line_offset_r"]
    assert a0["mask_policy_version"] == el.MASK_POLICY_VERSION and "2" not in a0["eligible_marker_ids"].split()
    c0 = rows[("c", "0.png")]
    assert c0["axis.12_6_line_offset_r"] == "" and c0["baton.3_9_line_offset_r"]           # the 6 is excluded
    excl = read(tmp_path / "excl.csv")
    assert {(e["physical_watch_id"], e["metric"], e["statuses"]) for e in excl} >= {
        ("a", "round.ring_rho", "2:OBSTRUCTED"), ("c", "axis.12_6_line_offset_r", "6:OBSTRUCTED")}
    assert "value" not in excl[0]
    assert summary["values_excluded"] == len(excl) == 5
    # downstream: watch medians and the genuine envelope see only eligible values
    pm.summarise(out, METRICS, tmp_path / "w.csv", tmp_path / "r.csv", {}, MASKED)
    ring = {r["physical_watch_id"]: r for r in read(tmp_path / "w.csv") if r["metric"] == "round.ring_rho"}
    assert ring["a"]["photos"] == "1" and ring["a"]["median"] == "3.2" and "c" not in ring
    env = genuine_envelope._collect({"development": out}, METRICS, MASKED)
    assert {r["value"] for r in env["round.ring_rho"]} == {3.2, 3.3}


def test_several_photos_of_one_watch_are_one_independent_sample(tmp_path, acq):
    out, _ = masked(tmp_path, acq)
    pm.summarise(out, METRICS, tmp_path / "w.csv", tmp_path / "r.csv", {}, MASKED)
    rot = [r for r in read(tmp_path / "w.csv") if r["metric"] == "twelve.rotation_deg"]
    assert sorted((r["physical_watch_id"], r["photos"]) for r in rot) == [("a", "2"), ("b", "1"), ("c", "1")]


# --- fail closed -------------------------------------------------------------------------------------------------

def test_missing_masks_or_a_photo_without_a_mask_stop_the_run(tmp_path, acq):
    lines = (acq / marker_mask.MASKS_FILE).read_text().splitlines()
    (acq / marker_mask.MASKS_FILE).write_text("\n".join(lines[1:]) + "\n")
    with pytest.raises(el.MaskError, match="no eligibility mask"):
        masked(tmp_path, acq)
    (acq / marker_mask.MASKS_FILE).write_text("")
    with pytest.raises(el.MaskError):
        masked(tmp_path, acq)
    (acq / marker_mask.MASKS_FILE).unlink()
    with pytest.raises(el.MaskError):
        masked(tmp_path, acq)


def test_photo_and_mask_must_share_the_verified_sha256(tmp_path, acq):
    Image.new("RGB", (8, 8), "white").save(acq / "images/b/0.png")                       # bytes replaced
    with pytest.raises(el.MaskError, match="SHA-256"):
        masked(tmp_path, acq)


@pytest.mark.parametrize("edit", [
    lambda m: m["markers"]["2"].update(status="ELIGIBLE"),
    lambda m: m.update(eligible_marker_ids=list(range(1, 13))),
    lambda m: m.update(mask_policy_version="none"),
    lambda m: m.update(mask_digest="0" * 64),
    lambda m: m["markers"]["2"]["evidence"].update(hand_confirmed=False),
])
def test_a_tampered_mask_file_is_refused(tmp_path, acq, edit):
    masks = [json.loads(line) for line in (acq / marker_mask.MASKS_FILE).read_text().splitlines()]
    edit(masks[0])
    (acq / marker_mask.MASKS_FILE).write_text("".join(json.dumps(m) + "\n" for m in masks))
    with pytest.raises(el.MaskError):
        masked(tmp_path, acq)


def test_a_mask_for_another_layout_or_two_masks_for_one_photo_are_refused(tmp_path, acq):
    masks = [json.loads(line) for line in (acq / marker_mask.MASKS_FILE).read_text().splitlines()]
    date = el.build(masks[0]["sha256"], "126610", statuses(model="submariner_126610"), "face-on-v3")
    (acq / marker_mask.MASKS_FILE).write_text("".join(json.dumps(m) + "\n" for m in [date] + masks[1:]))
    with pytest.raises(el.MaskError, match="layout"):
        masked(tmp_path, acq)
    other = el.build(masks[0]["sha256"], "124060", statuses((4,)), "face-on-v3")
    (acq / marker_mask.MASKS_FILE).write_text("".join(json.dumps(m) + "\n" for m in masks + [other]))
    with pytest.raises(el.MaskError, match="two different masks"):
        masked(tmp_path, acq)


def test_unknown_policies_and_undeclared_metric_dependencies_fail_closed(tmp_path, acq):
    with pytest.raises(el.MaskError):
        marker_mask.required(dict(LEGACY, marker_eligibility={"policy": "v0"}))
    cfg = dict(MASKED, calibration_metrics=LEGACY["calibration_metrics"] + [{"metric": "new.metric"}])
    with pytest.raises(el.MaskError, match="dependency"):
        marker_mask.apply(cfg, acq, tmp_path / "raw.csv", tmp_path / "o.csv", tmp_path / "e.csv")
    with pytest.raises(el.MaskError):
        marker_mask.dependencies("marker.3.pos", "submariner_126610")                    # the date window


# --- bypass of the consumption check ------------------------------------------------------------------------------

def test_reinserting_an_excluded_value_or_stripping_mask_columns_is_caught_before_statistics(tmp_path, acq):
    out, _ = masked(tmp_path, acq)
    rows = read(out)
    fields = list(rows[0])
    rows[0]["round.ring_rho"] = "0.9"                                    # the excluded 2-dependent value, put back
    write(out, rows, fields)
    with pytest.raises(el.MaskError, match="ineligible"):
        pm.summarise(out, METRICS, tmp_path / "w.csv", tmp_path / "r.csv", {}, MASKED)
    with pytest.raises(el.MaskError, match="ineligible"):
        genuine_envelope._collect({"development": out}, METRICS, MASKED)
    rows[0]["round.ring_rho"] = ""
    rows[0]["eligible_marker_ids"] = " ".join(map(str, range(1, 13)))   # widening the list needs a valid digest
    rows[0]["mask_digest"] = "nope"
    write(out, rows, fields)
    with pytest.raises(el.MaskError):
        pm.summarise(out, METRICS, tmp_path / "w.csv", tmp_path / "r.csv", {}, MASKED)
    stripped = [{k: v for k, v in r.items() if k not in marker_mask.MASK_COLUMNS} for r in read(tmp_path / "raw.csv")]
    write(out, stripped)
    with pytest.raises(el.MaskError, match="unmasked"):
        pm.summarise(out, METRICS, tmp_path / "w.csv", tmp_path / "r.csv", {}, MASKED)


# --- the legacy, unmasked path ------------------------------------------------------------------------------------

def test_partially_eligible_photos_cannot_enter_the_unmasked_legacy_path(tmp_path, acq):
    with pytest.raises(el.MaskError, match="legacy"):
        pm.measure(LEGACY, acq, tmp_path / "split.csv", tmp_path / "geom", "development", "gen")
    out, _ = masked(tmp_path, acq)
    with pytest.raises(el.MaskError, match="legacy"):
        pm.summarise(out, METRICS, tmp_path / "w.csv", tmp_path / "r.csv", {}, LEGACY)
    with pytest.raises(el.MaskError, match="legacy"):
        genuine_envelope.build(LEGACY, {"development": out}, tmp_path / "none.csv")


def test_the_strict_fully_eligible_dataset_calibrates_exactly_as_before(tmp_path, acq):
    """Property 10: with every marker eligible, the masked pipeline gives the legacy result."""
    masks = [json.loads(line) for line in (acq / marker_mask.MASKS_FILE).read_text().splitlines()]
    full = [el.build(m["sha256"], "124060", statuses(), "face-on-v3", m["profile"]) for m in masks]
    (acq / marker_mask.MASKS_FILE).write_text("".join(json.dumps(m) + "\n" for m in full))
    out, summary = masked(tmp_path, acq)
    assert summary["values_excluded"] == 0
    split_csv = tmp_path / "split.csv"
    write(split_csv, [{"physical_watch_id": w, "partition": "development", "source_name": "S" + w} for w in "abc"])
    cfg = dict(calibration_policy={"genuine_envelope_min_watches": 2, "genuine_envelope_min_sources": 2})
    legacy = genuine_envelope.build(dict(LEGACY, **cfg), {"development": tmp_path / "raw.csv"}, split_csv)
    new = genuine_envelope.build(dict(MASKED, **cfg), {"development": out}, split_csv)
    assert new == legacy and legacy["state"] == "GENUINE_ENVELOPE_READY"
    pm.summarise(tmp_path / "raw.csv", METRICS, tmp_path / "w0.csv", tmp_path / "r0.csv", {}, LEGACY)
    pm.summarise(out, METRICS, tmp_path / "w1.csv", tmp_path / "r1.csv", {}, MASKED)
    assert read(tmp_path / "w0.csv") == read(tmp_path / "w1.csv")


def test_source_diversity_is_still_enforced_on_masked_values(tmp_path, acq):
    out, _ = masked(tmp_path, acq)
    split_csv = tmp_path / "split.csv"
    write(split_csv, [{"physical_watch_id": w, "partition": "development", "source_name": "one dealer"} for w in "abc"])
    cfg = dict(MASKED, calibration_policy={"genuine_envelope_min_watches": 2, "genuine_envelope_min_sources": 2})
    result = genuine_envelope.build(cfg, {"development": out}, split_csv)
    assert all(m["status"] == "INSUFFICIENT_GENUINE_ENVELOPE" and "sources" in m["reason"] for m in result["metrics"].values())


# --- per-marker populations ---------------------------------------------------------------------------------------

def photo(sha, wid, hands=(), cls="gen", source="dealer-a", value=0.1):
    st = statuses(hands)
    m = {str(h): [value, value, None if el.SHAPES[MODEL][h] == "round" else value, value] for h in REQUIRED[MODEL]}
    return ({"sha256": sha, "model": MODEL, "m": m, "physical_watch_ids": {wid} if wid else set(),
             "class_label": cls, "source": source},
            el.build(sha, "124060", st, "face-on-v3"))


def test_population_counts_watches_per_marker_only_where_the_marker_is_eligible():
    items = [photo("1" * 64, "w1", (2,), value=0.1), photo("2" * 64, "w1", (), value=0.3),
             photo("3" * 64, "w2", (2,)), photo("4" * 64, "w3", (), source="dealer-b"),
             photo("5" * 64, None), photo("6" * 64, "w4", cls="rep"),
             ({**photo("7" * 64, "w5")[0], "physical_watch_ids": {"w5", "w6"}}, photo("7" * 64, "w5")[1])]
    pop = marker_population.population([p for p, _ in items], {m["sha256"]: m for _, m in items})
    two = pop["populations"][MODEL][2]["metrics"]["pos"]
    one = pop["populations"][MODEL][1]["metrics"]["pos"]
    assert two["genuine_watches"] == 2 and two["photos"] == 2          # w1 (its eligible photo only) and w3
    assert one["genuine_watches"] == 3 and one["photos"] == 4          # w1 counted once from two photos
    assert pop["populations"][MODEL][1]["metrics"].keys() == {"side", "inout", "pos"}   # round: no tilt
    assert pop["populations"][MODEL][12]["metrics"].keys() == {"side", "inout", "tilt"}
    assert pop["photos_excluded"] == {"identity_conflict_or_unknown_watch": 2, "not_genuine": 1}
    assert two["sources"] == 2 and two["underrepresented"]
    # one partition per watch, shared by every marker: no watch spans development and holdout
    assert set(pop["partition_of_watch"]) == {"w1", "w2", "w3"}
    for rec in pop["populations"][MODEL].values():
        for q in rec["metrics"].values():
            assert sum(q["watches_by_partition"].values()) == q["genuine_watches"]


def test_population_refuses_a_mask_bound_to_another_photograph():
    p, m = photo("1" * 64, "w1")
    with pytest.raises(el.MaskError):
        marker_population.population([p], {"1" * 64: dict(m, sha256="2" * 64)})


def test_real_evidence_populations_keep_the_strict_13_and_admit_only_masked_photos():
    records = marker_population.from_evidence(REPO / "docs/image-archive/face-on-evidence.jsonl", catalogue=[])
    admitted = [r for r in records if r["admitted"]]
    assert all(not r["photo_reasons"] and len(r["mask"]["eligible_marker_ids"]) >= el.min_eligible(r["model"])
               for r in admitted)
    assert sum(r["mask"]["full_watch"] for r in admitted) == 13
    assert all(r["mask"] is None for r in records if r["photo_reasons"])          # no mask excuses a photo failure
    two = [r for r in admitted if el.eligible(r["mask"], 2)]
    assert 0 < len(two) < len(admitted) / 3
