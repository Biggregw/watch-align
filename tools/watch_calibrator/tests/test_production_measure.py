import csv, tempfile, unittest
from pathlib import Path
from unittest import mock
import sys

HERE=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(HERE))
import contracts  # noqa: E402
import production_measure as pm  # noqa: E402
import split  # noqa: E402

M=["twelve.rotation_deg","round.ring_rho"]


def write(path,rows):
    with path.open("w",newline="",encoding="utf-8") as fh:
        w=csv.DictWriter(fh,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)


class ProductionMeasureTest(unittest.TestCase):
    def test_gated_blanks_never_reach_watch_medians(self):
        with tempfile.TemporaryDirectory() as td:
            p=Path(td)
            write(p/"photo.csv",[
                {"physical_watch_id":"a","pose_tilt_deg":"","twelve.rotation_deg":"0.5","round.ring_rho":""},
                {"physical_watch_id":"a","pose_tilt_deg":"","twelve.rotation_deg":"","round.ring_rho":""},
                {"physical_watch_id":"b","pose_tilt_deg":"","twelve.rotation_deg":"","round.ring_rho":"0.81"}])
            pm.summarise(p/"photo.csv",M,p/"w.csv",p/"r.csv")
            rows=list(csv.DictReader((p/"w.csv").open()))
            self.assertEqual({("twelve.rotation_deg","a","1"),("round.ring_rho","b","1")},
                             {(r["metric"],r["physical_watch_id"],r["photos"]) for r in rows})
            rep={r["metric"]:r for r in csv.DictReader((p/"r.csv").open())}
            self.assertEqual("insufficient data",rep["twelve.rotation_deg"]["repeatability_class"])

    def test_metric_tracking_photo_angle_is_flagged_pose_sensitive(self):
        rows=[]
        for w in "abcd":
            for t in (0.0,3.0,6.0):
                rows.append({"physical_watch_id":w,"pose_tilt_deg":str(t),"twelve.rotation_deg":str(0.1*t),"round.ring_rho":str(0.81+(0.001 if t==3.0 else 0))})
        with tempfile.TemporaryDirectory() as td:
            p=Path(td);write(p/"photo.csv",rows)
            pm.summarise(p/"photo.csv",M,p/"w.csv",p/"r.csv")
            rep={r["metric"]:r for r in csv.DictReader((p/"r.csv").open())}
            self.assertEqual("1",rep["twelve.rotation_deg"]["pose_sensitive"])
            self.assertEqual("",rep["round.ring_rho"]["pose_sensitive"])
            self.assertEqual("measured",rep["round.ring_rho"]["repeatability_class"])

    def test_photo_identities_name_measured_paths_by_manifest_identity(self):
        rows=[
            {"physical_watch_id":"w1","model":"124060","class_label":"gen","local_path":"images/w1/01,a.jpg",
             "sha256":"A"*64,"acquisition_status":"acquired","exact_duplicate_of":""},
            {"physical_watch_id":"w1","model":"124060","class_label":"gen","local_path":"images/w1/02.jpg",
             "sha256":"b"*64,"acquisition_status":"acquired","exact_duplicate_of":"w1:1"},
            {"physical_watch_id":"w2","model":"124060","class_label":"gen","local_path":"",
             "sha256":"","acquisition_status":"failed","exact_duplicate_of":""},
        ]
        with tempfile.TemporaryDirectory() as td:
            p=Path(td)
            write(p/"split.csv",[{"physical_watch_id":"w1","partition":"development","stratum":"gen/124060/s",
                                  "class_label":"gen","model":"124060","factory":"","source_name":"s","added_in":"locked_split"}])
            identities=[]
            for root in (p/"live"/"dataset",p/"replay"/"124060"/"dataset"):
                root.mkdir(parents=True);write(root/"acquired_images.csv",rows)
                ids=pm.photo_identities(root)
                # Keys are exactly the path text measurement routes write for photo_list() photos.
                measured=[str(photo).replace(",",";") for _,photo in pm.photo_list(root,p/"split.csv","development","gen","124060")]
                self.assertEqual(measured,list(ids))
                identities.append(list(ids.values()))
            self.assertEqual([{"local_path":"images/w1/01,a.jpg","image_sha256":"a"*64}],identities[0])
            self.assertEqual(identities[0],identities[1])

    def test_photo_identities_fail_closed_without_image_hash(self):
        with tempfile.TemporaryDirectory() as td:
            p=Path(td)
            write(p/"acquired_images.csv",[{"physical_watch_id":"w1","local_path":"images/w1/01.jpg","sha256":"",
                                            "acquisition_status":"acquired","exact_duplicate_of":""}])
            with self.assertRaisesRegex(contracts.ContractError,"sha256 are required"):
                pm.photo_identities(p)

    def test_run_harness_passes_exact_model_instead_of_implicit_124060(self):
        with tempfile.TemporaryDirectory() as td:
            p=Path(td);out=p/"photo.csv"
            photos=[("w1",p/"one.jpg")]
            with mock.patch.object(pm.subprocess,"run") as run:
                pm.run_harness("126610lv",photos,out)
            cmd=run.call_args.args[0]
            self.assertEqual("CalibMeasure",cmd[2])
            self.assertEqual("126610LV",cmd[3])
            self.assertEqual(str(out.with_suffix(".list.tsv")),cmd[4])
            self.assertEqual(str(out),cmd[5])
            self.assertTrue(run.call_args.kwargs["check"])

    def test_run_harness_rejects_missing_model(self):
        with tempfile.TemporaryDirectory() as td:
            with self.assertRaises(ValueError):
                pm.run_harness("",[],Path(td)/"photo.csv")

    def test_measurement_output_rejects_wrong_model(self):
        with tempfile.TemporaryDirectory() as td:
            p=Path(td);photo=p/"one.jpg";out=p/"photo.csv"
            write(out,[{"physical_watch_id":"w1","model":"126610LN","path":str(photo)}])
            with self.assertRaisesRegex(contracts.ContractError,"does not match requested model"):
                pm.validate_harness_output(out,"124060",[("w1",photo)])

    def test_measurement_output_rejects_missing_or_wrong_population(self):
        with tempfile.TemporaryDirectory() as td:
            p=Path(td);one=p/"one.jpg";two=p/"two.jpg";out=p/"photo.csv"
            write(out,[{"physical_watch_id":"wrong","model":"124060","path":str(one)}])
            with self.assertRaisesRegex(contracts.ContractError,"row count"):
                pm.validate_harness_output(out,"124060",[("w1",one),("w2",two)])
            write(out,[{"physical_watch_id":"wrong","model":"124060","path":str(one)}])
            with self.assertRaisesRegex(contracts.ContractError,"physical-watch population"):
                pm.validate_harness_output(out,"124060",[("w1",one)])


class SplitTest(unittest.TestCase):
    def test_split_is_watch_level_stratified_and_locked(self):
        ws=[{"physical_watch_id":f"g{i}","class_label":"gen","model":"124060","factory":""} for i in range(10)]
        ws+=[{"physical_watch_id":f"r{i}","class_label":"rep","model":"124060","factory":"VSF"} for i in range(5)]
        with tempfile.TemporaryDirectory() as td:
            path=Path(td)/"s.csv";rows=split.create(ws,path)
            self.assertEqual(15,len({r["physical_watch_id"] for r in rows}))
            gen=[r["partition"] for r in rows if r["class_label"]=="gen"]
            self.assertEqual((6,2,2),(gen.count("development"),gen.count("validation"),gen.count("holdout")))
            with self.assertRaises(FileExistsError): split.create(ws,path)
            self.assertEqual(rows,split.make_split(list(reversed(ws))))


if __name__=="__main__": unittest.main()
