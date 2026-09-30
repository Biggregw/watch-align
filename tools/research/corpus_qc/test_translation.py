"""python3 -m unittest discover -s tools/research/corpus_qc -p 'test_*.py'"""
from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import stability as ST  # noqa: E402
import translate as T  # noqa: E402


class TranslateTest(unittest.TestCase):
    def test_content_moves_by_the_recorded_offset_and_fill_is_flat(self):
        a = np.full((100, 200, 3), 40, np.uint8)
        a[45:56, 95:106] = 250                                          # 11x11 bright block centred at (100, 50)
        with tempfile.TemporaryDirectory() as d:
            src, dst = Path(d) / "s.png", Path(d) / "t.jpg"
            Image.fromarray(a).save(src)
            dx, dy = T.translate(str(src), "x+2", dst)
            self.assertEqual((dx, dy), (4, 0))
            b = np.asarray(Image.open(dst)).astype(int)
            self.assertEqual(b.shape, a.shape)
            cols = np.where(b[50, :, 0] > 150)[0]
            self.assertEqual(round(float(cols.mean())), 104)            # the block moved +4 px in x
            strip = b[:, :4]                                              # uncovered strip: flat border colour
            self.assertLess(strip.std(), 2.0)
            self.assertLess(abs(strip.mean() - 40), 3)
            dx, dy = T.translate(str(src), "y-1", dst)
            self.assertEqual((dx, dy), (0, -1))

    def test_t0_is_an_untranslated_reencode(self):
        a = np.random.default_rng(0).integers(0, 255, (60, 80, 3), dtype=np.uint8)
        with tempfile.TemporaryDirectory() as d:
            src, dst = Path(d) / "s.png", Path(d) / "t.jpg"
            Image.fromarray(a).save(src)
            self.assertEqual(T.translate(str(src), "t0", dst), (0, 0))


class StabilityTest(unittest.TestCase):
    def test_to_orig_removes_scale_offset_and_translation(self):
        r = {"to_orig_scale": 2.0, "to_orig_ox": 10.0, "to_orig_oy": 5.0, "dx": 8, "dy": -4}
        self.assertEqual(ST.to_orig(r, 3.0, 4.0), (3 * 2 + 10 - 8, 4 * 2 + 5 + 4))

    def test_consensus_is_conservative_and_ignores_unassessable(self):
        self.assertEqual(ST.consensus(["GOOD", "GOOD", "RETAKE"]), "GOOD")
        self.assertEqual(ST.consensus(["GOOD", "RETAKE", "RETAKE"]), "RETAKE")
        self.assertEqual(ST.consensus(["GOOD", "RETAKE"]), "RETAKE")             # tie -> the stricter label
        self.assertEqual(ST.consensus(["UNASSESSABLE", "CORRECTABLE", "GOOD"]), "CORRECTABLE")
        self.assertEqual(ST.consensus(["UNASSESSABLE"]), "UNASSESSABLE")

    def test_gate_keep(self):
        row = {"stability": 0.625, "stable_in": {"x+2": True, "x-2": False, "y-2": True}}
        self.assertTrue(ST.gate_keep(row, {"kind": "fraction", "min_fraction": 0.5}))
        self.assertFalse(ST.gate_keep(row, {"kind": "fraction", "min_fraction": 0.75}))
        self.assertFalse(ST.gate_keep(row, {"kind": "all_of", "shifts": ["x-2", "y-2"]}))
        self.assertTrue(ST.gate_keep(row, {"kind": "all_of", "shifts": ["x+2", "y-2"]}))
        self.assertFalse(ST.gate_keep(row, {"kind": "all_of", "shifts": ["y+1"]}))   # missing view = not stable

    def test_every_candidate_rule_keeps_a_fully_stable_flag(self):
        row = {"stability": 1.0, "stable_in": {k: True for k in ST.SHIFTS}}
        for rule in ST.candidate_rules():
            self.assertTrue(ST.gate_keep(row, rule), rule)


if __name__ == "__main__":
    unittest.main()
