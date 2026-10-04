import copy
import json
import re
import unittest
from pathlib import Path
import sys

HERE = Path(__file__).resolve().parents[1]
REPO = HERE.parents[1]
sys.path.insert(0, str(HERE))

import catalogue_registry as cr  # noqa: E402
import measurement_contract_v2 as v2  # noqa: E402
import submariner12_measurement_adapter_v2 as adapter  # noqa: E402


class MeasurementContractV2HardeningTest(unittest.TestCase):
    def setUp(self):
        self.config = json.loads(
            (REPO / "calibration" / "models" / "124060.json").read_text(encoding="utf-8")
        )
        self.metric_ids = sorted(m["app_key"] for m in self.config["calibration_metrics"])
        self.request = {
            "photo_key": "images/gen/watch-1/photo-01.jpg",
            "image_sha256": "a" * 64,
            "physical_watch_id": "watch-1",
            "workspace_path": Path("/tmp/not-persisted.jpg"),
            "provenance": {
                "source_type": "dealer_listing",
                "source_name": "Example",
                "source_url": "https://example.invalid/watch-1",
                "image_url": "https://example.invalid/watch-1.jpg",
                "listing_id": "watch-1",
            },
        }
        self.core = cr.load_reason_catalogue(cr.CORE_REASON_CATALOGUE, expected_namespace="core")
        self.sub12 = cr.load_reason_catalogue(cr.SUB12_REASON_CATALOGUE, expected_namespace="sub12")
        self.metrics = cr.load_metric_catalogue(
            cr.SUB12_124060_METRIC_CATALOGUE,
            expected_family="submariner_12",
            expected_model="124060",
        )

    def decision(self):
        metrics = []
        for i, metric_id in enumerate(self.metric_ids):
            accepted = i == 0
            metrics.append({
                "metric_id": metric_id,
                "state": "accepted" if accepted else "unavailable",
                "terminal_stage": "metric.reliability" if accepted else "metric.raw",
                "origin": "metric",
                "reason": None if accepted else {
                    "code": "sub12.triangle_not_found",
                    "subject": None,
                    "detail": "not found",
                },
                "secondary_reasons": [],
                "raw_value": 0.125 if accepted else None,
                "eligible_value": 0.125 if accepted else None,
                "raw_support": {},
                "eligible_support": {},
                "diagnostics": {},
            })
        return {
            "photo_key": self.request["photo_key"],
            "physical_watch_id": self.request["physical_watch_id"],
            "model": "124060",
            "stages": [
                {"stage": "readable", "outcome": "pass", "reason": None, "diagnostics": {}},
                {
                    "stage": "preflight",
                    "outcome": "not_applicable",
                    "reason": {"code": "core.stage_not_applicable", "subject": None, "detail": None},
                    "diagnostics": {},
                },
                {"stage": "region", "outcome": "pass", "reason": None, "diagnostics": {"dial_source": "AUTO_EDGE_FIT"}},
                {"stage": "geometry", "outcome": "pass", "reason": None, "diagnostics": {"dial_reproducible": True}},
                {"stage": "layout", "outcome": "compatible", "reason": None, "diagnostics": {}},
            ],
            "layout": {
                "expected": {"layout_id": "sub12.no_date_v1", "version": 1},
                "observations": [{
                    "feature": "sub12.baton_at_3",
                    "present": True,
                    "high_confidence": True,
                    "detector_id": "sub12.baton_status",
                    "detector_version": 1,
                    "supports": ["sub12.no_date_v1"],
                    "contradicts": ["sub12.date_v1"],
                }],
                "hypotheses": [
                    {"layout_id": "sub12.no_date_v1", "state": "supported", "decided_by": ["sub12.baton_at_3"]},
                    {"layout_id": "sub12.date_v1", "state": "contradicted", "decided_by": ["sub12.baton_at_3"]},
                ],
                "compatibility": "compatible",
                "reliability": "high",
                "quarantined": False,
            },
            "metrics": metrics,
        }

    def records(self):
        return adapter.build_records(
            self.config,
            partition="development",
            cls="gen",
            snapshot_id="snapshot-proof",
            requested=[self.request],
            decisions=[self.decision()],
            checkout_sha="1" * 40,
        )

    def validate(self, records):
        return v2.validate_contract(
            records,
            metric_catalogue=self.metrics,
            reason_catalogues=[self.core, self.sub12],
            expected_identity={
                "snapshot_id": "snapshot-proof",
                "claimed_model": "124060",
                "family": "submariner_12",
                "partition": "development",
                "class_label": "gen",
                "adapter": adapter.ADAPTER,
                "reliability_policy": adapter.RELIABILITY_POLICY,
            },
            expected_photos=[self.request],
        )

    def test_complete_contract_validates_and_indexes_population(self):
        result = self.validate(self.records())
        self.assertEqual({self.request["photo_key"]}, set(result["photos"]))
        self.assertEqual(
            {(self.request["photo_key"], metric_id) for metric_id in self.metric_ids},
            set(result["metrics"]),
        )

    def test_state_value_and_reason_invariants_fail_closed(self):
        records = self.records()
        accepted = next(r for r in records if r.get("record_type") == "metric" and r["state"] == "accepted")
        accepted["raw_value"] = None
        with self.assertRaisesRegex(v2.MeasurementContractV2Error, "accepted requires raw"):
            self.validate(records)

        records = self.records()
        withheld = next(r for r in records if r.get("record_type") == "metric" and r["state"] == "unavailable")
        withheld["state"] = "withheld"
        withheld["terminal_stage"] = "metric.reliability"
        with self.assertRaisesRegex(v2.MeasurementContractV2Error, "withheld requires raw"):
            self.validate(records)

        records = self.records()
        unavailable = next(r for r in records if r.get("record_type") == "metric" and r["state"] == "unavailable")
        unavailable["reason_code"] = "sub12.uncoded"
        with self.assertRaisesRegex(v2.MeasurementContractV2Error, "not valid in persisted contracts"):
            self.validate(records)

    def test_declared_types_and_diagnostics_fail_closed(self):
        records = self.records()
        round_metric = next(
            r for r in records
            if r.get("record_type") == "metric" and r["metric_id"] == "round.ring_rho"
        )
        round_metric["raw_support"] = {"markers": "4"}
        with self.assertRaisesRegex(v2.MeasurementContractV2Error, "integer or null"):
            self.validate(records)

        records = self.records()
        metric = next(r for r in records if r.get("record_type") == "metric")
        metric["diagnostics"]["invented"] = True
        with self.assertRaisesRegex(v2.MeasurementContractV2Error, "undeclared fields"):
            self.validate(records)

    def test_stage_order_blocking_and_layout_evidence_are_rechecked(self):
        records = self.records()
        photo = records[1]
        photo["photo_stages"][2]["outcome"] = "fail"
        with self.assertRaisesRegex(v2.MeasurementContractV2Error, "reason is required"):
            self.validate(records)

        records = self.records()
        photo = records[1]
        obs = photo["layout_evidence"]["observations"][0]
        obs["present"] = False
        obs["high_confidence"] = False
        with self.assertRaisesRegex(v2.MeasurementContractV2Error, "does not match positive high-confidence observations"):
            self.validate(records)

    def test_frozen_identity_and_workspace_paths_are_enforced(self):
        records = self.records()
        records[1]["image_sha256"] = "b" * 64
        with self.assertRaisesRegex(v2.MeasurementContractV2Error, "frozen"):
            self.validate(records)

        records = self.records()
        records[1]["provenance"]["workspace_path"] = "/tmp/leak.jpg"
        with self.assertRaisesRegex(v2.MeasurementContractV2Error, "workspace path"):
            self.validate(records)


class SharedDecisionArchitectureGuardTest(unittest.TestCase):
    @staticmethod
    def _source(path):
        return (REPO / path).read_text(encoding="utf-8")

    def test_desktop_v2_driver_calls_shared_analysis_and_decision_once(self):
        src = self._source("tools/desktop-harness/drivers/CalibDecisionV2.java")
        code = re.sub(r"(?s)/\*.*?\*/", "", src)
        code = re.sub(r"//[^\n]*", "", code)
        self.assertEqual(1, code.count("WatchAlignCoreV13.analyse("))
        self.assertEqual(1, code.count("Sub124060Calibration.decide("))
        for forbidden in (
            "GmtSixLandmarkAnalyzer.measureStability(",
            "Sub124060Calibration.assess(",
            "Sub124060Calibration.project(",
            "Sub124060QcAnalyzer.batonRepeatable(",
            "Sub124060QcAnalyzer.roundOffsetRepeatable(",
        ):
            self.assertNotIn(forbidden, code)

    def test_v2_is_sidecar_only_and_not_registered_as_live_calibration_input(self):
        registry = self._source("tools/watch_calibrator/measurement_adapters.py")
        self.assertNotIn("submariner12_measurement_adapter_v2", registry)
        self.assertNotIn("submariner12_measured_v2", registry)

    def test_python_v2_adapter_has_no_image_processing_dependency(self):
        src = self._source("tools/watch_calibrator/submariner12_measurement_adapter_v2.py")
        for forbidden in ("import cv2", "from cv2", "import numpy", "from numpy", "scipy", "skimage"):
            self.assertNotIn(forbidden, src)


if __name__ == "__main__":
    unittest.main()
