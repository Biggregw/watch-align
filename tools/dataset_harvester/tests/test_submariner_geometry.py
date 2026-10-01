"""Development-only genuine 124060 geometry-calibration study: scope, geometry, statistics, outputs."""
import csv
import json
import math
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))

from subresearch import geometry124060 as G  # noqa: E402
from subresearch.tables import forbidden_columns  # noqa: E402

SCOPE = {"family": "submariner_12", "model": "124060", "class_tag": "gen", "partition": "development"}
IMG_COLS = ["sha256", "variant", "physical_watch_id", "source_id", "image_index", "family", "model", "class_tag", "partition",
            "dial_found", "dial_source", "edge_fit_valid", "dial_cx", "dial_cy", "dial_r", "fit_cx", "fit_cy", "fit_a", "fit_b",
            "fit_angle_deg", "fit_axis_ratio", "fit_rms_px", "mpose_tilt_deg", "mpose_residual_over_r",
            "rh_w12_px", "rh_w3_px", "rh_w6_px", "rh_w9_px", "rh_min_over_mean"]
LM_COLS = ["sha256", "variant", "landmark", "family", "model", "class_tag", "partition", "detected", "x", "y", "fit_path",
           "outline_class", "dtheta_from_nominal_deg", "width_over_r", "length_over_r", "apex_deg", "rotation_deg", "gap_over_r",
           "centring_raw", "track_r_over_r", "tick_pitch_deg", "track_spread_over_r", "inset", "radius_over_r",
           "surround_radius_over_r", "inner_ring_over_r", "rings_found", "reject_fraction"]


def img(sha, watch, variant="orig", source="edge_fit", r=400.0, **scope):
    row = dict(SCOPE, **scope)
    row.update({"sha256": sha, "variant": variant, "physical_watch_id": watch, "source_id": watch, "image_index": "0",
                "dial_found": "True", "dial_source": source, "edge_fit_valid": str(source == "edge_fit"),
                "dial_cx": 500.0, "dial_cy": 500.0, "dial_r": r, "fit_cx": 500.0, "fit_cy": 500.0, "fit_a": r, "fit_b": r,
                "fit_angle_deg": 0.0, "fit_axis_ratio": 1.0, "fit_rms_px": 1.0, "mpose_tilt_deg": 2.0,
                "mpose_residual_over_r": 0.002, "rh_w12_px": 20, "rh_w3_px": 20, "rh_w6_px": 20, "rh_w9_px": 20, "rh_min_over_mean": 0.9})
    return row


def at(rho, clock_deg, r=400.0):
    a = math.radians(clock_deg)
    return 500.0 + rho * r * math.sin(a), 500.0 - rho * r * math.cos(a)


def lms(sha, variant="orig", round_rho=0.80, tri_rho=0.80, r=400.0, **scope):
    out = []
    base = dict(SCOPE, **scope)
    x, y = at(tri_rho, 0.0, r)
    out.append(dict(base, sha256=sha, variant=variant, landmark="12", detected="True", x=x, y=y, fit_path="sub_tri_v2:edge_refit",
                    outline_class="outer", dtheta_from_nominal_deg=0.0, width_over_r=0.22, length_over_r=0.29, apex_deg=44.0,
                    rotation_deg=0.3, gap_over_r=0.04, centring_raw=0.0, track_r_over_r=0.88, tick_pitch_deg=6.0,
                    track_spread_over_r=0.01))
    for h in (3, 6, 9):
        x, y = at(0.78, h * 30.0, r)
        out.append(dict(base, sha256=sha, variant=variant, landmark=f"b{h}", detected="True", x=x, y=y, fit_path="edge_refit",
                        length_over_r=0.2, width_over_r=0.06, rotation_deg=0.1, inset=0.1, gap_over_r=0.03))
    for h in (1, 2, 4, 5, 7, 8, 10, 11):
        x, y = at(round_rho, h * 30.0, r)
        out.append(dict(base, sha256=sha, variant=variant, landmark=f"r{h}", detected="True", x=x, y=y, radius_over_r=0.09,
                        surround_radius_over_r=0.09, inner_ring_over_r=0.07, rings_found=2, reject_fraction=0.0, inset=0.1,
                        gap_over_r=0.03))
    return out


def write_csv(path, rows, cols):
    with path.open("w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=cols, extrasaction="ignore")
        w.writeheader()
        w.writerows(rows)


class GeometryTest(unittest.TestCase):
    def test_polar_circle_and_ellipse(self):
        d = G.Dial(img("a", "w"))
        rho, ang = d.polar(*at(0.8, 0.0))
        self.assertAlmostEqual(rho, 0.8)
        self.assertAlmostEqual(ang, 0.0)
        self.assertAlmostEqual(d.polar(*at(0.5, 90.0))[1], 90.0)
        self.assertAlmostEqual(d.polar(*at(0.5, 270.0))[1], -90.0)
        e = G.Dial(dict(img("a", "w"), fit_a=400.0, fit_b=300.0))  # squashed vertically
        self.assertAlmostEqual(e.polar(900.0, 500.0)[0], 1.0)
        self.assertAlmostEqual(e.polar(500.0, 200.0)[0], 1.0)

    def test_wrap_and_angle_variation(self):
        self.assertAlmostEqual(G.wrap180(181.0), -179.0)
        self.assertAlmostEqual(G.wrap180(-190.0), 170.0)
        vals = G.variation({"orig": {"a_deg": 179.0}, "s94": {"a_deg": -179.0}}, "a_deg")
        self.assertAlmostEqual(G.rng(vals), 2.0)

    def test_relational_symmetric_dial(self):
        cen = {f"r{h}": (0.8, h * 30.0) for h in G.ROUND_HOURS}
        cen.update({"b3": (0.78, 90.0), "b9": (0.78, 270.0), "b6": (0.78, 180.0), "12": (0.8, 0.0)})
        m = G.relational(cen, 0.0, 0.88)
        self.assertAlmostEqual(m["ring_round_rho_median"], 0.8)
        self.assertAlmostEqual(m["ring_round_circle_radius_r"], 0.8, places=6)
        self.assertAlmostEqual(m["ring_round_circle_centre_offset_r"], 0.0, places=6)
        self.assertAlmostEqual(m["ring_round_spacing_rms_deg"], 0.0, places=6)
        for k in ("opp_r1_r7_angle_dev_deg", "b3_b9_angle_dev_deg", "t12_b6_angle_dev_deg", "line12_6_vs_line3_9_orthogonality_deg"):
            self.assertAlmostEqual(m[k], 0.0, places=6, msg=k)
        self.assertAlmostEqual(m["baton_minus_round_rho"], -0.02)
        self.assertAlmostEqual(m["track_minus_round_ring_r"], 0.08)


class ScopeTest(unittest.TestCase):
    def measurement(self, d: Path, extra_img=(), extra_lm=()):
        imgs = [img("dev1", "w1")] + list(extra_img)
        write_csv(d / "sub_images.csv", imgs, IMG_COLS)
        write_csv(d / "sub_landmarks.csv", lms("dev1") + list(extra_lm), LM_COLS)

    def test_only_development_genuine_124060_rows_are_read(self):
        with tempfile.TemporaryDirectory() as t:
            d = Path(t)
            others = [img("val", "wv", partition="validation"), img("hold", "wh", partition="holdout"),
                      img("rep", "wr", class_tag="rep"), img("ln", "wl", model="126610LN")]
            self.measurement(d, others, lms("val", partition="validation") + lms("hold", partition="holdout"))
            imgs, lm = G.load(d)
            self.assertEqual({r["sha256"] for r in imgs}, {"dev1"})
            self.assertEqual({r["sha256"] for r in lm}, {"dev1"})

    def test_no_development_rows_stops(self):
        with tempfile.TemporaryDirectory() as t:
            d = Path(t)
            write_csv(d / "sub_images.csv", [img("hold", "wh", partition="holdout")], IMG_COLS)
            write_csv(d / "sub_landmarks.csv", lms("hold", partition="holdout"), LM_COLS)
            with self.assertRaises(SystemExit):
                G.load(d)

    def test_exclusions(self):
        imgs = [img("good", "w1"), img("fallback", "w1", source="seed_circle"), img("upside", "w2"),
                img("good", "w1", variant="s94", r=430.0)]  # variant on a different ring: dropped
        lm = lms("good") + lms("fallback") + lms("upside") + lms("good", variant="s94", r=430.0)
        review = {"upside": {"exclude_photo": True, "landmarks": {"all"}, "reason": "upside down"},
                  "good": {"exclude_photo": False, "landmarks": {"r8", "12"}, "reason": "hand"}}
        photos, excluded = G.photo_records(imgs, lm, review)
        self.assertEqual([p["sha256"] for p in photos], ["good"])
        self.assertEqual(excluded["dial_not_edge_fitted_or_fallback"], ["fallback"])
        self.assertEqual(excluded["review:upside down"], ["upside"])
        o = photos[0]["variants"]
        self.assertEqual(set(o), {"orig"})
        self.assertNotIn("r8_rho", o["orig"])
        self.assertFalse(any(k.startswith("t12_") for k in o["orig"]))
        self.assertIn("r7_rho", o["orig"])
        # Without the triangle there is no 60-tick reference, so angular offsets are undefined.
        self.assertTrue(math.isnan(o["orig"]["r7_dtheta_deg"]))

    def test_lume_and_surround_kept_separate(self):
        lm = lms("a")
        lm[0]["outline_class"] = "inner"
        photos, _ = G.photo_records([img("a", "w")], lm, {})
        keys = photos[0]["variants"]["orig"]
        self.assertIn("t12_lume_width_r", keys)
        self.assertNotIn("t12_surround_width_r", keys)


class StatisticsTest(unittest.TestCase):
    def photos(self, between, within):
        out = []
        for w in range(6):
            for k in range(2):
                v = between * w + within * (1 if k else -1)
                out.append({"sha256": f"{w}{k}", "physical_watch_id": f"w{w}", "source_id": "s", "image_index": k,
                            "variants": {"orig": {"m": v, "dial_axis_ratio": 1.0, "mpose_tilt_deg": 0.0,
                                                  "inplane_rotation_deg": 0.0, "dial_r_px": 400.0}}, "review": {}})
        return out

    def cls(self, between, within):
        _, watch_rows, rep = G.analyse(self.photos(between, within))
        r = next(x for x in rep if x["metric"] == "m")
        self.assertEqual(r["watches"], 6)
        self.assertEqual(r["photos"], 12)
        self.assertEqual({w["usable_photos"] for w in watch_rows if w["metric"] == "m"}, {2})
        return r["repeatability_class"], r["usefulness_ratio_between_over_within"]

    def test_classes_are_descriptive(self):
        self.assertEqual(self.cls(1.0, 0.1)[0], "between-watch variation clearly exceeds measurement noise")
        self.assertEqual(self.cls(1.0, 1.0)[0], "similar scale")
        self.assertEqual(self.cls(0.1, 1.0)[0], "measurement noise dominates")

    def test_few_watches_is_insufficient(self):
        ph = [p for p in self.photos(1.0, 0.1) if p["physical_watch_id"] in ("w0", "w1", "w2")]
        rep = G.analyse(ph)[2]
        self.assertEqual(next(x for x in rep if x["metric"] == "m")["repeatability_class"], "insufficient data")

    def test_writer_refuses_decision_columns(self):
        with tempfile.TemporaryDirectory() as t:
            with self.assertRaises(ValueError):
                G.write(Path(t) / "x.csv", [{"metric": "m", "verdict": "x"}])


class EndToEndTest(unittest.TestCase):
    def test_outputs(self):
        with tempfile.TemporaryDirectory() as t:
            d, out = Path(t) / "m", Path(t) / "o"
            d.mkdir()
            imgs, lm = [], []
            for w in range(6):
                for k in range(2):
                    sha = f"w{w}p{k}"
                    imgs.append(img(sha, f"watch{w}"))
                    lm += lms(sha, round_rho=0.80 + 0.005 * w + 0.0005 * k)
            imgs.append(img("hold", "wh", partition="holdout"))
            write_csv(d / "sub_images.csv", imgs, IMG_COLS)
            write_csv(d / "sub_landmarks.csv", lm, LM_COLS)
            self.assertEqual(G.main([str(d), str(out)]), 0)
            names = ["sub124060_photo_geometry.csv", "sub124060_watch_geometry.csv", "sub124060_metric_repeatability.csv",
                     "sub124060_geometry_summary.json", "sub124060_geometry_summary.md"]
            for n in names:
                self.assertTrue((out / n).exists(), n)
            for n in names[:3]:
                with (out / n).open(encoding="utf-8") as fh:
                    rows = list(csv.DictReader(fh))
                self.assertEqual(forbidden_columns(rows[0].keys()), [])
                self.assertNotIn("hold", {r.get("sha256") for r in rows})
            with (out / names[1]).open(encoding="utf-8") as fh:
                wr = list(csv.DictReader(fh))
            self.assertTrue({"physical_watch_id", "usable_photos"} <= set(wr[0]))
            s = json.loads((out / names[3]).read_text(encoding="utf-8"))
            self.assertEqual((s["watches_used"], s["usable_photos"]), (6, 12))
            with (out / names[2]).open(encoding="utf-8") as fh:
                rep = {r["metric"]: r for r in csv.DictReader(fh)}
            self.assertEqual(rep["ring_round_rho_median"]["repeatability_class"],
                             "between-watch variation clearly exceeds measurement noise")


if __name__ == "__main__":
    unittest.main()
