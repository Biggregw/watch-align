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
        # the ring depends on exactly the markers its fit used: a hand at the 2 does not discard it
        row = {"ring.markers": "1 3 4 5 6 7 8 9 10 11"}
        self.assertEqual({1, 3, 4, 5, 6, 7, 8, 9, 10, 11}, set(marker_mask.dependencies("ring.place_x", "submariner_124060", row)))
        for bad in ({}, {"ring.markers": ""}, {"ring.markers": "1 4 5 7"}, {"ring.markers": "1 4 5 7 12"},
                    {"ring.markers": "1 3 4 5 7"}, {"ring.markers": "x"}):
            with self.assertRaises(marker_mask.MaskError):          # no list, too few, the triangle, the date window
                marker_mask.dependencies("ring.place_x", "submariner_126610", bad)
        with self.assertRaises(marker_mask.MaskError):
            marker_mask.dependencies("ring.place_x", "submariner_124060")

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


    def test_a_masked_run_keeps_the_ring_of_a_photo_with_the_2_obstructed(self):
        import hashlib
        from PIL import Image
        from tools.image_archive import eligibility as el
        from tools.image_archive.faceon import REQUIRED
        model = "submariner_124060"
        req = list(REQUIRED[model])
        ev = {"required": req, "present": req, "credible": req, "reliable": req, "cover": {h: 1.0 for h in req},
              "withheld": {}, "hand_confirmed": [2], "soft": {}}
        cfg = dict(GEOMETRY, marker_eligibility={"policy": el.MASK_POLICY_VERSION})
        with tempfile.TemporaryDirectory() as td:
            root = Path(td) / "acq"
            rows, raw, masks = [], [], []
            for i, used in enumerate(("1 3 4 5 6 7 8 9 10 11", "1 2 3 4 5 6 7 8 9 10 11")):
                rel = "images/p%d.png" % i
                (root / rel).parent.mkdir(parents=True, exist_ok=True)
                Image.new("RGB", (8, 8), (i * 60, 0, 0)).save(root / rel)
                sha = hashlib.sha256((root / rel).read_bytes()).hexdigest()
                rows.append({"sha256": sha, "physical_watch_id": "w%d" % i, "class_label": "gen", "local_path": rel})
                masks.append(el.build(sha, "124060", el.marker_statuses(ev, model), "face-on-v3"))
                raw.append({"physical_watch_id": "w%d" % i, "path": str(root / rel), "dial_source": "minute_track",
                            "dial_reproducible": "", "pose_tilt_deg": "3", "marker.4.tang": "0.05",
                            "marker.6.tilt": "0.1", "ring.place_x": "0.02", "ring.markers": used})
            for path, data in ((root / "acquired_images.csv", rows), (Path(td) / "raw.csv", raw)):
                with path.open("w", newline="") as fh:
                    wr = csv.DictWriter(fh, fieldnames=list(data[0]))
                    wr.writeheader()
                    wr.writerows(data)
            (root / marker_mask.MASKS_FILE).write_text("".join(json.dumps(m) + "\n" for m in masks))
            out = Path(td) / "photo.csv"
            marker_mask.apply(cfg, root, Path(td) / "raw.csv", out, Path(td) / "excl.csv")
            got = {r["physical_watch_id"]: r for r in csv.DictReader(out.open())}
            self.assertEqual("0.02", got["w0"]["ring.place_x"])   # the fit left the obstructed 2 out: kept
            self.assertEqual("", got["w1"]["ring.place_x"])       # a ring that used the obstructed 2: excluded
            excl = list(csv.DictReader((Path(td) / "excl.csv").open()))
            self.assertEqual([("w1", "ring.place_x", "2:OBSTRUCTED")],
                             [(e["physical_watch_id"], e["metric"], e["statuses"]) for e in excl])
            pm.summarise(out, ["marker.4.tang", "ring.place_x"], Path(td) / "w.csv", Path(td) / "r.csv", {}, cfg)
            data = list(csv.DictReader(out.open()))
            data[1]["ring.place_x"] = "0.02"                         # re-inserting the excluded value is caught
            with out.open("w", newline="") as fh:
                wr = csv.DictWriter(fh, fieldnames=list(data[0]))
                wr.writeheader()
                wr.writerows(data)
            with self.assertRaises(marker_mask.MaskError):
                pm.summarise(out, ["ring.place_x"], Path(td) / "w.csv", Path(td) / "r.csv", {}, cfg)


if __name__ == "__main__":
    unittest.main()
