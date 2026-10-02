import csv, json, tempfile, unittest
from pathlib import Path
import sys

HERE=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(HERE))
from calibrate import propose, finalize  # noqa: E402


def write_csv(path, rows):
    path.parent.mkdir(parents=True,exist_ok=True)
    fields=list(rows[0]) if rows else ["metric"]
    with path.open("w",newline="",encoding="utf-8") as fh:
        w=csv.DictWriter(fh,fieldnames=fields);w.writeheader();w.writerows(rows)


class CalibrationTest(unittest.TestCase):
    def config(self):
        return {"model":"X","family":"f","calibration_policy":{"min_development_watches":4,"min_validation_watches":2,"min_holdout_watches":2,
            "clear_sigma":3.0,"check_sigma":5.0,"check_over_clear":1.5,"validation_clear_rate_min":0.75,"validation_check_rate_min":1.0,
            "holdout_clear_rate_min":0.75,"holdout_check_rate_min":1.0},
            "calibration_metrics":[{"metric":"m","app_key":"m","minimum_half_width":0.01,"allow_pose_sensitive":False}]}

    def test_validation_and_holdout_never_move_limits(self):
        with tempfile.TemporaryDirectory() as td:
            p=Path(td)
            write_csv(p/"dev.csv",[{"physical_watch_id":str(i),"metric":"m","median":v} for i,v in enumerate([0.0,0.01,-0.01,0.005])])
            write_csv(p/"rep.csv",[{"metric":"m","repeatability_class":"similar scale","pose_sensitive":"","between_watch_mad":"0.005","within_watch_mad_median":"0.002","perturbation_range_p90":"0.004"}])
            write_csv(p/"val.csv",[{"physical_watch_id":"v1","metric":"m","median":"0.0"},{"physical_watch_id":"v2","metric":"m","median":"0.01"}])
            write_csv(p/"hold.csv",[{"physical_watch_id":"h1","metric":"m","median":"0.0"},{"physical_watch_id":"h2","metric":"m","median":"-0.01"}])
            r=propose(self.config(),p/"dev.csv",p/"rep.csv",p/"val.csv")
            self.assertEqual("FROZEN_PENDING_HOLDOUT",r["metrics"]["m"]["status"])
            lo,hi=r["metrics"]["m"]["clear_low"],r["metrics"]["m"]["clear_high"]
            f=finalize(r,p/"hold.csv")
            # Genuine watches stay clear, but no sensitivity evidence was configured.
            self.assertEqual("HOLDOUT_PASSED_SENSITIVITY_UNPROVEN",f["metrics"]["m"]["status"])
            self.assertEqual("SENSITIVITY_UNPROVEN",f["state"])
            self.assertEqual(lo,f["metrics"]["m"]["clear_low"]);self.assertEqual(hi,f["metrics"]["m"]["clear_high"])

    def test_pose_sensitive_metric_is_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            p=Path(td)
            write_csv(p/"dev.csv",[{"physical_watch_id":str(i),"metric":"m","median":0.0} for i in range(4)])
            write_csv(p/"rep.csv",[{"metric":"m","repeatability_class":"similar scale","pose_sensitive":"image_scale(88%)","between_watch_mad":"0.005","within_watch_mad_median":"0.002","perturbation_range_p90":"0.004"}])
            write_csv(p/"val.csv",[{"physical_watch_id":"v1","metric":"m","median":0.0},{"physical_watch_id":"v2","metric":"m","median":0.0}])
            r=propose(self.config(),p/"dev.csv",p/"rep.csv",p/"val.csv")
            self.assertEqual("INSUFFICIENT",r["metrics"]["m"]["status"])
            self.assertIn("pose",r["metrics"]["m"]["reason"])

    def _files(self,p,dev,val,hold=None,within="0.002"):
        write_csv(p/"dev.csv",[{"physical_watch_id":str(i),"metric":"m","median":v} for i,v in enumerate(dev)])
        write_csv(p/"rep.csv",[{"metric":"m","repeatability_class":"measured","pose_sensitive":"","within_watch_mad_median":within}])
        write_csv(p/"val.csv",[{"physical_watch_id":f"v{i}","metric":"m","median":v} for i,v in enumerate(val)])
        if hold is not None:
            write_csv(p/"hold.csv",[{"physical_watch_id":f"h{i}","metric":"m","median":v} for i,v in enumerate(hold)])

    def test_one_extreme_development_watch_does_not_set_the_band(self):
        with tempfile.TemporaryDirectory() as td:
            p=Path(td);self._files(p,[0.0,0.01,-0.01,0.005,-0.005,0.002,1.0],[0.0,0.01])
            r=propose(self.config(),p/"dev.csv",p/"rep.csv",p/"val.csv")["metrics"]["m"]
            self.assertEqual(1,r["development_outliers_rejected"])
            self.assertLess(r["clear_high"],0.1)
            self.assertNotIn("development_max_abs_from_center",r)

    def test_upper_sided_metric_has_no_lower_limit(self):
        c=self.config();c["calibration_metrics"][0]["sided"]="upper"
        with tempfile.TemporaryDirectory() as td:
            p=Path(td);self._files(p,[0.02,0.03,0.025,0.035,0.028],[0.0,0.03])
            r=propose(c,p/"dev.csv",p/"rep.csv",p/"val.csv")["metrics"]["m"]
            self.assertIsNone(r["clear_low"]);self.assertIsNone(r["check_low"])
            self.assertEqual(1.0,r["validation_clear_rate"])  # 0.0 is a perfect value, never flagged

    def test_band_wider_than_product_cap_is_rejected(self):
        c=self.config();c["calibration_metrics"][0]["max_clear_half_width"]=0.001
        with tempfile.TemporaryDirectory() as td:
            p=Path(td);self._files(p,[0.0,0.01,-0.01,0.005],[0.0,0.01])
            r=propose(c,p/"dev.csv",p/"rep.csv",p/"val.csv")["metrics"]["m"]
            self.assertEqual("REJECTED_SENSITIVITY",r["status"])

    def test_sensitivity_evidence_allows_calibrated(self):
        c=self.config();c["calibration_metrics"][0]["max_clear_half_width"]=1.0
        with tempfile.TemporaryDirectory() as td:
            p=Path(td);self._files(p,[0.0,0.01,-0.01,0.005],[0.0,0.01],[0.0,-0.01])
            r=propose(c,p/"dev.csv",p/"rep.csv",p/"val.csv")
            self.assertTrue(r["metrics"]["m"]["sensitivity_proven"])
            self.assertEqual("CALIBRATED",finalize(r,p/"hold.csv")["metrics"]["m"]["status"])

    def test_known_defects_inside_clear_reject_the_band(self):
        c=self.config();c["calibration_metrics"][0]["defect_evidence"]="defects.csv"
        with tempfile.TemporaryDirectory() as td:
            p=Path(td);self._files(p,[0.0,0.01,-0.01,0.005],[0.0,0.01])
            write_csv(p/"defects.csv",[{"metric":"m","value":"0.003"},{"metric":"m","value":"0.5"}])
            r=propose(c,p/"dev.csv",p/"rep.csv",p/"val.csv",evidence_root=p)["metrics"]["m"]
            self.assertEqual(0.5,r["defect_outside_clear_rate"])
            self.assertEqual("REJECTED_SENSITIVITY",r["status"])

if __name__=="__main__": unittest.main()
