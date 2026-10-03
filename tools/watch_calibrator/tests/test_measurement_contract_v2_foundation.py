import json
import math
import tempfile
import unittest
from pathlib import Path
import sys

HERE = Path(__file__).resolve().parents[1]
REPO = HERE.parents[1]
sys.path.insert(0, str(HERE))

import catalogue_registry as cr  # noqa: E402
import measurement_contract_v2 as v2  # noqa: E402


class CatalogueFoundationTest(unittest.TestCase):
    def test_real_catalogues_validate_and_match_java_reason_constants(self):
        core = cr.load_reason_catalogue(cr.CORE_REASON_CATALOGUE, expected_namespace="core")
        sub12 = cr.load_reason_catalogue(cr.SUB12_REASON_CATALOGUE, expected_namespace="sub12")
        metrics = cr.load_metric_catalogue(cr.SUB12_124060_METRIC_CATALOGUE, expected_family="submariner_12", expected_model="124060")
        java_dir = REPO / "android" / "app" / "src" / "main" / "java" / "com" / "watchalign" / "mobile"
        self.assertEqual(cr.java_reason_codes(java_dir / "CoreReasons.java"), {r["code"] for r in core["reasons"]})
        self.assertEqual(cr.java_reason_codes(java_dir / "Sub12Reasons.java"), {r["code"] for r in sub12["reasons"]})
        model = json.loads((REPO / "calibration" / "models" / "124060.json").read_text(encoding="utf-8"))
        self.assertEqual(sorted(m["app_key"] for m in model["calibration_metrics"]), [m["metric_id"] for m in metrics["metrics"]])
        self.assertFalse(cr.reason_index(core, sub12)["sub12.uncoded"]["contract_valid"])

    def test_catalogue_bundle_hashes_are_stable_sha256(self):
        first = cr.bundle_metadata()
        self.assertEqual(first, cr.bundle_metadata())
        for item in [first["metric_catalogue"], *first["reason_catalogues"]]:
            self.assertRegex(item["sha256"], r"^[0-9a-f]{64}$")

    def test_reason_catalogue_fails_closed_on_namespace_or_duplicate(self):
        with tempfile.TemporaryDirectory() as td:
            p = Path(td) / "r.json"
            bad = {"schema":cr.REASON_SCHEMA,"catalogue_id":"x","version":1,"namespace":"x","reasons":[{"code":"x.a","class":"input","meaning":"a","contract_valid":True},{"code":"x.a","class":"input","meaning":"b","contract_valid":True}]}
            p.write_text(json.dumps(bad), encoding="utf-8")
            with self.assertRaises(cr.CatalogueError): cr.load_reason_catalogue(p, expected_namespace="x")
            bad["reasons"][1]["code"] = "y.b"
            p.write_text(json.dumps(bad), encoding="utf-8")
            with self.assertRaises(cr.CatalogueError): cr.load_reason_catalogue(p, expected_namespace="x")

    def test_metric_catalogue_declares_round_recomputation_only(self):
        by_id = cr.metric_index(cr.load_metric_catalogue(cr.SUB12_124060_METRIC_CATALOGUE))
        self.assertEqual("recomputed_on_reliable_support", by_id["round.ring_rho"]["eligible_relation"])
        self.assertEqual("recomputed_on_reliable_support", by_id["round.spacing_rms_deg"]["eligible_relation"])
        for metric_id, spec in by_id.items():
            if not metric_id.startswith("round."):
                self.assertEqual("identical_to_raw", spec["eligible_relation"])


class CanonicalJsonlFoundationTest(unittest.TestCase):
    def header(self):
        return {"record_type":"header","schema":v2.SCHEMA,"schema_version":v2.SCHEMA_VERSION,"snapshot_id":"snapshot","claimed_model":"124060","family":"submariner_12","partition":"development","class_label":"gen","adapter":{"id":"submariner12_measured_v2","version":"2"},"reliability_policy":{"id":"sub124060_production_reliability","version":1},"metric_catalogue":{"id":"sub12.124060.metrics","version":1,"sha256":"0"*64},"reason_catalogues":[],"stage_map":list(v2.PHOTO_STAGES)+list(v2.METRIC_STAGES),"measurement_fingerprint":"f"*64}

    def test_canonical_round_trip_is_byte_identical_and_normalises_negative_zero(self):
        with tempfile.TemporaryDirectory() as td:
            p = Path(td) / "contract.jsonl"; h = self.header(); h["z"] = -0.0
            v2.write_records(p, [h]); original = p.read_bytes(); records = v2.read_canonical(p)
            self.assertEqual(0.0, records[0]["z"]); v2.write_records(p, records)
            self.assertEqual(original, p.read_bytes()); self.assertNotIn(b"-0.0", original)

    def test_noncanonical_jsonl_is_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            p = Path(td) / "contract.jsonl"; p.write_bytes(b'{"schema_version": 2, "record_type":"header"}\n')
            with self.assertRaisesRegex(v2.MeasurementContractV2Error, "not canonical"): v2.read_canonical(p)

    def test_nonfinite_values_are_rejected_before_serialisation(self):
        with self.assertRaises(cr.CatalogueError): v2.canonical_line({"x": math.nan})

    def test_header_foundation_requires_identity_fields(self):
        h = self.header(); v2.validate_header_foundation(h); del h["snapshot_id"]
        with self.assertRaisesRegex(v2.MeasurementContractV2Error, "snapshot_id"): v2.validate_header_foundation(h)


if __name__ == "__main__": unittest.main()
