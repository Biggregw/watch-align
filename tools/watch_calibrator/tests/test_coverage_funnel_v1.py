import copy
import tempfile
import unittest
from pathlib import Path
import sys

HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))

import calibration_outcome_v1 as outcome  # noqa: E402
import coverage_funnel_v1 as funnel  # noqa: E402


class CoverageFunnelV1Test(unittest.TestCase):
    METRIC = "twelve.rotation_deg"

    @staticmethod
    def _stages():
        return [
            {"stage": "readable", "outcome": "pass", "reason": None, "diagnostics": {}},
            {"stage": "preflight", "outcome": "not_applicable",
             "reason": {"code": "core.stage_not_applicable", "subject": None, "detail": None},
             "diagnostics": {}},
            {"stage": "region", "outcome": "pass", "reason": None, "diagnostics": {}},
            {"stage": "geometry", "outcome": "pass", "reason": None, "diagnostics": {}},
            {"stage": "layout", "outcome": "compatible", "reason": None, "diagnostics": {}},
        ]

    @classmethod
    def contract(cls, partition, label, photo_key, sha, watch, source, state, value=None, reason=None):
        photo = {
            "record_type": "photo",
            "photo_key": photo_key,
            "image_sha256": sha,
            "physical_watch_id": watch,
            "provenance": {
                "source_type": "dealer" if label == "gen" else "forum_qc",
                "source_name": source,
                "source_url": "https://example.invalid/item",
                "image_url": "https://example.invalid/image.jpg",
                "listing_id": watch,
            },
            "photo_stages": cls._stages(),
            "layout_evidence": {"compatibility": "compatible"},
        }
        metric = {
            "record_type": "metric",
            "photo_key": photo_key,
            "metric_id": cls.METRIC,
            "state": state,
            "raw_value": value if state in {"accepted", "withheld"} else None,
            "eligible_value": value if state == "accepted" else None,
            "reason_code": reason if state != "accepted" else None,
        }
        return {
            "header": {
                "snapshot_id": "a" * 64,
                "claimed_model": "124060",
                "family": "submariner_12",
                "partition": partition,
                "class_label": label,
                "measurement_fingerprint": "b" * 64,
            },
            "photos": {photo_key: photo},
            "metrics": {(photo_key, cls.METRIC): metric},
        }

    def contracts(self):
        return {
            ("development", "gen"): self.contract(
                "development", "gen", "images/gen/dev.jpg", "1" * 64,
                "g-dev", "Dealer A", "accepted", 1.0
            ),
            ("validation", "gen"): self.contract(
                "validation", "gen", "images/gen/val.jpg", "2" * 64,
                "g-val", "Dealer A", "accepted", 1.1
            ),
            ("holdout", "gen"): self.contract(
                "holdout", "gen", "images/gen/hold.jpg", "3" * 64,
                "g-hold", "Dealer B", "accepted", 1.2
            ),
            ("development", "rep"): self.contract(
                "development", "rep", "images/rep/dev.jpg", "4" * 64,
                "r-dev", "r/RepTimeQC", "accepted", 1.3
            ),
            ("validation", "rep"): self.contract(
                "validation", "rep", "images/rep/val.jpg", "5" * 64,
                "r-val", "r/RepTimeQC", "withheld", 1.4, "sub12.baton_resize_not_repeatable"
            ),
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
                    "genuine_watches": 3,
                    "genuine_sources": 2,
                    "genuine_by_source": {"Dealer A": 2, "Dealer B": 1},
                    "obvious_photo_outliers_rejected": 1,
                    "obvious_photo_outliers": [{
                        "physical_watch_id": "g-hold",
                        "partition": "holdout",
                        "local_path": "images/gen/hold.jpg",
                        "image_sha256": "3" * 64,
                        "value": 1.2,
                        "reason": "test outlier",
                    }],
                    "replica_stress": {
                        "photos": 1,
                        "watches": 1,
                        "outside_clear_photos": 0,
                        "outside_check_photos": 0,
                        "outside_clear_rate": 0.0,
                        "outside_check_rate": 0.0,
                    },
                    "utility": "NO_REPLICA_SEPARATION_OBSERVED",
                }
            },
        }

    def config(self):
        return {
            "model": "124060",
            "family": "submariner_12",
            "calibration_policy": {
                "genuine_envelope_min_watches": 2,
                "genuine_envelope_min_sources": 2,
            },
            "discovery": {
                "genuine_source_diversity": {
                    "minimum_sources": 2,
                    "minimum_watches_per_source": 1,
                    "max_single_source_share": 0.8,
                }
            },
        }

    def outcome_records(self):
        return outcome.build_records(
            self.contracts(), self.calibration(), calibration_sha256="c" * 64
        )

    def acquired_rows(self):
        return [
            {
                "local_path": "images/rep/dev.jpg",
                "physical_watch_id": "r-dev",
                "factory": "VSF",
                "acquisition_status": "acquired",
                "exact_duplicate_of": "",
            },
            {
                "local_path": "images/rep/val.jpg",
                "physical_watch_id": "r-val",
                "factory": "Clean",
                "acquisition_status": "acquired",
                "exact_duplicate_of": "",
            },
        ]

    def test_full_funnel_reconciles_and_separates_sufficiency_from_usefulness(self):
        report = funnel.build_report(
            self.contracts(),
            self.outcome_records(),
            self.calibration(),
            self.config(),
            acquired_rows=self.acquired_rows(),
            candidate_summary_rows=[
                {"acquisition_status": "resolved", "images_acquired": "2"},
                {"acquisition_status": "fetch_error", "images_acquired": "0"},
            ],
        )
        metric = report["metrics"][self.METRIC]
        totals = metric["coverage"]["totals"]
        self.assertEqual(5, totals["population"])
        self.assertEqual(4, totals["accepted"])
        self.assertEqual(1, totals["withheld"])
        self.assertEqual(0, totals["unavailable"])
        self.assertEqual(3, totals["admitted"])
        self.assertEqual(1, totals["excluded"])
        self.assertEqual(2, totals["retained"])
        self.assertEqual(1, totals["rejected_outlier"])

        suff = metric["calibration_sufficiency"]
        self.assertTrue(suff["engine_calibration_sufficient"])
        self.assertEqual(3, suff["contributing_watches"])
        self.assertEqual(2, suff["contributing_sources"])
        self.assertAlmostEqual(2 / 3, suff["dominant_source_share_by_watches"])
        self.assertTrue(suff["criteria"]["acquisition_source_dominance_met"])
        self.assertEqual(1, suff["partition_support"]["holdout"]["rejected_outlier_photos"])

        usefulness = metric["qc_usefulness"]
        self.assertEqual(1, usefulness["accepted_replica_photos"])
        self.assertEqual(1, usefulness["accepted_replica_watches"])
        self.assertEqual(["VSF"], usefulness["factories"])
        self.assertEqual("NO_REPLICA_SEPARATION_OBSERVED", usefulness["utility"])
        self.assertTrue(report["acquisition"]["stage0_acquisition_ledger_available"])
        self.assertEqual(1, report["acquisition"]["candidates_without_images"])

    def test_missing_or_extra_outcome_fails_closed(self):
        rows = self.outcome_records()
        bad = [rows[0]] + rows[2:]
        with self.assertRaisesRegex(funnel.CoverageFunnelError, "accepted/outcome identity mismatch"):
            funnel.build_report(self.contracts(), bad, self.calibration(), self.config())

        rows = copy.deepcopy(self.outcome_records())
        extra = copy.deepcopy(rows[1])
        extra["photo_key"] = "images/gen/not-in-contract.jpg"
        rows.append(extra)
        with self.assertRaises((funnel.CoverageFunnelError, outcome.CalibrationOutcomeError)):
            funnel.build_report(self.contracts(), rows, self.calibration(), self.config())

    def test_nonaccepted_reason_and_replica_admission_invariants_fail_closed(self):
        contracts = self.contracts()
        contracts[("validation", "rep")]["metrics"][("images/rep/val.jpg", self.METRIC)]["reason_code"] = None
        with self.assertRaisesRegex(funnel.CoverageFunnelError, "non-accepted metric has no reason"):
            funnel.build_report(contracts, self.outcome_records(), self.calibration(), self.config())

        rows = copy.deepcopy(self.outcome_records())
        rep = next(r for r in rows[1:] if r["class_label"] == "rep")
        rep["admission_state"] = "admitted"
        rep["admission_reason_code"] = None
        rep["outcome"] = "retained"
        with self.assertRaises(outcome.CalibrationOutcomeError):
            funnel.build_report(self.contracts(), rows, self.calibration(), self.config())

    def test_report_is_canonical_and_round_trips(self):
        report = funnel.build_report(
            self.contracts(), self.outcome_records(), self.calibration(), self.config()
        )
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "coverage.json"
            funnel.write_report(path, report)
            original = path.read_bytes()
            reread = funnel.read_canonical(path)
            self.assertEqual(report, reread)
            funnel.write_report(path, reread)
            self.assertEqual(original, path.read_bytes())


if __name__ == "__main__":
    unittest.main()
