import unittest
from pathlib import Path
import sys

HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))

import phase2c_equivalence as p2c  # noqa: E402


class Phase2CEquivalenceTest(unittest.TestCase):
    def setUp(self):
        self.registry = p2c._reason_registry()

    @staticmethod
    def accepted(raw="1.000000", eligible="1.000000"):
        return {"state": "accepted", "raw_value": raw, "eligible_value": eligible, "reason_text": ""}

    @staticmethod
    def v2_accepted(raw=1.0, eligible=1.0):
        return {
            "state": "accepted", "raw_value": raw, "eligible_value": eligible,
            "reason_code": None, "reason_subject": None,
        }

    def test_identical_accepted_value_passes(self):
        key = ("images/a.jpg", "twelve.rotation_deg")
        result = p2c.compare_population(
            {key: self.accepted()}, {key: self.v2_accepted()}, self.registry
        )
        self.assertEqual(1, result["accepted_rows"])
        self.assertEqual(1, result["raw_values_compared"])
        self.assertEqual({}, result["allowed_state_changes"])

    def test_only_documented_twelve_unavailable_to_withheld_is_allowed(self):
        key = ("images/a.jpg", "twelve.gap_r")
        v1 = {key: {
            "state": "unavailable", "raw_value": "", "eligible_value": "",
            "reason_text": "metric geometry not measured",
        }}
        v2 = {key: {
            "state": "withheld", "raw_value": 0.02, "eligible_value": None,
            "reason_code": "sub12.gap_resize_unstable", "reason_subject": None,
        }}
        result = p2c.compare_population(v1, v2, self.registry)
        self.assertEqual({"twelve.gap_r": 1}, result["allowed_state_changes"])
        self.assertEqual(1, len(result["v1_reason_mappings"]))

    def test_same_transition_on_non_twelve_metric_fails(self):
        key = ("images/a.jpg", "baton.3_9_line_offset_r")
        v1 = {key: {
            "state": "unavailable", "raw_value": "", "eligible_value": "",
            "reason_text": "3/9 relation not measurable",
        }}
        v2 = {key: {
            "state": "withheld", "raw_value": 0.01, "eligible_value": None,
            "reason_code": "sub12.baton_resize_not_repeatable", "reason_subject": "baton:3",
        }}
        with self.assertRaisesRegex(p2c.Phase2CError, "unapproved state change"):
            p2c.compare_population(v1, v2, self.registry)

    def test_existing_v1_raw_and_accepted_eligible_must_match_exactly(self):
        key = ("images/a.jpg", "twelve.rotation_deg")
        with self.assertRaisesRegex(p2c.Phase2CError, "raw changed"):
            p2c.compare_population(
                {key: self.accepted("1.000000", "1.000000")},
                {key: self.v2_accepted(1.000001, 1.0)},
                self.registry,
            )
        with self.assertRaisesRegex(p2c.Phase2CError, "eligible changed"):
            p2c.compare_population(
                {key: self.accepted("1.000000", "1.000000")},
                {key: self.v2_accepted(1.0, 1.000001)},
                self.registry,
            )

    def test_uncoded_reason_is_never_persistable(self):
        key = ("images/a.jpg", "twelve.rotation_deg")
        v1 = {key: {
            "state": "withheld", "raw_value": "1.000000", "eligible_value": "",
            "reason_text": "current reliability policy withheld metric",
        }}
        v2 = {key: {
            "state": "withheld", "raw_value": 1.0, "eligible_value": None,
            "reason_code": "sub12.uncoded", "reason_subject": None,
        }}
        with self.assertRaisesRegex(p2c.Phase2CError, "not valid in persisted contracts"):
            p2c.compare_population(v1, v2, self.registry)


if __name__ == "__main__":
    unittest.main()
