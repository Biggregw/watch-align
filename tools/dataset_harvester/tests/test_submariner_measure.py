import math
import sys
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

from submariner_measure import DialFrame, assign_markers, expected_marker_kind, med_mad, wrap_deg  # noqa: E402


class SubmarinerMeasureTest(unittest.TestCase):
    def test_marker_layout_differs_only_at_three_for_phase_one(self):
        self.assertEqual("baton", expected_marker_kind("124060", 3))
        self.assertEqual("date", expected_marker_kind("126610LN", 3))
        self.assertEqual("date", expected_marker_kind("126610LV", 3))
        for model in ("124060", "126610LN", "126610LV"):
            self.assertEqual("triangle", expected_marker_kind(model, 12))
            self.assertEqual("baton", expected_marker_kind(model, 6))
            self.assertEqual("baton", expected_marker_kind(model, 9))
            self.assertEqual("round", expected_marker_kind(model, 1))

    def test_dial_frame_normalisation_round_trips_cardinals(self):
        f = DialFrame(400, 300, 250, 220, 17, "test")
        for deg in (0, 90, 180, 270):
            x, y = f.point(deg, .74)
            xn, yn = f.norm(x, y)
            self.assertAlmostEqual(math.hypot(xn, yn), .74, places=6)
            got = math.degrees(math.atan2(xn, -yn)) % 360
            self.assertAlmostEqual(got, deg, places=6)

    def test_assign_markers_removes_common_roll(self):
        cands = []
        roll = 2.5
        for h in range(1, 13):
            if h == 3:
                continue
            deg = (0 if h == 12 else 30 * h) + roll
            p = math.radians(deg)
            cands.append({
                "cx": 0, "cy": 0, "xn": math.sin(p) * .74, "yn": -math.cos(p) * .74,
                "rho": .74, "clock_deg": deg % 360, "area_norm": .003,
                "circularity": .85 if h not in (6, 9, 12) else .45,
                "aspect": 2.0 if h in (6, 9) else 1.1,
                "solidity": .9, "rect_angle_deg": 0,
            })
        rows = assign_markers(cands, "126610LN")
        found = [r for r in rows if r.get("found")]
        self.assertGreaterEqual(len(found), 10)
        residuals = [abs(r["angle_residual_after_roll_deg"]) for r in found]
        self.assertLess(max(residuals), 1e-6)

    def test_robust_stats_ignore_non_finite(self):
        med, mad = med_mad([1.0, 2.0, 3.0, math.nan])
        self.assertEqual(2.0, med)
        self.assertEqual(1.0, mad)
        self.assertEqual(-179.0, wrap_deg(181.0))


if __name__ == "__main__":
    unittest.main()
