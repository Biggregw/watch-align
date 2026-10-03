import json
import tempfile
import unittest
from pathlib import Path
import sys

HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))

import catalogue_registry as cr  # noqa: E402
import measurement_contract_v2 as v2  # noqa: E402
import submariner12_measurement_adapter_v2 as adapter  # noqa: E402


class V2AdapterSerializationTest(unittest.TestCase):
    def setUp(self):
        self.config = json.loads((HERE.parents[1] / "calibration" / "models" / "124060.json").read_text(encoding="utf-8"))
        self.metric_ids = sorted(m["app_key"] for m in self.config["calibration_metrics"])
        self.request = {
            "photo_key": "images/gen/watch-1/photo-01.jpg",
            "image_sha256": "a" * 64,
            "physical_watch_id": "watch-1",
            "workspace_path": Path("/tmp/not-persisted.jpg"),
            "provenance": {
                "source_type": "dealer_listing",
                "source_name": "Example source",
                "source_url": "https://example.invalid/watch-1",
                "image_url": "https://example.invalid/watch-1.jpg",
                "listing_id": "watch-1",
            },
        }

    def decision(self):
        metrics = []
        for i, metric_id in enumerate(self.metric_ids):
            accepted = i == 0
            metrics.append({
                "metric_id": metric_id,
                "state": "accepted" if accepted else "unavailable",
                "terminal_stage": "metric.reliability" if accepted else "metric.raw",
                "origin": "metric",
                "reason": None if accepted else {"code": "sub12.triangle_not_found", "subject": None, "detail": "not found"},
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
                {"stage": "preflight", "outcome": "not_applicable", "reason": {"code": "core.stage_not_applicable", "subject": None, "detail": None}, "diagnostics": {}},
                {"stage": "region", "outcome": "pass", "reason": None, "diagnostics": {"dial_source": "AUTO_ELLIPSE"}},
                {"stage": "geometry", "outcome": "pass", "reason": None, "diagnostics": {"dial_reproducible": True, "pose_tilt_deg": 1.25}},
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

    def test_build_records_is_header_photo_then_sorted_metrics_and_drops_workspace_path(self):
        records = adapter.build_records(
            self.config,
            partition="development",
            cls="gen",
            snapshot_id="s" * 64,
            requested=[self.request],
            decisions=[self.decision()],
            checkout_sha="1" * 40,
        )
        self.assertEqual("header", records[0]["record_type"])
        v2.validate_header_foundation(records[0])
        self.assertEqual("photo", records[1]["record_type"])
        self.assertEqual(self.request["photo_key"], records[1]["photo_key"])
        self.assertNotIn("workspace_path", records[1])
        metric_records = records[2:]
        self.assertEqual(self.metric_ids, [r["metric_id"] for r in metric_records])
        first = metric_records[0]
        self.assertEqual("accepted", first["state"])
        self.assertEqual(0.125, first["raw_value"])
        self.assertEqual(0.125, first["eligible_value"])
        self.assertIsNone(first["reason_code"])
        self.assertEqual("sub12.triangle_not_found", metric_records[1]["reason_code"])

    def test_written_contract_is_canonical_and_round_trips_byte_identically(self):
        with tempfile.TemporaryDirectory() as td:
            out = Path(td) / "c.jsonl"
            adapter.write_contract(
                self.config,
                partition="validation",
                cls="rep",
                snapshot_id="s" * 64,
                requested=[self.request],
                decisions=[self.decision()],
                out_jsonl=out,
                checkout_sha="2" * 40,
            )
            original = out.read_bytes()
            records = v2.read_canonical(out)
            v2.write_records(out, records)
            self.assertEqual(original, out.read_bytes())
            self.assertNotIn(str(self.request["workspace_path"]).encode(), original)

    def test_fingerprint_is_bound_to_checkout_and_catalogues(self):
        a = adapter.measurement_fingerprint("1" * 40)
        b = adapter.measurement_fingerprint("2" * 40)
        self.assertRegex(a, r"^[0-9a-f]{64}$")
        self.assertNotEqual(a, b)
        bundle = cr.bundle_metadata()
        self.assertEqual(bundle["metric_catalogue"]["id"], "sub12.124060.metrics")

    def test_population_or_watch_identity_mismatch_fails_closed(self):
        with self.assertRaisesRegex(adapter.Submariner12V2Error, "population"):
            adapter.build_records(
                self.config,
                partition="development",
                cls="gen",
                snapshot_id="s" * 64,
                requested=[self.request],
                decisions=[],
                checkout_sha="3" * 40,
            )
        bad = self.decision(); bad["physical_watch_id"] = "other-watch"
        with self.assertRaisesRegex(adapter.Submariner12V2Error, "physical watch"):
            adapter.build_records(
                self.config,
                partition="development",
                cls="gen",
                snapshot_id="s" * 64,
                requested=[self.request],
                decisions=[bad],
                checkout_sha="3" * 40,
            )

    def test_photo_key_must_be_manifest_relative(self):
        with self.assertRaises(adapter.Submariner12V2Error):
            adapter._safe_photo_key("/absolute/photo.jpg")
        with self.assertRaises(adapter.Submariner12V2Error):
            adapter._safe_photo_key("../outside.jpg")


if __name__ == "__main__":
    unittest.main()
