import csv
import hashlib
import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock
import sys

HERE=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(HERE))
import evidence_snapshot as es  # noqa: E402
import replay  # noqa: E402


def config_value():
    return {
        "model":"124060","family":"submariner_12",
        "acquisition_adapter":"submariner_acquire_v3","measurement_adapter":"production_app_route_v1",
        "layout":{"triangle":[12]},
        "discovery":{"genuine_source_diversity":{
            "minimum_sources":1,"minimum_watches_per_source":1,"minimum_acquired_watches":1,
            "max_single_source_share":1.0}},
        "calibration_metrics":[{
            "metric":"m","app_key":"m","sided":"two","minimum_half_width":0.1,
            "allow_pose_sensitive":False}],
    }


def write_csv(path: Path, rows: list[dict]):
    with path.open("w",newline="",encoding="utf-8") as fh:
        w=csv.DictWriter(fh,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)


def make_snapshot(root: Path) -> Path:
    acq=root/"dataset";acq.mkdir()
    image=acq/"images"/"w1"/"01.jpg";image.parent.mkdir(parents=True);image.write_bytes(b"image")
    digest=hashlib.sha256(b"image").hexdigest()
    acquired=acq/"acquired_images.csv"
    write_csv(acquired,[{
        "physical_watch_id":"w1","candidate_id":"w1","model":"124060","family":"submariner_12",
        "class_label":"gen","local_path":"images/w1/01.jpg","sha256":digest,"bytes":"5",
        "width":"10","height":"10","exact_duplicate_of":"","acquisition_status":"acquired",
    }])
    split=root/"locked_split.csv"
    write_csv(split,[{
        "physical_watch_id":"w1","partition":"development","stratum":"gen/124060/source",
        "class_label":"gen","model":"124060","factory":"","source_name":"source","added_in":"locked_split",
    }])
    cfg=root/"124060.json";cfg.write_text(json.dumps(config_value(),indent=2)+"\n",encoding="utf-8")
    snap=root/"snapshot"
    es.create("124060","submariner_12",cfg,acquired,acq,split,snap)
    return snap


class ReplayTest(unittest.TestCase):
    def test_replay_uses_frozen_snapshot_and_shared_execution(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);snap=make_snapshot(root);out=root/"out"
            def fake_execute(config, acquisition_root, split_path, geometry_dir, calibration_path, base,
                             measurement_adapter_id=None):
                self.assertIsNone(measurement_adapter_id)
                self.assertEqual(b"image",(acquisition_root/"images"/"w1"/"01.jpg").read_bytes())
                self.assertTrue(split_path.is_file())
                calibration_path.write_text(json.dumps({"state":"READY"},sort_keys=True)+"\n")
                return {"measurement_adapter":{"id":"production_app_route_v1","version":"1",
                            "reliability_policy":"legacy_production_gate_v1","contract_schema_version":None},
                        "configured_measurement_adapter":"production_app_route_v1",
                        "development":{"photos":1},"validation":{},"holdout":{},
                        "replica_measured":{},"legacy_split_state":"TEST","final":{"state":"READY"}}
            with mock.patch.object(replay.calibration_execution,"execute",side_effect=fake_execute) as execute:
                status=replay.replay(snap,out)
            self.assertEqual("offline_snapshot_replay",status["mode"])
            self.assertEqual("READY",status["state"])
            self.assertEqual(es.verify(snap)["snapshot_id"],status["snapshot_id"])
            self.assertEqual(1,execute.call_count)
            manifest=json.loads((out/"124060"/"run_manifest.json").read_text())
            self.assertEqual("offline_snapshot_replay",manifest["mode"])
            self.assertEqual(status["snapshot_id"],manifest["replay_snapshot_id"])
            self.assertEqual("production_app_route_v1",manifest["adapters"]["measurement_effective"]["id"])

    def test_candidate_adapter_override_is_passed_and_recorded(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);snap=make_snapshot(root);out=root/"out"
            adapter={"id":"submariner12_measured_v1","version":"1",
                     "reliability_policy":"sub124060_production_reliability_v1","contract_schema_version":1}
            def fake_execute(config, acquisition_root, split_path, geometry_dir, calibration_path, base,
                             measurement_adapter_id=None):
                self.assertEqual("submariner12_measured_v1",measurement_adapter_id)
                calibration_path.write_text(json.dumps({"state":"READY"},sort_keys=True)+"\n")
                return {"measurement_adapter":adapter,"configured_measurement_adapter":"production_app_route_v1",
                        "development":{},"validation":{},"holdout":{},"replica_measured":{},
                        "legacy_split_state":"TEST","final":{"state":"READY"}}
            with mock.patch.object(replay.calibration_execution,"execute",side_effect=fake_execute):
                replay.replay(snap,out,measurement_adapter_id="submariner12_measured_v1")
            manifest=json.loads((out/"124060"/"run_manifest.json").read_text())
            self.assertEqual("submariner12_measured_v1",manifest["requested_measurement_adapter_override"])
            self.assertEqual(adapter,manifest["adapters"]["measurement_effective"])

    def test_replay_refuses_tampered_snapshot_before_execution(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);snap=make_snapshot(root)
            manifest=es.load(snap);obj=snap/manifest["objects"][0]["object_path"]
            obj.write_bytes(b"tampered")
            with mock.patch.object(replay.calibration_execution,"execute") as execute:
                with self.assertRaises(es.SnapshotError):
                    replay.replay(snap,root/"out")
            execute.assert_not_called()


if __name__=="__main__": unittest.main()
