import tempfile
import unittest
from pathlib import Path
from unittest import mock
import sys

HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))

import phase3_reporting as phase3  # noqa: E402


class Phase3ReportingTest(unittest.TestCase):
    def test_v2_path_is_deterministic(self):
        self.assertEqual(
            Path("geometry/124060_validation_rep_measurement_contract_v2.jsonl"),
            phase3._v2_path(Path("geometry"), "124060", "validation", "rep"),
        )

    def test_load_validated_contracts_binds_every_group_to_same_snapshot_and_fingerprint(self):
        config = {"model": "124060", "family": "submariner_12"}
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            geometry = root / "geometry"
            geometry.mkdir()
            for partition, cls in phase3.GROUPS:
                phase3._v2_path(geometry, "124060", partition, cls).write_text("{}\n", encoding="utf-8")

            calls = []

            def fake_validate(path, **kwargs):
                identity = kwargs["expected_identity"]
                calls.append((path.name, identity, kwargs["expected_photos"]))
                return {
                    "header": {
                        "snapshot_id": identity["snapshot_id"],
                        "claimed_model": identity["claimed_model"],
                        "family": identity["family"],
                        "partition": identity["partition"],
                        "class_label": identity["class_label"],
                        "measurement_fingerprint": identity["measurement_fingerprint"],
                    },
                    "photos": {},
                    "metrics": {},
                }

            with mock.patch.object(
                phase3.submariner12_measurement_adapter_v2,
                "requested_photos",
                side_effect=lambda _a, _s, partition, cls, _m: [{"photo_key": f"{partition}-{cls}"}],
            ), mock.patch.object(
                phase3.submariner12_measurement_adapter_v2,
                "measurement_fingerprint",
                return_value="f" * 64,
            ), mock.patch.object(
                phase3.measurement_contract_v2,
                "validate_file",
                side_effect=fake_validate,
            ):
                validated = phase3.load_validated_contracts(
                    config,
                    "a" * 64,
                    root / "dataset",
                    root / "locked_split.csv",
                    geometry,
                    checkout_sha="1" * 40,
                )

        self.assertEqual(set(phase3.GROUPS), set(validated))
        self.assertEqual(len(phase3.GROUPS), len(calls))
        for _name, identity, expected_photos in calls:
            self.assertEqual("a" * 64, identity["snapshot_id"])
            self.assertEqual("f" * 64, identity["measurement_fingerprint"])
            self.assertEqual("124060", identity["claimed_model"])
            self.assertEqual("submariner_12", identity["family"])
            self.assertEqual(1, len(expected_photos))

    def test_missing_v2_contract_fails_before_reporting(self):
        config = {"model": "124060", "family": "submariner_12"}
        with tempfile.TemporaryDirectory() as td, mock.patch.object(
            phase3.submariner12_measurement_adapter_v2,
            "requested_photos",
            return_value=[],
        ):
            with self.assertRaisesRegex(phase3.Phase3ReportingError, "missing v2 sidecar"):
                phase3.load_validated_contracts(
                    config,
                    "a" * 64,
                    Path(td) / "dataset",
                    Path(td) / "locked_split.csv",
                    Path(td) / "geometry",
                    checkout_sha="1" * 40,
                )


if __name__ == "__main__":
    unittest.main()
