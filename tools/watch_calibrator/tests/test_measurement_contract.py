import csv
import tempfile
import unittest
from pathlib import Path
import sys

HERE=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(HERE))
import measurement_adapters  # noqa: E402
import measurement_contract as mc  # noqa: E402


def config():
    return {
        "model":"124060","family":"submariner_12",
        "calibration_metrics":[
            {"metric":"m.a","app_key":"m.a"},
            {"metric":"m.b","app_key":"m.b"},
        ],
    }


def adapter():
    return {"id":"submariner12_measured_v1","version":"1",
            "reliability_policy":"sub124060_production_reliability_v1"}


def row(path,metric,raw,state,reason,eligible):
    return {
        "schema_version":"1","physical_watch_id":"w1","model":"124060","family":"submariner_12",
        "path":str(path),"adapter_id":"submariner12_measured_v1","adapter_version":"1",
        "reliability_policy":"sub124060_production_reliability_v1","dial_source":"AUTO_EDGE_FIT",
        "dial_reproducible":"true","pose_tilt_deg":"2.0","expected_layout":"submariner_124060_no_date_v1",
        "observed_layout_state":"compatible_no_date","metric":metric,"raw_value":raw,
        "reliability_state":state,"reliability_reason":reason,"eligible_value":eligible,
    }


def write(path,rows):
    with path.open("w",newline="",encoding="utf-8") as fh:
        w=csv.DictWriter(fh,fieldnames=mc.FIELDS);w.writeheader();w.writerows(rows)


class MeasurementContractTest(unittest.TestCase):
    def test_valid_contract_preserves_raw_withheld_and_materialises_only_eligible(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);photo=root/"one.jpg";contract=root/"contract.csv";wide=root/"wide.csv"
            write(contract,[
                row(photo,"m.a","1.25","accepted","","1.25"),
                row(photo,"m.b","9.5","withheld","resize repeatability failed",""),
            ])
            summary=mc.validate(contract,config(),adapter(),[("w1",photo)])
            self.assertEqual(2,summary["raw_values"])
            self.assertEqual(1,summary["eligible_values"])
            self.assertEqual({"accepted":1,"withheld":1},summary["reliability_states"])
            mc.materialize_eligible_wide(contract,config(),[("w1",photo)],wide)
            rows=list(csv.DictReader(wide.open()))
            self.assertEqual("1.25",rows[0]["m.a"])
            self.assertEqual("",rows[0]["m.b"])

    def test_nonaccepted_value_requires_reason_and_cannot_be_eligible(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);photo=root/"one.jpg";contract=root/"contract.csv"
            bad=row(photo,"m.a","1","withheld","","1")
            write(contract,[bad,row(photo,"m.b","","unavailable","not measured","")])
            with self.assertRaises(mc.MeasurementContractError):
                mc.validate(contract,config(),adapter(),[("w1",photo)])

    def test_each_photo_must_have_every_metric_once(self):
        with tempfile.TemporaryDirectory() as td:
            root=Path(td);photo=root/"one.jpg";contract=root/"contract.csv"
            write(contract,[
                row(photo,"m.a","1","accepted","","1"),
                row(photo,"m.a","1","accepted","","1"),
            ])
            with self.assertRaisesRegex(mc.MeasurementContractError,"each configured metric exactly once"):
                mc.validate(contract,config(),adapter(),[("w1",photo)])

    def test_registry_is_fail_closed_and_exposes_versioned_adapter(self):
        a=measurement_adapters.resolve("submariner12_measured_v1")
        self.assertEqual("1",a.version)
        self.assertEqual(1,a.contract_schema_version)
        with self.assertRaises(ValueError):
            measurement_adapters.resolve("mystery_adapter")


if __name__=="__main__": unittest.main()
