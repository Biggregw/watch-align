import csv
import tempfile
import unittest
from pathlib import Path
import sys

HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))
from genuine_envelope import build  # noqa: E402


def write_csv(path, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    fields = list(rows[0]) if rows else ["physical_watch_id"]
    with path.open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=fields)
        w.writeheader()
        w.writerows(rows)


class GenuineEnvelopeTest(unittest.TestCase):
    def config(self, sided="two"):
        return {
            "model": "X",
            "family": "f",
            "discovery": {"genuine_source_diversity": {"minimum_sources": 3}},
            "calibration_policy": {
                "genuine_envelope_min_watches": 4,
                "genuine_envelope_min_sources": 2,
                "obvious_outlier_min_photos": 4,
                "obvious_outlier_within_watch_mad_k": 8.0,
                "obvious_outlier_minimum_width_multiplier": 4.0,
                "genuine_envelope_within_watch_quantile": 0.90,
                "genuine_envelope_strong_guard_multiplier": 2.0,
            },
            "calibration_metrics": [
                {"metric": "m", "app_key": "m", "sided": sided, "minimum_half_width": 0.01}
            ],
        }

    def make_split(self, p, watches):
        rows = []
        for i, wid in enumerate(watches):
            rows.append({
                "physical_watch_id": wid,
                "partition": "development",
                "class_label": "gen",
                "factory": "",
                "source_name": "A" if i % 2 == 0 else "B",
            })
        write_csv(p / "split.csv", rows)

    def build_simple(self, p, dev, val, hold, rep=None, config=None):
        config = config or self.config()
        watches = sorted({r["physical_watch_id"] for group in (dev, val, hold) for r in group})
        self.make_split(p, watches)
        files = {}
        for name, rows in (("development", dev), ("validation", val), ("holdout", hold)):
            path = p / f"{name}.csv"
            write_csv(path, rows)
            files[name] = path
        rep_files = {}
        if rep is not None:
            path = p / "rep.csv"
            write_csv(path, rep)
            rep_files["development"] = path
        return build(config, files, p / "split.csv", replica_photo_files=rep_files)

    def test_validation_and_holdout_genuine_values_expand_final_envelope(self):
        with tempfile.TemporaryDirectory() as td:
            p = Path(td)
            dev = [
                {"physical_watch_id": "d1", "m": "0.00"},
                {"physical_watch_id": "d2", "m": "0.05"},
            ]
            val = [{"physical_watch_id": "v1", "m": "0.20"}]
            hold = [{"physical_watch_id": "h1", "m": "0.30"}]
            rec = self.build_simple(p, dev, val, hold)["metrics"]["m"]
            self.assertEqual("CALIBRATED_GENUINE_ENVELOPE", rec["status"])
            self.assertEqual(0.30, rec["observed_genuine_max"])
            self.assertGreater(rec["clear_high"], 0.30)
            self.assertEqual(1, rec["split_diagnostics"]["validation"]["outside_raw_development_envelope"])
            self.assertEqual(1, rec["split_diagnostics"]["holdout"]["outside_raw_development_envelope"])

    def test_consistently_unusual_genuine_watch_is_kept(self):
        with tempfile.TemporaryDirectory() as td:
            p = Path(td)
            dev = [
                {"physical_watch_id": "d1", "m": "0.00"},
                {"physical_watch_id": "d2", "m": "0.01"},
                {"physical_watch_id": "u", "m": "0.50"},
                {"physical_watch_id": "u", "m": "0.51"},
                {"physical_watch_id": "u", "m": "0.49"},
                {"physical_watch_id": "u", "m": "0.50"},
            ]
            val = [{"physical_watch_id": "v1", "m": "0.02"}]
            hold = []
            rec = self.build_simple(p, dev, val, hold)["metrics"]["m"]
            self.assertEqual(0, rec["obvious_photo_outliers_rejected"])
            self.assertEqual(0.51, rec["observed_genuine_max"])
            self.assertGreater(rec["clear_high"], 0.51)

    def test_single_obvious_within_watch_spike_is_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            p = Path(td)
            dev = [
                {"physical_watch_id": "w1", "m": "0.00"},
                {"physical_watch_id": "w1", "m": "0.01"},
                {"physical_watch_id": "w1", "m": "-0.01"},
                {"physical_watch_id": "w1", "m": "1.00"},
                {"physical_watch_id": "w2", "m": "0.02"},
            ]
            val = [{"physical_watch_id": "w3", "m": "0.03"}]
            hold = [{"physical_watch_id": "w4", "m": "0.04"}]
            rec = self.build_simple(p, dev, val, hold)["metrics"]["m"]
            self.assertEqual(1, rec["obvious_photo_outliers_rejected"])
            self.assertLess(rec["observed_genuine_max"], 0.1)

    def test_extreme_single_photo_genuine_watch_is_not_trimmed(self):
        with tempfile.TemporaryDirectory() as td:
            p = Path(td)
            dev = [
                {"physical_watch_id": "w1", "m": "0.00"},
                {"physical_watch_id": "w2", "m": "0.01"},
            ]
            val = [{"physical_watch_id": "w3", "m": "0.02"}]
            hold = [{"physical_watch_id": "w4", "m": "0.70"}]
            rec = self.build_simple(p, dev, val, hold)["metrics"]["m"]
            self.assertEqual(0, rec["obvious_photo_outliers_rejected"])
            self.assertEqual(0.70, rec["observed_genuine_max"])

    def test_upper_sided_metric_has_no_lower_warning_boundary(self):
        with tempfile.TemporaryDirectory() as td:
            p = Path(td)
            config = self.config("upper")
            dev = [
                {"physical_watch_id": "w1", "m": "0.01"},
                {"physical_watch_id": "w2", "m": "0.02"},
            ]
            val = [{"physical_watch_id": "w3", "m": "0.03"}]
            hold = [{"physical_watch_id": "w4", "m": "0.04"}]
            rec = self.build_simple(p, dev, val, hold, config=config)["metrics"]["m"]
            self.assertIsNone(rec["clear_low"])
            self.assertIsNone(rec["check_low"])
            self.assertGreater(rec["clear_high"], 0.04)

    def test_replica_data_never_moves_genuine_limits(self):
        with tempfile.TemporaryDirectory() as td:
            p = Path(td)
            dev = [
                {"physical_watch_id": "w1", "m": "0.00"},
                {"physical_watch_id": "w2", "m": "0.01"},
            ]
            val = [{"physical_watch_id": "w3", "m": "0.02"}]
            hold = [{"physical_watch_id": "w4", "m": "0.03"}]
            no_rep = self.build_simple(p / "a", dev, val, hold)["metrics"]["m"]
            rep = [
                {"physical_watch_id": "r1", "m": "9.0"},
                {"physical_watch_id": "r2", "m": "-9.0"},
            ]
            with_rep = self.build_simple(p / "b", dev, val, hold, rep=rep)["metrics"]["m"]
            for key in ("clear_low", "clear_high", "check_low", "check_high"):
                self.assertEqual(no_rep[key], with_rep[key])
            self.assertEqual(2, with_rep["replica_stress"]["outside_clear_photos"])


if __name__ == "__main__":
    unittest.main()
