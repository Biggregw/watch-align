"""python3 -m unittest discover -s tools/research/corpus_qc -p 'test_*.py'"""
from __future__ import annotations

import csv
import math
import sys
import tempfile
import unittest
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import candidates as C  # noqa: E402
import corpus  # noqa: E402
import metrics as M  # noqa: E402
import split as S  # noqa: E402


def watches():
    out = []
    for i in range(11):
        out.append({"physical_watch_id": f"g{i}", "class_label": "gen", "model": "126710BLNR", "factory": "Rolex"})
    for i in range(9):
        out.append({"physical_watch_id": f"v{i}", "class_label": "rep", "model": "126710BLNR", "factory": "VSF"})
    out.append({"physical_watch_id": "c0", "class_label": "rep", "model": "126710BLNR", "factory": "C+"})
    out.append({"physical_watch_id": "b0", "class_label": "rep", "model": "126710BLRO", "factory": "VSF"})
    return out


class SplitTest(unittest.TestCase):
    def test_deterministic_watch_level_and_stratified(self):
        a, b = S.make_split(watches()), S.make_split(list(reversed(watches())))
        self.assertEqual([(w["physical_watch_id"], w["partition"]) for w in a], [(w["physical_watch_id"], w["partition"]) for w in b])
        self.assertEqual(len({w["physical_watch_id"] for w in a}), len(a))          # one partition per watch
        gen = [w["partition"] for w in a if w["class_label"] == "gen"]
        self.assertEqual((7, 2, 2), (gen.count("development"), gen.count("validation"), gen.count("holdout")))
        self.assertEqual(sum(w["partition"] == "development" for w in a), 14)
        self.assertTrue(all(w["partition"] in S.PARTS for w in a))

    def test_committed_split_is_what_the_code_produces(self):
        p = Path(__file__).resolve().parents[3] / "docs" / "research" / "corpus_qc" / "split_v1.csv"
        rows = list(csv.DictReader(p.open()))
        again = S.make_split([{k: r[k] for k in ("physical_watch_id", "class_label", "model", "factory")} for r in rows])
        self.assertEqual({r["physical_watch_id"]: r["partition"] for r in rows}, {r["physical_watch_id"]: r["partition"] for r in again})


class GeometryTest(unittest.TestCase):
    def rec(self, H=None, shift=(0, 0), R=300.0):
        cx, cy = 500.0, 400.0
        rnd = []
        for h in (1, 2, 4, 5, 7, 8, 10, 11):
            mx, my = M.ROUND_R * math.sin(math.radians(h * 30)), -M.ROUND_R * math.cos(math.radians(h * 30))
            p = np.array([mx, my])
            if H is not None:
                q = H @ np.array([mx, my, 1.0])
                p = q[:2] / q[2]
            rnd.append({"hour": h, "found": True, "x": cx + R * p[0] + shift[0], "y": cy + R * p[1] + shift[1],
                        "mx": mx, "my": my, "r": 12.0})   # as the driver reports: master at the marker radius
        return {"dial_cx": cx, "dial_cy": cy, "dial_a": R, "dial_b": R, "round": rnd, "dial_angle_deg": 0.0}

    def test_projective_recovers_keystone(self):
        H = np.array([[1.0, 0.02, 0.0], [0.0, 0.97, 0.0], [0.0, 0.08, 1.0]])
        pr = M.projective(self.rec(H))
        self.assertAlmostEqual(pr["keystone_y"], 0.08, delta=1e-6)
        self.assertLess(pr["homog_resid"], 1e-6)
        self.assertLess(M.projective(self.rec())["affine_resid"], 1e-9)

    def test_bias_and_pooled_sd(self):
        b = M.bias(self.rec(shift=(0, -6)))           # layout shifted up: upper markers further from the centre
        self.assertGreater(b["tb_bias"], 0)
        self.assertAlmostEqual(b["lr_bias"], 0, delta=1e-9)
        sd, n, df = M.pooled_within_sd({"a": [1.0, 3.0], "b": [5.0, 5.0], "c": [9.0]})
        self.assertEqual((n, df), (2, 2))
        self.assertAlmostEqual(sd, 1.0)


class CandidateTest(unittest.TestCase):
    def test_candidates_only_withdraw_verdicts(self):
        fz = {"C1_rehaut_asym_gate": {"key": "rh_asym", "above": 0.5}, "C2_marker_tilt_gate": {"key": "mpose_tilt", "above": 8.0},
              "C3_resize_spread_gate": {"key": "stab_gap_spread", "above": 0.05}, "C6_rotation_flag_margin_deg": 0.6}
        r = {"gap_att": "CLEAR", "align_att": "CHECK", "six_att": "UNASSESSABLE", "nine_att": "STRONG", "rh_asym": 0.9,
             "mpose_tilt": 12.0, "stab_gap_spread": 0.1, "rot_deg": 1.3}
        base = C.apply(r, "baseline", fz)
        for c in C.CANDS[1:]:
            after = C.apply(r, c, fz)
            for f in C.FIELDS:
                self.assertTrue(after[f] == base[f] or after[f] == "UNASSESSABLE", (c, f))
        self.assertEqual("UNASSESSABLE", C.apply(r, "C6_rotation_flag_margin", fz)["align_att"])


class CorpusTest(unittest.TestCase):
    def test_incomplete_corpus_is_refused(self):
        d = Path(tempfile.mkdtemp())
        with (d / "manifest.csv").open("w", newline="") as f:
            w = csv.DictWriter(f, fieldnames=["local_path", "sha256", "dataset_decision", "physical_watch_id", "class_label", "model",
                                              "factory", "suitable", "duplicate_of", "measurement_status", "suitability_reasons", "sample_role"])
            w.writeheader()
            w.writerow({"local_path": "missing.jpg", "sha256": "ab" * 32, "dataset_decision": "ACCEPT", "physical_watch_id": "w",
                        "class_label": "gen", "model": "126710BLNR", "factory": "Rolex", "suitable": "yes", "duplicate_of": "",
                        "measurement_status": "measured", "suitability_reasons": "", "sample_role": "population"})
        with self.assertRaises(RuntimeError):
            corpus.load(d)


if __name__ == "__main__":
    unittest.main()
