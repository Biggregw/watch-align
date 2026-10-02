import sys
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

from harvester.config import SUPPORTED_MODELS, UNSUPPORTED_GMT_MODELS  # noqa: E402
from harvester.families import GMT_12, SUBMARINER_12, family_for_model, models_for_family  # noqa: E402


class FamilyConfigTest(unittest.TestCase):
    def test_gmt_backwards_compatibility(self):
        self.assertEqual(GMT_12.models, SUPPORTED_MODELS)
        self.assertEqual(GMT_12.unsupported_predecessors, UNSUPPORTED_GMT_MODELS)

    def test_phase_one_submariners_are_separate_family(self):
        self.assertEqual(("124060", "126610LN", "126610LV"), models_for_family("submariner_12"))
        self.assertEqual("submariner_12", family_for_model("124060").key)
        self.assertEqual("submariner_12", family_for_model("126610ln").key)
        self.assertNotIn("116610LN", SUBMARINER_12.models)
        self.assertIn("116610LN", SUBMARINER_12.unsupported_predecessors)

    def test_sub_models_do_not_leak_into_gmt_supported_models(self):
        for model in SUBMARINER_12.models:
            self.assertNotIn(model, SUPPORTED_MODELS)


if __name__ == "__main__":
    unittest.main()
