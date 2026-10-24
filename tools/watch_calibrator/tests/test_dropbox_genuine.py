"""Dropbox research mode: input gates, read-only source, cache, statistics and the end-to-end report (offline)."""
import csv
from hashlib import sha256
import json
import math
from pathlib import Path
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

FOLDER = "Suitable Photos/Submariner 124060"
INDEX_HEAD = ["sha256", "exact_reference", "dial_generation", "physical_watch_id", "identity_status", "dealer",
              "provenance", "authenticity", "photographic_suitability", "source_url"]


def jpeg(i):
    return b"\xff\xd8\xff" + f"photo {i}".encode() * 30


class Archive:
    """A controlled mirror of the Dropbox archive layout, readable through CacheSource."""

    def __init__(self, root):
        self.root = Path(root)
        self.photos, self.index, self.selections = {}, [], {}
        (self.root / "CACHE_MANIFEST.json").parent.mkdir(parents=True, exist_ok=True)
        (self.root / "CACHE_MANIFEST.json").write_text("{}")

    def add(self, i, watch="w1", ref="124060", folder=FOLDER, auth="genuine_dealer", prov="established_dealer",
            ident="confirmed", suit="suitable", index=True, in_folder=True):
        data = jpeg(i)
        h = sha256(data).hexdigest()
        if in_folder:
            p = self.root / folder / f"{h}.jpg"
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_bytes(data)
        if index:
            self.index.append({"sha256": h, "exact_reference": ref, "dial_generation": "Mk1", "physical_watch_id": watch,
                               "identity_status": ident, "dealer": "Bob's", "provenance": prov,
                               "authenticity": auth, "photographic_suitability": suit,
                               "source_url": f"https://dealer.test/{i}"})
        self.photos[i] = h
        return h

    def select(self, sid, entries, when="2026-10-23T20:00:00Z", complete=True, verified=None):
        sel = {"schema": "watch-align-suitable-selection/1", "selection_id": sid, "reviewer": "chatgpt",
               "reviewed_at": when, "photos": [{"sha256": h, "model": m, "suitability": s} for h, m, s in entries]}
        raw = json.dumps(sel).encode()
        d = self.root / "Suitable Photos/_audit" / sid
        d.mkdir(parents=True, exist_ok=True)
        (d / "selection.json").write_bytes(raw)
        verified = {h for h, _, s in entries if s == "suitable"} if verified is None else verified
        run = {"copy_status": "complete" if complete else "incomplete", "dry_run": False,
               "selection_sha256": sha256(raw).hexdigest(),
               "photos": [{"sha256": h, "result": "copied_verified" if h in verified else "failed"} for h, _, _ in entries]}
        (d / "run-20261024T000000Z-abcd.json").write_text(json.dumps(run))

    def write_index(self, head=INDEX_HEAD):
        p = self.root / gates.DEFAULT_INDEX
        p.parent.mkdir(parents=True, exist_ok=True)
        with p.open("w", newline="") as fh:
            w = csv.DictWriter(fh, fieldnames=head, extrasaction="ignore")
            w.writeheader()
            w.writerows(self.index)

    def gate(self, column_map=None):
        self.write_index()
        acc, rej, audit, _ = gates.select_inputs(ds.CacheSource(self.root), column_map=column_map)
        return {a["sha256"]: a for a in acc}, {r["sha256"]: r["reason"] for r in rej}, audit


class GateTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.a = Archive(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def test_accepted_photo_keeps_exact_identity_and_is_never_production_approved(self):
        h = self.a.add(1, watch="bobs-124060-17")
        self.a.select("sel-20261023-x", [(h, "submariner_124060", "suitable")])
        acc, rej, audit = self.a.gate()
        rec = acc[h]
        self.assertEqual((rec["reference"], rec["physical_watch_id"], rec["dealer"], rec["provenance"], rec["dial_generation"]),
                         ("124060", "bobs-124060-17", "Bob's", "established_dealer", "Mk1"))
        self.assertFalse(rec["production_calibration_approved"])
        self.assertEqual(audit["accepted_watches"], 1)

    def test_each_failed_gate_excludes_with_its_reason(self):
        a = self.a
        cases = {
            "replica": a.add(2, auth="rep"),
            "replica provenance": a.add(3, prov="curated_replica_seed", auth="genuine_dealer"),
            "authenticity not established": a.add(4, auth="unknown"),
            "identity not confirmed": a.add(5, ident="probable"),
            "no physical-watch identity": a.add(6, watch=""),
            "index suitability": a.add(7, suit="uncertain"),
            "no explicitly supported app geometry": a.add(8, ref="116610LN", folder="Suitable Photos/Submariner Date 126610"),
            "contradicts index reference": a.add(9, ref="126710BLNR"),
            "missing from the source and review index": a.add(10, index=False),
            "unreviewed": a.add(11),
            "not in Suitable Photos": a.add(12, in_folder=False),
        }
        entries = [(h, "submariner_124060", "suitable") for k, h in cases.items() if k != "unreviewed"]
        a.select("sel-20261023-x", entries)
        acc, rej, _ = a.gate()
        self.assertEqual(acc, {})
        for why, h in cases.items():
            self.assertIn(why.split()[0], rej[h], f"{why}: {rej[h]}")

    def test_suitability_must_still_hold_and_be_applied(self):
        a = self.a
        later_out, no_run, no_copy = a.add(1), a.add(2), a.add(3)
        a.select("sel-20261023-a", [(later_out, "submariner_124060", "suitable")])
        a.select("sel-20261024-b", [(later_out, "submariner_124060", "not_suitable")], when="2026-10-24T08:00:00Z")
        a.select("sel-20261024-c", [(no_run, "submariner_124060", "suitable")], complete=False)
        a.select("sel-20261024-d", [(no_copy, "submariner_124060", "suitable")], verified=set())
        acc, rej, _ = a.gate()
        self.assertEqual(acc, {})
        self.assertIn("not_suitable", rej[later_out])
        self.assertIn("no complete verified copy run", rej[no_run])
        self.assertIn("never verified", rej[no_copy])

    def test_conflicting_evidence_fails_closed(self):
        a = self.a
        dup = a.add(1)
        a.index.append({**a.index[-1], "physical_watch_id": "other-watch"})
        two = a.add(2)
        p = a.root / "Suitable Photos/GMT-Master II 126710" / f"{two}.jpg"
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(jpeg(2))
        a.select("sel-20261023-x", [(dup, "submariner_124060", "suitable"), (two, "submariner_124060", "suitable")])
        acc, rej, _ = a.gate()
        self.assertIn("conflicting records", rej[dup])
        self.assertIn("more than one model folder", rej[two])

    def test_index_without_required_fields_stops_the_run_unless_mapped(self):
        h = self.a.add(1)
        self.a.select("sel-20261023-x", [(h, "submariner_124060", "suitable")])
        head = [c if c != "identity_status" else "who_is_it" for c in INDEX_HEAD]
        for r in self.a.index:
            r["who_is_it"] = r.pop("identity_status")
        self.a.write_index(head)
        with self.assertRaisesRegex(gates.InputError, "identity_status"):
            gates.select_inputs(ds.CacheSource(self.a.root))
        acc, _, _, _ = gates.select_inputs(ds.CacheSource(self.a.root), column_map={"identity_status": "who_is_it"})
        self.assertEqual(len(acc), 1)


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
        self.assertNotIn("token", str(e.exception).lower().replace("token refresh", ""))
        with self.assertRaises(ds.SourceError):
            src.read("../outside.jpg")

    def test_cache_holds_only_verified_approved_photos(self):
        with tempfile.TemporaryDirectory() as t:
            a = Archive(Path(t) / "src")
            keep, drop = a.add(1), a.add(2)
            a.select("sel-20261023-x", [(keep, "submariner_124060", "suitable"), (drop, "submariner_124060", "uncertain")])
            a.write_index()
            src = ds.CacheSource(a.root)
            acc, _, _, raw = gates.select_inputs(src)
            cache = Path(t) / "cache"
            ds.cache_approved(src, acc, {gates.DEFAULT_INDEX: raw}, cache)
            photos = sorted(p.name for p in cache.rglob("*.jpg"))
            self.assertEqual(photos, [f"{keep}.jpg"])
            bad = [{**acc[0], "sha256": "0" * 64}]
            with self.assertRaisesRegex(ds.SourceError, "SHA-256"):
                ds.cache_approved(src, bad, {}, Path(t) / "cache2")
            (cache / gates.DEFAULT_INDEX).write_bytes(b"changed")
            with self.assertRaisesRegex(ds.SourceError, "different"):
                ds.cache_approved(src, acc, {gates.DEFAULT_INDEX: raw}, cache)


def row(sha, hour, shape, **kw):
    base = {"status": "MEASURED", "photo_reason": "", "credible": "1", "sha256": sha, "hour": str(hour), "shape": shape,
            "source": "outer metal", "radial_from_top_edge": "0", "gap_from_fit": "0", "withheld_reason": "",
            "state": "NO_VISIBLE_ISSUE", "track_scale": "1.0", "ring_turn_pct": "0", "ring_scale": "1"}
    base.update({k: str(v) for k, v in kw.items()})
    return base


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
        self.assertAlmostEqual(d["median"], 0.20)            # watch medians 0.12, 0.20, 0.30
        self.assertAlmostEqual(d["within_watch_sd"], statistics.stdev([0.10, 0.12, 0.50]))
        rep = next(r for r in res["repeatability"] if r["physical_watch_id"] == "A" and r["dimension"] == "sideways_pct")
        self.assertAlmostEqual(rep["range"], 0.40)

    def test_no_range_from_one_or_two_watches(self):
        res = mv.analyse([row("A-0", 1, "round", side_pct=0.1), row("B-0", 1, "round", side_pct=0.3)],
                         self.photos({"A": 1, "B": 1}))
        d = next(x for x in res["distributions"] if x["dimension"] == "sideways_pct")
        self.assertTrue(math.isnan(d["min"]) and math.isnan(d["p95"]) and math.isnan(d["mad"]))
        self.assertIn("insufficient", d["evidence"])
        self.assertEqual(d["individual_values"], "A=0.1000;B=0.3000")

    def test_unusual_genuine_reading_is_flagged_and_kept(self):
        watches = {chr(65 + i): 1 for i in range(12)}
        rows = [row(f"{w}-0", 4, "round", side_pct=0.05 * (i % 4)) for i, w in enumerate(watches)]
        rows[-1]["side_pct"] = "2.5"
        d = next(x for x in mv.analyse(rows, self.photos(watches))["distributions"] if x["dimension"] == "sideways_pct")
        self.assertEqual(d["independent_watches"], 12)
        self.assertEqual(d["max"], 2.5)
        self.assertEqual(d["unusual_watches_kept"], 1)

    def test_top_edge_and_centre_fallback_are_never_mixed(self):
        rows = [row("A-0", 12, "triangle", inout_pct=0.1, radial_from_top_edge=1),
                row("B-0", 12, "triangle", inout_pct=0.9, radial_from_top_edge=0)]
        dims = {d["dimension"]: d for d in mv.analyse(rows, self.photos({"A": 1, "B": 1}))["distributions"]
                if d["hour"] == 12}
        self.assertEqual(dims["radial_top_edge_pct"]["median"], 0.1)
        self.assertEqual(dims["radial_centre_fallback_pct"]["median"], 0.9)

    def test_withheld_and_placed_readings_never_enter_statistics(self):
        rows = [row("A-0", 12, "triangle", gap_pct=3.0, gap_from_fit=1),
                row("B-0", 12, "triangle", credible=0, withheld_reason="a hand reaches it", side_pct=5.0),
                row("C-0", 12, "triangle", gap_pct=3.2, side_pct=0.1)]
        res = mv.analyse(rows, self.photos({"A": 1, "B": 1, "C": 1}))
        dims = {d["dimension"]: d for d in res["distributions"] if d["hour"] == 12}
        self.assertEqual(dims["gap_pct"]["independent_watches"], 1)
        self.assertEqual(dims["sideways_pct"]["independent_watches"], 1)
        m = next(x for x in res["markers"] if x["hour"] == 12)
        self.assertEqual((m["credible_photos"], m["withheld_photos"]), (2, 1))
        self.assertIn("a hand reaches it", m["withheld_reasons"])

    def test_physical_spread_separates_photographic_noise(self):
        # 200 watches with physical SD 0.10, each photographed 3 times with photographic SD 0.05 (seeded)
        import random
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
        rows = [row(s, 1, "round", side_pct=0.01 * (i % 7), fit_rms_px=0.1)
                for i, s in enumerate(self.photos(watches, dealers=("EWC",)))]
        dims = {d["dimension"]: d for d in mv.analyse(rows, self.photos(watches, dealers=("EWC",)))["distributions"]}
        self.assertIn("one dealer only", dims["sideways_pct"]["evidence"])
        self.assertIn("diagnostic", dims["outline_fit_rms_px"]["evidence"])
        self.assertIn("diagnostic", dims["minute_track_scale"]["evidence"])
        two = self.photos(watches)
        rows = [row(s, 1, "round", side_pct=0.01 * (i % 7)) for i, s in enumerate(two)]
        d = next(x for x in mv.analyse(rows, two)["distributions"] if x["dimension"] == "sideways_pct")
        self.assertTrue(d["evidence"].startswith("sufficient"))

    def test_hours_sort_numerically_and_limitless_dimensions_are_not_compared(self):
        rows = [row(f"{w}-0", h, "round", side_pct=0.1, pos_pct=0.2, pos_limit_pct=1.1)
                for w in ("A", "B", "C") for h in (2, 10, 1)]
        res = mv.analyse(rows, self.photos({"A": 1, "B": 1, "C": 1}))
        self.assertEqual([m["hour"] for m in res["markers"]], [1, 2, 10])
        self.assertEqual({c["dimension"] for c in res["comparison"]}, {"position_replica_qc_pct"})

    def test_comparison_uses_the_frozen_limits_as_applied(self):
        rows = [row("A-0", 12, "triangle", gap_pct=2.0, gap_limit_pct=2.4, strict_gap_limit_pct=2.7),
                row("B-0", 12, "triangle", gap_pct=2.6, gap_limit_pct=2.4, strict_gap_limit_pct=2.7),
                row("C-0", 12, "triangle", gap_pct=3.0, gap_limit_pct=2.4, strict_gap_limit_pct=2.7)]
        c = next(x for x in mv.analyse(rows, self.photos({"A": 1, "B": 1, "C": 1}))["comparison"]
                 if x["dimension"] == "gap_pct")
        self.assertEqual((c["replica_qc_watches_outside"], c["strict_genuine_watches_outside"]), (1, 2))


class EndToEndTest(unittest.TestCase):
    def test_run_from_cache_produces_traceable_research_outputs_only(self):
        with tempfile.TemporaryDirectory() as t:
            a = Archive(Path(t) / "cache")
            hs = [a.add(i, watch=f"w{i // 2}") for i in range(6)]
            rep = a.add(9, auth="rep")
            a.select("sel-20261023-x", [(h, "submariner_124060", "suitable") for h in hs + [rep]])
            a.write_index()

            def fake_measure(model, manifest, root, out):
                lines = list(csv.DictReader(open(manifest)))
                with open(out, "w", newline="") as fh:
                    w = csv.DictWriter(fh, fieldnames=list(row("x", 1, "round", side_pct=0)))
                    w.writeheader()
                    for i, r in enumerate(lines):
                        self.assertEqual(sha256((Path(root) / r["local_path"]).read_bytes()).hexdigest(), r["sha256"])
                        w.writerow(row(r["sha256"], 1, "round", side_pct=0.01 * i))
                return "PARITY compared=6 differences=0 (fixture)"

            original = dg.harness_measure
            dg.harness_measure = fake_measure
            try:
                args = type("A", (), {"cache": str(a.root), "root": ds.DEFAULT_ROOT, "index": gates.DEFAULT_INDEX,
                                      "column_map": None, "out": str(Path(t) / "research-out"), "skip_parity": False})
                out = dg.run(args)
            finally:
                dg.harness_measure = original
            names = {p.name for p in out.iterdir()}
            self.assertTrue({"REPORT.md", "results.json", "marker_measurements.csv", "genuine_distributions.csv",
                             "within_watch_repeatability.csv", "production_comparison.csv", "input_rejected.csv"} <= names)
            meas = list(csv.DictReader(open(out / "marker_measurements.csv")))
            self.assertEqual({m["sha256"] for m in meas}, set(hs))
            self.assertTrue(all(m["physical_watch_id"] and m["archive_path"].startswith("Suitable Photos/") for m in meas))
            rejected = list(csv.DictReader(open(out / "input_rejected.csv")))
            self.assertEqual([r["sha256"] for r in rejected], [rep])
            report = (out / "REPORT.md").read_text()
            for section in ("## 1. Input audit", "## 2.", "## 3.", "## 4.", "## 5.", "## 6.", "## 7."):
                self.assertIn(section, report)
            self.assertFalse(json.loads((out / "results.json").read_text())["production_calibration_approved"])
            with self.assertRaises(SystemExit):
                dg.output_dir(dg.REPO / "calibration" / "x")
            with self.assertRaises(SystemExit):
                dg.output_dir(a.root / "out", [a.root])
            with self.assertRaises(SystemExit):
                dg.output_dir(out)                                    # not empty

    def test_tampered_cached_photo_stops_measurement(self):
        with tempfile.TemporaryDirectory() as t:
            a = Archive(Path(t) / "cache")
            h = a.add(1)
            a.select("sel-20261023-x", [(h, "submariner_124060", "suitable")])
            acc, _, _ = a.gate()
            (a.root / FOLDER / f"{h}.jpg").write_bytes(b"\xff\xd8\xfftampered")
            with self.assertRaisesRegex(SystemExit, "SHA-256"):
                dg.measure_all(ds.CacheSource(a.root), list(acc.values()), Path(t) / "work",
                               lambda *x: self.fail("measured a tampered photo"))


if __name__ == "__main__":
    unittest.main()
