import sys
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

from harvester.config import SUPPORTED_MODELS, UNSUPPORTED_GMT_MODELS  # noqa: E402
from harvester.families import FAMILIES, GMT_12, SUBMARINER_12, family_for_model, models_for_family  # noqa: E402


class FamilyConfigTest(unittest.TestCase):
    def test_gmt_backwards_compatibility(self):
        self.assertEqual(GMT_12.models, SUPPORTED_MODELS)
        self.assertEqual(GMT_12.unsupported_predecessors, UNSUPPORTED_GMT_MODELS)

    def test_gmt_family_constants_unchanged(self):
        # Snapshot: any change to the GMT family must be deliberate and reviewed.
        self.assertEqual(("126710BLNR", "126710BLRO", "126710GRNR", "126711CHNR", "126713GRNR",
                          "126715CHNR", "126718GRNR", "126719BLRO", "126720VTNR", "126729VTNR"), GMT_12.models)
        self.assertEqual(("16710", "16713", "16718", "16760", "116710", "116713", "116718", "116719", "116759"),
                         GMT_12.unsupported_predecessors)
        self.assertEqual("gmt_12", GMT_12.key)
        self.assertEqual({"gmt_12", "submariner_12"}, set(FAMILIES))

    def test_phase_one_submariners_are_separate_family(self):
        self.assertEqual(("124060", "126610LN", "126610LV"), models_for_family("submariner_12"))
        self.assertEqual("submariner_12", family_for_model("124060").key)
        self.assertEqual("submariner_12", family_for_model("126610ln").key)
        self.assertNotIn("116610LN", SUBMARINER_12.models)
        self.assertIn("116610LN", SUBMARINER_12.unsupported_predecessors)

    def test_eleven_series_references_do_not_enter_submariner_12(self):
        self.assertIsNone(family_for_model("116610LN"))
        for ref in ("114060", "116610LN", "116610LV", "116610ln", "16610"):
            self.assertIsNone(family_for_model(ref), ref)
            self.assertNotIn(ref.upper(), SUBMARINER_12.models)

    def test_sub_models_do_not_leak_into_gmt_supported_models(self):
        for model in SUBMARINER_12.models:
            self.assertNotIn(model, SUPPORTED_MODELS)
            self.assertEqual("submariner_12", family_for_model(model).key)
        for model in GMT_12.models:
            self.assertEqual("gmt_12", family_for_model(model).key)


if __name__ == "__main__":
    unittest.main()
