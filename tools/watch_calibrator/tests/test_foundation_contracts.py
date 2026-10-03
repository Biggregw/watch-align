import csv
import json
import tempfile
import unittest
from pathlib import Path
import sys

HERE=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(HERE))
import contracts  # noqa: E402
import run_manifest  # noqa: E402


def valid_config():
    return {
        "model":"124060",
        "family":"submariner_12",
        "acquisition_adapter":"submariner_acquire_v3",
        "measurement_adapter":"production_app_route_v1",
        "layout":{"triangle":[12],"batons":[3,6,9],"rounds":[1,2,4,5,7,8,10,11]},
        "discovery":{"genuine_source_diversity":{
            "minimum_sources":5,"minimum_watches_per_source":4,
            "minimum_acquired_watches":28,"max_single_source_share":0.45}},
        "calibration_metrics":[
            {"metric":"m.two","app_key":"m.two","sided":"two","minimum_half_width":0.1,"allow_pose_sensitive":False},
            {"metric":"m.upper","app_key":"m.upper","sided":"upper","minimum_half_width":0.2,"allow_pose_sensitive":True},
        ],
    }


class ContractTest(unittest.TestCase):
    def test_valid_config_passes_without_mutation(self):
        cfg=valid_config()
        self.assertIs(cfg,contracts.validate_config(cfg,"124060",Path("124060.json")))

    def test_repository_124060_config_passes_contract(self):
        cp=HERE.parents[1]/"calibration"/"models"/"124060.json"
        cfg=json.loads(cp.read_text(encoding="utf-8"))
        self.assertIs(cfg,contracts.validate_config(cfg,"124060",cp))

    def test_requested_model_must_match_config_and_filename(self):
        cfg=valid_config()
        with self.assertRaisesRegex(contracts.ContractError,"does not match requested model"):
            contracts.validate_config(cfg,"126610LN",Path("124060.json"))
        with self.assertRaisesRegex(contracts.ContractError,"filename model"):
            contracts.validate_config(cfg,"124060",Path("126610LN.json"))

    def test_metric_semantics_fail_closed(self):
        cfg=valid_config();cfg["calibration_metrics"][0]["sided"]="mystery"
        with self.assertRaisesRegex(contracts.ContractError,"unsupported sidedness"):
            contracts.validate_config(cfg,"124060",Path("124060.json"))
        cfg=valid_config();cfg["calibration_metrics"][1]["metric"]="m.two"
        with self.assertRaisesRegex(contracts.ContractError,"duplicate metric id"):
            contracts.validate_config(cfg,"124060",Path("124060.json"))

    def test_layout_cannot_assign_one_hour_two_marker_types(self):
        cfg=valid_config();cfg["layout"]["rounds"].append(3)
        with self.assertRaisesRegex(contracts.ContractError,"declared as both"):
            contracts.validate_config(cfg,"124060",Path("124060.json"))

    def test_csv_model_boundary_rejects_mismatch(self):
        with tempfile.TemporaryDirectory() as td:
            p=Path(td)/"rows.csv"
            with p.open("w",newline="",encoding="utf-8") as fh:
                w=csv.DictWriter(fh,fieldnames=["model","physical_watch_id"]);w.writeheader()
                w.writerow({"model":"124060","physical_watch_id":"a"})
                w.writerow({"model":"126610LN","physical_watch_id":"b"})
            with self.assertRaisesRegex(contracts.ContractError,"does not match requested model"):
                contracts.validate_csv_exact_model(p,"124060","test rows")


class RunManifestTest(unittest.TestCase):
    def test_metric_fingerprint_is_order_stable_for_object_keys(self):
        a=valid_config()
        b=json.loads(json.dumps(a))
        b["calibration_metrics"][0]={
            "allow_pose_sensitive":False,"minimum_half_width":0.1,
            "sided":"two","app_key":"m.two","metric":"m.two"}
        self.assertEqual(run_manifest.metric_definition_fingerprint(a),run_manifest.metric_definition_fingerprint(b))

    def test_measurement_fingerprint_changes_with_commit_or_metrics(self):
        a=valid_config()
        fp=run_manifest.measurement_fingerprint(a,"abc")
        self.assertNotEqual(fp,run_manifest.measurement_fingerprint(a,"def"))
        b=json.loads(json.dumps(a));b["calibration_metrics"][0]["minimum_half_width"]=0.11
        self.assertNotEqual(fp,run_manifest.measurement_fingerprint(b,"abc"))

    def test_build_freezes_exact_config_and_evidence_hash(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);base=root/"out";base.mkdir();repo=root/"repo";repo.mkdir()
            cfg=valid_config();cp=root/"124060.json";cp.write_text(json.dumps(cfg,indent=2)+"\n",encoding="utf-8")
            evidence=root/"acquired.csv";evidence.write_text("model\n124060\n",encoding="utf-8")
            manifest=run_manifest.build(cfg,cp,base,repo,acquired_csv=evidence)
            self.assertEqual(cp.read_bytes(),(base/"frozen_config.json").read_bytes())
            self.assertEqual(run_manifest.sha256_file(cp),manifest["config"]["sha256"])
            self.assertEqual(run_manifest.sha256_file(evidence),manifest["evidence_manifest"]["sha256"])
            self.assertEqual(1,manifest["schema_version"])


if __name__=="__main__": unittest.main()
