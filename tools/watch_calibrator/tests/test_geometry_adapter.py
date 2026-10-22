"""Phase B: the marker_geometry_v1 measurement adapter (GeometryMeasure through MarkerGeometry)."""
import csv
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

HERE = Path(__file__).resolve().parents[1]
REPO = HERE.parents[1]
for p in (str(HERE), str(REPO)):
    if p not in sys.path:
        sys.path.insert(0, p)
import marker_mask  # noqa: E402
import production_measure as pm  # noqa: E402
import genuine_envelope  # noqa: E402

LEGACY = json.loads((REPO / "calibration/models/124060.json").read_text())
GEOMETRY = dict(LEGACY, measurement_adapter="marker_geometry_v1",
                calibration_metrics=[{"metric": "marker.4.tang", "minimum_half_width": 0.05},
                                     {"metric": "marker.6.tilt", "minimum_half_width": 0.2},
                                     {"metric": "ring.place_x", "minimum_half_width": 0.05}])


class GeometryAdapterTest(unittest.TestCase):
    def test_adapters_select_their_driver_and_layout(self):
        self.assertEqual(("CalibMeasure", None), pm.adapter(LEGACY))
        self.assertEqual(("GeometryMeasure", "submariner_124060"), pm.adapter(GEOMETRY))
        with self.assertRaises(SystemExit):
            pm.adapter(dict(LEGACY, measurement_adapter="anything"))

    def test_the_layout_reaches_the_harness(self):
        with tempfile.TemporaryDirectory() as td, mock.patch.object(pm.subprocess, "run") as run:
            pm.run_harness([("w", Path("/x.jpg"))], Path(td) / "o.csv", "GeometryMeasure", "submariner_124060")
            args, kw = run.call_args
            self.assertEqual("GeometryMeasure", args[0][2])
            self.assertIn("-Dwatchalign.model=submariner_124060", kw["env"]["JAVA_TOOL_OPTIONS"])

    def test_per_marker_and_ring_metrics_declare_their_markers(self):
        self.assertEqual({4}, set(marker_mask.dependencies("marker.4.tang", "submariner_124060")))
        ring = marker_mask.dependencies("ring.place_x", "submariner_124060")
        self.assertEqual(set(range(1, 12)), set(ring))           # every round marker and baton, not the 12 triangle
        self.assertNotIn(3, marker_mask.dependencies("ring.rot_deg", "submariner_126610"))   # the date window

    def test_geometry_values_feed_the_unchanged_watch_aggregation_and_envelope(self):
        rows = []
        for i, w in enumerate("abcdefghij"):
            for k in range(2):
                rows.append({"physical_watch_id": w, "path": f"/{w}{k}.jpg", "dial_source": "minute_track",
                             "dial_reproducible": "", "pose_tilt_deg": "3.0",
                             "marker.4.tang": f"{0.01 * i + 0.001 * k:.6f}", "marker.6.tilt": "" if w == "c" else "0.1",
                             "ring.place_x": "0.02"})
        with tempfile.TemporaryDirectory() as td:
            p = Path(td)
            with (p / "photo.csv").open("w", newline="") as fh:
                wr = csv.DictWriter(fh, fieldnames=list(rows[0]))
                wr.writeheader()
                wr.writerows(rows)
            metrics = [s["metric"] for s in GEOMETRY["calibration_metrics"]]
            pm.summarise(p / "photo.csv", metrics, p / "w.csv", p / "r.csv", {}, GEOMETRY)
            w = [r for r in csv.DictReader((p / "w.csv").open()) if r["metric"] == "marker.4.tang"]
            self.assertEqual(10, len(w))                         # one value per physical watch
            tilt = [r for r in csv.DictReader((p / "w.csv").open()) if r["metric"] == "marker.6.tilt"]
            self.assertEqual(9, len(tilt))                       # a blank (ineligible) value never counts
            split = p / "split.csv"
            with split.open("w", newline="") as fh:
                wr = csv.DictWriter(fh, fieldnames=["physical_watch_id", "partition", "source_name"])
                wr.writeheader()
                wr.writerows({"physical_watch_id": x, "partition": "development", "source_name": "S" + str(i % 3)}
                             for i, x in enumerate("abcdefghij"))
            env = genuine_envelope.build(dict(GEOMETRY, calibration_policy={"genuine_envelope_min_watches": 5,
                                                                            "genuine_envelope_min_sources": 2}),
                                         {"development": p / "photo.csv"}, split)
            self.assertEqual("CALIBRATED_GENUINE_ENVELOPE", env["metrics"]["marker.4.tang"]["status"])


if __name__ == "__main__":
    unittest.main()
