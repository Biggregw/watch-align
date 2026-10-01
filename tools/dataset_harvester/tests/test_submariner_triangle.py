"""Research-only v2 Submariner 12-triangle detector: consensus, plausibility and evaluation."""
import math
import re
import sys
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

from harvester.config import HARNESS_DIR  # noqa: E402
from subresearch import triangle as T  # noqa: E402
from subresearch import triangle_eval as E  # noqa: E402

SRC = (HARNESS_DIR / "drivers" / "SubTriangle.java").read_text(encoding="utf-8")


def cand(cid, rank, x, y, w=100.0, score=1.0, rho=0.8, wr=0.25, gap=0.03, plausible=True, apex=44.0, hr=0.30):
    return {"cand_id": cid, "cand_rank": rank, "sel_score": score, "x": x, "y": y, "width_px": w, "rho_r": rho,
            "width_over_r": wr, "length_over_r": hr, "gap_over_r": gap, "plausible": plausible, "rotation_deg": 0.5, "apex_deg": apex,
            "fit_path": "sub_tri_v2:edge_refit"}


def rec(variant, cands, cx=500.0, r=400.0, source="edge_fit"):
    top = dict(cands[0], landmark="12", kind="triangle", detected=True) if cands else {"landmark": "12", "detected": False}
    return {"sha256": "s", "variant": variant, "dial_found": True, "dial_source": source, "edge_fit_valid": source == "edge_fit",
            "dial_cx": cx, "dial_cy": 500.0, "dial_r": r, "tri_candidates": cands, "landmarks": [top]}


def twelve(r):
    return next(l for l in r["landmarks"] if l["landmark"] == "12")


class ConsensusTest(unittest.TestCase):
    def test_the_same_physical_outline_is_selected_in_every_variant(self):
        good = lambda rank: cand(1, rank, 500, 180, score=2.0)
        wrong = lambda rank: cand(2, rank, 500, 120, score=1.0)   # e.g. the bezel triangle
        recs = [rec("orig", [good(1), wrong(2)]), rec("s94", [good(1), wrong(2)]), rec("x+1", [wrong(1), good(2)])]
        d = T.apply_consensus(recs)
        self.assertTrue(d["s"]["consensus"])
        for r in recs:
            sel = twelve(r)
            self.assertTrue(sel["detected"])
            self.assertEqual(180, sel["y"])
        self.assertFalse(twelve(recs[2])["selected_is_variant_top"])
        self.assertAlmostEqual(2 / 3, twelve(recs[0])["consensus_support"])
        self.assertEqual("12_top", recs[2]["landmarks"][-1]["landmark"])
        self.assertEqual(120, recs[2]["landmarks"][-1]["y"])   # each variant's own top is kept as 12_top

    def test_variants_with_another_dial_ring_or_fallback_dial_are_excluded(self):
        recs = [rec("orig", [cand(1, 1, 500, 180)]), rec("s94", [cand(1, 1, 500, 180)], r=460.0),
                rec("x+1", [cand(1, 1, 500, 180)], source="seed_circle"), rec("y+1", [cand(1, 1, 500, 180)], cx=503.0)]
        d = T.apply_consensus(recs)
        self.assertEqual(2, d["s"]["variants_edge_fit"])
        self.assertFalse(twelve(recs[1])["detected"])
        self.assertFalse(twelve(recs[2])["detected"])
        self.assertTrue(twelve(recs[3])["detected"])

    def test_implausible_candidates_are_never_selected(self):
        crown = cand(1, 1, 500, 300, rho=0.5)            # crown logo when a hand hides the triangle
        small = cand(2, 2, 500, 180, wr=0.08)
        far = cand(3, 3, 500, 170, gap=0.2)              # its "track" is the bezel's graduations
        roundish = cand(4, 4, 500, 175, apex=66.0, hr=0.10, wr=0.137)   # inscribed in a round marker
        handy = cand(5, 5, 500, 176, apex=21.0, hr=0.60, wr=0.22)       # merged with a hand
        weak = cand(6, 6, 500, 178, score=9.0)                           # transparent score above the ceiling
        recs = [rec("orig", [crown, small, far, roundish, handy, weak])]
        T.apply_consensus(recs)
        self.assertFalse(twelve(recs[0])["detected"])
        self.assertIn("no plausible", twelve(recs[0])["reason"])

    def test_no_edge_fitted_original_means_no_v2_triangle(self):
        recs = [rec("orig", [cand(1, 1, 500, 180)], source="seed_circle")]
        T.apply_consensus(recs)
        self.assertFalse(twelve(recs[0])["detected"])

    def test_plausibility_windows_match_the_java_detector(self):
        for name, val in (("PLAUS_RHO_LO", T.PLAUS_RHO[0]), ("PLAUS_RHO_HI", T.PLAUS_RHO[1]), ("PLAUS_W_LO", T.PLAUS_WIDTH[0]),
                          ("PLAUS_W_HI", T.PLAUS_WIDTH[1]), ("PLAUS_GAP_LO", T.PLAUS_GAP[0]), ("PLAUS_GAP_HI", T.PLAUS_GAP[1]),
                          ("PLAUS_APEX_LO", T.PLAUS_APEX[0]), ("PLAUS_APEX_HI", T.PLAUS_APEX[1]), ("PLAUS_HW_LO", T.PLAUS_HW[0]),
                          ("PLAUS_HW_HI", T.PLAUS_HW[1]), ("SCORE_MAX", T.SCORE_MAX)):
            m = re.search(name + r"=(-?[0-9.]+)", SRC)
            self.assertIsNotNone(m, name)
            self.assertEqual(val, float(m.group(1)), name)

    def test_no_gmt_apex_gate_in_the_v2_detector(self):
        code = re.sub(r"/\*.*?\*/", "", SRC, flags=re.S)
        code = re.sub(r"//[^\n]*", "", code)
        self.assertNotIn("EXPECTED_APEX_DEG", code)
        self.assertNotIn("APEX_TOLERANCE_DEG", code)
        self.assertNotIn("TriangleEdgeRefiner.refine(", code)
        # The only apex use is the broad plausibility window, never the GMT +/-2 deg gate.
        lo, hi = T.PLAUS_APEX
        self.assertGreaterEqual(hi - lo, 20.0)
        for banned in ("GmtHumanQcMath", "GmtHumanPosePolicy", "GmtHumanQcAnalyzerV2", "GmtDialLayout"):
            self.assertNotIn(banned, code)


class EvalTest(unittest.TestCase):
    def rows(self):
        imgs, lms = [], []
        for v, dx, rot in (("orig", 0, 0.5), ("s94", 2, 0.7), ("x+1", 0, 0.4)):
            imgs.append({"sha256": "a", "variant": v, "partition": "development", "dial_found": "True", "edge_fit_valid": "True",
                         "dial_source": "edge_fit", "dial_cx": "500", "dial_cy": "500", "dial_r": "400", "model": "124060",
                         "class_tag": "gen", "physical_watch_id": "w"})
            for name in E.DETECTORS:
                lms.append({"sha256": "a", "variant": v, "partition": "development", "landmark": name, "detected": "True",
                            "x": str(500 + dx), "y": "180", "rotation_deg": str(rot), "apex_deg": "44", "fit_path": "p",
                            "selected_is_variant_top": "True", "consensus_support": "1"})
        return imgs, lms

    def test_spreads_and_rates(self):
        photos, excl = E.per_photo(*self.rows(), "development")
        s = E.summary(photos, excl)
        self.assertEqual(1, s["photos_edge_fitted_original"])
        self.assertAlmostEqual(0.3, s["12"]["rotation_spread_median"])
        self.assertAlmostEqual(0.0, s["12"]["apex_spread_median"])
        self.assertEqual(0.0, s["12"]["path_switch_rate"])

    def test_holdout_is_refused(self):
        with self.assertRaises(SystemExit):
            E.per_photo([], [], "holdout")


if __name__ == "__main__":
    unittest.main()
