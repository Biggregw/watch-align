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
            self.assertEqual("CALIBRATED",f["metrics"]["m"]["status"])
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

if __name__=="__main__": unittest.main()
