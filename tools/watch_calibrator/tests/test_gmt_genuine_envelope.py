import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
MATH = ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "watchalign" / "mobile" / "GmtHumanQcMath.java"


class GmtGenuineEnvelopePolicyTest(unittest.TestCase):
    """Regression guard for the product rule: a known genuine value is not a replica-QC warning."""

    @classmethod
    def setUpClass(cls):
        cls.text = MATH.read_text(encoding="utf-8")

    def constant(self, name):
        m = re.search(rf"\b{name}\s*=\s*([0-9.]+)", self.text)
        self.assertIsNotNone(m, f"missing GMT threshold {name}")
        return float(m.group(1))

    def test_gap_warning_is_below_observed_genuine_minimum(self):
        # gmt_fix_list_2026-09-27.md: stable genuine outer-edge gaps down to 0.081.
        self.assertLess(self.constant("LOW_CLEARANCE_ATTENTION"), 0.081)

    def test_rotation_warning_is_outside_observed_genuine_turns(self):
        # gmt_dial_crop_2026-09-28.md: corroborated genuine turns reached about 1.7 degrees.
        self.assertGreater(self.constant("ROTATION_CHECK_DEG"), 1.7)

    def test_level_top_skew_warning_is_outside_observed_genuine_skew(self):
        # Same audit: a genuine Phillips Bruce Wayne read +2.2 degrees with a level top edge.
        self.assertGreater(self.constant("SKEW_ONLY_MIN_DEG"), 2.2)

    def test_off_centre_warning_is_outside_observed_genuine_asymmetry(self):
        # Phillips genuine 126710BLNR reached 0.080 spacing asymmetry.
        self.assertGreater(self.constant("OFF_CENTRE_CHECK"), 0.080)

    def test_six_baton_warnings_are_outside_observed_genuine_values(self):
        # gmt_fix_list_2026-09-27.md: genuine centring reached 0.075 and rotation 1.79 degrees.
        self.assertGreater(self.constant("SIX_CENTRING_CHECK"), 0.075)
        self.assertGreater(self.constant("SIX_ROTATION_CHECK_DEG"), 1.79)

    def test_round_marker_warnings_are_outside_observed_genuine_values(self):
        # gmt_round_markers_2026-09-28.md: offset max 0.103, size difference max 0.072.
        self.assertGreater(self.constant("ROUND_OFFSET_CHECK"), 0.103)
        self.assertGreater(self.constant("ROUND_SIZE_CHECK"), 0.072)


if __name__ == "__main__":
    unittest.main()
