import csv
import tempfile
import unittest
from pathlib import Path
import sys

HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))
import production_measure as pm  # noqa: E402


class ProductionMeasureSourceTest(unittest.TestCase):
    def test_watch_summary_preserves_locked_source_name(self):
        with tempfile.TemporaryDirectory() as td:
            p = Path(td)
            photo = p / "photo.csv"
            with photo.open("w", newline="", encoding="utf-8") as fh:
                w = csv.DictWriter(fh, fieldnames=["physical_watch_id", "pose_tilt_deg", "m"])
                w.writeheader()
                w.writerow({"physical_watch_id": "w1", "pose_tilt_deg": "", "m": "0.10"})
                w.writerow({"physical_watch_id": "w1", "pose_tilt_deg": "", "m": "0.12"})
            pm.summarise(
                photo,
                ["m"],
                p / "watch.csv",
                p / "repeat.csv",
                {"w1": "SwissWatchExpo"},
            )
            rows = list(csv.DictReader((p / "watch.csv").open(newline="", encoding="utf-8")))
        self.assertEqual(1, len(rows))
        self.assertEqual("w1", rows[0]["physical_watch_id"])
        self.assertEqual("SwissWatchExpo", rows[0]["source_name"])
        self.assertEqual("2", rows[0]["photos"])


if __name__ == "__main__":
    unittest.main()
