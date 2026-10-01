"""Tests for the research-only Submariner data and measurement path (issue #34)."""
import csv
import json
import math
import re
import shutil
import sys
import tempfile
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

import submariner_acquire as ACQ  # noqa: E402
from harvester.config import HARNESS_DIR  # noqa: E402
from harvester.harness import Harness, HarnessUnavailable  # noqa: E402
from subresearch import dataset as D  # noqa: E402
from subresearch import layout as L  # noqa: E402
from subresearch import measure as MS  # noqa: E402
from subresearch import split as SP  # noqa: E402
from subresearch import tables as TB  # noqa: E402

DRIVER_SRC = (HARNESS_DIR / "drivers" / "SubMeasure.java").read_text(encoding="utf-8")


def noise(seed, w=900, h=700):
    return Image.fromarray(np.random.default_rng(seed).integers(0, 255, (h, w, 3), dtype=np.uint8))


def smooth(seed, w=900, h=700):
    """Structured image so that perceptual hashes are meaningful."""
    rng = np.random.default_rng(seed)
    small = rng.integers(0, 255, (7, 9, 3), dtype=np.uint8)
    return Image.fromarray(small).resize((w, h), Image.Resampling.BICUBIC)


class LayoutTest(unittest.TestCase):
    def test_explicit_layouts(self):
        self.assertEqual((3, 6, 9), L.layout_for("124060")["batons"])
        self.assertEqual((), L.layout_for("124060")["date"])
        for m in ("126610LN", "126610lv"):
            self.assertEqual((6, 9), L.layout_for(m)["batons"])
            self.assertEqual((3,), L.layout_for(m)["date"])
        for m in ("124060", "126610LN", "126610LV"):
            lay = L.layout_for(m)
            self.assertEqual((12,), lay["triangle"])
            self.assertEqual((1, 2, 4, 5, 7, 8, 10, 11), lay["round"])
        self.assertEqual("baton", L.kind_at("124060", 3))
        self.assertEqual("date", L.kind_at("126610LN", 3))
        self.assertEqual(["12", "b3", "b6", "b9", "r1", "r2", "r4", "r5", "r7", "r8", "r10", "r11"], L.expected_landmarks("124060"))

    def test_non_submariner_models_have_no_layout(self):
        for m in ("126710BLNR", "126720VTNR", "116610LN", "116610LV", "114060", ""):
            with self.assertRaises(L.UnsupportedModel):
                L.layout_for(m)

    def test_layout_never_comes_from_gmt_date_side_logic(self):
        src = (Path(L.__file__)).read_text(encoding="utf-8")
        self.assertNotIn("GmtDialLayout", src.replace("(GmtDialLayout)", ""))
        self.assertNotIn("GmtDialLayout.", DRIVER_SRC)

    def test_job_line_carries_model_layout(self):
        self.assertEqual("/p\tabc\t124060\t3,6,9\t-\torig,s94", MS.job_line("/p", "abc", "124060", ("orig", "s94")))
        self.assertEqual("/p\tabc\t126610LV\t6,9\t3\torig", MS.job_line("/p", "abc", "126610LV", ("orig",)))


class AcquisitionHelpersTest(unittest.TestCase):
    def test_class_labels_normalised(self):
        for raw, want in (("genuine", "gen"), ("Genuine", "gen"), ("gen", "gen"), ("replica", "rep"), ("REP", "rep"), ("?", "")):
            self.assertEqual(want, ACQ.normalize_class(raw))
            self.assertEqual(want, D.normalize_class(raw))

    def test_physical_watch_id_explicit_or_candidate(self):
        self.assertEqual("w1", ACQ.physical_watch_id({"candidate_id": "c1", "physical_watch_id": "w1"}))
        self.assertEqual("c1", ACQ.physical_watch_id({"candidate_id": "c1", "physical_watch_id": ""}))

    def test_listing_id(self):
        self.assertEqual("188986", ACQ.listing_id({"candidate_id": "gen_126610ln_bobs_188986", "source_type": "authenticated_dealer",
                                                   "provenance_note": "Actual photos; SKU 188986", "source_url": "https://x/y.html"}))
        self.assertEqual("433916", ACQ.listing_id({"candidate_id": "gen_x", "source_type": "authenticated_dealer",
                                                   "source_url": "https://www.watchfinder.co.uk/watches/rolex/submariner/124060/433916"}))
        self.assertEqual("", ACQ.listing_id({"candidate_id": "rep_124060_vsf_1rslupa", "source_type": "reddit_qc",
                                             "image_album_url": "https://imgur.com/a/FjezTHL"}))

    def test_pools_share_one_schema_and_never_double_count_a_listing(self):
        with tempfile.TemporaryDirectory() as d:
            main, top = Path(d) / "main.csv", Path(d) / "topup.csv"
            main.write_text("candidate_id,class,model,factory,source_type,source_name,source_url,image_album_url,provenance_note,candidate_status\n"
                            "gen_124060_watchfinder_433916,genuine,124060,,authenticated_dealer,Watchfinder,https://w/watches/rolex/submariner/124060/433916,,x,candidate\n",
                            encoding="utf-8")
            top.write_text("physical_watch_id,family,model,class_tag,source_type,source_name,source_url,provenance_note,candidate_status\n"
                           "wf_124060_434346,submariner_12,124060,gen,authenticated_dealer,Watchfinder,https://w/watches/rolex/submariner/124060/434346,product code 434346,candidate\n"
                           "wf_124060_433916_again,submariner_12,124060,gen,authenticated_dealer,Watchfinder,https://w/Rolex/Submariner/124060/48245/item/433916,product code 433916,candidate\n",
                           encoding="utf-8")
            keep, skipped = ACQ.load_pools([main, top])
            self.assertEqual(["gen_124060_watchfinder_433916", "wf_124060_434346"], [r["candidate_id"] for r in keep])
            self.assertEqual("wf_124060_434346", keep[1]["physical_watch_id"])
            self.assertEqual("gen", ACQ.normalize_class(keep[1]["class"]))
            self.assertEqual("topup.csv", keep[1]["pool"])
            self.assertEqual(1, len(skipped))
            self.assertEqual("duplicate_listing_of:gen_124060_watchfinder_433916", skipped[0]["skip_reason"])

    def test_committed_topup_pool_is_consumable(self):
        root = Path(__file__).resolve().parents[3] / "docs" / "research"
        keep, skipped = ACQ.load_pools([root / "submariner_candidate_sources_2026-09-30.csv", root / "submariner_genuine_topup_2026-09-30.csv"])
        top = [r for r in keep if r["pool"] == "submariner_genuine_topup_2026-09-30.csv"]
        self.assertEqual(30, len(top))
        bobs_124060 = [r for r in top if r["model"] == "124060" and r["source_name"] == "Bobs Watches"]
        self.assertEqual(15, len(bobs_124060))
        self.assertEqual([], skipped)
        self.assertTrue(all(ACQ.normalize_class(r["class"]) == "gen" and ACQ.listing_id(r) for r in top))
        self.assertEqual(len(keep), len({r["physical_watch_id"] for r in keep}))

    def test_cached_image_bytes_are_reused_by_url(self):
        class NoNet:
            def get(self, url):
                raise AssertionError("must not download a cached URL")
        with tempfile.TemporaryDirectory() as d:
            images = Path(d)
            (images / "c1").mkdir()
            (images / "c1" / "01_ab.jpg").write_bytes(b"cached-bytes")
            index = {"https://x/a.jpg": "c1/01_ab.jpg"}
            ref = ACQ.ImageRef(url="https://x/a.jpg")
            self.assertEqual((b"cached-bytes", "https://x/a.jpg"), ACQ.image_bytes(ref, NoNet(), index, images))
            (images / ACQ.URL_INDEX).write_text(json.dumps(index))
            self.assertEqual(index, ACQ.load_url_index(images))

    def test_listing_images_exclude_navigation_and_other_watches(self):
        html = """<img src="/cdn-cgi/image/width=312/images/nav-thumb-rolex.jpg">
        <a href="/images/zUsed-Rolex-Submariner-126610-SKU188986.jpg">x</a>
        <img data-src="https://www.bobswatches.com/images/zUsed-Steel-Rolex-Submariner-126610LN-Black-Dial-SKU188986.jpg">
        <img src="/images/sUsed-Rolex-Submariner-126610LV-SKU193101.jpg">
        <img src="/cdn-cgi/image/width=520,quality=70/images/zUsed-Rolex-Submariner-126610-SKU188986.jpg">
        <a href="http://pinterest.com/pin/create/button/?url=x&amp;media=https%3A%2F%2Fwww.bobswatches.com%2Fimages%2FzUsed-SKU188986.jpg">p</a>"""
        got = ACQ.listing_image_urls(html, "https://www.bobswatches.com/listing.html", "188986")
        self.assertEqual(["https://www.bobswatches.com/images/zUsed-Rolex-Submariner-126610-SKU188986.jpg",
                          "https://www.bobswatches.com/images/zUsed-Steel-Rolex-Submariner-126610LN-Black-Dial-SKU188986.jpg"], got)


class DatasetTest(unittest.TestCase):
    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def save(self, img, name):
        p = self.tmp / name
        img.save(p, quality=95)
        return name

    def row(self, cid, name, sha, model="126610LN", cls="genuine", idx=1, url="", listing="", watch=""):
        return {"candidate_id": cid, "physical_watch_id": watch, "class_label": cls, "model": model, "factory": "",
                "image_index": idx, "image_url": url, "sha256": sha, "local_path": name, "listing_id": listing}

    def test_states_duplicates_and_independence(self):
        a = self.save(smooth(1), "a.jpg")
        a_small = self.save(smooth(1).resize((600, 467), Image.Resampling.LANCZOS), "a_small.jpg")   # near duplicate of a
        b = self.save(smooth(2), "b.jpg")
        c = self.save(smooth(3), "c.jpg")
        shared = self.save(smooth(4), "shared.jpg")
        tiny = self.save(smooth(5, 200, 150), "tiny.jpg")
        other_sku = self.save(smooth(6), "other.jpg")
        gmt = self.save(smooth(7), "gmt.jpg")
        eleven = self.save(smooth(8), "eleven.jpg")
        rows = [
            self.row("w1", a, "sa", idx=1),
            self.row("w1", a, "sa", idx=2),                      # exact duplicate inside one watch
            self.row("w1", a_small, "sa_small", idx=3),          # resized copy inside one watch
            self.row("w1", b, "sb", idx=4),
            self.row("w1", shared, "sshared", idx=5),            # same photo under two watches
            self.row("w2", shared, "sshared", idx=1),
            self.row("w2", c, "sc", idx=2, cls="replica"),       # conflicting class inside w2
            self.row("w3", tiny, "stiny", model="124060", cls="replica"),
            self.row("w4", other_sku, "sother", url="https://d/images/x-SKU999.jpg", listing="123"),
            self.row("w4", self.save(smooth(13), "own.jpg"), "sown", url="https://d/images/x-SKU123.jpg", listing="123", idx=2),
            self.row("w5", gmt, "sgmt", model="126710BLNR"),
            self.row("w6", eleven, "s11", model="116610LN"),
            self.row("w7", self.save(smooth(9), "d.jpg"), "sd", cls="replica", model="124060"),
            self.row("w7", self.save(smooth(10), "e.jpg"), "se", cls="replica", model="124060", idx=2),
        ]
        imgs, watches = D.build(rows, self.tmp)
        st = {(r["physical_watch_id"], r["image_index"]): (r["research_state"], r["reason"]) for r in imgs}
        self.assertEqual(("ACCEPT", ""), st[("w1", 1)])
        self.assertEqual(("REJECT", "reject_exact_duplicate"), st[("w1", 2)])
        self.assertEqual(("REJECT", "reject_near_duplicate"), st[("w1", 3)])
        self.assertEqual(("ACCEPT", ""), st[("w1", 4)])
        self.assertEqual(("QUARANTINE", "quarantine_cross_watch_duplicate"), st[("w1", 5)])
        self.assertEqual("QUARANTINE", st[("w2", 1)][0])
        self.assertEqual(("QUARANTINE", "quarantine_conflicting_watch_labels"), st[("w2", 2)])
        self.assertEqual(("REJECT", "reject_low_resolution"), st[("w3", 1)])
        self.assertEqual(("QUARANTINE", "quarantine_unverified_listing_image"), st[("w4", 1)])
        self.assertEqual(("ACCEPT", ""), st[("w4", 2)])
        self.assertEqual(("REJECT", "reject_unsupported_model"), st[("w5", 1)])
        self.assertEqual(("REJECT", "reject_unsupported_model"), st[("w6", 1)])
        ws = {w["physical_watch_id"]: w for w in watches}
        self.assertEqual("ACCEPT", ws["w1"]["research_state"])
        self.assertEqual("QUARANTINE", ws["w2"]["research_state"])
        self.assertEqual("QUARANTINE", ws["w3"]["research_state"])
        self.assertEqual("REJECT", ws["w6"]["research_state"])
        self.assertEqual("rep", ws["w7"]["class_label"])
        self.assertEqual("gen", ws["w1"]["class_label"])
        self.assertTrue(all(r["family"] == "submariner_12" for r in imgs if r["reason"] != "reject_unsupported_model"))
        # Two accepted photos of w1 and two of w7 are ONE independent sample each.
        self.assertEqual(3, D.independent_watch_count(imgs))   # w1, w4, w7
        self.assertEqual(2, int(ws["w1"]["images_accepted"]))

    def test_explicit_physical_watch_id_groups_candidates(self):
        a, b = self.save(smooth(11), "a.jpg"), self.save(smooth(12), "b.jpg")
        imgs, watches = D.build([self.row("c1", a, "s1", watch="W"), self.row("c2", b, "s2", watch="W")], self.tmp)
        self.assertEqual(1, len(watches))
        self.assertEqual("c1;c2", watches[0]["candidate_ids"])
        self.assertEqual(1, D.independent_watch_count(imgs))

    def test_unacquired_candidates_stay_visible(self):
        ws = D.add_unacquired([], [{"candidate_id": "gen_124060_watchfinder_1", "class": "genuine", "model": "124060",
                                    "acquisition_status": "fetch_error"},
                                   {"candidate_id": "x", "class": "genuine", "model": "116610LN", "acquisition_status": "resolved"}])
        self.assertEqual(["QUARANTINE", "REJECT"], [w["research_state"] for w in ws])
        self.assertEqual("quarantine_no_images:fetch_error", ws[0]["reason"])


class SplitTest(unittest.TestCase):
    def watches(self, n_gen=10, n_rep=10):
        out = [{"physical_watch_id": f"g{i}", "class_label": "gen", "model": "126610LN", "factory": ""} for i in range(n_gen)]
        out += [{"physical_watch_id": f"r{i}", "class_label": "rep", "model": "124060", "factory": "VSF"} for i in range(n_rep)]
        return out

    def test_deterministic_watch_level_partition(self):
        a, b = SP.make_split(self.watches()), SP.make_split(list(reversed(self.watches())))
        self.assertEqual(a, b)
        ids = [r["physical_watch_id"] for r in a]
        self.assertEqual(len(ids), len(set(ids)))
        parts = {p: sum(1 for r in a if r["partition"] == p) for p in SP.PARTS}
        self.assertEqual({"development": 12, "validation": 4, "holdout": 4}, parts)

    def test_split_is_locked_and_new_watches_are_unassigned(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d) / "split.csv"
            SP.create(self.watches(), p)
            with self.assertRaises(FileExistsError):
                SP.create(self.watches(), p)
            locked = SP.load(p)
            got = SP.assign(["g0", "brand_new_watch"], locked)
            self.assertEqual(locked["g0"], got["g0"])
            self.assertEqual(SP.UNASSIGNED, got["brand_new_watch"])

    def test_new_split_version_keeps_every_existing_assignment(self):
        with tempfile.TemporaryDirectory() as d:
            v1, v2 = Path(d) / "split_sub_v1.csv", Path(d) / "split_sub_v2.csv"
            SP.create(self.watches(), v1)
            newcomers = [{"physical_watch_id": f"n{i}", "class_label": "gen", "model": "124060", "factory": ""} for i in range(10)]
            new = SP.extend(v1, self.watches() + newcomers, v2, "v2")
            self.assertEqual({f"n{i}" for i in range(10)}, {r["physical_watch_id"] for r in new})
            a, b = SP.load(v1), SP.load(v2)
            for w, p in a.items():
                self.assertEqual(p, b[w])
            self.assertEqual(30, len(b))
            self.assertEqual({"development": 6, "validation": 2, "holdout": 2},
                             {p: sum(1 for r in new if r["partition"] == p) for p in SP.PARTS})
            with self.assertRaises(FileExistsError):
                SP.extend(v1, newcomers, v2, "v2")

    def test_committed_split_is_watch_level_and_sub_only(self):
        p = Path(__file__).resolve().parents[3] / "docs" / "research" / "submariner" / "split_sub_v1.csv"
        with p.open() as f:
            rows = list(csv.DictReader(f))
        ids = [r["physical_watch_id"] for r in rows]
        self.assertEqual(len(ids), len(set(ids)))
        self.assertTrue({r["partition"] for r in rows} <= set(SP.PARTS))
        self.assertTrue({r["model"] for r in rows} <= {"124060", "126610LN", "126610LV"})
        self.assertTrue({r["class_label"] for r in rows} <= {"gen", "rep"})
        self.assertIn("holdout", {r["partition"] for r in rows})


def rec(variant, sha="s1", dx=0.0, rot=0.0, detected=True, dial=True):
    lms = [{"landmark": "12", "kind": "triangle", "hour": 12.0, "detected": detected, "fit_path": "unconstrained_refit",
            "x": 500.0 + dx, "y": 200.0, "rho_r": 0.8, "dtheta_from_nominal_deg": 179.0 if variant == "x+1" else -179.5,
            "rotation_deg": rot, "width_over_r": 0.25, "gap_raw": 0.1, "apex_deg": 44.0},
           {"landmark": "date3", "kind": "date", "hour": 3.0, "detected": False, "reason": "not_measured_phase1"}]
    return {"sha256": sha, "variant": variant, "model": "126610LN", "dial_found": dial, "dial_cx": 500.0 + dx, "dial_cy": 500.0,
            "dial_r": 400.0, "fit_axis_ratio": 0.99, "edge_fit_valid": True, "dial_source": "edge_fit", "landmarks": lms if dial else []}


class TablesTest(unittest.TestCase):
    META = {"s1": {"family": "submariner_12", "model": "126610LN", "class_label": "gen", "physical_watch_id": "w1",
                   "partition": "development", "research_state": "ACCEPT", "candidate_id": "c1", "image_index": 1}}

    def test_long_format_and_identity(self):
        recs = [rec("orig"), rec("x+1", dx=4.0, rot=0.5), rec("s94", rot=-0.5)]
        imgs, lms = TB.image_rows(recs, self.META), TB.landmark_rows(recs, self.META)
        self.assertEqual(3, len(imgs))
        self.assertEqual(6, len(lms))
        for r in imgs + lms:
            for k in ("family", "model", "class_tag", "physical_watch_id", "partition", "sha256", "source_id", "variant"):
                self.assertIn(k, r)
            self.assertEqual("w1", r["physical_watch_id"])
            self.assertEqual("gen", r["class_tag"])

    def test_stability_is_min_max_spread_only(self):
        recs = [rec("orig"), rec("x+1", dx=4.0, rot=0.5), rec("s94", rot=-0.5)]
        imgs, lms = TB.image_rows(recs, self.META), TB.landmark_rows(recs, self.META)
        st = {r["landmark"]: r for r in TB.stability_rows(imgs, lms)}
        tri = st["12"]
        self.assertEqual(3, tri["variants_detected"])
        self.assertAlmostEqual(1.0, tri["rotation_deg_spread"])
        # Angles either side of +/-180 are one cluster (the old harness failed on 360 vs 0).
        self.assertAlmostEqual(1.5, tri["dtheta_from_nominal_deg_spread"])
        self.assertGreater(tri["centre_spread_over_r"], 0.0)
        self.assertLess(tri["centre_spread_over_r"], 0.011)
        self.assertEqual(0.0, st["dial"]["dial_radius_rel_spread"])
        for r in st.values():
            self.assertFalse(any(k in r for k in ("stable", "unstable", "pass", "fail")))

    def test_lost_landmarks_are_listed(self):
        recs = [rec("orig"), rec("x+1", detected=False)]
        st = {r["landmark"]: r for r in TB.stability_rows(TB.image_rows(recs, self.META), TB.landmark_rows(recs, self.META))}
        self.assertEqual("x+1", st["12"]["variants_missing"])

    def test_no_verdict_columns(self):
        for cols in (TB.IMAGE_COLS, TB.LANDMARK_COLS, TB.STABILITY_COLS):
            self.assertEqual([], TB.forbidden_columns(cols))
        self.assertEqual(["gap_att", "pose_label", "genuine_score", "stable_frame", "twelve_verdict"],
                         TB.forbidden_columns(["gap_att", "pose_label", "genuine_score", "stable_frame", "twelve_verdict", "seed_boundary_strength"]))
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaises(ValueError):
                TB.write(Path(d) / "x.csv", [], ["sha256", "align_att"])

    def test_driver_output_values_never_contain_verdict_labels(self):
        recs = [rec("orig")]
        rows = TB.image_rows(recs, self.META) + TB.landmark_rows(recs, self.META)
        text = json.dumps(rows)
        for token in ("CLEAR", "CHECK", "STRONG", "GOOD", "CORRECTABLE", "RETAKE", "UNASSESSABLE"):
            self.assertNotIn(token, text)

    def test_wrap180(self):
        self.assertEqual(-179.0, TB.wrap180(181.0))
        self.assertEqual(180.0, TB.wrap180(-180.0))
        self.assertAlmostEqual(0.0, TB.wrap180(360.0))


class DriverIsolationTest(unittest.TestCase):
    FORBIDDEN_SOURCE = (r"GmtHumanQcAnalyzerV2", r"GmtHumanPosePolicy", r"GmtDialLayout\.", r"GmtDialCrop\.", r"GmtHumanQcMath\.",
                        r"CanonicalGmtGeometryAnalyzer", r"noReadableDial", r"Attention\.", r"PoseLabel", r"GmtHumanSummary",
                        r"assessRotation", r"assessOffCentre", r"judgeRound", r"resampleStable", r"resampleGapStable",
                        r"resampleRotStable", r"measureStability", r"\bLoad\.", r"\bSuit\.", r"GmtMarkerPose\.estimate",
                        r"gapTrendAt12", r"lowReason", r"\.stable\b", r"detectorStable", r"EXPECTED_APEX_DEG", r"TriangleEdgeRefiner\.refine\(")

    def test_driver_source_calls_no_gmt_verdict_or_pose_policy(self):
        code = re.sub(r"/\*.*?\*/", "", DRIVER_SRC, flags=re.S)
        code = re.sub(r"//[^\n]*", "", code)
        for pat in self.FORBIDDEN_SOURCE:
            self.assertIsNone(re.search(pat, code), pat)

    def test_driver_labels_gmt_priors(self):
        self.assertIn("gmt_126710BLNR_master", DRIVER_SRC)
        self.assertIn("triangle_shape_prior=NOT_APPLIED", DRIVER_SRC)
        self.assertIn('j.str("priors",PRIORS)', DRIVER_SRC)

    def test_compiled_driver_pulls_in_no_gmt_decision_class(self):
        try:
            h = Harness(shards=1)
            classes = MS.compile_driver(h, Path(tempfile.mkdtemp()))
        except HarnessUnavailable as e:
            self.skipTest(f"JDK/OpenCV unavailable: {e}")
        rep = MS.isolation_report(classes)
        self.assertEqual([], rep["forbidden_present"], rep)
        self.assertIn("SubMeasure", rep["compiled_classes"])
        self.assertEqual("SELFTEST OK", MS.selftest(h, classes))
        shutil.rmtree(classes, ignore_errors=True)


if __name__ == "__main__":
    unittest.main()
