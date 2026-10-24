"""Dropbox research mode on the REAL index schema and audit-record structure (offline, controlled fixtures)."""
import csv
from hashlib import sha256
import json
import math
from pathlib import Path
import random
import statistics
import sys
import tempfile
import unittest

HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))
import dropbox_genuine as dg  # noqa: E402
import dropbox_inputs as gates  # noqa: E402
import dropbox_source as ds  # noqa: E402
import marker_variation as mv  # noqa: E402

SNAPSHOT = HERE / "snapshots" / "dropbox_index_20261024.json"
FOLDER_OF = {"124060": "Submariner 124060", "126610LN": "Submariner Date 126610", "126710BLNR": "GMT-Master II 126710"}
SELECTION_MODEL = {"124060": "submariner_124060", "126610LN": "submariner_126610", "126710BLNR": "gmt_126710"}


def jpeg(i):
    return b"\xff\xd8\xff" + f"photo {i} ".encode() * (20 + i % 7)


class Archive:
    """A controlled mirror of the real Dropbox archive: the exact 15-column index, Suitable Photos folders, and
    _audit/<selection>/selection.json + run-*.json records with the Suitable Photos copy job's structure."""

    def __init__(self, root):
        self.root = Path(root)
        self.root.mkdir(parents=True, exist_ok=True)
        (self.root / "CACHE_MANIFEST.json").write_text("{}")
        self.rows, self.selected = [], []
        self.n = 0

    def add(self, ref="124060", watch=None, folder=None, location=None, suitability="suitable",
            provenance=gates.CANDIDATE, conflict="False", select=True, select_model=None, select_as="suitable",
            in_folder=True, nbytes=None, copied_to=None):
        self.n += 1
        data = jpeg(self.n)
        h = sha256(data).hexdigest()
        folder = folder or FOLDER_OF.get(ref, "Unknown model")
        path = f"Suitable Photos/{folder}/{h}.jpg"
        if in_folder:
            p = self.root / path
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_bytes(data)
        self.rows.append({
            "sha256": h, "original_path": f"recovered/{self.n}.jpg", "bytes": str(nbytes or len(data)),
            "initial_photo_suitability": suitability, "initial_model_family": "submariner",
            "provenance_status": provenance, "specific_model": ref,
            "physical_watch_id": f"w-{ref}-{self.n}" if watch is None else watch, "source_name": "Bob's Watches",
            "original_source_url": f"https://dealer.test/{self.n}", "association_count": "1",
            "identity_conflict": conflict,
            "current_curated_location": location if location is not None else f"WatchAlign/{path}",
            "calibration_approved": "False", "notes": ""})
        if select:
            self.selected.append((h, select_model or SELECTION_MODEL.get(ref, "unknown"), select_as,
                                  copied_to or f"Suitable Photos/{folder}/{h}.jpg"))
        return h

    def apply(self, sid="sel-20261023-screened-373", when="2026-10-23T20:09:20Z", complete=True, unverified=()):
        """selection.json and one run record exactly as tools/image_archive/suitable.py writes them."""
        if not self.selected:
            return
        sel = {"schema": "watch-align-suitable-selection/1", "selection_id": sid, "reviewer": "chatgpt",
               "reviewed_at": when,
               "photos": [{"sha256": h, "model": m, "suitability": s, "reasons": [], "comment": ""}
                          for h, m, s, _ in self.selected]}
        raw = json.dumps(sel, indent=1).encode()
        d = self.root / "Suitable Photos/_audit" / sid
        d.mkdir(parents=True, exist_ok=True)
        (d / "selection.json").write_bytes(raw)
        photos = []
        for h, m, s, dest in self.selected:
            rec = {"sha256": h, "model": m, "suitability": s, "reasons": [], "comment": "", "catalogue_families": [],
                   "catalogue_provenance": ["established_dealer"]}
            if s == "suitable":
                rec.update(action="copy", source=f"unreviewed/objects/{h[:2]}/{h}.jpg", destination=dest, size=1,
                           result="failed" if h in unverified else "copied_verified")
            else:
                rec["action"] = "excluded"
            photos.append(rec)
        run = {"schema": "watch-align-suitable-copy-run/1", "selection_id": sid,
               "selection_sha256": sha256(raw).hexdigest(), "reviewer": "chatgpt", "reviewed_at": when,
               "started": "2026-10-24T08:00:00+00:00", "finished": "2026-10-24T08:20:00+00:00", "dry_run": False,
               "duplicate_entries": 0, "photos": photos, "summary": {},
               "copy_status": "complete" if complete else "incomplete", "selection_record": "written_verified"}
        (d / "run-20261024T082000+0000-1a2b3c4d.json").write_text(json.dumps(run, indent=1))
        self.selected = []

    def write_index(self):
        p = self.root / gates.DEFAULT_INDEX
        p.parent.mkdir(parents=True, exist_ok=True)
        with p.open("w", newline="") as fh:
            w = csv.DictWriter(fh, fieldnames=list(gates.COLUMNS))
            w.writeheader()
            w.writerows(self.rows)

    def gate(self):
        self.apply()
        self.write_index()
        acc, rej, audit, _ = gates.select_inputs(ds.CacheSource(self.root))
        return {a["sha256"]: a for a in acc}, {r["sha256"]: r["reason"] for r in rej}, audit


class GateTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.a = Archive(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def test_candidate_designation_and_identity_are_kept_verbatim(self):
        h = self.a.add(watch="bobs-124060-17")
        acc, _, audit = self.a.gate()
        rec = acc[h]
        self.assertEqual(rec["provenance_status"], "dealer_attributed_genuine_candidate")
        self.assertEqual((rec["reference"], rec["physical_watch_id"], rec["dealer"]), ("124060", "bobs-124060-17",
                                                                                       "Bob's Watches"))
        self.assertEqual(rec["index_calibration_approved"], "False")
        self.assertFalse(rec["production_calibration_approved"])
        self.assertEqual(audit["provenance_designation"], "dealer_attributed_genuine_candidate")
        self.assertNotIn("verified_genuine", json.dumps(rec))

    def test_every_gate_fails_closed_with_its_reason(self):
        a = self.a
        cases = {
            "replica": a.add(provenance="replica"),
            "unknown provenance": a.add(provenance="unknown"),
            "blank provenance": a.add(provenance=""),
            "identity conflict": a.add(conflict="True"),
            "identity conflict not recorded": a.add(conflict=""),
            "no physical_watch_id": a.add(watch=""),
            "index suitability": a.add(suitability="not_suitable"),
            "bytes mismatch": a.add(nbytes=999999),
            "unsupported 126610 (unspecific)": a.add(ref="126610", folder="Submariner Date 126610"),
            "unsupported 116610LN": a.add(ref="116610LN", folder="Submariner Date 126610"),
            "unreviewed": a.add(select=False),
            "latest selection uncertain": a.add(select_as="uncertain"),
            "not in Suitable Photos": a.add(in_folder=False),
        }
        acc, rej, audit = a.gate()
        self.assertEqual(acc, {})
        expect = {"replica": "provenance_status replica", "unknown provenance": "provenance_status unknown",
                  "blank provenance": "provenance_status blank", "identity conflict": "identity conflict recorded",
                  "identity conflict not recorded": "not explicitly false", "no physical_watch_id": "no physical_watch_id",
                  "index suitability": "initial_photo_suitability", "bytes mismatch": "differs from index bytes",
                  "unsupported 126610 (unspecific)": "unsupported or unspecific reference: 126610",
                  "unsupported 116610LN": "unsupported or unspecific reference: 116610LN",
                  "unreviewed": "unreviewed", "latest selection uncertain": "says uncertain",
                  "not in Suitable Photos": "not in Suitable Photos"}
        for name, h in cases.items():
            self.assertIn(expect[name], rej[h], name)
        self.assertEqual({u["specific_model"] for u in audit["unsupported_references"]}, {"126610", "116610LN"})

    def test_folder_must_match_the_index_location_model_and_reference(self):
        a = self.a
        wrong_loc = a.add(location="WatchAlign/Suitable Photos/GMT-Master II 126710/x.jpg")
        not_curated = a.add(location="WatchAlign/unreviewed/objects/ab/x.jpg")
        wrong_folder = a.add(ref="124060", folder="GMT-Master II 126710")
        sel_contradicts = a.add(ref="126610LN", select_model="gmt_126710")
        bad_folder = a.add(folder="Misc")
        acc, rej, _ = a.gate()
        self.assertEqual(acc, {})
        self.assertIn("does not match index current_curated_location", rej[wrong_loc])
        self.assertIn("not in Suitable Photos", rej[not_curated])
        self.assertIn("does not hold 124060 geometry", rej[wrong_folder])
        self.assertIn("contradicts 126610LN", rej[sel_contradicts])
        self.assertIn("not a recognised", rej[bad_folder])

    def test_curated_move_after_an_unknown_model_selection_is_accepted(self):
        # selected as "unknown" into Unknown model, later curated into Submariner Date 126610 with the index updated
        h = self.a.add(ref="126610LN", select_model="unknown",
                       copied_to="Suitable Photos/Unknown model/placeholder.jpg")
        bare = self.a.add(ref="126610LN", location="/Watch Align/Apps/Watch Align Archive/WatchAlign/"
                                                   "Suitable Photos/Submariner Date 126610")
        acc, rej, _ = self.a.gate()
        self.assertTrue(acc[h]["model_refined_after_selection"])
        self.assertEqual(acc[h]["archive_path"].split("/")[1], "Submariner Date 126610")
        self.assertIn(bare, acc)                      # a folder-only location with any root prefix also matches

    def test_selection_evidence_must_be_applied_and_verified(self):
        a = self.a
        later_out = a.add()
        a.apply()
        a.selected.append((later_out, "submariner_124060", "not_suitable", ""))
        a.apply(sid="sel-20261024-recheck", when="2026-10-24T09:00:00Z")
        no_run = a.add()
        a.apply(sid="sel-20261024-incomplete", complete=False)
        unverified = a.add()
        a.apply(sid="sel-20261024-unverified", unverified={unverified})
        twice = a.add()
        extra = a.root / "Suitable Photos/GMT-Master II 126710" / f"{twice}.jpg"
        extra.parent.mkdir(parents=True, exist_ok=True)
        extra.write_bytes(b"x")
        acc, rej, _ = a.gate()
        self.assertEqual(acc, {})
        self.assertIn("not_suitable", rej[later_out])
        self.assertIn("no complete verified copy run", rej[no_run])
        self.assertIn("never verified", rej[unverified])
        self.assertIn("more than one curated folder", rej[twice])

    def test_conflicting_index_rows_and_wrong_schema_fail_closed(self):
        h = self.a.add()
        self.a.rows.append({**self.a.rows[-1], "physical_watch_id": "someone-else"})
        _, rej, _ = self.a.gate()
        self.assertIn("conflicting rows", rej[h])
        (self.a.root / gates.DEFAULT_INDEX).write_text("sha256,model,watch_id\n" + h + ",124060,w\n")
        with self.assertRaisesRegex(gates.InputError, "provenance_status"):
            gates.select_inputs(ds.CacheSource(self.a.root))


def snapshot_archive(root):
    """417 index rows reproducing the reviewed 24 Oct 2026 population: 82 suitable photographs of 46 watches in Suitable
    Photos (124060 71/35, 126610LN 2/2, 126710BLNR 1/1, eight unsupported or unspecific), 335 other rows."""
    a = Archive(root)
    for w in range(35):                                     # 34 watches x 2 photos + 1 watch x 3 photos = 71
        for _ in range(3 if w == 0 else 2):
            a.add(ref="124060", watch=f"bobs-124060-{w}")
    for w in range(2):
        a.add(ref="126610LN", watch=f"bobs-126610ln-{w}")
    a.add(ref="126710BLNR", watch="bobs-126710blnr-0")
    for i, ref in enumerate(["126610", "126610", "126610", "116610LN", "116610LN", "Submariner", "Submariner", ""]):
        a.add(ref=ref, watch=f"other-{i}", folder="Submariner Date 126610" if ref.startswith(("126610", "116610"))
              else "Unknown model")
    for i in range(300):                                    # screened not suitable: never copied
        a.add(ref="124060", watch=f"ns-{i}", suitability="not_suitable", in_folder=False,
              select=i < 250, select_as="not_suitable")
    for i in range(35):                                     # replica seeds: never measured
        a.add(ref="124060", watch=f"rep-{i}", provenance="replica", suitability="not_suitable", in_folder=False,
              select=False)
    a.apply()
    a.write_index()
    return a


def row(sha, hour, shape, **kw):
    base = {"status": "MEASURED", "photo_reason": "", "credible": "1", "sha256": sha, "hour": str(hour), "shape": shape,
            "source": "outer metal", "radial_from_top_edge": "0", "gap_from_fit": "0", "withheld_reason": "",
            "state": "NO_VISIBLE_ISSUE", "track_scale": "1.0", "ring_turn_pct": "0", "ring_scale": "1"}
    base.update({k: str(v) for k, v in kw.items()})
    return base


def run_args(cache, out, expect=None):
    return type("A", (), {"cache": str(cache), "root": ds.DEFAULT_ROOT, "index": gates.DEFAULT_INDEX,
                          "out": str(out), "skip_parity": False, "expect": str(expect) if expect else None})


class SnapshotTest(unittest.TestCase):
    def test_real_population_matches_the_committed_source_snapshot(self):
        with tempfile.TemporaryDirectory() as t:
            a = snapshot_archive(Path(t) / "cache")
            acc, rej, audit, _ = gates.select_inputs(ds.CacheSource(a.root))
            self.assertEqual(audit["index_rows"], 417)
            self.assertEqual(gates.check_snapshot(audit, json.loads(SNAPSHOT.read_text())), [])
            self.assertEqual(len(audit["unsupported_references"]), 8)
            self.assertEqual(audit["in_suitable_folders"], {"photos": 82, "watches": 46,
                                                            "by_reference": audit["in_suitable_folders"]["by_reference"]})
            self.assertTrue(all(r["provenance_status"] == gates.CANDIDATE for r in acc))

    def test_a_different_population_stops_before_measuring(self):
        with tempfile.TemporaryDirectory() as t:
            a = snapshot_archive(Path(t) / "cache")
            gone = next((a.root / "Suitable Photos/GMT-Master II 126710").iterdir())
            gone.unlink()                                     # the only 126710BLNR photograph has been removed
            out = Path(t) / "out"
            with self.assertRaisesRegex(SystemExit, "126710BLNR"):
                dg.run(run_args(a.root, out, SNAPSHOT))
            self.assertTrue((out / "FAILED.md").exists())
            self.assertFalse((out / "REPORT.md").exists())

    def test_zero_accepted_photographs_fails_visibly(self):
        with tempfile.TemporaryDirectory() as t:
            a = Archive(Path(t) / "cache")
            a.add(provenance="replica")
            a.add(ref="126610")
            a.apply()
            a.write_index()
            out = Path(t) / "out"
            with self.assertRaisesRegex(SystemExit, "no photograph passed"):
                dg.run(run_args(a.root, out))
            self.assertTrue((out / "FAILED.md").exists() and (out / "input_rejected.csv").exists())
            self.assertFalse((out / "REPORT.md").exists())


class EndToEndTest(unittest.TestCase):
    def test_run_on_the_snapshot_archive_produces_traceable_research_outputs(self):
        with tempfile.TemporaryDirectory() as t:
            a = snapshot_archive(Path(t) / "cache")
            models = []

            def fake_measure(model, manifest, root, out):
                models.append(model)
                lines = list(csv.DictReader(open(manifest)))
                with open(out, "w", newline="") as fh:
                    w = csv.DictWriter(fh, fieldnames=list(row("x", 1, "round", side_pct=0, pos_pct=0,
                                                               pos_limit_pct=1, qc_pos_off=0)))
                    w.writeheader()
                    for i, r in enumerate(lines):
                        self.assertEqual(sha256((Path(root) / r["local_path"]).read_bytes()).hexdigest(), r["sha256"])
                        w.writerow(row(r["sha256"], 1, "round", side_pct=0.01 * i, pos_pct=0.02 * i,
                                       pos_limit_pct=1.1, qc_pos_off=0))
                return "PARITY compared=0 differences=0 (fixture)"

            original = dg.harness_measure
            dg.harness_measure = fake_measure
            try:
                out = dg.run(run_args(a.root, Path(t) / "research-out", SNAPSHOT))
            finally:
                dg.harness_measure = original
            self.assertEqual(sorted(models), ["gmt_126710", "submariner_124060", "submariner_126610"])
            meas = list(csv.DictReader(open(out / "marker_measurements.csv")))
            self.assertEqual(len(meas), 74)
            self.assertTrue(all(m["provenance_status"] == gates.CANDIDATE and m["physical_watch_id"]
                                and m["archive_path"].startswith("Suitable Photos/") for m in meas))
            report = (out / "REPORT.md").read_text()
            self.assertIn("dealer_attributed_genuine_candidate", report)
            self.assertIn("NOT independently authenticated", report)
            self.assertIn("matches dropbox_index_20261024.json", report)
            unsupported = report[report.index("### Unsupported"):report.index("## 2.")]
            self.assertEqual(unsupported.count("| other-"), 8)
            self.assertNotIn("verified_genuine", report)
            for section in ("## 1. Input audit", "## 2.", "## 3.", "## 4.", "## 5.", "## 6.", "## 7."):
                self.assertIn(section, report)
            self.assertFalse(json.loads((out / "results.json").read_text())["production_calibration_approved"])
            with self.assertRaises(SystemExit):
                dg.output_dir(dg.REPO / "calibration" / "x")
            with self.assertRaises(SystemExit):
                dg.output_dir(a.root / "out", [a.root])
            with self.assertRaises(SystemExit):
                dg.output_dir(out)

    def test_tampered_cached_photo_stops_measurement(self):
        with tempfile.TemporaryDirectory() as t:
            a = Archive(Path(t) / "cache")
            h = a.add()
            acc, _, _ = a.gate()
            (a.root / "Suitable Photos/Submariner 124060" / f"{h}.jpg").write_bytes(b"\xff\xd8\xfftampered")
            with self.assertRaisesRegex(SystemExit, "SHA-256"):
                dg.measure_all(ds.CacheSource(a.root), list(acc.values()), Path(t) / "work",
                               lambda *x: self.fail("measured a tampered photo"))


class SourceTest(unittest.TestCase):
    def test_dropbox_is_read_only_and_never_leaks_credentials(self):
        class R:
            def __init__(self, status, body=None, content=b""):
                self.status_code, self.body, self.content = status, body or {}, content
            def json(self):
                return self.body
        class S:
            def __init__(self):
                self.calls = []
                self.replies = [R(401), R(200, {"access_token": "fresh-token"}),
                                R(200, {"entries": [{".tag": "file", "name": "a.jpg", "size": 3}], "has_more": True,
                                        "cursor": "c"}),
                                R(200, {"entries": [{".tag": "folder", "name": "_audit"}], "has_more": False})]
            def post(self, url, **kw):
                self.calls.append((url, kw))
                return self.replies.pop(0)
        env = {"DROPBOX_ACCESS_TOKEN": "stale-token", "DROPBOX_REFRESH_TOKEN": "refresh-secret", "DROPBOX_APP_KEY": "key"}
        s = S()
        src = ds.DropboxSource(environ=env, session=s)
        self.assertEqual(src.list("Suitable Photos"), [("a.jpg", True, 3), ("_audit", False, 0)])
        self.assertTrue(all(u in ds.READ_ENDPOINTS | {ds.TOKEN_URL} for u, _ in s.calls))
        self.assertEqual(s.calls[2][1]["headers"]["Authorization"], "Bearer fresh-token")
        with self.assertRaises(ds.SourceError):
            src._call(ds.API + "files/delete_v2", json_body={"path": "/x"})
        with self.assertRaisesRegex(ds.SourceError, "not configured"):
            ds.DropboxSource(environ={}, session=s)
        s.replies = [R(500)]
        with self.assertRaises(ds.SourceError) as e:
            src.read("Suitable Photos/a.jpg")
        self.assertNotIn("refresh-secret", str(e.exception))
        self.assertNotIn("fresh-token", str(e.exception))
        with self.assertRaises(ds.SourceError):
            src.read("../outside.jpg")

    def test_cache_holds_only_verified_approved_photos(self):
        with tempfile.TemporaryDirectory() as t:
            a = Archive(Path(t) / "src")
            keep = a.add()
            drop = a.add(select_as="uncertain")
            a.apply()
            a.write_index()
            src = ds.CacheSource(a.root)
            acc, _, _, raw = gates.select_inputs(src)
            cache = Path(t) / "cache"
            ds.cache_approved(src, acc, {gates.DEFAULT_INDEX: raw}, cache)
            self.assertEqual(sorted(p.name for p in cache.rglob("*.jpg")), [f"{keep}.jpg"])
            with self.assertRaisesRegex(ds.SourceError, "SHA-256"):
                ds.cache_approved(src, [{**acc[0], "sha256": "0" * 64}], {}, Path(t) / "cache2")
            (cache / gates.DEFAULT_INDEX).write_bytes(b"changed")
            with self.assertRaisesRegex(ds.SourceError, "different"):
                ds.cache_approved(src, acc, {gates.DEFAULT_INDEX: raw}, cache)


class StatisticsTest(unittest.TestCase):
    def photos(self, watches, dealers=("Bob's", "EWC")):
        return {f"{w}-{k}": {"reference": "124060", "physical_watch_id": w, "dealer": dealers[i % len(dealers)]}
                for i, (w, n) in enumerate(watches.items()) for k in range(n)}

    def test_the_independent_watch_is_the_unit(self):
        photos = self.photos({"A": 3, "B": 1, "C": 1})
        vals = {"A-0": 0.10, "A-1": 0.12, "A-2": 0.50, "B-0": 0.20, "C-0": 0.30}
        res = mv.analyse([row(s, 6, "baton", side_pct=v) for s, v in vals.items()], photos)
        d = next(x for x in res["distributions"] if x["hour"] == 6 and x["dimension"] == "sideways_pct")
        self.assertEqual((d["independent_watches"], d["photos"], d["multi_photo_watches"]), (3, 5, 1))
        self.assertAlmostEqual(d["median"], 0.20)
        self.assertAlmostEqual(d["within_watch_sd"], statistics.stdev([0.10, 0.12, 0.50]))

    def test_no_range_from_one_or_two_watches(self):
        res = mv.analyse([row("A-0", 1, "round", side_pct=0.1), row("B-0", 1, "round", side_pct=0.3)],
                         self.photos({"A": 1, "B": 1}))
        d = next(x for x in res["distributions"] if x["dimension"] == "sideways_pct")
        self.assertTrue(math.isnan(d["min"]) and math.isnan(d["p95"]) and math.isnan(d["mad"]))
        self.assertIn("insufficient", d["evidence"])
        self.assertEqual(d["individual_values"], "A=0.1000;B=0.3000")

    def test_unusual_genuine_candidate_reading_is_flagged_and_kept(self):
        watches = {chr(65 + i): 1 for i in range(12)}
        rows = [row(f"{w}-0", 4, "round", side_pct=0.05 * (i % 4)) for i, w in enumerate(watches)]
        rows[-1]["side_pct"] = "2.5"
        d = next(x for x in mv.analyse(rows, self.photos(watches))["distributions"] if x["dimension"] == "sideways_pct")
        self.assertEqual((d["independent_watches"], d["max"], d["unusual_watches_kept"]), (12, 2.5, 1))

    def test_top_edge_and_centre_fallback_are_never_mixed(self):
        rows = [row("A-0", 12, "triangle", inout_pct=0.1, radial_from_top_edge=1),
                row("B-0", 12, "triangle", inout_pct=0.9, radial_from_top_edge=0)]
        dims = {d["dimension"]: d for d in mv.analyse(rows, self.photos({"A": 1, "B": 1}))["distributions"]
                if d["hour"] == 12}
        self.assertEqual((dims["radial_top_edge_pct"]["median"], dims["radial_centre_fallback_pct"]["median"]), (0.1, 0.9))

    def test_withheld_and_placed_readings_never_enter_statistics(self):
        rows = [row("A-0", 12, "triangle", gap_pct=3.0, gap_from_fit=1),
                row("B-0", 12, "triangle", credible=0, withheld_reason="a hand reaches it", side_pct=5.0),
                row("C-0", 12, "triangle", gap_pct=3.2, side_pct=0.1)]
        res = mv.analyse(rows, self.photos({"A": 1, "B": 1, "C": 1}))
        dims = {d["dimension"]: d for d in res["distributions"] if d["hour"] == 12}
        self.assertEqual((dims["gap_pct"]["independent_watches"], dims["sideways_pct"]["independent_watches"]), (1, 1))
        m = next(x for x in res["markers"] if x["hour"] == 12)
        self.assertEqual((m["credible_photos"], m["withheld_photos"]), (2, 1))

    def test_physical_spread_separates_photographic_noise(self):
        rnd = random.Random(11)
        rows, photos = [], {}
        for w in range(200):
            true = rnd.gauss(0, 0.10)
            for k in range(3):
                s = f"W{w}-{k}"
                photos[s] = {"reference": "124060", "physical_watch_id": f"W{w}", "dealer": "ABC"[w % 3]}
                rows.append(row(s, 3, "baton", side_pct=true + rnd.gauss(0, 0.05)))
        d = next(x for x in mv.analyse(rows, photos)["distributions"] if x["dimension"] == "sideways_pct")
        self.assertAlmostEqual(d["within_watch_sd"], 0.05, delta=0.005)
        self.assertAlmostEqual(d["estimated_physical_sd"], 0.10, delta=0.012)
        self.assertTrue(d["evidence"].startswith("sufficient"))

    def test_single_dealer_or_diagnostic_dimensions_are_never_sufficient(self):
        watches = {f"W{i}": 2 for i in range(40)}
        one = self.photos(watches, dealers=("EWC",))
        dims = {d["dimension"]: d for d in mv.analyse([row(s, 1, "round", side_pct=0.01 * (i % 7), fit_rms_px=0.1)
                                                       for i, s in enumerate(one)], one)["distributions"]}
        self.assertIn("one dealer only", dims["sideways_pct"]["evidence"])
        self.assertIn("diagnostic", dims["outline_fit_rms_px"]["evidence"])

    def test_production_decisions_are_per_photo_with_each_photos_own_limit(self):
        # Same reading 1.20 % R on two photographs of one watch; the low-resolution photograph has the larger
        # allowance (limit 1.30) and passes, the other (limit 1.10) is outside. A median limit (1.20) would call
        # neither outside; production's own decisions say exactly one photograph is outside.
        rows = [row("A-0", 4, "round", pos_pct=1.2, pos_limit_pct=1.1, qc_pos_off=1),
                row("A-1", 4, "round", pos_pct=1.2, pos_limit_pct=1.3, qc_pos_off=0),
                row("B-0", 4, "round", pos_pct=0.3, pos_limit_pct=1.1, qc_pos_off=0,
                    strict_pos_pct=0.9, strict_pos_limit_pct=0.8, strict_pos_off=1)]
        res = mv.analyse(rows, self.photos({"A": 2, "B": 1}))
        qc = next(c for c in res["comparison"] if c["dimension"] == "position_replica_qc_pct")
        self.assertEqual((qc["replica_qc_photos_evaluated"], qc["replica_qc_photos_outside"]), (3, 1))
        self.assertEqual((qc["replica_qc_limit_min"], qc["replica_qc_limit_max"]), (1.1, 1.3))
        self.assertEqual((qc["replica_qc_watches_evaluated"], qc["replica_qc_watches_with_a_photo_outside"]), (2, 1))
        self.assertEqual(qc["replica_qc_recomputed_disagreements"], 0)
        st = next(c for c in res["comparison"] if c["dimension"] == "position_strict_pct")
        self.assertEqual((st["strict_genuine_photos_evaluated"], st["strict_genuine_photos_outside"]), (1, 1))
        # a decision that disagrees with the photograph's own value and limit is counted, never hidden
        rows[1]["qc_pos_off"] = "1"
        qc = next(c for c in mv.analyse(rows, self.photos({"A": 2, "B": 1}))["comparison"]
                  if c["dimension"] == "position_replica_qc_pct")
        self.assertEqual(qc["replica_qc_recomputed_disagreements"], 1)

    def test_gap_is_a_minimum(self):
        rows = [row("A-0", 12, "triangle", gap_pct=2.0, gap_limit_pct=2.4, qc_gap_off=1),
                row("B-0", 12, "triangle", gap_pct=3.0, gap_limit_pct=2.4, qc_gap_off=0)]
        c = next(x for x in mv.analyse(rows, self.photos({"A": 1, "B": 1}))["comparison"] if x["dimension"] == "gap_pct")
        self.assertEqual((c["replica_qc_photos_outside"], c["replica_qc_recomputed_disagreements"]), (1, 0))


if __name__ == "__main__":
    unittest.main()
