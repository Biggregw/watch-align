import copy
import tempfile
import unittest
from pathlib import Path
import sys

HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))

import calibration_outcome_v1 as outcome  # noqa: E402


class CalibrationOutcomeV1Test(unittest.TestCase):
    METRIC = "twelve.rotation_deg"

    @staticmethod
    def contract(partition, cls, photo_key, sha, value=1.25):
        header = {
            "snapshot_id": "a" * 64,
            "claimed_model": "124060",
            "family": "submariner_12",
            "partition": partition,
            "class_label": cls,
            "measurement_fingerprint": "b" * 64,
        }
        photo = {
            "photo_key": photo_key,
            "image_sha256": sha,
            "physical_watch_id": f"watch-{partition}-{cls}",
        }
        metric = {
            "state": "accepted",
            "eligible_value": value,
        }
        return {
            "header": header,
            "photos": {photo_key: photo},
            "metrics": {(photo_key, CalibrationOutcomeV1Test.METRIC): metric},
        }

    def contracts(self):
        groups = [
            ("development", "gen", "images/gen/dev.jpg", "1" * 64, 1.0),
            ("validation", "gen", "images/gen/val.jpg", "2" * 64, 1.1),
            ("holdout", "gen", "images/gen/hold.jpg", "3" * 64, 1.2),
            ("development", "rep", "images/rep/dev.jpg", "4" * 64, 1.3),
            ("validation", "rep", "images/rep/val.jpg", "5" * 64, 1.4),
        ]
        return {
            (partition, cls): self.contract(partition, cls, photo, sha, value)
            for partition, cls, photo, sha, value in groups
        }

    def calibration(self):
        return {
            "model": "124060",
            "family": "submariner_12",
            "state": "GENUINE_ENVELOPE_READY",
            "method": "all_reliable_genuine_photo_envelope_v1",
            "metrics": {
                self.METRIC: {
                    "metric": self.METRIC,
                    "app_key": self.METRIC,
                    "status": "CALIBRATED_GENUINE_ENVELOPE",
                    "genuine_photos": 2,
                    "obvious_photo_outliers_rejected": 1,
                    "obvious_photo_outliers": [{
                        "physical_watch_id": "watch-holdout-gen",
                        "partition": "holdout",
                        "local_path": "images/gen/hold.jpg",
                        "image_sha256": "3" * 64,
                        "value": 1.2,
                        "reason": "test outlier",
                    }],
                }
            },
        }

    def test_outcomes_reconcile_accepted_measurements_with_engine_result(self):
        records = outcome.build_records(
            self.contracts(), self.calibration(), calibration_sha256="c" * 64
        )
        outcome.validate_records(records)
        rows = records[1:]
        self.assertEqual(5, len(rows))
        admitted = [r for r in rows if r["admission_state"] == "admitted"]
        excluded = [r for r in rows if r["admission_state"] == "excluded"]
        self.assertEqual(3, len(admitted))
        self.assertEqual(2, len(excluded))
        self.assertEqual(2, sum(r["outcome"] == "retained" for r in admitted))
        rejected = [r for r in admitted if r["outcome"] == "rejected_outlier"]
        self.assertEqual(1, len(rejected))
        self.assertEqual("images/gen/hold.jpg", rejected[0]["photo_key"])
        self.assertEqual("calib.obvious_within_watch_outlier", rejected[0]["outcome_reason_code"])
        self.assertTrue(all(r["admission_reason_code"] == "calib.replica_stress_only" for r in excluded))
        self.assertTrue(all(r["outcome"] is None for r in excluded))

    def test_engine_count_or_outlier_value_mismatch_fails_closed(self):
        bad = self.calibration()
        bad["metrics"][self.METRIC]["genuine_photos"] = 1
        with self.assertRaisesRegex(outcome.CalibrationOutcomeError, "retained accepted count"):
            outcome.build_records(self.contracts(), bad, calibration_sha256="c" * 64)

        bad = self.calibration()
        bad["metrics"][self.METRIC]["obvious_photo_outliers"][0]["value"] = 9.9
        with self.assertRaisesRegex(outcome.CalibrationOutcomeError, "does not match accepted value"):
            outcome.build_records(self.contracts(), bad, calibration_sha256="c" * 64)

    def test_missing_group_or_measurement_identity_drift_fails_closed(self):
        contracts = self.contracts()
        del contracts[("validation", "rep")]
        with self.assertRaisesRegex(outcome.CalibrationOutcomeError, "groups mismatch"):
            outcome.build_records(contracts, self.calibration(), calibration_sha256="c" * 64)

        contracts = self.contracts()
        contracts[("validation", "gen")]["header"]["measurement_fingerprint"] = "d" * 64
        with self.assertRaisesRegex(outcome.CalibrationOutcomeError, "identity changes"):
            outcome.build_records(contracts, self.calibration(), calibration_sha256="c" * 64)

    def test_outcome_jsonl_is_canonical_and_round_trips(self):
        records = outcome.build_records(
            self.contracts(), self.calibration(), calibration_sha256="c" * 64
        )
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "outcomes.jsonl"
            outcome.write_records(path, records)
            original = path.read_bytes()
            reread = outcome.read_canonical(path)
            self.assertEqual(records, reread)
            outcome.write_records(path, reread)
            self.assertEqual(original, path.read_bytes())

    def test_tampered_outcome_invariant_is_rejected(self):
        records = outcome.build_records(
            self.contracts(), self.calibration(), calibration_sha256="c" * 64
        )
        tampered = copy.deepcopy(records)
        row = next(r for r in tampered[1:] if r["admission_state"] == "excluded")
        row["outcome"] = "retained"
        with self.assertRaisesRegex(outcome.CalibrationOutcomeError, "excluded stress-only invariant"):
            outcome.validate_records(tampered)


if __name__ == "__main__":
    unittest.main()
